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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.exception.ValidExceptionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public abstract class RaiseNode extends PNodeWithContext {
    private final BranchProfile baseCheckFailedProfile = BranchProfile.create();

    public abstract void execute(VirtualFrame frame, Object typeOrExceptionObject, Object cause, boolean rootNodeVisible);

    @ImportStatic(PGuards.class)
    public abstract static class SetExceptionCauseNode extends Node {
        public abstract void execute(VirtualFrame frame, Object exception, Object cause);

        // raise * from None
        @Specialization(guards = "isNone(cause)")
        static void setNone(@SuppressWarnings("unused") VirtualFrame frame, Object exception, @SuppressWarnings("unused") PNone cause,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, exception, PNone.NONE);
        }

        // raise * from <exception>
        @Specialization(guards = "check.execute(inliningTarget, cause)")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, Object exception, Object cause,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, exception, cause);
        }

        // raise * from <class>
        @Specialization(guards = "isTypeNode.execute(causeClass)", limit = "1")
        static void setCause(VirtualFrame frame, Object exception, Object causeClass,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached InlinedBranchProfile baseCheckFailedProfile,
                        @Cached ValidExceptionNode validException,
                        @Cached CallNode callConstructor,
                        @Cached PRaiseNode raise,
                        @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            if (!validException.execute(frame, causeClass)) {
                baseCheckFailedProfile.enter(inliningTarget);
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
            }
            Object cause = callConstructor.execute(frame, causeClass);
            if (check.execute(inliningTarget, cause)) {
                setCauseNode.execute(inliningTarget, exception, cause);
            } else {
                // msimacek: CPython currently (3.8.2) has a bug that it's not checking the type of
                // the value returned by the constructor, you can set a non-exception as a cause
                // this way and see the exception printer TypeError on it. I don't want to raise an
                // exception because CPython doesn't do it and I don't want to change the cause type
                // to Object either, because it's not meant to be that way.
                setCauseNode.execute(inliningTarget, exception, PNone.NONE);
            }
        }

        // raise * from <invalid>
        @Fallback
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object exception, @SuppressWarnings("unused") Object cause,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
        }
    }

    // raise
    @Specialization(guards = "isNoValue(type)")
    static void reraise(VirtualFrame frame, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") Object cause, boolean rootNodeVisible,
                    @Shared @Cached PRaiseNode raise,
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
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible) {
        throw PRaiseNode.raiseExceptionObject(this, exception);
    }

    // raise <native-exception>
    @Specialization(guards = {"check.execute(inliningTarget, exception)", "isNoValue(cause)"})
    void doRaiseNative(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractNativeObject exception, @SuppressWarnings("unused") PNone cause,
                    @SuppressWarnings("unused") boolean rootNodeVisible,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check) {
        throw PRaiseNode.raiseExceptionObject(this, exception);
    }

    // raise <exception> from *
    @Specialization(guards = "!isNoValue(cause)")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Shared @Cached SetExceptionCauseNode setExceptionCauseNode) {
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raiseExceptionObject(this, exception);
    }

    // raise <native-exception> from *
    @Specialization(guards = {"check.execute(inliningTarget, exception)", "!isNoValue(cause)"})
    void doRaiseNative(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractNativeObject exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached SetExceptionCauseNode setExceptionCauseNode) {
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raiseExceptionObject(this, exception);
    }

    private void checkBaseClass(VirtualFrame frame, Object pythonClass, ValidExceptionNode validException, PRaiseNode raise) {
        if (!validException.execute(frame, pythonClass)) {
            baseCheckFailedProfile.enter();
            throw raiseNoException(raise);
        }
    }

    // raise <class>
    @Specialization(guards = {"isTypeNode.execute(pythonClass)", "isNoValue(cause)"}, limit = "1")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Shared @Cached ValidExceptionNode validException,
                    @Shared @Cached CallNode callConstructor,
                    @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached PRaiseNode raise) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (check.execute(inliningTarget, newException)) {
            throw PRaiseNode.raiseExceptionObject(this, newException);
        } else {
            throw raise.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <class> from *
    @Specialization(guards = {"isTypeNode.execute(pythonClass)", "!isNoValue(cause)"}, limit = "1")
    void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Shared @Cached ValidExceptionNode validException,
                    @Shared @Cached PRaiseNode raise,
                    @Shared @Cached CallNode callConstructor,
                    @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached SetExceptionCauseNode setExceptionCauseNode) {
        checkBaseClass(frame, pythonClass, validException, raise);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (check.execute(inliningTarget, newException)) {
            setExceptionCauseNode.execute(frame, newException, cause);
            throw PRaiseNode.raiseExceptionObject(this, newException);
        } else {
            throw raise.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <invalid> [from *]
    @Fallback
    @SuppressWarnings("unused")
    static void doRaise(VirtualFrame frame, Object exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @Shared @Cached PRaiseNode raise) {
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
}
