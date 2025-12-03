# Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import unittest
import difflib
import sys
import signal
from tests import util
import builtins


def basic():
    return 'return value'


basic.events = [((), [(0, 'basic', 'call', None),
                      (1, 'basic', 'line', None),
                      (1, 'basic', 'return', 'return value')])]

def more_complex(a):
    if a:
        b = 3
    else:
        b = 5


more_complex.events = [((True,), [(0, 'more_complex', 'call', None),
                                  (1, 'more_complex', 'line', None),
                                  (2, 'more_complex', 'line', None),
                                  (2, 'more_complex', 'return', None)]),
                       ((False,), [(0, 'more_complex', 'call', None),
                                   (1, 'more_complex', 'line', None),
                                   (4, 'more_complex', 'line', None),
                                   (4, 'more_complex', 'return', None)])]


# there are 4 line events on line 3, since there is the first check of the loop flag, then 4 backward jumps, last one ending the loop

def oneline_loop():
    items = range(0, 3)
    i = 4
    while i: i -= 1


oneline_loop.events = [((), [(0, 'oneline_loop', 'call', None),
                             (1, 'oneline_loop', 'line', None),
                             (2, 'oneline_loop', 'line', None),
                             (3, 'oneline_loop', 'line', None),
                             (3, 'oneline_loop', 'line', None),
                             (3, 'oneline_loop', 'line', None),
                             (3, 'oneline_loop', 'line', None),
                             (3, 'oneline_loop', 'return', None)])]

def helper(): # line -5
    str(1)
    return 2


def two_functions():
    str(1)
    var = helper()
    return var + 1

two_functions.events = [((), [(0, 'two_functions', 'call', None),
                              (1, 'two_functions', 'line', None),
                              (2, 'two_functions', 'line', None),
                              (-5, 'helper', 'call', None),
                              (-4, 'helper', 'line', None),
                              (-3, 'helper', 'line', None),
                              (-3, 'helper', 'return', 2),
                              (3, 'two_functions', 'line', None),
                              (3, 'two_functions', 'return', 3)])]

def gen(): # line -5
    yield 1
    yield 2


def generator_example():
    x, m = gen(), {}
    while m.setdefault('i', next(x, False)): # this prevents CPython from tracing a StopIteration once gen() ends
        del m['i']
    return m['i']

generator_example.events = [((), [(0, 'generator_example', 'call', None),
                                  (1, 'generator_example', 'line', None),
                                  (2, 'generator_example', 'line', None),
                                  (-5, 'gen', 'call', None),
                                  (-4, 'gen', 'line', None),
                                  (-4, 'gen', 'return', 1),
                                  (3, 'generator_example', 'line', None),
                                  (2, 'generator_example', 'line', None),
                                  (-4, 'gen', 'call', None),
                                  (-3, 'gen', 'line', None),
                                  (-3, 'gen', 'return', 2),
                                  (3, 'generator_example', 'line', None),
                                  (2, 'generator_example', 'line', None),
                                  (-3, 'gen', 'call', None),
                                  (-3, 'gen', 'return', None),
                                  (4, 'generator_example', 'line', None),
                                  (4, 'generator_example', 'return', False)])]

def f_trace_delete():
    del sys._getframe().f_trace
    return 1

f_trace_delete.events = [((), [(0, 'f_trace_delete', 'call', None),
                               (1, 'f_trace_delete', 'line', None)])]

def make_test_method(fun, name):
    def test_case(self):
        for args, events in fun.events:
            try:
                self.events = []
                self.first_line = fun.__code__.co_firstlineno
                sys.settrace(self.trace)
                fun(*args)
            finally:
                sys.settrace(None)
            if self.events != events:
                self.fail(str(args) + '\n' + '\n'.join(difflib.ndiff([str(x) for x in events], [str(x) for x in self.events])))

    test_case.__name__ = name
    return test_case


