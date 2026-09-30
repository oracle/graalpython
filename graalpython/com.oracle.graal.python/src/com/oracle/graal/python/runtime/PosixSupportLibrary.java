/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNIX;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFBLK;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFCHR;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFDIR;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFIFO;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFLNK;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFMT;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFREG;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Internal abstraction layer for POSIX functionality. Instance of the implementation is stored in
 * the context. Use {@link PythonContext#getPosixSupport()} to access it.
 */
@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class PosixSupportLibrary extends Library {

    /** Retained for the generated library until the remaining exports are removed. */
    public abstract TruffleString getBackend(Object receiver);

    public static final char POSIX_FILENAME_SEPARATOR = '/';

    // Constants for accessing the fields of the fstat result:
    // TODO: have these in posix.c (maybe posix.h) and extract them along with other constants
    public static final int ST_SIZE = 6;

    /**
     * Equivalent to struct r_usage with the fields expected on macOS and Linux.
     *
     * @param ru_utime time in user mode (fractional seconds)
     * @param ru_stime time in system mode (fractional seconds)
     * @param ru_maxrss reserved memory size
     * @param ru_ixrss shared memory size
     * @param ru_idrss unshared memory size
     * @param ru_isrss unshared stack size
     * @param ru_minflt page faults not requiring I/O
     * @param ru_majflt page faults requiring I/O
     * @param ru_nswap number of swap outs
     * @param ru_inblock block input operations
     * @param ru_oublock block output operations
     * @param ru_msgsnd messages sent
     * @param ru_msgrcv messages received
     * @param ru_nsignals signals received
     * @param ru_nvcsw voluntary context switches
     * @param ru_nivcsw involuntary context switches
     */
    public record RusageResult(double ru_utime, double ru_stime,
                    long ru_maxrss, long ru_ixrss, long ru_idrss, long ru_isrss,
                    long ru_minflt, long ru_majflt, long ru_nswap, long ru_inblock, long ru_oublock,
                    long ru_msgsnd, long ru_msgrcv, long ru_nsignals, long ru_nvcsw, long ru_nivcsw) {
    }

    public record OpenPtyResult(int masterFd, int slaveFd) {
    }

    public static final class PwdResult {
        public final TruffleString name;
        /**
         * This value represents unsigned 64 bit integer.
         */
        public final long uid;
        /**
         * This value represents unsigned 64 bit integer.
         */
        public final long gid;
        public final TruffleString dir;
        public final TruffleString shell;

        public PwdResult(TruffleString name, long uid, long gid, TruffleString dir, TruffleString shell) {
            this.name = name;
            this.uid = uid;
            this.gid = gid;
            this.dir = dir;
            this.shell = shell;
        }

        @Override
        public String toString() {
            return "PwdResult{name='" + name + '\'' +
                            ", uid=" + uid +
                            ", gid=" + gid +
                            ", dir='" + dir + '\'' +
                            ", shell='" + shell + "'}";
        }
    }

    // region Socket addresses

    /**
     * Base class for addresses specific to a particular socket family.
     *
     * The subclasses are simple POJOs whose definitions are common to all backends. They need to be
     * converted to {@code UniversalSockAddr} before use.
     */
    public abstract static class FamilySpecificSockAddr {
        private final int family;

        protected FamilySpecificSockAddr(int family) {
            this.family = family;
        }

        public int getFamily() {
            return family;
        }
    }

    /**
     * A tagged union of all address types which is capable of holding an address of any socket
     * family.
     *
     * An universal socket address keeps the value in a representation used internally by the given
     * backend, therefore implementations of this interface are backend-specific (unlike
     * {@link FamilySpecificSockAddr} subclasses). This interface roughly corresponds to POSIX
     * {@code struct sockaddr_storage}.
     *
     */
    public interface UniversalSockAddr {
        int getFamily();

        Inet4SockAddr asInet4SockAddr();

        Inet6SockAddr asInet6SockAddr();

        UnixSockAddr asUnixSockAddr();
    }

    /**
     * Represents an address for IPv4 sockets (the {@link PosixConstants#AF_INET} socket family).
     *
     * This is a higher level equivalent of POSIX {@code struct sockaddr_in} - integer values are
     * kept in host byte order, conversion to network order ({@code htons/htonl}) is done
     * automatically by the backend. This makes the integer representation of address compatible
     * with the {@code INADDR_xxx} constants. On the other hand, addresses represented as byte
     * arrays are in network order to make them compatible with {@code inet_pton} and
     * {@code inet_ntop}).
     */
    @ValueType
    public static final class Inet4SockAddr extends FamilySpecificSockAddr {
        private final int port;           // host order, 0 - 65535
        private final int address;        // host order, e.g. INADDR_LOOPBACK

        public Inet4SockAddr(int port, int address) {
            super(AF_INET.value);
            assert port >= 0 && port <= 65535;
            this.port = port;
            this.address = address;
        }

        public Inet4SockAddr(int port, byte[] address) {
            this(port, bytesToInt(address));
        }

        public int getPort() {
            return port;
        }

        public int getAddress() {
            return address;
        }

        public byte[] getAddressAsBytes() {
            return intToBytes(address);
        }

        private static int bytesToInt(byte[] src) {
            assert src != null && src.length >= 4;
            return ByteArraySupport.bigEndian().getInt(src, 0);
        }

        private static byte[] intToBytes(int src) {
            byte[] dst = new byte[4];
            ByteArraySupport.bigEndian().putInt(dst, 0, src);
            return dst;
        }
    }

    /**
     * Represents an address for IPv6 sockets (the {@link PosixConstants#AF_INET6} socket family).
     *
     * This is a higher level equivalent of POSIX {@code struct sockaddr_in6} - the values are kept
     * in host byte order, conversion to network order ({@code htons/htonl}) is done automatically
     * by the backend.
     */
    @ValueType
    public static final class Inet6SockAddr extends FamilySpecificSockAddr {
        private final int port;           // host order, 0 - 65535
        private final byte[] address = new byte[16];
        private final int flowInfo;       // host order, 0 - 2^20-1
        private final int scopeId;        // host order, interpreted as unsigned

        public Inet6SockAddr(int port, byte[] address, int flowInfo, int scopeId) {
            super(AF_INET6.value);
            assert port >= 0 && port <= 65535;
            assert address != null && address.length == 16;
            assert flowInfo >= 0 && flowInfo <= 1048575;
            this.port = port;
            PythonUtils.arraycopy(address, 0, this.address, 0, 16);
            this.flowInfo = flowInfo;
            this.scopeId = scopeId;
        }

        public int getPort() {
            return port;
        }

        public byte[] getAddress() {
            return Arrays.copyOf(address, 16);
        }

        public int getFlowInfo() {
            return flowInfo;
        }

        public int getScopeId() {
            return scopeId;
        }
    }

    /**
     * Represents an address for UNIX domain sockets (the {@link PosixConstants#AF_UNIX} socket
     * family).
     *
     * This is a higher level equivalent of POSIX {@code struct sockaddr_un}, see
     * {@code man -7 unix}. It is the responsibility of the user to ensure that pathname addresses
     * are zero terminated and abstract addresses start with a zero.
     */
    @ValueType
    public static final class UnixSockAddr extends FamilySpecificSockAddr {
        private final byte[] path;

        public UnixSockAddr(byte[] path) {
            super(AF_UNIX.value);
            this.path = path;
        }

        /**
         * Returns the path, which:
         * <ul>
         * <li>for unnamed addresses is of length 0,</li>
         * <li>for pathname addresses contains the terminating zero,</li>
         * <li>for abstract addresses start with a zero,</li>
         * <li>should not be modified by the caller.</li>
         * </ul>
         */
        public byte[] getPath() {
            return path;
        }
    }

    // endregion

    public static final class AcceptResult {
        public final int socketFd;
        public final UniversalSockAddr sockAddr;

        public AcceptResult(int socketFd, UniversalSockAddr sockAddr) {
            this.socketFd = socketFd;
            this.sockAddr = sockAddr;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "RecvfromResult{" + "socketFd=" + socketFd + ", sockAddr=" + sockAddr + '}';
        }
    }

    public static final class RecvfromResult {
        public final int readBytes;
        public final UniversalSockAddr sockAddr;

        public RecvfromResult(int readBytes, UniversalSockAddr sockAddr) {
            this.readBytes = readBytes;
            this.sockAddr = sockAddr;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "RecvfromResult{" + "readBytes=" + readBytes + ", sockAddr=" + sockAddr + '}';
        }
    }

    // region Name resolution messages

    /**
     * Represents one or more addrinfos returned by {@code getaddrinfo()}.
     *
     * Must be explicitly released using {@link #release()}.
     * Behaves like a cursor which points to a {@code struct addrinfo} structure (initially pointing
     * at the first address info). The cursor can only move forward using the
     * {@link #next()} method.
     */
    public interface AddrInfoCursor {
        void release();

        boolean next();

        int getFlags();

        int getFamily();

        int getSockType();

        int getProtocol();

        Object getCanonName();

        UniversalSockAddr getSockAddr();
    }

    /**
     * Exception that indicates and error while executing
     * {@link PosixSupport#getaddrinfo(Object, Object, int, int, int, int)}.
     */
    public static final class GetAddrInfoException extends Exception {

        private static final long serialVersionUID = 3013253817849329391L;

        private final int errorCode;
        private final transient TruffleString msg;

        public GetAddrInfoException(int errorCode, TruffleString message) {
            super(message.toJavaStringUncached());
            this.errorCode = errorCode;
            msg = message;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public final TruffleString getMessageAsTruffleString() {
            return msg;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    // endregion

    /**
     * Base class for exceptions that originate in the POSIX support layer.
     */
    public abstract static class PosixException extends Exception {

        private static final long serialVersionUID = 8700515065120346760L;

        protected PosixException() {
        }

        protected PosixException(String message) {
            super(message);
        }

        public final boolean hasErrno(OSErrorEnum errno) {
            return this instanceof PosixErrnoException errnoException && errnoException.getErrorCode() == errno.getNumber();
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Exception that indicates POSIX level error associated with numeric code. If the message is
     * known, it may be included in the exception, otherwise it can be queried using
     * {@link PosixSupport#strerror(int)}.
     */
    public static final class PosixErrnoException extends PosixException {

        private static final long serialVersionUID = -115762483478883093L;

        private final int errorCode;
        private final transient TruffleString msg;
        /*
         * Windows APIs expose both POSIX errno and a native Win32/Winsock error code. Store the
         * native code as java.lang.Integer rather than int because most PosixErrnoException
         * instances are not backed by a Windows error, so this field can be null.
         */
        private final Integer winerror;

        public PosixErrnoException(int errorCode, TruffleString message) {
            this(errorCode, message, null);
        }

        public PosixErrnoException(int errorCode, TruffleString message, Integer winerror) {
            this.errorCode = errorCode;
            msg = message;
            this.winerror = winerror;
        }

        public TruffleString getMessageAsTruffleString() {
            return msg;
        }

        @Override
        public String getMessage() {
            return msg.toJavaStringUncached();
        }

        public int getErrorCode() {
            return errorCode;
        }

        public Integer getWinerror() {
            return winerror;
        }
    }

    /**
     * Exception that indicates that a string of characters passed into the {@code inet_aton} or
     * {@code inet_pton} function does not represent a valid IP address. These functions do not use
     * the usual {@code errno} mechanism to report this kind of errors.
     */
    public static class InvalidAddressException extends Exception {

        private static final long serialVersionUID = -2999913421191382026L;

        public InvalidAddressException() {
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Exception that indicates that path for a unix socket was too long.
     */
    public static class InvalidUnixSocketPathException extends Exception {

        private static final long serialVersionUID = 3603545222627858084L;

        public static InvalidUnixSocketPathException INSTANCE = new InvalidUnixSocketPathException();

        private InvalidUnixSocketPathException() {
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Exception that may be thrown by all the messages. It indicates that given functionality is
     * not available in given implementation. In the future, there will be methods to query if
     * certain feature is supported or not, but even then this exception may be thrown for other
     * features.
     */
    public static class UnsupportedPosixFeatureException extends PosixException {

        private static final long serialVersionUID = 1846254827094902593L;

        public UnsupportedPosixFeatureException(String message) {
            super(message);
        }
    }

    /**
     * Simple wrapper that allows exchanging byte buffers with the outside world.
     */
    @ValueType
    public static class Buffer {
        public final byte[] data;
        public long length;

        public Buffer(byte[] data, long length) {
            assert data != null && length >= 0 && length <= data.length;
            this.data = data;
            this.length = length;
        }

        public static Buffer allocate(long capacity) {
            if (capacity > Integer.MAX_VALUE) {
                throw CompilerDirectives.shouldNotReachHere("Long arrays are not supported yet");
            }
            return new Buffer(new byte[(int) capacity], 0);
        }

        public static Buffer wrap(byte[] data) {
            return new Buffer(data, data.length);
        }

        public Buffer withLength(long newLength) {
            if (newLength > data.length) {
                throw CompilerDirectives.shouldNotReachHere("Actual length cannot be greater than capacity");
            }
            length = newLength;
            return this;
        }

        @TruffleBoundary
        public ByteBuffer getByteBuffer() {
            return ByteBuffer.wrap(data, 0, (int) length);
        }
    }

    /**
     * Corresponds to the {@code timeval} struct.
     */
    @ValueType
    public static final class Timeval {
        public static final Timeval SELECT_TIMEOUT_NOW = new Timeval(0, 0);

        private final long seconds;
        private final long microseconds;

        public Timeval(long seconds, long microseconds) {
            this.seconds = seconds;
            this.microseconds = microseconds;
        }

        public long getSeconds() {
            return seconds;
        }

        public long getMicroseconds() {
            return microseconds;
        }
    }

    /**
     * Wraps boolean arrays that indicate if given file descriptor was selected or not. For example,
     * if {@code getReadFds()[X]} is {@code true}, then the file descriptor that was passed to
     * {@code select} as {@code readfds[X]} was selected.
     */
    @ValueType
    public static final class SelectResult {
        private final boolean[] readfds;
        private final boolean[] writefds;
        private final boolean[] errorfds;

        public SelectResult(boolean[] readfds, boolean[] writefds, boolean[] errorfds) {
            this.readfds = readfds;
            this.writefds = writefds;
            this.errorfds = errorfds;
        }

        public boolean[] getReadFds() {
            return readfds;
        }

        public boolean[] getWriteFds() {
            return writefds;
        }

        public boolean[] getErrorFds() {
            return errorfds;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("select[read = %s; write = %s; err = %s]", Arrays.toString(readfds), Arrays.toString(writefds), Arrays.toString(errorfds));
        }
    }

    // from stat.h macros
    private static boolean istype(long mode, int mask) {
        return (mode & S_IFMT.value) == mask;
    }

    public static boolean isDIR(long mode) {
        return istype(mode, S_IFDIR.value);
    }

    public static boolean isCHR(long mode) {
        return istype(mode, S_IFCHR.value);
    }

    public static boolean isBLK(long mode) {
        return istype(mode, S_IFBLK.value);
    }

    public static boolean isREG(long mode) {
        return istype(mode, S_IFREG.value);
    }

    public static boolean isFIFO(long mode) {
        return istype(mode, S_IFIFO.value);
    }

    public static boolean isLNK(long mode) {
        return istype(mode, S_IFLNK.value);
    }

    public static class ChannelNotSelectableException extends UnsupportedPosixFeatureException {
        private static final long serialVersionUID = -4185480181939639297L;
        static ChannelNotSelectableException INSTANCE = new ChannelNotSelectableException();

        private ChannelNotSelectableException() {
            super(null);
        }
    }

    static final LibraryFactory<PosixSupportLibrary> FACTORY = LibraryFactory.resolve(PosixSupportLibrary.class);

    public static LibraryFactory<PosixSupportLibrary> getFactory() {
        return FACTORY;
    }

    public static PosixSupportLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
