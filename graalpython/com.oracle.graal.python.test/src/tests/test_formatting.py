# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import unittest


class Polymorph:
    def __index__(self):
        return 42
    def __int__(self):
        return 1
    def __float__(self):
        return 3.14
    def __str__(self):
        return "hello"
    def __bytes__(self):
        return b"bytes"


# This is all one needs to implement to satisfy PyCheck_Mapping
class MyPseudoMapping:
    def __getitem__(self, item):
        return item


def test_formatting():
    # tests some corner-cases that the standard tests do not cover
    assert format(-12e8, "0=30,.4f") == '-0,000,000,001,200,000,000.0000'
    assert b"%(mykey)d" % {b'mykey': 42} == b"42"
    assert b"%c" % b'q' == b"q"
    assert "%-5d" % 42 == "42   "
    assert "%.*f" % (-2, 2.5) == "2"
    assert "%.*f" % (True, 2.51) == "2.5"
    assert "%ld" % 42 == "42"

    assert "%c" % Polymorph() == "*"
    assert "%d" % Polymorph() == "1"
    assert "%x" % Polymorph() == "2a"
    assert "%s" % Polymorph() == "hello"
    assert "%.2f" % Polymorph() == "3.14"
    assert b"%c" % Polymorph() == b"*"
    assert b"%s" % Polymorph() == b"bytes"

    assert type(bytearray("hello %d", "ascii") % 42) == bytearray
    assert type(b"hello %d" % 42) == bytes

    # No error about too many arguments,
    # because the object is considered as a mapping...
    assert " " % MyPseudoMapping() == " "


def test_complex_formatting():
    assert format(3+2j, ">20,.4f") == "      3.0000+2.0000j"
    assert format(3+2j, "+.2f") == "+3.00+2.00j"
    assert format(-3+2j, "+.2f") == "-3.00+2.00j"
    assert format(3+2j, "-.3f") == "3.000+2.000j"
    assert format(3-2j, "-.3f") == "3.000-2.000j"
    assert format(-3-2j, "-.3f") == "-3.000-2.000j"
    assert format(3+2j, " .1f") == " 3.0+2.0j"
    assert format(-3+2j, " .1f") == "-3.0+2.0j"
    assert format(complex(3), ".1g") == "3+0j"
    assert format(3j, ".1g") == "0+3j"
    assert format(-3j, ".1g") == "-0-3j"
    assert format(3j, "") == "3j"
    assert format(1+0j, "") == "(1+0j)"
    assert format(1+2j, "") == "(1+2j)"
    assert format(complex(1, float("NaN")), "") == "(1+nanj)"
    assert format(complex(1, float("Inf")), "") == "(1+infj)"


class AnyRepr:
    def __init__(self, val):
        self.val = val
    def __repr__(self):
        return self.val


def test_non_ascii_repr():
    assert "%a" % AnyRepr("\t") == "\t"
    assert "%a" % AnyRepr("\\") == "\\"
    assert "%a" % AnyRepr("\\") == "\\"
    assert "%a" % AnyRepr("\u0378") == "\\u0378"
    assert "%r" % AnyRepr("\u0378") == "\u0378"
    assert "%a" % AnyRepr("\u0374") == "\\u0374"
    assert "%r" % AnyRepr("\u0374") == "\u0374"

    assert b"%a" % AnyRepr("\t") == b"\t"
    assert b"%a" % AnyRepr("\\") == b"\\"
    assert b"%a" % AnyRepr("\\") == b"\\"
    assert b"%a" % AnyRepr("\u0378") == b"\\u0378"
    assert b"%r" % AnyRepr("\u0378") == b"\\u0378"
    assert b"%a" % AnyRepr("\u0374") == b"\\u0374"
    assert b"%r" % AnyRepr("\u0374") == b"\\u0374"


class FormattingErrorsTest(unittest.TestCase):
    def test_formatting_errors(self):
        self.assertRaises(TypeError, lambda: format(-12e8, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format(42, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format("str", b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format(3+1j, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: b"hello" % b"world")
        self.assertRaises(TypeError, lambda: b"%f" % "str")
        self.assertRaises(TypeError, lambda: b"%c" % "str")

        self.assertRaises(KeyError, lambda: b"%(mykey)d" % {"mykey": 42})
        self.assertRaises(KeyError, lambda: "%(mykey)d" % {b"mykey": 42})
        self.assertRaises(OverflowError, lambda: b"%c" % 260)

        self.assertRaises(ValueError, lambda: format(3+2j, "f=30,.4f"))
        self.assertRaises(ValueError, lambda: format(3+2j, "0=30,.4f"))