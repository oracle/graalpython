/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import java.lang.reflect.Field;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerAsserts;

public final class NativeMemberNames {
    public static final String OB_BASE = "ob_base";
    public static final String OB_REFCNT = "ob_refcnt";
    public static final String OB_TYPE = "ob_type";
    public static final String OB_SIZE = "ob_size";
    public static final String OB_SVAL = "ob_sval";
    public static final String OB_START = "ob_start";
    public static final String TP_FLAGS = "tp_flags";
    public static final String TP_NAME = "tp_name";
    public static final String TP_BASE = "tp_base";
    public static final String TP_BASES = "tp_bases";
    public static final String TP_MRO = "tp_mro";
    public static final String TP_BASICSIZE = "tp_basicsize";
    public static final String TP_ITEMSIZE = "tp_itemsize";
    public static final String TP_DICTOFFSET = "tp_dictoffset";
    public static final String TP_WEAKLISTOFFSET = "tp_weaklistoffset";
    public static final String TP_DOC = "tp_doc";
    public static final String TP_ALLOC = "tp_alloc";
    public static final String TP_AS_NUMBER = "tp_as_number";
    public static final String TP_HASH = "tp_hash";
    public static final String TP_RICHCOMPARE = "tp_richcompare";
    public static final String TP_SUBCLASSES = "tp_subclasses";
    public static final String TP_AS_BUFFER = "tp_as_buffer";
    public static final String TP_AS_SEQUENCE = "tp_as_sequence";
    public static final String TP_AS_MAPPING = "tp_as_mapping";
    public static final String TP_GETATTR = "tp_getattr";
    public static final String TP_SETATTR = "tp_setattr";
    public static final String TP_GETATTRO = "tp_getattro";
    public static final String TP_SETATTRO = "tp_setattro";
    public static final String TP_ITERNEXT = "tp_iternext";
    public static final String TP_NEW = "tp_new";
    public static final String TP_DICT = "tp_dict";
    public static final String TP_STR = "tp_str";
    public static final String TP_REPR = "tp_repr";
    public static final String TP_TRAVERSE = "tp_traverse";
    public static final String TP_CLEAR = "tp_clear";
    public static final String _BASE = "_base";
    public static final String OB_ITEM = "ob_item";
    public static final String SQ_ITEM = "sq_item";
    public static final String MA_USED = "ma_used";
    public static final String UNICODE_LENGTH = "length";
    public static final String UNICODE_DATA = "data";
    public static final String UNICODE_DATA_ANY = "any";
    public static final String UNICODE_DATA_LATIN1 = "latin1";
    public static final String UNICODE_DATA_UCS2 = "ucs2";
    public static final String UNICODE_DATA_UCS4 = "ucs4";
    public static final String UNICODE_WSTR = "wstr";
    public static final String UNICODE_WSTR_LENGTH = "wstr_length";
    public static final String UNICODE_STATE = "state";
    public static final String UNICODE_STATE_INTERNED = "interned";
    public static final String UNICODE_STATE_KIND = "kind";
    public static final String UNICODE_STATE_COMPACT = "compact";
    public static final String UNICODE_STATE_ASCII = "ascii";
    public static final String UNICODE_STATE_READY = "ready";
    public static final String UNICODE_HASH = "hash";
    public static final String MD_STATE = "md_state";
    public static final String MD_DEF = "md_def";
    public static final String MD_DICT = "md_dict";
    public static final String BUF_DELEGATE = "buf_delegate";
    public static final String BUF_READONLY = "readonly";
    public static final String NB_ABSOLUTE = "nb_absolute";
    public static final String NB_ADD = "nb_add";
    public static final String NB_AND = "nb_and";
    public static final String NB_BOOL = "nb_bool";
    public static final String NB_DIVMOD = "nb_divmod";
    public static final String NB_FLOAT = "nb_float";
    public static final String NB_FLOOR_DIVIDE = "nb_floor_divide";
    public static final String NB_INDEX = "nb_index";
    public static final String NB_INPLACE_ADD = "nb_inplace_add";
    public static final String NB_INPLACE_AND = "nb_inplace_and";
    public static final String NB_INPLACE_FLOOR_DIVIDE = "nb_inplace_floor_divide";
    public static final String NB_INPLACE_LSHIFT = "nb_inplace_lshift";
    public static final String NB_INPLACE_MULTIPLY = "nb_inplace_multiply";
    public static final String NB_INPLACE_OR = "nb_inplace_or";
    public static final String NB_INPLACE_POWER = "nb_inplace_power";
    public static final String NB_INPLACE_REMAINDER = "nb_inplace_remainder";
    public static final String NB_INPLACE_RSHIFT = "nb_inplace_rshift";
    public static final String NB_INPLACE_SUBTRACT = "nb_inplace_subtract";
    public static final String NB_INPLACE_TRUE_DIVIDE = "nb_inplace_true_divide";
    public static final String NB_INPLACE_XOR = "nb_inplace_xor";
    public static final String NB_INT = "nb_int";
    public static final String NB_INVERT = "nb_invert";
    public static final String NB_LSHIFT = "nb_lshift";
    public static final String NB_MULTIPLY = "nb_multiply";
    public static final String NB_NEGATIVE = "nb_negative";
    public static final String NB_OR = "nb_or";
    public static final String NB_POSITIVE = "nb_positive";
    public static final String NB_POWER = "nb_power";
    public static final String NB_REMAINDER = "nb_remainder";
    public static final String NB_RSHIFT = "nb_rshift";
    public static final String NB_SUBTRACT = "nb_subtract";
    public static final String NB_TRUE_DIVIDE = "nb_true_divide";
    public static final String NB_XOR = "nb_xor";
    public static final String MP_LENGTH = "mp_length";
    public static final String MP_SUBSCRIPT = "mp_subscript";
    public static final String MP_ASS_SUBSCRIPT = "mp_ass_subscript";
    public static final String OB_FVAL = "ob_fval";
    public static final String START = "start";
    public static final String STOP = "stop";
    public static final String STEP = "step";
    public static final String IM_FUNC = "im_func";
    public static final String IM_SELF = "im_self";
    public static final String SQ_REPEAT = "sq_repeat";
    public static final String MEMORYVIEW_FLAGS = "flags";
    public static final String D_COMMON = "d_common";
    public static final String D_MEMBER = "d_member";
    public static final String D_GETSET = "d_getset";
    public static final String D_METHOD = "d_method";
    public static final String D_BASE = "d_base";
    public static final String D_QUALNAME = "d_qualname";
    public static final String D_NAME = "d_name";
    public static final String D_TYPE = "d_type";
    public static final String M_ML = "m_ml";
    public static final String DATETIME_DATA = "data";
    public static final String SET_USED = "used";
    public static final String MMAP_DATA = "data";

    private static final HashSet<String> values = new HashSet<>();

    static {
        Field[] declaredFields = NativeMemberNames.class.getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
            Field s = declaredFields[i];
            if (s.getType() == String.class) {
                try {
                    values.add((String) s.get(NativeMemberNames.class));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                }
            }
        }
    }

    public static boolean isValid(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return values.contains(name);
    }
}
