# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import datetime
import unittest

class DateTest(unittest.TestCase):

    def test_new(self):
        # returns an instance of the datetime.date class
        d = datetime.date(2025, 8, 1)
        self.assertIsInstance(d, datetime.date)
        self.assertEqual(d.year, 2025)
        self.assertEqual(d.month, 8)
        self.assertEqual(d.day, 1)

        # raises Error when year < 1
        with self.assertRaisesRegex(ValueError, "year -1 is out of range"):
            datetime.date(-1, 8, 1)

        # raises Error when year > 9999
        with self.assertRaisesRegex(ValueError, "year 10000 is out of range"):
            datetime.date(10_000, 8, 1)

        # raises Error when month < 1
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            datetime.date(2025, 0, 1)

        # raises Error when month > 12
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            datetime.date(2025, 13, 1)

        # raises Error when day < 1
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date(2024, 4, 0)

        # raises Error when day > max day of a month
        self.assertEqual(datetime.date(2024, 4, 30).day, 30)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date(2024, 4, 31)

        # raises Error when day > max day of a month and takes into account leap years
        self.assertEqual(datetime.date(2023, 2, 28).day, 28)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date(2023, 2, 29)

        self.assertEqual(datetime.date(2024, 2, 29).day, 29)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date(2024, 2, 30)

        # accepts bytes returned by __reduce__
        bytes = b'\x07\xe9\x08\x0f'
        self.assertEqual(datetime.date(bytes), datetime.date(2025, 8, 15))

        # accepts String equivalent bytes returned by __reduce__
        bytes = b'\x07\xe9\x08\x0f'
        string = bytes.decode('latin1')
        self.assertEqual(datetime.date(string), datetime.date(2025, 8, 15))

        # requires latin1 encoding
        bytes = b'\x07\xe9\x08\x0f'
        string = bytes.decode('windows-1251')
        with self.assertRaisesRegex(ValueError, "Failed to encode latin1 string when unpickling a date object. pickle.load\\(data, encoding='latin1'\\) is assumed"):
            datetime.date(string)


    def test_replace(self):
        # returns a new date instance and doesn't modify original object
        d = datetime.date(2025, 8, 1)
        d2 = d.replace(2024, 5, 6)

        self.assertIsNot(d, d2)

        self.assertEqual(d.year, 2025)
        self.assertEqual(d.month, 8)
        self.assertEqual(d.day, 1)

        # returns a new date with replaced year, month and day when given year, month and day
        d = datetime.date(2025, 8, 1)

        d2 = d.replace(2024, 5, 6)

        self.assertEqual(d2.year, 2024)
        self.assertEqual(d2.month, 5)
        self.assertEqual(d2.day, 6)

        # accepts year, month and day as keyword arguments
        d = datetime.date(2025, 8, 1)

        d2 = d.replace(year = 2024, month = 5, day = 6)

        self.assertEqual(d2.year, 2024)
        self.assertEqual(d2.month, 5)
        self.assertEqual(d2.day, 6)

        # returns a new date with replaced year and month but original day when day not given"
        d = datetime.date(2025, 8, 1)

        d2 = d.replace(2024, 5)

        self.assertEqual(d2.year, 2024)
        self.assertEqual(d2.month, 5)
        self.assertEqual(d2.day, 1)

        # returns a new date with replaced year but original month and day when month and day not given
        d = datetime.date(2025, 8, 1)

        d2 = d.replace(2024)

        self.assertEqual(d2.year, 2024)
        self.assertEqual(d2.month, 8)
        self.assertEqual(d2.day, 1)

        # returns a new date with original year, month and day when no parameters given
        d = datetime.date(2025, 8, 1)

        d2 = d.replace()

        self.assertEqual(d2.year, 2025)
        self.assertEqual(d2.month, 8)
        self.assertEqual(d2.day, 1)

        # raises Error when year < 1
        d = datetime.date(2025, 8, 1)
        with self.assertRaisesRegex(ValueError, "year -1 is out of range"):
            d.replace(-1)

        # raises Error when year > 9999
        d = datetime.date(2025, 8, 1)
        with self.assertRaisesRegex(ValueError, "year 10000 is out of range"):
            d.replace(10_000)

        # raises Error when month < 1
        d = datetime.date(2025, 8, 1)
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            d.replace(2024, 0)

        # raises Error when month > 12
        d = datetime.date(2025, 8, 1)
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            d.replace(2024, 13)

        # raises Error when day < 1
        d = datetime.date(2025, 8, 1)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            d.replace(2024, 4, 0)

        # raises Error when day > max day of a month
        d = datetime.date(2025, 8, 1)

        self.assertEqual(d.replace(2024, 4, 30).day, 30)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            d.replace(2024, 4, 31)

        # raises Error when day > max day of a month and takes into account leap years
        d = datetime.date(2025, 8, 1)

        self.assertEqual(d.replace(2023, 2, 28).day, 28)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            d.replace(2023, 2, 29)

        self.assertEqual(d.replace(2024, 2, 29).day, 29)
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            d.replace(2024, 2, 30)


    def test_toordinal(self):
        # returns a date from days count since Jan 1, 1
        d = datetime.date(1, 1, 10)
        self.assertEqual(d.toordinal(), 10)

        # takes into account month max days
        d = datetime.date(1, 5, 1)
        days = 31 + 28 + 31 + 30 + 1
        self.assertEqual(d.toordinal(), days)

        # takes into account leap years
        d = datetime.date(5, 1, 1)
        days = 365 + 365 + 365 + 366 + 1
        self.assertEqual(d.toordinal(), days)


    def test_fromordinal(self):
        # returns days count from Jan 1, 1
        d = datetime.date(1, 1, 10)
        self.assertEqual(datetime.date.fromordinal(10), d)

        # takes into account month max days
        d = datetime.date(1, 5, 1)
        days = 31 + 28 + 31 + 30 + 1
        self.assertEqual(datetime.date.fromordinal(days), d)

        # takes into account leap years
        d = datetime.date(5, 1, 1)
        days = 365 + 365 + 365 + 366 + 1
        self.assertEqual(datetime.date.fromordinal(days), d)

        # raises ValueError when ordinal < 1
        with self.assertRaisesRegex(ValueError, "ordinal must be >= 1"):
            datetime.date.fromordinal(-1)

        # raises ValueError when ordinal > date.max.toordinal()
        with self.assertRaisesRegex(ValueError, "year 10020 is out of range"):
            datetime.date.fromordinal(9999*366)


    def test_fromtimestamp(self):
        with self.assertRaisesRegex(OverflowError, "timestamp out of range for platform time_t"):
            datetime.date.fromtimestamp(1e200)


    def test_fromisocalendar(self):
        with self.assertRaisesRegex(ValueError, "Year is out of range: -1"):
            datetime.date.fromisocalendar(-1, 28, 2)
        with self.assertRaisesRegex(ValueError, "Year is out of range: 10000"):
            datetime.date.fromisocalendar(10_000, 28, 2)

        with self.assertRaisesRegex(ValueError, "Invalid week: 0"):
            datetime.date.fromisocalendar(2025, 0, 2)
        with self.assertRaisesRegex(ValueError, "Invalid week: 53"):
            datetime.date.fromisocalendar(2025, 53, 2)

        # ISO years have 53 weeks in it on years starting with a Thursday
        # and on leap years starting on Wednesday
        d = datetime.date.fromisocalendar(2015, 53, 1) # Jan 1, 2015 is Thursday
        self.assertEqual(d.year, 2015)
        self.assertEqual(d.month, 12)
        self.assertEqual(d.day, 28)
        d = datetime.date.fromisocalendar(2020, 53, 1) # Jan 1, 2020 is Wednesday
        self.assertEqual(d.year, 2020)
        self.assertEqual(d.month, 12)
        self.assertEqual(d.day, 28)

        with self.assertRaisesRegex(ValueError, r"Invalid day: 0 \(range is \[1, 7\]\)"):
            datetime.date.fromisocalendar(2025, 28, 0)
        with self.assertRaisesRegex(ValueError, r"Invalid day: 8 \(range is \[1, 7\]\)"):
            datetime.date.fromisocalendar(2025, 28, 8)


    def test_fromisoformat(self):
        # format

        # raises ValueError when given extra digits for a year
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '202500-08-04'"):
            datetime.date.fromisoformat("202500-08-04")

        # raises ValueError when missing digits for a year
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '202500-08-04'"):
            datetime.date.fromisoformat("202500-08-04")

        # raises ValueError when given non-digit characters in a year segment
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '202a-08-04'"):
            datetime.date.fromisoformat("202a-08-04")

        # raises ValueError when given extra digits for a month
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-080-04'"):
            datetime.date.fromisoformat("2025-080-04")

        # raises ValueError when missing digits for a month
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-8-04'"):
            datetime.date.fromisoformat("2025-8-04")

        # raises ValueError when given non-digit characters in a month segment
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-a8-04'"):
            datetime.date.fromisoformat("2025-a8-04")

        # raises ValueError when given extra digits for a day of a month
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-08-04000'"):
            datetime.date.fromisoformat("2025-08-04000")

        # raises ValueError when missing digits for a day of a month
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-08-4'"):
            datetime.date.fromisoformat("2025-08-4")

        # raises ValueError when given non-digit characters in a day of a month segment
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-08-4a'"):
            datetime.date.fromisoformat("2025-08-4a")

        # raises ValueError when given extra digits for a week
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W040-2'"):
            datetime.date.fromisoformat("2025-W040-2")

        # raises ValueError when missing digits for a week
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W4-2'"):
            datetime.date.fromisoformat("2025-W4-2")

        # raises ValueError when given non-digit characters in a week segment
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-Wa4-2'"):
            datetime.date.fromisoformat("2025-Wa4-2")

        # raises ValueError when given extra digits for a day of a week
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W04-20'"):
            datetime.date.fromisoformat("2025-W04-20")

        # raises ValueError when missing digits for a day of a week
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W04-'"):
            datetime.date.fromisoformat("2025-W04-")

        # raises ValueError when given non-digit characters in a day of a week segment
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W04-a'"):
            datetime.date.fromisoformat("2025-W04-a")

        # raises ValueError when missing '-' character
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-0804'"):
            datetime.date.fromisoformat("2025-0804")
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '202508-04'"):
            datetime.date.fromisoformat("202508-04")
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W042'"):
            datetime.date.fromisoformat("2025-W042")
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025W04-2'"):
            datetime.date.fromisoformat("2025W04-2")

        # argument type

        # raises TypeError when given non-String argument
        with self.assertRaisesRegex(TypeError, "fromisoformat: argument must be str"):
            datetime.date.fromisoformat(42)

        # value ranges

        # raises Error when year < 1
        with self.assertRaisesRegex(ValueError, "year 0 is out of range"):
            datetime.date.fromisoformat("0000-08-04")

        # raises Error when month < 1
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            datetime.date.fromisoformat("2025-00-04")

        # raises Error when month > 12
        with self.assertRaisesRegex(ValueError, "month must be in 1..12"):
            datetime.date.fromisoformat("2025-13-04")

        # raises Error when day < 1
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date.fromisoformat("2025-08-00")

        # raises Error when day > max day of a month
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date.fromisoformat("2024-04-31")

        # raises Error when day > max day of a month and takes into account leap years
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date.fromisoformat("2023-02-29")
        with self.assertRaisesRegex(ValueError, "day is out of range for month"):
            datetime.date.fromisoformat("2024-02-30")

        # raises ValueError when week <= 0
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W00-2'"):
            datetime.date.fromisoformat("2025-W00-2")

        # raises ValueError when week >= 53
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W53-2'"):
            datetime.date.fromisoformat("2025-W53-2")

        # raises ValueError when day of a week <= 0
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W04-0'"):
            datetime.date.fromisoformat("2025-W04-0")

        # raises ValueError when day of a week >= 8
        with self.assertRaisesRegex(ValueError, "Invalid isoformat string: '2025-W04-8'"):
            datetime.date.fromisoformat("2025-W04-8")


    def test_ctime(self):
        # returns a date string representation in format "<month> <day-of-week> <day-of-month> <hours>:<minutes>:<seconds> <year>"
        d = datetime.date(2025, 8, 20)
        self.assertEqual(d.ctime(), "Wed Aug 20 00:00:00 2025")

        # pads day of month with whitespaces
        d = datetime.date(2025, 8, 5)
        self.assertEqual(d.ctime(), "Tue Aug  5 00:00:00 2025")


    def test_add(self):
        with self.assertRaisesRegex(OverflowError, "date value out of range"):
            datetime.date.min + datetime.timedelta(days = -1)

        with self.assertRaisesRegex(OverflowError, "date value out of range"):
            datetime.date.max + datetime.timedelta(days = 1)


    def test_sub(self):
        with self.assertRaisesRegex(OverflowError, "date value out of range"):
            datetime.date.min - datetime.timedelta(days = 1)

        with self.assertRaisesRegex(OverflowError, "date value out of range"):
            datetime.date.max - datetime.timedelta(days = -1)


    def test_strftime(self):
        import locale
        locale_original = locale.getlocale(locale.LC_TIME)

        try:
            # tests with default locale
            locale.setlocale(locale.LC_TIME, None)

            d = datetime.date(2025, 8, 13)

            self.assertEqual(d.strftime("%a"), "Wed")
            self.assertEqual(d.strftime("%A"), "Wednesday")
            self.assertEqual(d.strftime("%w"), "3")
            self.assertEqual(d.strftime("%d"), "13")
            self.assertEqual(d.strftime("%b"), "Aug")
            self.assertEqual(d.strftime("%B"), "August")
            self.assertEqual(d.strftime("%m"), "08")
            self.assertEqual(d.strftime("%y"), "25")
            self.assertEqual(d.strftime("%Y"), "2025")
            self.assertEqual(d.strftime("%H"), "00")
            self.assertEqual(d.strftime("%I"), "12")
            self.assertEqual(d.strftime("%p"), "AM")
            self.assertEqual(d.strftime("%M"), "00")
            self.assertEqual(d.strftime("%S"), "00")
            self.assertEqual(d.strftime("%f"), "000000")
            self.assertEqual(d.strftime("%z"), "")
            self.assertEqual(d.strftime("%Z"), "")
            self.assertEqual(d.strftime("%j"), "225")
            self.assertEqual(d.strftime("%U"), "32")
            self.assertEqual(d.strftime("%W"), "32") # TODO: check a case when %U and %W differ
            self.assertEqual(d.strftime("%c"), "Wed Aug 13 00:00:00 2025")
            self.assertEqual(d.strftime("%x"), "08/13/25")
            self.assertEqual(d.strftime("%X"), "00:00:00")
            self.assertEqual(d.strftime("%%"), "%")

            self.assertEqual(d.strftime("%G"), "2025")
            self.assertEqual(d.strftime("%u"), "3")
            self.assertEqual(d.strftime("%V"), "33")
            self.assertEqual(d.strftime("%:z"), "")

            # check zero padding
            d = datetime.date(5, 1, 4)
            self.assertEqual(d.strftime("%d"), "04")
            self.assertEqual(d.strftime("%y"), "05")
            self.assertEqual(d.strftime("%j"), "004")
            self.assertEqual(d.strftime("%U"), "01")
            self.assertEqual(d.strftime("%V"), "01")

            # trailing '%' can be not escaped
            d = datetime.date.today()
            self.assertEqual(d.strftime("abc%"), "abc%")
            self.assertEqual(d.strftime("%"), "%")

            # unknown format code
            t = datetime.date.today()
            self.assertIn(t.strftime("ab %K cd"), ["ab %K cd", "ab K cd"]) # platform specific

            # datetime-specific format codes (that aren't supported by time module)
            self.assertEqual(d.strftime("a %f b %z c %Z d %:z e"), "a 000000 b  c  d  e")

        finally:
            # restore locale
            locale.setlocale(locale.LC_TIME, locale_original)


    def test_reduce(self):
        import pickle

        d = datetime.date(2025, 8, 15)
        dump = pickle.dumps(d)
        expected = b'\x80\x04\x95 \x00\x00\x00\x00\x00\x00\x00\x8c\x08datetime\x94\x8c\x04date\x94\x93\x94C\x04\x07\xe9\x08\x0f\x94\x85\x94R\x94.'
        self.assertEqual(dump, expected)


    def test_strptime(self):

        # explicit day of the year takes precedence other month/day
        actual = datetime.datetime.strptime("20 02/09", "%j %m/%d")
        expected = datetime.datetime(1900, 1, 20, 0, 0, 0)
        self.assertEqual(actual, expected)

        # calculated day of the year (using week and day of week) takes precedence other month/day
        actual = datetime.datetime.strptime("W01-6 02/09", "W%W-%w %m/%d")
        expected = datetime.datetime(1900, 1, 6, 0, 0, 0)
        self.assertEqual(actual, expected)

        # calculated day of the year (using ISO 8601 year, ISO 8601 week and day of week) takes precedence other month/day
        actual = datetime.datetime.strptime("1900 W01-6 02/09", "%G W%V-%w %m/%d")
        expected = datetime.datetime(1900, 1, 6, 0, 0, 0)
        self.assertEqual(actual, expected)

        # year without century (%y) is treated differently depending on the value

        actual = datetime.datetime.strptime("68", "%y")
        expected = datetime.datetime(2068, 1, 1, 0, 0, 0)
        self.assertEqual(actual, expected)

        actual = datetime.datetime.strptime("69", "%y")
        expected = datetime.datetime(1969, 1, 1, 0, 0, 0)
        self.assertEqual(actual, expected)

        # 12-clock hours are treated differently depending on value

        actual = datetime.datetime.strptime("12 AM", "%I %p")
        expected = datetime.datetime(1900, 1, 1, 0)
        self.assertEqual(actual, expected)

        actual = datetime.datetime.strptime("12 PM", "%I %p")
        expected = datetime.datetime(1900, 1, 1, 12)
        self.assertEqual(actual, expected)

        # %Z

        actual = datetime.datetime.strptime("+00:00 UTC", "%z %Z")
        self.assertEqual(actual.tzinfo.tzname(None), "UTC")

        actual = datetime.datetime.strptime("+00:00 GMT", "%z %Z")
        self.assertEqual(actual.tzinfo.tzname(None), "GMT")

        import time
        timezone_name = time.localtime().tm_zone
        self.assertIsNotNone(timezone_name)
        actual = datetime.datetime.strptime(f"+00:00 {timezone_name}", "%z %Z")
        self.assertEqual(actual.tzinfo.tzname(None), timezone_name)

        # time zone name without utc offset is ignored
        actual = datetime.datetime.strptime("UTC", "%Z")
        self.assertIsNone(actual.tzinfo)

        # %z

        actual = datetime.datetime.strptime("Z", "%z")
        self.assertEqual(actual.tzinfo, datetime.timezone.utc)


        # invalid arguments handling

        with self.assertRaisesRegex(TypeError, ".* must be str, not bytes"):
            datetime.datetime.strptime(b"2025-W04-0", "%Y-W%U-%w")

        with self.assertRaisesRegex(TypeError, ".* must be str, not bytes"):
            datetime.datetime.strptime("2025-W04-0", b"%Y-W%U-%w")


        # format errors

        with self.assertRaisesRegex(ValueError, "'R' is a bad directive in format 'abc %R'"):
            datetime.datetime.strptime("abc 0", "abc %R")

        with self.assertRaisesRegex(ValueError, "time data '2025-W04-0' does not match format '%Y/W%U/%w'"):
            datetime.datetime.strptime("2025-W04-0", "%Y/W%U/%w")

        with self.assertRaisesRegex(ValueError, "unconverted data remains: abc"):
            datetime.datetime.strptime("2025-W04-0abc", "%Y-W%U-%w")

        with self.assertRaisesRegex(ValueError, "Inconsistent use of : in \\+00:0000"):
            datetime.datetime.strptime("+00:0000", "%z")


        # ambiguity handling

        with self.assertRaisesRegex(ValueError, "Day of the year directive '%j' is not compatible with ISO year directive '%G'. Use '%Y' instead."):
            datetime.datetime.strptime("2025 2", "%G %j")

        with self.assertRaisesRegex(ValueError, "ISO year directive '%G' must be used with the ISO week directive '%V' and a weekday directive \\('%A', '%a', '%w', or '%u'\\)."):
            datetime.datetime.strptime("2025 02", "%G %m")
        with self.assertRaisesRegex(ValueError, "ISO year directive '%G' must be used with the ISO week directive '%V' and a weekday directive \\('%A', '%a', '%w', or '%u'\\)."):
            datetime.datetime.strptime("2025 02", "%G %V")
        with self.assertRaisesRegex(ValueError, "ISO year directive '%G' must be used with the ISO week directive '%V' and a weekday directive \\('%A', '%a', '%w', or '%u'\\)."):
            datetime.datetime.strptime("2025 2", "%G %w")
        self.assertEqual(datetime.datetime.strptime("2025 02 3", "%G %V %w"), datetime.datetime(2025, 1, 8, 0, 0))

        with self.assertRaisesRegex(ValueError, "%V' must be used with the ISO year directive '%G' and a weekday directive \\('%A', '%a', '%w', or '%u'\\)."):
            datetime.datetime.strptime("02", "%V")

        with self.assertRaisesRegex(ValueError, "ISO week directive '%V' is incompatible with the year directive '%Y'. Use the ISO year '%G' instead."):
            datetime.datetime.strptime("02 2025 6", "%V %Y %w")


        # locale specific checks
        import locale
        locale_original = locale.getlocale(locale.LC_TIME)

        try:
            # tests with default locale
            locale.setlocale(locale.LC_TIME, None)

            # %a
            actual = datetime.datetime.strptime("2025-W04-Sun", "%Y-W%U-%a")
            expected = datetime.datetime(2025, 1, 26, 0, 0)
            self.assertEqual(actual, expected)

            with self.assertRaisesRegex(ValueError, "time data '.*' does not match format '.*'"):
                datetime.datetime.strptime("2025-W04-XYZ", "%Y-W%U-%a")

            # %A
            actual = datetime.datetime.strptime("2025-W04-Monday", "%Y-W%U-%A")
            expected = datetime.datetime(2025, 1, 27, 0, 0)
            self.assertEqual(actual, expected)

            with self.assertRaisesRegex(ValueError, "time data '.*' does not match format '.*'"):
                datetime.datetime.strptime("2025-W04-XYZ", "%Y-W%U-%A")

            # %b
            actual = datetime.datetime.strptime("Sep 23, 2025", "%b %d, %Y")
            expected = datetime.datetime(2025, 9, 23, 0, 0)
            self.assertEqual(actual, expected)

            with self.assertRaisesRegex(ValueError, "time data '.*' does not match format '.*'"):
                datetime.datetime.strptime("XYZ 23, 2025", "%b %d, %Y")

            # %B
            actual = datetime.datetime.strptime("September 23, 2025", "%B %d, %Y")
            expected = datetime.datetime(2025, 9, 23, 0, 0)
            self.assertEqual(actual, expected)

            with self.assertRaisesRegex(ValueError, "time data '.*' does not match format '.*'"):
                datetime.datetime.strptime("XYZ 23, 2025", "%B %d, %Y")

            # %p
            actual = datetime.datetime.strptime("2025/09/23 06:58 PM", "%Y/%m/%d %I:%M %p")
            expected = datetime.datetime(2025, 9, 23, 18, 58)
            self.assertEqual(actual, expected)

            actual = datetime.datetime.strptime("2025/09/23 06:58 AM", "%Y/%m/%d %I:%M %p")
            expected = datetime.datetime(2025, 9, 23, 6, 58)
            self.assertEqual(actual, expected)

            with self.assertRaisesRegex(ValueError, "time data '.*' does not match format '.*'"):
                datetime.datetime.strptime("2025/09/23 06:58 XY", "%Y/%m/%d %I:%M %p")

        finally:
            # restore locale
            locale.setlocale(locale.LC_TIME, locale_original)


