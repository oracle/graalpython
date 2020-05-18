/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

/*  C implementation for the date/time type documented at
 *  http://www.zope.org/Members/fdrake/DateTimeWiki/FrontPage
 */

/* bpo-35081: Defining this prevents including the C API capsule;
 * internal versions of the  Py*_Check macros which do not require
 * the capsule are defined below */

#define _PY_DATETIME_IMPL

#include "capi.h"
#include <time.h>

POLYGLOT_DECLARE_TYPE(PyDateTime_CAPI);

/* Forward declarations. */
static PyTypeObject PyDateTime_DateType;
static PyTypeObject PyDateTime_DateTimeType;
static PyTypeObject PyDateTime_DeltaType;
static PyTypeObject PyDateTime_TimeType;
static PyTypeObject PyDateTime_TZInfoType;


/* special built-in module 'datetime' */
POLYGLOT_DECLARE_TYPE(PyDateTime_Date);
POLYGLOT_DECLARE_TYPE(PyDateTime_Time);
POLYGLOT_DECLARE_TYPE(PyDateTime_DateTime);
POLYGLOT_DECLARE_TYPE(PyDateTime_Delta);
POLYGLOT_DECLARE_TYPE(PyDateTime_TZInfo);

/** to be used from Java code only; returns the type ID for a PyDateTime_CAPI */
extern PyObject* set_PyDateTime_typeids(PyTypeObject* dtcapiType, PyTypeObject* dateType, PyTypeObject* dateTimeType, PyTypeObject* timeType, PyTypeObject* deltaType, PyTypeObject* tzinfoType) {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_SulongType", dtcapiType, polyglot_PyDateTime_CAPI_typeid());

    /* safe native get/set descriptors */
    PyGetSetDef* getsets_date= PyDateTime_DateType.tp_getset;
    PyGetSetDef* getsets_time = PyDateTime_TimeType.tp_getset;
    PyGetSetDef* getsets_datetime = PyDateTime_DateTimeType.tp_getset;
    PyMemberDef* members_delta = PyDateTime_DeltaType.tp_members;

    initialize_type_structure(&PyDateTime_DateType, dateType, polyglot_PyDateTime_Date_typeid());
    initialize_type_structure(&PyDateTime_DateTimeType, dateTimeType, polyglot_PyDateTime_DateTime_typeid());
    initialize_type_structure(&PyDateTime_TimeType, timeType, polyglot_PyDateTime_Time_typeid());
    initialize_type_structure(&PyDateTime_DeltaType, deltaType, polyglot_PyDateTime_Delta_typeid());
    initialize_type_structure(&PyDateTime_TZInfoType, tzinfoType, polyglot_PyDateTime_TZInfo_typeid());

    /* register native get/set descriptors to managed types */
    register_native_slots(&PyDateTime_DateType, getsets_date, NULL);
    register_native_slots(&PyDateTime_DateTimeType, getsets_datetime, NULL);
    register_native_slots(&PyDateTime_TimeType, getsets_time, NULL);
    register_native_slots(&PyDateTime_DeltaType, NULL, members_delta);

    return Py_True;
}

BASICSIZE_GETTER(PyDateTime_Date);
BASICSIZE_GETTER(PyDateTime_Time);
BASICSIZE_GETTER(PyDateTime_DateTime);
BASICSIZE_GETTER(PyDateTime_Delta);

/* Helper method: implements parsing of longobject to C int as format specifier 'i' does. */
static int PyLong_AsInt(PyObject *arg) {
	long ival = PyLong_AsLong(arg);
	if (ival == -1 && PyErr_Occurred())
		return -1;
	else if (ival > INT_MAX) {
		PyErr_SetString(PyExc_OverflowError,
				"signed integer is greater than maximum");
		return -1;
	}
	else if (ival < INT_MIN) {
		PyErr_SetString(PyExc_OverflowError,
				"signed integer is less than minimum");
		return -1;
	}
	return (int) ival;
}


/* The following code is taken from CPython '_datetimemodule.c' */