@unittest.skipUnless(os.environ.get('BYTECODE_DSL_INTERPRETER') == 'false', "TODO: FrameSlotTypeException with reparsing")
class TraceTests(unittest.TestCase):
    def trace(self, frame, event, arg):
        code = frame.f_code
        name = code.co_name
        if event == 'exception':
            self.events.append((frame.f_lineno - self.first_line, name, event, arg[0]))
        else:
            self.events.append((frame.f_lineno - self.first_line, name, event, arg))
        return self.trace
    test_01_basic = make_test_method(basic, 'test_01_basic')
    test_02_more_complex = make_test_method(more_complex, 'test_02_more_complex')
    # TODO need to adapt to PEP 626
    # test_03_oneline_loop = make_test_method(oneline_loop, 'test_03_oneline_loop')
    test_04_two_functions = make_test_method(two_functions, 'test_04_two_functions')
    test_05_generator_example = make_test_method(generator_example, 'test_05_generator_example')
    def test_06_f_trace_preserved(self):
        def erroring_trace(*_):
            raise ValueError
        def fun2():
            str(0)
        def fun1():
            try:
                sys.settrace(erroring_trace)
                sys._getframe().f_trace = self.trace
                fun2()
            except ValueError:
                pass
            else:
                self.fail("didn't raise ValueError")
            str(1)
            sys.settrace(self.trace)
            str(2)
            sys.settrace(None)

        self.first_line = fun1.__code__.co_firstlineno
        self.events = []
        sys.settrace(None)
        fun1()
        events = [(4, 'fun1', 'line', None),
                  (11, 'fun1', 'line', None),
                  (12, 'fun1', 'line', None)]
        if self.events != events:
            self.fail('\n'+'\n'.join(difflib.ndiff([str(x) for x in events], [str(x) for x in self.events])))

    def simpler_trace(self, fr, ev, arg):
        self.events.append(fr.f_code.co_name)

    @unittest.skipIf(not hasattr(signal, 'SIGUSR1'), "User defined signal not present")
    @unittest.skipIf(not hasattr(builtins, '__graalpython__'), "async actions do get traced in CPython")
    def test_07_async_actions_not_traced(self):
        def handler(*_): handler.called = 1

        def helper(): return 1
        signal.signal(signal.SIGUSR1, handler)
        self.events = []
        sys.settrace(self.simpler_trace)
        signal.raise_signal(signal.SIGUSR1)
        for i in range(1000): helper()
        sys.settrace(None)
        # handler.called is not checked, since it could cause a transient
        for name in self.events:
            self.assertEqual(name, 'helper')

    test_08_frame_f_trace_deletable = make_test_method(f_trace_delete, 'test_08_frame_f_trace_deletable')

class TracingEventsUnitTest(unittest.TestCase):
    def trace(self, frame, event, arg):
        code = frame.f_code
        name = code.co_name
        self.events.append((frame.f_lineno - self.first_line, name, event))
        return self.trace

    def assert_events(self, expected_events, actual_events):
        if expected_events != actual_events:
            self.fail('\n'+'\n'.join(difflib.ndiff([str(x) for x in actual_events], [str(x) for x in expected_events])))

    def trace_function(self, func):
        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

    def trace_function_with_offset(self, func, offset_func):
        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)
        return offset_func.__code__.co_firstlineno - self.first_line

