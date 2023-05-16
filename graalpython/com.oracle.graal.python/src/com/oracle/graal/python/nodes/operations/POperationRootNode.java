package com.oracle.graal.python.nodes.operations;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemSliceNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListExtendNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.bytecode.ImportFromNode;
import com.oracle.graal.python.nodes.bytecode.ImportNode;
import com.oracle.graal.python.nodes.bytecode.ImportStarNode;
import com.oracle.graal.python.nodes.bytecode.RaiseNode;
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
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.RestoreExceptionStateNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SaveExceptionStateNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.LocalSetter;
import com.oracle.truffle.api.operation.LocalSetterRange;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.ShortCircuitOperation;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@GenerateOperations(//
                languageClass = PythonLanguage.class, //
                boxingEliminationTypes = {int.class, long.class, double.class}, //
                enableYield = true, //
                decisionsFile = "decisions.json")
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
@OperationProxy(YesNode.class)
@OperationProxy(NotNode.class)
@OperationProxy(IsNode.class)
@OperationProxy(ContainsNode.class)
@OperationProxy(value = PyObjectGetIter.class, operationName = "GetIter")
@OperationProxy(value = PyObjectGetItem.class, operationName = "GetItem")
@OperationProxy(RaiseNode.class)
@OperationProxy(ExceptMatchNode.class)
@OperationProxy(SaveExceptionStateNode.class)
@OperationProxy(RestoreExceptionStateNode.class)
@OperationProxy(value = PyObjectReprAsTruffleStringNode.class, operationName = "FormatRepr")
@OperationProxy(value = PyObjectAsciiNode.class, operationName = "FormatAscii")
@OperationProxy(value = PyObjectStrAsTruffleStringNode.class, operationName = "FormatStr")
@OperationProxy(value = ListNodes.AppendNode.class, operationName = "ListAppend")
@OperationProxy(value = SetNodes.AddNode.class, operationName = "SetAdd")
@OperationProxy(value = DictNodes.UpdateNode.class, operationName = "DictUpdate")
@ShortCircuitOperation(name = "BoolAnd", booleanConverter = YesNode.class, continueWhen = true)
@ShortCircuitOperation(name = "BoolOr", booleanConverter = YesNode.class, continueWhen = false)
@SuppressWarnings("unused")
public abstract class POperationRootNode extends PRootNode implements OperationRootNode {

