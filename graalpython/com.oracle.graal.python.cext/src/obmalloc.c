/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "pycore_pymem.h"

struct _PyTraceMalloc_Config _Py_tracemalloc_config = {
  .initialized = 0,
  .tracing = 0,
  .max_nframe = 1
};

/*
 * This header needs to be 16 bytes long to ensure that allocations will still be aligned to 16 byte boundaries.
 * (necessary for aligned vector instruction access)
 */
typedef struct {
	size_t size;
	size_t dummy;
} mem_head_t;

/* Get an object's GC head */
#define AS_MEM_HEAD(o) ((mem_head_t *)(o)-1)

/* Get the object given the GC head */
#define FROM_MEM_HEAD(g) ((void *)(((mem_head_t *)g)+1))

size_t PyTruffle_AllocatedMemory = 0;
size_t PyTruffle_MaxNativeMemory = 0;
size_t PyTruffle_NativeMemoryGCBarrier = 0;
#define MAX_COLLECTION_RETRIES (7)
#define COLLECTION_DELAY_INCREMENT (50)

void PyTruffle_InitializeNativeMemorySize() {
	PyTruffle_AllocatedMemory = 0;
	PyTruffle_MaxNativeMemory = GraalPyTruffle_GetMaxNativeMemory();
	PyTruffle_NativeMemoryGCBarrier = GraalPyTruffle_GetInitialNativeMemory();
}

int PyTruffle_AllocMemory(size_t size) {
    // memory management
	while ((PyTruffle_AllocatedMemory + size) > PyTruffle_NativeMemoryGCBarrier) {
		if (PyTruffle_MaxNativeMemory == 0) {
			PyTruffle_InitializeNativeMemorySize();
			continue;
		}
	    PyTruffle_Log(PY_TRUFFLE_LOG_CONFIG, "PyTruffle_AllocMemory: exceeding PyTruffle_NativeMemoryGCBarrier (%lu) with allocation of size %lu, current PyTruffle_AllocatedMemory: %lu\n", PyTruffle_NativeMemoryGCBarrier, size, PyTruffle_AllocatedMemory);

	    size_t delay = 0;
	    int iteration = 0;
	    for (int iteration = 0; iteration < MAX_COLLECTION_RETRIES; iteration++) {
	    	GraalPyTruffle_TriggerGC(delay);
	    	delay += COLLECTION_DELAY_INCREMENT;
	    	if ((PyTruffle_AllocatedMemory + size) <= PyTruffle_NativeMemoryGCBarrier) {
	    		PyTruffle_AllocatedMemory += size;
	    		return 0;
	    	}
	    }
		if (PyTruffle_NativeMemoryGCBarrier < PyTruffle_MaxNativeMemory) {
			PyTruffle_NativeMemoryGCBarrier *= 2;
			if (PyTruffle_NativeMemoryGCBarrier > PyTruffle_MaxNativeMemory) {
				PyTruffle_NativeMemoryGCBarrier = PyTruffle_MaxNativeMemory;
			}
			PyTruffle_Log(PY_TRUFFLE_LOG_CONFIG, "PyTruffle_AllocMemory: enlarging PyTruffle_NativeMemoryGCBarrier to %lu\n", PyTruffle_NativeMemoryGCBarrier);
		} else {
			PyTruffle_Log(PY_TRUFFLE_LOG_INFO, "PyTruffle_AllocMemory: native memory exhausted while allocating %lu bytes\n", size);
			return 1;
		}
	}
	PyTruffle_AllocatedMemory += size;
	return 0;
}

void PyTruffle_FreeMemory(size_t size) {
    if (PyTruffle_AllocatedMemory < size) {
        PyTruffle_Log(PY_TRUFFLE_LOG_INFO, "PyTruffle_FreeMemory: freed memory size (%lu) is larger than allocated memory size (%lu)\n", size, PyTruffle_AllocMemory);
        PyTruffle_AllocatedMemory = size;
    }
    PyTruffle_AllocatedMemory -= size;
}

/* This is our version of 'PyObject_Free' which is also able to free Sulong handles. */
MUST_INLINE static
void _PyObject_Free(void* ptr) {
	if (ptr == NULL) {
		return;
	}
	if(points_to_py_handle_space(ptr)) {
	    GraalPyTruffle_Object_Free(ptr);
	} else {
        mem_head_t* ptr_with_head = AS_MEM_HEAD(ptr);
        PyTruffle_FreeMemory(ptr_with_head->size);
        free(ptr_with_head);
	}
}

void* PyObject_Malloc(size_t size) {
	if (PyTruffle_AllocMemory(size)) {
		return NULL;
	}
	mem_head_t* ptr_with_head = malloc(size + sizeof(mem_head_t));
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
    return ptr;
}

void PyObject_Free(void* ptr) {
	_PyObject_Free(ptr);
}

void* PyObject_Realloc(void *ptr, size_t new_size) {
	return PyMem_RawRealloc(ptr, new_size);
}

void* PyMem_Malloc(size_t size) {
    if (size > (size_t)PY_SSIZE_T_MAX) {
        return NULL;
    }
	if (PyTruffle_AllocMemory(size)) {
		return NULL;
	}
	mem_head_t* ptr_with_head = malloc(size + sizeof(mem_head_t));
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
    return ptr;
}

void* PyMem_Calloc(size_t nelem, size_t elsize) {
    if (elsize != 0 && nelem > (size_t)PY_SSIZE_T_MAX / elsize) {
        return NULL;
    }
    return PyMem_RawCalloc(nelem, elsize);
}

void* PyMem_RawMalloc(size_t size) {
	if (PyTruffle_AllocMemory(size)) {
		return NULL;
	}
	mem_head_t* ptr_with_head = malloc((size == 0 ? 1 : size) + sizeof(mem_head_t));
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
	ptr_with_head->size = size;
    return ptr;
}

void* PyMem_RawCalloc(size_t nelem, size_t elsize) {
    size_t n = (nelem == 0 || elsize == 0) ? 1 : nelem;
	if (PyTruffle_AllocMemory(n * elsize)) {
		return NULL;
	}
    size_t total = n * elsize + sizeof(mem_head_t);
	mem_head_t* ptr_with_head = (mem_head_t*) malloc(total);
	memset(ptr_with_head, 0, total);
	void* ptr = FROM_MEM_HEAD(ptr_with_head);
    return ptr;
}

void* PyMem_RawRealloc(void *ptr, size_t new_size) {
	mem_head_t* old;
	size_t old_size;

	if (ptr != NULL) {
		old = AS_MEM_HEAD(ptr);
		old_size = old->size;
	} else {
		old = NULL;
		old_size = 0;
	}

    // account for the difference in size
    if (old_size >= new_size) {
        PyTruffle_FreeMemory(old_size - new_size);
    } else {
        if (PyTruffle_AllocMemory(new_size - old_size)) {
            return NULL;
        }
    }

    mem_head_t* ptr_with_head = (mem_head_t*) realloc(old, new_size + sizeof(mem_head_t));
    ptr_with_head->size = new_size;
    return FROM_MEM_HEAD(ptr_with_head);
}

void * PyMem_Realloc(void *ptr, size_t new_size) {
    return PyMem_RawRealloc(ptr, new_size);
}

void PyMem_RawFree(void *ptr) {
	_PyObject_Free(ptr);
}

void PyMem_Free(void *ptr) {
	_PyObject_Free(ptr);
}
