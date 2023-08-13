/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "hpy_log.h"
#include "hpy_native_cache.h"
#include "hpy_trace.h"

#include <wchar.h>
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>
#include <errno.h>

//*************************
// JNI upcalls

#include "com_oracle_graal_python_builtins_objects_cext_hpy_jni_GraalHPyJNIContext.h"
#include "hpynative.h"

#include "autogen_ctx_init_jni.h"

/* definitions for HPyTracker */
#include "hpy/runtime/ctx_funcs.h"

#define JNI_HELPER(NAME) Java_com_oracle_graal_python_builtins_objects_cext_hpy_jni_GraalHPyJNIContext_ ## NAME

_HPy_HIDDEN JNIEnv* jniEnv;

#define ALL_FIELDS \
    FIELD(hpyHandleTable, CLASS_HPYCONTEXT, SIG_JOBJECTARRAY) \
    FIELD(hpyGlobalsTable, CLASS_HPYCONTEXT, SIG_JOBJECTARRAY) \
    FIELD(nextHandle, CLASS_HPYCONTEXT, SIG_INT)

#define FIELD(name, clazz, jniSig) static jfieldID jniField_ ## name;
ALL_FIELDS
#undef FIELD

/*
 * This macro specifies custom JNI upcalls.
 * Add entries if you want to have (a) an additional upcall that does not
 * correspond to an official HPy context function, or (b) if an upcall has a
 * different signature then the public one.
 */
#define CUSTOM_UPCALLS \
    UPCALL(ctxGetItems, SIG_HPY SIG_JSTRING, SIG_HPY) \
    UPCALL(ctxSetItems, SIG_HPY SIG_JSTRING SIG_HPY, SIG_INT) \
    UPCALL(ctxGetAttrs, SIG_HPY SIG_JSTRING, SIG_HPY) \
    UPCALL(ctxFieldStore, SIG_HPY SIG_HPYFIELD SIG_HPY, SIG_PTR) \
    UPCALL(ctxGlobalStore, SIG_HPY SIG_HPYGLOBAL, SIG_PTR) \
    UPCALL(ctxContextVarGet, SIG_HPY SIG_HPY SIG_HPY, SIG_HPY) \
    UPCALL(ctxBulkClose, SIG_PTR SIG_INT, SIG_VOID) \
    UPCALL(ctxUnicodeFromJCharArray, SIG_JCHARARRAY, SIG_HPY) \
    UPCALL(ctxSequenceFromArray, SIG_JLONGARRAY SIG_BOOL SIG_BOOL, SIG_HPY)


#define UPCALL(name, jniSigArgs, jniSigRet) static jmethodID jniMethod_ ## name;
CUSTOM_UPCALLS
#undef UPCALL

static jmethodID jniMethod_hpy_debug_get_context;
static jmethodID jniMethod_hpy_trace_get_context;


#define MAX_UNCLOSED_HANDLES 32
static int32_t unclosedHandleTop = 0;
static HPy unclosedHandles[MAX_UNCLOSED_HANDLES];

static inline jsize get_handle_table_size(HPyContext *ctx) {
    uint64_t size = HANDLE_TABLE_SIZE(ctx->_private);
    assert((jsize)size == size);
    return (jsize)size;
}

static uint64_t get_hpy_handle_for_object(HPyContext *ctx, jobject hpyContext, jobject element, bool update_native_cache) {
    /* TODO(fa): for now, we fall back to the upcall */
    if (update_native_cache) {
        return 0;
    }

    jobjectArray hpy_handles = (jobjectArray)(*jniEnv)->GetObjectField(jniEnv, hpyContext, jniField_hpyHandleTable);
    if (hpy_handles == NULL) {
        LOGS("hpy handle table is NULL")
        return 0;
    }

    /* try to reuse a closed handle from our native list */
    jsize next_handle;
    if (unclosedHandleTop > 0) {
        uint64_t recycled = toBits(unclosedHandles[--unclosedHandleTop]);
        LOG("%llu", (unsigned long long)recycled)
        assert(recycled < INT32_MAX);
        next_handle = (jsize) recycled;
    } else {
        next_handle = (*jniEnv)->GetIntField(jniEnv, hpyContext, jniField_nextHandle);
        LOG("%d", next_handle)
        jsize s = get_handle_table_size(ctx);
        if (next_handle >= s) {
            return 0;
        }
        (*jniEnv)->SetIntField(jniEnv, hpyContext, jniField_nextHandle, next_handle+1);
    }
    (*jniEnv)->SetObjectArrayElement(jniEnv, hpy_handles, next_handle, element);
    (*jniEnv)->DeleteLocalRef(jniEnv, hpy_handles);
    /* TODO(fa): update native data pointer cache here (if specified) */
    return boxHandle(next_handle);
}

