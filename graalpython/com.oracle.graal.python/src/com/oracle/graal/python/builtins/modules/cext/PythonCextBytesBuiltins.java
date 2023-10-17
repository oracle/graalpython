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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.BytesNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.GetBytesStorage;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextBytesBuiltins {

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyBytes_Size extends CApiUnaryBuiltinNode {
        @Specialization
        static long doPBytes(PBytes obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(null, inliningTarget, obj);
        }

        @Specialization
        long doOther(PythonAbstractNativeObject obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyBytesCheckNode check,
                        @Cached CStructAccess.ReadI64Node readI64Node) {
            if (check.execute(inliningTarget, obj)) {
                return readI64Node.readFromObj(obj, PyVarObject__ob_size);
            }
            return fallback(obj);
        }

        @Fallback
        @TruffleBoundary
        long fallback(Object obj) {
            throw raise(TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleBytes_Concat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object concat(Object original, Object newPart,
                        @Cached BytesBuiltins.AddNode addNode) {
            return addNode.execute(null, original, newPart);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class _PyBytes_Join extends CApiBinaryBuiltinNode {
        @Specialization
        static Object join(Object original, Object newPart,
                        @Cached BytesBuiltins.JoinNode joinNode) {
            return joinNode.execute(null, original, newPart);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyObject}, call = Ignored)
    abstract static class PyTruffleBytes_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object fromFormat(TruffleString fmt, Object args,
                        @Cached ModNode modeNode,
                        @Cached EncodeNode encodeNode) {
            Object formated = modeNode.execute(null, fmt, args);
            return encodeNode.execute(null, formated, PNone.NONE, PNone.NONE);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyBytes_FromObject extends CApiUnaryBuiltinNode {
        @Specialization
        Object fromObject(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached BytesNode bytesNode,
                        @Cached PyObjectLookupAttr lookupAttrNode) {

            if (PGuards.isPBytes(obj)) {
                return obj;
            } else {
                Object klass = getClassNode.execute(inliningTarget, obj);
                if (isSubtypeNode.execute(klass, PythonBuiltinClassType.PBytes)) {
                    return obj;
                } else if (isAcceptedSubtype(inliningTarget, obj, klass, isSubtypeNode, lookupAttrNode)) {
                    return bytesNode.execute(null, PythonBuiltinClassType.PBytes, obj, PNone.NO_VALUE, PNone.NO_VALUE);
                } else {
                    throw raise(TypeError, CANNOT_CONVERT_P_OBJ_TO_S, obj, "bytes");
                }
            }
        }

        private static boolean isAcceptedSubtype(Node inliningTarget, Object obj, Object klass, IsSubtypeNode isSubtypeNode, PyObjectLookupAttr lookupAttrNode) {
            return isSubtypeNode.execute(klass, PythonBuiltinClassType.PList) ||
                            isSubtypeNode.execute(klass, PythonBuiltinClassType.PTuple) ||
                            isSubtypeNode.execute(klass, PythonBuiltinClassType.PMemoryView) ||
                            (!isSubtypeNode.execute(klass, PythonBuiltinClassType.PString) && lookupAttrNode.execute(null, inliningTarget, obj, T___ITER__) != PNone.NO_VALUE);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtr, Py_ssize_t}, call = Ignored)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleBytes_FromStringAndSize extends CApiBinaryBuiltinNode {
        // n.b.: the specializations for PIBytesLike are quite common on
        // managed, when the PySequenceArrayWrapper that we used never went
        // native, and during the upcall to here it was simply unwrapped again
        // with the ToJava (rather than mapped from a native pointer back into a
        // PythonNativeObject)

        @Specialization
        static Object doGeneric(PythonNativeWrapper object, long size,
                        @Cached NativeToPythonNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] ary = getByteArrayNode.execute(null, asPythonObjectNode.execute(object));
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                return factory.createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                return factory.createBytes(ary);
            }
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        static Object doNativePointer(Object nativePointer, long size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return factory.createBytes(getByteArrayNode.execute(nativePointer, size));
            } catch (InteropException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtr, Py_ssize_t}, call = Ignored)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleByteArray_FromStringAndSize extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(PythonNativeWrapper object, long size,
                        @Cached NativeToPythonNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] ary = getByteArrayNode.execute(null, asPythonObjectNode.execute(object));
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                return factory.createByteArray(Arrays.copyOf(ary, (int) size));
            } else {
                return factory.createByteArray(ary);
            }
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        static Object doNativePointer(Object nativePointer, long size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return factory.createByteArray(getByteArrayNode.execute(nativePointer, size));
            } catch (InteropException e) {
                return raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                return raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
        }
    }

    @CApiBuiltin(name = "PyByteArray_Resize", ret = Int, args = {PyObject, Py_ssize_t}, call = Direct)
    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t}, call = Ignored)
    abstract static class _PyTruffleBytes_Resize extends CApiBinaryBuiltinNode {

        @Specialization
        static int resize(PBytesLike self, long newSizeL,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = asSizeNode.executeExact(null, inliningTarget, newSizeL);
            int len = storage.length();
            byte[] smaller = new byte[newSize];
            for (int i = 0; i < newSize && i < len; i++) {
                smaller[i] = castToByteNode.execute(null, getItemNode.execute(storage, i));
            }
            self.setSequenceStorage(new ByteSequenceStorage(smaller));
            return 0;
        }

        @Fallback
        int fallback(Object self, @SuppressWarnings("unused") Object o) {
            throw raise(SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a bytes object", self);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Long}, call = Ignored)
    abstract static class PyTruffle_Bytes_EmptyWithCapacity extends CApiUnaryBuiltinNode {

        @Specialization
        static PBytes doInt(int size,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(new byte[size]);
        }

        @Specialization(rewriteOn = OverflowException.class)
        static PBytes doLong(long size,
                        @Shared @Cached PythonObjectFactory factory) throws OverflowException {
            return doInt(PInt.intValueExact(size), factory);
        }

        @Specialization(replaces = "doLong")
        static PBytes doLongOvf(long size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return doInt(PInt.intValueExact(size), factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raiseNumberTooLarge(IndexError, size);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static PBytes doPInt(PInt size,
                        @Shared @Cached PythonObjectFactory factory) throws OverflowException {
            return doInt(size.intValueExact(), factory);
        }

        @Specialization(replaces = "doPInt")
        static PBytes doPIntOvf(PInt size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return doInt(size.intValueExact(), factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raiseNumberTooLarge(IndexError, size);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Ignored)
    abstract static class PyTruffle_ByteArray_EmptyWithCapacity extends CApiUnaryBuiltinNode {

        @Specialization
        static PByteArray doInt(int size,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createByteArray(new byte[size]);
        }

        @Specialization(rewriteOn = OverflowException.class)
        static PByteArray doLong(long size,
                        @Shared @Cached PythonObjectFactory factory) throws OverflowException {
            return doInt(PInt.intValueExact(size), factory);
        }

        @Specialization(replaces = "doLong")
        static PByteArray doLongOvf(long size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return doInt(PInt.intValueExact(size), factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raiseNumberTooLarge(IndexError, size);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static PByteArray doPInt(PInt size,
                        @Shared @Cached PythonObjectFactory factory) throws OverflowException {
            return doInt(size.intValueExact(), factory);
        }

        @Specialization(replaces = "doPInt")
        static PByteArray doPIntOvf(PInt size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return doInt(size.intValueExact(), factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raiseNumberTooLarge(IndexError, size);
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = CApiCallPath.Ignored)
    abstract static class PyTruffle_Bytes_CheckEmbeddedNull extends CApiUnaryBuiltinNode {

        @Specialization
        static int doBytes(Object bytes,
                        @Bind("this") Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached GetItemScalarNode getItemScalarNode) {
            SequenceStorage sequenceStorage = getBytesStorage.execute(inliningTarget, bytes);
            int len = sequenceStorage.length();
            try {
                for (int i = 0; i < len; i++) {
                    if (getItemScalarNode.executeInt(inliningTarget, sequenceStorage, i) == 0) {
                        return -1;
                    }
                }
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("bytes object contains non-int value");
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {PyObject}, call = Direct)
    abstract static class PyBytes_AsString extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doBytes(PBytes bytes) {
            return PySequenceArrayWrapper.ensureNativeSequence(bytes);
        }

        @Specialization
        Object doNative(PythonAbstractNativeObject obj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CStructAccess.GetElementPtrNode getArray) {
            if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PBytes)) {
                return getArray.getElementPtr(obj.getPtr(), CFields.PyBytesObject__ob_sval);
            }
            return doError(obj);
        }

        @Fallback
        Object doError(Object obj) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", obj);
        }
    }
}