/* We require that C int be at least 32 bits, and use int virtually
 * everywhere.  In just a few cases we use a temp long, where a Python
 * API returns a C long.  In such cases, we have to ensure that the
 * final result fits in a C int (this can be an issue on 64-bit boxes).
 */
#if SIZEOF_INT < 4
#       error "_datetime.c requires that C int have at least 32 bits"
#endif

#define MINYEAR 1
#define MAXYEAR 9999

/* Rename the long macros in datetime.h to more reasonable short names. */
#define GET_YEAR                PyDateTime_GET_YEAR
#define GET_MONTH               PyDateTime_GET_MONTH
#define GET_DAY                 PyDateTime_GET_DAY
#define DATE_GET_HOUR           PyDateTime_DATE_GET_HOUR
#define DATE_GET_MINUTE         PyDateTime_DATE_GET_MINUTE
#define DATE_GET_SECOND         PyDateTime_DATE_GET_SECOND
#define DATE_GET_MICROSECOND    PyDateTime_DATE_GET_MICROSECOND
#define DATE_GET_FOLD           PyDateTime_DATE_GET_FOLD

/* Date accessors for date and datetime. */
#define SET_YEAR(o, v)          (((o)->data[0] = ((v) & 0xff00) >> 8), \
                 ((o)->data[1] = ((v) & 0x00ff)))
#define SET_MONTH(o, v)         (PyDateTime_GET_MONTH(o) = (v))
#define SET_DAY(o, v)           (PyDateTime_GET_DAY(o) = (v))

/* Date/Time accessors for datetime. */
#define DATE_SET_HOUR(o, v)     (PyDateTime_DATE_GET_HOUR(o) = (v))
#define DATE_SET_MINUTE(o, v)   (PyDateTime_DATE_GET_MINUTE(o) = (v))
#define DATE_SET_SECOND(o, v)   (PyDateTime_DATE_GET_SECOND(o) = (v))
#define DATE_SET_MICROSECOND(o, v)      \
    (((o)->data[7] = ((v) & 0xff0000) >> 16), \
     ((o)->data[8] = ((v) & 0x00ff00) >> 8), \
     ((o)->data[9] = ((v) & 0x0000ff)))
#define DATE_SET_FOLD(o, v)   (PyDateTime_DATE_GET_FOLD(o) = (v))

/* Time accessors for time. */
#define TIME_GET_HOUR           PyDateTime_TIME_GET_HOUR
#define TIME_GET_MINUTE         PyDateTime_TIME_GET_MINUTE
#define TIME_GET_SECOND         PyDateTime_TIME_GET_SECOND
#define TIME_GET_MICROSECOND    PyDateTime_TIME_GET_MICROSECOND
#define TIME_GET_FOLD           PyDateTime_TIME_GET_FOLD
#define TIME_SET_HOUR(o, v)     (PyDateTime_TIME_GET_HOUR(o) = (v))
#define TIME_SET_MINUTE(o, v)   (PyDateTime_TIME_GET_MINUTE(o) = (v))
#define TIME_SET_SECOND(o, v)   (PyDateTime_TIME_GET_SECOND(o) = (v))
#define TIME_SET_MICROSECOND(o, v)      \
    (((o)->data[3] = ((v) & 0xff0000) >> 16), \
     ((o)->data[4] = ((v) & 0x00ff00) >> 8), \
     ((o)->data[5] = ((v) & 0x0000ff)))
#define TIME_SET_FOLD(o, v)   (PyDateTime_TIME_GET_FOLD(o) = (v))

/* p is a pointer to a time or a datetime object; HASTZINFO(p) returns
 * p->hastzinfo.
 */
#define HASTZINFO(p) (((_PyDateTime_BaseTZInfo *)(p))->hastzinfo)


/* For each month ordinal in 1..12, the number of days in that month,
 * and the number of days before that month in the same year.  These
 * are correct for non-leap years only.
 */
static const int _days_in_month[] = {
    0, /* unused; this vector uses 1-based indexing */
    31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
};

