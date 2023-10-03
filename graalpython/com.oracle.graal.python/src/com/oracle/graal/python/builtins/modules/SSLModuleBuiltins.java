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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PermissionError;
import static com.oracle.graal.python.nodes.BuiltinNames.J__SSL;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SSL;
import static com.oracle.graal.python.nodes.ErrorMessages.SSL_CANT_OPEN_FILE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.SSL_ERR_DECODING_PEM_FILE;
import static com.oracle.graal.python.nodes.ErrorMessages.SSL_ERR_DECODING_PEM_FILE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.SSL_ERR_DECODING_PEM_FILE_UNEXPECTED_S;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.bouncycastle.util.encoders.DecoderException;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils;
import com.oracle.graal.python.builtins.objects.ssl.SSLCipher;
import com.oracle.graal.python.builtins.objects.ssl.SSLCipherSelector;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode;
import com.oracle.graal.python.builtins.objects.ssl.SSLMethod;
import com.oracle.graal.python.builtins.objects.ssl.SSLOptions;
import com.oracle.graal.python.builtins.objects.ssl.SSLProtocol;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__SSL)
public final class SSLModuleBuiltins extends PythonBuiltins {

    public static final TruffleLogger LOGGER = PythonLanguage.getLogger(SSLModuleBuiltins.class);

    // Taken from CPython
    static final String J_DEFAULT_CIPHER_STRING = "DEFAULT:!aNULL:!eNULL:!MD5:!3DES:!DES:!RC4:!IDEA:!SEED:!aDSS:!SRP:!PSK";
    static final TruffleString T_DEFAULT_CIPHER_STRING = tsLiteral(J_DEFAULT_CIPHER_STRING);

    private static List<SSLProtocol> supportedProtocols;
    private static SSLProtocol minimumVersion;
    private static SSLProtocol maximumVersion;

    public static final SSLCipher[] defaultCiphers;

    public static final int SSL_CERT_NONE = 0;
    public static final int SSL_CERT_OPTIONAL = 1;
    public static final int SSL_CERT_REQUIRED = 2;

    public static final int X509_V_FLAG_CRL_CHECK = 0x4;
    public static final int X509_V_FLAG_CRL_CHECK_ALL = 0x8;
    private static final int X509_V_FLAG_X509_STRICT = 0x20;
    public static final int X509_V_FLAG_TRUSTED_FIRST = 0x8000;

    public static List<SSLProtocol> getSupportedProtocols() {
        assert supportedProtocols != null : "Uninitialized protocols";
        return supportedProtocols;
    }

    public static SSLProtocol getMinimumVersion() {
        return minimumVersion;
    }

    public static SSLProtocol getMaximumVersion() {
        return maximumVersion;
    }

    static {
        SSLCipher[] computed;
        try {
            computed = SSLCipherSelector.selectCiphers(null, J_DEFAULT_CIPHER_STRING);
        } catch (PException e) {
            computed = new SSLCipher[0];
        }
        defaultCiphers = computed;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLModuleBuiltinsFactory.getFactories();
    }

