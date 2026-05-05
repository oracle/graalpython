/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode.checkNonNullArgUncached;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SEND;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_ISNT_MAPPING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.runtime.PythonContext.NATIVE_NULL;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.CallSlotMpAssSubscriptNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
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
import com.oracle.graal.python.lib.PyObjectSizeNode;
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
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.ConstructTupleNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextAbstractBuiltins {
    private static final TruffleLogger PY_OBJECT_SET_DOC_LOGGER = CApiContext.getLogger(PythonCextAbstractBuiltins.class);
    /////// PyNumber ///////

    @CApiBuiltin(name = "_PyNumber_Index", ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct, acquireGil = false)
    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct, acquireGil = false)
    static long PyNumber_Index(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        checkNonNullArgUncached(obj);
        Object result = PyNumberIndexNode.executeUncached(obj);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Long(long objectPtr) {
        Object object = NativeToPythonNode.executeRawUncached(objectPtr);
        Object result = PyNumberLongNode.executeUncached(object);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    // TODO(CAPI STATIC): uses nodes without @GenerateUncached
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Direct, acquireGil = false)
    protected abstract static class PyNumber_ToBase extends CApiBinaryBuiltinNode {
        @Specialization(guards = "base == 2")
        static Object toBase2(Object n, @SuppressWarnings("unused") int base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached BinNode binNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            return binNode.execute(null, i);
        }

        @Specialization(guards = "base == 8")
        static Object toBase8(Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode) {
            return octNode.execute(null, n);
        }

        @Specialization(guards = "base == 10")
        static Object toBase10(Object n, @SuppressWarnings("unused") int base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached StringBuiltins.StrNewNode strNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            if (i instanceof Boolean) {
                i = ((boolean) i) ? 1 : 0;
            }
            return strNode.executeWith(i);
        }

        @Specialization(guards = "base == 16")
        static Object toBase16(Object n, @SuppressWarnings("unused") int base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached HexNode hexNode) {
            Object i = indexNode.execute(null, inliningTarget, n);
            return hexNode.execute(null, i);
        }

        @Specialization(guards = "!checkBase(base)")
        static Object toBase(@SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, BASE_MUST_BE);
        }

        protected boolean checkBase(int base) {
            return base == 2 || base == 8 || base == 10 || base == 16;
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Float(long objectPtr) {
        Object object = NativeToPythonNode.executeRawUncached(objectPtr);
        double result = PyNumberFloatNode.executeUncached(object);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Add(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberAddNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Subtract(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberSubtractNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Multiply(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberMultiplyNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Remainder(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberRemainderNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_TrueDivide(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberTrueDivideNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_FloorDivide(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberFloorDivideNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Divmod(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberDivmodNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_And(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberAndNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Or(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberOrNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Xor(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberXorNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Lshift(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberLshiftNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Rshift(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberRshiftNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_MatrixMultiply(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberMatrixMultiplyNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceAdd(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceAddNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceSubtract(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceSubtractNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceMultiply(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceMultiplyNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceRemainder(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceRemainderNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceTrueDivide(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceTrueDivideNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceFloorDivide(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceFloorDivideNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceAnd(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceAndNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceOr(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceOrNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceXor(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceXorNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceLshift(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceLshiftNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceRshift(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceRshiftNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlaceMatrixMultiply(long o1Ptr, long o2Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object result = PyNumberInPlaceMatrixMultiplyNode.getUncached().execute(null, o1, o2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_InPlacePower(long o1Ptr, long o2Ptr, long o3Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object o3 = NativeToPythonNode.executeRawUncached(o3Ptr);
        Object result = PyNumberInPlacePowerNode.getUncached().execute(null, o1, o2, o3);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static long GraalPyPrivate_PyNumber_Power(long o1Ptr, long o2Ptr, long o3Ptr) {
        Object o1 = NativeToPythonNode.executeRawUncached(o1Ptr);
        Object o2 = NativeToPythonNode.executeRawUncached(o2Ptr);
        Object o3 = NativeToPythonNode.executeRawUncached(o3Ptr);
        Object result = PyNumberPowerNode.getUncached().execute(null, o1, o2, o3);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    /////// PySequence ///////

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct, acquireGil = false)
    static long PySequence_Tuple(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        checkNonNullArgUncached(obj);
        Object result = GetClassNode.executeUncached(obj) == PythonBuiltinClassType.PTuple ? obj : ConstructTupleNode.getUncached().execute(null, obj);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct, acquireGil = false)
    static long PySequence_List(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object result = ConstructListNode.getUncached().execute(null, obj);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t, PyObjectRawPointer}, call = Ignored, acquireGil = false)
    static int GraalPyPrivate_Sequence_SetItem(long objPtr, long key, long valuePtr) {
        if ((int) key != key) {
            throw PRaiseNode.raiseStatic(null, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, key);
        }
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object value = NativeToPythonNode.executeRawUncached(valuePtr);
        PySequenceSetItemNode.executeUncached(obj, (int) key, value);
        return 0;
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, Py_ssize_t, Py_ssize_t}, call = Direct, acquireGil = false)
    static long PySequence_GetSlice(long objPtr, long iLow, long iHigh) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        if (PySequenceCheckNode.executeUncached(obj)) {
            Object getItemCallable = PyObjectLookupAttr.executeUncached(obj, T___GETITEM__);
            Object result = CallNode.executeUncached(getItemCallable, PySliceNew.executeUncached(iLow, iHigh, PNone.NONE));
            return PythonToNativeNewRefNode.executeLongUncached(result);
        }
        throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.OBJ_IS_UNSLICEABLE, obj);
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct, acquireGil = false)
    static int PySequence_Contains(long haystackPtr, long needlePtr) {
        Object haystack = NativeToPythonNode.executeRawUncached(haystackPtr);
        Object needle = NativeToPythonNode.executeRawUncached(needlePtr);
        return PInt.intValue(PySequenceContainsNode.executeUncached(haystack, needle));
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, Py_ssize_t}, call = Direct)
    static long PySequence_InPlaceRepeat(long objPtr, long n) {
        if (!PInt.isIntRange(n)) {
            throw PRaiseNode.raiseStatic(null, OverflowError);
        }
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object result = PySequenceInPlaceRepeatNode.executeUncached(obj, (int) n);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PySequence_Concat(long s1Ptr, long s2Ptr) {
        Object s1 = NativeToPythonNode.executeRawUncached(s1Ptr);
        Object s2 = NativeToPythonNode.executeRawUncached(s2Ptr);
        Object result = PySequenceConcatNode.executeUncached(s1, s2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PySequence_InPlaceConcat(long s1Ptr, long s2Ptr) {
        Object s1 = NativeToPythonNode.executeRawUncached(s1Ptr);
        Object s2 = NativeToPythonNode.executeRawUncached(s2Ptr);
        Object result = PySequenceInPlaceConcatNode.executeUncached(s1, s2);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t}, call = Ignored)
    static int GraalPyPrivate_Sequence_DelItem(long oPtr, long i) {
        if ((int) i != i) {
            throw PRaiseNode.raiseStatic(null, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, i);
        }
        Object o = NativeToPythonNode.executeRawUncached(oPtr);
        PySequenceDelItemNode.executeUncached(o, (int) i);
        return 0;
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, Py_ssize_t}, call = Ignored)
    static long GraalPyPrivate_Sequence_GetItem(long delegatePtr, long position) {
        if ((int) position != position) {
            throw PRaiseNode.raiseStatic(null, PythonErrorType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, position);
        }
        Object delegate = NativeToPythonNode.executeRawUncached(delegatePtr);
        Object result = PySequenceGetItemNode.executeUncached(delegate, (int) position);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Sequence_Size(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        return PySequenceSizeNode.executeUncached(obj);
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t, Py_ssize_t, PyObjectRawPointer}, call = Direct)
    static int PySequence_SetSlice(long sequencePtr, long iLow, long iHigh, long sPtr) {
        Object sequence = NativeToPythonNode.executeRawUncached(sequencePtr);
        TpSlots slots = GetObjectSlotsNode.executeUncached(sequence);
        if (slots.mp_ass_subscript() != null) {
            Object s = NativeToPythonNode.executeRawUncached(sPtr);
            PSlice slice = PySliceNew.executeUncached(iLow, iHigh, PNone.NONE);
            CallSlotMpAssSubscriptNode.executeUncached(slots.mp_ass_subscript(), sequence, slice, s);
            return 0;
        } else {
            throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.P_OBJECT_DOESNT_SUPPORT_SLICE_ASSIGNMENT, sequence);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t, Py_ssize_t}, call = Direct)
    static int PySequence_DelSlice(long sequencePtr, long iLow, long iHigh) {
        Object sequence = NativeToPythonNode.executeRawUncached(sequencePtr);
        TpSlots slots = GetObjectSlotsNode.executeUncached(sequence);
        if (slots.mp_ass_subscript() != null) {
            PSlice slice = PySliceNew.executeUncached(iLow, iHigh, PNone.NONE);
            CallSlotMpAssSubscriptNode.executeUncached(slots.mp_ass_subscript(), sequence, slice, PNone.NO_VALUE);
            return 0;
        } else {
            throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.P_OBJECT_DOESNT_SUPPORT_SLICE_DELETION, sequence);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PySequence_Count(long haystackPtr, long needlePtr) {
        Object haystack = NativeToPythonNode.executeRawUncached(haystackPtr);
        Object needle = NativeToPythonNode.executeRawUncached(needlePtr);
        return PySequenceIterSearchNode.executeUncached(haystack, needle, PySequenceIterSearchNode.PY_ITERSEARCH_COUNT);
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PySequence_Index(long haystackPtr, long needlePtr) {
        Object haystack = NativeToPythonNode.executeRawUncached(haystackPtr);
        Object needle = NativeToPythonNode.executeRawUncached(needlePtr);
        return PySequenceIterSearchNode.executeUncached(haystack, needle, PySequenceIterSearchNode.PY_ITERSEARCH_INDEX);
    }

    /////// PyObject ///////

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PyObject_GetItem(long objPtr, long keyPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object key = NativeToPythonNode.executeRawUncached(keyPtr);
        Object result = PyObjectGetItem.executeUncached(obj, key);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(name = "GraalPyPrivate_Object_GetItemString", ret = PyObjectRawPointer, args = {PyObjectRawPointer, ConstCharPtr}, call = Ignored)
    static long GraalPyPrivate_Object_GetItemString(long objPtr, long keyPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object key = CharPtrToPythonNode.getUncached().execute(keyPtr);
        Object result = PyObjectGetItem.executeUncached(obj, key);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Object_Size(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        // Native objects are handled in C
        assert !(obj instanceof PythonAbstractNativeObject);
        // TODO: theoretically, it is legal for __LEN__ to return a PythonNativeVoidPtr,
        // which is not handled in c.o.g.p.lib.PyObjectSizeNode at this point
        return PyObjectSizeNode.executeUncached(obj);
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer, Py_ssize_t}, call = Direct)
    static long PyObject_LengthHint(long objPtr, long defaultValue) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        int len = IteratorNodes.GetLength.executeUncached(obj);
        if (len == -1) {
            return defaultValue;
        }
        return len;
    }

    /////// PyMapping ///////

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyMapping_Keys(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        checkNonNullArgUncached(obj);
        ConstructListNode listNode = ConstructListNode.getUncached();
        Object listResult;
        if (obj instanceof PDict dict && PGuards.isBuiltinDict(dict)) {
            PythonLanguage language = PythonContext.get(null).getLanguage();
            Object view = PFactory.createDictKeysView(language, dict);
            listResult = listNode.execute(null, view);
        } else {
            Object callable = PyObjectGetAttr.executeUncached(obj, T_KEYS);
            Object view = CallNode.executeUncached(callable);
            listResult = listNode.execute(null, view);
        }
        return PythonToNativeNewRefNode.executeLongUncached(listResult);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyMapping_Items(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        checkNonNullArgUncached(obj);
        ConstructListNode listNode = ConstructListNode.getUncached();
        Object listResult;
        if (obj instanceof PDict dict && PGuards.isBuiltinDict(dict)) {
            PythonLanguage language = PythonContext.get(null).getLanguage();
            Object view = PFactory.createDictItemsView(language, dict);
            listResult = listNode.execute(null, view);
        } else {
            Object callable = PyObjectGetAttr.executeUncached(obj, T_ITEMS);
            Object view = CallNode.executeUncached(callable);
            listResult = listNode.execute(null, view);
        }
        return PythonToNativeNewRefNode.executeLongUncached(listResult);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyMapping_Values(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        checkNonNullArgUncached(obj);
        ConstructListNode listNode = ConstructListNode.getUncached();
        Object listResult;
        if (obj instanceof PDict dict && PGuards.isBuiltinDict(dict)) {
            PythonLanguage language = PythonContext.get(null).getLanguage();
            Object view = PFactory.createDictValuesView(language, dict);
            listResult = listNode.execute(null, view);
        } else {
            Object callable = PyObjectGetAttr.executeUncached(obj, T_VALUES);
            Object view = CallNode.executeUncached(callable);
            listResult = listNode.execute(null, view);
        }
        return PythonToNativeNewRefNode.executeLongUncached(listResult);
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Mapping_Size(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object cls = GetClassNode.executeUncached(obj);
        if (IsSameTypeNode.executeUncached(cls, PythonBuiltinClassType.PSet) ||
                        IsSameTypeNode.executeUncached(cls, PythonBuiltinClassType.PFrozenSet) ||
                        IsSameTypeNode.executeUncached(cls, PythonBuiltinClassType.PDeque)) {
            throw PRaiseNode.raiseStatic(null, TypeError, OBJ_ISNT_MAPPING, obj);
        }
        return PyObjectSizeNode.executeUncached(obj);
    }

    /////// PyIter ///////

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyIter_Next(long iteratorPtr) {
        Object iterator = NativeToPythonNode.executeRawUncached(iteratorPtr);
        try {
            Object result = PyIterNextNode.executeUncached(iterator);
            if (result == NATIVE_NULL) {
                return NULLPTR;
            }
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (IteratorExhausted e) {
            return NULLPTR;
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Iter_Send(long iterPtr, long argPtr) {
        Object iter = NativeToPythonNode.executeRawUncached(iterPtr);
        Object arg = NativeToPythonNode.executeRawUncached(argPtr);
        if (arg instanceof PNone && PyIterCheckNode.executeUncached(iter)) {
            try {
                Object result = PyIterNextNode.executeUncached(iter);
                if (result == NATIVE_NULL) {
                    return NULLPTR;
                }
                return PythonToNativeNewRefNode.executeLongUncached(result);
            } catch (IteratorExhausted e) {
                return NULLPTR;
            }
        }
        try {
            Object result = PyObjectCallMethodObjArgs.executeUncached(iter, T_SEND, arg);
            if (result == NATIVE_NULL) {
                return NULLPTR;
            }
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (PException e) {
            e.expectStopIteration(null, IsBuiltinObjectProfile.getUncached());
            return NULLPTR;
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObjectRawPointer}, call = Direct)
    static long PyObject_GetDoc(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        try {
            Object doc = PyObjectLookupAttr.executeUncached(obj, T___DOC__);
            if (!(doc instanceof PNone)) {
                return AsCharPointerNode.getUncached().execute(doc);
            }
        } catch (PException e) {
            // ignore
        }
        return NULLPTR;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, ConstCharPtr}, call = Direct)
    static int PyObject_SetDoc(long objPtr, long valuePtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        Object value = CharPtrToPythonNode.getUncached().execute(valuePtr);
        if (obj instanceof PBuiltinFunction builtinFunction) {
            WriteAttributeToPythonObjectNode.executeUncached(builtinFunction, T___DOC__, value);
            return 1;
        }
        if (obj instanceof PBuiltinMethod builtinMethod) {
            PBuiltinFunction builtinFunction = builtinMethod.getBuiltinFunction();
            WriteAttributeToPythonObjectNode.executeUncached(builtinFunction, T___DOC__, value);
            return 1;
        }
        if (obj instanceof GetSetDescriptor descriptor) {
            WriteAttributeToPythonObjectNode.executeUncached(descriptor, T___DOC__, value);
            return 1;
        }
        if (obj instanceof PythonAbstractNativeObject nativeType) {
            if (IsTypeNode.executeUncached(nativeType)) {
                long cValue = NULLPTR;
                if (value instanceof TruffleString stringValue) {
                    cValue = AsCharPointerNode.getUncached().execute(stringValue);
                }
                writePtrField(nativeType.getPtr(), PyTypeObject__tp_doc, cValue);
                return 1;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PY_OBJECT_SET_DOC_LOGGER.warning("Unexpected type in PyObject_SetDoc: " + obj.getClass());
        return 1;
    }
}
