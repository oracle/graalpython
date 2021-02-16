package com.oracle.graal.python.builtins.objects.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.spec.DHParameterSpec;
import javax.net.ssl.SSLContext;

import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLMethod method;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;
    private SSLCipher[] ciphers;
    private long options;
    private boolean setDefaultVerifyPaths = false;
    private SSLProtocol minimumVersion;
    private SSLProtocol maximumVersion;

    private DHParameterSpec dhParameters;
    // TODO: this is part of X509_VERIFY_PARAM, maybe replicate the whole structure
    private int verifyFlags;

    // number of TLS v1.3 session tickets
    // TODO can this be set in java?
    // TODO '2' is openssl default, but should we return it even though it might not be right?
    private int numTickets = 2;

    private String[] alpnProtocols;

    private KeyStore keystore;

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
    }

    public KeyStore getKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (keystore == null) {
            keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
        }
        return keystore;
    }

    public SSLMethod getMethod() {
        return method;
    }

    void setCertificateEntry(X509Certificate cert) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        getKeyStore().setCertificateEntry(CertUtils.getAlias(cert), cert);
    }

    void setKeyEntry(PrivateKey pk, char[] password, X509Certificate[] certs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        this.password = password;
        getKeyStore().setKeyEntry(CertUtils.getAlias(pk), pk, password, certs);
    }

    void init() throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        TrustManager[] tms = null;
        KeyManager[] kms = null;
        if (verifyMode == SSLModuleBuiltins.SSL_CERT_NONE) {
            tms = new TrustManager[]{new CertNoneTrustManager()};
        }
        if (keystore != null && keystore.size() > 0) {
            if (tms == null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keystore);
                tms = tmf.getTrustManagers();
                if (verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL) {
                    for (int i = 0; i < tms.length; i++) {
                        tms[i] = new DelegateTrustManager((X509ExtendedTrustManager) tms[i], true);
                    }
                }
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, password);
            kms = kmf.getKeyManagers();
        }
        context.init(kms, tms, null);
    }

    public SSLContext getContext() {
        return context;
    }

    public boolean getCheckHostname() {
        return checkHostname;
    }

    public void setCheckHostname(boolean checkHostname) {
        this.checkHostname = checkHostname;
    }

    int getVerifyMode() {
        return verifyMode;
    }

    void setVerifyMode(int verifyMode) {
        assert verifyMode == SSLModuleBuiltins.SSL_CERT_NONE || verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL || verifyMode == SSLModuleBuiltins.SSL_CERT_REQUIRED;
        this.verifyMode = verifyMode;
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
        this.ciphers = ciphers;
    }

    public long getOptions() {
        return options;
    }

    public void setOptions(long options) {
        this.options = options;
    }

    void setDefaultVerifyPaths() {
        this.setDefaultVerifyPaths = true;
    }

    boolean getDefaultVerifyPaths() {
        // TODO and where should this be used from?
        return this.setDefaultVerifyPaths;
    }

    int getNumTickets() {
        return this.numTickets;
    }

    void setNumTickets(int numTickets) {
        this.numTickets = numTickets;
    }

    void setDHParameters(DHParameterSpec dh) {
        this.dhParameters = dh;
    }

    DHParameterSpec getDHParameters() {
        return dhParameters;
    }

    int getVerifyFlags() {
        return verifyFlags;
    }

    void setVerifyFlags(int flags) {
        this.verifyFlags = flags;
    }

    public String[] getAlpnProtocols() {
        return alpnProtocols;
    }

    public void setAlpnProtocols(String[] alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
    }

    public SSLProtocol getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(SSLProtocol minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public SSLProtocol getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(SSLProtocol maximumVersion) {
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
        return SSLModuleBuiltins.getSupportedProtocols().stream().filter(this::allowsProtocol).map(SSLProtocol::getName).toArray(String[]::new);
    }
    
    private static class CertNoneTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static class DelegateTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private final boolean acceptEmptyChain;

        public DelegateTrustManager(X509ExtendedTrustManager delegate, boolean acceptEmptyChain) {
            this.delegate = delegate;
            this.acceptEmptyChain = acceptEmptyChain;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkClientTrusted(chain, string, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkClientTrusted(chain, string, ssle);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkServerTrusted(chain, string, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            if ((chain == null || chain.length == 0) && acceptEmptyChain) {
                return;
            }
            delegate.checkServerTrusted(chain, string, ssle);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }

}
