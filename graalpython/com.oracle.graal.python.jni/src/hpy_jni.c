/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <hpy.h>
#include <wchar.h>
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>

#ifndef NDEBUG
#define LOG(FORMAT, ...) printf("%-15s (%s:%d): %s " FORMAT "\n", __FUNCTION__, __FILE__, __LINE__, #__VA_ARGS__, __VA_ARGS__);
#define LOGS(FORMAT) printf("%-15s (%s:%d): " FORMAT "\n", __FUNCTION__, __FILE__, __LINE__);
#else
#define LOG(FORMAT, ...)
#define LOGS(FORMAT)
#endif

//*************************
// JNI upcalls

#include "com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext.h"
#include "hpynative.h"
#include <jni.h>

/* definitions for HPyTracker */
#include "hpy/runtime/ctx_funcs.h"

static JNIEnv* jniEnv;

#define ALL_UPCALLS \
    UPCALL(New, SIG_HPY SIG_PTR, SIG_HPY) \
    UPCALL(TypeGenericNew, SIG_HPY, SIG_HPY) \
    UPCALL(AsStruct, SIG_HPY, SIG_PTR) \
    UPCALL(Close, SIG_HPY, SIG_VOID) \
    UPCALL(FloatFromDouble, SIG_DOUBLE, SIG_HPY) \
    UPCALL(FloatAsDouble, SIG_HPY, SIG_DOUBLE) \
    UPCALL(LongAsLong, SIG_HPY, SIG_LONG) \
    UPCALL(LongAsDouble, SIG_HPY, SIG_DOUBLE) \
    UPCALL(LongFromLong, SIG_LONG, SIG_HPY) \
    UPCALL(Dup, SIG_HPY, SIG_HPY) \
    UPCALL(GetItemi, SIG_HPY SIG_SIZE_T, SIG_HPY) \
    UPCALL(SetItemi, SIG_HPY SIG_SIZE_T SIG_HPY, SIG_INT) \
    UPCALL(SetItem, SIG_HPY SIG_HPY SIG_HPY, SIG_INT) \
    UPCALL(NumberCheck, SIG_HPY, SIG_INT) \
    UPCALL(TypeCheck, SIG_HPY SIG_HPY, SIG_INT) \
    UPCALL(Length, SIG_HPY, SIG_SIZE_T) \
    UPCALL(ListCheck, SIG_HPY, SIG_INT) \
    UPCALL(UnicodeFromWideChar, SIG_PTR SIG_SIZE_T, SIG_HPY) \
    UPCALL(UnicodeFromJCharArray, SIG_JCHARARRAY, SIG_HPY) \
    UPCALL(DictNew, , SIG_HPY) \
    UPCALL(ListNew, SIG_SIZE_T, SIG_HPY) \
    UPCALL(TupleFromArray, SIG_JLONGARRAY SIG_BOOL, SIG_HPY) \

#define UPCALL(name, jniSigArgs, jniSigRet) static jmethodID jniMethod_ ## name;
ALL_UPCALLS
#undef UPCALL

#define DO_UPCALL_HPY(jni_ctx, name, ...) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_HPY_NOARGS(jni_ctx, name) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)})
#define DO_UPCALL_TRACKER(jni_ctx, name, ...) ((HPyTracker){(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_PTR(jni_ctx, name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_SIZE_T(jni_ctx, name, ...) (HPy_ssize_t) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_INT(jni_ctx, name, ...) (int) (*jniEnv)->CallIntMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_DOUBLE(jni_ctx, name, ...) (double) (*jniEnv)->CallDoubleMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_LONG(jni_ctx, name, ...) (long) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_VOID(jni_ctx, name, ...) (*jniEnv)->CallVoidMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)

#define HPY_UP(_h) ((jlong)((_h)._i))
#define PTR_UP jlong
#define LONG_UP jlong
#define DOUBLE_UP jdouble
#define SIZE_T_UP jlong
#define TRACKER_UP(_h) ((jlong)((_h)._i))

#define CONTEXT_INSTANCE(_hpy_ctx) ((jobject)(graal_hpy_context_get_native_context(_hpy_ctx)->jni_context))


static void *ctx_AsStruct_jni(HPyContext *ctx, HPy h) {
    return DO_UPCALL_PTR(CONTEXT_INSTANCE(ctx), AsStruct, HPY_UP(h));
}

