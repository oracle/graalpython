package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CastToByteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.EqNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil.NormalizeIndexNode;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage.ElementType;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class SequenceStorageNodes {

    public abstract static class GetItemNode extends PBaseNode {
        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public abstract Object execute(SequenceStorage s, Object key);

        public abstract Object executeInt(SequenceStorage s, int key);

        public abstract Object executeLong(SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarLong(SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarPInt(SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()));
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

        private NormalizeIndexNode getNormalizeIndexNode() {
            if (normalizeIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                normalizeIndexNode = insert(NormalizeIndexNode.create());
            }
            return normalizeIndexNode;
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create();
        }

    }

    public abstract static class GetItemScalarNode extends PBaseNode {

        @Child private Node readNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

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

        @Specialization(guards = "!isByteStorage(storage)")
        protected Object doNative(NativeSequenceStorage storage, int idx) {
            try {
                return verifyResult(storage, ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected Object doNativeByte(NativeSequenceStorage storage, int idx) {
            Object result = doNative(storage, idx);
            return (int) ((byte) result);
        }

        private Object verifyResult(NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verifyNativeItemNode = insert(VerifyNativeItemNode.create());
            }
            if (invalidTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidTypeProfile = BranchProfile.create();
            }
            if (!verifyNativeItemNode.execute(storage.getElementType(), item)) {
                invalidTypeProfile.enter();
                throw raise(SystemError, "Invalid item type %s returned from native sequence storage (expected: %s)", item, storage.getElementType());
            }
            return item;
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

        protected boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ElementType.BYTE;
        }

    }

    @ImportStatic(ElementType.class)
    public abstract static class GetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization(limit = "5", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "storage.getElementType() == BYTE")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == INT")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == LONG")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == DOUBLE")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == OBJECT")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, int idx) {
            try {
                return ForeignAccess.sendRead(getReadNode(), ptr, idx);
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

// private Node getExecuteNode() {
// if (executeNode == null) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// executeNode = insert(Message.createExecute(5).createNode());
// }
// return executeNode;
// }

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }
    }

    public abstract static class SetItemNode extends PBaseNode {
        @Child private SetItemScalarNode setItemScalarNode;
        @Child private SetItemSliceNode setItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public abstract void execute(SequenceStorage s, Object key, Object value);

        public abstract void executeInt(SequenceStorage s, int key, Object value);

        public abstract void executeLong(SequenceStorage s, long key, Object value);

        @Specialization
        protected void doScalarInt(SequenceStorage storage, int idx, Object value) {
            getSetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarLong(SequenceStorage storage, long idx, Object value) {
            getSetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarPInt(SequenceStorage storage, PInt idx, Object value) {
            getSetItemScalarNode().execute(storage, getNormalizeIndexNode().forGeneric(idx, storage.length()), value);
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice, PSequence value) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            getSetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length);
        }

        private SetItemScalarNode getSetItemScalarNode() {
            if (setItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemScalarNode = insert(SetItemScalarNode.create());
            }
            return setItemScalarNode;
        }

        private SetItemSliceNode getSetItemSliceNode() {
            if (setItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // TODO
                // setItemSliceNode = insert(SetItemSliceNode.create());
            }
            return setItemSliceNode;
        }

        private NormalizeIndexNode getNormalizeIndexNode() {
            if (normalizeIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                normalizeIndexNode = insert(NormalizeIndexNode.create());
            }
            return normalizeIndexNode;
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }

    }

    public abstract static class SetItemScalarNode extends PBaseNode {

        @Child private Node writeNode;

        public abstract void execute(SequenceStorage s, int idx, Object value);

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }

        @Specialization
        protected void doByte(ByteSequenceStorage storage, int idx, Object value,
                        @Cached("create()") CastToByteNode castToByteNode) {
            storage.setByteItemNormalized(idx, castToByteNode.execute(value));
        }

        @Specialization
        protected void doInt(IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        protected void doLong(LongSequenceStorage storage, int idx, long value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization
        protected void doDouble(DoubleSequenceStorage storage, int idx, double value) {
            storage.setDoubleItemNormalized(idx, value);
        }

        @Specialization
        protected void doObject(ObjectSequenceStorage storage, int idx, Object value) {
            storage.setItemNormalized(idx, value);
        }

        @Specialization
        protected void doNative(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, value);
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getWriteNode() {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(Message.WRITE.createNode());
            }
            return writeNode;
        }

    }

    @ImportStatic(ElementType.class)
    public abstract static class SetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization(limit = "5", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "storage.getElementType() == BYTE")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == INT")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == LONG")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == DOUBLE")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == OBJECT")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, int idx) {
            try {
                return ForeignAccess.sendRead(getReadNode(), ptr, idx);
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

        public static SetItemSliceNode create() {
            return SetItemSliceNodeGen.create();
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

        public static VerifyNativeItemNode create() {
            return VerifyNativeItemNodeGen.create();
        }

    }

    @ImportStatic(NativeCAPISymbols.class)
    public abstract static class StorageToNativeNode extends PBaseNode {
        @Child private Node executeNode;

        public abstract NativeSequenceStorage execute(Object obj);

        @Specialization
        NativeSequenceStorage doByte(byte[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.BYTE);
        }

        @Specialization
        NativeSequenceStorage doInt(int[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.INT);
        }

        @Specialization
        NativeSequenceStorage doLong(long[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.LONG);
        }

        @Specialization
        NativeSequenceStorage doDouble(double[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.DOUBLE);
        }

        @Specialization
        NativeSequenceStorage doObject(Object[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.OBJECT);
        }

        public static StorageToNativeNode create() {
            return StorageToNativeNodeGen.create();
        }
    }

    public static class PCallBinaryCapiFunction extends PBaseNode {

        @Child private Node callNode;

        private final String name;
        private final BranchProfile profile = BranchProfile.create();

        @CompilationFinal TruffleObject receiver;

        public PCallBinaryCapiFunction(String name) {
            this.name = name;
        }

        public Object execute(Object arg0, Object arg1) {
            try {
                return ForeignAccess.sendExecute(getCallNode(), getFunction(), arg0, arg1);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                profile.enter();
                throw e.raise();
            }
        }

        private Node getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(Message.createExecute(2).createNode());
            }
            return callNode;
        }

        private TruffleObject getFunction() {
            if (receiver == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                receiver = (TruffleObject) getContext().getEnv().importSymbol(name);
            }
            return receiver;
        }

        public static PCallBinaryCapiFunction create(String name) {
            return new PCallBinaryCapiFunction(name);
        }
    }

    public abstract static class CastToByteNode extends PBaseNode {

        public abstract byte execute(Object val);

        @Specialization
        protected byte doByte(byte value) {
            return value;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doInt(int value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doInt")
        protected byte doIntOvf(int value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doLong(long value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doLong")
        protected byte doLongOvf(long value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doPInt(PInt value) {
            return value.byteValueExact();
        }

        @Specialization(replaces = "doPInt")
        protected byte doPIntOvf(PInt value) {
            try {
                return value.byteValueExact();
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization
        protected byte doBoolean(boolean value) {
            return value ? (byte) 1 : (byte) 0;
        }

        public static CastToByteNode create() {
            return CastToByteNodeGen.create();
        }

    }

    public abstract static class EqNode extends PBaseNode {
        @Child private GetItemNode getItemNode;
        @Child private GetItemNode getRightItemNode;
        @Child private BinaryComparisonNode equalsNode;

        public abstract boolean execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return true;
        }

        @Specialization(guards = {"left.getClass() == right.getClass()", "!isNative(left)"})
        boolean doManagedManagedSameType(SequenceStorage left, SequenceStorage right) {
            assert !isNative(right);
            return left.equals(right);
        }

        @Specialization(guards = "left.getElementType() == right.getElementType()")
        boolean doNativeNativeSameType(NativeSequenceStorage left, NativeSequenceStorage right) {
            // TODO profile or guard !
            if (left.length() != right.length()) {
                return false;
            }
            for (int i = 0; i < left.length(); i++) {
                // use the same 'getItemNode'
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetItemNode().execute(right, i);
                if (!getEqualsNode().executeBool(leftItem, rightItem)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = {"!isNative(left)", "compatible(left, right)"})
        boolean doManagedNative(SequenceStorage left, NativeSequenceStorage right) {
            // TODO profile or guard !
            if (left.length() != right.length()) {
                return false;
            }
            for (int i = 0; i < left.length(); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                if (!getEqualsNode().executeBool(leftItem, rightItem)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = {"!isNative(right)", "compatible(right, left)"})
        boolean doNatveManaged(NativeSequenceStorage left, SequenceStorage right) {
            return doManagedNative(right, left);
        }

        @Fallback
        boolean doFallback(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return false;
        }

        protected boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
            switch (right.getElementType()) {
                case BYTE:
                    return left instanceof ByteSequenceStorage;
                case INT:
                    return left instanceof IntSequenceStorage;
                case LONG:
                    return left instanceof LongSequenceStorage;
                case DOUBLE:
                    return left instanceof DoubleSequenceStorage;
                case OBJECT:
                    return left instanceof ObjectSequenceStorage || left instanceof TupleSequenceStorage || left instanceof ListSequenceStorage;
            }
            assert false : "should not reach";
            return false;
        }

        protected boolean isEmpty(SequenceStorage left) {
            // TODO use a node
            return left.length() == 0;
        }

        protected boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        private GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.create());
            }
            return getItemNode;
        }

        private GetItemNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemNode.create());
            }
            return getRightItemNode;
        }

        private BinaryComparisonNode getEqualsNode() {
            if (equalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalsNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return equalsNode;
        }

        public static EqNode create() {
            return EqNodeGen.create();
        }

    }

}
