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

import ensurepip
import os
import platform
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from . import compile_module_from_string_no_import


ABI3T_SMOKE_SOURCE = r"""
#define Py_TARGET_ABI3T 0x030f0000
#include <Python.h>

static char module_token;
static PyABIInfo abi_info;

static int
exec_module(PyObject *module)
{
    int *state = PyModule_GetState(module);
    if (state == NULL) {
        return -1;
    }
    *state = 40;
    return 0;
}

static PyObject *
increment(PyObject *module, PyObject *Py_UNUSED(ignored))
{
    int *state = PyModule_GetState_DuringGC(module);
    Py_ssize_t state_size = -1;
    void *token = NULL;
    void *gc_token = NULL;

    if (PyModule_GetStateSize(module, &state_size) < 0 ||
                    PyModule_GetToken(module, &token) < 0 ||
                    PyModule_GetToken_DuringGC(module, &gc_token) < 0) {
        return NULL;
    }
    if (state == NULL || state_size != sizeof(*state) ||
                    token != &module_token || gc_token != &module_token) {
        PyErr_SetString(PyExc_AssertionError, "invalid abi3t module metadata");
        return NULL;
    }
    return PyLong_FromLong(++*state);
}

static PyObject *
from_slots(PyObject *Py_UNUSED(module), PyObject *spec)
{
    static PySlot slots[] = {
        PySlot_DATA(Py_mod_abi, &abi_info),
        PySlot_END,
    };
    return PyModule_FromSlotsAndSpec(slots, spec);
}

static PyMethodDef methods[] = {
    {"increment", increment, METH_NOARGS,
        "increment($module, /)\n"
        "--\n\n"
        "Increment the module state."},
    {"from_slots", from_slots, METH_O,
        "from_slots($module, spec, /)\n"
        "--\n\n"
        "Create a module from slots."},
    {NULL, NULL, 0, NULL},
};

static PyABIInfo abi_info = {
    1,
    0,
    PyABIInfo_STABLE | PyABIInfo_FREETHREADING_AGNOSTIC,
    PY_VERSION_HEX,
    0x030f0000,
};

static PyModuleDef_Slot legacy_slots[] = {
    {2, exec_module},
    {0, NULL},
};

PyMODEXPORT_FUNC
PyModExport_abi3t_smoke(PyObject *Py_UNUSED(spec))
{
    static PySlot slots[] = {
        PySlot_DATA(Py_mod_abi, &abi_info),
        PySlot_STATIC_DATA(Py_mod_methods, methods),
        PySlot_SIZE(Py_mod_state_size, sizeof(int)),
        PySlot_DATA(Py_mod_token, &module_token),
        PySlot_DATA(Py_mod_slots, legacy_slots),
        {.sl_id=0xfbad, .sl_flags=PySlot_OPTIONAL},
        PySlot_END,
    };
    return slots;
}
"""


