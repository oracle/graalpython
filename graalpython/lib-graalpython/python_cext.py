# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import _imp
import sys
import _thread

capi = capi_to_java = None
_capi_hooks = []


def register_capi_hook(hook):
    assert callable(hook)
    if capi:
        hook()
    else:
        _capi_hooks.append(hook)
    

def may_raise(error_result=native_null):
    if isinstance(error_result, type(may_raise)):
        # direct annotation
        return may_raise(native_null)(error_result)
    else:
        def decorator(fun):
            return make_may_raise_wrapper(fun, error_result)
        return decorator


def Py_ErrorHandler():
    return to_sulong(error_handler)


def Py_NotImplemented():
    return NotImplemented


def Py_True():
    return True


def Py_False():
    return False


def Py_Ellipsis():
    return ...


moduletype = type(sys)


def _PyModule_CreateInitialized_PyModule_New(name):
    # see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
    # comparison how they handle _Py_PackageContext
    if _imp._py_package_context:
        if _imp._py_package_context.endswith(name):
            name = _imp._py_package_context
            _imp._py_package_context = None
    new_module = moduletype(name)
    # TODO: (tfel) I don't think this is the right place to set it, but somehow
    # at least in the import of sklearn.neighbors.dist_metrics through
    # sklearn.neighbors.ball_tree the __package__ attribute seems to be already
    # set in CPython. To not produce a warning, I'm setting it here, although I
    # could not find what CPython really does
    if "." in name:
        new_module.__package__ = name.rpartition('.')[0]
    return new_module


def PyModule_SetDocString(module, string):
    module.__doc__ = string


def PyModule_NewObject(name):
    return moduletype(name)


@may_raise
def PyModule_GetNameObject(module_obj):
    return module_obj.__name__


##################### DICT

def PyDict_New():
    return {}


@may_raise
def PyDict_Next(dictObj, pos):
    if not isinstance(dictObj, dict):
        return native_null
    curPos = 0
    max = len(dictObj)
    if pos >= max:
        return native_null
    for key in dictObj:
        if curPos == pos:
            return key, dictObj[key], hash(key)
        curPos = curPos + 1
    return native_null


@may_raise(-1)
def PyDict_Size(dictObj):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    return len(dictObj)


@may_raise(None)
def PyDict_Copy(dictObj):
    if not isinstance(dictObj, dict):
        __bad_internal_call(None, None, dictObj)
    return dictObj.copy()


@may_raise
def PyDict_GetItem(dictObj, key):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    return dictObj.get(key, native_null)


@may_raise(-1)
def PyDict_SetItem(dictObj, key, value):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    dictObj[key] = value
    return 0


@may_raise(-1)
def PyDict_SetItem_KnownHash(dictObj, key, value, given_hash):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    assert hash(key) == given_hash, "hash mismatch: known hash is different to computed hash"
    dictObj[key] = value
    return 0


@may_raise(-1)
def PyDict_DelItem(dictObj, key):
    if not isinstance(dictObj, dict):
        raise TypeError('expected dict, {!s} found'.format(type(dictObj)))
    del dictObj[key]
    return 0


@may_raise(-1)
def PyDict_Contains(dictObj, key):
    if not isinstance(dictObj, dict):
        __bad_internal_call(None, None, dictObj)
    return key in dictObj


@may_raise(-1)
def PyDict_Merge(a, b, override):
    if override:
        a.update(b)
    else:
        for k in b:
            if not k in a:
                a[k] = b[k]
    return 0

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


def PyTruffle_Object_LEN(obj):
    return len(to_java(obj))


##################### BYTES

def PyBytes_AsStringCheckEmbeddedNull(obj, encoding):
    if not PyBytes_Check(obj):
        raise TypeError('expected bytes, {!s} found'.format(type(obj)))
    result = obj.decode(encoding)
    for ch in obj:
        if ch == 0:
            raise ValueError('embedded null byte')
    return result


def PyBytes_Size(obj):
    return PyObject_Size(obj)


def PyBytes_Check(obj):
    return isinstance(obj, bytes)


@may_raise
def PyBytes_Concat(original, newpart):
    return original + newpart


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
def PyList_SetItem(listObj, pos, newitem):
    if not isinstance(listObj, list):
        __bad_internal_call(None, None, listObj)
    if pos < 0:
        raise IndexError("list assignment index out of range")
    listObj[pos] = newitem
    return 0


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


##################### LONG

@may_raise(-1)
def PyLong_AsPrimitive(n, signed, size):
    return TrufflePInt_AsPrimitive(int(n), signed, size)


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
def PyNumber_BinOp(v, w, binop):
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
    elif binop == 12:
        return v @ w
    else:
        raise SystemError("unknown binary operator (code=%s)" % binop)


