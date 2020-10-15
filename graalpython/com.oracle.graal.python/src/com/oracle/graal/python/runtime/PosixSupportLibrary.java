/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

/**
 * Internal abstraction layer for POSIX functionality. Instance of the implementation is stored in
 * the context. Use {@link PythonContext#getPosixSupport()} to access it.
 */
@GenerateLibrary
public abstract class PosixSupportLibrary extends Library {

    public static final int DEFAULT_DIR_FD = -100;  // TODO C code assumes that this constant is
                                                    // equal to AT_FDCWD

    public static final int EINTR = 4;     // TODO this duplicates OSErrorEnum
    public static final int EINVAL = 22;

    public static final int O_CLOEXEC = 524288;

    public abstract String strerror(Object receiver, int errorCode);

    public abstract long getpid(Object receiver);

    public abstract long umask(Object receiver, long mask);

    public abstract int openAt(Object receiver, int dirFd, PosixPath pathname, int flags, int mode) throws PosixException;

    public abstract void close(Object receiver, int fd) throws PosixException;

    public abstract Buffer read(Object receiver, int fd, long length) throws PosixException;

    public abstract long write(Object receiver, int fd, Buffer data) throws PosixException;

    public abstract int dup(Object receiver, int fd) throws PosixException;

    public abstract int dup2(Object receiver, int fd, int fd2, boolean inheritable) throws PosixException;

    public abstract boolean getInheritable(Object receiver, int fd) throws PosixException;

    public abstract void setInheritable(Object receiver, int fd, boolean inheritable) throws PosixException;

    public static class PosixException extends Exception {

        private static final long serialVersionUID = -115762483478883093L;

        private final int errorCode;
        private final Object filename1;
        private final Object filename2;

        public PosixException(int errorCode, String message) {
            this(errorCode, message, null, null);
        }

        public PosixException(int errorCode, String message, Object filename) {
            this(errorCode, message, filename, null);
        }

        public PosixException(int errorCode, String message, Object filename1, Object filename2) {
            super(message);
            this.errorCode = errorCode;
            this.filename1 = filename1;
            this.filename2 = filename2;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public Object getFilename1() {
            return filename1;
        }

        public Object getFilename2() {
            return filename2;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

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
    }

    /**
     * Represents the result of {@code path_t} conversion. Similar to CPython's {@code path_t}
     * structure, but only contains the results of the conversion, not the conversion parameters.
     */
    public abstract static class PosixFileHandle {

        public static final PosixFileHandle DEFAULT = new PosixFileHandle() {
        };

        /**
         * Contains the original object (or the object returned by {@code __fspath__}) for auditing
         * purposes. This field is {code null} iff the path parameter was optional and the caller
         * did not provide it.
         */
        public final Object originalObject;

        private PosixFileHandle() {
            originalObject = null;
        }

        protected PosixFileHandle(Object originalObject) {
            assert originalObject != null;
            this.originalObject = originalObject;
        }
    }

    /**
     * Contains the path as a sequence of bytes (already fs-encoded, but without the terminating
     * null character).
     */
    public static class PosixPath extends PosixFileHandle {
        public final byte[] path;

        public PosixPath(Object originalObject, byte[] path) {
            super(originalObject);
            assert path != null;
            this.path = path;
        }
    }

    /**
     * Contains the file descriptor if it was allowed in the argument conversion node and the caller
     * provided an integer instead of a path.
     */
    public static class PosixFd extends PosixFileHandle {
        public final int fd;

        public PosixFd(Object originalObject, int fd) {
            super(originalObject);
            this.fd = fd;
        }
    }
}
