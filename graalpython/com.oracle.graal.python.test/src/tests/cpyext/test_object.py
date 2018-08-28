# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtType, CPyExtTestCase, CPyExtFunction
__dir__ = __file__.rpartition("/")[0]


class AttroClass(object):
    def __getattribute__(self, key):
        if key == "foo":
            return "foo"
        else:
            return object.__getattribute__(self, key)


class TestObject(object):
    def test_add(self):
        TestAdd = CPyExtType("TestAdd",
                             """
                             PyObject* test_add(PyObject* a, PyObject* b) {
                                 return PyTuple_Pack(2, a, b);
                             }
                             """,
                             nb_add="test_add"
        )
        tester = TestAdd()
        assert tester + 12 == (tester, 12)

    def test_pow(self):
        TestPow = CPyExtType("TestPow",
                             """
                             PyObject* test_pow(PyObject* a, PyObject* b, PyObject* c) {
                                 return PyTuple_Pack(3, a, b, c);
                             }
                             """,
                             nb_power="test_pow"
        )
        tester = TestPow()
        assert tester ** 12 == (tester, 12, None), tester ** 12
        assert 12 ** tester == (12, tester, None), 12 ** tester
        assert pow(tester, 48, 2) == (tester, 48, 2), pow(tester, 48, 2)
        assert pow(48, tester, 2) == (48, tester, 2), pow(48, tester, 2)

    def test_int(self):
        TestInt = CPyExtType("TestInt",
                             """
                             PyObject* test_int(PyObject* self) {
                                 return PyLong_FromLong(42);
                             }
                             """,
                             nb_int="test_int"
        )
        tester = TestInt()
        assert int(tester) == 42

    def test_index(self):
        TestIndex = CPyExtType("TestIndex",
                             """
                             PyObject* test_index(PyObject* self) {
                                 return PyLong_FromLong(1);
                             }
                             """,
                             nb_index="test_index"
        )
        tester = TestIndex()
        assert [0, 1][tester] == 1

    def test_getattro(self):
        return  # TODO: not working yet
        # XXX: Cludge to get type into C
        sys.modules["test_getattro_AttroClass"] = AttroClass
        try:
            TestInt = CPyExtType("TestGetattro",
                                 """
                                 """,
                                 ready_code="""
                                 PyObject* AttroClass = PyDict_GetItemString(PyImport_GetModuleDict(), "test_getattro_AttroClass");
                                 TestGetattroType.tp_getattro = ((PyTypeObject*)AttroClass)->tp_getattro;
                                 """
            )
        finally:
            del sys.modules["test_getattro_AttroClass"]
        tester = TestInt()
        assert tester.foo == "foo"

    def test_dict(self):
        TestDict = CPyExtType("TestDict",
                             """static PyObject* custom_dict = NULL;
                             static PyObject* get_dict(PyObject* self, PyObject* kwargs) {
                                 Py_INCREF(custom_dict);
                                 return custom_dict;
                             }
                             """,
                             ready_code="""
                                 custom_dict = PyDict_New();
                                 PyDict_SetItemString(custom_dict, "hello", PyUnicode_FromString("first custom property"));
                                 TestDictType.tp_dict = custom_dict;
                             """,
                             post_ready_code="""
                                 PyDict_SetItemString(TestDictType.tp_dict, "world", PyUnicode_FromString("second custom property"));
                             """,
                             tp_methods='{"get_dict", get_dict, METH_NOARGS, ""}'
        )
        tester = TestDict()
        assert tester.hello == "first custom property"
        assert tester.world == "second custom property"
        assert "hello" in tester.get_dict().keys() and "world" in tester.get_dict().keys(), "was: %s" % tester.get_dict().keys()
        tester.get_dict()["extra"] = "blah"
        assert tester.extra == "blah"


class TestObjectFunctions(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    test_PyCallable_Check = CPyExtFunction(
        lambda args: callable(args[0]),
        lambda: (
            (len,),
            (sum,),
            (int,),
            ("hello",),
            (3,),
            (None,),
        ),
        arguments=["PyObject* callable"],
        resultspec="i",
        argspec="O",
    )
