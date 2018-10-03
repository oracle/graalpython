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

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class PCode extends PythonBuiltinObject {
    private final long FLAG_POS_GENERATOR = 5;
    private final long FLAG_POS_VAR_ARGS = 2;
    private final long FLAG_POS_VAR_KW_ARGS = 3;

    private final RootNode rootNode;
    private final PythonCore core;

    // number of arguments (not including keyword only arguments, * or ** args)
    private int argcount = -1;
    // number of keyword only arguments (not including ** arg)
    private int kwonlyargcount = -1;
    // number of local variables
    private int nlocals = -1;
    // is the required stack size (including local variables)
    private int stacksize = -1;
    // is an integer encoding a number of flags for the interpreter.
    // The following flag bits are defined for co_flags: bit 0x04 is set if the function uses the
    // *arguments syntax to accept an arbitrary number of positional arguments; bit 0x08 is set if
    // the function uses the **keywords syntax to accept arbitrary keyword arguments; bit 0x20 is
    // set if the function is a generator.
    private int flags = -1;
    // is a string representing the sequence of bytecode instructions
    private String codestring;
    // tuple of constants used in the bytecode
    private Object constants;
    // tuple containing the literals used by the bytecode
    private Object names;
    // is a tuple containing the names of the local variables (starting with the argument names)
    private Object[] varnames;
    // name of file in which this code object was created
    private String filename;
    // name with which this code object was defined
    private String name;
    // number of first line in Python source code
    private int firstlineno = -1;
    // is a string encoding the mapping from bytecode offsets to line numbers
    private Object lnotab;
    // tuple of names of free variables (referenced via a function’s closure)
    private Object[] freevars;
    // tuple of names of cell variables (referenced by containing scopes)
    private Object[] cellvars;

    // internal cache for keyword names
    private Arity.KeywordName[] keywordNames;
    // internal cache for the FrameDescriptor
    private FrameDescriptor frameDescriptor;

    public PCode(PythonClass cls, RootNode rootNode, PythonCore core) {
        super(cls);
        this.rootNode = rootNode;
        this.core = core;
    }

    public PCode(PythonClass cls, int argcount, int kwonlyargcount,
                    int nlocals, int stacksize, int flags,
                    String codestring, Object constants, Object names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    Object lnotab) {
        super(cls);
        this.rootNode = null;
        this.core = null;

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

    @TruffleBoundary
    private static Set<String> asSet(String[] values) {
        return (values != null) ? new HashSet<>(Arrays.asList(values)) : new HashSet<>();
    }

    private static String[] extractFreeVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getFreeVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getFreeVars();
        } else {
            return null;
        }
    }

    private static String[] extractCellVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getCellVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getCellVars();
        } else {
            return null;
        }
    }

    private static String extractFileName(RootNode rootNode) {
        RootNode funcRootNode = (rootNode instanceof GeneratorFunctionRootNode) ? ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode() : rootNode;
        SourceSection src = funcRootNode.getSourceSection();
        if (src != null) {
            return src.getSource().getName();
        } else if (funcRootNode instanceof ModuleRootNode) {
            return funcRootNode.getName();
        } else {
            return null;
        }
    }

    private static int extractFirstLineno(RootNode rootNode) {
        RootNode funcRootNode = (rootNode instanceof GeneratorFunctionRootNode) ? ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode() : rootNode;
        SourceSection sourceSection = funcRootNode.getSourceSection();
        return (sourceSection != null) ? sourceSection.getStartLine() : 1;
    }

    private static String extractName(RootNode rootNode) {
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

    @TruffleBoundary
    private static Set<String> getKeywordArgumentNames(List<ReadKeywordNode> readKeywordNodes) {
        Set<String> kwArgNames = new HashSet<>();
        for (ReadKeywordNode node : readKeywordNodes) {
            kwArgNames.add(node.getName());
        }
        return kwArgNames;
    }

    @TruffleBoundary
    private static Set<String> extractArgumentNames(List<? extends ReadIndexedArgumentNode> readIndexedArgumentNodes) {
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

    private static int extractStackSize(RootNode rootNode) {
        return rootNode.getFrameDescriptor().getSize();
    }

    @TruffleBoundary
    private void extractArgStats() {
        // 0x20 - generator
        this.flags = 0;
        RootNode funcRootNode = rootNode;
        if (funcRootNode instanceof GeneratorFunctionRootNode) {
            flags |= (1 << FLAG_POS_GENERATOR);
            funcRootNode = ((GeneratorFunctionRootNode) funcRootNode).getFunctionRootNode();
        }

        // 0x04 - *arguments
        if (NodeUtil.findAllNodeInstances(funcRootNode, ReadVarArgsNode.class).size() == 1) {
            flags |= (1 << FLAG_POS_VAR_ARGS);
        }
        // 0x08 - **keywords
        if (NodeUtil.findAllNodeInstances(funcRootNode, ReadVarKeywordsNode.class).size() == 1) {
            flags |= (1 << FLAG_POS_VAR_KW_ARGS);
        }

        this.freevars = extractFreeVars(rootNode);
        this.cellvars = extractCellVars(rootNode);
        Set<String> freeVarsSet = asSet((String[]) freevars);
        Set<String> cellVarsSet = asSet((String[]) cellvars);

        List<ReadKeywordNode> readKeywordNodes = NodeUtil.findAllNodeInstances(funcRootNode, ReadKeywordNode.class);
        keywordNames = new Arity.KeywordName[readKeywordNodes.size()];
        List<ReadIndexedArgumentNode> readIndexedArgumentNodes = NodeUtil.findAllNodeInstances(funcRootNode, ReadIndexedArgumentNode.class);

        Set<String> kwNames = getKeywordArgumentNames(readKeywordNodes);
        Set<String> argNames = extractArgumentNames(readIndexedArgumentNodes);

        Set<String> allArgNames = new HashSet<>();
        allArgNames.addAll(kwNames);
        allArgNames.addAll(argNames);

        this.argcount = readIndexedArgumentNodes.size();
        this.kwonlyargcount = 0;

        for (int i = 0; i < readKeywordNodes.size(); i++) {
            ReadKeywordNode kwNode = readKeywordNodes.get(i);
            keywordNames[i] = new Arity.KeywordName(kwNode.getName(), kwNode.isRequired());
            if (!kwNode.canBePositional()) {
                kwonlyargcount++;
            }
        }

        Set<String> varnamesSet = new HashSet<>();
        for (Object identifier : rootNode.getFrameDescriptor().getIdentifiers()) {
            if (identifier instanceof String) {
                String varName = (String) identifier;

                if (core.getParser().isIdentifier(core, varName)) {
                    if (allArgNames.contains(varName)) {
                        varnamesSet.add(varName);
                    } else if (!freeVarsSet.contains(varName) && !cellVarsSet.contains(varName)) {
                        varnamesSet.add(varName);
                    }
                }
            }
        }

        this.varnames = varnamesSet.toArray();
        this.nlocals = varnamesSet.size();
    }

    public RootNode getRootNode() {
        return rootNode;
    }

    public Object[] getFreeVars() {
        if (freevars == null && rootNode != null) {
            extractArgStats();
        }
        return freevars;
    }

    public Object[] getCellVars() {
        if (freevars == null && rootNode != null) {
            extractArgStats();
        }
        return cellvars;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        if (filename == null && rootNode != null) {
            filename = extractFileName(rootNode);
        }
        return filename;
    }

    public int getFirstLineNo() {
        if (firstlineno == -1 && rootNode != null) {
            firstlineno = extractFirstLineno(rootNode);
        }
        return firstlineno;
    }

    public String getName() {
        if (name == null && rootNode != null) {
            name = extractName(rootNode);
        }
        return name;
    }

    public int getArgcount() {
        if (argcount == -1 && rootNode != null) {
            extractArgStats();
        }
        return argcount;
    }

    public int getKwonlyargcount() {
        if (kwonlyargcount == -1 && rootNode != null) {
            extractArgStats();
        }
        return kwonlyargcount;
    }

    public int getNlocals() {
        if (nlocals == -1 && rootNode != null) {
            extractArgStats();
        }
        return nlocals;
    }

    public int getStacksize() {
        if (stacksize == -1 && rootNode != null) {
            stacksize = extractStackSize(rootNode);
        }
        return stacksize;
    }

    public long getFlags() {
        if (flags == -1 && rootNode != null) {
            extractArgStats();
        }
        return flags;
    }

    public Object[] getVarnames() {
        if (varnames == null && rootNode != null) {
            extractArgStats();
        }
        return varnames;
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

    public Object getLnotab() {
        return lnotab;
    }

    private Arity.KeywordName[] getKeywordNames() {
        if (keywordNames == null && rootNode != null) {
            extractArgStats();
        }
        return keywordNames;
    }

    public boolean isGenerator() {
        return (getFlags() & (1 << FLAG_POS_GENERATOR)) > 0;
    }

    public boolean takesVarArgs() {
        return (getFlags() & (1 << FLAG_POS_VAR_ARGS)) > 0;
    }

    public boolean takesVarKeywordArgs() {
        return (getFlags() & (1 << FLAG_POS_VAR_KW_ARGS)) > 0;
    }

    private int getMinNumOfPositionalArgs() {
        int defaultKwNames = 0;
        for (Arity.KeywordName kwName : getKeywordNames()) {
            defaultKwNames += (kwName.required) ? 0 : 1;
        }
        return getArgcount() - defaultKwNames;
    }

    private int getMaxNumOfPositionalArgs() {
        return this.getArgcount();
    }

    public Arity getArity() {
        return new Arity(this.getName(), this.getMinNumOfPositionalArgs(), this.getMaxNumOfPositionalArgs(), this.takesVarKeywordArgs(), this.takesVarArgs(), this.getKeywordNames());
    }

    @TruffleBoundary
    private FrameDescriptor createFrameDescriptor() {
        FrameDescriptor fd = new FrameDescriptor();
        for (Object identifier : varnames) {
            fd.addFrameSlot(identifier);
        }
        return fd;
    }

    public FrameDescriptor getFrameDescriptor() {
        if (frameDescriptor == null) {
            if (rootNode != null) {
                frameDescriptor = rootNode.getFrameDescriptor();
            } else {
                frameDescriptor = createFrameDescriptor();
            }
        }
        return frameDescriptor;
    }

    @TruffleBoundary
    public RootCallTarget getRootCallTarget() {
        if (rootNode != null) {
            return Truffle.getRuntime().createCallTarget(rootNode);
        }
        return null;
    }
}
