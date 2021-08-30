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
import com.oracle.graal.python.nodes.statement.AbstractImportNode.ImportName;
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

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

    @CompilationFinal(dimensions = 1) private final FrameSlot[] stackSlots;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] varSlots;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] freeSlots;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] cellSlots;

    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    @CompilationFinal private Object osrMetadata;

    private static final FrameDescriptor EMPTY_DESCRIPTOR = new FrameDescriptor();

    public PBytecodeRootNode(TruffleLanguage<?> language, Signature sign, byte[] bc,
                    String filename, String name, int firstlineno,
                    Object[] consts, String[] names, String[] varnames, String[] freevars, String[] cellvars,
                    int stacksize) {
        super(language);
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
        this.stackSlots = new FrameSlot[stacksize];
        this.varSlots = new FrameSlot[varnames.length];
        this.freeSlots = new FrameSlot[freevars.length];
        this.cellSlots = new FrameSlot[cellvars.length];
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
        this.stackSlots = new FrameSlot[stacksize];
        this.varSlots = new FrameSlot[varnames.length];
        this.freeSlots = new FrameSlot[freevars.length];
        this.cellSlots = new FrameSlot[cellvars.length];
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

    private final void push(VirtualFrame frame, int i, Object o) {
        FrameSlot slot = stackSlots[i];
        if (slot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slot = stackSlots[i] = frame.getFrameDescriptor().addFrameSlot(i, FrameSlotKind.Object);
        }
        frame.setObject(slot, o);
    }

    private final Object top(VirtualFrame frame, int i) {
        return FrameUtil.getObjectSafe(frame, stackSlots[i]);
    }

    private final void setLocal(VirtualFrame frame, int i, Object o) {
        FrameSlot slot = varSlots[i];
        if (slot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slot = varSlots[i] = frame.getFrameDescriptor().addFrameSlot(varnames[i], FrameSlotKind.Object);
        }
        frame.setObject(slot, o);
    }

    private final Object getLocal(VirtualFrame frame, int i) {
        return FrameUtil.getObjectSafe(frame, varSlots[i]);
    }

    private final void setFreevar(VirtualFrame frame, int i, Object o) {
        FrameSlot slot = freeSlots[i];
        if (slot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slot = freeSlots[i] = frame.getFrameDescriptor().addFrameSlot(freevars[i], FrameSlotKind.Object);
        }
        frame.setObject(slot, o);
    }

    private final Object getFreevar(VirtualFrame frame, int i) {
        return FrameUtil.getObjectSafe(frame, freeSlots[i]);
    }

    private final void setCellvar(VirtualFrame frame, int i, Object o) {
        FrameSlot slot = cellSlots[i];
        if (slot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slot = cellSlots[i] = frame.getFrameDescriptor().addFrameSlot(cellvars[i], FrameSlotKind.Object);
        }
        frame.setObject(slot, o);
    }

    private final Object getCellvar(VirtualFrame frame, int i) {
        return FrameUtil.getObjectSafe(frame, cellSlots[i]);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        PythonContext context = PythonContext.get(this);
        calleeContext.enter(frame);
        try {
            assert stacksize < Short.MAX_VALUE : "stacksize cannot be larger than short range";
            assert bytecode.length < Math.pow(2, Short.SIZE) : "bytecode cannot be longer than unsigned short range";

            Object[] args = frame.getArguments();
            for (int i = PArguments.USER_ARGUMENTS_OFFSET; i < args.length; i++) {
                setLocal(frame, i - PArguments.USER_ARGUMENTS_OFFSET, args[i]);
            }
            int varargsIdx = signature.getVarargsIdx();
            if (varargsIdx >= 0) {
                setLocal(frame, varargsIdx, factory.createList(PArguments.getVariableArguments(args)));
            }
            if (signature.takesVarKeywordArgs()) {
                int idx = signature.getParameterIds().length + signature.getKeywordNames().length;
                if (varargsIdx >= 0) {
                    idx += 1;
                }
                setLocal(frame, idx, factory.createDict(PArguments.getKeywordArguments(args)));
            }

            return executeOSR(frame, 0xffff, args);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Supplier<T> nodeSupplier, int bytecodeIndex) {
        T node = (T) adoptedNodes[bytecodeIndex];
        if (node == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            node = nodeSupplier.get();
            adoptedNodes[bytecodeIndex] = insert(node);
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Function<String, T> nodeSupplier, int bytecodeIndex, String argument) {
        T node = (T) adoptedNodes[bytecodeIndex];
        if (node == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            node = nodeSupplier.apply(argument);
            adoptedNodes[bytecodeIndex] = insert(node);
        }
        return node;
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
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object executeOSR(VirtualFrame frame, int target, Object originalArgs) {
        PythonContext context = PythonContext.get(this);
        PythonModule builtins = context.getBuiltins();

        Object globals = PArguments.getGlobals((Object[])originalArgs);
        // Object locals = PArguments.getCustomLocals(frame); // TODO: deal with custom locals

        int stackTop = (short)target;
        int i = (target >> Short.SIZE) & 0xffff;
        int oparg = Byte.toUnsignedInt(bytecode[i + 1]);
        while (true) {
            switch (bytecode[i]) {
                case NOP:
                    break;
                case LOAD_FAST:
                    {
                        Object value = getLocal(frame, oparg);
                        if (value == null) {
                            throw insertChildNode(() -> PRaiseNode.create(), i).raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                        }
                        push(frame, ++stackTop, value);
                    }
                    break;
                case LOAD_CONST:
                    push(frame, ++stackTop, consts[oparg]);
                    break;
                case STORE_FAST:
                    setLocal(frame, oparg, top(frame, stackTop));
                    push(frame, stackTop--, null);
                    break;
                case POP_TOP:
                    push(frame, stackTop--, null);
                    break;
                case ROT_TWO:
                    {
                        Object first = top(frame, stackTop);
                        Object second = top(frame, stackTop - 1);
                        push(frame, stackTop, second);
                        push(frame, stackTop - 1, first);
                        break;
                    }
                case ROT_THREE:
                    {
                        Object first = top(frame, stackTop);
                        Object second = top(frame, stackTop - 1);
                        Object third = top(frame, stackTop - 2);
                        push(frame, stackTop, second);
                        push(frame, stackTop - 1, third);
                        push(frame, stackTop - 2, first);
                        break;
                    }
                case ROT_FOUR:
                    {
                        Object first = top(frame, stackTop);
                        Object second = top(frame, stackTop - 1);
                        Object third = top(frame, stackTop - 2);
                        Object fourth = top(frame, stackTop - 3);
                        push(frame, stackTop, second);
                        push(frame, stackTop - 1, third);
                        push(frame, stackTop - 2, fourth);
                        push(frame, stackTop - 3, first);
                        break;
                    }
                case DUP_TOP:
                    {
                        Object first = top(frame, stackTop);
                        push(frame, ++stackTop, first);
                    }
                    break;
                case DUP_TOP_TWO:
                    {
                        Object first = top(frame, stackTop);
                        Object second = top(frame, stackTop - 1);
                        push(frame, ++stackTop, second);
                        push(frame, ++stackTop, first);
                    }
                    break;
                case UNARY_POSITIVE:
                    {
                        PosNode posNode = insertChildNode(() -> PosNodeGen.create(null), i);
                        push(frame, stackTop, posNode.execute(frame, top(frame, stackTop)));
                    }
                    break;
                case UNARY_NEGATIVE:
                    {
                        NegNode negNode = insertChildNode(() -> NegNodeGen.create(null), i);
                        push(frame, stackTop, negNode.execute(frame, top(frame, stackTop)));
                    }
                    break;
                case UNARY_NOT:
                    push(frame, stackTop, PyObjectIsTrueNode.getUncached().execute(frame, top(frame, stackTop)));
                    break;
                case UNARY_INVERT:
                    {
                        InvertNode invertNode = insertChildNode(() -> InvertNodeGen.create(null), i);
                        push(frame, stackTop, invertNode.execute(frame, top(frame, stackTop)));
                    }
                    break;
                case BINARY_POWER:
                    {
                        PowNode powNode = insertChildNode(() -> PowNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, powNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_MULTIPLY:
                    {
                        MulNode mulNode = insertChildNode(() -> MulNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, mulNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_MATRIX_MULTIPLY:
                    {
                        MatMulNode matMulNode = insertChildNode(() -> MatMulNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, matMulNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_TRUE_DIVIDE:
                    {
                        TrueDivNode trueDivNode = insertChildNode(() -> TrueDivNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, trueDivNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_FLOOR_DIVIDE:
                    {
                        FloorDivNode floorDivNode = insertChildNode(() -> FloorDivNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, floorDivNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_MODULO:
                    {
                        ModNode modNode = insertChildNode(() -> ModNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, modNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_ADD:
                    {
                        AddNode addNode = insertChildNode(() -> AddNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, addNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_SUBTRACT:
                    {
                        SubNode subNode = insertChildNode(() -> SubNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, subNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_SUBSCR:
                    {
                        GetItemNode getItemNode = insertChildNode(() -> GetItemNode.create(), i);
                        Object slice = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, getItemNode.execute(frame, top(frame, stackTop), slice));
                    }
                    break;
                case BINARY_LSHIFT:
                    {
                        LShiftNode lShiftNode = insertChildNode(() -> LShiftNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, lShiftNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_RSHIFT:
                    {
                        RShiftNode rShiftNode = insertChildNode(() -> RShiftNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, rShiftNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_AND:
                    {
                        BitAndNode bitAndNode = insertChildNode(() -> BitAndNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, bitAndNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_XOR:
                    {
                        BitXorNode bitXorNode = insertChildNode(() -> BitXorNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, bitXorNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case BINARY_OR:
                    {
                        BitOrNode bitOrNode = insertChildNode(() -> BitOrNodeGen.create(null, null), i);
                        Object right = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        push(frame, stackTop, bitOrNode.executeObject(frame, top(frame, stackTop), right));
                    }
                    break;
                case LIST_APPEND:
                    {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), i);
                        Object list = top(frame, stackTop - oparg);
                        Object value = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        callNode.execute(frame, list, "append", value);
                    }
                    break;
                case SET_ADD:
                    {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), i);
                        Object value = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        callNode.execute(frame, top(frame, stackTop), "add", value);
                    }
                    break;
                case INPLACE_POWER:
                case INPLACE_MULTIPLY:
                case INPLACE_MATRIX_MULTIPLY:
                case INPLACE_TRUE_DIVIDE:
                case INPLACE_FLOOR_DIVIDE:
                case INPLACE_MODULO:
                case INPLACE_ADD:
                case INPLACE_SUBTRACT:
                case INPLACE_LSHIFT:
                case INPLACE_RSHIFT:
                case INPLACE_AND:
                case INPLACE_XOR:
                case INPLACE_OR:
                    throw new RuntimeException("inplace bytecodes");
                case STORE_SUBSCR:
                    {
                        PyObjectSetItem setItem = insertChildNode(() -> PyObjectSetItem.create(), i);
                        Object index = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        Object container = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        Object value = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        setItem.execute(frame, container, index, value);
                    }
                    break;
                case DELETE_SUBSCR:
                    throw new RuntimeException("DELETE_SUBSCR");
                case PRINT_EXPR:
                    throw new RuntimeException("PRINT_EXPR");
                case RAISE_VARARGS:
                    {
                        RaiseNode raiseNode = insertChildNode(() -> RaiseNode.create(null, null), i);
                        int arg = oparg;
                        Object cause;
                        Object exception;
                        if (arg > 1) {
                            cause = top(frame, stackTop);
                            push(frame, stackTop--, null);
                        } else {
                            cause = PNone.NO_VALUE;
                        }
                        if (arg > 0) {
                            exception = top(frame, stackTop);
                            push(frame, stackTop--, null);
                        } else {
                            exception = PNone.NO_VALUE;
                        }
                        raiseNode.execute(frame, exception, cause);
                    }
                    break;
                case RETURN_VALUE:
                    return top(frame, stackTop);
                case GET_AITER:
                case GET_ANEXT:
                case GET_AWAITABLE:
                    throw new RuntimeException("async bytecodes");
                case YIELD_FROM:
                case YIELD_VALUE:
                    throw new RuntimeException("yield bytecodes");
                case POP_EXCEPT:
                case POP_BLOCK:
                case POP_FINALLY:
                case CALL_FINALLY:
                case BEGIN_FINALLY:
                case END_FINALLY:
                    throw new RuntimeException("exception blocks");
                case END_ASYNC_FOR:
                    throw new RuntimeException("async bytecodes");
                case LOAD_BUILD_CLASS:
                    {
                        ReadGlobalOrBuiltinNode read = insertChildNode(() -> ReadGlobalOrBuiltinNode.create(__BUILD_CLASS__), i);
                        push(frame, ++stackTop, read.executeWithGlobals(frame, globals));
                    }
                    break;
                case STORE_NAME:
                    {
                        String varname = names[oparg];
                        WriteGlobalNode writeGlobalNode = insertChildNode((a) -> WriteGlobalNode.create(a), i, varname);
                        writeGlobalNode.executeObject(frame, top(frame, stackTop));
                        push(frame, stackTop--, null);
                    }
                    break;
                case DELETE_NAME:
                    {
                        String varname = names[oparg];
                        DeleteGlobalNode deleteGlobalNode = insertChildNode((a) -> DeleteGlobalNode.create(a), i, varname);
                        deleteGlobalNode.executeVoid(frame);
                    }
                    break;
                case UNPACK_SEQUENCE:
                case UNPACK_EX:
                    throw new RuntimeException("unpack bytecodes");
                case STORE_ATTR:
                    {
                        String varname = names[oparg];
                        Object owner = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        Object value = top(frame, stackTop);
                        push(frame, stackTop--, null);
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
                        ReadGlobalOrBuiltinNode read = insertChildNode((a) -> ReadGlobalOrBuiltinNode.create(a), i, varname);
                        push(frame, ++stackTop, read.executeWithGlobals(frame, globals));
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
                            list[oparg] = top(frame, stackTop);
                            push(frame, stackTop--, null);
                        }
                        push(frame, ++stackTop, factory.createList(list));
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
                        PyObjectGetAttr getAttr = insertChildNode(() -> PyObjectGetAttr.create(), i);
                        String varname = names[oparg];
                        Object owner = top(frame, stackTop);
                        Object value = getAttr.execute(frame, owner, varname);
                        push(frame, stackTop, value);
                    }
                    break;
                case COMPARE_OP:
                    throw new RuntimeException("COMARE_OP");
                case IMPORT_NAME:
                    {
                        CastToJavaIntExactNode castNode = insertChildNode(() -> CastToJavaIntExactNode.create(), i);
                        String modname = names[oparg];
                        Object fromlist = top(frame, stackTop);
                        push(frame, stackTop--, null);
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
                        int level = castNode.execute(top(frame, stackTop));
                        ImportName importNode = insertChildNode(() -> ImportName.create(), i + 1);
                        Object result = importNode.execute(frame, context, builtins, modname, globals, fromlistArg, level);
                        push(frame, stackTop, result);
                    }
                    break;
                case IMPORT_STAR:
                case IMPORT_FROM:
                    throw new RuntimeException("import start / import from");
                case JUMP_FORWARD:
                    i += oparg;
                    oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                    continue;
                case POP_JUMP_IF_FALSE:
                    {
                        PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), i);
                        Object cond = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        if (!isTrue.execute(frame, cond)) {
                            i = oparg;
                            oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                            continue;
                        }
                    }
                    break;
                case POP_JUMP_IF_TRUE:
                    {
                        PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), i);
                        Object cond = top(frame, stackTop);
                        push(frame, stackTop--, null);
                        if (isTrue.execute(frame, cond)) {
                            i = oparg;
                            oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                            continue;
                        }
                    }
                    break;
                case JUMP_IF_FALSE_OR_POP:
                    {
                        PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), i);
                        Object cond = top(frame, stackTop);
                        if (!isTrue.execute(frame, cond)) {
                            i = oparg;
                            oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                            continue;
                        } else {
                            push(frame, stackTop--, null);
                        }
                    }
                    break;
                case JUMP_IF_TRUE_OR_POP:
                    {
                        PyObjectIsTrueNode isTrue = insertChildNode(() -> PyObjectIsTrueNode.create(), i);
                        Object cond = top(frame, stackTop);
                        if (isTrue.execute(frame, cond)) {
                            i = oparg;
                            oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                            continue;
                        } else {
                            push(frame, stackTop--, null);
                        }
                    }
                    break;
                case JUMP_ABSOLUTE:
                    if (oparg < i) {
                        if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                            Object osrResult = BytecodeOSRNode.tryOSR(this, (oparg << Short.SIZE) | stackTop, originalArgs, null, frame);
                            if (osrResult != null) {
                                return osrResult;
                            }
                        }
                    }
                    i = oparg;
                    oparg = Byte.toUnsignedInt(bytecode[i + 1]);
                    continue;
                case GET_ITER:
                    push(frame, stackTop, insertChildNode(() -> PyObjectGetIter.create(), i).execute(frame, top(frame, stackTop)));
                    break;
                case GET_YIELD_FROM_ITER:
                    {
                        Object iterable = top(frame, stackTop);
                        // TODO: handle coroutines iterable
                        if (!(iterable instanceof PGenerator)) {
                            PyObjectGetIter getIter = insertChildNode(() -> PyObjectGetIter.create(), i);
                            push(frame, stackTop, getIter.execute(frame, iterable));
                        }
                    }
                    break;
                case FOR_ITER:
                    {
                        try {
                            Object next = insertChildNode(() -> GetNextNode.create(), i).execute(frame, top(frame, stackTop));
                            push(frame, ++stackTop, next);
                        } catch (PException e) {
                            e.expect(StopIteration, insertChildNode(() -> IsBuiltinClassProfile.create(), i + 1));
                            push(frame, stackTop--, null);
                            i += oparg;
                        }
                    }
                    break;
                case SETUP_FINALLY:
                case BEFORE_ASYNC_WITH:
                case SETUP_ASYNC_WITH:
                case SETUP_WITH:
                case WITH_CLEANUP_START:
                case WITH_CLEANUP_FINISH:
                    throw new RuntimeException("loop / with / finally blocks");
                case LOAD_METHOD:
                case CALL_METHOD:
                    throw new RuntimeException("_METHOD bytecodes");
                case CALL_FUNCTION:
                    {
                        Object func = top(frame, stackTop - oparg);
                        switch (oparg) {
                            case 1:
                                {
                                    CallUnaryMethodNode callNode = insertChildNode(() -> CallUnaryMethodNode.create(), i);
                                    Object result = callNode.executeObject(frame, func, top(frame, stackTop));
                                    push(frame, stackTop--, null);
                                    push(frame, stackTop, result);
                                }
                                break;
                            case 2:
                                {
                                    CallBinaryMethodNode callNode = insertChildNode(() -> CallBinaryMethodNode.create(), i);
                                    Object arg1 = top(frame, stackTop);
                                    push(frame, stackTop--, null);
                                    Object arg0 = top(frame, stackTop);
                                    push(frame, stackTop--, null);
                                    push(frame, stackTop, callNode.executeObject(frame, func, arg0, arg1));
                                }
                                break;
                            case 3:
                                {
                                    CallTernaryMethodNode callNode = insertChildNode(() -> CallTernaryMethodNode.create(), i);
                                    Object arg2 = top(frame, stackTop);
                                    push(frame, stackTop--, null);
                                    Object arg1 = top(frame, stackTop);
                                    push(frame, stackTop--, null);
                                    Object arg0 = top(frame, stackTop);
                                    push(frame, stackTop--, null);
                                    push(frame, stackTop, callNode.execute(frame, func, arg0, arg1, arg2));
                                }
                                break;
                            default:
                                {
                                    Object[] arguments = new Object[oparg];
                                    for (int j = oparg - 1; j >= 0; j--) {
                                        arguments[j] = top(frame, stackTop);
                                        push(frame, stackTop--, null);
                                    }
                                    CallNode callNode = insertChildNode(() -> CallNode.create(), i);
                                    Object result = callNode.execute(frame, func, arguments, PKeyword.EMPTY_KEYWORDS);
                                    push(frame, stackTop, result);
                                }
                                break;
                        }
                    }
                    break;
                case CALL_FUNCTION_KW:
                    {
                        CallNode callNode = insertChildNode(() -> CallNode.create(), i);
                        Object[] kwNamesArray = ((PTuple) top(frame, stackTop)).getSequenceStorage().getInternalArray();
                        String[] kwNames = new String[kwNamesArray.length];
                        CastToJavaStringNode castStr = insertChildNode(() -> CastToJavaStringNode.create(), i + 1);
                        for (int j = 0; j < kwNamesArray.length; j++) {
                            kwNames[j] = castStr.execute(kwNamesArray[j]);
                        }
                        push(frame, stackTop--, null);
                        Object func = top(frame, stackTop - oparg);
                        int nkwargs = kwNames.length;
                        int nargs = oparg - nkwargs;
                        Object[] arguments = new Object[nargs];
                        for (int j = nargs - 1; j >= 0; j--) {
                            arguments[j] = top(frame, stackTop);
                            push(frame, stackTop--, null);
                        }
                        PKeyword[] kwArgs = new PKeyword[nkwargs];
                        for (int j = nkwargs - 1; j >= 0; j--) {
                            kwArgs[j] = new PKeyword(kwNames[j], top(frame, stackTop));
                            push(frame, stackTop--, null);
                        }
                        push(frame, stackTop, callNode.execute(frame, func, arguments, kwArgs));
                    }
                    break;
                case CALL_FUNCTION_EX:
                    {
                        CallNode callNode = insertChildNode(() -> CallNode.create(), i);
                        Object func;
                        Object[] callargs;
                        PKeyword[] kwargs;
                        if ((oparg & 0x01) != 0) {
                            kwargs = dictToPKeywords((PDict) top(frame, stackTop), i + 1);
                            push(frame, stackTop--, null);
                        } else {
                            kwargs = PKeyword.EMPTY_KEYWORDS;
                        }
                        callargs = ((PList) top(frame, stackTop)).getSequenceStorage().getInternalArray();
                        push(frame, stackTop--, null);
                        func = top(frame, stackTop);
                        push(frame, stackTop, callNode.execute(frame, func, callargs, kwargs));
                    }
                    break;
                case MAKE_FUNCTION:
                    {
                        String qualname = insertChildNode(() -> CastToJavaStringNode.create(), i).execute(top(frame, stackTop));
                        push(frame, stackTop--, null);
                        PCode codeobj = (PCode) top(frame, stackTop);
                        push(frame, stackTop--, null);
                        PCell[] closure = null;
                        Object annotations = null;
                        PKeyword[] kwdefaults = null;
                        Object[] defaults = null;
                        if ((oparg & 0x08) != 0) {
                            closure = (PCell[]) ((PTuple) top(frame, stackTop)).getSequenceStorage().getInternalArray();
                            push(frame, stackTop--, null);
                        }
                        if ((oparg & 0x04) != 0) {
                            annotations = top(frame, stackTop);
                            push(frame, stackTop--, null);
                        }
                        if ((oparg & 0x02) != 0) {
                            PDict kwDict = (PDict) top(frame, stackTop);
                            push(frame, stackTop--, null);
                            kwdefaults = dictToPKeywords(kwDict, i + 1);
                        }
                        if ((oparg & 0x01) != 0) {
                            defaults = ((PTuple) top(frame, stackTop)).getSequenceStorage().getInternalArray();
                            push(frame, stackTop--, null);
                        }
                        push(frame, ++stackTop, factory.createFunction(qualname, null, codeobj, (PythonObject) globals, defaults, kwdefaults, closure));
                        if (annotations != null) {
                            DynamicObjectLibrary.getUncached().put((DynamicObject)top(frame, stackTop), __ANNOTATIONS__, annotations);
                        }
                    }
                    break;
                case BUILD_SLICE:
                    throw new RuntimeException("BUILD_SLICE");
                case FORMAT_VALUE:
                    throw new RuntimeException("FORMAT_VALUE");
                case EXTENDED_ARG:
                    i += 2;
                    oparg = Byte.toUnsignedInt(bytecode[i + 1]) | (oparg << 8);
                    continue;
                default:
                    throw new RuntimeException("not implemented bytecode");
            }

            // prepare next loop
            i += 2;
            oparg = Byte.toUnsignedInt(bytecode[i + 1]);
        }
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
}
