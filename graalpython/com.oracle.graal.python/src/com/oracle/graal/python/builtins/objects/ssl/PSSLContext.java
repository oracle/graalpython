package com.oracle.graal.python.builtins.objects.ssl;

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

    // number of TLS v1.3 session tickets
    // TODO can this be set in java?
    // TODO '2' is openssl default, but should we return it even though it might not be right?
    private int numTickets = 2;

    public PSSLContext(Object cls, Shape instanceShape, SSLProtocolVersion version, SSLContext context) {
        super(cls, instanceShape);
        assert version != null;
        this.version = version;
        this.context = context;
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

}
