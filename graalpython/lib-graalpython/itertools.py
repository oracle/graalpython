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

class accumulate(object):
    """
    "accumulate(iterable) --> accumulate object

    Return series of accumulated sums."""

    _marker = object()

    @__graalpython__.builtin_method
    def __init__(self, iterable, func=None, *, initial=None):
        self.iterable = iter(iterable)
        self.func = func
        self.total = accumulate._marker
        self.initial = initial

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        if self.initial is not None:
            self.total = self.initial
            self.initial = None
            return self.total
        value = next(self.iterable)
        if self.total is accumulate._marker:
            self.total = value
            return value

        if self.func is None:
            self.total = self.total + value
        else:
            self.total = self.func(self.total, value)
        return self.total

    @__graalpython__.builtin_method
    def __reduce__(self):
        if self.initial is not None:
            it = chain((self.initial,), self.iterable)
            return type(self), (it, self.func), None
        elif self.total is None:
            it = accumulate(chain((self.total,), self.iterable), self.func)
            return islice, (it, 1, None)
        else:
            return type(self), (self.iterable, self.func), self.total

    @__graalpython__.builtin_method
    def __setstate__(self, state):
        self.total = state


class dropwhile(object):
    """
    dropwhile(predicate, iterable) --> dropwhile object

    Drop items from the iterable while predicate(item) is true.
    Afterwards, return every element until the iterable is exhausted.
    """

    @__graalpython__.builtin_method
    def __init__(self, predicate, iterable):
        self.predicate = predicate
        self.iterable = iter(iterable)
        self.done_dropping = False

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        while not self.done_dropping:
            n = next(self.iterable)
            if self.predicate(n):
                continue
            else:
                self.done_dropping = True
                return n
        return next(self.iterable)


class filterfalse(object):
    """
    filterfalse(function or None, sequence) --> filterfalse object

    Return those items of sequence for which function(item) is false.
    If function is None, return the items that are false.
    """

    @__graalpython__.builtin_method
    def __init__(self, func, sequence):
        self.func = func
        self.iterator = iter(sequence)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        while True:
            n = next(self.iterator)
            if self.func is None:
                if not n:
                    return n
            elif not self.func(n):
                return n

    @__graalpython__.builtin_method
    def __reduce__(self):
        return type(self), (self.func, self.iterator)


class takewhile(object):
    """Make an iterator that returns elements from the iterable as
    long as the predicate is true.

    Equivalent to :

    def takewhile(predicate, iterable):
        for x in iterable:
            if predicate(x):
                yield x
            else:
                break
    """
    @__graalpython__.builtin_method
    def __init__(self, predicate, iterable):
        self._predicate = predicate
        self._iter = iter(iterable)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        value = next(self._iter)
        if not self._predicate(value):
            self._iter = iter([])
            raise StopIteration()
        return value


class groupby(object):
    """Make an iterator that returns consecutive keys and groups from the
    iterable. The key is a function computing a key value for each
    element. If not specified or is None, key defaults to an identity
    function and returns the element unchanged. Generally, the
    iterable needs to already be sorted on the same key function.

    The returned group is itself an iterator that shares the
    underlying iterable with groupby(). Because the source is shared,
    when the groupby object is advanced, the previous group is no
    longer visible. So, if that data is needed later, it should be
    stored as a list:

       groups = []
       uniquekeys = []
       for k, g in groupby(data, keyfunc):
           groups.append(list(g))      # Store group iterator as a list
           uniquekeys.append(k)
    """
    @__graalpython__.builtin_method
    def __init__(self, iterable, key=None):
        self._marker = object()
        self._tgtkey = self._currkey = self._currvalue = self._marker
        self._currgrouper = None
        self._keyfunc = key
        self._it = iter(iterable)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        self._currgrouper = None
        marker = self._marker
        while True:
            if self._currkey is marker:
                pass
            elif self._tgtkey is marker:
                break
            else:
                if not self._tgtkey == self._currkey:
                    break
            self._groupby_step()

        self._tgtkey = self._currkey
        grouper = _grouper(self, self._tgtkey)
        return (self._currkey, grouper)

    @__graalpython__.builtin_method
    def _groupby_step(self):
        newvalue = next(self._it)
        if self._keyfunc is None:
            newkey = newvalue
        else:
            newkey = self._keyfunc(newvalue)
        self._currvalue = newvalue
        self._currkey = newkey


class _grouper():
    @__graalpython__.builtin_method
    def __init__(self, parent, tgtkey):
        if not isinstance(parent, groupby):
            raise TypeError("incorrect usage of internal _grouper")
        parent._currgouper = self
        self._parent = parent
        self._tgtkey = tgtkey
        self._marker = parent._marker

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        gbo = self._parent
        if gbo._currgouper != self:
            raise StopIteration
        if gbo._currvalue is self._marker:
            gbo._groupby_step()
        if not (self._tgtkey == gbo._currkey):
            raise StopIteration
        r = gbo._currvalue
        gbo._currvalue = self._marker
        return r


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


class compress():
    """Make an iterator that filters elements from *data* returning
   only those that have a corresponding element in *selectors* that evaluates to
   ``True``.  Stops when either the *data* or *selectors* iterables has been
   exhausted.
   Equivalent to::

       def compress(data, selectors):
           # compress('ABCDEF', [1,0,1,0,1,1]) --> A C E F
           return (d for d, s in zip(data, selectors) if s)
    """
    @__graalpython__.builtin_method
    def __init__(self, data, selectors):
        self.data = iter(data)
        self.selectors = iter(selectors)

    @__graalpython__.builtin_method
    def __iter__(self):
        return self

    @__graalpython__.builtin_method
    def __next__(self):
        # No need to check for StopIteration since either data or selectors will
        # raise this. The shortest one stops first.
        while True:
            next_item = next(self.data)
            next_selector = next(self.selectors)
            if next_selector:
                return next_item

    @__graalpython__.builtin_method
    def __reduce__(self):
        return (type(self), (self.data, self.selectors))


