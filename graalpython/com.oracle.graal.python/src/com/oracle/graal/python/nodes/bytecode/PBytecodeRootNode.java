/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZeroDivisionError;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MAPPING;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.SEQUENCE;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.FormatNodeFactory.FormatNodeGen;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNode;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNodeGen;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageFactory;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.DictNodesFactory;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.exception.ChainExceptionsNode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.set.SetNodesFactory;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CreateSliceNode;
import com.oracle.graal.python.builtins.objects.slice.SliceNodesFactory.CreateSliceNodeGen;
import com.oracle.graal.python.compiler.BinaryOpsConstants;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.FormatOptions;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.compiler.OpCodesConstants;
import com.oracle.graal.python.compiler.QuickeningTypes;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.compiler.UnaryOpsConstants;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectAsciiNodeGen;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectDelItemNodeGen;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetAttrNodeGen;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetItemNodeGen;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetIterNodeGen;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectGetMethodNodeGen;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNodeGen;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNodeGen;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttrNodeGen;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSetItemNodeGen;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNodeGen;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNodeGen;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodesFactory;
import com.oracle.graal.python.nodes.bytecode.SequenceFromStackNode.ListFromStackNode;
import com.oracle.graal.python.nodes.bytecode.SequenceFromStackNode.TupleFromStackNode;
import com.oracle.graal.python.nodes.bytecode.SequenceFromStackNodeFactory.ListFromStackNodeGen;
import com.oracle.graal.python.nodes.bytecode.SequenceFromStackNodeFactory.TupleFromStackNodeGen;
import com.oracle.graal.python.nodes.bytecode.instrumentation.InstrumentationRoot;
import com.oracle.graal.python.nodes.bytecode.instrumentation.InstrumentationSupport;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.CallNodeGen;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNodeGen;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNodeGen;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNodeGen;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNodeGen;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNodeGen;
import com.oracle.graal.python.nodes.exception.ExceptMatchNode;
import com.oracle.graal.python.nodes.exception.ExceptMatchNodeGen;
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
import com.oracle.graal.python.nodes.frame.DeleteGlobalNodeGen;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadFromLocalsNode;
import com.oracle.graal.python.nodes.frame.ReadFromLocalsNodeGen;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNodeGen;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNameNodeGen;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNodeGen;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.frame.WriteNameNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNodeGen;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Root node with main bytecode interpreter loop.
 */
@SuppressWarnings("static-method")
public final class PBytecodeRootNode extends PRootNode implements BytecodeOSRNode {

    private static final NodeSupplier<RaiseNode> NODE_RAISENODE = RaiseNode::create;
    private static final NodeSupplier<PyObjectDelItem> NODE_OBJECT_DEL_ITEM = PyObjectDelItem::create;
    private static final PyObjectDelItem UNCACHED_OBJECT_DEL_ITEM = PyObjectDelItem.getUncached();

    private static final NodeSupplier<SetItemNode> NODE_SET_ITEM = HashingCollectionNodes.SetItemNode::create;
    private static final SetItemNode UNCACHED_SET_ITEM = HashingCollectionNodes.SetItemNode.getUncached();
    private static final NodeSupplier<CastToJavaIntExactNode> NODE_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode::create;
    private static final CastToJavaIntExactNode UNCACHED_CAST_TO_JAVA_INT_EXACT = CastToJavaIntExactNode.getUncached();
    private static final ImportNode UNCACHED_IMPORT = ImportNode.getUncached();
    private static final ReadNameNode UNCACHED_READ_NAME = ReadNameNodeGen.getUncached();
    private static final WriteNameNode UNCACHED_WRITE_NAME = WriteNameNodeGen.getUncached();
    private static final NodeSupplier<ImportNode> NODE_IMPORT = ImportNode::create;
    private static final ImportStarNode UNCACHED_IMPORT_STAR = ImportStarNode.getUncached();
    private static final NodeSupplier<ImportStarNode> NODE_IMPORT_STAR = ImportStarNode::create;
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
    private static final ForIterONode UNCACHED_FOR_ITER_O = ForIterONode.getUncached();
    private static final NodeSupplier<ForIterONode> NODE_FOR_ITER_O = ForIterONode::create;
    private static final ForIterINode UNCACHED_FOR_ITER_I = ForIterINode.getUncached();
    private static final NodeSupplier<ForIterINode> NODE_FOR_ITER_I = ForIterINode::create;
    private static final NodeSupplier<PyObjectGetIter> NODE_OBJECT_GET_ITER = PyObjectGetIter::create;
    private static final PyObjectGetIter UNCACHED_OBJECT_GET_ITER = PyObjectGetIter.getUncached();
    private static final NodeSupplier<GetYieldFromIterNode> NODE_OBJECT_GET_YIELD_FROM_ITER = GetYieldFromIterNode::create;
    private static final GetYieldFromIterNode UNCACHED_OBJECT_GET_YIELD_FROM_ITER = GetYieldFromIterNode.getUncached();

    private static final NodeSupplier<GetAwaitableNode> NODE_OBJECT_GET_AWAITABLE = GetAwaitableNode::create;
    private static final GetAwaitableNode UNCACHED_OBJECT_GET_AWAITABLE = GetAwaitableNode.getUncached();
    private static final NodeSupplier<PyObjectSetAttr> NODE_OBJECT_SET_ATTR = PyObjectSetAttr::create;
    private static final PyObjectSetAttr UNCACHED_OBJECT_SET_ATTR = PyObjectSetAttr.getUncached();
    private static final NodeSupplier<ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS = () -> ReadGlobalOrBuiltinNode.create();
    private static final NodeSupplier<ReadGlobalOrBuiltinNode> NODE_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode::create;
    private static final NodeSupplier<ReadNameNode> NODE_READ_NAME = ReadNameNode::create;
    private static final NodeSupplier<WriteNameNode> NODE_WRITE_NAME = WriteNameNode::create;
    private static final ReadGlobalOrBuiltinNode UNCACHED_READ_GLOBAL_OR_BUILTIN = ReadGlobalOrBuiltinNode.getUncached();
    private static final NodeSupplier<PyObjectSetItem> NODE_OBJECT_SET_ITEM = PyObjectSetItem::create;
    private static final PyObjectSetItem UNCACHED_OBJECT_SET_ITEM = PyObjectSetItem.getUncached();
    private static final NodeSupplier<PyObjectIsTrueNode> NODE_OBJECT_IS_TRUE = PyObjectIsTrueNode::create;
    private static final PyObjectIsTrueNode UNCACHED_OBJECT_IS_TRUE = PyObjectIsTrueNode.getUncached();
    private static final NodeSupplier<PyObjectGetItem> NODE_GET_ITEM = PyObjectGetItem::create;
    private static final ExceptMatchNode UNCACHED_EXCEPT_MATCH = ExceptMatchNode.getUncached();
    private static final NodeSupplier<ExceptMatchNode> NODE_EXCEPT_MATCH = ExceptMatchNode::create;
    private static final SetupWithNode UNCACHED_SETUP_WITH_NODE = SetupWithNode.getUncached();
    private static final NodeSupplier<SetupWithNode> NODE_SETUP_WITH = SetupWithNode::create;
    private static final ExitWithNode UNCACHED_EXIT_WITH_NODE = ExitWithNode.getUncached();
    private static final NodeSupplier<ExitWithNode> NODE_EXIT_WITH = ExitWithNode::create;
    private static final SetupAwithNode UNCACHED_SETUP_AWITH_NODE = SetupAwithNode.getUncached();
    private static final NodeSupplier<SetupAwithNode> NODE_SETUP_AWITH = SetupAwithNode::create;
    private static final GetAExitCoroNode UNCACHED_GET_AEXIT_CORO_NODE = GetAExitCoroNode.getUncached();
    private static final NodeSupplier<GetAExitCoroNode> NODE_GET_AEXIT_CORO = GetAExitCoroNode::create;
    private static final ExitAWithNode UNCACHED_EXIT_AWITH_NODE = ExitAWithNode.getUncached();
    private static final GetAIterNode UNCACHED_GET_AITER = GetAIterNode.getUncached();
    private static final NodeSupplier<GetAIterNode> NODE_GET_AITER = GetAIterNode::create;
    private static final GetANextNode UNCACHED_GET_ANEXT = GetANextNode.getUncached();
    private static final NodeSupplier<GetANextNode> NODE_GET_ANEXT = GetANextNode::create;
    private static final EndAsyncForNode UNCACHED_END_ASYNC_FOR = EndAsyncForNode.getUncached();
    private static final NodeSupplier<EndAsyncForNode> NODE_END_ASYNC_FOR = EndAsyncForNode::create;
    private static final NodeSupplier<ExitAWithNode> NODE_EXIT_AWITH = ExitAWithNode::create;
    private static final ImportFromNode UNCACHED_IMPORT_FROM = ImportFromNode.getUncached();
    private static final NodeSupplier<ImportFromNode> NODE_IMPORT_FROM = ImportFromNode::create;
    private static final ExecutePositionalStarargsNode UNCACHED_EXECUTE_STARARGS = ExecutePositionalStarargsNode.getUncached();
    private static final NodeSupplier<ExecutePositionalStarargsNode> NODE_EXECUTE_STARARGS = ExecutePositionalStarargsNode::create;
    private static final KeywordsNode UNCACHED_KEYWORDS = KeywordsNode.getUncached();
    private static final NodeSupplier<KeywordsNode> NODE_KEYWORDS = KeywordsNode::create;
    private static final CreateSliceNode UNCACHED_CREATE_SLICE = CreateSliceNode.getUncached();
    private static final NodeSupplier<CreateSliceNode> NODE_CREATE_SLICE = CreateSliceNode::create;
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
    private static final PyObjectSizeNode UNCACHED_SIZE = PyObjectSizeNode.getUncached();
    private static final NodeSupplier<PyObjectSizeNode> NODE_SIZE = PyObjectSizeNode::create;
    private static final NodeSupplier<GetTPFlagsNode> NODE_TP_FLAGS = GetTPFlagsNode::create;
    private static final GetTPFlagsNode UNCACHED_TP_FLAGS = GetTPFlagsNode.getUncached();
    private static final DeleteGlobalNode UNCACHED_DELETE_GLOBAL = DeleteGlobalNodeGen.getUncached();
    private static final NodeSupplier<MatchKeysNode> NODE_MATCH_KEYS = MatchKeysNode::create;
    private static final NodeSupplier<CopyDictWithoutKeysNode> NODE_COPY_DICT_WITHOUT_KEYS = CopyDictWithoutKeysNode::create;
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
    private static final NodeSupplier<FormatNode> NODE_FORMAT = FormatNode::create;
    private static final NodeSupplier<SendNode> NODE_SEND = SendNode::create;
    private static final NodeSupplier<ThrowNode> NODE_THROW = ThrowNode::create;
    private static final WriteGlobalNode UNCACHED_WRITE_GLOBAL = WriteGlobalNode.getUncached();
    private static final NodeSupplier<WriteGlobalNode> NODE_WRITE_GLOBAL = WriteGlobalNode::create;
    private static final NodeSupplier<DeleteGlobalNode> NODE_DELETE_GLOBAL = DeleteGlobalNode::create;
    private static final PrintExprNode UNCACHED_PRINT_EXPR = PrintExprNode.getUncached();
    private static final NodeSupplier<PrintExprNode> NODE_PRINT_EXPR = PrintExprNode::create;
    private static final ReadFromLocalsNode UNCACHED_READ_FROM_LOCALS = ReadFromLocalsNode.getUncached();
    private static final NodeSupplier<ReadFromLocalsNode> NODE_READ_FROM_LOCALS = ReadFromLocalsNode::create;
    private static final SetupAnnotationsNode UNCACHED_SETUP_ANNOTATIONS = SetupAnnotationsNode.getUncached();
    private static final NodeSupplier<SetupAnnotationsNode> NODE_SETUP_ANNOTATIONS = SetupAnnotationsNode::create;
    private static final GetSendValueNode UNCACHED_GET_SEND_VALUE = GetSendValueNode.getUncached();
    private static final NodeSupplier<GetSendValueNode> NODE_GET_SEND_VALUE = GetSendValueNode::create;
    private static final NodeSupplier<BinarySubscrSeq.ONode> NODE_BINARY_SUBSCR_SEQ_O = BinarySubscrSeq.ONode::create;
    private static final NodeSupplier<BinarySubscrSeq.INode> NODE_BINARY_SUBSCR_SEQ_I = BinarySubscrSeq.INode::create;
    private static final NodeSupplier<BinarySubscrSeq.DNode> NODE_BINARY_SUBSCR_SEQ_D = BinarySubscrSeq.DNode::create;
    private static final NodeSupplier<StoreSubscrSeq.ONode> NODE_STORE_SUBSCR_SEQ_O = StoreSubscrSeq.ONode::create;
    private static final NodeSupplier<StoreSubscrSeq.INode> NODE_STORE_SUBSCR_SEQ_I = StoreSubscrSeq.INode::create;
    private static final NodeSupplier<StoreSubscrSeq.DNode> NODE_STORE_SUBSCR_SEQ_D = StoreSubscrSeq.DNode::create;

    private static final NodeSupplier<IntBuiltins.AddNode> NODE_INT_ADD = IntBuiltins.AddNode::create;
    private static final NodeSupplier<IntBuiltins.SubNode> NODE_INT_SUB = IntBuiltins.SubNode::create;
    private static final NodeSupplier<IntBuiltins.MulNode> NODE_INT_MUL = IntBuiltins.MulNode::create;
    private static final NodeSupplier<IntBuiltins.FloorDivNode> NODE_INT_FLOORDIV = IntBuiltins.FloorDivNode::create;
    private static final NodeSupplier<IntBuiltins.TrueDivNode> NODE_INT_TRUEDIV = IntBuiltins.TrueDivNode::create;
    private static final NodeSupplier<IntBuiltins.ModNode> NODE_INT_MOD = IntBuiltins.ModNode::create;
    private static final NodeSupplier<IntBuiltins.LShiftNode> NODE_INT_LSHIFT = IntBuiltins.LShiftNode::create;
    private static final NodeSupplier<IntBuiltins.RShiftNode> NODE_INT_RSHIFT = IntBuiltins.RShiftNode::create;
    private static final NodeSupplier<IntBuiltins.NegNode> NODE_INT_NEG = IntBuiltins.NegNode::create;
    private static final NodeSupplier<IntBuiltins.PowNode> NODE_INT_POW = IntBuiltins.PowNode::create;
    private static final NodeSupplier<FloatBuiltins.PowNode> NODE_FLOAT_POW = FloatBuiltins.PowNode::create;
    private static final NodeSupplier<HashingStorageFromListSequenceStorageNode> NODE_HASHING_STORAGE_FROM_SEQUENCE = HashingStorageFromListSequenceStorageNode::create;
    private static final NodeSupplier<MatchClassNode> NODE_MATCH_CLASS = MatchClassNode::create;

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

    private static final byte TRACE_FUN = 0b01;
    private static final byte PROFILE_FUN = 0b10;
    private static final byte TRACE_AND_PROFILE_FUN = TRACE_FUN | PROFILE_FUN;

    private final Signature signature;
    private final TruffleString name;
    private final boolean internal;
    private boolean pythonInternal;

    final int celloffset;
    final int freeoffset;
    final int stackoffset;
    final int bcioffset;
    final int selfIndex;
    final int classcellIndex;

    private final BytecodeCodeUnit co;
    private final Source source;
    private SourceSection sourceSection;
    // For deferred deprecation warnings
    private final RaisePythonExceptionErrorCallback parserErrorCallback;

    @CompilationFinal(dimensions = 1) final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final long[] longConsts;
    @CompilationFinal(dimensions = 1) private final TruffleString[] names;
    @CompilationFinal(dimensions = 1) private final TruffleString[] varnames;
    @CompilationFinal(dimensions = 1) private final TruffleString[] freevars;
    @CompilationFinal(dimensions = 1) private final TruffleString[] cellvars;
    @CompilationFinal(dimensions = 1) private final int[] cell2arg;
    @CompilationFinal(dimensions = 1) protected final Assumption[] cellEffectivelyFinalAssumptions;

    @CompilationFinal(dimensions = 1) private final int[] exceptionHandlerRanges;

    /**
     * Whether instruction at given bci can put a primitive value on stack. The number is a bitwise
     * or of possible types defined by {@link QuickeningTypes}.
     */
    private final byte[] outputCanQuicken;
    /**
     * Whether store instructions to this variable should attempt to unbox primitives. The number
     * determines the type like above.
     */
    private final byte[] variableShouldUnbox;
    /**
     * Which instruction bci's have to be generalized when generalizing inputs of instruction at
     * given bci.
     */
    private final int[][] generalizeInputsMap;
    /**
     * Which store instruction bci's have to be generalized when generalizing variable with given
     * index.
     */
    private final int[][] generalizeVarsMap;

    /*
     * Whether this variable should be unboxed in the interpreter. We unbox all variables in
     * compiled code, but in the interpreter we do an optimization that we only unbox variables that
     * would actually get used without immediately being boxed again. This optimization doesn't
     * apply to generators where all variables get unboxed both in the interpreter and compiled
     * code.
     */
    private static final byte UNBOXED_IN_INTERPRETER = (byte) (1 << 7);
    /**
     * Current primitive types of variables. The value is one of {@link QuickeningTypes} potentially
     * ORed with {@link #UNBOXED_IN_INTERPRETER}. Used by argument copying and store instructions.
     */
    @CompilationFinal(dimensions = 1) private byte[] variableTypes;

    /*
     * When instrumentation is in use, InstrumentationSupport#bciToHelper node is used instead of
     * this array. Use getChildNodes() to get the right array.
     */
    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    // TODO: make some of those lazy?
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private MaterializeFrameNode traceMaterializeFrameNode = null;
    @Child private ChainExceptionsNode chainExceptionsNode;

    @CompilationFinal private Object osrMetadata;

    @CompilationFinal private boolean usingCachedNodes;
    @CompilationFinal(dimensions = 1) private int[] conditionProfiles;

    @Child private InstrumentationRoot instrumentationRoot = InstrumentationRoot.create();