class TimeTest(unittest.TestCase):

    def test_new(self):
        # validation

        # invalid hour
        with self.assertRaisesRegex(ValueError, "hour must be in 0..23"):
            datetime.time(hour = -1)
        with self.assertRaisesRegex(ValueError, "hour must be in 0..23"):
            datetime.time(hour = 24)
        with self.assertRaisesRegex(TypeError, "'list' object cannot be interpreted as an integer"):
            datetime.time(hour = [])

        # invalid minute
        with self.assertRaisesRegex(ValueError, "minute must be in 0..59"):
            datetime.time(minute = -1)
        with self.assertRaisesRegex(ValueError, "minute must be in 0..59"):
            datetime.time(minute = 60)
        with self.assertRaisesRegex(TypeError, "'str' object cannot be interpreted as an integer"):
            datetime.time(minute = "")

        # invalid second
        with self.assertRaisesRegex(ValueError, "second must be in 0..59"):
            datetime.time(second = -1)
        with self.assertRaisesRegex(ValueError, "second must be in 0..59"):
            datetime.time(second = 60)
        with self.assertRaisesRegex(TypeError, "'str' object cannot be interpreted as an integer"):
            datetime.time(second = "")

        # invalid microsecond
        with self.assertRaisesRegex(ValueError, "microsecond must be in 0..999999"):
            datetime.time(microsecond = -1)
        with self.assertRaisesRegex(ValueError, "microsecond must be in 0..999999"):
            datetime.time(microsecond = 1_000_000)
        with self.assertRaisesRegex(TypeError, "'str' object cannot be interpreted as an integer"):
            datetime.time(microsecond = "")

        # invalid fold
        with self.assertRaisesRegex(ValueError, "fold must be either 0 or 1"):
            datetime.time(fold = -1)
        with self.assertRaisesRegex(ValueError, "fold must be either 0 or 1"):
            datetime.time(fold = 2)
        with self.assertRaisesRegex(TypeError, "'str' object cannot be interpreted as an integer"):
            datetime.time(fold = "")

        # invalid tzinfo type
        with self.assertRaisesRegex(TypeError, "tzinfo argument must be None or of a tzinfo subclass, not type 'str'"):
            datetime.time(tzinfo = "")

        # tzinfo can be None
        self.assertEqual(datetime.time(tzinfo = None), datetime.time())

        # tzinfo can be a datetime.tzinfo's subclass
        class A(datetime.tzinfo):
            pass
        a = A()
        t = datetime.time(tzinfo = a)
        self.assertEqual(t.tzinfo, a)


    def test_fromisoformat(self):
        with self.assertRaisesRegex(TypeError, "fromisoformat: argument must be str"):
            datetime.time.fromisoformat([])

        # utcoffset is None
        t = datetime.time(1, 2, 3, tzinfo = None)
        self.assertEqual(t.isoformat(), "01:02:03")

        # tzinfo.utcoffset() returns non-timedelta value
        class A(datetime.tzinfo):
            def utcoffset(self, dt):
                return 42

        with self.assertRaisesRegex(TypeError, "tzinfo.utcoffset\\(\\) must return None or timedelta, not 'int'"):
            tzinfo = A()
            t = datetime.time(tzinfo = tzinfo)
            t.isoformat()

        # given timespec None
        time = datetime.time()
        with self.assertRaisesRegex(TypeError, "isoformat\\(\\) argument 1 must be str, not None"):
          time.isoformat(timespec = None)

        # given unknown timespec
        time = datetime.time()
        with self.assertRaisesRegex(ValueError, "Unknown timespec value"):
            time.isoformat(timespec = "foo")

        # given timespec and timezone is present
        timezone = datetime.timezone(datetime.timedelta(hours = 1))
        time = datetime.time(1, 2, 3, tzinfo = timezone)

        self.assertEqual(time.isoformat(timespec = "auto"), "01:02:03+01:00")
        self.assertEqual(time.isoformat(timespec = "hours"), "01+01:00")
        self.assertEqual(time.isoformat(timespec = "minutes"), "01:02+01:00")
        self.assertEqual(time.isoformat(timespec = "seconds"), "01:02:03+01:00")
        self.assertEqual(time.isoformat(timespec = "milliseconds"), "01:02:03.000+01:00")
        self.assertEqual(time.isoformat(timespec = "microseconds"), "01:02:03.000000+01:00")


    def test_equality(self):
        # times with the same components and tzinfo are equal
        time = datetime.time(0, tzinfo = datetime.timezone.utc)
        time2 = datetime.time(0, tzinfo = datetime.timezone.utc)
        self.assertEqual(time, time2)

        # times with the same components but different tzinfo aren't equal
        time = datetime.time(0, tzinfo = datetime.timezone.utc)
        time2 = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1)))
        self.assertNotEqual(time, time2)

        # times with the different components and tzinfo but that point at the same moment in time are equal
        time = datetime.time(1, tzinfo = datetime.timezone.utc)
        time2 = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = -1)))
        self.assertEqual(time, time2)

        # naive and aware time objects are never equal
        time = datetime.time(0)
        time2 = datetime.time(0, tzinfo = datetime.timezone.utc)
        self.assertNotEqual(time, time2)

        # comparing with non-time objects
        time = datetime.time(0)
        self.assertNotEqual(time, 42)

        # comparing with a time subclass
        class A(datetime.time):
            pass
        self.assertEqual(time, A(0))
        self.assertNotEqual(time, A(1))

        # different fold values don't affect equality comparison
        self.assertEqual(datetime.time(0, fold = 0), datetime.time(0, fold = 1))

        # treats as naive an aware time that #utcoffset() returns None
        class B(datetime.tzinfo):
            def utcoffset(self, dt):
                return None

        self.assertEqual(datetime.time(0), datetime.time(0, tzinfo = B()))

        # raises TypeError given an aware time that #utcoffset() returns values that aren't None or timedelta
        class C(datetime.tzinfo):
            def utcoffset(self, dt):
                return 42

        with self.assertRaisesRegex(TypeError, "tzinfo.utcoffset\\(\\) must return None or timedelta, not 'int'"):
            datetime.time(0) == datetime.time(0, tzinfo = C())


    def test_comparison(self):
        # order comparison between naive and aware time objects raises TypeError
        naive = datetime.time(1, 2, 3)

        timedelta = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(timedelta)
        aware = datetime.time(1, 2, 3, tzinfo = timezone)

        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive > aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive >= aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive < aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive <= aware

        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            aware > naive
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            aware >= naive
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            aware < naive
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            aware <= naive

        # comparing with a time subclass
        class A(datetime.time):
            pass

        self.assertLess(datetime.time(0), A(1))
        self.assertGreater(datetime.time(1), A(0))

        # treats as naive an aware time that #utcoffset() returns None
        class B(datetime.tzinfo):
            def utcoffset(self, dt):
                return None

        naive = datetime.time(1, tzinfo = B())
        timedelta = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(timedelta)
        aware = datetime.time(1, 2, 3, tzinfo = timezone)

        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive > aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive >= aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive < aware
        with self.assertRaisesRegex(TypeError, "can't compare offset-naive and offset-aware times"):
            naive <= aware

        self.assertGreater(naive, datetime.time(0))
        self.assertLess(naive, datetime.time(2))

        # raises TypeError given an aware time that #utcoffset() returns values that aren't None or timedelta
        class C(datetime.tzinfo):
            def utcoffset(self, dt):
                return 42

        with self.assertRaisesRegex(TypeError, "tzinfo.utcoffset\\(\\) must return None or timedelta, not 'int'"):
            datetime.time(0) == datetime.time(0, tzinfo = C())

        timedelta = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(timedelta)
        with self.assertRaisesRegex(TypeError, "tzinfo.utcoffset\\(\\) must return None or timedelta, not 'int'"):
            datetime.time(0, tzinfo = timezone) == datetime.time(0, tzinfo = C())


    def test_hash(self):
        # times with the same components but different tzinfo have different hash codes
        time = datetime.time(0, tzinfo = datetime.timezone.utc)
        time2 = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1)))
        self.assertNotEqual(time.__hash__(), time2.__hash__())

        # times with the different components and tzinfo but that point at the same moment in time have equal hash codes
        time = datetime.time(1, tzinfo = datetime.timezone.utc)
        time2 = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = -1)))
        self.assertEqual(time.__hash__(), time2.__hash__())


    def test_fromisoformat(self):
        # check that a timezone offset is calculated correctly when given positive offset
        offset = datetime.timedelta(hours = 1, minutes = 2, seconds = 3, microseconds= 123000)
        timezone = datetime.timezone(offset)
        expected = datetime.time(0, tzinfo = timezone)
        actual = datetime.time.fromisoformat("00:00+01:02:03.123")

        self.assertEqual(expected, actual)

        # check that a timezone offset is calculated correctly when given negative offset
        offset = datetime.timedelta(days = -1, seconds = 82676, microseconds= 877000)
        timezone = datetime.timezone(offset)
        expected = datetime.time(0, tzinfo = timezone)
        actual = datetime.time.fromisoformat("00:00-01:02:03.123")

        self.assertEqual(expected, actual)

        # allows utc offset be specified with time seconds missing
        self.assertEqual(datetime.time.fromisoformat("00:00+01:00"), datetime.time(0, 0, tzinfo=datetime.timezone(datetime.timedelta(seconds=3600))))
        self.assertEqual(datetime.time.fromisoformat("00:00-01:00"), datetime.time(0, 0, tzinfo=datetime.timezone(datetime.timedelta(days=-1, seconds=82800))))
        self.assertEqual(datetime.time.fromisoformat("00:00Z"), datetime.time(0, 0, tzinfo=datetime.timezone.utc))

        # allows utc offset be specified with time minutes missing
        self.assertEqual(datetime.time.fromisoformat("00+01:00"), datetime.time(0, 0, tzinfo=datetime.timezone(datetime.timedelta(seconds=3600))))
        self.assertEqual(datetime.time.fromisoformat("00-01:00"), datetime.time(0, 0, tzinfo=datetime.timezone(datetime.timedelta(days=-1, seconds=82800))))
        self.assertEqual(datetime.time.fromisoformat("00Z"), datetime.time(0, 0, tzinfo=datetime.timezone.utc))


    def test_replace(self):
        # changing fold
        time = datetime.time(1, 2, 3, fold = 1)
        time2 = time.replace(fold = 0)
        self.assertEqual(time2.fold, 0)

        time = datetime.time(1, 2, 3, fold = 0)
        time2 = time.replace(fold = 1)
        self.assertEqual(time2.fold, 1)

        # changing tzinfo
        offset = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(offset)

        time = datetime.time(1, 2, 3, tzinfo = timezone)
        time2 = time.replace(tzinfo = None)
        self.assertIsNone(time2.tzinfo)

        time = datetime.time(1, 2, 3, tzinfo = None)
        time2 = time.replace(tzinfo = timezone)
        self.assertEqual(time2.tzinfo, timezone)

        time = datetime.time(1, 2, 3, tzinfo = timezone)
        offset2 = datetime.timedelta(hours = 2)
        timezone2 = datetime.timezone(offset2)
        time2 = time.replace(tzinfo = timezone2)
        self.assertEqual(time2.tzinfo, timezone2)

        # handling None values
        offset = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(offset)
        time = datetime.time(1, 2, 3, fold = 1, tzinfo = timezone)

        with self.assertRaisesRegex(TypeError, "'NoneType' object cannot be interpreted as an integer"):
            time.replace(hour = None)

        with self.assertRaisesRegex(TypeError, "'NoneType' object cannot be interpreted as an integer"):
            time.replace(minute = None)

        with self.assertRaisesRegex(TypeError, "'NoneType' object cannot be interpreted as an integer"):
            time.replace(second = None)

        with self.assertRaisesRegex(TypeError, "'NoneType' object cannot be interpreted as an integer"):
            time.replace(microsecond = None)

        time2 = time.replace(tzinfo = None)
        self.assertIsNone(time2.tzinfo)

        with self.assertRaisesRegex(TypeError, "'NoneType' object cannot be interpreted as an integer"):
            time.replace(fold = None)

        # returns
        class A(datetime.time):
            pass

        time = A(1, 2, 3)
        time2 = time.replace(hour = 10)
        self.assertIsInstance(time2, A)


    def test_utcoffset(self):
        # datetime.tzinfo.utcoffset() returns value other than None or timedelta
        class A(datetime.tzinfo):
            def utcoffset(self, dt):
                return 42

        time = datetime.time(tzinfo = A())
        with self.assertRaisesRegex(TypeError, "tzinfo.utcoffset\\(\\) must return None or timedelta, not 'int'"):
            time.utcoffset()

        # datetime.tzinfo.utcoffset() returns timedelta with magnitude less than one day
        class B(datetime.tzinfo):
            def utcoffset(self, dt):
                return datetime.timedelta(hours = 24, seconds = 1)

        class C(datetime.tzinfo):
            def utcoffset(self, dt):
                return datetime.timedelta(hours = -24, seconds = -1)

        time = datetime.time(tzinfo = B())
        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\)"):
            time.utcoffset()

        time = datetime.time(tzinfo = C())
        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\)"):
            time.utcoffset()


    def test_dst(self):
        # datetime.tzinfo.dst() returns value other than None or timedelta
        class A(datetime.tzinfo):
            def dst(self, dt):
                return 42

        time = datetime.time(tzinfo = A())
        with self.assertRaisesRegex(TypeError, "tzinfo.dst\\(\\) must return None or timedelta, not 'int'"):
            time.dst()

        # datetime.tzinfo.dst() returns timedelta with magnitude less than one day
        class B(datetime.tzinfo):
            def dst(self, dt):
                return datetime.timedelta(hours = 24, seconds = 1)

        class C(datetime.tzinfo):
            def dst(self, dt):
                return datetime.timedelta(hours = -24, seconds = -1)

        time = datetime.time(tzinfo = B())
        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\)"):
            time.dst()

        time = datetime.time(tzinfo = C())
        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\)"):
            time.dst()


    def test_tzname(self):
        # datetime.tzinfo.tzname() returns value other than None or str
        class A(datetime.tzinfo):
            def tzname(self, dt):
                return 42

        time = datetime.time(tzinfo = A())
        with self.assertRaisesRegex(TypeError, "tzinfo.tzname\\(\\) must return None or a string, not 'int'"):
            time.tzname()


    # based on DateTest.test_strftime()
    def test_strftime(self):
        import locale
        locale_original = locale.getlocale(locale.LC_TIME)

        try:
            locale.setlocale(locale.LC_TIME, None)
            t = datetime.time(16, 23, 55, 123)

            self.assertEqual(t.strftime("%a"), "Mon")
            self.assertEqual(t.strftime("%A"), "Monday")
            self.assertEqual(t.strftime("%w"), "1")
            self.assertEqual(t.strftime("%d"), "01")
            self.assertEqual(t.strftime("%b"), "Jan")
            self.assertEqual(t.strftime("%B"), "January")
            self.assertEqual(t.strftime("%m"), "01")
            self.assertEqual(t.strftime("%y"), "00")
            self.assertEqual(t.strftime("%Y"), "1900")
            self.assertEqual(t.strftime("%H"), "16")
            self.assertEqual(t.strftime("%I"), "04")
            self.assertEqual(t.strftime("%p"), "PM")
            self.assertEqual(t.strftime("%M"), "23")
            self.assertEqual(t.strftime("%S"), "55")
            self.assertEqual(t.strftime("%f"), "000123")
            self.assertEqual(t.strftime("%z"), "")
            self.assertEqual(t.strftime("%Z"), "")
            self.assertEqual(t.strftime("%j"), "001")
            self.assertEqual(t.strftime("%U"), "00")
            self.assertEqual(t.strftime("%W"), "01")
            self.assertEqual(t.strftime("%c"), "Mon Jan  1 16:23:55 1900")
            self.assertEqual(t.strftime("%x"), "01/01/00")
            self.assertEqual(t.strftime("%X"), "16:23:55")
            self.assertEqual(t.strftime("%%"), "%")

            self.assertEqual(t.strftime("%G"), "1900")
            self.assertEqual(t.strftime("%u"), "1")
            self.assertEqual(t.strftime("%V"), "01")
            self.assertEqual(t.strftime("%:z"), "")

            # check zero padding
            t = datetime.time(5, 1, 4)
            self.assertEqual(t.strftime("%H"), "05")
            self.assertEqual(t.strftime("%p"), "AM")
            self.assertEqual(t.strftime("%M"), "01")
            self.assertEqual(t.strftime("%S"), "04")
            self.assertEqual(t.strftime("%V"), "01")

            # check AP
            t = datetime.time(9, 23, 55, 123)
            self.assertEqual(t.strftime("%p"), "AM")

            # trailing '%' can be not escaped
            t = datetime.time(0)
            self.assertEqual(t.strftime("abc%"), "abc%")
            self.assertEqual(t.strftime("%"), "%")

            # unknown format code
            t = datetime.time(0)
            self.assertIn(t.strftime("ab %K cd"), ["ab %K cd", "ab K cd"]) # platform specific

            # datetime-specific format codes (that aren't supported by time module)
            self.assertEqual(t.strftime("a b %z c %Z d %:z e"), "a b  c  d  e")

            # %Z when timezone is missing
            t = datetime.time(0, tzinfo = None)
            self.assertEqual(t.strftime("%Z"), "")

            # %Z when timezone is present but tzname is missing
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1)))
            self.assertEqual(t.strftime("%Z"), "UTC+01:00")
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3, microseconds = 4)))
            self.assertEqual(t.strftime("%Z"), "UTC+01:02:03.000004")
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3)))
            self.assertEqual(t.strftime("%Z"), "UTC+01:02:03")
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2)))
            self.assertEqual(t.strftime("%Z"), "UTC+01:02")
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1)))
            self.assertEqual(t.strftime("%Z"), "UTC+01:00")
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta()))
            self.assertEqual(t.strftime("%Z"), "UTC")
            t = datetime.time(0, tzinfo = datetime.timezone(-datetime.timedelta(hours = 1, minutes = 2, seconds = 3, microseconds = 4)))
            self.assertEqual(t.strftime("%Z"), "UTC-01:02:03.000004")

            # %Z when timezone is present and tzname is present
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1), "FOOBAR"))
            self.assertEqual(t.strftime("%Z"), "FOOBAR")

            # %z when timezone is present and all utc offset components present
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3, microseconds = 4), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "+010203.000004")

            # %z when timezone is present and present utc offset's hours, minutes and seconds
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "+010203")

            # %z when timezone is present and present utc offset's hours, minutes only
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "+0102")

            # %z when timezone is present and present utc offset's hours only
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "+0100")

            # %z when timezone is present and it's UTC
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "+0000")

            # %z when timezone is present and it's negative
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = -1), "FOOBAR"))
            self.assertEqual(t.strftime("%z"), "-0100")

            # %:z when timezone is present and all utc offset components present
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3, microseconds = 4), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "+01:02:03.000004")

            # %:z when timezone is present and present utc offset's hours, minutes and seconds
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2, seconds = 3), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "+01:02:03")

            # %:z when timezone is present and present utc offset's hours, minutes only
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1, minutes = 2), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "+01:02")

            # %:z when timezone is present and present utc offset's hours only
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = 1), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "+01:00")

            # %:z when timezone is present and it's UTC
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "+00:00")

            # %:z when timezone is present and it's negative
            t = datetime.time(0, tzinfo = datetime.timezone(datetime.timedelta(hours = -1), "FOOBAR"))
            self.assertEqual(t.strftime("%:z"), "-01:00")

        finally:
            # restore locale
            locale.setlocale(locale.LC_TIME, locale_original)


