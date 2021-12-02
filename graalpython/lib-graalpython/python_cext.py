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
import _thread

__builtins_module_dict = None

def may_raise(error_result=native_null):
    if isinstance(error_result, type(may_raise)):
        # direct annotation
        return may_raise(native_null)(error_result)
    else:
        def decorator(fun):
            return make_may_raise_wrapper(fun, error_result)
        return decorator

##################### ABSTRACT

@may_raise(-1)
def PySequence_DelItem(o,i):
    del o[i]
    return 0

@may_raise
def PyModule_GetNameObject(module_obj):
    return module_obj.__name__

##################### SET, FROZENSET


@may_raise
def PySet_New(iterable):
    if iterable:
        return set(iterable)
    else:
        return set()


@may_raise(-1)
def PySet_Contains(anyset, item):
    if not (isinstance(anyset, set) or isinstance(anyset, frozenset)):
        __bad_internal_call(None, None, anyset)
    return item in anyset


@may_raise
def PySet_NextEntry(anyset, pos):
    curPos = 0
    max = len(anyset)
    if pos >= max:
        return native_null
    for key in anyset:
        if curPos == pos:
            return key, hash(key)
        curPos = curPos + 1
    return native_null


@may_raise
def PySet_Pop(anyset):
    if not isinstance(anyset, set):
        __bad_internal_call(None, None, anyset)
    return anyset.pop()


@may_raise
def PyFrozenSet_New(iterable):
    if iterable:
        return frozenset(iterable)
    else:
        return frozenset()


@may_raise(-1)
def PySet_Discard(s, key):
    if key in s:
        s.discard(key)
        return 1
    return 0


@may_raise(-1)
def PySet_Clear(s):
    s.clear()
    return 0


##################### MAPPINGPROXY


def PyDictProxy_New(mapping):
    mappingproxy = type(type.__dict__)
    return mappingproxy(mapping)


def Py_DECREF(obj):
    pass


def Py_INCREF(obj):
    pass


def Py_XINCREF(obj):
    pass


def PyObject_LEN(obj):
    return len(obj)


##################### BYTES

def PyBytes_Size(obj):
    return PyObject_Size(obj)


def PyBytes_Check(obj):
    return isinstance(obj, bytes)


@may_raise
def PyBytes_Concat(original, newpart):
    return original + newpart


@may_raise
def PyBytes_FromFormat(fmt, args):
    formatted = fmt % args
    return formatted.encode()


@may_raise
def PyBytes_Join(sep, iterable):
    return sep.join(iterable)


@may_raise
def PyBytes_FromObject(obj):
    if type(obj) == bytes:
        return obj
    if isinstance(obj, (list, tuple, memoryview)) or (not isinstance(obj, str) and hasattr(obj, "__iter__")):
        return bytes(obj)
    raise TypeError("cannot convert '%s' object to bytes" % type(obj).__name__)


##################### LIST

@may_raise
def PyList_New(size):
    if size < 0:
        __bad_internal_call(None, None, None)
    return [None] * size


@may_raise
def PyList_GetItem(listObj, pos):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    if pos < 0:
        raise IndexError("list index out of range")
    return listObj[pos]


@may_raise(-1)
def PyList_Append(listObj, newitem):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    listObj.append(newitem)
    return 0


@may_raise
def PyList_AsTuple(listObj):
    if not isinstance(listObj, list):
        raise SystemError("expected list type")
    return tuple(listObj)


@may_raise
def PyList_GetSlice(listObj, ilow, ihigh):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    return listObj[ilow:ihigh]


@may_raise(-1)
def PyList_SetSlice(listObj, ilow, ihigh, s):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    listObj[ilow:ihigh] = s
    return 0


@may_raise
def PyList_Extend(listObj, iterable):
    listObj.extend(iterable)
    return None


@may_raise(-1)
def PyList_Size(listObj):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    return len(listObj)


@may_raise(-1)
def PyList_Sort(listObj):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    listObj.sort()
    return 0

@may_raise(-1)
def PyList_Insert(listObj, i, item):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    listObj.insert(i, item)
    return 0


##################### LONG

def _PyLong_Sign(n):
    if n==0:
        return 0
    elif n < 0:
        return -1
    else:
        return 1


@may_raise
def PyLong_FromDouble(d):
    return int(d)


@may_raise
def PyLong_FromString(string, base, negative):
    result = int(string, base)
    if negative:
        return -result
    else:
        return result


##################### FLOAT

@may_raise
def PyFloat_FromDouble(n):
    return float(n)


##################### COMPLEX

@may_raise
def PyComplex_AsCComplex(n):
    obj = complex(n)
    return (obj.real, obj.imag)


