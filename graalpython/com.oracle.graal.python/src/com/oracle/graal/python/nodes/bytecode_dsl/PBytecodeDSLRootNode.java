package com.oracle.graal.python.nodes.bytecode_dsl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AEXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EXIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNode;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ChainExceptionsNode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListExtendNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ConcatDictToStorageNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.bytecode.GetSendValueNode;
import com.oracle.graal.python.nodes.bytecode.GetTPFlagsNode;
import com.oracle.graal.python.nodes.bytecode.GetYieldFromIterNode;
import com.oracle.graal.python.nodes.bytecode.ImportFromNode;
import com.oracle.graal.python.nodes.bytecode.ImportNode;
import com.oracle.graal.python.nodes.bytecode.ImportStarNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode.PrintExprNode;
import com.oracle.graal.python.nodes.bytecode.RaiseNode;
import com.oracle.graal.python.nodes.bytecode.SetupAnnotationsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.exception.ExceptMatchNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.AddNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.DivModNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.FloorDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.LShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MatMulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.ModNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.PowNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.RShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.SubNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.TrueDivNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode.NotNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode.YesNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.InvertNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.NegNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.PosNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.ReadFromLocalsNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalSetter;
import com.oracle.truffle.api.bytecode.LocalSetterRange;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@GenerateBytecode(//
                languageClass = PythonLanguage.class, //
                enableYield = true, //
                enableSerialization = true, //
                storeBciInFrame = true //
)
@TypeSystemReference(PythonTypes.class)
@OperationProxy(AddNode.class)
@OperationProxy(SubNode.class)
@OperationProxy(MulNode.class)
@OperationProxy(TrueDivNode.class)
@OperationProxy(FloorDivNode.class)
@OperationProxy(ModNode.class)
@OperationProxy(LShiftNode.class)
@OperationProxy(RShiftNode.class)
@OperationProxy(BitAndNode.class)
@OperationProxy(BitOrNode.class)
@OperationProxy(BitXorNode.class)
@OperationProxy(MatMulNode.class)
@OperationProxy(PowNode.class)
@OperationProxy(DivModNode.class)
@OperationProxy(PosNode.class)
@OperationProxy(NegNode.class)
@OperationProxy(InvertNode.class)
@OperationProxy(IsNode.class)
@OperationProxy(ContainsNode.class)
@OperationProxy(FormatNode.class)
@OperationProxy(ExceptMatchNode.class)
@OperationProxy(GetYieldFromIterNode.class)
@OperationProxy(GetAwaitableNode.class)
@OperationProxy(SetupAnnotationsNode.class)
@OperationProxy(value = ListNodes.AppendNode.class, name = "ListAppend")
@OperationProxy(value = SetNodes.AddNode.class, name = "SetAdd")
@ShortCircuitOperation(name = "BoolAnd", booleanConverter = PBytecodeDSLRootNode.Yes.class, operator = Operator.AND_RETURN_VALUE)
@ShortCircuitOperation(name = "BoolOr", booleanConverter = PBytecodeDSLRootNode.Yes.class, operator = Operator.OR_RETURN_VALUE)
@ShortCircuitOperation(name = "PrimitiveBoolAnd", booleanConverter = PBytecodeDSLRootNode.BooleanIdentity.class, operator = Operator.AND_RETURN_CONVERTED)
@ShortCircuitOperation(name = "PrimitiveBoolOr", booleanConverter = PBytecodeDSLRootNode.BooleanIdentity.class, operator = Operator.OR_RETURN_CONVERTED)
@SuppressWarnings("unused")
public abstract class PBytecodeDSLRootNode extends PRootNode implements BytecodeRootNode {

    @Child protected transient PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private transient CalleeContext calleeContext = CalleeContext.create();
    @Child private transient ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private transient ChainExceptionsNode chainExceptionsNode;

    // These fields are effectively final, but can only be set after construction.
    @CompilationFinal protected transient BytecodeDSLCodeUnit co;
    @CompilationFinal protected transient Signature signature;
    @CompilationFinal protected transient int selfIndex;
    @CompilationFinal protected transient int classcellIndex;

    private transient boolean pythonInternal;
    @CompilationFinal private transient boolean internal;

    @SuppressWarnings("this-escape")
    protected PBytecodeDSLRootNode(TruffleLanguage<?> language, FrameDescriptor.Builder frameDescriptorBuilder) {
        super(language, frameDescriptorBuilder.info(new BytecodeDSLFrameInfo()).build());
        ((BytecodeDSLFrameInfo) getFrameDescriptor().getInfo()).setRootNode(this);
    }

    public PythonObjectFactory getFactory() {
        return factory;
    }

