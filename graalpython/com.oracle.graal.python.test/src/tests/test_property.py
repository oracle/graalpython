# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

class C(object):
    def __init__(self, x):
        self._x = x

    def getx(self):
        return self._x

    def setx(self, value):
        self._x = value

    def delx(self):
        del self._x

    prop_x = property(getx, setx, delx, "I'm the 'x' property.")


# decorator style
class D(object):
    def __init__(self, x):
        self._x = x

    @property
    def prop_x(self):
        "I am the 'x' property."
        return self._x

    @prop_x.setter
    def prop_x(self, value):
        self._x = value

    @prop_x.deleter
    def prop_x(self):
        del self._x


class X(object):
    @property
    def prop_x(self):
        return self._prop_x

    @prop_x.setter
    def prop_x(self, new_val):
        self._prop_x = new_val
        return new_val

class Y(X):
    @X.prop_x.setter
    def prop_x(self, ax):
        X.prop_x.fset(self, ax)


ERROR_FROM_GETTER = "ERROR FROM GETTER"
class Z:
    @property
    def prop_x(self):
        raise AttributeError(ERROR_FROM_GETTER)


def test_properties():
    c = C(10)
    assert c.prop_x == 10
    c.prop_x = 20
    assert c.prop_x == 20
    del c.prop_x
    not_found = False
    try:
        c.prop_x
    except AttributeError:
        not_found = True
    assert not_found

    d = D(10)
    assert d.prop_x == 10
    d.prop_x = 20
    assert d.prop_x == 20
    del d.prop_x
    not_found = False
    try:
        d.prop_x
    except AttributeError:
        not_found = True
    assert not_found

    assert X.prop_x is not Y.prop_x


def test_property_error():
    try:
        Z().prop_x
    except BaseException as e:
        assert isinstance(e, AttributeError), "did not get AttributeError, was %s" % type(e)
        assert str(e) == ERROR_FROM_GETTER, "did not get expected error message; was %s" % str(e)
