/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A context-independent representation of code for bytecode interpreter. Contains the actual
 * bytecode and all the related data, like constants or exception handler ranges. It doesn't contain
 * the filename to make it easier to keep in native images.
 */
public final class CodeUnit {
    private static final int DISASSEMBLY_NUM_COLUMNS = 8;

    public final TruffleString name;
    public final TruffleString qualname;

    public final int argCount;
    public final int kwOnlyArgCount;
    public final int positionalOnlyArgCount;

    public final int stacksize;

    @CompilationFinal(dimensions = 1) public final byte[] code;
    @CompilationFinal(dimensions = 1) public final byte[] srcOffsetTable;
    public final int flags;

    @CompilationFinal(dimensions = 1) public final TruffleString[] names;
    @CompilationFinal(dimensions = 1) public final TruffleString[] varnames;
    @CompilationFinal(dimensions = 1) public final TruffleString[] cellvars;
    @CompilationFinal(dimensions = 1) public final TruffleString[] freevars;
    @CompilationFinal(dimensions = 1) public final int[] cell2arg;
    @CompilationFinal(dimensions = 1) public final int[] arg2cell;

    @CompilationFinal(dimensions = 1) public final Object[] constants;
    @CompilationFinal(dimensions = 1) public final long[] primitiveConstants;

    @CompilationFinal(dimensions = 1) public final int[] exceptionHandlerRanges;

    public final int conditionProfileCount;

    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;

    /* Lazily initialized source map */
    @CompilationFinal SourceMap sourceMap;

    /* Quickening data. See docs in PBytecodeRootNode */
    @CompilationFinal(dimensions = 1) public final byte[] outputCanQuicken;
    @CompilationFinal(dimensions = 1) public final byte[] variableShouldUnbox;
    @CompilationFinal(dimensions = 1) public final int[][] generalizeInputsMap;
    @CompilationFinal(dimensions = 1) public final int[][] generalizeVarsMap;

    public CodeUnit(TruffleString name, TruffleString qualname,
                    int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int stacksize,
                    byte[] code, byte[] linetable, int flags,
                    TruffleString[] names, TruffleString[] varnames, TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg,
                    Object[] constants, long[] primitiveConstants,
                    int[] exceptionHandlerRanges, int conditionProfileCount,
                    int startLine, int startColumn, int endLine, int endColumn,
                    byte[] outputCanQuicken, byte[] variableShouldUnbox, int[][] generalizeInputsMap, int[][] generalizeVarsMap) {
        this.name = name;
        this.qualname = qualname != null ? qualname : name;
        this.argCount = argCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.stacksize = stacksize;
        this.code = code;
        this.srcOffsetTable = linetable;
        this.flags = flags;
        this.names = names;
        this.varnames = varnames;
        this.cellvars = cellvars;
        this.freevars = freevars;
        this.cell2arg = cell2arg;
        int[] arg2cellValue = null;
        if (cell2arg != null) {
            arg2cellValue = new int[getTotalArgCount()];
            Arrays.fill(arg2cellValue, -1);
            for (int i = 0; i < cell2arg.length; i++) {
                if (cell2arg[i] >= 0) {
                    arg2cellValue[cell2arg[i]] = i;
                }
            }
        }
        this.arg2cell = arg2cellValue;
        this.constants = constants;
        this.primitiveConstants = primitiveConstants;
        this.exceptionHandlerRanges = exceptionHandlerRanges;
        this.conditionProfileCount = conditionProfileCount;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.outputCanQuicken = outputCanQuicken;
        this.variableShouldUnbox = variableShouldUnbox;
        this.generalizeInputsMap = generalizeInputsMap;
        this.generalizeVarsMap = generalizeVarsMap;
    }

    public SourceMap getSourceMap() {
        if (sourceMap == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            sourceMap = new SourceMap(code, srcOffsetTable, startLine, startColumn);
        }
        return sourceMap;
    }

    public int bciToLine(int bci) {
        if (bci < 0 || bci >= code.length) {
            return -1;
        }
        return getSourceMap().startLineMap[bci];
    }

    public int bciToColumn(int bci) {
        if (bci < 0 || bci >= code.length) {
            return -1;
        }
        return getSourceMap().startColumnMap[bci];
    }

    public SourceSection getSourceSection(Source source) {
        return SourceMap.getSourceSection(source, startLine, startColumn, endLine, endColumn);
    }

    public boolean takesVarKeywordArgs() {
        return (flags & PCode.CO_VARKEYWORDS) != 0;
    }

    public boolean takesVarArgs() {
        return (flags & PCode.CO_VARARGS) != 0;
    }

    public boolean isGenerator() {
        return (flags & PCode.CO_GENERATOR) != 0;
    }

    public boolean isCoroutine() {
        return (flags & PCode.CO_COROUTINE) != 0;
    }

