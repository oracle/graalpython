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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class TpSlotBinaryFunc {
    private TpSlotBinaryFunc() {
    }

    public abstract static class TpSlotBinaryFuncBuiltin<T extends PythonBinaryBuiltinNode> extends TpSlotBuiltinBase<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        private final String builtinName;

        protected TpSlotBinaryFuncBuiltin(NodeFactory<T> nodeFactory, PExternalFunctionWrapper wrapper, String builtinName) {
            super(nodeFactory, BuiltinSlotWrapperSignature.BINARY, wrapper);
            this.builtinName = builtinName;
        }

        final PythonBinaryBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget target = createSlotCallTarget(language, BuiltinSlotWrapperSignature.BINARY, getNodeFactory(), builtinName);
            language.setBuiltinSlotCallTarget(callTargetIndex, target);
        }
    }

    public abstract static class TpSlotMpSubscript<T extends PythonBinaryBuiltinNode> extends TpSlotBinaryFuncBuiltin<T> {
        protected TpSlotMpSubscript(NodeFactory<T> nodeFactory) {
            super(nodeFactory, PExternalFunctionWrapper.BINARYFUNC, J___GETITEM__);
        }
    }

    public abstract static class TpSlotSqConcat<T extends PythonBinaryBuiltinNode> extends TpSlotBinaryFuncBuiltin<T> {
        protected TpSlotSqConcat(NodeFactory<T> nodeFactory) {
            super(nodeFactory, PExternalFunctionWrapper.BINARYFUNC, J___ADD__);
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class MpSubscriptBuiltinNode extends PythonBinaryBuiltinNode {
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class SqConcatBuiltinNode extends PythonBinaryBuiltinNode {
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotBinaryFuncNode extends Node {
        private static final TruffleString T_BINARY_SLOT = tsLiteral("<binary slot>");
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "binaryfunc");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object arg);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotBinaryFuncBuiltin<?> slot, Object self, Object arg,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotBinaryFuncBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") PythonBinaryBuiltinNode slotNode) {
            return slotNode.execute(frame, self, arg);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object arg,
                        @Cached BinaryPythonSlotDispatcherNode dispatcherNode) {
            return dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), self, arg);
        }

        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object self, Object arg,
                        @Exclusive @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode selfToNativeNode,
                        @Cached(inline = false) PythonToNativeNode argToNativeNode,
                        @Exclusive @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Exclusive @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T_BINARY_SLOT, slot.callable,
                            selfToNativeNode.execute(self), argToNativeNode.execute(arg));
            return checkResultNode.execute(state, T_BINARY_SLOT, toPythonNode.execute(result));
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotBinaryFuncBuiltin<?> slot, Object self, Object arg,
                        @Cached CallDispatchers.SimpleIndirectInvokeNode invoke) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg);
            RootCallTarget callTarget = PythonLanguage.get(inliningTarget).getBuiltinSlotCallTarget(slot.callTargetIndex);
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }
}
