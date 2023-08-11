/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public class PContextVarsContext extends PythonBuiltinObject {
    Hamt contextVarValues;
    private PContextVarsContext previousContext = null;

    public void enter(Node inliningTarget, PythonContext.PythonThreadState threadState, PRaiseNode.Lazy raise) {
        if (previousContext != null) {
            throw raise.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_ENTER_CONTEXT_ALREADY_ENTERED, this);
        }
        previousContext = threadState.getContextVarsContext();
        assert previousContext != null : "ThreadState had null Context. This should not happen";
        threadState.setContextVarsContext(this);
    }

    public void leave(PythonContext.PythonThreadState threadState) {
        assert threadState.getContextVarsContext() == this : "leaving a context which is not currently entered";
        assert previousContext != null : "entered context has no previous context";
        threadState.setContextVarsContext(previousContext);
        previousContext = null;
    }

    public PContextVarsContext(Object cls, Shape instanceShape) {
        this(new Hamt(), cls, instanceShape);
    }

    public PContextVarsContext(PContextVarsContext original, Object cls, Shape instanceShape) {
        this(original.contextVarValues, cls, instanceShape);
    }

    private PContextVarsContext(Hamt contextVarValues, Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.contextVarValues = contextVarValues;

    }
}
