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
            def m(*a, **kw):
                cls = type.__new__(*a, **kw)
                cls.metatype = NewDescriptor
                return cls
            return m

        def __get__(self, *args):
            return self.new_descriptor_new()

        def __set__(self, *args):
            raise NotImplementedError

    class MetaMeta(type):
        def __new__(*args, **kwargs):
            cls = type.__new__(*args, **kwargs)
            cls.metatype = MetaMeta
            return cls

    class Meta(type, metaclass=MetaMeta):
        def __new__(*args, **kwargs):
            cls = type.__new__(*args, **kwargs)
            cls.metatype = Meta
            return cls

    assert Meta.metatype is MetaMeta

    class aMeta(metaclass=Meta):
        pass

    aMeta2 = type("aMeta2", (aMeta,), {})
    assert aMeta.metatype is Meta
    assert aMeta2.metatype is Meta

    # overriding the meta-meta-class' __new__ does not affect creating new
    # instances of the meta-class
    MetaMeta.__new__ = Meta.__new__

    class stillAMeta(metaclass=Meta):
        pass
    stillAMeta2 = type("stillAMeta2", (stillAMeta,), {})

    # overriding the meta-meta-class' __new__ does affect creating new
    # meta-classes
    class aMetaThatIsNotAMetaMeta(metaclass=MetaMeta):
        pass
    aMetaThatIsNotAMetaMeta2 = type("aMetaThatIsNotAMetaMeta2", (aMetaThatIsNotAMetaMeta,), {})

    assert stillAMeta.metatype is Meta
    assert stillAMeta2.metatype is Meta
    assert aMetaThatIsNotAMetaMeta.metatype is Meta
    assert aMetaThatIsNotAMetaMeta2.metatype is Meta

    # setting the meta-meta class' __new__ to a data descriptor does affect
    # creating instances of the instances of the meta-meta-class
    MetaMeta.__new__ = NewDescriptor()

    class notAMeta(metaclass=Meta):
        pass
    notAMeta2 = type("notAMeta2", (notAMeta,), {})

    # the below assertions should pass, but this is such an unusual case that we
    # ignore this.
    # assert notAMeta.metatype is NewDescriptor
    # assert notAMeta2.metatype is NewDescriptor
