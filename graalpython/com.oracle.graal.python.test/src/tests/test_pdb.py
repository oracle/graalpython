# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

from test.test_doctest import _FakeInput

import doctest
import sys


class PdbTestInput(object):
    """Context manager that makes testing Pdb in doctests easier."""

    def __init__(self, input):
        self.input = input

    def __enter__(self):
        self.real_stdin = sys.stdin
        sys.stdin = _FakeInput(self.input)
        self.orig_trace = sys.gettrace() if hasattr(sys, 'gettrace') else None

    def __exit__(self, *exc):
        sys.stdin = self.real_stdin
        if self.orig_trace:
            sys.settrace(self.orig_trace)


def doctest_pdb_locals():
    """
    Test that locals get synced after breakpoint

    >>> def test_function():
    ...     a = 1
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     a = 2

    >>> with PdbTestInput([
    ...     'p a',
    ...     'next',
    ...     'p a',
    ...     'continue',
    ... ]):
    ...     test_function()
    > <doctest tests.test_pdb.doctest_pdb_locals[0]>(4)test_function()
    -> a = 2
    (Pdb) p a
    1
    (Pdb) next
    --Return--
    > <doctest tests.test_pdb.doctest_pdb_locals[0]>(4)test_function()->None
    -> a = 2
    (Pdb) p a
    2
    (Pdb) continue
    """


def doctest_pdb_locals_generator():
    """
    Test that locals get synced after breakpoint in a generator

    >>> def test_function():
    ...     a = 1
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     a = 2
    ...     yield

    >>> with PdbTestInput([
    ...     'p a',
    ...     'next',
    ...     'p a',
    ...     'continue',
    ... ]):
    ...     next(test_function())
    > <doctest tests.test_pdb.doctest_pdb_locals_generator[0]>(4)test_function()
    -> a = 2
    (Pdb) p a
    1
    (Pdb) next
    > <doctest tests.test_pdb.doctest_pdb_locals_generator[0]>(5)test_function()
    -> yield
    (Pdb) p a
    2
    (Pdb) continue
    """


def doctest_pdb_locals_sync_back():
    """
    Test that locals set by debugger get propagated back into the frame.

    >>> def test_function():
    ...     foo = 1
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     return foo

    >>> with PdbTestInput([
    ...     'p foo',
    ...     'foo = 5',
    ...     'continue',
    ... ]):
    ...     print(test_function())
    > <doctest tests.test_pdb.doctest_pdb_locals_sync_back[0]>(4)test_function()
    -> return foo
    (Pdb) p foo
    1
    (Pdb) foo = 5
    (Pdb) continue
    5
    """


def test_run_doctests():
    doctest.testmod(sys.modules[__name__], raise_on_error=True)
