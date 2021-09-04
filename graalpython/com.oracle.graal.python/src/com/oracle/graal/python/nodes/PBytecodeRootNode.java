/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.BuiltinNames.__BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgsNodeGen;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.AddNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.FloorDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.LShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MatMulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.ModNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.PowNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.RShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.SubNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.TrueDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.AddNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitAndNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitOrNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitXorNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.FloorDivNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.LShiftNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.MatMulNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.ModNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.MulNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.PowNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.RShiftNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.SubNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.TrueDivNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.InvertNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.NegNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.PosNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.InvertNodeGen;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.NegNodeGen;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.PosNodeGen;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode.ImportName;
import com.oracle.graal.python.nodes.statement.ExceptNode.ExceptMatchNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import java.util.Arrays;

public final class PBytecodeRootNode extends PRootNode implements BytecodeOSRNode {

    private final int stacksize;
    private final Signature signature;
    private final String name;
    public final String filename;
    public final int firstlineno;

    @CompilationFinal(dimensions = 1) private final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final String[] names;
    @CompilationFinal(dimensions = 1) private final String[] varnames;
    @CompilationFinal(dimensions = 1) private final String[] freevars;
    @CompilationFinal(dimensions = 1) private final String[] cellvars;

    /*
     * Our approach to exceptions is as follows: in the interpreter, it pretty much works as in
     * CPython, using the current blockstack. However, that blockstack cannot easily be made PE
     * constant including its contents. When we encounter an exception, we save the current
     * blockstack into the exceptionBlockStacks; we also record a mapping from the current bci to
     * this exception blockstack. During PE, we will look at this and thus be able to know which
     * blockstack to use when an exception occurs at a certain point.
     *
     * There are some potential pitfalls with this approach. First, the mapping may grow quite
     * large - in the worst case, we might be saving the blockstack for each bci. Second, while the
     * Python compiler will not generate code (tfel: I think!) that would end with different
     * blockstacks for the same bci depending on execution path, artificially created bytecode
     * might, and in that case, we'd produce wrong results. We could add assertions to at least
     * catch this properly.
     */
    @CompilationFinal(dimensions = 4) private Object[][][][] exceptionBlockStackIndices = null;
    @CompilationFinal(dimensions = 1) private short[] shortExceptionBlockStackIndices = new short[32];
    @CompilationFinal(dimensions = 2) private long[][] exceptionBlockStacks = new long[0][];

    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    @CompilationFinal private Object osrMetadata;

    private static final FrameDescriptor DESCRIPTOR = new FrameDescriptor();
    private static final FrameSlot STACK_SLOT = DESCRIPTOR.addFrameSlot("stack", FrameSlotKind.Object);
    private static final FrameSlot FAST_SLOT = DESCRIPTOR.addFrameSlot("fast", FrameSlotKind.Object);
    private static final FrameSlot CELL_SLOT = DESCRIPTOR.addFrameSlot("cell", FrameSlotKind.Object);
    private static final FrameSlot FREE_SLOT = DESCRIPTOR.addFrameSlot("free", FrameSlotKind.Object);
    private static final FrameSlot BLOCKSTACK_SLOT = DESCRIPTOR.addFrameSlot("blockstack", FrameSlotKind.Object);
    private static final int MAXBLOCKS = 15; // 25% less than on CPython, shouldn't matter much

    private static final Object UNREIFIED_EXC_TYPE = new Object();
    private static final Object UNREIFIED_EXC_VALUE = new Object();
    private static final Object UNREIFIED_EXC_TRACEBACK = new Object();

    public PBytecodeRootNode(TruffleLanguage<?> language, Signature sign, byte[] bc,
                    String filename, String name, int firstlineno,
                    Object[] consts, String[] names, String[] varnames, String[] freevars, String[] cellvars,
                    int stacksize) {
        super(language, DESCRIPTOR);
        this.signature = sign;
        this.bytecode = bc;
        this.adoptedNodes = new Node[bc.length];
        this.consts = consts;
        this.names = names;
        this.varnames = varnames;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.stacksize = stacksize;
        this.filename = filename;
        this.name = name;
        this.firstlineno = firstlineno;
        assert stacksize < Math.pow(2, 12) : "stacksize cannot be larger than 12-bit range";
        assert bytecode.length < Math.pow(2, 16) : "bytecode cannot be longer than 16-bit range";
    }

