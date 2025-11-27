/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode_dsl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.T_GENERIC_ALIAS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTION_GROUP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AEXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EXIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.math.BigInteger;
import java.util.Iterator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallTypingFuncObjectNode;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.UnpackTypeVarTuplesNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNode;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenWrappedValue;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexWithBoundsCheckNode;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionGroupBuiltins;
import com.oracle.graal.python.builtins.objects.exception.ChainExceptionsNode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PBaseExceptionGroup;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.typing.PTypeAliasType;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodes.MakeTypeParamKind;
import com.oracle.graal.python.compiler.ParserCallbacksImpl;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAndNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAddNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAndNode;
import com.oracle.graal.python.lib.PyNumberInPlaceFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceLshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceOrNode;
import com.oracle.graal.python.lib.PyNumberInPlacePowerNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRemainderNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceSubtractNode;
import com.oracle.graal.python.lib.PyNumberInPlaceTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceXorNode;
import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberLshiftNode;
import com.oracle.graal.python.lib.PyNumberMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyNumberPositiveNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberRshiftNode;
import com.oracle.graal.python.lib.PyNumberSubtractNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberXorNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectIsNotTrueNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompare.GenericRichCompare;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttrO;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ConcatDictToStorageNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.attributes.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.bytecode.CopyDictWithoutKeysNode;
import com.oracle.graal.python.nodes.bytecode.GetAIterNode;
import com.oracle.graal.python.nodes.bytecode.GetANextNode;
import com.oracle.graal.python.nodes.bytecode.GetSendValueNode;
import com.oracle.graal.python.nodes.bytecode.GetTPFlagsNode;
import com.oracle.graal.python.nodes.bytecode.GetYieldFromIterNode;
import com.oracle.graal.python.nodes.bytecode.ImportFromNode;
import com.oracle.graal.python.nodes.bytecode.ImportNode;
import com.oracle.graal.python.nodes.bytecode.ImportStarNode;
import com.oracle.graal.python.nodes.bytecode.MatchClassNode;
import com.oracle.graal.python.nodes.bytecode.MatchKeysNode;
import com.oracle.graal.python.nodes.bytecode.PrintExprNode;
import com.oracle.graal.python.nodes.bytecode.RaiseNode;
import com.oracle.graal.python.nodes.bytecode.SetupAnnotationsNode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.exception.EncapsulateExceptionGroupNode;
import com.oracle.graal.python.nodes.exception.ExceptMatchNode;
import com.oracle.graal.python.nodes.exception.HandleExceptionsInHandlerNode;
import com.oracle.graal.python.nodes.exception.ValidExceptionNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadFromLocalsNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.ProfileEvent;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContext.TraceEvent;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.PTupleListBase;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.Yield;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@GenerateBytecode(//
                languageClass = PythonLanguage.class, //
                enableBlockScoping = false, //
                enableYield = true, //
                enableSerialization = true, //
                enableTagInstrumentation = true, //
                boxingEliminationTypes = {int.class}, //
                storeBytecodeIndexInFrame = true //
)
@OperationProxy(PyNumberSubtractNode.class)
@OperationProxy(PyNumberTrueDivideNode.class)
@OperationProxy(PyNumberFloorDivideNode.class)
@OperationProxy(PyNumberRemainderNode.class)
@OperationProxy(PyNumberLshiftNode.class)
@OperationProxy(PyNumberRshiftNode.class)
@OperationProxy(PyNumberAndNode.class)
@OperationProxy(PyNumberOrNode.class)
@OperationProxy(PyNumberXorNode.class)
@OperationProxy(PyNumberMatrixMultiplyNode.class)
@OperationProxy(PyNumberMultiplyNode.class)
@OperationProxy(PyNumberAddNode.class)
@OperationProxy(PyNumberPositiveNode.class)
@OperationProxy(PyNumberNegativeNode.class)
@OperationProxy(PyNumberInvertNode.class)
@OperationProxy(PyNumberInPlaceAddNode.class)
@OperationProxy(PyNumberInPlaceSubtractNode.class)
@OperationProxy(PyNumberInPlaceMultiplyNode.class)
@OperationProxy(PyNumberInPlaceTrueDivideNode.class)
@OperationProxy(PyNumberInPlaceFloorDivideNode.class)
@OperationProxy(PyNumberInPlaceRemainderNode.class)
@OperationProxy(PyNumberInPlaceMatrixMultiplyNode.class)
@OperationProxy(PyNumberInPlaceAndNode.class)
@OperationProxy(PyNumberInPlaceOrNode.class)
@OperationProxy(PyNumberInPlaceXorNode.class)
@OperationProxy(PyNumberInPlaceLshiftNode.class)
@OperationProxy(PyNumberInPlaceRshiftNode.class)
@OperationProxy(IsNode.class)
@OperationProxy(FormatNode.class)
@OperationProxy(ExceptMatchNode.class)
@OperationProxy(HandleExceptionsInHandlerNode.class)
@OperationProxy(EncapsulateExceptionGroupNode.class)
@OperationProxy(GetYieldFromIterNode.class)
@OperationProxy(GetAwaitableNode.class)
@OperationProxy(SetupAnnotationsNode.class)
@OperationProxy(GetAIterNode.class)
@OperationProxy(GetANextNode.class)
@OperationProxy(value = CopyDictWithoutKeysNode.class, name = "CopyDictWithoutKeys")
@OperationProxy(value = PyObjectIsTrueNode.class, name = "Yes")
@OperationProxy(value = PyObjectIsNotTrueNode.class, name = "Not")
@OperationProxy(value = ListNodes.AppendNode.class, name = "ListAppend")
@OperationProxy(value = SetNodes.AddNode.class, name = "SetAdd")
@ShortCircuitOperation(name = "BoolAnd", booleanConverter = PyObjectIsTrueNode.class, operator = Operator.AND_RETURN_VALUE)
@ShortCircuitOperation(name = "BoolOr", booleanConverter = PyObjectIsTrueNode.class, operator = Operator.OR_RETURN_VALUE)
@ShortCircuitOperation(name = "PrimitiveBoolAnd", operator = Operator.AND_RETURN_VALUE)
@SuppressWarnings({"unused"})
public abstract class PBytecodeDSLRootNode extends PRootNode implements BytecodeRootNode {
    public static final int EXPLODE_LOOP_THRESHOLD = 30;
    private static final BytecodeConfig TRACE_AND_PROFILE_CONFIG = PBytecodeDSLRootNodeGen.newConfigBuilder().//
                    addInstrumentation(TraceOrProfileCall.class).//
                    addInstrumentation(TraceLine.class).//
                    addInstrumentation(TraceLineAtLoopHeader.class).//
                    addInstrumentation(TraceOrProfileReturn.class).//
                    addInstrumentation(TraceException.class).//
                    build();

    @Child private transient CalleeContext calleeContext = CalleeContext.create();
    @Child private transient ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private transient ChainExceptionsNode chainExceptionsNode;

    private static final class TracingNodes extends Node {
        @Child MaterializeFrameNode materializeFrameNode = MaterializeFrameNode.create();
        @Child CallNode tracingCallNode = CallNode.create();
        @Child CallNode profilingCallNode = CallNode.create();
    }

    // Not a child of this root, adopted by the BytecodeNode
    private transient TracingNodes tracingNodes;

    // These fields are effectively final, but can only be set after construction.
    @CompilationFinal protected transient BytecodeDSLCodeUnit co;
    @CompilationFinal protected transient Signature signature;
    @CompilationFinal protected transient int selfIndex;
    @CompilationFinal protected transient int classcellIndex;
    @CompilationFinal public int yieldFromGeneratorIndex = -1;
    @CompilationFinal(dimensions = 1) protected transient Assumption[] cellEffectivelyFinalAssumptions;

    private transient boolean pythonInternal;
    @CompilationFinal private transient boolean internal;

    // For deferred deprecation warnings
    @CompilationFinal private transient ParserCallbacksImpl parserErrorCallback;

    @SuppressWarnings("this-escape")
    protected PBytecodeDSLRootNode(PythonLanguage language, FrameDescriptor.Builder frameDescriptorBuilder) {
        super(language, frameDescriptorBuilder.info(new BytecodeDSLFrameInfo()).build());
        ((BytecodeDSLFrameInfo) getFrameDescriptor().getInfo()).setRootNode(this);
    }

    public static PBytecodeDSLRootNode cast(RootNode root) {
        return PBytecodeDSLRootNodeGen.BYTECODE.cast(root);
    }

    public final PythonLanguage getLanguage() {
        return getLanguage(PythonLanguage.class);
    }

