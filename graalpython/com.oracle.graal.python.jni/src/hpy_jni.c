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

#include "hpy_jni.h"
#include "hpy_log.h"
#include "hpy_native_cache.h"

#include <wchar.h>
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>

//*************************
// JNI upcalls

#include "com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext.h"
#include "hpynative.h"

/* definitions for HPyTracker */
#include "hpy/runtime/ctx_funcs.h"

static JNIEnv* jniEnv;

#define ALL_FIELDS \
    FIELD(hpyHandleTable, CLASS_HPYCONTEXT, SIG_JOBJECTARRAY) \
    FIELD(hpyGlobalsTable, CLASS_HPYCONTEXT, SIG_JOBJECTARRAY) \
    FIELD(nextHandle, CLASS_HPYCONTEXT, SIG_INT)

#define FIELD(name, clazz, jniSig) static jfieldID jniField_ ## name;
ALL_FIELDS
#undef FIELD

#define ALL_UPCALLS \
    UPCALL(New, SIG_HPY SIG_PTR, SIG_HPY) \
    UPCALL(TypeGenericNew, SIG_HPY, SIG_HPY) \
    UPCALL(AsStruct, SIG_HPY, SIG_PTR) \
    UPCALL(Close, SIG_HPY, SIG_VOID) \
    UPCALL(BulkClose, SIG_PTR SIG_INT, SIG_VOID)        \
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
    UPCALL(SetItems, SIG_HPY SIG_STRING SIG_HPY, SIG_INT) \
    UPCALL(GetItems, SIG_HPY SIG_STRING, SIG_HPY) \
    UPCALL(DictNew, , SIG_HPY) \
    UPCALL(ListNew, SIG_SIZE_T, SIG_HPY) \
    UPCALL(TupleFromArray, SIG_JLONGARRAY SIG_BOOL, SIG_HPY) \
    UPCALL(GlobalLoad, SIG_HPYGLOBAL, SIG_HPY) \
    UPCALL(GlobalStore, SIG_PTR SIG_HPY, SIG_SIZE_T) \
    UPCALL(FieldLoad, SIG_HPY SIG_HPYFIELD, SIG_HPY) \
    UPCALL(FieldStore, SIG_HPY SIG_PTR SIG_HPY, SIG_SIZE_T) \
    UPCALL(Type, SIG_HPY, SIG_HPY) \
    UPCALL(TypeGetName, SIG_HPY, SIG_HPY) \
    UPCALL(ContextVarGet, SIG_HPY SIG_HPY SIG_HPY, SIG_HPY) \
    UPCALL(Is, SIG_HPY SIG_HPY, SIG_INT) \
    UPCALL(CapsuleNew, SIG_PTR SIG_PTR SIG_PTR, SIG_HPY) \
    UPCALL(CapsuleGet, SIG_HPY SIG_INT SIG_PTR, SIG_PTR)


#define UPCALL(name, jniSigArgs, jniSigRet) static jmethodID jniMethod_ ## name;
ALL_UPCALLS
#undef UPCALL

static jmethodID jniMethod_hpy_debug_get_context;

#define DO_UPCALL_HPY(jni_ctx, name, ...) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_HPY_NOARGS(jni_ctx, name) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)})
#define DO_UPCALL_TRACKER(jni_ctx, name, ...) ((HPyTracker){(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_PTR(jni_ctx, name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_PTR_NOARGS(jni_ctx, name) (void*) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)
#define DO_UPCALL_SIZE_T(jni_ctx, name, ...) (HPy_ssize_t) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_INT(jni_ctx, name, ...) (int) (*jniEnv)->CallIntMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_DOUBLE(jni_ctx, name, ...) (double) (*jniEnv)->CallDoubleMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_LONG(jni_ctx, name, ...) (long) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_VOID(jni_ctx, name, ...) (*jniEnv)->CallVoidMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)

#define HPY_UP(_h) ((jlong)((_h)._i))
#define PTR_UP jlong
#define INT_UP jint
#define LONG_UP jlong
#define DOUBLE_UP jdouble
#define SIZE_T_UP jlong
#define TRACKER_UP(_h) ((jlong)((_h)._i))

#define CONTEXT_INSTANCE(_hpy_ctx) ((jobject)(graal_hpy_context_get_native_context(_hpy_ctx)->jni_context))

#define MAX_UNCLOSED_HANDLES 32
static int32_t unclosedHandleTop = 0;
static HPy unclosedHandles[MAX_UNCLOSED_HANDLES];

