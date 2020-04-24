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
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

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

    @Builtin(name = TB_FRAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackFrameNode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self) {
            return self.getPFrame();
        }
    }

    @Builtin(name = TB_NEXT, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackNextNode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PTraceback tb = self.getNext();
            if (profile.profile(tb == null)) {
                self.setNext(createTracebackChain(self.getException(), materializeNode, factory()));
                tb = self.getNext();
            }
            assert tb != null;
            // do never expose 'NO_TRACEBACK'; it's just a marker to avoid re-evaluation
            return tb == PTraceback.NO_TRACEBACK ? PNone.NONE : tb;
        }

        @TruffleBoundary
        public static PTraceback createTracebackChain(PException exception, MaterializeFrameNode materializeNode, PythonObjectFactory factory) {
            // recover the traceback from Truffle stack trace
            PTraceback prev = PTraceback.NO_TRACEBACK;
            PTraceback cur = null;
            for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(exception)) {

                Frame frame = element.getFrame();
                // frames may have not been requested
                if (frame != null) {
                    Node location = element.getLocation();
                    // only include frames of non-builtin python functions
                    if (PArguments.isPythonFrame(frame) && location != null && location.getRootNode() != null && !location.getRootNode().isInternal()) {
                        // create the PFrame and refresh frame values
                        PFrame escapedFrame = materializeNode.execute(null, location, false, true, frame);
                        cur = factory.createTraceback(escapedFrame, exception);
                        cur.setNext(prev);
                        prev = cur;
                    }
                }
            }
            return cur == null ? PTraceback.NO_TRACEBACK : cur;
        }
    }

    @Builtin(name = TB_LASTI, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLastINode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self) {
            return self.getLasti();
        }
    }

    @Builtin(name = TB_LINENO, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLinenoNode extends PythonBuiltinNode {
        @Specialization
        int get(PTraceback self) {
            return self.getLineno();
        }
    }
}