/* year -> 1 if leap year, else 0. */
static int
is_leap(int year)
{
    /* Cast year to unsigned.  The result is the same either way, but
     * C can generate faster code for unsigned mod than for signed
     * mod (especially for % 4 -- a good compiler should just grab
     * the last 2 bits when the LHS is unsigned).
     */
    const unsigned int ayear = (unsigned int)year;
    return ayear % 4 == 0 && (ayear % 100 != 0 || ayear % 400 == 0);
}

/* year, month -> number of days in that month in that year */
static int
days_in_month(int year, int month)
{
    assert(month >= 1);
    assert(month <= 12);
    if (month == 2 && is_leap(year))
        return 29;
    else
        return _days_in_month[month];
}

/* Check that date arguments are in range.  Return 0 if they are.  If they
 * aren't, raise ValueError and return -1.
 */
static int
check_date_args(int year, int month, int day)
{

    if (year < MINYEAR || year > MAXYEAR) {
        PyErr_Format(PyExc_ValueError, "year %i is out of range", year);
        return -1;
    }
    if (month < 1 || month > 12) {
        PyErr_SetString(PyExc_ValueError,
                        "month must be in 1..12");
        return -1;
    }
    if (day < 1 || day > days_in_month(year, month)) {
        PyErr_SetString(PyExc_ValueError,
                        "day is out of range for month");
        return -1;
    }
    return 0;
}

/* Check that time arguments are in range.  Return 0 if they are.  If they
 * aren't, raise ValueError and return -1.
 */
static int
check_time_args(int h, int m, int s, int us, int fold)
{
    if (h < 0 || h > 23) {
        PyErr_SetString(PyExc_ValueError,
                        "hour must be in 0..23");
        return -1;
    }
    if (m < 0 || m > 59) {
        PyErr_SetString(PyExc_ValueError,
                        "minute must be in 0..59");
        return -1;
    }
    if (s < 0 || s > 59) {
        PyErr_SetString(PyExc_ValueError,
                        "second must be in 0..59");
        return -1;
    }
    if (us < 0 || us > 999999) {
        PyErr_SetString(PyExc_ValueError,
                        "microsecond must be in 0..999999");
        return -1;
    }
    if (fold != 0 && fold != 1) {
        PyErr_SetString(PyExc_ValueError,
                        "fold must be either 0 or 1");
        return -1;
    }
    return 0;
}
/* ---------------------------------------------------------------------------
 * Basic object allocation:  tp_alloc implementations.  These allocate
 * Python objects of the right size and type, and do the Python object-
 * initialization bit.  If there's not enough memory, they return NULL after
 * setting MemoryError.  All data members remain uninitialized trash.
 *
 * We abuse the tp_alloc "nitems" argument to communicate whether a tzinfo
 * member is needed.  This is ugly, imprecise, and possibly insecure.
 * tp_basicsize for the time and datetime types is set to the size of the
 * struct that has room for the tzinfo member, so subclasses in Python will
 * allocate enough space for a tzinfo member whether or not one is actually
 * needed.  That's the "ugly and imprecise" parts.  The "possibly insecure"
 * part is that PyType_GenericAlloc() (which subclasses in Python end up
 * using) just happens today to effectively ignore the nitems argument
 * when tp_itemsize is 0, which it is for these type objects.  If that
 * changes, perhaps the callers of tp_alloc slots in this file should
 * be changed to force a 0 nitems argument unless the type being allocated
 * is a base type implemented in this file (so that tp_alloc is time_alloc
 * or datetime_alloc below, which know about the nitems abuse).
 */

static PyObject *
time_alloc(PyTypeObject *type, Py_ssize_t aware)
{
    PyObject *self;

    self = (PyObject *)
        PyObject_MALLOC(aware ?
                        sizeof(PyDateTime_Time) :
                sizeof(_PyDateTime_BaseTime));
    if (self == NULL)
        return (PyObject *)PyErr_NoMemory();
    (void)PyObject_INIT(self, type);
    return self;
}

