# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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


def _get_posix_vars():
    """Initialize the config vars as appropriate for POSIX systems."""
    import _imp
    import sys
    import os
    darwin_native = sys.platform == "darwin"
    win32_native = sys.platform == "win32"

    # note: this must be kept in sync with _imp.extension_suffixes
    so_abi = sys.implementation.cache_tag + "-native-" + sys.implementation._multiarch
    if win32_native:
        so_ext = ".pyd"
    else:
        so_ext = ".so"
    assert _imp.extension_suffixes()[0] == "." + so_abi + so_ext, "mismatch between extension suffix to _imp.extension_suffixes"

    get_toolchain = __graalpython__.determine_system_toolchain().get

    toolchain_cxx = get_toolchain('CXX')
    have_cxx = toolchain_cxx is not None

    if win32_native:
        python_inc = os.path.join(os.path.normpath(sys.base_prefix), 'Include')
    else:
        python_inc = os.path.join(
            os.path.normpath(sys.base_prefix),
            'include',
            f'python{sys.version_info[0]}.{sys.version_info[1]}{sys.abiflags}'
        )

    fpic = "" if win32_native else "-fPIC"

    g = {}
    g['CC'] = get_toolchain('CC')
    g['CXX'] = toolchain_cxx if have_cxx else get_toolchain('CC') + ' --driver-mode=g++'
    opt_flags = ["-DNDEBUG"]
    g['OPT'] = ' '.join(opt_flags)
    g['INCLUDEPY'] = python_inc
    g['CONFINCLUDEPY'] = python_inc
    g['CPPFLAGS'] = '-I. -I' + python_inc
    gnu_source = "-D_GNU_SOURCE=1"
    g['USE_GNU_SOURCE'] = gnu_source
    cflags_default = list(opt_flags)
    if win32_native:
        cflags_default += ["-DMS_WINDOWS", "-DPy_ENABLE_SHARED", "-DHAVE_DECLSPEC_DLL"]
    g['CFLAGS_DEFAULT'] = ' '.join(cflags_default)
    g['CFLAGS'] = ' '.join(cflags_default + [gnu_source])
    g['LDFLAGS'] = ""
    g['LIBS'] = ""
    g['SYSLIBS'] = ""
    g['CCSHARED'] = fpic
    if darwin_native:
        # MACOSX_DEPLOYMENT_TARGET is taken from the minimum version we build
        # GraalPy for, which is currently BigSur
        g['MACOSX_DEPLOYMENT_TARGET'] = "11"
    else:
        g['MACOSX_DEPLOYMENT_TARGET'] = ""
    if darwin_native:
        g['LDFLAGS'] = "-bundle -undefined dynamic_lookup"
        ldshared_common = g['LDFLAGS']
        g['LIBPYTHON'] = ''
    elif win32_native:
        g['LDFLAGS'] = f"-L{__graalpython__.capi_home.replace(os.path.sep, '/')}"
        ldshared_common = f"-shared {fpic} {g['LDFLAGS']}"
    else:
        ldshared_common = f"-shared {fpic}"
        g['LIBPYTHON'] = ''
    g['LDSHARED'] = f"{g['CC']} {ldshared_common}"
    g['LDCXXSHARED'] = f"{g['CXX']} {ldshared_common}"
    g['SOABI'] = so_abi
    g['EXT_SUFFIX'] = "." + so_abi + so_ext
    g['SHLIB_SUFFIX'] = so_ext
    g['SO'] = "." + so_abi + so_ext # deprecated in Python 3, for backward compatibility
    g['AR'] = get_toolchain('AR')
    g['RANLIB'] = get_toolchain('RANLIB')
    g['ARFLAGS'] = "rc"
    g['LD'] = get_toolchain('LD')
    g['EXE'] = ".exe" if win32_native else ""
    g['LIBDIR'] = os.path.join(sys.prefix, 'lib')
    g['VERSION'] = ".".join(sys.version.split(".")[:2])
    g['Py_HASH_ALGORITHM'] = 0 # does not give any specific info about the hashing algorithm
    g['NM'] = get_toolchain('NM')
    g['MULTIARCH'] = sys.implementation._multiarch
    g['ABIFLAGS'] = ""
    g['Py_DEBUG'] = 0
    g['Py_ENABLE_SHARED'] = 0
    g['LIBDIR'] = __graalpython__.capi_home
    g['LIBDEST'] = __graalpython__.capi_home
    g['LDLIBRARY'] = 'libpython.' + so_abi + so_ext
    g['LIBPL'] = __graalpython__.capi_home.replace(os.path.sep, '/')
    return g


def make_sysconfigdata():
    # the sysconfigdata module name matches what's computed in stdlib's sysconfig.py
    import sys
    multiarch = getattr(sys.implementation, '_multiarch', '')
    name = f'_sysconfigdata_{sys.abiflags}_{sys.platform}_{multiarch}'
    sys.modules[name] = mod = type(sys)(name)
    no_default = object()
    def __getattr__(key, default=no_default):
        if key == "build_time_vars":
            return _get_posix_vars()
        elif default is no_default:
            raise AttributeError(key)
        else:
            return default
    mod.__getattr__ = __getattr__


make_sysconfigdata()
