/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "hpy_native_fast_paths.h"
#include "hpy_native_cache.h"

#include <assert.h>
#include <stdint.h>

#define MAX_UNCLOSED_HANDLES 32
static int32_t unclosedHandleTop = 0;
static HPy unclosedHandles[MAX_UNCLOSED_HANDLES];

//*************************
// BOXING

static_assert(sizeof(uint64_t) == sizeof(double), "Assumption necessary for NaN boxing to work");

static inline double unboxDouble(uint64_t value) {
    double result;
    uint64_t unboxed = value - NAN_BOXING_BASE;
    memcpy(&result, &unboxed, sizeof(double));
    return result;
}

static inline uint64_t boxDouble(double value) {
    // assumes that value doesn't contain non-standard silent NaNs
    uint64_t unboxed;
    memcpy(&unboxed, &value, sizeof(double));
    return unboxed + NAN_BOXING_BASE;
}

//*************************
// direct fast paths that handle certain calls on the native side:

static void *(*original_AsStruct_Object)(HPyContext *ctx, HPy h);
static HPy (*original_Dup)(HPyContext *ctx, HPy h);
static HPy (*original_Long)(HPyContext *ctx, HPy h);
static HPy (*original_Float_FromDouble)(HPyContext *ctx, double v);
static double (*original_Float_AsDouble)(HPyContext *ctx, HPy h);
static int32_t (*original_Long_AsInt32_t)(HPyContext *ctx, HPy h);
static int64_t (*original_Long_AsInt64_t)(HPyContext *ctx, HPy h);
static uint32_t (*original_Long_AsUInt32_t)(HPyContext *ctx, HPy h);
static size_t (*original_Long_AsSize_t)(HPyContext *ctx, HPy h);
static HPy_ssize_t (*original_Long_AsSsize_t)(HPyContext *ctx, HPy h);
static double (*original_Long_AsDouble)(HPyContext *ctx, HPy h);
static HPy (*original_Long_FromInt32_t)(HPyContext *ctx, int32_t l);
static HPy (*original_Long_FromUInt32_t)(HPyContext *ctx, uint32_t l);
static HPy (*original_Long_FromInt64_t)(HPyContext *ctx, int64_t l);
static HPy (*original_Long_FromUInt64_t)(HPyContext *ctx, uint64_t l);
static HPy (*original_Long_FromSsize_t)(HPyContext *ctx, HPy_ssize_t l);
static HPy (*original_Long_FromSize_t)(HPyContext *ctx, size_t l);
static int (*original_List_Check)(HPyContext *ctx, HPy h);
static int (*original_Number_Check)(HPyContext *ctx, HPy h);
static int (*original_TypeCheck)(HPyContext *ctx, HPy h, HPy type);
static void (*original_Close)(HPyContext *ctx, HPy h);
static void (*original_Global_Store)(HPyContext *ctx, HPyGlobal *global, HPy h);
static HPy (*original_Global_Load)(HPyContext *ctx, HPyGlobal global);
static void (*original_Field_Store)(HPyContext *ctx, HPy target_object, HPyField *target_field, HPy h);
static HPy (*original_Field_Load)(HPyContext *ctx, HPy source_object, HPyField source_field);
static int (*original_Is)(HPyContext *ctx, HPy a, HPy b);
static int (*original_IsTrue)(HPyContext *ctx, HPy h);
static HPy (*original_Type)(HPyContext *ctx, HPy obj);
static HPy (*original_Add)(HPyContext *ctx, HPy h1, HPy h2);
static HPy (*original_Subtract)(HPyContext *ctx, HPy h1, HPy h2);
static HPy (*original_Multiply)(HPyContext *ctx, HPy h1, HPy h2);
static HPy (*original_FloorDivide)(HPyContext *ctx, HPy h1, HPy h2);
static HPy (*original_TrueDivide)(HPyContext *ctx, HPy h1, HPy h2);
static HPy (*original_RichCompare)(HPyContext *ctx, HPy v, HPy w, int op);
static int (*original_RichCompareBool)(HPyContext *ctx, HPy v, HPy w, int op);

