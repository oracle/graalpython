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
package com.oracle.graal.python.builtins.objects.frame;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.DictNode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeFactory;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadLocalsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFrame)
public final class FrameBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrameBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        String repr(VirtualFrame frame, PFrame self,
                        @Cached GetCodeNode getCodeNode,
                        @Cached GetLinenoNode getLinenoNode) {
            PCode code = getCodeNode.executeObject(frame, self);
            int lineno = getLinenoNode.executeInt(frame, self);
            return getFormat(self, code, lineno);
        }

        @TruffleBoundary
        private static String getFormat(PFrame self, PCode code, int lineno) {
            return String.format("<frame at 0x%x, file '%s', line %d, code %s>",
                            self.hashCode(), code.getFilename(), lineno, code.getName());
        }
    }

    @Builtin(name = "f_globals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Child private DictNode getDictNode;

        @Specialization
        Object get(VirtualFrame curFrame, PFrame self) {
            if (self.isAssociated()) {
                PythonObject globals = self.getGlobals();
                if (globals instanceof PythonModule) {
                    if (getDictNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getDictNode = insert(DictNodeFactory.create());
                    }
                    return getDictNode.execute(curFrame, globals, PNone.NO_VALUE);
                } else {
                    return globals != null ? globals : factory().createDict();
                }
            }
            return factory().createDict();
        }
    }

    @Builtin(name = "f_builtins", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBuiltinsNode extends PythonBuiltinNode {
        @Child private DictNode dictNode = DictNodeFactory.create();

        @Specialization
        Object get(VirtualFrame frame, @SuppressWarnings("unused") PFrame self) {
            // TODO: builtins can be set per frame
            return dictNode.execute(frame, getContext().getBuiltins(), PNone.NO_VALUE);
        }
    }

    @Builtin(name = "f_lineno", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonBuiltinNode {
        public abstract int executeInt(VirtualFrame frame, PFrame self);

        @Specialization
        int get(VirtualFrame frame, PFrame self,
                        @Cached ConditionProfile isCurrentFrameProfile,
                        @Cached MaterializeFrameNode materializeNode) {
            // Special case because this builtin can be called without going through an invoke node:
            // we need to sync the location of the frame if and only if 'self' represents the
            // current frame. If 'self' represents another frame on the stack, the location is
            // already set
            if (isCurrentFrameProfile.profile(frame != null && PArguments.getCurrentFrameInfo(frame) == self.getRef())) {
                PFrame pyFrame = materializeNode.execute(frame, this, false, false);
                assert pyFrame == self;
            }
            return self.getLine();
        }

        public static GetLinenoNode create() {
            return FrameBuiltinsFactory.GetLinenoNodeFactory.create(new ReadArgumentNode[0]);
        }
    }

    @Builtin(name = "f_lasti", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLastiNode extends PythonBuiltinNode {
        @Specialization
        int get(PFrame self) {
            return self.getLasti();
        }
    }

    @Builtin(name = "f_trace", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTraceNode extends PythonBuiltinNode {
        @Specialization
        Object get(@SuppressWarnings("unused") PFrame self) {
            // TODO: frames: This must return the traceback if there is a
            // handled exception here
            return PNone.NONE;
        }
    }

    @Builtin(name = "f_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        public abstract PCode executeObject(VirtualFrame frame, PFrame self);

        @Specialization
        PCode get(VirtualFrame frame, PFrame self,
                        @Cached("create()") CodeNodes.CreateCodeNode createCodeNode) {
            RootCallTarget ct = self.getTarget();
            if (ct != null) {
                return factory().createCode(ct);
            }
            // TODO: frames: this just shouldn't happen anymore
            assert false : "should not be reached";
            return createCodeNode.execute(frame, PythonBuiltinClassType.PCode, -1, -1, -1, -1, -1, -1, new byte[0], new Object[0], new Object[0], new Object[0], new Object[0], new Object[0],
                            "<internal>",
                            "<internal>", -1, new byte[0]);
        }

        public static GetCodeNode create() {
            return FrameBuiltinsFactory.GetCodeNodeFactory.create(new ReadArgumentNode[0]);
        }
    }

    @Builtin(name = "f_locals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLocalsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getUpdating(VirtualFrame frame, PFrame self,
                        @Cached ReadLocalsNode readLocals,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached MaterializeFrameNode materializeNode) {
            assert self.isAssociated() : "It's impossible to call f_locals on a frame without that frame having escaped";
            // Special case because this builtin can be called without going through an invoke node:
            // we need to sync the values of the frame if and only if 'self' represents the current
            // frame. If 'self' represents another frame on the stack, the values are already
            // refreshed.
            if (profile.profile(frame != null && PArguments.getCurrentFrameInfo(frame) == self.getRef())) {
                PFrame pyFrame = materializeNode.execute(frame, false, true, frame);
                assert pyFrame == self;
            }
            return readLocals.execute(frame, self);
        }
    }

    @Builtin(name = "f_back", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBackrefNode extends PythonBuiltinNode {
        @Specialization
        Object getBackref(VirtualFrame frame, PFrame self,
                        @Cached BranchProfile noBackref,
                        @Cached BranchProfile topRef,
                        @Cached("createBinaryProfile()") ConditionProfile notMaterialized,
                        @Cached ReadCallerFrameNode readCallerFrame) {
            PFrame.Reference backref;
            for (PFrame cur = self;; cur = backref.getPyFrame()) {
                backref = cur.getBackref();
                if (backref == null) {
                    noBackref.enter();
                    // The backref is not there. There's three cases:

                    // a) self is still on the stack and the caller isn't filled in
                    // b) this frame has returned, but not (yet) to a Python caller
                    // c) this frame has no caller (it is/was a top frame)
                    PFrame callerFrame = readCallerFrame.executeWith(frame, cur.getRef(), false, 0);

                    // We don't need to mark the caller frame as 'escaped' because if 'self' is
                    // escaped, the caller frame will be escaped when leaving the current function.

                    if (callerFrame == null) {
                        topRef.enter();
                        // so we won't do this again
                        cur.setBackref(PFrame.Reference.EMPTY);
                        return PNone.NONE;
                    } else {
                        backref = callerFrame.getRef();
                        cur.setBackref(backref);
                    }
                }

                if (backref == Reference.EMPTY) {
                    return PNone.NONE;
                } else if (!PRootNode.isPythonInternal(backref.getCallNode().getRootNode())) {
                    PFrame fback = materialize(frame, readCallerFrame, backref, notMaterialized);
                    assert fback.getRef() == backref;
                    return fback;
                }

                assert backref.getPyFrame() != null;
            }
        }

        private static PFrame materialize(VirtualFrame frame, ReadCallerFrameNode readCallerFrameNode, PFrame.Reference backref, ConditionProfile notMaterialized) {
            if (notMaterialized.profile(backref.getPyFrame() == null)) {
                // Special case: the backref's PFrame object is not yet available; this is because
                // the frame is still on the stack. So we need to find and materialize it.
                for (int i = 0;; i++) {
                    PFrame caller = readCallerFrameNode.executeWith(frame, i);
                    if (caller == null) {
                        break;
                    } else if (caller.getRef() == backref) {
                        // now, the PFrame object is available since the readCallerFrameNode
                        // materialized it
                        assert backref.getPyFrame() != null;
                        return caller;
                    }
                }
                assert false : "could not find frame of backref on the stack";
            }
            return backref.getPyFrame();
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FrameClearNode extends PythonBuiltinNode {
        @Specialization
        Object clear(@SuppressWarnings("unused") PFrame self) {
            // TODO: implement me
            // see: https://github.com/python/cpython/blob/master/Objects/frameobject.c#L503
            return PNone.NONE;
        }
    }
}
