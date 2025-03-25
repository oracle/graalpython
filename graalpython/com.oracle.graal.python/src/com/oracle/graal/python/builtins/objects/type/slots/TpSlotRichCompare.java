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

import static com.oracle.graal.python.builtins.objects.type.slots.BuiltinSlotWrapperSignature.J_DOLLAR_SELF;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_TP_RICHCOMPARE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_TP_RICHCOMPARE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinBase;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

public abstract class TpSlotRichCompare {

    private TpSlotRichCompare() {
    }

    public abstract static sealed class TpSlotRichCmpBuiltin<T extends RichCmpBuiltinNode>
                    extends TpSlotBuiltinBase<T> permits TpSlotRichCmpBuiltinSimple, TpSlotRichCmpBuiltinComplex {
        static final BuiltinSlotWrapperSignature SIGNATURE = BuiltinSlotWrapperSignature.of(J_DOLLAR_SELF, "other", "op");

        protected TpSlotRichCmpBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, SIGNATURE, PExternalFunctionWrapper.BINARYFUNC);
        }

        final RichCmpBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            RichCmpOp op = RichCmpOp.fromName(tsName);
            assert op != null : "Unexpected richcmp name: " + tsName.toJavaStringUncached();
            var factory = WrapperNodeFactory.wrap(getNodeFactory(), RichCmpWrapperNode.class, n -> new RichCmpWrapperNode(op, n));
            return createBuiltin(core, type, tsName, BuiltinSlotWrapperSignature.BINARY, wrapper, factory);
        }
    }

    public abstract static non-sealed class TpSlotRichCmpBuiltinSimple<T extends RichCmpBuiltinNode> extends TpSlotRichCmpBuiltin<T> {
        protected TpSlotRichCmpBuiltinSimple(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        protected abstract Object executeUncached(Object a, Object b, RichCmpOp op);

        @Override
        public final void initialize(PythonLanguage language) {
            // nop: we do not need a call target
        }
    }

    public abstract static non-sealed class TpSlotRichCmpBuiltinComplex<T extends RichCmpBuiltinNode> extends TpSlotRichCmpBuiltin<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotRichCmpBuiltinComplex(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        @Override
        public final void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, SIGNATURE, getNodeFactory(), "tp_richcompare");
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class RichCmpBuiltinNode extends PythonTernaryBuiltinNode {
        @Override
        public final Object execute(VirtualFrame frame, Object arg, Object arg2, Object arg3) {
            return execute(frame, arg, arg2, (RichCmpOp) arg3);
        }

        public abstract Object execute(VirtualFrame frame, Object a, Object b, RichCmpOp op);
    }

    public static final class RichCmpWrapperNode extends PythonBinaryBuiltinNode {
        private final RichCmpOp op;
        @Child RichCmpBuiltinNode slotNode;

        public RichCmpWrapperNode(RichCmpOp op, RichCmpBuiltinNode slotNode) {
            this.op = op;
            this.slotNode = slotNode;
        }

        @Override
        public Object execute(VirtualFrame frame, Object arg, Object arg2) {
            return slotNode.execute(frame, arg, arg2, op);
        }
    }

    public static final class TpSlotRichCmpPython extends TpSlotPython {
        private final TruffleWeakReference<Object> lt;
        private final TruffleWeakReference<Object> le;
        private final TruffleWeakReference<Object> eq;
        private final TruffleWeakReference<Object> ne;
        private final TruffleWeakReference<Object> gt;
        private final TruffleWeakReference<Object> ge;
        private final TruffleWeakReference<Object> type;

        public TpSlotRichCmpPython(Object type, Object lt, Object le, Object eq, Object ne, Object gt, Object ge) {
            this.type = asWeakRef(type);
            this.lt = asWeakRef(lt);
            this.le = asWeakRef(le);
            this.eq = asWeakRef(eq);
            this.ne = asWeakRef(ne);
            this.gt = asWeakRef(gt);
            this.ge = asWeakRef(ge);
        }

        public static TpSlotRichCmpPython create(Object[] callables, TruffleString[] callableNames, Object type) {
            assert callables.length == RichCmpOp.VALUES.length;
            assert callableNames == null || checkCallableNames(callableNames);
            return new TpSlotRichCmpPython(type, callables[0], callables[1], callables[2], callables[3], callables[4], callables[5]);
        }

        private static boolean checkCallableNames(TruffleString[] callableNames) {
            for (int i = 0; i < RichCmpOp.VALUES.length; i++) {
                assert RichCmpOp.VALUES[i].getPythonName().equalsUncached(callableNames[i], TS_ENCODING);
            }
            return true;
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            return new TpSlotRichCmpPython(type.get(), lt.get(), le.get(), eq.get(), ne.get(), gt.get(), ge.get());
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotRichCmpNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, J_TP_RICHCOMPARE);

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object a, Object b, RichCmpOp op);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotRichCmpBuiltin<?> slot, Object a, Object b, RichCmpOp op,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotRichCmpBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") RichCmpBuiltinNode slotNode) {
            return slotNode.execute(frame, a, b, op);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotRichCmpPython slot, Object a, Object b, RichCmpOp op,
                        @Cached BinaryPythonSlotDispatcherNode dispatcher,
                        @Cached InlinedBranchProfile notImplementedProfile) {
            TruffleWeakReference<Object> callableRef = switch (op) {
                case Py_LT -> slot.lt;
                case Py_LE -> slot.le;
                case Py_EQ -> slot.eq;
                case Py_NE -> slot.ne;
                case Py_GT -> slot.gt;
                case Py_GE -> slot.ge;
            };
            Object callable = slot.safeGet(callableRef);
            Object type = slot.safeGet(slot.type);
            if (callable != null && type != null) {
                return dispatcher.executeIgnoreDescriptorBindErrors(frame, inliningTarget, callable, type, a, b);
            } else {
                notImplementedProfile.enter(inliningTarget);
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object a, Object b, RichCmpOp op,
                        @Exclusive @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNodeA,
                        @Cached(inline = false) PythonToNativeNode toNativeNodeB,
                        @Exclusive @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Exclusive @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Exclusive @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T_TP_RICHCOMPARE, slot.callable, toNativeNodeA.execute(a), toNativeNodeB.execute(b),
                            op.asNative());
            return checkResultNode.execute(state, T___HASH__, toPythonNode.execute(result));
        }

        @Specialization(replaces = "callCachedBuiltin")
        @TruffleBoundary
        static Object callGenericSimpleBuiltin(TpSlotRichCmpBuiltinSimple<?> slot, Object a, Object b, RichCmpOp op) {
            return slot.executeUncached(a, b, op);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotRichCmpBuiltinComplex<?> slot, Object a, Object b, RichCmpOp op,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, a);
            PArguments.setArgument(arguments, 1, b);
            PArguments.setArgument(arguments, 2, op);
            return BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
