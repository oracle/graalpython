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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
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
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
        public abstract Object execute(Node inliningTarget, BufferFormat format, byte[] bytes, int offset);

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
            return PythonUtils.ARRAY_ACCESSOR.getShort(bytes, offset);
        }

        @Specialization(guards = "format == UINT_16")
        static int unpackUnsignedShort(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.ARRAY_ACCESSOR.getShort(bytes, offset) & 0xFFFF;
        }

        @Specialization(guards = "format == INT_32")
        static int unpackSignedInt(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.ARRAY_ACCESSOR.getInt(bytes, offset);
        }

        @Specialization(guards = "format == UINT_32")
        static long unpackUnsignedInt(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.ARRAY_ACCESSOR.getInt(bytes, offset) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "format == INT_64")
        static long unpackSignedLong(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return PythonUtils.ARRAY_ACCESSOR.getLong(bytes, offset);
        }

        @Specialization(guards = "format == UINT_64")
        static Object unpackUnsignedLong(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Cached InlinedConditionProfile needsPIntProfile,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            long signedLong = PythonUtils.ARRAY_ACCESSOR.getLong(bytes, offset);
            if (needsPIntProfile.profile(inliningTarget, signedLong < 0)) {
                return factory.createInt(PInt.longToUnsignedBigInteger(signedLong));
            } else {
                return signedLong;
            }
        }

        @Specialization(guards = "format == FLOAT")
        static double unpackFloat(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return Float.intBitsToFloat(PythonUtils.ARRAY_ACCESSOR.getInt(bytes, offset));
        }

        @Specialization(guards = "format == DOUBLE")
        static double unpackDouble(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return Double.longBitsToDouble(PythonUtils.ARRAY_ACCESSOR.getLong(bytes, offset));
        }

        @Specialization(guards = "format == BOOLEAN")
        static boolean unpackBoolean(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset) {
            return bytes[offset] != 0;
        }

        @Specialization(guards = "format == CHAR")
        static PBytes unpackChar(@SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            return factory.createBytes(new byte[]{bytes[offset]});
        }

        @Specialization(guards = "format == UNICODE")
        static TruffleString unpackUnicode(Node inliningTarget, @SuppressWarnings("unused") BufferFormat format, byte[] bytes, int offset,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached(inline = false) TruffleString.FromCodePointNode fromCodePointNode) {
            int codePoint = PythonUtils.ARRAY_ACCESSOR.getInt(bytes, offset);
            if (!Character.isValidCodePoint(codePoint)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.UNMAPPABLE_CHARACTER);
            }
            return fromCodePointNode.execute(codePoint, TS_ENCODING, true);
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
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < 0 || value > 0xFF) {
                throw raise(OverflowError);
            }
            bytes[offset] = (byte) value;
        }

        @Specialization(guards = "format == INT_8", replaces = "packUnsignedByteInt")
        void packSignedByteGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw raise(OverflowError);
            }
            bytes[offset] = (byte) value;
        }

        @Specialization(guards = "format == INT_16")
        void packSignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw raise(OverflowError);
            }
            PythonUtils.ARRAY_ACCESSOR.putShort(bytes, offset, (short) value);
        }

        @Specialization(guards = "format == UINT_16")
        void packUnsignedShortGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, object);
            if (value < 0 || value > (Short.MAX_VALUE << 1) + 1) {
                throw raise(OverflowError);
            }
            PythonUtils.ARRAY_ACCESSOR.putShort(bytes, offset, (short) value);
        }

        @Specialization(guards = "format == INT_32")
        static void packSignedIntInt(@SuppressWarnings("unused") BufferFormat format, int value, byte[] bytes, int offset) {
            PythonUtils.ARRAY_ACCESSOR.putInt(bytes, offset, value);
        }

        @Specialization(guards = "format == INT_32", replaces = "packSignedIntInt")
        void packSignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            PythonUtils.ARRAY_ACCESSOR.putInt(bytes, offset, asSizeNode.executeExact(frame, inliningTarget, object));
        }

        @Specialization(guards = "format == UINT_32", replaces = "packSignedIntInt")
        void packUnsignedIntGeneric(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached CastToJavaLongExactNode castToLong) {
            long value = castToLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object));
            if (value < 0 || value > ((long) (Integer.MAX_VALUE) << 1L) + 1L) {
                throw raise(OverflowError);
            }
            PythonUtils.ARRAY_ACCESSOR.putInt(bytes, offset, (int) value);
        }

        @Specialization(guards = "format == INT_64")
        static void packSignedLong(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached CastToJavaLongExactNode castToLong) {
            PythonUtils.ARRAY_ACCESSOR.putLong(bytes, offset, castToLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object)));
        }

        @Specialization(guards = "format == UINT_64")
        static void packUnsignedLong(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaUnsignedLongNode castToUnsignedLong) {
            PythonUtils.ARRAY_ACCESSOR.putLong(bytes, offset, castToUnsignedLong.execute(inliningTarget, indexNode.execute(frame, inliningTarget, object)));
        }

        @Specialization(guards = "format == FLOAT")
        static void packFloat(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            PythonUtils.ARRAY_ACCESSOR.putInt(bytes, offset, Float.floatToRawIntBits((float) asDoubleNode.execute(frame, inliningTarget, object)));
        }

        @Specialization(guards = "format == DOUBLE")
        static void packDouble(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            PythonUtils.ARRAY_ACCESSOR.putLong(bytes, offset, Double.doubleToRawLongBits(asDoubleNode.execute(frame, inliningTarget, object)));
        }

        @Specialization(guards = "format == BOOLEAN")
        static void packBoolean(VirtualFrame frame, @SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrue) {
            bytes[offset] = isTrue.execute(frame, inliningTarget, object) ? (byte) 1 : (byte) 0;
        }

        @Specialization(guards = "format == CHAR", limit = "1")
        void packChar(@SuppressWarnings("unused") BufferFormat format, PBytes object, byte[] bytes, int offset,
                        @CachedLibrary("object") PythonBufferAccessLibrary bufferLib) {
            if (bufferLib.getBufferLength(object) != 1) {
                throw raise(OverflowError);
            }
            bytes[offset] = bufferLib.readByte(object, 0);
        }

        @Specialization(guards = {"format == CHAR", "!isBytes(object)"})
        @SuppressWarnings("unused")
        void packChar(BufferFormat format, Object object, byte[] bytes, int offset) {
            throw raise(TypeError);
        }

        @Specialization(guards = "format == UNICODE")
        @SuppressWarnings("truffle-static-method")
        void packDouble(@SuppressWarnings("unused") BufferFormat format, Object object, byte[] bytes, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            TruffleString str = cast.cast(inliningTarget, object, ErrorMessages.ARRAY_ITEM_MUST_BE_UNICODE);
            if (codePointLengthNode.execute(str, TS_ENCODING) == 1) {
                int codePoint = codePointAtIndexNode.execute(str, 0, TS_ENCODING);
                PythonUtils.ARRAY_ACCESSOR.putInt(bytes, offset, codePoint);
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
}
