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

import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest

from tests.util import run_subprocess_with_graalpy_startup_retry


POSIX_BACKEND_IS_JAVA = sys.implementation.name == "graalpy" and __graalpython__.posix_module_backend() == "java"


@unittest.skipUnless(sys.platform == "linux", "requires Linux LD_PRELOAD")
@unittest.skipIf(POSIX_BACKEND_IS_JAVA, "requires native password database lookups")
class PwdLookupErrorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        compiler = shlex.split(os.environ.get("CC", "cc"))
        if not compiler or shutil.which(compiler[0]) is None:
            raise unittest.SkipTest("requires a C compiler")
        temporary_directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(temporary_directory.cleanup)
        source = os.path.join(temporary_directory.name, "pwd_lookup_error.c")
        cls.library = os.path.join(temporary_directory.name, "pwd_lookup_error.so")
        # Inject the libc error itself, so the test exercises the real pwd builtin
        # and native POSIX backend without touching the host password database.
        with open(source, "w") as f:
            f.write(textwrap.dedent("""\
                #include <errno.h>
                #include <pwd.h>
                #include <stddef.h>
                #include <string.h>

                static int retry_count;

                int getpwuid_r(uid_t uid, struct passwd *pwd, char *buffer,
                               size_t buffer_size, struct passwd **result) {
                    *result = NULL;
                    if (uid == 2147483646) {
                        if (retry_count++ == 0) {
                            return ERANGE;
                        }
                        const char data[] = "graalpy_test_user\\0/home/graalpy_test_user\\0/bin/sh";
                        if (buffer_size < sizeof(data)) {
                            return ERANGE;
                        }
                        memcpy(buffer, data, sizeof(data));
                        pwd->pw_name = buffer;
                        pwd->pw_dir = buffer + strlen(buffer) + 1;
                        pwd->pw_shell = pwd->pw_dir + strlen(pwd->pw_dir) + 1;
                        pwd->pw_uid = uid;
                        pwd->pw_gid = uid;
                        pwd->pw_passwd = "x";
                        pwd->pw_gecos = "";
                        *result = pwd;
                        return 0;
                    }
                    errno = ENOENT;
                    return ENOENT;
                }

                int getpwnam_r(const char *name, struct passwd *pwd, char *buffer,
                               size_t buffer_size, struct passwd **result) {
                    *result = NULL;
                    errno = ENOENT;
                    return ENOENT;
                }
            """))
        result = subprocess.run(
            [*compiler, "-shared", "-fPIC", source, "-o", cls.library],
            capture_output=True, text=True, timeout=60,
        )
        if result.returncode:
            raise AssertionError(f"Could not compile password lookup shim:\n{result.stdout}\n{result.stderr}")

    def run_child(self, code, *, no_site=True):
        env = os.environ.copy()
        env.pop("HOME", None)
        env.pop("PYTHONUSERBASE", None)
        env.pop("PYTHONNOUSERSITE", None)
        env["LD_PRELOAD"] = self.library + (" " + env["LD_PRELOAD"] if env.get("LD_PRELOAD") else "")
        args = [sys.executable]
        if no_site:
            args.append("-S")
        args.extend(["-c", textwrap.dedent(code)])
        result = run_subprocess_with_graalpy_startup_retry(args, env=env, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, f"{result.stdout}\n{result.stderr}")

    def test_getpwuid_lookup_error_is_keyerror(self):
        self.run_child("""\
            import os
            import pwd

            try:
                pwd.getpwuid(os.getuid())
            except KeyError:
                pass
            else:
                raise AssertionError("Expected KeyError for a failed password database lookup")
        """)

    def test_getpwuid_retries_erange(self):
        self.run_child("""\
            import pwd

            entry = pwd.getpwuid(2147483646)
            assert entry.pw_name == "graalpy_test_user", entry
            assert entry.pw_dir == "/home/graalpy_test_user", entry
            assert entry.pw_shell == "/bin/sh", entry
            assert entry.pw_uid == 2147483646, entry
        """)

    def test_getpwnam_lookup_error_is_keyerror(self):
        self.run_child("""\
            import pwd

            try:
                pwd.getpwnam("graalpy_test_user")
            except KeyError:
                pass
            else:
                raise AssertionError("Expected KeyError for a failed password database lookup")
        """)

    def test_expanduser_with_lookup_error(self):
        self.run_child("""\
            import os

            assert os.path.expanduser("~/.local") == "~/.local"
        """)

    def test_site_startup_with_lookup_error(self):
        # No -S: exercise automatic site initialization before the script runs.
        self.run_child("""\
            import site

            assert site.getuserbase() == "~/.local", site.getuserbase()
        """, no_site=False)
