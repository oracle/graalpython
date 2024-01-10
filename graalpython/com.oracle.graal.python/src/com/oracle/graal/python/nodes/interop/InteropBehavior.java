/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.interop;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.strings.TruffleString;

public class InteropBehavior {
    @ValueType
    private record InteropBehaviorMethodRecord(CallTarget callTarget, PythonObject globals, boolean constant) {
    }

    private final PythonAbstractObject receiver;

    private final InteropBehaviorMethodRecord[] records = new InteropBehaviorMethodRecord[InteropBehaviorMethod.getLength()];

    public InteropBehavior(PythonAbstractObject receiver) {
        this.receiver = receiver;
    }

    public void defineBehavior(InteropBehaviorMethod method, PFunction function) {
        records[method.ordinal()] = new InteropBehaviorMethodRecord(function.getCode().getRootCallTarget(), function.getGlobals(), false);
    }

    public void defineBehavior(InteropBehaviorMethod method, boolean constant) {
        records[method.ordinal()] = new InteropBehaviorMethodRecord(null, null, constant);
    }

    public boolean isDefined(InteropBehaviorMethod method) {
        return records[method.ordinal()] != null;
    }

    public CallTarget getCallTarget(InteropBehaviorMethod method) {
        assert isDefined(method) : "interop behavior method not defined";
        return records[method.ordinal()].callTarget;
    }

    public PythonObject getGlobals(InteropBehaviorMethod method) {
        assert isDefined(method) : "interop behavior method not defined";
        return records[method.ordinal()].globals;
    }

    public boolean isConstant(InteropBehaviorMethod method) {
        assert isDefined(method) : "interop behavior method not defined";
        return records[method.ordinal()].callTarget == null;
    }

    public boolean getConstantValue(InteropBehaviorMethod method) {
        assert isConstant(method) : "interop behavior method not constant";
        return records[method.ordinal()].constant;
    }

    public Object[] createArguments(InteropBehaviorMethod method, PythonAbstractObject receiver, Object[] extraArguments) {
        assert method.checkArity(extraArguments);
        Object[] pArguments = PArguments.create(1 + (method.takesVarArgs ? 0 : method.extraArguments));
        PArguments.setGlobals(pArguments, getGlobals(method));
        PArguments.setArgument(pArguments, 0, receiver);
        if (method.takesVarArgs) {
            PArguments.setVariableArguments(pArguments, extraArguments);
        } else {
            for (int i = 0; i < extraArguments.length; i++) {
                PArguments.setArgument(pArguments, i + 1, extraArguments[i]);
            }
        }
        return pArguments;
    }

    public PythonAbstractObject getReceiver() {
        return receiver;
    }

    @CompilerDirectives.TruffleBoundary
    public Object[] getDefinedMethods() {
        ArrayList<TruffleString> defined = new ArrayList<>();
        for (int i = 0; i < records.length; i++) {
            InteropBehaviorMethod method = InteropBehaviorMethod.VALUES[i];
            if (isDefined(method)) {
                defined.add(method.tsName);
            }
        }
        return defined.toArray();
    }
}
