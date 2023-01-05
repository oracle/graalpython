/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.exception.ValidExceptionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class RaiseNode extends PNodeWithContext {
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    public abstract void execute(VirtualFrame frame, Object typeOrExceptionObject, Object cause, boolean rootNodeVisible);

    @ImportStatic(PGuards.class)
    public abstract static class SetExceptionCauseNode extends Node {
        public abstract void execute(VirtualFrame frame, PBaseException exception, Object cause);

        // raise * from <exception>
        @Specialization
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, PBaseException cause) {
            exception.setCause(cause);
        }

        // raise * from <class>
        @Specialization(guards = "isTypeNode.execute(causeClass)", limit = "1")
        static void setCause(VirtualFrame frame, PBaseException exception, Object causeClass,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached BranchProfile baseCheckFailedProfile,
                        @Cached ValidExceptionNode validException,
                        @Cached CallNode callConstructor,
                        @Cached PRaiseNode raise) {
            if (!validException.execute(frame, causeClass)) {
                baseCheckFailedProfile.enter();
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
            }
            Object cause = callConstructor.execute(frame, causeClass);
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
            return object instanceof PBaseException || PGuards.isPythonClass(object) || PGuards.isNone(object);
        }
    }

    // raise
    @Specialization(guards = "isNoValue(type)")
    static void reraise(VirtualFrame frame, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") Object cause, boolean rootNodeVisible,
                    @Cached PRaiseNode raise,
                    @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                    @Cached ConditionProfile hasCurrentException) {
        PException caughtException = getCaughtExceptionNode.execute(frame);
        if (hasCurrentException.profile(caughtException == null)) {
            throw raise.raise(RuntimeError, ErrorMessages.NO_ACTIVE_EX_TO_RERAISE);
        }
        throw caughtException.getExceptionForReraise(rootNodeVisible);
    }

    // raise <exception>
    @Specialization(guards = "isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Cached BranchProfile isReraise) {
        if (exception.getException() != null) {
            isReraise.enter();
            exception.ensureReified();
        }
        throw PRaiseNode.raise(this, exception, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(this)));
    }

    // raise <exception> from *
    @Specialization(guards = "!isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Cached BranchProfile isReraise,
                    @Cached SetExceptionCauseNode setExceptionCauseNode) {
        if (exception.getException() != null) {
            isReraise.enter();
            exception.ensureReified();
        }
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raise(this, exception, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(this)));
    }

    private void checkBaseClass(VirtualFrame frame, Object pythonClass, ValidExceptionNode validException, PRaiseNode raise) {
        if (!validException.execute(frame, pythonClass)) {
            baseCheckFailedProfile.enter();
            throw raiseNoException(raise);
        }
    }

    // raise <class>
    @Specialization(guards = {"isPythonClass(pythonClass)", "isNoValue(cause)"})
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Cached ValidExceptionNode validException,
                    @Cached CallNode callConstructor,
                    @Cached BranchProfile constructorTypeErrorProfile,
                    @Cached PRaiseNode raise) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (newException instanceof PBaseException) {
            throw raise.raiseExceptionObject((PBaseException) newException);
        } else {
            constructorTypeErrorProfile.enter();
            throw raise.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <class> from *
    @Specialization(guards = {"isPythonClass(pythonClass)", "!isNoValue(cause)"})
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Cached ValidExceptionNode validException,
                    @Cached PRaiseNode raise,
                    @Cached CallNode callConstructor,
                    @Cached SetExceptionCauseNode setExceptionCauseNode) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (newException instanceof PBaseException) {
            setExceptionCauseNode.execute(frame, (PBaseException) newException, cause);
            throw raise.raiseExceptionObject((PBaseException) newException);
        } else {
            throw raise.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <invalid> [from *]
    @Specialization(guards = "!isBaseExceptionOrPythonClass(exception)", limit = "3")
    @SuppressWarnings("unused")
    static void doRaise(VirtualFrame frame, Object exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @CachedLibrary("exception") InteropLibrary lib,
                    @Cached PRaiseNode raise) {
        if (lib.isException(exception)) {
            try {
                throw lib.throwException(exception);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
        throw raiseNoException(raise);
    }

    private static PException raiseNoException(PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
    }

    public static RaiseNode create() {
        return RaiseNodeGen.create();
    }

    protected static boolean isBaseExceptionOrPythonClass(Object object) {
        return object instanceof PBaseException || PGuards.isPythonClass(object);
    }
}
