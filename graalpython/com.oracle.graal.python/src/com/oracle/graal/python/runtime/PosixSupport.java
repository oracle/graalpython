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
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PosixSupport {

    public static final int ST_MODE = 0;

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

    public abstract static class PwdResult {
        /**
         * This value represents unsigned 64 bit integer.
         */
        public final long uid;

        /**
         * This value represents unsigned 64 bit integer.
         */
        public final long gid;

        protected PwdResult(long uid, long gid) {
            this.uid = uid;
            this.gid = gid;
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

    public abstract static class Path {
    }

    public void setEnv(@SuppressWarnings("unused") Env env) {
        // nop
    }

    public static PosixSupport get(Node node) {
        return PythonContext.get(node).getPosixSupport();
    }

    public abstract long semOpen(Object name, int openFlags, int mode, int value) throws PosixException;

    public final long semOpen(Object name) throws PosixException {
        return semOpen(name, 0, 0, 0);
    }

    public abstract void semClose(long handle) throws PosixException;

    public abstract void semUnlink(Object name) throws PosixException;

    public abstract int shmOpen(Object name, int openFlags, int mode) throws PosixException;

    public abstract void shmUnlink(Object name) throws PosixException;

    public abstract int semGetValue(long handle) throws PosixException;

    public abstract void semPost(long handle) throws PosixException;

    public abstract void semWait(long handle) throws PosixException;

    public abstract boolean semTryWait(long handle) throws PosixException;

    public abstract boolean semTimedWait(Node location, long handle, long deadlineNs) throws PosixException;

    /**
     * Equivalent of POSIX {@code getpwuid_r}. On top of the error codes defined by POSIX, this may
     * also throw {@code ENOMEM}. Returns {@code null} if no matching entry was found.
     */
    public abstract PwdResult getpwuid(long uid) throws PosixException;

    /**
     * Equivalent of POSIX {@code getpwnam_r}. On top of the error codes defined by POSIX, this may
     * also throw {@code ENOMEM}. Returns {@code null} if no matching entry was found.
     *
     * @param name the name encoded the same way as paths
     */
    public abstract PwdResult getpwnam(Object name) throws PosixException;

    /**
     * Availability of {@link #getpwentries()}. If {@code false}, then {@link #getpwentries()} will
     * throw {@link UnsupportedPosixFeatureException}.
     */
    public abstract boolean hasGetpwentries();

    /**
     * Returns a list of all entries in the password database. Equivalent of using POSIX functions
     * {@code setpwent}, {@code getpwent}, and {@code endpwent}.
     */
    public abstract PwdResult[] getpwentries() throws PosixException;

    /** Wraps already-encoded bytes for narrow native APIs without applying a filesystem conversion. */
    public abstract Object createCStringFromBytes(byte[] bytes);

    public abstract int socket(int domain, int type, int protocol) throws PosixException;

    public abstract AcceptResult accept(int sockfd) throws PosixException;

    public abstract void bind(int sockfd, UniversalSockAddr addr) throws PosixException;

    public abstract void connect(int sockfd, UniversalSockAddr addr) throws PosixException;

    public abstract void listen(int sockfd, int backlog) throws PosixException;

    public abstract UniversalSockAddr getpeername(int sockfd) throws PosixException;

    public abstract UniversalSockAddr getsockname(int sockfd) throws PosixException;

    public abstract int send(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

    // Unlike POSIX sendto(), we don't support destAddr == null. Use plain send instead.
    public abstract int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr destAddr) throws PosixException;

    public abstract int recv(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

    // For STREAM sockets, the returned address will be AF_UNSPEC
    public abstract RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

    public abstract void shutdown(int sockfd, int how) throws PosixException;

    public abstract int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException;

    public abstract void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException;

    public abstract int inet_addr(Object src);

    public abstract int inet_aton(Object src) throws InvalidAddressException;

    public abstract Object inet_ntoa(int address);

    public abstract byte[] inet_pton(int family, Object src) throws PosixException, InvalidAddressException;

    public abstract Object inet_ntop(int family, byte[] src) throws PosixException;

    public abstract Object gethostname() throws PosixException;

    public abstract Object[] getnameinfo(UniversalSockAddr addr, int flags) throws UnsupportedPosixFeatureException, GetAddrInfoException;

    /** The caller must release the returned cursor exactly once. */
    public abstract AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags)
                    throws UnsupportedPosixFeatureException, GetAddrInfoException;

    public abstract int ioctlBytes(int fd, long request, byte[] arg) throws PosixException;

    public abstract int ioctlInt(int fd, long request, int arg) throws PosixException;

    public abstract UniversalSockAddr createUniversalSockAddrInet4(Inet4SockAddr src);

    public abstract UniversalSockAddr createUniversalSockAddrInet6(Inet6SockAddr src);

    public abstract UniversalSockAddr createUniversalSockAddrUnix(UnixSockAddr src) throws UnsupportedPosixFeatureException, InvalidUnixSocketPathException;

    public abstract void raise(int signal) throws PosixException;

    public abstract int alarm(int seconds) throws PosixException;

    public abstract Timeval[] getitimer(int which) throws PosixException;

    public abstract Timeval[] setitimer(int which, Timeval delay, Timeval interval) throws PosixException;

    public abstract void signalSelf(int signal) throws PosixException;

    public abstract void kill(long pid, int signal) throws PosixException;

    public abstract void killpg(long pid, int signal) throws PosixException;

    public abstract long[] waitpid(Node location, long pid, int options) throws PosixException;

    public abstract boolean wcoredump(int status);

    public abstract boolean wifcontinued(int status);

    public abstract boolean wifstopped(int status);

    public abstract boolean wifsignaled(int status);

    public abstract boolean wifexited(int status);

    public abstract int wexitstatus(int status);

    public abstract int wtermsig(int status);

    public abstract int wstopsig(int status);

    public abstract long getuid();

    public abstract long geteuid() throws UnsupportedPosixFeatureException;

    public abstract long getgid();

    public abstract long getegid() throws UnsupportedPosixFeatureException;

    public abstract long getppid() throws UnsupportedPosixFeatureException;

    public abstract long getpgid(long pid) throws PosixException;

    public abstract void setpgid(long pid, long pgid) throws PosixException;

    public abstract long getpgrp() throws UnsupportedPosixFeatureException;

    public abstract long getsid(long pid) throws PosixException;

    public abstract long setsid() throws PosixException;

    public abstract long[] getgroups() throws PosixException;

    public abstract RusageResult getrusage(int who) throws PosixException;

    public abstract OpenPtyResult openpty() throws PosixException;

    public abstract Object ctermid() throws PosixException;

    // note: this leaks memory in native backend and is not synchronized
    public abstract void setenv(Object name, Object value, boolean overwrite) throws PosixException;

    public abstract void unsetenv(Object name) throws PosixException;

    public abstract int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd,
                    int stderrReadFd, int stderrWriteFd, int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int pgidToSet, int[] fdsToKeep,
                    boolean allowVFork) throws PosixException;

    // args.length must be > 0
    public abstract void execv(Object pathname, Object[] args) throws PosixException;

    // does not throw, because posix does not exactly define the return value
    public abstract int system(Object command);

    public abstract Object mmap(Node location, long length, int prot, int flags, int fd, long offset, Object tagname) throws PosixException;

    public abstract byte mmapReadByte(Object mmap, long index) throws PosixException;

    public abstract void mmapWriteByte(Object mmap, long index, byte value) throws PosixException;

    public abstract int mmapReadBytes(Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapFlush(Object mmap, long offset, long length) throws PosixException;

    public abstract void mmapUnmap(Object mmap, long length) throws PosixException;

    public abstract long mmapGetPointer(Object mmap) throws UnsupportedPosixFeatureException;

    public abstract TruffleString getBackend();

    public abstract TruffleString strerror(int errorCode);

    public abstract long getpid();

    public abstract int umask(int mask) throws PosixException;

    public abstract int openat(int dirFd, Object pathname, int flags, int mode) throws PosixException;

    public abstract int close(int fd) throws PosixException;

    public abstract long getOsfHandle(int fd) throws PosixException;

    public abstract int openOsfHandle(long handle, int flags) throws PosixException;

    public abstract int setMode(int fd, int mode) throws PosixException;

    public abstract void msvcrtLocking(int fd, int mode, long nbytes) throws PosixException;

    public abstract int[] pipe() throws PosixException;

    public abstract SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException;

    public abstract void poll(int[] fds, int[] events, int[] revents, int timeout) throws PosixException;

    public abstract long lseek(int fd, long offset, int how) throws PosixException;

    public abstract void ftruncate(int fd, long length) throws PosixException;

    public abstract void truncate(Object path, long length) throws PosixException;

    public abstract void fsync(int fd) throws PosixException;

    public abstract void flock(int fd, int operation) throws PosixException;

    public abstract void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length) throws PosixException;

    public abstract boolean getBlocking(int fd) throws PosixException;

    public abstract void setBlocking(int fd, boolean blocking) throws PosixException;

    public abstract int[] getTerminalSize(int fd) throws PosixException;

    public abstract long sysconf(int name) throws PosixException;

    public abstract Buffer read(int fd, long length) throws PosixException;

    public abstract long write(int fd, Buffer data) throws PosixException;

    /** Returns {@code 'r'}, {@code 'w'}, or zero when {@code fd} is not a Windows console. */
    public abstract int getWindowsConsoleType(int fd);

    public abstract long writeWindowsConsole(int fd, Buffer data) throws PosixException;

    public abstract int dup(int fd) throws PosixException;

    public abstract int dup2(int fd, int fd2, boolean inheritable) throws PosixException;

    public abstract boolean getInheritable(int fd) throws PosixException;

    public abstract void setInheritable(int fd, boolean inheritable) throws PosixException;

    // see stat_struct_to_longs in posix.c for the layout of the array
    public abstract long[] fstatat(int dirFd, Object pathname, boolean followSymlinks) throws PosixException;

    /**
     * Performs operation of fstat(fd).
     *
     * @param fd the file descriptor
     * @return see {@code stat_struct_to_longs} in posix.c for the layout of the array. There are
     *         constants for some of the indices, e.g., {@link #ST_MODE}.
     * @throws PosixException if an error occurs
     */
    public abstract long[] fstat(int fd) throws PosixException;

    public abstract long[] statvfs(Object path) throws PosixException;

    public abstract long[] fstatvfs(int fd) throws PosixException;

    public abstract Object[] uname() throws PosixException;

    public record WindowsVersion(int major, int minor, int build, int platform, TruffleString servicePack,
                    int servicePackMajor, int servicePackMinor, int suiteMask, int productType,
                    int platformMajor, int platformMinor, int platformBuild) {
    }

    /** Returns the Windows version and product information. */
    public abstract WindowsVersion getWindowsVersion() throws PosixException;

    public abstract void unlinkat(int dirFd, Object pathname, boolean rmdir) throws PosixException;

    public abstract void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags) throws PosixException;

    public abstract void symlinkat(Object target, int linkpathDirFd, Object linkpath) throws PosixException;

    public abstract void mkdirat(int dirFd, Object pathname, int mode) throws PosixException;

    public abstract Object getcwd() throws PosixException;

    public abstract void chdir(Object path) throws PosixException;

    public abstract void fchdir(int fd) throws PosixException;

    public abstract boolean isatty(int fd);

    /** Caller is responsible for closing the returned directory stream with {@link #closedir(Object)}. */
    public abstract Object opendir(Object path) throws PosixException;

    public abstract Object fdopendir(int fd) throws PosixException;

    /** Implementations must deal with this being called more than once. */
    public abstract void closedir(Object dirStream) throws PosixException;

    /** Returns null when there are no more entries or the stream has been closed. */
    public abstract Object readdir(Object dirStream) throws PosixException;

    public abstract void rewinddir(Object dirStream);

    /** Returns an opaque directory-entry name suitable for path conversion. */
    public abstract Object dirEntryGetName(Object dirEntry) throws PosixException;

    /** Returns the entry name joined to the path originally passed to {@link #opendir(Object)}. */
    public abstract Object dirEntryGetPath(Object dirEntry, Object scandirPath) throws PosixException;

    public abstract long dirEntryGetInode(Object dirEntry) throws PosixException;

    public abstract int dirEntryGetType(Object dirEntry);

    /** The timespec contains access seconds/nanoseconds, then modification seconds/nanoseconds, or is null for now. */
    public abstract void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException;

    public abstract void futimens(int fd, long[] timespec) throws PosixException;

    /** The timeval is null or contains access and modification times. */
    public abstract void futimes(int fd, Timeval[] timeval) throws PosixException;

    public abstract void lutimes(Object filename, Timeval[] timeval) throws PosixException;

    public abstract void utimes(Object filename, Timeval[] timeval) throws PosixException;

    public abstract void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException;

    public abstract void replaceat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException;

    public abstract boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks) throws UnsupportedPosixFeatureException;

    public abstract void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks) throws PosixException;

    public abstract void fchmod(int fd, int mode) throws PosixException;

    public abstract void fchownat(int dirFd, Object pathname, long owner, long group, boolean followSymlinks) throws PosixException;

    public abstract void fchown(int fd, long owner, long group) throws PosixException;

    public abstract Object readlinkat(int dirFd, Object path) throws PosixException;
}
