/* Copyright (c) 2020, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.AbsNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "cmath")
public final class CmathModuleBuiltins extends PythonBuiltins {

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
    public void initialize(Python3Core core) {
        // Add constant values
        addBuiltinConstant("pi", Math.PI);
        addBuiltinConstant("e", Math.E);
        addBuiltinConstant("tau", 2 * Math.PI);
        addBuiltinConstant("inf", Double.POSITIVE_INFINITY);
        addBuiltinConstant("nan", Double.NaN);
        addBuiltinConstant("infj", core.factory().createComplex(0, Double.POSITIVE_INFINITY));
        addBuiltinConstant("nanj", core.factory().createComplex(0, Double.NaN));
        super.initialize(core);
    }

    static PComplex specialValue(PythonObjectFactory factory, ComplexValue[][] table, double real, double imag) {
        ComplexValue v = specialValue(table, real, imag);
        return v == null ? null : v.toPComplex(factory);
    }

    static ComplexValue specialValue(ComplexValue[][] table, double real, double imag) {
        if (!Double.isFinite(real) || !Double.isFinite(imag)) {
            ComplexValue c = table[SpecialType.ofDouble(real).ordinal()][SpecialType.ofDouble(imag).ordinal()];
            if (c == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
            return c;
        }
        return null;
    }

    /**
     * Creates an instance of {@link ComplexValue}. The name of this factory method is intentionally
     * short to allow nested classes compact definition of their tables of special values.
     *
     * @param real the real part of the complex constant
     * @param imag the imaginary part of the complex constant
     * @return a new {@link ComplexValue} instance representing the complex number real + i * imag
     */
    static ComplexValue C(double real, double imag) {
        return new ComplexValue(real, imag);
    }

    @ValueType
    static class ComplexValue {
        final double real;
        final double imag;

        ComplexValue(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        PComplex toPComplex(PythonObjectFactory factory) {
            return factory.createComplex(real, imag);
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

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    abstract static class CmathComplexUnaryHelperNode extends Node {

        @FunctionalInterface
        interface Op {
            ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode);
        }

        abstract PComplex execute(VirtualFrame frame, Node inliningTarget, Object value, Op op);

        @Specialization
        static PComplex doL(Node inliningTarget, long value, Op op,
                        @Shared @Cached(inline = false) PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op.compute(inliningTarget, value, 0, raiseNode).toPComplex(factory);
        }

        @Specialization
        static PComplex doD(Node inliningTarget, double value, Op op,
                        @Shared @Cached(inline = false) PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op.compute(inliningTarget, value, 0, raiseNode).toPComplex(factory);
        }

        @Specialization
        static PComplex doC(Node inliningTarget, PComplex value, Op op,
                        @Shared @Cached(inline = false) PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op.compute(inliningTarget, value.getReal(), value.getImag(), raiseNode).toPComplex(factory);
        }

        @Specialization
        static PComplex doGeneral(VirtualFrame frame, Node inliningTarget, Object value, Op op,
                        @Cached CoerceToComplexNode coerceToComplex,
                        @Shared @Cached(inline = false) PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return doC(inliningTarget, coerceToComplex.execute(frame, inliningTarget, value), op, factory, raiseNode);
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateInline
    @GenerateCached(false)
    abstract static class CmathBooleanUnaryHelperNode extends Node {

        @FunctionalInterface
        interface Op {
            boolean compute(double real, double imag);
        }

        abstract boolean execute(VirtualFrame frame, Node inliningTarget, Object o, Op op);

        @Specialization
        static boolean doL(long value, Op op) {
            return op.compute(value, 0);
        }

        @Specialization
        static boolean doD(double value, Op op) {
            return op.compute(value, 0);
        }

        @Specialization
        static boolean doC(PComplex value, Op op) {
            return op.compute(value.getReal(), value.getImag());
        }

        @Specialization
        static boolean doGeneral(VirtualFrame frame, Node inliningTarget, Object value, Op op,
                        @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, inliningTarget, value), op);
        }
    }

    @Builtin(name = "isnan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsNanNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(VirtualFrame frame, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathBooleanUnaryHelperNode helper) {
            return helper.execute(frame, inliningTarget, o, (real, imag) -> Double.isNaN(real) || Double.isNaN(imag));
        }
    }

    @Builtin(name = "isinf", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsInfNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(VirtualFrame frame, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathBooleanUnaryHelperNode helper) {
            return helper.execute(frame, inliningTarget, o, (real, imag) -> Double.isInfinite(real) || Double.isInfinite(imag));
        }
    }

    @Builtin(name = "isfinite", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsFiniteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(VirtualFrame frame, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathBooleanUnaryHelperNode helper) {
            return helper.execute(frame, inliningTarget, o, (real, imag) -> Double.isFinite(real) && Double.isFinite(imag));
        }
    }

    @Builtin(name = "phase", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class PhaseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doL(long value) {
            return value < 0 ? Math.PI : 0;
        }

        @Specialization
        static double doD(double value) {
            return value < 0 ? Math.PI : 0;
        }

        @Specialization
        static double doC(PComplex value) {
            return Math.atan2(value.getImag(), value.getReal());
        }

        @Specialization
        static double doGeneral(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToComplexNode coerceToComplex) {
            return doC(coerceToComplex.execute(frame, inliningTarget, value));
        }
    }

    @Builtin(name = "polar", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class PolarNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTuple doL(long value,
                        @Shared @Cached PythonObjectFactory factory) {
            return doD(value, factory);
        }

        @Specialization
        static PTuple doD(double value,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{Math.abs(value), value < 0 ? Math.PI : 0});
        }

        @Specialization
        static PTuple doC(PComplex value,
                        @Shared @Cached ComplexBuiltins.AbsNode absNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return toPolar(value, absNode, factory);
        }

        @Specialization
        static PTuple doGeneral(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToComplexNode coerceToComplex,
                        @Shared @Cached ComplexBuiltins.AbsNode absNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return toPolar(coerceToComplex.execute(frame, inliningTarget, value), absNode, factory);
        }

        private static PTuple toPolar(PComplex value, ComplexBuiltins.AbsNode absNode, PythonObjectFactory factory) {
            double r = absNode.executeDouble(value);
            return factory.createTuple(new Object[]{r, Math.atan2(value.getImag(), value.getReal())});
        }
    }

    @Builtin(name = "rect", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class RectNode extends PythonBinaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
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
            return rect(this, r, phi);
        }

        @Specialization
        PComplex doLD(long r, double phi) {
            return rect(this, r, phi);
        }

        @Specialization
        PComplex doDL(double r, long phi) {
            return rect(this, r, phi);
        }

        @Specialization
        PComplex doDD(double r, double phi) {
            return rect(this, r, phi);
        }

        @Specialization
        static PComplex doGeneral(VirtualFrame frame, Object r, Object phi,
                        @Bind("this") Node inliningTarget,
                        @Cached PyFloatAsDoubleNode rAsDoubleNode,
                        @Cached PyFloatAsDoubleNode phiAsDoubleNode) {
            return rect(inliningTarget, rAsDoubleNode.execute(frame, inliningTarget, r), phiAsDoubleNode.execute(frame, inliningTarget, phi));
        }

        @TruffleBoundary
        private static PComplex rect(Node raisingNode, double r, double phi) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            // deal with special values
            if (!Double.isFinite(r) || !Double.isFinite(phi)) {
                // need to raise an exception if r is a nonzero number and phi is infinite
                if (r != 0.0 && !Double.isNaN(r) && Double.isInfinite(phi)) {
                    throw PRaiseNode.raiseUncached(raisingNode, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }

                // if r is +/-infinity and phi is finite but nonzero then
                // result is (+-INF +-INF i), but we need to compute cos(phi)
                // and sin(phi) to figure out the signs.
                if (Double.isInfinite(r) && Double.isFinite(phi) && phi != 0.0) {
                    double real = Math.copySign(Double.POSITIVE_INFINITY, Math.cos(phi));
                    double imag = Math.copySign(Double.POSITIVE_INFINITY, Math.sin(phi));
                    if (r > 0) {
                        return factory.createComplex(real, imag);
                    } else {
                        return factory.createComplex(-real, -imag);
                    }
                }
                return specialValue(factory, SPECIAL_VALUES, r, phi);
            }
            return factory.createComplex(r * Math.cos(phi), r * Math.sin(phi));
        }
    }

    @Builtin(name = "log", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class LogNode extends PythonBinaryBuiltinNode {

        abstract PComplex executeComplex(VirtualFrame frame, Object x, Object y);

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
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
        PComplex doComplexNone(PComplex x, @SuppressWarnings("unused") PNone y,
                        @Shared @Cached PythonObjectFactory factory) {
            return log(x, factory);
        }

        @Specialization
        PComplex doComplexComplex(VirtualFrame frame, PComplex x, PComplex y,
                        @Shared @Cached ComplexBuiltins.DivNode divNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return divNode.executeComplex(frame, log(x, factory), log(y, factory));
        }

        @Specialization(guards = "isNoValue(yObj)")
        PComplex doGeneral(VirtualFrame frame, Object xObj, @SuppressWarnings("unused") PNone yObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CoerceToComplexNode coerceXToComplex,
                        // unused node to avoid mixing shared and non-shared inlined nodes
                        @SuppressWarnings("unsued") @Shared @Cached CoerceToComplexNode coerceYToComplex,
                        @Shared @Cached PythonObjectFactory factory) {
            return log(coerceXToComplex.execute(frame, inliningTarget, xObj), factory);
        }

        @Specialization(guards = "!isNoValue(yObj)")
        PComplex doGeneral(VirtualFrame frame, Object xObj, Object yObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CoerceToComplexNode coerceXToComplex,
                        @Shared @Cached CoerceToComplexNode coerceYToComplex,
                        @Shared @Cached ComplexBuiltins.DivNode divNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PComplex x = log(coerceXToComplex.execute(frame, inliningTarget, xObj), factory);
            PComplex y = log(coerceYToComplex.execute(frame, inliningTarget, yObj), factory);
            return divNode.executeComplex(frame, x, y);
        }

        private PComplex log(PComplex z, PythonObjectFactory factory) {
            PComplex r = specialValue(factory, SPECIAL_VALUES, z.getReal(), z.getImag());
            if (r != null) {
                return r;
            }
            double real = computeRealPart(z.getReal(), z.getImag());
            double imag = Math.atan2(z.getImag(), z.getReal());
            return factory.createComplex(real, imag);
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
                throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
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
        PComplex doComplex(VirtualFrame frame, PComplex z,
                        @Shared @Cached PythonObjectFactory factory) {
            PComplex r = logNode.executeComplex(frame, z, PNone.NO_VALUE);
            return factory.createComplex(r.getReal() / LN_10, r.getImag() / LN_10);
        }

        @Specialization
        PComplex doGeneral(VirtualFrame frame, Object zObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToComplexNode coerceXToComplex,
                        @Shared @Cached PythonObjectFactory factory) {
            return doComplex(frame, coerceXToComplex.execute(frame, inliningTarget, zObj), factory);
        }
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SqrtNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(INF, -INF), C(0.0, -INF), C(0.0, -INF), C(0.0, INF), C(0.0, INF), C(INF, INF), C(NAN, INF)},
                {C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         C(0.0, -0.0), C(0.0, 0.0), null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), null,         null,         null,        null,        C(INF, INF), C(NAN, NAN)},
                {C(INF, -INF), C(INF, -0.0), C(INF, -0.0), C(INF, 0.0), C(INF, 0.0), C(INF, INF), C(INF, NAN)},
                {C(INF, -INF), C(NAN, NAN),  C(NAN, NAN),  C(NAN, NAN), C(NAN, NAN), C(INF, INF), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SqrtNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue result = specialValue(SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            if (real == 0.0 && imag == 0.0) {
                return new ComplexValue(0.0, imag);
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
                return new ComplexValue(s, Math.copySign(d, imag));
            }
            return new ComplexValue(d, Math.copySign(s, imag));
        }
    }

    @Builtin(name = "acos", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcosNode extends PythonUnaryBuiltinNode {
        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(P34, INF), C(P, INF),   C(P, INF),   C(P, -INF),   C(P, -INF),   C(P34, -INF), C(NAN, INF)},
                {C(P12, INF), null,        null,        null,         null,         C(P12, -INF), C(NAN, NAN)},
                {C(P12, INF), null,        C(P12, 0.0), C(P12, -0.0), null,         C(P12, -INF), C(P12, NAN)},
                {C(P12, INF), null,        C(P12, 0.0), C(P12, -0.0), null,         C(P12, -INF), C(P12, NAN)},
                {C(P12, INF), null,        null,        null,         null,         C(P12, -INF), C(NAN, NAN)},
                {C(P14, INF), C(0.0, INF), C(0.0, INF), C(0.0, -INF), C(0.0, -INF), C(P14, -INF), C(NAN, INF)},
                {C(NAN, INF), C(NAN, NAN), C(NAN, NAN), C(NAN, NAN),  C(NAN, NAN),  C(NAN, -INF), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AcosNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue result = specialValue(SPECIAL_VALUES, real, imag);
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
                ComplexValue s1 = SqrtNode.compute(inliningTarget, 1.0 - real, -imag, raiseNode);
                ComplexValue s2 = SqrtNode.compute(inliningTarget, 1.0 + real, imag, raiseNode);
                rreal = 2.0 * Math.atan2(s1.real, s2.real);
                rimag = MathModuleBuiltins.AsinhNode.compute(s2.real * s1.imag - s2.imag * s1.real);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "acosh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcoshNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(INF, -P34), C(INF, -P),   C(INF, -P),   C(INF, P),   C(INF, P),   C(INF, P34), C(INF, NAN)},
                {C(INF, -P12), null,         null,         null,        null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(0.0, -P12), C(0.0, P12), null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         C(0.0, -P12), C(0.0, P12), null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P12), null,         null,         null,        null,        C(INF, P12), C(NAN, NAN)},
                {C(INF, -P14), C(INF, -0.0), C(INF, -0.),  C(INF, 0.0), C(INF, 0.0), C(INF, P14), C(INF, NAN)},
                {C(INF, NAN),  C(NAN, NAN),  C(NAN, NAN),  C(NAN, NAN), C(NAN, NAN), C(INF, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AcoshNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue result = specialValue(SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            double rreal;
            double rimag;
            if (Math.abs(real) > LARGE_DOUBLE || Math.abs(imag) > LARGE_DOUBLE) {
                rreal = Math.log(Math.hypot(real / 2.0, imag / 2.0)) + LN_2 * 2.0;
                rimag = Math.atan2(imag, real);
            } else {
                ComplexValue s1 = SqrtNode.compute(inliningTarget, real - 1.0, imag, raiseNode);
                ComplexValue s2 = SqrtNode.compute(inliningTarget, real + 1.0, imag, raiseNode);
                rreal = MathModuleBuiltins.AsinhNode.compute(s1.real * s2.real + s1.imag * s2.imag);
                rimag = 2.0 * Math.atan2(s1.imag, s2.real);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "asin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsinNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AsinNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue s = AsinhNode.compute(inliningTarget, -imag, real, raiseNode);
            return new ComplexValue(s.imag, -s.real);
        }
    }

    @Builtin(name = "asinh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsinhNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(-INF, -P14), C(-INF, -0.0), C(-INF, -0.0), C(-INF, 0.0), C(-INF, 0.0), C(-INF, P14), C(-INF, NAN)},
                {C(-INF, -P12), null,          null,          null,         null,         C(-INF, P12), C(NAN, NAN)},
                {C(-INF, -P12), null,          C(-0.0, -0.0), C(-0.0, 0.0), null,         C(-INF, P12), C(NAN, NAN)},
                {C(INF, -P12),  null,          C(0.0, -0.0),  C(0.0, 0.0),  null,         C(INF, P12),  C(NAN, NAN)},
                {C(INF, -P12),  null,          null,          null,         null,         C(INF, P12),  C(NAN, NAN)},
                {C(INF, -P14),  C(INF, -0.0),  C(INF, -0.0),  C(INF, 0.0),  C(INF, 0.0),  C(INF, P14),  C(INF, NAN)},
                {C(INF, NAN),   C(NAN, NAN),   C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN),  C(INF, NAN),  C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AsinhNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue result = specialValue(SPECIAL_VALUES, real, imag);
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
                ComplexValue s1 = SqrtNode.compute(inliningTarget, 1.0 + imag, -real, raiseNode);
                ComplexValue s2 = SqrtNode.compute(inliningTarget, 1.0 - imag, real, raiseNode);
                rreal = MathModuleBuiltins.AsinhNode.compute(s1.real * s2.imag - s2.real * s1.imag);
                rimag = Math.atan2(imag, s1.real * s2.real - s1.imag * s2.imag);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "atan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AtanNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AtanNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue s = AtanhNode.compute(inliningTarget, -imag, real, raiseNode);
            return new ComplexValue(s.imag, -s.real);
        }
    }

    @Builtin(name = "atanh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AtanhNode extends PythonUnaryBuiltinNode {

        static final double SQRT_LARGE_DOUBLE = Math.sqrt(LARGE_DOUBLE);
        static final double CM_SQRT_DBL_MIN = Math.sqrt(Double.MIN_NORMAL);

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(-0.0, -P12), C(-0.0, -P12), C(-0.0, -P12), C(-0.0, P12), C(-0.0, P12), C(-0.0, P12), C(-0.0, NAN)},
                {C(-0.0, -P12), null,          null,          null,         null,         C(-0.0, P12), C(NAN, NAN)},
                {C(-0.0, -P12), null,          C(-0.0, -0.0), C(-0.0, 0.0), null,         C(-0.0, P12), C(-0.0, NAN)},
                {C(0.0, -P12),  null,          C(0.0, -0.0),  C(0.0, 0.0),  null,         C(0.0, P12),  C(0.0, NAN)},
                {C(0.0, -P12),  null,          null,          null,         null,         C(0.0, P12),  C(NAN, NAN)},
                {C(0.0, -P12),  C(0.0, -P12),  C(0.0, -P12),  C(0.0, P12),  C(0.0, P12),  C(0.0, P12),  C(0.0, NAN)},
                {C(0.0, -P12),  C(NAN, NAN),   C(NAN, NAN),   C(NAN, NAN),  C(NAN, NAN),  C(0.0, P12),  C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AtanhNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue result = specialValue(SPECIAL_VALUES, real, imag);
            if (result != null) {
                return result;
            }
            if (real < 0.0) {
                return computeWithRealPositive(inliningTarget, -real, -imag, -1.0, raiseNode);
            }
            return computeWithRealPositive(inliningTarget, real, imag, 1.0, raiseNode);
        }

        private static ComplexValue computeWithRealPositive(Node inliningTarget, double real, double imag, double resultScale, PRaiseNode.Lazy raiseNode) {
            double rreal;
            double rimag;
            double ay = Math.abs(imag);
            if (real > SQRT_LARGE_DOUBLE || ay > SQRT_LARGE_DOUBLE) {
                double h = Math.hypot(real / 2.0, imag / 2.0);
                rreal = real / 4.0 / h / h;
                rimag = -Math.copySign(Math.PI / 2.0, -imag);
            } else if (real == 1.0 && ay < CM_SQRT_DBL_MIN) {
                if (ay == 0.0) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                rreal = -Math.log(Math.sqrt(ay) / Math.sqrt(Math.hypot(ay, 2.0)));
                rimag = Math.copySign(Math.atan2(2.0, -ay) / 2, imag);
            } else {
                rreal = Math.log1p(((4.0 * real) / ((1 - real) * (1 - real) + ay * ay))) / 4.0;
                rimag = -Math.atan2(-2.0 * imag, (1 - real) * (1 + real) - ay * ay) / 2.0;
            }
            return new ComplexValue(resultScale * rreal, resultScale * rimag);
        }
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ExpNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(0.0, 0.0), null,        C(0.0, -0.0), C(0.0, 0.0), null,        C(0.0, 0.0), C(0.0, 0.0)},
                {C(NAN, NAN), null,        null,         null,        null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        C(1.0, -0.0), C(1.0, 0.0), null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        C(1.0, -0.0), C(1.0, 0.0), null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, NAN), null,        null,         null,        null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0), C(INF, 0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, -0.0), C(NAN, 0.0), C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, ExpNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                ComplexValue r;
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    if (real > 0) {
                        r = new ComplexValue(Math.copySign(INF, Math.cos(imag)), Math.copySign(INF, Math.sin(imag)));
                    } else {
                        r = new ComplexValue(Math.copySign(0.0, Math.cos(imag)), Math.copySign(0.0, Math.sin(imag)));
                    }
                } else {
                    r = specialValue(SPECIAL_VALUES, real, imag);
                }
                if (Double.isInfinite(imag) && (Double.isFinite(real) || (Double.isInfinite(real) && real > 0))) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
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
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "cos", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, CosNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            return CoshNode.compute(inliningTarget, -imag, real, raiseNode);
        }
    }

    @Builtin(name = "cosh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CoshNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(INF, NAN), null,        C(INF, 0.0),  C(INF, -0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), null,        null,         null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(NAN, 0.0), null,        C(1.0, 0.0),  C(1.0, -0.0), null,        C(NAN, 0.0), C(NAN, 0.0)},
                {C(NAN, 0.0), null,        C(1.0, -0.0), C(1.0, 0.0),  null,        C(NAN, 0.0), C(NAN, 0.0)},
                {C(NAN, NAN), null,        null,         null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0), C(INF, 0.0),  null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, 0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, CoshNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && !Double.isNaN(real)) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    double r = Math.copySign(INF, Math.sin(imag));
                    return new ComplexValue(Math.copySign(INF, Math.cos(imag)), real > 0 ? r : -r);
                } else {
                    return specialValue(SPECIAL_VALUES, real, imag);
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
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "sin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SinNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SinNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue s = SinhNode.compute(inliningTarget, -imag, real, raiseNode);
            return new ComplexValue(s.imag, -s.real);
        }
    }

    @Builtin(name = "sinh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SinhNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(INF, NAN), null,        C(-INF, -0.0), C(-INF, 0.0), null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), null,        null,          null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(0.0, NAN), null,        C(-0.0, -0.0), C(-0.0, 0.0), null,        C(0.0, NAN), C(0.0, NAN)},
                {C(0.0, NAN), null,        C(0.0, -0.0),  C(0.0, 0.0),  null,        C(0.0, NAN), C(0.0, NAN)},
                {C(NAN, NAN), null,        null,          null,         null,        C(NAN, NAN), C(NAN, NAN)},
                {C(INF, NAN), null,        C(INF, -0.0),  C(INF, 0.0),  null,        C(INF, NAN), C(INF, NAN)},
                {C(NAN, NAN), C(NAN, NAN), C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN), C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SinhNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && !Double.isNaN(real)) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    double r = Math.copySign(INF, Math.cos(imag));
                    return new ComplexValue(real > 0 ? r : -r, Math.copySign(INF, Math.sin(imag)));
                } else {
                    return specialValue(SPECIAL_VALUES, real, imag);
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
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            }
            return new ComplexValue(rreal, rimag);
        }
    }

    @Builtin(name = "tan", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TanNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, TanNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            ComplexValue s = TanhNode.compute(inliningTarget, -imag, real, raiseNode);
            return new ComplexValue(s.imag, -s.real);
        }
    }

    @Builtin(name = "tanh", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TanhNode extends PythonUnaryBuiltinNode {

        // @formatter:off
        @CompilationFinal(dimensions = 2)
        private static final ComplexValue[][] SPECIAL_VALUES = {
                {C(-1.0, 0.0), null,        C(-1.0, -0.0), C(-1.0, 0.0), null,        C(-1.0, 0.0), C(-1.0, 0.0)},
                {C(NAN, NAN),  null,        null,          null,         null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        C(-0.0, -0.0), C(-0.0, 0.0), null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        C(0.0, -0.0),  C(0.0, 0.0),  null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(NAN, NAN),  null,        null,          null,         null,        C(NAN, NAN),  C(NAN, NAN)},
                {C(1.0, 0.0),  null,        C(1.0, -0.0),  C(1.0, 0.0),  null,        C(1.0, 0.0),  C(1.0, 0.0)},
                {C(NAN, NAN),  C(NAN, NAN), C(NAN, -0.0),  C(NAN, 0.0),  C(NAN, NAN), C(NAN, NAN),  C(NAN, NAN)},
        };
        // @formatter:on

        @Specialization
        static PComplex doIt(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CmathComplexUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, TanhNode::compute);
        }

        static ComplexValue compute(Node inliningTarget, double real, double imag, PRaiseNode.Lazy raiseNode) {
            if (!Double.isFinite(real) || !Double.isFinite(imag)) {
                if (Double.isInfinite(imag) && Double.isFinite(real)) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                if (Double.isInfinite(real) && Double.isFinite(imag) && imag != 0.0) {
                    return new ComplexValue(real > 0 ? 1.0 : -1.0, Math.copySign(0.0, 2.0 * Math.sin(imag) * Math.cos(imag)));
                } else {
                    return specialValue(SPECIAL_VALUES, real, imag);
                }
            }
            if (Math.abs(real) > LOG_LARGE_DOUBLE) {
                return new ComplexValue(Math.copySign(1.0, real),
                                4.0 * Math.sin(imag) * Math.cos(imag) * Math.exp(-20. * Math.abs(real)));
            }
            double tx = Math.tanh(real);
            double ty = Math.tan(imag);
            double cx = 1.0 / Math.cosh(real);
            double txty = tx * ty;
            double denom = 1.0 + txty * txty;
            return new ComplexValue(tx * (1.0 + ty * ty) / denom, ((ty / denom) * cx) * cx);
        }
    }

    @Builtin(name = "isclose", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, varArgsMarker = true, keywordOnlyNames = {"rel_tol", "abs_tol"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    abstract static class IsCloseNode extends PythonBuiltinNode {

        private static final double DEFAULT_REL_TOL = 1e-09;
        private static final double DEFAULT_ABS_TOL = 0;

        @Specialization
        static boolean doCCDD(PComplex a, PComplex b, double relTolObj, double absTolObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached AbsNode absNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return isClose(inliningTarget, a, b, relTolObj, absTolObj, factory, absNode, raiseNode);
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean doCCNN(PComplex a, PComplex b, PNone relTolObj, PNone absTolObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached AbsNode absNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return isClose(inliningTarget, a, b, DEFAULT_REL_TOL, DEFAULT_ABS_TOL, factory, absNode, raiseNode);
        }

        @Specialization
        static boolean doGeneral(VirtualFrame frame, Object aObj, Object bObj, Object relTolObj, Object absTolObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToComplexNode coerceAToComplex,
                        @Cached CoerceToComplexNode coerceBToComplex,
                        @Cached PyFloatAsDoubleNode relAsDoubleNode,
                        @Cached PyFloatAsDoubleNode absAsDoubleNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached AbsNode absNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            PComplex a = coerceAToComplex.execute(frame, inliningTarget, aObj);
            PComplex b = coerceBToComplex.execute(frame, inliningTarget, bObj);
            double relTol = PGuards.isNoValue(relTolObj) ? DEFAULT_REL_TOL : relAsDoubleNode.execute(frame, inliningTarget, relTolObj);
            double absTol = PGuards.isPNone(absTolObj) ? DEFAULT_ABS_TOL : absAsDoubleNode.execute(frame, inliningTarget, absTolObj);
            return isClose(inliningTarget, a, b, relTol, absTol, factory, absNode, raiseNode);
        }

        private static boolean isClose(Node inliningTarget, PComplex a, PComplex b, double relTol, double absTol, PythonObjectFactory factory, AbsNode absNode, PRaiseNode.Lazy raiseNode) {
            if (relTol < 0.0 || absTol < 0.0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.TOLERANCE_MUST_NON_NEGATIVE);
            }
            if (a.getReal() == b.getReal() && a.getImag() == b.getImag()) {
                return true;
            }
            if (Double.isInfinite(a.getReal()) || Double.isInfinite(a.getImag()) ||
                            Double.isInfinite(b.getReal()) || Double.isInfinite(b.getImag())) {
                return false;
            }
            PComplex diff = factory.createComplex(a.getReal() - b.getReal(), a.getImag() - b.getImag());
            double len = absNode.executeDouble(diff);
            return len <= absTol || len <= relTol * absNode.executeDouble(b) || len <= relTol * absNode.executeDouble(a);
        }
    }
}
