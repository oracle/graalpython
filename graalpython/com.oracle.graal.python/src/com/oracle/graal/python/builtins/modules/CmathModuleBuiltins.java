/* Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.List;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

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

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathComplexUnaryBuiltinNode extends PythonUnaryBuiltinNode {

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
        }

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
         * Creates an instance of ComplexConstant. The name of this factory method is intentionally
         * short to allow subclassess compact definition of their tables of special values.
         *
         * @param real the real part of the complex constant
         * @param imag the imaginary part of the complex constant
         * @return a new instance of ComplexConstant representing the complex number real + i * imag
         */
        protected static ComplexConstant C(double real, double imag) {
            return new ComplexConstant(real, imag);
        }

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

        @Specialization
        PComplex doGeneral(VirtualFrame frame, Object value, @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, value));
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathBooleanUnaryBuiltinNode extends PythonUnaryBuiltinNode {

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

        @Specialization
        boolean doGeneral(VirtualFrame frame, Object value, @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, value));
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
    abstract static class PhaseNode extends PythonUnaryBuiltinNode {

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

        @Specialization
        double doGeneral(VirtualFrame frame, Object value, @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, value));
        }
    }

    @Builtin(name = "polar", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class PolarNode extends PythonUnaryBuiltinNode {

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
            // TODO: the implementation of abs(z) should be shared with ComplexBuiltins.AbsNode, but
            // it currently does not pass the overflow test
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
            return factory().createTuple(new Object[]{r, Math.atan2(value.getImag(), value.getReal())});
        }

        @Specialization
        PTuple doGeneral(VirtualFrame frame, Object value, @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, value));
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SqrtNode extends CmathComplexUnaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, -INF), C(0.0, -INF), C(0.0, -INF), C(0.0, INF), C(0.0, INF), C(INF, INF), C(NAN, INF)},
                {C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), C(INF, -0.0), C(INF, -0.0), C(INF, 0.0), C(INF, 0.0), C(INF, INF), C(INF, NAN)},
                {C(INF, -INF), C(NAN, NAN),  C(NAN, NAN),  C(NAN, NAN), C(NAN, NAN), C(INF, INF), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(double real, double imag) {
            PComplex result = specialValue(SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            if (real == 0.0 && imag == 0.0) {
                return factory().createComplex(0.0, imag);
            }

            double ax = Math.abs(real);
            double ay = Math.abs(imag);

            double s;
            if (ax < Double.MIN_NORMAL && ay < Double.MIN_NORMAL && (ax > 0.0 || ay > 0.0)) {
                final double scaleUp = 0x1.0p53;
                final double scaleDown = 0x1.0p-27;
                ax *= scaleUp;
                s = Math.sqrt(ax + Math.hypot(ax, ay * scaleUp)) * scaleDown;
            } else {
                ax /= 8.0;
                s = 2.0 * Math.sqrt(ax + Math.hypot(ax, ay / 8.0));
            }
            double d = ay / (2.0 * s);

            if (real >= 0.0) {
                return factory().createComplex(s, Math.copySign(d, imag));
            }
            return factory().createComplex(d, Math.copySign(s, imag));
        }
    }
}
