# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

# ankitv 10/10/13
# Iterating by Sequence Index

BUF = []
def print(*s):
    BUF.append(" ".join([str(e) for e in s]))


def test_it():
    fruits = ['banana', 'apple',  'mango']
    for index in range(len(fruits)):
        print ('Current fruit :' , fruits[index])

    #The else Statement Used with Loops
    for num in range(10,15):  #to iterate between 10 to 20
        for i in range(2,num): #to iterate on the factors of the number
            if num%i == 0:      #to determine the first factor
                j=num/i          #to calculate the second factor
                print ('%d = %d * %d' % (num,i,j))
                break #to move to the next number, the #first FOR
        else:                  # else part of the loop
            print (num, 'prime number')

    assert "\n".join(BUF) == """Current fruit : banana
Current fruit : apple
Current fruit : mango
10 = 2 * 5
11 prime number
12 = 2 * 6
13 prime number
14 = 2 * 7"""


def test_else_continue():
    sequence = []
    items = [1,2,3]
    do_break = False
    while True:
        for _ in items:
            pass # no break so we will execute the else
        else:
            if do_break:
                sequence.append("break")
                break
            do_break = True
            sequence.append("continue")
            continue

    assert ["continue", "break"] == sequence


def test_else_break():
    iters = 0
    while iters < 4:
        for i in range(iters):
            if False:
                break
            iters += 1
        else:
            iters += 1
            break
    assert iters == 1, "if the for-loop doesn't break, the else should be executed and break out of the outer loop"


def test_else_break_from_while():
    iters = 0
    while iters < 40:
        while iters < 10:
            if False:
                break
            iters += 1
        else:
            iters += 1
            break
    assert iters == 11, "if the while-loop doesn't break, the else should be executed and break out of the outer loop"

class A(object):
    def __iter__(self):
        return iter(['key', 'value'])


def test_for_iter():
    a1 = A()
    a2 = A()
    list = [a1, a2]
    iterator = 0
    for key, value in list:
        assert 'key' in key
        assert 'value' in value
        iterator += 1
    assert iterator==2