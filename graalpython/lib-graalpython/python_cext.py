# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import sys

__builtins_module_dict = None

def may_raise(error_result=native_null):
    if isinstance(error_result, type(may_raise)):
        # direct annotation
        return may_raise(native_null)(error_result)
    else:
        def decorator(fun):
            return make_may_raise_wrapper(fun, error_result)
        return decorator

@may_raise
def PyIter_Next(itObj):
    try:
        return next(itObj)
    except StopIteration:
        PyErr_Restore(None, None, None)
        return native_null


@may_raise
def PyCallIter_New(it, sentinel):
    return iter(it, sentinel)

@may_raise(-1)
def PyModule_AddObject(m, k, v):
    m.__dict__[k] = v
    return 0


def METH_UNSUPPORTED():
    raise NotImplementedError("unsupported message type")


def PyMethodDescr_Check(func):
    return 1 if isinstance(func, type(list.append)) else 0


# corresponds to PyInstanceMethod_Type
class instancemethod:
    def __init__(self, func):
        if not callable(func):
            raise TypeError("first argument must be callable")
        self.__func__ = func

    def __getattribute__(self, name):
        try:
            return object.__getattribute__(self, name)
        except AttributeError:
            return getattr(self.__func__, name)

    @property
    def __doc__(self):
        return self.__func__.__doc__

    def __call__(self, *args, **kwargs):
        return self.__func__(*args, **kwargs)

    def __get__(self, obj, type):
        if not obj:
            return self.__func__
        return PyMethod_New(self.__func__, obj)

    def __repr__(self):
        return "<instancemethod {} at ?>".format(self.__func__.__name__)


def PyInstanceMethod_New(func):
    return instancemethod(func)


@may_raise
def PyStaticMethod_New(func):
    return staticmethod(func)


getset_descriptor = type(type(PyInstanceMethod_New).__code__)


def PyObject_Str(o):
    return str(o)


def PyObject_Repr(o):
    return repr(o)


@may_raise(-1)
def PyTuple_Size(t):
    if not isinstance(t, tuple):
        __bad_internal_call(None, None, t)
    return len(t)


@may_raise
def PyTuple_GetSlice(t, start, end):
    if not isinstance(t, tuple):
        __bad_internal_call(None, None, t)
    return t[start:end]


@may_raise
def dict_from_list(lst):
    if len(lst) % 2 != 0:
        raise SystemError("list cannot be converted to dict")
    d = {}
    for i in range(0, len(lst), 2):
        d[lst[i]] = lst[i + 1]
    return d


@may_raise(-1)
def PyObject_DelItem(obj, key):
    del obj[key]
    return 0


@may_raise(-1)
def PyObject_SetItem(obj, key, value):
    obj[key] = value
    return 0


def PyObject_IsInstance(obj, typ):
    if isinstance(obj, typ):
        return 1
    else:
        return 0


def PyObject_IsSubclass(derived, cls):
    if issubclass(derived, cls):
        return 1
    else:
        return 0


@may_raise
def PyObject_RichCompare(left, right, op):
    return do_richcompare(left, right, op)


def PyObject_AsFileDescriptor(obj):
    if isinstance(obj, int):
        result = obj
    elif hasattr(obj, "fileno"):
        result = obj.fileno()
        if not isinstance(result, int):
            raise TypeError("fileno() returned a non-integer")
    else:
        raise TypeError("argument must be an int, or have a fileno() method")
    if result < 0:
        raise ValueError("file descriptor cannot be a negative integer (%d)" % result)
    return int(result)


@may_raise
def PyObject_GenericGetAttr(obj, attr):
    return object.__getattribute__(obj, attr)


@may_raise(-1)
def PyObject_GenericSetAttr(obj, attr, value):
    object.__setattr__(obj, attr, value)
    return 0


# Note: Any exception that occurs during getting the attribute will cause this function to return 0.
# This is intended and correct (see 'object.c: PyObject_HasAttr').
def PyObject_HasAttr(obj, attr):
    try:
        return 1 if hasattr(obj, attr) else 0
    except Exception:
        return 0


@may_raise(-1)
def PyObject_HashNotImplemented(obj):
    raise TypeError("unhashable type: '%s'" % type(obj).__name__)


@may_raise(-1)
def PyObject_IsTrue(obj):
    return 1 if obj else 0


