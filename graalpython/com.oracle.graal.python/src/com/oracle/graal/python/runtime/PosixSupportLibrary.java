/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.runtime.PosixConstants.S_IFBLK;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFCHR;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFDIR;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFIFO;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFLNK;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFMT;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFREG;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

/**
 * Internal abstraction layer for POSIX functionality. Instance of the implementation is stored in
 * the context. Use {@link PythonContext#getPosixSupport()} to access it.
 */
@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class PosixSupportLibrary extends Library {

    public static final char POSIX_FILENAME_SEPARATOR = '/';

    // Constants for accessing the fields of the fstat result:
    // TODO: have these in posix.c (maybe posix.h) and extract them along with other constants
    public static final int ST_MODE = 0;
    public static final int ST_SIZE = 6;

    public abstract String getBackend(Object recevier);

    public abstract String strerror(Object receiver, int errorCode);

    public abstract long getpid(Object receiver);

    public abstract int umask(Object receiver, int mask) throws PosixException;

    public abstract int openat(Object receiver, int dirFd, Object pathname, int flags, int mode) throws PosixException;

    public abstract int close(Object receiver, int fd) throws PosixException;

    public abstract Buffer read(Object receiver, int fd, long length) throws PosixException;

    public abstract long write(Object receiver, int fd, Buffer data) throws PosixException;

    public abstract int dup(Object receiver, int fd) throws PosixException;

    public abstract int dup2(Object receiver, int fd, int fd2, boolean inheritable) throws PosixException;

    public abstract boolean getInheritable(Object receiver, int fd) throws PosixException;

    public abstract void setInheritable(Object receiver, int fd, boolean inheritable) throws PosixException;

    public abstract int[] pipe(Object receiver) throws PosixException;

    public abstract SelectResult select(Object receiver, int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException;

    public abstract long lseek(Object receiver, int fd, long offset, int how) throws PosixException;

    public abstract void ftruncate(Object receiver, int fd, long length) throws PosixException;

    public abstract void fsync(Object receiver, int fd) throws PosixException;

    public abstract void flock(Object receiver, int fd, int operation) throws PosixException;

    public abstract boolean getBlocking(Object receiver, int fd) throws PosixException;

    public abstract void setBlocking(Object receiver, int fd, boolean blocking) throws PosixException;

    public abstract int[] getTerminalSize(Object receiver, int fd) throws PosixException;

    // see stat_struct_to_longs in posix.c for the layout of the array
    public abstract long[] fstatat(Object receiver, int dirFd, Object pathname, boolean followSymlinks) throws PosixException;

    /**
     * Performs operation of fstat(fd).
     *
     * @param receiver the receiver of the message
     * @param fd the file descriptor
     * @return see {@code stat_struct_to_longs} in posix.c for the layout of the array. There are
     *         constants for some of the indices, e.g., {@link #ST_MODE}.
     * @throws PosixException if an error occurs
     */
    public abstract long[] fstat(Object receiver, int fd) throws PosixException;

    public abstract Object[] uname(Object receiver) throws PosixException;

    public abstract void unlinkat(Object receiver, int dirFd, Object pathname, boolean rmdir) throws PosixException;

    public abstract void symlinkat(Object receiver, Object target, int linkpathDirFd, Object linkpath) throws PosixException;

    public abstract void mkdirat(Object receiver, int dirFd, Object pathname, int mode) throws PosixException;

    public abstract Object getcwd(Object receiver) throws PosixException;

    public abstract void chdir(Object receiver, Object path) throws PosixException;

    /**
     * Performs operation of fchdir(fd).
     *
     * @param receiver the receiver of the message
     * @param fd the file descriptor
     * @throws PosixException if an error occurs
     */
    public abstract void fchdir(Object receiver, int fd) throws PosixException;

    public abstract boolean isatty(Object receiver, int fd);

    /**
     * Caller is responsible for calling {@link #closedir(Object, Object)} to free the allocated
     * resources.
     *
     * @return an opaque directory stream object to be used in calls to {@code readdir} and
     *         {@code closedir}
     */
    public abstract Object opendir(Object receiver, Object path) throws PosixException;

    public abstract Object fdopendir(Object receiver, int fd) throws PosixException;

    /**
     * Implementations must deal with this being called more than once.
     */
    public abstract void closedir(Object receiver, Object dirStream);

    /**
     * @return an opaque dir entry object to be used in calls to {@code dirEntry*()} methods or
     *         {@code null} when there are no more items or if the stream has been closed by
     *         {@code closedir}.
     */
    public abstract Object readdir(Object receiver, Object dirStream) throws PosixException;

    /**
     * @return an opaque object representing the dir entry name
     * @see #getPathAsBytes(Object, Object)
     * @see #getPathAsString(Object, Object)
     */
    public abstract Object dirEntryGetName(Object receiver, Object dirEntry) throws PosixException;

    /**
     * Returns the dir entry path, which is the name of the dir entry joined with the given path.
     *
     * @param scandirPath the path originally passed to {@link #opendir(Object, Object)}
     * @return an opaque object representing the dir entry path
     * @see #getPathAsBytes(Object, Object)
     * @see #getPathAsString(Object, Object)
     */
    public abstract Object dirEntryGetPath(Object receiver, Object dirEntry, Object scandirPath) throws PosixException;

    public abstract long dirEntryGetInode(Object receiver, Object dirEntry) throws PosixException;

    /**
     * @return one of the {@code DT_xxx} constants
     */
    public abstract int dirEntryGetType(Object receiver, Object dirEntry);

    /**
     * Equivalent of POSIX {@code utimensat()}.
     * 
     * @param timespec an array of 4 longs in this order:
     *            {@code atime.tv_sec, atime.tv_nsec, mtime.tv_sec, mtime.tv_nsec} or {@code null}
     *            to set both times to 'now' TODO change long[] timespec to Timespec[] timespec
     */
    public abstract void utimensat(Object receiver, int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException;

    /**
     * Equivalent of POSIX {@code futimens()}.
     */
    public abstract void futimens(Object receiver, int fd, long[] timespec) throws PosixException;

    /**
     * @param timeval either {@code null} or has two elements: access time and modification time
     */
    public abstract void futimes(Object receiver, int fd, Timeval[] timeval) throws PosixException;

    public abstract void lutimes(Object receiver, Object filename, Timeval[] timeval) throws PosixException;

    public abstract void utimes(Object receiver, Object filename, Timeval[] timeval) throws PosixException;

    public abstract void renameat(Object receiver, int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException;

    public abstract boolean faccessat(Object receiver, int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks);

    public abstract void fchmodat(Object receiver, int dirFd, Object path, int mode, boolean followSymlinks) throws PosixException;

    public abstract void fchmod(Object receiver, int fd, int mode) throws PosixException;

    public abstract Object readlinkat(Object receiver, int dirFd, Object path) throws PosixException;

    public abstract void kill(Object receiver, long pid, int signal) throws PosixException;

    public abstract long[] waitpid(Object receiver, long pid, int options) throws PosixException;

    public abstract boolean wcoredump(Object receiver, int status);

    public abstract boolean wifcontinued(Object receiver, int status);

    public abstract boolean wifstopped(Object receiver, int status);

    public abstract boolean wifsignaled(Object receiver, int status);

    public abstract boolean wifexited(Object receiver, int status);

    public abstract int wexitstatus(Object receiver, int status);

    public abstract int wtermsig(Object receiver, int status);

    public abstract int wstopsig(Object receiver, int status);

    public abstract long getuid(Object receiver);

    public abstract long getppid(Object receiver);

    public abstract long getsid(Object receiver, long pid) throws PosixException;

    public abstract String ctermid(Object receiver) throws PosixException;

    // note: this leaks memory in nfi backend and is not synchronized
    // TODO is it worth synchronizing at least all accesses made through PosixSupportLibrary?
    public abstract void setenv(Object receiver, Object name, Object value, boolean overwrite) throws PosixException;

    public abstract int forkExec(Object receiver, Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd,
                    int stderrReadFd, int stderrWriteFd, int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep) throws PosixException;

    // args.length must be > 0
    public abstract void execv(Object receiver, Object pathname, Object[] args) throws PosixException;

    // does not throw, because posix does not exactly define the return value
    public abstract int system(Object receiver, Object command);

    public abstract Object mmap(Object receiver, long length, int prot, int flags, int fd, long offset) throws PosixException;

    public abstract byte mmapReadByte(Object receiver, Object mmap, long index) throws PosixException;

    public abstract int mmapReadBytes(Object receiver, Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapWriteBytes(Object receiver, Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapFlush(Object receiver, Object mmap, long offset, long length) throws PosixException;

    public abstract void mmapUnmap(Object receiver, Object mmap, long length) throws PosixException;

    /**
     * Converts a {@code String} into the internal representation of paths used by the library
     * implementation. The implementation should return {@code null} if the path after any necessary
     * conversion contains embedded null characters.
     *
     * @param receiver the receiver of the message
     * @param path the path as a {@code String}
     * @return an opaque object representing the path or {@code null} if the path contains null
     *         characters
     */
    public abstract Object createPathFromString(Object receiver, String path);

    /**
     * Converts a {@code byte} array into the internal representation of paths used by the library
     * implementation. The implementation should return {@code null} if the path after any necessary
     * conversion contains embedded null characters.
     *
     * @param receiver the receiver of the message
     * @param path the path as a a {@code byte[]} array
     * @return an opaque object representing the path or {@code null} if the path contains null
     *         characters
     */
    public abstract Object createPathFromBytes(Object receiver, byte[] path);

    public abstract String getPathAsString(Object receiver, Object path);

    public abstract Buffer getPathAsBytes(Object receiver, Object path);

    // region Socket addresses

    /**
     * Represents an address of a socket.
     *
     * Addresses are either specific to a particular socket family (subclasses of
     * {@link FamilySpecificSockAddr}) or universal ({@link UniversalSockAddr}). It is possible to
     * convert any family-specific address to a universal address. The conversion in opposite
     * direction is possible only if the target family-specific type matches the socket family of
     * the address stored in the source {@link UniversalSockAddr} instance.
     */
    public interface SockAddr {
    }

    /**
     * Base class for addresses specific to a particular socket family.
     *
     * The subclasses are simple POJOs whose definitions are common to all backends. A
     * family-specific address is convenient to use (compared to {@link UniversalSockAddr}), but the
     * backend needs to convert it to its internal representation every time it is used. The use of
     * family-specific addresses is appropriate when the socket family is known and when the address
     * is used just a couple of times. This class corresponds to POSIX {@code struct sockaddr}.
     */
    public static abstract class FamilySpecificSockAddr implements SockAddr {
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
     * {@link FamilySpecificSockAddr} subclasses). This makes them suitable in situations where the
     * address needs to be used more than once or when the socket family is not known. For example,
     * a UDP server that responds to an incoming packet by sending multiple packets might want to
     * use universal address (and does not even need to know whether it is using IPv4 or IPv6). The
     * disadvantage of {@link UniversalSockAddr} is that it needs to be explicitly deallocated since
     * it is stored in the native heap (in the NFI backend). This interface corresponds to POSIX
     * {@code struct sockaddr_storage}.
     *
     * @see UniversalSockAddrLibrary
     */
    public interface UniversalSockAddr extends SockAddr {
    }

    /**
     * Represents an address for IPv4 sockets (the {@link PosixConstants#AF_INET} socket family).
     *
     * This is a higher level equivalent of POSIX {@code struct sockaddr_in} - the values are kept
     * in host byte order, conversion to network order ({@code htons/htonl}) is done automatically
     * by the backend.
     */
    @ValueType
    public static final class Inet4SockAddr extends FamilySpecificSockAddr {
        private int port;           // host order, 0 - 65535
        private int address;        // host order, e.g. INADDR_LOOPBACK

        public Inet4SockAddr(int port, int address) {
            super(AF_INET.value);
            assert port >= 0 && port <= 65535;
            this.port = port;
            this.address = address;
        }

        public Inet4SockAddr() {
            super(AF_INET.value);
        }

        public int getPort() {
            return port;
        }

        public int getAddress() {
            return address;
        }

        public void setPort(int port) {
            assert port >= 0 && port <= 65535;
            this.port = port;
        }

        public void setAddress(int address) {
            this.address = address;
        }
    }

    // endregion

    // region socket messages

    /**
     * Creates a new socket.
     *
     * @see "socket(2) man pages"
     * @see PosixConstants
     */
    public abstract int socket(Object receiver, int domain, int type, int protocol) throws PosixException;

    // addr is an input parameter
    public abstract void bind(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // addr is an output parameter
    public abstract void getsockname(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // Unlike POSIX sendto(), we don't support destAddr == null. Use plain send instead.
    // destAddr is an input parameter
    public abstract int sendto(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr destAddr) throws PosixException;

    // Unlike POSIX recvfrom(), we don't support srcAddr == null. Use plain recv instead.
    // srcAddr is an output parameter
    // throws IllegalArgumentException if the type of srcAddr does not match the actual socket
    // family of the packet's source address, in which case the state of the socket and buf is
    // unspecified.
    public abstract int recvfrom(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr srcAddr) throws PosixException;

    // endregion

    // region Name resolution messages

    /**
     * Corresponds to POSIX {@code getaddrinfo(3)}, except it always passes a non-null value for the
     * {@code hints} parameter.
     *
     * @param node is the host name converted using
     *            {@link PosixSupportLibrary#createPathFromBytes(Object, byte[])} or
     *            {@link PosixSupportLibrary#createPathFromString(Object, String)}
     * @param service is the service name converted using
     *            {@link PosixSupportLibrary#createPathFromBytes(Object, byte[])} or
     *            {@link PosixSupportLibrary#createPathFromString(Object, String)}
     * @param family one of the {@code AF_xxx} constants, or {@link PosixConstants#AF_UNSPEC} to get
     *            addresses of any family
     * @param sockType one of the {@code SOCK_xxx} constants, or 0 to get addresses of any type
     * @param protocol 0 to get addresses with any protocol
     * @param flags bitwise OR of {@code AI_xxx} constants
     * @return an object representing one or more {@code struct addrinfo}s, which must be explicitly
     *         released by the caller
     * @throws GetAddrInfoException when an error occurs (PosixException is not thrown because
     *             getaddrinfo uses its own error codes and gai_strerror instead of the usual errno
     *             and strerror)
     */
    public abstract AddrInfoCursor getaddrinfo(Object receiver, Object node, Object service, int family, int sockType, int protocol, int flags) throws GetAddrInfoException;

    /**
     * Represents one or more addrinfos returned by {@code getaddrinfo()}.
     *
     * Must be explicitly released using {@link AddrInfoCursorLibrary#release(AddrInfoCursor)}.
     * Behaves like a cursor which points to a {@code struct addrinfo} structure (initially pointing
     * at the first address info). The cursor can only move forward using the
     * {@link AddrInfoCursorLibrary#next(AddrInfoCursor)} message.
     *
     * @see AddrInfoCursorLibrary
     */
    public interface AddrInfoCursor {
    }

    /**
     * Provides messages for manipulating {@link AddrInfoCursor}.
     */
    @GenerateLibrary
    public static abstract class AddrInfoCursorLibrary extends Library {

        protected AddrInfoCursorLibrary() {
        }

        /**
         * Releases resources associated with the results of {@code getaddrinfo()}.
         *
         * This must be called exactly once on all instances returned from
         * {@link #getaddrinfo(Object, Object, Object, int, int, int, int)}. Released instances can
         * no longer be used for any purpose.
         */
        public abstract void release(AddrInfoCursor receiver);

        /**
         * Moves the cursor to the next address info.
         *
         * @return false if there are no more address infos in which case the cursor keeps pointing
         *         to the last item
         */
        public abstract boolean next(AddrInfoCursor receiver);

        public abstract int getFlags(AddrInfoCursor receiver);

        public abstract int getFamily(AddrInfoCursor receiver);

        public abstract int getSockType(AddrInfoCursor receiver);

        public abstract int getProtocol(AddrInfoCursor receiver);

        /**
         * @return {@code null} or opaque name to be converted using
         *         {@link PosixSupportLibrary#getPathAsString(Object, Object)} or
         *         {@link PosixSupportLibrary#getPathAsBytes(Object, Object)}
         */
        public abstract Object getCanonName(AddrInfoCursor receiver);

        // addr is an output parameter
        public abstract void getSockAddr(AddrInfoCursor receiver, SockAddr addr);

        static final LibraryFactory<AddrInfoCursorLibrary> FACTORY = LibraryFactory.resolve(AddrInfoCursorLibrary.class);

        public static LibraryFactory<AddrInfoCursorLibrary> getFactory() {
            return FACTORY;
        }

        public static AddrInfoCursorLibrary getUncached() {
            return FACTORY.getUncached();
        }
    }

    /**
     * Exception that indicates and error while executing
     * {@link #getaddrinfo(Object, Object, Object, int, int, int, int)}.
     */
    public static class GetAddrInfoException extends Exception {

        private static final long serialVersionUID = 3013253817849329391L;

        private final int errorCode;

        public GetAddrInfoException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    // endregion

    /**
     * Allocates a new {@link UniversalSockAddr} and sets its family to
     * {@link PosixConstants#AF_UNSPEC}. It can be either filled by
     * {@link UniversalSockAddrLibrary#fill(UniversalSockAddr, SockAddr)} or used in a call that
     * returns an address, such as {@link #getsockname(Object, int, SockAddr)} or
     * {@link #recvfrom(Object, int, byte[], int, int, SockAddr)}. The returned object must be
     * explicitly deallocated exactly once using the
     * {@link UniversalSockAddrLibrary#release(UniversalSockAddr)} message.
     */
    public abstract UniversalSockAddr allocUniversalSockAddr(Object receiver);

    /**
     * Provides messages for manipulating {@link UniversalSockAddr}.
     */
    @GenerateLibrary
    public static abstract class UniversalSockAddrLibrary extends Library {

        protected UniversalSockAddrLibrary() {
        }

        /**
         * Releases resources associated with the address.
         *
         * This must be called exactly once on all instances returned from
         * {@link #allocUniversalSockAddr(Object)}. Released instances can no longer be used for any
         * purpose.
         */
        public abstract void release(UniversalSockAddr receiver);

        /**
         * Returns the socket family of the address (one of the {@code AF_xxx} values defined in
         * {@link PosixConstants}).
         */
        public abstract int getFamily(UniversalSockAddr receiver);

        /**
         * Fills the receiver with the backend-specific representation of the {@code src} address.
         * Note that {@code src} can itself be an instance of {@link UniversalSockAddr} (provided by
         * the same backend), in which case a direct copy is made.
         */
        public abstract void fill(UniversalSockAddr receiver, SockAddr src);

        /**
         * Converts the address represented by the receiver to {@code dst}. Note that {@code dst}
         * can itself be an instance of {@link UniversalSockAddr} (provided by the same backend), in
         * which case a direct copy is made.
         *
         * @throws IllegalArgumentException if the socket family of the address does not match the
         *             type of {@code dst}
         */
        public abstract void convert(UniversalSockAddr receiver, SockAddr dest);

        /**
         * Converts the address represented by the receiver (which must be of the
         * {@link PosixConstants#AF_INET} family) into a {@link Inet4SockAddr} instance.
         *
         * @throws IllegalArgumentException if the socket family of the address is not
         *             {@link PosixConstants#AF_INET}
         */
        public Inet4SockAddr asInet4SockAddr(UniversalSockAddr receiver) {
            Inet4SockAddr addr = new Inet4SockAddr();
            convert(receiver, addr);
            return addr;
        }

        static final LibraryFactory<UniversalSockAddrLibrary> FACTORY = LibraryFactory.resolve(UniversalSockAddrLibrary.class);

        public static LibraryFactory<UniversalSockAddrLibrary> getFactory() {
            return FACTORY;
        }

        public static UniversalSockAddrLibrary getUncached() {
            return FACTORY.getUncached();
        }
    }

    /**
     * Exception that indicates POSIX level error associated with numeric code. If the message is
     * known, it may be included in the exception, otherwise it can be queried using
     * {@link #strerror(Object, int)}.
     */
    public static class PosixException extends Exception {

        private static final long serialVersionUID = -115762483478883093L;

        private final int errorCode;

        public PosixException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
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
    public static class UnsupportedPosixFeatureException extends RuntimeException {

        private static final long serialVersionUID = 1846254827094902593L;

        public UnsupportedPosixFeatureException(String message) {
            super(message);
        }

        @Override
        @SuppressWarnings("sync-override")
        public final Throwable fillInStackTrace() {
            return this;
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