static inline jsize get_handle_table_size(HPyContext *ctx) {
    return HANDLE_TABLE_SIZE(ctx->_private);
}

static uint64_t get_hpy_handle_for_object(HPyContext *ctx, jobject element, bool update_native_cache) {
    /* TODO(fa): for now, we fall back to the upcall */
    if (update_native_cache) {
        return 0;
    }

    jobjectArray hpy_handles = (jobjectArray)(*jniEnv)->GetObjectField(jniEnv, CONTEXT_INSTANCE(ctx), jniField_hpyHandleTable);
    if (hpy_handles == NULL) {
        LOGS("hpy handle table is NULL")
        return 0;
    }

    /* try to reuse a closed handle from our native list */
    jsize next_handle;
    if (unclosedHandleTop > 0) {
        uint64_t recycled = toBits(unclosedHandles[--unclosedHandleTop]);
        LOG("%llu", recycled)
        assert(recycled < INT32_MAX);
        next_handle = (jsize) recycled;
    } else {
        next_handle = (*jniEnv)->GetIntField(jniEnv, CONTEXT_INSTANCE(ctx), jniField_nextHandle);
        LOG("%d", next_handle)
        jsize s = get_handle_table_size(ctx);
        if (next_handle >= s) {
            return 0;
        }
        (*jniEnv)->SetIntField(jniEnv, CONTEXT_INSTANCE(ctx), jniField_nextHandle, next_handle+1);
    }
    (*jniEnv)->SetObjectArrayElement(jniEnv, hpy_handles, next_handle, element);
    /* TODO(fa): update native data pointer cache here (if specified) */
    return boxHandle(next_handle);
}

static jobject get_object_for_hpy_handle(HPyContext *ctx, uint64_t bits) {
    jobjectArray hpy_handles = (jobjectArray)(*jniEnv)->GetObjectField(jniEnv, CONTEXT_INSTANCE(ctx), jniField_hpyHandleTable);
    if (hpy_handles == NULL) {
        LOGS("hpy handle table is NULL")
        return NULL;
    }
    jobject element = (*jniEnv)->GetObjectArrayElement(jniEnv, (jobjectArray)hpy_handles, (jsize)unboxHandle(bits));
    if (element == NULL) {
        LOGS("handle delegate is NULL")
    }
    return element;
}


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

static HPy ctx_Global_Load_jni(HPyContext *ctx, HPyGlobal h) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), GlobalLoad, HPY_UP(h));
}

static void ctx_Global_Store_jni(HPyContext *ctx, HPyGlobal *h, HPy v) {
    h->_i = DO_UPCALL_SIZE_T(CONTEXT_INSTANCE(ctx), GlobalStore, h->_i, HPY_UP(v));
}

static HPy ctx_Field_Load_jni(HPyContext *ctx, HPy owner, HPyField field) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), FieldLoad, HPY_UP(owner), HPY_UP(field));
}

static void ctx_Field_Store_jni(HPyContext *ctx, HPy owner, HPyField *field, HPy value) {
    field->_i = DO_UPCALL_SIZE_T(CONTEXT_INSTANCE(ctx), FieldStore, HPY_UP(owner), field->_i, HPY_UP(value));
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
        char message[buffer_size];
        snprintf(message, buffer_size,
                 "'%s' object does not support item assignment", getBoxedPrimitiveName(bits));
        HPyErr_SetString(ctx, ctx->h_TypeError, message);
        return -1;
    }
    jstring jname = (*jniEnv)->NewStringUTF(jniEnv, name);
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), SetItems, target, jname, value);
}

static HPy ctx_GetItem_s_jni(HPyContext *ctx, HPy target, const char *name) {
    uint64_t bits = toBits(target);
    if (!isBoxedHandle(bits)) {
        const size_t buffer_size = 128;
        char message[buffer_size];
        snprintf(message, buffer_size,
                 "'%s' object is not subscriptable", getBoxedPrimitiveName(bits));
        return HPyErr_SetString(ctx, ctx->h_TypeError, message);
    }
    jstring jname = (*jniEnv)->NewStringUTF(jniEnv, name);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), GetItems, target, jname);
}

static HPy ctx_Type_jni(HPyContext *ctx, HPy obj) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), Type, HPY_UP(obj));
}

static const char *ctx_TypeGetName_jni(HPyContext *ctx, HPy obj) {
    return DO_UPCALL_PTR(CONTEXT_INSTANCE(ctx), TypeGetName, HPY_UP(obj));
}