static jobject get_object_for_hpy_handle(jobject hpyContext, uint64_t bits) {
    jobjectArray hpy_handles = (jobjectArray)(*jniEnv)->GetObjectField(jniEnv, hpyContext, jniField_hpyHandleTable);
    if (hpy_handles == NULL) {
        LOGS("hpy handle table is NULL")
        return NULL;
    }
    jobject element = (*jniEnv)->GetObjectArrayElement(jniEnv, (jobjectArray)hpy_handles, (jsize)unboxHandle(bits));
    (*jniEnv)->DeleteLocalRef(jniEnv, hpy_handles);
    if (element == NULL) {
        LOGS("handle delegate is NULL")
    }
    return element;
}

static jobject get_object_for_hpy_global(jobject hpyContext, uint64_t bits) {
    jobject hpy_globals = (*jniEnv)->GetObjectField(jniEnv, hpyContext, jniField_hpyGlobalsTable);
    if (hpy_globals == NULL) {
        LOGS("hpy globals is NULL")
        return NULL;
    }
    jobject element = (*jniEnv)->GetObjectArrayElement(jniEnv, (jobjectArray)hpy_globals, (jsize)unboxHandle(bits));
    (*jniEnv)->DeleteLocalRef(jniEnv, hpy_globals);
    if (element == NULL) {
        LOGS("globals element is NULL")
        return NULL;
    }
    return element;
}

#define MAX_UNICODE 0x10ffff

_HPy_HIDDEN HPy ctx_Unicode_FromWideChar_jni(HPyContext *ctx, const wchar_t *u, HPy_ssize_t size) {
    if (u == NULL && size != 0) {
        return HPy_NULL;
    }

    if (sizeof(wchar_t) != sizeof(uint32_t)) {
        HPyErr_SetString(ctx, ctx->h_SystemError, "unsupported size of type wchar_t");
        return HPy_NULL;
    }

    if (size == -1) {
        size = wcslen(u);
    }

    if (size > INT32_MAX) {
        HPyErr_SetString(ctx, ctx->h_SystemError, "wchar_t array is too large");
        return HPy_NULL;
    }

    uint32_t maxchar = 0;
    wchar_t ch;
    HPy_ssize_t i;
    for (i = 0; i < size; i++) {
        ch = u[i];
        if (ch > maxchar) {
            maxchar = ch;
            if (maxchar > MAX_UNICODE) {
                HPyErr_SetString(ctx, ctx->h_ValueError, "character is not in range [U+0000; U+10ffff]");
                return HPy_NULL;
            }
        }
    }

    if (maxchar < UINT16_MAX) {
        jarray jCharArray = (*jniEnv)->NewCharArray(jniEnv, (jsize) size);
        jchar *content = (*jniEnv)->GetPrimitiveArrayCritical(jniEnv, jCharArray, 0);
        HPy_ssize_t i;
        for (i = 0; i < size; i++) {
            content[i] = (jchar) u[i];
        }
        (*jniEnv)->ReleasePrimitiveArrayCritical(jniEnv, jCharArray, content, 0);
        return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctxUnicodeFromJCharArray, jCharArray);
    } else {
        return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctx_Unicode_FromWideChar, PTR_UP(u), SIZE_T_UP(size));
    }
}

_HPy_HIDDEN HPy ctx_Global_Load_jni(HPyContext *ctx, HPyGlobal global) {
    uint64_t bits = toBits(global);
    if (bits && isBoxedHandle(bits)) {
        jobject hpyContext = graal_hpy_context_get_native_context(ctx)->jni_context;
        jobject element = get_object_for_hpy_global(hpyContext, bits);
        if (element == NULL) {
            return HPy_NULL;
        }

        uint64_t new_handle = get_hpy_handle_for_object(ctx, hpyContext, element, false);
        (*jniEnv)->DeleteLocalRef(jniEnv, element);
        if (new_handle) {
            load_global_native_data_pointer(ctx, bits, new_handle);
            return toPtr(new_handle);
        }
        return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctx_Global_Load, bits);
    } else {
        return toPtr(bits);
    }
}

