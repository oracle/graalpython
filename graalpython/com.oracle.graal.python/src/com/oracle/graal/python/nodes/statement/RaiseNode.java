/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "type", type = ExpressionNode.class)
@NodeChild(value = "cause", type = ExpressionNode.class)
public abstract class RaiseNode extends StatementNode {
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    @ImportStatic(PGuards.class)
    public abstract static class SetExceptionCauseNode extends Node {
        public abstract void execute(VirtualFrame frame, PBaseException exception, Object cause);

        // raise * from <exception>
        @Specialization
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, PBaseException cause) {
            exception.setCause(cause);
        }

        // raise * from <class>
        @Specialization(guards = "lib.isLazyPythonClass(causeClass)")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object causeClass,
                        @Cached BranchProfile baseCheckFailedProfile,
                        @Cached ValidExceptionNode validException,
                        @Cached CallNode callConstructor,
                        @Cached PRaiseNode raise,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            if (!validException.execute(frame, causeClass)) {
                baseCheckFailedProfile.enter();
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
            }
            Object cause = callConstructor.execute(causeClass);
            if (cause instanceof PBaseException) {
                exception.setCause((PBaseException) cause);
            } else {
                // msimacek: CPython currently (3.8.2) has a bug that it's not checking the type of
                // the value returned by the constructor, you can set a non-exception as a cause
                // this way and see the exception printer TypeError on it. I don't want to raise an
                // exception because CPython doesn't do it and I don't want to change the cause type
                // to Object either, because it's not meant to be that way.
                exception.setCause(null);
            }
        }

        // raise * from None
        @Specialization(guards = "isNone(cause)")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause) {
            setCause(frame, exception, (PBaseException) null);
        }

        // raise * from <invalid>
        @Specialization(guards = "!isValidCause(cause)")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PBaseException exception, @SuppressWarnings("unused") Object cause,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
        }

        protected static boolean isValidCause(Object object) {
            return object instanceof PBaseException || object instanceof PythonAbstractClass || PGuards.isNone(object);
        }
    }

    // raise
    @Specialization(guards = "isNoValue(type)")
    static void reraise(VirtualFrame frame, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") Object cause,
                    @Cached PRaiseNode raise,
                    @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                    @Cached("createBinaryProfile()") ConditionProfile hasCurrentException) {
        PException caughtException = getCaughtExceptionNode.execute(frame);
        if (hasCurrentException.profile(caughtException == null)) {
            throw raise.raise(RuntimeError, ErrorMessages.NO_ACTIVE_EX_TO_RERAISE);
        }
        throw caughtException.getExceptionForReraise();
    }

    // raise <exception>
    @Specialization(guards = "isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause,
                    @Cached BranchProfile isReraise,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        if (exception.getException() != null) {
            isReraise.enter();
            exception.ensureReified();
        }
        throw PRaiseNode.raise(this, exception, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    // raise <exception> from *
    @Specialization(guards = "!isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object cause,
                    @Cached BranchProfile isReraise,
                    @Cached SetExceptionCauseNode setExceptionCauseNode,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        if (exception.getException() != null) {
            isReraise.enter();
            exception.ensureReified();
        }
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raise(this, exception, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    private void checkBaseClass(VirtualFrame frame, PythonAbstractClass pythonClass, ValidExceptionNode validException, PRaiseNode raise) {
        if (!validException.execute(frame, pythonClass)) {
            baseCheckFailedProfile.enter();
            throw raiseNoException(raise);
        }
    }

    // raise <class>
    @Specialization(guards = "isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractClass pythonClass, @SuppressWarnings("unused") PNone cause,
                    @Cached ValidExceptionNode validException,
                    @Cached CallNode callConstructor,
                    @Cached BranchProfile constructorTypeErrorProfile,
                    @Cached PRaiseNode raise,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (newException instanceof PBaseException) {
            throw raise.raiseExceptionObject((PBaseException) newException, language);
        } else {
            constructorTypeErrorProfile.enter();
            throw raise.raise(TypeError, "calling %s should have returned an instance of BaseException, not %p", pythonClass, newException);
        }
    }

    // raise <class> from *
    @Specialization(guards = "!isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractClass pythonClass, Object cause,
                    @Cached ValidExceptionNode validException,
                    @Cached PRaiseNode raise,
                    @Cached CallNode callConstructor,
                    @Cached SetExceptionCauseNode setExceptionCauseNode,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (newException instanceof PBaseException) {
            setExceptionCauseNode.execute(frame, (PBaseException) newException, cause);
            throw raise.raiseExceptionObject((PBaseException) newException, language);
        } else {
            throw raise.raise(TypeError, "calling %s should have returned an instance of BaseException, not %p", pythonClass, newException);
        }
    }

    // raise <invalid> [from *]
    @Specialization(guards = "!isBaseExceptionOrPythonClass(exception)")
    @SuppressWarnings("unused")
    static void doRaise(VirtualFrame frame, Object exception, Object cause,
                    @Cached PRaiseNode raise) {
        throw raiseNoException(raise);
    }

    private static PException raiseNoException(PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
    }

    public static RaiseNode create(ExpressionNode type, ExpressionNode cause) {
        return RaiseNodeGen.create(type, cause);
    }

    protected static boolean isBaseExceptionOrPythonClass(Object object) {
        return object instanceof PBaseException || object instanceof PythonAbstractClass;
    }
}
