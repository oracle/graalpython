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



def prints_like_iter(obj):
    s = str(obj)
    assert s.startswith("<class '")
    assert s.endswith("iterator'>")

def test_iter():
    bytes_iterator = type(iter(b''))
    prints_like_iter(bytes_iterator)
    # <class 'bytes_iterator'>

    # bytearray_iterator = type(iter(bytearray()))
    # print(bytearray_iterator)

    dict_iterator = type(iter({}))
    prints_like_iter(dict_iterator)
    # <class 'dict_keyiterator'>

    dict_keyiterator = type(iter({}.keys()))
    prints_like_iter(dict_keyiterator)
    # <class 'dict_keyiterator'>

    dict_valueiterator = type(iter({}.values()))
    prints_like_iter(dict_valueiterator)
    # <class 'dict_valueiterator'>

    dict_itemiterator = type(iter({}.items()))
    prints_like_iter(dict_itemiterator)
    # <class 'dict_itemiterator'>

    list_iterator = type(iter([]))
    prints_like_iter(list_iterator)
    # <class 'list_iterator'>

    # list_reverseiterator = type(iter(reversed([])))
    # prints_like_iter(list_reverseiterator)

    range_iterator = type(iter(range(0)))
    prints_like_iter(range_iterator)
    # <class 'range_iterator'>

    longrange_iterator = type(iter(range(1 << 1000)))
    prints_like_iter(longrange_iterator)
    # <class 'longrange_iterator'>

    set_iterator = type(iter(set()))
    prints_like_iter(set_iterator)
    # <class 'set_iterator'>

    str_iterator = type(iter(""))
    prints_like_iter(str_iterator)
    # <class 'str_iterator'>

    tuple_iterator = type(iter(()))
    prints_like_iter(tuple_iterator)
    # <class 'tuple_iterator'>

    zip_iterator = type(iter(zip()))
    assert str(zip_iterator) == "<class 'zip'>"
    # <class 'zip'>


def test_zip_no_args():
    assert list(zip(*[])) == []


def test_iter_try_except():
    it = iter(range(3))
    exit_via_break = False
    while 1:
        try:
            next(it)
        except StopIteration:
            exit_via_break = True
            break

    assert exit_via_break
