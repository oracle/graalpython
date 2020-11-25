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

copyreg = None


def import_copyreg():
    global copyreg
    if not copyreg:
        import copyreg


def __reduce__(obj, proto=0):
    if proto >= 2:
        return reduce_newobj(obj)

    proto = int(proto)
    import_copyreg()
    return copyreg._reduce_ex(obj, proto)


def _get_new_arguments(obj):
    # We first attempt to fetch the arguments for __new__ by calling __getnewargs_ex__ on the object.
    getnewargs_ex = getattr(obj, '__getnewargs_ex__', None)
    if getnewargs_ex is not None:
        newargs = getnewargs_ex()
        if not isinstance(newargs, tuple):
            raise TypeError("__getnewargs_ex__ should return a tuple, not '{}'".format(type(newargs)))
        if len(newargs) != 2:
            raise ValueError("__getnewargs_ex__ should return a tuple of length 2, not {}".format(len(newargs)))
        args, kwargs = newargs
        if not isinstance(args, tuple):
            raise TypeError("first item of the tuple returned by __getnewargs_ex__ must be a tuple, not '{}".format(type(args)))
        if not isinstance(kwargs, dict):
            raise TypeError("second item of the tuple returned by __getnewargs_ex__ must be a dict, not '{}'".format(type(kwargs)))
        return args, kwargs

    getnewargs = getattr(obj, '__getnewargs__', None)
    if getnewargs is not None:
        args = getnewargs()
        if not isinstance(args, tuple):
            raise TypeError("__getnewargs__ should return a tuple, not '{}'".format(type(args)))
        return args, None

    # The object does not have __getnewargs_ex__ and __getnewargs__. This may
    # mean __new__ does not takes any arguments on this object, or that the
    # object does not implement the reduce protocol for pickling or
    # copying.
    return None, None


def reduce_newobj(obj):
    cls = obj.__class__
    if not hasattr(cls, '__new__'):
        raise TypeError("cannot pickle '{}' object".format(cls.__name__))

    args, kwargs = _get_new_arguments(obj)
    import_copyreg()

    hasargs = args is not None

    if kwargs is None or len(kwargs) == 0:
        newobj = copyreg.__newobj__
        newargs = (cls, ) + (args if args else tuple())
    elif args is not None:
        newobj = copyreg.__newobj_ex__
        newargs = (cls, args, kwargs)
    else:
        import sys
        frame = sys._getframe(0)
        file_name = frame.f_code.co_filename
        line_no = frame.f_lineno
        raise SystemError("{}:{}: bad argument to internal function".format(file_name, line_no))

    try:
        getstate = obj.__getstate__
    except AttributeError:
        required = not hasargs and not isinstance(obj, (list, dict))
        itemsize = getattr(type(obj), '__itemsize__', 0)
        if required and itemsize != 0:
            raise TypeError("cannot pickle '{}' object".format(cls.__name__))

        state = getattr(obj, "__dict__", None)
        names = slotnames(cls)  # not checking for list
        if names is not None:
            slots = {}
            for name in names:
                try:
                    value = getattr(obj, name)
                except AttributeError:
                    pass
                else:
                    slots[name] = value
            if slots:
                state = state, slots
    else:
        state = getstate()
    listitems = iter(obj) if isinstance(obj, list) else None
    dictitems = iter(obj.items()) if isinstance(obj, dict) else None

    return newobj, newargs, state, listitems, dictitems


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
    obj_reduce = getattr(type, "__reduce__", None)
    _reduce = getattr(obj, "__reduce__", None)
    if _reduce is not None:
        # Check if __reduce__ has been overridden:
        # "type(obj).__reduce__ is not object.__reduce__"
        cls_reduce = getattr(type(obj), "__reduce__", None)
        override = cls_reduce is not obj_reduce
        if override:
            return _reduce()
    return __reduce__(obj, proto)


def __sizeof__(obj):
    cls = type(obj)
    res = 0
    if hasattr(cls, "__itemsize__"):
        if not cls.__itemsize__ or not hasattr(obj, "__len__"):
            res = 0
        else:
            res = cls.__itemsize__ * len(obj)
    res += cls.__basicsize__
    return res


object.__reduce__ = __reduce__
object.__reduce_ex__ = __reduce_ex__
object.__sizeof__ = __sizeof__
