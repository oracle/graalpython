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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public final class FFIType {

    private static final TruffleString T_LEFT_PAREN_COLON = tsLiteral("):");

    public static final FFIType ffi_type_pointer = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_POINTER);

    // Primitives
    public static final FFIType ffi_type_uint8 = new FFIType(Byte.BYTES, Byte.BYTES, FFI_TYPES.FFI_TYPE_UINT8);
    public static final FFIType ffi_type_sint8 = new FFIType(Byte.BYTES, Byte.BYTES, FFI_TYPES.FFI_TYPE_SINT8);
    public static final FFIType ffi_type_uint16 = new FFIType(Short.BYTES, Short.BYTES, FFI_TYPES.FFI_TYPE_UINT16);
    public static final FFIType ffi_type_sint16 = new FFIType(Short.BYTES, Short.BYTES, FFI_TYPES.FFI_TYPE_SINT16);
    public static final FFIType ffi_type_uint32 = new FFIType(Integer.BYTES, Integer.BYTES, FFI_TYPES.FFI_TYPE_UINT32);
    public static final FFIType ffi_type_sint32 = new FFIType(Integer.BYTES, Integer.BYTES, FFI_TYPES.FFI_TYPE_SINT32);
    public static final FFIType ffi_type_uint64 = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_UINT64);
    public static final FFIType ffi_type_sint64 = new FFIType(Long.BYTES, Long.BYTES, FFI_TYPES.FFI_TYPE_SINT64);
    public static final FFIType ffi_type_float = new FFIType(Float.BYTES, Float.BYTES, FFI_TYPES.FFI_TYPE_FLOAT);
    public static final FFIType ffi_type_double = new FFIType(Double.BYTES, Float.BYTES, FFI_TYPES.FFI_TYPE_DOUBLE);
    public static final FFIType ffi_type_uchar = ffi_type_uint8;
    public static final FFIType ffi_type_schar = ffi_type_sint8;
    public static final FFIType ffi_type_ushort = ffi_type_uint16;
    public static final FFIType ffi_type_sshort = ffi_type_sint16;
    public static final FFIType ffi_type_uint = ffi_type_uint32;
    public static final FFIType ffi_type_sint = ffi_type_sint32;
    // XXX there is no direct representation in java for long double
    public static final FFIType ffi_type_longdouble = ffi_type_double;

    public enum FFI_TYPES {
        /*
         * This type is only allowed as return type, and is used to denote functions that do not
         * return a value.
         *
         * Since in the Polyglot API, all executable objects have to return a value, a Polyglot
         * object with isNull == true will be returned from native functions that have a VOID return
         * type.
         *
         * The return value of managed callback functions with return type VOID will be ignored.
         */
        FFI_TYPE_VOID(tsLiteral("VOID"), 0), // `void`

        FFI_TYPE_UINT8(tsLiteral("UINT8"), Byte.BYTES),
        FFI_TYPE_SINT8(tsLiteral("SINT8"), Byte.BYTES),
        FFI_TYPE_UINT16(tsLiteral("UINT16"), Short.BYTES),
        FFI_TYPE_SINT16(tsLiteral("SINT16"), Short.BYTES),
        FFI_TYPE_UINT32(tsLiteral("UINT32"), Integer.BYTES),
        FFI_TYPE_SINT32(tsLiteral("SINT32"), Integer.BYTES),
        FFI_TYPE_UINT64(tsLiteral("UINT64"), Long.BYTES),
        FFI_TYPE_SINT64(tsLiteral("SINT64"), Long.BYTES),
        FFI_TYPE_FLOAT(tsLiteral("FLOAT"), Float.BYTES),
        FFI_TYPE_DOUBLE(tsLiteral("DOUBLE"), Double.BYTES),

        /*
         * This type is a generic pointer argument. On the native side, it does not matter what
         * exact pointer type the argument is.
         *
         * A polyglot object passed to POINTER arguments will be converted to a native pointer if
         * possible (using the isPointer, asPointer and toNative messages as necessary). An object
         * with isNull == true will be passed as a native NULL.
         *
         * POINTER return values will produce a polyglot object with isPointer == true. The native
         * NULL pointer will additionally have isNull == true.
         *
         * In addition, the returned pointer object will also have a method bind, and behave the
         * same as symbols loaded from an NFI library. When calling bind on such a pointer, it is
         * the user’s responsibility to ensure that the pointer really points to a function with a
         * matching signature.
         */
        FFI_TYPE_POINTER(tsLiteral("POINTER"), Long.BYTES), // `void *`

        FFI_TYPE_STRUCT(tsLiteral("POINTER"), Long.BYTES);

        private final TruffleString nfiType;
        private final int size;

        FFI_TYPES(TruffleString str, int size) {
            this.nfiType = str;
            this.size = size;
        }

        protected int getSize() {
            return size;
        }

        protected TruffleString getNFIType() {
            return nfiType;
        }
    }

    public int size;
    public int alignment; // short
    public FFI_TYPES type;
    FFIType[] elements;

    final CThunkObject callback;

    private FFIType(int size, int alignment, FFI_TYPES type, FFIType[] elements, CThunkObject callback) {
        this.size = size;
        this.alignment = alignment;
        this.type = type;
        this.elements = elements;
        this.callback = callback;
    }

    protected FFIType(int size, int alignment, FFI_TYPES type, FFIType[] elements) {
        this(size, alignment, type, elements, null);
    }

    protected FFIType(int size, int alignment, FFI_TYPES type) {
        this(size, alignment, type, null, null);
    }

    protected FFIType(FFIType copyFrom, CThunkObject callback) {
        this(copyFrom.size, copyFrom.alignment, copyFrom.type, copyFrom.elements, callback);
    }

    protected FFIType() {
        this(0, 0, FFI_TYPES.FFI_TYPE_VOID, null, null);
    }

    protected static int typeSize() {
        return 1;
    }

    protected boolean isCallback() {
        return callback != null;
    }

    private static final TruffleString UINT8_ARRAY = tsLiteral("[UINT8]");

    private static TruffleString getNFIType(FFIType type) {
        return type == ffi_type_pointer ? UINT8_ARRAY : type.type.getNFIType();
    }

    private static TruffleString getNFICallback(FFIType type) {
        return getNFIReturnType(type);
    }

    private static TruffleString getNFIReturnType(FFIType type) {
        return type.type.getNFIType();
    }

    @TruffleBoundary
    static TruffleString buildNFISignature(FFIType[] atypes, FFIType restype, boolean isCallback) {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        boolean first = true;
        TruffleStringBuilder.AppendStringNode appendStringNode = TruffleStringBuilder.AppendStringNode.getUncached();
        appendStringNode.execute(sb, T_LPAREN);
        for (FFIType type : atypes) {
            if (first) {
                first = false;
            } else {
                appendStringNode.execute(sb, T_COMMA_SPACE);
            }
            if (type.isCallback()) {
                TruffleString subSignature = buildNFISignature(type.callback.atypes, type.callback.ffi_restype, true);
                appendStringNode.execute(sb, subSignature);
            } else {
                appendStringNode.execute(sb, isCallback ? getNFICallback(type) : getNFIType(type));
            }
        }
        appendStringNode.execute(sb, T_LEFT_PAREN_COLON);
        appendStringNode.execute(sb, getNFIReturnType(restype));
        return TruffleStringBuilder.ToStringNode.getUncached().execute(sb);
    }

    @TruffleBoundary
    static TruffleString buildNFISignature(FFIType[] atypes, FFIType restype) {
        return buildNFISignature(atypes, restype, false);
    }

    enum FieldSet {
        nil,

        s_set, // byte
        b_set, // byte
        B_set, // byte
        c_set, // byte
        d_set, // double
        d_set_sw, // double
        g_set, // double
        f_set, // float
        f_set_sw, // float
        h_set, // short
        h_set_sw, // short
        H_set, // short
        H_set_sw, // short
        i_set, // int
        i_set_sw, // int
        I_set, // int
        I_set_sw, // int
        l_set, // long
        l_set_sw, // long
        L_set, // long
        L_set_sw, // long
        q_set, // long
        q_set_sw, // long
        Q_set, // long
        Q_set_sw, // long
        P_set, // Pointer
        z_set, // char *
        u_set, // String
        U_set, // String
        Z_set, // char *
        vBOOL_set, // boolean
        bool_set, // boolean
        O_set // Object
    }

    enum FieldGet {
        nil,

        s_get, // byte
        b_get, // byte
        B_get, // byte
        c_get, // byte
        d_get, // double
        d_get_sw, // double
        g_get, // double
        f_get, // float
        f_get_sw, // float
        h_get, // short
        h_get_sw, // short
        H_get, // short
        H_get_sw, // short
        i_get, // int
        i_get_sw, // int
        I_get, // int
        I_get_sw, // int
        l_get, // long
        l_get_sw, // long
        L_get, // long
        L_get_sw, // long
        q_get, // long
        q_get_sw, // long
        Q_get, // long
        Q_get_sw, // long
        P_get, // Pointer
        z_get, // char *
        u_get, // String
        U_get, // String
        Z_get, // char *
        vBOOL_get, // boolean
        bool_get, // boolean
        O_get // Object
    }

    enum FieldDesc {
        // @formatter:off
        s('s', FieldSet.s_set, FieldGet.s_get, ffi_type_schar), // ASCII String
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
        z('z', FieldSet.z_set, FieldGet.z_get, ffi_type_pointer), // ASCII String
        u('u', FieldSet.u_set, FieldGet.u_get, WCHAR_T_SIZE == 2 ? ffi_type_sint16 : ffi_type_sint32), // wchar_t CTYPES_UNICODE
        U('U', FieldSet.U_set, FieldGet.U_get, ffi_type_pointer), // Unicode String CTYPES_UNICODE
        Z('Z', FieldSet.Z_set, FieldGet.Z_get, ffi_type_pointer), // Unicode String wchar_t
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
