package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage.ElementType;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class SequenceStorageNodes {

    public abstract static class GetItemNode extends PBaseNode {
        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;

        public abstract Object execute(SequenceStorage s, Object key);

        @Specialization
        protected Object doScalarInt(IntSequenceStorage storage, int idx) {
            return getGetItemScalarNode().executeInt(storage, idx);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected Object doScalarLong(IntSequenceStorage storage, long idx) {
            return getGetItemScalarNode().executeInt(storage, PInt.intValueExact(idx));
        }

        @Specialization(replaces = "doScalarLong")
        protected Object doScalarLongOvf(IntSequenceStorage storage, long idx) {
            try {
                return getGetItemScalarNode().executeInt(storage, PInt.intValueExact(idx));
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected Object doScalarPInt(IntSequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().executeInt(storage, idx.intValueExact());
        }

        @Specialization(replaces = "doScalarPInt")
        protected Object doScalarPIntOvf(IntSequenceStorage storage, PInt idx) {
            try {
                return getGetItemScalarNode().executeInt(storage, idx.intValueExact());
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization
        protected Object doSlice(SequenceStorage storage, PSlice slice) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            return getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length);
        }

        private GetItemScalarNode getGetItemScalarNode() {
            if (getItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemScalarNode = insert(GetItemScalarNode.create());
            }
            return getItemScalarNode;
        }

        private GetItemSliceNode getGetItemSliceNode() {
            if (getItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemSliceNode = insert(GetItemSliceNode.create());
            }
            return getItemSliceNode;
        }

    }

    public abstract static class GetItemScalarNode extends PBaseNode {

        @Child private Node readNode;

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        protected long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected Object doObject(ObjectSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization
        protected Object doNative(NativeSequenceStorage storage, int idx) {
            try {
                return verifyResult(storage, ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Object verifyResult(NativeSequenceStorage storage, Object sendRead) {
            // TODO Auto-generated method stub
            return null;
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

    }

    public abstract static class GetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }

        @Specialization
        protected IntSequenceStorage doInt(IntSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        protected LongSequenceStorage doLong(LongSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected DoubleSequenceStorage doDouble(DoubleSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected ObjectSequenceStorage doObject(ObjectSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected NativeSequenceStorage doNative(NativeSequenceStorage storage, int start, int stop, int step, int length) {
            // TODO
            Object[] newArray = new Object[length];

            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = values[i];
            }

            return new ObjectSequenceStorage(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, )
            try {
                Object newPtr = ForeignAccess.sendExecute(getExecuteNode(), (TruffleObject) storage.getPtr(), start, stop, step, length);
                return new NativeSequenceStorage(newPtr, length, length, storage.getElementType());
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

        private Node getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.createExecute(5).createNode());
            }
            return executeNode;
        }

    }

    abstract static class VerifyNativeItemNode extends PBaseNode {

        public abstract boolean execute(ElementType expectedType, Object item);

        @Specialization(guards = "elementType == cachedElementType", limit = "1")
        boolean doCached(@SuppressWarnings("unused") ElementType elementType, Object item,
                        @Cached("elementType") ElementType cachedElementType) {
            return doGeneric(cachedElementType, item);
        }

        @Specialization(replaces = "doCached")
        boolean doGeneric(ElementType expectedType, Object item) {
            switch (expectedType) {
                case BYTE:
                    return item instanceof Byte;
                case INT:
                    return item instanceof Integer;
                case LONG:
                    return item instanceof Long;
                case DOUBLE:
                    return item instanceof Double;
                case OBJECT:
                    return !(item instanceof Byte || item instanceof Integer || item instanceof Long || item instanceof Double);
            }
            return false;
        }

    }

}
