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

import sys
import tempfile
import unittest


class AuditHookTests(unittest.TestCase):
    def test_sys_audit_calls_registered_hook(self):
        seen = []

        def hook(event, args):
            if event == "graalpy.test_sys_audit":
                seen.append((event, args))

        sys.addaudithook(hook)
        sys.audit("graalpy.test_sys_audit", 1, "two")

        self.assertEqual(seen, [("graalpy.test_sys_audit", (1, "two"))])

    def test_sys_audit_propagates_hook_exception(self):
        class AuditError(Exception):
            pass

        def hook(event, args):
            if event == "graalpy.test_sys_audit_error":
                raise AuditError(args)

        sys.addaudithook(hook)

        with self.assertRaises(AuditError):
            sys.audit("graalpy.test_sys_audit_error", 42)

    def test_java_audit_site_calls_registered_hook(self):
        seen = []

        def hook(event, args):
            if event == "open":
                seen.append(args)

        sys.addaudithook(hook)
        with tempfile.TemporaryFile("w"):
            pass

        self.assertTrue(seen)

    def test_addaudithook_exception_blocks_new_hook(self):
        seen = []
        block_add = True

        def blocking_hook(event, args):
            nonlocal block_add
            if event == "sys.addaudithook":
                if not block_add:
                    return
                block_add = False
                raise RuntimeError("blocked")
            if event == "graalpy.test_blocked_hook":
                seen.append("blocking")

        def blocked_hook(event, args):
            if event == "graalpy.test_blocked_hook":
                seen.append("blocked")

        sys.addaudithook(blocking_hook)
        sys.addaudithook(blocked_hook)
        sys.audit("graalpy.test_blocked_hook")

        self.assertEqual(seen, ["blocking"])

    def test_addaudithook_propagates_baseexception(self):
        class AuditBaseException(BaseException):
            pass

        block_add = True

        def hook(event, args):
            nonlocal block_add
            if event == "sys.addaudithook":
                if not block_add:
                    return
                block_add = False
                raise AuditBaseException

        sys.addaudithook(hook)

        with self.assertRaises(AuditBaseException):
            sys.addaudithook(lambda event, args: None)


if __name__ == "__main__":
    unittest.main()
