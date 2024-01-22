/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMappingMethods__mp_ass_subscript;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMappingMethods__mp_length;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMappingMethods__mp_subscript;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_absolute;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_add;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_and;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_bool;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_divmod;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_float;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_floor_divide;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_index;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_add;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_and;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_floor_divide;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_lshift;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_multiply;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_or;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_power;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_remainder;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_rshift;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_subtract;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_true_divide;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_inplace_xor;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_int;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_invert;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_lshift;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_multiply;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_negative;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_or;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_positive;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_power;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_remainder;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_rshift;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_subtract;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_true_divide;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyNumberMethods__nb_xor;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySequenceMethods__sq_ass_item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySequenceMethods__sq_concat;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySequenceMethods__sq_item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySequenceMethods__sq_length;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySequenceMethods__sq_repeat;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_mapping;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_number;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_sequence;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_call;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_descr_get;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_descr_set;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_getattro;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_hash;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_init;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_iter;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_iternext;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_repr;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_richcompare;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_setattro;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_str;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinaryFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrGetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.GetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.HashfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InitWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InquiryWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.LenfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.RichcmpFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SsizeargfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TernaryFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.UnaryFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum SlotMethodDef {
    TP_CALL(PyTypeObject__tp_call, T___CALL__, TernaryFunctionWrapper::new),
    TP_GETATTRO(PyTypeObject__tp_getattro, T___GETATTRIBUTE__, GetAttrWrapper::new),
    TP_HASH(PyTypeObject__tp_hash, T___HASH__, HashfuncWrapper::new),
    TP_INIT(PyTypeObject__tp_init, T___INIT__, InitWrapper::new),
    TP_ITER(PyTypeObject__tp_iter, T___ITER__, UnaryFuncWrapper::new),
    TP_ITERNEXT(PyTypeObject__tp_iternext, T___NEXT__, UnaryFuncWrapper::new),
    TP_REPR(PyTypeObject__tp_repr, T___REPR__, UnaryFuncWrapper::new),
    TP_RICHCOMPARE(PyTypeObject__tp_richcompare, T_RICHCMP, RichcmpFunctionWrapper::new),
    TP_SETATTRO(PyTypeObject__tp_setattro, T___SETATTR__, SetAttrWrapper::new),
    TP_STR(PyTypeObject__tp_str, T___STR__, UnaryFuncWrapper::new),
    TP_DESCR_GET(PyTypeObject__tp_descr_get, T___GET__, DescrGetFunctionWrapper::new),
    TP_DESCR_SET(PyTypeObject__tp_descr_set, T___SET__, SetAttrWrapper::new),

    MP_LENGTH(PyMappingMethods__mp_length, T___LEN__, LenfuncWrapper::new, MethodsFlags.MP_LENGTH),
    MP_SUBSCRIPT(PyMappingMethods__mp_subscript, T___GETITEM__, BinaryFuncWrapper::new, MethodsFlags.MP_SUBSCRIPT),
    MP_ASS_SUBSCRIPT(PyMappingMethods__mp_ass_subscript, T___SETITEM__, SetAttrWrapper::new, MethodsFlags.MP_ASS_SUBSCRIPT),

    SQ_LENGTH(PySequenceMethods__sq_length, T___LEN__, LenfuncWrapper::new, MethodsFlags.SQ_LENGTH),
    SQ_ITEM(PySequenceMethods__sq_item, T___GETITEM__, SsizeargfuncWrapper::new, MethodsFlags.SQ_ITEM),
    SQ_ASS_ITEM(PySequenceMethods__sq_ass_item, T___SETITEM__, SetAttrWrapper::new, MethodsFlags.SQ_ASS_ITEM),
    SQ_REPEAT(PySequenceMethods__sq_repeat, T___MUL__, SsizeargfuncWrapper::new, MethodsFlags.SQ_REPEAT),
    SQ_CONCAT(PySequenceMethods__sq_concat, T___ADD__, BinaryFuncWrapper::new, MethodsFlags.SQ_CONCAT),

    NB_ABSOLUTE(PyNumberMethods__nb_absolute, T___ABS__, UnaryFuncWrapper::new, MethodsFlags.NB_ABSOLUTE),
    NB_ADD(PyNumberMethods__nb_add, T___ADD__, BinaryFuncWrapper::new, MethodsFlags.NB_ADD),
    NB_AND(PyNumberMethods__nb_and, T___AND__, BinaryFuncWrapper::new, MethodsFlags.NB_AND),
    NB_BOOL(PyNumberMethods__nb_bool, T___BOOL__, InquiryWrapper::new, MethodsFlags.NB_BOOL),
    NB_DIVMOD(PyNumberMethods__nb_divmod, T___DIVMOD__, BinaryFuncWrapper::new, MethodsFlags.NB_DIVMOD),
    NB_FLOAT(PyNumberMethods__nb_float, T___FLOAT__, UnaryFuncWrapper::new, MethodsFlags.NB_FLOAT),
    NB_FLOOR_DIVIDE(PyNumberMethods__nb_floor_divide, T___FLOORDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_FLOOR_DIVIDE),
    NB_INDEX(PyNumberMethods__nb_index, T___INDEX__, UnaryFuncWrapper::new, MethodsFlags.NB_INDEX),
    NB_INPLACE_ADD(PyNumberMethods__nb_inplace_add, T___IADD__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_ADD),
    NB_INPLACE_AND(PyNumberMethods__nb_inplace_and, T___IAND__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_AND),
    NB_INPLACE_FLOOR_DIVIDE(PyNumberMethods__nb_inplace_floor_divide, T___IFLOORDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_FLOOR_DIVIDE),
    NB_INPLACE_LSHIFT(PyNumberMethods__nb_inplace_lshift, T___ILSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_LSHIFT),
    NB_INPLACE_MULTIPLY(PyNumberMethods__nb_inplace_multiply, T___IMUL__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_MULTIPLY),
    NB_INPLACE_OR(PyNumberMethods__nb_inplace_or, T___IOR__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_OR),
    NB_INPLACE_POWER(PyNumberMethods__nb_inplace_power, T___IPOW__, TernaryFunctionWrapper::new, MethodsFlags.NB_INPLACE_POWER),
    NB_INPLACE_REMAINDER(PyNumberMethods__nb_inplace_remainder, T___IMOD__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_REMAINDER),
    NB_INPLACE_RSHIFT(PyNumberMethods__nb_inplace_rshift, T___IRSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_RSHIFT),
    NB_INPLACE_SUBTRACT(PyNumberMethods__nb_inplace_subtract, T___ISUB__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_SUBTRACT),
    NB_INPLACE_TRUE_DIVIDE(PyNumberMethods__nb_inplace_true_divide, T___ITRUEDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_TRUE_DIVIDE),
    NB_INPLACE_XOR(PyNumberMethods__nb_inplace_xor, T___IXOR__, BinaryFuncWrapper::new, MethodsFlags.NB_INPLACE_XOR),
    NB_INT(PyNumberMethods__nb_int, T___INT__, UnaryFuncWrapper::new, MethodsFlags.NB_INT),
    NB_INVERT(PyNumberMethods__nb_invert, T___INVERT__, UnaryFuncWrapper::new, MethodsFlags.NB_INVERT),
    NB_LSHIFT(PyNumberMethods__nb_lshift, T___LSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_LSHIFT),
    NB_MULTIPLY(PyNumberMethods__nb_multiply, T___MUL__, BinaryFuncWrapper::new, MethodsFlags.NB_MULTIPLY),
    NB_NEGATIVE(PyNumberMethods__nb_negative, T___NEG__, UnaryFuncWrapper::new, MethodsFlags.NB_NEGATIVE),
    NB_OR(PyNumberMethods__nb_or, T___OR__, BinaryFuncWrapper::new, MethodsFlags.NB_OR),
    NB_POSITIVE(PyNumberMethods__nb_positive, T___POS__, UnaryFuncWrapper::new, MethodsFlags.NB_POSITIVE),
    NB_POWER(PyNumberMethods__nb_power, T___POW__, TernaryFunctionWrapper::new, MethodsFlags.NB_POWER),
    NB_REMAINDER(PyNumberMethods__nb_remainder, T___MOD__, BinaryFuncWrapper::new, MethodsFlags.NB_REMAINDER),
    NB_RSHIFT(PyNumberMethods__nb_rshift, T___RSHIFT__, BinaryFuncWrapper::new, MethodsFlags.NB_RSHIFT),
    NB_SUBTRACT(PyNumberMethods__nb_subtract, T___SUB__, BinaryFuncWrapper::new, MethodsFlags.NB_SUBTRACT),
    NB_TRUE_DIVIDE(PyNumberMethods__nb_true_divide, T___TRUEDIV__, BinaryFuncWrapper::new, MethodsFlags.NB_TRUE_DIVIDE),
    NB_XOR(PyNumberMethods__nb_xor, T___XOR__, BinaryFuncWrapper::new, MethodsFlags.NB_XOR);

    public final TruffleString methodName;
    public final Function<Object, PyProcsWrapper> wrapperFactory;
    public final long methodFlag;

    @CompilationFinal public CFields typeField;
    @CompilationFinal public CFields methodsField;

    /**
     * Different slot that is C-compatible and maps to the same Python method.
     */
    @CompilationFinal public SlotMethodDef overlappingSlot;

    SlotMethodDef(CFields typeField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory) {
        this(typeField, null, methodName, wrapperFactory, 0);
    }

    SlotMethodDef(CFields typeField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory, long methodFlag) {
        this(typeField, null, methodName, wrapperFactory, methodFlag);
    }

    SlotMethodDef(CFields typeField, CFields methodsField, TruffleString methodName, Function<Object, PyProcsWrapper> wrapperFactory, long methodFlag) {
        this.typeField = typeField;
        this.methodsField = methodsField;
        this.methodName = methodName;
        this.wrapperFactory = wrapperFactory;
        this.methodFlag = methodFlag;
    }

    static void overlap(SlotMethodDef a, SlotMethodDef b) {
        a.overlappingSlot = b;
        b.overlappingSlot = a;
    }

    static {
        overlap(SQ_LENGTH, MP_LENGTH);

        // SQ_(ASS_)ITEM and MP_(ASS_)SUBSCRIPT do *not* overlap for
        // the purposes of initialising native slots, since the sq
        // slots use ssizeargfunc/ssizeobjargproc and the mp slots
        // use binaryfunc/objobjargproc
        //
        // Similarly for NB_ADD/NB_MUL (wrap_binaryfunc_l) and
        // SQ_CONCAT/SQ_REPEAT (wrap_binaryfunc)

        initGroup(
                        PyTypeObject__tp_as_sequence,
                        SQ_LENGTH,
                        SQ_ITEM,
                        SQ_REPEAT,
                        SQ_CONCAT);
        initGroup(
                        PyTypeObject__tp_as_mapping,
                        MP_LENGTH,
                        MP_SUBSCRIPT,
                        MP_ASS_SUBSCRIPT);
        initGroup(
                        PyTypeObject__tp_as_number,
                        NB_ABSOLUTE,
                        NB_ADD,
                        NB_AND,
                        NB_BOOL,
                        NB_DIVMOD,
                        NB_FLOAT,
                        NB_FLOOR_DIVIDE,
                        NB_INDEX,
                        NB_INPLACE_ADD,
                        NB_INPLACE_AND,
                        NB_INPLACE_FLOOR_DIVIDE,
                        NB_INPLACE_LSHIFT,
                        NB_INPLACE_MULTIPLY,
                        NB_INPLACE_OR,
                        NB_INPLACE_POWER,
                        NB_INPLACE_REMAINDER,
                        NB_INPLACE_RSHIFT,
                        NB_INPLACE_SUBTRACT,
                        NB_INPLACE_TRUE_DIVIDE,
                        NB_INPLACE_XOR,
                        NB_INT,
                        NB_INVERT,
                        NB_LSHIFT,
                        NB_MULTIPLY,
                        NB_NEGATIVE,
                        NB_OR,
                        NB_POSITIVE,
                        NB_POWER,
                        NB_REMAINDER,
                        NB_RSHIFT,
                        NB_SUBTRACT,
                        NB_TRUE_DIVIDE,
                        NB_XOR);
    }

    private static void initGroup(CFields typeField, SlotMethodDef... slots) {
        for (SlotMethodDef slot : slots) {
            assert slot.methodsField == null && slot.typeField != null;
            slot.methodsField = slot.typeField;
            slot.typeField = typeField;
        }
    }
}
