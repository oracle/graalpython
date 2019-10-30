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

#include "capi.h"

UPCALL_ID(PyTruffle_OS_StringToDouble);
double PyOS_string_to_double(const char *s, char **endptr, PyObject *overflow_exception) {
	double result = -1.0;
	PyObject* resultTuple = UPCALL_CEXT_O(_jls_PyTruffle_OS_StringToDouble, polyglot_from_string(s, "ascii"), endptr != NULL);
	if (resultTuple != NULL) {
		result = as_double(PyTuple_GetItem(resultTuple, 0));
		if (endptr != NULL) {
			*endptr = s + as_long(PyTuple_GetItem(resultTuple, 1));
		}
	}
	return result;
}

/* translation macro to be independent of changes in 'pystrtod.h' */
#define TRANSLATE_TYPE(__tc__) ((__tc__) == 0 ? Py_DTST_FINITE : ((__tc__) == 1 ? Py_DTST_INFINITE : Py_DTST_NAN))

UPCALL_ID(PyTruffle_OS_DoubleToString);
char * PyOS_double_to_string(double val, char format_code, int precision, int flags, int *type) {
	char* result = NULL;
	PyObject* resultTuple = UPCALL_CEXT_O(_jls_PyTruffle_OS_DoubleToString, val, (int32_t)format_code, precision, flags);
	if (resultTuple != NULL) {
		result = (char *) PyTuple_GetItem(resultTuple, 0);
		if (type != NULL) {
			*type = TRANSLATE_TYPE(as_int(PyTuple_GetItem(resultTuple, 1)));
		}
	}
	return result;
}
