/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "hpy_jni.h"
#include "com_oracle_graal_python_builtins_objects_cext_hpy_jni_GraalHPyJNITrampolines.h"

#define TRAMPOLINE(name) Java_com_oracle_graal_python_builtins_objects_cext_hpy_jni_GraalHPyJNITrampolines_ ## name


/*******************************************************************
 *                        MANUAL TRAMPOLINES                       *
 *******************************************************************/

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugKeywords)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong self, jlong args, jlong nargs, jlong kwnames)
{
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_keywords f = (HPyFunc_keywords)target;
    DHPy dh_self = _jlong2dh(dctx, self);
    UHPy uh_kwnames = _jlong2h(kwnames);
    DHPy dh_kwnames;
    HPy_ssize_t n_kwnames;
    if (!HPy_IsNull(uh_kwnames))
    {
        n_kwnames = HPy_Length(get_info(dctx)->uctx, uh_kwnames);
        dh_kwnames = DHPy_open(dctx, uh_kwnames);
    }
    else
    {
        n_kwnames = 0;
        dh_kwnames = HPy_NULL;
    }
    assert(nargs >= 0);
    assert(n_kwnames >= 0);
    size_t nargs_with_kw = (size_t)nargs + (size_t)n_kwnames;
    _ARR_JLONG2DH(dctx, dh_args, args, nargs_with_kw)
    DHPy dh_result = f(dctx, dh_self, dh_args, (size_t)nargs, dh_kwnames);
    _ARR_DH_CLOSE(dctx, dh_args, nargs_with_kw)
    DHPy_close_and_check(dctx, dh_self);
    DHPy_close_and_check(dctx, dh_kwnames);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugGetbufferproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jint arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_getbufferproc f = (HPyFunc_getbufferproc) target;
    DHPy_buffer dbuffer;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    jint result = f(dctx, dh_arg1, &dbuffer, (int) arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    _buffer_d2u(dctx, &dbuffer, (UHPy_buffer *) arg2);
    DHPy_close(dctx, dbuffer.obj);
    return result;
}

JNIEXPORT void JNICALL TRAMPOLINE(executeDebugReleasebufferproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_releasebufferproc f = (HPyFunc_releasebufferproc) target;
    DHPy_buffer dbuf;
    _buffer_u2d(dctx, (UHPy_buffer *) arg2, &dbuf);
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    f(dctx, dh_arg1, &dbuf);
    DHPy_close_and_check(dctx, dh_arg1);
    // TODO(fa): should we use DHPy_close_and_check ?
    DHPy_close(dctx, dbuf.obj);
}

JNIEXPORT void JNICALL TRAMPOLINE(executeDestroyfunc)(JNIEnv *env, jclass clazz, jlong target, jlong dataptr)
{
    HPyFunc_destroyfunc f = (HPyFunc_destroyfunc)target;
    f((void *)dataptr);
}

#undef TRAMPOLINE