static int augment_Is(HPyContext *ctx, HPy a, HPy b) {
    uint64_t bitsA = toBits(a);
    uint64_t bitsB = toBits(b);
    if (bitsA == bitsB) {
        return 1;
    } else if (isBoxedHandle(bitsA) && isBoxedHandle(bitsB)) {
        // This code assumes that objects pointed by a handle <= SINGLETON_HANDLES_MAX
        // always get that same handle
        uint64_t unboxedA = unboxHandle(bitsA);
        uint64_t unboxedB = unboxHandle(bitsB);
        if (unboxedA <= SINGLETON_HANDLES_MAX) {
            return 0;
        } else if (unboxedB <= SINGLETON_HANDLES_MAX) {
            return 0;
        }
        // This code assumes that space[x] != NULL <=> objects pointed by x has native struct
        void *dataA = get_handle_native_data_pointer(ctx, unboxedA);
        void *dataB = get_handle_native_data_pointer(ctx, unboxedB);
        if (dataA == NULL && dataB == NULL) {
            return original_Is(ctx, a, b);
        }
        return dataA == dataB;
    } else {
        return 0;
    }
}

static int augment_IsTrue(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits) != 0;
    } else if (isBoxedDouble(bits)) {
        return unboxDouble(bits) != 0.0;
    } else if (augment_Is(ctx, ctx->h_None, h)) {
        return 0;
    }
    return original_IsTrue(ctx, h);
}

static void *augment_AsStruct_Object(HPyContext *ctx, HPy h) {
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

static int32_t augment_Long_AsInt32_t(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Long_AsInt32_t(ctx, h);
    }
}

static int64_t augment_Long_AsInt64_t(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Long_AsInt64_t(ctx, h);
    }
}

static uint32_t augment_Long_AsUInt32_t(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        int32_t unboxed = unboxInt(bits);
        if (unboxed >= 0) {
            return unboxed;
        }
    }
    return original_Long_AsUInt32_t(ctx, h);
}

static HPy_ssize_t augment_Long_AsSsize_t(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        int32_t unboxed = unboxInt(bits);
        if (unboxed >= 0) {
            return unboxed;
        }
    }
    return original_Long_AsSsize_t(ctx, h);
}

static size_t augment_Long_AsSize_t(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        int32_t unboxed = unboxInt(bits);
        if (unboxed >= 0) {
            return unboxed;
        }
    }
    return original_Long_AsSize_t(ctx, h);
}

static double augment_Long_AsDouble(HPyContext *ctx, HPy h) {
    uint64_t bits = toBits(h);
    if (isBoxedInt(bits)) {
        return unboxInt(bits);
    } else {
        return original_Long_AsDouble(ctx, h);
    }
}

static HPy augment_Long_FromInt32_t(HPyContext *ctx, int32_t l) {
    return toPtr(boxInt(l));
}

static HPy augment_Long_FromUInt32_t(HPyContext *ctx, uint32_t l) {
    if (isBoxableUnsignedInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromUInt32_t(ctx, l);
    }
}

static HPy augment_Long_FromInt64_t(HPyContext *ctx, int64_t l) {
    if (isBoxableInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromInt64_t(ctx, l);
    }
}

static HPy augment_Long_FromUInt64_t(HPyContext *ctx, uint64_t l) {
    if (isBoxableUnsignedInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromUInt64_t(ctx, l);
    }
}

static HPy augment_Long_FromSsize_t(HPyContext *ctx, HPy_ssize_t l) {
    if (isBoxableInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromSsize_t(ctx, l);
    }
}

