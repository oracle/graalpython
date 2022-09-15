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


#ifndef CTX_TRACKER_H_
#define CTX_TRACKER_H_

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

//*************************
// native HPyTracker implementation

typedef struct {
    HPy_ssize_t capacity;  // allocated handles
    HPy_ssize_t length;    // used handles
    HPy *handles;
} _HPyTracker_s;

static inline _HPyTracker_s *_ht2hp(HPyTracker ht) {
    return (_HPyTracker_s *) (ht)._i;
}
static inline HPyTracker _hp2ht(_HPyTracker_s *hp) {
    return (HPyTracker) {(HPy_ssize_t) (hp)};
}

void init_native_fast_paths(HPyContext *context);

_HPy_HIDDEN HPyTracker
augment_Tracker_New(HPyContext *ctx, HPy_ssize_t capacity);

/* Very much like 'augment_Tracker_Add' but doesn't do special handling for
   boxed values and immutable handles */
_HPy_HIDDEN int
raw_Tracker_Add(HPyContext *ctx, HPyTracker ht, HPy h);

_HPy_HIDDEN int
augment_Tracker_Add(HPyContext *ctx, HPyTracker ht, HPy h);

_HPy_HIDDEN void
augment_Tracker_ForgetAll(HPyContext *ctx, HPyTracker ht);

_HPy_HIDDEN void
augment_Tracker_Close(HPyContext *ctx, HPyTracker ht);

#endif /* CTX_TRACKER_H_ */
