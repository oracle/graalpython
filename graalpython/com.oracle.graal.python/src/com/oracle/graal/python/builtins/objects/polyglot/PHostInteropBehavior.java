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
package com.oracle.graal.python.builtins.objects.polyglot;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.interop.HostInteropBehaviorMethod;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.ArrayList;

public class PHostInteropBehavior extends PythonBuiltinObject {
    private final PythonAbstractObject receiver;

    private final CallTarget[] callTargets = new CallTarget[HostInteropBehaviorMethod.getLength()];
    private final PythonObject[] globals = new PythonObject[HostInteropBehaviorMethod.getLength()];
    private final boolean[] constants;

    public PHostInteropBehavior(Object cls, Shape instanceShape, PythonAbstractObject receiver, PFunction[] functions, boolean[] constants) {
        super(cls, instanceShape);
        this.receiver = receiver;
        assert functions.length == HostInteropBehaviorMethod.getLength();
        assert constants.length == HostInteropBehaviorMethod.getLength();
        this.constants = constants;
        for (int i = 0; i < functions.length; i++) {
            PFunction function = functions[i];
            if (function != null) {
                callTargets[i] = functions[i].getCode().getRootCallTarget();
                globals[i] = functions[i].getGlobals();
            }
        }
    }

    public CallTarget getCallTarget(HostInteropBehaviorMethod method) {
        return callTargets[method.ordinal()];
    }

    public PythonObject getGlobals(HostInteropBehaviorMethod method) {
        return globals[method.ordinal()];
    }

    public boolean isDefined(HostInteropBehaviorMethod method) {
        return method.constantBoolean || callTargets[method.ordinal()] != null;
    }

    public Object[] createArguments(HostInteropBehaviorMethod method, PythonAbstractObject receiver, Object[] extraArguments) {
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

    public boolean getConstantValue(HostInteropBehaviorMethod method) {
        assert method.constantBoolean;
        return constants[method.ordinal()];
    }

    @CompilerDirectives.TruffleBoundary
    public Object[] getDefinedMethods() {
        ArrayList<TruffleString> defined = new ArrayList<>();
        for (int i = 0; i < callTargets.length; i++) {
            HostInteropBehaviorMethod method = HostInteropBehaviorMethod.VALUES[i];
            if (isDefined(method)) {
                defined.add(method.tsName);
            }
        }
        return defined.toArray();
    }
}
