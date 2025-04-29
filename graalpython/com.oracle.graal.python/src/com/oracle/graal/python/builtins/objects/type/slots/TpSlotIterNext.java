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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ClearCurrentExceptionNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryPythonNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotIterNext {
    private TpSlotIterNext() {
    }

    /** Equivalent of {@code _PyObject_NextNotImplemented} */
    public static final TpSlot NEXT_NOT_IMPLEMENTED = TpSlotIterNextSlotsGen.SLOTS.tp_iternext();

    public abstract static class TpSlotIterNextBuiltin<T extends PythonUnaryBuiltinNode> extends TpSlotBuiltin<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotIterNextBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final PythonUnaryBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public final void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, BuiltinSlotWrapperSignature.UNARY, getNodeFactory(), J___NEXT__);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            var factory = new WrapperNodeFactory<>(getNodeFactory(), TpIterNextBuiltinWrapperNode.class, TpIterNextBuiltinWrapperNode::new);
            return createBuiltin(core, type, tsName, BuiltinSlotWrapperSignature.UNARY, wrapper, factory);
        }
    }

    public static final class TpIterNextBuiltinWrapperNode extends PythonUnaryBuiltinNode {
        @Child PythonUnaryBuiltinNode delegate;

        public TpIterNextBuiltinWrapperNode(PythonUnaryBuiltinNode delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object execute(VirtualFrame frame, Object arg) {
            try {
                return delegate.execute(frame, arg);
            } catch (IteratorExhausted e) {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.StopIteration);
            }
        }
    }

    /**
     * Convenience base class for {@code tp_iternext} builtin implementations.
     * {@code PythonUnaryBuiltinNode} can be used instead if necessitated by existing inheritance
     * hierarchy.
     */
    @GenerateInline(value = false, inherit = true)
    public abstract static class TpIterNextBuiltin extends PythonUnaryBuiltinNode {
        /**
         * Raise a control flow exception that signals iterator exhaustion. A faster equivalent to
         * raising {@code StopIteration} python exception. Should not be used outside of
         * {@code tp_iternext} implementations.
         */
        public static IteratorExhausted iteratorExhausted() {
            throw IteratorExhausted.INSTANCE;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class NextNotImplementedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object error(Object iterable,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, iterable);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotTpIterNextNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "iternext");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotIterNextBuiltin<?> slot, Object self,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotIterNextBuiltin<?> cachedSlot,
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
                        @Cached ClearCurrentExceptionNode clearCurrentExceptionNode) {
            PythonThreadState state = getThreadStateNode.execute(inliningTarget);
            Object nativeResult = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___NEXT__, slot.callable,
                            toNativeNode.execute(self));
            Object pythonResult = toPythonNode.execute(nativeResult);
            if (pythonResult == PNone.NO_VALUE) {
                if (state.getCurrentException() != null) {
                    throw clearCurrentExceptionNode.getCurrentExceptionForReraise(inliningTarget, state);
                } else {
                    throw TpIterNextBuiltin.iteratorExhausted();
                }
            }
            return pythonResult;
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotIterNextBuiltin<?> slot, Object self,
                        @Cached CallDispatchers.SimpleIndirectInvokeNode invoke) {
            Object[] arguments = PArguments.create(1);
            PArguments.setArgument(arguments, 0, self);
            RootCallTarget callTarget = PythonLanguage.get(inliningTarget).getBuiltinSlotCallTarget(slot.callTargetIndex);
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }
}
