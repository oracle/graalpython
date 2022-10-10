# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import unittest
import difflib
import sys
import signal

import builtins

# only while the bytecode interpreter is not the default
skip = not getattr(getattr(builtins, '__graalpython__', None), 'uses_bytecode_interpreter', True)

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

@unittest.skipIf(skip, 'sys.settrace only works in the bytecode interpreter')
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
    test_03_oneline_loop = make_test_method(oneline_loop, 'test_03_oneline_loop')
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
