/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * HPy native cache implementation.
 * Currently, the native cache is an array of uint64_t values.
 * Layout:
 * -----------------------------------------------------------------------
 * | n | hptr_0 | hptr_1 | ... | hptr_n | gptr_0 | gptr_1 | ... | gptr_m |
 * -----------------------------------------------------------------------
 * 'n' is the current size of the handle table.
 * 'hptr_0' is the native data pointer of the object associated with HPy handle 0.
 * 'gptr_0' is the native data pointer of the object associated with HPy global 0.
 */


#ifndef SRC_HPY_NATIVE_CACHE_H_
#define SRC_HPY_NATIVE_CACHE_H_

#include "hpy_native_fast_paths.h"
#include "hpy_log.h"

#define HANDLE_MIRROR_OFFSET 1
#define HANDLE_DATAPTR_INDEX(bits) (HANDLE_MIRROR_OFFSET + unboxHandle(bits))
#define GLOBAL_DATAPTR_INDEX(n_ht, bits) (HANDLE_MIRROR_OFFSET + n_ht + unboxHandle(bits))
#define HANDLE_TABLE_SIZE(cache_ptr) (((uint64_t *)cache_ptr)[0])

/*
 * Get the native data pointer of an object denoted by a handle from the native
 * cache.
 */
static inline void *
get_handle_native_data_pointer(HPyContext *ctx, uint64_t bits) {
    void** space = (void**)ctx->_private;
    return space[HANDLE_DATAPTR_INDEX(bits)];
}


/*
 * Get the native data pointer of an object denoted by an HPyGlobal from the
 * native cache.
 */
static inline void *
get_global_native_data_pointer(HPyContext *ctx, uint64_t bits) {
    void** space = (void**)ctx->_private;
    return space[GLOBAL_DATAPTR_INDEX(HANDLE_TABLE_SIZE(space), bits)];
}

/*
 * Load the native data pointer of an object denoted by an HPyGlobal into the
 * native cache.
 */
static inline void
load_global_native_data_pointer(HPyContext *ctx, uint64_t g_bits, uint64_t h_bits) {
    void** space = (void**)ctx->_private;
    uint64_t n_handle_table = (uint64_t)space[0];
    void *g_data_ptr = space[GLOBAL_DATAPTR_INDEX(n_handle_table, g_bits)];
    LOG("%llu %llu %p", (unsigned long long)g_bits, (unsigned long long)h_bits, g_data_ptr)
    space[HANDLE_DATAPTR_INDEX(h_bits)] = g_data_ptr;
}

#endif /* SRC_HPY_NATIVE_CACHE_H_ */
