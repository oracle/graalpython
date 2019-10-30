# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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



def test_module_without():
    import annotations.noannotations as without
    raised = False
    try:
        without.__annotations__
    except AttributeError:
        raised = True
    assert raised

def test_module_with():
    import annotations.withannotations as m
    raised = False
    try:
        a = m.__annotations__
    except AttributeError:
        assert False

    assert int == a['j']
    assert len(a) == 1


def test_function_withouth():
    import annotations.annotatedFunctions as m
    
    assert hasattr(m, '__annotations__') == False

    assert len(m.noAnnotation1.__annotations__) == 0
    assert len(m.noAnnotation2.__annotations__) == 0
    assert len(m.noAnnotation3.__annotations__) == 0
    assert len(m.noAnnotation4.__annotations__) == 0
    
def test_function_with():
    import annotations.annotatedFunctions as m

    assert len(m.withAnnotation1.__annotations__) == 1
    assert int == m.withAnnotation1.__annotations__['j']

    assert len(m.withAnnotation2.__annotations__) == 2
    assert int == m.withAnnotation2.__annotations__['j']
    assert str == m.withAnnotation2.__annotations__['k']

    assert len(m.withAnnotation3.__annotations__) == 3
    assert int == m.withAnnotation3.__annotations__['j']
    assert str == m.withAnnotation3.__annotations__['k']
    assert object == m.withAnnotation3.__annotations__['o']

    assert hasattr(m, '__annotations__') == False
    #m.withAnnotation3(1, 2);
    assert hasattr(m, '__annotations__') == False
    assert len(m.withAnnotation3.__annotations__) == 3

def test_wrongTypeInFunction():
    import annotations.annotatedFunctions as m

    assert m.wrongTypeInside() == 0
    assert len(m.wrongTypeInside.__annotations__) == 0


def test_class_without():
    import annotations.annotatedClasses as m
    assert hasattr(m, '__annotations__') == False

    assert hasattr(m.NoAnn, '__annotations__') == False


def test_decoratedMethod():
    import annotations.annotatedClasses as m
    
    assert hasattr(m.DecoratedMethodClass, '__annotations__') == False

    assert len(m.DecoratedMethodClass.method.__annotations__) == 1, "__annotations__ attribute was not found"
    assert int == m.DecoratedMethodClass.method.__annotations__['index']

def test_decoratedClassMethod():
    import annotations.annotatedClasses as m

    assert hasattr(m.ClsWithClassMethod, '__annotations__') == False

    assert len(m.ClsWithClassMethod.method.__annotations__) == 1, "__annotations__ attribute was not found"
    assert str == m.ClsWithClassMethod.method.__annotations__['string']

def test_addAnnotation():
    import annotations.annotatedFunctions as m

    assert hasattr(m.addAnnotation, '__annotations__') == True
    assert len(m.addAnnotation.__annotations__) == 1
    assert str == m.addAnnotation.__annotations__['key']
    m.addAnnotation('myKey', 10)
    assert len(m.addAnnotation.__annotations__) == 2
    assert 10 == m.addAnnotation.__annotations__['myKey']
