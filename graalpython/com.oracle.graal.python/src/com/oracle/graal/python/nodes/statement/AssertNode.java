/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;

import java.io.PrintStream;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class AssertNode extends StatementNode {
    @Child private PRaiseNode raise;
    @Child private CastToBooleanNode condition;
    @Child private ExpressionNode message;
    @Child private LookupAndCallUnaryNode callNode;
    @CompilationFinal private Boolean assertionsEnabled = null;
    @CompilationFinal private boolean javaExceptionsFailAssertions;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    public AssertNode(CastToBooleanNode condition, ExpressionNode message) {
        this.condition = condition;
        this.message = message;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (assertionsEnabled == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PythonContext context = PythonLanguage.getContextRef().get();
            assertionsEnabled = !PythonOptions.getOption(context, PythonOptions.PythonOptimizeFlag);
            javaExceptionsFailAssertions = PythonOptions.getOption(context, PythonOptions.CatchAllExceptions);
        }
        if (assertionsEnabled) {
            try {
                if (profile.profile(!condition.executeBoolean(frame))) {
                    throw assertionFailed(frame);
                }
            } catch (PException e) {
                // Python exceptions just fall through
                throw e;
            } catch (Exception e) {
                if (javaExceptionsFailAssertions) {
                    // catch any other exception and convert to Python exception
                    throw assertionFailed(frame);
                } else {
                    throw e;
                }
            }
        }
    }

    private PException assertionFailed(VirtualFrame frame) {
        String assertionMessage = "";
        if (message != null) {
            try {
                Object messageObj = message.execute(frame);
                if (callNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__STR__));
                }
                assertionMessage = (String) callNode.executeObject(frame, messageObj);
            } catch (PException e) {
                // again, Python exceptions just fall through
                throw e;
            } catch (Exception e) {
                assertionMessage = "internal exception occurred";
                PythonContext context = getContext();
                if (PythonOptions.getOption(context, PythonOptions.WithJavaStacktrace)) {
                    printStackTrace(context, e);
                }
            }
        }
        if (raise == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raise = insert(PRaiseNode.create());
        }
        return raise.raise(AssertionError, assertionMessage);
    }

    public CastToBooleanNode getCondition() {
        return condition;
    }

    public ExpressionNode getMessage() {
        return message;
    }

    @TruffleBoundary
    private static void printStackTrace(PythonContext context, Exception e) {
        e.printStackTrace(new PrintStream(context.getStandardErr()));
    }

    private PythonContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef.get();
    }
}