static HPy ctx_FloatFromDouble_jni(HPyContext *ctx, double v) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), FloatFromDouble, (DOUBLE_UP) v);
}

static double ctx_FloatAsDouble_jni(HPyContext *ctx, HPy h) {
    return DO_UPCALL_DOUBLE(CONTEXT_INSTANCE(ctx), FloatAsDouble, HPY_UP(h));
}

static long ctx_LongAsLong_jni(HPyContext *ctx, HPy h) {
    return DO_UPCALL_LONG(CONTEXT_INSTANCE(ctx), LongAsLong, HPY_UP(h));
}

static double ctx_LongAsDouble_jni(HPyContext *ctx, HPy h) {
    return DO_UPCALL_DOUBLE(CONTEXT_INSTANCE(ctx), LongAsDouble, HPY_UP(h));
}

static HPy ctx_LongFromLong_jni(HPyContext *ctx, long l) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), LongFromLong, l);
}

static HPy ctx_New_jni(HPyContext *ctx, HPy type, void** data) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), New, HPY_UP(type), (SIZE_T_UP) data);
}

static HPy ctx_GetItemi_jni(HPyContext *ctx, HPy obj, HPy_ssize_t idx) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), GetItemi, HPY_UP(obj), (SIZE_T_UP) idx);
}

static int ctx_SetItemi_jni(HPyContext *ctx, HPy obj, HPy_ssize_t idx, HPy value) {
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), SetItemi, HPY_UP(obj), (SIZE_T_UP) idx, HPY_UP(value));
}

static int ctx_SetItem_jni(HPyContext *ctx, HPy obj, HPy key, HPy value) {
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), SetItem, HPY_UP(obj), HPY_UP(key), HPY_UP(value));
}

static void ctx_Close_jni(HPyContext *ctx, HPy h) {
    DO_UPCALL_VOID(CONTEXT_INSTANCE(ctx), Close, HPY_UP(h));
}

static HPy ctx_Dup_jni(HPyContext *ctx, HPy h) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), Dup, HPY_UP(h));
}

static int ctx_NumberCheck_jni(HPyContext *ctx, HPy obj) {
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), NumberCheck, HPY_UP(obj));
}

static int ctx_TypeCheck_jni(HPyContext *ctx, HPy obj, HPy type) {
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), TypeCheck, HPY_UP(obj), HPY_UP(type));
}

static int ctx_ListCheck_jni(HPyContext *ctx, HPy obj) {
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), ListCheck, HPY_UP(obj));
}

static HPy_ssize_t ctx_Length_jni(HPyContext *ctx, HPy obj) {
    return DO_UPCALL_SIZE_T(CONTEXT_INSTANCE(ctx), Length, HPY_UP(obj));
}

static HPy ctx_TypeGenericNew_jni(HPyContext *ctx, HPy type, _HPyPtr args, HPy_ssize_t nargs, HPy kw) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), TypeGenericNew, HPY_UP(type));
}

static HPy ctx_Unicode_FromWideChar_jni(HPyContext *ctx, const wchar_t *arr, HPy_ssize_t idx) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), UnicodeFromWideChar, (PTR_UP) arr, (SIZE_T_UP) idx);
}

static HPy ctx_DictNew_jni(HPyContext *ctx) {
    return DO_UPCALL_HPY_NOARGS(CONTEXT_INSTANCE(ctx), DictNew);
}

static HPy ctx_ListNew_jni(HPyContext *ctx, HPy_ssize_t len) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ListNew, (SIZE_T_UP) len);
}

//*************************
// BOXING

#define NAN_BOXING_BASE (0x0007000000000000llu)
#define NAN_BOXING_MASK (0xFFFF000000000000llu)
#define NAN_BOXING_INT (0x0001000000000000llu)
#define NAN_BOXING_INT_MASK (0x00000000FFFFFFFFllu)
#define NAN_BOXING_MAX_HANDLE (0x000000007FFFFFFFllu)
#define IMMUTABLE_HANDLES (0x0000000000000100llu)

static bool isBoxedDouble(uint64_t value) {
    return value >= NAN_BOXING_BASE;
}

static bool isBoxedHandle(uint64_t value) {
    return value <= NAN_BOXING_MAX_HANDLE;
}

static bool isBoxedInt(uint64_t value) {
    return (value & NAN_BOXING_MASK) == NAN_BOXING_INT;
}

