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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.objects.decimal.DecimalRounding.ROUND_HALF_UP;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.contextvars.ContextVarNodes.GetValueNode;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class DecimalModuleState {
    public static final TruffleString T_DECIMAL_CONTEXT = tsLiteral("decimal_context");

    private final PythonClass[] exceptionTypes;
    private final PythonClass decimalTupleType;
    private final PythonClass signalDictType;

    private final Object rationalType;

    private final PDecimalContext defaultContext;
    private final PDecimalContext basicContext;
    private final PDecimalContext extendedContext;
    private final PContextVar currentContext;

    public DecimalModuleState(PythonLanguage language, PythonClass[] exceptionTypes, PythonClass decimalTupleType, PythonClass signalDictType, Object rationalType) {
        this.exceptionTypes = exceptionTypes;
        this.decimalTupleType = decimalTupleType;
        this.signalDictType = signalDictType;
        this.rationalType = rationalType;
        currentContext = PFactory.createContextVar(language, T_DECIMAL_CONTEXT, PContextVar.NO_DEFAULT);
        Object cls = PythonBuiltinClassType.PDecimalContext;
        Shape shape = PythonBuiltinClassType.PDecimalContext.getInstanceShape(language);
        defaultContext = createContext(cls, shape);
        basicContext = newContext(cls, shape);
        basicContext.setPrecision(9);
        basicContext.setRounding(ROUND_HALF_UP);
        basicContext.setTraps(DecimalContextUtils.DEFAULT_TRAPS | DecimalContextUtils.mask(DecimalExceptionTypes.Underflow) | DecimalContextUtils.mask(DecimalExceptionTypes.Clamped));
        extendedContext = newContext(cls, shape);
        extendedContext.setPrecision(9);
        extendedContext.setTraps(0);
    }

    public PythonClass getExceptionType(DecimalExceptionTypes type) {
        return exceptionTypes[type.ordinal()];
    }

    public PythonClass getDecimalTupleType() {
        return decimalTupleType;
    }

    public PythonClass getSignalDictType() {
        return signalDictType;
    }

    public Object getRationalType() {
        return rationalType;
    }

    public PDecimalContext getDefaultContext() {
        return defaultContext;
    }

    public PDecimalContext getBasicContext() {
        return basicContext;
    }

    public PDecimalContext getExtendedContext() {
        return extendedContext;
    }

    public PDecimalContext newContext(Object cls, Shape shape) {
        PDecimalContext result = createContext(cls, shape);
        result.copyFrom(defaultContext);
        // Context.__new__ inherits arithmetic settings and signals, but not capitals.
        result.setCapitals(true);
        return result;
    }

    private PDecimalContext createContext(Object cls, Shape shape) {
        PDecimalContext result = PFactory.createDecimalContext(cls, shape);
        result.setSignalDicts(PFactory.createDecimalSignalDict(signalDictType, signalDictType.getInstanceShape(), result, false),
                        PFactory.createDecimalSignalDict(signalDictType, signalDictType.getInstanceShape(), result, true));
        return result;
    }

    public PDecimalContext copyContext(PythonLanguage language, PDecimalContext original) {
        PDecimalContext copy = newContext(PythonBuiltinClassType.PDecimalContext, PythonBuiltinClassType.PDecimalContext.getInstanceShape(language));
        copy.copyFrom(original);
        return copy;
    }

    public PDecimalContext getCurrentContext(Node node, InlinedBranchProfile initializeDecimalContextProfile, GetValueNode getValue) {
        PythonContext context = PythonContext.get(node);
        PythonLanguage language = context.getLanguage(node);
        PythonContext.PythonThreadState threadState = context.getThreadState(language);
        Object value = getValue.execute(node, currentContext, threadState);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, value == null)) {
            initializeDecimalContextProfile.enter(node);
            return initializeCurrentContext(node, language, threadState);
        }
        return (PDecimalContext) value;
    }

    @TruffleBoundary
    private PDecimalContext initializeCurrentContext(Node node, PythonLanguage language, PythonContext.PythonThreadState threadState) {
        PDecimalContext initial = copyContext(language, defaultContext);
        initial.setFlags(0);
        currentContext.setValue(node, threadState, initial);
        return initial;
    }

    public void setCurrentContext(Node node, PDecimalContext value) {
        PythonContext context = PythonContext.get(node);
        PythonLanguage language = context.getLanguage(node);
        if (value == defaultContext || value == basicContext || value == extendedContext) {
            value = copyContext(language, value);
            value.setFlags(0);
        }
        currentContext.setValue(node, context.getThreadState(language), value);
    }
}
