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
import sys


# this code is ported from pypy
BLOCKLEN = 62
CENTER = ((BLOCKLEN - 1) // 2)


class Block(object):
    __slots__ = ('leftlink', 'rightlink', 'data')

    def __init__(self, leftlink, rightlink):
        self.leftlink = leftlink
        self.rightlink = rightlink
        self.data = [None] * BLOCKLEN


class Lock(object):
    pass


def _add(d1, d2):
    d1.extend(d2)
    return d1


def _mul(d, times):
    if not isinstance(times, int):
        raise TypeError("can't multiply sequence by non-int of type '%s'" % (type(times)))
    if times <= 0:
        d.clear()
    else:
        l = list(d)
        for _ in range(times - 1):
            d.extend(l)
    return d


def _next_or_none(_iter):
    try:
        return next(_iter)
    except StopIteration:
        return None


class deque(object):
    def __init__(self, iterable=None, maxlen=None):
        if maxlen is None:
            self._maxlen = sys.maxsize
        elif maxlen >= 0:
            self._maxlen = maxlen
        else:
            raise ValueError("maxlen must be non-negative")

        self._maxlen = sys.maxsize if maxlen is None else maxlen
        self.leftblock = Block(None, None)
        self.rightblock = self.leftblock
        self.leftindex = CENTER + 1
        self.rightindex = CENTER
        self.len = 0
        self._lock = None
        assert self.leftindex > 0
        assert self.rightindex > 0
        if iterable is not None:
            self.extend(iterable)

    def pop(self):
        """Remove and return the rightmost element."""
        if self.len == 0:
            raise IndexError("pop from an empty deque")

        self.len -= 1
        ri = self.rightindex
        obj = self.rightblock.data[ri]
        self.rightblock.data[ri] = None
        ri -= 1
        if ri < 0:
            if self.len == 0:
                # re-center instead of freeing the last block
                self.leftindex = CENTER + 1
                ri = CENTER
            else:
                b = self.rightblock.leftlink
                self.rightblock = b
                b.rightlink = None
                ri = BLOCKLEN - 1
        self.rightindex = ri
        self._modified()
        return obj

    def _modified(self):
        self._lock = None
        
    def _getlock(self):
        if self._lock is None:
            self._lock = Lock()
        return self._lock

    def _checklock(self, lock):
        if lock is not self._lock:
            raise RuntimeError("deque mutated during iteration")

    def _trimleft(self):
        if self.len > self._maxlen:
            self.popleft()
            assert self.len == self._maxlen

    def _trimright(self):
        if self.len > self._maxlen:
            self.pop()
            assert self.len == self._maxlen

    def append(self, x):
        """Add an element to the right side of the deque."""
        ri = self.rightindex + 1
        if ri >= BLOCKLEN:
            b = Block(self.rightblock, None)
            self.rightblock.rightlink = b
            self.rightblock = b
            ri = 0
        self.rightindex = ri
        self.rightblock.data[ri] = x
        self.len += 1
        self._trimleft()
        self._modified()

    def appendleft(self, x):
        """Add an element to the left side of the deque."""
        li = self.leftindex - 1
        if li < 0:
            b = Block(None, self.leftblock)
            self.leftblock.leftlink = b
            self.leftblock = b
            li = BLOCKLEN - 1
        self.leftindex = li
        self.leftblock.data[li] = x
        self.len += 1
        self._trimright()
        self._modified()

    def clear(self):
        """Remove all elements from the deque."""
        self.leftblock = Block(None, None)
        self.rightblock = self.leftblock
        self.leftindex = CENTER + 1
        self.rightindex = CENTER
        self.len = 0
        self._modified()

    def count(self, x):
        """Return number of occurrences of value."""
        result = 0
        block = self.leftblock
        index = self.leftindex
        lock = self._getlock()
        for i in range(self.len):
            w_item = block.data[index]
            if w_item == x:
                result += 1
            self._checklock(lock)
            # Advance the block/index pair
            index += 1
            if index >= BLOCKLEN:
                block = block.rightlink
                index = 0
        return result

    def extend(self, iterable):
        """Extend the right side of the deque with elements from the iterable"""
        # Handle case where id(deque) == id(iterable)
        if self == iterable:
            iterable = list(iterable)

        _iter = iter(iterable)
        while True:
            try:
                obj = next(_iter)
            except StopIteration:
                break
            self.append(obj)

    def __iadd__(self, other):
        return _add(self, other)

    def __add__(self, other):
        if not isinstance(other, deque):
            raise TypeError("can only concatenate deque (not '%s') to deque" % (type(other)))
        return _add(deque(self, maxlen=self.maxlen), other)

    def __imul__(self, times):
        return _mul(self, times)

    def __mul__(self, times):
        return _mul(deque(self, maxlen=self.maxlen), times)

    def __rmul__(self, times):
        return _mul(deque(self, maxlen=self.maxlen), times)

    def __hash__(self):
        raise TypeError("unhashable type: '%s'" % self.__name__)

    def extendleft(self, iterable):
        """Extend the left side of the deque with elements from the iterable"""
        # Handle case where id(deque) == id(iterable)
        if self == iterable:
            iterable = list(iterable)

        _iter = iter(iterable)
        while True:
            try:
                obj = next(_iter)
            except StopIteration:
                break
            self.appendleft(obj)

    def popleft(self):
        """Remove and return the leftmost element."""
        if self.len == 0:
            raise IndexError("pop from an empty deque")

        self.len -= 1
        li = self.leftindex
        obj = self.leftblock.data[li]
        self.leftblock.data[li] = None
        li += 1
        if li >= BLOCKLEN:
            if self.len == 0:
                # re-center instead of freeing the last block
                li = CENTER + 1
                self.rightindex = CENTER
            else:
                b = self.leftblock.rightlink
                self.leftblock = b
                b.leftlink = None
                li = 0
        self.leftindex = li
        self._modified()
        return obj

    def remove(self, x):
        """Remove first occurrence of value."""
        block = self.leftblock
        index = self.leftindex
        lock = self._getlock()
        for i in range(self.len):
            item = block.data[index]
            self._checklock(lock)
            if item == x:
                self._delitem(i)
                return
            # Advance the block/index pair
            index += 1
            if index >= BLOCKLEN:
                block = block.rightlink
                index = 0
        raise ValueError("deque.remove(x): x not in deque")

    def reverse(self):
        """Reverse *IN PLACE*."""
        li = self.leftindex
        lb = self.leftblock
        ri = self.rightindex
        rb = self.rightblock
        for i in range(self.len >> 1):
            lb.data[li], rb.data[ri] = rb.data[ri], lb.data[li]
            li += 1
            if li >= BLOCKLEN:
                lb = lb.rightlink
                li = 0
            ri -= 1
            if ri < 0:
                rb = rb.leftlink
                ri = BLOCKLEN - 1

    def rotate(self, n=1):
        """Rotate the deque n steps to the right (default n=1).  If n is negative, rotates left."""
        len = self.len
        if len <= 1:
            return
        half_len = len >> 1
        if n > half_len or n < -half_len:
            n %= len
            if n > half_len:
                n -= len
        i = 0
        while i < n:
            self.appendleft(self.pop())
            i += 1
        while i > n:
            self.append(self.popleft())
            i -= 1

    def __iter__(self):
        return _DequeIter(self)

    def __reversed__(self):
        """Return a reverse iterator over the deque."""
        return _DequeRevIter(self)

    def __len__(self):
        return self.len

    def __repr__(self):
        # TODO: this does not handle infinite repr recursive calls ... (GR-10763)
        try:
            list_repr = "[" + ", ".join([repr(x) for x in self]) + ']'
        finally:
            pass
        if self.maxlen is None:
            maxlen_repr = ''
        else:
            maxlen_repr = ', maxlen=%d' % (self.maxlen,)
        return 'deque(%s%s)' % (list_repr, maxlen_repr)

    def __compare__(self, other, op):
        if not isinstance(other, deque):
            return NotImplemented

        it1 = iter(self)
        it2 = iter(other)
        while True:
            x1 = _next_or_none(it1)
            x2 = _next_or_none(it2)
            if x1 is None or x2 is None:
                if op == 'eq':
                    return x1 is x2  # both None
                if op == 'ne':
                    return x1 is not x2
                if op == 'lt':
                    return x2 is not None
                if op == 'le':
                    return x1 is None
                if op == 'gt':
                    return x1 is not None
                if op == 'ge':
                    return x2 is None
                assert False, "bad value for op"

            if not x1 == x2:
                if op == 'eq':
                    return False
                if op == 'ne':
                    return True
                if op == 'lt':
                    return x1 < x2
                if op == 'le':
                    return x1 <= x2
                if op == 'gt':
                    return x1 > x2
                if op == 'ge':
                    return x1 >= x2
                assert False, "bad value for op"

    def __contains__(self, item):
        for itm in self:
            if itm == item:
                return True
        return False

    def __lt__(self, other):
        return self.__compare__(other, 'lt')

    def __le__(self, other):
        return self.__compare__(other, 'le')

    def __eq__(self, other):
        return self.__compare__(other, 'eq')

    def __ne__(self, other):
        return self.__compare__(other, 'ne')

    def __gt__(self, other):
        return self.__compare__(other, 'gt')

    def __ge__(self, other):
        return self.__compare__(other, 'ge')

    def index(self, i):
        if i < (self.len >> 1):
            i += self.leftindex
            b = self.leftblock
            while i >= BLOCKLEN:
                b = b.rightlink
                i -= BLOCKLEN
        else:
            i = i - self.len + 1     # then i <= 0
            i += self.rightindex
            b = self.rightblock
            while i < 0:
                b = b.leftlink
                i += BLOCKLEN
        assert i >= 0
        return b, i

    def _delitem(self, i):
        # delitem() implemented in terms of rotate for simplicity and
        # reasonable performance near the end points.
        self.rotate(-i)
        self.popleft()
        self.rotate(i)

    def __getitem__(self, idx):
        b, i = self.index(idx)
        return b.data[i]

    def __setitem__(self, idx, value):
        b, i = self.index(idx)
        b.data[i] = value

    def __delitem__(self, idx):
        self._delitem(idx)

    def copy(self):
        """Return a shallow copy of a deque."""
        if self._maxlen == sys.maxsize:
            return deque(self)
        else:
            return deque(self, self.maxlen)

    def __copy__(self):
        return self.copy()

    def __reduce__(self):
        """Return state information for pickling."""
        _type = type(self)
        _dict = self.__dict__
        _list = list(self)
        if _dict is None:
            if self._maxlen == sys.maxsize:
                result = [_type, tuple([_list])]
            else:
                result = [_type, tuple([_list, self._maxlen])]
        else:
            if self._maxlen == sys.maxsize:
                _len = None
            else:
                _len = self._maxlen
            result = [_type, tuple([_list, _len]), _dict]
        return tuple(result)

    @property
    def maxlen(self):
        if self._maxlen == sys.maxsize:
            return None
        else:
            return self._maxlen


class _DequeIter(object):
    def __init__(self, dq):
        self._deque = dq
        self.block = dq.leftblock
        self.index = dq.leftindex
        self.counter = dq.len
        self.lock = dq._getlock()
        assert self.index > 0

    def __iter__(self):
        return self

    def __len__(self):
        return self.counter

    def __next__(self):
        if self.lock is not self._deque._lock:
            self.counter = 0
            raise RuntimeError("deque mutated during iteration")
        if self.counter == 0:
            raise StopIteration(None)
        self.counter -= 1
        ri = self.index
        x = self.block.data[ri]
        ri += 1
        if ri == BLOCKLEN:
            self.block = self.block.rightlink
            ri = 0
        self.index = ri
        return x


class _DequeRevIter(object):
    def __init__(self, dq):
        self._deque = dq
        self.block = dq.rightblock
        self.index = dq.rightindex
        self.counter = dq.len
        self.lock = dq._getlock()
        assert self.index > 0

    def __iter__(self):
        return self

    def __len__(self):
        return self.counter

    def __next__(self):
        if self.lock is not self._deque._lock:
            self.counter = 0
            raise RuntimeError("deque mutated during iteration")
        if self.counter == 0:
            raise StopIteration(None)
        self.counter -= 1
        ri = self.index
        x = self.block.data[ri]
        ri -= 1
        if ri < 0:
            self.block = self.block.leftlink
            ri = BLOCKLEN - 1
        self.index = ri
        return x


class defaultdict(dict):
    def __new__(self, default_factory, *args, **kwds):
        dict.__init__(self, args, kwds)
        self.default_factory = default_factory

    def __missing__(self, key):
        if self.default_factory is None:
            raise KeyError((key,))
        self[key] = value = self.default_factory()
        return value
