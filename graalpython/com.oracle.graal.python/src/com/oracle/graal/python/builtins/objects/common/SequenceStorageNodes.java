package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;
import java.util.function.BiFunction;

import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CastToByteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CmpNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ContainsNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NormalizeIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.datamodel.IsIndexNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.CharSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class SequenceStorageNodes {

    abstract static class SequenceStorageBaseNode extends PBaseNode {

        protected static boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ElementType.BYTE;
        }

        protected static boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
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

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        protected boolean isEmpty(SequenceStorage left) {
            // TODO use a node or profile
            return left instanceof EmptySequenceStorage || left.length() == 0;
        }
    }

    public abstract static class GetItemNode extends PBaseNode {

        private static final String KEY_TYPE_ERROR_MESSAGE = "indices must be integers or slices, not %p";

        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private CastToIndexNode castToIndexNode;

        private final String keyTypeErrorMessage;
        private final BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            this.normalizeIndexNode = normalizeIndexNode;
            this.keyTypeErrorMessage = keyTypeErrorMessage;
            this.factoryMethod = factoryMethod;
        }

        public abstract Object execute(SequenceStorage s, Object key);

        public abstract Object execute(SequenceStorage s, int key);

        public abstract Object execute(SequenceStorage s, long key);

        public abstract int executeInt(SequenceStorage s, int key);

        public abstract long executeLong(SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarLong(SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarPInt(SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage.length()));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarPInt(SequenceStorage storage, Object idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(idx, storage.length()));
        }

        @Specialization
        protected Object doSlice(SequenceStorage storage, PSlice slice) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            if (factoryMethod != null) {
                return factoryMethod.apply(getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length), factory());
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }

        @Fallback
        protected Object doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            throw raise(TypeError, keyTypeErrorMessage, key);
        }

        protected boolean isPSlice(Object obj) {
            return obj instanceof PSlice;
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

        private CastToIndexNode getCastToIndexNode() {
            if (castToIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIndexNode = insert(CastToIndexNode.create());
            }
            return castToIndexNode;
        }

        private int normalizeIndex(Object idx, int length) {
            int intIdx = getCastToIndexNode().execute(idx);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, length);
            }
            return intIdx;
        }

        private int normalizeIndex(int idx, int length) {
            int intIdx = getCastToIndexNode().execute(idx);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, length);
            }
            return intIdx;
        }

        private int normalizeIndex(long idx, int length) {
            int intIdx = getCastToIndexNode().execute(idx);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, length);
            }
            return intIdx;
        }

        public static GetItemNode createNotNormalized() {
            return GetItemNodeGen.create(null, KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return GetItemNodeGen.create(normalizeIndexNode, KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), KEY_TYPE_ERROR_MESSAGE, null);
        }

        public static GetItemNode createNotNormalized(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(null, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, factoryMethod);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, KEY_TYPE_ERROR_MESSAGE, factoryMethod);
        }

    }

    abstract static class GetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node readNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public abstract boolean executeBoolean(SequenceStorage s, int idx);

        public abstract byte executeByte(SequenceStorage s, int idx);

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected boolean doBoolean(BoolSequenceStorage storage, int idx) {
            return storage.getBoolItemNormalized(idx);
        }

        @Specialization
        protected int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected char doChar(CharSequenceStorage storage, int idx) {
            return storage.getCharItemNormalized(idx);
        }

        @Specialization
        protected int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected PList doObject(ListSequenceStorage storage, int idx) {
            return storage.getListItemNormalized(idx);
        }

        @Specialization
        protected PTuple doObject(TupleSequenceStorage storage, int idx) {
            return storage.getPTupleItemNormalized(idx);
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
        protected int doNativeByte(NativeSequenceStorage storage, int idx) {
            Object result = doNative(storage, idx);
            return (byte) result & 0xFF;
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

    }

    @ImportStatic(ElementType.class)
    abstract static class GetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract SequenceStorage execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization
        @SuppressWarnings("unused")
        protected EmptySequenceStorage doEmpty(EmptySequenceStorage storage, int start, int stop, int step, int length) {
            return EmptySequenceStorage.INSTANCE;
        }

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

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }
    }

    public abstract static class SetItemNode extends PBaseNode {
        @Child private SetItemScalarNode setItemScalarNode;
        @Child private SetItemSliceNode setItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public SetItemNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        public abstract void execute(SequenceStorage s, Object key, Object value);

        public abstract void execute(SequenceStorage s, int key, Object value);

        public abstract void execute(SequenceStorage s, long key, Object value);

        @Specialization
        protected void doScalarInt(SequenceStorage storage, int idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarLong(SequenceStorage storage, long idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarPInt(SequenceStorage storage, PInt idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice, PSequence value) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            getSetItemSliceNode().execute(storage, info, value);
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

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return SetItemNodeGen.create(normalizeIndexNode);
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create(NormalizeIndexNode.create());
        }

    }

    abstract static class SetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node writeNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;
        @Child private CastToByteNode castToByteNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract void execute(SequenceStorage s, int idx, Object value);

        @Specialization
        protected void doByte(ByteSequenceStorage storage, int idx, Object value) {
            storage.setByteItemNormalized(idx, getCastToByteNode().execute(value));
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

        @Specialization(guards = "isByteStorage(storage)")
        protected void doNativeByte(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, getCastToByteNode().execute(value));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization
        protected void doNative(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, verifyValue(storage, value));
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

        private CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(CastToByteNode.create());
            }
            return castToByteNode;
        }

        private Object verifyValue(NativeSequenceStorage storage, Object item) {
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
                throw raise(TypeError, "%s is required, was %p", storage.getElementType(), item);
            }
            return item;
        }

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }
    }

    @ImportStatic(ElementType.class)
    public abstract static class SetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, SliceInfo info, Object iterable);

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") SequenceStorage s, @SuppressWarnings("unused") SliceInfo info, @SuppressWarnings("unused") Object iterable) {
            throw new UnsupportedOperationException();
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
                callNode = insert(Message.EXECUTE.createNode());
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
                throw raiseByteRangeError();
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
                throw raiseByteRangeError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doPInt(PInt value) {
            return PInt.byteValueExact(value.longValueExact());
        }

        @Specialization(replaces = "doPInt")
        protected byte doPIntOvf(PInt value) {
            try {
                return PInt.byteValueExact(value.longValueExact());
            } catch (ArithmeticException e) {
                throw raiseByteRangeError();
            }
        }

        @Specialization
        protected byte doBoolean(boolean value) {
            return value ? (byte) 1 : (byte) 0;
        }

        @Fallback
        protected byte doGeneric(@SuppressWarnings("unused") Object val) {
            throw raise(TypeError, "an integer is required");
        }

        private PException raiseByteRangeError() {
            throw raise(ValueError, "byte must be in range(0, 256)");
        }

        public static CastToByteNode create() {
            return CastToByteNodeGen.create();
        }

    }

    protected abstract static class BinCmpOp {
        protected abstract boolean cmp(int l, int r);

        protected abstract boolean cmp(long l, long r);

        protected abstract boolean cmp(char l, char r);

        protected abstract boolean cmp(byte l, byte r);

        protected abstract boolean cmp(double l, double r);

        protected abstract boolean cmpLen(int l, int r);

        protected abstract BinaryComparisonNode createBinaryComparisonNode();
    }

    private static final class Le extends BinCmpOp {
        private static final Le INSTANCE = new Le();

        @Override
        protected boolean cmp(int l, int r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l <= r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l <= r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__LE__, __GE__, "<=");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l <= r;
        }

    }

    private static final class Lt extends BinCmpOp {

        private static final Lt INSTANCE = new Lt();

        @Override
        protected boolean cmp(int l, int r) {
            return l < r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l < r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l < r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l < r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l < r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__LT__, __GT__, "<");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l < r;
        }

    }

    private static final class Ge extends BinCmpOp {

        private static final Ge INSTANCE = new Ge();

        @Override
        protected boolean cmp(int l, int r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l >= r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l >= r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__GE__, __LE__, ">=");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l >= r;
        }

    }

    private static final class Gt extends BinCmpOp {

        private static final Gt INSTANCE = new Gt();

        @Override
        protected boolean cmp(int l, int r) {
            return l > r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l > r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l > r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l > r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l > r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__GT__, __LT__, ">");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l > r;
        }

    }

    private static final class Eq extends BinCmpOp {

        private static final Eq INSTANCE = new Eq();

        @Override
        protected boolean cmp(int l, int r) {
            return l == r;
        }

        @Override
        protected boolean cmp(long l, long r) {
            return l == r;
        }

        @Override
        protected boolean cmp(char l, char r) {
            return l == r;
        }

        @Override
        protected boolean cmp(byte l, byte r) {
            return l == r;
        }

        @Override
        protected boolean cmp(double l, double r) {
            return l == r;
        }

        @Override
        protected BinaryComparisonNode createBinaryComparisonNode() {
            return BinaryComparisonNode.create(__EQ__, __EQ__, "==");
        }

        @Override
        protected boolean cmpLen(int l, int r) {
            return l == r;
        }

    }

    public abstract static class CmpNode extends SequenceStorageBaseNode {
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private BinaryComparisonNode eqNode;
        @Child private BinaryComparisonNode comparisonNode;
        @Child private CastToBooleanNode castToBooleanNode;

        private final BinCmpOp cmpOp;

        protected CmpNode(BinCmpOp cmpOp) {
            this.cmpOp = cmpOp;
        }

        public abstract boolean execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return cmpOp.cmp(0, 0);
        }

        @Specialization
        boolean doBoolStorage(BoolSequenceStorage left, BoolSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = PInt.intValue(left.getBoolItemNormalized(i));
                int ritem = PInt.intValue(right.getBoolItemNormalized(i));
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doByteStorage(ByteSequenceStorage left, ByteSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = left.getIntItemNormalized(i);
                int ritem = right.getIntItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doCharStorage(CharSequenceStorage left, CharSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                char litem = left.getCharItemNormalized(i);
                char ritem = right.getCharItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doIntStorage(IntSequenceStorage left, IntSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = left.getIntItemNormalized(i);
                int ritem = right.getIntItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doLongStorage(LongSequenceStorage left, LongSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                long litem = left.getLongItemNormalized(i);
                long ritem = right.getLongItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doDoubleStorage(DoubleSequenceStorage left, DoubleSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                double litem = left.getDoubleItemNormalized(i);
                double ritem = right.getDoubleItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Fallback
        boolean doGeneric(SequenceStorage left, SequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                if (!eq(leftItem, rightItem)) {
                    return cmpGeneric(leftItem, rightItem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        private boolean eq(Object left, Object right) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return castToBoolean(eqNode.executeWith(left, right));
        }

        private boolean cmpGeneric(Object left, Object right) {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(cmpOp.createBinaryComparisonNode());
            }
            return castToBoolean(comparisonNode.executeWith(left, right));
        }

        private boolean castToBoolean(Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CastToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeWith(value);
        }

        public static CmpNode createLe() {
            return CmpNodeGen.create(Le.INSTANCE);
        }

        public static CmpNode createLt() {
            return CmpNodeGen.create(Lt.INSTANCE);
        }

        public static CmpNode createGe() {
            return CmpNodeGen.create(Ge.INSTANCE);
        }

        public static CmpNode createGt() {
            return CmpNodeGen.create(Gt.INSTANCE);
        }

        public static CmpNode createEq() {
            return CmpNodeGen.create(Eq.INSTANCE);
        }
    }

    public abstract static class NormalizeIndexNode extends PBaseNode {
        public static final String INDEX_OUT_OF_BOUNDS = "index out of range";
        public static final String RANGE_OUT_OF_BOUNDS = "range index out of range";
        public static final String TUPLE_OUT_OF_BOUNDS = "tuple index out of range";
        public static final String TUPLE_ASSIGN_OUT_OF_BOUNDS = "tuple assignment index out of range";
        public static final String LIST_OUT_OF_BOUNDS = "list index out of range";
        public static final String LIST_ASSIGN_OUT_OF_BOUNDS = "list assignment index out of range";
        public static final String ARRAY_OUT_OF_BOUNDS = "array index out of range";
        public static final String ARRAY_ASSIGN_OUT_OF_BOUNDS = "array assignment index out of range";
        public static final String BYTEARRAY_OUT_OF_BOUNDS = "bytearray index out of range";

        private final String errorMessage;
        private final boolean boundsCheck;
        private final ConditionProfile negativeIndexProfile = ConditionProfile.createBinaryProfile();

        @CompilationFinal private ConditionProfile outOfBoundsProfile;

        public NormalizeIndexNode(String errorMessage, boolean boundsCheck) {
            this.errorMessage = errorMessage;
            this.boundsCheck = boundsCheck;
        }

        public abstract int execute(Object index, int length);

        @Specialization
        int doInt(int index, int length) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            doBoundsCheck(idx, length);
            return idx;
        }

        @Specialization
        int doBool(boolean index, int length) {
            int idx = PInt.intValue(index);
            doBoundsCheck(idx, length);
            return idx;
        }

        private void doBoundsCheck(int idx, int length) {
            if (boundsCheck) {
                if (outOfBoundsProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    outOfBoundsProfile = ConditionProfile.createBinaryProfile();
                }
                if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                    throw raise(IndexError, errorMessage);
                }
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLong(long index, int length) {
            int idx = PInt.intValueExact(index);
            return doInt(idx, length);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length) {
            try {
                return doLong(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt index, int length) {
            int idx = index.intValueExact();
            return doInt(idx, length);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length) {
            try {
                return doPInt(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        public static NormalizeIndexNode create() {
            return create(INDEX_OUT_OF_BOUNDS, true);
        }

        public static NormalizeIndexNode create(String errorMessage) {
            return NormalizeIndexNodeGen.create(errorMessage, true);
        }

        public static NormalizeIndexNode create(boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(INDEX_OUT_OF_BOUNDS, boundsCheck);
        }

        public static NormalizeIndexNode create(String errorMessage, boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(errorMessage, boundsCheck);
        }

        public static NormalizeIndexNode forList() {
            return create(LIST_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forListAssign() {
            return create(LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTuple() {
            return create(TUPLE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTupleAssign() {
            return create(TUPLE_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArray() {
            return create(ARRAY_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArrayAssign() {
            return create(ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forRange() {
            return create(RANGE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forBytearray() {
            return create(BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    public abstract static class ToByteArrayNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;

        private final boolean exact;

        public ToByteArrayNode(boolean exact) {
            this.exact = exact;
        }

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            byte[] barr = s.getInternalByteArray();
            if (exact) {
                return exactCopy(barr, s.length());
            }
            return barr;

        }

        @Specialization(guards = "isByteStorage(s)")
        byte[] doNativeByte(NativeSequenceStorage s) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getGetItemNode().executeInt(s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Fallback
        byte[] doFallback(@SuppressWarnings("unused") SequenceStorage s) {
            throw raise(TypeError, "expected a bytes-like object");
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] exactCopy(byte[] barr, int len) {
            return Arrays.copyOf(barr, len);
        }

        protected GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        public static ToByteArrayNode create() {
            return ToByteArrayNodeGen.create(true);
        }

        public static ToByteArrayNode create(boolean exact) {
            return ToByteArrayNodeGen.create(exact);
        }
    }

    public abstract static class ConcatNode extends SequenceStorageBaseNode {
        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = "!isNative(right)")
        SequenceStorage doLeftEmpty(@SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            try {
                return storageTypeProfile.profile(right).copy();
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = "!isNative(left)")
        SequenceStorage doRightEmpty(SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            try {
                return storageTypeProfile.profile(left).copy();
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = {"left.getClass() == right.getClass()", "!isNative(left)", "cachedClass == left.getClass()"})
        SequenceStorage doManagedManagedSameType(SequenceStorage left, SequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            // TODO, there should be better specialization, which will check,
            // whether there is enough free memory. If not, then fire OOM.
            // The reason is that GC is trying to find out enough space for arrays
            // that can fit to the free memory, but at the end there is no conti-
            // nual space for such big array and this can takes looong time (a few mimutes).
            try {
                SequenceStorage leftProfiled = cachedClass.cast(left);
                SequenceStorage rightProfiled = cachedClass.cast(right);
                Object arr1 = leftProfiled.getInternalArrayObject();
                int len1 = leftProfiled.length();
                Object arr2 = rightProfiled.getInternalArrayObject();
                int len2 = rightProfiled.length();
                SequenceStorage dest = leftProfiled.createEmpty(Math.addExact(len1, len2));
                concat(dest.getInternalArrayObject(), arr1, len1, arr2, len2);
                dest.setNewLength(len1 + len2);
                return dest;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = {"!isNative(left)", "compatible(left, right)"})
        SequenceStorage doManagedNative(SequenceStorage left, NativeSequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile) {
            try {
                int len1 = left.length();
                SequenceStorage dest = left.createEmpty(Math.addExact(len1, right.length()));
                for (int i = 0; i < len1; i++) {
                    getSetItemNode().execute(dest, i, getGetItemNode().execute(left, i));
                }
                for (int i = 0; i < right.length(); i++) {
                    getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(right, i));
                }
                return dest;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = {"!isNative(right)", "compatible(right, left)"})
        SequenceStorage doNatveManaged(NativeSequenceStorage left, SequenceStorage right,
                        @Cached("create()") BranchProfile outOfMemProfile) {
            try {
                int len1 = left.length();
                SequenceStorage dest = right.createEmpty(Math.addExact(len1, right.length()));
                for (int i = 0; i < len1; i++) {
                    getSetItemNode().execute(dest, i, getGetItemNode().execute(left, i));
                }
                for (int i = 0; i < right.length(); i++) {
                    getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(right, i));
                }
                return dest;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization
        SequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            // TODO complete
            throw raise(TypeError, "cannot concatenate sequences");
        }

        private SetItemScalarNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemScalarNode.create());
            }
            return setItemNode;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static void concat(Object dest, Object arr1, int len1, Object arr2, int len2) {
            System.arraycopy(arr1, 0, dest, 0, len1);
            System.arraycopy(arr2, 0, dest, len1, len2);
        }

        public static ConcatNode create() {
            return ConcatNodeGen.create();
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private IsIndexNode isIndexNode;
        @Child private CastToIndexNode castToindexNode;
        @Child private RepeatNode recursive;

        public abstract SequenceStorage execute(SequenceStorage left, Object times);

        public abstract SequenceStorage execute(SequenceStorage left, int times);

        @Specialization(guards = "times <= 0")
        SequenceStorage doGeneric(SequenceStorage s, @SuppressWarnings("unused") int times,
                        @Cached("createClassProfile()") ValueProfile storageTypeProfile) {
            return storageTypeProfile.profile(s).createEmpty(0);
        }

        @Specialization(limit = "2", guards = {"!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doManaged(SequenceStorage s, int times,
                        @Cached("create()") BranchProfile outOfMemProfile,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            try {
                SequenceStorage profiled = cachedClass.cast(s);
                Object arr1 = profiled.getInternalArrayObject();
                int len = profiled.length();
                SequenceStorage repeated = profiled.createEmpty(Math.multiplyExact(len, times));
                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, arr1, len, times);
                repeated.setNewLength(len * times);
                return repeated;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Specialization(replaces = "doManaged")
        SequenceStorage doGeneric(SequenceStorage s, int times,
                        @Cached("create()") BranchProfile outOfMemProfile) {
            try {
                int len = s.length();

                ObjectSequenceStorage repeated = new ObjectSequenceStorage(Math.multiplyExact(len, times));

                // TODO avoid temporary array
                Object[] values = new Object[len];
                for (int i = 0; i < len; i++) {
                    values[i] = getGetItemNode().execute(s, i);
                }

                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, values, len, times);
                return repeated;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemProfile.enter();
                throw raise(MemoryError);
            }
        }

        @Fallback
        SequenceStorage doGeneric(SequenceStorage s, Object times) {
            int i = toIndex(times);
            if (recursive == null) {
                recursive = insert(RepeatNodeGen.create());
            }
            return recursive.execute(s, i);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static void repeat(Object dest, Object src, int len, int times) {
            for (int i = 0; i < times; i++) {
                System.arraycopy(src, 0, dest, i * len, len);
            }
        }

        private int toIndex(Object times) {
            if (isIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isIndexNode = insert(IsIndexNode.create());
            }
            if (isIndexNode.execute(times)) {
                if (castToindexNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    castToindexNode = insert(CastToIndexNode.create());
                }
                return castToindexNode.execute(times);
            }
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", times);
        }

        public static RepeatNode create() {
            return RepeatNodeGen.create();
        }
    }

    public abstract static class ContainsNode extends SequenceStorageBaseNode {
        @Child private GetItemScalarNode getItemNode;
        @Child private BinaryComparisonNode equalsNode;
        @Child private CastToBooleanNode castToBooleanNode;

        public abstract boolean execute(SequenceStorage left, Object item);

        @Specialization(guards = "isEmpty(left)")
        @SuppressWarnings("unused")
        boolean doEmpty(SequenceStorage left, Object item) {
            return false;
        }

        @Specialization
        public boolean doByteStorage(ByteSequenceStorage s, int item) {
            return s.indexOfInt(item) != -1;
        }

        @Specialization
        public boolean doIntStorage(IntSequenceStorage s, int item) {
            return s.indexOfInt(item) != -1;
        }

        @Specialization
        public boolean doLongStorage(LongSequenceStorage s, long item) {
            return s.indexOfLong(item) != -1;
        }

        @Specialization
        public boolean doDoubleStorage(DoubleSequenceStorage s, double item) {
            return s.indexOfDouble(item) != -1;
        }

        @Specialization
        boolean doGeneric(SequenceStorage left, Object item) {
            for (int i = 0; i < left.length(); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                if (eq(leftItem, item)) {
                    return true;
                }
            }
            return false;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private boolean eq(Object left, Object right) {
            if (equalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalsNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return castToBoolean(equalsNode.executeWith(left, right));
        }

        private boolean castToBoolean(Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CastToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeWith(value);
        }

        public static ContainsNode create() {
            return ContainsNodeGen.create();
        }
    }

}
