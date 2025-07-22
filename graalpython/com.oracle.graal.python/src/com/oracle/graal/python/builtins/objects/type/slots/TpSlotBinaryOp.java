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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RLSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import java.util.Arrays;

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
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PMethodBase;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotBinaryFuncBuiltin;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/**
 * Slots that have the {@code __xxx__} and {@code __rxxx__} semantics in the Python wrapper. We
 * reuse some of the machinery of {@link TpSlotBinaryFunc}.
 */
public class TpSlotBinaryOp {
    private TpSlotBinaryOp() {
    }

    public enum ReversibleSlot {
        NB_ADD(T___ADD__, T___RADD__),
        NB_SUBTRACT(T___SUB__, T___RSUB__),
        NB_MULTIPLY(T___MUL__, T___RMUL__),
        NB_REMAINDER(T___MOD__, T___RMOD__),
        NB_LSHIFT(T___LSHIFT__, T___RLSHIFT__),
        NB_RSHIFT(T___RSHIFT__, T___RRSHIFT__),
        NB_AND(T___AND__, T___RAND__),
        NB_XOR(T___XOR__, T___RXOR__),
        NB_OR(T___OR__, T___ROR__),
        NB_FLOOR_DIVIDE(T___FLOORDIV__, T___RFLOORDIV__),
        NB_TRUE_DIVIDE(T___TRUEDIV__, T___RTRUEDIV__),
        NB_DIVMOD(T___DIVMOD__, T___RDIVMOD__),
        NB_MATRIX_MULTIPLY(T___MATMUL__, T___RMATMUL__),
        NB_POWER_BINARY(T___POW__, T___RPOW__);

        private static final ReversibleSlot[] VALUES = values();
        private final TruffleString name;
        private final TruffleString rname;

        ReversibleSlot(TruffleString name, TruffleString rname) {
            this.name = name;
            this.rname = rname;
        }

        public TpSlot getSlotValue(TpSlots slots) {
            // switch instead of using TpSlotMeta for better inlining on fast-path
            return switch (this) {
                case NB_ADD -> slots.nb_add();
                case NB_SUBTRACT -> slots.nb_subtract();
                case NB_MULTIPLY -> slots.nb_multiply();
                case NB_REMAINDER -> slots.nb_remainder();
                case NB_LSHIFT -> slots.nb_lshift();
                case NB_RSHIFT -> slots.nb_rshift();
                case NB_AND -> slots.nb_and();
                case NB_XOR -> slots.nb_xor();
                case NB_OR -> slots.nb_or();
                case NB_FLOOR_DIVIDE -> slots.nb_floor_divide();
                case NB_TRUE_DIVIDE -> slots.nb_true_divide();
                case NB_DIVMOD -> slots.nb_divmod();
                case NB_MATRIX_MULTIPLY -> slots.nb_matrix_multiply();
                case NB_POWER_BINARY -> slots.nb_power();
            };
        }

        public static ReversibleSlot fromCallableNames(TruffleString[] names) {
            for (ReversibleSlot op : VALUES) {
                if (names[0].equals(op.name) && names[1].equals(op.rname)) {
                    return op;
                }
            }
            return null;
        }
    }

    public enum InplaceSlot {
        NB_INPLACE_ADD(ReversibleSlot.NB_ADD),
        NB_INPLACE_SUBTRACT(ReversibleSlot.NB_SUBTRACT),
        NB_INPLACE_MULTIPLY(ReversibleSlot.NB_MULTIPLY),
        NB_INPLACE_REMAINDER(ReversibleSlot.NB_REMAINDER),
        NB_INPLACE_LSHIFT(ReversibleSlot.NB_LSHIFT),
        NB_INPLACE_RSHIFT(ReversibleSlot.NB_RSHIFT),
        NB_INPLACE_AND(ReversibleSlot.NB_AND),
        NB_INPLACE_XOR(ReversibleSlot.NB_XOR),
        NB_INPLACE_OR(ReversibleSlot.NB_OR),
        NB_INPLACE_FLOOR_DIVIDE(ReversibleSlot.NB_FLOOR_DIVIDE),
        NB_INPLACE_TRUE_DIVIDE(ReversibleSlot.NB_TRUE_DIVIDE),
        NB_INPLACE_MATRIX_MULTIPLY(ReversibleSlot.NB_MATRIX_MULTIPLY);

