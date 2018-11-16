# coding=utf-8
# Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
class repeat():
    def __init__(self, obj, times=None):
        self.obj = obj
        self.times = times
        self.step = 0

    def __iter__(self):
        return self

    def __next__(self):
        if self.times is not None:
            if self.step >= self.times:
                raise StopIteration
            else:
                self.step += 1
        return self.obj


class chain():
    """
    Return a chain object whose .__next__() method returns elements from the
    first iterable until it is exhausted, then elements from the next
    iterable, until all of the iterables are exhausted.
    """
    def __init__(self, *iterables):
        self._iterables = iterables
        self._len = len(iterables)
        if self._len > 0:
            self._current = iter(self._iterables[0])
        self._idx = 0

    def __iter__(self):
        return self

    def __next__(self):
        if self._idx >= self._len:
            raise StopIteration
        try:
            return next(self._current)
        except (StopIteration, IndexError):
            self._idx += 1
            self._current = iter(self._iterables[self._idx])
            return self.__next__()


class starmap():
    pass


class islice(object):
    def __init__(self, iterable, *args):
        self._iterable = enumerate(iter(iterable))
        self._indexes = iter(range(*args))

    def __iter__(self):
        return self

    def __next__(self):
        index = next(self._indexes) # may raise StopIteration
        while True:
            i, element = next(self._iterable) # may raise StopIteration
            if i == index:
                return element


class count(object):
    def __init__(self, start=0, step=1):
        if not isinstance(start, (int, float)) or \
                not isinstance(step, (int, float)):
            raise TypeError('a number is required')
        self._cnt = start
        self._step = step

    def __next__(self):
        _cnt = self._cnt
        self._cnt += self._step
        return _cnt

    def __repr__(self):
        _repr = 'count({}'.format(self._cnt)
        if not isinstance(self._step, int) or self._step != 1:
            _repr += ', {}'.format(self._step)
        return _repr + ')'


class permutations():
    """permutations(iterable[, r]) --> permutations object

    Return successive r-length permutations of elements in the iterable.

    permutations(range(3), 2) --> (0,1), (0,2), (1,0), (1,2), (2,0), (2,1)

    """
    def __init__(self, iterable, r = None):
        self.pool = iterable
        if r is None:
            self.r = len(iterable)
        else:
            self.r = r
        n = len(iterable)
        n_minus_r = n - self.r
        if n_minus_r < 0:
            self.stopped = self.raised_stop_iteration = True
        else:
            self.stopped = self.raised_stop_iteration = False
            self.indices = list(range(n))
            self.cycles = list(range(n, n_minus_r, -1))
            self.started = False

    def __iter__(self):
        return self

    def __next__(self):
        if self.stopped:
            self.raised_stop_iteration = True
            raise StopIteration
        r = self.r
        indices = self.indices
        result = tuple([self.pool[indices[i]] for i in range(r)])
        cycles = self.cycles
        i = r - 1
        while i >= 0:
            j = cycles[i] - 1
            if j > 0:
                cycles[i] = j
                indices[i], indices[-j] = indices[-j], indices[i]
                return result
            cycles[i] = len(indices) - i
            n1 = len(indices) - 1
            assert n1 >= 0
            num = indices[i]
            for k in range(i, n1):
                indices[k] = indices[k+1]
            indices[n1] = num
            i -= 1
        self.stopped = True
        if self.started:
            raise StopIteration
        else:
            self.started = True
        return result

    def __reduce__(self):
        if self.raised_stop_iteration:
            pool = []
        else:
            pool = self.pool
        result = [
            type(self),
            tuple([
                tuple(pool), self.r
            ])
        ]
        if not self.raised_stop_iteration:
            # we must pickle the indices and use them for setstate
            result = result + [
                tuple([
                    tuple(self.indices),
                    tuple(self.cycles),
                    self.started,
                ])]
        return tuple(result)

    def __setstate__(self, state):
        state = list(state)
        if len(state) == 3:
            indices, cycles, started = state
            indices = list(indices)
            cycles = list(cycles)
            self.started = bool(started)
        else:
            raise ValueError("invalid arguments")

        if len(indices) != len(self.pool) or len(cycles) != self.r:
            raise ValueError("invalid arguments")

        n = len(self.pool)
        for i in range(n):
            index = indices[i]
            if index < 0:
                index = 0
            elif index > n-1:
                index = n-1
            self.indices[i] = index

        for i in range(self.r):
            index = cycles[i]
            if index < 1:
                index = 1
            elif index > n-i:
                index = n-i
            self.cycles[i] = index


