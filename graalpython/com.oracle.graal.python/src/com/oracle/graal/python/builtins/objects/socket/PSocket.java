/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class PSocket extends PythonBuiltinObject implements Channel {
    public static final int AF_UNSPEC = 0;
    public static final int AF_INET = 2;
    public static final int AF_INET6 = 23;

    public static final int SOCK_DGRAM = 1;
    public static final int SOCK_STREAM = 2;

    public static final int AI_PASSIVE = 1;
    public static final int AI_CANONNAME = 2;
    public static final int AI_NUMERICHOST = 4;

    public static final int AI_ALL = 256;
    public static final int AI_V4MAPPED_CFG = 512;
    public static final int AI_ADDRCONFIG = 1024;
    public static final int AI_V4MAPPED = 2048;

    public static final int AI_MASK = (AI_PASSIVE | AI_CANONNAME | AI_NUMERICHOST);

    public static final int AI_DEFAULT = (AI_V4MAPPED_CFG | AI_ADDRCONFIG);

    public static final int NI_NOFQDN = 1;
    public static final int NI_NUMERICHOST = 2;
    public static final int NI_NAMEREQD = 4;
    public static final int NI_NUMERICSERV = 8;
    public static final int NI_DGRAM = 10;

    public static final int IPPROTO_TCP = 6;

    private static final InetSocketAddress EPHEMERAL_ADDRESS = new InetSocketAddress(0);

    private final int family;
    private final int type;
    private final int proto;

    private int fileno;

    public int serverPort;
    public String serverHost;

    private double timeout;

    private InetSocketAddress address = EPHEMERAL_ADDRESS;

    private SocketChannel socket;

    private ServerSocketChannel serverSocket;
    private boolean blocking;

    private HashMap<Object, Object> options;

    public PSocket(LazyPythonClass cls, int family, int type, int proto) {
        super(cls);
        this.family = family;
        this.proto = proto;
        this.type = type;
        this.fileno = -1;
    }

    public PSocket(LazyPythonClass cls, int family, int type, int proto, int fileno) {
        super(cls);
        this.fileno = fileno;
        this.family = family;
        this.proto = proto;
        this.type = type;
    }

    public int getFamily() {
        return family;
    }

    public int getType() {
        return type;
    }

    public int getProto() {
        return proto;
    }

    public int getFileno() {
        return fileno;
    }

    public void setFileno(int fileno) {
        this.fileno = fileno;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public ServerSocketChannel getServerSocket() {
        return serverSocket;
    }

    public SocketChannel getSocket() {
        return socket;
    }

    public void setServerSocket(ServerSocketChannel serverSocket) {
        if (this.getSocket() != null) {
            throw new Error();
        }
        this.serverSocket = serverSocket;
    }

    public void setSocket(SocketChannel socket) {
        if (this.getServerSocket() != null) {
            throw new Error();
        }
        this.socket = socket;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    @TruffleBoundary
    public boolean isOpen() {
        return (getSocket() != null && getSocket().isOpen()) || (getServerSocket() != null && getServerSocket().isOpen());
    }

    @TruffleBoundary
    public void close() throws IOException {
        if (getSocket() != null) {
            getSocket().close();
        } else if (getServerSocket() != null) {
            getServerSocket().close();
        }
    }

    @TruffleBoundary
    public void setSockOpt(Object option, Object value) {
        if (options == null) {
            options = new HashMap<>();
        }
        options.put(option, value);
    }

    @TruffleBoundary
    public Object getSockOpt(Object option) {
        if (options != null) {
            return options.getOrDefault(option, PNone.NONE);
        }
        return PNone.NONE;
    }
}
