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

class mappingproxy_iterator(object):
    def __init__(self, proxy):
        self.items = proxy.keys()
        self.index = 0

    def __iter__(self):
        return self

    def __length_hint__(self):
        return len(self.items)

    def __next__(self):
        if self.index >= len(self.items):
            raise StopIteration
        else:
            index = self.index
            self.index += 1
            return self.items[index]


mappingproxy = type(type.__dict__)


def __iter__(self):
    return mappingproxy_iterator(self)
mappingproxy.__iter__ = __iter__


def clear(self):
    for k in self.keys():
        delete_attribute(self.obj, k)
mappingproxy.clear = clear


def items(self):
    items = []
    for k in self.keys():
        items.append(tuple([k, self.get(k)]))
    return items
mappingproxy.items = items


def values(self):
    values = []
    for k in self.keys():
        values.append(self.get(k))
    return values
mappingproxy.values = values


def __repr__(self):
    d = []
    for k in self.keys():
        d.append(repr(k) + ": " + repr(self.get(k)))
    dstr = ", ".join(d)
    return "".join(["mappingproxy({", dstr, "})"])
mappingproxy.__repr__ = __repr__
mappingproxy.__str__ = __repr__


mappingproxy.update = dict.update
