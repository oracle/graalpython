/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory.GetItemNodeFactory;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory.IndexNodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import com.oracle.graal.python.runtime.sequence.SequenceUtil.NormalizeIndexNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import java.math.BigInteger;

@CoreFunctions(extendClasses = PTuple.class)
public class TupleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return TupleBuiltinsFactory.getFactories();
    }

    // index(element)
    @Builtin(name = "index", minNumOfArguments = 2, maxNumOfArguments = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonBuiltinNode {

        private final static String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

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
            int len = tuple.len();
            for (int i = start; i < end && i < len; i++) {
                Object object = tuple.getItem(i);
                if (eqNode.executeBool(object, value)) {
                    return i;
                }
            }
            throw raise(PythonErrorType.ValueError, "tuple.index(x): x not in tuple");
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
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            long count = 0;
            for (int i = 0; i < self.len(); i++) {
                Object object = self.getItem(i);
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
        @Specialization
        public int len(PTuple self) {
            return self.len();
        }
    }

    @Builtin(name = SpecialMethodNames.__GETITEM__, fixedNumOfArguments = 2)
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBuiltinNode {

        private static final String TYPE_ERROR_MESSAGE = "tuple indices must be integers of slices, not %p";

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

        public abstract Object execute(PTuple tuple, Object index);

        @Specialization
        public Object doPTuple(PTuple tuple, PSlice slice) {
            return tuple.getSlice(factory(), slice);
        }

        @Specialization
        public Object doPTuple(PTuple tuple, long idx) {
            return tuple.getItemNormalized(normalize.forTuple(idx, tuple.len()));
        }

        @Specialization
        public Object doPTuple(@SuppressWarnings("unused") PTuple tuple, double idx) {
            throw raise(TypeError, TYPE_ERROR_MESSAGE, idx);
        }

        @Specialization(guards = {"!isPSlice(idx)", "!isNumber(idx)"})
        Object doObject(PTuple tuple, Object idx,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode indexNode,
                        @Cached("createGetItemNode()") GetItemNode getItemNode) {
            Object indexValue = indexNode.executeObject(idx);
            if (MathGuards.isNumber(indexValue) || isPSlice(idx)) {
                return getItemNode.execute(tuple, indexValue);
            }
            throw raise(TypeError, TYPE_ERROR_MESSAGE, idx);
        }

        protected GetItemNode createGetItemNode() {
            return GetItemNodeFactory.create(new PNode[0]);
        }

        protected boolean isPSlice(Object object) {
            return object instanceof PSlice;
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "left.len() == right.len()")
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            for (int i = 0; i < left.len(); i++) {
                Object oleft = left.getItem(i);
                Object oright = right.getItem(i);
                if (!eqNode.executeBool(oleft, oright)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = "left.len() != right.len()")
        @SuppressWarnings("unused")
        boolean doPTuple(PTuple left, PTuple right) {
            return false;
        }

        @Fallback
        @SuppressWarnings("unused")
        boolean doOther(Object left, Object right) {
            return false;
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "left.len() == right.len()")
        boolean doPTuple(PTuple left, PTuple right,
                        @Cached("create(__NE__, __NE__, __NE__)") BinaryComparisonNode neNode) {
            for (int i = 0; i < left.len(); i++) {
                Object oleft = left.getItem(i);
                Object oright = right.getItem(i);
                if (neNode.executeBool(oleft, oright)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = "left.len() != right.len()")
        @SuppressWarnings("unused")
        boolean doPTuple(PTuple left, PTuple right) {
            return true;
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
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__GE__, __LE__, __GE__)") BinaryComparisonNode geNode) {
            int llen = left.len();
            int rlen = right.len();
            int min = Math.min(llen, rlen);
            for (int i = 0; i < min; i++) {
                Object oleft = left.getItem(i);
                Object oright = right.getItem(i);
                if (!eqNode.executeBool(oleft, oright)) {
                    return geNode.executeBool(oleft, oright);
                }
            }
            return llen >= rlen;
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
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__LE__, __GE__, __LE__)") BinaryComparisonNode geNode) {
            int llen = left.len();
            int rlen = right.len();
            int min = Math.min(llen, rlen);
            for (int i = 0; i < min; i++) {
                Object oleft = left.getItem(i);
                Object oright = right.getItem(i);
                if (!eqNode.executeBool(oleft, oright)) {
                    return geNode.executeBool(oleft, oright);
                }
            }
            return llen <= rlen;
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
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__GT__, __LT__, __GT__)") BinaryComparisonNode gtNode) {
            int llen = left.len();
            int rlen = right.len();
            int min = Math.min(llen, rlen);
            for (int i = 0; i < min; i++) {
                Object oleft = left.getItem(i);
                Object oright = right.getItem(i);
                if (!eqNode.executeBool(oleft, oright)) {
                    return gtNode.executeBool(oleft, oright);
                }
            }
            return llen > rlen;
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
        boolean doPTuple(PTuple self, PTuple other,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__LT__, __GT__, __LT__)") BinaryComparisonNode ltNode) {
            int len = self.len();
            int len2 = other.len();
            int min = Math.min(len, len2);
            for (int i = 0; i < min; i++) {
                Object left = self.getItem(i);
                Object right = other.getItem(i);
                if (!eqNode.executeBool(left, right)) {
                    return ltNode.executeBool(left, right);
                }
            }
            return len < len2;
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
        PTuple doPTuple(PTuple left, PTuple right) {
            // TODO, there should be better specialization, which will check,
            // whether there is enough free memory. If not, then fire OOM.
            // The reason is that GC is trying to find out enough space for arrays
            // that can fit to the free memory, but at the end there is no conti-
            // nual space for such big array and this can takes looong time (a few mimutes).
            Object[] rightArray = right.getArray();
            Object[] leftArray = left.getArray();
            try {
                int resultLength = Math.addExact(leftArray.length, rightArray.length);
                Object[] newArray = new Object[resultLength];
                System.arraycopy(leftArray, 0, newArray, 0, leftArray.length);
                System.arraycopy(rightArray, 0, newArray, leftArray.length, rightArray.length);
                return factory().createTuple(newArray);
            } catch (OutOfMemoryError | ArithmeticException e) {
                throw raise(MemoryError);
            }
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
        PTuple doPTupleInt(PTuple left, boolean right) {
            return right ? left : factory().createEmptyTuple();
        }

        @Specialization(rewriteOn = {ArithmeticException.class, OutOfMemoryError.class})
        PTuple doPTupleInt(PTuple left, int right) throws OutOfMemoryError {
            if (right > 0) {
                Object[] objects = new Object[Math.multiplyExact(left.len(), right)];
                for (int i = 0; i < objects.length; i++) {
                    objects[i] = left.getItem(i % left.len());
                }
                return factory().createTuple(objects);
            }
            return factory().createEmptyTuple();
        }

        @Specialization
        PTuple doPTupleIntGeneric(PTuple left, int right) {
            try {
                return doPTupleInt(left, right);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        @Specialization
        PTuple doPTupleIntGeneric(PTuple left, long right) {
            try {
                return doPTupleInt(left, (int) right);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        protected boolean isBool(Object o) {
            return o instanceof Boolean;
        }

        @Specialization(guards = {"!isInteger(right)", "!isBool(right)"})
        PTuple doPTupleInt(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", right);
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
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            int len = self.len();
            for (int i = 0; i < len; i++) {
                Object object = self.getItem(i);
                if (eqNode.executeBool(object, other)) {
                    return true;
                }
            }
            return false;
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