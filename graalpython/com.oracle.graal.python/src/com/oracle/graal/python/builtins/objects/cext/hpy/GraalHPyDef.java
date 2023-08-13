/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_EQ;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_GE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_GT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_LE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_LT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper.RICHCMP_NE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RLSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNew;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A container class for mirroring definitions of {@code hpydef.h}
 */
public abstract class GraalHPyDef {

    public static final HiddenKey TYPE_HPY_ITEMSIZE = new HiddenKey("hpy_itemsize");
    public static final HiddenKey TYPE_HPY_FLAGS = new HiddenKey("hpy_flags");
    public static final HiddenKey TYPE_HPY_BUILTIN_SHAPE = new HiddenKey("hpy_builtin_shape");

    /* enum values of 'HPyDef_Kind' */
    public static final int HPY_DEF_KIND_SLOT = 1;
    public static final int HPY_DEF_KIND_METH = 2;
    public static final int HPY_DEF_KIND_MEMBER = 3;
    public static final int HPY_DEF_KIND_GETSET = 4;

    /**
     * Same as {@code HPyFunc_Signature}.
     */
    enum HPyFuncSignature {
        VARARGS(1, LLVMType.HPyFunc_varargs),   // METH_VARARGS
        KEYWORDS(2, LLVMType.HPyFunc_keywords),   // METH_VARARGS | METH_KEYWORDS
        NOARGS(3, LLVMType.HPyFunc_noargs),   // METH_NOARGS
        O(4, LLVMType.HPyFunc_o),   // METH_O
        DESTROYFUNC(5, LLVMType.HPyFunc_destroyfunc),
        GETBUFFERPROC(6, LLVMType.HPyFunc_getbufferproc),
        RELEASEBUFFERPROC(7, LLVMType.HPyFunc_releasebufferproc),
        UNARYFUNC(8, LLVMType.HPyFunc_unaryfunc),
        BINARYFUNC(9, LLVMType.HPyFunc_binaryfunc),
        TERNARYFUNC(10, LLVMType.HPyFunc_ternaryfunc),
        INQUIRY(11, LLVMType.HPyFunc_inquiry),
        LENFUNC(12, LLVMType.HPyFunc_lenfunc),
        SSIZEARGFUNC(13, LLVMType.HPyFunc_ssizeargfunc),
        SSIZESSIZEARGFUNC(14, LLVMType.HPyFunc_ssizessizeargfunc),
        SSIZEOBJARGPROC(15, LLVMType.HPyFunc_ssizeobjargproc),
        SSIZESSIZEOBJARGPROC(16, LLVMType.HPyFunc_ssizessizeobjargproc),
        OBJOBJARGPROC(17, LLVMType.HPyFunc_objobjargproc),
        FREEFUNC(18, LLVMType.HPyFunc_freefunc),
        GETATTRFUNC(19, LLVMType.HPyFunc_getattrfunc),
        GETATTROFUNC(20, LLVMType.HPyFunc_getattrofunc),
        SETATTRFUNC(21, LLVMType.HPyFunc_setattrfunc),
        SETATTROFUNC(22, LLVMType.HPyFunc_setattrofunc),
        REPRFUNC(23, LLVMType.HPyFunc_reprfunc),
        HASHFUNC(24, LLVMType.HPyFunc_hashfunc),
        RICHCMPFUNC(25, LLVMType.HPyFunc_richcmpfunc),
        GETITERFUNC(26, LLVMType.HPyFunc_getiterfunc),
        ITERNEXTFUNC(27, LLVMType.HPyFunc_iternextfunc),
        DESCRGETFUNC(28, LLVMType.HPyFunc_descrgetfunc),
        DESCRSETFUNC(29, LLVMType.HPyFunc_descrsetfunc),
        INITPROC(30, LLVMType.HPyFunc_initproc),
        GETTER(31, LLVMType.HPyFunc_getter),
        SETTER(32, LLVMType.HPyFunc_setter),
        OBJOBJPROC(33, LLVMType.HPyFunc_objobjproc);

