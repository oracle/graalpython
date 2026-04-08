/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.buffer;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.nio.ByteOrder;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(value = PythonBufferAcquireLibrary.class, receiverType = Object.class)
final class ForeignBufferAcquireExports {

    @ExportMessage
    static boolean hasBuffer(Object receiver,
                    @CachedLibrary("receiver") InteropLibrary interop) {
        return interop.hasBufferElements(receiver);
    }

    @ExportMessage
    static Object acquire(Object receiver, int flags,
                    @Bind Node inliningTarget,
                    @Cached CastToJavaIntExactNode castInt,
                    @CachedLibrary("receiver") InteropLibrary interop) {
        if (!interop.hasBufferElements(receiver)) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, receiver);
        }

        long bufferSize;
        try {
            bufferSize = interop.getBufferSize(receiver);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }

        boolean readonly;
        try {
            readonly = !interop.isBufferWritable(receiver);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        if (BufferFlags.requestsWritable(flags) && readonly) {
            throw PRaiseNode.raiseStatic(inliningTarget, BufferError, ErrorMessages.OBJ_IS_NOT_WRITABLE);
        }
        return new ForeignBufferAdapter(receiver, castInt.execute(inliningTarget, bufferSize), readonly);
    }

    @ExportLibrary(PythonBufferAccessLibrary.class)
    static final class ForeignBufferAdapter {
        final Object foreignBuffer;
        final int len;
        final boolean readonly;

        ForeignBufferAdapter(Object foreignBuffer, int len, boolean readonly) {
            this.foreignBuffer = foreignBuffer;
            this.len = len;
            this.readonly = readonly;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isBuffer() {
            return true;
        }

        @ExportMessage
        boolean isReadonly() {
            return readonly;
        }

        @ExportMessage
        int getBufferLength() {
            return len;
        }

        @ExportMessage
        Object getOwner() {
            return foreignBuffer;
        }

        @ExportMessage
        byte readByte(int byteOffset,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferByte(foreignBuffer, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeByte(int byteOffset, byte value,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferByte(foreignBuffer, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void readIntoByteArray(int srcOffset, byte[] dest, int destOffset, int length,
                        @Bind Node inliningTarget,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.readBuffer(foreignBuffer, srcOffset, dest, destOffset, length);
            } catch (InvalidBufferOffsetException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, IndexError, ErrorMessages.STRUCT_OFFSET_OUT_OF_RANGE, e.getByteOffset(), len);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void readIntoBuffer(int srcOffset, Object dest, int destOffset, int length, PythonBufferAccessLibrary otherLib,
                        @Bind Node inliningTarget,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            if (otherLib.hasInternalByteArray(dest)) {
                readIntoByteArray(srcOffset, otherLib.getInternalByteArray(dest), destOffset, length, inliningTarget, interop);
            } else {
                for (int i = 0; i < length; i++) {
                    otherLib.writeByte(dest, destOffset + i, readByte(srcOffset + i, interop));
                }
            }
        }

        @ExportMessage
        short readShortByteOrder(int byteOffset, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferShort(foreignBuffer, byteOrder, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        int readIntByteOrder(int byteOffset, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferInt(foreignBuffer, byteOrder, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        long readLongByteOrder(int byteOffset, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferLong(foreignBuffer, byteOrder, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        float readFloatByteOrder(int byteOffset, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferFloat(foreignBuffer, byteOrder, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        double readDoubleByteOrder(int byteOffset, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                return interop.readBufferDouble(foreignBuffer, byteOrder, byteOffset);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeShortByteOrder(int byteOffset, short value, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferShort(foreignBuffer, byteOrder, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeIntByteOrder(int byteOffset, int value, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferInt(foreignBuffer, byteOrder, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeLongByteOrder(int byteOffset, long value, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferLong(foreignBuffer, byteOrder, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeFloatByteOrder(int byteOffset, float value, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferFloat(foreignBuffer, byteOrder, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        void writeDoubleByteOrder(int byteOffset, double value, ByteOrder byteOrder,
                        @CachedLibrary("this.foreignBuffer") InteropLibrary interop) {
            try {
                interop.writeBufferDouble(foreignBuffer, byteOrder, byteOffset, value);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        int getItemSize() {
            return 1;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        TruffleString getFormatString() {
            return BufferFormat.T_UINT_8_TYPE_CODE;
        }
    }
}
