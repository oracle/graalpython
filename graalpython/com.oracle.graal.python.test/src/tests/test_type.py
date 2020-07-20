# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised

def test_base():
    A = type('A', (), {})
    assert A.__base__ == object

    class B:
        def ham(self):
            return 'ham%d' % self

    class A(object):
        pass

    class B(dict):
        pass

    class C(A, B):
        pass

    assert C.__base__ == B

    #-----------------------------------------------

    class A(object):
        pass

    class B(object):
        pass

    class C(A, B):
        pass

    assert C.__base__ == A

    C = type('C', (B, int), {'spam': lambda self: 'spam%s' % self})
    assert C.__base__ == int

    #-----------------------------------------------

    class A (object): pass

    class BB (A): pass

    class B(BB): pass

    class C (A): 
        __slots__ = ['a']

    class D (B, C): pass

    assert D.__base__ == C

    #-----------------------------------------------

    class A: pass

    class B: pass

    class C(A): pass

    C.__bases__ = (A, B)

    assert C.__bases__ == (A, B)
    assert A.__subclasses__() == [C]
    assert B.__subclasses__() == [C]

    #-----------------------------------------------

    class A: pass

    class B: pass

    class C(A, B): pass

    C.__bases__ == (A, B)
    A.__subclasses__() == [C]
    B.__subclasses__() == [C]

    raised = False
    try:
        C.__bases__ = (int,)
    except TypeError:
        raised = True
    assert raised

    assert C.__bases__ == (A, B)
    assert A.__subclasses__() == [C]
    assert B.__subclasses__() == [C]    

    #-----------------------------------------------

#    class A: pass
#
#    class B: pass
#
#    class C: pass
#
#    raised = False
#    try:
#        C.__bases__ = (A, B)
#    except TypeError:
#        raised = True
#    assert raised
#    assert C.__bases__ == [object]

def test_namespace_with_non_string_keys():
    class MyStr(str):
        pass

    A = type('A', (), {
        MyStr("x"): 42
    })
    assert any(type(k) == MyStr for k in A.__dict__.keys())

def test_mro():
    class M(type):
        def mro(cls):
            assert type.mro(cls) == [cls, A, B, object]
            return [cls, B, A, object]

    class A: pass
    class B: pass
    class C(A, B, metaclass = M): pass

    assert C.__mro__ == (C, B, A, object)
