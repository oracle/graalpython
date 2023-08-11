/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;

import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.pegparser.PythonStringFactory;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

class PythonStringFactoryImpl implements PythonStringFactory<TruffleString> {

    @Override
    public PythonStringBuilder<TruffleString> createBuilder(int initialCodePointLength) {
        return new TruffleStringBuilderWrapper(tsbCapacity(initialCodePointLength));
    }

    @Override
    public TruffleString fromJavaString(String s) {
        return TruffleString.fromJavaStringUncached(s, TS_ENCODING);
    }

    @Override
    public TruffleString emptyString() {
        return StringLiterals.T_EMPTY_STRING;
    }

    private static class TruffleStringBuilderWrapper implements PythonStringBuilder<TruffleString> {
        private final TruffleStringBuilder sb;

        TruffleStringBuilderWrapper(int initialCapacity) {
            sb = TruffleStringBuilder.create(TS_ENCODING, initialCapacity);
        }

        @Override
        public PythonStringBuilder<TruffleString> appendString(String s) {
            sb.appendStringUncached(TruffleString.fromJavaStringUncached(s, TS_ENCODING));
            return this;
        }

        @Override
        public PythonStringBuilder<TruffleString> appendPythonString(TruffleString s) {
            sb.appendStringUncached(s);
            return this;
        }

        @Override
        public PythonStringBuilder<TruffleString> appendCodePoint(int codePoint) {
            sb.appendCodePointUncached(codePoint, 1, true);
            return this;
        }

        @Override
        public boolean isEmpty() {
            return sb.isEmpty();
        }

        @Override
        public TruffleString build() {
            return sb.toStringUncached();
        }
    }
}
