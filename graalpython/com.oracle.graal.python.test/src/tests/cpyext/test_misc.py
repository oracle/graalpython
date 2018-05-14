# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]

class TestMisc(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestMisc, self).compile_module(name)

    test_PyEllipsis_isSingleton = CPyExtFunction(
        lambda args: 1,
        lambda: (
            (...,),
        ),
        callfunction="CheckPyEllipsis",
        code="""
        static int CheckPyEllipsis(PyObject* ellipsis_singleton) {
            return ellipsis_singleton == &_Py_EllipsisObject;
        }
        """,
        resultspec="i",
        argspec="O",
        arguments=["PyObject* ellipsis_singleton"],
    )

#     test_PyEllipsis_type = CPyExtFunction(
#         lambda args: 1,
#         lambda: (
#             (...,),
#         ),
#         callfunction="CheckPyTrue",
#         code="""
#         static int CheckPyTrue(int flag) {
#             if (flag) {
#                 return ellipsis_new() == _Py_EllipsisObject;
#             } else {
#                 return PyBool_FromLong(flag) == Py_False;
#             }
#         }
#         """,
#         resultspec="i",
#         argspec="O",
#         arguments=["PyObject* ellipsisSingleton"],
#     )
