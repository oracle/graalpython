/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class CoerceToComplexNode extends PythonBuiltinBaseNode {
    @Child private LookupAndCallUnaryNode callComplexFunc;
    @Child private CoerceToDoubleNode coerceToDouble;

    public abstract PComplex execute(VirtualFrame frame, Object x);

    static CoerceToComplexNode create() {
        return CoerceToComplexNodeGen.create();
    }

    @Specialization
    PComplex toComplex(long x) {
        return factory().createComplex(x, 0);
    }

    @Specialization
    PComplex toComplex(double x) {
        return factory().createComplex(x, 0);
    }

    @Specialization
    PComplex toComplex(VirtualFrame frame, Object x) {
        if (x instanceof PComplex) {
            return (PComplex) x;
        }
        // TODO taken from BuiltinConstructors, should probably be refactored somehow
        if (callComplexFunc == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callComplexFunc = insert(LookupAndCallUnaryNode.create(__COMPLEX__));
        }
        Object result = callComplexFunc.executeObject(frame, x);
        if (result != PNone.NO_VALUE) {
            if (result instanceof PComplex) {
                // TODO we need pass here deprecation warning
                // DeprecationWarning: __complex__ returned non-complex (type %p).
                // The ability to return an instance of a strict subclass of complex is
                // deprecated,
                // and may be removed in a future version of Python.
                return (PComplex) result;
            } else {
                throw raise(TypeError, "__complex__ should return a complex object");
            }
        }
        if (coerceToDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            coerceToDouble = insert(CoerceToDoubleNode.create());
        }
        return factory().createComplex(coerceToDouble.execute(frame, x), 0);
    }
}
