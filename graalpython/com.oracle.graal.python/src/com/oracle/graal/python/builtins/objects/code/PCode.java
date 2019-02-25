/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class PCode extends PythonBuiltinObject {
    private static final String[] EMPTY_STRINGS = new String[0];
    private final static long FLAG_GENERATOR = 32;
    private final static long FLAG_VAR_ARGS = 0x0004;
    private final static long FLAG_VAR_KW_ARGS = 0x0008;
    private final static long FLAG_MODULE = 0x0040; // CO_NOFREE on CPython, we only set it on
                                                    // modules

    private final RootCallTarget callTarget;
    private final Arity arity;

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
    private byte[] codestring;
    // tuple of constants used in the bytecode
    private Object[] constants;
    // tuple containing the literals used by the bytecode
    private Object[] names;
    // is a tuple containing the names of the local variables (starting with the argument names)
    private Object[] varnames;
    // name of file in which this code object was created
    private String filename;
    // name with which this code object was defined
    private String name;
    // number of first line in Python source code
    private int firstlineno = -1;
    // is a string encoding the mapping from bytecode offsets to line numbers
    private byte[] lnotab;
    // tuple of names of free variables (referenced via a function’s closure)
    private Object[] freevars;
    // tuple of names of cell variables (referenced by containing scopes)
    private Object[] cellvars;

    public PCode(LazyPythonClass cls, RootCallTarget callTarget) {
        super(cls);
        this.callTarget = callTarget;
        if (callTarget.getRootNode() instanceof PRootNode) {
            this.arity = ((PRootNode) callTarget.getRootNode()).getArity();
        } else {
            this.arity = Arity.createVarArgsAndKwArgsOnly();
        }
    }

    @TruffleBoundary
    public PCode(LazyPythonClass cls, int argcount, int kwonlyargcount,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        super(cls);
        CompilerDirectives.transferToInterpreter();
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

        // Derive a new call target from the code string, if we can
        RootNode rootNode = null;
        if (codestring.length > 0) {
            if ((flags & FLAG_MODULE) == 0) {
                // we're looking for the function, not the module
                String funcdef;
                if (freevars.length > 0) {
                    // we build an outer function to provide the initial scoping
                    String outernme = "_____" + System.nanoTime();
                    StringBuilder sb = new StringBuilder();
                    sb.append("def ").append(outernme).append("():\n");
                    for (Object freevar : freevars) {
                        String v;
                        if (freevar instanceof PString) {
                            v = ((PString) freevar).getValue();
                        } else if (freevar instanceof String) {
                            v = (String) freevar;
                        } else {
                            continue;
                        }
                        sb.append(" ").append(v).append(" = None\n");
                    }
                    sb.append(" global ").append(name).append("\n");
                    sb.append(" ").append(new String(codestring));
                    sb.append("\n\n").append(outernme).append("()");
                    funcdef = sb.toString();
                } else {
                    funcdef = new String(codestring);
                }

                rootNode = (RootNode) PythonLanguage.getCore().getParser().parse(ParserMode.File, PythonLanguage.getCore(), Source.newBuilder("python", funcdef, name).build(), null);
                Object[] args = PArguments.create();
                PDict globals = PythonLanguage.getCore().factory().createDict();
                PArguments.setGlobals(args, globals);
                Truffle.getRuntime().createCallTarget(rootNode).call(args);
                Object function = globals.getDictStorage().getItem(name, HashingStorage.getSlowPathEquivalence(name));
                if (function instanceof PFunction) {
                    rootNode = ((PFunction) function).getFunctionRootNode();
                } else {
                    throw PythonLanguage.getCore().raise(PythonBuiltinClassType.ValueError, "got an invalid codestring trying to create a function code object");
                }
            } else {
                MaterializedFrame frame = null;
                if (freevars.length > 0) {
                    FrameDescriptor frameDescriptor = new FrameDescriptor();
                    frame = Truffle.getRuntime().createMaterializedFrame(new Object[0], frameDescriptor);
                    for (int i = 0; i < freevars.length; i++) {
                        Object ident = freevars[i];
                        FrameSlot slot = frameDescriptor.addFrameSlot(ident);
                        frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Object);
                        frame.setObject(slot, new PCell());
                    }
                }
                rootNode = (RootNode) PythonLanguage.getCore().getParser().parse(ParserMode.File, PythonLanguage.getCore(), Source.newBuilder("python", new String(codestring), name).build(), frame);
                assert rootNode instanceof ModuleRootNode;
            }
            this.callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        } else {
            this.callTarget = Truffle.getRuntime().createCallTarget(new RootNode(PythonLanguage.getCurrent()) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return PNone.NONE;
                }
            });
        }

        char paramNom = 'A';
        String[] paramNames = new String[argcount];
        for (int i = 0; i < paramNames.length; i++) {
            if (varnames.length > i) {
                Object varname = varnames[i];
                if (varname instanceof String) {
                    paramNames[i] = (String) varname;
                    continue;
                }
            }
            paramNames[i] = Character.toString(paramNom++);
        }
        String[] kwNames = new String[kwonlyargcount];
        for (int i = 0; i < kwNames.length; i++) {
            if (varnames.length > i + argcount) {
                Object varname = varnames[i + argcount];
                if (varname instanceof String) {
                    kwNames[i] = (String) varname;
                    continue;
                }
            }
            kwNames[i] = Character.toString(paramNom++);
        }
        this.arity = new Arity(takesVarKeywordArgs(), takesVarArgs() ? argcount : -1, !takesVarArgs() && kwonlyargcount > 0, paramNames, kwNames);
    }

    @TruffleBoundary
    private static Set<Object> asSet(Object[] objects) {
        return (objects != null) ? new HashSet<>(Arrays.asList(objects)) : new HashSet<>();
    }

    private static String[] extractFreeVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getFreeVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getFreeVars();
        } else if (rootNode instanceof ModuleRootNode) {
            return ((ModuleRootNode) rootNode).getFreeVars();
        } else {
            return EMPTY_STRINGS;
        }
    }

    private static String[] extractCellVars(RootNode rootNode) {
        if (rootNode instanceof FunctionRootNode) {
            return ((FunctionRootNode) rootNode).getCellVars();
        } else if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getCellVars();
        } else {
            return EMPTY_STRINGS;
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
            return "<unknown source>";
        }
    }

    @TruffleBoundary
    private static int extractFirstLineno(RootNode rootNode) {
        RootNode funcRootNode = (rootNode instanceof GeneratorFunctionRootNode) ? ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode() : rootNode;
        SourceSection sourceSection = funcRootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getStartLine();
        }
        return 1;
    }

    private static String extractName(RootNode rootNode) {
        String name;
        if (rootNode instanceof ModuleRootNode) {
            name = rootNode.getName();
        } else if (rootNode instanceof FunctionRootNode) {
            name = ((FunctionRootNode) rootNode).getFunctionName();
        } else {
            name = rootNode.getName();
        }
        return name;
    }

    @TruffleBoundary
    private static Set<String> getKeywordArgumentNames(List<ReadIndexedArgumentNode> readKeywordNodes) {
        return extractArgumentNames(readKeywordNodes);
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
    private static Object[] extractVarnames(RootNode rootNode, String[] parameterIds, String[] keywordNames, Object[] freeVars, Object[] cellVars) {
        Set<Object> freeVarsSet = asSet(freeVars);
        Set<Object> cellVarsSet = asSet(cellVars);

        ArrayList<String> varNameList = new ArrayList<>(); // must be ordered!
        varNameList.addAll(Arrays.asList(parameterIds));
        varNameList.addAll(Arrays.asList(keywordNames));

        for (Object identifier : rootNode.getFrameDescriptor().getIdentifiers()) {
            if (identifier instanceof String) {
                String varName = (String) identifier;

                if (FrameSlotIDs.RETURN_SLOT_ID.equals(varName) || varName.startsWith(FrameSlotIDs.TEMP_LOCAL_PREFIX)) {
                    // pass
                } else if (!varNameList.contains(varName)) {
                    if (PythonLanguage.getCore().getParser().isIdentifier(PythonLanguage.getCore(), varName)) {
                        if (!freeVarsSet.contains(varName) && !cellVarsSet.contains(varName)) {
                            varNameList.add(varName);
                        }
                    }
                }
            }
        }

        return varNameList.toArray();
    }

    @TruffleBoundary
    private static int extractFlags(RootNode rootNode) {
        int flags = 0;
        RootNode funcRootNode = rootNode;
        if (funcRootNode instanceof ModuleRootNode) {
            // Not on CPython
            flags |= FLAG_MODULE;
        } else {
            // 0x20 - generator
            if (funcRootNode instanceof GeneratorFunctionRootNode) {
                flags |= FLAG_GENERATOR;
                funcRootNode = ((GeneratorFunctionRootNode) funcRootNode).getFunctionRootNode();
            }
            // 0x04 - *arguments
            if (NodeUtil.findFirstNodeInstance(funcRootNode, ReadVarArgsNode.class) != null) {
                flags |= FLAG_VAR_ARGS;
            }
            // 0x08 - **keywords
            if (NodeUtil.findFirstNodeInstance(funcRootNode, ReadVarKeywordsNode.class) != null) {
                flags |= FLAG_VAR_KW_ARGS;
            }
        }
        return flags;
    }

    @TruffleBoundary
    private static byte[] extractCodeString(RootNode rootNode) {
        RootNode funcRootNode = rootNode;
        if (rootNode instanceof GeneratorFunctionRootNode) {
            funcRootNode = ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode();
        }
        SourceSection sourceSection = funcRootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getCharacters().toString().getBytes();
        }
        return new byte[0];
    }

    public RootNode getRootNode() {
        return getRootCallTarget().getRootNode();
    }

    public Object[] getFreeVars() {
        if (freevars == null) {
            freevars = extractFreeVars(getRootNode());
        }
        return freevars;
    }

    public Object[] getCellVars() {
        if (cellvars == null) {
            cellvars = extractCellVars(getRootNode());
        }
        return cellvars;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        if (filename == null) {
            filename = extractFileName(getRootNode());
        }
        return filename;
    }

    public int getFirstLineNo() {
        if (firstlineno == -1) {
            firstlineno = extractFirstLineno(getRootNode());
        }
        return firstlineno;
    }

    public String getName() {
        if (name == null) {
            name = extractName(getRootNode());
        }
        return name;
    }

    public int getArgcount() {
        return arity.getMaxNumOfPositionalArgs();
    }

    public int getKwonlyargcount() {
        return arity.getNumOfRequiredKeywords();
    }

    public int getNlocals() {
        if (nlocals == -1) {
            nlocals = getVarnames().length;
        }
        return nlocals;
    }

    public int getStacksize() {
        if (stacksize == -1) {
            stacksize = extractStackSize(getRootNode());
        }
        return stacksize;
    }

    public int getFlags() {
        if (flags == -1) {
            flags = extractFlags(getRootNode());
        }
        return flags;
    }

    public Object[] getVarnames() {
        if (varnames == null) {
            varnames = extractVarnames(getRootNode(), getArity().getParameterIds(), getArity().getKeywordNames(), getFreeVars(), getCellVars());
        }
        return varnames;
    }

    public byte[] getCodestring() {
        if (codestring == null) {
            this.codestring = extractCodeString(getRootNode());
        }
        return codestring;
    }

    public Object[] getConstants() {
        return constants;
    }

    public Object[] getNames() {
        return names;
    }

    public byte[] getLnotab() {
        return lnotab;
    }

    public boolean isGenerator() {
        return (getFlags() & FLAG_GENERATOR) > 0;
    }

    public boolean takesVarArgs() {
        return (getFlags() & FLAG_VAR_ARGS) > 0;
    }

    public boolean takesVarKeywordArgs() {
        return (getFlags() & FLAG_VAR_KW_ARGS) > 0;
    }

    public Arity getArity() {
        return arity;
    }

    public RootCallTarget getRootCallTarget() {
        return callTarget;
    }
}
