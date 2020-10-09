/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsClinicProviders.IndexNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory.SliceIndexNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTuple)
public class TupleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TupleBuiltinsFactory.getFactories();
    }

    // CPython narrows the result down to long range, but in Java we can use only int range for
    // array indices
    public abstract static class SliceIndexNode extends ArgumentCastNodeWithRaise {
        private final int defaultValue;

        protected SliceIndexNode(int defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        int handleNone(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        static int doInt(int i) {
            // fast-path for the most common case
            return i;
        }

        @Specialization(guards = "!isNoValue(value)", limit = "3")
        int doOthers(VirtualFrame frame, Object value,
                        @Cached CastToJavaIntLossyNode castToInt,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            if (lib.canBeIndex(value)) {
                return castToInt.execute(lib.asIndexWithFrame(value, frame));
            }
            throw raise(TypeError, ErrorMessages.SLICE_INDICES_TYPE_ERROR);
        }
    }

    // index(element)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value", "start", "stop"})
    @ArgumentClinic(name = "start", customConversion = "createSliceIndexStart", shortCircuitPrimitive = PrimitiveType.Int)
    @ArgumentClinic(name = "stop", customConversion = "createSliceIndexStop", shortCircuitPrimitive = PrimitiveType.Int)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IndexNodeClinicProviderGen.INSTANCE;
        }

        public static SliceIndexNode createSliceIndexStart() {
            return SliceIndexNodeGen.create(0);
        }

        public static SliceIndexNode createSliceIndexStop() {
            return SliceIndexNodeGen.create(Integer.MAX_VALUE);
        }

        @Specialization
        int index(VirtualFrame frame, PTuple self, Object value, int startIn, int endIn,
                        @Cached BranchProfile startLe0Profile,
                        @Cached BranchProfile endLe0Profile,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage storage = self.getSequenceStorage();
            int start = startIn;
            if (start < 0) {
                startLe0Profile.enter();
                start += lenNode.execute(storage);
                if (start < 0) {
                    start = 0;
                }
            }

            int end = endIn;
            if (end < 0) {
                endLe0Profile.enter();
                end += lenNode.execute(storage);
            }

            // Note: ItemIndexNode normalizes the end to min(end, length(storage))
            int idx = itemIndexNode.execute(frame, storage, value, start, end);
            if (idx != -1) {
                return idx;
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_TUPLE);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonBuiltinNode {

        @Specialization(limit = "5")
        long count(VirtualFrame frame, PTuple self, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @CachedLibrary("value") PythonObjectLibrary valueLib,
                        @CachedLibrary(limit = "16") PythonObjectLibrary otherLib) {
            long count = 0;
            SequenceStorage tupleStore = self.getSequenceStorage();
            for (int i = 0; i < tupleStore.length(); i++) {
                Object object = getItemNode.execute(frame, tupleStore, i);
                if (valueLib.equals(value, object, otherLib)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int doManaged(PTuple self,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage());
        }

        @Specialization
        public int doNative(PythonNativeObject self,
                        @Cached PCallCapiFunction callSizeNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached CastToJavaIntExactNode castToInt) {
            return castToInt.execute(lib.asJavaLong(callSizeNode.call(NativeCAPISymbols.FUN_PY_TRUFFLE_OBJECT_SIZE, toSulongNode.execute(self))));
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        public String toString(VirtualFrame frame, Object item, BuiltinFunctions.ReprNode reprNode) {
            if (item != null) {
                Object value = reprNode.call(frame, item);
                if (value instanceof String) {
                    return (String) value;
                } else if (value instanceof PString) {
                    return ((PString) value).getValue();
                }
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("should not reach");
            }
            return "(null)";
        }

        @Specialization
        public String repr(VirtualFrame frame, PTuple self,
                        @Cached("create()") SequenceStorageNodes.LenNode getLen,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createRepr()") BuiltinFunctions.ReprNode reprNode) {
            SequenceStorage tupleStore = self.getSequenceStorage();
            int len = getLen.execute(tupleStore);
            StringBuilder buf = PythonUtils.newStringBuilder();
            PythonUtils.append(buf, "(");
            for (int i = 0; i < len - 1; i++) {
                PythonUtils.append(buf, toString(frame, getItemNode.execute(frame, tupleStore, i), reprNode));
                PythonUtils.append(buf, ", ");
            }

            if (len > 0) {
                PythonUtils.append(buf, toString(frame, getItemNode.execute(frame, tupleStore, len - 1), reprNode));
            }

            if (len == 1) {
                PythonUtils.append(buf, ",");
            }

            PythonUtils.append(buf, ")");
            return PythonUtils.sbToString(buf);
        }

        protected static BuiltinFunctions.ReprNode createRepr() {
            return BuiltinFunctionsFactory.ReprNodeFactory.create();
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @ImportStatic({MathGuards.class, PGuards.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        private static final String TYPE_ERROR_MESSAGE = "tuple " + ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES;

        public abstract Object execute(VirtualFrame frame, PTuple tuple, Object index);

        @Specialization(guards = "!isPSlice(key)")
        Object doPTuple(VirtualFrame frame, PTuple tuple, Object key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, tuple.getSequenceStorage(), key);
        }

        @Specialization
        Object doPTuple(VirtualFrame frame, PTuple tuple, PSlice key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, tuple.getSequenceStorage(), key);
        }

        @Specialization
        Object doNative(PythonNativeObject tuple, long key,
                        @Cached PCallCapiFunction callSetItem,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            return asPythonObjectNode.execute(callSetItem.call(NativeCAPISymbols.FUN_PY_TRUFFLE_TUPLE_GET_ITEM, toSulongNode.execute(tuple), key));
        }

        protected static SequenceStorageNodes.GetItemNode createGetItemNode() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forTuple(), TYPE_ERROR_MESSAGE, (s, f) -> f.createTuple(s));
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPTuple(VirtualFrame frame, PTuple left, PTuple right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
        @Specialization
        PTuple doPTuple(PTuple left, PTuple right,
                        @Cached("createConcat()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory().createTuple(concatenated);
        }

        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(() -> SequenceStorageNodes.ListGeneralizationNode.create());
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "tuple", right, "tuple");
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        protected static boolean isSingleRepeat(PTuple left, PythonObjectLibrary tuplelib, Object right, PythonObjectLibrary lib) {
            return PGuards.isPythonBuiltinClassType(tuplelib.getLazyPythonClass(left)) && lib.canBeIndex(right) && lib.asSize(right) == 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isSingleRepeat(left, tuplelib, right, lib)")
        PTuple doPTupleSingleRepeat(VirtualFrame frame, PTuple left, Object right,
                        @CachedLibrary(limit = "3") PythonObjectLibrary tuplelib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return left;
        }

        @Specialization(guards = "!isSingleRepeat(left, tuplelib, right, lib)")
        PTuple mul(VirtualFrame frame, PTuple left, Object right,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary tuplelib,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return factory().createTuple(repeatNode.execute(frame, left.getSequenceStorage(), right));
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, PTuple self, Object other,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, self.getSequenceStorage(), other);
        }

    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doPTuple(PTuple self,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage()) != 0;
        }

        @Fallback
        Object toBoolean(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doPTuple(PTuple self) {
            return factory().createSequenceIterator(self);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        public long getHash(PTuple self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"})
        public long computeHash(VirtualFrame frame, PTuple self,
                        @Cached SequenceStorageNodes.LenNode getLen,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            // adapted from https://github.com/python/cpython/blob/v3.6.5/Objects/tupleobject.c#L345
            SequenceStorage tupleStore = self.getSequenceStorage();
            int len = getLen.execute(tupleStore);
            long multiplier = 0xf4243;
            long hash = 0x345678;
            for (int i = 0; i < len; i++) {
                Object item = getItemNode.execute(frame, tupleStore, i);
                long tmp = lib.hashWithFrame(item, frame);
                hash = (hash ^ tmp) * multiplier;
                multiplier += 82520 + len + len;
            }

            hash += 97531;

            if (hash == Long.MAX_VALUE) {
                hash = -2;
            }

            self.setHash(hash);
            return hash;
        }

        @Fallback
        Object genericHash(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
