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

typedef struct {
	size_t size;
} mem_head_t;


/* Get an object's GC head */
#define AS_MEM_HEAD(o) ((mem_head_t *)(o)-1)

/* Get the object given the GC head */
#define FROM_MEM_HEAD(g) ((void *)(((mem_head_t *)g)+1))

typedef int (*alloc_reporter_fun_t)(void *, Py_ssize_t size);
UPCALL_TYPED_ID(PyTruffle_Object_Alloc, alloc_reporter_fun_t);

/* This is our version of 'PyObject_Free' which is also able to free Sulong handles. */
MUST_INLINE static
void PyTruffle_Object_Free(void* ptr) {
	if((!truffle_cannot_be_handle(ptr) && truffle_is_handle_to_managed(ptr)) || polyglot_is_value(ptr)) {
		if(polyglot_ensure_i32(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Object_Free", native_pointer_to_java(ptr)))) {
		    /* If 1 is returned, the upcall function already took care of freeing */
		    return;
		}
	}
	mem_head_t* ptr_with_head = AS_MEM_HEAD(ptr);
    (void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Trace_Free", ptr, ptr_with_head->size);
    free(ptr_with_head);
}

void* PyObject_Malloc(size_t size) {
	// we add a header
	mem_head_t* ptr_with_head = calloc(size + sizeof(mem_head_t), 1);
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
	_jls_PyTruffle_Object_Alloc(ptr, size);
    return ptr;
}

void* PyObject_Realloc(void *ptr, size_t new_size) {
	void* new_ptr = realloc(ptr, new_size);
    _jls_PyTruffle_Object_Alloc(new_ptr, new_size);
	return new_ptr;
}

void PyObject_Free(void* ptr) {
	PyTruffle_Object_Free(ptr);
}

void* PyMem_Malloc(size_t size) {
    void* ptr;
    if (size > (size_t)PY_SSIZE_T_MAX) {
        return NULL;
    }
    ptr = malloc(size == 0 ? 1 : size);
    _jls_PyTruffle_Object_Alloc(ptr, size);
    return ptr;
}

void* PyMem_RawMalloc(size_t size) {
    void* ptr = malloc(size == 0 ? 1 : size);
    _jls_PyTruffle_Object_Alloc(ptr, size);
    return ptr;
}

void* PyMem_RawCalloc(size_t nelem, size_t elsize) {
    size_t n = nelem == 0 || elsize == 0 ? 1 : nelem;
    void* ptr = calloc(n, elsize);
    _jls_PyTruffle_Object_Alloc(ptr, n * elsize);
    return ptr;
}

void* PyMem_RawRealloc(void *ptr, size_t new_size) {
    return realloc(ptr, new_size);
}

void PyMem_RawFree(void *ptr) {
	PyTruffle_Object_Free(ptr);
}

void * PyMem_Realloc(void *ptr, size_t new_size) {
    return PyMem_RawRealloc(ptr, new_size);
}

void PyMem_Free(void *ptr) {
	PyTruffle_Object_Free(ptr);
}

int PyTraceMalloc_Track(unsigned int domain, uintptr_t ptr, size_t size) {
	// 0 = success, -2 = disabled
    return _jls_PyTruffle_Object_Alloc(ptr, size);
}


int PyTraceMalloc_Untrack(unsigned int domain, uintptr_t ptr) {
	// 0 = success, -2 = disabled
    if(PyTruffle_Trace_Memory()) {
        (void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Trace_Free", (int64_t)ptr);
        return 0;
    }
    return -2;
}
