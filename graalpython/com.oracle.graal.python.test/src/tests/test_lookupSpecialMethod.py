# Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import math
import unittest

class SpecialMethodTest(unittest.TestCase):
            
    def test_special(self):
        def fun_float(arg):
            return 4.2
        def fun_int(arg):
            return 42
        def fun_int2(arg1, arg2):
            return 42
        def fun_complex(arg):
            return (4+2j)
        def fun_str(arg):
            return '42'
        def fun_str2(arg1, arg2):
            return '42'
        def fun_bool(arg):
            return True
        def fun_bytes(arg):
            return b'42'       
        def fun_empty_seq(arg):
            return []        
        def fun_seq(arg):
            return [42]        
        specials = [
            ("__bytes__", bytes, 1, 1.1, "b'abc'", fun_bytes, True, []),
            ("__dir__", dir,  1, 1.1, 'abc', fun_empty_seq, True, []),
            ("__round__", round, 1, 1.1, 'abc', fun_int2, True, [1]),
            ("__complex__", complex, 1, 1.1, '1+1j', fun_complex, True, []),
            ("__float__", float, 1, 1.1, '1.1', fun_float, True, []),
            ("__int__", int, 1, 1.1, '1', fun_int, True, []),
            ("__bool__", bool, 1, 1.1, 'True', fun_bool, True, []),            
            ("__str__", str, 1, 1.1, 'abc', fun_str, True, []),                                      
            ("__add__", None, 1, 1.1, '1', fun_int2, False, [1]),
            ("__sub__", None, 1, 1.1, '1', fun_int2, False, [1]),
            ("__mul__", None, 1, 1.1, '1', fun_int2, False, [1]),
            ("__truediv__", None, 1, 1.1, '1', fun_int2, False, [1]),
            ("__floordiv__", None, 1, 1.1, '1', fun_int2, False, [1]),
            ("__abs__", abs, -1, -1.1, '-1', fun_int, False, []),
            ("__hash__", hash, 1, 1.1, '1', fun_int, False, []),
            ("__divmod__", divmod, 1, 1.1, '1', fun_int2, False, [1]),
            ("__len__", len, 1, 1.1, '1', fun_int, False, []),
            ("__pow__", pow, 1, 1.1, '1', fun_int2, False, [1]),            
            ("__repr__", repr, 1, 1.1, 'abc', fun_str, False, []),            
            # TODO: fix __format__, __floor__, __trunc__, __ceil__, __sizeof__
            # recursion when special method set to constructor builtin
            # >>> setattr(C, "__format__", format)
            # >>> format(C())
#            ("__sizeof__", sys.getsizeof, 1, 1.1, 'abc', fun_int, False, []),
            ("__format__", format, 1, 1.1, 'abc', fun_str2, False, ['']),
            ("__floor__", math.floor, 1, 1.1, 'abc', fun_int, False, []),
            ("__trunc__", math.trunc, 1, 1.1, 'abc', fun_int, False, []),
            ("__ceil__", math.ceil, 1, 1.1, 'abc', fun_int, False, []),
            ]
        for name, runner, int_repr, float_repr, str_repr, meth_impl, check_with_runner_set, additional_args in specials:
            def check_method_as_attr(X, repr):
                setattr(X, name, meth_impl)
                x = X(repr)
                if(name == "__add__"):                
                    rx = x + 1          
                    mx = meth_impl(x, *additional_args)          
                elif(name == "__sub__"):                
                    rx = x - 1
                    mx = meth_impl(x, *additional_args)
                elif(name == "__mul__"):                
                    rx = x * 1
                    mx = meth_impl(x, *additional_args)
                elif(name == "__truediv__"):                
                    rx = x / 1
                    mx = meth_impl(x, *additional_args)
                elif(name == "__floordiv__"):                
                    rx = x // 1
                    mx = meth_impl(x, *additional_args)                    
                else:
                    rx = runner(x, *additional_args)
                    if name == "__complex__" and type(repr) == str:
                        # str subtypes behave different
                        mx = complex(repr)
                    else:    
                        mx = meth_impl(x, *additional_args)                
                            
                if mx != rx:
                    raise AssertionError("expected runner '" + str(runner) + "' and method '" + str(meth_impl) + "' result to be the same for '" + name + "' : runner = " + str(rx) + ", method = " + str(mx))
                    
            def check_runner_as_attr(X, repr):
                setattr(X, name, runner)
                x = X(repr)
                # just check that no error is raised
                runner(x, *additional_args)                
                        
            class XI(int):
                pass
            
            class XF(float):
                pass
            
            class XS(str):
                pass                        
            
            check_method_as_attr(XI, int_repr)
            check_method_as_attr(XF, float_repr)
            check_method_as_attr(XS, str_repr)
            
            if check_with_runner_set:
                check_runner_as_attr(XI, int_repr)
                check_runner_as_attr(XF, float_repr)
                check_runner_as_attr(XS, str_repr)
                
    # modified version of test_descr.py#test_special_method_lookup() - added more special methods to the test
    def test_special2(self):
        def run_context(manager):
            with manager:
                pass
        def iden(self):
            return self
        def hello(self):
            return b"hello"
        def fun_empty_seq(self):
            return []
        def zero(self):
            return 0        
        def fint2(a1, a2):
            return 42                
        def ffloat(self):
            return 4.2                   
        def fstr(self):
            return '42'
        def complex_num(self):
            return 1j
        def stop(self):
            raise StopIteration
        def return_true(self, thing=None):
            return True
        def do_isinstance(obj):
            return isinstance(int, obj)
        def do_issubclass(obj):
            return issubclass(int, obj)
        def do_dict_missing(checker):
            class DictSub(checker.__class__, dict):
                pass
            self.assertEqual(DictSub()["hi"], 4)
        def some_number(self_, key):
            self.assertEqual(key, "hi")
            return 4
        def swallow(*args): pass
        def format_impl(self, spec):
            return "hello"
        specials = [
            ("__bytes__", bytes, hello, set(), {}),
            ("__reversed__", reversed, fun_empty_seq, set(), {}),
            ("__length_hint__", list, zero, set(), {"__iter__" : iden, "__next__" : stop}),
            ("__sizeof__", sys.getsizeof, zero, set(), {}),
            ("__instancecheck__", do_isinstance, return_true, set(), {}),
            ("__missing__", do_dict_missing, some_number, set(("__class__",)), {}),
            ("__subclasscheck__", do_issubclass, return_true, set(("__bases__",)), {}),
            ("__enter__", run_context, iden, set(), {"__exit__" : swallow}),
            ("__exit__", run_context, swallow, set(), {"__enter__" : iden}),
            ("__complex__", complex, complex_num, set(), {}),
            ("__format__", format, format_impl, set(), {}),
            ("__floor__", math.floor, zero, set(), {}),
            ("__trunc__", math.trunc, zero, set(), {}),
            ("__trunc__", int, zero, set(), {}),
            ("__ceil__", math.ceil, zero, set(), {}),
            ("__dir__", dir, fun_empty_seq, set(), {}),
            ("__round__", round, zero, set(), {}),
            # ----------------------------------------------------
            ("__float__", float, ffloat, set(), {}),
            ("__int__", int, zero, set(), {}),
            ("__bool__", bool, return_true, set(), {}),            
            ("__str__", str, fstr, set(), {}),
            ("__abs__", abs, zero, set(), {}),
            ("__hash__", hash, zero, set(), {}),
            ("__divmod__", divmod, fint2, set(), {}),
            ("__len__", len, zero, set(), {}),
            ("__pow__", pow, fint2, set(), {}),            
            ("__repr__", repr, fstr, set(), {}),            
            ("__add__", None, fint2, set(), {}),
            ("__sub__", None, fint2, set(), {}),
            ("__mul__", None, fint2, set(), {}),
            ("__truediv__", None, fint2, set(), {}),
            ("__floordiv__", None, fint2, set(), {}),
            ]
        class SpecialDescr(object):
            def __init__(self, impl):
                self.impl = impl
            def __get__(self, obj, owner):
                record.append(1)
                return self.impl.__get__(obj, owner)
        class MyException(Exception):
            pass
        class ErrDescr(object):
            def __get__(self, obj, owner):
                raise MyException
        for name, runner, meth_impl, ok, env in specials:
            class X:
                pass
            for attr, obj in env.items():
                setattr(X, attr, obj)
            setattr(X, name, meth_impl)
            if name == "__divmod__" or name == "__pow__":
                runner(X(), 1)   
            elif name == "__add__":
                X() + 1
            elif name == "__mul__":
                X() * 1    
            elif name == "__truediv__":
                X() / 1
            elif name == "__floordiv__":
                X() // 1                
            elif name == "__sub__":
                X() - 1                
            else:
                runner(X())   
            record = []
            class X:
                pass
            for attr, obj in env.items():
                setattr(X, attr, obj)
            setattr(X, name, SpecialDescr(meth_impl))
            if name == "__divmod__" or name == "__pow__":
                runner(X(), 1)   
            elif name == "__add__":
                X() + 1              
            elif name == "__mul__":
                X() * 1    
            elif name == "__truediv__":
                X() / 1
            elif name == "__floordiv__":
                X() // 1                
            elif name == "__sub__":
                X() - 1                    
            else:
                runner(X())
            self.assertEqual(record, [1], name)
            class X:
                pass
            for attr, obj in env.items():
                setattr(X, attr, obj)
            setattr(X, name, ErrDescr())
            if name == "__divmod__" or name == "__pow__":
                self.assertRaises(MyException, runner, X(), 1)
            elif name == "__add__":
                def fe(): X() + 1
                self.assertRaises(MyException, fe)                
            elif name == "__mul__":
                def fe(): X() * 1  
                self.assertRaises(MyException, fe)                  
            elif name == "__truediv__":
                def fe(): X() / 1
                self.assertRaises(MyException, fe)                
            elif name == "__floordiv__":
                def fe(): X() // 1
                self.assertRaises(MyException, fe)                     
            elif name == "__sub__":
                def fe(): X() - 1                  
                self.assertRaises(MyException, fe)                
            elif name == "__repr__":
                runner(X())
            elif name == "__hash__":
                # XXX just skip, does not work in cpython either
                pass
            else:
                self.assertRaises(MyException, runner, X())            

            
    def test_reversed(self):            
        class C(list): 
            pass
        
        def f(o): 
            return [42]
            
        setattr(C, "__reversed__", f)
        assert list(reversed(C([1,2,3]))) == [42]
        
        setattr(C, "__reversed__", reversed)
        try:
            reversed(C([1,2,3]))
        except TypeError:    
            pass