    public PBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor fd, Signature sign, byte[] bc,
                    String filename, String name, int firstlineno,
                    Object[] consts, String[] names, String[] varnames, String[] freevars, String[] cellvars,
                    int stacksize) {
        super(language, fd);
        this.signature = sign;
        this.bytecode = bc;
        this.adoptedNodes = new Node[bc.length];
        this.consts = consts;
        this.names = names;
        this.varnames = varnames;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.stacksize = stacksize;
        this.filename = filename;
        this.name = name;
        this.firstlineno = firstlineno;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "<bytecode " + name + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public boolean isPythonInternal() {
        return false;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        PythonContext context = PythonContext.get(this);
        calleeContext.enter(frame);
        try {
            // CPython has an array of object called "localsplus" with everything. We use separate
            // arrays.
            Object[] stack = new Object[stacksize];
            long[] handlerBlocks = new long[MAXBLOCKS];
            Object[] args = frame.getArguments();
            Object[] fastlocals = new Object[varnames.length];
            System.arraycopy(args, PArguments.USER_ARGUMENTS_OFFSET, fastlocals, 0, PArguments.getUserArgumentLength(args));
            int varargsIdx = signature.getVarargsIdx();
            if (varargsIdx >= 0) {
                fastlocals[varargsIdx] = factory.createList(PArguments.getVariableArguments(args));
            }
            if (signature.takesVarKeywordArgs()) {
                int idx = signature.getParameterIds().length + signature.getKeywordNames().length;
                if (varargsIdx >= 0) {
                    idx += 1;
                }
                fastlocals[idx] = factory.createDict(PArguments.getKeywordArguments(args));
            }
            Object[] celllocals = new Object[cellvars.length];
            Object[] freelocals = new Object[freevars.length];

            frame.setObject(STACK_SLOT, stack);
            frame.setObject(FAST_SLOT, fastlocals);
            frame.setObject(CELL_SLOT, celllocals);
            frame.setObject(FREE_SLOT, freelocals);
            frame.setObject(BLOCKSTACK_SLOT, handlerBlocks);

            return executeOSR(frame, encodeBCI(0) | encodeStackTop(-1) | encodeBlockstackTop(-1), args);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Supplier<T> nodeSupplier, int bytecodeIndex) {
        CompilerAsserts.partialEvaluationConstant(bytecodeIndex);
        T node = (T) adoptedNodes[bytecodeIndex];
        CompilerAsserts.partialEvaluationConstant(node);
        if (node != null) {
            return node;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            T newNode = nodeSupplier.get();
            adoptedNodes[bytecodeIndex] = insert(newNode);
            return newNode;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Function<String, T> nodeSupplier, int bytecodeIndex, String argument) {
        CompilerAsserts.partialEvaluationConstant(bytecodeIndex);
        T node = (T) adoptedNodes[bytecodeIndex];
        CompilerAsserts.partialEvaluationConstant(node);
        if (node != null) {
            return node;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            T newNode = nodeSupplier.apply(argument);
            adoptedNodes[bytecodeIndex] = insert(newNode);
            return newNode;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Function<Integer, T> nodeSupplier, int bytecodeIndex, int argument) {
        CompilerAsserts.partialEvaluationConstant(bytecodeIndex);
        T node = (T) adoptedNodes[bytecodeIndex];
        CompilerAsserts.partialEvaluationConstant(node);
        if (node != null) {
            return node;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            T newNode = nodeSupplier.apply(argument);
            adoptedNodes[bytecodeIndex] = insert(newNode);
            return newNode;
        }
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    @Override
    public void copyIntoOSRFrame(VirtualFrame frame, VirtualFrame parentFrame, int target) {
        Object[] stack = new Object[stacksize];
        Object[] fast = new Object[varnames.length];
        Object[] cell = new Object[cellvars.length];
        Object[] free = new Object[freevars.length];
        long[] blockstack = new long[MAXBLOCKS];
        frame.setObject(STACK_SLOT, stack);
        frame.setObject(FAST_SLOT, fast);
        frame.setObject(CELL_SLOT, cell);
        frame.setObject(FREE_SLOT, free);
        frame.setObject(BLOCKSTACK_SLOT, blockstack);
        try {
            copyStack(stack, (Object[])parentFrame.getObject(STACK_SLOT));
            copyLocals(fast, (Object[])parentFrame.getObject(FAST_SLOT));
            copyCellvars(cell, (Object[])parentFrame.getObject(CELL_SLOT));
            copyFreevars(free, (Object[])parentFrame.getObject(FREE_SLOT));
            copyBlocks(blockstack, (long[])parentFrame.getObject(BLOCKSTACK_SLOT));
        } catch (FrameSlotTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        try {
            Object[] stack = (Object[])parentFrame.getObject(STACK_SLOT);
            Object[] fast = (Object[])parentFrame.getObject(FAST_SLOT);
            Object[] cell = (Object[])parentFrame.getObject(CELL_SLOT);
            Object[] free = (Object[])parentFrame.getObject(FREE_SLOT);
            long[] blockstack = (long[])parentFrame.getObject(BLOCKSTACK_SLOT);
            Object[] osrStack = (Object[])osrFrame.getObject(STACK_SLOT);
            Object[] osrFast = (Object[])osrFrame.getObject(FAST_SLOT);
            Object[] osrCell = (Object[])osrFrame.getObject(CELL_SLOT);
            Object[] osrFree = (Object[])osrFrame.getObject(FREE_SLOT);
            long[] osrBlockstack = (long[])osrFrame.getObject(BLOCKSTACK_SLOT);
            copyStack(stack, osrStack);
            copyLocals(fast, osrFast);
            copyCellvars(cell, osrCell);
            copyFreevars(free, osrFree);
            copyBlocks(blockstack, osrBlockstack);
        } catch (FrameSlotTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @ExplodeLoop
    private final void copyStack(Object[] dst, Object[] src) {
        for (int i = 0; i < stacksize; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private final void copyLocals(Object[] dst, Object[] src) {
        for (int i = 0; i < varnames.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private final void copyCellvars(Object[] dst, Object[] src) {
        for (int i = 0; i < cellvars.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private final void copyFreevars(Object[] dst, Object[] src) {
        for (int i = 0; i < freevars.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private final void copyBlocks(long[] dst, long[] src) {
        for (int i = 0; i < MAXBLOCKS; i++) {
            dst[i] = src[i];
        }
    }

    private static final int decodeBCI(int target) {
        return (target >> 16) & 0xffff; // unsigned
    }

    private static final int decodeStackTop(int target) {
        return ((target >> 4) & 0xfff) - 1;
    }

    private static final int decodeBlockstackTop(int target) {
        return ((target & 0xf) - 1);
    }

    private static final int encodeBCI(int bci) {
        return bci << 16;
    }

    private static final int encodeStackTop(int stackTop) {
        return (stackTop + 1) << 4;
    }

    private static final int encodeBlockstackTop(int blockstackTop) {
        return blockstackTop + 1;
    }

    /**
     * @param target - encodes bci (16bit), stackTop (12bit), and blockstackTop (4bit)
     */
    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object executeOSR(VirtualFrame frame, int target, Object originalArgs) {
        PythonContext context = PythonContext.get(this);
        PythonModule builtins = context.getBuiltins();

        Object globals = PArguments.getGlobals((Object[])originalArgs);
        // Object locals = PArguments.getCustomLocals(frame); // TODO: deal with custom locals
        Object[] stack = (Object[])FrameUtil.getObjectSafe(frame, STACK_SLOT);
        Object[] fastlocals = (Object[])FrameUtil.getObjectSafe(frame, FAST_SLOT);
        Object[] celllocals = (Object[])FrameUtil.getObjectSafe(frame, CELL_SLOT);
        Object[] freelocals = (Object[])FrameUtil.getObjectSafe(frame, FREE_SLOT);
        long[] blockstack = (long[])FrameUtil.getObjectSafe(frame, BLOCKSTACK_SLOT);

        int loopCount = 0;
        int stackTop = decodeStackTop(target);
        int blockstackTop = decodeBlockstackTop(target);
        int bci = decodeBCI(target);
        int oparg = Byte.toUnsignedInt(bytecode[bci + 1]);

        CompilerAsserts.partialEvaluationConstant(bytecode);
        CompilerAsserts.partialEvaluationConstant(blockstackTop);
        CompilerAsserts.partialEvaluationConstant(target);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(oparg);

        while (true) {
            final byte bc = bytecode[bci];

            CompilerAsserts.partialEvaluationConstant(bc);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(stackTop);
            CompilerAsserts.partialEvaluationConstant(blockstackTop);
            CompilerDirectives.ensureVirtualized(stack);
            CompilerDirectives.ensureVirtualized(fastlocals);
            CompilerDirectives.ensureVirtualized(celllocals);
            CompilerDirectives.ensureVirtualized(freelocals);
            CompilerDirectives.ensureVirtualized(blockstack);

            try {
                switch (bc) {
                    case NOP:
                        break;
                    case LOAD_FAST:
                        {
                            Object value = fastlocals[oparg];
                            if (value == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                bytecode[bci] = LOAD_FAST_WITH_ERROR;
                                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            }
                            stack[++stackTop] = value;
                        }
                        break;
                    case LOAD_FAST_WITH_ERROR:
                        {
                            PRaiseNode raiseNode = insertChildNode(() -> PRaiseNode.create(), bci);
                            Object value = fastlocals[oparg];
                            if (value == null) {
                                throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            }
                            stack[++stackTop] = value;
                        }
                        break;
                    case LOAD_CONST:
                        stack[++stackTop] = consts[oparg];
                        break;
                    case STORE_FAST:
                        fastlocals[oparg] = stack[stackTop];
                        stack[stackTop--] = null;
                        break;
                    case POP_TOP:
                        stack[stackTop--] = null;
                        break;
                    case ROT_TWO:
                        {
                            Object top = stack[stackTop];
                            stack[stackTop] = stack[stackTop - 1];
                            stack[stackTop - 1] = top;
                            break;
                        }
                    case ROT_THREE:
                        {
                            Object top = stack[stackTop];
                            stack[stackTop] = stack[stackTop - 1];
                            stack[stackTop - 1] = stack[stackTop - 2];
                            stack[stackTop - 2] = top;
                            break;
                        }
                    case ROT_FOUR:
                        {
                            Object top = stack[stackTop];
                            stack[stackTop] = stack[stackTop - 1];
                            stack[stackTop - 1] = stack[stackTop - 2];
                            stack[stackTop - 2] = stack[stackTop - 3];
                            stack[stackTop - 3] = top;
                            break;
                        }
                    case DUP_TOP:
                        stack[stackTop + 1] = stack[stackTop];
                        stackTop++;
                        break;
                    case DUP_TOP_TWO:
                        stack[stackTop + 2] = stack[stackTop];
                        stack[stackTop + 1] = stack[stackTop - 1];
                        stackTop += 2;
                        break;
                    case UNARY_POSITIVE:
                        {
                            PosNode posNode = insertChildNode(() -> PosNodeGen.create(null), bci);
                            stack[stackTop] = posNode.execute(frame, stack[stackTop]);
                        }
                        break;
                    case UNARY_NEGATIVE:
                        {
                            NegNode negNode = insertChildNode(() -> NegNodeGen.create(null), bci);
                            stack[stackTop] = negNode.execute(frame, stack[stackTop]);
                        }
                        break;
                    case UNARY_NOT:
                        stack[stackTop] = PyObjectIsTrueNode.getUncached().execute(frame, stack[stackTop]);
                        break;
                    case UNARY_INVERT:
                        {
                            InvertNode invertNode = insertChildNode(() -> InvertNodeGen.create(null), bci);
                            stack[stackTop] = invertNode.execute(frame, stack[stackTop]);
                        }
                        break;
                    case BINARY_POWER:
                        {
                            PowNode powNode = insertChildNode(() -> PowNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = powNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_MULTIPLY:
                        {
                            MulNode mulNode = insertChildNode(() -> MulNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = mulNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_MATRIX_MULTIPLY:
                        {
                            MatMulNode matMulNode = insertChildNode(() -> MatMulNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = matMulNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_TRUE_DIVIDE:
                        {
                            TrueDivNode trueDivNode = insertChildNode(() -> TrueDivNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = trueDivNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_FLOOR_DIVIDE:
                        {
                            FloorDivNode floorDivNode = insertChildNode(() -> FloorDivNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = floorDivNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_MODULO:
                        {
                            ModNode modNode = insertChildNode(() -> ModNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = modNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_ADD:
                        {
                            AddNode addNode = insertChildNode(() -> AddNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = addNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_SUBTRACT:
                        {
                            SubNode subNode = insertChildNode(() -> SubNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = subNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_SUBSCR:
                        {
                            GetItemNode getItemNode = insertChildNode(() -> GetItemNode.create(), bci);
                            Object slice = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = getItemNode.execute(frame, stack[stackTop], slice);
                        }
                        break;
                    case BINARY_LSHIFT:
                        {
                            LShiftNode lShiftNode = insertChildNode(() -> LShiftNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = lShiftNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_RSHIFT:
                        {
                            RShiftNode rShiftNode = insertChildNode(() -> RShiftNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = rShiftNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_AND:
                        {
                            BitAndNode bitAndNode = insertChildNode(() -> BitAndNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = bitAndNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_XOR:
                        {
                            BitXorNode bitXorNode = insertChildNode(() -> BitXorNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = bitXorNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case BINARY_OR:
                        {
                            BitOrNode bitOrNode = insertChildNode(() -> BitOrNodeGen.create(null, null), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            stack[stackTop] = bitOrNode.executeObject(frame, stack[stackTop], right);
                        }
                        break;
                    case LIST_APPEND:
                        {
                            PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), bci);
                            Object list = stack[stackTop - oparg];
                            Object value = stack[stackTop];
                            stack[stackTop--] = null;
                            callNode.execute(frame, list, "append", value);
                        }
                        break;
                    case SET_ADD:
                        {
                            PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), bci);
                            Object value = stack[stackTop];
                            stack[stackTop--] = null;
                            callNode.execute(frame, stack[stackTop], "add", value);
                        }
                        break;
                    case INPLACE_POWER:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IPow.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_MULTIPLY:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IMul.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_MATRIX_MULTIPLY:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IMatMul.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_TRUE_DIVIDE:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.ITrueDiv.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_FLOOR_DIVIDE:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IFloorDiv.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_MODULO:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IMod.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_ADD:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IAdd.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_SUBTRACT:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.ISub.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_LSHIFT:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.ILShift.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_RSHIFT:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IRShift.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_AND:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IAnd.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_XOR:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IXor.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case INPLACE_OR:
                        {
                            LookupAndCallInplaceNode opNode = insertChildNode(() -> InplaceArithmetic.IOr.create(), bci);
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            stack[stackTop] = opNode.execute(frame, left, right);
                        }
                        break;
                    case STORE_SUBSCR:
                        {
                            PyObjectSetItem setItem = insertChildNode(() -> PyObjectSetItem.create(), bci);
                            Object index = stack[stackTop];
                            stack[stackTop--] = null;
                            Object container = stack[stackTop];
                            stack[stackTop--] = null;
                            Object value = stack[stackTop];
                            stack[stackTop--] = null;
                            setItem.execute(frame, container, index, value);
                        }
                        break;
                    case DELETE_SUBSCR:
                        throw new RuntimeException("DELETE_SUBSCR");
                    case PRINT_EXPR:
                        throw new RuntimeException("PRINT_EXPR");
                    case RAISE_VARARGS:
                        {
                            RaiseNode raiseNode = insertChildNode(() -> RaiseNode.create(null, null), bci);
                            int arg = oparg;
                            Object cause;
                            Object exception;
                            if (arg > 1) {
                                cause = stack[stackTop];
                                stack[stackTop--] = null;
                            } else {
                                cause = PNone.NO_VALUE;
                            }
                            if (arg > 0) {
                                exception = stack[stackTop];
                                stack[stackTop--] = null;
                            } else {
                                exception = PNone.NO_VALUE;
                            }
                            raiseNode.execute(frame, exception, cause);
                        }
                        break;
                    case RETURN_VALUE:
                        if (CompilerDirectives.inInterpreter()) {
                            LoopNode.reportLoopCount(this, loopCount);
                        }
                        return stack[stackTop];
                    case GET_AITER:
                    case GET_ANEXT:
                    case GET_AWAITABLE:
                        throw new RuntimeException("async bytecodes");
                    case YIELD_FROM:
                    case YIELD_VALUE:
                        throw new RuntimeException("yield bytecodes");
                    case POP_EXCEPT:
                        {
                            assert EXCEPT_HANDLER == (blockstack[blockstackTop] >> 32);
                            blockstackTop--;
                            // pop the previous exception info (probably wasn't even materialized)
                            stack[stackTop--] = null;
                            stack[stackTop--] = null;
                            stack[stackTop--] = null;
                        }
                        break;
                    case POP_BLOCK:
                        blockstackTop--;
                        break;
                    case POP_FINALLY:
                        {
                            // very similar to END_FINALLY, but with an argument
                            Object result;
                            if (oparg == 0) {
                                result = null;
                            } else {
                                result = stack[stackTop];
                                stack[stackTop--] = null;
                            }
                            Object exc = stack[stackTop];
                            stack[stackTop--] = null;
                            if (exc == null || exc instanceof Integer) {
                                // nothing to do
                            } else {
                                // first, pop the remaining two current exc_info entries
                                stack[stackTop--] = null;
                                stack[stackTop--] = null;
                                assert EXCEPT_HANDLER == (blockstack[blockstackTop] >> 32);
                                assert stackTop == (short)blockstack[blockstackTop] + 3;
                                blockstackTop--;
                                // just pop the previously handled exception also, since we can
                                // recover it differently than CPython (I think...)
                                stack[stackTop--] = null;
                                stack[stackTop--] = null;
                                stack[stackTop--] = null;
                            }
                            if (oparg != 0) {
                                stack[++stackTop] = result;
                            }
                        }
                        break;
                    case CALL_FINALLY:
                        stack[++stackTop] = bci + 2;
                        bci = oparg + 2;
                        oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                        continue;
                    case BEGIN_FINALLY:
                        stack[++stackTop] = null;
                        break;
                    case END_FINALLY:
                        {
                            Object exc = stack[stackTop];
                            stack[stackTop--] = null;
                            if (exc == null) {
                                // nothing, we just fall through
                            } else if (exc instanceof Integer) {
                                bci = (int)exc;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            } else {
                                // CPython expects 6 values, the current exc_info and the previous one
                                // We usually just have placeholders, with a PException object in stackTop

                                // first, pop the remaining two current exc_info entries
                                stack[stackTop--] = null;
                                stack[stackTop--] = null;
                                // throw the exception again for the next handler to run
                                throw (AbstractTruffleException)exc;
                            }
                        }
                        break;
                    case END_ASYNC_FOR:
                        throw new RuntimeException("async bytecodes");
                    case LOAD_BUILD_CLASS:
                        {
                            ReadGlobalOrBuiltinNode read = insertChildNode(() -> ReadGlobalOrBuiltinNode.create(__BUILD_CLASS__), bci);
                            stack[++stackTop] = read.executeWithGlobals(frame, globals);
                        }
                        break;
                    case STORE_NAME:
                        {
                            String varname = names[oparg];
                            WriteGlobalNode writeGlobalNode = insertChildNode((a) -> WriteGlobalNode.create(a), bci, varname);
                            writeGlobalNode.executeObjectWithGlobals(frame, globals, stack[stackTop]);
                            stack[stackTop--] = null;
                        }
                        break;
                    case DELETE_NAME:
                        {
                            String varname = names[oparg];
                            DeleteGlobalNode deleteGlobalNode = insertChildNode((a) -> DeleteGlobalNode.create(a), bci, varname);
                            deleteGlobalNode.executeWithGlobals(frame, globals);
                        }
                        break;
                    case UNPACK_SEQUENCE:
                    case UNPACK_EX:
                        throw new RuntimeException("unpack bytecodes");
                    case STORE_ATTR:
                        {
                            String varname = names[oparg];
                            Object owner = stack[stackTop];
                            stack[stackTop--] = null;
                            Object value = stack[stackTop];
                            stack[stackTop--] = null;
                            throw new RuntimeException("store attr");
                        }
                    case DELETE_ATTR:
                        throw new RuntimeException("delete attr");
                    case STORE_GLOBAL:
                    case DELETE_GLOBAL:
                        throw new RuntimeException("global writes");
                    case LOAD_NAME: // TODO: check custom locals first
                    case LOAD_GLOBAL:
                        {
                            String varname = names[oparg];
                            ReadGlobalOrBuiltinNode read = insertChildNode((a) -> ReadGlobalOrBuiltinNode.create(a), bci, varname);
                            stack[++stackTop] = read.executeWithGlobals(frame, globals);
                        }
                        break;
                    case DELETE_FAST:
                    case DELETE_DEREF:
                        throw new RuntimeException("delete locals");
                    case LOAD_CLOSURE:
                        throw new RuntimeException("LOAD_CLOSURE");
                    case LOAD_CLASSDEREF:
                        throw new RuntimeException("LOAD_CLASSDEREF");
                    case LOAD_DEREF:
                    case STORE_DEREF:
                        throw new RuntimeException("deref load/store");
                    case BUILD_STRING:
                        throw new RuntimeException("build string");
                    case BUILD_TUPLE:
                        throw new RuntimeException("build tuple");
                    case BUILD_LIST:
                        {
                            Object[] list = new Object[oparg];
                            while (oparg > 0) {
                                oparg--;
                                list[oparg] = stack[stackTop];
                                stack[stackTop--] = null;
                            }
                            stack[++stackTop] = factory.createList(list);
                        }
                        break;
                    case BUILD_TUPLE_UNPACK_WITH_CALL:
                    case BUILD_TUPLE_UNPACK:
                    case BUILD_LIST_UNPACK:
                    case BUILD_SET:
                    case BUILD_SET_UNPACK:
                    case BUILD_MAP:
                    case SETUP_ANNOTATIONS:
                    case BUILD_CONST_KEY_MAP:
                    case BUILD_MAP_UNPACK:
                    case BUILD_MAP_UNPACK_WITH_CALL:
                        throw new RuntimeException("build bytecodes");
                    case MAP_ADD:
                        throw new RuntimeException("MAP_ADD");
                    case LOAD_ATTR:
                        {
                            PyObjectGetAttr getAttr = insertChildNode(() -> PyObjectGetAttr.create(), bci);
                            String varname = names[oparg];
                            Object owner = stack[stackTop];
                            Object value = getAttr.execute(frame, owner, varname);
                            stack[stackTop] = value;
                        }
                        break;
                    case COMPARE_OP:
                        {
                            Object right = stack[stackTop];
                            stack[stackTop--] = null;
                            Object left = stack[stackTop];
                            Node opNode = insertChildNode((op) -> {
                                switch (op) {
                                    case 0:
                                        return BinaryComparisonNode.LtNode.create();
                                    case 1:
                                        return BinaryComparisonNode.LeNode.create();
                                    case 2:
                                        return BinaryComparisonNode.EqNode.create();
                                    case 3:
                                        return BinaryComparisonNode.NeNode.create();
                                    case 4:
                                        return BinaryComparisonNode.GtNode.create();
                                    case 5:
                                        return BinaryComparisonNode.GeNode.create();
                                    case 6:
                                        return ContainsNode.create();
                                    case 7:
                                        return ContainsNode.create();
                                    case 8:
                                        return IsNode.create();
                                    case 9:
                                        return IsNode.create();
                                    case 10:
                                        return ExceptMatchNode.create();
                                    default: throw CompilerDirectives.shouldNotReachHere();
                                }
                            }, bci, oparg);
                            if (opNode instanceof BinaryComparisonNode) {
                                stack[stackTop] = ((BinaryComparisonNode) opNode).executeObject(frame, left, right);
                            } else if (opNode instanceof ContainsNode) {
                                Object result = ((ContainsNode) opNode).executeObject(frame, left, right);
                                if (oparg == 7) {
                                    CoerceToBooleanNode invert = insertChildNode(() -> CoerceToBooleanNode.createIfFalseNode(), bci + 1);
                                    stack[stackTop] = invert.execute(frame, result);
                                } else {
                                    stack[stackTop] = result;
                                }
                            } else if (opNode instanceof IsNode) {
                                Object result = ((IsNode) opNode).execute(left, right);
                                if (oparg == 9) {
                                    CoerceToBooleanNode invert = insertChildNode(() -> CoerceToBooleanNode.createIfFalseNode(), bci + 1);
                                    stack[stackTop] = invert.execute(frame, result);
                                } else {
                                    stack[stackTop] = result;
                                }
                            } else if (opNode instanceof ExceptMatchNode) {
                                // top of stack (bci.e., right) is the clause, below is the exception
                                stack[stackTop] = ((ExceptMatchNode) opNode).executeMatch(frame, left, right);
                            } else {
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                        }
                        break;
                    case IMPORT_NAME:
                        {
                            CastToJavaIntExactNode castNode = insertChildNode(() -> CastToJavaIntExactNode.create(), bci);
                            String modname = names[oparg];
                            Object fromlist = stack[stackTop];
                            stack[stackTop--] = null;
                            String[] fromlistArg;
                            if (fromlist == PNone.NONE) {
                                fromlistArg = PythonUtils.EMPTY_STRING_ARRAY;
                            } else {
                                // import statement won't be dynamically created, so the fromlist is always
                                // from a LOAD_CONST, which will either be a tuple of strings or None.
                                assert fromlist instanceof PTuple;
                                Object[] list = ((PTuple) fromlist).getSequenceStorage().getInternalArray();
                                fromlistArg = (String[]) list;
                            }
                            int level = castNode.execute(stack[stackTop]);
                            ImportName importNode = insertChildNode(() -> ImportName.create(), bci + 1);
                            Object result = importNode.execute(frame, context, builtins, modname, globals, fromlistArg, level);
                            stack[stackTop] = result;
                        }
                        break;
                    case IMPORT_STAR:
                    case IMPORT_FROM:
                        throw new RuntimeException("import start / import from");
                    case JUMP_FORWARD:
                        bci += oparg;
                        oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                        continue;
                    case POP_JUMP_IF_FALSE:
                        {
                            PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), bci);
                            Object cond = stack[stackTop];
                            stack[stackTop--] = null;
                            if (!isTrue.execute(frame, cond)) {
                                bci = oparg;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            }
                        }
                        break;
                    case POP_JUMP_IF_TRUE:
                        {
                            PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), bci);
                            Object cond = stack[stackTop];
                            stack[stackTop--] = null;
                            if (isTrue.execute(frame, cond)) {
                                bci = oparg;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            }
                        }
                        break;
                    case JUMP_IF_FALSE_OR_POP:
                        {
                            PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), bci);
                            Object cond = stack[stackTop];
                            if (!isTrue.execute(frame, cond)) {
                                bci = oparg;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            } else {
                                stack[stackTop--] = null;
                            }
                        }
                        break;
                    case JUMP_IF_TRUE_OR_POP:
                        {
                            PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), bci);
                            Object cond = stack[stackTop];
                            if (isTrue.execute(frame, cond)) {
                                bci = oparg;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            } else {
                                stack[stackTop--] = null;
                            }
                        }
                        break;
                    case JUMP_ABSOLUTE:
                        if (oparg < bci) {
                            if (CompilerDirectives.inInterpreter()) {
                                loopCount++;
                            }
                            if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                                Object osrResult = BytecodeOSRNode.tryOSR(this, encodeBCI(oparg) | encodeStackTop(stackTop) | encodeBlockstackTop(blockstackTop), originalArgs, null, frame);
                                if (osrResult != null) {
                                    if (CompilerDirectives.inInterpreter()) {
                                        LoopNode.reportLoopCount(this, loopCount);
                                    }
                                    return osrResult;
                                }
                            }
                        }
                        bci = oparg;
                        oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                        continue;
                    case GET_ITER:
                        stack[stackTop] = insertChildNode(() -> PyObjectGetIter.create(), bci).execute(frame, stack[stackTop]);
                        break;
                    case GET_YIELD_FROM_ITER:
                        {
                            Object iterable = stack[stackTop];
                            // TODO: handle coroutines iterable
                            if (!(iterable instanceof PGenerator)) {
                                PyObjectGetIter getIter = insertChildNode(() -> PyObjectGetIter.create(), bci);
                                stack[stackTop] = getIter.execute(frame, iterable);
                            }
                        }
                        break;
                    case FOR_ITER:
                        {
                            try {
                                Object next = insertChildNode(() -> GetNextNode.create(), bci).execute(frame, stack[stackTop]);
                                stack[++stackTop] = next;
                            } catch (PException e) {
                                e.expect(StopIteration, insertChildNode(() -> IsBuiltinClassProfile.create(), bci + 1));
                                stack[stackTop--] = null;
                                bci += oparg + 2;
                                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                                continue;
                            }
                        }
                        break;
                    case SETUP_FINALLY:
                        {
                            blockstack[++blockstackTop] = ((long)SETUP_FINALLY << 32) | ((bci + oparg) << 16) | (short)stackTop;
                        }
                        break;
                    case BEFORE_ASYNC_WITH:
                    case SETUP_ASYNC_WITH:
                    case SETUP_WITH:
                    case WITH_CLEANUP_START:
                    case WITH_CLEANUP_FINISH:
                        throw new RuntimeException("with blocks");
                    case LOAD_METHOD:
                    case CALL_METHOD:
                        throw new RuntimeException("_METHOD bytecodes");
                    case CALL_FUNCTION:
                        {
                            Object func = stack[stackTop - oparg];
                            switch (oparg) {
                                case 1:
                                    {
                                        CallUnaryMethodNode callNode = insertChildNode(() -> CallUnaryMethodNode.create(), bci);
                                        Object result = callNode.executeObject(frame, func, stack[stackTop]);
                                        stack[stackTop--] = null;
                                        stack[stackTop] = result;
                                    }
                                    break;
                                case 2:
                                    {
                                        CallBinaryMethodNode callNode = insertChildNode(() -> CallBinaryMethodNode.create(), bci);
                                        Object arg1 = stack[stackTop];
                                        stack[stackTop--] = null;
                                        Object arg0 = stack[stackTop];
                                        stack[stackTop--] = null;
                                        stack[stackTop] = callNode.executeObject(frame, func, arg0, arg1);
                                    }
                                    break;
                                case 3:
                                    {
                                        CallTernaryMethodNode callNode = insertChildNode(() -> CallTernaryMethodNode.create(), bci);
                                        Object arg2 = stack[stackTop];
                                        stack[stackTop--] = null;
                                        Object arg1 = stack[stackTop];
                                        stack[stackTop--] = null;
                                        Object arg0 = stack[stackTop];
                                        stack[stackTop--] = null;
                                        stack[stackTop] = callNode.execute(frame, func, arg0, arg1, arg2);
                                    }
                                    break;
                                default:
                                    {
                                        Object[] arguments = new Object[oparg];
                                        for (int j = oparg - 1; j >= 0; j--) {
                                            arguments[j] = stack[stackTop];
                                            stack[stackTop--] = null;
                                        }
                                        CallNode callNode = insertChildNode(() -> CallNode.create(), bci);
                                        Object result = callNode.execute(frame, func, arguments, PKeyword.EMPTY_KEYWORDS);
                                        stack[stackTop] = result;
                                    }
                                    break;
                            }
                        }
                        break;
                    case CALL_FUNCTION_KW:
                        {
                            CallNode callNode = insertChildNode(() -> CallNode.create(), bci);
                            Object[] kwNamesArray = ((PTuple) stack[stackTop]).getSequenceStorage().getInternalArray();
                            String[] kwNames = new String[kwNamesArray.length];
                            CastToJavaStringNode castStr = insertChildNode(() -> CastToJavaStringNode.create(), bci + 1);
                            for (int j = 0; j < kwNamesArray.length; j++) {
                                kwNames[j] = castStr.execute(kwNamesArray[j]);
                            }
                            stack[stackTop--] = null;
                            Object func = stack[stackTop - oparg];
                            int nkwargs = kwNames.length;
                            int nargs = oparg - nkwargs;
                            Object[] arguments = new Object[nargs];
                            for (int j = nargs - 1; j >= 0; j--) {
                                arguments[j] = stack[stackTop];
                                stack[stackTop--] = null;
                            }
                            PKeyword[] kwArgs = new PKeyword[nkwargs];
                            for (int j = nkwargs - 1; j >= 0; j--) {
                                kwArgs[j] = new PKeyword(kwNames[j], stack[stackTop]);
                                stack[stackTop--] = null;
                            }
                            stack[stackTop] = callNode.execute(frame, func, arguments, kwArgs);
                        }
                        break;
                    case CALL_FUNCTION_EX:
                        {
                            CallNode callNode = insertChildNode(() -> CallNode.create(), bci);
                            Object func;
                            Object[] callargs;
                            PKeyword[] kwargs;
                            if ((oparg & 0x01) != 0) {
                                kwargs = dictToPKeywords((PDict) stack[stackTop], bci + 1);
                                stack[stackTop--] = null;
                            } else {
                                kwargs = PKeyword.EMPTY_KEYWORDS;
                            }
                            callargs = ((PList) stack[stackTop]).getSequenceStorage().getInternalArray();
                            stack[stackTop--] = null;
                            func = stack[stackTop];
                            stack[stackTop] = callNode.execute(frame, func, callargs, kwargs);
                        }
                        break;
                    case MAKE_FUNCTION:
                        {
                            String qualname = insertChildNode(() -> CastToJavaStringNode.create(), bci).execute(stack[stackTop]);
                            stack[stackTop--] = null;
                            PCode codeobj = (PCode) stack[stackTop];
                            stack[stackTop--] = null;
                            PCell[] closure = null;
                            Object annotations = null;
                            PKeyword[] kwdefaults = null;
                            Object[] defaults = null;
                            if ((oparg & 0x08) != 0) {
                                closure = (PCell[]) ((PTuple) stack[stackTop]).getSequenceStorage().getInternalArray();
                                stack[stackTop--] = null;
                            }
                            if ((oparg & 0x04) != 0) {
                                annotations = stack[stackTop];
                                stack[stackTop--] = null;
                            }
                            if ((oparg & 0x02) != 0) {
                                PDict kwDict = (PDict) stack[stackTop];
                                stack[stackTop--] = null;
                                kwdefaults = dictToPKeywords(kwDict, bci + 1);
                            }
                            if ((oparg & 0x01) != 0) {
                                defaults = ((PTuple) stack[stackTop]).getSequenceStorage().getInternalArray();
                                stack[stackTop--] = null;
                            }
                            stack[++stackTop] = factory.createFunction(qualname, null, codeobj, (PythonObject) globals, defaults, kwdefaults, closure);
                            if (annotations != null) {
                                DynamicObjectLibrary.getUncached().put((DynamicObject)stack[stackTop], __ANNOTATIONS__, annotations);
                            }
                        }
                        break;
                    case BUILD_SLICE:
                        throw new RuntimeException("BUILD_SLICE");
                    case FORMAT_VALUE:
                        throw new RuntimeException("FORMAT_VALUE");
                    case EXTENDED_ARG:
                        bci += 2;
                        oparg = Byte.toUnsignedInt(bytecode[bci + 1]) | (oparg << 8);
                        continue;
                    default:
                        throw new RuntimeException("not implemented bytecode");
                }
                // prepare next loop
                bci += 2;
                oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                // TODO: avoid extra read
                // if (bytecode[bci] >= HAVE_ARGUMENT) oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
            } catch (PException e) {
                int handlerBCI = findHandler(stackTop, stack, blockstack, blockstackTop, bci);
                if (handlerBCI != -1) {
                    // push the exception that is being handled
                    // would use GetCaughtExceptionNode to reify the currently handled exception.
                    // but we don't want to do that if it is not needed
                    stack[++stackTop] = UNREIFIED_EXC_TRACEBACK;
                    stack[++stackTop] = UNREIFIED_EXC_VALUE;
                    stack[++stackTop] = UNREIFIED_EXC_TYPE;
                    // push the exception currently being raised
                    stack[++stackTop] = UNREIFIED_EXC_TRACEBACK;
                    stack[++stackTop] = UNREIFIED_EXC_VALUE;
                    stack[++stackTop] = e; // just push the exception, for the handler to look at

                    bci = handlerBCI;
                    oparg = Byte.toUnsignedInt(bytecode[bci + 1]);
                    // continue;
                } else {
                    throw e;
                }
            } catch (AbstractTruffleException e) {
                throw e;
            } catch (ControlFlowException | ThreadDeath e) {
                // do not handle ThreadDeath, result of TruffleContext.closeCancelled()
                throw e;
            } catch (Exception | StackOverflowError | AssertionError e) {
                throw e;
            }
        }
    }

    /**
     * Transfer exception block indices stored in the short lookup table to the prefix tree. This
     * method is called the first time an exception is handled outside of the short lookup table
     * range.
     */
    private final void transferExceptionBlockstacks() {
        short[] lookupTbl = shortExceptionBlockStackIndices;
        shortExceptionBlockStackIndices = null;
        exceptionBlockStackIndices = new Object[0][][][];

        for (int i = 0; i < 32; i++) {
            short knownBlock = lookupTbl[i];
            if (knownBlock != 0) {
                saveExceptionBlockstack(i, exceptionBlockStacks[knownBlock], exceptionBlockStacks[knownBlock].length);
            }
        }
    }

    /**
     * Save the blockstackToSave for the halfBci index. The halfBci index is assumed to be the
     * bci>>1 (since each bytecode has a byte argument). The blockstackToSave is copied for saving.
     */
    private final long[] saveExceptionBlockstack(int halfBci, long[] blockstackToSave, int blockstackToSaveLength) {
        CompilerAsserts.neverPartOfCompilation();
        short newBlockstackIdx = (short)exceptionBlockStacks.length;
        assert newBlockstackIdx < Short.MAX_VALUE : "we cannot have more blockstacks than bytecodes";
        exceptionBlockStacks = Arrays.copyOf(exceptionBlockStacks, newBlockstackIdx + 1);
        exceptionBlockStacks[newBlockstackIdx] = Arrays.copyOf(blockstackToSave, blockstackToSaveLength);

        if (shortExceptionBlockStackIndices != null) {
            if (halfBci < 32) {
                // we can save to the compact lookup table
                shortExceptionBlockStackIndices[halfBci] = newBlockstackIdx;
                return exceptionBlockStacks[newBlockstackIdx];
            } else {
                transferExceptionBlockstacks();
            }
        }

        // save to the 4-level prefix tree
        int prefix = (halfBci >> 11) & 0xf;

        Object[][][] lvl1 = exceptionBlockStackIndices[prefix];
        if (lvl1.length == 0) {
            lvl1 = exceptionBlockStackIndices[prefix] = new Object[0xf + 1][][];
        }

        prefix = (halfBci >> 7) & 0xf;
        Object[][] lvl2 = lvl1[prefix];
        if (lvl2.length == 0) {
            lvl2 = lvl1[prefix] = new Object[0xf + 1][];
        }

        prefix = (halfBci >> 3) & 0xf;
        Short[] lvl3 = (Short[])lvl2[prefix];
        if (lvl3.length == 0) {
            // the lowest bci bit was already shifted out, so the last array is only 3 bits long
            lvl3 = (Short[])(lvl2[prefix] = new Short[0b111 + 1]);
        }

        // 3-bits, like the Short array above
        prefix = halfBci & 0b111;
        lvl3[prefix] = (short)newBlockstackIdx;

        return exceptionBlockStacks[newBlockstackIdx];
    }

    // exceptionBlockStackIndices is a prefix tree with 4-bit fan-out (seems to me a reasonable
    // balance of constant memory consumption to lookup speed in the interpreter - tfel). This
    // method returns -1 when no index was found, otherwise the index has to be interpreted as
    // unsigned (this means we currently don't support caching all exception block stacks in a
    // method that has 0xffff bytecodes and raises an each an every one of them... which is
    // impossible in non-synthetic bytecode)
    private final long[] getExceptionBlockstack(int bci, long[] blockstack, int blockstackTop) {
        int bciIdx = bci >> 1;
        short blockstackIdx;

        if (shortExceptionBlockStackIndices != null) {
            if (bciIdx < 32) {
                blockstackIdx = shortExceptionBlockStackIndices[bciIdx];
                if (blockstackIdx != 0) {
                    return exceptionBlockStacks[blockstackIdx];
                }
            }
        } else {
            // we have already shifted out one byte...
            int prefix = (bciIdx >> 11) & 0xf;
            Object[][][] lvl1 = exceptionBlockStackIndices[prefix];
            if (lvl1.length > 0) {
                prefix = (bciIdx >> 7) & 0xf;
                Object[][] lvl2 = lvl1[prefix];
                if (lvl2.length > 0) {
                    prefix = (bciIdx >> 3) & 0xf;
                    Short[] lvl3 = (Short[])lvl2[prefix];
                    if (lvl3.length > 0) {
                        // 3 bits, like the Short[] array above
                        prefix = bciIdx & 0b111;
                        Short value = lvl3[prefix];
                        if (value != null) {
                            return exceptionBlockStacks[(short)value];
                        }
                    }
                }
            }
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        return saveExceptionBlockstack(bciIdx, blockstack, blockstackTop + 1);
    }

    @ExplodeLoop
    private final int findHandler(int stackTop, Object[] stack, long[] blockstack, int blockstackTop, int bci) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(blockstackTop);
        CompilerDirectives.ensureVirtualized(stack);
        CompilerDirectives.ensureVirtualized(blockstack);

        long[] savedBlockstack = getExceptionBlockstack(bci, blockstack, blockstackTop);
        int savedBlockstackTop = savedBlockstack.length;
        // TODO: the below comparison method does not exist prior to JDK9
        // assert Arrays.equals(savedBlockstack, 0, savedBlockstackTop, blockstack, 0, savedBlockstackTop) : "odd bytecode pattern with non-constant blockstack for bci";

        CompilerAsserts.partialEvaluationConstant(savedBlockstack);
        CompilerAsserts.partialEvaluationConstant(savedBlockstackTop);

        for (int i = savedBlockstackTop; i >= 0; i--) {
            long block = savedBlockstack[i];
            CompilerAsserts.partialEvaluationConstant(block);
            int type = (int)(block >> 32);
            int stackTopBeforeBlock = (short)block;
            if (type == EXCEPT_HANDLER) {
                stackTop = unwindExceptHandler(stack, stackTop, stackTopBeforeBlock);
            } else {
                stackTop = unwindBlock(stack, stackTop, stackTopBeforeBlock);
                if (type == SETUP_FINALLY) {
                    blockstack[i] = ((long)EXCEPT_HANDLER << 32) | (short)stackTop;
                    // return handler target bci
                    int handlerBCI = (((int)block >> 16) & 0xffff) + 2;
                    return handlerBCI;
                }
            }
        }
        return -1;
    }

    @ExplodeLoop
    private static final int unwindExceptHandler(Object[] stack, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        CompilerDirectives.ensureVirtualized(stack);
        for (int i = stackTopBeforeBlock; i > stackTop; i--) {
            stack[i] = null;
        }
        return stackTopBeforeBlock;
    }

    @ExplodeLoop
    private static final int unwindBlock(Object[] stack, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        CompilerDirectives.ensureVirtualized(stack);
        for (int i = stackTopBeforeBlock; i > stackTop; i--) {
            stack[i] = null;
        }
        return stackTopBeforeBlock;
    }

    private PKeyword[] dictToPKeywords(PDict kwDict, int nodeIndex) {
        HashingStorage store = kwDict.getDictStorage();
        PKeyword[] kwdefaults;
        if (store instanceof KeywordsStorage) {
            kwdefaults = ((KeywordsStorage) store).getStore();
        } else {
            HashingStorageLibrary lib = insertChildNode(() -> HashingStorageLibrary.getFactory().createDispatched(3), nodeIndex);
            kwdefaults = new PKeyword[lib.length(store)];
            int j = 0;
            for (HashingStorage.DictEntry entry : lib.entries(store)) {
                kwdefaults[j++] = new PKeyword((String) entry.key, entry.value);
            }
        }
        return kwdefaults;
    }

    // TODO: Below are 119 bytecodes. That's less than 128, so we can compact them and then use the
    // sign bit to quicken bytecodes. Thus we could initially just set the sign bit and use an
    // uncached node, then if we execute with the sign bit, we create a cached node for the
    // bytecode and optimize from there.

    private static final byte POP_TOP =                        1;
    private static final byte ROT_TWO =                        2;
    private static final byte ROT_THREE =                      3;
    private static final byte DUP_TOP =                        4;
    private static final byte DUP_TOP_TWO =                    5;
    private static final byte ROT_FOUR =                       6;
    private static final byte NOP =                            9;
    private static final byte UNARY_POSITIVE =                10;
    private static final byte UNARY_NEGATIVE =                11;
    private static final byte UNARY_NOT =                     12;
    private static final byte UNARY_INVERT =                  15;
    private static final byte BINARY_MATRIX_MULTIPLY =        16;
    private static final byte INPLACE_MATRIX_MULTIPLY =       17;
    private static final byte BINARY_POWER =                  19;
    private static final byte BINARY_MULTIPLY =               20;
    private static final byte BINARY_MODULO =                 22;
    private static final byte BINARY_ADD =                    23;
    private static final byte BINARY_SUBTRACT =               24;
    private static final byte BINARY_SUBSCR =                 25;
    private static final byte BINARY_FLOOR_DIVIDE =           26;
    private static final byte BINARY_TRUE_DIVIDE =            27;
    private static final byte INPLACE_FLOOR_DIVIDE =          28;
    private static final byte INPLACE_TRUE_DIVIDE =           29;
    private static final byte GET_AITER =                     50;
    private static final byte GET_ANEXT =                     51;
    private static final byte BEFORE_ASYNC_WITH =             52;
    private static final byte BEGIN_FINALLY =                 53;
    private static final byte END_ASYNC_FOR =                 54;
    private static final byte INPLACE_ADD =                   55;
    private static final byte INPLACE_SUBTRACT =              56;
    private static final byte INPLACE_MULTIPLY =              57;
    private static final byte INPLACE_MODULO =                59;
    private static final byte STORE_SUBSCR =                  60;
    private static final byte DELETE_SUBSCR =                 61;
    private static final byte BINARY_LSHIFT =                 62;
    private static final byte BINARY_RSHIFT =                 63;
    private static final byte BINARY_AND =                    64;
    private static final byte BINARY_XOR =                    65;
    private static final byte BINARY_OR =                     66;
    private static final byte INPLACE_POWER =                 67;
    private static final byte GET_ITER =                      68;
    private static final byte GET_YIELD_FROM_ITER =           69;
    private static final byte PRINT_EXPR =                    70;
    private static final byte LOAD_BUILD_CLASS =              71;
    private static final byte YIELD_FROM =                    72;
    private static final byte GET_AWAITABLE =                 73;
    private static final byte INPLACE_LSHIFT =                75;
    private static final byte INPLACE_RSHIFT =                76;
    private static final byte INPLACE_AND =                   77;
    private static final byte INPLACE_XOR =                   78;
    private static final byte INPLACE_OR =                    79;
    private static final byte WITH_CLEANUP_START =            81;
    private static final byte WITH_CLEANUP_FINISH =           82;
    private static final byte RETURN_VALUE =                  83;
    private static final byte IMPORT_STAR =                   84;
    private static final byte SETUP_ANNOTATIONS =             85;
    private static final byte YIELD_VALUE =                   86;
    private static final byte POP_BLOCK =                     87;
    private static final byte END_FINALLY =                   88;
    private static final byte POP_EXCEPT =                    89;
    private static final byte HAVE_ARGUMENT =                 90;
    private static final byte STORE_NAME =                    90;
    private static final byte DELETE_NAME =                   91;
    private static final byte UNPACK_SEQUENCE =               92;
    private static final byte FOR_ITER =                      93;
    private static final byte UNPACK_EX =                     94;
    private static final byte STORE_ATTR =                    95;
    private static final byte DELETE_ATTR =                   96;
    private static final byte STORE_GLOBAL =                  97;
    private static final byte DELETE_GLOBAL =                 98;
    private static final byte LOAD_CONST =                   100;
    private static final byte LOAD_NAME =                    101;
    private static final byte BUILD_TUPLE =                  102;
    private static final byte BUILD_LIST =                   103;
    private static final byte BUILD_SET =                    104;
    private static final byte BUILD_MAP =                    105;
    private static final byte LOAD_ATTR =                    106;
    private static final byte COMPARE_OP =                   107;
    private static final byte IMPORT_NAME =                  108;
    private static final byte IMPORT_FROM =                  109;
    private static final byte JUMP_FORWARD =                 110;
    private static final byte JUMP_IF_FALSE_OR_POP =         111;
    private static final byte JUMP_IF_TRUE_OR_POP =          112;
    private static final byte JUMP_ABSOLUTE =                113;
    private static final byte POP_JUMP_IF_FALSE =            114;
    private static final byte POP_JUMP_IF_TRUE =             115;
    private static final byte LOAD_GLOBAL =                  116;
    private static final byte SETUP_FINALLY =                122;
    private static final byte LOAD_FAST_WITH_ERROR =         123; // Added by us
    private static final byte LOAD_FAST =                    124;
    private static final byte STORE_FAST =                   125;
    private static final byte DELETE_FAST =                  126;
    private static final byte RAISE_VARARGS =                (byte) 130;
    private static final byte CALL_FUNCTION =                (byte) 131;
    private static final byte MAKE_FUNCTION =                (byte) 132;
    private static final byte BUILD_SLICE =                  (byte) 133;
    private static final byte LOAD_CLOSURE =                 (byte) 135;
    private static final byte LOAD_DEREF =                   (byte) 136;
    private static final byte STORE_DEREF =                  (byte) 137;
    private static final byte DELETE_DEREF =                 (byte) 138;
    private static final byte CALL_FUNCTION_KW =             (byte) 141;
    private static final byte CALL_FUNCTION_EX =             (byte) 142;
    private static final byte SETUP_WITH =                   (byte) 143;
    private static final byte EXTENDED_ARG =                 (byte) 144;
    private static final byte LIST_APPEND =                  (byte) 145;
    private static final byte SET_ADD =                      (byte) 146;
    private static final byte MAP_ADD =                      (byte) 147;
    private static final byte LOAD_CLASSDEREF =              (byte) 148;
    private static final byte BUILD_LIST_UNPACK =            (byte) 149;
    private static final byte BUILD_MAP_UNPACK =             (byte) 150;
    private static final byte BUILD_MAP_UNPACK_WITH_CALL =   (byte) 151;
    private static final byte BUILD_TUPLE_UNPACK =           (byte) 152;
    private static final byte BUILD_SET_UNPACK =             (byte) 153;
    private static final byte SETUP_ASYNC_WITH =             (byte) 154;
    private static final byte FORMAT_VALUE =                 (byte) 155;
    private static final byte BUILD_CONST_KEY_MAP =          (byte) 156;
    private static final byte BUILD_STRING =                 (byte) 157;
    private static final byte BUILD_TUPLE_UNPACK_WITH_CALL = (byte) 158;
    private static final byte LOAD_METHOD =                  (byte) 160;
    private static final byte CALL_METHOD =                  (byte) 161;
    private static final byte CALL_FINALLY =                 (byte) 162;
    private static final byte POP_FINALLY =                  (byte) 163;

    private static final int EXCEPT_HANDLER =                257;
}
