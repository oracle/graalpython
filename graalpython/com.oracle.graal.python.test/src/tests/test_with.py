# Copyright (c) 2018, Oracle and/or its affiliates.
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
