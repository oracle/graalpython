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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.LOGGER;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_POSIX;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.bouncycastle.util.encoders.DecoderException;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils.NeedsPasswordException;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils.NoCertificateFoundException;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
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
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.IPAddressUtil;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLContext)
public final class SSLContextBuiltins extends PythonBuiltins {

    private static final TruffleString T_ENVIRON = tsLiteral("environ");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLContextBuiltinsFactory.getFactories();
    }

    @Builtin(name = "_SSLContext", constructsClass = PythonBuiltinClassType.PSSLContext, minNumOfPositionalArgs = 2, parameterNames = {"type", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SSLContextNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static PSSLContext createContext(VirtualFrame frame, Object type, int protocol,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            SSLMethod method = SSLMethod.fromPythonId(protocol);
            if (method == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, "NULL");
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
                PSSLContext context = factory.createSSLContext(type, method, SSLModuleBuiltins.X509_V_FLAG_TRUSTED_FIRST, checkHostname, verifyMode, createSSLContext());
                long options = SSLOptions.SSL_OP_ALL;
                if (method != SSLMethod.SSL3) {
                    options |= SSLOptions.SSL_OP_NO_SSLv3;
                }
                context.setOptions(options);
                return context;
            } catch (NoSuchAlgorithmException e) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.INVALID_OR_UNSUPPORTED_PROTOCOL_VERSION, e);
            } catch (KeyManagementException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL, e);
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
    static SSLEngine createSSLEngine(Node raisingNode, PSSLContext context, boolean serverMode, String serverHostname) {
        try {
            context.init();
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException | InvalidAlgorithmParameterException | IOException | CertificateException ex) {
            throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL, ex);
        }
        SSLParameters parameters = new SSLParameters();
        SSLEngine engine;
        // Set SNI hostname only for non-IP hostnames
        if (serverHostname != null && !isIPAddress(serverHostname)) {
            try {
                parameters.setServerNames(Collections.singletonList(new SNIHostName(serverHostname)));
            } catch (IllegalArgumentException e) {
                if (serverHostname.contains("\0")) {
                    throw PRaiseNode.raiseUncached(raisingNode, TypeError, ErrorMessages.ARG_MUST_BE_ENCODED_NON_NULL);
                }
                throw PRaiseNode.raiseUncached(raisingNode, ValueError, ErrorMessages.INVALID_HOSTNAME);
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

        if (context.getAlpnProtocols() != null) {
            parameters.setApplicationProtocols(context.getAlpnProtocols());
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

    @TruffleBoundary
    private static boolean isIPAddress(String str) {
        return IPAddressUtil.isIPv4LiteralAddress(str) || IPAddressUtil.isIPv6LiteralAddress(str) ||
                        str.startsWith("[") && str.endsWith("]") && IPAddressUtil.isIPv6LiteralAddress(str.substring(1, str.length() - 1));
    }

    @Builtin(name = "_wrap_socket", minNumOfPositionalArgs = 3, parameterNames = {"$self", "sock", "server_side", "server_hostname"}, keywordOnlyNames = {"owner", "session"})
    @ArgumentClinic(name = "server_side", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class WrapSocketNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object wrap(PSSLContext context, PSocket sock, boolean serverSide, Object serverHostnameObj, Object owner, @SuppressWarnings("unused") PNone session,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached PythonObjectFactory factory) {
            TruffleString serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(inliningTarget, serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(inliningTarget, context, serverSide, serverHostname == null ? null : toJavaStringNode.execute(serverHostname));
            PSSLSocket sslSocket = factory.createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, sock);
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            sslSocket.setServerHostname(serverHostname);
            return sslSocket;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object wrap(Object context, Object sock, Object serverSide, Object serverHostname, Object owner, Object session,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_WRAP_SOCKET_CALL);
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
        static Object wrap(PSSLContext context, PMemoryBIO incoming, PMemoryBIO outgoing, boolean serverSide, Object serverHostnameObj, Object owner,
                        @SuppressWarnings("unused") PNone session,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached PythonObjectFactory factory) {
            TruffleString serverHostname = null;
            if (!(serverHostnameObj instanceof PNone)) {
                serverHostname = cast.cast(inliningTarget, serverHostnameObj, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "serverHostname", serverHostnameObj);
            }
            SSLEngine engine = createSSLEngine(inliningTarget, context, serverSide, serverHostname == null ? null : toJavaStringNode.execute(serverHostname));
            PSSLSocket sslSocket = factory.createSSLSocket(PythonBuiltinClassType.PSSLSocket, context, engine, incoming, outgoing);
            if (!(owner instanceof PNone)) {
                sslSocket.setOwner(owner);
            }
            sslSocket.setServerHostname(serverHostname);
            return sslSocket;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object wrap(Object context, Object incoming, Object outgoing, Object serverSide, Object serverHostname, Object owner, Object session,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_WRAP_BIO_CALL);
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

        @Specialization(guards = "!isNoValue(value)")
        static Object setCheckHostname(VirtualFrame frame, PSSLContext self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            boolean checkHostname = isTrueNode.execute(frame, inliningTarget, value);
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

        @Specialization(guards = "!isNoValue(flags)")
        static Object setVerifyFlags(VirtualFrame frame, PSSLContext self, Object flags,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            self.setVerifyFlags(asSizeNode.executeLossy(frame, inliningTarget, flags));
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

        @Specialization(guards = "!isNoValue(valueObj)")
        static Object setOption(VirtualFrame frame, PSSLContext self, Object valueObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaLongExactNode cast) {
            long value = cast.execute(inliningTarget, indexNode.execute(frame, inliningTarget, valueObj));
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

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PSSLContext self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int mode = asSizeNode.executeLossy(frame, inliningTarget, value);
            if (mode == SSLModuleBuiltins.SSL_CERT_NONE && self.getCheckHostname()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.CANNOT_SET_VERIFY_MODE_TO_CERT_NONE);
            }
            switch (mode) {
                case SSLModuleBuiltins.SSL_CERT_NONE:
                case SSLModuleBuiltins.SSL_CERT_OPTIONAL:
                case SSLModuleBuiltins.SSL_CERT_REQUIRED:
                    self.setVerifyMode(mode);
                    return PNone.NONE;
                default:
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.INVALID_VALUE_FOR_VERIFY_MODE);
            }
        }
    }

    private static void setMinMaxVersion(Node inliningTarget, PRaiseNode.Lazy raiseNode, PSSLContext context, boolean maximum, int value) {
        if (context.getMethod().isSingleVersion()) {
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.CONTEXT_DOESNT_SUPPORT_MIN_MAX);
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
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.UNSUPPORTED_PROTOCOL_VERSION, value);
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

        @Specialization(guards = "!isNoValue(obj)")
        static Object set(VirtualFrame frame, PSSLContext self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            setMinMaxVersion(inliningTarget, raiseNode, self, false, asSizeNode.executeExact(frame, inliningTarget, obj));
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

        @Specialization(guards = "!isNoValue(obj)")
        static Object set(VirtualFrame frame, PSSLContext self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            setMinMaxVersion(inliningTarget, raiseNode, self, true, asSizeNode.executeExact(frame, inliningTarget, obj));
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_ciphers", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetCiphersNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PList getCiphers(PSSLContext self) {
            List<SSLCipher> ciphers = self.computeEnabledCiphers(self.getContext().createSSLEngine());
            Object[] dicts = new Object[ciphers.size()];
            for (int i = 0; i < dicts.length; i++) {
                dicts[i] = PythonObjectFactory.getUncached().createDict(ciphers.get(i).asKeywords());
            }
            return PythonObjectFactory.getUncached().createList(dicts);
        }
    }

    @Builtin(name = "set_ciphers", minNumOfPositionalArgs = 2, parameterNames = {"$self", "cipherlist"})
    @ArgumentClinic(name = "cipherlist", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class SetCiphersNode extends PythonClinicBuiltinNode {
        @Specialization
        Object setCiphers(PSSLContext self, TruffleString cipherlist,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            self.setCiphers(SSLCipherSelector.selectCiphers(this, toJavaStringNode.execute(cipherlist)));
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
    abstract static class NumTicketsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(value)")
        static int get(PSSLContext self, PNone value,
                        @Shared @Cached PRaiseNode raiseNode) {
            // not used yet so rather raise error
            throw raiseNode.raise(NotImplementedError);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PSSLContext self, Object value,
                        @Shared @Cached PRaiseNode raiseNode) {
            // not used yet so rather raise error
            throw raiseNode.raise(NotImplementedError);
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
    abstract static class SNICallbackNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object notImplemented(@SuppressWarnings("unused") PSSLContext self, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(NotImplementedError);
        }
    }

    @Builtin(name = "post_handshake_auth", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PostHandshakeAuthNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object pha(@SuppressWarnings("unused") PSSLContext self) {
            // JDK doesn't implement post handshake auth. CPython returns None when it's not
            // available
            return PNone.NONE;
        }
    }

    @Builtin(name = "set_default_verify_paths", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetDefaultVerifyPathsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object set(VirtualFrame frame, PSSLContext self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached("createEnvironLookup()") GetAttributeNode getAttribute,
                        @Cached HashingStorageGetItem getItem,
                        @Cached("createCertFileKey()") PBytes certFileKey,
                        @Cached("createCertDirKey()") PBytes certDirKey,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {

            PythonModule posix;
            if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                posix = getContext().lookupBuiltinModule(T_NT);
            } else {
                posix = getContext().lookupBuiltinModule(T_POSIX);
            }
            PDict environ = (PDict) getAttribute.executeObject(frame, posix);
            HashingStorage storage = environ.getDictStorage();

            TruffleFile file = toTruffleFile(frame, asPath, getItem.execute(frame, inliningTarget, storage, certFileKey), toJavaStringNode);
            TruffleFile path = toTruffleFile(frame, asPath, getItem.execute(frame, inliningTarget, storage, certDirKey), toJavaStringNode);
            if (file != null || path != null) {
                LOGGER.fine(() -> String.format("set_default_verify_paths file: %s. path: %s", file != null ? file.getPath() : "None", path != null ? path.getPath() : "None"));
                try {
                    self.setCAEntries(CertUtils.loadVerifyLocations(file, path));
                } catch (IOException | DecoderException | GeneralSecurityException | NoCertificateFoundException ex) {
                    // do not raise any errors
                    LOGGER.log(Level.FINER, "", ex);
                }
            } else {
                self.setUseDefaultTrustStore(true);
            }
            return PNone.NONE;
        }

        @NeverDefault
        @TruffleBoundary
        protected PBytes createCertFileKey() {
            return PythonObjectFactory.getUncached().createBytes("SSL_CERT_FILE".getBytes());
        }

        @NeverDefault
        @TruffleBoundary
        protected PBytes createCertDirKey() {
            return PythonObjectFactory.getUncached().createBytes("SSL_CERT_DIR".getBytes());
        }

        @NeverDefault
        protected static GetAttributeNode createEnvironLookup() {
            return GetAttributeNode.create(T_ENVIRON);
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, PyUnicodeFSDecoderNode asPath, Object path, TruffleString.ToJavaStringNode toJavaStringNode) throws PException {
            if (path == null) {
                return null;
            }
            TruffleFile file;
            try {
                file = getContext().getEnv().getPublicTruffleFile(toJavaStringNode.execute(asPath.execute(frame, path)));
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
    abstract static class CertStoreStatsNode extends PythonUnaryBuiltinNode {

        public static final TruffleString T_X509 = tsLiteral("x509");
        public static final TruffleString T_CRL = tsLiteral("crl");
        public static final TruffleString T_X509_CA = tsLiteral("x509_ca");

        @Specialization
        static Object storeStats(VirtualFrame frame, PSSLContext self,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                int x509 = 0, crl = 0, ca = 0;
                for (X509Certificate cert : self.getCACerts()) {
                    boolean[] keyUsage = CertUtils.getKeyUsage(cert);
                    if (CertUtils.isCrl(keyUsage)) {
                        crl++;
                    } else {
                        x509++;
                        if (CertUtils.isCA(cert, keyUsage)) {
                            ca++;
                        }
                    }
                }
                return factory.createDict(new PKeyword[]{new PKeyword(T_X509, x509), new PKeyword(T_CRL, crl), new PKeyword(T_X509_CA, ca)});
            } catch (Exception ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL, ex);
            }
        }
    }

    @Builtin(name = "load_verify_locations", minNumOfPositionalArgs = 1, parameterNames = {"$self", "cafile", "capath", "cadata"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadVerifyLocationsNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, Object cafile, Object capath, Object cadata,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached CastToJavaStringNode castToString,
                        @Cached ToByteArrayNode toBytes,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (cafile instanceof PNone && capath instanceof PNone && cadata instanceof PNone) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CA_FILE_PATH_DATA_CANNOT_BE_ALL_OMMITED);
            }
            if (!(cafile instanceof PNone) && !PGuards.isString(cafile) && !PGuards.isBytes(cafile)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "cafile");
            }
            if (!(capath instanceof PNone) && !PGuards.isString(capath) && !PGuards.isBytes(capath)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "capath");
            }
            final TruffleFile file;
            if (!(cafile instanceof PNone)) {
                file = toTruffleFile(frame, inliningTarget, asPath, cafile, toJavaStringNode, eqNode, constructAndRaiseNode);
                if (!file.exists()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
                }
            } else {
                file = null;
            }
            final TruffleFile path;
            if (!(capath instanceof PNone)) {
                path = toTruffleFile(frame, inliningTarget, asPath, capath, toJavaStringNode, eqNode, constructAndRaiseNode);
            } else {
                path = null;
            }

            try {
                if (!(cadata instanceof PNone)) {
                    Collection<?> certificates;
                    try {
                        certificates = fromString(inliningTarget, castToString.execute(cadata), raiseNode);
                    } catch (CannotCastException cannotCastException) {
                        if (cadata instanceof PBytesLike) {
                            certificates = fromBytesLike(toBytes.execute(inliningTarget, ((PBytesLike) cadata).getSequenceStorage()));
                        } else {
                            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_SHOULD_BE_ASCII_OR_BYTELIKE, "cadata");
                        }
                    }
                    self.setCAEntries(certificates);
                }

                if (file != null || path != null) {
                    LOGGER.fine(() -> String.format("LoadVerifyLocationsNode cafile: %s, capath: %s", file != null ? file.getPath() : "None", path != null ? path.getPath() : "None"));
                    // https://www.openssl.org/docs/man1.1.1/man3/SSL_CTX_load_verify_locations.html
                    try {
                        self.setCAEntries(CertUtils.loadVerifyLocations(file, path));
                    } catch (NoCertificateFoundException e) {
                        throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_NO_CERTIFICATE_OR_CRL_FOUND, ErrorMessages.NO_CERTIFICATE_OR_CRL_FOUND);
                    } catch (IOException | DecoderException e) {
                        throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.X509_PEM_LIB);
                    }
                }
            } catch (IOException | GeneralSecurityException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL, ex);
            }
            return PNone.NONE;
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, Node inliningTarget, PyUnicodeFSDecoderNode asPath, Object fileObject, TruffleString.ToJavaStringNode toJavaStringNode,
                        TruffleString.EqualNode eqNode, PConstructAndRaiseNode.Lazy constructAndRaiseNode) throws PException {
            try {
                return getContext().getEnv().getPublicTruffleFile(toJavaStringNode.execute(asPath.execute(frame, fileObject)));
            } catch (Exception e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            }
        }

        private static List<Object> fromString(Node inliningTarget, String dataString, PRaiseNode.Lazy raiseNode)
                        throws IOException, CertificateException, CRLException {
            if (dataString.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMPTY_CERTIFICATE_DATA);
            }
            return getCertificates(dataString);
        }

        @TruffleBoundary
        private static List<Object> getCertificates(String dataString) throws PException, CRLException, IOException, CertificateException {
            try (BufferedReader r = new BufferedReader(new StringReader(dataString))) {
                try {
                    List<Object> certificates = CertUtils.getCertificates(r);
                    if (certificates.isEmpty()) {
                        throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_NO_START_LINE, ErrorMessages.SSL_PEM_NO_START_LINE);
                    }
                    return certificates;
                } catch (DecoderException e) {
                    throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_BAD_BASE64_DECODE, ErrorMessages.BAD_BASE64_DECODE);
                } catch (IOException e) {
                    throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                }
            }
        }

        @TruffleBoundary
        private static Collection<?> fromBytesLike(byte[] bytes) {
            try {
                return CertUtils.generateCertificates(bytes);
            } catch (CertificateException ex) {
                String msg = ex.getMessage();
                if (msg != null) {
                    if (msg.contains("No certificate data found")) {
                        throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_NOT_ENOUGH_DATA, ErrorMessages.NOT_ENOUGH_DATA);
                    }
                } else {
                    msg = "error while reading cadata";
                }
                throw PConstructAndRaiseNode.raiseUncachedSSLError(null, SSLErrorCode.ERROR_SSL, toTruffleStringUncached(msg));
            }
        }
    }

    @Builtin(name = "load_cert_chain", minNumOfPositionalArgs = 2, parameterNames = {"$self", "certfile", "keyfile", "password"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LoadCertChainNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object load(VirtualFrame frame, PSSLContext self, Object certfile, Object keyfile, Object passwordObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached GetPasswordNode getPasswordNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!PGuards.isString(certfile) && !PGuards.isBytes(certfile)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "certfile");
            }
            if (!(keyfile instanceof PNone) && !PGuards.isString(keyfile) && !PGuards.isBytes(keyfile)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, "keyfile");
            }
            Object kf = keyfile instanceof PNone ? certfile : keyfile;
            TruffleFile certTruffleFile = toTruffleFile(frame, inliningTarget, asPath.execute(frame, certfile), toJavaStringNode, eqNode, constructAndRaiseNode);
            TruffleFile keyTruffleFile = toTruffleFile(frame, inliningTarget, asPath.execute(frame, kf), toJavaStringNode, eqNode, constructAndRaiseNode);
            try {
                try {
                    return load(getContext(), certTruffleFile, keyTruffleFile, null, self);
                } catch (NeedsPasswordException e) {
                    if (passwordObj != PNone.NONE) {
                        char[] password = getPasswordNode.execute(frame, passwordObj);
                        try {
                            return load(getContext(), certTruffleFile, keyTruffleFile, password, self);
                        } catch (NeedsPasswordException e1) {
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                    }
                    throw raiseNode.get(inliningTarget).raise(NotImplementedError, ErrorMessages.PASSWORD_NOT_IMPLEMENTED);
                }
            } catch (IOException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @TruffleBoundary
        private Object load(PythonContext context, TruffleFile certTruffleFile, TruffleFile keyTruffleFile, char[] password, PSSLContext self)
                        throws IOException, NeedsPasswordException {
            try (BufferedReader certReader = getReader(certTruffleFile, "certfile");
                            BufferedReader keyReader = getReader(keyTruffleFile, "keyfile")) {
                return load(context, self, certReader, keyReader, password);
            }
        }

        private BufferedReader getReader(TruffleFile file, String arg) throws IOException {
            try {
                LOGGER.fine(() -> String.format("load_cert_chain %s:%s", arg, file.getPath()));
                return file.newBufferedReader();
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.S_SHOULD_BE_A_VALID_FILESYSTEMPATH, arg);
            }
        }

        private static Object load(PythonContext context, PSSLContext self, BufferedReader certReader, BufferedReader keyReader, char[] password) throws NeedsPasswordException {
            // TODO add logging
            try {
                X509Certificate[] certs;
                try {
                    List<Object> certificates = CertUtils.getCertificates(certReader, true);
                    certs = certificates.toArray(new X509Certificate[certificates.size()]);
                    if (certs.length == 0) {
                        throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                    }
                } catch (IOException | DecoderException e) {
                    throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
                }
                // if keyReader and certReader are from the same file, key is expected to come first
                PrivateKey pk = CertUtils.getPrivateKey(context, keyReader, password, certs[0]);
                self.setCertChain(pk, PythonUtils.EMPTY_CHAR_ARRAY, certs);
                return PNone.NONE;
            } catch (GeneralSecurityException | IOException ex) {
                throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL, ex);
            }
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, Node inliningTarget, TruffleString path, TruffleString.ToJavaStringNode toJavaStringNode, TruffleString.EqualNode eqNode,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode) throws PException {
            try {
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(toJavaStringNode.execute(path));
                if (!file.exists()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
                }
                return file;
            } catch (Exception e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            }
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    abstract static class GetPasswordNode extends PNodeWithContext {
        // PEM_BUFSIZE
        private static final int MAX_LEN = 1024;

        public abstract char[] execute(VirtualFrame frame, Object passwordObj);

        @Specialization(guards = "isString(password)")
        static char[] doString(Object password,
                        @Cached CastToJavaStringNode cast,
                        @Shared @Cached PRaiseNode raiseNode) {
            String str = cast.execute(password);
            checkPasswordLength(raiseNode, str.length());
            return stringToChars(str);
        }

        @Specialization(limit = "2")
        static char[] doBytes(PBytesLike bytes,
                        @CachedLibrary("bytes") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode raiseNode) {
            byte[] data = bufferLib.getInternalOrCopiedByteArray(bytes);
            int length = bufferLib.getBufferLength(bytes);
            checkPasswordLength(raiseNode, length);
            char[] res = new char[length];
            for (int i = 0; i < res.length; i++) {
                res[i] = (char) data[i];
            }
            return res;
        }

        @Fallback
        static char[] doCallable(VirtualFrame frame, Object callable,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached CallNode callNode,
                        @Cached GetPasswordNode recursive,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (callableCheckNode.execute(inliningTarget, callable)) {
                Object result = callNode.execute(frame, callable);
                if (PGuards.isString(result) || result instanceof PBytesLike) {
                    return recursive.execute(frame, result);
                }
                throw raiseNode.raise(TypeError, ErrorMessages.PSSWD_CALLBACK_MUST_RETURN_STR);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.PSSWD_SHOULD_BE_STR_OR_CALLABLE);
        }

        @TruffleBoundary
        private static char[] stringToChars(String str) {
            return str.toCharArray();
        }

        private static void checkPasswordLength(PRaiseNode raiseNode, int length) {
            if (length > MAX_LEN) {
                throw raiseNode.raise(ValueError, ErrorMessages.PSSWD_CANNOT_BE_LONGER_THAN_D_BYTES, MAX_LEN);
            }
        }
    }

    @Builtin(name = "load_dh_params", minNumOfPositionalArgs = 2, parameterNames = {"$self", "filepath"})
    @GenerateNodeFactory
    abstract static class LoadDhParamsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone load(VirtualFrame frame, PSSLContext self, Object pathObject,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached PRaiseNode raiseNode) {
            TruffleString path = asPath.execute(frame, pathObject);
            // not used yet so rather raise error
            throw raiseNode.raise(NotImplementedError);
            // File file = new File(path);
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
    }

    @Builtin(name = "_set_alpn_protocols", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "protos"})
    @ArgumentClinic(name = "protos", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class SetAlpnProtocols extends PythonBinaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static Object setFromBuffer(VirtualFrame frame, PSSLContext self, Object buffer,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                self.setAlpnProtocols(parseProtocols(bytes, len));
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @TruffleBoundary
        private static String[] parseProtocols(byte[] bytes, int length) {
            List<String> protocols = new ArrayList<>();
            int i = 0;
            while (i < length) {
                int len = bytes[i];
                i++;
                if (i + len <= length) {
                    protocols.add(new String(bytes, i, len, StandardCharsets.US_ASCII));
                }
                i += len;
            }
            return protocols.toArray(new String[0]);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.SetAlpnProtocolsClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "get_ca_certs", minNumOfPositionalArgs = 1, parameterNames = {"$self", "binary_form"})
    @ArgumentClinic(name = "binary_form", conversion = ArgumentClinic.ClinicConversion.Boolean, useDefaultForNone = true, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class GetCACerts extends PythonBinaryClinicBuiltinNode {
        @Specialization(guards = "!binary_form")
        Object getCerts(VirtualFrame frame, PSSLContext self, @SuppressWarnings("unused") boolean binary_form,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            try {
                List<PDict> result = PythonUtils.newList();
                for (X509Certificate cert : self.getCACerts()) {
                    if (CertUtils.isCA(cert, CertUtils.getKeyUsage(cert))) {
                        PythonUtils.add(result, CertUtils.decodeCertificate(getContext().factory(), cert));
                    }
                }
                return factory.createList(PythonUtils.toArray(result));
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateParsingException ex) {
                throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @Specialization(guards = "binary_form")
        static Object getCertsBinary(PSSLContext self, @SuppressWarnings("unused") boolean binary_form,
                        @Shared @Cached PythonObjectFactory factory) {
            try {
                List<PBytes> result = PythonUtils.newList();
                for (X509Certificate cert : self.getCACerts()) {
                    if (CertUtils.isCA(cert, CertUtils.getKeyUsage(cert))) {
                        PythonUtils.add(result, factory.createBytes(CertUtils.getEncoded(cert)));
                    }
                }
                return factory.createList(PythonUtils.toArray(result));
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateEncodingException ex) {
                throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL, ex);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLContextBuiltinsClinicProviders.GetCACertsClinicProviderGen.INSTANCE;
        }
    }
}
