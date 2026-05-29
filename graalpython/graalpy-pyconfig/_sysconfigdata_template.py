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
    "ABIFLAGS": "@GRAALPY_SYSCONFIG_ABIFLAGS_PYTHON_STRING@",
    "ARFLAGS": "@GRAALPY_SYSCONFIG_ARFLAGS_PYTHON_STRING@",
    "CCSHARED": "@GRAALPY_SYSCONFIG_CCSHARED_PYTHON_STRING@",
    "CFLAGS": "@GRAALPY_SYSCONFIG_CFLAGS_PYTHON_STRING@",
    "CFLAGS_DEFAULT": "@GRAALPY_SYSCONFIG_CFLAGS_DEFAULT_PYTHON_STRING@",
    "EXE": "@GRAALPY_SYSCONFIG_EXE_PYTHON_STRING@",
    "EXT_SUFFIX": "@GRAALPY_SYSCONFIG_EXT_SUFFIX_PYTHON_STRING@",
    "LDLIBRARY": "@GRAALPY_SYSCONFIG_LDLIBRARY_PYTHON_STRING@",
    "LDFLAGS": "@GRAALPY_SYSCONFIG_LDFLAGS_PYTHON_STRING@",
    "LIBPYTHON": "",
    "LIBS": "",
    "MACOSX_DEPLOYMENT_TARGET": "@GRAALPY_SYSCONFIG_MACOSX_DEPLOYMENT_TARGET_PYTHON_STRING@",
    "MULTIARCH": "@GRAALPY_SYSCONFIG_MULTIARCH_PYTHON_STRING@",
    "OPT": "@GRAALPY_SYSCONFIG_OPT_PYTHON_STRING@",
    "Py_GIL_DISABLED": @GRAALPY_SYSCONFIG_GIL_DISABLED@,
    "Py_DEBUG": 0,
    "Py_ENABLE_SHARED": 0,
    "Py_HASH_ALGORITHM": 0,
    "SHLIB_SUFFIX": "@GRAALPY_SYSCONFIG_SHLIB_SUFFIX_PYTHON_STRING@",
    "SO": "@GRAALPY_SYSCONFIG_EXT_SUFFIX_PYTHON_STRING@",
    "SOABI": "@GRAALPY_SYSCONFIG_SOABI_PYTHON_STRING@",
    "SYSLIBS": "",
    "USE_GNU_SOURCE": "@GRAALPY_SYSCONFIG_USE_GNU_SOURCE_PYTHON_STRING@",
    "VERSION": "@GRAALPY_SYSCONFIG_VERSION_PYTHON_STRING@",
}

for _key, _value in {
    "AR": "@GRAALPY_SYSCONFIG_AR_PYTHON_STRING@",
    "CC": "@GRAALPY_SYSCONFIG_CC_PYTHON_STRING@",
    "CXX": "@GRAALPY_SYSCONFIG_CXX_PYTHON_STRING@",
    "LD": "@GRAALPY_SYSCONFIG_LD_PYTHON_STRING@",
    "LDCXXSHARED": "@GRAALPY_SYSCONFIG_LDCXXSHARED_PYTHON_STRING@",
    "LDSHARED": "@GRAALPY_SYSCONFIG_LDSHARED_PYTHON_STRING@",
    "NM": "@GRAALPY_SYSCONFIG_NM_PYTHON_STRING@",
    "RANLIB": "@GRAALPY_SYSCONFIG_RANLIB_PYTHON_STRING@",
}.items():
    if _value:
        build_time_vars[_key] = _value
