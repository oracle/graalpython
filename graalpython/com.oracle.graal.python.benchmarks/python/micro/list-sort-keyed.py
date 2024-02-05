# Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

class C:
    def __init__(self, i):
        self.i = i


data = [
    C(x) for x in [
        -315, -115, 75, 159, 2, 3, 4, 5, 298, 346, 66, -134, -415, 243, -331, 359, -5, -4, -3, -2, -171, -151, 115, 5,
        354, 270, 315, -1, 0, 1, 84, 189, -28, -77, 247, 490, 114, -273, -379, 5, -161, 160, -5, 369, 398, -302, 0,
        -491, -320, -238, 81, 173, -294, -354, -241, -31, -262, -388, -51, -337, -341, -189, 463, -440, -3, -2, -1, 0,
        1, 2, 3, 4, -230, 387, -4, -3, -2, -1, 0, 1, 2, 3, 283, 0, 1, 2, -212, 152, 368, 116, -350, -415, -106, 235,
        256, -357, 1, 2, 3, 4, 5, 6, 21, -347, -106, -242, 24, -290, -77, 144, -74, 69, -332, 243, -184, -390, -101,
        -4, -3, -2, -1, 0, 1, 2, 3, 4, -270, -176, -239, -8, -349, 481, -3, -2, 368, -441, 0, 1, 2, 3, 4, 5, 6, 7,
        -348, -90, 405, 283, -463, -470, 475, -263, -397, 372, 55, 387, -81, -384, 349, -4, -3, -2, -1, 0, 1, 2, 3, 4,
        291, -436, -311, -45, 5, 6, 7, -484, 111, -405, 90, 396, 272, 491, -491, -5, -4, -3, -2, -1, 0, 10, -5, -4, -3,
        -2, -1, 0, 1, 2, 493, -375, 26, 447, 110, -272, 258, -401, -484, -42, 190, 2, -369, 412, -185, -17, -73, -324,
        -5, -4, -3, -2, -1, 0, 1, 2, 95, 446, 439, -141, 440, -1, 0, 1, 2, 3, 4, 5, -301, -429, -78, -251, 3, 472,
        -316, -284, 437, -155, 356, 320, 160, -14, -265, 277, -3, -2, -1, 0, 1, -132, -122, 268, -325, -130, -5, -4,
        -3, -148, 374, -433, -346, 293, 2, 3, 4, -462, 421, -260, 430, -164, 193, -396, -422, -5, -4, -3, -2, -1, -279,
        -403, 368, 1, 2, 312, 18, 430, -48, 151, 353, 301, -398, -322, -6, -5, -4, -312, -232, -58, -290, 87, -479,
        340, 184,
    ]
]


def measure(num):
    m = -1000
    for i in range(num):
        m = max(m, sorted(data, key=lambda c: c.i)[-1].i)
    return m


def __benchmark__(num=1000000):
    return measure(num)