def _binop_name(binop):
    if binop == 0:
        return "+"
    elif binop == 1:
        return "-"
    elif binop == 2:
        return "*"
    elif binop == 3:
        return "/"
    elif binop == 4:
        return "<<"
    elif binop == 5:
        return ">>"
    elif binop == 6:
        return "|"
    elif binop == 7:
        return "&"
    elif binop == 8:
        return "^"
    elif binop == 9:
        return "//"
    elif binop == 10:
        return "%"
    elif binop == 12:
        return "@"


@may_raise
def PyNumber_InPlaceBinOp(v, w, binop):
    if binop == 0:
        v += w
    elif binop == 1:
        v -= w
    elif binop == 2:
        v *= w
    elif binop == 3:
        v /= w
    elif binop == 4:
        v <<= w
    elif binop == 5:
        v >>= w
    elif binop == 6:
        v |= w
    elif binop == 7:
        v &= w
    elif binop == 8:
        v ^= w
    elif binop == 9:
        v //= w
    elif binop == 10:
        v %= w
    elif binop == 12:
        v @= w
    else:
        raise SystemError("unknown in-place binary operator (code=%s)" % binop)

    # nothing else required; the operator will automatically fall back if 
    # no in-place operation is available
    return v


@may_raise
def PyNumber_UnaryOp(v, unaryop):
    if unaryop == 0:
        return +v
    elif unaryop == 1:
        return -v
    elif unaryop == 2:
        return ~v
    else:
        raise SystemError("unknown unary operator (code=%s)" % unaryop)


@may_raise
def PyNumber_Index(v):
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


@may_raise
def PySequence_GetItem(obj, key):
    if not hasattr(obj, '__getitem__'):
        raise TypeError("'%s' object does not support indexing)" % repr(obj))
    if len(obj) < 0:
        return native_null
    return obj[key]


@may_raise(-1)
def PySequence_SetItem(obj, key, value):
    if not hasattr(obj, '__setitem__'):
        raise TypeError("'%s' object does not support item assignment)" % repr(obj))
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
    if not PyTruffle_IsSequence(obj):
        raise TypeError("'%p' object can't be repeated", obj)
    return obj * n


@may_raise
def PySequence_InPlaceRepeat(obj, n):
    if not PyTruffle_IsSequence(obj):
        raise TypeError("'%p' object can't be repeated", obj)
    obj *= n
    return obj


@may_raise
def PySequence_Concat(s, o):
    if not (PyTruffle_IsSequence(s) and PyTruffle_IsSequence(o)):
        raise TypeError("'%p' object can't be repeated", s)
    return s + o


@may_raise
def PySequence_InPlaceConcat(s, o):
    if not (PyTruffle_IsSequence(s) and PyTruffle_IsSequence(o)):
        raise TypeError("'%p' object can't be repeated", s)
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
        raise TypeError("must be str, not %s" % type(left));
    if not isinstance(right, str):
        raise TypeError("must be str, not %s" % type(right));
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


##################### CAPSULE


class PyCapsule:
    name = None
    pointer = None
    context = None

    def __init__(self, name, pointer, destructor):
        self.name = name
        self.pointer = to_sulong(pointer)

    def __repr__(self):
        name = "NULL" if self.name is None else self.name
        quote = "" if self.name is None else '"'
        return "<capsule object %s%s%s at %p>" % (quote, name, quote, self.pointer)


@may_raise
def PyCapsule_GetContext(obj):
    if not isinstance(obj, PyCapsule) or obj.pointer is None:
        raise ValueError("PyCapsule_GetContext called with invalid PyCapsule object")
    return obj.context


@may_raise(-1)
def PyCapsule_SetContext(obj, ptr):
    if not isinstance(obj, PyCapsule):
        raise ValueError("PyCapsule_SetContext called with invalid PyCapsule object")
    obj.context = ptr
    return 0


@may_raise
def PyCapsule_GetPointer(obj, name):
    if not isinstance(obj, PyCapsule) or obj.pointer is None:
        raise ValueError("PyCapsule_GetPointer called with invalid PyCapsule object")
    if name != None and name != obj.name:
        raise ValueError("PyCapsule_GetPointer called with incorrect name")
    return obj.pointer


@may_raise
def PyCapsule_Import(name, no_block):
    obj = None
    mod = name.split(".")[0]
    try:
        obj = __import__(mod)
    except:
        raise ImportError('PyCapsule_Import could not import module "%s"' % name)
    for attr in name.split(".")[1:]:
        obj = getattr(obj, attr)
    if PyCapsule_IsValid(obj, name):
        return obj.pointer
    else:
        raise AttributeError('PyCapsule_Import "%s" is not valid' % name)


def PyCapsule_IsValid(obj, name):
    return (isinstance(obj, PyCapsule) and
            obj.pointer != None and
            obj.name == name)


