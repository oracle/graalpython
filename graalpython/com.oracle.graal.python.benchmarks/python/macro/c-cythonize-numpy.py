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

import os


_old_pip_no_binary = os.environ.get("PIP_NO_BINARY")
_old_pip_no_cache_dir = os.environ.get("PIP_NO_CACHE_DIR")
_old_cython_compile_all = os.environ.get("CYTHON_COMPILE_ALL")
os.environ["PIP_NO_BINARY"] = ",".join(filter(None, (_old_pip_no_binary, "cython")))
os.environ["PIP_NO_CACHE_DIR"] = "true"
# Cython only enables its default set of compiled modules automatically on
# CPython. Compile all of them so that GraalPy exercises the accelerated
# compiler instead of silently installing Cython's pure Python wheel.
os.environ["CYTHON_COMPILE_ALL"] = "true"
try:
    ensure_packages(cython="3.2.4", setuptools="77.0.1")
finally:
    if _old_pip_no_binary is None:
        del os.environ["PIP_NO_BINARY"]
    else:
        os.environ["PIP_NO_BINARY"] = _old_pip_no_binary
    if _old_pip_no_cache_dir is None:
        del os.environ["PIP_NO_CACHE_DIR"]
    else:
        os.environ["PIP_NO_CACHE_DIR"] = _old_pip_no_cache_dir
    if _old_cython_compile_all is None:
        del os.environ["CYTHON_COMPILE_ALL"]
    else:
        os.environ["CYTHON_COMPILE_ALL"] = _old_cython_compile_all

import importlib.machinery
import shutil
import subprocess
import sys
import tempfile

from Cython.Build import cythonize
from Cython.Compiler import Scanning


NUMPY_VERSION = "2.2.6"
NUMPY_REPO_URL = os.environ.get("NUMPY_REPO_URL") or "https://github.com/numpy/numpy.git"
RANDOM_DIR = os.path.join("numpy", "random")
CYTHON_SOURCES = [
    os.path.join(RANDOM_DIR, name)
    for name in (
        "_bounded_integers.pyx",
        "_common.pyx",
        "_mt19937.pyx",
        "_philox.pyx",
        "_pcg64.pyx",
        "_sfc64.pyx",
        "bit_generator.pyx",
        "_generator.pyx",
        "mtrand.pyx",
    )
]

WORK_DIR = None
NUMPY_SOURCE_DIR = None
CYTHON_BUILD_DIR = None


def _is_extension_module(module):
    module_file = getattr(module, "__file__", "")
    return any(module_file.endswith(suffix) for suffix in importlib.machinery.EXTENSION_SUFFIXES)


def _render_template(source, output):
    tempita_script = os.path.join(NUMPY_SOURCE_DIR, "numpy", "_build_utils", "tempita.py")
    subprocess.run([sys.executable, tempita_script, source, "-o", output], check=True)


def __setup__():
    global WORK_DIR, NUMPY_SOURCE_DIR, CYTHON_BUILD_DIR

    if not _is_extension_module(Scanning):
        raise RuntimeError(f"Cython.Compiler.Scanning is not a native extension: {Scanning.__file__}")

    WORK_DIR = tempfile.mkdtemp(prefix="graalpy-c-cythonize-numpy-")
    NUMPY_SOURCE_DIR = os.path.join(WORK_DIR, "numpy")
    CYTHON_BUILD_DIR = os.path.join(WORK_DIR, "cython-build")

    subprocess.run(
        [
            "git",
            "clone",
            "--depth",
            "1",
            "--branch",
            f"v{NUMPY_VERSION}",
            "--single-branch",
            NUMPY_REPO_URL,
            NUMPY_SOURCE_DIR,
        ],
        check=True,
    )

    for extension in (".pyx", ".pxd"):
        template = os.path.join(NUMPY_SOURCE_DIR, RANDOM_DIR, f"_bounded_integers{extension}.in")
        output = os.path.join(NUMPY_SOURCE_DIR, RANDOM_DIR, f"_bounded_integers{extension}")
        _render_template(template, output)


def __benchmark__():
    old_cwd = os.getcwd()
    try:
        os.chdir(NUMPY_SOURCE_DIR)
        return cythonize(
            CYTHON_SOURCES,
            build_dir=CYTHON_BUILD_DIR,
            compiler_directives={
                "freethreading_compatible": True,
                "language_level": 3,
            },
            force=True,
            nthreads=0,
            quiet=True,
        )
    finally:
        os.chdir(old_cwd)


def __teardown__():
    if WORK_DIR is not None:
        shutil.rmtree(WORK_DIR)
