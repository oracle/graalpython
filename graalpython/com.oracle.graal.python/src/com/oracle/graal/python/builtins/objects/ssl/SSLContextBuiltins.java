package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.xml.bind.DatatypeConverter;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLContext)
public class SSLContextBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLContextBuiltinsFactory.getFactories();
    }

    @Builtin(name = "_SSLContext", constructsClass = PythonBuiltinClassType.PSSLContext, minNumOfPositionalArgs = 2, parameterNames = {"type", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SSLContextNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PSSLContext createContext(Object type, int protocol) {
            SSLProtocolVersion version = SSLProtocolVersion.fromPythonId(protocol);
            if (version == null) {
                throw raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, "NULL");
            }
            try {
                return factory().createSSLContext(type, version, createSSLContext(version));
            } catch (NoSuchAlgorithmException e) {
                throw raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, e.getMessage());
            } catch (KeyManagementException e) {
                // TODO when does this happen?
                throw raise(SSLError, e);
            }
        }

        @TruffleBoundary
        private static SSLContext createSSLContext(SSLProtocolVersion version) throws NoSuchAlgorithmException, KeyManagementException {
            SSLContext context = SSLContext.getInstance(version.getJavaId());
            context.init(null, null, null);
            return context;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.SSLContextNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "check_hostname", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class CheckHostnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static boolean getCheckHostname(PSSLContext self, @SuppressWarnings("unused") PNone none) {
            return self.getCheckHostname();
        }

        @Specialization(guards = "!isNoValue(value)", limit = "3")
        static Object setCheckHostname(PSSLContext self, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            self.setCheckHostname(lib.isTrue(value));
            // TODO check_hostname = True sets verify_mode = CERT_REQUIRED
            return PNone.NONE;
        }
    }

    @Builtin(name = "protocol", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ProtocolNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getProtocol(PSSLContext self) {
            return self.getVersion().getPythonId();
        }
    }

    @Builtin(name = "verify_mode", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class VerifyModeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PSSLContext self, @SuppressWarnings("unused") PNone value) {
            return self.getVerifyMode();
        }

	@Specialization(guards = "canBeInteger(value)", limit = "3")
        Object set(PSSLContext self, Object value,
	  @CachedLibrary("value") PythonObjectLibrary lib) {
	    int mode = lib.asSize(value);
            if (mode == SSLModuleBuiltins.SSL_CERT_NONE && self.getCheckHostname()) {
                throw raise(ValueError, ErrorMessages.CANNOT_SET_VERIFY_MODE_TO_CERT_NONE);
            }
            switch (mode) {
                case SSLModuleBuiltins.SSL_CERT_NONE:
                case SSLModuleBuiltins.SSL_CERT_OPTIONAL:
                case SSLModuleBuiltins.SSL_CERT_REQUIRED:
                    self.setVerifyMode(mode);
                    return PNone.NONE;
                default:
                    throw raise(ValueError, ErrorMessages.INVALID_VALUE_FOR_VERIFY_MODE);
            }
        }
	
	@SuppressWarnings("unused")
        @Specialization(guards = "!canBeInteger(value)")
        Object set(PSSLContext self, Object value) {
	    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, value);	    
	}
    }

    @Builtin(name = "load_verify_locations", minNumOfPositionalArgs = 1, parameterNames = {"$self", "cafile", "capath", "cadata"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadVerifyLocationsNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object load(PSSLContext self, Object cafile, Object capath, Object cadata) {
	    // TODO
            return PNone.NONE;
        }
    }

    @Builtin(name = "load_cert_chain", minNumOfPositionalArgs = 2, parameterNames = {"$self", "certfile", "keyfile", "password"})
    @ArgumentClinic(name = "certfile", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "keyfile", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"\"", useDefaultForNone = true)
    @ArgumentClinic(name = "password", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"\"", useDefaultForNone = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadCertChainNode extends PythonQuaternaryClinicBuiltinNode {

        private static final String END_CERTIFICATE = "END CERTIFICATE";
        private static final String BEGIN_CERTIFICATE = "BEGIN CERTIFICATE";
        private static final String END_PRIVATE_KEY = "END PRIVATE KEY";
        private static final String BEGIN_PRIVATE_KEY = "BEGIN PRIVATE KEY";

        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, String certfile, String keyfile, String password) {
            // TODO trufflefile ?
            File certPem = new File(certfile);
            if (!certPem.exists()) {
                throw raiseOSError(frame, OSErrorEnum.ENOENT);
            }
            File pkPem = "".equals(keyfile) ? certPem : new File(keyfile);

            try {
                // TODO: preliminary implementation - import and convert PEM format to java keystore
                X509Certificate[] cert = getCertificates(certPem);
                KeyStore keystore = KeyStore.getInstance("JKS");
                keystore.load(null);
                PrivateKey key = getPrivateKey(pkPem);
                keystore.setKeyEntry(pkPem.getName(), key, "".equals(password) ? password.toCharArray() : PythonUtils.EMPTY_CHAR_ARRAY, cert);

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(keystore, password.toCharArray());
                KeyManager[] km = kmf.getKeyManagers();
                self.getContext().init(km, null, null);
            } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | InvalidKeySpecException | UnrecoverableKeyException | KeyManagementException ex) {
                throw raise(SSLError, ErrorMessages.SSL_PEM_LIB_S, ex.getMessage());
            } catch (IOException ex) {
                // TODO
                throw raise(ValueError, ex.getMessage());
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.LoadCertChainNodeClinicProviderGen.INSTANCE;
        }

        private X509Certificate[] getCertificates(File pem) throws IOException, CertificateException {
            try (BufferedReader r = new BufferedReader(new FileReader(pem))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(BEGIN_CERTIFICATE)) {
                        break;
                    }
                }
                if (line == null) {
                    // TODO: append any additional info? original msg is e.g. "[SSL] PEM lib
                    // (_ssl.c:3991)"
                    throw raise(SSLError, ErrorMessages.SSL_PEM_LIB_S, "no certificate found in certfile");
                }
                StringBuilder sb = new StringBuilder();
                List<X509Certificate> res = new ArrayList<>();
                while ((line = r.readLine()) != null) {
                    if (line.contains(END_CERTIFICATE)) {
                        String hexString = sb.toString();
                        CertificateFactory factory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(DatatypeConverter.parseBase64Binary(hexString)));
                        res.add(cert);
                        sb = new StringBuilder();
                    } else {
                        sb.append(line);
                    }

                }
                return res.toArray(new X509Certificate[res.size()]);
            }
        }

        private PrivateKey getPrivateKey(File pkPem) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
            try (BufferedReader r = new BufferedReader(new FileReader(pkPem))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(BEGIN_PRIVATE_KEY)) {
                        break;
                    }
                }
                if (line == null) {
                    // TODO: append any additional info? original msg is e.g. "[SSL] PEM lib
                    // (_ssl.c:3991)"
                    throw raise(SSLError, ErrorMessages.SSL_PEM_LIB_S, "no private key found in keyfile/certfile");
                }
                StringBuilder sb = new StringBuilder();
                while ((line = r.readLine()) != null) {
                    if (line.contains(END_PRIVATE_KEY)) {
                        break;
                    }
                    sb.append(line);
                }
                byte[] bytes = DatatypeConverter.parseBase64Binary(sb.toString());
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
                final KeyFactory factory = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) factory.generatePrivate(spec);
            }
        }
    }

    @Builtin(name = "_wrap_socket", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sock", "server_side", "server_hostname"}, keywordOnlyNames = {"owner", "session"})
    @ArgumentClinic(name = "server_side", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "server_hostname", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "null", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class WrapSocketNode extends PythonClinicBuiltinNode {
        @Specialization
        // TODO parameters
        Object wrap(PSSLContext context, PSocket sock, boolean serverSide, String serverHostname, Object owner, Object session) {
            // TODO hostname
            // TODO hostname encode as IDNA?
            // TODO hostname can be null
            SSLContext javaContext = context.getContext();
            return factory().createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, sock, createSSLEngine(javaContext, !serverSide));
        }

        @TruffleBoundary
        private static SSLEngine createSSLEngine(SSLContext javaContext, boolean clientMode) {
            SSLEngine engine = javaContext.createSSLEngine();
            engine.setUseClientMode(clientMode);
            return engine;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.WrapSocketNodeClinicProviderGen.INSTANCE;
        }
    }
}
