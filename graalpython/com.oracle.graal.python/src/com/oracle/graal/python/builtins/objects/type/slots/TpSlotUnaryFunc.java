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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.UnaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotUnaryFunc {
    private TpSlotUnaryFunc() {
    }

    public abstract static class TpSlotUnaryFuncBuiltin<T extends PythonUnaryBuiltinNode> extends TpSlotBuiltinBase<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        private final String builtinName;

        protected TpSlotUnaryFuncBuiltin(NodeFactory<T> nodeFactory, String builtinName) {
            super(nodeFactory, BuiltinSlotWrapperSignature.UNARY, PExternalFunctionWrapper.UNARYFUNC);
            this.builtinName = builtinName;
        }

        final PythonUnaryBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public final void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createBuiltinCallTarget(language, BuiltinSlotWrapperSignature.UNARY, getNodeFactory(), builtinName);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotUnaryNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "unaryfunc");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotUnaryFuncBuiltin<?> slot, Object self,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotUnaryFuncBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") PythonUnaryBuiltinNode slotNode) {
            return slotNode.execute(frame, self);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Cached(inline = false) CallSlotUnaryPythonNode callSlotNode) {
            return callSlotNode.execute(frame, slot, self);
        }

        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object self,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonThreadState state = getThreadStateNode.execute(inliningTarget);
            TruffleString name = T___INDEX__; // TODO: how to determine
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, name, slot.callable,
                            toNativeNode.execute(self));
            return checkResultNode.execute(state, name, toPythonNode.execute(result));
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotUnaryFuncBuiltin<?> slot, Object self,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(1);
            PArguments.setArgument(arguments, 0, self);
            return BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // intentionally lazy
    abstract static class CallSlotUnaryPythonNode extends Node {
        abstract Object execute(VirtualFrame frame, TpSlotPythonSingle slot, Object obj);

        @Specialization
        static Object doIt(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached UnaryPythonSlotDispatcherNode dispatcherNode) {
            return dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), self);
        }
    }
}
