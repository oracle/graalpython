# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import time
import calendar
import unittest

def test_sleep():
    start = time.time()
    time.sleep(0.1)
    assert time.time() - start > 0.1


def test_sleep_sec():
    start = time.time()
    time.sleep(1)
    assert time.time() - start > 1


def test_monotonic():
    times = [time.monotonic() for _ in range(100)]
    for t1, t2 in zip(times[:-1], times[1:]):
        assert t1 <= t2

class ClockInfoTests(unittest.TestCase):
    def test_get_clock_info(self):
        self.assertRaises(TypeError, time.get_clock_info, 1)
        self.assertRaises(ValueError, time.get_clock_info, 'bogus')

class StructTimeTests(unittest.TestCase):

    def test_new_struct_time(self):
        t = time.struct_time((2018, 11, 26, 17, 34, 12, 0, 340, -1))
        self.assertEqual(t.tm_year,  2018)
        self.assertEqual(t.tm_mon, 11)
        self.assertEqual(t.tm_mday, 26)
        self.assertEqual(t[2], 26)
        self.assertEqual(t.tm_zone, None)
        
        self.assertRaises(TypeError, time.struct_time, (2018, 11, 26, 17, 34, 12, 0, 340))
        self.assertRaises(TypeError, time.struct_time, (2018, 11, 26, 17, 34, 12, 0, 340, 9, 10, 11, 12))

    def test_from_times(self):
        gt = time.gmtime()
        self.assertNotEqual(gt.tm_zone, None)
        self.assertNotEqual(gt.tm_gmtoff, None)
        
        lt = time.localtime()
        self.assertNotEqual(lt.tm_zone, None)
        self.assertNotEqual(lt.tm_gmtoff, None)
        
    def test_destructuring_assignment(self):
        t = time.struct_time((1,2,3,4,5,6,7,8,9))
        y,m,d,h,mi,s,wd,yd,dst = t
        self.assertEqual(y, 1)
        self.assertEqual(mi, 5)
        self.assertEqual(dst, 9)
        self.assertEqual(t.tm_zone, None)
        self.assertEqual(t.tm_gmtoff, None)
        
        t = time.struct_time((11,12,13,14,15,16,17,18,19,20, 21))
        y,m,d,h,mi,s,wd,yd,dst = t
        self.assertEqual(y, 11)
        self.assertEqual(mi, 15)
        self.assertEqual(dst, 19)
        self.assertEqual(t.tm_zone, 20)
        self.assertEqual(t.tm_gmtoff, 21)

