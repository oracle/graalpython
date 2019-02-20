/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListAppendNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.FastConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.IndexNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ListNodes {

    public static final class CreateStorageFromIteratorNode extends PNodeWithContext {

        private static final int START_SIZE = 2;

        @Child private GetNextNode next = GetNextNode.create();

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ListStorageType type = ListStorageType.Uninitialized;

        public static CreateStorageFromIteratorNode create() {
            return new CreateStorageFromIteratorNode();
        }

        public SequenceStorage execute(Object iterator) {
            SequenceStorage storage;
            if (type == ListStorageType.Uninitialized) {
                try {
                    Object[] elements = new Object[START_SIZE];
                    int i = 0;
                    while (true) {
                        try {
                            Object value = next.execute(iterator);
                            if (i >= elements.length) {
                                elements = Arrays.copyOf(elements, elements.length * 2);
                            }
                            elements[i++] = value;
                        } catch (PException e) {
                            e.expectStopIteration(errorProfile);
                            break;
                        }
                    }
                    storage = new SequenceStorageFactory().createStorage(Arrays.copyOf(elements, i));
                    if (storage instanceof IntSequenceStorage) {
                        type = ListStorageType.Int;
                    } else if (storage instanceof LongSequenceStorage) {
                        type = ListStorageType.Long;
                    } else if (storage instanceof DoubleSequenceStorage) {
                        type = ListStorageType.Double;
                    } else if (storage instanceof ListSequenceStorage) {
                        type = ListStorageType.List;
                    } else if (storage instanceof TupleSequenceStorage) {
                        type = ListStorageType.Tuple;
                    } else {
                        type = ListStorageType.Generic;
                    }
                } catch (Throwable t) {
                    type = ListStorageType.Generic;
                    throw t;
                }
            } else {
                int i = 0;
                Object array = null;
                try {
                    switch (type) {
                        case Int: {
                            int[] elements = new int[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    int value = next.executeInt(iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new IntSequenceStorage(elements, i);
                            break;
                        }
                        case Long: {
                            long[] elements = new long[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    long value = next.executeLong(iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new LongSequenceStorage(elements, i);
                            break;
                        }
                        case Double: {
                            double[] elements = new double[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    double value = next.executeDouble(iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new DoubleSequenceStorage(elements, i);
                            break;
                        }
                        case List: {
                            PList[] elements = new PList[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    PList value = PList.expect(next.execute(iterator));
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ListSequenceStorage(elements, i);
                            break;
                        }
                        case Tuple: {
                            PTuple[] elements = new PTuple[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    PTuple value = PTuple.expect(next.execute(iterator));
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new TupleSequenceStorage(elements, i);
                            break;
                        }
                        case Generic: {
                            Object[] elements = new Object[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    Object value = next.execute(iterator);
                                    if (i >= elements.length) {
                                        elements = Arrays.copyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ObjectSequenceStorage(elements, i);
                            break;
                        }
                        default:
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    storage = genericFallback(iterator, array, i, e.getResult());
                }
            }
            return storage;
        }

        private SequenceStorage genericFallback(Object iterator, Object array, int count, Object result) {
            type = ListStorageType.Generic;
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            while (true) {
                try {
                    Object value = next.execute(iterator);
                    if (i >= elements.length) {
                        elements = Arrays.copyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return new ObjectSequenceStorage(elements, i);
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructListNode extends PNodeWithContext {

        @Child private ListAppendNode appendNode;

        public final PList execute(Object value) {
            return execute(PythonBuiltinClassType.PList, value);
        }

        public abstract PList execute(LazyPythonClass cls, Object value);

        @Specialization
        public PList listString(LazyPythonClass cls, PString arg) {
            return listString(cls, arg.getValue());
        }

        @Specialization
        public PList listString(LazyPythonClass cls, String arg) {
            char[] chars = arg.toCharArray();
            PList list = factory().createList(cls);

            for (char c : chars) {
                getAppendNode().execute(list, Character.toString(c));
            }

            return list;
        }

        @Specialization(guards = "isNoValue(none)")
        public PList listIterable(LazyPythonClass cls, @SuppressWarnings("unused") PNone none) {
            return factory().createList(cls);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"})
        public PList listIterable(LazyPythonClass cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") CreateStorageFromIteratorNode createStorageFromIteratorNode) {

            Object iterObj = getIteratorNode.executeWith(iterable);
            SequenceStorage storage = createStorageFromIteratorNode.execute(iterObj);
            return factory().createList(cls, storage);
        }

        @Fallback
        public PList listObject(@SuppressWarnings("unused") LazyPythonClass cls, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        private ListAppendNode getAppendNode() {
            if (appendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendNode = insert(ListAppendNode.create());
            }
            return appendNode;
        }

        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class FastConstructListNode extends PNodeWithContext {

        @Child private ConstructListNode constructListNode;

        public abstract PSequence execute(Object value);

        @Specialization(guards = "cannotBeOverridden(value.getLazyPythonClass())")
        protected PSequence doPList(PSequence value) {
            return value;
        }

        @Fallback
        protected PSequence doGeneric(Object value) {
            if (constructListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructListNode = insert(ConstructListNode.create());
            }
            return constructListNode.execute(PythonBuiltinClassType.PList, value);
        }

        public static FastConstructListNode create() {
            return FastConstructListNodeGen.create();
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IndexNode extends PNodeWithContext {
        private static final String DEFAULT_ERROR_MSG = "list indices must be integers or slices, not %p";
        @Child LookupAndCallUnaryNode getIndexNode;
        private final CheckType checkType;
        private final String errorMessage;

        protected static enum CheckType {
            SUBSCRIPT,
            INTEGER,
            NUMBER;
        }

        protected IndexNode(String message, CheckType type) {
            checkType = type;
            getIndexNode = LookupAndCallUnaryNode.create(__INDEX__);
            errorMessage = message;
        }

        public static IndexNode create(String message) {
            return IndexNodeGen.create(message, CheckType.SUBSCRIPT);
        }

        public static IndexNode create() {
            return IndexNodeGen.create(DEFAULT_ERROR_MSG, CheckType.SUBSCRIPT);
        }

        public static IndexNode createInteger(String msg) {
            return IndexNodeGen.create(msg, CheckType.INTEGER);
        }

        public static IndexNode createNumber(String msg) {
            return IndexNodeGen.create(msg, CheckType.NUMBER);
        }

        public abstract Object execute(Object object);

        protected boolean isSubscript() {
            return checkType == CheckType.SUBSCRIPT;
        }

        protected boolean isNumber() {
            return checkType == CheckType.NUMBER;
        }

        @Specialization
        long doLong(long slice) {
            return slice;
        }

        @Specialization
        PInt doPInt(PInt slice) {
            return slice;
        }

        @Specialization(guards = "isSubscript()")
        PSlice doSlice(PSlice slice) {
            return slice;
        }

        @Specialization(guards = "isNumber()")
        float doFloat(float slice) {
            return slice;
        }

        @Specialization(guards = "isNumber()")
        double doDouble(double slice) {
            return slice;
        }

        @Fallback
        Object doGeneric(Object object) {
            Object idx = getIndexNode.executeObject(object);
            boolean valid = false;
            switch (checkType) {
                case SUBSCRIPT:
                    valid = MathGuards.isInteger(idx) || idx instanceof PSlice;
                    break;
                case NUMBER:
                    valid = MathGuards.isNumber(idx);
                    break;
                case INTEGER:
                    valid = MathGuards.isInteger(idx);
                    break;
            }
            if (valid) {
                return idx;
            } else {
                throw raise(TypeError, errorMessage, idx);
            }
        }
    }
}