static double unboxDouble(uint64_t value) {
    uint64_t doubleBits = value - NAN_BOXING_BASE;
    return * ((double*) &doubleBits);
}

static uint64_t boxDouble(double value) {
    // assumes that value doesn't contain non-standard silent NaNs
    uint64_t doubleBits = * ((uint64_t*) &value);
    return doubleBits + NAN_BOXING_BASE;
}

static uint64_t unboxHandle(uint64_t value) {
    return value;
}

static uint64_t boxHandle(uint64_t handle) {
    return handle;
}

static int32_t unboxInt(uint64_t value) {
    return (int32_t) (value - NAN_BOXING_INT);
}

static uint64_t boxInt(int32_t value) {
    return (value & NAN_BOXING_INT_MASK) + NAN_BOXING_INT;
}

static inline uint64_t toBits(HPy ptr) {
    /* return * ((uint64_t*) &ptr._i); */
    return (uint64_t) (ptr._i);
}

static inline HPy toPtr(uint64_t ptr) {
    /* return * ((void**) &ptr); */
    return (HPy) { (HPy_ssize_t) ptr };
}

//*************************
// direct fast paths that handle certain calls on the native side:

static void *(*original_AsStruct)(HPyContext *ctx, HPy h);
static HPy (*original_Dup)(HPyContext *ctx, HPy h);
static HPy (*original_FloatFromDouble)(HPyContext *ctx, double v);
static double (*original_FloatAsDouble)(HPyContext *ctx, HPy h);
static long (*original_LongAsLong)(HPyContext *ctx, HPy h);
static double (*original_LongAsDouble)(HPyContext *ctx, HPy h);
static HPy (*original_LongFromLong)(HPyContext *ctx, long l);
static int (*original_ListCheck)(HPyContext *ctx, HPy h);
static int (*original_NumberCheck)(HPyContext *ctx, HPy h);
static int (*original_TypeCheck)(HPyContext *ctx, HPy h, HPy type);
static void (*original_Close)(HPyContext *ctx, HPy h);
static HPy (*original_UnicodeFromWideChar)(HPyContext *ctx, const wchar_t *arr, HPy_ssize_t size);
static HPy (*original_TupleFromArray)(HPyContext *ctx, HPy *items, HPy_ssize_t nitems);

static void *augment_AsStruct(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        void** space = (void**)ctx->_private;
        return space[unboxHandle(bits)];
    } else {
        return NULL;
    }
}

static HPy augment_FloatFromDouble(HPyContext *ctx, double v) {
    return toPtr(boxDouble(v));
}

static double augment_FloatAsDouble(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedDouble(bits)) {
        return unboxDouble(bits);
    } else if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_FloatAsDouble(ctx, h);
    }
}

static long augment_LongAsLong(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_LongAsLong(ctx, h);
    }
}

static double augment_LongAsDouble(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_LongAsDouble(ctx, h);
    }
}

static HPy augment_LongFromLong(HPyContext *ctx, long l) {
	int32_t i = (int32_t) l;
	if (l == i) {
        return toPtr(boxInt(i));
    } else {
        return original_LongFromLong(ctx, l);
    }
}

static void augment_Close(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (!bits) {
        return;
    } else if (isBoxedHandle(bits)) {
        return original_Close(ctx, h);
    }
}

static HPy augment_Dup(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        if (bits < IMMUTABLE_HANDLES) {
            return h;
        }
        return original_Dup(ctx, h);
    } else {
        return h;
    }
}

static int augment_NumberCheck(HPyContext *ctx, HPy obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedDouble(bits) || isBoxedInt(bits)) {
        return true;
    } else {
        return original_NumberCheck(ctx, obj);
    }
}

static int augment_TypeCheck(HPyContext *ctx, HPy obj, HPy type) {
    uint64_t bits = toBits(obj);
    if (isBoxedInt(bits)) {
        if (toBits(type) == toBits(ctx->h_LongType)) {
            return true;
        }
        if (toBits(type) == toBits(ctx->h_FloatType)) {
            return false;
        }
    } else if (isBoxedDouble(bits)) {
        if (toBits(type) == toBits(ctx->h_FloatType)) {
            return true;
        }
        if (toBits(type) == toBits(ctx->h_LongType)) {
            return false;
        }
    }
    return original_TypeCheck(ctx, obj, type);
}

