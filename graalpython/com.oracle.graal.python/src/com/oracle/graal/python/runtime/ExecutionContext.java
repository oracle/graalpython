/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.exception.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNodeGen;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode.StackWalkResult;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

/**
 * An ExecutionContext ensures proper entry and exit for Python calls on both sides of the call, and
 * depending on whether the other side is also a Python frame.
 */
public abstract class ExecutionContext {
    @GenerateInline(false) // 28 -> 10
    @GenerateUncached
    public abstract static class CallContext extends Node {
        /**
         * Prepare an indirect call from a Python frame to a Python function.
         */
        public void prepareIndirectCall(VirtualFrame frame, Object[] callArguments, Node callNode) {
            executePrepareCall(frame, getActualCallArguments(callArguments), callNode, true, true);
        }

        private static Object[] getActualCallArguments(Object[] callArguments) {
            /**
             * Bytecode DSL note: When resuming a generator/coroutine, the call target is a
             * ContinuationRoot with a different calling convention from regular PRootNodes. The
             * first argument is a materialized frame containing the arguments used for argument
             * reads.
             */
            if (callArguments.length == 2 && callArguments[0] instanceof MaterializedFrame materialized) {
                return materialized.getArguments();
            }
            return callArguments;
        }

        /**
         * Prepare a call from a Python frame to a Python function.
         */
        public void prepareCall(VirtualFrame frame, Object[] callArguments, RootCallTarget callTarget, Node callNode) {
            RootNode rootNode = callTarget.getRootNode();

            PRootNode calleeRootNode;
            Object[] actualCallArguments;
            boolean needsExceptionState;
            if (rootNode instanceof ContinuationRootNode continuationRoot) {
                calleeRootNode = (PRootNode) continuationRoot.getSourceRootNode();
                assert callArguments.length == 2;
                actualCallArguments = ((MaterializedFrame) callArguments[0]).getArguments();
                // Local exception state takes precedence over any exception in the caller's context
                needsExceptionState = calleeRootNode.needsExceptionState() && !PArguments.hasException(actualCallArguments);
            } else {
                // n.b.: The class cast should always be correct, since this context
                // must only be used when calling from Python to Python
                calleeRootNode = (PRootNode) rootNode;
                actualCallArguments = callArguments;
                needsExceptionState = calleeRootNode.needsExceptionState();
            }
            executePrepareCall(frame, actualCallArguments, callNode, calleeRootNode.needsCallerFrame(), needsExceptionState);
        }

        protected abstract void executePrepareCall(VirtualFrame frame, Object[] callArguments, Node callNode, boolean needsCallerFrame, boolean needsExceptionState);

        /**
         * Equivalent to PyPy's ExecutionContext.enter `frame.f_backref = self.topframeref` we here
         * pass the current top frame reference to the next frame. An optimization we do is to only
         * pass the frame info if the caller requested it, otherwise they'll have to deopt and walk
         * the stack up once.
         */
        @GenerateCached(false)
        @GenerateUncached
        @GenerateInline
        @ImportStatic(PArguments.class)
        protected abstract static class PassCallerFrameNode extends Node {
            protected abstract void execute(VirtualFrame frame, Node inliningTarget, Object[] callArguments, Node callNode, boolean needsCallerFrame);

            @Specialization(guards = "!needsCallerFrame")
            protected static void dontPassCallerFrame(Object[] callArguments, Node callNode, boolean needsCallerFrame) {
            }

            @Specialization(guards = {"needsCallerFrame", "isPythonFrame(frame)"})
            protected static void passCallerFrame(VirtualFrame frame, Object[] callArguments, Node callNode, boolean needsCallerFrame,
                            @Cached(inline = false) MaterializeFrameNode materialize) {
                PFrame.Reference thisInfo = PArguments.getCurrentFrameInfo(frame);
                // We are handing the PFrame of the current frame to the caller, i.e., it does
                // not 'escape' since it is still on the stack.Also, force synchronization of
                // values
                PFrame pyFrame = materialize.execute(frame, callNode, false, true);
                assert thisInfo.getPyFrame() == pyFrame;
                assert pyFrame.getRef() == thisInfo;
                PArguments.setCallerFrameInfo(callArguments, thisInfo);
            }