static PyObject *
datetime_alloc(PyTypeObject *type, Py_ssize_t aware)
{
    PyObject *self;

    self = (PyObject *)
        PyObject_MALLOC(aware ?
                        sizeof(PyDateTime_DateTime) :
                sizeof(_PyDateTime_BaseDateTime));
    if (self == NULL)
        return (PyObject *)PyErr_NoMemory();
    (void)PyObject_INIT(self, type);
    return self;
}


#define OFFSET(field)  offsetof(PyDateTime_Delta, field)

static PyMemberDef delta_members[] = {

    {"_days",         T_INT, OFFSET(days),         0,
     PyDoc_STR("Number of days.")},

    {"_seconds",      T_INT, OFFSET(seconds),      0,
     PyDoc_STR("Number of seconds (>= 0 and less than 1 day).")},

    {"_microseconds", T_INT, OFFSET(microseconds), 0,
     PyDoc_STR("Number of microseconds (>= 0 and less than 1 second).")},

    {"_hashcode", T_PYSSIZET, OFFSET(hashcode), 0,
     PyDoc_STR("hashcode")},
    {NULL}
};

static PyTypeObject PyDateTime_DeltaType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "datetime.timedelta",                               /* tp_name */
    sizeof(PyDateTime_Delta),                           /* tp_basicsize */
    0,                                                  /* tp_itemsize */
    0,                                                  /* tp_dealloc */
    0,                                                  /* tp_vectorcall_offset */
    0,                                                  /* tp_getattr */
    0,                                                  /* tp_setattr */
    0,                                                  /* tp_as_async */
    0,                                                  /* tp_repr */
    0,                                                  /* tp_as_number */
    0,                                                  /* tp_as_sequence */
    0,                                                  /* tp_as_mapping */
    0,                                                  /* tp_hash */
    0,                                                  /* tp_call */
    0,                                                  /* tp_str */
    0,                                                  /* tp_getattro */
    0,                                                  /* tp_setattro */
    0,                                                  /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,           /* tp_flags */
    0,                                                  /* tp_doc */
    0,                                                  /* tp_traverse */
    0,                                                  /* tp_clear */
    0,                                                  /* tp_richcompare */
    0,                                                  /* tp_weaklistoffset */
    0,                                                  /* tp_iter */
    0,                                                  /* tp_iternext */
    0,                                                  /* tp_methods */
    delta_members,                                      /* tp_members */
    0,                                                  /* tp_getset */
    0,                                                  /* tp_base */
    0,                                                  /* tp_dict */
    0,                                                  /* tp_descr_get */
    0,                                                  /* tp_descr_set */
    0,                                                  /* tp_dictoffset */
    0,                                                  /* tp_init */
    0,                                                  /* tp_alloc */
    0,                                                  /* tp_new */
    0,                                                  /* tp_free */
};

/*
 * PyDateTime_Date implementation.
 */

/* Accessor properties. */

static PyObject *
date_year(PyDateTime_Date *self, void *unused)
{
    return PyLong_FromLong(GET_YEAR(self));
}

static PyObject *
date_month(PyDateTime_Date *self, void *unused)
{
    return PyLong_FromLong(GET_MONTH(self));
}

static PyObject *
date_day(PyDateTime_Date *self, void *unused)
{
    return PyLong_FromLong(GET_DAY(self));
}

static int
set_date_year(PyDateTime_Date *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_date_args(ival, 1, 1) < 0) {
        return -1;
    }
    self->hashcode = -1;
    SET_YEAR(self, ival);
    return 0;
}

static int
set_date_month(PyDateTime_Date *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_date_args(MINYEAR, ival, 1) < 0) {
        return -1;
    }
    self->hashcode = -1;
    SET_MONTH(self, ival);
    return 0;
}

static int
set_date_day(PyDateTime_Date *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_date_args(MINYEAR, 1, ival) < 0) {
        return -1;
    }
    self->hashcode = -1;
    SET_DAY(self, ival);
    return 0;
}

