/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "hpy.h"
#include "hpytypes.h"
#include "structmember.h"
#include <graalvm/llvm/polyglot.h>
#include <truffle.h>

#include <wchar.h>
#include <string.h>
#include <errno.h>


#define SRC_CS "utf-8"
#define UNWRAP(_h) ((_h)._i)
#define WRAP(_ptr) ((HPy){(_ptr)})

/* References to the managed HPy contexts (Java objects). */
static HPyContext *g_universal_ctx;
static HPyContext *g_debug_ctx;

typedef HPyDef* HPyDefPtr;
typedef PyMemberDef cpy_PyMemberDef;
typedef PyGetSetDef cpy_PyGetSetDef;
typedef PyType_Slot cpy_PyTypeSlot;

POLYGLOT_DECLARE_TYPE(HPy)
POLYGLOT_DECLARE_TYPE(HPyField)
POLYGLOT_DECLARE_TYPE(HPyGlobal)
POLYGLOT_DECLARE_TYPE(HPyContext)
POLYGLOT_DECLARE_TYPE(HPyThreadState)
POLYGLOT_DECLARE_TYPE(HPyDef)
POLYGLOT_DECLARE_TYPE(HPyDef_Kind)
POLYGLOT_DECLARE_TYPE(HPyDefPtr)
POLYGLOT_DECLARE_TYPE(HPySlot)
POLYGLOT_DECLARE_TYPE(HPyMeth)
POLYGLOT_DECLARE_TYPE(HPyFunc_Signature)
POLYGLOT_DECLARE_TYPE(cpy_PyMethodDef)
POLYGLOT_DECLARE_TYPE(cpy_PyMemberDef)
POLYGLOT_DECLARE_TYPE(cpy_PyTypeSlot)
POLYGLOT_DECLARE_TYPE(cpy_PyGetSetDef)
POLYGLOT_DECLARE_TYPE(HPyMember_FieldType)
POLYGLOT_DECLARE_TYPE(HPyMember)
POLYGLOT_DECLARE_TYPE(HPyGetSet)
POLYGLOT_DECLARE_TYPE(HPyModuleDef)
POLYGLOT_DECLARE_TYPE(wchar_t)
POLYGLOT_DECLARE_TYPE(HPyType_Spec)
POLYGLOT_DECLARE_TYPE(HPyType_SpecParam)
POLYGLOT_DECLARE_TYPE(HPyCapsule_Destructor)

int Py_EXPORTED_SYMBOL graal_hpy_init(HPyContext *context, void *initObject) {
	// save context in global for NFI upcalls
	g_universal_ctx = context;

	// register the native type of HPy
	polyglot_invoke(initObject, "setHPyContextNativeType", polyglot_HPyContext_typeid());
	polyglot_invoke(initObject, "setHPyNativeType", polyglot_HPy_typeid());
	polyglot_invoke(initObject, "setHPyFieldNativeType", polyglot_HPyField_typeid());
	polyglot_invoke(initObject, "setHPyArrayNativeType", polyglot_array_typeid(polyglot_HPy_typeid(), 0));

	// register size of wchar_t
	polyglot_invoke(initObject, "setWcharSize", sizeof(wchar_t));

	return 0;
}

void *graal_hpy_long2ptr(int64_t lval) {
	return (void *)lval;
}

/* Call from Java if the HPy debug context is initialized. */
void graal_hpy_set_debug_context(HPyContext *debug_ctx) {
	g_debug_ctx = debug_ctx;
}

void* graal_hpy_calloc(size_t count, size_t eltsize) {
	return calloc(count, eltsize);
}

void graal_hpy_free(void *ptr) {
	free(ptr);
}

char* graal_hpy_strdup(const char *ptr) {
    return strdup(ptr);
}

char* graal_hpy_type_name(const char *ptr) {
    const char *s = strrchr(ptr, '.');
    if (s == NULL) {
        s = ptr;
    }
    else {
        s++;
    }
    return strdup(s);
}

void* graal_hpy_from_HPy_array(void *arr, uint64_t len) {
	return polyglot_from_HPy_array(arr, len);
}

void* graal_hpy_from_i8_array(void *arr, uint64_t len) {
	return polyglot_from_i8_array(arr, len);
}

void* graal_hpy_from_wchar_array(wchar_t *arr, uint64_t len) {
    if (len == -1) {
        len = (uint64_t) wcslen(arr);
    }
	return polyglot_from_wchar_t_array(arr, len);
}

void* graal_hpy_from_HPyMeth(void *ptr) {
	return polyglot_from_HPyMeth(ptr);
}

void* graal_hpy_from_HPyModuleDef(void *ptr) {
	return polyglot_from_HPyModuleDef(ptr);
}

void* graal_hpy_from_HPyType_Spec(HPyType_Spec *ptr) {
	return polyglot_from_HPyType_Spec(ptr);
}

void* graal_hpy_from_HPyType_SpecParam_array(HPyType_SpecParam *ptr) {
	if (ptr != NULL) {
	    uint64_t len=0;
	    while (ptr[len].kind) {
		    len++;
	    }
	    return polyglot_from_HPyType_SpecParam_array(ptr, len);
	}
	return NULL;
}

void* graal_hpy_from_HPyCapsule_Destructor(void *ptr) {
	return polyglot_from_HPyCapsule_Destructor(ptr);
}


void* graal_hpy_get_m_doc(HPyModuleDef *moduleDef) {
	const char *m_doc = moduleDef->doc;
	if (m_doc) {
		return polyglot_from_string(m_doc, SRC_CS);
	}
	return polyglot_from_string("", SRC_CS);
}

/* getters for HPyMeth */

void* graal_hpy_get_ml_name(HPyMeth *methodDef) {
	return polyglot_from_string(methodDef->name, SRC_CS);
}

void* graal_hpy_get_ml_doc(HPyMeth *methodDef) {
	return polyglot_from_string(methodDef->doc, SRC_CS);
}

HPyFunc_Signature graal_hpy_meth_get_signature(HPyMeth *methodDef) {
	return methodDef->signature;
}

void* graal_hpy_get_field_i(HPyField *hf) {
	return hf->_i;
}

void graal_hpy_set_field_i(HPyField *hf, void* i) {
	hf->_i = i;
}

void* graal_hpy_get_global_i(HPyGlobal *hg) {
	return hg->_i;
}

void graal_hpy_set_global_i(HPyGlobal *hg, void* i) {
	hg->_i = i;
}

/* getters for HPySlot */

HPySlot_Slot graal_hpy_slot_get_slot(HPySlot *slot) {
	return slot->slot;
}

/* getters for PyType_Slot */

int graal_hpy_legacy_slot_get_slot(cpy_PyTypeSlot *slot) {
	return slot->slot;
}

