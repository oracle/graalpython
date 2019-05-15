/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.DictNode;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadLocalsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFrame)
public final class FrameBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrameBuiltinsFactory.getFactories();
    }

    @Builtin(name = "f_globals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Child private DictNode getDictNode;

        @Specialization
        Object get(VirtualFrame curFrame, PFrame self) {
            Frame frame = self.getFrame();
            if (frame != null) {
                PythonObject globals = PArguments.getGlobals(frame);
                if (globals instanceof PythonModule) {
                    if (getDictNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getDictNode = insert(DictNodeFactory.create());
                    }
                    return getDictNode.execute(curFrame, globals, PNone.NO_VALUE);
                } else {
                    return globals != null ? globals : PNone.NONE;
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
        @Specialization
        int get(PFrame self) {
            return self.getLine();
        }
    }

    @Builtin(name = "f_lasti", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLastiNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") PFrame self) {
            return -1;
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
        @Specialization
        Object get(VirtualFrame frame, PFrame self,
                        @Cached("create()") CodeNodes.CreateCodeNode createCodeNode) {
            RootCallTarget ct = self.getTarget();
            if (ct != null) {
                return factory().createCode(ct);
            }
            // TODO: frames: this just shouldn't happen anymore
            assert false : "should not be reached";
            return createCodeNode.execute(frame, PythonBuiltinClassType.PCode, -1, -1, -1, -1, -1, new byte[0], new Object[0], new Object[0], new Object[0], new Object[0], new Object[0], "<internal>",
                            "<internal>", -1, new byte[0]);
        }
    }

    @Builtin(name = "f_locals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLocalsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getUpdating(VirtualFrame frame, PFrame self,
                        @Cached ReadLocalsNode readLocals) {
            Frame materializedFrame = self.getFrame();
            assert materializedFrame != null : "It's impossible to call f_locals on a frame without that frame having escaped";
            return readLocals.execute(frame, materializedFrame);
        }
    }

    @Builtin(name = "f_back", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBackrefNode extends PythonBuiltinNode {
        @Specialization
        Object getBackref(@SuppressWarnings("unused") VirtualFrame frame, PFrame self,
                        @Cached BranchProfile noBackref,
                        @Cached BranchProfile topRef,
                        @Cached MaterializeFrameNode materializeFrameNode,
                        @Cached ReadCallerFrameNode readCallerFrame) {
            PFrame.Reference backref = self.getBackref();
            if (backref == null) {
                noBackref.enter();
                // The backref is not there. There's three cases:

                // a) self is still on the stack and the caller isn't filled in
                // b) this frame has returned, but not (yet) to a Python caller
                // c) this frame has no caller (it is/was a top frame)
                Frame callerFrame = readCallerFrame.executeWith(self.getFrame(), 0);
                if (callerFrame == null) {
                    topRef.enter();
                    // so we won't do this again
                    self.setBackref(PFrame.Reference.EMPTY);
                    return PNone.NONE;
                } else {
                    // we need it now, so materialize it
                    PFrame callerFrameObject = materializeFrameNode.execute(callerFrame);
                    backref = callerFrameObject.getRef();
                    self.setBackref(backref);
                }
            }
            if (backref.getPyFrame() == null) {
                return PNone.NONE;
            } else {
                return backref.getPyFrame();
            }
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
