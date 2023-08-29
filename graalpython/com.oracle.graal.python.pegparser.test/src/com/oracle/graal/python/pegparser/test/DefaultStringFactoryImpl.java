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
package com.oracle.graal.python.pegparser.test;

import com.oracle.graal.python.pegparser.PythonStringFactory;

public class DefaultStringFactoryImpl implements PythonStringFactory<String> {
    @Override
    public PythonStringBuilder<String> createBuilder(int initialCodePointLength) {
        return new StringBuilderWrapper(initialCodePointLength);
    }

    @Override
    public String emptyString() {
        return "";
    }

    @Override
    public String fromJavaString(String s) {
        return s;
    }

    private static class StringBuilderWrapper implements PythonStringBuilder<String> {
        private final StringBuilder sb;

        StringBuilderWrapper(int initialCapacity) {
            this.sb = new StringBuilder(initialCapacity);
        }

        @Override
        public PythonStringBuilder<String> appendString(String s) {
            sb.append(s);
            return this;
        }

        @Override
        public PythonStringBuilder<String> appendPythonString(String s) {
            sb.append(s);
            return null;
        }

        @Override
        public PythonStringBuilder<String> appendCodePoint(int codePoint) {
            sb.appendCodePoint(codePoint);
            return null;
        }

        @Override
        public boolean isEmpty() {
            return sb.length() == 0;
        }

        @Override
        public String build() {
            return sb.toString();
        }
    }
}
