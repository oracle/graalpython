# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import sys
import os

from tests.cpyext import CPyExtTestCase, CPyExtType

# synchronize with Java implementation of __graalpython__.indirect_call_tester
TYPE_INDIRECT_BOUNDARY = 1
TYPE_INDIRECT_INTEROP_CALL = 2
TYPE_INDIRECT_INTEROP = 3
TYPE_INDIRECT_BOUNDARY_UNCACHED_INTEROP = 4

# We want the code to get compiled, so that we also test compiled version of the code
NUM_ITERATIONS = 1000000 if sys.implementation.name == "graalpy" else 5

# Because of splitting, the code may not stabilize after first iteration and we may
# see some stack walks during first few iterations. With default runtime or splitting
# disabled, these tests should pass with STABILIZES_AT=1
STABILIZES_AT = int(os.environ.get('GRAALPY_TEST_INDIRECT_CALL_STABILIZES_AT', 10))

has_stack_walk_check = False
if sys.implementation.name == "graalpy":
    result = __graalpython__.was_stack_walk(False)
    if result is None:
        print("NOTE: assertions are not enabled; test_indirect_call cannot check for "
              "repeated stack walks and will perform only basic sanity checks")
    else:
        has_stack_walk_check = True

def was_stack_walk(new_value):
    if has_stack_walk_check:
        return __graalpython__.was_stack_walk(new_value)
    return False


IndirectCApiCallTester = CPyExtType(
    'IndirectCApiCallTester',
    code='''
    static PyObject* IndirectCApiCallTester_call(PyObject* self, PyObject *callable) {
        return PyObject_CallNoArgs(callable);
    }
    ''',
    tp_methods='''{"call", (PyCFunction)IndirectCApiCallTester_call, METH_O, ""}''',
)

# === capturing frame

def check_get_frame_no_deopt(forwarding_call, *args):
    def callee():
        return sys._getframe(1)

    def fun(x):
        known_local = x
        return forwarding_call(callee, *args)

    for i in range(NUM_ITERATIONS):
        assert fun(i * 2).f_locals['known_local'] == i * 2
        if i <= STABILIZES_AT:
            # just reset the flag
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False), f"{i=}"

def test_capi_get_frame():
    check_get_frame_no_deopt(IndirectCApiCallTester().call)

def check_get_frame_indirect_call_tester(indirect_call_type):
    if sys.implementation.name == "graalpy":
        check_get_frame_no_deopt(__graalpython__.indirect_call_tester, indirect_call_type)
    else:
        check_get_frame_no_deopt(IndirectCApiCallTester().call)

def test_truffle_boundary_call_get_frame():
    check_get_frame_indirect_call_tester(TYPE_INDIRECT_BOUNDARY)

def test_interop_call_get_frame():
    check_get_frame_indirect_call_tester(TYPE_INDIRECT_INTEROP_CALL)

def test_interop_get_frame():
    check_get_frame_indirect_call_tester(TYPE_INDIRECT_INTEROP)

def test_truffle_boundary_interop_call_get_frame():
    check_get_frame_indirect_call_tester(TYPE_INDIRECT_BOUNDARY_UNCACHED_INTEROP)

class Var:
    def __init__(self, name):
        self.name = name
    def __str__(self):
        self.escape_frame = sys._getframe(1)
        return str(self.escape_frame.f_locals[self.name])

def test_format_with_get_frame_in_str():
    secret_var = 42
    for i in range(NUM_ITERATIONS):
        assert "{}".format(Var("secret_var")) == "42"
        if i <= STABILIZES_AT:
            # just reset the flag
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False), f"{i=}"

escape_frame = None
class AttrGetter:
    def __getattribute__(self, name):
        global escape_frame
        escape_frame = sys._getframe(1)
        return escape_frame.f_locals[name]


def test_obj_with_get_frame_in_getattribute():
    secret_var = 42
    for i in range(NUM_ITERATIONS):
        assert AttrGetter().secret_var == 42
        if i <= STABILIZES_AT:
            # just reset the flag
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False), f"{i=}"

# === exception state

def check_get_ex_no_deopt(forwarding_call, *args):
    def callee1():
        return sys.exc_info()[1]

    def callee2():
        return callee1()

    def fun(msg):
        try:
            raise IndexError(str(msg))
        except IndexError as e:
            return forwarding_call(callee2, *args)

    def check_ex(msg):
        ex = fun(str(msg))
        assert type(ex) == IndexError, f"{ex=}, {msg=}"
        assert str(ex) == str(msg), f"{ex=}, {msg=}"

    for i in range(NUM_ITERATIONS):
        check_ex(i*2)
        if i <= max(STABILIZES_AT, 2):
            # uncached interpreter always passes exception down, so on first call we do not walk the stack
            # and do not invalidate the assumptions. We can proactively walk the stack just to invalidate
            # the assumptions if we ever find that this is a problem.
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False)

def test_capi_get_ex():
    check_get_ex_no_deopt(IndirectCApiCallTester().call)

def check_get_ex_indirect_call_tester(indirect_call_type):
    pass
    if sys.implementation.name == "graalpy":
        check_get_ex_no_deopt(__graalpython__.indirect_call_tester, indirect_call_type)
    else:
        check_get_ex_no_deopt(IndirectCApiCallTester().call)

def test_truffle_boundary_call_get_ex():
    check_get_ex_indirect_call_tester(TYPE_INDIRECT_BOUNDARY)

def test_interop_call_get_ex():
    check_get_ex_indirect_call_tester(TYPE_INDIRECT_INTEROP_CALL)

def test_truffle_boundary_interop_call_get_ex():
    check_get_ex_indirect_call_tester(TYPE_INDIRECT_BOUNDARY_UNCACHED_INTEROP)

class ExStr:
    def __str__(self):
        self.escape_ex = sys.exc_info()[1]
        return str(self.escape_ex)

def test_format_with_get_ex_in_str():
    for i in range(NUM_ITERATIONS):
        try:
            raise IndexError(str(i))
        except:
            assert "{}".format(ExStr()) == str(i)
        if i <= max(STABILIZES_AT, 2):
            # see similar code above
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False)

escape_ex = None
class AttrGetterFromEx:
    def __getattribute__(self, name):
        global escape_ex
        escape_ex = sys.exc_info()[1]
        return str(escape_ex)


def test_obj_with_get_ex_in_getattribute():
    for i in range(NUM_ITERATIONS):
        try:
            raise IndexError(str(i))
        except:
            assert AttrGetterFromEx().dummy_attribute == str(i)
        if i <= max(STABILIZES_AT, 2):
            # see similar code above
            was_stack_walk(False)
        else:
            assert not was_stack_walk(False)
