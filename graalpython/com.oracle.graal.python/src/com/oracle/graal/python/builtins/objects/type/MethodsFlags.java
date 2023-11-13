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
package com.oracle.graal.python.builtins.objects.type;

import com.oracle.graal.python.annotations.CApiConstants;

/**
 * This class is a simple representation of methods slots occupation of `cls->tp_as_number`,
 * `cls->tp_as_sequence` and `cls->tp_as_mapping`. Builtins types are populated manually and then
 * assigned in {@link com.oracle.graal.python.builtins.PythonBuiltinClassType}. While other heap
 * types are set during type initalization in {@link SpecialMethodSlot}.
 *
 * Use {@link com.oracle.graal.python.lib.GetMethodsFlagsNode} to retrieve slots occupation of a
 * given class.
 */
@CApiConstants
public abstract class MethodsFlags {

    // PyNumberMethods

    /*
     * Number implementations must check *both* arguments for proper type and implement the
     * necessary conversions in the slot functions themselves.
     */

    public static final long NB_ADD = 1L;
    public static final long NB_SUBTRACT = 1L << 1;
    public static final long NB_MULTIPLY = 1L << 2;
    public static final long NB_REMAINDER = 1L << 3;
    public static final long NB_DIVMOD = 1L << 4;
    public static final long NB_POWER = 1L << 5;
    public static final long NB_NEGATIVE = 1L << 6;
    public static final long NB_POSITIVE = 1L << 7;
    public static final long NB_ABSOLUTE = 1L << 8;
    public static final long NB_BOOL = 1L << 9;
    public static final long NB_INVERT = 1L << 10;
    public static final long NB_LSHIFT = 1L << 11;
    public static final long NB_RSHIFT = 1L << 12;
    public static final long NB_AND = 1L << 13;
    public static final long NB_XOR = 1L << 14;
    public static final long NB_OR = 1L << 15;
    public static final long NB_INT = 1L << 16;
    public static final long NB_FLOAT = 1L << 18;
    public static final long NB_INPLACE_ADD = 1L << 19;
    public static final long NB_INPLACE_SUBTRACT = 1L << 20;
    public static final long NB_INPLACE_MULTIPLY = 1L << 21;
    public static final long NB_INPLACE_REMAINDER = 1L << 22;
    public static final long NB_INPLACE_POWER = 1L << 23;
    public static final long NB_INPLACE_LSHIFT = 1L << 24;
    public static final long NB_INPLACE_RSHIFT = 1L << 25;
    public static final long NB_INPLACE_AND = 1L << 26;
    public static final long NB_INPLACE_XOR = 1L << 27;
    public static final long NB_INPLACE_OR = 1L << 28;
    public static final long NB_FLOOR_DIVIDE = 1L << 29;
    public static final long NB_TRUE_DIVIDE = 1L << 30;
    public static final long NB_INPLACE_FLOOR_DIVIDE = 1L << 31;
    public static final long NB_INPLACE_TRUE_DIVIDE = 1L << 32;
    public static final long NB_INDEX = 1L << 33;
    public static final long NB_MATRIX_MULTIPLY = 1L << 34;
    public static final long NB_INPLACE_MATRIX_MULTIPLY = 1L << 35;

    // this is helpful to determine if the binop slot is of a heaptype.
    public static final long SLOT1BINFULL = 1L << 39;

    private static final long SLOT1BINFULL_METHODS = SLOT1BINFULL | NB_ADD | NB_SUBTRACT | NB_POWER | NB_FLOOR_DIVIDE |
                    NB_TRUE_DIVIDE | NB_LSHIFT | NB_RSHIFT | NB_AND | NB_XOR | NB_OR | NB_MULTIPLY | NB_REMAINDER |
                    NB_DIVMOD | NB_MATRIX_MULTIPLY;

    public static boolean isSLOT1BINFULL(long methodsFlags, long op) {
        return (methodsFlags & (SLOT1BINFULL_METHODS & (op | SLOT1BINFULL))) > SLOT1BINFULL;
    }

    // PySequenceMethods

    public static final long SQ_LENGTH = 1L << 40;
    public static final long SQ_CONCAT = 1L << 41;
    public static final long SQ_REPEAT = 1L << 42;
    public static final long SQ_ITEM = 1L << 43;
    public static final long SQ_ASS_ITEM = 1L << 45;
    public static final long SQ_CONTAINS = 1L << 47;
    public static final long SQ_INPLACE_CONCAT = 1L << 48;
    public static final long SQ_INPLACE_REPEAT = 1L << 49;

