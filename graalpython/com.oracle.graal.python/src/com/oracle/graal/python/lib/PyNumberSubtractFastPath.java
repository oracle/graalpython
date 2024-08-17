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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

@GenerateCached(false)
@TypeSystemReference(PythonIntegerTypes.class)
public abstract class PyNumberSubtractFastPath extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins
     */
    @Specialization(rewriteOn = ArithmeticException.class)
    public static int doII(int x, int y) throws ArithmeticException {
        return Math.subtractExact(x, y);
    }

    @Specialization(replaces = "doII")
    public static long doIIOvf(int x, int y) {
        return (long) x - (long) y;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public static long doLL(long x, long y) throws ArithmeticException {
        return Math.subtractExact(x, y);
    }

    @Specialization(replaces = "doLL")
    public static Object doLongWithOverflow(long x, long y,
                    @Bind("this") Node inliningTarget) {
        /* Inlined version of Math.subtractExact(x, y) with BigInteger fallback. */
        long r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        if (((x ^ y) & (x ^ r)) < 0) {
            return PFactory.createInt(PythonLanguage.get(inliningTarget), IntBuiltins.SubNode.sub(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
        }
        return r;
    }

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in FloatBuiltins
     */
    @Specialization
    public static double doDD(double left, double right) {
        return left - right;
    }

    @Specialization
    public static double doDL(double left, long right) {
        return left - right;
    }

    @Specialization
    public static double doLD(long left, double right) {
        return left - right;
    }
}
