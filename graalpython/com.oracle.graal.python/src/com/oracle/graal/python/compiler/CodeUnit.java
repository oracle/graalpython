/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import java.util.HashMap;
import java.util.Objects;

public final class CodeUnit {
    public static final byte HAS_DEFAULTS = 0x1;
    public static final byte HAS_KWONLY_DEFAULTS = 0x2;
    public static final byte HAS_VAR_ARGS = 0x4;
    public static final byte HAS_VAR_KW_ARGS = 0x8;
    public static final byte HAS_ANNOTATIONS = 0x10;
    public static final byte IS_GENERATOR = 0x20;
    public static final byte IS_ASYNC = 0x40;

    public final String name;
    public final String filename;

    public final int argCount;
    public final int kwOnlyArgCount;
    public final int positionalOnlyArgCount;

    public final int nlocals;
    public final int stacksize;

    public final byte[] code;
    public final byte[] srcOffsetTable;
    public final byte flags;

    public final String[] names;
    public final String[] varnames;
    public final String[] cellvars;
    public final String[] freevars;

    public final Object[] constants;
    public final long[] primitiveConstants;

    public final short[] exceptionHandlerRanges;

    public final int startOffset;
    public final int endOffset;

    CodeUnit(String name, String filename,
                    int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int nlocals, int stacksize,
                    byte[] code, byte[] linetable, byte flags,
                    String[] names, String[] varnames, String[] cellvars, String[] freevars,
                    Object[] constants, long[] primitiveConstants,
                    short[] exceptionHandlerRanges, int startOffset, int endOffset) {
        this.name = name;
        this.filename = filename;
        this.argCount = argCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.nlocals = nlocals;
        this.stacksize = stacksize;
        this.code = code;
        this.srcOffsetTable = linetable;
        this.flags = flags;
        this.names = names;
        this.varnames = varnames;
        this.cellvars = cellvars;
        this.freevars = freevars;
        this.constants = constants;
        this.primitiveConstants = primitiveConstants;
        this.exceptionHandlerRanges = exceptionHandlerRanges;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    OpCodes codeForBC(int bc) {
        return OpCodes.values()[bc];
    }

    int bciToSrcOffset(int bci) {
        int diffIdx = 0;
        int currentOffset = 0;
        for (int i = 0; i < srcOffsetTable.length; i++) {
            byte diff = srcOffsetTable[i];
            int intDiff = diff;
            if (diff != (byte)0x80) {
                currentOffset += diff;
            } else {
                while (diff == (byte)0x80) {
                    intDiff -= 0x80;
                    diff = srcOffsetTable[++i];
                }
                assert intDiff < 0;
                if (diff >= 0) {
                    currentOffset += -intDiff + diff;
                } else {
                    currentOffset += intDiff + diff + 1;
                }
            }
            if (diffIdx == bci) {
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

    @SuppressWarnings("fallthrough")
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        HashMap<Integer, String[]> lines = new HashMap<>();

        sb.append("Disassembly of ").append(name).append(":\n");
        int offset = -1;

        int bci = 0;
        int bytecodeCount = 0;
        while (bci < code.length) {
            int bcBCI = bci;
            int bc = Byte.toUnsignedInt(code[bci++]);
            OpCodes opcode = codeForBC(bc);

            String[] line = lines.computeIfAbsent(bcBCI, k -> new String[6]);

            int srcOffset = bciToSrcOffset(bytecodeCount++);
            offset = srcOffset;
            line[0] = String.format("%06d", offset);
            if (line[1] == null) {
                line[1] = "";
            }
            line[2] = String.valueOf(bcBCI);
            line[3] = opcode.toString();
            int arg = 0;
            if (!opcode.hasArg()) {
                line[4] = "";
            } else {
                for (int i = 0; i < opcode.argLength; i++) {
                    arg = (arg << 8) | Byte.toUnsignedInt(code[bci++]);
                }
                line[4] = String.format("% 2d", arg);
            }

            switch (opcode) {
                case LOAD_CONST:
                case LOAD_BIGINT:
                case LOAD_STRING:
                case LOAD_BYTES:
                    {
                        Object constant = constants[arg];
                        if (constant instanceof CodeUnit) {
                            line[5] = ((CodeUnit) constant).name + " from " + ((CodeUnit) constant).filename;
                        } else {
                            line[5] = Objects.toString(constant);
                        }
                        break;
                    }
                case LOAD_LONG:
                    line[5] = Objects.toString(primitiveConstants[arg]);
                    break;
                case LOAD_DOUBLE:
                    line[5] = Objects.toString(Double.longBitsToDouble(primitiveConstants[arg]));
                    break;
                case LOAD_DEREF:
                case STORE_DEREF:
                case DELETE_DEREF:
                    if (arg >= cellvars.length) {
                        line[5] = freevars[arg - cellvars.length];
                    } else {
                        line[5] = cellvars[arg];
                    }
                    break;
                case LOAD_FAST:
                case STORE_FAST:
                case DELETE_FAST:
                    line[5] = varnames[arg];
                    break;
                case LOAD_NAME:
                case STORE_NAME:
                case DELETE_NAME:
                case LOAD_GLOBAL:
                case STORE_GLOBAL:
                case DELETE_GLOBAL:
                case LOAD_ATTR:
                case STORE_ATTR:
                case DELETE_ATTR:
                case CALL_METHOD_VARARGS:
                    line[5] = names[arg];
                    break;
                case COLLECTION_FROM_STACK:
                case COLLECTION_ADD_STACK:
                case COLLECTION_FROM_COLLECTION:
                case COLLECTION_ADD_COLLECTION:
                    line[4] = String.format("% 2d", arg & CollectionBits.MAX_STACK_ELEMENT_COUNT);
                    switch (arg & ~CollectionBits.MAX_STACK_ELEMENT_COUNT) {
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
                case JUMP_BACKWARD:
                case JUMP_BACKWARD_FAR:
                    arg = -arg;
                    // fall through
                case FOR_ITER:
                case FOR_ITER_FAR:
                case JUMP_FORWARD:
                case JUMP_FORWARD_FAR:
                case POP_AND_JUMP_IF_FALSE:
                case POP_AND_JUMP_IF_FALSE_FAR:
                case POP_AND_JUMP_IF_TRUE:
                case POP_AND_JUMP_IF_TRUE_FAR:
                case JUMP_IF_FALSE_OR_POP:
                case JUMP_IF_FALSE_OR_POP_FAR:
                case JUMP_IF_TRUE_OR_POP:
                case JUMP_IF_TRUE_OR_POP_FAR: {
                    lines.computeIfAbsent(bcBCI + arg, k -> new String[6])[1] = ">>";
                    line[5] = String.format("to %d", bcBCI + arg);
                    break;
                }
            }
        }

        for (int i = 0; i < exceptionHandlerRanges.length; i += 3) {
            int start = exceptionHandlerRanges[i] & 0xffff;
            int stop = exceptionHandlerRanges[i + 1] & 0xffff;
            int stackAtHandler = exceptionHandlerRanges[i + 2];
            String[] line = lines.get(stop);
            assert line != null;
            line[5] = String.format("exc handler %d--%d; stack: %d", start, stop - 1, stackAtHandler);
        }

        for (bci = 0; bci < code.length; bci++) {
            String[] line = lines.get(bci);
            if (line != null) {
                String firstPart = String.format("%-8s %2s %4s %-32s %-3s", (Object[])line);
                if (line[5] != null) {
                    sb.append(firstPart).append(String.format("   (%s)", line[5]));
                } else {
                    sb.append(firstPart.stripTrailing());
                }
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
