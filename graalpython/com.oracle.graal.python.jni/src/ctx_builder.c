/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Native implementation of HPyTupleBuilder.
 * This is written in a way that we could also use the internal functions
 * 'builder_*' to implement HPyListBuilder.
 */


#include <stddef.h>
#include "hpy.h"

typedef struct {
    HPy_ssize_t capacity;  // allocated handles
    HPy handles[0];
} _HPyBuilder_s;

static inline _HPyBuilder_s *_ht2hb(HPyTupleBuilder ht) {
    return (_HPyBuilder_s *) (ht)._tup;
}

static inline HPyTupleBuilder _hb2ht(_HPyBuilder_s *hp) {
    return (HPyTupleBuilder) {(HPy_ssize_t) (hp)};
}

static inline _HPyBuilder_s *builder_new(HPy_ssize_t size) {
    _HPyBuilder_s *hb = calloc(1, sizeof(_HPyBuilder_s) + size * sizeof(HPy));
    if (hb == NULL) {
        /* delay the MemoryError */
        /* note: it's done this way so that the caller doesn't need to
           check if HPyTupleBuilder_New() or every HPyTupleBuilder_Set()
           raised.  If there is a rare error like a MemoryError somewhere,
           further calls to the HPyTupleBuilder are ignored.  The final
           HPyTupleBuilder_Build() will re-raise the MemoryError and so
           it's enough for the caller to check at that point. */
    } else {
        hb->capacity = size;
    }
    return hb;
}
static inline void builder_set(HPyContext *ctx, _HPyBuilder_s *hb, HPy_ssize_t index, HPy h_item)
{
    if (hb != NULL) {
        assert(index >= 0 && index < hb->capacity);
        assert(HPy_IsNull(hb->handles[index]));
        hb->handles[index] = HPy_Dup(ctx, h_item);
    }
}

static inline void builder_cancel(HPyContext *ctx, _HPyBuilder_s *hb)
{
    if (hb == NULL) {
        // we don't report the memory error here: the builder
        // is being cancelled (so the result of the builder is not being used)
        // and likely it's being cancelled during the handling of another error
        return;
    }
    for (HPy_ssize_t i = 0; i < hb->capacity; i++) {
        HPy_Close(ctx, hb->handles[i]);
    }
    free(hb);
}

_HPy_HIDDEN HPyTupleBuilder
ctx_TupleBuilder_New(HPyContext *ctx, HPy_ssize_t size)
{
    return _hb2ht(builder_new(size));
}

_HPy_HIDDEN void
ctx_TupleBuilder_Set(HPyContext *ctx, HPyTupleBuilder builder,
                     HPy_ssize_t index, HPy h_item)
{
    builder_set(ctx, _ht2hb(builder), index, h_item);
}

_HPy_HIDDEN HPy
ctx_TupleBuilder_Build(HPyContext *ctx, HPyTupleBuilder builder)
{
    _HPyBuilder_s *hb = _ht2hb(builder);
    if (hb == NULL) {
        HPyErr_NoMemory(ctx);
        return HPy_NULL;
    }
    /* TODO(fa): we could have an internal upcall that steals the references */
    HPy res = HPyTuple_FromArray(ctx, hb->handles, hb->capacity);
    for (HPy_ssize_t i = 0; i < hb->capacity; i++) {
        HPy_Close(ctx, hb->handles[i]);
    }
    free(hb);
    return res;
}

_HPy_HIDDEN void
ctx_TupleBuilder_Cancel(HPyContext *ctx, HPyTupleBuilder builder)
{
    builder_cancel(ctx, _ht2hb(builder));
}
