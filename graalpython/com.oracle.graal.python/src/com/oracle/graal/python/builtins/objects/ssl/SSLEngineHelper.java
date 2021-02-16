package com.oracle.graal.python.builtins.objects.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.CertPathBuilderException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils.TimeoutHelper;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class SSLEngineHelper {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    @TruffleBoundary
    public static void write(PNodeWithRaise node, PSSLSocket socket, ByteBuffer input) {
        loop(node, socket, input, EMPTY_BUFFER, Operation.WRITE);
    }

    @TruffleBoundary
    public static void read(PNodeWithRaise node, PSSLSocket socket, ByteBuffer target) {
        loop(node, socket, EMPTY_BUFFER, target, Operation.READ);
    }

    @TruffleBoundary
    public static void handshake(PNodeWithRaise node, PSSLSocket socket) {
        if (!socket.isHandshakeComplete()) {
            try {
                socket.getEngine().beginHandshake();
            } catch (SSLException e) {
                // TODO better error handling
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
            }
            loop(node, socket, EMPTY_BUFFER, EMPTY_BUFFER, Operation.HANDSHAKE);
        }
    }

    @TruffleBoundary
    public static void shutdown(PNodeWithRaise node, PSSLSocket socket) {
        socket.getEngine().closeOutbound();
        loop(node, socket, EMPTY_BUFFER, EMPTY_BUFFER, Operation.SHUTDOWN);
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

    private enum Operation {
        READ,
        WRITE,
        HANDSHAKE,
        SHUTDOWN
    }

    private static void loop(PNodeWithRaise node, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, Operation op) {
        // TODO maybe we need some checks for closed connection etc here?
        MemoryBIO applicationInboundBIO = socket.getApplicationInboundBIO();
        MemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
        MemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
        if (op == Operation.READ && applicationInboundBIO.getPending() > 0) {
            // Flush leftover data from previous read
            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
            // OpenSSL's SSL_read returns only the pending data
            return;
        }
        SSLEngine engine = socket.getEngine();
        PSocket pSocket = socket.getSocket();
        TimeoutHelper timeoutHelper = null;
        if (pSocket != null) {
            long timeoutMillis = pSocket.getTimeoutInMilliseconds();
            if (timeoutMillis > 0) {
                timeoutHelper = new TimeoutHelper(timeoutMillis);
            }
        }
        // Whether we can write directly to targetBuffer
        boolean writeDirectlyToTarget = true;
        boolean hanshakeComplete = socket.isHandshakeComplete();
        boolean currentlyWrapping = op != Operation.READ;
        try {
            // Flush output that didn't get written in the last call (for non-blocking)
            emitOutput(node, networkOutboundBIO, pSocket, timeoutHelper);
            transmissionLoop: while (true) {
                SSLEngineResult result;
                int netBufferSize = engine.getSession().getPacketBufferSize();
                if (currentlyWrapping) {
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
                // Send the network output to socket, if any. If the output is a MemoryBIO, the
                // output is already in it at this point
                emitOutput(node, networkOutboundBIO, pSocket, timeoutHelper);
                // Decide what we're going to do next
                switch (result.getStatus()) {
                    case CLOSED:
                        switch (op) {
                            case READ:
                                // Read operation should just return empty output signifying EOF.
                                break transmissionLoop;
                            case WRITE:
                            case HANDSHAKE:
                                // Write and handshake operations need to fail loudly
                                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_ZERO_RETURN, ErrorMessages.SSL_SESSION_CLOSED);
                            case SHUTDOWN:
                                if (engine.isInboundDone()) {
                                    // Closing hanshake complete
                                    break transmissionLoop;
                                } else {
                                    // Closing handshake needs to unwrap
                                    currentlyWrapping = false;
                                    continue transmissionLoop;
                                }
                        }
                        throw CompilerDirectives.shouldNotReachHere();
                    case BUFFER_OVERFLOW:
                        assert !currentlyWrapping;
                        // We are trying to read a packet whose content doesn't fit into the
                        // output buffer. That means we need to read the whole content into a
                        // temporary buffer, then copy as much as we can into the target buffer
                        // and save the rest for the next read call
                        writeDirectlyToTarget = false;
                        continue transmissionLoop;
                    case BUFFER_UNDERFLOW:
                        // We need to obtain more input from the socket. This can raise
                        // SSLWantReadError if the socket is non-blocking and the operation would
                        // block. If the input is a MemoryBIO, this will raise SSLWantReadError
                        // because if we reached this point, it means the buffer doesn't have enough
                        // data.
                        obtainMoreInput(node, networkInboundBIO, pSocket, netBufferSize, timeoutHelper);
                        continue transmissionLoop;
                    case OK:
                        // If the handshake is not complete, do the operations that it requests
                        // until it completes.
                        // Note that TLS supports renegotiation - the peer can request a new
                        // handhake at any time, so we need to check this every time and not just in
                        // the beginning.
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
                                currentlyWrapping = true;
                                continue transmissionLoop;
                            case NEED_UNWRAP:
                                hanshakeComplete = false;
                                currentlyWrapping = false;
                                continue transmissionLoop;
                            case FINISHED:
                                hanshakeComplete = true;
                                currentlyWrapping = op != Operation.READ;
                                continue transmissionLoop;
                            case NOT_HANDSHAKING:
                                assert hanshakeComplete;
                                // Read operation needs to return after a single packet of
                                // application data has been read.
                                // Write operation needs to continue until the buffer is empty.
                                // Shutdown operation needs to continue until the closing handshake
                                // is complete.
                                if ((op == Operation.WRITE && appInput.hasRemaining()) || op == Operation.SHUTDOWN) {
                                    continue transmissionLoop;
                                } else {
                                    break transmissionLoop;
                                }
                        }
                        throw CompilerDirectives.shouldNotReachHere("unhandled SSL handshake status");
                }
                throw CompilerDirectives.shouldNotReachHere("unhandled SSL status");
            }
        } catch (SSLException e) {
            try {
                // Attempt to perform the closing handshake. If we would just close the socket, the
                // peer would have no idea what went wrong. This gives the engine a chance to
                // communicate the error to the peer.
                shutdown(node, socket);
            } catch (PException e1) {
                // We tried to close cleanly and failed. No big deal.
            }
            Throwable c = e.getCause();
            Throwable cc = null;
            if (c != null) {
                cc = c.getCause();
            }
            if (cc instanceof CertPathBuilderException) {
                // TODO: where else can this be "hidden"?
                // ... cc instanceof CertificateException || c instanceof CertificateException ?
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_CERT_VERIFICATION, ErrorMessages.CERTIFICATE_VERIFY_FAILED, e.toString());
            }
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
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

    private static void obtainMoreInput(PNodeWithRaise node, MemoryBIO networkInboundBIO, PSocket socket, int netBufferSize, TimeoutHelper timeoutHelper) throws IOException {
        if (socket != null) {
            // Network input
            networkInboundBIO.ensureWriteCapacity(netBufferSize);
            ByteBuffer writeBuffer = networkInboundBIO.getBufferForWriting();
            try {
                int readBytes = SocketUtils.recv(node, socket, writeBuffer, timeoutHelper == null ? 0 : timeoutHelper.checkAndGetRemainingTimeout(node));
                if (readBytes > 0) {
                    return;
                } else if (readBytes < 0) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_EOF, ErrorMessages.SSL_ERROR_EOF);
                }
                // fallthrough to the raise
            } finally {
                networkInboundBIO.applyWrite(writeBuffer);
            }
        } else if (networkInboundBIO.didWriteEOF()) {
            // Note this checks didWriteEOF and not isEOF - the fact that we're here means that we
            // consumed as much data as possible to form a TLS packet, but that doesn't have to be
            // all the data in the BIO
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_EOF, ErrorMessages.SSL_ERROR_EOF);
        }
        throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_READ, ErrorMessages.SSL_WANT_READ);
    }

    private static void emitOutput(PNodeWithRaise node, MemoryBIO networkOutboundBIO, PSocket socket, TimeoutHelper timeoutHelper) throws IOException {
        if (socket != null && networkOutboundBIO.getPending() > 0) {
            // Network output
            ByteBuffer readBuffer = networkOutboundBIO.getBufferForReading();
            try {
                int writtenBytes = SocketUtils.send(node, socket, readBuffer, timeoutHelper == null ? 0 : timeoutHelper.checkAndGetRemainingTimeout(node));
                if (writtenBytes == 0) {
                    throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_WRITE, ErrorMessages.SSL_WANT_WRITE);
                }
            } finally {
                networkOutboundBIO.applyRead(readBuffer);
            }
        }
    }
}