    protected POperationRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
        setCode(PythonUtils.EMPTY_BYTE_ARRAY);
    }

    private String name;
    private Signature signature;

    @Child protected PythonObjectFactory factory = PythonObjectFactory.create();

    @Override
    public String toString() {
        return "<function op " + name + ">";
    }

    private static final boolean LOG_CALLS = false;

    @Child private CalleeContext calleeContext = CalleeContext.create();

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
        if (throwable != null) {
            System.err.println(" ======================================= ");
            System.err.println(throwable);
            System.err.println(" Name: " + getName());
            System.err.println(dump());
            System.err.println(" ======================================= ");
        }
        calleeContext.exit(frame, this);
    }

    @Override
    public boolean isPythonInternal() {
        return false;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    @Override
    protected byte[] extractCode() {
        return PythonUtils.EMPTY_BYTE_ARRAY;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Operation
    public static final class UnwrapException {
        @Specialization
        public static PBaseException doInts(PException ex) {
            return ex.getEscapedException();
        }

        @Fallback
        public static Object doStrings(Object ex) {
            // Let interop exceptions be
            assert ex instanceof AbstractTruffleException;
            return ex;
        }
    }

    @Operation
    public static final class Equals {
        @Specialization
        public static boolean doInts(int lhs, int rhs) {
            return lhs == rhs;
        }

        @Specialization
        public static boolean doStrings(String lhs, String rhs) {
            return lhs.equals(rhs);
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
                        @Cached("create(name)") WriteNameNode writeNode) {
            assert name.equals(writeNode.getAttributeId()) : "name should be compilation constant";
            writeNode.execute(frame, value);
        }
    }

    @Operation
    public static final class ReadName {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached("create(name)") ReadNameNode readNode,
                        @Bind("$bci") int bci) {
            assert name.equals(readNode.getAttributeId()) : "name should be compilation constant";
            Object result = readNode.execute(frame);
            return result;
        }
    }

    @Operation
    public static final class DeleteName {
        @Specialization(guards = "hasLocals(frame)")
        public static void performLocals(VirtualFrame frame, TruffleString name, @Cached PyObjectDelItem deleteNode) {
            deleteNode.executeCached(frame, PArguments.getSpecialArgument(frame), name);
        }

        @Specialization(guards = "!hasLocals(frame)")
        public static void performGlobals(VirtualFrame frame, TruffleString name, @Cached("create(name)") DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame));
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
        public static Object perform(VirtualFrame frame, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createTuple(PArguments.getVariableArguments(frame));
        }
    }

    @Operation
    public static final class LoadKeywordArguments {
        @Specialization
        public static Object perform(VirtualFrame frame, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createDict(PArguments.getKeywordArguments(frame));
        }
    }

    @Operation
    public static final class MakeFunction {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name, PCode code, Object[] defaults, Object[] kwDefaultsObject, Object closure, @Bind("$root") Node node) {
            PKeyword[] kwDefaults = new PKeyword[kwDefaultsObject.length];
            System.arraycopy(kwDefaultsObject, 0, kwDefaults, 0, kwDefaults.length);
            return ((POperationRootNode) node).factory.createFunction(name, code, PArguments.getGlobals(frame), defaults, kwDefaults, (PCell[]) closure);
        }
    }

    @Operation
    public static final class MakeGenerator {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name, TruffleString qualname, POperationRootNode genNode, @Bind("$root") Node node) {
            // TODO: createGenerator takes an array of call targets (one per bci, mostly null) so
            // that generators can be PE'd. But we don't know how many bci there are, since the DSL
            // does it for us. We probably need to hook into the Operation DSL's yield
            // mechanism instead of using an operation.
            return ((POperationRootNode) node).factory.createGenerator(name, qualname, genNode, null, frame.getArguments());
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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IXor);
        }
    }

    @Operation
    public static final class IRShift {

        @Specialization
        public static int add(int left, int right) {
            return left >> right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left >> right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IRShift);
        }
    }

    @Operation
    public static final class ILShift {

        @Specialization
        public static int add(int left, int right) {
            return left << right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left << right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ILShift);
        }
    }

    @Operation
    public static final class IMatMul {

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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
                        @Cached("create()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMul);
        }
    }

    @Operation
    public static final class ITrueDiv {
        @Specialization
        public static double doII(int left, int right, @Bind("this") Node self) {
            return TrueDivNode.doII(left, right, self);
        }

        @Specialization
        public static double doDL(double left, long right, @Bind("this") Node self) {
            return TrueDivNode.doDL(left, right, self);
        }

        @Specialization
        public static double doDD(double left, double right, @Bind("this") Node self) {
            return TrueDivNode.doDD(left, right, self);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ITrueDiv);
        }
    }

    @Operation
    public static final class IFloorDiv {
        @Specialization
        public static int doII(int left, int right, @Bind("this") Node self) {
            return FloorDivNode.doII(left, right, self);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right, @Bind("this") Node self) throws OverflowException {
            return FloorDivNode.doLL(left, right, self);
        }

        @Specialization
        public static double doDL(double left, long right, @Bind("this") Node self) {
            return FloorDivNode.doDL(left, right, self);
        }

        @Specialization
        public static double doDD(double left, double right, @Bind("this") Node self) {
            return FloorDivNode.doDD(left, right, self);
        }

        @Specialization
        public static double doLD(long left, double right, @Bind("this") Node self) {
            return FloorDivNode.doLD(left, right, self);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

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

        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IPow);
        }
    }

    @Operation
    public static final class IMod {
        @Specialization
        public static double doII(int left, int right, @Bind("this") Node self) {
            return ModNode.doII(left, right, self);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static double doLL(long left, long right, @Bind("this") Node self) throws OverflowException {
            return ModNode.doLL(left, right, self);
        }

        @Specialization
        public static double doDL(double left, long right, @Bind("this") Node self) {
            return ModNode.doDL(left, right, self);
        }

        @Specialization
        public static double doDD(double left, double right, @Bind("this") Node self) {
            return ModNode.doDD(left, right, self);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMod);
        }
    }

    @Operation
    public static final class ForIterate {

        @Specialization
        public static boolean doIntegerIterator(VirtualFrame frame, PIntegerIterator iterator, LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setInt(frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doObjectIterator(VirtualFrame frame, PObjectSequenceIterator iterator, LocalSetter output) {
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
        public static boolean doLongIterator(VirtualFrame frame, PLongSequenceIterator iterator, LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setLong(frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doDoubleIterator(VirtualFrame frame, PDoubleSequenceIterator iterator, LocalSetter output) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setDouble(frame, iterator.next());
            return true;
        }

        @Specialization
        @InliningCutoff
        public static boolean doIterator(VirtualFrame frame, Object object, LocalSetter output,
                        @Bind("this") Node self,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                Object value = next.execute(frame, object);
                output.setObject(frame, value);
                return value != null;
            } catch (PException e) {
                output.setObject(frame, null);
                e.expectStopIteration(self, errorProfile);
                return false;
            }
        }
    }

    @Operation
    public static final class GetAttribute {
        @Specialization
        @InliningCutoff
        public static Object doIt(VirtualFrame frame,
                        Object obj, TruffleString name,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            assert name.equals(getAttributeNode.getKey()) : "name must be compilation constsnt";

            Object value = getAttributeNode.execute(frame, obj);
            return value;
        }
    }

    @Operation
    public static final class SetAttribute {
        @Specialization
        @InliningCutoff
        public static void doIt(VirtualFrame frame, Object value, Object object, Object key,
                        @Cached("create()") LookupAndCallTernaryNode call) {
            if (value instanceof PCell) {
                throw new AssertionError("pcell");
            }
            call.execute(frame, object, key, value);
        }

        public static LookupAndCallTernaryNode create() {
            return LookupAndCallTernaryNode.create(SpecialMethodSlot.SetAttr);
        }
    }

    @Operation
    public static final class DeleteAttribute {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame, Object object, Object key,
                        @Cached("create()") LookupAndCallBinaryNode call) {
            return call.executeObject(frame, object, key);
        }

        public static LookupAndCallBinaryNode create() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.DelAttr);
        }
    }

    @Operation
    @ImportStatic(SpecialMethodSlot.class)
    public static final class DeleteItem {
        @Specialization
        public static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(DelItem)") LookupSpecialMethodSlotNode lookupDelitem,
                        @Cached PRaiseNode raise,
                        @Cached CallBinaryMethodNode callDelitem) {
            Object delitem = lookupDelitem.execute(frame, getClassNode.execute(primary), primary);
            if (delitem == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
            }
            callDelitem.executeObject(frame, delitem, primary, index);
        }
    }

    @Operation
    public static final class ReadGlobal {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name, @Cached("create(name)") ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame);
        }
    }

    @Operation
    public static final class WriteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, Object value, TruffleString name, @Cached("create(name)") WriteGlobalNode writeNode) {
            writeNode.executeObject(frame, value);
        }
    }

    @Operation
    public static final class DeleteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name, @Cached("create(name)") DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame));
        }
    }

    @Operation
    public static final class BuildClass {

        public static final TruffleString NAME = BuiltinNames.T___BUILD_CLASS__;

        @Specialization
        @InliningCutoff
        public static Object perform(VirtualFrame frame, @Cached("create(NAME)") ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame);
        }
    }

    @Operation
    public static final class MakeList {
        @Specialization
        public static PList perform(Object[] elements, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createList(elements);
        }
    }

    @Operation
    public static final class MakeSet {
        @Specialization
        public static PSet perform(VirtualFrame frame, Object[] elements, @Bind("$root") Node node, @Cached SetNodes.AddNode addNode, @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            int size = elements.length;
            CompilerAsserts.compilationConstant(size);

            PSet set = ((POperationRootNode) node).factory.createSet();
            for (int i = 0; i < size; i++) {
                SetNodes.AddNode.add(frame, set, elements[i], addNode, setItemNode);
            }

            return set;
        }
    }

    @Operation
    public static final class MakeEmptySet {
        @Specialization
        public static PSet perform(VirtualFrame frame, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createSet();
        }
    }

    @Operation
    public static final class MakeFrozenSet {
        @Specialization
        @ExplodeLoop
        public static PFrozenSet doFrozenSet(VirtualFrame frame, @Variadic Object[] elements,
                        @Cached HashingStorageSetItem hashingStorageLibrary,
                        @Bind("$root") Node node) {
            HashingStorage setStorage = EmptyStorage.INSTANCE;
            int length = elements.length;
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; ++i) {
                Object o = elements[i];
                setStorage = hashingStorageLibrary.execute(frame, setStorage, o, PNone.NONE);
            }
            return ((POperationRootNode) node).factory.createFrozenSet(setStorage);
        }
    }

    @Operation
    public static final class MakeEmptyList {
        @Specialization
        public static PList perform(@Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createList();
        }
    }

    @Operation
    public static final class MakeTuple {
        @Specialization
        @ExplodeLoop
        public static Object perform(@Variadic Object[] elements, @Bind("$root") Node node) {
            CompilerAsserts.compilationConstant(elements.length);

            int totalLength = 0;
            for (Object arr : elements) {
                totalLength += ((Object[]) arr).length;
            }

            Object[] elts = new Object[totalLength];
            int idx = 0;
            for (Object arr : elements) {
                int len = ((Object[]) arr).length;
                System.arraycopy(arr, 0, elts, idx, len);
                idx += len;
            }

            return ((POperationRootNode) node).factory.createTuple(elts);
        }
    }

    @Operation
    public static final class MakeSlice {

        @Specialization
        public static Object doIII(int start, int end, int step, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createIntSlice(start, end, step);
        }

        @Specialization
        public static Object doNIN(PNone start, int end, PNone step, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createIntSlice(0, end, 1, true, true);
        }

        @Specialization
        public static Object doIIN(int start, int end, PNone step, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createIntSlice(start, end, 1, false, true);
        }

        @Specialization
        public static Object doNII(PNone start, int end, int step, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createIntSlice(0, end, step, true, false);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(Object start, Object end, Object step, @Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createObjectSlice(start, end, step);
        }
    }

    @Operation
    public static final class MakeKeywords {
        @Specialization
        @ExplodeLoop
        public static PKeyword[] perform(TruffleString[] keys, @Variadic Object[] values, @Bind("$root") Node node) {
            int length = keys.length;
            assert values.length == length;
            CompilerAsserts.compilationConstant(keys);
            CompilerAsserts.compilationConstant(length);
            PKeyword[] result = new PKeyword[length];
            for (int i = 0; i < length; i++) {
                result[i] = new PKeyword(keys[i], values[i]);
            }
            return result;
        }
    }

    @Operation
    public static final class SplatKeywords {
        @Specialization
        public static PKeyword[] perform(Object sourceCollection,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached PRaiseNode raise) {
            return expandKeywordStarargsNode.execute(sourceCollection);
        }
    }

    @Operation
    public static final class MakeDict {
        @Specialization
        @ExplodeLoop
        public static PDict perform(VirtualFrame frame, @Variadic Object[] keysAndValues, @Bind("$root") Node node, @Cached DictNodes.UpdateNode updateNode) {
            PDict dict = ((POperationRootNode) node).factory.createDict();
            for (int i = 0; i < keysAndValues.length; i += 2) {
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
        public static PDict perform(@Bind("$root") Node node) {
            return ((POperationRootNode) node).factory.createDict();
        }
    }

    @Operation
    public static final class SetDictItem {
        @Specialization
        public static void perform(Object key, Object value, PDict item) {
            item.setItem(key, value);
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
                        @Shared("setItemNode") @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, primary, slice, value);
        }

        @Specialization
        public static void performI(VirtualFrame frame, int value, Object primary, Object slice,
                        @Shared("setItemNode") @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, primary, slice, value);
        }

        @Specialization
        public static void performL(VirtualFrame frame, long value, Object primary, Object slice,
                        @Shared("setItemNode") @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, primary, slice, value);
        }

        @Specialization
        public static void performD(VirtualFrame frame, double value, Object primary, Object slice,
                        @Shared("setItemNode") @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, primary, slice, value);
        }

        @Specialization
        public static void performO(VirtualFrame frame, Object value, Object primary, Object slice,
                        @Shared("setItemNode") @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, primary, slice, value);
        }
    }

    @Operation
    @ImportStatic(SpecialMethodSlot.class)
    public static final class DelItem {
        @Specialization
        static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(DelItem)") LookupSpecialMethodSlotNode lookupDelitem,
                        @Cached PRaiseNode raise,
                        @Cached CallBinaryMethodNode callDelitem) {
            Object delitem = lookupDelitem.execute(frame, getClassNode.execute(primary), primary);
            if (delitem == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
            }
            callDelitem.executeObject(frame, delitem, primary, index);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackIterable {

        @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
        @ExplodeLoop
        public static void doUnpackSequence(VirtualFrame localFrame, PSequence sequence,
                        LocalSetterRange results,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached BranchProfile errorProfile,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(sequence);
            int len = storage.length();

            int count = results.length();
            CompilerAsserts.partialEvaluationConstant(count);

            if (len == count) {
                for (int i = 0; i < count; i++) {
                    Object value = getItemNode.execute(storage, i);
                    if (value == null) {
                        throw new AssertionError("null in list: " + sequence);
                    }
                    results.setObject(localFrame, i, value);
                }
            } else {
                errorProfile.enter();
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
                        @Bind("this") Node self,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Cached IsBuiltinObjectProfile stopIterationProfile2,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {

            int count = results.length();
            CompilerAsserts.partialEvaluationConstant(count);

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, collection);
            } catch (PException e) {
                e.expectTypeError(self, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            for (int i = 0; i < count; i++) {
                try {
                    Object item = getNextNode.execute(virtualFrame, iterator);
                    if (item == null) {
                        throw new AssertionError("null in list: " + collection);
                    }
                    results.setObject(virtualFrame, i, item);
                } catch (PException e) {
                    e.expectStopIteration(self, stopIterationProfile1);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                getNextNode.execute(virtualFrame, iterator);
            } catch (PException e) {
                e.expectStopIteration(self, stopIterationProfile2);
                return;
            }
            throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackIterableStarred {

        @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
        public static void doUnpackSequence(VirtualFrame localFrame,
                        PSequence sequence, int starIndex, LocalSetterRange results,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached GetItemSliceNode getItemSliceNode,
                        @Cached BranchProfile errorProfile,
                        @Bind("$root") Node node,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {

            int resultsLength = results.length();
            CompilerAsserts.compilationConstant(results);
            CompilerAsserts.compilationConstant(starIndex);
            CompilerAsserts.compilationConstant(resultsLength);

            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            SequenceStorage storage = getSequenceStorageNode.execute(sequence);
            int len = storage.length();

            int starLen = len - resultsLength + 1;
            if (starLen < 0) {
                errorProfile.enter();
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            moveItemsToStack(storage, localFrame, results, 0, 0, countBefore, getItemNode);
            PList starList = ((POperationRootNode) node).factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            results.setObject(localFrame, starIndex, starList);
            moveItemsToStack(storage, localFrame, results, starIndex + 1, len - countAfter, countAfter, getItemNode);
        }

        @Specialization
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame frame,
                        Object collection, int starIndex, LocalSetterRange results,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Bind("this") Node self,
                        @Bind("$root") Node node,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {

            int resultsLength = results.length();
            CompilerAsserts.compilationConstant(results);
            CompilerAsserts.compilationConstant(starIndex);
            CompilerAsserts.compilationConstant(resultsLength);

            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            CompilerAsserts.partialEvaluationConstant(countBefore);
            CompilerAsserts.partialEvaluationConstant(countAfter);

            Object iterator;
            try {
                iterator = getIter.execute(frame, collection);
            } catch (PException e) {
                e.expectTypeError(self, notIterableProfile);
                throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            moveItemsToStack(frame, self, iterator, results, 0, 0, countBefore, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);

            PList starAndAfter = constructListNode.execute(frame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                results.setObject(frame, starIndex, starAndAfter);
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = ((POperationRootNode) node).factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                results.setObject(frame, starIndex, starList);

                moveItemsToStack(storage, frame, results, starIndex + 1, starLen, countAfter, getItemNode);
            }
        }

        @ExplodeLoop
        private static void moveItemsToStack(VirtualFrame frame, Node self, Object iterator, LocalSetterRange results, int resultsOffset, int offset, int length, int totalLength,
                        GetNextNode getNextNode,
                        IsBuiltinObjectProfile stopIterationProfile, PRaiseNode raiseNode) {
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
        private static void moveItemsToStack(SequenceStorage storage, VirtualFrame localFrame, LocalSetterRange run, int runOffset, int offset, int length,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                run.setObject(localFrame, runOffset + i, getItemNode.execute(storage, offset + i));
            }
        }
    }

    private static final RuntimeException noSupported(Object left, Object right, PRaiseNode raiseNode, TruffleString operator) {
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
                throw noSupported(left, right, raiseNode, T_OPERATOR);
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
                throw noSupported(left, right, raiseNode, T_OPERATOR);
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
                throw noSupported(left, right, raiseNode, T_OPERATOR);
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
                throw noSupported(left, right, raiseNode, T_OPERATOR);
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
        public static Object doImport(VirtualFrame frame, TruffleString name, TruffleString[] fromList, int level, @Cached ImportNode node) {
            return node.execute(frame, name, PArguments.getGlobals(frame), fromList, level);
        }
    }

    @Operation
    public static final class ImportFrom {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, Object module, TruffleString name, @Cached ImportFromNode node) {
            return node.execute(frame, module, name);
        }
    }

    @Operation
    public static final class ImportStar {
        @Specialization
        @InliningCutoff
        public static void doImport(VirtualFrame frame, TruffleString name, int level, @Cached("create(name, level)") ImportStarNode node) {
            node.execute(frame, name, level);
        }

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
    public static final class SetExceptionState {
        @Specialization
        @InliningCutoff
        public static void doPException(VirtualFrame frame, PException ex, @Bind("$root") Node node) {
            // todo: properly implement
            // ex.setCatchingFrameReference(frame, node);
            PArguments.setException(frame, ex);
        }

        @Specialization(guards = {"notPException(ex)"})
        @InliningCutoff
        public static void doAbstractTruffleException(VirtualFrame frame, AbstractTruffleException ex, @Bind("$root") Node node) {
            PArguments.setException(frame, ExceptionUtils.wrapJavaException(ex, node, ((POperationRootNode) node).factory.createBaseException(SystemError, ErrorMessages.M, new Object[]{ex})));
        }

        static boolean notPException(AbstractTruffleException ex) {
            return !(ex instanceof PException);
        }
    }

    @Operation
    public static final class AssertFailed {
        @Specialization
        public static void doAssertFailed(VirtualFrame frame, Object assertionMessage, @Cached PRaiseNode raise) {
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
        public static Object doLoadCell(PCell cell) {
            return cell.getRef();
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
        public static void doClearCell(PCell cell) {
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
        @ExplodeLoop
        public static void doLoadClosure(VirtualFrame frame, LocalSetterRange locals) {
            PCell[] closure = PArguments.getClosure(frame);
            int length = locals.length();
            assert closure.length == length;
            for (int i = 0; i < length; i++) {
                locals.setObject(frame, i, closure[i]);
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
        public static Object[] doUnstar(@Variadic Object[] values) {
            int totalLength = 0;
            for (int i = 0; i < values.length; i++) {
                totalLength += ((Object[]) values[i]).length;
            }
            Object[] result = new Object[totalLength];
            int idx = 0;
            for (int i = 0; i < values.length; i++) {
                int nl = ((Object[]) values[i]).length;
                System.arraycopy(values[i], 0, result, idx, nl);
                idx += nl;
            }

            return result;
        }
    }

    /**
     * Flattens an array of arrays of keywords. Used for splatting in dicts and kwargs.
     */
    @Operation
    public static final class UnstarKw {
        @Specialization
        @ExplodeLoop
        public static PKeyword[] doUnstar(@Variadic Object[] values) {
            int totalLength = 0;
            for (int i = 0; i < values.length; i++) {
                totalLength += ((Object[]) values[i]).length;
            }
            PKeyword[] result = new PKeyword[totalLength];
            int idx = 0;
            for (int i = 0; i < values.length; i++) {
                int nl = ((Object[]) values[i]).length;
                System.arraycopy(values[i], 0, result, idx, nl);
                idx += nl;
            }

            return result;
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class IterToArray {
        @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
        @ExplodeLoop
        public static Object[] doUnpackSequence(VirtualFrame localFrame, PSequence sequence,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(sequence);
            int len = storage.length();

            Object[] result = new Object[len];

            for (int i = 0; i < len; i++) {
                result[i] = getItemNode.execute(storage, i);
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
                        @Bind("this") Node self,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, collection);
            } catch (PException e) {
                e.expectTypeError(self, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            ArrayList<Object> result = new ArrayList<>();
            while (true) {
                try {
                    Object item = getNextNode.execute(virtualFrame, iterator);
                    result.add(item);
                } catch (PException e) {
                    e.expectStopIteration(self, stopIterationProfile1);
                    return result.toArray();
                }
            }
        }
    }

    @Operation
    public static final class CallNilaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, @Cached CallNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ([] [])%n", callable);
            }
            return node.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Operation
    public static final class CallUnaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, @Cached CallUnaryMethodNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ([%s] [])%n", callable, arg0);
            }
            return node.executeObject(frame, callable, arg0);
        }
    }

    @Operation
    public static final class CallBinaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame, Object callable, Object arg0, Object arg1, @Cached CallBinaryMethodNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ([%s,%s] [])%n", callable, arg0, arg1);
            }
            return node.executeObject(frame, callable, arg0, arg1);
        }
    }

    @Operation
    public static final class CallTernaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2, @Cached CallTernaryMethodNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ([%s,%s,%s] [])%n", callable, arg0, arg1, arg2);
            }
            return node.execute(frame, callable, arg0, arg1, arg2);
        }
    }

    @Operation
    public static final class CallQuaternaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2, Object arg3, @Cached CallQuaternaryMethodNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ([%s,%s,%s,%s] [])%n", callable, arg0, arg1, arg2, arg3);
            }
            return node.execute(frame, callable, arg0, arg1, arg2, arg3);
        }
    }

    @Operation
    public static final class CallVarargsMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object[] args, PKeyword[] keywords, @Cached CallNode node) {
            if (LOG_CALLS) {
                System.err.printf(" [call] %s ( %s %s )%n", callable, Arrays.toString(args), Arrays.toString(keywords));
            }
            return node.execute(frame, callable, args, keywords);
        }
    }

    @Operation
    @ImportStatic({SpecialMethodSlot.class})
    public static final class ContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame, Object mgr, LocalSetter exit, LocalSetter value,
                        @Cached GetClassNode getClass,
                        @Cached("create(Enter)") LookupSpecialMethodSlotNode enterSpecialGetter,
                        @Cached("create(Exit)") LookupSpecialMethodSlotNode exitSpecialGetter,
                        @Cached CallUnaryMethodNode enterDispatch) {
            Object clazz = getClass.execute(mgr);
            exit.setObject(frame, exitSpecialGetter.execute(frame, clazz, mgr));
            Object enter = enterSpecialGetter.execute(frame, clazz, mgr);
            value.setObject(frame, enterDispatch.executeObject(enter, mgr));
        }
    }

    @Operation
    public static final class ContextManagerExit {
        @Specialization
        @InliningCutoff
        public static Object doExit(VirtualFrame frame,
                        Object exit, Object mgr, AbstractTruffleException ex,
                        @Cached CallQuaternaryMethodNode callNode,
                        @Cached GetClassNode getClass,
                        @Cached GetExceptionTracebackNode getTraceback) {
            Object clazz = getClass.execute(ex);
            Object traceback = getTraceback.execute(ex);
            return callNode.execute(frame, exit, mgr, clazz, ex, traceback);
        }
    }

    @Operation
    public static final class BuildString {
        @Specialization
        @ExplodeLoop
        public static Object doBuildString(@Variadic Object[] strings,
                        @Cached TruffleStringBuilder.AppendStringNode appendNode,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            int size = strings.length;
            CompilerAsserts.compilationConstant(size);

            TruffleStringBuilder tsb = TruffleStringBuilder.create(PythonUtils.TS_ENCODING);

            for (int i = 0; i < size; i++) {
                appendNode.execute(tsb, (TruffleString) strings[i]);
            }

            return toString.execute(tsb);
        }
    }

    @Operation
    @ImportStatic({SpecialMethodSlot.class})
    public static final class FormatValue {
        @Specialization
        public static Object format(VirtualFrame frame, Object value, Object formatSpec,
                        @Cached("create(Format)") LookupAndCallBinaryNode callFormat,
                        @Cached PRaiseNode raiseNode) {
            return FormatNode.format(frame, value, formatSpec, callFormat, raiseNode);
        }
    }

    @Operation
    public static final class ListExtend {
        @Specialization
        public static void listExtend(VirtualFrame frame, PList list, Object obj,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            ListExtendNode.extendSequence(frame, list, obj, lenNode, extendNode);
        }

        public static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }
    }

    @Operation
    public static final class TeeLocal {
        @Specialization
        public static int doInt(VirtualFrame frame, int value, LocalSetter local) {
            local.setInt(frame, value);
            return value;
        }

        @Specialization
        public static double doDouble(VirtualFrame frame, double value, LocalSetter local) {
            local.setDouble(frame, value);
            return value;
        }

        @Specialization
        public static long doLong(VirtualFrame frame, long value, LocalSetter local) {
            local.setLong(frame, value);
            return value;
        }

        @Specialization(replaces = {"doInt", "doDouble", "doLong"})
        public static Object doObject(VirtualFrame frame, Object value, LocalSetter local) {
            local.setObject(frame, value);
            return value;
        }
    }
}