@may_raise
def PyObject_Bytes(obj):
    if type(obj) == bytes:
        return obj
    if hasattr(obj, "__bytes__"):
        res = obj.__bytes__()
        if not isinstance(res, bytes):
            raise TypeError("__bytes__ returned non-bytes (type %s)" % type(res).__name__)
    return PyBytes_FromObject(obj)


## EXCEPTIONS

@may_raise(None)
def PyErr_CreateAndSetException(exception_type, value):
    if not _is_exception_class(exception_type):
        raise SystemError("exception %r not a BaseException subclass" % exception_type)
    if value is None:
        raise exception_type()
    else:
        # If value is already an exception object then raise it
        if isinstance(value, BaseException):
            raise value
        raise exception_type(value)


@may_raise(None)
def _PyErr_BadInternalCall(filename, lineno, obj):
    __bad_internal_call(filename, lineno, obj)


# IMPORTANT: only call from functions annotated with 'may_raise'
def __bad_internal_call(filename, lineno, obj):
    if filename is not None and lineno is not None:
        msg = "{!s}:{!s}: bad argument to internal function".format(filename, lineno)
    else:
        msg = "bad argument to internal function, was '{!s}' (type '{!s}')".format(obj, type(obj))
    raise SystemError(msg)


@may_raise
def PyErr_NewException(name, base, dictionary):
    dot_idx = name.find(".")
    if dot_idx == -1:
        raise SystemError( "PyErr_NewException: name must be module.class")
    if "__module__" not in dictionary:
        dictionary["__module__"] = name[:dot_idx]
    if not isinstance(base, tuple):
        bases = (base,)
    else:
        bases = base
    return type(name[dot_idx+1:], bases, dictionary)


def PyErr_NewExceptionWithDoc(name, doc, base, dictionary):
    new_exc_obj = PyErr_NewException(name, base, dictionary)
    new_exc_obj.__doc__ = doc
    return new_exc_obj


def PyErr_Format(err_type, format_str, args):
    PyErr_CreateAndSetException(err_type, format_str % args)


def PyErr_GetExcInfo():
    res = sys.exc_info()
    if res != (None, None, None):
        return res
    return native_null


def PyErr_PrintEx(set_sys_last_vars):
    typ, val, tb = sys.exc_info()
    if PyErr_GivenExceptionMatches(PyErr_Occurred(), SystemExit):
        _handle_system_exit()
    fetched = PyErr_Fetch()
    typ, val, tb = fetched if fetched is not native_null else (None, None, None)
    if typ is None:
        return
    if tb is native_null:
        tb = None
    if val.__traceback__ is None:
        val.__traceback__ = tb
    if set_sys_last_vars:
        try:
            sys.last_type = typ
            sys.last_value = val
            sys.last_traceback = tb
        except BaseException:
            PyErr_Restore(None, None, None)
    if hasattr(sys, "excepthook"):
        try:
            sys.excepthook(typ, val, tb)
        except BaseException as e:
            typ1, val1, tb1 = sys.exc_info()
            # not quite the same as 'PySys_WriteStderr' but close
            sys.__stderr__.write("Error in sys.excepthook:\n")
            PyErr_Display(typ1, val1, tb1);
            sys.__stderr__.write("\nOriginal exception was:\n");
            PyErr_Display(typ, val, tb);


def _handle_system_exit():
    typ, val, tb = sys.exc_info()
    rc = 0
    return_object = None
    if val is not None and hasattr(val, "code"):
        return_object = val.code
    if isinstance(val.code, int):
        rc = return_object
    else:
        PyErr_Restore(None, None, None)
        if sys.stderr:
            PyFile_WriteObject(return_object, sys.stderr, 1)
        else:
            # TODO should print to native 'stderr'
            print(return_object)

    import os
    os._exit(rc)


def PyErr_WriteUnraisable(obj):
    fetched = PyErr_Fetch()
    typ, val, tb = fetched if fetched is not native_null else (None, None, None)
    if val is None:
        # This means an invalid call, but this function is not supposed to raise exceptions
        return
    if tb is native_null:
        tb = None
    val.__traceback__ = tb
    PyTruffle_WriteUnraisable(val, obj)


def _is_exception_class(exc):
    return isinstance(exc, type) and issubclass(exc, BaseException)


