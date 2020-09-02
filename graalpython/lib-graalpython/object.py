# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.

def __reduce__(obj, proto=0):
    if proto >= 2:
        descr = getattr(w_obj, '__getnewargs_ex__', None)
        hasargs = True
        if descr is not None:
            result = descr()
            if not isinstance(result, tuple):
                raise TypeError("__getnewargs_ex__ should return a tuple, not '%s'", type(result))
            n = len(result)
            if n != 2:
                raise ValueError("__getnewargs_ex__ should return a tuple of length 2, not %d", n)
            args, kwargs = result
            if isinstance(args, tuple):
                raise TypeError("first item of the tuple returned by __getnewargs_ex__ must be a tuple, not '%s'", type(args))
            if not isinstance(kwargs, dict):
                raise TypeError("second item of the tuple returned by __getnewargs_ex__ must be a dict, not '%s'", type(kwargs))
        else:
            descr = getattr(obj, '__getnewargs__', None)
            if descr is not None:
                args = descr(obj)
                if not isinstance(args, tuple):
                    raise TypeError("__getnewargs__ should return a tuple, not '%s'", type(args))
            else:
                hasargs = False
                args = tuple()
            kwargs = None
        getstate = getattr(obj, '__getstate__')
        if getstate is None:
            required = (not hasargs and
                not isinstance(obj, list) and
                not isinstance(obj, dict))
            obj_type = type(obj)
            if required:
                raise TypeError("cannot pickle %s objects", obj_type)
        return reduce_2(obj, proto, args, kwargs)
    return reduce_1(obj, proto)


def _getstate(obj):
    cls = obj.__class__

    try:
        getstate = obj.__getstate__
    except AttributeError:
        # and raises a TypeError if the condition holds true, this is done
        # just before reduce_2 is called in pypy
        state = getattr(obj, "__dict__", None)
        # CPython returns None if the dict is empty
        if state is not None and len(state) == 0:
            state = None
        names = slotnames(cls) # not checking for list
        if names is not None:
            slots = {}
            for name in names:
                try:
                    value = getattr(obj, name)
                except AttributeError:
                    pass
                else:
                    slots[name] =  value
            if slots:
                state = state, slots
    else:
        state = getstate()
    return state


copyreg = None
def reduce_1(obj, proto):
    global copyreg
    if not copyreg:
        import copyreg
    return copyreg._reduce_ex(obj, proto)


def reduce_2(obj, proto, args, kwargs):
    cls = obj.__class__

    if not hasattr(type(obj), "__new__"):
        raise TypeError("can't pickle %s objects" % type(obj).__name__)

    global copyreg
    if not copyreg:
        import copyreg

    if not isinstance(args, tuple):
        raise TypeError("__getnewargs__ should return a tuple")
    if not kwargs:
       newobj = copyreg.__newobj__
       args2 = (cls,) + args
    elif proto >= 4:
       newobj = copyreg.__newobj_ex__
       args2 = (cls, args, kwargs)
    else:
       raise ValueError("must use protocol 4 or greater to copy this "
                        "object; since __getnewargs_ex__ returned "
                        "keyword arguments.")
    state = _getstate(obj)
    listitems = iter(obj) if isinstance(obj, list) else None
    dictitems = iter(obj.items()) if isinstance(obj, dict) else None

    return newobj, args2, state, listitems, dictitems


def slotnames(cls):
    if not isinstance(cls, type):
        return None

    try:
        return cls.__dict__["__slotnames__"]
    except KeyError:
        pass

    global copyreg
    if not copyreg:
        import copyreg
    slotnames = copyreg._slotnames(cls)
    if not isinstance(slotnames, list) and slotnames is not None:
        raise TypeError("copyreg._slotnames didn't return a list or None")
    return slotnames


def __reduce_ex__(obj, proto=0):
    obj_reduce = getattr(obj, "__reduce__", None)
    if obj_reduce is not None:
        # Check if __reduce__ has been overridden:
        # "type(obj).__reduce__ is not object.__reduce__"
        cls_reduce = getattr(type(obj), "__reduce__", None)
        override = cls_reduce is not obj_reduce
        if override:
            return obj_reduce()
    return __reduce__(obj, proto)


def __sizeof__(obj):
    res = 0
    if hasattr(obj, "__itemsize__"):
        res = 0 if not obj.__itemsize__ else obj.__itemsize__ * len(obj)
    res += obj.__basicsize__
    return res


object.__reduce__ = __reduce__
object.__reduce_ex__ = __reduce_ex__
object.__sizeof__ = __sizeof__
