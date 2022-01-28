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

import static com.oracle.graal.python.compiler.CompilationScope.AsyncFunction;
import static com.oracle.graal.python.compiler.CompilationScope.Class;
import static com.oracle.graal.python.compiler.CompilationScope.Function;
import static com.oracle.graal.python.compiler.CompilationScope.Lambda;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public final class CompilationUnit {
    final String name;
    final String qualName;
    final HashMap<Object, Integer> constants = new HashMap<>();
    final HashMap<Long, Integer> primitiveConstants = new HashMap<>();
    final HashMap<String, Integer> names = new HashMap<>();
    final HashMap<String, Integer> varnames = new HashMap<>();
    final HashMap<String, Integer> cellvars;
    final HashMap<String, Integer> freevars;
    final int argCount;
    final int positionalOnlyArgCount;
    final int kwOnlyArgCount;
    final boolean takesVarArgs;
    final boolean takesVarKeywordArgs;

    final Block startBlock = new Block();
    final Scope scope;
    final CompilationScope scopeType;
    final String privateName;
    final Stack<BlockInfo> blockInfoStack = new Stack<>();

    Block currentBlock = startBlock;

    int startOffset;
    int endOffset;

    CompilationUnit(CompilationScope scopeType, Scope scope, String name, CompilationUnit parent, int argCount, int positionalOnlyArgCount, int kwOnlyArgCount, boolean takesVarArgs, boolean takesVarKeywordArgs, int startOffset, int endOffset) {
        this.scopeType = scopeType;
        this.scope = scope;
        this.name = name;
        this.argCount = argCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.takesVarArgs = takesVarArgs;
        this.takesVarKeywordArgs = takesVarKeywordArgs;
        this.startOffset = startOffset;
        this.endOffset = endOffset;

        if (parent != null) {
            if (scopeType == Class) {
                privateName = name;
            } else {
                privateName = parent.privateName;
            }
            if (!(EnumSet.of(Function, AsyncFunction, Class).contains(scopeType) &&
                  parent.scope.getUseOfName(ScopeEnvironment.mangle(parent.privateName, name)).
                                            contains(Scope.DefUse.GlobalExplicit))) {
                String base;
                if (EnumSet.of(Function, AsyncFunction, Lambda).contains(parent.scopeType)) {
                    base = parent.qualName + ".<locals>";
                } else {
                    base = parent.qualName;
                }
                name = base + "." + name;
            }
        } else {
            privateName = null;
        }
        qualName = name;

        // derive variable names
        for (int i = 0; i < scope.getVarnames().size(); i++) {
            varnames.put(scope.getVarnames().get(i), i);
        }
        cellvars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Cell), 0);
        if (scope.needsClassClosure()) {
            assert scopeType == Class;
            assert cellvars.isEmpty();
            cellvars.put("__class__", 0);
        }
        freevars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.DefFreeClass), cellvars.size());
    }

    void useNextBlock(Block b) {
        if (b == currentBlock) {
            return;
        }
        assert currentBlock.next == null;
        currentBlock.next = b;
        useBlock(b);
    }

    void useBlock(Block b) {
        currentBlock = b;
    }

    private void addImplicitReturn() {
        Block b = startBlock;
        while (b.next != null) {
            b = b.next;
        }
        if (!b.isReturn()) {
            b.instr.add(new Instruction(OpCodes.LOAD_NONE, 0, null, 0));
            b.instr.add(new Instruction(OpCodes.RETURN_VALUE, 0, null, 0));
        }
    }

    public CodeUnit assemble(String filename, int flags) {
        addImplicitReturn();
        calculateJumpInstructionArguments();

        // Just a list of bytes with deltas of src offsets from bytecode to bytecode. The range is
        // -127 to 127. -128 (0x80) is reserved for larger deltas. The calculation is to take the
        // -128 and keep adding any following -128 values. Once we reach a value that is in the
        // range -127 to 127, we know if the preceding values are supposed to be a positive or
        // negative delta, so we adjust the sign of the collected value and then add the final
        // value. If the resulting value is negative, we add 1. So 128 is encoded as (0x80 0x00),
        // 129 is (0x80 0x01), -128 is ((0x80, 0xff) = -128 + -1 + 1), -129 is ((0x80, 0xfe) = -128
        // + -2 + 1) and so on.
        ByteArrayOutputStream srcOffsets = new ByteArrayOutputStream();

        // The actual bytecodes
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // stackdepth
        HashMap<Block, Integer> stackAtBlock = new HashMap<>();

        Stack<short[]> exceptionHandlerStack = new Stack<>();
        ArrayList<short[]> finishedExceptionHandlerRanges = new ArrayList<>();

        int stacksize = 0;
        int maxStackSize = 0;

        Block b = startBlock;

        int lastSrcOffset = 0;
        while (b != null) {
            assert !stackAtBlock.containsKey(b) || stacksize == stackAtBlock.get(b);

            for (Instruction i : b.instr) {
                if (handleMarker(i, buf, exceptionHandlerStack, finishedExceptionHandlerRanges, stacksize)) {
                    continue;
                }

                emitBytecode(i, buf);

                stacksize = calculateStackEffect(i, stackAtBlock, stacksize);
                maxStackSize = Math.max(stacksize, maxStackSize);

                insertSrcOffsetTable(i.srcOffset, lastSrcOffset, srcOffsets);
                lastSrcOffset = i.srcOffset;
            }
            b = b.next;
        }

        assert flags < 256;
        flags |= takesVarArgs ? CodeUnit.HAS_VAR_ARGS : 0;
        flags |= takesVarKeywordArgs ? CodeUnit.HAS_VAR_KW_ARGS : 0;

        assert exceptionHandlerStack.isEmpty();

        short[] exceptionHandlerRanges = new short[finishedExceptionHandlerRanges.size() * 3];
        for (int i = 0; i < finishedExceptionHandlerRanges.size(); i++) {
            short[] range = finishedExceptionHandlerRanges.get(i);
            exceptionHandlerRanges[i * 3] = range[0];
            exceptionHandlerRanges[i * 3 + 1] = range[1];
            exceptionHandlerRanges[i * 3 + 2] = range[2];
        }
        return new CodeUnit(qualName == null ? name : qualName, filename,
                        argCount, kwOnlyArgCount, positionalOnlyArgCount,
                        varnames.size(), maxStackSize,
                        buf.toByteArray(), srcOffsets.toByteArray(), (byte)flags,
                        orderedKeys(names, new String[0]),
                        orderedKeys(varnames, new String[0]),
                        orderedKeys(cellvars, new String[0]),
                        orderedKeys(freevars, new String[0], cellvars.size()),
                        orderedKeys(constants, new Object[0]),
                        orderedLong(primitiveConstants),
                        exceptionHandlerRanges,
                        startOffset, endOffset);
    }

    private void calculateJumpInstructionArguments() {
        HashMap<Block, Integer> blockLocationMap = new HashMap<>();
        int p = 0;
        Block b = startBlock;
        while (b != null) {
            blockLocationMap.put(b, p);
            for (Instruction i : b.instr) {
                p += i.opcode.length();
            }
            b = b.next;
        }

        p = 0;
        b = startBlock;
        while (b != null) {
            for (int i = 0; i < b.instr.size(); i++) {
                Instruction is = b.instr.get(i);
                Block tgt = is.getTarget();
                if (tgt != null) {
                    int targetPos = blockLocationMap.get(tgt);
                    int distance = Math.abs(p - targetPos);
                    OpCodes opcode = is.opcode;
                    if (distance > 0xff) {
                        // switch to long jump opcode
                        opcode = OpCodes.values()[opcode.ordinal() + 1];
                    }
                    b.instr.set(i, new Instruction(opcode, distance, is.target, is.srcOffset));
                }
                p += is.opcode.length();
            }
            b = b.next;
        }
    }

    private void insertSrcOffsetTable(int srcOffset, int lastSrcOffset, ByteArrayOutputStream srcOffsets) {
        int srcDelta = srcOffset - lastSrcOffset;
        boolean negative = srcDelta < 0;
        srcDelta = Math.abs(srcDelta);
        if (srcDelta > 127) {
            while (srcDelta > 127) {
                srcOffsets.write((byte)0x80);
                srcDelta -= 0x80;
            }
            assert srcDelta >= 0;
            if (!negative) {
                srcOffsets.write((byte)srcDelta);
            } else {
                if (srcDelta == 127) {
                    srcOffsets.write((byte)0x80);
                    srcOffsets.write((byte)-1);
                } else {
                    srcDelta = -srcDelta - 1;
                    srcOffsets.write((byte)srcDelta);
                }
            }
        } else {
            if (!negative) {
                srcOffsets.write((byte)srcDelta);
            } else {
                srcOffsets.write((byte)-srcDelta);
            }
        }
    }

    private void emitBytecode(Instruction i, ByteArrayOutputStream buf) throws IllegalStateException {
        assert i.opcode.ordinal() < 256;
        buf.write((byte)i.opcode.ordinal());
        switch (i.opcode.argLength) {
            case 0:
                break;
            case 1:
                assert i.arg <= 0xff;
                buf.write((byte)i.arg);
                break;
            case 2:
                assert i.arg <= 0xffff;
                buf.write((byte) (i.arg >> 8));
                buf.write((byte) i.arg);
                break;
            default:
                throw new IllegalStateException("unsupported length");
        }
    }

    private boolean handleMarker(Instruction i, ByteArrayOutputStream buf, Stack<short[]> exceptionHandlerStack, ArrayList<short[]> finishedExceptionHandlerRanges, int stacksize) {
        if (i == Instruction.START_OF_TRY_MARKER) {
            assert buf.size() + 1 == (short)(buf.size() + 1);
            exceptionHandlerStack.push(new short[]{(short)(buf.size() + 1), -1, -1});
            return true;
        } else if (i == Instruction.START_OF_EXCEPT_MARKER) {
            short[] range = exceptionHandlerStack.pop();
            assert range[1] == -1;
            assert buf.size() + 1 == (short)(buf.size() + 1);
            range[1] = (short)(buf.size() + 1);
            assert stacksize == (short)stacksize;
            range[2] = (short)stacksize;
            finishedExceptionHandlerRanges.add(range);
            return true;
        } else if (i == Instruction.START_OF_FINALLY_MARKER) {
            short[] exceptRange = exceptionHandlerStack.pop();
            short[] finallyRange = exceptRange.clone();
            assert buf.size() + 1 == (short)(buf.size() + 1);
            if (exceptRange[1] != -1) {
                // there was an except handler which should handle any exception in the try
                // block. this range is only used directly for exceptions in try blocks themselves
                // and the else block
                finallyRange[0] = finallyRange[1];
            }
            finallyRange[1] = (short)(buf.size() + 1);
            assert stacksize == (short)stacksize;
            finallyRange[2] = (short)stacksize;
            finishedExceptionHandlerRanges.add(finallyRange);
            return true;
        } else if (i == Instruction.END_OF_FINALLY_MARKER) {
            exceptionHandlerStack.pop();
            return true;
        }
        return false;
    }

    private int calculateStackEffect(Instruction i, HashMap<Block, Integer> stackAtBlock, int stacksize) {
        // calculate stack effect
        if (i.getTarget() != null) {
            Integer sz = stackAtBlock.get(i.getTarget());
            int stacksizeAtTarget = stacksize + i.opcode.getStackEffect(i.arg, true);
            if (sz != null) {
                assert stacksizeAtTarget == sz;
            } else {
                stackAtBlock.put(i.getTarget(), stacksizeAtTarget);
            }
        }
        stacksize += i.opcode.getStackEffect(i.arg, false);
        assert stacksize >= 0;
        return stacksize;
    }

    private <T> T[] orderedKeys(HashMap<T, Integer> map, T[] template, int offset) {
        T[] ary = Arrays.copyOf(template, map.size());
        for (Map.Entry<T, Integer> e : map.entrySet()) {
            ary[e.getValue() - offset] = e.getKey();
        }
        return ary;
    }

    private <T> T[] orderedKeys(HashMap<T, Integer> map, T[] template) {
        return orderedKeys(map, template, 0);
    }

    private long[] orderedLong(HashMap<Long, Integer> map) {
        long[] ary = new long[map.size()];
        for (Map.Entry<Long, Integer> e : map.entrySet()) {
            ary[e.getValue()] = e.getKey();
        }
        return ary;
    }
}
