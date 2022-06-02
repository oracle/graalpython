/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.math.BigInteger;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.GeneratorControlData;
import com.oracle.graal.python.builtins.objects.generator.ThrowData;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.compiler.BinaryOpsConstants;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.FormatOptions;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.compiler.OpCodesConstants;
import com.oracle.graal.python.compiler.UnaryOpsConstants;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectGetMethodNodeGen;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOp;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.InvertNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.NegNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.PosNode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.statement.ExceptNode.ExceptMatchNode;
import com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Root node with main bytecode interpreter loop.
 */
public final class PBytecodeRootNode extends PRootNode implements BytecodeOSRNode {

    private static final NodeSupplier<RaiseNode> NODE_RAISENODE = () -> RaiseNode.create(null, null);
    private static final NodeSupplier<DeleteItemNode> NODE_DELETE_ITEM = DeleteItemNode::create;
    private static final NodeSupplier<PyObjectDelItem> NODE_OBJECT_DEL_ITEM = PyObjectDelItem::create;
    private static final PyObjectDelItem UNCACHED_OBJECT_DEL_ITEM = PyObjectDelItem.getUncached();

    private static final NodeSupplier<SetItemNode> NODE_SET_ITEM = HashingCollectionNodes.SetItemNode::create;
    private static final SetItemNode UNCACHED_SET_ITEM = HashingCollectionNodes.SetItemNode.getUncached();
    private static final NodeSupplier<CastToJavaIntExactNode> NODE_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode::create;
    private static final CastToJavaIntExactNode UNCACHED_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode.getUncached();
    private static final NodeSupplier<ImportNode> NODE_IMPORT = ImportNode::new;
    private static final NodeSupplier<PyObjectGetAttr> NODE_OBJECT_GET_ATTR = PyObjectGetAttr::create;
    private static final PyObjectGetAttr UNCACHED_OBJECT_GET_ATTR = PyObjectGetAttr.getUncached();
    private static final NodeSupplier<PRaiseNode> NODE_RAISE = PRaiseNode::create;
    private static final PRaiseNode UNCACHED_RAISE = PRaiseNode.getUncached();
    private static final NodeSupplier<CallNode> NODE_CALL = CallNode::create;
    private static final CallNode UNCACHED_CALL = CallNode.getUncached();
    private static final NodeSupplier<CallQuaternaryMethodNode> NODE_CALL_QUATERNARY_METHOD = CallQuaternaryMethodNode::create;
    private static final CallQuaternaryMethodNode UNCACHED_CALL_QUATERNARY_METHOD = CallQuaternaryMethodNode.getUncached();
    private static final NodeSupplier<CallTernaryMethodNode> NODE_CALL_TERNARY_METHOD = CallTernaryMethodNode::create;
    private static final CallTernaryMethodNode UNCACHED_CALL_TERNARY_METHOD = CallTernaryMethodNode.getUncached();
    private static final NodeSupplier<CallBinaryMethodNode> NODE_CALL_BINARY_METHOD = CallBinaryMethodNode::create;
    private static final CallBinaryMethodNode UNCACHED_CALL_BINARY_METHOD = CallBinaryMethodNode.getUncached();
    private static final NodeSupplier<CallUnaryMethodNode> NODE_CALL_UNARY_METHOD = CallUnaryMethodNode::create;
    private static final CallUnaryMethodNode UNCACHED_CALL_UNARY_METHOD = CallUnaryMethodNode.getUncached();
    private static final NodeSupplier<PyObjectGetMethod> NODE_OBJECT_GET_METHOD = PyObjectGetMethodNodeGen::create;
    private static final PyObjectGetMethod UNCACHED_OBJECT_GET_METHOD = PyObjectGetMethodNodeGen.getUncached();
    private static final NodeSupplier<PyIterNextNode> NODE_GET_NEXT = PyIterNextNode::create;
    private static final PyIterNextNode UNCACHED_GET_NEXT = PyIterNextNode.getUncached();
    private static final NodeSupplier<PyObjectGetIter> NODE_OBJECT_GET_ITER = PyObjectGetIter::create;
    private static final PyObjectGetIter UNCACHED_OBJECT_GET_ITER = PyObjectGetIter.getUncached();
    private static final NodeSupplier<PyObjectSetAttr> NODE_OBJECT_SET_ATTR = PyObjectSetAttr::create;
    private static final PyObjectSetAttr UNCACHED_OBJECT_SET_ATTR = PyObjectSetAttr.getUncached();
    private static final NodeSupplier<ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS = () -> ReadGlobalOrBuiltinNode.create(T___BUILD_CLASS__);
    private static final NodeFunction<TruffleString, ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode::create;
    private static final NodeFunction<TruffleString, ReadNameNode> NODE_READ_NAME = ReadNameNode::create;
    private static final NodeFunction<TruffleString, WriteNameNode> NODE_WRITE_NAME = WriteNameNode::create;
    private static final ReadGlobalOrBuiltinNode UNCACHED_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode.getUncached();
    private static final NodeSupplier<PyObjectSetItem> NODE_OBJECT_SET_ITEM = PyObjectSetItem::create;
    private static final PyObjectSetItem UNCACHED_OBJECT_SET_ITEM = PyObjectSetItem.getUncached();
    private static final NodeSupplier<PyObjectIsTrueNode> NODE_OBJECT_IS_TRUE = PyObjectIsTrueNode::create;
    private static final PyObjectIsTrueNode UNCACHED_OBJECT_IS_TRUE = PyObjectIsTrueNode.getUncached();
    private static final NodeSupplier<GetItemNode> NODE_GET_ITEM = GetItemNode::create;
    private static final ExceptMatchNode UNCACHED_EXCEPT_MATCH = ExceptMatchNode.getUncached();
    private static final NodeSupplier<ExceptMatchNode> NODE_EXCEPT_MATCH = ExceptMatchNode::create;
    private static final SetupWithNode UNCACHED_SETUP_WITH_NODE = SetupWithNode.getUncached();
    private static final NodeSupplier<SetupWithNode> NODE_SETUP_WITH = SetupWithNode::create;
    private static final ExitWithNode UNCACHED_EXIT_WITH_NODE = ExitWithNode.getUncached();
    private static final NodeSupplier<ExitWithNode> NODE_EXIT_WITH = ExitWithNode::create;
    private static final ImportFromNode UNCACHED_IMPORT_FROM = ImportFromNode.getUncached();
    private static final NodeSupplier<ImportFromNode> NODE_IMPORT_FROM = ImportFromNode::create;
    private static final ExecutePositionalStarargsNode UNCACHED_EXECUTE_STARARGS = ExecutePositionalStarargsNode.getUncached();
    private static final NodeSupplier<ExecutePositionalStarargsNode> NODE_EXECUTE_STARARGS = ExecutePositionalStarargsNode::create;
    private static final ExpandKeywordStarargsNode UNCACHED_EXPAND_KEYWORD_STARARGS = ExpandKeywordStarargsNode.getUncached();
    private static final NodeSupplier<ExpandKeywordStarargsNode> NODE_EXPAND_KEYWORD_STARARGS = ExpandKeywordStarargsNode::create;
    private static final KeywordsNode UNCACHED_KEYWORDS = KeywordsNode.getUncached();
    private static final NodeSupplier<KeywordsNode> NODE_KEYWORDS = KeywordsNode::create;
    private static final SliceNodes.CreateSliceNode UNCACHED_CREATE_SLICE = SliceNodes.CreateSliceNode.getUncached();
    private static final NodeSupplier<SliceNodes.CreateSliceNode> NODE_CREATE_SLICE = SliceNodes.CreateSliceNode::create;
    private static final ListNodes.ConstructListNode UNCACHED_CONSTRUCT_LIST = ListNodes.ConstructListNode.getUncached();
    private static final NodeSupplier<ListNodes.ConstructListNode> NODE_CONSTRUCT_LIST = ListNodes.ConstructListNode::create;
    private static final TupleNodes.ConstructTupleNode UNCACHED_CONSTRUCT_TUPLE = TupleNodes.ConstructTupleNode.getUncached();
    private static final NodeSupplier<TupleNodes.ConstructTupleNode> NODE_CONSTRUCT_TUPLE = TupleNodes.ConstructTupleNode::create;
    private static final SetNodes.ConstructSetNode UNCACHED_CONSTRUCT_SET = SetNodes.ConstructSetNode.getUncached();
    private static final NodeSupplier<SetNodes.ConstructSetNode> NODE_CONSTRUCT_SET = SetNodes.ConstructSetNode::create;
    private static final NodeSupplier<HashingStorage.InitNode> NODE_HASHING_STORAGE_INIT = HashingStorage.InitNode::create;
    private static final NodeSupplier<ListBuiltins.ListExtendNode> NODE_LIST_EXTEND = ListBuiltins.ListExtendNode::create;
    private static final SetBuiltins.UpdateSingleNode UNCACHED_SET_UPDATE = SetBuiltins.UpdateSingleNode.getUncached();
    private static final NodeSupplier<DictNodes.UpdateNode> NODE_DICT_UPDATE = DictNodes.UpdateNode::create;
    private static final NodeSupplier<SetBuiltins.UpdateSingleNode> NODE_SET_UPDATE = SetBuiltins.UpdateSingleNode::create;
    private static final ListNodes.AppendNode UNCACHED_LIST_APPEND = ListNodes.AppendNode.getUncached();
    private static final NodeSupplier<ListNodes.AppendNode> NODE_LIST_APPEND = ListNodes.AppendNode::create;
    private static final SetNodes.AddNode UNCACHED_SET_ADD = SetNodes.AddNode.getUncached();
    private static final NodeSupplier<SetNodes.AddNode> NODE_SET_ADD = SetNodes.AddNode::create;
    private static final KwargsMergeNode UNCACHED_KWARGS_MERGE = KwargsMergeNode.getUncached();
    private static final NodeSupplier<KwargsMergeNode> NODE_KWARGS_MERGE = KwargsMergeNode::create;
    private static final UnpackSequenceNode UNCACHED_UNPACK_SEQUENCE = UnpackSequenceNode.getUncached();
    private static final NodeSupplier<UnpackSequenceNode> NODE_UNPACK_SEQUENCE = UnpackSequenceNode::create;
    private static final UnpackExNode UNCACHED_UNPACK_EX = UnpackExNode.getUncached();
    private static final NodeSupplier<UnpackExNode> NODE_UNPACK_EX = UnpackExNode::create;
    private static final PyObjectStrAsObjectNode UNCACHED_STR = PyObjectStrAsObjectNode.getUncached();
    private static final NodeSupplier<PyObjectStrAsObjectNode> NODE_STR = PyObjectStrAsObjectNode::create;
    private static final PyObjectReprAsObjectNode UNCACHED_REPR = PyObjectReprAsObjectNode.getUncached();
    private static final NodeSupplier<PyObjectReprAsObjectNode> NODE_REPR = PyObjectReprAsObjectNode::create;
    private static final PyObjectAsciiNode UNCACHED_ASCII = PyObjectAsciiNode.getUncached();
    private static final NodeSupplier<PyObjectAsciiNode> NODE_ASCII = PyObjectAsciiNode::create;
    private static final NodeSupplier<BuiltinFunctions.FormatNode> NODE_FORMAT = BuiltinFunctions.FormatNode::create;
    private static final NodeSupplier<SendNode> NODE_SEND = SendNode::create;
    private static final NodeSupplier<ThrowNode> NODE_THROW = ThrowNode::create;
    private static final WriteGlobalNode UNCACHED_WRITE_GLOBAL = WriteGlobalNode.getUncached();
    private static final NodeFunction<TruffleString, WriteGlobalNode> NODE_WRITE_GLOBAL = WriteGlobalNode::create;
    private static final NodeFunction<TruffleString, DeleteGlobalNode> NODE_DELETE_GLOBAL = DeleteGlobalNode::create;
    private static final PrintExprNode UNCACHED_PRINT_EXPR = PrintExprNode.getUncached();
    private static final NodeSupplier<PrintExprNode> NODE_PRINT_EXPR = PrintExprNode::create;
    private static final NodeSupplier<GetNameFromLocalsNode> NODE_GET_NAME_FROM_LOCALS = GetNameFromLocalsNode::create;

