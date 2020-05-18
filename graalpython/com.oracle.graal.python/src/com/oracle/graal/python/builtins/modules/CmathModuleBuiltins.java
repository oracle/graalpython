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
package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToDoubleNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.List;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

@CoreFunctions(defineModule = "cmath")
public class CmathModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CmathModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        // Add constant values
        builtinConstants.put("pi", Math.PI);
        builtinConstants.put("e", Math.E);
        builtinConstants.put("tau", 2 * Math.PI);
        builtinConstants.put("inf", Double.POSITIVE_INFINITY);
        builtinConstants.put("nan", Double.NaN);
        builtinConstants.put("infj", core.factory().createComplex(0, Double.POSITIVE_INFINITY));
        builtinConstants.put("nanj", core.factory().createComplex(0, Double.NaN));
        super.initialize(core);
    }

    //TODO should this be replaced with something like CoerceToComplexNode?
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CmathUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Child private LookupAndCallUnaryNode callComplexFunc;
        @Child private CoerceToDoubleNode coerceToDouble;

        PComplex getComplexNumberFromObject(VirtualFrame frame, Object object) {
            //TODO taken from BuiltinConstructors, should probably be refactored somehow
            if (callComplexFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callComplexFunc = insert(LookupAndCallUnaryNode.create(__COMPLEX__));
            }
            Object result = callComplexFunc.executeObject(frame, object);
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
                coerceToDouble = insert(CoerceToDoubleNode.create());
            }
            return factory().createComplex(coerceToDouble.execute(frame, object), 0);
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathComplexUnaryBuiltinNode extends CmathUnaryBuiltinNode {

        // Constants used for the definition of special values tables in subclassess
        static final double INF = Double.POSITIVE_INFINITY;
        static final double NAN = Double.NaN;

        protected static class ComplexConstant {
            final double real;
            final double imag;

            ComplexConstant(double real, double imag) {
                this.real = real;
                this.imag = imag;
            }
        }

        private enum SpecialType {
            NINF,           // 0, negative infinity
            NEG,            // 1, negative finite number (nonzero)
            NZERO,          // 2, -0.0
            PZERO,          // 3, +0.0
            POS,            // 4, positive finite number (nonzero)
            PINF,           // 5, positive infinity
            NAN;            // 6, Not a Number

            static SpecialType ofDouble(double d) {
                if (Double.isFinite(d)) {
                    if (d != 0) {
                        if (Math.copySign(1.0, d) == 1.0) {
                            return POS;
                        } else {
                            return NEG;
                        }
                    } else {
                        if (Math.copySign(1.0, d) == 1.0) {
                            return PZERO;
                        } else {
                            return NZERO;
                        }
                    }
                }
                if (Double.isNaN(d)) {
                    return NAN;
                }
                if (Math.copySign(1.0, d) == 1.0) {
                    return PINF;
                } else {
                    return NINF;
                }
            }
        };

        protected PComplex specialValue(ComplexConstant[][] table, double real, double imag) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                ComplexConstant c = table[SpecialType.ofDouble(real).ordinal()][SpecialType.ofDouble(imag).ordinal()];
                if (c == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("should not be reached");
                }
                return factory().createComplex(c.real, c.imag);
            }
            return null;
        }

        /**
         * Creates an instance of ComplexConstant. The name of this factory method is intentionally short to allow
         * subclassess compact definition of their tables of special values.
         * @param real the real part of the complex constant
         * @param imag the imaginary part of the complex constant
         * @return a new instance of ComplexConstant representing the complex number real + i * imag
         */
        protected static ComplexConstant C(double real, double imag) {
            return new ComplexConstant(real, imag);
        }

        abstract PComplex executeObject(VirtualFrame frame, Object value);

        PComplex compute(@SuppressWarnings("unused") double real, @SuppressWarnings("unused") double imag) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not be reached");
        }

        @Specialization
        PComplex doL(long value) {
            return compute(value, 0);
        }

        @Specialization
        PComplex doD(double value) {
            return compute(value, 0);
        }

        @Specialization
        PComplex doC(PComplex value) {
            return compute(value.getReal(), value.getImag());
        }

        @Specialization(guards = "!isNumber(value)")
        PComplex doGeneral(VirtualFrame frame, Object value) {
            return doC(getComplexNumberFromObject(frame, value));
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathBooleanUnaryBuiltinNode extends CmathUnaryBuiltinNode {

        abstract boolean executeObject(VirtualFrame frame, Object value);

        boolean compute(@SuppressWarnings("unused") double real, @SuppressWarnings("unused") double imag) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not be reached");
        }

        @Specialization
        boolean doL(long value) {
            return compute(value, 0);
        }

        @Specialization
        boolean doD(double value) {
            return compute(value, 0);
        }

        @Specialization
        boolean doC(PComplex value) {
            return compute(value.getReal(), value.getImag());
        }

        @Specialization(guards = "!isNumber(value)")
        boolean doGeneral(VirtualFrame frame, Object value) {
            return doC(getComplexNumberFromObject(frame, value));
        }
    }

    @Builtin(name = "isnan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsNanNode extends CmathBooleanUnaryBuiltinNode {
        @Override
        boolean compute(double real, double imag) {
            return Double.isNaN(real) || Double.isNaN(imag);
        }
    }

    @Builtin(name = "isinf", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsInfNode extends CmathBooleanUnaryBuiltinNode {
        @Override
        boolean compute(double real, double imag) {
            return Double.isInfinite(real) || Double.isInfinite(imag);
        }
    }

    @Builtin(name = "isfinite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsFiniteNode extends CmathBooleanUnaryBuiltinNode {
        @Override
        boolean compute(double real, double imag) {
            return Double.isFinite(real) && Double.isFinite(imag);
        }
    }

    @Builtin(name = "phase", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class PhaseNode extends CmathUnaryBuiltinNode {

        abstract double executeObject(VirtualFrame frame, Object value);

        @Specialization
        double doL(long value) {
            return value < 0 ? Math.PI : 0;
        }

        @Specialization
        double doD(double value) {
            return value < 0 ? Math.PI : 0;
        }

        @Specialization
        double doC(PComplex value) {
            return Math.atan2(value.getImag(), value.getReal());
        }

        @Specialization(guards = "!isNumber(value)")
        double doGeneral(VirtualFrame frame, Object value) {
            return doC(getComplexNumberFromObject(frame, value));
        }
    }

    @Builtin(name = "polar", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class PolarNode extends CmathUnaryBuiltinNode {

        abstract PTuple executeObject(VirtualFrame frame, Object value);

        @Specialization
        PTuple doL(long value) {
            return doD(value);
        }

        @Specialization
        PTuple doD(double value) {
            return factory().createTuple(new Object[]{Math.abs(value), value < 0 ? Math.PI : 0});
        }

        @Specialization
        PTuple doC(PComplex value) {
            //TODO: the implementation of abs(z) should be shared with ComplexBuiltins.AbsNode, but it currently does
            //not pass the overflow test
            double r;
            if (!Double.isFinite(value.getReal()) || !Double.isFinite(value.getImag())) {
                if (Double.isInfinite(value.getReal())) {
                    r = Math.abs(value.getReal());
                } else if (Double.isInfinite(value.getImag())) {
                    r = Math.abs(value.getImag());
                } else {
                    r = Double.NaN;
                }
            } else {
                r = Math.hypot(value.getReal(), value.getImag());
                if (Double.isInfinite(r)) {
                    throw raise(OverflowError, "absolute value too large");
                }
            }
            return factory().createTuple(new Object[]{ r, Math.atan2(value.getImag(), value.getReal()) });
        }

        @Specialization(guards = "!isNumber(value)")
        PTuple doGeneral(VirtualFrame frame, Object value) {
            return doC(getComplexNumberFromObject(frame, value));
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SqrtNode extends CmathComplexUnaryBuiltinNode {

        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                { C(INF, -INF), C(0.0, -INF), C(0.0, -INF), C(0.0, INF), C(0.0, INF), C(INF, INF), C(NAN, INF) },
                { C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN) },
                { C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN) },
                { C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN) },
                { C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN) },
                { C(INF, -INF), C(INF, -0.0), C(INF, -0.0), C(INF, 0.0), C(INF, 0.0), C(INF, INF), C(INF, NAN) },
                { C(INF, -INF), C(NAN, NAN),  C(NAN, NAN),  C(NAN, NAN), C(NAN, NAN), C(INF, INF), C(NAN, NAN) },
        };

        @Override
        PComplex compute(double real, double imag) {
            PComplex result = specialValue(SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }

            double ax = Math.abs(real);
            double ay = Math.abs(imag);

            double s;
            if (ax < Double.MIN_NORMAL && ay < Double.MIN_NORMAL && (ax > 0.0 || ay > 0.0)) {
                final double SCALE_UP = 0x1.0p53;
                final double SCALE_DOWN = 0x1.0p-27;
                ax *= SCALE_UP;
                s = Math.sqrt(ax + Math.hypot(ax, ay * SCALE_UP)) * SCALE_DOWN;
            } else {
                ax /= 8.0;
                s = 2.0 * Math.sqrt(ax + Math.hypot(ax, ay / 8.0));
            }
            double d = ay / (2.0 * s);

            if (real >= 0.0)
            {
                return factory().createComplex(s, Math.copySign(d, imag));
            }
            return factory().createComplex(d, Math.copySign(s, imag));
        }
    }
}
