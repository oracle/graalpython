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
import marshal
import os
import sys


IS_GRAAL = sys.implementation.name == "graalpython"
DIR0 = os.path.dirname(__file__)


if IS_GRAAL:
    get_code = lambda n,s: __graalpython__.compile(s, n, "pyc")
else:
    def get_code(n,s):
        c = compile(s,n,"exec")
        import dis
        dis.dis(c)
        return c


CODE = get_code("bench.py", """
import sys
def foo():
  pass
len(sys.__name__)
print(sys, flush=False)
pass
""")

# pollute the profile of the bytecode loop
exec(CODE)


# We're making sure that each code object is a separate call target. This is to
# simulate we are loading SIMULATED_FILECOUNT modules, because each module is
# potentially a lot of code but only gets executed once
RETURN_NONE = [100, 0, 83, 0]
BYTECODE_COUNT = 200
SIMULATED_FILECOUNT = 1000
CODE = []


def generate_code(name, code_to_repeat, **kwargs):
    filename = os.path.join(DIR0, name)
    if not IS_GRAAL and True:
        # generate our code files only with cpython
        def foo(): pass
        code = foo.__code__
        cnt = int(2 * BYTECODE_COUNT / len(code_to_repeat))
        newcode = code.replace(co_code=bytes(bytearray(code_to_repeat * cnt + RETURN_NONE)), **kwargs)
        with open(filename, "wb") as f:
            marshal.dump(newcode, f)
    # pollute the profile for the bytecode loop
    with open(filename, "rb") as f:
        exec(marshal.load(f))
    return filename


BYTECODE_FILES = [
    generate_code("nop", [9, 0]),
    generate_code("pushpop", [100, 0, 1, 0]),
    generate_code("negative_one", [100, 1, 11, 0, 1, 0], co_consts=(None, 1,)),
    generate_code("load_fast", [
        100, 0, # load None
        125, 0, # store fast 0
        124, 0, # load fast 0
        1, 0, # pop top
    ], co_varnames=('x',)),
]


for _ in range(0, SIMULATED_FILECOUNT, len(BYTECODE_FILES)):
    for filename in BYTECODE_FILES:
        with open(filename, "rb") as f:
            CODE.append(marshal.load(f))


def measure(num):
    for i in range(num):
        exec(CODE[i % SIMULATED_FILECOUNT])


def __benchmark__(num=1_000_000):
    measure(num)
