/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
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
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.IndexNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
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
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.SetSequenceStorageItem;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = PList.class)
public class ListBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
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
                Object reprString;
                if (self != value) {
                    reprString = repr.executeObject(value);
                    if (reprString instanceof PString) {
                        reprString = ((PString) reprString).getValue();
                    }
                } else {
                    reprString = "[...]";
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

    @Builtin(name = __DELITEM__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doPListInt(PList primary, long idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected PNone doPListLong(PList primary, long idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected PNone doPListDouble(PList primary, long idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected PNone doPListObject(PList primary, long idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPList(PList list, long idx) {
            SequenceStorage storage = list.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doPListInt(PList primary, PInt idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected PNone doPListLong(PList primary, PInt idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected PNone doPListDouble(PList primary, PInt idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected PNone doPListObject(PList primary, PInt idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPList(PList list, PInt idx) {
            SequenceStorage storage = list.getSequenceStorage();
            storage.delItemInBound(normalize.forList(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPListSlice(PList self, PSlice slice) {
            self.delSlice(slice);
            return PNone.NONE;
        }

        protected static DelItemNode create() {
            return ListBuiltinsFactory.DelItemNodeFactory.create();
        }

        @Specialization
        protected Object doObjectIndex(PList self, Object objectIdx,
                        @Cached("create()") IndexNode getIndexNode,
                        @Cached("create()") DelItemNode getRecursiveNode) {
            return getRecursiveNode.execute(self, getIndexNode.execute(objectIdx));
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx) {
            throw raise(TypeError, "descriptor '__delitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

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

        protected static GetItemNode create() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        @Specialization
        protected Object doObjectIndex(PList self, Object objectIdx,
                        @Cached("create()") IndexNode getIndexNode,
                        @Cached("create()") GetItemNode getRecursiveNode) {
            return getRecursiveNode.execute(self, getIndexNode.execute(objectIdx));
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx) {
            throw raise(TypeError, "descriptor '__getitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

        @Specialization
        public PNone doPList(PList list, PSlice slice, Object value,
                        @Cached("create()") ListNodes.SetSliceNode sliceNode) {
            return sliceNode.execute(list, slice, value);
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

        protected static SetItemNode create() {
            return ListBuiltinsFactory.SetItemNodeFactory.create();
        }

        @Specialization
        protected Object doObjectIndex(PList self, Object objectIdx, Object value,
                        @Cached("create()") IndexNode getIndexNode,
                        @Cached("create()") SetItemNode getRecursiveNode) {
            return getRecursiveNode.execute(self, getIndexNode.execute(objectIdx), value);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx, Object value) {
            throw raise(TypeError, "descriptor '__setitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    // list.append(x)
    @Builtin(name = "append", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isEmptyStorage(list)")
        public PNone appendEmpty(PList list, Object arg) {
            list.append(arg);
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(list)")
        public PNone appendInt(PList list, int arg) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            store.appendInt(arg);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone appendLong(PList list, long arg) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            store.appendLong(arg);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PNone appendDouble(PList list, double arg) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            store.appendDouble(arg);
            return PNone.NONE;
        }

        @Specialization(guards = "isListStorage(list)")
        public PNone appendList(PList list, PList arg) {
            ListSequenceStorage store = (ListSequenceStorage) list.getSequenceStorage();
            store.appendList(arg);
            return PNone.NONE;
        }

        @Specialization(guards = "isTupleStorage(list)")
        public PNone appendTuple(PList list, PTuple arg) {
            TupleSequenceStorage store = (TupleSequenceStorage) list.getSequenceStorage();
            store.appendPTuple(arg);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isKnownStorage(list)"}, rewriteOn = {SequenceStoreException.class})
        public PNone appendObject(PList list, Object arg) throws SequenceStoreException {
            list.getSequenceStorage().append(arg);
            return PNone.NONE;
        }

        @Specialization()
        public PNone appendObjectGeneric(PList list, Object arg) {
            list.append(arg);
            return PNone.NONE;
        }

        protected boolean isKnownStorage(PList list) {
            return PGuards.isEmptyStorage(list) || PGuards.isIntStorage(list) || PGuards.isLongStorage(list) || PGuards.isDoubleStorage(list) || PGuards.isListStorage(list) ||
                            PGuards.isTupleStorage(list);
        }
    }

    // list.extend(L)
    @Builtin(name = "extend", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBuiltinNode {

        public abstract PNone execute(PList list, Object source);

        @Specialization(guards = {"isPSequenceWithStorage(source)"}, rewriteOn = {SequenceStoreException.class})
        public PNone extendSequenceStore(PList list, Object source) throws SequenceStoreException {
            SequenceStorage target = list.getSequenceStorage();
            target.extend(((PSequence) source).getSequenceStorage());
            return PNone.NONE;
        }

        @Specialization(guards = {"isPSequenceWithStorage(source)"})
        public PNone extendSequence(PList list, Object source) {
            SequenceStorage eSource = ((PSequence) source).getSequenceStorage();
            if (eSource.length() > 0) {
                SequenceStorage target = list.getSequenceStorage();
                try {
                    target.extend(eSource);
                } catch (SequenceStoreException e) {
                    target = target.generalizeFor(eSource.getItemNormalized(0), eSource);
                    list.setSequenceStorage(target);
                    try {
                        target.extend(eSource);
                    } catch (SequenceStoreException e1) {
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException();
                    }
                }
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPSequenceWithStorage(source)")
        public PNone extend(PList list, Object source,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            Object workSource = list != source ? source : factory().createList(((PList) source).getSequenceStorage().copy());
            Object iterator = getIterator.executeWith(workSource);
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return PNone.NONE;
                }
                list.append(value);
            }
        }

        protected boolean isPSequenceWithStorage(Object source) {
            return (source instanceof PSequence && !(source instanceof PTuple || source instanceof PRange));
        }

    }

    // list.insert(i, x)
    @Builtin(name = "insert", fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonBuiltinNode {
        protected static final String ERROR_MSG = "'%p' object cannot be interpreted as an integer";

        public abstract PNone execute(PList list, Object index, Object value);

        @Specialization(guards = "isIntStorage(list)")
        public PNone insertIntInt(PList list, int index, int value) {
            IntSequenceStorage target = (IntSequenceStorage) list.getSequenceStorage();
            target.insertIntItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone insertLongLong(PList list, int index, int value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone insertLongLong(PList list, int index, long value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PNone insertDoubleDouble(PList list, int index, double value) {
            DoubleSequenceStorage target = (DoubleSequenceStorage) list.getSequenceStorage();
            target.insertDoubleItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        public PNone insert(PList list, int index, Object value) {
            list.insert(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization
        public PNone insertLongIndex(PList list, long index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = index < Integer.MIN_VALUE ? 0 : index > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) index;
            where = normalizeIndex(where, list.len());
            return insertNode.execute(list, where, value);
        }

        @Specialization
        public PNone insertPIntIndex(PList list, PInt index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = normalizePIntForIndex(index);
            where = normalizeIndex(where, list.len());
            return insertNode.execute(list, where, value);
        }

        @Specialization(guards = {"!isIntegerOrPInt(i)"})
        public PNone insert(PList list, Object i, Object value,
                        @Cached("createInteger(ERROR_MSG)") IndexNode indexNode,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            Object indexValue = indexNode.execute(i);
            return insertNode.execute(list, indexValue, value);
        }

        @TruffleBoundary
        private static int normalizePIntForIndex(PInt index) {
            int where = 0;
            BigInteger bigIndex = index.getValue();
            if (bigIndex.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) == -1) {
                where = 0;
            } else if (bigIndex.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 1) {
                where = Integer.MAX_VALUE;
            } else {
                where = bigIndex.intValue();
            }
            return where;
        }

        private static int normalizeIndex(int index, int len) {
            int idx = index;
            if (idx < 0) {
                idx += len;
                if (idx < 0) {
                    idx = 0;
                }
            }
            if (idx > len) {
                idx = len;
            }
            return idx;
        }

        protected boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && PGuards.isInteger(value)) ||
                            (PGuards.isDoubleStorage(list) && value instanceof Double));
        }

        protected boolean isIntegerOrPInt(Object index) {
            return index instanceof Integer || index instanceof PInt;
        }

        protected ListInsertNode createListInsertNode() {
            return ListBuiltinsFactory.ListInsertNodeFactory.create(new PNode[0]);
        }

    }

    // list.remove(x)
    @Builtin(name = "remove", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBuiltinNode {

        private static String NOT_IN_LIST_MESSAGE = "list.index(x): x not in list";

        @Specialization(guards = "isIntStorage(list)")
        public PNone removeInt(PList list, int value) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getIntItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone removeLong(PList list, int value) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getLongItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone removeLong(PList list, long value) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getLongItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PNone removeDouble(PList list, double value) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getDoubleItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        public PNone remove(PList list, Object value,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            int len = list.len();
            for (int i = 0; i < len; i++) {
                Object object = list.getItem(i);
                if (eqNode.executeBool(object, value)) {
                    list.delItem(i);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        protected boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && (value instanceof Integer || value instanceof Long)) ||
                            PGuards.isDoubleStorage(list) && value instanceof Double);
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
        protected final static String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

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
            for (int i = start; i < end && i < list.len(); i++) {
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
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(start);
            return indexNode.execute(self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)",})
        int indexLO(PTuple self, Object value, long start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object endValue = endNode.execute(end);
            return indexNode.execute(self, value, start, endValue);
        }

        @Specialization(guards = {"!isNumber(start) || !isNumber(end)",})
        int indexOO(PTuple self, Object value, Object start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(start);
            Object endValue = endNode.execute(end);
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

    // list.clear()
    @Builtin(name = "clear", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ListClearNode extends PythonBuiltinNode {

        @Specialization
        public PNone clear(PList list) {
            if (list.len() > 0) {
                list.setSequenceStorage(new EmptySequenceStorage());
            }
            return PNone.NONE;
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

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
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

        @Specialization(guards = "!isList(right)")
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only concatenate list (not \"%p\") to list", right);
        }
    }

    @Builtin(name = __IADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBuiltinNode {

        @Specialization(guards = "areBothIntStorage(left,right)")
        PList doPListInt(PList left, PList right) {
            IntSequenceStorage leftStore = (IntSequenceStorage) left.getSequenceStorage();
            IntSequenceStorage rightStore = (IntSequenceStorage) right.getSequenceStorage();
            leftStore.extendWithIntStorage(rightStore);
            return left;
        }

        @Specialization(guards = "areBothLongStorage(left,right)")
        PList doPListLong(PList left, PList right) {
            LongSequenceStorage leftStore = (LongSequenceStorage) left.getSequenceStorage();
            LongSequenceStorage rightStore = (LongSequenceStorage) right.getSequenceStorage();
            leftStore.extendWithLongStorage(rightStore);
            return left;
        }

        @Specialization(guards = "areBothDoubleStorage(left,right)")
        PList doPListDouble(PList left, PList right) {
            DoubleSequenceStorage leftStore = (DoubleSequenceStorage) left.getSequenceStorage();
            DoubleSequenceStorage rightStore = (DoubleSequenceStorage) right.getSequenceStorage();
            leftStore.extendWithDoubleStorage(rightStore);
            return left;
        }

        @Specialization(guards = "areBothObjectStorage(left,right)")
        PList doPListObject(PList left, PList right) {
            ObjectSequenceStorage leftStore = (ObjectSequenceStorage) left.getSequenceStorage();
            ObjectSequenceStorage rightStore = (ObjectSequenceStorage) right.getSequenceStorage();
            leftStore.extend(rightStore);
            return left;
        }

        @Specialization(guards = "isNotSameStorage(left, right)")
        PList doPList(PList left, PList right) {
            left.extend(right);
            return left;
        }

        @Specialization(guards = "!isList(right)")
        PList doPList(PList left, Object right,
                        @Cached("createExtendNode()") ListExtendNode extendNode) {
            extendNode.execute(left, right);
            return left;
        }

        protected ListExtendNode createExtendNode() {
            return ListBuiltinsFactory.ListExtendNodeFactory.create(new PNode[0]);
        }

        protected boolean isNotSameStorage(PList left, PList right) {
            return !(PGuards.areBothIntStorage(right, left) || PGuards.areBothDoubleStorage(right, left) || PGuards.areBothLongStorage(right, left) || PGuards.areBothObjectStorage(right, left));
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBuiltinNode {
        public static String CANNOT_FIT_MESSAGE = "cannot fit 'int' into an index-sized integer";

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

        @Specialization(guards = "right <= 0")
        PList doPListLongNegative(@SuppressWarnings("unused") PList left, @SuppressWarnings("unused") long right) {
            return factory().createList();
        }

        @Specialization(guards = "right > 0", rewriteOn = ArithmeticException.class)
        PList doPListLong(PList left, long right,
                        @Cached("createClassProfile()") ValueProfile profile) {
            return doPListInt(left, PInt.intValueExact(right), profile);
        }

        @Specialization(replaces = "doPListLong")
        PList doPListLongOvf(PList left, long right,
                        @Cached("create()") BranchProfile notPositiveProfile,
                        @Cached("createClassProfile()") ValueProfile profile) {
            if (right <= 0) {
                notPositiveProfile.enter();
                return factory().createList();
            }
            try {
                return doPListInt(left, PInt.intValueExact(right), profile);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "right.isZeroOrNegative()", rewriteOn = ArithmeticException.class)
        PList doPListBigIntNegative(@SuppressWarnings("unused") PList left, @SuppressWarnings("unused") PInt right) {
            return factory().createList();
        }

        @Specialization(guards = "!right.isZeroOrNegative()", rewriteOn = ArithmeticException.class)
        PList doPListBigInt(PList left, PInt right,
                        @Cached("createClassProfile()") ValueProfile profile) {
            return doPListInt(left, right.intValueExact(), profile);
        }

        @Specialization(replaces = "doPListBigInt")
        PList doPListBigIntOvf(PList left, PInt right,
                        @Cached("create()") BranchProfile notPositiveProfile,
                        @Cached("createClassProfile()") ValueProfile profile) {
            if (right.isZeroOrNegative()) {
                notPositiveProfile.enter();
                return factory().createList();
            }
            try {
                return doPListInt(left, right.intValueExact(), profile);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(OverflowError, CANNOT_FIT_MESSAGE);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __IMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBuiltinNode {
        protected static final String ERROR_MSG = "can't multiply sequence by non-int of type '%p'";

        public abstract PList execute(PList list, Object value);

        @Specialization(guards = "isEmptyStorage(list)")
        PList doEmptyBoolean(PList list, @SuppressWarnings("unused") boolean right) {
            return list;
        }

        @Specialization(guards = "isEmptyStorage(list)")
        PList doEmptyInt(PList list, @SuppressWarnings("unused") int right) {
            return list;
        }

        @Specialization(guards = "isEmptyStorage(list)")
        PList doEmptyLong(PList list, long right) {
            try {
                PInt.intValueExact(right);
                return list;
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isEmptyStorage(list)")
        PList doEmptyPInt(PList list, PInt right) {
            try {
                right.intValueExact();
                return list;
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isIntStorage(list)")
        PList doIntBoolean(PList list, boolean right) {
            return doIntInt(list, right ? 1 : 0);
        }

        @Specialization(guards = "isIntStorage(list)")
        PList doIntInt(PList list, int right) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            if (right < 1) {
                store.clear();
                return list;
            }
            try {
                IntSequenceStorage copy = (IntSequenceStorage) store.copy();
                for (int i = 1; i < right; i++) {
                    store.extendWithIntStorage(copy);
                }
                return list;
            } catch (OutOfMemoryError e) {
                throw raise(MemoryError);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isIntStorage(list)")
        PList doIntLong(PList list, long right) {
            try {
                return doIntInt(list, PInt.intValueExact(right));
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isIntStorage(list)")
        PList doIntPInt(PList list, PInt right) {
            try {
                return doIntInt(list, right.intValueExact());
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isLongStorage(list)")
        PList doLongBoolean(PList list, boolean right) {
            return doLongInt(list, right ? 1 : 0);
        }

        @Specialization(guards = "isLongStorage(list)")
        PList doLongInt(PList list, int right) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            if (right < 1) {
                store.clear();
                return list;
            }
            try {
                LongSequenceStorage copy = (LongSequenceStorage) store.copy();
                for (int i = 1; i < right; i++) {
                    store.extendWithLongStorage(copy);
                }
                return list;
            } catch (OutOfMemoryError e) {
                throw raise(MemoryError);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isLongStorage(list)")
        PList doLongLong(PList list, long right) {
            try {
                return doLongInt(list, PInt.intValueExact(right));
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isLongStorage(list)")
        PList doLongPInt(PList list, PInt right) {
            try {
                return doLongInt(list, right.intValueExact());
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isDoubleStorage(list)")
        PList doDoubleBoolean(PList list, boolean right) {
            return doDoubleInt(list, right ? 1 : 0);
        }

        @Specialization(guards = "isDoubleStorage(list)")
        PList doDoubleInt(PList list, int right) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            if (right < 1) {
                store.clear();
                return list;
            }
            try {
                DoubleSequenceStorage copy = (DoubleSequenceStorage) store.copy();
                for (int i = 1; i < right; i++) {
                    store.extendWithDoubleStorage(copy);
                }
                return list;
            } catch (OutOfMemoryError e) {
                throw raise(MemoryError);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isDoubleStorage(list)")
        PList doDoubleLong(PList list, long right) {
            try {
                return doDoubleInt(list, PInt.intValueExact(right));
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isDoubleStorage(list)")
        PList doDoublePInt(PList list, PInt right) {
            try {
                return doLongInt(list, right.intValueExact());
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isObjectStorage(list)")
        PList doObjectBoolean(PList list, boolean right) {
            return doDoubleInt(list, right ? 1 : 0);
        }

        @Specialization(guards = "isObjectStorage(list)")
        PList doObjectInt(PList list, int right) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) list.getSequenceStorage();
            if (right < 1) {
                store.clear();
                return list;
            }
            try {
                ObjectSequenceStorage copy = (ObjectSequenceStorage) store.copy();
                for (int i = 1; i < right; i++) {
                    store.extend(copy);
                }
                return list;
            } catch (OutOfMemoryError e) {
                throw raise(MemoryError);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isObjectStorage(list)")
        PList doObjectLong(PList list, long right) {
            try {
                return doObjectInt(list, PInt.intValueExact(right));
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = "isObjectStorage(list)")
        PList doObjectPInt(PList list, PInt right) {
            try {
                return doObjectInt(list, right.intValueExact());
            } catch (ArithmeticException e) {
                throw raise(OverflowError, MulNode.CANNOT_FIT_MESSAGE);
            }
        }

        @Specialization(guards = {"!isInt(right)"})
        Object doGeneric(PList list, Object right,
                        @Cached("createInteger(ERROR_MSG)") IndexNode dispatchIndex,
                        @Cached("createIMulNode()") IMulNode imulNode) {
            Object index = dispatchIndex.execute(right);
            int iIndex;
            try {
                iIndex = convertToInt(index);
            } catch (ArithmeticException e) {
                throw raise(OverflowError, "cannot fit '%p' into an index-sized integer", index);
            }
            return imulNode.execute(list, iIndex);
        }

        private static int convertToInt(Object value) throws ArithmeticException {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Boolean) {
                return (Boolean) value ? 0 : 1;
            } else if (value instanceof Long) {
                return PInt.intValueExact((Long) value);
            } else {
                assert value instanceof PInt;
                return ((PInt) value).intValueExact();
            }
        }

        protected IMulNode createIMulNode() {
            return ListBuiltinsFactory.IMulNodeFactory.create(new PNode[0]);
        }

        protected boolean isInt(Object value) {
            return value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof PInt;
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
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

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
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

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends ListComparisonNode {

        @Specialization
        boolean contains(PList self, PList other,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__LT__, __GT__, __LT__)") BinaryComparisonNode ltNode) {
            return doComparison(self, other, eqNode, ltNode);
        }
    }

    @Builtin(name = __GT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends ListComparisonNode {

        @Specialization
        boolean contains(PList self, PList other,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__GT__, __LT__, __GT__)") BinaryComparisonNode gtNode) {
            return doComparison(self, other, eqNode, gtNode);
        }
    }

    @Builtin(name = __GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends ListComparisonNode {

        @Specialization
        boolean doPTuple(PList left, PList right,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__GE__, __LE__, __GE__)") BinaryComparisonNode geNode) {
            return doComparison(left, right, eqNode, geNode);
        }
    }

    @Builtin(name = __LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends ListComparisonNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode,
                        @Cached("create(__LE__, __GE__, __LE__)") BinaryComparisonNode leNode) {
            return doComparison(left, right, eqNode, leNode);
        }
    }

    abstract static class ListComparisonNode extends PythonBinaryBuiltinNode {

        static boolean doComparison(PList self, PList other,
                        BinaryComparisonNode eqNode,
                        BinaryComparisonNode compNode) {
            int len = self.len();
            int len2 = other.len();
            int min = Math.min(len, len2);
            for (int i = 0; i < min; i++) {
                Object left = self.getItem(i);
                Object right = other.getItem(i);
                if (!eqNode.executeBool(left, right)) {
                    return compNode.executeBool(left, right);
                }
            }
            return compNode.executeBool(len, len2);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
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

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
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

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
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

    @Builtin(name = __HASH__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object self) {
            throw raise(TypeError, "unhashable type: '%p'", self);
        }
    }
}
