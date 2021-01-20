package com.oracle.graal.python.builtins.objects.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

public class SSLEngineHelper {

    public static void write(Node node, PSSLSocket socket, ByteBuffer input) {
        loop(node, socket, input, ByteBuffer.allocate(0), true);
    }

    public static void read(Node node, PSSLSocket socket, ByteBuffer target) {
        loop(node, socket, ByteBuffer.allocate(0), target, false);
    }

    public static void handshake(Node node, PSSLSocket socket) {
        try {
            socket.getEngine().beginHandshake();
        } catch (SSLException e) {
            // TODO better error handling
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
        }
        loop(node, socket, ByteBuffer.allocate(0), ByteBuffer.allocate(0), true);
    }

    private static void putAsMuchAsPossible(ByteBuffer target, MemoryBIO sourceBIO) {
        ByteBuffer source = sourceBIO.getBufferForReading();
        int remaining = Math.min(source.remaining(), target.remaining());
        int oldLimit = source.limit();
        source.limit(source.position() + remaining);
        target.put(source);
        source.limit(oldLimit);
        sourceBIO.applyRead(source);
    }

    @TruffleBoundary
    private static void loop(Node node, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, boolean writing) {
        // TODO maybe we need some checks for closed connection etc here?
        MemoryBIO applicationInboundBIO = socket.getApplicationInboundBIO();
        MemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
        MemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
        if (!writing && applicationInboundBIO.getPending() > 0) {
            // Flush leftover data from previous read
            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
            // OpenSSL's SSL_read returns only the pending data
            return;
        }
        SSLEngine engine = socket.getEngine();
        ByteChannel javaSocket = null;
        PSocket pSocket = socket.getSocket();
        if (pSocket != null) {
            if (pSocket.getSocket() != null) {
                javaSocket = pSocket.getSocket();
            } else {
                // TODO what now? It could be ServerSocket
            }
        }
        // Whether we can write directly to targetBuffer
        boolean writeDirectlyToTarget = true;
        boolean hanshakeComplete = socket.isHandshakeComplete();
        int netBufferSize = engine.getSession().getPacketBufferSize();
        boolean currentlyWriting = writing;
        try {
            // Flush output that didn't get written in the last call (for non-blocking)
            emitOutput(node, networkOutboundBIO, javaSocket);
            // TODO check engine.isInboundDone/engine.isOutboundDone
            transmissionLoop: while (true) {
                SSLEngineResult result;
                if (currentlyWriting) {
                    networkOutboundBIO.ensureWriteCapacity(netBufferSize);
                    ByteBuffer writeBuffer = networkOutboundBIO.getBufferForWriting();
                    try {
                        result = engine.wrap(appInput, writeBuffer);
                    } finally {
                        networkOutboundBIO.applyWrite(writeBuffer);
                    }
                } else {
                    ByteBuffer readBuffer = networkInboundBIO.getBufferForReading();
                    try {
                        if (writeDirectlyToTarget) {
                            result = engine.unwrap(readBuffer, targetBuffer);
                        } else {
                            applicationInboundBIO.ensureWriteCapacity(engine.getSession().getApplicationBufferSize());
                            ByteBuffer writeBuffer = applicationInboundBIO.getBufferForWriting();
                            try {
                                result = engine.unwrap(readBuffer, writeBuffer);
                            } finally {
                                applicationInboundBIO.applyWrite(writeBuffer);
                            }
                            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
                        }
                    } finally {
                        networkInboundBIO.applyRead(readBuffer);
                    }
                }
                switch (result.getStatus()) {
                    case CLOSED:
                        if (writing) {
                            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_ZERO_RETURN, ErrorMessages.SSL_SESSION_CLOSED);
                        } else {
                            break transmissionLoop;
                        }
                    case BUFFER_OVERFLOW:
                        assert !currentlyWriting;
                        // We are trying to read a packet whose content doesn't fit into the
                        // output buffer. That means we need to read the whole content into a
                        // temporary buffer, then copy as much as we can into the target buffer
                        // and save the rest for the next read call
                        writeDirectlyToTarget = false;
                        continue transmissionLoop;
                    case BUFFER_UNDERFLOW:
                        obtainMoreInput(node, networkInboundBIO, javaSocket, netBufferSize);
                        continue transmissionLoop;
                    case OK:
                        emitOutput(node, networkOutboundBIO, javaSocket);
                        switch (result.getHandshakeStatus()) {
                            case NEED_TASK:
                                hanshakeComplete = false;
                                Runnable task;
                                while ((task = engine.getDelegatedTask()) != null) {
                                    task.run();
                                }
                                continue transmissionLoop;
                            case NEED_WRAP:
                                hanshakeComplete = false;
                                currentlyWriting = true;
                                continue transmissionLoop;
                            case NEED_UNWRAP:
                                hanshakeComplete = false;
                                currentlyWriting = false;
                                continue transmissionLoop;
                            case FINISHED:
                                hanshakeComplete = true;
                                currentlyWriting = writing;
                                continue transmissionLoop;
                            case NOT_HANDSHAKING:
                                assert hanshakeComplete;
                                // Read operation needs to return after a single packet of
                                // application data has been read.
                                // Write operation needs to continue until the buffer is empty
                                if (writing && appInput.hasRemaining()) {
                                    continue transmissionLoop;
                                } else {
                                    break transmissionLoop;
                                }
                        }
                        throw CompilerDirectives.shouldNotReachHere("unhandled SSL handshake status");
                }
                throw CompilerDirectives.shouldNotReachHere("unhandled SSL status");
            }
        } catch (IOException e) {
            // TODO better error handling, distinguish SSL errors and socket errors
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
        } finally {
            socket.setHandshakeComplete(hanshakeComplete);
        }
        assert !appInput.hasRemaining();
        // TODO handle other socket errors (NotYetConnected)
        // TODO handle OOM
    }

    private static void obtainMoreInput(Node node, MemoryBIO networkInboundBIO, ByteChannel javaSocket, int netBufferSize) throws IOException {
        if (javaSocket == null) {
            // MemoryBIO input
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_READ, ErrorMessages.SSL_WANT_READ);
        }
        // Network input
        networkInboundBIO.ensureWriteCapacity(netBufferSize);
        ByteBuffer writeBuffer = networkInboundBIO.getBufferForWriting();
        try {
            // TODO direct use of socket
            int readBytes = javaSocket.read(writeBuffer);
            // TODO if (readBytes < 0)
            if (readBytes == 0) {
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_READ, ErrorMessages.SSL_WANT_READ);
            }
        } finally {
            networkInboundBIO.applyWrite(writeBuffer);
        }
    }

    private static void emitOutput(Node node, MemoryBIO networkOutboundBIO, ByteChannel javaSocket) throws IOException {
        if (networkOutboundBIO.getPending() > 0) {
            if (javaSocket == null) {
                // MemoryBIO outut
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_WRITE, ErrorMessages.SSL_WANT_WRITE);
            }
            // Network output
            ByteBuffer readBuffer = networkOutboundBIO.getBufferForReading();
            try {
                // TODO direct use of socket
                int writtenBytes = javaSocket.write(readBuffer);
                if (writtenBytes == 0) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_WRITE, ErrorMessages.SSL_WANT_WRITE);
                }
            } finally {
                networkOutboundBIO.applyRead(readBuffer);
            }
        }
    }
}
