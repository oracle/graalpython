/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import com.oracle.graal.python.builtins.objects.bytes.PythonBufferLibrary;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltins.InternalLenNode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferLibrary.class)
public final class PMMap extends PythonObject {

    private final SeekableByteChannel mappedByteBuffer;
    private final long length;
    private final long offset;

    public PMMap(LazyPythonClass pythonClass, SeekableByteChannel mappedByteBuffer, long length, long offset) {
        super(pythonClass);
        this.mappedByteBuffer = mappedByteBuffer;
        this.length = length;
        this.offset = offset;
    }

    public SeekableByteChannel getChannel() {
        return mappedByteBuffer;
    }

    public long getLength() {
        return length;
    }

    public long getOffset() {
        return offset;
    }

    @ExportMessage
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength(
                    @Shared("lenNode") @Cached InternalLenNode lenNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntNode castToIntNode) {
        return castToIntNode.execute(lenNode.execute(this));
    }

    @ExportMessage
    byte[] getBufferBytes(
                    @Shared("lenNode") @Cached InternalLenNode lenNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntNode castToIntNode,
                    @Cached ReadFromChannelNode readNode) {

        try {
            int len = getBufferLength(lenNode, castToIntNode);

            // save current position
            long oldPos = PMMap.position(mappedByteBuffer);

            PMMap.position(mappedByteBuffer, 0);
            ByteSequenceStorage s = readNode.execute(mappedByteBuffer, len);
            return s.getInternalByteArray();
        } catch (IOException e) {
            // TODO(fa) how to handle?
            return null;
        }
    }

    @TruffleBoundary
    static long position(SeekableByteChannel ch) throws IOException {
        return ch.position();
    }

    @TruffleBoundary
    static void position(SeekableByteChannel ch, long offset) throws IOException {
        ch.position(offset);
    }

    @TruffleBoundary
    static long size(SeekableByteChannel ch) throws IOException {
        return ch.size();
    }

}