            @Specialization(guards = {"needsCallerFrame", "!isPythonFrame(frame)"})
            protected static void passEmptyCallerFrame(VirtualFrame frame, Object[] callArguments, Node callNode, boolean needsCallerFrame) {
                PArguments.setCallerFrameInfo(callArguments, PFrame.Reference.EMPTY);
            }
        }

        @GenerateCached(false)
        @GenerateUncached
        @GenerateInline
        @ImportStatic(PArguments.class)
        protected abstract static class PassExceptionStateNode extends Node {

            /*
             * This may seem a bit odd on first sight, but it's straightforward with a bit of
             * explanation:
             *
             * 1. Most callees won't need exception state, so that is the first specialization. We
             * pass the NO_EXCEPTION marker if we have it though.
             *
             * 2. If we call a callee that needs exception state, the first time around we likely do
             * not have it, so we do a stack walk. If this is a top level function e.g. always
             * called from a new Python lambda or something like that in an embedding, we will get
             * stuck in this specialization, but that's the best we can do and it's straight line
             * code with a boundary call.
             *
             * 3. If we come around again in normal Python code, we'll likely have exception state
             * now because the caller passed it. If this caller is the only one that needs to pass
             * exception state (maybe all other callers do not trigger code paths that need it) we
             * will never have to walk the stack again. So we *replace* the specialization that does
             * the stack walk with one that never does so there are just guards and no full branches
             * in the compiled code.
             *
             * 4. If we get into the situation again that we need to pass exception state, but do
             * not have it, this means we got invoked from another call site that did not pass the
             * exception state. We resist the tempation to be fancy here. We'll switch to putting
             * the stack walk in the compiled code with a profile to inject how probable the stack
             * walk is. We'll just have to hope the compiler does something decent with it. We also
             * report this as an expensive specialization using the @Megamorphic annotation, so
             * Truffle might be more inclined to split.
             *
             * 5. The last and least likely scenario is that this is directly a call from an
             * embedding, e.g. via the #execute interop message. We trivially won't have an active
             * exception in this case.
             */
            protected abstract void execute(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState);

            @Specialization(guards = {"!needsExceptionState"})
            protected static void dontPassExceptionState(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState,
                            @Cached InlinedConditionProfile hasNoException) {
                AbstractTruffleException curExc = PArguments.getException(frame);
                if (hasNoException.profile(inliningTarget, curExc == PException.NO_EXCEPTION)) {
                    PArguments.setException(callArguments, curExc);
                }
            }

            @Specialization(guards = {"needsExceptionState", "isPythonFrame(frame)", "getException(frame) == null"})
            protected static void passExceptionStateFromStackWalk(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState) {
                AbstractTruffleException fromStackWalk = GetCaughtExceptionNode.fullStackWalk();
                if (fromStackWalk == null) {
                    fromStackWalk = PException.NO_EXCEPTION;
                }
                // set it also in our args, such that we won't stack walk again in later calls that
                // start with this frame
                PArguments.setException(frame, fromStackWalk);
                PArguments.setException(callArguments, fromStackWalk);
            }

            @Specialization(guards = {"needsExceptionState", "isPythonFrame(frame)", "curExc != null"}, replaces = "passExceptionStateFromStackWalk")
            protected static void passGivenExceptionState(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState,
                            @Bind("getException(frame)") AbstractTruffleException curExc) {
                PArguments.setException(callArguments, curExc);
            }

