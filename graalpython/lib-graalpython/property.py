# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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


class property(object):
    """
    property(fget=None, fset=None, fdel=None, doc=None) -> property attribute

     fget is a function to be used for getting an attribute value, and likewise
     fset is a function for setting, and fdel a function for del'ing, an
     attribute.  Typical use is to define a managed attribute x:

     class C(object):
         def getx(self): return self._x
         def setx(self, value): self._x = value
         def delx(self): del self._x
         x = property(getx, setx, delx, "I'm the 'x' property.")

     Decorators make defining new properties or modifying existing ones easy:

     class C(object):
         @property
         def x(self):
             "I am the 'x' property."
             return self._x
         @x.setter
         def x(self, value):
             self._x = value
         @x.deleter
         def x(self):
             del self._x

    """
    def __init__(self, fget=None, fset=None, fdel=None, doc=None, name=None):
        self.fget = fget
        self.fset = fset
        self.__delete = fdel
        self.doc = doc
        self.name = name
        self._owner = None

    def __get__(self, instance, owner):
        if self._owner is None:
            self._owner = owner
        if instance is None:
            return self
        if self.fget is None:
            raise AttributeError("unreadable attribute")
        return self.fget(instance)

    def __set__(self, instance, value):
        if self.fset is None:
            raise AttributeError("attribute '{}' of '{}' objects is not writable".format(
                self.name, getattr(self._owner, "__name__", str(self._owner))))
        return self.fset(instance, value)

    def __delete__(self, instance):
        if self.__delete is None:
            raise AttributeError("can't delete attribute")
        return self.__delete(instance)

    def setter(self, func):
        self.fset = func
        return self

    def deleter(self, func):
        self.__delete = func
        return self

    def getter(self, func):
        self.fget = func
        return self

    def __repr__(self):
        return "'".join([
            "<property ",
            str(self.name),
            " of ",
            getattr(self._owner, "__name__", str(self._owner)),
            " objects>"
        ])