class TraceTestsStmtWith(TracingEventsUnitTest):
    @util.skipUnlessBytecodeDSL("Incorrect break in with statement tracing.")
    @util.skipIfBytecodeDSL("TODO: Breaking from within with: __exit__ sometimes won't get traced.")
    def test_09_break_in_with(self):
        class C:
            def __enter__(self):
                return self
            def __exit__(*args):
                pass

        def func_break():
            for i in (1,2):
                with C():
                    break
            pass

        self.first_line = func_break.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func_break()
        sys.settrace(None)

        events = [
            (0, 'func_break', 'call'),
            (1, 'func_break', 'line'),
            (2, 'func_break', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (3, 'func_break', 'line'),
            (2, 'func_break', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (4, 'func_break', 'line'),
            (4, 'func_break', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_10_if_false_in_with_multiple_nested(self):
        class C:
            def __enter__(self):
                return self
            def __exit__(*args):
                pass

        def func():
            with C():
                with C():
                    with C():
                        if False:
                            pass

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (2, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (3, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (4, 'func', 'line'),
            (3, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (2, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (1, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("Incorrect break in with statement tracing.")
    @util.skipIfBytecodeDSL("TODO: Breaking from within with: __exit__ sometimes won't get traced.")
    def test_11_break_in_with_nested(self):
        class C:
            def __enter__(self):
                return self
            def __exit__(*args):
                pass

        def func():
            with C():
                for i in (1, 2):
                    with C():
                        with C():
                            break

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (4, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (5, 'func', 'line'),
            (4, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (3, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (1, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("Incorrect break in with statement tracing.")
    @util.skipIfBytecodeDSL("TODO: Breaking from within with: __exit__ sometimes won't get traced.")
    def test_12_reraise(self):
        def func():
            try:
                try:
                    raise ValueError(13)
                except ValueError:
                    raise
            except Exception:
                pass

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (7, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_13_multiline_binop(self):
        v1 = 1
        v2 = 2
        v3 = 3
        v4 = 4
        v5 = 5
        v6 = 6

        def func():
            return (
                v1
                +
                v2
                +
                v3
                +
                v4
                +
                v5
                +
                v6
            )

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (2, 'func', 'line'),
            (4, 'func', 'line'),
            (2, 'func', 'line'),
            (6, 'func', 'line'),
            (2, 'func', 'line'),
            (8, 'func', 'line'),
            (2, 'func', 'line'),
            (10, 'func', 'line'),
            (2, 'func', 'line'),
            (12, 'func', 'line'),
            (2, 'func', 'line'),
            (1, 'func', 'line'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_14_multiline_boolop(self):
        b1 = False
        b2 = False
        b3 = False
        b4 = False
        b5 = False
        b6 = True

        def func():
            return (
                    b1
                    or
                    b2
                    or
                    b3
                    or
                    b4
                    or
                    b5
                    or
                    b6
            )

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (2, 'func', 'line'),
            (4, 'func', 'line'),
            (2, 'func', 'line'),
            (6, 'func', 'line'),
            (2, 'func', 'line'),
            (8, 'func', 'line'),
            (2, 'func', 'line'),
            (10, 'func', 'line'),
            (2, 'func', 'line'),
            (12, 'func', 'line'),
            (1, 'func', 'line'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_15_multiline_boolop_short(self):
        b1 = False
        b2 = False
        b3 = False
        b4 = True
        b5 = False
        b6 = True

        def func():
            return (
                    b1
                    or
                    b2
                    or
                    b3
                    or
                    b4
                    or
                    b5
                    or
                    b6
            )

        self.first_line = func.__code__.co_firstlineno
        self.events = []
        sys.settrace(self.trace)
        func()
        sys.settrace(None)

        events = [
            (0, 'func', 'call'),
            (2, 'func', 'line'),
            (4, 'func', 'line'),
            (2, 'func', 'line'),
            (6, 'func', 'line'),
            (2, 'func', 'line'),
            (8, 'func', 'line'),
            (2, 'func', 'line'),
            (1, 'func', 'line'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

class MultilineCallsTraceTest(TracingEventsUnitTest):
    class A:
        def m_basic(self, a1, a2, a3):
            return a1 + a2 + a3

        def m_varargs(self, a1, a2, *args):
            return a1 + a2 + len(args)

    a = A()

    def test_01_multiline_call_method_basic(self):
        def func():
            return self.a.m_basic(
                1,
                2,
                3,
            )

        method_first_line = self.trace_function_with_offset(func, self.a.m_basic)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (1, 'func', 'line'),
            (method_first_line, 'm_basic', 'call'),
            (method_first_line + 1, 'm_basic', 'line'),
            (method_first_line + 1, 'm_basic', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_02_multiline_call_method_vargs(self):
        def func():
            return self.a.m_varargs(
                1,
                2,
                3,
                4,
                5,
            )

        method_first_line = self.trace_function_with_offset(func, self.a.m_varargs)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (6, 'func', 'line'),
            (1, 'func', 'line'),
            (method_first_line, 'm_varargs', 'call'),
            (method_first_line + 1, 'm_varargs', 'line'),
            (method_first_line + 1, 'm_varargs', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_03_multiline_call_function_basic(self):
        def f_basic(a1, a2, a3):
            return a1 + a2 + a3

        def func():
            return f_basic(
                1,
                2,
                3,
            )

        func_first_line = self.trace_function_with_offset(func, f_basic)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (1, 'func', 'line'),
            (func_first_line, 'f_basic', 'call'),
            (func_first_line + 1, 'f_basic', 'line'),
            (func_first_line + 1, 'f_basic', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_04_multiline_call_function_vargs(self):
        def f_varargs(a1, a2, *args):
            return a1 + a2 + len(args)

        def func():
            return f_varargs(
                1,
                2,
                3,
                4,
                5
            )

        func_first_line = self.trace_function_with_offset(func, f_varargs)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (6, 'func', 'line'),
            (1, 'func', 'line'),
            (func_first_line, 'f_varargs', 'call'),
            (func_first_line + 1, 'f_varargs', 'line'),
            (func_first_line + 1, 'f_varargs', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

    def test_05_multiline_call_function_fallback_to_vargs(self):
        def f_varargs_fallback(a1, a2, a3, a4, a5, a6):
            return a1 + a2 + a3 + a4 + a5 + a6

        def func():
            return f_varargs_fallback(
                1,
                2,
                3,
                4,
                5,
                6
            )

        func_first_line = self.trace_function_with_offset(func, f_varargs_fallback)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (1, 'func', 'line'),
            (func_first_line, 'f_varargs_fallback', 'call'),
            (func_first_line + 1, 'f_varargs_fallback', 'line'),
            (func_first_line + 1, 'f_varargs_fallback', 'return'),
            (1, 'func', 'return'),
        ]

        self.assert_events(self.events, events)

class ExceptStarTraceTest(TracingEventsUnitTest):
    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_01_except_star_with_name(self):
        def func():
            try:
                try:
                    raise ExceptionGroup("eg", [ValueError(1)])
                except* ValueError as ve:
                    raise
                    x = "Something"
                    y = "Something"
            except ExceptionGroup:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (8, 'func', 'line'),
            (9, 'func', 'line'),
            (9, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_02_except_star_multi_with_name(self):
        def func():
            try:
                try:
                    raise ExceptionGroup("eg", [ValueError(1), TypeError(2)])
                except* ValueError as ve:
                    pass
                except* TypeError as te:
                    raise
                    x = "Something"
                    y = "Something"
                except* IndexError as ie:
                    pass
            except ExceptionGroup:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (10, 'func', 'line'),
            (12, 'func', 'line'),
            (13, 'func', 'line'),
            (13, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    @util.skipIfBytecodeDSL("TODO: Fix return in finally.")
    def test_03_except_star_with_finally(self):
        def func():
            try:
                try:
                    try:
                        raise ExceptionGroup("eg", [ValueError(1), TypeError(2)])
                    finally:
                        y = "Something"
                except* ValueError:
                    b = 23
            except ExceptionGroup:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (4, 'func', 'exception'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (8, 'func', 'line'),
            (9, 'func', 'line'),
            (10, 'func', 'line'),
            (10, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    @util.skipIfBytecodeDSL("TODO: Fix return in finally.")
    def test_04_test_try_except_star_with_wrong_type(self):
        def func():
            try:
                try:
                    raise ExceptionGroup("eg", [ValueError(1)])
                except IndexError:
                    4
                finally:
                    return 6
            except ExceptionGroup:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (7, 'func', 'line'),
            (7, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_05_if_false_in_try_except_star(self):
        def func():
            try:
                if False:
                    pass
            except* ValueError:
                X

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (2, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    @unittest.skipIf(sys.implementation.name == "cpython", "TODO: seems broken on CPython")
    def test_06_try_in_try_with_exception(self):
        def func():
            try:
                try:
                    try:
                        raise ExceptionGroup("eg", [TypeError(1)])
                    except* ValueError as ex:
                        5
                except* TypeError:
                    7
            except ExceptionGroup:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (4, 'func', 'exception'),
            (5, 'func', 'line'),
            (7, 'func', 'line'),
            (8, 'func', 'line'),
            (8, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @unittest.skip("TODO: Isn't even tagged from CPython tests.")
    def test_07_tracing_exception_raised_in_with(self):
        class NullCtx:
            def __enter__(self):
                return self
            def __exit__(self, *excinfo):
                pass

        def func():
            try:
                with NullCtx():
                    raise ExceptionGroup("eg", [TypeError(1)])
            except* TypeError:
                pass

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (-5, '__enter__', 'call'),
            (-4, '__enter__', 'line'),
            (-4, '__enter__', 'return'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (2, 'func', 'line'),
            (-3, '__exit__', 'call'),
            (-2, '__exit__', 'line'),
            (-2, '__exit__', 'return'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (5, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_08_try_except_star_no_exception(self):
        def func():
            try:
                2
            except* Exception:
                4
            else:
                6
                if False:
                    8
                else:
                    10
                if func.__name__ == 'Fred':
                    12
            finally:
                14

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (10, 'func', 'line'),
            (11, 'func', 'line'),
            (14, 'func', 'line'),
            (14, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_09_try_except_star_named_no_exception(self):
        def func():
            try:
                2
            except* Exception as e:
                4
            else:
                6
            finally:
                8

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (6, 'func', 'line'),
            (8, 'func', 'line'),
            (8, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_10_try_except_star_exception_caught(self):
        def func():
            try:
                raise ValueError(2)
            except* ValueError:
                4
            else:
                6
            finally:
                8

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (2, 'func', 'exception'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (8, 'func', 'line'),
            (8, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_11_try_except_star_named_exception_caught(self):
        def func():
            try:
                raise ValueError(2)
            except* ValueError as e:
                4
            else:
                6
            finally:
                8

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (2, 'func', 'exception'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (8, 'func', 'line'),
            (8, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_12_try_except_star_exception_not_caught(self):
        def func():
            try:
                try:
                    raise ValueError(3)
                except* TypeError:
                    5
            except ValueError:
                7

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (7, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_13_try_except_star_named_exception_not_caught(self):
        def func():
            try:
                try:
                    raise ValueError(3)
                except* TypeError as e:
                    5
            except ValueError:
                7

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (4, 'func', 'line'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (7, 'func', 'return')
        ]

        self.assert_events(self.events, events)

    @util.skipUnlessBytecodeDSL("try-except* not implemented")
    def test_14_try_except_star_nested(self):
        def func():
            try:
                try:
                    raise ExceptionGroup(
                        'eg',
                        [ValueError(5), TypeError('bad type')])
                except* TypeError as e:
                    7
                except* OSError:
                    9
                except* ValueError:
                    raise
            except* ValueError:
                try:
                    raise TypeError(14)
                except* OSError:
                    16
                except* TypeError as e:
                    18
            return 0

        self.trace_function(func)

        events = [
            (0, 'func', 'call'),
            (1, 'func', 'line'),
            (2, 'func', 'line'),
            (3, 'func', 'line'),
            (4, 'func', 'line'),
            (5, 'func', 'line'),
            (3, 'func', 'line'),
            (3, 'func', 'exception'),
            (6, 'func', 'line'),
            (7, 'func', 'line'),
            (8, 'func', 'line'),
            (10, 'func', 'line'),
            (11, 'func', 'line'),
            (12, 'func', 'line'),
            (13, 'func', 'line'),
            (14, 'func', 'line'),
            (14, 'func', 'exception'),
            (15, 'func', 'line'),
            (17, 'func', 'line'),
            (18, 'func', 'line'),
            (19, 'func', 'line'),
            (19, 'func', 'return')
        ]

        self.assert_events(self.events, events)
