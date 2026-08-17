# Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import ssl
import os
import json
import sys


IS_OPENSSL_3_0_0 = sys.implementation.name == 'graalpy' or ssl.OPENSSL_VERSION_INFO >= (3, 0, 0)


def data_file(name):
    return os.path.join(os.path.dirname(__file__), "ssldata", name)


def cpython_cert_data(name):
    return os.path.join(os.path.dirname(__file__), "..", "..", "..", "lib-python", "3", "test", "certdata", name)


class StringWrapper(str):
    pass

def check_handshake(server_context, client_context, err = None):
    hostname = 'localhost'
    c_in = ssl.MemoryBIO()
    c_out = ssl.MemoryBIO()
    s_in = ssl.MemoryBIO()
    s_out = ssl.MemoryBIO()
    client = client_context.wrap_bio(c_in, c_out, server_hostname=hostname)
    server = server_context.wrap_bio(s_in, s_out, server_side=True)

    try:
        for _ in range(5):
            try:
                client.do_handshake()
            except ssl.SSLWantReadError:
                pass
            if c_out.pending:
                s_in.write(c_out.read())
            try:
                server.do_handshake()
            except ssl.SSLWantReadError:
                pass
            if s_out.pending:
                c_in.write(s_out.read())
    except Exception as e:
        if err is None:
            assert False
        else:
            assert isinstance(e, err)
    else:
        if err is not None:
            assert False
    return server, client


