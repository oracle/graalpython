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
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Represents a set of keyword arguments, typically used for interfacing with Python functions that accept {@code **kwargs}.
 *
 * <p>
 * The {@link KeywordArguments} class provides factory methods to create instances from a {@link Map}
 * or by directly specifying key-value pairs.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Create using a map
 * Map<String, Object> kwargsMap = Map.of("arg1", 10, "arg2", "value");
 * KeywordArguments kwargs = KeywordArguments.from(kwargsMap);
 *
 * // Create using key-value pairs
 * KeywordArguments kwargs2 = KeywordArguments.of("arg1", 10, "arg2", "value");
 *
 * // Using a builder for predefined keys
 * KeywordArguments args = new MyCustomKwArgsBuilder("mandatoryValue")
 *     .optionalKey("optionalValue")
 *     .add("dynamicKey", 42)
 *     .build();
 * }</pre>
 *
 * <h3>When to Use</h3>
 * {@link KeywordArguments} must be used whenever a Python function accepts named arguments (both required and optional).
 * This ensures proper mapping of Java arguments to Python function parameters, especially when working with Python methods
 * that utilize {@code **kwargs}.
 *
 * <p><b>Important:</b> An instance of {@link KeywordArguments} must always be the last argument when invoking a Python function.
 * It may be preceded by an instance of {@link PositionalArguments}, but {@link KeywordArguments} must always be the final argument.
 * This ensures proper alignment with Python's argument structure.</p>
 *
 *
 * @see PositionalArguments
 */
public sealed abstract class KeywordArguments permits KeywordArguments.Implementation {

    /**
     * Internal implementation of the {@link KeywordArguments} class.
     * <p>
     * This class is not intended for direct use. It provides interoperability
     * with Python's mapping and object protocols via {@link ProxyHashMap} and {@link ProxyObject}.
     * </p>
     */
    protected static final class Implementation extends KeywordArguments implements ProxyHashMap, ProxyObject {

        public static final String MEMBER_KEY  = "org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments";
        private final Map<String, Object> kwArgs;

        private Implementation(Map<String, Object> kwArgs) {
            this.kwArgs = kwArgs;
        }
        @Override
        public Object getMember(String key) throws UnsupportedOperationException {
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

        @Override
        public long getHashSize() {
            return kwArgs.size();
        }

        @Override
        public boolean hasHashEntry(Value key) {
            String unboxedKey = unboxKey(key);
            return kwArgs.containsKey(unboxedKey);
        }

        private static String unboxKey(Value key) {
            return key.asString();
        }

        @Override
        public Object getHashValue(Value key) {
            Object unboxedKey = unboxKey(key);
            return kwArgs.get(unboxedKey);
        }

        @Override
        public void putHashEntry(Value key, Value value) {
            String unboxedKey = unboxKey(key);
            kwArgs.put(unboxedKey, value.isHostObject() ? value.asHostObject() : value);
        }

        @Override
        public Object getHashEntriesIterator() {
            Iterator<Map.Entry<String, Object>> entryIterator = kwArgs.entrySet().iterator();
            return new ProxyIterator() {
                @Override
                public boolean hasNext() {
                    return entryIterator.hasNext();
                }

                @Override
                public Object getNext() throws NoSuchElementException, UnsupportedOperationException {
                    return new ProxyEntryImpl(entryIterator.next());
                }
            };
        }

        private class ProxyEntryImpl implements ProxyArray {

            private Map.Entry<String, Object> mapEntry;

            ProxyEntryImpl(Map.Entry<String, Object> mapEntry) {
                this.mapEntry = mapEntry;
            }

            @Override
            public Object get(long index) {
                if (index == 0L) {
                    return mapEntry.getKey();
                } else if (index == 1L) {
                    return mapEntry.getValue();
                } else {
                    throw new ArrayIndexOutOfBoundsException();
                }
            }

            @Override
            public void set(long index, Value value) {
                if (index == 0L) {
                    throw new UnsupportedOperationException();
                } else if (index == 1L) {
                    KeywordArguments.Implementation.this.kwArgs.put(mapEntry.getKey(), value.isHostObject() ? value.asHostObject() : value);
                } else {
                    throw new ArrayIndexOutOfBoundsException();
                }
            }

            @Override
            public long getSize() {
                return 2;
            }
        }
    }

    private KeywordArguments() {
    }

    /**
     * Creates a {@link KeywordArguments} instance from the specified map.
     *
     * @param values a map containing the keyword arguments; each key-value pair represents a named argument.
     *               Keys must be non-null strings, and values can be any object.
     * @return a new {@link KeywordArguments} instance containing the provided arguments
     * @see java.util.Map
     */
    public static KeywordArguments from(Map<String, Object> values) {
        return new KeywordArguments.Implementation(Map.copyOf(values));
    }

    /**
     * Creates a {@link KeywordArguments} instance from a single key-value pair.
     *
     * @param name the name of the argument; must not be {@code null}.
     * @param value the value of the argument; must not be {@code null}.
     * @return a new {@link KeywordArguments} instance containing the specified argument
     * @see java.util.Map#of(Object, Object)
     */
    public static KeywordArguments of(String name, Object value) {
        return new KeywordArguments.Implementation(Map.of(name, value));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4 ) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
            String name5, Object value5) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
                                      String name5, Object value5, String name6, Object value6) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5, name6, value6));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
                                      String name5, Object value5, String name6, Object value6, String name7, Object value7) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5, name6, value6, name7, value7));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
                                      String name5, Object value5, String name6, Object value6, String name7, Object value7, String name8, Object value8) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5, name6, value6, name7, value7, name8, value8));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
                                      String name5, Object value5, String name6, Object value6, String name7, Object value7, String name8, Object value8,
                                      String name9, Object value9) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5, name6, value6, name7, value7, name8, value8, name9, value9));
    }

    /**
     * Creates a {@link KeywordArguments} instance from multiple key-value pairs.
     *
     * @param name1 the name of the first argument
     * @param value1 the value of the first argument
     * @param name2 the name of the second argument
     * @param value2 the value of the second argument
     * @param name3 the name of the third argument
     * @param value3 the value of the third argument
     * ... (similar for other parameters) ...
     * @return a new {@link KeywordArguments} instance containing the specified arguments
     * @see java.util.Map#of(Object, Object, Object, Object, ...)
     */
    public static KeywordArguments of(String name1, Object value1, String name2, Object value2, String name3, Object value3, String name4, Object value4,
                                      String name5, Object value5, String name6, Object value6, String name7, Object value7, String name8, Object value8,
                                      String name9, Object value9, String name10, Object value10) {
        return new KeywordArguments.Implementation(Map.of(name1, value1, name2, value2, name3, value3, name4, value4,
                name5, value5, name6, value6, name7, value7, name8, value8, name9, value9, name10, value10));
    }
}