    private static final IntNodeFunction<UnaryOpNode> UNARY_OP_FACTORY = (int op) -> {
        switch (op) {
            case UnaryOpsConstants.NOT:
                return CoerceToBooleanNode.createIfFalseNode();
            case UnaryOpsConstants.POSITIVE:
                return PosNode.create();
            case UnaryOpsConstants.NEGATIVE:
                return NegNode.create();
            case UnaryOpsConstants.INVERT:
                return InvertNode.create();
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    };

    private static final IntNodeFunction<Node> BINARY_OP_FACTORY = (int op) -> {
        switch (op) {
            case BinaryOpsConstants.ADD:
                return BinaryArithmetic.Add.create();
            case BinaryOpsConstants.SUB:
                return BinaryArithmetic.Sub.create();
            case BinaryOpsConstants.MUL:
                return BinaryArithmetic.Mul.create();
            case BinaryOpsConstants.TRUEDIV:
                return BinaryArithmetic.TrueDiv.create();
            case BinaryOpsConstants.FLOORDIV:
                return BinaryArithmetic.FloorDiv.create();
            case BinaryOpsConstants.MOD:
                return BinaryArithmetic.Mod.create();
            case BinaryOpsConstants.LSHIFT:
                return BinaryArithmetic.LShift.create();
            case BinaryOpsConstants.RSHIFT:
                return BinaryArithmetic.RShift.create();
            case BinaryOpsConstants.AND:
                return BinaryArithmetic.And.create();
            case BinaryOpsConstants.OR:
                return BinaryArithmetic.Or.create();
            case BinaryOpsConstants.XOR:
                return BinaryArithmetic.Xor.create();
            case BinaryOpsConstants.POW:
                return BinaryArithmetic.Pow.create();
            case BinaryOpsConstants.MATMUL:
                return BinaryArithmetic.MatMul.create();
            case BinaryOpsConstants.INPLACE_ADD:
                return InplaceArithmetic.IAdd.create();
            case BinaryOpsConstants.INPLACE_SUB:
                return InplaceArithmetic.ISub.create();
            case BinaryOpsConstants.INPLACE_MUL:
                return InplaceArithmetic.IMul.create();
            case BinaryOpsConstants.INPLACE_TRUEDIV:
                return InplaceArithmetic.ITrueDiv.create();
            case BinaryOpsConstants.INPLACE_FLOORDIV:
                return InplaceArithmetic.IFloorDiv.create();
            case BinaryOpsConstants.INPLACE_MOD:
                return InplaceArithmetic.IMod.create();
            case BinaryOpsConstants.INPLACE_LSHIFT:
                return InplaceArithmetic.ILShift.create();
            case BinaryOpsConstants.INPLACE_RSHIFT:
                return InplaceArithmetic.IRShift.create();
            case BinaryOpsConstants.INPLACE_AND:
                return InplaceArithmetic.IAnd.create();
            case BinaryOpsConstants.INPLACE_OR:
                return InplaceArithmetic.IOr.create();
            case BinaryOpsConstants.INPLACE_XOR:
                return InplaceArithmetic.IXor.create();
            case BinaryOpsConstants.INPLACE_POW:
                return InplaceArithmetic.IPow.create();
            case BinaryOpsConstants.INPLACE_MATMUL:
                return InplaceArithmetic.IMatMul.create();
            case BinaryOpsConstants.EQ:
                return BinaryComparisonNode.EqNode.create();
            case BinaryOpsConstants.NE:
                return BinaryComparisonNode.NeNode.create();
            case BinaryOpsConstants.LT:
                return BinaryComparisonNode.LtNode.create();
            case BinaryOpsConstants.LE:
                return BinaryComparisonNode.LeNode.create();
            case BinaryOpsConstants.GT:
                return BinaryComparisonNode.GtNode.create();
            case BinaryOpsConstants.GE:
                return BinaryComparisonNode.GeNode.create();
            case BinaryOpsConstants.IS:
                return IsNode.create();
            case BinaryOpsConstants.IN:
                return ContainsNode.create();
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    };

    /*
     * Create fake GeneratorControlData just to maintain the same generator frame layout as AST
     * interpreter. TODO remove
     */
    public static final GeneratorControlData GENERATOR_CONTROL_DATA = new GeneratorControlData(new GeneratorInfo(new GeneratorInfo.Mutable()));

    private final Signature signature;
    private final TruffleString name;
    private boolean pythonInternal;

    final int celloffset;
    final int freeoffset;
    final int stackoffset;
    final int bcioffset;
    final int selfIndex;
    final int classcellIndex;

    private final CodeUnit co;
    private final Source source;
    private SourceSection sourceSection;

    @CompilationFinal(dimensions = 1) final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final long[] longConsts;
    @CompilationFinal(dimensions = 1) private final TruffleString[] names;
    @CompilationFinal(dimensions = 1) private final TruffleString[] varnames;
    @CompilationFinal(dimensions = 1) private final TruffleString[] freevars;
    @CompilationFinal(dimensions = 1) private final TruffleString[] cellvars;
    @CompilationFinal(dimensions = 1) protected final Assumption[] cellEffectivelyFinalAssumptions;

    @CompilationFinal(dimensions = 1) private final short[] exceptionHandlerRanges;
    @CompilationFinal(dimensions = 1) private final int[] extraArgs;

    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @CompilationFinal private LoopConditionProfile exceptionChainProfile1 = LoopConditionProfile.createCountingProfile();
    @CompilationFinal private LoopConditionProfile exceptionChainProfile2 = LoopConditionProfile.createCountingProfile();
    @CompilationFinal private Object osrMetadata;

    private static final Node MARKER_NODE = new Node() {
        @Override
        public boolean isAdoptable() {
            return false;
        }
    };

    private static FrameDescriptor makeFrameDescriptor(CodeUnit co) {
        int capacity = co.varnames.length + co.cellvars.length + co.freevars.length + co.stacksize + 1;
        FrameDescriptor.Builder newBuilder = FrameDescriptor.newBuilder(capacity);
        newBuilder.info(new FrameInfo());
        // locals
        for (int i = 0; i < co.varnames.length; i++) {
            newBuilder.addSlot(FrameSlotKind.Illegal, co.varnames[i], null);
        }
        // cells
        for (int i = 0; i < co.cellvars.length; i++) {
            newBuilder.addSlot(FrameSlotKind.Illegal, co.cellvars[i], null);
        }
        // freevars
        for (int i = 0; i < co.freevars.length; i++) {
            newBuilder.addSlot(FrameSlotKind.Illegal, co.freevars[i], null);
        }
        // stack
        newBuilder.addSlots(co.stacksize, FrameSlotKind.Illegal);
        // BCI filled when unwinding the stack or when pausing generators
        newBuilder.addSlot(FrameSlotKind.Static, null, null);
        if (co.isGeneratorOrCoroutine()) {
            // stackTop saved when pausing a generator
            newBuilder.addSlot(FrameSlotKind.Int, null, null);
            // return value of a generator
            newBuilder.addSlot(FrameSlotKind.Illegal, null, null);
        }
        return newBuilder.build();
    }

    private static Signature makeSignature(CodeUnit co) {
        int posArgCount = co.argCount + co.positionalOnlyArgCount;
        TruffleString[] parameterNames = Arrays.copyOf(co.varnames, posArgCount);
        TruffleString[] kwOnlyNames = Arrays.copyOfRange(co.varnames, posArgCount, posArgCount + co.kwOnlyArgCount);
        int varArgsIndex = co.takesVarArgs() ? posArgCount : -1;
        return new Signature(co.positionalOnlyArgCount,
                        co.takesVarKeywordArgs(),
                        varArgsIndex,
                        co.positionalOnlyArgCount > 0,
                        parameterNames,
                        kwOnlyNames);
    }

    @TruffleBoundary
    public PBytecodeRootNode(TruffleLanguage<?> language, CodeUnit co, Source source) {
        this(language, makeFrameDescriptor(co), makeSignature(co), co, source);
    }

    @TruffleBoundary
    public PBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor fd, Signature sign, CodeUnit co, Source source) {
        super(language, fd);
        ((FrameInfo) fd.getInfo()).rootNode = this;
        this.celloffset = co.varnames.length;
        this.freeoffset = celloffset + co.cellvars.length;
        this.stackoffset = freeoffset + co.freevars.length;
        this.bcioffset = stackoffset + co.stacksize;
        this.source = source;
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
        this.name = co.name;
        this.exceptionHandlerRanges = co.exceptionHandlerRanges;
        this.co = co;
        assert co.stacksize < Math.pow(2, 12) : "stacksize cannot be larger than 12-bit range";
        assert bytecode.length < Math.pow(2, 16) : "bytecode cannot be longer than 16-bit range";
        cellEffectivelyFinalAssumptions = new Assumption[cellvars.length];
        for (int i = 0; i < cellvars.length; i++) {
            cellEffectivelyFinalAssumptions[i] = Truffle.getRuntime().createAssumption("cell is effectively final");
        }
        int classcellIndex = -1;
        for (int i = 0; i < this.freevars.length; i++) {
            if (T___CLASS__.equalsUncached(this.freevars[i], TS_ENCODING)) {
                classcellIndex = this.freeoffset + i;
                break;
            }
        }
        this.classcellIndex = classcellIndex;
        int selfIndex = -1;
        if (!signature.takesNoArguments()) {
            selfIndex = 0;
            if (co.cell2arg != null) {
                for (int i = 0; i < co.cell2arg.length; i++) {
                    if (co.cell2arg[i] == 0) {
                        selfIndex = celloffset + i;
                        break;
                    }
                }
            }
        }
        this.selfIndex = selfIndex;
    }

    @Override
    public String getName() {
        return name.toJavaStringUncached();
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
        return pythonInternal;
    }

    public void setPythonInternal(boolean pythonInternal) {
        this.pythonInternal = pythonInternal;
    }

    public CodeUnit getCodeUnit() {
        return co;
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
    private static interface IntNodeFunction<T extends Node> {
        T apply(int argument);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, NodeFunction<A, T> nodeSupplier, A argument) {
        Node node = nodes[nodeIndex];
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNode(nodes, nodeIndex, nodeSupplier, argument);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T doInsertChildNode(Node[] nodes, int nodeIndex, NodeFunction<A, T> nodeSupplier, A argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        T newNode = nodeSupplier.apply(argument);
        nodes[nodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, T uncached, NodeFunction<A, T> nodeSupplier, A argument) {
        Node node = nodes[nodeIndex];
        if (node != null && node != MARKER_NODE) {
            return (T) node;
        }
        return doInsertChildNode(nodes, nodeIndex, node, uncached, nodeSupplier, argument);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T doInsertChildNode(Node[] nodes, int nodeIndex, Node node, T uncached, NodeFunction<A, T> nodeSupplier, A argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (node == null) { // first execution uncached
            nodes[nodeIndex] = MARKER_NODE;
            return uncached;
        } else {
            assert node == MARKER_NODE; // second execution caches
            T newNode = nodeSupplier.apply(argument);
            nodes[nodeIndex] = insert(newNode);
            return newNode;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNodeInt(Node[] nodes, int nodeIndex, IntNodeFunction<T> nodeSupplier, int argument) {
        Node node = nodes[nodeIndex];
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNodeInt(nodes, nodeIndex, nodeSupplier, argument);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNodeInt(Node[] nodes, int nodeIndex, IntNodeFunction<T> nodeSupplier, int argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        T newNode = nodeSupplier.apply(argument);
        nodes[nodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, NodeSupplier<T> nodeSupplier) {
        Node node = nodes[nodeIndex];
        if (node != null) {
            return (T) node;
        }
        return doInsertChildNode(nodes, nodeIndex, nodeSupplier);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node[] nodes, int nodeIndex, NodeSupplier<T> nodeSupplier) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        T newNode = nodeSupplier.get();
        nodes[nodeIndex] = insert(newNode);
        return newNode;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, T uncached, NodeSupplier<T> nodeSupplier) {
        Node node = nodes[nodeIndex];
        if (node != null && node != MARKER_NODE) {
            return (T) node;
        }
        return doInsertChildNode(nodes, nodeIndex, node, uncached, nodeSupplier);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node[] nodes, int bytecodeIndex, Node node, T uncached, NodeSupplier<T> nodeSupplier) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (node == null) { // first execution uncached
            nodes[bytecodeIndex] = MARKER_NODE;
            return uncached;
        } else { // second execution caches
            assert node == MARKER_NODE;
            T newNode = nodeSupplier.get();
            nodes[bytecodeIndex] = insert(newNode);
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

    private static int decodeBCI(int target) {
        return (target >>> 16) & 0xffff; // unsigned
    }

    private static int decodeStackTop(int target) {
        return (target & 0xffff) - 1;
    }

    private static int encodeBCI(int bci) {
        return bci << 16;
    }

    private static int encodeStackTop(int stackTop) {
        return stackTop + 1;
    }

    @ExplodeLoop
    private void copyArgs(Object[] args, Frame localFrame) {
        int argCount = co.argCount + co.positionalOnlyArgCount + co.kwOnlyArgCount;
        for (int i = 0; i < argCount; i++) {
            localFrame.setObject(i, args[i + PArguments.USER_ARGUMENTS_OFFSET]);
        }
    }

    public void createGeneratorFrame(Object[] arguments) {
        Object[] generatorFrameArguments = PArguments.create();
        MaterializedFrame generatorFrame = Truffle.getRuntime().createMaterializedFrame(generatorFrameArguments, getFrameDescriptor());
        PArguments.setGeneratorFrame(arguments, generatorFrame);
        PArguments.setCurrentFrameInfo(generatorFrameArguments, new PFrame.Reference(null));
        // The invoking node will set these two to the correct value only when the callee requests
        // it, otherwise they stay at the initial value, which we must set to null here
        PArguments.setException(arguments, null);
        PArguments.setCallerFrameInfo(arguments, null);
        PArguments.setControlData(arguments, GENERATOR_CONTROL_DATA);
        PArguments.setGeneratorFrameLocals(generatorFrameArguments, factory.createDictLocals(generatorFrame));
        copyArgsAndCells(generatorFrame, arguments);
    }

    private void copyArgsAndCells(Frame localFrame, Object[] arguments) {
        copyArgs(arguments, localFrame);
        int varIdx = co.argCount + co.positionalOnlyArgCount + co.kwOnlyArgCount;
        if (co.takesVarArgs()) {
            localFrame.setObject(varIdx++, factory.createTuple(PArguments.getVariableArguments(arguments)));
        }
        if (co.takesVarKeywordArgs()) {
            localFrame.setObject(varIdx, factory.createDict(PArguments.getKeywordArguments(arguments)));
        }
        initCellVars(localFrame);
        initFreeVars(localFrame, arguments);
    }

    int getInitialStackTop() {
        return stackoffset - 1;
    }

    @Override
    public Object execute(VirtualFrame virtualFrame) {
        calleeContext.enter(virtualFrame);
        try {
            if (!co.isGeneratorOrCoroutine()) {
                copyArgsAndCells(virtualFrame, virtualFrame.getArguments());
            }

            return executeFromBci(virtualFrame, virtualFrame, virtualFrame, this, 0, getInitialStackTop(), Integer.MAX_VALUE);
        } finally {
            calleeContext.exit(virtualFrame, this);
        }
    }

    @Override
    public Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
        Object[] arguments = parentFrame.getArguments();
        PArguments.setOSRFrame(arguments, parentFrame);
        return arguments;
    }

    @Override
    public Frame restoreParentFrameFromArguments(Object[] arguments) {
        return PArguments.getOSRFrame(arguments);
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterStateObject) {
        OSRInterpreterState interpreterState = (OSRInterpreterState) interpreterStateObject;
        return executeFromBci(osrFrame, osrFrame, osrFrame, this, target, interpreterState.stackTop, interpreterState.loopEndBci);
    }

    private static final class OSRContinuation {
        public final int bci;
        public final int stackTop;

        private OSRContinuation(int bci, int stackTop) {
            this.bci = bci;
            this.stackTop = stackTop;
        }
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @SuppressWarnings("fallthrough")
    Object executeFromBci(VirtualFrame virtualFrame, Frame localFrame, Frame stackFrame, BytecodeOSRNode osrNode, int initialBci, int initialStackTop, int loopEndBci) {
        Object globals = PArguments.getGlobals(virtualFrame);
        Object locals = PArguments.getSpecialArgument(virtualFrame);

        /*
         * We use an array as a workaround for not being able to specify which local variables are
         * loop constants (GR-35338).
         */
        int[] loopCount = new int[]{0};
        int stackTop = initialStackTop;
        int bci = initialBci;

        boolean isGeneratorOrCoroutine = co.isGeneratorOrCoroutine();
        byte[] localBC = bytecode;
        int[] localArgs = extraArgs;
        Object[] localConsts = consts;
        long[] localLongConsts = longConsts;
        TruffleString[] localNames = names;
        Node[] localNodes = adoptedNodes;
        final int bciSlot = bcioffset;

        virtualFrame.setIntStatic(bciSlot, initialBci);

        /*
         * This separate tracking of local exception is necessary to make exception state saving
         * work in generators. On one hand we need to retain the exception that was caught in the
         * generator, on the other hand we don't want to retain the exception state that was passed
         * from the outer frame because that changes with every resume.
         */
        PException localException = null;

        // We initialize this lazily when pushing exception state
        boolean fetchedException = false;
        PException outerException = null;

        CompilerAsserts.partialEvaluationConstant(localBC);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerAsserts.partialEvaluationConstant(stackTop);

        int oparg = 0;
        while (true) {
            final byte bc = localBC[bci];
            final int beginBci = bci;
            virtualFrame.setIntStatic(bciSlot, bci);

            CompilerAsserts.partialEvaluationConstant(bc);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(stackTop);

            if (CompilerDirectives.inCompiledCode() && bci > loopEndBci) {
                /*
                 * This means we're in OSR and we just jumped out of the OSR compiled loop. We want
                 * to return to the caller to continue in interpreter again otherwise we would most
                 * likely deopt on the next instruction. The caller handles the special return value
                 * in JUMP_BACKWARD. In generators, we need to additionally copy the stack items
                 * back to the generator frame.
                 */
                if (localFrame != stackFrame) {
                    copyStackSlotsToGeneratorFrame(stackFrame, localFrame, stackTop);
                    // Clear slots that were popped (if any)
                    clearFrameSlots(localFrame, stackTop + 1, initialStackTop);
                }
                return new OSRContinuation(bci, stackTop);
            }

            try {
                switch (bc) {
                    case OpCodesConstants.LOAD_NONE:
                        stackFrame.setObject(++stackTop, PNone.NONE);
                        break;
                    case OpCodesConstants.LOAD_ELLIPSIS:
                        stackFrame.setObject(++stackTop, PEllipsis.INSTANCE);
                        break;
                    case OpCodesConstants.LOAD_TRUE:
                        stackFrame.setObject(++stackTop, true);
                        break;
                    case OpCodesConstants.LOAD_FALSE:
                        stackFrame.setObject(++stackTop, false);
                        break;
                    case OpCodesConstants.LOAD_BYTE:
                        stackFrame.setObject(++stackTop, (int) localBC[++bci]); // signed!
                        break;
                    case OpCodesConstants.LOAD_LONG: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackFrame.setObject(++stackTop, localLongConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_DOUBLE: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackFrame.setObject(++stackTop, Double.longBitsToDouble(localLongConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_BIGINT: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackFrame.setObject(++stackTop, factory.createInt((BigInteger) localConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_STRING:
                    case OpCodesConstants.LOAD_CONST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackFrame.setObject(++stackTop, localConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_BYTES: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackFrame.setObject(++stackTop, factory.createBytes((byte[]) localConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_COMPLEX: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        double[] num = (double[]) localConsts[oparg];
                        stackFrame.setObject(++stackTop, factory.createComplex(num[0], num[1]));
                        break;
                    }
                    case OpCodesConstants.MAKE_KEYWORD: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        TruffleString key = (TruffleString) localConsts[oparg];
                        Object value = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop, new PKeyword(key, value));
                        break;
                    }
                    case OpCodesConstants.BUILD_SLICE: {
                        int count = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeBuildSlice(stackFrame, stackTop, beginBci, count, localNodes);
                        break;
                    }
                    case OpCodesConstants.FORMAT_VALUE: {
                        int options = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeFormatValue(virtualFrame, stackTop, beginBci, stackFrame, localNodes, options);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_FROM_COLLECTION: {
                        int type = Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeCollectionFromCollection(virtualFrame, localFrame, stackFrame, type, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_ADD_COLLECTION: {
                        /*
                         * The first collection must be in the target format already, the second one
                         * is a python object.
                         */
                        int type = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCollectionAddCollection(virtualFrame, stackFrame, type, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_FROM_STACK: {
                        int countAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int count = CollectionBits.elementCount(countAndType);
                        int type = CollectionBits.elementType(countAndType);
                        stackTop = bytecodeCollectionFromStack(virtualFrame, stackFrame, type, count, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_ADD_STACK: {
                        int countAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int count = CollectionBits.elementCount(countAndType);
                        int type = CollectionBits.elementType(countAndType);
                        // Just combine COLLECTION_FROM_STACK and COLLECTION_ADD_COLLECTION for now
                        stackTop = bytecodeCollectionFromStack(virtualFrame, stackFrame, type, count, stackTop, localNodes, beginBci);
                        stackTop = bytecodeCollectionAddCollection(virtualFrame, stackFrame, type, stackTop, localNodes, beginBci + 1);
                        break;
                    }
                    case OpCodesConstants.ADD_TO_COLLECTION: {
                        int depthAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int depth = CollectionBits.elementCount(depthAndType);
                        int type = CollectionBits.elementType(depthAndType);
                        stackTop = bytecodeAddToCollection(virtualFrame, stackFrame, stackTop, beginBci, localNodes, depth, type);
                        break;
                    }
                    case OpCodesConstants.KWARGS_DICT_MERGE: {
                        KwargsMergeNode mergeNode = insertChildNode(localNodes, bci, UNCACHED_KWARGS_MERGE, NODE_KWARGS_MERGE);
                        stackTop = mergeNode.execute(virtualFrame, stackTop, stackFrame);
                        break;
                    }
                    case OpCodesConstants.UNPACK_SEQUENCE: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeUnpackSequence(virtualFrame, stackFrame, stackTop, beginBci, localNodes, oparg);
                        break;
                    }
                    case OpCodesConstants.UNPACK_EX: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        int countAfter = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeUnpackEx(virtualFrame, stackFrame, stackTop, beginBci, localNodes, oparg, countAfter);
                        break;
                    }
                    case OpCodesConstants.NOP:
                        break;
                    case OpCodesConstants.LOAD_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        Object value = localFrame.getObject(oparg);
                        if (value == null) {
                            if (localArgs[bci] == 0) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                localArgs[bci] = 1;
                                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            } else {
                                PRaiseNode raiseNode = insertChildNode(localNodes, bci, NODE_RAISE);
                                throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                            }
                        }
                        stackFrame.setObject(++stackTop, value);
                        break;
                    }
                    case OpCodesConstants.LOAD_CLOSURE: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        PCell cell = (PCell) localFrame.getObject(celloffset + oparg);
                        stackFrame.setObject(++stackTop, cell);
                        break;
                    }
                    case OpCodesConstants.CLOSURE_FROM_STACK: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeClosureFromStack(stackFrame, stackTop, oparg);
                        break;
                    }
                    case OpCodesConstants.LOAD_CLASSDEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadClassDeref(virtualFrame, localFrame, stackFrame, locals, stackTop, beginBci, localNodes, oparg);
                        break;
                    }
                    case OpCodesConstants.LOAD_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadDeref(localFrame, stackFrame, stackTop, beginBci, localNodes, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreDeref(localFrame, stackFrame, stackTop, oparg);
                        break;
                    }
                    case OpCodesConstants.DELETE_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteDeref(localFrame, beginBci, localNodes, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        localFrame.setObject(oparg, stackFrame.getObject(stackTop));
                        stackFrame.setObject(stackTop--, null);
                        break;
                    }
                    case OpCodesConstants.POP_TOP:
                        stackFrame.setObject(stackTop--, null);
                        break;
                    case OpCodesConstants.ROT_TWO: {
                        Object top = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop, stackFrame.getObject(stackTop - 1));
                        stackFrame.setObject(stackTop - 1, top);
                        break;
                    }
                    case OpCodesConstants.ROT_THREE: {
                        Object top = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop, stackFrame.getObject(stackTop - 1));
                        stackFrame.setObject(stackTop - 1, stackFrame.getObject(stackTop - 2));
                        stackFrame.setObject(stackTop - 2, top);
                        break;
                    }
                    case OpCodesConstants.DUP_TOP:
                        stackFrame.setObject(stackTop + 1, stackFrame.getObject(stackTop));
                        stackTop++;
                        break;
                    case OpCodesConstants.UNARY_OP: {
                        int op = Byte.toUnsignedInt(localBC[++bci]);
                        UnaryOpNode opNode = insertChildNodeInt(localNodes, bci, UNARY_OP_FACTORY, op);
                        Object value = stackFrame.getObject(stackTop);
                        Object result = opNode.execute(virtualFrame, value);
                        stackFrame.setObject(stackTop, result);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP: {
                        int op = Byte.toUnsignedInt(localBC[++bci]);
                        BinaryOp opNode = (BinaryOp) insertChildNodeInt(localNodes, bci, BINARY_OP_FACTORY, op);
                        Object right = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        Object left = stackFrame.getObject(stackTop);
                        Object result = opNode.executeObject(virtualFrame, left, right);
                        stackFrame.setObject(stackTop, result);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR: {
                        GetItemNode getItemNode = insertChildNode(localNodes, bci, NODE_GET_ITEM);
                        Object slice = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        stackFrame.setObject(stackTop, getItemNode.execute(virtualFrame, stackFrame.getObject(stackTop), slice));
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR: {
                        stackTop = bytecodeStoreSubscr(virtualFrame, stackFrame, stackTop, beginBci, localNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_SUBSCR: {
                        stackTop = bytecodeDeleteSubscr(virtualFrame, stackFrame, stackTop, beginBci, localNodes);
                        break;
                    }
                    case OpCodesConstants.RAISE_VARARGS: {
                        int count = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeRaiseVarargs(virtualFrame, stackFrame, stackTop, beginBci, count, localNodes);
                        break;
                    }
                    case OpCodesConstants.RETURN_VALUE: {
                        if (CompilerDirectives.hasNextTier() && loopCount[0] > 0) {
                            LoopNode.reportLoopCount(this, loopCount[0]);
                        }
                        Object value = stackFrame.getObject(stackTop);
                        if (isGeneratorOrCoroutine) {
                            if (localFrame != stackFrame) {
                                clearFrameSlots(localFrame, stackoffset, stackTop + 1);
                            }
                            return GeneratorResult.createReturn(value);
                        } else {
                            return value;
                        }
                    }
                    case OpCodesConstants.LOAD_BUILD_CLASS: {
                        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes, beginBci, UNCACHED_READ_GLOBAL_OR_BUILTIN, NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS);
                        stackFrame.setObject(++stackTop, read.read(virtualFrame, globals, T___BUILD_CLASS__));
                        break;
                    }
                    case OpCodesConstants.LOAD_ASSERTION_ERROR: {
                        stackFrame.setObject(++stackTop, PythonBuiltinClassType.AssertionError);
                        break;
                    }
                    case OpCodesConstants.STORE_NAME: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreName(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNames, localNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_NAME: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteName(virtualFrame, globals, locals, beginBci, oparg, localNames, localNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_ATTR: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreAttr(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.DELETE_ATTR: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeDeleteAttr(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.STORE_GLOBAL: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreGlobal(virtualFrame, stackFrame, globals, stackTop, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.DELETE_GLOBAL: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteGlobal(virtualFrame, globals, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.LOAD_NAME: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadName(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.LOAD_GLOBAL: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadGlobal(virtualFrame, stackFrame, globals, stackTop, beginBci, localNames[oparg], localNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteFast(stackFrame, beginBci, localNodes, oparg);
                        break;
                    }
                    case OpCodesConstants.LOAD_ATTR: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeLoadAttr(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNodes, localNames);
                        break;
                    }
                    case OpCodesConstants.IMPORT_NAME: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportName(virtualFrame, stackFrame, globals, stackTop, beginBci, oparg, localNames, localNodes);
                        break;
                    }
                    case OpCodesConstants.IMPORT_FROM: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportFrom(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNames, localNodes);
                        break;
                    }
                    case OpCodesConstants.IMPORT_STAR: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportStar(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNames, localNodes);
                        break;
                    }
                    case OpCodesConstants.JUMP_FORWARD:
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bci += oparg;
                        oparg = 0;
                        continue;
                    case OpCodesConstants.POP_AND_JUMP_IF_FALSE: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE);
                        Object cond = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        if (!isTrue.execute(virtualFrame, cond)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        } else {
                            bci++;
                        }
                        break;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_TRUE: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE);
                        Object cond = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        if (isTrue.execute(virtualFrame, cond)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        } else {
                            bci++;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_IF_FALSE_OR_POP: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE);
                        Object cond = stackFrame.getObject(stackTop);
                        if (!isTrue.execute(stackFrame, cond)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        } else {
                            stackFrame.setObject(stackTop--, null);
                            bci++;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_IF_TRUE_OR_POP: {
                        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_IS_TRUE, NODE_OBJECT_IS_TRUE);
                        Object cond = stackFrame.getObject(stackTop);
                        if (isTrue.execute(virtualFrame, cond)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        } else {
                            stackFrame.setObject(stackTop--, null);
                            bci++;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_BACKWARD: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bci -= oparg;
                        if (CompilerDirectives.hasNextTier()) {
                            loopCount[0]++;
                        }
                        if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(osrNode)) {
                            /*
                             * Beware of race conditions when adding more things to the
                             * interpreterState argument. It gets stored already at this point, but
                             * the compilation runs in parallel. The compiled code may get entered
                             * from a different invocation of this root, using the interpreterState
                             * that was saved here. Don't put any data specific to particular
                             * invocation in there (like python-level arguments or variables) or it
                             * will get mixed up. To retain such state, put it into the frame
                             * instead.
                             */
                            Object osrResult = BytecodeOSRNode.tryOSR(osrNode, bci, new OSRInterpreterState(stackTop, beginBci), null, virtualFrame);
                            if (osrResult != null) {
                                if (osrResult instanceof OSRContinuation) {
                                    // We should continue executing in interpreter after the loop
                                    OSRContinuation continuation = (OSRContinuation) osrResult;
                                    bci = continuation.bci;
                                    stackTop = continuation.stackTop;
                                    oparg = 0;
                                    continue;
                                } else {
                                    // We reached a return/yield
                                    if (CompilerDirectives.hasNextTier() && loopCount[0] > 0) {
                                        LoopNode.reportLoopCount(this, loopCount[0]);
                                    }
                                    return osrResult;
                                }
                            }
                        }
                        oparg = 0;
                        continue;
                    }
                    case OpCodesConstants.GET_ITER: {
                        PyObjectGetIter getIter = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_GET_ITER, NODE_OBJECT_GET_ITER);
                        stackFrame.setObject(stackTop, getIter.execute(virtualFrame, stackFrame.getObject(stackTop)));
                        break;
                    }
                    case OpCodesConstants.FOR_ITER: {
                        Object next = insertChildNode(localNodes, beginBci, UNCACHED_GET_NEXT, NODE_GET_NEXT).execute(virtualFrame, stackFrame.getObject(stackTop));
                        if (next != null) {
                            stackFrame.setObject(++stackTop, next);
                            bci++;
                        } else {
                            stackFrame.setObject(stackTop--, null);
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        }
                        break;
                    }
                    case OpCodesConstants.CALL_METHOD: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        TruffleString methodName = localNames[oparg];
                        int argcount = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCallMethod(virtualFrame, stackFrame, stackTop, beginBci, argcount, methodName, localNodes);
                        break;
                    }
                    case OpCodesConstants.CALL_METHOD_VARARGS: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeCallMethodVarargs(virtualFrame, stackFrame, stackTop, beginBci, localNames, oparg, localNodes);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCallFunction(virtualFrame, stackFrame, stackTop, beginBci, oparg, localNodes);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION_VARARGS: {
                        stackTop = bytecodeCallFunctionVarargs(virtualFrame, stackFrame, stackTop, beginBci, localNodes);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION_KW: {
                        stackTop = bytecodeCallFunctionKw(virtualFrame, stackFrame, stackTop, beginBci, localNodes);
                        break;
                    }
                    case OpCodesConstants.MAKE_FUNCTION: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        int flags = Byte.toUnsignedInt(localBC[++bci]);
                        CodeUnit codeUnit = (CodeUnit) localConsts[oparg];
                        MakeFunctionNode makeFunctionNode = insertChildNode(localNodes, beginBci, () -> MakeFunctionNode.create(PythonLanguage.get(this), codeUnit, source));
                        stackTop = makeFunctionNode.execute(globals, stackTop, stackFrame, flags);
                        break;
                    }
                    case OpCodesConstants.MATCH_EXC_OR_JUMP: {
                        Object exception = stackFrame.getObject(stackTop - 1);
                        Object matchType = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        ExceptMatchNode matchNode = insertChildNode(localNodes, beginBci, UNCACHED_EXCEPT_MATCH, NODE_EXCEPT_MATCH);
                        if (!matchNode.executeMatch(virtualFrame, exception, matchType)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        } else {
                            bci++;
                        }
                        break;
                    }
                    case OpCodesConstants.UNWRAP_EXC: {
                        Object exception = stackFrame.getObject(stackTop);
                        if (exception instanceof PException) {
                            stackFrame.setObject(stackTop, ((PException) exception).getEscapedException());
                        }
                        // Let interop exceptions be
                        break;
                    }
                    case OpCodesConstants.SETUP_WITH: {
                        SetupWithNode setupWithNode = insertChildNode(localNodes, beginBci, UNCACHED_SETUP_WITH_NODE, NODE_SETUP_WITH);
                        stackTop = setupWithNode.execute(virtualFrame, stackTop, stackFrame);
                        break;
                    }
                    case OpCodesConstants.EXIT_WITH: {
                        ExitWithNode exitWithNode = insertChildNode(localNodes, beginBci, UNCACHED_EXIT_WITH_NODE, NODE_EXIT_WITH);
                        stackTop = exitWithNode.execute(virtualFrame, stackTop, stackFrame);
                        break;
                    }
                    case OpCodesConstants.PUSH_EXC_INFO: {
                        Object exception = stackFrame.getObject(stackTop);
                        if (!(exception instanceof PException)) {
                            throw CompilerDirectives.shouldNotReachHere("interop exception state not implemented");
                        }
                        if (!fetchedException) {
                            outerException = PArguments.getException(virtualFrame);
                            fetchedException = true;
                        }
                        stackFrame.setObject(stackTop++, localException);
                        localException = (PException) exception;
                        PArguments.setException(virtualFrame, localException);
                        stackFrame.setObject(stackTop, exception);
                        break;
                    }
                    case OpCodesConstants.POP_EXCEPT: {
                        localException = popExceptionState(virtualFrame, stackFrame.getObject(stackTop), outerException);
                        stackFrame.setObject(stackTop--, null);
                        break;
                    }
                    case OpCodesConstants.END_EXC_HANDLER: {
                        localException = popExceptionState(virtualFrame, stackFrame.getObject(stackTop - 1), outerException);
                        throw bytecodeEndExcHandler(stackFrame, stackTop);
                    }
                    case OpCodesConstants.YIELD_VALUE: {
                        if (CompilerDirectives.hasNextTier() && loopCount[0] > 0) {
                            LoopNode.reportLoopCount(this, loopCount[0]);
                        }
                        Object value = stackFrame.getObject(stackTop);
                        stackFrame.setObject(stackTop--, null);
                        PArguments.setException(PArguments.getGeneratorFrame(virtualFrame), localException);
                        // See PBytecodeGeneratorRootNode#execute
                        if (localFrame != stackFrame) {
                            copyStackSlotsToGeneratorFrame(stackFrame, localFrame, stackTop);
                            // Clear slots that were popped (if any)
                            clearFrameSlots(localFrame, stackTop + 1, initialStackTop);
                        }
                        return GeneratorResult.createYield(bci + 1, stackTop, value);
                    }
                    case OpCodesConstants.RESUME_YIELD: {
                        Object sendValue = PArguments.getSpecialArgument(virtualFrame);
                        if (sendValue == null) {
                            sendValue = PNone.NONE;
                        } else if (sendValue instanceof ThrowData) {
                            ThrowData throwData = (ThrowData) sendValue;
                            throw PException.fromObject(throwData.pythonException, this, throwData.withJavaStacktrace);
                        }
                        localException = PArguments.getException(PArguments.getGeneratorFrame(virtualFrame));
                        if (localException != null) {
                            PArguments.setException(virtualFrame, localException);
                        }
                        stackFrame.setObject(++stackTop, sendValue);
                        break;
                    }
                    case OpCodesConstants.SEND: {
                        Object value = stackFrame.getObject(stackTop);
                        Object obj = stackFrame.getObject(stackTop - 1);
                        SendNode sendNode = insertChildNode(localNodes, beginBci, NODE_SEND);
                        boolean returned = sendNode.execute(virtualFrame, stackTop, stackFrame, obj, value);
                        if (!returned) {
                            bci++;
                            break;
                        } else {
                            stackTop--;
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        }
                    }
                    case OpCodesConstants.THROW: {
                        Object exception = stackFrame.getObject(stackTop);
                        if (!(exception instanceof PException)) {
                            throw CompilerDirectives.shouldNotReachHere("interop exceptions not supported in throw");
                        }
                        Object obj = stackFrame.getObject(stackTop - 1);
                        ThrowNode throwNode = insertChildNode(localNodes, beginBci, NODE_THROW);
                        boolean returned = throwNode.execute(virtualFrame, stackTop, stackFrame, obj, (PException) exception);
                        if (!returned) {
                            bci++;
                            break;
                        } else {
                            stackTop--;
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            continue;
                        }
                    }
                    case OpCodesConstants.PRINT_EXPR: {
                        PrintExprNode printExprNode = insertChildNode(localNodes, beginBci, UNCACHED_PRINT_EXPR, NODE_PRINT_EXPR);
                        printExprNode.execute(virtualFrame, stackFrame.getObject(stackTop));
                        stackFrame.setObject(stackTop--, null);
                        break;
                    }
                    case OpCodesConstants.EXTENDED_ARG: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        oparg <<= 8;
                        bci++;
                        continue;
                    }
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw insertChildNode(localNodes, bci, NODE_RAISE).raise(SystemError, toTruffleStringUncached("not implemented bytecode %s"), OpCodes.VALUES[bc]);
                }
                // prepare next loop
                oparg = 0;
                bci++;
            } catch (Exception | StackOverflowError | AssertionError e) {
                // TODO interop exceptions
                PException pe;
                if (e instanceof PException) {
                    pe = (PException) e;
                } else {
                    pe = wrapJavaExceptionIfApplicable(e);
                    if (pe == null) {
                        throw e;
                    }
                }
                long newTarget = findHandler(bci);
                CompilerAsserts.partialEvaluationConstant(newTarget);
                if (localException != null) {
                    ExceptionHandlingStatementNode.chainExceptions(pe.getUnreifiedException(), localException, exceptionChainProfile1, exceptionChainProfile2);
                } else {
                    if (getCaughtExceptionNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getCaughtExceptionNode = ExceptionStateNodes.GetCaughtExceptionNode.create();
                    }
                    PException exceptionState = getCaughtExceptionNode.execute(virtualFrame);
                    if (exceptionState != null) {
                        ExceptionHandlingStatementNode.chainExceptions(pe.getUnreifiedException(), exceptionState, exceptionChainProfile1, exceptionChainProfile2);
                    }
                }
                if (newTarget == -1) {
                    // For tracebacks
                    virtualFrame.setIntStatic(bciSlot, beginBci);
                    if (isGeneratorOrCoroutine) {
                        if (localFrame != stackFrame) {
                            // Unwind the generator frame stack
                            clearFrameSlots(localFrame, stackoffset, initialStackTop);
                        }
                    }
                    if (CompilerDirectives.hasNextTier() && loopCount[0] > 0) {
                        LoopNode.reportLoopCount(this, loopCount[0]);
                    }
                    if (e == pe) {
                        throw pe;
                    } else {
                        throw pe.getExceptionForReraise();
                    }
                } else {
                    pe.setCatchingFrameReference(virtualFrame, this, bci);
                    int stackSizeOnEntry = decodeStackTop((int) newTarget);
                    stackTop = unwindBlock(stackFrame, stackTop, stackSizeOnEntry + stackoffset);
                    // handler range encodes the stacksize, not the top of stack. so the stackTop is
                    // to be replaced with the exception
                    stackFrame.setObject(stackTop, pe);
                    bci = decodeBCI((int) newTarget);
                    oparg = 0;
                }
            }
        }
    }

    protected PException wrapJavaExceptionIfApplicable(Throwable e) {
        if (e instanceof ControlFlowException) {
            return null;
        }
        if (PythonLanguage.get(this).getEngineOption(PythonOptions.CatchAllExceptions) && (e instanceof Exception || e instanceof AssertionError)) {
            return wrapJavaException(e, factory.createBaseException(SystemError, ErrorMessages.M, new Object[]{e}));
        }
        if (e instanceof StackOverflowError) {
            PythonContext.get(this).reacquireGilAfterStackOverflow();
            return wrapJavaException(e, factory.createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{}));
        }
        return null;
    }

    public PException wrapJavaException(Throwable e, PBaseException pythonException) {
        PException pe = PException.fromObject(pythonException, this, e);
        pe.setHideLocation(true);
        // Host exceptions have their stacktrace already filled in, call this to set
        // the cutoff point to the catch site
        pe.getTruffleStackTrace();
        return pe;
    }

    @ExplodeLoop
    private void copyStackSlotsToGeneratorFrame(Frame virtualFrame, Frame generatorFrame, int stackTop) {
        for (int i = stackoffset; i <= stackTop; i++) {
            if (virtualFrame.isObject(i)) {
                generatorFrame.setObject(i, virtualFrame.getObject(i));
            } else if (virtualFrame.isInt(i)) {
                generatorFrame.setInt(i, virtualFrame.getInt(i));
            } else if (virtualFrame.isLong(i)) {
                generatorFrame.setLong(i, virtualFrame.getLong(i));
            } else if (virtualFrame.isDouble(i)) {
                generatorFrame.setDouble(i, virtualFrame.getDouble(i));
            } else if (virtualFrame.isBoolean(i)) {
                generatorFrame.setBoolean(i, virtualFrame.getBoolean(i));
            } else {
                throw CompilerDirectives.shouldNotReachHere("unexpected frame slot type");
            }
        }
    }

    @ExplodeLoop
    private void clearFrameSlots(Frame frame, int start, int end) {
        CompilerAsserts.partialEvaluationConstant(start);
        CompilerAsserts.partialEvaluationConstant(end);
        for (int i = start; i <= end; i++) {
            frame.setObject(i, null);
        }
    }

    private int bytecodeFormatValue(VirtualFrame virtualFrame, int initialStackTop, int bci, Frame stackFrame, Node[] localNodes, int options) {
        int stackTop = initialStackTop;
        int type = options & FormatOptions.FVC_MASK;
        Object spec = PNone.NO_VALUE;
        if ((options & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC) {
            spec = stackFrame.getObject(stackTop);
            stackFrame.setObject(stackTop--, null);
        }
        Object value = stackFrame.getObject(stackTop);
        switch (type) {
            case FormatOptions.FVC_STR:
                value = insertChildNode(localNodes, bci, UNCACHED_STR, NODE_STR).execute(virtualFrame, value);
                break;
            case FormatOptions.FVC_REPR:
                value = insertChildNode(localNodes, bci, UNCACHED_REPR, NODE_REPR).execute(virtualFrame, value);
                break;
            case FormatOptions.FVC_ASCII:
                value = insertChildNode(localNodes, bci, UNCACHED_ASCII, NODE_ASCII).execute(virtualFrame, value);
                break;
            default:
                assert type == FormatOptions.FVC_NONE;
        }
        BuiltinFunctions.FormatNode formatNode = insertChildNode(localNodes, bci + 1, NODE_FORMAT);
        value = formatNode.execute(virtualFrame, value, spec);
        stackFrame.setObject(stackTop, value);
        return stackTop;
    }

    private void bytecodeDeleteDeref(Frame localFrame, int bci, Node[] localNodes, int oparg) {
        PCell cell = (PCell) localFrame.getObject(celloffset + oparg);
        Object value = cell.getRef();
        if (value == null) {
            raiseUnboundCell(localNodes, bci, oparg);
        }
        cell.clearRef();
    }

    private int bytecodeStoreDeref(Frame localFrame, Frame stackFrame, int stackTop, int oparg) {
        PCell cell = (PCell) localFrame.getObject(celloffset + oparg);
        Object value = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        cell.setRef(value);
        return stackTop;
    }

    private int bytecodeLoadClassDeref(VirtualFrame virtualFrame, Frame localFrame, Frame stackFrame, Object locals, int stackTop, int bci, Node[] localNodes, int oparg) {
        TruffleString name;
        boolean isCellVar;
        if (oparg < cellvars.length) {
            name = cellvars[oparg];
            isCellVar = true;
        } else {
            name = freevars[oparg - cellvars.length];
            isCellVar = false;
        }
        GetNameFromLocalsNode getNameFromLocals = insertChildNode(localNodes, bci, NODE_GET_NAME_FROM_LOCALS);
        Object value = getNameFromLocals.execute(virtualFrame, locals, name, isCellVar);
        if (value != null) {
            stackFrame.setObject(++stackTop, value);
            return stackTop;
        } else {
            return bytecodeLoadDeref(localFrame, stackFrame, stackTop, bci, localNodes, oparg);
        }
    }

    private int bytecodeLoadDeref(Frame localFrame, Frame stackFrame, int stackTop, int bci, Node[] localNodes, int oparg) {
        PCell cell = (PCell) localFrame.getObject(celloffset + oparg);
        Object value = cell.getRef();
        if (value == null) {
            raiseUnboundCell(localNodes, bci, oparg);
        }
        stackFrame.setObject(++stackTop, value);
        return stackTop;
    }

    private int bytecodeClosureFromStack(Frame stackFrame, int stackTop, int oparg) {
        PCell[] closure = new PCell[oparg];
        moveFromStack(stackFrame, stackTop - oparg + 1, stackTop + 1, closure);
        stackTop -= oparg - 1;
        stackFrame.setObject(stackTop, closure);
        return stackTop;
    }

    private PException popExceptionState(VirtualFrame virtualFrame, Object savedException, PException outerException) {
        PException localException = null;
        if (savedException instanceof PException) {
            localException = (PException) savedException;
        }
        if (savedException == null) {
            savedException = outerException;
        }
        PArguments.setException(virtualFrame, (PException) savedException);
        return localException;
    }

    private PException bytecodeEndExcHandler(Frame stackFrame, int stackTop) {
        Object exception = stackFrame.getObject(stackTop);
        if (exception instanceof PException) {
            throw ((PException) exception).getExceptionForReraise();
        } else if (exception instanceof AbstractTruffleException) {
            throw (AbstractTruffleException) exception;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(this, SystemError, ErrorMessages.EXPECTED_EXCEPTION_ON_THE_STACK);
        }
    }

    private void bytecodeLoadAttr(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        PyObjectGetAttr getAttr = insertChildNode(localNodes, bci, UNCACHED_OBJECT_GET_ATTR, NODE_OBJECT_GET_ATTR);
        TruffleString varname = localNames[oparg];
        Object owner = stackFrame.getObject(stackTop);
        Object value = getAttr.execute(virtualFrame, owner, varname);
        stackFrame.setObject(stackTop, value);
    }

    private void bytecodeDeleteFast(Frame localFrame, int bci, Node[] localNodes, int oparg) {
        Object value = localFrame.getObject(oparg);
        if (value == null) {
            PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, NODE_RAISE);
            throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
        }
        localFrame.setObject(oparg, null);
    }

    private int bytecodeLoadGlobal(VirtualFrame virtualFrame, Frame stackFrame, Object globals, int stackTop, int bci, TruffleString localName, Node[] localNodes) {
        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes, bci, UNCACHED_READ_GLOBAL_OR_BUILTIN, NODE_READ_GLOBAL_OR_BUILTIN, localName);
        stackFrame.setObject(++stackTop, read.read(virtualFrame, globals, localName));
        return stackTop;
    }

    private void bytecodeDeleteGlobal(VirtualFrame virtualFrame, Object globals, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        TruffleString varname = localNames[oparg];
        DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes, bci, NODE_DELETE_GLOBAL, varname);
        deleteGlobalNode.executeWithGlobals(virtualFrame, globals);
    }

    private int bytecodeStoreGlobal(VirtualFrame virtualFrame, Frame stackFrame, Object globals, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        TruffleString varname = localNames[oparg];
        WriteGlobalNode writeGlobalNode = insertChildNode(localNodes, bci, UNCACHED_WRITE_GLOBAL, NODE_WRITE_GLOBAL, varname);
        writeGlobalNode.write(virtualFrame, globals, varname, stackFrame.getObject(stackTop));
        stackFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private int bytecodeDeleteAttr(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        PyObjectSetAttr callNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ATTR, NODE_OBJECT_SET_ATTR);
        TruffleString varname = localNames[oparg];
        Object owner = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        callNode.delete(virtualFrame, owner, varname);
        return stackTop;
    }

    private int bytecodeStoreAttr(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        PyObjectSetAttr callNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ATTR, NODE_OBJECT_SET_ATTR);
        TruffleString varname = localNames[oparg];
        Object owner = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        Object value = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        callNode.execute(virtualFrame, owner, varname, value);
        return stackTop;
    }

    private void bytecodeDeleteName(VirtualFrame virtualFrame, Object globals, Object locals, int bci, int oparg, TruffleString[] localNames, Node[] localNodes) {
        TruffleString varname = localNames[oparg];
        if (locals != null) {
            PyObjectDelItem delItemNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_DEL_ITEM, NODE_OBJECT_DEL_ITEM);
            delItemNode.execute(virtualFrame, locals, varname);
        } else {
            DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes, bci + 1, NODE_DELETE_GLOBAL, varname);
            deleteGlobalNode.executeWithGlobals(virtualFrame, globals);
        }
    }

    private int bytecodeDeleteSubscr(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, Node[] localNodes) {
        DeleteItemNode delItem = insertChildNode(localNodes, bci, NODE_DELETE_ITEM);
        Object slice = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        Object container = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        delItem.executeWith(virtualFrame, container, slice);
        return stackTop;
    }

    private int bytecodeStoreSubscr(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, Node[] localNodes) {
        PyObjectSetItem setItem = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ITEM, NODE_OBJECT_SET_ITEM);
        Object index = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        Object container = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        Object value = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        setItem.execute(virtualFrame, container, index, value);
        return stackTop;
    }

    private int bytecodeBuildSlice(Frame stackFrame, int stackTop, int bci, int count, Node[] localNodes) {
        Object step;
        if (count == 3) {
            step = stackFrame.getObject(stackTop);
            stackFrame.setObject(stackTop--, null);
        } else {
            assert count == 2;
            step = PNone.NONE;
        }
        Object stop = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        Object start = stackFrame.getObject(stackTop);
        SliceNodes.CreateSliceNode sliceNode = insertChildNode(localNodes, bci, UNCACHED_CREATE_SLICE, NODE_CREATE_SLICE);
        PSlice slice = sliceNode.execute(start, stop, step);
        stackFrame.setObject(stackTop, slice);
        return stackTop;
    }

    private int bytecodeCallFunctionKw(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, Node[] localNodes) {
        int stackTop = initialStackTop;
        CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, NODE_CALL);
        Object callable = stackFrame.getObject(stackTop - 2);
        Object[] args = (Object[]) stackFrame.getObject(stackTop - 1);
        stackFrame.setObject(stackTop - 2, callNode.execute(virtualFrame, callable, args, (PKeyword[]) stackFrame.getObject(stackTop)));
        stackFrame.setObject(stackTop--, null);
        stackFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private int bytecodeCallFunctionVarargs(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, Node[] localNodes) {
        int stackTop = initialStackTop;
        CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, NODE_CALL);
        Object callable = stackFrame.getObject(stackTop - 1);
        Object[] args = (Object[]) stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop - 1, callNode.execute(virtualFrame, callable, args, PKeyword.EMPTY_KEYWORDS));
        stackFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private void bytecodeCallMethodVarargs(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, TruffleString[] localNames, int oparg, Node[] localNodes) {
        PyObjectGetMethod getMethodNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_GET_METHOD, NODE_OBJECT_GET_METHOD);
        Object[] args = (Object[]) stackFrame.getObject(stackTop);
        TruffleString methodName = localNames[oparg];
        Object rcvr = args[0];
        Object func = getMethodNode.execute(virtualFrame, rcvr, methodName);
        CallNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL, NODE_CALL);
        stackFrame.setObject(stackTop, callNode.execute(virtualFrame, func, args, PKeyword.EMPTY_KEYWORDS));
    }

    private int bytecodeLoadName(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames) {
        int stackTop = initialStackTop;
        ReadNameNode readNameNode = insertChildNode(localNodes, bci, NODE_READ_NAME, localNames[oparg]);
        stackFrame.setObject(++stackTop, readNameNode.execute(virtualFrame));
        return stackTop;
    }

    private int bytecodeCallFunction(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int oparg, Node[] localNodes) {
        Object func = stackFrame.getObject(stackTop - oparg);
        switch (oparg) {
            case 0: {
                CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, NODE_CALL);
                Object result = callNode.execute(virtualFrame, func, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
                stackFrame.setObject(stackTop, result);
                break;
            }
            case 1: {
                CallUnaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_UNARY_METHOD, NODE_CALL_UNARY_METHOD);
                Object result = callNode.executeObject(virtualFrame, func, stackFrame.getObject(stackTop));
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, result);
                break;
            }
            case 2: {
                CallBinaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_BINARY_METHOD, NODE_CALL_BINARY_METHOD);
                Object arg1 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg0 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, callNode.executeObject(virtualFrame, func, arg0, arg1));
                break;
            }
            case 3: {
                CallTernaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_TERNARY_METHOD, NODE_CALL_TERNARY_METHOD);
                Object arg2 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg1 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg0 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, callNode.execute(virtualFrame, func, arg0, arg1, arg2));
                break;
            }
            case 4: {
                CallQuaternaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_QUATERNARY_METHOD, NODE_CALL_QUATERNARY_METHOD);
                Object arg3 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg2 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg1 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg0 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, callNode.execute(virtualFrame, func, arg0, arg1, arg2, arg3));
                break;
            }
        }
        return stackTop;
    }

    private int bytecodeCallMethod(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int oparg, TruffleString methodName, Node[] localNodes) {
        Object rcvr = stackFrame.getObject(stackTop - oparg);
        PyObjectGetMethod getMethodNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_GET_METHOD, NODE_OBJECT_GET_METHOD);
        Object func = getMethodNode.execute(virtualFrame, rcvr, methodName);
        switch (oparg) {
            case 0: {
                CallUnaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_UNARY_METHOD, NODE_CALL_UNARY_METHOD);
                Object result = callNode.executeObject(virtualFrame, func, rcvr);
                stackFrame.setObject(stackTop, result);
                break;
            }
            case 1: {
                CallBinaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_BINARY_METHOD, NODE_CALL_BINARY_METHOD);
                Object result = callNode.executeObject(virtualFrame, func, rcvr, stackFrame.getObject(stackTop));
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, result);
                break;
            }
            case 2: {
                CallTernaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_TERNARY_METHOD, NODE_CALL_TERNARY_METHOD);
                Object arg1 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg0 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, callNode.execute(virtualFrame, func, rcvr, arg0, arg1));
                break;
            }
            case 3: {
                CallQuaternaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_QUATERNARY_METHOD, NODE_CALL_QUATERNARY_METHOD);
                Object arg2 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg1 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                Object arg0 = stackFrame.getObject(stackTop);
                stackFrame.setObject(stackTop--, null);
                stackFrame.setObject(stackTop, callNode.execute(virtualFrame, func, rcvr, arg0, arg1, arg2));
                break;
            }
        }
        return stackTop;
    }

    private int bytecodeStoreName(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes) {
        int stackTop = initialStackTop;
        Object value = stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        WriteNameNode writeNameNode = insertChildNode(localNodes, bci, NODE_WRITE_NAME, localNames[oparg]);
        writeNameNode.execute(virtualFrame, value);
        return stackTop;
    }

    private int bytecodeRaiseVarargs(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, int count, Node[] localNodes) {
        RaiseNode raiseNode = insertChildNode(localNodes, bci, NODE_RAISENODE);
        Object cause;
        Object exception;
        if (count > 1) {
            cause = stackFrame.getObject(stackTop);
            stackFrame.setObject(stackTop--, null);
        } else {
            cause = PNone.NO_VALUE;
        }
        if (count > 0) {
            exception = stackFrame.getObject(stackTop);
            stackFrame.setObject(stackTop--, null);
        } else {
            exception = PNone.NO_VALUE;
        }
        raiseNode.execute(virtualFrame, exception, cause);
        return stackTop;
    }

    private void raiseUnboundCell(Node[] localNodes, int bci, int oparg) {
        PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, NODE_RAISE);
        if (oparg < freeoffset) {
            int varIdx = oparg;
            throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, cellvars[varIdx]);
        } else {
            int varIdx = oparg - cellvars.length;
            throw raiseNode.raise(PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, freevars[varIdx]);
        }
    }

    private int bytecodeImportName(VirtualFrame virtualFrame, Frame stackFrame, Object globals, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes) {
        CastToJavaIntExactNode castNode = insertChildNode(localNodes, bci, UNCACHED_CAST_TO_JAVA_INT_EXACT, NODE_CAST_TO_JAVA_INT_EXACT);
        TruffleString modname = localNames[oparg];
        int stackTop = initialStackTop;
        TruffleString[] fromlist = (TruffleString[]) stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        int level = castNode.execute(stackFrame.getObject(stackTop));
        ImportNode importNode = insertChildNode(localNodes, bci + 1, NODE_IMPORT);
        Object result = importNode.execute(virtualFrame, modname, globals, fromlist, level);
        stackFrame.setObject(stackTop, result);
        return stackTop;
    }

    private int bytecodeImportFrom(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes) {
        int stackTop = initialStackTop;
        TruffleString name = localNames[oparg];
        Object from = stackFrame.getObject(stackTop);
        ImportFromNode importFromNode = insertChildNode(localNodes, bci, UNCACHED_IMPORT_FROM, NODE_IMPORT_FROM);
        Object imported = importFromNode.execute(virtualFrame, from, name);
        stackFrame.setObject(++stackTop, imported);
        return stackTop;
    }

    private int bytecodeImportStar(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes) {
        int stackTop = initialStackTop;
        TruffleString name = localNames[oparg];
        int level = (int) stackFrame.getObject(stackTop);
        stackFrame.setObject(stackTop--, null);
        ImportStarNode importStarNode = insertChildNode(localNodes, bci, () -> new ImportStarNode(name, level));
        importStarNode.executeVoid(virtualFrame);
        return stackTop;
    }

    private void initCellVars(Frame localFrame) {
        if (cellvars.length <= 32) {
            initCellVarsExploded(localFrame);
        } else {
            initCellVarsLoop(localFrame);
        }
    }

    @ExplodeLoop
    private void initCellVarsExploded(Frame localFrame) {
        for (int i = 0; i < cellvars.length; i++) {
            initCell(localFrame, i);
        }
    }

    private void initCellVarsLoop(Frame localFrame) {
        for (int i = 0; i < cellvars.length; i++) {
            initCell(localFrame, i);
        }
    }

    private void initCell(Frame localFrame, int i) {
        PCell cell = new PCell(cellEffectivelyFinalAssumptions[i]);
        localFrame.setObject(celloffset + i, cell);
        if (co.cell2arg != null && co.cell2arg[i] != -1) {
            int idx = co.cell2arg[i];
            cell.setRef(localFrame.getObject(idx));
            localFrame.setObject(idx, null);
        }
    }

    private void initFreeVars(Frame localFrame, Object[] originalArgs) {
        if (freevars.length > 0) {
            if (freevars.length <= 32) {
                initFreeVarsExploded(localFrame, originalArgs);
            } else {
                initFreeVarsLoop(localFrame, originalArgs);
            }
        }
    }

    @ExplodeLoop
    private void initFreeVarsExploded(Frame localFrame, Object[] originalArgs) {
        PCell[] closure = PArguments.getClosure(originalArgs);
        for (int i = 0; i < freevars.length; i++) {
            localFrame.setObject(freeoffset + i, closure[i]);
        }
    }

    private void initFreeVarsLoop(Frame localFrame, Object[] originalArgs) {
        PCell[] closure = PArguments.getClosure(originalArgs);
        for (int i = 0; i < freevars.length; i++) {
            localFrame.setObject(freeoffset + i, closure[i]);
        }
    }

    @ExplodeLoop
    @SuppressWarnings("unchecked")
    private static <T> void moveFromStack(Frame stackFrame, int start, int stop, T[] target) {
        CompilerAsserts.partialEvaluationConstant(start);
        CompilerAsserts.partialEvaluationConstant(stop);
        for (int j = 0, i = start; i < stop; i++, j++) {
            target[j] = (T) stackFrame.getObject(i);
            stackFrame.setObject(i, null);
        }
    }

    private int bytecodeCollectionFromStack(VirtualFrame virtualFrame, Frame stackFrame, int type, int count, int oldStackTop, Node[] localNodes, int nodeIndex) {
        int stackTop = oldStackTop;
        Object res = null;
        switch (type) {
            case CollectionBits.LIST: {
                Object[] store = new Object[count];
                moveFromStack(stackFrame, stackTop - count + 1, stackTop + 1, store);
                res = factory.createList(store);
                break;
            }
            case CollectionBits.TUPLE: {
                Object[] store = new Object[count];
                moveFromStack(stackFrame, stackTop - count + 1, stackTop + 1, store);
                res = factory.createTuple(store);
                break;
            }
            case CollectionBits.SET: {
                PSet set = factory.createSet();
                HashingCollectionNodes.SetItemNode newNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, NODE_SET_ITEM);
                for (int i = stackTop - count + 1; i <= stackTop; i++) {
                    newNode.execute(virtualFrame, set, stackFrame.getObject(i), PNone.NONE);
                    stackFrame.setObject(i, null);
                }
                res = set;
                break;
            }
            case CollectionBits.DICT: {
                PDict dict = factory.createDict();
                HashingCollectionNodes.SetItemNode setItem = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, NODE_SET_ITEM);
                assert count % 2 == 0;
                for (int i = stackTop - count + 1; i <= stackTop; i += 2) {
                    setItem.execute(virtualFrame, dict, stackFrame.getObject(i), stackFrame.getObject(i + 1));
                    stackFrame.setObject(i, null);
                    stackFrame.setObject(i + 1, null);
                }
                res = dict;
                break;
            }
            case CollectionBits.KWORDS: {
                PKeyword[] kwds = new PKeyword[count];
                moveFromStack(stackFrame, stackTop - count + 1, stackTop + 1, kwds);
                res = kwds;
                break;
            }
            case CollectionBits.OBJECT: {
                Object[] objs = new Object[count];
                moveFromStack(stackFrame, stackTop - count + 1, stackTop + 1, objs);
                res = objs;
                break;
            }
        }
        stackTop -= count;
        stackFrame.setObject(++stackTop, res);
        return stackTop;
    }

    private void bytecodeCollectionFromCollection(VirtualFrame virtualFrame, Frame localFrame, Frame stackFrame, int type, int stackTop, Node[] localNodes, int nodeIndex) {
        Object sourceCollection = stackFrame.getObject(stackTop);
        Object result;
        switch (type) {
            case CollectionBits.LIST: {
                ListNodes.ConstructListNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_LIST, NODE_CONSTRUCT_LIST);
                result = constructNode.execute(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.TUPLE: {
                TupleNodes.ConstructTupleNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_TUPLE, NODE_CONSTRUCT_TUPLE);
                result = constructNode.execute(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.SET: {
                SetNodes.ConstructSetNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_SET, NODE_CONSTRUCT_SET);
                result = constructNode.executeWith(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.DICT: {
                // TODO create uncached node
                HashingStorage.InitNode initNode = insertChildNode(localNodes, nodeIndex, NODE_HASHING_STORAGE_INIT);
                HashingStorage storage = initNode.execute(virtualFrame, sourceCollection, PKeyword.EMPTY_KEYWORDS);
                result = factory.createDict(storage);
                break;
            }
            case CollectionBits.OBJECT: {
                ExecutePositionalStarargsNode executeStarargsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_EXECUTE_STARARGS, NODE_EXECUTE_STARARGS);
                result = executeStarargsNode.executeWith(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.KWORDS: {
                KeywordsNode keywordsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_KEYWORDS, NODE_KEYWORDS);
                result = keywordsNode.execute(virtualFrame, sourceCollection, stackTop, localFrame);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere("Unexpected collection type");
        }
        stackFrame.setObject(stackTop, result);
    }

    private int bytecodeCollectionAddCollection(VirtualFrame virtualFrame, Frame stackFrame, int type, int initialStackTop, Node[] localNodes, int nodeIndex) {
        int stackTop = initialStackTop;
        Object collection1 = stackFrame.getObject(stackTop - 1);
        Object collection2 = stackFrame.getObject(stackTop);
        Object result;
        switch (type) {
            case CollectionBits.LIST: {
                // TODO uncached node
                ListBuiltins.ListExtendNode extendNode = insertChildNode(localNodes, nodeIndex, NODE_LIST_EXTEND);
                extendNode.execute(virtualFrame, (PList) collection1, collection2);
                result = collection1;
                break;
            }
            case CollectionBits.SET: {
                SetBuiltins.UpdateSingleNode updateNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_UPDATE, NODE_SET_UPDATE);
                PSet set = (PSet) collection1;
                set.setDictStorage(updateNode.execute(virtualFrame, set.getDictStorage(), collection2));
                result = set;
                break;
            }
            case CollectionBits.DICT: {
                // TODO uncached node
                DictNodes.UpdateNode updateNode = insertChildNode(localNodes, nodeIndex, NODE_DICT_UPDATE);
                updateNode.execute(virtualFrame, (PDict) collection1, collection2);
                result = collection1;
                break;
            }
            // Note: we don't allow this operation for tuple
            case CollectionBits.OBJECT: {
                Object[] array1 = (Object[]) collection1;
                ExecutePositionalStarargsNode executeStarargsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_EXECUTE_STARARGS, NODE_EXECUTE_STARARGS);
                Object[] array2 = executeStarargsNode.executeWith(virtualFrame, collection2);
                Object[] combined = new Object[array1.length + array2.length];
                System.arraycopy(array1, 0, combined, 0, array1.length);
                System.arraycopy(array2, 0, combined, array1.length, array2.length);
                result = combined;
                break;
            }
            case CollectionBits.KWORDS: {
                throw CompilerDirectives.shouldNotReachHere("keywords merging handled elsewhere");
            }
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.getUncached().raise(SystemError, ErrorMessages.INVALID_TYPE_FOR_S, "COLLECTION_ADD_COLLECTION");
        }
        stackFrame.setObject(stackTop--, null);
        stackFrame.setObject(stackTop, result);
        return stackTop;
    }

    private int bytecodeAddToCollection(VirtualFrame virtualFrame, Frame stackFrame, int initialStackTop, int nodeIndex, Node[] localNodes, int depth, int type) {
        int stackTop = initialStackTop;
        Object collection = stackFrame.getObject(stackTop - depth);
        Object item = stackFrame.getObject(stackTop);
        switch (type) {
            case CollectionBits.LIST: {
                ListNodes.AppendNode appendNode = insertChildNode(localNodes, nodeIndex, UNCACHED_LIST_APPEND, NODE_LIST_APPEND);
                appendNode.execute((PList) collection, item);
                break;
            }
            case CollectionBits.SET: {
                SetNodes.AddNode addNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ADD, NODE_SET_ADD);
                addNode.execute(virtualFrame, (PSet) collection, item);
                break;
            }
            case CollectionBits.DICT: {
                Object key = stackFrame.getObject(stackTop - 1);
                HashingCollectionNodes.SetItemNode setItem = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, NODE_SET_ITEM);
                setItem.execute(virtualFrame, (PDict) collection, key, item);
                stackFrame.setObject(stackTop--, null);
                break;
            }
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.getUncached().raise(SystemError, ErrorMessages.INVALID_TYPE_FOR_S, "ADD_TO_COLLECTION");
        }
        stackFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private int bytecodeUnpackSequence(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, Node[] localNodes, int count) {
        UnpackSequenceNode unpackNode = insertChildNode(localNodes, bci, UNCACHED_UNPACK_SEQUENCE, NODE_UNPACK_SEQUENCE);
        Object collection = stackFrame.getObject(stackTop);
        return unpackNode.execute(virtualFrame, stackTop - 1, stackFrame, collection, count);
    }

    private int bytecodeUnpackEx(VirtualFrame virtualFrame, Frame stackFrame, int stackTop, int bci, Node[] localNodes, int countBefore, int countAfter) {
        UnpackExNode unpackNode = insertChildNode(localNodes, bci, UNCACHED_UNPACK_EX, NODE_UNPACK_EX);
        Object collection = stackFrame.getObject(stackTop);
        return unpackNode.execute(virtualFrame, stackTop - 1, stackFrame, collection, countBefore, countAfter);
    }

    @ExplodeLoop
    private long findHandler(int bci) {
        CompilerAsserts.partialEvaluationConstant(bci);

        int targetBCI = -1;
        int targetStackTop = -1;
        for (int i = 0; i < exceptionHandlerRanges.length; i += 4) {
            // The ranges are ordered by their start and non-overlapping
            if (bci < exceptionHandlerRanges[i]) {
                break;
            } else if (bci < exceptionHandlerRanges[i + 1]) {
                // bci is inside this try-block range. get the target stack size
                targetBCI = exceptionHandlerRanges[i + 2] & 0xffff;
                targetStackTop = exceptionHandlerRanges[i + 3] & 0xffff;
                break;
            }
        }
        if (targetBCI == -1) {
            return -1;
        } else {
            return encodeBCI(targetBCI) | encodeStackTop(targetStackTop);
        }
    }

    @ExplodeLoop
    private static int unwindBlock(Frame stackFrame, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        for (int i = stackTop; i > stackTopBeforeBlock; i--) {
            stackFrame.setObject(i, null);
        }
        return stackTopBeforeBlock;
    }

    public PCell readClassCell(VirtualFrame virtualFrame) {
        Frame localFrame = virtualFrame;
        if (co.isGeneratorOrCoroutine()) {
            localFrame = PArguments.getGeneratorFrame(virtualFrame);
        }
        if (classcellIndex < 0) {
            return null;
        }
        return (PCell) localFrame.getObject(classcellIndex);
    }

    public Object readSelf(VirtualFrame virtualFrame) {
        Frame localFrame = virtualFrame;
        if (co.isGeneratorOrCoroutine()) {
            localFrame = PArguments.getGeneratorFrame(virtualFrame);
        }
        if (selfIndex < 0) {
            return null;
        } else if (selfIndex == 0) {
            return localFrame.getObject(0);
        } else {
            PCell selfCell = (PCell) localFrame.getObject(selfIndex);
            return selfCell.getRef();
        }
    }

    public int getStartOffset() {
        return co.startOffset;
    }

    @TruffleBoundary
    public int bciToLine(int bci) {
        if (source != null && source.hasCharacters() && bci >= 0) {
            /*
             * TODO We only store source offsets, which makes it impossible to reconstruct linenos
             * without the text source. We should store lines and columns separately like CPython.
             */
            return source.createSection(co.bciToSrcOffset(bci), 0).getStartLine();
        }
        return -1;
    }

    @TruffleBoundary
    public int getFirstLineno() {
        if (source != null && source.hasCharacters()) {
            // TODO the same problem as bciToLine
            return source.createSection(co.startOffset, 0).getStartLine();
        }
        return -1;
    }

    @Override
    public SourceSection getSourceSection() {
        if (sourceSection != null) {
            return sourceSection;
        } else if (source == null) {
            return null;
        } else if (!source.hasCharacters()) {
            /*
             * TODO We could still expose the disassembled bytecode for a debugger to have something
             * to step through.
             */
            return source.createUnavailableSection();
        } else {
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int bci = 0; bci < co.code.length; bci++) {
                int offset = co.bciToSrcOffset(bci);
                min = Math.min(min, offset);
                max = Math.max(max, offset);
            }
            sourceSection = source.createSection(min, max - min);
            return sourceSection;
        }
    }

    @Override
    protected byte[] extractCode() {
        /*
         * CPython exposes individual items of code objects, like constants, as different members of
         * the code object and the co_code attribute contains just the bytecode. It would be better
         * if we did the same, however we currently serialize everything into just co_code and
         * ignore the rest. The reasons are:
         *
         * 1) TruffleLanguage.parsePublic does source level caching but it only accepts bytes or
         * Strings. We could cache ourselves instead, but we have to come up with a cache key. It
         * would be impractical to compute a cache key from all the deserialized constants, but we
         * could just generate a large random number at compile time to serve as a key.
         *
         * 2) The arguments of code object constructor would be different. Some libraries like
         * cloudpickle (used by pyspark) still rely on particular signature, even though CPython has
         * changed theirs several times. We would have to match CPython's signature. It's doable,
         * but it would certainly be more practical to update to 3.11 first to have an attribute for
         * exception ranges.
         *
         * 3) While the AST interpreter is still in use, we have to share the code in CodeBuiltins,
         * so it's much simpler to do it in a way that is close to what the AST interpreter is
         * doing.
         *
         * TODO We should revisit this when the AST interpreter is removed.
         */
        return MarshalModuleBuiltins.serializeCodeUnit(co);
    }
}
