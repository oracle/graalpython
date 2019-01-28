/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltinsFactory.GetLocalsNodeFactory;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.DictNode;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFrame)
public final class FrameBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrameBuiltinsFactory.getFactories();
    }

    @Builtin(name = "f_globals", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Child DictNode getDictNode;

        @Specialization
        Object get(PFrame self) {
            Frame frame = self.getFrame();
            if (frame != null) {
                PythonObject globals = PArguments.getGlobals(frame);
                if (globals instanceof PythonModule) {
                    if (getDictNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getDictNode = insert(DictNodeFactory.create());
                    }
                    return getDictNode.execute(globals, PNone.NO_VALUE);
                } else {
                    return globals;
                }
            }
            return factory().createDict();
        }
    }

    @Builtin(name = "f_builtins", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBuiltinsNode extends PythonBuiltinNode {
        @Child DictNode dictNode = DictNodeFactory.create();

        @Specialization
        Object get(@SuppressWarnings("unused") PFrame self) {
            return dictNode.execute(getContext().getBuiltins(), PNone.NO_VALUE);
        }
    }

    @Builtin(name = "f_lineno", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonBuiltinNode {
        @Specialization
        int get(PFrame self) {
            return self.getLine();
        }
    }

    @Builtin(name = "f_lasti", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLastiNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") PFrame self) {
            return -1;
        }
    }

    @Builtin(name = "f_trace", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTraceNode extends PythonBuiltinNode {
        @Specialization
        Object get(PFrame self) {
            PTraceback traceback = self.getException().getTraceback(factory(), self.getIndex());
            if (traceback == null) {
                return PNone.NONE;
            }
            return traceback;
        }
    }

    @Builtin(name = "f_code", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        Object get(PFrame self) {
            RootNode rootNode = self.getTarget();
            if (rootNode != null) {
                return factory().createCode(rootNode);
            }
            return factory().createCode(PythonBuiltinClassType.PCode, -1, -1, -1, -1, -1, new byte[0], new Object[0], new Object[0], new Object[0], new Object[0], new Object[0], "<internal>",
                            "<internal>", -1, new byte[0]);
        }
    }

    @Builtin(name = "f_locals", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLocalsNode extends PythonUnaryBuiltinNode {
        @Child SetItemNode setItemNode;

        @Specialization
        Object get(PFrame self) {
            Frame frame = self.getFrame();
            Object locals = self.getLocals(factory());
            if (!self.inClassScope()) {
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(SetItemNode.create());
                }
                PDict currentDictLocals = factory().createDictLocals(frame, false);
                for (DictEntry entry : currentDictLocals.entries()) {
                    setItemNode.executeWith(locals, entry.getKey(), entry.getValue());
                }
            }
            return locals;
        }

        @Specialization
        Object getFromFrame(Frame owner,
                        @Cached("createBinaryProfile()") ConditionProfile noPFrame,
                        @Cached("create()") BranchProfile noFrameOnPFrame) {
            PFrame pFrame = PArguments.getPFrame(owner);
            if (noPFrame.profile(pFrame == null)) {
                pFrame = factory().createPFrame(owner);
                PArguments.setPFrame(owner, pFrame);
            } else if (!pFrame.hasFrame()) {
                noFrameOnPFrame.enter();
                pFrame = factory().createPFrame(owner, pFrame.getLocalsDict());
                PArguments.setPFrame(owner, pFrame);
            }
            return get(pFrame);
        }

        public static GetLocalsNode create() {
            return GetLocalsNodeFactory.create();
        }
    }

    @Builtin(name = "f_back", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBackrefNode extends PythonBuiltinNode {
        @Specialization
        Object get(PFrame self) {
            PTraceback traceback = self.getException().getTraceback(factory(), self.getIndex() + 1);
            if (traceback == null) {
                return PNone.NONE;
            }
            return traceback.getPFrame(factory());
        }
    }

    @Builtin(name = "clear", fixedNumOfPositionalArgs = 1)
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
