# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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


def _proxy_get(proxy):
    attr = object.__getattribute__(proxy, "_weakref")()
    if attr is None:
        raise ReferenceError("weakly-referenced object no longer exists")
    return attr


class ProxyType(object):
    def __init__(self, other, callback=None):
        import weakref
        object.__setattr__(self, "_weakref", weakref.ref(other, callback))

    def __getattribute__(self, key):
        return getattr(_proxy_get(self), key)

    def __setattr__(self, key, value):
        setattr(_proxy_get(self), key, value)

    def __delattr__(self, key):
        return delattr(_proxy_get(self), key)

    def __getitem__(self, key):
        return _proxy_get(self)[key]

    def __setitem__(self, key, value):
        _proxy_get(self)[key] = value

    def __delitem__(self, key):
        del _proxy_get(self)[key]

    def __repr__(self):
        obj = object.__getattribute__(self, '_weakref')()
        return f"<weakproxy at {id(self)} to {type(obj).__name__} at {id(obj)}>"

    def __str__(self):
        return str(_proxy_get(self))

    def __bytes__(self):
        return _proxy_get(self).__bytes__()

    def __lt__(self, other):
        return _proxy_get(self) < other

    def __le__(self, other):
        return _proxy_get(self) <= other

    def __eq__(self, other):
        return _proxy_get(self) == other

    def __ne__(self, other):
        return _proxy_get(self) != other

    def __gt__(self, other):
        return _proxy_get(self) > other

    def __ge__(self, other):
        return _proxy_get(self) >= other

    def __hash__(self):
        return hash(_proxy_get(self))

    def __bool__(self):
        return bool(_proxy_get(self))

    def __call__(self, *args):
        return _proxy_get(self)(*args)

    def __len__(self):
        return len(_proxy_get(self))

    def __iter__(self):
        return iter(_proxy_get(self))

    def __next__(self):
        return next(_proxy_get(self))

    def __reversed__(self):
        return _proxy_get(self).__reversed__()

    def __contains__(self, obj):
        return obj in _proxy_get(self)

    def __add__(self, other):
        return _proxy_get(self) + other

    def __radd__(self, other):
        return other + _proxy_get(self)

    def __sub__(self, other):
        return _proxy_get(self) - other

    def __mul__(self, other):
        return _proxy_get(self) * other

    def __matmul__(self, other):
        return _proxy_get(self) @ other

    def __truediv__(self, other):
        return _proxy_get(self) / other

    def __floordiv__(self, other):
        return _proxy_get(self) // other

    def __mod__(self, other):
        return _proxy_get(self) % other

    def __divmod__(self, other):
        return divmod(_proxy_get(self), other)

    def __pow__(self, exp, mod=None):
        return pow(_proxy_get(self), exp, mod)

    def __lshift__(self, other):
        return _proxy_get(self) << other

    def __rshift__(self, other):
        return _proxy_get(self) >> other

    def __and__(self, other):
        return _proxy_get(self) & other

    def __xor__(self, other):
        return _proxy_get(self) ^ other

    def __or__(self, other):
        return _proxy_get(self) | other

    def __iadd__(self, other):
        value = _proxy_get(self)
        value += other
        return value

    def __isub__(self, other):
        value = _proxy_get(self)
        value -= other
        return value

    def __imul__(self, other):
        value = _proxy_get(self)
        value *= other
        return value

    def __imatmul__(self, other):
        value = _proxy_get(self)
        value @= other
        return value

    def __itruediv__(self, other):
        value = _proxy_get(self)
        value /= other
        return value

    def __ifloordiv__(self, other):
        value = _proxy_get(self)
        value //= other
        return value

    def __imod__(self, other):
        value = _proxy_get(self)
        value %= other
        return value

    def __ipow__(self, exp):
        value = _proxy_get(self)
        value **= exp
        return value

    def __ilshift__(self, other):
        value = _proxy_get(self)
        value <<= other
        return value

    def __irshift__(self, other):
        value = _proxy_get(self)
        value >>= other
        return value

    def __iand__(self, other):
        value = _proxy_get(self)
        value &= other
        return value

    def __ixor__(self, other):
        value = _proxy_get(self)
        value ^= other
        return value

    def __ior__(self, other):
        value = _proxy_get(self)
        value |= other
        return value

    def __neg__(self):
        return -_proxy_get(self)

    def __pos__(self):
        return +_proxy_get(self)

    def __abs__(self):
        return abs(_proxy_get(self))

    def __invert__(self):
        return ~_proxy_get(self)

    def __int__(self):
        return int(_proxy_get(self))

    def __float__(self):
        return float(_proxy_get(self))

    def __round__(self):
        return round(_proxy_get(self))

    def __index__(self):
        v = _proxy_get(self)
        if not hasattr(v, "__index__"):
            raise TypeError("'%s' object cannot be interpreted as an integer" % type(v))
        result = v.__index__()
        result_type = type(result)
        if not isinstance(result, int):
            raise TypeError("__index__ returned non-int (type %s)" % result_type)
        return result


proxy = ProxyType

# getweakrefcount,  -> truffle
# getweakrefs,  -> truffle
# ref,  -> link in py
# proxy,    -> link in py
# CallableProxyType,
# ProxyType,
# ReferenceType, -> truffle
# _remove_dead_weakref  -> truffle
