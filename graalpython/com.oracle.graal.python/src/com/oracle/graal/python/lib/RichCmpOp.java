/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

/**
 * Mirror of the CPython constants of the same name. The order is important: the ordinal should be
 * the same as the constant value on the CPython side.
 */
public enum RichCmpOp {
    Py_LT(T___LT__, "<"),
    Py_LE(T___LE__, "<="),
    Py_EQ(T___EQ__, "=="),
    Py_NE(T___NE__, "!="),
    Py_GT(T___GT__, ">"),
    Py_GE(T___GE__, ">=");

    public static final RichCmpOp[] VALUES = values();
    private final TruffleString pythonName;
    private final String opName;

    RichCmpOp(TruffleString pythonName, String opName) {
        this.pythonName = pythonName;
        this.opName = opName;
    }

    public static RichCmpOp fromNative(int value) {
        try {
            return VALUES[value];
        } catch (IndexOutOfBoundsException ex) {
            throw CompilerDirectives.shouldNotReachHere("Wrong 'op' argument to tp_richcompare");
        }
    }

    public static RichCmpOp fromName(TruffleString name) {
        for (RichCmpOp value : VALUES) {
            if (value.pythonName.equalsUncached(name, TS_ENCODING)) {
                return value;
            }
        }
        return null;
    }

    public final TruffleString getPythonName() {
        return pythonName;
    }

    public final String getOpName() {
        return opName;
    }

    /**
     * Returns the numeric value that is used by CPython C API.
     */
    public final int asNative() {
        return ordinal();
    }

    // Convenience shortcuts:
    public final boolean isEqOrNe() {
        return this == Py_EQ || this == Py_NE;
    }

    public final boolean isEq() {
        return this == Py_EQ;
    }

    public final boolean isNe() {
        return this == Py_NE;
    }

    public final boolean isLe() {
        return this == Py_LE;
    }

    public final boolean isLt() {
        return this == Py_LT;
    }

    public final boolean isGt() {
        return this == Py_GT;
    }

    public final boolean isGe() {
        return this == Py_GE;
    }

    /**
     * Converts the result of Java's "compare" static method, such as
     * {@link Integer#compare(int, int)} to a boolean value according to the given operation. If the
     * result is computed, make sure that it can never overflow!
     */
    public final boolean compareResultToBool(int result) {
        return switch (this) {
            case Py_LT -> result < 0;
            case Py_LE -> result <= 0;
            case Py_EQ -> result == 0;
            case Py_NE -> result != 0;
            case Py_GT -> result > 0;
            case Py_GE -> result >= 0;
        };
    }

    public final boolean compare(double a, double b) {
        return switch (this) {
            case Py_LT -> a < b;
            case Py_LE -> a <= b;
            case Py_EQ -> a == b;
            case Py_NE -> a != b;
            case Py_GT -> a > b;
            case Py_GE -> a >= b;
        };
    }

    public final boolean compare(long a, long b) {
        return switch (this) {
            case Py_LT -> a < b;
            case Py_LE -> a <= b;
            case Py_EQ -> a == b;
            case Py_NE -> a != b;
            case Py_GT -> a > b;
            case Py_GE -> a >= b;
        };
    }

    public final boolean compare(int a, int b) {
        return switch (this) {
            case Py_LT -> a < b;
            case Py_LE -> a <= b;
            case Py_EQ -> a == b;
            case Py_NE -> a != b;
            case Py_GT -> a > b;
            case Py_GE -> a >= b;
        };
    }

    public final boolean compare(byte a, byte b) {
        return switch (this) {
            case Py_LT -> a < b;
            case Py_LE -> a <= b;
            case Py_EQ -> a == b;
            case Py_NE -> a != b;
            case Py_GT -> a > b;
            case Py_GE -> a >= b;
        };
    }

    /**
     * Equivalent of {@code Py_RETURN_RICHCOMPARE} macro from CPython.
     */
    public final boolean compare(boolean a, boolean b) {
        return compareResultToBool(PInt.intValue(a) - PInt.intValue(b));
    }

    public final RichCmpOp getSwapped() {
        return switch (this) {
            case Py_LT -> Py_GT;
            case Py_LE -> Py_GE;
            case Py_EQ -> Py_EQ;
            case Py_NE -> Py_NE;
            case Py_GT -> Py_LT;
            case Py_GE -> Py_LE;
        };
    }
}
