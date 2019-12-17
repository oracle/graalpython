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

package com.oracle.graal.python.builtins.objects.code;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.GlobalsNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.ContainsKeyNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCode)
public class CodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodeBuiltinsFactory.getFactories();
    }

    @Builtin(name = "co_freevars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFreeVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] freeVars = self.getFreeVars();
            if (freeVars != null) {
                return factory().createTuple(freeVars);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_cellvars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCellVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] cellVars = self.getCellVars();
            if (cellVars != null) {
                return factory().createTuple(cellVars);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_filename", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFilenameNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            String filename = self.getFilename();
            if (filename != null) {
                return filename;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_firstlineno", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getFirstLineNo();
        }
    }

    @Builtin(name = "co_name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object get(PCode self) {
            String name = self.getName();
            if (name != null) {
                return name;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_argcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetArgCountNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getArgcount();
        }
    }

    @Builtin(name = "co_posonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetPosOnlyArgCountNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getPositionalOnlyArgCount();
        }
    }

    @Builtin(name = "co_kwonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetKnownlyArgCountNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getKwonlyargcount();
        }
    }

    @Builtin(name = "co_nlocals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNLocalsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getNlocals();
        }
    }

    @Builtin(name = "co_stacksize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStackSizeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getStacksize();
        }
    }

    @Builtin(name = "co_flags", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFlagsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getFlags();
        }
    }

    @Builtin(name = "co_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            byte[] codestring = self.getCodestring();
            if (codestring == null) {
                codestring = new byte[0];
            }
            return factory().createBytes(codestring);
        }
    }

    @Builtin(name = "co_consts", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetConstsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] constants = self.getConstants();
            if (constants == null) {
                // TODO: this is for the moment undefined (see co_code)
                constants = new Object[0];
            }
            return factory().createTuple(constants);
        }
    }

    @Builtin(name = "co_names", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNamesNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] names = self.getNames();
            if (names == null) {
                // TODO: this is for the moment undefined (see co_code)
                names = new Object[0];
            }
            return factory().createTuple(names);
        }
    }

    @Builtin(name = "co_varnames", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetVarNamesNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] varNames = self.getVarnames();
            if (varNames != null) {
                return factory().createTuple(varNames);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "truffle_co_globals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalVarNamesNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(VirtualFrame frame, PCode self,
                        @Cached GlobalsNode globalsNode,
                        @Cached ContainsKeyNode containsKeyNode) {
            Object[] varNames = self.getGlobalAndBuiltinVarNames();
            if (varNames != null) {
                PDict globals = (PDict) globalsNode.execute(frame);
                ArrayList<Object> vars = new ArrayList<>();
                for (Object name : varNames) {
                    if (containsKeyNode.execute(frame, globals.getDictStorage(), name)) {
                        vars.add(name);
                    }
                }
                return factory().createTuple(vars.toArray());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_lnotab", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLNoTabNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            byte[] lnotab = self.getLnotab();
            if (lnotab == null) {
                // TODO: this is for the moment undefined (see co_code)
                lnotab = new byte[0];
            }
            return factory().createBytes(lnotab);
        }
    }
}
