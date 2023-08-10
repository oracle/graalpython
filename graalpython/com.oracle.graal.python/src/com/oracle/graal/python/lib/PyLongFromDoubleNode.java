/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(MathGuards.class)
public abstract class PyLongFromDoubleNode extends Node {
    public abstract Object execute(Node inliningTarget, double value);

    public static Object executeUncached(double value) {
        return PyLongFromDoubleNodeGen.getUncached().execute(null, value);
    }

    @Specialization(guards = "fitInt(value)")
    static int doInt(double value) {
        return (int) value;
    }

    @Specialization(guards = "fitLong(value)")
    static long doLong(double value) {
        return (long) value;
    }

    @Specialization(guards = {"!fitLong(value)", "isFinite(value)"})
    static Object doFinite(double value,
                    @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createInt(toBigInteger(value));
    }

    @Specialization(guards = "!isFinite(value)")
    static Object doInfinite(double value,
                    @Cached(inline = false) PRaiseNode raiseNode) {
        if (Double.isNaN(value)) {
            throw raiseNode.raise(ValueError, ErrorMessages.CANNOT_CONVERT_FLOAT_NAN_TO_INTEGER);
        }
        assert Double.isInfinite(value);
        throw raiseNode.raise(OverflowError, ErrorMessages.CANNOT_CONVERT_FLOAT_INFINITY_TO_INTEGER);
    }

    @TruffleBoundary
    private static BigInteger toBigInteger(double self) {
        return new BigDecimal(self, MathContext.UNLIMITED).toBigInteger();
    }

    protected static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
