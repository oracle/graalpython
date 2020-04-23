/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PClosureRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.GlobalNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonCodeSerializer;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public final class PCode extends PythonBuiltinObject {
    static final String[] EMPTY_STRINGS = new String[0];
    static final long FLAG_GENERATOR = 32;
    static final long FLAG_VAR_ARGS = 0x0004;
    static final long FLAG_VAR_KW_ARGS = 0x0008;
    static final long FLAG_MODULE = 0x0040; // CO_NOFREE on CPython, we only set it on
                                            // modules

    private final RootCallTarget callTarget;
    private final Signature signature;

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
    // is a tuple containing the names of the global variables accessed from this code object
    private Object[] globalAndBuiltinVarNames;

    public PCode(LazyPythonClass cls, RootCallTarget callTarget) {
        super(cls);
        this.callTarget = callTarget;
        if (callTarget.getRootNode() instanceof PRootNode) {
            this.signature = ((PRootNode) callTarget.getRootNode()).getSignature();
        } else {
            this.signature = Signature.createVarArgsAndKwArgsOnly();
        }
    }

    public PCode(LazyPythonClass cls, RootCallTarget callTarget, byte[] codestring, int firstlineno, byte[] lnotab) {
        this(cls, callTarget);
        this.codestring = codestring;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;
    }

    public PCode(LazyPythonClass cls, RootCallTarget callTarget, Signature signature,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        super(cls);
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
        this.callTarget = callTarget;
        this.signature = signature;
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
        SourceSection src;
        if (funcRootNode instanceof PRootNode) {
            src = ((PRootNode) funcRootNode).getSourceSection();
        } else {
            // foreign root nodes might consider getting the source section slow-path
            CompilerDirectives.transferToInterpreter();
            src = funcRootNode.getSourceSection();
        }
        if (src != null) {
            if (src.getSource().getPath() == null) {
                return src.getSource().getName();
            }
            return src.getSource().getPath();
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
            name = ((FunctionRootNode) rootNode).getName();
        } else {
            name = rootNode.getName();
        }
        return name;
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
    private static Object[] extractGlobalAndBuiltinVarnames(RootNode rootNode) {
        RootNode funcRootNode = (rootNode instanceof GeneratorFunctionRootNode) ? ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode() : rootNode;
        Set<Object> varNameList = new HashSet<>();

        List<GlobalNode> globalNodes = NodeUtil.findAllNodeInstances(funcRootNode, GlobalNode.class);
        for (GlobalNode node : globalNodes) {
            if (node instanceof ReadGlobalOrBuiltinNode) {
                varNameList.add(((ReadGlobalOrBuiltinNode) node).getAttributeId());
            } else if (node instanceof WriteGlobalNode) {
                varNameList.add(((WriteGlobalNode) node).getAttributeId());
            } else if (node instanceof DeleteGlobalNode) {
                varNameList.add(((DeleteGlobalNode) node).getAttributeId());
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
        if (rootNode instanceof GeneratorFunctionRootNode || funcRootNode instanceof PClosureRootNode) {
            PythonCodeSerializer serializer = PythonLanguage.getCore().getSerializer();
            return serializer.serialize(rootNode);
        }
        // no code for non-user functions
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
        return signature.getMaxNumOfPositionalArgs();
    }

    public int getPositionalOnlyArgCount() {
        int positionalMarkIndex = signature.getPositionalOnlyArgIndex();
        return positionalMarkIndex == -1 ? 0 : positionalMarkIndex;
    }

    public int getKwonlyargcount() {
        return signature.getNumOfRequiredKeywords();
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
            varnames = extractVarnames(getRootNode(), getSignature().getParameterIds(), getSignature().getKeywordNames(), getFreeVars(), getCellVars());
        }
        return varnames;
    }

    public byte[] getCodestring() {
        if (codestring == null) {
            this.codestring = extractCodeString(getRootNode());
        }
        return codestring;
    }

    public Object[] getGlobalAndBuiltinVarNames() {
        if (globalAndBuiltinVarNames == null) {
            this.globalAndBuiltinVarNames = extractGlobalAndBuiltinVarnames(getRootNode());
        }
        return globalAndBuiltinVarNames;
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

    public static boolean isModule(int flags) {
        return (flags & FLAG_MODULE) > 0;
    }

    static boolean takesVarArgs(int flags) {
        return (flags & FLAG_VAR_ARGS) > 0;
    }

    static boolean takesVarKeywordArgs(int flags) {
        return (flags & FLAG_VAR_KW_ARGS) > 0;
    }

    public boolean takesVarArgs() {
        return PCode.takesVarArgs(getFlags());
    }

    public boolean takesVarKeywordArgs() {
        return PCode.takesVarKeywordArgs(getFlags());
    }

    public Signature getSignature() {
        return signature;
    }

    public RootCallTarget getRootCallTarget() {
        return callTarget;
    }
}
