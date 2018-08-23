/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory.IndexNodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTuple)
public class TupleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TupleBuiltinsFactory.getFactories();
    }

    // index(element)
    @Builtin(name = "index", minNumOfArguments = 2, maxNumOfArguments = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonBuiltinNode {

        private final static String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        public abstract int execute(Object arg1, Object arg2, Object arg3, Object arg4);

        private static int correctIndex(PTuple tuple, long index) {
            long resultIndex = index;
            if (resultIndex < 0) {
                resultIndex += tuple.len();
                if (resultIndex < 0) {
                    return 0;
                }
            }
            return (int) Math.min(resultIndex, Integer.MAX_VALUE);
        }

        @TruffleBoundary
        private static int correctIndex(PTuple tuple, PInt index) {
            BigInteger value = index.getValue();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                BigInteger resultAdd = value.add(BigInteger.valueOf(tuple.len()));
                if (resultAdd.compareTo(BigInteger.ZERO) < 0) {
                    return 0;
                }
                return resultAdd.intValue();
            }
            return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        private int findIndex(PTuple tuple, Object value, int start, int end, BinaryComparisonNode eqNode) {
            SequenceStorage tupleStore = tuple.getSequenceStorage();
            int len = tupleStore.length();
            for (int i = start; i < end && i < len; i++) {
                Object object = getGetItemNode().execute(tupleStore, i);
                if (eqNode.executeBool(object, value)) {
                    return i;
                }
            }
            throw raise(PythonErrorType.ValueError, "tuple.index(x): x not in tuple");
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.createNotNormalized());
            }
            return getItemNode;
        }

        @Specialization
        int index(PTuple self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, 0, self.len(), eqNode);
        }

        @Specialization
        int index(PTuple self, Object value, long start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int index(PTuple self, Object value, long start, long end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPI(PTuple self, Object value, PInt start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int indexPIPI(PTuple self, Object value, PInt start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexLPI(PTuple self, Object value, long start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPIL(PTuple self, Object value, PInt start, Long end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexDO(PTuple self, Object value, double start, Object end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexOD(PTuple self, Object value, Object start, double end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization(guards = "!isNumber(start)")
        int indexO(PTuple self, Object value, Object start, PNone end,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode startNode,
                        @Cached("createIndexNode()") IndexNode indexNode) {

            Object startValue = startNode.executeObject(start);
            if (PNone.NO_VALUE == startValue || !MathGuards.isNumber(startValue)) {
                throw raise(TypeError, ERROR_TYPE_MESSAGE);
            }
            return indexNode.execute(self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)",})
        int indexLO(PTuple self, Object value, long start, Object end,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode endNode,
                        @Cached("createIndexNode()") IndexNode indexNode) {

            Object endValue = endNode.executeObject(end);
            if (PNone.NO_VALUE == endValue || !MathGuards.isNumber(endValue)) {
                throw raise(TypeError, ERROR_TYPE_MESSAGE);
            }
            return indexNode.execute(self, value, start, endValue);
        }

        @Specialization(guards = {"!isNumber(start) || !isNumber(end)",})
        int indexOO(PTuple self, Object value, Object start, Object end,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode startNode,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode endNode,
                        @Cached("createIndexNode()") IndexNode indexNode) {

            Object startValue = startNode.executeObject(start);
            if (PNone.NO_VALUE == startValue || !MathGuards.isNumber(startValue)) {
                throw raise(TypeError, ERROR_TYPE_MESSAGE);
            }
            Object endValue = endNode.executeObject(end);
            if (PNone.NO_VALUE == endValue || !MathGuards.isNumber(endValue)) {
                throw raise(TypeError, ERROR_TYPE_MESSAGE);
            }
            return indexNode.execute(self, value, startValue, endValue);
        }

        protected IndexNode createIndexNode() {
            return IndexNodeFactory.create(new PNode[0]);
        }

    }

    @Builtin(name = "count", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonBuiltinNode {

        @Specialization
        long count(PTuple self, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            long count = 0;
            SequenceStorage tupleStore = self.getSequenceStorage();
            for (int i = 0; i < tupleStore.length(); i++) {
                Object object = getItemNode.execute(tupleStore, i);
                if (eqNode.executeBool(object, value)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Builtin(name = SpecialMethodNames.__LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "cachedClass == self.getSequenceStorage().getClass()", limit = "2")
        public int len(PTuple self,
                        @Cached("self.getSequenceStorage().getClass()") Class<? extends SequenceStorage> cachedClass) {
            return CompilerDirectives.castExact(self.getSequenceStorage(), cachedClass).length();
        }

        @Specialization(replaces = "len")
        public int lenGeneric(PTuple self) {
            return self.len();
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        public String toString(Object item, BuiltinFunctions.ReprNode reprNode) {
            if (item != null) {
                Object value = reprNode.execute(item);
                if (value instanceof String) {
                    return (String) value;
                } else if (value instanceof PString) {
                    return ((PString) value).getValue();
                }
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("should not reach");
            }
            return "(null)";
        }

        @Specialization
        @TruffleBoundary
        public String repr(PTuple self,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createRepr()") BuiltinFunctions.ReprNode reprNode) {
            SequenceStorage tupleStore = self.getSequenceStorage();
            int len = tupleStore.length();
            StringBuilder buf = new StringBuilder();
            append(buf, "(");
            for (int i = 0; i < len - 1; i++) {
                append(buf, toString(getItemNode.execute(tupleStore, i), reprNode));
                append(buf, ", ");
            }

            if (len > 0) {
                append(buf, toString(getItemNode.execute(tupleStore, len - 1), reprNode));
            }

            if (len == 1) {
                append(buf, ",");
            }

            append(buf, ")");
            return toString(buf);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static void append(StringBuilder sb, String s) {
            sb.append(s);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static String toString(StringBuilder sb) {
            return sb.toString();
        }

        protected static BuiltinFunctions.ReprNode createRepr() {
            return BuiltinFunctionsFactory.ReprNodeFactory.create();
        }
    }

    @Builtin(name = SpecialMethodNames.__GETITEM__, fixedNumOfArguments = 2)
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        private static final String TYPE_ERROR_MESSAGE = "tuple indices must be integers or slices, not %p";

        public abstract Object execute(PTuple tuple, Object index);

        @Specialization(guards = "!isPSlice(key)")
        public Object doPTuple(PTuple tuple, Object key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(tuple.getSequenceStorage(), key);
        }

        @Specialization
        public Object doPTuple(PTuple tuple, PSlice key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(tuple.getSequenceStorage(), key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItemNode() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forTuple(), TYPE_ERROR_MESSAGE, (s, f) -> f.createTuple(s));
        }

        protected boolean isPSlice(Object object) {
            return object instanceof PSlice;
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        boolean doOther(Object left, Object right) {
            return true;
        }
    }

    @Builtin(name = SpecialMethodNames.__GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = SpecialMethodNames.__LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = SpecialMethodNames.__GT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
        @Specialization
        PTuple doPTuple(PTuple left, PTuple right,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory().createTuple(concatenated);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only concatenate tuple (not \"%p\") to tuple", right);
        }
    }

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBuiltinNode {
        @Specialization
        PTuple mul(PTuple left, Object right,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            return factory().createTuple(repeatNode.execute(left.getSequenceStorage(), right));
        }
    }

    @Builtin(name = SpecialMethodNames.__RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = SpecialMethodNames.__CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PTuple self, Object other,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(self.getSequenceStorage(), other);
        }

    }

    @Builtin(name = SpecialMethodNames.__BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doPTuple(PTuple self) {
            return self.len() != 0;
        }

        @Fallback
        Object toBoolean(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PSequenceIterator doPTuple(PTuple self) {
            return factory().createSequenceIterator(self);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
