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

public enum GraalHPyNativeSymbol {

    GRAAL_HPY_BUFFER_TO_NATIVE("graal_hpy_buffer_to_native"),
    GRAAL_HPY_BUFFER_FREE("graal_hpy_buffer_free"),
    GRAAL_HPY_FREE("graal_hpy_free"),
    GRAAL_HPY_FROM_HPY_ARRAY("graal_hpy_from_HPy_array"),
    GRAAL_HPY_GET_ERRNO("graal_hpy_get_errno"),
    GRAAL_HPY_GET_STRERROR("graal_hpy_get_strerror"),
    GRAAL_HPY_STRLEN("graal_hpy_strlen"),
    GRAAL_HPY_ARRAY_TO_NATIVE("graal_hpy_array_to_native"),
    GRAAL_HPY_FROM_I8_ARRAY("graal_hpy_from_i8_array"),
    GRAAL_HPY_FROM_WCHAR_ARRAY("graal_hpy_from_wchar_array"),
    GRAAL_HPY_CONTEXT_TO_NATIVE("graal_hpy_context_to_native"),
    GRAAL_HPY_BYTE_ARRAY_TO_NATIVE("graal_hpy_byte_array_to_native"),
    GRAAL_HPY_INT_ARRAY_TO_NATIVE("graal_hpy_int_array_to_native"),
    GRAAL_HPY_LONG_ARRAY_TO_NATIVE("graal_hpy_long_array_to_native"),
    GRAAL_HPY_DOUBLE_ARRAY_TO_NATIVE("graal_hpy_double_array_to_native"),
    GRAAL_HPY_POINTER_ARRAY_TO_NATIVE("graal_hpy_pointer_array_to_native"),
    GRAAL_HPY_CALLOC("graal_hpy_calloc"),
    GRAAL_HPY_GET_FIELD_I("graal_hpy_get_field_i"),
    GRAAL_HPY_SET_FIELD_I("graal_hpy_set_field_i"),
    GRAAL_HPY_GET_GLOBAL_I("graal_hpy_get_global_i"),
    GRAAL_HPY_SET_GLOBAL_I("graal_hpy_set_global_i"),
    GRAAL_HPY_LONG2PTR("graal_hpy_long2ptr"),
    GRAAL_HPY_GET_ELEMENT_PTR("graal_hpy_get_element_ptr"),

    /* C functions for reading native members by offset */
    GRAAL_HPY_READ_BOOL("graal_hpy_read_bool"),
    GRAAL_HPY_READ_I8("graal_hpy_read_i8"),
    GRAAL_HPY_READ_UI8("graal_hpy_read_ui8"),
    GRAAL_HPY_READ_I16("graal_hpy_read_i16"),
    GRAAL_HPY_READ_UI16("graal_hpy_read_ui16"),
    GRAAL_HPY_READ_I32("graal_hpy_read_i32"),
    GRAAL_HPY_READ_UI32("graal_hpy_read_ui32"),
    GRAAL_HPY_READ_I64("graal_hpy_read_i64"),
    GRAAL_HPY_READ_UI64("graal_hpy_read_ui64"),
    GRAAL_HPY_READ_S("graal_hpy_read_s"),
    GRAAL_HPY_READ_I("graal_hpy_read_i"),
    GRAAL_HPY_READ_L("graal_hpy_read_l"),
    GRAAL_HPY_READ_F("graal_hpy_read_f"),
    GRAAL_HPY_READ_D("graal_hpy_read_d"),
    GRAAL_HPY_READ_PTR("graal_hpy_read_ptr"),
    GRAAL_HPY_READ_HPY("graal_hpy_read_HPy"),
    GRAAL_HPY_READ_HPYFIELD("graal_hpy_read_HPyField"),
    GRAAL_HPY_READ_UI("graal_hpy_read_ui"),
    GRAAL_HPY_READ_UL("graal_hpy_read_ul"),
    GRAAL_HPY_READ_HPY_SSIZE_T("graal_hpy_read_HPy_ssize_t"),

    /* C functions for writing native members by offset */
    GRAAL_HPY_WRITE_BOOL("graal_hpy_write_bool"),
    GRAAL_HPY_WRITE_I8("graal_hpy_write_i8"),
    GRAAL_HPY_WRITE_UI8("graal_hpy_write_ui8"),
    GRAAL_HPY_WRITE_I16("graal_hpy_write_i16"),
    GRAAL_HPY_WRITE_UI16("graal_hpy_write_ui16"),
    GRAAL_HPY_WRITE_I32("graal_hpy_write_i32"),
    GRAAL_HPY_WRITE_UI32("graal_hpy_write_ui32"),
    GRAAL_HPY_WRITE_I64("graal_hpy_write_i64"),
    GRAAL_HPY_WRITE_UI64("graal_hpy_write_ui64"),
    GRAAL_HPY_WRITE_I("graal_hpy_write_i"),
    GRAAL_HPY_WRITE_L("graal_hpy_write_l"),
    GRAAL_HPY_WRITE_F("graal_hpy_write_f"),
    GRAAL_HPY_WRITE_D("graal_hpy_write_d"),
    GRAAL_HPY_WRITE_HPY("graal_hpy_write_HPy"),
    GRAAL_HPY_WRITE_HPYFIELD("graal_hpy_write_HPyField"),
    GRAAL_HPY_WRITE_UI("graal_hpy_write_ui"),
    GRAAL_HPY_WRITE_UL("graal_hpy_write_ul"),
    GRAAL_HPY_WRITE_HPY_SSIZE_T("graal_hpy_write_HPy_ssize_t"),
    GRAAL_HPY_WRITE_PTR("graal_hpy_write_ptr"),

    GRAAL_HPY_BULK_FREE("graal_hpy_bulk_free");

    private final String name;

    GraalHPyNativeSymbol(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
