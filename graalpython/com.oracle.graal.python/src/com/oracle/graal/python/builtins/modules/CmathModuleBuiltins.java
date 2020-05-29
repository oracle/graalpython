/* Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

import java.util.List;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

@CoreFunctions(defineModule = "cmath")
public class CmathModuleBuiltins extends PythonBuiltins {

    // Constants used for the definition of special values tables in node classes
    static final double INF = Double.POSITIVE_INFINITY;
    static final double NAN = Double.NaN;
    static final double P = Math.PI;
    static final double P14 = 0.25 * Math.PI;
    static final double P12 = 0.5 * Math.PI;
    static final double P34 = 0.75 * Math.PI;

    static final double largeDouble = Double.MAX_VALUE / 4.0;       // used to avoid overflow
    static final double ln2 = 0.6931471805599453094;    // natural log of 2
    static final double ln10 = 2.302585092994045684;    // natural log of 10

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

    static PComplex specialValue(PythonObjectFactory factory, ComplexConstant[][] table, double real, double imag) {
        if (!Double.isFinite(real) || !Double.isFinite(imag)) {
            ComplexConstant c = table[SpecialType.ofDouble(real).ordinal()][SpecialType.ofDouble(imag).ordinal()];
            if (c == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
            return factory.createComplex(c.real, c.imag);
        }
        return null;
    }

    /**
     * Creates an instance of ComplexConstant. The name of this factory method is intentionally
     * short to allow nested classess compact definition of their tables of special values.
     *
     * @param real the real part of the complex constant
     * @param imag the imaginary part of the complex constant
     * @return a new instance of ComplexConstant representing the complex number real + i * imag
     */
    static ComplexConstant C(double real, double imag) {
        return new ComplexConstant(real, imag);
    }

    static class ComplexConstant {
        final double real;
        final double imag;

        ComplexConstant(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }
    }

    enum SpecialType {
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

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathComplexUnaryBuiltinNode extends PythonUnaryBuiltinNode {
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
        PComplex doGeneral(VirtualFrame frame, Object value,
                        @Cached CoerceToComplexNode coerceToComplex) {
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
        boolean doGeneral(VirtualFrame frame, Object value,
                        @Cached CoerceToComplexNode coerceToComplex) {
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
        double doGeneral(VirtualFrame frame, Object value,
                        @Cached CoerceToComplexNode coerceToComplex) {
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
        PTuple doC(PComplex value,
                        @Cached ComplexBuiltins.AbsNode absNode) {
            return toPolar(value, absNode);
        }

        @Specialization
        PTuple doGeneral(VirtualFrame frame, Object value,
                        @Cached CoerceToComplexNode coerceToComplex,
                        @Cached ComplexBuiltins.AbsNode absNode) {
            return toPolar(coerceToComplex.execute(frame, value), absNode);
        }

        private PTuple toPolar(PComplex value, ComplexBuiltins.AbsNode absNode) {
            double r = absNode.executeDouble(value);
            return factory().createTuple(new Object[]{r, Math.atan2(value.getImag(), value.getReal())});
        }
    }

    @Builtin(name = "rect", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class RectNode extends PythonBinaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, NAN), null,        C(-INF, 0.0), C(-INF, -0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), null,        null,         null,          null,        C(NAN, NAN), C(NAN, NAN)},
                {C(0.0, 0.0), null,        C(-0.0, 0.0), C(-0.0, -0.0), null,        C(0.0, 0.0), C(0.0, 0.0)},
                {C(0.0, 0.0), null,        C(0.0, -0.0), C(0.0, 0.0),   null,        C(0.0, 0.0), C(0.0, 0.0)},
                {C(NAN, NAN), null,        null,         null,          null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0), C(INF, 0.0),   null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, 0.0),  C(NAN, 0.0),   C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        PComplex doLL(long r, long phi) {
            return rect(r, phi);
        }

        @Specialization
        PComplex doLD(long r, double phi) {
            return rect(r, phi);
        }

        @Specialization
        PComplex doDL(double r, long phi) {
            return rect(r, phi);
        }

        @Specialization
        PComplex doDD(double r, double phi) {
            return rect(r, phi);
        }

        @Specialization(limit = "2")
        PComplex doGeneral(Object r, Object phi,
                        @CachedLibrary("r") PythonObjectLibrary rLib,
                        @CachedLibrary("phi") PythonObjectLibrary phiLib) {
            return rect(rLib.asJavaDouble(r), phiLib.asJavaDouble(phi));
        }

        private PComplex rect(double r, double phi) {
            // deal with special values
            if (!Double.isFinite(r) || !Double.isFinite(phi)) {
                // need to raise an exception if r is a nonzero number and phi is infinite
                if (r != 0.0 && !Double.isNaN(r) && Double.isInfinite(phi)) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }

                // if r is +/-infinity and phi is finite but nonzero then
                // result is (+-INF +-INF i), but we need to compute cos(phi)
                // and sin(phi) to figure out the signs.
                if (Double.isInfinite(r) && Double.isFinite(phi) && phi != 0.0) {
                    double real = Math.copySign(Double.POSITIVE_INFINITY, Math.cos(phi));
                    double imag = Math.copySign(Double.POSITIVE_INFINITY, Math.sin(phi));
                    if (r > 0) {
                        return factory().createComplex(real, imag);
                    } else {
                        return factory().createComplex(-real, -imag);
                    }
                }
                return specialValue(factory(), SPECIAL_VALUES, r, phi);
            }
            return factory().createComplex(r * Math.cos(phi), r * Math.sin(phi));
        }
    }

    @Builtin(name = "log", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class LogNode extends PythonBinaryBuiltinNode {

        abstract PComplex executeComplex(VirtualFrame frame, Object x, Object y);

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, -P34), C(INF, -P),   C(INF, -P),    C(INF, P),    C(INF, P),   C(INF, P34), C(INF, NAN)},
                {C(INF, -P12), null,         null,          null,         null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(-INF, -P),   C(-INF, P),   null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(-INF, -0.0), C(-INF, 0.0), null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         null,          null,         null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P14), C(INF, -0.0), C(INF, -0.0),  C(INF, 0.0),  C(INF, 0.0), C(INF, P14), C(INF, NAN)},
                {C(INF, NAN),  C(NAN, NAN),  C(NAN, NAN),   C(NAN, NAN),  C(NAN, NAN), C(INF, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        static LogNode create() {
            return CmathModuleBuiltinsFactory.LogNodeFactory.create();
        }

        @Specialization(guards = "isNoValue(y)")
        PComplex doComplexNone(PComplex x, @SuppressWarnings("unused") PNone y) {
            return log(x);
        }

        @Specialization
        PComplex doComplexComplex(VirtualFrame frame, PComplex x, PComplex y,
                        @Cached ComplexBuiltins.DivNode divNode) {
            return divNode.executeComplex(frame, log(x), log(y));
        }

        @Specialization(guards = "isNoValue(yObj)")
        PComplex doGeneral(VirtualFrame frame, Object xObj, @SuppressWarnings("unused") PNone yObj,
                        @Cached CoerceToComplexNode coerceXToComplex) {
            return log(coerceXToComplex.execute(frame, xObj));
        }

        @Specialization(guards = "!isNoValue(yObj)")
        PComplex doGeneral(VirtualFrame frame, Object xObj, Object yObj,
                        @Cached CoerceToComplexNode coerceXToComplex,
                        @Cached CoerceToComplexNode coerceYToComplex,
                        @Cached ComplexBuiltins.DivNode divNode) {
            PComplex x = log(coerceXToComplex.execute(frame, xObj));
            PComplex y = log(coerceYToComplex.execute(frame, yObj));
            return divNode.executeComplex(frame, x, y);
        }

        private PComplex log(PComplex z) {
            PComplex r = specialValue(factory(), SPECIAL_VALUES, z.getReal(), z.getImag());
            if (r != null) {
                return r;
            }
            double real = computeRealPart(z.getReal(), z.getImag());
            double imag = Math.atan2(z.getImag(), z.getReal());
            return factory().createComplex(real, imag);
        }

        private double computeRealPart(double real, double imag) {
            double ax = Math.abs(real);
            double ay = Math.abs(imag);

            if (ax > largeDouble || ay > largeDouble) {
                return Math.log(Math.hypot(ax / 2.0, ay / 2.0)) + ln2;
            }
            if (ax < Double.MIN_NORMAL && ay < Double.MIN_NORMAL) {
                if (ax > 0.0 || ay > 0.0) {
                    final double scaleUp = 0x1.0p53;
                    return Math.log(Math.hypot(ax * scaleUp, ay * scaleUp)) - 53 * ln2;
                }
                throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
            double h = Math.hypot(ax, ay);
            if (0.71 <= h && h <= 1.73) {
                double am = Math.max(ax, ay);
                double an = Math.min(ax, ay);
                return Math.log1p((am - 1) * (am + 1) + an * an) / 2.0;
            }
            return Math.log(h);
        }
    }

    @Builtin(name = "log10", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class Log10Node extends PythonUnaryBuiltinNode {
        @Child LogNode logNode = LogNode.create();

        @Specialization
        PComplex doComplex(VirtualFrame frame, PComplex z) {
            PComplex r = logNode.executeComplex(frame, z, PNone.NO_VALUE);
            return factory().createComplex(r.getReal() / ln10, r.getImag() / ln10);
        }

        @Specialization
        PComplex doGeneral(VirtualFrame frame, Object zObj,
                        @Cached CoerceToComplexNode coerceXToComplex) {
            return doComplex(frame, coerceXToComplex.execute(frame, zObj));
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
            PComplex result = specialValue(factory(), SPECIAL_VALUES, real, imag);
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
