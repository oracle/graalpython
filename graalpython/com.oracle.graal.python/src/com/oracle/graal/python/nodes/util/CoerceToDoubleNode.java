/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.function.Function;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class CoerceToDoubleNode extends PNodeWithContext {
    @Child private LookupAndCallUnaryNode callFloatNode;
    @Child private LookupAndCallUnaryNode callIndexNode;

    private final Function<Object, Double> typeErrorHandler;

    public CoerceToDoubleNode(Function<Object, Double> typeErrorHandler) {
        this.typeErrorHandler = typeErrorHandler;
    }

    public abstract double execute(VirtualFrame frame, Object x);

    public static CoerceToDoubleNode create() {
        return CoerceToDoubleNodeGen.create(null);
    }

    public static CoerceToDoubleNode create(Function<Object, Double> trypeErrorHandler) {
        return CoerceToDoubleNodeGen.create(trypeErrorHandler);
    }

    @Specialization
    public double toDouble(long x) {
        return x;
    }

    @Specialization
    public double toDouble(PInt x,
                    @Cached PRaiseNode raise) {
        double value = x.doubleValue();
        if (Double.isInfinite(value)) {
            throw raise.raise(OverflowError, "int too large to convert to float");
        }
        return value;
    }

    @Specialization
    public double toDouble(double x) {
        return x;
    }

    @Specialization(guards = "!isNumber(x)")
    public double toDouble(VirtualFrame frame, Object x,
                    @Cached PRaiseNode raise) {
        if (callFloatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callFloatNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__FLOAT__));
        }
        Object result = callFloatNode.executeObject(frame, x);
        if (result != PNone.NO_VALUE) {
            if (result instanceof PFloat) {
                return ((PFloat) result).getValue();
            }
            if (result instanceof Float || result instanceof Double) {
                return (double) result;
            }
            throw raise.raise(TypeError, "%p.__float__ returned non-float (type %p)", x, result);
        }

        if (callIndexNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callIndexNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__INDEX__));
        }
        result = callIndexNode.executeObject(frame, x);
        if (result != PNone.NO_VALUE) {
            if (result instanceof Integer) {
                return ((Integer) result).doubleValue();
            } else if (result instanceof Long) {
                return ((Long) result).doubleValue();
            } else if (result instanceof Boolean) {
                return (Boolean) result ? 1.0 : 0.0;
            } else if (result instanceof PInt) {
                return toDouble((PInt) result, raise);
            }
            throw raise.raise(TypeError, " __index__ returned non-int (type %p)", result);
        }
        if (typeErrorHandler == null) {
            throw raise.raise(TypeError, "must be real number, not %p", x);
        } else {
            return typeErrorHandler.apply(x);
        }
    }
}