@may_raise
def PyCapsule_GetName(obj):
    return obj.name


def PyModule_AddObject(m, k, v):
    m.__dict__[k] = v
    return None


@may_raise
def PyStructSequence_New(typ):
    n = len(typ._fields)
    return typ(*([None]*n))


namedtuple_type = None
@may_raise
def PyStructSequence_InitType2(type_name, type_doc, field_names, field_docs):
    assert len(field_names) == len(field_docs)
    global namedtuple_type
    if not namedtuple_type:
        from collections import namedtuple as namedtuple_type
    new_type = namedtuple_type(type_name, field_names)
    new_type.__doc__ = type_doc
    for i in range(len(field_names)):
        prop = getattr(new_type, field_names[i])
        assert isinstance(prop, property)
        prop.__doc__ = field_docs[i]
    # ensure '_fields' attribute; required in 'PyStructSequence_New'
    assert hasattr(new_type, "_fields")
    return new_type


def METH_UNSUPPORTED(fun):
    raise NotImplementedError("unsupported message type")


def METH_DIRECT(fun):
    return fun


class _C:
    def _m(self): pass
methodtype = type(_C()._m)


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


def AddFunction(primary, tpDict, name, cfunc, cwrapper, wrapper, doc, isclass=False, isstatic=False):
    owner = to_java_type(primary)
    if isinstance(owner, moduletype):
        # module case, we create the bound function-or-method
        func = PyCFunction_NewEx(name, cfunc, cwrapper, wrapper, owner, owner.__name__, doc)
        object.__setattr__(owner, name, func)
    else:
        func = wrapper(CreateFunction(name, cfunc, cwrapper, owner))
        if isclass:
            func = classmethod(func)
        elif isstatic:
            func = cstaticmethod(func)
        PyTruffle_SetAttr(func, "__name__", name)
        PyTruffle_SetAttr(func, "__doc__", doc)
        type_dict = to_java(tpDict)
        if name == "__init__":
            def __init__(self, *args, **kwargs):
                if func(self, *args, **kwargs) != 0:
                    raise TypeError("__init__ failed")
            type_dict[name] = __init__
        else:
            type_dict[name] = func


def PyCFunction_NewEx(name, cfunc, cwrapper, wrapper, self, module, doc):
    func = wrapper(CreateFunction(name, cfunc, cwrapper))
    PyTruffle_SetAttr(func, "__name__", name)
    PyTruffle_SetAttr(func, "__doc__", doc)
    method = PyTruffle_BuiltinMethod(self, func)
    PyTruffle_SetAttr(method, "__module__", to_java(module))
    return method


def PyMethod_New(func, self):
    # TODO we should use the method constructor
    # e.g. methodtype(func, self)
    def bound_function(*args, **kwargs):
        return func(self, *args, **kwargs)
    return bound_function


def AddMember(primary, tpDict, name, memberType, offset, canSet, doc):
    # the ReadMemberFunctions and WriteMemberFunctions don't have a wrapper to
    # convert arguments to Sulong, so we can avoid boxing the offsets into PInts
    pclass = to_java_type(primary)
    member = property()
    getter = ReadMemberFunctions[memberType]
    def member_getter(self):
        return to_java(getter(to_sulong(self), TrufflePInt_AsPrimitive(offset, 1, 8)))
    member.getter(member_getter)
    if canSet:
        setter = WriteMemberFunctions[memberType]
        def member_setter(self, value):
            setter(to_sulong(self), TrufflePInt_AsPrimitive(offset, 1, 8), to_sulong(value))
        member.setter(member_setter)
    member.__doc__ = doc
    type_dict = to_java(tpDict)
    type_dict[name] = member


getset_descriptor = type(type(AddMember).__code__)
def AddGetSet(primary, name, getter, getter_wrapper, setter, setter_wrapper, doc, closure):
    pclass = to_java_type(primary)
    fset = fget = None
    if getter:
        getter_w = CreateFunction(name, getter, getter_wrapper, pclass)
        def member_getter(self):
            # NOTE: The 'to_java' is intended and correct because this call will do a downcall an 
            # all args will go through 'to_sulong' then. So, if we don't convert the pointer 
            # 'closure' to a Python value, we will get the wrong wrapper from 'to_sulong'.
            return capi_to_java(getter_w(self, to_java(closure)))

        fget = member_getter
    if setter:
        setter_w = CreateFunction(name, setter, setter_wrapper, pclass)
        def member_setter(self, value):
            result = setter_w(self, value, closure)
            if result != 0:
                raise
            return None

        fset = member_setter
    else:
        fset = lambda self, value: GetSet_SetNotWritable(self, value, name)

    getset = PyTruffle_GetSetDescriptor(fget=fget, fset=fset, name=name, owner=pclass)
    PyTruffle_SetAttr(getset, "__doc__", doc)
    PyTruffle_SetAttr(pclass, name, getset)


