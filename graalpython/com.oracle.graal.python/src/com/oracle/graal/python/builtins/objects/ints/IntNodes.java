/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_LARGE_TO_CONVERT;

import java.math.BigInteger;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.NumericSupport;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

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
    @GenerateInline(false)
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
    @GenerateInline(false)
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
    @GenerateInline(inlineByDefault = true)
    public abstract static class PyLongAsByteArray extends Node {
        public abstract byte[] execute(Node inliningTarget, Object value, int size, boolean bigEndian);

        public final byte[] executeCached(Object value, int size, boolean bigEndian) {
            return execute(this, value, size, bigEndian);
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

        @Specialization(guards = "size == cachedDataLen", limit = "4")
        static byte[] doPrimitive(long value, int size, boolean bigEndian,
                        @Cached("asWellSizedData(size)") int cachedDataLen) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            support.putLong(bytes, 0, value, cachedDataLen);
            return bytes;
        }

        @Specialization
        static byte[] doArbitraryBytesLong(Node inliningTarget, long value, int size, boolean bigEndian,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            try {
                support.putBigInteger(bytes, 0, PInt.longToBigInteger(value), size);
            } catch (OverflowException oe) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, TOO_LARGE_TO_CONVERT, "int");
            }
            return bytes;
        }

        @Specialization
        static byte[] doPInt(Node inliningTarget, PInt value, int size, boolean bigEndian,
                        @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
            final byte[] bytes = new byte[size];
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            try {
                support.putBigInteger(bytes, 0, value.getValue(), size);
            } catch (OverflowException oe) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, TOO_LARGE_TO_CONVERT, "int");
            }
            return bytes;
        }
    }

    /**
     * Equivalent to CPython's {@code _PyLong_FromByteArray}.
     */
    @GenerateInline(inlineByDefault = true)
    @GenerateUncached
    public abstract static class PyLongFromByteArray extends Node {
        public abstract Object execute(Node inliningTarget, byte[] data, boolean bigEndian, boolean signed);

        public final Object executeCached(byte[] data, boolean bigEndian, boolean signed) {
            return execute(this, data, bigEndian, signed);
        }

        public static Object executeUncached(byte[] data, boolean bigEndian, boolean signed) {
            return IntNodesFactory.PyLongFromByteArrayNodeGen.getUncached().execute(null, data, bigEndian, signed);
        }

        @Specialization
        static Object doOther(Node inliningTarget, byte[] data, boolean bigEndian, boolean signed,
                        @Cached InlinedBranchProfile fastPath1,
                        @Cached InlinedBranchProfile fastPath2,
                        @Cached InlinedBranchProfile fastPath4,
                        @Cached InlinedBranchProfile fastPath8,
                        @Cached InlinedBranchProfile generic,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            NumericSupport support = bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
            if (signed) {
                switch (data.length) {
                    case 1 -> {
                        fastPath1.enter(inliningTarget);
                        return (int) support.getByte(data, 0);
                    }
                    case 2 -> {
                        fastPath2.enter(inliningTarget);
                        return (int) support.getShort(data, 0);
                    }
                    case 4 -> {
                        fastPath4.enter(inliningTarget);
                        return support.getInt(data, 0);
                    }
                    case 8 -> {
                        fastPath8.enter(inliningTarget);
                        return support.getLong(data, 0);
                    }
                }
            }
            generic.enter(inliningTarget);
            try {
                BigInteger integer = support.getBigInteger(data, signed);
                if (PInt.bigIntegerFitsInLong(integer)) {
                    long longValue = PInt.longValue(integer);
                    return PInt.isIntRange(longValue) ? (int) longValue : longValue;
                } else {
                    return factory.createInt(integer);
                }
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.BYTE_ARRAY_TOO_LONG_TO_CONVERT_TO_INT);
            }
        }
    }
}
