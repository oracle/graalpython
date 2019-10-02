# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

# memoryview is mainly implemented in C
from python_cext import PyTruffle_SetAttr


c_memoryview_module = None


def __memoryview_init(self, *args, **kwargs):
    import _memoryview
    global c_memoryview_module
    c_memoryview_module = _memoryview
    PyTruffle_SetAttr(memoryview, "__init__", __memoryview_init2)
    return __memoryview_init2(self, *args, **kwargs)


def __memoryview_init2(self, *args, **kwargs):
    # NOTE: DO NOT CHANGE THE NAME OF PROPERTY '__c_memoryview'
    # it is also referenced in native code and Java code
    if args and isinstance(args[0], c_memoryview_module.nativememoryview):
        # wrapping case
        PyTruffle_SetAttr(self, "__c_memoryview", args[0])
    else:
        PyTruffle_SetAttr(self, "__c_memoryview", c_memoryview_module.nativememoryview(*args, **kwargs))


def __memoryview_getitem(self, key):
    res = self.__c_memoryview.__getitem__(key)
    return memoryview(res) if isinstance(res, type(self.__c_memoryview)) else res


getsetdescriptor = type(type(__memoryview_init).__code__)


def make_property(name):
    def getter(self):
        return getattr(self.__c_memoryview, name)

    error_string = "attribute '%s' of 'memoryview' objects is not writable" % name
    def setter(self, value):
        raise AttributeError(error_string)

    return getsetdescriptor(fget=getter, fset=setter, name=name, owner=memoryview)


for p in ["nbytes", "readonly", "itemsize", "format", "ndim", "shape", "strides",
          "suboffsets", "c_contiguous", "f_contiguous", "contiguous"]:
    setattr(memoryview, p, make_property(p))


def make_delegate0(p):
    def delegate(self):
        return getattr(self.__c_memoryview, p)()
    delegate.__name__ = p
    return delegate

for p in ["__repr__", "__len__", "release", "tobytes", "hex", "tolist", "__enter__"]:
    setattr(memoryview, p, make_delegate0(p))


# other delegate methods
memoryview.__init__ = __memoryview_init
memoryview.__getitem__ = __memoryview_getitem
memoryview.__setitem__ = lambda self, key, value: self.__c_memoryview.__setitem__(key, value)
memoryview.cast = lambda self, *args: memoryview(self.__c_memoryview.cast(*args))
memoryview.__exit__ = lambda self, exc_type, exc_value, traceback: self.__c_memoryview.__exit__(exc_type, exc_value, traceback)
