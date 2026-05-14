# Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import os
import doctest
import sys
import unittest
from tests import util


# Copied from test_doctest
class _FakeInput:
    def __init__(self, lines):
        self.lines = lines

    def readline(self):
        line = self.lines.pop(0)
        print(line)
        return line + '\n'


# Copied from test_pdb
class PdbTestInput(object):
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


# Extracted from CPython test_pdb_basic_commands and test_pdb_issue_gh_91742.
# The full upstream doctests require unsupported Bytecode DSL f_lineno jumps;
# these keep the non-jump debugger coverage that is still relevant.
def doctest_pdb_args_for_kwonly_and_posonly():
    """
    Test that args displays keyword-only and positional-only parameters.

    >>> def kwonly_func(arg=None, *, kwonly=None):
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     pass

    >>> def posonly_func(a, b, /, c=None):
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     pass

    >>> with PdbTestInput([
    ...     'args',
    ...     'continue',
    ...     'args',
    ...     'continue',
    ... ]):
    ...     kwonly_func('value', kwonly=True)
    ...     posonly_func(1, 2, c=3)
    > <doctest tests.test_pdb.doctest_pdb_args_for_kwonly_and_posonly[0]>(3)kwonly_func()
    -> pass
    (Pdb) args
    arg = 'value'
    kwonly = True
    (Pdb) continue
    > <doctest tests.test_pdb.doctest_pdb_args_for_kwonly_and_posonly[1]>(3)posonly_func()
    -> pass
    (Pdb) args
    a = 1
    b = 2
    c = 3
    (Pdb) continue
    """


def doctest_pdb_multiline_call_line_tracing():
    """
    Test stepping through a nested function and stopping on a multiline call continuation.

    >>> def test_function():
    ...     __author__ = "pi"
    ...     __version__ = "3.14"
    ...
    ...     def about():
    ...         '''About'''
    ...         print(f"Author: {__author__!r}",
    ...             f"Version: {__version__!r}",
    ...             sep=" ")
    ...
    ...     import pdb; pdb.Pdb(nosigint=True, readrc=False).set_trace()
    ...     about()

    >>> with PdbTestInput([  # doctest: +NORMALIZE_WHITESPACE
    ...     'step',
    ...     'next',
    ...     'next',
    ...     'continue',
    ... ]):
    ...     test_function()
    > <doctest tests.test_pdb.doctest_pdb_multiline_call_line_tracing[0]>(12)test_function()
    -> about()
    (Pdb) step
    --Call--
    > <doctest tests.test_pdb.doctest_pdb_multiline_call_line_tracing[0]>(5)about()
    -> def about():
    (Pdb) next
    > <doctest tests.test_pdb.doctest_pdb_multiline_call_line_tracing[0]>(7)about()
    -> print(f"Author: {__author__!r}",
    (Pdb) next
    > <doctest tests.test_pdb.doctest_pdb_multiline_call_line_tracing[0]>(8)about()
    -> f"Version: {__version__!r}",
    (Pdb) continue
    Author: 'pi' Version: '3.14'
    """


if not util.IS_BYTECODE_DSL:
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