static void ctx_Global_Store_jni(HPyContext *ctx, HPyGlobal *h, HPy v) {
    h->_i = DO_UPCALL_INTPTR_T(CONTEXT_INSTANCE(ctx), ctxGlobalStore, HPY_GLOBAL_UP(*h), HPY_UP(v));
}

static void ctx_Field_Store_jni(HPyContext *ctx, HPy owner, HPyField *field, HPy value) {
    field->_i = DO_UPCALL_INTPTR_T(CONTEXT_INSTANCE(ctx), ctxFieldStore, HPY_UP(owner), HPY_FIELD_UP(*field), HPY_UP(value));
}

static const char* getBoxedPrimitiveName(uint64_t bits) {
    assert(!isBoxedHandle(bits));
    if (isBoxedInt(bits)) {
        return "int";
    }
    assert(isBoxedDouble(bits));
    return "float";
}

static int ctx_SetItem_s_jni(HPyContext *ctx, HPy target, const char *name, HPy value) {
    uint64_t bits = toBits(target);
    if (!isBoxedHandle(bits)) {
        const size_t buffer_size = 128;
#ifdef _MSC_VER
        char *message = (char *)alloca(buffer_size);
#else
        char message[buffer_size];
#endif
        snprintf(message, buffer_size,
                 "'%s' object does not support item assignment", getBoxedPrimitiveName(bits));
        HPyErr_SetString(ctx, ctx->h_TypeError, message);
        return -1;
    }
    jstring jname = (*jniEnv)->NewStringUTF(jniEnv, name);
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), ctxSetItems, target, jname, value);
}

static HPy ctx_GetItem_s_jni(HPyContext *ctx, HPy target, const char *name) {
    uint64_t bits = toBits(target);
    if (!isBoxedHandle(bits)) {
        const size_t buffer_size = 128;
#ifdef _MSC_VER
        char *message = (char *)alloca(buffer_size);
#else
        char message[buffer_size];
#endif
        snprintf(message, buffer_size,
                 "'%s' object is not subscriptable", getBoxedPrimitiveName(bits));
        return HPyErr_SetString(ctx, ctx->h_TypeError, message);
    }
    jstring jname = (*jniEnv)->NewStringUTF(jniEnv, name);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctxGetItems, target, jname);
}

static HPy ctx_GetAttr_s_jni(HPyContext *ctx, HPy target, const char *name) {
    jstring jname = (*jniEnv)->NewStringUTF(jniEnv, name);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctxGetAttrs, target, jname);
}

static int ctx_ContextVar_Get_jni(HPyContext *ctx, HPy var, HPy def, HPy *result) {
    /* This uses 'h_Ellipsis' as an error marker assuming that it is rather uncertain that this will be a valid return
       value. If 'h_Ellipsis' is returned, this indicates an error and we explicitly check for an error then. */
    HPy err_marker = ctx->h_Ellipsis;
    HPy r = DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctxContextVarGet, HPY_UP(var), HPY_UP(def), HPY_UP(err_marker));
    if (toBits(r) == toBits(err_marker) && HPyErr_Occurred(ctx)) {
        return -1;
    }
    *result = r;
    return 0;
}

_HPy_HIDDEN HPy upcallSequenceFromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems, bool steal, bool create_list) {
    jarray jLongArray = (*jniEnv)->NewLongArray(jniEnv, (jsize) nitems);
    (*jniEnv)->SetLongArrayRegion(jniEnv, jLongArray, 0, (jsize) nitems, (const jlong *)items);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ctxSequenceFromArray, jLongArray, (jboolean) steal, (jboolean) create_list);
}

static HPy ctx_Tuple_FromArray_jni(HPyContext *ctx, HPy *items, HPy_ssize_t nitems) {
    return upcallSequenceFromArray(ctx, items, nitems, false, JNI_FALSE);
}

_HPy_HIDDEN void upcallBulkClose(HPyContext *ctx, HPy *items, HPy_ssize_t nitems) {
    DO_UPCALL_VOID(CONTEXT_INSTANCE(ctx), ctxBulkClose, items, nitems);
}

