/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_MAPPING_CHECK;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_MAPPING_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_OBJECT_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.ints.PInt.intValue;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_ISNT_MAPPING;
import static com.oracle.graal.python.nodes.ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TupleNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.AbsNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.DivModNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.NextNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ItemsNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.KeysNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ValuesNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.lib.PySequenceIterSearchNode;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextAbstractBuiltins {

    /////// PyIndex ///////

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyIndex_Check extends CApiUnaryBuiltinNode {
        @Specialization
        Object check(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode checkNode) {
            return checkNode.execute(inliningTarget, obj) ? 1 : 0;
        }
    }

    /////// PyNumber ///////

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Check extends CApiUnaryBuiltinNode {
        @Specialization
        Object check(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberCheckNode checkNode) {
            return PInt.intValue(checkNode.execute(inliningTarget, obj));
        }
    }

    @CApiBuiltin(name = "_PyNumber_Index", ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Index extends CApiUnaryBuiltinNode {
        @Specialization
        static Object index(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkNonNullArg(inliningTarget, obj, raiseNode);
            return indexNode.execute(null, inliningTarget, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Long extends CApiUnaryBuiltinNode {

        @Specialization
        static int nlong(int i) {
            return i;
        }

        @Specialization
        static long nlong(long i) {
            return i;
        }

        @Fallback
        Object nlong(Object obj,
                        @Cached BuiltinConstructors.IntNode intNode) {
            return intNode.executeWith(null, obj, PNone.NO_VALUE);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Absolute extends CApiUnaryBuiltinNode {
        @Specialization
        Object abs(Object obj,
                        @Cached AbsNode absNode) {
            return absNode.execute(null, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyNumber_Divmod extends CApiBinaryBuiltinNode {
        @Specialization
        Object div(Object a, Object b,
                        @Cached DivModNode divNode) {
            return divNode.execute(null, a, b);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Direct)
    abstract static class PyNumber_ToBase extends CApiBinaryBuiltinNode {
        @Specialization(guards = "base == 2")
        Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached BinNode binNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            return binNode.execute(null, i);
        }

        @Specialization(guards = "base == 8")
        Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode) {
            return octNode.execute(null, n);
        }

        @Specialization(guards = "base == 10")
        Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached StrNode strNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            if (i instanceof Boolean) {
                i = ((boolean) i) ? 1 : 0;
            }
            return strNode.executeWith(i);
        }

        @Specialization(guards = "base == 16")
        Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Cached PyNumber_Index indexNode,
                        @Cached HexNode hexNode) {
            Object i = indexNode.execute(n);
            return hexNode.execute(null, i);
        }

        @Specialization(guards = "!checkBase(base)")
        static Object toBase(@SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BASE_MUST_BE);
        }

        protected boolean checkBase(int base) {
            return base == 2 || base == 8 || base == 10 || base == 16;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Float extends CApiUnaryBuiltinNode {

        @Specialization
        static double doDoubleNativeWrapper(double object) {
            return object;
        }

        @Specialization
        static double doLongNativeWrapper(long object) {
            return object;
        }

        @Specialization
        Object doGeneric(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            return pyNumberFloat.execute(inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Ignored)
    abstract static class PyTruffleNumber_UnaryOp extends CApiBinaryBuiltinNode {
        static int MAX_CACHE_SIZE = UnaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(Object left, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode) {
            return callNode.executeCached(null, left);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_unaryop}.
         */
        static UnaryOpNode createCallNode(int op) {
            UnaryArithmetic unaryArithmetic;
            switch (op) {
                case 0:
                    unaryArithmetic = UnaryArithmetic.Pos;
                    break;
                case 1:
                    unaryArithmetic = UnaryArithmetic.Neg;
                    break;
                case 2:
                    unaryArithmetic = UnaryArithmetic.Invert;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid unary operator");
            }
            return unaryArithmetic.create();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, Int}, call = Ignored)
    abstract static class PyTruffleNumber_BinOp extends CApiTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = BinaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode) {
            return callNode.executeObject(null, left, right);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static BinaryOpNode createCallNode(int op) {
            return getBinaryArithmetic(op).create();
        }

        private static BinaryArithmetic getBinaryArithmetic(int op) {
            switch (op) {
                case 0:
                    return BinaryArithmetic.Add;
                case 1:
                    return BinaryArithmetic.Sub;
                case 2:
                    return BinaryArithmetic.Mul;
                case 3:
                    return BinaryArithmetic.TrueDiv;
                case 4:
                    return BinaryArithmetic.LShift;
                case 5:
                    return BinaryArithmetic.RShift;
                case 6:
                    return BinaryArithmetic.Or;
                case 7:
                    return BinaryArithmetic.And;
                case 8:
                    return BinaryArithmetic.Xor;
                case 9:
                    return BinaryArithmetic.FloorDiv;
                case 10:
                    return BinaryArithmetic.Mod;
                case 12:
                    return BinaryArithmetic.MatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, Int}, call = Ignored)
    abstract static class PyTruffleNumber_InPlaceBinOp extends CApiTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = InplaceArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode) {
            return callNode.execute(null, left, right);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static LookupAndCallInplaceNode createCallNode(int op) {
            return getInplaceArithmetic(op).create();
        }

        private static InplaceArithmetic getInplaceArithmetic(int op) {
            switch (op) {
                case 0:
                    return InplaceArithmetic.IAdd;
                case 1:
                    return InplaceArithmetic.ISub;
                case 2:
                    return InplaceArithmetic.IMul;
                case 3:
                    return InplaceArithmetic.ITrueDiv;
                case 4:
                    return InplaceArithmetic.ILShift;
                case 5:
                    return InplaceArithmetic.IRShift;
                case 6:
                    return InplaceArithmetic.IOr;
                case 7:
                    return InplaceArithmetic.IAnd;
                case 8:
                    return InplaceArithmetic.IXor;
                case 9:
                    return InplaceArithmetic.IFloorDiv;
                case 10:
                    return InplaceArithmetic.IMod;
                case 12:
                    return InplaceArithmetic.IMatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyNumber_InPlacePower extends CApiTernaryBuiltinNode {
        @Child private LookupAndCallInplaceNode callNode;

        @Specialization(guards = {"o1.isIntLike()", "o2.isIntLike()", "o3.isIntLike()"})
        Object doIntLikePrimitiveWrapper(PrimitiveNativeWrapper o1, PrimitiveNativeWrapper o2, PrimitiveNativeWrapper o3) {
            return ensureCallNode().executeTernary(null, o1.getLong(), o2.getLong(), o3.getLong());
        }

        @Specialization(replaces = "doIntLikePrimitiveWrapper")
        Object doGeneric(Object o1, Object o2, Object o3) {
            return ensureCallNode().executeTernary(null, o1, o2, o3);
        }

        private LookupAndCallInplaceNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(InplaceArithmetic.IPow.create());
            }
            return callNode;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyNumber_Power extends CApiTernaryBuiltinNode {
        @Child private LookupAndCallTernaryNode callNode;

        @Specialization
        Object doGeneric(Object o1, Object o2, Object o3) {
            return ensureCallNode().execute(null, o1, o2, o3);
        }

        private LookupAndCallTernaryNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(TernaryArithmetic.Pow.create());
            }
            return callNode;
        }
    }

    /////// PySequence ///////

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PySequence_Tuple extends CApiUnaryBuiltinNode {

        @Specialization
        Object values(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached TupleNode tupleNode,
                        @Cached GetClassNode getClassNode) {
            if (getClassNode.execute(inliningTarget, obj) == PythonBuiltinClassType.PTuple) {
                return obj;
            } else {
                return tupleNode.execute(null, PythonBuiltinClassType.PTuple, obj);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PySequence_List extends CApiUnaryBuiltinNode {
        @Specialization
        Object values(Object obj,
                        @Cached ConstructListNode listNode) {
            return listNode.execute(null, obj);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObject}, call = Direct)
    public abstract static class PySequence_SetItem extends CApiTernaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(inliningTarget, obj)")
        static Object setItem(Object obj, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached ConditionProfile hasSetItem,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object setItemCallable = lookupAttrNode.execute(null, inliningTarget, obj, T___SETITEM__);
            if (hasSetItem.profile(setItemCallable == PNone.NO_VALUE)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, obj);
            } else {
                callNode.execute(setItemCallable, key, value);
                return 0;
            }
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, obj)")
        static Object setItem(Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PySequence_GetSlice extends CApiTernaryBuiltinNode {

        @Specialization(guards = "checkNode.execute(inliningTarget, obj)")
        static Object getSlice(Object obj, long iLow, long iHigh,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode) {
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, obj, T___GETITEM__);
            return callNode.execute(getItemCallable, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE));
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, obj)")
        static Object getSlice(Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_IS_UNSLICEABLE, obj);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_Contains extends CApiBinaryBuiltinNode {

        @Specialization
        static int contains(Object haystack, Object needle,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            return PInt.intValue(containsNode.execute(null, inliningTarget, haystack, needle));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PySequence_Repeat extends CApiBinaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(inliningTarget, obj)")
        Object repeat(Object obj, long n,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("check") @Cached PySequenceCheckNode checkNode,
                        @Cached("createMul()") MulNode mulNode) {
            return mulNode.executeObject(null, obj, n);
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, obj)")
        static Object repeat(Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("check") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PySequence_InPlaceRepeat extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(inliningTarget, obj)"})
        static Object repeat(Object obj, long n,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createMul()") MulNode mulNode,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode) {
            Object imulCallable = lookupNode.execute(null, inliningTarget, obj, T___IMUL__);
            if (imulCallable != PNone.NO_VALUE) {
                Object ret = callNode.execute(imulCallable, n);
                return ret;
            }
            return mulNode.executeObject(null, obj, n);
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, obj)")
        static Object repeat(Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_Concat extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(inliningTarget, s1)", "checkNode.execute(inliningTarget, s1)"})
        Object concat(Object s1, Object s2,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("check") @Cached PySequenceCheckNode checkNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode) {
            return addNode.executeObject(null, s1, s2);
        }

        @Specialization(guards = {"!checkNode.execute(inliningTarget, s1) || checkNode.execute(inliningTarget, s2)"})
        static Object cantConcat(Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("check") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_InPlaceConcat extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"checkNode.execute(inliningTarget, s1)"})
        static Object concat(Object s1, Object s2,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode) {
            Object iaddCallable = lookupNode.execute(null, inliningTarget, s1, T___IADD__);
            if (iaddCallable != PNone.NO_VALUE) {
                return callNode.execute(iaddCallable, s2);
            }
            return addNode.executeObject(null, s1, s2);
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, s1)")
        static Object concat(Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PySequence_DelItem extends CApiBinaryBuiltinNode {
        @Specialization
        static Object run(Object o, Object i,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectDelItem delItemNode) {
            delItemNode.execute(null, inliningTarget, o, i);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PySequence_Check extends CApiUnaryBuiltinNode {
        @Specialization
        static int check(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceCheckNode check) {
            if (object == PNone.NO_VALUE) {
                return intValue(false);
            }
            return intValue(check.execute(inliningTarget, object));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PySequence_GetItem extends CApiBinaryBuiltinNode {
        @Specialization
        Object doManaged(Object delegate, long position,
                        @Cached PySequenceGetItemNode getItemNode) {
            if ((int) position != position) {
                throw PRaiseNode.raiseUncached(this, OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, position);
            }
            return getItemNode.execute(null, delegate, (int) position);
        }
    }

    @CApiBuiltin(name = "PySequence_Length", ret = Py_ssize_t, args = {PyObject}, call = Direct)
    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PySequence_Size extends CApiUnaryBuiltinNode {

        // cant use PySequence_Size: PySequence_Size returns the __len__ value also for
        // subclasses of types not accepted by PySequence_Check as long they have an overriden
        // __len__ method
        @Specialization(guards = "!isNativeObject(obj)")
        static Object doSequence(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached IsSameTypeNode isSameType,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (obj instanceof PMappingproxy || isSameType.execute(inliningTarget, getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PDict)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, obj);
            } else {
                return sizeNode.execute(null, inliningTarget, obj);
            }
        }

        @Specialization(guards = "isNativeObject(obj)")
        Object doNative(Object obj,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction) {
            Object state = IndirectCallContext.enter(null, this);
            try {
                return callCapiFunction.call(FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE, toSulongNode.execute(obj));
            } finally {
                IndirectCallContext.exit(null, this, state);
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, Py_ssize_t, PyObject}, call = Direct)
    abstract static class PySequence_SetSlice extends CApiQuaternaryBuiltinNode {
        @Specialization
        static int setSlice(Object sequence, Object iLow, Object iHigh, Object s,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(SetItem)") LookupAndCallTernaryNode setItemNode,
                        @Cached PySliceNew sliceNode) {
            setItemNode.execute(null, sequence, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE), s);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PySequence_DelSlice extends CApiTernaryBuiltinNode {
        @Specialization
        static int setSlice(Object sequence, Object iLow, Object iHigh,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(DelItem)") LookupAndCallBinaryNode delItemNode,
                        @Cached PySliceNew sliceNode) {
            delItemNode.executeObject(null, sequence, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE));
            return 0;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_Count extends CApiBinaryBuiltinNode {

        @Specialization
        static int contains(Object haystack, Object needle,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceIterSearchNode searchNode) {
            return searchNode.execute(inliningTarget, haystack, needle, PySequenceIterSearchNode.PY_ITERSEARCH_COUNT);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_Index extends CApiBinaryBuiltinNode {

        @Specialization
        static int contains(Object haystack, Object needle,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceIterSearchNode searchNode) {
            return searchNode.execute(inliningTarget, haystack, needle, PySequenceIterSearchNode.PY_ITERSEARCH_INDEX);
        }
    }

    /////// PyObject ///////

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    @CApiBuiltin(name = "PyTruffleObject_GetItemString", ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyObject_GetItem extends CApiBinaryBuiltinNode {
        @Specialization
        Object doManaged(Object list, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetItem getItem) {
            return getItem.execute(null, inliningTarget, list, key);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyObject_Size extends CApiUnaryBuiltinNode {

        @Specialization(guards = "!isNativeObject(obj)")
        static int doGenericUnboxed(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode) {
            // TODO: theoretically, it is legal for __LEN__ to return a PythonNativeVoidPtr,
            // which is not handled in c.o.g.p.lib.PyObjectSizeNode at this point
            return sizeNode.execute(null, inliningTarget, obj);
        }

        @Specialization(guards = {"isNativeObject(obj)"})
        Object size(Object obj,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction) {
            Object state = IndirectCallContext.enter(null, this);
            try {
                return callCapiFunction.call(FUN_PY_TRUFFLE_PY_OBJECT_SIZE, toSulongNode.execute(obj));
            } finally {
                IndirectCallContext.exit(null, this, state);
            }
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyObject_LengthHint extends CApiBinaryBuiltinNode {

        @Specialization
        static long doGenericUnboxed(Object obj, long defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength getLength) {
            int len = getLength.execute(null, inliningTarget, obj);
            if (len == -1) {
                return defaultValue;
            }
            return len;
        }
    }

    /////// PyMapping ///////

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Keys extends CApiUnaryBuiltinNode {
        @Specialization
        Object keys(PDict obj,
                        @Cached KeysNode keysNode,
                        @Cached ConstructListNode listNode) {
            return listNode.execute(null, keysNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        Object keys(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode) {
            return getKeys(null, inliningTarget, obj, getAttrNode, callNode, listNode);
        }

    }

    private static PList getKeys(VirtualFrame frame, Node inliningTarget, Object obj, PyObjectGetAttr getAttrNode, CallNode callNode, ConstructListNode listNode) {
        Object attr = getAttrNode.execute(frame, inliningTarget, obj, T_KEYS);
        return listNode.execute(frame, callNode.execute(frame, attr));
    }

    @CApiBuiltin(name = "PyDict_Items", ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Items extends CApiUnaryBuiltinNode {
        @Specialization
        static Object items(PDict obj,
                        @Cached ItemsNode itemsNode,
                        @Cached ConstructListNode listNode) {
            return listNode.execute(null, itemsNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        static Object items(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode) {
            Object attr = getAttrNode.execute(inliningTarget, obj, T_ITEMS);
            return listNode.execute(null, callNode.execute(attr));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Values extends CApiUnaryBuiltinNode {
        @Specialization
        static Object values(PDict obj,
                        @Cached ConstructListNode listNode,
                        @Cached ValuesNode valuesNode) {
            return listNode.execute(null, valuesNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        static Object values(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkNonNullArg(inliningTarget, obj, raiseNode);
            Object attr = getAttrNode.execute(inliningTarget, obj, T_VALUES);
            return listNode.execute(null, callNode.execute(attr));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Check extends CApiUnaryBuiltinNode {

        @Specialization(guards = "!isNativeObject(object)")
        static int doPythonObject(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyMappingCheckNode checkNode) {
            return intValue(checkNode.execute(inliningTarget, object));
        }

        @Specialization(guards = "isNativeObject(obj)")
        static Object doNative(Object obj,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction) {
            return callCapiFunction.call(FUN_PY_TRUFFLE_PY_MAPPING_CHECK, toSulongNode.execute(obj));
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Size extends CApiUnaryBuiltinNode {

        // cant use PyMapping_Check: PyMapping_Size returns the __len__ value also for
        // subclasses of types not accepted by PyMapping_Check as long they have an overriden
        // __len__ method
        @Specialization(guards = "!isNativeObject(obj)")
        static int doMapping(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode,
                        @Cached IsSameTypeNode isSameType,
                        @Cached GetClassNode getClassNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object cls = getClassNode.execute(inliningTarget, obj);
            if (isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PSet) ||
                            isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PFrozenSet) ||
                            isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PDeque)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, OBJ_ISNT_MAPPING, obj);
            } else {
                return sizeNode.execute(null, inliningTarget, obj);
            }
        }

        @Specialization(guards = "isNativeObject(obj)")
        static Object doNative(Object obj,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction) {
            return callCapiFunction.call(FUN_PY_TRUFFLE_PY_MAPPING_SIZE, toSulongNode.execute(obj));
        }
    }

    /////// PyIter ///////

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyIter_Next extends CApiUnaryBuiltinNode {
        @Specialization
        Object check(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached NextNode nextNode,
                        @Cached IsBuiltinObjectProfile isClassProfile) {
            try {
                return nextNode.execute(null, object, PNone.NO_VALUE);
            } catch (PException e) {
                if (isClassProfile.profileException(inliningTarget, e, PythonBuiltinClassType.StopIteration)) {
                    return getNativeNull();
                } else {
                    throw e;
                }
            }
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObject}, call = Direct)
    abstract static class PyObject_GetDoc extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached AsCharPointerNode asCharPointerNode) {
            try {
                Object doc = lookupAttr.execute(null, inliningTarget, obj, T___DOC__);
                if (!(doc instanceof PNone)) {
                    return asCharPointerNode.execute(doc);
                }
            } catch (PException e) {
                // ignore
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyObject_SetDoc extends CApiBinaryBuiltinNode {
        @Specialization
        static int set(PBuiltinFunction obj, TruffleString value,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode write) {
            write.execute(obj, T___DOC__, value);
            return 1;
        }

        @Specialization
        static int set(PBuiltinMethod obj, TruffleString value,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode write) {
            set(obj.getBuiltinFunction(), value, write);
            return 1;
        }

        @Specialization
        static int set(GetSetDescriptor obj, TruffleString value,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode write) {
            write.execute(obj, T___DOC__, value);
            return 1;
        }

        @Specialization(guards = "isType.execute(inliningTarget, type)", limit = "1")
        static int set(PythonNativeClass type, TruffleString value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsTypeNode isType,
                        // TODO we should write to tp_doc, this writes to __doc__ in the type dict
                        @Cached("createForceType()") WriteAttributeToObjectNode write) {
            write.execute(type, T___DOC__, value);
            return 1;
        }

        @Fallback
        @SuppressWarnings("unused")
        static int set(Object obj, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("Don't know how to set doc for " + obj.getClass());
        }
    }
}
