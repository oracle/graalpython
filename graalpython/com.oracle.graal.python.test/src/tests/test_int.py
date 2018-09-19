# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

class _NamedIntConstant(int):
    def __new__(cls, value, name):
        self = super(_NamedIntConstant, cls).__new__(cls, value)
        self.name = name
        return self

    def __str__(self):
        return self.name

    __repr__ = __str__


def test_int_subclassing():
    MAXREPEAT = _NamedIntConstant(1, 'MAXREPEAT')
    assert MAXREPEAT == 1
    assert str(MAXREPEAT) == "MAXREPEAT"
    

def test_boolean2int():
    assert int(True) == 1
    assert int(False) == 0
    
    
def test_int_from_custom():
    class CustomInt4():
        def __int__(self):
            return 1
    
    class CustomInt8():
        def __int__(self):
            return 0xCAFEBABECAFED00D
        
    class SubInt(int):
        def __int__(self):
            return 0xBADF00D
        
    class NoInt():
        pass
        
    assert int(CustomInt4()) == 1
    assert int(CustomInt8()) == 0xCAFEBABECAFED00D
    assert CustomInt8() != 0xCAFEBABECAFED00D
    assert int(SubInt()) == 0xBADF00D
    assert SubInt() == 0
    try:
        int(NoInt())
        assert False, "converting non-integer to integer must not be possible"
    except BaseException as e:
        assert type(e) == TypeError, "expected type error, was: %r" % type(e)

def test_int_bit_length():
    assert (int(0)).bit_length() == 0
    assert (int(1)).bit_length() == 1
    assert (int(-1)).bit_length() == 1
    assert (int(255)).bit_length() == 8
    assert (int(-255)).bit_length() == 8
    assert (int(6227020800)).bit_length() == 33
    assert (int(-6227020800)).bit_length() == 33
    assert (int(2432902008176640000)).bit_length() == 62
    assert (int(-2432902008176640000)).bit_length() == 62
    assert (int(9999992432902008176640000999999)).bit_length() == 103
    assert (int(-9999992432902008176640000999999)).bit_length() == 103

class MyInt(int):
    pass

def test_int():
    def builtinTest(number):
        a = int(number)
        b = a.__int__()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)
    
    assert True.__int__() == 1
    assert False.__int__() == 0


def test_int_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.__int__()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_real_imag():
    def builtinTest(number):
        a = int(number)
        b = a.real
        c = a.imag
        assert a == b
        assert a is b
        assert c == 0
        assert type(a) == int
        assert type(b) == int
        assert type(c) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)
    
    assert True.real == 1
    assert False.real == 0
    assert True.imag == 0
    assert False.imag == 0

def test_real_imag_subclass():    
    def subclassTest(number):
        a = MyInt(number)
        b = a.real
        c = a.imag
        assert a == b
        assert a is not b
        assert c == 0
        assert type(a) == MyInt
        assert type(b) == int
        assert type(c) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_numerator_denominator():
    def builtinTest(number):
        a = int(number)
        b = a.numerator
        c = a.denominator
        assert a == b
        assert a is b
        assert c == 1
        assert type(a) == int
        assert type(b) == int
        assert type(c) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)
    
    assert True.numerator == 1
    assert False.numerator == 0
    assert True.denominator == 1
    assert False.denominator == 1

def test_mumerator_denominator_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.numerator
        c = a.denominator
        assert a == b
        assert a is not b
        assert c == 1
        assert type(a) == MyInt
        assert type(b) == int
        assert type(c) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_conjugate():
    def builtinTest(number):
        a = int(number)
        b = a.conjugate()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)
    
    assert True.conjugate() == 1
    assert False.conjugate() == 0

def test_conjugate_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.conjugate()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

class MyTrunc:
    def __trunc__(self):
        return 1972
class MyIntTrunc:
    def __trunc__(self):
        return 1972
    def __int__(self):
        return 66

def test_trunc():
    def builtinTest(number):
        a = int(number)
        b = a.__trunc__()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)
    
    assert True.__trunc__() == 1
    assert False.__trunc__() == 0

    assert int(MyTrunc()) == 1972
    assert int(MyIntTrunc()) == 66

def test_trunc_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.__trunc__()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

    assert MyInt(MyTrunc()) == 1972
    assert MyInt(MyIntTrunc()) == 66

def test_create_int_from_bool():

    class SpecInt1:
        def __int__(self):
            return True

    class SpecInt0:
        def __int__(self):
            return False

    assert int(SpecInt1()) == 1
    assert int(SpecInt0()) == 0

def test_create_int_from_string():
  assert int("5c7920a80f5261a2e5322163c79b71a25a41f414", 16) == 527928385865769069253929759180846776123316630548