static int ctx_ContextVar_Get_jni(HPyContext *ctx, HPy var, HPy def, HPy *result) {
    /* This uses 'h_Ellipsis' as an error marker assuming that it is rather uncertain that this will be a valid return
       value. If 'h_Ellipsis' is returned, this indicates an error and we explicitly check for an error then. */
    HPy err_marker = ctx->h_Ellipsis;
    HPy r = DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), ContextVarGet, HPY_UP(var), HPY_UP(def), HPY_UP(err_marker));
    if (toBits(r) == toBits(err_marker) && HPyErr_Occurred(ctx)) {
        return -1;
    }
    *result = r;
    return 0;
}

static int ctx_Is_jni(HPyContext *ctx, HPy a, HPy b) {
    uint64_t bitsA = toBits(a);
    uint64_t bitsB = toBits(b);
    if (isBoxedHandle(bitsA) && isBoxedHandle(bitsB)) {
        jobject objA = get_object_for_hpy_handle(ctx, bitsA);
        jobject objB = get_object_for_hpy_handle(ctx, bitsB);
        if (objA != NULL && objB != NULL) {
            return (int) (*jniEnv)->IsSameObject(jniEnv, objA, objB);
        }
    }
    return DO_UPCALL_INT(CONTEXT_INSTANCE(ctx), Is, HPY_UP(a), HPY_UP(b));
}

static HPy ctx_Capsule_New_jni(HPyContext *ctx, void *pointer, const char *name, HPyCapsule_Destructor destructor) {
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), CapsuleNew, (PTR_UP)pointer, (PTR_UP)name, (PTR_UP)destructor);
}

static void *ctx_Capsule_Get_jni(HPyContext *ctx, HPy capsule, _HPyCapsule_key key, const char *name) {
    return DO_UPCALL_PTR(CONTEXT_INSTANCE(ctx), CapsuleGet, HPY_UP(capsule), (INT_UP)key, (PTR_UP)name);
}


//*************************
// BOXING

static double unboxDouble(uint64_t value) {
    uint64_t doubleBits = value - NAN_BOXING_BASE;
    return * ((double*) &doubleBits);
}

static uint64_t boxDouble(double value) {
    // assumes that value doesn't contain non-standard silent NaNs
    uint64_t doubleBits = * ((uint64_t*) &value);
    return doubleBits + NAN_BOXING_BASE;
}

//*************************
// direct fast paths that handle certain calls on the native side:

static void *(*original_AsStruct)(HPyContext *ctx, HPy h);
static HPy (*original_Dup)(HPyContext *ctx, HPy h);
static HPy (*original_Long)(HPyContext *ctx, HPy h);
static HPy (*original_Float_FromDouble)(HPyContext *ctx, double v);
static double (*original_Float_AsDouble)(HPyContext *ctx, HPy h);
static long (*original_Long_AsLong)(HPyContext *ctx, HPy h);
static unsigned long (*original_Long_AsUnsignedLong)(HPyContext *ctx, HPy h);
static double (*original_Long_AsDouble)(HPyContext *ctx, HPy h);
static HPy (*original_Long_FromLong)(HPyContext *ctx, long l);
static HPy (*original_Long_FromUnsignedLong)(HPyContext *ctx, unsigned long l);
static HPy (*original_Long_FromLongLong)(HPyContext *ctx, long long l);
static HPy (*original_Long_FromUnsignedLongLong)(HPyContext *ctx, unsigned long long l);
static int (*original_List_Check)(HPyContext *ctx, HPy h);
static int (*original_Number_Check)(HPyContext *ctx, HPy h);
static int (*original_TypeCheck)(HPyContext *ctx, HPy h, HPy type);
static void (*original_Close)(HPyContext *ctx, HPy h);
static HPy (*original_Unicode_FromWideChar)(HPyContext *ctx, const wchar_t *arr, HPy_ssize_t size);
static HPy (*original_Tuple_FromArray)(HPyContext *ctx, HPy *items, HPy_ssize_t nitems);
static void (*original_Global_Store)(HPyContext *ctx, HPyGlobal *global, HPy h);
static HPy (*original_Global_Load)(HPyContext *ctx, HPyGlobal global);
static void (*original_Field_Store)(HPyContext *ctx, HPy target_object, HPyField *target_field, HPy h);
static HPy (*original_Field_Load)(HPyContext *ctx, HPy source_object, HPyField source_field);
static int (*original_Is)(HPyContext *ctx, HPy a, HPy b);
static HPy (*original_Type)(HPyContext *ctx, HPy obj);

