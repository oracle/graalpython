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
import unittest


NATIVE_WINDOWS = (
    sys.platform == "win32"
    and (sys.implementation.name != "graalpy" or __graalpython__.posix_module_backend() == "native")
)


@unittest.skipUnless(NATIVE_WINDOWS, "requires native Windows pipes")
class WinapiPipeTests(unittest.TestCase):
    def setUp(self):
        import _winapi
        import multiprocessing

        self.api = _winapi
        self.reader, self.writer = multiprocessing.Pipe(duplex=False)
        self.addCleanup(self.reader.close)
        self.addCleanup(self.writer.close)

    def test_peek_named_pipe(self):
        peek = self.api.PeekNamedPipe
        handle = self.reader.fileno()
        self.assertEqual(peek(handle), (0, 0))
        self.assertEqual(peek(handle, 0), (0, 0))
        self.assertEqual(peek(handle, 10), (b"", 0, 0))
        self.writer.send_bytes(b"a" * 200)
        self.writer.send_bytes(b"b" * 20)
        self.assertEqual(peek(handle), (220, 200))
        self.assertEqual(peek(handle, 0), (220, 200))
        self.assertEqual(peek(handle, 10), (b"a" * 10, 220, 190))

    def check_partial_read(self, pending):
        api = self.api
        handle = self.reader.fileno()
        payload = bytes(range(200))
        if not pending:
            self.writer.send_bytes(payload)
        ov, err = api.ReadFile(handle, 128, overlapped=True)
        try:
            if pending:
                self.assertEqual(err, api.ERROR_IO_PENDING)
                self.writer.send_bytes(payload)
                self.assertEqual(api.WaitForSingleObject(ov.event, 5000), api.WAIT_OBJECT_0)
            else:
                self.assertEqual(err, api.ERROR_MORE_DATA)
            self.assertEqual(ov.GetOverlappedResult(False), (128, api.ERROR_MORE_DATA))
            self.assertEqual(ov.getbuffer(), payload[:128])
            self.assertEqual(api.PeekNamedPipe(handle), (72, 72))
            tail, err = api.ReadFile(handle, 72, overlapped=True)
            self.assertEqual(tail.GetOverlappedResult(True), (72, 0))
            self.assertEqual(tail.getbuffer(), payload[128:])
        finally:
            ov.cancel()
            ov.GetOverlappedResult(True)

    def test_immediate_partial_read(self):
        self.check_partial_read(pending=False)

    def test_pending_partial_read(self):
        self.check_partial_read(pending=True)

    def test_message_boundaries(self):
        # Queue multiple messages so total available differs from the bytes
        # remaining in the first message after the initial 128-byte read.
        messages = [bytes(range(256)) * 2, b"next message", b"", b"last"]
        for _ in range(10):
            self.assertFalse(self.reader.poll(0))
            for message in messages:
                self.writer.send_bytes(message)
            for message in messages:
                self.assertTrue(self.reader.poll(0))
                self.assertEqual(self.reader.recv_bytes(len(message)), message)
            self.assertFalse(self.reader.poll(0))

    def check_connect_named_pipe(self, client_first):
        import os
        import time
        from multiprocessing.connection import PipeClient, PipeListener

        address = rf"\\.\pipe\graalpy-connect-{os.getpid()}-{time.time_ns()}"
        listener = PipeListener(address)
        self.addCleanup(listener.close)
        if client_first:
            client = PipeClient(address)
            self.addCleanup(client.close)
        ov = self.api.ConnectNamedPipe(listener._handle_queue[0], overlapped=True)
        try:
            if not client_first:
                client = PipeClient(address)
                self.addCleanup(client.close)
            # PipeListener.accept waits on this event even when the client
            # connected before ConnectNamedPipe (ERROR_PIPE_CONNECTED).
            self.assertEqual(self.api.WaitForSingleObject(ov.event, 5000), self.api.WAIT_OBJECT_0)
            self.assertEqual(ov.GetOverlappedResult(True), (0, 0))
        finally:
            ov.cancel()
            ov.GetOverlappedResult(True)

    def test_connect_named_pipe_client_first(self):
        self.check_connect_named_pipe(client_first=True)

    def test_connect_named_pipe_pending(self):
        self.check_connect_named_pipe(client_first=False)

    def test_pickled_messages(self):
        for _ in range(10):
            messages = [list(range(200)), {"payload": "x" * 512}]
            for message in messages:
                self.writer.send(message)
            for message in messages:
                self.assertEqual(self.reader.recv(), message)