static int augment_ListCheck(HPyContext *ctx, HPy obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedHandle(bits)) {
        return original_ListCheck(ctx, obj);
    } else {
        return false;
    }
}

#define MAX_UNICODE 0x10ffff

static HPy augment_UnicodeFromWideChar(HPyContext *ctx, const wchar_t *u, HPy_ssize_t size) {
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
        return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), UnicodeFromJCharArray, jCharArray);
    } else {
        return original_UnicodeFromWideChar(ctx, u, size);
    }
}

static HPy augment_TupleFromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems) {
    jarray jLongArray = (*jniEnv)->NewLongArray(jniEnv, (jsize) nitems);
    jlong *content = (*jniEnv)->GetPrimitiveArrayCritical(jniEnv, jLongArray, 0);
    HPy_ssize_t i;
    for (i = 0; i < nitems; i++) {
        content[i] = (jlong) toBits(items[i]);
    }
    (*jniEnv)->ReleasePrimitiveArrayCritical(jniEnv, jLongArray, content, 0);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), TupleFromArray, jLongArray, JNI_FALSE);
}

void initDirectFastPaths(HPyContext *context) {
    LOG("%p", context);
    context->name = "HPy Universal ABI (GraalVM backend, JNI)";

    original_FloatFromDouble = context->ctx_Float_FromDouble;
    context->ctx_Float_FromDouble = augment_FloatFromDouble;

    original_FloatAsDouble = context->ctx_Float_AsDouble;
    context->ctx_Float_AsDouble = augment_FloatAsDouble;

    original_LongAsLong = context->ctx_Long_AsLong;
    context->ctx_Long_AsLong = augment_LongAsLong;

    original_LongAsDouble = context->ctx_Long_AsDouble;
    context->ctx_Long_AsDouble = augment_LongAsDouble;

    original_LongFromLong = context->ctx_Long_FromLong;
    context->ctx_Long_FromLong = augment_LongFromLong;

    original_Close = context->ctx_Close;
    context->ctx_Close = augment_Close;

    original_AsStruct = context->ctx_AsStruct;
    context->ctx_AsStruct = augment_AsStruct;

    original_Dup = context->ctx_Dup;
    context->ctx_Dup = augment_Dup;

    original_NumberCheck = context->ctx_Number_Check;
    context->ctx_Number_Check = augment_NumberCheck;

    original_TypeCheck = context->ctx_TypeCheck;
    context->ctx_TypeCheck = augment_TypeCheck;

    original_ListCheck = context->ctx_List_Check;
    context->ctx_List_Check = augment_ListCheck;

    original_UnicodeFromWideChar = context->ctx_Unicode_FromWideChar;
    context->ctx_Unicode_FromWideChar = augment_UnicodeFromWideChar;

    original_TupleFromArray = context->ctx_Tuple_FromArray;
    context->ctx_Tuple_FromArray = augment_TupleFromArray;
}

void setHPyContextNativeSpace(HPyContext *context, void** nativeSpace) {
    LOG("%p %p", context, nativeSpace);
    context->_private = nativeSpace;
}

typedef void (*destroyfunc)(void *);

JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_hpyCallDestroyFunc(JNIEnv *env, jclass clazz, jlong obj, jlong func) {
    destroyfunc f = (destroyfunc) func;
    f((void*) obj);
}