    public void setMetadata(BytecodeDSLCodeUnit co) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.co = co;
        this.signature = co.computeSignature();
        this.classcellIndex = co.getClassCellIndex();
        this.selfIndex = co.getSelfIndex();
        this.internal = getSource().isInternal();
    }

    @Override
    public boolean isInternal() {
        return internal;
    }

    @Override
    public boolean isPythonInternal() {
        return pythonInternal;
    }

    public void setPythonInternal(boolean pythonInternal) {
        this.pythonInternal = pythonInternal;
    }

    @Override
    public String toString() {
        return "<function op " + co.name + ">";
    }

    @Override
    public void executeProlog(VirtualFrame frame) {
        calleeContext.enter(frame);
    }

    public MaterializedFrame createGeneratorFrame(Object[] arguments) {
        Object[] generatorFrameArguments = PArguments.create();
        MaterializedFrame generatorFrame = Truffle.getRuntime().createMaterializedFrame(generatorFrameArguments, getFrameDescriptor());
        PArguments.setGeneratorFrame(arguments, generatorFrame);
        PArguments.setCurrentFrameInfo(generatorFrameArguments, new PFrame.Reference(null));
        // The invoking node will set these two to the correct value only when the callee requests
        // it, otherwise they stay at the initial value, which we must set to null here
        PArguments.setException(arguments, null);
        PArguments.setCallerFrameInfo(arguments, null);
        return generatorFrame;
    }

    @Override
    public void executeEpilog(VirtualFrame frame, Object returnValue, Throwable throwable) {
        if (throwable != null && throwable instanceof PException pe) {
            pe.notifyAddedTracebackFrame(!isPythonInternal());
        }
        calleeContext.exit(frame, this);
    }

    @Override
    public Throwable interceptInternalException(Throwable throwable, BytecodeNode bytecodeNode, int bci) {
        if (throwable instanceof StackOverflowError soe) {
            PythonContext.get(this).reacquireGilAfterStackOverflow();
            return ExceptionUtils.wrapJavaException(soe, this, factory.createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{}));
        }
        return throwable;
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        if (ex instanceof PException pe) {
            pe.setCatchLocation(bci, bytecodeNode);

            // Fill in the __context__, if available.
            if (getCaughtExceptionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
            }
            PException context = getCaughtExceptionNode.execute(frame);
            if (context != null) {
                if (chainExceptionsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    chainExceptionsNode = insert(ChainExceptionsNode.create());
                }
                chainExceptionsNode.execute(pe, context);
            }
        }

        return ex;
    }

    @Override
    public boolean setsUpCalleeContext() {
        return true;
    }

    @Override
    public String getName() {
        return co.name.toJavaStringUncached();
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

    @TruffleBoundary
    public int bciToLine(int bci, BytecodeNode bytecodeNode) {
        return getSourceSectionForLocation(bci, bytecodeNode).getStartLine();
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(BytecodeLocation location) {
        return getSourceSectionForLocation(location.getBytecodeIndex(), location.getBytecodeNode());
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(int bci, BytecodeNode bytecodeNode) {
        SourceSection sourceSection = null;
        if (bytecodeNode != null) {
            BytecodeLocation bytecodeLocation = bytecodeNode.getBytecodeLocation(bci);
            if (bytecodeLocation != null) {
                sourceSection = bytecodeLocation.findSourceLocation();
            }
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

    @Override
    protected byte[] extractCode() {
        return MarshalModuleBuiltins.serializeCodeUnit(co);
    }

    private static Object checkUnboundCell(PCell cell, int index, PBytecodeDSLRootNode rootNode, Node inliningTarget, PRaiseNode.Lazy raiseNode) {
        Object result = cell.getRef();
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CodeUnit codeUnit = rootNode.getCodeUnit();
            if (index < codeUnit.cellvars.length) {
                TruffleString localName = codeUnit.cellvars[index];
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
            } else {
                TruffleString localName = codeUnit.freevars[index - codeUnit.cellvars.length];
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, localName);
            }
        }
        return result;
    }

    public PCell readClassCell(Frame frame) {
        if (classcellIndex < 0) {
            return null;
        }
        return (PCell) getLocal(frame, classcellIndex);
    }

    public Object readSelf(Frame frame) {
        if (selfIndex < 0) {
            return null;
        } else if (selfIndex == 0) {
            return getLocal(frame, 0);
        } else {
            PCell selfCell = (PCell) getLocal(frame, selfIndex);
            return selfCell.getRef();
        }
    }

    @Operation
    public static final class BooleanIdentity {
        @Specialization
        public static boolean doBoolean(boolean b) {
            return b;
        }
    }

    @Operation
    public static final class ArrayIndex {
        @Specialization
        public static Object doObject(Object[] array, int i) {
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

    @Operation
    public static final class Yes {
        @Specialization
        public static boolean perform(VirtualFrame frame, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached YesNode yesNode) {
            return yesNode.executeBoolean(frame, inliningTarget, o);
        }
    }

    @Operation
    public static final class Not {
        @Specialization
        public static boolean perform(VirtualFrame frame, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached NotNode notNode) {
            return notNode.executeBoolean(frame, inliningTarget, o);
        }
    }

    @Operation
    public static final class MakeVariadic {
        @Specialization
        public static Object[] perform(@Variadic Object[] values) {
            return values;
        }
    }

    @Operation
    public static final class GetVariableArguments {
        @Specialization
        public static Object[] perform(VirtualFrame frame) {
            return PArguments.getVariableArguments(frame);
        }
    }

    @Operation
    public static final class WriteName {
        @Specialization
        public static void perform(VirtualFrame frame, Object value, TruffleString name,
                        @Cached WriteNameNode writeNode) {
            writeNode.execute(frame, name, value);
        }
    }

    @Operation
    public static final class ReadName {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadNameNode readNode) {
            Object result = readNode.execute(frame, name);
            return result;
        }
    }

    @Operation
    public static final class DeleteName {
        @Specialization(guards = "hasLocals(frame)")
        public static void performLocals(VirtualFrame frame, TruffleString name,
                        @Bind("this") Node inliningTarget,
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

    @Operation
    public static final class LoadArgumentOptional {
        @Specialization
        public static Object perform(VirtualFrame frame, int argumentIndex) {
            Object[] arguments = frame.getArguments();
            if (arguments.length > argumentIndex) {
                return arguments[argumentIndex];
            } else {
                return null;
            }
        }
    }

    @Operation
    public static final class LoadVariableArguments {
        @Specialization
        public static Object perform(VirtualFrame frame,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createTuple(PArguments.getVariableArguments(frame));
        }
    }

    @Operation
    public static final class LoadKeywordArguments {
        @Specialization
        public static Object perform(VirtualFrame frame,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createDict(PArguments.getKeywordArguments(frame));
        }
    }

    @Operation
    public static final class LoadComplex {
        @Specialization
        public static Object perform(double[] complex,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createComplex(complex[0], complex[1]);
        }
    }

    @Operation
    public static final class LoadBigInt {
        @Specialization
        public static Object perform(BigInteger bigInt,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createInt(bigInt);
        }
    }

    @Operation
    public static final class LoadBytes {
        @Specialization
        public static Object perform(byte[] bytes,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createBytes(bytes);
        }
    }

    @Operation
    public static final class GetIter {
        @Specialization
        public static Object perform(VirtualFrame frame, Object receiver,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIterNode) {
            return getIterNode.execute(frame, inliningTarget, receiver);
        }
    }

    @Operation
    public static final class GetItem {
        @Specialization
        public static Object perform(VirtualFrame frame, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetItem getItemNode) {
            return getItemNode.execute(frame, inliningTarget, key, value);
        }
    }

    @Operation
    public static final class FormatStr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStrAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class FormatRepr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class FormatAscii {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectAsciiNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class PrintExpr {
        @Specialization
        public static void perform(VirtualFrame frame, Object object,
                        @Cached PrintExprNode printExprNode) {
            printExprNode.execute(frame, object);
        }
    }

    @Operation
    public static final class MakeFunction {
        @Specialization(guards = {"isSingleContext(rootNode)", "!codeUnit.isGeneratorOrCoroutine()"})
        public static Object functionSingleContext(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults, Object[] kwDefaultsObject,
                        Object closure, Object annotations, Object doc,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached("createCode(rootNode, codeUnit, functionRootNode)") PCode cachedCode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            return createFunction(frame, name, qualifiedName, cachedCode, defaults, kwDefaultsObject, closure, annotations, doc, rootNode, dylib);
        }

        @Specialization(replaces = "functionSingleContext", guards = "!codeUnit.isGeneratorOrCoroutine()")
        public static Object functionMultiContext(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults, Object[] kwDefaultsObject,
                        Object closure, Object annotations, Object doc,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            PCode code = createCode(rootNode, codeUnit, functionRootNode);
            return createFunction(frame, name, qualifiedName, code, defaults, kwDefaultsObject, closure, annotations, doc, rootNode, dylib);
        }

        @Specialization(guards = {"isSingleContext(rootNode)", "codeUnit.isGeneratorOrCoroutine()"})
        public static Object generatorOrCoroutineSingleContext(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults, Object[] kwDefaultsObject,
                        Object closure, Object annotations, Object doc,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached(value = "createGeneratorRootNode(rootNode, functionRootNode, codeUnit)", adopt = false) PBytecodeDSLGeneratorFunctionRootNode generatorRootNode,
                        @Cached("createCode(rootNode, codeUnit, generatorRootNode)") PCode cachedCode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            return createFunction(frame, name, qualifiedName, cachedCode, defaults, kwDefaultsObject, closure, annotations, doc, rootNode, dylib);
        }

        @Specialization(replaces = "generatorOrCoroutineSingleContext", guards = "codeUnit.isGeneratorOrCoroutine()")
        public static Object generatorOrCoroutineMultiContext(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults, Object[] kwDefaultsObject,
                        Object closure, Object annotations, Object doc,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached(value = "createGeneratorRootNode(rootNode, functionRootNode, codeUnit)", adopt = false) PBytecodeDSLGeneratorFunctionRootNode generatorRootNode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            PCode code = createCode(rootNode, codeUnit, generatorRootNode);
            return createFunction(frame, name, qualifiedName, code, defaults, kwDefaultsObject, closure, annotations, doc, rootNode, dylib);
        }

        @Idempotent
        protected static boolean isSingleContext(Node node) {
            return PythonLanguage.get(node).isSingleContext();
        }

        @NeverDefault
        protected static PBytecodeDSLRootNode createFunctionRootNode(PBytecodeDSLRootNode outerRootNode, BytecodeDSLCodeUnit codeUnit) {
            return codeUnit.createRootNode(PythonContext.get(outerRootNode), outerRootNode.getSource());
        }

        @NeverDefault
        protected static PBytecodeDSLGeneratorFunctionRootNode createGeneratorRootNode(PBytecodeDSLRootNode outerRootNode, PBytecodeDSLRootNode functionRootNode,
                        BytecodeDSLCodeUnit codeUnit) {
            return new PBytecodeDSLGeneratorFunctionRootNode(PythonLanguage.get(outerRootNode), functionRootNode.getFrameDescriptor(), functionRootNode, codeUnit.name);
        }

        @NeverDefault
        protected static PCode createCode(PBytecodeDSLRootNode outerRootNode, BytecodeDSLCodeUnit codeUnit, PRootNode rootNode) {
            return outerRootNode.getFactory().createCode(
                            rootNode.getCallTarget(),
                            rootNode.getSignature(),
                            codeUnit);
        }

        protected static PFunction createFunction(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName,
                        PCode code,
                        Object[] defaults, Object[] kwDefaultsObject,
                        Object closure, Object annotations, Object doc,
                        PBytecodeDSLRootNode node,
                        DynamicObjectLibrary dylib) {
            PKeyword[] kwDefaults = new PKeyword[kwDefaultsObject.length];
            System.arraycopy(kwDefaultsObject, 0, kwDefaults, 0, kwDefaults.length);
            PFunction function = node.factory.createFunction(name, qualifiedName, code, PArguments.getGlobals(frame), defaults, kwDefaults, (PCell[]) closure);

            if (annotations != null) {
                dylib.put(function, T___ANNOTATIONS__, annotations);
            }
            if (doc != null) {
                dylib.put(function, T___DOC__, doc);
            }

            return function;
        }
    }

    @Operation
    public static final class Raise {
        @Specialization
        public static void perform(VirtualFrame frame, Object typeOrExceptionObject, Object cause,
                        @Cached RaiseNode raiseNode) {
            raiseNode.execute(frame, typeOrExceptionObject, cause, true);
        }
    }

    @Operation
    public static final class IAdd {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x + (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left + right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IAdd);
        }
    }

    @Operation
    public static final class IAnd {

        @Specialization
        public static int add(int left, int right) {
            return left & right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left & right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IAnd);
        }
    }

    @Operation
    public static final class IOr {

        @Specialization
        public static int add(int left, int right) {
            return left | right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left | right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IOr);
        }
    }

    @Operation
    public static final class IXor {

        @Specialization
        public static int add(int left, int right) {
            return left ^ right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left ^ right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IXor);
        }
    }

    @Operation
    public static final class IRShift {

        @Specialization(guards = {"right < 32", "right >= 0"})
        public static int doIISmall(int left, int right) {
            return left >> right;
        }

        @Specialization(guards = {"right < 64", "right >= 0"})
        public static long doIISmall(long left, long right) {
            return left >> right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IRShift);
        }
    }

    @Operation
    public static final class ILShift {

        @Specialization(guards = {"right < 32", "right >= 0"}, rewriteOn = OverflowException.class)
        public static int doII(int left, int right) throws OverflowException {
            int result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(guards = {"right < 64", "right >= 0"}, rewriteOn = OverflowException.class)
        public static long doLL(long left, long right) throws OverflowException {
            long result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ILShift);
        }
    }

    @Operation
    public static final class IMatMul {

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMatMul);
        }
    }

    @Operation
    public static final class ISub {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int doII(int left, int right) {
            return Math.subtractExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.subtractExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left - right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ISub);
        }
    }

    @Operation
    public static final class IMult {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.multiplyExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.multiplyExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left * right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMul);
        }
    }

    @Operation
    public static final class ITrueDiv {
        @Specialization
        public static double doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ITrueDiv);
        }
    }

    @Operation
    public static final class IFloorDiv {
        @Specialization
        public static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            return FloorDivNode.doLL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IFloorDiv);
        }
    }

    @Operation
    public static final class IPow {
        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IPow);
        }
    }

    @Operation
    public static final class IMod {
        @Specialization
        public static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            return ModNode.doLL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMod);
        }
    }

    @Operation
    public static final class ForIterate {

        @Specialization
        public static boolean doIntegerSequence(VirtualFrame frame, PIntegerSequenceIterator iterator,
                        LocalSetter output) {
            return doInteger(frame, iterator, output);
        }

        @Specialization
        public static boolean doIntRange(VirtualFrame frame, PIntRangeIterator iterator,
                        LocalSetter output) {
            return doInteger(frame, iterator, output);
        }

        private static boolean doInteger(VirtualFrame frame, PIntegerIterator iterator,
                        LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setInt(frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doObjectIterator(VirtualFrame frame, PObjectSequenceIterator iterator,
                        LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                output.setObject(frame, null);
                return false;
            }
            Object value = iterator.next();
            output.setObject(frame, value);
            return value != null;
        }

        @Specialization
        public static boolean doLongIterator(VirtualFrame frame, PLongSequenceIterator iterator,
                        LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setLong(frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doDoubleIterator(VirtualFrame frame, PDoubleSequenceIterator iterator,
                        LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setDouble(frame, iterator.next());
            return true;
        }

        @Specialization
        @InliningCutoff
        public static boolean doIterator(VirtualFrame frame, Object object,
                        LocalSetter output,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                Object value = next.execute(frame, object);
                output.setObject(frame, value);
                return value != null;
            } catch (PException e) {
                output.setObject(frame, null);
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            }
        }
    }

    @Operation
    public static final class GetMethod {
        @Specialization
        public static Object doIt(VirtualFrame frame,
                        Object obj, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetMethod getMethod) {
            return getMethod.execute(frame, inliningTarget, obj, name);
        }
    }

    @Operation
    public static final class GetAttribute {
        @Specialization
        @InliningCutoff
        public static Object doIt(VirtualFrame frame,
                        Object obj, TruffleString name,
                        @Cached("name") TruffleString cachedName,
                        @Cached("create(cachedName)") GetFixedAttributeNode getAttributeNode) {
            // TODO (GR-52217): make name a DSL constant.
            assert name == cachedName;
            return getAttributeNode.execute(frame, obj);
        }
    }

    @Operation
    public static final class SetAttribute {
        @Specialization
        @InliningCutoff
        public static void doIt(VirtualFrame frame, Object value, Object object, Object key,
                        @Cached LookupAndCallTernaryNode call) {
            call.execute(frame, object, key, value);
        }

        @NeverDefault
        public static LookupAndCallTernaryNode create() {
            return LookupAndCallTernaryNode.create(SpecialMethodSlot.SetAttr);
        }
    }

    @Operation
    public static final class DeleteAttribute {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame, Object object, Object key,
                        @Cached LookupAndCallBinaryNode call) {
            return call.executeObject(frame, object, key);
        }

        @NeverDefault
        public static LookupAndCallBinaryNode create() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.DelAttr);
        }
    }

    @Operation
    @ImportStatic(SpecialMethodSlot.class)
    public static final class DeleteItem {
        @Specialization
        public static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(DelItem)") LookupSpecialMethodSlotNode lookupDelitem,
                        @Cached PRaiseNode raise,
                        @Cached CallBinaryMethodNode callDelitem) {
            Object delitem = lookupDelitem.execute(frame, getClassNode.execute(inliningTarget, primary), primary);
            if (delitem == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
            }
            callDelitem.executeObject(frame, delitem, primary, index);
        }
    }

    @Operation
    public static final class ReadGlobal {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame, name);
        }
    }

    @Operation
    public static final class WriteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, Object value, TruffleString name,
                        @Cached WriteGlobalNode writeNode) {
            writeNode.executeObject(frame, name, value);
        }
    }

    @Operation
    public static final class DeleteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name,
                        @Cached DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame), name);
        }
    }

    @Operation
    public static final class BuildClass {

        public static final TruffleString NAME = BuiltinNames.T___BUILD_CLASS__;

        @Specialization
        @InliningCutoff
        public static Object perform(VirtualFrame frame,
                        @Cached ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame, NAME);
        }
    }

    @Operation
    public static final class MakeList {
        @Specialization
        public static PList perform(Object[] elements,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createList(elements);
        }
    }

    @Operation
    public static final class MakeSet {
        @Specialization
        @ExplodeLoop
        public static PSet perform(VirtualFrame frame, Object[] elements,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node node,
                        @Cached(value = "elements.length", neverDefault = true) int length,
                        @Cached SetNodes.AddNode addNode,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            // TODO (GR-52217): make length a DSL constant.
            assert elements.length == length;

            PSet set = rootNode.factory.createSet();
            for (int i = 0; i < length; i++) {
                SetNodes.AddNode.add(frame, set, elements[i], addNode, setItemNode);
            }

            return set;
        }
    }

    @Operation
    public static final class MakeEmptySet {
        @Specialization
        public static PSet perform(VirtualFrame frame,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createSet();
        }
    }

    @Operation
    public static final class MakeFrozenSet {
        @Specialization
        @ExplodeLoop
        public static PFrozenSet doFrozenSet(VirtualFrame frame, @Variadic Object[] elements,
                        @Cached(value = "elements.length", neverDefault = false) int length,
                        @Cached HashingStorageSetItem hashingStorageLibrary,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget) {
            // TODO (GR-52217): make length a DSL constant.
            assert elements.length == length;

            HashingStorage setStorage = EmptyStorage.INSTANCE;
            for (int i = 0; i < length; ++i) {
                Object o = elements[i];
                setStorage = hashingStorageLibrary.execute(frame, inliningTarget, setStorage, o, PNone.NONE);
            }
            return rootNode.factory.createFrozenSet(setStorage);
        }
    }

    @Operation
    public static final class MakeEmptyList {
        @Specialization
        public static PList perform(@Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createList();
        }
    }

    @Operation
    public static final class MakeTuple {
        @Specialization
        @ExplodeLoop
        public static Object perform(@Variadic Object[] elements,
                        @Cached(value = "elements.length", neverDefault = false) int length,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            // TODO (GR-52217): make length a DSL constant.
            assert elements.length == length;

            int totalLength = 0;
            for (int i = 0; i < length; i++) {
                totalLength += ((Object[]) elements[i]).length;
            }

            Object[] elts = new Object[totalLength];
            int idx = 0;
            for (int i = 0; i < length; i++) {
                Object[] arr = (Object[]) elements[i];
                int len = arr.length;
                System.arraycopy(arr, 0, elts, idx, len);
                idx += len;
            }

            return rootNode.factory.createTuple(elts);
        }
    }

    @Operation
    public static final class MakeConstantIntList {
        @Specialization
        public static PList perform(int[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    public static final class MakeConstantLongList {
        @Specialization
        public static PList perform(long[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    public static final class MakeConstantBooleanList {
        @Specialization
        public static PList perform(boolean[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    public static final class MakeConstantDoubleList {
        @Specialization
        public static PList perform(double[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    public static final class MakeConstantObjectList {
        @Specialization
        public static PList perform(Object[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    public static final class MakeConstantIntTuple {
        @Specialization
        public static PTuple perform(int[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeConstantLongTuple {
        @Specialization
        public static PTuple perform(long[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeConstantBooleanTuple {
        @Specialization
        public static PTuple perform(boolean[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeConstantDoubleTuple {
        @Specialization
        public static PTuple perform(double[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeConstantObjectTuple {
        @Specialization
        public static PTuple perform(Object[] array,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeSlice {

        @Specialization
        public static Object doIII(int start, int end, int step,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(start, end, step);
        }

        @Specialization
        public static Object doNIN(PNone start, int end, PNone step,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(0, end, 1, true, true);
        }

        @Specialization
        public static Object doIIN(int start, int end, PNone step,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(start, end, 1, false, true);
        }

        @Specialization
        public static Object doNII(PNone start, int end, int step,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(0, end, step, true, false);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(Object start, Object end, Object step,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createObjectSlice(start, end, step);
        }
    }

    @Operation
    public static final class MakeKeywords {
        @Specialization
        @ExplodeLoop
        public static PKeyword[] perform(@Variadic Object[] keysAndValues,
                        @Cached(value = "keysAndValues.length", neverDefault = true) int length) {
            // TODO (GR-52217): make length a DSL constant.
            assert keysAndValues.length == length;
            assert length % 2 == 0;

            PKeyword[] result = new PKeyword[length / 2];
            for (int i = 0; i < length; i += 2) {
                CompilerAsserts.compilationConstant(keysAndValues[i]);
                result[i / 2] = new PKeyword((TruffleString) keysAndValues[i], keysAndValues[i + 1]);
            }
            return result;
        }
    }

    @Operation
    public static final class MappingToKeywords {
        @Specialization
        public static PKeyword[] perform(Object sourceCollection,
                        @Bind("this") Node inliningTarget,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached PRaiseNode raise) {
            return expandKeywordStarargsNode.execute(inliningTarget, sourceCollection);
        }
    }

    @Operation
    public static final class MakeDict {
        @Specialization
        @ExplodeLoop
        public static PDict perform(VirtualFrame frame, @Variadic Object[] keysAndValues,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached(value = "keysAndValues.length", neverDefault = true) int length,
                        @Cached DictNodes.UpdateNode updateNode) {
            // TODO (GR-52217): make length a DSL constant.
            assert keysAndValues.length == length;

            PDict dict = rootNode.factory.createDict();
            for (int i = 0; i < length; i += 2) {
                Object key = keysAndValues[i];
                Object value = keysAndValues[i + 1];
                if (key == PNone.NO_VALUE) {
                    updateNode.execute(frame, dict, value);
                } else {
                    dict.setItem(key, value);
                }
            }

            return dict;
        }
    }

    @Operation
    public static final class MakeEmptyDict {
        @Specialization
        public static PDict perform(@Bind("$root") PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createDict();
        }
    }

    @Operation
    public static final class SetDictItem {
        @Specialization
        public static void perform(VirtualFrame frame, PDict item, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setItem) {
            item.setDictStorage(setItem.execute(frame, inliningTarget, item.getDictStorage(), key, value));
        }
    }

    public static final class LiteralBoolean {
        @Specialization
        public static boolean doBoolean(boolean value) {
            return value;
        }
    }

    @Operation
    public static final class SetItem {
        @Specialization
        public static void performB(VirtualFrame frame, boolean value, Object primary, Object slice,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performI(VirtualFrame frame, int value, Object primary, Object slice,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performL(VirtualFrame frame, long value, Object primary, Object slice,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performD(VirtualFrame frame, double value, Object primary, Object slice,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performO(VirtualFrame frame, Object value, Object primary, Object slice,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackToLocals {
        @Specialization(guards = {"cannotBeOverridden(sequence, inliningTarget, getClassNode)", "!isPString(sequence)"}, limit = "1")
        @ExplodeLoop
        public static void doUnpackSequence(VirtualFrame localFrame, PSequence sequence,
                        LocalSetterRange results,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();

            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            if (len == count) {
                for (int i = 0; i < count; i++) {
                    results.setObject(localFrame, i, getItemNode.execute(inliningTarget, storage, i));
                }
            } else {
                errorProfile.enter(inliningTarget);
                if (len < count) {
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
                } else {
                    throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
                }
            }
        }

        @Specialization
        @ExplodeLoop
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame virtualFrame, Object collection,
                        LocalSetterRange results,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Cached IsBuiltinObjectProfile stopIterationProfile2,
                        @Shared @Cached PRaiseNode raiseNode) {
            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            for (int i = 0; i < count; i++) {
                try {
                    results.setObject(virtualFrame, i, getNextNode.execute(virtualFrame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                getNextNode.execute(virtualFrame, iterator);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopIterationProfile2);
                return;
            }
            throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class UnpackStarredToLocals {
        @Specialization(guards = {"cannotBeOverridden(sequence, inliningTarget, getClassNode)", "!isPString(sequence)"}, limit = "1")
        public static void doUnpackSequence(VirtualFrame localFrame, PSequence sequence, int starIndex,
                        LocalSetterRange results,
                        @Shared @Cached(value = "starIndex", neverDefault = false) int cachedStarIndex,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            // TODO (GR-52217): make starIndex a DSL constant.
            assert starIndex == cachedStarIndex;

            int resultsLength = results.getLength();
            int countBefore = cachedStarIndex;
            int countAfter = resultsLength - cachedStarIndex - 1;

            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();

            int starLen = len - resultsLength + 1;
            if (starLen < 0) {
                errorProfile.enter(inliningTarget);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            moveItemsToStack(storage, localFrame, inliningTarget, results, 0, 0, countBefore, getItemNode);
            PList starList = rootNode.factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            results.setObject(localFrame, cachedStarIndex, starList);
            moveItemsToStack(storage, localFrame, inliningTarget, results, cachedStarIndex + 1, len - countAfter, countAfter, getItemNode);
        }

        @Specialization
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame frame, Object collection, int starIndex,
                        LocalSetterRange results,
                        @Shared @Cached(value = "starIndex", neverDefault = false) int cachedStarIndex,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            // TODO (GR-52217): make starIndex a DSL constant.
            assert starIndex == cachedStarIndex;

            int resultsLength = results.getLength();
            int countBefore = cachedStarIndex;
            int countAfter = resultsLength - cachedStarIndex - 1;

            Object iterator;
            try {
                iterator = getIter.execute(frame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            moveItemsToStack(frame, inliningTarget, iterator, results, 0, 0, countBefore, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);

            PList starAndAfter = constructListNode.execute(frame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                results.setObject(frame, cachedStarIndex, starAndAfter);
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = rootNode.factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                results.setObject(frame, cachedStarIndex, starList);

                moveItemsToStack(storage, frame, inliningTarget, results, cachedStarIndex + 1, starLen, countAfter, getItemNode);
            }
        }

        @ExplodeLoop
        private static void moveItemsToStack(VirtualFrame frame, Node self, Object iterator, LocalSetterRange results, int resultsOffset, int offset, int length, int totalLength,
                        GetNextNode getNextNode, IsBuiltinObjectProfile stopIterationProfile, PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                try {
                    Object item = getNextNode.execute(frame, iterator);
                    results.setObject(frame, resultsOffset + i, item);
                } catch (PException e) {
                    e.expectStopIteration(self, stopIterationProfile);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, offset + i);
                }
            }
        }

        @ExplodeLoop
        private static void moveItemsToStack(SequenceStorage storage, VirtualFrame localFrame, Node inliningTarget, LocalSetterRange run, int runOffset, int offset, int length,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                run.setObject(localFrame, runOffset + i, getItemNode.execute(inliningTarget, storage, offset + i));
            }
        }
    }

    private static final RuntimeException notSupported(Object left, Object right, PRaiseNode raiseNode, TruffleString operator) {
        throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, operator, left, right);
    }

    @Operation
    public static final class Le {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral("<=");

        @Specialization
        public static boolean cmp(int l, int r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(l, r, compareIntsUTF32Node) <= 0;
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l <= r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l <= r;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Le, SpecialMethodSlot.Ge, true, true);
        }
    }

    @Operation
    public static final class Lt {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral("<");

        @Specialization
        public static boolean cmp(int l, int r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(l, r, compareIntsUTF32Node) < 0;
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l < r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l < r;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Lt, SpecialMethodSlot.Gt, true, true);
        }
    }

    @Operation
    public static final class Ge {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral(">=");

        @Specialization
        public static boolean cmp(int l, int r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(l, r, compareIntsUTF32Node) >= 0;
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l >= r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l >= r;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ge, SpecialMethodSlot.Le, true, true);
        }
    }

    @Operation
    public static final class Gt {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral(">");

        @Specialization
        public static boolean cmp(int l, int r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(l, r, compareIntsUTF32Node) > 0;
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l > r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l > r;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Gt, SpecialMethodSlot.Lt, true, true);
        }
    }

    @Operation
    public static final class Eq {

        @Specialization
        public static boolean cmp(int l, int r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.EqualNode equalNode) {
            return equalNode.execute(l, r, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l == r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l == r;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached IsNode isNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return isNode.execute(left, right);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Eq, SpecialMethodSlot.Eq, true, true);
        }
    }

    @Operation
    public static final class Ne {

        @Specialization
        public static boolean cmp(int l, int r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(long l, long r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(char l, char r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(byte l, byte r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(double l, double r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(TruffleString l, TruffleString r,
                        @Cached TruffleString.EqualNode equalNode) {
            return !equalNode.execute(l, r, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int l, double r) {
            return l != r;
        }

        @Specialization
        public static boolean cmp(double l, int r) {
            return l != r;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached IsNode isNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return !isNode.execute(left, right);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ne, SpecialMethodSlot.Ne, true, true);
        }
    }

    @Operation
    public static final class Import {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, TruffleString name, TruffleString[] fromList, int level,
                        @Cached ImportNode node) {
            return node.execute(frame, name, PArguments.getGlobals(frame), fromList, level);
        }
    }

    @Operation
    public static final class ImportFrom {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, Object module, TruffleString name,
                        @Cached ImportFromNode node) {
            return node.execute(frame, module, name);
        }
    }

    @Operation
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

    @Operation
    public static final class Throw {

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
            throw (E) e;
        }

        @Specialization
        public static void doThrow(Throwable ex) {
            sneakyThrow(ex);
        }
    }

    @Operation
    public static final class GetCurrentException {
        @Specialization
        public static PException doPException(VirtualFrame frame) {
            return PArguments.getException(frame);
        }
    }

    @Operation
    public static final class SetCurrentException {
        @Specialization
        @InliningCutoff
        public static void doPException(VirtualFrame frame, PException ex) {
            PArguments.setException(frame, ex);
        }

        @Specialization(guards = {"notPException(ex)"})
        @InliningCutoff
        public static void doAbstractTruffleException(VirtualFrame frame, AbstractTruffleException ex,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            PArguments.setException(frame, ExceptionUtils.wrapJavaException(ex, rootNode, rootNode.factory.createBaseException(SystemError, ErrorMessages.M, new Object[]{ex})));
        }

        @Fallback
        @InliningCutoff
        public static void doNull(VirtualFrame frame, Object ex) {
            assert ex == null;
            PArguments.setException(frame, PException.NO_EXCEPTION);
        }

        static boolean notPException(AbstractTruffleException ex) {
            return ex != null && !(ex instanceof PException);
        }
    }

    @Operation
    public static final class SetCatchingFrameReference {
        @Specialization
        @InliningCutoff
        public static void doPException(VirtualFrame frame, PException ex,
                        @Bind("$root") PBytecodeDSLRootNode rootNode) {
            ex.setCatchingFrameReference(frame, rootNode);
        }

        @Fallback
        @InliningCutoff
        public static void doNothing(VirtualFrame frame, @SuppressWarnings("unused") Object ex) {
        }
    }

    @Operation
    public static final class AssertFailed {
        @Specialization
        public static void doAssertFailed(VirtualFrame frame, Object assertionMessage,
                        @Cached PRaiseNode raise) {
            if (assertionMessage == PNone.NO_VALUE) {
                throw raise.raise(AssertionError);
            } else {
                throw raise.raise(AssertionError, new Object[]{assertionMessage});
            }
        }
    }

    @Operation
    public static final class LoadCell {
        @Specialization
        public static Object doLoadCell(int index, PCell cell,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
        }
    }

    @Operation
    public static final class ClassLoadCell {
        @Specialization
        public static Object doLoadCell(VirtualFrame frame,
                        int index,
                        PCell cell,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadFromLocalsNode readLocalsNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CodeUnit co = rootNode.getCodeUnit();
            TruffleString name = co.freevars[index - co.cellvars.length];
            Object locals = PArguments.getSpecialArgument(frame);
            Object value = readLocalsNode.execute(frame, inliningTarget, locals, name);
            if (value != PNone.NO_VALUE) {
                return value;
            } else {
                return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            }
        }
    }

    @Operation
    public static final class StoreCell {
        @Specialization
        public static void doStoreCell(PCell cell, Object value) {
            cell.setRef(value);
        }
    }

    @Operation
    public static final class CreateCell {
        @Specialization
        public static PCell doCreateCell(Object value) {
            PCell cell = new PCell(Assumption.create());
            cell.setRef(value);
            return cell;
        }
    }

    @Operation
    public static final class ClearCell {
        @Specialization
        public static void doClearCell(int index, PCell cell,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            cell.clearRef();
        }
    }

    @Operation
    public static final class ClearLocal {
        @Specialization
        public static void doClearLocal(VirtualFrame frame, LocalSetter localSetter) {
            localSetter.setObject(frame, null);
        }
    }

    @Operation
    public static final class LoadClosure {
        @Specialization
        public static PCell[] doLoadClosure(VirtualFrame frame) {
            return PArguments.getClosure(frame);
        }
    }

    @Operation
    public static final class StoreRange {
        @Specialization(guards = {"locals.length <= 32"})
        @ExplodeLoop
        public static void doExploded(VirtualFrame frame, Object[] values,
                        LocalSetterRange locals) {
            assert values.length == locals.getLength();
            for (int i = 0; i < locals.length; i++) {
                locals.setObject(frame, i, values[i]);
            }
        }

        @Specialization(replaces = "doExploded")
        public static void doRegular(VirtualFrame frame, Object[] values,
                        LocalSetterRange locals) {
            assert values.length == locals.getLength();
            for (int i = 0; i < locals.length; i++) {
                locals.setObject(frame, i, values[i]);
            }
        }
    }

    @Operation
    public static final class MakeCellArray {
        @Specialization
        public static PCell[] doMakeCellArray(@Variadic Object[] cells) {
            return PCell.toCellArray(cells);
        }
    }

    /**
     * Flattens an array of arrays. Used for splatting Starred expressions.
     */
    @Operation
    public static final class Unstar {
        @Specialization
        @ExplodeLoop
        public static Object[] doUnstar(@Variadic Object[] values,
                        @Cached(value = "values.length", neverDefault = false) int length) {
            // TODO (GR-52217): make length a DSL constant.
            assert values.length == length;

            int totalLength = 0;
            for (int i = 0; i < length; i++) {
                totalLength += ((Object[]) values[i]).length;
            }
            Object[] result = new Object[totalLength];
            int idx = 0;
            for (int i = 0; i < length; i++) {
                int nl = ((Object[]) values[i]).length;
                System.arraycopy(values[i], 0, result, idx, nl);
                idx += nl;
            }

            return result;
        }
    }

    @Operation
    public static final class KwargsMerge {
        @Specialization
        public static PDict doMerge(VirtualFrame frame,
                        PDict dict,
                        Object toMerge,
                        Object function,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached ConcatDictToStorageNode concatNode,
                        @Cached PRaiseNode raise) {
            try {
                HashingStorage resultStorage = concatNode.execute(frame, dict.getDictStorage(), toMerge);
                dict.setDictStorage(resultStorage);
            } catch (SameDictKeyException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, PyObjectFunctionStr.execute(function), e.getKey());
            } catch (NonMappingException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, PyObjectFunctionStr.execute(function), toMerge);
            }
            return dict;
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackStarred {
        @Specialization(guards = {"cannotBeOverridden(sequence, inliningTarget, getClassNode)", "!isPString(sequence)"}, limit = "1")
        public static Object[] doUnpackSequence(VirtualFrame localFrame, PSequence sequence,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            Object[] result = new Object[len];
            for (int i = 0; i < len; i++) {
                result[i] = getItemNode.execute(inliningTarget, storage, i);
            }
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame, Object collection,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            ArrayList<Object> result = new ArrayList<>();
            while (true) {
                try {
                    Object item = getNextNode.execute(virtualFrame, iterator);
                    appendItem(result, item);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    return result.toArray();
                }
            }
        }

        @TruffleBoundary
        private static void appendItem(ArrayList<Object> result, Object item) {
            result.add(item);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackSequence {
        @Specialization(guards = {"cannotBeOverridden(sequence, inliningTarget, getClassNode)", "!isPString(sequence)"}, limit = "1")
        @ExplodeLoop
        public static Object[] doUnpackSequence(VirtualFrame localFrame, PSequence sequence, int count,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached(value = "count", neverDefault = false) int cachedCount,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            // TODO (GR-52217): make count a DSL constant.
            assert count == cachedCount;

            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            if (len == cachedCount) {
                Object[] result = new Object[len];
                for (int i = 0; i < cachedCount; i++) {
                    result[i] = getItemNode.execute(inliningTarget, storage, i);
                }
                return result;
            } else {
                if (len < cachedCount) {
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, cachedCount, len);
                } else {
                    throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, cachedCount);
                }
            }
        }

        @Specialization
        @ExplodeLoop
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame, Object collection, int count,
                        @Shared @Cached(value = "count", neverDefault = false) int cachedCount,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Cached IsBuiltinObjectProfile stopIterationProfile2,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            // TODO (GR-52217): make count a DSL constant.
            assert count == cachedCount;

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[cachedCount];
            for (int i = 0; i < cachedCount; i++) {
                try {
                    result[i] = getNextNode.execute(virtualFrame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, cachedCount, i);
                }
            }
            try {
                getNextNode.execute(virtualFrame, iterator);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopIterationProfile2);
                return result;
            }
            throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, cachedCount);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackEx {
        @Specialization(guards = {"cannotBeOverridden(sequence, inliningTarget, getClassNode)", "!isPString(sequence)"}, limit = "1")
        public static Object[] doUnpackSequence(VirtualFrame localFrame, PSequence sequence, int countBefore, int countAfter,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached(value = "countBefore", neverDefault = false) int cachedCountBefore,
                        @Shared @Cached(value = "countAfter", neverDefault = false) int cachedCountAfter,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            // TODO (GR-52217): make countBefore and countAfter DSL constants.
            assert countBefore == cachedCountBefore;
            assert countAfter == cachedCountAfter;

            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            int starLen = len - cachedCountBefore - cachedCountAfter;
            if (starLen < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, cachedCountBefore + cachedCountAfter, len);
            }

            Object[] result = new Object[cachedCountBefore + 1 + cachedCountAfter];
            copyItemsToArray(inliningTarget, storage, 0, result, 0, cachedCountBefore, getItemNode);
            result[cachedCountBefore] = factory.createList(getItemSliceNode.execute(storage, cachedCountBefore, cachedCountBefore + starLen, 1, starLen));
            copyItemsToArray(inliningTarget, storage, len - cachedCountAfter, result, cachedCountBefore + 1, cachedCountAfter, getItemNode);
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame, Object collection, int countBefore, int countAfter,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached(value = "countBefore", neverDefault = false) int cachedCountBefore,
                        @Shared @Cached(value = "countAfter", neverDefault = false) int cachedCountAfter,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            // TODO (GR-52217): make countBefore and countAfter DSL constants.
            assert countBefore == cachedCountBefore;
            assert countAfter == cachedCountAfter;

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[cachedCountBefore + 1 + cachedCountAfter];
            copyItemsToArray(virtualFrame, inliningTarget, iterator, result, 0, cachedCountBefore, cachedCountBefore + cachedCountAfter, getNextNode, stopIterationProfile, raiseNode);
            PList starAndAfter = constructListNode.execute(virtualFrame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < cachedCountAfter) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, cachedCountBefore + cachedCountAfter, cachedCountBefore + lenAfter);
            }
            if (cachedCountAfter == 0) {
                result[cachedCountBefore] = starAndAfter;
            } else {
                int starLen = lenAfter - cachedCountAfter;
                PList starList = factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                result[cachedCountBefore] = starList;
                copyItemsToArray(inliningTarget, storage, starLen, result, cachedCountBefore + 1, cachedCountAfter, getItemNode);
            }
            return result;
        }

        @ExplodeLoop
        private static void copyItemsToArray(VirtualFrame frame, Node inliningTarget, Object iterator, Object[] destination, int destinationOffset, int length, int totalLength,
                        GetNextNode getNextNode,
                        IsBuiltinObjectProfile stopIterationProfile, PRaiseNode.Lazy raiseNode) {
            CompilerAsserts.partialEvaluationConstant(destinationOffset);
            CompilerAsserts.partialEvaluationConstant(length);
            CompilerAsserts.partialEvaluationConstant(totalLength);
            for (int i = 0; i < length; i++) {
                try {
                    destination[destinationOffset + i] = getNextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile);
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, destinationOffset + i);
                }
            }
        }

        @ExplodeLoop
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

    @Operation
    public static final class CallNilaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable,
                        @Cached CallNode node) {
            return node.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Operation
    public static final class CallUnaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0,
                        @Cached CallUnaryMethodNode node) {
            return node.executeObject(frame, callable, arg0);
        }
    }

    @Operation
    public static final class CallBinaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame, Object callable, Object arg0, Object arg1,
                        @Cached CallBinaryMethodNode node) {
            return node.executeObject(frame, callable, arg0, arg1);
        }
    }

    @Operation
    public static final class CallTernaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2,
                        @Cached CallTernaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2);
        }
    }

    @Operation
    public static final class CallQuaternaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2, Object arg3,
                        @Cached CallQuaternaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2, arg3);
        }
    }

    @Operation
    public static final class CallVarargsMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object[] args, PKeyword[] keywords,
                        @Cached CallNode node) {
            return node.execute(frame, callable, args, keywords);
        }
    }

    @Operation
    @ImportStatic({SpecialMethodSlot.class})
    public static final class ContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame, Object contextManager,
                        LocalSetter exitSetter,
                        LocalSetter resultSetter,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached(parameters = "Enter") LookupSpecialMethodSlotNode lookupEnter,
                        @Cached(parameters = "Exit") LookupSpecialMethodSlotNode lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, type, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, new Object[]{T___ENTER__});
            }
            Object exit = lookupExit.execute(frame, type, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, new Object[]{T___EXIT__});
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(frame, exit);
            resultSetter.setObject(frame, result);
        }
    }

    @Operation
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
                        @Bind("this") Node inliningTarget,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException pException) {
                    PArguments.setException(frame, pException);
                    pythonException = pException.getEscapedException();
                }
                Object excType = getClass.execute(inliningTarget, pythonException);
                Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
                Object result = callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
                if (!isTrue.execute(frame, inliningTarget, result)) {
                    if (exception instanceof PException pException) {
                        throw pException.getExceptionForReraise(rootNode.isPythonInternal());
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

    @Operation
    @ImportStatic({SpecialMethodSlot.class})
    public static final class AsyncContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame, Object contextManager,
                        LocalSetter exitSetter,
                        LocalSetter resultSetter,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached(parameters = "AEnter") LookupSpecialMethodSlotNode lookupEnter,
                        @Cached(parameters = "AExit") LookupSpecialMethodSlotNode lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, type, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, new Object[]{T___AENTER__});
            }
            Object exit = lookupExit.execute(frame, type, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, new Object[]{T___AEXIT__});
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(frame, exit);
            resultSetter.setObject(frame, result);
        }
    }

    @Operation
    public static final class AsyncContextManagerCallExit {
        @Specialization
        public static Object doRegular(VirtualFrame frame,
                        PNone none, Object exit, Object contextManager,
                        @Shared @Cached CallQuaternaryMethodNode callExit) {
            return callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        }

        @Specialization
        @InliningCutoff
        public static Object doExceptional(VirtualFrame frame,
                        Object exception, Object exit, Object contextManager,
                        @Bind("this") Node inliningTarget,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException) {
                    PArguments.setException(frame, (PException) exception);
                    pythonException = ((PException) exception).getEscapedException();
                }
                Object excType = getClass.execute(inliningTarget, pythonException);
                Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
                return callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
            } finally {
                PArguments.setException(frame, savedExcState);
            }
        }
    }

    @Operation
    public static final class AsyncContextManagerExit {
        /**
         * NB: There is nothing to do after awaiting __exit__(None, None, None), so this operation
         * is only emitted for the case where the context manager exits due to an exception.
         */
        @Specialization
        @InliningCutoff
        public static void doExceptional(VirtualFrame frame,
                        Object exception, Object result,
                        @Bind("this") Node inliningTarget,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!isTrue.execute(frame, inliningTarget, result)) {
                if (exception instanceof PException) {
                    throw ((PException) exception).getExceptionForReraise(rootNode.isPythonInternal());
                } else if (exception instanceof AbstractTruffleException) {
                    throw (AbstractTruffleException) exception;
                } else {
                    throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
                }
            }
        }
    }

    @Operation
    public static final class BuildString {
        @Specialization
        @ExplodeLoop
        public static Object doBuildString(@Variadic Object[] strings,
                        @Cached(value = "strings.length", neverDefault = false) int length,
                        @Cached TruffleStringBuilder.AppendStringNode appendNode,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            // TODO (GR-52217): make length a DSL constant.
            assert strings.length == length;

            TruffleStringBuilder tsb = TruffleStringBuilder.create(PythonUtils.TS_ENCODING);

            for (int i = 0; i < length; i++) {
                appendNode.execute(tsb, (TruffleString) strings[i]);
            }

            return toString.execute(tsb);
        }
    }

    @Operation
    public static final class ListExtend {
        @Specialization
        public static void listExtend(VirtualFrame frame, PList list, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            ListExtendNode.extendSequence(frame, list, obj, inliningTarget, lenNode, extendNode);
        }

        @NeverDefault
        public static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }
    }

    @Operation
    public static final class TeeLocal {
        @Specialization
        public static int doInt(VirtualFrame frame, int value,
                        LocalSetter local) {
            local.setInt(frame, value);
            return value;
        }

        @Specialization
        public static double doDouble(VirtualFrame frame, double value,
                        LocalSetter local) {
            local.setDouble(frame, value);
            return value;
        }

        @Specialization
        public static long doLong(VirtualFrame frame, long value,
                        LocalSetter local) {
            local.setLong(frame, value);
            return value;
        }

        @Specialization(replaces = {"doInt", "doDouble", "doLong"})
        public static Object doObject(VirtualFrame frame, Object value,
                        LocalSetter local) {
            local.setObject(frame, value);
            return value;
        }
    }

    @Operation
    public static final class NonNull {
        @Specialization
        public static boolean doObject(Object value) {
            return value != null;
        }
    }

    @Operation
    public static final class GetLen {
        @Specialization
        public static int doObject(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, inliningTarget, value);
        }
    }

    @Operation
    public static final class IsSequence {
        @Specialization
        public static boolean doObject(Object value,
                        @Cached GetTPFlagsNode getTPFlagsNode) {
            return (getTPFlagsNode.execute(value) & TypeFlags.SEQUENCE) != 0;
        }
    }

    @Operation
    public static final class IsMapping {
        @Specialization
        public static boolean doObject(Object value,
                        @Cached GetTPFlagsNode getTPFlagsNode) {
            return (getTPFlagsNode.execute(value) & TypeFlags.MAPPING) != 0;
        }
    }

    @Operation
    @ImportStatic(PGuards.class)
    public static final class BinarySubscript {
        // TODO: support boxing elimination
// @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
// public static int doIntSequence(PList sequence, int index,
// @Shared("list") @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) throws
// UnexpectedResultException {
// return getItemNode.executeInt(sequence.getSequenceStorage(), index);
// }
//
// @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
// public static int doIntTuple(PTuple sequence, int index,
// @Shared("tuple") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) throws
// UnexpectedResultException {
// return getItemNode.executeInt(sequence.getSequenceStorage(), index);
// }
//
// @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
// public static double doDoubleSequence(PList sequence, int index,
// @Shared("list") @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) throws
// UnexpectedResultException {
// return getItemNode.executeDouble(sequence.getSequenceStorage(), index);
// }
//
// @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
// public static double doDoubleTuple(PTuple sequence, int index,
// @Shared("tuple") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) throws
// UnexpectedResultException {
// return getItemNode.executeDouble(sequence.getSequenceStorage(), index);
// }
// TODO: add @Shared to GetItemNodes

        @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
        public static Object doObjectSequence(PList sequence, int index,
                        @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(sequence.getSequenceStorage(), index);
        }

        @Specialization(guards = "cannotBeOverriddenForImmutableType(sequence)")
        public static Object doObjectTuple(PTuple sequence, int index,
                        @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(sequence.getSequenceStorage(), index);
        }

        @Specialization
        public static Object doObjectKey(VirtualFrame frame, Object receiver, Object key,
                        @Cached(inline = false) PyObjectGetItem getItemNode) {
            return getItemNode.executeCached(frame, receiver, key);
        }
    }

    @Operation
    public static final class PreYield {
        @Specialization
        public static Object doObject(VirtualFrame frame, Object value) {
            // Store any current exception into the generator frame so it is remembered on
            // resumption.
            PArguments.setException(PArguments.getGeneratorFrame(frame), PArguments.getException(frame));
            return value;
        }
    }

    @Operation
    public static final class ResumeYield {
        @Specialization
        public static Object doObject(VirtualFrame frame, Object sendValue,
                        @Cached GetSendValueNode getSendValue) {
            /**
             * This operation resumes execution after a yield. Most of the resumption work is done
             * by the caller. All we need to do here is restore any existing exception state from
             * before the yield and process the sent/thrown value.
             */
            PException currentException = PArguments.getException(PArguments.getGeneratorFrame(frame));
            if (currentException != null) {
                PArguments.setException(frame, currentException);
            }
            return getSendValue.execute(sendValue);
        }
    }

    @Operation
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class YieldFromSend {
        private static final TruffleString T_SEND = tsLiteral("send");

        @Specialization
        static boolean doGenerator(VirtualFrame virtualFrame, PGenerator generator, Object arg,
                        LocalSetter yieldValue,
                        LocalSetter returnValue,
                        @Bind("this") Node inliningTarget,
                        @Cached CommonGeneratorBuiltins.SendNode sendNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = sendNode.execute(virtualFrame, generator, arg);
                yieldValue.setObject(virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, returnValue);
                return true;
            }
        }

        @Specialization(guards = "iterCheck.execute(inliningTarget, iter)", limit = "1")
        static boolean doIterator(VirtualFrame virtualFrame, Object iter, @SuppressWarnings("unused") PNone arg,
                        LocalSetter yieldValue,
                        LocalSetter returnValue,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyIterCheckNode iterCheck,
                        @Cached GetNextNode getNextNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = getNextNode.execute(virtualFrame, iter);
                yieldValue.setObject(virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, returnValue);
                return true;
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame virtualFrame, Object obj, Object arg,
                        LocalSetter yieldValue,
                        LocalSetter returnValue,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = callMethodNode.execute(virtualFrame, inliningTarget, obj, T_SEND, arg);
                yieldValue.setObject(virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, returnValue);
                return true;
            }
        }

        private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, IsBuiltinObjectProfile stopIterationProfile,
                        StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalSetter returnValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnValue.setObject(frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }

    }

    @Operation
    @SuppressWarnings("turffle-interpreted-performance")
    public static final class YieldFromThrow {

        private static final TruffleString T_CLOSE = tsLiteral("close");
        private static final TruffleString T_THROW = tsLiteral("throw");

        @Specialization
        static boolean doGenerator(VirtualFrame frame, PGenerator generator, PException exception,
                        LocalSetter yieldValue,
                        LocalSetter returnValue,
                        @Bind("this") Node inliningTarget,
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
                    yieldValue.setObject(frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, inliningTarget, e, stopIterationProfile, getValue, returnValue);
                    return true;
                }
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame frame, Object obj, Object exception,
                        LocalSetter yieldValue,
                        LocalSetter returnValue,
                        @Bind("this") Node inliningTarget,
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
                    yieldValue.setObject(frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, inliningTarget, e, stopIterationProfile, getValue, returnValue);
                    return true;
                }
            }
        }

        private static void handleException(VirtualFrame frame, Node inliningTarget, PException e,
                        IsBuiltinObjectProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalSetter returnValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnValue.setObject(frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }
    }

    @Operation
    public static final class CheckUnboundLocal {
        @Specialization
        public static Object doObject(int index, Object localValue,
                        @Bind("$root") PBytecodeDSLRootNode rootNode,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (localValue == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                TruffleString localName = rootNode.getCodeUnit().varnames[index];
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
            }
            return localValue;
        }
    }

    @Operation
    public static final class RaiseNotImplementedError {
        @Specialization
        public static void doRaise(VirtualFrame frame, TruffleString name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError, name);

        }
    }
}