class TzInfoTest(unittest.TestCase):
    def test_new(self):
        # accepts arbitrary positional parameters
        self.assertIsInstance(datetime.tzinfo(1, 2, 3, 4), datetime.tzinfo)

        # accepts arbitrary keyword parameters
        self.assertIsInstance(datetime.tzinfo(a = 1, b = 2, c = 3), datetime.tzinfo)


class TimezoneTest(unittest.TestCase):
    def test_new(self):
        with self.assertRaisesRegex(TypeError, "timezone\\(\\) argument 1 must be datetime.timedelta, not int"):
            datetime.timezone(42)

        with self.assertRaisesRegex(TypeError, "timezone\\(\\) argument 2 must be str, not int"):
            td = datetime.timedelta(hours = 1)
            datetime.timezone(td, 42)

        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\), not datetime.timedelta\\(days=1, seconds=1\\)"):
            td = datetime.timedelta(hours = 24, seconds = 1)
            datetime.timezone(td)

        with self.assertRaisesRegex(ValueError, "offset must be a timedelta strictly between -timedelta\\(hours=24\\) and timedelta\\(hours=24\\), not datetime.timedelta\\(days=-2, seconds=86399\\)"):
            td = datetime.timedelta(hours = -24, seconds = -1)
            datetime.timezone(td)


    def test_repr(self):
        # when name is present
        td = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(td, "EEST")
        self.assertEqual(timezone.__repr__(), "datetime.timezone(datetime.timedelta(seconds=3600), 'EEST')")

        # skip name argument if it isn't given
        td = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(td)
        self.assertEqual(timezone.__repr__(), "datetime.timezone(datetime.timedelta(seconds=3600))")

        # return "timezone.utc" when timedelta is timedelta(0)
        td = datetime.timedelta(0)
        timezone = datetime.timezone(td)
        self.assertEqual(timezone.__repr__(), "datetime.timezone.utc")


    def test_getinitargs(self):
        # when name is present
        td = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(td, "EEST")
        self.assertEqual(timezone.__getinitargs__(), (td, "EEST"))

        # skip name in result if name isn't given
        td = datetime.timedelta(hours = 1)
        timezone = datetime.timezone(td)
        self.assertEqual(timezone.__getinitargs__(), (td, ))

    def test_inheritance(self):
        # it cannot be subclassed
        with self.assertRaisesRegex(TypeError, "type 'datetime.timezone' is not an acceptable base type"):
            class A(datetime.timezone):
                pass


