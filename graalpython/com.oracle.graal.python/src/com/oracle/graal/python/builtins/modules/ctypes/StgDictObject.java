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

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class StgDictObject extends PDict {

    protected static final int VOID_PTR_SIZE = Long.BYTES; // sizeof(void *)

    /*
     * The size and align fields are unneeded, they are in ffi_type as well. As an experiment shows,
     * it's trivial to get rid of them, the only thing to remember is that in PyCArrayType_new the
     * ffi_type fields must be filled in - so far it was unneeded because libffi doesn't support
     * arrays at all (because they are passed as pointers to function calls anyway). But it's too
     * much risk to change that now, and there are other fields which doesn't belong into this
     * structure anyway. Maybe in ctypes 2.0... (ctypes 2000?)
     */
    int size; /* number of bytes */
    int align; /* alignment requirements */
    int length; /* number of fields */
    FFIType ffi_type_pointer;
    Object proto; /* Only for Pointer/ArrayObject */
    FieldSet setfunc; /* Only for simple objects */
    FieldGet getfunc; /* Only for simple objects */
    int paramfunc;

    /* Following fields only used by PyCFuncPtrType_Type instances */
    Object[] argtypes; /* tuple of CDataObjects */
    Object[] converters; /* tuple([t.from_param for t in argtypes]) */
    Object restype; /* CDataObject or NULL */
    Object checker;
    int flags; /* calling convention and such */

    /* pep3118 fields, pointers neeed PyMem_Free */
    TruffleString format;
    int ndim;
    int[] shape;

    Object[] fieldsNames;
    int[] fieldsOffsets;
    FFI_TYPES[] fieldsTypes;

    public StgDictObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        format = null;
        ndim = 0;
        shape = null;
        fieldsNames = null;
        paramfunc = -1;
        setfunc = FieldSet.nil;
        getfunc = FieldGet.nil;
        ffi_type_pointer = new FFIType();
    }

    protected void clear() {
        this.proto = null;
        this.argtypes = null;
        this.converters = null;
        this.restype = null;
        this.checker = null;
    }

    protected static void clone(StgDictObject dst, StgDictObject src) {
        dst.clear();
        dst.format = null;
        dst.shape = null;
        dst.ffi_type_pointer = new FFIType();

        if (src.format != null) {
            dst.format = src.format;
        }
        if (src.shape != null) {
            dst.shape = new int[src.ndim];
            PythonUtils.arraycopy(dst.shape, 0, src.shape, 0, src.ndim);
        }

        if (src.ffi_type_pointer.elements == null) {
            return;
        }
        int size = src.length;
        dst.ffi_type_pointer.elements = new FFIType[size];
        PythonUtils.arraycopy(dst.ffi_type_pointer.elements, 0, src.ffi_type_pointer.elements, 0, size);
    }

}
