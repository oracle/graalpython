/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public final class PStringIO extends PTextIOBase {

    /*
     * The PStringIO object can be in two states: accumulating or realized. In accumulating state,
     * the internal buffer contains nothing and the contents are given by the string builder. In
     * realized state, the internal buffer is meaningful and the string builder is destroyed.
     * Invariant: buf == null iff sb != null
     */

    private boolean closed;

    private TruffleString cachedString;
    private TruffleStringBuilder buf;
    private TruffleStringBuilder sb;
    private int pos;
    private int stringSize;

    public PStringIO(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        buf = null;
    }

    public TruffleStringBuilder getBuf() {
        return buf;
    }

    public void setBuf(TruffleStringBuilder buf) {
        this.buf = buf;
        cachedString = null;
    }

    public void invalidateBufCache() {
        cachedString = null;
    }

    public int getPos() {
        return pos;
    }

    public void incPos(int p) {
        this.pos += p;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public void setStringsize(int string_size) {
        this.stringSize = string_size;
    }

    public int getStringSize() {
        return stringSize;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }

    public void realize() {
        if (!isAccumulating()) {
            return;
        }
        buf = sb;
        cachedString = null;
        sb = null;
    }

    public boolean isAccumulating() {
        return sb != null;
    }

    public void setAccumulating() {
        assert stringSize == 0 && !isAccumulating();
        sb = TruffleStringBuilder.create(TS_ENCODING);
        cachedString = null;
    }

    public void append(TruffleString str, TruffleStringBuilder.AppendStringNode appendStringNode) {
        assert isAccumulating();
        appendStringNode.execute(sb, str);
    }

    public void setRealized() {
        sb = null;
        buf = TruffleStringBuilder.create(TS_ENCODING);
    }

    public TruffleString makeIntermediate(TruffleStringBuilder.ToStringNode toStringNode) {
        assert isAccumulating();
        return toStringNode.execute(sb);
    }

    @Override
    public void clearAll() {
        super.clearAll();
        buf = TruffleStringBuilder.create(TS_ENCODING);
        sb = null;
        cachedString = null;
        setWriteNewline(null);
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class PStringIOBufToStringNode extends Node {
        abstract TruffleString execute(Node inliningTarget, PStringIO self);

        static boolean hasCache(PStringIO s) {
            return s.cachedString != null;
        }

        @Specialization(guards = "hasCache(self)")
        TruffleString doCached(PStringIO self) {
            return self.cachedString;
        }

        @Specialization(guards = "!hasCache(self)")
        TruffleString doUncached(PStringIO self,
                        @Cached(inline = false) TruffleStringBuilder.ToStringNode toStringNode) {
            self.cachedString = toStringNode.execute(self.getBuf());
            return self.cachedString;
        }
    }
}
