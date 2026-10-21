# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_import():
    imported = True
    try:
        from _locale import (setlocale, LC_ALL, LC_CTYPE, LC_NUMERIC, localeconv, Error)
    except ImportError:
        imported = False
    assert imported


def test_get_setlocale():
    import locale
    current_locale = locale.getlocale(0)
    new_locale = ('en_GB', 'UTF-8')
    try:
        try:
            assert str(locale.setlocale(0, new_locale)) == '.'.join(new_locale)
        except locale.Error:
            # Skip when unavailable in the CI
            return
        assert locale.getlocale(0) == new_locale
    finally:
        locale.setlocale(0, current_locale)


def test_numeric_locale_formatting():
    import locale
    from decimal import Decimal

    saved_locale = locale.setlocale(locale.LC_NUMERIC)
    try:
        for name, separator, point in (("C", "", "."), ("en_US.UTF-8", ",", "."),
                                       ("de_DE.UTF-8", ".", ","), ("C", "", ".")):
            try:
                locale.setlocale(locale.LC_NUMERIC, name)
            except locale.Error:
                if name == "C":
                    raise
                continue  # These locales are not installed on every test host.
            conv = locale.localeconv()
            assert conv["decimal_point"] == point
            assert conv["thousands_sep"] == separator
            if name == "C":
                assert conv["grouping"] == []
            integer = separator.join(("1", "234", "567"))
            number = integer + point + "89"
            for sign in ("", "-"):
                assert format(int(sign + "1234567"), "n") == sign + integer
                assert format(float(sign + "1234567.89"), ".9n") == sign + number
                assert format(Decimal(sign + "1234567.89"), "n") == sign + number
                assert format(complex(float(sign + "1234567.89"), 1234567.89), ".9n") == sign + number + "+" + number + "j"
            assert locale.format_string("%.2f", 1234567.89, grouping=True) == number
            assert format(1234567.89, ",.2f") == "1,234,567.89"
            assert format(1234567, ",d") == "1,234,567"
    finally:
        locale.setlocale(locale.LC_NUMERIC, saved_locale)
