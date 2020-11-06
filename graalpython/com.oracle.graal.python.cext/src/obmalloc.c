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

typedef void (*trace_free_fun_t)(void *, size_t);
UPCALL_TYPED_ID(PyTruffle_Trace_Free, trace_free_fun_t);

/* This is our version of 'PyObject_Free' which is also able to free Sulong handles. */
MUST_INLINE static
void _PyObject_Free(void* ptr) {
	if (ptr == NULL) {
		return;
	}
	if((points_to_handle_space(ptr) && is_handle(ptr)) || polyglot_is_value(ptr)) {
		if(free_upcall(native_pointer_to_java(ptr))) {
		    /* If 1 is returned, the upcall function already took care of freeing */
		    return;
		}
	}
    mem_head_t* ptr_with_head = AS_MEM_HEAD(ptr);
    _jls_PyTruffle_Trace_Free(ptr, ptr_with_head->size);
    free(ptr_with_head);
}

void* PyObject_Malloc(size_t size) {
	// we add a header
	mem_head_t* ptr_with_head = calloc(size + sizeof(mem_head_t), 1);
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
	alloc_upcall(ptr, size);
    return ptr;
}

void* PyObject_Realloc(void *ptr, size_t new_size) {
	mem_head_t* old = ptr != NULL ? AS_MEM_HEAD(ptr) : NULL;
    mem_head_t* ptr_with_head = (mem_head_t*) realloc(old, new_size + sizeof(mem_head_t));
    ptr_with_head->size = new_size;
    return FROM_MEM_HEAD(ptr_with_head);
}

void PyObject_Free(void* ptr) {
	_PyObject_Free(ptr);
}

void* PyMem_Malloc(size_t size) {
    if (size > (size_t)PY_SSIZE_T_MAX) {
        return NULL;
    }
	mem_head_t* ptr_with_head = malloc(size + sizeof(mem_head_t));
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
    alloc_upcall(ptr, size);
    return ptr;
}

void* PyMem_RawMalloc(size_t size) {
	mem_head_t* ptr_with_head = malloc((size == 0 ? 1 : size) + sizeof(mem_head_t));
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
    alloc_upcall(ptr, size);
    return ptr;
}

void* PyMem_RawCalloc(size_t nelem, size_t elsize) {
    size_t n = (nelem == 0 || elsize == 0) ? 1 : nelem;
    size_t total = n * elsize + sizeof(mem_head_t);
	mem_head_t* ptr_with_head = (mem_head_t*) malloc(total);
	memset(ptr_with_head, 0, total);
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
    alloc_upcall(ptr, n * elsize);
    return ptr;
}

void* PyMem_RawRealloc(void *ptr, size_t new_size) {
	mem_head_t* old = ptr != NULL ? AS_MEM_HEAD(ptr) : NULL;
    mem_head_t* ptr_with_head = (mem_head_t*) realloc(old, new_size + sizeof(mem_head_t));
    ptr_with_head->size = new_size;
    return FROM_MEM_HEAD(ptr_with_head);
}

void PyMem_RawFree(void *ptr) {
	_PyObject_Free(ptr);
}

void * PyMem_Realloc(void *ptr, size_t new_size) {
    return PyMem_RawRealloc(ptr, new_size);
}

void PyMem_Free(void *ptr) {
	_PyObject_Free(ptr);
}

typedef int (*track_fun_t)(int64_t, int64_t, uint64_t);
UPCALL_TYPED_ID(PyTruffle_TraceMalloc_Track, track_fun_t);
int PyTraceMalloc_Track(unsigned int domain, uintptr_t ptr, size_t size) {
	// 0 = success, -2 = disabled
	return _jls_PyTruffle_TraceMalloc_Track(domain, ptr, size);
}


typedef int (*untrack_fun_t)(int64_t, int64_t);
UPCALL_TYPED_ID(PyTruffle_TraceMalloc_Untrack, untrack_fun_t);
int PyTraceMalloc_Untrack(unsigned int domain, uintptr_t ptr) {
	// 0 = success, -2 = disabled
	return _jls_PyTruffle_TraceMalloc_Untrack(domain, ptr);
}


/* If the object memory block is already traced, update its trace
   with the current Python traceback.

   Do nothing if tracemalloc is not tracing memory allocations
   or if the object memory block is not already traced. */
typedef int (*newref_fun_t)(PyObject*);
UPCALL_TYPED_ID(PyTruffle_TraceMalloc_NewReference, newref_fun_t);
int _PyTraceMalloc_NewReference(PyObject *op) {
	// 0 = success, -1 = not tracing
	return _jls_PyTruffle_TraceMalloc_NewReference(op);
}

/* */

PyObject *
_PyObject_GC_Malloc(size_t basicsize)
{
    return PyObject_Malloc(basicsize);
}

PyObject *
_PyObject_GC_Calloc(size_t basicsize)
{
    return PyMem_RawCalloc(1, basicsize);
}
