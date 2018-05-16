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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@CoreFunctions(extendClasses = PFrame.class)
public final class FrameBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return FrameBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__truffle_getargument__", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetArgumentNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public Object clear(PFrame self, int idx) {
            Frame frame = self.getFrame();
            if (frame == null) {
                return PNone.NONE;
            }
            Object[] arguments = frame.getArguments();
            if (arguments.length > idx + PArguments.USER_ARGUMENTS_OFFSET) {
                return arguments[idx + PArguments.USER_ARGUMENTS_OFFSET];
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "__truffle_get_class_scope__", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class TruffleGetClassScopeNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object add(PFrame self) {
            // TODO: remove me
            // TODO: do it properly via the python API in super.__init__ :
            // sys._getframe(1).f_code.co_closure?
            FrameSlot classSlot = self.getFrame().getFrameDescriptor().findFrameSlot(__CLASS__);
            try {
                Object classLocal = self.getFrame().getObject(classSlot);
                if (classLocal instanceof PCell) {
                    return ((PCell) classLocal).getPythonRef();
                }
                return classLocal;
            } catch (FrameSlotTypeException e) {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "f_globals", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Specialization
        Object get(PFrame self) {
            Frame frame = self.getFrame();
            if (frame != null) {
                PythonObject globals = PArguments.getGlobals(frame);
                if (globals instanceof PythonModule) {
                    return factory().createDictFixedStorage(globals);
                } else {
                    return globals;
                }
            }
            return factory().createDict();
        }
    }

    @Builtin(name = "f_builtins", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBuiltinsNode extends PythonBuiltinNode {
        @Specialization
        Object get(@SuppressWarnings("unused") PFrame self) {
            return factory().createDictFixedStorage(getContext().getBuiltins());
        }
    }

    @Builtin(name = "f_lineno", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonBuiltinNode {
        @Specialization
        int get(PFrame self) {
            return self.getLine();
        }
    }

    @Builtin(name = "f_lasti", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLastiNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") PFrame self) {
            return -1;
        }
    }

    @Builtin(name = "f_trace", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTraceNode extends PythonBuiltinNode {
        @Specialization
        Object get(@SuppressWarnings("unused") PFrame self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "f_code", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        Object get(PFrame self) {
            Node callNode = self.getCallNode();
            if (callNode == null) {
                return PNone.NONE;
            }
            RootNode rootNode = callNode.getRootNode();
            if (rootNode == null) {
                return PNone.NONE;
            } else {
                return new PythonParseResult(rootNode, getCore());
            }
        }
    }

    @Builtin(name = "f_locals", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLocalsNode extends PythonBuiltinNode {
        @Specialization
        PDict get(PFrame self) {
            return self.getLocals(factory());
        }
    }

    @Builtin(name = "f_back", fixedNumOfArguments = 1, isGetter = true)
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

    @Builtin(name = "clear", fixedNumOfArguments = 1)
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
