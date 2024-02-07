/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;

import com.oracle.truffle.api.CompilerDirectives;

public enum ComparisonOp {

    EQ(J___EQ__, 2, a -> a == 0),
    NE(J___NE__, 3, a -> a != 0),
    LT(J___LT__, 0, a -> a < 0),
    GT(J___GT__, 4, a -> a > 0),
    LE(J___LE__, 1, a -> a <= 0),
    GE(J___GE__, 5, a -> a >= 0);

    public final String builtinName;
    /**
     * The integer code of the operation as used by CPython (and in native code).
     */
    public final int opCode;
    public final IntPredicate intPredicate;

    ComparisonOp(String builtinName, int opCode, IntPredicate intPredicate) {
        this.builtinName = builtinName;
        this.opCode = opCode;
        this.intPredicate = intPredicate;
    }

    public boolean cmpResultToBool(int cmpResult) {
        return intPredicate.test(cmpResult);
    }

    public boolean isEqualityOp() {
        return this == EQ || this == NE;
    }

    public static boolean isEqualityOpCode(int op) {
        assert EQ.opCode == 2 && NE.opCode == 3;
        return op == 2 || op == 3;
    }

    public static ComparisonOp fromOpCode(int op) {
        ComparisonOp result;
        switch (op) {
            case 0:
                result = LT;
                break;
            case 1:
                result = LE;
                break;
            case 2:
                result = EQ;
                break;
            case 3:
                result = NE;
                break;
            case 4:
                result = GT;
                break;
            case 5:
                result = GE;
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("unexpected operation: " + op);
        }
        assert result.opCode == op;
        return result;
    }
}
