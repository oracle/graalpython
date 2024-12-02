/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.embedding;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

/**
 * Represents positional arguments (`*args`) in Python. The {@code PositionalArguments} class
 * provides a way to pass and manage a variable number of positional arguments in a Java context,
 * mimicking the behavior of `*args` in Python. Instances of this class are immutable and can be
 * created using static factory methods for convenience.
 *
 * <p>
 * Each {@code PositionalArguments} instance encapsulates an ordered list of arguments, which can be
 * of any type. These arguments are treated as if they were passed to a Python function using the
 * `*args` syntax.
 * </p>
 *
 * <h3>Creating PositionalArguments</h3>
 *
 * <p>
 * You can create an instance of {@code PositionalArguments} using one of the provided static
 * factory methods:
 * </p>
 *
 * <ul>
 * <li>{@link #from(List)}: Creates {@code PositionalArguments} from a {@link java.util.List} of
 * arguments.</li>
 * <li>{@link #of(Object...)}: Creates {@code PositionalArguments} directly from a variable number
 * of arguments.</li>
 * </ul>
 *
 * <p>
 * Examples:
 * </p>
 *
 * <pre>{@code
 * // Create PositionalArguments from a list
 * PositionalArguments args = PositionalArguments.from(List.of(1, "hello", true));
 *
 * // Create PositionalArguments directly
 * PositionalArguments args2 = PositionalArguments.of(1, "hello", true);
 * }</pre>
 *
 * <h3>When to Use</h3> {@link PositionalArguments} should be used whenever a Python function
 * accepts variable-length positional arguments (i.e., {@code *args}). It can also be combined with
 * {@link KeywordArguments} to support functions that accept both types of arguments.
 *
 * <p>
 * <b>Important:</b> An instance of {@link PositionalArguments} can be used in one of the following
 * scenarios:
 * </p>
 * <ul>
 * <li>As the penultimate argument, followed by an instance of {@link KeywordArguments}.</li>
 * <li>As the final argument, if the Python function does not accept named arguments.</li>
 * </ul>
 * This ensures proper alignment with Python's argument structure and guarantees that all arguments
 * are passed correctly.
 *
 * @see KeywordArguments
 */
public sealed abstract class PositionalArguments permits PositionalArguments.Implementation {

    static final class Implementation extends PositionalArguments implements ProxyArray, ProxyObject {

        public static final String MEMBER_KEY = "org.graalvm.python.embedding.PositionalArguments.is_positional_arguments";
        private final Object[] values;

        private Implementation(Object[] values) {
            this.values = values;
        }

        @Override
        public Object get(long index) {
            return this.values[(int) index];
        }

        @Override
        public void set(long index, Value value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getSize() {
            return values.length;
        }

        @Override
        public Object getMember(String key) {
            if (MEMBER_KEY.equals(key)) {
                return true;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getMemberKeys() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasMember(String key) {
            return MEMBER_KEY.equals(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException();
        }
    }

    private PositionalArguments() {
    }

    /**
     * Creates a {@link PositionalArguments} instance from the specified values.
     *
     * @param values the positional arguments to be included; each value represents an argument
     *            passed to the Python function. Individual values can be {@code null}, which
     *            translates to the Python value {@code None}.
     * @return a new {@link PositionalArguments} instance containing the provided values
     */
    public static PositionalArguments of(Object... values) {
        return new PositionalArguments.Implementation(values);
    }

    /**
     * Creates a {@link PositionalArguments} instance from the specified list of values.
     *
     * @param values a list of positional arguments to be included; each value represents an
     *            argument passed to the Python function. Null value can not be passed in this case.
     * @return a new {@link PositionalArguments} instance containing the provided values
     */
    public static PositionalArguments from(List<Object> values) {
        return new PositionalArguments.Implementation(values.toArray());
    }
}
