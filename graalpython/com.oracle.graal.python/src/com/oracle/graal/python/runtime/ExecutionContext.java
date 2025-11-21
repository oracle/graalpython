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
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.exception.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNodeGen;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.IndirectCallData.IndirectCallDataBase;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DenyReplace;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnadoptableNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

/**
 * An ExecutionContext ensures proper entry and exit for Python calls on both sides of the call, and
 * for proper entry and exit from calls to {@code TruffleBoundary} annotated methods and calls to
 * foreign callable interop objects.
 * <p>
 * The whole reason for this infrastructure is to:
 * <p>
 * 1) avoid passing the {@link PFrame.Reference} and exception state (the currently handled
 * exception) to the callee as arguments, because that would prevent them from being escape analyzed
 * (unless everything is inlined, we do not want to rely on that).
 * <p>
 * 2) avoid materializing the {@link PFrame} instance that represents the current
 * {@link VirtualFrame}, because it is expensive operation and may prevent objects stored in the
 * {@link VirtualFrame} from being escape analyzed.
 * <p>
 * Terminology: We are in a "code with access to the Python frame" if we are executing cached AST
 * nodes of some Python root node and can pass the {@link VirtualFrame} around. If we call into a
 * {@code TruffleBoundary}, or into interop including native code, we cannot pass the
 * {@link VirtualFrame} as argument. If such code still technically takes {@code frame} argument
 * (like uncached nodes), we can pass {@code null} instead. If such code calls back to us via
 * interop (e.g., C API upcall), we are not in a Python root node anymore and do not have a Python
 * {@link VirtualFrame}, so if we call any cached AST from there, we also pass {@code null} as the
 * {@code frame} argument indicating that we do not have access to the current Python frame. Code
 * that handles the current exception state or frame reference takes different paths depending on
 * whether the {@code frame} is available.
 * <p>
 * Note: in uncached execution we can still pass the VirtualFrame around. The trick is that we do
 * {@link CompilerDirectives#transferToInterpreter()} at the beginning of uncached execution.
 * <p>
 * The Python frame is where we store the current exception state and PFrame reference, and we do
 * not want those to leak outside the PE code, which would prevent escape analysis. But if the code
 * we call (callee) needs them, we need to pass them somehow - we do that by stashing them into
 * thread state - but we do that only if the callee asked for them in the past, first time we
 * retrieve them via a slow Truffle stack walk.
 * <p>
 * Examples of some transitions (see {@code test_indirect_call.py} for runnable examples):
 * <ul>
 * <li>Python function calls Python function: the {@link PRootNode} of the callee tells us if we
 * need to pass the frame/exception state as arguments ({@link PRootNode#getCallerFlags()}. We don't
 * pass them initially. First time we actually need them, we do Truffle stack walk
 * ({@code TruffleRuntime#iterateFrames}), during which we set the
 * {@link PRootNode#getCallerFlags()} flags for all the root nodes that we had to traverse - so next
 * time, we should not need to do the stack walk, we should just receive {@link PFrame.Reference}
 * from the caller and just traverse the linked-list of {@link PFrame.Reference}s to the
 * {@link PFrame.Reference} we need.</li>
 * <li>Python function calls into {@code @TruffleBoundary} annotated code: We need to store the
 * exception state and PFrame reference into the thread state. In order to avoid doing this every
 * time, flags in {@link IndirectCallData.BoundaryCallData} tells us if we should pass them, and
 * they are also invalidated during the initial Truffle stack walk. We use the
 * {@link FrameInstance#getCallNode()} for this: it should either be
 * {@link IndirectCallData.BoundaryCallData} node itself (we do this by setting it as
 * {@link EncapsulatingNodeReference}), or an adopted node that is in the same subtree of the
 * {@link IndirectCallData.BoundaryCallData} (e.g., when interop overrides our
 * {@link EncapsulatingNodeReference}).</li>
 * <li>{@code @TruffleBoundary} annotated code calls Python function: we need to transfer the
 * exception state and the PFrame reference from the thread state and pass them to the callee in the
 * arguments.</li>
 * <li>Python code calls native code or interop: like with {@code @TruffleBoundary}, we store
 * exception state and frame reference in thread state, so that once the control flow reaches back
 * to Python code (e.g., C API call), we can read them from thread state.</li>
 * </ul>
 */
