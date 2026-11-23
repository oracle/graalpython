# Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import errno
import os
import socket
import struct
import sys
import threading
import unittest

from tests.util import storage_to_native


@unittest.skipUnless(sys.platform == "win32", "Windows socket errors")
class TestWindowsSocketErrors(unittest.TestCase):
    def test_strerror_uses_crt_messages(self):
        # Winsock messages belong to socket exceptions, not os.strerror().
        unknown_error = os.strerror(123456)
        for code in (10022, 10054, 10061):
            with self.subTest(code=code):
                self.assertEqual(os.strerror(code), unknown_error)

    def assert_system_message(self, error):
        self.assertTrue(error.strerror)
        self.assertNotIn("Unknown error", error.strerror)
        self.assertEqual(error.strerror, error.strerror.rstrip(" \t\r\n."))

    def test_winsock_crt_errno(self):
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            with self.assertRaises(OSError) as caught:
                sock.bind(("127.0.0.1", 0))
            # Winsock duplicates of CRT errors use the CRT errno, retaining winerror.
            self.assertEqual(caught.exception.errno, errno.EINVAL)
            self.assertEqual(caught.exception.winerror, 10022)
            self.assert_system_message(caught.exception)

    def test_connection_reset(self):
        self.assertEqual(errno.ECONNRESET, 10054)
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen()
            with socket.create_connection(listener.getsockname(), timeout=5) as client:
                peer, _ = listener.accept()
                with peer:
                    # Abort the connection rather than sending an orderly EOF.
                    peer.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("HH", 1, 0))
                with self.assertRaises(ConnectionResetError) as caught:
                    client.recv(1)
                self.assertEqual(caught.exception.errno, errno.ECONNRESET)
                self.assertEqual(caught.exception.winerror, 10054)
                self.assert_system_message(caught.exception)

    def test_would_block(self):
        self.assertEqual(errno.EWOULDBLOCK, 10035)
        self.assertNotEqual(errno.EWOULDBLOCK, errno.EAGAIN)
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen()
            with socket.create_connection(listener.getsockname()) as client:
                peer, _ = listener.accept()
                with peer:
                    client.setblocking(False)
                    with self.assertRaises(BlockingIOError) as caught:
                        client.recv(1)
                    self.assertEqual(caught.exception.errno, errno.EWOULDBLOCK)
                    self.assertEqual(caught.exception.winerror, 10035)
                    self.assert_system_message(caught.exception)

    def test_connection_refused(self):
        self.assertEqual(errno.ECONNREFUSED, 10061)
        # Keep the port reserved without listening, so another process cannot take it.
        with socket.socket() as reserved:
            reserved.bind(("127.0.0.1", 0))
            for timeout in (None, 5):
                with self.subTest(timeout=timeout), socket.socket() as client:
                    client.settimeout(timeout)
                    with self.assertRaises(ConnectionRefusedError) as caught:
                        client.connect(reserved.getsockname())
                    self.assertEqual(caught.exception.errno, errno.ECONNREFUSED)
                    self.assertEqual(caught.exception.winerror, 10061)
                    self.assert_system_message(caught.exception)


def test_shutdown_write_sends_eof():
    for how in (socket.SHUT_WR, socket.SHUT_RDWR):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen()
            with socket.create_connection(listener.getsockname()) as client:
                peer, _ = listener.accept()
                with peer:
                    peer.settimeout(5)
                    client.settimeout(5)
                    client.shutdown(how)
                    assert peer.recv(1) == b""
                    if how == socket.SHUT_WR:
                        # Shutting down writes must leave the receive direction usable.
                        peer.sendall(b"r")
                        assert client.recv(1) == b"r"


def test_inet_aton():
    assert socket.inet_aton('127.255.1.2') == bytes([127, 255, 1, 2])
    assert socket.inet_aton('127.0Xff.1.2') == bytes([127, 255, 1, 2])
    assert socket.inet_aton('127.260') == bytes([127, 0, 1, 4])
    assert socket.inet_aton('127.127.260') == bytes([127, 127, 1, 4])
    assert socket.inet_aton('0x7fff0201') == bytes([0x7f, 0xff, 2, 1])
    # the same number as above, but in decimal
    assert socket.inet_aton('2147418625') == bytes([0x7f, 0xff, 2, 1])


class TestInetAtonErrors(unittest.TestCase):
    def test_inet_aton_errs(self):
        self.assertRaises(OSError, lambda : socket.inet_aton('oracle.com'))
        self.assertRaises(OSError, lambda : socket.inet_aton('255.255.256.1'))
        self.assertRaises(TypeError, lambda : socket.inet_aton(255))


class TestHostLookupErrors(unittest.TestCase):
    def test_gethostbyname_ex_invalid_host_raises_gaierror(self):
        with self.assertRaises(socket.gaierror):
            socket.gethostbyname_ex("nonexistent.invalid")


def test_get_name_info():
    import socket
    try :
        socket.getnameinfo((1, 0, 0, 0), 0)
    except TypeError:
        raised = True
    assert raised


def test_recv_into():
    try:
        if __graalpython__.posix_module_backend() == "java":
            return  # transient timeouts
    except NameError:
        pass # not graalpy

    port = None
    event = threading.Event()
    def server():
        nonlocal port
        with socket.create_server(('localhost', 0)) as sock:
            port = sock.getsockname()[1]
            event.set()
            conn, addr = sock.accept()
            conn.send(b'123')
            conn.close()
    thread = threading.Thread(target=server)
    thread.start()
    event.wait()
    with socket.create_connection(('localhost', port)) as sock:
        # Byte buffer with direct access to internal array
        b = bytearray(b'aaa')
        sock.recv_into(b, 1)
        assert b == b'1aa'
        # Byte buffer with offset, this currently doesn't have internal array acces, but we might implement it later
        buffer = memoryview(b)[1:]
        sock.recv_into(buffer, 1)
        assert b == b'12a'
        storage_to_native(b)
        # Native buffer, no internal array
        buffer = memoryview(buffer)[1:]
        sock.recv_into(buffer, 1)
        assert b == b'123'
    thread.join()


def test_sendto_recvfrom_roundtrip():
    try:
        if __graalpython__.posix_module_backend() == "java":
            return
    except NameError:
        pass

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as server:
        server.bind(("127.0.0.1", 0))
        server_addr = server.getsockname()

        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
            client.bind(("127.0.0.1", 0))
            client_addr = client.getsockname()

            sent = client.sendto(b"phase5", server_addr)
            assert sent == 6

            data, addr = server.recvfrom(64)
            assert data == b"phase5"
            assert addr == client_addr

            sent = server.sendto(b"reply", client_addr)
            assert sent == 5

            data, addr = client.recvfrom(64)
            assert data == b"reply"
            assert addr == server_addr
