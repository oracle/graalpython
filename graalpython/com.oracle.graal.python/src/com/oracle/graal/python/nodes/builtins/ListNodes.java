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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.AppendNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.FastConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.IndexNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorWithoutFrameNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.control.GetNextNode.GetNextWithoutFrameNode;
import com.oracle.graal.python.nodes.control.GetNextNodeFactory.GetNextWithoutFrameNodeGen;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class ListNodes {

    public abstract static class CreateStorageFromIteratorHelper<T extends Node> {

        private static final int START_SIZE = 2;

        protected abstract boolean nextBoolean(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract int nextInt(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract long nextLong(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract double nextDouble(VirtualFrame frame, T nextNode, Object iterator) throws UnexpectedResultException;

        protected abstract Object nextObject(VirtualFrame frame, T nextNode, Object iterator);

        protected SequenceStorage doIt(VirtualFrame frame, Object iterator, ListStorageType type, T nextNode, IsBuiltinClassProfile errorProfile) {
            SequenceStorage storage;
            if (type == ListStorageType.Uninitialized || type == ListStorageType.Empty) {
                Object[] elements = new Object[START_SIZE];
                int i = 0;
                while (true) {
                    try {
                        Object value = nextObject(frame, nextNode, iterator);
                        if (i >= elements.length) {
                            elements = Arrays.copyOf(elements, elements.length * 2);
                        }
                        elements[i++] = value;
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        break;
                    }
                }
                storage = SequenceStorageFactory.createStorage(Arrays.copyOf(elements, i));
            } else {
                int i = 0;
                Object array = null;
                try {
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    boolean value = nextBoolean(frame, nextNode, iterator);
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
                            storage = new BoolSequenceStorage(elements, i);
                            break;
                        }
                        case Byte: {
                            byte[] elements = new byte[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    int value = nextInt(frame, nextNode, iterator);
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(value);
                                        if (i >= elements.length) {
                                            elements = Arrays.copyOf(elements, elements.length * 2);
                                            array = elements;
                                        }
                                        elements[i++] = bvalue;
                                    } catch (ArithmeticException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                } catch (PException e) {
                                    e.expectStopIteration(errorProfile);
                                    break;
                                }
                            }
                            storage = new ByteSequenceStorage(elements, i);
                            break;
                        }
                        case Int: {
                            int[] elements = new int[START_SIZE];
                            array = elements;
                            while (true) {
                                try {
                                    int value = nextInt(frame, nextNode, iterator);
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
                                    long value = nextLong(frame, nextNode, iterator);
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
                                    double value = nextDouble(frame, nextNode, iterator);
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
                                    PList value = PList.expect(nextObject(frame, nextNode, iterator));
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
                                    PTuple value = PTuple.expect(nextObject(frame, nextNode, iterator));
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
                            while (true) {
                                try {
                                    Object value = nextObject(frame, nextNode, iterator);
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
                    storage = genericFallback(frame, iterator, array, i, e.getResult(), nextNode, errorProfile);
                }
            }
            return storage;
        }

        private SequenceStorage genericFallback(VirtualFrame frame, Object iterator, Object array, int count, Object result, T nextNode, IsBuiltinClassProfile errorProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            while (true) {
                try {
                    Object value = nextObject(frame, nextNode, iterator);
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

    private static final class CreateStorageFromIteratorInternalNode extends CreateStorageFromIteratorHelper<GetNextNode> {

        @Override
        protected boolean nextBoolean(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeBoolean(frame, iterator);
        }

        @Override
        protected int nextInt(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeInt(frame, iterator);
        }

        @Override
        protected long nextLong(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeLong(frame, iterator);
        }

        @Override
        protected double nextDouble(VirtualFrame frame, GetNextNode nextNode, Object iterator) throws UnexpectedResultException {
            return nextNode.executeDouble(frame, iterator);
        }

        @Override
        protected Object nextObject(VirtualFrame frame, GetNextNode nextNode, Object iterator) {
            return nextNode.execute(frame, iterator);
        }

    }

    public static final class CreateStorageFromIteratorNode extends Node {
        private static final CreateStorageFromIteratorInternalNode HELPER = new CreateStorageFromIteratorInternalNode();

        @Child private GetNextNode getNextNode = GetNextNode.create();

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ListStorageType expectedElementType = ListStorageType.Uninitialized;

        public SequenceStorage execute(VirtualFrame frame, Object iterator) {
            SequenceStorage doIt = HELPER.doIt(frame, iterator, expectedElementType, getNextNode, errorProfile);
            ListStorageType actualElementType = doIt.getElementType();
            if (expectedElementType != actualElementType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedElementType = actualElementType;
            }
            return doIt;
        }

        public static CreateStorageFromIteratorNode create() {
            return new CreateStorageFromIteratorNode();
        }
    }

    private static final class CreateStorageFromIteratorInteropHelper extends CreateStorageFromIteratorHelper<GetNextWithoutFrameNode> {

        @Override
        protected boolean nextBoolean(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Boolean) {
                return (boolean) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected int nextInt(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Integer) {
                return (int) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected long nextLong(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Long) {
                return (long) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected double nextDouble(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) throws UnexpectedResultException {
            Object value = nextNode.executeWithGlobalState(iterator);
            if (value instanceof Double) {
                return (double) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        protected Object nextObject(VirtualFrame frame, GetNextWithoutFrameNode nextNode, Object iterator) {
            return nextNode.executeWithGlobalState(iterator);
        }
    }

    public abstract static class CreateStorageFromIteratorInteropNode extends PNodeWithContext {

        protected static final CreateStorageFromIteratorInteropHelper HELPER = new CreateStorageFromIteratorInteropHelper();

        protected abstract SequenceStorage execute(Object iterator);

        public static CreateStorageFromIteratorInteropNode create() {
            return new CreateStorageFromIteratorCachedNode();
        }

        public static CreateStorageFromIteratorInteropNode getUncached() {
            return CreateStorageFromIteratorUncachedNode.INSTANCE;
        }
    }

    private static final class CreateStorageFromIteratorCachedNode extends CreateStorageFromIteratorInteropNode {

        @Child private GetNextWithoutFrameNode getNextNode = GetNextWithoutFrameNodeGen.create();

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ListStorageType expectedElementType = ListStorageType.Uninitialized;

        @Override
        public SequenceStorage execute(Object iterator) {
            // NOTE: it is fine to pass 'null' frame because the callers must already take care of
            // the global state
            SequenceStorage doIt = HELPER.doIt(null, iterator, expectedElementType, getNextNode, errorProfile);
            ListStorageType actualElementType = doIt.getElementType();
            if (expectedElementType != actualElementType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedElementType = actualElementType;
            }
            return doIt;
        }
    }

    private static final class CreateStorageFromIteratorUncachedNode extends CreateStorageFromIteratorInteropNode {
        public static final CreateStorageFromIteratorUncachedNode INSTANCE = new CreateStorageFromIteratorUncachedNode();

        @Override
        public SequenceStorage execute(Object iterator) {
            // NOTE: it is fine to pass 'null' frame because the callers must already take care of
            // the global state
            return HELPER.doIt(null, iterator, ListStorageType.Uninitialized, GetNextWithoutFrameNodeGen.getUncached(), IsBuiltinClassProfile.getUncached());
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructListNode extends PNodeWithContext {

        public final PList execute(Object value) {
            return execute(PythonBuiltinClassType.PList, value);
        }

        protected abstract PList execute(LazyPythonClass cls, Object value);

        @Specialization
        PList listString(LazyPythonClass cls, PString arg,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return listString(cls, arg.getValue(), appendNode, factory);
        }

        @Specialization
        PList listString(LazyPythonClass cls, String arg,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            char[] chars = arg.toCharArray();
            PList list = factory.createList(cls);

            for (char c : chars) {
                appendNode.execute(list, Character.toString(c));
            }

            return list;
        }

        @Specialization(guards = "isNoValue(none)")
        PList listIterable(LazyPythonClass cls, @SuppressWarnings("unused") PNone none,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createList(cls);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"})
        PList listIterable(LazyPythonClass cls, Object iterable,
                        @Cached GetIteratorWithoutFrameNode getIteratorNode,
                        @Cached CreateStorageFromIteratorInteropNode createStorageFromIteratorNode,
                        @Cached PythonObjectFactory factory) {

            Object iterObj = getIteratorNode.executeWithGlobalState(iterable);
            SequenceStorage storage = createStorageFromIteratorNode.execute(iterObj);
            return factory.createList(cls, storage);
        }

        @Fallback
        PList listObject(@SuppressWarnings("unused") LazyPythonClass cls, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }

        public static ConstructListNode getUncached() {
            return ConstructListNodeGen.getUncached();
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
        @Child private PRaiseNode raise;
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

        public abstract Object execute(VirtualFrame frame, Object object);

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
        Object doGeneric(VirtualFrame frame, Object object) {
            Object idx = getIndexNode.executeObject(frame, object);
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
                if (raise == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raise = insert(PRaiseNode.create());
                }
                throw raise.raise(TypeError, errorMessage, idx);
            }
        }
    }

    /**
     * This node takes a bit of care to avoid compiling code for switching storages. In the
     * interpreter, it will use a different {@link AppendNode} than in the compiled code, so the
     * specializations for the compiler are different. This is useful, because in the interpreter we
     * will report back type and size changes to the origin of the list via {@link PList#getOrigin}
     * and {@link ListLiteralNode#reportUpdatedCapacity}. Thus, there's a chance that the compiled
     * code will only see lists of the correct size and storage type.
     */
    @GenerateUncached
    public abstract static class AppendNode extends PNodeWithContext {

        public abstract void execute(PList list, Object value);

        protected static boolean[] flagContainer(boolean flag) {
            boolean[] container = new boolean[1];
            container[0] = flag;
            return container;
        }

        @Specialization
        public void appendObjectGeneric(PList list, Object value,
                        @Cached SequenceStorageNodes.AppendNode appendInInterpreterNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached(value = "flagContainer(false)", uncached = "flagContainer(true)", dimensions = 1) boolean[] triedToCompile,
                        @Cached BranchProfile updateStoreProfile) {
            if (CompilerDirectives.inInterpreter() && !triedToCompile[0] && list.getOrigin() != null) {
                SequenceStorage newStore = appendInInterpreterNode.execute(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                list.setSequenceStorage(newStore);
                if (list.getOrigin() != null && newStore instanceof BasicSequenceStorage) {
                    list.getOrigin().reportUpdatedCapacity((BasicSequenceStorage) newStore);
                }
            } else {
                if (!triedToCompile[0]) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    triedToCompile[0] = true;
                }
                SequenceStorage newStore = appendNode.execute(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                if (list.getSequenceStorage() != newStore) {
                    updateStoreProfile.enter();
                    list.setSequenceStorage(newStore);
                }
            }
        }

        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }
}
