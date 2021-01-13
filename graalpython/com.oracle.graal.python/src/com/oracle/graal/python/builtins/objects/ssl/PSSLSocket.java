package com.oracle.graal.python.builtins.objects.ssl;

import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.truffle.api.object.Shape;

public final class PSSLSocket extends PythonBuiltinObject {
    private final PSSLContext context;
    private final PSocket socket;
    private final SSLEngine engine;

    private final MemoryBIO networkInboundBIO = new MemoryBIO();
    private final MemoryBIO networkOutboundBIO = new MemoryBIO();
    private final MemoryBIO applicationInboundBIO = new MemoryBIO();

    private boolean handshakeComplete = false;

    public PSSLSocket(Object cls, Shape instanceShape, PSSLContext context, PSocket socket, SSLEngine engine) {
        super(cls, instanceShape);
        this.context = context;
        this.socket = socket;
        this.engine = engine;
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
}
