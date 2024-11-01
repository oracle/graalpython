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
package com.oracle.graal.python.pegparser.sst;

import java.math.BigInteger;

import com.oracle.graal.python.pegparser.tokenizer.CodePoints;

/**
 * This is the representation of the {@code constant} type used in asdl. In CPython, this is
 * represented by {@code PyObject *} and therefore can be any python object. To enable AST/PCode
 * sharing between contexts and to keep the parser independent of graalpy, the parser does not
 * produce Python values, but Java equivalents.
 */
public final class ConstantValue {

    public static final ConstantValue TRUE = new ConstantValue(true, Kind.BOOLEAN);
    public static final ConstantValue FALSE = new ConstantValue(false, Kind.BOOLEAN);
    public static final ConstantValue NONE = new ConstantValue(null, Kind.NONE);
    public static final ConstantValue ELLIPSIS = new ConstantValue(null, Kind.ELLIPSIS);

    private static final int CACHED_MIN = -1;
    private static final int CACHED_MAX = 30;
    private static final ConstantValue[] CACHED_LONGS;

    static {
        CACHED_LONGS = new ConstantValue[CACHED_MAX - CACHED_MIN + 1];
        for (int i = CACHED_MIN; i <= CACHED_MAX; ++i) {
            CACHED_LONGS[i - CACHED_MIN] = new ConstantValue((long) i, Kind.LONG);
        }
    }

    private final Object value;
    public final Kind kind;

    private ConstantValue(Object value, Kind kind) {
        assert kind != null && kind.checkValueType(value);
        this.value = value;
        this.kind = kind;
    }

    public BigInteger getBigInteger() {
        assert kind == Kind.BIGINTEGER;
        return (BigInteger) value;
    }

    public boolean getBoolean() {
        assert kind == Kind.BOOLEAN;
        return (Boolean) value;
    }

    public byte[] getBytes() {
        assert kind == Kind.BYTES;
        return (byte[]) value;
    }

    public double[] getComplex() {
        assert kind == Kind.COMPLEX;
        return (double[]) value;
    }

    public double getDouble() {
        assert kind == Kind.DOUBLE;
        return (Double) value;
    }

    public long getLong() {
        assert kind == Kind.LONG;
        return (Long) value;
    }

    public CodePoints getCodePoints() {
        assert kind == Kind.CODEPOINTS;
        return (CodePoints) value;
    }

    public ConstantValue[] getTupleElements() {
        assert kind == Kind.TUPLE;
        return (ConstantValue[]) value;
    }

    public ConstantValue[] getFrozensetElements() {
        assert kind == Kind.FROZENSET;
        return (ConstantValue[]) value;
    }

    public static ConstantValue ofBigInteger(BigInteger v) {
        return new ConstantValue(v, Kind.BIGINTEGER);
    }

    public static ConstantValue ofBoolean(boolean v) {
        return v ? TRUE : FALSE;
    }

    public static ConstantValue ofBytes(byte[] v) {
        return new ConstantValue(v, Kind.BYTES);
    }

    public static ConstantValue ofComplex(double real, double imag) {
        return new ConstantValue(new double[]{real, imag}, Kind.COMPLEX);
    }

    public static ConstantValue ofDouble(double v) {
        return new ConstantValue(v, Kind.DOUBLE);
    }

    public ConstantValue addComplex(ConstantValue right) {
        assert right.kind == Kind.COMPLEX;
        double ld = toDouble();
        double[] rd = right.getComplex();
        return ofComplex(ld + rd[0], rd[1]);
    }

    public ConstantValue subComplex(ConstantValue right) {
        assert right.kind == Kind.COMPLEX;
        double ld = toDouble();
        double[] rd = right.getComplex();
        return ofComplex(ld - rd[0], -rd[1]);
    }

    private double toDouble() {
        assert kind == Kind.BIGINTEGER || kind == Kind.DOUBLE || kind == Kind.LONG : kind;
        switch (kind) {
            case BIGINTEGER:
                return getBigInteger().doubleValue();
            case DOUBLE:
                return getDouble();
            case LONG:
                return getLong();
            default:
                throw new IllegalStateException("should not reach here");
        }
    }

    public ConstantValue negate() {
        assert kind == Kind.BIGINTEGER || kind == Kind.DOUBLE || kind == Kind.LONG || kind == Kind.COMPLEX : kind;
        switch (kind) {
            case BIGINTEGER:
                return ofBigInteger(getBigInteger().negate());
            case DOUBLE:
                return ofDouble(-getDouble());
            case LONG:
                long v = getLong();
                if (v != Long.MIN_VALUE) {
                    return ofLong(-v);
                } else {
                    return ofBigInteger(BigInteger.valueOf(v).negate());
                }
            case COMPLEX:
                double[] complex = getComplex();
                return ofComplex(-complex[0], -complex[1]);
            default:
                throw new IllegalStateException("should not reach here");
        }
    }

    public static ConstantValue ofLong(long v) {
        if (v >= CACHED_MIN && v <= CACHED_MAX) {
            return CACHED_LONGS[(int) (v - CACHED_MIN)];
        }
        return new ConstantValue(v, Kind.LONG);
    }

    public static ConstantValue ofCodePoints(CodePoints cp) {
        return new ConstantValue(cp, Kind.CODEPOINTS);
    }

    public static ConstantValue ofTuple(ConstantValue[] values) {
        return new ConstantValue(values, Kind.TUPLE);
    }

    public static ConstantValue ofFrozenset(ConstantValue[] values) {
        return new ConstantValue(values, Kind.FROZENSET);
    }

    public enum Kind {
        NONE() {
            @Override
            boolean checkValueType(Object value) {
                return value == null;
            }
        },
        ELLIPSIS() {
            @Override
            boolean checkValueType(Object value) {
                return value == null;
            }
        },
        BOOLEAN() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof Boolean;
            }
        },
        LONG() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof Long;
            }
        },
        DOUBLE() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof Double;
            }
        },
        COMPLEX() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof double[] && ((double[]) value).length == 2;
            }
        },
        BIGINTEGER() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof BigInteger;
            }
        },
        CODEPOINTS() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof CodePoints;
            }
        },
        BYTES() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof byte[];
            }
        },
        TUPLE() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof ConstantValue[];
            }
        },
        FROZENSET() {
            @Override
            boolean checkValueType(Object value) {
                return value instanceof ConstantValue[];
            }
        };

        abstract boolean checkValueType(Object value);
    }
}
