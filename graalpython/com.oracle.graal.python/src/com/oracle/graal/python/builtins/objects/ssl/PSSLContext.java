package com.oracle.graal.python.builtins.objects.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.crypto.spec.DHParameterSpec;
import javax.net.ssl.SSLContext;

import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.object.Shape;
import java.security.KeyManagementException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLMethod method;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;
    private SSLCipher[] ciphers;
    private long options;
    private boolean setDefaultVerifyPaths = false;

    private DHParameterSpec dhParameters;
    // TODO: this is part of X509_VERIFY_PARAM, maybe replicate the whole structure
    private int verifyFlags;

    // number of TLS v1.3 session tickets
    // TODO can this be set in java?
    // TODO '2' is openssl default, but should we return it even though it might not be right?
    private int numTickets = 2;

    private String[] alpnProtocols;

    private KeyStore keystore;

    public PSSLContext(Object cls, Shape instanceShape, SSLMethod method, int verifyFlags, boolean checkHostname, int verifyMode, SSLContext context) {
        super(cls, instanceShape);
        assert method != null;
        this.method = method;
        this.context = context;
        this.verifyFlags = verifyFlags;
        this.checkHostname = checkHostname;
        this.verifyMode = verifyMode;
        this.ciphers = SSLModuleBuiltins.defaultCiphers;
        assert this.ciphers != null;
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

    private char[] password = PythonUtils.EMPTY_CHAR_ARRAY;

    void setCertificateEntry(String alias, Certificate cert) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        getKeyStore().setCertificateEntry(alias, cert);
    }

    void setKeyEntry(String alias, PrivateKey pk, char[] password, X509Certificate[] certs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        this.password = password;
        for (X509Certificate cert : certs) {
            getKeyStore().setKeyEntry(alias, pk, password, certs);
        }
    }

    void init() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        TrustManager[] tms = null;
        if (verifyMode == SSLModuleBuiltins.SSL_CERT_NONE) {
            // TODO: what about optional?
            tms = new TrustManager[]{new X509TrustManager() {
                private X509Certificate[] chain;

                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    this.chain = chain;
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    this.chain = chain;
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return chain;
                }
            }};
        }
        if (keystore != null && keystore.size() > 0) {
            if (tms == null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(getKeyStore());
                tms = tmf.getTrustManagers();
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(getKeyStore(), password);
            context.init(kmf.getKeyManagers(), tms, null);
        } else {
            context.init(null, tms, null);
        }
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

    public SSLCipher[] getCiphers() {
        return ciphers;
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
}