static PyObject *
base_hashcode(_PyDateTime_BaseTZInfo *self, void *unused)
{
    return PyLong_FromSsize_t(self->hashcode);
}

static int
set_base_hashcode(_PyDateTime_BaseTZInfo *self, PyObject *value, void *unused)
{
    self->hashcode = (Py_hash_t) PyLong_AsSsize_t(value);
    return 0;
}


static PyGetSetDef date_getset[] = {
    {"_year",        (getter)date_year, (setter)set_date_year},
    {"_month",       (getter)date_month, (setter)set_date_month},
    {"_day",         (getter)date_day, (setter)set_date_day},
    {"_hashcode",   (getter)base_hashcode, (setter)set_base_hashcode},
    {NULL}
};

static Py_hash_t
generic_hash(unsigned char *data, int len)
{
    return _Py_HashBytes(data, len);
}


static PyObject *date_getstate(PyDateTime_Date *self);

static Py_hash_t
date_hash(PyDateTime_Date *self)
{
    if (self->hashcode == -1) {
        self->hashcode = generic_hash(
            (unsigned char *)self->data, _PyDateTime_DATE_DATASIZE);
    }

    return self->hashcode;
}

/* __getstate__ isn't exposed */
static PyObject *
date_getstate(PyDateTime_Date *self)
{
    PyObject* field;
    field = PyBytes_FromStringAndSize((char*)self->data,
                                       _PyDateTime_DATE_DATASIZE);
    return Py_BuildValue("(N)", field);
}

static PyTypeObject PyDateTime_DateType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "datetime.date",                                    /* tp_name */
    sizeof(PyDateTime_Date),                            /* tp_basicsize */
    0,                                                  /* tp_itemsize */
    0,                                                  /* tp_dealloc */
    0,                                                  /* tp_vectorcall_offset */
    0,                                                  /* tp_getattr */
    0,                                                  /* tp_setattr */
    0,                                                  /* tp_as_async */
    0,                                                  /* tp_repr */
    0,                                                  /* tp_as_number */
    0,                                                  /* tp_as_sequence */
    0,                                                  /* tp_as_mapping */
    (hashfunc)date_hash,                                /* tp_hash */
    0,                                                  /* tp_call */
    0,                                                  /* tp_str */
    0,                                                  /* tp_getattro */
    0,                                                  /* tp_setattro */
    0,                                                  /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,           /* tp_flags */
    0,                                                  /* tp_doc */
    0,                                                  /* tp_traverse */
    0,                                                  /* tp_clear */
    0,                                                  /* tp_richcompare */
    0,                                                  /* tp_weaklistoffset */
    0,                                                  /* tp_iter */
    0,                                                  /* tp_iternext */
    0,                                                  /* tp_methods */
    0,                                                  /* tp_members */
    date_getset,                                        /* tp_getset */
    0,                                                  /* tp_base */
    0,                                                  /* tp_dict */
    0,                                                  /* tp_descr_get */
    0,                                                  /* tp_descr_set */
    0,                                                  /* tp_dictoffset */
    0,                                                  /* tp_init */
    0,                                                  /* tp_alloc */
    0,                                                  /* tp_new */
    0,                                                  /* tp_free */
};

static PyTypeObject PyDateTime_TZInfoType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "datetime.tzinfo",                          /* tp_name */
    sizeof(PyDateTime_TZInfo),                  /* tp_basicsize */
    0,                                          /* tp_itemsize */
    0,                                          /* tp_dealloc */
    0,                                          /* tp_vectorcall_offset */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_as_async */
    0,                                          /* tp_repr */
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */
    0,                                          /* tp_as_mapping */
    0,                                          /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    0,                                          /* tp_getattro */
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,   /* tp_flags */
    0,                                          /* tp_doc */
    0,                                          /* tp_traverse */
    0,                                          /* tp_clear */
    0,                                          /* tp_richcompare */
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */
    0,                                          /* tp_members */
    0,                                          /* tp_getset */
    0,                                          /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */
    0,                                          /* tp_alloc */
    0,                                          /* tp_new */
    0,                                          /* tp_free */
};