        /** The corresponding C enum value. */
        private final int value;

        /** The C function's type (basically it's signature). */
        private final LLVMType llvmFunctionType;

        HPyFuncSignature(int value, LLVMType llvmFunctionType) {
            this.value = value;
            this.llvmFunctionType = llvmFunctionType;
        }

        public int getValue() {
            return value;
        }

        public LLVMType getLLVMFunctionType() {
            return llvmFunctionType;
        }

        @CompilationFinal(dimensions = 1) private static final HPyFuncSignature[] VALUES = values();
        @CompilationFinal(dimensions = 1) private static final HPyFuncSignature[] BY_VALUE = new HPyFuncSignature[40];

        static {
            for (var entry : VALUES) {
                assert BY_VALUE[entry.value] == null;
                BY_VALUE[entry.value] = entry;
            }
        }

        static HPyFuncSignature fromValue(int value) {
            return value >= 0 && value < BY_VALUE.length ? BY_VALUE[value] : null;
        }

        public static int getFlags(HPyFuncSignature sig) {
            switch (sig) {
                case VARARGS:
                    return CExtContext.METH_VARARGS;
                case KEYWORDS:
                    return CExtContext.METH_VARARGS | CExtContext.METH_KEYWORDS;
                case NOARGS:
                    return CExtContext.METH_NOARGS;
                case O:
                    return CExtContext.METH_O;
            }
            return 0;
        }

        static boolean isValid(int value) {
            return fromValue(value) != null;
        }
    }

    /**
     * An enumeration of all available slot wrappers as used by CPython (see
     * {@code typeobject.c: slotdefs}. Each enum value (except of {@link #NULL},
     * {@link #DESTROYFUNC}, {@link #GETBUFFER}, and {@link #RELEASEBUFFER}) corresponds to a
     * wrapper function which name starts with {@code wrap_}. For example, value {@link #UNARYFUNC}
     * corresponds to wrapper function {@code wrap_unaryfunc}.
     */
    enum HPySlotWrapper {
        NULL(LLVMType.HPyFunc_keywords),
        UNARYFUNC(LLVMType.HPyFunc_unaryfunc),
        BINARYFUNC(LLVMType.HPyFunc_binaryfunc),
        BINARYFUNC_L(LLVMType.HPyFunc_binaryfunc),
        BINARYFUNC_R(LLVMType.HPyFunc_binaryfunc),
        CALL(LLVMType.HPyFunc_keywords),
        HASHFUNC(LLVMType.HPyFunc_hashfunc),
        TERNARYFUNC(LLVMType.HPyFunc_ternaryfunc),
        TERNARYFUNC_R(LLVMType.HPyFunc_ternaryfunc),
        INQUIRYPRED(LLVMType.HPyFunc_inquiry),
        DEL,
        INIT(LLVMType.HPyFunc_initproc),
        LENFUNC(LLVMType.HPyFunc_lenfunc),
        DELITEM,
        SQ_ITEM(LLVMType.HPyFunc_ssizeargfunc),
        SQ_SETITEM(LLVMType.HPyFunc_ssizeobjargproc),
        SQ_DELITEM(LLVMType.HPyFunc_ssizeobjargproc),
        OBJOBJARGPROC(LLVMType.HPyFunc_objobjargproc),
        OBJOBJPROC(LLVMType.HPyFunc_objobjproc),
        INDEXARGFUNC(LLVMType.HPyFunc_ssizeargfunc),
        SETATTR(LLVMType.HPyFunc_setattrfunc),
        DELATTR(LLVMType.HPyFunc_setattrfunc),
        RICHCMP_LT(LLVMType.HPyFunc_richcmpfunc),
        RICHCMP_LE(LLVMType.HPyFunc_richcmpfunc),
        RICHCMP_EQ(LLVMType.HPyFunc_richcmpfunc),
        RICHCMP_NE(LLVMType.HPyFunc_richcmpfunc),
        RICHCMP_GT(LLVMType.HPyFunc_richcmpfunc),
        RICHCMP_GE(LLVMType.HPyFunc_richcmpfunc),
        DESCR_GET(LLVMType.HPyFunc_descrgetfunc),
        DESCR_SET(LLVMType.HPyFunc_descrsetfunc),
        DESCR_DELETE(LLVMType.HPyFunc_descrsetfunc),
        DESTROYFUNC(LLVMType.HPyFunc_destroyfunc),
        TRAVERSE(LLVMType.HPyFunc_traverseproc),
        DESTRUCTOR(LLVMType.HPyFunc_destructor),
        GETBUFFER(LLVMType.HPyFunc_getbufferproc),
        RELEASEBUFFER(LLVMType.HPyFunc_releasebufferproc),
        MOD_CREATE(LLVMType.HPyModule_create);

