/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.strings.TruffleString;

public final class CompilationUnit {
    final String name;
    final String qualName;
    final HashMap<Object, Integer> constants = new HashMap<>();
    final HashMap<Long, Integer> primitiveConstants = new HashMap<>();
    final HashMap<String, Integer> names = new HashMap<>();
    final HashMap<String, Integer> varnames = new HashMap<>();
    final HashMap<String, Integer> cellvars;
    final HashMap<String, Integer> freevars;
    final int[] cell2arg;
    final int argCount;
    final int positionalOnlyArgCount;
    final int kwOnlyArgCount;
    final boolean takesVarArgs;
    final boolean takesVarKeywordArgs;

    final Block startBlock = new Block();
    final Scope scope;
    final CompilationScope scopeType;
    final String privateName;
    BlockInfo blockInfo;
    int conditionProfileCount;

    Block currentBlock = startBlock;
    int maxStackSize = 0;

    SourceRange startLocation;
    SourceRange currentLocation;

    final EnumSet<FutureFeature> futureFeatures;

    CompilationUnit(CompilationScope scopeType, Scope scope, String name, CompilationUnit parent, int scopeDepth, int argCount, int positionalOnlyArgCount, int kwOnlyArgCount, boolean takesVarArgs,
                    boolean takesVarKeywordArgs, SourceRange startLocation, EnumSet<FutureFeature> futureFeatures) {
        this.scopeType = scopeType;
        this.scope = scope;
        this.name = name;
        this.argCount = argCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.takesVarArgs = takesVarArgs;
        this.takesVarKeywordArgs = takesVarKeywordArgs;
        this.startLocation = startLocation;
        this.futureFeatures = futureFeatures;
        currentLocation = startLocation;

        if (scopeType == Class) {
            privateName = name;
        } else if (parent != null) {
            privateName = parent.privateName;
        } else {
            privateName = null;
        }
        if (scopeDepth > 1 && parent != null) {
            if (!(EnumSet.of(Function, AsyncFunction, Class).contains(scopeType) &&
                            parent.scope.getUseOfName(ScopeEnvironment.mangle(parent.privateName, name)).contains(Scope.DefUse.GlobalExplicit))) {
                String base;
                if (EnumSet.of(Function, AsyncFunction, Lambda).contains(parent.scopeType)) {
                    base = parent.qualName + ".<locals>";
                } else {
                    base = parent.qualName;
                }
                name = base + "." + name;
            }
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

        int[] cell2argValue = new int[cellvars.size()];
        boolean hasArgCell = false;
        Arrays.fill(cell2argValue, -1);
        for (String cellvar : cellvars.keySet()) {
            if (varnames.containsKey(cellvar)) {
                cell2argValue[cellvars.get(cellvar)] = varnames.get(cellvar);
                hasArgCell = true;
            }
        }
        this.cell2arg = hasArgCell ? cell2argValue : null;
        freevars = scope.getSymbolsByType(EnumSet.of(Scope.DefUse.Free, Scope.DefUse.DefFreeClass), cellvars.size());
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
        b.info = blockInfo;
        currentBlock = b;
    }

    private void addImplicitReturn() {
        Block b = startBlock;
        while (b.next != null) {
            b = b.next;
        }
        if (!b.isReturn()) {
            b.instr.add(new Instruction(OpCodes.LOAD_NONE, 0, null, null, currentLocation));
            b.instr.add(new Instruction(OpCodes.RETURN_VALUE, 0, null, null, currentLocation));
        }
    }

    public BytecodeCodeUnit assemble() {
        addImplicitReturn();
        calculateJumpInstructionArguments();

        SourceMap.Builder sourceMapBuilder = new SourceMap.Builder(startLocation.startLine, startLocation.startColumn);

        // The actual bytecodes
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        computeStackLevels();

        int varCount = varnames.size();
        List<Instruction> quickenedInstructions = new ArrayList<>();
        List<List<Instruction>> variableStores = new ArrayList<>(varCount);
        for (int i = 0; i < varCount; i++) {
            variableStores.add(new ArrayList<>());
        }
        int[] boxingMetric = new int[varCount];
        byte[] shouldUnboxVariable = new byte[varCount];
        Arrays.fill(shouldUnboxVariable, (byte) 0xff);

        SortedSet<int[]> finishedExceptionHandlerRanges = new TreeSet<>(Comparator.comparingInt(a -> a[0]));

        Block b = startBlock;
        HashMap<Block, List<Block>> handlerBlocks = new HashMap<>();
        while (b != null) {
            b.startBci = buf.size();
            int quickenMetricWeight = b.computeLoopDepth() * 3 + 1;
            BlockInfo.AbstractExceptionHandler handler = b.findExceptionHandler();
            if (handler != null) {
                handlerBlocks.computeIfAbsent(handler.exceptionHandler, (x) -> new ArrayList<>()).add(b);
            }
            if (handlerBlocks.containsKey(b)) {
                List<Block> blocks = handlerBlocks.get(b);
                Block firstBlock = blocks.get(0);
                int stackLevel = firstBlock.findExceptionHandler().tryBlock.stackLevel + b.unwindOffset;
                int start = firstBlock.startBci;
                int end = firstBlock.endBci;
                int handlerBci = buf.size();
                for (int i = 1; i < blocks.size(); i++) {
                    assert start >= 0 && end >= 0;
                    Block block = blocks.get(i);
                    if (block.startBci != end) {
                        addExceptionRange(finishedExceptionHandlerRanges, start, end, handlerBci, stackLevel);
                        start = block.startBci;
                    }
                    end = block.endBci;
                }
                addExceptionRange(finishedExceptionHandlerRanges, start, end, handlerBci, stackLevel);
            }
            for (Instruction i : b.instr) {
                if (i.quickenOutput != 0 || i.quickeningGeneralizeList != null) {
                    quickenedInstructions.add(i);
                }
                if (i.opcode == OpCodes.STORE_FAST) {
                    variableStores.get(i.arg).add(i);
                } else if (i.opcode == OpCodes.LOAD_FAST) {
                    boxingMetric[i.arg] += i.quickenOutput != 0 ? quickenMetricWeight : -quickenMetricWeight;
                }
                i.bci = buf.size();
                emitBytecode(i, buf, sourceMapBuilder);
            }
            b.endBci = buf.size();
            b = b.next;
        }

        int flags = PCode.CO_OPTIMIZED | PCode.CO_NEWLOCALS;
        flags |= takesVarArgs ? PCode.CO_VARARGS : 0;
        flags |= takesVarKeywordArgs ? PCode.CO_VARKEYWORDS : 0;
        if (scope.isNested()) {
            flags |= PCode.CO_NESTED;
        }
        if (scope.isModule()) {
            flags |= PCode.CO_GRAALPYHON_MODULE;
        }
        if (scope.isGenerator() && scope.isCoroutine()) {
            flags |= PCode.CO_ASYNC_GENERATOR;
        } else if (scope.isGenerator()) {
            flags |= PCode.CO_GENERATOR;
        } else if (scope.isCoroutine()) {
            flags |= PCode.CO_COROUTINE;
        }

        for (FutureFeature flag : futureFeatures) {
            flags |= flag.flagValue;
        }

        final int rangeElements = 4;
        int[] exceptionHandlerRanges = new int[finishedExceptionHandlerRanges.size() * rangeElements];
        int rangeIndex = 0;
        for (int[] range : finishedExceptionHandlerRanges) {
            assert range.length == rangeElements;
            System.arraycopy(range, 0, exceptionHandlerRanges, rangeIndex, rangeElements);
            rangeIndex += rangeElements;
        }

        byte[] finishedCanQuickenOutput = new byte[buf.size()];
        int[][] finishedGeneralizeInputsMap = new int[buf.size()][];
        int[][] finishedGeneralizeVarsMap = new int[varCount][];
        for (Instruction insn : quickenedInstructions) {
            int insnBodyBci = insn.bodyBci();
            finishedCanQuickenOutput[insnBodyBci] = insn.quickenOutput;
            if (insn.quickeningGeneralizeList != null && insn.quickeningGeneralizeList.size() > 0) {
                finishedGeneralizeInputsMap[insnBodyBci] = new int[insn.quickeningGeneralizeList.size()];
                for (int j = 0; j < finishedGeneralizeInputsMap[insnBodyBci].length; j++) {
                    int bci = insn.quickeningGeneralizeList.get(j).bodyBci();
                    assert bci >= 0;
                    finishedGeneralizeInputsMap[insnBodyBci][j] = bci;
                }
            }
        }
        if (cell2arg != null) {
            for (int i = 0; i < cell2arg.length; i++) {
                if (cell2arg[i] != -1 && cell2arg[i] < varCount) {
                    shouldUnboxVariable[cell2arg[i]] = 0;
                }
            }
        }
        if (!scope.isGenerator()) {
            /*
             * We do an optimization in the interpreter that we don't unbox variables that would
             * mostly get boxed again. This helps for interpreter performance, but for compiled code
             * we have to unbox all variables otherwise the compiler is not always able to prove the
             * variable was initialized. In generators, transferring the variables between the two
             * modes of usage (boxed vs unboxed) would be too complex, so we skip the optimization
             * there and unbox all variables.
             */
            for (int i = 0; i < varCount; i++) {
                List<Instruction> stores = variableStores.get(i);
                finishedGeneralizeVarsMap[i] = new int[stores.size()];
                for (int j = 0; j < stores.size(); j++) {
                    finishedGeneralizeVarsMap[i][j] = stores.get(j).bodyBci();
                }
                if (boxingMetric[i] <= 0) {
                    shouldUnboxVariable[i] = 0;
                }
            }
        }
        return new BytecodeCodeUnit(toTruffleStringUncached(name), toTruffleStringUncached(qualName),
                        argCount, kwOnlyArgCount, positionalOnlyArgCount, flags,
                        orderedKeys(names, new TruffleString[0], PythonUtils::toTruffleStringUncached), orderedKeys(varnames, new TruffleString[0], PythonUtils::toTruffleStringUncached), orderedKeys(cellvars, new TruffleString[0], PythonUtils::toTruffleStringUncached),
                        orderedKeys(freevars, new TruffleString[0], cellvars.size(), PythonUtils::toTruffleStringUncached),
                        cell2arg,
                        orderedKeys(constants, new Object[0]),
                        startLocation.startLine,
                        startLocation.startColumn,
                        startLocation.endLine,
                        startLocation.endColumn,
                        buf.toByteArray(),
                        sourceMapBuilder.build(),
                        orderedLong(primitiveConstants), exceptionHandlerRanges, maxStackSize, conditionProfileCount,
                        finishedCanQuickenOutput, shouldUnboxVariable, finishedGeneralizeInputsMap, finishedGeneralizeVarsMap);
    }

    private static void addExceptionRange(Collection<int[]> finishedExceptionHandlerRanges, int start, int end, int handler, int stackLevel) {
        if (start == end) {
            // Don't emit empty ranges. TODO don't emit the block at all if not necessary
            return;
        }
        int[] range = {start, end, handler, stackLevel};
        finishedExceptionHandlerRanges.add(range);
    }

    private static final EnumSet<OpCodes> UNCONDITIONAL_JUMP_OPCODES = EnumSet.of(OpCodes.JUMP_BACKWARD, OpCodes.JUMP_FORWARD, OpCodes.RETURN_VALUE, OpCodes.RAISE_VARARGS, OpCodes.END_EXC_HANDLER);

    private void computeStackLevels() {
        Deque<Block> todo = new ArrayDeque<>();
        todo.add(startBlock);
        startBlock.stackLevel = 0;
        while (!todo.isEmpty()) {
            Block block = todo.pop();
            int level = block.stackLevel;
            assert level >= 0;
            maxStackSize = Math.max(maxStackSize, level);
            BlockInfo.AbstractExceptionHandler handler = block.findExceptionHandler();
            if (handler != null) {
                assert handler.tryBlock.stackLevel != -1;
                int handlerLevel = handler.tryBlock.stackLevel + handler.exceptionHandler.unwindOffset + 1;
                computeStackLevels(handler.exceptionHandler, handlerLevel, todo);
            }
            boolean fallthrough = true;
            for (Instruction i : block.instr) {
                Block target = i.target;
                if (target != null) {
                    int jumpLevel = level + i.opcode.getStackEffect(i.arg, i.followingArgs, true);
                    computeStackLevels(target, jumpLevel, todo);
                }
                if (UNCONDITIONAL_JUMP_OPCODES.contains(i.opcode)) {
                    assert i.opcode != OpCodes.RETURN_VALUE || level == 1;
                    fallthrough = false;
                    break;
                }
                level += i.opcode.getStackEffect(i.arg, i.followingArgs, false);
                assert level >= 0;
                maxStackSize = Math.max(maxStackSize, level);
            }
            if (fallthrough && block.next != null) {
                computeStackLevels(block.next, level, todo);
            }
        }
    }

    private static void computeStackLevels(Block block, int level, Deque<Block> todo) {
        if (block.stackLevel == -1) {
            block.stackLevel = level;
            todo.push(block);
        } else {
            assert block.stackLevel == level;
        }
    }

    private void calculateJumpInstructionArguments() {
        HashMap<Block, Integer> blockLocationMap = new HashMap<>();
        boolean repeat;
        do {
            repeat = false;
            int bci = 0;
            Block block = startBlock;
            while (block != null) {
                blockLocationMap.put(block, bci);
                for (Instruction i : block.instr) {
                    bci += i.extendedLength();
                }
                block = block.next;
            }

            bci = 0;
            block = startBlock;
            while (block != null) {
                for (int i = 0; i < block.instr.size(); i++) {
                    Instruction instr = block.instr.get(i);
                    Block target = instr.target;
                    if (target != null) {
                        int targetPos = blockLocationMap.get(target);
                        int distance = Math.abs(bci + instr.extensions() * 2 - targetPos);
                        int prevLength = instr.extendedLength();
                        instr.arg = distance;
                        if (instr.extendedLength() != prevLength) {
                            repeat = true;
                        }
                    }
                    bci += instr.extendedLength();
                }
                block = block.next;
            }
        } while (repeat);
    }

    private static void emitBytecode(Instruction instr, ByteArrayOutputStream buf, SourceMap.Builder sourceMapBuilder) throws IllegalStateException {
        OpCodes opcode = instr.opcode;
        // Pre-quicken constant loads
        if (opcode == OpCodes.LOAD_BYTE) {
            opcode = (instr.quickenOutput & QuickeningTypes.INT) != 0 ? OpCodes.LOAD_BYTE_I : OpCodes.LOAD_BYTE_O;
        } else if (opcode == OpCodes.LOAD_INT) {
            opcode = (instr.quickenOutput & QuickeningTypes.INT) != 0 ? OpCodes.LOAD_INT_I : OpCodes.LOAD_INT_O;
        } else if (opcode == OpCodes.LOAD_LONG) {
            opcode = (instr.quickenOutput & QuickeningTypes.LONG) != 0 ? OpCodes.LOAD_LONG_L : OpCodes.LOAD_LONG_O;
        } else if (opcode == OpCodes.LOAD_DOUBLE) {
            opcode = (instr.quickenOutput & QuickeningTypes.DOUBLE) != 0 ? OpCodes.LOAD_DOUBLE_D : OpCodes.LOAD_DOUBLE_O;
        } else if (opcode == OpCodes.LOAD_TRUE) {
            opcode = (instr.quickenOutput & QuickeningTypes.BOOLEAN) != 0 ? OpCodes.LOAD_TRUE_B : OpCodes.LOAD_TRUE_O;
        } else if (opcode == OpCodes.LOAD_FALSE) {
            opcode = (instr.quickenOutput & QuickeningTypes.BOOLEAN) != 0 ? OpCodes.LOAD_FALSE_B : OpCodes.LOAD_FALSE_O;
        }
        assert opcode.ordinal() < 256;
        SourceRange location = instr.location;
        sourceMapBuilder.appendLocation(location.startLine, location.startColumn, location.endLine, location.endColumn);
        if (!opcode.hasArg()) {
            buf.write(opcode.ordinal());
        } else {
            int oparg = instr.arg;
            for (int i = instr.extensions(); i >= 1; i--) {
                buf.write(OpCodes.EXTENDED_ARG.ordinal());
                buf.write((oparg >> (i * 8)) & 0xFF);
                sourceMapBuilder.appendLocation(location.startLine, location.startColumn, location.endLine, location.endColumn);
            }
            buf.write(opcode.ordinal());
            buf.write(oparg & 0xFF);
            if (opcode.argLength > 1) {
                assert instr.followingArgs.length == opcode.argLength - 1;
                for (int i = 0; i < instr.followingArgs.length; i++) {
                    buf.write(instr.followingArgs[i]);
                }
            }
        }
    }

    private static <T, U> U[] orderedKeys(HashMap<T, Integer> map, U[] template, int offset, com.oracle.graal.python.util.Function<T, U> convertor) {
        U[] ary = Arrays.copyOf(template, map.size());
        for (Map.Entry<T, Integer> e : map.entrySet()) {
            ary[e.getValue() - offset] = convertor.apply(e.getKey());
        }
        return ary;
    }

    private static <T> T[] orderedKeys(HashMap<T, Integer> map, T[] template) {
        return orderedKeys(map, template, 0, i -> i);
    }

    private static <T, U> U[] orderedKeys(HashMap<T, Integer> map, U[] template, com.oracle.graal.python.util.Function<T, U> convertor) {
        return orderedKeys(map, template, 0, convertor);
    }

    private static long[] orderedLong(HashMap<Long, Integer> map) {
        long[] ary = new long[map.size()];
        for (Map.Entry<Long, Integer> e : map.entrySet()) {
            ary[e.getValue()] = e.getKey();
        }
        return ary;
    }

    void pushBlock(BlockInfo info) {
        info.outer = blockInfo;
        blockInfo = info;
    }

    void popBlock() {
        blockInfo = blockInfo.outer;
    }
}