        private final ReversibleSlot reversibleSlot;

        InplaceSlot(ReversibleSlot reversibleSlot) {
            this.reversibleSlot = reversibleSlot;
        }

        public TpSlot getSlotValue(TpSlots slots) {
            // switch instead of using TpSlotMeta for better inlining on fast-path
            return switch (this) {
                case NB_INPLACE_ADD -> slots.nb_inplace_add();
                case NB_INPLACE_SUBTRACT -> slots.nb_inplace_subtract();
                case NB_INPLACE_MULTIPLY -> slots.nb_inplace_multiply();
                case NB_INPLACE_REMAINDER -> slots.nb_inplace_remainder();
                case NB_INPLACE_LSHIFT -> slots.nb_inplace_lshift();
                case NB_INPLACE_RSHIFT -> slots.nb_inplace_rshift();
                case NB_INPLACE_AND -> slots.nb_inplace_and();
                case NB_INPLACE_XOR -> slots.nb_inplace_xor();
                case NB_INPLACE_OR -> slots.nb_inplace_or();
                case NB_INPLACE_FLOOR_DIVIDE -> slots.nb_inplace_floor_divide();
                case NB_INPLACE_TRUE_DIVIDE -> slots.nb_inplace_true_divide();
                case NB_INPLACE_MATRIX_MULTIPLY -> slots.nb_inplace_matrix_multiply();
            };
        }

        public ReversibleSlot getReversibleSlot() {
            return reversibleSlot;
        }
    }

    public abstract static class TpSlotBinaryOpBuiltin<T extends BinaryOpBuiltinNode> extends TpSlotBuiltin<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        private final String builtinName;

        protected TpSlotBinaryOpBuiltin(NodeFactory<T> nodeFactory, String builtinName) {
            super(nodeFactory);
            this.builtinName = builtinName;
        }