class product():
    """Cartesian product of input iterables.

       Equivalent to nested for-loops in a generator expression. For example,
        ``product(A, B)`` returns the same as ``((x,y) for x in A for y in B)``.

       The nested loops cycle like an odometer with the rightmost element advancing
        on every iteration.  This pattern creates a lexicographic ordering so that if
        the input's iterables are sorted, the product tuples are emitted in sorted
        order.

       To compute the product of an iterable with itself, specify the number of
        repetitions with the optional *repeat* keyword argument.  For example,
        ``product(A, repeat=4)`` means the same as ``product(A, A, A, A)``.

       This function is equivalent to the following code, except that the
        actual implementation does not build up intermediate results in memory::

           def product(*args, **kwds):
               # product('ABCD', 'xy') --> Ax Ay Bx By Cx Cy Dx Dy
               # product(range(2), repeat=3) --> 000 001 010 011 100 101 110 111
               pools = map(tuple, args) * kwds.get('repeat', 1)
               result = [[]]
               for pool in pools:
                   result = [x+[y] for x in result for y in pool]
               for prod in result:
                   yield tuple(prod)
    """
    def __init__(self, *args, repeat=1):
        self.gears = [list(arg) for arg in args] * repeat
        for gear in self.gears:
            if len(gear) == 0:
                self.indices = None
                self.lst = None
                self.stopped = True
                break
        else:
            self.indices = [0] * len(self.gears)
            self.lst = None
            self.stopped = False

    def _rotate_previous_gears(self):
        lst = self.lst
        x = len(self.gears) - 1
        lst[x] = self.gears[x][0]
        self.indices[x] = 0
        x -= 1
        # the outer loop runs as long as a we have a carry
        while x >= 0:
            gear = self.gears[x]
            index = self.indices[x] + 1
            if index < len(gear):
                # no carry: done
                lst[x] = gear[index]
                self.indices[x] = index
                return
            lst[x] = gear[0]
            self.indices[x] = 0
            x -= 1
        else:
            self.lst = None
            self.stopped = True

    def fill_next_result(self):
        # the last gear is done here, in a function with no loop,
        # to allow the JIT to look inside
        if self.lst is None:
            self.lst = [None for gear in self.gears]
            for index, gear in enumerate(self.gears):
                self.lst[index] = gear[0]
            return
        lst = self.lst
        x = len(self.gears) - 1
        if x >= 0:
            gear = self.gears[x]
            index = self.indices[x] + 1
            if index < len(gear):
                # no carry: done
                lst[x] = gear[index]
                self.indices[x] = index
            else:
                self._rotate_previous_gears()
        else:
            self.stopped = True

    def __iter__(self):
        return self

    def __next__(self):
        if not self.stopped:
            self.fill_next_result()
        if self.stopped:
            raise StopIteration
        return tuple(self.lst)

    def __reduce__(self):
        if not self.stopped:
            gears = [tuple(gear) for gear in self.gears]
            result = [
                type(self),
                tuple(gears)
            ]
            if self.lst is not None:
                result = result + [tuple(self.indices)]
        else:
            result = [
                type(self),
                tuple([tuple([])])
            ]
        return tuple(result)

    def __setstate__(self, state):
        gear_count = len(self.gears)
        indices = list(state)
        lst = []
        for i, gear in enumerate(self.gears):
            index = indices[i]
            gear_size = len(gear)
            if self.indices is None or gear_size == 0:
                self.stopped = True
                return
            if index < 0:
                index = 0
            if index > gear_size - 1:
                index = gear_size - 1
            self.indices[i] = index
            lst.append(gear[index])
        self.lst = lst


class accumulate(object):
    """
    "accumulate(iterable) --> accumulate object

    Return series of accumulated sums."""

    def __init__(self, iterable, func=None):
        self.iterable = iter(iterable)
        self.func = func
        self.total = None

    def __iter__(self):
        return self

    def __next__(self):
        value = next(self.iterable)
        if self.total is None:
            self.total = value
            return value

        if self.func is None:
            self.total += value
        else:
            self.total = self.func(total, value)
        return self.total


class dropwhile(object):
    """
    dropwhile(predicate, iterable) --> dropwhile object

    Drop items from the iterable while predicate(item) is true.
    Afterwards, return every element until the iterable is exhausted.
    """

    def __init__(self, predicate, iterable):
        self.predicate = predicate
        self.iterable = iter(iterable)
        self.done_dropping = False

    def __iter__(self):
        return self

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

    def __init__(self, func, sequence):
        self.func = func or (lambda x: False)
        self.iterator = iter(sequence)

    def __iter__(self):
        return self

    def __next__(self):
        while True:
            n = next(self.iterator)
            if not self.func(n):
                return n


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
    def __init__(self, predicate, iterable):
        self._predicate = predicate
        self._iter = iter(iterable)

    def __iter__(self):
        return self

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
    def __init__(self, iterable, key=None):
        if key is None:
            key = lambda x: x
        self._keyfunc = key
        self._iter = iter(iterable)
        self._tgtkey = self._currkey = self._currvalue = xrange(0)

    def __iter__(self):
        return self

    def __next__(self):
        while self._currkey == self._tgtkey:
            self._currvalue = next(self._iter) # Exit on StopIteration
            self._currkey = self._keyfunc(self._currvalue)
        self._tgtkey = self._currkey
        return (self._currkey, self._grouper(self._tgtkey))

    def _grouper(self, tgtkey):
        while self._currkey == tgtkey:
            yield self._currvalue
            self._currvalue = next(self._iter) # Exit on StopIteration
            self._currkey = self._keyfunc(self._currvalue)


class combinations():
    """
    combinations(iterable, r) --> combinations object

    Return successive r-length combinations of elements in the iterable.

    combinations(range(4), 3) --> (0,1,2), (0,1,3), (0,2,3), (1,2,3)
    """

    def __init__(self, pool, indices, r):
        self.pool = pool
        self.indices = range(indices)
        if r < 0:
            raise ValueError("r must be non-negative")
        self.r = r
        self.last_result = None
        self.stopped = r > len(pool)

    def get_maximum(self, i):
        return i + len(self.pool) - self.r

    def max_index(self, j):
        return self.indices[j - 1] + 1

    def __iter__(self):
        return self

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
