/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.StringLiterals.J_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.toInternedTruffleStringUncached;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public final class PCode extends PythonBuiltinObject {
    public static final int CO_OPTIMIZED = 0x1;
    public static final int CO_NEWLOCALS = 0x2;
    public static final int CO_VARARGS = 0x4;
    public static final int CO_VARKEYWORDS = 0x8;
    public static final int CO_NESTED = 0x10;
    public static final int CO_GENERATOR = 0x20;
    public static final int CO_COROUTINE = 0x80;
    public static final int CO_ITERABLE_COROUTINE = 0x100;
    public static final int CO_ASYNC_GENERATOR = 0x200;

    /* GraalPy-specific */
    public static final int CO_GRAALPYHON_MODULE = 0x1000;

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
    // tuple of constants used in the bytecode
    private Object[] constants;
    // tuple containing the literals (builtins/globals) used by the bytecode
    private TruffleString[] names;
    // is a tuple containing the names of the local variables (starting with the argument names)
    private TruffleString[] varnames;
    // name of file in which this code object was created
    private TruffleString filename;
    // name with which this code object was defined
    private TruffleString name;
    // qualified name with which this code object was defined
    private TruffleString qualname;

    // number of first line in Python source code
    private int firstlineno = -1;
    // is a string encoding the mapping from bytecode offsets to line numbers
    private byte[] linetable;
    // tuple of names of free variables (referenced via a function’s closure)
    private TruffleString[] freevars;
    // tuple of names of cell variables (referenced by containing scopes)
    private TruffleString[] cellvars;

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget) {
        super(cls, instanceShape);
        this.callTarget = callTarget;
        this.signature = Signature.fromCallTarget(callTarget);
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, int flags, int firstlineno, byte[] linetable, TruffleString filename) {
        this(cls, instanceShape, callTarget);
        this.flags = flags;
        this.firstlineno = firstlineno;
        this.linetable = linetable;
        this.filename = filename;
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, Signature signature, BytecodeCodeUnit codeUnit) {
        this(cls, instanceShape, callTarget, signature, codeUnit.varnames.length, -1, -1, null, null,
                        null, null, null, null,
                        codeUnit.name, codeUnit.qualname, -1, codeUnit.srcOffsetTable);
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, Signature signature, BytecodeDSLCodeUnit codeUnit) {
        this(cls, instanceShape, callTarget, signature, codeUnit.varnames.length, -1, -1, null, null,
                        null, null, null, null,
                        codeUnit.name, codeUnit.qualname, -1, null);
    }

    public PCode(Object cls, Shape instanceShape, RootCallTarget callTarget, Signature signature, int nlocals,
                    int stacksize, int flags, Object[] constants, TruffleString[] names,
                    TruffleString[] varnames, TruffleString[] freevars, TruffleString[] cellvars,
                    TruffleString filename, TruffleString name, TruffleString qualname,
                    int firstlineno, byte[] linetable) {
        super(cls, instanceShape);
        this.nlocals = nlocals;
        this.stacksize = stacksize;
        this.flags = flags;
        this.constants = constants;
        this.names = names;
        this.varnames = varnames;
        this.filename = filename;
        this.name = name;
        this.qualname = qualname;
        this.firstlineno = firstlineno;
        this.linetable = linetable;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.callTarget = callTarget;
        this.signature = signature;
        assert signature != null;
    }

    private static TruffleString[] extractFreeVars(RootNode rootNode) {
        CodeUnit code = getCodeUnit(rootNode);
        if (code != null) {
            return Arrays.copyOf(code.freevars, code.freevars.length);
        } else {
            return PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        }
    }

    private static TruffleString[] extractCellVars(RootNode rootNode) {
        CodeUnit code = getCodeUnit(rootNode);
        if (code != null) {
            return Arrays.copyOf(code.cellvars, code.cellvars.length);
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

        PythonContext context = PythonContext.get(rootNode);
        TruffleString filename;
        if (context != null) {
            if (rootNode instanceof PBytecodeRootNode) {
                filename = context.getCodeUnitFilename(((PBytecodeRootNode) rootNode).getCodeUnit());
            } else {
                filename = context.getCodeFilename(funcRootNode.getCallTarget());
            }
        } else {
            return toInternedTruffleStringUncached(funcRootNode.getName());
        }
        if (filename != null) {
            // for compiled modules, _imp._fix_co_filename will set the filename
            return filename;
        }
        SourceSection src = funcRootNode.getSourceSection();
        String jFilename;
        if (src != null) {
            jFilename = getSourceSectionFileName(src);
        } else {
            jFilename = funcRootNode.getName();
        }
        return toInternedTruffleStringUncached(jFilename);
    }

    @TruffleBoundary
    private static String getSourceSectionFileName(SourceSection src) {
        if (src == null) {
            return null;
        }
        String path = src.getSource().getPath();
        if (path == null) {
            return src.getSource().getName();
        }
        return path;
    }

    @TruffleBoundary
    private static int extractFirstLineno(RootNode rootNode) {
        RootNode funcRootNode = rootNodeForExtraction(rootNode);
        CodeUnit co = getCodeUnit(funcRootNode);
        if (co != null) {
            if ((co.flags & CO_GRAALPYHON_MODULE) != 0) {
                return 1;
            }
            return co.startLine;
        }

        SourceSection sourceSection = funcRootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getStartLine();
        }
        return 1;
    }

    @TruffleBoundary
    private static TruffleString extractName(RootNode rootNode) {
        CodeUnit code = getCodeUnit(rootNode);
        if (code != null) {
            return code.name;
        }
        return toInternedTruffleStringUncached(rootNode.getName());
    }

    @TruffleBoundary
    private static int extractStackSize(RootNode rootNode) {
        RootNode funcRootNode = rootNodeForExtraction(rootNode);
        if (funcRootNode instanceof PBytecodeRootNode bytecodeRootNode) {
            BytecodeCodeUnit code = bytecodeRootNode.getCodeUnit();
            return code.stacksize + code.varnames.length + code.cellvars.length + code.freevars.length;
        }
        /**
         * NB: This fallback case includes PBytecodeDSLRootNode. The Bytecode DSL stack does not
         * mirror a CPython stack (it's an operand stack for its own instruction set), so the frame
         * size is our best estimate.
         */
        return funcRootNode.getFrameDescriptor().getNumberOfSlots();
    }

    @TruffleBoundary
    private static TruffleString[] extractVarnames(RootNode node) {
        CodeUnit code = getCodeUnit(node);
        if (code != null) {
            return Arrays.copyOf(code.varnames, code.varnames.length);
        }
        return EMPTY_TRUFFLESTRING_ARRAY;
    }

    @TruffleBoundary
    private static Object[] extractConstants(RootNode node) {
        RootNode rootNode = rootNodeForExtraction(node);
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (rootNode instanceof PBytecodeDSLRootNode bytecodeDSLRootNode) {
                BytecodeDSLCodeUnit co = bytecodeDSLRootNode.getCodeUnit();
                List<Object> constants = new ArrayList<>();
                for (int i = 0; i < co.constants.length; i++) {
                    Object constant = convertConstantToPythonSpace(rootNode, co.constants[i]);
                    constants.add(constant);
                }
                return constants.toArray(new Object[0]);
            }
        } else if (rootNode instanceof PBytecodeRootNode bytecodeRootNode) {
            BytecodeCodeUnit co = bytecodeRootNode.getCodeUnit();
            Set<Object> bytecodeConstants = new HashSet<>();
            for (int bci = 0; bci < co.code.length;) {
                OpCodes op = OpCodes.fromOpCode(co.code[bci]);
                if (op.quickens != null) {
                    op = op.quickens;
                }
                if (op == OpCodes.LOAD_BYTE) {
                    bytecodeConstants.add(Byte.toUnsignedInt(co.code[bci + 1]));
                } else if (op == OpCodes.LOAD_NONE) {
                    bytecodeConstants.add(PNone.NONE);
                } else if (op == OpCodes.LOAD_TRUE) {
                    bytecodeConstants.add(true);
                } else if (op == OpCodes.LOAD_FALSE) {
                    bytecodeConstants.add(false);
                } else if (op == OpCodes.LOAD_ELLIPSIS) {
                    bytecodeConstants.add(PEllipsis.INSTANCE);
                } else if (op == OpCodes.LOAD_INT || op == OpCodes.LOAD_LONG) {
                    bytecodeConstants.add(co.primitiveConstants[Byte.toUnsignedInt(co.code[bci + 1])]);
                } else if (op == OpCodes.LOAD_DOUBLE) {
                    bytecodeConstants.add(Double.longBitsToDouble(co.primitiveConstants[Byte.toUnsignedInt(co.code[bci + 1])]));
                }
                bci += op.length();
            }
            List<Object> constants = new ArrayList<>();
            for (int i = 0; i < co.constants.length; i++) {
                Object constant = convertConstantToPythonSpace(rootNode, co.constants[i]);
                if (constant != PNone.NONE || !bytecodeConstants.contains(PNone.NONE)) {
                    constants.add(constant);
                }
            }
            constants.addAll(bytecodeConstants);
            return constants.toArray(new Object[0]);
        }
        return EMPTY_OBJECT_ARRAY;
    }

    @TruffleBoundary
    private static TruffleString[] extractNames(RootNode node) {
        CodeUnit code = getCodeUnit(node);
        if (code != null) {
            return Arrays.copyOf(code.names, code.names.length);
        }
        return EMPTY_TRUFFLESTRING_ARRAY;
    }

    private static RootNode rootNodeForExtraction(RootNode rootNode) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return PGenerator.unwrapContinuationRoot(rootNode);
        } else {
            if (rootNode instanceof PBytecodeGeneratorFunctionRootNode generatorFunctionRootNode) {
                return generatorFunctionRootNode.getBytecodeRootNode();
            }
            if (rootNode instanceof PBytecodeGeneratorRootNode generatorRootNode) {
                return generatorRootNode.getBytecodeRootNode();
            }
            return rootNode;
        }
    }

    @TruffleBoundary
    private static int extractFlags(RootNode node) {
        int flags = 0;
        CodeUnit code = getCodeUnit(node);
        if (code != null) {
            flags = code.flags;
        }
        return flags;
    }

    private static CodeUnit getCodeUnit(RootNode node) {
        RootNode rootNode = rootNodeForExtraction(node);
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (rootNode instanceof PBytecodeDSLRootNode bytecodeDSLRootNode) {
                return bytecodeDSLRootNode.getCodeUnit();
            }
        } else if (rootNode instanceof PBytecodeRootNode bytecodeRootNode) {
            return bytecodeRootNode.getCodeUnit();
        }
        return null;
    }

    RootNode getRootNode() {
        return getRootCallTarget().getRootNode();
    }

    RootNode getRootNodeForExtraction() {
        return rootNodeForExtraction(getRootNode());
    }

    public TruffleString[] getFreeVars() {
        if (freevars == null) {
            freevars = extractFreeVars(getRootNode());
        }
        return freevars;
    }

    public TruffleString[] getCellVars() {
        if (cellvars == null) {
            cellvars = extractCellVars(getRootNode());
        }
        return cellvars;
    }

    public void setFilename(TruffleString filename) {
        CompilerAsserts.neverPartOfCompilation();
        filename = PythonUtils.internString(filename);

        this.filename = filename;
        RootNode rootNode = rootNodeForExtraction(getRootNode());
        setRootNodeFileName(rootNode, filename);
        if (rootNode instanceof PBytecodeRootNode) {
            PythonContext context = PythonContext.get(rootNode);
            CodeUnit co = ((PBytecodeRootNode) rootNode).getCodeUnit();
            context.setCodeUnitFilename(co, filename);
            for (int i = 0; i < co.constants.length; i++) {
                if (co.constants[i] instanceof CodeUnit) {
                    context.setCodeUnitFilename((CodeUnit) co.constants[i], filename);
                }
            }
        }
    }

    @TruffleBoundary
    public TruffleString getFilename() {
        if (filename == null) {
            filename = extractFileName(getRootNode());
            assert filename == null || PythonUtils.isInterned(filename);
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
    public int lastiToLine(int lasti) {
        RootNode funcRootNode = rootNodeForExtraction(getRootNode());
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (funcRootNode instanceof PBytecodeDSLRootNode bytecodeDSLRootNode) {
                BytecodeNode bytecodeNode = bytecodeDSLRootNode.getBytecodeNode();
                return bytecodeDSLRootNode.bciToLine(PBytecodeDSLRootNode.lastiToBci(lasti, bytecodeNode), bytecodeNode);
            }
        } else if (funcRootNode instanceof PBytecodeRootNode bytecodeRootNode) {
            return bytecodeRootNode.bciToLine(bytecodeRootNode.lastiToBci(lasti));
        }
        return -1;
    }

    public TruffleString getName() {
        if (name == null) {
            name = extractName(getRootNode());
        }
        return name;
    }

    public TruffleString getQualName() {
        if (qualname == null) {
            qualname = extractName(getRootNode());
        }
        return qualname;
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

    public TruffleString[] getVarnames() {
        if (varnames == null) {
            varnames = extractVarnames(getRootNode());
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

    public CodeUnit getCodeUnit() {
        return getCodeUnit(getRootNode());
    }

    public Object[] getConstants() {
        if (constants == null) {
            constants = extractConstants(getRootNode());
        }
        return constants;
    }

    @TruffleBoundary
    private static Object convertConstantToPythonSpace(RootNode rootNode, Object o) {
        PythonLanguage language = PythonLanguage.get(null);
        if (o instanceof CodeUnit) {
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                BytecodeDSLCodeUnit code = (BytecodeDSLCodeUnit) o;
                PBytecodeDSLRootNode root = code.createRootNode(PythonContext.get(rootNode), getSourceSection(rootNode).getSource());
                return PFactory.createCode(language, root.getCallTarget(), root.getSignature(), code);
            } else {
                BytecodeCodeUnit code = (BytecodeCodeUnit) o;
                PBytecodeRootNode bytecodeRootNode = PBytecodeRootNode.create(language, code, ((PBytecodeRootNode) rootNode).getLazySource(), rootNode.isInternal());
                return PFactory.createCode(language, bytecodeRootNode.getCallTarget(), bytecodeRootNode.getSignature(), code);
            }
        } else if (o instanceof BigInteger) {
            return PFactory.createInt(language, (BigInteger) o);
        } else if (o instanceof int[]) {
            return PFactory.createTuple(language, (int[]) o);
        } else if (o instanceof long[]) {
            return PFactory.createTuple(language, new LongSequenceStorage((long[]) o));
        } else if (o instanceof double[]) {
            return PFactory.createTuple(language, new DoubleSequenceStorage((double[]) o));
        } else if (o instanceof boolean[]) {
            return PFactory.createTuple(language, new BoolSequenceStorage((boolean[]) o));
        } else if (o instanceof byte[]) {
            return PFactory.createBytes(language, (byte[]) o);
        } else if (o instanceof TruffleString string) {
            return PythonUtils.internString(string);
        } else if (o instanceof TruffleString[] strings) {
            Object[] array = new Object[strings.length];
            System.arraycopy(strings, 0, array, 0, strings.length);
            return PFactory.createTuple(language, array);
        } else if (o instanceof Object[] objects) {
            return PFactory.createTuple(language, objects.clone());
        }
        // Ensure no conversion is missing
        assert !IsForeignObjectNode.executeUncached(o) : o;
        return o;
    }

    @TruffleBoundary
    private static SourceSection getSourceSection(RootNode rootNode) {
        return rootNode.getSourceSection();
    }

    public TruffleString[] getNames() {
        if (names == null) {
            names = extractNames(getRootNode());
        }
        return names;
    }

    public byte[] getLinetable() {
        return linetable;
    }

    public boolean isGenerator() {
        return (getFlags() & CO_GENERATOR) > 0;
    }

    public static boolean isModule(int flags) {
        return (flags & CO_GRAALPYHON_MODULE) > 0;
    }

    static boolean takesVarArgs(int flags) {
        return (flags & CO_VARARGS) > 0;
    }

    static boolean takesVarKeywordArgs(int flags) {
        return (flags & CO_VARKEYWORDS) > 0;
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
        /*
         * This might be called without an active context (i.e. when dumping graphs), we cannot use
         * getFilename
         */
        String codeFilename = getSourceSectionFileName(rootNodeForExtraction(getRootNode()).getSourceSection());
        if (codeFilename == null) {
            codeFilename = "None";
        }
        int codeFirstLineNo = this.getFirstLineNo() == 0 ? -1 : this.getFirstLineNo();
        return String.format("<code object %s, file \"%s\", line %d>", codeName, codeFilename, codeFirstLineNo);
    }

    @TruffleBoundary
    public String toDisassembledString(boolean quickened) {
        RootNode rootNode = getRootCallTarget().getRootNode();
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER && rootNode instanceof PBytecodeDSLRootNode dslRoot) {
            return dslRoot.getCodeUnit().toString(quickened, dslRoot);
        } else if (rootNode instanceof PBytecodeGeneratorRootNode r) {
            rootNode = r.getBytecodeRootNode();
        } else if (rootNode instanceof PBytecodeGeneratorFunctionRootNode r) {
            rootNode = r.getBytecodeRootNode();
        }
        if (rootNode instanceof PBytecodeRootNode bytecodeRootNode) {
            return bytecodeRootNode.getCodeUnit().toString(quickened, bytecodeRootNode);
        }
        return J_EMPTY_STRING;
    }

    private static PTuple createTuple(PythonLanguage language, Object[] array) {
        Object[] data = array;
        if (data == null) {
            data = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        return PFactory.createTuple(language, data);
    }

    private static PBytes createBytes(byte[] array, PythonLanguage language) {
        byte[] bytes = array;
        if (bytes == null) {
            bytes = PythonUtils.EMPTY_BYTE_ARRAY;
        }
        return PFactory.createBytes(language, bytes);
    }

    public TruffleString co_name() {
        TruffleString codeName = this.getName();
        assert codeName != null : "PCode.co_name cannot be null!";
        return codeName;
    }

    public TruffleString co_qualname() {
        TruffleString qualName = this.getQualName();
        assert qualName != null : "PCode.co_qualname cannot be null!";
        return qualName;
    }

    public TruffleString co_filename() {
        TruffleString fName = this.getFilename();
        assert fName != null : "PCode.co_filename cannot be null";
        return fName;
    }

    public PBytes co_code(PythonLanguage language) {
        return createBytes(this.getCodestring(), language);
    }

    public PBytes co_lnotab(PythonLanguage language) {
        return createBytes(this.getLinetable(), language);
    }

    public PTuple co_consts(PythonLanguage language) {
        return createTuple(language, this.getConstants());
    }

    public PTuple co_names(PythonLanguage language) {
        return createTuple(language, this.getNames());
    }

    public PTuple co_varnames(PythonLanguage language) {
        return createTuple(language, this.getVarnames());
    }

    public PTuple co_freevars(PythonLanguage language) {
        return createTuple(language, this.getFreeVars());
    }

    public PTuple co_cellvars(PythonLanguage language) {
        return createTuple(language, this.getCellVars());
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
