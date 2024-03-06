# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

test_frame = sys._getframe(0)
test_frame_no_back = test_frame
while test_frame_no_back.f_back:
    test_frame_no_back = test_frame_no_back.f_back


class TestMisc(CPyExtTestCase):

    test_PyFrame_GetCode = CPyExtFunction(
        lambda args: args[0].f_code,
        lambda: (
            (test_frame,),
        ),
        code='''PyObject* wrap_PyFrame_GetCode(PyObject* frame) {
            return (PyObject*)PyFrame_GetCode((PyFrameObject*)frame);
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* frame"],
        callfunction="wrap_PyFrame_GetCode",
        cmpfunc=unhandled_error_compare,
    )

    test_PyFrame_GetLineNumber = CPyExtFunction(
        lambda args: args[0].f_lineno,
        lambda: (
            (test_frame,),
        ),
        code='''int wrap_PyFrame_GetLineNumber(PyObject* frame) {
            return PyFrame_GetLineNumber((PyFrameObject*)frame);
        }
        ''',
        resultspec="i",
        argspec="O",
        arguments=["PyObject* frame"],
        callfunction="wrap_PyFrame_GetLineNumber",
        cmpfunc=unhandled_error_compare,
    )

    # GR-46546 These APIs are backported from 3.11
    if sys.version_info >= (3, 11) or sys.implementation.name == 'graalpy':
        test_PyFrame_GetLasti = CPyExtFunction(
            lambda args: args[0].f_lasti,
            lambda: (
                (test_frame,),
            ),
            code='''int wrap_PyFrame_GetLasti(PyObject* frame) {
                return PyFrame_GetLasti((PyFrameObject*)frame);
            }
            ''',
            resultspec="i",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetLasti",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetBack = CPyExtFunction(
            lambda args: args[0].f_back or "NULL",
            lambda: (
                (test_frame,),
                (test_frame_no_back,),
            ),
            code='''PyObject* wrap_PyFrame_GetBack(PyObject* frame) {
                PyObject* back = (PyObject*)PyFrame_GetBack((PyFrameObject*)frame);
                if (!back)
                    return PyUnicode_FromString("NULL");
                return back;
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetBack",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetLocals = CPyExtFunction(
            lambda args: args[0].f_locals,
            lambda: (
                (test_frame,),
            ),
            code='''PyObject* wrap_PyFrame_GetLocals(PyObject* frame) {
                return PyFrame_GetLocals((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetLocals",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetGlobals = CPyExtFunction(
            lambda args: args[0].f_globals,
            lambda: (
                (test_frame,),
            ),
            code='''PyObject* wrap_PyFrame_GetGlobals(PyObject* frame) {
                return PyFrame_GetGlobals((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetGlobals",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetBuiltins = CPyExtFunction(
            lambda args: args[0].f_builtins,
            lambda: (
                (test_frame,),
            ),
            code='''PyObject* wrap_PyFrame_GetBuiltins(PyObject* frame) {
                return PyFrame_GetBuiltins((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetBuiltins",
            cmpfunc=unhandled_error_compare,
        )
