# coding=utf-8
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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

class defaultdict(dict):
    @__graalpython__.builtin_method
    def __init__(self, default_factory=None, *args, **kwds):
        dict.__init__(self, *args, **kwds)
        if (default_factory is None or callable(default_factory)):
            self.default_factory = default_factory
        else:
            raise TypeError("first argument must be callable or None")

    @__graalpython__.builtin_method
    def __missing__(self, key):
        if self.default_factory is None:
            raise KeyError(key)
        self[key] = value = self.default_factory()
        return value

    @__graalpython__.builtin_method
    def __repr__(self):
        return "%s(%r, %s)" % (type(self).__name__, self.default_factory, dict.__repr__(self))

    @__graalpython__.builtin_method
    def copy(self):
        cp = defaultdict(default_factory=self.default_factory)
        for k,v in self.items():
            cp[k] = v
        return cp

    @__graalpython__.builtin_method
    def __reduce__(self):
        args = tuple() if self.default_factory is None else (self.default_factory,)
        return type(self), args, None, None, iter(self.items())


defaultdict.__module__ = 'collections'


class _tuplegetter(object):
    @__graalpython__.builtin_method
    def __init__(self, index, doc):
        self.index = index
        self.__doc__ = doc

    @__graalpython__.builtin_method
    def __set__(self, instance, value):
        raise AttributeError("can't set attribute")

    @__graalpython__.builtin_method
    def __delete__(self, instance):
        raise AttributeError("can't delete attribute")

    @__graalpython__.builtin_method
    def __get__(self, instance, owner=None):
        index = self.index
        if not isinstance(instance, tuple):
            if instance is None:
                return self
            raise TypeError("descriptor for index '%d' for tuple subclasses "
                            "doesn't apply to '%s' object" % (index, instance))
        if index >= len(instance):
            raise IndexError("tuple index out of range")

        return instance[index]

    @__graalpython__.builtin_method
    def __reduce__(self):
        return type(self), (self.index, self.__doc__)
