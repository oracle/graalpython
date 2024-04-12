# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# if and elif


def test_if():
    sum = 0
    for i in range(10):
        if i == 0:
            sum += 10
        elif i < 3:
            sum += 2
        elif i < 7:
            sum += 5
        else:
            sum -= 1

    assert sum == 31


def test_no_long():
    longval = 0xFFFFFFFFFFFFFFFFFF & 0
    if not longval:
        assert True
    else:
        assert False


def test_yes_long():
    longval = 0xFFFFFFFFFFFFFFFFFF & 1
    if longval:
        assert True
    else:
        assert False


def test_yes_container():
    assert not[], "empty list must yield False"
    assert not dict(), "empty dict must yield False"
    assert not set(), "empty set must yield False"
    assert not tuple(), "empty tuple must yield False"

    assert [1], "non-empty list must yield True"
    assert {'a': 42}, "non-empty dict must yield True"
    assert {1,2,3}, "non-empty set must yield True"
    assert (1,2,3), "non-empty tuple must yield True"

    import collections
    assert not collections.deque(), "empty deque must yield False"
    assert collections.deque([1,2,3]), "non-empty deque must yield True"

    class CustomDummyDefault:
        pass

    class CustomEmptyContainer:

        def __len__(self):
            return 0

        def __bool__(self):
            return False

    class CustomContainerInvalid:

        def __len__(self):
            return "hello"

    class CustomBoolableTrue:

        def __bool__(self):
            return True

    class CustomBoolableFalse:

        def __bool__(self):
            return False

    class CustomBoolableRecursiveInvalid:
        def __bool__(self):
            class MyRecursiveBool:
                def __bool__(self): return False
            return MyRecursiveBool()

    class CustomEmptyContainerWithIndex:
        def __len__(self):
            class MyIndex:
                def __index__(self): return 0
            return MyIndex()

    assert CustomDummyDefault(), "custom empty class must yield True"
    assert not CustomEmptyContainer(), "custom empty container must yield False"
    assert CustomBoolableTrue(), "custom boolable true container must yield True"
    assert not CustomBoolableFalse(), "custom boolable false container must yield False"
    assert not CustomEmptyContainerWithIndex(), "custom empty container with __index__ must yield False"

    try:
        assert not CustomContainerInvalid(), "custom container invalid must yield False"
    except TypeError as e:
        assert True
    else:
        assert False, "exception expected"

    try:
        assert not CustomBoolableRecursiveInvalid(), "custom boolable invalid must yield False"
    except TypeError as e:
        assert True
    else:
        assert False, "exception expected"


def test_descriptor():
    class MyDescr:
        def __init__(self, r):
            self.r = r
        def __get__(self, obj, owner):
            return lambda: self.r

    class DescrTrue:
        __bool__ = MyDescr(True)

    class DescrFalse:
        __bool__ = MyDescr(False)

    class DescrLenFalse:
        __len__ = MyDescr(0)

    assert DescrTrue()
    assert not DescrFalse()
    assert not DescrLenFalse()

def test_dunder_method():
    assert (42).__bool__(), "42.__bool__() should give True"
    assert not (0).__bool__(), "0.__bool__() should give False"
    assert (True).__bool__(), "True.__bool__() should give True"
    assert not (False).__bool__(), "False.__bool__() should give False"

    class CustomBoolableFalse:
        def __bool__(self):
            return False
    assert not CustomBoolableFalse().__bool__(), "CustomBoolableFalse.__bool__() should give False"
