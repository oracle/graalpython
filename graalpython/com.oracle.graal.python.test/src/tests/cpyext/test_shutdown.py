# Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import os
import signal
import subprocess
import tempfile
import textwrap
from pathlib import Path

import sys
from unittest import skipIf
from tests import compile_module_from_string
from tests.util import skip_if_sandboxed

GRAALPY = sys.implementation.name == 'graalpy'

DIR = Path(__file__).parent
MODULE_PATH = DIR / 'module_with_native_destructor.py'
ENV = dict(os.environ)
ENV['PYTHONPATH'] = str(DIR.parent.parent)
ARGS = []
if GRAALPY:
    ARGS = ['--experimental-options', f'--log.file={os.devnull}', '--python.EnableDebuggingBuiltins']
    if not __graalpython__.is_native:
        ARGS += [f'--vm.Djdk.graal.LogFile={os.devnull}']
        ARGS += [f'--vm.Dpython.EnableBytecodeDSLInterpreter={str(__graalpython__.is_bytecode_dsl_interpreter).lower()}']
COMMAND = [sys.executable, *ARGS, str(MODULE_PATH)]


# Test that running Py_DECREF in native global destructor doesn't crash
def test_normal_exit():
    subprocess.run(COMMAND, check=True, env=ENV)


def test_sigterm():
    proc = subprocess.Popen([*COMMAND, "sleep"], env=ENV, stdout=subprocess.PIPE)
    expected = b'sleeping\n'
    assert proc.stdout.read(len(expected)) == expected
    proc.terminate()
    assert proc.wait() in [-signal.SIGTERM, 128 + signal.SIGTERM]


@skip_if_sandboxed("Needs native extension support in sandboxed runs")
@skipIf(not GRAALPY, "GraalPy-only native weakref shutdown test")
def test_native_weakref_shutdown_with_c_retained_object():
    module = compile_module_from_string(textwrap.dedent("""
        #include "Python.h"
        #include "structmember.h"
        #include <stddef.h>
        #include <stdio.h>
        #include <stdlib.h>

        typedef struct {
            PyObject_HEAD
            PyObject *weakreflist;
        } NativeWeakRefObject;

        static PyObject *kept_alive;

        static void write_marker(const char *contents) {
            const char *marker_path = getenv("GR50212_DEALLOC_MARKER");
            if (marker_path != NULL) {
                FILE *marker = fopen(marker_path, "w");
                if (marker != NULL) {
                    fputs(contents, marker);
                    fclose(marker);
                }
            }
        }

        static void NativeWeakRef_dealloc(NativeWeakRefObject *self) {
            write_marker("deallocated\\n");
            if (self->weakreflist != NULL) {
                PyObject_ClearWeakRefs((PyObject *)self);
            }
            Py_TYPE(self)->tp_free((PyObject *)self);
        }

        static PyTypeObject NativeWeakRefType = {
            PyVarObject_HEAD_INIT(NULL, 0)
            .tp_name = "native_weakref_shutdown_reproducer_gr50212.NativeWeakRef",
            .tp_basicsize = sizeof(NativeWeakRefObject),
            .tp_dealloc = (destructor)NativeWeakRef_dealloc,
            .tp_flags = Py_TPFLAGS_DEFAULT,
            .tp_new = PyType_GenericNew,
            .tp_weaklistoffset = offsetof(NativeWeakRefObject, weakreflist),
        };

        static PyObject *hold(PyObject *self, PyObject *Py_UNUSED(ignored)) {
            Py_CLEAR(kept_alive);
            kept_alive = PyObject_CallNoArgs((PyObject *)&NativeWeakRefType);
            if (kept_alive == NULL) {
                return NULL;
            }
            write_marker("held\\n");
            return Py_NewRef(kept_alive);
        }

        static PyMethodDef methods[] = {
            {"hold", hold, METH_NOARGS, ""},
            {NULL, NULL, 0, NULL},
        };

        static PyModuleDef module = {
            PyModuleDef_HEAD_INIT,
            "native_weakref_shutdown_reproducer_gr50212",
            "",
            -1,
            methods,
            NULL, NULL, NULL, NULL
        };

        PyMODINIT_FUNC PyInit_native_weakref_shutdown_reproducer_gr50212(void) {
            if (PyType_Ready(&NativeWeakRefType) < 0) {
                return NULL;
            }
            PyObject *m = PyModule_Create(&module);
            if (m == NULL) {
                return NULL;
            }
            Py_INCREF(&NativeWeakRefType);
            if (PyModule_AddObject(m, "NativeWeakRef", (PyObject *)&NativeWeakRefType) < 0) {
                Py_DECREF(&NativeWeakRefType);
                Py_DECREF(m);
                return NULL;
            }
            return m;
        }
        """), "native_weakref_shutdown_reproducer_gr50212")
    module_dir = str(Path(module.__file__).parent)
    args = [
        sys.executable,
        '--experimental-options',
        '--python.EnableDebuggingBuiltins',
    ]
    if not __graalpython__.is_native:
        args += [f'--vm.Dpython.EnableBytecodeDSLInterpreter={str(__graalpython__.is_bytecode_dsl_interpreter).lower()}']
    code = textwrap.dedent("""
        import weakref
        import native_weakref_shutdown_reproducer_gr50212

        obj = native_weakref_shutdown_reproducer_gr50212.hold()
        wr = weakref.ref(obj)
        type_wr = weakref.ref(native_weakref_shutdown_reproducer_gr50212.NativeWeakRef)
        print(type(obj).__name__, flush=True)
    """)
    env = dict(ENV)
    env["PYTHONPATH"] = os.pathsep.join([module_dir, env["PYTHONPATH"]])
    with tempfile.TemporaryDirectory() as tmpdir:
        marker = Path(tmpdir) / "deallocated"
        env["GR50212_DEALLOC_MARKER"] = str(marker)
        proc = subprocess.run([*args, '-c', code], env=env, capture_output=True, text=True, check=True)
        assert proc.stdout.strip() == "NativeWeakRef"
        assert marker.read_text() == "deallocated\n"