    // PyMappingMethods

    public static final long MP_LENGTH = 1L << 50;
    public static final long MP_SUBSCRIPT = 1L << 51;
    public static final long MP_ASS_SUBSCRIPT = 1L << 52;

    public static final long AM_AWAIT = 1L << 54;
    public static final long AM_AITER = 1L << 55;
    public static final long AM_ANEXT = 1L << 56;
    public static final long AM_SEND = 1L << 57;

    public static final long ASYNC_METHODS = AM_AWAIT | AM_AITER | AM_ANEXT | AM_SEND;
    public static final long SEQUENCE_METHODS = SQ_LENGTH | SQ_ITEM | SQ_ASS_ITEM | SQ_CONCAT | SQ_INPLACE_CONCAT | SQ_INPLACE_REPEAT | SQ_REPEAT | SQ_CONTAINS;
    public static final long MAPPING_METHODS = MP_LENGTH | MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;

    // builtins methods flags

    public static final long DEFAULT_M_FLAGS = 0;

    public static final long TYPE_M_FLAGS = NB_OR;

    public static final long NONE_M_FLAGS = NB_BOOL;
    public static final long INT_M_FLAGS = NB_ADD | NB_SUBTRACT | NB_MULTIPLY | NB_REMAINDER | NB_DIVMOD |
                    NB_POWER | NB_NEGATIVE | NB_POSITIVE | NB_ABSOLUTE | NB_BOOL | NB_INVERT | NB_LSHIFT |
                    NB_RSHIFT | NB_AND | NB_XOR | NB_OR | NB_INT | NB_FLOAT |
                    NB_FLOOR_DIVIDE | NB_TRUE_DIVIDE | NB_INDEX;
    public static final long BOOLEAN_M_FLAGS = INT_M_FLAGS /* base */ | NB_AND | NB_XOR | NB_OR;
    public static final long FLOAT_M_FLAGS = NB_ADD | NB_SUBTRACT | NB_MULTIPLY | NB_REMAINDER | NB_DIVMOD |
                    NB_POWER | NB_NEGATIVE | NB_POSITIVE | NB_ABSOLUTE | NB_BOOL | NB_INT | NB_FLOAT |
                    NB_FLOOR_DIVIDE | NB_TRUE_DIVIDE;
    public static final long BYTE_ARRAY_M_FLAGS = NB_REMAINDER | SQ_LENGTH | SQ_CONCAT | SQ_REPEAT |
                    SQ_ITEM | SQ_ASS_ITEM | SQ_CONTAINS | SQ_INPLACE_CONCAT | SQ_INPLACE_REPEAT | MP_LENGTH |
                    MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;
    public static final long BYTES_M_FLAGS = NB_REMAINDER | SQ_LENGTH | SQ_CONCAT | SQ_REPEAT | SQ_ITEM |
                    SQ_CONTAINS | MP_LENGTH | MP_SUBSCRIPT;
    public static final long COMPLEX_M_FLAGS = NB_ADD | NB_SUBTRACT | NB_MULTIPLY | NB_POWER | NB_NEGATIVE |
                    NB_POSITIVE | NB_ABSOLUTE | NB_BOOL | NB_TRUE_DIVIDE;
    public static final long DICT_M_FLAGS = NB_OR | NB_INPLACE_OR | SQ_CONTAINS | MP_LENGTH | MP_SUBSCRIPT |
                    MP_ASS_SUBSCRIPT;
    public static final long DICTVALUESVIEW_M_FLAGS = SQ_LENGTH;
    public static final long DICTKEYSVIEW_M_FLAGS = NB_SUBTRACT | NB_AND | NB_XOR | NB_OR | SQ_LENGTH |
                    SQ_CONTAINS;

