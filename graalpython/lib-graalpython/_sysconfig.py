# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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


def _append_flags(g, key, flags):
    value = g.get(key, "")
    parts = value.split()
    for flag in flags:
        if flag and flag not in parts:
            parts.append(flag)
    g[key] = " ".join(parts)


def _setdefault(g, key, value):
    if key not in g:
        g[key] = value


def _update_posix_vars(config_vars=None):
    """Add runtime path-dependent config vars."""
    import os
    import sys

    if config_vars is None:
        config_vars = {}

    win32_native = sys.platform == "win32"
    darwin_native = sys.platform == "darwin"
    get_toolchain = __graalpython__.determine_system_toolchain().get

    if win32_native:
        python_inc = os.path.join(os.path.normpath(sys.base_prefix), 'Include')
    else:
        python_inc = os.path.join(
            os.path.normpath(sys.base_prefix),
            'include',
            f'python{sys.version_info[0]}.{sys.version_info[1]}{sys.abiflags}'
        )

    _setdefault(config_vars, 'CC', get_toolchain('CC'))
    _setdefault(config_vars, 'CXX', get_toolchain('CXX'))
    _setdefault(config_vars, 'AR', get_toolchain('AR'))
    _setdefault(config_vars, 'RANLIB', get_toolchain('RANLIB'))
    _setdefault(config_vars, 'LD', get_toolchain('LD'))
    _setdefault(config_vars, 'NM', get_toolchain('NM'))
    _setdefault(config_vars, 'INCLUDEPY', python_inc)
    _setdefault(config_vars, 'CONFINCLUDEPY', python_inc)
    _append_flags(config_vars, 'CPPFLAGS', ['-I.', '-I' + python_inc])
    if win32_native:
        lib_path = f"-L{__graalpython__.capi_home.replace(os.path.sep, '/')}"
        _append_flags(config_vars, 'LDFLAGS', [lib_path])
        if 'LDSHARED' in config_vars:
            _append_flags(config_vars, 'LDSHARED', [lib_path])
        if 'LDCXXSHARED' in config_vars:
            _append_flags(config_vars, 'LDCXXSHARED', [lib_path])
        ldshared_common = f"-shared {config_vars.get('CCSHARED', '')} {config_vars.get('LDFLAGS', '')}"
    elif darwin_native:
        ldshared_common = config_vars.get('LDFLAGS', '')
    else:
        ldshared_common = f"-shared {config_vars.get('CCSHARED', '')}"
    _setdefault(config_vars, 'LDSHARED', f"{config_vars['CC']} {ldshared_common}".rstrip())
    _setdefault(config_vars, 'LDCXXSHARED', f"{config_vars['CXX']} {ldshared_common}".rstrip())
    _setdefault(config_vars, 'LIBDIR', __graalpython__.capi_home)
    _setdefault(config_vars, 'LIBDEST', __graalpython__.capi_home)
    _setdefault(config_vars, 'LIBPL', __graalpython__.capi_home.replace(os.path.sep, '/'))
    return config_vars