class StrftimeTests(unittest.TestCase):

    def check_format(self, format, date, expectedStr):
        st = time.struct_time(date);
        self.assertEqual(expectedStr, time.strftime(format, st))

    def check_weekDay(self, format, days):
        for d in range (0,21):
            self.check_format(format, (2018, 11, 28, 15, 24, 30, d, 1, 0), days[d % 7])

        self.check_format(format, (2018, 11, 28, 15, 24, 30, -1, 1, 0), days[6])
        self.check_format(format, (2018, 11, 28, 15, 24, 30, 356, 1, 0), days[6])
        self.check_format(format, (2018, 11, 28, 15, 24, 30, 9000, 1, 0), days[5])

        self.assertRaises(ValueError, time.strftime, format, time.struct_time((2018, 10, 28, 15, 24, 30, -2, 1, 0)))

    def check_month(self, format, months):
        self.check_format(format, (2018, 0, 28, 15, 24, 30, 1, 1, 0), months[1])
        #for m in range (1,12):
        #    self.check_format(format, (2018, m, 28, 15, 24, 30, 1, 1, 0), months[m-1])

        self.assertRaises(ValueError, time.strftime, format, time.struct_time((2018, -1, 28, 15, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, format, time.struct_time((2018, 13, 28, 15, 24, 30, 1, 1, 0)))

    def test_weekOfDayShort(self):
        self.check_weekDay("%a", calendar.day_abbr)

    def test_weekOfDay(self):
        self.check_weekDay("%A", calendar.day_name)
    
    def test_monthShortName(self):
        self.check_month("%b", calendar.month_abbr)

    def test_monthLongName(self):
        self.check_month("%B", calendar.month_name)

    def test_asctime(self):
        # 'Thu Aug  8 05:24:10 2018' in en locale
        self.check_format("%c", (2018, 8, 8, 5, 24, 10, 3, 1, 0), calendar.day_abbr[3] + ' ' + calendar.month_abbr[8] + '  8 05:24:10 2018')

    def test_day(self):
        self.check_format("%d", (2018, 8, 8, 5, 24, 10, 3, 1, 0), '08')
        self.check_format("%d", (2018, 8, 18, 5, 24, 10, 3, 1, 0), '18')
        self.check_format("%d", (2018, 8, 0, 5, 24, 10, 3, 1, 0), '01')
        self.check_format("%d", (2018, 2, 31, 5, 24, 10, 3, 1, 0), '31')
        self.assertRaises(ValueError, time.strftime, "%d", time.struct_time((2018, 8, -1, 15, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%d", time.struct_time((2018, 8, 32, 15, 24, 30, 1, 1, 0)))

    def test_hour24(self):
        self.check_format("%H", (2018, 8, 8, 0, 24, 0, 3, 1, 0), '00')
        self.check_format("%H", (2018, 8, 18, 1, 24, 1, 3, 1, 0), '01')
        self.check_format("%H", (2018, 8, 8, 22, 24, 10, 3, 1, 0), '22')
        self.check_format("%H", (2018, 2, 31, 23, 24, 10, 3, 1, 0), '23')
        self.assertRaises(ValueError, time.strftime, "%H", time.struct_time((2018, 8, 2, -1, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%H", time.struct_time((2018, 8, 2, 24, 24, 30, 1, 1, 0)))

    def test_hour12(self):
        self.check_format("%I", (2018, 8, 8, 0, 24, 0, 3, 1, 0), '12')
        self.check_format("%I", (2018, 8, 18, 1, 24, 1, 3, 1, 0), '01')
        self.check_format("%I", (2018, 8, 8, 12, 24, 0, 3, 1, 0), '12')
        self.check_format("%I", (2018, 8, 8, 22, 24, 10, 3, 1, 0), '10')
        self.check_format("%I", (2018, 2, 31, 23, 24, 10, 3, 1, 0), '11')
        self.assertRaises(ValueError, time.strftime, "%I", time.struct_time((2018, 8, 2, -1, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%I", time.struct_time((2018, 8, 2, 24, 24, 30, 1, 1, 0)))

    def test_dayOfYear(self):
        self.check_format("%j", (2018, 8, 8, 0, 24, 0, 7, 0, 0), '001')
        self.check_format("%j", (2018, 8, 8, 0, 24, 0, 7, 1, 0), '001')
        self.check_format("%j", (2018, 8, 8, 0, 24, 0, 7, 10, 0), '010')
        self.check_format("%j", (2018, 8, 8, 0, 24, 0, 7, 365, 0), '365')
        self.check_format("%j", (2018, 8, 8, 0, 24, 0, 7, 366, 0), '366')
        self.assertRaises(ValueError, time.strftime, "%j", time.struct_time((2018, 8, 2, 24, 24, 30, 1, -1, 0)))
        self.assertRaises(ValueError, time.strftime, "%j", time.struct_time((2018, 8, 2, 24, 24, 30, 1, 367, 0)))

    def test_month(self):
        self.check_format("%m", (2018, 0, 8, 0, 24, 0, 3, 1, 0), '01')
        self.check_format("%m", (2018, 1, 8, 0, 24, 0, 3, 1, 0), '01')
        self.check_format("%m", (2018, 8, 18, 1, 24, 1, 3, 1, 0), '08')
        self.check_format("%m", (2018, 12, 8, 10, 24, 0, 3, 1, 0), '12')
        self.assertRaises(ValueError, time.strftime, "%m", time.struct_time((2018, -1, 2, 2, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%m", time.struct_time((2018, 13, 2, 2, 24, 30, 1, 1, 0)))

    def test_minute(self):
        self.check_format("%M", (2018, 0, 8, 0, 0, 0, 3, 1, 0), '00')
        self.check_format("%M", (2018, 1, 8, 0, 8, 0, 3, 1, 0), '08')
        self.check_format("%M", (2018, 8, 18, 1, 50, 1, 3, 1, 0), '50')
        self.check_format("%M", (2018, 12, 8, 10, 59, 0, 3, 1, 0), '59')
        self.assertRaises(ValueError, time.strftime, "%M", time.struct_time((2018, 11, 2, 2, -1, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%M", time.struct_time((2018, 11, 2, 2, 60, 30, 1, 1, 0)))

    def test_ampm(self):
        pm_am = time.strftime("%p");
        if pm_am == 'AM' or pm_am == 'am' or pm_am == 'PM' or pm_am == 'pm':
            # the test has sence only if the pm/am is provided
            is_lower_case = pm_am[1] == 'm'
            pm_case = 'pm' if is_lower_case else 'PM'
            am_case = 'am' if is_lower_case else 'AM'
            self.check_format("%p", (2018, 2, 18, 0, 0, 0, 3, 1, 0), am_case)
            self.check_format("%p", (2018, 8, 18, 11, 8, 0, 3, 1, 0), am_case)
            self.check_format("%p", (2018, 8, 18, 12, 50, 1, 3, 1, 0), pm_case)
            self.check_format("%p", (2018, 8, 18, 23, 59, 0, 3, 1, 0), pm_case)
        self.assertRaises(ValueError, time.strftime, "%p", time.struct_time((2018, 8, 2, -1, 24, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%p", time.struct_time((2018, 8, 2, 24, 24, 30, 1, 1, 0)))

    def test_sec(self):
        self.check_format("%S", (2018, 2, 18, 10, 10, 0, 3, 1, 0), '00')
        self.check_format("%S", (2018, 8, 18, 11, 12, 2, 3, 1, 0), '02')
        self.check_format("%S", (2018, 8, 18, 12, 20, 60, 3, 1, 0), '60')
        self.check_format("%S", (2018, 8, 18, 23, 20, 61, 3, 1, 0), '61')
        self.assertRaises(ValueError, time.strftime, "%S", time.struct_time((2018, 8, 2, 10, -1, 30, 1, 1, 0)))
        self.assertRaises(ValueError, time.strftime, "%S", time.struct_time((2018, 8, 2, 24, 62, 30, 1, 1, 0)))

    def test_weekDay(self):
        self.check_format("%w", (2018, 11, 28, 10, 0, 0, -1, 1, 0), '0')
        self.check_format("%w", (2018, 11, 28, 10, 0, 0, 0, 1, 0), '1')
        self.check_format("%w", (2018, 11, 25, 11, 12, 2, 3, 1, 0), '4')
        self.check_format("%w", (2018, 11, 24, 23, 20, 61, 6, 1, 0), '0')
        self.check_format("%w", (2018, 11, 24, 23, 20, 61, 7, 1, 0), '1')
        self.check_format("%w", (2018, 11, 24, 23, 20, 61, 999, 1, 0), '6')
        self.assertRaises(ValueError, time.strftime, "%w", time.struct_time((2018, 8, 2, 10, 20, 30, -2, 1, 0)))
    
    def test_YearY(self):
        self.check_format("%Y", (2018, 11, 28, 10, 0, 0, -1, 1, 0), '2018')
        self.check_format("%Y", (18, 11, 28, 10, 0, 0, 0, 1, 0), '18')
        self.check_format("%Y", (0, 11, 25, 11, 12, 2, 3, 1, 0), '0')
        self.check_format("%Y", (-365, 11, 24, 23, 20, 61, 6, 1, 0), '-365')
        self.check_format("%Y", (17829, 11, 24, 23, 20, 61, 7, 1, 0), '17829')

    def test_Yeary(self):
        self.check_format("%y", (2018, 11, 28, 10, 0, 0, -1, 1, 0), '18')
        self.check_format("%y", (18, 11, 28, 10, 0, 0, 0, 1, 0), '18')
        self.check_format("%y", (0, 11, 25, 11, 12, 2, 3, 1, 0), '00')
        # This is failing on CPython, which return '35' 
        #self.check_format("%y", (-365, 11, 24, 23, 20, 61, 6, 1, 0), '65')
        self.check_format("%y", (17829, 11, 24, 23, 20, 61, 7, 1, 0), '29')

    def test_wrongInput(self):
        self.assertRaises(TypeError, time.strftime, 10, (2018, 8, 2, 10, 20, 30, -2, 1, 0))
        self.assertRaises(TypeError, time.strftime, "%w", 10)
        self.assertRaises(TypeError, time.strftime, "%w", (2018, 11, 29))

