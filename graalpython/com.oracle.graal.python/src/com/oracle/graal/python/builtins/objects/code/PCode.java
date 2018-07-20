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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class PCode extends PythonBuiltinObject {
    private final RootNode result;
    private final int argcount;
    private final int kwonlyargcount;
    private final int nlocals;
    private final int stacksize;
    private final int flags;
    private final String codestring;
    private final Object constants;
    private final Object names;
    private final Object[] varnames;
    private final String filename;
    private final String name;
    private final int firstlineno;
    private final Object lnotab;
    private final Object[] freevars;
    private final Object[] cellvars;

    @TruffleBoundary
    public PCode(PythonClass cls, RootNode result, PythonCore core) {
        super(cls);
        this.result = result;
        // file stats
        this.filename = getFileName(this.result);
        this.name = getName(this.result);
        this.firstlineno = getFirstLineno(this.result);
        // arg stats
        ArgStats argStats = getArgStats(this.result);
        this.argcount = argStats.argCnt;
        this.kwonlyargcount = argStats.kwOnlyArgCnt;
        // var stats
        String[] freevars = getFreeVars(this.result);
        String[] cellvars = getCellVars(this.result);
        Set<String> freeVarsSet = asSet(freevars);
        Set<String> cellVarsSet = asSet(cellvars);
        ArrayList<String> varNames = getVarNames(this.result, freeVarsSet, cellVarsSet, core);

        this.freevars = freevars;
        this.cellvars = cellvars;
        this.varnames = varNames.toArray();
        this.nlocals = this.varnames.length;

        this.stacksize = -1;
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
        this.result = null;
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
        if (sourceSection == null) {
            return 1;
        } else {
            return sourceSection.getStartLine();
        }
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

    private static ArrayList<String> getVarNames(RootNode rootNode, Set<String> freeVarsSet, Set<String> cellVarsSet, PythonCore core) {
        // tuple of names of arguments and local variables
        ArrayList<String> variableNames = new ArrayList<>();
        for (Object identifier : rootNode.getFrameDescriptor().getIdentifiers()) {
            if (identifier instanceof String) {
                String varName = (String) identifier;
                if (core.getParser().isIdentifier(core, varName) && !freeVarsSet.contains(varName) && !cellVarsSet.contains(varName)) {
                    variableNames.add(varName);
                }
            }
        }
        return variableNames;
    }

    private final static class ArgStats {
        public final int argCnt;
        private final int kwOnlyArgCnt;

        private ArgStats(int argCnt, int kwOnlyArgCnt) {
            this.argCnt = argCnt;
            this.kwOnlyArgCnt = kwOnlyArgCnt;
        }
    }

    private static ArgStats getArgStats(RootNode rootNode) {
        int argC = NodeUtil.findAllNodeInstances(rootNode, ReadIndexedArgumentNode.class).size();
        int kwOnlyArgC = 0;
        List<ReadKeywordNode> kwNodes = NodeUtil.findAllNodeInstances(rootNode, ReadKeywordNode.class);
        for (ReadKeywordNode kwNode : kwNodes) {
            if (kwNode.canBePositional()) {
                argC++;
            } else {
                kwOnlyArgC++;
            }
        }
        return new ArgStats(argC, kwOnlyArgC);
    }

    public RootNode getRootNode() {
        return result;
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
