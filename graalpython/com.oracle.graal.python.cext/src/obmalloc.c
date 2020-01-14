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
#include "capi.h"

struct _PyTraceMalloc_Config _Py_tracemalloc_config = {
  .initialized = 0,
  .tracing = 0,
  .max_nframe = 1,
  .use_domain = 0
};

void* PyMem_Malloc(size_t size) {
    if (size > (size_t)PY_SSIZE_T_MAX) {
        return NULL;
    }
    if(size == 0) {
        return malloc(1);
    }
    return malloc(size);
}

void* PyMem_RawMalloc(size_t size) {
    if(size == 0) {
        return malloc(1);
    }
    return malloc(size);
}

void* PyMem_RawCalloc(size_t nelem, size_t elsize) {
    if(nelem == 0 || elsize == 0) {
        return calloc(1, elsize);
    }
    return calloc(nelem, elsize);
}

void* PyMem_RawRealloc(void *ptr, size_t new_size) {
    return realloc(ptr, new_size);
}

void PyMem_RawFree(void *ptr) {
    free(ptr);
}

void * PyMem_Realloc(void *ptr, size_t new_size) {
    return PyMem_RawRealloc(ptr, new_size);
}

void PyMem_Free(void *ptr) {
    free(ptr);
}

int PyTraceMalloc_Track(unsigned int domain, uintptr_t ptr, size_t size) {
	// 0 = success, -2 = disabled
    return PyObject_AllocationReporter(ptr, size);
}


int PyTraceMalloc_Untrack(unsigned int domain, uintptr_t ptr) {
	// 0 = success, -2 = disabled
    return 0;
}
