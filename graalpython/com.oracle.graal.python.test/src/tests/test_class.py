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

def test_special_descriptor():
    class A:
        @property
        def __eq__(self):
            def inner(other):
                return True
            return inner

    assert A() == A()


def test_new_not_descriptor():
    class C:
        __new__ = str
    assert C() == str(C)


def test_meta_meta_new():
    class NewDescriptor():
        def new_descriptor_new(self):
            return lambda *a, **kw: ("a kind of NewDescriptor thing", a, kw)

        def __get__(self, *args):
            return self.new_descriptor_new()

        def __set__(self, *args):
            raise NotImplementedError

    class MetaMeta(type):
        pass

    class Meta(type, metaclass=MetaMeta):
        def __new__(*args, **kwargs):
            cls = super().__new__(*args, **kwargs)
            cls.metatype = Meta
            return cls

    # setup done, now testing

    class aMeta(metaclass=Meta):
        pass

    MetaMeta.__new__ = Meta.__new__

    class stillAMeta(metaclass=Meta):
        pass

    class aMetaThatIsNotAMetaMeta(metaclass=MetaMeta):
        pass

    MetaMeta.__new__ = NewDescriptor()

    class notAMeta(metaclass=Meta):
        pass

    assert aMeta[0] == 'a kind of Meta'
    assert stillAMeta[0] == 'a kind of Meta'
    assert aMetaThatIsNotAMetaMeta[0] == 'a kind of Meta'
    assert notAMeta[0] == 'a kind of NewDescriptor thing'
q
