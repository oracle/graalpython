/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.list;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil.NormalizeIndexNode;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.SetSequenceStorageItem;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import java.math.BigInteger;

@CoreFunctions(extendClasses = PList.class)
public class ListBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return ListBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public Object repr(Object self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            StringBuilder result = new StringBuilder("[");
            Object iterator = getIterator.executeWith(self);
            boolean initial = true;
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return result.append(']').toString();
                }
                Object reprString = repr.executeObject(value);
                if (reprString instanceof PString) {
                    reprString = ((PString) reprString).getValue();
                }
                if (reprString instanceof String) {
                    if (initial) {
                        initial = false;
                    } else {
                        result.append(", ");
                    }
                    result.append((String) reprString);
                } else {
                    raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", reprString);
                }
            }
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

        @Specialization(guards = "isIntStorage(primary)")
        protected int doPListInt(PList primary, int idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            return storage.getIntItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected long doPListLong(PList primary, int idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            return storage.getLongItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected double doPListDouble(PList primary, int idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            return storage.getDoubleItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected Object doPListObject(PList primary, int idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization
        protected Object doPList(PList list, int idx) {
            SequenceStorage storage = list.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected int doPListInt(PList primary, long idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            return storage.getIntItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected long doPListLong(PList primary, long idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            return storage.getLongItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected double doPListDouble(PList primary, long idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            return storage.getDoubleItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected Object doPListObject(PList primary, long idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization
        protected Object doPList(PList list, long idx) {
            SequenceStorage storage = list.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected int doPListInt(PList primary, PInt idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            return storage.getIntItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected long doPListLong(PList primary, PInt idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            return storage.getLongItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected double doPListDouble(PList primary, PInt idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            return storage.getDoubleItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected Object doPListObject(PList primary, PInt idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization
        protected Object doPList(PList list, PInt idx) {
            SequenceStorage storage = list.getSequenceStorage();
            return storage.getItemNormalized(normalize.forList(idx, storage.length()));
        }

        @Specialization
        protected Object doPListSlice(PList self, PSlice slice) {
            return self.getSlice(factory(), slice);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValidIndexType(idx)")
        protected Object doGeneric(PList self, Object idx) {
            throw raise(PythonErrorType.TypeError, "list indices must be integers or slices, not %p", idx);
        }

        protected boolean isValidIndexType(Object idx) {
            return PGuards.isInteger(idx) || idx instanceof PSlice;
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

        @Specialization
        public Object doPList(PList primary, PSlice slice, PSequence value) {
            primary.setSlice(slice, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        public Object doPListInt(PList primary, int idx, int value) {
            IntSequenceStorage store = (IntSequenceStorage) primary.getSequenceStorage();
            store.setIntItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        public Object doPListDouble(PList primary, int idx, double value) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) primary.getSequenceStorage();
            store.setDoubleItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        public Object doPListObject(PList primary, int idx, Object value) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) primary.getSequenceStorage();
            store.setItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization
        public Object doPList(PList list, int idx, Object value,
                        @Cached("create()") SetSequenceStorageItem setItem) {
            setItem.setItem(list, idx, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        public Object doPListInt(PList primary, long idx, int value) {
            IntSequenceStorage store = (IntSequenceStorage) primary.getSequenceStorage();
            store.setIntItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        public Object doPListDouble(PList primary, long idx, double value) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) primary.getSequenceStorage();
            store.setDoubleItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        public Object doPListObject(PList primary, long idx, Object value) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) primary.getSequenceStorage();
            store.setItemNormalized(normalize.forListAssign(idx, store.length()), value);
            return PNone.NONE;
        }

        @Specialization
        public Object doPList(PList list, long idx, Object value,
                        @Cached("create()") SetSequenceStorageItem setItem) {
            setItem.setItem(list, idx, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValidIndexType(idx)")
        protected Object doGeneric(PList self, Object idx, Object value) {
            throw raise(PythonErrorType.TypeError, "list indices must be integers or slices, not %p", idx);
        }

        protected boolean isValidIndexType(Object idx) {
            return PGuards.isInteger(idx) || idx instanceof PSlice;
        }
    }

    // list.append(x)
    @Builtin(name = "append", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBuiltinNode {

        @Specialization(guards = "isEmptyStorage(list)")
        public PList appendEmpty(PList list, Object arg) {
            list.append(arg);
            return list;
        }

        @Specialization(guards = "isIntStorage(list)")
        public PList appendInt(PList list, int arg) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            store.appendInt(arg);
            return list;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PList appendLong(PList list, long arg) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            store.appendLong(arg);
            return list;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PList appendDouble(PList list, double arg) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            store.appendDouble(arg);
            return list;
        }

        @Specialization(guards = "isListStorage(list)")
        public PList appendList(PList list, PList arg) {
            ListSequenceStorage store = (ListSequenceStorage) list.getSequenceStorage();
            store.appendList(arg);
            return list;
        }

        @Specialization(guards = "isTupleStorage(list)")
        public PList appendTuple(PList list, PTuple arg) {
            TupleSequenceStorage store = (TupleSequenceStorage) list.getSequenceStorage();
            store.appendPTuple(arg);
            return list;
        }

        @Specialization(rewriteOn = {SequenceStoreException.class})
        public PList appendObject(PList list, Object arg) throws SequenceStoreException {
            list.getSequenceStorage().append(arg);
            return list;
        }

        @Specialization()
        public PList appendObjectGeneric(PList list, Object arg) {
            list.append(arg);
            return list;
        }
    }

    // list.extend(L)
    @Builtin(name = "extend", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBuiltinNode {

        @Specialization
        public PList extend(PList list, Object source,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {

            Object iterator = getIterator.executeWith(source);
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return list;
                }
                list.append(value);
            }
        }
    }

    // list.insert(i, x)
    @Builtin(name = "insert", fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonBuiltinNode {

        @Specialization
        public PList insert(PList list, int index, Object value) {
            list.insert(index, value);
            return list;
        }

        @Specialization
        @SuppressWarnings("unused")
        public PList insert(PList list, Object i, Object arg1) {
            throw new RuntimeException("invalid arguments for insert()");
        }
    }

    // list.remove(x)
    @Builtin(name = "remove", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBuiltinNode {

        @Specialization
        public PList remove(PList list, Object arg) {
            int index = list.index(arg);
            if (index >= 0) {
                list.delItem(index);
                return list;
            } else {
                throw raise(PythonErrorType.ValueError, "list.remove(x): x not in list");
            }
        }
    }

    // list.pop([i])
    @Builtin(name = "pop", minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListPopNode extends PythonBuiltinNode {

        @Specialization(guards = "isIntStorage(list)")
        public int popInt(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            return store.popInt();
        }

        @Specialization(guards = "isLongStorage(list)")
        public long popLong(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            return store.popLong();
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public double popDouble(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            return store.popDouble();
        }

        @Specialization(guards = "isObjectStorage(list)")
        public Object popObject(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            ObjectSequenceStorage store = (ObjectSequenceStorage) list.getSequenceStorage();
            return store.popObject();
        }

        @Specialization
        public Object popLast(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            Object ret = list.getItem(list.len() - 1);
            list.delItem(list.len() - 1);
            return ret;
        }

        @Specialization
        public Object pop(PList list, boolean bindex,
                        @Cached("createBinaryProfile()") ConditionProfile isOutOfRange) {
            int index = bindex ? 1 : 0;
            return popOnIndex(list, index, isOutOfRange);
        }

        @Specialization
        public Object pop(PList list, int index,
                        @Cached("createBinaryProfile()") ConditionProfile isOutOfRange) {
            return popOnIndex(list, index, isOutOfRange);
        }

        @Specialization
        @SuppressWarnings("unused")
        public Object pop(PList list, long arg) {
            raiseIndexError(true);
            return null;
        }

        @Specialization
        @SuppressWarnings("unused")
        public Object pop(PList list, Object arg) {
            throw raise(TypeError, "integer argument expected, got %p", arg);
        }

        protected void raiseIndexError(boolean con) {
            if (con) {
                throw raise(PythonErrorType.IndexError, "pop index out of range");
            }
        }

        private Object popOnIndex(PList list, int index, ConditionProfile cp) {
            int len = list.len();
            if (cp.profile((index < 0 && (index + len) < 0) || index >= len)) {
                throw raise(PythonErrorType.IndexError, "pop index out of range");
            }
            Object ret = list.getItem(index);
            list.delItem(index);
            return ret;
        }
    }

    // list.index(x)
    @Builtin(name = "index", minNumOfArguments = 2, maxNumOfArguments = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class ListIndexNode extends PythonBuiltinNode {
        private final static String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

        public abstract int execute(Object arg1, Object arg2, Object arg3, Object arg4);

        private static int correctIndex(PList list, long index) {
            long resultIndex = index;
            if (resultIndex < 0) {
                resultIndex += list.len();
                if (resultIndex < 0) {
                    return 0;
                }
            }
            return (int) Math.min(resultIndex, Integer.MAX_VALUE);
        }

        @TruffleBoundary
        private static int correctIndex(PList list, PInt index) {
            BigInteger value = index.getValue();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                BigInteger resultAdd = value.add(BigInteger.valueOf(list.len()));
                if (resultAdd.compareTo(BigInteger.ZERO) < 0) {
                    return 0;
                }
                return resultAdd.intValue();
            }
            return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        private int findIndex(PList list, Object value, int start, int end, BinaryComparisonNode eqNode) {
            int len = list.len();
            for (int i = start; i < end && i < len; i++) {
                Object object = list.getItem(i);
                if (eqNode.executeBool(object, value)) {
                    return i;
                }
            }
            throw raise(PythonErrorType.ValueError, "x not in list");
        }

        @Specialization
        int index(PList self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, 0, self.len(), eqNode);
        }

        @Specialization
        int index(PList self, Object value, long start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int index(PList self, Object value, long start, long end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPI(PList self, Object value, PInt start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int indexPIPI(PList self, Object value, PInt start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexLPI(PList self, Object value, long start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPIL(PList self, Object value, PInt start, Long end,
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
                        @Cached("createIndexNode()") ListIndexNode indexNode) {

            Object startValue = startNode.executeObject(start);
            if (PNone.NO_VALUE == startValue || !MathGuards.isNumber(startValue)) {
                throw raise(TypeError, ERROR_TYPE_MESSAGE);
            }
            return indexNode.execute(self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)",})
        int indexLO(PTuple self, Object value, long start, Object end,
                        @Cached("create(__INDEX__)") LookupAndCallUnaryNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {

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
                        @Cached("createIndexNode()") ListIndexNode indexNode) {

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

        protected ListIndexNode createIndexNode() {
            return ListBuiltinsFactory.ListIndexNodeFactory.create(new PNode[0]);
        }

    }

    // list.count(x)
    @Builtin(name = "count", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListCountNode extends PythonBuiltinNode {

        @Specialization
        long count(PList self, Object value,
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

    // list.reverse()
    @Builtin(name = "reverse", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ListReverseNode extends PythonBuiltinNode {

        @Specialization
        public PList reverse(PList list) {
            list.reverse();
            return list;
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ListIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PList list) {
            return factory().createSequenceIterator(list);
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isEmptyStorage(list)")
        public int lenPListEmpty(@SuppressWarnings("unused") PList list) {
            return 0;
        }

        @Specialization(guards = "isIntStorage(list)")
        public int lenPListInt(PList list) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isLongStorage(list)")
        public int lenPListLong(PList list) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public int lenPListDouble(PList list) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isObjectStorage(list)")
        public int lenPListObject(PList list) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) list.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isBasicStorage(list)")
        public int lenPList(PList list) {
            BasicSequenceStorage store = (BasicSequenceStorage) list.getSequenceStorage();
            return store.length();
        }

        @Specialization
        public int len(PList self) {
            return self.len();
        }
    }

    @Builtin(name = SpecialMethodNames.__ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
        @Specialization(guards = "areBothIntStorage(left,right)")
        PList doPListInt(PList left, PList right) {
            IntSequenceStorage leftStore = (IntSequenceStorage) left.getSequenceStorage().copy();
            IntSequenceStorage rightStore = (IntSequenceStorage) right.getSequenceStorage();
            leftStore.extendWithIntStorage(rightStore);
            return factory().createList(leftStore);
        }

        @Specialization(guards = "areBothObjectStorage(left,right)")
        PList doPListObject(PList left, PList right) {
            try {
                ObjectSequenceStorage leftStore = (ObjectSequenceStorage) left.getSequenceStorage().copy();
                ObjectSequenceStorage rightStore = (ObjectSequenceStorage) right.getSequenceStorage();
                leftStore.extend(rightStore);
                return factory().createList(leftStore);
            } catch (ArithmeticException e) {
                throw raise(MemoryError);
            }
        }

        @Specialization
        PList doPList(PList left, PList right) {
            try {
                return left.__add__(right);
            } catch (ArithmeticException e) {
                throw raise(MemoryError);
            }
        }

        protected boolean isList(Object o) {
            return o instanceof PList;
        }

        @Specialization(guards = "!isList(right)")
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only concatenate list (not \"%p\") to list", right);
        }
    }

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBuiltinNode {
        @Specialization
        PList doPListInt(PList left, boolean right,
                        @Cached("createClassProfile()") ValueProfile profile) {
            return doPListInt(left, PInt.intValue(right), profile);
        }

        @Specialization
        PList doPListInt(PList left, int right,
                        @Cached("createClassProfile()") ValueProfile profile) {
            try {
                return right > 0 ? left.__mul__(profile, right) : factory().createList();
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PList doPListBigInt(PList left, PInt right,
                        @Cached("createClassProfile()") ValueProfile profile) {
            try {
                return doPListInt(left, right.intValueExact(), profile);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(OverflowError, "cannot fit 'int' into an index-sized integer");
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBuiltinNode {
        protected abstract boolean executeWith(Object left, Object right);

        @Specialization(guards = "areBothIntStorage(left,right)")
        boolean doPListInt(PList left, PList right) {
            IntSequenceStorage leftStore = (IntSequenceStorage) left.getSequenceStorage();
            IntSequenceStorage rightStore = (IntSequenceStorage) right.getSequenceStorage();
            return leftStore.equals(rightStore);
        }

        @Specialization(guards = "areBothLongStorage(left,right)")
        boolean doPListLong(PList left, PList right) {
            LongSequenceStorage leftStore = (LongSequenceStorage) left.getSequenceStorage();
            LongSequenceStorage rightStore = (LongSequenceStorage) right.getSequenceStorage();
            return leftStore.equals(rightStore);
        }

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("create(__EQ__, __EQ__)") LookupAndCallBinaryNode equalNode) {
            if (left.len() == right.len()) {
                for (int i = 0; i < left.len(); i++) {
                    if (equalNode.executeObject(left.getItem(i), right.getItem(i)) != Boolean.TRUE) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBuiltinNode {
        @Specialization(guards = "areBothIntStorage(left,right)")
        boolean doPListInt(PList left, PList right) {
            IntSequenceStorage leftStore = (IntSequenceStorage) left.getSequenceStorage();
            IntSequenceStorage rightStore = (IntSequenceStorage) right.getSequenceStorage();
            return !leftStore.equals(rightStore);
        }

        protected abstract boolean executeWith(Object left, Object right);

        @Specialization(guards = "areBothObjectStorage(left,right)")
        boolean doPListObject(PList left, PList right) {
            ObjectSequenceStorage leftStore = (ObjectSequenceStorage) left.getSequenceStorage();
            ObjectSequenceStorage rightStore = (ObjectSequenceStorage) right.getSequenceStorage();
            return !leftStore.equals(rightStore);
        }

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("create(__EQ__, __EQ__)") LookupAndCallBinaryNode equalNode) {
            if (left.len() == right.len()) {
                for (int i = 0; i < left.len(); i++) {
                    if (equalNode.executeObject(left.getItem(i), right.getItem(i)) != Boolean.TRUE) {
                        return true;
                    }
                }
                return false;
            } else {
                return true;
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean contains(PList self, PList other,
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

    @Builtin(name = SpecialMethodNames.__CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isEmptyStorage(self)")
        public boolean doPListEmpty(PList self, Object arg) {
            return false;
        }

        @Specialization(guards = "isIntStorage(self)")
        public boolean doPListInt(PList self, int arg) {
            IntSequenceStorage store = (IntSequenceStorage) self.getSequenceStorage();
            return store.indexOfInt(arg) != -1;
        }

        @Specialization(guards = "isDoubleStorage(self)")
        public boolean doPListDouble(PList self, double other) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) self.getSequenceStorage();
            return store.indexOfDouble(other) != -1;
        }

        @Specialization
        boolean contains(PSequence self, Object other) {
            return self.index(other) != -1;
        }
    }

    @Builtin(name = SpecialMethodNames.__BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization(guards = "isEmptyStorage(list)")
        public boolean doPListEmpty(@SuppressWarnings("unused") PList list) {
            return false;
        }

        @Specialization(guards = "isIntStorage(primary)")
        public boolean doPListInt(PList primary) {
            IntSequenceStorage store = (IntSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        public boolean doPListDouble(PList primary) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        public boolean doPListObject(PList primary) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        boolean doPList(PList operand) {
            return operand.len() != 0;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        public PIntegerSequenceIterator doPListInt(PList primary) {
            return factory().createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        public PLongSequenceIterator doPListLong(PList primary) {
            return factory().createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        public PDoubleSequenceIterator doPListDouble(PList primary) {
            return factory().createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        public PSequenceIterator doPList(PList primary) {
            return factory().createSequenceIterator(primary);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = SpecialMethodNames.__HASH__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object self) {
            throw raise(TypeError, "unhashable type: '%p'", self);
        }
    }
}
