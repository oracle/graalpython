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
package com.oracle.graal.python.builtins.modules.io;

public class BufferedIOUtil {

    // start of the stream (the default); offset should be zero or positive
    protected static final int SEEK_SET = 0;
    // current stream position; offset may be negative
    protected static final int SEEK_CUR = 1;
    // end of the stream; offset is usually negative
    protected static final int SEEK_END = 2;

    protected static boolean isValidWriteBuffer(PBuffered self) {
        return (self.isWritable() && self.getWriteEnd() != -1);
    }

    protected static int safeDowncast(PBuffered self) {
        return ((self.isReadable() && self.getReadEnd() != -1)) ? (self.getReadEnd() - self.getPos()) : 0;
    }

    protected static boolean isValidReadBuffer(PBuffered self) {
        return self.isReadable() && self.getReadEnd() != -1;
    }

    protected static int readahead(PBuffered self) {
        return ((self.isReadable() && isValidReadBuffer(self)) ? (self.getReadEnd() - self.getPos()) : 0);
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:RAW_OFFSET
     */
    protected static long rawOffset(PBuffered self) {
        return (((isValidReadBuffer(self) || isValidWriteBuffer(self)) && self.getRawPos() >= 0) ? self.getRawPos() - self.getPos() : 0);
    }

    /**
     * implementation of cpython/Modules/_io/bufferedio.c:MINUS_LAST_BLOCK
     */
    protected static int minusLastBlock(PBuffered self, int size) {
        return (self.getBufferMask() != 0 ? (size & ~self.getBufferMask()) : (self.getBufferSize() * (size / self.getBufferSize())));
    }
}
