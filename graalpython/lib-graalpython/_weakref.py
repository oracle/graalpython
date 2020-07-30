# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

class CallableProxyType(object):
    pass


# weak refs are used to allow circular data structures to be collected.
# Java's GC has no problem with these, so we can make this a simple proxy.
class ProxyType(object):
    def __init__(self, other):
        object.__setattr__(self, "value", other)

    def __getattr__(self, key):
        return getattr(object.__getattribute__(self, "value"), key)

    def __setattr__(self, key, value):
        setattr(object.__getattribute__(self, "value"), key, value)

    def __delattr__(self, key):
        return delattr(object.__getattribute__(self, "value"), key)

    def __getitem__(self, key):
        return object.__getattribute__(self, "value")[key]

    def __setitem__(self, key, value):
        object.__getattribute__(self, "value")[key] = value

    def __delitem__(self, key):
        del object.__getattribute__(self, "value")[key]

    def __repr__(self):
        return f"<weakproxy at {id(self)} to {type(self).__name__} at {id(object.__getattribute__(self, 'value'))}>"

    def __str__(self):
        return str(object.__getattribute__(self, "value"))

    def __bytes__(self):
        return object.__getattribute__(self, "value").__bytes__()

    def __lt__(self, other):
        return object.__getattribute__(self, "value") < other

    def __le__(self, other):
        return object.__getattribute__(self, "value") <= other

    def __eq__(self, other):
        return object.__getattribute__(self, "value") == other

    def __ne__(self, other):
        return object.__getattribute__(self, "value") != other

    def __gt__(self, other):
        return object.__getattribute__(self, "value") > other

    def __ge__(self, other):
        return object.__getattribute__(self, "value") >= other

    def __hash__(self):
        return hash(object.__getattribute__(self, "value"))

    def __bool__(self):
        return bool(object.__getattribute__(self, "value"))

    def __call__(self, *args):
        return object.__getattribute__(self, "value")(*args)

    def __len__(self):
        return len(object.__getattribute__(self, "value"))

    def __iter__(self):
        return iter(object.__getattribute__(self, "value"))

    def __next__(self):
        return next(object.__getattribute__(self, "value"))

    def __reversed__(self):
        return object.__getattribute__(self, "value").__reversed__()

    def __contains__(self, obj):
        return obj in object.__getattribute__(self, "value")

    def __add__(self, other):
        return object.__getattribute__(self, "value") + other

    def __sub__(self, other):
        return object.__getattribute__(self, "value") - other

    def __mul__(self, other):
        return object.__getattribute__(self, "value") * other

    def __matmul__(self, other):
        return object.__getattribute__(self, "value") @ other

    def __truediv__(self, other):
        return object.__getattribute__(self, "value") / other

    def __floordiv__(self, other):
        return object.__getattribute__(self, "value") // other

    def __mod__(self, other):
        return object.__getattribute__(self, "value") % other

    def __divmod__(self, other):
        return divmod(object.__getattribute__(self, "value"), other)

    def __pow__(self, exp, mod=None):
        return pow(object.__getattribute__(self, "value"), exp, mod)

    def __lshift__(self, other):
        return object.__getattribute__(self, "value") << other

    def __rshift__(self, other):
        return object.__getattribute__(self, "value") >> other

    def __and__(self, other):
        return object.__getattribute__(self, "value") & other

    def __xor__(self, other):
        return object.__getattribute__(self, "value") ^ other

    def __or__(self, other):
        return object.__getattribute__(self, "value") | other

    def __iadd__(self, other):
        value = object.__getattribute__(self, "value")
        value += other
        return value

    def __isub__(self, other):
        value = object.__getattribute__(self, "value")
        value -= other
        return value

    def __imul__(self, other):
        value = object.__getattribute__(self, "value")
        value *= other
        return value

    def __imatmul__(self, other):
        value = object.__getattribute__(self, "value")
        value @= other
        return value

    def __itruediv__(self, other):
        value = object.__getattribute__(self, "value")
        value /= other
        return value

    def __ifloordiv__(self, other):
        value = object.__getattribute__(self, "value")
        value //= other
        return value

    def __imod__(self, other):
        value = object.__getattribute__(self, "value")
        value %= other
        return value

    def __ipow__(self, exp):
        value = object.__getattribute__(self, "value")
        value **= exp
        return value

    def __ilshift__(self, other):
        value = object.__getattribute__(self, "value")
        value <<= other
        return value

    def __irshift__(self, other):
        value = object.__getattribute__(self, "value")
        value >>= other
        return value

    def __iand__(self, other):
        value = object.__getattribute__(self, "value")
        value &= other
        return value

    def __ixor__(self, other):
        value = object.__getattribute__(self, "value")
        value ^= other
        return value

    def __ior__(self, other):
        value = object.__getattribute__(self, "value")
        value |= other
        return value

    def __neg__(self):
        return -object.__getattribute__(self, "value")

    def __pos__(self):
        return +object.__getattribute__(self, "value")

    def __abs__(self):
        return abs(object.__getattribute__(self, "value"))

    def __invert__(self):
        return ~object.__getattribute__(self, "value")

    def __int__(self):
        return int(object.__getattribute__(self, "value"))

    def __float__(self):
        return float(object.__getattribute__(self, "value"))

    def __round__(self):
        return round(object.__getattribute__(self, "value"))

    def __index__(self):
        v = object.__getattribute__(self, "value")
        if not hasattr(v, "__index__"):
            raise TypeError("'%s' object cannot be interpreted as an integer" % type(v))
        result = v.__index__()
        result_type = type(result)
        if not isinstance(result, int):
            raise TypeError("__index__ returned non-int (type %s)" % result_type)
        return result


ref = ReferenceType
proxy = ProxyType

# getweakrefcount,  -> truffle
# getweakrefs,  -> truffle
# ref,  -> link in py
# proxy,    -> link in py
# CallableProxyType,
# ProxyType,
# ReferenceType, -> truffle
# _remove_dead_weakref  -> truffle
