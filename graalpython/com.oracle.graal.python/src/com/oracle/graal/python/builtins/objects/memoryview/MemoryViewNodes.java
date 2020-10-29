/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class MemoryViewNodes {
    static int bytesize(PMemoryView.BufferFormat format) {
        // TODO fetch from sulong
        switch (format) {
            case UNSIGNED_BYTE:
            case SIGNED_BYTE:
            case CHAR:
            case BOOLEAN:
                return 1;
            case UNSIGNED_SHORT:
            case SIGNED_SHORT:
                return 2;
            case SIGNED_INT:
            case UNSIGNED_INT:
            case FLOAT:
                return 4;
            case UNSIGNED_LONG:
            case SIGNED_LONG:
            case DOUBLE:
                return 8;
        }
        return -1;
    }

    static boolean isByteFormat(PMemoryView.BufferFormat format) {
        return format == PMemoryView.BufferFormat.UNSIGNED_BYTE || format == PMemoryView.BufferFormat.SIGNED_BYTE || format == PMemoryView.BufferFormat.CHAR;
    }

    public abstract static class InitFlagsNode extends Node {
        public abstract int execute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets);

        @Specialization
        static int compute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets) {
            if (ndim == 0) {
                return PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN | PMemoryView.FLAG_SCALAR;
            } else if (suboffsets != null) {
                return PMemoryView.FLAG_PIL;
            } else {
                int flags = PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN;
                int expectedStride = itemsize;
                for (int i = ndim - 1; i >= 0; i--) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_C;
                        break;
                    }
                    expectedStride *= dim;
                }
                expectedStride = itemsize;
                for (int i = 0; i < ndim; i++) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_FORTRAN;
                        break;
                    }
                    expectedStride *= dim;
                }
                return flags;
            }
        }
    }

    @ImportStatic(PMemoryView.BufferFormat.class)
    abstract static class UnpackValueNode extends Node {
        // bytes are expected to already have the appropriate length
        public abstract Object execute(PMemoryView.BufferFormat format, String formatStr, byte[] bytes);

        @Specialization(guards = "format == UNSIGNED_BYTE")
        static int unpackUnsignedByte(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return bytes[0] & 0xFF;
        }

        @Specialization(guards = "format == SIGNED_BYTE")
        static int unpackSignedByte(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return bytes[0];
        }

        @Specialization(guards = "format == SIGNED_SHORT")
        static int unpackSignedShort(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return unpackInt16(bytes);
        }

        @Specialization(guards = "format == UNSIGNED_SHORT")
        static int unpackUnsignedShort(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return unpackInt16(bytes) & 0xFFFF;
        }

        @Specialization(guards = "format == SIGNED_INT")
        static int unpackSignedInt(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return unpackInt32(bytes);
        }

        @Specialization(guards = "format == UNSIGNED_INT")
        static long unpackUnsignedInt(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return unpackInt32(bytes) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "format == SIGNED_LONG")
        static long unpackSignedLong(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return unpackInt64(bytes);
        }

        @Specialization(guards = "format == UNSIGNED_LONG")
        static Object unpackUnsignedLong(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes,
                        @Cached ConditionProfile needsPIntProfile,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            long signedLong = unpackInt64(bytes);
            if (needsPIntProfile.profile(signedLong < 0)) {
                return factory.createInt(PInt.longToUnsignedBigInteger(signedLong));
            } else {
                return signedLong;
            }
        }

        @Specialization(guards = "format == FLOAT")
        static double unpackFloat(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return Float.intBitsToFloat(unpackInt32(bytes));
        }

        @Specialization(guards = "format == DOUBLE")
        static double unpackDouble(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return Double.longBitsToDouble(unpackInt64(bytes));
        }

        @Specialization(guards = "format == BOOLEAN")
        static boolean unpackBoolean(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes) {
            return bytes[0] != 0;
        }

        @Specialization(guards = "format == CHAR")
        static Object unpackChar(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, byte[] bytes,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            assert bytes.length == 1;
            return factory.createBytes(bytes);
        }

        @Specialization(guards = "format == OTHER")
        static Object notImplemented(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, @SuppressWarnings("unused") byte[] bytes,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }

        private static short unpackInt16(byte[] bytes) {
            return (short) ((bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8);
        }

        private static int unpackInt32(byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
        }

        private static long unpackInt64(byte[] bytes) {
            return (bytes[0] & 0xFFL) | (bytes[1] & 0xFFL) << 8 | (bytes[2] & 0xFFL) << 16 | (bytes[3] & 0xFFL) << 24 |
                            (bytes[4] & 0xFFL) << 32 | (bytes[5] & 0xFFL) << 40 | (bytes[6] & 0xFFL) << 48 | (bytes[7] & 0xFFL) << 56;
        }
    }

    @ImportStatic({PMemoryView.BufferFormat.class, PGuards.class})
    abstract static class PackValueNode extends Node {
        @Child private PRaiseNode raiseNode;
        @Child private IsBuiltinClassProfile isOverflowErrorProfile;

        // Output goes to bytes, lenght not checked
        public abstract void execute(VirtualFrame frame, PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes);

        @Specialization(guards = "format == UNSIGNED_BYTE")
        void packUnsignedByteInt(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, int value, byte[] bytes) {
            assert bytes.length == 1;
            if (value < 0 || value > 0xFF) {
                throw valueError(formatStr);
            }
            bytes[0] = (byte) value;
        }

        @Specialization(guards = "format == UNSIGNED_BYTE", replaces = "packUnsignedByteInt", limit = "2")
        void packUnsignedByteGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            assert bytes.length == 1;
            if (value < 0 || value > 0xFF) {
                throw valueError(formatStr);
            }
            bytes[0] = (byte) value;
        }

        @Specialization(guards = "format == SIGNED_BYTE", replaces = "packUnsignedByteInt", limit = "2")
        void packSignedByteGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            assert bytes.length == 1;
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw valueError(formatStr);
            }
            bytes[0] = (byte) value;
        }

        @Specialization(guards = "format == SIGNED_SHORT", limit = "2")
        void packSignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw valueError(formatStr);
            }
            packInt16((int) value, bytes);
        }

        @Specialization(guards = "format == UNSIGNED_SHORT", limit = "2")
        void packUnsignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            if (value < 0 || value > (Short.MAX_VALUE << 1) + 1) {
                throw valueError(formatStr);
            }
            packInt16((int) value, bytes);
        }

        @Specialization(guards = "format == SIGNED_INT")
        static void packSignedIntInt(@SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, int value, byte[] bytes) {
            packInt32(value, bytes);
        }

        @Specialization(guards = "format == SIGNED_INT", replaces = "packSignedIntInt", limit = "2")
        void packSignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw valueError(formatStr);
            }
            packInt32((int) value, bytes);
        }

        @Specialization(guards = "format == UNSIGNED_INT", replaces = "packSignedIntInt", limit = "2")
        void packUnsignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            long value = asJavaLong(frame, formatStr, object, lib);
            if (value < 0 || value > ((long) (Integer.MAX_VALUE) << 1L) + 1L) {
                throw valueError(formatStr);
            }
            packInt32((int) value, bytes);
        }

        @Specialization(guards = "format == SIGNED_LONG", limit = "2")
        void packSignedLong(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 8;
            packInt64(asJavaLong(frame, formatStr, object, lib), bytes);
        }

        @Specialization(guards = "format == UNSIGNED_LONG", limit = "2")
        void packUnsignedLong(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @Cached CastToJavaUnsignedLongNode cast,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 8;
            try {
                packInt64(cast.execute(lib.asIndexWithFrame(object, frame)), bytes);
            } catch (PException e) {
                throw processException(e, formatStr);
            }
        }

        @Specialization(guards = "format == FLOAT", limit = "2")
        void packFloat(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 4;
            try {
                packInt32(Float.floatToRawIntBits((float) lib.asJavaDouble(object)), bytes);
            } catch (PException e) {
                throw processException(e, formatStr);
            }
        }

        @Specialization(guards = "format == DOUBLE", limit = "2")
        void packDouble(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 8;
            try {
                packInt64(Double.doubleToRawLongBits(lib.asJavaDouble(object)), bytes);
            } catch (PException e) {
                throw processException(e, formatStr);
            }
        }

        @Specialization(guards = "format == BOOLEAN", limit = "2")
        static void packBoolean(VirtualFrame frame, @SuppressWarnings("unused") PMemoryView.BufferFormat format, @SuppressWarnings("unused") String formatStr, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 1;
            bytes[0] = lib.isTrue(object, frame) ? (byte) 1 : (byte) 0;
        }

        @Specialization(guards = "format == CHAR", limit = "2")
        void packChar(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, PBytes object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                byte[] value = lib.getBufferBytes(object);
                if (value.length != 1) {
                    throw valueError(formatStr);
                }
                bytes[0] = value[0];
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = {"format == CHAR", "!isBytes(object)"})
        void packChar(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, @SuppressWarnings("unused") Object object, @SuppressWarnings("unused") byte[] bytes) {
            throw typeError(formatStr);
        }

        @Specialization(guards = "format == OTHER")
        void notImplemented(@SuppressWarnings("unused") PMemoryView.BufferFormat format, String formatStr, @SuppressWarnings("unused") Object object, @SuppressWarnings("unused") byte[] bytes) {
            throw raise(NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }

        private PException valueError(String formatStr) {
            throw raise(ValueError, ErrorMessages.MEMORYVIEW_INVALID_VALUE_FOR_FORMAT_S, formatStr);
        }

        private PException typeError(String formatStr) {
            throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_TYPE_FOR_FORMAT_S, formatStr);
        }

        private PException processException(PException e, String formatStr) {
            e.expect(OverflowError, getIsOverflowErrorProfile());
            throw valueError(formatStr);
        }

        private long asJavaLong(VirtualFrame frame, String formatStr, Object object, PythonObjectLibrary lib) {
            try {
                return lib.asJavaLong(object, frame);
            } catch (PException e) {
                throw processException(e, formatStr);
            }
        }

        private static void packInt16(int value, byte[] bytes) {
            assert bytes.length == 2;
            bytes[0] = (byte) value;
            bytes[1] = (byte) (value >> 8);
        }

        private static void packInt32(int value, byte[] bytes) {
            assert bytes.length == 4;
            bytes[0] = (byte) value;
            bytes[1] = (byte) (value >> 8);
            bytes[2] = (byte) (value >> 16);
            bytes[3] = (byte) (value >> 24);
        }

        private static void packInt64(long value, byte[] bytes) {
            assert bytes.length == 8;
            bytes[0] = (byte) value;
            bytes[1] = (byte) (value >> 8);
            bytes[2] = (byte) (value >> 16);
            bytes[3] = (byte) (value >> 24);
            bytes[4] = (byte) (value >> 32);
            bytes[5] = (byte) (value >> 40);
            bytes[6] = (byte) (value >> 48);
            bytes[7] = (byte) (value >> 56);
        }

        private PException raise(PythonBuiltinClassType type, String message, Object... args) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(type, message, args);
        }

        private IsBuiltinClassProfile getIsOverflowErrorProfile() {
            if (isOverflowErrorProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isOverflowErrorProfile = insert(IsBuiltinClassProfile.create());
            }
            return isOverflowErrorProfile;
        }
    }

    @GenerateUncached
    abstract static class ReadBytesAtNode extends Node {
        public abstract void execute(byte[] dest, int destOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < cachedLen; i++) {
                    dest[destOffset + i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] dest, int destOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    dest[destOffset + i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doManagedCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < cachedLen; i++) {
                dest[destOffset + i] = (byte) getItemNode.executeInt(storage, offset + i);
            }
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached")
        static void doManagedGeneric(byte[] dest, int destOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < len; i++) {
                dest[destOffset + i] = (byte) getItemNode.executeInt(storage, offset + i);
            }
        }
    }

    @GenerateUncached
    abstract static class WriteBytesAtNode extends Node {
        public abstract void execute(byte[] src, int srcOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < cachedLen; i++) {
                    lib.writeArrayElement(ptr, offset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] src, int srcOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(ptr, offset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doManagedCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < cachedLen; i++) {
                setItemNode.execute(storage, offset + i, src[srcOffset + i]);
            }
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached")
        static void doManagedGeneric(byte[] src, int srcOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < len; i++) {
                setItemNode.execute(storage, offset + i, src[srcOffset + i]);
            }
        }
    }

    abstract static class ReadItemAtNode extends Node {
        public abstract Object execute(PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static Object doNativeCached(PMemoryView self, Object ptr, int offset,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached UnpackValueNode unpackValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            try {
                for (int i = 0; i < cachedItemSize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes);
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static Object doNativeGeneric(PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            try {
                for (int i = 0; i < itemSize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes);
        }

        @Specialization(guards = {"ptr == null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static Object doManagedCached(PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached UnpackValueNode unpackValueNode) {
            // TODO assumes byte storage
            byte[] bytes = new byte[cachedItemSize];
            for (int i = 0; i < cachedItemSize; i++) {
                bytes[i] = (byte) getItemNode.executeInt(getStorageNode.execute(self.getOwner()), offset + i);
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes);
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached")
        static Object doManagedGeneric(PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached UnpackValueNode unpackValueNode) {
            // TODO assumes byte storage
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            for (int i = 0; i < itemSize; i++) {
                bytes[i] = (byte) getItemNode.executeInt(getStorageNode.execute(self.getOwner()), offset + i);
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes);
        }
    }

    abstract static class WriteItemAtNode extends Node {
        public abstract void execute(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object);

        @Specialization(guards = {"ptr != null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PackValueNode packValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes);
            try {
                for (int i = 0; i < cachedItemSize; i++) {
                    lib.writeArrayElement(ptr, offset + i, bytes[i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PackValueNode packValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes);
            try {
                for (int i = 0; i < itemSize; i++) {
                    lib.writeArrayElement(ptr, offset + i, bytes[i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static void doManagedCached(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @Cached PackValueNode packValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes bytes storage
            byte[] bytes = new byte[cachedItemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes);
            for (int i = 0; i < cachedItemSize; i++) {
                setItemNode.execute(getStorageNode.execute(self.getOwner()), offset + i, bytes[i]);
            }
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached")
        static void doManagedGeneric(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @Cached PackValueNode packValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes bytes storage
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes);
            for (int i = 0; i < itemSize; i++) {
                setItemNode.execute(getStorageNode.execute(self.getOwner()), offset + i, bytes[i]);
            }
        }
    }

    @ValueType
    static class MemoryPointer {
        public Object ptr;
        public int offset;

        public MemoryPointer(Object ptr, int offset) {
            this.ptr = ptr;
            this.offset = offset;
        }
    }

    @ImportStatic(PGuards.class)
    abstract static class PointerLookupNode extends Node {
        @Child private PRaiseNode raiseNode;
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;
        @Child private PythonObjectLibrary indexLib;
        @CompilationFinal private ConditionProfile hasSuboffsetsProfile;

        // index can be a tuple, int or int-convertible
        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, Object index);

        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, int index);

        private void lookupDimension(PMemoryView self, MemoryPointer ptr, int dim, int initialIndex) {
            int index = initialIndex;
            int[] shape = self.getBufferShape();
            int nitems = shape[dim];
            if (index < 0) {
                index += nitems;
            }
            if (index < 0 || index >= nitems) {
                throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
            }

            ptr.offset += self.getBufferStrides()[dim] * index;

            int[] suboffsets = self.getBufferSuboffsets();
            if (getHasSuboffsetsProfile().profile(suboffsets != null) && suboffsets[dim] >= 0) {
                // The length may be out of bounds, but sulong shouldn't care if we don't
                // access the out-of-bound part
                ptr.ptr = getCallCapiFunction().call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr.ptr, ptr.offset, suboffsets[dim], self.getLength());
                ptr.offset = 0;
            }
        }

        @Specialization(guards = "self.getDimensions() == 1")
        MemoryPointer resolveInt(PMemoryView self, int index) {
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            lookupDimension(self, ptr, 0, index);
            return ptr;
        }

        @Specialization(guards = "self.getDimensions() != 1")
        MemoryPointer resolveIntError(PMemoryView self, @SuppressWarnings("unused") int index) {
            if (self.getDimensions() == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            // CPython doesn't implement this either, as of 3.8
            throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
        }

        @Specialization(guards = {"cachedDimensions == self.getDimensions()", "cachedDimensions <= 8"})
        @ExplodeLoop
        MemoryPointer resolveTupleCached(PMemoryView self, PTuple indices,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            checkTupleLength(lenNode, indicesStorage, cachedDimensions);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < cachedDimensions; dim++) {
                Object indexObj = getItemNode.execute(indicesStorage, dim);
                int index = convertIndex(indexObj);
                lookupDimension(self, ptr, dim, index);
            }
            return ptr;
        }

        @Specialization(replaces = "resolveTupleCached")
        MemoryPointer resolveTupleGeneric(PMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            int ndim = self.getDimensions();
            checkTupleLength(lenNode, indicesStorage, ndim);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < ndim; dim++) {
                Object indexObj = getItemNode.execute(indicesStorage, dim);
                int index = convertIndex(indexObj);
                lookupDimension(self, ptr, dim, index);
            }
            return ptr;
        }

        @Specialization(guards = "!isPTuple(indexObj)")
        MemoryPointer resolveInt(PMemoryView self, Object indexObj) {
            return resolveInt(self, convertIndex(indexObj));
        }

        private void checkTupleLength(SequenceStorageNodes.LenNode lenNode, SequenceStorage indicesStorage, int ndim) {
            int length = lenNode.execute(indicesStorage);
            if (length == ndim) {
                return;
            }
            // Error cases
            if (ndim == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            } else if (length > ndim) {
                throw raise(TypeError, ErrorMessages.CANNOT_INDEX_D_DIMENSION_VIEW_WITH_D, ndim, length);
            } else {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.SUB_VIEWS_NOT_IMPLEMENTED);
            }
        }

        private int convertIndex(Object indexObj) {
            if (!getIndexLib().canBeIndex(indexObj)) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
            }
            return getIndexLib().asSize(indexObj, IndexError);
        }

        private PythonObjectLibrary getIndexLib() {
            if (indexLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexLib = insert(PythonObjectLibrary.getFactory().createDispatched(3));
            }
            return indexLib;
        }

        private ConditionProfile getHasSuboffsetsProfile() {
            if (hasSuboffsetsProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasSuboffsetsProfile = ConditionProfile.create();
            }
            return hasSuboffsetsProfile;
        }

        private PException raise(PythonBuiltinClassType type, String message, Object... args) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(type, message, args);
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @GenerateUncached
    public abstract static class ToJavaBytesNode extends Node {
        public abstract byte[] execute(PMemoryView self);

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"})
        byte[] tobytesCached(PMemoryView self,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            byte[] bytes = new byte[self.getLength()];
            if (cachedDimensions == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convert(bytes, self, cachedDimensions, readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @Specialization(replaces = "tobytesCached")
        byte[] tobytesGeneric(PMemoryView self,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            byte[] bytes = new byte[self.getLength()];
            if (self.getDimensions() == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convertBoundary(bytes, self, self.getDimensions(), readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @TruffleBoundary
        private void convertBoundary(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            convert(dest, self, ndim, readBytesAtNode, callCapiFunction);
        }

        protected void convert(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static int recursive(byte[] dest, int initialDestOffset, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                    destOffset += self.getItemSize();
                } else {
                    destOffset = recursive(dest, destOffset, self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                offset += self.getBufferStrides()[dim];
            }
            return destOffset;
        }

        public static ToJavaBytesNode create() {
            return MemoryViewNodesFactory.ToJavaBytesNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class ToJavaBytesFortranOrderNode extends ToJavaBytesNode {
        @Override
        protected void convert(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self.getItemSize(), self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static void recursive(byte[] dest, int initialDestOffset, int destStride, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                } else {
                    recursive(dest, destOffset, destStride * self.getBufferShape()[dim], self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                destOffset += destStride;
                offset += self.getBufferStrides()[dim];
            }
        }

        public static ToJavaBytesFortranOrderNode create() {
            return MemoryViewNodesFactory.ToJavaBytesFortranOrderNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class ReleaseBufferOfManagedObjectNode extends Node {
        public abstract void execute(Object object);

        @Specialization
        static void bytearray(@SuppressWarnings("unused") PByteArray object) {
            // TODO
        }

        public static ReleaseBufferOfManagedObjectNode create() {
            return MemoryViewNodesFactory.ReleaseBufferOfManagedObjectNodeGen.create();
        }

        public static ReleaseBufferOfManagedObjectNode getUncached() {
            return MemoryViewNodesFactory.ReleaseBufferOfManagedObjectNodeGen.getUncached();
        }
    }

    public abstract static class GetBufferReferences extends Node {
        public abstract BufferReferences execute();

        @Specialization
        @SuppressWarnings("unchecked")
        static BufferReferences getRefs(@CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ReadAttributeFromObjectNode readNode) {
            return (BufferReferences) readNode.execute(context.getCore().lookupType(PythonBuiltinClassType.PMemoryView), MemoryViewBuiltins.bufferReferencesKey);
        }
    }

    public static class BufferReferences {
        public final ReferenceQueue<PMemoryView> queue = new ReferenceQueue<>();
        public final Set<BufferReference> set = new HashSet<>();
    }
}