    private static synchronized void loadDefaults() {
        if (ImageInfo.inImageBuildtimeCode()) {
            // The values are dependent on system properties, don't bake them into the image
            throw new AssertionError("SSL module initialized at build time");
        }
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            List<SSLProtocol> protocols = new ArrayList<>(SSLProtocol.values().length);
            for (SSLProtocol protocol : SSLProtocol.values()) {
                if (tryProtocolAvailability(context, protocol)) {
                    protocols.add(protocol);
                }
            }
            supportedProtocols = Collections.unmodifiableList(protocols);
            if (!supportedProtocols.isEmpty()) {
                minimumVersion = supportedProtocols.get(0);
                maximumVersion = supportedProtocols.get(supportedProtocols.size() - 1);
            }
        } catch (NoSuchAlgorithmException | KeyManagementException | PException e) {
            // This module is not essential for the interpreter to function, so don't fail
            // at startup, let it fail, when it gets used
            supportedProtocols = new ArrayList<>();
        }
    }

    /**
     * JDK reports protocols as supported even if they are disabled and cannot be used. We have to
     * attempt a handshake to truly know if the protocol is available.
     */
    private static boolean tryProtocolAvailability(SSLContext context, SSLProtocol protocol) {
        String[] protocols = {protocol.getName()};
        SSLEngine engine;
        try {
            engine = context.createSSLEngine();
            engine.setUseClientMode(true);
            engine.setEnabledProtocols(protocols);
            engine.beginHandshake();
            return true;
        } catch (Exception e1) {
            try {
                engine = context.createSSLEngine();
                engine.setUseClientMode(false);
                engine.setEnabledProtocols(protocols);
                engine.beginHandshake();
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        loadDefaults();
        PythonModule module = core.lookupBuiltinModule(T__SSL);
        PythonObjectFactory factory = core.factory();
        module.setAttribute(tsLiteral("OPENSSL_VERSION_NUMBER"), 0);
        PTuple versionInfo = factory.createTuple(new int[]{0, 0, 0, 0, 0});
        module.setAttribute(tsLiteral("OPENSSL_VERSION_INFO"), versionInfo);
        module.setAttribute(tsLiteral("OPENSSL_VERSION"), toTruffleStringUncached("GraalVM JSSE"));
        module.setAttribute(tsLiteral("_DEFAULT_CIPHERS"), T_DEFAULT_CIPHER_STRING);
        module.setAttribute(tsLiteral("_OPENSSL_API_VERSION"), versionInfo);

        module.setAttribute(tsLiteral("CERT_NONE"), SSL_CERT_NONE);
        module.setAttribute(tsLiteral("CERT_OPTIONAL"), SSL_CERT_OPTIONAL);
        module.setAttribute(tsLiteral("CERT_REQUIRED"), SSL_CERT_REQUIRED);

        module.setAttribute(tsLiteral("HAS_SNI"), true);
        // We have ECDH ciphers, but we don't yet expose the methods that let you pick the curve
        module.setAttribute(tsLiteral("HAS_ECDH"), false);
        module.setAttribute(tsLiteral("HAS_NPN"), false);
        module.setAttribute(tsLiteral("HAS_ALPN"), true);
        module.setAttribute(tsLiteral("HAS_NEVER_CHECK_COMMON_NAME"), false);
        module.setAttribute(tsLiteral("HAS_SSLv2"), false);
        boolean hasSSLv3 = supportedProtocols.contains(SSLProtocol.SSLv3);
        module.setAttribute(tsLiteral("HAS_SSLv3"), hasSSLv3);
        module.setAttribute(tsLiteral("HAS_TLSv1"), supportedProtocols.contains(SSLProtocol.TLSv1));
        module.setAttribute(tsLiteral("HAS_TLSv1_1"), supportedProtocols.contains(SSLProtocol.TLSv1_1));
        module.setAttribute(tsLiteral("HAS_TLSv1_2"), supportedProtocols.contains(SSLProtocol.TLSv1_2));
        module.setAttribute(tsLiteral("HAS_TLSv1_3"), supportedProtocols.contains(SSLProtocol.TLSv1_3));

        module.setAttribute(tsLiteral("PROTO_MINIMUM_SUPPORTED"), SSLProtocol.PROTO_MINIMUM_SUPPORTED);
        module.setAttribute(tsLiteral("PROTO_MAXIMUM_SUPPORTED"), SSLProtocol.PROTO_MAXIMUM_SUPPORTED);
        module.setAttribute(tsLiteral("PROTO_SSLv3"), SSLProtocol.SSLv3.getId());
        module.setAttribute(tsLiteral("PROTO_TLSv1"), SSLProtocol.TLSv1.getId());
        module.setAttribute(tsLiteral("PROTO_TLSv1_1"), SSLProtocol.TLSv1_1.getId());
        module.setAttribute(tsLiteral("PROTO_TLSv1_2"), SSLProtocol.TLSv1_2.getId());
        module.setAttribute(tsLiteral("PROTO_TLSv1_3"), SSLProtocol.TLSv1_3.getId());

        if (hasSSLv3) {
            module.setAttribute(tsLiteral("PROTOCOL_SSLv3"), SSLMethod.SSL3.getPythonId());
        }
        module.setAttribute(tsLiteral("PROTOCOL_SSLv23"), SSLMethod.TLS.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLS"), SSLMethod.TLS.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLS_CLIENT"), SSLMethod.TLS_CLIENT.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLS_SERVER"), SSLMethod.TLS_SERVER.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLSv1"), SSLMethod.TLS1.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLSv1_1"), SSLMethod.TLS1_1.getPythonId());
        module.setAttribute(tsLiteral("PROTOCOL_TLSv1_2"), SSLMethod.TLS1_2.getPythonId());

        module.setAttribute(tsLiteral("SSL_ERROR_SSL"), SSLErrorCode.ERROR_SSL.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_WANT_READ"), SSLErrorCode.ERROR_WANT_READ.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_WANT_WRITE"), SSLErrorCode.ERROR_WANT_WRITE.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_WANT_X509_LOOKUP"), SSLErrorCode.ERROR_WANT_X509_LOOKUP.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_SYSCALL"), SSLErrorCode.ERROR_SYSCALL.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_ZERO_RETURN"), SSLErrorCode.ERROR_ZERO_RETURN.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_WANT_CONNECT"), SSLErrorCode.ERROR_WANT_CONNECT.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_EOF"), SSLErrorCode.ERROR_EOF.getErrno());
        module.setAttribute(tsLiteral("SSL_ERROR_INVALID_ERROR_CODE"), 10);

        module.setAttribute(tsLiteral("OP_ALL"), SSLOptions.SSL_OP_ALL);
        module.setAttribute(tsLiteral("OP_NO_SSLv2"), SSLOptions.SSL_OP_NO_SSLv2);
        module.setAttribute(tsLiteral("OP_NO_SSLv3"), SSLOptions.SSL_OP_NO_SSLv3);
        module.setAttribute(tsLiteral("OP_NO_TLSv1"), SSLOptions.SSL_OP_NO_TLSv1);
        module.setAttribute(tsLiteral("OP_NO_TLSv1_1"), SSLOptions.SSL_OP_NO_TLSv1_1);
        module.setAttribute(tsLiteral("OP_NO_TLSv1_2"), SSLOptions.SSL_OP_NO_TLSv1_2);
        module.setAttribute(tsLiteral("OP_NO_TLSv1_3"), SSLOptions.SSL_OP_NO_TLSv1_3);
        module.setAttribute(tsLiteral("OP_NO_COMPRESSION"), SSLOptions.SSL_OP_NO_COMPRESSION);
        module.setAttribute(tsLiteral("OP_NO_TICKET"), SSLOptions.SSL_OP_NO_TICKET);

        module.setAttribute(tsLiteral("VERIFY_DEFAULT"), 0);
        module.setAttribute(tsLiteral("VERIFY_CRL_CHECK_LEAF"), X509_V_FLAG_CRL_CHECK);
        module.setAttribute(tsLiteral("VERIFY_CRL_CHECK_CHAIN"), X509_V_FLAG_CRL_CHECK | X509_V_FLAG_CRL_CHECK_ALL);
        module.setAttribute(tsLiteral("VERIFY_X509_STRICT"), X509_V_FLAG_X509_STRICT);
        module.setAttribute(tsLiteral("VERIFY_X509_TRUSTED_FIRST"), X509_V_FLAG_TRUSTED_FIRST);
    }

    @Builtin(name = "txt2obj", minNumOfPositionalArgs = 1, parameterNames = {"txt", "name"})
    @ArgumentClinic(name = "txt", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class Txt2ObjNode extends PythonBinaryClinicBuiltinNode {

        private static final TruffleString T_OID_TLS_SERVER = tsLiteral("1.3.6.1.5.5.7.3.1");
        private static final TruffleString T_OID_TLS_CLIENT = tsLiteral("1.3.6.1.5.5.7.3.2");
        private static final TruffleString T_SERVER_AUTH = tsLiteral("serverAuth");
        private static final TruffleString T_CLIENT_AUTH = tsLiteral("clientAuth");
        private static final TruffleString T_TLS_WEB_SERVER_AUTHENTICATION = tsLiteral("TLS Web Server Authentication");
        private static final TruffleString T_TLS_WEB_CLIENT_AUTHENTICATION = tsLiteral("TLS Web Client Authentication");

        @Specialization
        @SuppressWarnings("unused")
        static Object txt2obj(TruffleString txt, boolean name,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // TODO implement properly
            if (equalNode.execute(T_OID_TLS_SERVER, txt, TS_ENCODING)) {
                return factory.createTuple(new Object[]{129, T_SERVER_AUTH, T_TLS_WEB_SERVER_AUTHENTICATION, txt});
            } else if (equalNode.execute(T_OID_TLS_CLIENT, txt, TS_ENCODING)) {
                return factory.createTuple(new Object[]{130, T_CLIENT_AUTH, T_TLS_WEB_CLIENT_AUTHENTICATION, txt});
            }
            throw raiseNode.get(inliningTarget).raise(NotImplementedError);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLModuleBuiltinsClinicProviders.Txt2ObjNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "nid2obj", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"nid"})
    @GenerateNodeFactory
    abstract static class Nid2ObjNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object nid2obj(Object nid) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_status")
    @GenerateNodeFactory
    abstract static class RandStatusNode extends PythonBuiltinNode {
        @Specialization
        Object randStatus() {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_add", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"string", "entropy"})
    @GenerateNodeFactory
    abstract static class RandAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object randAdd(Object string, Object entropy,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_pseudo_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandPseudoBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randPseudoBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "get_default_verify_paths")
    @GenerateNodeFactory
    abstract static class GetDefaultVerifyPathsNode extends PythonBuiltinNode {
        private static final TruffleString T_SSL_CERT_FILE = tsLiteral("SSL_CERT_FILE");
        private static final TruffleString T_SSL_CERT_DIR = tsLiteral("SSL_CERT_DIR");

        @Specialization
        Object getDefaultPaths(
                        @Cached PythonObjectFactory factory) {
            // there is no default location given by graalpython
            // in case the env variables SSL_CERT_FILE or SSL_CERT_DIR
            // are provided, ssl.py#get_default_verify_paths will take care of it
            return factory.createTuple(new Object[]{T_SSL_CERT_FILE, T_EMPTY_STRING, T_SSL_CERT_DIR, T_EMPTY_STRING});
        }
    }

    @Builtin(name = "enum_certificates", minNumOfPositionalArgs = 1, parameterNames = {"store_name"}, os = PythonOS.PLATFORM_WIN32)
    @Builtin(name = "enum_crls", minNumOfPositionalArgs = 1, parameterNames = {"store_name"}, os = PythonOS.PLATFORM_WIN32)
    @ArgumentClinic(name = "store_name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class EnumCertificatesNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLModuleBuiltinsClinicProviders.EnumCertificatesNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fail(TruffleString argument,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PermissionError);
        }
    }

    @Builtin(name = "_test_decode_cert", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    abstract static class DecodeCertNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, Object path,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            return decode(toTruffleFile(frame, inliningTarget, asPath, path, toJavaStringNode, eqNode, constructAndRaiseNode));
        }

        @TruffleBoundary
        private Object decode(TruffleFile file) throws PException {
            try (BufferedReader r = file.newBufferedReader()) {
                List<Object> certs = CertUtils.getCertificates(r);
                if (certs.isEmpty()) {
                    throw PConstructAndRaiseNode.raiseUncachedSSLError(SSL_ERR_DECODING_PEM_FILE);
                }
                Object cert = certs.get(0);
                if (!(cert instanceof X509Certificate)) {
                    throw PConstructAndRaiseNode.raiseUncachedSSLError(SSL_ERR_DECODING_PEM_FILE_UNEXPECTED_S, cert.getClass().getName());
                }
                return CertUtils.decodeCertificate(getContext().factory(), (X509Certificate) certs.get(0));
            } catch (IOException | DecoderException ex) {
                throw PConstructAndRaiseNode.raiseUncachedSSLError(SSL_CANT_OPEN_FILE_S, ex.toString());
            } catch (CertificateException | CRLException ex) {
                throw PConstructAndRaiseNode.raiseUncachedSSLError(SSL_ERR_DECODING_PEM_FILE_S, ex.toString());
            }
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, Node inliningTarget, PyUnicodeFSDecoderNode asPath, Object fileObject, TruffleString.ToJavaStringNode toJavaStringNode,
                        TruffleString.EqualNode eqNode, PConstructAndRaiseNode.Lazy constructAndRaiseNode) throws PException {
            TruffleFile file;
            try {
                file = getContext().getEnv().getPublicTruffleFile(toJavaStringNode.execute(asPath.execute(frame, fileObject)));
                if (!file.exists()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
                }
                return file;
            } catch (Exception e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            }
        }
    }
}
