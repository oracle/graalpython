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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMemoryViewWrapper;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes.ReleaseBufferNode;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

// TODO interop library
@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PMemoryView extends PythonBuiltinObject {
    public static final int MAX_DIM = 64;

    public static final int FLAG_RELEASED = 0x001;
    public static final int FLAG_C = 0x002;
    public static final int FLAG_FORTRAN = 0x004;
    public static final int FLAG_SCALAR = 0x008;
    public static final int FLAG_PIL = 0x010;

    private Object owner;
    // The buffer object that the PythonBufferAccessLibrary API delegates to
    private Object buffer;
    private boolean shouldReleaseImmediately;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final TruffleString formatString;
    private final BufferFormat format;
    private final int ndim;
    // We cannot easily add numbers to pointers in Java, so the actual pointer is bufPointer +
    // offset
    private final Object bufPointer;
    private final int offset;
    private final int[] shape;
    private final int[] strides;
    private final int[] suboffsets;

    // Count of exports via native buffer interface
    private final AtomicLong exports = new AtomicLong();
    // Phantom ref to this object that will decref/release the managed buffer if any
    private BufferReference reference;
    private int flags;

    // Cached hash value, required to comply with CPython's semantics
    private int cachedHash = -1;

    public PMemoryView(Object cls, Shape instanceShape, PythonContext context, BufferLifecycleManager bufferLifecycleManager, Object buffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, TruffleString formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        super(cls, instanceShape);
        PythonBufferAccessLibrary.assertIsBuffer(buffer);
        this.buffer = buffer;
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = format;
        this.formatString = formatString;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.offset = offset;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
        this.flags = flags;
        if (bufferLifecycleManager != null) {
            this.reference = BufferReference.createBufferReference(this, bufferLifecycleManager, context);
        }
        setNativeWrapper(new PyMemoryViewWrapper(this));
    }

    // From CPython init_strides_from_shape
    public static int[] initStridesFromShape(int ndim, int itemsize, int[] shape) {
        int[] strides = new int[ndim];
        strides[ndim - 1] = itemsize;
        for (int i = ndim - 2; i >= 0; i--) {
            strides[i] = strides[i + 1] * shape[i + 1];
        }
        return strides;
    }

    public BufferLifecycleManager getLifecycleManager() {
        return (reference != null) ? reference.getLifecycleManager() : null;
    }

    public Object getBuffer() {
        return buffer;
    }

    @Ignore
    public Object getOwner() {
        return owner;
    }

    public int getLength() {
        return len;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    @ExportMessage
    public int getItemSize() {
        return itemsize;
    }

    @ExportMessage
    public TruffleString getFormatString() {
        return formatString;
    }

    public BufferFormat getFormat() {
        return format;
    }

    public int getDimensions() {
        return ndim;
    }

    public Object getBufferPointer() {
        return bufPointer;
    }

    public int getOffset() {
        return offset;
    }

    public int[] getBufferShape() {
        return shape;
    }

    public int[] getBufferStrides() {
        return strides;
    }

    public int[] getBufferSuboffsets() {
        return suboffsets;
    }

    public boolean isReleased() {
        return (flags & FLAG_RELEASED) != 0;
    }

    public boolean isCContiguous() {
        return (flags & (FLAG_C | FLAG_SCALAR)) != 0;
    }

    public boolean isFortranContiguous() {
        return (flags & (FLAG_FORTRAN | FLAG_SCALAR)) != 0;
    }

    public int getFlags() {
        return flags;
    }

    public AtomicLong getExports() {
        return exports;
    }

    public BufferReference getReference() {
        return reference;
    }

    public int getCachedHash() {
        return cachedHash;
    }

    public void setCachedHash(int cachedHash) {
        this.cachedHash = cachedHash;
    }

    public void setReleased() {
        flags |= FLAG_RELEASED;
        if (reference != null) {
            reference.markReleased();
            reference = null;
        }
        buffer = null;
        owner = null;
    }

    public void setShouldReleaseImmediately(boolean shouldReleaseImmediately) {
        this.shouldReleaseImmediately = shouldReleaseImmediately;
    }

    public void checkReleased(Node inliningTarget, PRaiseNode.Lazy raiseNode) {
        if (isReleased()) {
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    boolean checkShouldReleaseBuffer() {
        if (getReference() != null) {
            return getReference().getLifecycleManager().decrementExports() == 0;
        }
        return false;
    }

    void checkExports(PRaiseNode node) {
        long exportsValue = getExports().get();
        if (exportsValue > 0) {
            throw node.raise(BufferError, ErrorMessages.MEMORYVIEW_HAS_D_EXPORTED_BUFFERS, exportsValue);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return getLength();
    }

    @ExportMessage
    Object acquire(int requestedFlags,
                    @Bind("$node") Node inliningTarget,
                    @Cached PRaiseNode.Lazy raiseNode) {
        checkReleased(inliningTarget, raiseNode);
        if (BufferFlags.requestsWritable(requestedFlags) && readonly) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_WRITABLE);
        }
        if (BufferFlags.requestsCContiguous(requestedFlags) && !isCContiguous()) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_C_CONTIGUOUS);
        }
        if (BufferFlags.requestsFContiguous(requestedFlags) && !isFortranContiguous()) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_FORTRAN_CONTIGUOUS);
        }
        if (BufferFlags.requestsAnyContiguous(requestedFlags) && !isCContiguous() && !isFortranContiguous()) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_CONTIGUOUS);
        }
        if (!BufferFlags.requestsIndirect(requestedFlags) && (flags & FLAG_PIL) != 0) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_REQUIRES_SUBOFFSETS);
        }
        if (!BufferFlags.requestsStrides(requestedFlags) && !isCContiguous()) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_UNDERLYING_BUF_ISNT_C_CONTIGUOUS);
        }
        // TODO should reflect the cast to unsigned bytes if necessary
        if (!BufferFlags.requestsShape(requestedFlags) && BufferFlags.requestsFormat(requestedFlags)) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.MV_CANNOT_CAST_UNSIGNED_BYTES_IF_FMT_FLAG);
        }
        exports.incrementAndGet();
        return this;
    }

    @ExportMessage
    void release(
                    @Bind("$node") Node inliningTarget,
                    @Cached PRaiseNode raiseNode,
                    @Cached ReleaseBufferNode releaseNode) {
        /*
         * This is a bit hacky - the shouldReleaseImmediately marker is used when this is a helper
         * memoryview that was created to hold a buffer for native object. In the future there
         * should be no such helper memoryviews, the C buffer should have a separate implementation.
         */
        if (shouldReleaseImmediately) {
            checkExports(raiseNode);
            if (checkShouldReleaseBuffer()) {
                releaseNode.execute(inliningTarget, getLifecycleManager());
            }
            setReleased();
        } else {
            long l = exports.decrementAndGet();
            assert l >= 0;
        }
    }

    @ExportMessage
    boolean isReadonly() {
        return readonly;
    }

    @ExportMessage
    boolean hasInternalByteArray(
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        // Allow direct access only when we have no offset
        return offset == 0 && bufferLib.hasInternalByteArray(buffer);
    }

    @ExportMessage
    byte[] getInternalByteArray(
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert hasInternalByteArray(bufferLib);
        return bufferLib.getInternalByteArray(buffer);
    }

    @ExportMessage
    void readIntoByteArray(int srcOffset, byte[] dest, int destOffset, int length,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        bufferLib.readIntoByteArray(buffer, offset + srcOffset, dest, destOffset, length);
    }

    @ExportMessage
    void writeFromByteArray(int destOffset, byte[] src, int srcOffset, int length,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        bufferLib.writeFromByteArray(buffer, offset + destOffset, src, srcOffset, length);
    }

    @ExportMessage
    void readIntoBuffer(int srcOffset, Object dest, int destOffset, int length, PythonBufferAccessLibrary otherLib,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        bufferLib.readIntoBuffer(buffer, offset + srcOffset, dest, destOffset, length, otherLib);
    }

    @ExportMessage
    byte readByte(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readByte(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        assert !readonly;
        bufferLib.writeByte(buffer, offset + byteOffset, value);
    }

    @ExportMessage
    short readShort(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readShort(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeShort(int byteOffset, short value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        assert !readonly;
        bufferLib.writeShort(buffer, offset + byteOffset, value);
    }

    @ExportMessage
    int readInt(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readInt(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeInt(int byteOffset, int value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        assert !readonly;
        bufferLib.writeInt(buffer, offset + byteOffset, value);
    }

    @ExportMessage
    long readLong(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readLong(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeLong(int byteOffset, long value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        assert !readonly;
        bufferLib.writeLong(buffer, offset + byteOffset, value);
    }

    @ExportMessage
    float readFloat(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readFloat(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeFloat(int byteOffset, float value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert !readonly;
        bufferLib.writeFloat(buffer, offset + byteOffset, value);
    }

    @ExportMessage
    double readDouble(int byteOffset,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        return bufferLib.readDouble(buffer, offset + byteOffset);
    }

    @ExportMessage
    void writeDouble(int byteOffset, double value,
                    @Shared("bufferLib") @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous() && !isReleased();
        assert !readonly;
        bufferLib.writeDouble(buffer, offset + byteOffset, value);
    }
}
