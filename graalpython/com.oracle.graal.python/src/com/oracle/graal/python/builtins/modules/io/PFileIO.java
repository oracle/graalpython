/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.AsyncHandler.SharedFinalizer.FinalizableReference;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.object.Shape;

public final class PFileIO extends PythonBuiltinObject {

    private FD fd;
    private boolean created;
    private boolean readable;
    private boolean writable;
    private boolean appending;
    private int seekable;
    private boolean closefd;
    boolean finalizing;
    private int blksize;

    public PFileIO(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.fd = null;
        this.created = false;
        this.readable = false;
        this.writable = false;
        this.appending = false;
        this.seekable = -1; /* -1 means unknown */
        this.blksize = 0;
        this.closefd = true;
        this.finalizing = false;
    }

    public int getFD() {
        return fd != null ? fd.getFD() : -1;
    }

    public void setFD(int fd, PythonContext context) {
        assert this.fd == null : "'fd' has not been closed!";
        if (closefd) {
            this.fd = new FD(fd, context);
        } else {
            this.fd = new FD(fd);
        }
    }

    public void setClosed() {
        if (fd != null) {
            if (fd.getOwnFD() != null) {
                fd.getOwnFD().markReleased();
            }
            fd = null;
        }
    }

    public boolean isClosed() {
        return fd == null;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated() {
        this.created = true;
    }

    public boolean isReadable() {
        return readable;
    }

    public void setReadable() {
        this.readable = true;
    }

    public boolean isWritable() {
        return writable;
    }

    public void setWritable() {
        this.writable = true;
    }

    public boolean isAppending() {
        return appending;
    }

    public void setAppending() {
        this.appending = true;
    }

    public int getSeekable() {
        return seekable;
    }

    public void setSeekable(int seekable) {
        this.seekable = seekable;
    }

    public boolean isCloseFD() {
        return closefd;
    }

    public void setCloseFD(boolean closefd) {
        this.closefd = closefd;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    public void setFinalizing(boolean finalizing) {
        this.finalizing = finalizing;
    }

    public int getBlksize() {
        return blksize;
    }

    public void setBlksize(int blksize) {
        this.blksize = blksize;
    }
}

@ValueType
final class FD {
    private final int fd;
    private final OwnFD ownFD;

    public FD(int fd, PythonContext context) {
        this.fd = fd;
        if (fd > 2) {
            this.ownFD = new OwnFD(this, fd, context);
        } else {
            /*
             * FD_STDIN(0), FD_STDOUT(1) and FD_STDERR(2) should not be closed using AsyncHandler.
             */
            this.ownFD = null;
        }
    }

    public FD(int fd) {
        this.fd = fd;
        this.ownFD = null;
    }

    public OwnFD getOwnFD() {
        return ownFD;
    }

    public int getFD() {
        return fd;
    }
}

final class OwnFD extends FinalizableReference {

    private final PythonContext context;

    public OwnFD(Object referent, int fd, PythonContext context) {
        super(referent, fd, context.getSharedFinalizer());
        this.context = context;
    }

    @SuppressWarnings("try")
    void doRelease() {
        markReleased();
        try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
            PosixSupportLibrary.getUncached().close(context.getPosixSupport(), (int) getReference());
        } catch (PosixException e) {
            // ignore
        }
    }

    @Override
    public AsyncHandler.AsyncAction release() {
        if (!isReleased()) {
            return new FileIOBuiltins.FDReleaseCallback(this);
        }
        return null;
    }
}
