package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.ssl.CertUtils.getCertificates;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CRLException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.LOGGER;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils.LoadCertError;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
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
import java.util.logging.Level;

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
                PSSLContext context = factory().createSSLContext(type, method, SSLModuleBuiltins.X509_V_FLAG_TRUSTED_FIRST, checkHostname, verifyMode, createSSLContext());
                long options = SSLOptions.DEFAULT_OPTIONS;
                if (method != SSLMethod.SSL3) {
                    options |= SSLOptions.SSL_OP_NO_SSLv3;
                }
                context.setOptions(options);
                return context;
            } catch (NoSuchAlgorithmException e) {
                throw raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, e);
            } catch (KeyManagementException e) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, e);
            }
        }

        @TruffleBoundary
        private static SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
            SSLContext context = SSLContext.getInstance("TLS");
            // Disable automatic client session caching, because CPython doesn't do that
            context.getClientSessionContext().setSessionCacheSize(0);
            // Pre-init to be able to get default parameters
            context.init(null, null, null);
            return context;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.SSLContextNodeClinicProviderGen.INSTANCE;
        }
    }

    @TruffleBoundary
    static SSLEngine createSSLEngine(PNodeWithRaise node, PSSLContext context, boolean serverMode, String serverHostname) {
        try {
            context.init();
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException | InvalidAlgorithmParameterException ex) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, ex);
        }
        SSLParameters parameters = new SSLParameters();
        SSLEngine engine;
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
            engine = context.getContext().createSSLEngine(serverHostname, -1);
        } else {
            engine = context.getContext().createSSLEngine();
        }
        engine.setUseClientMode(!serverMode);
        engine.setEnabledProtocols(context.computeEnabledProtocols());

        List<SSLCipher> enabledCiphers = context.computeEnabledCiphers(engine);
        String[] enabledCipherNames = new String[enabledCiphers.size()];
        for (int i = 0; i < enabledCiphers.size(); i++) {
            enabledCipherNames[i] = enabledCiphers.get(i).name();
        }
        parameters.setCipherSuites(enabledCipherNames);

        if (ALPNHelper.hasAlpn() && context.getAlpnProtocols() != null) {
            ALPNHelper.setApplicationProtocols(parameters, context.getAlpnProtocols());
        }
        if (serverMode) {
            switch (context.getVerifyMode()) {
                case SSLModuleBuiltins.SSL_CERT_NONE:
                    parameters.setNeedClientAuth(false);
                    parameters.setWantClientAuth(false);
                    break;
                case SSLModuleBuiltins.SSL_CERT_OPTIONAL:
                    parameters.setWantClientAuth(true);
                    break;
                case SSLModuleBuiltins.SSL_CERT_REQUIRED:
                    parameters.setNeedClientAuth(true);
                    break;
                default:
                    assert false;
            }
        }
        engine.setSSLParameters(parameters);
        return engine;
    }

    @Builtin(name = "_wrap_socket", minNumOfPositionalArgs = 3, parameterNames = {"$self", "sock", "server_side", "server_hostname"}, keywordOnlyNames = {"owner", "session"})
    @ArgumentClinic(name = "server_side", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class WrapSocketNode extends PythonClinicBuiltinNode {
        @Specialization
        Object wrap(PSSLContext context, PSocket sock, boolean serverSide, Object serverHostnameObj, Object owner, @SuppressWarnings("unused") Object session,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            String serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(this, context, serverSide, serverHostname);
            PSSLSocket sslSocket = factory().createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, sock);
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            sslSocket.setServerHostname(serverHostname);
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
        Object wrap(PSSLContext context, PMemoryBIO incoming, PMemoryBIO outgoing, boolean serverSide, Object serverHostnameObj, Object owner, @SuppressWarnings("unused") Object session,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            String serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(this, context, serverSide, serverHostname);
            PSSLSocket sslSocket = factory().createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, incoming.getBio(), outgoing.getBio());
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            sslSocket.setServerHostname(serverHostname);
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

    private static void setMinMaxVersion(PNodeWithRaise node, PSSLContext context, boolean maximum, int value) {
        if (context.getMethod().isSingleVersion()) {
            throw node.raise(ValueError, ErrorMessages.CONTEXT_DOESNT_SUPPORT_MIN_MAX);
        }
        SSLProtocol selected = null;
        switch (value) {
            case SSLProtocol.PROTO_MINIMUM_SUPPORTED:
                selected = maximum ? SSLModuleBuiltins.getMinimumVersion() : null;
                break;
            case SSLProtocol.PROTO_MAXIMUM_SUPPORTED:
                selected = maximum ? null : SSLModuleBuiltins.getMaximumVersion();
                break;
            default:
                for (SSLProtocol protocol : SSLProtocol.values()) {
                    if (protocol.getId() == value) {
                        selected = protocol;
                        break;
                    }
                }
                if (selected == null) {
                    throw node.raise(ValueError, ErrorMessages.UNSUPPORTED_PROTOCOL_VERSION, value);
                }
        }
        if (maximum) {
            context.setMaximumVersion(selected);
        } else {
            context.setMinimumVersion(selected);
        }
    }

    @Builtin(name = "minimum_version", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class MinimumVersionNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static int get(PSSLContext self, @SuppressWarnings("unused") Object none) {
            return self.getMinimumVersion() != null ? self.getMinimumVersion().getId() : SSLProtocol.PROTO_MINIMUM_SUPPORTED;
        }

        @Specialization(guards = "!isNoValue(obj)", limit = "3")
        Object set(PSSLContext self, Object obj,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            setMinMaxVersion(this, self, false, lib.asSize(obj));
            return PNone.NONE;
        }
    }

    @Builtin(name = "maximum_version", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class MaximumVersionNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static int get(PSSLContext self, @SuppressWarnings("unused") Object none) {
            return self.getMaximumVersion() != null ? self.getMaximumVersion().getId() : SSLProtocol.PROTO_MAXIMUM_SUPPORTED;
        }

        @Specialization(guards = "!isNoValue(obj)", limit = "3")
        Object set(PSSLContext self, Object obj,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            setMinMaxVersion(this, self, true, lib.asSize(obj));
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_ciphers", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetCiphersNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        PList getCiphers(PSSLContext self) {
            List<SSLCipher> ciphers = self.computeEnabledCiphers(self.getContext().createSSLEngine());
            Object[] dicts = new Object[ciphers.size()];
            for (int i = 0; i < dicts.length; i++) {
                dicts[i] = factory().createDict(ciphers.get(i).asKeywords());
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
        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(value)")
        int get(PSSLContext self, PNone value) {
            // not used yet so rather raise error
            throw raise(NotImplementedError);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isNoValue(value)")
        Object set(VirtualFrame frame, PSSLContext self, Object value) {
            // not used yet so rather raise error
            throw raise(NotImplementedError);
            // int num;
            // try {
            // num = (int) castToLong.execute(lib.asIndexWithFrame(value, frame));
            // } catch (CannotCastException cannotCastException) {
            // throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, value);
            // }
            // if (num < 0) {
            // throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE, "value");
            // }
            // if (self.getMethod() != SSLMethod.TLS_SERVER) {
            // throw raise(ValueError, ErrorMessages.SSL_CTX_NOT_SERVER_CONTEXT);
            // }
            // self.setNumTickets(num);
            // return PNone.NONE;
        }
    }

    @Builtin(name = "sni_callback", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class SNICallbackNode extends PythonBuiltinNode {
        @Specialization
        Object notImplemented(@SuppressWarnings("unused") PSSLContext self, @SuppressWarnings("unused") Object value) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "set_default_verify_paths", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetDefaultVerifyPathsNode extends PythonBuiltinNode {
        @Specialization
        Object set(VirtualFrame frame, PSSLContext self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("createEnvironLookup()") GetAttributeNode getAttribute,
                        @CachedLibrary(limit = "1") HashingStorageLibrary environLib,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorage,
                        @Cached("createCertFileKey()") PBytes certFileKey,
                        @Cached("createCertDirKey()") PBytes certDirKey) {

            PythonModule posix = getCore().lookupBuiltinModule("posix");
            PDict environ = (PDict) getAttribute.executeObject(frame, posix);
            HashingStorage storage = getStorage.execute(environ);

            TruffleFile file = toTruffleFile(lib, environLib.getItem(storage, certFileKey));
            TruffleFile path = toTruffleFile(lib, environLib.getItem(storage, certDirKey));
            if (file != null || path != null) {
                LOGGER.fine(() -> String.format("set_default_verify_paths file: %s. path: %s", file != null ? file.getPath() : "None", path != null ? path.getPath() : "None"));
                List<Object> certificates = new ArrayList<>();
                try {
                    LoadCertError result = CertUtils.loadVerifyLocations(file, path, certificates);
                    if (result == LoadCertError.NO_ERROR) {
                        self.setCertificateEntries(certificates);
                    } else {
                        // do not raise any errors
                        LOGGER.finer(() -> String.format("set_default_verify_paths loadVerifyLocations returned %s", result));
                    }
                } catch (NoSuchAlgorithmException | CertificateException | CRLException | IOException | KeyStoreException ex) {
                    // do not raise any errors
                    LOGGER.log(Level.FINER, "", ex);
                }
            }
            return PNone.NONE;
        }

        protected PBytes createCertFileKey() {
            return factory().createBytes("SSL_CERT_FILE".getBytes());
        }

        protected PBytes createCertDirKey() {
            return factory().createBytes("SSL_CERT_DIR".getBytes());
        }

        protected static GetAttributeNode createEnvironLookup() {
            return GetAttributeNode.create("environ");
        }

        private TruffleFile toTruffleFile(PythonObjectLibrary lib, Object path) throws PException {
            TruffleFile file;
            try {
                file = getContext().getEnv().getPublicTruffleFile(lib.asPath(path));
                if (!file.exists()) {
                    return null;
                }
                return file;
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Builtin(name = "cert_store_stats", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CertStoreStatsNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object storeStats(PSSLContext self) {
            try {
                KeyStore keystore = self.getKeyStore();
                Enumeration<String> aliases = keystore.aliases();
                int x509 = 0, crl = 0, ca = 0;
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (keystore.isCertificateEntry(alias)) {
                        Certificate cert = keystore.getCertificate(alias);
                        if (cert instanceof X509Certificate) {
                            X509Certificate x509Cert = (X509Certificate) cert;
                            boolean[] keyUsage = ((X509Certificate) cert).getKeyUsage();
                            if (CertUtils.isCrl(keyUsage)) {
                                crl++;
                            } else {
                                x509++;
                                if (CertUtils.isCA(x509Cert, keyUsage)) {
                                    ca++;
                                }
                            }
                        }
                    }
                }
                return factory().createDict(new PKeyword[]{new PKeyword("x509", x509), new PKeyword("crl", crl), new PKeyword("x509_ca", ca)});
            } catch (Exception ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
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
            if (!(cafile instanceof PNone) && !PGuards.isString(cafile) && !PGuards.isBytes(cafile)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "cafile");
            }
            if (!(capath instanceof PNone) && !PGuards.isString(capath) && !PGuards.isBytes(capath)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "capath");
            }
            final TruffleFile file;
            if (!(cafile instanceof PNone)) {
                file = toTruffleFile(frame, fileLib, cafile);
                if (!file.exists()) {
                    throw raiseOSError(frame, OSErrorEnum.ENOENT);
                }
            } else {
                file = null;
            }
            final TruffleFile path;
            if (!(capath instanceof PNone)) {
                path = toTruffleFile(frame, pathLib, capath);
            } else {
                path = null;
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

                if (file != null || path != null) {
                    LOGGER.fine(() -> String.format("LoadVerifyLocationsNode cafile: %s, capath: %s", file != null ? file.getPath() : "None", path != null ? path.getPath() : "None"));
                    // https://www.openssl.org/docs/man1.1.1/man3/SSL_CTX_load_verify_locations.html
                    List<Object> certificates = new ArrayList<>();
                    LoadCertError result = CertUtils.loadVerifyLocations(file, path, certificates);
                    switch (result) {
                        case EMPTY_CERT:
                        case BEGIN_CERTIFICATE_WITHOUT_END:
                        case BAD_BASE64_DECODE:
                        case SOME_BAD_BASE64_DECODE:
                            throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.X509_PEM_LIB);
                        case NO_CERT_DATA:
                            throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_NO_CERTIFICATE_OR_CRL_FOUND, ErrorMessages.NO_CERTIFICATE_OR_CRL_FOUND);
                        case NO_ERROR:
                            break;
                        default:
                            assert false : "not handled: " + result;
                    }
                    self.setCertificateEntries(certificates);
                }
            } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | CRLException ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
            return PNone.NONE;
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, PythonObjectLibrary lib, Object fileObject) throws PException {
            try {
                return getContext().getEnv().getPublicTruffleFile(lib.asPath(fileObject));
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
        }

        private void fromString(String dataString, PSSLContext context) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, CRLException {
            if (dataString.isEmpty()) {
                throw raise(ValueError, ErrorMessages.EMPTY_CERTIFICATE_DATA);
            }
            List<Object> certificates = new ArrayList<>();
            getCertificates(dataString, certificates);
            context.setCertificateEntries(certificates);
        }

        @TruffleBoundary
        private void getCertificates(String dataString, List<Object> certificates) throws PException, CRLException, IOException, CertificateException {
            try (BufferedReader r = new BufferedReader(new StringReader(dataString))) {
                LoadCertError result = CertUtils.getCertificates(r, certificates);
                switch (result) {
                    case BAD_BASE64_DECODE:
                    case EMPTY_CERT:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_BAD_BASE64_DECODE, ErrorMessages.BAD_BASE64_DECODE);
                    case BEGIN_CERTIFICATE_WITHOUT_END:
                    case NO_CERT_DATA:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_NO_START_LINE, ErrorMessages.SSL_PEM_NO_START_LINE);
                    case NO_ERROR:
                        break;
                    default:
                        assert false : "not handled: " + result;
                }
            }
        }

        @TruffleBoundary
        private void fromBytesLike(ToByteArrayNode toBytes, Object cadata, PSSLContext context) throws KeyStoreException, IOException, NoSuchAlgorithmException {
            byte[] bytes = toBytes.execute(((PBytesLike) cadata).getSequenceStorage());
            try {
                context.setCertificateEntries(CertUtils.generateCertificates(bytes));
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
        Object load(VirtualFrame frame, PSSLContext self, Object certfile, Object keyfile, Object password,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib) {
            if (!PGuards.isString(certfile) && !PGuards.isBytes(certfile)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "certfile");
            }
            if (!(keyfile instanceof PNone) && !PGuards.isString(keyfile) && !PGuards.isBytes(keyfile)) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "keyfile");
            }
            Object kf = keyfile instanceof PNone ? certfile : keyfile;
            TruffleFile certTruffleFile = toTruffleFile(frame, lib.asPath(certfile));
            TruffleFile keyTruffleFile = toTruffleFile(frame, lib.asPath(kf));
            try {
                checkPassword(password);
                return load(certTruffleFile, keyTruffleFile, self);
            } catch (IOException ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @TruffleBoundary
        private Object load(TruffleFile certTruffleFile, TruffleFile keyTruffleFile, PSSLContext self) throws IOException {
            try (BufferedReader certReader = getReader(certTruffleFile, "certfile");
                            BufferedReader keyReader = getReader(keyTruffleFile, "keyfile")) {
                return load(self, certReader, keyReader);
            }
        }

        private void checkPassword(Object password) {
            if (password instanceof PNone) {
                return;
            }
            throw raise(NotImplementedError);
            // TODO: once at least some psswd support exists
            // String psswd = null;
            // try {
            // psswd = castToString.execute(password);
            // } catch (CannotCastException e) {
            // if(password instanceof PNone) {
            // psswd = "";
            // } else if(password instanceof PBytesLike) {
            // String psswd = lib.asPath(password);
            // if(psswd.size() > 1024) {
            // // TODO 1024 is openssl default, might vary, might not need this restriction at all
            // throw raise(ValueError, "password cannot be longer than 1024 bytes");
            // }
            // } else if(PGuards.isCallable(password)) {
            // throw raise(NotImplementedError);
            // } else {
            // throw raise(TypeError, "password should be a string or callable");
            // }
            // }
            // return psswd;
        }

        private BufferedReader getReader(TruffleFile file, String arg) throws IOException {
            try {
                LOGGER.fine(() -> String.format("load_cert_chain %s:%s", arg, file.getPath()));
                return file.newBufferedReader();
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, arg);
            }
        }

        private Object load(PSSLContext self, BufferedReader certReader, BufferedReader keyReader) {
            // TODO add logging
            try {
                // if keyReader and certReader are from the same file, key is expected to come first
                byte[] pkBytes = CertUtils.getEncodedPrivateKey(this, keyReader);
                X509Certificate[] certs;
                List<Object> certificates = new ArrayList<>();
                LoadCertError result = getCertificates(certReader, certificates);
                switch (result) {
                    case BAD_BASE64_DECODE:
                    case BEGIN_CERTIFICATE_WITHOUT_END:
                    case NO_CERT_DATA:
                    case EMPTY_CERT:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                    case SOME_BAD_BASE64_DECODE:
                        throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_BAD_BASE64_DECODE, ErrorMessages.BAD_BASE64_DECODE);
                    case NO_ERROR:
                        break;
                    default:
                        assert false : "not handled: " + result;
                }
                certs = certificates.toArray(new X509Certificate[certificates.size()]);
                PrivateKey pk = CertUtils.createPrivateKey(this, pkBytes, certs[0]);
                self.setKeyEntry(pk, PythonUtils.EMPTY_CHAR_ARRAY, certs);
            } catch (InvalidKeySpecException | IOException | NoSuchAlgorithmException | KeyStoreException | CertificateException | CRLException ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
            return PNone.NONE;
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

    @Builtin(name = "load_dh_params", minNumOfPositionalArgs = 2, parameterNames = {"$self", "filepath"})
    @GenerateNodeFactory
    abstract static class LoadDhParamsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PNone load(VirtualFrame frame, PSSLContext self, String filepath) {
            // not used yet so rather raise error
            throw raise(NotImplementedError);
            // File file = new File(filepath);
            // if (!file.exists()) {
            // throw raiseOSError(frame, OSErrorEnum.ENOENT);
            // }
            // DHParameterSpec dh = null;
            // try {
            // dh = getDHParameters(this, file);
            // if (dh != null) {
            // self.setDHParameters(dh);
            // }
            // } catch (IOException | NoSuchAlgorithmException | InvalidParameterSpecException ex) {
            // throw raise(SSLError, ex.getMessage());
            // }
            // return PNone.NONE;
        }

        @Specialization(limit = "3")
        PNone load(VirtualFrame frame, PSSLContext self, PBytes filepath,
                        @CachedLibrary("filepath") PythonObjectLibrary lib) {
            load(frame, self, lib.asPath(filepath));

            return PNone.NONE;
        }

        @Specialization(limit = "1")
        PNone load(VirtualFrame frame, PSSLContext self, PythonObject filepath,
                        @CachedLibrary("filepath") PythonObjectLibrary lib) {
            load(frame, self, lib.asPath(filepath));

            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(filepath)", "!isBytes(filepath)", "!isPythonObject(filepath)"})
        Object wrap(PSSLContext self, Object filepath) {
            throw raise(TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, filepath);
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

    @Builtin(name = "get_ca_certs", minNumOfPositionalArgs = 1, parameterNames = {"$self", "binary_form"})
    @ArgumentClinic(name = "binary_form", conversion = ArgumentClinic.ClinicConversion.Boolean, useDefaultForNone = true, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class GetCACerts extends PythonBinaryClinicBuiltinNode {

        @TruffleBoundary
        @Specialization(guards = "!binary_form")
        Object getCerts(PSSLContext self, @SuppressWarnings("unused") boolean binary_form) {
            try {
                List<PDict> result = new ArrayList<>();
                KeyStore ks = self.getKeyStore();
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    X509Certificate cert = (X509Certificate) ks.getCertificate(aliases.nextElement());
                    if (CertUtils.isCA(cert, cert.getKeyUsage())) {
                        result.add(CertUtils.decodeCertificate(cert, factory()));
                    }
                }
                return factory().createList(result.toArray(new Object[result.size()]));
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "binary_form")
        Object getCertsBinary(PSSLContext self, @SuppressWarnings("unused") boolean binary_form) {
            try {
                List<PBytes> result = new ArrayList<>();
                KeyStore ks = self.getKeyStore();
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    X509Certificate cert = (X509Certificate) ks.getCertificate(aliases.nextElement());
                    if (CertUtils.isCA(cert, cert.getKeyUsage())) {
                        result.add(factory().createBytes(cert.getEncoded()));
                    }
                }
                return factory().createList(result.toArray(new Object[result.size()]));
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException ex) {
                throw PRaiseSSLErrorNode.raiseUncached(this, SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.GetCACertsClinicProviderGen.INSTANCE;
        }
    }
}
