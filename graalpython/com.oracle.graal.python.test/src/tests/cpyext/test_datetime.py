# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import datetime

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, unhandled_error_compare, is_native_object, IS_MANAGED_LAUNCHER

__dir__ = __file__.rpartition("/")[0]


def create_datetime_subclass(typename):
    return CPyExtType(
        f"Native{typename}Subclass",
        struct_base=f'PyDateTime_{typename} base;',
        tp_new='0',
        tp_alloc='0',
        tp_free='0',
        includes='#include "datetime.h"',
        ready_code=f'''
        PyDateTime_IMPORT;
        PyTypeObject* t = PyDateTimeAPI->{typename}Type;
        Py_XINCREF(t);
        Native{typename}SubclassType.tp_base = t;
        ''',
    )


NativeDateSubclass = create_datetime_subclass("Date")
NativeTimeSubclass = create_datetime_subclass("Time")
NativeDateTimeSubclass = create_datetime_subclass("DateTime")
NativeDeltaSubclass = create_datetime_subclass("Delta")


class ManagedNativeDateSubclass(NativeDateSubclass):
    pass


class ManagedNativeTimeSubclass(NativeTimeSubclass):
    pass


class ManagedNativeDateTimeSubclass(NativeDateTimeSubclass):
    pass


class ManagedNativeDeltaSubclass(NativeDeltaSubclass):
    pass


