# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

import _imp
import sys


def Py_True():
    return True


def Py_False():
    return False


moduletype = type(sys)


def _PyModule_CreateInitialized_PyModule_New(name):
    # see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
    # comparison how they handle _Py_PackageContext
    if _imp._py_package_context:
        if _imp._py_package_context.endswith(name):
            name = _imp._py_package_context
            _imp._py_package_context = None
    return moduletype(name)


def PyModule_SetDocString(module, string):
    module.__doc__ = string


##################### DICT

def PyDict_New():
    return {}


def PyDict_Next(dictObj, pos, error_marker):
    if isinstance(dictObj, dict):
        curPos = 0
        max = len(dictObj)
        if pos >= max:
            return error_marker
        for key in dictObj:
            if curPos == pos:
                return key, dictObj[key]
            curPos = curPos + 1
    return error_marker


def PyDict_Size(dictObj):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    return len(dictObj)


def PyDict_Copy(dictObj):
    typ = val = tb = None
    try:
        if not isinstance(dictObj, dict):
            _PyErr_BadInternalCall(None, None, dictObj)
        return dictObj.copy()
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return None


def PyDict_GetItem(dictObj, key, error_marker):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    typ = val = tb = None
    try:
        return dictObj.get(key, error_marker)
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyDict_SetItem(dictObj, key, value):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    typ = val = tb = None
    try:
        dictObj[key] = value
        return 0
    except TypeError as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def PyDict_DelItem(dictObj, key):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    typ = val = tb = None
    try:
        del dictObj[key]
        return 0
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


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

def PyBytes_FromStringAndSize(string, encoding):
    return bytes(string, encoding)


def PyBytes_AsStringCheckEmbeddedNull(obj, encoding):
    if not PyBytes_Check(obj):
        raise TypeError('expected bytes, {!s} found'.format(type(obj)))
    result = obj.decode(encoding)
    for ch in obj:
        if ch == 0:
            raise ValueError('embedded null byte')
    return result


def PyBytes_Size(obj):
    assert isinstance(obj, bytes)
    return PyObject_Size(obj)


def PyBytes_Check(obj):
    return isinstance(obj, bytes)


def PyBytes_Concat(original, newpart):
    return original + newpart


def PyBytes_FromFormat(fmt, args):
    formatted = fmt % args
    return formatted.encode()


##################### LIST

def PyList_New(size, errormarker):
    typ = val = tb = None
    try:
        if size < 0:
            _PyErr_BadInternalCall(None, None, None)
        return []
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return errormarker


def PyList_GetItem(listObj, pos, errormarker):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        if pos < 0:
            raise IndexError("list index out of range")
        return listObj[pos]
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return errormarker


