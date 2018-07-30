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
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
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
                return ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx);
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

    }

    public abstract static class GetItemSliceNode extends PBaseNode {

        @Child private Node readNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }

        @Specialization
        protected Object doInt(IntSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        protected Object doLong(LongSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected Object doDouble(DoubleSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected Object doObject(ObjectSequenceStorage storage, int start, int stop, int step, int length) {
            return storage.getSliceInBound(start, stop, step, length);
        }

        @Specialization
        protected Object doNative(NativeSequenceStorage storage, int start, int stop, int step, int length) {
            try {
                return ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx);
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

    }

}
