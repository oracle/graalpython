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

import static com.oracle.graal.python.compiler.OpCodesConstants.*;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
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
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectGetMethodNodeGen;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
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
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
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
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PBytecodeRootNode extends PRootNode implements BytecodeOSRNode {

    private static final NodeSupplier<MulNode> NODE_BINARY_MUL = () -> MulNodeGen.create(null, null);
    private static final NodeSupplier<CoerceToBooleanNode> NODE_COERCE_TO_BOOLEAN_IF_FALSE = CoerceToBooleanNode::createIfFalseNode;
    private static final NodeSupplier<RaiseNode> NODE_RAISENODE = () -> RaiseNode.create(null, null);
    private static final NodeSupplier<BitOrNode> NODE_BINARY_BITOR = () -> BitOrNodeGen.create(null, null);
    private static final NodeSupplier<BitXorNode> NODE_BINARY_BITXOR = () -> BitXorNodeGen.create(null, null);
    private static final NodeSupplier<BitAndNode> NODE_BINARY_BITAND = () -> BitAndNodeGen.create(null, null);
    private static final NodeSupplier<RShiftNode> NODE_BINARY_RSHIFT = () -> RShiftNodeGen.create(null, null);
    private static final NodeSupplier<LShiftNode> NODE_BINARY_LSHIFT = () -> LShiftNodeGen.create(null, null);
    private static final NodeSupplier<SubNode> NODE_BINARY_SUB = () -> SubNodeGen.create(null, null);
    private static final NodeSupplier<AddNode> NODE_BINARY_ADD = () -> AddNodeGen.create(null, null);
    private static final NodeSupplier<ModNode> NODE_BINARY_MOD = () -> ModNodeGen.create(null, null);
    private static final NodeSupplier<FloorDivNode> NODE_BINARY_FLOORDIV = () -> FloorDivNodeGen.create(null, null);
    private static final NodeSupplier<TrueDivNode> NODE_BINARY_TRUEDIV = () -> TrueDivNodeGen.create(null, null);
    private static final NodeSupplier<MatMulNode> NODE_BINARY_MATMUL = () -> MatMulNodeGen.create(null, null);
    private static final NodeSupplier<PowNode> NODE_BINARY_POW = () -> PowNodeGen.create(null, null);
    private static final NodeSupplier<InvertNode> NODE_UNARY_INVERT = () -> InvertNodeGen.create(null);
    private static final NodeSupplier<NegNode> NODE_UNARY_NEG = () -> NegNodeGen.create(null);
    private static final NodeSupplier<PosNode> NODE_UNARY_POS = () -> PosNodeGen.create(null);
    private static final NodeSupplier<DeleteItemNode> NODE_DELETE_ITEM = DeleteItemNode::create;
    private static final NodeSupplier<PyObjectDelItem> NODE_OBJECT_DEL_ITEM = PyObjectDelItem::create;
    private static final PyObjectDelItem UNCACHED_OBJECT_DEL_ITEM = PyObjectDelItem.getUncached();

    private static final NodeSupplier<SetItemNode> NODE_SET_ITEM = HashingCollectionNodes.SetItemNode::create;
    private static final SetItemNode UNCACHED_SET_ITEM = HashingCollectionNodes.SetItemNode.getUncached();
    private static final NodeSupplier<CastToJavaIntExactNode> NODE_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode::create;
    private static final CastToJavaIntExactNode UNCACHED_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode.getUncached();
    private static final NodeSupplier<ImportName> NODE_IMPORT_NAME = ImportName::create;
    private static final ImportName UNCACHED_IMPORT_NAME = ImportName.getUncached();
    private static final NodeSupplier<PyObjectGetAttr> NODE_OBJECT_GET_ATTR = PyObjectGetAttr::create;
    private static final PyObjectGetAttr UNCACHED_OBJECT_GET_ATTR = PyObjectGetAttr.getUncached();
    private static final NodeSupplier<PRaiseNode> NODE_RAISE = PRaiseNode::create;
    private static final PRaiseNode UNCACHED_RAISE = PRaiseNode.getUncached();
    private static final NodeSupplier<IsBuiltinClassProfile> NODE_IS_BUILTIN_CLASS_PROFILE = IsBuiltinClassProfile::create;
    private static final IsBuiltinClassProfile UNCACHED_IS_BUILTIN_CLASS_PROFILE = IsBuiltinClassProfile.getUncached();
    private static final NodeSupplier<CastToJavaStringNode> NODE_CAST_TO_JAVA_STRING = CastToJavaStringNode::create;
    private static final CastToJavaStringNode UNCACHED_CAST_TO_JAVA_STRING = CastToJavaStringNode.getUncached();
    private static final NodeSupplier<CallNode> NODE_CALL = CallNode::create;
    private static final CallNode UNCACHED_CALL = CallNode.getUncached();
    private static final NodeSupplier<CallTernaryMethodNode> NODE_CALL_TERNARY_METHOD = CallTernaryMethodNode::create;
    private static final CallTernaryMethodNode UNCACHED_CALL_TERNARY_METHOD = CallTernaryMethodNode.getUncached();
    private static final NodeSupplier<CallBinaryMethodNode> NODE_CALL_BINARY_METHOD = CallBinaryMethodNode::create;
    private static final CallBinaryMethodNode UNCACHED_CALL_BINARY_METHOD = CallBinaryMethodNode.getUncached();
    private static final NodeSupplier<CallUnaryMethodNode> NODE_CALL_UNARY_METHOD = CallUnaryMethodNode::create;
    private static final CallUnaryMethodNode UNCACHED_CALL_UNARY_METHOD = CallUnaryMethodNode.getUncached();
    private static final NodeSupplier<PyObjectGetMethod> NODE_OBJECT_GET_METHOD = PyObjectGetMethodNodeGen::create;
    private static final PyObjectGetMethod UNCACHED_OBJECT_GET_METHOD = PyObjectGetMethodNodeGen.getUncached();
    private static final NodeSupplier<GetNextNode> NODE_GET_NEXT = GetNextNode::create;
    private static final GetNextNode UNCACHED_GET_NEXT = GetNextNode.getUncached();
    private static final NodeSupplier<PyObjectGetIter> NODE_OBJECT_GET_ITER = PyObjectGetIter::create;
    private static final PyObjectGetIter UNCACHED_OBJECT_GET_ITER = PyObjectGetIter.getUncached();
    private static final NodeSupplier<HashingStorageLibrary> NODE_HASHING_STORAGE_LIBRARY = () -> HashingStorageLibrary.getFactory().createDispatched(2);
    private static final NodeFunction<Object, HashingStorageLibrary> NODE_HASHING_STORAGE_LIBRARY_DIRECT = a -> HashingStorageLibrary.getFactory().create(a);
    private static final HashingStorageLibrary UNCACHED_HASHING_STORAGE_LIBRARY = HashingStorageLibrary.getUncached();
    private static final NodeSupplier<PyObjectSetAttr> NODE_OBJECT_SET_ATTR = PyObjectSetAttr::create;
    private static final PyObjectSetAttr UNCACHED_OBJECT_SET_ATTR = PyObjectSetAttr.getUncached();
    private static final NodeSupplier<ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS = () -> ReadGlobalOrBuiltinNode.create(__BUILD_CLASS__);
    private static final NodeFunction<String, ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode::create;
    private static final ReadGlobalOrBuiltinNode UNCACHED_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode.getUncached();
    private static final NodeSupplier<PyObjectSetItem> NODE_OBJECT_SET_ITEM = PyObjectSetItem::create;
    private static final PyObjectSetItem UNCACHED_OBJECT_SET_ITEM = PyObjectSetItem.getUncached();
    private static final NodeSupplier<PyObjectCallMethodObjArgs> NODE_OBJECT_CALL_METHOD_OBJ_ARGS = PyObjectCallMethodObjArgs::create;
    private static final PyObjectCallMethodObjArgs UNCACHED_OBJECT_CALL_METHOD_OBJ_ARGS = PyObjectCallMethodObjArgs.getUncached();
    private static final NodeSupplier<PyObjectIsTrueNode> NODE_OBJECT_IS_TRUE = PyObjectIsTrueNode::create;
    private static final PyObjectIsTrueNode UNCACHED_OBJECT_IS_TRUE = PyObjectIsTrueNode.getUncached();
    private static final NodeSupplier<GetItemNode> NODE_GET_ITEM = GetItemNode::create;

    private static final WriteGlobalNode UNCACHED_WRITE_GLOBAL = WriteGlobalNode.getUncached();
    private static final NodeFunction<String, WriteGlobalNode> NODE_WRITE_GLOBAL = WriteGlobalNode::create;

    private static final NodeFunction<String, DeleteGlobalNode> NODE_DELETE_GLOBAL = DeleteGlobalNode::create;

    private static final IntNodeFunction<Node> COMPARE_OP_FACTORY = (int op) -> {
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
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    };

    private static final IntNodeFunction<LookupAndCallInplaceNode> INPLACE_ARITH_FACTORY = (int op) -> {
        switch (op) {
            case INPLACE_POWER:
                return InplaceArithmetic.IPow.create();
            case INPLACE_MULTIPLY:
                return InplaceArithmetic.IMul.create();
            case INPLACE_MATRIX_MULTIPLY:
                return InplaceArithmetic.IMatMul.create();
            case INPLACE_TRUE_DIVIDE:
                return InplaceArithmetic.ITrueDiv.create();
            case INPLACE_FLOOR_DIVIDE:
                return InplaceArithmetic.IFloorDiv.create();
            case INPLACE_MODULO:
                return InplaceArithmetic.IMod.create();
            case INPLACE_ADD:
                return InplaceArithmetic.IAdd.create();
            case INPLACE_SUBTRACT:
                return InplaceArithmetic.ISub.create();
            case INPLACE_LSHIFT:
                return InplaceArithmetic.ILShift.create();
            case INPLACE_RSHIFT:
                return InplaceArithmetic.IRShift.create();
            case INPLACE_AND:
                return InplaceArithmetic.IAnd.create();
            case INPLACE_XOR:
                return InplaceArithmetic.IXor.create();
            case INPLACE_OR:
                return InplaceArithmetic.IOr.create();
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    };

    private final int stacksize;
    private final Signature signature;
    private final String name;
    public final String filename;

    private final CodeUnit co;

    @CompilationFinal(dimensions = 1) private final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final long[] longConsts;
    @CompilationFinal(dimensions = 1) private final String[] names;
    @CompilationFinal(dimensions = 1) private final String[] varnames;
    @CompilationFinal(dimensions = 1) private final String[] freevars;
    @CompilationFinal(dimensions = 1) private final String[] cellvars;

    @CompilationFinal(dimensions = 1) private final int[] exceptionHandlerRanges;

    /**
     * PE-final store for quickened bytecodes.
     */
    @CompilationFinal(dimensions = 1) private final int[] extraArgs;

    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    @CompilationFinal private Object osrMetadata;

    public static final FrameDescriptor DESCRIPTOR = new FrameDescriptor();
    private static final FrameSlot STACK_SLOT = DESCRIPTOR.addFrameSlot("stack", FrameSlotKind.Object);
    private static final FrameSlot FAST_SLOT = DESCRIPTOR.addFrameSlot("fast", FrameSlotKind.Object);
    private static final FrameSlot CELL_SLOT = DESCRIPTOR.addFrameSlot("cell", FrameSlotKind.Object);
    private static final FrameSlot FREE_SLOT = DESCRIPTOR.addFrameSlot("free", FrameSlotKind.Object);
    private static final FrameSlot BLOCKSTACK_SLOT = DESCRIPTOR.addFrameSlot("blockstack", FrameSlotKind.Object);
    private static final int MAXBLOCKS = 15; // 25% less than on CPython, shouldn't matter much

    private static final Node MARKER_NODE = new Node() {
        @Override
        public boolean isAdoptable() {
            return false;
        }
    };

    private static final Object UNREIFIED_EXC_TYPE = new Object();
    private static final Object UNREIFIED_EXC_VALUE = new Object();
    private static final Object UNREIFIED_EXC_TRACEBACK = new Object();

    public PBytecodeRootNode(TruffleLanguage<?> language, Signature sign, CodeUnit co) {
        this(language, DESCRIPTOR, sign, co);
    }

    public PBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor fd, Signature sign, CodeUnit co) {
        super(language, fd);
        this.signature = sign;
        this.bytecode = co.code;
        this.adoptedNodes = new Node[co.code.length];
        this.extraArgs = new int[co.code.length];
        this.consts = co.constants;
        this.longConsts = co.primitiveConstants;
        this.names = co.names;
        this.varnames = co.varnames;
        this.freevars = co.freevars;
        this.cellvars = co.cellvars;
        this.stacksize = co.stacksize;
        this.filename = co.filename;
        this.name = co.name;
        this.exceptionHandlerRanges = co.exceptionHandlerRanges;
        this.co = co;
        assert stacksize < Math.pow(2, 12) : "stacksize cannot be larger than 12-bit range";
        assert bytecode.length < Math.pow(2, 16) : "bytecode cannot be longer than 16-bit range";
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
        calleeContext.enter(frame);
        try {
            // CPython has an array of object called "localsplus" with everything. We use separate
            // arrays.
            Object[] stack = new Object[stacksize];
            int[] blockstack = new int[MAXBLOCKS];
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
            frame.setObject(BLOCKSTACK_SLOT, blockstack);

            return executeOSR(frame, encodeBCI(0) | encodeStackTop(-1) | encodeBlockstackTop(-1), args);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @FunctionalInterface
    private static interface NodeSupplier<T> {
        T get();
    }

    @FunctionalInterface
    private static interface NodeFunction<A, T> {
        T apply(A argument);
    }

    @FunctionalInterface
    private static interface IntNodeFunction<T> {
        T apply(int argument);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node node, NodeFunction<A, T> nodeSupplier, int bytecodeIndex, A argument) {
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNode(node, nodeSupplier, bytecodeIndex, argument);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <A, T extends Node> T doInsertChildNode(Node node, NodeFunction<A, T> nodeSupplier, int bytecodeIndex, A argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert node == null;
        T newNode = nodeSupplier.apply(argument);
        adoptedNodes[bytecodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node node, T uncached, NodeFunction<A, T> nodeSupplier, int bytecodeIndex, A argument) {
        if (node != null && node != MARKER_NODE) {
            return (T) node;
        }
        return doInsertChildNode(node, uncached, nodeSupplier, bytecodeIndex, argument);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <A, T extends Node> T doInsertChildNode(Node node, T uncached, NodeFunction<A, T> nodeSupplier, int bytecodeIndex, A argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (node == null) { // first execution uncached
            adoptedNodes[bytecodeIndex] = MARKER_NODE;
            return uncached;
        } else {
            assert node == MARKER_NODE; // second execution caches
            T newNode = nodeSupplier.apply(argument);
            adoptedNodes[bytecodeIndex] = insert(newNode);
            return newNode;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node node, IntNodeFunction<T> nodeSupplier, int bytecodeIndex, int argument) {
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNode(node, nodeSupplier, bytecodeIndex, argument);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node node, IntNodeFunction<T> nodeSupplier, int bytecodeIndex, int argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert node == null;
        T newNode = nodeSupplier.apply(argument);
        adoptedNodes[bytecodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node node, T uncached, IntNodeFunction<T> nodeSupplier, int bytecodeIndex, int argument) {
        if (node != null && node != MARKER_NODE) {
            return (T) node;
        }
        return doInsertChildNode(node, uncached, nodeSupplier, bytecodeIndex, argument);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node node, T uncached, IntNodeFunction<T> nodeSupplier, int bytecodeIndex, int argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (node == null) { // first execution uncached
            adoptedNodes[bytecodeIndex] = MARKER_NODE;
            return uncached;
        } else if (node == MARKER_NODE) { // second execution caches
            T newNode = nodeSupplier.apply(argument);
            adoptedNodes[bytecodeIndex] = insert(newNode);
            return newNode;
        } else {
            return (T) node;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node node, NodeSupplier<T> nodeSupplier, int bytecodeIndex) {
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNode(node, nodeSupplier, bytecodeIndex);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node node, NodeSupplier<T> nodeSupplier, int bytecodeIndex) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert node == null;
        T newNode = nodeSupplier.get();
        adoptedNodes[bytecodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node node, T uncached, NodeSupplier<T> nodeSupplier, int bytecodeIndex) {
        if (node != null && node != MARKER_NODE) {
            return (T) node;
        }
        return doInsertChildNode(node, uncached, nodeSupplier, bytecodeIndex);
    }

    @BytecodeInterpreterSwitchBoundary
    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node node, T uncached, NodeSupplier<T> nodeSupplier, int bytecodeIndex) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (node == null) { // first execution uncached
            adoptedNodes[bytecodeIndex] = MARKER_NODE;
            return uncached;
        } else { // second execution caches
            assert node == MARKER_NODE;
            T newNode = nodeSupplier.get();
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
        int[] blockstack = new int[MAXBLOCKS];
        frame.setObject(STACK_SLOT, stack);
        frame.setObject(FAST_SLOT, fast);
        frame.setObject(CELL_SLOT, cell);
        frame.setObject(FREE_SLOT, free);
        frame.setObject(BLOCKSTACK_SLOT, blockstack);
        try {
            copyStack(stack, (Object[]) parentFrame.getObject(STACK_SLOT));
            copyLocals(fast, (Object[]) parentFrame.getObject(FAST_SLOT));
            copyCellvars(cell, (Object[]) parentFrame.getObject(CELL_SLOT));
            copyFreevars(free, (Object[]) parentFrame.getObject(FREE_SLOT));
            copyBlocks(blockstack, (int[]) parentFrame.getObject(BLOCKSTACK_SLOT));
        } catch (FrameSlotTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        try {
            Object[] stack = (Object[]) parentFrame.getObject(STACK_SLOT);
            Object[] fast = (Object[]) parentFrame.getObject(FAST_SLOT);
            Object[] cell = (Object[]) parentFrame.getObject(CELL_SLOT);
            Object[] free = (Object[]) parentFrame.getObject(FREE_SLOT);
            int[] blockstack = (int[]) parentFrame.getObject(BLOCKSTACK_SLOT);
            Object[] osrStack = (Object[]) osrFrame.getObject(STACK_SLOT);
            Object[] osrFast = (Object[]) osrFrame.getObject(FAST_SLOT);
            Object[] osrCell = (Object[]) osrFrame.getObject(CELL_SLOT);
            Object[] osrFree = (Object[]) osrFrame.getObject(FREE_SLOT);
            int[] osrBlockstack = (int[]) osrFrame.getObject(BLOCKSTACK_SLOT);
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
    private void copyStack(Object[] dst, Object[] src) {
        for (int i = 0; i < stacksize; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private void copyLocals(Object[] dst, Object[] src) {
        for (int i = 0; i < varnames.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private void copyCellvars(Object[] dst, Object[] src) {
        for (int i = 0; i < cellvars.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private void copyFreevars(Object[] dst, Object[] src) {
        for (int i = 0; i < freevars.length; i++) {
            dst[i] = src[i];
        }
    }

    @ExplodeLoop
    private static void copyBlocks(int[] dst, int[] src) {
        for (int i = 0; i < MAXBLOCKS; i++) {
            dst[i] = src[i];
        }
    }

    private static int decodeBCI(int target) {
        return (target >>> 16) & 0xffff; // unsigned
    }

    private static int decodeStackTop(int target) {
        return ((target >>> 4) & 0xfff) - 1;
    }

    private static int decodeBlockstackTop(int target) {
        return ((target & 0xf) - 1);
    }

    private static boolean isBlockTypeFinally(int target) {
        return (target & 1) == 1;
    }

    private static boolean isBlockTypeExcept(int target) {
        return (target & 1) == 0;
    }

    private static int encodeBCI(int bci) {
        return bci << 16;
    }

    private static int encodeStackTop(int stackTop) {
        return (stackTop + 1) << 4;
    }

    private static int encodeBlockstackTop(int blockstackTop) {
        return blockstackTop + 1;
    }

    private static int encodeBlockTypeFinally() {
        return 1;
    }

    private static int encodeBlockTypeExcept() {
        return 0;
    }

    private static final Object BOOLEAN_MARKER = new Object();
    private static final Object INTEGER_MARKER = new Object();
    private static final Object LONG_MARKER = new Object();
    private static final Object DOUBLE_MARKER = new Object();

    /**
     * @param target - encodes bci (16bit), stackTop (12bit), and blockstackTop (4bit)
     */
    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @SuppressWarnings("fallthrough")
    public Object executeOSR(VirtualFrame frame, int target, Object originalArgs) {
        PythonLanguage lang = PythonLanguage.get(this);
        PythonContext context = PythonContext.get(this);
        PythonModule builtins = context.getBuiltins();

        boolean inInterpreter = CompilerDirectives.inInterpreter();

        Object globals = PArguments.getGlobals((Object[]) originalArgs);
        Object locals = PArguments.getSpecialArgument((Object[]) originalArgs);
        Object[] stack = (Object[]) FrameUtil.getObjectSafe(frame, STACK_SLOT);
        Object[] fastlocals = (Object[]) FrameUtil.getObjectSafe(frame, FAST_SLOT);
        long[] longlocals = new long[varnames.length];
        Object[] celllocals = (Object[]) FrameUtil.getObjectSafe(frame, CELL_SLOT);
        Object[] freelocals = (Object[]) FrameUtil.getObjectSafe(frame, FREE_SLOT);
        int[] blockstack = (int[]) FrameUtil.getObjectSafe(frame, BLOCKSTACK_SLOT);

        int loopCount = 0;
        int stackTop = decodeStackTop(target);
        int blockstackTop = decodeBlockstackTop(target);
        int bci = decodeBCI(target);
        int oparg = Byte.toUnsignedInt(bytecode[bci + 1]);

        byte[] localBC = bytecode;
        int[] localArgs = extraArgs;
        Object[] localConsts = consts;
        String[] localNames = names;
        Node[] localNodes = adoptedNodes;

        verifyBeforeLoop(target, stackTop, blockstackTop, bci, oparg, localBC);

        while (true) {
            // System.out.println(java.util.Arrays.toString(stack));
            // System.out.println(bci);

            final byte bc = localBC[bci];

            verifyInLoop(stack, fastlocals, celllocals, freelocals, blockstack, stackTop, blockstackTop, bci, bc);

            try {
                switch (bc) {
                    case NOP:
                        break;
                    case LOAD_FAST: {
                        Object value = fastlocals[oparg];
                        if (!inInterpreter) {
                            if (value == BOOLEAN_MARKER) {
                                value = longlocals[oparg] == 1;
                            } else if (value == INTEGER_MARKER) {
                                // CompilerDirectives.transferToInterpreterAndInvalidate();
                                // localBC[bci] = LOAD_FAST_INT;
                                value = (int) longlocals[oparg];
                            } else if (value == LONG_MARKER) {
                                value = longlocals[oparg];
                            } else if (value == DOUBLE_MARKER) {
                                value = Double.longBitsToDouble(longlocals[oparg]);
                            }
                        }
                        if (value == null) {
                            if (localArgs[bci >> 1] == 0) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                localArgs[bci >> 1] = 1;
                                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            } else {
                                PRaiseNode raiseNode = insertChildNode(localNodes[bci], NODE_RAISE, bci);
                                throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            }
                        }
                        stack[++stackTop] = value;
                        break;
                    }
                    case LOAD_CONST:
                        stack[++stackTop] = localConsts[oparg];
                        break;
                    case STORE_FAST: {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Object value = stack[stackTop];
                        fastlocals[oparg] = value;
                        if (value instanceof Boolean) {
                            localBC[bci] = STORE_FAST_BOOLEAN;
                        } else if (value instanceof Integer) {
                            localBC[bci] = STORE_FAST_INT;
                        } else if (value instanceof Long) {
                            localBC[bci] = STORE_FAST_LONG;
                        } else if (value instanceof Double) {
                            localBC[bci] = STORE_FAST_DOUBLE;
                        } else {
                            localBC[bci] = STORE_FAST_GENERIC;
                        }
                        stack[stackTop--] = null;
                        break;
                    }
                    case STORE_FAST_BOOLEAN: {
                        Object value = stack[stackTop];
                        if (value instanceof Boolean) {
                            if (inInterpreter) {
                                fastlocals[oparg] = value;
                            } else {
                                fastlocals[oparg] = BOOLEAN_MARKER;
                                longlocals[oparg] = value == Boolean.TRUE ? 1 : 0;
                            }
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            localBC[bci] = STORE_FAST_GENERIC;
                            fastlocals[oparg] = value;
                        }
                        stack[stackTop--] = null;
                        break;
                    }
                    case STORE_FAST_INT: {
                        Object value = stack[stackTop];
                        if (value instanceof Integer) {
                            if (inInterpreter) {
                                fastlocals[oparg] = value;
                            } else {
                                fastlocals[oparg] = INTEGER_MARKER;
                                longlocals[oparg] = (int) value;
                            }
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            if (value instanceof Long) {
                                localBC[bci] = STORE_FAST_LONG;
                            } else {
                                localBC[bci] = STORE_FAST_GENERIC;
                            }
                            fastlocals[oparg] = value;
                        }
                        stack[stackTop--] = null;
                        break;
                    }
                    case STORE_FAST_LONG: {
                        Object value = stack[stackTop];
                        if (value instanceof Long) {
                            if (inInterpreter) {
                                fastlocals[oparg] = value;
                            } else {
                                fastlocals[oparg] = LONG_MARKER;
                                longlocals[oparg] = (long) value;
                            }
                        } else if (value instanceof Integer) {
                            if (inInterpreter) {
                                fastlocals[oparg] = value;
                            } else {
                                fastlocals[oparg] = LONG_MARKER;
                                longlocals[oparg] = (int) value;
                            }
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            localBC[bci] = STORE_FAST_GENERIC;
                            fastlocals[oparg] = value;
                        }
                        stack[stackTop--] = null;
                        break;
                    }
                    case STORE_FAST_DOUBLE: {
                        Object value = stack[stackTop];
                        if (value instanceof Double) {
                            if (inInterpreter) {
                                fastlocals[oparg] = value;
                            } else {
                                fastlocals[oparg] = DOUBLE_MARKER;
                                longlocals[oparg] = Double.doubleToRawLongBits((double) value);
                            }
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            localBC[bci] = STORE_FAST_GENERIC;
                            fastlocals[oparg] = value;
                        }
                        stack[stackTop--] = null;
                        break;
                    }
                    case STORE_FAST_GENERIC:
                        fastlocals[oparg] = stack[stackTop];
                        stack[stackTop--] = null;
                        break;
                    case POP_TOP:
                        stack[stackTop--] = null;
                        break;
                    case ROT_TWO: {
                        Object top = stack[stackTop];
                        stack[stackTop] = stack[stackTop - 1];
                        stack[stackTop - 1] = top;
                        break;
                    }
                    case ROT_THREE: {
                        Object top = stack[stackTop];
                        stack[stackTop] = stack[stackTop - 1];
                        stack[stackTop - 1] = stack[stackTop - 2];
                        stack[stackTop - 2] = top;
                        break;
                    }
                    case ROT_FOUR: {
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
                    case UNARY_POSITIVE: {
                        PosNode posNode = insertChildNode(localNodes[bci], NODE_UNARY_POS, bci);
                        stack[stackTop] = posNode.execute(frame, stack[stackTop]);
                        break;
                    }
                    case UNARY_NEGATIVE: {
                        NegNode negNode = insertChildNode(localNodes[bci], NODE_UNARY_NEG, bci);
                        stack[stackTop] = negNode.execute(frame, stack[stackTop]);
                        break;
                    }
                    case UNARY_NOT: {
                        boolean result = insertChildNode(localNodes[bci], UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE, bci).execute(frame, stack[stackTop]);
                        stack[stackTop] = !result;
                        break;
                    }
                    case UNARY_INVERT: {
                        InvertNode invertNode = insertChildNode(localNodes[bci], NODE_UNARY_INVERT, bci);
                        stack[stackTop] = invertNode.execute(frame, stack[stackTop]);
                        break;
                    }
                    case BINARY_POWER: {
                        PowNode powNode = insertChildNode(localNodes[bci], NODE_BINARY_POW, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = powNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_MULTIPLY: {
                        stackTop = bytecodeBinaryMultiply(frame, stack, stackTop, bci);
                        break;
                    }
                    case BINARY_MATRIX_MULTIPLY: {
                        MatMulNode matMulNode = insertChildNode(localNodes[bci], NODE_BINARY_MATMUL, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = matMulNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_TRUE_DIVIDE: {
                        TrueDivNode trueDivNode = insertChildNode(localNodes[bci], NODE_BINARY_TRUEDIV, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = trueDivNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_FLOOR_DIVIDE: {
                        FloorDivNode floorDivNode = insertChildNode(localNodes[bci], NODE_BINARY_FLOORDIV, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = floorDivNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_MODULO: {
                        ModNode modNode = insertChildNode(localNodes[bci], NODE_BINARY_MOD, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = modNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_ADD: {
                        AddNode addNode = insertChildNode(localNodes[bci], NODE_BINARY_ADD, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = addNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_SUBTRACT: {
                        SubNode subNode = insertChildNode(localNodes[bci], NODE_BINARY_SUB, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = subNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_SUBSCR: {
                        GetItemNode getItemNode = insertChildNode(localNodes[bci], NODE_GET_ITEM, bci);
                        Object slice = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = getItemNode.execute(frame, stack[stackTop], slice);
                        break;
                    }
                    case BINARY_LSHIFT: {
                        LShiftNode lShiftNode = insertChildNode(localNodes[bci], NODE_BINARY_LSHIFT, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = lShiftNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_RSHIFT: {
                        RShiftNode rShiftNode = insertChildNode(localNodes[bci], NODE_BINARY_RSHIFT, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = rShiftNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_AND: {
                        BitAndNode bitAndNode = insertChildNode(localNodes[bci], NODE_BINARY_BITAND, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = bitAndNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_XOR: {
                        BitXorNode bitXorNode = insertChildNode(localNodes[bci], NODE_BINARY_BITXOR, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = bitXorNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case BINARY_OR: {
                        BitOrNode bitOrNode = insertChildNode(localNodes[bci], NODE_BINARY_BITOR, bci);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        stack[stackTop] = bitOrNode.executeObject(frame, stack[stackTop], right);
                        break;
                    }
                    case LIST_APPEND: {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_CALL_METHOD_OBJ_ARGS, NODE_OBJECT_CALL_METHOD_OBJ_ARGS, bci);
                        Object list = stack[stackTop - oparg];
                        Object value = stack[stackTop];
                        stack[stackTop--] = null;
                        callNode.execute(frame, list, "append", value);
                        break;
                    }
                    case SET_ADD: {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_CALL_METHOD_OBJ_ARGS, NODE_OBJECT_CALL_METHOD_OBJ_ARGS, bci);
                        Object value = stack[stackTop];
                        stack[stackTop--] = null;
                        callNode.execute(frame, stack[stackTop], "add", value);
                        break;
                    }
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
                    case INPLACE_OR: {
                        LookupAndCallInplaceNode opNode = insertChildNode(localNodes[bci], INPLACE_ARITH_FACTORY, bci, (int) bc);
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        Object left = stack[stackTop];
                        stack[stackTop] = opNode.execute(frame, left, right);
                        break;
                    }
                    case STORE_SUBSCR: {
                        PyObjectSetItem setItem = insertChildNode(localNodes[bci], UNCACHED_OBJECT_SET_ITEM, NODE_OBJECT_SET_ITEM, bci);
                        Object index = stack[stackTop];
                        stack[stackTop--] = null;
                        Object container = stack[stackTop];
                        stack[stackTop--] = null;
                        Object value = stack[stackTop];
                        stack[stackTop--] = null;
                        setItem.execute(frame, container, index, value);
                        break;
                    }
                    case DELETE_SUBSCR: {
                        DeleteItemNode delItem = insertChildNode(localNodes[bci], NODE_DELETE_ITEM, bci);
                        Object slice = stack[stackTop];
                        stack[stackTop--] = null;
                        Object container = stack[stackTop];
                        stack[stackTop--] = null;
                        delItem.executeWith(frame, container, slice);
                        break;
                    }
                    case PRINT_EXPR:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "bc print expr");
                    case RAISE_VARARGS: {
                        stackTop = bytecodeRaiseVarargs(frame, stack, stackTop, bci, oparg, localNodes);
                        break;
                    }
                    case RETURN_VALUE:
                        if (inInterpreter) {
                            LoopNode.reportLoopCount(this, loopCount);
                        }
                        return stack[stackTop];
                    case GET_AITER:
                    case GET_ANEXT:
                    case GET_AWAITABLE:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "async bytecodes");
                    case YIELD_FROM:
                    case YIELD_VALUE:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "yield bytecodes");
                    case POP_EXCEPT: {
                        assert isBlockTypeExcept(blockstack[blockstackTop]);
                        blockstackTop--;
                        // pop the previous exception info (probably wasn't even materialized)
                        stack[stackTop--] = null;
                        stack[stackTop--] = null;
                        stack[stackTop--] = null;
                        break;
                    }
                    case POP_BLOCK:
                        blockstackTop--;
                        break;
                    case POP_FINALLY: {
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
                            assert isBlockTypeExcept(blockstack[blockstackTop]);
                            assert stackTop == decodeStackTop(blockstack[blockstackTop]) + 3;
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
                        int savedStackTop = localArgs[bci >> 1];
                        if (savedStackTop == 0) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            localArgs[bci >> 1] = encodeStackTop(stackTop) | encodeBlockstackTop(1);
                        } else {
                            stackTop = decodeStackTop(savedStackTop);
                        }
                        break;
                    }
                    case CALL_FINALLY:
                        stack[++stackTop] = bci + 2;
                        bci = oparg + 2;
                        oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                        continue;
                    case BEGIN_FINALLY:
                        stack[++stackTop] = null;
                        break;
                    case END_FINALLY: {
                        Object exc = stack[stackTop];
                        stack[stackTop--] = null;
                        int savedStackTop = localArgs[bci >> 1];
                        if (exc == null) {
                            if (savedStackTop == 0) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                // don't care about blockstack top, but we want a bit set
                                localArgs[bci >> 1] = encodeStackTop(stackTop) | encodeBlockstackTop(1);
                            } else {
                                stackTop = decodeStackTop(savedStackTop);
                            }
                            // nothing to do, we just fall through
                        } else if (exc instanceof Integer) {
                            if (savedStackTop == 0) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                localArgs[bci >> 1] = encodeBCI((int) exc) | encodeStackTop(stackTop) | encodeBlockstackTop(1);
                                bci = (int) exc;
                            } else {
                                bci = decodeBCI(savedStackTop);
                                stackTop = decodeStackTop(savedStackTop);
                            }
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        } else {
                            // CPython expects 6 values, the current exc_info and the previous one
                            // We usually just have placeholders, with a PException object in
                            // stackTop
                            // first, pop the remaining two current exc_info entries
                            stack[stackTop--] = null;
                            stack[stackTop--] = null;
                            if (savedStackTop == 0) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                localArgs[bci >> 1] = encodeStackTop(stackTop) | encodeBlockstackTop(1);
                            } else {
                                stackTop = decodeStackTop(savedStackTop);
                            }
                            // throw the exception again for the next handler to run
                            throw (AbstractTruffleException) exc;
                        }
                        break;
                    }
                    case END_ASYNC_FOR:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "async bytecodes");
                    case LOAD_BUILD_CLASS: {
                        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes[bci], UNCACHED_READ_GLOBAL_OR_BUILTIN, NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS, bci);
                        stack[++stackTop] = read.read(frame, globals, __BUILD_CLASS__);
                        break;
                    }
                    case STORE_NAME: {
                        stackTop = bytecodeStoreName(frame, lang, globals, locals, stack, stackTop, bci, oparg, localNames, localNodes);
                        break;
                    }
                    case DELETE_NAME: {
                        String varname = localNames[oparg];
                        if (locals != null) {
                            PyObjectDelItem delItemNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_DEL_ITEM, NODE_OBJECT_DEL_ITEM, bci);
                            delItemNode.execute(frame, locals, varname);
                        } else {
                            DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes[bci + 1], NODE_DELETE_GLOBAL, bci + 1, varname);
                            deleteGlobalNode.executeWithGlobals(frame, globals);
                        }
                        break;
                    }
                    case UNPACK_SEQUENCE:
                    case UNPACK_EX:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "unpack bytecodes");
                    case STORE_ATTR: {
                        PyObjectSetAttr callNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_SET_ATTR, NODE_OBJECT_SET_ATTR, bci);
                        String varname = localNames[oparg];
                        Object owner = stack[stackTop];
                        stack[stackTop--] = null;
                        Object value = stack[stackTop];
                        stack[stackTop--] = null;
                        callNode.execute(frame, owner, varname, value);
                        break;
                    }
                    case DELETE_ATTR: {
                        PyObjectSetAttr callNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_SET_ATTR, NODE_OBJECT_SET_ATTR, bci);
                        String varname = localNames[oparg];
                        Object owner = stack[stackTop];
                        stack[stackTop--] = null;
                        callNode.delete(frame, owner, varname);
                        break;
                    }
                    case STORE_GLOBAL: {
                        String varname = localNames[oparg];
                        WriteGlobalNode writeGlobalNode = insertChildNode(localNodes[bci], UNCACHED_WRITE_GLOBAL, NODE_WRITE_GLOBAL, bci, varname);
                        writeGlobalNode.write(frame, globals, varname, stack[stackTop]);
                        stack[stackTop--] = null;
                        break;
                    }
                    case DELETE_GLOBAL: {
                        String varname = localNames[oparg];
                        DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes[bci], NODE_DELETE_GLOBAL, bci, varname);
                        deleteGlobalNode.executeWithGlobals(frame, globals);
                        break;
                    }
                    case LOAD_NAME: {
                        String varname = localNames[oparg];
                        Object result = null;
                        if (locals != null) {
                            Node helper = localNodes[bci];
                            if (helper instanceof HashingStorageLibrary) {
                                if (locals instanceof PDict && ((PDict) locals).getShape() == PythonBuiltinClassType.PDict.getInstanceShape(lang)) {
                                    HashingStorageLibrary lib = (HashingStorageLibrary) helper;
                                    result = lib.getItem(((PDict) locals).getDictStorage(), varname);
                                } else { // generalize
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    GetItemNode newNode = GetItemNode.create();
                                    localNodes[bci] = helper.replace(newNode);
                                    result = newNode.execute(frame, locals, varname);
                                }
                            } else if (helper instanceof GetItemNode) {
                                result = ((GetItemNode) helper).execute(frame, locals, varname);
                            } else {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                assert helper == null;
                                if (locals instanceof PDict && ((PDict) locals).getShape() == PythonBuiltinClassType.PDict.getInstanceShape(lang)) {
                                    HashingStorageLibrary lib = insertChildNode(localNodes[bci], UNCACHED_HASHING_STORAGE_LIBRARY, NODE_HASHING_STORAGE_LIBRARY, bci);
                                    result = lib.getItem(((PDict) locals).getDictStorage(), varname);
                                } else {
                                    GetItemNode newNode = insertChildNode(localNodes[bci], NODE_GET_ITEM, bci);
                                    result = newNode.execute(frame, locals, varname);
                                }
                            }
                        }
                        if (result == null) {
                            ReadGlobalOrBuiltinNode read = insertChildNode(localNodes[bci + 1], UNCACHED_READ_GLOBAL_OR_BUILTIN, NODE_READ_GLOBAL_OR_BUILTIN, bci + 1,
                                            varname);
                            result = read.read(frame, globals, varname);
                        }
                        stack[++stackTop] = result;
                        break;
                    }
                    case LOAD_GLOBAL: {
                        String varname = localNames[oparg];
                        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes[bci], UNCACHED_READ_GLOBAL_OR_BUILTIN, NODE_READ_GLOBAL_OR_BUILTIN, bci, varname);
                        stack[++stackTop] = read.read(frame, globals, varname);
                        break;
                    }
                    case DELETE_FAST: {
                        Object value = fastlocals[oparg];
                        if (value == null) {
                            PRaiseNode raiseNode = insertChildNode(localNodes[bci], UNCACHED_RAISE, NODE_RAISE, bci);
                            throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                        }
                        fastlocals[oparg] = null;
                        break;
                    }
                    case DELETE_DEREF:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "DELETE_DEREF");
                    case LOAD_CLOSURE:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "LOAD_CLOSURE");
                    case LOAD_CLASSDEREF:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "LOAD_CLASSDEREF");
                    case LOAD_DEREF:
                    case STORE_DEREF:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "deref load/store");
                    case BUILD_STRING:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "build string");
                    case BUILD_TUPLE: {
                        Object[] list = new Object[oparg];
                        while (oparg > 0) {
                            oparg--;
                            list[oparg] = stack[stackTop];
                            stack[stackTop--] = null;
                        }
                        stack[++stackTop] = factory.createTuple(list);
                        break;
                    }
                    case BUILD_LIST: {
                        Object[] list = new Object[oparg];
                        while (oparg > 0) {
                            oparg--;
                            list[oparg] = stack[stackTop];
                            stack[stackTop--] = null;
                        }
                        stack[++stackTop] = factory.createList(list);
                        break;
                    }
                    case BUILD_TUPLE_UNPACK_WITH_CALL:
                    case BUILD_TUPLE_UNPACK:
                    case BUILD_LIST_UNPACK:
                    case BUILD_SET:
                    case BUILD_SET_UNPACK:
                    case BUILD_MAP:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "build bytecodes");
                    case SETUP_ANNOTATIONS:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "setup annotations");
                    case BUILD_CONST_KEY_MAP: {
                        PTuple keys = ((PTuple) stack[stackTop]);
                        SequenceStorage keysStorage = keys.getSequenceStorage();
                        EconomicMapStorage map = EconomicMapStorage.create(oparg);
                        HashingStorageLibrary mapLib = insertChildNode(localNodes[bci], UNCACHED_HASHING_STORAGE_LIBRARY, NODE_HASHING_STORAGE_LIBRARY_DIRECT, bci, map);
                        for (int i = oparg; i > 0; i--) {
                            Object key = keysStorage.getItemNormalized(oparg - i);
                            int stackIdx = stackTop - oparg + 1;
                            Object value = stack[stackIdx];
                            stack[stackIdx] = null;
                            mapLib.setItemWithFrame(map, key, value, ConditionProfile.getUncached(), frame);
                        }
                        stackTop = stackTop - oparg;
                        stack[++stackTop] = factory.createDict(map);
                        break;
                    }
                    case BUILD_MAP_UNPACK:
                    case BUILD_MAP_UNPACK_WITH_CALL:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "build bytecodes");
                    case MAP_ADD:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "MAP_ADD");
                    case LOAD_ATTR: {
                        PyObjectGetAttr getAttr = insertChildNode(localNodes[bci], UNCACHED_OBJECT_GET_ATTR, NODE_OBJECT_GET_ATTR, bci);
                        String varname = localNames[oparg];
                        Object owner = stack[stackTop];
                        Object value = getAttr.execute(frame, owner, varname);
                        stack[stackTop] = value;
                        break;
                    }
                    case COMPARE_OP: {
                        Object right = stack[stackTop];
                        stack[stackTop--] = null;
                        Object left = stack[stackTop];
                        Node opNode = insertChildNode(localNodes[bci], COMPARE_OP_FACTORY, bci, oparg);
                        if (opNode instanceof BinaryComparisonNode) {
                            stack[stackTop] = ((BinaryComparisonNode) opNode).executeObject(frame, left, right);
                        } else if (opNode instanceof ContainsNode) {
                            Object result = ((ContainsNode) opNode).executeObject(frame, left, right);
                            if (oparg == 7) {
                                CoerceToBooleanNode invert = insertChildNode(localNodes[bci + 1], NODE_COERCE_TO_BOOLEAN_IF_FALSE, bci + 1);
                                stack[stackTop] = invert.execute(frame, result);
                            } else {
                                stack[stackTop] = result;
                            }
                        } else if (opNode instanceof IsNode) {
                            Object result = ((IsNode) opNode).execute(left, right);
                            if (oparg == 9) {
                                CoerceToBooleanNode invert = insertChildNode(localNodes[bci + 1], NODE_COERCE_TO_BOOLEAN_IF_FALSE, bci + 1);
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
                        break;
                    }
                    case IMPORT_NAME: {
                        stackTop = bytecodeImportName(frame, context, builtins, globals, stack, stackTop, bci, oparg, localNames, localNodes);
                        break;
                    }
                    case IMPORT_STAR:
                    case IMPORT_FROM:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "import start / import from");
                    case JUMP_FORWARD:
                        bci += oparg + 2;
                        oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                        continue;
                    case POP_JUMP_IF_FALSE: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes[bci], UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE, bci);
                        Object cond = stack[stackTop];
                        stack[stackTop--] = null;
                        // TODO: this hack artificially increases profiled loop counts
                        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, !isTrue.execute(frame, cond))) {
                            bci = oparg;
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        }
                        break;
                    }
                    case POP_JUMP_IF_TRUE: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes[bci], UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE, bci);
                        Object cond = stack[stackTop];
                        stack[stackTop--] = null;
                        if (isTrue.execute(frame, cond)) {
                            bci = oparg;
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        }
                        break;
                    }
                    case JUMP_IF_FALSE_OR_POP: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes[bci], UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE, bci);
                        Object cond = stack[stackTop];
                        if (!isTrue.execute(frame, cond)) {
                            bci = oparg;
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        } else {
                            stack[stackTop--] = null;
                        }
                        break;
                    }
                    case JUMP_IF_TRUE_OR_POP: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes[bci], UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE, bci);
                        Object cond = stack[stackTop];
                        if (isTrue.execute(frame, cond)) {
                            bci = oparg;
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        } else {
                            stack[stackTop--] = null;
                        }
                        break;
                    }
                    case JUMP_ABSOLUTE:
                        if (oparg < bci) {
                            if (inInterpreter) {
                                loopCount++;
                                if (BytecodeOSRNode.pollOSRBackEdge(this)) {
                                    // we're in the interpreter, so the unboxed storage for locals
                                    // is not used
                                    Object osrResult = BytecodeOSRNode.tryOSR(this, encodeBCI(oparg) | encodeStackTop(stackTop) | encodeBlockstackTop(blockstackTop), originalArgs, null, frame);
                                    if (osrResult != null) {
                                        LoopNode.reportLoopCount(this, loopCount);
                                        return osrResult;
                                    }
                                }
                            }
                        }
                        bci = oparg;
                        oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                        continue;
                    case GET_ITER:
                        stack[stackTop] = insertChildNode(localNodes[bci], UNCACHED_OBJECT_GET_ITER, NODE_OBJECT_GET_ITER, bci).execute(frame, stack[stackTop]);
                        break;
                    case GET_YIELD_FROM_ITER: {
                        Object iterable = stack[stackTop];
                        // TODO: handle coroutines iterable
                        if (!(iterable instanceof PGenerator)) {
                            PyObjectGetIter getIter = insertChildNode(localNodes[bci], UNCACHED_OBJECT_GET_ITER, NODE_OBJECT_GET_ITER, bci);
                            stack[stackTop] = getIter.execute(frame, iterable);
                        }
                        break;
                    }
                    case FOR_ITER: {
                        try {
                            Object next = insertChildNode(localNodes[bci], UNCACHED_GET_NEXT, NODE_GET_NEXT, bci).execute(frame, stack[stackTop]);
                            stack[++stackTop] = next;
                        } catch (PException e) {
                            e.expect(StopIteration, insertChildNode(localNodes[bci + 1], UNCACHED_IS_BUILTIN_CLASS_PROFILE, NODE_IS_BUILTIN_CLASS_PROFILE, bci + 1));
                            stack[stackTop--] = null;
                            bci += oparg + 2;
                            oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                            continue;
                        }
                        break;
                    }
                    case SETUP_FINALLY: {
                        blockstack[++blockstackTop] = encodeBCI(bci + oparg) | encodeStackTop(stackTop) | encodeBlockTypeFinally();
                        break;
                    }
                    case BEFORE_ASYNC_WITH:
                    case SETUP_ASYNC_WITH:
                    case SETUP_WITH:
                    case WITH_CLEANUP_START:
                    case WITH_CLEANUP_FINISH:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "with blocks");
                    case LOAD_METHOD: {
                        String methodName = localNames[oparg];
                        PyObjectGetMethod getMethod = insertChildNode(localNodes[bci], UNCACHED_OBJECT_GET_METHOD, NODE_OBJECT_GET_METHOD, bci);
                        Object receiver = stack[stackTop];
                        stack[stackTop] = getMethod.execute(frame, stack[stackTop], methodName);
                        stack[++stackTop] = receiver;
                        break;
                    }
                    case CALL_METHOD:
                        // Python's LOAD_METHOD/CALL_METHOD optimization is not useful for us, we
                        // use BoundDescriptor as wrapper from LOAD_METHOD when it's not a normal
                        // method call, and Call(Unary/.../)Node deal with that directly. However,
                        // there's a different alignment, and to use the code below, we need to
                        // increment oparg by 1, to account for the receiver.
                        oparg += 1;
                    case CALL_FUNCTION: {
                        stackTop = bytecodeCallFunction(frame, stack, stackTop, bci, oparg, localNodes);
                        break;
                    }
                    case CALL_FUNCTION_KW: {
                        stackTop = bytecodeCallFunctionKw(frame, stack, stackTop, bci, oparg, localNodes);
                        break;
                    }
                    case CALL_FUNCTION_EX: {
                        stackTop = bytecodeCallFunctionEx(frame, stack, stackTop, bci, oparg, localNodes);
                        break;
                    }
                    case MAKE_FUNCTION:
                        stackTop = bytecodeMakeFunction(globals, stack, stackTop, bci, oparg);
                        break;
                    case BUILD_SLICE:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "BUILD_SLICE");
                    case FORMAT_VALUE:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "FORMAT_VALUE");
                    case EXTENDED_ARG:
                        bci += 2;
                        oparg = Byte.toUnsignedInt(localBC[bci + 1]) | (oparg << 8);
                        continue;
                    default:
                        throw insertChildNode(localNodes[bci], NODE_RAISE, bci).raise(SystemError, "not implemented bytecode");
                }
                // prepare next loop
                bci += 2;
                oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                // TODO: avoid extra read
                // if (localBC[bci] >= HAVE_ARGUMENT) oparg = Byte.toUnsignedInt(localBC[bci +
                // 1]);
            } catch (PException e) {
                if (blockstackRanges == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    blockstackRanges = new long[0];
                }
                long blockstackThumbprint = findHandler(stackTop, stack, blockstack, blockstackTop, bci);
                CompilerAsserts.partialEvaluationConstant(blockstackThumbprint);
                // now execute what the thumbprint tells us
                int stackTopAfterExcepts = decodeExceptBlockStackTop(blockstackThumbprint);
                stackTop = unwindExceptHandler(stack, stackTop, stackTopAfterExcepts);
                int stackTopAfterFinally = decodeStackTop((int) blockstackThumbprint);
                stackTop = unwindBlock(stack, stackTop, stackTopAfterFinally);
                blockstackTop = decodeBlockstackTop((int) blockstackThumbprint);
                int handlerBCI = decodeBCI((int) blockstackThumbprint);

                if (handlerBCI > 0) {
                    // handlerBCI cannot be 0, since +2 is always addeed to the jump target of the
                    // finally block
                    assert blockstackTop >= 0 && blockstackTop < MAXBLOCKS;
                    blockstack[blockstackTop] = encodeBlockTypeExcept() | encodeStackTop(stackTop);

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
                    oparg = Byte.toUnsignedInt(localBC[bci + 1]);
                    continue;
                } else {
                    // didn't find a finally block, we're done
                    throw e;
                }
            } catch (AbstractTruffleException | StackOverflowError e) {
                throw e;
            } catch (ControlFlowException | ThreadDeath e) {
                // do not handle ThreadDeath, result of TruffleContext.closeCancelled()
                throw e;
            }
        }
    }

    private int bytecodeCallFunction(VirtualFrame frame, Object[] stack, int stackTop, int bci, int oparg, Node[] localNodes) {
        Object func = stack[stackTop - oparg];
        switch (oparg) {
            case 1: {
                CallUnaryMethodNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL_UNARY_METHOD, NODE_CALL_UNARY_METHOD, bci);
                Object result = callNode.executeObject(frame, func, stack[stackTop]);
                stack[stackTop--] = null;
                stack[stackTop] = result;
                break;
            }
            case 2: {
                CallBinaryMethodNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL_BINARY_METHOD, NODE_CALL_BINARY_METHOD, bci);
                Object arg1 = stack[stackTop];
                stack[stackTop--] = null;
                Object arg0 = stack[stackTop];
                stack[stackTop--] = null;
                stack[stackTop] = callNode.executeObject(frame, func, arg0, arg1);
                break;
            }
            case 3: {
                CallTernaryMethodNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL_TERNARY_METHOD, NODE_CALL_TERNARY_METHOD, bci);
                Object arg2 = stack[stackTop];
                stack[stackTop--] = null;
                Object arg1 = stack[stackTop];
                stack[stackTop--] = null;
                Object arg0 = stack[stackTop];
                stack[stackTop--] = null;
                stack[stackTop] = callNode.execute(frame, func, arg0, arg1, arg2);
                break;
            }
            default: {
                Object[] arguments = new Object[oparg];
                for (int j = oparg - 1; j >= 0; j--) {
                    arguments[j] = stack[stackTop];
                    stack[stackTop--] = null;
                }
                CallNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL, NODE_CALL, bci);
                Object result = callNode.execute(frame, func, arguments, PKeyword.EMPTY_KEYWORDS);
                stack[stackTop] = result;
                break;
            }
        }
        return stackTop;
    }

    private int bytecodeStoreName(VirtualFrame frame, PythonLanguage lang, Object globals, Object locals, Object[] stack, int stackTop, int bci, int oparg, String[] localNames, Node[] localNodes) {
        String varname = localNames[oparg];
        Object value = stack[stackTop];
        stack[stackTop--] = null;
        if (locals != null) {
            Node helper = localNodes[bci];
            if (helper instanceof HashingCollectionNodes.SetItemNode) {
                if (locals instanceof PDict && ((PDict) locals).getShape() == PythonBuiltinClassType.PDict.getInstanceShape(lang)) {
                    HashingCollectionNodes.SetItemNode setItemNode = (HashingCollectionNodes.SetItemNode) helper;
                    setItemNode.execute(frame, (PDict) locals, varname, value);
                    return stackTop;
                }
            }
            if (helper instanceof PyObjectSetItem) {
                ((PyObjectSetItem) helper).execute(frame, locals, varname, value);
                return stackTop;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            assert helper == null;
            if (locals instanceof PDict && ((PDict) locals).getShape() == PythonBuiltinClassType.PDict.getInstanceShape(lang)) {
                HashingCollectionNodes.SetItemNode newNode = insertChildNode(localNodes[bci], UNCACHED_SET_ITEM, NODE_SET_ITEM, bci);
                newNode.execute(frame, (PDict) locals, varname, value);
            } else {
                PyObjectSetItem newNode = insertChildNode(localNodes[bci], UNCACHED_OBJECT_SET_ITEM, NODE_OBJECT_SET_ITEM, bci);
                newNode.execute(frame, locals, varname, value);
            }
        } else {
            WriteGlobalNode writeGlobalNode = insertChildNode(localNodes[bci + 1], UNCACHED_WRITE_GLOBAL, NODE_WRITE_GLOBAL, bci + 1, varname);
            writeGlobalNode.write(frame, globals, varname, value);
        }
        return stackTop;
    }

    private int bytecodeRaiseVarargs(VirtualFrame frame, Object[] stack, int stackTop, int bci, int oparg, Node[] localNodes) {
        RaiseNode raiseNode = insertChildNode(localNodes[bci], NODE_RAISENODE, bci);
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
        return stackTop;
    }

    private int bytecodeImportName(VirtualFrame frame, PythonContext context, PythonModule builtins, Object globals, Object[] stack, int stackTop, int bci, int oparg, String[] localNames,
                    Node[] localNodes) {
        CastToJavaIntExactNode castNode = insertChildNode(localNodes[bci], UNCACHED_CAST_TO_JAVA_INT_EXACT, NODE_CAST_TO_JAVA_INT_EXACT, bci);
        String modname = localNames[oparg];
        Object fromlist = stack[stackTop];
        stack[stackTop--] = null;
        String[] fromlistArg;
        if (fromlist == PNone.NONE) {
            fromlistArg = PythonUtils.EMPTY_STRING_ARRAY;
        } else {
            // import statement won't be dynamically created, so the fromlist is
            // always
            // from a LOAD_CONST, which will either be a tuple of strings or None.
            assert fromlist instanceof PTuple;
            Object[] list = ((PTuple) fromlist).getSequenceStorage().getInternalArray();
            fromlistArg = (String[]) list;
        }
        int level = castNode.execute(stack[stackTop]);
        ImportName importNode = insertChildNode(localNodes[bci + 1], UNCACHED_IMPORT_NAME, NODE_IMPORT_NAME, bci + 1);
        Object result = importNode.execute(frame, context, builtins, modname, globals, fromlistArg, level);
        stack[stackTop] = result;
        return stackTop;
    }

    private void verifyInLoop(Object[] stack, Object[] fastlocals, Object[] celllocals, Object[] freelocals, int[] blockstack, int stackTop, int blockstackTop, int bci, final byte bc) {
        CompilerAsserts.partialEvaluationConstant(bc);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(blockstackTop);
        CompilerDirectives.ensureVirtualized(stack);
        CompilerDirectives.ensureVirtualized(fastlocals);
        CompilerDirectives.ensureVirtualized(celllocals);
        CompilerDirectives.ensureVirtualized(freelocals);
        CompilerDirectives.ensureVirtualized(blockstack);
    }

    private void verifyBeforeLoop(int target, int stackTop, int blockstackTop, int bci, int oparg, byte[] localBC) {
        CompilerAsserts.partialEvaluationConstant(localBC);
        CompilerAsserts.partialEvaluationConstant(blockstackTop);
        CompilerAsserts.partialEvaluationConstant(target);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(oparg);
    }

    private int bytecodeCallFunctionKw(VirtualFrame frame, Object[] stack, int stackTop, int bci, int oparg, Node[] localNodes) {
        CallNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL, NODE_CALL, bci);
        Object[] kwNamesArray = ((PTuple) stack[stackTop]).getSequenceStorage().getInternalArray();
        String[] kwNames = new String[kwNamesArray.length];
        CastToJavaStringNode castStr = insertChildNode(localNodes[bci + 1], UNCACHED_CAST_TO_JAVA_STRING, NODE_CAST_TO_JAVA_STRING, bci + 1);
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
        return stackTop;
    }

    private int bytecodeCallFunctionEx(VirtualFrame frame, Object[] stack, int stackTop, int bci, int oparg, Node[] localNodes) {
        CallNode callNode = insertChildNode(localNodes[bci], UNCACHED_CALL, NODE_CALL, bci);
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
        return stackTop;
    }

    private int bytecodeBinaryMultiply(VirtualFrame frame, Object[] stack, int lastStackTop, int bci) {
        int stackTop = lastStackTop;
        MulNode mulNode = insertChildNode(adoptedNodes[bci], NODE_BINARY_MUL, bci);
        Object right = stack[stackTop];
        stack[stackTop--] = null;
        stack[stackTop] = mulNode.executeObject(frame, stack[stackTop], right);
        return stackTop;
    }

    private int bytecodeMakeFunction(Object globals, Object[] stack, int lastStackTop, int bci, int oparg) {
        int stackTop = lastStackTop;
        String qualname = insertChildNode(adoptedNodes[bci], UNCACHED_CAST_TO_JAVA_STRING, NODE_CAST_TO_JAVA_STRING, bci).execute(stack[stackTop]);
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
            DynamicObjectLibrary.getUncached().put((DynamicObject) stack[stackTop], __ANNOTATIONS__, annotations);
        }
        return stackTop;
    }

    /**
     * Record the current {@code blockstack} via it's "thumbprint" as the handlers for the current
     * {@code bci}. This may insert a new blockstack range (in which case it starts out as [bci,
     * bci]). If we already recorded this exact blockstack range, we assume proper nesting in which
     * case we extend the range the block belongs to to include {@code bci}. The method ensures the
     * ranges remain sorted by known start index.
     *
     * This is done to control the amount of code before PE when handling exceptions... What does
     * searching for the handler really do? There are only two kinds of blocks - EXCEPT and FINALLY.
     * The except blocks use unwindExceptHandler, the first FINALLY block that's found calls
     * unwindBlock, inserts an EXCEPT block in its own position on the blockstack, and returns the
     * jump target. We already assume that the blockstacks are properly nested and thus can be
     * encoded PE safely as nested ranges. This we can just store the blockstack information more
     * concisely:
     *
     * 1) How deep to unwind EXCEPT blocks. Only the last except block can win as being the "new-old
     * currently handled exception" that is restored and there cannot be any FINALLY blocks
     * in-between. So we just need the lowest stack level to pop to for EXCEPT blocks ... that's 12
     * bits;
     *
     * 2) 16 bit - the handler BCI. It cannot be 0, we just don't generate that kind of code.
     *
     * 3) stackTop before FINALLY block was pushed ... that's another 12 bits.
     *
     * 4) which position in the blockstack is transformed into an EXCEPT block .. just 4 bits needed
     * for this due to MAXBLOCKS being 15
     *
     * So really, all we need is to store one long per bci range that raised and that tells use all
     * we need.
     */
    private long saveExceptionBlockstack(int bci, int stackTop, int[] blockstack, int blockstackTop) {
        CompilerAsserts.neverPartOfCompilation();
        int stackTopAfterExceptUnwinding = stackTop;
        int stackTopAfterFinally = stackTop;
        int handlerBCI = 0;
        int newExceptBlockIndex = -1;
        for (int i = blockstackTop; i >= 0; i--) {
            int block = blockstack[i];
            int stackTopBeforeBlock = decodeStackTop(block);
            if (isBlockTypeExcept(block)) {
                stackTopAfterExceptUnwinding = stackTopBeforeBlock;
            } else {
                assert isBlockTypeFinally(block);
                stackTopAfterFinally = stackTopBeforeBlock;
                handlerBCI = decodeBCI(block) + 2;
                newExceptBlockIndex = i;
                break;
            }
        }
        long currentThumbprint = encodeExceptBlockStackTop(stackTopAfterExceptUnwinding) |
                        encodeStackTop(stackTopAfterFinally) |
                        encodeBlockstackTop(newExceptBlockIndex) |
                        encodeBCI(handlerBCI);

        int knownIndex = -1;
        for (int i = 0; i < blockstackRanges.length; i += 3) {
            if (blockstackRanges[i + 2] == currentThumbprint) {
                knownIndex = i;
                break;
            }
        }

        if (knownIndex >= 0) {
            // we already know of this blockstack, so there's a block we need to extend
            if (bci < blockstackRanges[knownIndex]) {
                blockstackRanges[knownIndex] = bci;
                // potentially need to re-sort the ranges
                for (int j = 0; j < knownIndex; j += 3) {
                    if (bci < blockstackRanges[j]) {
                        // shift all ranges from j three places to the right, overwriting the range
                        // starting at knownIndex, then the re-insert range knownIndex where range
                        // j was
                        long savedStop = blockstackRanges[knownIndex + 1];
                        System.arraycopy(blockstackRanges, j, blockstackRanges, j + 3, knownIndex - j);
                        blockstackRanges[j] = bci;
                        blockstackRanges[j + 1] = savedStop;
                        blockstackRanges[j + 2] = knownIndex;
                    }
                }
            } else {
                assert bci > blockstackRanges[knownIndex + 1];
                blockstackRanges[knownIndex + 1] = bci;
            }
        } else {
            // we don't know this blockstack at all, insert a new range
            int insertionIndex = 0;
            for (int i = 0; i < blockstackRanges.length; i += 3) {
                assert bci != blockstackRanges[i] && bci != blockstackRanges[i + 1];
                if (bci < blockstackRanges[i]) {
                    insertionIndex = i;
                } else {
                    break;
                }
            }
            long[] newRanges = new long[blockstackRanges.length + 3];
            System.arraycopy(blockstackRanges, 0, newRanges, 0, insertionIndex);
            System.arraycopy(blockstackRanges, insertionIndex, newRanges, insertionIndex + 3, blockstackRanges.length - insertionIndex);
            blockstackRanges = newRanges;
            blockstackRanges[insertionIndex] = bci;
            blockstackRanges[insertionIndex + 1] = bci;
            blockstackRanges[insertionIndex + 2] = currentThumbprint;
        }

        return currentThumbprint;
    }

    // Encoding for blockstack thumbprints is basically like for the target (since it is a target),
    // but in addition there's the stackTop for any except blocks on top.
    private static long encodeExceptBlockStackTop(int top) {
        return (long) encodeStackTop(top) << 32;
    }

    private static int decodeExceptBlockStackTop(long thumbprint) {
        return decodeStackTop((int) (thumbprint >>> 32));
    }

    /**
     * @see #saveExceptionBlockstack
     */
    @ExplodeLoop
    private long findHandler(int stackTop, Object[] stack, int[] blockstack, int blockstackTop, int bci) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(blockstackTop);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerDirectives.ensureVirtualized(stack);
        CompilerDirectives.ensureVirtualized(blockstack);

        CompilerAsserts.partialEvaluationConstant(blockstackRanges.length);

        long blockstackThumbprint = -1;
        for (int i = 0; i < blockstackRanges.length; i += 3) {
            CompilerAsserts.partialEvaluationConstant(blockstackRanges[i]);
            CompilerAsserts.partialEvaluationConstant(blockstackRanges[i + 1]);
            CompilerAsserts.partialEvaluationConstant(blockstackRanges[i + 2]);
            if (bci < blockstackRanges[i]) {
                // all following blockstack ranges are after this bci
                break;
            } else if (bci > blockstackRanges[i + 1]) {
                // bci is after this blockstack entry starts, but also after it ends. Assuming
                // non-overlapping and sorted by begin bci, this means that there cannot be an
                // entry after this that would match, since that would have to have a higher start
                // bci and also a higher end bci, which would make it overlap with the current
                // block
                break;
            } else {
                blockstackThumbprint = blockstackRanges[i + 2];
            }
        }

        CompilerAsserts.partialEvaluationConstant(blockstackThumbprint);
        if (blockstackThumbprint == -1) {
            // -1 cannot happen, since we're never setting all the bits in a real thumbprint
            CompilerDirectives.transferToInterpreterAndInvalidate();
            blockstackThumbprint = saveExceptionBlockstack(bci, stackTop, blockstack, blockstackTop);
        }
        return blockstackThumbprint;
    }

    @ExplodeLoop
    private static int unwindExceptHandler(Object[] stack, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        CompilerDirectives.ensureVirtualized(stack);
        for (int i = stackTop; i > stackTopBeforeBlock; i--) {
            stack[i] = null;
        }
        return stackTopBeforeBlock;
    }

    @ExplodeLoop
    private static int unwindBlock(Object[] stack, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        CompilerDirectives.ensureVirtualized(stack);
        for (int i = stackTop; i > stackTopBeforeBlock; i--) {
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
            HashingStorageLibrary lib = insertChildNode(adoptedNodes[nodeIndex], UNCACHED_HASHING_STORAGE_LIBRARY, NODE_HASHING_STORAGE_LIBRARY, nodeIndex);
            kwdefaults = new PKeyword[lib.length(store)];
            int j = 0;
            for (HashingStorage.DictEntry entry : lib.entries(store)) {
                kwdefaults[j++] = new PKeyword((String) entry.key, entry.value);
            }
        }
        return kwdefaults;
    }

    public void syncFastToLocals(Object localsObject, Frame frameToSync, PyObjectSetItem setItem, PyObjectDelItem delItem) {
        Object[] fastlocals = (Object[]) FrameUtil.getObjectSafe(frameToSync, FAST_SLOT);
        assert fastlocals.length == varnames.length;
        for (int i = 0; i < varnames.length; i++) {
            Object v = fastlocals[i];
            String n = varnames[i];
            if (v == null) {
                delItem.execute(frameToSync, localsObject, n);
            } else {
                setItem.execute(frameToSync, localsObject, n, v);
            }
        }
    }

    // our own quickened bytecodes, counting down towards the generic codes
    private static final byte STORE_FAST_BOOLEAN = (byte) 255;
    private static final byte STORE_FAST_INT = (byte) 254;
    private static final byte STORE_FAST_LONG = (byte) 253;
    private static final byte STORE_FAST_DOUBLE = (byte) 252;
    private static final byte STORE_FAST_GENERIC = (byte) 251;
}
