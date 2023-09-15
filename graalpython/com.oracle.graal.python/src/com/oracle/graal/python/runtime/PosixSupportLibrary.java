/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

    public static final char POSIX_FILENAME_SEPARATOR = '/';

    // Constants for accessing the fields of the fstat result:
    // TODO: have these in posix.c (maybe posix.h) and extract them along with other constants
    public static final int ST_MODE = 0;
    public static final int ST_SIZE = 6;

    public abstract TruffleString getBackend(Object recevier);

    public abstract TruffleString strerror(Object receiver, int errorCode);

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

    public abstract long[] statvfs(Object receiver, Object path) throws PosixException;

    public abstract long[] fstatvfs(Object receiver, int fd) throws PosixException;

    public abstract Object[] uname(Object receiver) throws PosixException;

    public abstract void unlinkat(Object receiver, int dirFd, Object pathname, boolean rmdir) throws PosixException;

    public abstract void linkat(Object receiver, int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags) throws PosixException;

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
    public abstract void closedir(Object receiver, Object dirStream) throws PosixException;

    /**
     * @return an opaque dir entry object to be used in calls to {@code dirEntry*()} methods or
     *         {@code null} when there are no more items or if the stream has been closed by
     *         {@code closedir}.
     */
    public abstract Object readdir(Object receiver, Object dirStream) throws PosixException;

    public abstract void rewinddir(Object receiver, Object dirStream);

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

    public abstract void killpg(Object receiver, long pid, int signal) throws PosixException;

    public abstract long[] waitpid(Object receiver, long pid, int options) throws PosixException;

    public abstract void abort(Object receiver);

    public abstract boolean wcoredump(Object receiver, int status);

    public abstract boolean wifcontinued(Object receiver, int status);

    public abstract boolean wifstopped(Object receiver, int status);

    public abstract boolean wifsignaled(Object receiver, int status);

    public abstract boolean wifexited(Object receiver, int status);

    public abstract int wexitstatus(Object receiver, int status);

    public abstract int wtermsig(Object receiver, int status);

    public abstract int wstopsig(Object receiver, int status);

    public abstract long getuid(Object receiver);

    public abstract long geteuid(Object receiver);

    public abstract long getgid(Object receiver);

    public abstract long getppid(Object receiver);

    public abstract long getpgid(Object receiver, long pid) throws PosixException;

    public abstract void setpgid(Object receiver, long pid, long pgid) throws PosixException;

    public abstract long getpgrp(Object receiver);

    public abstract long getsid(Object receiver, long pid) throws PosixException;

    public abstract long setsid(Object receiver) throws PosixException;

    public record OpenPtyResult(int masterFd, int slaveFd) {
    }

    public abstract OpenPtyResult openpty(Object receiver) throws PosixException;

    public abstract TruffleString ctermid(Object receiver) throws PosixException;

    // note: this leaks memory in nfi backend and is not synchronized
    // TODO is it worth synchronizing at least all accesses made through PosixSupportLibrary?
    public abstract void setenv(Object receiver, Object name, Object value, boolean overwrite) throws PosixException;

    public abstract void unsetenv(Object receiver, Object name) throws PosixException;

    public abstract int forkExec(Object receiver, Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd,
                    int stderrReadFd, int stderrWriteFd, int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep) throws PosixException;

    // args.length must be > 0
    public abstract void execv(Object receiver, Object pathname, Object[] args) throws PosixException;

    // does not throw, because posix does not exactly define the return value
    public abstract int system(Object receiver, Object command);

    public abstract Object mmap(Object receiver, long length, int prot, int flags, int fd, long offset) throws PosixException;

    public abstract byte mmapReadByte(Object receiver, Object mmap, long index) throws PosixException;

    public abstract void mmapWriteByte(Object receiver, Object mmap, long index, byte value) throws PosixException;

    public abstract int mmapReadBytes(Object receiver, Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapWriteBytes(Object receiver, Object mmap, long index, byte[] bytes, int length) throws PosixException;

    public abstract void mmapFlush(Object receiver, Object mmap, long offset, long length) throws PosixException;

    public abstract void mmapUnmap(Object receiver, Object mmap, long length) throws PosixException;

    public abstract long mmapGetPointer(Object receiver, Object mmap);

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

    /**
     * Equivalent of POSIX {@code getpwuid_r}. On top of the error codes defined by POSIX, this may
     * also throw {@code ENOMEM}. Returns {@code null} if no matching entry was found.
     */
    public abstract PwdResult getpwuid(Object receiver, long uid) throws PosixException;

    /**
     * Equivalent of POSIX {@code getpwnam_r}. On top of the error codes defined by POSIX, this may
     * also throw {@code ENOMEM}. Returns {@code null} if no matching entry was found.
     *
     * @param receiver the receiver of the message
     * @param name the name encoded the same way as paths
     */
    public abstract PwdResult getpwnam(Object receiver, Object name) throws PosixException;

    /**
     * Availability of {@link #getpwentries(Object)}. If {@code false}, then
     * {@link #getpwentries(Object)} will throw {@link UnsupportedPosixFeatureException}.
     */
    public abstract boolean hasGetpwentries(Object receiver);

    /**
     * Returns a list of all entries in the password database. Equivalent of using POSIX functions
     * {@code setpwent}, {@code getpwent}, and {@code endpwent}.
     */
    public abstract PwdResult[] getpwentries(Object receiver) throws PosixException;

    /**
     * Converts a {@code TruffleString} into the internal representation of paths used by the
     * library implementation. The implementation should return {@code null} if the path after any
     * necessary conversion contains embedded null characters.
     *
     * @param receiver the receiver of the message
     * @param path the path as a {@code TruffleString}
     * @return an opaque object representing the path or {@code null} if the path contains null
     *         characters
     */
    public abstract Object createPathFromString(Object receiver, TruffleString path);

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

    public abstract TruffleString getPathAsString(Object receiver, Object path);

    public abstract Buffer getPathAsBytes(Object receiver, Object path);

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
     * @see UniversalSockAddrLibrary
     */
    public interface UniversalSockAddr {
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

    // region socket messages

    /**
     * Creates a new socket.
     *
     * @see "socket(2) man pages"
     * @see PosixConstants
     */
    public abstract int socket(Object receiver, int domain, int type, int protocol) throws PosixException;

    public abstract AcceptResult accept(Object receiver, int sockfd) throws PosixException;

    public abstract void bind(Object receiver, int sockfd, UniversalSockAddr addr) throws PosixException;

    public abstract void connect(Object receiver, int sockfd, UniversalSockAddr addr) throws PosixException;

    public abstract void listen(Object receiver, int sockfd, int backlog) throws PosixException;

    public abstract UniversalSockAddr getpeername(Object receiver, int sockfd) throws PosixException;

    public abstract UniversalSockAddr getsockname(Object receiver, int sockfd) throws PosixException;

    public abstract int send(Object receiver, int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

    // Unlike POSIX sendto(), we don't support destAddr == null. Use plain send instead.
    public abstract int sendto(Object receiver, int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr destAddr) throws PosixException;

    public abstract int recv(Object receiver, int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

    // For STREAM sockets, the returned address will be AF_UNSPEC
    public abstract RecvfromResult recvfrom(Object receiver, int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException;

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

    public abstract void shutdown(Object receiver, int sockfd, int how) throws PosixException;

    /**
     * @param optval buffer for the option value
     * @param optlen size of the buffer // TODO use optval.length instead? See also recv,
     *            mmapReadBytes, read and GR-29856
     * @return the actual size of the value returned
     */
    public abstract int getsockopt(Object receiver, int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException;

    public abstract void setsockopt(Object receiver, int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException;

    // endregion

    // region Name resolution messages

    /**
     * Corresponds to POSIX {@code inet_addr} function, but the address is returned in host byte
     * order (and is signed) so that it can be used in {@link Inet4SockAddr} directly without any
     * further conversions. If the input is invalid, the native function returns
     * {@link PosixConstants#INADDR_NONE}, which is also returned when the input is
     * {@code 255.255.255.255}. Since it is not possible to tell whether an error occurred, this
     * message does not throw exception and leaves the decision to the caller who might have more
     * information.
     *
     * @param src the IPv4 address in numbers-and-dots notation (converted to opaque object using
     *            createPathFromBytes or createPathFromString)
     * @return address in host byte order or {@link PosixConstants#INADDR_NONE} if the input is
     *         invalid
     * @see "inet(3) man pages"
     */
    public abstract int inet_addr(Object receiver, Object src);

    /**
     * Corresponds to POSIX {@code inet_aton} function, but the address is returned in host byte
     * order (and is signed) so that it can be used in {@link Inet4SockAddr} directly without any
     * further conversions.
     *
     * @param src the IPv4 address in numbers-and-dots notation (converted to opaque object using
     *            createPathFromBytes or createPathFromString)
     * @return address in host byte order
     * @throws InvalidAddressException if {@code cp} is not a valid representation of an IPv4
     *             address
     * @see "inet(3) man pages"
     */
    public abstract int inet_aton(Object receiver, Object src) throws InvalidAddressException;

    /**
     * Corresponds to POSIX {@code inet_ntoa} function, but the address is expected in host byte
     * order (and is signed) for consistency with other messages.
     *
     * @param address tha IPv4 address in host byte order
     * @return opaque string in IPv4 dotted-decimal notation to be converted using
     *         {@link PosixSupportLibrary#getPathAsString(Object, Object)} or
     *         {@link PosixSupportLibrary#getPathAsBytes(Object, Object)}
     * @see "inet(3) man pages"
     */
    public abstract Object inet_ntoa(Object receiver, int address);

    /**
     * Corresponds to POSIX {@code inet_pton} function.
     *
     * @param family {@code AF_INET} or {@code AF_INET6}
     * @param src opaque string (converted using createPathFromBytes or createPathFromString)
     * @return the binary address in network order (4 bytes for {@code AF_INET}, 16 bytes for
     *         {@code AF_INET6})
     * @throws PosixException with {@code EAFNOSUPPORT} if the {@code family} is not supported
     * @throws InvalidAddressException if {@code src} is not a valid representation of an address of
     *             given family
     */
    public abstract byte[] inet_pton(Object receiver, int family, Object src) throws PosixException, InvalidAddressException;

    /**
     * Corresponds to POSIX {@code inet_ntop} function.
     *
     * @param family {@code AF_INET} or {@code AF_INET6}
     * @param src the address in network order, must be at least 4 (for {@code AF_INET}) or 16 (for
     *            {@code AF_INET6}) bytes long, extra bytes are ignored
     * @return an opaque string to be converted using getPathAsString or getPathAsBytes
     * @throws PosixException with {@code EAFNOSUPPORT} if the {@code family} is not supported
     * @throws IllegalArgumentException if {@code src} does not satisfy the requirements stated
     *             above
     */
    public abstract Object inet_ntop(Object receiver, int family, byte[] src) throws PosixException;

    /**
     * @return an opaque string to be converted using getPathAsString or getPathAsBytes
     */
    public abstract Object gethostname(Object receiver) throws PosixException;

    /**
     * Corresponds to POSIX {@code getnameinfo(3)}, except it always retrieves both host and service
     * names.
     *
     * @param addr socket address to convert
     * @param flags a combination of {@code NI_xxx} flags
     * @return an array of two (host, service) opaque strings to be converted using getPathAsString
     *         or getPathAsBytes
     * @throws GetAddrInfoException when an error occurs (PosixException is not thrown because
     *             getnameinfo uses its own error codes and gai_strerror instead of the usual errno
     *             and strerror)
     */
    public abstract Object[] getnameinfo(Object receiver, UniversalSockAddr addr, int flags) throws GetAddrInfoException;

    /**
     * Corresponds to POSIX {@code getaddrinfo(3)}, except it always passes a non-null value for the
     * {@code hints} parameter.
     *
     * @param node {@code null} or the host name converted using
     *            {@link PosixSupportLibrary#createPathFromBytes(Object, byte[])} or
     *            {@link PosixSupportLibrary#createPathFromString(Object, TruffleString)}
     * @param service {@code null} or the service name converted using
     *            {@link PosixSupportLibrary#createPathFromBytes(Object, byte[])} or
     *            {@link PosixSupportLibrary#createPathFromString(Object, TruffleString)}
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
     * Corresponds to POSIX crypt function that hashes passwords with salt.
     *
     * @param word password to be hashed
     * @param salt random salt, optionally prefixed with $DIGIT$ hash method indication
     * @return hashed password
     * @throws PosixException when an error occurs in the underlying crypt call
     * @see "crypt(3) manpage"
     */
    public abstract TruffleString crypt(Object receiver, TruffleString word, TruffleString salt) throws PosixException;

    /**
     * Provides messages for manipulating {@link AddrInfoCursor}.
     */
    @GenerateLibrary
    public abstract static class AddrInfoCursorLibrary extends Library {

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

        public abstract UniversalSockAddr getSockAddr(AddrInfoCursor receiver);

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
     * Allocates a new {@link UniversalSockAddr} and initializes it with the provided address.
     */
    public abstract UniversalSockAddr createUniversalSockAddrInet4(Object receiver, Inet4SockAddr src);

    public abstract UniversalSockAddr createUniversalSockAddrInet6(Object receiver, Inet6SockAddr src);

    public abstract UniversalSockAddr createUniversalSockAddrUnix(Object receiver, UnixSockAddr src) throws InvalidUnixSocketPathException;

    /**
     * Provides messages for manipulating {@link UniversalSockAddr}.
     */
    @GenerateLibrary
    public abstract static class UniversalSockAddrLibrary extends Library {

        protected UniversalSockAddrLibrary() {
        }

        /**
         * Returns the socket family of the address (one of the {@code AF_xxx} values defined in
         * {@link PosixConstants}).
         */
        public abstract int getFamily(UniversalSockAddr receiver);

        /**
         * Converts the address represented by the receiver (which must be of the
         * {@link PosixConstants#AF_INET} family) into a {@link Inet4SockAddr} instance.
         *
         * @throws IllegalArgumentException if the socket family of the address is not
         *             {@link PosixConstants#AF_INET}
         */
        public abstract Inet4SockAddr asInet4SockAddr(UniversalSockAddr receiver);

        /**
         * Converts the address represented by the receiver (which must be of the
         * {@link PosixConstants#AF_INET6} family) into a {@link Inet6SockAddr} instance.
         *
         * @throws IllegalArgumentException if the socket family of the address is not
         *             {@link PosixConstants#AF_INET6}
         */
        public abstract Inet6SockAddr asInet6SockAddr(UniversalSockAddr receiver);

        /**
         * Converts the address represented by the receiver (which must be of the
         * {@link PosixConstants#AF_UNIX} family) into a {@link UnixSockAddr} instance.
         *
         * @throws IllegalArgumentException if the socket family of the address is not
         *             {@link PosixConstants#AF_UNIX}
         */
        public abstract UnixSockAddr asUnixSockAddr(UniversalSockAddr receiver);

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
    public static final class PosixException extends Exception {

        private static final long serialVersionUID = -115762483478883093L;

        private final int errorCode;
        private final transient TruffleString msg;

        public PosixException(int errorCode, TruffleString message) {
            this.errorCode = errorCode;
            msg = message;
        }

        public final TruffleString getMessageAsTruffleString() {
            return msg;
        }

        @Override
        public String getMessage() {
            return msg.toJavaStringUncached();
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
