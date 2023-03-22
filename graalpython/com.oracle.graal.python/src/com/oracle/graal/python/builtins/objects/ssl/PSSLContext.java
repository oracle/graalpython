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

import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.LOGGER;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.X509_V_FLAG_CRL_CHECK;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.X509_V_FLAG_CRL_CHECK_ALL;
import static java.security.cert.PKIXRevocationChecker.Option.NO_FALLBACK;
import static java.security.cert.PKIXRevocationChecker.Option.ONLY_END_ENTITY;
import static java.security.cert.PKIXRevocationChecker.Option.PREFER_CRLS;

import java.io.IOException;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLMethod method;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;
    private SSLCipher[] ciphers;
    private long options;
    private SSLProtocol minimumVersion;
    private SSLProtocol maximumVersion;

    private int verifyFlags;

    private String[] alpnProtocols;

    private KeyStore caKeystore;
    private KeyStore chainKeystore;
    private Set<X509CRL> crls;

    private boolean useDefaultTrustStore;

    private char[] password = PythonUtils.EMPTY_CHAR_ARRAY;

    public PSSLContext(Object cls, Shape instanceShape, SSLMethod method, int verifyFlags, boolean checkHostname, int verifyMode, SSLContext context) {
        super(cls, instanceShape);
        assert method != null;
        this.method = method;
        this.context = context;
        this.verifyFlags = verifyFlags;
        this.checkHostname = checkHostname;
        this.verifyMode = verifyMode;
        this.ciphers = SSLModuleBuiltins.defaultCiphers;
        LOGGER.fine(() -> String.format("PSSLContext() method: %s, verifyMode: %d, verifyFlags: %d, checkHostname: %b", method, verifyMode, verifyFlags, checkHostname));
    }

    @TruffleBoundary
    private KeyStore getCAKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (caKeystore == null) {
            caKeystore = KeyStore.getInstance("JKS");

            caKeystore.load(null);
        }
        return caKeystore;
    }

    @TruffleBoundary
    private KeyStore getChainKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (chainKeystore == null) {
            chainKeystore = KeyStore.getInstance("JKS");
            chainKeystore.load(null);
        }
        return chainKeystore;
    }

    @TruffleBoundary
    public X509Certificate[] getCACerts() throws KeyStoreException, NoSuchAlgorithmException {
        List<X509Certificate> result = new ArrayList<>();
        if (caKeystore != null) {
            Enumeration<String> aliases = caKeystore.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate cert = (X509Certificate) caKeystore.getCertificate(aliases.nextElement());
                result.add(cert);
            }
        }
        if (useDefaultTrustStore) {
            X509ExtendedTrustManager tm = getDefaultTrustManager();
            for (X509Certificate cert : tm.getAcceptedIssuers()) {
                result.add(cert);
            }
        }
        return result.toArray(new X509Certificate[0]);
    }

    public SSLMethod getMethod() {
        return method;
    }

    @TruffleBoundary
    void setCAEntries(Collection<? extends Object> list) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        for (Object obj : list) {
            if (obj instanceof X509Certificate) {
                X509Certificate cert = (X509Certificate) obj;
                getCAKeyStore().setCertificateEntry(CertUtils.getAlias(cert), cert);
            } else if (obj instanceof X509CRL) {
                getCRLs().add((X509CRL) obj);
            } else {
                throw new IllegalStateException("expected X509Certificate or X509CRL but got " + obj.getClass().getName());
            }
        }
    }

    @TruffleBoundary
    private Set<X509CRL> getCRLs() {
        if (crls == null) {
            crls = new HashSet<>();
        }
        return crls;
    }

    void setCertChain(PrivateKey pk, char[] password, X509Certificate[] certs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        this.password = password;
        getChainKeyStore().setKeyEntry(CertUtils.getAlias(pk), pk, password, certs);
    }

    void init() throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, InvalidAlgorithmParameterException, IOException, CertificateException {
        X509ExtendedTrustManager defaultTrustManager = getDefaultTrustManager();
        X509ExtendedTrustManager trustManager = getX509ExtendedTrustManager(getTrustManagerFactory(getCAKeyStore()).getTrustManagers());
        TrustManager tm = new DelegateTrustManager(trustManager, defaultTrustManager, verifyMode);

        KeyManager[] kms = null;
        if (chainKeystore != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(chainKeystore, password);
            kms = kmf.getKeyManagers();
        }

        context.init(kms, new TrustManager[]{tm}, null);
    }

    private X509ExtendedTrustManager getDefaultTrustManager() throws KeyStoreException, NoSuchAlgorithmException {
        if (useDefaultTrustStore) {
            TrustManagerFactory tmf = getTrustManagerFactory();
            tmf.init((KeyStore) null);
            return getX509ExtendedTrustManager(tmf.getTrustManagers());
        }
        return null;
    }

    private static X509ExtendedTrustManager getX509ExtendedTrustManager(TrustManager[] tms) {
        for (TrustManager tm : tms) {
            if (tm instanceof X509ExtendedTrustManager) {
                return (X509ExtendedTrustManager) tm;
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("at least one X509ExtendedTrustManager should be provided.");
    }

    @TruffleBoundary
    private static TrustManagerFactory getTrustManagerFactory() throws NoSuchAlgorithmException {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    }

    @TruffleBoundary
    private TrustManagerFactory getTrustManagerFactory(KeyStore ks) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = getTrustManagerFactory();
        boolean crlCheck = (verifyFlags & X509_V_FLAG_CRL_CHECK) != 0;
        boolean crlCheckAll = (verifyFlags & X509_V_FLAG_CRL_CHECK_ALL) != 0;
        LOGGER.fine(() -> String.format("PSSLContext.getTrustManagerFactory() crlCheck: %b, crlCheckAll: %b", crlCheck, crlCheckAll));
        if (crlCheck || crlCheckAll) {
            PKIXRevocationChecker rc = (PKIXRevocationChecker) CertPathBuilder.getInstance("PKIX").getRevocationChecker();
            EnumSet<PKIXRevocationChecker.Option> opt = EnumSet.of(PREFER_CRLS, NO_FALLBACK);
            if (crlCheck) {
                opt.add(ONLY_END_ENTITY);
            }
            rc.setOptions(opt);
            PKIXBuilderParameters params = new PKIXBuilderParameters(ks, new X509CertSelector());
            params.addCertPathChecker(rc);
            if (crls != null && !crls.isEmpty()) {
                CertStore certStores = CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls));
                params.addCertStore(certStores);
                LOGGER.fine("PSSLContext.getTrustManagerFactory() adding crls");
            }
            tmf.init(new CertPathTrustManagerParameters(params));
        } else {
            tmf.init(ks);
        }
        return tmf;
    }

    public SSLContext getContext() {
        return context;
    }

    public boolean getCheckHostname() {
        return checkHostname;
    }

    public void setCheckHostname(boolean checkHostname) {
        LOGGER.fine(() -> String.format("PSSLContext.setCheckHostname: %b", checkHostname));
        this.checkHostname = checkHostname;
    }

    int getVerifyMode() {
        return verifyMode;
    }

    void setVerifyMode(int verifyMode) {
        assert verifyMode == SSLModuleBuiltins.SSL_CERT_NONE || verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL || verifyMode == SSLModuleBuiltins.SSL_CERT_REQUIRED;
        LOGGER.fine(() -> String.format("PSSLContext.setVerifyMode: %d", verifyMode));
        this.verifyMode = verifyMode;
    }

    public void setUseDefaultTrustStore(boolean useDefaultTrustStore) {
        this.useDefaultTrustStore = useDefaultTrustStore;
    }

    @TruffleBoundary
    public List<SSLCipher> computeEnabledCiphers(SSLEngine engine) {
        // We use the supported cipher suites to avoid errors, but the JDK provider will do
        // additional filtering based on the security policy before the communication starts
        Set<String> allowedCiphers = new HashSet<>(Arrays.asList(engine.getSupportedCipherSuites()));
        List<SSLCipher> enabledCiphers = new ArrayList<>(ciphers.length);
        for (SSLCipher cipher : ciphers) {
            if (allowedCiphers.contains(cipher.name())) {
                enabledCiphers.add(cipher);
            }
        }
        return enabledCiphers;
    }

    public void setCiphers(SSLCipher[] ciphers) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PSSLContext.setCiphers:");
            for (SSLCipher c : ciphers) {
                LOGGER.fine(() -> String.format("\t", c));
            }
        }
        this.ciphers = ciphers;
    }

    public long getOptions() {
        return options;
    }

    public void setOptions(long options) {
        LOGGER.fine(() -> String.format("PSSLContext.setOptions: %d", options));
        this.options = options;
    }

    int getVerifyFlags() {
        return verifyFlags;
    }

    void setVerifyFlags(int flags) {
        LOGGER.fine(() -> String.format("PSSLContext.setVerifyFlags: %d", flags));
        this.verifyFlags = flags;
    }

    public String[] getAlpnProtocols() {
        return alpnProtocols;
    }

    public void setAlpnProtocols(String[] alpnProtocols) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PSSLContext.setAlpnProtocols:");
            for (String p : alpnProtocols) {
                LOGGER.fine(() -> String.format("\t", p));
            }
        }
        this.alpnProtocols = alpnProtocols;
    }

    public SSLProtocol getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(SSLProtocol minimumVersion) {
        LOGGER.fine(() -> String.format("PSSLContext.setMinimumVersion: %s", minimumVersion));
        this.minimumVersion = minimumVersion;
    }

    public SSLProtocol getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(SSLProtocol maximumVersion) {
        LOGGER.fine(() -> String.format("PSSLContext.setMaximumVersion: %s", maximumVersion));
        this.maximumVersion = maximumVersion;
    }

    public boolean allowsProtocol(SSLProtocol protocol) {
        boolean disabledByOption = (options & protocol.getDisableOption()) == 0;
        boolean inMinBound = minimumVersion == null || protocol.getId() >= minimumVersion.getId();
        boolean inMaxBound = maximumVersion == null || protocol.getId() <= maximumVersion.getId();
        return inMinBound && inMaxBound && disabledByOption && method.allowsProtocol(protocol);
    }

    @TruffleBoundary
    public String[] computeEnabledProtocols() {
        List<SSLProtocol> supportedProtocols = SSLModuleBuiltins.getSupportedProtocols();
        List<String> list = new ArrayList<>(supportedProtocols.size());
        for (SSLProtocol protocol : supportedProtocols) {
            if (allowsProtocol(protocol)) {
                String name = protocol.getName();
                list.add(name);
            }
        }
        return list.toArray(new String[0]);
    }

    private static class DelegateTrustManager extends X509ExtendedTrustManager {

        private final X509ExtendedTrustManager delegate;
        private final X509ExtendedTrustManager defaultTM;
        private final int verifyMode;

        private X509Certificate[] issuers;

        public DelegateTrustManager(X509ExtendedTrustManager delegate, X509ExtendedTrustManager defaultTM, int verifyMode) {
            this.delegate = delegate;
            this.defaultTM = defaultTM;
            this.verifyMode = verifyMode;
            LOGGER.fine(() -> String.format("PSSLContext.init() using DelegateTrustManager, verifyMode=",
                            verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL ? "SSL_CERT_OPTIONAL" : "SSL_CERT_REQUIRED"));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (skipCheckClientTrusted(chain)) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkClientTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM != null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkClientTrusted(chain, authType);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            if (skipCheckClientTrusted(chain)) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkClientTrusted(chain, string, socket);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM == null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkClientTrusted(chain, string, socket);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            if (skipCheckClientTrusted(chain)) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkClientTrusted(chain, string, ssle);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM == null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkClientTrusted(chain, string, ssle);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (skipCheckServerTrusted()) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM == null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkServerTrusted(chain, authType);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            if (skipCheckServerTrusted()) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkServerTrusted(chain, string, socket);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM == null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkServerTrusted(chain, string, socket);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            if (skipCheckServerTrusted()) {
                return;
            }
            if (canCheckDelegateTrustManager()) {
                try {
                    delegate.checkServerTrusted(chain, string, ssle);
                    return;
                } catch (CertificateException e) {
                    if (defaultTM == null) {
                        throw e;
                    }
                }
            }
            if (canCheckDefaultTrustManager()) {
                defaultTM.checkServerTrusted(chain, string, ssle);
                return;
            }
            throw new CertificateException("certificate verify failed: unable to get local issuer certificate");
        }

        private boolean skipCheckClientTrusted(X509Certificate[] chain) {
            return verifyMode == SSLModuleBuiltins.SSL_CERT_NONE ||
                            (verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL && (chain == null || chain.length == 0));
        }

        private boolean skipCheckServerTrusted() {
            return verifyMode == SSLModuleBuiltins.SSL_CERT_NONE;
        }

        private boolean canCheckDelegateTrustManager() {
            return delegate.getAcceptedIssuers().length > 0;
        }

        private boolean canCheckDefaultTrustManager() {
            return defaultTM != null && defaultTM.getAcceptedIssuers().length > 0;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            if (issuers == null) {
                if (defaultTM == null) {
                    issuers = delegate.getAcceptedIssuers();
                } else {
                    X509Certificate[] delegateIssuers = delegate.getAcceptedIssuers();
                    X509Certificate[] defaultIssuers = defaultTM.getAcceptedIssuers();
                    issuers = new X509Certificate[delegateIssuers.length + defaultIssuers.length];
                    PythonUtils.arraycopy(delegateIssuers, 0, issuers, 0, delegateIssuers.length);
                    PythonUtils.arraycopy(defaultIssuers, 0, issuers, delegateIssuers.length, defaultIssuers.length);
                }
            }
            return issuers;
        }
    }

}
