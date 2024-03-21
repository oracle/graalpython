/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateInline(false) // used in BCI root node
public abstract class RaiseNode extends PNodeWithContext {
    public abstract void execute(VirtualFrame frame, Object typeOrExceptionObject, Object cause, boolean rootNodeVisible);

    @ImportStatic(PGuards.class)
    @GenerateInline(false) // Not used in all specializations, better be lazy
    public abstract static class SetExceptionCauseNode extends Node {
        public abstract void execute(VirtualFrame frame, Object exception, Object cause);

        // raise * from None
        @Specialization(guards = "isNone(cause)")
        static void setNone(@SuppressWarnings("unused") VirtualFrame frame, Object exception, @SuppressWarnings("unused") PNone cause,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, exception, PNone.NONE);
        }

        // raise * from <exception>
        @Specialization(guards = "check.execute(inliningTarget, cause)", limit = "1")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, Object exception, Object cause,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PyExceptionInstanceCheckNode check,
                        @Exclusive @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, exception, cause);
        }

        // raise * from <class>
        @Specialization(guards = "isTypeNode.execute(inliningTarget, causeClass)", limit = "1")
        static void setCause(VirtualFrame frame, Object exception, Object causeClass,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Exclusive @Cached InlinedBranchProfile baseCheckFailedProfile,
                        @Exclusive @Cached ValidExceptionNode validException,
                        @Exclusive @Cached CallNode callConstructor,
                        @Exclusive @Cached PRaiseNode.Lazy raise,
                        @Exclusive @Cached PyExceptionInstanceCheckNode check,
                        @Exclusive @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            if (!validException.execute(frame, causeClass)) {
                baseCheckFailedProfile.enter(inliningTarget);
                throw raise.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
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
        @Specialization(guards = {"!check.execute(inliningTarget, cause)", "!isTypeNode.execute(inliningTarget, cause)"}, limit = "1")
        static void setCause(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object exception, @SuppressWarnings("unused") Object cause,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Exclusive @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @SuppressWarnings("unused") @Exclusive @Cached PyExceptionInstanceCheckNode check,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXCEPTION_CAUSES_MUST_DERIVE_FROM_BASE_EX);
        }
    }

    // raise
    @Specialization(guards = "isNoValue(type)")
    public static void reraise(VirtualFrame frame, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") Object cause, boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached PRaiseNode.Lazy raise,
                    @Exclusive @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                    @Exclusive @Cached InlinedConditionProfile hasCurrentException) {
        PException caughtException = getCaughtExceptionNode.execute(frame);
        if (hasCurrentException.profile(inliningTarget, caughtException == null)) {
            throw raise.get(inliningTarget).raise(RuntimeError, ErrorMessages.NO_ACTIVE_EX_TO_RERAISE);
        }
        throw caughtException.getExceptionForReraise(rootNodeVisible);
    }

    // raise <exception>
    @Specialization(guards = "isNoValue(cause)")
    public static void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget) {
        throw PRaiseNode.raiseExceptionObject(inliningTarget, exception);
    }

    // raise <native-exception>
    @Specialization(guards = {"check.execute(inliningTarget, exception)", "isNoValue(cause)"})
    public static void doRaiseNative(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractNativeObject exception, @SuppressWarnings("unused") PNone cause,
                    @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check) {
        throw PRaiseNode.raiseExceptionObject(inliningTarget, exception);
    }

    // raise <exception> from *
    @Specialization(guards = "!isNoValue(cause)")
    public static void doRaise(@SuppressWarnings("unused") VirtualFrame frame, PBaseException exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached SetExceptionCauseNode setExceptionCauseNode) {
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raiseExceptionObject(inliningTarget, exception);
    }

    // raise <native-exception> from *
    @Specialization(guards = {"check.execute(inliningTarget, exception)", "!isNoValue(cause)"})
    public static void doRaiseNative(@SuppressWarnings("unused") VirtualFrame frame, PythonAbstractNativeObject exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached SetExceptionCauseNode setExceptionCauseNode) {
        setExceptionCauseNode.execute(frame, exception, cause);
        throw PRaiseNode.raiseExceptionObject(inliningTarget, exception);
    }

    private static void checkBaseClass(VirtualFrame frame, Node inliningTarget, Object pythonClass, ValidExceptionNode validException, PRaiseNode.Lazy raise,
                    InlinedBranchProfile baseCheckFailedProfile) {
        if (!validException.execute(frame, pythonClass)) {
            baseCheckFailedProfile.enter(inliningTarget);
            throw raiseNoException(raise.get(inliningTarget));
        }
    }

    // raise <class>
    @Specialization(guards = {"isTypeNode.execute(this, pythonClass)", "isNoValue(cause)"}, limit = "1")
    public static void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached ValidExceptionNode validException,
                    @Exclusive @Cached CallNode callConstructor,
                    @Exclusive @Cached PyExceptionInstanceCheckNode check,
                    @Exclusive @Cached InlinedBranchProfile baseCheckFailedProfile,
                    @Exclusive @Cached PRaiseNode.Lazy raise) {
        checkBaseClass(frame, inliningTarget, pythonClass, validException, raise, baseCheckFailedProfile);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (check.execute(inliningTarget, newException)) {
            throw PRaiseNode.raiseExceptionObject(inliningTarget, newException);
        } else {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <class> from *
    @Specialization(guards = {"isTypeNode.execute(this, pythonClass)", "!isNoValue(cause)"}, limit = "1")
    public static void doRaise(@SuppressWarnings("unused") VirtualFrame frame, Object pythonClass, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached ValidExceptionNode validException,
                    @Exclusive @Cached PRaiseNode.Lazy raise,
                    @Exclusive @Cached CallNode callConstructor,
                    @Exclusive @Cached PyExceptionInstanceCheckNode check,
                    @Exclusive @Cached InlinedBranchProfile baseCheckFailedProfile,
                    @Exclusive @Cached SetExceptionCauseNode setExceptionCauseNode) {
        checkBaseClass(frame, inliningTarget, pythonClass, validException, raise, baseCheckFailedProfile);
        Object newException = callConstructor.execute(frame, pythonClass);
        if (check.execute(inliningTarget, newException)) {
            setExceptionCauseNode.execute(frame, newException, cause);
            throw PRaiseNode.raiseExceptionObject(inliningTarget, newException);
        } else {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, pythonClass, newException);
        }
    }

    // raise <invalid> [from *]
    @Fallback
    @SuppressWarnings("unused")
    public static void doRaise(VirtualFrame frame, Object exception, Object cause, @SuppressWarnings("unused") boolean rootNodeVisible,
                    @Bind("this") Node inliningTarget,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @Exclusive @Cached PRaiseNode.Lazy raise) {
        if (lib.isException(exception)) {
            try {
                throw lib.throwException(exception);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
        throw raiseNoException(raise.get(inliningTarget));
    }

    private static PException raiseNoException(PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
    }

    @NeverDefault
    public static RaiseNode create() {
        return RaiseNodeGen.create();
    }
}
