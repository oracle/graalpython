/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

POLYGLOT_DECLARE_TYPE(PyDateTime_CAPI);

PyTypeObject PyDateTime_DateType = PY_TRUFFLE_TYPE("datetime.date", NULL, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyDateTime_Date));
PyTypeObject PyDateTime_DateTimeType = PY_TRUFFLE_TYPE("datetime.datetime", NULL, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyDateTime_DateTime));
PyTypeObject PyDateTime_DeltaType = PY_TRUFFLE_TYPE("datetime.timedelta", NULL, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyDateTime_Delta));
PyTypeObject PyDateTime_TimeType = PY_TRUFFLE_TYPE("datetime.time", NULL, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyDateTime_Time));
PyTypeObject PyDateTime_TZInfoType = PY_TRUFFLE_TYPE("datetime.tzinfo", NULL, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyDateTime_TZInfo));

/* special builtin module 'datetime' */
POLYGLOT_DECLARE_TYPE(PyDateTime_Date);
POLYGLOT_DECLARE_TYPE(PyDateTime_Time);
POLYGLOT_DECLARE_TYPE(PyDateTime_DateTime);
POLYGLOT_DECLARE_TYPE(PyDateTime_Delta);
POLYGLOT_DECLARE_TYPE(PyDateTime_TZInfo);


/** to be used from Java code only; returns the type ID for a PyDateTime_CAPI */
extern PyObject* set_PyDateTime_typeids(PyTypeObject* dtcapiType, PyTypeObject* dateType, PyTypeObject* dateTimeType, PyTypeObject* timeType, PyTypeObject* deltaType, PyTypeObject* tzinfoType) {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_SulongType", dtcapiType, polyglot_PyDateTime_CAPI_typeid());

    initialize_type_structure(&PyDateTime_DateType, dateType, polyglot_PyDateTime_Date_typeid());
    initialize_type_structure(&PyDateTime_DateTimeType, dateTimeType, polyglot_PyDateTime_DateTime_typeid());
    initialize_type_structure(&PyDateTime_TimeType, timeType, polyglot_PyDateTime_Time_typeid());
    initialize_type_structure(&PyDateTime_DeltaType, deltaType, polyglot_PyDateTime_Delta_typeid());
    initialize_type_structure(&PyDateTime_TZInfoType, tzinfoType, polyglot_PyDateTime_TZInfo_typeid());
    return Py_True;
}

BASICSIZE_GETTER(PyDateTime_Date);
BASICSIZE_GETTER(PyDateTime_Time);
BASICSIZE_GETTER(PyDateTime_DateTime);
BASICSIZE_GETTER(PyDateTime_Delta);
