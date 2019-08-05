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

def build_capi():
    def create_if_needed(d):
        if not os.path.exists(d):
            os.makedirs(d)
        elif os.path.islink(d) or os.path.isfile(d):
            raise ValueError('Unable to create directory %r' % d)

    def compile(f, module, cflags=[]):
        ld = sysconfig.get_config_vars()["LDSHARED"]
        cmd_line = " ".join([ld, "-I" + sysconfig.get_python_inc(), "-o", module] + cflags + f)
        logger.debug(cmd_line)
        res = os.system(cmd_line)
        if res:
            logger.fatal("compilation failed: '%s' returned with %r" % (cmd_line, res))
            raise BaseException

    create_if_needed(sys.graal_python_cext_module_home)
    create_if_needed(sys.graal_python_cext_home)

    darwin_native = sys.platform == "darwin" and sys.graal_python_platform_id == "native"
    so_ext = sysconfig.get_config_var("EXT_SUFFIX")

    cext_src_path = os.path.join(sys.graal_python_cext_src, "src")
    files = [os.path.join(cext_src_path, f) for f in os.listdir(cext_src_path) if f.endswith(".c")]
    capi_module = os.path.abspath(os.path.join(sys.graal_python_cext_home, "libpython" + so_ext))
    if not os.path.exists(capi_module):
        cflags = ["-lpolyglot-mock", "-Wl,-install_name=@rpath/libpython" + so_ext] if darwin_native else []
        compile([os.path.abspath(f) for f in files], capi_module, cflags)
        

    cext_module_src_path = os.path.join(sys.graal_python_cext_src, "modules")
    files = [os.path.join(cext_module_src_path, f) for f in os.listdir(cext_module_src_path) if f.endswith(".c")]
    for f in files:
        f_basename = os.path.splitext(os.path.basename(f))[0]
        module = os.path.join(sys.graal_python_cext_module_home, f_basename + so_ext)
        if not os.path.exists(module):
            cflags = ["-lbz2", "-lpolyglot-mock", "-lpython" + so_ext, "-Wl,-rpath=" + capi_module] if darwin_native else []
            compile([os.path.abspath(f)], os.path.abspath(module), cflags)


if __name__ == "__main__":
    build_capi()
