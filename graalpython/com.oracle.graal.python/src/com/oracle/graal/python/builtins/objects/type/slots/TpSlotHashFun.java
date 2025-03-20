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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.UnaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class TpSlotHashFun {
    private TpSlotHashFun() {
    }

    public abstract static class TpSlotHashBuiltin<T extends HashBuiltinNode>
                    extends TpSlotBuiltinBase<T> {
        static final BuiltinSlotWrapperSignature SIGNATURE = BuiltinSlotWrapperSignature.UNARY;
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        public TpSlotHashBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, SIGNATURE, PExternalFunctionWrapper.HASHFUNC);
        }

        HashBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, SIGNATURE, getNodeFactory(), J___HASH__);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class HashBuiltinNode extends PythonUnaryBuiltinNode {
        public abstract long executeLong(VirtualFrame frame, Object obj);

        @Override
        public final Object execute(VirtualFrame frame, Object obj) {
            return executeLong(frame, obj);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotHashFunNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "tp_hash");

        public abstract long execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static long callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotHashBuiltin<?> slot, Object self,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotHashBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") HashBuiltinNode slotNode) {
            return slotNode.executeLong(frame, self);
        }

        @Specialization
        static long callPython(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Cached(inline = false) CallSlotHashFunPythonNode callSlotNode) {
            return callSlotNode.execute(frame, slot, self);
        }

        @Specialization
        static long callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object self,
                        @Exclusive @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Exclusive @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Exclusive @Cached(inline = false) CheckPrimitiveFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___HASH__, slot.callable, toNativeNode.execute(self));
            return checkResultNode.executeLong(state, T___HASH__, result);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static long callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotHashBuiltin<?> slot, Object self,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(1);
            PArguments.setArgument(arguments, 0, self);
            return (long) BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // intentionally lazy
    abstract static class CallSlotHashFunPythonNode extends Node {
        abstract long execute(VirtualFrame frame, TpSlotPythonSingle slot, Object obj);

        @Specialization
        static long doIt(VirtualFrame frame, TpSlotPythonSingle slot, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached UnaryPythonSlotDispatcherNode dispatcherNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsLongAndOverflowNode asLongNode,
                        @Cached IntBuiltins.HashNode intHashNode,
                        @Cached InlinedConditionProfile minusOneProfile,
                        @Cached PRaiseNode raiseNode) {
            // See CPython: slot_sq_length
            Object callable = slot.getCallable();
            if (callable == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.UNHASHABLE_TYPE_P, self);
            }
            Object result = dispatcherNode.execute(frame, inliningTarget, callable, slot.getType(), self);
            if (!longCheckNode.execute(inliningTarget, result)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.HASH_SHOULD_RETURN_INTEGER);
            }
            /*
             * CPython transforms to Py_hash_t using PyLong_AsSsize_t. For us the equivalent is Java
             * long. In any case: our transformation must preserve values that already lie within
             * this range, to ensure that if x.__hash__() returns hash(y) then hash(x) == hash(y)
             */
            long hash;
            try {
                hash = asLongNode.execute(frame, inliningTarget, result);
            } catch (OverflowException e) {
                /*
                 * CPython note: res was not within the range of a Py_hash_t, so we're free to use
                 * any sufficiently bit-mixing transformation; long.__hash__ will do nicely.
                 */
                return intHashNode.executeLong(frame, result);
            }
            /* -1 is reserved for errors. */
            if (minusOneProfile.profile(inliningTarget, hash == -1)) {
                return -2;
            }
            return hash;
        }
    }
}