/*
 * PyDateTime_Time implementation.
 */

/* Accessor properties.
 */

static PyObject *
time_hour(PyDateTime_Time *self, void *unused)
{
    return PyLong_FromLong(TIME_GET_HOUR(self));
}

static PyObject *
time_minute(PyDateTime_Time *self, void *unused)
{
    return PyLong_FromLong(TIME_GET_MINUTE(self));
}

/* The name time_second conflicted with some platform header file. */
static PyObject *
py_time_second(PyDateTime_Time *self, void *unused)
{
    return PyLong_FromLong(TIME_GET_SECOND(self));
}

static PyObject *
time_microsecond(PyDateTime_Time *self, void *unused)
{
    return PyLong_FromLong(TIME_GET_MICROSECOND(self));
}

static PyObject *
time_tzinfo(PyDateTime_Time *self, void *unused)
{
    PyObject *result = HASTZINFO(self) ? self->tzinfo : Py_None;
    Py_INCREF(result);
    return result;
}

static PyObject *
time_fold(PyDateTime_Time *self, void *unused)
{
    return PyLong_FromLong(TIME_GET_FOLD(self));
}

static PyObject *
set_time_hour(PyDateTime_Time *self, PyObject* value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(ival, 0, 0, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    TIME_SET_HOUR(self, ival);
    return 0;
}

static PyObject *
set_time_minute(PyDateTime_Time *self, PyObject* value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, ival, 0, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    TIME_SET_MINUTE(self, ival);
    return 0;
}

/* The name time_second conflicted with some platform header file. */
static PyObject *
set_py_time_second(PyDateTime_Time *self, PyObject* value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, ival, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    TIME_SET_SECOND(self, ival);
    return 0;
}

static PyObject *
set_time_microsecond(PyDateTime_Time *self, PyObject* value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, 0, ival, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    TIME_SET_MICROSECOND(self, ival);
    return 0;
}

static PyObject *
set_time_tzinfo(PyDateTime_Time *self, PyObject* value, void *unused)
{
    if (value != Py_None) {
        Py_INCREF(value);
        self->tzinfo = value;
    }
    return 0;
}

static PyObject *
set_time_fold(PyDateTime_Time *self, PyObject* value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, 0, 0, ival) < 0) {
        return -1;
    }
    self->hashcode = -1;
    TIME_SET_FOLD(self, ival);
    return 0;
}

static PyGetSetDef time_getset[] = {
    {"_hour",        (getter)time_hour, (setter)set_time_hour},
    {"_minute",      (getter)time_minute, (setter)set_time_minute},
    {"_second",      (getter)py_time_second, (setter)set_py_time_second},
    {"_microsecond", (getter)time_microsecond, (setter)set_time_microsecond},
    {"_tzinfo",      (getter)time_tzinfo, (setter)set_time_tzinfo},
    {"_fold",        (getter)time_fold, (setter)set_time_fold},
    {"_hashcode",    (getter)base_hashcode, (setter)set_base_hashcode},
    {NULL}
};


static PyTypeObject PyDateTime_TimeType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "datetime.time",                            /* tp_name */
    sizeof(PyDateTime_Time),                    /* tp_basicsize */
    0,                                          /* tp_itemsize */
    0,                                          /* tp_dealloc */
    0,                                          /* tp_vectorcall_offset */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_as_async */
    0,                                          /* tp_repr */
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */
    0,                                          /* tp_as_mapping */
    0,                                          /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    PyObject_GenericGetAttr,                    /* tp_getattro */
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,   /* tp_flags */
    0,                                          /* tp_doc */
    0,                                          /* tp_traverse */
    0,                                          /* tp_clear */
    0,                                          /* tp_richcompare */
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */
    0,                                          /* tp_members */
    time_getset,                                /* tp_getset */
    0,                                          /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */
    time_alloc,                                 /* tp_alloc */
    0,                                          /* tp_new */
    0,                                          /* tp_free */
};

