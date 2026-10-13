/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_COLLECTIONS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_COLLECTIONS_ABC;
import static com.oracle.graal.python.nodes.BuiltinNames.T_DECIMAL;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MUTABLE_MAPPING;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NAMEDTUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DECIMAL;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.decimal.DecimalContextUtils;
import com.oracle.graal.python.builtins.objects.decimal.DecimalExceptionTypes;
import com.oracle.graal.python.builtins.objects.decimal.DecimalModuleState;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.GetCurrentContextNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalRounding;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils;
import com.oracle.graal.python.builtins.objects.decimal.PDecimalContext;
import com.oracle.graal.python.builtins.objects.decimal.PDecimalContextManager;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_decimal")
public final class DecimalModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T_NUMBERS = tsLiteral("numbers");
    private static final TruffleString T_NUMBER = tsLiteral("Number");
    private static final TruffleString T_RATIONAL = tsLiteral("Rational");
    private static final TruffleString T_REGISTER = tsLiteral("register");
    private static final TruffleString T_VERSION = tsLiteral("1.70");
    private static final TruffleString T_LIBMPDEC_VERSION = tsLiteral("2.5.1");
    private static final TruffleString T_DECIMAL_TUPLE = tsLiteral("DecimalTuple");
    private static final TruffleString T_DECIMAL_TUPLE_FIELDS = tsLiteral("sign digits exponent");
    private static final TruffleString T_SIGNAL_DICT = tsLiteral("SignalDict");
    public static final TruffleString T_DEFAULT_CONTEXT = tsLiteral("DefaultContext");
    public static final TruffleString T_BASIC_CONTEXT = tsLiteral("BasicContext");
    public static final TruffleString T_EXTENDED_CONTEXT = tsLiteral("ExtendedContext");

    public static final TruffleString T_ROUND_DOWN = DecimalRounding.ROUND_DOWN.getPythonName();
    public static final TruffleString T_ROUND_UP = DecimalRounding.ROUND_UP.getPythonName();
    public static final TruffleString T_ROUND_HALF_EVEN = DecimalRounding.ROUND_HALF_EVEN.getPythonName();
    public static final TruffleString T_ROUND_HALF_DOWN = DecimalRounding.ROUND_HALF_DOWN.getPythonName();
    public static final TruffleString T_ROUND_HALF_UP = DecimalRounding.ROUND_HALF_UP.getPythonName();
    public static final TruffleString T_ROUND_CEILING = DecimalRounding.ROUND_CEILING.getPythonName();
    public static final TruffleString T_ROUND_FLOOR = DecimalRounding.ROUND_FLOOR.getPythonName();
    public static final TruffleString T_ROUND_05UP = DecimalRounding.ROUND_05UP.getPythonName();

    public DecimalModuleBuiltins() {
        addBuiltinConstant("MAX_PREC", DecimalUtils.MAX_PREC);
        addBuiltinConstant("MAX_EMAX", DecimalUtils.MAX_EMAX);
        addBuiltinConstant("MIN_EMIN", DecimalUtils.MIN_EMIN);
        addBuiltinConstant("MIN_ETINY", DecimalUtils.MIN_ETINY);

        addBuiltinConstant(T_ROUND_DOWN, T_ROUND_DOWN);
        addBuiltinConstant(T_ROUND_UP, T_ROUND_UP);
        addBuiltinConstant(T_ROUND_HALF_EVEN, T_ROUND_HALF_EVEN);
        addBuiltinConstant(T_ROUND_HALF_DOWN, T_ROUND_HALF_DOWN);
        addBuiltinConstant(T_ROUND_HALF_UP, T_ROUND_HALF_UP);
        addBuiltinConstant(T_ROUND_CEILING, T_ROUND_CEILING);
        addBuiltinConstant(T_ROUND_FLOOR, T_ROUND_FLOOR);
        addBuiltinConstant(T_ROUND_05UP, T_ROUND_05UP);

        addBuiltinConstant("HAVE_THREADS", true);
        addBuiltinConstant("HAVE_CONTEXTVAR", true);

        // Even _pydecimal exports these constants, despite not being based on libmpdec, so we should do that too
        addBuiltinConstant("__version__", T_VERSION);
        addBuiltinConstant("__libmpdec_version__", T_LIBMPDEC_VERSION);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        PythonModule module = core.lookupBuiltinModule(T__DECIMAL);
        PythonLanguage language = core.getLanguage();
        PythonClass[] exceptionTypes = new PythonClass[DecimalExceptionTypes.VALUES.length];
        for (DecimalExceptionTypes exception : DecimalExceptionTypes.VALUES) {
            PythonAbstractClass[] bases = new PythonAbstractClass[exception.bases.length];
            for (int i = 0; i < bases.length; i++) {
                Object base = exception.bases[i];
                if (base instanceof PythonBuiltinClassType builtinType) {
                    bases[i] = core.lookupType(builtinType);
                } else {
                    bases[i] = exceptionTypes[((DecimalExceptionTypes) base).ordinal()];
                }
            }
            PythonClass type = PFactory.createPythonClassAndFixupSlots(null, language, exception.typeName, bases[0], bases);
            type.setAttribute(T___MODULE__, T_DECIMAL);
            module.setAttribute(exception.typeName, type);
            exceptionTypes[exception.ordinal()] = type;
        }

        PythonModule collections = AbstractImportNode.importModule(T_COLLECTIONS);
        Object namedtuple = collections.getAttribute(T_NAMEDTUPLE);
        PythonClass decimalTuple = (PythonClass) CallNode.executeUncached(namedtuple, new Object[]{T_DECIMAL_TUPLE, T_DECIMAL_TUPLE_FIELDS},
                        new PKeyword[]{new PKeyword(T_MODULE, T_DECIMAL)});
        module.setAttribute(T_DECIMAL_TUPLE, decimalTuple);

        PythonModule abc = AbstractImportNode.importModule(T_COLLECTIONS_ABC);
        PythonAbstractClass mixin = core.lookupType(PythonBuiltinClassType.PDecimalSignalDictMixin);
        PythonAbstractClass mapping = (PythonAbstractClass) abc.getAttribute(T_MUTABLE_MAPPING);
        PythonClass signalDict = PFactory.createPythonClassAndFixupSlots(null, language, T_SIGNAL_DICT, mixin, new PythonAbstractClass[]{mixin, mapping});
        signalDict.setAttribute(T___MODULE__, T_DECIMAL);

        PythonModule numbers = AbstractImportNode.importModule(T_NUMBERS);
        DecimalModuleState state = new DecimalModuleState(language, exceptionTypes, decimalTuple, signalDict, numbers.getAttribute(T_RATIONAL));
        module.setModuleState(state);
        core.getContext().setDecimalModuleState(state);
        module.setAttribute(T_DEFAULT_CONTEXT, state.getDefaultContext());
        module.setAttribute(T_BASIC_CONTEXT, state.getBasicContext());
        module.setAttribute(T_EXTENDED_CONTEXT, state.getExtendedContext());

        Object register = PyObjectGetAttr.executeUncached(numbers.getAttribute(T_NUMBER), T_REGISTER);
        CallNode.executeUncached(register, PythonBuiltinClassType.PDecimal);

    }

    @Builtin(name = "getcontext", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetcontextNode extends PythonBuiltinNode {
        @Specialization
        static PDecimalContext get(@Bind Node inliningTarget,
                        @Cached GetCurrentContextNode getCurrentContext) {
            return getCurrentContext.execute(inliningTarget);
        }
    }

    @Builtin(name = "setcontext", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SetcontextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object set(Object context,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (!(context instanceof PDecimalContext decimalContext)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_CONTEXT_TYPE);
            }
            DecimalContextUtils.getState(inliningTarget).setCurrentContext(inliningTarget, decimalContext);
            return PNone.NONE;
        }
    }

    @Builtin(name = "localcontext", minNumOfPositionalArgs = 0, parameterNames = {"ctx", "prec", "rounding", "Emin", "Emax", "capitals", "clamp", "flags", "traps"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LocalcontextNode extends PythonBuiltinNode {
        @Specialization
        static PDecimalContextManager local(Object ctx, Object prec, Object rounding, Object emin, Object emax, Object capitals, Object clamp, Object flags, Object traps,
                        PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode,
                        @Cached GetCurrentContextNode getCurrentContext) {
            if (kwargs.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DECIMAL_INVALID_LOCALCONTEXT_KEYWORD, kwargs[0].getName());
            }
            DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
            PDecimalContext outer = getCurrentContext.execute(inliningTarget);
            PDecimalContext source = outer;
            if (!(ctx instanceof PNone)) {
                if (!(ctx instanceof PDecimalContext decimalContext)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OPTIONAL_DECIMAL_CONTEXT);
                }
                source = decimalContext;
            }
            PDecimalContext local = state.copyContext(language, source);
            DecimalContextUtils.setAttributes(inliningTarget, local, prec, rounding, emin, emax, capitals, clamp, flags, traps);
            return PFactory.createDecimalContextManager(language, outer, local);
        }
    }
}
