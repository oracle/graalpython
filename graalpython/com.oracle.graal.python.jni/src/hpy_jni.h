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

#include <hpy.h>
#include <jni.h>
#include <stdint.h>

#include "debug_internal.h"
#include "ctx_tracker.h"

//*************************
// BOXING

#define NAN_BOXING_BASE (0x0007000000000000llu)
#define NAN_BOXING_MASK (0xFFFF000000000000llu)
#define NAN_BOXING_INT (0x0001000000000000llu)
#define NAN_BOXING_INT_MASK (0x00000000FFFFFFFFllu)
#define NAN_BOXING_MAX_HANDLE (0x000000007FFFFFFFllu)
#define IMMUTABLE_HANDLES (0x0000000000000100llu)

// Some singleton Python objects are guaranteed to be always represented by
// those handles, so that we do not have to upcall to unambiguously check if
// a handle represents one of those
#define SINGLETON_HANDLES_MAX (3)

#define isBoxedDouble(value) ((value) >= NAN_BOXING_BASE)
#define isBoxedHandle(value) ((value) <= NAN_BOXING_MAX_HANDLE)
#define isBoxedInt(value) (((value) & NAN_BOXING_MASK) == NAN_BOXING_INT)

#define unboxHandle(value) (value)
#define boxHandle(handle) (handle)

#define isBoxableInt(value) (INT32_MIN < (value) && (value) < INT32_MAX)
#define isBoxableUnsignedInt(value) ((value) < INT32_MAX)
#define unboxInt(value) ((int32_t) ((value) - NAN_BOXING_INT))
#define boxInt(value) ((((uint64_t) (value)) & NAN_BOXING_INT_MASK) + NAN_BOXING_INT)

#define toBits(ptr) ((uint64_t) ((ptr)._i))
#define toPtr(ptr) ((HPy) { (HPy_ssize_t) (ptr) })

_HPy_HIDDEN HPy upcallTupleFromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems, jboolean steal);

_HPy_HIDDEN void upcallBulkClose(HPyContext *ctx, HPy *items, HPy_ssize_t nitems);

_HPy_HIDDEN int hpy_debug_ctx_init(HPyContext *dctx, HPyContext *uctx);

_HPy_HIDDEN void hpy_debug_ctx_free(HPyContext *dctx);
