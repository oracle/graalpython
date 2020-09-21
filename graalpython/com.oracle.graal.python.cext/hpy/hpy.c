/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include <polyglot.h>
#include <truffle.h>

#include <wchar.h>

#define SRC_CS "utf-8"
#define UNWRAP(_h) ((_h)._i)
#define WRAP(_ptr) ((HPy){(_ptr)})


typedef HPyDef* HPyDefPtr;

POLYGLOT_DECLARE_TYPE(HPy);
POLYGLOT_DECLARE_STRUCT(_HPyContext_s);
POLYGLOT_DECLARE_TYPE(HPyDef);
POLYGLOT_DECLARE_TYPE(HPyDefPtr);
POLYGLOT_DECLARE_TYPE(HPySlot);
POLYGLOT_DECLARE_TYPE(HPyMeth);
POLYGLOT_DECLARE_TYPE(cpy_PyMethodDef);
POLYGLOT_DECLARE_TYPE(HPyMember_FieldType);
POLYGLOT_DECLARE_TYPE(HPyMember);
POLYGLOT_DECLARE_TYPE(HPyModuleDef);
POLYGLOT_DECLARE_TYPE(wchar_t);
POLYGLOT_DECLARE_TYPE(HPyType_Spec);
POLYGLOT_DECLARE_TYPE(HPyType_SpecParam);

int graal_hpy_init(void *initObject) {
	// register the native type of HPy
	polyglot_invoke(initObject, "setHPyContextNativeType", polyglot__HPyContext_s_typeid());
	polyglot_invoke(initObject, "setHPyNativeType", polyglot_HPy_typeid());
	polyglot_invoke(initObject, "setHPyArrayNativeType", polyglot_array_typeid(polyglot_HPy_typeid(), 0));

	// register null handle
	polyglot_invoke(initObject, "setHPyNullHandle", HPy_NULL);

	// register size of wchar_t
	polyglot_invoke(initObject, "setWcharSize", sizeof(wchar_t));

	return 0;
}


void* graal_hpy_from_HPy_array(void *arr, uint64_t len) {
	return polyglot_from_HPy_array(arr, len);
}

void* graal_hpy_from_i8_array(void *arr, uint64_t len) {
	return polyglot_from_i8_array(arr, len);
}

wchar_t* graal_hpy_from_wchar_array(wchar_t *arr, uint64_t len) {
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

HPyType_Spec* graal_hpy_from_HPyType_Spec(HPyType_Spec *ptr) {
	return polyglot_from_HPyType_Spec(ptr);
}

HPyType_SpecParam* graal_hpy_from_HPyType_SpecParam_array(HPyType_SpecParam *ptr) {
	if (ptr != NULL) {
	    uint64_t len=0;
	    while (ptr[len].kind) {
		    len++;
	    }
	    return polyglot_from_HPyType_SpecParam_array(ptr, len);
	}
	return NULL;
}

void* graal_hpy_get_m_name(HPyModuleDef *moduleDef) {
	return polyglot_from_string(moduleDef->m_name, SRC_CS);
}

void* graal_hpy_get_m_doc(HPyModuleDef *moduleDef) {
	const char *m_doc = moduleDef->m_doc;
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

/* getters for HPyDef */

int graal_hpy_def_get_kind(HPyDef *def) {
	return def->kind;
}

/* getters for HPyType_SpecParam */

void* graal_hpy_HPyType_SpecParam_get_object(HPyType_SpecParam *def) {
	return UNWRAP(def->object);
}

/* getters for HPyModuleDef */

void* graal_hpy_module_get_legacy_methods(HPyModuleDef *moduleDef) {
	uint64_t len=0;
	while ((moduleDef->legacy_methods[len]).ml_name != NULL) {
		len++;
	}
	return polyglot_from_cpy_PyMethodDef_array(moduleDef->legacy_methods, len);
}

void* graal_hpy_module_get_defines(HPyModuleDef *moduleDef) {
	uint64_t len=0;
	while (moduleDef->defines[len] != NULL) {
		len++;
	}
	return polyglot_from_HPyDefPtr_array(moduleDef->defines, len);
}

void* graal_hpy_from_string(const char *ptr) {
	return polyglot_from_string(ptr, SRC_CS);
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
OutVarPtr* graal_hpy_allocate_outvar() {
	return polyglot_from_OutVarPtr(truffle_managed_malloc(sizeof(OutVarPtr)));
}

/*
 * Transforms a Java handle array to native.
 * TODO(fa): This currently uses a workaround because Sulong does not fully
 * support passing structs via interop. Therefore, we pretend to have 'void *'
 * array and convert to handle using 'HPy_FromVoidP'.
 */
HPy* graal_hpy_array_to_native(VoidPtr *source, uint64_t len) {
	uint64_t i;
	HPy *dest = (HPy *)malloc(len*sizeof(HPy));
	for (i=0; i < len; i++) {
		dest[i] = HPy_FromVoidP(source[i]);
	}
	return dest;
}

void get_next_vaarg(va_list *p_va, OutVarPtr *p_outvar) {
	p_outvar->content = (OutVar *)va_arg(*p_va, void *);
}

void* graal_hpy_context_to_native(void* cobj) {
    return truffle_deref_handle_for_managed(cobj);
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
    return polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
}

void* graal_hpy_read_string_in_place(void* object, HPy_ssize_t offset) {
	char *addr = (char*) (((char*)object) + offset);
    return polyglot_from_string(addr, "utf-8");
}

void* graal_hpy_read_HPy(void* object, HPy_ssize_t offset) {
    return UNWRAP(ReadMember(object, offset, HPy));
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

void graal_hpy_write_HPy(void* object, HPy_ssize_t offset, void* value) {
    WriteMember(object, offset, WRAP(value), HPy);
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

#undef WriteMember

#undef WRAP
#undef UNWRAP
