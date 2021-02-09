package com.oracle.graal.python.builtins.objects.ssl;

import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.truffle.api.object.Shape;

public final class PSSLSocket extends PythonBuiltinObject {
    private final PSSLContext context;
    // May be null for SSLSocket backed by MemoryBIO
    private final PSocket socket;
    private final SSLEngine engine;
    private Object owner;
    private String serverHostname;

    private final MemoryBIO networkInboundBIO;
    private final MemoryBIO networkOutboundBIO;
    private final MemoryBIO applicationInboundBIO = new MemoryBIO();

    private boolean handshakeComplete = false;

    public PSSLSocket(Object cls, Shape instanceShape, PSSLContext context, SSLEngine engine, PSocket socket) {
        super(cls, instanceShape);
        this.context = context;
        this.engine = engine;
        this.socket = socket;
        this.networkInboundBIO = new MemoryBIO();
        this.networkOutboundBIO = new MemoryBIO();
    }

    public PSSLSocket(Object cls, Shape instanceShape, PSSLContext context, SSLEngine engine, MemoryBIO networkInboundBIO, MemoryBIO networkOutboundBIO) {
        super(cls, instanceShape);
        this.context = context;
        this.engine = engine;
        this.socket = null;
        this.networkInboundBIO = networkInboundBIO;
        this.networkOutboundBIO = networkOutboundBIO;
    }

    public PSSLContext getContext() {
        return context;
    }

    public PSocket getSocket() {
        return socket;
    }

    public SSLEngine getEngine() {
        return engine;
    }

    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public void setHandshakeComplete(boolean handshakeComplete) {
        this.handshakeComplete = handshakeComplete;
    }

    public MemoryBIO getNetworkInboundBIO() {
        return networkInboundBIO;
    }

    public MemoryBIO getNetworkOutboundBIO() {
        return networkOutboundBIO;
    }

    public MemoryBIO getApplicationInboundBIO() {
        return applicationInboundBIO;
    }

    public Object getOwner() {
        return owner;
    }

    public void setOwner(Object owner) {
        this.owner = owner;
    }

    public String getServerHostname() {
        return serverHostname;
    }

    public void setServerHostname(String serverHostname) {
        this.serverHostname = serverHostname;
    }
}
