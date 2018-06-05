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
    pass


class chain():
    pass


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
