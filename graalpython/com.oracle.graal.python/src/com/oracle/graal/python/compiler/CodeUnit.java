/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A context-independent representation of code for bytecode interpreter. Contains the actual
 * bytecode and all the related data, like constants or exception handler ranges. It doesn't contain
 * the filename to make it easier to keep in native images.
 */
public final class CodeUnit {
    public static final int HAS_DEFAULTS = 0x1;
    public static final int HAS_KWONLY_DEFAULTS = 0x2;
    public static final int HAS_ANNOTATIONS = 0x04;
    public static final int HAS_CLOSURE = 0x08;
    public static final int HAS_VAR_ARGS = 0x10;
    public static final int HAS_VAR_KW_ARGS = 0x20;
    public static final int IS_GENERATOR = 0x40;
    public static final int IS_COROUTINE = 0x80;

    public static final int DISASSEMBLY_NUM_COLUMNS = 7;

    public final TruffleString name;
    public final TruffleString qualname;

    public final int argCount;
    public final int kwOnlyArgCount;
    public final int positionalOnlyArgCount;

    public final int stacksize;

    public final byte[] code;
    public final byte[] srcOffsetTable;
    public final int flags;

    public final TruffleString[] names;
    public final TruffleString[] varnames;
    public final TruffleString[] cellvars;
    public final TruffleString[] freevars;
    public final int[] cell2arg;

    public final Object[] constants;
    public final long[] primitiveConstants;

    public final short[] exceptionHandlerRanges;

    public final int startOffset;

    public final boolean lambda;

    public CodeUnit(TruffleString name, TruffleString qualname,
                    int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int stacksize,
                    byte[] code, byte[] linetable, int flags,
                    TruffleString[] names, TruffleString[] varnames, TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg,
                    Object[] constants, long[] primitiveConstants,
                    short[] exceptionHandlerRanges, int startOffset) {
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
        this.constants = constants;
        this.primitiveConstants = primitiveConstants;
        this.exceptionHandlerRanges = exceptionHandlerRanges;
        this.startOffset = startOffset;
        this.lambda = name.equalsUncached(BuiltinNames.T_LAMBDA_NAME, TS_ENCODING);
    }

    OpCodes codeForBC(int bc) {
        return OpCodes.VALUES[bc];
    }

    public int bciToSrcOffset(int bci) {
        int diffIdx = 0;
        int currentOffset = startOffset;

        int bytecodeNumber = 0;
        for (int i = 0; i < code.length;) {
            if (bci <= i) {
                break;
            } else {
                OpCodes op = codeForBC(code[i]);
                i += op.length();
                bytecodeNumber++;
            }
        }

        for (int i = 0; i < srcOffsetTable.length; i++) {
            byte diff = srcOffsetTable[i];
            int overflow = 0;
            while (diff == (byte) 128) {
                overflow += 127;
                diff = srcOffsetTable[++i];
            }
            if (diff < 0) {
                overflow = -overflow;
            }
            currentOffset += overflow + diff;
            if (diffIdx == bytecodeNumber) {
                break;
            }
            diffIdx++;
        }
        return currentOffset;
    }

    public boolean takesVarKeywordArgs() {
        return (flags & HAS_VAR_KW_ARGS) != 0;
    }

    public boolean takesVarArgs() {
        return (flags & HAS_VAR_ARGS) != 0;
    }

    public boolean hasDefaults() {
        return (flags & HAS_DEFAULTS) != 0;
    }

    public boolean hasKwDefaults() {
        return (flags & HAS_KWONLY_DEFAULTS) != 0;
    }

    public boolean isGenerator() {
        return (flags & IS_GENERATOR) != 0;
    }

    public boolean isCoroutine() {
        return (flags & IS_COROUTINE) != 0;
    }

    public boolean isGeneratorOrCoroutine() {
        return (flags & (IS_GENERATOR | IS_COROUTINE)) != 0;
    }

