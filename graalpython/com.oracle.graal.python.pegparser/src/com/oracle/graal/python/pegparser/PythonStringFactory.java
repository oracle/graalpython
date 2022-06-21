/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

/**
 * Used by the parser to construct run-time representation of string literals. In the graalpython
 * interpreter, strings are represented by {@code TruffleString}. This interface allows the use of
 * the parser (e.g. in tools such as an IDE) without the dependency on Truffle - the
 * {@link DefaultStringFactoryImpl} class is an implementation that uses {@link String} for the
 * representation of python string literals. Note thought that this is not entirely correct since
 * {@link String} is not capable of distinguishing a SMP codepoint from a pair of corresponding
 * surrogates, which in Python are different: {@code '\U00010400' != '\uD801\uDC00'}.
 *
 * @param <T> the type used to represent string literals
 */
public interface PythonStringFactory<T> {

    PythonStringBuilder<T> createBuilder(int initialCodePointLength);

    T emptyString();

    T fromJavaString(String s);

    interface PythonStringBuilder<T> {
        PythonStringBuilder<T> appendString(String s);

        PythonStringBuilder<T> appendPythonString(T s);

        PythonStringBuilder<T> appendCodePoint(int codePoint);

        boolean isEmpty();

        T build();
    }
}
