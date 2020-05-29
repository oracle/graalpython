/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

import java.math.BigInteger;

public abstract class NumberLiteralSSTNode extends SSTNode {

    private NumberLiteralSSTNode(int startIndex, int endIndex) {
        super(startIndex, endIndex);
    }

    public static final class IntegerLiteralSSTNode extends NumberLiteralSSTNode {

        protected long value;

        protected IntegerLiteralSSTNode(long value, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public void negate() {
            assert value != Long.MIN_VALUE;
            value = -value;
        }

        @Override
        public boolean isNegative() {
            return value < 0;
        }
    }

    public static final class BigIntegerLiteralSSTNode extends NumberLiteralSSTNode {

        protected BigInteger value;

        protected BigIntegerLiteralSSTNode(BigInteger value, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public void negate() {
            value = value.negate();
        }

        @Override
        public boolean isNegative() {
            return value.signum() < 0;
        }
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    public static NumberLiteralSSTNode create(String rawValue, int start, int base, int startIndex, int endIndex) {
        String value = rawValue.replace("_", "");

        final long max = Long.MAX_VALUE;
        final long moltmax = max / base;
        int i = start;
        long result = 0;
        int lastD;
        boolean overunder = false;
        while (i < value.length()) {
            lastD = digitValue(value.charAt(i));

            long next = result;
            if (next > moltmax) {
                overunder = true;
            } else {
                next *= base;
                if (next > (max - lastD)) {
                    overunder = true;
                } else {
                    next += lastD;
                }
            }
            if (overunder) {
                // overflow
                BigInteger bigResult = BigInteger.valueOf(result);
                BigInteger bigBase = BigInteger.valueOf(base);
                while (i < value.length()) {
                    bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(value.charAt(i))));
                    i++;
                }
                return new BigIntegerLiteralSSTNode(bigResult, startIndex, endIndex);
            }
            result = next;
            i++;
        }

        return new IntegerLiteralSSTNode(result, startIndex, endIndex);
    }

    public abstract void negate();

    public abstract boolean isNegative();
}
