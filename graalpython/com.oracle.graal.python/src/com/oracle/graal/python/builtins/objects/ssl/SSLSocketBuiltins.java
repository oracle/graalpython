/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLSocket)
public final class SSLSocketBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLSocketBuiltinsFactory.getFactories();
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, parameterNames = {"$self", "len", "buffer"})
    @ArgumentClinic(name = "len", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(guards = "isNoValue(buffer)")
        static Object read(VirtualFrame frame, PSSLSocket self, int len, @SuppressWarnings("unused") PNone buffer,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SSLOperationNode sslOperationNode,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (len == 0) {
                return factory.createBytes(new byte[0]);
            } else if (len < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SIZE_SHOULD_NOT_BE_NEGATIVE);
            }
            ByteBuffer output = PythonUtils.allocateByteBuffer(len);
            sslOperationNode.read(frame, inliningTarget, self, output);
            PythonUtils.flipBuffer(output);
            return factory.createBytes(PythonUtils.getBufferArray(output), PythonUtils.getBufferLimit(output));
        }

        @Specialization(guards = "!isNoValue(bufferObj)", limit = "3")
        Object readInto(VirtualFrame frame, PSSLSocket self, int len, Object bufferObj,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached SSLOperationNode sslOperationNode,
                        // unused node to avoid mixing shared and non-shared inlined nodes
                        @SuppressWarnings("unused") @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireWritableWithTypeError(bufferObj, "read", frame, this);
            try {
                int bufferLen = bufferLib.getBufferLength(buffer);
                int toReadLen = len;
                if (len <= 0 || len > bufferLen) {
                    toReadLen = bufferLen;
                }
                if (toReadLen == 0) {
                    return 0;
                }
                byte[] bytes;
                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    bytes = new byte[toReadLen];
                }
                ByteBuffer output = PythonUtils.wrapByteBuffer(bytes, 0, toReadLen);
                sslOperationNode.read(frame, inliningTarget, self, output);
                PythonUtils.flipBuffer(output);
                int readBytes = PythonUtils.getBufferRemaining(output);
                if (!directWrite) {
                    bufferLib.writeFromByteArray(buffer, 0, bytes, 0, readBytes);
                }
                return readBytes;
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLSocketBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object write(VirtualFrame frame, PSSLSocket self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached SSLOperationNode sslOperationNode) {
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int length = bufferLib.getBufferLength(buffer);
                ByteBuffer input = PythonUtils.wrapByteBuffer(bytes, 0, length);
                sslOperationNode.write(frame, inliningTarget, self, input);
                return length;
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLSocketBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "do_handshake", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DoHandshakeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doHandshake(VirtualFrame frame, PSSLSocket self,
                        @Bind("this") Node inliningTarget,
                        @Cached SSLOperationNode sslOperationNode) {
            sslOperationNode.handshake(frame, inliningTarget, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "shutdown", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ShutdownNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object shutdown(VirtualFrame frame, PSSLSocket self,
                        @Bind("this") Node inliningTarget,
                        @Cached SSLOperationNode sslOperationNode) {
            sslOperationNode.shutdown(frame, inliningTarget, self);
            return self.getSocket() != null ? self.getSocket() : PNone.NONE;
        }
    }

    @Builtin(name = "context", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PSSLContext getContext(PSSLSocket self) {
            return self.getContext();
        }
    }

    @Builtin(name = "server_side", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ServerSideNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static boolean get(PSSLSocket self) {
            return !self.getEngine().getUseClientMode();
        }
    }

    @Builtin(name = "server_hostname", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ServerHostnameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSSLSocket self) {
            return self.getServerHostname() != null ? self.getServerHostname() : PNone.NONE;
        }
    }

    @Builtin(name = "version", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class VersionNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getVersion(PSSLSocket self,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (self.isHandshakeComplete()) {
                return fromJavaStringNode.execute(getProtocol(self.getEngine()), TS_ENCODING);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static String getProtocol(SSLEngine engine) {
            return engine.getSession().getProtocol();
        }
    }

    @Builtin(name = "pending", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PendingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getPending(PSSLSocket self) {
            return self.getApplicationInboundBIO().getPending();
        }
    }

    @Builtin(name = "owner", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class OwnerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PSSLSocket self, @SuppressWarnings("unused") Object none) {
            return self.getOwner() != null ? self.getOwner() : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object set(PSSLSocket self, Object obj) {
            self.setOwner(obj);
            return PNone.NONE;
        }
    }

    @Builtin(name = "session", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class SessionNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(@SuppressWarnings("unused") PSSLSocket self, @SuppressWarnings("unused") Object none) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(obj)")
        Object set(@SuppressWarnings("unused") PSSLSocket self, @SuppressWarnings("unused") Object obj) {
            // JDK API doesn't support setting session ID
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "session_reused", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SessionReusedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(@SuppressWarnings("unused") PSSLSocket self) {
            return false;
        }
    }

    @Builtin(name = "getpeercert", minNumOfPositionalArgs = 1, parameterNames = {"$self", "der"})
    @ArgumentClinic(name = "der", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class GetPeerCertNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(guards = "der")
        Object getPeerCertDER(PSSLSocket self, @SuppressWarnings("unused") boolean der,
                        @Cached PythonObjectFactory factory) {
            if (!self.isHandshakeComplete()) {
                throw raise(ValueError, ErrorMessages.HANDSHAKE_NOT_DONE_YET);
            }
            Certificate certificate = getCertificate(self.getEngine());
            if (certificate != null) {
                try {
                    return factory.createBytes(getEncoded(certificate));
                } catch (CertificateEncodingException e) {
                    // Fallthrough
                }
            }
            // msimacek: In CPython, this is able to return unverified certificates. I don't see a
            // way to do it in the Java API
            return PNone.NONE;
        }

        @Specialization(guards = "!der")
        PDict getPeerCertDict(PSSLSocket self, @SuppressWarnings("unused") boolean der,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (!self.isHandshakeComplete()) {
                throw raise(ValueError, ErrorMessages.HANDSHAKE_NOT_DONE_YET);
            }
            Certificate certificate = getCertificate(self.getEngine());
            if (certificate instanceof X509Certificate) {
                try {
                    return CertUtils.decodeCertificate(getContext().factory(), (X509Certificate) certificate);
                } catch (CertificateParsingException e) {
                    return factory.get(inliningTarget).createDict();
                }
            }
            return factory.get(inliningTarget).createDict();
        }

        @TruffleBoundary
        private static Certificate getCertificate(SSLEngine engine) {
            try {
                Certificate[] certificates = engine.getSession().getPeerCertificates();
                if (certificates.length != 0) {
                    return certificates[0];
                } else {
                    return null;
                }
            } catch (SSLPeerUnverifiedException e) {
                return null;
            }
        }

        @TruffleBoundary
        private static byte[] getEncoded(Certificate certificate) throws CertificateEncodingException {
            return certificate.getEncoded();
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLSocketBuiltinsClinicProviders.GetPeerCertNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "get_channel_binding", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sb_type"})
    @ArgumentClinic(name = "sb_type", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_TLS_UNIQUE")
    @GenerateNodeFactory
    abstract static class GetChannelBinding extends PythonBinaryClinicBuiltinNode {
        static final TruffleString T_TLS_UNIQUE = tsLiteral("tls-unique");

        @Specialization
        @SuppressWarnings("unused")
        Object getChannelBinding(PSSLSocket self, TruffleString sbType) {
            // JDK doesn't have an API to access what we need. BouncyCastle could provide this
            throw raise(ValueError, ErrorMessages.S_CHANNEL_BINDING_NOT_IMPLEMENTED, sbType);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLSocketBuiltinsClinicProviders.GetChannelBindingClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "cipher", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CipherNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getCipher(PSSLSocket self,
                        @Cached PythonObjectFactory factory) {
            if (!self.isHandshakeComplete()) {
                return PNone.NONE;
            }
            SSLCipher cipher = getCipher(self.getEngine());
            if (cipher == null) {
                return PNone.NONE;
            }
            return factory.createTuple(new Object[]{cipher.getOpensslName(), cipher.getProtocol(), cipher.getStrengthBits()});
        }

        @TruffleBoundary
        private static SSLCipher getCipher(SSLEngine engine) {
            SSLSession session = engine.getSession();
            String cipherSuite = session.getCipherSuite();
            if (cipherSuite != null) {
                return SSLCipher.valueOf(cipherSuite);
            }
            return null;
        }
    }

    @Builtin(name = "shared_ciphers", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SharedCiphers extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(PSSLSocket socket) {
            List<SSLCipher> ciphers = socket.getContext().computeEnabledCiphers(socket.getEngine());
            Object[] result = new Object[ciphers.size()];
            for (int i = 0; i < ciphers.size(); i++) {
                SSLCipher cipher = ciphers.get(i);
                result[i] = PythonObjectFactory.getUncached().createTuple(new Object[]{cipher.getOpensslName(), cipher.getProtocol(), cipher.getStrengthBits()});
            }
            return PythonObjectFactory.getUncached().createList(result);
        }
    }

    @Builtin(name = "selected_alpn_protocol", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SelectedAlpnProtocol extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object get(PSSLSocket socket) {
            String protocol = socket.getEngine().getApplicationProtocol();
            return protocol != null && !protocol.isEmpty() ? toTruffleStringUncached(protocol) : PNone.NONE;
        }
    }

    @Builtin(name = "compression", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CompressionNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(@SuppressWarnings("unused") PSSLSocket self) {
            // JSSE doesn't support compression. Neither does OpenSSL in regular distribution
            // builds. Compression is discouraged because it opens up possibilities for CRIME-type
            // attacks
            return PNone.NONE;
        }
    }
}
