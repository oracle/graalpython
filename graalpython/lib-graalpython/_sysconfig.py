# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


def get_config_var(name):
    return get_config_vars().get(name)


# Following code is shared between GraalPython patches in the sysconfig and distutils modules:

def get_python_inc(plat_specific=0, prefix=None):
    import sys
    import os
    BASE_PREFIX = os.path.normpath(sys.base_prefix)
    BASE_EXEC_PREFIX = os.path.normpath(sys.base_exec_prefix)
    if prefix is None:
        prefix = plat_specific and BASE_EXEC_PREFIX or BASE_PREFIX
    return os.path.join(prefix, 'include')


def get_python_version():
    """Return a string containing the major and minor Python version,
    leaving off the patchlevel.  Sample return values could be '1.5'
    or '2.2'.
    """
    import sys
    return sys.version[:3]


def _get_posix_vars():
    """Initialize the config vars as appropriate for POSIX systems."""
    import _imp
    import sys
    import os
    darwin_native = sys.platform == "darwin" and __graalpython__.platform_id == "native"

    # note: this must be kept in sync with _imp.extension_suffixes
    so_abi = sys.implementation.cache_tag + "-" + __graalpython__.platform_id + "-" + sys.implementation._multiarch
    so_ext = ".so" if not darwin_native else ".dylib"
    assert _imp.extension_suffixes()[0] == "." + so_abi + so_ext, "mismatch between extension suffix to _imp.extension_suffixes"

    toolchain_cxx = __graalpython__.get_toolchain_path('CXX')
    have_cxx = toolchain_cxx is not None

    g = {}
    g['CC'] = __graalpython__.get_toolchain_path('CC')
    g['CXX'] = toolchain_cxx if have_cxx else g['CC'] + ' --driver-mode=g++'
    g['OPT'] = "-stdlib=libc++ -DNDEBUG -O1"
    g['CONFINCLUDEPY'] = get_python_inc()
    g['CPPFLAGS'] = '-I. -I' + get_python_inc()
    g['CFLAGS'] = "-Wno-unused-command-line-argument -stdlib=libc++ -DNDEBUG -O1 -DHPY_UNIVERSAL_ABI"
    g['CCSHARED'] = "-fPIC"
    g['LDSHARED_LINUX'] = "%s -shared -fPIC" % __graalpython__.get_toolchain_path('CC')
    if darwin_native:
        g['LDSHARED'] = g['LDSHARED_LINUX'] + " -Wl,-undefined,dynamic_lookup"
    else:
        g['LDSHARED'] = g['LDSHARED_LINUX']
    g['SOABI'] = so_abi
    g['EXT_SUFFIX'] = "." + so_abi + so_ext
    g['SHLIB_SUFFIX'] = so_ext
    g['SO'] = "." + so_abi + so_ext # deprecated in Python 3, for backward compatibility
    g['AR'] = __graalpython__.get_toolchain_path('AR')
    g['RANLIB'] = __graalpython__.get_toolchain_path('RANLIB')
    g['ARFLAGS'] = "rc"
    g['EXE'] = ""
    g['LIBDIR'] = os.path.join(sys.prefix, 'lib')
    g['VERSION'] = get_python_version()
    g['Py_HASH_ALGORITHM'] = 0 # does not give any specific info about the hashing algorithm
    return g
