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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.PythonSlotDispatcherNodeBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGetFactory.CallSlotDescrGetNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.FunctionInvokeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class TpSlotDescrGet {
    private TpSlotDescrGet() {
    }

    public abstract static sealed class TpSlotDescrGetBuiltin<T extends DescrGetBuiltinNode> extends TpSlotBuiltin<T>//
    permits TpSlotDescrGetBuiltinComplex, TpSlotDescrGetBuiltinSimple {
        static final BuiltinSlotWrapperSignature SIGNATURE = BuiltinSlotWrapperSignature.of(2, J_DOLLAR_SELF, "obj", "type");
        static final PExternalFunctionWrapper WRAPPER = PExternalFunctionWrapper.DESCR_GET;

        protected TpSlotDescrGetBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final DescrGetBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            if (wrapper != WRAPPER) {
                return null;
            }
            // The __get__ wrapper normalizes None to NO_VALUE (NULL in CPython), so we cannot use
            // the "raw" slot node, but a wrapper that implements this logic and delegates to it
            var factory = WrapperNodeFactory.wrap(getNodeFactory(), DescrGetWrapperNode.class, DescrGetWrapperNode::new);
            return createBuiltin(core, type, tsName, SIGNATURE, wrapper, factory);
        }
    }

    public abstract static non-sealed class TpSlotDescrGetBuiltinSimple<T extends DescrGetBuiltinNode> extends TpSlotDescrGetBuiltin<T> {
        protected TpSlotDescrGetBuiltinSimple(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        protected abstract Object executeUncached(Object self, Object obj, Object type);

        @Override
        public void initialize(PythonLanguage language) {
            // nop
        }
    }

    public abstract static non-sealed class TpSlotDescrGetBuiltinComplex<T extends DescrGetBuiltinNode> extends TpSlotDescrGetBuiltin<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotDescrGetBuiltinComplex(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        @Override
        public void initialize(PythonLanguage language) {
            // We need a different call-target for the "raw" slot. It must not normalize None to
            // NO_VALUE (NULL in CPython) like the __get__ wrapper.
            RootCallTarget callTarget = createBuiltinCallTarget(language, SIGNATURE, getNodeFactory(), J___GET__);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }
    }

    static final class DescrGetWrapperNode extends PythonTernaryBuiltinNode {
        private final ConditionProfile objIsNoneProfile = ConditionProfile.create();
        private final ConditionProfile typeIsNoneProfile = ConditionProfile.create();
        @Child private PRaiseNode raiseNode;
        @Child private DescrGetBuiltinNode wrapped;

        DescrGetWrapperNode(DescrGetBuiltinNode wrapped) {
            this.wrapped = wrapped;
        }

        private static Object normalizeNone(ConditionProfile profile, Object o) {
            return profile.profile(PGuards.isNone(o)) ? PNone.NO_VALUE : o;
        }

        @Override
        public Object execute(VirtualFrame frame, Object self, Object objIn, Object typeIn) {
            Object obj = normalizeNone(objIsNoneProfile, objIn);
            Object type = normalizeNone(typeIsNoneProfile, typeIn);
            if (obj == PNone.NO_VALUE && type == PNone.NO_VALUE) {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.GET_NONE_NONE_IS_INVALID);
            }
            return wrapped.execute(frame, self, obj, type);
        }

        @Override
        protected Object execute1(VirtualFrame frame, Object self, Object obj, Object type) {
            return execute(frame, self, obj, type);
        }
    }

    public abstract static class DescrGetBuiltinNode extends PythonTernaryBuiltinNode {
    }

    @GenerateCached
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @SuppressWarnings("rawtypes")
    public abstract static class CallSlotDescrGet extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "tp_descr_get");

        /**
         * Note: for GraalPy value {@link PNone#NO_VALUE} has the same semantics as {@code NULL} in
         * CPython. It is forwarded to builtins and native slots and translated to
         * {@link PNone#NONE} for Python slots like CPython does in {@code slot_tp_descr_get}.
         */
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object obj, Object type);

        public final Object executeCached(VirtualFrame frame, TpSlot slot, Object self, Object obj, Object type) {
            return execute(frame, this, slot, self, obj, type);
        }

        public static CallSlotDescrGet create() {
            return CallSlotDescrGetNodeGen.create();
        }

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotDescrGetBuiltin slot, Object self, Object obj, Object type,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotDescrGetBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") DescrGetBuiltinNode slotNode) {
            return slotNode.execute(frame, self, obj, type);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object obj, Object type,
                        @Cached DescrGetPythonSlotDispatcherNode dispatcherNode) {
            return dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), self, obj, type);
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self, Object obj, Object value,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode selfToNativeNode,
                        @Cached(inline = false) PythonToNativeNode objToNativeNode,
                        @Cached(inline = false) PythonToNativeNode valueToNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, T___GET__, slot.callable, //
                            selfToNativeNode.execute(self), //
                            objToNativeNode.execute(obj), //
                            valueToNativeNode.execute(value));
            return checkResultNode.execute(threadState, T___GET__, toPythonNode.execute(result));
        }

        @Specialization(replaces = "callCachedBuiltin")
        static Object callGenericSimpleBuiltin(TpSlotDescrGetBuiltinSimple slot, Object self, Object obj, Object type) {
            // Simple builtins should not need a frame
            return slot.executeUncached(self, obj, type);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotDescrGetBuiltinComplex slot, Object self, Object obj, Object type,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, obj);
            PArguments.setArgument(arguments, 2, type);
            return BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }

    /**
     * The wrapper {@code slot_tp_descr_get} in CPython does not use {@code vectorcall_method} or
     * {@code lookup_maybe_method}, but {@code _PyType_LookupId}, i.e., it does not bind
     * descriptors.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class DescrGetPythonSlotDispatcherNode extends PythonSlotDispatcherNodeBase {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object callable, Object type, Object self, Object arg1, Object arg2);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee", "isSimpleSignature(cachedCallee, 3)"}, limit = "getCallSiteInlineCacheMaxDepth()")
        protected static Object doCachedPFunction(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PFunction callee, @SuppressWarnings("unused") Object type, Object self,
                        Object arg1, Object arg2,
                        @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                        @Cached @Shared InlinedConditionProfile arg1Profile,
                        @Cached @Shared InlinedConditionProfile arg2Profile,
                        @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, normalizeNoValue(arg1Profile, inliningTarget, arg1));
            PArguments.setArgument(arguments, 2, normalizeNoValue(arg2Profile, inliningTarget, arg2));
            return invoke.execute(frame, arguments);
        }

        private static Object normalizeNoValue(InlinedConditionProfile profile, Node inlinintTarget, Object o) {
            return profile.profile(inlinintTarget, o == PNone.NO_VALUE) ? PNone.NONE : o;
        }

        @Specialization(replaces = "doCachedPFunction")
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object callable, @SuppressWarnings("unused") Object type, Object self, Object arg1, Object arg2,
                        @Cached @Shared InlinedConditionProfile arg1Profile,
                        @Cached @Shared InlinedConditionProfile arg2Profile,
                        @Cached(inline = false) CallNode callNode) {
            return callNode.execute(frame, callable, self, //
                            normalizeNoValue(arg1Profile, inliningTarget, arg1), //
                            normalizeNoValue(arg2Profile, inliningTarget, arg2));
        }
    }
}