def PyList_SetItem(listObj, pos, newitem):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        if pos < 0:
            raise IndexError("list assignment index out of range")
        listObj[pos] = newitem
        return 0
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def PyList_Append(listObj, newitem):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        listObj.append(newitem)
        return 0
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def PyList_AsTuple(listObj, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        return tuple(listObj)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyList_GetSlice(listObj, ilow, ihigh, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        return listObj[ilow:ihigh]
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyList_Size(listObj):
    typ = val = tb = None
    try:
        if not isinstance(listObj, list):
            _PyErr_BadInternalCall(None, None, listObj)
        return len(listObj)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


##################### LONG

def PyLong_FromLongLong(n, signed, error_marker):
    typ = val = tb = None
    try:
        if signed:
            return int(n)
        else:
            return int(n & 0xffffffffffffffff)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyLong_AsPrimitive(n, signed, size, descr):
    typ = val = tb = None
    try:
        if isinstance(n, int):
            return TrufflePInt_AsPrimitive(n, signed, size, descr)
        else:
            return TrufflePInt_AsPrimitive(int(n), signed, size, descr)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def _PyLong_Sign(n):
    if n==0:
        return 0
    elif n < 0:
        return -1
    else:
        return 1


##################### FLOAT

def PyFloat_FromDouble(n, error_marker):
    typ = val = tb = None
    try:
        return float(n)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyFloat_AsPrimitive(n):
    typ = val = tb = None
    try:
        if isinstance(n, float):
            return TrufflePFloat_AsPrimitive(n)
        else:
            return TrufflePFloat_AsPrimitive(float(n))
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1.0


##################### NUMBER

def _safe_check(v, type_check):
    try:
        return type_check(v)
    except:
        return False


def PyNumber_Check(v):
    return _safe_check(v, lambda x: isinstance(int(x), int)) or _safe_check(v, lambda x: isinstance(float(x), float))


def PyNumber_BinOp(v, w, binop, name, error_marker):
    typ = val = tb = None
    try:
        if binop == 0:
            return v + w
        elif binop == 1:
            return v - w
        elif binop == 2:
            return v * w
        elif binop == 3:
            return v / w
        elif binop == 4:
            return v << w
        elif binop == 5:
            return v >> w
        elif binop == 6:
            return v | w
        elif binop == 7:
            return v & w
        elif binop == 8:
            return v ^ w
        elif binop == 9:
            return v // w
        elif binop == 10:
            return v % w
        else:
            raise SystemError("unknown binary operator %s" % name)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyNumber_UnaryOp(v, unaryop, name, error_marker):
    typ = val = tb = None
    try:
        if unaryop == 0:
            return +v
        elif unaryop == 1:
            return -v
        else:
            raise SystemError("unknown unary operator %s" % name)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyNumber_Index(v, error_marker):
    typ = val = tb = None
    try:
        if not hasattr(v, "__index__"):
            raise TypeError("'%s' object cannot be interpreted as an integer" % type(v))
        result = v.__index__()
        result_type = type(result)
        if not isinstance(result, int):
            raise TypeError("__index__ returned non-int (type %s)" % result_type)
        if result_type is not int:
            from warnings import warn
            warn("__index__ returned non-int (type %s). The ability to return an instance of a strict subclass of int "
                 "is deprecated, and may be removed in a future version of Python." % result_type)
        return result
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyNumber_Float(v, error_marker):
    typ = val = tb = None
    try:
        return float(v)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyNumber_Long(v, error_marker):
    typ = val = tb = None
    try:
        return int(v)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


##################### UNICODE


def PyUnicode_FromObject(o, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(o, str):
            raise TypeError("Can't convert '%s' object to str implicitly" % type(o).__name__)
        return str(o)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyUnicode_GetLength(o):
    typ = val = tb = None
    try:
        if not isinstance(o, str):
            raise TypeError("bad argument type for built-in operation");
        return len(o)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def PyUnicode_Concat(left, right, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(left, str):
            raise TypeError("must be str, not %s" % type(left));
        if not isinstance(right, str):
            raise TypeError("must be str, not %s" % type(right));
        return left + right
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyUnicode_FromEncodedObject(obj, encoding, errors, error_marker):
    typ = val = tb = None
    try:
        if isinstance(obj, bytes):
            return obj.decode(encoding, errors)
        if isinstance(obj, str):
            raise TypeError("decoding str is not supported")
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyUnicode_InternInPlace(s):
    return sys.intern(s)


def PyUnicode_Format(format, args, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(format, str):
            raise TypeError("Must be str, not %s" % type(format).__name__)
        return format % args
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyModule_AddObject(m, k, v):
    m.__dict__[k] = v
    return None


def PyStructSequence_New(typ):
    from posix import stat_result
    return stat_result([None] * stat_result.n_sequence_fields * 2)


def METH_KEYWORDS(fun):
    def wrapped(self, *args, **kwds):
        return fun(self, args, kwds)
    return wrapped


def METH_VARARGS(fun):
    def wrapped(self, *args):
        return fun(self, args)
    return wrapped


def METH_NOARGS(fun):
    def wrapped(self):
        return fun(self, None)
    return wrapped


def METH_O(fun):
    def wrapped(self, arg):
        return fun(self, (arg,));
    return wrapped


def METH_FASTCALL(fun):
    def wrapped(self, *args, **kwargs):
        return fun(self, args, len(args), kwargs)
    return wrapped


def METH_UNSUPPORTED(fun):
    raise NotImplementedError("unsupported message type")


def METH_DIRECT(fun):
    def wrapped(*args, **kwargs):
        return fun(*args, **kwargs)
    return wrapped


methodtype = classmethod.method


class modulemethod(methodtype):
    def __new__(cls, mod, func):
        return super().__new__(cls, mod, func)


class cstaticmethod():
    def __init__(self, func):
        self.__func__ = func

    def __get__(self, instance, owner=None):
        return methodtype(None, self.__func__)

    def __call__(*args, **kwargs):
        return self.__func__(None, *args, **kwargs)


def AddFunction(primary, name, cfunc, wrapper, doc, isclass=False, isstatic=False):
    func = wrapper(CreateFunction(name, cfunc))
    if isclass:
        func = classmethod(func)
    elif isstatic:
        func = cstaticmethod(func)
    elif isinstance(primary, moduletype):
        func = modulemethod(primary, func)
    func.__name__ = name
    func.__doc__ = doc
    if name == "__init__":
        def __init__(self, *args, **kwargs):
            if func(self, *args, **kwargs) != 0:
                raise TypeError("__init__ failed")
        object.__setattr__(primary, name, __init__)
    else:
        object.__setattr__(primary, name, func)


def AddMember(primary, name, memberType, offset, canSet, doc):
    member = property()
    getter = ReadMemberFunctions[memberType]
    def member_getter(self):
        return getter(self, offset)
    member.getter(member_getter)
    if canSet:
        setter = WriteMemberFunctions[memberType]
        def member_setter(self, value):
            setter(self, offset, value)
        member.setter(member_setter)
    member.__doc__ = doc
    object.__setattr__(primary, name, member)


def AddGetSet(primary, name, getter, setter, doc, closure):
    getset = property()
    getter_w = CreateFunction(name, getter)
    def member_getter(self):
        return capi_to_java(getter_w(self, closure))
    getset.getter(member_getter)
    setter_w = CreateFunction(name, setter)
    def member_setter(self, value):
        setter_w(self, value, closure)
        return None
    getset.setter(member_setter)
    getset.__doc__ = doc
    object.__setattr__(primary, name, getset)


def PyObject_Str(o):
    return str(o)


def PyObject_Repr(o):
    return repr(o)


def PyType_IsSubtype(a, b):
    return b in a.mro()


def PyTuple_New(size):
    return (None,) * size


def PyTuple_GetItem(t, n, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(t, tuple):
            _PyErr_BadInternalCall(None, None, t)
        return t[n]
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyTuple_Size(t):
    typ = val = tb = None
    try:
        if not isinstance(t, tuple):
            _PyErr_BadInternalCall(None, None, t)
        return len(t)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1


def PyTuple_GetSlice(t, start, end, error_marker):
    typ = val = tb = None
    try:
        if not isinstance(t, tuple):
            _PyErr_BadInternalCall(None, None, t)
        return t[start:end]
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyObject_Size(obj):
    try:
        return int(len(obj))
    except Exception:
        return -1


def PyObject_Call(callee, args, kwargs):
    return callee(*args, **kwargs)


def PyObject_CallMethod(rcvr, method, args):
    return getattr(rcvr, method)(*args)


def PyObject_GetItem(obj, key, error_marker):
    typ = val = tb = None
    try:
        return obj[key]
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyObject_SetItem(obj, key, value):
    typ = val = tb = None
    try:
        obj[key] = value
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    if typ:
        PyErr_Restore(typ, val, tb)
        return 1
    else:
        return 0


def PyObject_IsInstance(obj, typ):
    if isinstance(obj, typ):
        return 1
    else:
        return 0


def PyObject_RichCompare(left, right, op, errormarker):
    typ = val = tb = None
    try:
        return do_richcompare(left, right, op)
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return errormarker


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


def PyObject_SetAttr(obj, attr, value):
    typ = val = tb = None
    try:
        setattr(obj, attr, value)
    except BaseException as e:
        typ, val, tb = sys.exc_info()
    if typ:
        PyErr_Restore(typ, val, tb)
        return -1
    else:
        return 0


def PyObject_HasAttr(obj, attr):
    return 1 if hasattr(obj, attr) else 0


def PyObject_HashNotImplemented(obj):
    return TypeError("unhashable type: '%s'" % type(obj).__name__)


def PyObject_IsTrue(obj):
    return 1 if obj else 0

## EXCEPTIONS

def PyErr_CreateAndSetException(exception_type, msg):
    typ = val = tb = None
    try:
        if not _is_exception_class(exception_type):
            raise SystemError("exception %r not a BaseException subclass" % exception_type)
        if msg is None:
            raise exception_type()
        else:
            raise exception_type(msg)
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)


def _PyErr_BadInternalCall(filename, lineno, obj):
    if filename is not None and lineno is not None:
        msg = "{!s}:{!s}: bad argument to internal function".format(filename, lineno)
    else:
        msg = "bad argument to internal function, was '{!s}' (type '{!s}')".format(obj, type(obj))
    raise SystemError(msg)


def PyErr_NewException(name, base, dictionary):
    if "__module__" not in dictionary:
        dictionary["__module__"] = name.rpartition(".")[2]
    if not isinstance(base, tuple):
        bases = (base,)
    else:
        bases = base
    return type(name, bases, dictionary)


def PyErr_Format(err_type, format_str, args):
    PyErr_CreateAndSetException(err_type, format_str % args)


def PyErr_Fetch(consume, error_marker):
    res = sys.exc_info()
    if res != (None, None, None):
        # fetch 'consumes' the exception
        if consume:
            PyErr_Restore(None, None, None)
        return res
    return error_marker


def PyErr_PrintEx(set_sys_last_vars):
    typ, val, tb = sys.exc_info()
    if PyErr_GivenExceptionMatches(PyErr_Occurred(), SystemExit):
        _handle_system_exit()
    typ, val, tb = PyErr_Fetch(True, (None, None, None))
    if typ is None:
        return
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
    typ, val, tb = PyErr_Fetch(True, (None, None, None))
    try:
        if sys.stderr is None:
            return

        if obj:
            obj_str_arg = None
            try:
                obj_str_arg = repr(obj)
            except:
                obj_str_arg = "<object repr() failed>"
            sys.stderr.write("Exception ignored in: %s\n" % obj_str_arg)

        try:
            import tb
            tb.print_tb(tb, file=sys.stderr)
        except:
            pass

        if not typ:
            return

        if typ.__module__ is None or typ.__name__ is None:
            sys.stderr.write("<unknown>")

        str_exc = None
        try:
            str_exc = str(obj)
        except:
            str_exc = "<exception str() failed>"
        sys.stderr.write("%s.%s: %s" % (typ.__module__, typ.__name__, str_exc))
    except:
        pass


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


def _PyErr_NormalizeExceptionEx(exc, val, tb, recursion_depth, error_marker):
    pass


def PyErr_NormalizeException(exc, val, tb, error_marker):
    return _PyErr_NormalizeExceptionEx(exc, val, tb, 0, error_marker)


def _PyErr_Warn(message, category, stack_level, source, error_marker):
    typ = val = tb = None
    try:
        import warnings
        warnings.warn(message, category, stack_level, source)
        return None
    except:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


## FILE

def PyFile_WriteObject(obj, file, flags):
    typ = val = tb = None
    try:
        if file is None:
            raise TypeError("writeobject with NULL file")

        if flags & 0x1:
            write_value = str(obj)
        else:
            write_value = repr(obj)
        file.write(write_value)
        return 0
    except BaseException:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return -1

##################### C EXT HELPERS

def PyTruffle_Debug(*args):
    __tdebug__(*args)


def PyTruffle_Type(type_name):
    if type_name == "mappingproxy":
        return type(dict().keys())
    elif type_name == "NotImplementedType":
        return type(NotImplemented)
    else:
        return getattr(sys.modules["builtins"], type_name)


def check_argtype(idx, obj, typ):
    if not isinstance(obj, typ):
        raise TypeError("argument %d must be '%s', not '%s'" % (idx, str(typ), str(type(obj))))


def import_c_func(name):
    return CreateFunction(name, capi[name])


capi = capi_to_java = None
def initialize_capi(capi_library):
    """This method is called from a C API constructor function"""
    global capi
    capi = capi_library
    initialize_member_accessors()
    global capi_to_java
    capi_to_java = import_c_func("to_java")


ReadMemberFunctions = []
WriteMemberFunctions = []
def initialize_member_accessors():
    # order must correspond to member type definitions in structmember.h
    for memberFunc in ["ReadShortMember", "ReadIntMember", "ReadLongMember",
                       "ReadFloatMember", "ReadDoubleMember",
                       "ReadStringMember", "ReadObjectMember", "ReadCharMember",
                       "ReadByteMember", "ReadUByteMember", "ReadUShortMember",
                       "ReadUIntMember", "ReadULongMember", "ReadStringMember",
                       "ReadBoolMember", "ReadObjectExMember",
                       "ReadObjectExMember", "ReadLongLongMember",
                       "ReadULongLongMember", "ReadPySSizeT"]:
        ReadMemberFunctions.append(import_c_func(memberFunc))
    ReadMemberFunctions.append(lambda x: None)
    for memberFunc in ["WriteShortMember", "WriteIntMember", "WriteLongMember",
                       "WriteFloatMember", "WriteDoubleMember",
                       "WriteStringMember", "WriteObjectMember",
                       "WriteCharMember", "WriteByteMember", "WriteUByteMember",
                       "WriteUShortMember", "WriteUIntMember",
                       "WriteULongMember", "WriteStringMember",
                       "WriteBoolMember", "WriteObjectExMember",
                       "WriteObjectExMember", "WriteLongLongMember",
                       "WriteULongLongMember", "WritePySSizeT"]:
        WriteMemberFunctions.append(import_c_func(memberFunc))
    WriteMemberFunctions.append(lambda x,v: None)


def PyImport_ImportModule(name, error_marker):
    try:
        return __import__(name)
    except Exception:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker


def PyRun_String(source, typ, globals, locals, error_marker):
    try:
        return exec(compile(source, typ, typ), globals, locals)
    except Exception:
        typ, val, tb = sys.exc_info()
    PyErr_Restore(typ, val, tb)
    return error_marker