            @ReportPolymorphism.Megamorphic
            @Specialization(guards = {"needsExceptionState", "isPythonFrame(frame)"}, replaces = "passGivenExceptionState")
            protected static void passExceptionStateFromFrameOrStack(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState,
                            @Cached InlinedCountingConditionProfile needsStackWalk) {
                AbstractTruffleException curExc = PArguments.getException(frame);
                if (needsStackWalk.profile(inliningTarget, curExc == null)) {
                    passExceptionStateFromStackWalk(frame, inliningTarget, callArguments, needsExceptionState);
                } else {
                    passGivenExceptionState(frame, inliningTarget, callArguments, needsExceptionState, curExc);
                }
            }

            @Specialization(guards = {"needsExceptionState", "!isPythonFrame(frame)"})
            protected static void passNoExceptionState(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState) {
                // If we're here, it can only be because some top-level call
                // inside Python led us here
                PArguments.setException(callArguments, PException.NO_EXCEPTION);
            }
        }

        @Specialization
        protected static void prepareCall(VirtualFrame frame, Object[] callArguments, Node callNode, boolean needsCallerFrame, boolean needsExceptionState,
                        @Bind Node inliningTarget,
                        @Cached PassCallerFrameNode passCallerFrame,
                        @Cached PassExceptionStateNode passExceptionState) {
            assert PArguments.isPythonFrame(frame) || callNode.getRootNode() instanceof TopLevelExceptionHandler : "calling from non-Python or non-top-level frame";
            passCallerFrame.execute(frame, inliningTarget, callArguments, callNode, needsCallerFrame);
            passExceptionState.execute(frame, inliningTarget, callArguments, needsExceptionState);
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
        public void enter(VirtualFrame frame) {
            // TODO: assert PythonLanguage.getContext().ownsGil() :
            // PythonContext.dumpStackOnAssertionHelper("callee w/o GIL");
            // tfel: Create our frame reference here and store it so that
            // there's no reference to it from the caller side.
            PFrame.Reference thisFrameRef = new PFrame.Reference(getRootNode(), PArguments.getCallerFrameInfo(frame));
            PArguments.setCurrentFrameInfo(frame, thisFrameRef);
        }

        public void exit(VirtualFrame frame, PRootNode node) {
            exit(frame, node, node);
        }

        public void exit(VirtualFrame frame, PRootNode node, Node location) {
            /*
             * equivalent to PyPy's ExecutionContext.leave. Note that <tt>got_exception</tt> in
             * their code is handled automatically by the Truffle lazy exceptions, so here we only
             * deal with explicitly escaped frames.
             */
            PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
            CompilerAsserts.partialEvaluationConstant(node);
            if (node.getFrameEscapedProfile().profile(info.isEscaped())) {
                exitEscaped(frame, node, location, info);
            }
        }

        @InliningCutoff
        private void exitEscaped(VirtualFrame frame, PRootNode node, Node location, Reference info) {
            if (!everEscaped) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                everEscaped = true;
                reportPolymorphicSpecialize();
            }
            // This assumption acts as our branch profile here
            Reference callerInfo = PArguments.getCallerFrameInfo(frame);
            if (callerInfo == null) {
                // we didn't request the caller frame reference. now we need it.
                CompilerDirectives.transferToInterpreter();

                // n.b. We need to use 'ReadCallerFrameNode.getCallerFrame' instead of
                // 'Truffle.getRuntime().getCallerFrame()' because we still need to skip
                // non-Python frames, even if we do not skip frames of builtin functions.
                StackWalkResult callerFrameResult = ReadCallerFrameNode.getCallerFrame(info, FrameInstance.FrameAccess.READ_ONLY, ReadCallerFrameNode.AllFramesSelector.INSTANCE, 0);
                if (callerFrameResult != null) {
                    callerInfo = PArguments.getCurrentFrameInfo(callerFrameResult.frame());
                } else {
                    callerInfo = Reference.EMPTY;
                }
                // ReadCallerFrameNode.getCallerFrame must have the assumption invalidated
                assert node.needsCallerFrame() : "stack walk did not invalidate caller frame assumption";
            }

            // force the frame so that it can be accessed later
            ensureMaterializeNode().execute(frame, location, false, true);
            // if this frame escaped we must ensure that also f_back does
            callerInfo.markAsEscaped();
            info.setBackref(callerInfo);
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }

