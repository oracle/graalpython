/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.HashMap;

import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum GraalHPyNativeSymbol implements NativeCExtSymbol {

    POLYGLOT_FROM_STRING("polyglot_from_string"),

    GRAAL_HPY_ALLOCATE_BUFFER("graal_hpy_allocate_buffer"),
    GRAAL_HPY_BUFFER_TO_NATIVE("graal_hpy_buffer_to_native"),
    GRAAL_HPY_BUFFER_FREE("graal_hpy_buffer_free"),
    GRAAL_HPY_FREE("graal_hpy_free"),
    GRAAL_HPY_GET_M_NAME("graal_hpy_get_m_name"),
    GRAAL_HPY_GET_M_DOC("graal_hpy_get_m_doc"),
    GRAAL_HPY_GET_ML_NAME("graal_hpy_get_ml_name"),
    GRAAL_HPY_GET_ML_DOC("graal_hpy_get_ml_doc"),
    GRAAL_HPY_METH_GET_SIGNATURE("graal_hpy_meth_get_signature"),
    GRAAL_HPY_FROM_HPY_ARRAY("graal_hpy_from_HPy_array"),
    GRAAL_HPY_FROM_STRING("graal_hpy_from_string"),
    GRAAL_HPY_GET_ERRNO("graal_hpy_get_errno"),
    GRAAL_HPY_GET_STRERROR("graal_hpy_get_strerror"),
    GRAAL_HPY_STRLEN("graal_hpy_strlen"),
    GRAAL_HPY_ALLOCATE_OUTVAR("graal_hpy_allocate_outvar"),
    GRAAL_HPY_ARRAY_TO_NATIVE("graal_hpy_array_to_native"),
    GRAAL_HPY_FROM_I8_ARRAY("graal_hpy_from_i8_array"),
    GRAAL_HPY_FROM_WCHAR_ARRAY("graal_hpy_from_wchar_array"),
    GRAAL_HPY_CONTEXT_TO_NATIVE("graal_hpy_context_to_native"),
    GRAAL_HPY_BYTE_ARRAY_TO_NATIVE("graal_hpy_byte_array_to_native"),
    GRAAL_HPY_INT_ARRAY_TO_NATIVE("graal_hpy_int_array_to_native"),
    GRAAL_HPY_LONG_ARRAY_TO_NATIVE("graal_hpy_long_array_to_native"),
    GRAAL_HPY_DOUBLE_ARRAY_TO_NATIVE("graal_hpy_double_array_to_native"),
    GRAAL_HPY_POINTER_ARRAY_TO_NATIVE("graal_hpy_pointer_array_to_native"),
    GRAAL_HPY_FROM_HPY_MODULE_DEF("graal_hpy_from_HPyModuleDef"),
    GRAAL_HPY_MODULE_GET_DEFINES("graal_hpy_module_get_defines"),
    GRAAL_HPY_MODULE_INIT_GLOBALS("graal_hpy_module_init_globals"),
    GRAAL_HPY_DEF_GET_KIND("graal_hpy_def_get_kind"),
    GRAAL_HPY_DEF_GET_SLOT("graal_hpy_def_get_slot"),
    GRAAL_HPY_DEF_GET_METH("graal_hpy_def_get_meth"),
    GRAAL_HPY_DEF_GET_MEMBER("graal_hpy_def_get_member"),
    GRAAL_HPY_DEF_GET_GETSET("graal_hpy_def_get_getset"),
    GRAAL_HPY_MODULE_GET_LEGACY_METHODS("graal_hpy_module_get_legacy_methods"),
    GRAAL_HPY_FROM_HPY_TYPE_SPEC("graal_hpy_from_HPyType_Spec"),
    GRAAL_HPY_TYPE_SPEC_GET_DEFINES("graal_hpy_type_spec_get_defines"),
    GRAAL_HPY_TYPE_SPEC_GET_LEGECY_SLOTS("graal_hpy_type_spec_get_legacy_slots"),
    GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY("graal_hpy_from_HPyType_SpecParam_array"),
    GRAAL_HPY_TYPE_SPEC_PARAM_GET_KIND("graal_hpy_HPyType_SpecParam_get_kind"),
    GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT("graal_hpy_HPyType_SpecParam_get_object"),
    GRAAL_HPY_MEMBER_GET_TYPE("graal_hpy_member_get_type"),
    GRAAL_HPY_SLOT_GET_SLOT("graal_hpy_slot_get_slot"),
    GRAAL_HPY_CALLOC("graal_hpy_calloc"),
    GRAAL_HPY_LEGACY_SLOT_GET_SLOT("graal_hpy_legacy_slot_get_slot"),
    GRAAL_HPY_LEGACY_SLOT_GET_PFUNC("graal_hpy_legacy_slot_get_pfunc"),
    GRAAL_HPY_LEGACY_SLOT_GET_METHODS("graal_hpy_legacy_slot_get_methods"),
    GRAAL_HPY_LEGACY_SLOT_GET_MEMBERS("graal_hpy_legacy_slot_get_members"),
    GRAAL_HPY_LEGACY_SLOT_GET_DESCRS("graal_hpy_legacy_slot_get_descrs"),
    GRAAL_HPY_LEGACY_METHODDEF_GET_ML_NAME("graal_hpy_legacy_methoddef_get_ml_name"),
    GRAAL_HPY_LEGACY_GETSETDEF_GET_NAME("graal_hpy_legacy_getsetdef_get_name"),
    GRAAL_HPY_SET_DEBUG_CONTEXT("graal_hpy_set_debug_context"),
    GRAAL_HPY_GET_FIELD_I("graal_hpy_get_field_i"),
    GRAAL_HPY_SET_FIELD_I("graal_hpy_set_field_i"),
    GRAAL_HPY_GET_GLOBAL_I("graal_hpy_get_global_i"),
    GRAAL_HPY_SET_GLOBAL_I("graal_hpy_set_global_i"),
    GRAAL_HPY_STRDUP("graal_hpy_strdup"),
    GRAAL_HPY_LONG2PTR("graal_hpy_long2ptr"),

    /* C functions for reading native members by offset */
    GRAAL_HPY_READ_S("graal_hpy_read_s"),
    GRAAL_HPY_READ_I("graal_hpy_read_i"),
    GRAAL_HPY_READ_L("graal_hpy_read_l"),
    GRAAL_HPY_READ_F("graal_hpy_read_f"),
    GRAAL_HPY_READ_D("graal_hpy_read_d"),
    GRAAL_HPY_READ_STRING("graal_hpy_read_string"),
    GRAAL_HPY_READ_STRING_IN_PLACE("graal_hpy_read_string_in_place"),
    GRAAL_HPY_READ_HPY("graal_hpy_read_HPy"),
    GRAAL_HPY_READ_C("graal_hpy_read_c"),
    GRAAL_HPY_READ_UC("graal_hpy_read_uc"),
    GRAAL_HPY_READ_US("graal_hpy_read_us"),
    GRAAL_HPY_READ_UI("graal_hpy_read_ui"),
    GRAAL_HPY_READ_UL("graal_hpy_read_ul"),
    GRAAL_HPY_READ_LL("graal_hpy_read_ll"),
    GRAAL_HPY_READ_ULL("graal_hpy_read_ull"),
    GRAAL_HPY_READ_HPY_SSIZE_T("graal_hpy_read_HPy_ssize_t"),

    /* C functions for writing native members by offset */
    GRAAL_HPY_WRITE_S("graal_hpy_write_s"),
    GRAAL_HPY_WRITE_I("graal_hpy_write_i"),
    GRAAL_HPY_WRITE_L("graal_hpy_write_l"),
    GRAAL_HPY_WRITE_F("graal_hpy_write_f"),
    GRAAL_HPY_WRITE_D("graal_hpy_write_d"),
    GRAAL_HPY_WRITE_STRING("graal_hpy_write_string"),
    GRAAL_HPY_WRITE_STRING_IN_PLACE("graal_hpy_write_string_in_place"),
    GRAAL_HPY_WRITE_HPY("graal_hpy_write_HPy"),
    GRAAL_HPY_WRITE_C("graal_hpy_write_c"),
    GRAAL_HPY_WRITE_UC("graal_hpy_write_uc"),
    GRAAL_HPY_WRITE_US("graal_hpy_write_us"),
    GRAAL_HPY_WRITE_UI("graal_hpy_write_ui"),
    GRAAL_HPY_WRITE_UL("graal_hpy_write_ul"),
    GRAAL_HPY_WRITE_LL("graal_hpy_write_ll"),
    GRAAL_HPY_WRITE_ULL("graal_hpy_write_ull"),
    GRAAL_HPY_WRITE_HPY_SSIZE_T("graal_hpy_write_HPy_ssize_t"),
    GRAAL_HPY_WRITE_PTR("graal_hpy_write_ptr"),

    GRAAL_HPY_BULK_FREE("graal_hpy_bulk_free"),

    // getter for LLVM type IDs
    GRAAL_HPY_GET_HPYMODULE_INIT_TYPEID("graal_hpy_get_HPyModule_init_typeid"),
    GRAAL_HPY_GET_HPYFUNC_NOARGS_TYPEID("graal_hpy_get_HPyFunc_noargs_typeid"),
    GRAAL_HPY_GET_HPYFUNC_O_TYPEID("graal_hpy_get_HPyFunc_o_typeid"),
    GRAAL_HPY_GET_HPYFUNC_VARARGS_TYPEID("graal_hpy_get_HPyFunc_varargs_typeid"),
    GRAAL_HPY_GET_HPYFUNC_KEYWORDS_TYPEID("graal_hpy_get_HPyFunc_keywords_typeid"),
    GRAAL_HPY_GET_HPYFUNC_UNARYFUNC_TYPEID("graal_hpy_get_HPyFunc_unaryfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_BINARYFUNC_TYPEID("graal_hpy_get_HPyFunc_binaryfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_TERNARYFUNC_TYPEID("graal_hpy_get_HPyFunc_ternaryfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_INQUIRY_TYPEID("graal_hpy_get_HPyFunc_inquiry_typeid"),
    GRAAL_HPY_GET_HPYFUNC_LENFUNC_TYPEID("graal_hpy_get_HPyFunc_lenfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SSIZEARGFUNC_TYPEID("graal_hpy_get_HPyFunc_ssizeargfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SSIZESSIZEARGFUNC_TYPEID("graal_hpy_get_HPyFunc_ssizessizeargfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SSIZEOBJARGPROC_TYPEID("graal_hpy_get_HPyFunc_ssizeobjargproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SSIZESSIZEOBJARGPROC_TYPEID("graal_hpy_get_HPyFunc_ssizessizeobjargproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_OBJOBJARGPROC_TYPEID("graal_hpy_get_HPyFunc_objobjargproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_FREEFUNC_TYPEID("graal_hpy_get_HPyFunc_freefunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_GETATTRFUNC_TYPEID("graal_hpy_get_HPyFunc_getattrfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_GETATTROFUNC_TYPEID("graal_hpy_get_HPyFunc_getattrofunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SETATTRFUNC_TYPEID("graal_hpy_get_HPyFunc_setattrfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SETATTROFUNC_TYPEID("graal_hpy_get_HPyFunc_setattrofunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_REPRFUNC_TYPEID("graal_hpy_get_HPyFunc_reprfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_HASHFUNC_TYPEID("graal_hpy_get_HPyFunc_hashfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_RICHCMPFUNC_TYPEID("graal_hpy_get_HPyFunc_richcmpfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_GETITERFUNC_TYPEID("graal_hpy_get_HPyFunc_getiterfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_ITERNEXTFUNC_TYPEID("graal_hpy_get_HPyFunc_iternextfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_DESCRGETFUNC_TYPEID("graal_hpy_get_HPyFunc_descrgetfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_DESCRSETFUNC_TYPEID("graal_hpy_get_HPyFunc_descrsetfunc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_INITPROC_TYPEID("graal_hpy_get_HPyFunc_initproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_GETTER_TYPEID("graal_hpy_get_HPyFunc_getter_typeid"),
    GRAAL_HPY_GET_HPYFUNC_SETTER_TYPEID("graal_hpy_get_HPyFunc_setter_typeid"),
    GRAAL_HPY_GET_HPYFUNC_OBJOBJPROC_TYPEID("graal_hpy_get_HPyFunc_objobjproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_GETBUFFERPROC_TYPEID("graal_hpy_get_HPyFunc_getbufferproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_RELEASEBUFFERPROC_TYPEID("graal_hpy_get_HPyFunc_releasebufferproc_typeid"),
    GRAAL_HPY_GET_HPYFUNC_DESTROYFUNC_TYPEID("graal_hpy_get_HPyFunc_destroyfunc_typeid");

    private final String name;
    private final TruffleString tsName;

    private GraalHPyNativeSymbol(String name) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TruffleString getTsName() {
        return tsName;
    }

    @CompilationFinal(dimensions = 1) private static final GraalHPyNativeSymbol[] VALUES = values();
    private static final HashMap<String, GraalHPyNativeSymbol> MAP = new HashMap<>();

    public static GraalHPyNativeSymbol[] getValues() {
        return VALUES;
    }

    public static GraalHPyNativeSymbol getByName(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return MAP.get(name);
    }

    static {
        for (var symbol : VALUES) {
            assert !MAP.containsKey(symbol.name);
            MAP.put(symbol.name, symbol);
        }
    }

    public String getSignature() {
        return null;
    }
}
