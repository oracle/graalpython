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
#ifndef HPY_INLINE_HELPERS_H
#define HPY_INLINE_HELPERS_H

#include <assert.h>

#if defined(_MSC_VER)
# include <malloc.h>  /* for alloca() */
#endif

HPyAPI_FUNC HPy HPyErr_SetFromErrno(HPyContext *ctx, HPy h_type)
{
    return HPyErr_SetFromErrnoWithFilenameObjects(ctx, h_type, HPy_NULL, HPy_NULL);
}

HPyAPI_FUNC HPy HPyErr_SetFromErrnoWithFilenameObject(HPyContext *ctx, HPy h_type, HPy filename)
{
    return HPyErr_SetFromErrnoWithFilenameObjects(ctx, h_type, filename, HPy_NULL);
}

HPyAPI_FUNC int HPySlice_AdjustIndices(HPy_ssize_t length, HPy_ssize_t *start, HPy_ssize_t *stop, HPy_ssize_t step) {
    /* Taken from CPython: Written by Jim Hugunin and Chris Chase. */
    /* this is harder to get right than you might think */
    assert(step != 0);
    // assert(step >= -PY_SSIZE_T_MAX); TODO: add the macro for HPy_ssize_t

    if (*start < 0) {
        *start += length;
        if (*start < 0) {
            *start = (step < 0) ? -1 : 0;
        }
    }
    else if (*start >= length) {
        *start = (step < 0) ? length - 1 : length;
    }

    if (*stop < 0) {
        *stop += length;
        if (*stop < 0) {
            *stop = (step < 0) ? -1 : 0;
        }
    }
    else if (*stop >= length) {
        *stop = (step < 0) ? length - 1 : length;
    }

    if (step < 0) {
        if (*stop < *start) {
            return (*start - *stop - 1) / (-step) + 1;
        }
    }
    else {
        if (*start < *stop) {
            return (*stop - *start - 1) / step + 1;
        }
    }
    return 0;
}

HPyAPI_FUNC HPy HPyTuple_Pack(HPyContext *ctx, HPy_ssize_t n, ...) {
    va_list vargs;
    HPy_ssize_t i;

    if (n == 0) {
        return HPyTuple_FromArray(ctx, (HPy*)NULL, n);
    }
    HPy *array = (HPy *)alloca(n * sizeof(HPy));
    va_start(vargs, n);
    if (array == NULL) {
        va_end(vargs);
        return HPy_NULL;
    }
    for (i = 0; i < n; i++) {
        array[i] = va_arg(vargs, HPy);
    }
    va_end(vargs);
    return HPyTuple_FromArray(ctx, array, n);
}

#endif //HPY_INLINE_HELPERS_H