    public boolean isLambda() {
        return lambda;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public String toString() {
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
        if (!flagNames.isEmpty()) {
            sb.append("Flags: ").append(String.join(" | ", flagNames)).append("\n");
        }

        int bci = 0;
        int oparg = 0;
        while (bci < code.length) {
            int bcBCI = bci;
            int bc = Byte.toUnsignedInt(code[bci++]);
            OpCodes opcode = codeForBC(bc);

            String[] line = lines.computeIfAbsent(bcBCI, k -> new String[DISASSEMBLY_NUM_COLUMNS]);

            int offset = bciToSrcOffset(bcBCI);
            line[0] = String.format("%06d", offset);
            if (line[1] == null) {
                line[1] = "";
            }
            line[2] = String.valueOf(bcBCI);
            line[3] = opcode.toString();
            byte[] followingArgs = PythonUtils.EMPTY_BYTE_ARRAY;
            if (!opcode.hasArg()) {
                line[4] = "";
            } else {
                oparg |= Byte.toUnsignedInt(code[bci++]);
                if (opcode.argLength > 1) {
                    followingArgs = new byte[opcode.argLength - 1];
                    for (int i = 0; i < opcode.argLength - 1; i++) {
                        followingArgs[i] = code[bci++];
                    }
                }
                line[4] = String.format("% 2d", oparg);
            }

            switch (opcode) {
                case EXTENDED_ARG:
                    line[4] = "";
                    break;
                case LOAD_CONST:
                case LOAD_BIGINT:
                case LOAD_STRING:
                case LOAD_BYTES:
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
                        } else if (constant instanceof Object[]) {
                            line[5] = Arrays.toString((Object[]) constant);
                        } else {
                            line[5] = Objects.toString(constant);
                        }
                    }
                    break;
                }
                case MAKE_FUNCTION: {
                    line[4] = String.format("% 2d", followingArgs[0]);
                    CodeUnit code = (CodeUnit) constants[oparg];
                    line[5] = line[5] = code.qualname.toJavaStringUncached();
                    break;
                }
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
                case CALL_METHOD_VARARGS:
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
                    line[4] = String.format("% 2d", followingArgs[0]);
                    line[5] = names[oparg].toJavaStringUncached();
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
                    switch (CollectionBits.elementType(oparg)) {
                        case CollectionBits.LIST:
                            line[5] = "list";
                            break;
                        case CollectionBits.TUPLE:
                            line[5] = "tuple";
                            break;
                        case CollectionBits.SET:
                            line[5] = "set";
                            break;
                        case CollectionBits.DICT:
                            line[5] = "dict";
                            break;
                        case CollectionBits.KWORDS:
                            line[5] = "PKeyword[]";
                            break;
                        case CollectionBits.OBJECT:
                            line[5] = "Object[]";
                            break;
                    }
                    break;
                case UNPACK_EX:
                    line[5] = String.format("%d, %d", oparg, Byte.toUnsignedInt(followingArgs[0]));
                    break;
                case JUMP_BACKWARD:
                    oparg = -oparg;
                    // fall through
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
            }
            if (opcode == OpCodes.EXTENDED_ARG) {
                oparg <<= 8;
            } else {
                oparg = 0;
            }
        }

        for (int i = 0; i < exceptionHandlerRanges.length; i += 4) {
            int start = exceptionHandlerRanges[i] & 0xffff;
            int stop = exceptionHandlerRanges[i + 1] & 0xffff;
            int handler = exceptionHandlerRanges[i + 2] & 0xffff;
            int stackAtHandler = exceptionHandlerRanges[i + 3] & 0xffff;
            String[] line = lines.get(handler);
            assert line != null;
            String handlerStr = String.format("exc handler %d - %d; stack: %d", start, stop, stackAtHandler);
            if (line[6] == null) {
                line[6] = handlerStr;
            } else {
                line[6] += " | " + handlerStr;
            }
        }

        for (bci = 0; bci < code.length; bci++) {
            String[] line = lines.get(bci);
            if (line != null) {
                line[5] = line[5] == null ? "" : String.format("(%s)", line[5]);
                line[6] = line[6] == null ? "" : String.format("(%s)", line[6]);
                String formatted = String.format("%-8s %2s %4s %-32s %-3s   %-32s %s", (Object[]) line);
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
}