        @NeverDefault
        public static CalleeContext create() {
            return new CalleeContext();
        }

    }

    @ValueType
    private static final class IndirectCallState {
        private final PFrame.Reference info;
        private final AbstractTruffleException curExc;

        private IndirectCallState(PFrame.Reference info, AbstractTruffleException curExc) {
            this.info = info;
            this.curExc = curExc;
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
         *     Object doSomething(VirtualFrame frame, Object arg) {
         *         // ...
         *         PException savedExceptionState = IndirectCallContext.enter(frame, PythonContext.get(this), this);
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
        public static Object enter(VirtualFrame frame, PythonLanguage language, PythonContext context, IndirectCallData indirectCallData) {
            if (frame == null || indirectCallData.isUncached()) {
                return null;
            }
            boolean needsCallerFrame = indirectCallData.calleeNeedsCallerFrame();
            boolean needsExceptionState = indirectCallData.calleeNeedsExceptionState();
            if (!needsCallerFrame && !needsExceptionState) {
                return null;
            }

            PythonThreadState pythonThreadState = context.getThreadState(language);
            return enter(frame, pythonThreadState, needsCallerFrame, needsExceptionState);
        }

        public static Object enter(VirtualFrame frame, Node node, IndirectCallData indirectCallData) {
            if (frame == null || indirectCallData.isUncached()) {
                return null;
            }
            boolean needsCallerFrame = indirectCallData.calleeNeedsCallerFrame();
            boolean needsExceptionState = indirectCallData.calleeNeedsExceptionState();
            if (!needsCallerFrame && !needsExceptionState) {
                return null;
            }

            PythonContext context = PythonContext.get(node);
            PythonThreadState pythonThreadState = context.getThreadState(context.getLanguage(node));
            return enter(frame, pythonThreadState, needsCallerFrame, needsExceptionState);
        }

        /**
         * @see #enter(VirtualFrame, PythonLanguage, PythonContext, IndirectCallData)
         */
        public static Object enter(VirtualFrame frame, PythonThreadState pythonThreadState, IndirectCallData indirectCallData) {
            if (frame == null || indirectCallData.isUncached()) {
                return null;
            }
            return enter(frame, pythonThreadState, indirectCallData.calleeNeedsCallerFrame(), indirectCallData.calleeNeedsExceptionState());
        }

        private static IndirectCallState enter(VirtualFrame frame, PythonThreadState pythonThreadState, boolean needsCallerFrame, boolean needsExceptionState) {
            PFrame.Reference info = null;
            if (needsCallerFrame) {
                PFrame.Reference prev = pythonThreadState.popTopFrameInfo();
                assert prev == null : "trying to call from Python to a foreign function, but we didn't clear the topframeref. " +
                                "This indicates that a call into Python code happened without a proper enter through ForeignToPythonCallContext";
                info = PArguments.getCurrentFrameInfo(frame);
                pythonThreadState.setTopFrameInfo(info);
            }
            AbstractTruffleException curExc = pythonThreadState.getCaughtException();
            AbstractTruffleException exceptionState = PArguments.getException(frame);
            if (needsExceptionState) {
                pythonThreadState.setCaughtException(exceptionState);
            } else if (exceptionState != curExc) {
                // the thread state has exception info inconsistent with the current frame's. we
                // need to force lower frames to walk the stack
                pythonThreadState.setCaughtException(null);
            }

            if (HostCompilerDirectives.inInterpreterFastPath() && curExc == null && info == null) {
                return null;
            } else {
                return new IndirectCallState(info, curExc);
            }
        }

        /**
         * Cleanup after a call without frame. For more details, see {@link #enter}.
         */
        public static void exit(VirtualFrame frame, PythonLanguage language, PythonContext context, Object savedState) {
            if (savedState != null && frame != null && context != null) {
                exit(frame, context.getThreadState(language), savedState);
                return;
            }
            assert savedState == null : "tried to exit an indirect call with state, but without frame/context";
        }

        public static void exit(VirtualFrame frame, Node node, IndirectCallData indirectCallData, Object savedState) {
            if (savedState != null && frame != null && !indirectCallData.isUncached()) {
                PythonContext context = PythonContext.get(node);
                if (context != null) {
                    PythonLanguage language = context.getLanguage(node);
                    exit(frame, context.getThreadState(language), savedState);
                    return;
                }
            }
            assert savedState == null : "tried to exit an indirect call with state, but without frame/context";
        }

        /**
         * @see #exit(VirtualFrame, PythonLanguage, PythonContext, Object)
         */
        public static void exit(VirtualFrame frame, PythonThreadState pythonThreadState, Object savedState) {
            if (frame == null) {
                assert savedState == null : "tried to exit an indirect call with state, but without frame";
                return;
            }
            if (savedState == null) {
                return;
            }
            IndirectCallState state = (IndirectCallState) savedState;
            if (state.info != null) {
                pythonThreadState.popTopFrameInfo();
            }
            if (state.curExc != null) {
                pythonThreadState.setCaughtException(state.curExc);
            }
        }
    }

    public abstract static class IndirectCalleeContext {
        /**
         * Prepare an indirect call from a foreign frame to a Python function.
         */
        public static Object enterIndirect(PythonLanguage language, PythonContext context, Object[] pArguments) {
            return enter(context.getThreadState(language), pArguments, true);
        }

        /**
         * @see #enterIndirect(PythonLanguage, PythonContext, Object[])
         */
        public static Object enterIndirect(PythonThreadState threadState, Object[] pArguments) {
            return enter(threadState, pArguments, true);
        }

        /**
         * @see #enter(PythonThreadState, Object[], RootCallTarget)
         */
        public static Object enter(PythonLanguage language, PythonContext context, Object[] pArguments, RootCallTarget callTarget) {
            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            return enter(context.getThreadState(language), pArguments, calleeRootNode.needsExceptionState());
        }

        /**
         * Prepare a call from a foreign frame to a Python function.
         */
        public static Object enter(PythonThreadState threadState, Object[] pArguments, RootCallTarget callTarget) {
            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            return enter(threadState, pArguments, calleeRootNode.needsExceptionState());
        }

        private static Object enter(PythonThreadState threadState, Object[] pArguments, boolean needsExceptionState) {
            Reference popTopFrameInfo = threadState.popTopFrameInfo();
            PArguments.setCallerFrameInfo(pArguments, popTopFrameInfo);

            if (needsExceptionState) {
                AbstractTruffleException curExc = threadState.getCaughtException();
                if (curExc != null) {
                    threadState.setCaughtException(null);
                }
                PArguments.setException(pArguments, curExc);
                return new IndirectCallState(popTopFrameInfo, curExc);
            }
            return popTopFrameInfo;
        }

        public static void exit(PythonLanguage language, PythonContext context, Object state) {
            exit(context.getThreadState(language), state);
        }

        public static void exit(PythonThreadState threadState, Object state) {
            /*
             * Note that the Python callee, if it escaped, has already been materialized due to a
             * CalleeContext in its RootNode. If this topframeref was marked as escaped, it'll be
             * materialized at the latest needed time
             */
            if (state instanceof IndirectCallState indirectCallState) {
                threadState.setTopFrameInfo(indirectCallState.info);
                threadState.setCaughtException(indirectCallState.curExc);
            } else {
                threadState.setTopFrameInfo((Reference) state);
            }
        }
    }
}
