/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This header needs to be 16 bytes long to ensure that allocations will still be aligned to 16 byte boundaries.
 * (necessary for aligned vector instruction access)
 */
typedef struct {
	size_t size;
	size_t dummy;
} mem_head_t;

typedef struct {
    void *ptr;
    void *stack[12];
    size_t size;
    size_t depth;
    unsigned long long serial;
    char operation;
} GraalPyMemSample_t;

/* Get an object's GC head */
#define AS_MEM_HEAD(o) ((mem_head_t *)(o)-1)

/* Get the object given the GC head */
#define FROM_MEM_HEAD(g) ((void *)(((mem_head_t *)g)+1))

#define GRAALPY_MEM_HEAD_MAGIC ((size_t)0x47505241574D454DULL)
#define GRAALPY_MEM_HEAD_POISON ((size_t)0xDDDDBAD0DDDDBAD0ULL)
#define GRAALPY_MEM_SAMPLE_RING_SIZE (4096)
#define GRAALPY_MEM_SAMPLE_HISTORY (8)
#define GRAALPY_MEM_SAMPLE_STACK_SKIP (2)
#define GRAALPY_MEM_SAMPLE_USEFUL_DEPTH (10)
#define MAX_COLLECTION_RETRIES (7)
#define COLLECTION_DELAY_INCREMENT (50)

/* Forward declaration */
static void* _GraalPyMem_RawMalloc(void *ctx, size_t size);
static void* _GraalPyMem_RawCalloc(void *ctx, size_t nelem, size_t elsize);
static void _GraalPyMem_RawFree(void *ctx, void *p);
static void* _GraalPyMem_RawRealloc(void *ctx, void *ptr, size_t size);

typedef struct {
    size_t allocated_memory;
    size_t max_native_memory;
    size_t native_memory_gc_barrier;
} GraalPyMem_t;

static GraalPyMem_t _GraalPyMem_State = { 0, 0, 0 };
static GraalPyMemSample_t _GraalPyMem_Samples[GRAALPY_MEM_SAMPLE_RING_SIZE] = {{0}};
static unsigned long long _GraalPyMem_SampleSerial = 0;
static size_t _GraalPyMem_SampleIndex = 0;

static MUST_INLINE int
_GraalPyMem_PoisonOnFreeEnabled(void)
{
    return GraalPyPrivate_PoisonNativeMemoryOnFree();
}

static MUST_INLINE int
_GraalPyMem_SampleAllocSitesEnabled(void)
{
    return GraalPyPrivate_SampleNativeMemoryAllocSites();
}

static void
_GraalPyMem_CaptureSampleStack(GraalPyMemSample_t *sample)
{
    sample->depth = GraalPyPrivate_CaptureStacktrace(sample->stack, GRAALPY_MEM_SAMPLE_USEFUL_DEPTH,
                    GRAALPY_MEM_SAMPLE_STACK_SKIP);
}

static void
_GraalPyMem_RecordSample(char operation, void *ptr, size_t size)
{
    if (UNLIKELY(ptr == NULL)) {
        return;
    }
    if (LIKELY(!_GraalPyMem_SampleAllocSitesEnabled())) {
        return;
    }

    size_t index = _GraalPyMem_SampleIndex++ % GRAALPY_MEM_SAMPLE_RING_SIZE;
    GraalPyMemSample_t *sample = &_GraalPyMem_Samples[index];
    sample->ptr = ptr;
    sample->size = size;
    sample->serial = ++_GraalPyMem_SampleSerial;
    sample->operation = operation;
    _GraalPyMem_CaptureSampleStack(sample);
}