        final BinaryOpBuiltinNode createOpSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, BuiltinSlotWrapperSignature.BINARY, getNodeFactory(), builtinName);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case BINARYFUNC_L -> createBuiltin(core, type, tsName, BuiltinSlotWrapperSignature.BINARY, wrapper, getNodeFactory());
                case BINARYFUNC_R -> createRBuiltin(core, type, tsName);
                default -> null;
            };
        }

        private PBuiltinFunction createRBuiltin(Python3Core core, Object type, TruffleString tsName) {
            var factory = WrapperNodeFactory.wrap(getNodeFactory(), SwapArgumentsNode.class, SwapArgumentsNode::new);
            return createBuiltin(core, type, tsName, BuiltinSlotWrapperSignature.BINARY, PExternalFunctionWrapper.BINARYFUNC_R, factory);
        }
    }

    public abstract static class TpSlotBinaryIOpBuiltin<T extends PythonBinaryBuiltinNode> extends TpSlotBinaryFuncBuiltin<T> {

        protected TpSlotBinaryIOpBuiltin(NodeFactory<T> nodeFactory, String builtinName) {
            super(nodeFactory, PExternalFunctionWrapper.BINARYFUNC, builtinName);
        }
    }

    static final class SwapArgumentsNode extends PythonBinaryBuiltinNode {
        @Child private BinaryOpBuiltinNode wrapped;

        SwapArgumentsNode(BinaryOpBuiltinNode wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public Object execute(VirtualFrame frame, Object self, Object other) {
            return wrapped.execute(frame, other, self);
        }
    }

    /**
     * Slots representing "reversible" binary operations on the Python side do not have two
     * versions, instead they should be able to handle the situation when "left" operand is not the
     * object that "owns" the slot. See also {@link com.oracle.graal.python.lib.CallBinaryOp1Node}.
     */
    @GenerateInline(value = false, inherit = true)
    public abstract static class BinaryOpBuiltinNode extends PythonBinaryBuiltinNode {
    }

    public static final class TpSlotReversiblePython extends TpSlotPython {
        private final ReversibleSlot op;
        private final TruffleWeakReference<Object> left;
        private final TruffleWeakReference<Object> right;
        private final TruffleWeakReference<Object> type;

        public TpSlotReversiblePython(ReversibleSlot op, Object left, Object right, Object type) {
            this.op = op;
            this.left = asWeakRef(left);
            this.right = asWeakRef(right);
            this.type = new TruffleWeakReference<>(type);
        }

        public static TpSlotReversiblePython create(Object[] callables, TruffleString[] callableNames, Object type) {
            assert callables.length == 2;
            ReversibleSlot op = ReversibleSlot.fromCallableNames(callableNames);
            assert op != null : "Unexpected callable names: " + Arrays.toString(callableNames);
            return new TpSlotReversiblePython(op, callables[0], callables[1], type);
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object newLeft = LookupAttributeInMRONode.Dynamic.getUncached().execute(klass, op.name);
            Object newRight = LookupAttributeInMRONode.Dynamic.getUncached().execute(klass, op.rname);
            if (newLeft != getLeft() || newRight != getRight()) {
                return new TpSlotReversiblePython(op, newLeft, newRight, getType());
            }
            return this;
        }

        public Object getLeft() {
            return safeGet(left);
        }

        public Object getRight() {
            return safeGet(right);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotBinaryOpNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "binaryop");

        // Note: it is allowed that the slot does not belong to self, but in fact belongs to 'other'
        // I.e., one can call the slot on another object, native/builtin slots should deal with that
        // This happens in CallBinaryOp1Node (cpython:binary_op1)

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes,
                        ReversibleSlot op);

        @SuppressWarnings("unused")
        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, TpSlotBinaryOpBuiltin<?> slot, Object self,
                        Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes, ReversibleSlot op,
                        @Cached("slot") TpSlotBinaryOpBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createOpSlotNode()") BinaryOpBuiltinNode slotNode) {
            return slotNode.execute(frame, self, other);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, TpSlotReversiblePython slot, Object self, Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes, ReversibleSlot op,
                        @Cached(inline = false) CallReversiblePythonSlotNode callPython) {
            return callPython.execute(frame, slot, self, selfType, other, otherSlot, otherType, sameTypes, op);
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotCExtNative slot, Object self,
                        Object selfType, Object arg, TpSlot otherSlot, Object otherType, boolean sameTypes, ReversibleSlot op,
                        @Exclusive @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode selfToNativeNode,
                        @Cached(inline = false) PythonToNativeNode argToNativeNode,
                        @Exclusive @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Exclusive @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, op.name, slot.callable,
                            selfToNativeNode.execute(self), argToNativeNode.execute(arg));
            return checkResultNode.execute(state, op.name, toPythonNode.execute(result));
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericComplexBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotBinaryOpBuiltin<?> slot,
                        Object self, Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes, ReversibleSlot op,
                        @Cached CallDispatchers.SimpleIndirectInvokeNode invoke) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, other);
            RootCallTarget callTarget = PythonLanguage.get(inliningTarget).getBuiltinSlotCallTarget(slot.callTargetIndex);
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // intentional explicit "data-class"
    abstract static class CallReversiblePythonSlotNode extends Node {
        public abstract Object execute(VirtualFrame frame, TpSlotReversiblePython slot, Object self, Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes,
                        ReversibleSlot op);

        @Specialization
        static Object callPython(VirtualFrame frame, TpSlotReversiblePython slot, Object self, Object selfType, Object other, TpSlot otherSlot, Object otherType, boolean sameTypes, ReversibleSlot op,
                        @Bind Node inliningTarget,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetCachedTpSlotsNode getSelfSlotsNode,
                        @Cached RichCompareCallablesNotEqual neNode,
                        @Cached InlinedConditionProfile dispatchR1Profile,
                        @Cached InlinedConditionProfile dispatchLProfile,
                        @Cached InlinedConditionProfile dispatchR2Profile,
                        @Cached BinaryPythonSlotDispatcherNode dispatcherNode) {
            // Implements the logic of SLOT1BINFULL macro from CPython
            TpSlotReversiblePython otherSlotValue = null;
            boolean doOther = false;
            if (!sameTypes) {
                if (isSameReversibleWrapper(otherSlot, op)) {
                    otherSlotValue = (TpSlotReversiblePython) otherSlot;
                    doOther = true;
                }
            }

            TpSlots selfSlots = getSelfSlotsNode.execute(inliningTarget, selfType);
            TpSlot selfSlotValue = op.getSlotValue(selfSlots);
            // Note: the slot may be other's slot, not self's slot, so this test may not pass
            if (isSameReversibleWrapper(selfSlotValue, op)) {
                if (doOther && isSubtypeNode.execute(otherType, selfType)) {
                    if (methodIsOverloaded(frame, inliningTarget, slot, otherSlotValue, neNode)) {
                        Object result = dispatchIfAvailable(frame, inliningTarget, dispatchR1Profile, dispatcherNode, other, self, otherSlotValue.getRight(), otherSlotValue.getType());
                        if (result != PNotImplemented.NOT_IMPLEMENTED) {
                            return result;
                        }
                        doOther = false;
                    }
                }
                Object result = dispatchIfAvailable(frame, inliningTarget, dispatchLProfile, dispatcherNode, self, other, slot.getLeft(), slot.getType());
                if (result != PNotImplemented.NOT_IMPLEMENTED || sameTypes) {
                    return result;
                }
            }

            if (doOther) {
                return dispatchIfAvailable(frame, inliningTarget, dispatchR2Profile, dispatcherNode, other, self, otherSlotValue.getRight(), otherSlotValue.getType());
            }

            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private static Object dispatchIfAvailable(VirtualFrame frame, Node inliningTarget, InlinedConditionProfile profile,
                        BinaryPythonSlotDispatcherNode dispatcherNode, Object self, Object other, Object method, Object type) {
            if (profile.profile(inliningTarget, method != null)) {
                return dispatcherNode.execute(frame, inliningTarget, method, type, self, other);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        static boolean isSameReversibleWrapper(TpSlot s, ReversibleSlot op) {
            // Equivalent to CPython test: Py_TYPE(other)->tp_as_number->SLOTNAME == TESTFUNC
            // We have multiple wrappers, because we cache state (MRO lookups) in the wrapper too
            return s instanceof TpSlotReversiblePython p && p.op == op;
        }

        static boolean methodIsOverloaded(VirtualFrame frame, Node inliningTarget,
                        TpSlotReversiblePython leftSlot, TpSlotReversiblePython rightSlot, RichCompareCallablesNotEqual neNode) {
            // CPython uses _PyObject_LookupAttr(Py_TYPE(x), name) as opposed to
            // _PyType_Lookup(Py_TYPE(x), name). Moreover, we cache the MRO lookup results in the
            // slot, CPython doesn't, presumably because the slot cannot (easily) hold the extra
            // state. We do not use _PyObject_LookupAttr and use cached MRO lookups.
            // Possible differences :
            // - _PyObject_LookupAttr looks in metatype attributes
            // - metatype may override tp_getattro and have some custom logic
            // We can do fast-path for types whose metatype is type and generic path for others if
            // this turns out to be a problem
            // See also: https://bugs.python.org/issue623669 (__rdiv__ vs new-style classes)
            Object rightMethod = rightSlot.getRight();
            if (rightMethod == null) {
                /* If right doesn't have it, it's not overloaded */
                return false;
            }
            Object leftMethod = leftSlot.getRight();
            if (leftMethod == null) {
                /* If right has it but left doesn't, it's overloaded */
                return true;
            }
            return neNode.execute(frame, inliningTarget, leftMethod, rightMethod);
        }
    }

    // Fast-paths for common callables and fallback to PyObjectRichCompareBool.NeNode as per CPython
    // semantics
    @ImportStatic(PGuards.class)
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    protected abstract static class RichCompareCallablesNotEqual extends Node {
        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization(guards = "a == b")
        static boolean areIdenticalFastPath(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
            return false;
        }

        @Specialization(guards = "isNone(a) || isNone(b)")
        static boolean noneFastPath(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
            return a != b;
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doBuiltins(PBuiltinFunction a, PBuiltinFunction b) {
            return a != b;
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doFunctions(PFunction a, PFunction b) {
            return a != b;
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doMethods(PMethodBase a, PMethodBase b) {
            return a != b;
        }

        @Fallback
        static boolean doGenericRuntimeObjects(VirtualFrame frame, Node inliningTarget, Object a, Object b,
                        @Cached PyObjectRichCompareBool neNode) {
            return neNode.execute(frame, inliningTarget, a, b, RichCmpOp.Py_NE);
        }
    }
}
