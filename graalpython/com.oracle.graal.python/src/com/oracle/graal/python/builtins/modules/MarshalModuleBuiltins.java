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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
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
                throw raise(ValueError, "Was not possible to marshal %p", o);
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

    private abstract static class PNodeWithState extends PNodeWithContext {
        @Child private PythonObjectFactory objectFactory;
        @Child private PRaiseNode raiseNode;

        protected final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode.raise(type, format, arguments);
        }

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectFactory = insert(PythonObjectFactory.create());
            }
            return objectFactory;
        }
    }

    abstract static class MarshallerNode extends PNodeWithState {

        public abstract void execute(VirtualFrame frame, Object x, int version, DataOutputStream buffer);

        @Child private MarshallerNode recursiveNode;
        private int depth = 0;
        private IsBuiltinClassProfile isBuiltinProfile;

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
            throw raise(ValueError, "Was not possible to marshal %p", v);
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
                throw raise(ValueError, "Was not possible to marshal");
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

        @Specialization
        void handlePDict(VirtualFrame frame, PDict d, int version, DataOutputStream buffer,
                        @Cached HashingStorageNodes.LenNode len) {
            writeByte(TYPE_DICT, version, buffer);
            HashingStorage storage = d.getDictStorage();
            writeInt(len.execute(storage), version, buffer);
            for (DictEntry entry : storage.entries()) {
                getRecursiveNode().execute(frame, entry.key, version, buffer);
                getRecursiveNode().execute(frame, entry.value, version, buffer);
            }
        }

        @Specialization
        void handlePCode(VirtualFrame frame, PCode c, int version, DataOutputStream buffer) {
            writeByte(TYPE_CODE, version, buffer);
            writeInt(c.getArgcount(), version, buffer);
            writeInt(c.getPositionalOnlyArgCount(), version, buffer);
            writeInt(c.getKwonlyargcount(), version, buffer);
            writeInt(c.getNlocals(), version, buffer);
            writeInt(c.getStacksize(), version, buffer);
            writeInt(c.getFlags(), version, buffer);
            writeBytes(c.getCodestring() == null ? new byte[0] : c.getCodestring(), version, buffer);
            getRecursiveNode().execute(frame, internStrings(c.getConstants()), version, buffer);
            getRecursiveNode().execute(frame, internStrings(c.getNames()), version, buffer);
            getRecursiveNode().execute(frame, internStrings(c.getVarnames()), version, buffer);
            getRecursiveNode().execute(frame, internStrings(c.getFreeVars()), version, buffer);
            getRecursiveNode().execute(frame, internStrings(c.getCellVars()), version, buffer);
            getRecursiveNode().execute(frame, new InternedString(c.getFilename()), version, buffer);
            getRecursiveNode().execute(frame, new InternedString(c.getName()), version, buffer);
            writeInt(c.getFirstLineNo(), version, buffer);
            writeBytes(c.getLnotab() == null ? new byte[0] : c.getLnotab(), version, buffer);
        }

        private PTuple internStrings(Object[] values) {
            Object[] interned;
            if (values == null) {
                interned = new Object[0];
            } else {
                interned = new Object[values.length];
                for (int i = 0; i < interned.length; i++) {
                    if (values[i] instanceof String) {
                        interned[i] = new InternedString((String) values[i]);
                    } else if (values[i] instanceof PString) {
                        interned[i] = new InternedString(((PString) values[i]).getValue());
                    } else {
                        interned[i] = values[i];
                    }
                }
            }
            return factory().createTuple(interned);
        }

        @Specialization
        void handlePSet(VirtualFrame frame, PSet s, int version, DataOutputStream buffer,
                        @Cached HashingStorageNodes.LenNode lenNode) {
            writeByte(TYPE_SET, version, buffer);
            HashingStorage dictStorage = s.getDictStorage();
            int len = lenNode.execute(dictStorage);
            writeInt(len, version, buffer);
            for (DictEntry entry : dictStorage.entries()) {
                getRecursiveNode().execute(frame, entry.key, version, buffer);
            }
        }

        @Specialization
        void handlePForzenSet(VirtualFrame frame, PFrozenSet s, int version, DataOutputStream buffer,
                        @Cached HashingStorageNodes.LenNode lenNode) {
            writeByte(TYPE_FROZENSET, version, buffer);
            HashingStorage dictStorage = s.getDictStorage();
            int len = lenNode.execute(dictStorage);
            writeInt(len, version, buffer);
            for (DictEntry entry : dictStorage.entries()) {
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
                throw raise(ValueError, "Maximum marshal stack depth");
            } else if (v == null) {
                writeByte(TYPE_NULL, version, buffer);
            } else if (v == PNone.NONE) {
                writeByte(TYPE_NONE, version, buffer);
            } else if (v instanceof LazyPythonClass) {
                if (isBuiltinProfile == null) {
                    isBuiltinProfile = IsBuiltinClassProfile.create();
                }
                if (isBuiltinProfile.profileClass((LazyPythonClass) v, PythonBuiltinClassType.StopIteration)) {
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

        @Child private HashingStorageNodes.SetItemNode setItemNode;
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
            return data[index++];
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

        private PCode readCode(int depth) {
            int argcount = readInt();
            int posonlyargcount = readInt();
            int kwonlyargcount = readInt();
            int nlocals = readInt();
            int stacksize = readInt();
            int flags = readInt();
            byte[] codestring = readBytes();
            Object[] constants = getArray((PTuple) readObject(depth + 1));
            Object[] names = getArray((PTuple) readObject(depth + 1));
            Object[] varnames = getArray((PTuple) readObject(depth + 1));
            Object[] freevars = getArray((PTuple) readObject(depth + 1));
            Object[] cellvars = getArray((PTuple) readObject(depth + 1));
            String filename = ((String) readObject(depth + 1));
            String name = ((String) readObject(depth + 1));
            int firstlineno = readInt();
            byte[] lnotab = readBytes();

            return ensureCreateCodeNode().execute(null, PythonBuiltinClassType.PCode, argcount, posonlyargcount,
                            kwonlyargcount, nlocals, stacksize, flags, codestring, constants, names,
                            varnames, freevars, cellvars, filename, name, firstlineno, lnotab);
        }

        private PDict readDict(int depth) {
            int len = readInt();
            HashMap<Object, Object> map = new HashMap<>(len);
            for (int i = 0; i < len; i++) {
                Object key = readObject(depth + 1);
                if (key == null) {
                    break;
                }
                Object value = readObject(depth + 1);
                if (value != null) {
                    map.put(key, value);
                }
            }
            return factory().createDict(map);
        }

        private PList readList(int depth) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, "bad marshal data");
            }
            Object[] items = new Object[n];
            for (int i = 0; i < n; i++) {
                Object item = readObject(depth + 1);
                if (item == null) {
                    throw raise(ValueError, "bad marshal data");
                }
                items[i] = item;
            }
            return factory().createList(items);
        }

        private PSet readSet(int depth) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, "bad marshal data");
            }
            HashingStorage newStorage = EconomicMapStorage.create(n, true);
            if (n > 0 && setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingStorageNodes.SetItemNode.create());
            }
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transfered
                setItemNode.execute(null, newStorage, key, PNone.NO_VALUE);
            }

            return factory().createSet(newStorage);
        }

        private PFrozenSet readFrozenSet(int depth) {
            int n = readInt();
            if (n < 0) {
                throw raise(ValueError, "bad marshal data");
            }
            HashingStorage newStorage = EconomicMapStorage.create(n, true);
            if (n > 0 && setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingStorageNodes.SetItemNode.create());
            }
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transfered
                setItemNode.execute(null, newStorage, key, PNone.NO_VALUE);
            }

            return factory().createFrozenSet(newStorage);
        }

        private static Object[] getArray(PTuple tuple) {
            CompilerAsserts.neverPartOfCompilation();
            return GetObjectArrayNodeGen.getUncached().execute(tuple);

        }

        @TruffleBoundary
        private Object readObject(int depth) {
            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw raise(ValueError, "Maximum marshal stack depth");
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
                        throw raise(ValueError, "bad marshal data");
                    }
                    Object[] items = new Object[n];
                    for (int i = 0; i < n; i++) {
                        items[i] = readObject(depth + 1);
                    }
                    return factory().createTuple(items);
                }
                case TYPE_DICT:
                    return readDict(depth);
                case TYPE_LIST:
                    return readList(depth);
                case TYPE_SET:
                    return readSet(depth);
                case TYPE_FROZENSET:
                    return readFrozenSet(depth);
                case TYPE_CODE:
                    return readCode(depth);
                default:
                    throw raise(ValueError, "bad marshal data");
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
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            reset();
            this.data = dataBytes;
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return readObject(0);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static UnmarshallerNode create() {
            return MarshalModuleBuiltinsFactory.UnmarshallerNodeGen.create();
        }
    }
}
