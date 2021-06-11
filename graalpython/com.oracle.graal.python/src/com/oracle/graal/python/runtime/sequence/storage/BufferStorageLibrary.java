package com.oracle.graal.python.runtime.sequence.storage;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;

/**
 * A copy of the buffer APIs from {@link PythonBufferAccessLibrary} and {@link InteropLibrary} used
 * for delegating the API functions to sequence storages that are not interop objects. Generally
 * shouldn't be used unless you're implementing a buffer object that is backed by a
 * {@link SequenceStorage}.
 */
@GenerateLibrary
public abstract class BufferStorageLibrary extends Library {
    public abstract int getBufferLength(Object receiver);

    public boolean hasInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @Abstract(ifExported = "hasInternalByteArray")
    public byte[] getInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        throw CompilerDirectives.shouldNotReachHere("getInternalByteArray");
    }

    public abstract void copyFrom(Object receiver, int srcOffset, byte[] dest, int destOffset, int length);

    public abstract void copyTo(Object receiver, int destOffset, byte[] src, int srcOffset, int length);

    public abstract byte readBufferByte(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferByte(Object receiver, long byteOffset, byte value) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract short readBufferShort(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferShort(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract int readBufferInt(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferInt(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract long readBufferLong(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferLong(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract float readBufferFloat(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferFloat(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract double readBufferDouble(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

    public abstract void writeBufferDouble(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException;
}
