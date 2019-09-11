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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "type", type = ExpressionNode.class)
@NodeChild(value = "cause", type = ExpressionNode.class)
public abstract class RaiseNode extends StatementNode {
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    @Specialization
    public void reraise(VirtualFrame frame, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") Object cause,
                    @Cached PRaiseNode raise,
                    @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                    @Cached("createBinaryProfile()") ConditionProfile hasCurrentException) {
        PException currentException = getCaughtExceptionNode.execute(frame);
        if (hasCurrentException.profile(currentException == null)) {
            throw raise.raise(RuntimeError, "No active exception to reraise");
        }
        throw currentException;
    }

    @Specialization
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause,
                    @Cached PRaiseNode raise) {
        throw raise.raise(exception);
    }

    @Specialization(guards = "!isPNone(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object cause,
                    @Cached PRaiseNode raise,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        writeCause.execute(exception, SpecialAttributeNames.__CAUSE__, cause);
        throw raise.raise(exception);
    }

    private void checkBaseClass(VirtualFrame frame, PythonAbstractClass pythonClass, ValidExceptionNode validException, PRaiseNode raise) {
        if (!validException.execute(frame, pythonClass)) {
            baseCheckFailedProfile.enter();
            throw raiseNoException(raise);
        }
    }

    @Specialization
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractClass pythonClass, @SuppressWarnings("unused") PNone cause,
                    @Cached ValidExceptionNode validException,
                    @Cached PRaiseNode raise) {
        checkBaseClass(frame, pythonClass, validException, raise);
        throw raise.raise(pythonClass);
    }

    @Specialization(guards = "!isPNone(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractClass pythonClass, Object cause,
                    @Cached PythonObjectFactory factory,
                    @Cached ValidExceptionNode validException,
                    @Cached PRaiseNode raise,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        checkBaseClass(frame, pythonClass, validException, raise);
        PBaseException pythonException = factory.createBaseException(pythonClass);
        writeCause.execute(pythonException, SpecialAttributeNames.__CAUSE__, cause);
        throw raise.raise(pythonException);
    }

    @Specialization(guards = "!isAnyPythonObject(exception)")
    @SuppressWarnings("unused")
    void doRaise(VirtualFrame frame, Object exception, Object cause,
                    @Cached PRaiseNode raise) {
        throw raiseNoException(raise);
    }

    private static PException raiseNoException(PRaiseNode raise) {
        throw raise.raise(TypeError, "exceptions must derive from BaseException");
    }

    public static RaiseNode create(ExpressionNode type, ExpressionNode cause) {
        return RaiseNodeGen.create(type, cause);
    }
}
