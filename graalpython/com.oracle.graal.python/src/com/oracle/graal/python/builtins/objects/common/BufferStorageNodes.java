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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class BufferStorageNodes {

    @ImportStatic(BufferFormat.class)
    @GenerateInline
    @GenerateCached(false)
    public abstract static class UnpackValueNode extends Node {
        public abstract Object execute(Node inliningTarget, BufferFormat format, Object buffer, int offset);

        @Specialization(guards = "format == UINT_8")
        static int unpackUnsignedByte(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(buffer, offset) & 0xFF;
        }

        @Specialization(guards = "format == INT_8")
        static int unpackSignedByte(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(buffer, offset);
        }

        @Specialization(guards = "format == INT_16")
        static int unpackSignedShort(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readShort(buffer, offset);
        }

        @Specialization(guards = "format == UINT_16")
        static int unpackUnsignedShort(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readShort(buffer, offset) & 0xFFFF;
        }

        @Specialization(guards = "format == INT_32")
        static int unpackSignedInt(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readInt(buffer, offset);
        }

        @Specialization(guards = "format == UINT_32")
        static long unpackUnsignedInt(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readInt(buffer, offset) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "format == INT_64")
        static long unpackSignedLong(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readLong(buffer, offset);
        }

        @Specialization(guards = "format == UINT_64")
        static Object unpackUnsignedLong(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile needsPIntProfile,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            long signedLong = bufferLib.readLong(buffer, offset);
            if (needsPIntProfile.profile(inliningTarget, signedLong < 0)) {
                return factory.createInt(PInt.longToUnsignedBigInteger(signedLong));
            } else {
                return signedLong;
            }
        }

        @Specialization(guards = "format == FLOAT")
        static double unpackFloat(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readFloat(buffer, offset);
        }

        @Specialization(guards = "format == DOUBLE")
        static double unpackDouble(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readDouble(buffer, offset);
        }

        @Specialization(guards = "format == BOOLEAN")
        static boolean unpackBoolean(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.readByte(buffer, offset) != 0;
        }

        @Specialization(guards = "format == CHAR")
        static PBytes unpackChar(@SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            return factory.createBytes(new byte[]{bufferLib.readByte(buffer, offset)});
        }

        @Specialization(guards = "format == UNICODE")
        static TruffleString unpackUnicode(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached(inline = false) TruffleString.FromCodePointNode fromCodePointNode) {
            int codePoint = bufferLib.readInt(buffer, offset);
            if (!Character.isValidCodePoint(codePoint)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.UNMAPPABLE_CHARACTER);
            }
            return fromCodePointNode.execute(codePoint, TS_ENCODING, true);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({BufferFormat.class, PGuards.class})
    public abstract static class PackValueNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, BufferFormat format, Object object, Object buffer, int offset);

        @Specialization(guards = "format == UINT_8")
        static void packUnsignedByteInt(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, int value, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (value < 0 || value > 0xFF) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeByte(buffer, offset, (byte) value);
        }

        @Specialization(guards = "format == UINT_8", replaces = "packUnsignedByteInt")
        static void packUnsignedByteGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < 0 || value > 0xFF) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeByte(buffer, offset, (byte) value);
        }

        @Specialization(guards = "format == INT_8", replaces = "packUnsignedByteInt")
        static void packSignedByteGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeByte(buffer, offset, (byte) value);
        }

        @Specialization(guards = "format == INT_16")
        static void packSignedShortGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeShort(buffer, offset, (short) value);
        }

        @Specialization(guards = "format == UINT_16")
        static void packUnsignedShortGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < 0 || value > (Short.MAX_VALUE << 1) + 1) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeShort(buffer, offset, (short) value);
        }

        @Specialization(guards = "format == INT_32")
        static void packSignedIntInt(@SuppressWarnings("unused") BufferFormat format, int value, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            bufferLib.writeInt(buffer, offset, value);
        }

        @Specialization(guards = "format == INT_32", replaces = "packSignedIntInt")
        static void packSignedIntGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            bufferLib.writeInt(buffer, offset, value);
        }

        @Specialization(guards = "format == UINT_32", replaces = "packSignedIntInt")
        static void packUnsignedIntGeneric(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached CastToJavaLongExactNode castToLong,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            long value = castToLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object));
            if (value < 0 || value > ((long) (Integer.MAX_VALUE) << 1L) + 1L) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            bufferLib.writeInt(buffer, offset, (int) value);
        }

        @Specialization(guards = "format == INT_64")
        static void packSignedLong(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached CastToJavaLongExactNode castToLong) {
            long value = castToLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object));
            bufferLib.writeLong(buffer, offset, value);
        }

        @Specialization(guards = "format == UINT_64")
        static void packUnsignedLong(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaUnsignedLongNode castToUnsignedLong) {
            long value = castToUnsignedLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object));
            bufferLib.writeLong(buffer, offset, value);
        }

        @Specialization(guards = "format == FLOAT")
        static void packFloat(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            float value = (float) asDoubleNode.execute(frame, inliningTarget, object);
            bufferLib.writeFloat(buffer, offset, value);
        }

        @Specialization(guards = "format == DOUBLE")
        static void packDouble(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            double value = asDoubleNode.execute(frame, inliningTarget, object);
            bufferLib.writeDouble(buffer, offset, value);
        }

        @Specialization(guards = "format == BOOLEAN")
        static void packBoolean(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PyObjectIsTrueNode isTrue) {
            byte value = isTrue.execute(frame, inliningTarget, object) ? (byte) 1 : (byte) 0;
            bufferLib.writeByte(buffer, offset, value);
        }

        @Specialization(guards = "format == CHAR")
        static void packChar(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, PBytes object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (bufferLib.getBufferLength(object) != 1) {
                throw raiseNode.get(inliningTarget).raise(OverflowError);
            }
            byte value = bufferLib.readByte(object, 0);
            bufferLib.writeByte(buffer, offset, value);
        }

        @Specialization(guards = {"format == CHAR", "!isPBytes(object)"})
        @SuppressWarnings("unused")
        static void packChar(Node inliningTarget, BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(TypeError);
        }

        @Specialization(guards = "format == UNICODE")
        static void packDouble(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, Object object, Object buffer, int offset,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString str = cast.cast(inliningTarget, object, ErrorMessages.ARRAY_ITEM_MUST_BE_UNICODE);
            if (codePointLengthNode.execute(str, TS_ENCODING) == 1) {
                int codePoint = codePointAtIndexNode.execute(str, 0, TS_ENCODING);
                bufferLib.writeInt(buffer, offset, codePoint);
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError);
            }
        }
    }
}
