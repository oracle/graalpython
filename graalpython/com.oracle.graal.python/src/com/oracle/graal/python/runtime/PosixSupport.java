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

import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PosixSupport {

    public static final int ST_MODE = 0;

    public static abstract class Path {
    }

    public void setEnv(@SuppressWarnings("unused") Env env) {
        // nop
    }

    public static PosixSupport get(Node node) {
        return PythonContext.get(node).getPosixSupport();
    }

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
}
