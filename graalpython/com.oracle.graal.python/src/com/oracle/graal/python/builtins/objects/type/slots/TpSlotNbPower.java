/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.TernaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.CallReversiblePythonSlotNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotReversiblePython;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotNbPower {
    private TpSlotNbPower() {
    }

    public abstract static class TpSlotNbPowerBuiltin<T extends PythonTernaryBuiltinNode> extends TpSlotBuiltin<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        private static final BuiltinSlotWrapperSignature SIGNATURE = BuiltinSlotWrapperSignature.of(2, "v", "w", "z");

        protected TpSlotNbPowerBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final PythonTernaryBuiltinNode createOpSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, SIGNATURE, getNodeFactory(), J___POW__);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case TERNARYFUNC -> createBuiltin(core, type, tsName, SIGNATURE, wrapper, getNodeFactory());
                case TERNARYFUNC_R -> createRBuiltin(core, type, tsName);
                default -> null;
            };
        }

        private PBuiltinFunction createRBuiltin(Python3Core core, Object type, TruffleString tsName) {
            var factory = WrapperNodeFactory.wrap(getNodeFactory(), SwapArgumentsNode.class, SwapArgumentsNode::new);
            return createBuiltin(core, type, tsName, SIGNATURE, PExternalFunctionWrapper.TERNARYFUNC_R, factory);
        }
    }

    static final class SwapArgumentsNode extends PythonTernaryBuiltinNode {
        @Child private PythonTernaryBuiltinNode wrapped;

        SwapArgumentsNode(PythonTernaryBuiltinNode wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public Object execute(VirtualFrame frame, Object self, Object w, Object z) {
            return wrapped.execute(frame, w, self, z);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotNbPowerNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "ternaryfunc");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes);

        @SuppressWarnings("unused")
        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, TpSlotNbPowerBuiltin<?> slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes,
                        @Cached("slot") TpSlotNbPowerBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createOpSlotNode()") PythonTernaryBuiltinNode slotNode) {
            return slotNode.execute(frame, v, w, z);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, TpSlotReversiblePython slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes,
                        @Cached(inline = false) CallNbPowerPythonNode callPython) {
            return callPython.execute(frame, slot, v, vType, w, wSlot, wType, z, sameTypes);
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode vToNative,
                        @Cached(inline = false) PythonToNativeNode wToNative,
                        @Cached(inline = false) PythonToNativeNode zToNative,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonContext.PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___POW__, slot.callable,
                            vToNative.execute(v), wToNative.execute(w), zToNative.execute(z));
            return checkResultNode.execute(state, T___POW__, toPythonNode.execute(result));
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotNbPowerBuiltin<?> slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes,
                        @Cached CallDispatchers.SimpleIndirectInvokeNode invoke) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, v);
            PArguments.setArgument(arguments, 1, w);
            PArguments.setArgument(arguments, 2, z);
            RootCallTarget callTarget = PythonLanguage.get(inliningTarget).getBuiltinSlotCallTarget(slot.callTargetIndex);
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // intentional explicit "data-class"
    abstract static class CallNbPowerPythonNode extends Node {
        public abstract Object execute(VirtualFrame frame, TpSlotReversiblePython slot, Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes);

        @Specialization
        static Object callPythonAsBinary(VirtualFrame frame, TpSlotReversiblePython slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, @SuppressWarnings("unused") PNone z, boolean sameTypes,
                        @Cached CallReversiblePythonSlotNode callPython) {
            return callPython.execute(frame, slot, v, vType, w, wSlot, wType, sameTypes, ReversibleSlot.NB_POWER_BINARY);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object callPythonAsTernary(VirtualFrame frame, TpSlotReversiblePython slot,
                        Object v, Object vType, Object w, TpSlot wSlot, Object wType, Object z, boolean sameTypes,
                        @Bind("this") Node inliningTarget,
                        @Cached GetCachedTpSlotsNode getSelfSlotsNode,
                        @Cached TernaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached InlinedBranchProfile notImplementedProfile) {
            /*
             * Three-arg power doesn't use __rpow__. But ternary_op can call this when the second
             * argument's type uses slot_nb_power, so check before calling self.__pow__.
             */
            TpSlots selfSlots = getSelfSlotsNode.execute(inliningTarget, vType);
            if (CallReversiblePythonSlotNode.isSameReversibleWrapper(selfSlots.nb_power(), ReversibleSlot.NB_POWER_BINARY)) {
                return dispatcherNode.execute(frame, inliningTarget, slot.getLeft(), slot.getType(), v, w, z);
            }
            notImplementedProfile.enter(inliningTarget);
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotNbInPlacePowerNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "ternaryfunc");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object v, Object w, Object z);

        // There are no builtin implementations

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object v, Object w, @SuppressWarnings("unused") Object z,
                        @Cached BinaryPythonSlotDispatcherNode dispatcherNode) {
            // CPython doesn't pass the third argument to __ipow__
            return dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), v, w);
        }

        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object v, Object w, Object z,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode vToNative,
                        @Cached(inline = false) PythonToNativeNode wToNative,
                        @Cached(inline = false) PythonToNativeNode zToNative,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonContext.PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___IPOW__, slot.callable,
                            vToNative.execute(v), wToNative.execute(w), zToNative.execute(z));
            return checkResultNode.execute(state, T___IPOW__, toPythonNode.execute(result));
        }
    }
}
