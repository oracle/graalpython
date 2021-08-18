/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.util.PythonUtils;

public class FFIType {

    public static final FFIType ffi_type_pointer = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_POINTER);
    // Arrays
    public static final FFIType ffi_type_uint8_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT8_ARRAY);
    public static final FFIType ffi_type_sint8_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT8_ARRAY);
    public static final FFIType ffi_type_uint16_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT16_ARRAY);
    public static final FFIType ffi_type_sint16_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT16_ARRAY);
    public static final FFIType ffi_type_uint32_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT32_ARRAY);
    public static final FFIType ffi_type_sint32_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT32_ARRAY);
    public static final FFIType ffi_type_uint64_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT64_ARRAY);
    public static final FFIType ffi_type_sint64_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT64_ARRAY);
    public static final FFIType ffi_type_float_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_FLOAT_ARRAY);
    public static final FFIType ffi_type_double_array = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_DOUBLE_ARRAY);

    // Primitives
    public static final FFIType nfi_type_string = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_STRING);
    public static final FFIType ffi_type_uint8 = new FFIType(Byte.BYTES, Byte.BYTES, FFI_TYPES.FFI_TYPE_UINT8, ffi_type_uint8_array);
    public static final FFIType ffi_type_sint8 = new FFIType(Byte.BYTES, Byte.BYTES, FFI_TYPES.FFI_TYPE_SINT8, ffi_type_sint8_array);
    public static final FFIType ffi_type_uint16 = new FFIType(Short.BYTES, Short.BYTES, FFI_TYPES.FFI_TYPE_UINT16, ffi_type_uint16_array);
    public static final FFIType ffi_type_sint16 = new FFIType(Short.BYTES, Short.BYTES, FFI_TYPES.FFI_TYPE_SINT16, ffi_type_sint16_array);
    public static final FFIType ffi_type_uint32 = new FFIType(Integer.BYTES, Integer.BYTES, FFI_TYPES.FFI_TYPE_UINT32, ffi_type_uint32_array);
    public static final FFIType ffi_type_sint32 = new FFIType(Integer.BYTES, Integer.BYTES, FFI_TYPES.FFI_TYPE_SINT32, ffi_type_sint32_array);
    public static final FFIType ffi_type_uint64 = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT64, ffi_type_uint64_array);
    public static final FFIType ffi_type_sint64 = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT64, ffi_type_sint64_array);
    public static final FFIType ffi_type_double = new FFIType(Double.BYTES, Float.BYTES, FFI_TYPES.FFI_TYPE_DOUBLE, ffi_type_double_array);
    // XXX there is no direct representation in java for long double
    public static final FFIType ffi_type_uchar = ffi_type_uint8;
    public static final FFIType ffi_type_schar = ffi_type_sint8;
    public static final FFIType ffi_type_ushort = ffi_type_uint16;
    public static final FFIType ffi_type_sshort = ffi_type_sint16;
    public static final FFIType ffi_type_uint = ffi_type_uint32;
    public static final FFIType ffi_type_sint = ffi_type_sint32;
    public static final FFIType ffi_type_ulong = ffi_type_uint64;
    public static final FFIType ffi_type_slong = ffi_type_sint64;
    public static final FFIType ffi_type_float = new FFIType(Float.BYTES, Float.BYTES, FFI_TYPES.FFI_TYPE_FLOAT);
    public static final FFIType ffi_type_longdouble = ffi_type_double;

    enum FFI_TYPES {
        FFI_TYPE_VOID("VOID"),
        FFI_TYPE_UINT8("UINT8"),
        FFI_TYPE_SINT8("SINT8"),
        FFI_TYPE_UINT16("UINT16"),
        FFI_TYPE_SINT16("SINT16"),
        FFI_TYPE_UINT32("UINT32"),
        FFI_TYPE_SINT32("SINT32"),
        FFI_TYPE_UINT64("UINT64"),
        FFI_TYPE_SINT64("SINT64"),
        FFI_TYPE_FLOAT("FLOAT"),
        FFI_TYPE_DOUBLE("DOUBLE"),
        FFI_TYPE_UINT8_ARRAY("[UINT8]"),
        FFI_TYPE_SINT8_ARRAY("[SINT8]"),
        FFI_TYPE_UINT16_ARRAY("[UINT16]"),
        FFI_TYPE_SINT16_ARRAY("[SINT16]"),
        FFI_TYPE_UINT32_ARRAY("[UINT32]"),
        FFI_TYPE_SINT32_ARRAY("[SINT32]"),
        FFI_TYPE_UINT64_ARRAY("[UINT64]"),
        FFI_TYPE_SINT64_ARRAY("[SINT64]"),
        FFI_TYPE_FLOAT_ARRAY("[FLOAT]"),
        FFI_TYPE_DOUBLE_ARRAY("[DOUBLE]"),
        FFI_TYPE_POINTER("POINTER"),
        FFI_TYPE_STRING("STRING"),
        FFI_TYPE_STRUCT("OBJECT");

        private final String nfiType;

        FFI_TYPES(String str) {
            this.nfiType = str;
        }

        public String getNFIType() {
            return nfiType;
        }
    }

    int size;
    int alignment; // short
    FFI_TYPES type;
    FFIType[] elements;

    final FFIType asArray;

    public FFIType(int size, int alignment, FFI_TYPES type, FFIType[] elements, FFIType asArray) {
        this.size = size;
        this.alignment = alignment;
        this.type = type;
        this.elements = elements;
        this.asArray = asArray == null ? this : asArray;
    }

    public FFIType(int size, int alignment, FFI_TYPES type, FFIType[] elements) {
        this(size, alignment, type, elements, null);
    }

    public FFIType(int size, int alignment, FFI_TYPES type) {
        this(size, alignment, type, null, null);
    }

    public FFIType(int size, int alignment, FFI_TYPES type, FFIType asArray) {
        this(size, alignment, type, null, asArray);
    }

    public FFIType(FFIType copyFrom) {
        this(copyFrom.size, copyFrom.alignment, copyFrom.type, copyFrom.elements);
    }

    public FFIType() {
        this(ffi_type_pointer.size, ffi_type_pointer.alignment, ffi_type_pointer.type, null, null);
    }

    protected static int typeSize() {
        return 1;
    }

    protected FFIType getAsArray() {
        return asArray;
    }

    private static String getNFIType(FFIType type) {
        return type.type.nfiType;
    }

    protected static String buildNFISignature(FFIType[] atypes, FFIType restype) {
        StringBuilder sb = PythonUtils.newStringBuilder();
        boolean first = true;
        PythonUtils.append(sb, "(");
        for (FFIType type : atypes) {
            if (first) {
                first = false;
            } else {
                PythonUtils.append(sb, ", ");
            }
            PythonUtils.append(sb, getNFIType(type));
        }
        PythonUtils.append(sb, "):");
        PythonUtils.append(sb, getNFIType(restype));
        return PythonUtils.sbToString(sb);
    }

    protected static final int BOOL_TYPE = 1;
    protected static final int BYTE_TYPE = 2;
    protected static final int SHORT_TYPE = 4;
    protected static final int INT_TYPE = 8;
    protected static final int LONG_TYPE = 16;
    protected static final int FLOAT_TYPE = 32;
    protected static final int DOUBLE_TYPE = 64;
    protected static final int STRING_TYPE = 128;
    protected static final int OBJECT_TYPE = 256;
    protected static final int POINTER_TYPE = 512;

    enum FieldSet {
        nil(0),

        s_set(BYTE_TYPE), // byte
        b_set(BYTE_TYPE), // byte
        B_set(BYTE_TYPE), // byte
        c_set(BYTE_TYPE), // byte
        d_set(DOUBLE_TYPE), // double
        d_set_sw(DOUBLE_TYPE), // double
        g_set(DOUBLE_TYPE), // double
        f_set(FLOAT_TYPE), // float
        f_set_sw(FLOAT_TYPE), // float
        h_set(SHORT_TYPE), // short
        h_set_sw(SHORT_TYPE), // short
        H_set(SHORT_TYPE), // short
        H_set_sw(SHORT_TYPE), // short
        i_set(INT_TYPE), // int
        i_set_sw(INT_TYPE), // int
        I_set(INT_TYPE), // int
        I_set_sw(INT_TYPE), // int
        l_set(LONG_TYPE), // long
        l_set_sw(LONG_TYPE), // long
        L_set(LONG_TYPE), // long
        L_set_sw(LONG_TYPE), // long
        q_set(LONG_TYPE), // long
        q_set_sw(LONG_TYPE), // long
        Q_set(LONG_TYPE), // long
        Q_set_sw(LONG_TYPE), // long
        P_set(POINTER_TYPE), // Pointer
        z_set(STRING_TYPE), // String
        u_set(STRING_TYPE), // String
        U_set(STRING_TYPE), // String
        Z_set(STRING_TYPE), // String
        vBOOL_set(BOOL_TYPE), // boolean
        bool_set(BOOL_TYPE), // boolean
        O_set(OBJECT_TYPE); // Object

        final int type;

        FieldSet(int type) {
            this.type = type;
        }

        boolean isType(int t) {
            return type == t;
        }
    }

    enum FieldGet {
        nil(0),

        s_get(BYTE_TYPE), // byte
        b_get(BYTE_TYPE), // byte
        B_get(BYTE_TYPE), // byte
        c_get(BYTE_TYPE), // byte
        d_get(DOUBLE_TYPE), // double
        d_get_sw(DOUBLE_TYPE), // double
        g_get(DOUBLE_TYPE), // double
        f_get(FLOAT_TYPE), // float
        f_get_sw(FLOAT_TYPE), // float
        h_get(SHORT_TYPE), // short
        h_get_sw(SHORT_TYPE), // short
        H_get(SHORT_TYPE), // short
        H_get_sw(SHORT_TYPE), // short
        i_get(INT_TYPE), // int
        i_get_sw(INT_TYPE), // int
        I_get(INT_TYPE), // int
        I_get_sw(INT_TYPE), // int
        l_get(LONG_TYPE), // long
        l_get_sw(LONG_TYPE), // long
        L_get(LONG_TYPE), // long
        L_get_sw(LONG_TYPE), // long
        q_get(LONG_TYPE), // long
        q_get_sw(LONG_TYPE), // long
        Q_get(LONG_TYPE), // long
        Q_get_sw(LONG_TYPE), // long
        P_get(POINTER_TYPE), // Pointer
        z_get(STRING_TYPE), // String
        u_get(STRING_TYPE), // String
        U_get(STRING_TYPE), // String
        Z_get(STRING_TYPE), // String
        vBOOL_get(BOOL_TYPE), // boolean
        bool_get(BOOL_TYPE), // boolean
        O_get(OBJECT_TYPE); // Object

        final int type;

        FieldGet(int type) {
            this.type = type;
        }

        boolean isType(int t) {
            return type == t;
        }
    }

    enum FieldDesc {
        // @formatter:off
        s('s', FieldSet.s_set, FieldGet.s_get, ffi_type_pointer), // String
        b('b', FieldSet.b_set, FieldGet.b_get, ffi_type_schar), // signed char
        B('B', FieldSet.B_set, FieldGet.B_get, ffi_type_uchar), // unsigned char
        c('c', FieldSet.c_set, FieldGet.c_get, ffi_type_schar), // char
        d('d', FieldSet.d_set, FieldGet.d_get, ffi_type_double, FieldSet.d_set_sw, FieldGet.d_get_sw), // double
        g('g', FieldSet.g_set, FieldGet.g_get, ffi_type_longdouble), // long double
        f('f', FieldSet.f_set, FieldGet.f_get, ffi_type_float, FieldSet.f_set_sw, FieldGet.f_get_sw), // float
        h('h', FieldSet.h_set, FieldGet.h_get, ffi_type_sshort, FieldSet.h_set_sw, FieldGet.h_get_sw), // short
        H('H', FieldSet.H_set, FieldGet.H_get, ffi_type_ushort, FieldSet.H_set_sw, FieldGet.H_get_sw), // unsigned short
        i('i', FieldSet.i_set, FieldGet.i_get, ffi_type_sint, FieldSet.i_set_sw, FieldGet.i_get_sw), // int
        I('I', FieldSet.I_set, FieldGet.I_get, ffi_type_uint, FieldSet.I_set_sw, FieldGet.I_get_sw), // unsigned int
        l('l', FieldSet.l_set, FieldGet.l_get, ffi_type_sint64, FieldSet.l_set_sw, FieldGet.l_get_sw), // long
        L('L', FieldSet.L_set, FieldGet.L_get, ffi_type_uint64, FieldSet.L_set_sw, FieldGet.L_get_sw), // unsigned long
        q('q', FieldSet.q_set, FieldGet.q_get, ffi_type_sint64, FieldSet.q_set_sw, FieldGet.q_get_sw), // long long
        Q('Q', FieldSet.Q_set, FieldGet.Q_get, ffi_type_uint64, FieldSet.Q_set_sw, FieldGet.Q_get_sw), // long long
        P('P', FieldSet.P_set, FieldGet.P_get, ffi_type_pointer), // Pointer
        z('z', FieldSet.z_set, FieldGet.z_get, nfi_type_string), // string
        u('u', FieldSet.u_set, FieldGet.u_get, nfi_type_string), // wchar_t CTYPES_UNICODE
        U('U', FieldSet.U_set, FieldGet.U_get, nfi_type_string), // String CTYPES_UNICODE
        Z('Z', FieldSet.Z_set, FieldGet.Z_get, nfi_type_string), // wchar_t
        v('v', FieldSet.vBOOL_set, FieldGet.vBOOL_get, ffi_type_sshort), // short int
        QM('?', FieldSet.bool_set, FieldGet.bool_get, ffi_type_uchar), // _Bool
        O('O', FieldSet.O_set, FieldGet.O_get, ffi_type_pointer); // PyObject
        // @formatter:on

        final FieldSet setfunc;
        final FieldGet getfunc;
        final FFIType pffi_type;
        final char code;

        final FieldSet setfunc_swapped;
        final FieldGet getfunc_swapped;

        FieldDesc(char code, FieldSet setfunc, FieldGet getfunc, FFIType type, FieldSet setfuncSwap, FieldGet getfuncSwap) {
            this.code = code;
            this.pffi_type = type;
            this.setfunc = setfunc;
            this.getfunc = getfunc;
            this.setfunc_swapped = setfuncSwap;
            this.getfunc_swapped = getfuncSwap;
        }

        FieldDesc(char code, FieldSet setfunc, FieldGet getfunc, FFIType type) {
            this(code, setfunc, getfunc, type, FieldSet.nil, FieldGet.nil);
        }
    }

    protected static FieldDesc _ctypes_get_fielddesc(char fmt) {
        if (fmt == '?') {
            return FieldDesc.QM;
        }
        for (FieldDesc t : FieldDesc.values()) {
            if (t.code == fmt) {
                return t;
            }
        }
        return null;
    }
}
