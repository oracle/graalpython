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
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
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
@NodeChildren({@NodeChild(value = "type", type = PNode.class), @NodeChild(value = "cause", type = PNode.class)})
public abstract class RaiseNode extends StatementNode {

    private final ConditionProfile simpleBaseCheckProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    @Specialization
    public Object reraise(PNone type, Object cause,
                    @Cached("createBinaryProfile()") ConditionProfile hasCurrentException) {
        PythonContext context = getContext();
        PException currentException = context.getCurrentException();
        if (hasCurrentException.profile(currentException == null)) {
            throw raise(RuntimeError, "No active exception to reraise");
        }
        throw currentException;
    }

    @Specialization
    public Object raise(PBaseException exception, PNone cause) {
        throw getCore().raise(exception, this);
    }

    @Specialization(guards = "!isPNone(cause)")
    public Object raise(PBaseException exception, Object cause,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        writeCause.execute(exception, SpecialAttributeNames.__CAUSE__, cause);
        throw getCore().raise(exception, this);
    }

    private void checkBaseClass(PythonClass pythonClass) {
        PythonClass baseExceptionClass = getCore().getErrorClass(BaseException);
        if (simpleBaseCheckProfile.profile(pythonClass == baseExceptionClass)) {
            return;
        }
        for (PythonClass klass : pythonClass.getMethodResolutionOrder()) {
            if (klass == baseExceptionClass) {
                return;
            }
        }
        baseCheckFailedProfile.enter();
        throw raise(PythonErrorType.TypeError, "exceptions must derive from BaseException");
    }

    @Specialization
    public Object raise(PythonClass pythonClass, PNone cause) {
        checkBaseClass(pythonClass);
        throw getCore().raise(factory().createBaseException(pythonClass), this);
    }

    @Specialization(guards = "!isPNone(cause)")
    public Object raise(PythonClass pythonClass, Object cause,
                    @Cached("create()") WriteAttributeToObjectNode writeCause) {
        checkBaseClass(pythonClass);
        PBaseException pythonException = factory().createBaseException(pythonClass);
        writeCause.execute(pythonException, SpecialAttributeNames.__CAUSE__, cause);
        throw getCore().raise(pythonException, this);
    }

    @Fallback
    public Object raise(Object exception, Object cause) {
        throw raise(TypeError, "exceptions must derive from BaseException");
    }

    public static RaiseNode create(PNode type, PNode cause) {
        return RaiseNodeGen.create(type, cause);
    }
}
