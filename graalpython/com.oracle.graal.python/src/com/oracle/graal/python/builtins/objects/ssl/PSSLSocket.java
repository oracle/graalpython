/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PSSLSocket extends PythonBuiltinObject {
    private final PSSLContext context;
    // May be null for SSLSocket backed by MemoryBIO
    private final PSocket socket;
    private final SSLEngine engine;
    private Object owner;
    private TruffleString serverHostname;

    private final PMemoryBIO networkInboundBIO;
    private final PMemoryBIO networkOutboundBIO;
    private final PMemoryBIO applicationInboundBIO;
    // The connection needs to attempt the closing handshake before throwing the exception, so we
    // need to store it
    private SSLException exception;

    private boolean handshakeComplete = false;

    public PSSLSocket(Object cls, Shape instanceShape, PSSLContext context, SSLEngine engine, PSocket socket, PMemoryBIO networkInboundBIO, PMemoryBIO networkOutboundBIO,
                    PMemoryBIO applicationInboundBIO) {
        super(cls, instanceShape);
        this.context = context;
        this.engine = engine;
        this.socket = socket;
        this.networkInboundBIO = networkInboundBIO;
        this.networkOutboundBIO = networkOutboundBIO;
        this.applicationInboundBIO = applicationInboundBIO;
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

    public PMemoryBIO getNetworkInboundBIO() {
        return networkInboundBIO;
    }

    public PMemoryBIO getNetworkOutboundBIO() {
        return networkOutboundBIO;
    }

    public PMemoryBIO getApplicationInboundBIO() {
        return applicationInboundBIO;
    }

    public Object getOwner() {
        return owner;
    }

    public void setOwner(Object owner) {
        this.owner = owner;
    }

    public TruffleString getServerHostname() {
        return serverHostname;
    }

    public void setServerHostname(TruffleString serverHostname) {
        this.serverHostname = serverHostname;
    }

    public boolean hasSavedException() {
        return exception != null;
    }

    public SSLException getAndClearSavedException() {
        SSLException savedException = this.exception;
        this.exception = null;
        return savedException;
    }

    public void setException(SSLException exception) {
        this.exception = exception;
    }
}
