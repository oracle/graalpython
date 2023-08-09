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
package com.oracle.graal.python.builtins.objects.ints;

import static com.oracle.graal.python.nodes.ErrorMessages.TOO_LARGE_TO_CONVERT;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.NumericSupport;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Namespace containing equivalent nodes of {@code _Pylong_XXX} private function from
 * {@code longobject.c}
 */
public final class IntNodes {
    private IntNodes() {
    }

    /**
     * Equivalent of CPython's {@code _PyLong_Sign}. Return 0 if v is 0, -1 if v < 0, +1 if v > 0.
     */
    @GenerateUncached
    public abstract static class PyLongSign extends Node {
        public abstract int execute(Object value);

        @Specialization
        static int doInt(int value) {
            return Integer.compare(value, 0);
        }

        @Specialization
        static int doLong(long value) {
            return Long.compare(value, 0);
        }

        @Specialization
        static int doPInt(PInt value) {
            return value.compareTo(0);
        }
    }

    /**
     * Equivalent to CPython's {@code _PyLong_NumBits}. Return the number of bits needed to
     * represent the absolute value of a long.
     */
    public abstract static class PyLongNumBits extends Node {
        public abstract int execute(Object value);

        @Specialization
        static int doInt(int value) {
            return Integer.SIZE - Integer.numberOfLeadingZeros(Math.abs(value));
        }

        @Specialization
        static int doLong(long value) {
            return Long.SIZE - Long.numberOfLeadingZeros(Math.abs(value));
        }

        @Specialization
        static int doPInt(PInt value) {
            return value.bitLength();
        }
    }

    /**
     * Equivalent to CPython's {@code _PyLong_AsByteArray}. Convert the least-significant 8*n bits
     * of long v to a base-256 integer, stored in array bytes.
     */
    public abstract static class PyLongAsByteArray extends Node {
        public abstract byte[] execute(Object value, int size, boolean bigEndian);

        protected static int asWellSizedData(int len) {
            switch (len) {
                case 1:
                case 2:
                case 4:
                case 8:
                    return len;
                default:
                    return -1;
            }
        }

        @Specialization(guards = "size == cachedDataLen", limit = "4")
        static byte[] doPrimitive(long value, int size, boolean bigEndian,
                        @Cached("asWellSizedData(size)") int cachedDataLen) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            support.putLong(bytes, 0, value, cachedDataLen);
            return bytes;
        }

        @Specialization
        static byte[] doArbitraryBytesLong(long value, int size, boolean bigEndian,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            try {
                support.putBigInteger(bytes, 0, PInt.longToBigInteger(value), size);
            } catch (OverflowException oe) {
                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT, "int");
            }
            return bytes;
        }

        @Specialization
        static byte[] doPInt(PInt value, int size, boolean bigEndian,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            try {
                support.putBigInteger(bytes, 0, value.getValue(), size);
            } catch (OverflowException oe) {
                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT, "int");
            }
            return bytes;
        }
    }

    /**
     * Equivalent to CPython's {@code _PyLong_FromByteArray}. View the n unsigned bytes as a binary
     * integer in base 256, and return a Python int with the same numeric value.
     */
    public abstract static class PyLongFromByteArray extends Node {
        public abstract Object execute(byte[] data, boolean bigEndian);

        protected static boolean fitsInLong(byte[] data) {
            return data.length <= Long.BYTES;
        }

        protected static int asWellSizedData(int len) {
            switch (len) {
                case 1:
                case 2:
                case 4:
                case 8:
                    return len;
                default:
                    return -1;
            }
        }

        @Specialization(guards = "data.length == cachedDataLen", limit = "4")
        static Object doLong(byte[] data, boolean bigEndian,
                        @Cached("asWellSizedData(data.length)") int cachedDataLen) {
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            return support.getLong(data, 0, cachedDataLen);
        }

        @Specialization(guards = "fitsInLong(data)")
        static long doArbitraryBytesLong(byte[] data, boolean bigEndian) {
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            BigInteger integer = support.getBigInteger(data, 0);
            return PInt.longValue(integer);
        }

        @Specialization(guards = "!fitsInLong(data)")
        static Object doPInt(byte[] data, boolean bigEndian,
                        @Cached PythonObjectFactory factory) {
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            return factory.createInt(support.getBigInteger(data, 0));
        }
    }
}
