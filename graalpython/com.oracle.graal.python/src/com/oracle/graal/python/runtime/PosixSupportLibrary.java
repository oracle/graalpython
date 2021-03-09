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

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.PLATFORM_DARWIN;
import static com.oracle.graal.python.util.PythonUtils.getPythonOSName;

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

    public static final int DEFAULT_DIR_FD = -100;  // TODO C code assumes that this constant is
                                                    // equal to AT_FDCWD

    public static final int O_CLOEXEC = 524288;
    public static final int O_APPEND = 1024;
    public static final int O_TRUNC = 512;
    public static final int O_EXCL = 128;
    public static final int O_CREAT = 64;
    public static final int O_RDWR = 2;
    public static final int O_WRONLY = 1;
    public static final int O_RDONLY = 0;

    public static final char POSIX_FILENAME_SEPARATOR = '/';

    // from stat.h

    /* Encoding of the file mode. */
    public static final int S_IFMT = 0170000; /* These bits determine file type. */

    /* File types. */
    public static final int S_IFDIR = 0040000; /* Directory. */
    public static final int S_IFCHR = 0020000; /* Character device. */
    public static final int S_IFBLK = 0060000; /* Block device. */
    public static final int S_IFREG = 0100000; /* Regular file. */
    public static final int S_IFIFO = 0010000; /* FIFO. */
    public static final int S_IFLNK = 0120000; /* Symbolic link. */
    public static final int S_IFSOCK = 0140000; /* Socket. */

    public static final int MAP_ANONYMOUS = getPythonOSName().equals(PLATFORM_DARWIN) ? 4096 : 0x20;

    // Constants for accessing the fields of the fstat result:
    // TODO: have these in posix.c (maybe posix.h) and extract them along with other constants
    public static final int ST_MODE = 0;
    public static final int ST_SIZE = 6;

    public static final int DT_UNKNOWN = 0;
    public static final int DT_DIR = 4;
    public static final int DT_REG = 8;
    public static final int DT_LNK = 10;

    public static final int LOCK_SH = 1;
    public static final int LOCK_EX = 2;
    public static final int LOCK_NB = 4;
    public static final int LOCK_UN = 8;

    public static final int MAP_SHARED = 0x01;
    public static final int MAP_PRIVATE = 0x02;

    public static final int PROT_READ = 0x1; /* Page can be read. */
    public static final int PROT_WRITE = 0x2; /* Page can be written. */
    public static final int PROT_EXEC = 0x4; /* Page can be executed. */
    public static final int PROT_NONE = 0x0; /* Page can not be accessed. */

    public static final int FD_SETSIZE = 1024;

    public abstract String getBackend(Object recevier);

    public abstract String strerror(Object receiver, int errorCode);

    public abstract long getpid(Object receiver);

    public abstract int umask(Object receiver, int mask) throws PosixException;

    public abstract int openat(Object receiver, int dirFd, Object pathname, int flags, int mode) throws PosixException;

    public abstract void close(Object receiver, int fd) throws PosixException;

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
     *            to set both times to 'now'
     */
    public abstract void utimensat(Object receiver, int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException;

    /**
     * Equivalent of POSIX {@code futimens()}.
     */
    public abstract void futimens(Object receiver, int fd, long[] timespec) throws PosixException;

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
        return (mode & S_IFMT) == mask;
    }

    public static boolean isDIR(long mode) {
        return istype(mode, S_IFDIR);
    }

    public static boolean isCHR(long mode) {
        return istype(mode, S_IFCHR);
    }

    public static boolean isBLK(long mode) {
        return istype(mode, S_IFBLK);
    }

    public static boolean isREG(long mode) {
        return istype(mode, S_IFREG);
    }

    public static boolean isFIFO(long mode) {
        return istype(mode, S_IFIFO);
    }

    public static boolean isLNK(long mode) {
        return istype(mode, S_IFLNK);
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