/* Initialize the jmethodID pointers for all the context functions implemented via JNI. */
JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_initJNI(JNIEnv *env, jclass clazz, jobject ctx, jlong ctxPointer) {
    LOG("%s", "hpy_native.c:initJNI\n");
    jniEnv = env;
    HPyContext *context = (HPyContext *) ctxPointer;

    context->ctx_Float_FromDouble = ctx_FloatFromDouble_jni;
    context->ctx_Float_AsDouble = ctx_FloatAsDouble_jni;
    context->ctx_Long_AsLong = ctx_LongAsLong_jni;
    context->ctx_Long_AsDouble = ctx_LongAsDouble_jni;
    context->ctx_AsStruct = ctx_AsStruct_jni;
    context->ctx_Close = ctx_Close_jni;

    context->ctx_Dup = ctx_Dup_jni;
    context->ctx_Number_Check = ctx_NumberCheck_jni;
    context->ctx_TypeCheck = ctx_TypeCheck_jni;
    context->ctx_List_Check = ctx_ListCheck_jni;

    context->ctx_Length = ctx_Length_jni;
    context->ctx_New = ctx_New_jni;
    context->ctx_Type_GenericNew = ctx_TypeGenericNew_jni;

    context->ctx_GetItem_i = ctx_GetItemi_jni;
    context->ctx_SetItem_i = ctx_SetItemi_jni;
    context->ctx_SetItem = ctx_SetItem_jni;

    context->ctx_Unicode_FromWideChar = ctx_Unicode_FromWideChar_jni;

    context->ctx_Dict_New = ctx_DictNew_jni;
    context->ctx_List_New = ctx_ListNew_jni;

    context->ctx_Tracker_New = ctx_Tracker_New;
    context->ctx_Tracker_Add = ctx_Tracker_Add;
    context->ctx_Tracker_ForgetAll = ctx_Tracker_ForgetAll;
    context->ctx_Tracker_Close = ctx_Tracker_Close;

    graal_hpy_context_get_native_context(context)->jni_context = (void *) (*env)->NewGlobalRef(env, ctx);
    assert(clazz != NULL);

#define SIG_HPY "J"
#define SIG_SIZE_T "J"
#define SIG_PTR "J"
#define SIG_VOID "V"
#define SIG_BOOL "Z"
#define SIG_INT "I"
#define SIG_LONG "J"
#define SIG_DOUBLE "D"
#define SIG_TRACKER "J"
#define SIG_JCHARARRAY "[C"
#define SIG_JLONGARRAY "[J"

#define UPCALL(name, jniSigArgs, jniSigRet) \
    jniMethod_ ## name = (*env)->GetMethodID(env, clazz, "ctx" #name, "(" jniSigArgs ")" jniSigRet); \
    if (jniMethod_ ## name == NULL) { \
    	LOGS("ERROR: jni method ctx" #name " not found found !\n"); \
    	return 1; \
    }

ALL_UPCALLS
#undef UPCALL

    /* success */
    return 0;
}

// helper functions for fast HPy downcalls:

JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive1(JNIEnv *env, jclass clazz, jlong target, jlong arg1) {
    return ((jlong (*)(jlong)) target)(arg1);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive2(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2) {
    return ((jlong (*)(jlong, jlong)) target)(arg1, arg2);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive3(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3) {
    return ((jlong (*)(jlong, jlong, jlong)) target)(arg1, arg2, arg3);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive4(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4) {
    return ((jlong (*)(jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive5(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive6(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5, jlong arg6) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5, arg6);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive7(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5, jlong arg6, jlong arg7) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive8(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5, jlong arg6, jlong arg7, jlong arg8) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive9(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5, jlong arg6, jlong arg7, jlong arg8, jlong arg9) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
}
JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executePrimitive10(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5, jlong arg6, jlong arg7, jlong arg8, jlong arg9, jlong arg10) {
    return ((jlong (*)(jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
}
JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeInquiry(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2) {
	return ((jint (*)(jlong, jlong)) target)(arg1, arg2);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeSsizeobjargproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4) {
	return ((jint (*)(jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeSsizesizeobjargproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5) {
	return ((jint (*)(jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeObjobjproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3) {
	return ((jint (*)(jlong, jlong, jlong)) target)(arg1, arg2, arg3);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeObjobjargproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4) {
	return ((jint (*)(jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeInitproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jlong arg4, jlong arg5) {
	return ((jint (*)(jlong, jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4, arg5);
}

JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeFreefunc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2) {
	((void (*)(jlong, jlong)) target)(arg1, arg2);
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeGetbufferproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jint arg4) {
	return ((jint (*)(jlong, jlong, jlong, jint)) target)(arg1, arg2, arg3, arg4);
}

JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeReleasebufferproc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3) {
	((void (*)(jlong, jlong, jlong)) target)(arg1, arg2, arg3);
}

JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeRichcomparefunc(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2, jlong arg3, jint arg4) {
	return ((jlong (*)(jlong, jlong, jlong, jlong)) target)(arg1, arg2, arg3, arg4);
}

JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_executeDestructor(JNIEnv *env, jclass clazz, jlong target, jlong arg1, jlong arg2) {
	((void (*)(jlong, jlong)) target)(arg1, arg2);
}
