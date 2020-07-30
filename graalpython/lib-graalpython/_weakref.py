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

from _descriptor import descriptor

class CallableProxyType(object):
    pass

# weak refs are used to allow circular data structures to be collected.
# Java's GC has no problem with these, so we can make this a simple proxy.
class ProxyType(object):
    def __init__(self, other):
        object.__setattr__(self, "value", other)

    def __getattribute__(self, key):
        return object.__getattribute__(self, "value").__getattribute__(key)

    def __setattr__(self, key, value):
        return object.__getattribute__(self, "value").__setattr__(key, value)

    def __delattr__(self, key):
        return object.__getattribute__(self, "value").__delattr__(key)

    def __getitem__(self, key):
        return object.__getattribute__(self, "value").__getitem__(key)

    def __setitem__(self, key, value):
        return object.__getattribute__(self, "value").__setitem__(key, value)

    def __delitem__(self, other):
        return object.__getattribute__(self, "value").__delitem__(other)

    def __ceil__(self):
        return object.__getattribute__(self, "value").__ceil__()

    def __floor__(self):
        return object.__getattribute__(self, "value").__floor__()

    def __repr__(self):
        return f"<weakproxy at {id(self)} to {type(self).__name__} at {id(object.__getattribute__(self, 'value'))}>"

    def __str__(self):
        return object.__getattribute__(self, "value").__str__()

    def __bytes__(self):
        return object.__getattribute__(self, "value").__bytes__()

    def __format__(self, format_spec):
        return object.__getattribute__(self, "value").__format__(self, format_spec)

    def __lt__(self, other):
        return object.__getattribute__(self, "value").__lt__(other)

    def __le__(self, other):
        return object.__getattribute__(self, "value").__le__(other)

    def __eq__(self, other):
        return object.__getattribute__(self, "value").__eq__(other)

    def __ne__(self, other):
        return object.__getattribute__(self, "value").__ne__(other)

    def __gt__(self, other):
        return object.__getattribute__(self, "value").__gt__(other)

    def __ge__(self, other):
        return object.__getattribute__(self, "value").__ge__(other)

    def __hash__(self):
        return object.__getattribute__(self, "value").__hash__()

    def __bool__(self):
        return object.__getattribute__(self, "value").__bool__()

    def __call__(self, *args):
        return object.__getattribute__(self, "value").__call__(*args)

    def __len__(self):
        return object.__getattribute__(self, "value").__len__()

    def __iter__(self):
        return object.__getattribute__(self, "value").__iter__()

    def __next__(self):
        return object.__getattribute__(self, "value").__next__()

    def __reversed__(self):
        return object.__getattribute__(self, "value").__reversed__()

    def __contains__(self, obj):
        return object.__getattribute__(self, "value").__contains__(obj)

    def __add__(self, other):
        return object.__getattribute__(self, "value").__add__(other)

    def __sub__(self, other):
        return object.__getattribute__(self, "value").__sub__(other)

    def __mul__(self, other):
        return object.__getattribute__(self, "value").__mul__(other)

    def __matmul__(self, other):
        return object.__getattribute__(self, "value").__matmul__(other)

    def __truediv__(self, other):
        return object.__getattribute__(self, "value").__truediv__(other)

    def __trunc__(self):
        return object.__getattribute__(self, "value").__trunc__()

    def __floordiv__(self, other):
        return object.__getattribute__(self, "value").__floordiv__(other)

    def __mod__(self, other):
        return object.__getattribute__(self, "value").__mod__(other)

    def __divmod__(self, other):
        return object.__getattribute__(self, "value").__divmod__(other)

    def __pow__(self, exp, mod=None):
        if mod is None:
            return object.__getattribute__(self, "value").__pow__(exp)
        return object.__getattribute__(self, "value").__pow__(exp, mod)

    def __lshift__(self, other):
        return object.__getattribute__(self, "value").__lshift__(other)

    def __rshift__(self, other):
        return object.__getattribute__(self, "value").__rshift__(other)

    def __and__(self, other):
        return object.__getattribute__(self, "value").__and__(other)

    def __xor__(self, other):
        return object.__getattribute__(self, "value").__xor__(other)

    def __or__(self, other):
        return object.__getattribute__(self, "value").__or__(other)

    def __radd__(self, other):
        return object.__getattribute__(self, "value").__radd__(other)

    def __rsub__(self, other):
        return object.__getattribute__(self, "value").__rsub__(other)

    def __rmul__(self, other):
        return object.__getattribute__(self, "value").__rmul__(other)

    def __rmatmul__(self, other):
        return object.__getattribute__(self, "value").__rmatmul__(other)

    def __rtruediv__(self, other):
        return object.__getattribute__(self, "value").__rtruediv__(other)

    def __rfloordiv__(self, other):
        return object.__getattribute__(self, "value").__rfloordiv__(other)

    def __rmod__(self, other):
        return object.__getattribute__(self, "value").__rmod__(other)

    def __rdivmod__(self, other):
        return object.__getattribute__(self, "value").__rdivmod__(other)

    def __rpow__(self, exp, mod=None):
        if mod is None:
            return object.__getattribute__(self, "value").__rpow__(exp)
        return object.__getattribute__(self, "value").__rpow__(exp, mod)

    def __rlshift__(self, other):
        return object.__getattribute__(self, "value").__rlshift__(other)

    def __rand__(self, other):
        return object.__getattribute__(self, "value").__rand__(other)

    def __rxor__(self, other):
        return object.__getattribute__(self, "value").__rxor__(other)

    def __ror__(self, other):
        return object.__getattribute__(self, "value").__ror__(other)

    def __iadd__(self, other):
        return object.__getattribute__(self, "value").__iadd__(other)

    def __isub__(self, other):
        return object.__getattribute__(self, "value").__isub__(other)

    def __imul__(self, other):
        return object.__getattribute__(self, "value").__imul__(other)

    def __imatmul__(self, other):
        return object.__getattribute__(self, "value").__imatmul__(other)

    def __itruediv__(self, other):
        return object.__getattribute__(self, "value").__itruediv__(other)

    def __ifloordiv__(self, other):
        return object.__getattribute__(self, "value").__ifloordiv__(other)

    def __imod__(self, other):
        return object.__getattribute__(self, "value").__imod__(other)

    def __ipow__(self, exp, mod=None):
        if mod is None:
            return object.__getattribute__(self, "value").__ipow__(exp)
        return object.__getattribute__(self, "value").__ipow__(exp, mod)

    def __ilshift__(self, other):
        return object.__getattribute__(self, "value").__ilshift__(other)

    def __irshift__(self, other):
        return object.__getattribute__(self, "value").__irshift__(other)

    def __iand__(self, other):
        return object.__getattribute__(self, "value").__iand__(other)

    def __ixor__(self, other):
        return object.__getattribute__(self, "value").__ixor__(other)

    def __ior__(self, other):
        return object.__getattribute__(self, "value").__ior__(other)

    def __neg__(self):
        return object.__getattribute__(self, "value").__neg__()

    def __pos__(self):
        return object.__getattribute__(self, "value").__pos__()

    def __abs__(self):
        return object.__getattribute__(self, "value").__abs__()

    def __invert__(self):
        return object.__getattribute__(self, "value").__invert__()

    def __complex__(self):
        return object.__getattribute__(self, "value").__complex__()

    def __int__(self):
        return object.__getattribute__(self, "value").__int__()

    def __float__(self):
        return object.__getattribute__(self, "value").__float__()

    def __round__(self):
        return object.__getattribute__(self, "value").__round__()

    def __index__(self):
        return object.__getattribute__(self, "value").__index__()


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