/*
 * PyDateTime_DateTime implementation.
 */

/* Accessor properties.  Properties for day, month, and year are inherited
 * from date.
 */

static PyObject *
datetime_hour(PyDateTime_DateTime *self, void *unused)
{
    return PyLong_FromLong(DATE_GET_HOUR(self));
}

static PyObject *
datetime_minute(PyDateTime_DateTime *self, void *unused)
{
    return PyLong_FromLong(DATE_GET_MINUTE(self));
}

static PyObject *
datetime_second(PyDateTime_DateTime *self, void *unused)
{
    return PyLong_FromLong(DATE_GET_SECOND(self));
}

static PyObject *
datetime_microsecond(PyDateTime_DateTime *self, void *unused)
{
    return PyLong_FromLong(DATE_GET_MICROSECOND(self));
}

static PyObject *
datetime_tzinfo(PyDateTime_DateTime *self, void *unused)
{
    PyObject *result = HASTZINFO(self) ? self->tzinfo : Py_None;
    Py_INCREF(result);
    return result;
}

static PyObject *
datetime_fold(PyDateTime_DateTime *self, void *unused)
{
    return PyLong_FromLong(DATE_GET_FOLD(self));
}

static int
set_datetime_hour(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(ival, 0, 0, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    DATE_SET_HOUR(self, ival);
    return 0;
}

static int
set_datetime_minute(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, ival, 0, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    DATE_SET_MINUTE(self, ival);
    return 0;
}

static int
set_datetime_second(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, ival, 0, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    DATE_SET_SECOND(self, ival);
    return 0;
}

static int
set_datetime_microsecond(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, 0, ival, 0) < 0) {
        return -1;
    }
    self->hashcode = -1;
    DATE_SET_MICROSECOND(self, ival);
    return 0;
}

static int
set_datetime_tzinfo(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
    if (value != Py_None) {
        Py_INCREF(value);
        self->tzinfo = value;
    }
    return 0;
}

static int
set_datetime_fold(PyDateTime_DateTime *self, PyObject *value, void *unused)
{
	int ival = PyLong_AsInt(value);
    if (check_time_args(0, 0, 0, 0, ival) < 0) {
        return -1;
    }
    self->hashcode = -1;
    DATE_SET_FOLD(self, ival);
    return 0;
}

static PyGetSetDef datetime_getset[] = {
    {"_hour",        (getter)datetime_hour, (setter)set_datetime_hour},
    {"_minute",      (getter)datetime_minute, (setter)set_datetime_minute},
    {"_second",      (getter)datetime_second, (setter)set_datetime_second},
    {"_microsecond", (getter)datetime_microsecond, (setter)set_datetime_microsecond},
    {"_tzinfo",      (getter)datetime_tzinfo, (setter)set_datetime_tzinfo},
    {"_fold",        (getter)datetime_fold, (setter)set_datetime_fold},
    {"_hashcode",    (getter)base_hashcode, (setter)set_base_hashcode},
    {NULL}
};


static PyTypeObject PyDateTime_DateTimeType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "datetime.datetime",                        /* tp_name */
    sizeof(PyDateTime_DateTime),                /* tp_basicsize */
    0,                                          /* tp_itemsize */
    0,                                          /* tp_dealloc */
    0,                                          /* tp_vectorcall_offset */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_as_async */
    0,                                          /* tp_repr */
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */
    0,                                          /* tp_as_mapping */
    0,                                          /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    0,                                          /* tp_getattro */
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,   /* tp_flags */
    0,                                          /* tp_doc */
    0,                                          /* tp_traverse */
    0,                                          /* tp_clear */
    0,                                          /* tp_richcompare */
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */
    0,                                          /* tp_members */
    datetime_getset,                            /* tp_getset */
    &PyDateTime_DateType,                       /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */
    datetime_alloc,                             /* tp_alloc */
    0,                                          /* tp_new */
    0,                                          /* tp_free */
};
