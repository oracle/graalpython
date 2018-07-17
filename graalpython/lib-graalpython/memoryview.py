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

# memoryview is mainly implemented in C

def make_init():
    def __memoryview_init(self, *args, **kwargs):
        import _memoryview
        self.__c_memoryview = _memoryview.nativememoryview(*args, **kwargs)

    return __memoryview_init


def make_getitem():
    def __memoryview_getitem(self, key):
        res = self.__c_memoryview.__getitem__(key)
        return memoryview(res) if isinstance(res, type(self.__c_memoryview)) else res
    return __memoryview_getitem


def make_property(name):
    template = """def {0}_getter(self):
    return self.__c_memoryview.{0}

{0} = property({0}_getter)

def {0}_setter(self, value):
    raise AttributeError("attribute '{0}' of 'memoryview' objects is not writable")

{0} = {0}.setter({0}_setter)"""
    # use dict for globals and locals to avoid name conflicts
    _globals = dict(globals())
    _locals = dict()
    exec(template.format(name), _globals, _locals)
    return eval(name, _globals, _locals)


# delegate methods
memoryview.__init__ = make_init()
memoryview.__repr__ = lambda self: self.__c_memoryview.__repr__()
memoryview.__len__ = lambda self: self.__c_memoryview.__len__()
memoryview.__getitem__ = make_getitem()
memoryview.__setitem__ = lambda self, key, value: self.__c_memoryview.__setitem__(key, value)
memoryview.release = lambda self: self.__c_memoryview.release()
memoryview.tobytes = lambda self: self.__c_memoryview.tobytes()
memoryview.hex = lambda self: self.__c_memoryview.hex()
memoryview.tolist = lambda self: self.__c_memoryview.tolist()
memoryview.cast = lambda self, *args: self.__c_memoryview.cast(*args)
memoryview.__enter__ = lambda self: self.__c_memoryview.__enter__()
memoryview.__exit__ = lambda self: self.__c_memoryview.__exit__()

# delegate properties
memoryview.nbytes = make_property("nbytes")
memoryview.readonly = make_property("readonly")
memoryview.itemsize = make_property("itemsize")
memoryview.format = make_property("format")
memoryview.ndim = make_property("ndim")
memoryview.shape = make_property("shape")
memoryview.strides = make_property("strides")
memoryview.suboffsets = make_property("suboffsets")
memoryview.c_contiguous = make_property("c_contiguous")
memoryview.f_contiguous = make_property("f_contiguous")
memoryview.contiguous = make_property("contiguous")


del make_init
del make_getitem
# del make_nbytes
# del make_readonly
# del make_itemsize
# del make_format
# del make_ndim
# del make_shape
# del make_strides
# del make_suboffsets
# del make_c_contiguous
# del make_f_contiguous
# del make_contiguous
