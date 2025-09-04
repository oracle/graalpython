# Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import io
import mmap
import sys
import tempfile

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare


def create_and_map_file():
    tmp = tempfile.mktemp(prefix="pymmap_")
    with open(tmp, "w") as f:
        f.write("hello, world")
    f = open(tmp, "r")
    return mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)


def _reference_buffer(args):
    if sys.implementation.name == 'graalpy' and __graalpython__.posix_module_backend() == 'java':
        # Cannot get mmap pointer under emulated backend
        raise io.UnsupportedOperation
    return b"hello, world"


class TestPyMmap(CPyExtTestCase):
    test_buffer = CPyExtFunction(
        _reference_buffer,
        lambda: (
            (create_and_map_file(),),
        ),
        code="""
        static PyObject* get_mmap_buf(PyObject* mmapObj) {
            Py_buffer buf;
            Py_ssize_t len, i;
            char* data = NULL;
            if (PyObject_GetBuffer(mmapObj, &buf, PyBUF_SIMPLE) < 0) {
                return NULL;
            }
            len = buf.len;
            data = (char*) malloc(sizeof(char)*len);
            for (i=0; i < buf.len; i++) {
                data[i] = ((char *) buf.buf)[i];
            }
            return PyBytes_FromStringAndSize(data, len);
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* mmapObj"],
        callfunction="get_mmap_buf",
        cmpfunc=unhandled_error_compare
    )

    # Exercises conversion to native and copying from the actual mmap pointer
    test_buffer_memcpy = CPyExtFunction(
        _reference_buffer,
        lambda: (
            (create_and_map_file(),),
        ),
        code="""
        static PyObject* get_mmap_buf(PyObject* mmapObj) {
            Py_buffer buf;
            Py_ssize_t len;
            char* data = NULL;
            if (PyObject_GetBuffer(mmapObj, &buf, PyBUF_SIMPLE)) {
                return NULL;
            }
            len = buf.len;
            data = (char*) malloc(sizeof(char)*len);
            memcpy(data, buf.buf, len);
            return PyBytes_FromStringAndSize(data, len);
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* mmapObj"],
        callfunction="get_mmap_buf",
        cmpfunc=unhandled_error_compare
    )
