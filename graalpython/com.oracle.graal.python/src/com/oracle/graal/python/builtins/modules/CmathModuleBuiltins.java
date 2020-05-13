/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToDoubleNode;
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

@CoreFunctions(defineModule = "cmath")
public class CmathModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CmathModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        // Add constant values
        builtinConstants.put("pi", Math.PI);
        builtinConstants.put("e", Math.E);
        builtinConstants.put("tau", 2 * Math.PI);
        builtinConstants.put("inf", Double.POSITIVE_INFINITY);
        builtinConstants.put("nan", Double.NaN);
        builtinConstants.put("infj", core.factory().createComplex(0, Double.POSITIVE_INFINITY));
        builtinConstants.put("nanj", core.factory().createComplex(0, Double.NaN));
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class CmathUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Child private LookupAndCallUnaryNode callComplexFunc;

        protected PComplex getComplexNumberFromObject(VirtualFrame frame, Object object) {
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
            return null;
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    public abstract static class CmathComplexUnaryBuiltinNode extends CmathUnaryBuiltinNode {

        public abstract PComplex executeObject(VirtualFrame frame, Object value);

        public PComplex compute(@SuppressWarnings("unused") double real, @SuppressWarnings("unused") double imag) {
            throw raise(NotImplementedError, "compute function in cmath");
        }

        @Specialization
        public PComplex doL(long value) {
            return compute(value, 0);
        }

        @Specialization
        public PComplex doD(double value) {
            return compute(value, 0);
        }

        @Specialization
        public PComplex doPI(PInt value) {
            return compute(value.doubleValue(), 0);
        }

        @Specialization
        public PComplex doC(PComplex value) {
            return compute(value.getReal(), value.getImag());
        }

        @Specialization(guards = "!isNumber(value)")        //TODO: what is the purpose of this guard?
        public PComplex doGeneral(VirtualFrame frame, Object value,
                                @Cached("create()") CoerceToDoubleNode coerceToDouble) {
            //TODO should this be replaced with something like CoerceToComplexNode?
            PComplex complex = getComplexNumberFromObject(frame, value);
            if (complex != null) {
                return doC(complex);
            }
            return doD(coerceToDouble.execute(frame, value));
        }
    }

    private static boolean isInf(double d) {
        return Double.isInfinite(d);
    }

    private static boolean isNaN(double d) {
        return Double.isNaN(d);
    }

    private static boolean isPos(double d) {
        return Math.copySign(1.0, d) == 1.0;
    }

    private static boolean isNeg(double d) {
        return Math.copySign(1.0, d) != 1.0;
    }

    private static boolean isPosInf(double d) {
        return isInf(d) && isPos(d);
    }

    private static boolean isNegInf(double d) {
        return isInf(d) && isNeg(d);
    }

    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SqrtNode extends CmathComplexUnaryBuiltinNode {
        @Override
        public PComplex compute(double real, double imag) {
            if (isInf(imag)) {
                return factory().createComplex(Double.POSITIVE_INFINITY, imag);
            }
            if (isNegInf(real)) {
                if (isNaN(imag)) {
                    return factory().createComplex(Double.NaN, Double.POSITIVE_INFINITY);
                }
                return factory().createComplex(0.0, Math.copySign(Double.POSITIVE_INFINITY, imag));
            }
            if (isPosInf(real)) {
                if (isNaN(imag)) {
                    return factory().createComplex(Double.POSITIVE_INFINITY, Double.NaN);
                }
                return factory().createComplex(Double.POSITIVE_INFINITY, Math.copySign(0.0, imag));
            }
            if (isNaN(real) || isNaN(imag)) {
                return factory().createComplex(Double.NaN, Double.NaN);
            }
            if (real == 0.0 && imag == 0.0) {
                return factory().createComplex(0.0, imag);
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

    //////////////////////////////////////////////////////////////////////////////////////////////////

    public abstract static class DummyNode extends CmathComplexUnaryBuiltinNode {
        @Override
        public PComplex compute(double real, double imag) {
            return factory().createComplex(real, imag);
        }
    }

    @Builtin(name = "acos", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AcosNode extends DummyNode {}
    @Builtin(name = "acosh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AcoshNode extends DummyNode {}
    @Builtin(name = "asin", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AsinNode extends DummyNode {}
    @Builtin(name = "asinh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AsinhNode extends DummyNode {}
    @Builtin(name = "atan", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AtanNode extends DummyNode {}
    @Builtin(name = "atanh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class AtanhNode extends DummyNode {}
    @Builtin(name = "cos", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class CosNode extends DummyNode {}
    @Builtin(name = "cosh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class CoshNode extends DummyNode {}
    @Builtin(name = "sin", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class SinNode extends DummyNode {}
    @Builtin(name = "sinh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class SinhNode extends DummyNode {}
    @Builtin(name = "tan", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class TanNode extends DummyNode {}
    @Builtin(name = "tanh", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class TanhNode extends DummyNode {}

    @Builtin(name = "exp", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class ExpNode extends DummyNode {}
    @Builtin(name = "log", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class LogNode extends DummyNode {}
    @Builtin(name = "log10", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class Log10Node extends DummyNode {}

    @Builtin(name = "isclose", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class IsCloseNode extends DummyNode {}
    @Builtin(name = "isinf", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class IsInfNode extends DummyNode {}
    @Builtin(name = "isnan", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class IsNaNNode extends DummyNode {}
    @Builtin(name = "isfinite", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class IsFiniteNode extends DummyNode {}
    @Builtin(name = "polar", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class PolarNode extends DummyNode {}
    @Builtin(name = "phase", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class PhaseNode extends DummyNode {}
    @Builtin(name = "rect", minNumOfPositionalArgs = 1) @GenerateNodeFactory public abstract static class RectNode extends DummyNode {}
}
