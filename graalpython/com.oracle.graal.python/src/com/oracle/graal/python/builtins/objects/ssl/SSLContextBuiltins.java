package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLContext)
public class SSLContextBuiltins extends PythonBuiltins {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";

    private static final String BEGIN_DH_PARAMETERS = "-----BEGIN DH PARAMETERS-----";
    private static final String END_DH_PARAMETERS = "-----END DH PARAMETERS-----";

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
            SSLMethod method = SSLMethod.fromPythonId(protocol);
            if (method == null) {
                throw raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, "NULL");
            }
            try {
                boolean checkHostname;
                int verifyMode;
                if (method == SSLMethod.TLS_CLIENT) {
                    checkHostname = true;
                    verifyMode = SSLModuleBuiltins.SSL_CERT_REQUIRED;
                } else {
                    checkHostname = false;
                    verifyMode = SSLModuleBuiltins.SSL_CERT_NONE;
                }
                SSLContext sslContext = createSSLContext(method);
                PSSLContext context = factory().createSSLContext(type, method, SSLModuleBuiltins.X509_V_FLAG_TRUSTED_FIRST, checkHostname, verifyMode, sslContext);
                long options = SSLOptions.DEFAULT_OPTIONS;
                if (method != SSLMethod.SSL2) {
                    options |= SSLOptions.SSL_OP_NO_SSLv2;
                }
                if (method != SSLMethod.SSL3) {
                    options |= SSLOptions.SSL_OP_NO_SSLv3;
                }
                context.setOptions(options);
                return context;
            } catch (NoSuchAlgorithmException e) {
                throw raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, e.getMessage());
            } catch (KeyManagementException e) {
                // TODO when does this happen?
                throw raise(SSLError, e);
            }
        }

        @TruffleBoundary
        private static SSLContext createSSLContext(SSLMethod version) throws NoSuchAlgorithmException, KeyManagementException {
            return SSLContext.getInstance(version.getJavaId());
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.SSLContextNodeClinicProviderGen.INSTANCE;
        }
    }

    @TruffleBoundary
    // TODO session
    static SSLEngine createSSLEngine(PNodeWithRaise node, PSSLContext context, boolean serverMode, String serverHostname, Object session) {
        try {
            context.init();
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException | UnrecoverableKeyException | KeyManagementException ex) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, ex.toString());
        }
        SSLEngine engine = context.getContext().createSSLEngine();
        engine.setUseClientMode(!serverMode);
        List<String> selectedProtocols = new ArrayList<>(SSLModuleBuiltins.supportedProtocols);
        selectedProtocols.retainAll(Arrays.asList(engine.getSupportedProtocols()));
        if (context.getMethod().isSingleVersion()) {
            selectedProtocols.retainAll(Collections.singletonList(context.getMethod().getJavaId()));
        }
        for (SSLProtocol protocol : SSLProtocol.values()) {
            if ((context.getOptions() & protocol.getDisableOption()) != 0) {
                selectedProtocols.remove(protocol.getName());
            }
        }
        engine.setEnabledProtocols(selectedProtocols.toArray(new String[0]));
        SSLParameters parameters = new SSLParameters();
        if (serverHostname != null) {
            try {
                parameters.setServerNames(Collections.singletonList(new SNIHostName(serverHostname)));
            } catch (IllegalArgumentException e) {
                if (serverHostname.contains("\0")) {
                    throw node.raise(TypeError, "argument must be encoded string without null bytes");
                }
                throw node.raise(ValueError, "invalid hostname");
            }
            if (context.getCheckHostname()) {
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
            }
        }
        SSLCipher[] ciphers = context.getCiphers();
        if (ciphers != null) {
            String[] cipherNames = new String[ciphers.length];
            for (int i = 0; i < ciphers.length; i++) {
                cipherNames[i] = ciphers[i].name();
            }
            parameters.setCipherSuites(cipherNames);
        }
        if (ALPNHelper.hasAlpn() && context.getAlpnProtocols() != null) {
            ALPNHelper.setApplicationProtocols(parameters, context.getAlpnProtocols());
        }
        engine.setSSLParameters(parameters);
        return engine;
    }

    @Builtin(name = "_wrap_socket", minNumOfPositionalArgs = 3, parameterNames = {"$self", "sock", "server_side", "server_hostname"}, keywordOnlyNames = {"owner", "session"})
    @ArgumentClinic(name = "server_side", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class WrapSocketNode extends PythonClinicBuiltinNode {
        @Specialization
        Object wrap(PSSLContext context, PSocket sock, boolean serverSide, Object serverHostnameObj, Object owner, Object session,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            String serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(this, context, serverSide, serverHostname, session);
            PSSLSocket sslSocket = factory().createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, sock);
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            return sslSocket;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object wrap(Object context, Object sock, Object serverSide, Object serverHostname, Object owner, Object session) {
            throw raise(TypeError, "invalid _wrap_socket call");
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.WrapSocketNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "_wrap_bio", minNumOfPositionalArgs = 4, parameterNames = {"$self", "incoming", "outgoing", "server_side", "server_hostname"}, keywordOnlyNames = {"owner", "session"})
    @ArgumentClinic(name = "server_side", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class WrapBIONode extends PythonClinicBuiltinNode {
        @Specialization
        Object wrap(PSSLContext context, PMemoryBIO incoming, PMemoryBIO outgoing, boolean serverSide, Object serverHostnameObj, Object owner, Object session,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            String serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(this, context, serverSide, serverHostname, session);
            PSSLSocket sslSocket = factory().createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, incoming.getBio(), outgoing.getBio());
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            return sslSocket;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object wrap(Object context, Object incoming, Object outgoing, Object serverSide, Object serverHostname, Object owner, Object session) {
            throw raise(TypeError, "invalid _wrap_bio call");
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.WrapBIONodeClinicProviderGen.INSTANCE;
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
            boolean checkHostname = lib.isTrue(value);
            if (checkHostname && self.getVerifyMode() == SSLModuleBuiltins.SSL_CERT_NONE) {
                self.setVerifyMode(SSLModuleBuiltins.SSL_CERT_REQUIRED);
            }
            self.setCheckHostname(checkHostname);
            return PNone.NONE;
        }
    }

    @Builtin(name = "verify_flags", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class VerifyFlagsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static long getVerifyFlags(PSSLContext self, @SuppressWarnings("unused") PNone none) {
            return self.getVerifyFlags();
        }

        @Specialization(guards = "!isNoValue(flags)", limit = "3")
        Object setVerifyFlags(VirtualFrame frame, PSSLContext self, Object flags,
                        @CachedLibrary("flags") PythonObjectLibrary lib,
                        @Cached CastToJavaLongExactNode castToLong) {
            try {
                self.setVerifyFlags((int) castToLong.execute(lib.asIndexWithFrame(flags, frame)));
            } catch (CannotCastException cannotCastException) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "", "int", flags);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "protocol", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ProtocolNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getProtocol(PSSLContext self) {
            return self.getMethod().getPythonId();
        }
    }

    @Builtin(name = "options", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class OptionsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static long getOptions(PSSLContext self, @SuppressWarnings("unused") PNone none) {
            return self.getOptions();
        }

        @Specialization(guards = "!isNoValue(valueObj)", limit = "3")
        static Object setOption(VirtualFrame frame, PSSLContext self, Object valueObj,
                        @Cached CastToJavaLongExactNode cast,
                        @CachedLibrary("valueObj") PythonObjectLibrary lib) {
            long value = cast.execute(lib.asIndexWithFrame(valueObj, frame));
            // TODO validate the options
            // TODO use the options
            self.setOptions(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "verify_mode", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class VerifyModeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PSSLContext self, @SuppressWarnings("unused") PNone value) {
            return self.getVerifyMode();
        }

        @Specialization(guards = "!isNoValue(value)", limit = "3")
        Object set(VirtualFrame frame, PSSLContext self, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongExactNode castToLong) {
            int mode;
            try {
                mode = (int) castToLong.execute(lib.asIndexWithFrame(value, frame));
            } catch (CannotCastException cannotCastException) {
                throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, value);
            }
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
    }

    @Builtin(name = "get_ciphers", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetCiphersNode extends PythonUnaryBuiltinNode {
        @Specialization
        PList getCiphers(PSSLContext self) {
            SSLCipher[] ciphers = self.getCiphers();
            Object[] dicts = new Object[ciphers.length];
            for (int i = 0; i < dicts.length; i++) {
                dicts[i] = factory().createDict(ciphers[i].asKeywords());
            }
            return factory().createList(dicts);
        }
    }

    @Builtin(name = "set_ciphers", minNumOfPositionalArgs = 2, parameterNames = {"$self", "cipherlist"})
    @ArgumentClinic(name = "cipherlist", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class SetCiphersNode extends PythonClinicBuiltinNode {

        @Specialization
        Object setCiphers(PSSLContext self, String cipherlist) {
            self.setCiphers(SSLCipherSelector.selectCiphers(this, cipherlist));
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.SetCiphersNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "num_tickets", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NumTicketsNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PSSLContext self, @SuppressWarnings("unused") PNone value) {
            return self.getNumTickets();
        }

        @Specialization(guards = "!isNoValue(value)", limit = "1")
        Object set(VirtualFrame frame, PSSLContext self, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongExactNode castToLong) {
            int num;
            try {
                num = (int) castToLong.execute(lib.asIndexWithFrame(value, frame));
            } catch (CannotCastException cannotCastException) {
                throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, value);
            }
            if (num < 0) {
                throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE, "value");
            }
            if (self.getMethod() != SSLMethod.TLS_SERVER) {
                throw raise(ValueError, ErrorMessages.SSL_CTX_NOT_SERVER_CONTEXT);
            }
            self.setNumTickets(num);
            return PNone.NONE;
        }
    }

    @Builtin(name = "set_default_verify_paths", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetDefaultVerifyPathsNode extends PythonBuiltinNode {
        @Specialization
        static Object set(PSSLContext self) {
            self.setDefaultVerifyPaths();
            return PNone.NONE;
        }
    }

    @Builtin(name = "cert_store_stats", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CertStoreStatsNode extends PythonBuiltinNode {
        @Specialization
        Object set(PSSLContext self) {
            try {
                KeyStore keystore = self.getKeyStore();
                Enumeration<String> aliases = keystore.aliases();
                int x509 = 0, crl = 0, ca = 0;
                while (aliases.hasMoreElements()) {
                    Certificate cert = keystore.getCertificate(aliases.nextElement());
                    if (cert instanceof X509Certificate) {
                        X509Certificate x509Cert = (X509Certificate) cert;
                        boolean[] keyUsage = ((X509Certificate) cert).getKeyUsage();
                        if (keyUsage != null) {
                            if (keyUsage.length > 6 && keyUsage[6]) {
                                crl++;
                            } else {
                                x509++;
                                if (keyUsage.length > 5 && keyUsage[5]) {
                                    ca++;
                                }
                            }
                            continue;
                        }
                        // key usage might not be filled
                        // TODO crl++ is there some way to determine if on Certificate
                        // Revocation List
                        x509++;
                        if (x509Cert.getBasicConstraints() != -1 || isSelfSigned(x509Cert)) {
                            ca++;
                        }
                    }
                }
                return factory().createDict(new PKeyword[]{new PKeyword("x509", x509), new PKeyword("crl", crl), new PKeyword("x509_ca", ca)});
            } catch (Exception ex) {
                // TODO
                throw raise(ValueError, "cert_store_stats " + ex.getMessage());
            }
        }

        private static boolean isSelfSigned(X509Certificate x509Cert) {
            try {
                x509Cert.verify(x509Cert.getPublicKey());
            } catch (Exception e) {
                return false;
            }
            return true;
        }
    }

    @Builtin(name = "load_verify_locations", minNumOfPositionalArgs = 1, parameterNames = {"$self", "cafile", "capath", "cadata"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadVerifyLocationsNode extends PythonQuaternaryBuiltinNode {
        @Specialization(limit = "2")
        Object load(VirtualFrame frame, PSSLContext self, Object cafile, Object capath, Object cadata,
                        @CachedLibrary("cafile") PythonObjectLibrary fileLib,
                        @CachedLibrary("capath") PythonObjectLibrary pathLib,
                        @Cached CastToJavaStringNode castToString,
                        @Cached ToByteArrayNode toBytes) {
            if (cafile instanceof PNone && capath instanceof PNone && cadata instanceof PNone) {
                throw raise(TypeError, ErrorMessages.CA_FILE_PATH_DATA_CANNOT_BE_ALL_OMMITED);
            }
            if (!(cafile instanceof PNone) && !PGuards.isString(cafile)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "cafile");
            }
            if (!(capath instanceof PNone) && !PGuards.isString(capath)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "capath");
            }
            TruffleFile file = null;
            if (!(cafile instanceof PNone)) {
                file = toTruffleFile(frame, fileLib, cafile);
            } else if (!(capath instanceof PNone)) {
                file = toTruffleFile(frame, pathLib, capath);
            }

            try {
                if (!(cadata instanceof PNone)) {
                    try {
                        fromString(castToString.execute(cadata), self);
                    } catch (CannotCastException cannotCastException) {
                        if (cadata instanceof PBytesLike) {
                            fromBytesLike(toBytes, cadata, self);
                        } else {
                            throw raise(TypeError, ErrorMessages.S_SHOULD_BE_ASCII_OR_BYTELIKE, "cadata");
                        }
                    }
                }

                if (file != null) {
                    // https://www.openssl.org/docs/man1.1.1/man3/SSL_CTX_load_verify_locations.html
                    List<X509Certificate> certList = new ArrayList<>();
                    try (BufferedReader r = file.newBufferedReader()) {
                        LoadCertError result = getCertificates(this, r, certList);
                        switch (result) {
                            case EMPTY_CERT:
                            case BEGIN_CERTIFICATE_WITHOUT_END:
                            case BAD_BASE64_DECODE:
                                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.X509_PEM_LIB);
                            case SOME_BAD_BASE64_DECODE:
                                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.X509_PEM_LIB);
                            case NO_CERT_DATA:
                                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_NO_CERTIFICATE_OR_CRL_FOUND, ErrorMessages.NO_CERTIFICATE_OR_CRL_FOUND);
                            case NO_ERROR:
                                break;
                            default:
                                assert false : "not handled: " + result;
                        }
                    }
                    X509Certificate[] certs = certList.toArray(new X509Certificate[certList.size()]);
                    for (X509Certificate cert : certs) {
                        // TODO what to use for alias
                        String alias = file.getAbsoluteFile().getPath() + ":" + cert.getIssuerX500Principal().getName() + ":" + cert.getSerialNumber();
                        self.setCertificateEntry(alias, cert);
                    }
                }
            } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException ex) {
                // TODO
                throw raise(ValueError, ex.getMessage());
            }
            return PNone.NONE;
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, PythonObjectLibrary lib, Object fileObject) throws PException {
            TruffleFile file;
            try {
                file = getContext().getEnv().getPublicTruffleFile(lib.asPath(fileObject));
                if (!file.exists()) {
                    throw raiseOSError(frame, OSErrorEnum.ENOENT);
                }
                return file;
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
        }

        private void fromString(String dataString, PSSLContext context) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
            if (dataString.isEmpty()) {
                throw raise(ValueError, ErrorMessages.EMPTY_CERTIFICATE_DATA);
            }
            List<X509Certificate> certList = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new StringReader(dataString))) {
                LoadCertError result = getCertificates(this, r, certList);
                switch (result) {
                    case BAD_BASE64_DECODE:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_BAD_BASE64_DECODE, ErrorMessages.BAD_BASE64_DECODE);
                    case EMPTY_CERT:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_UNKNOWN, ErrorMessages.UNKNOWN_ERROR);
                    case BEGIN_CERTIFICATE_WITHOUT_END:
                    case NO_CERT_DATA:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_NO_START_LINE, ErrorMessages.SSL_PEM_NO_START_LINE);
                    case NO_ERROR:
                        break;
                    default:
                        assert false : "not handled: " + result;
                }
            }
            X509Certificate[] certs = certList.toArray(new X509Certificate[certList.size()]);
            for (X509Certificate cert : certs) {
                // TODO what to use for alias
                String alias = cert.getIssuerX500Principal().getName() + ":" + cert.getSerialNumber();
                context.setCertificateEntry(alias, cert);
            }
        }

        private void fromBytesLike(ToByteArrayNode toBytes, Object cadata, PSSLContext context) throws KeyStoreException, IOException, NoSuchAlgorithmException {
            byte[] bytes = toBytes.execute(((PBytesLike) cadata).getSequenceStorage());
            try {
                Collection<? extends Certificate> col = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));
                for (Certificate cert : col) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    String alias = x509Cert.getIssuerX500Principal().getName() + ":" + x509Cert.getSerialNumber();
                    context.setCertificateEntry(alias, x509Cert);
                }
            } catch (CertificateException ex) {
                String msg = ex.getMessage();
                if (msg != null) {
                    if (msg.contains("No certificate data found")) {
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_NOT_ENOUGH_DATA, ErrorMessages.NOT_ENOUGH_DATA);
                    }
                } else {
                    msg = "error while reading cadata";
                }
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, msg);
            }
        }
    }

    @Builtin(name = "load_cert_chain", minNumOfPositionalArgs = 2, parameterNames = {"$self", "certfile", "keyfile", "password"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadCertChainNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, String certfile, PNone keyfile, PNone password) {
            TruffleFile cert = toTruffleFile(frame, certfile);
            return load(self, cert, cert, "");
        }

        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, String certfile, PNone keyfile, String password) {
            TruffleFile cert = toTruffleFile(frame, certfile);
            return load(self, cert, cert, password);
        }

        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, String certfile, String keyfile, PNone password) {
            TruffleFile cert = toTruffleFile(frame, certfile);
            TruffleFile key = toTruffleFile(frame, keyfile);
            return load(self, cert, key, "");
        }

        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, String certfile, String keyfile, String password) {
            TruffleFile cert = toTruffleFile(frame, certfile);
            TruffleFile key = toTruffleFile(frame, keyfile);
            return load(self, cert, key, password);
        }

        @Specialization(guards = "!isString(password)")
        Object loadPasswordCallback(VirtualFrame frame, PSSLContext self, String certfile, String keyfile, Object password) {
            // TODO: password callable/callback
            throw raise(NotImplementedError);
        }

        @Specialization(guards = "!isString(password)")
        Object loadPasswordCallback(VirtualFrame frame, PSSLContext self, String certfile, PNone keyfile, Object password) {
            // TODO: password callable/callback
            throw raise(NotImplementedError);
        }

        @Specialization(guards = {"!isString(certfile)"})
        Object load(VirtualFrame frame, PSSLContext self, Object certfile, Object keyfile, Object password) {
            throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "certfile");
        }

        @Specialization(guards = {"!isString(keyfile)", "!isNoValue(keyfile)"})
        Object load(VirtualFrame frame, PSSLContext self, String certfile, Object keyfile, Object password) {
            throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "keyfile");
        }

        private Object load(PSSLContext self, TruffleFile certfile, TruffleFile keyfile, String password) {
            // TODO add logging
            try {
                X509Certificate[] certs;
                try (BufferedReader r = certfile.newBufferedReader()) {
                    List<X509Certificate> certList = new ArrayList<>();
                    LoadCertError result = getCertificates(this, r, certList);
                    switch (result) {
                        case BAD_BASE64_DECODE:
                        case BEGIN_CERTIFICATE_WITHOUT_END:
                        case NO_CERT_DATA:
                            throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                        case SOME_BAD_BASE64_DECODE:
                            throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_BAD_BASE64_DECODE, ErrorMessages.BAD_BASE64_DECODE);
                        case EMPTY_CERT:
                            if (certList.isEmpty()) {
                                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                            }
                            throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_UNKNOWN, ErrorMessages.UNKNOWN_ERROR);
                        case NO_ERROR:
                            break;
                        default:
                            assert false : "not handled: " + result;
                    }
                    certs = certList.toArray(new X509Certificate[certList.size()]);
                }
                // TODO only 1. cert?
                PrivateKey pk = getPrivateKey(this, keyfile, certs[0].getPublicKey().getAlgorithm());
                checkPrivateKey(this, pk, certs[0]);

                String alias = keyfile.getName();
                char[] psswdChars = "".equals(password) ? password.toCharArray() : PythonUtils.EMPTY_CHAR_ARRAY;
                self.setKeyEntry(alias, pk, psswdChars, certs);
            } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
                throw raise(SSLError, ErrorMessages.SSL_ERROR, ex.getMessage());
            }
            return PNone.NONE;
        }

        private void checkPrivateKey(Node node, PrivateKey pk, X509Certificate cert) throws PException {
            if (!pk.getAlgorithm().equals(cert.getPublicKey().getAlgorithm())) {
                // TODO correct err code
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_KEY_TYPE_MISMATCH, ErrorMessages.KEY_TYPE_MISMATCH);
            }
            if (pk instanceof RSAPrivateKey) {
                RSAPrivateKey privKey = (RSAPrivateKey) pk;
                RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
                if (!privKey.getModulus().equals(pubKey.getModulus())) {
                    // TODO: only modulus?
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_KEY_VALUES_MISMATCH, ErrorMessages.KEY_VALUES_MISMATCH);
                }
            } else if (pk instanceof ECPrivateKey) {
                ECPrivateKey privKey = (ECPrivateKey) pk;
                ECPublicKey pubKey = (ECPublicKey) cert.getPublicKey();
                if (!privKey.getParams().equals(pubKey.getParams())) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_KEY_VALUES_MISMATCH, ErrorMessages.KEY_VALUES_MISMATCH);
                }
            } else if (pk instanceof DHPrivateKey) {
                DHPrivateKey privKey = (DHPrivateKey) pk;
                DHPublicKey pubKey = (DHPublicKey) cert.getPublicKey();
                if (!privKey.getParams().equals(pubKey.getParams())) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_KEY_VALUES_MISMATCH, ErrorMessages.KEY_VALUES_MISMATCH);
                }
            } else if (pk instanceof DSAPrivateKey) {
                DSAPrivateKey privKey = (DSAPrivateKey) pk;
                DSAPublicKey pubKey = (DSAPublicKey) cert.getPublicKey();
                if (!privKey.getParams().equals(pubKey.getParams())) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_KEY_VALUES_MISMATCH, ErrorMessages.KEY_VALUES_MISMATCH);
                }
            }
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, String path) throws PException {
            try {
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(path);
                if (!file.exists()) {
                    throw raiseOSError(frame, OSErrorEnum.ENOENT);
                }
                return file;
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
        }
    }

    private enum LoadCertError {
        NO_ERROR,
        NO_CERT_DATA,
        EMPTY_CERT,
        BEGIN_CERTIFICATE_WITHOUT_END,
        SOME_BAD_BASE64_DECODE,
        BAD_BASE64_DECODE;
    }

    @TruffleBoundary
    private static LoadCertError getCertificates(Node node, BufferedReader r, List<X509Certificate> result) throws IOException, CertificateException {
        Base64.Decoder decoder = Base64.getDecoder();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        boolean sawBegin = false;
        boolean someData = false;
        StringBuilder sb = new StringBuilder(2000);
        List<String> data = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (sawBegin) {
                if (line.contains(BEGIN_CERTIFICATE)) {
                    break;
                }
                if (line.contains(END_CERTIFICATE)) {
                    sawBegin = false;
                    if (!someData && sb.length() > 0) {
                        someData = true;
                    }
                    data.add(sb.toString());
                } else {
                    sb.append(line);
                }
            } else if (line.contains(BEGIN_CERTIFICATE)) {
                sawBegin = true;
                sb.setLength(0);
            }
        }
        if (sawBegin) {
            return LoadCertError.BEGIN_CERTIFICATE_WITHOUT_END;
        }
        for (String s : data) {
            if (!s.isEmpty()) {
                byte[] der;
                try {
                    der = decoder.decode(s);
                } catch (IllegalArgumentException e) {
                    if (result.isEmpty()) {
                        return LoadCertError.BAD_BASE64_DECODE;
                    } else {
                        return LoadCertError.SOME_BAD_BASE64_DECODE;
                    }
                }
                result.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
            } else if (someData) {
                return LoadCertError.EMPTY_CERT;
            }
        }
        if (result.isEmpty()) {
            return LoadCertError.NO_CERT_DATA;
        }
        return LoadCertError.NO_ERROR;
    }

    /**
     * Returns the first private key found in file SSL_CTX_use_PrivateKey_file
     */
    @TruffleBoundary
    private static PrivateKey getPrivateKey(Node node, TruffleFile pkPem, String alg) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        boolean begin = false;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = pkPem.newBufferedReader()) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!begin && line.contains(BEGIN_PRIVATE_KEY)) {
                    begin = true;
                } else if (begin) {
                    if (line.contains(END_PRIVATE_KEY)) {
                        begin = false;
                        // get first private key found
                        break;
                    }
                    sb.append(line);
                }
            }
        }
        if (begin || sb.length() == 0) {
            // TODO: append any additional info? original msg is e.g. "[SSL] PEM lib (_ssl.c:3991)"
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(sb.toString());
        } catch (IllegalArgumentException e) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        KeyFactory factory = KeyFactory.getInstance(alg);
        return factory.generatePrivate(spec);
    }

    @Builtin(name = "load_dh_params", minNumOfPositionalArgs = 2, parameterNames = {"$self", "filepath"})
    @GenerateNodeFactory
    abstract static class LoadDhParamsNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone load(VirtualFrame frame, PSSLContext self, String filepath,
                        @Cached PRaiseSSLErrorNode raiseSSL) {
            File file = new File(filepath);
            if (!file.exists()) {
                throw raiseOSError(frame, OSErrorEnum.ENOENT);
            }
            DHParameterSpec dh = null;
            try {
                dh = getDHParameters(this, file);
                if (dh != null) {
                    self.setDHParameters(dh);
                }
            } catch (IOException | NoSuchAlgorithmException | InvalidParameterSpecException ex) {
                // TODO
                throw raise(ValueError, "load_dh_params: " + ex.getMessage());
            }
            return PNone.NONE;
        }

        @Specialization(limit = "3")
        PNone load(VirtualFrame frame, PSSLContext self, PBytes filepath,
                        @CachedLibrary("filepath") PythonObjectLibrary lib,
                        @Cached PRaiseSSLErrorNode raiseSSL) {
            load(frame, self, lib.asPath(filepath), raiseSSL);

            return PNone.NONE;
        }

        @Specialization(limit = "1")
        PNone load(VirtualFrame frame, PSSLContext self, PythonObject filepath,
                        @CachedLibrary("filepath") PythonObjectLibrary lib,
                        @Cached PRaiseSSLErrorNode raiseSSL) {
            load(frame, self, lib.asPath(filepath), raiseSSL);

            return PNone.NONE;
        }

        @Specialization(guards = {"!isString(filepath)", "!isBytes(filepath)", "!isPythonObject(filepath)"})
        Object wrap(PSSLContext self, Object filepath) {
            throw raise(TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, filepath);
        }
    }

    @TruffleBoundary
    private static DHParameterSpec getDHParameters(Node node, File file) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException {
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            // TODO: test me!
            String line;
            boolean begin = false;
            StringBuilder sb = new StringBuilder();
            while ((line = r.readLine()) != null) {
                if (line.contains(BEGIN_DH_PARAMETERS)) {
                    begin = true;
                } else if (begin) {
                    if (line.contains(END_DH_PARAMETERS)) {
                        break;
                    }
                    sb.append(line.trim());
                }
            }
            if (!begin) {
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_NO_START_LINE, ErrorMessages.SSL_PEM_NO_START_LINE);
            }
            AlgorithmParameters ap = AlgorithmParameters.getInstance("DH");
            ap.init(Base64.getDecoder().decode(sb.toString()));
            return ap.getParameterSpec(DHParameterSpec.class);
        }
    }

    @Builtin(name = "_set_alpn_protocols", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetAlpnProtocols extends PythonBinaryBuiltinNode {

        @Specialization(guards = "lib.isBuffer(buffer)", limit = "2")
        Object setFromBuffer(PSSLContext self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary lib) {
            if (!ALPNHelper.hasAlpn()) {
                throw raise(NotImplementedError, "The ALPN extension requires JDK 8u252 or later");
            }
            try {
                byte[] bytes = lib.getBufferBytes(buffer);
                self.setAlpnProtocols(parseProtocols(bytes));
                return PNone.NONE;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, arg);
        }

        @TruffleBoundary
        private static String[] parseProtocols(byte[] bytes) {
            List<String> protocols = new ArrayList<>();
            int i = 0;
            while (i < bytes.length) {
                int len = bytes[i];
                if (i + len + 1 < bytes.length) {
                    protocols.add(new String(bytes, i + 1, len, StandardCharsets.US_ASCII));
                }
                i += len + 1;
            }
            return protocols.toArray(new String[0]);
        }
    }
}