        /** The C function's type (basically it's signature). */
        private final LLVMType llvmFunctionType;

        HPySlotWrapper() {
            this.llvmFunctionType = null;
        }

        HPySlotWrapper(LLVMType llvmFunctionType) {
            this.llvmFunctionType = llvmFunctionType;
        }

        public LLVMType getLLVMFunctionType() {
            return llvmFunctionType;
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
    public static final int HPyType_SPEC_PARAM_METACLASS = 3;

    /* type flags according to 'hpytype.h' */
    public static final long _Py_TPFLAGS_HEAPTYPE = (1L << 9);
    public static final long HPy_TPFLAGS_BASETYPE = (1L << 10);
    public static final long HPy_TPFLAGS_HAVE_GC = (1L << 14);
    public static final long HPy_TPFLAGS_DEFAULT = _Py_TPFLAGS_HEAPTYPE;

    /* enum values of 'HPyType_BuiltinShape' */
    public static final int HPyType_BUILTIN_SHAPE_LEGACY = -1;
    public static final int HPyType_BUILTIN_SHAPE_OBJECT = 0;
    public static final int HPyType_BUILTIN_SHAPE_TYPE = 1;
    public static final int HPyType_BUILTIN_SHAPE_LONG = 2;
    public static final int HPyType_BUILTIN_SHAPE_FLOAT = 3;
    public static final int HPyType_BUILTIN_SHAPE_UNICODE = 4;
    public static final int HPyType_BUILTIN_SHAPE_TUPLE = 5;
    public static final int HPyType_BUILTIN_SHAPE_LIST = 6;

    /* enum values for 'HPySlot_Slot' */
    enum HPySlot {
        HPY_BF_GETBUFFER(1, HPySlotWrapper.GETBUFFER, TypeBuiltins.TYPE_GETBUFFER),
        HPY_BF_RELEASEBUFFER(2, HPySlotWrapper.RELEASEBUFFER, TypeBuiltins.TYPE_RELEASEBUFFER),
        HPY_MP_ASS_SUBSCRRIPT(3, HPySlotWrapper.OBJOBJARGPROC, T___SETITEM__, T___DELITEM__),
        HPY_MP_LENGTH(4, HPySlotWrapper.LENFUNC, T___LEN__),
        HPY_MP_SUBSCRIPT(5, HPySlotWrapper.BINARYFUNC, T___GETITEM__),
        HPY_NB_ABSOLUTE(6, HPySlotWrapper.UNARYFUNC, T___ABS__),
        HPY_NB_ADD(7, HPySlotWrapper.BINARYFUNC_L, T___ADD__, HPySlotWrapper.BINARYFUNC_R, T___RADD__),
        HPY_NB_AND(8, HPySlotWrapper.BINARYFUNC_L, T___AND__, HPySlotWrapper.BINARYFUNC_R, T___RAND__),
        HPY_NB_BOOL(9, HPySlotWrapper.INQUIRYPRED, T___BOOL__),
        HPY_NB_DIVMOD(10, HPySlotWrapper.BINARYFUNC_L, T___DIVMOD__),
        HPY_NB_FLOAT(11, HPySlotWrapper.UNARYFUNC, T___FLOAT__),
        HPY_NB_FLOOR_DIVIDE(12, HPySlotWrapper.BINARYFUNC_L, T___FLOORDIV__, HPySlotWrapper.BINARYFUNC_R, T___RFLOORDIV__),
        HPY_NB_INDEX(13, HPySlotWrapper.UNARYFUNC, T___INDEX__),
        HPY_NB_INPLACE_ADD(14, HPySlotWrapper.BINARYFUNC_L, T___IADD__),
        HPY_NB_INPLACE_AND(15, HPySlotWrapper.BINARYFUNC_L, T___IAND__),
        HPY_NB_INPLACE_FLOOR_DIVIDE(16, HPySlotWrapper.BINARYFUNC_L, T___IFLOORDIV__),
        HPY_NB_INPLACE_LSHIFT(17, HPySlotWrapper.BINARYFUNC_L, T___ILSHIFT__),
        HPY_NB_INPLACE_MULTIPLY(18, HPySlotWrapper.BINARYFUNC_L, T___IMUL__),
        HPY_NB_INPLACE_OR(19, HPySlotWrapper.BINARYFUNC_L, T___IOR__),
        HPY_NB_INPLACE_POWER(20, HPySlotWrapper.TERNARYFUNC, T___IPOW__),
        HPY_NB_INPLACE_REMAINDER(21, HPySlotWrapper.BINARYFUNC_L, T___IMOD__),
        HPY_NB_INPLACE_RSHIFT(22, HPySlotWrapper.BINARYFUNC_L, T___IRSHIFT__),
        HPY_NB_INPLACE_SUBTRACT(23, HPySlotWrapper.BINARYFUNC_L, T___ISUB__),
        HPY_NB_INPLACE_TRUE_DIVIDE(24, HPySlotWrapper.BINARYFUNC_L, T___ITRUEDIV__),
        HPY_NB_INPLACE_XOR(25, HPySlotWrapper.BINARYFUNC_L, T___IXOR__),
        HPY_NB_INT(26, HPySlotWrapper.UNARYFUNC, T___INT__),
        HPY_NB_INVERT(27, HPySlotWrapper.UNARYFUNC, T___INVERT__),
        HPY_NB_LSHIFT(28, HPySlotWrapper.BINARYFUNC_L, T___LSHIFT__, HPySlotWrapper.BINARYFUNC_R, T___RLSHIFT__),
        HPY_NB_MULTIPLY(29, HPySlotWrapper.BINARYFUNC_L, T___MUL__, HPySlotWrapper.BINARYFUNC_R, T___RMUL__),
        HPY_NB_NEGATIVE(30, HPySlotWrapper.UNARYFUNC, T___NEG__),
        HPY_NB_OR(31, HPySlotWrapper.BINARYFUNC_L, T___OR__, HPySlotWrapper.BINARYFUNC_R, T___ROR__),
        HPY_NB_POSITIVE(32, HPySlotWrapper.UNARYFUNC, T___POS__),
        HPY_NB_POWER(33, HPySlotWrapper.TERNARYFUNC, T___POW__),
        HPY_NB_REMAINDER(34, HPySlotWrapper.BINARYFUNC_L, T___MOD__, HPySlotWrapper.BINARYFUNC_R, T___RMOD__),
        HPY_NB_RSHIFT(35, HPySlotWrapper.BINARYFUNC_L, T___RSHIFT__, HPySlotWrapper.BINARYFUNC_R, T___RRSHIFT__),
        HPY_NB_SUBTRACT(36, HPySlotWrapper.BINARYFUNC_L, T___SUB__, HPySlotWrapper.BINARYFUNC_R, T___RSUB__),
        HPY_NB_TRUE_DIVIDE(37, HPySlotWrapper.BINARYFUNC_L, T___TRUEDIV__, HPySlotWrapper.BINARYFUNC_R, T___RTRUEDIV__),
        HPY_NB_XOR(38, HPySlotWrapper.BINARYFUNC_L, T___XOR__, HPySlotWrapper.BINARYFUNC_R, T___RXOR__),
        HPY_SQ_ASS_ITEM(39, HPySlotWrapper.SQ_SETITEM, T___SETITEM__, HPySlotWrapper.SQ_DELITEM, T___DELITEM__),
        HPY_SQ_CONCAT(40, HPySlotWrapper.BINARYFUNC_L, T___ADD__),
        HPY_SQ_CONTAINS(41, HPySlotWrapper.OBJOBJPROC, T___CONTAINS__),
        HPY_SQ_INPLACE_CONCAT(42, HPySlotWrapper.BINARYFUNC_L, T___IADD__),
        HPY_SQ_INPLACE_REPEAT(43, HPySlotWrapper.INDEXARGFUNC, T___IMUL__),
        HPY_SQ_ITEM(44, HPySlotWrapper.SQ_ITEM, T___GETITEM__),
        HPY_SQ_LENGTH(45, HPySlotWrapper.LENFUNC, T___LEN__),
        HPY_SQ_REPEAT(46, HPySlotWrapper.INDEXARGFUNC, T___MUL__, T___RMUL__),
        HPY_TP_CALL(50, HPySlotWrapper.CALL, T___CALL__),
        HPY_TP_HASH(59, HPySlotWrapper.HASHFUNC, T___HASH__),
        HPY_TP_INIT(60, HPySlotWrapper.INIT, T___INIT__),
        HPY_TP_ITER(62, HPySlotWrapper.UNARYFUNC, T___ITER__),
        HPY_TP_NEW(65, HPySlotWrapper.NULL, T___NEW__),
        HPY_TP_REPR(66, HPySlotWrapper.UNARYFUNC, T___REPR__),
        HPY_TP_RICHCOMPARE(67, w(RICHCMP_LT, RICHCMP_LE, RICHCMP_EQ, RICHCMP_NE, RICHCMP_GT, RICHCMP_GE), k(T___LT__, T___LE__, T___EQ__, T___NE__, T___GT__, T___GE__)),
        HPY_TP_STR(70, HPySlotWrapper.UNARYFUNC, T___STR__),
        HPY_TP_TRAVERSE(71, HPySlotWrapper.TRAVERSE),
        HPY_NB_MATRIX_MULTIPLY(75, HPySlotWrapper.BINARYFUNC_L, T___MATMUL__, HPySlotWrapper.BINARYFUNC_R, T___RMATMUL__),
        HPY_NB_INPLACE_MATRIX_MULTIPLY(76, HPySlotWrapper.BINARYFUNC_L, T___IMATMUL__),
        HPY_TP_FINALIZE(80, HPySlotWrapper.DESTRUCTOR),
        HPY_TP_DESTROY(1000, HPySlotWrapper.DESTROYFUNC),
        HPY_MOD_CREATE(2000, HPySlotWrapper.MOD_CREATE),
        HPY_MOD_EXEC(2001, HPySlotWrapper.INQUIRYPRED);

        /** The corresponding C enum value. */
        private final int value;

        /**
         * The corresponding attribute key (mostly a {@link TruffleString} which is the name of a
         * magic method, or a {@link HiddenKey} if it's not exposed to the user, or {@code null} if
         * unsupported).
         */
        @CompilationFinal(dimensions = 1) private final Object[] attributeKeys;

        /** The signatures of the slot functions. */
        @CompilationFinal(dimensions = 1) private final HPySlotWrapper[] signatures;

        /**
         * Common case: one slot causes the creation of one attribute.
         */
        HPySlot(int value, HPySlotWrapper signature, HiddenKey attributeKey) {
            this.value = value;
            this.attributeKeys = new Object[]{attributeKey};
            this.signatures = new HPySlotWrapper[]{signature};
        }

        /**
         * Special case: one slot causes the creation of multiple attributes using the same slot
         * wrapper.
         */
        HPySlot(int value, HPySlotWrapper signature, TruffleString... attributeKeys) {
            this.value = value;
            this.attributeKeys = attributeKeys;
            if (attributeKeys.length > 0) {
                this.signatures = new HPySlotWrapper[attributeKeys.length];
                Arrays.fill(this.signatures, signature);
            } else {
                this.signatures = new HPySlotWrapper[]{signature};
            }
        }

        /**
         * Special case: one slot causes the creation of two attributes using different slot
         * wrappers.
         */
        HPySlot(int value, HPySlotWrapper sig0, TruffleString key0, HPySlotWrapper sig1, TruffleString key1) {
            this.value = value;
            this.attributeKeys = new Object[]{key0, key1};
            this.signatures = new HPySlotWrapper[]{sig0, sig1};
        }

        /**
         * Generic case: one slot causes the creation of multiple attributes with each different
         * slot wrappers.
         */
        HPySlot(int value, HPySlotWrapper[] sigs, TruffleString... keys) {
            this.value = value;
            this.attributeKeys = keys;
            this.signatures = sigs;
        }

        int getValue() {
            return value;
        }

        Object[] getAttributeKeys() {
            return attributeKeys;
        }

        HPySlotWrapper[] getSignatures() {
            return signatures;
        }

        @CompilationFinal(dimensions = 1) private static final HPySlot[] BY_VALUE = new HPySlot[100];

        static {
            for (var entry : values()) {
                if (entry.value >= 0 && entry.value < BY_VALUE.length) {
                    assert BY_VALUE[entry.value] == null;
                    BY_VALUE[entry.value] = entry;
                }
            }
        }

        static HPySlot fromValue(int value) {
            if (value >= 0 && value < BY_VALUE.length) {
                return BY_VALUE[value];
            }
            if (HPY_TP_DESTROY.value == value) {
                return HPY_TP_DESTROY;
            } else if (HPY_MOD_CREATE.value == value) {
                return HPY_MOD_CREATE;
            } else if (HPY_MOD_EXEC.value == value) {
                return HPY_MOD_EXEC;
            }
            return null;
        }

        private static HPySlotWrapper[] w(HPySlotWrapper... wrappers) {
            return wrappers;
        }

        private static TruffleString[] k(TruffleString... keys) {
            return keys;
        }
    }

    public static boolean isValidBuiltinShape(int i) {
        return HPyType_BUILTIN_SHAPE_LEGACY <= i && i <= HPyType_BUILTIN_SHAPE_LIST;
    }

    public static int getBuiltinShapeFromHiddenAttribute(Object object) {
        if (object instanceof PythonClass pythonClass) {
            return pythonClass.getBuiltinShape();
        } else if (object instanceof PythonAbstractNativeObject) {
            assert IsTypeNode.executeUncached(object);
            return HPyType_BUILTIN_SHAPE_LEGACY;
        }
        return -2; // error
    }

    static PythonBuiltinClassType getBuiltinClassType(int builtinShape) {
        return switch (builtinShape) {
            case HPyType_BUILTIN_SHAPE_LEGACY, HPyType_BUILTIN_SHAPE_OBJECT -> PythonBuiltinClassType.PythonObject;
            case HPyType_BUILTIN_SHAPE_TYPE -> PythonBuiltinClassType.PythonClass;
            case HPyType_BUILTIN_SHAPE_LONG -> PythonBuiltinClassType.PInt;
            case HPyType_BUILTIN_SHAPE_FLOAT -> PythonBuiltinClassType.PFloat;
            case HPyType_BUILTIN_SHAPE_UNICODE -> PythonBuiltinClassType.PString;
            case HPyType_BUILTIN_SHAPE_TUPLE -> PythonBuiltinClassType.PTuple;
            case HPyType_BUILTIN_SHAPE_LIST -> PythonBuiltinClassType.PList;
            default -> throw CompilerDirectives.shouldNotReachHere(GraalHPyNew.INVALID_BUILT_IN_SHAPE);
        };
    }
}
