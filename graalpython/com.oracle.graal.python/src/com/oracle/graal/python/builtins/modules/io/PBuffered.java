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

import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;

public final class PBuffered extends PythonBuiltinObject {

    private Object raw;
    private boolean ok; /* Initialized? */
    private boolean detached;

    private final boolean readable;
    private final boolean writable;

    /*
     * TODO: finalizing is set using an unimplemented `bufferedio.c:buffered_dealloc`
     */
    private boolean finalizing;

    /*-
     * True if this is a vanilla Buffered object
     * (rather than a user derived class) *and* the raw stream is a vanilla FileIO object.
     */
    private PFileIO fileioRaw;

    /* Absolute position inside the raw stream (-1 if unknown). */
    private long absPos;

    /* A static buffer of size `bufferSize` */
    private byte[] buffer;

    /* Current logical position in the buffer. */
    private int pos;
    /* Position of the raw stream in the buffer. */
    private long rawPos;

    /*
     * Just after the last buffered byte in the buffer, or -1 if the buffer isn't ready for reading.
     */
    private int readEnd;

    /* Just after the last byte actually written */
    private int writePos;
    /*
     * Just after the last byte waiting to be written, or -1 if the buffer isn't ready for writing.
     */
    private int writeEnd;

    private PLock lock;
    private long owner;

    @CompilerDirectives.CompilationFinal private int bufferSize;
    @CompilerDirectives.CompilationFinal private int bufferMask;

    public PBuffered(Object cls, Shape instanceShape, boolean readable, boolean writable) {
        super(cls, instanceShape);
        this.ok = false;
        this.detached = false;
        this.readable = readable;
        this.writable = writable;
        this.finalizing = false;
    }

    public void setBufferMask(int bufferMask) {
        this.bufferMask = bufferMask;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public Object getRaw() {
        return raw;
    }

    public void setRaw(Object newRaw, boolean isFileIO) {
        this.raw = newRaw;
        this.fileioRaw = isFileIO ? (PFileIO) newRaw : null;
    }

    public void clearRaw() {
        this.raw = null;
        this.fileioRaw = null;
    }

    public boolean isOK() {
        return ok;
    }

    public void setOK(boolean ok) {
        this.ok = ok;
    }

    public boolean isDetached() {
        return detached;
    }

    public void setDetached(boolean detached) {
        this.detached = detached;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    public void setFinalizing(boolean finalizing) {
        this.finalizing = finalizing;
    }

    public boolean isFastClosedChecks() {
        return fileioRaw != null;
    }

    public PFileIO getFileIORaw() {
        assert fileioRaw != null;
        return fileioRaw;
    }

    public long getAbsPos() {
        return absPos;
    }

    public void setAbsPos(long absPos) {
        this.absPos = absPos;
    }

    public void incAbsPos(long n) {
        this.absPos += n;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public void incPos(int n) {
        this.pos += n;
    }

    public long getRawPos() {
        return rawPos;
    }

    public void setRawPos(long n) {
        this.rawPos = n;
    }

    public void incRawPos(long n) {
        this.rawPos += n;
    }

    public int getReadEnd() {
        return readEnd;
    }

    public void resetRead() {
        this.readEnd = -1;
    }

    public void setReadEnd(int n) {
        this.readEnd = n;
    }

    public void incReadEnd(int n) {
        this.readEnd += n;
    }

    public int getWritePos() {
        return writePos;
    }

    public void setWritePos(int n) {
        this.writePos = n;
    }

    public void incWritePos(int n) {
        this.writePos += n;
    }

    public int getWriteEnd() {
        return writeEnd;
    }

    public void setWriteEnd(int n) {
        this.writeEnd = n;
    }

    public void incWriteEnd(int n) {
        this.writeEnd += n;
    }

    public void resetWrite() {
        this.writePos = 0;
        this.writeEnd = -1;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void initBuffer(int bufSize) {
        this.bufferSize = bufSize;
        this.buffer = new byte[bufSize];
    }

    public int getBufferMask() {
        return bufferMask;
    }

    public PLock getLock() {
        return lock;
    }

    public void setLock(PLock lock) {
        this.lock = lock;
    }

    public long getOwner() {
        return owner;
    }

    public void setOwner(long owner) {
        this.owner = owner;
    }

    public boolean isOwn() {
        return ThreadModuleBuiltins.GetCurrentThreadIdNode.getId() == owner;
    }
}
