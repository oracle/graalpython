/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodesFactory.RestoreExceptionStateNodeGen;
import com.oracle.graal.python.nodes.util.ExceptionStateNodesFactory.SaveExceptionStateNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExceptionStateNodes {

    protected abstract static class ExceptionStateBaseNode extends Node {
    }

    /**
     * Writes an exception into the corresponding slot in the arguments. The frame slot is created
     * if it does not yet exist. This node should primarily be used in an exception handler to make
     * the exception accessible.
     */
    public static final class SetCaughtExceptionNode extends ExceptionStateBaseNode {

        @CompilationFinal private FrameSlot excSlot;

        public static void execute(VirtualFrame frame, PException e) {
            PArguments.setException(frame, e);
        }

        public static SetCaughtExceptionNode create() {
            return new SetCaughtExceptionNode();
        }
    }

    /**
     * Use this node to forcefully get the current exception state. <it>Forcefully</it> means, if
     * the exception state is not provided in the frame, in the arguments or in the context, it will
     * do a full stack walk and request the exception state for the next time from the callers. The
     * returned object may escape to the value space.
     */
    public static final class GetCaughtExceptionNode extends ExceptionStateBaseNode {

        private final ConditionProfile nullFrameProfile = ConditionProfile.createBinaryProfile();

        @CompilationFinal private ContextReference<PythonContext> contextRef;

        public PException execute(VirtualFrame frame) {
            if (nullFrameProfile.profile(frame == null)) {
                return getFromContext();
            }
            PException e = PArguments.getException(frame);
            if (e == null) {
                e = fromStackWalk();
            }
            return ensure(e);
        }

        private PException getFromContext() {
            // contextRef acts as a branch profile
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            PythonContext ctx = contextRef.get();
            PException fromContext = ctx.getCaughtException();
            if (fromContext == null) {
                fromContext = fromStackWalk();

                // important: set into context to avoid stack walk next time
                ctx.setCaughtException(fromContext != null ? fromContext : PException.NO_EXCEPTION);
            }
            return ensure(fromContext);
        }

        private static PException fromStackWalk() {
            // The very-slow path: This is the first time we want to fetch the exception state
            // from the context. The caller didn't know that it is necessary to provide the
            // exception in the context. So, we do a full stack walk until the first frame
            // having the exception state in the special slot. And we set the appropriate flag
            // on the root node such that the next time, we will find the exception state in the
            // context immediately.
            CompilerDirectives.transferToInterpreter();

            // TODO(fa) performance warning ?
            return fullStackWalk();
        }

        @TruffleBoundary
        public static PException fullStackWalk() {

            return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<PException>() {
                public PException visitFrame(FrameInstance frameInstance) {
                    RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                    RootNode rootNode = target.getRootNode();
                    if (rootNode instanceof PRootNode) {
                        PRootNode pRootNode = (PRootNode) rootNode;
                        pRootNode.setNeedsExceptionState();
                        Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                        return PArguments.getException(frame);
                    }
                    return null;
                }
            });

        }

        private static PException ensure(PException e) {
            return e != PException.NO_EXCEPTION ? e : null;
        }

        public static GetCaughtExceptionNode create() {
            return new GetCaughtExceptionNode();
        }
    }

    /**
     * Use this node to pass the exception state if provided. This node won't do a full stack walk
     * and may return {@link PException#NO_EXCEPTION}. This node should primarily be used to move
     * the exception state from the frame to the context or vice versa.
     */
    public static final class PassCaughtExceptionNode extends ExceptionStateBaseNode {

        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public PException execute(VirtualFrame frame) {
            if (profile.profile(frame == null)) {
                return null;
            }
            return PArguments.getException(frame);
        }

        public static PassCaughtExceptionNode create() {
            return new PassCaughtExceptionNode();
        }

    }

    /**
     * Saves the current local exception state. This is required for nested {@code try-except}
     * statements because all exception handlers in one function store the exception state to the
     * same frame slot.
     */
    public abstract static class SaveExceptionStateNode extends ExceptionStateBaseNode {

        public abstract ExceptionState execute(VirtualFrame frame);

        @Specialization(guards = "frame != null")
        static ExceptionState doArgs(VirtualFrame frame) {
            return new ExceptionState(PArguments.getException(frame), ExceptionState.SOURCE_CALLER);
        }

        @Specialization(guards = "frame == null")
        static ExceptionState doContext(@SuppressWarnings("unused") VirtualFrame frame,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return new ExceptionState(context.getCaughtException(), ExceptionState.SOURCE_CONTEXT);
        }

        @Specialization(replaces = {"doArgs", "doContext"})
        static ExceptionState doGeneric(VirtualFrame frame,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            if (frame != null) {
                return doArgs(frame);
            }
            return doContext(frame, context);
        }

        public static SaveExceptionStateNode create() {
            return SaveExceptionStateNodeGen.create();
        }

    }

    /**
     * Restores the exception state.
     */
    public abstract static class RestoreExceptionStateNode extends ExceptionStateBaseNode {

        public abstract void execute(VirtualFrame frame, ExceptionState state);

        @SuppressWarnings("unused")
        @Specialization(guards = "state == null")
        void doNothing(VirtualFrame frame, ExceptionState state) {
        }

        @Specialization(guards = "fromArgs(e)")
        void doArgs(VirtualFrame frame, ExceptionState e) {
            PArguments.setException(frame, e.exc);
        }

        @Specialization(guards = "fromContext(e)")
        void doContext(@SuppressWarnings("unused") VirtualFrame frame, ExceptionState e,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            context.setCaughtException(e.exc);
        }

        @Specialization(replaces = {"doNothing", "doArgs", "doContext"})
        void doGeneric(VirtualFrame frame, ExceptionState e,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            if (e == null) {
                return;
            }
            if (fromArgs(e)) {
                doArgs(frame, e);
            } else if (fromContext(e)) {
                doContext(frame, e, context);
            }
        }

        protected static boolean fromFrame(ExceptionState state) {
            return state.source == ExceptionState.SOURCE_FRAME;
        }

        protected static boolean fromArgs(ExceptionState state) {
            return state.source == ExceptionState.SOURCE_CALLER;
        }

        protected static boolean fromContext(ExceptionState state) {
            return state.source == ExceptionState.SOURCE_CONTEXT;
        }

        public static RestoreExceptionStateNode create() {
            return RestoreExceptionStateNodeGen.create();
        }
    }

    @ValueType
    public static final class ExceptionState {
        public static final int SOURCE_FRAME = 0;
        public static final int SOURCE_CONTEXT = 1;
        public static final int SOURCE_GENERATOR = 2;
        public static final int SOURCE_CALLER = 3;

        public final PException exc;
        public final int source;

        public ExceptionState(PException exc, int source) {
            this.exc = exc;
            this.source = source;
        }
    }
}
