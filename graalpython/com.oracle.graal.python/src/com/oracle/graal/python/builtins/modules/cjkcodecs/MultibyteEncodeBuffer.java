/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.MAXSIZE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class MultibyteEncodeBuffer {

    protected final TruffleString inobj;
    protected PBytes outobj;
    CharBuffer inputBuffer;
    ByteBuffer outputBuffer;
    protected PBaseException excobj;

    @TruffleBoundary
    public MultibyteEncodeBuffer(TruffleString inobj) {
        this.inobj = inobj;
        this.inputBuffer = CharBuffer.wrap(inobj.toJavaStringUncached());
        this.outputBuffer = ByteBuffer.allocate(this.inputBuffer.length() * 2 + 16);
        this.excobj = null;
    }

    public MultibyteEncodeBuffer(int outbufSize) {
        this.inobj = null;
        this.inputBuffer = null;
        this.outputBuffer = ByteBuffer.allocate(outbufSize);
        this.excobj = null;
    }

    protected int getInpos() {
        return inputBuffer.position();
    }

    protected int getInlen() {
        return inputBuffer.limit();
    }

    @TruffleBoundary
    protected void setInpos(int pos) {
        inputBuffer.position(pos);
    }

    @TruffleBoundary
    protected void incInpos(int len) {
        setInpos(getInpos() + len);
    }

    protected void rewindInbuf() {
        inputBuffer.rewind();
    }

    protected boolean isFull() {
        return !inputBuffer.hasRemaining();
    }

    @TruffleBoundary
    protected void append(char c) {
        outputBuffer.putChar(c);
    }

    @TruffleBoundary
    protected void append(byte[] bytes) {
        outputBuffer.put(bytes);
    }

    protected int remaining() {
        return outputBuffer.remaining();
    }

    protected void rewindOutbuf() {
        outputBuffer.rewind();
    }

    @TruffleBoundary
    protected void expandOutputBuffer(int esize, Node raisingNode) {
        if (esize < 0 || esize > remaining()) {
            int orgsize = outputBuffer.capacity();
            int incsize = esize < (orgsize >> 1) ? (orgsize >> 1) | 1 : esize;
            if (orgsize > MAXSIZE - incsize) {
                throw PRaiseNode.raiseUncached(raisingNode, MemoryError);
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(incsize);
            outputBuffer.flip();
            newBuffer.put(outputBuffer);
            outputBuffer = newBuffer;
            outobj = null;
        }
    }

    @TruffleBoundary
    protected PBytes createPBytes(PythonObjectFactory factory) {
        outobj = factory.createBytes(outputBuffer.array(), outputBuffer.position());
        return outobj;
    }

    protected TruffleString toTString() {
        return inobj;
    }
}