class CertTests(unittest.TestCase):

    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)

    def test_create_default_context(self):
        context = ssl.create_default_context()
        self.assertTrue(context.verify_flags & ssl.VERIFY_X509_PARTIAL_CHAIN)
        self.assertTrue(context.verify_flags & ssl.VERIFY_X509_STRICT)

    def test_hostname_checks_common_name(self):
        self.assertTrue(ssl.HAS_NEVER_CHECK_COMMON_NAME)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        self.assertTrue(context.hostname_checks_common_name)
        context.hostname_checks_common_name = False
        self.assertFalse(context.hostname_checks_common_name)
        context.hostname_checks_common_name = True
        self.assertTrue(context.hostname_checks_common_name)

    def test_verify_x509_partial_chain(self):
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(data_file("signed_cert.pem"))

        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.load_verify_locations(data_file("signed_cert.pem"))
        client_context.verify_flags |= ssl.VERIFY_X509_PARTIAL_CHAIN
        check_handshake(server_context, client_context)

        client_context.verify_flags &= ~ssl.VERIFY_X509_PARTIAL_CHAIN
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

    @unittest.skipUnless(IS_OPENSSL_3_0_0, "test requires RFC 5280 check added in OpenSSL 3.0+")
    def test_verify_x509_strict(self):
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        try:
            server_context.load_cert_chain(cpython_cert_data("leaf-missing-aki.keycert.pem"))
        except ssl.SSLError as e:
            self.assertIn("require BouncyCastle support", str(e))
            return

        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.load_verify_locations(cpython_cert_data("leaf-missing-aki.ca.pem"))
        client_context.verify_flags |= ssl.VERIFY_X509_STRICT
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        client_context.verify_flags &= ~ssl.VERIFY_X509_STRICT
        check_handshake(server_context, client_context)

    def check_load_cert_chain_error(self, certfile, keyfile=None, errno=-1, strerror=None, err=ssl.SSLError):
        try:
            if keyfile is None:
                self.ctx.load_cert_chain(data_file(certfile))
            else:
                self.ctx.load_cert_chain(data_file(certfile), data_file(keyfile))
        except err as e:
            if errno != -1:
                self.assertEqual(e.errno, errno)
            if strerror is not None:
                self.assertIn(strerror, e.strerror)
            self.assertIsInstance(type(e), type(err))
        else:
            assert False

    def check_load_verify_locations_error(self, cafile=None, capath=None, cadata=None, errno=-1, strerror=None, err=ssl.SSLError):
        try:
            if capath is not None:
                capath = data_file(capath)
            if cafile is not None:
                cafile = data_file(cafile)
            if cadata is not None:
                cadata = open(data_file(cadata)).read()
            self.ctx.load_verify_locations(cafile, capath, cadata)
        except err as e:
            if errno != -1:
                self.assertEqual(e.errno, errno)
            if strerror is not None:
                if isinstance(ssl.SSLError, err):
                    self.assertIn(strerror, e.strerror)
                else:
                 self.assertIn(strerror, str(e))
            self.assertIsInstance(type(e), type(err))
        else:
            assert False

    def test_load_cert_chain(self):
        self.ctx.load_cert_chain(data_file("cert_rsa.pem"), keyfile=data_file("empty_pk_at_end.pem"))
        self.ctx.load_cert_chain(StringWrapper(data_file("cert_rsa.pem")), keyfile=StringWrapper(data_file("empty_pk_at_end.pem")))

        with self.assertRaisesRegex(TypeError, "certfile should be a valid filesystem path"):
            self.ctx.load_cert_chain(1)
        with self.assertRaisesRegex(TypeError, "certfile should be a valid filesystem path"):
            self.ctx.load_cert_chain(1, 1)
        with self.assertRaisesRegex(TypeError, "keyfile should be a valid filesystem path"):
            self.ctx.load_cert_chain(data_file("empty.pem"), 1)

        self.check_load_cert_chain_error(certfile="does_not_exit", errno=2, strerror="No such file or directory", err=FileNotFoundError)
        self.check_load_cert_chain_error(certfile="does_not_exit", keyfile="does_not_exist", errno=2, strerror="No such file or directory", err=FileNotFoundError)

        self.check_load_cert_chain_error(certfile="empty.pem", strerror="[SSL] PEM lib")
        self.check_load_cert_chain_error(certfile="empty_cert.pem", strerror="[SSL] PEM lib")
        self.check_load_cert_chain_error(certfile="empty_cert_at_begin.pem", strerror="[SSL] PEM lib")
        self.check_load_cert_chain_error(certfile="empty_cert_at_end.pem")

        self.check_load_cert_chain_error(certfile="broken_cert_double_begin.pem")
        self.check_load_cert_chain_error(certfile="broken_cert_only_begin.pem")
        self.check_load_cert_chain_error(certfile="broken_cert_no_end.pem")
        self.check_load_cert_chain_error(certfile="broken_cert_data.pem")
        self.check_load_cert_chain_error(certfile="broken_cert_data_at_begin.pem")
        self.check_load_cert_chain_error(certfile="broken_cert_data_at_end.pem")

        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="empty.pem")
        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="empty_pk.pem")
        # self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="empty_pk_at_begin.pem")

        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="broken_pk_data.pem")
        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="broken_pk_only_begin.pem")
        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="broken_pk_double_begin.pem")
        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="broken_pk_no_begin.pem")
        self.check_load_cert_chain_error(certfile="cert_rsa.pem", keyfile="broken_pk_no_end.pem")

        self.check_load_cert_chain_error(certfile="cert_rsa2.pem", keyfile="pk_rsa.pem", errno=116, strerror="[X509: KEY_VALUES_MISMATCH] key values mismatch")
        self.check_load_cert_chain_error(certfile="cert_rsa2.pem", keyfile="pk_ecc.pem")

    def test_load_verify_locations(self):
        self.ctx.load_verify_locations(data_file("cert_rsa.pem"))
        self.ctx.load_verify_locations(capath=data_file("cert_rsa.pem"))
        cad = open(data_file("cert_rsa.pem")).read()
        self.ctx.load_verify_locations(cadata=cad)
        cad = ssl.PEM_cert_to_DER_cert(cad)
        self.ctx.load_verify_locations(cadata=cad)
        self.ctx.load_verify_locations(data_file("cert_rsa.pem"), 'does_not_exit')
        self.ctx.load_verify_locations(StringWrapper(data_file("cert_rsa.pem")), )
        self.ctx.load_verify_locations(capath=StringWrapper(data_file("cert_rsa.pem")))

        with self.assertRaisesRegex(TypeError, "cafile should be a valid filesystem path"):
            self.ctx.load_verify_locations(1)
        with self.assertRaisesRegex(TypeError, "cafile should be a valid filesystem path"):
            self.ctx.load_verify_locations(1, 1)
        with self.assertRaisesRegex(TypeError, "capath should be a valid filesystem path"):
            self.ctx.load_verify_locations(capath=1)
        with self.assertRaisesRegex(TypeError, "capath should be a valid filesystem path"):
            self.ctx.load_verify_locations('a', capath=1)
        with self.assertRaisesRegex(TypeError, "cafile should be a valid filesystem path"):
            self.ctx.load_verify_locations(1, capath='a')

        self.check_load_verify_locations_error(cafile="does_not_exit", errno=2, strerror="No such file or directory", err=FileNotFoundError)
        self.check_load_verify_locations_error(cafile="does_not_exit", capath='does_not_exit', errno=2, strerror="No such file or directory", err=FileNotFoundError)

        self.check_load_verify_locations_error(cafile="empty.pem", errno=136, strerror="[X509: NO_CERTIFICATE_OR_CRL_FOUND] no certificate or crl found")
        self.check_load_verify_locations_error(cafile="empty_cert.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="empty_cert_at_begin.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="empty_cert_at_end.pem", strerror="[X509] PEM lib")

        # SEGFAULT on CPython ?!
        # self.check_load_verify_locations_error(cafile="broken_cert_double_begin.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="broken_cert_only_begin.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="broken_cert_no_end.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="broken_cert_data.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="broken_cert_data_at_begin.pem", strerror="[X509] PEM lib")
        self.check_load_verify_locations_error(cafile="broken_cert_data_at_end.pem", strerror="[X509] PEM lib")

        self.check_load_verify_locations_error(cadata="empty.pem", strerror="Empty certificate data", err=ValueError)
        self.check_load_verify_locations_error(cadata="empty_cert.pem")

        self.check_load_verify_locations_error(cadata="broken_cert_double_begin.pem")
        self.check_load_verify_locations_error(cadata="broken_cert_only_begin.pem")
        self.check_load_verify_locations_error(cadata="broken_cert_no_end.pem")
        self.check_load_verify_locations_error(cadata="broken_cert_data.pem")
        self.check_load_verify_locations_error(cadata="broken_cert_data_at_begin.pem")
        self.check_load_verify_locations_error(cadata="broken_cert_data_at_end.pem")

    def test_load_default_verify_paths(self):
        env = os.environ
        certFile = env["SSL_CERT_FILE"] if "SSL_CERT_FILE" in env else None
        certDir = env["SSL_CERT_DIR"] if "SSL_CERT_DIR" in env else None
        try:
            env["SSL_CERT_DIR"] = "does_not_exit"
            env["SSL_CERT_FILE"] = "does_not_exit"
            self.ctx.load_default_certs()
            env["SSL_CERT_DIR"] = data_file("empty.pem")
            env["SSL_CERT_FILE"] = data_file("empty.pem")
            self.ctx.load_default_certs()
            env["SSL_CERT_DIR"] = data_file("cert_rsa.pem")
            env["SSL_CERT_FILE"] = data_file("cert_rsa.pem")
            self.ctx.load_default_certs()
        except Exception:
            # load_default_certs reports no errors
            assert False
        finally:
            if certFile is not None:
                env["SSL_CERT_FILE"] = certFile
            else:
                del env["SSL_CERT_FILE"]
            if certDir is not None:
                env["SSL_CERT_DIR"] = certDir
            else:
                del env["SSL_CERT_DIR"]

    def test_verify_mode(self):
        signed_cert = data_file("signed_cert.pem")
        signed_cert2 = data_file("keycertecc.pem")
        signing_ca = data_file("signing_ca.pem")

        ########################################################################
        # verify_mode - client
        ########################################################################

        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)

        server_context.verify_mode = ssl.CERT_NONE

        client_context.check_hostname = False

        # no cert chain on server
        # openssl SSLError: [SSL: NO_SHARED_CIPHER] / jdk javax.net.ssl.SSLHandshakeException: No available authentication scheme
        client_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context, ssl.SSLError)
        client_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLError)
        client_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context, ssl.SSLError)

        # server provides cert, but client has noverify locations
        server_context.load_cert_chain(signed_cert)

        client_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context)
        client_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)
        client_context.verify_mode = ssl.CERT_OPTIONAL
        # CERT_OPTIONAL in client mode has the same meaning as CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        client_context.check_hostname = True

        with self.assertRaisesRegex(ValueError, "Cannot set verify_mode to CERT_NONE when check_hostname is enabled"):
            client_context.verify_mode = ssl.CERT_NONE

        client_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        client_context.verify_mode = ssl.CERT_OPTIONAL
        # CERT_OPTIONAL in client mode has the same meaning as CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        # client provides cert, server verifies
        client_context.load_verify_locations(signing_ca)

        client_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context)
        client_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context)

        # server provides wrong cert for CERT_OPTIONAL client
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(signed_cert2)
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        ########################################################################
        # verify_mode - server
        ########################################################################

        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)

        client_context.check_hostname = False
        client_context.verify_mode = ssl.CERT_NONE

        # no cert chain on server and client
        # openssl SSLError: [SSL: NO_SHARED_CIPHER] / jdk javax.net.ssl.SSLHandshakeException: No available authentication scheme
        server_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context, ssl.SSLError)
        server_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLError)
        server_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context, ssl.SSLError)

        # no cert from client
        server_context.load_cert_chain(signed_cert)

        server_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context)
        server_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLError)
        server_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context)

        # client provides cert, but server has nothing to verify with
        client_context.load_cert_chain(signed_cert)

        server_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context)
        server_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context, ssl.SSLError)
        server_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

        # client provides cert, server verifies
        server_context.load_verify_locations(signing_ca)

        server_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context)
        server_context.verify_mode = ssl.CERT_REQUIRED
        check_handshake(server_context, client_context)
        server_context.verify_mode = ssl.CERT_OPTIONAL
        check_handshake(server_context, client_context)

        # client provides wrong cert for CERT_OPTIONAL server
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.load_cert_chain(signed_cert2)
        check_handshake(server_context, client_context, ssl.SSLCertVerificationError)

    def check_keypair(self, signed_cert, signing_ca, password=None):
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        try:
            server_context.load_cert_chain(data_file(signed_cert), password=password)
        except ssl.SSLError as e:
            self.assertIn("require BouncyCastle support", str(e))
            return
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.load_verify_locations(data_file(signing_ca))
        check_handshake(server_context, client_context)

    def test_private_key_pkcs8(self):
        self.check_keypair("signed_cert.pem", "signing_ca.pem")

    def test_private_key_pkcs8_password(self):
        self.check_keypair("signed_cert_password.pem", "signing_ca.pem", password="password")

    def test_private_key_pkcs1(self):
        self.check_keypair("signed_cert_pkcs1.pem", "signing_ca.pem")

    def test_private_key_pkcs1_password(self):
        self.check_keypair("signed_cert_pkcs1_password.pem", "signing_ca.pem", password="password")

    def load_legacy_cert_chain(self, certfile, keyfile):
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        try:
            server_context.load_cert_chain(data_file(certfile), keyfile=data_file(keyfile))
        except ssl.SSLError as e:
            self.assertIn("require BouncyCastle support", str(e))
            return None
        return server_context

    def test_private_key_ec_legacy(self):
        server_context = self.load_legacy_cert_chain("keycertecc.pem", "pk_ecc_legacy.pem")
        if server_context is None:
            return
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.verify_mode = ssl.CERT_NONE
        check_handshake(server_context, client_context)

    def test_private_key_dsa_legacy(self):
        self.load_legacy_cert_chain("cert_dsa.pem", "pk_dsa_legacy.pem")

    def test_alpn(self):
        signed_cert = data_file("signed_cert.pem")
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(signed_cert)
        server_context.verify_mode = ssl.CERT_NONE
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.verify_mode = ssl.CERT_NONE
        server, client = check_handshake(server_context, client_context)
        self.assertIsNone(client.selected_alpn_protocol())

        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(signed_cert)
        server_context.set_alpn_protocols(["http/1.1"])
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.verify_mode = ssl.CERT_NONE
        client_context.set_alpn_protocols(["http/1.1"])
        server, client = check_handshake(server_context, client_context)
        self.assertEqual(client.selected_alpn_protocol(), "http/1.1")

    def test_peer_certificate_chain(self):
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(data_file("signed_cert.pem"))
        client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        client_context.check_hostname = False
        client_context.verify_mode = ssl.CERT_NONE
        _, client = check_handshake(server_context, client_context)

        peer_cert = client.getpeercert(binary_form=True)
        self.assertEqual(client.get_unverified_chain()[0], peer_cert)
        cert = client._sslobj.get_unverified_chain()[0]
        same_cert = client._sslobj.get_unverified_chain()[0]
        self.assertEqual(cert, same_cert)
        self.assertEqual(hash(cert), hash(same_cert))
        self.assertNotEqual(cert, object())
        self.assertTrue(repr(cert).startswith("<_ssl.Certificate '"))


