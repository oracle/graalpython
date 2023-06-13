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

#include "debug_internal.h"
#include "../hpy_jni.h"

/* ~~~ debug mode implementation of HPyTracker ~~~

   This is a bit special and it's worth explaining what is going on.

   The HPyTracker functions need their own debug mode implementation because
   the debug mode needs to be aware of when a DHPy is closed, for the same
   reason for why we need debug_ctx_Close.

   So, in theory here we should have our own implementation of a
   DebugHPyTracker which manages a growable list of handles, and which calls
   debug_ctx_Close at the end. But, we ALREADY have the logic available, it's
   implemented in ctx_tracker.c.

   So, we can share some functions but note that the tracker will not store
   universal handles but debug handles.

   Since we already have some special (and faster) implementation for
   'ctx_Tracker_Add' and 'ctx_Tracker_Close', we need to have a separate debug
   mode implementation for them.
*/

HPyTracker debug_ctx_Tracker_New(HPyContext *dctx, HPy_ssize_t size)
{
    return ctx_Tracker_New_jni(dctx, size);
}

_HPy_HIDDEN int
debug_ctx_Tracker_Add(HPyContext *dctx, HPyTracker ht, DHPy h)
{
    return raw_Tracker_Add(dctx, ht, h);
}

void debug_ctx_Tracker_ForgetAll(HPyContext *dctx, HPyTracker ht)
{
    ctx_Tracker_ForgetAll_jni(dctx, ht);
}

_HPy_HIDDEN void
debug_ctx_Tracker_Close(HPyContext *ctx, HPyTracker ht)
{
    _HPyTracker_s *hp = _ht2hp(ht);
    HPy_ssize_t i;
    for (i=0; i<hp->length; i++) {
        HPy_Close(ctx, hp->handles[i]);
    }
    free(hp->handles);
    free(hp);
}
