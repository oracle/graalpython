/*
* Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

#define HPY_UNIVERSAL_ABI
#include <hpy.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>

#define LOG(FORMAT, ...) printf("%-15s (%s:%d): %s " FORMAT "\n", __FUNCTION__, __FILE__, __LINE__, #__VA_ARGS__, __VA_ARGS__);

//*************************
// JNI upcalls

#include "com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext.h"
#include <jni.h>

static JNIEnv* jniEnv;
static jclass contextClass;
static jobject contextInstance;
static jmethodID testUpcall;

#define ALL_UPCALLS \
    UPCALL(New, SIG_HPY SIG_PTR, SIG_HPY) \
    UPCALL(TypeGenericNew, SIG_HPY, SIG_HPY) \
    UPCALL(Cast, SIG_HPY, SIG_PTR) \
    UPCALL(Close, SIG_HPY, SIG_VOID) \
    UPCALL(FloatFromDouble, SIG_DOUBLE, SIG_HPY) \
    UPCALL(FloatAsDouble, SIG_HPY, SIG_DOUBLE) \
    UPCALL(LongAsLong, SIG_LONG, SIG_HPY) \
    UPCALL(Dup, SIG_HPY, SIG_HPY) \
    UPCALL(GetItemi, SIG_HPY SIG_SIZE_T, SIG_HPY) \
    UPCALL(NumberCheck, SIG_HPY, SIG_INT) \
    UPCALL(Length, SIG_HPY, SIG_SIZE_T) \
    UPCALL(ListCheck, SIG_HPY, SIG_INT) \
    UPCALL(TrackerNew, SIG_SIZE_T, SIG_TRACKER) \
    UPCALL(TrackerAdd, SIG_TRACKER SIG_HPY, SIG_INT) \
    UPCALL(TrackerClose, SIG_TRACKER, SIG_VOID)

#define UPCALL(name, jniSigArgs, jniSigRet) static jmethodID jniMethod_ ## name;
ALL_UPCALLS
#undef UPCALL

#define DO_UPCALL_HPY(name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_TRACKER(name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_PTR(name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_SIZE_T(name, ...) (HPy_ssize_t) (*jniEnv)->CallLongMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_INT(name, ...) (int) (*jniEnv)->CallIntMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_DOUBLE(name, ...) (double) (*jniEnv)->CallDoubleMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_LONG(name, ...) (long) (*jniEnv)->CallLongMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_VOID(name, ...) (*jniEnv)->CallVoidMethod(jniEnv, contextInstance, jniMethod_ ## name, __VA_ARGS__)

#define HPY_UP jlong
#define PTR_UP jlong
#define LONG_UP jlong
#define DOUBLE_UP jdouble
#define SIZE_T_UP jlong
#define TRACKER_UP jlong


static void * ctx_Cast_jni(HPyContext ctx, void* h) {
    return DO_UPCALL_PTR(Cast, (HPY_UP) h);
}

static void* ctx_FloatFromDouble_jni(HPyContext ctx, double v) {
    return DO_UPCALL_HPY(FloatFromDouble, (DOUBLE_UP) v);
}

static double ctx_FloatAsDouble_jni(HPyContext ctx, void* h) {
    return DO_UPCALL_DOUBLE(FloatAsDouble, (HPY_UP) h);
}

static long ctx_LongAsLong_jni(HPyContext ctx, void* h) {
    return DO_UPCALL_LONG(LongAsLong, (HPY_UP) h);
}

static void* ctx_New_jni(HPyContext ctx, void* type, void** data) {
    return DO_UPCALL_HPY(New, (HPY_UP) type, (SIZE_T_UP) data);
}

static void* ctx_GetItemi_jni(HPyContext ctx, void* obj, HPy_ssize_t idx) {
    return DO_UPCALL_HPY(GetItemi, (HPY_UP) obj, (SIZE_T_UP) idx);
}

static void ctx_Close_jni(HPyContext ctx, void* h) {
    DO_UPCALL_VOID(Close, (HPY_UP) h);
}

static void *ctx_Dup_jni(HPyContext ctx, void* h) {
    return DO_UPCALL_HPY(Dup, (HPY_UP) h);
}

static int ctx_NumberCheck_jni(HPyContext ctx, void* obj) {
    return DO_UPCALL_INT(NumberCheck, (HPY_UP) obj);
}

static int ctx_ListCheck_jni(HPyContext ctx, void* obj) {
    return DO_UPCALL_INT(ListCheck, (HPY_UP) obj);
}

static HPy_ssize_t ctx_Length_jni(HPyContext ctx, void* obj) {
    return DO_UPCALL_SIZE_T(Length, (HPY_UP) obj);
}

static void* ctx_TrackerNew_jni(HPyContext ctx, HPy_ssize_t size) {
    return DO_UPCALL_TRACKER(TrackerNew, (SIZE_T_UP) size);
}

static int ctx_TrackerAdd_jni(HPyContext ctx, void* tracker, void* obj) {
    return DO_UPCALL_INT(TrackerAdd, (jobject) tracker, (HPY_UP) obj);
}

static void  ctx_TrackerClose_jni(HPyContext ctx, void* tracker) {
    return DO_UPCALL_VOID(TrackerClose, (jobject) tracker);
}

static void* ctx_TypeGenericNew_jni(HPyContext ctx, void* type, _HPyPtr args, HPy_ssize_t nargs, void* kw) {
    return DO_UPCALL_HPY(TypeGenericNew, (HPY_UP) type);
}

//*************************
// BOXING

#define NAN_BOXING_BASE (0x0007000000000000llu)
#define NAN_BOXING_MASK (0xFFFF000000000000llu)
#define NAN_BOXING_INT (0x0001000000000000llu)
#define NAN_BOXING_INT_MASK (0x00000000FFFFFFFFllu)
#define NAN_BOXING_MAX_HANDLE (0x000000007FFFFFFFllu)

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

static uint64_t toBits(void* ptr) {
    return * ((uint64_t*) &ptr);
}

static void* toPtr(uint64_t ptr) {
    return * ((void**) &ptr);
}

//*************************
// direct fast paths that handle certain calls on the native side:

static void *(*original_Cast)(HPyContext ctx, void* h);
static void *(*original_Dup)(HPyContext ctx, void* h);
static void *(*original_FloatFromDouble)(HPyContext ctx, double v);
static double (*original_FloatAsDouble)(HPyContext ctx, void* h);
static long (*original_LongAsLong)(HPyContext ctx, void* h);
static int (*original_ListCheck)(HPyContext, void*);
static int (*original_NumberCheck)(HPyContext ctx, void* h);
static void (*original_Close)(HPyContext ctx, void* h);
static void *(*original_TrackerNew)(HPyContext ctx, HPy_ssize_t size);

static void *augment_Cast(HPyContext ctx, void* h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        void** space = (void**)ctx->_private;
        return space[unboxHandle(bits)];
    } else {
        return NULL;
    }
}

static void* augment_FloatFromDouble(HPyContext ctx, double v) {
    return toPtr(boxDouble(v));
}

static double augment_FloatAsDouble(HPyContext ctx, void* h) {
    uint64_t bits = toBits(h);
    if (isBoxedDouble(bits)) {
        return unboxDouble(bits);
    } else if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_FloatAsDouble(ctx, h);
    }
}

static long augment_LongAsLong(HPyContext ctx, void* h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_LongAsLong(ctx, h);
    }
}

static void augment_Close(HPyContext ctx, void* h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        return original_Close(ctx, h);
    }
}

static void *augment_Dup(HPyContext ctx, void* h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        return original_Dup(ctx, h);
    } else {
        return h;
    }
}

static int augment_NumberCheck(HPyContext ctx, void* obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedDouble(bits) || isBoxedInt(bits)) {
        return true;
    } else {
        return original_NumberCheck(ctx, obj);
    }
}

static int augment_ListCheck(HPyContext ctx, void* obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedHandle(bits)) {
        return original_ListCheck(ctx, obj);
    } else {
        return false;
    }
}

void initDirectFastPaths(HPyContext context) {
    LOG("%p", context);
    context->name = "augmented!";
    
    original_FloatFromDouble = context->ctx_Float_FromDouble;
    context->ctx_Float_FromDouble = augment_FloatFromDouble;
    
    original_FloatAsDouble = context->ctx_Float_AsDouble;
    context->ctx_Float_AsDouble = augment_FloatAsDouble;
    
    original_LongAsLong = context->ctx_Long_AsLong;
    context->ctx_Long_AsLong = augment_LongAsLong;
    
    original_Close = context->ctx_Close;
    context->ctx_Close = augment_Close;
    
    original_Cast = context->ctx_Cast;
    context->ctx_Cast = augment_Cast;
    
    original_Dup = context->ctx_Dup;
    context->ctx_Dup = augment_Dup;
    
    original_NumberCheck = context->ctx_Number_Check;
    context->ctx_Number_Check = augment_NumberCheck;
    
    original_ListCheck = context->ctx_List_Check;
    context->ctx_List_Check = augment_ListCheck;
}

void setHPyContextNativeSpace(HPyContext context, void** nativeSpace) {
    LOG("%p %p", context, nativeSpace);
    context->_private = nativeSpace;
}

typedef void (*destroyfunc)(void *);

JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_hpyCallDestroyFunc(JNIEnv *env, jclass clazz, jlong obj, jlong func) {
    destroyfunc f = (destroyfunc) func;
    f((void*) obj);
}

/* Initialize the jmethodID pointers for all the context functions implemented via JNI. */
JNIEXPORT void JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_initJNI(JNIEnv *env, jclass clazz, jobject ctx, jlong ctxPointer) {
    printf("hpy_native.c:initJNI\n");
    jniEnv = env;
    HPyContext context = (HPyContext) ctxPointer;

    context->ctx_Float_FromDouble = ctx_FloatFromDouble_jni;
    context->ctx_Float_AsDouble = ctx_FloatAsDouble_jni;
    context->ctx_Long_AsLong = ctx_LongAsLong_jni;
    context->ctx_Cast = ctx_Cast_jni;
    context->ctx_Close = ctx_Close_jni;

    context->ctx_Dup = ctx_Dup_jni;
    context->ctx_Number_Check = ctx_NumberCheck_jni;
    context->ctx_List_Check = ctx_ListCheck_jni;

    context->ctx_Length = ctx_Length_jni;
    context->ctx_New = ctx_New_jni;
    context->ctx_Type_GenericNew = ctx_TypeGenericNew_jni;
    
    context->ctx_GetItem_i = ctx_GetItemi_jni;
    context->ctx_Tracker_New = ctx_TrackerNew_jni;
    context->ctx_Tracker_Add = ctx_TrackerAdd_jni;
    context->ctx_Tracker_Close = ctx_TrackerClose_jni;

    contextInstance = (*env)->NewGlobalRef(env, ctx);
    jclass cls = (*env)->FindClass(env, "com/oracle/graal/python/builtins/objects/cext/hpy/GraalHPyContext");
    if (cls == NULL) {
        printf("ERROR: class GraalHPyContext not found !\n");
    } else {
        contextClass = (*env)->NewGlobalRef(env, cls);
        printf("Class GraalHPyContext found\n");
        
#define SIG_HPY "J"
#define SIG_SIZE_T "J"
#define SIG_PTR "J"
#define SIG_VOID "V"
#define SIG_INT "I"
#define SIG_LONG "J"
#define SIG_DOUBLE "D"
#define SIG_TRACKER "J"

#define UPCALL(name, jniSigArgs, jniSigRet) \
    jniMethod_ ## name = (*env)->GetMethodID(env, contextClass, "ctx" #name, "(" jniSigArgs ")" jniSigRet); \
    if (jniMethod_ ## name == NULL) printf("ERROR: jni method ctx" #name " not found found !\n");
ALL_UPCALLS
#undef UPCALL
     }
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