void* graal_hpy_legacy_slot_get_pfunc(cpy_PyTypeSlot *slot) {
	return slot->pfunc;
}

void* graal_hpy_legacy_slot_get_methods(cpy_PyTypeSlot *slot) {
	uint64_t len=0;
	cpy_PyMethodDef *legacy_methods = (cpy_PyMethodDef *) slot->pfunc;
	if (legacy_methods != NULL) {
		while ((legacy_methods[len]).ml_name != NULL) {
			len++;
		}
		return polyglot_from_cpy_PyMethodDef_array(legacy_methods, len);
	}
	return NULL;
}

void* graal_hpy_legacy_slot_get_members(cpy_PyTypeSlot *slot) {
	uint64_t len=0;
	cpy_PyMemberDef *legacy_members = (cpy_PyMemberDef *) slot->pfunc;
	if (legacy_members != NULL) {
		while ((legacy_members[len]).name != NULL) {
			len++;
		}
		return polyglot_from_cpy_PyMemberDef_array(legacy_members, len);
	}
	return NULL;
}

void* graal_hpy_legacy_slot_get_descrs(cpy_PyTypeSlot *slot) {
	uint64_t len=0;
	cpy_PyGetSetDef *legacy_getset = (cpy_PyGetSetDef *) slot->pfunc;
	if (legacy_getset != NULL) {
		while (legacy_getset[len].name != NULL) {
			len++;
		}
		return polyglot_from_cpy_PyGetSetDef_array(legacy_getset, len);
	}
	return NULL;
}

/* getters for legacy cpy_PyMethodDef */

void* graal_hpy_legacy_methoddef_get_ml_name(cpy_PyMethodDef *methodDef) {
	return polyglot_from_string(methodDef->ml_name, SRC_CS);
}

/* getters for legacy cpy_PyGetSetDef */

void* graal_hpy_legacy_getsetdef_get_name(cpy_PyGetSetDef *getSetDef) {
	return polyglot_from_string(getSetDef->name, SRC_CS);
}

/* getters for HPyDef */

int graal_hpy_def_get_kind(HPyDef *def) {
	return def->kind;
}

void* graal_hpy_def_get_slot(HPyDef *def) {
	return polyglot_from_HPySlot(&(def->slot));
}

void* graal_hpy_def_get_meth(HPyDef *def) {
	return polyglot_from_HPyMeth(&(def->meth));
}

void* graal_hpy_def_get_member(HPyDef *def) {
	return polyglot_from_HPyMember(&(def->member));
}

void* graal_hpy_def_get_getset(HPyDef *def) {
	return polyglot_from_HPyGetSet(&(def->getset));
}

/* getters for HPyMember */

HPyMember_FieldType graal_hpy_member_get_type(HPyMember *member_def) {
	return member_def->type;
}

/* getters for HPyType_SpecParam */

HPyType_SpecParam_Kind graal_hpy_HPyType_SpecParam_get_kind(HPyType_SpecParam *def) {
	return def->kind;
}

void* graal_hpy_HPyType_SpecParam_get_object(HPyType_SpecParam *def) {
	return UNWRAP(def->object);
}

/* getters for HPyModuleDef */

void* graal_hpy_module_get_legacy_methods(HPyModuleDef *moduleDef) {
	uint64_t len=0;
	if (moduleDef->legacy_methods != NULL) {
		while ((moduleDef->legacy_methods[len]).ml_name != NULL) {
			len++;
		}
		return polyglot_from_cpy_PyMethodDef_array(moduleDef->legacy_methods, len);
	}
	return NULL;
}

void* graal_hpy_module_get_defines(HPyModuleDef *moduleDef) {
	uint64_t len=0;
	if (moduleDef->defines) {
	    while (moduleDef->defines[len] != NULL) {
		    len++;
	    }
	    return polyglot_from_HPyDefPtr_array(moduleDef->defines, len);
	}
	return NULL;
}

uint64_t graal_hpy_module_init_globals(HPyModuleDef *moduleDef, intptr_t startID) {
	uint64_t len=0;
	if (moduleDef->globals) {
	    while (moduleDef->globals[len] != NULL) {
            moduleDef->globals[len]->_i = startID++;
		    len++;
	    }
	    return len;
	}
	return 0;
}

void* graal_hpy_from_string(const char *ptr) {
	return polyglot_from_string(ptr, SRC_CS);
}

int graal_hpy_get_errno() {
	return errno;
}

char *graal_hpy_get_strerror(int i) {
	if (i != 0) {
		return strerror(i);
	}
	return "Error";
}

uint64_t graal_hpy_strlen(const char *ptr) {
	return strlen(ptr);
}

/* getters for HPyType_Spec */

void* graal_hpy_type_spec_get_defines(HPyType_Spec *type_spec) {
	uint64_t len=0;
	if (type_spec->defines) {
	    while (type_spec->defines[len] != NULL) {
		    len++;
	    }
	    return polyglot_from_HPyDefPtr_array(type_spec->defines, len);
	}
	return NULL;
}

void* graal_hpy_type_spec_get_legacy_slots(HPyType_Spec *type_spec) {
	uint64_t len=0;
	cpy_PyTypeSlot *legacy_slots = (cpy_PyTypeSlot *) type_spec->legacy_slots;
	if (legacy_slots) {
	    while (legacy_slots[len].slot != 0) {
		    len++;
	    }
	    return polyglot_from_cpy_PyTypeSlot_array(legacy_slots, len);
	}
	return NULL;
}

int64_t graal_hpy_type_spec_get_builtin_shape(HPyType_Spec *type_spec) {
	return (int64_t) type_spec->builtin_shape;
}


/*
 * Casts a 'wchar_t*' array to an 'int8_t*' array and also associates the proper length.
 * The length is determined using 'wcslen' if 'len == -1'.
 */
int8_t* graal_hpy_i8_from_wchar_array(wchar_t *arr, uint64_t len) {
    if (len == -1) {
        len = (uint64_t) (wcslen(arr) * sizeof(wchar_t));
    }
	return polyglot_from_i8_array((int8_t *) arr, len);
}

typedef void* VoidPtr;
POLYGLOT_DECLARE_TYPE(VoidPtr);

typedef union {
	void *ptr;
	float f;
	double d;
	int64_t i64;
	int32_t i32;
	int16_t i16;
	int8_t i8;
	uint64_t u64;
	uint32_t u32;
	uint16_t u16;
	uint8_t u8;
	/* TODO(fa) this should be a Py_complex; is there any equivalent ? */
	uint64_t c;
} OutVar;

POLYGLOT_DECLARE_TYPE(OutVar);
typedef struct { OutVar *content; } OutVarPtr;
POLYGLOT_DECLARE_TYPE(OutVarPtr);
void* graal_hpy_allocate_outvar() {
	return polyglot_from_OutVarPtr(truffle_managed_malloc(sizeof(OutVarPtr)));
}