static int augment_Is(HPyContext *ctx, HPy a, HPy b) {
    long bitsA = toBits(a);
    long bitsB = toBits(b);
    if (bitsA == bitsB) {
        return 1;
    } else if (isBoxedHandle(bitsA) && isBoxedHandle(bitsB)) {
        // This code assumes that objects pointed by a handle <= SINGLETON_HANDLES_MAX
        // always get that same handle
        long unboxedA = unboxHandle(bitsA);
        long unboxedB = unboxHandle(bitsB);
        if (unboxedA <= SINGLETON_HANDLES_MAX) {
            return 0;
        } else if (unboxedB <= SINGLETON_HANDLES_MAX) {
            return 0;
        }
        // This code assumes that space[x] != NULL <=> objects pointed by x has native struct
        void** space = (void**)ctx->_private;
        if (space[unboxedA] == NULL && space[unboxedB] == NULL) {
            return original_Is(ctx, a, b);
        }
        return space[unboxedA] == space[unboxedB];
    } else {
        return 0;
    }
}

static void *augment_AsStruct(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedHandle(bits)) {
        return get_handle_native_data_pointer(ctx, bits);
    } else {
        return NULL;
    }
}

static HPy augment_Long(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return h;
    } else if (isBoxedDouble(bits)) {
        double v = unboxDouble(bits);
        return toPtr(boxInt((int) v));
    }
    return original_Long(ctx, h);
}

static HPy augment_Float_FromDouble(HPyContext *ctx, double v) {
    return toPtr(boxDouble(v));
}

static double augment_Float_AsDouble(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedDouble(bits)) {
        return unboxDouble(bits);
    } else if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Float_AsDouble(ctx, h);
    }
}

static long augment_Long_AsLong(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Long_AsLong(ctx, h);
    }
}

static unsigned long augment_Long_AsUnsignedLong(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        int32_t unboxed = unboxInt(bits);
        if (unboxed >= 0) {
            return unboxed;
        }
    }
    return original_Long_AsUnsignedLong(ctx, h);
}

static double augment_Long_AsDouble(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Long_AsDouble(ctx, h);
    }
}

static HPy augment_Long_FromLong(HPyContext *ctx, long l) {
    if (isBoxableInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromLong(ctx, l);
    }
}

static HPy augment_Long_FromUnsignedLong(HPyContext *ctx, unsigned long l) {
    if (isBoxableUnsignedInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromUnsignedLong(ctx, l);
    }
}

static HPy augment_Long_FromLongLong(HPyContext *ctx, long long l) {
    if (isBoxableInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromLongLong(ctx, l);
    }
}

static HPy augment_Long_FromUnsignedLongLong(HPyContext *ctx, unsigned long long l) {
    if (isBoxableUnsignedInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromUnsignedLongLong(ctx, l);
    }
}

static void augment_Close(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (!bits) {
        return;
    } else if (isBoxedHandle(bits)) {
        if (bits < IMMUTABLE_HANDLES) {
            return;
        }
        if (unclosedHandleTop < MAX_UNCLOSED_HANDLES) {
            unclosedHandles[unclosedHandleTop++] = h;
        } else {
            upcallBulkClose(ctx, unclosedHandles, unclosedHandleTop);
            memset(unclosedHandles, 0, sizeof(uint64_t) * unclosedHandleTop);
            unclosedHandleTop = 0;
        }
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

static int augment_Number_Check(HPyContext *ctx, HPy obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedDouble(bits) || isBoxedInt(bits)) {
        return true;
    } else {
        return original_Number_Check(ctx, obj);
    }
}

static int augment_TypeCheck(HPyContext *ctx, HPy obj, HPy type) {
    uint64_t bits = toBits(obj);
    if (isBoxedInt(bits)) {
        return toBits(type) == toBits(ctx->h_LongType) || toBits(type) == toBits(ctx->h_BaseObjectType);
    } else if (isBoxedDouble(bits)) {
        return toBits(type) == toBits(ctx->h_FloatType) || toBits(type) == toBits(ctx->h_BaseObjectType);
    }
    return original_TypeCheck(ctx, obj, type);
}

static int augment_List_Check(HPyContext *ctx, HPy obj) {
    uint64_t bits = toBits(obj);
    if (isBoxedHandle(bits)) {
        return original_List_Check(ctx, obj);
    } else {
        return false;
    }
}

#define MAX_UNICODE 0x10ffff

static HPy augment_Unicode_FromWideChar(HPyContext *ctx, const wchar_t *u, HPy_ssize_t size) {
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
        return original_Unicode_FromWideChar(ctx, u, size);
    }
}

