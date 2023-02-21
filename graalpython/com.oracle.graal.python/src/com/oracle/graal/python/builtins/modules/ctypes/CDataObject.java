/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public class CDataObject extends PythonBuiltinObject {

    /*
     * Hm. Are there CDataObject's which do not need the b_objects member? In this case we probably
     * should introduce b_flags to mark it as present... If b_objects is not present/unused b_length
     * is unneeded as well.
     */

    PtrValue b_ptr; /* pointer to memory block */
    int b_needsfree; /* need _we_ free the memory? */
    CDataObject b_base; /* pointer to base object or NULL */
    int b_size; /* size of memory block in bytes */
    int b_length; /* number of references we need */
    int b_index; /* index of this object into base's b_object list */
    Object b_objects; /* dictionary of references we need to keep, or Py_None */

    /*
     * A default buffer in CDataObject, which can be used for small C types. If this buffer is too
     * small, PyMem_Malloc will be called to create a larger one, and this one is not used.
     *
     * Making CDataObject a variable size object would be a better solution, but more difficult in
     * the presence of PyCFuncPtrObject. Maybe later.
     */
    // Object b_value;

    public CDataObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.b_ptr = new PtrValue();
    }

    protected static CDataObjectWrapper createWrapper(StgDictObject dictObject, byte[] storage) {
        return new CDataObjectWrapper(dictObject, storage);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    Object acquire(@SuppressWarnings("unused") int flags) {
        return this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return b_size;
    }

    @ExportMessage
    byte readByte(int byteIndex,
                    @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        if (b_ptr.ptr != null) {
            return bufferLib.readByte(b_ptr.ptr, b_ptr.offset + byteIndex);
        }
        throw CompilerDirectives.shouldNotReachHere("buffer read from empty CDataObject");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    void writeByte(int byteIndex, byte value,
                    @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        if (b_ptr.ptr != null) {
            bufferLib.writeByte(b_ptr.ptr, b_ptr.offset + byteIndex, value);
            return;
        }
        throw CompilerDirectives.shouldNotReachHere("buffer write to empty CDataObject");
    }

    @ExportMessage
    boolean hasInternalByteArray(
                    @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        if (b_ptr.offset != 0) {
            return false;
        }
        if (b_ptr.ptr != null) {
            return bufferLib.hasInternalByteArray(b_ptr.ptr);
        }
        return true;
    }

    @ExportMessage
    byte[] getInternalByteArray(
                    @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        assert hasInternalByteArray(bufferLib);
        if (b_ptr.ptr != null) {
            return bufferLib.getInternalByteArray(b_ptr.ptr);
        }
        return PythonUtils.EMPTY_BYTE_ARRAY;
    }

    /*-
    static int PyCData_NewGetBuffer(Object myself, Py_buffer *view, int flags)
    {
        CDataObject self = (CDataObject *)myself;
        StgDictObject dict = PyObject_stgdict(myself);
        Py_ssize_t i;

        if (view == null) return 0;

        view.buf = self.b_ptr;
        view.obj = myself;
        view.len = self.b_size;
        view.readonly = 0;
        /* use default format character if not set * /
        view.format = dict.format ? dict.format : "B";
        view.ndim = dict.ndim;
        view.shape = dict.shape;
        view.itemsize = self.b_size;
        if (view.itemsize) {
            for (i = 0; i < view.ndim; ++i) {
                view.itemsize /= dict.shape[i];
            }
        }
        view.strides = NULL;
        view.suboffsets = NULL;
        view.internal = NULL;
        return 0;
    }

    /*-
    static PyBufferProcs PyCData_as_buffer = {
            PyCData_NewGetBuffer,
            NULL,
    };
    */

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    public static class CDataObjectWrapper extends PythonNativeWrapper {

        final byte[] storage;
        final StgDictObject stgDict;

        Object nativePointer;

        private String[] members;

        public CDataObjectWrapper(StgDictObject stgDict, byte[] storage) {
            this.storage = storage;
            assert stgDict != null;
            this.stgDict = stgDict;
            this.nativePointer = null;
        }

        private int getIndex(String field, TruffleString.ToJavaStringNode toJavaStringNode) {
            String[] fields = getMembers(true, toJavaStringNode);
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].equals(field)) {
                    return i;
                }
            }
            return -1;
        }

        @ExportMessage
        boolean hasMembers() {
            return this.stgDict.fieldsNames.length > 0;
        }

        @ExportMessage
        String[] getMembers(@SuppressWarnings("unused") boolean includeInternal,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            if (members == null) {
                members = new String[this.stgDict.fieldsNames.length];
                for (int i = 0; i < this.stgDict.fieldsNames.length; i++) {
                    members[i] = toJavaStringNode.execute(this.stgDict.fieldsNames[i]);
                }
            }
            return members;
        }

        @ExportMessage
        boolean isMemberReadable(String member,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return getIndex(member, toJavaStringNode) != -1;
        }

        @ExportMessage
        final boolean isMemberModifiable(String member,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return isMemberReadable(member, toJavaStringNode);
        }

        @ExportMessage
        final boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        Object readMember(String member,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) throws UnknownIdentifierException {
            int idx = getIndex(member, toJavaStringNode);
            if (idx != -1) {
                return CtypesNodes.getValue(stgDict.fieldsTypes[idx], storage, stgDict.fieldsOffsets[idx]);
            }
            throw UnknownIdentifierException.create(member);
        }

        @ExportMessage
        void writeMember(String member, Object value,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) throws UnknownIdentifierException {
            int idx = getIndex(member, toJavaStringNode);
            if (idx != -1) {
                CtypesNodes.setValue(stgDict.fieldsTypes[idx], storage, stgDict.fieldsOffsets[idx], value);
                return;
            }
            throw UnknownIdentifierException.create(member);
        }

        // TO POINTER / AS POINTER / TO NATIVE

        @ExportMessage
        protected boolean isPointer() {
            return isNative();
        }

        @ExportMessage
        public long asPointer() {
            return getNativePointer();
        }

        @ExportMessage
        protected void toNative(
                        @Cached ConditionProfile isNativeProfile) {
            if (!isNative(isNativeProfile)) {
                CApiTransitions.firstToNative(this);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            // TODO implement native type
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getNativeType() {
            // TODO implement native type
            return null;
        }
    }
}