/*
 * Transforms a Java handle array to native.
 * TODO(fa): This currently uses a workaround because Sulong does not fully
 * support passing structs via interop. Therefore, we pretend to have 'void *'
 * array and convert to handle using 'HPy_FromVoidP'.
 */
void* graal_hpy_array_to_native(VoidPtr *source, uint64_t len) {
	uint64_t i;
	HPy *dest = (HPy *)malloc(len*sizeof(HPy));
	for (i=0; i < len; i++) {
		dest[i] = HPy_FromVoidP(source[i]);
	}
	return polyglot_from_HPy_array(dest, len);
}

HPy_buffer* graal_hpy_buffer_to_native(void* buf, HPy obj, HPy_ssize_t len, HPy_ssize_t item_size, int readonly, int ndim,
		int64_t format_ptr, int64_t shape_ptr, int64_t strides_ptr, int64_t suboffsets_ptr, void *internal) {
	HPy_buffer *hpy_buffer = (HPy_buffer *) malloc(sizeof(HPy_buffer));
	hpy_buffer->buf = buf;
	hpy_buffer->obj = obj;
	hpy_buffer->len = len;
	hpy_buffer->itemsize = item_size;
	hpy_buffer->readonly = readonly;
	hpy_buffer->ndim = ndim;
	hpy_buffer->format = (char *)format_ptr;
	hpy_buffer->shape = (HPy_ssize_t *)shape_ptr;
	hpy_buffer->strides = (HPy_ssize_t *)strides_ptr;
	hpy_buffer->suboffsets = (HPy_ssize_t *)suboffsets_ptr;
	hpy_buffer->internal = internal;
	return hpy_buffer;
}

void graal_hpy_buffer_free(HPy_buffer *hpy_buffer) {
	free(hpy_buffer->format);
	free(hpy_buffer->shape);
	free(hpy_buffer->strides);
	free(hpy_buffer->suboffsets);
	free(hpy_buffer);
}

void get_next_vaarg(va_list *p_va, OutVarPtr *p_outvar) {
	p_outvar->content = (OutVar *)va_arg(*p_va, void *);
}

#define PRIMITIVE_ARRAY_TO_NATIVE(__jtype__, __ctype__, __polyglot_type__, __element_cast__) \
    void* graal_hpy_##__jtype__##_array_to_native(const void* jarray, int64_t len) { \
        int64_t i; \
        int64_t size = len + 1; \
        __ctype__* carr = (__ctype__*) malloc(size * sizeof(__ctype__)); \
        carr[len] = (__ctype__)0; \
        for (i=0; i < len; i++) { \
            carr[i] = __element_cast__(polyglot_get_array_element(jarray, i)); \
        } \
        return polyglot_from_##__polyglot_type__##_array(carr, len); \
    } \

PRIMITIVE_ARRAY_TO_NATIVE(byte, int8_t, i8, polyglot_as_i8);
PRIMITIVE_ARRAY_TO_NATIVE(int, int32_t, i32, polyglot_as_i32);
PRIMITIVE_ARRAY_TO_NATIVE(long, int64_t, i64, polyglot_as_i64);
PRIMITIVE_ARRAY_TO_NATIVE(double, double, double, polyglot_as_double);
PRIMITIVE_ARRAY_TO_NATIVE(pointer, VoidPtr, VoidPtr, (VoidPtr));

/*****************************************************************************/
/* getter for reading native members                                         */
/*****************************************************************************/

#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

int graal_hpy_read_s(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, short);
}

int graal_hpy_read_i(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, int);
}

long graal_hpy_read_l(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, long);
}

double graal_hpy_read_f(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, float);
}

double graal_hpy_read_d(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, double);
}

void* graal_hpy_read_string(void* object, HPy_ssize_t offset) {
    char *ptr = ReadMember(object, offset, char*);
    if (ptr != NULL) {
    	return polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
    }
    return NULL;
}

void* graal_hpy_read_string_in_place(void* object, HPy_ssize_t offset) {
    char *addr = (char*) (((char*)object) + offset);
    return polyglot_from_string(addr, "utf-8");
}

void* graal_hpy_read_HPyField(void* object, HPy_ssize_t offset) {
    return UNWRAP_FIELD(ReadMember(object, offset, HPyField));
}

char graal_hpy_read_c(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, char);
}

int graal_hpy_read_uc(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, unsigned char);
}

int graal_hpy_read_us(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, unsigned short);
}

long graal_hpy_read_ui(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, unsigned int);
}

unsigned long graal_hpy_read_ul(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, unsigned long);
}

long long graal_hpy_read_ll(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, long long);
}

unsigned long long graal_hpy_read_ull(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, unsigned long long);
}

HPy_ssize_t graal_hpy_read_HPy_ssize_t(void* object, HPy_ssize_t offset) {
    return ReadMember(object, offset, HPy_ssize_t);
}

#undef ReadMember

/*****************************************************************************/
/* setter for writing native members                                         */
/*****************************************************************************/

#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = (value)

void graal_hpy_write_s(void* object, HPy_ssize_t offset, short value) {
    WriteMember(object, offset, value, short);
}

void graal_hpy_write_i(void* object, HPy_ssize_t offset, int value) {
    WriteMember(object, offset, (value), int);
}

void graal_hpy_write_l(void* object, HPy_ssize_t offset, long value) {
    WriteMember(object, offset, (value), long);
}

void graal_hpy_write_f(void* object, HPy_ssize_t offset, float value) {
    WriteMember(object, offset, (value), float);
}

void graal_hpy_write_d(void* object, HPy_ssize_t offset, double value) {
    WriteMember(object, offset, (value), double);
}

void graal_hpy_write_string(void* object, HPy_ssize_t offset, char* value) {
    WriteMember(object, offset, value, char*);
}

void graal_hpy_write_string_in_place(void* object, HPy_ssize_t offset, char* value) {
	char *addr = (char*) (((char*)object) + offset);
	size_t n;
	if (polyglot_has_array_elements(value)) {
		n = polyglot_get_array_size(value);
	} else {
		n = strlen(value);
	}
	memcpy(addr, value, n);
}

void graal_hpy_write_HPyField(void* object, HPy_ssize_t offset, void* value) {
    WriteMember(object, offset, WRAP_FIELD(value), HPyField);
}

void graal_hpy_write_c(void* object, HPy_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
}

void graal_hpy_write_uc(void* object, HPy_ssize_t offset, unsigned char value) {
    WriteMember(object, offset, value, unsigned char);
}

void graal_hpy_write_us(void* object, HPy_ssize_t offset, unsigned short value) {
    WriteMember(object, offset, value, unsigned short);
}

