/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.code;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class PCode extends PythonBuiltinObject {
    private final RootNode rootNode;
    // number of arguments (not including keyword only arguments, * or ** args)
    private final int argcount;
    // number of keyword only arguments (not including ** arg)
    private final int kwonlyargcount;
    // number of local variables
    private final int nlocals;
    // is the required stack size (including local variables)
    private final int stacksize;
    // bitmap of CO_* flags, read more
    // (https://docs.python.org/3/library/inspect.html#inspect-module-co-flags)
    private final int flags;
    // is a string representing the sequence of bytecode instructions
    private final String codestring;
    // tuple of constants used in the bytecode
    private final Object constants;
    // tuple containing the literals used by the bytecode
    private final Object names;
    // is a tuple containing the names of the local variables (starting with the argument names)
    private final Object[] varnames;
    // name of file in which this code object was created
    private final String filename;
    // name with which this code object was defined
    private final String name;
    // number of first line in Python source code
    private final int firstlineno;
    // is a string encoding the mapping from bytecode offsets to line numbers
    private final Object lnotab;
    // tuple of names of free variables (referenced via a function’s closure)
    private final Object[] freevars;
    // tuple of names of cell variables (referenced by containing scopes)
    private final Object[] cellvars;

    @TruffleBoundary
    public PCode(PythonClass cls, RootNode rootNode, PythonCore core) {
        super(cls);
        this.rootNode = rootNode;
        // file stats
        this.filename = getFileName(this.rootNode);
        this.name = getName(this.rootNode);
        this.firstlineno = getFirstLineno(this.rootNode);
        // arg stats
        ArgStats argStats = getArgStats(this.rootNode, core);
        this.argcount = argStats.argCnt;
        this.kwonlyargcount = argStats.kwOnlyArgCnt;
        this.freevars = argStats.freeVars;
        this.cellvars = argStats.cellVars;
        this.varnames = argStats.varNames;
        this.nlocals = argStats.nLocals;

        this.stacksize = getStackSize(rootNode);
        this.flags = -1;
        this.codestring = null;
        this.constants = null;
        this.names = null;
        this.lnotab = null;
    }

    public PCode(PythonClass cls, int argcount, int kwonlyargcount, int nlocals, int stacksize,
                    int flags, String codestring, Object constants, Object names, Object[] varnames,
                    String filename, String name, int firstlineno, Object lnotab, Object[] freevars,
                    Object[] cellvars) {
        super(cls);
        this.rootNode = null;
        this.argcount = argcount;
        this.kwonlyargcount = kwonlyargcount;
        this.nlocals = nlocals;
        this.stacksize = stacksize;
        this.flags = flags;
        this.codestring = codestring;
        this.constants = constants;
        this.names = names;
        this.varnames = varnames;
        this.filename = filename;
        this.name = name;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;
        this.freevars = freevars;
        this.cellvars = cellvars;
    }

    private static Set<String> asSet(String[] values) {
        return (values != null) ? new HashSet<>(Arrays.asList(values)) : new HashSet<>();
    }

    private static String[] getFreeVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getFreeVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getFreeVars();
        } else {
            return null;
        }
    }

    private static String[] getCellVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getCellVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getCellVars();
        } else {
            return null;
        }
    }

    private static String getFileName(RootNode rootNode) {
        SourceSection src = rootNode.getSourceSection();
        if (src != null) {
            return src.getSource().getName();
        } else if (rootNode instanceof ModuleRootNode) {
            return rootNode.getName();
        } else {
            return null;
        }
    }

    private static int getFirstLineno(RootNode rootNode) {
        SourceSection sourceSection = rootNode.getSourceSection();
        return (sourceSection != null) ? sourceSection.getStartLine() : 1;
    }

    private static String getName(RootNode rootNode) {
        String name;
        if (rootNode instanceof ModuleRootNode) {
            name = "<module>";
        } else if (rootNode instanceof FunctionRootNode) {
            name = ((FunctionRootNode) rootNode).getFunctionName();
        } else {
            name = rootNode.getName();
        }
        return name;
    }

    private static Set<String> getKeywordArgumentNames(List<ReadKeywordNode> readKeywordNodes) {
        Set<String> kwArgNames = new HashSet<>();
        for (ReadKeywordNode node : readKeywordNodes) {
            kwArgNames.add(node.getName());
        }
        return kwArgNames;
    }

    private static Set<String> getArgumentNames(List<ReadIndexedArgumentNode> readIndexedArgumentNodes) {
        Set<String> argNames = new HashSet<>();
        for (ReadIndexedArgumentNode node : readIndexedArgumentNodes) {
            Node parent = node.getParent();
            if (parent instanceof WriteIdentifierNode) {
                Object identifier = ((WriteIdentifierNode) parent).getIdentifier();
                if (identifier instanceof String) {
                    argNames.add((String) identifier);
                }
            }
        }
        return argNames;
    }

    private final static class ArgStats {
        public final int argCnt;
        private final int kwOnlyArgCnt;
        private final Object[] varNames;
        private final Object[] freeVars;
        private final Object[] cellVars;
        private final int nLocals;

        private ArgStats(int argCnt, int kwOnlyArgCnt, Object[] varNames, Object[] freeVars, Object[] cellVars) {
            this.argCnt = argCnt;
            this.kwOnlyArgCnt = kwOnlyArgCnt;
            this.varNames = varNames;
            this.freeVars = freeVars;
            this.cellVars = cellVars;
            this.nLocals = varNames.length;
        }
    }

    private static ArgStats getArgStats(RootNode rootNode, PythonCore core) {
        String[] freeVars = getFreeVars(rootNode);
        String[] cellVars = getCellVars(rootNode);
        Set<String> freeVarsSet = asSet(freeVars);
        Set<String> cellVarsSet = asSet(cellVars);

        List<ReadKeywordNode> readKeywordNodes = NodeUtil.findAllNodeInstances(rootNode, ReadKeywordNode.class);
        List<ReadIndexedArgumentNode> readIndexedArgumentNodes = NodeUtil.findAllNodeInstances(rootNode, ReadIndexedArgumentNode.class);

        Set<String> kwNames = getKeywordArgumentNames(readKeywordNodes);
        Set<String> argNames = getArgumentNames(readIndexedArgumentNodes);

        Set<String> allArgNames = new HashSet<>();
        allArgNames.addAll(kwNames);
        allArgNames.addAll(argNames);

        int argC = readIndexedArgumentNodes.size();
        int kwOnlyArgC = 0;

        for (ReadKeywordNode kwNode : readKeywordNodes) {
            if (!kwNode.canBePositional()) {
                kwOnlyArgC++;
            }
        }

        Set<String> varNames = new HashSet<>();
        for (Object identifier : rootNode.getFrameDescriptor().getIdentifiers()) {
            if (identifier instanceof String) {
                String varName = (String) identifier;

                if (core.getParser().isIdentifier(core, varName)) {
                    if (allArgNames.contains(varName)) {
                        varNames.add(varName);
                    } else if (!freeVarsSet.contains(varName) && !cellVarsSet.contains(varName)) {
                        varNames.add(varName);
                    }
                }
            }
        }
        return new ArgStats(argC, kwOnlyArgC, varNames.toArray(), freeVars, cellVars);
    }

    private static int getStackSize(RootNode rootNode) {
        return rootNode.getFrameDescriptor().getSize();
    }

    public RootNode getRootNode() {
        return rootNode;
    }

    public Object[] getFreeVars() {
        return freevars;
    }

    public Object[] getCellVars() {
        return cellvars;
    }

    public String getFilename() {
        return filename;
    }

    public int getFirstLineNo() {
        return firstlineno;
    }

    public String getName() {
        return name;
    }

    public int getArgcount() {
        return argcount;
    }

    public int getKwonlyargcount() {
        return kwonlyargcount;
    }

    public int getNlocals() {
        return nlocals;
    }

    public int getStacksize() {
        return stacksize;
    }

    public int getFlags() {
        return flags;
    }

    public String getCodestring() {
        return codestring;
    }

    public Object getConstants() {
        return constants;
    }

    public Object getNames() {
        return names;
    }

    public Object[] getVarnames() {
        return varnames;
    }

    public Object getLnotab() {
        return lnotab;
    }
}
