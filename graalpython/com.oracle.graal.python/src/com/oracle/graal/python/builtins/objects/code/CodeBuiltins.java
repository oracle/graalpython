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

package com.oracle.graal.python.builtins.objects.code;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(extendClasses = PythonParseResult.class)
public class CodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return CodeBuiltinsFactory.getFactories();
    }

    @Builtin(name = "co_freevars", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FreeVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PythonParseResult self) {
            RootNode rootNode = self.getRootNode();
            if (rootNode instanceof FunctionRootNode) {
                return factory().createTuple(((FunctionRootNode) rootNode).getFreeVars());
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "co_cellvars", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CellVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PythonParseResult self) {
            RootNode rootNode = self.getRootNode();
            if (rootNode instanceof FunctionRootNode) {
                return factory().createTuple(((FunctionRootNode) rootNode).getCellVars());
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "co_filename", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FilenameNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PythonParseResult self) {
            RootNode rootNode = self.getRootNode();
            SourceSection src = rootNode.getSourceSection();
            if (src != null) {
                return src.getSource().getName();
            } else if (rootNode instanceof ModuleRootNode) {
                return ((ModuleRootNode) rootNode).getName();
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "co_firstlineno", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class LinenoNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object doIt(PythonParseResult self) {
            RootNode rootNode = self.getRootNode();
            return rootNode.getSourceSection().getStartLine();
        }
    }

    @Builtin(name = "co_name", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object doIt(PythonParseResult self) {
            RootNode rootNode = self.getRootNode();
            String name;
            if (rootNode instanceof ModuleRootNode) {
                name = "<module>";
            } else if (rootNode instanceof FunctionRootNode) {
                name = ((FunctionRootNode) rootNode).getFunctionName();
            } else {
                name = rootNode.getName();
            }
            if (name != null) {
                return name;
            }
            return PNone.NONE;
        }
    }
}