    public boolean isAsyncGenerator() {
        return (flags & PCode.CO_ASYNC_GENERATOR) != 0;
    }

    public boolean isGeneratorOrCoroutine() {
        return (flags & (PCode.CO_GENERATOR | PCode.CO_COROUTINE | PCode.CO_ASYNC_GENERATOR | PCode.CO_ITERABLE_COROUTINE)) != 0;
    }

    public int getRegularArgCount() {
        return argCount + positionalOnlyArgCount + kwOnlyArgCount;
    }

    public int getTotalArgCount() {
        int count = getRegularArgCount();
        if (takesVarArgs()) {
            count++;
        }
        if (takesVarKeywordArgs()) {
            count++;
        }
        return count;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public String toString() {
        return toString(code);
    }

    public String toString(byte[] bytecode) {
        StringBuilder sb = new StringBuilder();

        HashMap<Integer, String[]> lines = new HashMap<>();

        sb.append("Disassembly of ").append(qualname).append(":\n");

        List<String> flagNames = new ArrayList<>();
        if (isGenerator()) {
            flagNames.add("CO_GENERATOR");
        }
        if (isCoroutine()) {
            flagNames.add("CO_COROUTINE");
        }
        if (isAsyncGenerator()) {
            flagNames.add("CO_ASYNC_GENERATOR");
        }
        if (!flagNames.isEmpty()) {
            sb.append("Flags: ").append(String.join(" | ", flagNames)).append("\n");
        }

        int bci = 0;
        int oparg = 0;
        SourceMap map = getSourceMap();
        while (bci < bytecode.length) {
            int bcBCI = bci;
            OpCodes opcode = OpCodes.fromOpCode(bytecode[bci++]);

            String[] line = lines.computeIfAbsent(bcBCI, k -> new String[DISASSEMBLY_NUM_COLUMNS]);
            line[0] = String.format("%3d:%-3d - %3d:%-3d", map.startLineMap[bcBCI], map.startColumnMap[bcBCI], map.endLineMap[bcBCI], map.endColumnMap[bcBCI]);
            if (line[1] == null) {
                line[1] = "";
            }
            line[2] = String.valueOf(bcBCI);
            line[3] = opcode.toString();
            byte[] followingArgs = PythonUtils.EMPTY_BYTE_ARRAY;
            if (!opcode.hasArg()) {
                line[4] = "";
            } else {
                oparg |= Byte.toUnsignedInt(bytecode[bci++]);
                if (opcode.argLength > 1) {
                    followingArgs = new byte[opcode.argLength - 1];
                    for (int i = 0; i < opcode.argLength - 1; i++) {
                        followingArgs[i] = bytecode[bci++];
                    }
                }
                line[4] = String.format("% 2d", oparg);
            }

            while (true) {
                switch (opcode) {
                    case EXTENDED_ARG:
                        line[4] = "";
                        break;
                    case LOAD_BYTE:
                        line[4] = String.format("% 2d", (byte) oparg);
                        break;
                    case LOAD_CONST:
                    case LOAD_BIGINT:
                    case LOAD_STRING:
                    case LOAD_BYTES:
                    case LOAD_CONST_COLLECTION:
                    case MAKE_KEYWORD: {
                        Object constant = constants[oparg];
                        if (constant instanceof CodeUnit) {
                            line[5] = ((CodeUnit) constant).qualname.toJavaStringUncached();
                        } else {
                            if (constant instanceof TruffleString) {
                                line[5] = StringNodes.StringReprNode.getUncached().execute((TruffleString) constant).toJavaStringUncached();
                            } else if (constant instanceof byte[]) {
                                byte[] bytes = (byte[]) constant;
                                line[5] = BytesUtils.bytesRepr(bytes, bytes.length);
                            } else if (constant instanceof int[]) {
                                line[5] = Arrays.toString((int[]) constant);
                            } else if (constant instanceof long[]) {
                                line[5] = Arrays.toString((long[]) constant);
                            } else if (constant instanceof boolean[]) {
                                line[5] = Arrays.toString((boolean[]) constant);
                            } else if (constant instanceof double[]) {
                                line[5] = Arrays.toString((double[]) constant);
                            } else if (constant instanceof Object[]) {
                                line[5] = Arrays.toString((Object[]) constant);
                            } else {
                                line[5] = Objects.toString(constant);
                            }
                        }
                        if (opcode == OpCodes.LOAD_CONST_COLLECTION) {
                            line[5] += " type " + collectionTypeToString(followingArgs[0]) + " into " + collectionKindToString(followingArgs[0]);
                        }
                        break;
                    }
                    case MAKE_FUNCTION: {
                        line[4] = String.format("% 2d", followingArgs[0]);
                        CodeUnit codeUnit = (CodeUnit) constants[oparg];
                        line[5] = line[5] = codeUnit.qualname.toJavaStringUncached();
                        break;
                    }
                    case LOAD_INT:
                    case LOAD_LONG:
                        line[5] = Objects.toString(primitiveConstants[oparg]);
                        break;
                    case LOAD_DOUBLE:
                        line[5] = Objects.toString(Double.longBitsToDouble(primitiveConstants[oparg]));
                        break;
                    case LOAD_COMPLEX: {
                        double[] num = (double[]) constants[oparg];
                        if (num[0] == 0.0) {
                            line[5] = String.format("%gj", num[1]);
                        } else {
                            line[5] = String.format("%g%+gj", num[0], num[1]);
                        }
                        break;
                    }
                    case LOAD_CLOSURE:
                    case LOAD_DEREF:
                    case STORE_DEREF:
                    case DELETE_DEREF:
                        if (oparg >= cellvars.length) {
                            line[5] = freevars[oparg - cellvars.length].toJavaStringUncached();
                        } else {
                            line[5] = cellvars[oparg].toJavaStringUncached();
                        }
                        break;
                    case LOAD_FAST:
                    case STORE_FAST:
                    case DELETE_FAST:
                        line[5] = varnames[oparg].toJavaStringUncached();
                        break;
                    case LOAD_NAME:
                    case LOAD_METHOD:
                    case STORE_NAME:
                    case DELETE_NAME:
                    case IMPORT_NAME:
                    case IMPORT_FROM:
                    case LOAD_GLOBAL:
                    case STORE_GLOBAL:
                    case DELETE_GLOBAL:
                    case LOAD_ATTR:
                    case STORE_ATTR:
                    case DELETE_ATTR:
                        line[5] = names[oparg].toJavaStringUncached();
                        break;
                    case FORMAT_VALUE: {
                        int type = oparg & FormatOptions.FVC_MASK;
                        switch (type) {
                            case FormatOptions.FVC_STR:
                                line[5] = "STR";
                                break;
                            case FormatOptions.FVC_REPR:
                                line[5] = "REPR";
                                break;
                            case FormatOptions.FVC_ASCII:
                                line[5] = "ASCII";
                                break;
                            case FormatOptions.FVC_NONE:
                                line[5] = "NONE";
                                break;
                        }
                        if ((oparg & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC) {
                            line[5] += " + SPEC";
                        }
                        break;
                    }
                    case CALL_METHOD: {
                        line[4] = String.format("% 2d", oparg);
                        break;
                    }
                    case UNARY_OP:
                        line[5] = UnaryOps.values()[oparg].toString();
                        break;
                    case BINARY_OP:
                        line[5] = BinaryOps.values()[oparg].toString();
                        break;
                    case COLLECTION_FROM_STACK:
                    case COLLECTION_ADD_STACK:
                    case COLLECTION_FROM_COLLECTION:
                    case COLLECTION_ADD_COLLECTION:
                    case ADD_TO_COLLECTION:
                        line[4] = String.format("% 2d", CollectionBits.elementCount(oparg));
                        line[5] = collectionKindToString(oparg);
                        break;
                    case UNPACK_EX:
                        line[5] = String.format("%d, %d", oparg, Byte.toUnsignedInt(followingArgs[0]));
                        break;
                    case JUMP_BACKWARD:
                        lines.computeIfAbsent(bcBCI - oparg, k -> new String[DISASSEMBLY_NUM_COLUMNS])[1] = ">>";
                        line[5] = String.format("to %d", bcBCI - oparg);
                        break;
                    case FOR_ITER:
                    case JUMP_FORWARD:
                    case POP_AND_JUMP_IF_FALSE:
                    case POP_AND_JUMP_IF_TRUE:
                    case JUMP_IF_FALSE_OR_POP:
                    case JUMP_IF_TRUE_OR_POP:
                    case MATCH_EXC_OR_JUMP:
                    case SEND:
                        lines.computeIfAbsent(bcBCI + oparg, k -> new String[DISASSEMBLY_NUM_COLUMNS])[1] = ">>";
                        line[5] = String.format("to %d", bcBCI + oparg);
                        break;
                    default:
                        if (opcode.quickens != null) {
                            opcode = opcode.quickens;
                            continue;
                        }
                }
                if (opcode == OpCodes.EXTENDED_ARG) {
                    oparg <<= 8;
                } else {
                    oparg = 0;
                }
                break;
            }
        }

        for (int i = 0; i < exceptionHandlerRanges.length; i += 4) {
            int start = exceptionHandlerRanges[i];
            int stop = exceptionHandlerRanges[i + 1];
            int handler = exceptionHandlerRanges[i + 2];
            int stackAtHandler = exceptionHandlerRanges[i + 3];
            String[] line = lines.get(handler);
            assert line != null;
            String handlerStr = String.format("exc handler %d - %d; stack: %d", start, stop, stackAtHandler);
            if (line[6] == null) {
                line[6] = handlerStr;
            } else {
                line[6] += " | " + handlerStr;
            }
        }

        for (bci = 0; bci < bytecode.length; bci++) {
            String[] line = lines.get(bci);
            if (line != null) {
                line[5] = line[5] == null ? "" : String.format("(%s)", line[5]);
                line[6] = line[6] == null ? "" : String.format("(%s)", line[6]);
                line[7] = "";
                if (outputCanQuicken != null && (outputCanQuicken[bci] != 0 || generalizeInputsMap[bci] != null)) {
                    StringBuilder quickenSb = new StringBuilder();
                    if (outputCanQuicken[bci] != 0) {
                        quickenSb.append("can quicken");
                    }
                    if (generalizeInputsMap[bci] != null) {
                        if (quickenSb.length() > 0) {
                            quickenSb.append(", ");
                        }
                        quickenSb.append("generalizes: ");
                        for (int i = 0; i < generalizeInputsMap[bci].length; i++) {
                            if (i > 0) {
                                quickenSb.append(", ");
                            }
                            quickenSb.append(generalizeInputsMap[bci][i]);
                        }
                    }
                    line[7] = quickenSb.toString();
                }
                String formatted = String.format("%-8s %2s %4s %-32s %-3s   %-32s %s %s", (Object[]) line);
                sb.append(formatted.stripTrailing());
                sb.append('\n');
            }
        }

        for (Object c : constants) {
            if (c instanceof CodeUnit) {
                sb.append('\n');
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static String collectionKindToString(int oparg) {
        switch (CollectionBits.collectionKind(oparg)) {
            case CollectionBits.KIND_LIST:
                return "list";
            case CollectionBits.KIND_TUPLE:
                return "tuple";
            case CollectionBits.KIND_SET:
                return "set";
            case CollectionBits.KIND_DICT:
                return "dict";
            case CollectionBits.KIND_KWORDS:
                return "PKeyword[]";
            case CollectionBits.KIND_OBJECT:
                return "Object[]";
        }
        throw new IllegalStateException("Unknown kind");
    }

    private static String collectionTypeToString(int oparg) {
        switch (CollectionBits.elementType(oparg)) {
            case CollectionBits.ELEMENT_BOOLEAN:
                return "boolean";
            case CollectionBits.ELEMENT_INT:
                return "int";
            case CollectionBits.ELEMENT_LONG:
                return "long";
            case CollectionBits.ELEMENT_DOUBLE:
                return "double";
            case CollectionBits.ELEMENT_OBJECT:
                return "Object";
        }
        throw new IllegalStateException("Unknown type");
    }

    // -1 for line after the code block, -2 for line before the code block, line number otherwise
    public int lineToBci(int line) {
        if (startLine == line) {
            return 0;
        }
        // todo look into instrumentation support
        int[] map = getSourceMap().startLineMap;
        int bestBci = -1;
        int lineDiff = Integer.MAX_VALUE;
        boolean afterFirst = false;
        for (int bci = 0; bci < map.length; ++bci) {
            if (map[bci] >= line) {
                int lineDiff2 = map[bci] - line;
                // the first bci found is the start of the line
                if (lineDiff2 < lineDiff) {
                    bestBci = bci;
                    lineDiff = lineDiff2;
                }
            }
            if (map[bci] > 0 && map[bci] <= line) {
                // the line is actually within the codeblock.
                afterFirst = true;
            }
        }
        // bestBci being -1 means the line is outside the code block
        return afterFirst ? bestBci : -2;
    }

    @FunctionalInterface
    public interface BytecodeAction {
        void run(int bci, OpCodes op, int oparg, byte[] followingArgs);
    }

    public static void iterateBytecode(byte[] bytecode, BytecodeAction action) {
        int oparg = 0;
        for (int bci = 0; bci < bytecode.length;) {
            OpCodes op = OpCodes.fromOpCode(bytecode[bci]);
            if (op == OpCodes.EXTENDED_ARG) {
                oparg |= Byte.toUnsignedInt(bytecode[bci + 1]);
                oparg <<= 8;
            } else {
                byte[] followingArgs = null;
                if (op.argLength > 0) {
                    oparg |= Byte.toUnsignedInt(bytecode[bci + 1]);
                    if (op.argLength > 1) {
                        followingArgs = new byte[op.argLength - 1];
                        System.arraycopy(bytecode, bci + 2, followingArgs, 0, followingArgs.length);
                    }
                }
                action.run(bci, op, oparg, followingArgs);
                oparg = 0;
            }
            bci += op.length();
        }
    }

    public void iterateBytecode(BytecodeAction action) {
        iterateBytecode(code, action);
    }
}
