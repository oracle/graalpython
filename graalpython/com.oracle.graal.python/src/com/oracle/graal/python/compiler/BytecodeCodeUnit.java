package com.oracle.graal.python.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public class BytecodeCodeUnit extends CodeUnit {
    private static final int DISASSEMBLY_NUM_COLUMNS = 8;

    @CompilationFinal(dimensions = 1) public final byte[] code;
    @CompilationFinal(dimensions = 1) public final byte[] srcOffsetTable;
    @CompilationFinal(dimensions = 1) public final long[] primitiveConstants;
    @CompilationFinal(dimensions = 1) public final int[] exceptionHandlerRanges;
    public final int stacksize;
    public final int conditionProfileCount;

    /* Quickening data. See docs in PBytecodeRootNode */
    @CompilationFinal(dimensions = 1) public final byte[] outputCanQuicken;
    @CompilationFinal(dimensions = 1) public final byte[] variableShouldUnbox;
    @CompilationFinal(dimensions = 1) public final int[][] generalizeInputsMap;
    @CompilationFinal(dimensions = 1) public final int[][] generalizeVarsMap;

    /* Lazily initialized source map */
    @CompilationFinal SourceMap sourceMap;

    public BytecodeCodeUnit(TruffleString name, TruffleString qualname,
                    int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags,
                    TruffleString[] names, TruffleString[] varnames, TruffleString[] cellvars,
                    TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn,
                    int endLine, int endColumn,
                    byte[] code, byte[] linetable,
                    long[] primitiveConstants, int[] exceptionHandlerRanges, int stacksize, int conditionProfileCount,
                    byte[] outputCanQuicken, byte[] variableShouldUnbox, int[][] generalizeInputsMap, int[][] generalizeVarsMap) {
        super(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames, cellvars, freevars, cell2arg, constants, startLine, startColumn, endLine, endColumn);
        this.code = code;
        this.srcOffsetTable = linetable;
        this.primitiveConstants = primitiveConstants;
        this.exceptionHandlerRanges = exceptionHandlerRanges;
        this.stacksize = stacksize;
        this.conditionProfileCount = conditionProfileCount;
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
