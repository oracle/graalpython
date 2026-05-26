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

# system configuration generated and used by the sysconfig module

build_time_vars = {
    "ABIFLAGS": "@GRAALPY_SYSCONFIG_ABIFLAGS@",
    "ARFLAGS": "@GRAALPY_SYSCONFIG_ARFLAGS@",
    "CCSHARED": "@GRAALPY_SYSCONFIG_CCSHARED@",
    "CFLAGS": "@GRAALPY_SYSCONFIG_CFLAGS@",
    "CFLAGS_DEFAULT": "@GRAALPY_SYSCONFIG_CFLAGS_DEFAULT@",
    "EXE": "@GRAALPY_SYSCONFIG_EXE@",
    "EXT_SUFFIX": "@GRAALPY_SYSCONFIG_EXT_SUFFIX@",
    "LDLIBRARY": "@GRAALPY_SYSCONFIG_LDLIBRARY@",
    "LDFLAGS": "@GRAALPY_SYSCONFIG_LDFLAGS@",
    "LIBPYTHON": "",
    "LIBS": "",
    "MACOSX_DEPLOYMENT_TARGET": "@GRAALPY_SYSCONFIG_MACOSX_DEPLOYMENT_TARGET@",
    "MULTIARCH": "@GRAALPY_SYSCONFIG_MULTIARCH@",
    "OPT": "@GRAALPY_SYSCONFIG_OPT@",
    "Py_GIL_DISABLED": @GRAALPY_SYSCONFIG_GIL_DISABLED@,
    "Py_DEBUG": 0,
    "Py_ENABLE_SHARED": 0,
    "Py_HASH_ALGORITHM": 0,
    "SHLIB_SUFFIX": "@GRAALPY_SYSCONFIG_SHLIB_SUFFIX@",
    "SO": "@GRAALPY_SYSCONFIG_EXT_SUFFIX@",
    "SOABI": "@GRAALPY_SYSCONFIG_SOABI@",
    "SYSLIBS": "",
    "USE_GNU_SOURCE": "@GRAALPY_SYSCONFIG_USE_GNU_SOURCE@",
    "VERSION": "@GRAALPY_SYSCONFIG_VERSION@",
}

for _key, _value in {
    "AR": "@GRAALPY_SYSCONFIG_AR@",
    "CC": "@GRAALPY_SYSCONFIG_CC@",
    "CXX": "@GRAALPY_SYSCONFIG_CXX@",
    "LD": "@GRAALPY_SYSCONFIG_LD@",
    "LDCXXSHARED": "@GRAALPY_SYSCONFIG_LDCXXSHARED@",
    "LDSHARED": "@GRAALPY_SYSCONFIG_LDSHARED@",
    "NM": "@GRAALPY_SYSCONFIG_NM@",
    "RANLIB": "@GRAALPY_SYSCONFIG_RANLIB@",
}.items():
    if _value:
        build_time_vars[_key] = _value
