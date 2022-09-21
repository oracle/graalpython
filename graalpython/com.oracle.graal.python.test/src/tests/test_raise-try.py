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
# Qunaibit 10/10/2013
# Raise Exceptions

got_kbd_int = False
got_finally = False


def divide(x, y):
    global got_kbd_int, got_finally
    try:
        result = x / y
        raise KeyboardInterrupt
    except ZeroDivisionError as err:
        assert False
    except KeyboardInterrupt as k:
        got_kbd_int = True
    else:
        assert False
    finally:
        got_finally = True


def test_raise():
    divide(1, 1)
    assert got_kbd_int
    assert got_finally


def test_exception_restoring():
    import sys
    trace = []
    try:
        try:
            assert sys.exc_info() == (None, None, None)
            trace.append(1)
            raise ValueError("1")
        except ValueError:
            assert sys.exc_info()[0] == ValueError
            try:
                trace.append(2)
                raise KeyError("2")
            except KeyError:
                assert sys.exc_info()[0] == KeyError
                trace.append(3)
            trace.append(4)
            raise
    except ValueError:
        assert sys.exc_info()[0] == ValueError, "IS: %s" % sys.exc_info()[0]
        trace.append(5)
    assert trace == [1, 2, 3, 4, 5]


def test_exception_restoring_finally():
    import sys
    trace = []
    try:
        try:
            assert sys.exc_info() == (None, None, None)
            trace.append(1)
            raise ValueError("1")
        except ValueError:
            assert sys.exc_info()[0] == ValueError
            trace.append(2)
            raise KeyError("2")
        finally:
            assert sys.exc_info()[0] == KeyError, "was: %s" % sys.exc_info()[0]
            trace.append(3)
    except KeyError:
        assert sys.exc_info()[0] == KeyError, "was: %s" % sys.exc_info()[0]
        trace.append(4)
    assert trace == [1, 2, 3, 4]


def test_exception_restoring_raise_finally():
    import sys
    trace = []
    try:
        try:
            try:
                assert sys.exc_info() == (None, None, None)
                trace.append(1)
                raise ValueError("1")
            except ValueError:
                assert sys.exc_info()[0] == ValueError
                trace.append(2)
                raise KeyError("2")
            finally:
                assert sys.exc_info()[0] == KeyError, "was: %s" % sys.exc_info()[0]
                trace.append(3)
                raise TypeError
        finally:
            assert sys.exc_info()[0] == TypeError, "was: %s" % sys.exc_info()[0]
            trace.append(4)
    except TypeError:
        assert sys.exc_info()[0] == TypeError, "was: %s" % sys.exc_info()[0]
        trace.append(5)
    assert trace == [1, 2, 3, 4, 5]


def test_exception_restoring_with_return():
    import sys
    trace = []
    def handler():
        try:
            trace.append(2)
            raise KeyError("2")
        except KeyError:
            assert sys.exc_info()[0] == KeyError
            trace.append(3)
            return
            trace.append(-1)
        trace.append(-1)

    try:
        try:
            assert sys.exc_info() == (None, None, None)
            trace.append(1)
            raise ValueError("1")
        except ValueError:
            assert sys.exc_info()[0] == ValueError
            handler()
            trace.append(4)
            raise
    except ValueError:
        assert sys.exc_info()[0] == ValueError
        trace.append(5)
    assert trace == [1, 2, 3, 4, 5]
