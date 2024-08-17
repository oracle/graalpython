# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

from textwrap import dedent

if sys.implementation.name == "graalpy" and not __graalpython__.is_native:
    def test_load_default_verify_keystore():
        # execute with javax.net.ssl.trustStore=tests/ssldata/signing_keystore.jks
        # the JKS keystore:
        # - contains one trusted certificate, the same as in tests/ssldata/signing_ca.pem
        # - password is testssl
        curdir = os.path.abspath(os.path.dirname(__file__))
        src = dedent(f"""\
            import ssl, sys, os
            sys.path.append('{curdir}')
            from test_ssl import data_file, check_handshake
            server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            server_context.load_cert_chain(data_file('signed_cert.pem'))
            client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            check_handshake(server_context, client_context, ssl.SSLCertVerificationError)
            client_context.load_default_certs()
            check_handshake(server_context, client_context)
        """)
        env = os.environ.copy()
        env['JAVA_TOOL_OPTIONS'] = f"-Djavax.net.ssl.trustStore={curdir}/ssldata/signing_keystore.jks"

        args = []
        if __graalpython__.is_bytecode_dsl_interpreter:
            args += ['--vm.Dpython.EnableBytecodeDSLInterpreter=true']

        subprocess.run([sys.executable, *args, '-c', src], env=env, check=True)