void graal_hpy_write_ui(void* object, HPy_ssize_t offset, unsigned int value) {
    WriteMember(object, offset, value, unsigned int);
}

void graal_hpy_write_ul(void* object, HPy_ssize_t offset, unsigned long value) {
    WriteMember(object, offset, value, unsigned long);
}

void graal_hpy_write_ll(void* object, HPy_ssize_t offset, long long value) {
    WriteMember(object, offset, value, long long);
}

void graal_hpy_write_ull(void* object, HPy_ssize_t offset, unsigned long long value) {
    WriteMember(object, offset, value, unsigned long long);
}

void graal_hpy_write_HPy_ssize_t(void* object, HPy_ssize_t offset, HPy_ssize_t value) {
    WriteMember(object, offset, value, HPy_ssize_t);
}

void graal_hpy_write_ptr(void* object, HPy_ssize_t offset, void* value) {
    WriteMember(object, offset, value, void*);
}

#undef WriteMember

typedef void (*destroyfunc)(void *);
/* to be used from Java code only */
int graal_hpy_bulk_free(uint64_t ptrArray[], int64_t len) {
	int64_t i;
	uint64_t obj;
	destroyfunc func;

	for (i=0; i < len; i+=2) {
		obj = ptrArray[i];
	    func = (destroyfunc) ptrArray[i+1];
		if (obj) {
		    if (func != NULL) {
		        func((void *) obj);
		    }
			free((void *) obj);
		}
	}
    return 0;
}

POLYGLOT_DECLARE_TYPE(HPy_buffer);
HPy_buffer *graal_hpy_allocate_buffer() {
	return polyglot_from_HPy_buffer((HPy_buffer *) malloc(sizeof(HPy_buffer)));
}


/*****************************************************************************/
/* HPy context helper functions                                              */
/*****************************************************************************/


#define HPyAPI_STORAGE _HPy_HIDDEN
#define _HPy_IMPL_NAME(name) ctx_##name
#define _HPy_IMPL_NAME_NOPREFIX(name) ctx_##name
#define _py2h(_x) HPy_NULL
#define _h2py(_x) NULL

typedef HPy* _HPyPtr;
typedef HPyField* _HPyFieldPtr;
typedef HPy _HPyConst;
typedef HPyGlobal* _HPyGlobalPtr;

#define HPy void*
#define HPyListBuilder void*
#define HPyTupleBuilder void*
#define HPyTracker void*
#define HPyField void*
#define HPyThreadState void*
#define HPyGlobal void*
#define _HPyCapsule_key int32_t

#define SELECT_CTX(__ctx__) ((__ctx__) == g_universal_ctx ? g_universal_ctx : g_debug_ctx)

#define UPCALL_HPY(__name__, __ctx__, ...) polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__)
#define UPCALL_HPY0(__name__, __ctx__) polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__))
#define UPCALL_CHARPTR(__name__, __ctx__, ...) ((char*)polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__))
#define UPCALL_VOID(__name__, __ctx__, ...) (void)polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__)
#define UPCALL_VOID0(__name__, __ctx__) ((void)polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__)))
#define UPCALL_DOUBLE(__name__, __ctx__, ...) polyglot_as_double(polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__))
#define UPCALL_I64(__name__, __ctx__, ...) polyglot_as_i64(polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__))
#define UPCALL_I32(__name__, __ctx__, ...) polyglot_as_i32(polyglot_invoke(SELECT_CTX(__ctx__), #__name__, (__ctx__), __VA_ARGS__))


HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Module_Create)(HPyContext *ctx, HPyModuleDef *hpydef)
{
	return UPCALL_HPY(ctx_Module_Create, ctx, hpydef);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dup)(HPyContext *ctx, HPy h)
{
	return UPCALL_HPY(ctx_Dup, ctx, h);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Close)(HPyContext *ctx, HPy h)
{
	UPCALL_VOID(ctx_Close, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromLong)(HPyContext *ctx, long v)
{
	return UPCALL_HPY(ctx_Long_FromLong, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromUnsignedLong)(HPyContext *ctx, unsigned long v)
{
	return UPCALL_HPY(ctx_Long_FromUnsignedLong, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromLongLong)(HPyContext *ctx, long long v)
{
	return UPCALL_HPY(ctx_Long_FromLongLong, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromUnsignedLongLong)(HPyContext *ctx, unsigned long long v)
{
	return UPCALL_HPY(ctx_Long_FromUnsignedLongLong, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromSize_t)(HPyContext *ctx, size_t v)
{
	return UPCALL_HPY(ctx_Long_FromSize_t, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromSsize_t)(HPyContext *ctx, HPy_ssize_t v)
{
	return UPCALL_HPY(ctx_Long_FromSsize_t, ctx, v);
}

HPyAPI_STORAGE long _HPy_IMPL_NAME(Long_AsLong)(HPyContext *ctx, HPy h)
{
	return (long) UPCALL_I64(ctx_Long_AsLong, ctx, h);
}

HPyAPI_STORAGE unsigned long _HPy_IMPL_NAME(Long_AsUnsignedLong)(HPyContext *ctx, HPy h)
{
	return (unsigned long) UPCALL_I64(ctx_Long_AsUnsignedLong, ctx, h);
}

HPyAPI_STORAGE unsigned long _HPy_IMPL_NAME(Long_AsUnsignedLongMask)(HPyContext *ctx, HPy h)
{
	return (unsigned long) UPCALL_I64(ctx_Long_AsUnsignedLongMask, ctx, h);
}

HPyAPI_STORAGE long long _HPy_IMPL_NAME(Long_AsLongLong)(HPyContext *ctx, HPy h)
{
	return (long long) UPCALL_I64(ctx_Long_AsLongLong, ctx, h);
}

HPyAPI_STORAGE unsigned long long _HPy_IMPL_NAME(Long_AsUnsignedLongLong)(HPyContext *ctx, HPy h)
{
	return (unsigned long long) UPCALL_I64(ctx_Long_AsUnsignedLongLong, ctx, h);
}

HPyAPI_STORAGE unsigned long long _HPy_IMPL_NAME(Long_AsUnsignedLongLongMask)(HPyContext *ctx, HPy h)
{
	return (unsigned long long) UPCALL_I64(ctx_Long_AsUnsignedLongLongMask, ctx, h);
}

HPyAPI_STORAGE size_t _HPy_IMPL_NAME(Long_AsSize_t)(HPyContext *ctx, HPy h)
{
	return (size_t) UPCALL_I64(ctx_Long_AsSize_t, ctx, h);
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME(Long_AsSsize_t)(HPyContext *ctx, HPy h)
{
	return (HPy_ssize_t) UPCALL_I64(ctx_Long_AsSsize_t, ctx, h);
}

HPyAPI_STORAGE void* _HPy_IMPL_NAME(Long_AsVoidPtr)(HPyContext *ctx, HPy h) {
     return (void *) UPCALL_I64(ctx_Long_AsVoidPtr, ctx, h);
}

HPyAPI_STORAGE double _HPy_IMPL_NAME(Long_AsDouble)(HPyContext *ctx, HPy h) {
     return UPCALL_DOUBLE(ctx_Long_AsDouble, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Float_FromDouble)(HPyContext *ctx, double v)
{
	return UPCALL_HPY(ctx_Float_FromDouble, ctx, v);
}

HPyAPI_STORAGE double _HPy_IMPL_NAME(Float_AsDouble)(HPyContext *ctx, HPy h)
{
	return UPCALL_DOUBLE(ctx_Float_AsDouble, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Bool_FromLong)(HPyContext *ctx, long v)
{
	return UPCALL_HPY(ctx_Bool_FromLong, ctx, v);
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME_NOPREFIX(Length)(HPyContext *ctx, HPy h)
{
	return (HPy_ssize_t) UPCALL_I64(ctx_Length, ctx, h);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Number_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_Number_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Add)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Add, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Subtract)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Subtract, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Multiply)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Multiply, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(MatrixMultiply)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_MatrixMultiply, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(FloorDivide)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_FloorDivide, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(TrueDivide)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_TrueDivide, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Remainder)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Remainder, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Divmod)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Divmod, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Power)(HPyContext *ctx, HPy h1, HPy h2, HPy h3)
{
	return UPCALL_HPY(ctx_Power, ctx, h1, h2, h3);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Negative)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Negative, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Positive)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Positive, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Absolute)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Absolute, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Invert)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Invert, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Lshift)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Lshift, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Rshift)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Rshift, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(And)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_And, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Xor)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Xor, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Or)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_Or, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Index)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Index, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Long)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Long, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Float)(HPyContext *ctx, HPy h1)
{
	return UPCALL_HPY(ctx_Float, ctx, h1);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceAdd)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceAdd, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceSubtract)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceSubtract, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceMultiply)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceMultiply, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceMatrixMultiply)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceMatrixMultiply, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceFloorDivide)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceFloorDivide, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceTrueDivide)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceTrueDivide, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceRemainder)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceRemainder, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlacePower)(HPyContext *ctx, HPy h1, HPy h2, HPy h3)
{
	return UPCALL_HPY(ctx_InPlacePower, ctx, h1, h2, h3);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceLshift)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceLshift, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceRshift)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceRshift, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceAnd)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceAnd, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceXor)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceXor, ctx, h1, h2);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(InPlaceOr)(HPyContext *ctx, HPy h1, HPy h2)
{
	return UPCALL_HPY(ctx_InPlaceOr, ctx, h1, h2);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Callable_Check)(HPyContext *ctx, HPy h)
{
    return (int) UPCALL_I32(ctx_Callable_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(CallTupleDict)(HPyContext *ctx, HPy callable, HPy args, HPy kw)
{
    return UPCALL_HPY(ctx_CallTupleDict, ctx, callable, args, kw);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_SetString)(HPyContext *ctx, HPy h_type, const char *message)
{
	UPCALL_VOID(ctx_Err_SetString, ctx, h_type, message);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_SetObject)(HPyContext *ctx, HPy h_type, HPy h_value)
{
	UPCALL_VOID(ctx_Err_SetObject, ctx, h_type, h_value);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Err_SetFromErrnoWithFilename)(HPyContext *ctx, HPy h_type, const char *filename_fsencoded) {
     return UPCALL_HPY(ctx_Err_SetFromErrnoWithFilename, ctx, h_type, filename_fsencoded);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_SetFromErrnoWithFilenameObjects)(HPyContext *ctx, HPy h_type, HPy filename1, HPy filename2) {
     UPCALL_VOID(ctx_Err_SetFromErrnoWithFilenameObjects, ctx, h_type, filename1, filename2);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Err_Occurred)(HPyContext *ctx)
{
    return (int) polyglot_as_i32(UPCALL_HPY0(ctx_Err_Occurred, ctx));
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Err_ExceptionMatches)(HPyContext *ctx, HPy exc) {
     return (int) UPCALL_I32(ctx_Err_ExceptionMatches, ctx, exc);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_NoMemory)(HPyContext *ctx)
{
	UPCALL_VOID0(ctx_Err_NoMemory, ctx);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_Clear)(HPyContext *ctx)
{
	UPCALL_VOID0(ctx_Err_Clear, ctx);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Err_NewException)(HPyContext *ctx, const char *name, HPy base, HPy dict) {
    return UPCALL_HPY(ctx_Err_NewException, ctx, name, base, dict);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Err_NewExceptionWithDoc)(HPyContext *ctx, const char *name, const char *doc, HPy base, HPy dict) {
    return UPCALL_HPY(ctx_Err_NewExceptionWithDoc, ctx, name, doc, base, dict);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Err_WarnEx)(HPyContext *ctx, HPy category, const char *message, HPy_ssize_t stack_level) {
     return (int) UPCALL_I32(ctx_Err_WarnEx, ctx, category, message, stack_level);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_WriteUnraisable)(HPyContext *ctx, HPy obj) {
     UPCALL_VOID(ctx_Err_WriteUnraisable, ctx, obj);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(FatalError)(HPyContext *ctx, const char *msg) {
     UPCALL_VOID(ctx_FatalError, ctx, msg);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(IsTrue)(HPyContext *ctx, HPy h)
{
	return UPCALL_HPY(ctx_IsTrue, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Type_FromSpec)(HPyContext *ctx, HPyType_Spec *spec, HPyType_SpecParam *params)
{
	return UPCALL_HPY(ctx_Type_FromSpec, ctx, spec, params);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Type_GenericNew)(HPyContext *ctx, HPy type, _HPyPtr args, HPy_ssize_t nargs, HPy kw)
{
	return UPCALL_HPY(ctx_Type_GenericNew, ctx, type, args, nargs, kw);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(GetAttr)(HPyContext *ctx, HPy obj, HPy name)
{
	return UPCALL_HPY(ctx_GetAttr, ctx, obj, name);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(GetAttr_s)(HPyContext *ctx, HPy obj, const char *name)
{
	return UPCALL_HPY(ctx_GetAttr_s, ctx, obj, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(HasAttr)(HPyContext *ctx, HPy obj, HPy name)
{
	return (int) UPCALL_I32(ctx_HasAttr, ctx, obj, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(HasAttr_s)(HPyContext *ctx, HPy obj, const char *name)
{
	return (int) UPCALL_I32(ctx_HasAttr_s, ctx, obj, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(SetAttr)(HPyContext *ctx, HPy obj, HPy name, HPy value)
{
	return (int) UPCALL_I32(ctx_SetAttr, ctx, obj, name, value);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(SetAttr_s)(HPyContext *ctx, HPy obj, const char *name, HPy value)
{
	return (int) UPCALL_I32(ctx_SetAttr_s, ctx, obj, name, value);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(GetItem)(HPyContext *ctx, HPy obj, HPy key)
{
	return UPCALL_HPY(ctx_GetItem, ctx, obj, key);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(GetItem_i)(HPyContext *ctx, HPy obj, HPy_ssize_t idx)
{
	return UPCALL_HPY(ctx_GetItem_i, ctx, obj, idx);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(GetItem_s)(HPyContext *ctx, HPy obj, const char *key)
{
	return UPCALL_HPY(ctx_GetItem_s, ctx, obj, key);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(Contains)(HPyContext *ctx, HPy container, HPy key) {
     return (int) UPCALL_I32(ctx_Contains, ctx, container, key);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(SetItem)(HPyContext *ctx, HPy obj, HPy key, HPy value)
{
	return UPCALL_HPY(ctx_SetItem, ctx, obj, key, value);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(SetItem_i)(HPyContext *ctx, HPy obj, HPy_ssize_t idx, HPy value)
{
	return UPCALL_HPY(ctx_SetItem_i, ctx, obj, idx, value);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(SetItem_s)(HPyContext *ctx, HPy obj, const char *key, HPy value)
{
	return UPCALL_HPY(ctx_SetItem_s, ctx, obj, key, value);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Type)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_Type, ctx, obj);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(TypeCheck)(HPyContext *ctx, HPy obj, HPy type)
{
	return (int) UPCALL_I32(ctx_TypeCheck, ctx, obj, type);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(TypeCheck_g)(HPyContext *ctx, HPy obj, HPyGlobal type)
{
	return (int) UPCALL_I32(ctx_TypeCheck_g, ctx, obj, type);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(Is)(HPyContext *ctx, HPy obj, HPy other)
{
	return (int) UPCALL_I32(ctx_Is, ctx, obj, other);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(Is_g)(HPyContext *ctx, HPy obj, HPyGlobal other)
{
	return (int) UPCALL_I32(ctx_Is_g, ctx, obj, other);
}

HPyAPI_STORAGE void *_HPy_IMPL_NAME_NOPREFIX(AsStruct)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_AsStruct, ctx, obj);
}

HPyAPI_STORAGE void *_HPy_IMPL_NAME_NOPREFIX(AsStructLegacy)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_AsStructLegacy, ctx, obj);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(New)(HPyContext *ctx, HPy h_type, void **data)
{
	return UPCALL_HPY(ctx_New, ctx, h_type, data);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Repr)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_Repr, ctx, obj);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Str)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_Str, ctx, obj);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(ASCII)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_ASCII, ctx, obj);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(Bytes)(HPyContext *ctx, HPy obj)
{
	return UPCALL_HPY(ctx_Bytes, ctx, obj);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(RichCompare)(HPyContext *ctx, HPy v, HPy w, int op)
{
	return UPCALL_HPY(ctx_RichCompare, ctx, v, w, op);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME_NOPREFIX(RichCompareBool)(HPyContext *ctx, HPy v, HPy w, int op)
{
	return (int) UPCALL_I32(ctx_RichCompareBool, ctx, v, w, op);
}

HPyAPI_STORAGE HPy_hash_t _HPy_IMPL_NAME_NOPREFIX(Hash)(HPyContext *ctx, HPy obj)
{
	return (HPy_hash_t) UPCALL_I64(ctx_Hash, ctx, obj);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Bytes_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_Bytes_Check, ctx, h);
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME(Bytes_Size)(HPyContext *ctx, HPy h)
{
	return (HPy_ssize_t) UPCALL_I64(ctx_Bytes_Size, ctx, h);
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME(Bytes_GET_SIZE)(HPyContext *ctx, HPy h)
{
	return (HPy_ssize_t) UPCALL_I64(ctx_Bytes_GET_SIZE, ctx, h);
}

HPyAPI_STORAGE char *_HPy_IMPL_NAME(Bytes_AsString)(HPyContext *ctx, HPy h)
{
	return UPCALL_CHARPTR(ctx_Bytes_AsString, ctx, h);
}

HPyAPI_STORAGE char *_HPy_IMPL_NAME(Bytes_AS_STRING)(HPyContext *ctx, HPy h)
{
	return UPCALL_CHARPTR(ctx_Bytes_AS_STRING, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Bytes_FromString)(HPyContext *ctx, const char *v)
{
	return UPCALL_HPY(ctx_Bytes_FromString, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Bytes_FromStringAndSize)(HPyContext *ctx, const char *v, HPy_ssize_t len)
{
	return UPCALL_HPY(ctx_Bytes_FromStringAndSize, ctx, v, len);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_FromString)(HPyContext *ctx, const char *utf8)
{
	return UPCALL_HPY(ctx_Unicode_FromString, ctx, utf8);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Unicode_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_Unicode_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_AsASCIIString)(HPyContext *ctx, HPy h) {
     return UPCALL_HPY(ctx_Unicode_AsASCIIString, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_AsLatin1String)(HPyContext *ctx, HPy h) {
     return UPCALL_HPY(ctx_Unicode_AsLatin1String, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_AsUTF8String)(HPyContext *ctx, HPy h)
{
	return UPCALL_HPY(ctx_Unicode_AsUTF8String, ctx, h);
}

HPyAPI_STORAGE const char *_HPy_IMPL_NAME(Unicode_AsUTF8AndSize)(HPyContext *ctx, HPy h, HPy_ssize_t *size) {
	return UPCALL_CHARPTR(ctx_Unicode_AsUTF8AndSize, ctx, h, size);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_FromWideChar)(HPyContext *ctx, const wchar_t *w, HPy_ssize_t size)
{
	return UPCALL_HPY(ctx_Unicode_FromWideChar, ctx, w, size);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_DecodeFSDefault)(HPyContext *ctx, const char *v)
{
	return UPCALL_HPY(ctx_Unicode_DecodeFSDefault, ctx, v);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_DecodeFSDefaultAndSize)(HPyContext *ctx, const char *v, HPy_ssize_t size) {
     return UPCALL_HPY(ctx_Unicode_DecodeFSDefaultAndSize, ctx, v, size);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_EncodeFSDefault)(HPyContext *ctx, HPy h) {
     return UPCALL_HPY(ctx_Unicode_EncodeFSDefault, ctx, h);
}

HPyAPI_STORAGE uint32_t _HPy_IMPL_NAME(Unicode_ReadChar)(HPyContext *ctx, HPy h, HPy_ssize_t index) {
     return (uint32_t) UPCALL_I32(ctx_Unicode_ReadChar, ctx, h, index);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_DecodeLatin1)(HPyContext *ctx, const char *s, HPy_ssize_t size, const char *errors) {
     return UPCALL_HPY(ctx_Unicode_DecodeLatin1, ctx, s, size, errors );
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_DecodeASCII)(HPyContext *ctx, const char *s, HPy_ssize_t size, const char *errors) {
     return UPCALL_HPY(ctx_Unicode_DecodeASCII, ctx, s, size, errors );
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(List_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_List_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(List_New)(HPyContext *ctx, HPy_ssize_t len)
{
	return UPCALL_HPY(ctx_List_New, ctx, len);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(List_Append)(HPyContext *ctx, HPy h_list, HPy h_item)
{
	return (int) UPCALL_I32(ctx_List_Append, ctx, h_list, h_item);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Dict_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_Dict_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dict_New)(HPyContext *ctx)
{
	return UPCALL_HPY0(ctx_Dict_New, ctx);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Tuple_Check)(HPyContext *ctx, HPy h)
{
	return (int) UPCALL_I32(ctx_Tuple_Check, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Tuple_FromArray)(HPyContext *ctx, _HPyPtr items, HPy_ssize_t n)
{
	return UPCALL_HPY(ctx_Tuple_FromArray, ctx, items, n);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Import_ImportModule)(HPyContext *ctx, const char *name)
{
	return UPCALL_HPY(ctx_Import_ImportModule, ctx, name);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME_NOPREFIX(FromPyObject)(HPyContext *ctx, cpy_PyObject *obj)
{
	/* Although, this operation is not supported for in ABI compatibility mode, we still
	   need to implement the callback properly because it might still be a valid path
	   in other modes. */
	return UPCALL_HPY(ctx_FromPyObject, ctx, obj);
}

HPyAPI_STORAGE cpy_PyObject *_HPy_IMPL_NAME_NOPREFIX(AsPyObject)(HPyContext *ctx, HPy h)
{
	return (cpy_PyObject *) UPCALL_CHARPTR(ctx_AsPyObject, ctx, h);
}

HPyAPI_STORAGE HPyListBuilder _HPy_IMPL_NAME(ListBuilder_New)(HPyContext *ctx, HPy_ssize_t initial_size) {
	return UPCALL_HPY(ctx_ListBuilder_New, ctx, initial_size);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(ListBuilder_Set)(HPyContext *ctx, HPyListBuilder builder, HPy_ssize_t index, HPy h_item) {
	UPCALL_VOID(ctx_ListBuilder_Set, ctx, builder, index, h_item);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(ListBuilder_Build)(HPyContext *ctx, HPyListBuilder builder) {
	return UPCALL_HPY(ctx_ListBuilder_Build, ctx, builder);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(ListBuilder_Cancel)(HPyContext *ctx, HPyListBuilder builder) {
	UPCALL_VOID(ctx_ListBuilder_Cancel, ctx, builder);
}

HPyAPI_STORAGE HPyTupleBuilder _HPy_IMPL_NAME(TupleBuilder_New)(HPyContext *ctx, HPy_ssize_t initial_size) {
	return UPCALL_HPY(ctx_TupleBuilder_New, ctx, initial_size);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(TupleBuilder_Set)(HPyContext *ctx, HPyTupleBuilder builder, HPy_ssize_t index, HPy h_item) {
	UPCALL_VOID(ctx_TupleBuilder_Set, ctx, builder, index, h_item);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(TupleBuilder_Build)(HPyContext *ctx, HPyTupleBuilder builder) {
	return UPCALL_HPY(ctx_TupleBuilder_Build, ctx, builder);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(TupleBuilder_Cancel)(HPyContext *ctx, HPyTupleBuilder builder) {
	UPCALL_VOID(ctx_TupleBuilder_Cancel, ctx, builder);
}

HPyAPI_STORAGE HPyTracker _HPy_IMPL_NAME(Tracker_New)(HPyContext *ctx, HPy_ssize_t size) {
	return UPCALL_HPY(ctx_Tracker_New, ctx, size);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Tracker_Add)(HPyContext *ctx, HPyTracker ht, HPy h) {
	return (int) UPCALL_I32(ctx_Tracker_Add, ctx, ht, h);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Tracker_ForgetAll)(HPyContext *ctx, HPyTracker ht) {
	UPCALL_VOID(ctx_Tracker_ForgetAll, ctx, ht);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Tracker_Close)(HPyContext *ctx, HPyTracker ht) {
	UPCALL_VOID(ctx_Tracker_Close, ctx, ht);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Field_Store)(HPyContext *ctx, HPy target_object, _HPyFieldPtr target_field, HPy h) {
	UPCALL_VOID(ctx_Field_Store, ctx, target_object, target_field, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Field_Load)(HPyContext *ctx, HPy source_object, HPyField source_field) {
	return UPCALL_HPY(ctx_Field_Load, ctx, source_object, source_field);
}

HPyAPI_STORAGE HPyThreadState _HPy_IMPL_NAME(LeavePythonExecution)(HPyContext *ctx) {
	return UPCALL_HPY0(ctx_LeavePythonExecution, ctx);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(ReenterPythonExecution)(HPyContext *ctx, HPyThreadState state) {
	UPCALL_VOID(ctx_ReenterPythonExecution, ctx, state);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Global_Store)(HPyContext *ctx, _HPyGlobalPtr global, HPy h) {
	UPCALL_VOID(ctx_Global_Store, ctx, global, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Global_Load)(HPyContext *ctx, HPyGlobal global) {
	return UPCALL_HPY(ctx_Global_Load, ctx, global);
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Dump)(HPyContext *ctx, HPy h) {
	UPCALL_VOID(ctx_Dump, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(MaybeGetAttr_s)(HPyContext *ctx, HPy obj, const char *name) {
    return UPCALL_HPY(ctx_MaybeGetAttr_s, ctx, obj, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(SetType)(HPyContext *ctx, HPy obj, HPy type) {
    return (int) UPCALL_I32(ctx_SetType, ctx, obj, type);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Type_IsSubtype)(HPyContext *ctx, HPy sub, HPy type) {
    return (int) UPCALL_I32(ctx_Type_IsSubtype, ctx, sub, type);
}

HPyAPI_STORAGE const char* _HPy_IMPL_NAME(Type_GetName)(HPyContext *ctx, HPy type) {
    return UPCALL_CHARPTR(ctx_Type_GetName, ctx, type);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_FromEncodedObject)(HPyContext *ctx, HPy obj, const char *encoding, const char *errors) {
    return UPCALL_HPY(ctx_Unicode_FromEncodedObject, ctx, obj, encoding, errors);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_InternFromString)(HPyContext *ctx, const char *str) {
    return UPCALL_HPY(ctx_Unicode_InternFromString, ctx, str);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_Substring)(HPyContext *ctx, HPy obj, HPy_ssize_t start, HPy_ssize_t end) {
    return UPCALL_HPY(ctx_Unicode_Substring, ctx, obj, start, end);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dict_Keys)(HPyContext *ctx, HPy h) {
    return UPCALL_HPY(ctx_Dict_Keys, ctx, h);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dict_GetItem)(HPyContext *ctx, HPy op, HPy key) {
    return UPCALL_HPY(ctx_Dict_GetItem, ctx, op, key);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(ContextVar_New)(HPyContext *ctx, const char *name, HPy default_value) {
    return UPCALL_HPY(ctx_ContextVar_New, ctx, name, default_value);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(ContextVar_Get)(HPyContext *ctx, HPy context_var, HPy default_value, _HPyPtr result) {
    return (int) UPCALL_I32(ctx_ContextVar_Get, ctx, context_var, default_value, result);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(ContextVar_Set)(HPyContext *ctx, HPy context_var, HPy value) {
    return UPCALL_HPY(ctx_ContextVar_Set, ctx, context_var, value);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Capsule_New)(HPyContext *ctx, void *pointer, const char *name, HPyCapsule_Destructor destructor) {
    return UPCALL_HPY(ctx_Capsule_New, ctx, pointer, name, destructor);
}

HPyAPI_STORAGE void *_HPy_IMPL_NAME(Capsule_Get)(HPyContext *ctx, HPy capsule, _HPyCapsule_key key, const char *name) {
    return UPCALL_HPY(ctx_Capsule_Get, ctx, capsule, (int32_t)key, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Capsule_IsValid)(HPyContext *ctx, HPy capsule, const char *name) {
    return (int) UPCALL_I32(ctx_Capsule_IsValid, ctx, capsule, name);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Capsule_Set)(HPyContext *ctx, HPy capsule, _HPyCapsule_key key, void *value) {
    return (int) UPCALL_I32(ctx_Capsule_Set, ctx, capsule, (int32_t)key, value);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Type_CheckSlot)(HPyContext *ctx, HPy type, HPyDef *expected) {
    return (int) UPCALL_I32(ctx_Type_CheckSlot, ctx, type, expected);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Sequence_Check)(HPyContext *ctx, HPy obj) {
    return (int) UPCALL_I32(ctx_Sequence_Check, ctx, obj);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Slice_Unpack)(HPyContext *ctx, HPy slice, HPy_ssize_t *start, HPy_ssize_t *stop, HPy_ssize_t *step) {
    return (int) UPCALL_I32(ctx_Slice_Unpack, ctx, slice, start, stop, step);
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(SeqIter_New)(HPyContext *ctx, HPy seq) {
    return UPCALL_HPY(ctx_SeqIter_New, ctx, seq);
}

#undef HPy
#undef HPyListBuilder
#undef HPyTupleBuilder
#undef HPyTracker
#undef HPyField
#undef HPyThreadState
#undef HPyGlobal
#undef _HPyCapsule_key

#undef _HPy_IMPL_NAME_NOPREFIX
#undef _HPy_IMPL_NAME

#include "hpynative.h"

/* Allocate a native HPy context structure and fill it. */
HPyContext *graal_hpy_context_to_native(HPyContext *managed_context) {
	GraalHPyContext *full_native_context = (GraalHPyContext *) malloc(sizeof(GraalHPyContext));

	HPyContext *native_context = graal_native_context_get_hpy_context(full_native_context);

#define COPY(__member) native_context->__member = managed_context->__member
    COPY(name);
    COPY(abi_version);
    COPY(h_None);
    COPY(h_True);
    COPY(h_False);
    COPY(h_NotImplemented);
    COPY(h_Ellipsis);
    COPY(h_BaseException);
    COPY(h_Exception);
    COPY(h_StopAsyncIteration);
    COPY(h_StopIteration);
    COPY(h_GeneratorExit);
    COPY(h_ArithmeticError);
    COPY(h_LookupError);
    COPY(h_AssertionError);
    COPY(h_AttributeError);
    COPY(h_BufferError);
    COPY(h_EOFError);
    COPY(h_FloatingPointError);
    COPY(h_OSError);
    COPY(h_ImportError);
    COPY(h_ModuleNotFoundError);
    COPY(h_IndexError);
    COPY(h_KeyError);
    COPY(h_KeyboardInterrupt);
    COPY(h_MemoryError);
    COPY(h_NameError);
    COPY(h_OverflowError);
    COPY(h_RuntimeError);
    COPY(h_RecursionError);
    COPY(h_NotImplementedError);
    COPY(h_SyntaxError);
    COPY(h_IndentationError);
    COPY(h_TabError);
    COPY(h_ReferenceError);
    COPY(h_SystemError);
    COPY(h_SystemExit);
    COPY(h_TypeError);
    COPY(h_UnboundLocalError);
    COPY(h_UnicodeError);
    COPY(h_UnicodeEncodeError);
    COPY(h_UnicodeDecodeError);
    COPY(h_UnicodeTranslateError);
    COPY(h_ValueError);
    COPY(h_ZeroDivisionError);
    COPY(h_BlockingIOError);
    COPY(h_BrokenPipeError);
    COPY(h_ChildProcessError);
    COPY(h_ConnectionError);
    COPY(h_ConnectionAbortedError);
    COPY(h_ConnectionRefusedError);
    COPY(h_ConnectionResetError);
    COPY(h_FileExistsError);
    COPY(h_FileNotFoundError);
    COPY(h_InterruptedError);
    COPY(h_IsADirectoryError);
    COPY(h_NotADirectoryError);
    COPY(h_PermissionError);
    COPY(h_ProcessLookupError);
    COPY(h_TimeoutError);
    COPY(h_Warning);
    COPY(h_UserWarning);
    COPY(h_DeprecationWarning);
    COPY(h_PendingDeprecationWarning);
    COPY(h_SyntaxWarning);
    COPY(h_RuntimeWarning);
    COPY(h_FutureWarning);
    COPY(h_ImportWarning);
    COPY(h_UnicodeWarning);
    COPY(h_BytesWarning);
    COPY(h_ResourceWarning);
    COPY(h_BaseObjectType);
    COPY(h_TypeType);
    COPY(h_BoolType);
    COPY(h_LongType);
    COPY(h_FloatType);
    COPY(h_UnicodeType);
    COPY(h_TupleType);
    COPY(h_ListType);
    COPY(h_ComplexType);
    COPY(h_BytesType);
    COPY(h_MemoryViewType);
    COPY(h_CapsuleType);
    COPY(h_SliceType);
#undef COPY

	return native_context;
}

#undef WRAP
#undef UNWRAP
