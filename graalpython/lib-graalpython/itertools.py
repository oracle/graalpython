# coding=utf-8
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import sys

class cycle():
    """
    Make an iterator returning elements from the iterable and
    saving a copy of each. When the iterable is exhausted, return
    elements from the saved copy. Repeats indefinitely.

    Equivalent to :

    def cycle(iterable):
        saved = []
        for element in iterable:
            yield element
            saved.append(element)
        while saved:
            for element in saved:
                yield element
    """

    @__graalpython__.builtin_method
    def __init__(self, iterable):
        self.saved = []
        self.iterable = iter(iterable)
        self.index = 0
        self.firstpass = False

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.iterable:
            try:
                obj = next(self.iterable)
            except StopIteration:
                self.iterable = None
            else:
                if not self.firstpass:
                    self.saved.append(obj)
                return obj
        if not self.saved:
            raise StopIteration
        obj = self.saved[self.index]
        self.index += 1
        if self.index >= len(self.saved):
            self.index = 0
        return obj

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.iterable is None:
            it = iter(self.saved)
            if self.index:
                it.__setstate__(self.index)
            return type(self), (it,), (self.saved, True)
        return type(self), (self.iterable,), (self.saved, self.firstpass)

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        if (not isinstance(state, tuple) or
            len(state) != 2 or
            not isinstance(state[0], list) or
            not isinstance(state[1], int)):
            raise TypeError("invalid state tuple")
        self.saved = state[0]
        self.firstpass = state[1]
        self.index = 0


