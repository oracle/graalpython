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
import subprocess
import sys
import textwrap
import unittest


@unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific C API initialization test")
@unittest.skipIf(os.name == "nt", "uses os.pipe() blocking semantics")
class TestCApiInit(unittest.TestCase):
    def run_in_subprocess(self, code):
        python_args = [sys.executable, "--experimental-options", "--python.EnableDebuggingBuiltins"]
        if not __graalpython__.is_native:
            python_args += [f"--vm.Dpython.EnableBytecodeDSLInterpreter={str(__graalpython__.is_bytecode_dsl_interpreter).lower()}"]
        proc = subprocess.run(
            [*python_args, "-c", code],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        if proc.returncode != 0:
            self.fail(
                "Subprocess failed with exit code {}\nstdout:\n{}\nstderr:\n{}".format(
                    proc.returncode, proc.stdout, proc.stderr
                )
            )

    def test_import_ctypes_while_other_thread_is_blocked_on_io(self):
        code = textwrap.dedent(
            """
            import os
            import queue
            import threading

            import __graalpython__

            assert __graalpython__.get_capi_state() == "UNINITIALIZED"

            read_fd, write_fd = os.pipe()
            about_to_block = threading.Event()
            import_started = threading.Event()
            import_done = threading.Event()
            errors = queue.Queue()

            def blocked_thread():
                try:
                    assert __graalpython__.get_capi_state() == "UNINITIALIZED"
                    about_to_block.set()
                    data = os.read(read_fd, 1)
                    assert data == b"x"
                    assert __graalpython__.get_capi_state() == "INITIALIZED"
                    import ctypes
                    assert ctypes.sizeof(ctypes.py_object) > 0
                except BaseException as e:
                    errors.put(e)

            def importing_thread():
                try:
                    assert __graalpython__.get_capi_state() == "UNINITIALIZED"
                    import_started.set()
                    import _ctypes
                    import ctypes
                    assert __graalpython__.get_capi_state() == "INITIALIZED"
                    assert _ctypes.sizeof(ctypes.py_object) > 0
                except BaseException as e:
                    errors.put(e)
                finally:
                    import_done.set()

            blocked = threading.Thread(target=blocked_thread, daemon=True)
            importer = threading.Thread(target=importing_thread, daemon=True)
            try:
                blocked.start()
                assert about_to_block.wait(10), "blocked thread did not start"
                assert __graalpython__.get_capi_state() == "UNINITIALIZED"

                importer.start()
                assert import_started.wait(10), "importing thread did not start"
                assert import_done.wait(20), "C API initialization did not finish"
                assert __graalpython__.get_capi_state() == "INITIALIZED"
            finally:
                try:
                    os.write(write_fd, b"x")
                except OSError:
                    pass
                blocked.join(20)
                importer.join(20)
                os.close(read_fd)
                os.close(write_fd)

            assert not blocked.is_alive(), "blocked thread did not finish"
            assert not importer.is_alive(), "importing thread did not finish"

            if not errors.empty():
                raise errors.get()
            """
        )
        self.run_in_subprocess(code)


if __name__ == "__main__":
    unittest.main()
