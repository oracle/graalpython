package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLZeroReturnError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class SSLEngineHelper {

    public static void write(PNodeWithRaise node, PSSLSocket socket, ByteBuffer input) {
        loop(node, socket, input, ByteBuffer.allocate(0), true);
    }

    public static void read(PNodeWithRaise node, PSSLSocket socket, ByteBuffer target) {
        loop(node, socket, ByteBuffer.allocate(0), target, false);
    }

    public static void handshake(PNodeWithRaise node, PSSLSocket socket) {
        try {
            socket.getEngine().beginHandshake();
        } catch (SSLException e) {
            // TODO better error handling
            throw node.raise(SSLError, e);
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
    private static void loop(PNodeWithRaise node, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, boolean writing) {
        // TODO maybe we need some checks for closed connection etc here?
        MemoryBIO applicationInboundBIO = socket.getApplicationInboundBIO();
        MemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
        MemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
        if (!writing && applicationInboundBIO.getPending() > 0) {
            // Try to flush leftover data from previous read
            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
            if (!targetBuffer.hasRemaining()) {
                // We read enough from the buffer, no need to do IO
                return;
            }
        }
        SSLEngine engine = socket.getEngine();
        SocketChannel javaSocket = socket.getSocket().getSocket();
        // Whether we can write directly to targetBuffer
        boolean writeDirectlyToTarget = true;
        boolean hanshakeComplete = socket.isHandshakeComplete();
        try {
            int netBufferSize = engine.getSession().getPacketBufferSize();
            boolean currentlyWriting = writing;
            boolean needsRecv = false;
            // TODO check engine.isInboundDone/engine.isOutboundDone
            while (!hanshakeComplete ||
                            (writing ? appInput.hasRemaining() : targetBuffer.hasRemaining())) {
                if (needsRecv) {
                    // Network input
                    networkInboundBIO.ensureWriteCapacity(netBufferSize);
                    ByteBuffer writeBuffer = networkInboundBIO.getBufferForWriting();
                    try {
                        // TODO direct use of socket
                        javaSocket.read(writeBuffer);
                    } finally {
                        networkInboundBIO.applyWrite(writeBuffer);
                    }
                    needsRecv = false;
                }
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
                        throw node.raise(SSLZeroReturnError, ErrorMessages.SSL_SESSION_CLOSED);
                    case BUFFER_OVERFLOW:
                        assert !currentlyWriting;
                        // We are trying to read a packet whose content doesn't fit into the
                        // output buffer. That means we need to read the whole content into a
                        // temporary buffer, then copy as much as we can into the target buffer
                        // and save the rest for the next read call
                        writeDirectlyToTarget = false;
                        break;
                    case BUFFER_UNDERFLOW:
                        needsRecv = true;
                        break;
                    case OK:
                        // Network output
                        if (networkOutboundBIO.getPending() > 0) {
                            ByteBuffer readBuffer = networkOutboundBIO.getBufferForReading();
                            try {
                                // TODO direct use of socket
                                javaSocket.write(readBuffer);
                            } finally {
                                networkOutboundBIO.applyRead(readBuffer);
                            }
                        }
                        switch (result.getHandshakeStatus()) {
                            case NEED_TASK:
                                hanshakeComplete = false;
                                Runnable task;
                                while ((task = engine.getDelegatedTask()) != null) {
                                    task.run();
                                }
                                break;
                            case NEED_WRAP:
                                hanshakeComplete = false;
                                currentlyWriting = true;
                                break;
                            case NEED_UNWRAP:
                                hanshakeComplete = false;
                                currentlyWriting = false;
                                break;
                            case FINISHED:
                                hanshakeComplete = true;
                                currentlyWriting = writing;
                                break;
                            case NOT_HANDSHAKING:
                                break;
                            default:
                                throw CompilerDirectives.shouldNotReachHere("unexpected hanshake status");
                        }
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("unexpected SSL status");
                }
            }
            targetBuffer.flip();
        } catch (IOException e) {
            // TODO better error handling, distinguish SSL errors and socket errors
            throw node.raise(SSLError, e);
        } finally {
            socket.setHandshakeComplete(hanshakeComplete);
        }
        assert !appInput.hasRemaining();
        // TODO handle other socket errors (NotYetConnected)
        // TODO handle OOM
    }
}