class ProtocolSelectionTests(unittest.TestCase):

    def check_protocol_availability_controls_selection(self, has_name, protocol_name, protocol_id):
        if getattr(ssl, has_name):
            self.assertTrue(hasattr(ssl, protocol_name))
            ssl.SSLContext(getattr(ssl, protocol_name))
        else:
            protocol = getattr(ssl, protocol_name, protocol_id)
            with self.assertRaisesRegex(ValueError, "invalid or unsupported protocol version"):
                ssl.SSLContext(protocol)

    def test_single_version_availability_controls_selection(self):
        self.check_protocol_availability_controls_selection("HAS_SSLv3", "PROTOCOL_SSLv3", 1)
        self.check_protocol_availability_controls_selection("HAS_TLSv1", "PROTOCOL_TLSv1", 3)
        self.check_protocol_availability_controls_selection("HAS_TLSv1_1", "PROTOCOL_TLSv1_1", 4)
        self.check_protocol_availability_controls_selection("HAS_TLSv1_2", "PROTOCOL_TLSv1_2", 5)

    def test_generic_protocols_keep_sslv3_disabled(self):
        for protocol in (ssl.PROTOCOL_TLS, ssl.PROTOCOL_TLS_CLIENT, ssl.PROTOCOL_TLS_SERVER):
            with self.subTest(protocol=protocol):
                self.assertTrue(ssl.SSLContext(protocol).options & ssl.OP_NO_SSLv3)


