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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.INT8_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.BytesNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextBytesBuiltins {

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    public abstract static class PyBytes_Size extends CApiUnaryBuiltinNode {
        @Specialization
        public static int size(PBytes obj,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(null, obj);
        }

        @Fallback
        public int fallback(Object obj) {
            throw raiseFallback(obj, PythonBuiltinClassType.PBytes);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    public abstract static class PyTruffleBytes_Concat extends CApiBinaryBuiltinNode {
        @Specialization
        public Object concat(PBytes original, Object newPart,
                        @Cached BytesBuiltins.AddNode addNode) {
            return addNode.execute(null, original, newPart);
        }

        @Fallback
        Object fallback(Object original, @SuppressWarnings("unused") Object newPart) {
            throw raiseFallback(original, PythonBuiltinClassType.PBytes);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    public abstract static class _PyBytes_Join extends CApiBinaryBuiltinNode {
        @Specialization
        public Object join(PBytes original, Object newPart,
                        @Cached BytesBuiltins.JoinNode joinNode) {
            return joinNode.execute(null, original, newPart);
        }

        @Fallback
        Object fallback(Object original, @SuppressWarnings("unused") Object newPart) {
            throw raiseFallback(original, PythonBuiltinClassType.PBytes);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyObject}, call = Ignored)
    public abstract static class PyTruffleBytes_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        public Object fromFormat(TruffleString fmt, Object args,
                        @Cached ModNode modeNode,
                        @Cached EncodeNode encodeNode) {
            Object formated = modeNode.execute(null, fmt, args);
            return encodeNode.execute(null, formated, PNone.NONE, PNone.NONE);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    public abstract static class PyBytes_FromObject extends CApiUnaryBuiltinNode {
        @Specialization(guards = {"isPBytes(obj) || isBytesSubtype(obj, getClassNode, isSubtypeNode)"})
        public static Object fromObject(@SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return obj;
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(obj, getClassNode, isSubtypeNode)", "isAcceptedSubtype(obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(Object obj,
                        @Cached BytesNode bytesNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode) {
            return bytesNode.execute(null, PythonBuiltinClassType.PBytes, obj, PNone.NO_VALUE, PNone.NO_VALUE);
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(obj, getClassNode, isSubtypeNode)", "!isAcceptedSubtype(obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode) {
            throw raise(TypeError, CANNOT_CONVERT_P_OBJ_TO_S, obj, "bytes");
        }

        protected static boolean isBytesSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }

        protected static boolean isAcceptedSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode, PyObjectLookupAttr lookupAttrNode) {
            Object klass = getClassNode.execute(obj);
            return isSubtypeNode.execute(klass, PythonBuiltinClassType.PList) ||
                            isSubtypeNode.execute(klass, PythonBuiltinClassType.PTuple) ||
                            isSubtypeNode.execute(klass, PythonBuiltinClassType.PMemoryView) ||
                            (!isSubtypeNode.execute(klass, PythonBuiltinClassType.PString) && lookupAttrNode.execute(null, obj, T___ITER__) != PNone.NO_VALUE);
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
        Object doGeneric(PythonNativeWrapper object, long size,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode) {
            byte[] ary = getByteArrayNode.execute(null, asPythonObjectNode.execute(object));
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                return factory().createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                return factory().createBytes(ary);
            }
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        Object doNativePointer(Object nativePointer, long size,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return factory().createBytes(getByteArrayNode.execute(nativePointer, size));
            } catch (InteropException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {INT8_T_PTR, Py_ssize_t}, call = Ignored)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleByteArray_FromStringAndSize extends CApiBinaryBuiltinNode {
        @Specialization
        Object doGeneric(PythonNativeWrapper object, long size,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode) {
            byte[] ary = getByteArrayNode.execute(null, asPythonObjectNode.execute(object));
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                return factory().createByteArray(Arrays.copyOf(ary, (int) size));
            } else {
                return factory().createByteArray(ary);
            }
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        Object doNativePointer(Object nativePointer, long size,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return factory().createByteArray(getByteArrayNode.execute(nativePointer, size));
            } catch (InteropException e) {
                return raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                return raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
        }
    }

    @CApiBuiltin(name = "PyByteArray_Resize", ret = Int, args = {PyObject, Py_ssize_t}, call = Direct)
    @CApiBuiltin(ret = Int, args = {PyObject, Py_ssize_t}, call = Ignored)
    public abstract static class _PyTruffleBytes_Resize extends CApiBinaryBuiltinNode {

        @Specialization
        static int resize(PBytesLike self, long newSizeL,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = asSizeNode.executeExact(null, newSizeL);
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
}
