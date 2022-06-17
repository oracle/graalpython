/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.PClosureRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.GlobalNode;
import com.oracle.graal.python.nodes.frame.PythonFrame;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.literal.SimpleLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public final class PCode extends PythonBuiltinObject {
    public static final long FLAG_VAR_ARGS = 0x4;
    public static final long FLAG_VAR_KW_ARGS = 0x8;
    public static final long FLAG_LAMBDA = 0x10; // CO_NESTED on CPython, not needed
    public static final long FLAG_GENERATOR = 0x20;
    public static final long FLAG_MODULE = 0x40; // CO_NOFREE on CPython, we use it on modules, it's
    // redundant anyway

    // callTargetSupplier may be null, in which case callTarget and signature will be
    // set. Otherwise, these are lazily created from the supplier.
    private Supplier<CallTarget> callTargetSupplier;
    RootCallTarget callTarget;
    Signature signature;

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
    // tuple of constants used in the bytecode
    private Object[] constants;
    // tuple containing the literals (builtins/globals) used by the bytecode
    private Object[] names;
    // is a tuple containing the names of the local variables (starting with the argument names)
    private Object[] varnames;
    // name of file in which this code object was created
    private TruffleString filename;
    // name with which this code object was defined
    private TruffleString name;
    // number of first line in Python source code
    private int firstlineno = -1;
    // is a string encoding the mapping from bytecode offsets to line numbers
    private byte[] lnotab;
    // tuple of names of free variables (referenced via a function’s closure)
    private Object[] freevars;
    // tuple of names of cell variables (referenced by containing scopes)
    private Object[] cellvars;
    // convert the constants (code units) to code on first access
    private boolean convertConstants = true;

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget) {
        super(cls, instanceShape);
        this.callTarget = callTarget;
        initializeSignature(callTarget);
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, int flags, int firstlineno, byte[] lnotab, TruffleString filename) {
        this(cls, instanceShape, callTarget);
        this.flags = flags;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;
        this.filename = filename;
    }

    public PCode(Object cls, Shape instanceShape, Supplier<CallTarget> callTargetSupplier, int flags, int firstlineno, byte[] lnotab, TruffleString filename) {
        super(cls, instanceShape);
        this.callTargetSupplier = callTargetSupplier;
        this.flags = flags;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;
        this.filename = filename;
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, Signature signature, int nlocals, int stacksize, int flags, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars, TruffleString filename, TruffleString name, int firstlineno, byte[] lnotab) {
        super(cls, instanceShape);
        this.nlocals = nlocals;
        this.stacksize = stacksize;
        this.flags = flags;
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

    private static TruffleString[] extractFreeVars(RootNode rootNode) {
        if (rootNode instanceof PClosureRootNode) {
            return ((PClosureRootNode) rootNode).getFreeVars();
        } else if (rootNode instanceof PBytecodeRootNode) {
            return ((PBytecodeRootNode) rootNode).getCodeUnit().freevars;
        } else {
            return PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        }
    }

    private static TruffleString[] extractCellVars(RootNode rootNode) {
        if (rootNode instanceof PClosureFunctionRootNode) {
            return ((PClosureFunctionRootNode) rootNode).getCellVars();
        } else if (rootNode instanceof PBytecodeRootNode) {
            return ((PBytecodeRootNode) rootNode).getCodeUnit().cellvars;
        } else {
            return PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        }
    }

    @TruffleBoundary
    private static void setRootNodeFileName(RootNode rootNode, TruffleString filename) {
        RootNode funcRootNode = rootNodeForExtraction(rootNode);
        PythonContext.get(rootNode).setCodeFilename(funcRootNode.getCallTarget(), filename);
    }

    @TruffleBoundary
    public static TruffleString extractFileName(RootNode rootNode) {
        RootNode funcRootNode = rootNodeForExtraction(rootNode);
        SourceSection src = funcRootNode.getSourceSection();

        PythonContext context = PythonContext.get(rootNode);
        TruffleString filename;
        if (context != null) {
            filename = context.getCodeFilename(funcRootNode.getCallTarget());
        } else {
            return toTruffleStringUncached(funcRootNode.getName());
        }
        if (filename != null) {
            // for compiled modules, _imp._fix_co_filename will set the filename
            return filename;
        }
        String jFilename;
        if (src != null) {
            jFilename = getSourceSectionFileName(src);
        } else {
            jFilename = funcRootNode.getName();
        }
        return toTruffleStringUncached(jFilename);
    }

    @TruffleBoundary
    private static String getSourceSectionFileName(SourceSection src) {
        String path = src.getSource().getPath();
        if (path == null) {
            return src.getSource().getName();
        }
        return path;
    }

    @TruffleBoundary
    private static int extractFirstLineno(RootNode rootNode) {
        RootNode funcRootNode = rootNodeForExtraction(rootNode);
        if (funcRootNode instanceof PBytecodeRootNode) {
            return ((PBytecodeRootNode) funcRootNode).getStartOffset();
        }
        SourceSection sourceSection = funcRootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getStartLine();
        }
        return 1;
    }

    @TruffleBoundary
    private static TruffleString extractName(RootNode rootNode) {
        return toTruffleStringUncached(rootNode.getName());
    }

    @TruffleBoundary
    private static int extractStackSize(RootNode rootNode) {
        if (rootNode instanceof PBytecodeRootNode) {
            CodeUnit code = ((PBytecodeRootNode) rootNode).getCodeUnit();
            return code.stacksize + code.varnames.length + code.cellvars.length + code.freevars.length;
        }
        return rootNode.getFrameDescriptor().getNumberOfSlots();
    }

    @TruffleBoundary
    private static Object[] extractVarnames(RootNode rootNode, TruffleString[] parameterIds, TruffleString[] keywordNames, Object[] freeVars, Object[] cellVars) {
        Set<Object> freeVarsSet = asSet(freeVars);
        Set<Object> cellVarsSet = asSet(cellVars);

        ArrayList<TruffleString> varNameList = new ArrayList<>(); // must be ordered!
        varNameList.addAll(Arrays.asList(parameterIds));
        varNameList.addAll(Arrays.asList(keywordNames));

        for (Object identifier : PythonFrame.getIdentifiers(rootNode.getFrameDescriptor())) {
            if (identifier instanceof TruffleString) {
                TruffleString varName = (TruffleString) identifier;

                if (!varNameList.contains(varName)) {
                    if (StringUtils.isIdentifierUncached(varName)) {
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
    private static Object[] extractConstants(RootNode rootNode) {
        ConstantsVisitor visitor = new ConstantsVisitor();
        return visitor.findConstants(rootNodeForExtraction(rootNode));
    }

    private static class ConstantsVisitor implements NodeVisitor {
        HashSet<Object> constants;

        Object[] findConstants(Node node) {
            constants = new HashSet<>();

            this.visit(node);
            return constants.toArray();
        }

        @Override
        public boolean visit(Node node) {
            if (node instanceof FunctionRootNode) {
                ExpressionNode doc = ((FunctionRootNode) node).getDoc();
                if (doc != null) {
                    doc.accept(this);
                }
            } else if (node instanceof SimpleLiteralNode) {
                constants.add(((SimpleLiteralNode) node).getValue());
            } else if (node instanceof TupleLiteralNode) {
                List<Object> tlConstants = new ArrayList<>();
                node.accept(new NodeVisitor() {
                    @Override
                    public boolean visit(Node aNode) {
                        if (aNode instanceof SimpleLiteralNode) {
                            tlConstants.add(((SimpleLiteralNode) aNode).getValue());
                        }
                        return true;
                    }
                });
                if (!tlConstants.isEmpty()) {
                    constants.add(PythonObjectFactory.getUncached().createTuple(tlConstants.toArray()));
                }
                return false;
            } else if (node instanceof FunctionDefinitionNode) {
                FunctionDefinitionNode fdNode = (FunctionDefinitionNode) node;
                constants.add(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(PythonLanguage.get(node)), fdNode.getCallTarget()));
                constants.add(fdNode.getQualname());
                return true;
            } else if (node instanceof GeneratorExpressionNode) {
                // TODO: we do it this way here since we cannot deserialize generator
                // expressions right now
                constants.addAll(Arrays.asList(extractConstants(((GeneratorExpressionNode) node).getCallTarget().getRootNode())));
            }
            NodeUtil.forEachChild(node, this);
            return true;
        }

    }

    @TruffleBoundary
    private static Object[] extractNames(RootNode rootNode) {
        List<Object> names = new ArrayList<>();
        rootNodeForExtraction(rootNode).accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof GlobalNode) {
                    names.add(((GlobalNode) node).getAttributeId());
                } else if (node instanceof GeneratorExpressionNode) {
                    // TODO: since we do *not* add GeneratorExpressionNodes in #extractConstants, we
                    // need to find the names referenced in them here
                    names.addAll(Arrays.asList(extractNames(((GeneratorExpressionNode) node).getCallTarget().getRootNode())));
                }
                return true;
            }
        });
        return names.toArray();
    }

    private static RootNode rootNodeForExtraction(RootNode rootNode) {
        if (rootNode instanceof GeneratorFunctionRootNode) {
            return ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode();
        }
        if (rootNode instanceof PBytecodeGeneratorFunctionRootNode) {
            return ((PBytecodeGeneratorFunctionRootNode) rootNode).getBytecodeRootNode();
        }
        return rootNode;
    }

    @TruffleBoundary
    private static int extractFlags(RootNode rootNode) {
        int flags = 0;
        RootNode funcRootNode = rootNode;
        if (funcRootNode instanceof ModuleRootNode) {
            // Not on CPython
            flags |= FLAG_MODULE;
        }
        if (funcRootNode instanceof PBytecodeRootNode) {
            CodeUnit codeUnit = ((PBytecodeRootNode) funcRootNode).getCodeUnit();
            flags = getFlags(flags, codeUnit);
        }
        if (funcRootNode instanceof PBytecodeGeneratorFunctionRootNode) {
            CodeUnit codeUnit = ((PBytecodeGeneratorFunctionRootNode) funcRootNode).getBytecodeRootNode().getCodeUnit();
            flags = getFlags(flags, codeUnit);
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
            // 0x10 - lambda, not on CPython
            if (funcRootNode instanceof FunctionRootNode && ((FunctionRootNode) funcRootNode).isLambda()) {
                flags |= FLAG_LAMBDA;
            }
        }
        return flags;
    }

    private static int getFlags(int flags, CodeUnit codeUnit) {
        flags |= codeUnit.isGenerator() ? FLAG_GENERATOR : 0;
        flags |= codeUnit.takesVarArgs() ? FLAG_VAR_ARGS : 0;
        flags |= codeUnit.takesVarKeywordArgs() ? FLAG_VAR_KW_ARGS : 0;
        flags |= codeUnit.isLambda() ? FLAG_LAMBDA : 0;
        return flags;
    }

    RootNode getRootNode() {
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

    public void setFilename(TruffleString filename) {
        CompilerAsserts.neverPartOfCompilation();
        this.filename = filename;
        RootNode rootNode = getRootNode();
        setRootNodeFileName(rootNode, filename);
        constants = extractConstants(rootNode);
        for (Object ob : constants) {
            if (ob instanceof PCode) {
                ((PCode) ob).setFilename(filename);
            }
        }
    }

    @TruffleBoundary
    public TruffleString getFilename() {
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

    @TruffleBoundary
    public TruffleString getName() {
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
        RootNode rootNode = getRootNode();
        if (rootNode instanceof PRootNode) {
            return ((PRootNode) rootNode).getCode();
        } else {
            return PythonUtils.EMPTY_BYTE_ARRAY;
        }
    }

    public Object[] getConstants(PythonObjectFactory factory) {
        if (constants == null) {
            constants = extractConstants(getRootNode());
        }
        if (convertConstants) {
            convertConstants = false;
            RootNode rootNode = getRootNode();
            for (int i = 0; i < constants.length; i++) {
                constants[i] = convert(rootNode, constants[i], factory);
            }
        }
        return constants;
    }

    @TruffleBoundary
    private static Object convert(RootNode rootNode, Object o, PythonObjectFactory factory) {
        if (o instanceof CodeUnit) {
            CodeUnit code = ((CodeUnit) o);
            PBytecodeRootNode bytecodeRootNode = new PBytecodeRootNode(PythonLanguage.get(rootNode), code, getSourceSection(rootNode).getSource());
            return factory.createCode(bytecodeRootNode.getCallTarget(), bytecodeRootNode.getSignature(), code.varnames.length, code.stacksize, code.flags, code.constants,
                            code.names,
                            code.varnames, code.freevars, code.cellvars, null, code.name, code.startOffset, code.srcOffsetTable);
        }
        return o;
    }

    @TruffleBoundary
    private static SourceSection getSourceSection(RootNode rootNode) {
        return rootNode.getSourceSection();
    }

    public Object[] getNames() {
        if (names == null) {
            names = extractNames(getRootNode());
        }
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

    private Signature getSignature() {
        if (signature == null) {
            initializeSignature(getRootCallTarget());
        }
        return signature;
    }

    @TruffleBoundary
    synchronized Signature initializeSignature(RootCallTarget rootCallTarget) {
        if (signature == null) {
            if (rootCallTarget.getRootNode() instanceof PRootNode) {
                signature = ((PRootNode) rootCallTarget.getRootNode()).getSignature();
            } else {
                signature = Signature.createVarArgsAndKwArgsOnly();
            }
        }
        return signature;
    }

    private RootCallTarget getRootCallTarget() {
        if (callTarget == null) {
            initializeCallTarget();
        }
        return callTarget;
    }

    @TruffleBoundary
    synchronized RootCallTarget initializeCallTarget() {
        if (callTarget == null) {
            callTarget = (RootCallTarget) callTargetSupplier.get();
            callTargetSupplier = null;
        }
        return callTarget;
    }

    @ExportMessage
    public SourceSection getSourceLocation(@Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            SourceSection result = readSourceLocation();
            if (result != null) {
                return result;
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    private SourceSection readSourceLocation() {
        return getRootNode().getSourceSection();
    }

    @ExportMessage
    public boolean hasSourceLocation(@Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return readSourceLocation() != null;
        } finally {
            gil.release(mustRelease);
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        String codeName = this.getName() == null ? "None" : this.getName().toJavaStringUncached();
        String codeFilename = this.getFilename() == null ? "None" : this.getFilename().toJavaStringUncached();
        int codeFirstLineNo = this.getFirstLineNo() == 0 ? -1 : this.getFirstLineNo();
        return String.format("<code object %s, file \"%s\", line %d>", codeName, codeFilename, codeFirstLineNo);
    }

    private static PTuple createTuple(Object[] array, PythonObjectFactory factory) {
        Object[] data = array;
        if (data == null) {
            data = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        return factory.createTuple(data);
    }

    private static PBytes createBytes(byte[] array, PythonObjectFactory factory) {
        byte[] bytes = array;
        if (bytes == null) {
            bytes = PythonUtils.EMPTY_BYTE_ARRAY;
        }
        return factory.createBytes(bytes);
    }

    public TruffleString co_name() {
        TruffleString codeName = this.getName();
        assert codeName != null : "PCode.co_name cannot be null!";
        return codeName;
    }

    public TruffleString co_filename() {
        TruffleString fName = this.getFilename();
        assert fName != null : "PCode.co_filename cannot be null";
        return fName;
    }

    public PBytes co_code(PythonObjectFactory factory) {
        return createBytes(this.getCodestring(), factory);
    }

    public PBytes co_lnotab(PythonObjectFactory factory) {
        return createBytes(this.getLnotab(), factory);
    }

    public PTuple co_consts(PythonObjectFactory factory) {
        return createTuple(this.getConstants(factory), factory);
    }

    public PTuple co_names(PythonObjectFactory factory) {
        return createTuple(this.getNames(), factory);
    }

    public PTuple co_varnames(PythonObjectFactory factory) {
        return createTuple(this.getVarnames(), factory);
    }

    public PTuple co_freevars(PythonObjectFactory factory) {
        return createTuple(this.getFreeVars(), factory);
    }

    public PTuple co_cellvars(PythonObjectFactory factory) {
        return createTuple(this.getCellVars(), factory);
    }

    public int co_argcount() {
        return this.getArgcount();
    }

    public int co_posonlyargcount() {
        return this.getPositionalOnlyArgCount();
    }

    public int co_kwonlyargcount() {
        return this.getKwonlyargcount();
    }

    public int co_nlocals() {
        return this.getNlocals();
    }

    public int co_flags() {
        return this.getFlags();
    }

    public int co_firstlineno() {
        return this.getFirstLineNo();
    }

    public int co_stacksize() {
        return this.getStacksize();
    }
}
