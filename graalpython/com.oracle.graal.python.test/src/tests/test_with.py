# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# Qunaibit 02/05/2014
# With Statement

import sys

a = 5

LOG = []
LOG1 = []
LOG2 = []
LOG3 = []


class Context:

    def __init__(self, log, suppress_exception, raise_exception):
        self._log = log
        self._suppress = suppress_exception
        self._raise = raise_exception

    def __enter__(self):
        self._log.append("__enter__")
        return self

    def __exit__(self, type, value, trace):
        self._log.append("type: %s" % type)
        self._log.append("value: %s" % value)
#         LOG.append("trace: %s" % trace) # trace back is not supported yet
        return self._suppress

    def do_something(self):
        self._log.append("do_something")
        bar = 1
        if self._raise:
            bar = bar / 0
        return bar + 10


def payload(log, suppress_exception, raise_exception, do_return):
    a = 5
    try:
        with Context(log, suppress_exception, raise_exception) as sample:
            if do_return:
                a = sample.do_something()
                return a
            else:
                a = sample.do_something()
    except ZeroDivisionError:
        log.append("Exception has been thrown correctly")

    else:
        log.append("no exception or exception suppressed")

    finally:
        log.append("a = %s" % a)

    return a


def test_with_dont_suppress():
    payload(LOG, False, True, False)
    assert LOG == [
        "__enter__" ,
        "do_something" ,
        "type: <class 'ZeroDivisionError'>" ,
        "value: division by zero" ,
        "Exception has been thrown correctly" ,
         "a = 5"
    ], "was: " + str(LOG)


def test_with_suppress():
    payload(LOG1, True, True, False)
    assert LOG1 == [ "__enter__" ,
                    "do_something" ,
                    "type: <class 'ZeroDivisionError'>" ,
                    "value: division by zero" ,
                    "no exception or exception suppressed" ,
                    "a = 5"
    ], "was: " + str(LOG1)


def with_return(ctx):
    with ctx as sample:
        return ctx.do_something()
    return None


def test_with_return():
    result = payload(LOG2, False, False, True)
    assert result == 11
    assert LOG2 == [ "__enter__",
                    "do_something",
                    "type: None",
                    "value: None",
                    "a = 11",
    ], "was: " + str(LOG2)


def test_with_return_and_exception():
    result = payload(LOG3, True, False, True)
    assert result == 11
    assert LOG3 == [ "__enter__",
                    "do_something",
                    "type: None",
                    "value: None",
                    "a = 11",
    ], "was: " + str(LOG3)


def test_with_restore():
    import sys
    log = []
    try:
        log.append("raise")
        raise ValueError("1")
    except:
        assert sys.exc_info()[0] == ValueError
        with Context(log, True, False) as sample:
            assert sys.exc_info()[0] == ValueError
            log.append("with body")
        assert sys.exc_info()[0] == ValueError
        log.append("except exit")
    assert log == [  "raise"
                   , "__enter__"
                   , "with body"
                   , "type: None"
                   , "value: None"
                   , "except exit"], "was: %s" % log


def test_with_restore_raise():
    import sys
    log = []
    try:
        log.append("raise")
        raise ValueError("1")
    except:
        assert sys.exc_info()[0] == ValueError
        with Context(log, True, False) as sample:
            assert sys.exc_info()[0] == ValueError
            log.append("with body")
            raise TypeError
            log.append("INVALID")
        assert sys.exc_info()[0] == ValueError
        log.append("except exit")
    assert log == [  "raise"
                   , "__enter__"
                   , "with body"
                   , "type: <class 'TypeError'>"
                   , "value: "
                   , "except exit"], "was: %s" % log


def test_with_in_generator():
    enter = 0
    exit = 0
    itr = 0
    nxt = 0
    r = []

    class Gen():
        def __init__(self):
            self.l = iter([1,2,3])

        def __enter__(self):
            nonlocal enter
            enter += 1
            return self

        def __exit__(self, *args):
            nonlocal exit
            exit += 1

        def __iter__(self):
            nonlocal itr
            itr += 1
            return self

        def __next__(self):
            nonlocal nxt
            nxt += 1
            return next(self.l)

    def gen():
        with Gen() as g:
            for i in g:
                yield i

    e = None
    try:
        try:
            raise OverflowError
        except OverflowError as e1:
            e = e1
            for i in gen():
                r.append(i)
            raise
    except OverflowError as e2:
        assert e2 is e
    else:
        assert False

    assert r == [1,2,3], r
    assert enter == 1, enter
    assert exit == 1, exit
    assert itr == 1, itr
    assert nxt == 4, nxt


def test_with_non_inherited():
    class X():
        pass

    x = X()
    x.__enter__ = lambda s: s

    try:
        with x as l:
            pass
    except TypeError as e:
        assert str(e) == "'X' object does not support the context manager protocol"
    else:
        assert False

    y_enter_called = 0

    class Y():
        def __enter__(self):
            nonlocal y_enter_called
            y_enter_called += 1

    try:
        with Y() as y:
            pass
    except TypeError as e:
        assert str(e) == "'Y' object does not support the context manager protocol (missed __exit__ method)"
    else:
        assert False
    assert y_enter_called == 0
