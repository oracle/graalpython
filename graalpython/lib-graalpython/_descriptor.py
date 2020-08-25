# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

def _f(): pass
FunctionType = type(_f)
descriptor = type(FunctionType.__code__)


def make_named_tuple_class(name, fields):
    class named_tuple(tuple):
        __name__ = name
        n_sequence_fields = len(fields)
        fields = fields

        def __str__(self):
            return self.__repr__()

        def __repr__(self):
            sb = [name, "("]
            for f in fields:
                sb.append(f)
                sb.append("=")
                sb.append(repr(getattr(self, f)))
                sb.append(", ")
            sb.pop()
            sb.append(")")
            return "".join(sb)

    def _define_named_tuple_methods():
        for i, name in enumerate(fields):
            def make_func(i):
                def func(self):
                    return self[i]
                return func
            setattr(named_tuple, name, descriptor(fget=make_func(i), name=name, owner=named_tuple))


    _define_named_tuple_methods()
    return named_tuple


def recursive_repr(fillfn):
    def inner(fn):
        data = None

        def wrapper(self):
            nonlocal data
            if data is None:
                # lazy initialization to avoid bootstrap issues
                import threading
                data = threading.local()
                data.running = set()
            key = id(self)
            if key in data.running:
                return fillfn(self)
            data.running.add(key)
            try:
                result = fn(self)
            finally:
                data.running.discard(key)
            return result

        wrapper.__name__ = fn.__name__
        wrapper.__qualname__ = fn.__qualname__
        return wrapper
    return inner


class SimpleNamespace(object):
    def __init__(self, **kwargs):
        for k, v in kwargs.items():
            setattr(self, k, v)

    @recursive_repr(lambda self: "%s(...)" % 'namespace' if type(self) is SimpleNamespace else type(self).__name__)
    def __repr__(self):
        sb = []
        for k, v in sorted(self.__dict__.items()):
            sb.append("%s=%r" % (k, v))
        name = 'namespace' if type(self) is SimpleNamespace else type(self).__name__
        return "%s(%s)" % (name, ", ".join(sb))

    def __reduce__(self):
        return type(self), (), self.__dict__

    def __eq__(self, other):
        if __graalpython__.type_check(self, SimpleNamespace) and __graalpython__.type_check(other, SimpleNamespace):
            return self.__dict__ == other.__dict__
        return NotImplemented
