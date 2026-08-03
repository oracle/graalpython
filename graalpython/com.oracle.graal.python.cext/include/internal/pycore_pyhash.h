/* Copyright (c) 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2026 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_INTERNAL_PYHASH_H
#define Py_INTERNAL_PYHASH_H

#ifndef Py_BUILD_CORE
#  error "this header requires Py_BUILD_CORE define"
#endif

static inline Py_hash_t
_Py_HashPointerRaw(const void *ptr)
{
    uintptr_t x = (uintptr_t)ptr;
    Py_BUILD_ASSERT(sizeof(x) == sizeof(ptr));

    x = (x >> 4) | (x << (8 * sizeof(uintptr_t) - 4));

    Py_BUILD_ASSERT(sizeof(x) == sizeof(Py_hash_t));
    return (Py_hash_t)x;
}

PyAPI_FUNC(Py_hash_t) _Py_HashBytes(const void*, Py_ssize_t);

typedef union {
    unsigned char uc[24];
    struct {
        Py_hash_t prefix;
        Py_hash_t suffix;
    } fnv;
    struct {
        uint64_t k0;
        uint64_t k1;
    } siphash;
    struct {
        unsigned char padding[16];
        Py_hash_t suffix;
    } djbx33a;
    struct {
        uint8_t hashsalt16[16];
        Py_hash_t hashsalt;
    } expat;
} _Py_HashSecret_t;

PyAPI_DATA(_Py_HashSecret_t) _Py_HashSecret;

#ifdef Py_DEBUG
extern int _Py_HashSecret_Initialized;
#endif


struct pyhash_runtime_state {
    struct {
#ifndef MS_WINDOWS
        int fd;
        dev_t st_dev;
        ino_t st_ino;
#else
        int _not_used;
#endif
    } urandom_cache;
};

#ifndef MS_WINDOWS
# define _py_urandom_cache_INIT \
    { \
        .fd = -1, \
    }
#else
# define _py_urandom_cache_INIT {0}
#endif

#define pyhash_state_INIT \
    { \
        .urandom_cache = _py_urandom_cache_INIT, \
    }


extern uint64_t _Py_KeyedHash(uint64_t key, const void *src, Py_ssize_t src_sz);

#endif  // !Py_INTERNAL_PYHASH_H