public abstract class ExecutionContext {
    /**
     * Helper methods to prepare for a call from code that has access to the current Python
     * {@link VirtualFrame} to a Python callable. The frame contains exception state and current
     * PFrame reference, which may need to be passed down to the callee (see
     * {@link PRootNode#getCallerFlags()}).
     * <p>
     * Code that does not have access to the current Python {@link VirtualFrame}, e.g.,
     * {@code TruffleBoundary} code, should be wrapped in {@link IndirectCallContext}, which
     * transfers the state from the frame to the thread state and back. If such code then needs to
     * call a Python callable, it should use {@link IndirectCalleeContext}.
     */
    @GenerateInline(false) // 28 -> 10
    @GenerateUncached
    public abstract static class CallContext extends Node {

        /**
         * Prepare a call from a Python frame to a Python function.
         */
        public void prepareCall(VirtualFrame frame, Object[] callArguments, RootCallTarget callTarget) {
            // n.b.: The class cast should always be correct, since this context
            // must only be used when calling from Python to Python
            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            executePrepareCall(frame, callArguments, calleeRootNode.getCallerFlags());
        }

        public abstract void executePrepareCall(VirtualFrame frame, Object[] callArguments, int callerFlags);

        @Specialization
        protected static void prepareCall(VirtualFrame frame, Object[] callArguments, int callerFlags,
                        @Bind Node inliningTarget,
                        @Cached PassCallerFrameNode passCallerFrame,
                        @Cached PassExceptionStateNode passExceptionState) {
            assert PArguments.isPythonFrame(frame) || inliningTarget.getRootNode() instanceof TopLevelExceptionHandler : "calling from non-Python or non-top-level frame";
            passCallerFrame.execute(frame, inliningTarget, callArguments, callerFlags);
            passExceptionState.execute(frame, inliningTarget, callArguments, CallerFlags.needsExceptionState(callerFlags));
        }

        /**
         * Equivalent to PyPy's ExecutionContext.enter `frame.f_backref = self.topframeref` we here
         * pass the current top frame reference to the next frame. An optimization we do is to only
         * pass the frame info if the caller requested it, otherwise they'll have to deopt and walk
         * the stack up once.
         */
        @GenerateCached(false)
        @GenerateInline
        @ImportStatic({PArguments.class, CallerFlags.class})
        protected abstract static class PassCallerFrameNode extends Node {
            protected abstract void execute(VirtualFrame frame, Node inliningTarget, Object[] callArguments, int callerFlags);

            @Specialization(guards = "!needsFrameReference(callerFlags)")
            protected static void dontPassCallerFrame(Object[] callArguments, int callerFlags) {
            }

            @Specialization(guards = {"needsFrameReference(callerFlags)", "isPythonFrame(frame)"})
            protected static void passCallerFrame(VirtualFrame frame, Object[] callArguments, int callerFlags,
                            @Cached(inline = false) MaterializeFrameNode materialize) {
                PFrame.Reference thisInfo = PArguments.getCurrentFrameInfo(frame);
                if (CallerFlags.needsPFrame(callerFlags)) {
                    // We are handing the PFrame of the current frame to the caller, i.e., it does
                    // not 'escape' since it is still on the stack. Also, force synchronization of
                    // values if requested
                    if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                        // For the manual interpreter it is OK to executeOnStack with uncached
                        // `materialize` Node, but once we have uncached Bytecode DSL interpreter,
                        // it
                        // will have to use EncapsulatingNodeReference or some other way (e.g., in
                        // frame) to pass down the BytecodeNode. This can be also sign of missing
                        // BoundaryCallContext.enter/exit around TruffleBoundary
                        assert materialize.isAdoptable();
                    }
                    if (thisInfo.getPyFrame() != null && !CallerFlags.needsLocals(callerFlags) && !CallerFlags.needsLasti(callerFlags)) {
                        thisInfo.getPyFrame().setLastCallerFlags(callerFlags);
                    } else {
                        PFrame pyFrame = materialize.executeOnStack(false, CallerFlags.needsLocals(callerFlags), frame);
                        assert thisInfo.getPyFrame() == pyFrame;
                        assert pyFrame.getRef() == thisInfo;
                    }
                } else if (thisInfo.getPyFrame() != null) {
                    thisInfo.getPyFrame().setLastCallerFlags(callerFlags);
                }
                PArguments.setCallerFrameInfo(callArguments, thisInfo);
            }

            @Specialization(guards = {"needsFrameReference(callerFlags)", "!isPythonFrame(frame)"})
            protected static void passEmptyCallerFrame(VirtualFrame frame, Object[] callArguments, int callerFlags) {
                PArguments.setCallerFrameInfo(callArguments, PFrame.Reference.EMPTY);
            }

