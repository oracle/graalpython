package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;

import java.nio.ByteBuffer;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * This class implements equivalents of OpenSSL transport functions ({@code SSL_read} etc) on top of
 * JDK's {@link SSLEngine}. It takes care of the handshake, packeting, network and application data
 * buffering and network or memory buffer IO. In addition to what the OpenSSL functions would do, it
 * also handles Python-specific error handling, non-blocking IO and socket timeouts.
 */
public abstract class SSLOperationNode extends PNodeWithRaise {

    /**
     * Equivalent of {@code SSL_write}. Attempts to transmit all the data in the supplied buffer
     * into given {@link PSSLSocket} (which can be backed by a memory buffer).
     * <p>
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    public void write(VirtualFrame frame, PSSLSocket socket, ByteBuffer input) {
        execute(frame, socket, input, SSLOperationNode.EMPTY_BUFFER, SSLOperation.WRITE);
    }

    /**
     * Equivalent of {@code SSL_read}. Attempts to read bytes from the {@link PSSLSocket} (which can
     * be backed by a memory buffer) into given buffer. Will read at most the data of one TLS
     * packet. Decrypted but not read data is buffered on the socket and returned by the next call.
     * Empty output buffer after the call signifies the peer closed the connection cleanly.
     * <p>
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    public void read(VirtualFrame frame, PSSLSocket socket, ByteBuffer target) {
        execute(frame, socket, SSLOperationNode.EMPTY_BUFFER, target, SSLOperation.READ);
    }

    /**
     * Equivalent of {@code SSL_do_handshake}. Initiate a handshake if one has not been initiated
     * already. Becomes a no-op after the initial handshake is done, i.e. cannot be used to perform
     * renegotiation.
     * <p>
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    public void handshake(VirtualFrame frame, PSSLSocket socket) {
        if (!socket.isHandshakeComplete()) {
            try {
                beginHandshake(socket);
            } catch (SSLException e) {
                throw handleSSLException(this, e);
            }
            execute(frame, socket, SSLOperationNode.EMPTY_BUFFER, SSLOperationNode.EMPTY_BUFFER, SSLOperation.HANDSHAKE);
        }
    }

    @TruffleBoundary
    private static void beginHandshake(PSSLSocket socket) throws SSLException {
        socket.getEngine().beginHandshake();
    }

    /**
     * Equivalent of {@code SSL_do_shutdown}. Initiates a duplex close of the TLS connection - sends
     * a close_notify message and tries to receive a close_notify from the peer.
     * <p>
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    public void shutdown(VirtualFrame frame, PSSLSocket socket) {
        closeOutbound(socket);
        execute(frame, socket, SSLOperationNode.EMPTY_BUFFER, SSLOperationNode.EMPTY_BUFFER, SSLOperation.SHUTDOWN);
    }

    @TruffleBoundary
    private static void closeOutbound(PSSLSocket socket) {
        socket.getEngine().closeOutbound();
    }

    protected abstract void execute(VirtualFrame frame, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, SSLOperation operation);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int TLS_HEADER_SIZE = 5;

    protected enum SSLOperationStatus {
        WANTS_READ,
        WANTS_WRITE,
        COMPLETE
    }

    protected enum SSLOperation {
        READ,
        WRITE,
        HANDSHAKE,
        SHUTDOWN
    }

    @Specialization
    void run(VirtualFrame frame, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, SSLOperation operation,
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                    @Cached("context.getPosixSupport()") Object posixSupport,
                    @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                    @Cached GilNode gil,
                    @Cached PRaiseSSLErrorNode raiseSSLErrorNode,
                    @Cached PConstructAndRaiseNode constructAndRaiseNode) {
        // TODO common timeout
        try {
            SSLOperationStatus status;
            while (true) {
                status = loop(this, socket, appInput, targetBuffer, operation);
                switch (status) {
                    case COMPLETE:
                        return;
                    case WANTS_READ:
                        PMemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
                        if (socket.getSocket() != null) {
                            /*
                             * Network input: OpenSSL only reads as much as necessary for given
                             * packet. SSLEngine doesn't tell us how much it expects. The size
                             * returned by getPacketBufferSize() is the maximum expected size, not
                             * the actual size. CPython has some situations that rely on not reading
                             * more than the packet, notably after the SSL connection is closed by a
                             * proper closing handshake, the socket can be used for plaintext
                             * communication. If we over-read, we would read that plaintext and it
                             * would get discarded. So we try to get at least the 5 bytes for the
                             * header and then determine the packet size from the header. If the
                             * packet is not SSL, the engine should reject it as soon as it gets the
                             * header.
                             */
                            int len;
                            if (networkInboundBIO.getPending() >= TLS_HEADER_SIZE) {
                                len = TLS_HEADER_SIZE + ((networkInboundBIO.getByte(3) & 0xFF) << 8) +
                                                (networkInboundBIO.getByte(4) & 0xFF);
                            } else {
                                len = TLS_HEADER_SIZE;
                            }
                            if (networkInboundBIO.getPending() >= len) {
                                /*
                                 * The engine requested more data, but we think we already got
                                 * enough data
                                 */
                                throw raiseSSLErrorNode.raise(SSLErrorCode.ERROR_SSL, "Packet size mismatch");
                            }
                            int toRead = len - networkInboundBIO.getPending();
                            networkInboundBIO.ensureWriteCapacity(toRead);
                            // TODO avoid copying
                            byte[] bytes = new byte[toRead];
                            try {
                                // TODO flags?
                                int readBytes = SocketUtils.callSocketFunctionWithRetry(this, posixLib, posixSupport, gil, socket.getSocket(),
                                                () -> posixLib.recv(posixSupport, socket.getSocket().getFd(), bytes, bytes.length, 0),
                                                true, false, socket.getSocket().getTimeoutNs());
                                networkInboundBIO.write(bytes, readBytes);
                            } catch (PosixException e) {
                                // TODO EAGAIN/EWOULDBLOCK
                                throw constructAndRaiseNode.raiseOSError(frame, e.getErrorCode(), e.getMessage(), null, null);
                            }
                        } else if (networkInboundBIO.didWriteEOF()) {
                            /*
                             * MemoryBIO output with signalled EOF. Note this checks didWriteEOF and
                             * not isEOF - the fact that we're here means that we consumed as much
                             * data as possible to form a TLS packet, but that doesn't have to be
                             * all the data in the BIO
                             */
                            if (socket.hasSavedException()) {
                                throw socket.getAndClearSavedException();
                            }
                            throw raiseSSLErrorNode.raise(SSLErrorCode.ERROR_EOF, ErrorMessages.SSL_ERROR_EOF);
                        } else {
                            /*
                             * MemoryBIO input - we already read as much as we could. Signal to the
                             * caller that we need more.
                             */
                            throw raiseSSLErrorNode.raise(SSLErrorCode.ERROR_WANT_READ, ErrorMessages.SSL_WANT_READ);
                        }
                        break;
                    case WANTS_WRITE:
                        if (socket.getSocket() != null) {
                            PMemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
                            // TODO avoid copying
                            byte[] bytes = PythonUtils.arrayCopyOfRange(networkOutboundBIO.getInternalBytes(), networkOutboundBIO.getReadPosition(), networkOutboundBIO.getPending());
                            try {
                                // TODO flags?
                                int writtenBytes = SocketUtils.callSocketFunctionWithRetry(this, posixLib, posixSupport, gil, socket.getSocket(),
                                                () -> posixLib.send(posixSupport, socket.getSocket().getFd(), bytes, bytes.length, 0),
                                                true, false, socket.getSocket().getTimeoutNs());
                                networkOutboundBIO.advanceReadPosition(writtenBytes);
                            } catch (PosixException e) {
                                // TODO EAGAIN/EWOULDBLOCK
                                throw constructAndRaiseNode.raiseOSError(frame, e.getErrorCode(), e.getMessage(), null, null);
                            }
                        } else {
                            throw CompilerDirectives.shouldNotReachHere("MemoryBIO-based socket operation returned WANTS_WRITE");
                        }
                        break;
                }
                PythonContext.triggerAsyncActions(this);
            }
        } catch (SSLException e) {
            throw handleSSLException(this, e);
        } catch (OverflowException | OutOfMemoryError node) {
            throw raise(MemoryError);
        }
    }

    private static void putAsMuchAsPossible(ByteBuffer target, PMemoryBIO sourceBIO) {
        ByteBuffer source = sourceBIO.getBufferForReading();
        int remaining = Math.min(source.remaining(), target.remaining());
        int oldLimit = source.limit();
        source.limit(source.position() + remaining);
        target.put(source);
        source.limit(oldLimit);
        sourceBIO.applyRead(source);
    }

    @TruffleBoundary
    private static SSLOperationStatus loop(PNodeWithRaise node, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, SSLOperation op) throws SSLException, OverflowException {
        PMemoryBIO applicationInboundBIO = socket.getApplicationInboundBIO();
        PMemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
        PMemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
        if (op == SSLOperation.READ && applicationInboundBIO.getPending() > 0) {
            // Flush leftover data from previous read
            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
            // OpenSSL's SSL_read returns only the pending data
            return SSLOperationStatus.COMPLETE;
        }
        SSLEngine engine = socket.getEngine();
        PSocket pSocket = socket.getSocket();
        // Whether we can write directly to targetBuffer
        boolean writeDirectlyToTarget = true;
        boolean currentlyWrapping;
        boolean didReadApplicationData = false;
        SSLEngineResult.HandshakeStatus lastStatus;
        // Flush output that didn't get written in the last call (for non-blocking)
        if (pSocket != null && networkOutboundBIO.getPending() > 0) {
            return SSLOperationStatus.WANTS_WRITE;
        }
        transmissionLoop: while (true) {
            // If the handshake is not complete, do the operations that it requests
            // until it completes. This can happen in different situations:
            // * Initial handshake
            // * Renegotiation handshake, which can occur at any point in the communication
            // * Closing handshake. Can be initiated by us (#shutdown), the peer or when an
            // exception occurred
            lastStatus = engine.getHandshakeStatus();
            switch (lastStatus) {
                case NEED_TASK:
                    socket.setHandshakeComplete(false);
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    // Get the next step
                    continue transmissionLoop;
                case NEED_WRAP:
                    socket.setHandshakeComplete(false);
                    currentlyWrapping = true;
                    break;
                case NEED_UNWRAP:
                    socket.setHandshakeComplete(false);
                    currentlyWrapping = false;
                    break;
                case NOT_HANDSHAKING:
                    currentlyWrapping = op != SSLOperation.READ;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("Unhandled SSL handshake status");
            }
            if (engine.isOutboundDone()) {
                currentlyWrapping = false;
            }
            SSLEngineResult result;
            try {
                if (currentlyWrapping) {
                    result = doWrap(engine, appInput, networkOutboundBIO, engine.getSession().getPacketBufferSize());
                } else {
                    result = doUnwrap(engine, networkInboundBIO, targetBuffer, applicationInboundBIO, writeDirectlyToTarget);
                    didReadApplicationData = result.bytesProduced() > 0;
                }
            } catch (SSLException e) {
                // If a SSL exception occurs, we need to attempt to perform the closing
                // handshake in order to let the peer know what went wrong. We raise the
                // exception only after the handshake is done or if we get an exception the
                // second time
                if (socket.hasSavedException()) {
                    // We already got an exception in the previous iteration/call. Let's give up
                    // on the closing handshake to avoid going into an infinite loop
                    throw socket.getAndClearSavedException();
                }
                socket.setException(e);
                engine.closeOutbound();
                continue transmissionLoop;
            }
            if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED && !engine.isOutboundDone()) {
                socket.setHandshakeComplete(true);
            }
            // Send the network output to socket, if any. If the output is a MemoryBIO, the
            // output is already in it at this point
            if (pSocket != null && networkOutboundBIO.getPending() > 0) {
                return SSLOperationStatus.WANTS_WRITE;
            }
            // Handle possible closure
            if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                engine.closeOutbound();
            }
            if (engine.isOutboundDone() && engine.isInboundDone()) {
                // Closure handshake is done, we can handle the exit conditions now
                if (socket.hasSavedException()) {
                    throw socket.getAndClearSavedException();
                }
                switch (op) {
                    case READ:
                        // Read operation should just return the current output. If it's
                        // empty, the application will interpret is as EOF, which is what is
                        // expected
                        break transmissionLoop;
                    case SHUTDOWN:
                        // Shutdown is considered done at this point
                        break transmissionLoop;
                    case WRITE:
                    case HANDSHAKE:
                        // Write and handshake operations need to fail loudly
                        throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_ZERO_RETURN, ErrorMessages.SSL_SESSION_CLOSED);
                }
                throw CompilerDirectives.shouldNotReachHere();
            }
            // Try extra hard to converge on the status before doing potentially blocking
            // operations
            if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING && engine.getHandshakeStatus() != lastStatus) {
                continue transmissionLoop;
            }
            // Decide if we need to obtain more data or change the buffer
            switch (result.getStatus()) {
                case BUFFER_OVERFLOW:
                    if (currentlyWrapping) {
                        throw CompilerDirectives.shouldNotReachHere("Unexpected overflow of network buffer");
                    }
                    // We are trying to read a packet whose content doesn't fit into the
                    // output buffer. That means we need to read the whole content into a
                    // temporary buffer, then copy as much as we can into the target buffer
                    // and save the rest for the next read call
                    writeDirectlyToTarget = false;
                    continue transmissionLoop;
                case BUFFER_UNDERFLOW:
                    // We need to obtain more input from the socket or MemoryBIO
                    return SSLOperationStatus.WANTS_READ;
            }
            // Continue handshaking until done
            if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                continue transmissionLoop;
            }
            switch (result.getStatus()) {
                case OK:
                    // At this point, the handshake is complete and the session is not closed.
                    // Decide what to do about the actual application-level operation
                    switch (op) {
                        case READ:
                            // Read operation needs to return after a single packet of
                            // application data has been read
                            if (didReadApplicationData) {
                                break transmissionLoop;
                            }
                            continue transmissionLoop;
                        case HANDSHAKE:
                            // Handshake is done at this point
                            break transmissionLoop;
                        case WRITE:
                            // Write operation needs to continue until the buffer is empty
                            if (appInput.hasRemaining()) {
                                continue transmissionLoop;
                            }
                            break transmissionLoop;
                        case SHUTDOWN:
                            // Continue the closing handshake
                            continue transmissionLoop;
                    }
                    throw CompilerDirectives.shouldNotReachHere();
                case CLOSED:
                    // SSLEngine says there's no handshake, but there could still be a response
                    // to our close waiting to be read
                    continue transmissionLoop;
            }
            throw CompilerDirectives.shouldNotReachHere("Unhandled SSL engine status");
        }
        // The loop exit - the operation finished (doesn't need more input)
        if (socket.hasSavedException()) {
            // We encountered an error during the communication and we temporarily suppressed it
            // to perform the closing handshake. Now we should process it
            throw socket.getAndClearSavedException();
        }
        assert !appInput.hasRemaining();
        // The operation finished successfully at this point
        return SSLOperationStatus.COMPLETE;
    }

    private static SSLEngineResult doUnwrap(SSLEngine engine, PMemoryBIO networkInboundBIO, ByteBuffer targetBuffer, PMemoryBIO applicationInboundBIO, boolean writeDirectlyToTarget)
                    throws SSLException, OverflowException {
        ByteBuffer readBuffer = networkInboundBIO.getBufferForReading();
        try {
            if (writeDirectlyToTarget) {
                return engine.unwrap(readBuffer, targetBuffer);
            } else {
                applicationInboundBIO.ensureWriteCapacity(engine.getSession().getApplicationBufferSize());
                ByteBuffer writeBuffer = applicationInboundBIO.getBufferForWriting();
                try {
                    return engine.unwrap(readBuffer, writeBuffer);
                } finally {
                    applicationInboundBIO.applyWrite(writeBuffer);
                    putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
                }
            }
        } finally {
            networkInboundBIO.applyRead(readBuffer);
        }
    }

    private static SSLEngineResult doWrap(SSLEngine engine, ByteBuffer appInput, PMemoryBIO networkOutboundBIO, int netBufferSize) throws SSLException, OverflowException {
        networkOutboundBIO.ensureWriteCapacity(netBufferSize);
        ByteBuffer writeBuffer = networkOutboundBIO.getBufferForWriting();
        try {
            return engine.wrap(appInput, writeBuffer);
        } finally {
            networkOutboundBIO.applyWrite(writeBuffer);
        }
    }

    @TruffleBoundary
    private static PException handleSSLException(Node node, SSLException e) {
        if (e.getCause() instanceof CertificateException) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_CERT_VERIFICATION, ErrorMessages.CERTIFICATE_VERIFY_FAILED, e.toString());
        }
        throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
    }
}
