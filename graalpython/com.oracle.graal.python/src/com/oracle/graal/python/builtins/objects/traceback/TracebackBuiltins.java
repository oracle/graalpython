/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.TB_FRAME;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.TB_LASTI;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.TB_LINENO;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.TB_NEXT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback.LazyTracebackStorage;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback.MaterializedTracebackStorage;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTraceback)
public final class TracebackBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TracebackBuiltinsFactory.getFactories();
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization
        public Object dir(@SuppressWarnings("unused") PTraceback self) {
            return factory().createList(PTraceback.getTbFieldNames());
        }
    }

    public abstract static class GetPFrameNode extends Node {

        public abstract PFrame execute(VirtualFrame frame, LazyTracebackStorage tb);

        @Specialization(guards = "hasPFrame(tb)")
        PFrame doExisting(LazyTracebackStorage tb) {
            return tb.getFrame();
        }

        // case 1: not on stack: there is already a PFrame (so the frame of this frame info is
        // no
        // longer on the stack) and the frame has already been materialized
        @Specialization(guards = {"!hasPFrame(tb)", "isMaterialized(tb.getFrameInfo())"})
        PFrame doMaterializedFrame(LazyTracebackStorage tb) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";
            PFrame escapedFrame = frameInfo.getPyFrame();
            assert escapedFrame != null;
            return escapedFrame;
        }

        // case 2: on stack: the PFrame is not yet available so the frame must still be on the
        // stack
        @Specialization(guards = {"!hasPFrame(tb)", "!isMaterialized(tb.getFrameInfo())"})
        PFrame doOnStack(VirtualFrame frame, LazyTracebackStorage tb,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ReadCallerFrameNode readCallerFrame,
                        @Cached("createBinaryProfile()") ConditionProfile isCurFrameProfile) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";

            PFrame escapedFrame = null;

            // case 2.1: the frame info refers to the current frame
            if (isCurFrameProfile.profile(PArguments.getCurrentFrameInfo(frame) == frameInfo)) {
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

            return escapedFrame;
        }

        protected static boolean hasPFrame(LazyTracebackStorage tb) {
            return tb.getFrame() != null;
        }

        protected static boolean isMaterialized(PFrame.Reference frameInfo) {
            return frameInfo.getPyFrame() != null;
        }
    }

    public abstract static class GetMaterializedTracebackNode extends Node {
        public abstract MaterializedTracebackStorage execute(VirtualFrame frame, PTraceback tb);

        protected static boolean isMaterialized(PTraceback tb) {
            return tb.getTracebackStorage() instanceof MaterializedTracebackStorage;
        }

        @Specialization(guards = "isMaterialized(tb)")
        MaterializedTracebackStorage getMaterialized(PTraceback tb) {
            return (MaterializedTracebackStorage) tb.getTracebackStorage();
        }

        @Specialization(guards = "!isMaterialized(tb)")
        MaterializedTracebackStorage getLazy(VirtualFrame frame, PTraceback tb,
                        @Cached GetPFrameNode getPFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached PythonObjectFactory factory) {
            LazyTracebackStorage storage = (LazyTracebackStorage) tb.getTracebackStorage();
            PFrame pFrame = getPFrameNode.execute(frame, storage);
            MaterializedTracebackStorage materializedStorage = createTracebackFromTruffle(materializeNode, factory, storage, pFrame);
            tb.setStorage(materializedStorage);
            return materializedStorage;
        }

        @TruffleBoundary
        private static MaterializedTracebackStorage createTracebackFromTruffle(MaterializeFrameNode materializeNode, PythonObjectFactory factory, LazyTracebackStorage storage, PFrame pFrame) {
            int lineno = -2;
            PTraceback prev = null;
            CallTarget currentCallTarget = Truffle.getRuntime().getCurrentFrame().getCallTarget();
            for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(storage.getException())) {
                if (element.getTarget() == currentCallTarget) {
                    SourceSection sourceSection = element.getLocation().getEncapsulatingSourceSection();
                    if (sourceSection != null) {
                        lineno = sourceSection.getStartLine();
                    }
                    break;
                }
                prev = truffleStackTraceElementToPTraceback(materializeNode, factory, prev, element);
            }
            return new MaterializedTracebackStorage(pFrame, lineno, prev);
        }

        public static PTraceback truffleStackTraceElementToPTraceback(MaterializeFrameNode materializeNode, PythonObjectFactory factory, PTraceback prevTraceback, TruffleStackTraceElement element) {
            Frame tracebackFrame = element.getFrame();
            // frames may have not been requested
            if (tracebackFrame != null) {
                Node location = element.getLocation();
                // only include frames of non-builtin python functions
                if (PArguments.isPythonFrame(tracebackFrame) && location != null && !location.getRootNode().isInternal()) {
                    // create the PFrame and refresh frame values
                    PFrame escapedFrame = materializeNode.execute(null, location, false, true, tracebackFrame);
                    prevTraceback = factory.createTraceback(escapedFrame, prevTraceback);
                }
            }
            return prevTraceback;
        }
    }

    @Builtin(name = TB_FRAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackFrameNode extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PTraceback self,
                        @Cached GetMaterializedTracebackNode getMaterializedTracebackNode) {
            return getMaterializedTracebackNode.execute(frame, self).getFrame();
        }
    }

    @Builtin(name = TB_NEXT, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackNextNode extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PTraceback self,
                        @Cached GetMaterializedTracebackNode getMaterializedTracebackNode) {
            PTraceback next = getMaterializedTracebackNode.execute(frame, self).getNext();
            return (next != null) ? next : PNone.NONE;
        }
    }

    @Builtin(name = TB_LASTI, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLastINode extends PythonBuiltinNode {
        @Specialization
        Object get(@SuppressWarnings("unused") PTraceback self) {
            return -1;
        }
    }

    @Builtin(name = TB_LINENO, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLinenoNode extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PTraceback self,
                        @Cached GetMaterializedTracebackNode getMaterializedTracebackNode) {
            return getMaterializedTracebackNode.execute(frame, self).getLineno();
        }
    }
}
