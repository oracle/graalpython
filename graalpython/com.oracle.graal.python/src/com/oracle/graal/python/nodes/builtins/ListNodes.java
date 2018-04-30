/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
package com.oracle.graal.python.nodes.builtins;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.FastConstructListNodeGen;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class ListNodes {

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class CreateListFromIteratorNode extends PBaseNode {

        public abstract PList execute(PythonClass cls, Object iterable);

        @Specialization
        public PList executeGeneric(PythonClass cls, Object iterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            PList list = factory().createList(cls);
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

        public static CreateListFromIteratorNode create() {
            return ListNodesFactory.CreateListFromIteratorNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructListNode extends PBaseNode {

        public final PList execute(Object value, PythonClass valueClass) {
            return execute(lookupClass(PythonBuiltinClassType.PList), value, valueClass);
        }

        public abstract PList execute(Object cls, Object value, PythonClass valueClass);

        @Specialization
        public PList listString(PythonClass cls, String arg, @SuppressWarnings("unused") PythonClass valueClass) {
            char[] chars = arg.toCharArray();
            PList list = factory().createList(cls);

            for (char c : chars) {
                list.append(Character.toString(c));
            }

            return list;
        }

        @Specialization(guards = "isNoValue(none)")
        public PList listIterable(PythonClass cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PythonClass valueClass) {
            return factory().createList(cls);
        }

        @Specialization(guards = "!isNoValue(iterable)")
        public PList listIterable(PythonClass cls, Object iterable, @SuppressWarnings("unused") PythonClass valueClass,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") CreateListFromIteratorNode createListFromIteratorNode) {

            Object iterObj = getIteratorNode.executeWith(iterable);
            return createListFromIteratorNode.execute(cls, iterObj);
        }

        @Fallback
        public PList listObject(@SuppressWarnings("unused") Object cls, Object value, @SuppressWarnings("unused") PythonClass valueClass) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class FastConstructListNode extends PBaseNode {

        @Child private ConstructListNode constructListNode;

        public final PSequence execute(Object value, PythonClass valueClass) {
            return execute(lookupClass(PythonBuiltinClassType.PList), value, valueClass);
        }

        public abstract PSequence execute(Object cls, Object value, PythonClass valueClass);

        @Specialization(guards = "cannotBeOverridden(valueClass)")
        protected PSequence doPList(@SuppressWarnings("unused") Object cls, PSequence value, @SuppressWarnings("unused") PythonClass valueClass) {
            return value;
        }

        @Fallback
        protected PSequence doGeneric(Object cls, Object value, PythonClass valueClass) {
            if (constructListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructListNode = insert(ConstructListNode.create());
            }
            return constructListNode.execute(cls, value, valueClass);
        }

        public static FastConstructListNode create() {
            return FastConstructListNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class SetSliceNode extends PBaseNode {

        public abstract PNone execute(PList list, PSlice slice, Object value);

        @Specialization(guards = {"isPTuple(value)", "isEmptyStorage(list)"})
        public PNone doPListEmptyTupleValue(PList list, PSlice slice, PSequence value,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {
            if (value.len() > 0) {
                PList pvalue = factory().createList(((PTuple) value).getArray());
                SequenceStorage newStorage = list.getSequenceStorage().generalizeFor(pvalue.getSequenceStorage().getIndicativeValue());
                list.setSequenceStorage(newStorage);
                setSlice(list, slice, pvalue, wrongLength);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"isPTuple(value)", "!isEmptyStorage(list)"})
        public PNone doPListTupleValue(PList list, PSlice slice, PSequence value,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {
            PList pvalue = factory().createList(((PTuple) value).getArray());
            setSlice(list, slice, pvalue, wrongLength);
            return PNone.NONE;
        }

        @Specialization(guards = {"isEmptyStorage(list)"})
        public PNone doPListEmpty(PList list, PSlice slice, PSequence value,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {
            if (value.len() > 0) {
                SequenceStorage newStorage = list.getSequenceStorage().generalizeFor(value.getSequenceStorage().getIndicativeValue());
                list.setSequenceStorage(newStorage);
                setSlice(list, slice, value, wrongLength);
            }

            return PNone.NONE;
        }

        @Specialization(guards = {"areTheSameType(list, value)"})
        public PNone doPListInt(PList list, PSlice slice, PSequence value,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {
            setSlice(list, slice, value, wrongLength);
            return PNone.NONE;
        }

        @Specialization(guards = {"!areTheSameType(list, value)"})
        public PNone doPList(PList list, PSlice slice, PSequence value,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {
            SequenceStorage newStorage = list.getSequenceStorage().generalizeFor(value.getSequenceStorage().getIndicativeValue());
            list.setSequenceStorage(newStorage);
            setSlice(list, slice, value, wrongLength);
            return PNone.NONE;
        }

        @Specialization
        public PNone doPList(PList list, PSlice slice, Object value,
                        @Cached("create()") LookupInheritedAttributeNode attrNode,
                        @Cached("create()") ListNodes.ConstructListNode constructListNode,
                        @Cached("create()") GetClassNode getClassNode,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLength) {

            PythonClass clazz = getClassNode.execute(value);
            boolean isIter = attrNode.execute(clazz, __ITER__) != null;
            if (!isIter) {
                isIter = attrNode.execute(clazz, __GETITEM__) != null;
                if (!isIter) {
                    throw raise(PythonErrorType.TypeError, "can only assign an iterable");
                }
            }
            PList pvalue = constructListNode.execute(value, clazz);
            canGeneralize(list, slice, pvalue, wrongLength);
            return PNone.NONE;
        }

        private void canGeneralize(PList list, PSlice slice, PSequence value, ConditionProfile wrongLength) {
            try {
                setSlice(list, slice, value, wrongLength);
            } catch (SequenceStoreException e) {
                SequenceStorage newStorage = list.getSequenceStorage().generalizeFor(value.getSequenceStorage().getIndicativeValue());
                list.setSequenceStorage(newStorage);
                try {
                    setSlice(list, slice, value, wrongLength);
                } catch (SequenceStoreException ex) {
                    throw new IllegalStateException();
                }
            }
        }

        private void setSlice(PSequence list, PSlice slice, PSequence value, ConditionProfile wrongLength) {
            SequenceStorage store = list.getSequenceStorage();
            PSlice.SliceInfo sinfo = slice.computeActualIndices(store.length());
            int valueLen = value.len();
            int length = list.len();
            if (wrongLength.profile(sinfo.step != 1 && sinfo.length != valueLen)) {
                throw raise(ValueError, "attempt to assign sequence of size %d to extended slice of size %d", valueLen, length);
            }
            int start = sinfo.start;
            int stop = sinfo.stop;
            int step = sinfo.step;
            boolean negativeStep = false;

            if (step < 0) {
                // For the simplicity of algorithm, then start and stop are swapped.
                // The start index has to recalculated according the step, because
                // the algorithm bellow removes the start item and then start + step ....
                step = Math.abs(step);
                stop++;
                int tmpStart = stop + ((start - stop) % step);
                stop = start + 1;
                start = tmpStart;
                negativeStep = true;
            }
            if (start < 0) {
                start = 0;
            } else if (start > length) {
                start = length;
            }
            if (stop < start) {
                stop = start;
            } else if (stop > length) {
                stop = length;
            }

            int norig = stop - start;
            int delta = valueLen - norig;
            int index;

            if (length + delta == 0) {
                store.setNewLength(0);
                return;
            }
            store.ensureCapacity(length + delta);
            // we need to work with the copy in the case if a[i:j] = a
            SequenceStorage workingValue = store == value.getSequenceStorage()
                            ? workingValue = store.copy() : value.getSequenceStorage();

            if (step == 1) {
                if (delta < 0) {
                    // delete items
                    for (index = stop + delta; index < length + delta; index++) {
                        store.copyItem(index, index - delta);
                    }
                    length += delta;
                    stop += delta;
                } else if (delta > 0) {
                    // insert items
                    for (index = length - 1; index >= stop; index--) {
                        store.copyItem(index + delta, index);
                    }
                    length += delta;
                    stop += delta;
                }
            }

            if (!negativeStep) {
                for (int i = start, j = 0; i < stop; i += step, j++) {
                    store.setItemNormalized(i, workingValue.getItemNormalized(j));
                }
            } else {
                for (int i = start, j = valueLen - 1; i < stop; i += step, j--) {
                    store.setItemNormalized(i, workingValue.getItemNormalized(j));
                }
            }
            store.setNewLength(length);
        }

        protected boolean areTheSameType(PList list, PSequence value) {
            return list.getSequenceStorage().getClass() == value.getSequenceStorage().getClass();
        }

        public static SetSliceNode create() {
            return ListNodesFactory.SetSliceNodeGen.create();
        }
    }
}
