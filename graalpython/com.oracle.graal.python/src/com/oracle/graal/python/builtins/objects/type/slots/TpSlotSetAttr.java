/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.builtins.objects.type.slots.BuiltinSlotWrapperSignature.J_DOLLAR_SELF;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckInquiryResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.BinaryToTernaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.TernaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotSimpleBuiltinBase;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.Dynamic;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/**
 * For managed slots implementations (either builtins or Python code) the calling convention
 * difference between {@code tp_setattr} and {@code tp_setattro} does not make a difference, it only
 * makes a difference for the native slots. We distinguish them using different execute methods on
 * the call slot node.
 */
public class TpSlotSetAttr {

    public abstract static class TpSlotSetAttrBuiltin<T extends SetAttrBuiltinNode> extends TpSlotSimpleBuiltinBase<T> {
        public static final BuiltinSlotWrapperSignature SET_SIGNATURE = BuiltinSlotWrapperSignature.of(J_DOLLAR_SELF, "name", "value");
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotSetAttrBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, BuiltinSlotWrapperSignature.BINARY, PExternalFunctionWrapper.BINARYFUNC);
        }

        final SetAttrBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget target = createBuiltinCallTarget(language, SET_SIGNATURE, getNodeFactory(), J___SETATTR__);
            language.setBuiltinSlotCallTarget(callTargetIndex, target);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case SETATTRO -> createBuiltin(core, type, T___SETATTR__, SET_SIGNATURE, wrapper, getNodeFactory());
                case DELATTRO -> createBuiltin(core, type, T___DELATTR__, BuiltinSlotWrapperSignature.BINARY, wrapper, BinaryToTernaryBuiltinNode.wrapFactory(getNodeFactory()));
                default -> null;
            };
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class SetAttrBuiltinNode extends PythonTernaryBuiltinNode {
        public abstract void executeSetAttr(VirtualFrame frame, Object obj, TruffleString name, Object value);

        @Override
        public final Object execute(VirtualFrame frame, Object obj, Object nameObj, Object value) {
            executeVoid(frame, obj, nameObj, value);
            return PNone.NONE;
        }

        protected abstract void executeVoid(VirtualFrame frame, Object obj, Object nameObj, Object value);

        // The builtins should have a @Specialization that does the coercion to TruffleString and
        // also fast-path specialization for TruffleString as the 2nd argument.
    }

    public static final class TpSlotSetAttrPython extends TpSlotPython {
        private final TruffleWeakReference<Object> setattr;
        private final TruffleWeakReference<Object> delattr;
        private final TruffleWeakReference<Object> type;

        public TpSlotSetAttrPython(Object setattr, Object delattr, Object type) {
            this.setattr = asWeakRef(setattr);
            this.delattr = asWeakRef(delattr);
            this.type = new TruffleWeakReference<>(type);
        }

        public static TpSlotSetAttrPython create(Object[] callables, TruffleString[] callableNames, Object type) {
            assert callables.length == 2;
            assert callableNames == null || (callableNames[0].equals(T___SETATTR__) && callableNames[1].equals(T___DELATTR__));
            return new TpSlotSetAttrPython(callables[0], callables[1], type);
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object newSet = Dynamic.getUncached().execute(klass, T___SETATTR__);
            Object newDel = Dynamic.getUncached().execute(klass, T___DELATTR__);
            if (newSet != getSetattr() || newDel != getDelattr()) {
                return new TpSlotSetAttrPython(newSet, newDel, getType());
            }
            return this;
        }

        public Object getSetattr() {
            return safeGet(setattr);
        }

        public Object getDelattr() {
            return safeGet(delattr);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    /**
     * For managed types, this calls {@link TpSlots#combined_tp_getattro_getattr()}, because their
     * signature difference is not visible on the managed level. For native slots, this calls
     * {@link TpSlots#tp_getattro()} if available, otherwise {@link TpSlots#tp_getattr()} - at least
     * one of those must be available, i.e., the caller is expected to check precondition
     * {@code combined_tp_getattro_getattr() != null}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotSetAttrNode extends Node {
        public final void execute(VirtualFrame frame, Node inliningTarget, TpSlots slots, Object self, TruffleString name, Object value) {
            assert slots.combined_tp_setattro_setattr() != null;
            executeImpl(frame, inliningTarget, slots, slots.combined_tp_setattro_setattr(), self, name, value);
        }

        abstract void executeImpl(VirtualFrame frame, Node inliningTarget, TpSlots slots, TpSlot tp_get_attro_attr, Object self, TruffleString name, Object value);

        @Specialization
        static void callManagedSlot(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlots slots, TpSlotManaged slot, Object self, TruffleString name, Object value,
                        @Cached CallManagedSlotSetAttrNode slotNode) {
            slotNode.execute(frame, inliningTarget, slot, self, name, value);
        }

        @Specialization
        @InliningCutoff
        static void callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, TruffleString name, Object value,
                        @Cached(inline = false) CallNativeSlotSetAttrNode callNativeSlot) {
            callNativeSlot.execute(frame, slots, slot, self, name, value);
        }
    }

    /**
     * Variant of {@link CallSlotSetAttrNode} that accepts generic object as the name. The caller
     * should ensure that the "name" is a Unicode object (subclasses are permitted).
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotSetAttrONode extends Node {
        public final void execute(VirtualFrame frame, Node inliningTarget, TpSlots slots, Object self, Object name, Object value) {
            assert slots.combined_tp_setattro_setattr() != null;
            executeImpl(frame, inliningTarget, slots, slots.combined_tp_setattro_setattr(), self, name, value);
        }

        abstract void executeImpl(VirtualFrame frame, Node inliningTarget, TpSlots slots, TpSlot tp_get_attro_attr, Object self, Object name, Object value);

        @Specialization
        static void callManagedSlot(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlots slots, TpSlotManaged slot, Object self, Object name, Object value,
                        @Cached CallManagedSlotSetAttrNode slotNode) {
            slotNode.execute(frame, inliningTarget, slot, self, name, value);
        }

        @Specialization
        @InliningCutoff
        static void callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, Object name, Object value,
                        @Cached(inline = false) CallNativeSlotSetAttrNode callNativeSlot) {
            callNativeSlot.execute(frame, slots, slot, self, name, value);
        }
    }

    @GenerateInline(false) // Used lazily
    @GenerateUncached
    abstract static class CallNativeSlotSetAttrNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "tp_setattr");

        // The caller should ensure that the "name" is a Unicode object (subclasses are permitted)
        abstract void execute(VirtualFrame frame, TpSlots slots, TpSlotNative tp_set_attro_attr, Object self, Object name, Object value);

        @Specialization
        static void callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, Object name, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached InlinedConditionProfile isSetAttrProfile,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached FreeNode freeNode,
                        @Cached PythonToNativeNode nameToNativeNode,
                        @Cached PythonToNativeNode selfToNativeNode,
                        @Cached PythonToNativeNode valueToNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached CheckInquiryResultNode checkResultNode) {
            assert PyUnicodeCheckNode.executeUncached(name);
            boolean isSetAttr = isSetAttrProfile.profile(inliningTarget, slots.tp_setattr() == slot);
            Object nameArg;
            if (isSetAttr) {
                nameArg = asCharPointerNode.execute(name);
            } else {
                nameArg = nameToNativeNode.execute(name);
            }
            Object result;
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, null);
            try {
                result = externalInvokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, T___GETATTR__, slot.callable, selfToNativeNode.execute(self), nameArg,
                                valueToNativeNode.execute(value));
            } finally {
                if (isSetAttr) {
                    freeNode.free(nameArg);
                }
            }
            checkResultNode.execute(threadState, T___GETATTR__, result);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    @SuppressWarnings("rawtypes")
    public abstract static class CallManagedSlotSetAttrNode extends Node {
        /**
         * We need this entry-point for {@code setattr} builtin that permits any key that's Unicode
         * subclass and upcalls from native code: there the user may, in theory, call the slot
         * directly with non-string {@code name}. We let the builtin/python method handle such
         * situation, because even builtins may differ in the way they handle non-string keys, and
         * Python methods may do all sorts of thins like looking for some tagged string or even
         * accepting non-string keys ({@code slot_tp_setattro} just forwards to the Python method
         * without checking the name).
         */
        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlotManaged slot, Object self, Object name, Object value);

        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlotManaged slot, Object self, TruffleString name, Object value);

        @Specialization(guards = "slot == cachedSlot", limit = "3")
        static void callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotSetAttrBuiltin slot, Object self, TruffleString name, Object value,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotSetAttrBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") SetAttrBuiltinNode slotNode) {
            // Assumption: most of the code is going to pass TruffleString even for the "Object
            // name" execute, we have a slow-path fallback for other cases at the end
            slotNode.executeSetAttr(frame, self, name, value);
        }

        @Specialization(guards = "!isNoValue(value)")
        static void callPythonSimpleSet(VirtualFrame frame, Node inliningTarget, TpSlotSetAttrPython slot, Object self, Object name, Object value,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Cached TernaryPythonSlotDispatcherNode callPythonFun) {
            Object callable = slot.getSetattr();
            if (callable == null) {
                throw raiseAttributeError(inliningTarget, raiseNode, T___SETATTR__);
            }
            callPythonFun.execute(frame, inliningTarget, callable, slot.getType(), self, name, value);
        }

        @Specialization(guards = "isNoValue(value)")
        @InliningCutoff
        static void callPythonSimpleDel(VirtualFrame frame, Node inliningTarget, TpSlotSetAttrPython slot, Object self, Object name, @SuppressWarnings("unused") Object value,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Cached BinaryPythonSlotDispatcherNode callPythonFun) {
            Object callable = slot.getDelattr();
            if (callable == null) {
                throw raiseAttributeError(inliningTarget, raiseNode, T___DELATTR__);
            }
            callPythonFun.execute(frame, inliningTarget, callable, slot.getType(), self, name);
        }

        @InliningCutoff
        private static PException raiseAttributeError(Node inliningTarget, PRaiseNode.Lazy raiseNode, TruffleString attrName) {
            return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, attrName);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static void callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotSetAttrBuiltin slot, Object self, Object name, Object value,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, name);
            PArguments.setArgument(arguments, 2, value);
            BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