@may_raise(-1.0)
def PyComplex_RealAsDouble(n):
    if isinstance(n, complex):
        return n.real
    return n.__float__()


def PyComplex_ImagAsDouble(n):
    if isinstance(n, complex):
        return n.imag
    return 0.0


@may_raise
def PyComplex_FromDoubles(real, imag):
    return complex(real, imag)


##################### NUMBER

def _safe_check(v, type_check):
    try:
        return type_check(v)
    except:
        return False


def PyNumber_Check(v):
    return _safe_check(v, lambda x: isinstance(int(x), int)) or _safe_check(v, lambda x: isinstance(float(x), float))


@may_raise
def PyNumber_Index(v):
    if not hasattr(v, "__index__"):
        raise TypeError("'%s' object cannot be interpreted as an integer" % type(v).__name__)
    result = v.__index__()
    result_type = type(result)
    if not isinstance(result, int):
        raise TypeError("__index__ returned non-int (type %s)" % result_type.__name__)
    if result_type is not int:
        from warnings import warn
        warn("__index__ returned non-int (type %s). The ability to return an instance of a strict subclass of int "
             "is deprecated, and may be removed in a future version of Python." % result_type.__name__)
    return result


@may_raise
def PyNumber_Long(v):
    return int(v)


@may_raise
def PyNumber_Absolute(v):
    return abs(v)


@may_raise
def PyNumber_Divmod(a, b):
    return divmod(a, b)


@may_raise
def PyNumber_ToBase(n, base):
    b_index = PyNumber_Index(n)
    if base == 2:
        return bin(b_index)
    elif base == 8:
        return oct(b_index)
    elif base == 10:
        return str(b_index)
    elif base == 16:
        return hex(b_index)
    raise ValueError("Unsupported base " + str(base))


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


##################### SEQUENCE


@may_raise
def PySequence_Tuple(obj):
    return tuple(obj)


@may_raise
def PySequence_List(obj):
    return list(obj)


@may_raise(-1)
def PySequence_SetItem(obj, key, value):
    if not hasattr(obj, '__setitem__'):
        raise TypeError("'%s' object does not support item assignment)" % type(obj).__name__)
    if len(obj) < 0:
        return -1
    obj.__setitem__(key, value)
    return 0


@may_raise
def PySequence_GetSlice(obj, low, high):
    return obj[low:high]


@may_raise(-1)
def PySequence_Contains(haystack, needle):
    return needle in haystack


@may_raise
def PySequence_Repeat(obj, n):
    if not PySequence_Check(obj):
        raise TypeError("'%s' object can't be repeated" % type(obj).__name__)
    return obj * n


@may_raise
def PySequence_InPlaceRepeat(obj, n):
    if not PySequence_Check(obj):
        raise TypeError("'%s' object can't be repeated" % type(obj).__name__)
    obj *= n
    return obj


@may_raise
def PySequence_Concat(s, o):
    if not PySequence_Check(s):
        raise TypeError("'%s' object can't be concatenated" % type(s).__name__)
    return s + o


@may_raise
def PySequence_InPlaceConcat(s, o):
    if not PySequence_Check(s):
        raise TypeError("'%s' object can't be concatenated" % type(s).__name__)
    s += o
    return s


##################### UNICODE


@may_raise
def PyUnicode_FromObject(o):
    if not isinstance(o, str):
        raise TypeError("Can't convert '%s' object to str implicitly" % type(o).__name__)
    return str(o)


@may_raise(-1)
def PyUnicode_GetLength(o):
    if not isinstance(o, str):
        raise TypeError("bad argument type for built-in operation");
    return len(o)


@may_raise
def PyUnicode_Concat(left, right):
    if not isinstance(left, str):
        raise TypeError("must be str, not %s" % type(left).__name__)
    if not isinstance(right, str):
        raise TypeError("must be str, not %s" % type(right).__name__)
    return left + right


@may_raise
def PyUnicode_FromEncodedObject(obj, encoding, errors):
    if isinstance(obj, bytes):
        return obj.decode(encoding, errors)
    if isinstance(obj, str):
        raise TypeError("decoding str is not supported")


def PyUnicode_InternInPlace(s):
    return sys.intern(s)


@may_raise
def PyUnicode_Format(format, args):
    if not isinstance(format, str):
        raise TypeError("Must be str, not %s" % type(format).__name__)
    return format % args


@may_raise(-1)
def PyUnicode_FindChar(string, char, start, end, direction):
    if not isinstance(string, str):
        raise TypeError("Must be str, not %s" % type(string).__name__)
    if direction > 0:
        return string.find(chr(char), start, end)
    else:
        return string.rfind(chr(char), start, end)


