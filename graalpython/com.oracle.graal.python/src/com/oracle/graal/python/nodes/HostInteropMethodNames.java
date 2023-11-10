/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

public abstract class HostInteropMethodNames {
    public static final String J_IS_BOOLEAN = "is_boolean";
    public static final TruffleString T_IS_BOOLEAN = tsLiteral(J_IS_BOOLEAN);
    public static final String J_IS_DATE = "is_date";
    public static final TruffleString T_IS_DATE = tsLiteral(J_IS_DATE);
    public static final String J_IS_DURATION = "is_duration";
    public static final TruffleString T_IS_DURATION = tsLiteral(J_IS_DURATION);
    public static final String J_IS_EXCEPTION = "is_exception";
    public static final TruffleString T_IS_EXCEPTION = tsLiteral(J_IS_EXCEPTION);
    public static final String J_IS_ITERATOR = "is_iterator";
    public static final TruffleString T_IS_ITERATOR = tsLiteral(J_IS_ITERATOR);
    public static final String J_IS_NULL = "is_null";
    public static final TruffleString T_IS_NULL = tsLiteral(J_IS_NULL);
    public static final String J_IS_NUMBER = "is_number";
    public static final TruffleString T_IS_NUMBER = tsLiteral(J_IS_NUMBER);
    public static final String J_IS_STRING = "is_string";
    public static final TruffleString T_IS_STRING = tsLiteral(J_IS_STRING);
    public static final String J_IS_TIME = "is_time";
    public static final TruffleString T_IS_TIME = tsLiteral(J_IS_TIME);
    public static final String J_IS_TIME_ZONE = "is_time_zone";
    public static final TruffleString T_IS_TIME_ZONE = tsLiteral(J_IS_TIME_ZONE);
    public static final String J_IS_EXECUTABLE = "is_executable";
    public static final TruffleString T_IS_EXECUTABLE = tsLiteral(J_IS_EXECUTABLE);
    public static final String J_FITS_IN_BIG_INTEGER = "fits_in_big_integer";
    public static final TruffleString T_FITS_IN_BIG_INTEGER = tsLiteral(J_FITS_IN_BIG_INTEGER);
    public static final String J_FITS_IN_BYTE = "fits_in_byte";
    public static final TruffleString T_FITS_IN_BYTE = tsLiteral(J_FITS_IN_BYTE);
    public static final String J_FITS_IN_DOUBLE = "fits_in_double";
    public static final TruffleString T_FITS_IN_DOUBLE = tsLiteral(J_FITS_IN_DOUBLE);
    public static final String J_FITS_IN_FLOAT = "fits_in_float";
    public static final TruffleString T_FITS_IN_FLOAT = tsLiteral(J_FITS_IN_FLOAT);
    public static final String J_FITS_IN_INT = "fits_in_int";
    public static final TruffleString T_FITS_IN_INT = tsLiteral(J_FITS_IN_INT);
    public static final String J_FITS_IN_LONG = "fits_in_long";
    public static final TruffleString T_FITS_IN_LONG = tsLiteral(J_FITS_IN_LONG);
    public static final String J_FITS_IN_SHORT = "fits_in_short";
    public static final TruffleString T_FITS_IN_SHORT = tsLiteral(J_FITS_IN_SHORT);
    public static final String J_AS_BIG_INTEGER = "as_big_integer";
    public static final TruffleString T_AS_BIG_INTEGER = tsLiteral(J_AS_BIG_INTEGER);
    public static final String J_AS_BOOLEAN = "as_boolean";
    public static final TruffleString T_AS_BOOLEAN = tsLiteral(J_AS_BOOLEAN);
    public static final String J_AS_BYTE = "as_byte";
    public static final TruffleString T_AS_BYTE = tsLiteral(J_AS_BYTE);
    public static final String J_AS_DATE = "as_date";
    public static final TruffleString T_AS_DATE = tsLiteral(J_AS_DATE);
    public static final String J_AS_DOUBLE = "as_double";
    public static final TruffleString T_AS_DOUBLE = tsLiteral(J_AS_DOUBLE);
    public static final String J_AS_DURATION = "as_duration";
    public static final TruffleString T_AS_DURATION = tsLiteral(J_AS_DURATION);
    public static final String J_AS_FLOAT = "as_float";
    public static final TruffleString T_AS_FLOAT = tsLiteral(J_AS_FLOAT);
    public static final String J_AS_INT = "as_int";
    public static final TruffleString T_AS_INT = tsLiteral(J_AS_INT);
    public static final String J_AS_LONG = "as_long";
    public static final TruffleString T_AS_LONG = tsLiteral(J_AS_LONG);
    public static final String J_AS_SHORT = "as_short";
    public static final TruffleString T_AS_SHORT = tsLiteral(J_AS_SHORT);
    public static final String J_AS_STRING = "as_string";
    public static final TruffleString T_AS_STRING = tsLiteral(J_AS_STRING);
    public static final String J_AS_TIME = "as_time";
    public static final TruffleString T_AS_TIME = tsLiteral(J_AS_TIME);
    public static final String J_AS_TIME_ZONE = "as_time_zone";
    public static final TruffleString T_AS_TIME_ZONE = tsLiteral(J_AS_TIME_ZONE);
    public static final String J_EXECUTE = "execute";
    public static final TruffleString T_EXECUTE = tsLiteral(J_EXECUTE);
    public static final String J_READ_ARRAY_ELEMENT = "read_array_element";
    public static final TruffleString T_READ_ARRAY_ELEMENT = tsLiteral(J_READ_ARRAY_ELEMENT);
    public static final String J_GET_ARRAY_SIZE = "get_array_size";
    public static final TruffleString T_GET_ARRAY_SIZE = tsLiteral(J_GET_ARRAY_SIZE);
    public static final String J_HAS_ARRAY_ELEMENTS = "has_array_elements";
    public static final TruffleString T_HAS_ARRAY_ELEMENTS = tsLiteral(J_HAS_ARRAY_ELEMENTS);
    public static final String J_IS_ARRAY_ELEMENT_READABLE = "is_array_element_readable";
    public static final TruffleString T_IS_ARRAY_ELEMENT_READABLE = tsLiteral(J_IS_ARRAY_ELEMENT_READABLE);
    public static final String J_IS_ARRAY_ELEMENT_MODIFIABLE = "is_array_element_modifiable";
    public static final TruffleString T_IS_ARRAY_ELEMENT_MODIFIABLE = tsLiteral(J_IS_ARRAY_ELEMENT_MODIFIABLE);
    public static final String J_IS_ARRAY_ELEMENT_INSERTABLE = "is_array_element_insertable";
    public static final TruffleString T_IS_ARRAY_ELEMENT_INSERTABLE = tsLiteral(J_IS_ARRAY_ELEMENT_INSERTABLE);
    public static final String J_IS_ARRAY_ELEMENT_REMOVABLE = "is_array_element_removable";
    public static final TruffleString T_IS_ARRAY_ELEMENT_REMOVABLE = tsLiteral(J_IS_ARRAY_ELEMENT_REMOVABLE);
    public static final String J_REMOVE_ARRAY_ELEMENT = "remove_array_element";
    public static final TruffleString T_REMOVE_ARRAY_ELEMENT = tsLiteral(J_REMOVE_ARRAY_ELEMENT);
    public static final String J_WRITE_ARRAY_ELEMENT = "write_array_element";
    public static final TruffleString T_WRITE_ARRAY_ELEMENT = tsLiteral(J_WRITE_ARRAY_ELEMENT);
    public static final String J_HAS_ITERATOR = "has_iterator";
    public static final TruffleString T_HAS_ITERATOR = tsLiteral(J_HAS_ITERATOR);
    public static final String J_HAS_ITERATOR_NEXT_ELEMENT = "has_iterator_next_element";
    public static final TruffleString T_HAS_ITERATOR_NEXT_ELEMENT = tsLiteral(J_HAS_ITERATOR_NEXT_ELEMENT);
    public static final String J_GET_ITERATOR = "get_iterator";
    public static final TruffleString T_GET_ITERATOR = tsLiteral(J_GET_ITERATOR);
    public static final String J_GET_ITERATOR_NEXT_ELEMENT = "get_iterator_next_element";
    public static final TruffleString T_GET_ITERATOR_NEXT_ELEMENT = tsLiteral(J_GET_ITERATOR_NEXT_ELEMENT);
    public static final String J_GET_HASH_ENTRIES_ITERATOR = "get_hash_entries_iterator";
    public static final TruffleString T_GET_HASH_ENTRIES_ITERATOR = tsLiteral(J_GET_HASH_ENTRIES_ITERATOR);
    public static final String J_GET_HASH_KEYS_ITERATOR = "get_hash_keys_iterator";
    public static final TruffleString T_GET_HASH_KEYS_ITERATOR = tsLiteral(J_GET_HASH_KEYS_ITERATOR);
    public static final String J_GET_HASH_SIZE = "get_hash_size";
    public static final TruffleString T_GET_HASH_SIZE = tsLiteral(J_GET_HASH_SIZE);
    public static final String J_READ_HASH_VALUE = "get_hash_value";
    public static final TruffleString T_READ_HASH_VALUE = tsLiteral(J_READ_HASH_VALUE);
    public static final String J_READ_HASH_VALUE_OR_DEFAULT = "get_hash_value_or_default";
    public static final TruffleString T_READ_HASH_VALUE_OR_DEFAULT = tsLiteral(J_READ_HASH_VALUE_OR_DEFAULT);
    public static final String J_GET_HASH_VALUES_ITERATOR = "get_hash_values_iterator";
    public static final TruffleString T_GET_HASH_VALUES_ITERATOR = tsLiteral(J_GET_HASH_VALUES_ITERATOR);
    public static final String J_HAS_HASH_ENTRIES = "has_hash_entries";
    public static final TruffleString T_HAS_HASH_ENTRIES = tsLiteral(J_HAS_HASH_ENTRIES);
    public static final String J_IS_HASH_ENTRY_READABLE = "is_hash_entry_readable";
    public static final TruffleString T_IS_HASH_ENTRY_READABLE = tsLiteral(J_IS_HASH_ENTRY_READABLE);
    public static final String J_IS_HASH_ENTRY_MODIFIABLE = "is_hash_entry_modifiable";
    public static final TruffleString T_IS_HASH_ENTRY_MODIFIABLE = tsLiteral(J_IS_HASH_ENTRY_MODIFIABLE);
    public static final String J_IS_HASH_ENTRY_INSERTABLE = "is_hash_entry_insertable";
    public static final TruffleString T_IS_HASH_ENTRY_INSERTABLE = tsLiteral(J_IS_HASH_ENTRY_INSERTABLE);
    public static final String J_IS_HASH_ENTRY_REMOVABLE = "is_hash_entry_removable";
    public static final TruffleString T_IS_HASH_ENTRY_REMOVABLE = tsLiteral(J_IS_HASH_ENTRY_REMOVABLE);
    public static final String J_WRITE_HASH_ENTRY = "write_hash_entry";
    public static final TruffleString T_WRITE_HASH_ENTRY = tsLiteral(J_WRITE_HASH_ENTRY);
    public static final String J_REMOVE_HASH_ENTRY = "remove_hash_entry";
    public static final TruffleString T_REMOVE_HASH_ENTRY = tsLiteral(J_REMOVE_HASH_ENTRY);
}
