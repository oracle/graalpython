# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import unittest
import sys


def test_os_pipe():
    import os
    r,w = os.pipe()
    written = os.write(w, b"hello")
    assert os.read(r, written) == b"hello"
    os.close(r)
    os.close(w)


class TestSubprocess(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        global subprocess, io, sys
        import subprocess, io, sys

    def test_io_buffered_by_default(self):
        p = subprocess.Popen([sys.executable, "-c", "import sys; sys.exit(0)"],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE)
        try:
            self.assertIsInstance(p.stdin, io.BufferedIOBase)
            self.assertIsInstance(p.stdout, io.BufferedIOBase)
            self.assertIsInstance(p.stderr, io.BufferedIOBase)
        finally:
            p.stdin.close()
            p.stdout.close()
            p.stderr.close()
            p.wait()

    def test_io_unbuffered_works(self):
        p = subprocess.Popen([sys.executable, "-c", "import sys; sys.exit(0)"],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, bufsize=0)
        try:
            self.assertIsInstance(p.stdin, io.RawIOBase)
            self.assertIsInstance(p.stdout, io.RawIOBase)
            self.assertIsInstance(p.stderr, io.RawIOBase)
        finally:
            p.stdin.close()
            p.stdout.close()
            p.stderr.close()
            p.wait()

    def test_check_call_zero(self):
        # check_call() function with zero return code
        rc = subprocess.check_call([sys.executable, "-c",
                                    "import sys; sys.exit(0)"])
        self.assertEqual(rc, 0)

    def test_check_call_nonzero(self):
        # check_call() function with non-zero return code
        with self.assertRaises(subprocess.CalledProcessError) as c:
            subprocess.check_call([sys.executable, "-c",
                                   "import sys; sys.exit(47)"])
        self.assertEqual(c.exception.returncode, 47)

    def test_check_output(self):
        # check_output() function with zero return code
        output = subprocess.check_output(
                [sys.executable, "-c", "print('BDFL')"])
        self.assertIn(b'BDFL', output)

    def test_check_output_nonzero(self):
        # check_call() function with non-zero return code
        with self.assertRaises(subprocess.CalledProcessError) as c:
            subprocess.check_output(
                    [sys.executable, "-c", "import sys; sys.exit(5)"])
        self.assertEqual(c.exception.returncode, 5)

    def test_check_output_stdout_arg(self):
        # check_output() refuses to accept 'stdout' argument
        with self.assertRaises(ValueError) as c:
            output = subprocess.check_output(
                    [sys.executable, "-c", "print('will not be run')"],
                    stdout=sys.stdout)
            self.fail("Expected ValueError when stdout arg supplied.")
        self.assertIn('stdout', c.exception.args[0])

    def test_kill(self):
        p = subprocess.Popen([sys.executable, "-c", "print('oh no')"])
        p.kill()
        assert True
        p.wait()

    @unittest.skipIf(sys.platform == 'win32', "Posix-specific")
    def test_waitpid(self):
        import os
        p = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(2); 42"])
        try:
            assert os.waitpid(p.pid, os.WNOHANG) == (0, 0)
        finally:
            p.kill()
            p.wait()

    # @unittest.skipIf(sys.platform == 'win32', "Posix-specific")
    # Skipped because of transient: GR-66709
    @unittest.skip
    def test_waitpid_group_child(self):
        import os
        p = subprocess.Popen([sys.executable, "-c", "import time; print('before'); time.sleep(0.1); print('after'); 42"],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE)
        ps_list = 'not available'
        if sys.implementation.name == "graalpy" and \
                __graalpython__.posix_module_backend != 'java' \
                and sys.platform.startswith("linux"):
            ps_list = subprocess.check_output("ps", shell=True, text=True)
        res = os.waitpid(0, 0)
        msg = f"Spawned {p.pid=}, os.waitpid result={res}, output of ps:\n{ps_list}"
        try:
            stdout, stderr = p.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            p.kill()
            stdout, stderr = p.communicate()
        msg += f"\n{stdout.decode().strip()=}, {stderr.decode().strip()=}"
        assert res[1] == 0, msg

    # @unittest.skipIf(sys.platform == 'win32', "Posix-specific")
    # Skipped because of transient: https://jira.oci.oraclecorp.com/browse/GR-65714
    @unittest.skip
    def test_waitpid_any_child(self):
        import os
        p = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(0.1); 42"])
        res = os.waitpid(-1, 0)
        assert res[1] == 0, res

    # Skipped because of transient: GR-66709
    @unittest.skip
    def test_waitpid_no_child(self):
        import os
        try:
            os.waitpid(-1, 0)
        except ChildProcessError:
            assert True
        else:
            assert False

    def test_kill(self):
        import os
        p = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(2); 42"])
        try:
            assert os.kill(p.pid, 9) is None
        finally:
            p.kill()
            p.wait()

    def test_java_asserts(self):
        import sys
        if sys.implementation.name == "graalpy":
            import subprocess, __graalpython__
            if __graalpython__.java_assert():
                result = subprocess.run([sys.executable, "-c", "import __graalpython__; __graalpython__.java_assert()"])
            else:
                result = subprocess.run([sys.executable, "-c", "import __graalpython__; not __graalpython__.java_assert()"])
            assert result.returncode == 0

    def test_subprocess_inherits_environ(self):
        import os
        import subprocess
        prev = os.environ.get("FOOBAR")
        try:
            expected_value = f"42{prev}".strip()
            os.environ["FOOBAR"] = expected_value
            out = subprocess.check_output([sys.executable, '-c', "import os; print(os.environ['FOOBAR'])"]).decode().strip()
            assert out == expected_value, f"{out!r} != {expected_value!r}"
        finally:
            if prev:
                os.environ["FOOBAR"] = prev
            else:
                del os.environ["FOOBAR"]

    @unittest.skipUnless(sys.implementation.name == 'graalpy', "GraalPy-specific test")
    @unittest.skipIf(sys.platform == 'win32', "TODO the cmd replacement breaks the test")
    def test_graal_python_args(self):
        if sys.implementation.name == "graalpy":
            import subprocess

            env = {"GRAAL_PYTHON_ARGS": "-c 12"}
            result = subprocess.run([sys.executable], env=env)
            self.assertEqual(0, result.returncode)

            env = {"GRAAL_PYTHON_ARGS": "-c 'print(12)'"}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('12\n', result)

            env = {"GRAAL_PYTHON_ARGS": """-c 'print("Hello world")'"""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('Hello world\n', result)

            env = {"GRAAL_PYTHON_ARGS": """-c ""'print("Hello world")'"""""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('Hello world\n', result)

            env = {"GRAAL_PYTHON_ARGS": r"""-c 'print(\'"Hello world"\')'"""""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('"Hello world"\n', result)

            env = {"GRAAL_PYTHON_ARGS": """\v-c\vprint('"Hello world"')"""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('"Hello world"\n', result)

            env = {"GRAAL_PYTHON_ARGS": """\v-c\vprint('Hello', "world")"""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('Hello world\n', result)

            # check that the subprocess receives the args and thus it should fail because it recurses
            args = """\v-c\vimport os\nprint(os.environ.get("GRAAL_PYTHON_ARGS"))"""
            env = {"GRAAL_PYTHON_ARGS": args}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual(f"{args}\n", result)

            # check that the subprocess does not receive the args when we end with \v
            env = {"GRAAL_PYTHON_ARGS": """\v-c\vimport os\nprint(os.environ.get("GRAAL_PYTHON_ARGS"))\v"""}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual('None\n', result)

            # check that the subprocess receives an empty arg
            args = """\v-c\vimport sys\nprint(repr(sys.argv))\va1\v\va3"""
            env = {"GRAAL_PYTHON_ARGS": args}
            result = subprocess.check_output([sys.executable], env=env, text=True)
            self.assertEqual("['-c', 'a1', '', 'a3']\n", result)
