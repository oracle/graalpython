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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
// TODO array access messages
@ExportLibrary(InteropLibrary.class)
public final class PMemoryView extends PythonBuiltinObject {
    public static final int MAX_DIM = 64;

    public static final int FLAG_RELEASED = 0x001;
    public static final int FLAG_C = 0x002;
    public static final int FLAG_FORTRAN = 0x004;
    public static final int FLAG_SCALAR = 0x008;
    public static final int FLAG_PIL = 0x010;

    private Object owner;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final String formatString;
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

    public PMemoryView(Object cls, Shape instanceShape, PythonContext context, ManagedBuffer managedBuffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, String formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        super(cls, instanceShape);
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
        if (managedBuffer != null) {
            this.reference = BufferReference.createBufferReference(this, managedBuffer, context);
        }
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

    public ManagedBuffer getManagedBuffer() {
        return (reference != null) ? reference.getManagedBuffer() : null;
    }

    public Object getOwner() {
        return owner;
    }

    public int getLength() {
        return len;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    public int getItemSize() {
        return itemsize;
    }

    public String getFormatString() {
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
        return (flags & FLAG_C) != 0;
    }

    public boolean isFortranContiguous() {
        return (flags & FLAG_FORTRAN) != 0;
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
        owner = null;
    }

    public void checkReleased(PRaiseNode raiseNode) {
        if (isReleased()) {
            throw raiseNode.raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    public void checkReleased(PNodeWithRaise node) {
        if (isReleased()) {
            throw node.raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return getLength();
    }

    @ExportMessage
    byte[] getBufferBytes(@Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode) {
        return toJavaBytesNode.execute(this);
    }

    @ExportMessage
    Object acquireReadonly(
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        if (!isCContiguous()) {
            throw raiseNode.raise(BufferError, "memoryview: underlying buffer is not C-contiguous");
        }
        return this;
    }

    @ExportMessage
    boolean mayBeWritableBuffer() {
        return !readonly;
    }

    @ExportMessage
    Object acquireWritable(
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        if (!isCContiguous()) {
            throw raiseNode.raise(BufferError, "memoryview: underlying buffer is not C-contiguous");
        }
        if (readonly) {
            throw raiseNode.raise(BufferError, "memoryview: underlying buffer is not writable");
        }
        return this;
    }

    @ExportMessage
    boolean hasInternalByteArray(
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
        assert isCContiguous();
        // Allow direct access only when we have a managed object with no offset
        return bufPointer == null && offset == 0 && bufferLib.hasInternalByteArray(owner);
    }

    @ExportMessage
    byte[] getInternalByteArray(
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
        assert hasInternalByteArray(bufferLib);
        // Delegate to the underlying managed object
        return bufferLib.getInternalByteArray(owner);
    }

    @ExportMessage
    void copyFrom(int srcOffset, byte[] dest, int destOffset, int length,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) {
        assert isCContiguous();
        if (bufPointer == null) {
            // Delegate to the underlying managed object
            bufferLib.copyFrom(owner, offset + srcOffset, dest, destOffset, length);
        } else {
            // Read using the native pointer
            try {
                for (int i = 0; i < length; i++) {
                    dest[destOffset + i] = (byte) interopLib.readArrayElement(bufPointer, offset + srcOffset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }
    }

    @ExportMessage
    void copyTo(int destOffset, byte[] src, int srcOffset, int length,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) {
        assert isCContiguous();
        if (bufPointer == null) {
            // Delegate to the underlying managed object
            bufferLib.copyTo(owner, offset + destOffset, src, srcOffset, length);
        } else {
            // Write using the native pointer
            try {
                for (int i = 0; i < length; i++) {
                    interopLib.writeArrayElement(bufPointer, offset + destOffset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer write failed");
            }
        }
    }

    // Interop buffer API

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBufferWritable() {
        return !readonly;
    }

    @ExportMessage
    long getBufferSize() {
        return getBufferLength();
    }

    private void checkOffsetForInterop(long byteOffset, int elementLen) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isCContiguous()) {
            throw UnsupportedMessageException.create();
        }
        if (byteOffset < 0 || byteOffset + elementLen - 1 >= len) {
            throw InvalidBufferOffsetException.create(byteOffset, len);
        }
    }

    private void checkWritableForInterop() throws UnsupportedMessageException {
        if (readonly) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    byte readBufferByte(long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 1);
        if (bufPointer == null) {
            return interopLib.readBufferByte(owner, offset + byteOffset);
        } else {
            return NativeSequenceStorage.readByteFromNative(bufPointer, byteOffset, interopLib);
        }
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 1);
        if (bufPointer == null) {
            interopLib.writeBufferByte(owner, offset + byteOffset, value);
        } else {
            NativeSequenceStorage.writeByteToNative(bufPointer, byteOffset, value, interopLib);
        }
    }

    @ExportMessage
    short readBufferShort(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 2);
        if (bufPointer == null) {
            return interopLib.readBufferShort(owner, order, offset + byteOffset);
        } else {
            return NativeSequenceStorage.readShortFromNative(bufPointer, order, byteOffset, interopLib);
        }
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 2);
        if (bufPointer == null) {
            interopLib.writeBufferShort(owner, order, offset + byteOffset, value);
        } else {
            NativeSequenceStorage.writeShortToNative(bufPointer, order, byteOffset, value, interopLib);
        }
    }

    @ExportMessage
    int readBufferInt(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 4);
        if (bufPointer == null) {
            return interopLib.readBufferInt(owner, order, offset + byteOffset);
        } else {
            return NativeSequenceStorage.readIntFromNative(bufPointer, order, byteOffset, interopLib);
        }
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 4);
        if (bufPointer == null) {
            interopLib.writeBufferInt(owner, order, offset + byteOffset, value);
        } else {
            NativeSequenceStorage.writeIntToNative(bufPointer, order, byteOffset, value, interopLib);
        }
    }

    @ExportMessage
    long readBufferLong(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 8);
        if (bufPointer == null) {
            return interopLib.readBufferLong(owner, order, offset + byteOffset);
        } else {
            return NativeSequenceStorage.readLongFromNative(bufPointer, order, byteOffset, interopLib);
        }
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 8);
        if (bufPointer == null) {
            interopLib.writeBufferLong(owner, order, offset + byteOffset, value);
        } else {
            NativeSequenceStorage.writeLongToNative(bufPointer, order, byteOffset, value, interopLib);
        }
    }

    @ExportMessage
    float readBufferFloat(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 4);
        if (bufPointer == null) {
            return interopLib.readBufferFloat(owner, order, offset + byteOffset);
        } else {
            return Float.intBitsToFloat(readBufferInt(order, byteOffset, interopLib));
        }
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 4);
        if (bufPointer == null) {
            interopLib.writeBufferFloat(owner, order, offset + byteOffset, value);
        } else {
            writeBufferInt(order, byteOffset, Float.floatToRawIntBits(value), interopLib);
        }
    }

    @ExportMessage
    double readBufferDouble(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 8);
        if (bufPointer == null) {
            return interopLib.readBufferDouble(owner, order, offset + byteOffset);
        } else {
            return Double.longBitsToDouble(readBufferLong(order, byteOffset, interopLib));
        }
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value,
                    @Shared("interopLib") @CachedLibrary(limit = "2") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkWritableForInterop();
        checkOffsetForInterop(byteOffset, 8);
        if (bufPointer == null) {
            interopLib.writeBufferDouble(owner, order, offset + byteOffset, value);
        } else {
            writeBufferLong(order, byteOffset, Double.doubleToRawLongBits(value), interopLib);
        }
    }
}