def PyErr_GivenExceptionMatches(err, exc):
    if isinstance(exc, tuple):
        for e in exc:
            if PyErr_GivenExceptionMatches(err, e):
                return 1
        return 0
    if isinstance(err, BaseException):
        err = type(err)
    if _is_exception_class(err) and _is_exception_class(exc):
        return issubclass(err, exc)
    return exc is err


def _PyErr_NormalizeExceptionEx(exc, val, tb, recursion_depth):
    pass


def PyErr_NormalizeException(exc, val, tb):
    return _PyErr_NormalizeExceptionEx(exc, val, tb, 0)


@may_raise
def _PyErr_Warn(message, category, stack_level, source):
    import warnings
    # TODO: pass source again once we update to newer lib-python
    warnings.warn(message, category, stack_level)
    return None


@may_raise
def PyException_SetCause(exc, cause):
    exc.__cause__ = cause


@may_raise
def PyException_GetContext(exc):
    return exc.__context__


@may_raise
def PyException_SetContext(exc, context):
    exc.__context__ = context


##################### C EXT HELPERS

def PyTruffle_Debug(*args):
    __graalpython__.tdebug(*args)


def PyTruffle_GetBuiltin(name):
    return getattr(sys.modules["builtins"], name)


def check_argtype(idx, obj, typ):
    if not isinstance(obj, typ):
        raise TypeError("argument %d must be '%s', not '%s'" % (idx, str(typ), str(type(obj)).__name__))


def initialize_datetime_capi(capi_library):
    import datetime

    class PyDateTime_CAPI:
        DateType = datetime.date
        DateTimeType = datetime.datetime
        TimeType = datetime.time
        DeltaType = datetime.timedelta
        TZInfoType = datetime.tzinfo

        @staticmethod
        def Date_FromDate(y, m, d, typ):
            return typ(y, month=m, day=d)

        @staticmethod
        def DateTime_FromDateAndTime(y, mon, d, h, m, s, usec, tzinfo, typ):
            return PyDateTime_CAPI.DateTime_FromDateAndTimeAndFold(y, mon, d, h, m, s, usec, tzinfo, 0, typ)

        @staticmethod
        def Time_FromTime(h, m, s, usec, tzinfo, typ):
            return PyDateTime_CAPI.Time_FromTimeAndFold(h, m, s, usec, tzinfo, 0, typ)

        @staticmethod
        def Delta_FromDelta(d, s, microsec, normalize, typ):
            return typ(days=d, seconds=s, microseconds=microsec)

        @staticmethod
        def DateTime_FromTimestamp(cls, args, kwds):
            return cls(*args, **kwds)

        @staticmethod
        def Date_FromTimestamp(cls, args):
            return cls(*args)

        @staticmethod
        def DateTime_FromDateAndTimeAndFold(y, mon, d, h, m, s, usec, tz, fold, typ):
            return typ(y, month=mon, day=d, hour=h, minute=m, second=s, microseconds=usec, tzinfo=tz, fold=fold)

        @staticmethod
        def Time_FromTimeAndFold(h, m, s, us, tz, fold, typ):
            return typ(hour=h, minute=m, second=s, microsecond=us, tzinfo=tz, fold=fold)

    import_c_func("set_PyDateTime_typeids", capi_library)(PyDateTime_CAPI, PyDateTime_CAPI.DateType, PyDateTime_CAPI.DateTimeType, PyDateTime_CAPI.TimeType, PyDateTime_CAPI.DeltaType, PyDateTime_CAPI.TZInfoType)
    datetime.datetime_CAPI = import_c_func("truffle_create_datetime_capsule", capi_library)(wrap_PyDateTime_CAPI(PyDateTime_CAPI()))
    assert datetime.datetime_CAPI is not native_null
    datetime.date.__basicsize__ = import_c_func("get_PyDateTime_Date_basicsize", capi_library)()
    datetime.time.__basicsize__ = import_c_func("get_PyDateTime_Time_basicsize", capi_library)()
    datetime.datetime.__basicsize__ = import_c_func("get_PyDateTime_DateTime_basicsize", capi_library)()
    datetime.timedelta.__basicsize__ = import_c_func("get_PyDateTime_Delta_basicsize", capi_library)()


@may_raise
def PyEval_GetBuiltins():
    global __builtins_module_dict
    if not __builtins_module_dict:
        import builtins
        __builtins_module_dict = builtins.__dict__
    return __builtins_module_dict


def sequence_clear():
    return 0

