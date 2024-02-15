/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.traceback;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_FRAME;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_LASTI;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_LINENO;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_NEXT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTraceback)
public final class TracebackBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TracebackBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization
        static Object dir(@SuppressWarnings("unused") PTraceback self,
                        @Cached PythonObjectFactory factory) {
            return factory.createList(PTraceback.getTbFieldNames());
        }
    }

    /**
     * Use the Truffle stacktrace attached to an exception to populate the information in the
     * {@link PTraceback} and its tb_next chain as far as the stacktrace goes for this segment.
     *
     * @see MaterializeLazyTracebackNode
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class MaterializeTruffleStacktraceNode extends Node {
        public abstract void execute(Node inliningTarget, PTraceback tb);

        @Specialization(guards = "tb.isMaterialized()")
        static void doExisting(@SuppressWarnings("unused") PTraceback tb) {
        }

        @TruffleBoundary
        @Specialization(guards = "!tb.isMaterialized()")
        static void doMaterialize(Node inliningTarget, PTraceback tb,
                        @Cached(inline = false) MaterializeFrameNode materializeFrameNode,
                        @Cached MaterializeLazyTracebackNode materializeLazyTracebackNode,
                        @Cached(inline = false) PythonObjectFactory factory) {
            /*
             * Truffle stacktrace consists of the frames captured during the unwinding and the
             * frames that are now on the Java stack. We don't want the frames from the stack to
             * creep in. They would be incorrect because we are no longer in the location where the
             * exception was caught, and unwanted because Python doesn't include frames from the
             * active stack in the traceback. Truffle doesn't tell us where the boundary between
             * exception frames and frames from the Java stack is, so we cut it off based on our
             * counting we did in the bytecode root node.
             */
            PTraceback next = null;
            LazyTraceback lazyTraceback = tb.getLazyTraceback();
            if (lazyTraceback.getNextChain() != null) {
                next = materializeLazyTracebackNode.execute(inliningTarget, lazyTraceback.getNextChain());
            }
            /*
             * The logic of skipping and cutting off frames here and in MaterializeLazyTracebackNode
             * must be the same.
             */
            PException pException = lazyTraceback.getException();
            List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(pException);
            if (stackTrace != null) {
                for (int truffleIndex = pException.getTracebackStartIndex(), pyIndex = 0; truffleIndex < stackTrace.size() && pyIndex < pException.getTracebackFrameCount(); truffleIndex++) {
                    TruffleStackTraceElement element = stackTrace.get(truffleIndex);
                    if (LazyTraceback.elementWantedForTraceback(element)) {
                        PFrame pFrame = materializeFrame(element, materializeFrameNode);
                        next = factory.createTraceback(pFrame, pFrame.getLine(), next);
                        next.setLocation(pFrame.getBci(), pFrame.getBytecodeNode());
                        pyIndex++;
                    }
                }
            }
            if (lazyTraceback.catchingFrameWantedForTraceback()) {
                tb.setLocation(pException.getCatchBci(), pException.getBytecodeNode());
                tb.setLineno(pException.getCatchLine());
                tb.setNext(next);
            } else {
                assert next != null;
                tb.copyFrom(next);
            }
            tb.markMaterialized(); // Marks the Truffle stacktrace part as materialized
        }

        private static PFrame materializeFrame(TruffleStackTraceElement element, MaterializeFrameNode materializeFrameNode) {
            Node location = element.getLocation();
            RootNode rootNode = element.getTarget().getRootNode();
            if (rootNode instanceof PBytecodeRootNode || rootNode instanceof PBytecodeGeneratorRootNode) {
                location = rootNode;
            }
            // create the PFrame and refresh frame values
            return materializeFrameNode.execute(location, false, true, element.getFrame());
        }
    }

    @Builtin(name = J_TB_FRAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackFrameNode extends PythonBuiltinNode {
        public abstract PFrame execute(VirtualFrame frame, Object traceback);

        @NeverDefault
        public static GetTracebackFrameNode create() {
            return TracebackBuiltinsFactory.GetTracebackFrameNodeFactory.create(null);
        }

        @Specialization(guards = "hasPFrame(tb)")
        static PFrame getExisting(PTraceback tb) {
            return tb.getFrame();
        }

        // case 1: not on stack: there is already a PFrame (so the frame of this frame info is
        // no
        // longer on the stack) and the frame has already been materialized
        @Specialization(guards = {"!hasPFrame(tb)", "hasFrameInfo(tb)", "isMaterialized(tb.getFrameInfo())", "hasVisibleFrame(tb)"})
        static PFrame doMaterializedFrame(PTraceback tb) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";
            PFrame escapedFrame = frameInfo.getPyFrame();
            assert escapedFrame != null;

            tb.setFrame(escapedFrame);
            return escapedFrame;
        }

        // case 2: on stack: the PFrame is not yet available so the frame must still be on the
        // stack
        @Specialization(guards = {"!hasPFrame(tb)", "hasFrameInfo(tb)", "!isMaterialized(tb.getFrameInfo())", "hasVisibleFrame(tb)"})
        static PFrame doOnStack(VirtualFrame frame, PTraceback tb,
                        @Bind("this") Node inliningTarget,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ReadCallerFrameNode readCallerFrame,
                        @Cached InlinedConditionProfile isCurFrameProfile) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";

            PFrame escapedFrame;

            // case 2.1: the frame info refers to the current frame
            if (isCurFrameProfile.profile(inliningTarget, PArguments.getCurrentFrameInfo(frame) == frameInfo)) {
                // materialize the current frame; marking is not necessary (already done);
                // refreshing
                // values is also not necessary (will be done on access to the locals or when
                // returning
                // from the frame)
                escapedFrame = materializeNode.execute(frame, false);
            } else {
                // case 2.2: the frame info does not refer to the current frame
                for (int i = 0;; i++) {
                    escapedFrame = readCallerFrame.executeWith(frame, i);
                    if (escapedFrame == null || escapedFrame.getRef() == frameInfo) {
                        break;
                    }
                }
            }

            assert escapedFrame != null : "Failed to find escaped frame on stack";
            tb.setFrame(escapedFrame);
            return escapedFrame;
        }

        // case 3: there is no PFrame[Ref], we need to take the top frame from the Truffle
        // stacktrace instead
        @Specialization(guards = "!hasVisibleFrame(tb)")
        static PFrame doFromTruffle(PTraceback tb,
                        @Bind("this") Node inliningTarget,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(inliningTarget, tb);
            return tb.getFrame();
        }

        protected static boolean hasPFrame(PTraceback tb) {
            return tb.getFrame() != null;
        }

        protected static boolean hasFrameInfo(PTraceback tb) {
            return tb.getFrameInfo() != null;
        }

        protected static boolean hasVisibleFrame(PTraceback tb) {
            return tb.getLazyTraceback() == null || tb.getLazyTraceback().catchingFrameWantedForTraceback();
        }

        protected static boolean isMaterialized(PFrame.Reference frameInfo) {
            return frameInfo.getPyFrame() != null;
        }
    }

    @Builtin(name = J_TB_NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackNextNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PTraceback self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(inliningTarget, self);
            return (self.getNext() != null) ? self.getNext() : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(next)")
        static Object set(PTraceback self, PTraceback next,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Exclusive @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // Check for loops
            PTraceback tb = next;
            while (loopProfile.profile(inliningTarget, tb != null)) {
                if (tb == self) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.TRACEBACK_LOOP_DETECTED);
                }
                tb = tb.getNext();
            }
            // Realize whatever was in the truffle stacktrace, so that we don't overwrite the
            // user-set next later
            materializeTruffleStacktraceNode.execute(inliningTarget, self);
            self.setNext(next);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(next)")
        static Object clear(PTraceback self, @SuppressWarnings("unused") PNone next,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            // Realize whatever was in the truffle stacktrace, so that we don't overwrite the
            // user-set next later
            materializeTruffleStacktraceNode.execute(inliningTarget, self);
            self.setNext(null);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPNone(next)", "!isPTraceback(next)"})
        static Object setError(@SuppressWarnings("unused") PTraceback self, Object next,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_TRACEBACK_OBJ, next);
        }
    }

    @Builtin(name = J_TB_LASTI, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLastINode extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PTraceback self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTracebackFrameNode getTracebackFrameNode,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(inliningTarget, self);
            PFrame pFrame = getTracebackFrameNode.execute(frame, self);
            return self.getLasti(pFrame);
        }
    }

    @Builtin(name = J_TB_LINENO, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLinenoNode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self,
                        @Bind("this") Node inliningTarget,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(inliningTarget, self);
            return self.getLineno();
        }
    }
}
