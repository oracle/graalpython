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

import static com.oracle.graal.python.builtins.objects.cext.NativeMemberType.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberType.POINTER;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberType.PRIMITIVE;

import com.oracle.truffle.api.CompilerAsserts;

public enum NativeMember {
    // PyObject_VAR_HEAD
    OB_BASE("ob_base"),

    // PyObject
    OB_REFCNT("ob_refcnt", PRIMITIVE),
    OB_TYPE("ob_type", OBJECT),

    // PyVarObject
    OB_SIZE("ob_size", PRIMITIVE),

    // PyBytesObject
    OB_SVAL("ob_sval"),

    // PyByteArrayObject
    OB_START("ob_start"),

    // PyTupleObject, PyListObject, arrayobject
    OB_ITEM("ob_item"),

    // PyTypeObject
    TP_FLAGS("tp_flags", PRIMITIVE),
    TP_NAME("tp_name"),
    TP_BASE("tp_base", OBJECT),
    TP_BASES("tp_bases", OBJECT),
    TP_MRO("tp_mro", OBJECT),
    TP_BASICSIZE("tp_basicsize", PRIMITIVE),
    TP_ITEMSIZE("tp_itemsize", PRIMITIVE),
    TP_DICTOFFSET("tp_dictoffset", PRIMITIVE),
    TP_WEAKLISTOFFSET("tp_weaklistoffset", PRIMITIVE),
    TP_DOC("tp_doc"),
    TP_ALLOC("tp_alloc"),
    TP_DEALLOC("tp_dealloc"),
    TP_DEL("tp_del"),
    TP_FREE("tp_free"),
    TP_AS_NUMBER("tp_as_number"),
    TP_HASH("tp_hash"),
    TP_RICHCOMPARE("tp_richcompare"),
    TP_SUBCLASSES("tp_subclasses", OBJECT),
    TP_AS_BUFFER("tp_as_buffer"),
    TP_AS_SEQUENCE("tp_as_sequence"),
    TP_AS_MAPPING("tp_as_mapping"),
    TP_GETATTR("tp_getattr"),
    TP_SETATTR("tp_setattr"),
    TP_GETATTRO("tp_getattro"),
    TP_SETATTRO("tp_setattro"),
    TP_ITERNEXT("tp_iternext"),
    TP_NEW("tp_new"),
    TP_DICT("tp_dict", OBJECT),
    TP_STR("tp_str"),
    TP_REPR("tp_repr"),
    TP_TRAVERSE("tp_traverse"),
    TP_CLEAR("tp_clear"),
    _BASE("_base"),
    TP_VECTORCALL_OFFSET("tp_vectorcall_offset", PRIMITIVE),

    // PySequenceMethods
    SQ_ITEM("sq_item"),
    SQ_REPEAT("sq_repeat"),

    // PyDictObject
    MA_USED("ma_used", PRIMITIVE),

    // PyASCIIObject
    UNICODE_LENGTH("length", PRIMITIVE),
    UNICODE_HASH("hash", PRIMITIVE),
    UNICODE_STATE("state", PRIMITIVE),
    UNICODE_STATE_INTERNED("interned", PRIMITIVE),
    UNICODE_STATE_KIND("kind", PRIMITIVE),
    UNICODE_STATE_COMPACT("compact", PRIMITIVE),
    UNICODE_STATE_ASCII("ascii", PRIMITIVE),
    UNICODE_STATE_READY("ready", PRIMITIVE),
    UNICODE_WSTR("wstr"),

    // PyCompactUnicodeObject
    UNICODE_WSTR_LENGTH("wstr_length", PRIMITIVE),

    // PyUnicodeObject
    UNICODE_DATA("data"),
    UNICODE_DATA_ANY("any"),
    UNICODE_DATA_LATIN1("latin1"),
    UNICODE_DATA_UCS2("ucs2"),
    UNICODE_DATA_UCS4("ucs4"),

    // PyModuleObject
    MD_STATE("md_state"),
    MD_DEF("md_def"),
    MD_DICT("md_dict", OBJECT),

    BUF_DELEGATE("buf_delegate"),
    BUF_READONLY("readonly", PRIMITIVE),

