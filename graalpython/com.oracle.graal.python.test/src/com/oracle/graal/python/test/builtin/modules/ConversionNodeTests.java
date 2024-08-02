/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin.modules;

import static com.oracle.graal.python.util.PythonUtils.tsArray;

import org.junit.Assert;
import org.junit.function.ThrowingRunnable;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ConversionNodeTests {
    static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("arg"), null);

    protected static Object call(Object arg, ArgumentCastNode castNode) {
        PythonLanguage language = PythonLanguage.get(castNode);
        final PythonContext pythonContext = PythonContext.get(castNode);

        RootCallTarget callTarget = new PRootNode(language) {
            @Child private CalleeContext calleeContext = CalleeContext.create();
            @Child private ArgumentCastNode node = castNode;

            @Override
            public Object execute(VirtualFrame frame) {
                GilNode gilNode = GilNode.getUncached();
                boolean wasAcquired = gilNode.acquire();
                calleeContext.enter(frame);
                try {
                    return node.execute(frame, PArguments.getArgument(frame, 0));
                } finally {
                    calleeContext.exit(frame, this);
                    gilNode.release(wasAcquired);
                }
            }

            @Override
            public Signature getSignature() {
                return SIGNATURE;
            }

            @Override
            public boolean isPythonInternal() {
                return true;
            }
        }.getCallTarget();
        try {
            Object[] arguments = PArguments.create(1);
            PArguments.setGlobals(arguments, pythonContext.factory().createDict());
            PArguments.setException(arguments, PException.NO_EXCEPTION);
            PArguments.setArgument(arguments, 0, arg);
            PythonThreadState threadState = pythonContext.getThreadState(language);
            Object state = IndirectCalleeContext.enter(threadState, arguments, callTarget);
            try {
                return CallTargetInvokeNode.invokeUncached(callTarget, arguments);
            } finally {
                IndirectCalleeContext.exit(threadState, state);
            }
        } catch (PException e) {
            // materialize PException's error message since we are leaving Python
            if (e.getUnreifiedException() instanceof PBaseException managedException) {
                e.setMessage(managedException.getFormattedMessage());
            }
            throw e;
        }
    }

    protected void expectPythonMessage(String expectedMessage, ThrowingRunnable runnable) {
        PException exception = Assert.assertThrows(PException.class, runnable);
        Assert.assertEquals(expectedMessage, exception.getMessage());
    }
}
