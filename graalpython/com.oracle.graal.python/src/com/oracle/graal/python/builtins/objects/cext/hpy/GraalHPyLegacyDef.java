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

import static com.oracle.graal.python.nodes.SpecialMethodNames.RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DEL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.Arrays;

import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;

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
        Py_tp_alloc(47, __ALLOC__, PExternalFunctionWrapper.ALLOC),
        Py_tp_base(48),
        Py_tp_bases(49),
        Py_tp_call(50, __CALL__, PExternalFunctionWrapper.KEYWORDS),
        Py_tp_clear(51, "__clear__", PExternalFunctionWrapper.INQUIRY),
        Py_tp_dealloc(52, "__dealloc__"),
        Py_tp_del(53, __DEL__),
        Py_tp_descr_get(54, __GET__),
        Py_tp_descr_set(55, __SET__),
        Py_tp_doc(56),
        Py_tp_getattr(57, __GETATTR__, PExternalFunctionWrapper.GETATTR),
        Py_tp_getattro(58, __GETATTR__),
        Py_tp_hash(59, __HASH__),
        Py_tp_init(60, __INIT__, PExternalFunctionWrapper.KEYWORDS),
        Py_tp_is_gc(61),
        Py_tp_iter(62, __ITER__),
        Py_tp_iternext(63, __NEXT__, PExternalFunctionWrapper.ITERNEXT),
        Py_tp_methods(64),
        Py_tp_new(65, __NEW__, PExternalFunctionWrapper.KEYWORDS),
        Py_tp_repr(66, __REPR__),
        Py_tp_richcompare(67, RICHCMP, PExternalFunctionWrapper.RICHCMP),
        Py_tp_setattr(68, __SETATTR__, PExternalFunctionWrapper.SETATTR),
        Py_tp_setattro(69, __SETATTR__),
        Py_tp_str(70, __STR__),
        Py_tp_traverse(71),
        Py_tp_members(72),
        Py_tp_getset(73),
        Py_tp_free(74, "__free__"),

        // PyMappingMethods
        Py_mp_ass_subscript(3, __SETITEM__),
        Py_mp_length(4, __LEN__),
        Py_mp_subscript(5, __GETITEM__),

        // PyNumberMethods
        Py_nb_absolute(6, __ABS__),
        Py_nb_add(7, __ADD__),
        Py_nb_and(8, __AND__),
        Py_nb_bool(9, __BOOL__, PExternalFunctionWrapper.INQUIRY),
        Py_nb_divmod(10, __DIVMOD__),
        Py_nb_float(11, __FLOAT__),
        Py_nb_floor_divide(12, __FLOORDIV__),
        Py_nb_index(13, __INDEX__),
        Py_nb_inplace_add(14, __IADD__),
        Py_nb_inplace_and(15, __IAND__),
        Py_nb_inplace_floor_divide(16, __IFLOORDIV__),
        Py_nb_inplace_lshift(17, __ILSHIFT__),
        Py_nb_inplace_multiply(18, __IMUL__),
        Py_nb_inplace_or(19, __IOR__),
        Py_nb_inplace_power(20, __IPOW__),
        Py_nb_inplace_remainder(21, __IMOD__),
        Py_nb_inplace_rshift(22, __IRSHIFT__),
        Py_nb_inplace_subtract(23, __ISUB__),
        Py_nb_inplace_true_divide(24, __ITRUEDIV__),
        Py_nb_inplace_xor(25, __IXOR__),
        Py_nb_int(26, __INT__),
        Py_nb_invert(27, __INVERT__),
        Py_nb_lshift(28, __LSHIFT__),
        Py_nb_multiply(29, __MUL__),
        Py_nb_negative(30, __NEG__),
        Py_nb_or(31, __OR__),
        Py_nb_positive(32, __POS__),
        Py_nb_power(33, __POW__, PExternalFunctionWrapper.POW),
        Py_nb_remainder(34, __MOD__),
        Py_nb_rshift(35, __RSHIFT__),
        Py_nb_subtract(36, __SUB__),
        Py_nb_true_divide(37, __TRUEDIV__),
        Py_nb_xor(38, __XOR__),
        Py_nb_matrix_multiply(75, __MATMUL__),
        Py_nb_inplace_matrix_multiply(76, __IMATMUL__),

        // PySequenceMethods
        Py_sq_ass_item(39, __SETITEM__, PExternalFunctionWrapper.SETITEM),
        Py_sq_concat(40, __ADD__),
        Py_sq_contains(41, __CONTAINS__),
        Py_sq_inplace_concat(42, __IADD__),
        Py_sq_inplace_repeat(43, __IMUL__, PExternalFunctionWrapper.ALLOC),
        Py_sq_item(44, __GETITEM__, PExternalFunctionWrapper.GETITEM),
        Py_sq_length(45, __LEN__),
        Py_sq_repeat(46, __MUL__, PExternalFunctionWrapper.ALLOC),

        // PyAsyncMethods
        Py_am_await(77),
        Py_am_aiter(78),
        Py_am_anext(79);

        /** The corresponding C enum value. */
        private final int value;

        /**
         * The corresponding attribute key (mostly a {@link String} which is the name of a magic
         * method, or a {@link com.oracle.truffle.api.object.HiddenKey} if it's not exposed to the
         * user, or {@code null} if unsupported).
         */
        private final String attributeKey;

        /** The signature of the slot function. */
        private final PExternalFunctionWrapper signature;

        HPyLegacySlot(int value) {
            this(value, null, null);
        }

        HPyLegacySlot(int value, String attributeKey) {
            this.value = value;
            this.attributeKey = attributeKey;
            this.signature = PExternalFunctionWrapper.DIRECT;
        }

        HPyLegacySlot(int value, String attributeKey, PExternalFunctionWrapper signature) {
            this.value = value;
            this.attributeKey = attributeKey;
            this.signature = signature;
        }

        int getValue() {
            return value;
        }

        String getAttributeKey() {
            return attributeKey;
        }

        PExternalFunctionWrapper getSignature() {
            return signature;
        }

        @CompilationFinal(dimensions = 1) private static final HPyLegacySlot[] VALUES = Arrays.copyOf(values(), values().length);

        @ExplodeLoop
        static HPyLegacySlot fromValue(int value) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].value == value) {
                    return VALUES[i];
                }
            }
            return null;
        }
    }
}
