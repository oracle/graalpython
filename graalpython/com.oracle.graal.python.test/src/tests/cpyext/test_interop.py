# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

from unittest import TestCase, skipUnless

from . import CPyExtType, GRAALPYTHON

if GRAALPYTHON:
    import java
    from __graalpython__ import is_native
else:
    is_native = False


@skipUnless(GRAALPYTHON and not is_native, "requires GraalPy Java interop")
class TestInteropBuffer(TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader = CPyExtType(
            "InteropBufferReader",
            """
            static PyObject* read_buffer(PyObject* self, PyObject* obj) {
                Py_buffer view;
                if (PyObject_GetBuffer(obj, &view, PyBUF_SIMPLE) < 0) {
                    return NULL;
                }
                PyObject* result = PyBytes_FromStringAndSize((const char*)view.buf, view.len);
                PyBuffer_Release(&view);
                return result;
            }
            """,
            tp_methods='{"read_buffer", read_buffer, METH_O, ""}',
        )()

    def setUp(self):
        self.buffer = java.type("java.nio.ByteBuffer").allocate(4)
        for i, value in enumerate(b"test"):
            self.buffer.put(i, value)

    def test_getbuffer(self):
        self.assertRaises(TypeError, self.reader.read_buffer, self.buffer)

    def test_getbuffer_memoryview(self):
        with memoryview(self.buffer) as view:
            # memoryview supports interop objects, but sending them to native causes a copy with a warning
            self.assertEqual(self.reader.read_buffer(view), b"test")
