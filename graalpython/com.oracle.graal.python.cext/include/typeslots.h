/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
/* Do not renumber the file; these numbers are part of the stable ABI. */
/* Disabled, see #10181 */
#undef Py_bf_getbuffer
#undef Py_bf_releasebuffer
#define Py_mp_ass_subscript 3
#define Py_mp_length 4
#define Py_mp_subscript 5
#define Py_nb_absolute 6
#define Py_nb_add 7
#define Py_nb_and 8
#define Py_nb_bool 9
#define Py_nb_divmod 10
#define Py_nb_float 11
#define Py_nb_floor_divide 12
#define Py_nb_index 13
#define Py_nb_inplace_add 14
#define Py_nb_inplace_and 15
#define Py_nb_inplace_floor_divide 16
#define Py_nb_inplace_lshift 17
#define Py_nb_inplace_multiply 18
#define Py_nb_inplace_or 19
#define Py_nb_inplace_power 20
#define Py_nb_inplace_remainder 21
#define Py_nb_inplace_rshift 22
#define Py_nb_inplace_subtract 23
#define Py_nb_inplace_true_divide 24
#define Py_nb_inplace_xor 25
#define Py_nb_int 26
#define Py_nb_invert 27
#define Py_nb_lshift 28
#define Py_nb_multiply 29
#define Py_nb_negative 30
#define Py_nb_or 31
#define Py_nb_positive 32
#define Py_nb_power 33
#define Py_nb_remainder 34
#define Py_nb_rshift 35
#define Py_nb_subtract 36
#define Py_nb_true_divide 37
#define Py_nb_xor 38
#define Py_sq_ass_item 39
#define Py_sq_concat 40
#define Py_sq_contains 41
#define Py_sq_inplace_concat 42
#define Py_sq_inplace_repeat 43
#define Py_sq_item 44
#define Py_sq_length 45
#define Py_sq_repeat 46
#define Py_tp_alloc 47
#define Py_tp_base 48
#define Py_tp_bases 49
#define Py_tp_call 50
#define Py_tp_clear 51
#define Py_tp_dealloc 52
#define Py_tp_del 53
#define Py_tp_descr_get 54
#define Py_tp_descr_set 55
#define Py_tp_doc 56
#define Py_tp_getattr 57
#define Py_tp_getattro 58
#define Py_tp_hash 59
#define Py_tp_init 60
#define Py_tp_is_gc 61
#define Py_tp_iter 62
#define Py_tp_iternext 63
#define Py_tp_methods 64
#define Py_tp_new 65
#define Py_tp_repr 66
#define Py_tp_richcompare 67
#define Py_tp_setattr 68
#define Py_tp_setattro 69
#define Py_tp_str 70
#define Py_tp_traverse 71
#define Py_tp_members 72
#define Py_tp_getset 73
#define Py_tp_free 74
#define Py_nb_matrix_multiply 75
#define Py_nb_inplace_matrix_multiply 76
#define Py_am_await 77
#define Py_am_aiter 78
#define Py_am_anext 79
#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 >= 0x03050000
/* New in 3.5 */
#define Py_tp_finalize 80
#endif