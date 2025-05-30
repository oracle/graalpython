/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;

/**
 * Storage for classmethods, staticmethods and instancemethods
 */
public final class PDecoratedMethod extends PythonBuiltinObject implements BoundBuiltinCallable<Object> {
    private Object callable;

    public PDecoratedMethod(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public PDecoratedMethod(Object cls, Shape instanceShape, Object callable) {
        this(cls, instanceShape);
        this.callable = callable;
    }

    public Object getCallable() {
        return callable;
    }

    public void setCallable(Object callable) {
        assert this.callable == null;
        this.callable = callable;
    }

    @SuppressWarnings("unchecked")
    public Object boundToObject(PythonBuiltinClassType binding, PythonLanguage language) {
        if (GetPythonObjectClassNode.executeUncached(this) != PythonBuiltinClassType.PStaticmethod) {
            if (callable instanceof BoundBuiltinCallable) {
                return PFactory.createBuiltinClassmethodFromCallableObj(language, ((BoundBuiltinCallable<Object>) callable).boundToObject(binding, language));
            }
        } else {
            if (callable instanceof PBuiltinMethod) {
                return PFactory.createStaticmethodFromCallableObj(language, ((PBuiltinMethod) callable).getBuiltinFunction().boundToObject(binding, language));
            }
        }
        return this;
    }

    public String getName() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    public Signature getSignature() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }
}
