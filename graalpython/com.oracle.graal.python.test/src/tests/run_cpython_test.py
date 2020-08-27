# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import sys
import unittest

sys.path.insert(0, os.getcwd())


class TestLoader(unittest.TestLoader):

    def prepare_test_decimal(self, module):
        # Taken from test_main() in test_decimal.py
        module.init(module.C)
        module.init(module.P)
        module.TEST_ALL = True
        module.DEBUG = None
        for filename in os.listdir(module.directory):
            if '.decTest' not in filename or filename.startswith("."):
                continue
            head, tail = filename.split('.')
            tester = lambda self, f=filename: self.eval_file(module.directory + f)
            setattr(module.CIBMTestCases, 'test_' + head, tester)
            setattr(module.PyIBMTestCases, 'test_' + head, tester)
            del filename, head, tail, tester
        return self.suiteClass(self.loadTestsFromTestCase(cls) for cls in module.all_tests)

    def loadTestsFromModule(self, module, pattern=None):
        if module.__name__.endswith('test_decimal'):
            return self.prepare_test_decimal(module)
        suite = super().loadTestsFromModule(module, pattern=pattern)
        test_main = getattr(module, 'test_main', None)
        if callable(test_main):
            class TestMain(unittest.TestCase):
                pass

            TestMain.__module__ = test_main.__module__
            TestMain.__qualname__ = TestMain.__name__
            TestMain.test_main = staticmethod(test_main)
            suite.addTests(self.loadTestsFromTestCase(TestMain))
        return suite


# We would normmally just pass the loader to the main, but there are tests for the framework itself (test_unittest)
# that interact weirdly with non-default loaders
unittest.defaultTestLoader = TestLoader()
unittest.main(module=None, testLoader=unittest.defaultTestLoader)
