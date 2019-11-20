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

package com.oracle.graal.python.runtime;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.control.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNodeGen;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * An ExecutionContext ensures proper entry and exit for Python calls on both sides of the call, and
 * depending on whether the other side is also a Python frame.
 */
public abstract class ExecutionContext {

    public static final class CallContext extends Node {
        @CompilationFinal boolean neededCallerFrame;
        @CompilationFinal boolean neededExceptionState;
        private static final CallContext INSTANCE = new CallContext(false);

        @Child private MaterializeFrameNode materializeNode;

        private final boolean adoptable;

        @CompilationFinal private ConditionProfile isPythonFrameProfile;

        private CallContext(boolean adoptable) {
            this.adoptable = adoptable;
            this.neededExceptionState = !adoptable;
            this.neededCallerFrame = !adoptable;
        }

        /**
         * Prepare a call from a Python frame to a Python function.
         */
        public void prepareCall(VirtualFrame frame, Object[] callArguments, RootCallTarget callTarget, Node callNode) {
            // equivalent to PyPy's ExecutionContext.enter `frame.f_backref =
            // self.topframeref` we here pass the current top frame reference to
            // the next frame. An optimization we do is to only pass the frame
            // info if the caller requested it, otherwise they'll have to deopt
            // and walk the stack up once.

            // n.b.: The class cast should always be correct, since this context
            // must only be used when calling from Python to Python
            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            if (calleeRootNode.needsCallerFrame()) {
                if (!neededCallerFrame) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    neededCallerFrame = true;
                    reportPolymorphicSpecialize();
                }
                PFrame.Reference thisInfo;

                if (isPythonFrame(frame, callNode)) {
                    thisInfo = PArguments.getCurrentFrameInfo(frame);

                    // We are handing the PFrame of the current frame to the caller, i.e., it does
                    // not 'escape' since it is still on the stack.Also, force synchronization of
                    // values
                    PFrame pyFrame = materialize(frame, callNode, false, true);
                    assert thisInfo.getPyFrame() == pyFrame;
                    assert pyFrame.getRef() == thisInfo;
                } else {
                    thisInfo = PFrame.Reference.EMPTY;
                }

                thisInfo.setCallNode(callNode);
                PArguments.setCallerFrameInfo(callArguments, thisInfo);
            }
            if (calleeRootNode.needsExceptionState()) {
                if (!neededExceptionState) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    neededExceptionState = true;
                    reportPolymorphicSpecialize();
                }
                PException curExc = null;
                if (isPythonFrame(frame, callNode)) {
                    curExc = PArguments.getException(frame);
                    if (curExc == null) {
                        // bad, but we must provide the exception state

                        // TODO: frames: check that this also set
                        // needsExceptionState on our own root node
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        PException fromStackWalk = GetCaughtExceptionNode.fullStackWalk();
                        curExc = fromStackWalk != null ? fromStackWalk : PException.NO_EXCEPTION;
                        // now, set in our args, such that we won't do this again
                        PArguments.setException(frame, curExc);
                    }
                } else {
                    // If we're here, it can only be because some top-level call
                    // inside Python led us here
                    curExc = PException.NO_EXCEPTION;
                }
                PArguments.setException(callArguments, curExc);
            }
        }

        private PFrame materialize(VirtualFrame frame, Node callNode, boolean markAsEscaped, boolean forceSync) {
            if (adoptable) {
                return ensureMaterializeNode().execute(frame, callNode, markAsEscaped, forceSync);
            }
            return MaterializeFrameNode.getUnadoptable().execute(frame, callNode, markAsEscaped, forceSync);
        }

        private boolean isPythonFrame(VirtualFrame frame, Node callNode) {
            if (isPythonFrameProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPythonFrameProfile = ConditionProfile.createBinaryProfile();
            }
            boolean result = isPythonFrameProfile.profile(PArguments.isPythonFrame(frame));
            assert result || callNode.getRootNode() instanceof TopLevelExceptionHandler : "calling from non-Python or non-top-level frame";
            return result;
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;

        }

        @Override
        public boolean isAdoptable() {
            return adoptable;
        }

        public static CallContext create() {
            return new CallContext(true);
        }

        public static CallContext getUncached() {
            return INSTANCE;
        }
    }

    public static final class CalleeContext extends Node {

        @Child private MaterializeFrameNode materializeNode;

        @CompilationFinal private boolean everEscaped = false;

        @Override
        public Node copy() {
            return new CalleeContext();
        }

        /**
         * Wrap the execution of a Python callee called from a Python frame.
         */
        public static void enter(VirtualFrame frame, ConditionProfile profile) {
            // tfel: Create our frame reference here and store it so that
            // there's no reference to it from the caller side.
            PFrame.Reference thisFrameRef = new PFrame.Reference(PArguments.getCallerFrameInfo(frame));
            Object customLocals = PArguments.getCustomLocals(frame);
            PArguments.setCurrentFrameInfo(frame, thisFrameRef);
            // tfel: If there are custom locals, write them into an (incomplete)
            // PFrame here
            if (profile.profile(customLocals != null && !(customLocals instanceof PFrame.Reference))) {
                thisFrameRef.setCustomLocals(customLocals);
            }
        }

        public void exit(VirtualFrame frame, PRootNode node) {
            /*
             * equivalent to PyPy's ExecutionContext.leave. Note that <tt>got_exception</tt> in
             * their code is handled automatically by the Truffle lazy exceptions, so here we only
             * deal with explicitly escaped frames.
             */
            PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
            CompilerAsserts.partialEvaluationConstant(node);
            if (node.getFrameEscapedProfile().profile(info.isEscaped())) {
                if (!everEscaped) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    everEscaped = true;
                    reportPolymorphicSpecialize();
                }
                // This assumption acts as our branch profile here
                PFrame.Reference callerInfo = PArguments.getCallerFrameInfo(frame);
                if (callerInfo == null) {
                    // we didn't request the caller frame reference. now we need it.
                    CompilerDirectives.transferToInterpreterAndInvalidate();

                    // n.b. We need to use 'ReadCallerFrameNode.getCallerFrame' instead of
                    // 'Truffle.getRuntime().getCallerFrame()' because we still need to skip
                    // non-Python frames, even if we do not skip frames of builtin functions.
                    Frame callerFrame = ReadCallerFrameNode.getCallerFrame(info, FrameInstance.FrameAccess.READ_ONLY, false, 0);
                    if (PArguments.isPythonFrame(callerFrame)) {
                        callerInfo = PArguments.getCurrentFrameInfo(callerFrame);
                    } else {
                        // TODO: frames: an assertion should be that this is one of our
                        // entry point call nodes
                        callerInfo = PFrame.Reference.EMPTY;
                    }
                    // ReadCallerFrameNode.getCallerFrame must have the assumption invalidated
                    assert node.needsCallerFrame() : "stack walk did not invalidate caller frame assumption";
                }

                // force the frame so that it can be accessed later
                ensureMaterializeNode().execute(frame, node, false, true);
                info.materialize(frame, node);
                // if this frame escaped we must ensure that also f_back does
                callerInfo.markAsEscaped();
                info.setBackref(callerInfo);
            }
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }

        public static CalleeContext create() {
            return new CalleeContext();
        }

    }

    public abstract static class IndirectCallContext {
        /**
         * Prepare a call from a Python frame to a callable without frame. This transfers the
         * exception state from the frame to the context and also puts the current frame info (which
         * represents the last Python caller) in the context.
         *
         * This is mostly useful when calling methods annotated with {@code @TruffleBoundary} that
         * again use nodes that would require a frame. Use following pattern to call such methods
         * and just pass a {@code null} frame.
         * <p>
         *
         * <pre>
         * public abstract class SomeNode extends Node {
         *     {@literal @}Child private OtherNode otherNode = OtherNode.create();
         *
         *     public abstract Object execute(VirtualFrame frame, Object arg);
         *
         *     {@literal @}Specialization
         *     Object doSomething(VirtualFrame frame, Object arg,
         *                            {@literal @}CachedContext(PythonLanguage.class) PythonContext context) {
         *         // ...
         *         PException savedExceptionState = IndirectCallContext.enter(frame, context, this);
         *         try {
         *             truffleBoundaryMethod(arg);
         *         } finally {
         *             IndirectCallContext.exit(context, savedExceptionState);
         *         }
         *         // ...
         *     }
         *
         *     {@literal @}TruffleBoundary
         *     private void truffleBoundaryMethod(Object arg) {
         *         otherNode.execute(null, arg);
         *     }
         *
         * </pre>
         * </p>
         */
        public static PException enter(VirtualFrame frame, PythonContext context, Node callNode) {
            if (frame == null || context == null) {
                return null;
            }
            PFrame.Reference prev = context.popTopFrameInfo();
            assert prev == null : "trying to call from Python to a foreign function, but we didn't clear the topframeref. " +
                            "This indicates that a call into Python code happened without a proper enter through ForeignToPythonCallContext";
            PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
            info.setCallNode(callNode);
            // TODO: frames: add an assumption that none of the callers interop calls ever need
            // these infos
            context.setTopFrameInfo(info);
            return ExceptionContext.enter(context, PArguments.getException(frame));
        }

        /**
         * Cleanup after a call without frame. For more details, see
         * {@link #enter(VirtualFrame, PythonContext, Node)}.
         */
        public static void exit(VirtualFrame frame, PythonContext context, PException savedExceptionState) {
            if (frame != null && context != null) {
                context.popTopFrameInfo();
                ExceptionContext.exit(context, savedExceptionState);
            }
        }
    }

    public abstract static class ForeignCallContext {

        /**
         * Prepare a call from a Python frame to foreign callable. This will also call
         * {@link IndirectCallContext#enter(VirtualFrame, PythonContext, Node)} to transfer the
         * state to the context. In addition, this will acquire the interop lock from the
         * {@link PythonContext} to ensure exclusive execution to prevent unsynchronized global
         * state modification (which is in particular a problem when calling native code).
         *
         * <pre>
         * public abstract class SomeNode extends Node {
         *     {@literal @}Child private OtherNode otherNode = OtherNode.create();
         *
         *     public abstract Object execute(VirtualFrame frame, Object arg);
         *
         *     {@literal @}Specialization
         *     Object doSomething(VirtualFrame frame, Object foreignCallable,
         *                            {@literal @}CachedContext(PythonLanguage.class) PythonContext context,
         *                            {@literal @}CachedLibrary InteropLibrary interopLib}) {
         *         // ...
         *         PException savedExceptionState = ForeignCallContext.enter(frame, context, this);
         *         try {
         *             return lib.execute(foreignCallable, 1, 2, 3);
         *         } finally {
         *             ForeignCallContext.exit(context, savedExceptionState);
         *         }
         *         // ...
         *     }
         *
         * </pre>
         * </p>
         */
        public static PException enter(VirtualFrame frame, PythonContext context, Node callNode) {
            if (context == null) {
                return null;
            }
            if (!context.getSingleThreadedAssumption().isValid()) {
                context.acquireInteropLock();
            }
            return IndirectCallContext.enter(frame, context, callNode);
        }

        /**
         * Cleanup after an interop call. For more details, see
         * {@link #enter(VirtualFrame, PythonContext, Node)}.
         */
        public static void exit(VirtualFrame frame, PythonContext context, PException savedExceptionState) {
            if (context != null) {
                IndirectCallContext.exit(frame, context, savedExceptionState);
                if (!context.getSingleThreadedAssumption().isValid()) {
                    context.releaseInteropLock();
                }
            }
        }
    }

    public abstract static class IndirectCalleeContext {
        /**
         * Prepare a call from a foreign frame to a Python function.
         */
        public static PFrame.Reference enter(PythonContext context, Object[] pArguments, RootCallTarget callTarget) {
            Reference popTopFrameInfo = context.popTopFrameInfo();
            PArguments.setCallerFrameInfo(pArguments, popTopFrameInfo);

            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            if (calleeRootNode.needsExceptionState()) {
                PException curExc = context.getCaughtException();
                if (curExc == null) {
                    // bad, but we must provide the exception state

                    // TODO: frames: check that this also set
                    // needsExceptionState on our own root node
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    PException fromStackWalk = GetCaughtExceptionNode.fullStackWalk();
                    curExc = fromStackWalk != null ? fromStackWalk : PException.NO_EXCEPTION;
                    // now, set in our args, such that we won't do this again
                    context.setCaughtException(curExc);
                }
                PArguments.setException(pArguments, curExc);
            }
            return popTopFrameInfo;
        }

        public static void exit(PythonContext context, PFrame.Reference frameInfo) {
            // Note that the Python callee, if it escaped, has already been
            // materialized due to a CalleeContext in its RootNode. If this
            // topframeref was marked as escaped, it'll be materialized at the
            // latest needed time
            context.setTopFrameInfo(frameInfo);
        }
    }

    public abstract static class ExceptionContext {

        public static PException enter(PythonContext context, PException exceptionState) {
            PException cur = context.getCaughtException();
            context.setCaughtException(exceptionState);
            return cur;
        }

        public static void exit(PythonContext context, PException savedExceptionState) {
            if (context != null) {
                context.setCaughtException(savedExceptionState);
            }
        }

    }
}
