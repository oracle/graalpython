# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_traceback_dir():
    try:
        raise Exception("the unthinkable happened!")
    except Exception:
        _type, _err, tb = sys.exc_info()
        assert _type == Exception
        assert str(_err) == "the unthinkable happened!"
        assert _err.args == ("the unthinkable happened!", )
        assert hasattr(tb, '__dir__')
        assert set(dir(tb)) == {'tb_frame', 'tb_next', 'tb_lasti', 'tb_lineno'}


def test_import():
    imported = True
    try:
        import traceback
    except ImportError:
        imported = False
    assert imported


def assert_has_traceback(code, expected_elements):
    import traceback
    stack = None
    try:
        code()
    except Exception:
        stack = traceback.TracebackException(*sys.exc_info()).stack
    expected_elements = [('assert_has_traceback', 'code()')] + expected_elements
    actual_elements = []
    for frame in stack:
        actual_elements.append((frame.name, frame.line))
    assert expected_elements == actual_elements, \
        "Expected traceback elements:\n{}\nGot:\n{}".format('\n'.join(map(str, expected_elements)), '\n'.join(map(str, actual_elements)))


def test_basic_traceback():
    def foo():
        raise RuntimeError("test")

    def test():
        foo()

    assert_has_traceback(
        test,
        [
            ('test', 'foo()'),
            ('foo', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_direct():
    def reraise():
        try:
            raise RuntimeError("test")
        except Exception:
            raise

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_direct_no_accumulate():
    # This is a tricky corner case where sys.exc_info()[1].__traceback__ != sys.exc_info()[2] because e gets mutated by
    # the inner exception block, but raise uses sys.exc_info which contains the unmodified traceback at the time the
    # the exception was caught
    def reraise():
        try:
            raise RuntimeError("test")
        except Exception as e:
            try:
                raise e
            except Exception:
                pass
            raise

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_named():
    def reraise():
        try:
            raise RuntimeError("test")
        except Exception as e:
            raise e

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise e'),
            ('reraise', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_captured():
    captured_exc = None

    try:
        raise RuntimeError("test")
    except Exception as e:
        captured_exc = e

    def reraise():
        raise captured_exc

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise captured_exc'),
            ('test_reraise_captured', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_multiple():
    # Test that the exception traceback accumulates frames even when reraised independently
    captured_exc = None

    try:
        raise RuntimeError("test")
    except Exception as e:
        captured_exc = e

    def reraise():
        raise captured_exc

    try:
        reraise()
    except Exception:
        pass

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise captured_exc'),
            ('test_reraise_multiple', 'reraise()'),
            ('reraise', 'raise captured_exc'),
            ('test_reraise_multiple', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_with_traceback():
    captured_exc = None

    try:
        raise RuntimeError("test")
    except Exception as e:
        captured_exc = e

    def reraise_with_traceback():
        exc = NameError("reraised").with_traceback(captured_exc.__traceback__)
        raise exc

    assert_has_traceback(
        reraise_with_traceback,
        [
            ('reraise_with_traceback', 'raise exc'),
            ('test_reraise_with_traceback', 'raise RuntimeError("test")'),
        ]
    )


def test_reraise_with_traceback_multiple():
    # Test that the exception traceback doesn't accumulate frames when copied to different exception using with_traceback
    captured_exc = None

    try:
        raise RuntimeError("test")
    except Exception as e:
        captured_exc = e

    def reraise_with_traceback():
        exc = NameError("reraised").with_traceback(captured_exc.__traceback__)
        raise exc

    # If the implementation is wrong, this could affect captured_exc.__traceback__
    try:
        reraise_with_traceback()
    except Exception:
        pass

    assert_has_traceback(
        reraise_with_traceback,
        [
            ('reraise_with_traceback', 'raise exc'),
            ('test_reraise_with_traceback_multiple', 'raise RuntimeError("test")'),
        ]
    )


def test_with():
    stored_exc = None

    class cm:
        def __enter__(self):
            return self

        def __exit__(self, etype, e, tb):
            nonlocal stored_exc
            stored_exc = e
            return True

    with cm():
        raise OSError("test")

    def reraise():
        raise stored_exc

    assert_has_traceback(
        reraise,
        [
            ('reraise', 'raise stored_exc'),
            ('test_with', 'raise OSError("test")'),
        ]
    )

