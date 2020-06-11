/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.CreateCodeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithState;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(defineModule = "marshal")
public final class MarshalModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MarshalModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "dump", minNumOfPositionalArgs = 2, parameterNames = {"self", "file", "version"})
    @GenerateNodeFactory
    abstract static class DumpNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doit(Object value, Object file, Object version) {
            throw raise(NotImplementedError, "marshal.dump");
        }
    }

    @Builtin(name = "dumps", minNumOfPositionalArgs = 1, parameterNames = {"self", "version"})
    @GenerateNodeFactory
    abstract static class DumpsNode extends PythonBuiltinNode {

        @Child private MarshallerNode marshaller = MarshallerNode.create();

        private byte[] dump(VirtualFrame frame, Object o, int version) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream buffer = new DataOutputStream(baos);
            marshaller.resetRecursionDepth();
            marshaller.execute(frame, o, version, buffer);
            try {
                return getByteArrayFromStream(baos, buffer);
            } catch (IOException e) {
                throw raise(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, o);
            }
        }

        @TruffleBoundary
        private static byte[] getByteArrayFromStream(ByteArrayOutputStream baos, DataOutputStream buffer) throws IOException {
            buffer.flush();
            byte[] result = baos.toByteArray();
            baos.close();
            buffer.close();
            return result;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object value, int version) {
            return factory().createBytes(dump(frame, value, version));
        }

        @Specialization
        Object doit(VirtualFrame frame, Object value, @SuppressWarnings("unused") PNone version) {
            return factory().createBytes(dump(frame, value, CURRENT_VERSION));
        }
    }

    @Builtin(name = "load", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doit(Object file) {
            throw raise(NotImplementedError, "marshal.load");
        }
    }

    @Builtin(name = "loads", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadsNode extends PythonBuiltinNode {

        @Child private UnmarshallerNode marshaller = UnmarshallerNode.create();

        @SuppressWarnings("unused")
        @Specialization
        Object doit(VirtualFrame frame, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return marshaller.execute(frame, toBytesNode.execute(frame, bytes), CURRENT_VERSION);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object doit(VirtualFrame frame, PByteArray bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return marshaller.execute(frame, toBytesNode.execute(frame, bytes), CURRENT_VERSION);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object doit(VirtualFrame frame, PMemoryView bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return marshaller.execute(frame, toBytesNode.execute(frame, bytes), CURRENT_VERSION);
        }
    }

    private static final char TYPE_NULL = '0';
    private static final char TYPE_NONE = 'N';
    private static final char TYPE_NOVALUE = 'n';
    private static final char TYPE_FALSE = 'F';
    private static final char TYPE_TRUE = 'T';
    private static final char TYPE_STOPITER = 'S';
    private static final char TYPE_ELLIPSIS = '.';
    private static final char TYPE_INT = 'i';
    private static final char TYPE_INT64 = 'I'; // just for backward compatibility with CPython
    private static final char TYPE_FLOAT = 'f';
    // private final static char TYPE_BINARY_FLOAT = 'g';
    private static final char TYPE_COMPLEX = 'x';
    // private final static char TYPE_BINARY_COMPLEX = 'y';
    private static final char TYPE_LONG = 'l';
    private static final char TYPE_PINT = 'L';
    private static final char TYPE_STRING = 's';
    private static final char TYPE_INTERNED = 't';
    // private final static char TYPE_STRINGREF = 'R';
    private static final char TYPE_BYTESLIKE = 'b';
    private static final char TYPE_TUPLE = '(';
    private static final char TYPE_LIST = '[';
    private static final char TYPE_DICT = '{';
    private static final char TYPE_CODE = 'c';
    // private final static char TYPE_UNICODE = 'u';
    private static final char TYPE_UNKNOWN = '?';
    private static final char TYPE_SET = '<';
    private static final char TYPE_FROZENSET = '>';
    private static final int MAX_MARSHAL_STACK_DEPTH = 2000;
    private static final int CURRENT_VERSION = 1;

    static final class InternedString {
        public final String string;

        private InternedString(String string) {
            this.string = string;
        }
    }

    abstract static class MarshallerNode extends PNodeWithState {

        public abstract void execute(VirtualFrame frame, Object x, int version, DataOutputStream buffer);

        @Child private CastToJavaStringNode castStrNode;
        @Child private MarshallerNode recursiveNode;
        private int depth = 0;
        @Child private IsBuiltinClassProfile isBuiltinProfile;

        protected MarshallerNode getRecursiveNode() {
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (this) {
                    recursiveNode = insert(create());
                    recursiveNode.depth += 1;
                }
            }
            return recursiveNode;
        }

        private void handleIOException(Object v) {
            throw raise(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, v);
        }

        public void resetRecursionDepth() {
            depth = 0;
        }

        @Specialization
        @TruffleBoundary
        void writeByte(char v, @SuppressWarnings("unused") int version, DataOutputStream buffer) {
            try {
                buffer.write(v);
            } catch (IOException e) {
                handleIOException(v);
            }
        }

        @TruffleBoundary
        private void writeBytes(byte[] bytes, int version, DataOutputStream buffer) {
            int len = bytes.length;
            writeInt(len, version, buffer);
            try {
                buffer.write(bytes);
            } catch (IOException e) {
                throw raise(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL);
            }
        }

        @TruffleBoundary
        private void writeInt(int v, @SuppressWarnings("unused") int version, DataOutputStream buffer) {
            try {
                buffer.writeInt(v);
            } catch (IOException e) {
                handleIOException(v);
            }
        }

        @Specialization
        void handleInt(int v, int version, DataOutputStream buffer) {
            writeByte(TYPE_INT, version, buffer);
            writeInt(v, version, buffer);
        }

        private void writeLong(long v, int version, DataOutputStream buffer) {
            writeInt((int) (v & 0xffffffff), version, buffer);
            writeInt((int) ((v >> 32) & 0xffffffff), version, buffer);
        }

        @Specialization
        void handleLong(long v, int version, DataOutputStream buffer) {
            writeByte(TYPE_LONG, version, buffer);
            writeLong(v, version, buffer);
        }

        @Specialization
        @TruffleBoundary
        void handlePInt(PInt v, int version, DataOutputStream buffer) {
            writeByte(TYPE_PINT, version, buffer);
            writeBytes(v.getValue().toByteArray(), version, buffer);
        }

        private void writeDouble(double v, int version, DataOutputStream buffer) {
            writeLong(Double.doubleToLongBits(v), version, buffer);
        }

        @Specialization
        void handleFloat(float v, int version, DataOutputStream buffer) {
            handleDouble(v, version, buffer);
        }

        @Specialization
        void handleDouble(double v, int version, DataOutputStream buffer) {
            writeByte(TYPE_FLOAT, version, buffer);
            writeDouble(v, version, buffer);
        }

        @Specialization
        void handlePFloat(PFloat v, int version, DataOutputStream buffer) {
            handleDouble(v.getValue(), version, buffer);
        }

        @Specialization
        void handlePComplex(PComplex v, int version, DataOutputStream buffer) {
            writeByte(TYPE_COMPLEX, version, buffer);
            writeDouble(v.getReal(), version, buffer);
            writeDouble(v.getImag(), version, buffer);
        }

        @Specialization
        void writeBoolean(boolean v, int version, DataOutputStream buffer) {
            if (v) {
                writeByte(TYPE_TRUE, version, buffer);
            } else {
                writeByte(TYPE_FALSE, version, buffer);
            }
        }

        @TruffleBoundary
        private void writeString(String v, int version, DataOutputStream buffer) {
            byte[] bytes = v.getBytes();
            writeInt(bytes.length, version, buffer);
            try {
                buffer.write(bytes);
            } catch (IOException e) {
                handleIOException(v);
            }
        }

        @Specialization
        void handleString(String v, int version, DataOutputStream buffer) {
            writeByte(TYPE_STRING, version, buffer);
            writeString(v, version, buffer);
        }

        @Specialization
        void handlePString(PString v, int version, DataOutputStream buffer) {
            writeByte(TYPE_STRING, version, buffer);
            writeString(v.getValue(), version, buffer);
        }

        @Specialization
        void handleInternedString(InternedString v, int version, DataOutputStream buffer) {
            writeByte(TYPE_INTERNED, version, buffer);
            writeString(v.string, version, buffer);
        }

        @Specialization
        void handleBytesLike(VirtualFrame frame, PIBytesLike v, int version, DataOutputStream buffer,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            writeByte(TYPE_BYTESLIKE, version, buffer);
            writeBytes(toBytesNode.execute(frame, v), version, buffer);
        }

        @Specialization
        void handleMemoryView(VirtualFrame frame, PMemoryView v, int version, DataOutputStream buffer,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            writeByte(TYPE_BYTESLIKE, version, buffer);
            writeBytes(toBytesNode.execute(frame, v), version, buffer);
        }

        @Specialization
        void handlePArray(@SuppressWarnings("unused") PArray v, @SuppressWarnings("unused") int version, @SuppressWarnings("unused") DataOutputStream buffer) {
            throw raise(NotImplementedError, "marshal.dumps(array)");
        }

        @Specialization
        void handlePTuple(VirtualFrame frame, PTuple t, int version, DataOutputStream buffer,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            writeByte(TYPE_TUPLE, version, buffer);
            Object[] items = getObjectArrayNode.execute(t);
            writeInt(items.length, version, buffer);
            for (int i = 0; i < items.length; i++) {
                getRecursiveNode().execute(frame, items[i], version, buffer);
            }
        }

        @Specialization
        void handlePList(VirtualFrame frame, PList l, int version, DataOutputStream buffer) {
            writeByte(TYPE_LIST, version, buffer);
            Object[] items = l.getSequenceStorage().getInternalArray();
            writeInt(items.length, version, buffer);
            for (int i = 0; i < items.length; i++) {
                getRecursiveNode().execute(frame, items[i], version, buffer);
            }
        }

        @Specialization(limit = "1")
        void handlePDict(VirtualFrame frame, PDict d, int version, DataOutputStream buffer,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(d)") HashingStorageLibrary lib) {
            writeByte(TYPE_DICT, version, buffer);
            HashingStorage dictStorage = getStore.execute(d);
            int len = lib.lengthWithFrame(dictStorage, hasFrame, frame);
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(frame, entry.key, version, buffer);
                getRecursiveNode().execute(frame, entry.value, version, buffer);
            }
        }

        @Specialization
        void handlePCode(@SuppressWarnings("unused") VirtualFrame frame, PCode c, int version, DataOutputStream buffer) {
            writeByte(TYPE_CODE, version, buffer);
            writeString(getSourceCode(c), version, buffer);
            writeInt(c.getFlags(), version, buffer);
            byte[] code = c.getCodestring();
            writeBytes(code == null ? new byte[0] : code, version, buffer);
            writeString(c.getFilename(), version, buffer);
            writeInt(c.getFirstLineNo(), version, buffer);
            writeBytes(c.getLnotab() == null ? new byte[0] : c.getLnotab(), version, buffer);
        }

        @TruffleBoundary
        private static String getSourceCode(PCode c) {
            SourceSection sourceSection = c.getRootNode().getSourceSection();
            return sourceSection.getCharacters().toString();
        }

        @Specialization(limit = "1")
        void handlePSet(VirtualFrame frame, PSet s, int version, DataOutputStream buffer,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(s)") HashingStorageLibrary lib) {
            writeByte(TYPE_SET, version, buffer);
            int len;
            HashingStorage dictStorage = getStore.execute(s);
            if (hasFrame.profile(frame != null)) {
                len = lib.lengthWithState(dictStorage, PArguments.getThreadState(frame));
            } else {
                len = lib.length(dictStorage);
            }
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(frame, entry.key, version, buffer);
            }
        }

        @Specialization(limit = "1")
        void handlePForzenSet(VirtualFrame frame, PFrozenSet s, int version, DataOutputStream buffer,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(s)") HashingStorageLibrary lib) {
            writeByte(TYPE_FROZENSET, version, buffer);
            int len;
            HashingStorage dictStorage = getStore.execute(s);
            if (hasFrame.profile(frame != null)) {
                len = lib.lengthWithState(dictStorage, PArguments.getThreadState(frame));
            } else {
                len = lib.length(dictStorage);
            }
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(frame, entry.key, version, buffer);
            }
        }

        @Specialization
        void handlePNone(PNone v, int version, DataOutputStream buffer) {
            if (v == PNone.NONE) {
                writeByte(TYPE_NONE, version, buffer);
            } else if (v == PNone.NO_VALUE) {
                writeByte(TYPE_NOVALUE, version, buffer);
            }
        }

        @Fallback
        void writeObject(Object v, int version, DataOutputStream buffer) {
            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw raise(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            } else if (v == null) {
                writeByte(TYPE_NULL, version, buffer);
            } else if (v == PNone.NONE) {
                writeByte(TYPE_NONE, version, buffer);
            } else if (v instanceof LazyPythonClass) {
                if (isBuiltinProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isBuiltinProfile = insert(IsBuiltinClassProfile.create());
                }
                if (isBuiltinProfile.profileClass(v, PythonBuiltinClassType.StopIteration)) {
                    writeByte(TYPE_STOPITER, version, buffer);
                } else {
                    writeByte(TYPE_UNKNOWN, version, buffer);
                }
            } else if (v == PythonBuiltinClassType.PEllipsis) {
                writeByte(TYPE_ELLIPSIS, version, buffer);
            } else {
                writeByte(TYPE_UNKNOWN, version, buffer);
            }
            depth--;
        }

        public static MarshallerNode create() {
            return MarshalModuleBuiltinsFactory.MarshallerNodeGen.create();
        }
    }

    public abstract static class UnmarshallerNode extends PNodeWithState implements IndirectCallNode {
        public abstract Object execute(VirtualFrame frame, byte[] dataBytes, int version);

        @Child private CodeNodes.CreateCodeNode createCodeNode;
        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        private int index;
        private byte[] data;

        public void reset() {
            index = 0;
        }

        private int readByte() {
            if (index < data.length) {
                return data[index++];
            } else {
                throw raise(EOFError, "EOF read where not expected");
            }
        }

        private int readInt() {
            int ch1 = readByte() & 0xFF;
            int ch2 = readByte() & 0xFF;
            int ch3 = readByte() & 0xFF;
            int ch4 = readByte() & 0xFF;
            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
        }

        private long read_long64() { // cpython calls this r_long64
            long lo4 = readInt();
            long hi4 = readInt();
            long x = (hi4 << 32) | (lo4 & 0xFFFFFFFFL);
            return x;
        }

        private PInt readPInt() {
            byte[] bytes = readBytes();
            return factory().createInt(new BigInteger(bytes));
        }

        private double readDouble() {
            long n = read_long64();
            return Double.longBitsToDouble(n);
        }

        private String readString() {
            int len = readInt();
            String text = new String(data, index, len);
            index += len;
            return text;
        }

        private String readInternedString() {
            return readString().intern();
        }

        private byte[] readBytes() {
            int len = readInt();
            byte[] bytes = Arrays.copyOfRange(data, index, index + len);
            index += len;
            return bytes;
        }

        private PBytes readBytesLike() {
            byte[] bytes = readBytes();
            return factory().createBytes(bytes);
        }

        private PCode readCode() {
            String codetext = readString();
            int flags = readInt();
            byte[] serializationData = readBytes();
            String filename = readString();
            int firstlineno = readInt();
            byte[] lnotab = readBytes();
            return ensureCreateCodeNode().execute(null, PythonBuiltinClassType.PCode, codetext, flags, serializationData, filename, firstlineno, lnotab);
        }

        private PDict readDict(int depth, HashingStorageLibrary lib) {
            int len = readInt();
            HashingStorage store = PDict.createNewStorage(false, len);
            PDict dict = factory().createDict(store);
            for (int i = 0; i < len; i++) {
                Object key = readObject(depth + 1, lib);
                if (key == null) {
                    break;
                }
                Object value = readObject(depth + 1, lib);
                if (value != null) {
                    store = lib.setItem(store, key, value);
                }
            }
            dict.setDictStorage(store);
            return dict;
        }

        private PList readList(int depth, HashingStorageLibrary lib) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            Object[] items = new Object[n];
            for (int i = 0; i < n; i++) {
                Object item = readObject(depth + 1, lib);
                if (item == null) {
                    throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
                }
                items[i] = item;
            }
            return factory().createList(items);
        }

        private PSet readSet(int depth, HashingStorageLibrary lib) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            HashingStorage newStorage = EconomicMapStorage.create(n);
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1, lib);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transfered
                newStorage = lib.setItem(newStorage, key, PNone.NO_VALUE);
            }

            return factory().createSet(newStorage);
        }

        private PFrozenSet readFrozenSet(int depth, HashingStorageLibrary lib) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            HashingStorage newStorage = EconomicMapStorage.create(n);
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1, lib);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transfered
                newStorage = lib.setItem(newStorage, key, PNone.NO_VALUE);
            }

            return factory().createFrozenSet(newStorage);
        }

        @TruffleBoundary
        private Object readObject(int depth, HashingStorageLibrary lib) {
            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw raise(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }
            int type = readByte();
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_NONE:
                    return PNone.NONE;
                case TYPE_NOVALUE:
                    return PNone.NO_VALUE;
                case TYPE_STOPITER:
                    return PythonLanguage.getCore().lookupType(PythonBuiltinClassType.StopIteration);
                case TYPE_ELLIPSIS:
                    return PythonBuiltinClassType.PEllipsis;
                case TYPE_FALSE:
                    return false;
                case TYPE_TRUE:
                    return true;
                case TYPE_INT:
                    return readInt();
                case TYPE_INT64:
                    return read_long64();
                case TYPE_LONG:
                    return read_long64();
                case TYPE_PINT:
                    return readPInt();
                case TYPE_FLOAT:
                    return readDouble();
                case TYPE_STRING:
                    return readString();
                case TYPE_INTERNED:
                    return readInternedString();
                case TYPE_BYTESLIKE:
                    return readBytesLike();
                case TYPE_TUPLE: {
                    int n = readInt();
                    if (n < 0) {
                        throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
                    }
                    Object[] items = new Object[n];
                    for (int i = 0; i < n; i++) {
                        items[i] = readObject(depth + 1, lib);
                    }
                    return factory().createTuple(items);
                }
                case TYPE_DICT:
                    return readDict(depth, lib);
                case TYPE_LIST:
                    return readList(depth, lib);
                case TYPE_SET:
                    return readSet(depth, lib);
                case TYPE_FROZENSET:
                    return readFrozenSet(depth, lib);
                case TYPE_CODE:
                    return readCode();
                default:
                    throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
        }

        private CreateCodeNode ensureCreateCodeNode() {
            if (createCodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createCodeNode = insert(CodeNodes.CreateCodeNode.create());
            }
            return createCodeNode;
        }

        @Specialization
        Object readObject(VirtualFrame frame, byte[] dataBytes, @SuppressWarnings("unused") int version,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            reset();
            this.data = dataBytes;
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return readObject(0, lib);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static UnmarshallerNode create() {
            return MarshalModuleBuiltinsFactory.UnmarshallerNodeGen.create();
        }
    }
}
