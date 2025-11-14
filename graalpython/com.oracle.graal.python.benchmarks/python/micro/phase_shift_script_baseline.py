# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

from datetime import datetime, timezone
import sys

def f1(a=1):
    b = 2
    c = 3.0
    s = "s1"
    t = "t1"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(11):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f2(a=2, b=3):
    c = 4.0
    s = "s2"
    t = "t2"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(12):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f3(a=3, b=4, c=5.0):
    s = "s3"
    t = "t3"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(13):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f4(a=4, b=5, c=6.0, s="s4"):
    t = "t4"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(14):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f5(a=5, b=6, c=7.0, s="s5", t="t5"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(15):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f6(a=6, b=7, c=8.0, s="s6", t="t6", u="u6"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(16):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f7(a=7, b=8, c=9.0, s="s7", t="t7", u="u7", p0=14):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(17):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f8(a=8, b=9, c=10.0, s="s8", t="t8", u="u8", p0=16, p1=5.0):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(18):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f9(a=9, b=10, c=11.0, s="s9", t="t9", u="u9", p0=18, p1=5.5, p2="p9"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(19):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f10(a=10, b=11, c=12.0, s="s10", t="t10", u="u10", p0=20, p1=6.0, p2="p10", p3=13):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(20):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f11():
    a = 11
    b = 12
    c = 13.0
    s = "s11"
    t = "t11"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(21):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f12(a=12):
    b = 13
    c = 14.0
    s = "s12"
    t = "t12"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(22):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f13(a=13, b=14):
    c = 15.0
    s = "s13"
    t = "t13"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(23):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f14(a=14, b=15, c=16.0):
    s = "s14"
    t = "t14"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(24):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f15(a=15, b=16, c=17.0, s="s15"):
    t = "t15"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(25):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f16(a=16, b=17, c=18.0, s="s16", t="t16"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(26):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f17(a=17, b=18, c=19.0, s="s17", t="t17", u="u17"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(27):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f18(a=18, b=19, c=20.0, s="s18", t="t18", u="u18", p0=36):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(28):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f19(a=19, b=20, c=21.0, s="s19", t="t19", u="u19", p0=38, p1=10.5):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(29):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f20(a=20, b=21, c=22.0, s="s20", t="t20", u="u20", p0=40, p1=11.0, p2="p20"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = 0.0
    for j in range(30):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f21(a=21, b=22, c=23.0, s="s21", t="t21", u="u21", p0=42, p1=11.5, p2="p21", p3=24):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f1() + f1()
    for j in range(31):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f22():
    a = 22
    b = 23
    c = 24.0
    s = "s22"
    t = "t22"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f2() + f1()
    for j in range(32):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f23(a=23):
    b = 24
    c = 25.0
    s = "s23"
    t = "t23"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f3() + f2()
    for j in range(33):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f24(a=24, b=25):
    c = 26.0
    s = "s24"
    t = "t24"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f4() + f3()
    for j in range(34):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f25(a=25, b=26, c=27.0):
    s = "s25"
    t = "t25"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f5() + f4()
    for j in range(35):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f26(a=26, b=27, c=28.0, s="s26"):
    t = "t26"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f6() + f5()
    for j in range(36):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f27(a=27, b=28, c=29.0, s="s27", t="t27"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f7() + f6()
    for j in range(37):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f28(a=28, b=29, c=30.0, s="s28", t="t28", u="u28"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f8() + f7()
    for j in range(38):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f29(a=29, b=30, c=31.0, s="s29", t="t29", u="u29", p0=58):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f9() + f8()
    for j in range(39):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f30(a=30, b=31, c=32.0, s="s30", t="t30", u="u30", p0=60, p1=16.0):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f10() + f9()
    for j in range(40):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f31(a=31, b=32, c=33.0, s="s31", t="t31", u="u31", p0=62, p1=16.5, p2="p31"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f11() + f10()
    for j in range(41):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f32(a=32, b=33, c=34.0, s="s32", t="t32", u="u32", p0=64, p1=17.0, p2="p32", p3=35):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f12() + f11()
    for j in range(42):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f33():
    a = 33
    b = 34
    c = 35.0
    s = "s33"
    t = "t33"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f13() + f12()
    for j in range(43):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f34(a=34):
    b = 35
    c = 36.0
    s = "s34"
    t = "t34"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f14() + f13()
    for j in range(44):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f35(a=35, b=36):
    c = 37.0
    s = "s35"
    t = "t35"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f15() + f14()
    for j in range(45):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f36(a=36, b=37, c=38.0):
    s = "s36"
    t = "t36"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f16() + f15()
    for j in range(46):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f37(a=37, b=38, c=39.0, s="s37"):
    t = "t37"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f17() + f16()
    for j in range(47):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f38(a=38, b=39, c=40.0, s="s38", t="t38"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f18() + f17()
    for j in range(48):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f39(a=39, b=40, c=41.0, s="s39", t="t39", u="u39"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f19() + f18()
    for j in range(49):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f40(a=40, b=41, c=42.0, s="s40", t="t40", u="u40", p0=80):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f20() + f19()
    for j in range(50):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f41(a=41, b=42, c=43.0, s="s41", t="t41", u="u41", p0=82, p1=21.5):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f21() + f20()
    for j in range(51):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f42(a=42, b=43, c=44.0, s="s42", t="t42", u="u42", p0=84, p1=22.0, p2="p42"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f22() + f21()
    for j in range(52):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f43(a=43, b=44, c=45.0, s="s43", t="t43", u="u43", p0=86, p1=22.5, p2="p43", p3=46):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f23() + f22()
    for j in range(53):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f44():
    a = 44
    b = 45
    c = 46.0
    s = "s44"
    t = "t44"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f24() + f23()
    for j in range(54):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f45(a=45):
    b = 46
    c = 47.0
    s = "s45"
    t = "t45"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f25() + f24()
    for j in range(55):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f46(a=46, b=47):
    c = 48.0
    s = "s46"
    t = "t46"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f26() + f25()
    for j in range(56):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f47(a=47, b=48, c=49.0):
    s = "s47"
    t = "t47"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f27() + f26()
    for j in range(57):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f48(a=48, b=49, c=50.0, s="s48"):
    t = "t48"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f28() + f27()
    for j in range(58):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f49(a=49, b=50, c=51.0, s="s49", t="t49"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f29() + f28()
    for j in range(59):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f50(a=50, b=51, c=52.0, s="s50", t="t50", u="u50"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f30() + f29()
    for j in range(60):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f51(a=51, b=52, c=53.0, s="s51", t="t51", u="u51", p0=102):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f31() + f30()
    for j in range(61):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f52(a=52, b=53, c=54.0, s="s52", t="t52", u="u52", p0=104, p1=27.0):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f32() + f31()
    for j in range(62):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f53(a=53, b=54, c=55.0, s="s53", t="t53", u="u53", p0=106, p1=27.5, p2="p53"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f33() + f32()
    for j in range(63):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f54(a=54, b=55, c=56.0, s="s54", t="t54", u="u54", p0=108, p1=28.0, p2="p54", p3=57):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f34() + f33()
    for j in range(64):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f55():
    a = 55
    b = 56
    c = 57.0
    s = "s55"
    t = "t55"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f35() + f34()
    for j in range(65):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f56(a=56):
    b = 57
    c = 58.0
    s = "s56"
    t = "t56"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f36() + f35()
    for j in range(66):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f57(a=57, b=58):
    c = 59.0
    s = "s57"
    t = "t57"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f37() + f36()
    for j in range(67):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f58(a=58, b=59, c=60.0):
    s = "s58"
    t = "t58"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f38() + f37()
    for j in range(68):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f59(a=59, b=60, c=61.0, s="s59"):
    t = "t59"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f39() + f38()
    for j in range(69):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f60(a=60, b=61, c=62.0, s="s60", t="t60"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f40() + f39()
    for j in range(70):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f61(a=61, b=62, c=63.0, s="s61", t="t61", u="u61"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f41() + f40()
    for j in range(71):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f62(a=62, b=63, c=64.0, s="s62", t="t62", u="u62", p0=124):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f42() + f41()
    for j in range(72):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f63(a=63, b=64, c=65.0, s="s63", t="t63", u="u63", p0=126, p1=32.5):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f43() + f42()
    for j in range(73):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f64(a=64, b=65, c=66.0, s="s64", t="t64", u="u64", p0=128, p1=33.0, p2="p64"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f44() + f43()
    for j in range(74):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f65(a=65, b=66, c=67.0, s="s65", t="t65", u="u65", p0=130, p1=33.5, p2="p65", p3=68):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f45() + f44()
    for j in range(75):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f66():
    a = 66
    b = 67
    c = 68.0
    s = "s66"
    t = "t66"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f46() + f45()
    for j in range(76):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f67(a=67):
    b = 68
    c = 69.0
    s = "s67"
    t = "t67"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f47() + f46()
    for j in range(77):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f68(a=68, b=69):
    c = 70.0
    s = "s68"
    t = "t68"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f48() + f47()
    for j in range(78):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f69(a=69, b=70, c=71.0):
    s = "s69"
    t = "t69"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f49() + f48()
    for j in range(79):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f70(a=70, b=71, c=72.0, s="s70"):
    t = "t70"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f50() + f49()
    for j in range(80):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f71(a=71, b=72, c=73.0, s="s71", t="t71"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f51() + f50()
    for j in range(81):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f72(a=72, b=73, c=74.0, s="s72", t="t72", u="u72"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f52() + f51()
    for j in range(82):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f73(a=73, b=74, c=75.0, s="s73", t="t73", u="u73", p0=146):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f53() + f52()
    for j in range(83):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f74(a=74, b=75, c=76.0, s="s74", t="t74", u="u74", p0=148, p1=38.0):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f54() + f53()
    for j in range(84):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f75(a=75, b=76, c=77.0, s="s75", t="t75", u="u75", p0=150, p1=38.5, p2="p75"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f55() + f54()
    for j in range(85):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f76(a=76, b=77, c=78.0, s="s76", t="t76", u="u76", p0=152, p1=39.0, p2="p76", p3=79):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f56() + f55()
    for j in range(86):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f77():
    a = 77
    b = 78
    c = 79.0
    s = "s77"
    t = "t77"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f57() + f56()
    for j in range(87):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f78(a=78):
    b = 79
    c = 80.0
    s = "s78"
    t = "t78"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f58() + f57()
    for j in range(88):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f79(a=79, b=80):
    c = 81.0
    s = "s79"
    t = "t79"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f59() + f58()
    for j in range(89):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f80(a=80, b=81, c=82.0):
    s = "s80"
    t = "t80"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f60() + f59()
    for j in range(90):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f81(a=81, b=82, c=83.0, s="s81"):
    t = "t81"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f61() + f60()
    for j in range(91):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f82(a=82, b=83, c=84.0, s="s82", t="t82"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f62() + f61()
    for j in range(92):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f83(a=83, b=84, c=85.0, s="s83", t="t83", u="u83"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f63() + f62()
    for j in range(93):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f84(a=84, b=85, c=86.0, s="s84", t="t84", u="u84", p0=168):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f64() + f63()
    for j in range(94):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f85(a=85, b=86, c=87.0, s="s85", t="t85", u="u85", p0=170, p1=43.5):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f65() + f64()
    for j in range(95):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f86(a=86, b=87, c=88.0, s="s86", t="t86", u="u86", p0=172, p1=44.0, p2="p86"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f66() + f65()
    for j in range(96):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f87(a=87, b=88, c=89.0, s="s87", t="t87", u="u87", p0=174, p1=44.5, p2="p87", p3=90):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f67() + f66()
    for j in range(97):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f88():
    a = 88
    b = 89
    c = 90.0
    s = "s88"
    t = "t88"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f68() + f67()
    for j in range(98):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f89(a=89):
    b = 90
    c = 91.0
    s = "s89"
    t = "t89"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f69() + f68()
    for j in range(99):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f90(a=90, b=91):
    c = 92.0
    s = "s90"
    t = "t90"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f70() + f69()
    for j in range(10):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f91(a=91, b=92, c=93.0):
    s = "s91"
    t = "t91"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f71() + f70()
    for j in range(11):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f92(a=92, b=93, c=94.0, s="s92"):
    t = "t92"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f72() + f71()
    for j in range(12):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f93(a=93, b=94, c=95.0, s="s93", t="t93"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f73() + f72()
    for j in range(13):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f94(a=94, b=95, c=96.0, s="s94", t="t94", u="u94"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f74() + f73()
    for j in range(14):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f95(a=95, b=96, c=97.0, s="s95", t="t95", u="u95", p0=190):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f75() + f74()
    for j in range(15):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f96(a=96, b=97, c=98.0, s="s96", t="t96", u="u96", p0=192, p1=49.0):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f76() + f75()
    for j in range(16):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f97(a=97, b=98, c=99.0, s="s97", t="t97", u="u97", p0=194, p1=49.5, p2="p97"):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f77() + f76()
    for j in range(17):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f98(a=98, b=99, c=100.0, s="s98", t="t98", u="u98", p0=196, p1=50.0, p2="p98", p3=101):
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f78() + f77()
    for j in range(18):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f99():
    a = 99
    b = 100
    c = 101.0
    s = "s99"
    t = "t99"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f79() + f78()
    for j in range(19):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def f100(a=100):
    b = 101
    c = 102.0
    s = "s100"
    t = "t100"
    u0 = a + b + int(c)
    x = (a + b) * 2 - int(c)
    y = c * 1.1 + a / (b + 1.0) - 0.01
    z = (a + 2) * (b + 3) - int(c * 0.5)
    text = s + '-' + t
    idx = text.find(s[:1])
    if idx < 0: idx = 0
    text2 = text[idx:] + text[:idx]
    acc = 0.0
    base = f80() + f79()
    for j in range(20):
        acc += (x + j) * 0.17 + (y - j / 7.0) - (z * 0.003) + (u0 % 7) * 0.01 + base * 0.0001
        if j % 3 == 0:
            text2 = text2 + s[:1]
        elif j % 5 == 0 and len(text2) > 1:
            text2 = text2[1:] + text2[:1]
        else:
            k = text2.find(t[:1])
            if k >= 0:
                text2 = text2[:k] + '_' + text2[k+1:]
    res = acc + x * 0.1 + y * 0.2 - z * 0.05 + len(text2) + idx + base * 0.01
    return res

def phase2main():
    total = 0.0
    # Call all 100 functions; arguments use defaults
    total += f1() + f2() + f3() + f4() + f5()
    total += f6() + f7() + f8() + f9() + f10()
    total += f11() + f12() + f13() + f14() + f15()
    total += f16() + f17() + f18() + f19() + f20()
    total += f21() + f22() + f23() + f24() + f25()
    total += f26() + f27() + f28() + f29() + f30()
    total += f31() + f32() + f33() + f34() + f35()
    total += f36() + f37() + f38() + f39() + f40()
    total += f41() + f42() + f43() + f44() + f45()
    total += f46() + f47() + f48() + f49() + f50()
    total += f51() + f52() + f53() + f54() + f55()
    total += f56() + f57() + f58() + f59() + f60()
    total += f61() + f62() + f63() + f64() + f65()
    total += f66() + f67() + f68() + f69() + f70()
    total += f71() + f72() + f73() + f74() + f75()
    total += f76() + f77() + f78() + f79() + f80()
    total += f81() + f82() + f83() + f84() + f85()
    total += f86() + f87() + f88() + f89() + f90()
    total += f91() + f92() + f93() + f94() + f95()
    total += f96() + f97() + f98() + f99() + f100()
    
def main():
    last200it_time: int = int(datetime.now(timezone.utc).timestamp() * 1000)
    phase2it: int = int(sys.argv[1])
    print("START EXECUTION2", flush = True)  
    for i in range(phase2it + 200):
        if i == phase2it:
            print("START MEASURING", flush = True)
            last200it_time = int(datetime.now(timezone.utc).timestamp() * 1000)
        phase2main()
    endtimestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
    print(f"LAST_200_IT_TIME = {endtimestamp - last200it_time} ms", flush = True)
    
if __name__ == "__main__":
    main()    

