/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.object.Shape;

public final class PStringIO extends PTextIOBase {

    private static final class Accu {
        static final int STATE_REALIZED = 1;
        static final int STATE_ACCUMULATING = 2;
        /*
         * The stringio object can be in two states: accumulating or realized. In accumulating
         * state, the internal buffer contains nothing and the contents are given by the embedded
         * _PyAccu structure. In realized state, the internal buffer is meaningful and the _PyAccu
         * is destroyed.
         */
        int state = -1;
        StringBuilder sb = PythonUtils.newStringBuilder();

        String finish() {
            String str = PythonUtils.sbToString(sb);
            sb = PythonUtils.newStringBuilder();
            return str;
        }

        void clear() {
            sb = PythonUtils.newStringBuilder();
        }
    }

    private boolean closed;

    private String buf;
    private int pos;
    private int stringSize;

    private final Accu accu = new Accu();

    public PStringIO(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        buf = "";
    }

    public boolean hasBuf() {
        return buf != null;
    }

    public String getBuf() {
        return buf;
    }

    public void setBuf(String buf) {
        this.buf = buf;
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
        if (accu.state == Accu.STATE_REALIZED) {
            return;
        }
        assert (accu.state == Accu.STATE_ACCUMULATING);
        accu.state = Accu.STATE_REALIZED;

        buf = accu.finish();
    }

    public boolean isAccumlating() {
        return accu.state == Accu.STATE_ACCUMULATING;
    }

    public void setAccumlating() {
        accu.state = Accu.STATE_ACCUMULATING;
    }

    public void append(String str) {
        PythonUtils.append(accu.sb, str);
    }

    public void setRealized() {
        accu.state = Accu.STATE_REALIZED;
    }

    public String makeIntermediate() {
        assert (accu.state == Accu.STATE_ACCUMULATING);
        return PythonUtils.sbToString(accu.sb);
    }

    @Override
    public void clearAll() {
        super.clearAll();
        buf = "";
        accu.clear();
        setWriteNewline(null);
    }
}
