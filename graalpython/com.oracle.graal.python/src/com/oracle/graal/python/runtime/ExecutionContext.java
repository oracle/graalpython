/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode.FrameSelector;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
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
         * Prepare an indirect call from a Python frame to a Python function.
         */
        public void prepareIndirectCall(VirtualFrame frame, Object[] callArguments, Node callNode) {
            prepareCall(frame, getActualCallArguments(callArguments), callNode, true, true);
        }

        /**
         * Prepare a call from a Python frame to a Python function.
         */
        public void prepareCall(VirtualFrame frame, Object[] callArguments, RootCallTarget callTarget, Node callNode) {
            RootNode rootNode = callTarget.getRootNode();

            PRootNode calleeRootNode;
            Object[] actualCallArguments;
            if (rootNode instanceof ContinuationRootNode continuationRoot) {
                calleeRootNode = (PRootNode) continuationRoot.getSourceRootNode();
                assert callArguments.length == 2;
                actualCallArguments = ((MaterializedFrame) callArguments[0]).getArguments();
            } else {
                // n.b.: The class cast should always be correct, since this context
                // must only be used when calling from Python to Python
                calleeRootNode = (PRootNode) rootNode;
                actualCallArguments = callArguments;
            }
            prepareCall(frame, actualCallArguments, callNode, calleeRootNode.needsCallerFrame(), calleeRootNode.needsExceptionState());

        }

        private void prepareCall(VirtualFrame frame, Object[] callArguments, Node callNode, boolean needsCallerFrame, boolean needsExceptionState) {
            // equivalent to PyPy's ExecutionContext.enter `frame.f_backref =
            // self.topframeref` we here pass the current top frame reference to
            // the next frame. An optimization we do is to only pass the frame
            // info if the caller requested it, otherwise they'll have to deopt
            // and walk the stack up once.

            if (needsCallerFrame) {
                if (!neededCallerFrame) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    neededCallerFrame = true;
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
            if (needsExceptionState) {
                if (!neededExceptionState) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    neededExceptionState = true;
                }
                PException curExc;
                if (isPythonFrame(frame, callNode)) {
                    curExc = PArguments.getException(frame);
                    if (curExc == null) {
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

        private PFrame materialize(VirtualFrame frame, Node callNode, boolean markAsEscaped, boolean forceSync) {
            if (adoptable) {
                return ensureMaterializeNode().execute(frame, callNode, markAsEscaped, forceSync);
            }
            return MaterializeFrameNode.getUncached().execute(frame, callNode, markAsEscaped, forceSync);
        }

        private boolean isPythonFrame(VirtualFrame frame, Node callNode) {
            if (isPythonFrameProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPythonFrameProfile = ConditionProfile.create();
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

        @NeverDefault
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
        public void enter(VirtualFrame frame) {
            // TODO: assert PythonLanguage.getContext().ownsGil() :
            // PythonContext.dumpStackOnAssertionHelper("callee w/o GIL");
            // tfel: Create our frame reference here and store it so that
            // there's no reference to it from the caller side.
            PFrame.Reference thisFrameRef = new PFrame.Reference(PArguments.getCallerFrameInfo(frame));
            PArguments.setCurrentFrameInfo(frame, thisFrameRef);
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
                exitEscaped(frame, node, info);
            }
        }

        @InliningCutoff
        private void exitEscaped(VirtualFrame frame, PRootNode node, Reference info) {
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
                Frame callerFrame = ReadCallerFrameNode.getCallerFrame(info, FrameInstance.FrameAccess.READ_ONLY, FrameSelector.ALL_PYTHON_FRAMES, 0);
                if (PArguments.isPythonFrame(callerFrame)) {
                    callerInfo = PArguments.getCurrentFrameInfo(callerFrame);
                } else {
                    // TODO: frames: an assertion should be that this is one of our
                    // entry point call nodes
                    callerInfo = Reference.EMPTY;
                }
                // ReadCallerFrameNode.getCallerFrame must have the assumption invalidated
                assert node.needsCallerFrame() : "stack walk did not invalidate caller frame assumption";
            }

            // force the frame so that it can be accessed later
            ensureMaterializeNode().execute(frame, node, false, true);
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

        public static CalleeContext create() {
            return new CalleeContext();
        }

    }

    @ValueType
    private static final class IndirectCallState {
        private final PFrame.Reference info;
        private final PException curExc;

        private IndirectCallState(PFrame.Reference info, PException curExc) {
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
            return enter(frame, pythonThreadState, needsCallerFrame, needsExceptionState, indirectCallData.getNode());
        }

        public static Object enter(VirtualFrame frame, IndirectCallData indirectCallData) {
            if (frame == null || indirectCallData.isUncached()) {
                return null;
            }
            boolean needsCallerFrame = indirectCallData.calleeNeedsCallerFrame();
            boolean needsExceptionState = indirectCallData.calleeNeedsExceptionState();
            if (!needsCallerFrame && !needsExceptionState) {
                return null;
            }

            Node indirectCallNode = indirectCallData.getNode();
            PythonThreadState pythonThreadState = PythonContext.get(indirectCallNode).getThreadState(PythonLanguage.get(indirectCallNode));
            return enter(frame, pythonThreadState, needsCallerFrame, needsExceptionState, indirectCallNode);
        }

        /**
         * @see #enter(VirtualFrame, PythonLanguage, PythonContext, IndirectCallData)
         */
        public static Object enter(VirtualFrame frame, PythonThreadState pythonThreadState, IndirectCallData indirectCallData) {
            if (frame == null || indirectCallData.isUncached()) {
                return null;
            }
            return enter(frame, pythonThreadState, indirectCallData.calleeNeedsCallerFrame(), indirectCallData.calleeNeedsExceptionState(), indirectCallData.getNode());
        }

        private static IndirectCallState enter(VirtualFrame frame, PythonThreadState pythonThreadState, boolean needsCallerFrame, boolean needsExceptionState, Node callNode) {
            PFrame.Reference info = null;
            if (needsCallerFrame) {
                PFrame.Reference prev = pythonThreadState.popTopFrameInfo();
                assert prev == null : "trying to call from Python to a foreign function, but we didn't clear the topframeref. " +
                                "This indicates that a call into Python code happened without a proper enter through ForeignToPythonCallContext";
                info = PArguments.getCurrentFrameInfo(frame);
                info.setCallNode(callNode);
                pythonThreadState.setTopFrameInfo(info);
            }
            PException curExc = pythonThreadState.getCaughtException();
            PException exceptionState = PArguments.getException(frame);
            if (needsExceptionState) {
                pythonThreadState.setCaughtException(exceptionState);
            } else if (exceptionState != curExc) {
                // the thread state has exception info inconsistent with the current frame's. we
                // need to force lower frames to walk the stack
                pythonThreadState.setCaughtException(null);
            }

            if (curExc == null && info == null) {
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

        public static void exit(VirtualFrame frame, IndirectCallData indirectCallData, Object savedState) {
            if (savedState != null && frame != null && !indirectCallData.isUncached()) {
                Node indirectCallNode = indirectCallData.getNode();
                PythonContext context = PythonContext.get(indirectCallNode);
                if (context != null) {
                    PythonLanguage language = PythonLanguage.get(indirectCallNode);
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
            pythonThreadState.setCaughtException(state.curExc);
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
                PException curExc = threadState.getCaughtException();
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
            if (state instanceof IndirectCallState) {
                IndirectCallState indirectCallState = (IndirectCallState) state;
                threadState.setTopFrameInfo(indirectCallState.info);
                threadState.setCaughtException(indirectCallState.curExc);
            } else {
                threadState.setTopFrameInfo((Reference) state);
            }
        }
    }
}
