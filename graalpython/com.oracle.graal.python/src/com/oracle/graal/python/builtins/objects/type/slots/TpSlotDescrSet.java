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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DEL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.InitCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.BinaryToTernaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.TernaryOrBinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSetFactory.CallSlotDescrSetNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.Dynamic;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

public abstract class TpSlotDescrSet {
    private TpSlotDescrSet() {
    }

    public static boolean PyDescr_IsData(TpSlots descrSlots) {
        return descrSlots.tp_descr_set() != null;
    }

    public abstract static class TpSlotDescrSetBuiltin<T extends DescrSetBuiltinNode> extends TpSlotBuiltin<T> {
        private static final BuiltinSlotWrapperSignature SET_SIGNATURE = BuiltinSlotWrapperSignature.of(J_DOLLAR_SELF, "object", "value");
        private static final BuiltinSlotWrapperSignature GET_SIGNATURE = BuiltinSlotWrapperSignature.of(J_DOLLAR_SELF, "object");
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotDescrSetBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final DescrSetBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget target = createBuiltinCallTarget(language, SET_SIGNATURE, getNodeFactory(), J___SET__);
            language.setBuiltinSlotCallTarget(callTargetIndex, target);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case DESCR_SET -> createBuiltin(core, type, tsName, SET_SIGNATURE, PExternalFunctionWrapper.DESCR_SET, getNodeFactory());
                case DESCR_DELETE -> createBuiltin(core, type, tsName, GET_SIGNATURE, PExternalFunctionWrapper.DESCR_DELETE, BinaryToTernaryBuiltinNode.wrapFactory(getNodeFactory()));
                default -> null;
            };
        }
    }

    public static final class TpSlotDescrSetPython extends TpSlotPython {
        final TruffleWeakReference<Object> setCallable;
        final TruffleWeakReference<Object> delCallable;
        final TruffleWeakReference<Object> type;

        public TpSlotDescrSetPython(Object setCallable, Object delCallable, Object type) {
            this.setCallable = asWeakRef(setCallable);
            this.delCallable = asWeakRef(delCallable);
            this.type = new TruffleWeakReference<>(type);
        }

        public static TpSlotDescrSetPython create(Object[] callables, TruffleString[] callableNames, Object klass) {
            assert callables.length == 2;
            assert callableNames == null || (callableNames[0].equals(T___SET__) && callableNames[1].equals(T___DELETE__));
            return new TpSlotDescrSetPython(callables[0], callables[1], klass);
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object set = Dynamic.getUncached().execute(klass, T___SET__);
            Object delete = Dynamic.getUncached().execute(klass, T___DELETE__);
            if (set != getSetCallable() || delete != getDelCallable()) {
                return new TpSlotDescrSetPython(set, delete, getType());
            }
            return this;
        }

        public Object getSetCallable() {
            return safeGet(setCallable);
        }

        public Object getDelCallable() {
            return safeGet(delCallable);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class DescrSetBuiltinNode extends PythonTernaryBuiltinNode {
        public abstract void executeVoid(VirtualFrame frame, Object self, Object obj, Object value);

        @Override
        public final Object execute(VirtualFrame frame, Object arg, Object arg2, Object arg3) {
            executeVoid(frame, arg, arg2, arg3);
            return PNone.NONE;
        }

        @Override
        protected final Object execute1(VirtualFrame frame, Object arg, Object arg2, Object arg3) {
            executeVoid(frame, arg, arg2, arg3);
            return PNone.NONE;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class CallNativeSlotDescrSet extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "tp_descr_set");

        /**
         * Note: {@link PNone#NO_VALUE} is the equivalent of {@code NULL} for the slot invocation in
         * CPython.
         */
        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object obj, Object value);

        @Specialization
        static void callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self, Object obj, Object value,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode selfToNativeNode,
                        @Cached(inline = false) PythonToNativeNode objToNativeNode,
                        @Cached(inline = false) PythonToNativeNode valueToNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) InitCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, T___SET__, slot.callable, //
                            selfToNativeNode.execute(self), //
                            objToNativeNode.execute(obj), //
                            valueToNativeNode.execute(value));
            checkResultNode.execute(threadState, T___SET__, result);
        }
    }

    private static PException raiseAttributeError(Node inliningTarget, PRaiseNode.Lazy raiseNode, TruffleString attrName) {
        return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, attrName);
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    @SuppressWarnings("rawtypes")
    public abstract static class CallSlotDescrSet extends Node {
        @NeverDefault
        public static CallSlotDescrSet create() {
            return CallSlotDescrSetNodeGen.create();
        }

        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object obj, Object value);

        public final void executeCached(VirtualFrame frame, TpSlot slot, Object self, Object obj, Object value) {
            execute(frame, this, slot, self, obj, value);
        }

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static void callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotDescrSetBuiltin slot, Object self, Object obj, Object value,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotDescrSetBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") DescrSetBuiltinNode slotNode) {
            slotNode.executeVoid(frame, self, obj, value);
        }

        @Specialization
        static void callPython(VirtualFrame frame, Node inliningTarget, TpSlotDescrSetPython slot, Object self, Object obj, Object value,
                        @Cached TernaryOrBinaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object callable;
            boolean callDel = PGuards.isNoValue(value);
            TruffleString name;
            if (callDel) {
                callable = slot.getDelCallable();
                name = T___DEL__;
            } else {
                callable = slot.getSetCallable();
                name = T___SET__;
            }
            if (callable == null) {
                throw raiseAttributeError(inliningTarget, raiseNode, name);
            }
            dispatcherNode.execute(frame, inliningTarget, !callDel, callable, slot.getType(), self, obj, value);
        }

        @Specialization
        @InliningCutoff
        static void callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self, Object obj, Object value,
                        @Cached CallNativeSlotDescrSet callNativeSlot) {
            callNativeSlot.execute(frame, inliningTarget, slot, self, obj, value);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static void callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotDescrSetBuiltin slot, Object self, Object obj, Object value,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, obj);
            PArguments.setArgument(arguments, 2, value);
            BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
