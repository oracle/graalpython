# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import unittest
import sys
import re
import subprocess
import platform

# Both lists should remain as small as possible to avoid adding overhead to startup
expected_nosite_startup_modules = [
    '_frozen_importlib',
    '_frozen_importlib_external',
    '__graalpython__',
    '_weakref',
    '_sre',
    '_sysconfig',
    'java',
    'pip_hook',
] + (['_nt'] if platform.system() == 'Windows' else [])

expected_full_startup_modules = expected_nosite_startup_modules + [
    '_abc',
    'types',
    '_weakrefset',
    '_py_abc',
    'abc',
    'stat',
    '_collections_abc',
    'genericpath',
    *(['_winapi', 'ntpath'] if platform.system() == 'Windows' else ['posixpath']),
    'os',
    '_sitebuiltins',
    '_io',
    'io',
    'site',
]

class StartupTests(unittest.TestCase):
    @unittest.skipUnless(sys.implementation.name == 'graalpy', "GraalPy-specific test")
    def test_startup_nosite(self):
        result = subprocess.check_output([sys.executable, '--log.level=FINE', '-S', '-v', '-c', 'print("Hello")'], stderr=subprocess.STDOUT, text=True)
        assert 'Hello' in result
        imports = re.findall("import '(\S+)'", result)
        self.assertEqual(expected_nosite_startup_modules, imports)

    @unittest.skipUnless(sys.implementation.name == 'graalpy', "GraalPy-specific test")
    def test_startup_full(self):
        result = subprocess.check_output([sys.executable, '--log.level=FINE', '-s', '-v', '-c', 'print("Hello")'], stderr=subprocess.STDOUT, text=True)
        assert 'Hello' in result
        imports = re.findall("import '(\S+)'", result)
        self.assertEqual(expected_full_startup_modules, imports)
