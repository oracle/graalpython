/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

abstract class PTextIOBase extends PythonBuiltinObject {

    private boolean ok; /* initialized? */
    private Object decoder;
    private TruffleString readnl;
    private TruffleString writenl; /* ASCII-encoded; NULL stands for \n */
    private boolean readuniversal;
    private boolean readtranslate;

    PTextIOBase(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public void clearAll() {
        decoder = null;
        readnl = null;
    }

    public final boolean isOK() {
        return ok;
    }

    public final void setOK(boolean ok) {
        this.ok = ok;
    }

    public final Object getDecoder() {
        return decoder;
    }

    public final void setDecoder(Object decoder) {
        this.decoder = decoder;
    }

    public final boolean hasDecoder() {
        return decoder != null;
    }

    public final TruffleString getReadNewline() {
        return readnl;
    }

    public final void setReadNewline(TruffleString readnl) {
        this.readnl = readnl;
    }

    public final TruffleString getWriteNewline() {
        return writenl;
    }

    public final boolean hasWriteNewline() {
        return writenl != null;
    }

    public final void setWriteNewline(TruffleString writenl) {
        this.writenl = writenl;
    }

    public final boolean isReadUniversal() {
        return readuniversal;
    }

    public final void setReadUniversal(boolean readuniversal) {
        this.readuniversal = readuniversal;
    }

    public final boolean isReadTranslate() {
        return readtranslate;
    }

    public final void setReadTranslate(boolean readtranslate) {
        this.readtranslate = readtranslate;
    }
}