def GetSet_SetNotWritable(self, value, attr):
    raise AttributeError("attribute '%s' of '%s' objects is not writable" % (attr, type(self).__name__))


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


def PyObject_Size(obj):
    try:
        return int(len(obj))
    except Exception:
        return -1


@may_raise
def PyObject_Call(callee, args, kwargs):
    return callee(*args, **kwargs)


@may_raise
def PyObject_CallMethod(rcvr, method, args):
    # TODO(fa) that seems to be a workaround
    if type(args) is tuple:
        return getattr(rcvr, method)(*args)
    elif args is not None:
        return getattr(rcvr, method)(args)
    return getattr(rcvr, method)()


@may_raise
def PyObject_GetItem(obj, key):
    return obj[key]


@may_raise(-1)
def PyObject_DelItem(obj, key):
    del obj[key]
    return 0


@may_raise(1)
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


def PyObject_HasAttr(obj, attr):
    return 1 if hasattr(obj, attr) else 0


def PyObject_HashNotImplemented(obj):
    return TypeError("unhashable type: '%s'" % type(obj).__name__)


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
def PyErr_CreateAndSetException(exception_type, msg):
    if not _is_exception_class(exception_type):
        raise SystemError("exception %r not a BaseException subclass" % exception_type)
    if msg is None:
        raise exception_type()
    else:
        raise exception_type(msg)


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


def PyErr_NewException(name, base, dictionary):
    if "__module__" not in dictionary:
        dictionary["__module__"] = name.rpartition(".")[2]
    if not isinstance(base, tuple):
        bases = (base,)
    else:
        bases = base
    return type(name, bases, dictionary)


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
    return codetype(*args)


## TRACEBACK

tbtype = type(sys._getframe(0).f_trace)

@may_raise(-1)
def PyTraceBack_Here(frame):
    exc, val, tb = sys.exc_info()
    if val:
        # CPython does a PyErr_Fetch and then PyErr_Restore with the newly
        # created traceback. So if val is None, the restore would just do
        # nothing. But if it is available, we basically just set the current
        # __traceback__ to a traceback object wrapped around the exception here.
        exc.__traceback__ = PyTruffleTraceBack_Here(frame, tb);
    return 0


##################### C EXT HELPERS

def PyTruffle_Debug(*args):
    __tdebug__(*args)


def PyTruffle_GetBuiltin(name):
    return getattr(sys.modules["builtins"], name)


def check_argtype(idx, obj, typ):
    if not isinstance(obj, typ):
        raise TypeError("argument %d must be '%s', not '%s'" % (idx, str(typ), str(type(obj))))


def import_c_func(name):
    return CreateFunction(name, capi[name])


def initialize_capi(capi_library):
    """This method is called from a C API constructor function"""
    global capi
    global capi_to_java
    capi = capi_library
    capi_to_java = import_c_func("to_java")

    initialize_member_accessors()
    initialize_datetime_capi()
    

# run C API initialize hooks
def run_capi_loaded_hooks(capi_library):
    local_hooks = _capi_hooks.copy()
    _capi_hooks.clear()
    for hook in local_hooks:
        hook()


def import_native_memoryview(capi_library):
    import _memoryview
    assert _memoryview is not None


def initialize_datetime_capi():
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

    import_c_func("set_PyDateTime_typeids")(PyDateTime_CAPI, PyDateTime_CAPI.DateType, PyDateTime_CAPI.DateTimeType, PyDateTime_CAPI.TimeType, PyDateTime_CAPI.DeltaType, PyDateTime_CAPI.TZInfoType)
    datetime.datetime_CAPI = PyCapsule("datetime.datetime_CAPI", PyDateTime_CAPI(), None)
    datetime.date.__basicsize__ = import_c_func("get_PyDateTime_Date_basicsize")()
    datetime.time.__basicsize__ = import_c_func("get_PyDateTime_Time_basicsize")()
    datetime.datetime.__basicsize__ = import_c_func("get_PyDateTime_DateTime_basicsize")()
    datetime.timedelta.__basicsize__ = import_c_func("get_PyDateTime_Delta_basicsize")()


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
        ReadMemberFunctions.append(capi[memberFunc])
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
        WriteMemberFunctions.append(capi[memberFunc])
    WriteMemberFunctions.append(lambda x,v: None)


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
def PyMapping_Keys(obj):
    return list(obj.keys())


@may_raise
def PyMapping_Values(obj):
    return list(obj.values())


@may_raise
def PyState_FindModule(module_name):
    return sys.modules[module_name]


@may_raise
def PyEval_GetBuiltins():
    return __builtins__.__dir__
