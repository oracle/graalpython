/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.Arrays;

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
        SETTER(30),
        OBJOBJPROC(31);

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
        HPY_NB_ABSOLUTE(6, HPyFuncSignature.UNARYFUNC, __ABS__),
        HPY_NB_ADD(7, HPyFuncSignature.BINARYFUNC, __ADD__),
        HPY_NB_AND(8, HPyFuncSignature.BINARYFUNC, __AND__),
        HPY_NB_BOOL(9, HPyFuncSignature.INQUIRY, __BOOL__),
        HPY_NB_DIVMOD(10, HPyFuncSignature.BINARYFUNC, __DIVMOD__),
        HPY_NB_FLOAT(11, HPyFuncSignature.UNARYFUNC, __FLOAT__),
        HPY_NB_FLOOR_DIVIDE(12, HPyFuncSignature.BINARYFUNC, __FLOORDIV__),
        HPY_NB_INDEX(13, HPyFuncSignature.UNARYFUNC, __INDEX__),
        HPY_NB_INPLACE_ADD(14, HPyFuncSignature.BINARYFUNC, __IADD__),
        HPY_NB_INPLACE_AND(15, HPyFuncSignature.BINARYFUNC, __IAND__),
        HPY_NB_INPLACE_FLOOR_DIVIDE(16, HPyFuncSignature.BINARYFUNC, __IFLOORDIV__),
        HPY_NB_INPLACE_LSHIFT(17, HPyFuncSignature.BINARYFUNC, __ILSHIFT__),
        HPY_NB_INPLACE_MULTIPLY(18, HPyFuncSignature.BINARYFUNC, __IMUL__),
        HPY_NB_INPLACE_OR(19, HPyFuncSignature.BINARYFUNC, __IOR__),
        HPY_NB_INPLACE_POWER(20, HPyFuncSignature.TERNARYFUNC, __IPOW__),
        HPY_NB_INPLACE_REMAINDER(21, HPyFuncSignature.BINARYFUNC, __IMOD__),
        HPY_NB_INPLACE_RSHIFT(22, HPyFuncSignature.BINARYFUNC, __IRSHIFT__),
        HPY_NB_INPLACE_SUBTRACT(23, HPyFuncSignature.BINARYFUNC, __ISUB__),
        HPY_NB_INPLACE_TRUE_DIVIDE(24, HPyFuncSignature.BINARYFUNC, __ITRUEDIV__),
        HPY_NB_INPLACE_XOR(25, HPyFuncSignature.BINARYFUNC, __IXOR__),
        HPY_NB_INT(26, HPyFuncSignature.UNARYFUNC, __INT__),
        HPY_NB_INVERT(27, HPyFuncSignature.UNARYFUNC, __INVERT__),
        HPY_NB_LSHIFT(28, HPyFuncSignature.BINARYFUNC, __LSHIFT__),
        HPY_NB_MULTIPLY(29, HPyFuncSignature.BINARYFUNC, __MUL__),
        HPY_NB_NEGATIVE(30, HPyFuncSignature.UNARYFUNC, __NEG__),
        HPY_NB_OR(31, HPyFuncSignature.BINARYFUNC, __OR__),
        HPY_NB_POSITIVE(32, HPyFuncSignature.UNARYFUNC, __POS__),
        HPY_NB_POWER(33, HPyFuncSignature.TERNARYFUNC, __POW__),
        HPY_NB_REMAINDER(34, HPyFuncSignature.BINARYFUNC, __MOD__),
        HPY_NB_RSHIFT(35, HPyFuncSignature.BINARYFUNC, __RSHIFT__),
        HPY_NB_SUBTRACT(36, HPyFuncSignature.BINARYFUNC, __SUB__),
        HPY_NB_TRUE_DIVIDE(37, HPyFuncSignature.BINARYFUNC, __TRUEDIV__),
        HPY_NB_XOR(38, HPyFuncSignature.BINARYFUNC, __XOR__),
        HPY_SQ_ASS_ITEM(39, HPyFuncSignature.SSIZEOBJARGPROC, __SETITEM__, __DELITEM__),
        HPY_SQ_CONCAT(40, HPyFuncSignature.BINARYFUNC, __ADD__),
        HPY_SQ_CONTAINS(41, HPyFuncSignature.OBJOBJPROC, __CONTAINS__),
        HPY_SQ_INPLACE_CONCAT(42, HPyFuncSignature.BINARYFUNC, __IADD__),
        HPY_SQ_INPLACE_REPEAT(43, HPyFuncSignature.SSIZEARGFUNC, __IMUL__),
        HPY_SQ_ITEM(44, HPyFuncSignature.SSIZEARGFUNC, __GETITEM__),
        HPY_SQ_LENGTH(45, HPyFuncSignature.LENFUNC, __LEN__),
        HPY_SQ_REPEAT(46, HPyFuncSignature.SSIZEARGFUNC, __MUL__),
        HPY_TP_INIT(60, HPyFuncSignature.INITPROC, __INIT__),
        HPY_TP_NEW(65, HPyFuncSignature.KEYWORDS, __NEW__),
        HPY_TP_REPR(66, HPyFuncSignature.REPRFUNC, __REPR__),
        HPY_NB_MATRIX_MULTIPLY(75, HPyFuncSignature.BINARYFUNC, __MATMUL__),
        HPY_NB_INPLACE_MATRIX_MULTIPLY(76, HPyFuncSignature.BINARYFUNC, __IMATMUL__),
        HPY_TP_DESTROY(1000, HPyFuncSignature.DESTROYFUNC, TYPE_HPY_DESTROY);

        /** The corresponding C enum value. */
        private final int value;

        /**
         * The corresponding attribute key (mostly a {@link String} which is the name of a magic
         * method, or a {@link HiddenKey} if it's not exposed to the user, or {@code null} if
         * unsupported).
         */
        @CompilationFinal(dimensions = 1) private final Object[] attributeKeys;

        /** The signatures of the slot functions. */
        @CompilationFinal(dimensions = 1) private final HPyFuncSignature[] signatures;

        /**
         * Common case: one slot causes the creation of one attribute.
         */
        HPySlot(int value, HPyFuncSignature signature, Object attributeKey) {
            this.value = value;
            this.attributeKeys = new Object[]{attributeKey};
            this.signatures = new HPyFuncSignature[]{signature};
        }

        /**
         * Special case: one slot causes the creation of multiple attributes using the same
         * signature.
         */
        HPySlot(int value, HPyFuncSignature signature, Object... attributeKeys) {
            this.value = value;
            this.attributeKeys = attributeKeys;
            this.signatures = new HPyFuncSignature[this.attributeKeys.length];
            for (int i = 0; i < this.signatures.length; i++) {
                this.signatures[i] = signature;
            }
        }

        int getValue() {
            return value;
        }

        Object[] getAttributeKeys() {
            return attributeKeys;
        }

        HPyFuncSignature[] getSignatures() {
            return signatures;
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
