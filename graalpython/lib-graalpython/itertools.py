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

class combinations():
    """
    combinations(iterable, r) --> combinations object

    Return successive r-length combinations of elements in the iterable.

    combinations(range(4), 3) --> (0,1,2), (0,1,3), (0,2,3), (1,2,3)
    """

    @__graalpython__.builtin_method
    def __init__(self, iterable, r):
        self.pool = tuple(iterable)
        n = len(self.pool)
        if r < 0:
            raise ValueError("r must be non-negative")
        self.indices = [i for i in range(r)]
        self.r = r
        self.last_result = None
        self.stopped = r > len(self.pool)

    @__graalpython__.builtin_method
    def get_maximum(self, i):
        return i + len(self.pool) - self.r

    @__graalpython__.builtin_method
    def max_index(self, j):
        return self.indices[j - 1] + 1

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.stopped:
            raise StopIteration
        if self.last_result is None:
            # On the first pass, initialize result tuple using the indices
            result = [None] * self.r
            for i in range(self.r):
                index = self.indices[i]
                result[i] = self.pool[index]
        else:
            # Copy the previous result
            result = self.last_result[:]
            # Scan indices right-to-left until finding one that is not at its
            # maximum
            i = self.r - 1
            while i >= 0 and self.indices[i] == self.get_maximum(i):
                i -= 1

            # If i is negative, then the indices are all at their maximum value
            # and we're done
            if i < 0:
                self.stopped = True
                raise StopIteration

            # Increment the current index which we know is not at its maximum.
            # Then move back to the right setting each index to its lowest
            # possible value
            self.indices[i] += 1
            for j in range(i + 1, self.r):
                self.indices[j] = self.max_index(j)

            # Update the result for the new indices starting with i, the
            # leftmost index that changed
            for i in range(i, self.r):
                index = self.indices[i]
                elem = self.pool[index]
                result[i] = elem
        self.last_result = result
        return tuple(result)


class combinations_with_replacement(combinations):
    """
    combinations_with_replacement(iterable, r) --> combinations_with_replacement object

    Return successive r-length combinations of elements in the iterable
    allowing individual elements to have successive repeats.
    combinations_with_replacement('ABC', 2) --> AA AB AC BB BC CC
    """
    @__graalpython__.builtin_method
    def __init__(self, iterable, r):
        pool = list(iterable)
        if r < 0:
            raise ValueError("r must be non-negative")
        super().__init__(pool, r)
        self.indices = [0] * r
        self.stopped = len(pool) == 0 and r > 0

    @__graalpython__.builtin_method
    def get_maximum(self, i):
        return len(self.pool) - 1

    @__graalpython__.builtin_method
    def max_index(self, j):
        return self.indices[j - 1]


class zip_longest():
    """
    zip_longest(iter1 [,iter2 [...]], [fillvalue=None]) --> zip_longest object

    Return a zip_longest object whose .next() method returns a tuple where
    the i-th element comes from the i-th iterable argument.  The .next()
    method continues until the longest iterable in the argument sequence
    is exhausted and then it raises StopIteration.  When the shorter iterables
    are exhausted, the fillvalue is substituted in their place.  The fillvalue
    defaults to None or can be specified by a keyword argument.
    """

    @__graalpython__.builtin_method
    def __new__(subtype, *args, fillvalue=None):
        self = object.__new__(subtype)
        self.fillvalue = fillvalue
        self.tuplesize = len(args)
        self.numactive = len(args)
        self.ittuple = [iter(arg) for arg in args]
        return self

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if not self.tuplesize:
            raise StopIteration
        if not self.numactive:
            raise StopIteration
        result = [None] * self.tuplesize
        for idx, it in enumerate(self.ittuple):
            if it is None:
                item = self.fillvalue
            else:
                try:
                    item = next(it)
                except StopIteration:
                    self.numactive -= 1
                    if self.numactive == 0:
                        raise StopIteration
                    else:
                        item = self.fillvalue
                        self.ittuple[idx] = None
                except:
                    self.numactive = 0
                    raise
            result[idx] = item
        return tuple(result)

    @__graalpython__.builtin_method
    def __reduce__(self):
        args = []
        for elem in self.ittuple:
            args.append(elem if elem is not None else tuple())
        return type(self), tuple(args), self.fillvalue

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        self.fillvalue = state


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


