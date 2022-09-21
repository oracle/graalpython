/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.mmap;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PMMap extends PythonObject {
    public static final int ACCESS_DEFAULT = 0;
    public static final int ACCESS_READ = 1;
    public static final int ACCESS_WRITE = 2;
    public static final int ACCESS_COPY = 3;

    private Object handle;
    private long pos;
    private final int fd; // -1 for anonymous mapping
    private final long length;
    private final int access;

    public PMMap(Object pythonClass, Shape instanceShape, Object handle, int fd, long length, int access) {
        super(pythonClass, instanceShape);
        assert handle != null;
        this.handle = handle;
        this.length = length;
        this.access = access;
        this.fd = fd;
    }

    public Object getPosixSupportHandle() {
        return handle;
    }

    void close(PosixSupportLibrary lib, Object posix) throws PosixException {
        if (handle != null) {
            if (fd != -1) {
                lib.close(posix, fd);
            }
            lib.mmapUnmap(posix, handle, length);
            handle = null;
        }
    }

    boolean isClosed() {
        return handle == null;
    }

    public boolean isWriteable() {
        return access != ACCESS_READ;
    }

    public int getAccess() {
        return access;
    }

    public long getLength() {
        return length;
    }

    public long getPos() {
        return pos;
    }

    public void setPos(long pos) {
        this.pos = pos;
    }

    public long getRemaining() {
        return pos < length ? length - pos : 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength(
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode) {
        return castToIntNode.execute(length);
    }

    @ExportMessage
    byte readByte(int byteOffset,
                    @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                    @Cached BranchProfile gotException,
                    @Cached PConstructAndRaiseNode raiseNode,
                    @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        try {
            return posixLib.mmapReadByte(PythonContext.get(raiseNode).getPosixSupport(), getPosixSupportHandle(), byteOffset);
        } catch (PosixException e) {
            // TODO(fa) how to handle?
            gotException.enter();
            throw raiseNode.raiseOSError(null, e.getErrorCode(), fromJavaStringNode.execute(e.getMessage(), TS_ENCODING), null, null);
        }
    }

    @ExportMessage
    Object acquire(@SuppressWarnings("unused") int flags) {
        return this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }
}
