/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckInquiryResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.TernaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
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

public final class TpSlotMpAssSubscript {
    private TpSlotMpAssSubscript() {
    }

    public abstract static class TpSlotMpAssSubscriptBuiltin<T extends MpAssSubscriptBuiltinNode> extends TpSlotBuiltinBase<T> {
        public static final BuiltinSlotWrapperSignature SET_SIGNATURE = BuiltinSlotWrapperSignature.of(J_DOLLAR_SELF, "key", "value");
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotMpAssSubscriptBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, BuiltinSlotWrapperSignature.BINARY, PExternalFunctionWrapper.BINARYFUNC);
        }

        final MpAssSubscriptBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget target = createBuiltinCallTarget(language, SET_SIGNATURE, getNodeFactory(), J___SETITEM__);
            language.setBuiltinSlotCallTarget(callTargetIndex, target);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case OBJOBJARGPROC -> createBuiltin(core, type, T___SETITEM__, SET_SIGNATURE, wrapper, getNodeFactory());
                case MP_DELITEM -> createBuiltin(core, type, T___DELITEM__, BuiltinSlotWrapperSignature.BINARY, wrapper,
                                NodeFactoryUtils.BinaryToTernaryBuiltinNode.wrapFactory(getNodeFactory()));
                default ->
                    throw new IllegalStateException(Objects.toString(wrapper));
            };
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class MpAssSubscriptBuiltinNode extends PythonTernaryBuiltinNode {
        public abstract void executeVoid(VirtualFrame frame, Object self, Object key, Object value);

        @Override
        public final Object execute(VirtualFrame frame, Object self, Object key, Object value) {
            executeVoid(frame, self, key, value);
            return PNone.NONE;
        }
    }

    public static final class TpSlotMpAssSubscriptPython extends TpSlotPython {
        private final TruffleWeakReference<Object> setitem;
        private final TruffleWeakReference<Object> delitem;
        private final TruffleWeakReference<Object> type;

        public TpSlotMpAssSubscriptPython(Object setitem, Object delitem, Object type) {
            this.setitem = asWeakRef(setitem);
            this.delitem = asWeakRef(delitem);
            this.type = new TruffleWeakReference<>(type);
        }

        public static TpSlotMpAssSubscriptPython create(Object[] callables, TruffleString[] callableNames, Object type) {
            assert callables.length == 2;
            assert callableNames == null || (callableNames[0].equals(T___SETITEM__) && callableNames[1].equals(T___DELITEM__));
            return new TpSlotMpAssSubscriptPython(callables[0], callables[1], type);
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object newSet = LookupAttributeInMRONode.Dynamic.getUncached().execute(klass, T___SETITEM__);
            Object newDel = LookupAttributeInMRONode.Dynamic.getUncached().execute(klass, T___DELITEM__);
            if (newSet != getSetitem() || newDel != getDelitem()) {
                return new TpSlotMpAssSubscriptPython(newSet, newDel, getType());
            }
            return this;
        }

        public Object getSetitem() {
            return safeGet(setitem);
        }

        public Object getDelitem() {
            return safeGet(delitem);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotMpAssSubscriptNode extends Node {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object key, Object value);

        @Specialization
        static void callManagedSlot(VirtualFrame frame, Node inliningTarget, TpSlotManaged slot, Object self, Object key, Object value,
                        @Cached CallManagedSlotMpAssSubscriptNode slotNode) {
            slotNode.execute(frame, inliningTarget, slot, self, key, value);
        }

        @Specialization
        @InliningCutoff
        static void callNative(VirtualFrame frame, TpSlotNative slot, Object self, Object key, Object value,
                        @Cached(inline = false) CallNativeSlotMpAssSubscriptNode callNativeSlot) {
            callNativeSlot.execute(frame, slot, self, key, value);
        }
    }

    @GenerateInline(false) // Used lazily
    @GenerateUncached
    abstract static class CallNativeSlotMpAssSubscriptNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "sq_ass_item");

        abstract void execute(VirtualFrame frame, TpSlotNative slot, Object self, Object key, Object value);

        @Specialization
        static void callNative(VirtualFrame frame, TpSlotNative slot, Object self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached PythonToNativeNode selfToNativeNode,
                        @Cached PythonToNativeNode keyToNativeNode,
                        @Cached PythonToNativeNode valueToNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached CheckInquiryResultNode checkResultNode) {
            Object result;
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget);
            result = externalInvokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, T___SETITEM__, slot.callable,
                            selfToNativeNode.execute(self), keyToNativeNode.execute(key), valueToNativeNode.execute(value));
            checkResultNode.execute(threadState, T___SETITEM__, result);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    @SuppressWarnings("rawtypes")
    public abstract static class CallManagedSlotMpAssSubscriptNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlotManaged slot, Object self, Object key, Object value);

        @Specialization(guards = "slot == cachedSlot", limit = "3")
        static void callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotMpAssSubscriptBuiltin slot, Object self, Object key, Object value,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotMpAssSubscriptBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") MpAssSubscriptBuiltinNode slotNode) {
            slotNode.executeVoid(frame, self, key, value);
        }

        @Specialization(guards = "!isNoValue(value)")
        static void callPythonSimpleSet(VirtualFrame frame, Node inliningTarget, TpSlotMpAssSubscriptPython slot, Object self, Object key, Object value,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Cached TernaryPythonSlotDispatcherNode callPythonFun) {
            Object callable = slot.getSetitem();
            if (callable == null) {
                throw raiseAttributeError(inliningTarget, raiseNode, T___SETITEM__);
            }
            callPythonFun.execute(frame, inliningTarget, callable, slot.getType(), self, key, value);
        }

        @Specialization(guards = "isNoValue(value)")
        @InliningCutoff
        static void callPythonSimpleDel(VirtualFrame frame, Node inliningTarget, TpSlotMpAssSubscriptPython slot, Object self, Object key, @SuppressWarnings("unused") Object value,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Cached BinaryPythonSlotDispatcherNode callPythonFun) {
            Object callable = slot.getDelitem();
            if (callable == null) {
                throw raiseAttributeError(inliningTarget, raiseNode, T___DELITEM__);
            }
            callPythonFun.execute(frame, inliningTarget, callable, slot.getType(), self, key);
        }

        @InliningCutoff
        private static PException raiseAttributeError(Node inliningTarget, PRaiseNode.Lazy raiseNode, TruffleString attrName) {
            return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, attrName);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static void callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotMpAssSubscriptBuiltin slot, Object self, Object key, Object value,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, key);
            PArguments.setArgument(arguments, 2, value);
            BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
