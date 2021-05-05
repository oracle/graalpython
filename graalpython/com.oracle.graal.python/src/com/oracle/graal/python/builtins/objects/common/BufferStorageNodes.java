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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.io.PBytesIOBuffer;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class BufferStorageNodes {
    @ImportStatic(BufferFormat.class)
    public abstract static class UnpackValueNode extends Node {
        public abstract Object execute(BufferFormat format, byte[] bytes, int offset);

        @Specialization(guards = "format == UINT_8")
        static int unpackUnsignedByte(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return bytes[offset] & 0xFF;
        }

        @Specialization(guards = "format == INT_8")
        static int unpackSignedByte(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return bytes[offset];
        }

        @Specialization(guards = "format == INT_16")
        static int unpackSignedShort(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.arrayAccessor.getShort(bytes, offset);
        }

        @Specialization(guards = "format == UINT_16")
        static int unpackUnsignedShort(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.arrayAccessor.getShort(bytes, offset) & 0xFFFF;
        }

        @Specialization(guards = "format == INT_32")
        static int unpackSignedInt(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.arrayAccessor.getInt(bytes, offset);
        }

        @Specialization(guards = "format == UINT_32")
        static long unpackUnsignedInt(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.arrayAccessor.getInt(bytes, offset) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "format == INT_64")
        static long unpackSignedLong(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.arrayAccessor.getLong(bytes, offset);
        }

        @Specialization(guards = "format == UINT_64")
        static Object unpackUnsignedLong(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Cached ConditionProfile needsPIntProfile,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            long signedLong = PythonUtils.arrayAccessor.getLong(bytes, offset);
            if (needsPIntProfile.profile(signedLong < 0)) {
                return factory.createInt(PInt.longToUnsignedBigInteger(signedLong));
            } else {
                return signedLong;
            }
        }

        @Specialization(guards = "format == FLOAT")
        static double unpackFloat(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return Float.intBitsToFloat(PythonUtils.arrayAccessor.getInt(bytes, offset));
        }

        @Specialization(guards = "format == DOUBLE")
        static double unpackDouble(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return Double.longBitsToDouble(PythonUtils.arrayAccessor.getLong(bytes, offset));
        }

        @Specialization(guards = "format == BOOLEAN")
        static boolean unpackBoolean(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return bytes[offset] != 0;
        }

        @Specialization(guards = "format == CHAR")
        static Object unpackChar(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createBytes(new byte[]{bytes[offset]});
        }

        @Specialization(guards = "format == UNICODE")
        static Object unpackUnicode(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Cached PRaiseNode raiseNode) {
            int codePoint = PythonUtils.arrayAccessor.getInt(bytes, offset);
            if (!Character.isValidCodePoint(codePoint)) {
                throw raiseNode.raise(ValueError, ErrorMessages.UNMAPPABLE_CHARACTER);
            }
            return codePointToString(codePoint);
        }

        @TruffleBoundary
        private static String codePointToString(int codePoint) {
            StringBuilder sb = new StringBuilder(2);
            sb.appendCodePoint(codePoint);
            return sb.toString();
        }
    }

    @ImportStatic({BufferFormat.class, PGuards.class})
    public abstract static class PackValueNode extends Node {
        @Child private PRaiseNode raiseNode;

        public abstract void execute(VirtualFrame frame, BufferFormat format, Object object, byte[] bytes, int offset);

        @Specialization(guards = "format == UINT_8")
        void packUnsignedByteInt(@SuppressWarnings("unused") BufferFormat format, int value, byte[] bytes, int offset) {
            if (value < 0 || value > 0xFF) {
                throw raise(OverflowError);
            }
            bytes[offset] = (byte) value;
        }

        @Specialization(guards = "format == UINT_8", replaces = "packUnsignedByteInt")
        void packUnsignedByteGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < 0 || value > 0xFF) {
                throw raise(OverflowError);
            }
            bytes[offset] = (byte) value;
        }

        @Specialization(guards = "format == INT_8", replaces = "packUnsignedByteInt")
        void packSignedByteGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw raise(OverflowError);
            }
            bytes[offset] = (byte) value;
        }

        @Specialization(guards = "format == INT_16")
        void packSignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw raise(OverflowError);
            }
            PythonUtils.arrayAccessor.putShort(bytes, offset, (short) value);
        }

        @Specialization(guards = "format == UINT_16")
        void packUnsignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < 0 || value > (Short.MAX_VALUE << 1) + 1) {
                throw raise(OverflowError);
            }
            PythonUtils.arrayAccessor.putShort(bytes, offset, (short) value);
        }

        @Specialization(guards = "format == INT_32")
        static void packSignedIntInt(@SuppressWarnings("unused") BufferFormat format, int value, byte[] bytes, int offset) {
            PythonUtils.arrayAccessor.putInt(bytes, offset, value);
        }

        @Specialization(guards = "format == INT_32", replaces = "packSignedIntInt")
        void packSignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw raise(OverflowError);
            }
            PythonUtils.arrayAccessor.putInt(bytes, offset, (int) value);
        }

        @Specialization(guards = "format == UINT_32", replaces = "packSignedIntInt")
        void packUnsignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, object);
            if (value < 0 || value > ((long) (Integer.MAX_VALUE) << 1L) + 1L) {
                throw raise(OverflowError);
            }
            PythonUtils.arrayAccessor.putInt(bytes, offset, (int) value);
        }

        @Specialization(guards = "format == INT_64")
        static void packSignedLong(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyLongAsLongNode asLongNode) {
            PythonUtils.arrayAccessor.putLong(bytes, offset, asLongNode.execute(frame, object));
        }

        @Specialization(guards = "format == UINT_64")
        static void packUnsignedLong(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaUnsignedLongNode cast) {
            PythonUtils.arrayAccessor.putLong(bytes, offset, cast.execute(indexNode.execute(frame, object)));
        }

        @Specialization(guards = "format == FLOAT")
        static void packFloat(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            PythonUtils.arrayAccessor.putInt(bytes, offset, Float.floatToRawIntBits((float) asDoubleNode.execute(frame, object)));
        }

        @Specialization(guards = "format == DOUBLE")
        static void packDouble(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            PythonUtils.arrayAccessor.putLong(bytes, offset, Double.doubleToRawLongBits(asDoubleNode.execute(frame, object)));
        }

        @Specialization(guards = "format == BOOLEAN", limit = "2")
        static void packBoolean(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            bytes[offset] = lib.isTrue(object, frame) ? (byte) 1 : (byte) 0;
        }

        @Specialization(guards = "format == CHAR", limit = "2")
        void packChar(@SuppressWarnings("unused") BufferFormat format, PBytes object, byte[] bytes, int offset,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                byte[] value = lib.getBufferBytes(object);
                if (value.length != 1) {
                    throw raise(OverflowError);
                }
                bytes[offset] = value[0];
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = {"format == CHAR", "!isBytes(object)"})
        @SuppressWarnings("unused")
        void packChar(BufferFormat format, Object object, byte[] bytes, int offset) {
            throw raise(TypeError);
        }

        @Specialization(guards = "format == UNICODE")
        void packDouble(@SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            String str = cast.cast(object, "array item must be unicode character");
            if (PString.codePointCount(str, 0, str.length()) == 1) {
                int codePoint = PString.codePointAt(str, 0);
                PythonUtils.arrayAccessor.putInt(bytes, offset, codePoint);
            } else {
                throw raise(TypeError);
            }
        }

        private PException raise(PythonBuiltinClassType type) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(type);
        }
    }

    @GenerateUncached
    public abstract static class GetByteLength extends Node {
        public abstract int execute(Object buffer);

        @Specialization
        static int doBytes(PBytesLike bytes,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(getSequenceStorageNode.execute(bytes));
        }

        @Specialization
        static int doArray(PArray array) {
            return array.getLength() * array.getFormat().bytesize;
        }
    }

    @GenerateUncached
    public abstract static class CopyBytesFromBuffer extends Node {
        public abstract void execute(Object buffer, int srcPos, byte[] dest, int destPos, int length);

        @Specialization
        static void doBytes(PBytesLike src, int srcPos, byte[] dest, int destPos, int length,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.CopyBytesFromByteStorage copyFrom) {
            copyFrom.execute(getSequenceStorageNode.execute(src), srcPos, dest, destPos, length);
        }

        @Specialization
        static void doBytesIOBuffer(PBytesIOBuffer src, int srcPos, byte[] dest, int destPos, int length,
                        @Cached CopyBytesFromBuffer rec) {
            rec.execute(src.getSource().getBuf(), srcPos, dest, destPos, length);
        }

        @Specialization
        static void doArray(PArray src, int srcPos, byte[] dest, int destPos, int length,
                        @Cached PRaiseNode raiseNode) {
            try {
                PythonUtils.arraycopy(src.getBuffer(), srcPos, dest, destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // This can happen when the array gets resized while being exported
                throw raiseNode.raise(IndexError, ErrorMessages.INVALID_BUFFER_ACCESS);
            }
        }
    }

    @GenerateUncached
    public abstract static class CopyBytesToBuffer extends Node {
        public abstract void execute(byte[] src, int srcPos, Object dest, int destPos, int length);

        @Specialization
        static void doBytes(byte[] src, int srcPos, PBytesLike dest, int destPos, int length,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.CopyBytesToByteStorage copyTo) {
            copyTo.execute(src, srcPos, getSequenceStorageNode.execute(dest), destPos, length);
        }

        @Specialization
        static void doArray(byte[] src, int srcPos, PArray dest, int destPos, int length,
                        @Cached PRaiseNode raiseNode) {
            try {
                PythonUtils.arraycopy(src, srcPos, dest.getBuffer(), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // This can happen when the array gets resized while being exported
                throw raiseNode.raise(IndexError, ErrorMessages.INVALID_BUFFER_ACCESS);
            }
        }
    }
}
