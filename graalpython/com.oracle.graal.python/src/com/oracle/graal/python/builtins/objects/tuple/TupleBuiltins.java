/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsClinicProviders.IndexNodeClinicProviderGen;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTuple)
public class TupleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TupleBuiltinsFactory.getFactories();
    }

    // index(element)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value", "start", "stop"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0")
    @ArgumentClinic(name = "stop", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int index(VirtualFrame frame, PTuple self, Object value, int startIn, int endIn,
                        @Cached BranchProfile startLe0Profile,
                        @Cached BranchProfile endLe0Profile,
                        @Cached SequenceStorageNodes.ItemIndexNode itemIndexNode) {
            SequenceStorage storage = self.getSequenceStorage();
            int start = startIn;
            if (start < 0) {
                startLe0Profile.enter();
                start += storage.length();
                if (start < 0) {
                    start = 0;
                }
            }

            int end = endIn;
            if (end < 0) {
                endLe0Profile.enter();
                end += storage.length();
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

        @Specialization
        long count(VirtualFrame frame, PTuple self, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            long count = 0;
            SequenceStorage tupleStore = self.getSequenceStorage();
            for (int i = 0; i < tupleStore.length(); i++) {
                Object seqItem = getItemNode.execute(tupleStore, i);
                if (eqNode.execute(frame, seqItem, value)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(Object self,
                        @Cached PyTupleSizeNode pyTupleSizeNode) {
            return pyTupleSizeNode.execute(self);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Override
        public abstract TruffleString execute(VirtualFrame VirtualFrame, Object arg);

        private static final TruffleString NULL = tsLiteral("(null)");

        public static TruffleString toString(VirtualFrame frame, Object item, PyObjectReprAsTruffleStringNode reprNode) {
            if (item != null) {
                return reprNode.execute(frame, item);
            }
            return NULL;
        }

        @Specialization
        public static TruffleString repr(VirtualFrame frame, PTuple self,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage tupleStore = self.getSequenceStorage();
            int len = tupleStore.length();
            if (len == 0) {
                return T_EMPTY_PARENS;
            }
            if (!PythonContext.get(reprNode).reprEnter(self)) {
                return T_ELLIPSIS_IN_PARENS;
            }
            try {
                TruffleStringBuilder buf = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(buf, T_LPAREN);
                for (int i = 0; i < len - 1; i++) {
                    appendStringNode.execute(buf, toString(frame, getItemNode.execute(tupleStore, i), reprNode));
                    appendStringNode.execute(buf, T_COMMA_SPACE);
                }

                if (len > 0) {
                    appendStringNode.execute(buf, toString(frame, getItemNode.execute(tupleStore, len - 1), reprNode));
                }

                if (len == 1) {
                    appendStringNode.execute(buf, T_COMMA);
                }

                appendStringNode.execute(buf, T_RPAREN);
                return toStringNode.execute(buf);
            } finally {
                PythonContext.get(reprNode).reprLeave(self);
            }
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @ImportStatic({MathGuards.class, PGuards.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        public abstract Object execute(VirtualFrame frame, PTuple tuple, Object index);

        public abstract Object execute(VirtualFrame frame, PTuple tuple, int index);

        @Specialization
        static Object doInBounds(PTuple tuple, int index,
                        @Shared("getItem") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(tuple.getSequenceStorage(), index);
        }

        @InliningCutoff
        @Specialization(guards = "indexCheck.execute(key)", limit = "1")
        static Object doIndex(VirtualFrame frame, PTuple tuple, Object key,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheck,
                        @Shared("getItem") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, tuple.getSequenceStorage(), key);
        }

        @InliningCutoff
        @Specialization
        static Object doSlice(VirtualFrame frame, PTuple tuple, PSlice key,
                        @Shared("getItem") @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, tuple.getSequenceStorage(), key);
        }

        @InliningCutoff
        @Specialization
        static Object doNative(PythonNativeObject tuple, long key,
                        @Cached PCallCapiFunction callSetItem,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            return asPythonObjectNode.execute(callSetItem.call(NativeCAPISymbol.FUN_PY_TRUFFLE_TUPLE_GET_ITEM, toSulongNode.execute(tuple), key));
        }

        @SuppressWarnings("unused")
        @InliningCutoff
        @Fallback
        static Object doError(VirtualFrame frame, Object tuple, Object key,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "tuple", key);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
        @Specialization
        PTuple doPTuple(PTuple left, PTuple right,
                        @Cached("createConcat()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory().createTuple(concatenated);
        }

        @NeverDefault
        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(() -> SequenceStorageNodes.ListGeneralizationNode.create());
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "tuple", right, "tuple");
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        PTuple mul(VirtualFrame frame, PTuple left, Object right,
                        @Cached GetClassNode getClassNode,
                        @Cached ConditionProfile isSingleRepeat,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode) {
            int repeats = asSizeNode.executeExact(frame, right);
            if (isSingleRepeat.profile(PGuards.isPythonBuiltinClassType(getClassNode.execute(left)) && repeats == 1)) {
                return left;
            } else {
                return factory().createTuple(repeatNode.execute(frame, left.getSequenceStorage(), repeats));
            }
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, PTuple self, Object other,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, self.getSequenceStorage(), other);
        }

    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doPTuple(PTuple self) {
            return self.getSequenceStorage().length() != 0;
        }

        @Fallback
        Object toBoolean(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        PIntegerSequenceIterator doPListInt(PTuple primary) {
            return factory().createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isObjectStorage(primary)"})
        PObjectSequenceIterator doPListObject(PTuple primary) {
            return factory().createObjectSequenceIterator((ObjectSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        PLongSequenceIterator doPListLong(PTuple primary) {
            return factory().createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        PDoubleSequenceIterator doPListDouble(PTuple primary) {
            return factory().createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        PSequenceIterator doPList(PTuple primary) {
            return factory().createSequenceIterator(primary);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        public long getHash(PTuple self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"})
        public long computeHash(VirtualFrame frame, PTuple self,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyObjectHashNode hashNode) {
            // adapted from https://github.com/python/cpython/blob/v3.6.5/Objects/tupleobject.c#L345
            SequenceStorage tupleStore = self.getSequenceStorage();
            int len = tupleStore.length();
            long multiplier = 0xf4243;
            long hash = 0x345678;
            for (int i = 0; i < len; i++) {
                Object item = getItemNode.execute(tupleStore, i);
                long tmp = hashNode.execute(frame, item);
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

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetNewargsNode extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple doIt(PTuple self) {
            return factory().createTuple(new Object[]{factory().createTuple(self.getSequenceStorage())});
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object classGetItem(Object cls, Object key) {
            return factory().createGenericAlias(cls, key);
        }
    }
}
