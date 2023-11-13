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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;

import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckInquiryResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.UnaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotSimpleBuiltinBase;
import com.oracle.graal.python.lib.PyBoolCheckNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class TpSlotInquiry {
    private TpSlotInquiry() {
    }

    public abstract static class TpSlotInquiryBuiltin<T extends InquiryBuiltinNode> extends TpSlotSimpleBuiltinBase<T> {
        protected TpSlotInquiryBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, BuiltinSlotWrapperSignature.UNARY, PExternalFunctionWrapper.INQUIRY);
        }

        final InquiryBuiltinNode createSlotNode() {
            return createNode();
        }

        protected abstract boolean executeUncached(Object self);
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class InquiryBuiltinNode extends PythonUnaryBuiltinNode {
        public abstract boolean executeBool(VirtualFrame frame, Object obj);

        @Override
        public final Object execute(VirtualFrame frame, Object arg) {
            return executeBool(frame, arg);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @SuppressWarnings("rawtypes")
    public abstract static class CallSlotNbBoolNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "nb_bool");

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self);

        @Specialization(guards = "slot == cachedSlot", limit = "3")
        static boolean callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotInquiryBuiltin slot, Object self,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotInquiryBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") InquiryBuiltinNode slotNode) {
            return slotNode.executeBool(frame, self);
        }

        @Specialization(replaces = "callCachedBuiltin")
        static boolean callGenericSimpleBuiltin(TpSlotInquiryBuiltin slot, Object self) {
            // All nb_bool builtins are known to be simple enough to not require PE for good
            // performance, so we call them uncached
            return slot.executeUncached(self);
        }

        @Specialization
        static boolean callPython(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Cached(inline = false) CallNbBoolPythonNode callSlotNode) {
            return callSlotNode.execute(frame, slot, self);
        }

        @Specialization
        @InliningCutoff
        static boolean callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) CheckInquiryResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, ctx, threadState, C_API_TIMING, T___BOOL__, slot.callable, toNativeNode.execute(self));
            return checkResultNode.executeBool(threadState, T___BOOL__, result);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // intentionally lazy
    public abstract static class CallNbBoolPythonNode extends Node {
        abstract boolean execute(VirtualFrame frame, TpSlotPythonSingle slot, Object obj);

        @Specialization
        static boolean doIt(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached UnaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached PyBoolCheckNode pyBoolCheckNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyObjectIsTrueNode pyObjectIsTrueNode) {
            // See CPython: slot_nb_bool
            // TODO: it is not clear to me why CPython lookups __len__ in the slot wrapper although
            // the slow wrapper is assigned only in the presence of __bool__ magic method and not
            // __len__. We ignore the __len__ lookup for now.
            Object result = dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.type, self);
            if (!pyBoolCheckNode.execute(inliningTarget, result)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BOOL_SHOULD_RETURN_BOOL, result);
            }
            return pyObjectIsTrueNode.execute(frame, inliningTarget, result);
        }
    }
}
