/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CLEAR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DEALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DEL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FREE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUFFLE_RICHCOMPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Definitions for legacy slots.
 */
public abstract class GraalHPyLegacyDef {
    public static final int MEMBER_FLAG_READONLY = 1;

    /**
     * Values for field {@code slot} of structure {@code PyType_Slot}.
     */
    enum HPyLegacySlot {
        // generic type slots
        Py_tp_alloc(47, T___ALLOC__, PExternalFunctionWrapper.ALLOC),
        Py_tp_base(48),
        Py_tp_bases(49),
        Py_tp_call(50, T___CALL__, PExternalFunctionWrapper.KEYWORDS),
        Py_tp_clear(51, T___CLEAR__, PExternalFunctionWrapper.INQUIRY),
        Py_tp_dealloc(52, T___DEALLOC__),
        Py_tp_del(53, T___DEL__),
        Py_tp_descr_get(54, T___GET__),
        Py_tp_descr_set(55, T___SET__),
        Py_tp_doc(56),
        Py_tp_getattr(57, T___GETATTR__, PExternalFunctionWrapper.GETATTR),
        Py_tp_getattro(58, T___GETATTR__),
        Py_tp_hash(59, T___HASH__, PExternalFunctionWrapper.HASHFUNC),
        Py_tp_init(60, T___INIT__, PExternalFunctionWrapper.INITPROC),
        Py_tp_is_gc(61),
        Py_tp_iter(62, T___ITER__),
        Py_tp_iternext(63, T___NEXT__, PExternalFunctionWrapper.ITERNEXT),
        Py_tp_methods(64),
        Py_tp_new(65, T___NEW__, PExternalFunctionWrapper.KEYWORDS),
        Py_tp_repr(66, T___REPR__, PExternalFunctionWrapper.TP_REPR),
        Py_tp_richcompare(67, T___TRUFFLE_RICHCOMPARE__, PExternalFunctionWrapper.RICHCMP),
        Py_tp_setattr(68, T___SETATTR__, PExternalFunctionWrapper.SETATTR),
        Py_tp_setattro(69, T___SETATTR__),
        Py_tp_str(70, T___STR__, PExternalFunctionWrapper.TP_STR),
        Py_tp_traverse(71),
        Py_tp_members(72),
        Py_tp_getset(73),
        Py_tp_free(74, T___FREE__),

        // PyMappingMethods
        Py_mp_ass_subscript(3, T___SETITEM__, PExternalFunctionWrapper.OBJOBJARGPROC),
        Py_mp_length(4, T___LEN__, PExternalFunctionWrapper.LENFUNC),
        Py_mp_subscript(5, T___GETITEM__),

        // PyNumberMethods
        Py_nb_absolute(6, T___ABS__),
        Py_nb_add(7, T___ADD__),
        Py_nb_and(8, T___AND__),
        Py_nb_bool(9, T___BOOL__, PExternalFunctionWrapper.INQUIRY),
        Py_nb_divmod(10, T___DIVMOD__),
        Py_nb_float(11, T___FLOAT__),
        Py_nb_floor_divide(12, T___FLOORDIV__),
        Py_nb_index(13, T___INDEX__),
        Py_nb_inplace_add(14, T___IADD__),
        Py_nb_inplace_and(15, T___IAND__),
        Py_nb_inplace_floor_divide(16, T___IFLOORDIV__),
        Py_nb_inplace_lshift(17, T___ILSHIFT__),
        Py_nb_inplace_multiply(18, T___IMUL__),
        Py_nb_inplace_or(19, T___IOR__),
        Py_nb_inplace_power(20, T___IPOW__),
        Py_nb_inplace_remainder(21, T___IMOD__),
        Py_nb_inplace_rshift(22, T___IRSHIFT__),
        Py_nb_inplace_subtract(23, T___ISUB__),
        Py_nb_inplace_true_divide(24, T___ITRUEDIV__),
        Py_nb_inplace_xor(25, T___IXOR__),
        Py_nb_int(26, T___INT__),
        Py_nb_invert(27, T___INVERT__),
        Py_nb_lshift(28, T___LSHIFT__),
        Py_nb_multiply(29, T___MUL__),
        Py_nb_negative(30, T___NEG__),
        Py_nb_or(31, T___OR__),
        Py_nb_positive(32, T___POS__),
        Py_nb_power(33, T___POW__, PExternalFunctionWrapper.TERNARYFUNC),
        Py_nb_remainder(34, T___MOD__),
        Py_nb_rshift(35, T___RSHIFT__),
        Py_nb_subtract(36, T___SUB__),
        Py_nb_true_divide(37, T___TRUEDIV__),
        Py_nb_xor(38, T___XOR__),
        Py_nb_matrix_multiply(75, T___MATMUL__),
        Py_nb_inplace_matrix_multiply(76, T___IMATMUL__),

        // PySequenceMethods
        Py_sq_ass_item(39, T___SETITEM__, PExternalFunctionWrapper.SETITEM),
        Py_sq_concat(40, T___ADD__),
        Py_sq_contains(41, T___CONTAINS__, PExternalFunctionWrapper.OBJOBJPROC),
        Py_sq_inplace_concat(42, T___IADD__),
        Py_sq_inplace_repeat(43, T___IMUL__, PExternalFunctionWrapper.ALLOC),
        Py_sq_item(44, T___GETITEM__, PExternalFunctionWrapper.GETITEM),
        Py_sq_length(45, T___LEN__, PExternalFunctionWrapper.LENFUNC),
        Py_sq_repeat(46, T___MUL__, PExternalFunctionWrapper.ALLOC),

        // PyAsyncMethods
        Py_am_await(77),
        Py_am_aiter(78),
        Py_am_anext(79);

        /** The corresponding C enum value. */
        private final int value;

        /**
         * The corresponding attribute key (mostly a {@link String} which is the name of a magic
         * method, or a {@link com.oracle.graal.python.nodes.HiddenAttr} if it's not exposed to the
         * user, or {@code null} if unsupported).
         */
        private final TruffleString attributeKey;

        /** The signature of the slot function. */
        private final PExternalFunctionWrapper signature;

        HPyLegacySlot(int value) {
            this(value, null, null);
        }

        HPyLegacySlot(int value, TruffleString attributeKey) {
            this.value = value;
            this.attributeKey = attributeKey;
            this.signature = PExternalFunctionWrapper.DIRECT;
        }

        HPyLegacySlot(int value, TruffleString attributeKey, PExternalFunctionWrapper signature) {
            this.value = value;
            this.attributeKey = attributeKey;
            this.signature = signature;
        }

        int getValue() {
            return value;
        }

        TruffleString getAttributeKey() {
            return attributeKey;
        }

        PExternalFunctionWrapper getSignature() {
            return signature;
        }

        @CompilationFinal(dimensions = 1) private static final HPyLegacySlot[] VALUES = values();
        @CompilationFinal(dimensions = 1) private static final HPyLegacySlot[] BY_VALUE = new HPyLegacySlot[100];

        static {
            for (var entry : VALUES) {
                assert BY_VALUE[entry.value] == null;
                BY_VALUE[entry.value] = entry;
            }
        }

        static HPyLegacySlot fromValue(int value) {
            return value >= 0 && value < BY_VALUE.length ? BY_VALUE[value] : null;
        }
    }
}
