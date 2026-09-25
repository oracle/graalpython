# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


"""Same workload as object-dir, rejected only by the final namespace-access guard."""


_type_bases = type.__dict__["__bases__"]


class Meta(type):
    @property
    def __bases__(cls):
        # Preserve the hierarchy, but require Python descriptor dispatch.
        # Calling type.__getattribute__(cls, "__bases__") would recurse here.
        return _type_bases.__get__(cls)


class Base(metaclass=Meta):
    pass


class Config(Base):
    pass


# Overlapping class and instance attributes exercise duplicate elimination.
# Use setattr, not a replacement __dict__, to retain DynamicObjectStorage.
for i in range(32):
    setattr(Base, "attr_%02d" % i, i)
    setattr(Config, "attr_%02d" % (i + 16), i)

objects = []
results = []
expected = []


def meta_attribute(name):
    for cls in Meta.__mro__:
        if name in cls.__dict__:
            return cls.__dict__[name]
    return None


def __setup__(num=10000):
    if num < 1:
        raise ValueError("num must be positive")
    # Mirror hasStandardNamespaceAccess without invoking the bases property.
    assert meta_attribute("__getattribute__") is type.__getattribute__
    assert meta_attribute("__getattr__") is None
    assert meta_attribute("__dict__") is type.__dict__["__dict__"]
    assert meta_attribute("__bases__") is not type.__dict__["__bases__"]

    objects.clear()
    results.clear()
    expected.clear()
    for index in range(4):
        obj = Config()
        for i in range(32, 64):
            setattr(obj, "attr_%02d" % i, i)
        setattr(obj, "instance_%d" % index, index)
        obj.removed = None
        del obj.removed
        objects.append(obj)
        results.append(None)
        names = set(obj.__dict__)
        for cls in Config.__mro__:
            names.update(cls.__dict__)
        expected.append(sorted(names))


def __benchmark__(num=10000):
    # Retain the actual lists, not just their lengths, so names must be produced.
    # Object/class construction and result validation are outside the timed loop.
    for _ in range(num):
        for index, obj in enumerate(objects):
            results[index] = dir(obj)
    return results


def __cleanup__(num=10000):
    assert results == expected
