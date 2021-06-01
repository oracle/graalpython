/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SocketTimeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class SocketUtils {
    @TruffleBoundary
    public static void setBlocking(PSocket socket, boolean blocking) throws IOException {
        if (socket.getSocket() != null) {
            socket.getSocket().configureBlocking(blocking);
        }

        if (socket.getServerSocket() != null) {
            socket.getServerSocket().configureBlocking(blocking);
        }
    }

    public static int recv(PNodeWithRaise node, PSocket socket, ByteBuffer target) throws IOException {
        return recv(node, socket, target, socket.getTimeout());
    }

    @TruffleBoundary
    public static int recv(PNodeWithRaise node, PSocket socket, ByteBuffer target, long timeoutMilliseconds) throws IOException {
        SocketChannel nativeSocket = socket.getSocket();
        handleTimeout(node, nativeSocket, SelectionKey.OP_READ, timeoutMilliseconds);
        int length = nativeSocket.read(target);
        if (length < 0) {
            return 0; // EOF, but Python expects 0-bytes
        } else {
            return length;
        }
    }

    public static int send(PNodeWithRaise node, PSocket socket, ByteBuffer source) throws IOException {
        return send(node, socket, source, socket.getTimeout());
    }

    @TruffleBoundary
    public static int send(PNodeWithRaise node, PSocket socket, ByteBuffer source, long timeoutMilliseconds) throws IOException {
        SocketChannel nativeSocket = socket.getSocket();
        handleTimeout(node, nativeSocket, SelectionKey.OP_WRITE, timeoutMilliseconds);
        return nativeSocket.write(source);
    }

    public static SocketChannel accept(PNodeWithRaise node, PSocket socket) throws IOException {
        return accept(node, socket, socket.getTimeout());
    }

    @TruffleBoundary
    public static SocketChannel accept(PNodeWithRaise node, PSocket socket, long timeoutMillisedonds) throws IOException {
        ServerSocketChannel nativeSocket = socket.getServerSocket();
        handleTimeout(node, nativeSocket, SelectionKey.OP_ACCEPT, timeoutMillisedonds);
        return nativeSocket.accept();
    }

    private static void handleTimeout(PNodeWithRaise node, SelectableChannel nativeSocket, int op, long timeoutMilliseconds) throws IOException {
        if (!nativeSocket.isBlocking() && timeoutMilliseconds > 0) {
            try (Selector selector = Selector.open()) {
                SelectionKey key = nativeSocket.register(selector, op);
                selector.select(timeoutMilliseconds);
                if ((key.readyOps() & op) == 0) {
                    throw node.raise(SocketTimeout, ErrorMessages.TIMED_OUT);
                }
            }
        }
    }

    public static class TimeoutHelper {
        long startNano;
        long initialTimeoutMillis;

        public TimeoutHelper(long initialTimeoutMillis) {
            this.initialTimeoutMillis = initialTimeoutMillis;
        }

        public long checkAndGetRemainingTimeout(PNodeWithRaise node) {
            if (startNano == 0) {
                startNano = System.nanoTime();
                return initialTimeoutMillis;
            } else {
                long remainingMillis = initialTimeoutMillis - (System.nanoTime() - startNano) / 1000000;
                if (remainingMillis <= 0) {
                    throw node.raise(SocketTimeout, ErrorMessages.TIMED_OUT);
                }
                return remainingMillis;
            }
        }
    }
}