class TestPyDateTime(CPyExtTestCase):

    test_PyDateTime_GET_YEAR = CPyExtFunction(
        lambda args: args[0].year,
        lambda: (
            (datetime.date(year=2023, month=2, day=12),),
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_GET_MONTH = CPyExtFunction(
        lambda args: args[0].month,
        lambda: (
            (datetime.date(year=2023, month=2, day=12),),
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_GET_DAY = CPyExtFunction(
        lambda args: args[0].day,
        lambda: (
            (datetime.date(year=2023, month=2, day=12),),
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_HOUR = CPyExtFunction(
        lambda args: args[0].hour,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_MINUTE = CPyExtFunction(
        lambda args: args[0].minute,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_SECOND = CPyExtFunction(
        lambda args: args[0].second,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_MICROSECOND = CPyExtFunction(
        lambda args: args[0].microsecond,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_FOLD = CPyExtFunction(
        lambda args: args[0].fold,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DATE_GET_TZINFO = CPyExtFunction(
        lambda args: args[0].tzinfo,
        lambda: (
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9),),
            (datetime.datetime(year=2023, month=2, day=12, hour=3, minute=14, second=8, microsecond=9, tzinfo=datetime.timezone.utc),),
        ),
        code='#include "datetime.h"',
        resultspec="O",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_HOUR = CPyExtFunction(
        lambda args: args[0].hour,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_MINUTE = CPyExtFunction(
        lambda args: args[0].minute,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_SECOND = CPyExtFunction(
        lambda args: args[0].second,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_MICROSECOND = CPyExtFunction(
        lambda args: args[0].microsecond,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_FOLD = CPyExtFunction(
        lambda args: args[0].fold,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_TIME_GET_TZINFO = CPyExtFunction(
        lambda args: args[0].tzinfo,
        lambda: (
            (datetime.time(hour=3, minute=14, second=8, microsecond=9),),
            (datetime.time(hour=3, minute=14, second=8, microsecond=9, tzinfo=datetime.timezone.utc),),
        ),
        code='#include "datetime.h"',
        resultspec="O",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DELTA_GET_DAYS = CPyExtFunction(
        lambda args: args[0].days,
        lambda: (
            (datetime.timedelta(days=3, seconds=8, microseconds=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DELTA_GET_SECONDS = CPyExtFunction(
        lambda args: args[0].seconds,
        lambda: (
            (datetime.timedelta(days=3, seconds=8, microseconds=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )

    test_PyDateTime_DELTA_GET_MICROSECONDS = CPyExtFunction(
        lambda args: args[0].microseconds,
        lambda: (
            (datetime.timedelta(days=3, seconds=8, microseconds=9),),
        ),
        code='#include "datetime.h"',
        resultspec="i",
        argspec='O',
        arguments=("PyObject* datetime",),
        cmpfunc=unhandled_error_compare,
    )


class TestDateTime(object):

    def test_date_type(self):
        TestDate = CPyExtType("TestDate",
                             """                             
                             PyTypeObject* getDateType() {
                                PyDateTime_IMPORT;
                                PyTypeObject* t = PyDateTimeAPI->DateType;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getDateType", (PyCFunction)getDateType, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDate()
        assert tester.getDateType() == datetime.date

    def test_datetime_type(self):
        TestDateTime = CPyExtType("TestDateTime",
                             """                             
                             PyTypeObject* getDateTimeType() {
                                PyDateTime_IMPORT;
                                PyTypeObject* t = PyDateTimeAPI->DateTimeType;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getDateTimeType", (PyCFunction)getDateTimeType, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDateTime()
        assert tester.getDateTimeType() == datetime.datetime

    def test_time_type(self):
        TestTime = CPyExtType("TestTime",
                             """                             
                             PyTypeObject* getTimeType() {
                                PyDateTime_IMPORT;
                                PyTypeObject* t = PyDateTimeAPI->TimeType;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getTimeType", (PyCFunction)getTimeType, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTime()
        assert tester.getTimeType() == datetime.time

    def test_timedelta_type(self):
        TestTimeDelta = CPyExtType("TestTimeDelta",
                             """                             
                             PyTypeObject* getTimeDeltaType() {
                                PyDateTime_IMPORT;
                                PyTypeObject* t = PyDateTimeAPI->DeltaType;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getTimeDeltaType", (PyCFunction)getTimeDeltaType, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTimeDelta()
        assert tester.getTimeDeltaType() == datetime.timedelta

    def test_tzinfo_type(self):
        TestTZInfo = CPyExtType("TestTZInfo",
                             """                             
                             PyTypeObject* getTZInfoType() {
                                PyDateTime_IMPORT;
                                PyTypeObject* t = PyDateTimeAPI->TZInfoType;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getTZInfoType", (PyCFunction)getTZInfoType, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTZInfo()
        assert tester.getTZInfoType() == datetime.tzinfo

    def test_timezone(self):
        TestTimezone = CPyExtType("TestTimezone",
                             """                             
                             PyObject* getTimezone() {
                                PyDateTime_IMPORT;
                                PyObject* t = PyDateTimeAPI->TimeZone_UTC;
                                Py_XINCREF(t);
                                return t;
                             }
                             """,
                             tp_methods='{"getTimezone", (PyCFunction)getTimezone, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTimezone()
        assert tester.getTimezone() == datetime.timezone.utc


    def test_date_from_date(self):
        TestDateFromDate = CPyExtType("TestDateFromDate",
                             """                             
                             PyObject* getDate() {
                                PyDateTime_IMPORT;
                                PyObject* o = PyDateTimeAPI->Date_FromDate(1, 1, 1, PyDateTimeAPI->DateType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDate", (PyCFunction)getDate, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDateFromDate()
        assert tester.getDate() == datetime.date(1, 1, 1)

    def test_datetime_from_date_and_time(self):
        TestDateTimeFromDateAndTime = CPyExtType("TestDateTimeFromDateAndTime",
                             """                             
                             PyObject* getDateTime() {
                                PyDateTime_IMPORT;
                                PyObject* o = PyDateTimeAPI->DateTime_FromDateAndTime(1, 1, 1, 1, 1, 1, 1, Py_None, PyDateTimeAPI->DateTimeType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDateTime", (PyCFunction)getDateTime, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDateTimeFromDateAndTime()
        assert tester.getDateTime() == datetime.datetime(1, 1, 1, 1, 1, 1, 1)

    def test_time_from_time(self):
        TestTimeFromTime = CPyExtType("TestTimeFromTime",
                             """                             
                             PyObject* getTime() {
                                PyDateTime_IMPORT;                               
                                PyObject* o = PyDateTimeAPI->Time_FromTime(1, 1, 1, 1, Py_None, PyDateTimeAPI->TimeType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getTime", (PyCFunction)getTime, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTimeFromTime()
        assert tester.getTime() == datetime.time(1, 1, 1, 1)

    def test_delta_from_delta(self):
        TestDeltaFromDelta = CPyExtType("TestDeltaFromDelta",
                             """                             
                             PyObject* getDelta() {
                                PyDateTime_IMPORT;
                                PyObject* o = PyDateTimeAPI->Delta_FromDelta(1, 1, 1, 1, PyDateTimeAPI->DeltaType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDelta", (PyCFunction)getDelta, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDeltaFromDelta()

        assert tester.getDelta() == datetime.timedelta(1, 1, 1)

    def test_timezone_from_timezone(self):
        TestTimezoneFromTimezone = CPyExtType("TestTimezoneFromTimezone",
                             """                             
                             PyObject* getTZ() {
                                PyDateTime_IMPORT;
                                PyObject* d = PyDateTimeAPI->Delta_FromDelta(0, 0, 1, 0, PyDateTimeAPI->DeltaType);
                                Py_XINCREF(d);
                                PyObject* o = PyDateTimeAPI->TimeZone_FromTimeZone(d, PyUnicode_FromString("CET"));
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getTZ", (PyCFunction)getTZ, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTimezoneFromTimezone()
        assert tester.getTZ() == datetime.timezone(datetime.timedelta(0,0,1,0), "CET")

    def test_time_from_time_and_fold(self):
        TestTimeFromTimeAndFold = CPyExtType("TestTimeFromTimeAndFold",
                             """                             
                             PyObject* getTime() {
                                PyDateTime_IMPORT;                               
                                PyObject* o = PyDateTimeAPI->Time_FromTimeAndFold(1, 1, 1, 1, Py_None, 1, PyDateTimeAPI->TimeType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getTime", (PyCFunction)getTime, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestTimeFromTimeAndFold()
        assert tester.getTime() == datetime.time(1, 1, 1, 1, None, fold=True)

    def test_datetime_from_date_and_time_and_fold(self):
        TestDateTimeFromDateAndTimeAndFold = CPyExtType("TestDateTimeFromDateAndTimeAndFold",
                             """                             
                             PyObject* getDateTime() {
                                PyDateTime_IMPORT;                               
                                PyObject* o = PyDateTimeAPI->DateTime_FromDateAndTimeAndFold(1, 1, 1, 1, 1, 1, 1, Py_None, 1, PyDateTimeAPI->DateTimeType);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDateTime", (PyCFunction)getDateTime, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDateTimeFromDateAndTimeAndFold()
        assert tester.getDateTime() == datetime.datetime(1, 1, 1, 1, 1, 1, 1, None, fold=True)

    def test_date_from_timestamp(self):
        TestDateFromTimestamp = CPyExtType("TestDateFromTimestamp",
                             """                             
                             PyObject* getDate(PyObject *self, PyObject *args) {
                                PyDateTime_IMPORT;          
                                PyObject* o = PyDateTimeAPI->Date_FromTimestamp((PyObject *)PyDateTimeAPI->DateType, args);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDate", (PyCFunction)getDate, METH_VARARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDateFromTimestamp()
        ts = datetime.datetime(1995, 4, 12).timestamp()
        assert tester.getDate(int(ts)) == datetime.date.fromtimestamp(int(ts))

    def test_datetime_from_timestamp(self):
        TestDatetimeFromTimestamp = CPyExtType("TestDatetimeFromTimestamp",
                             """                             
                             PyObject* getDatetime(PyObject *self, PyObject *args, PyObject *kwds) {                                
                                PyDateTime_IMPORT;                                          
                                PyObject* o = PyDateTimeAPI->DateTime_FromTimestamp((PyObject *)PyDateTimeAPI->DateTimeType, args, kwds);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDatetime", (PyCFunction)getDatetime, METH_VARARGS | METH_KEYWORDS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDatetimeFromTimestamp()
        ts = datetime.datetime(1995, 4, 12).timestamp()
        assert tester.getDatetime(int(ts)) == datetime.datetime.fromtimestamp(int(ts))

    def test_datetime_from_timestamp_and_tz(self):
        TestDatetimeFromTimestamp = CPyExtType("TestDatetimeFromTimestamp",
                             """                             
                             PyObject* getDatetime(PyObject *self, PyObject *args, PyObject *kwds) {                                
                                PyDateTime_IMPORT;                                          
                                PyObject* o = PyDateTimeAPI->DateTime_FromTimestamp((PyObject *)PyDateTimeAPI->DateTimeType, args, kwds);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDatetime", (PyCFunction)getDatetime, METH_VARARGS | METH_KEYWORDS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDatetimeFromTimestamp()
        ts = datetime.datetime(1995, 4, 12).timestamp()
        tz = datetime.timezone(datetime.timedelta(hours=3))
        assert tester.getDatetime(int(ts), tz) == datetime.datetime.fromtimestamp(int(ts), tz)

    def test_datetime_from_timestamp_and_tz_kwd(self):
        TestDatetimeFromTimestamp = CPyExtType("TestDatetimeFromTimestamp",
                             """                             
                             PyObject* getDatetime(PyObject *self, PyObject *args, PyObject *kwds) {                                
                                PyDateTime_IMPORT;                                          
                                PyObject* o = PyDateTimeAPI->DateTime_FromTimestamp((PyObject *)PyDateTimeAPI->DateTimeType, args, kwds);
                                Py_XINCREF(o);
                                return o;
                             }
                             """,
                             tp_methods='{"getDatetime", (PyCFunction)getDatetime, METH_VARARGS | METH_KEYWORDS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestDatetimeFromTimestamp()
        ts = datetime.datetime(1995, 4, 12).timestamp()
        tz = datetime.timezone(datetime.timedelta(hours=3))
        assert tester.getDatetime(int(ts), tz=tz) == datetime.datetime.fromtimestamp(int(ts), tz=tz)

    def test_write_and_invoke_member(self):
        TestWriteAndInvokeMemeber = CPyExtType("TestWriteAndInvokeMemeber",
                             """                             
                             PyObject* anotherDTFromDT(int y, int m, int d, PyTypeObject* t) {
                                return PyUnicode_FromString("foo");
                             }
                             
                             PyObject* getDate() {
                                PyDateTime_IMPORT;
                                PyObject *(*temp)(int, int, int, PyTypeObject*) = PyDateTimeAPI->Date_FromDate;
                                PyDateTimeAPI->Date_FromDate = anotherDTFromDT;
                                PyObject* r = PyDateTimeAPI->Date_FromDate(42, 1, 1, PyDateTimeAPI->DateType);
                                PyDateTimeAPI->Date_FromDate = temp;
                                Py_XINCREF(r);
                                return r;
                             }
                             """,
                             tp_methods='{"getDate", (PyCFunction)getDate, METH_NOARGS, ""}',
                             includes='#include "datetime.h"',
        )
        tester = TestWriteAndInvokeMemeber()
        assert tester.getDate() == "foo"


# GR-47546: Native subclasses of managed types don't work in managed mode
if not IS_MANAGED_LAUNCHER:
    class TestNativeSubclasses:
        def test_time(self):
            for t in (NativeTimeSubclass, ManagedNativeTimeSubclass):
                x = t(hour=6)
                assert is_native_object(x)
                assert x.hour == 6

        def test_date(self):
            for t in (NativeDateSubclass, ManagedNativeDateSubclass):
                x = t(1992, 4, 11)
                assert is_native_object(x)
                assert x.day == 11

        def test_datetime(self):
            for t in (NativeDateTimeSubclass, ManagedNativeDateTimeSubclass):
                x = t(1992, 4, 11, hour=13)
                assert is_native_object(x)
                assert x.day == 11
                assert x.hour == 13

        def test_timedelta(self):
            for t in (NativeDeltaSubclass, ManagedNativeDeltaSubclass):
                x = t(hours=6)
                assert is_native_object(x)
                assert x.seconds == 21600