def get_cipher_list(cipher_string):
    context = ssl.SSLContext()
    context.set_ciphers(cipher_string)
    return context.get_ciphers()


class CipherTests(unittest.TestCase):

    @unittest.skipUnless(IS_OPENSSL_3_0_0, "expected cipher list requires OpenSSL 3.0+")
    def test_set_ciphers(self):
        with open(data_file('expected_ciphers.json')) as fo:
            data = json.load(fo)
        for cipher_string, expected_output in data.items():
            try:
                output = get_cipher_list(cipher_string)
            except ssl.SSLError:
                self.fail(f"No cipher suites selected for list: {cipher_string}")
            self.assertGreater(len(output), 0)
            # JDK has just a subset of ciphers, test that the remaining ones are a subset of CPython's in the right order
            unexpected = set([x['name'] for x in output]) - set([x['name'] for x in expected_output])
            self.assertEqual(unexpected, set(), f"Cipher list: {cipher_string}\nUnexpected names: {unexpected}")
            matches = 0
            for entry in expected_output:
                if output[matches] == entry:
                    matches += 1
                    if matches == len(output):
                        break
            self.assertEqual(matches, len(output))

    def test_error(self):
        with self.assertRaisesRegex(ssl.SSLError, "No cipher can be selected"):
            get_cipher_list("ALL:!ALL:ADH")
        with self.assertRaisesRegex(ssl.SSLError, "No cipher can be selected"):
            get_cipher_list("ALL:@XXX")
        import sys
        if sys.implementation.name == "graalpy":
            with self.assertRaisesRegex(NotImplementedError, "only @SECLEVEL=1 is supported"):
                get_cipher_list("@SECLEVEL=2:ALL")
