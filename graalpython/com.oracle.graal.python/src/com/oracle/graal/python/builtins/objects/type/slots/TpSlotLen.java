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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;

import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.slots.HPyDispatchers.UnaryHPySlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.UnaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotSimpleBuiltinBase;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
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
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public abstract class TpSlotLen {
    private TpSlotLen() {
    }

    // Note: this should really return long and not int, but that would not work with quickening in
    // the BCI interpreter. For the time being we do what GraalPy used to do before slots were
    // introduced: raise overflow error if native code returns number larger than INT_MAX

    public abstract static class TpSlotLenBuiltin<T extends LenBuiltinNode> extends TpSlotSimpleBuiltinBase<T> {
        protected TpSlotLenBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, BuiltinSlotWrapperSignature.UNARY, PExternalFunctionWrapper.LENFUNC);
        }

        final LenBuiltinNode createSlotNode() {
            return createNode();
        }

        protected abstract int executeUncached(Object self);
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class LenBuiltinNode extends PythonUnaryBuiltinNode {
        public abstract int executeInt(VirtualFrame frame, Object obj);

        @Override
        public final Object execute(VirtualFrame frame, Object obj) {
            return executeInt(frame, obj);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotLenNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "sq_mp_len");

        public abstract int execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static int callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotLenBuiltin<?> slot, Object self,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotLenBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") LenBuiltinNode slotNode) {
            return slotNode.executeInt(frame, self);
        }

        @Specialization(replaces = "callCachedBuiltin")
        static int callGenericSimpleBuiltin(TpSlotLenBuiltin<?> slot, Object self) {
            // Assumption: all len builtins don't need a frame and PE
            return slot.executeUncached(self);
        }

        @Specialization
        static int callPython(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Cached(inline = false) CallSlotLenPythonNode callSlotNode) {
            return callSlotNode.execute(frame, slot, self);
        }

        @Specialization// (guards = "!slot.isHPySlot()")
        @InliningCutoff
        static int callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached(inline = false) CheckPrimitiveFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___LEN__, slot.callable, toNativeNode.execute(self));
            long l = checkResultNode.executeLong(state, T___LEN__, result);
            if (l != (int) l) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, l);
            }
            return (int) l;
        }

        // @Specialization(guards = "slot.isHPySlot()")
        // @InliningCutoff
        @SuppressWarnings("unused")
        static int callHPy(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached UnaryHPySlotDispatcherNode hpyDispatcher,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached(inline = false) CheckPrimitiveFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = hpyDispatcher.execute(frame, inliningTarget, ctx, state, slot.callable, self);
            long l = checkResultNode.executeLong(state, T___LEN__, result);
            if (!PInt.isIntRange(l)) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, l);
            }
            return (int) l;
        }
    }

    /**
     * Can be used to call both {@code sq_length} and {@code mp_length}.
     */
    @GenerateUncached
    @GenerateInline(false) // intentionally lazy
    abstract static class CallSlotLenPythonNode extends Node {
        abstract int execute(VirtualFrame frame, TpSlotPythonSingle slot, Object obj);

        @Specialization
        static int doIt(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached UnaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached InlinedBranchProfile genericCheck,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaIntLossyNode castLossy,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            // See CPython: slot_sq_length
            Object result = dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.type, self);
            if (!genericCheck.wasEntered(inliningTarget)) {
                try {
                    return checkLen(inliningTarget, raiseNode, PGuards.expectInteger(result));
                } catch (UnexpectedResultException e) {
                    // fall-through
                    genericCheck.enter(inliningTarget);
                }
            }
            return convertAndCheckLen(frame, inliningTarget, result, indexNode, castLossy, asSizeNode, raiseNode);
        }

        static int checkLen(Node inliningTarget, PRaiseNode.Lazy raiseNode, int len) {
            if (len < 0) {
                raiseLenGt0(inliningTarget, raiseNode);
            }
            return len;
        }

        @InliningCutoff
        private static void raiseLenGt0(Node inliningTarget, Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.LEN_SHOULD_RETURN_GT_ZERO);
        }

        public static int convertAndCheckLen(VirtualFrame frame, Node inliningTarget, Object result, PyNumberIndexNode indexNode,
                        CastToJavaIntLossyNode castLossy, PyNumberAsSizeNode asSizeNode, PRaiseNode.Lazy raiseNode) {
            int len;
            Object index = indexNode.execute(frame, inliningTarget, result);
            try {
                len = asSizeNode.executeExact(frame, inliningTarget, index);
            } catch (PException e) {
                /*
                 * CPython first checks whether the number is negative before converting it to an
                 * integer. Comparing PInts is not cheap for us, so we do the conversion first. If
                 * the conversion overflowed, we need to do the negativity check before raising the
                 * overflow error.
                 */
                throw checkNegative(inliningTarget, castLossy, raiseNode, e, index);
            }
            return checkLen(inliningTarget, raiseNode, len);
        }

        @InliningCutoff
        private static PException checkNegative(Node inliningTarget, CastToJavaIntLossyNode castLossy, Lazy raiseNode, PException e, Object index) {
            int len;
            len = castLossy.execute(inliningTarget, index);
            checkLen(inliningTarget, raiseNode, len);
            throw e;
        }
    }
}
