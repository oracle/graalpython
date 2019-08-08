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
import logging
from distutils import sysconfig

logger = logging.getLogger(__name__)

cflags_warnings = ["-Wno-int-to-pointer-cast", "-Wno-int-conversion", "-Wno-incompatible-pointer-types-discards-qualifiers", "-Wno-pointer-type-mismatch"]

def needs_update(module, source_files):
    if not os.path.exists(module):
        logger.info("Module %s does not exist" % module)
        return True
    
    module_mtime = os.path.getmtime(module)
    for source_file in source_files:
        # if any source file is newer than the module file
        if os.path.getmtime(source_file) >= module_mtime:
            logger.info("Source file %s is newer than %s" % (source_file, module))
            return True

    return False

def compile(f, module, cflags=[]):
    ld = sysconfig.get_config_vars()["LDSHARED"]
    cmd_line = " ".join([ld, "-I" + sysconfig.get_python_inc(), "-o", module] + cflags_warnings + cflags + f)
    logger.debug(cmd_line)
    logger.info("Building %s" % module)
    res = os.system(cmd_line)
    if res:
        logger.fatal("compilation failed: '%s' returned with %r" % (cmd_line, res))
        raise BaseException


def create_if_needed(d):
    if not os.path.exists(d):
        os.makedirs(d)
    elif os.path.islink(d) or os.path.isfile(d):
        raise ValueError('Unable to create directory %r' % d)


def build_capi(cflags=[]):
    logger.info("Additional CFLAGS=%r" % cflags)
    create_if_needed(sys.graal_python_cext_module_home)
    create_if_needed(sys.graal_python_cext_home)

    darwin_native = sys.platform == "darwin" and sys.graal_python_platform_id == "native"
    so_ext = sysconfig.get_config_var("EXT_SUFFIX")

    cext_src_path = os.path.join(sys.graal_python_cext_src, "src")
    files = [os.path.abspath(os.path.join(cext_src_path, f)) for f in os.listdir(cext_src_path) if f.endswith(".c")]
    logger.debug("Found C API source files: %r" % files)
    capi_module = os.path.abspath(os.path.join(sys.graal_python_cext_home, "libpython" + so_ext))
    if needs_update(capi_module, files):
        cflags += ["-lpolyglot-mock", "-Wl,-install_name=@rpath/libpython" + so_ext] if darwin_native else [] 
        compile(files, capi_module, cflags)
        

    cext_module_src_path = os.path.join(sys.graal_python_cext_src, "modules")
    files = [os.path.join(cext_module_src_path, f) for f in os.listdir(cext_module_src_path) if f.endswith(".c")]
    logger.debug("Found native builtin modules: %r" % files)
    for f in files:
        f_basename = os.path.splitext(os.path.basename(f))[0]
        f_abs = os.path.abspath(f)
        module = os.path.abspath(os.path.join(sys.graal_python_cext_module_home, f_basename + so_ext))
        if needs_update(module, [f_abs]):
            cflags += ["-lbz2", "-lpolyglot-mock", "-lpython" + so_ext, "-Wl,-rpath=" + capi_module] if darwin_native else []
            compile([f_abs], module, cflags)


if __name__ == "__main__":
    if "-v" in sys.argv or "--verbose" in sys.argv:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig(level=logging.INFO)
    build_capi([x for x in sys.argv if x.startswith("-I")])
