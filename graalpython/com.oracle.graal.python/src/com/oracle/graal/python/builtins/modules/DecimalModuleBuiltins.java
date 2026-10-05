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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.decimal.DecimalExceptionTypes;
import com.oracle.graal.python.builtins.objects.decimal.DecimalModuleState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_decimal")
public final class DecimalModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T__DECIMAL = tsLiteral("_decimal");
    private static final TruffleString T_DECIMAL = tsLiteral("decimal");
    private static final TruffleString T_DECIMAL_TUPLE = tsLiteral("DecimalTuple");
    private static final TruffleString T_DECIMAL_TUPLE_FIELDS = tsLiteral("sign digits exponent");
    private static final TruffleString T_COLLECTIONS = tsLiteral("collections");
    private static final TruffleString T_NAMEDTUPLE = tsLiteral("namedtuple");
    private static final TruffleString T_SIGNAL_DICT = tsLiteral("SignalDict");
    private static final TruffleString T_COLLECTIONS_ABC = tsLiteral("collections.abc");
    private static final TruffleString T_MUTABLE_MAPPING = tsLiteral("MutableMapping");

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
        module.setModuleState(new DecimalModuleState(exceptionTypes, decimalTuple, signalDict));
    }

    @Builtin(name = "getcontext", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetcontextNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement _decimal.getcontext.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "setcontext", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SetcontextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object context,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement _decimal.setcontext.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Builtin(name = "localcontext", minNumOfPositionalArgs = 0, parameterNames = {"ctx", "prec", "rounding", "Emin", "Emax", "capitals", "clamp", "flags", "traps"}, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class LocalcontextNode extends PythonBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object ctx, Object prec, Object rounding, Object Emin, Object Emax, Object capitals, Object clamp, Object flags, Object traps,
                        PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement _decimal.localcontext.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }
}
