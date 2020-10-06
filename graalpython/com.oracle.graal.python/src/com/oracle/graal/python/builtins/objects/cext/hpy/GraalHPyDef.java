/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import java.util.Arrays;

import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.HiddenKey;

/**
 * A container class for mirroring definitions of {@code hpydef.h}
 */
public abstract class GraalHPyDef {

    public static final HiddenKey TYPE_HPY_BASICSIZE = new HiddenKey("hpy_basicsize");
    public static final HiddenKey TYPE_HPY_ITEMSIZE = new HiddenKey("hpy_itemsize");
    public static final HiddenKey TYPE_HPY_FLAGS = new HiddenKey("hpy_flags");
    public static final HiddenKey TYPE_HPY_DESTROY = new HiddenKey("hpy_destroy");
    public static final HiddenKey OBJECT_HPY_NATIVE_SPACE = new HiddenKey("hpy_native_space");

    /* enum values of 'HPyDef_Kind' */
    public static final int HPY_DEF_KIND_SLOT = 1;
    public static final int HPY_DEF_KIND_METH = 2;
    public static final int HPY_DEF_KIND_MEMBER = 3;
    public static final int HPY_DEF_KIND_GETSET = 4;

    /** Same as {@code HPyFuncSignature.Signature} */
    enum HPyFuncSignature {
        VARARGS(1),   // METH_VARARGS
        KEYWORDS(2),   // METH_VARARGS | METH_KEYWORDS
        NOARGS(3),   // METH_NOARGS
        O(4),   // METH_O
        DESTROYFUNC(5),
        UNARYFUNC(6),
        BINARYFUNC(7),
        TERNARYFUNC(8),
        INQUIRY(9),
        LENFUNC(10),
        SSIZEARGFUNC(11),
        SSIZESSIZEARGFUNC(12),
        SSIZEOBJARGPROC(13),
        SSIZESSIZEOBJARGPROC(14),
        OBJOBJARGPROC(15),
        FREEFUNC(16),
        GETATTRFUNC(17),
        GETATTROFUNC(18),
        SETATTRFUNC(19),
        SETATTROFUNC(20),
        REPRFUNC(21),
        HASHFUNC(22),
        RICHCMPFUNC(23),
        GETITERFUNC(24),
        ITERNEXTFUNC(25),
        DESCRGETFUNC(26),
        DESCRSETFUNC(27),
        INITPROC(28),
        GETTER(29),
        SETTER(30);

        /** The corresponding C enum value. */
        private final int value;

