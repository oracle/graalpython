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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.getByteArray;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.getFieldPtr;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.GetBytesStorage;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextBytesBuiltins {

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectRawPointer}, call = Direct)
    static long PyBytes_Size(long objPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(objPtr);
        if (obj instanceof PBytes bytes) {
            return PyObjectSizeNode.executeUncached(bytes);
        }
        if (obj instanceof PythonAbstractNativeObject nativeObj && PyBytesCheckNode.executeUncached(nativeObj)) {
            return readLongField(nativeObj.getPtr(), PyVarObject__ob_size);
        }
        throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, obj);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Bytes_Concat(long originalPtr, long newPartPtr) {
        Object original = NativeToPythonNode.executeRawUncached(originalPtr);
        Object newPart = NativeToPythonNode.executeRawUncached(newPartPtr);
        Object result = BytesCommonBuiltins.ConcatNode.executeUncached(original, newPart);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    // TODO(CAPI STATIC): uses nodes without @GenerateUncached
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class _PyBytes_Join extends CApiBinaryBuiltinNode {
        @Specialization
        static Object join(Object original, Object newPart,
                        @Cached BytesCommonBuiltins.JoinNode joinNode) {
            return joinNode.execute(null, original, newPart);
        }
    }

    // TODO(CAPI STATIC): uses nodes without @GenerateUncached
    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Bytes_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object fromFormat(TruffleString fmt, Object args,
                        @Cached ModNode modeNode,
                        @Cached EncodeNode encodeNode) {
            Object formated = modeNode.execute(null, fmt, args);
            return encodeNode.execute(null, formated, PNone.NONE, PNone.NONE);
        }
    }

    // TODO(CAPI STATIC): uses nodes without @GenerateUncached
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyBytes_FromObject extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isBuiltinBytes(bytes)")
        static Object bytes(PBytes bytes) {
            return bytes;
        }

        @Fallback
        static Object fromObject(Object obj,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.BytesFromObject fromObject) {
            byte[] bytes = fromObject.execute(null, obj);
            return PFactory.createBytes(PythonLanguage.get(inliningTarget), bytes);
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {ConstCharPtr, Py_ssize_t}, call = Ignored)
    static long GraalPyPrivate_Bytes_FromStringAndSize(long nativePointer, long size) {
        try {
            byte[] bytes = getByteArray(nativePointer, size);
            Object result = PFactory.createBytes(PythonLanguage.get(null), bytes);
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (OverflowException e) {
            throw PRaiseNode.raiseStatic(null, PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {ConstCharPtr, Py_ssize_t}, call = Ignored)
    static long GraalPyPrivate_ByteArray_FromStringAndSize(long nativePointer, long size) {
        try {
            byte[] bytes = getByteArray(nativePointer, size);
            Object result = PFactory.createByteArray(PythonLanguage.get(null), bytes);
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (OverflowException e) {
            throw PRaiseNode.raiseStatic(null, PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
        }
    }

    @CApiBuiltin(name = "PyByteArray_Resize", ret = Int, args = {PyObjectRawPointer, Py_ssize_t}, call = Direct)
    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Py_ssize_t}, call = Ignored)
    static int GraalPyPrivate_Bytes_Resize(long selfPtr, long newSizeL) {
        Object self = NativeToPythonNode.executeRawUncached(selfPtr);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, !(self instanceof PBytesLike))) {
            throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a bytes object", self);
        }
        PBytesLike bytesLike = (PBytesLike) self;
        SequenceStorage storage = bytesLike.getSequenceStorage();
        int newSize = PyNumberAsSizeNode.executeExactUncached(newSizeL);
        int len = storage.length();
        byte[] resized = new byte[newSize];
        CastToByteNode castToByteNode = CastToByteNode.getUncached();
        for (int i = 0; i < newSize && i < len; i++) {
            resized[i] = castToByteNode.execute(null, GetItemScalarNode.executeUncached(storage, i));
        }
        bytesLike.setSequenceStorage(new ByteSequenceStorage(resized));
        return 0;
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {ArgDescriptor.Long}, call = Ignored)
    static long GraalPyPrivate_Bytes_EmptyWithCapacity(long size) {
        try {
            Object result = PFactory.createBytes(PythonLanguage.get(null), new byte[PInt.intValueExact(size)]);
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (OverflowException e) {
            throw PRaiseNode.raiseStatic(null, IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, size);
        }
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {Py_ssize_t}, call = Ignored)
    static long GraalPyPrivate_ByteArray_EmptyWithCapacity(long size) {
        try {
            Object result = PFactory.createByteArray(PythonLanguage.get(null), new byte[PInt.intValueExact(size)]);
            return PythonToNativeNewRefNode.executeLongUncached(result);
        } catch (OverflowException e) {
            throw PRaiseNode.raiseStatic(null, IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, size);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer}, call = CApiCallPath.Ignored)
    static int GraalPyPrivate_Bytes_CheckEmbeddedNull(long bytesPtr) {
        Object bytes = NativeToPythonNode.executeRawUncached(bytesPtr);
        SequenceStorage sequenceStorage = GetBytesStorage.executeUncached(bytes);
        int len = sequenceStorage.length();
        try {
            for (int i = 0; i < len; i++) {
                if (GetItemScalarNode.executeIntUncached(sequenceStorage, i) == 0) {
                    return -1;
                }
            }
        } catch (UnexpectedResultException e) {
            throw CompilerDirectives.shouldNotReachHere("bytes object contains non-int value");
        }
        return 0;
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {PyObjectRawPointer}, call = Direct)
    static long PyBytes_AsString(long bytesPtr) {
        Object obj = NativeToPythonNode.executeRawUncached(bytesPtr);
        if (obj instanceof PBytes bytes) {
            return PySequenceArrayWrapper.ensureNativeSequence(bytes);
        }
        if (obj instanceof PythonAbstractNativeObject nativeObj) {
            Object type = GetClassNode.executeUncached(nativeObj);
            if (IsSubtypeNode.getUncached().execute(type, PythonBuiltinClassType.PBytes)) {
                return getFieldPtr(nativeObj.getPtr(), CFields.PyBytesObject__ob_sval);
            }
        }
        throw PRaiseNode.raiseStatic(null, PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", obj);
    }
}