            @DenyReplace
            @GenerateCached(false)
            private static final class Uncached extends PassCallerFrameNode implements UnadoptableNode {
                private static final Uncached INSTANCE = new Uncached();

                @Override
                protected void execute(VirtualFrame frame, Node inliningTarget, Object[] callArguments, int callerFlags) {
                    // Always pass the reference when uncached
                    passCallerFrame(frame, callArguments, callerFlags, MaterializeFrameNode.getUncached());
                }
            }

            public static PassCallerFrameNode getUncached() {
                return Uncached.INSTANCE;
            }
        }

        @GenerateCached(false)
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

            @DenyReplace
            @GenerateCached(false)
            private static final class Uncached extends PassExceptionStateNode implements UnadoptableNode {
                private static final Uncached INSTANCE = new Uncached();

                @Override
                protected void execute(VirtualFrame frame, Node inliningTarget, Object[] callArguments, boolean needsExceptionState) {
                    if (PArguments.isPythonFrame(frame)) {
                        AbstractTruffleException exception = PArguments.getException(frame);
                        // Always pass the exception when we have it
                        if (exception != null) {
                            PArguments.setException(callArguments, exception);
                        } else if (needsExceptionState) {
                            passExceptionStateFromStackWalk(frame, null, callArguments, true);
                        }
                    }
                }
            }

