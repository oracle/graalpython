import unittest
import sys


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


# there are 5 line events on line 3, since there is the first check of the loop flag, then 4 backward jumps, last one ending the loop

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

def make_test_method(fun, name):
    def test_case(self):
        for args, events in fun.events:
            try:
                self.events=[]
                self.first_line=fun.__code__.co_firstlineno
                sys.settrace(self.trace)
                fun(*args)
            finally:
                sys.settrace(None)
            self.assertEqual(self.events, events, [args, self.events])

    test_case.__name__ = name
    return test_case

class TraceTests(unittest.TestCase):
    def trace(self, frame, event, arg):
        code = frame.f_code
        name = code.co_name
        self.events.append((frame.f_lineno - self.first_line, name, event, arg))
        return self.trace
    test_01_basic = make_test_method(basic, 'test_01_basic')
    test_02_more_complex = make_test_method(more_complex, 'test_02_more_complex')
    test_03_oneline_loop = make_test_method(oneline_loop, 'test_03_oneline_loop')
    test_04_two_functions = make_test_method(two_functions, 'test_04_two_functions')
