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
import tempfile
import textwrap
import threading
import unittest


@unittest.skipUnless(sys.implementation.name == "graalpy" and sys.platform.startswith("linux"), "Linux GraalPy-specific test")
class EntropySubprocessTests(unittest.TestCase):
    HASH_SECRET_BYTES = 24
    RANDOM_SEED_BYTES = 624 * 4
    RANDOM_INSTANCE_BYTES = HASH_SECRET_BYTES + RANDOM_SEED_BYTES
    RANDOM_MODULE_BYTES = HASH_SECRET_BYTES + (2 * RANDOM_SEED_BYTES)
    TEMPFILE_CANDIDATE_NAME_BYTES = HASH_SECRET_BYTES + (4 * RANDOM_SEED_BYTES)
    SSL_DATA_DIR = os.path.join(os.path.dirname(__file__), "ssldata")

    def _run_with_init_pipe(self, byte_count: int, code: str):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = os.path.join(temp_dir, "initrandom")
            subprocess.run(["mkfifo", path], check=True)
            writer_error = []
            bytes_written = 0

            def feed_pipe():
                nonlocal bytes_written
                try:
                    fd = os.open(path, os.O_WRONLY)
                    try:
                        remaining = byte_count
                        chunk = b"\x00" * min(4096, byte_count)
                        while remaining > 0:
                            written = os.write(fd, chunk[:remaining])
                            bytes_written += written
                            remaining -= written
                    finally:
                        os.close(fd)
                except BaseException as exc:  # re-raised in the main thread
                    writer_error.append(exc)

            writer = threading.Thread(target=feed_pipe)
            writer.start()
            try:
                result = self._run_with_init_source(f"device:{path}", code)
            finally:
                writer.join(timeout=10)
            if writer.is_alive():
                self.fail("initrandom pipe writer thread did not finish")
            if writer_error:
                raise writer_error[0]
            return result, bytes_written

    def _run_with_init_source(self, source: str, code: str):
        env = os.environ.copy()
        env.pop("PYTHONHASHSEED", None)
        return subprocess.run(
            [
                sys.executable,
                "-S",
                "--experimental-options=true",
                f"--python.InitializationEntropySource={source}",
                "-c",
                code,
            ],
            capture_output=True,
            text=True,
            env=env,
        )

    def assert_initrandom_exhausted(self, result):
        self.assertNotEqual(0, result.returncode, result)
        combined = f"{result.stdout}\n{result.stderr}"
        self.assertIn("initialization entropy device exhausted", combined)

    def assert_subprocess_ok(self, result):
        self.assertEqual(0, result.returncode, result)

    def assert_initrandom_bytes_used(self, byte_count: int, code: str, stdout: str):
        result, bytes_written = self._run_with_init_pipe(byte_count, code)
        self.assert_subprocess_ok(result)
        self.assertEqual(byte_count, bytes_written)
        self.assertEqual(stdout, result.stdout.strip())

        exhausted_result, exhausted_written = self._run_with_init_pipe(byte_count - 1, code)
        self.assert_initrandom_exhausted(exhausted_result)
        self.assertEqual(byte_count - 1, exhausted_written)

    def test_startup_hash_secret_uses_initrandom(self):
        self.assert_initrandom_bytes_used(self.HASH_SECRET_BYTES, "print('ok')", "ok")

    def test__random_random_uses_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_INSTANCE_BYTES,
            "import _random; _random.Random(); print('ok')",
            "ok",
        )

    def test_random_module_import_uses_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            "import random; print('ok')",
            "ok",
        )

    def test_systemrandom_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            "import random; random.SystemRandom().getrandbits(32); print('ok')",
            "ok",
        )

    def test_secrets_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            "import random; import secrets; secrets.token_hex(8); print('ok')",
            "ok",
        )

    def test_os_urandom_does_not_use_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import os; print(len(os.urandom(16)))",
            "16",
        )

    def test_multiprocessing_process_import_does_not_use_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import multiprocessing.process; print('ok')",
            "ok",
        )

    def test_multiprocessing_deliver_challenge_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            textwrap.dedent("""
                import random
                import multiprocessing.connection as mc
                auth = b'authkey'

                class Dummy:
                    def __init__(self):
                        self.sent = []

                    def send_bytes(self, data):
                        self.sent.append(data)

                    def recv_bytes(self, _max):
                        msg = self.sent[-1][len(mc._CHALLENGE):]
                        return mc._create_response(auth, msg)

                d = Dummy()
                mc.deliver_challenge(d, auth)
                print('ok')
            """),
            "ok",
        )

    def test_tempfile_candidate_names_use_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.TEMPFILE_CANDIDATE_NAME_BYTES,
            "import tempfile; next(tempfile._get_candidate_names()); print('ok')",
            "ok",
        )

    def test_tempfile_after_random_import_uses_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.TEMPFILE_CANDIDATE_NAME_BYTES,
            "import random; import tempfile; next(tempfile._get_candidate_names()); print('ok')",
            "ok",
        )

    def test_email_generator_boundary_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            "import random; from email.generator import Generator; Generator._make_boundary(); print('ok')",
            "ok",
        )

    def test_imaplib_connect_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            textwrap.dedent("""
                import random
                import imaplib

                class Dummy(imaplib.IMAP4):
                    def open(self, host='', port=imaplib.IMAP4_PORT, timeout=None):
                        pass

                    def _get_response(self):
                        self.untagged_responses = {'OK': [b'']}
                        return 'OK'

                    def _get_capabilities(self):
                        self.capabilities = ('IMAP4REV1',)

                    def shutdown(self):
                        pass

                d = Dummy.__new__(Dummy)
                d.debug = imaplib.Debug
                d.state = 'LOGOUT'
                d.literal = None
                d.tagged_commands = {}
                d.untagged_responses = {}
                d.continuation_response = ''
                d.is_readonly = False
                d.tagnum = 0
                d._tls_established = False
                d._mode_ascii()
                d._connect()
                print('ok')
            """),
            "ok",
        )

    def test_pyexpat_import_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import pyexpat; print(pyexpat.__name__)",
            "pyexpat",
        )

    def test_pyexpat_parsercreate_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import pyexpat; p = pyexpat.ParserCreate(); print(type(p).__name__)",
            "xmlparser",
        )

    def test_sqlite3_import_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import _sqlite3; print(_sqlite3.__name__)",
            "_sqlite3",
        )

    def test_sqlite3_randomblob_does_not_use_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import sqlite3; "
            "conn = sqlite3.connect(':memory:'); "
            "print(conn.execute('select length(randomblob(16))').fetchone()[0])",
            "16",
        )

    def test_ssl_load_cert_chain_does_not_use_initrandom(self):
        cert = os.path.join(self.SSL_DATA_DIR, "signed_cert.pem")
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            f"import ssl; "
            f"ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); "
            f"ctx.load_cert_chain(r'{cert}'); "
            f"print('ok')",
            "ok",
        )

    def test_uuid1_does_not_use_additional_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.RANDOM_MODULE_BYTES,
            "import random; import uuid; uuid.uuid1(node=1); print('ok')",
            "ok",
        )

    def test_uuid4_does_not_use_initrandom(self):
        self.assert_initrandom_bytes_used(
            self.HASH_SECRET_BYTES,
            "import uuid; print(uuid.uuid4().version)",
            "4",
        )
