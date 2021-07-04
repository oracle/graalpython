# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

"""
This benchmark simulates what happens when you import a Python file that has a
pyc file - namely, it is read, the first 16 bytes are metadata, and then the rest
is just unmarshalled into a code object that can be executed.

The code we're looking at is just a simple file like this:
    import sys
    len(sys.__name__)

In CPython bytecode, that's this:
  1           0 LOAD_CONST               0 (0)
              2 LOAD_CONST               1 (None)
              4 IMPORT_NAME              0 (sys)
              6 STORE_NAME               0 (sys)

  2           8 LOAD_NAME                1 (len)
             10 LOAD_NAME                0 (sys)
             12 LOAD_ATTR                2 (__name__)
             14 CALL_FUNCTION            1
             16 POP_TOP
             18 LOAD_CONST               1 (None)
             20 RETURN_VALUE
"""

import marshal
import sys


CPYTHON_CODE = b'U\r\r\n\x00\x00\x00\x00bx\xdc`\x1f\x00\x00\x00\xe3\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x02\x00\x00\x00@\x00\x00\x00s\x16\x00\x00\x00d\x00d\x01l\x00Z\x00e\x01e\x00j\x02\x83\x01\x01\x00d\x01S\x00)\x02\xe9\x00\x00\x00\x00N)\x03\xda\x03sys\xda\x03len\xda\x08__name__\xa9\x00r\x05\x00\x00\x00r\x05\x00\x00\x00\xfa,/home/tim/Dev/graalpython/simple/__main__.py\xda\x08<module>\x01\x00\x00\x00s\x02\x00\x00\x00\x08\x01'[16:]


GRAALPYTHON_CODE = b'\x8aR\r\n\x00\x00\x00\x00\x93z\xe1`\x1d\x00\x00\x00\xc3,\x00\x00\x00/home/tim/Dev/graalpython/simple/__main__.py@\x00\x00\x00\xb4\x00\x00\x00\r\x00\x0b__main__.py\x00,/home/tim/Dev/graalpython/simple/__main__.py\x00\x00\x00\x1dimport sys\nlen(sys.__name__)\n\x00\x00\x02[]R\x97\x03\x0e\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x07\x02\x1c\x02\x19\t\x03\x00\x03sys\x02\x11\t\r\x11,\r\x03\x00\x03len\x01\x00\x00\x00\x16\x11\x0c\x00\x08__name__,\x11\x03\x03\x82\xff\xff\xff\xff0\x01\x00\x00\x00\x00\x00\x00\x00'[16:]


IS_GRAAL = sys.implementation.name == "graalpython"


if IS_GRAAL:
    CODE = marshal.loads(GRAALPYTHON_CODE)
else:
    CODE = marshal.loads(CPYTHON_CODE)


def run_bytecode_loop():
    co = marshal.loads(CPYTHON_CODE)
    co = CODE
    exec(co)


def run_sst_execution():
    co = marshal.loads(GRAALPYTHON_CODE)
    exec(co, exec_sst=True)


def run_graalpython_execution():
    co = marshal.loads(GRAALPYTHON_CODE)
    co = CODE
    exec(co)


def measure(num):
    for i in range(num):
        if IS_GRAAL:
            # run_bytecode_loop()
            run_graalpython_execution()
        else:
            run_bytecode_loop()


def __benchmark__(num=5):
    measure(num)