@unittest.skipUnless(sys.implementation.name == "graalpy", "requires GraalPy")
class TestAbi3t(unittest.TestCase):
    def test_option_exposed(self):
        code = "import __graalpython__; print(__graalpython__.abi3t_enabled)"
        for enabled, expected in ((False, "False"), (True, "True")):
            proc = subprocess.run(
                [
                    sys.executable,
                    "--experimental-options=true",
                    f"--python.EnableAbi3t={str(enabled).lower()}",
                    "-S",
                    "-c",
                    code,
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            self.assertEqual(proc.stdout.strip(), expected)

    def test_pip_abi3t_tags(self):
        pip_wheel = next(Path(ensurepip.__file__).parent.joinpath("_bundled").glob("pip-*.whl"))
        code = (
            "import sys; from pip._vendor.packaging import tags; "
            "values = set(tags.sys_tags()); "
            "current = f'cp{sys.version_info[0]}{sys.version_info[1]}'; "
            "print(any(tag.interpreter == current and tag.abi == 'abi3t' for tag in values)); "
            "print(any(tag.abi == 'abi3' for tag in values)); "
            "print(any(tag.interpreter == current and tag.abi == current + 't' for tag in values))"
        )
        env = dict(os.environ, PYTHONPATH=str(pip_wheel))
        expected_tags = {
            False: ("False", "False", "False"),
            True: ("True", "False", "False"),
        }
        for enabled, expected in expected_tags.items():
            proc = subprocess.run(
                [
                    sys.executable,
                    "--experimental-options=true",
                    f"--python.EnableAbi3t={str(enabled).lower()}",
                    "-S",
                    "-c",
                    code,
                ],
                env=env,
                capture_output=True,
                text=True,
                check=True,
            )
            self.assertEqual(tuple(proc.stdout.strip().splitlines()), expected)

    @unittest.skipUnless(sys.platform == "linux", "requires Linux extension wheel tags")
    def test_pip_abi3t_wheel_install(self):
        module_dir = Path(compile_module_from_string_no_import(ABI3T_SMOKE_SOURCE, "abi3t_smoke"))
        extension = next(module_dir.glob("abi3t_smoke*.so"))
        pip_wheel = next(Path(ensurepip.__file__).parent.joinpath("_bundled").glob("pip-*.whl"))
        machine = platform.machine().lower().replace("-", "_")
        interpreter_tag = f"cp{sys.version_info[0]}{sys.version_info[1]}"
        wheel_name = f"abi3t_smoke-0.0.0-{interpreter_tag}-abi3.abi3t-linux_{machine}.whl"
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            wheel_path = tmp_path / wheel_name
            dist_info = "abi3t_smoke-0.0.0.dist-info"
            wheel_metadata = (
                "Wheel-Version: 1.0\n"
                "Generator: graalpython-abi3t-test\n"
                "Root-Is-Purelib: false\n"
                f"Tag: {interpreter_tag}-abi3.abi3t-linux_{machine}\n"
            )
            metadata = "Metadata-Version: 2.1\nName: abi3t-smoke\nVersion: 0.0.0\n"
            with zipfile.ZipFile(wheel_path, "w", compression=zipfile.ZIP_DEFLATED) as wheel:
                wheel.write(extension, "abi3t_smoke.so")
                wheel.writestr(f"{dist_info}/WHEEL", wheel_metadata)
                wheel.writestr(f"{dist_info}/METADATA", metadata)
                wheel.writestr(f"{dist_info}/RECORD", "")

            install_dir = tmp_path / "installed"
            install_dir.mkdir()
            env = dict(os.environ, PYTHONPATH=str(pip_wheel))
            install = subprocess.run(
                [
                    sys.executable,
                    "--experimental-options=true",
                    "--python.EnableAbi3t=true",
                    "-S",
                    "-m",
                    "pip",
                    "install",
                    "--no-index",
                    "--no-deps",
                    "-vv",
                    "--target",
                    str(install_dir),
                    str(wheel_path),
                ],
                env=env,
                capture_output=True,
                text=True,
            )
            self.assertEqual(install.returncode, 0, install.stderr)
            import_env = dict(os.environ, PYTHONPATH=str(install_dir))
            proc = subprocess.run(
                [
                    sys.executable,
                    "--experimental-options=true",
                    "--python.EnableAbi3t=true",
                    "-S",
                    "-c",
                    "import abi3t_smoke as m; print(m.increment())",
                ],
                env=import_env,
                capture_output=True,
                text=True,
                check=True,
            )
            self.assertEqual(proc.stdout.strip(), "41")

    def test_modexport_smoke(self):
        module_dir = compile_module_from_string_no_import(ABI3T_SMOKE_SOURCE, "abi3t_smoke")
        env = dict(os.environ)
        env["PYTHONPATH"] = module_dir
        code = (
            "import importlib.machinery; import abi3t_smoke as m; "
            "n = m.from_slots(importlib.machinery.ModuleSpec('from_slots', None)); "
            "assert m.increment.__name__ == 'increment'; "
            "assert m.increment.__module__ == 'abi3t_smoke'; "
            "assert m.increment.__doc__ == 'Increment the module state.'; "
            "assert m.increment.__text_signature__ == '($module, /)'; "
            "print(m.increment(), m.increment(), n.__name__)"
        )
        proc = subprocess.run(
            [sys.executable, "--experimental-options=true", "--python.EnableAbi3t=true", "-S", "-c", code],
            env=env,
            capture_output=True,
            text=True,
            check=True,
        )
        self.assertEqual(proc.stdout.strip(), "41 42 from_slots")