static HPy augment_Long_FromSize_t(HPyContext *ctx, size_t l) {
    if (isBoxableUnsignedInt(l)) {
        return toPtr(boxInt((int32_t) l));
    } else {
        return original_Long_FromSize_t(ctx, l);
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

HPy augment_Global_Load(HPyContext *ctx, HPyGlobal global) {
    uint64_t bits = toBits(global);
    if (bits && isBoxedHandle(bits)) {
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
    uint64_t bits = toBits(obj);
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

#define GENERATE_AUGMENTED_BINOP(NAME, OP) \
    static HPy augment_##NAME(HPyContext *ctx, HPy h1, HPy h2) { \
        uint64_t bits1 = toBits(h1); \
        uint64_t bits2 = toBits(h2); \
        if (isBoxedInt(bits1) && isBoxedInt(bits2)) { \
            int64_t i1 = (int64_t) unboxInt(bits1); \
            int64_t i2 = (int64_t) unboxInt(bits2); \
            return augment_Long_FromInt64_t(ctx, i1 OP i2); \
        } else if (isBoxedInt(bits1) && isBoxedDouble(bits2)) { \
            int32_t i1 = unboxInt(bits1); \
            double f2 = unboxDouble(bits2); \
            return augment_Float_FromDouble(ctx, i1 OP f2); \
        } else if (isBoxedDouble(bits1) && isBoxedInt(bits2)) { \
            double f1 = unboxDouble(bits1); \
            int32_t i2 = unboxInt(bits2); \
            return augment_Float_FromDouble(ctx, f1 OP i2); \
        } else if (isBoxedDouble(bits1) && isBoxedDouble(bits2)) { \
            double f1 = unboxDouble(bits1); \
            double f2 = unboxDouble(bits2); \
            return augment_Float_FromDouble(ctx, f1 OP f2); \
        } \
        return original_##NAME(ctx, h1, h2); \
    }

GENERATE_AUGMENTED_BINOP(Add, +)
GENERATE_AUGMENTED_BINOP(Subtract, -)
GENERATE_AUGMENTED_BINOP(Multiply, *)

static HPy augment_FloorDivide(HPyContext *ctx, HPy h1, HPy h2) {
    uint64_t bits1 = toBits(h1);
    uint64_t bits2 = toBits(h2);
    if (isBoxedInt(bits1) && isBoxedInt(bits2)) {
        int32_t i1 = unboxInt(bits1);
        int32_t i2 = unboxInt(bits2);
        if (i2 == 0) {
            HPyErr_SetString(ctx, ctx->h_ZeroDivisionError, "division by zero");
            return HPy_NULL;
        }
        return augment_Long_FromInt64_t(ctx, i1 / i2);
    }
    return original_FloorDivide(ctx, h1, h2);
}

static HPy augment_TrueDivide(HPyContext *ctx, HPy h1, HPy h2) {
    uint64_t bits1 = toBits(h1);
    uint64_t bits2 = toBits(h2);
    if (isBoxedInt(bits1) && isBoxedInt(bits2)) {
        int32_t i2 = unboxInt(bits2);
        if (i2 == 0) {
            goto div_by_zero;
        }
        double f1 = (double) unboxInt(bits1);
        return augment_Float_FromDouble(ctx, f1 / i2);
    } else if (isBoxedInt(bits1) && isBoxedDouble(bits2)) {
        int32_t i1 = unboxInt(bits1);
        double f2 = unboxDouble(bits2);
        if (f2 == 0.0) {
            goto div_by_zero;
        }
        return augment_Float_FromDouble(ctx, i1 / f2);
    } else if (isBoxedDouble(bits1) && isBoxedInt(bits2)) {
        double f1 = unboxDouble(bits1);
        int32_t i2 = unboxInt(bits2);
        if (i2 == 0) {
            goto div_by_zero;
        }
        return augment_Float_FromDouble(ctx, f1 / i2);
    } else if (isBoxedDouble(bits1) && isBoxedDouble(bits2)) {
        double f1 = unboxDouble(bits1);
        double f2 = unboxDouble(bits2);
        if (f2 == 0.0) {
            goto div_by_zero;
        }
        return augment_Float_FromDouble(ctx, f1 / f2);
    }
    return original_TrueDivide(ctx, h1, h2);
div_by_zero:
    HPyErr_SetString(ctx, ctx->h_ZeroDivisionError, "division by zero");
    return HPy_NULL;
}

#define HPy_RETURN_RICHCOMPARE_BOOL(ctx, val1, val2, op)                \
    do {                                                                \
        int result;                                                     \
        switch (op) {                                                   \
        case HPy_EQ: result = ((val1) == (val2)); break;                \
        case HPy_NE: result = ((val1) != (val2)); break;                \
        case HPy_LT: result = ((val1) <  (val2)); break;                \
        case HPy_GT: result = ((val1) >  (val2)); break;                \
        case HPy_LE: result = ((val1) <= (val2)); break;                \
        case HPy_GE: result = ((val1) >= (val2)); break;                \
        default:                                                        \
            HPy_FatalError(ctx, "Invalid value for HPy_RichCmpOp");     \
        }                                                               \
        return result;                                                  \
    } while (0)

static inline int richcompare_boxed_values(HPyContext *ctx, uint64_t vbits, uint64_t wbits, int op) {
    if (isBoxedInt(vbits)) {
        if (isBoxedInt(wbits)) {
            HPy_RETURN_RICHCOMPARE_BOOL(ctx, unboxInt(vbits), unboxInt(wbits), op);
        } else {
            assert(isBoxedDouble(wbits));
            HPy_RETURN_RICHCOMPARE_BOOL(ctx, unboxInt(vbits), unboxDouble(wbits), op);
        }
    } else {
        assert(isBoxedDouble(vbits));
        if (isBoxedInt(wbits)) {
            HPy_RETURN_RICHCOMPARE_BOOL(ctx, unboxDouble(vbits), unboxInt(wbits), op);
        } else {
            assert(isBoxedDouble(wbits));
            HPy_RETURN_RICHCOMPARE_BOOL(ctx, unboxDouble(vbits), unboxDouble(wbits), op);
        }
    }
}

static HPy augment_RichCompare(HPyContext *ctx, HPy v, HPy w, int op) {
    uint64_t vbits = toBits(v);
    uint64_t wbits = toBits(w);
    int result;
    if (!isBoxedHandle(vbits) && !isBoxedHandle(wbits)) {
        result = richcompare_boxed_values(ctx, vbits, wbits, op);
        if (result)
            return HPy_Dup(ctx, ctx->h_True);
        return HPy_Dup(ctx, ctx->h_False);
    }
    return original_RichCompare(ctx, v, w, op);
}

static int augment_RichCompareBool(HPyContext *ctx, HPy v, HPy w, int op) {
    uint64_t vbits = toBits(v);
    uint64_t wbits = toBits(w);
    if (!isBoxedHandle(vbits) && !isBoxedHandle(wbits)) {
        return richcompare_boxed_values(ctx, vbits, wbits, op);
    }
    return original_RichCompareBool(ctx, v, w, op);
}

void init_native_fast_paths(HPyContext *context) {
    LOG("%p", context);

#define AUGMENT(name) \
    original_ ## name = context->ctx_ ## name;  \
    context->ctx_ ## name = augment_ ## name;

    AUGMENT(Float_FromDouble);
    AUGMENT(Float_AsDouble);

    AUGMENT(Long);
    AUGMENT(Long_AsInt32_t);
    AUGMENT(Long_AsInt64_t);
    AUGMENT(Long_AsUInt32_t);
    AUGMENT(Long_AsDouble);
    AUGMENT(Long_AsSsize_t);
    AUGMENT(Long_AsSize_t);
    AUGMENT(Long_FromInt32_t);
    AUGMENT(Long_FromUInt32_t);
    AUGMENT(Long_FromInt64_t);
    AUGMENT(Long_FromUInt64_t);
    AUGMENT(Long_FromSsize_t);
    AUGMENT(Long_FromSize_t);

    AUGMENT(Dup);
    AUGMENT(Close);

    AUGMENT(AsStruct_Object);
    context->ctx_AsStruct_Legacy = augment_AsStruct_Object;
    context->ctx_AsStruct_Float = augment_AsStruct_Object;
    context->ctx_AsStruct_List = augment_AsStruct_Object;
    context->ctx_AsStruct_Long = augment_AsStruct_Object;
    context->ctx_AsStruct_Type = augment_AsStruct_Object;
    context->ctx_AsStruct_Unicode = augment_AsStruct_Object;
    context->ctx_AsStruct_Tuple = augment_AsStruct_Object;


    AUGMENT(Number_Check);

    AUGMENT(TypeCheck);

    AUGMENT(List_Check);

    AUGMENT(Global_Load);
    AUGMENT(Global_Store);

    AUGMENT(Field_Load);
    AUGMENT(Field_Store);

    AUGMENT(Is);
    AUGMENT(IsTrue);

    AUGMENT(Type);

    AUGMENT(Add);
    AUGMENT(Subtract);
    AUGMENT(Multiply);
    AUGMENT(FloorDivide);
    AUGMENT(TrueDivide);

    AUGMENT(RichCompare);
    AUGMENT(RichCompareBool);

#undef AUGMENT
}
