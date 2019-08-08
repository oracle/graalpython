# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import os
from distutils.core import setup, Extension
from distutils.sysconfig import get_config_var

__dir__ = __file__.rpartition("/")[0]
cflags_warnings = ["-Wno-int-to-pointer-cast", "-Wno-int-conversion", "-Wno-incompatible-pointer-types-discards-qualifiers", "-Wno-pointer-type-mismatch"]
libpython_name = "libpython"

verbosity = '--verbose' if sys.flags.verbose else '--quiet'
darwin_native = sys.platform == "darwin" and sys.graal_python_platform_id == "native"
so_ext = get_config_var("EXT_SUFFIX")
capi_module = os.path.abspath(os.path.join(sys.graal_python_cext_home, libpython_name + so_ext))
cflags = cflags_warnings + (["-lbz2", "-lpolyglot-mock", "-lpython" + so_ext, "-Wl,-rpath=" + capi_module] if darwin_native else [])


def __one_file(name, subdir):
    return Extension(name, sources=[os.path.join(__dir__, subdir, name + ".c")], extra_compile_args=cflags)


builtin_exts = (
    __one_file("_bz2", "modules"),
    __one_file("_cpython_sre", "modules"),
    __one_file("_cpython_unicodedata", "modules"),
    __one_file("_memoryview", "modules"),
    __one_file("_mmap", "modules"),
    __one_file("_struct", "modules"),
)


def build_libpython():
    src_dir = os.path.join(__dir__, "src")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    module = Extension(libpython_name,
                       sources=files,
                       # Darwin's linker is pretty pedantic so we need to avoid any unresolved syms. To do so, we use
                       # 'libpolyglot-mock' that provides definitions for polyglot functions and we define an install name for this lib.
                       extra_compile_args=cflags_warnings + (["-lpolyglot-mock", "-Wl,-install_name=@rpath/" + libpython_name + so_ext] if darwin_native else [])
                       )
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % sys.graal_python_cext_home, "clean"]
    setup(
        script_name='setup' + libpython_name,
        script_args=args,
        name=libpython_name,
        version='1.0',
        description="Graal Python's C API",
        ext_modules=[module],
    )

def build_builtin_exts():
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % sys.graal_python_cext_module_home, "clean"]
    for ext in builtin_exts:
        res = setup(
            script_name='setup_' + ext.name,
            script_args=args,
            name=ext.name,
            version='1.0',
            description="Graal Python builtin native module '%s'" % ext.name,
            ext_modules=[ext]
        )
        print("RES for %s = %r" % (ext.name, res))


def build():
    build_libpython()
    build_builtin_exts()


if __name__ == "__main__":
    build()
