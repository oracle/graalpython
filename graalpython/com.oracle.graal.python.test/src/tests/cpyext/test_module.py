# Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]

ModuleType = type(sys)


class SubModuleType(ModuleType):
    pass

def _reference_add_object(args):
    if not isinstance(args[0], ModuleType):
        raise TypeError
    args[0].__dict__[args[1]] = args[2]
    return 0

class TestPyModule(CPyExtTestCase):

    test_PyModule_Check = CPyExtFunction(
        lambda args: isinstance(args[0], ModuleType),
        lambda: (
            (sys,),
            (ModuleType("hello"),),
            (SubModuleType("subhello"),),
            ("not a module",),
            (3,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
    )

    test_PyModule_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is ModuleType,
        lambda: (
            (sys,),
            (ModuleType("hello"),),
            (SubModuleType("subhello"),),
            ("not a module",),
            (3,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
    )

    test_PyModule_GetState = CPyExtFunction(
        lambda args: "Hello, world!",
        lambda: (
            ("Hello, world!", ),
        ),
        code='''
        typedef struct {
            PyObject *content;
        } test_module_state_t;

        static PyModuleDef test_module_def = {
            PyModuleDef_HEAD_INIT,
            "DummyModule",
            "This is a dummy module.",
            sizeof(test_module_state_t),
            NULL, NULL, NULL, NULL, NULL
        };

        static PyObject* wrap_PyModule_GetState(PyObject* arg) {
            PyObject* module = PyModule_Create(&test_module_def);
            test_module_state_t* module_state = (test_module_state_t*) PyModule_GetState(module);
            Py_INCREF(arg);
            module_state->content = arg;
            return ((test_module_state_t*) PyModule_GetState(module))->content;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* arg"],
        callfunction="wrap_PyModule_GetState",
        cmpfunc=unhandled_error_compare
    )

    test_PyModule_GetName = CPyExtFunction(
        lambda args: args[0].__name__,
        lambda: (
            (sys,),
            (ModuleType("hello"),),
            (SubModuleType("subhello"),),
        ),
        resultspec="s",
        argspec='O',
        arguments=["PyObject* object"],
    )

    test_PyModule_GetNameObject = CPyExtFunction(
        lambda args: args[0].__name__,
        lambda: (
            (sys,),
            (ModuleType("hello"),),
            (SubModuleType("subhello"),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* object"],
    )
        
    test_PyModule_AddObject = CPyExtFunction(
        _reference_add_object,
        lambda: (
            (1, "testAddObject", None),
            (ModuleType("hello"), "testAddObject", None),
            (ModuleType("hello"), "testAddObject", "a"),
            (SubModuleType("subhello"), "testAddObject", "a"),
        ),
        code='''
        static int wrap_PyModule_AddObject(PyObject* m, const char* k, PyObject* v) {
            Py_INCREF(v); // the reference will be stolen
            return PyModule_AddObject(m, k, v);
        }
        ''',
        resultspec="i",
        argspec='OsO',
        arguments=["PyObject* m", "const char* k", "PyObject* v"],
        callfunction="wrap_PyModule_AddObject",
        cmpfunc=unhandled_error_compare
    )
        
    test_PyModule_AddObjectRef = CPyExtFunction(
        _reference_add_object,
        lambda: (
            (1, "testAddObject", None),
            (ModuleType("hello"), "testAddObject", None),
            (ModuleType("hello"), "testAddObject", "a"),
            (SubModuleType("subhello"), "testAddObject", "a"),
        ),
        resultspec="i",
        argspec='OsO',
        arguments=["PyObject* m", "const char* k", "PyObject* v"],
        cmpfunc=unhandled_error_compare
    )
