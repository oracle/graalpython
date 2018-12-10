/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.BaseException;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@SuppressWarnings("unused")
@NodeChild(value = "type", type = ExpressionNode.class)
@NodeChild(value = "cause", type = ExpressionNode.class)
public abstract class RaiseNode extends StatementNode {

    private final IsBuiltinClassProfile simpleBaseCheckProfile = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile iterativeBaseCheckProfile = IsBuiltinClassProfile.create();
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    @Specialization
    public void reraise(PNone type, Object cause,
                    @Cached("createBinaryProfile()") ConditionProfile hasCurrentException) {
        PythonContext context = getContext();
        PException currentException = context.getCaughtException();
        if (hasCurrentException.profile(currentException == null)) {
            throw raise(RuntimeError, "No active exception to reraise");
        }
        throw currentException;
    }

    @Specialization
    public void doRaise(PBaseException exception, PNone cause) {
        throw raise(exception);
    }

    @Specialization(guards = "!isPNone(cause)")
    public void doRaise(PBaseException exception, Object cause,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        writeCause.execute(exception, SpecialAttributeNames.__CAUSE__, cause);
        throw raise(exception);
    }

    private void checkBaseClass(PythonClass pythonClass) {
        if (simpleBaseCheckProfile.profileClass(pythonClass, BaseException)) {
            return;
        }
        for (PythonClass klass : pythonClass.getMethodResolutionOrder()) {
            if (iterativeBaseCheckProfile.profileClass(klass, BaseException)) {
                return;
            }
        }
        baseCheckFailedProfile.enter();
        throw raise(PythonErrorType.TypeError, "exceptions must derive from BaseException");
    }

    @Specialization
    public void doRaise(PythonClass pythonClass, PNone cause) {
        checkBaseClass(pythonClass);
        throw raise(pythonClass);
    }

    @Specialization(guards = "!isPNone(cause)")
    public void doRaise(PythonClass pythonClass, Object cause,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        checkBaseClass(pythonClass);
        PBaseException pythonException = factory().createBaseException(pythonClass);
        writeCause.execute(pythonException, SpecialAttributeNames.__CAUSE__, cause);
        throw raise(pythonException);
    }

    @Fallback
    public void doRaise(Object exception, Object cause) {
        throw raise(TypeError, "exceptions must derive from BaseException");
    }

    public static RaiseNode create(ExpressionNode type, ExpressionNode cause) {
        return RaiseNodeGen.create(type, cause);
    }
}
