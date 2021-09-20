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
    if not IS_GRAAL and False:
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


CODESTR1 = "\n".join(["""
# import sys
def foo(): pass
len(foo.__name__)
"""] * 100)
CODESTR2 = "\n".join(["""
def bar(): pass
x = None
len([])
y = x
"""] * 100)
import pydoc_data.topics
with open(pydoc_data.topics.__file__, "r") as f:
    CODESTR3 = f.read()
TEST_WITH_BYTECODE = True # False to test with AST
if IS_GRAAL and TEST_WITH_BYTECODE:
    JUST_PYC_1 = __graalpython__.compile(CODESTR1, "1", "pyc-nocompile")
    JUST_PYC_2 = __graalpython__.compile(CODESTR1, "2", "pyc-nocompile")
    JUST_PYC_3 = __graalpython__.compile(CODESTR3, pydoc_data.topics.__file__, "pyc-nocompile")
else:
    JUST_PYC_1 = marshal.dumps(compile(CODESTR1, "1", "exec"))
    JUST_PYC_2 = marshal.dumps(compile(CODESTR2, "2", "exec"))
    JUST_PYC_3 = marshal.dumps(compile(CODESTR3, pydoc_data.topics.__file__, "exec"))


MORE_CODEOBJECTS = []
for _ in range(0, SIMULATED_FILECOUNT, 2):
    MORE_CODEOBJECTS.append(marshal.loads(JUST_PYC_1))
    MORE_CODEOBJECTS.append(marshal.loads(JUST_PYC_2))
    MORE_CODEOBJECTS.append(marshal.loads(JUST_PYC_3))


BYTECODE_FILE_DATA = []
for filename in BYTECODE_FILES:
    with open(filename, "rb") as f:
        BYTECODE_FILE_DATA.append(f.read())


def measure(num):
    for i in range(num):
        # Enable this to benchmark GraalPython code deserialization SST vs
        # bytecode. Switch out the mode in the global setup above
        # marshal.loads(JUST_PYC_1); marshal.loads(JUST_PYC_2)
        # marshal.loads(JUST_PYC_1); marshal.loads(JUST_PYC_2); marshal.loads(JUST_PYC_3)

        # Enable this to measure executing different modules in AST vs bytecode
        # exec(MORE_CODEOBJECTS[i % len(MORE_CODEOBJECTS)])

        # Enable this to measure just unmarshalling
        # marshal.loads(BYTECODE_FILE_DATA[i % len(BYTECODE_FILE_DATA)])

        # Enable this to measure just loading file data
        # with open(BYTECODE_FILES[i % len(BYTECODE_FILES)], "rb") as f:
        #     f.read()

        # Enable this to measure loading all the modules
        # with open(BYTECODE_FILES[i % len(BYTECODE_FILES)], "rb") as f:
        #     marshal.loads(f.read())

        # Enable this to measure executing different modules
        # exec(CODE[i % len(CODE)])

        # Enable this to measure unmarshalling and executing code
        # exec(marshal.loads(BYTECODE_FILE_DATA[i % len(BYTECODE_FILE_DATA)]))

        # Enable this to measure all three, reading file, loading data, and executing
        # with open(BYTECODE_FILES[i % len(BYTECODE_FILES)], "rb") as f:
        #     exec(marshal.loads(f.read()))


def __benchmark__(num=10_000):
    measure(num)


print("Benchmark file loaded")


# I've written the bytecode benchmark to simulate loading thousands of modules. I
# always execute 10_000 iterations, to make it somewhat like loading a big
# project with many pyc files, but not so large that the compiler would have time
# to compile many of the operations involved in loading code.
#
# If we compare unmarshaling a code object from a bytes that has bytecode vs one
# that has SST, we see that unmarshalling 10_000 bytecode code objects is 2-3x
# faster than unmarshalling the SST. This is for two bits of code with 300 lines
# of statements (creating functions, imports, assignments, calling
# functions). This is just loading, not running. CPython on the same two codes is
# more than 10x faster still.
#
# If we compare executing 10_000 times (cycling through the code objects so each
# call target is not executed more than 10 times), the AST interpreter is faster
# by around 20%.
#
# Loading artificial bytecode data with just dummy content (~2000 NOPs, PUSH/POP,
# LOAD/STORE instructions...), CPython is ~15x faster unmarshalling it.
#
# Just opening files and reading their contents of that artificial bytecode data
# (like the importlib would do with pyc files), CPython is ~13x faster than we
# are. For CPython, this operation is 10x slower than unmarshalling the already
# loaded bytes. For us the loading is ~8x slower than unmarshalling.
#
# Opening the files *and* loading the bytecode data yields that CPython is ~14x
# faster than us.
#
# Now, if we preload that artificial code before the benchmark and just execut
# those code objects 10_000 times, CPython is a whopping 20-30x faster than we
# are. But the numbers are so small, it's hard to say (CPython 0.006-0.008s,
# Graal 0.16-0.19s). OTOH, it's hard to argue that we would ever load more
# modules than this during some application startup. Since this models quite well
# what might happen when we load a big Python application consisting of many pyc
# files, that seems a problem.
#
# Now, combining those: Loading + executing prepared bytes: is ~25-30x slower for
# us than on CPython. Combining all three operations, CPython is 13-16x faster
# than us. The large amount of time spent loading files helps us a little to skew
# it into that ratio.
#
# The ratios don't quite add up, because doing more work also gives libgraal more
# time to optimize parts of the work. It's still interesting, since this one-shot
# loading of many pyc files is crucial for our perceived startup performance.