static void
_GraalPyMem_LogRecentSamples(const char *func, void *ptr)
{
    if (LIKELY(!_GraalPyMem_SampleAllocSitesEnabled())) {
        return;
    }

    size_t next_index = _GraalPyMem_SampleIndex;
    int printed = 0;
    for (size_t offset = 0; offset < GRAALPY_MEM_SAMPLE_RING_SIZE && printed < GRAALPY_MEM_SAMPLE_HISTORY; offset++) {
        size_t index = (next_index + GRAALPY_MEM_SAMPLE_RING_SIZE - offset - 1) % GRAALPY_MEM_SAMPLE_RING_SIZE;
        const GraalPyMemSample_t *sample = &_GraalPyMem_Samples[index];
        if (sample->ptr == ptr && sample->serial != 0) {
            char prefix[128];
            GraalPyPrivate_Log(PY_TRUFFLE_LOG_INFO,
                    "%s: recent raw memory sample #%llu op=%c ptr=%p size=%lu depth=%lu\n",
                    func, sample->serial, sample->operation, sample->ptr, (unsigned long) sample->size, (unsigned long) sample->depth);
            snprintf(prefix, sizeof(prefix), "%s: sample #%llu ", func, sample->serial);
            GraalPyPrivate_LogCapturedStacktrace(PY_TRUFFLE_LOG_INFO, prefix, sample->stack, sample->depth);
            printed++;
        }
    }
}

static void
_GraalPyMem_InitHeader(mem_head_t *ptr_with_head, size_t size)
{
    ptr_with_head->size = size;
    ptr_with_head->dummy = GRAALPY_MEM_HEAD_MAGIC;
}

static void
_GraalPyMem_PoisonBlock(mem_head_t *ptr_with_head, size_t size)
{
    if (LIKELY(!_GraalPyMem_PoisonOnFreeEnabled())) {
        return;
    }

    memset(ptr_with_head, 0xDB, sizeof(mem_head_t) + size);
    ptr_with_head->size = GRAALPY_MEM_HEAD_POISON;
    ptr_with_head->dummy = GRAALPY_MEM_HEAD_POISON;
}

static void
_GraalPyMem_FatalInvalidHeader(const char *func, void *ptr, const mem_head_t *ptr_with_head)
{
    const char *reason = (ptr_with_head->size == GRAALPY_MEM_HEAD_POISON && ptr_with_head->dummy == GRAALPY_MEM_HEAD_POISON)
            ? "poisoned raw allocation header"
            : "invalid raw allocation header";
    GraalPyPrivate_Log(PY_TRUFFLE_LOG_INFO,
            "%s: %s for ptr=%p head=%p size=%lu dummy=0x%lx\n",
            func, reason, ptr, ptr_with_head, (unsigned long) ptr_with_head->size, (unsigned long) ptr_with_head->dummy);
    _GraalPyMem_LogRecentSamples(func, ptr);
    Py_FatalError("invalid GraalPy raw allocation header");
}

static mem_head_t *
_GraalPyMem_GetValidatedHead(const char *func, void *ptr)
{
    mem_head_t *ptr_with_head = AS_MEM_HEAD(ptr);
    if (UNLIKELY(ptr_with_head->dummy != GRAALPY_MEM_HEAD_MAGIC)) {
        _GraalPyMem_FatalInvalidHeader(func, ptr, ptr_with_head);
    }
    return ptr_with_head;
}

#if 0 // GraalPy change
/* bpo-35053: Declare tracemalloc configuration here rather than
   Modules/_tracemalloc.c because _tracemalloc can be compiled as dynamic
   library, whereas _Py_NewReference() requires it. */
struct _PyTraceMalloc_Config _Py_tracemalloc_config = _PyTraceMalloc_Config_INIT;
#endif // GraalPy change


static void *
_PyMem_RawMalloc(void *ctx, size_t size)
{
    /* PyMem_RawMalloc(0) means malloc(1). Some systems would return NULL
       for malloc(0), which would be treated as an error. Some platforms would
       return a pointer with no memory behind it, which would break pymalloc.
       To solve these problems, allocate an extra byte. */
    if (size == 0)
        size = 1;
    return malloc(size);
}

static void *
_PyMem_RawCalloc(void *ctx, size_t nelem, size_t elsize)
{
    /* PyMem_RawCalloc(0, 0) means calloc(1, 1). Some systems would return NULL
       for calloc(0, 0), which would be treated as an error. Some platforms
       would return a pointer with no memory behind it, which would break
       pymalloc.  To solve these problems, allocate an extra byte. */
    if (nelem == 0 || elsize == 0) {
        nelem = 1;
        elsize = 1;
    }
    return calloc(nelem, elsize);
}