    public static final long DICTITEMSVIEW_M_FLAGS = NB_SUBTRACT | NB_AND | NB_XOR | NB_OR | SQ_LENGTH |
                    SQ_CONTAINS;
    public static final long LIST_M_FLAGS = SQ_LENGTH | SQ_CONCAT | SQ_REPEAT | SQ_ITEM | SQ_CONTAINS |
                    SQ_INPLACE_CONCAT | SQ_INPLACE_REPEAT | MP_LENGTH | MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;
    public static final long TUPLE_M_FLAGS = SQ_LENGTH | SQ_CONCAT | SQ_REPEAT | SQ_ITEM | SQ_CONTAINS |
                    MP_LENGTH | MP_SUBSCRIPT;
    public static final long MEMORYVIEW_M_FLAGS = SQ_LENGTH | SQ_ITEM | MP_LENGTH | MP_SUBSCRIPT |
                    MP_ASS_SUBSCRIPT;
    public static final long RANGE_M_FLAGS = NB_BOOL | SQ_LENGTH | SQ_ITEM | SQ_CONTAINS | MP_LENGTH |
                    MP_SUBSCRIPT;
    public static final long FROZENSET_M_FLAGS = NB_SUBTRACT | NB_AND | NB_XOR | NB_OR | SQ_LENGTH |
                    SQ_CONTAINS;
    public static final long SET_M_FLAGS = NB_SUBTRACT | NB_AND | NB_XOR | NB_OR |
                    NB_INPLACE_SUBTRACT | NB_INPLACE_AND | NB_INPLACE_XOR | NB_INPLACE_OR | SQ_LENGTH | SQ_CONTAINS;
    public static final long STRING_M_FLAGS = NB_REMAINDER | SQ_LENGTH | SQ_CONCAT | SQ_REPEAT | SQ_ITEM |
                    SQ_CONTAINS | MP_LENGTH | MP_SUBSCRIPT;

    public static final long DEFAULTDICT_M_FLAGS = NB_OR | DICT_M_FLAGS;
    public static final long DEQUE_M_FLAGS = SQ_LENGTH | SQ_CONCAT | SQ_REPEAT |
                    SQ_ITEM | SQ_ASS_ITEM | SQ_CONTAINS | SQ_INPLACE_CONCAT | SQ_INPLACE_REPEAT;

    public static final long MAPPINGPROXY_M_FLAGS = NB_OR | NB_INPLACE_OR | SQ_CONTAINS | MP_LENGTH | MP_SUBSCRIPT;
    public static final long ARRAY_M_FLAGS = SQ_LENGTH | SQ_CONCAT | SQ_REPEAT | SQ_ITEM | SQ_ASS_ITEM |
                    SQ_CONTAINS | SQ_INPLACE_CONCAT | SQ_INPLACE_REPEAT | MP_LENGTH | MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;
    public static final long MMAP_M_FLAGS = SQ_LENGTH | SQ_ITEM | SQ_ASS_ITEM |
                    MP_LENGTH | MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;

    public static final long GENERIC_ALIAS_M_FLAGS = NB_OR | MP_SUBSCRIPT;
    public static final long UNION_TYPE_M_FLAGS = NB_OR | MP_SUBSCRIPT;

    public static final long CONTEXT_M_FLAGS = SQ_CONTAINS | MP_LENGTH | MP_SUBSCRIPT;

    public static final long GENERATOR_M_FLAGS = AM_SEND;
    public static final long COROUTINE_M_FLAGS = AM_AWAIT | AM_SEND;
    public static final long ASYNC_GENERATOR_M_FLAGS = AM_AITER | AM_ANEXT | AM_SEND;
    public static final long ASYNC_GENERATOR_ASEND_M_FLAGS = AM_AWAIT;
    public static final long ASYNC_GENERATOR_ATHROW_M_FLAGS = AM_AWAIT;

    // _ctypes
    public static final long PYCFUNCPTRTYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;
    public static final long PYCARRAY_M_FLAGS = SQ_LENGTH | SQ_ITEM | SQ_ASS_ITEM | MP_LENGTH | MP_SUBSCRIPT |
                    MP_ASS_SUBSCRIPT;
    public static final long PYCARRAYTYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;
    public static final long PYCFUNCPTR_M_FLAGS = NB_BOOL;
    public static final long PYCPOINTER_M_FLAGS = NB_BOOL | SQ_ITEM | SQ_ASS_ITEM | MP_SUBSCRIPT;
    public static final long PYCPOINTERTYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;
    public static final long PYCSIMPLETYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;
    public static final long PYCSTRUCTTYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;
    public static final long SIMPLECDATA_M_FLAGS = NB_BOOL;
    public static final long UNIONTYPE_M_FLAGS = SQ_REPEAT | TYPE_M_FLAGS;

    public static final long FOREIGNOBJECT_M_FLAGS = NB_BOOL | SQ_LENGTH | MP_LENGTH | NB_ADD | NB_MULTIPLY |
                    NB_SUBTRACT | NB_DIVMOD | NB_FLOOR_DIVIDE | NB_TRUE_DIVIDE | NB_AND | NB_XOR | NB_OR | NB_INDEX |
                    SQ_CONTAINS | MP_SUBSCRIPT | MP_ASS_SUBSCRIPT;

}
