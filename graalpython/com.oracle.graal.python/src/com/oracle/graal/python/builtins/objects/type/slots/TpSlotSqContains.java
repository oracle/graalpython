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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CONTAINS__;

import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckInquiryResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotBinaryFuncBuiltin;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
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

public final class TpSlotSqContains {
    private TpSlotSqContains() {
    }

    public abstract static class TpSlotSqContainsBuiltin<T extends SqContainsBuiltinNode> extends TpSlotBinaryFuncBuiltin<T> {

        protected TpSlotSqContainsBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, PExternalFunctionWrapper.OBJOBJPROC, J___CONTAINS__);
        }

        final SqContainsBuiltinNode createOpSlotNode() {
            return createNode();
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class SqContainsBuiltinNode extends PythonBinaryBuiltinNode {
        public abstract boolean executeBoolean(VirtualFrame frame, Object obj, Object value);

        @Override
        public final Object execute(VirtualFrame frame, Object obj, Object value) {
            return executeBoolean(frame, obj, value);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotSqContainsNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "sq_contains");

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object arg);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static boolean callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotSqContainsBuiltin<?> slot, Object self, Object arg,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotSqContainsBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createOpSlotNode()") SqContainsBuiltinNode slotNode) {
            return slotNode.executeBoolean(frame, self, arg);
        }

        @Specialization
        static boolean callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object arg,
                        @Cached BinaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object result = dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), self, arg);
            return isTrueNode.execute(frame, result);
        }

        @Specialization
        static boolean callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object self, Object arg,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode selfToNativeNode,
                        @Cached(inline = false) PythonToNativeNode argToNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) CheckInquiryResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___CONTAINS__, slot.callable,
                            selfToNativeNode.execute(self), argToNativeNode.execute(arg));
            return checkResultNode.executeBool(state, T___CONTAINS__, result);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static boolean callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotBinaryFuncBuiltin<?> slot, Object self, Object arg,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg);
            return (boolean) BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
