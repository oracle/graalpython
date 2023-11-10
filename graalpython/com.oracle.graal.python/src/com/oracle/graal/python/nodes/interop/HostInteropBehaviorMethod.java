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
package com.oracle.graal.python.nodes.interop;

import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_AS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_EXECUTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_FITS_IN_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_ARRAY_SIZE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_HASH_ENTRIES_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_HASH_KEYS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_HASH_SIZE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_HASH_VALUES_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_GET_ITERATOR_NEXT_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_HAS_ARRAY_ELEMENTS;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_HAS_HASH_ENTRIES;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_HAS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_HAS_ITERATOR_NEXT_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ARRAY_ELEMENT_INSERTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ARRAY_ELEMENT_MODIFIABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ARRAY_ELEMENT_READABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ARRAY_ELEMENT_REMOVABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_EXECUTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_HASH_ENTRY_INSERTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_HASH_ENTRY_MODIFIABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_HASH_ENTRY_READABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_HASH_ENTRY_REMOVABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_NULL;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_NUMBER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_IS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_READ_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_READ_HASH_VALUE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_REMOVE_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_REMOVE_HASH_ENTRY;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_WRITE_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.J_WRITE_HASH_ENTRY;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_INSTANT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_AS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_EXECUTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_BIG_INTEGER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_BYTE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_DOUBLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_FLOAT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_INT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_LONG;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_FITS_IN_SHORT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_ARRAY_SIZE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_HASH_ENTRIES_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_HASH_KEYS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_HASH_SIZE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_HASH_VALUES_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_GET_ITERATOR_NEXT_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_HAS_ARRAY_ELEMENTS;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_HAS_HASH_ENTRIES;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_HAS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_HAS_ITERATOR_NEXT_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ARRAY_ELEMENT_INSERTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ARRAY_ELEMENT_MODIFIABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ARRAY_ELEMENT_READABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ARRAY_ELEMENT_REMOVABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_BOOLEAN;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_DATE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_DURATION;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_EXECUTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_HASH_ENTRY_INSERTABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_HASH_ENTRY_MODIFIABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_HASH_ENTRY_READABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_HASH_ENTRY_REMOVABLE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_ITERATOR;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_NULL;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_NUMBER;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_STRING;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_TIME;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_IS_TIME_ZONE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_READ_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_READ_HASH_VALUE;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_REMOVE_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_REMOVE_HASH_ENTRY;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_WRITE_ARRAY_ELEMENT;
import static com.oracle.graal.python.nodes.HostInteropMethodNames.T_WRITE_HASH_ENTRY;

import com.oracle.truffle.api.strings.TruffleString;