_HPy_HIDDEN HPy upcallTupleFromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems, jboolean steal) {
    jarray jLongArray = (*jniEnv)->NewLongArray(jniEnv, (jsize) nitems);
    (*jniEnv)->SetLongArrayRegion(jniEnv, jLongArray, 0, (jsize) nitems, (const jlong *)items);
    return DO_UPCALL_HPY(CONTEXT_INSTANCE(ctx), TupleFromArray, jLongArray, steal);
}

static HPy augment_Tuple_FromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems) {
    return upcallTupleFromArray(ctx, items, nitems, JNI_FALSE);
}

_HPy_HIDDEN void upcallBulkClose(HPyContext *ctx, HPy *items, HPy_ssize_t nitems) {
    DO_UPCALL_VOID(CONTEXT_INSTANCE(ctx), BulkClose, items, nitems);
}

HPy augment_Global_Load(HPyContext *ctx, HPyGlobal global) {
    uint64_t bits = toBits(global);
    if (bits && isBoxedHandle(bits)) {
        jobject hpy_globals = (*jniEnv)->GetObjectField(jniEnv, CONTEXT_INSTANCE(ctx), jniField_hpyGlobalsTable);
        if (hpy_globals == NULL) {
            LOGS("hpy globals is NULL")
            return HPy_NULL;
        }
        jobject element = (*jniEnv)->GetObjectArrayElement(jniEnv, (jobjectArray)hpy_globals, (jsize)unboxHandle(bits));
        if (element == NULL) {
            LOGS("globals element is NULL")
            return HPy_NULL;
        }

        uint64_t new_handle = get_hpy_handle_for_object(ctx, element, false);
        if (new_handle) {
            load_global_native_data_pointer(ctx, bits, new_handle);
            return toPtr(new_handle);
        }
        return original_Global_Load(ctx, global);
    } else {
        return toPtr(bits);
    }
}

void augment_Global_Store(HPyContext *ctx, HPyGlobal *global, HPy h) {
    uint64_t bits = toBits(h);
    if (bits && isBoxedHandle(bits)) {
        original_Global_Store(ctx, global, h);
    } else {
        global->_i = h._i;
    }
}

HPy augment_Field_Load(HPyContext *ctx, HPy source_object, HPyField source_field) {
    uint64_t bits = toBits(source_field);
    if (bits && isBoxedHandle(bits)) {
        return original_Field_Load(ctx, source_object, source_field);
    } else {
        return toPtr(bits);
    }
}

void augment_Field_Store(HPyContext *ctx, HPy target_object, HPyField *target_field, HPy h) {
    uint64_t bits = toBits(h);
    if (bits && isBoxedHandle(bits)) {
        original_Field_Store(ctx, target_object, target_field, h);
    } else {
        target_field->_i = h._i;
    }
}

HPy augment_Type(HPyContext *ctx, HPy obj) {
    long bits = toBits(obj);
    if (isBoxedInt(bits)) {
        return augment_Dup(ctx, ctx->h_LongType);
    } else if (isBoxedDouble(bits))
        return augment_Dup(ctx, ctx->h_FloatType);
    if (bits && isBoxedHandle(bits)) {
        return original_Type(ctx, obj);
    } else {
        return toPtr(bits);
    }
}