class TimeDeltaTest(unittest.TestCase):
    def test_new(self):
        # parameters type validation

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta days component: list"):
            datetime.timedelta(days = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta seconds component: list"):
            datetime.timedelta(seconds = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta microseconds component: list"):
            datetime.timedelta(microseconds = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta milliseconds component: list"):
            datetime.timedelta(milliseconds = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta minutes component: list"):
            datetime.timedelta(minutes = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta hours component: list"):
            datetime.timedelta(hours = [])

        with self.assertRaisesRegex(TypeError, "unsupported type for timedelta weeks component: list"):
            datetime.timedelta(weeks = [])

        # Nan value

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(days = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(seconds = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(microseconds = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(milliseconds = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(minutes = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(hours = float("nan"))

        with self.assertRaisesRegex(ValueError, "cannot convert float NaN to integer"):
            datetime.timedelta(weeks = float("nan"))

    def test_total_seconds(self):
        self.assertAlmostEqual(datetime.timedelta(days=106751992).total_seconds(), 9223372108800.0)

class DateTimeTest(unittest.TestCase):

    def test_strptime(self):
        # generic case
        d = datetime.datetime.strptime('2014 7 2 6 14 0 742 +0700', '%Y %m %d %H %M %S %f %z')
        self.assertEqual(d.year, 2014)
        self.assertEqual(d.month, 7)
        self.assertEqual(d.day, 2)
        self.assertEqual(d.hour, 6)
        self.assertEqual(d.minute, 14)
        self.assertEqual(d.second, 0)
        self.assertEqual(d.microsecond, 742000)
        self.assertIsNotNone(d.tzinfo)
        self.assertEqual(d.tzinfo.utcoffset(d), datetime.timedelta(hours=7))