    // PyNumberMethods
    NB_ABSOLUTE("nb_absolute"),
    NB_ADD("nb_add"),
    NB_AND("nb_and"),
    NB_BOOL("nb_bool"),
    NB_DIVMOD("nb_divmod"),
    NB_FLOAT("nb_float"),
    NB_FLOOR_DIVIDE("nb_floor_divide"),
    NB_INDEX("nb_index"),
    NB_INPLACE_ADD("nb_inplace_add"),
    NB_INPLACE_AND("nb_inplace_and"),
    NB_INPLACE_FLOOR_DIVIDE("nb_inplace_floor_divide"),
    NB_INPLACE_LSHIFT("nb_inplace_lshift"),
    NB_INPLACE_MULTIPLY("nb_inplace_multiply"),
    NB_INPLACE_OR("nb_inplace_or"),
    NB_INPLACE_POWER("nb_inplace_power"),
    NB_INPLACE_REMAINDER("nb_inplace_remainder"),
    NB_INPLACE_RSHIFT("nb_inplace_rshift"),
    NB_INPLACE_SUBTRACT("nb_inplace_subtract"),
    NB_INPLACE_TRUE_DIVIDE("nb_inplace_true_divide"),
    NB_INPLACE_XOR("nb_inplace_xor"),
    NB_INT("nb_int"),
    NB_INVERT("nb_invert"),
    NB_LSHIFT("nb_lshift"),
    NB_MULTIPLY("nb_multiply"),
    NB_NEGATIVE("nb_negative"),
    NB_OR("nb_or"),
    NB_POSITIVE("nb_positive"),
    NB_POWER("nb_power"),
    NB_REMAINDER("nb_remainder"),
    NB_RSHIFT("nb_rshift"),
    NB_SUBTRACT("nb_subtract"),
    NB_TRUE_DIVIDE("nb_true_divide"),
    NB_XOR("nb_xor"),

    // PyMappingMethods
    MP_LENGTH("mp_length", PRIMITIVE),
    MP_SUBSCRIPT("mp_subscript"),
    MP_ASS_SUBSCRIPT("mp_ass_subscript"),

    // PyFloatObject
    OB_FVAL("ob_fval", PRIMITIVE),

    // PyLongObject
    OB_DIGIT("ob_digit"),

    // PySliceObject
    START("start", OBJECT),
    STOP("stop", OBJECT),
    STEP("step", OBJECT),

    // PyMethodObject
    IM_FUNC("im_func", OBJECT),
    IM_SELF("im_self", OBJECT),

    // PyMemoryViewObject
    MEMORYVIEW_FLAGS("flags", PRIMITIVE),

    // PyDescr_COMMON
    D_COMMON("d_common"),

    // PyMemberDescrObject,
    D_MEMBER("d_member"),

    // PyMethodDescrObject
    D_GETSET("d_getset"),

    // PyMethodDescrObject
    D_METHOD("d_method"),

    // PyWrapperDescrObject
    D_BASE("d_base"),

    // PyDescrObject
    D_QUALNAME("d_qualname", OBJECT),
    D_NAME("d_name", OBJECT),
    D_TYPE("d_type", OBJECT),

    // PyCFunctionObject
    M_ML("m_ml"),

    // PyDateTime_Date
    DATETIME_DATA("data"),

    // PySetObject
    SET_USED("used", PRIMITIVE),
    MMAP_DATA("data"),

    // PyFrameObject
    F_BACK("f_back", OBJECT),
    F_LINENO("f_lineno", PRIMITIVE),
    F_CODE("f_code", OBJECT);

    private final String memberName;
    private final NativeMemberType type;

    NativeMember(String name) {
        this.memberName = name;
        this.type = POINTER;
    }

    NativeMember(String name, NativeMemberType type) {
        this.memberName = name;
        this.type = type;
    }

    public String getMemberName() {
        return memberName;
    }

    public NativeMemberType getType() {
        return type;
    }

    public static boolean isValid(String name) {
        CompilerAsserts.neverPartOfCompilation();
        for (NativeMember nativeMember : NativeMember.values()) {
            if (nativeMember.memberName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