void initDirectFastPaths(HPyContext *context) {
    LOG("%p", context);
    context->name = "HPy Universal ABI (GraalVM backend, JNI)";

#define AUGMENT(name) \
    original_ ## name = context->ctx_ ## name;  \
    context->ctx_ ## name = augment_ ## name;

    AUGMENT(Long);

    AUGMENT(Float_FromDouble);

    AUGMENT(Float_AsDouble);

    AUGMENT(Long_AsLong);

    AUGMENT(Long_AsUnsignedLong);

    AUGMENT(Long_AsDouble);

    AUGMENT(Long_FromLong);

    AUGMENT(Long_FromUnsignedLong);

    AUGMENT(Long_FromLongLong);

    AUGMENT(Long_FromUnsignedLongLong);

    AUGMENT(Close);

    AUGMENT(AsStruct);

    context->ctx_AsStructLegacy = augment_AsStruct;

    AUGMENT(Dup);

    AUGMENT(Number_Check);

    AUGMENT(TypeCheck);

    AUGMENT(List_Check);

    AUGMENT(Unicode_FromWideChar);

    AUGMENT(Tuple_FromArray);

    AUGMENT(Global_Load);

    AUGMENT(Global_Store);

    AUGMENT(Field_Load);

    AUGMENT(Field_Store);

    AUGMENT(Is);

    AUGMENT(Type);

#undef AUGMENT
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
    LOG("%s", "hpy_jni.c:initJNI\n");
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

    context->ctx_Tracker_New = ctx_Tracker_New_jni;
    context->ctx_Tracker_Add = ctx_Tracker_Add_jni;
    context->ctx_Tracker_ForgetAll = ctx_Tracker_ForgetAll_jni;
    context->ctx_Tracker_Close = ctx_Tracker_Close_jni;

    context->ctx_TupleBuilder_New = ctx_TupleBuilder_New;
    context->ctx_TupleBuilder_Set = ctx_TupleBuilder_Set;
    context->ctx_TupleBuilder_Build = ctx_TupleBuilder_Build;
    context->ctx_TupleBuilder_Cancel = ctx_TupleBuilder_Cancel;

    context->ctx_Global_Load = ctx_Global_Load_jni;
    context->ctx_Global_Store = ctx_Global_Store_jni;
    context->ctx_Field_Load = ctx_Field_Load_jni;
    context->ctx_Field_Store = ctx_Field_Store_jni;

    context->ctx_SetItem_s = ctx_SetItem_s_jni;
    context->ctx_GetItem_s = ctx_GetItem_s_jni;
    context->ctx_Type = ctx_Type_jni;
    context->ctx_Type_GetName = ctx_TypeGetName_jni;

    context->ctx_ContextVar_Get = ctx_ContextVar_Get_jni;
    context->ctx_Is = ctx_Is_jni;
    context->ctx_Capsule_New = ctx_Capsule_New_jni;
    context->ctx_Capsule_Get = ctx_Capsule_Get_jni;

    graal_hpy_context_get_native_context(context)->jni_context = (void *) (*env)->NewGlobalRef(env, ctx);
    assert(clazz != NULL);

#define CLASS_HPYCONTEXT clazz

#define SIG_HPY "J"
#define SIG_HPYFIELD "J"
#define SIG_HPYGLOBAL "J"
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
#define SIG_STRING "Ljava/lang/String;"
#define SIG_JOBJECTARRAY "[Ljava/lang/Object;"

#define FIELD(name, clazz, jniSig) \
    jniField_ ## name = (*env)->GetFieldID(env, clazz, #name, jniSig); \
    if (jniField_ ## name == NULL) { \
        LOGS("ERROR: jni field " #name " not found found !\n"); \
        return 1; \
    }

ALL_FIELDS
#undef FIELD

#define UPCALL(name, jniSigArgs, jniSigRet) \
    jniMethod_ ## name = (*env)->GetMethodID(env, clazz, "ctx" #name, "(" jniSigArgs ")" jniSigRet); \
    if (jniMethod_ ## name == NULL) { \
        LOGS("ERROR: jni method ctx" #name " not found found !\n"); \
        return 1; \
    }

ALL_UPCALLS
#undef UPCALL

    jniMethod_hpy_debug_get_context = (*env)->GetMethodID(env, clazz, "getHPyDebugContext", "()" SIG_LONG);
    if (jniMethod_hpy_debug_get_context == NULL) {
        LOGS("ERROR: jni method getHPyDebugContext not found found !\n");
        return 1;
    }

    /* success */
    return 0;
}

JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_initJNIDebugContext(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNIDebugContext\n");
    HPyContext *uctx = (HPyContext *) uctxPointer;

    HPyContext *dctx = (HPyContext *) malloc(sizeof(HPyContext));
    dctx->name = "HPy Debug Mode ABI";
    dctx->_private = NULL;
    dctx->ctx_version = 1;

    hpy_debug_ctx_init(dctx, uctx);
    return (PTR_UP) dctx;
}

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_finalizeJNIDebugContext(JNIEnv *env, jclass clazz, jlong dctxPointer) {
    LOG("%s", "hpy_jni.c:finalizeJNIDebugContext\n");
    HPyContext *dctx = (HPyContext *) dctxPointer;
    hpy_debug_ctx_free(dctx);
    free(dctx);
    return 0;
}

JNIEXPORT jlong JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_initJNIDebugModule(JNIEnv *env, jclass clazz, jlong uctxPointer) {
    LOG("%s", "hpy_jni.c:initJNIDebugModule\n");
    return HPY_UP(HPyInit__debug((HPyContext *) uctxPointer));
}

HPyContext * hpy_debug_get_ctx(HPyContext *uctx)
{
    HPyContext *dctx = (HPyContext *) DO_UPCALL_PTR_NOARGS(CONTEXT_INSTANCE(uctx), hpy_debug_get_context);
    if (uctx == dctx) {
        HPy_FatalError(uctx, "hpy_debug_get_ctx: expected an universal ctx, got a debug ctx");
    }
    return dctx;
}

// helper functions

JNIEXPORT jint JNICALL Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_strcmp(JNIEnv *env, jclass clazz, jlong s1, jlong s2) {
    return (jint) strcmp((const char *)s1, (const char *)s2);
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

// HPy debug mode JNI trampolines

#define TRAMPOLINE(name) Java_com_oracle_graal_python_builtins_objects_cext_hpy_GraalHPyContext_ ## name

static inline HPy _jlong2h(jlong obj) {
    return (HPy){(HPy_ssize_t)obj};
}

static inline jlong _h2jlong(HPy h) {
    return (jlong)(h._i);
}

static inline DHPy _jlong2dh(HPyContext *dctx, jlong obj)
{
    return DHPy_open(dctx, _jlong2h(obj));
}

static inline jlong _dh2jlong(HPyContext *dctx, DHPy dh)
{
    return _h2jlong(DHPy_unwrap(dctx, dh));
}

static inline jlong from_dh(HPyContext *dctx, DHPy dh_result)
{
    jlong result = _dh2jlong(dctx, dh_result);
    DHPy_close(dctx, dh_result);
    return result;
}

/* just for better readability */
typedef HPy_buffer DHPy_buffer;
typedef HPy_buffer UHPy_buffer;

/* Copies everything from 'src' to 'dest' and unwraps the 'obj' debug handle. */
static inline void
_buffer_d2u(HPyContext *dctx, const DHPy_buffer *src, UHPy_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = DHPy_unwrap(dctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}

/* Copies everything from 'src' to 'dest' and opens a debug handle for 'obj'. */
static inline void
_buffer_u2d(HPyContext *dctx, const UHPy_buffer *src, DHPy_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = DHPy_open(dctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}


JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugModuleInit)(JNIEnv *env, jclass clazz, jlong target, jlong ctx) {
    HPyContext *dctx = (HPyContext *) ctx;
    return from_dh(dctx, ((DHPy (*)(HPyContext *)) target)(dctx));
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugUnaryFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_unaryfunc f = (HPyFunc_unaryfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_result = f(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg1);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugLenFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_lenfunc f = (HPyFunc_lenfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    jlong result = (jlong) f(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg1);
    return result;
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugBinaryFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_binaryfunc f = (HPyFunc_binaryfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    DHPy dh_result = f(dctx, dh_arg1, dh_arg2);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugGetattrFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_getattrfunc f = (HPyFunc_getattrfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_result = f(dctx, dh_arg1, (char *) arg2);
    DHPy_close_and_check(dctx, dh_arg1);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugVarargs)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong self, jlong args, jlong nargs) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_varargs f = (HPyFunc_varargs) target;
    jlong *uh_args = (jlong *) args;
    DHPy dh_self = _jlong2dh(dctx, self);
    DHPy *dh_args = (DHPy *)alloca(nargs * sizeof(DHPy));
    for (jlong i = 0; i < nargs; i++) {
        dh_args[i] = _jlong2dh(dctx, uh_args[i]);
    }
    DHPy dh_result = f(dctx, dh_self, dh_args, (HPy_ssize_t) nargs);
    DHPy_close_and_check(dctx, dh_self);
    for (jlong i = 0; i < nargs; i++) {
        DHPy_close_and_check(dctx, dh_args[i]);
    }
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugTernaryFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_ternaryfunc f = (HPyFunc_ternaryfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    DHPy dh_arg3 = _jlong2dh(dctx, arg3);
    DHPy dh_result = f(dctx, dh_arg1, dh_arg2, dh_arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    DHPy_close_and_check(dctx, dh_arg3);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugSsizeSsizeArgFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_ssizessizeargfunc f = (HPyFunc_ssizessizeargfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_result = f(dctx, dh_arg1, (HPy_ssize_t) arg2, (HPy_ssize_t) arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugKeywords)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong self, jlong args, jlong nargs, jlong kw) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_keywords f = (HPyFunc_keywords) target;
    jlong *uh_args = (jlong *) args;
    DHPy dh_self = _jlong2dh(dctx, self);
    DHPy *dh_args = (DHPy *)alloca(nargs * sizeof(DHPy));
    for (jlong i = 0; i < nargs; i++) {
        dh_args[i] = _jlong2dh(dctx, uh_args[i]);
    }
    DHPy dh_kw = _jlong2dh(dctx, kw);
    DHPy dh_result = f(dctx, dh_self, dh_args, (HPy_ssize_t) nargs, dh_kw);
    DHPy_close_and_check(dctx, dh_self);
    for (jlong i = 0; i < nargs; i++) {
        DHPy_close_and_check(dctx, dh_args[i]);
    }
    DHPy_close_and_check(dctx, dh_kw);
    return from_dh(dctx, dh_result);
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugInquiry)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_inquiry f = (HPyFunc_inquiry) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    jint result = f(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg1);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugSsizeobjargproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_ssizeobjargproc f = (HPyFunc_ssizeobjargproc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg3 = _jlong2dh(dctx, arg3);
    jint result = f(dctx, dh_arg1, (HPy_ssize_t) arg2, dh_arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg3);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugSsizesizeobjargproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3, jlong arg4) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_ssizessizeobjargproc f = (HPyFunc_ssizessizeobjargproc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg4 = _jlong2dh(dctx, arg4);
    jint result = f(dctx, dh_arg1, (HPy_ssize_t) arg2, (HPy_ssize_t) arg3, dh_arg4);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg4);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugSetter)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_setter f = (HPyFunc_setter) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    jint result = f(dctx, dh_arg1, dh_arg2, (char *)arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugObjobjproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_objobjproc f = (HPyFunc_objobjproc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    jint result = f(dctx, dh_arg1, dh_arg2);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugObjobjargproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_objobjargproc f = (HPyFunc_objobjargproc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    DHPy dh_arg3 = _jlong2dh(dctx, arg3);
    jint result = f(dctx, dh_arg1, dh_arg2, dh_arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    DHPy_close_and_check(dctx, dh_arg3);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugSetattrFunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jlong arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_setattrfunc f = (HPyFunc_setattrfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg3 = _jlong2dh(dctx, arg3);
    jint result = f(dctx, dh_arg1, (char *)arg2, dh_arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg3);
    return result;
}

JNIEXPORT jint JNICALL TRAMPOLINE(executeDebugInitproc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong self, jlong args, jlong nargs, jlong kw) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_initproc f = (HPyFunc_initproc) target;
    jlong *uh_args = (jlong *) args;
    DHPy dh_self = _jlong2dh(dctx, self);
    DHPy *dh_args = (DHPy *)alloca(nargs * sizeof(DHPy));
    for (jlong i = 0; i < nargs; i++) {
        dh_args[i] = _jlong2dh(dctx, uh_args[i]);
    }
    DHPy dh_kw = _jlong2dh(dctx, kw);
    jint result = f(dctx, dh_self, dh_args, (HPy_ssize_t) nargs, dh_kw);
    DHPy_close_and_check(dctx, dh_self);
    for (jlong i = 0; i < nargs; i++) {
        DHPy_close_and_check(dctx, dh_args[i]);
    }
    DHPy_close_and_check(dctx, dh_kw);
    return result;
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

JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugRichcomparefunc)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1, jlong arg2, jint arg3) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_richcmpfunc f = (HPyFunc_richcmpfunc) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    DHPy dh_arg2 = _jlong2dh(dctx, arg2);
    DHPy dh_result = f(dctx, dh_arg1, dh_arg2, (int) arg3);
    DHPy_close_and_check(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg2);
    return from_dh(dctx, dh_result);
}

JNIEXPORT void JNICALL TRAMPOLINE(executeDebugDestructor)(JNIEnv *env, jclass clazz, jlong target, jlong ctx, jlong arg1) {
    HPyContext *dctx = (HPyContext *) ctx;
    HPyFunc_destructor f = (HPyFunc_destructor) target;
    DHPy dh_arg1 = _jlong2dh(dctx, arg1);
    f(dctx, dh_arg1);
    DHPy_close_and_check(dctx, dh_arg1);
}