@may_raise
def PyUnicode_Substring(string, start, end):
    if not isinstance(string, str):
        raise TypeError("Must be str, not %s" % type(string).__name__)
    return string[start:end]


@may_raise
def PyUnicode_Join(separator, seq):
    if not isinstance(separator, str):
        raise TypeError("Must be str, not %s" % type(separator).__name__)
    return separator.join(seq)


@may_raise(-1)
def PyUnicode_Compare(left, right):
    if left == right:
        return 0
    elif left < right:
        return -1
    else:
        return 1

_codecs_module = None

@may_raise
def PyUnicode_AsUnicodeEscapeString(string):
    global _codecs_module
    if not _codecs_module:
        import _codecs as _codecs_module
    return _codecs_module.unicode_escape_encode(string)[0]

@may_raise(-1)
def PyUnicode_Tailmatch(s, substr, start, end, direction):
    if direction > 0:
        return 1 if s[start:end].endswith(substr) else 0
    return 1 if s[start:end].startswith(substr) else 0


@may_raise
def PyUnicode_AsEncodedString(s, encoding, errors):
    return s.encode(encoding, errors)


@may_raise
def PyUnicode_Replace(s, substr, replstr, count):
    return s.replace(substr, replstr, count)


##################### MEMORY_VIEW
@may_raise
def PyMemoryView_GetContiguous(obj, buffertype, order_int):
    PyBUF_READ = 0x100
    PyBUF_WRITE = 0x200
    assert buffertype == PyBUF_READ or buffertype == PyBUF_WRITE
    order = chr(order_int)
    assert order == 'C' or order == 'F' or order == 'A'
    mv = memoryview(obj)
    release = True
    try:
        if buffertype == PyBUF_WRITE and mv.readonly:
            raise BufferError("underlying buffer is not writable")
        if mv.contiguous:
            release = False
            return mv
        if buffertype == PyBUF_WRITE:
            raise BufferError("writable contiguous buffer requested for a non-contiguous object.")
        mv_bytes = memoryview(mv.tobytes(order))
        if mv.format == 'B':
            return mv_bytes
        else:
            try:
                return mv_bytes.cast(mv.format)
            finally:
                mv_bytes.release()
    finally:
        if release:
            mv.release()


@may_raise(-1)
def PyModule_AddObject(m, k, v):
    m.__dict__[k] = v
    return 0


def METH_UNSUPPORTED():
    raise NotImplementedError("unsupported message type")


def PyMethod_New(func, self):
    # TODO we should use the method constructor
    # e.g. methodtype(func, self)
    def bound_function(*args, **kwargs):
        return func(self, *args, **kwargs)
    return bound_function


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


## FILE

@may_raise(-1)
def PyFile_WriteObject(obj, file, flags):
    if file is None:
        raise TypeError("writeobject with NULL file")

    if flags & 0x1:
        write_value = str(obj)
    else:
        write_value = repr(obj)
    file.write(write_value)
    return 0


##  CODE

codetype = type(may_raise.__code__)


@may_raise
def PyCode_New(*args):
    # Add posonlyargcount (2nd arg)
    args = (args[0], 0) + args[1:]
    return codetype(*args)


@may_raise
def PyCode_NewWithPosOnlyArgs(*args):
    return codetype(*args)


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
def PyImport_ImportModule(name):
    return __import__(name, fromlist=["*"])


@may_raise
def PyImport_GetModuleDict():
    return sys.modules

@may_raise
def PyRun_String(source, typ, globals, locals):
    return exec(compile(source, typ, typ), globals, locals)


@may_raise
def PyThread_allocate_lock():
    return _thread.allocate_lock()


@may_raise
def PyThread_acquire_lock(lock, waitflag):
    return 1 if lock.acquire(waitflag) else 0


@may_raise
def PyThread_release_lock(lock):
    return lock.release()


@may_raise
def PySlice_New(start, stop, step):
    return slice(start, stop, step)

@may_raise
def PyEval_GetBuiltins():
    global __builtins_module_dict
    if not __builtins_module_dict:
        import builtins
        __builtins_module_dict = builtins.__dict__
    return __builtins_module_dict


def sequence_clear():
    return 0

namespace_type = None
@may_raise
def _PyNamespace_New(kwds):
    global namespace_type
    if not namespace_type:
        from types import SimpleNamespace as namespace_type
    return namespace_type(**kwds)


@may_raise
def PySys_GetObject(name):
    try:
        return getattr(sys, name)
    except AttributeError:
        raise KeyError(name)
