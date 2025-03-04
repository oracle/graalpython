/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_doc;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SEND;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_ISNT_MAPPING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TupleNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.NextNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ItemsNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.KeysNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ValuesNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.CallSlotMpAssSubscriptNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAndNode;
import com.oracle.graal.python.lib.PyNumberDivmodNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAddNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAndNode;
import com.oracle.graal.python.lib.PyNumberInPlaceFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceLshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceOrNode;
import com.oracle.graal.python.lib.PyNumberInPlacePowerNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRemainderNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceSubtractNode;
import com.oracle.graal.python.lib.PyNumberInPlaceTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceXorNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyNumberLongNode;
import com.oracle.graal.python.lib.PyNumberLshiftNode;
import com.oracle.graal.python.lib.PyNumberMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberRshiftNode;
import com.oracle.graal.python.lib.PyNumberSubtractNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberXorNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PySequenceConcatNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PySequenceDelItemNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.lib.PySequenceInPlaceConcatNode;
import com.oracle.graal.python.lib.PySequenceInPlaceRepeatNode;
import com.oracle.graal.python.lib.PySequenceIterSearchNode;
import com.oracle.graal.python.lib.PySequenceSetItemNode;
import com.oracle.graal.python.lib.PySequenceSizeNode;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextAbstractBuiltins {

    /////// PyNumber ///////

    @CApiBuiltin(name = "_PyNumber_Index", ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Index extends CApiUnaryBuiltinNode {
        @Specialization
        static Object index(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PRaiseNode raiseNode) {
            checkNonNullArg(inliningTarget, obj, raiseNode);
            return indexNode.execute(null, inliningTarget, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyNumber_Long extends CApiUnaryBuiltinNode {

        @Specialization
        static Object nlong(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberLongNode pyNumberLongNode) {
            return pyNumberLongNode.execute(null, inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Direct)
    abstract static class PyNumber_ToBase extends CApiBinaryBuiltinNode {
        @Specialization(guards = "base == 2")
        static Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached BinNode binNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            return binNode.execute(null, i);
        }

        @Specialization(guards = "base == 8")
        static Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode) {
            return octNode.execute(null, n);
        }

        @Specialization(guards = "base == 10")
        static Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached StrNode strNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            if (i instanceof Boolean) {
                i = ((boolean) i) ? 1 : 0;
            }
            return strNode.executeWith(i);
        }

        @Specialization(guards = "base == 16")
        static Object toBase(Object n, @SuppressWarnings("unused") int base,
                        @Cached PyNumber_Index indexNode,
                        @Cached HexNode hexNode) {
            Object i = indexNode.execute(n);
            return hexNode.execute(null, i);
        }

        @Specialization(guards = "!checkBase(base)")
        static Object toBase(@SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, BASE_MUST_BE);
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
        static Object doGeneric(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            return pyNumberFloat.execute(inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Add extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Bind Node inliningTarget,
                        @Cached PyNumberAddNode addNode) {
            return addNode.execute(null, inliningTarget, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Subtract extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberSubtractNode subtractNode) {
            return subtractNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Multiply extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Bind Node inliningTarget,
                        @Cached PyNumberMultiplyNode multiplyNode) {
            return multiplyNode.execute(null, inliningTarget, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Remainder extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberRemainderNode remainderNode) {
            return remainderNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_TrueDivide extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberTrueDivideNode trueDivideNode) {
            return trueDivideNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_FloorDivide extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberFloorDivideNode floorDivideNode) {
            return floorDivideNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Divmod extends CApiBinaryBuiltinNode {
        @Specialization
        static Object div(Object a, Object b,
                        @Bind Node inliningTarget,
                        @Cached PyNumberDivmodNode divmodNode) {
            return divmodNode.execute(null, inliningTarget, a, b);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_And extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberAndNode andNode) {
            return andNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Or extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberOrNode orNode) {
            return orNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Xor extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberXorNode xorNode) {
            return xorNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Lshift extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberLshiftNode lshiftNode) {
            return lshiftNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Rshift extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberRshiftNode rshiftNode) {
            return rshiftNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_MatrixMultiply extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberMatrixMultiplyNode matrixMultiplyNode) {
            return matrixMultiplyNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceAdd extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceAddNode addNode) {
            return addNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceSubtract extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceSubtractNode subtractNode) {
            return subtractNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceMultiply extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceMultiplyNode multiplyNode) {
            return multiplyNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceRemainder extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceRemainderNode remainderNode) {
            return remainderNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceTrueDivide extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceTrueDivideNode trueDivideNode) {
            return trueDivideNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceFloorDivide extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceFloorDivideNode floorDivideNode) {
            return floorDivideNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceAnd extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceAndNode andNode) {
            return andNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceOr extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceOrNode orNode) {
            return orNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceXor extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceXorNode xorNode) {
            return xorNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceLshift extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceLshiftNode lshiftNode) {
            return lshiftNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceRshift extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceRshiftNode rshiftNode) {
            return rshiftNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlaceMatrixMultiply extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2,
                        @Cached PyNumberInPlaceMatrixMultiplyNode matrixMultiplyNode) {
            return matrixMultiplyNode.execute(null, o1, o2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_InPlacePower extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object o1, Object o2, Object o3,
                        @Cached PyNumberInPlacePowerNode powerNode) {
            return powerNode.execute(null, o1, o2, o3);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Ignored)
    abstract static class PyTrufflePyNumber_Power extends CApiTernaryBuiltinNode {

        @Specialization
        Object doGeneric(Object o1, Object o2, Object o3,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberPowerNode powerNode) {
            return powerNode.execute(null, inliningTarget, o1, o2, o3);
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

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, PyObject}, call = Ignored)
    public abstract static class PyTruffleSequence_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        static Object setItem(Object obj, long key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceSetItemNode setItemNode) {
            if ((int) key != key) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, key);
            }
            setItemNode.execute(null, inliningTarget, obj, (int) key, value);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PySequence_GetSlice extends CApiTernaryBuiltinNode {

        @Specialization(guards = "checkNode.execute(inliningTarget, obj)", limit = "1")
        static Object getSlice(Object obj, long iLow, long iHigh,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode) {
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, obj, T___GETITEM__);
            return callNode.executeWithoutFrame(getItemCallable, sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE));
        }

        @Specialization(guards = "!checkNode.execute(inliningTarget, obj)", limit = "1")
        static Object getSlice(Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Exclusive @Cached PySequenceCheckNode checkNode,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_IS_UNSLICEABLE, obj);
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
    abstract static class PySequence_InPlaceRepeat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object repeat(Object obj, long n,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PySequenceInPlaceRepeatNode repeat) {
            if (!PInt.isIntRange(n)) {
                throw raiseNode.raise(inliningTarget, OverflowError);
            }
            return repeat.execute(null, inliningTarget, obj, (int) n);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_Concat extends CApiBinaryBuiltinNode {
        @Specialization
        Object doIt(Object s1, Object s2,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceConcatNode pySeqConcat) {
            return pySeqConcat.execute(null, inliningTarget, s1, s2);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PySequence_InPlaceConcat extends CApiBinaryBuiltinNode {

        @Specialization
        static Object concat(Object s1, Object s2,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceInPlaceConcatNode concat) {
            return concat.execute(null, inliningTarget, s1, s2);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t}, call = Ignored)
    abstract static class PyTruffleSequence_DelItem extends CApiBinaryBuiltinNode {
        @Specialization
        static Object run(Object o, long i,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceDelItemNode delItemNode) {
            if ((int) i != i) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, i);
            }
            delItemNode.execute(null, inliningTarget, o, (int) i);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t}, call = Ignored)
    abstract static class PyTruffleSequence_GetItem extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doManaged(Object delegate, long position,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceGetItemNode getItemNode) {
            if ((int) position != position) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, position);
            }
            return getItemNode.execute(null, delegate, (int) position);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleSequence_Size extends CApiUnaryBuiltinNode {

        @Specialization
        static int doSequence(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceSizeNode sizeNode) {
            return sizeNode.execute(null, inliningTarget, obj);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, Py_ssize_t, PyObject}, call = Direct)
    abstract static class PySequence_SetSlice extends CApiQuaternaryBuiltinNode {
        @Specialization
        static int setSlice(Object sequence, Object iLow, Object iHigh, Object s,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached CallSlotMpAssSubscriptNode callSetItem,
                        @Cached PySliceNew sliceNode,
                        @Cached PRaiseNode raiseNode) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, sequence);
            if (slots.mp_ass_subscript() != null) {
                PSlice slice = sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE);
                callSetItem.execute(null, inliningTarget, slots.mp_ass_subscript(), sequence, slice, s);
                return 0;
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.P_OBJECT_DOESNT_SUPPORT_SLICE_ASSIGNMENT, sequence);
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PySequence_DelSlice extends CApiTernaryBuiltinNode {
        @Specialization
        static int setSlice(Object sequence, Object iLow, Object iHigh,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached CallSlotMpAssSubscriptNode callSetItem,
                        @Cached PySliceNew sliceNode,
                        @Cached PRaiseNode raiseNode) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, sequence);
            if (slots.mp_ass_subscript() != null) {
                PSlice slice = sliceNode.execute(inliningTarget, iLow, iHigh, PNone.NONE);
                callSetItem.execute(null, inliningTarget, slots.mp_ass_subscript(), sequence, slice, PNone.NO_VALUE);
                return 0;
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.P_OBJECT_DOESNT_SUPPORT_SLICE_DELETION, sequence);
            }
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

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleObject_Size extends CApiUnaryBuiltinNode {

        @Specialization
        static int doGenericUnboxed(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode) {
            // Native objects are handled in C
            assert !(obj instanceof PythonAbstractNativeObject);
            // TODO: theoretically, it is legal for __LEN__ to return a PythonNativeVoidPtr,
            // which is not handled in c.o.g.p.lib.PyObjectSizeNode at this point
            return sizeNode.execute(null, inliningTarget, obj);
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
                        @Shared @Cached ConstructListNode listNode) {
            return listNode.execute(null, keysNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        Object keys(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Shared @Cached ConstructListNode listNode) {
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
                        @Shared @Cached ConstructListNode listNode) {
            return listNode.execute(null, itemsNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        static Object items(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Shared @Cached ConstructListNode listNode) {
            Object attr = getAttrNode.execute(inliningTarget, obj, T_ITEMS);
            return listNode.execute(null, callNode.executeWithoutFrame(attr));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyMapping_Values extends CApiUnaryBuiltinNode {
        @Specialization
        static Object values(PDict obj,
                        @Shared @Cached ConstructListNode listNode,
                        @Cached ValuesNode valuesNode) {
            return listNode.execute(null, valuesNode.execute(null, obj));
        }

        @Specialization(guards = "!isDict(obj)")
        static Object values(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Shared @Cached ConstructListNode listNode,
                        @Cached PRaiseNode raiseNode) {
            checkNonNullArg(inliningTarget, obj, raiseNode);
            Object attr = getAttrNode.execute(inliningTarget, obj, T_VALUES);
            return listNode.execute(null, callNode.executeWithoutFrame(attr));
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleMapping_Size extends CApiUnaryBuiltinNode {

        // cant use PyMapping_Check: PyMapping_Size returns the __len__ value also for
        // subclasses of types not accepted by PyMapping_Check as long they have an overriden
        // __len__ method
        @Specialization
        static int doMapping(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode,
                        @Cached IsSameTypeNode isSameType,
                        @Cached GetClassNode getClassNode,
                        @Cached PRaiseNode raiseNode) {
            Object cls = getClassNode.execute(inliningTarget, obj);
            if (isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PSet) ||
                            isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PFrozenSet) ||
                            isSameType.execute(inliningTarget, cls, PythonBuiltinClassType.PDeque)) {
                throw raiseNode.raise(inliningTarget, TypeError, OBJ_ISNT_MAPPING, obj);
            } else {
                return sizeNode.execute(null, inliningTarget, obj);
            }
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

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyTruffleIter_Send extends CApiBinaryBuiltinNode {
        @Specialization
        Object send(Object iter, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIterCheckNode pyiterCheck,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile isClassProfile) {
            try {
                if (arg instanceof PNone && pyiterCheck.execute(inliningTarget, iter)) {
                    return getNextNode.execute(iter);
                } else {
                    return callMethodNode.execute(null, inliningTarget, iter, T_SEND, arg);
                }
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
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyObject_SetDoc.class);

        @Specialization
        static int set(PBuiltinFunction obj, Object value,
                        @Shared("write") @Cached WriteAttributeToPythonObjectNode write) {
            write.execute(obj, T___DOC__, value);
            return 1;
        }

        @Specialization
        static int set(PBuiltinMethod obj, Object value,
                        @Shared("write") @Cached WriteAttributeToPythonObjectNode write) {
            set(obj.getBuiltinFunction(), value, write);
            return 1;
        }

        @Specialization
        static int set(GetSetDescriptor obj, Object value,
                        @Shared("write") @Cached WriteAttributeToPythonObjectNode write) {
            write.execute(obj, T___DOC__, value);
            return 1;
        }

        @Specialization(guards = "isType.execute(inliningTarget, type)", limit = "1")
        static int set(PythonAbstractNativeObject type, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsTypeNode isType,
                        @Cached TruffleString.SwitchEncodingNode switchEncoding,
                        @Cached CStructAccess.WritePointerNode writePointerNode) {
            Object cValue;
            if (value instanceof TruffleString stringValue) {
                cValue = new CStringWrapper(switchEncoding.execute(stringValue, TruffleString.Encoding.UTF_8), TruffleString.Encoding.UTF_8);
            } else {
                cValue = PythonContext.get(inliningTarget).getNativeNull();
            }
            writePointerNode.write(type.getPtr(), PyTypeObject__tp_doc, cValue);
            return 1;
        }

        @Fallback
        @SuppressWarnings("unused")
        static int set(Object obj, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The callers don't expect errors, so just warn
            LOGGER.warning("Unexpected type in PyObject_SetDoc: " + obj.getClass());
            return 1;
        }
    }
}
