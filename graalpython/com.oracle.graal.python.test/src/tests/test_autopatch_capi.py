# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import tempfile
import textwrap

if sys.implementation.name == 'graalpy':
    import autopatch_capi


    def check_autopatched(source, expected):
        source = textwrap.dedent(source).rstrip()
        expected = textwrap.dedent(expected).rstrip()
        with tempfile.NamedTemporaryFile('r+') as f:
            f.write(source)
            f.flush()
            autopatch_capi.auto_patch(f.name, False)
            f.seek(0)
            actual = f.read()
            assert actual == expected, f"Autopatch didn't make expected changes. Expected:\n{expected}\nActual:{actual}"


    def test_replace_field_access():
        check_autopatched(
            'PyObject* type = obj->ob_type;',
            'PyObject* type = Py_TYPE(obj);',
        )
        check_autopatched(
            'PyObject* type = ((PyObject*)obj)->ob_type;',
            'PyObject* type = Py_TYPE(((PyObject*)obj));',
        )
        check_autopatched(
            'const char* name = obj->ob_type->tp_name;',
            'const char* name = Py_TYPE(obj)->tp_name;',
        )
        check_autopatched(
            'foo = objs[0]->ob_type;',
            'foo = Py_TYPE(objs[0]);',
        )
        check_autopatched(
            'return obj->ob_type;',
            'return Py_TYPE(obj);',
        )
        check_autopatched(
            'return (PyObject*)obj->ob_type;',
            'return (PyObject*)Py_TYPE(obj);',
        )
        check_autopatched(
            'return ((PyObject*)obj)->ob_type;',
            'return Py_TYPE(((PyObject*)obj));',
        )
        check_autopatched(
            '(PyObject*)function_call(a, b, c(0))->ob_type->ob_base;',
            '(PyObject*)Py_TYPE(function_call(a, b, c(0)))->ob_base;',
        )
        check_autopatched(
            '''
            #if SOME_MACRO
            obj->ob_type->tp_free(self);
            #else
            obj->ob_type->tp_free(self);
            #endif
            ''',
            '''
            #if SOME_MACRO
            Py_TYPE(obj)->tp_free(self);
            #else
            Py_TYPE(obj)->tp_free(self);
            #endif
            ''',
        )
