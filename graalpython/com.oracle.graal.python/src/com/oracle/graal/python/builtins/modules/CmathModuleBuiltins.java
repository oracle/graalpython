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
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

import java.util.List;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
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

    static final double LARGE_DOUBLE = Double.MAX_VALUE / 4.0;       // used to avoid overflow
    static final double LOG_LARGE_DOUBLE = Math.log(LARGE_DOUBLE);
    static final double LN_2 = 0.6931471805599453094;    // natural log of 2
    static final double LN_10 = 2.302585092994045684;    // natural log of 10

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

        @TruffleBoundary
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

        public abstract PComplex executeComplex(VirtualFrame frame, Object value);

        @SuppressWarnings("unused")
        PComplex compute(VirtualFrame frame, double real, double imag) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not be reached");
        }

        @Specialization
        PComplex doL(VirtualFrame frame, long value) {
            return compute(frame, value, 0);
        }

        @Specialization
        PComplex doD(VirtualFrame frame, double value) {
            return compute(frame, value, 0);
        }

        @Specialization
        PComplex doC(VirtualFrame frame, PComplex value) {
            return compute(frame, value.getReal(), value.getImag());
        }

        @Specialization
        PComplex doGeneral(VirtualFrame frame, Object value,
                        @Cached CoerceToComplexNode coerceToComplex) {
            return doC(frame, coerceToComplex.execute(frame, value));
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

        @TruffleBoundary
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

        @TruffleBoundary
        private double computeRealPart(double real, double imag) {
            double ax = Math.abs(real);
            double ay = Math.abs(imag);

            if (ax > LARGE_DOUBLE || ay > LARGE_DOUBLE) {
                return Math.log(Math.hypot(ax / 2.0, ay / 2.0)) + LN_2;
            }
            if (ax < Double.MIN_NORMAL && ay < Double.MIN_NORMAL) {
                if (ax > 0.0 || ay > 0.0) {
                    final double scaleUp = 0x1.0p53;
                    return Math.log(Math.hypot(ax * scaleUp, ay * scaleUp)) - 53 * LN_2;
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
            return factory().createComplex(r.getReal() / LN_10, r.getImag() / LN_10);
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
        PComplex compute(VirtualFrame frame, double real, double imag) {
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

        static SqrtNode create() {
            return CmathModuleBuiltinsFactory.SqrtNodeFactory.create();
        }
    }

    @Builtin(name = "acos", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcosNode extends CmathComplexUnaryBuiltinNode {

        @Child private SqrtNode sqrtNode = SqrtNode.create();
        @Child private MathModuleBuiltins.AsinhNode realAsinhNode = MathModuleBuiltins.AsinhNode.create();

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(P34, INF), C(P, INF),   C(P, INF),   C(P, -INF),   C(P, -INF),   C(P34, -INF), C(NAN, INF)},
                {C(P12, INF), null,        null,        null,         null,         C(P12, -INF), C(NAN, NAN)},
                {C(P12, INF), null,        C(P12, 0.0), C(P12, -0.0), null,         C(P12, -INF), C(P12, NAN)},
                {C(P12, INF), null,        C(P12, 0.0), C(P12, -0.0), null,         C(P12, -INF), C(P12, NAN)},
                {C(P12, INF), null,        null,        null,         null,         C(P12, -INF), C(NAN, NAN)},
                {C(P14, INF), C(0.0, INF), C(0.0, INF), C(0.0, -INF), C(0.0, -INF), C(P14, -INF), C(NAN, INF)},
                {C(NAN, INF), C(NAN, NAN), C(NAN, NAN), C(NAN, NAN),  C(NAN, NAN),  C(NAN, -INF), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex result = specialValue(factory(), SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }

            double rreal;
            double rimag;
            if (Math.abs(real) > LARGE_DOUBLE || Math.abs(imag) > LARGE_DOUBLE) {
                rreal = Math.atan2(Math.abs(imag), real);
                double s = Math.log(Math.hypot(real / 2.0, imag / 2.0)) + LN_2 * 2.0;
                if (real < 0.0) {
                    rimag = -Math.copySign(s, imag);
                } else {
                    rimag = Math.copySign(s, -imag);
                }
            } else {
                PComplex s1 = sqrtNode.executeComplex(frame, factory().createComplex(1.0 - real, -imag));
                PComplex s2 = sqrtNode.executeComplex(frame, factory().createComplex(1.0 + real, imag));
                rreal = 2.0 * Math.atan2(s1.getReal(), s2.getReal());
                rimag = realAsinhNode.executeObject(frame, s2.getReal() * s1.getImag() - s2.getImag() * s1.getReal());
            }
            return factory().createComplex(rreal, rimag);
        }
    }

    @Builtin(name = "acosh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcoshNode extends CmathComplexUnaryBuiltinNode {

        @Child private SqrtNode sqrtNode = SqrtNode.create();
        @Child private MathModuleBuiltins.AsinhNode realAsinhNode = MathModuleBuiltins.AsinhNode.create();

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, -P34), C(INF, -P),   C(INF, -P),   C(INF, P),   C(INF, P),   C(INF, P34), C(INF, NAN)},
                {C(INF, -P12), null,         null,         null,        null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(0.0, -P12), C(0.0, P12), null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(0.0, -P12), C(0.0, P12), null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         null,         null,        null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P14), C(INF, -0.0), C(INF, -0.),  C(INF, 0.0), C(INF, 0.0), C(INF, P14), C(INF, NAN)},
                {C(INF, NAN),  C(NAN, NAN),  C(NAN, NAN),  C(NAN, NAN), C(NAN, NAN), C(INF, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex result = specialValue(factory(), SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            double rreal;
            double rimag;
            if (Math.abs(real) > LARGE_DOUBLE || Math.abs(imag) > LARGE_DOUBLE) {
                rreal = Math.log(Math.hypot(real / 2.0, imag / 2.0)) + LN_2 * 2.0;
                rimag = Math.atan2(imag, real);
            } else {
                PComplex s1 = sqrtNode.executeComplex(frame, factory().createComplex(real - 1.0, imag));
                PComplex s2 = sqrtNode.executeComplex(frame, factory().createComplex(real + 1.0, imag));
                rreal = realAsinhNode.executeObject(frame, s1.getReal() * s2.getReal() + s1.getImag() * s2.getImag());
                rimag = 2.0 * Math.atan2(s1.getImag(), s2.getReal());
            }
            return factory().createComplex(rreal, rimag);
        }
    }

    @Builtin(name = "asin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsinNode extends CmathComplexUnaryBuiltinNode {

        @Child private AsinhNode asinhNode = AsinhNode.create();

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex s = asinhNode.executeComplex(frame, factory().createComplex(-imag, real));
            return factory().createComplex(s.getImag(), -s.getReal());
        }
    }

    @Builtin(name = "asinh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsinhNode extends CmathComplexUnaryBuiltinNode {

        @Child private SqrtNode sqrtNode = SqrtNode.create();
        @Child private MathModuleBuiltins.AsinhNode realAsinhNode = MathModuleBuiltins.AsinhNode.create();

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(-INF, -P14), C(-INF, -0.0), C(-INF, -0.0), C(-INF, 0.0), C(-INF, 0.0), C(-INF, P14), C(-INF, NAN)},
                {C(-INF, -P12), null,          null,          null,         null,         C(-INF, P12), C(NAN, NAN)},
                {C(-INF, -P12), null,          C(-0.0, -0.0), C(-0.0, 0.0), null,         C(-INF, P12), C(NAN, NAN)},
                {C(INF, -P12),  null,          C(0.0, -0.0),  C(0.0, 0.0),  null,         C(INF, P12),  C(NAN, NAN)},
                {C(INF, -P12),  null,          null,          null,         null,         C(INF, P12),  C(NAN, NAN)},
                {C(INF, -P14),  C(INF, -0.0),  C(INF, -0.0),  C(INF, 0.0),  C(INF, 0.0),  C(INF, P14),  C(INF, NAN)},
                {C(INF, NAN),   C(NAN, NAN),   C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN),  C(INF, NAN),  C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex result = specialValue(factory(), SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            double rreal;
            double rimag;
            if (Math.abs(real) > LARGE_DOUBLE || Math.abs(imag) > LARGE_DOUBLE) {
                double s = Math.log(Math.hypot(real / 2.0, imag / 2.0)) + LN_2 * 2.0;
                if (imag >= 0.0) {
                    rreal = Math.copySign(s, real);
                } else {
                    rreal = -Math.copySign(s, -real);
                }
                rimag = Math.atan2(imag, Math.abs(real));
            } else {
                PComplex s1 = sqrtNode.executeComplex(frame, factory().createComplex(1.0 + imag, -real));
                PComplex s2 = sqrtNode.executeComplex(frame, factory().createComplex(1.0 - imag, real));
                rreal = realAsinhNode.executeObject(frame, s1.getReal() * s2.getImag() - s2.getReal() * s1.getImag());
                rimag = Math.atan2(imag, s1.getReal() * s2.getReal() - s1.getImag() * s2.getImag());
            }
            return factory().createComplex(rreal, rimag);
        }

        static AsinhNode create() {
            return CmathModuleBuiltinsFactory.AsinhNodeFactory.create();
        }
    }

    @Builtin(name = "atan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AtanNode extends CmathComplexUnaryBuiltinNode {

        @Child private AtanhNode atanhNode = AtanhNode.create();

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex s = atanhNode.executeComplex(frame, factory().createComplex(-imag, real));
            return factory().createComplex(s.getImag(), -s.getReal());
        }
    }

    @Builtin(name = "atanh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AtanhNode extends CmathComplexUnaryBuiltinNode {

        static final double SQRT_LARGE_DOUBLE = Math.sqrt(LARGE_DOUBLE);
        static final double CM_SQRT_DBL_MIN = Math.sqrt(Double.MIN_NORMAL);

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(-0.0, -P12), C(-0.0, -P12), C(-0.0, -P12), C(-0.0, P12), C(-0.0, P12), C(-0.0, P12), C(-0.0, NAN)},
                {C(-0.0, -P12), null,          null,          null,         null,         C(-0.0, P12), C(NAN, NAN)},
                {C(-0.0, -P12), null,          C(-0.0, -0.0), C(-0.0, 0.0), null,         C(-0.0, P12), C(-0.0, NAN)},
                {C(0.0, -P12),  null,          C(0.0, -0.0),  C(0.0, 0.0),  null,         C(0.0, P12),  C(0.0, NAN)},
                {C(0.0, -P12),  null,          null,          null,         null,         C(0.0, P12),  C(NAN, NAN)},
                {C(0.0, -P12),  C(0.0, -P12),  C(0.0, -P12),  C(0.0, P12),  C(0.0, P12),  C(0.0, P12),  C(0.0, NAN)},
                {C(0.0, -P12),  C(NAN, NAN),   C(NAN, NAN),   C(NAN, NAN),  C(NAN, NAN),  C(0.0, P12),  C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex result = specialValue(factory(), SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            if (real < 0.0) {
                return computeWithRealPositive(-real, -imag, -1.0);
            }
            return computeWithRealPositive(real, imag, 1.0);
        }

        private PComplex computeWithRealPositive(double real, double imag, double resultScale) {
            double rreal;
            double rimag;
            double ay = Math.abs(imag);
            if (real > SQRT_LARGE_DOUBLE || ay > SQRT_LARGE_DOUBLE) {
                double h = Math.hypot(real / 2.0, imag / 2.0);
                rreal = real / 4.0 / h / h;
                rimag = -Math.copySign(Math.PI / 2.0, -imag);
            } else if (real == 1.0 && ay < CM_SQRT_DBL_MIN) {
                if (ay == 0.0) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                rreal = -Math.log(Math.sqrt(ay) / Math.sqrt(Math.hypot(ay, 2.0)));
                rimag = Math.copySign(Math.atan2(2.0, -ay) / 2, imag);
            } else {
                rreal = Math.log1p(((4.0 * real) / ((1 - real) * (1 - real) + ay * ay))) / 4.0;
                rimag = -Math.atan2(-2.0 * imag, (1 - real) * (1 + real) - ay * ay) / 2.0;
            }
            return factory().createComplex(resultScale * rreal, resultScale * rimag);
        }

        static AtanhNode create() {
            return CmathModuleBuiltinsFactory.AtanhNodeFactory.create();
        }
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ExpNode extends CmathComplexUnaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(0.0, 0.0), null,        C(0.0, -0.0), C(0.0, 0.0), null,        C(0.0, 0.0), C(0.0, 0.0)},
                {C(NAN, NAN), null,        null,         null,        null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        C(1.0, -0.0), C(1.0, 0.0), null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        C(1.0, -0.0), C(1.0, 0.0), null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        null,         null,        null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0), C(INF, 0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, -0.0), C(NAN, 0.0), C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                PComplex r;
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    if (real > 0) {
                        r = factory().createComplex(Math.copySign(INF, Math.cos(imag)), Math.copySign(INF, Math.sin(imag)));
                    } else {
                        r = factory().createComplex(Math.copySign(0.0, Math.cos(imag)), Math.copySign(0.0, Math.sin(imag)));
                    }
                } else {
                    r = specialValue(factory(), SPECIAL_VALUES, real, imag);
                }
                if (Double.isInfinite(imag) && (Double.isFinite(real) || (Double.isInfinite(real) && real > 0))) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                return r;
            }

            double rreal, rimag;
            if (real > LOG_LARGE_DOUBLE) {
                double l = Math.exp(real - 1.0);
                rreal = l * Math.cos(imag) * Math.E;
                rimag = l * Math.sin(imag) * Math.E;
            } else {
                double l = Math.exp(real);
                rreal = l * Math.cos(imag);
                rimag = l * Math.sin(imag);
            }
            if (Double.isInfinite(rreal) || Double.isInfinite(rimag)) {
                throw raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return factory().createComplex(rreal, rimag);
        }
    }

    @Builtin(name = "cos", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CosNode extends CmathComplexUnaryBuiltinNode {

        @Child private CoshNode coshNode = CoshNode.create();

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            return coshNode.executeComplex(frame, factory().createComplex(-imag, real));
        }
    }

    @Builtin(name = "cosh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CoshNode extends CmathComplexUnaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, NAN), null,        C(INF, 0.0),  C(INF, -0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), null,        null,         null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, 0.0), null,        C(1.0, 0.0),  C(1.0, -0.0), null,        C(NAN, 0.0), C(NAN, 0.0)},
                {C(NAN, 0.0), null,        C(1.0, -0.0), C(1.0, 0.0),  null,        C(NAN, 0.0), C(NAN, 0.0)},
                {C(NAN, NAN), null,        null,         null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0), C(INF, 0.0),  null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, 0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && !Double.isNaN(real)) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    double r = Math.copySign(INF, Math.sin(imag));
                    return factory().createComplex(Math.copySign(INF, Math.cos(imag)), real > 0 ? r : -r);
                } else {
                    return specialValue(factory(), SPECIAL_VALUES, real, imag);
                }
            }

            double rreal, rimag;
            if (Math.abs(real) > LOG_LARGE_DOUBLE) {
                double x_minus_one = real - Math.copySign(1.0, real);
                rreal = Math.cos(imag) * Math.cosh(x_minus_one) * Math.E;
                rimag = Math.sin(imag) * Math.sinh(x_minus_one) * Math.E;
            } else {
                rreal = Math.cos(imag) * Math.cosh(real);
                rimag = Math.sin(imag) * Math.sinh(real);
            }
            if (Double.isInfinite(rreal) || Double.isInfinite(rimag)) {
                throw raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return factory().createComplex(rreal, rimag);
        }

        static CoshNode create() {
            return CmathModuleBuiltinsFactory.CoshNodeFactory.create();
        }
    }

    @Builtin(name = "sin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SinNode extends CmathComplexUnaryBuiltinNode {

        @Child private SinhNode sinhNode = SinhNode.create();

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex s = sinhNode.executeComplex(frame, factory().createComplex(-imag, real));
            return factory().createComplex(s.getImag(), -s.getReal());
        }
    }

    @Builtin(name = "sinh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SinhNode extends CmathComplexUnaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(INF, NAN), null,        C(-INF, -0.0), C(-INF, 0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), null,        null,          null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(0.0, NAN), null,        C(-0.0, -0.0), C(-0.0, 0.0), null,        C(0.0, NAN), C(0.0, NAN)},
                {C(0.0, NAN), null,        C(0.0, -0.0),  C(0.0, 0.0),  null,        C(0.0, NAN), C(0.0, NAN)},
                {C(NAN, NAN), null,        null,          null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0),  C(INF, 0.0),  null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && !Double.isNaN(real)) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    double r = Math.copySign(INF, Math.cos(imag));
                    return factory().createComplex(real > 0 ? r : -r, Math.copySign(INF, Math.sin(imag)));
                } else {
                    return specialValue(factory(), SPECIAL_VALUES, real, imag);
                }
            }

            double rreal, rimag;
            if (Math.abs(real) > LOG_LARGE_DOUBLE) {
                double x_minus_one = real - Math.copySign(1.0, real);
                rreal = Math.cos(imag) * Math.sinh(x_minus_one) * Math.E;
                rimag = Math.sin(imag) * Math.cosh(x_minus_one) * Math.E;
            } else {
                rreal = Math.cos(imag) * Math.sinh(real);
                rimag = Math.sin(imag) * Math.cosh(real);
            }
            if (Double.isInfinite(rreal) || Double.isInfinite(rimag)) {
                throw raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return factory().createComplex(rreal, rimag);
        }

        static SinhNode create() {
            return CmathModuleBuiltinsFactory.SinhNodeFactory.create();
        }
    }

    @Builtin(name = "tan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TanNode extends CmathComplexUnaryBuiltinNode {

        @Child private TanhNode tanhNode = TanhNode.create();

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            PComplex s = tanhNode.executeComplex(frame, factory().createComplex(-imag, real));
            return factory().createComplex(s.getImag(), -s.getReal());
        }
    }

    @Builtin(name = "tanh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TanhNode extends CmathComplexUnaryBuiltinNode {

        // @formatter:off
        @CompilerDirectives.CompilationFinal(dimensions = 2)
        private static final ComplexConstant[][] SPECIAL_VALUES = {
                {C(-1.0, 0.0), null,        C(-1.0, -0.0), C(-1.0, 0.0), null,        C(-1.0, 0.0), C(-1.0, 0.0)},
                {C(NAN, NAN),  null,        null,          null,         null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        C(-0.0, -0.0), C(-0.0, 0.0), null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        C(0.0, -0.0),  C(0.0, 0.0),  null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        null,          null,         null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(1.0, 0.0),  null,        C(1.0, -0.0),  C(1.0, 0.0),  null,        C(1.0, 0.0),  C(1.0, 0.0)},
                {C(NAN, NAN),  C(NAN, NAN), C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN),  C(NAN, NAN)},
        };
        // @formatter:on

        @Override
        PComplex compute(VirtualFrame frame, double real, double imag) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && Double.isFinite(real)) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    return factory().createComplex(real > 0 ? 1.0 : -1.0, Math.copySign(0.0, 2.0 * Math.sin(imag) * Math.cos(imag)));
                } else {
                    return specialValue(factory(), SPECIAL_VALUES, real, imag);
                }
            }
            if (Math.abs(real) > LOG_LARGE_DOUBLE) {
                return factory().createComplex(Math.copySign(1.0, real),
                                4.0 * Math.sin(imag) * Math.cos(imag) * Math.exp(-20. * Math.abs(real)));
            }
            double tx = Math.tanh(real);
            double ty = Math.tan(imag);
            double cx = 1.0 / Math.cosh(real);
            double txty = tx * ty;
            double denom = 1.0 + txty * txty;
            return factory().createComplex(tx * (1.0 + ty * ty) / denom, ((ty / denom) * cx) * cx);
        }

        static TanhNode create() {
            return CmathModuleBuiltinsFactory.TanhNodeFactory.create();
        }
    }

    @Builtin(name = "isclose", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, keywordOnlyNames = {"rel_tol", "abs_tol"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class IsCloseNode extends PythonQuaternaryBuiltinNode {

        private static final double DEFAULT_REL_TOL = 1e-09;
        private static final double DEFAULT_ABS_TOL = 0;

        @Child ComplexBuiltins.AbsNode abs = ComplexBuiltins.AbsNode.create();

        @Specialization
        boolean doCCDD(PComplex a, PComplex b, double relTolObj, double absTolObj) {
            return isClose(a, b, relTolObj, absTolObj);
        }

        @Specialization
        @SuppressWarnings("unused")
        boolean doCCNN(PComplex a, PComplex b, PNone relTolObj, PNone absTolObj) {
            return isClose(a, b, DEFAULT_REL_TOL, DEFAULT_ABS_TOL);
        }

        @Specialization(limit = "2")
        boolean doGeneral(VirtualFrame frame, Object aObj, Object bObj, Object relTolObj, Object absTolObj,
                        @Cached CoerceToComplexNode coerceAToComplex,
                        @Cached CoerceToComplexNode coerceBToComplex,
                        @CachedLibrary("relTolObj") PythonObjectLibrary relTolLib,
                        @CachedLibrary("absTolObj") PythonObjectLibrary absTolLib) {
            PComplex a = coerceAToComplex.execute(frame, aObj);
            PComplex b = coerceBToComplex.execute(frame, bObj);
            double relTol = PGuards.isNoValue(relTolObj) ? DEFAULT_REL_TOL : relTolLib.asJavaDouble(relTolObj);
            double absTol = PGuards.isPNone(absTolObj) ? DEFAULT_ABS_TOL : absTolLib.asJavaDouble(absTolObj);
            return isClose(a, b, relTol, absTol);
        }

        private boolean isClose(PComplex a, PComplex b, double relTol, double absTol) {
            if (relTol < 0.0 || absTol < 0.0) {
                throw raise(ValueError, ErrorMessages.TOLERANCE_MUST_NON_NEGATIVE);
            }
            if (a.getReal() == b.getReal() && a.getImag() == b.getImag()) {
                return true;
            }
            if (Double.isInfinite(a.getReal()) || Double.isInfinite(a.getImag()) ||
                            Double.isInfinite(b.getReal()) || Double.isInfinite(b.getImag())) {
                return false;
            }
            PComplex diff = factory().createComplex(a.getReal() - b.getReal(), a.getImag() - b.getImag());
            double len = abs.executeDouble(diff);
            return len <= absTol || len <= relTol * abs.executeDouble(b) || len <= relTol * abs.executeDouble(a);
        }
    }
}
