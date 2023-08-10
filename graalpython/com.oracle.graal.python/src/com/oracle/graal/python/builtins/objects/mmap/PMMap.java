/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PMMap extends PythonObject {
    public static final int ACCESS_DEFAULT = 0;
    public static final int ACCESS_READ = 1;
    public static final int ACCESS_WRITE = 2;
    public static final int ACCESS_COPY = 3;

    private final MMapRef ref;
    private long pos;
    private final int access;

    public PMMap(Object pythonClass, Shape instanceShape, PythonContext context, Object handle, int fd, long length, int access) {
        super(pythonClass, instanceShape);
        assert handle != null;
        this.ref = new PMMap.MMapRef(this, handle, context.getSharedFinalizer(), fd, length);
        this.access = access;
    }

    public Object getPosixSupportHandle() {
        return ref.getReference();
    }

    void close(PosixSupportLibrary lib, Object posix) {
        ref.close(lib, posix);
    }

    boolean isClosed() {
        return ref.isReleased();
    }

    @ExportMessage
    boolean isReadonly() {
        return !isWriteable();
    }

    public boolean isWriteable() {
        return access != ACCESS_READ;
    }

    public int getAccess() {
        return access;
    }

    public long getLength() {
        return ref.length;
    }

    public long getPos() {
        return pos;
    }

    public void setPos(long pos) {
        this.pos = pos;
    }

    public long getRemaining() {
        return pos < ref.length ? ref.length - pos : 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength(
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode) {
        return castToIntNode.execute(inliningTarget, ref.length);
    }

    @ExportMessage
    byte readByte(int byteOffset,
                    @Bind("$node") Node inliningTarget,
                    @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                    @Shared("raiseNode") @Cached PConstructAndRaiseNode.Lazy raiseNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        try {
            return posixLib.mmapReadByte(PythonContext.get(raiseNode).getPosixSupport(), getPosixSupportHandle(), byteOffset);
        } catch (PosixException e) {
            // TODO(fa) how to handle?
            throw raiseNode.get(inliningTarget).raiseOSError(null, e.getErrorCode(), fromJavaStringNode.execute(e.getMessage(), TS_ENCODING), null, null);
        }
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Bind("$node") Node inliningTarget,
                    @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                    @Shared("raiseNode") @Cached PConstructAndRaiseNode.Lazy raiseNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        try {
            posixLib.mmapWriteByte(PythonContext.get(raiseNode).getPosixSupport(), getPosixSupportHandle(), byteOffset, value);
        } catch (PosixException e) {
            // TODO(fa) how to handle?
            throw raiseNode.get(inliningTarget).raiseOSError(null, e.getErrorCode(), fromJavaStringNode.execute(e.getMessage(), TS_ENCODING), null, null);
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

    static class MMapRef extends AsyncHandler.SharedFinalizer.FinalizableReference {

        final int fd;
        private final long length;

        MMapRef(PMMap referent, Object handle, AsyncHandler.SharedFinalizer finalizer, int fd, long length) {
            super(referent, handle, finalizer);
            this.fd = fd;
            this.length = length;
        }

        @Override
        public AsyncHandler.AsyncAction release() {
            return new MMapBuiltins.ReleaseCallback(this);
        }

        void close(PosixSupportLibrary posixLib, Object posixSupport) {
            if (isReleased()) {
                return;
            }
            markReleased();

            Object handle = getReference();
            if (fd != -1) {
                try {
                    posixLib.close(posixSupport, fd);
                } catch (PosixException e) {
                    // ignored (CPython does not check the return value)
                }
            }
            try {
                posixLib.mmapUnmap(posixSupport, handle, length);
            } catch (PosixException e) {
                // ignored (CPython does not check the return value)
            }
        }
    }

}
