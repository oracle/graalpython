package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.objects.bytes.BytesNodes.compareByteArrays;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyBytesCheckExactNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBytes)
public class BytesBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @SuppressWarnings("unused")
        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object byteDone(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isPSlice(key) || indexCheckNode.execute(this, key)", limit = "1")
        static Object doSlice(VirtualFrame frame, Object self, Object key,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, getBytesStorage.execute(inliningTarget, self), key);
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object doError(VirtualFrame frame, Object self, Object key,
                        @Cached PRaiseNode raiseNode) {
            return raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "byte", key);
        }

        @NeverDefault
        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(IndexNodes.NormalizeIndexNode.create(), (s, f) -> f.createBytes(s));
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static TruffleString repr(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage store = getBytesStorage.execute(inliningTarget, self);
            byte[] bytes = getBytes.execute(inliningTarget, store);
            int len = store.length();
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            BytesUtils.reprLoop(sb, bytes, len, appendCodePointNode);
            return toStringNode.execute(sb);
        }
    }

    // bytes.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BytesNodes.BaseTranslateNode {

        @Specialization(guards = {"isNoValue(delete)", "checkExactNode.execute(this, self)"})
        static PBytes translate(PBytes self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @SuppressWarnings("unused") @Shared @Cached PyBytesCheckExactNode checkExactNode) {
            return self;
        }

        @Specialization(guards = {"isNoValue(delete)", "!checkExactNode.execute(this, self)"})
        static PBytes translate(Object self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @SuppressWarnings("unused") @Shared @Cached PyBytesCheckExactNode checkExactNode,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(toBytesNode.execute(null, self));
        }

        @Specialization(guards = "!isNone(table)")
        static Object translate(VirtualFrame frame, Object self, Object table, @SuppressWarnings("unused") PNone delete,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyBytesCheckExactNode checkExactNode,
                        @Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(inliningTarget, bTable, isLenTable256Profile, raiseNode);
            byte[] bSelf = toBytesNode.execute(null, self);

            Result result = translate(bSelf, bTable);
            if (result.changed || !checkExactNode.execute(inliningTarget, self)) {
                return factory.createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = "isNone(table)")
        static Object delete(VirtualFrame frame, Object self, @SuppressWarnings("unused") PNone table, Object delete,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyBytesCheckExactNode checkExactNode,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] bSelf = toBytesNode.execute(null, self);
            byte[] bDelete = toBytesNode.execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            if (result.changed || !checkExactNode.execute(inliningTarget, self)) {
                return factory.createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        static Object translateAndDelete(VirtualFrame frame, Object self, Object table, Object delete,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyBytesCheckExactNode checkExactNode,
                        @Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(inliningTarget, bTable, isLenTable256Profile, raiseNode);
            byte[] bDelete = toBytesNode.execute(frame, delete);
            byte[] bSelf = toBytesNode.execute(null, self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            if (result.changed || !checkExactNode.execute(inliningTarget, self)) {
                return factory.createBytes(result.array);
            }
            return self;
        }
    }

    // bytes.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "string"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = "isBuiltinBytesType(inliningTarget, cls, isSameType)")
        static PBytes doBytes(Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached TypeNodes.IsSameTypeNode isSameType,
                        @Shared("hexToBytes") @Cached BytesNodes.HexStringToBytesNode hexStringToBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(cls, hexStringToBytesNode.execute(str));
        }

        @Specialization(guards = "!isBuiltinBytesType(inliningTarget, cls, isSameType)")
        static Object doGeneric(VirtualFrame frame, Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached TypeNodes.IsSameTypeNode isSameType,
                        @Cached CallNode callNode,
                        @Shared("hexToBytes") @Cached BytesNodes.HexStringToBytesNode hexStringToBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PBytes bytes = factory.createBytes(hexStringToBytesNode.execute(str));
            return callNode.execute(frame, cls, bytes);
        }

        protected static boolean isBuiltinBytesType(Node inliningTarget, Object cls, TypeNodes.IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PBytes, cls);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.FromHexNodeClinicProviderGen.INSTANCE;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    // N.B. bytes only allow comparing to bytes, bytearray has its own implementation that uses
    // buffer API
    abstract static class ComparisonHelperNode extends Node {

        abstract Object execute(Node inliningTarget, Object self, Object other, ComparisonOp op);

        @Specialization
        static boolean cmp(Node inliningTarget, PBytes self, PBytes other, ComparisonOp op,
                        @Exclusive @Cached SequenceStorageNodes.GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
        }

        @Fallback
        static Object cmp(Node inliningTarget, Object self, Object other, ComparisonOp op,
                        @SuppressWarnings("unused") @Cached PyBytesCheckNode check,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Exclusive @Cached SequenceStorageNodes.GetInternalByteArrayNode getArray,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (check.execute(inliningTarget, self)) {
                if (check.execute(inliningTarget, other)) {
                    SequenceStorage selfStorage = getBytesStorage.execute(inliningTarget, self);
                    SequenceStorage otherStorage = getBytesStorage.execute(inliningTarget, other);
                    return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
                } else {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.builtinName, J_BYTES, self);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.EQ);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.NE);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.LT);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.LE);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.GT);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(inliningTarget, self, other, ComparisonOp.GE);
        }
    }
}