static void *
_PyMem_RawRealloc(void *ctx, void *ptr, size_t size)
{
    if (size == 0)
        size = 1;
    return realloc(ptr, size);
}

static void
_PyMem_RawFree(void *ctx, void *ptr)
{
    free(ptr);
}

#if 0 // GraalPy change
#ifdef MS_WINDOWS
static void *
_PyObject_ArenaVirtualAlloc(void *ctx, size_t size)
{
    return VirtualAlloc(NULL, size,
                        MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
}

static void
_PyObject_ArenaVirtualFree(void *ctx, void *ptr, size_t size)
{
    VirtualFree(ptr, 0, MEM_RELEASE);
}

#elif defined(ARENAS_USE_MMAP)
static void *
_PyObject_ArenaMmap(void *ctx, size_t size)
{
    void *ptr;
    ptr = mmap(NULL, size, PROT_READ|PROT_WRITE,
               MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    if (ptr == MAP_FAILED)
        return NULL;
    assert(ptr != NULL);
    return ptr;
}

static void
_PyObject_ArenaMunmap(void *ctx, void *ptr, size_t size)
{
    munmap(ptr, size);
}

#else
static void *
_PyObject_ArenaMalloc(void *ctx, size_t size)
{
    return malloc(size);
}

static void
_PyObject_ArenaFree(void *ctx, void *ptr, size_t size)
{
    free(ptr);
}
#endif
#endif // GraalPy change

#define GRAAL_ALLOC {&_GraalPyMem_State, _GraalPyMem_RawMalloc, _GraalPyMem_RawCalloc, _GraalPyMem_RawRealloc, _GraalPyMem_RawFree}
#define MALLOC_ALLOC {NULL, _PyMem_RawMalloc, _PyMem_RawCalloc, _PyMem_RawRealloc, _PyMem_RawFree}
#ifdef WITH_PYMALLOC
#  define PYMALLOC_ALLOC {NULL, _PyObject_Malloc, _PyObject_Calloc, _PyObject_Realloc, _PyObject_Free}
#endif

#define PYRAW_ALLOC GRAAL_ALLOC // GraalPy change
#ifdef WITH_PYMALLOC
#  define PYOBJ_ALLOC PYMALLOC_ALLOC
#else
#  define PYOBJ_ALLOC GRAAL_ALLOC // GraalPy change
#endif
#define PYMEM_ALLOC PYOBJ_ALLOC

#if 0 // GraalPy change
typedef struct {
    /* We tag each block with an API ID in order to tag API violations */
    char api_id;
    PyMemAllocatorEx alloc;
} debug_alloc_api_t;
static struct {
    debug_alloc_api_t raw;
    debug_alloc_api_t mem;
    debug_alloc_api_t obj;
} _PyMem_Debug = {
    {'r', PYRAW_ALLOC},
    {'m', PYMEM_ALLOC},
    {'o', PYOBJ_ALLOC}
    };

#define PYDBGRAW_ALLOC \
    {&_PyMem_Debug.raw, _PyMem_DebugRawMalloc, _PyMem_DebugRawCalloc, _PyMem_DebugRawRealloc, _PyMem_DebugRawFree}
#define PYDBGMEM_ALLOC \
    {&_PyMem_Debug.mem, _PyMem_DebugMalloc, _PyMem_DebugCalloc, _PyMem_DebugRealloc, _PyMem_DebugFree}
#define PYDBGOBJ_ALLOC \
    {&_PyMem_Debug.obj, _PyMem_DebugMalloc, _PyMem_DebugCalloc, _PyMem_DebugRealloc, _PyMem_DebugFree}
#endif // GraalPy change

#ifdef Py_DEBUG
static PyMemAllocatorEx _PyMem_Raw = PYDBGRAW_ALLOC;
static PyMemAllocatorEx _PyMem = PYDBGMEM_ALLOC;
static PyMemAllocatorEx _PyObject = PYDBGOBJ_ALLOC;
#else
static PyMemAllocatorEx _PyMem_Raw = PYRAW_ALLOC;
static PyMemAllocatorEx _PyMem = PYMEM_ALLOC;
static PyMemAllocatorEx _PyObject = PYOBJ_ALLOC;
#endif

void *
PyObject_Malloc(size_t size)
{
    /* see PyMem_RawMalloc() */
    if (size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
#if 0 // GraalPy change
    OBJECT_STAT_INC_COND(allocations512, size < 512);
    OBJECT_STAT_INC_COND(allocations4k, size >= 512 && size < 4094);
    OBJECT_STAT_INC_COND(allocations_big, size >= 4094);
    OBJECT_STAT_INC(allocations);
#endif // GraalPy change
    return _PyObject.malloc(_PyObject.ctx, size);
}

void *
PyObject_Calloc(size_t nelem, size_t elsize)
{
    /* see PyMem_RawMalloc() */
    if (elsize != 0 && nelem > (size_t)PY_SSIZE_T_MAX / elsize)
        return NULL;
#if 0 // GraalPy change
    OBJECT_STAT_INC_COND(allocations512, elsize < 512);
    OBJECT_STAT_INC_COND(allocations4k, elsize >= 512 && elsize < 4094);
    OBJECT_STAT_INC_COND(allocations_big, elsize >= 4094);
    OBJECT_STAT_INC(allocations);
#endif // GraalPy change
    return _PyObject.calloc(_PyObject.ctx, nelem, elsize);
}

void *
PyObject_Realloc(void *ptr, size_t new_size)
{
    /* see PyMem_RawMalloc() */
    if (new_size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
    return _PyObject.realloc(_PyObject.ctx, ptr, new_size);
}

void
PyObject_Free(void *ptr)
{
#if 0 // GraalPy change
    OBJECT_STAT_INC(frees);
#endif // GraalPy change
    _PyObject.free(_PyObject.ctx, ptr);
}

void *
PyMem_Malloc(size_t size)
{
    /* see PyMem_RawMalloc() */
    if (size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
#if 0 // GraalPy change
    OBJECT_STAT_INC_COND(allocations512, size < 512);
    OBJECT_STAT_INC_COND(allocations4k, size >= 512 && size < 4094);
    OBJECT_STAT_INC_COND(allocations_big, size >= 4094);
    OBJECT_STAT_INC(allocations);
#endif // GraalPy change
    return _PyMem.malloc(_PyMem.ctx, size);
}

void *
PyMem_Calloc(size_t nelem, size_t elsize)
{
    /* see PyMem_RawMalloc() */
    if (elsize != 0 && nelem > (size_t)PY_SSIZE_T_MAX / elsize)
        return NULL;
#if 0 // GraalPy change
    OBJECT_STAT_INC_COND(allocations512, elsize < 512);
    OBJECT_STAT_INC_COND(allocations4k, elsize >= 512 && elsize < 4094);
    OBJECT_STAT_INC_COND(allocations_big, elsize >= 4094);
    OBJECT_STAT_INC(allocations);
#endif // GraalPy change
    return _PyMem.calloc(_PyMem.ctx, nelem, elsize);
}

void *
PyMem_Realloc(void *ptr, size_t new_size)
{
    /* see PyMem_RawMalloc() */
    if (new_size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
    return _PyMem.realloc(_PyMem.ctx, ptr, new_size);
}

void
PyMem_Free(void *ptr)
{
#if 0 // GraalPy change
    OBJECT_STAT_INC(frees);
#endif // GraalPy change
    _PyMem.free(_PyMem.ctx, ptr);
}

void *
PyMem_RawMalloc(size_t size)
{
    /*
     * Limit ourselves to PY_SSIZE_T_MAX bytes to prevent security holes.
     * Most python internals blindly use a signed Py_ssize_t to track
     * things without checking for overflows or negatives.
     * As size_t is unsigned, checking for size < 0 is not required.
     */
    if (size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
    void *ptr = _PyMem_Raw.malloc(_PyMem_Raw.ctx, size);
    _GraalPyMem_RecordSample('m', ptr, size == 0 ? 1 : size);
    return ptr;
}

void *
PyMem_RawCalloc(size_t nelem, size_t elsize)
{
    /* see PyMem_RawMalloc() */
    if (elsize != 0 && nelem > (size_t)PY_SSIZE_T_MAX / elsize)
        return NULL;
    void *ptr = _PyMem_Raw.calloc(_PyMem_Raw.ctx, nelem, elsize);
    size_t nbytes = (nelem == 0 || elsize == 0) ? 1 : nelem * elsize;
    _GraalPyMem_RecordSample('c', ptr, nbytes);
    return ptr;
}

void*
PyMem_RawRealloc(void *ptr, size_t new_size)
{
    /* see PyMem_RawMalloc() */
    if (new_size > (size_t)PY_SSIZE_T_MAX)
        return NULL;
    void *new_ptr = _PyMem_Raw.realloc(_PyMem_Raw.ctx, ptr, new_size);
    _GraalPyMem_RecordSample('r', new_ptr, new_size == 0 ? 1 : new_size);
    return new_ptr;
}

void PyMem_RawFree(void *ptr)
{
    _GraalPyMem_RecordSample('f', ptr, 0);
    _PyMem_Raw.free(_PyMem_Raw.ctx, ptr);
}

/*============================================================================*/
/* GraalPy off-heap memory allocator.

   When C extensions use Python's memory management API to allocate native
   memory for objects, GraalPy's GC does not see those allocations because
   native memory is not movable and therefore in an off-heap region.
   This is problematic if an application allocates only few objects that have
   large amounts of off-heap memory attached. In such cases, the GC does not
   see any memory pressure and may therefore not collect the objects with
   associated native memory.

   We therefore track off-heap allocations and count the allocated bytes. In
   order to correctly account for free'd memory, we prepend a header that stores
   the allocated size. The layout looks as follows:

   |       header       | payload |
   | sizeof(mem_head_t) |  size   |
*/

static int
_GraalPyMem_PrepareAlloc(GraalPyMem_t *state, size_t size)
{
    // memory management
    while ((state->allocated_memory + size) > state->native_memory_gc_barrier) {
        if (state->max_native_memory == 0) {
            state->allocated_memory = 0;
            state->max_native_memory = GraalPyPrivate_GetMaxNativeMemory();
            state->native_memory_gc_barrier = GraalPyPrivate_GetInitialNativeMemory();
            continue;
        }
        GraalPyPrivate_Log(PY_TRUFFLE_LOG_CONFIG,
                "%s: exceeding native_memory_gc_barrier (%lu) with allocation of size %lu, current allocated_memory: %lu\n",
                __func__, state->native_memory_gc_barrier, size, state->allocated_memory);

        size_t delay = 0;
        for (int iteration = 0; iteration < MAX_COLLECTION_RETRIES;
                iteration++) {
            GraalPyPrivate_TriggerGC(delay);
            delay += COLLECTION_DELAY_INCREMENT;
            if ((state->allocated_memory + size)
                    <= state->native_memory_gc_barrier) {
                state->allocated_memory += size;
                return 0;
            }
        }
        if (state->native_memory_gc_barrier < state->max_native_memory) {
            state->native_memory_gc_barrier *= 2;
            if (state->native_memory_gc_barrier > state->max_native_memory) {
                state->native_memory_gc_barrier = state->max_native_memory;
            }
            GraalPyPrivate_Log(PY_TRUFFLE_LOG_CONFIG,
                    "%s: enlarging native_memory_gc_barrier to %lu\n",
                    __func__, state->native_memory_gc_barrier);
        }
        else {
            GraalPyPrivate_Log(PY_TRUFFLE_LOG_INFO,
                    "%s: native memory exhausted while allocating %lu bytes\n",
                    __func__, size);
            return 1;
        }
    }
    state->allocated_memory += size;
    return 0;
}

static void *
_GraalPyMem_RawMalloc(void *ctx, size_t size)
{
    assert(ctx != NULL);
    /* PyMem_RawMalloc(0) means malloc(1). Some systems would return NULL
       for malloc(0), which would be treated as an error. Some platforms would
       return a pointer with no memory behind it, which would break pymalloc.
       To solve these problems, allocate an extra byte. */
    if (size == 0)
        size = 1;
    GraalPyMem_t *state = (GraalPyMem_t *)ctx;
    if (_GraalPyMem_PrepareAlloc(state, size)) {
        return NULL;
    }
    mem_head_t *ptr_with_head = (mem_head_t *)malloc(size + sizeof(mem_head_t));
    if (ptr_with_head == NULL) {
        state->allocated_memory -= size;
        return NULL;
    }
    _GraalPyMem_InitHeader(ptr_with_head, size);
    return FROM_MEM_HEAD(ptr_with_head);
}

static void *
_GraalPyMem_RawCalloc(void *ctx, size_t nelem, size_t elsize)
{
    assert(ctx != NULL);
    /* Ensure that the multiplication 'nelem * elsize' does not overflow. */
    if (elsize != 0 && nelem > (size_t)PY_SSIZE_T_MAX / elsize)
        return NULL;
    /* PyMem_RawCalloc(0, 0) means calloc(1, 1). Some systems would return NULL
       for calloc(0, 0), which would be treated as an error. Some platforms
       would return a pointer with no memory behind it, which would break
       pymalloc.  To solve these problems, allocate an extra byte. */
    if (nelem == 0 || elsize == 0) {
        nelem = 1;
        elsize = 1;
    }
    size_t nbytes = nelem * elsize;
    GraalPyMem_t *state = (GraalPyMem_t *)ctx;
    if (_GraalPyMem_PrepareAlloc(state, nbytes)) {
        return NULL;
    }
    /* We cannot use 'calloc' because we need to allocate following layout:
       [ mem_head_t ] [ e_0 ] [ e_1 ]  [ e_2 ] ... [ n_nelem ] */
    size_t total = nbytes + sizeof(mem_head_t);
    mem_head_t *ptr_with_head = (mem_head_t *)malloc(total);
    if (ptr_with_head == NULL) {
        state->allocated_memory -= nbytes;
        return NULL;
    }
    memset(ptr_with_head, 0, total);
    _GraalPyMem_InitHeader(ptr_with_head, nbytes);
    return FROM_MEM_HEAD(ptr_with_head);
}

static void*
_GraalPyMem_RawRealloc(void *ctx, void *ptr, size_t size)
{
    assert(ctx != NULL);
    assert(!points_to_py_handle_space(ptr));
    GraalPyMem_t *state = (GraalPyMem_t*) ctx;
    mem_head_t *old;
    size_t old_size;
    if (size == 0)
        size = 1;

    if (ptr != NULL) {
        old = _GraalPyMem_GetValidatedHead(__func__, ptr);
        old_size = old->size;
    } else {
        old = NULL;
        old_size = 0;
    }

    if (old_size < size && _GraalPyMem_PrepareAlloc(state, size - old_size)) {
        return NULL;
    }

    mem_head_t *ptr_with_head = (mem_head_t *)realloc(old,
            size + sizeof(mem_head_t));
    if (ptr_with_head == NULL) {
        if (old_size < size) {
            state->allocated_memory -= size - old_size;
        }
        return NULL;
    }

    if (old_size > size) {
        /* In case of "shrinking", just subtract the difference but don't
           trigger the Java GC. */
        state->allocated_memory -= old_size - size;
    }

    _GraalPyMem_InitHeader(ptr_with_head, size);
    return FROM_MEM_HEAD(ptr_with_head);
}

static void
_GraalPyMem_RawFree(void *ctx, void *ptr)
{
    assert(ctx != NULL);
    assert(!points_to_py_handle_space(ptr));
    /* Free is sometimes called on NULL pointers which is valid because libc's
     * 'free' would just do nothing. */
    if (ptr == NULL)
        return;
    GraalPyMem_t *state = (GraalPyMem_t *)ctx;
    mem_head_t *ptr_with_head = _GraalPyMem_GetValidatedHead(__func__, ptr);
    const size_t size = ptr_with_head->size;
    if (state->allocated_memory < size) {
        GraalPyPrivate_Log(PY_TRUFFLE_LOG_INFO,
                "%s: freed memory size (%lu) is larger than allocated memory size (%lu)\n",
                __func__, size, state->allocated_memory);
        state->allocated_memory = size;
    }
    state->allocated_memory -= size;
    _GraalPyMem_PoisonBlock(ptr_with_head, size);
    free(ptr_with_head);
}