/* Initialize the jmethodID pointers for all the context functions implemented via JNI. */
JNIEXPORT jlong JNICALL JNI_HELPER(initJNI)(JNIEnv *env, jclass clazz, jobject jbackend, jobject jctx, jlongArray jctx_handles) {
    LOG("%s", "hpy_jni.c:initJNI\n");
    GraalHPyContext *graal_hpy_context = (GraalHPyContext *) calloc(1, sizeof(GraalHPyContext));
    HPyContext *ctx = graal_native_context_get_hpy_context(graal_hpy_context);
    ctx->name = "HPy Universal ABI (GraalVM backend, JNI)";
    graal_hpy_context->jni_backend = (*env)->NewGlobalRef(env, jbackend);
    graal_hpy_context->jni_context = (*env)->NewGlobalRef(env, jctx);
    jniEnv = env;

    if (init_autogen_jni_ctx(env, clazz, ctx, jctx_handles)) {
        return PTR_UP(NULL);
    }

    /* The HPyTracker is backend-specific. For JNI, we stay in native since there is no benefit for
       doing an upcall. */
    ctx->ctx_Tracker_New = ctx_Tracker_New_jni;
    ctx->ctx_Tracker_Add = ctx_Tracker_Add_jni;
    ctx->ctx_Tracker_ForgetAll = ctx_Tracker_ForgetAll_jni;
    ctx->ctx_Tracker_Close = ctx_Tracker_Close_jni;

    ctx->ctx_Unicode_FromWideChar = ctx_Unicode_FromWideChar_jni;

    ctx->ctx_Tuple_FromArray = ctx_Tuple_FromArray_jni;

    ctx->ctx_Global_Load = ctx_Global_Load_jni;
    ctx->ctx_Global_Store = ctx_Global_Store_jni;
    ctx->ctx_Field_Store = ctx_Field_Store_jni;

    ctx->ctx_SetItem_s = ctx_SetItem_s_jni;
    ctx->ctx_GetItem_s = ctx_GetItem_s_jni;

    ctx->ctx_ContextVar_Get = ctx_ContextVar_Get_jni;
    ctx->ctx_GetAttr_s = ctx_GetAttr_s_jni;

    assert(clazz != NULL);

    jclass jctx_class = (*env)->GetObjectClass(env, jctx);
    if (jctx_class == NULL) {
        LOGS("ERROR: could not get class of Java HPy context object");
        return PTR_UP(NULL);
    }

#define CLASS_HPYCONTEXT jctx_class

#define SIG_HPY "J"
#define SIG_HPYGLOBAL "J"
#define SIG_HPYFIELD "J"
#define SIG_PTR "J"
#define SIG_VOID "V"
#define SIG_INT "I"
#define SIG_BOOL "Z"
#define SIG_JSTRING "Ljava/lang/String;"
#define SIG_JCHARARRAY "[C"
#define SIG_JLONGARRAY "[J"
#define SIG_JOBJECTARRAY "[Ljava/lang/Object;"

#define FIELD(name, clazz, jniSig) \
    jniField_ ## name = (*env)->GetFieldID(env, clazz, #name, jniSig); \
    if (jniField_ ## name == NULL) { \
        LOGS("ERROR: jni field " #name " not found found !\n"); \
        return PTR_UP(NULL); \
    }

ALL_FIELDS
#undef FIELD

#define UPCALL(name, jniSigArgs, jniSigRet) \
    jniMethod_ ## name = (*env)->GetMethodID(env, clazz, #name, "(" jniSigArgs ")" jniSigRet); \
    if (jniMethod_ ## name == NULL) { \
        LOGS("ERROR: jni method " #name " not found found !\n"); \
        return PTR_UP(NULL); \
    }

CUSTOM_UPCALLS
#undef UPCALL

    jniMethod_hpy_debug_get_context = (*env)->GetMethodID(env, clazz, "getHPyDebugContext", "()" SIG_PTR);
    if (jniMethod_hpy_debug_get_context == NULL) {
        LOGS("ERROR: jni method getHPyDebugContext not found found !\n");
        return PTR_UP(NULL);
    }

    jniMethod_hpy_trace_get_context = (*env)->GetMethodID(env, clazz, "getHPyTraceContext", "()" SIG_PTR);
    if (jniMethod_hpy_trace_get_context == NULL) {
        LOGS("ERROR: jni method getHPyTraceContext not found found !\n");
        return PTR_UP(NULL);
    }

    return PTR_UP(ctx);
}

JNIEXPORT jint JNICALL JNI_HELPER(finalizeJNIContext)(JNIEnv *env, jclass clazz, jlong ctx) {
    LOG("%s", "hpy_jni.c:finalizeJNIContext\n");
    // assert(info->magic_number == HPY_TRACE_MAGIC);
    GraalHPyContext *native_ctx = graal_hpy_context_get_native_context((HPyContext *) ctx);
    (*env)->DeleteGlobalRef(env, (jobject) native_ctx->jni_backend);
    (*env)->DeleteGlobalRef(env, (jobject) native_ctx->jni_context);
    free((void *)native_ctx);
    return 0;
}

JNIEXPORT jlong JNICALL JNI_HELPER(initJNIDebugContext)(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNIDebugContext\n");
    HPyContext *uctx = (HPyContext *) uctxPointer;

    HPyContext *dctx = (HPyContext *) malloc(sizeof(HPyContext));
    dctx->name = "HPy Debug Mode ABI";
    dctx->_private = NULL;
    dctx->abi_version = HPY_ABI_VERSION;

    hpy_debug_ctx_init(dctx, uctx);
    return PTR_UP(dctx);
}

JNIEXPORT jint JNICALL JNI_HELPER(finalizeJNIDebugContext)(JNIEnv *env, jclass clazz, jlong dctxPointer) {
    LOG("%s", "hpy_jni.c:finalizeJNIDebugContext\n");
    HPyContext *dctx = (HPyContext *) dctxPointer;
    hpy_debug_ctx_free(dctx);
    free(dctx);
    return 0;
}

JNIEXPORT jlong JNICALL JNI_HELPER(initJNIDebugModule)(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNIDebugModule\n");
    return PTR_UP(HPyInit__debug());
}

JNIEXPORT jlong JNICALL JNI_HELPER(initJNITraceContext)(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNITraceContext\n");
    HPyContext *uctx = (HPyContext *) uctxPointer;

    HPyContext *tctx = (HPyContext *) malloc(sizeof(HPyContext));
    tctx->name = "HPy Trace Mode ABI";
    tctx->_private = NULL;
    tctx->abi_version = HPY_ABI_VERSION;

    hpy_trace_ctx_init(tctx, uctx);
    return PTR_UP(tctx);
}

JNIEXPORT jint JNICALL JNI_HELPER(finalizeJNITraceContext)(JNIEnv *env, jclass clazz, jlong tctxPointer) {
    LOG("%s", "hpy_jni.c:finalizeJNITraceContext\n");
    HPyContext *tctx = (HPyContext *) tctxPointer;
    hpy_trace_ctx_free(tctx);
    free(tctx);
    return 0;
}

JNIEXPORT jlong JNICALL JNI_HELPER(initJNITraceModule)(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNITraceModule\n");
    return PTR_UP(HPyInit__trace());
}

JNIEXPORT jint JNICALL JNI_HELPER(strcmp)(JNIEnv *env, jclass clazz, jlong s1, jlong s2) {
    return (jint) strcmp((const char *)s1, (const char *)s2);
}

JNIEXPORT jint JNICALL JNI_HELPER(initJNINativeFastPaths)(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    init_native_fast_paths((HPyContext *) uctxPointer);
    return 0;
}

JNIEXPORT jint JNICALL JNI_HELPER(setNativeSpaceFunction)(JNIEnv *env, jclass clazz, jlong uctxPointer, jlong cachePtr) {
    LOG("%ld %ld", uctxPointer, cachePtr);
    HPyContext *ctx = (HPyContext *) uctxPointer;
    ctx->_private = (void *) cachePtr;
    return 0;
}

JNIEXPORT jint JNICALL JNI_HELPER(getErrno)(JNIEnv *env, jclass clazz) {
	return (jint) errno;
}

JNIEXPORT jlong JNICALL JNI_HELPER(getStrerror)(JNIEnv *env, jclass clazz, jint i) {
	return PTR_UP(strerror(i));
}

HPyContext * hpy_debug_get_ctx(HPyContext *uctx)
{
    HPyContext *dctx = (HPyContext *) DO_UPCALL_PTR_NOARGS(CONTEXT_INSTANCE(uctx), hpy_debug_get_context);
    if (uctx == dctx) {
        HPy_FatalError(uctx, "hpy_debug_get_ctx: expected an universal ctx, got a debug ctx");
    }
    return dctx;
}

HPyContext * hpy_trace_get_ctx(HPyContext *uctx)
{
    HPyContext *tctx = (HPyContext *) DO_UPCALL_PTR_NOARGS(CONTEXT_INSTANCE(uctx), hpy_trace_get_context);
    if (uctx == tctx) {
        HPy_FatalError(uctx, "hpy_trace_get_ctx: expected an universal ctx, "
                             "got a trace ctx");
    }
    return tctx;
}