            public static PassExceptionStateNode getUncached() {
                return Uncached.INSTANCE;
            }
        }
    }

    /**
     * Execution of a Python function should be wrapped with {@link #enter(VirtualFrame)} and
     * {@link #exit(VirtualFrame, PRootNode)}.
     * <p>
     * When entering the function we create the
     * {@link com.oracle.graal.python.builtins.objects.frame.PFrame.Reference} that represents the
     * current (possibly not yet materialized virtual frame). From then on, at any point we can
     * refer to the current {@link com.oracle.graal.python.builtins.objects.frame.PFrame.Reference}
     * instance if we have the current virtual frame at hand.
     * <p>
     * When leaving the function, we check if the current frame needs to outlive the function
     * execution, i.e., needs to escape. In such case, the frame is materialized to a {@link PFrame}
     * instance, and we copy local variables and some arguments into a new {@link MaterializedFrame}
     * stored in the {@link PFrame} (see {@link MaterializeFrameNode}). The {@link PFrame} is stored
     * into {@link com.oracle.graal.python.builtins.objects.frame.PFrame.Reference}.
     * <p>
     * The frame may have been matarialized already during the function execution, in such case we
     * just synchronize the existing {@link PFrame} with the virtual frame when leaving the
     * function.
     */
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
            // For Bytecode DSL root node we need BytecodeNode as location
            assert !(node instanceof PBytecodeDSLRootNode);
            exitImpl(frame, node, node);
        }

        public void exit(VirtualFrame frame, PRootNode node, BytecodeNode location) {
            exitImpl(frame, node, location);
        }

        private void exitImpl(VirtualFrame frame, PRootNode node, Node location) {
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
            }
            // This assumption acts as our branch profile here
            Reference callerInfo = PArguments.getCallerFrameInfo(frame);
            if (callerInfo == null) {
                // we didn't request the caller frame reference. now we need it.
                CompilerDirectives.transferToInterpreterAndInvalidate();

                // n.b. We need to use 'ReadFrameNode.getCallerFrame' instead of
                // 'Truffle.getRuntime().getCallerFrame()' because we still need to skip
                // non-Python frames, even if we do not skip frames of builtin functions.
                Frame callerFrame = ReadFrameNode.getCallerFrame(info, FrameInstance.FrameAccess.READ_ONLY, ReadFrameNode.AllFramesSelector.INSTANCE, 1,
                                CallerFlags.NEEDS_FRAME_REFERENCE);
                if (callerFrame != null) {
                    callerInfo = PArguments.getCurrentFrameInfo(callerFrame);
                } else {
                    callerInfo = Reference.EMPTY;
                }
                // ReadFrameNode.getCallerFrame must have the assumption invalidated
            }
            // Else: we may have been called via uncached call where we always pass frame reference.
            // We assume uncached execution will eventually flip to cached execution, and then we'll
            // go to the other branch and setNeedsCallerFrame. This helps to prevent one-off
            // initializations (importing a module) from invalidating the assumption

            // force the frame so that it can be accessed later
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER && node instanceof PBytecodeDSLRootNode) {
                ensureMaterializeNode().executeOnStack(frame, (BytecodeNode) location, false, true);
            } else {
                ensureMaterializeNode().executeOnStack(frame, node, false, true);
            }
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
        private final Node prevEncapsulatingNode;

        private IndirectCallState(PFrame.Reference info, AbstractTruffleException curExc, Node prevEncapsulatingNode) {
            this.info = info;
            this.curExc = curExc;
            this.prevEncapsulatingNode = prevEncapsulatingNode;
        }

        private void exit(PythonThreadState pythonThreadState) {
            if (info != null) {
                pythonThreadState.popTopFrameInfo();
            }
            if (curExc != null) {
                pythonThreadState.setCaughtException(curExc);
            }
        }
    }

    public abstract static class BoundaryCallContext {
        private static final Object EMPTY_SAVED_STATE = new Object();

        /**
         * Prepare a call from a Python frame to TruffleBoundary code. This transfers the exception
         * state from the frame to the thread state and also puts the current frame info (which
         * represents the last Python caller) in the thread state, this is done so that we can then
         * access the exception state or current frame reference in the code that cannot take the
         * {@link VirtualFrame} as an argument.
         * <p>
         * See also {@link IndirectCalleeContext} for helper methods to make a call from a caller
         * without frame to a Python function.
         */
        public static Object enter(VirtualFrame frame, PythonThreadState pythonThreadState, BoundaryCallData boundaryCallData) {
            validateBoundaryCallData(boundaryCallData);
            if (frame == null || boundaryCallData.isUncached()) {
                return EMPTY_SAVED_STATE;
            }
            return enterWithPythonFrame(frame, boundaryCallData, pythonThreadState);
        }

        public static Object enter(VirtualFrame frame, PythonLanguage language, PythonContext context, BoundaryCallData boundaryCallData) {
            validateBoundaryCallData(boundaryCallData);
            if (frame == null || boundaryCallData.isUncached()) {
                return EMPTY_SAVED_STATE;
            }
            PythonThreadState pythonThreadState = context.getThreadState(language);
            return enterWithPythonFrame(frame, boundaryCallData, pythonThreadState);
        }

        public static Object enter(VirtualFrame frame, BoundaryCallData boundaryCallData) {
            validateBoundaryCallData(boundaryCallData);
            if (frame == null || boundaryCallData.isUncached()) {
                return EMPTY_SAVED_STATE;
            }
            PythonContext context = PythonContext.get(boundaryCallData);
            PythonThreadState pythonThreadState = context.getThreadState(context.getLanguage(boundaryCallData));
            return enterWithPythonFrame(frame, boundaryCallData, pythonThreadState);
        }

        private static Object enterWithPythonFrame(VirtualFrame frame, BoundaryCallData boundaryCallData, PythonThreadState pythonThreadState) {
            assert frame != null;
            return IndirectCallContext.enterWithPythonFrame(frame, boundaryCallData, boundaryCallData, pythonThreadState, boundaryCallData.getCallerFlags(), EMPTY_SAVED_STATE);
        }

        public static void exit(VirtualFrame frame, PythonLanguage language, PythonContext context, Object savedState) {
            if (savedState != EMPTY_SAVED_STATE && frame != null && context != null) {
                exit(frame, context.getThreadState(language), savedState);
                return;
            }
            assert savedState == EMPTY_SAVED_STATE : "tried to exit an indirect call with state, but without frame/context";
        }

        public static void exit(VirtualFrame frame, BoundaryCallData boundaryCallData, Object savedState) {
            if (savedState != EMPTY_SAVED_STATE && frame != null && !boundaryCallData.isUncached()) {
                PythonContext context = PythonContext.get(boundaryCallData);
                if (context != null) {
                    PythonLanguage language = context.getLanguage(boundaryCallData);
                    exit(frame, context.getThreadState(language), savedState);
                    return;
                }
            }
            assert savedState == EMPTY_SAVED_STATE : "tried to exit an indirect call with state, but without frame/context";
        }

        public static void exit(VirtualFrame frame, PythonThreadState pythonThreadState, Object savedState) {
            if (frame == null) {
                assert savedState == EMPTY_SAVED_STATE : "tried to exit an indirect call with state, but without frame";
                return;
            }
            if (savedState == EMPTY_SAVED_STATE) {
                return;
            }
            if (savedState instanceof IndirectCallState state) {
                state.exit(pythonThreadState);
                EncapsulatingNodeReference.getCurrent().set(state.prevEncapsulatingNode);
            } else {
                EncapsulatingNodeReference.getCurrent().set((Node) savedState);
            }
        }

        private static void validateBoundaryCallData(BoundaryCallData boundaryCallData) {
            assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER || boundaryCallData.isUncached() ||
                            (!(boundaryCallData.getRootNode() instanceof PBytecodeDSLRootNode) || BytecodeNode.get(boundaryCallData) != null);
        }
    }

    public abstract static class InteropCallContext {
        /**
         * Prepare a call from a Python frame to fast-path code that cannot thread through the frame
         * argument. The most notable example is when calling interop, or Truffle libraries in
         * general. This transfers the exception state from the frame to the thread state and also
         * puts the current frame info (which represents the last Python caller) in the thread
         * state, this is done so that we can then access the exception state or current frame
         * reference in the code that cannot take the {@link VirtualFrame} as an argument.
         * <p>
         * See also {@link IndirectCalleeContext} for helper methods to make a call from a caller
         * without frame to a Python function.
         */
        public static Object enter(VirtualFrame frame, PythonThreadState pythonThreadState, InteropCallData callData) {
            if (frame == null || callData.isUncached()) {
                return null;
            }
            return enterWithPythonFrame(frame, callData, pythonThreadState);
        }

        public static Object enter(VirtualFrame frame, PythonLanguage language, PythonContext context, InteropCallData callData) {
            if (frame == null || callData.isUncached()) {
                return null;
            }
            PythonThreadState pythonThreadState = context.getThreadState(language);
            return enterWithPythonFrame(frame, callData, pythonThreadState);
        }

        public static Object enter(VirtualFrame frame, Node node, InteropCallData callData) {
            if (frame == null || callData.isUncached()) {
                return null;
            }
            PythonContext context = PythonContext.get(node);
            PythonThreadState pythonThreadState = context.getThreadState(context.getLanguage(node));
            return enterWithPythonFrame(frame, callData, pythonThreadState);
        }

        private static Object enterWithPythonFrame(VirtualFrame frame, InteropCallData callData, PythonThreadState pythonThreadState) {
            assert frame != null;
            return IndirectCallContext.enterWithPythonFrame(frame, callData, null, pythonThreadState, callData.getCallerFlags(), null);
        }

        public static void exit(VirtualFrame frame, PythonLanguage language, PythonContext context, Object savedState) {
            if (savedState != null && frame != null && context != null) {
                exit(frame, context.getThreadState(language), savedState);
                return;
            }
            assert savedState == null : "tried to exit an indirect call with state, but without frame/context";
        }

        public static void exit(VirtualFrame frame, Node node, InteropCallData callData, Object savedState) {
            if (savedState != null && frame != null && !callData.isUncached()) {
                PythonContext context = PythonContext.get(node);
                if (context != null) {
                    PythonLanguage language = context.getLanguage(node);
                    exit(frame, context.getThreadState(language), savedState);
                    return;
                }
            }
            assert savedState == null : "tried to exit an indirect call with state, but without frame/context";
        }

        public static void exit(VirtualFrame frame, PythonThreadState pythonThreadState, Object savedState) {
            if (frame == null) {
                assert savedState == null : "tried to exit an indirect call with state, but without frame";
                return;
            }
            if (savedState == null) {
                return;
            }
            if (savedState instanceof IndirectCallState state) {
                state.exit(pythonThreadState);
            }
        }
    }

    // Common code shared by BoundaryCallContext and InteropCallContext
    public abstract static class IndirectCallContext {
        private static Object enterWithPythonFrame(VirtualFrame frame, IndirectCallDataBase callData, Node encapsulatingNodeToPush,
                        PythonThreadState pythonThreadState, int callerFlags, Object defaultReturn) {
            CompilerAsserts.partialEvaluationConstant(encapsulatingNodeToPush == null);
            CompilerAsserts.partialEvaluationConstant(defaultReturn);
            if (callerFlags == 0) {
                AbstractTruffleException curExc = pythonThreadState.getCaughtException();
                AbstractTruffleException exceptionState = PArguments.getException(frame);
                if (exceptionState != curExc) {
                    // the thread state has exception info inconsistent with the current frame's. we
                    // need to force lower frames to walk the stack.
                    pythonThreadState.setCaughtException(null);
                }
                if (encapsulatingNodeToPush != null) {
                    return EncapsulatingNodeReference.getCurrent().set(encapsulatingNodeToPush);
                } else {
                    return defaultReturn;
                }
            }

            return enterSlowPath(frame, callData, encapsulatingNodeToPush, pythonThreadState, callerFlags, defaultReturn);
        }

        private static Object enterSlowPath(VirtualFrame frame, IndirectCallDataBase callData, Node encapsulatingNodeToPush,
                        PythonThreadState pythonThreadState, int callerFlags, Object defaultReturn) {
            PFrame.Reference info = null;
            if (CallerFlags.needsFrameReference(callerFlags)) {
                PFrame.Reference prev = pythonThreadState.popTopFrameInfo();
                assert prev == null : "trying to call from Python to a foreign function, but we didn't clear the topframeref. " +
                                "This indicates that a call into Python code happened without a proper enter through IndirectCalleeContext";
                info = PArguments.getCurrentFrameInfo(frame);
                pythonThreadState.setTopFrameInfo(info);
                if (CallerFlags.needsPFrame(callerFlags)) {
                    callData.getMaterializeFrameNode().executeOnStack(false, CallerFlags.needsLocals(callerFlags), frame);
                } else if (info.getPyFrame() != null) {
                    info.getPyFrame().setLastCallerFlags(callerFlags);
                }
            }
            AbstractTruffleException curExc = pythonThreadState.getCaughtException();
            AbstractTruffleException exceptionState = PArguments.getException(frame);
            if (CallerFlags.needsExceptionState(callerFlags)) {
                pythonThreadState.setCaughtException(exceptionState);
            } else if (exceptionState != curExc) {
                // the thread state has exception info inconsistent with the current frame's. we
                // need to force lower frames to walk the stack
                pythonThreadState.setCaughtException(null);
            }

            Node prevEncapsulatingNode = null;
            if (encapsulatingNodeToPush != null) {
                prevEncapsulatingNode = EncapsulatingNodeReference.getCurrent().set(encapsulatingNodeToPush);
            }
            if (HostCompilerDirectives.inInterpreterFastPath() && curExc == null && info == null) {
                if (encapsulatingNodeToPush != null) {
                    return prevEncapsulatingNode;
                } else {
                    return defaultReturn;
                }
            } else {
                return new IndirectCallState(info, curExc, prevEncapsulatingNode);
            }
        }
    }

    @ValueType
    private static final class IndirectCalleeState {
        private final PFrame.Reference info;
        private final AbstractTruffleException curExc;

        private IndirectCalleeState(PFrame.Reference info, AbstractTruffleException curExc) {
            this.info = info;
            this.curExc = curExc;
        }
    }

    /**
     * Prepares a call from a caller without access to the current {@link VirtualFrame}, e.g.,
     * {@code TruffleBoundary} code, to a Python callable. There should have been a call to one of
     * the {@code enter} helper methods from {@link IndirectCallContext} before the transition from
     * the code with {@link VirtualFrame} to the code without access to the frame.
     * <p>
     * The helper methods in this class will save and restore the exception state and frame
     * reference to/from the thread state, and pass down the exception state to the callee if it
     * asks for it using {@link PRootNode#getCallerFlags()}.
     */
    public abstract static class IndirectCalleeContext {

        /**
         * @see #enter(PythonThreadState, Object[])
         */
        public static Object enter(PythonLanguage language, PythonContext context, Object[] pArguments) {
            return enter(context.getThreadState(language), pArguments);
        }

        /**
         * Prepare a call from a foreign frame to a Python function.
         */
        public static Object enter(PythonThreadState threadState, Object[] pArguments) {
            // We decided on if and how to materialize PFrame.Reference/PFrame itself at the point
            // of transition from code with access to virtual frame to the code without access to it
            // (e.g., TruffleBoundary code) in IndirectCallContext
            Reference popTopFrameInfo = threadState.popTopFrameInfo();
            PArguments.setCallerFrameInfo(pArguments, popTopFrameInfo);

            // If someone set the exception in the arguments explicitly, we do not override it. This
            // is used in top level code, async handlers, etc., where we want to avoid pointless
            // stack-walking
            if (PArguments.getException(pArguments) == null) {
                AbstractTruffleException curExc = threadState.getCaughtException();
                if (curExc != null) {
                    threadState.setCaughtException(null);
                }
                PArguments.setException(pArguments, curExc);
                return new IndirectCalleeState(popTopFrameInfo, curExc);
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
            if (state instanceof IndirectCalleeState indirectCallState) {
                threadState.setTopFrameInfo(indirectCallState.info);
                threadState.setCaughtException(indirectCallState.curExc);
            } else {
                threadState.setTopFrameInfo((Reference) state);
            }
        }
    }
}
