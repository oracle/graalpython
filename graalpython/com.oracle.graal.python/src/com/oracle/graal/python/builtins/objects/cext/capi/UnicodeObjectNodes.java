/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class UnicodeObjectNodes {

    @GenerateUncached
    public abstract static class UnicodeAsWideCharNode extends Node {

        public final PBytes executeNativeOrder(Object obj, long elementSize) {
            return execute(obj, elementSize, ByteOrder.nativeOrder());
        }

        public final PBytes executeLittleEndian(Object obj, long elementSize) {
            return execute(obj, elementSize, ByteOrder.LITTLE_ENDIAN);
        }

        public final PBytes executeBigEndian(Object obj, long elementSize) {
            return execute(obj, elementSize, ByteOrder.BIG_ENDIAN);
        }

        public abstract PBytes execute(Object obj, long elementSize, ByteOrder byteOrder);

        @Specialization
        static PBytes doUnicode(PString s, long elementSize, ByteOrder byteOrder,
                        @Bind("this") Node inliningTarget,
                        @Cached StringMaterializeNode materializeNode) {
            return doUnicode(materializeNode.execute(inliningTarget, s), elementSize, byteOrder);
        }

        @Specialization
        @TruffleBoundary
        static PBytes doUnicode(TruffleString s, long elementSize, ByteOrder byteOrder) {
            TruffleString.Encoding encoding = byteOrder == ByteOrder.LITTLE_ENDIAN ? TruffleString.Encoding.UTF_32LE : TruffleString.Encoding.UTF_32BE;

            // elementSize == 2: Store String in 'wchar_t' of size == 2, i.e., use UCS2. This is
            // achieved by decoding to UTF32 (which is basically UCS4) and ignoring the two
            // MSBs.
            if (elementSize == 2L) {
                ByteBuffer bytes = ByteBuffer.wrap(getBytes(s, encoding));
                // FIXME unsafe narrowing
                int size = bytes.remaining() / 2;
                ByteBuffer buf = ByteBuffer.allocate(size);
                while (bytes.remaining() >= 4) {
                    if (byteOrder != ByteOrder.nativeOrder()) {
                        buf.putChar((char) ((bytes.getInt() & 0xFFFF0000) >> 16));
                    } else {
                        buf.putChar((char) (bytes.getInt() & 0x0000FFFF));
                    }
                }
                buf.flip();
                byte[] barr = new byte[buf.remaining()];
                buf.get(barr);
                return PythonObjectFactory.getUncached().createBytes(barr);
            } else if (elementSize == 4L) {
                return PythonObjectFactory.getUncached().createBytes(getBytes(s, encoding));
            } else {
                throw new RuntimeException("unsupported wchar size; was: " + elementSize);
            }
        }

        private static byte[] getBytes(TruffleString s, TruffleString.Encoding encoding) {
            TruffleString utf32String = s.switchEncodingUncached(encoding);
            int len = utf32String.byteLength(encoding);
            byte[] b = new byte[len];
            utf32String.copyToByteArrayUncached(0, b, 0, len, encoding);
            return b;
        }
    }
}
