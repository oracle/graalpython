/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMemberType.CSTRING;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMemberType.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMemberType.POINTER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMemberType.PRIMITIVE;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.strings.TruffleString;

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
    OB_BYTES("ob_bytes"),
    OB_START("ob_start"),
    OB_EXPORTS("ob_exports", PRIMITIVE),

    // PyTupleObject, PyListObject, arrayobject
    OB_ITEM("ob_item"),

    // PyTypeObject
    TP_FLAGS("tp_flags", PRIMITIVE),
    TP_NAME("tp_name", CSTRING),
    TP_BASE("tp_base", OBJECT),
    TP_BASES("tp_bases", OBJECT),
    TP_MRO("tp_mro", OBJECT),
    TP_CACHE("tp_cache", OBJECT),
    TP_WEAKLIST("tp_weaklist", OBJECT),
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
    TP_AS_ASYNC("tp_as_async"),
    TP_GETATTR("tp_getattr"),
    TP_SETATTR("tp_setattr"),
    TP_GETATTRO("tp_getattro"),
    TP_SETATTRO("tp_setattro"),
    TP_DESCR_GET("tp_descr_get"),
    TP_DESCR_SET("tp_descr_set"),
    TP_ITER("tp_iter"),
    TP_ITERNEXT("tp_iternext"),
    TP_NEW("tp_new"),
    TP_INIT("tp_init"),
    TP_FINALIZE("tp_finalize"),
    TP_DICT("tp_dict", OBJECT),
    TP_STR("tp_str"),
    TP_REPR("tp_repr"),
    TP_TRAVERSE("tp_traverse"),
    TP_CLEAR("tp_clear"),
    TP_METHODS("tp_methods"),
    TP_MEMBERS("tp_members"),
    TP_GETSET("tp_getset"),
    TP_IS_GC("tp_is_gc"),
    _BASE("_base"),
    TP_VERSION_TAG("tp_version_tag", PRIMITIVE),
    TP_VECTORCALL_OFFSET("tp_vectorcall_offset", PRIMITIVE),
    TP_CALL("tp_call"),
    TP_VECTORCALL("tp_vectorcall"),

    // PySequenceMethods
    SQ_ITEM("sq_item"),
    SQ_REPEAT("sq_repeat"),
    SQ_CONCAT("sq_concat"),
    SQ_LENGTH("sq_length"),

    // PyDictObject
    MA_USED("ma_used", PRIMITIVE),
    MA_VERSION_TAG("ma_version_tag", PRIMITIVE),

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

    // PyModuleDef_Base
    M_INDEX("m_index", PRIMITIVE),

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

    // PyComplexObject
    COMPLEX_CVAL("cval"),

    // PySliceObject
    START("start", OBJECT),
    STOP("stop", OBJECT),
    STEP("step", OBJECT),

    // PyMethodObject
    IM_FUNC("im_func", OBJECT),
    IM_SELF("im_self", OBJECT),

    // PyMemoryViewObject
    MEMORYVIEW_FLAGS("flags", PRIMITIVE),
    MEMORYVIEW_EXPORTS("exports", PRIMITIVE),
    MEMORYVIEW_VIEW("view"),

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
    M_SELF("m_self"),
    M_MODULE("m_module"),
    // PyCMethodObject
    FUNC("func"),
    MM_CLASS("mm_class"),

    // PyDateTime_Date
    DATETIME_DATA("data"),
    DATETIME_TZINFO("tzinfo"),

    // PySetObject
    SET_USED("used", PRIMITIVE),
    MMAP_DATA("data"),

    // PyFrameObject
    F_BACK("f_back", OBJECT),
    F_LINENO("f_lineno", PRIMITIVE),
    F_CODE("f_code", OBJECT),

    // propertyobject
    PROP_GET("prop_get", OBJECT),
    PROP_SET("prop_set", OBJECT),
    PROP_DEL("prop_del", OBJECT),
    PROP_DOC("prop_doc", OBJECT),
    PROP_GETTERDOC("getter_doc", PRIMITIVE),

    // PyFunctionObject
    FUNC_CODE("func_code", OBJECT),
    FUNC_GLOBALS("func_globals", OBJECT),
    FUNC_DEFAULTS("func_defaults", OBJECT),
    FUNC_KWDEFAULTS("func_kwdefaults", OBJECT),
    FUNC_CLOSURE("func_closure", OBJECT),
    FUNC_DOC("func_doc", OBJECT),
    FUNC_NAME("func_name", OBJECT),
    FUNC_DICT("func_dict", OBJECT),
    FUNC_WEAKREFLIST("func_weakreflist", OBJECT),
    FUNC_MODULE("func_module", OBJECT),
    FUNC_ANNOTATIONS("func_annotations", OBJECT),
    FUNC_QUALNAME("func_qualname", OBJECT),
    FUNC_VECTORCALL("vectorcall"),

    // PyCodeObject
    CO_ARGCOUNT("co_argcount", PRIMITIVE),
    CO_POSONLYARGCOUNT("co_posonlyargcount", PRIMITIVE),
    CO_KWONLYCOUNT("co_kwonlyargcount", PRIMITIVE),
    CO_NLOCALS("co_nlocals", PRIMITIVE),
    CO_STACKSIZE("co_stacksize", PRIMITIVE),
    CO_FLAGS("co_flags", PRIMITIVE),
    CO_FIRSTLINENO("co_firstlineno", PRIMITIVE),
    CO_CODE("co_code", OBJECT),
    CO_CONSTS("co_consts", OBJECT),
    CO_NAMES("co_names", OBJECT),
    CO_VARNAMES("co_varnames", OBJECT),
    CO_FREEVARS("co_freevars", OBJECT),
    CO_CELLVARS("co_cellvars", OBJECT),

    // PyBaseException
    TRACEBACK("traceback", OBJECT),
    CAUSE("cause", OBJECT),
    CONTEXT("context", OBJECT),
    SUPPRESS_CONTEXT("suppress_context", PRIMITIVE),
    ARGS("args", OBJECT),

    // PyStopIterationObject
    VALUE("value", OBJECT);

    private final String jMemberName;
    private final TruffleString tMemberName;
    private final NativeMemberType type;
    private final NativeCAPISymbol getter;
    private final NativeCAPISymbol setter;

    NativeMember(String name) {
        this(name, POINTER);
    }

    NativeMember(String name, NativeMemberType type) {
        this.jMemberName = name;
        this.tMemberName = toTruffleStringUncached(name);
        this.type = type;
        this.getter = NativeCAPISymbol.getByName(StringLiterals.J_GET_ + name);
        this.setter = NativeCAPISymbol.getByName(StringLiterals.J_SET_ + name);
    }

    public TruffleString getMemberNameTruffleString() {
        return tMemberName;
    }

    public String getMemberNameJavaString() {
        // TODO GR-37896: needed because sulong and interop do not yet support TruffleString
        return jMemberName;
    }

    public NativeMemberType getType() {
        return type;
    }

    public NativeCAPISymbol getGetterFunctionName() {
        if (getter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("no getter for native member " + jMemberName);
        }
        return getter;
    }

    public NativeCAPISymbol getSetterFunctionName() {
        if (setter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("no setter for native member " + jMemberName);
        }
        return setter;
    }

    @CompilationFinal(dimensions = 1) private static final NativeMember[] VALUES = values();

    public static NativeMember byName(String name) {
        for (NativeMember nativeMember : VALUES) {
            if (name.equals(nativeMember.jMemberName)) {
                return nativeMember;
            }
        }
        return null;
    }

    @Idempotent
    public static boolean isValid(String name) {
        return byName(name) != null;
    }
}