    public void setMetadata(BytecodeDSLCodeUnit co, ParserCallbacksImpl parserErrorCallback) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.co = co;
        this.signature = co.computeSignature();
        this.classcellIndex = co.classcellIndex;
        this.selfIndex = co.selfIndex;
        this.internal = getSource().isInternal();
        this.parserErrorCallback = parserErrorCallback;
        if (co.cellvars.length > 0) {
            this.cellEffectivelyFinalAssumptions = new Assumption[co.cellvars.length];
            for (int i = 0; i < co.cellvars.length; i++) {
                cellEffectivelyFinalAssumptions[i] = Truffle.getRuntime().createAssumption("cell is effectively final");
            }
        }
    }

    @Override
    public final boolean isInternal() {
        return internal;
    }

    @Override
    public final boolean isPythonInternal() {
        return pythonInternal;
    }

    public final void setPythonInternal(boolean pythonInternal) {
        this.pythonInternal = pythonInternal;
    }

    public final void triggerDeferredDeprecationWarnings() {
        if (parserErrorCallback != null) {
            parserErrorCallback.triggerDeprecationWarnings();
        }
    }

    @Override
    public String toString() {
        return "<bytecode " + co.qualname + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Prolog(storeBytecodeIndex = false)
    public static final class EnterCalleeContext {
        @Specialization
        public static void doEnter(VirtualFrame frame,
                        @Bind PBytecodeDSLRootNode root) {
            root.calleeContext.enter(frame);

            if (root.needsTraceAndProfileInstrumentation()) {
                root.ensureTraceAndProfileEnabled();
                root.getThreadState().pushInstrumentationData(root);
            }
        }
    }

    @EpilogReturn(storeBytecodeIndex = true)
    public static final class EpilogForReturn {
        @Specialization
        public static Object doExit(VirtualFrame frame, Object returnValue,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode location) {
            if (root.needsTraceAndProfileInstrumentation()) {
                root.getThreadState().popInstrumentationData(root);
            }

            root.calleeContext.exit(frame, root, location);
            return returnValue;
        }
    }

    @EpilogExceptional(storeBytecodeIndex = true)
    public static final class EpilogForException {
        @Specialization
        public static void doExit(VirtualFrame frame, AbstractTruffleException ate,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode location) {
            if (ate instanceof PException pe) {
                pe.notifyAddedTracebackFrame(!root.isInternal());
            }

            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, location, null);
                root.getThreadState().popInstrumentationData(root);
            }

            root.calleeContext.exit(frame, root, location);
        }
    }

    /*
     * Data for tracing, profiling and instrumentation
     */
    public static final class InstrumentationData {
        private final InstrumentationData previous;
        private final PBytecodeDSLRootNode rootNode;
        private int pastLine;

        public InstrumentationData(PBytecodeDSLRootNode rootNode, InstrumentationData previous) {
            this.previous = previous;
            this.rootNode = rootNode;
            this.pastLine = -1;
        }

        public InstrumentationData getPrevious() {
            return previous;
        }

        public PBytecodeDSLRootNode getRootNode() {
            return rootNode;
        }

        int getPastLine() {
            return pastLine;
        }

        void setPastLine(int pastLine) {
            this.pastLine = pastLine;
        }

        void clearPastLine() {
            this.pastLine = -1;
        }
    }

    @NonIdempotent
    public final boolean needsTraceAndProfileInstrumentation() {
        // We need instrumentation only if the assumption is invalid
        return !getLanguage().noTracingOrProfilingAssumption.isValid();
    }

    @NonIdempotent
    public final PythonThreadState getThreadState() {
        return PythonContext.get(this).getThreadState(getLanguage());
    }

    /**
     * Reparses with instrumentations for settrace and setprofile enabled.
     */
    public final void ensureTraceAndProfileEnabled() {
        getRootNodes().update(TRACE_AND_PROFILE_CONFIG);
    }

    private TracingNodes getTracingNodes(BytecodeNode location) {
        /*
         * The TracingNodes node must be child of the BytecodeNode and not the PBytecodeRootNode, so
         * in case BytecodeNode changed, we must reinsert it
         */
        if (tracingNodes == null || tracingNodes.getParent() != location) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            tracingNodes = location.insert(new TracingNodes());
        }
        return tracingNodes;
    }

    private PFrame ensurePyFrame(VirtualFrame frame, BytecodeNode location) {
        return getTracingNodes(location).materializeFrameNode.executeOnStack(frame, location, true, false);
    }

    private void syncLocalsBackToFrame(VirtualFrame frame, PFrame pyFrame, BytecodeNode location) {
        if (pyFrame.localsAccessed()) {
            GetFrameLocalsNode.syncLocalsBackToFrame(co, location, pyFrame, frame);
        }
    }

    @InliningCutoff
    private void invokeProfileFunction(VirtualFrame virtualFrame, BytecodeNode location, Object profileFun,
                    PythonContext.PythonThreadState threadState, PythonContext.ProfileEvent event, Object arg) {
        if (threadState.isProfiling()) {
            return;
        }
        threadState.profilingStart();
        PFrame pyFrame = ensurePyFrame(virtualFrame, location);
        try {
            pyFrame.setLocalsAccessed(false);
            Object result = getTracingNodes(location).profilingCallNode.execute(virtualFrame, profileFun, pyFrame, event.name, arg == null ? PNone.NONE : arg);
            syncLocalsBackToFrame(virtualFrame, pyFrame, location);
            Object realResult = result == PNone.NONE ? null : result;
            pyFrame.setLocalTraceFun(realResult);
        } catch (Throwable e) {
            threadState.setProfileFun(null, getLanguage());
            throw e;
        } finally {
            threadState.profilingStop();
        }
    }

    @InliningCutoff
    private void invokeTraceFunction(VirtualFrame virtualFrame, BytecodeNode location, Object traceFun, PythonContext.PythonThreadState threadState,
                    PythonContext.TraceEvent event, Object arg, int line) {
        if (threadState.isTracing()) {
            return;
        }
        assert event != PythonContext.TraceEvent.DISABLED;
        threadState.tracingStart(event);
        PFrame pyFrame = ensurePyFrame(virtualFrame, location);
        /**
         * Call events use the thread-local trace function, which returns a "local" trace function
         * to use for other trace events.
         */
        boolean useLocalFn = event != TraceEvent.CALL;
        Object traceFn = useLocalFn ? pyFrame.getLocalTraceFun() : traceFun;
        if (traceFn == null) {
            threadState.tracingStop();
            return;
        }
        Object nonNullArg = arg == null ? PNone.NONE : arg;

        try {
            /**
             * The PFrame syncs to the line of the current bci. Sometimes this location is
             * inaccurate and needs to be overridden (e.g., when tracing an implicit return at the
             * end of a function, we need to trace the line of the last statement executed).
             */
            if (line != -1) {
                pyFrame.setLineLock(line);
            }

            pyFrame.setLocalsAccessed(false);
            Object result = getTracingNodes(location).tracingCallNode.execute(virtualFrame, traceFn, pyFrame, event.pythonName, nonNullArg);
            syncLocalsBackToFrame(virtualFrame, pyFrame, location);
            // https://github.com/python/cpython/issues/104232
            if (useLocalFn) {
                Object realResult = result == PNone.NONE ? traceFn : result;
                pyFrame.setLocalTraceFun(realResult);
            } else if (result != PNone.NONE) {
                pyFrame.setLocalTraceFun(result);
            } else {
                pyFrame.setLocalTraceFun(null);
            }
        } catch (Throwable e) {
            threadState.setTraceFun(null, getLanguage());
            throw e;
        } finally {
            if (line != -1) {
                pyFrame.lineUnlock();
            }
            threadState.tracingStop();
        }
    }

    private void traceOrProfileCall(VirtualFrame frame, BytecodeNode bytecode, int bci) {
        PythonThreadState threadState = getThreadState();
        Object traceFun = threadState.getTraceFun();
        if (traceFun != null) {
            int line = bciToLine(bci, bytecode);
            invokeTraceFunction(frame, bytecode, traceFun, threadState, TraceEvent.CALL, null, line);
        }
        Object profileFun = threadState.getProfileFun();
        if (profileFun != null) {
            invokeProfileFunction(frame, bytecode, profileFun, threadState, ProfileEvent.CALL, null);
        }
    }

    @InliningCutoff
    private void traceLine(VirtualFrame frame, BytecodeNode location, int line) {
        PythonThreadState threadState = getThreadState();
        InstrumentationData instrumentationData = threadState.getInstrumentationData(this);

        // TODO: this should never happen by nature of how we emit TraceLine, but sometimes does.
        // needs investigation.
        if (line == instrumentationData.getPastLine()) {
            return;
        }
        instrumentationData.setPastLine(line);

        PFrame pyFrame = ensurePyFrame(frame, location);
        if (pyFrame.getTraceLine()) {
            Object traceFun = threadState.getTraceFun();
            if (traceFun != null) {
                invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.LINE, null, line);
            }
        }
    }

    @InliningCutoff
    private void traceLineAtLoopHeader(VirtualFrame frame, BytecodeNode location, int line) {
        PythonThreadState threadState = getThreadState();
        InstrumentationData instrumentationData = threadState.getInstrumentationData(this);
        int pastLine = instrumentationData.getPastLine();

        /**
         * A loop should always be traced once, even if it is not entered. We also need to trace the
         * loop header on each iteration. To accomplish this, we emit a TraceLine at the top of each
         * loop (before loop initialization) and a TraceLineAtLoopHeader before the loop condition
         * evaluates. To avoid tracing twice on the first iteration, we need to check our line
         * against pastLine.
         */
        if (line != pastLine) {
            Object traceFun = threadState.getTraceFun();
            if (traceFun != null) {
                invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.LINE, null, line);
            }
        }
        /**
         * If the loop is all on one line, we need to trace on each iteration (even though the line
         * hasn't changed). Clear pastLine so the line comparison above succeeds.
         */
        instrumentationData.clearPastLine();
    }

    private void traceOrProfileReturn(VirtualFrame frame, BytecodeNode location, Object value) {
        PythonThreadState threadState = getThreadState();
        Object traceFun = threadState.getTraceFun();
        if (traceFun != null) {
            invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.RETURN, value, threadState.getInstrumentationData(this).getPastLine());
        }
        Object profileFun = threadState.getProfileFun();
        if (profileFun != null) {
            invokeProfileFunction(frame, location, profileFun, threadState, ProfileEvent.RETURN, value);
        }
    }

    @InliningCutoff
    private PException traceException(VirtualFrame frame, BytecodeNode bytecode, int bci, PException pe) {
        PException result = pe;

        PythonThreadState threadState = getThreadState();
        // We should only trace the exception if tracing is enabled.
        if (threadState.getTraceFun() != null) {
            PFrame pyFrame = ensurePyFrame(frame, bytecode);
            // We use the local function for tracing exceptions.
            if (pyFrame.getLocalTraceFun() != null) {
                pe.markAsCaught(frame, this);
                Object peForPython = pe.getEscapedException();
                Object peType = GetClassNode.executeUncached(peForPython);
                Object traceback = ExceptionNodes.GetTracebackNode.executeUncached(peForPython);
                try {
                    invokeTraceFunction(frame, bytecode, null, threadState, TraceEvent.EXCEPTION,
                                    PFactory.createTuple(getLanguage(), new Object[]{peType, peForPython, traceback}),
                                    bciToLine(bci, bytecode));
                } catch (PException newPe) {
                    // If the trace function raises, handle its exception instead.
                    result = newPe;
                    // Below, we get the exception for reraise in order to hide the original
                    // raising site that's being traced (i.e., hiding the original cause).
                }
                // The exception was reified already. Return a new exception that looks like this
                // catch didn't happen.
                result = result.getExceptionForReraise(!isInternal());
                result.setCatchLocation(bci, bytecode);
            }
        }
        return result;
    }

    @Instrumentation(storeBytecodeIndex = true)
    public static final class TraceOrProfileCall {
        @Specialization
        public static void perform(VirtualFrame frame,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci) {
            root.traceOrProfileCall(frame, bytecode, bci);
        }
    }

    @Instrumentation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class TraceLine {
        @Specialization
        public static void perform(VirtualFrame frame,
                        int line,
                        @Bind BytecodeNode location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceLine(frame, location, line);
        }
    }

    @Instrumentation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class TraceLineAtLoopHeader {
        @Specialization
        public static void perform(VirtualFrame frame,
                        int line,
                        @Bind BytecodeNode location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceLineAtLoopHeader(frame, location, line);
        }
    }

    @Instrumentation(storeBytecodeIndex = true)
    public static final class TraceOrProfileReturn {
        @Specialization
        public static Object perform(VirtualFrame frame, Object value,
                        @Bind BytecodeNode location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceOrProfileReturn(frame, location, value);
            return value;
        }
    }

    @Instrumentation
    public static final class TraceException {
        @Specialization
        public static void perform() {
            throw new UnsupportedOperationException("trace exception not implemented");
        }
    }

    @Override
    public Throwable interceptInternalException(Throwable throwable, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        PythonLanguage language = getLanguage();
        if (language.getEngineOption(PythonOptions.CatchAllExceptions) && (throwable instanceof Exception || throwable instanceof AssertionError)) {
            return ExceptionUtils.wrapJavaException(throwable, this, PFactory.createBaseException(language, SystemError, ErrorMessages.M, new Object[]{throwable}));
        }
        PException wrapped = ExceptionUtils.wrapJavaExceptionIfApplicable(bytecodeNode, throwable);
        return wrapped != null ? wrapped : throwable;
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        if (ex instanceof PException pe) {
            pe.setCatchLocation(bci, bytecodeNode);

            if (needsTraceAndProfileInstrumentation() && !getThreadState().isTracing()) {
                pe = traceException(frame, bytecodeNode, bci, pe);
            }

            // Fill in the __context__, if available.
            if (getCaughtExceptionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
            }
            AbstractTruffleException context = getCaughtExceptionNode.execute(frame);
            if (context instanceof PException pe2) {
                if (chainExceptionsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    chainExceptionsNode = insert(ChainExceptionsNode.create());
                }
                chainExceptionsNode.execute(pe, pe2);
            }
            return pe;
        }

        return ex;
    }

    @Override
    public boolean setsUpCalleeContext() {
        return true;
    }

    @Override
    public String getName() {
        if (co == null) {
            // getName can be called by validation code before the code unit has been set.
            return null;
        }
        return co.name.toJavaStringUncached();
    }

    @Override
    public String getQualifiedName() {
        if (co == null) {
            // getQualifiedName can be called by validation code before the code unit has been set.
            return null;
        }
        return co.qualname.toJavaStringUncached();
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    public BytecodeDSLCodeUnit getCodeUnit() {
        return co;
    }

    public int getFirstLineno() {
        return co.startLine;
    }

    protected Source getSource() {
        SourceSection section = getSourceSection();
        if (section == null) {
            return PythonUtils.createFakeSource();
        }
        return section.getSource();
    }

    @Override
    public abstract boolean isCaptureFramesForTrace(boolean compiledFrame);

    @TruffleBoundary
    public int bciToLine(int bci, BytecodeNode bytecodeNode) {
        SourceSection sourceSection = getSourceSectionForLocation(bci, bytecodeNode);
        if (sourceSection != null) {
            return sourceSection.getStartLine();
        }
        return -1;
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(BytecodeLocation location) {
        SourceSection sourceSection = null;
        if (location != null) {
            sourceSection = location.getSourceLocation();
        }

        if (sourceSection == null) {
            /**
             * If we don't have a source section, fall back on the root node's source section. This
             * can happen, for example, when throwing into an unstarted generator, where we don't
             * have an actual location (and the first line of the root node is expected).
             */
            sourceSection = getSourceSection();
        }

        return sourceSection;
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(int bci, BytecodeNode bytecodeNode) {
        BytecodeLocation location = null;
        if (bytecodeNode != null) {
            location = bytecodeNode.getBytecodeLocation(bci);
        }
        return getSourceSectionForLocation(location);
    }

    public static int bciToLasti(int bci, BytecodeNode bytecodeNode) {
        if (bci <= 0) {
            return bci;
        }
        int number = 0;
        for (Instruction instruction : bytecodeNode.getInstructions()) {
            if (instruction.isInstrumentation()) {
                continue;
            }
            if (instruction.getBytecodeIndex() >= bci) {
                return number;
            }
            // Emulate CPython's fixed 2-word instructions.
            number += 2;
        }
        return -1;
    }

    public static int lastiToBci(int lasti, BytecodeNode bytecodeNode) {
        if (lasti < 0) {
            return 0;
        }
        Iterator<Instruction> iter = bytecodeNode.getInstructions().iterator();
        assert iter.hasNext();

        int nexti = 0;
        Instruction result;
        do {
            result = iter.next();
            if (result.isInstrumentation()) {
                continue;
            }
            nexti += 2;
        } while (nexti <= lasti && iter.hasNext());
        return result.getBytecodeIndex();
    }

    @Override
    protected byte[] extractCode() {
        return MarshalModuleBuiltins.serializeCodeUnit(null, PythonContext.get(this), co);
    }

    private static Object checkUnboundCell(PCell cell, int index, PBytecodeDSLRootNode rootNode, Node inliningTarget, PRaiseNode raiseNode) {
        Object result = cell.getRef();
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CodeUnit codeUnit = rootNode.getCodeUnit();
            if (index < codeUnit.cellvars.length) {
                TruffleString localName = codeUnit.cellvars[index];
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
            } else {
                TruffleString localName = codeUnit.freevars[index - codeUnit.cellvars.length];
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, localName);
            }
        }
        return result;
    }

    public PCell readClassCell(Frame frame) {
        if (classcellIndex < 0) {
            return null;
        }
        return (PCell) getBytecodeNode().getLocalValue(0, frame, classcellIndex);
    }

    public boolean hasSelf() {
        return selfIndex >= 0;
    }

    public Object readSelf(Frame frame) {
        if (selfIndex < 0) {
            return null;
        } else if (selfIndex == 0) {
            return getBytecodeNode().getLocalValue(0, frame, 0);
        } else {
            PCell selfCell = (PCell) getBytecodeNode().getLocalValue(0, frame, selfIndex);
            return selfCell.getRef();
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class ArrayIndex {
        @Specialization
        public static Object doObject(int i, Object[] array) {
            return array[i];
        }
    }

    @Operation
    public static final class UnwrapException {
        @Specialization
        public static Object doPException(PException ex) {
            return ex.getEscapedException();
        }

        @Fallback
        public static Object doOther(Object ex) {
            // Let interop exceptions be
            assert ex instanceof AbstractTruffleException;
            return ex;
        }
    }

    /**
     * This yield is always the first instruction executed in a generator function after the usual
     * function prolog.
     * <p>
     * This allows us to have the same root node calling convention and type for generator functions
     * and for regular functions and don't make any distinction between them on the caller side.
     * <p>
     * Moreover, we can share the same root node for the generator function, and the generator
     * function body, which follows after this first "internal" yield. At this point when we are
     * creating the {@link PGenerator} object, we already have what will be the generator frame with
     * all the prolog executed (so arguments shuffled into the right locals, etc.). Note: Python
     * provides access to the generator frame even before the generator was started.
     */
    @Yield
    @SuppressWarnings("truffle-interpreted-performance") // blocked by GR-69979
    public static final class YieldGenerator {
        @Specialization
        public static Object doYield(
                        @Bind MaterializedFrame continuationFrame,
                        @Bind Node inliningTarget,
                        @Bind ContinuationRootNode continuationRootNode,
                        @Bind PBytecodeDSLRootNode innerRoot,
                        @Bind BytecodeNode bytecodeNode,
                        @Cached InlinedConditionProfile isIterableCoroutine) {
            Object result = createGenerator(continuationFrame, inliningTarget, continuationRootNode, innerRoot, isIterableCoroutine);
            if (innerRoot.needsTraceAndProfileInstrumentation()) {
                innerRoot.getThreadState().popInstrumentationData(innerRoot);
            }
            innerRoot.calleeContext.exit(continuationFrame, innerRoot, bytecodeNode);
            return result;
        }

        private static PythonAbstractObject createGenerator(MaterializedFrame continuationFrame, Node inliningTarget,
                        ContinuationRootNode continuationRootNode, PBytecodeDSLRootNode innerRoot,
                        InlinedConditionProfile isIterableCoroutine) {
            Object[] arguments = continuationFrame.getArguments();
            PFunction generatorFunction = PArguments.getFunctionObject(arguments);
            assert generatorFunction != null;
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            if (innerRoot.getCodeUnit().isGenerator()) {
                // if CO_ITERABLE_COROUTINE was explicitly set (likely by types.coroutine), we have
                // to pass the information to the generator .gi_code.co_flags will still be wrong,
                // but at least await will work correctly
                if (isIterableCoroutine.profile(inliningTarget, (generatorFunction.getCode().getFlags() & 0x100) != 0)) {
                    return PFactory.createIterableCoroutine(language, generatorFunction, innerRoot, arguments, continuationRootNode, continuationFrame);
                } else {
                    return PFactory.createGenerator(language, generatorFunction, innerRoot, arguments, continuationRootNode, continuationFrame);
                }
            } else if (innerRoot.getCodeUnit().isCoroutine()) {
                return PFactory.createCoroutine(language, generatorFunction, innerRoot, arguments, continuationRootNode, continuationFrame);
            } else if (innerRoot.getCodeUnit().isAsyncGenerator()) {
                return PFactory.createAsyncGenerator(language, generatorFunction, innerRoot, continuationRootNode, continuationFrame);
            }
            throw CompilerDirectives.shouldNotReachHere("Unknown generator/coroutine type");
        }
    }

    /**
     * Resumes execution after the artificial yield of the generator object
     * ({@link YieldGenerator}).
     */
    @Operation(storeBytecodeIndex = true)
    public static final class ResumeYieldGenerator {
        @Specialization
        public static void doObject(VirtualFrame frame, Object generator,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci,
                        @Cached GetSendValueNode getSendValue) {
            root.calleeContext.enter(frame);
            if (root.needsTraceAndProfileInstrumentation()) {
                // We may not have reparsed the root with instrumentation yet.
                root.ensureTraceAndProfileEnabled();
                root.getThreadState().pushInstrumentationData(root);
                root.traceOrProfileCall(frame, bytecode, bci);
            }
        }
    }

    /**
     * Performs some clean-up steps before suspending execution, and updates the generator state.
     */
    @Yield
    @SuppressWarnings("truffle-interpreted-performance") // blocked by GR-69979
    @ConstantOperand(type = LocalAccessor.class)
    public static final class YieldFromGenerator {
        @Specialization
        public static Object doObject(LocalAccessor currentGeneratorException, Object value,
                        @Bind ContinuationRootNode continuationRootNode,
                        @Bind MaterializedFrame frame,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode) {
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, bytecode, value);
                root.getThreadState().popInstrumentationData(root);
            }

            if (!currentGeneratorException.isCleared(bytecode, frame)) {
                Object genEx = currentGeneratorException.getObject(bytecode, frame);
                if (genEx instanceof PException pe) {
                    /*
                     * The frame reference is only valid for this particular resumption of the
                     * generator, so we need to materialize the frame to make sure the traceback
                     * will still be valid in the next resumption.
                     */
                    pe.markEscaped();
                }
            }

            // we may need to synchronize the generator's frame locals to the PFrame if it escaped
            root.calleeContext.exit(frame, root, bytecode);
            return ContinuationResult.create(continuationRootNode, frame, value);
        }
    }

    /**
     * Some operations take a single Object[] operand (e.g., {@link MakeTuple}). To pass a
     * fixed-length sequence of elements to these operands (e.g., to instantiate a constant tuple)
     * we need to first collect the values into an Object[].
     */
    @Operation
    public static final class CollectToObjectArray {
        @Specialization
        public static Object[] perform(@Variadic Object[] values) {
            return values;
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class Contains {
        @Specialization
        static Object contains(VirtualFrame frame, Object item, Object container,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, container, item);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class WriteName {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name, Object value,
                        @Cached WriteNameNode writeNode) {
            writeNode.execute(frame, name, value);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class ReadName {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadNameNode readNode) {
            return readNode.execute(frame, name);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteName {
        @Specialization(guards = "hasLocals(frame)")
        public static void performLocals(VirtualFrame frame, TruffleString name,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDelItem deleteNode) {
            deleteNode.execute(frame, inliningTarget, PArguments.getSpecialArgument(frame), name);
        }

        @Specialization(guards = "!hasLocals(frame)")
        public static void performGlobals(VirtualFrame frame, TruffleString name,
                        @Cached DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame), name);
        }

        public static boolean hasLocals(VirtualFrame frame) {
            return PArguments.getSpecialArgument(frame) != null;
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class)
    public static final class CopyArguments {
        @Specialization
        @ExplodeLoop
        public static void perform(VirtualFrame frame, LocalRangeAccessor locals,
                        @Bind BytecodeNode bytecodeNode) {
            for (int i = 0; i < locals.getLength(); i++) {
                locals.setObject(bytecodeNode, frame, i, PArguments.getArgument(frame, i));
            }
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class)
    public static final class LoadVariableArguments {
        @Specialization
        public static Object perform(VirtualFrame frame, int index,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createTuple(rootNode.getLanguage(), (Object[]) PArguments.getArgument(frame, index));
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class)
    public static final class LoadKeywordArguments {
        @Specialization
        public static Object perform(VirtualFrame frame, int index,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createDict(rootNode.getLanguage(), (PKeyword[]) PArguments.getArgument(frame, index));
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = double.class)
    @ConstantOperand(type = double.class)
    public static final class LoadComplex {
        @Specialization
        public static Object perform(double real, double imag,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createComplex(rootNode.getLanguage(), real, imag);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = BigInteger.class)
    public static final class LoadBigInt {
        @Specialization
        public static Object perform(BigInteger bigInt,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createInt(rootNode.getLanguage(), bigInt);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = byte[].class, dimensions = 0)
    public static final class LoadBytes {
        @Specialization
        public static Object perform(byte[] bytes,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createBytes(rootNode.getLanguage(), bytes);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class GetIter {
        @Specialization
        public static Object perform(VirtualFrame frame, Object receiver,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIterNode) {
            return getIterNode.execute(frame, inliningTarget, receiver);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class FormatStr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class FormatRepr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class FormatAscii {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsciiNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class PrintExpr {
        @Specialization
        public static void perform(VirtualFrame frame, Object object,
                        @Cached PrintExprNode printExprNode) {
            printExprNode.execute(frame, object);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class MatchKeys {
        @Specialization
        public static boolean perform(VirtualFrame frame, LocalAccessor values, Object map, Object[] keys,
                        @Bind BytecodeNode bytecodeNode,
                        @Cached MatchKeysNode node) {
            values.setObject(bytecodeNode, frame, node.execute(frame, map, keys));
            return node.execute(frame, map, keys) != PNone.NONE;
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class MatchClass {
        @Specialization
        public static Object perform(VirtualFrame frame, LocalAccessor attributes, Object subject, Object type, int nargs, TruffleString[] kwArgs, @Bind BytecodeNode bytecodeNode,
                        @Cached MatchClassNode node) {
            Object attrs = node.execute(frame, subject, type, nargs, kwArgs);
            attributes.setObject(bytecodeNode, frame, attrs);
            return attrs != null;
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class, name = "name")
    @ConstantOperand(type = TruffleString.class, name = "qualifiedName")
    @ConstantOperand(type = BytecodeDSLCodeUnitAndRoot.class)
    public static final class MakeFunction {
        @Specialization(guards = "isSingleContext(rootNode)")
        public static Object functionSingleContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnitAndRoot codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached("createCode(rootNode, codeUnit)") PCode cachedCode,
                        @Shared @Cached DynamicObject.PutNode putNode) {
            return createFunction(frame, name, qualifiedName, codeUnit.getCodeUnit().getDocstring(),
                            cachedCode, defaults, kwDefaultsObject, closure, annotations, rootNode, putNode);
        }

        @Specialization(replaces = "functionSingleContext")
        public static Object functionMultiContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnitAndRoot codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Shared @Cached DynamicObject.PutNode putNode) {
            PCode code = createCode(rootNode, codeUnit);
            return createFunction(frame, name, qualifiedName, codeUnit.getCodeUnit().getDocstring(),
                            code, defaults, kwDefaultsObject, closure, annotations, rootNode, putNode);
        }

        @Idempotent
        protected static boolean isSingleContext(Node node) {
            return PythonLanguage.get(node).isSingleContext();
        }

        @NeverDefault
        protected static PCode createCode(PBytecodeDSLRootNode outerRootNode, BytecodeDSLCodeUnitAndRoot codeUnit) {
            PBytecodeDSLRootNode rootNode = codeUnit.getRootNode(outerRootNode);
            return PFactory.createCode(
                            PythonLanguage.get(outerRootNode),
                            rootNode.getCallTarget(),
                            rootNode.getSignature(),
                            codeUnit.getCodeUnit());
        }

        protected static PFunction createFunction(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName, TruffleString doc,
                        PCode code, Object[] defaults,
                        Object[] kwDefaultsObject, Object closure, Object annotations,
                        PBytecodeDSLRootNode node,
                        DynamicObject.PutNode putNode) {
            PKeyword[] kwDefaults = new PKeyword[kwDefaultsObject.length];
            // Note: kwDefaultsObject should be a result of operation MakeKeywords, which produces
            // PKeyword[]
            System.arraycopy(kwDefaultsObject, 0, kwDefaults, 0, kwDefaults.length);
            PFunction function = PFactory.createFunction(PythonLanguage.get(node), name, qualifiedName, code, PArguments.getGlobals(frame), defaults, kwDefaults, (PCell[]) closure);

            if (annotations != null) {
                putNode.execute(function, T___ANNOTATIONS__, annotations);
            }
            if (doc != null) {
                putNode.execute(function, T___DOC__, doc);
            }

            return function;
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class Pow {
        @Specialization
        public static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberPowerNode powNode) {
            return powNode.execute(frame, left, right);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class InPlacePow {
        @Specialization
        public static Object doIt(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlacePowerNode ipowNode) {
            return ipowNode.execute(frame, left, right);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ForIterate {

        @Specialization
        public static boolean doIntegerSequence(VirtualFrame frame, LocalAccessor output, PIntegerSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            return doInteger(frame, output, iterator, bytecode);
        }

        @Specialization
        public static boolean doIntRange(VirtualFrame frame, LocalAccessor output, PIntRangeIterator iterator,
                        @Bind BytecodeNode bytecode) {
            return doInteger(frame, output, iterator, bytecode);
        }

        private static boolean doInteger(VirtualFrame frame, LocalAccessor output,
                        PIntegerIterator iterator, BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setInt(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doObjectIterator(VirtualFrame frame, LocalAccessor output, PObjectSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                output.setObject(bytecode, frame, null);
                return false;
            }
            Object value = iterator.next();
            output.setObject(bytecode, frame, value);
            return value != null;
        }

        @Specialization
        public static boolean doLongIterator(VirtualFrame frame, LocalAccessor output, PLongSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setLong(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doDoubleIterator(VirtualFrame frame, LocalAccessor output, PDoubleSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setDouble(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doIterator(VirtualFrame frame, LocalAccessor output, Object object,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached PyIterNextNode next,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                Object value = next.execute(frame, inliningTarget, object);
                output.setObject(bytecode, frame, value);
                return true;
            } catch (IteratorExhausted e) {
                output.setObject(bytecode, frame, null);
                return false;
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class GetMethod {
        @Specialization
        public static Object doIt(VirtualFrame frame,
                        TruffleString name, Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetMethod getMethod) {
            return getMethod.execute(frame, inliningTarget, obj, name);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class GetAttribute {
        @Specialization
        public static Object doIt(VirtualFrame frame,
                        TruffleString name,
                        Object obj,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            return getAttributeNode.execute(frame, obj);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class SetAttribute {
        @Specialization
        public static void doIt(VirtualFrame frame,
                        TruffleString key,
                        Object value,
                        Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttr setAttrNode) {
            setAttrNode.execute(frame, inliningTarget, object, key, value);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteAttribute {
        @Specialization
        @InliningCutoff
        public static void doObject(VirtualFrame frame,
                        TruffleString key,
                        Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttrO setAttrO) {
            setAttrO.execute(frame, inliningTarget, object, key, PNone.NO_VALUE);

        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class DeleteItem {
        @Specialization
        public static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDelItem delItemNode) {
            delItemNode.execute(frame, inliningTarget, primary, index);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class ReadGlobal {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame, name);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class WriteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name, Object value,
                        @Cached WriteGlobalNode writeNode) {
            writeNode.executeObject(frame, name, value);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name,
                        @Cached DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame), name);
        }
    }

    /**
     * Returns the {@code __build_class__} builtin.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class LoadBuildClass {

        public static final TruffleString NAME = BuiltinNames.T___BUILD_CLASS__;

        @Specialization
        @InliningCutoff
        public static Object perform(VirtualFrame frame,
                        @Cached ReadBuiltinNode readNode) {
            return readNode.execute(NAME);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MakeList {
        @Specialization(guards = "elements.length == 0")
        public static PList doEmpty(@Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            // Common pattern is to create an empty list and then add items.
            // We need to start from empty storage, so that we can specialize to, say, int storage
            // if only ints are appended to this list
            return PFactory.createList(rootNode.getLanguage(), EmptySequenceStorage.INSTANCE);
        }

        @Specialization(guards = "elements.length > 0")
        public static PList perform(@Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createList(rootNode.getLanguage(), elements);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ImportStatic({PBytecodeDSLRootNode.class})
    public static final class MakeSet {
        @Specialization(guards = "elements.length == 0")
        public static PSet doEmpty(VirtualFrame frame, @Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            // creates set backed by empty HashingStorage
            return PFactory.createSet(rootNode.getLanguage());
        }

        @Specialization(guards = "elements.length != 0")
        @StoreBytecodeIndex
        public static PSet doNonEmpty(VirtualFrame frame, @Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached MakeSetStorageNode makeSetStorageNode) {
            return PFactory.createSet(rootNode.getLanguage(), makeSetStorageNode.execute(frame, inliningTarget, elements));
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MakeFrozenSet {
        @Specialization(guards = "elements.length == 0")
        public static PFrozenSet doEmpty(VirtualFrame frame, @Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            // creates set backed by empty HashingStorage
            return PFactory.createFrozenSet(rootNode.getLanguage());
        }

        @Specialization(guards = "elements.length != 0")
        @StoreBytecodeIndex
        public static PFrozenSet doNonEmpty(VirtualFrame frame, @Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached MakeSetStorageNode makeSetStorageNode) {
            return PFactory.createFrozenSet(rootNode.getLanguage(), makeSetStorageNode.execute(frame, inliningTarget, elements));
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MakeTuple {
        @Specialization
        public static Object perform(@Variadic Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createTuple(rootNode.getLanguage(), elements);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int[].class, dimensions = 0)
    public static final class MakeConstantIntList {
        @Specialization
        public static PList perform(int[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return PFactory.createList(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = long[].class, dimensions = 0)
    public static final class MakeConstantLongList {
        @Specialization
        public static PList perform(long[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return PFactory.createList(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean[].class, dimensions = 0)
    public static final class MakeConstantBooleanList {
        @Specialization
        public static PList perform(boolean[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return PFactory.createList(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = double[].class, dimensions = 0)
    public static final class MakeConstantDoubleList {
        @Specialization
        public static PList perform(double[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return PFactory.createList(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = Object[].class, dimensions = 0)
    public static final class MakeConstantObjectList {
        @Specialization
        public static PList perform(Object[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return PFactory.createList(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int[].class, dimensions = 0)
    public static final class MakeConstantIntTuple {
        @Specialization
        public static PTuple perform(int[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(array);
            return PFactory.createTuple(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = long[].class, dimensions = 0)
    public static final class MakeConstantLongTuple {
        @Specialization
        public static PTuple perform(long[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(array);
            return PFactory.createTuple(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean[].class, dimensions = 0)
    public static final class MakeConstantBooleanTuple {
        @Specialization
        public static PTuple perform(boolean[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(array);
            return PFactory.createTuple(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = double[].class, dimensions = 0)
    public static final class MakeConstantDoubleTuple {
        @Specialization
        public static PTuple perform(double[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(array);
            return PFactory.createTuple(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = Object[].class, dimensions = 0)
    public static final class MakeConstantObjectTuple {
        @Specialization
        public static PTuple perform(Object[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(array);
            return PFactory.createTuple(rootNode.getLanguage(), storage);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MakeSlice {

        @Specialization
        public static Object doIII(int start, int end, int step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createIntSlice(rootNode.getLanguage(), start, end, step);
        }

        @Specialization
        public static Object doNIN(PNone start, int end, PNone step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createIntSlice(rootNode.getLanguage(), 0, end, 1, true, true);
        }

        @Specialization
        public static Object doIIN(int start, int end, PNone step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createIntSlice(rootNode.getLanguage(), start, end, 1, false, true);
        }

        @Specialization
        public static Object doNII(PNone start, int end, int step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createIntSlice(rootNode.getLanguage(), 0, end, step, true, false);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(Object start, Object end, Object step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return PFactory.createObjectSlice(rootNode.getLanguage(), start, end, step);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = TruffleString[].class, dimensions = 0, specifyAtEnd = true)
    public static final class MakeKeywords {
        @Specialization
        public static PKeyword[] perform(@Variadic Object[] values, TruffleString[] keys) {
            CompilerAsserts.partialEvaluationConstant(keys.length);
            PKeyword[] result = new PKeyword[keys.length];
            for (int i = 0; i < keys.length; i++) {
                result[i] = new PKeyword(keys[i], values[i]);
            }
            return result;
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MappingToKeywords {
        @Specialization
        public static PKeyword[] perform(Object sourceCollection,
                        @Bind Node inliningTarget,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached PRaiseNode raise) {
            return expandKeywordStarargsNode.execute(inliningTarget, sourceCollection);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class)
    public static final class MakeDict {
        @Specialization(guards = "entries == 0")
        public static PDict empty(VirtualFrame frame, int entries, @Variadic Object[] keysAndValues,
                        @Bind PBytecodeDSLRootNode rootNode) {
            // creates a dict with empty hashing storage
            return PFactory.createDict(rootNode.getLanguage());
        }

        @Specialization
        @StoreBytecodeIndex
        public static PDict empty(VirtualFrame frame, int entries, @Variadic Object[] keysAndValues,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.PutNode putNode,
                        @Cached DictNodes.UpdateNode updateNode) {
            if (keysAndValues.length != entries * 2) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            ObjectHashMap map = new ObjectHashMap(keysAndValues.length / 2);
            PDict dict = PFactory.createDict(rootNode.getLanguage(), new EconomicMapStorage(map, false));
            for (int i = 0; i < entries; i++) {
                Object key = keysAndValues[i * 2];
                Object value = keysAndValues[i * 2 + 1];
                // Each entry represents either a k: v pair or a **splats. splats have no key.
                if (key == PNone.NO_VALUE) {
                    updateNode.execute(frame, dict, value);
                    assert dict.getDictStorage() instanceof EconomicMapStorage es && es.mapIsEqualTo(map);
                } else {
                    long hash = hashNode.execute(frame, inliningTarget, key);
                    putNode.put(frame, inliningTarget, map, key, hash, value);
                }
            }
            return dict;
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class SetDictItem {
        @Specialization
        public static void perform(VirtualFrame frame, PDict item, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageSetItem setItem) {
            item.setDictStorage(setItem.execute(frame, inliningTarget, item.getDictStorage(), key, value));
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class SetItem {
        @Specialization
        public static void doIt(VirtualFrame frame, Object value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackToLocals {
        @ExplodeLoop
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static void doUnpackSequence(VirtualFrame localFrame, LocalRangeAccessor results, PSequence sequence,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            if (len != count) {
                raiseError(inliningTarget, raiseNode, len, count);
            }

            for (int i = 0; i < count; i++) {
                results.setObject(bytecode, localFrame, i, getItemNode.execute(inliningTarget, storage, i));
            }
        }

        @InliningCutoff
        private static void raiseError(Node inliningTarget, PRaiseNode raiseNode, int len, int count) {
            if (len < count) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
            } else {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
            }
        }

        @Specialization
        @ExplodeLoop
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame virtualFrame, LocalRangeAccessor results, Object collection,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            for (int i = 0; i < count; i++) {
                try {
                    Object value = getNextNode.execute(virtualFrame, inliningTarget, iterator);
                    results.setObject(bytecode, virtualFrame, i, value);
                } catch (IteratorExhausted e) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                Object value = getNextNode.execute(virtualFrame, inliningTarget, iterator);
            } catch (IteratorExhausted e) {
                return;
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ImportStatic({PGuards.class})
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class UnpackStarredToLocals {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static void doUnpackSequence(VirtualFrame localFrame,
                        int starIndex,
                        LocalRangeAccessor results,
                        PSequence sequence,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecode,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            int resultsLength = results.getLength();
            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();

            int starLen = len - resultsLength + 1;
            if (starLen < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            copyToLocalsFromSequence(storage, 0, 0, countBefore, results, localFrame, inliningTarget, bytecode, getItemNode);
            PList starList = PFactory.createList(rootNode.getLanguage(), getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            results.setObject(bytecode, localFrame, starIndex, starList);
            copyToLocalsFromSequence(storage, starIndex + 1, len - countAfter, countAfter, results, localFrame, inliningTarget, bytecode, getItemNode);
        }

        @Specialization
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame frame,
                        int starIndex,
                        LocalRangeAccessor results,
                        Object collection,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecode,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            int resultsLength = results.getLength();
            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            Object iterator;
            try {
                iterator = getIter.execute(frame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            copyToLocalsFromIterator(frame, inliningTarget, iterator, countBefore, results, bytecode, countBefore + countAfter, getNextNode, raiseNode);

            PList starAndAfter = constructListNode.execute(frame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                results.setObject(bytecode, frame, starIndex, starAndAfter);
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = PFactory.createList(rootNode.getLanguage(), getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                results.setObject(bytecode, frame, starIndex, starList);

                copyToLocalsFromSequence(storage, starIndex + 1, starLen, countAfter, results, frame, inliningTarget, bytecode, getItemNode);
            }
        }

        private static void copyToLocalsFromIterator(VirtualFrame frame, Node inliningTarget, Object iterator, int length, LocalRangeAccessor results,
                        BytecodeNode bytecode, int requiredLength,
                        PyIterNextNode getNextNode, PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                try {
                    Object item = getNextNode.execute(frame, inliningTarget, iterator);
                    results.setObject(bytecode, frame, i, item);
                } catch (IteratorExhausted e) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, requiredLength, i);
                }
            }
        }

        private static void copyToLocalsFromSequence(SequenceStorage storage, int runOffset, int offset, int length, LocalRangeAccessor run,
                        VirtualFrame localFrame, Node inliningTarget, BytecodeNode bytecode, SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                run.setObject(bytecode, localFrame, runOffset + i, getItemNode.execute(inliningTarget, storage, offset + i));
            }
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Le {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left <= right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_LE);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Lt {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left < right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_LT);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Ge {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left >= right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_GE);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Gt {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left > right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_GT);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Eq {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.EqualNode equalNode) {
            return equalNode.execute(left, right, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left == right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_EQ);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class Ne {
        @Specialization
        public static boolean cmp(int left, int right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.EqualNode equalNode) {
            return !equalNode.execute(left, right, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left != right;
        }

        @Specialization
        @InliningCutoff
        @StoreBytecodeIndex
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached GenericRichCompare richCompareNode) {
            return richCompareNode.execute(frame, left, right, RichCmpOp.Py_NE);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    @ConstantOperand(type = TruffleString[].class, dimensions = 0)
    @ConstantOperand(type = int.class)
    public static final class Import {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, TruffleString name, TruffleString[] fromList, int level,
                        @Cached ImportNode node) {
            return node.execute(frame, name, PArguments.getGlobals(frame), fromList, level);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class ImportFrom {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, TruffleString name, Object module,
                        @Cached ImportFromNode node) {
            return node.execute(frame, module, name);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    @ConstantOperand(type = int.class)
    public static final class ImportStar {
        @Specialization
        @InliningCutoff
        public static void doImport(VirtualFrame frame, TruffleString name, int level,
                        @Cached("create(name, level)") ImportStarNode node) {
            node.execute(frame, name, level);
        }

        @NeverDefault
        static ImportStarNode create(TruffleString name, int level) {
            return ImportStarNode.create();
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class Raise {
        @Specialization
        public static void perform(VirtualFrame frame, Object typeOrExceptionObject, Object cause,
                        @Bind PBytecodeDSLRootNode root,
                        @Cached RaiseNode raiseNode) {
            raiseNode.execute(frame, typeOrExceptionObject, cause, !root.isInternal());
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class Reraise {
        @Specialization
        public static void doPException(PException ex,
                        @Bind PBytecodeDSLRootNode root) {
            throw ex.getExceptionForReraise(!root.isInternal());
        }

        @Specialization
        public static void doAbstractTruffleException(AbstractTruffleException ex) {
            throw ex;
        }
    }

    /**
     * Throw is used internally for our try-catch-finally implementation when we need to throw an
     * exception and catch it elsewhere. We don't need to do any of the work done by RaiseNode.
     */
    @Operation
    public static final class Throw {
        @Specialization
        public static void doAbstractTruffleException(AbstractTruffleException ex) {
            throw ex;
        }
    }

    @Operation
    public static final class GetCurrentException {
        @Specialization
        public static AbstractTruffleException doPException(VirtualFrame frame) {
            return PArguments.getException(frame);
        }
    }

    @Operation
    public static final class SetCurrentException {
        @Specialization
        public static void doPException(VirtualFrame frame, Object ex) {
            PArguments.setException(frame, (AbstractTruffleException) ex);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = boolean.class)
    public static final class SetCurrentGeneratorException {
        @Specialization
        public static void doPException(VirtualFrame frame, LocalAccessor currentGeneratorException, boolean clearGeneratorEx, Object ex,
                        @Bind BytecodeNode bytecode) {
            if (clearGeneratorEx) {
                currentGeneratorException.clear(bytecode, frame);
            } else {
                currentGeneratorException.setObject(bytecode, frame, ex);
            }
            PArguments.setException(frame, (AbstractTruffleException) ex);
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MarkExceptionAsCaught {
        @Specialization
        public static void doPException(VirtualFrame frame, PException ex,
                        @Bind PBytecodeDSLRootNode rootNode) {
            ex.markAsCaught(frame, rootNode);
        }

        @Fallback
        public static void doNothing(@SuppressWarnings("unused") Object ex) {
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class AssertFailed {
        @Specialization
        @InliningCutoff
        public static void doAssertFailed(VirtualFrame frame, Object assertionMessage,
                        @Bind BytecodeNode bytecodeNode) {
            if (assertionMessage == PNone.NO_VALUE) {
                throw PRaiseNode.raiseStatic(bytecodeNode, AssertionError);
            } else {
                throw PRaiseNode.raiseStatic(bytecodeNode, AssertionError, new Object[]{assertionMessage});
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class LoadCell {
        @Specialization
        public static Object doLoadCell(int index, PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
        }
    }

    /**
     * Attempts to read a value from a dict (argument), and if not present, reads a local cell
     * variable (next argument). The immediate operand is the cell index, so that we can find out
     * the name of the variable for the dict lookup.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class LoadFromDictOrCell {
        @Specialization
        public static Object doLoadCell(VirtualFrame frame, int index, Object locals, PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached ReadFromLocalsNode readLocalsNode,
                        @Cached PRaiseNode raiseNode) {
            CodeUnit co = rootNode.getCodeUnit();
            TruffleString name;
            if (index < co.cellvars.length) {
                name = co.cellvars[index];
            } else {
                name = co.freevars[index - co.cellvars.length];
            }
            Object value = readLocalsNode.execute(frame, inliningTarget, locals, name);
            if (value != PNone.NO_VALUE) {
                return value;
            } else {
                return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            }
        }
    }

    /**
     * Attempts to read a value from a dict (argument), and if not present, reads a global variable
     * of given name (immediate argument).
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = TruffleString.class)
    public static final class LoadFromDictOrGlobals {
        @Specialization
        public static Object doLoadCell(VirtualFrame frame, TruffleString name, Object dict,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached ReadGlobalOrBuiltinNode readGlobal,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            Object value;
            try {
                value = getItemNode.execute(frame, inliningTarget, dict, name);
            } catch (PException e) {
                e.expect(inliningTarget, KeyError, errorProfile);
                value = readGlobal.read(frame, PArguments.getGlobals(frame), name);
            }
            return value;
        }
    }

    @Operation
    public static final class LoadSpecialArgument {
        @Specialization
        public static Object doLoadCell(VirtualFrame frame) {
            return PArguments.getSpecialArgument(frame);
        }
    }

    @Operation
    public static final class StoreCell {
        @Specialization
        public static void doStoreCell(PCell cell, Object value) {
            cell.setRef(value);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class)
    public static final class CreateCells {
        @Specialization
        @ExplodeLoop
        public static void doCreateCells(VirtualFrame frame, LocalRangeAccessor locals,
                        @Bind PBytecodeDSLRootNode rootNode) {
            for (int i = 0; i < locals.getLength(); i++) {
                PCell cell = new PCell(rootNode.cellEffectivelyFinalAssumptions[i]);
                locals.setObject(rootNode.getBytecodeNode(), frame, i, cell);
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class ClearCell {
        @Specialization
        public static void doClearCell(int index, PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            cell.clearRef();
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ClearLocal {
        @Specialization
        public static void doClearLocal(VirtualFrame frame, LocalAccessor localAccessor,
                        @Bind BytecodeNode bytecode) {
            localAccessor.setObject(bytecode, frame, null);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class)
    public static final class InitFreeVars {
        @Specialization
        @ExplodeLoop
        public static void doLoadClosure(VirtualFrame frame, LocalRangeAccessor locals,
                        @Bind BytecodeNode bytecode) {
            PCell[] closure = PArguments.getFunctionObject(frame.getArguments()).getClosure();
            for (int i = 0; i < locals.getLength(); i++) {
                locals.setObject(bytecode, frame, i, closure[i]);
            }
        }
    }

    @Operation(storeBytecodeIndex = false)
    public static final class MakeCellArray {
        @Specialization
        public static PCell[] doMakeCellArray(@Variadic Object[] cells) {
            return PCell.toCellArray(cells);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class KwargsMerge {
        @Specialization
        public static PDict doMerge(VirtualFrame frame,
                        LocalAccessor callee,
                        PDict dict,
                        Object toMerge,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecodeNode,
                        @Cached ConcatDictToStorageNode concatNode,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Cached PRaiseNode raise) {
            try {
                HashingStorage resultStorage = concatNode.execute(frame, dict.getDictStorage(), toMerge);
                dict.setDictStorage(resultStorage);
            } catch (SameDictKeyException e) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG,
                                PyObjectFunctionStr.execute(frame, boundaryCallData, callee.getObject(bytecodeNode, frame)),
                                e.getKey());
            } catch (NonMappingException e) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING,
                                PyObjectFunctionStr.execute(frame, boundaryCallData, callee.getObject(bytecodeNode, frame)),
                                toMerge);
            }
            return dict;
        }
    }

    @Operation(storeBytecodeIndex = true)
    @Variadic
    @ImportStatic({PGuards.class})
    public static final class UnpackStarredVariadic {
        public static boolean isListOrTuple(PSequence obj, Node inliningTarget, InlinedConditionProfile isListProfile) {
            return isListProfile.profile(inliningTarget, PGuards.isBuiltinList(obj)) || PGuards.isBuiltinTuple(obj);
        }

        @Specialization(guards = "isListOrTuple(seq, inliningTarget, isListProfile)", limit = "1")
        static Object[] fromListOrTuple(PSequence seq,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached InlinedConditionProfile isListProfile,
                        @Exclusive @Cached SequenceNodes.GetPSequenceStorageNode getStorage,
                        @Exclusive @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
            return toArrayNode.execute(inliningTarget, getStorage.execute(inliningTarget, seq));
        }

        @Specialization(guards = "isNoValue(none)")
        static Object[] none(@SuppressWarnings("unused") PNone none) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }

        @Fallback
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame, Object collection,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached PRaiseNode raiseNode) {

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            ArrayBuilder<Object> result = new ArrayBuilder<>();
            while (true) {
                try {
                    Object item = getNextNode.execute(virtualFrame, inliningTarget, iterator);
                    result.add(item);
                } catch (IteratorExhausted e) {
                    return result.toArray(new Object[0]);
                }
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackSequence {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static Object[] doUnpackSequence(VirtualFrame localFrame, int count, PSequence sequence,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(count);
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            if (len != count) {
                throw raiseError(inliningTarget, raiseNode, len, count);
            }
            Object[] result = new Object[len];
            for (int i = 0; i < count; i++) {
                result[i] = getItemNode.execute(inliningTarget, storage, i);
            }
            return result;
        }

        @InliningCutoff
        private static PException raiseError(Node inliningTarget, PRaiseNode raiseNode, int len, int count) {
            if (len < count) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
            } else {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
            }
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame,
                        int count,
                        Object collection,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(count);
            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[count];
            for (int i = 0; i < count; i++) {
                try {
                    Object value = getNextNode.execute(virtualFrame, inliningTarget, iterator);
                    result[i] = value;
                } catch (IteratorExhausted e) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                Object value = getNextNode.execute(virtualFrame, inliningTarget, iterator);
            } catch (IteratorExhausted e) {
                return result;
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = int.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackEx {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static Object[] doUnpackSequence(VirtualFrame localFrame,
                        int countBefore,
                        int countAfter,
                        PSequence sequence,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            int starLen = len - countBefore - countAfter;
            if (starLen < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            Object[] result = new Object[countBefore + 1 + countAfter];
            copyItemsToArray(inliningTarget, storage, 0, result, 0, countBefore, getItemNode);
            result[countBefore] = PFactory.createList(PythonLanguage.get(inliningTarget), getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            copyItemsToArray(inliningTarget, storage, len - countAfter, result, countBefore + 1, countAfter, getItemNode);
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame,
                        int countBefore,
                        int countAfter,
                        Object collection,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[countBefore + 1 + countAfter];
            copyItemsToArray(virtualFrame, inliningTarget, iterator, result, 0, countBefore, countBefore + countAfter, getNextNode, raiseNode);
            PList starAndAfter = constructListNode.execute(virtualFrame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                result[countBefore] = starAndAfter;
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = PFactory.createList(PythonLanguage.get(inliningTarget), getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                result[countBefore] = starList;
                copyItemsToArray(inliningTarget, storage, starLen, result, countBefore + 1, countAfter, getItemNode);
            }
            return result;
        }

        private static void copyItemsToArray(VirtualFrame frame, Node inliningTarget, Object iterator, Object[] destination, int destinationOffset, int length, int totalLength,
                        PyIterNextNode getNextNode, PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(destinationOffset);
            CompilerAsserts.partialEvaluationConstant(length);
            CompilerAsserts.partialEvaluationConstant(totalLength);
            for (int i = 0; i < length; i++) {
                try {
                    Object value = getNextNode.execute(frame, inliningTarget, iterator);
                    destination[destinationOffset + i] = value;
                } catch (IteratorExhausted e) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, destinationOffset + i);
                }
            }
        }

        private static void copyItemsToArray(Node inliningTarget, SequenceStorage source, int sourceOffset, Object[] destination, int destinationOffset, int length,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(sourceOffset);
            CompilerAsserts.partialEvaluationConstant(destinationOffset);
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                destination[destinationOffset + i] = getItemNode.execute(inliningTarget, source, sourceOffset + i);
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallNilaryMethod {
        @Specialization
        public static Object doCall(VirtualFrame frame, Object callable,
                        @Cached CallNode node) {
            return node.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallUnaryMethod {
        @Specialization
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0,
                        @Cached CallUnaryMethodNode node) {
            return node.executeObject(frame, callable, arg0);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallBinaryMethod {
        @Specialization
        public static Object doObject(VirtualFrame frame, Object callable, Object arg0, Object arg1,
                        @Cached CallBinaryMethodNode node) {
            return node.executeObject(frame, callable, arg0, arg1);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallTernaryMethod {
        @Specialization
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2,
                        @Cached CallTernaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallQuaternaryMethod {
        @Specialization
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2, Object arg3,
                        @Cached CallQuaternaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2, arg3);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class CallVarargsMethod {
        @Specialization
        public static Object doCall(VirtualFrame frame, Object callable, Object[] args, PKeyword[] keywords,
                        @Cached CallNode node) {
            return node.execute(frame, callable, args, keywords);
        }
    }

    /**
     * Specialized call operation for comprehension code units. These are never built-in functions
     * and never change, so the optimizations in the {@link CallUnaryMethod} operation are neither
     * applicable nor needed.
     */
    @Operation(storeBytecodeIndex = true)
    @ImportStatic(CallDispatchers.class)
    public static final class CallComprehension {
        @Specialization
        public static Object doObject(VirtualFrame frame, PFunction callable, Object arg,
                        @Bind Node inliningTarget,
                        @Cached("createDirectCallNodeFor(callable)") DirectCallNode callNode,
                        @Cached CallDispatchers.FunctionDirectInvokeNode invoke) {
            Object[] args = PArguments.create(1);
            args[PArguments.USER_ARGUMENTS_OFFSET] = arg;
            return invoke.execute(frame, inliningTarget, callNode, callable, args);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ContextManagerEnter {
        @Specialization
        public static void doEnter(VirtualFrame frame,
                        LocalAccessor exitSetter,
                        LocalAccessor resultSetter,
                        Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached GetClassNode getClass,
                        @Cached LookupSpecialMethodNode.Dynamic lookupEnter,
                        @Cached LookupSpecialMethodNode.Dynamic lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, inliningTarget, type, T___ENTER__, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_CONTEXT_MANAGER_PROTOCOL, type);
            }
            Object exit = lookupExit.execute(frame, inliningTarget, type, T___EXIT__, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_CONTEXT_MANAGER_PROTOCOL_EXIT, type);
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(bytecode, frame, exit);
            resultSetter.setObject(bytecode, frame, result);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class ContextManagerExit {
        @Specialization
        public static void doRegular(VirtualFrame frame, PNone none, Object exit, Object contextManager,
                        @Shared @Cached CallQuaternaryMethodNode callExit) {
            callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        }

        @Specialization
        @InliningCutoff
        public static void doExceptional(VirtualFrame frame,
                        Object exception, Object exit, Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue) {
            AbstractTruffleException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException pException) {
                    PArguments.setException(frame, pException);
                    pythonException = pException.getEscapedException();
                }
                Object excType = getClass.execute(inliningTarget, pythonException);
                Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
                Object result = callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
                if (!isTrue.execute(frame, result)) {
                    if (exception instanceof PException pException) {
                        throw pException.getExceptionForReraise(!rootNode.isInternal());
                    } else if (exception instanceof AbstractTruffleException ate) {
                        throw ate;
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
                    }
                }
            } finally {
                PArguments.setException(frame, savedExcState);
            }
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class AsyncContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame,
                        LocalAccessor exitSetter,
                        LocalAccessor resultSetter,
                        Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached GetClassNode getClass,
                        @Cached LookupSpecialMethodNode.Dynamic lookupEnter,
                        @Cached LookupSpecialMethodNode.Dynamic lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, inliningTarget, type, T___AENTER__, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_THE_ASYNC_CONTEXT_MANAGER_PROTOCOL, type);
            }
            Object exit = lookupExit.execute(frame, inliningTarget, type, T___AEXIT__, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_THE_ASYNC_CONTEXT_MANAGER_PROTOCOL_AEXIT, type);
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(bytecode, frame, exit);
            resultSetter.setObject(bytecode, frame, result);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ImportStatic(PGuards.class)
    public static final class AsyncContextManagerCallExit {
        @Specialization
        @InliningCutoff
        public static Object doRegular(VirtualFrame frame,
                        PNone none, Object exit, Object contextManager,
                        @Shared @Cached CallQuaternaryMethodNode callExit) {
            return callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        }

        @Specialization(guards = "!isNone(exception)")
        @InliningCutoff
        public static Object doExceptional(VirtualFrame frame,
                        Object exception, Object exit, Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue) {
            // The exception should be set as the current exception already
            assert exception == PArguments.getException(frame) : String.format("%s != %s", exception, PArguments.getException(frame));
            Object pythonException = exception;
            if (exception instanceof PException) {
                pythonException = ((PException) exception).getEscapedException();
            }
            Object excType = getClass.execute(inliningTarget, pythonException);
            Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
            return callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class AsyncContextManagerExit {
        /**
         * NB: There is nothing to do after awaiting __exit__(None, None, None), so this operation
         * is only emitted for the case where the context manager exits due to an exception.
         */
        @Specialization
        @InliningCutoff
        public static void doExceptional(VirtualFrame frame,
                        Object exception, Object result,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue) {
            if (!isTrue.execute(frame, result)) {
                if (exception instanceof PException) {
                    throw ((PException) exception).getExceptionForReraise(!rootNode.isInternal());
                } else if (exception instanceof AbstractTruffleException) {
                    throw (AbstractTruffleException) exception;
                } else {
                    throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
                }
            }
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class)
    public static final class BuildString {
        @Specialization
        public static Object perform(
                        int length,
                        @Variadic Object[] strings,
                        @Cached TruffleStringBuilder.AppendStringNode appendNode,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            var tsb = TruffleStringBuilderUTF32.create(PythonUtils.TS_ENCODING);
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                appendNode.execute(tsb, (TruffleString) strings[i]);
            }
            return toString.execute(tsb);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class TeeLocal {
        @Specialization
        public static int doInt(VirtualFrame frame, LocalAccessor local, int value,
                        @Bind BytecodeNode bytecode) {
            local.setInt(bytecode, frame, value);
            return value;
        }

        @Specialization
        public static double doDouble(VirtualFrame frame, LocalAccessor local, double value,
                        @Bind BytecodeNode bytecode) {
            local.setDouble(bytecode, frame, value);
            return value;
        }

        @Specialization
        public static long doLong(VirtualFrame frame, LocalAccessor local, long value,
                        @Bind BytecodeNode bytecode) {
            local.setLong(bytecode, frame, value);
            return value;
        }

        @Specialization(replaces = {"doInt", "doDouble", "doLong"})
        public static Object doObject(VirtualFrame frame, LocalAccessor local, Object value,
                        @Bind BytecodeNode bytecode) {
            local.setObject(bytecode, frame, value);
            return value;
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class GetLen {
        @Specialization
        public static int doObject(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, inliningTarget, value);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = long.class)
    public static final class CheckTypeFlags {
        @Specialization
        public static boolean doObject(long typeFlags, Object value,
                        @Cached GetTPFlagsNode getTPFlagsNode) {
            return (getTPFlagsNode.execute(value) & typeFlags) != 0;
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ImportStatic(PGuards.class)
    public static final class BinarySubscript {
        static boolean isBuiltinListOrTuple(PTupleListBase s) {
            return PGuards.isBuiltinTuple(s) || PGuards.isBuiltinList(s);
        }

        public static boolean isIntBuiltinListOrTuple(PTupleListBase s) {
            return isBuiltinListOrTuple(s) && s.getSequenceStorage() instanceof IntSequenceStorage;
        }

        @Specialization(guards = "isIntBuiltinListOrTuple(list)")
        public static int doIntStorage(PTupleListBase list, int index,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile negativeIndexProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            IntSequenceStorage storage = (IntSequenceStorage) list.getSequenceStorage();
            int normalizedIndex = NormalizeIndexWithBoundsCheckNode.normalizeIntIndex(inliningTarget, index, storage.length(), list, negativeIndexProfile, raiseNode);
            return storage.getIntItemNormalized(normalizedIndex);
        }

        public static boolean isDoubleBuiltinListOrTuple(PTupleListBase s) {
            return isBuiltinListOrTuple(s) && s.getSequenceStorage() instanceof DoubleSequenceStorage;
        }

        @Specialization(guards = "isDoubleBuiltinListOrTuple(list)")
        public static double doDoubleStorage(PTupleListBase list, int index,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile negativeIndexProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) list.getSequenceStorage();
            int normalizedIndex = NormalizeIndexWithBoundsCheckNode.normalizeIntIndex(inliningTarget, index, storage.length(), list, negativeIndexProfile, raiseNode);
            return storage.getDoubleItemNormalized(normalizedIndex);
        }

        @Specialization(replaces = {"doIntStorage", "doDoubleStorage"}, guards = "isBuiltinListOrTuple(list)")
        public static Object doGenericStorage(PTupleListBase list, int index,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile negativeIndexProfile,
                        @Exclusive @Cached PRaiseNode raiseNode,
                        @Cached GetItemScalarNode getItemScalarNode) {
            SequenceStorage storage = list.getSequenceStorage();
            int normalizedIndex = NormalizeIndexWithBoundsCheckNode.normalizeIntIndex(inliningTarget, index, storage.length(), list, negativeIndexProfile, raiseNode);
            return getItemScalarNode.execute(inliningTarget, storage, normalizedIndex);
        }

        @Fallback
        @InliningCutoff
        public static Object doOther(VirtualFrame frame, Object receiver, Object key,
                        @Bind Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached PyObjectGetItem.PyObjectGetItemGeneric getItemNode) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, receiver);
            return getItemNode.execute(frame, inliningTarget, receiver, slots, key);
        }
    }

    /**
     * Resumes execution after yield.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ResumeYield {
        @Specialization
        public static Object doObject(VirtualFrame frame, LocalAccessor currentGeneratorException, LocalAccessor savedException, Object sendValue,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci,
                        @Cached GetSendValueNode getSendValue) {
            root.calleeContext.enter(frame);
            if (savedException != currentGeneratorException) {
                // We cannot pass `null` as savedException, so savedException ==
                // currentGeneratorException means "no saveException"
                //
                // If we are passed savedException local, it means we are in an except block and the
                // saved exception was fetched from the caller, so what we do is that we override it
                // to the new caller passed exception state (possibly null meaning we need to fetch
                // it via stack-walking if needed).
                AbstractTruffleException callerExceptionState = PArguments.getException(frame.getArguments());
                savedException.setObject(bytecode, frame, callerExceptionState);
            }

            // The generator's exception overrides the exception state passed from the caller
            // GraalPy AST nodes assume that current frame's arguments contain the current exception
            // (or null meaning that stack-walk is required to get it).
            // see also RootNodeCompiler#generatorExceptionStateLocal
            if (!currentGeneratorException.isCleared(bytecode, frame)) {
                Object currentGenEx = currentGeneratorException.getObject(bytecode, frame);
                if (currentGenEx != null) {
                    PArguments.setException(frame, (PException) currentGenEx);
                }
            }

            if (root.needsTraceAndProfileInstrumentation()) {
                // We may not have reparsed the root with instrumentation yet.
                root.ensureTraceAndProfileEnabled();
                root.getThreadState().pushInstrumentationData(root);
                root.traceOrProfileCall(frame, bytecode, bci);
            }

            return getSendValue.execute(sendValue);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class YieldFromSend {
        private static final TruffleString T_SEND = tsLiteral("send");

        @Specialization
        static boolean doGenerator(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        PGenerator generator,
                        Object arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached CommonGeneratorBuiltins.SendNode sendNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = sendNode.execute(virtualFrame, generator, arg);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        static boolean hasIterSlot(TpSlots slots) {
            return PyIterCheckNode.checkSlots(slots);
        }

        @Specialization(guards = "hasIterSlot(slots)", limit = "1")
        static boolean doIterator(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object iter,
                        @SuppressWarnings("unused") PNone arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @SuppressWarnings("unused") @Cached GetObjectSlotsNode getSlots,
                        @Bind("getSlots.execute(inliningTarget, iter)") TpSlots slots,
                        @Cached CallSlotTpIterNextNode callIterNext,
                        @Exclusive @Cached InlinedBranchProfile exhaustedNoException,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = callIterNext.execute(virtualFrame, inliningTarget, slots.tp_iternext(), iter);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (IteratorExhausted e) {
                exhaustedNoException.enter(inliningTarget);
                returnedValue.setObject(bytecode, virtualFrame, PNone.NONE);
                return true;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object obj,
                        Object arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = callMethodNode.execute(virtualFrame, inliningTarget, obj, T_SEND, arg);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, BytecodeNode bytecode,
                        IsBuiltinObjectProfile stopIterationProfile,
                        StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalAccessor returnedValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnedValue.setObject(bytecode, frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }

    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class YieldFromThrow {

        private static final TruffleString T_CLOSE = tsLiteral("close");
        private static final TruffleString T_THROW = tsLiteral("throw");

        @Specialization
        static boolean doGenerator(VirtualFrame frame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        PGenerator generator,
                        PException exception,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode,
                        @Cached CommonGeneratorBuiltins.CloseNode closeNode,
                        @Shared @Cached IsBuiltinObjectProfile profileExit,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            if (profileExit.profileException(inliningTarget, exception, GeneratorExit)) {
                closeNode.execute(frame, generator);
                throw exception;
            } else {
                try {
                    Object value = throwNode.execute(frame, generator, exception.getEscapedException(), PNone.NO_VALUE, PNone.NO_VALUE);
                    yieldedValue.setObject(bytecode, frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                    return true;
                }
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame frame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object obj,
                        Object exception,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached PyObjectLookupAttr lookupThrow,
                        @Cached PyObjectLookupAttr lookupClose,
                        @Cached CallNode callThrow,
                        @Cached CallNode callClose,
                        @Cached WriteUnraisableNode writeUnraisableNode,
                        @Shared @Cached IsBuiltinObjectProfile profileExit,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            PException pException = (PException) exception;
            if (profileExit.profileException(inliningTarget, pException, GeneratorExit)) {
                Object close = PNone.NO_VALUE;
                try {
                    close = lookupClose.execute(frame, inliningTarget, obj, T_CLOSE);
                } catch (PException e) {
                    writeUnraisableNode.execute(frame, e.getEscapedException(), null, obj);
                }
                if (close != PNone.NO_VALUE) {
                    callClose.execute(frame, close);
                }
                throw pException;
            } else {
                Object throwMethod = lookupThrow.execute(frame, inliningTarget, obj, T_THROW);
                if (throwMethod == PNone.NO_VALUE) {
                    throw pException;
                }
                try {
                    Object value = callThrow.execute(frame, throwMethod, pException.getEscapedException());
                    yieldedValue.setObject(bytecode, frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                    return true;
                }
            }
        }

        private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, BytecodeNode bytecode,
                        IsBuiltinObjectProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalAccessor returnedValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnedValue.setObject(bytecode, frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }
    }

    /**
     * Wraps the value {@link PAsyncGenWrappedValue}. CPython 3.11 opcode, used here to avoid a
     * runtime check.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class AsyncGenWrap {
        @Specialization
        static Object doIt(Object value,
                        @Bind PythonLanguage language) {
            return new PAsyncGenWrappedValue(language, value);
        }
    }

    /**
     * If given exception is {@link PythonBuiltinClassType#StopAsyncIteration} do nothing, otherwise
     * rethrow the exception. Used to implement the termination condition of {@code async for}.
     */
    @Operation(storeBytecodeIndex = true)
    @ImportStatic(PGuards.class)
    public static final class ExpectStopAsyncIteration {
        @Specialization
        static void doNone(PNone none) {
        }

        @Specialization
        static void doPException(PException exception,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecodeNode,
                        @Cached IsBuiltinObjectProfile isStopAsyncIteration) {
            if (!isStopAsyncIteration.profileException(inliningTarget, exception, PythonBuiltinClassType.StopAsyncIteration)) {
                throw exception.getExceptionForReraise(!((PBytecodeDSLRootNode) bytecodeNode.getRootNode()).internal);
            }
        }

        @Specialization(guards = "!isPException(exception)")
        static void doInteropException(AbstractTruffleException exception) {
            throw exception;
        }
    }

    /**
     * Loads a user-defined local variable. Unlike a built-in LoadLocal, this operation raises an
     * unbound local error if the local has not been set.
     * <p>
     * This operation makes use of Truffle's boxing overloads. When an operation tries to quicken
     * this one for boxing elimination, the correct overload will be selected.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = int.class)
    public static final class CheckAndLoadLocal {
        @Specialization(rewriteOn = UnexpectedResultException.class)
        public static int doInt(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile localUnboundProfile) throws UnexpectedResultException {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getInt(bytecodeNode, frame);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public static boolean doBoolean(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile localUnboundProfile) throws UnexpectedResultException {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getBoolean(bytecodeNode, frame);
        }

        @Specialization(replaces = {"doInt", "doBoolean"})
        public static Object doObject(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile localUnboundProfile) {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getObject(bytecodeNode, frame);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = int.class)
    public static final class DeleteLocal {
        @Specialization
        public static void doObject(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile localUnboundProfile) {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            accessor.clear(bytecodeNode, frame);
        }
    }

    @TruffleBoundary
    private static PException raiseUnbound(PBytecodeDSLRootNode rootNode, Node inliningTarget, int index) {
        TruffleString localName = rootNode.getCodeUnit().varnames[index];
        throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
    }

    @Operation(storeBytecodeIndex = true)
    public static final class RaiseNotImplementedError {
        @Specialization
        public static void doRaise(VirtualFrame frame, TruffleString name,
                        @Bind BytecodeNode node) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.NotImplementedError, name);

        }
    }

    /**
     * Creates a TypeVar, TypeVarTuple or ParamSpec object. The constant argument determines
     * (defined in {@link MakeTypeParamKind}) which and whether it will need to pop bound or
     * constraints for a TypeVar.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    public static final class MakeTypeParam {
        @Specialization
        public static Object doObject(int kind, TruffleString name, Object boundOrConstraint,
                        @Bind PBytecodeDSLRootNode rootNode) {
            Object evaluateBound = null;
            Object evaluateConstraints = null;

            if (kind == MakeTypeParamKind.TYPE_VAR_WITH_BOUND) {
                evaluateBound = boundOrConstraint;
            } else if (kind == MakeTypeParamKind.TYPE_VAR_WITH_CONSTRAINTS) {
                evaluateConstraints = boundOrConstraint;
            }

            PythonLanguage language = PythonLanguage.get(rootNode);
            return switch (kind) {
                case MakeTypeParamKind.TYPE_VAR, MakeTypeParamKind.TYPE_VAR_WITH_BOUND, MakeTypeParamKind.TYPE_VAR_WITH_CONSTRAINTS -> PFactory.createTypeVar(language, name,
                                evaluateBound == null ? PNone.NONE : null, evaluateBound,
                                evaluateConstraints == null ? PFactory.createEmptyTuple(language) : null, evaluateConstraints,
                                false, false, true);
                case MakeTypeParamKind.PARAM_SPEC -> PFactory.createParamSpec(language, name, PNone.NONE, false, false, true);
                case MakeTypeParamKind.TYPE_VAR_TUPLE -> PFactory.createTypeVarTuple(language, name);
                default -> throw shouldNotReachHere();
            };
        }
    }

    /**
     * Creates a TypeAliasType object. Arguments: name, type parameters (tuple or null), and the
     * value of the type alias.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class MakeTypeAliasType {
        @Specialization
        public static PTypeAliasType doObject(TruffleString name, Object typeParams, Object computeValue,
                        @Bind PBytecodeDSLRootNode rootNode) {
            PythonLanguage language = PythonLanguage.get(rootNode);
            // bytecode compiler should ensure that typeParams are either PTuple or null
            return PFactory.createTypeAliasType(language, name, (PTuple) typeParams, computeValue, null, null);
        }
    }

    /**
     * Creates a base for generic classes by calling typing._GenericAlias. Expects Python tuple as
     * an argument.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class MakeGeneric {
        @Specialization
        static Object makeGeneric(VirtualFrame frame, PTuple params,
                        @Bind Node inliningTarget,
                        @Cached UnpackTypeVarTuplesNode unpackTypeVarTuplesNode,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            params = unpackTypeVarTuplesNode.execute(frame, inliningTarget, params);
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T_GENERIC_ALIAS, PythonBuiltinClassType.PGeneric, params);
        }
    }

    /**
     * Returns false, if provided argument is PNone, else returns true.
     */
    @Operation
    public static final class IsNotNone {
        @Specialization
        static boolean makeNone(PNone none) {
            return false;
        }

        @Fallback
        static boolean makeOther(Object o) {
            return true;
        }
    }

    /**
     * Returns true, if the exception is reraise of the matched exception group or it was raised
     * excplicitly. Returns false for new exceptions.
     */
    @Operation
    public static final class IsExceptionGroup {
        @Specialization
        static boolean checkException(VirtualFrame frame, PException exception, PException exceptionOrig) {
            if (exception.getUnreifiedException() instanceof PBaseExceptionGroup exceptionGroup) {
                PException parent = exceptionGroup.getParent();
                if (parent == exceptionOrig) {
                    // this is an explicit raise of a matched exceptions group.
                    return true;
                } else if (exceptionGroup == exceptionOrig.getUnreifiedException()) {
                    // reraise
                    return true;
                }
            }
            // new exception; no rethrow or reraise
            return false;
        }

        @Specialization
        static boolean checkExceptionGroup(VirtualFrame frame, PBaseExceptionGroup exceptionGroup, PException exceptionOrig) {
            PException parent = exceptionGroup.getParent();
            if (parent == exceptionOrig) {
                // this is a rethrow of a matched exceptions group.
                return true;
            } else if (exceptionGroup == exceptionOrig.getUnreifiedException()) {
                // reraise
                return true;
            }
            return false;
        }

        @Fallback
        static boolean checkOther(VirtualFrame frame, Object object, Object excOrig) {
            return false;
        }
    }

    @Operation(storeBytecodeIndex = true)
    public static final class GetCaughtException {
        @Specialization
        static Object execute(VirtualFrame frame,
                        @Cached ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode) {
            Object exception = getCaughtExceptionNode.execute(frame);
            return exception;
        }
    }

    @ImportStatic(PGuards.class)
    @Operation(storeBytecodeIndex = true)
    @GenerateInline(false)
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class SplitExceptionGroups {
        @TruffleBoundary
        private static void raiseIfNoExceptionTuples(Node inliningTarget, Object clause, ValidExceptionNode isValidException, IsSubtypeNode isSubtypeNode,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            if (clause instanceof PTuple clauseTuple) {
                SequenceStorage storage = clauseTuple.getSequenceStorage();
                int length = storage.length();
                for (int i = 0; i < length; i++) {
                    Object clauseType = getItemNode.execute(inliningTarget, storage, i);
                    raiseIfNoException(inliningTarget, clauseType, isValidException, isSubtypeNode, getItemNode);
                }
            }
        }

        @TruffleBoundary
        private static void raiseIfNoException(Node inliningTarget, Object clause, ValidExceptionNode isValidException, IsSubtypeNode isSubtypeNode,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            if (clause instanceof PTuple) {
                raiseIfNoExceptionTuples(inliningTarget, clause, isValidException, isSubtypeNode, getItemNode);
            } else {
                if (!isValidException.execute(clause)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CATCHING_CLS_NOT_ALLOWED);
                } else if (isSubtypeNode.execute(clause, PythonBuiltinClassType.PBaseExceptionGroup)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.NO_EXCEPTION_GROUPS_IN_EXCEPT_STAR);
                }
            }
        }

        public static final TruffleString T_SPLIT = tsLiteral("split");

        @Specialization(guards = "isNone(e)")
        public static boolean matchObject(VirtualFrame frame,
                        LocalAccessor matchedGroup, LocalAccessor unmatchedGroup,
                        PNone e, Object clause, Object exceptionOrig,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode) {
            matchedGroup.setObject(bytecode, frame, PNone.NONE);
            unmatchedGroup.setObject(bytecode, frame, PNone.NONE);
            return false;
        }

        @Specialization(guards = "containsPBaseExceptionGroup(exceptionGroup)")
        public static boolean makePythonGroup(VirtualFrame frame,
                        LocalAccessor matchedGroup, LocalAccessor unmatchedGroup,
                        PException exceptionGroup, Object clause, PException exceptionOrig,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Shared @Cached ValidExceptionNode isValidException,
                        @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached BaseExceptionGroupBuiltins.BaseExceptionGroupNode exceptionGroupNode,
                        @Cached InlinedConditionProfile isExceptionGroup,
                        @Cached PyObjectCallMethodObjArgs callSplit,
                        @Cached InlinedConditionProfile pExceptionGroupProfileMatched,
                        @Cached InlinedConditionProfile pExceptionGroupProfileUnmatched) {
            raiseIfNoException(inliningTarget, clause, isValidException, isSubtypeNode, getItemNode);

            if (exceptionGroup.getUnreifiedException() instanceof PBaseExceptionGroup group) {
                PythonLanguage language = PythonLanguage.get(inliningTarget);
                PTuple rv = (PTuple) callSplit.execute(frame, inliningTarget, group, T_SPLIT, clause);

                Object matched = getItemNode.execute(inliningTarget, rv.getSequenceStorage(), 0);
                Object unmatched = getItemNode.execute(inliningTarget, rv.getSequenceStorage(), 1);

                matchedGroup.setObject(bytecode, frame, matched);
                unmatchedGroup.setObject(bytecode, frame, unmatched);

                boolean didSomethingMatch = false;
                if (pExceptionGroupProfileMatched.profile(inliningTarget, matched instanceof PBaseExceptionGroup)) {
                    didSomethingMatch = true;

                    PBaseExceptionGroup matchedCast = (PBaseExceptionGroup) matched;
                    if (group.getParent() != null) {
                        matchedCast.setParent(group.getParent());
                    } else {
                        matchedCast.setParent(exceptionGroup);
                    }
                    matchedCast.setContextExplicitly(group.getContext());
                    PException matchedException = PException.fromExceptionInfo(matched, PythonOptions.isPExceptionWithJavaStacktrace(language));
                    matchedGroup.setObject(bytecode, frame, matchedException);
                } else {
                    matchedGroup.setObject(bytecode, frame, PNone.NONE);
                }

                if (pExceptionGroupProfileUnmatched.profile(inliningTarget, unmatched instanceof PBaseExceptionGroup)) {
                    PBaseExceptionGroup unmatchedCast = (PBaseExceptionGroup) unmatched;
                    if (group.getParent() != null) {
                        unmatchedCast.setParent(group.getParent());
                    } else {
                        unmatchedCast.setParent(exceptionGroup);
                    }
                    unmatchedCast.setContextExplicitly(group.getContext());
                    PException unmatchedException = PException.fromExceptionInfo(unmatched, PythonOptions.isPExceptionWithJavaStacktrace(language));
                    unmatchedGroup.setObject(bytecode, frame, unmatchedException);
                } else {
                    unmatchedGroup.setObject(bytecode, frame, PNone.NONE);
                }

                return didSomethingMatch;
            }

            return false;
        }

        @Specialization(guards = "!containsPBaseExceptionGroup(exception)")
        public static boolean makePythonSingle(VirtualFrame frame,
                        LocalAccessor matchedGroup, LocalAccessor unmatchedGroup,
                        PException exception, Object clause, PException exceptionOrig,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Shared @Cached ValidExceptionNode isValidException,
                        @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ExceptMatchNode exceptMatchNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached BaseExceptionGroupBuiltins.BaseExceptionGroupNode exceptionGroupNode,
                        @Cached PyObjectGetAttr getAttr) {
            raiseIfNoException(inliningTarget, clause, isValidException, isSubtypeNode, getItemNode);
            if (exceptMatchNode.executeMatch(exception, clause)) {
                PythonLanguage language = PythonLanguage.get(inliningTarget);
                PBaseExceptionGroup group = exceptionGroupNode.execute(frame,
                                getAttr.execute(inliningTarget, PythonContext.get(inliningTarget).getBuiltins(), T_EXCEPTION_GROUP),
                                StringLiterals.T_EMPTY_STRING,
                                PFactory.createList(PythonLanguage.get(inliningTarget), new Object[]{exception.getUnreifiedException()}));
                group.setParent(exceptionOrig);
                matchedGroup.setObject(bytecode, frame, PException.fromExceptionInfo(group, PythonOptions.isPExceptionWithJavaStacktrace(language)));
                unmatchedGroup.setObject(bytecode, frame, PNone.NONE);
                return true;
            }
            return false;
        }
    }
}
