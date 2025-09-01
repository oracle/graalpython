/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SRC_HANDLES_H_
#define SRC_HANDLES_H_

#include <object.h>

#define MANAGED_REFCNT 10

/*
 * We cannot do NaN tagging. Even if we rely on running systems that use at
 * most 48 bits for their adresses (so not running with PML5/la57 enabled), we
 * do not control all allocations, so PyObject* allocated somewhere else will
 * not be tagged and thus are indistinguishable from subnormal floating point
 * values or +0.0 for NULL values. Additionally, we know that at least cffi
 * uses tagging of the low 3 alignment bits o PyObject* and thus those also
 * need to be 0 for anything we hand out. So we can realistically only use the
 * upper 16 and lower 45 bits, which isn't enough to do NaN tagging (unless we
 * want to do weird stuff like disallow certain double bit patterns). So the
 * thing we can do is to tag managed pointers, 32-bit integers, and double
 * values that fit into single-precision floats without loss.
 */

// Aligned with the same constants in CApiTransitions. Update comment there if
// you change or move these.
#define HANDLE_TAG_BIT  ((uint64_t)(1ULL << 63))
#define INTEGER_TAG_BIT ((uint64_t)(1ULL << 62))
#define FLOAT_TAG_BIT   ((uint64_t)(1ULL << 61))

 // IMMORTAL_REFCNT value is aligned with include/object.h
#define IMMORTAL_REFCNT 0xFFFFFFFFL
#define _35_BIT_MASK (0xFFFFFFFFULL << 3)

#define points_to_py_handle_space(PTR) (((uint64_t)(uintptr_t)(PTR)) & HANDLE_TAG_BIT)
#define points_to_py_int_handle(PTR) (((uint64_t)(uintptr_t)(PTR)) & INTEGER_TAG_BIT)
#define points_to_py_float_handle(PTR) (((uint64_t)(uintptr_t)(PTR)) & FLOAT_TAG_BIT)

#define stub_to_pointer(STUB_PTR) ((uintptr_t)(((uint64_t)(uintptr_t)(PTR)) | HANDLE_TAG_BIT))
#define int_to_pointer(INT)       ((uintptr_t)((((uint64_t)(uint32_t)(INT) << 3) & _35_BIT_MASK) | HANDLE_TAG_BIT | INTEGER_TAG_BIT))
static inline PyObject* float_to_pointer(float dbl) {
    uint32_t float_bits;
    memcpy(&float_bits, &dbl, sizeof(float));
    return (PyObject *)(uintptr_t)(((((uint64_t)float_bits) << 3) & _35_BIT_MASK) | HANDLE_TAG_BIT | FLOAT_TAG_BIT);
}

#define pointer_to_stub(PTR)  ((PyObject*)(((uint64_t)(uintptr_t)(PTR)) & ~HANDLE_TAG_BIT))
#define pointer_to_long(PTR)   ((int64_t)(int32_t)(((uint64_t)(uintptr_t)(PTR)) >> 3))
static inline double pointer_to_double(PyObject* ptr) {
    uint32_t float_bits = (uint32_t)(((uint64_t)(uintptr_t)ptr) >> 3);
    float value;
    memcpy(&value, &float_bits, sizeof(float));
    return (double)value;
}

#endif /* SRC_HANDLES_H_ */