    private static FrameDescriptor makeFrameDescriptor(BytecodeCodeUnit co, FrameInfo info) {
        int capacity = co.varnames.length + co.cellvars.length + co.freevars.length + co.stacksize + 1;
        FrameDescriptor.Builder newBuilder = FrameDescriptor.newBuilder(capacity);
        newBuilder.info(info);
        // locals
        for (int i = 0; i < co.varnames.length; i++) {
            TruffleString varname = co.varnames[i];
            if (co.arg2cell != null && i < co.arg2cell.length && co.arg2cell[i] >= 0) {
                /*
                 * If an argument is a cell, its slot gets superseded by the cell's slot below. We
                 * need to hide it from introspection.
                 */
                varname = null;
            }
            newBuilder.addSlot(FrameSlotKind.Illegal, varname, null);
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
        // TODO we should use a static slot when GR-40849 and GR-40742 are fixed
        newBuilder.addSlot(FrameSlotKind.Int, null, null);
        if (co.isGeneratorOrCoroutine()) {
            // stackTop saved when pausing a generator
            newBuilder.addSlot(FrameSlotKind.Int, null, null);
            // return value of a generator
            newBuilder.addSlot(FrameSlotKind.Illegal, null, null);
        }
        return newBuilder.build();
    }

    @TruffleBoundary
    public static PBytecodeRootNode create(PythonLanguage language, BytecodeCodeUnit co, Source source) {
        return create(language, co, source, null);
    }

    @TruffleBoundary
    public static PBytecodeRootNode create(PythonLanguage language, BytecodeCodeUnit co, Source source, RaisePythonExceptionErrorCallback parserErrorCallback) {
        BytecodeFrameInfo frameInfo = new BytecodeFrameInfo();
        FrameDescriptor fd = makeFrameDescriptor(co, frameInfo);
        PBytecodeRootNode rootNode = new PBytecodeRootNode(language, fd, co.computeSignature(), co, source, parserErrorCallback);
        PythonContext context = PythonContext.get(rootNode);
        if (context != null && context.getOption(PythonOptions.EagerlyMaterializeInstrumentationNodes)) {
            rootNode.adoptChildren();
            rootNode.instrumentationRoot.materializeInstrumentableNodes(Collections.singleton(StandardTags.StatementTag.class));
        }
        frameInfo.setRootNode(rootNode);
        return rootNode;
    }

    @TruffleBoundary
    private PBytecodeRootNode(PythonLanguage language, FrameDescriptor fd, Signature sign, BytecodeCodeUnit co, Source source, RaisePythonExceptionErrorCallback parserErrorCallback) {
        super(language, fd);
        assert source != null;
        this.celloffset = co.varnames.length;
        this.freeoffset = celloffset + co.cellvars.length;
        this.stackoffset = freeoffset + co.freevars.length;
        this.bcioffset = stackoffset + co.stacksize;
        this.source = source;
        this.internal = source.isInternal();
        this.parserErrorCallback = parserErrorCallback;
        this.signature = sign;
        this.bytecode = PythonUtils.arrayCopyOf(co.code, co.code.length);
        this.adoptedNodes = new Node[co.code.length];
        this.conditionProfiles = new int[co.conditionProfileCount];
        this.outputCanQuicken = co.outputCanQuicken;
        this.variableShouldUnbox = co.variableShouldUnbox;
        this.generalizeInputsMap = co.generalizeInputsMap;
        this.generalizeVarsMap = co.generalizeVarsMap;
        this.consts = co.constants;
        this.longConsts = co.primitiveConstants;
        this.names = co.names;
        this.varnames = co.varnames;
        this.freevars = co.freevars;
        this.cellvars = co.cellvars;
        this.cell2arg = co.cell2arg;
        this.name = co.name;
        this.exceptionHandlerRanges = co.exceptionHandlerRanges;
        this.co = co;
        assert co.stacksize < Math.pow(2, 12) : "stacksize cannot be larger than 12-bit range";
        cellEffectivelyFinalAssumptions = new Assumption[cellvars.length];
        for (int i = 0; i < cellvars.length; i++) {
            cellEffectivelyFinalAssumptions[i] = Truffle.getRuntime().createAssumption("cell is effectively final");
        }
        this.classcellIndex = co.getClassCellIndex();
        this.selfIndex = co.getSelfIndex();
        if (language.getEngineOption(PythonOptions.ForceInitializeSourceSections)) {
            getSourceSection();
        }
    }

    @Override
    protected int computeSize() {
        return bytecode.length / 2;
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

    public BytecodeCodeUnit getCodeUnit() {
        return co;
    }

    public Source getSource() {
        return source;
    }

    public byte[] getBytecode() {
        return bytecode;
    }

    private Node[] getChildNodes() {
        InstrumentationSupport instrumentation = instrumentationRoot.getInstrumentation();
        return instrumentation == null ? adoptedNodes : instrumentation.bciToHelperNode;
    }

    @FunctionalInterface
    private interface NodeSupplier<T> {

        T get();
    }

    @FunctionalInterface
    private interface NodeFunction<A, T> {
        T apply(A argument);
    }

    @FunctionalInterface
    private interface IntNodeFunction<T extends Node> {
        T apply(int argument);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, Class<? extends T> cachedClass, NodeFunction<A, T> nodeSupplier, A argument) {
        Node node = nodes[nodeIndex];
        if (node != null && node.getClass() == cachedClass) {
            return CompilerDirectives.castExact(node, cachedClass);
        }
        return CompilerDirectives.castExact(doInsertChildNode(nodes, nodeIndex, nodeSupplier, argument), cachedClass);
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T doInsertChildNode(Node[] nodes, int nodeIndex, NodeFunction<A, T> nodeSupplier, A argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        Lock lock = getLock();
        lock.lock();
        try {
            T newNode = nodeSupplier.apply(argument);
            doInsertChildNode(nodes, nodeIndex, newNode);
            return newNode;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private <A, T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, T uncached, Class<? extends T> cachedClass, NodeFunction<A, T> nodeSupplier, A argument, boolean useCachedNodes) {
        if (!useCachedNodes) {
            return uncached;
        }
        Node node = nodes[nodeIndex];
        if (node != null && node.getClass() == cachedClass) {
            return CompilerDirectives.castExact(node, cachedClass);
        }
        return CompilerDirectives.castExact(doInsertChildNode(nodes, nodeIndex, nodeSupplier, argument), cachedClass);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node, U> T insertChildNodeInt(Node[] nodes, int nodeIndex, Class<U> expectedClass, IntNodeFunction<T> nodeSupplier, int argument) {
        Node node = nodes[nodeIndex];
        if (expectedClass.isInstance(node)) {
            return (T) node;
        }
        return doInsertChildNodeInt(nodes, nodeIndex, nodeSupplier, argument);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNodeInt(Node[] nodes, int nodeIndex, IntNodeFunction<T> nodeSupplier, int argument) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        Lock lock = getLock();
        lock.lock();
        try {
            T newNode = nodeSupplier.apply(argument);
            doInsertChildNode(nodes, nodeIndex, newNode);
            return newNode;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node, U extends T> U insertChildNode(Node[] nodes, int nodeIndex, Class<U> cachedClass, NodeSupplier<T> nodeSupplier) {
        Node node = nodes[nodeIndex];
        if (node != null && node.getClass() == cachedClass) {
            return CompilerDirectives.castExact(node, cachedClass);
        }
        return CompilerDirectives.castExact(doInsertChildNode(nodes, nodeIndex, nodeSupplier), cachedClass);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T doInsertChildNode(Node[] nodes, int nodeIndex, NodeSupplier<T> nodeSupplier) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        Lock lock = getLock();
        lock.lock();
        try {
            T newNode = nodeSupplier.get();
            doInsertChildNode(nodes, nodeIndex, newNode);
            return newNode;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Node[] nodes, int nodeIndex, T uncached, Class<? extends T> cachedClass, NodeSupplier<T> nodeSupplier, boolean useCachedNodes) {
        if (!useCachedNodes) {
            return uncached;
        }
        Node node = nodes[nodeIndex];
        if (node != null && node.getClass() == cachedClass) {
            return CompilerDirectives.castExact(node, cachedClass);
        }
        return CompilerDirectives.castExact(doInsertChildNode(nodes, nodeIndex, nodeSupplier), cachedClass);
    }

    private void doInsertChildNode(Node[] nodes, int nodeIndex, Node newNode) {
        if (nodes == adoptedNodes) {
            nodes[nodeIndex] = insert(newNode);
        } else {
            assert nodes == instrumentationRoot.getInstrumentation().bciToHelperNode;
            instrumentationRoot.getInstrumentation().insertHelperNode(newNode, nodeIndex);
        }
    }

    private static final int CONDITION_PROFILE_MAX_VALUE = 0x3fffffff;

    // Inlined from ConditionProfile.Counting#profile
    private boolean profileCondition(boolean value, byte[] localBC, int bci, boolean useCachedNodes) {
        if (!useCachedNodes) {
            return value;
        }
        int index = Byte.toUnsignedInt(localBC[bci + 2]) | Byte.toUnsignedInt(localBC[bci + 3]) << 8;
        int t = conditionProfiles[index];
        int f = conditionProfiles[index + 1];
        boolean val = value;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < CONDITION_PROFILE_MAX_VALUE) {
                    conditionProfiles[index] = t + 1;
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < CONDITION_PROFILE_MAX_VALUE) {
                    conditionProfiles[index + 1] = f + 1;
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
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

    @ExplodeLoop
    private void copyArgs(Object[] args, Frame localFrame) {
        boolean inCompiledCode = CompilerDirectives.inCompiledCode();
        if (variableTypes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            copyArgsFirstTime(args, localFrame);
            return;
        }
        int argCount = co.getRegularArgCount();
        for (int i = 0; i < argCount; i++) {
            Object arg = args[i + PArguments.USER_ARGUMENTS_OFFSET];
            byte type = variableTypes[i];
            if ((type & QuickeningTypes.OBJECT) != 0) {
                localFrame.setObject(i, arg);
                continue;
            } else if ((type & QuickeningTypes.INT) != 0) {
                if (arg instanceof Integer) {
                    if (inCompiledCode || (type & UNBOXED_IN_INTERPRETER) != 0) {
                        localFrame.setInt(i, (int) arg);
                    } else {
                        localFrame.setObject(i, arg);
                    }
                    continue;
                }
            } else if ((type & QuickeningTypes.LONG) != 0) {
                if (arg instanceof Long) {
                    if (inCompiledCode || (type & UNBOXED_IN_INTERPRETER) != 0) {
                        localFrame.setLong(i, (long) arg);
                    } else {
                        localFrame.setObject(i, arg);
                    }
                    continue;
                }
            } else if ((type & QuickeningTypes.DOUBLE) != 0) {
                if (arg instanceof Double) {
                    if (inCompiledCode || (type & UNBOXED_IN_INTERPRETER) != 0) {
                        localFrame.setDouble(i, (double) arg);
                    } else {
                        localFrame.setObject(i, arg);
                    }
                    continue;
                }
            } else if ((type & QuickeningTypes.BOOLEAN) != 0) {
                if (arg instanceof Boolean) {
                    if (inCompiledCode || (type & UNBOXED_IN_INTERPRETER) != 0) {
                        localFrame.setBoolean(i, (boolean) arg);
                    } else {
                        localFrame.setObject(i, arg);
                    }
                    continue;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            generalizeVariableStores(i);
            variableTypes[i] = QuickeningTypes.OBJECT;
            localFrame.setObject(i, arg);
        }
        if (inCompiledCode != CompilerDirectives.inCompiledCode()) {
            /*
             * If we deopted we might have unboxed some locals that are supposed to be boxed in the
             * interpreter, so we have to undo that because the bytecode loop won't expect them.
             */
            for (int i = 0; i < argCount; i++) {
                if (variableTypes[i] != 0 && (variableTypes[i] & UNBOXED_IN_INTERPRETER) == 0 && !localFrame.isObject(i)) {
                    localFrame.setObject(i, localFrame.getValue(i));
                }
            }
        }
    }

    private void copyArgsFirstTime(Object[] args, Frame localFrame) {
        CompilerAsserts.neverPartOfCompilation();
        variableTypes = new byte[varnames.length];
        int argCount = co.getRegularArgCount();
        for (int i = 0; i < argCount; i++) {
            Object arg = args[i + PArguments.USER_ARGUMENTS_OFFSET];
            if (arg instanceof Integer) {
                variableTypes[i] = QuickeningTypes.INT;
                if ((variableShouldUnbox[i] & QuickeningTypes.INT) != 0) {
                    variableTypes[i] |= UNBOXED_IN_INTERPRETER;
                    localFrame.setInt(i, (int) arg);
                    continue;
                }
            } else if (arg instanceof Long) {
                variableTypes[i] = QuickeningTypes.LONG;
                if ((variableShouldUnbox[i] & QuickeningTypes.LONG) != 0) {
                    variableTypes[i] |= UNBOXED_IN_INTERPRETER;
                    localFrame.setLong(i, (long) arg);
                    continue;
                }
            } else if (arg instanceof Double) {
                variableTypes[i] = QuickeningTypes.DOUBLE;
                if ((variableShouldUnbox[i] & QuickeningTypes.DOUBLE) != 0) {
                    variableTypes[i] |= UNBOXED_IN_INTERPRETER;
                    localFrame.setDouble(i, (double) arg);
                    continue;
                }
            } else if (arg instanceof Boolean) {
                variableTypes[i] = QuickeningTypes.BOOLEAN;
                if ((variableShouldUnbox[i] & QuickeningTypes.BOOLEAN) != 0) {
                    variableTypes[i] |= UNBOXED_IN_INTERPRETER;
                    localFrame.setBoolean(i, (boolean) arg);
                    continue;
                }
            } else {
                variableTypes[i] = QuickeningTypes.OBJECT;
            }
            localFrame.setObject(i, arg);
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
        copyArgsAndCells(generatorFrame, arguments);
    }

    private void copyArgsAndCells(Frame localFrame, Object[] arguments) {
        copyArgs(arguments, localFrame);
        int varIdx = co.getRegularArgCount();
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
            return executeFromBci(virtualFrame, virtualFrame, this, 0, getInitialStackTop());
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
        return executeFromBci(osrFrame, osrFrame, this, target, interpreterState.stackTop);
    }

    private static final class InterpreterContinuation {
        public final int bci;
        public final int stackTop;

        private InterpreterContinuation(int bci, int stackTop) {
            this.bci = bci;
            this.stackTop = stackTop;
        }
    }

    @ValueType
    private static final class MutableLoopData {
        public int getPastBci() {
            return getTraceData().pastBci;
        }

        public int setPastBci(int pastBci) {
            return this.getTraceData().pastBci = pastBci;
        }

        public int getPastLine() {
            return getTraceData().pastLine;
        }

        public int setPastLine(int pastLine) {
            return this.getTraceData().pastLine = pastLine;
        }

        public int getReturnLine() {
            return getTraceData().returnLine;
        }

        public int setReturnLine(int returnLine) {
            return this.getTraceData().returnLine = returnLine;
        }

        public boolean isReturnCalled() {
            return this.getTraceData().returnCalled;
        }

        public void setReturnCalled(boolean value) {
            this.getTraceData().returnCalled = value;
        }

        public boolean isExceptionNotified() {
            return this.getTraceData().exceptionNotified;
        }

        public void setExceptionNotified(boolean value) {
            this.getTraceData().exceptionNotified = value;
        }

        public PFrame getPyFrame() {
            return getTraceData().pyFrame;
        }

        public PFrame setPyFrame(PFrame pyFrame) {
            return this.getTraceData().pyFrame = pyFrame;
        }

        private InstrumentationData getTraceData() {
            if (instrumentationData == null) {
                instrumentationData = new InstrumentationData();
            }
            return instrumentationData;
        }

        public PythonContext.PythonThreadState getThreadState(Node node) {
            if (this.getTraceData().threadState == null) {
                return this.getTraceData().threadState = PythonContext.get(node).getThreadState(PythonLanguage.get(node));
            }
            return this.getTraceData().threadState;
        }

        /*
         * Data for tracing, profiling and instrumentation
         */
        private static final class InstrumentationData {
            InstrumentationData() {
                pastBci = 0;
                pastLine = returnLine = -1;
            }

            private int pastBci;
            private int pastLine;
            private int returnLine;
            private PFrame pyFrame = null;
            private boolean exceptionNotified;
            private boolean returnCalled;

            private PythonContext.PythonThreadState threadState = null;
        }

        private InstrumentationData instrumentationData = null;

        int loopCount;
        /*
         * This separate tracking of local exception is necessary to make exception state saving
         * work in generators. On one hand we need to retain the exception that was caught in the
         * generator, on the other hand we don't want to retain the exception state that was passed
         * from the outer frame because that changes with every resume.
         */
        boolean fetchedException;
        PException outerException;
        PException localException;
    }

    Object executeFromBci(VirtualFrame virtualFrame, Frame localFrame, BytecodeOSRNode osrNode, int initialBci, int initialStackTop) {
        /*
         * A lot of python code is executed just a single time, such as top level module code. We
         * want to save some time and memory by trying to first use uncached nodes. We use two
         * separate entry points so that they get each get compiled with monomorphic calls to either
         * cached or uncached nodes.
         */
        if (usingCachedNodes) {
            return executeCached(virtualFrame, localFrame, osrNode, initialBci, initialStackTop, false);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            usingCachedNodes = true;
            Object result = executeUncached(virtualFrame, localFrame, osrNode, initialBci, initialStackTop);
            if (result instanceof InterpreterContinuation) {
                InterpreterContinuation continuation = (InterpreterContinuation) result;
                return executeCached(virtualFrame, localFrame, osrNode, continuation.bci, continuation.stackTop, true);
            }
            return result;
        }
    }

    @BytecodeInterpreterSwitch
    private Object executeCached(VirtualFrame virtualFrame, Frame localFrame, BytecodeOSRNode osrNode, int initialBci, int initialStackTop, boolean fromOSR) {
        return bytecodeLoop(virtualFrame, localFrame, osrNode, initialBci, initialStackTop, true, fromOSR);
    }

    @BytecodeInterpreterSwitch
    private Object executeUncached(VirtualFrame virtualFrame, Frame localFrame, BytecodeOSRNode osrNode, int initialBci, int initialStackTop) {
        return bytecodeLoop(virtualFrame, localFrame, osrNode, initialBci, initialStackTop, false, false);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @SuppressWarnings("fallthrough")
    @BytecodeInterpreterSwitch
    private Object bytecodeLoop(VirtualFrame virtualFrame, Frame localFrame, BytecodeOSRNode osrNode, int initialBci, int initialStackTop, boolean useCachedNodes, boolean fromOSR) {
        boolean hasUnboxedLocals = CompilerDirectives.inCompiledCode();
        Object[] arguments = virtualFrame.getArguments();
        Object globals = PArguments.getGlobals(arguments);
        Object locals = PArguments.getSpecialArgument(arguments);

        boolean isGeneratorOrCoroutine = co.isGeneratorOrCoroutine();
        if (hasUnboxedLocals && !isGeneratorOrCoroutine) {
            unboxVariables(localFrame);
        }

        final PythonLanguage language = PythonLanguage.get(this);
        final Assumption noTraceOrProfile = language.noTracingOrProfilingAssumption;
        final InstrumentationSupport instrumentation = instrumentationRoot.getInstrumentation();
        if (instrumentation != null && !fromOSR) {
            Object result = notifyEnter(virtualFrame, instrumentation, initialBci);
            if (result != null) {
                return result;
            }
        }

        /*
         * We use an object as a workaround for not being able to specify which local variables are
         * loop constants (GR-35338).
         */
        MutableLoopData mutableData = new MutableLoopData();
        int stackTop = initialStackTop;
        int bci = initialBci;

        byte[] localBC = bytecode;
        Object[] localConsts = consts;
        long[] localLongConsts = longConsts;
        TruffleString[] localNames = names;
        Node[] localNodes = getChildNodes();
        final int bciSlot = bcioffset;
        final int localCelloffset = celloffset;

        setCurrentBci(virtualFrame, bciSlot, initialBci);

        CompilerAsserts.partialEvaluationConstant(localBC);
        CompilerAsserts.partialEvaluationConstant(bci);
        CompilerAsserts.partialEvaluationConstant(stackTop);

        byte tracingOrProfilingEnabled = 0;

        // if we are simply continuing to run an OSR loop after the replacement, tracing an
        // extra CALL event would be incorrect
        if (!fromOSR) {
            tracingOrProfilingEnabled = checkTracingAndProfilingEnabled(noTraceOrProfile, mutableData);
            traceOrProfileCall(virtualFrame, initialBci, mutableData, tracingOrProfilingEnabled);
        }

        int oparg = 0;
        while (true) {
            final byte bc = localBC[bci];
            final int beginBci = bci;
            tracingOrProfilingEnabled = checkTracingAndProfilingEnabled(noTraceOrProfile, mutableData);
            if (isTracingEnabled(tracingOrProfilingEnabled)) {
                traceLine(virtualFrame, mutableData, localBC, bci);
            }

            CompilerAsserts.partialEvaluationConstant(bc);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(stackTop);

            try {
                switch (bc) {
                    case OpCodesConstants.LOAD_NONE:
                        virtualFrame.setObject(++stackTop, PNone.NONE);
                        break;
                    case OpCodesConstants.LOAD_ELLIPSIS:
                        virtualFrame.setObject(++stackTop, PEllipsis.INSTANCE);
                        break;
                    case OpCodesConstants.LOAD_TRUE_B:
                        virtualFrame.setBoolean(++stackTop, true);
                        break;
                    case OpCodesConstants.LOAD_TRUE_O:
                        virtualFrame.setObject(++stackTop, true);
                        break;
                    case OpCodesConstants.LOAD_FALSE_B:
                        virtualFrame.setBoolean(++stackTop, false);
                        break;
                    case OpCodesConstants.LOAD_FALSE_O:
                        virtualFrame.setObject(++stackTop, false);
                        break;
                    case OpCodesConstants.LOAD_BYTE_I:
                        virtualFrame.setInt(++stackTop, localBC[++bci]); // signed!
                        break;
                    case OpCodesConstants.LOAD_BYTE_O:
                        virtualFrame.setObject(++stackTop, (int) localBC[++bci]); // signed!
                        break;
                    case OpCodesConstants.LOAD_INT_I: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setInt(++stackTop, (int) localLongConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_INT_O: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, (int) localLongConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_LONG_L: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setLong(++stackTop, localLongConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_LONG_O: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, localLongConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_DOUBLE_D: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setDouble(++stackTop, Double.longBitsToDouble(localLongConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_DOUBLE_O: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, Double.longBitsToDouble(localLongConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_BIGINT: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, factory.createInt((BigInteger) localConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_STRING:
                    case OpCodesConstants.LOAD_CONST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, localConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.LOAD_BYTES: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        virtualFrame.setObject(++stackTop, factory.createBytes((byte[]) localConsts[oparg]));
                        break;
                    }
                    case OpCodesConstants.LOAD_CONST_COLLECTION: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        int typeAndKind = Byte.toUnsignedInt(localBC[++bci]);
                        Object array = localConsts[oparg];
                        bytecodeLoadConstCollection(virtualFrame, ++stackTop, array, typeAndKind);
                        break;
                    }
                    case OpCodesConstants.LOAD_COMPLEX: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        double[] num = (double[]) localConsts[oparg];
                        virtualFrame.setObject(++stackTop, factory.createComplex(num[0], num[1]));
                        break;
                    }
                    case OpCodesConstants.MAKE_KEYWORD: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        TruffleString key = (TruffleString) localConsts[oparg];
                        Object value = virtualFrame.getObject(stackTop);
                        virtualFrame.setObject(stackTop, new PKeyword(key, value));
                        break;
                    }
                    case OpCodesConstants.BUILD_SLICE: {
                        int count = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeBuildSlice(virtualFrame, stackTop, beginBci, count, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.FORMAT_VALUE: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int options = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeFormatValue(virtualFrame, stackTop, beginBci, localNodes, options, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_FROM_COLLECTION: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int type = Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeCollectionFromCollection(virtualFrame, type, stackTop, localNodes, beginBci, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_ADD_COLLECTION: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        /*
                         * The first collection must be in the target format already, the second one
                         * is a python object.
                         */
                        int type = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCollectionAddCollection(virtualFrame, type, stackTop, localNodes, beginBci, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_FROM_STACK: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int countAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int count = CollectionBits.elementCount(countAndType);
                        int type = CollectionBits.collectionKind(countAndType);
                        stackTop = bytecodeCollectionFromStack(virtualFrame, type, count, stackTop, localNodes, beginBci, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.COLLECTION_ADD_STACK: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int countAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int count = CollectionBits.elementCount(countAndType);
                        int type = CollectionBits.collectionKind(countAndType);
                        // Just combine COLLECTION_FROM_STACK and COLLECTION_ADD_COLLECTION for now
                        stackTop = bytecodeCollectionFromStack(virtualFrame, type, count, stackTop, localNodes, beginBci, useCachedNodes);
                        stackTop = bytecodeCollectionAddCollection(virtualFrame, type, stackTop, localNodes, beginBci + 1, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.ADD_TO_COLLECTION: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int depthAndType = Byte.toUnsignedInt(localBC[++bci]);
                        int depth = CollectionBits.elementCount(depthAndType);
                        int type = CollectionBits.collectionKind(depthAndType);
                        stackTop = bytecodeAddToCollection(virtualFrame, stackTop, beginBci, localNodes, depth, type, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.TUPLE_FROM_LIST: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeTupleFromList(virtualFrame, stackTop);
                        break;
                    }
                    case OpCodesConstants.FROZENSET_FROM_LIST: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeFrozensetFromList(virtualFrame, stackTop, beginBci, localNodes);
                        break;
                    }
                    case OpCodesConstants.KWARGS_DICT_MERGE: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeKwargsMerge(virtualFrame, useCachedNodes, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.UNPACK_SEQUENCE: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeUnpackSequence(virtualFrame, stackTop, beginBci, localNodes, oparg, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.UNPACK_EX: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        int countAfter = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeUnpackEx(virtualFrame, stackTop, beginBci, localNodes, oparg, countAfter, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.NOP:
                        break;
                    case OpCodesConstants.LOAD_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastAdaptive(virtualFrame, localFrame, ++stackTop, localBC, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_O: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastO(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_I: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastI(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_I_BOX: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastIBox(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_L: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastL(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_L_BOX: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastLBox(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_D: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastD(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_D_BOX: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastDBox(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_B: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastB(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_FAST_B_BOX: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeLoadFastBBox(virtualFrame, localFrame, ++stackTop, bci++, oparg, localNodes, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.LOAD_CLOSURE: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        PCell cell = (PCell) localFrame.getObject(localCelloffset + oparg);
                        virtualFrame.setObject(++stackTop, cell);
                        break;
                    }
                    case OpCodesConstants.CLOSURE_FROM_STACK: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeClosureFromStack(virtualFrame, stackTop, oparg);
                        break;
                    }
                    case OpCodesConstants.LOAD_CLASSDEREF: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadClassDeref(virtualFrame, localFrame, locals, stackTop, beginBci, localNodes, oparg, localCelloffset, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.LOAD_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadDeref(virtualFrame, localFrame, stackTop, beginBci, localNodes, oparg, localCelloffset, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreDeref(virtualFrame, localFrame, stackTop, oparg, localCelloffset);
                        break;
                    }
                    case OpCodesConstants.DELETE_DEREF: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteDeref(localFrame, beginBci, localNodes, oparg, localCelloffset, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastAdaptive(virtualFrame, localFrame, stackTop--, bci++, localBC, oparg, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_O: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastO(virtualFrame, localFrame, stackTop--, oparg);
                        bci++;
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_UNBOX_I: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastUnboxI(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_BOXED_I: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastBoxedI(virtualFrame, localFrame, stackTop--, bci++, oparg, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_I: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastI(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_UNBOX_L: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastUnboxL(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_BOXED_L: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastBoxedL(virtualFrame, localFrame, stackTop--, bci++, oparg, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_L: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastL(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_UNBOX_D: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastUnboxD(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_BOXED_D: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastBoxedD(virtualFrame, localFrame, stackTop--, bci++, oparg, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_D: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastD(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_UNBOX_B: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastUnboxB(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_BOXED_B: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastBoxedB(virtualFrame, localFrame, stackTop--, bci++, oparg, hasUnboxedLocals);
                        break;
                    }
                    case OpCodesConstants.STORE_FAST_B: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeStoreFastB(virtualFrame, localFrame, stackTop--, bci++, oparg);
                        break;
                    }
                    case OpCodesConstants.POP_TOP:
                        virtualFrame.setObject(stackTop--, null);
                        break;
                    case OpCodesConstants.ROT_TWO: {
                        Object top = virtualFrame.getObject(stackTop);
                        virtualFrame.setObject(stackTop, virtualFrame.getObject(stackTop - 1));
                        virtualFrame.setObject(stackTop - 1, top);
                        break;
                    }
                    case OpCodesConstants.ROT_THREE: {
                        Object top = virtualFrame.getObject(stackTop);
                        virtualFrame.setObject(stackTop, virtualFrame.getObject(stackTop - 1));
                        virtualFrame.setObject(stackTop - 1, virtualFrame.getObject(stackTop - 2));
                        virtualFrame.setObject(stackTop - 2, top);
                        break;
                    }
                    case OpCodesConstants.ROT_N: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytcodeRotN(virtualFrame, stackTop, oparg);
                        break;
                    }
                    case OpCodesConstants.MATCH_SEQUENCE: {
                        stackTop = bytecodeCheckTpFlags(virtualFrame, SEQUENCE, useCachedNodes, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.MATCH_MAPPING: {
                        stackTop = bytecodeCheckTpFlags(virtualFrame, MAPPING, useCachedNodes, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.MATCH_KEYS: {
                        stackTop = bytecodeMatchKeys(virtualFrame, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.COPY_DICT_WITHOUT_KEYS: {
                        bytecodeCopyDictWithoutKeys(virtualFrame, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.MATCH_CLASS: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeMatchClass(virtualFrame, stackTop, oparg, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.GET_LEN: {
                        stackTop = bytecodeGetLen(virtualFrame, useCachedNodes, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.DUP_TOP:
                        virtualFrame.setObject(stackTop + 1, virtualFrame.getObject(stackTop));
                        stackTop++;
                        break;
                    case OpCodesConstants.UNARY_OP: {
                        bytecodeUnaryOpAdaptive(virtualFrame, stackTop, bci++, localBC, localNodes);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_O_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpOO(virtualFrame, stackTop, bci++, localNodes, op, bciSlot);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_I_I: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpII(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_I_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpIO(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_D_D: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpDD(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_D_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpDO(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_B_B: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpBB(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.UNARY_OP_B_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeUnaryOpBO(virtualFrame, stackTop, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpAdaptive(virtualFrame, stackTop--, localBC, bci++, localNodes, op, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_OO_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpOOO(virtualFrame, stackTop--, bci++, localNodes, op, bciSlot);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_II_I: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpIII(virtualFrame, stackTop--, bci++, localNodes, op, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_II_B: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpIIB(virtualFrame, stackTop--, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_II_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpIIO(virtualFrame, stackTop--, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_DD_D: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpDDD(virtualFrame, stackTop--, bci++, localNodes, op, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_DD_B: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpDDB(virtualFrame, stackTop--, bci++, localNodes, op);
                        break;
                    }
                    case OpCodesConstants.BINARY_OP_DD_O: {
                        int op = Byte.toUnsignedInt(localBC[bci + 1]);
                        bytecodeBinaryOpDDO(virtualFrame, stackTop--, bci++, localNodes, op, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR: {
                        stackTop = bytecodeBinarySubscrAdaptive(virtualFrame, stackTop, bci, localNodes, bciSlot);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR_SEQ_I_I: {
                        stackTop = bytecodeBinarySubscrSeqII(virtualFrame, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR_SEQ_I_D: {
                        stackTop = bytecodeBinarySubscrSeqID(virtualFrame, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR_SEQ_I_O: {
                        stackTop = bytecodeBinarySubscrSeqIO(virtualFrame, stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.BINARY_SUBSCR_SEQ_O_O: {
                        stackTop = bytecodeBinarySubscrOO(virtualFrame, stackTop, bci, localNodes, bciSlot);
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR: {
                        stackTop = bytecodeStoreSubscrAdaptive(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes, bciSlot);
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR_OOO: {
                        stackTop = bytecodeStoreSubscrOOO(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes, bciSlot);
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR_SEQ_IOO: {
                        stackTop = bytecodeStoreSubscrSeqIOO(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR_SEQ_IIO: {
                        stackTop = bytecodeStoreSubscrSeqIIO(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_SUBSCR_SEQ_IDO: {
                        stackTop = bytecodeStoreSubscrSeqIDO(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_SUBSCR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeDeleteSubscr(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.RAISE_VARARGS: {
                        int count = Byte.toUnsignedInt(localBC[bci + 1]);
                        throw bytecodeRaiseVarargs(virtualFrame, stackTop, beginBci, count, localNodes);
                    }
                    case OpCodesConstants.RETURN_VALUE: {
                        return bytecodeReturnValue(virtualFrame, isGeneratorOrCoroutine, instrumentation, mutableData, stackTop, tracingOrProfilingEnabled, beginBci);
                    }
                    case OpCodesConstants.LOAD_BUILD_CLASS: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeLoadBuildClass(virtualFrame, useCachedNodes, globals, ++stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.LOAD_ASSERTION_ERROR: {
                        virtualFrame.setObject(++stackTop, PythonBuiltinClassType.AssertionError);
                        break;
                    }
                    case OpCodesConstants.STORE_NAME: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreName(virtualFrame, stackTop, beginBci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_NAME: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteName(virtualFrame, globals, locals, beginBci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_ATTR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreAttr(virtualFrame, stackTop, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_ATTR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeDeleteAttr(virtualFrame, stackTop, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.STORE_GLOBAL: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeStoreGlobal(virtualFrame, globals, stackTop, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_GLOBAL: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteGlobal(virtualFrame, globals, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.LOAD_NAME: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadName(virtualFrame, stackTop, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.LOAD_GLOBAL: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadGlobal(virtualFrame, globals, stackTop, beginBci, localNames[oparg], localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.DELETE_FAST: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeDeleteFast(localFrame, beginBci, localNodes, oparg, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.LOAD_ATTR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        bytecodeLoadAttr(virtualFrame, stackTop, beginBci, oparg, localNodes, localNames, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.IMPORT_NAME: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportName(virtualFrame, globals, stackTop, beginBci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.IMPORT_FROM: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportFrom(virtualFrame, stackTop, beginBci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.IMPORT_STAR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeImportStar(virtualFrame, stackTop, beginBci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.JUMP_FORWARD:
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bci += oparg;
                        oparg = 0;
                        notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                        continue;
                    case OpCodesConstants.POP_AND_JUMP_IF_FALSE: {
                        bytecodePopAndJumpIfFalse(virtualFrame, bci, stackTop);
                        continue;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_TRUE: {
                        bytecodePopAndJumpIfTrue(virtualFrame, bci, stackTop);
                        continue;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_FALSE_O: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        if (profileCondition(!bytecodePopCondition(virtualFrame, stackTop--, localNodes, bci, useCachedNodes), localBC, bci, useCachedNodes)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_TRUE_O: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        if (profileCondition(bytecodePopCondition(virtualFrame, stackTop--, localNodes, bci, useCachedNodes), localBC, bci, useCachedNodes)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_FALSE_B: {
                        if (!virtualFrame.isBoolean(stackTop)) {
                            generalizePopAndJumpIfFalseB(bci);
                            continue;
                        }
                        if (profileCondition(!virtualFrame.getBoolean(stackTop--), localBC, bci, useCachedNodes)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.POP_AND_JUMP_IF_TRUE_B: {
                        if (!virtualFrame.isBoolean(stackTop)) {
                            generalizePopAndJumpIfTrueB(bci);
                            continue;
                        }
                        if (profileCondition(virtualFrame.getBoolean(stackTop--), localBC, bci, useCachedNodes)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_IF_FALSE_OR_POP: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean cond = evaluateObjectCondition(virtualFrame, useCachedNodes, stackTop, bci, localBC, localNodes, beginBci);
                        if (cond) {
                            virtualFrame.setObject(stackTop--, null);
                            bci += 3;
                        } else {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_IF_TRUE_OR_POP: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean cond = evaluateObjectCondition(virtualFrame, useCachedNodes, stackTop, bci, localBC, localNodes, beginBci);
                        if (cond) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            virtualFrame.setObject(stackTop--, null);
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.JUMP_BACKWARD: {
                        oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                        bci -= oparg;
                        notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                        if (CompilerDirectives.hasNextTier()) {
                            mutableData.loopCount++;
                        }
                        if (CompilerDirectives.inInterpreter()) {
                            if (!useCachedNodes) {
                                return new InterpreterContinuation(bci, stackTop);
                            }
                            if (BytecodeOSRNode.pollOSRBackEdge(osrNode)) {
                                /*
                                 * Beware of race conditions when adding more things to the
                                 * interpreterState argument. It gets stored already at this point,
                                 * but the compilation runs in parallel. The compiled code may get
                                 * entered from a different invocation of this root, using the
                                 * interpreterState that was saved here. Don't put any data specific
                                 * to particular invocation in there (like python-level arguments or
                                 * variables) or it will get mixed up. To retain such state, put it
                                 * into the frame instead.
                                 */
                                Object osrResult;
                                try {
                                    osrResult = BytecodeOSRNode.tryOSR(osrNode, bci, new OSRInterpreterState(stackTop), null, virtualFrame);
                                } catch (AbstractTruffleException e) {
                                    /*
                                     * If the OSR execution throws a python exception, it means it
                                     * has already been processed by the bytecode exception handler
                                     * therein. We wrap it in order to make sure it doesn't get
                                     * processed again, which would overwrite the traceback entry
                                     * with the location of this jump instruction.
                                     */
                                    throw new OSRException(e);
                                }
                                if (osrResult != null) {
                                    if (CompilerDirectives.hasNextTier() && mutableData.loopCount > 0) {
                                        LoopNode.reportLoopCount(this, mutableData.loopCount);
                                    }
                                    return osrResult;
                                }
                            }
                        }
                        TruffleSafepoint.poll(this);
                        oparg = 0;
                        continue;
                    }
                    case OpCodesConstants.GET_ITER: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeGetIter(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.GET_YIELD_FROM_ITER: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeGetYieldFromIter(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.FOR_ITER: {
                        bytecodeForIterAdaptive(bci);
                        continue;
                    }
                    case OpCodesConstants.FOR_ITER_O: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean shouldLoop = bytecodeForIterO(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        if (shouldLoop) {
                            stackTop++;
                            bci++;
                        } else {
                            virtualFrame.setObject(stackTop--, null);
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        }
                        break;
                    }
                    case OpCodesConstants.FOR_ITER_I: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean shouldLoop = bytecodeForIterI(virtualFrame, useCachedNodes, stackTop, bci, localNodes, beginBci);
                        if (shouldLoop) {
                            stackTop++;
                            bci++;
                        } else {
                            virtualFrame.setObject(stackTop--, null);
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        }
                        break;
                    }
                    case OpCodesConstants.LOAD_METHOD: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeLoadMethod(virtualFrame, stackTop, bci, oparg, localNames, localNodes, useCachedNodes);
                        break;
                    }
                    case OpCodesConstants.CALL_METHOD: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        int argcount = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCallMethod(virtualFrame, stackTop, beginBci, argcount, localNodes, useCachedNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.CALL_METHOD_VARARGS: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeCallMethodVarargs(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeCallFunction(virtualFrame, stackTop, beginBci, oparg, localNodes, useCachedNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.CALL_COMPREHENSION: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeCallComprehension(virtualFrame, stackTop, beginBci, localNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION_VARARGS: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeCallFunctionVarargs(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.CALL_FUNCTION_KW: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeCallFunctionKw(virtualFrame, stackTop, beginBci, localNodes, useCachedNodes, mutableData, tracingOrProfilingEnabled);
                        break;
                    }
                    case OpCodesConstants.MAKE_FUNCTION: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        int flags = Byte.toUnsignedInt(localBC[++bci]);
                        stackTop = bytecodeMakeFunction(virtualFrame, globals, stackTop, localNodes, beginBci, flags, localConsts[oparg]);
                        break;
                    }
                    case OpCodesConstants.SETUP_ANNOTATIONS: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        bytecodeSetupAnnotations(virtualFrame, useCachedNodes, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.MATCH_EXC_OR_JUMP: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean match = bytecodeMatchExc(virtualFrame, useCachedNodes, stackTop--, localNodes, beginBci);
                        if (profileCondition(!match, localBC, bci, useCachedNodes)) {
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        } else {
                            bci += 3;
                        }
                        break;
                    }
                    case OpCodesConstants.UNWRAP_EXC: {
                        bytecodeUnwrapExc(virtualFrame, stackTop);
                        break;
                    }
                    case OpCodesConstants.SETUP_WITH: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeSetupWith(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.EXIT_WITH: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeExitWith(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.SETUP_AWITH: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeSetupAWith(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.GET_AEXIT_CORO: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeGetAExitCoro(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.EXIT_AWITH: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeExitAWith(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.GET_AITER: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeGetAIter(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.GET_ANEXT: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeGetANext(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.END_ASYNC_FOR: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        stackTop = bytecodeEndAsyncFor(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.ASYNCGEN_WRAP: {
                        bytecodeAsyncGenWrap(virtualFrame, useCachedNodes, stackTop, localNodes, beginBci);
                        break;
                    }
                    case OpCodesConstants.PUSH_EXC_INFO: {
                        bytecodePushExcInfo(virtualFrame, arguments, mutableData, stackTop++);
                        break;
                    }
                    case OpCodesConstants.POP_EXCEPT: {
                        mutableData.localException = popExceptionState(arguments, virtualFrame.getObject(stackTop), mutableData.outerException);
                        virtualFrame.setObject(stackTop--, null);
                        break;
                    }
                    case OpCodesConstants.END_EXC_HANDLER: {
                        mutableData.localException = popExceptionState(arguments, virtualFrame.getObject(stackTop - 1), mutableData.outerException);
                        throw bytecodeEndExcHandler(virtualFrame, stackTop);
                    }
                    case OpCodesConstants.YIELD_VALUE: {
                        return bytecodeYieldValue(virtualFrame, localFrame, initialStackTop, arguments, instrumentation, mutableData, stackTop, bci, tracingOrProfilingEnabled, beginBci);
                    }
                    case OpCodesConstants.RESUME_YIELD: {
                        bytecodeResumeYield(virtualFrame, useCachedNodes, arguments, mutableData, ++stackTop, bci, localNodes);
                        break;
                    }
                    case OpCodesConstants.SEND: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean returned = bytecodeSend(virtualFrame, stackTop, localNodes, beginBci);
                        if (!returned) {
                            bci++;
                            break;
                        } else {
                            stackTop--;
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        }
                    }
                    case OpCodesConstants.THROW: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        boolean returned = bytecodeThrow(virtualFrame, stackTop, localNodes, beginBci);
                        if (!returned) {
                            bci++;
                            break;
                        } else {
                            stackTop--;
                            oparg |= Byte.toUnsignedInt(localBC[bci + 1]);
                            bci += oparg;
                            oparg = 0;
                            notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
                            continue;
                        }
                    }
                    case OpCodesConstants.GET_AWAITABLE: {
                        setCurrentBci(virtualFrame, bciSlot, bci);
                        GetAwaitableNode getAwait = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_GET_AWAITABLE, GetAwaitableNodeGen.class, NODE_OBJECT_GET_AWAITABLE, useCachedNodes);
                        virtualFrame.setObject(stackTop, getAwait.execute(virtualFrame, virtualFrame.getObject(stackTop)));
                        break;
                    }
                    case OpCodesConstants.PRINT_EXPR: {
                        stackTop = bytecodePrintExpr(virtualFrame, useCachedNodes, stackTop, bci, localNodes, bciSlot, beginBci);
                        break;
                    }
                    case OpCodesConstants.EXTENDED_ARG: {
                        oparg |= Byte.toUnsignedInt(localBC[++bci]);
                        oparg <<= 8;
                        bci++;
                        continue;
                    }
                    default:
                        throw raiseUnknownBytecodeError(bc);
                }
                // prepare next loop
                oparg = 0;
                bci++;
                notifyStatement(virtualFrame, instrumentation, mutableData, bci, beginBci);
            } catch (PythonExitException | PythonThreadKillException | GeneratorReturnException e) {
                throw e;
            } catch (OSRException e) {
                // Exception from OSR was already handled in the OSR code
                throw e.exception;
            } catch (Throwable e) {
                try {
                    if (instrumentation != null) {
                        if (!mutableData.isExceptionNotified()) {
                            notifyException(virtualFrame, instrumentation, beginBci, e);
                        }
                        mutableData.setExceptionNotified(false);
                    }
                    if (e instanceof ThreadDeath) {
                        throw e;
                    }
                    PException pe = null;
                    boolean isInteropException = false;
                    if (e instanceof PException) {
                        pe = (PException) e;
                    } else if (e instanceof AbstractTruffleException) {
                        isInteropException = true;
                    } else {
                        pe = wrapJavaExceptionIfApplicable(e);
                        if (pe == null) {
                            throw e;
                        }
                    }

                    tracingOrProfilingEnabled = checkTracingAndProfilingEnabled(noTraceOrProfile, mutableData);
                    if (isTracingEnabled(tracingOrProfilingEnabled) && pe != null && !mutableData.getThreadState(this).isTracing()) {
                        traceException(virtualFrame, mutableData, beginBci, pe);
                    }

                    int targetIndex = findHandler(beginBci);
                    CompilerAsserts.partialEvaluationConstant(targetIndex);
                    chainPythonExceptions(virtualFrame, mutableData, pe);
                    if (targetIndex == -1) {
                        reraiseUnhandledException(virtualFrame, localFrame, initialStackTop, isGeneratorOrCoroutine, mutableData, bciSlot, beginBci, pe, tracingOrProfilingEnabled);
                        if (pe != null) {
                            throw pe;
                        }
                        throw e;
                    }
                    if (pe != null) {
                        pe.setCatchingFrameReference(virtualFrame, this, beginBci);
                    }
                    int stackSizeOnEntry = exceptionHandlerRanges[targetIndex + 1];
                    int targetStackTop = stackSizeOnEntry + stackoffset;
                    stackTop = unwindBlock(virtualFrame, stackTop, targetStackTop);
                    /*
                     * Handler range encodes the stack size, not the top of stack. so the stackTop
                     * is to be replaced with the exception
                     */
                    virtualFrame.setObject(stackTop, isInteropException ? e : pe);
                    bci = exceptionHandlerRanges[targetIndex];
                    oparg = 0;
                    if (instrumentation != null) {
                        notifyStatementAfterException(virtualFrame, instrumentation, bci);
                    }
                } catch (Throwable t) {
                    if (instrumentation != null) {
                        // Need to handle instrumentation frame unwind
                        Object result = notifyExceptionalReturn(virtualFrame, mutableData, t);
                        if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                            CompilerDirectives.transferToInterpreter();
                            copyArgs(virtualFrame.getArguments(), virtualFrame);
                            bci = 0;
                            stackTop = getInitialStackTop();
                            oparg = 0;
                            result = notifyEnter(virtualFrame, instrumentation, bci);
                            if (result != null) {
                                return result;
                            }
                            continue;
                        } else if (result != null) {
                            return result;
                        }
                    }
                    throw t;
                }
            }
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeAsyncGenWrap(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        virtualFrame.setObject(stackTop, factory.createAsyncGeneratorWrappedValue(virtualFrame.getObject(stackTop)));
    }

    @BytecodeInterpreterSwitch
    private int bytecodeGetAIter(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int bci) {
        GetAIterNode node = insertChildNode(localNodes, bci, UNCACHED_GET_AITER, GetAIterNodeGen.class, NODE_GET_AITER, useCachedNodes);
        virtualFrame.setObject(stackTop, node.execute(virtualFrame, virtualFrame.getObject(stackTop)));
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeGetANext(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int bci) {
        GetANextNode node = insertChildNode(localNodes, bci, UNCACHED_GET_ANEXT, GetANextNodeGen.class, NODE_GET_ANEXT, useCachedNodes);
        virtualFrame.setObject(stackTop, node.execute(virtualFrame, virtualFrame.getObject(stackTop)));
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeEndAsyncFor(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int bci) {
        EndAsyncForNode node = insertChildNode(localNodes, bci, UNCACHED_END_ASYNC_FOR, EndAsyncForNodeGen.class, NODE_END_ASYNC_FOR, useCachedNodes);
        node.execute(virtualFrame.getObject(stackTop), frameIsVisibleToPython());
        virtualFrame.setObject(stackTop, null); // pop the exception
        virtualFrame.setObject(stackTop - 1, null); // the coroutine that raised the exception
        virtualFrame.setObject(stackTop - 2, null); // the async iterator
        return stackTop - 3;
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop
    private void bytcodeRotN(VirtualFrame virtualFrame, int stackTop, int oparg) {
        CompilerAsserts.partialEvaluationConstant(oparg);
        if (oparg > 1) {
            Object top = virtualFrame.getObject(stackTop);
            int i = 0;
            for (; i < oparg - 1; i++) {
                virtualFrame.setObject(stackTop - i, virtualFrame.getObject(stackTop - i - 1));
            }
            virtualFrame.setObject(stackTop - i, top);
        }
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCheckTpFlags(VirtualFrame virtualFrame, long flags, boolean useCachedNodes, int stackTop, int bci, Node[] localNodes) {
        Object obj = virtualFrame.getObject(stackTop);
        GetTPFlagsNode flagsNode = insertChildNode(localNodes, bci, UNCACHED_TP_FLAGS, GetTPFlagsNodeGen.class, NODE_TP_FLAGS, useCachedNodes);
        boolean res = (flagsNode.execute(obj) & flags) != 0;
        virtualFrame.setObject(++stackTop, res);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeMatchKeys(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        Object keys = virtualFrame.getObject(stackTop);
        Object subject = virtualFrame.getObject(stackTop - 1);
        MatchKeysNode matchKeysNode = insertChildNode(localNodes, bci, MatchKeysNodeGen.class, NODE_MATCH_KEYS);
        Object values = matchKeysNode.execute(virtualFrame, subject, (Object[]) keys);
        virtualFrame.setObject(++stackTop, values);
        virtualFrame.setObject(++stackTop, values != PNone.NONE ? true : false);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCopyDictWithoutKeys(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        Object keys = virtualFrame.getObject(stackTop);
        Object subject = virtualFrame.getObject(stackTop - 1);
        CopyDictWithoutKeysNode copyDictNode = insertChildNode(localNodes, bci, CopyDictWithoutKeysNodeGen.class, NODE_COPY_DICT_WITHOUT_KEYS);
        PDict rest = copyDictNode.execute(virtualFrame, subject, (Object[]) keys);
        virtualFrame.setObject(stackTop, rest);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeMatchClass(VirtualFrame virtualFrame, int stackTop, int oparg, int bci, Node[] localNodes) {
        TruffleString[] argNames = (TruffleString[]) virtualFrame.getObject(stackTop--);
        Object type = virtualFrame.getObject(stackTop);
        Object subject = virtualFrame.getObject(stackTop - 1);

        MatchClassNode matchClassNode = insertChildNode(localNodes, bci, MatchClassNodeGen.class, NODE_MATCH_CLASS);
        Object attrs = matchClassNode.execute(virtualFrame, subject, type, oparg, argNames);

        if (attrs != null) {
            virtualFrame.setObject(stackTop, true);
            virtualFrame.setObject(stackTop - 1, attrs);
        } else {
            virtualFrame.setObject(stackTop, false);
        }
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeGetLen(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, int bci, Node[] localNodes) {
        Object seq = virtualFrame.getObject(stackTop);
        PyObjectSizeNode sizeNode = insertChildNode(localNodes, bci, UNCACHED_SIZE, PyObjectSizeNodeGen.class, NODE_SIZE, useCachedNodes);
        Object s = sizeNode.executeCached(virtualFrame, seq);
        virtualFrame.setObject(++stackTop, s);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeKwargsMerge(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, int bci, Node[] localNodes) {
        KwargsMergeNode mergeNode = insertChildNode(localNodes, bci, UNCACHED_KWARGS_MERGE, KwargsMergeNodeGen.class, NODE_KWARGS_MERGE, useCachedNodes);
        return mergeNode.execute(virtualFrame, stackTop);
    }

    @BytecodeInterpreterSwitch
    private boolean evaluateObjectCondition(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, int bci, byte[] localBC, Node[] localNodes, int beginBci) {
        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_IS_TRUE, PyObjectIsTrueNodeGen.class, NODE_OBJECT_IS_TRUE, useCachedNodes);
        Object condObj = virtualFrame.getObject(stackTop);
        boolean cond = isTrue.executeCached(virtualFrame, condObj);
        return profileCondition(cond, localBC, bci, useCachedNodes);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeGetIter(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        PyObjectGetIter getIter = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_GET_ITER, PyObjectGetIterNodeGen.class, NODE_OBJECT_GET_ITER, useCachedNodes);
        virtualFrame.setObject(stackTop, getIter.executeCached(virtualFrame, virtualFrame.getObject(stackTop)));
    }

    @BytecodeInterpreterSwitch
    private void bytecodeGetYieldFromIter(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        GetYieldFromIterNode getIter = insertChildNode(localNodes, beginBci, UNCACHED_OBJECT_GET_YIELD_FROM_ITER, GetYieldFromIterNodeGen.class, NODE_OBJECT_GET_YIELD_FROM_ITER, useCachedNodes);
        virtualFrame.setObject(stackTop, getIter.execute(virtualFrame, virtualFrame.getObject(stackTop)));
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodeForIterO(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        ForIterONode node = insertChildNode(localNodes, beginBci, UNCACHED_FOR_ITER_O, ForIterONodeGen.class, NODE_FOR_ITER_O, useCachedNodes);
        return node.execute(virtualFrame, virtualFrame.getObject(stackTop), stackTop + 1);
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodeForIterI(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, int bci, Node[] localNodes, int beginBci) {
        ForIterINode node = insertChildNode(localNodes, beginBci, UNCACHED_FOR_ITER_I, ForIterINodeGen.class, NODE_FOR_ITER_I, useCachedNodes);
        boolean cont = true;
        try {
            cont = node.execute(virtualFrame, virtualFrame.getObject(stackTop), stackTop + 1);
        } catch (QuickeningGeneralizeException e) {
            generalizeForIterI(bci, e);
        }
        return cont;
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodeMatchExc(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        Object exception = virtualFrame.getObject(stackTop - 1);
        Object matchType = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop, null);
        ExceptMatchNode matchNode = insertChildNode(localNodes, beginBci, UNCACHED_EXCEPT_MATCH, ExceptMatchNodeGen.class, NODE_EXCEPT_MATCH, useCachedNodes);
        boolean match = !(exception instanceof PException) && matchType == null;
        if (!match) {
            match = matchNode.executeMatch(virtualFrame, exception, matchType);
        }
        return match;
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnwrapExc(VirtualFrame virtualFrame, int stackTop) {
        Object exception = virtualFrame.getObject(stackTop);
        if (exception instanceof PException) {
            virtualFrame.setObject(stackTop, ((PException) exception).getEscapedException());
        }
        // Let interop exceptions be
    }

    @BytecodeInterpreterSwitch
    private int bytecodeSetupWith(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        SetupWithNode setupWithNode = insertChildNode(localNodes, beginBci, UNCACHED_SETUP_WITH_NODE, SetupWithNodeGen.class, NODE_SETUP_WITH, useCachedNodes);
        return setupWithNode.execute(virtualFrame, stackTop);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeExitWith(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        ExitWithNode exitWithNode = insertChildNode(localNodes, beginBci, UNCACHED_EXIT_WITH_NODE, ExitWithNodeGen.class, NODE_EXIT_WITH, useCachedNodes);
        return exitWithNode.execute(virtualFrame, stackTop, frameIsVisibleToPython());
    }

    @BytecodeInterpreterSwitch
    private int bytecodeSetupAWith(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        SetupAwithNode setupAwithNode = insertChildNode(localNodes, beginBci, UNCACHED_SETUP_AWITH_NODE, SetupAwithNodeGen.class, NODE_SETUP_AWITH, useCachedNodes);
        return setupAwithNode.execute(virtualFrame, stackTop);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeGetAExitCoro(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        GetAExitCoroNode getAExitCoroNode = insertChildNode(localNodes, beginBci, UNCACHED_GET_AEXIT_CORO_NODE, GetAExitCoroNodeGen.class, NODE_GET_AEXIT_CORO, useCachedNodes);
        return getAExitCoroNode.execute(virtualFrame, stackTop);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeExitAWith(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, Node[] localNodes, int beginBci) {
        ExitAWithNode exitAWithNode = insertChildNode(localNodes, beginBci, UNCACHED_EXIT_AWITH_NODE, ExitAWithNodeGen.class, NODE_EXIT_AWITH, useCachedNodes);
        return exitAWithNode.execute(virtualFrame, stackTop, frameIsVisibleToPython());
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodeSend(VirtualFrame virtualFrame, int stackTop, Node[] localNodes, int beginBci) {
        Object value = virtualFrame.getObject(stackTop);
        Object obj = virtualFrame.getObject(stackTop - 1);
        SendNode sendNode = insertChildNode(localNodes, beginBci, SendNodeGen.class, NODE_SEND);
        return sendNode.execute(virtualFrame, stackTop, obj, value);
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodeThrow(VirtualFrame virtualFrame, int stackTop, Node[] localNodes, int beginBci) {
        Object exception = virtualFrame.getObject(stackTop);
        if (!(exception instanceof PException)) {
            throw CompilerDirectives.shouldNotReachHere("interop exceptions not supported in throw");
        }
        Object obj = virtualFrame.getObject(stackTop - 1);
        ThrowNode throwNode = insertChildNode(localNodes, beginBci, ThrowNodeGen.class, NODE_THROW);
        return throwNode.execute(virtualFrame, stackTop, obj, (PException) exception);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeSetupAnnotations(VirtualFrame virtualFrame, boolean useCachedNodes, Node[] localNodes, int beginBci) {
        SetupAnnotationsNode setupAnnotationsNode = insertChildNode(localNodes, beginBci, UNCACHED_SETUP_ANNOTATIONS, SetupAnnotationsNodeGen.class, NODE_SETUP_ANNOTATIONS,
                        useCachedNodes);
        setupAnnotationsNode.execute(virtualFrame);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeMakeFunction(VirtualFrame virtualFrame, Object globals, int stackTop, Node[] localNodes, int beginBci, int flags, Object localConsts) {
        BytecodeCodeUnit codeUnit = (BytecodeCodeUnit) localConsts;
        MakeFunctionNode makeFunctionNode = insertMakeFunctionNode(localNodes, beginBci, codeUnit);
        return makeFunctionNode.execute(virtualFrame, globals, stackTop, flags);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeResumeYield(VirtualFrame virtualFrame, boolean useCachedNodes, Object[] arguments, MutableLoopData mutableData, int stackTop, int bci, Node[] localNodes) {
        mutableData.localException = PArguments.getException(PArguments.getGeneratorFrame(arguments));
        if (mutableData.localException != null) {
            PArguments.setException(arguments, mutableData.localException);
        }
        GetSendValueNode node = insertChildNode(localNodes, bci, UNCACHED_GET_SEND_VALUE, GetSendValueNodeGen.class, NODE_GET_SEND_VALUE, useCachedNodes);
        virtualFrame.setObject(stackTop, node.execute(PArguments.getSpecialArgument(arguments)));
    }

    @BytecodeInterpreterSwitch
    private void bytecodePushExcInfo(VirtualFrame virtualFrame, Object[] arguments, MutableLoopData mutableData, int stackTop) {
        Object exception = virtualFrame.getObject(stackTop);
        Object origException = exception;
        if (!(exception instanceof PException)) {
            exception = ExceptionUtils.wrapJavaException((Throwable) exception, this, factory.createBaseException(PythonErrorType.SystemError, ErrorMessages.M, new Object[]{exception}));
        }
        if (!mutableData.fetchedException) {
            mutableData.outerException = PArguments.getException(arguments);
            mutableData.fetchedException = true;
        }
        virtualFrame.setObject(stackTop++, mutableData.localException);
        mutableData.localException = (PException) exception;
        PArguments.setException(arguments, mutableData.localException);
        virtualFrame.setObject(stackTop, origException);
    }

    @BytecodeInterpreterSwitch
    private GeneratorYieldResult bytecodeYieldValue(VirtualFrame virtualFrame, Frame localFrame, int initialStackTop, Object[] arguments, InstrumentationSupport instrumentation,
                    MutableLoopData mutableData, int stackTop, int bci, byte tracingOrProfilingEnabled, int beginBci) {
        if (CompilerDirectives.hasNextTier() && mutableData.loopCount > 0) {
            LoopNode.reportLoopCount(this, mutableData.loopCount);
        }
        Object value = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        PArguments.setException(PArguments.getGeneratorFrame(arguments), mutableData.localException);
        // See PBytecodeGeneratorRootNode#execute
        if (localFrame != virtualFrame) {
            copyStackSlotsToGeneratorFrame(virtualFrame, localFrame, stackTop);
            // Clear slots that were popped (if any)
            clearFrameSlots(localFrame, stackTop + 1, initialStackTop);
        }
        traceOrProfileYield(virtualFrame, mutableData, value, tracingOrProfilingEnabled);
        if (instrumentation != null) {
            notifyReturn(virtualFrame, mutableData, instrumentation, beginBci, value);
        }
        return new GeneratorYieldResult(bci + 1, stackTop, value);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadBuildClass(VirtualFrame virtualFrame, boolean useCachedNodes, Object globals, int stackTop, Node[] localNodes, int beginBci) {
        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes, beginBci, UNCACHED_READ_GLOBAL_OR_BUILTIN, ReadGlobalOrBuiltinNodeGen.class, NODE_READ_GLOBAL_OR_BUILTIN_BUILD_CLASS,
                        useCachedNodes);
        virtualFrame.setObject(stackTop, read.read(virtualFrame, globals, T___BUILD_CLASS__));
    }

    @BytecodeInterpreterSwitch
    private Object bytecodeReturnValue(VirtualFrame virtualFrame, boolean isGeneratorOrCoroutine, InstrumentationSupport instrumentation, MutableLoopData mutableData, int stackTop,
                    byte tracingOrProfilingEnabled, int beginBci) {
        if (CompilerDirectives.hasNextTier() && mutableData.loopCount > 0) {
            LoopNode.reportLoopCount(this, mutableData.loopCount);
        }
        Object value = virtualFrame.getObject(stackTop);
        traceOrProfileReturn(virtualFrame, mutableData, value, tracingOrProfilingEnabled);

        if (instrumentation != null) {
            notifyReturn(virtualFrame, mutableData, instrumentation, beginBci, value);
        }
        if (isGeneratorOrCoroutine) {
            throw new GeneratorReturnException(value);
        } else {
            return value;
        }
    }

    @InliningCutoff
    private void notifyStatementAfterException(VirtualFrame virtualFrame, InstrumentationSupport instrumentation, int bci) {
        instrumentation.notifyStatementEnter(virtualFrame, bciToLine(bci));
    }

    @InliningCutoff
    private void notifyException(VirtualFrame virtualFrame, InstrumentationSupport instrumentation, int bci, Throwable e) {
        instrumentation.notifyException(virtualFrame, bciToLine(bci), e);
    }

    @InliningCutoff
    private Object notifyExceptionalReturn(VirtualFrame virtualFrame, MutableLoopData mutableData, Throwable e) {
        if (instrumentationRoot instanceof WrapperNode) {
            WrapperNode wrapper = (WrapperNode) instrumentationRoot;
            Object result = wrapper.getProbeNode().onReturnExceptionalOrUnwind(virtualFrame, e, mutableData.isReturnCalled());
            checkOnReturnExceptionalOrUnwindResult(result);
            return result;
        }
        return null;
    }

    private void checkOnReturnExceptionalOrUnwindResult(Object result) {
        if (result != null) {
            if (co.isGeneratorOrCoroutine()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(result == ProbeNode.UNWIND_ACTION_REENTER ? "Frame restarting is not possible in generators" : "Cannot replace return value of generators");
            }
        }
    }

    @InliningCutoff
    private void notifyReturn(VirtualFrame virtualFrame, MutableLoopData mutableData, InstrumentationSupport instrumentation, int bci, Object value) {
        try {
            instrumentation.notifyStatementExit(virtualFrame, bciToLine(bci));
        } catch (Throwable t) {
            mutableData.setExceptionNotified(true);
            throw t;
        }
        mutableData.setReturnCalled(true);
        if (instrumentationRoot instanceof WrapperNode) {
            WrapperNode wrapper = (WrapperNode) instrumentationRoot;
            wrapper.getProbeNode().onReturnValue(virtualFrame, value);
        }
    }

    private void notifyStatement(VirtualFrame virtualFrame, InstrumentationSupport instrumentation, MutableLoopData mutableData, int bci, int beginBci) {
        if (instrumentation != null) {
            notifyStatementCutoff(virtualFrame, instrumentation, mutableData, beginBci, bci);
        }
    }

    @InliningCutoff
    private void notifyStatementCutoff(VirtualFrame virtualFrame, InstrumentationSupport instrumentation, MutableLoopData mutableData, int prevBci, int nextBci) {
        try {
            instrumentation.notifyStatement(virtualFrame, bciToLine(prevBci), bciToLine(nextBci));
        } catch (Throwable t) {
            mutableData.setExceptionNotified(true);
            throw t;
        }
    }

    @InliningCutoff
    private Object notifyEnter(VirtualFrame virtualFrame, InstrumentationSupport instrumentation, int initialBci) {
        if (instrumentationRoot instanceof WrapperNode) {
            WrapperNode wrapper = (WrapperNode) instrumentationRoot;
            try {
                wrapper.getProbeNode().onEnter(virtualFrame);
            } catch (Throwable t) {
                Object result = wrapper.getProbeNode().onReturnExceptionalOrUnwind(virtualFrame, t, false);
                checkOnReturnExceptionalOrUnwindResult(result);
                if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                    // We're at the beginning, reenter means just restore args and continue
                    copyArgs(virtualFrame.getArguments(), virtualFrame);
                    return null;
                } else if (result != null) {
                    return result;
                }
            }
        }
        int line = bciToLine(initialBci);
        try {
            instrumentation.notifyStatementEnter(virtualFrame, line);
        } catch (Throwable t) {
            instrumentation.notifyException(virtualFrame, line, t);
            throw t;
        }
        return null;
    }

    private MakeFunctionNode insertMakeFunctionNode(Node[] localNodes, int beginBci, BytecodeCodeUnit codeUnit) {
        return insertChildNode(localNodes, beginBci, MakeFunctionNodeGen.class, () -> MakeFunctionNode.create(getLanguage(PythonLanguage.class), codeUnit, source));
    }

    public void materializeContainedFunctionsForInstrumentation(Set<Class<? extends Tag>> materializedTags) {
        usingCachedNodes = true;
        BytecodeCodeUnit.iterateBytecode(bytecode, (bci, op, oparg, followingArgs) -> {
            if (op == OpCodes.MAKE_FUNCTION) {
                BytecodeCodeUnit codeUnit = (BytecodeCodeUnit) consts[oparg];
                MakeFunctionNode makeFunctionNode = insertMakeFunctionNode(getChildNodes(), bci, codeUnit);
                RootNode rootNode = makeFunctionNode.getCallTarget().getRootNode();
                if (rootNode instanceof PBytecodeGeneratorFunctionRootNode) {
                    rootNode = ((PBytecodeGeneratorFunctionRootNode) rootNode).getBytecodeRootNode();
                }
                ((PBytecodeRootNode) rootNode).instrumentationRoot.materializeInstrumentableNodes(materializedTags);
            }
        });
    }

    public Node createInstrumentationMaterializationForwarder() {
        return new InstrumentationMaterializationForwarder(instrumentationRoot);
    }

    @InliningCutoff // Used only to print expressions in interactive mode
    private int bytecodePrintExpr(VirtualFrame virtualFrame, boolean useCachedNodes, int stackTop, int bci, Node[] localNodes, int bciSlot, int beginBci) {
        setCurrentBci(virtualFrame, bciSlot, bci);
        PrintExprNode printExprNode = insertChildNode(localNodes, beginBci, UNCACHED_PRINT_EXPR, PrintExprNodeGen.class, NODE_PRINT_EXPR, useCachedNodes);
        printExprNode.execute(virtualFrame, virtualFrame.getObject(stackTop));
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private byte checkTracingAndProfilingEnabled(Assumption noTraceOrProfile, MutableLoopData mutableData) {
        if (!noTraceOrProfile.isValid() && frameIsVisibleToPython()) {
            PythonContext.PythonThreadState ts = mutableData.getThreadState(this);
            Object profileFun = ts.getProfileFun();
            if (ts.getTraceFun() != null) {
                if (profileFun != null) {
                    return TRACE_AND_PROFILE_FUN;
                } else {
                    return TRACE_FUN;
                }
            } else if (profileFun != null) {
                return PROFILE_FUN;
            }
        }
        return 0;
    }

    private static boolean isTracingOrProfilingEnabled(byte tracingOrProfilingEnabled) {
        return tracingOrProfilingEnabled != 0;
    }

    private static boolean isTracingEnabled(byte tracingOrProfilingEnabled) {
        return (tracingOrProfilingEnabled & TRACE_FUN) != 0;
    }

    private static boolean isProfilingEnabled(byte tracingOrProfilingEnabled) {
        return (tracingOrProfilingEnabled & PROFILE_FUN) != 0;
    }

    private void traceOrProfileYield(VirtualFrame virtualFrame, MutableLoopData mutableData, Object value, byte tracingOrProfilingEnabled) {
        if (isTracingOrProfilingEnabled(tracingOrProfilingEnabled)) {
            traceOrProfileYieldCutoff(virtualFrame, mutableData, value, tracingOrProfilingEnabled);
        }
    }

    @InliningCutoff
    private void traceOrProfileYieldCutoff(VirtualFrame virtualFrame, MutableLoopData mutableData, Object value, byte tracingOrProfilingEnabled) {
        if (isTracingEnabled(tracingOrProfilingEnabled)) {
            invokeTraceFunction(virtualFrame, value, mutableData.getThreadState(this), mutableData, PythonContext.TraceEvent.RETURN, mutableData.getReturnLine(), true);
        }
        if (isProfilingEnabled(tracingOrProfilingEnabled)) {
            invokeProfileFunction(virtualFrame, value, mutableData.getThreadState(this), mutableData, PythonContext.ProfileEvent.RETURN);
        }
    }

    private void traceOrProfileReturn(VirtualFrame virtualFrame, MutableLoopData mutableData, Object value, byte tracingOrProfilingEnabled) {
        if (isTracingOrProfilingEnabled(tracingOrProfilingEnabled)) {
            traceOrProfileReturnCutoff(virtualFrame, mutableData, value, tracingOrProfilingEnabled);
        }
    }

    @InliningCutoff
    private void traceOrProfileReturnCutoff(VirtualFrame virtualFrame, MutableLoopData mutableData, Object value, byte tracingOrProfilingEnabled) {
        if (isTracingEnabled(tracingOrProfilingEnabled)) {
            invokeTraceFunction(virtualFrame, value, mutableData.getThreadState(this), mutableData, PythonContext.TraceEvent.RETURN, mutableData.getReturnLine(), true);
        }
        if (isProfilingEnabled(tracingOrProfilingEnabled)) {
            invokeProfileFunction(virtualFrame, value, mutableData.getThreadState(this), mutableData, PythonContext.ProfileEvent.RETURN);
        }
    }

    @InliningCutoff
    private void traceException(VirtualFrame virtualFrame, MutableLoopData mutableData, int bci, PException pe) {
        mutableData.setPyFrame(ensurePyFrame(virtualFrame));
        if (mutableData.getPyFrame().getLocalTraceFun() != null) {
            pe.setCatchingFrameReference(virtualFrame, this, bci);
            Object peForPython = pe.getEscapedException();
            Object peType = GetClassNode.executeUncached(peForPython);
            Object traceback = ExceptionNodes.GetTracebackNode.executeUncached(peForPython);
            invokeTraceFunction(virtualFrame,
                            factory.createTuple(new Object[]{peType, peForPython, traceback}), mutableData.getThreadState(this),
                            mutableData,
                            PythonContext.TraceEvent.EXCEPTION, bciToLine(bci), true);
        }
    }

    private void traceOrProfileCall(VirtualFrame virtualFrame, int initialBci, MutableLoopData mutableData, byte tracingOrProfilingEnabled) {
        if (isTracingOrProfilingEnabled(tracingOrProfilingEnabled)) {
            traceOrProfileCallCutoff(virtualFrame, initialBci, mutableData, tracingOrProfilingEnabled);
        }
    }

    @InliningCutoff
    private void traceOrProfileCallCutoff(VirtualFrame virtualFrame, int initialBci, MutableLoopData mutableData, byte tracingOrProfilingEnabled) {
        if (isTracingEnabled(tracingOrProfilingEnabled)) {
            invokeTraceFunction(virtualFrame, null, mutableData.getThreadState(this), mutableData, PythonContext.TraceEvent.CALL,
                            initialBci == 0 ? getFirstLineno() : (mutableData.setPastLine(bciToLine(initialBci))), false);
        }
        if (isProfilingEnabled(tracingOrProfilingEnabled)) {
            invokeProfileFunction(virtualFrame, null, mutableData.getThreadState(this), mutableData, PythonContext.ProfileEvent.CALL);
        }
    }

    @InliningCutoff
    private void traceLine(VirtualFrame virtualFrame, MutableLoopData mutableData, byte[] localBC, int bci) {
        int thisLine = bciToLine(bci);
        boolean onANewLine = thisLine != mutableData.getPastLine();
        mutableData.setPastLine(thisLine);
        OpCodes c = OpCodes.fromOpCode(localBC[mutableData.getPastBci()]);
        /*
         * normally, we trace a line every time the previous bytecode instruction was on a different
         * line than the current one. There are a number of exceptions to this, notably around
         * jumps:
         *
         * - When a backward jumps happens, a line is traced, even if it is the same as the previous
         * one.
         *
         * - When a forward jump happens to the first bytecode instruction of a line, a line is
         * traced.
         *
         * - When a forward jump happens to the middle of a line, a line is not traced.
         *
         * see https://github.com/python/cpython/blob/main/Objects/lnotab_notes.txt#L210-L215 for
         * more details
         */
        boolean shouldTrace = mutableData.getPastBci() > bci; // is a backward jump
        if (!shouldTrace) {
            shouldTrace = onANewLine &&
                            // is not a forward jump
                            (mutableData.getPastBci() + c.length() >= bci ||
                                            // is a forward jump to the start of line
                                            bciToLine(bci - 1) != thisLine);
        }
        if (shouldTrace) {
            mutableData.setReturnLine(mutableData.getPastLine());
            mutableData.setPyFrame(ensurePyFrame(virtualFrame));
            if (mutableData.getPyFrame().getTraceLine()) {
                invokeTraceFunction(virtualFrame, null, mutableData.getThreadState(this), mutableData, PythonContext.TraceEvent.LINE,
                                mutableData.getPastLine(), true);
            }
        }
        mutableData.setPastBci(bci);
    }

    private int bytecodeBinarySubscrAdaptive(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int bciSlot) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isInt(stackTop) && virtualFrame.getObject(stackTop - 1) instanceof PSequence) {
            /* Always start with object result and then try to rewrite to a more specific one */
            // TODO this would benefit from having an uncached node
            bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_I_O;
            stackTop = bytecodeBinarySubscrSeqIO(virtualFrame, stackTop, bci, localNodes);
            if (bytecode[bci] == OpCodesConstants.BINARY_SUBSCR_SEQ_I_O && outputCanQuicken[bci] != 0) {
                Object result = virtualFrame.getObject(stackTop);
                if (result instanceof Integer && (outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                    bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_I_I;
                    virtualFrame.setInt(stackTop, (Integer) result);
                } else if (result instanceof Double && (outputCanQuicken[bci] & QuickeningTypes.DOUBLE) != 0) {
                    bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_I_D;
                    virtualFrame.setDouble(stackTop, (Double) result);
                }
            }
            return stackTop;
        }
        if (!virtualFrame.isObject(stackTop)) {
            generalizeInputs(bci);
            generalizeFrameSlot(virtualFrame, stackTop);
        }
        bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_O_O;
        return bytecodeBinarySubscrOO(virtualFrame, stackTop, bci, localNodes, bciSlot);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeBinarySubscrOO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int bciSlot) {
        setCurrentBci(virtualFrame, bciSlot, bci);
        PyObjectGetItem getItemNode = insertChildNode(localNodes, bci, PyObjectGetItemNodeGen.class, NODE_GET_ITEM);
        Object index;
        try {
            index = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // Should only happen in multi-context mode
            return generalizeBinarySubscr(virtualFrame, stackTop, bci, localNodes);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop, getItemNode.executeCached(virtualFrame, virtualFrame.getObject(stackTop), index));
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeBinarySubscrSeqIO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeBinarySubscr(virtualFrame, stackTop, bci, localNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object sequence = virtualFrame.getObject(stackTop - 1);
        BinarySubscrSeq.ONode node = insertChildNode(localNodes, bci, BinarySubscrSeqFactory.ONodeGen.class, NODE_BINARY_SUBSCR_SEQ_O);
        Object value;
        try {
            value = node.execute(sequence, index);
        } catch (QuickeningGeneralizeException e) {
            return generalizeBinarySubscrSeq(virtualFrame, stackTop, bci, localNodes, e);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop, value);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeBinarySubscrSeqII(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeBinarySubscr(virtualFrame, stackTop, bci, localNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object sequence = virtualFrame.getObject(stackTop - 1);
        BinarySubscrSeq.INode node = insertChildNode(localNodes, bci, BinarySubscrSeqFactory.INodeGen.class, NODE_BINARY_SUBSCR_SEQ_I);
        int value;
        try {
            value = node.execute(sequence, index);
        } catch (QuickeningGeneralizeException e) {
            return generalizeBinarySubscrSeq(virtualFrame, stackTop, bci, localNodes, e);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setInt(stackTop, value);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeBinarySubscrSeqID(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeBinarySubscr(virtualFrame, stackTop, bci, localNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object sequence = virtualFrame.getObject(stackTop - 1);
        BinarySubscrSeq.DNode node = insertChildNode(localNodes, bci, BinarySubscrSeqFactory.DNodeGen.class, NODE_BINARY_SUBSCR_SEQ_D);
        double value;
        try {
            value = node.execute(sequence, index);
        } catch (QuickeningGeneralizeException e) {
            return generalizeBinarySubscrSeq(virtualFrame, stackTop, bci, localNodes, e);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setDouble(stackTop, value);
        return stackTop;
    }

    private int generalizeBinarySubscrSeq(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, QuickeningGeneralizeException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (e == BinarySubscrSeq.GENERALIZE_RESULT) {
            return generalizeBinarySubscrSeqResult(virtualFrame, stackTop, bci, localNodes);
        } else {
            return generalizeBinarySubscr(virtualFrame, stackTop, bci, localNodes);
        }
    }

    private int generalizeBinarySubscrSeqResult(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_I_O;
        return bytecodeBinarySubscrOO(virtualFrame, stackTop, bci, localNodes, bcioffset);
    }

    private int generalizeBinarySubscr(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeInputs(bci);
        generalizeFrameSlot(virtualFrame, stackTop);
        bytecode[bci] = OpCodesConstants.BINARY_SUBSCR_SEQ_O_O;
        return bytecodeBinarySubscrOO(virtualFrame, stackTop, bci, localNodes, bcioffset);
    }

    private PFrame ensurePyFrame(VirtualFrame virtualFrame) {
        if (traceMaterializeFrameNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            traceMaterializeFrameNode = insert(MaterializeFrameNode.create());
        }
        return traceMaterializeFrameNode.execute(virtualFrame, this, true, true);
    }

    @InliningCutoff
    private void invokeTraceFunction(VirtualFrame virtualFrame, Object arg, PythonContext.PythonThreadState threadState, MutableLoopData mutableData,
                    PythonContext.TraceEvent event, int line, boolean useLocalFn) {
        if (threadState.isTracing()) {
            return;
        }
        assert event != PythonContext.TraceEvent.DISABLED;
        threadState.tracingStart(event);
        PFrame pyFrame = mutableData.setPyFrame(ensurePyFrame(virtualFrame));
        Object traceFn = useLocalFn ? pyFrame.getLocalTraceFun() : threadState.getTraceFun();
        if (traceFn == null) {
            threadState.tracingStop();
            return;
        }
        Object nonNullArg = arg == null ? PNone.NONE : arg;
        try {
            if (line != -1) {
                pyFrame.setLineLock(line);
            }
            // Force locals dict sync, so that we can sync them back later
            GetFrameLocalsNode.executeUncached(pyFrame);
            Object result = CallTernaryMethodNode.getUncached().execute(null, traceFn, pyFrame, event.pythonName, nonNullArg);
            syncLocalsBackToFrame(virtualFrame, pyFrame);
            Object realResult = result == PNone.NONE ? null : result;
            pyFrame.setLocalTraceFun(realResult);
        } catch (Throwable e) {
            threadState.setTraceFun(null, PythonLanguage.get(this));
            throw e;
        } finally {
            if (line != -1) {
                pyFrame.lineUnlock();
            }
            threadState.tracingStop();
        }
    }

    private void syncLocalsBackToFrame(VirtualFrame virtualFrame, PFrame pyFrame) {
        Frame localFrame = virtualFrame;
        if (co.isGeneratorOrCoroutine()) {
            localFrame = PArguments.getGeneratorFrame(virtualFrame);
        }
        GetFrameLocalsNode.syncLocalsBackToFrame(co, pyFrame, localFrame);
    }

    private void profileCEvent(VirtualFrame virtualFrame, Object callable, PythonContext.ProfileEvent event, MutableLoopData mutableData, byte tracingOrProfilingEnabled) {
        if (isProfilingEnabled(tracingOrProfilingEnabled)) {
            profileCEvent(virtualFrame, callable, event, mutableData);
        }
    }

    @InliningCutoff
    private void profileCEvent(VirtualFrame virtualFrame, Object callable, PythonContext.ProfileEvent event, MutableLoopData mutableData) {
        PythonContext.PythonThreadState threadState = mutableData.getThreadState(this);
        if (isBuiltin(callable)) {
            invokeProfileFunction(virtualFrame, callable, threadState, mutableData, event);
        } else if (callable instanceof BoundDescriptor && isBuiltin(((BoundDescriptor) callable).descriptor)) {
            invokeProfileFunction(virtualFrame, ((BoundDescriptor) callable).descriptor, threadState, mutableData, event);
        }
    }

    private static boolean isBuiltin(Object fun) {
        return fun instanceof PBuiltinFunction || fun instanceof PBuiltinMethod;
    }

    @InliningCutoff
    private void invokeProfileFunction(VirtualFrame virtualFrame, Object arg, PythonContext.PythonThreadState threadState, MutableLoopData mutableData, PythonContext.ProfileEvent event) {
        if (threadState.isProfiling()) {
            return;
        }

        threadState.profilingStart();
        PFrame pyFrame = mutableData.setPyFrame(ensurePyFrame(virtualFrame));
        Object profileFun = threadState.getProfileFun();

        if (profileFun == null) {
            threadState.profilingStop();
            return;
        }

        try {
            // Force locals dict sync, so that we can sync them back later
            GetFrameLocalsNode.executeUncached(pyFrame);
            Object result = CallTernaryMethodNode.getUncached().execute(null, profileFun, pyFrame, event.name, arg == null ? PNone.NONE : arg);
            syncLocalsBackToFrame(virtualFrame, pyFrame);
            Object realResult = result == PNone.NONE ? null : result;
            pyFrame.setLocalTraceFun(realResult);
        } catch (Throwable e) {
            threadState.setProfileFun(null, PythonLanguage.get(this));
            throw e;
        } finally {
            threadState.profilingStop();
        }
    }

    @ExplodeLoop
    private void unboxVariables(Frame localFrame) {
        /*
         * We keep some variables boxed in the interpreter, but unbox in the compiled code. When OSR
         * is entered, we need to unbox existing variables for the compiled code. Should have no
         * effect otherwise.
         */
        for (int i = 0; i < variableTypes.length; i++) {
            if (variableTypes[i] != 0 && variableTypes[i] != QuickeningTypes.OBJECT && localFrame.isObject(i)) {
                Object value = localFrame.getObject(i);
                if (value != null) {
                    if (variableTypes[i] == QuickeningTypes.INT) {
                        localFrame.setInt(i, (int) value);
                    } else if (variableTypes[i] == QuickeningTypes.BOOLEAN) {
                        localFrame.setBoolean(i, (boolean) value);
                    }
                }
            }
        }
    }

    @InliningCutoff
    private void reraiseUnhandledException(VirtualFrame virtualFrame, Frame localFrame, int initialStackTop, boolean isGeneratorOrCoroutine, MutableLoopData mutableData, int bciSlot,
                    int beginBci, PException pe, byte tracingOrProfilingEnabled) {
        // For tracebacks
        setCurrentBci(virtualFrame, bciSlot, beginBci);
        if (pe != null) {
            pe.notifyAddedTracebackFrame(frameIsVisibleToPython());
        }
        if (isGeneratorOrCoroutine) {
            if (localFrame != virtualFrame) {
                // Unwind the generator frame stack
                clearFrameSlots(localFrame, stackoffset, initialStackTop);
            }
        }
        if (CompilerDirectives.hasNextTier() && mutableData.loopCount > 0) {
            LoopNode.reportLoopCount(this, mutableData.loopCount);
        }
        traceOrProfileReturn(virtualFrame, mutableData, PNone.NONE, tracingOrProfilingEnabled);
    }

    @InliningCutoff
    private void chainPythonExceptions(VirtualFrame virtualFrame, MutableLoopData mutableData, PException pe) {
        if (pe != null) {
            if (mutableData.localException != null) {
                chainPythonExceptions(pe, mutableData.localException);
            } else {
                if (getCaughtExceptionNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
                }
                PException exceptionState = getCaughtExceptionNode.execute(virtualFrame);
                if (exceptionState != null) {
                    chainPythonExceptions(pe, exceptionState);
                }
            }
        }
    }

    private void chainPythonExceptions(PException current, PException context) {
        if (chainExceptionsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            chainExceptionsNode = insert(ChainExceptionsNode.create());
        }
        chainExceptionsNode.execute(current, context);
    }

    private PException raiseUnknownBytecodeError(byte bc) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw PRaiseNode.raiseUncached(this, SystemError, toTruffleStringUncached("not implemented bytecode %s"), OpCodes.fromOpCode(bc));
    }

    private void generalizeForIterI(int bci, QuickeningGeneralizeException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (e.type == QuickeningTypes.OBJECT) {
            bytecode[bci] = OpCodesConstants.FOR_ITER_O;
        } else {
            throw CompilerDirectives.shouldNotReachHere("invalid type");
        }
    }

    private void bytecodeForIterAdaptive(int bci) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
            bytecode[bci] = OpCodesConstants.FOR_ITER_I;
        } else {
            bytecode[bci] = OpCodesConstants.FOR_ITER_O;
        }
    }

    private void generalizePopAndJumpIfTrueB(int bci) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeInputs(bci);
        bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_TRUE_O;
    }

    private void generalizePopAndJumpIfFalseB(int bci) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeInputs(bci);
        bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_FALSE_O;
    }

    private static void setCurrentBci(VirtualFrame virtualFrame, int bciSlot, int bci) {
        virtualFrame.setInt(bciSlot, bci);
    }

    @BytecodeInterpreterSwitch
    private boolean bytecodePopCondition(VirtualFrame virtualFrame, int stackTop, Node[] localNodes, int bci, boolean useCachedNodes) {
        PyObjectIsTrueNode isTrue = insertChildNode(localNodes, bci, UNCACHED_OBJECT_IS_TRUE, PyObjectIsTrueNodeGen.class, NODE_OBJECT_IS_TRUE, useCachedNodes);
        Object cond;
        if (virtualFrame.isObject(stackTop)) {
            cond = virtualFrame.getObject(stackTop);
        } else {
            // Can happen when multiple code paths produce different types
            cond = generalizePopCondition(virtualFrame, stackTop, bci);
        }
        virtualFrame.setObject(stackTop, null);
        return isTrue.executeCached(virtualFrame, cond);
    }

    private Object generalizePopCondition(VirtualFrame virtualFrame, int stackTop, int bci) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeInputs(bci);
        return virtualFrame.getValue(stackTop);
    }

    private void bytecodeBinaryOpAdaptive(VirtualFrame virtualFrame, int stackTop, byte[] localBC, int bci, Node[] localNodes, int op, boolean useCachedNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isObject(stackTop) && virtualFrame.isObject(stackTop - 1)) {
            localBC[bci] = OpCodesConstants.BINARY_OP_OO_O;
            bytecodeBinaryOpOOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
            return;
        } else if (virtualFrame.isInt(stackTop) && virtualFrame.isInt(stackTop - 1)) {
            switch (op) {
                case BinaryOpsConstants.ADD:
                case BinaryOpsConstants.INPLACE_ADD:
                case BinaryOpsConstants.SUB:
                case BinaryOpsConstants.INPLACE_SUB:
                case BinaryOpsConstants.MUL:
                case BinaryOpsConstants.INPLACE_MUL:
                case BinaryOpsConstants.FLOORDIV:
                case BinaryOpsConstants.INPLACE_FLOORDIV:
                case BinaryOpsConstants.MOD:
                case BinaryOpsConstants.INPLACE_MOD:
                case BinaryOpsConstants.LSHIFT:
                case BinaryOpsConstants.INPLACE_LSHIFT:
                case BinaryOpsConstants.RSHIFT:
                case BinaryOpsConstants.INPLACE_RSHIFT:
                case BinaryOpsConstants.AND:
                case BinaryOpsConstants.INPLACE_AND:
                case BinaryOpsConstants.OR:
                case BinaryOpsConstants.INPLACE_OR:
                case BinaryOpsConstants.XOR:
                case BinaryOpsConstants.INPLACE_XOR:
                case BinaryOpsConstants.POW:
                case BinaryOpsConstants.INPLACE_POW:
                    if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                        localBC[bci] = OpCodesConstants.BINARY_OP_II_I;
                        bytecodeBinaryOpIII(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
                    } else {
                        localBC[bci] = OpCodesConstants.BINARY_OP_II_O;
                        bytecodeBinaryOpIIO(virtualFrame, stackTop, bci, localNodes, op);
                    }
                    return;
                case BinaryOpsConstants.TRUEDIV:
                case BinaryOpsConstants.INPLACE_TRUEDIV:
                    // TODO truediv should quicken to BINARY_OP_II_D
                    localBC[bci] = OpCodesConstants.BINARY_OP_II_O;
                    bytecodeBinaryOpIIO(virtualFrame, stackTop, bci, localNodes, op);
                    return;
                case BinaryOpsConstants.EQ:
                case BinaryOpsConstants.NE:
                case BinaryOpsConstants.GT:
                case BinaryOpsConstants.GE:
                case BinaryOpsConstants.LE:
                case BinaryOpsConstants.LT:
                case BinaryOpsConstants.IS:
                    if ((outputCanQuicken[bci] & QuickeningTypes.BOOLEAN) != 0) {
                        localBC[bci] = OpCodesConstants.BINARY_OP_II_B;
                        bytecodeBinaryOpIIB(virtualFrame, stackTop, bci, localNodes, op);
                    } else {
                        localBC[bci] = OpCodesConstants.BINARY_OP_II_O;
                        bytecodeBinaryOpIIO(virtualFrame, stackTop, bci, localNodes, op);
                    }
                    return;
            }
        } else if (virtualFrame.isDouble(stackTop) && virtualFrame.isDouble(stackTop - 1)) {
            switch (op) {
                case BinaryOpsConstants.ADD:
                case BinaryOpsConstants.INPLACE_ADD:
                case BinaryOpsConstants.SUB:
                case BinaryOpsConstants.INPLACE_SUB:
                case BinaryOpsConstants.MUL:
                case BinaryOpsConstants.INPLACE_MUL:
                case BinaryOpsConstants.TRUEDIV:
                case BinaryOpsConstants.INPLACE_TRUEDIV:
                case BinaryOpsConstants.POW:
                case BinaryOpsConstants.INPLACE_POW:
                    if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                        localBC[bci] = OpCodesConstants.BINARY_OP_DD_D;
                        bytecodeBinaryOpDDD(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
                    } else {
                        localBC[bci] = OpCodesConstants.BINARY_OP_DD_O;
                        bytecodeBinaryOpDDO(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
                    }
                    return;
                case BinaryOpsConstants.EQ:
                case BinaryOpsConstants.NE:
                case BinaryOpsConstants.GT:
                case BinaryOpsConstants.GE:
                case BinaryOpsConstants.LE:
                case BinaryOpsConstants.LT:
                    if ((outputCanQuicken[bci] & QuickeningTypes.BOOLEAN) != 0) {
                        localBC[bci] = OpCodesConstants.BINARY_OP_DD_B;
                        bytecodeBinaryOpDDB(virtualFrame, stackTop, bci, localNodes, op);
                    } else {
                        localBC[bci] = OpCodesConstants.BINARY_OP_DD_O;
                        bytecodeBinaryOpDDO(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
                    }
                    return;
            }
        }
        // TODO other types
        generalizeFrameSlot(virtualFrame, stackTop);
        generalizeFrameSlot(virtualFrame, stackTop - 1);
        generalizeInputs(bci);
        localBC[bci] = OpCodesConstants.BINARY_OP_OO_O;
        bytecodeBinaryOpOOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpIIB(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        int right, left;
        if (virtualFrame.isInt(stackTop) && virtualFrame.isInt(stackTop - 1)) {
            right = virtualFrame.getInt(stackTop);
            left = virtualFrame.getInt(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        boolean result;
        switch (op) {
            case BinaryOpsConstants.EQ:
            case BinaryOpsConstants.IS:
                result = left == right;
                break;
            case BinaryOpsConstants.NE:
                result = left != right;
                break;
            case BinaryOpsConstants.LT:
                result = left < right;
                break;
            case BinaryOpsConstants.LE:
                result = left <= right;
                break;
            case BinaryOpsConstants.GT:
                result = left > right;
                break;
            case BinaryOpsConstants.GE:
                result = left >= right;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_II_B");
        }
        virtualFrame.setBoolean(stackTop - 1, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpIIO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        int right, left;
        if (virtualFrame.isInt(stackTop) && virtualFrame.isInt(stackTop - 1)) {
            right = virtualFrame.getInt(stackTop);
            left = virtualFrame.getInt(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        Object result;
        switch (op) {
            case BinaryOpsConstants.ADD:
            case BinaryOpsConstants.INPLACE_ADD:
                IntBuiltins.AddNode addNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.AddNodeFactory.AddNodeGen.class, NODE_INT_ADD);
                result = addNode.execute(left, right);
                break;
            case BinaryOpsConstants.SUB:
            case BinaryOpsConstants.INPLACE_SUB:
                IntBuiltins.SubNode subNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.SubNodeFactory.SubNodeGen.class, NODE_INT_SUB);
                result = subNode.execute(left, right);
                break;
            case BinaryOpsConstants.MUL:
            case BinaryOpsConstants.INPLACE_MUL:
                IntBuiltins.MulNode mulNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.MulNodeFactory.MulNodeGen.class, NODE_INT_MUL);
                result = mulNode.execute(left, right);
                break;
            case BinaryOpsConstants.FLOORDIV:
            case BinaryOpsConstants.INPLACE_FLOORDIV:
                IntBuiltins.FloorDivNode floorDivNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.FloorDivNodeFactory.FloorDivNodeGen.class, NODE_INT_FLOORDIV);
                result = floorDivNode.execute(left, right);
                break;
            case BinaryOpsConstants.TRUEDIV:
            case BinaryOpsConstants.INPLACE_TRUEDIV:
                IntBuiltins.TrueDivNode trueDivNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.TrueDivNodeFactory.TrueDivNodeGen.class, NODE_INT_TRUEDIV);
                result = trueDivNode.execute(left, right);
                break;
            case BinaryOpsConstants.MOD:
            case BinaryOpsConstants.INPLACE_MOD:
                IntBuiltins.ModNode modNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.ModNodeFactory.ModNodeGen.class, NODE_INT_MOD);
                result = modNode.execute(left, right);
                break;
            case BinaryOpsConstants.LSHIFT:
            case BinaryOpsConstants.INPLACE_LSHIFT:
                IntBuiltins.LShiftNode lShiftNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.LShiftNodeFactory.LShiftNodeGen.class, NODE_INT_LSHIFT);
                result = lShiftNode.execute(left, right);
                break;
            case BinaryOpsConstants.RSHIFT:
            case BinaryOpsConstants.INPLACE_RSHIFT:
                IntBuiltins.RShiftNode rShiftNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.RShiftNodeFactory.RShiftNodeGen.class, NODE_INT_RSHIFT);
                result = rShiftNode.execute(left, right);
                break;
            case BinaryOpsConstants.POW:
            case BinaryOpsConstants.INPLACE_POW:
                IntBuiltins.PowNode powNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.PowNodeFactory.PowNodeGen.class, NODE_INT_POW);
                result = powNode.execute(left, right);
                break;
            case BinaryOpsConstants.AND:
            case BinaryOpsConstants.INPLACE_AND:
                result = left & right;
                break;
            case BinaryOpsConstants.OR:
            case BinaryOpsConstants.INPLACE_OR:
                result = left | right;
                break;
            case BinaryOpsConstants.XOR:
            case BinaryOpsConstants.INPLACE_XOR:
                result = left ^ right;
                break;
            case BinaryOpsConstants.IS:
            case BinaryOpsConstants.EQ:
                result = left == right;
                break;
            case BinaryOpsConstants.NE:
                result = left != right;
                break;
            case BinaryOpsConstants.LT:
                result = left < right;
                break;
            case BinaryOpsConstants.LE:
                result = left <= right;
                break;
            case BinaryOpsConstants.GT:
                result = left > right;
                break;
            case BinaryOpsConstants.GE:
                result = left >= right;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_II_O");
        }
        virtualFrame.setObject(stackTop, null);
        virtualFrame.setObject(stackTop - 1, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpIII(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, boolean useCachedNodes) {
        int right, left, result;
        if (virtualFrame.isInt(stackTop) && virtualFrame.isInt(stackTop - 1)) {
            right = virtualFrame.getInt(stackTop);
            left = virtualFrame.getInt(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        try {
            switch (op) {
                case BinaryOpsConstants.ADD:
                case BinaryOpsConstants.INPLACE_ADD:
                    try {
                        result = Math.addExact(left, right);
                    } catch (ArithmeticException e) {
                        generalizeBinaryOpIIIOverflow(virtualFrame, stackTop, bci, localNodes, op);
                        return;
                    }
                    break;
                case BinaryOpsConstants.SUB:
                case BinaryOpsConstants.INPLACE_SUB:
                    try {
                        result = Math.subtractExact(left, right);
                    } catch (ArithmeticException e) {
                        generalizeBinaryOpIIIOverflow(virtualFrame, stackTop, bci, localNodes, op);
                        return;
                    }
                    break;
                case BinaryOpsConstants.MUL:
                case BinaryOpsConstants.INPLACE_MUL:
                    try {
                        result = Math.multiplyExact(left, right);
                    } catch (ArithmeticException e) {
                        generalizeBinaryOpIIIOverflow(virtualFrame, stackTop, bci, localNodes, op);
                        return;
                    }
                    break;
                case BinaryOpsConstants.FLOORDIV:
                case BinaryOpsConstants.INPLACE_FLOORDIV:
                    if (left == Integer.MIN_VALUE && right == -1) {
                        generalizeBinaryOpIIIOverflow(virtualFrame, stackTop, bci, localNodes, op);
                        return;
                    }
                    if (right == 0) {
                        raiseDivOrModByZero(bci, localNodes, useCachedNodes);
                    }
                    result = Math.floorDiv(left, right);
                    break;
                case BinaryOpsConstants.MOD:
                case BinaryOpsConstants.INPLACE_MOD:
                    IntBuiltins.ModNode modNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.ModNodeFactory.ModNodeGen.class, NODE_INT_MOD);
                    result = modNode.executeInt(left, right);
                    break;
                case BinaryOpsConstants.LSHIFT:
                case BinaryOpsConstants.INPLACE_LSHIFT:
                    IntBuiltins.LShiftNode lShiftNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.LShiftNodeFactory.LShiftNodeGen.class, NODE_INT_LSHIFT);
                    result = lShiftNode.executeInt(left, right);
                    break;
                case BinaryOpsConstants.RSHIFT:
                case BinaryOpsConstants.INPLACE_RSHIFT:
                    IntBuiltins.RShiftNode rShiftNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.RShiftNodeFactory.RShiftNodeGen.class, NODE_INT_RSHIFT);
                    result = rShiftNode.executeInt(left, right);
                    break;
                case BinaryOpsConstants.AND:
                case BinaryOpsConstants.INPLACE_AND:
                    result = left & right;
                    break;
                case BinaryOpsConstants.OR:
                case BinaryOpsConstants.INPLACE_OR:
                    result = left | right;
                    break;
                case BinaryOpsConstants.XOR:
                case BinaryOpsConstants.INPLACE_XOR:
                    result = left ^ right;
                    break;
                case BinaryOpsConstants.POW:
                case BinaryOpsConstants.INPLACE_POW:
                    IntBuiltins.PowNode powNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.PowNodeFactory.PowNodeGen.class, NODE_INT_POW);
                    result = powNode.executeInt(left, right);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_II_I");
            }
        } catch (UnexpectedResultException e) {
            generalizeBinaryOpIIIOverflow(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        virtualFrame.setInt(stackTop - 1, result);
    }

    @InliningCutoff
    private void raiseDivOrModByZero(int bci, Node[] localNodes, boolean useCachedNodes) {
        PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, PRaiseNodeGen.class, NODE_RAISE, useCachedNodes);
        throw raiseNode.raise(ZeroDivisionError, ErrorMessages.S_DIVISION_OR_MODULO_BY_ZERO, "integer");
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpDDD(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, boolean useCachedNodes) {
        double right, left, result;
        if (virtualFrame.isDouble(stackTop) && virtualFrame.isDouble(stackTop - 1)) {
            right = virtualFrame.getDouble(stackTop);
            left = virtualFrame.getDouble(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        try {
            switch (op) {
                case BinaryOpsConstants.ADD:
                case BinaryOpsConstants.INPLACE_ADD:
                    result = left + right;
                    break;
                case BinaryOpsConstants.SUB:
                case BinaryOpsConstants.INPLACE_SUB:
                    result = left - right;
                    break;
                case BinaryOpsConstants.MUL:
                case BinaryOpsConstants.INPLACE_MUL:
                    result = left * right;
                    break;
                case BinaryOpsConstants.TRUEDIV:
                case BinaryOpsConstants.INPLACE_TRUEDIV:
                    if (right == 0.0) {
                        raiseDivByZero(bci, localNodes, useCachedNodes);
                    }
                    result = left / right;
                    break;
                case BinaryOpsConstants.POW:
                case BinaryOpsConstants.INPLACE_POW:
                    FloatBuiltins.PowNode powNode = insertChildNode(localNodes, bci, FloatBuiltinsFactory.PowNodeFactory.PowNodeGen.class, NODE_FLOAT_POW);
                    result = powNode.executeDouble(left, right);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_DD_D");
            }
        } catch (UnexpectedResultException e) {
            generalizeBinaryOpDDDOverflow(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
            return;
        }
        virtualFrame.setDouble(stackTop - 1, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpDDB(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        double right, left;
        if (virtualFrame.isDouble(stackTop) && virtualFrame.isDouble(stackTop - 1)) {
            right = virtualFrame.getDouble(stackTop);
            left = virtualFrame.getDouble(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        boolean result;
        switch (op) {
            case BinaryOpsConstants.EQ:
                result = left == right;
                break;
            case BinaryOpsConstants.NE:
                result = left != right;
                break;
            case BinaryOpsConstants.LT:
                result = left < right;
                break;
            case BinaryOpsConstants.LE:
                result = left <= right;
                break;
            case BinaryOpsConstants.GT:
                result = left > right;
                break;
            case BinaryOpsConstants.GE:
                result = left >= right;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_DD_B");
        }
        virtualFrame.setBoolean(stackTop - 1, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpDDO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, boolean useCachedNodes) {
        int right, left;
        if (virtualFrame.isInt(stackTop) && virtualFrame.isInt(stackTop - 1)) {
            right = virtualFrame.getInt(stackTop);
            left = virtualFrame.getInt(stackTop - 1);
        } else {
            generalizeBinaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        Object result;
        switch (op) {
            case BinaryOpsConstants.ADD:
            case BinaryOpsConstants.INPLACE_ADD:
                result = left + right;
                break;
            case BinaryOpsConstants.SUB:
            case BinaryOpsConstants.INPLACE_SUB:
                result = left - right;
                break;
            case BinaryOpsConstants.MUL:
            case BinaryOpsConstants.INPLACE_MUL:
                result = left * right;
                break;
            case BinaryOpsConstants.TRUEDIV:
            case BinaryOpsConstants.INPLACE_TRUEDIV:
                if (right == 0.0) {
                    raiseDivByZero(bci, localNodes, useCachedNodes);
                }
                result = left / right;
                break;
            case BinaryOpsConstants.POW:
            case BinaryOpsConstants.INPLACE_POW:
                FloatBuiltins.PowNode powNode = insertChildNode(localNodes, bci, FloatBuiltinsFactory.PowNodeFactory.PowNodeGen.class, NODE_FLOAT_POW);
                result = powNode.execute(left, right);
                break;
            case BinaryOpsConstants.IS:
            case BinaryOpsConstants.EQ:
                result = left == right;
                break;
            case BinaryOpsConstants.NE:
                result = left != right;
                break;
            case BinaryOpsConstants.LT:
                result = left < right;
                break;
            case BinaryOpsConstants.LE:
                result = left <= right;
                break;
            case BinaryOpsConstants.GT:
                result = left > right;
                break;
            case BinaryOpsConstants.GE:
                result = left >= right;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for BINARY_OP_DD_O");
        }
        virtualFrame.setObject(stackTop, null);
        virtualFrame.setObject(stackTop - 1, result);
    }

    @InliningCutoff
    private void raiseDivByZero(int bci, Node[] localNodes, boolean useCachedNodes) {
        PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, PRaiseNodeGen.class, NODE_RAISE, useCachedNodes);
        throw raiseNode.raise(ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
    }

    private void generalizeBinaryOp(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeFrameSlot(virtualFrame, stackTop);
        generalizeFrameSlot(virtualFrame, stackTop - 1);
        generalizeInputs(bci);
        bytecode[bci] = OpCodesConstants.BINARY_OP_OO_O;
        bytecodeBinaryOpOOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
    }

    private void generalizeBinaryOpIIIOverflow(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        bytecode[bci] = OpCodesConstants.BINARY_OP_II_O;
        bytecodeBinaryOpIIO(virtualFrame, stackTop, bci, localNodes, op);
    }

    private void generalizeBinaryOpDDDOverflow(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, boolean useCachedNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        bytecode[bci] = OpCodesConstants.BINARY_OP_DD_O;
        bytecodeBinaryOpDDO(virtualFrame, stackTop, bci, localNodes, op, useCachedNodes);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeBinaryOpOOO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, int bciSlot) {
        setCurrentBci(virtualFrame, bciSlot, bci);
        BinaryOp opNode = (BinaryOp) insertChildNodeInt(localNodes, bci, BinaryOp.class, BINARY_OP_FACTORY, op);
        Object right, left;
        try {
            right = virtualFrame.getObject(stackTop);
            left = virtualFrame.getObject(stackTop - 1);
        } catch (FrameSlotTypeException e) {
            right = generalizePopCondition(virtualFrame, stackTop, bci);
            left = virtualFrame.getValue(stackTop - 1);
        }
        virtualFrame.setObject(stackTop, null);
        Object result = opNode.executeObject(virtualFrame, left, right);
        virtualFrame.setObject(stackTop - 1, result);
    }

    private void bytecodeUnaryOpAdaptive(VirtualFrame virtualFrame, int stackTop, int bci, byte[] localBC, Node[] localNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int op = Byte.toUnsignedInt(localBC[bci + 1]);
        if (virtualFrame.isObject(stackTop)) {
            localBC[bci] = OpCodesConstants.UNARY_OP_O_O;
            bytecodeUnaryOpOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
            return;
        } else if (virtualFrame.isInt(stackTop)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                if (op == UnaryOpsConstants.NOT) {
                    // TODO UNARY_OP_I_B
                    localBC[bci] = OpCodesConstants.UNARY_OP_I_O;
                    bytecodeUnaryOpIO(virtualFrame, stackTop, bci, localNodes, op);
                } else {
                    localBC[bci] = OpCodesConstants.UNARY_OP_I_I;
                    bytecodeUnaryOpII(virtualFrame, stackTop, bci, localNodes, op);
                }
                return;
            }
            localBC[bci] = OpCodesConstants.UNARY_OP_I_O;
            bytecodeUnaryOpIO(virtualFrame, stackTop, bci, localNodes, op);
            return;
        } else if (virtualFrame.isDouble(stackTop)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                if (op == UnaryOpsConstants.NOT || op == UnaryOpsConstants.INVERT) {
                    // TODO UNARY_OP_D_B
                    localBC[bci] = OpCodesConstants.UNARY_OP_D_O;
                    bytecodeUnaryOpDO(virtualFrame, stackTop, bci, localNodes, op);
                } else {
                    localBC[bci] = OpCodesConstants.UNARY_OP_D_D;
                    bytecodeUnaryOpDD(virtualFrame, stackTop, bci, localNodes, op);
                }
                return;
            }
            localBC[bci] = OpCodesConstants.UNARY_OP_D_O;
            bytecodeUnaryOpIO(virtualFrame, stackTop, bci, localNodes, op);
            return;
        } else if (virtualFrame.isBoolean(stackTop)) {
            if (op == UnaryOpsConstants.NOT) {
                if ((outputCanQuicken[bci] & QuickeningTypes.BOOLEAN) != 0) {
                    localBC[bci] = OpCodesConstants.UNARY_OP_B_B;
                    bytecodeUnaryOpBB(virtualFrame, stackTop, bci, localNodes, op);
                } else {
                    localBC[bci] = OpCodesConstants.UNARY_OP_B_O;
                    bytecodeUnaryOpBO(virtualFrame, stackTop, bci, localNodes, op);
                }
                return;
            }
        }
        generalizeInputs(bci);
        generalizeFrameSlot(virtualFrame, stackTop);
        localBC[bci] = OpCodesConstants.UNARY_OP_O_O;
        bytecodeUnaryOpOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpII(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        int value;
        if (virtualFrame.isInt(stackTop)) {
            value = virtualFrame.getInt(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        switch (op) {
            case UnaryOpsConstants.POSITIVE:
                break;
            case UnaryOpsConstants.NEGATIVE:
                try {
                    virtualFrame.setInt(stackTop, Math.negateExact(value));
                } catch (ArithmeticException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    bytecode[bci] = OpCodesConstants.UNARY_OP_I_O;
                    bytecodeUnaryOpIO(virtualFrame, stackTop, bci, localNodes, op);
                    return;
                }
                break;
            case UnaryOpsConstants.INVERT:
                virtualFrame.setInt(stackTop, ~value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_I_I");
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpIO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        int value;
        if (virtualFrame.isInt(stackTop)) {
            value = virtualFrame.getInt(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        Object result;
        switch (op) {
            case UnaryOpsConstants.NOT:
                result = value == 0;
                break;
            case UnaryOpsConstants.POSITIVE:
                result = value;
                break;
            case UnaryOpsConstants.NEGATIVE:
                IntBuiltins.NegNode negNode = insertChildNode(localNodes, bci, IntBuiltinsFactory.NegNodeFactory.NegNodeGen.class, NODE_INT_NEG);
                result = negNode.execute(value);
                break;
            case UnaryOpsConstants.INVERT:
                result = ~value;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_I_O");
        }
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpDD(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        double value;
        if (virtualFrame.isDouble(stackTop)) {
            value = virtualFrame.getDouble(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        switch (op) {
            case UnaryOpsConstants.POSITIVE:
                break;
            case UnaryOpsConstants.NEGATIVE:
                virtualFrame.setDouble(stackTop, -value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_D_D");
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpDO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        double value;
        if (virtualFrame.isDouble(stackTop)) {
            value = virtualFrame.getDouble(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        Object result;
        switch (op) {
            case UnaryOpsConstants.NOT:
                result = value == 0;
                break;
            case UnaryOpsConstants.POSITIVE:
                result = value;
                break;
            case UnaryOpsConstants.NEGATIVE:
                result = -value;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_D_O");
        }
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpBB(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        boolean value;
        if (virtualFrame.isBoolean(stackTop)) {
            value = virtualFrame.getBoolean(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        if (op == UnaryOpsConstants.NOT) {
            virtualFrame.setBoolean(stackTop, !value);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_B_B");
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpBO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        boolean value;
        if (virtualFrame.isBoolean(stackTop)) {
            value = virtualFrame.getBoolean(stackTop);
        } else {
            generalizeUnaryOp(virtualFrame, stackTop, bci, localNodes, op);
            return;
        }
        if (op == UnaryOpsConstants.NOT) {
            virtualFrame.setObject(stackTop, !value);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Invalid operation for UNARY_OP_B_B");
        }
    }

    private void generalizeUnaryOp(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeFrameSlot(virtualFrame, stackTop);
        generalizeInputs(bci);
        bytecode[bci] = OpCodesConstants.UNARY_OP_O_O;
        bytecodeUnaryOpOO(virtualFrame, stackTop, bci, localNodes, op, bcioffset);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeUnaryOpOO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int op, int bciSlot) {
        setCurrentBci(virtualFrame, bciSlot, bci);
        UnaryOpNode opNode = insertChildNodeInt(localNodes, bci, UnaryOpNode.class, UNARY_OP_FACTORY, op);
        Object value;
        try {
            value = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context
            // mode
            generalizeInputs(bci);
            value = virtualFrame.getValue(stackTop);
        }
        Object result = opNode.executeCached(virtualFrame, value);
        virtualFrame.setObject(stackTop, result);
    }

    private void bytecodeStoreFastAdaptive(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, byte[] localBC, int index, boolean hasUnboxedLocals) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        byte stackType = stackSlotTypeToTypeId(virtualFrame, stackTop);
        byte itemType = stackType;
        boolean unboxInIntepreter = (variableShouldUnbox[index] & itemType) != 0;
        if (itemType == QuickeningTypes.OBJECT) {
            itemType = QuickeningTypes.fromObjectType(virtualFrame.getObject(stackTop));
        }
        byte variableType = variableTypes[index];
        if (variableType == 0) {
            variableType = itemType;
        } else if ((variableType & ~UNBOXED_IN_INTERPRETER) != itemType) {
            if (variableType != QuickeningTypes.OBJECT) {
                variableTypes[index] = QuickeningTypes.OBJECT;
                generalizeVariableStores(index);
            }
            if (itemType != QuickeningTypes.OBJECT) {
                generalizeInputs(bci);
                generalizeFrameSlot(virtualFrame, stackTop);
            }
            localBC[bci] = OpCodesConstants.STORE_FAST_O;
            bytecodeStoreFastO(virtualFrame, localFrame, stackTop, index);
            return;
        }
        if (itemType == QuickeningTypes.INT) {
            if (unboxInIntepreter && stackType == QuickeningTypes.INT) {
                localBC[bci] = OpCodesConstants.STORE_FAST_I;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastI(virtualFrame, localFrame, stackTop, bci, index);
            } else if (unboxInIntepreter) {
                localBC[bci] = OpCodesConstants.STORE_FAST_UNBOX_I;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastUnboxI(virtualFrame, localFrame, stackTop, bci, index);
            } else {
                variableTypes[index] = variableType;
                if (stackType == QuickeningTypes.INT) {
                    virtualFrame.setObject(stackTop, virtualFrame.getInt(stackTop));
                    generalizeInputs(bci);
                }
                localBC[bci] = OpCodesConstants.STORE_FAST_BOXED_I;
                bytecodeStoreFastBoxedI(virtualFrame, localFrame, stackTop, bci, index, hasUnboxedLocals);
            }
            return;
        } else if (itemType == QuickeningTypes.LONG) {
            if (unboxInIntepreter && stackType == QuickeningTypes.LONG) {
                localBC[bci] = OpCodesConstants.STORE_FAST_L;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastL(virtualFrame, localFrame, stackTop, bci, index);
            } else if (unboxInIntepreter) {
                localBC[bci] = OpCodesConstants.STORE_FAST_UNBOX_L;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastUnboxL(virtualFrame, localFrame, stackTop, bci, index);
            } else {
                variableTypes[index] = variableType;
                if (stackType == QuickeningTypes.LONG) {
                    virtualFrame.setObject(stackTop, virtualFrame.getLong(stackTop));
                    generalizeInputs(bci);
                }
                localBC[bci] = OpCodesConstants.STORE_FAST_BOXED_L;
                bytecodeStoreFastBoxedL(virtualFrame, localFrame, stackTop, bci, index, hasUnboxedLocals);
            }
            return;
        } else if (itemType == QuickeningTypes.DOUBLE) {
            if (unboxInIntepreter && stackType == QuickeningTypes.DOUBLE) {
                localBC[bci] = OpCodesConstants.STORE_FAST_D;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastD(virtualFrame, localFrame, stackTop, bci, index);
            } else if (unboxInIntepreter) {
                localBC[bci] = OpCodesConstants.STORE_FAST_UNBOX_D;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastUnboxD(virtualFrame, localFrame, stackTop, bci, index);
            } else {
                variableTypes[index] = variableType;
                if (stackType == QuickeningTypes.DOUBLE) {
                    virtualFrame.setObject(stackTop, virtualFrame.getDouble(stackTop));
                    generalizeInputs(bci);
                }
                localBC[bci] = OpCodesConstants.STORE_FAST_BOXED_D;
                bytecodeStoreFastBoxedD(virtualFrame, localFrame, stackTop, bci, index, hasUnboxedLocals);
            }
            return;
        } else if (itemType == QuickeningTypes.BOOLEAN) {
            if (unboxInIntepreter && stackType == QuickeningTypes.BOOLEAN) {
                localBC[bci] = OpCodesConstants.STORE_FAST_B;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastB(virtualFrame, localFrame, stackTop, bci, index);
            } else if (unboxInIntepreter) {
                localBC[bci] = OpCodesConstants.STORE_FAST_UNBOX_B;
                variableType |= UNBOXED_IN_INTERPRETER;
                variableTypes[index] = variableType;
                bytecodeStoreFastUnboxB(virtualFrame, localFrame, stackTop, bci, index);
            } else {
                variableTypes[index] = variableType;
                if (stackType == QuickeningTypes.BOOLEAN) {
                    virtualFrame.setObject(stackTop, virtualFrame.getBoolean(stackTop));
                    generalizeInputs(bci);
                }
                localBC[bci] = OpCodesConstants.STORE_FAST_BOXED_B;
                bytecodeStoreFastBoxedB(virtualFrame, localFrame, stackTop, bci, index, hasUnboxedLocals);
            }
            return;
        } else if (itemType == QuickeningTypes.OBJECT) {
            variableTypes[index] = variableType;
            localBC[bci] = OpCodesConstants.STORE_FAST_O;
            bytecodeStoreFastO(virtualFrame, localFrame, stackTop, index);
            return;
        }
        throw CompilerDirectives.shouldNotReachHere("Unexpected variable type: " + itemType);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastI(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        if (virtualFrame.isInt(stackTop)) {
            localFrame.setInt(index, virtualFrame.getInt(stackTop));
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastUnboxI(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (object instanceof Integer) {
            localFrame.setInt(index, (int) object);
            virtualFrame.setObject(stackTop, null);
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastBoxedI(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, boolean hasUnboxedLocals) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (!(object instanceof Integer)) {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (hasUnboxedLocals) {
            localFrame.setInt(index, (int) object);
        } else {
            localFrame.setObject(index, object);
        }
        virtualFrame.setObject(stackTop, null);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastL(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        if (virtualFrame.isLong(stackTop)) {
            localFrame.setLong(index, virtualFrame.getLong(stackTop));
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastUnboxL(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (object instanceof Long) {
            localFrame.setLong(index, (long) object);
            virtualFrame.setObject(stackTop, null);
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastBoxedL(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, boolean hasUnboxedLocals) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (!(object instanceof Long)) {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (hasUnboxedLocals) {
            localFrame.setLong(index, (long) object);
        } else {
            localFrame.setObject(index, object);
        }
        virtualFrame.setObject(stackTop, null);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastD(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        if (virtualFrame.isDouble(stackTop)) {
            localFrame.setDouble(index, virtualFrame.getDouble(stackTop));
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastUnboxD(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (object instanceof Double) {
            localFrame.setDouble(index, (double) object);
            virtualFrame.setObject(stackTop, null);
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastBoxedD(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, boolean hasUnboxedLocals) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (!(object instanceof Double)) {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (hasUnboxedLocals) {
            localFrame.setDouble(index, (double) object);
        } else {
            localFrame.setObject(index, object);
        }
        virtualFrame.setObject(stackTop, null);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastB(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        if (virtualFrame.isBoolean(stackTop)) {
            localFrame.setBoolean(index, virtualFrame.getBoolean(stackTop));
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastUnboxB(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (object instanceof Boolean) {
            localFrame.setBoolean(index, (boolean) object);
            virtualFrame.setObject(stackTop, null);
        } else {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastBoxedB(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, boolean hasUnboxedLocals) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (!(object instanceof Boolean)) {
            generalizeStoreFast(virtualFrame, localFrame, stackTop, bci, index);
            return;
        }
        if (hasUnboxedLocals) {
            localFrame.setBoolean(index, (boolean) object);
        } else {
            localFrame.setObject(index, object);
        }
        virtualFrame.setObject(stackTop, null);
    }

    private void generalizeStoreFast(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeFrameSlot(virtualFrame, index);
        generalizeInputs(index);
        bytecode[bci] = OpCodesConstants.STORE_FAST_O;
        generalizeVariableStores(index);
        bytecodeStoreFastO(virtualFrame, localFrame, stackTop, index);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeStoreFastO(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int index) {
        Object object;
        try {
            object = virtualFrame.getObject(stackTop);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context mode
            generalizeVariableStores(index);
            object = virtualFrame.getValue(stackTop);
        }
        localFrame.setObject(index, object);
        virtualFrame.setObject(stackTop, null);
    }

    @InliningCutoff
    private void bytecodeLoadFastAdaptive(VirtualFrame virtualFrame, Frame localFrame, int stackTop, byte[] localBC, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (localFrame.isObject(index)) {
            localBC[bci] = OpCodesConstants.LOAD_FAST_O;
            bytecodeLoadFastO(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        } else if (localFrame.isInt(index)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.INT) != 0) {
                localBC[bci] = OpCodesConstants.LOAD_FAST_I;
                bytecodeLoadFastI(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            } else {
                localBC[bci] = OpCodesConstants.LOAD_FAST_I_BOX;
                bytecodeLoadFastIBox(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            }
        } else if (localFrame.isLong(index)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.LONG) != 0) {
                localBC[bci] = OpCodesConstants.LOAD_FAST_L;
                bytecodeLoadFastL(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            } else {
                localBC[bci] = OpCodesConstants.LOAD_FAST_L_BOX;
                bytecodeLoadFastLBox(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            }
        } else if (localFrame.isDouble(index)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.DOUBLE) != 0) {
                localBC[bci] = OpCodesConstants.LOAD_FAST_D;
                bytecodeLoadFastD(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            } else {
                localBC[bci] = OpCodesConstants.LOAD_FAST_D_BOX;
                bytecodeLoadFastDBox(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            }
        } else if (localFrame.isBoolean(index)) {
            if ((outputCanQuicken[bci] & QuickeningTypes.BOOLEAN) != 0) {
                localBC[bci] = OpCodesConstants.LOAD_FAST_B;
                bytecodeLoadFastB(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            } else {
                localBC[bci] = OpCodesConstants.LOAD_FAST_B_BOX;
                bytecodeLoadFastBBox(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere("Unimplemented stack item type for LOAD_FAST");
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadFastIBox(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isInt(index)) {
            virtualFrame.setObject(stackTop, localFrame.getInt(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadFastI(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isInt(index)) {
            virtualFrame.setInt(stackTop, localFrame.getInt(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    private void bytecodeLoadFastLBox(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isLong(index)) {
            virtualFrame.setObject(stackTop, localFrame.getLong(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    private void bytecodeLoadFastL(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isLong(index)) {
            virtualFrame.setLong(stackTop, localFrame.getLong(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    private void bytecodeLoadFastDBox(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isDouble(index)) {
            virtualFrame.setObject(stackTop, localFrame.getDouble(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    private void bytecodeLoadFastD(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isDouble(index)) {
            virtualFrame.setDouble(stackTop, localFrame.getDouble(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadFastBBox(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isBoolean(index)) {
            virtualFrame.setObject(stackTop, localFrame.getBoolean(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadFastB(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        if (localFrame.isBoolean(index)) {
            virtualFrame.setBoolean(stackTop, localFrame.getBoolean(index));
        } else {
            generalizeLoadFast(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
        }
    }

    private void generalizeLoadFast(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeVariableStores(index);
        generalizeFrameSlot(virtualFrame, index);
        bytecode[bci] = OpCodesConstants.LOAD_FAST_O;
        bytecodeLoadFastO(virtualFrame, localFrame, stackTop, bci, index, localNodes, hasUnboxedLocals);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadFastO(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, int index, Node[] localNodes, boolean hasUnboxedLocals) {
        Object value;
        try {
            if (hasUnboxedLocals) {
                if (variableTypes[index] == QuickeningTypes.INT) {
                    virtualFrame.setObject(stackTop, virtualFrame.getInt(index));
                    return;
                } else if (variableTypes[index] == QuickeningTypes.LONG) {
                    virtualFrame.setObject(stackTop, virtualFrame.getLong(index));
                    return;
                } else if (variableTypes[index] == QuickeningTypes.DOUBLE) {
                    virtualFrame.setObject(stackTop, virtualFrame.getDouble(index));
                    return;
                } else if (variableTypes[index] == QuickeningTypes.BOOLEAN) {
                    virtualFrame.setObject(stackTop, virtualFrame.getBoolean(index));
                    return;
                }
            }
            value = localFrame.getObject(index);
        } catch (FrameSlotTypeException e) {
            // This should only happen when quickened concurrently in multi-context
            // mode
            value = generalizeBytecodeLoadFastO(localFrame, index);
        }
        if (value == null) {
            throw raiseVarReferencedBeforeAssignment(localNodes, bci, index);
        }
        virtualFrame.setObject(stackTop, value);
    }

    @InliningCutoff
    private PException raiseVarReferencedBeforeAssignment(Node[] localNodes, int bci, int index) {
        PRaiseNode raiseNode = insertChildNode(localNodes, bci, PRaiseNodeGen.class, NODE_RAISE);
        throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[index]);
    }

    private Object generalizeBytecodeLoadFastO(Frame localFrame, int index) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeVariableStores(index);
        generalizeFrameSlot(localFrame, index);
        return localFrame.getValue(index);
    }

    private static byte stackSlotTypeToTypeId(VirtualFrame virtualFrame, int stackTop) {
        return QuickeningTypes.fromFrameSlotTag(virtualFrame.getTag(stackTop));
    }

    private void generalizeInputs(int beginBci) {
        CompilerAsserts.neverPartOfCompilation();
        if (generalizeInputsMap != null) {
            if (generalizeInputsMap[beginBci] != null) {
                for (int i = 0; i < generalizeInputsMap[beginBci].length; i++) {
                    int generalizeBci = generalizeInputsMap[beginBci][i];
                    OpCodes generalizeInstr = OpCodes.fromOpCode(bytecode[generalizeBci]);
                    if (generalizeInstr.generalizesTo != null) {
                        bytecode[generalizeBci] = (byte) generalizeInstr.generalizesTo.ordinal();
                    }
                }
            }
        }
    }

    /**
     * The caller should ensure that the frame slot is assigned the boxed Object value to avoid
     * repeated FrameSlotTypeException. This can happen in combination with OSR: if there are
     * multiple OSR bytecode loop invocations that have different view on whether the local
     * variables are boxed or unboxed in the frame, but they all share the same frame.
     * <p>
     * For example, when we enter compiled bytecode loop, variable "hasUnboxedLocals" that captures
     * CompilerDirectives.inCompiledCode() at the beginning of the bytecode loop is true and frame
     * slots are unboxed. If we happen to deoptimize during the bytecode loop, then
     * "hasUnboxedLocals" variable is still true (although CompilerDirectives.inCompiledCode() would
     * return false) and we will work with unboxed locals even in the interpreter. However, we may
     * call tryOSR from the interpreter, and invoke another bytecode loop. If we are unlucky, the
     * OST target will deopt in the meantime, and we'll enter the OSR bytecode loop in interpreter,
     * so "hasUnboxedLocals" will be false, but the frame will have unboxed locals.
     * <p>
     * We could pass the "hasUnboxedLocals" flag down in the OSR state, but it is not worth it for
     * such a corner case.
     */
    private void generalizeVariableStores(int index) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        variableTypes[index] = QuickeningTypes.OBJECT;
        if (generalizeVarsMap != null) {
            if (generalizeVarsMap[index] != null) {
                for (int i = 0; i < generalizeVarsMap[index].length; i++) {
                    int generalizeBci = generalizeVarsMap[index][i];
                    /*
                     * Keep unadapted stores as they are because we don't know how to generalize
                     * their unadapted inputs. They will adapt to object once executed.
                     */
                    if (bytecode[generalizeBci] != OpCodesConstants.STORE_FAST) {
                        generalizeInputs(generalizeBci);
                        bytecode[generalizeBci] = OpCodesConstants.STORE_FAST_O;
                    }
                }
            }
        }
    }

    @InliningCutoff
    protected PException wrapJavaExceptionIfApplicable(Throwable e) {
        if (e instanceof AbstractTruffleException) {
            return null;
        }
        if (e instanceof ControlFlowException) {
            return null;
        }
        if (PythonLanguage.get(this).getEngineOption(PythonOptions.CatchAllExceptions) && (e instanceof Exception || e instanceof AssertionError)) {
            return ExceptionUtils.wrapJavaException(e, this, factory.createBaseException(SystemError, ErrorMessages.M, new Object[]{e}));
        }
        if (e instanceof StackOverflowError) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PythonContext.get(this).reacquireGilAfterStackOverflow();
            return ExceptionUtils.wrapJavaException(e, this, factory.createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{}));
        }
        return null;
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

    @InliningCutoff
    private int bytecodeFormatValue(VirtualFrame virtualFrame, int initialStackTop, int bci, Node[] localNodes, int options, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        int type = options & FormatOptions.FVC_MASK;
        Object spec = PNone.NO_VALUE;
        if ((options & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC) {
            spec = virtualFrame.getObject(stackTop);
            virtualFrame.setObject(stackTop--, null);
        }
        Object value = virtualFrame.getObject(stackTop);
        switch (type) {
            case FormatOptions.FVC_STR:
                value = insertChildNode(localNodes, bci, UNCACHED_STR, PyObjectStrAsObjectNodeGen.class, NODE_STR, useCachedNodes).executeCached(virtualFrame, value);
                break;
            case FormatOptions.FVC_REPR:
                value = insertChildNode(localNodes, bci, UNCACHED_REPR, PyObjectReprAsObjectNodeGen.class, NODE_REPR, useCachedNodes).executeCached(virtualFrame, value);
                break;
            case FormatOptions.FVC_ASCII:
                value = insertChildNode(localNodes, bci, UNCACHED_ASCII, PyObjectAsciiNodeGen.class, NODE_ASCII, useCachedNodes).executeCached(virtualFrame, value);
                break;
            default:
                assert type == FormatOptions.FVC_NONE;
        }
        FormatNode formatNode = insertChildNode(localNodes, bci + 1, FormatNodeGen.class, NODE_FORMAT);
        value = formatNode.execute(virtualFrame, value, spec);
        virtualFrame.setObject(stackTop, value);
        return stackTop;
    }

    private void bytecodeDeleteDeref(Frame localFrame, int bci, Node[] localNodes, int oparg, int cachedCelloffset, boolean useCachedNodes) {
        PCell cell = (PCell) localFrame.getObject(cachedCelloffset + oparg);
        Object value = cell.getRef();
        if (value == null) {
            raiseUnboundCell(localNodes, bci, oparg, useCachedNodes);
        }
        cell.clearRef();
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreDeref(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int oparg, int cachedCelloffset) {
        PCell cell = (PCell) localFrame.getObject(cachedCelloffset + oparg);
        Object value = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        cell.setRef(value);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeLoadClassDeref(VirtualFrame virtualFrame, Frame localFrame, Object locals, int stackTop, int bci, Node[] localNodes, int oparg, int cachedCelloffset, boolean useCachedNodes) {
        TruffleString varName = freevars[oparg - cellvars.length];
        ReadFromLocalsNode readFromLocals = insertChildNode(localNodes, bci, UNCACHED_READ_FROM_LOCALS, ReadFromLocalsNodeGen.class, NODE_READ_FROM_LOCALS, useCachedNodes);
        Object value = readFromLocals.executeCached(virtualFrame, locals, varName);
        if (value != PNone.NO_VALUE) {
            virtualFrame.setObject(++stackTop, value);
            return stackTop;
        } else {
            return bytecodeLoadDeref(virtualFrame, localFrame, stackTop, bci, localNodes, oparg, cachedCelloffset, useCachedNodes);
        }
    }

    @BytecodeInterpreterSwitch
    private int bytecodeLoadDeref(VirtualFrame virtualFrame, Frame localFrame, int stackTop, int bci, Node[] localNodes, int oparg, int cachedCelloffset, boolean useCachedNodes) {
        PCell cell = (PCell) localFrame.getObject(cachedCelloffset + oparg);
        Object value = cell.getRef();
        if (value == null) {
            raiseUnboundCell(localNodes, bci, oparg, useCachedNodes);
        }
        virtualFrame.setObject(++stackTop, value);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeClosureFromStack(VirtualFrame virtualFrame, int stackTop, int oparg) {
        PCell[] closure = new PCell[oparg];
        moveFromStack(virtualFrame, stackTop - oparg + 1, stackTop + 1, closure);
        stackTop -= oparg - 1;
        virtualFrame.setObject(stackTop, closure);
        return stackTop;
    }

    private PException popExceptionState(Object[] arguments, Object savedException, PException outerException) {
        PException localException = null;
        if (savedException instanceof PException) {
            localException = (PException) savedException;
            PArguments.setException(arguments, localException);
        } else if (savedException == null) {
            PArguments.setException(arguments, outerException);
        }
        return localException;
    }

    private PException bytecodeEndExcHandler(VirtualFrame virtualFrame, int stackTop) {
        Object exception = virtualFrame.getObject(stackTop);
        if (exception instanceof PException) {
            throw ((PException) exception).getExceptionForReraise(frameIsVisibleToPython());
        } else if (exception instanceof AbstractTruffleException) {
            throw (AbstractTruffleException) exception;
        } else {
            throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
        }
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadAttr(VirtualFrame virtualFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        PyObjectGetAttr getAttr = insertChildNode(localNodes, bci, UNCACHED_OBJECT_GET_ATTR, PyObjectGetAttrNodeGen.class, NODE_OBJECT_GET_ATTR, useCachedNodes);
        TruffleString varname = localNames[oparg];
        Object owner = virtualFrame.getObject(stackTop);
        Object value = getAttr.executeCached(virtualFrame, owner, varname);
        virtualFrame.setObject(stackTop, value);
    }

    private void bytecodeDeleteFast(Frame localFrame, int bci, Node[] localNodes, int oparg, boolean useCachedNodes) {
        if (localFrame.isObject(oparg)) {
            Object value = localFrame.getObject(oparg);
            if (value == null) {
                raiseVarReferencedBeforeAssignment(localNodes, bci, oparg);
            }
        } else {
            generalizeVariableStores(oparg);
        }
        localFrame.setObject(oparg, null);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeLoadGlobal(VirtualFrame virtualFrame, Object globals, int stackTop, int bci, TruffleString localName, Node[] localNodes, boolean useCachedNodes) {
        ReadGlobalOrBuiltinNode read = insertChildNode(localNodes, bci, UNCACHED_READ_GLOBAL_OR_BUILTIN, ReadGlobalOrBuiltinNodeGen.class, NODE_READ_GLOBAL_OR_BUILTIN, useCachedNodes);
        virtualFrame.setObject(++stackTop, read.read(virtualFrame, globals, localName));
        return stackTop;
    }

    @InliningCutoff
    private void bytecodeDeleteGlobal(VirtualFrame virtualFrame, Object globals, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        TruffleString varname = localNames[oparg];
        DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes, bci, UNCACHED_DELETE_GLOBAL, DeleteGlobalNodeGen.class, NODE_DELETE_GLOBAL, useCachedNodes);
        deleteGlobalNode.executeWithGlobals(virtualFrame, globals, varname);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreGlobal(VirtualFrame virtualFrame, Object globals, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        TruffleString varname = localNames[oparg];
        WriteGlobalNode writeGlobalNode = insertChildNode(localNodes, bci, UNCACHED_WRITE_GLOBAL, WriteGlobalNodeGen.class, NODE_WRITE_GLOBAL, useCachedNodes);
        writeGlobalNode.write(virtualFrame, globals, varname, virtualFrame.getObject(stackTop));
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private int bytecodeDeleteAttr(VirtualFrame virtualFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        PyObjectSetAttr callNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ATTR, PyObjectSetAttrNodeGen.class, NODE_OBJECT_SET_ATTR, useCachedNodes);
        TruffleString varname = localNames[oparg];
        Object owner = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        callNode.deleteCached(virtualFrame, owner, varname);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreAttr(VirtualFrame virtualFrame, int stackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        PyObjectSetAttr callNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ATTR, PyObjectSetAttrNodeGen.class, NODE_OBJECT_SET_ATTR, useCachedNodes);
        TruffleString varname = localNames[oparg];
        Object owner = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object value = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        callNode.executeCached(virtualFrame, owner, varname, value);
        return stackTop;
    }

    private void bytecodeDeleteName(VirtualFrame virtualFrame, Object globals, Object locals, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        // ceval.c: TARGET(DELETE_NAME)
        TruffleString varname = localNames[oparg];
        if (locals != null) {
            PyObjectDelItem delItemNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_DEL_ITEM, PyObjectDelItemNodeGen.class, NODE_OBJECT_DEL_ITEM, useCachedNodes);
            try {
                delItemNode.executeCached(virtualFrame, locals, varname);
            } catch (PException e) {
                PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, PRaiseNodeGen.class, NODE_RAISE, useCachedNodes);
                throw raiseNode.raise(NameError, ErrorMessages.NAME_NOT_DEFINED, varname);
            }
        } else {
            DeleteGlobalNode deleteGlobalNode = insertChildNode(localNodes, bci + 1, UNCACHED_DELETE_GLOBAL, DeleteGlobalNodeGen.class, NODE_DELETE_GLOBAL, useCachedNodes);
            deleteGlobalNode.executeWithGlobals(virtualFrame, globals, varname);
        }
    }

    private int bytecodeDeleteSubscr(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        PyObjectDelItem delItem = insertChildNode(localNodes, bci, UNCACHED_OBJECT_DEL_ITEM, PyObjectDelItemNodeGen.class, NODE_OBJECT_DEL_ITEM, useCachedNodes);
        Object slice = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object container = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        delItem.executeCached(virtualFrame, container, slice);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreSubscrAdaptive(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes, int bciSlot) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isInt(stackTop) && virtualFrame.getObject(stackTop - 1) instanceof PList) {
            if (virtualFrame.isInt(stackTop - 2)) {
                bytecode[bci] = OpCodesConstants.STORE_SUBSCR_SEQ_IIO;
                return bytecodeStoreSubscrSeqIIO(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
            } else if (virtualFrame.isDouble(stackTop - 2)) {
                bytecode[bci] = OpCodesConstants.STORE_SUBSCR_SEQ_IDO;
                return bytecodeStoreSubscrSeqIDO(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
            } else {
                bytecode[bci] = OpCodesConstants.STORE_SUBSCR_SEQ_IOO;
                return bytecodeStoreSubscrSeqIOO(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
            }
        }
        if (!virtualFrame.isObject(stackTop) || !virtualFrame.isObject(stackTop - 2)) {
            generalizeInputs(bci);
            generalizeFrameSlot(virtualFrame, stackTop);
            generalizeFrameSlot(virtualFrame, stackTop - 2);
        }
        bytecode[bci] = OpCodesConstants.STORE_SUBSCR_OOO;
        return bytecodeStoreSubscrOOO(virtualFrame, stackTop, bci, localNodes, useCachedNodes, bciSlot);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreSubscrOOO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes, int bciSlot) {
        setCurrentBci(virtualFrame, bciSlot, bci);
        PyObjectSetItem setItem = insertChildNode(localNodes, bci, UNCACHED_OBJECT_SET_ITEM, PyObjectSetItemNodeGen.class, NODE_OBJECT_SET_ITEM, useCachedNodes);
        try {
            Object index = virtualFrame.getObject(stackTop);
            Object container = virtualFrame.getObject(stackTop - 1);
            Object value = virtualFrame.getObject(stackTop - 2);
            setItem.executeCached(virtualFrame, container, index, value);
        } catch (FrameSlotTypeException e) {
            // Should only happen in multi-context mode
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreSubscrSeqIOO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object container, value;
        try {
            container = virtualFrame.getObject(stackTop - 1);
            value = virtualFrame.getObject(stackTop - 2);
        } catch (FrameSlotTypeException e) {
            // Should only happen in multi-context mode
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        try {
            StoreSubscrSeq.ONode setItem = insertChildNode(localNodes, bci, StoreSubscrSeqFactory.ONodeGen.class, NODE_STORE_SUBSCR_SEQ_O);
            setItem.execute(container, index, value);
        } catch (QuickeningGeneralizeException e) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreSubscrSeqIIO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        if (!virtualFrame.isInt(stackTop - 2)) {
            return generalizeStoreSubscrSeq(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object container = virtualFrame.getObject(stackTop - 1);
        int value = virtualFrame.getInt(stackTop - 2);
        try {
            StoreSubscrSeq.INode setItem = insertChildNode(localNodes, bci, StoreSubscrSeqFactory.INodeGen.class, NODE_STORE_SUBSCR_SEQ_I);
            setItem.execute(container, index, value);
        } catch (QuickeningGeneralizeException e) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreSubscrSeqIDO(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        if (!virtualFrame.isInt(stackTop - 2)) {
            return generalizeStoreSubscrSeq(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        if (!virtualFrame.isInt(stackTop)) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        int index = virtualFrame.getInt(stackTop);
        Object container = virtualFrame.getObject(stackTop - 1);
        double value = virtualFrame.getDouble(stackTop - 2);
        try {
            StoreSubscrSeq.DNode setItem = insertChildNode(localNodes, bci, StoreSubscrSeqFactory.DNodeGen.class, NODE_STORE_SUBSCR_SEQ_D);
            setItem.execute(container, index, value);
        } catch (QuickeningGeneralizeException e) {
            return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    private int generalizeStoreSubscrSeq(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isInt(stackTop)) {
            generalizeFrameSlot(virtualFrame, stackTop - 2);
            bytecode[bci] = OpCodesConstants.STORE_SUBSCR_SEQ_IOO;
            return bytecodeStoreSubscrSeqIOO(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
        }
        return generalizeStoreSubscr(virtualFrame, stackTop, bci, localNodes, useCachedNodes);
    }

    private int generalizeStoreSubscr(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, boolean useCachedNodes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        generalizeInputs(bci);
        bytecode[bci] = OpCodesConstants.STORE_SUBSCR_OOO;
        generalizeFrameSlot(virtualFrame, stackTop);
        generalizeFrameSlot(virtualFrame, stackTop - 2);
        return bytecodeStoreSubscrOOO(virtualFrame, stackTop, bci, localNodes, useCachedNodes, bcioffset);
    }

    private void generalizeFrameSlot(Frame frame, int slot) {
        if (!frame.isObject(slot)) {
            frame.setObject(slot, frame.getValue(slot));
        }
    }

    @BytecodeInterpreterSwitch
    private int bytecodeBuildSlice(VirtualFrame virtualFrame, int stackTop, int bci, int count, Node[] localNodes, boolean useCachedNodes) {
        Object step;
        if (count == 3) {
            step = virtualFrame.getObject(stackTop);
            virtualFrame.setObject(stackTop--, null);
        } else {
            assert count == 2;
            step = PNone.NONE;
        }
        Object stop = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object start = virtualFrame.getObject(stackTop);
        CreateSliceNode sliceNode = insertChildNode(localNodes, bci, UNCACHED_CREATE_SLICE, CreateSliceNodeGen.class, NODE_CREATE_SLICE, useCachedNodes);
        PSlice slice = sliceNode.execute(start, stop, step);
        virtualFrame.setObject(stackTop, slice);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private void bytecodeLoadConstCollection(VirtualFrame virtualFrame, int stackTop, Object array, int typeAndKind) {
        Object result;
        SequenceStorage storage;
        int kind = CollectionBits.collectionKind(typeAndKind);
        assert kind == CollectionBits.KIND_LIST || kind == CollectionBits.KIND_TUPLE;
        boolean list = kind == CollectionBits.KIND_LIST;
        switch (CollectionBits.elementType(typeAndKind)) {
            case CollectionBits.ELEMENT_INT: {
                int[] a = (int[]) array;
                if (list) {
                    a = PythonUtils.arrayCopyOf(a, a.length);
                }
                storage = new IntSequenceStorage(a);
                break;
            }
            case CollectionBits.ELEMENT_LONG: {
                long[] a = (long[]) array;
                if (list) {
                    a = PythonUtils.arrayCopyOf(a, a.length);
                }
                storage = new LongSequenceStorage(a);
                break;
            }
            case CollectionBits.ELEMENT_BOOLEAN: {
                boolean[] a = (boolean[]) array;
                if (list) {
                    a = PythonUtils.arrayCopyOf(a, a.length);
                }
                storage = new BoolSequenceStorage(a);
                break;
            }
            case CollectionBits.ELEMENT_DOUBLE: {
                double[] a = (double[]) array;
                if (list) {
                    a = PythonUtils.arrayCopyOf(a, a.length);
                }
                storage = new DoubleSequenceStorage(a);
                break;
            }
            case CollectionBits.ELEMENT_OBJECT: {
                Object[] a = (Object[]) array;
                if (list) {
                    a = PythonUtils.arrayCopyOf(a, a.length);
                }
                storage = new ObjectSequenceStorage(a);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        if (list) {
            result = factory.createList(storage);
        } else {
            result = factory.createTuple(storage);
        }
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallFunctionKw(VirtualFrame virtualFrame, int initialStackTop, int bci, Node[] localNodes, boolean useCachedNodes, MutableLoopData mutableData,
                    byte tracingOrProfilingEnabled) {
        int stackTop = initialStackTop;
        CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, CallNodeGen.class, NODE_CALL, useCachedNodes);
        Object callable = virtualFrame.getObject(stackTop - 2);
        Object[] args = (Object[]) virtualFrame.getObject(stackTop - 1);

        Object result;
        profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
        try {
            result = callNode.execute(virtualFrame, callable, args, (PKeyword[]) virtualFrame.getObject(stackTop));
            profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
        } catch (PException pe) {
            profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
            throw pe;
        }

        virtualFrame.setObject(stackTop - 2, result);
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallFunctionVarargs(VirtualFrame virtualFrame, int initialStackTop, int bci, Node[] localNodes, boolean useCachedNodes, MutableLoopData mutableData,
                    byte tracingOrProfilingEnabled) {
        int stackTop = initialStackTop;
        CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, CallNodeGen.class, NODE_CALL, useCachedNodes);
        Object callable = virtualFrame.getObject(stackTop - 1);
        Object[] args = (Object[]) virtualFrame.getObject(stackTop);

        Object result;
        profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
        try {
            result = callNode.execute(virtualFrame, callable, args, PKeyword.EMPTY_KEYWORDS);
            profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
        } catch (PException pe) {
            profileCEvent(virtualFrame, callable, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
            throw pe;
        }

        virtualFrame.setObject(stackTop - 1, result);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallMethodVarargs(VirtualFrame virtualFrame, int initialStackTop, int bci, Node[] localNodes, boolean useCachedNodes, MutableLoopData mutableData,
                    byte tracingOrProfilingEnabled) {
        int stackTop = initialStackTop;
        Object func = virtualFrame.getObject(stackTop - 1);
        Object[] args = (Object[]) virtualFrame.getObject(stackTop);
        CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, CallNodeGen.class, NODE_CALL, useCachedNodes);

        Object result;
        profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
        try {
            result = callNode.execute(virtualFrame, func, args, PKeyword.EMPTY_KEYWORDS);
            profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
        } catch (PException pe) {
            profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
            throw pe;
        }

        virtualFrame.setObject(stackTop - 1, result);
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeLoadName(VirtualFrame virtualFrame, int initialStackTop, int bci, int oparg, Node[] localNodes, TruffleString[] localNames, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        ReadNameNode readNameNode = insertChildNode(localNodes, bci, UNCACHED_READ_NAME, ReadNameNodeGen.class, NODE_READ_NAME, useCachedNodes);
        virtualFrame.setObject(++stackTop, readNameNode.execute(virtualFrame, localNames[oparg]));
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallFunction(VirtualFrame virtualFrame, int stackTop, int bci, int oparg, Node[] localNodes, boolean useCachedNodes, MutableLoopData mutableData,
                    byte tracingOrProfilingEnabled) {
        Object func = virtualFrame.getObject(stackTop - oparg);
        Object result;
        switch (oparg) {
            case 0: {
                CallNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL, CallNodeGen.class, NODE_CALL, useCachedNodes);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.execute(virtualFrame, func, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 1: {
                CallUnaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_UNARY_METHOD, CallUnaryMethodNodeGen.class, NODE_CALL_UNARY_METHOD, useCachedNodes);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.executeObject(virtualFrame, func, virtualFrame.getObject(stackTop));
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 2: {
                CallBinaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_BINARY_METHOD, CallBinaryMethodNodeGen.class, NODE_CALL_BINARY_METHOD, useCachedNodes);
                Object arg1 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg0 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.executeObject(virtualFrame, func, arg0, arg1);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 3: {
                CallTernaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_TERNARY_METHOD, CallTernaryMethodNodeGen.class, NODE_CALL_TERNARY_METHOD, useCachedNodes);
                Object arg2 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg1 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg0 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.execute(virtualFrame, func, arg0, arg1, arg2);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 4: {
                CallQuaternaryMethodNode callNode = insertChildNode(localNodes, bci, UNCACHED_CALL_QUATERNARY_METHOD, CallQuaternaryMethodNodeGen.class, NODE_CALL_QUATERNARY_METHOD, useCachedNodes);
                Object arg3 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg2 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg1 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg0 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.execute(virtualFrame, func, arg0, arg1, arg2, arg3);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop, result);
                break;
            }
        }
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallComprehension(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, MutableLoopData mutableData, byte tracingOrProfilingEnabled) {
        PFunction func = (PFunction) virtualFrame.getObject(stackTop - 1);
        CallTargetInvokeNode callNode = insertChildNode(localNodes, bci, CallTargetInvokeNodeGen.class, () -> CallTargetInvokeNode.create(func));

        Object result;
        profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
        try {
            Object[] arguments = PArguments.create(1);
            PArguments.setArgument(arguments, 0, virtualFrame.getObject(stackTop));
            result = callNode.execute(virtualFrame, func, func.getGlobals(), func.getClosure(), arguments);
            profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
        } catch (PException pe) {
            profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
            throw pe;
        }

        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop, result);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeLoadMethod(VirtualFrame virtualFrame, int stackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        Object rcvr = virtualFrame.getObject(stackTop);
        TruffleString methodName = localNames[oparg];
        PyObjectGetMethod getMethodNode = insertChildNode(localNodes, bci, UNCACHED_OBJECT_GET_METHOD, PyObjectGetMethodNodeGen.class, NODE_OBJECT_GET_METHOD, useCachedNodes);
        Object func = getMethodNode.executeCached(virtualFrame, rcvr, methodName);
        virtualFrame.setObject(++stackTop, func);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCallMethod(VirtualFrame virtualFrame, int stackTop, int bci, int argcount, Node[] localNodes, boolean useCachedNodes, MutableLoopData mutableData,
                    byte tracingOrProfilingEnabled) {
        Object func = virtualFrame.getObject(stackTop - argcount);
        Object rcvr = virtualFrame.getObject(stackTop - argcount - 1);

        Object result;

        switch (argcount) {
            case 0: {
                CallUnaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_UNARY_METHOD, CallUnaryMethodNodeGen.class, NODE_CALL_UNARY_METHOD, useCachedNodes);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.executeObject(virtualFrame, func, rcvr);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 1: {
                CallBinaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_BINARY_METHOD, CallBinaryMethodNodeGen.class, NODE_CALL_BINARY_METHOD, useCachedNodes);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.executeObject(virtualFrame, func, rcvr, virtualFrame.getObject(stackTop));
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop, result);
                break;
            }
            case 2: {
                CallTernaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_TERNARY_METHOD, CallTernaryMethodNodeGen.class, NODE_CALL_TERNARY_METHOD, useCachedNodes);
                Object arg1 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg0 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop--, null);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.execute(virtualFrame, func, rcvr, arg0, arg1);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }

                virtualFrame.setObject(stackTop, result);

                break;
            }
            case 3: {
                CallQuaternaryMethodNode callNode = insertChildNode(localNodes, bci + 1, UNCACHED_CALL_QUATERNARY_METHOD, CallQuaternaryMethodNodeGen.class, NODE_CALL_QUATERNARY_METHOD,
                                useCachedNodes);
                Object arg2 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg1 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                Object arg0 = virtualFrame.getObject(stackTop);
                virtualFrame.setObject(stackTop--, null);
                virtualFrame.setObject(stackTop--, null);

                profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_CALL, mutableData, tracingOrProfilingEnabled);
                try {
                    result = callNode.execute(virtualFrame, func, rcvr, arg0, arg1, arg2);
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_RETURN, mutableData, tracingOrProfilingEnabled);
                } catch (PException pe) {
                    profileCEvent(virtualFrame, func, PythonContext.ProfileEvent.C_EXCEPTION, mutableData, tracingOrProfilingEnabled);
                    throw pe;
                }
                virtualFrame.setObject(stackTop, result);
                break;
            }
        }
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeStoreName(VirtualFrame virtualFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        Object value = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        WriteNameNode writeNameNode = insertChildNode(localNodes, bci, UNCACHED_WRITE_NAME, WriteNameNodeGen.class, NODE_WRITE_NAME, useCachedNodes);
        writeNameNode.execute(virtualFrame, localNames[oparg], value);
        return stackTop;
    }

    @InliningCutoff
    private PException bytecodeRaiseVarargs(VirtualFrame virtualFrame, int stackTop, int bci, int count, Node[] localNodes) {
        RaiseNode raiseNode = insertChildNode(localNodes, bci, RaiseNodeGen.class, NODE_RAISENODE);
        Object cause;
        Object exception;
        if (count > 1) {
            cause = virtualFrame.getObject(stackTop);
            virtualFrame.setObject(stackTop--, null);
        } else {
            cause = PNone.NO_VALUE;
        }
        if (count > 0) {
            exception = virtualFrame.getObject(stackTop);
            virtualFrame.setObject(stackTop--, null);
        } else {
            exception = PNone.NO_VALUE;
        }
        raiseNode.execute(virtualFrame, exception, cause, frameIsVisibleToPython());
        throw CompilerDirectives.shouldNotReachHere();
    }

    @InliningCutoff
    private void raiseUnboundCell(Node[] localNodes, int bci, int oparg, boolean useCachedNodes) {
        PRaiseNode raiseNode = insertChildNode(localNodes, bci, UNCACHED_RAISE, PRaiseNodeGen.class, NODE_RAISE, useCachedNodes);
        if (oparg < cellvars.length) {
            throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, cellvars[oparg]);
        } else {
            int varIdx = oparg - cellvars.length;
            throw raiseNode.raise(PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, freevars[varIdx]);
        }
    }

    @InliningCutoff
    private int bytecodeImportName(VirtualFrame virtualFrame, Object globals, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        CastToJavaIntExactNode castNode = insertChildNode(localNodes, bci, UNCACHED_CAST_TO_JAVA_INT_EXACT, CastToJavaIntExactNodeGen.class, NODE_CAST_TO_JAVA_INT_EXACT, useCachedNodes);
        TruffleString modname = localNames[oparg];
        int stackTop = initialStackTop;
        TruffleString[] fromlist = (TruffleString[]) virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        int level = castNode.executeCached(virtualFrame.getObject(stackTop));
        ImportNode importNode = insertChildNode(localNodes, bci + 1, UNCACHED_IMPORT, ImportNodeGen.class, NODE_IMPORT, useCachedNodes);
        Object result = importNode.execute(virtualFrame, modname, globals, fromlist, level);
        virtualFrame.setObject(stackTop, result);
        return stackTop;
    }

    @InliningCutoff
    private int bytecodeImportFrom(VirtualFrame virtualFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        TruffleString importName = localNames[oparg];
        Object from = virtualFrame.getObject(stackTop);
        ImportFromNode importFromNode = insertChildNode(localNodes, bci, UNCACHED_IMPORT_FROM, ImportFromNodeGen.class, NODE_IMPORT_FROM, useCachedNodes);
        Object imported = importFromNode.execute(virtualFrame, from, importName);
        virtualFrame.setObject(++stackTop, imported);
        return stackTop;
    }

    @InliningCutoff
    private int bytecodeImportStar(VirtualFrame virtualFrame, int initialStackTop, int bci, int oparg, TruffleString[] localNames, Node[] localNodes, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        TruffleString importName = localNames[oparg];
        int level = (int) virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        ImportStarNode importStarNode = insertChildNode(localNodes, bci, UNCACHED_IMPORT_STAR, ImportStarNodeGen.class, NODE_IMPORT_STAR, useCachedNodes);
        importStarNode.execute(virtualFrame, importName, level);
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
        if (cell2arg != null && cell2arg[i] != -1) {
            int idx = cell2arg[i];
            if (CompilerDirectives.inCompiledCode()) {
                cell.setRef(localFrame.getValue(idx));
            } else {
                cell.setRef(localFrame.getObject(idx));
            }
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
    private static <T> void moveFromStack(VirtualFrame virtualFrame, int start, int stop, T[] target) {
        CompilerAsserts.partialEvaluationConstant(start);
        CompilerAsserts.partialEvaluationConstant(stop);
        for (int j = 0, i = start; i < stop; i++, j++) {
            target[j] = (T) virtualFrame.getObject(i);
            virtualFrame.setObject(i, null);
        }
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCollectionFromStack(VirtualFrame virtualFrame, int type, int count, int oldStackTop, Node[] localNodes, int nodeIndex, boolean useCachedNodes) {
        int stackTop = oldStackTop;
        Object res = null;
        switch (type) {
            case CollectionBits.KIND_LIST: {
                ListFromStackNode storageFromStackNode = insertChildNodeInt(localNodes, nodeIndex, ListFromStackNodeGen.class, ListFromStackNodeGen::create, count);
                SequenceStorage store = storageFromStackNode.execute(virtualFrame, stackTop - count + 1, stackTop + 1);
                res = factory.createList(store, storageFromStackNode);
                break;
            }
            case CollectionBits.KIND_TUPLE: {
                TupleFromStackNode storageFromStackNode = insertChildNodeInt(localNodes, nodeIndex, TupleFromStackNodeGen.class, TupleFromStackNodeGen::create, count);
                SequenceStorage store = storageFromStackNode.execute(virtualFrame, stackTop - count + 1, stackTop + 1);
                res = factory.createTuple(store);
                break;
            }
            case CollectionBits.KIND_SET: {
                PSet set = factory.createSet();
                HashingCollectionNodes.SetItemNode newNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, HashingCollectionNodesFactory.SetItemNodeGen.class, NODE_SET_ITEM,
                                useCachedNodes);
                for (int i = stackTop - count + 1; i <= stackTop; i++) {
                    newNode.executeCached(virtualFrame, set, virtualFrame.getObject(i), PNone.NONE);
                    virtualFrame.setObject(i, null);
                }
                res = set;
                break;
            }
            case CollectionBits.KIND_DICT: {
                PDict dict = factory.createDict();
                HashingCollectionNodes.SetItemNode setItem = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, HashingCollectionNodesFactory.SetItemNodeGen.class, NODE_SET_ITEM,
                                useCachedNodes);
                assert count % 2 == 0;
                for (int i = stackTop - count + 1; i <= stackTop; i += 2) {
                    setItem.executeCached(virtualFrame, dict, virtualFrame.getObject(i), virtualFrame.getObject(i + 1));
                    virtualFrame.setObject(i, null);
                    virtualFrame.setObject(i + 1, null);
                }
                res = dict;
                break;
            }
            case CollectionBits.KIND_KWORDS: {
                PKeyword[] kwds = new PKeyword[count];
                moveFromStack(virtualFrame, stackTop - count + 1, stackTop + 1, kwds);
                res = kwds;
                break;
            }
            case CollectionBits.KIND_OBJECT: {
                Object[] objs = new Object[count];
                moveFromStack(virtualFrame, stackTop - count + 1, stackTop + 1, objs);
                res = objs;
                break;
            }
        }
        stackTop -= count;
        virtualFrame.setObject(++stackTop, res);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private void bytecodeCollectionFromCollection(VirtualFrame virtualFrame, int type, int stackTop, Node[] localNodes, int nodeIndex, boolean useCachedNodes) {
        Object sourceCollection = virtualFrame.getObject(stackTop);
        Object result;
        switch (type) {
            case CollectionBits.KIND_LIST: {
                ListNodes.ConstructListNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_LIST, ListNodesFactory.ConstructListNodeGen.class, NODE_CONSTRUCT_LIST,
                                useCachedNodes);
                result = constructNode.execute(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.KIND_TUPLE: {
                TupleNodes.ConstructTupleNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_TUPLE, TupleNodesFactory.ConstructTupleNodeGen.class, NODE_CONSTRUCT_TUPLE,
                                useCachedNodes);
                result = constructNode.execute(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.KIND_SET: {
                SetNodes.ConstructSetNode constructNode = insertChildNode(localNodes, nodeIndex, UNCACHED_CONSTRUCT_SET, SetNodesFactory.ConstructSetNodeGen.class, NODE_CONSTRUCT_SET, useCachedNodes);
                result = constructNode.executeWith(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.KIND_DICT: {
                // TODO create uncached node
                HashingStorage.InitNode initNode = insertChildNode(localNodes, nodeIndex, HashingStorageFactory.InitNodeGen.class, NODE_HASHING_STORAGE_INIT);
                HashingStorage storage = initNode.execute(virtualFrame, sourceCollection, PKeyword.EMPTY_KEYWORDS);
                result = factory.createDict(storage);
                break;
            }
            case CollectionBits.KIND_OBJECT: {
                ExecutePositionalStarargsNode executeStarargsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_EXECUTE_STARARGS, ExecutePositionalStarargsNodeGen.class, NODE_EXECUTE_STARARGS,
                                useCachedNodes);
                result = executeStarargsNode.executeWith(virtualFrame, sourceCollection);
                break;
            }
            case CollectionBits.KIND_KWORDS: {
                KeywordsNode keywordsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_KEYWORDS, KeywordsNodeGen.class, NODE_KEYWORDS, useCachedNodes);
                result = keywordsNode.execute(virtualFrame, sourceCollection, stackTop);
                break;
            }
            default:
                throw CompilerDirectives.shouldNotReachHere("Unexpected collection type");
        }
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeCollectionAddCollection(VirtualFrame virtualFrame, int type, int initialStackTop, Node[] localNodes, int nodeIndex, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        Object collection1 = virtualFrame.getObject(stackTop - 1);
        Object collection2 = virtualFrame.getObject(stackTop);
        Object result;
        switch (type) {
            case CollectionBits.KIND_LIST: {
                // TODO uncached node
                ListBuiltins.ListExtendNode extendNode = insertChildNode(localNodes, nodeIndex, ListBuiltinsFactory.ListExtendNodeFactory.ListExtendNodeGen.class, NODE_LIST_EXTEND);
                extendNode.execute(virtualFrame, (PList) collection1, collection2);
                result = collection1;
                break;
            }
            case CollectionBits.KIND_SET: {
                SetBuiltins.UpdateSingleNode updateNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_UPDATE, SetBuiltinsFactory.UpdateSingleNodeGen.class, NODE_SET_UPDATE, useCachedNodes);
                PSet set = (PSet) collection1;
                updateNode.execute(virtualFrame, set, collection2);
                result = set;
                break;
            }
            case CollectionBits.KIND_DICT: {
                // TODO uncached node
                DictNodes.UpdateNode updateNode = insertChildNode(localNodes, nodeIndex, DictNodesFactory.UpdateNodeGen.class, NODE_DICT_UPDATE);
                updateNode.execute(virtualFrame, (PDict) collection1, collection2);
                result = collection1;
                break;
            }
            // Note: we don't allow this operation for tuple
            case CollectionBits.KIND_OBJECT: {
                Object[] array1 = (Object[]) collection1;
                ExecutePositionalStarargsNode executeStarargsNode = insertChildNode(localNodes, nodeIndex, UNCACHED_EXECUTE_STARARGS, ExecutePositionalStarargsNodeGen.class, NODE_EXECUTE_STARARGS,
                                useCachedNodes);
                Object[] array2 = executeStarargsNode.executeWith(virtualFrame, collection2);
                Object[] combined = new Object[array1.length + array2.length];
                System.arraycopy(array1, 0, combined, 0, array1.length);
                System.arraycopy(array2, 0, combined, array1.length, array2.length);
                result = combined;
                break;
            }
            case CollectionBits.KIND_KWORDS: {
                PKeyword[] array1 = (PKeyword[]) collection1;
                PKeyword[] array2 = (PKeyword[]) collection2;
                PKeyword[] combined = new PKeyword[array1.length + array2.length];
                System.arraycopy(array1, 0, combined, 0, array1.length);
                System.arraycopy(array2, 0, combined, array1.length, array2.length);
                result = combined;
                break;
            }
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.getUncached().raise(SystemError, ErrorMessages.INVALID_TYPE_FOR_S, "COLLECTION_ADD_COLLECTION");
        }
        virtualFrame.setObject(stackTop--, null);
        virtualFrame.setObject(stackTop, result);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private int bytecodeAddToCollection(VirtualFrame virtualFrame, int initialStackTop, int nodeIndex, Node[] localNodes, int depth, int type, boolean useCachedNodes) {
        int stackTop = initialStackTop;
        Object collection = virtualFrame.getObject(stackTop - depth);
        Object item = virtualFrame.getObject(stackTop);
        switch (type) {
            case CollectionBits.KIND_LIST: {
                ListNodes.AppendNode appendNode = insertChildNode(localNodes, nodeIndex, UNCACHED_LIST_APPEND, ListNodesFactory.AppendNodeGen.class, NODE_LIST_APPEND, useCachedNodes);
                appendNode.execute((PList) collection, item);
                break;
            }
            case CollectionBits.KIND_SET: {
                SetNodes.AddNode addNode = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ADD, SetNodesFactory.AddNodeGen.class, NODE_SET_ADD, useCachedNodes);
                addNode.execute(virtualFrame, (PSet) collection, item);
                break;
            }
            case CollectionBits.KIND_DICT: {
                Object key = virtualFrame.getObject(stackTop - 1);
                HashingCollectionNodes.SetItemNode setItem = insertChildNode(localNodes, nodeIndex, UNCACHED_SET_ITEM, HashingCollectionNodesFactory.SetItemNodeGen.class, NODE_SET_ITEM,
                                useCachedNodes);
                setItem.executeCached(virtualFrame, (PDict) collection, key, item);
                virtualFrame.setObject(stackTop--, null);
                break;
            }
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.getUncached().raise(SystemError, ErrorMessages.INVALID_TYPE_FOR_S, "ADD_TO_COLLECTION");
        }
        virtualFrame.setObject(stackTop--, null);
        return stackTop;
    }

    @BytecodeInterpreterSwitch
    private void bytecodeTupleFromList(VirtualFrame virtualFrame, int stackTop) {
        PList list = (PList) virtualFrame.getObject(stackTop);
        Object result = factory.createTuple(list.getSequenceStorage());
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private void bytecodeFrozensetFromList(VirtualFrame virtualFrame, int stackTop, int nodeIndex, Node[] localNodes) {
        PList list = (PList) virtualFrame.getObject(stackTop);
        HashingStorageFromListSequenceStorageNode node = insertChildNode(localNodes, nodeIndex, HashingStorageFromListSequenceStorageNodeGen.class, NODE_HASHING_STORAGE_FROM_SEQUENCE);
        Object result = factory.createFrozenSet(node.execute(virtualFrame, list.getSequenceStorage()));
        virtualFrame.setObject(stackTop, result);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeUnpackSequence(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int count, boolean useCachedNodes) {
        UnpackSequenceNode unpackNode = insertChildNode(localNodes, bci, UNCACHED_UNPACK_SEQUENCE, UnpackSequenceNodeGen.class, NODE_UNPACK_SEQUENCE, useCachedNodes);
        Object collection = virtualFrame.getObject(stackTop);
        return unpackNode.execute(virtualFrame, stackTop - 1, collection, count);
    }

    @BytecodeInterpreterSwitch
    private int bytecodeUnpackEx(VirtualFrame virtualFrame, int stackTop, int bci, Node[] localNodes, int countBefore, int countAfter, boolean useCachedNodes) {
        UnpackExNode unpackNode = insertChildNode(localNodes, bci, UNCACHED_UNPACK_EX, UnpackExNodeGen.class, NODE_UNPACK_EX, useCachedNodes);
        Object collection = virtualFrame.getObject(stackTop);
        return unpackNode.execute(virtualFrame, stackTop - 1, collection, countBefore, countAfter);
    }

    @InliningCutoff
    @ExplodeLoop
    private int findHandler(int bci) {
        CompilerAsserts.partialEvaluationConstant(bci);

        for (int i = 0; i < exceptionHandlerRanges.length; i += 4) {
            // The ranges are ordered by their start and non-overlapping
            if (bci < exceptionHandlerRanges[i]) {
                break;
            } else if (bci < exceptionHandlerRanges[i + 1]) {
                // bci is inside this try-block range. get the target stack size
                return i + 2;
            }
        }
        return -1;
    }

    @InliningCutoff
    @ExplodeLoop
    private static int unwindBlock(VirtualFrame virtualFrame, int stackTop, int stackTopBeforeBlock) {
        CompilerAsserts.partialEvaluationConstant(stackTop);
        CompilerAsserts.partialEvaluationConstant(stackTopBeforeBlock);
        for (int i = stackTop; i > stackTopBeforeBlock; i--) {
            virtualFrame.setObject(i, null);
        }
        return stackTopBeforeBlock;
    }

    public PCell readClassCell(Frame frame) {
        if (classcellIndex < 0) {
            return null;
        }
        return (PCell) frame.getObject(classcellIndex);
    }

    public Object readSelf(Frame frame) {
        if (selfIndex < 0) {
            return null;
        } else if (selfIndex == 0) {
            return frame.getObject(0);
        } else {
            PCell selfCell = (PCell) frame.getObject(selfIndex);
            return selfCell.getRef();
        }
    }

    public int bciToLine(int bci) {
        return co.bciToLine(bci);
    }

    public int getFirstLineno() {
        return co.startLine;
    }

    public boolean frameIsVisibleToPython() {
        return !internal;
    }

    @Override
    public SourceSection getSourceSection() {
        if (sourceSection != null) {
            return sourceSection;
        } else if (!source.hasCharacters()) {
            /*
             * TODO We could still expose the disassembled bytecode for a debugger to have something
             * to step through.
             */
            sourceSection = source.createUnavailableSection();
            return sourceSection;
        } else {
            sourceSection = co.getSourceSection(source);
            return sourceSection;
        }
    }

    public int bciToLasti(int bci) {
        if (bci <= 0) {
            return bci;
        }
        byte[] bytecode = co.code;
        int number = 0;
        for (int i = 0; i < bytecode.length;) {
            if (i >= bci) {
                return number;
            }
            i += OpCodes.fromOpCode(bytecode[i]).length();
            number += 2;
        }
        return -1;
    }

    public int lastiToBci(int lasti) {
        int bci = 0;
        for (int i = 0; i < lasti && bci < co.code.length; i += 2) {
            bci += OpCodes.fromOpCode(co.code[bci]).length();
        }
        return bci;
    }

    @Override
    public boolean isInternal() {
        return internal;
    }

    @Override
    public boolean setsUpCalleeContext() {
        return true;
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

    @Override
    protected boolean isCloneUninitializedSupported() {
        return true;
    }

    @Override
    protected RootNode cloneUninitialized() {
        return new PBytecodeRootNode(PythonLanguage.get(this), getFrameDescriptor(), getSignature(), co, source, parserErrorCallback);
    }

    public void triggerDeferredDeprecationWarnings() {
        if (parserErrorCallback != null) {
            parserErrorCallback.triggerDeprecationWarnings();
        }
    }

    private void bytecodePopAndJumpIfFalse(VirtualFrame virtualFrame, int bci, int stackTop) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isBoolean(stackTop)) {
            bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_FALSE_B;
        } else {
            bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_FALSE_O;
        }
    }

    private void bytecodePopAndJumpIfTrue(VirtualFrame virtualFrame, int bci, int stackTop) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (virtualFrame.isBoolean(stackTop)) {
            bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_TRUE_B;
        } else {
            bytecode[bci] = OpCodesConstants.POP_AND_JUMP_IF_TRUE_O;
        }
    }
}
