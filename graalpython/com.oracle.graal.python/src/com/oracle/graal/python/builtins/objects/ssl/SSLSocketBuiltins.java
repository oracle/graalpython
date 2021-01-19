package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
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
            if (len < 0) {
                throw raise(ValueError, ErrorMessages.SIZE_SHOULD_NOT_BE_NEGATIVE);
            }
            ByteBuffer output = ByteBuffer.allocate(len);
            SSLEngineHelper.read(this, self, output);
            return factory().createBytes(output.array(), output.limit());
        }

        @Specialization
        Object readInto(PSSLSocket self, int len, PByteArray buffer,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemScalarNode) {
            if (len < 0) {
                throw raise(ValueError, ErrorMessages.SIZE_SHOULD_NOT_BE_NEGATIVE);
            }
            // TODO write directly to the backing array
            SequenceStorage storage = getSequenceStorageNode.execute(buffer);
            int length = Math.min(len, lenNode.execute(storage));
            ByteBuffer output = ByteBuffer.allocate(length);
            SSLEngineHelper.read(this, self, output);
            output.flip();
            for (int i = 0; i < output.limit(); i++) {
                setItemScalarNode.execute(storage, i, output.get(i));
            }
            return output.limit();
        }

        // TODO arrays, memoryview
        // TODO proper error for bytes

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object len, Object buffer) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, buffer);
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
                int lenght = bufferLib.getBufferLength(buffer);
                ByteBuffer input = ByteBuffer.wrap(bytes, 0, lenght);
                SSLEngineHelper.write(this, self, input);
                return lenght;
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

    @Builtin(name = "context", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PSSLContext getContext(PSSLSocket self) {
            return self.getContext();
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
        PDict getPeerCertDict(PSSLSocket self, @SuppressWarnings("unused") boolean der,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            if (!self.isHandshakeComplete()) {
                throw raise(ValueError, ErrorMessages.HANDSHAKE_NOT_DONE_YET);
            }
            Certificate certificate = getCertificate(self.getEngine());
            if (certificate instanceof X509Certificate) {
                try {
                    X509Certificate x509Certificate = (X509Certificate) certificate;
                    PDict dict = factory().createDict();
                    HashingStorage storage = dict.getDictStorage();
                    storage = hlib.setItem(storage, "subject", factory().createTuple(parseSubject(x509Certificate)));
                    storage = hlib.setItem(storage, "subjectAltName", factory().createTuple(parseSubjectAltName(x509Certificate)));
                    storage = hlib.setItem(storage, "version", getVersion(x509Certificate));
                    storage = hlib.setItem(storage, "serialNumber", getSerialNumber(x509Certificate));
                    // TODO more entries
                    dict.setDictStorage(storage);
                    return dict;
                } catch (CertificateParsingException e) {
                    return factory().createDict();
                }
            }
            return factory().createDict();
        }

        @TruffleBoundary
        private static String getSerialNumber(X509Certificate x509Certificate) {
            return x509Certificate.getSerialNumber().toString(16).toUpperCase();
        }

        @TruffleBoundary
        private static int getVersion(X509Certificate x509Certificate) {
            return x509Certificate.getVersion();
        }

        @TruffleBoundary
        private Object[] parseSubject(X509Certificate certificate) {
            String name = certificate.getSubjectDN().getName();
            List<Object> tuples = new ArrayList<>(16);
            for (String component : name.split(",")) {
                String[] kv = component.split("=");
                if (kv.length == 2) {
                    tuples.add(factory().createTuple(new Object[]{ASN1Helper.translateKeyToPython(kv[0].trim()), kv[1].trim()}));
                }
            }
            return tuples.toArray(new Object[0]);
        }

        @TruffleBoundary
        private Object[] parseSubjectAltName(X509Certificate certificate) throws CertificateParsingException {
            // TODO null
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            List<Object> tuples = new ArrayList<>(16);
            for (List<?> altName : altNames) {
                if (altName.size() == 2 && altName.get(0) instanceof Integer) {
                    int type = (Integer) altName.get(0);
                    Object value = altName.get(1);
                    switch (type) {
                        case 2:
                            tuples.add(factory().createTuple(new Object[]{"DNS", value}));
                            break;
                        default:
                            // TODO other types
                            continue;
                    }
                }
            }
            return tuples.toArray(new Object[0]);
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
            if ("tls-unique".equals(sbType)) {
                // JDK doesn't have an API to access what we need. Bouncycastle could provide this
                throw raise(NotImplementedError);
            }
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
            Object[] cipherTuple = getCipherTuple(self.getEngine());
            if (cipherTuple == null) {
                return PNone.NONE;
            }
            return factory().createTuple(cipherTuple);
        }

        @TruffleBoundary
        private static Object[] getCipherTuple(SSLEngine engine) {
            SSLSession session = engine.getSession();
            String cipherSuite = session.getCipherSuite();
            String protocol = session.getProtocol();
            if (cipherSuite != null && protocol != null) {
                // TODO I don't see a nice way to obtain the number of bits, we have to rely on
                // string matching
                int cipherBits = 0;
                if (cipherSuite.contains("_AES_128_")) {
                    cipherBits = 128;
                } else if (cipherSuite.contains("_AES_256_")) {
                    cipherBits = 256;
                }
                return new Object[]{cipherSuite, protocol, cipherBits};
            }
            return null;
        }
    }
}
