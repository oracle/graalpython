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
import com.oracle.truffle.api.object.Shape;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLProtocolVersion version;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;
    private String[] ciphers;
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

    public PSSLContext(Object cls, Shape instanceShape, SSLProtocolVersion version, int verifyFlags, boolean chekHostname, int verifyMode, SSLContext context) {
        super(cls, instanceShape);
        assert version != null;
        this.version = version;
        this.context = context;
        this.verifyFlags = verifyFlags;
        this.checkHostname = chekHostname;
        this.verifyMode = verifyMode;
    }

    public KeyStore getKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (keystore == null) {
            keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
        }
        return keystore;
    }

    public SSLProtocolVersion getVersion() {
        return version;
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

    public String[] getCiphers() {
        return ciphers;
    }

    public void setCiphers(String[] ciphers) {
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
