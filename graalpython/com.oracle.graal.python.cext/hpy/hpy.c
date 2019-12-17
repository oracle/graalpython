/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#define SRC_CS "utf-8"

POLYGLOT_DECLARE_TYPE(HPy);
POLYGLOT_DECLARE_TYPE(HPyMethodDef);
POLYGLOT_DECLARE_TYPE(HPyModuleDef);
POLYGLOT_DECLARE_TYPE(wchar_t);

int graal_hpy_init(void *initObject) {
	// register the native type of HPy
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

void* graal_hpy_from_wchar_array(void *arr, uint64_t len) {
	return polyglot_from_wchar_t_array(arr, len);
}

void* graal_hpy_from_HPyMethodDef(void *ptr) {
	return polyglot_from_HPyMethodDef(ptr);
}

void* graal_hpy_from_HPyModuleDef(void *ptr) {
	return polyglot_from_HPyModuleDef(ptr);
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

void* graal_hpy_get_ml_name(HPyMethodDef *methodDef) {
	return polyglot_from_string(methodDef->ml_name, SRC_CS);
}

void* graal_hpy_get_ml_doc(HPyMethodDef *methodDef) {
	return polyglot_from_string(methodDef->ml_doc, SRC_CS);
}

void* graal_hpy_get_m_methods(HPyModuleDef *moduleDef) {
	uint64_t len=0;
	while ((moduleDef->m_methods[len]).ml_name != NULL) {
		len++;
	}
	return polyglot_from_HPyMethodDef_array(moduleDef->m_methods, len);
}

void* graal_hpy_from_string(const char *ptr) {
	return polyglot_from_string(ptr, SRC_CS);
}

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

HPy* graal_hpy_array_to_native(HPy *source, uint64_t len) {
	uint64_t i;
	HPy *dest = (HPy *)malloc(len*sizeof(HPy));
	for (i=0; i < len; i++) {
		dest[i] = source[i];
	}
	return dest;
}

void get_next_vaarg(va_list *p_va, OutVarPtr *p_outvar) {
	p_outvar->content = (OutVar *)va_arg(*p_va, void *);
}
