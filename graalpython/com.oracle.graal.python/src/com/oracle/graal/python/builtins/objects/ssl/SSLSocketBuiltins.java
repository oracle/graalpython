package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.io.IOException;
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
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLSocket)
public class SSLSocketBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLSocketBuiltinsFactory.getFactories();
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, parameterNames = {"$self", "len", "buffer"})
    @ArgumentClinic(name = "len", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(guards = "isNoValue(buffer)")
        Object read(PSSLSocket self, int len, @SuppressWarnings("unused") PNone buffer) {
            if (len == 0) {
                return factory().createBytes(new byte[0]);
            } else if (len < 0) {
                throw raise(ValueError, ErrorMessages.SIZE_SHOULD_NOT_BE_NEGATIVE);
            }
            ByteBuffer output = doRead(self, len);
            return factory().createBytes(PythonUtils.getBufferArray(output), PythonUtils.getBufferLimit(output));
        }

        @Specialization
        Object readInto(PSSLSocket self, int len, PByteArray buffer,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemScalarNode) {
            // TODO write directly to the backing array
            SequenceStorage storage = getSequenceStorageNode.execute(buffer);
            int storageLength = lenNode.execute(storage);
            int bufferLength = len;
            if (len <= 0 || len > storageLength) {
                bufferLength = storageLength;
            }
            if (bufferLength == 0) {
                return 0;
            }
            ByteBuffer output = doRead(self, bufferLength);
            int readBytes = PythonUtils.getBufferRemaining(output);
            byte[] array = PythonUtils.getBufferArray(output);
            for (int i = 0; i < readBytes; i++) {
                setItemScalarNode.execute(storage, i, array[i]);
            }
            return readBytes;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object errorBytes(PSSLSocket self, int len, PBytes bytes) {
            throw raise(TypeError, "read() argument 2 must be read-write bytes-like object, not bytes");
        }

        // TODO arrays, memoryview
        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object len, Object buffer) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, buffer);
        }

        @TruffleBoundary
        private ByteBuffer doRead(PSSLSocket self, int bufferLength) {
            ByteBuffer output = ByteBuffer.allocate(bufferLength);
            SSLEngineHelper.read(this, self, output);
            output.flip();
            return output;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLSocketBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"})
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        Object write(PSSLSocket self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                byte[] bytes = bufferLib.getBufferBytes(buffer);
                int length = bufferLib.getBufferLength(buffer);
                ByteBuffer input = PythonUtils.wrapByteBuffer(bytes, 0, length);
                SSLEngineHelper.write(this, self, input);
                return length;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object buffer) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, buffer);
        }
    }

    @Builtin(name = "do_handshake", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DoHandshakeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doHandshake(PSSLSocket self) {
            SSLEngineHelper.handshake(this, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "shutdown", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ShutdownNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object shutdown(PSSLSocket self) {
            SSLEngineHelper.shutdown(this, self);
            return PNone.NONE;
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
        static Object getVersion(PSSLSocket self) {
            if (self.isHandshakeComplete()) {
                return getProtocol(self.getEngine());
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
        Object getPeerCertDER(PSSLSocket self, @SuppressWarnings("unused") boolean der) {
            if (!self.isHandshakeComplete()) {
                throw raise(ValueError, ErrorMessages.HANDSHAKE_NOT_DONE_YET);
            }
            Certificate certificate = getCertificate(self.getEngine());
            if (certificate != null) {
                try {
                    return factory().createBytes(getEncoded(certificate));
                } catch (CertificateEncodingException e) {
                    // Fallthrough
                }
            }
            // msimacek: In CPython, this is able to return unverified certificates. I don't see a
            // way to do it in the Java API
            return PNone.NONE;
        }

        @Specialization(guards = "!der")
        PDict getPeerCertDict(PSSLSocket self, @SuppressWarnings("unused") boolean der) {
            if (!self.isHandshakeComplete()) {
                throw raise(ValueError, ErrorMessages.HANDSHAKE_NOT_DONE_YET);
            }
            Certificate certificate = getCertificate(self.getEngine());
            if (certificate instanceof X509Certificate) {
                try {
                    return CertUtils.decodeCertificate((X509Certificate) certificate);
                } catch (CertificateParsingException e) {
                    return factory().createDict();
                } catch (IOException ex) {
                    throw raise(SSLError, ex);
                }
            }
            return factory().createDict();
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
    @ArgumentClinic(name = "sb_type", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"tls-unique\"")
    @GenerateNodeFactory
    abstract static class GetChannelBinding extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object getChannelBinding(PSSLSocket self, String sbType) {
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
        Object getCipher(PSSLSocket self) {
            if (!self.isHandshakeComplete()) {
                return PNone.NONE;
            }
            SSLCipher cipher = getCipher(self.getEngine());
            if (cipher == null) {
                return PNone.NONE;
            }
            return factory().createTuple(new Object[]{cipher.getOpensslName(), cipher.getProtocol(), cipher.getStrengthBits()});
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
                result[i] = factory().createTuple(new Object[]{cipher.getOpensslName(), cipher.getProtocol(), cipher.getStrengthBits()});
            }
            return factory().createList(result);
        }
    }

    @Builtin(name = "selected_alpn_protocol", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SelectedAlpnProtocol extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSSLSocket socket) {
            String protocol = null;
            if (ALPNHelper.hasAlpn()) {
                protocol = ALPNHelper.getApplicationProtocol(socket.getEngine());
            }
            return protocol != null ? protocol : PNone.NONE;
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