public enum HostInteropBehaviorMethod {
    is_boolean(J_IS_BOOLEAN, T_IS_BOOLEAN, true),
    is_date(J_IS_DATE, T_IS_DATE, true),
    is_duration(J_IS_DURATION, T_IS_DURATION, true),
    is_exception(J_IS_DATE, T_IS_DATE, true),
    is_instant(J_IS_INSTANT, T_IS_INSTANT, true),
    is_iterator(J_IS_ITERATOR, T_IS_ITERATOR, true),
    is_null(J_IS_NULL, T_IS_NULL, true),
    is_number(J_IS_NUMBER, T_IS_NUMBER, true),
    is_string(J_IS_STRING, T_IS_STRING, true),
    is_time(J_IS_TIME, T_IS_TIME, true),
    is_time_zone(J_IS_TIME_ZONE, T_IS_TIME_ZONE, true),
    is_executable(J_IS_EXECUTABLE, T_IS_EXECUTABLE, true),
    fits_in_big_integer(J_FITS_IN_BIG_INTEGER, T_FITS_IN_BIG_INTEGER),
    fits_in_byte(J_FITS_IN_BYTE, T_FITS_IN_BYTE),
    fits_in_double(J_FITS_IN_DOUBLE, T_FITS_IN_DOUBLE),
    fits_in_float(J_FITS_IN_FLOAT, T_FITS_IN_FLOAT),
    fits_in_int(J_FITS_IN_INT, T_FITS_IN_INT),
    fits_in_long(J_FITS_IN_LONG, T_FITS_IN_LONG),
    fits_in_short(J_FITS_IN_SHORT, T_FITS_IN_SHORT),
    as_big_integer(J_AS_BIG_INTEGER, T_AS_BIG_INTEGER),
    as_boolean(J_AS_BOOLEAN, T_AS_BOOLEAN),
    as_byte(J_AS_BYTE, T_AS_BYTE),
    as_date(J_AS_DATE, T_AS_DATE),
    as_double(J_AS_DOUBLE, T_AS_DOUBLE),
    as_duration(J_AS_DURATION, T_AS_DURATION),
    as_float(J_AS_FLOAT, T_AS_FLOAT),
    as_instant(J_AS_INSTANT, T_AS_INSTANT),
    as_int(J_AS_INT, T_AS_INT),
    as_long(J_AS_LONG, T_AS_LONG),
    as_short(J_AS_SHORT, T_AS_SHORT),
    as_string(J_AS_STRING, T_AS_STRING),
    as_time(J_AS_TIME, T_AS_TIME),
    as_time_zone(J_AS_TIME_ZONE, T_AS_TIME_ZONE),
    execute(J_EXECUTE, T_EXECUTE, 0, true),
    // array
    read_array_element(J_READ_ARRAY_ELEMENT, T_READ_ARRAY_ELEMENT, 1),
    get_array_size(J_GET_ARRAY_SIZE, T_GET_ARRAY_SIZE),
    has_array_elements(J_HAS_ARRAY_ELEMENTS, T_HAS_ARRAY_ELEMENTS, true),
    is_array_element_readable(J_IS_ARRAY_ELEMENT_READABLE, T_IS_ARRAY_ELEMENT_READABLE, 1),
    is_array_element_modifiable(J_IS_ARRAY_ELEMENT_MODIFIABLE, T_IS_ARRAY_ELEMENT_MODIFIABLE, 1),
    is_array_element_insertable(J_IS_ARRAY_ELEMENT_INSERTABLE, T_IS_ARRAY_ELEMENT_INSERTABLE, 1),
    is_array_element_removable(J_IS_ARRAY_ELEMENT_REMOVABLE, T_IS_ARRAY_ELEMENT_REMOVABLE, 1),
    remove_array_element(J_REMOVE_ARRAY_ELEMENT, T_REMOVE_ARRAY_ELEMENT, 1),
    write_array_element(J_WRITE_ARRAY_ELEMENT, T_WRITE_ARRAY_ELEMENT, 2),
    // iterator
    has_iterator(J_HAS_ITERATOR, T_HAS_ITERATOR, true),
    has_iterator_next_element(J_HAS_ITERATOR_NEXT_ELEMENT, T_HAS_ITERATOR_NEXT_ELEMENT),
    get_iterator(J_GET_ITERATOR, T_GET_ITERATOR),
    get_iterator_next_element(J_GET_ITERATOR_NEXT_ELEMENT, T_GET_ITERATOR_NEXT_ELEMENT),
    // hash
    has_hash_entries(J_HAS_HASH_ENTRIES, T_HAS_HASH_ENTRIES, true),
    get_hash_entries_iterator(J_GET_HASH_ENTRIES_ITERATOR, T_GET_HASH_ENTRIES_ITERATOR),
    get_hash_keys_iterator(J_GET_HASH_KEYS_ITERATOR, T_GET_HASH_KEYS_ITERATOR),
    get_hash_size(J_GET_HASH_SIZE, T_GET_HASH_SIZE),
    get_hash_values_iterator(J_GET_HASH_VALUES_ITERATOR, T_GET_HASH_VALUES_ITERATOR),
    is_hash_entry_readable(J_IS_HASH_ENTRY_READABLE, T_IS_HASH_ENTRY_READABLE, 1),
    is_hash_entry_modifiable(J_IS_HASH_ENTRY_MODIFIABLE, T_IS_HASH_ENTRY_MODIFIABLE, 1),
    is_hash_entry_insertable(J_IS_HASH_ENTRY_INSERTABLE, T_IS_HASH_ENTRY_INSERTABLE, 1),
    is_hash_entry_removable(J_IS_HASH_ENTRY_REMOVABLE, T_IS_HASH_ENTRY_REMOVABLE, 1),
    read_hash_value(J_READ_HASH_VALUE, T_READ_HASH_VALUE, 1),
    read_hash_value_or_default(J_READ_HASH_VALUE_OR_DEFAULT, T_READ_HASH_VALUE_OR_DEFAULT, 2),
    write_hash_entry(J_WRITE_HASH_ENTRY, T_WRITE_HASH_ENTRY, 2),
    remove_hash_entry(J_REMOVE_HASH_ENTRY, T_REMOVE_HASH_ENTRY, 1);

    public static HostInteropBehaviorMethod[] VALUES = HostInteropBehaviorMethod.values();

    public static int getLength() {
        return VALUES.length;
    }

    public final String name;
    public final TruffleString tsName;
    public final boolean constantBoolean;
    public final int extraArguments;

    public final boolean takesVarArgs;

    HostInteropBehaviorMethod(String name, TruffleString tsName, boolean constantBoolean, int extraArguments, boolean takesVarArgs) {
        assert !(constantBoolean && extraArguments > 0) : "constant HostInteropBehaviorMethods cannot have extra arguments!";
        this.name = name;
        this.tsName = tsName;
        this.constantBoolean = constantBoolean;
        this.extraArguments = extraArguments;
        this.takesVarArgs = takesVarArgs;
    }

    HostInteropBehaviorMethod(String name, TruffleString tsName, int extraArguments, boolean takesVarArgs) {
        this(name, tsName, false, extraArguments, takesVarArgs);
    }

    HostInteropBehaviorMethod(String name, TruffleString tsName, boolean constantBoolean, int extraArguments) {
        this(name, tsName, constantBoolean, extraArguments, false);
    }

    HostInteropBehaviorMethod(String name, TruffleString tsName, boolean constantBoolean) {
        this(name, tsName, constantBoolean, 0);
    }

    HostInteropBehaviorMethod(String name, TruffleString tsName, int extraArguments) {
        this(name, tsName, false, extraArguments);
    }

    HostInteropBehaviorMethod(String name, TruffleString tsName) {
        this(name, tsName, false, 0);
    }

    public boolean isConstantBoolean() {
        return constantBoolean;
    }

    public boolean checkArity(Object[] extraArguments) {
        return this.takesVarArgs || extraArguments.length == this.extraArguments;
    }

    @Override
    public String toString() {
        return "HostInteropBehaviorMethod{" +
                        "name='" + name + '\'' +
                        ", constantBoolean=" + constantBoolean +
                        ", extraArguments=" + extraArguments +
                        ", takesVarArgs=" + takesVarArgs +
                        '}';
    }
}