        HPyFuncSignature(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @CompilationFinal(dimensions = 1) private static final HPyFuncSignature[] VALUES = Arrays.copyOf(values(), values().length);

        @ExplodeLoop
        static HPyFuncSignature fromValue(int value) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].value == value) {
                    return VALUES[i];
                }
            }
            return null;
        }

        static boolean isValid(int value) {
            return fromValue(value) != null;
        }
    }

    /* enum values of 'HPyMember_FieldType' */
    public static final int HPY_MEMBER_SHORT = 0;
    public static final int HPY_MEMBER_INT = 1;
    public static final int HPY_MEMBER_LONG = 2;
    public static final int HPY_MEMBER_FLOAT = 3;
    public static final int HPY_MEMBER_DOUBLE = 4;
    public static final int HPY_MEMBER_STRING = 5;
    public static final int HPY_MEMBER_OBJECT = 6;
    public static final int HPY_MEMBER_CHAR = 7;
    public static final int HPY_MEMBER_BYTE = 8;
    public static final int HPY_MEMBER_UBYTE = 9;
    public static final int HPY_MEMBER_USHORT = 10;
    public static final int HPY_MEMBER_UINT = 11;
    public static final int HPY_MEMBER_ULONG = 12;
    public static final int HPY_MEMBER_STRING_INPLACE = 13;
    public static final int HPY_MEMBER_BOOL = 14;
    public static final int HPY_MEMBER_OBJECT_EX = 16;
    public static final int HPY_MEMBER_LONGLONG = 17;
    public static final int HPY_MEMBER_ULONGLONG = 18;
    public static final int HPY_MEMBER_HPYSSIZET = 19;
    public static final int HPY_MEMBER_NONE = 20;

    /* enum values of 'HPyType_SpecParam_Kind' */
    public static final int HPyType_SPEC_PARAM_BASE = 1;
    public static final int HPyType_SPEC_PARAM_BASES_TUPLE = 2;

    /* type flags according to 'hpytype.h' */
    public static final long _Py_TPFLAGS_HEAPTYPE = (1L << 9);
    public static final long HPy_TPFLAGS_BASETYPE = (1L << 10);
    public static final long HPy_TPFLAGS_DEFAULT = _Py_TPFLAGS_HEAPTYPE;

    /* enum values for 'HPySlot_Slot' */
    enum HPySlot {
        HPY_NB_ABSOLUTE(6, SpecialMethodNames.__ABS__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_ADD(7, SpecialMethodNames.__ADD__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_AND(8, SpecialMethodNames.__AND__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_BOOL(9, SpecialMethodNames.__BOOL__, HPyFuncSignature.INQUIRY),
        HPY_NB_DIVMOD(10, SpecialMethodNames.__DIVMOD__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_FLOAT(11, SpecialMethodNames.__FLOAT__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_FLOOR_DIVIDE(12, SpecialMethodNames.__FLOORDIV__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INDEX(13, SpecialMethodNames.__INDEX__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_INPLACE_ADD(14, SpecialMethodNames.__IADD__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_AND(15, SpecialMethodNames.__IAND__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_FLOOR_DIVIDE(16, SpecialMethodNames.__IFLOORDIV__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_LSHIFT(17, SpecialMethodNames.__ILSHIFT__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_MULTIPLY(18, SpecialMethodNames.__IMUL__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_OR(19, SpecialMethodNames.__IOR__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_POWER(20, SpecialMethodNames.__IPOW__, HPyFuncSignature.TERNARYFUNC),
        HPY_NB_INPLACE_REMAINDER(21, SpecialMethodNames.__IMOD__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_RSHIFT(22, SpecialMethodNames.__IRSHIFT__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_SUBTRACT(23, SpecialMethodNames.__ISUB__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_TRUE_DIVIDE(24, SpecialMethodNames.__ITRUEDIV__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_XOR(25, SpecialMethodNames.__IXOR__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INT(26, SpecialMethodNames.__INT__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_INVERT(27, SpecialMethodNames.__INVERT__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_LSHIFT(28, SpecialMethodNames.__LSHIFT__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_MULTIPLY(29, SpecialMethodNames.__MUL__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_NEGATIVE(30, SpecialMethodNames.__NEG__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_OR(31, SpecialMethodNames.__OR__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_POSITIVE(32, SpecialMethodNames.__POS__, HPyFuncSignature.UNARYFUNC),
        HPY_NB_POWER(33, SpecialMethodNames.__POW__, HPyFuncSignature.TERNARYFUNC),
        HPY_NB_REMAINDER(34, SpecialMethodNames.__MOD__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_RSHIFT(35, SpecialMethodNames.__RSHIFT__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_SUBTRACT(36, SpecialMethodNames.__SUB__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_TRUE_DIVIDE(37, SpecialMethodNames.__TRUEDIV__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_XOR(38, SpecialMethodNames.__XOR__, HPyFuncSignature.BINARYFUNC),
        HPY_SQ_ITEM(44, SpecialMethodNames.__GETITEM__, HPyFuncSignature.SSIZEARGFUNC),
        HPY_SQ_LENGTH(45, SpecialMethodNames.__LEN__, HPyFuncSignature.LENFUNC),
        HPY_TP_INIT(60, SpecialMethodNames.__INIT__, HPyFuncSignature.INITPROC),
        HPY_TP_NEW(65, SpecialMethodNames.__NEW__, HPyFuncSignature.KEYWORDS),
        HPY_TP_REPR(66, SpecialMethodNames.__REPR__, HPyFuncSignature.REPRFUNC),
        HPY_NB_MATRIX_MULTIPLY(75, SpecialMethodNames.__MATMUL__, HPyFuncSignature.BINARYFUNC),
        HPY_NB_INPLACE_MATRIX_MULTIPLY(76, SpecialMethodNames.__IMATMUL__, HPyFuncSignature.BINARYFUNC),
        // TODO(fa): use a hidden key ?
        HPY_TP_DESTROY(1000, TYPE_HPY_DESTROY, HPyFuncSignature.DESTROYFUNC);

        /** The corresponding C enum value. */
        private final int value;

        /**
         * The corresponding attribute key (mostly a {@link String} which is the name of a magic
         * method, or a {@link HiddenKey} if it's not exposed to the user, or {@code null} if
         * unsupported).
         */
        private final Object attributeKey;

        /** The signature of the slot function. */
        private final HPyFuncSignature signature;

        HPySlot(int value, Object attributeKey, HPyFuncSignature signature) {
            this.value = value;
            this.attributeKey = attributeKey;
            this.signature = signature;
        }

        int getValue() {
            return value;
        }

        Object getAttributeKey() {
            return attributeKey;
        }

        HPyFuncSignature getSignature() {
            return signature;
        }

        @CompilationFinal(dimensions = 1) private static final HPySlot[] VALUES = Arrays.copyOf(values(), values().length);

        @ExplodeLoop
        static HPySlot fromValue(int value) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].value == value) {
                    return VALUES[i];
                }
            }
            return null;
        }
    }
}
