/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltinsClinicProviders.DumpsNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.CreateCodeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithState;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

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
    @ArgumentClinic(name = "version", defaultValue = CURRENT_VERSION_STR, conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DumpsNode extends PythonBinaryClinicBuiltinNode {

        @Child private MarshallerNode marshaller = MarshallerNode.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DumpsNodeClinicProviderGen.INSTANCE;
        }

        private byte[] dump(Object o, int version) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream buffer = new DataOutputStream(baos);
            marshaller.resetRecursionDepth();
            marshaller.execute(o, version, buffer);
            try {
                buffer.flush();
                byte[] result = baos.toByteArray();
                baos.close();
                buffer.close();
                return result;
            } catch (IOException e) {
                throw raise(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, o);
            }
        }

        @Specialization
        @TruffleBoundary
        Object doit(Object value, int version) {
            return factory().createBytes(dump(value, version));
        }
    }

    @Builtin(name = "load", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadNode extends PythonBuiltinNode {
        @Specialization
        Object doit(@SuppressWarnings("unused") Object file) {
            throw raise(NotImplementedError, "marshal.load");
        }
    }

    @Builtin(name = "loads", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadsNode extends PythonBuiltinNode {
        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        static Object doit(VirtualFrame frame, Object buffer,
                        @Cached UnmarshallerNode unmarshallerNode,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                return unmarshallerNode.execute(frame, bufferLib.getBufferBytes(buffer), CURRENT_VERSION);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
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
    private static final String CURRENT_VERSION_STR = "4";
    private static final int CURRENT_VERSION = Integer.parseInt(CURRENT_VERSION_STR);

    abstract static class MarshallerNode extends PNodeWithState {

        public abstract void execute(Object x, int version, DataOutputStream buffer);

        @Child private MarshallerNode recursiveNode;
        private int depth = 0;
        @Child private IsBuiltinClassProfile isBuiltinProfile;
        @Child private PythonObjectLibrary plib;

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
        void writeByte(char v, @SuppressWarnings("unused") int version, DataOutputStream buffer) {
            try {
                buffer.write(v);
            } catch (IOException e) {
                handleIOException(v);
            }
        }

        private void writeBytes(byte[] bytes, int version, DataOutputStream buffer) {
            if (bytes != null) {
                int len = bytes.length;
                writeInt(len, version, buffer);
                try {
                    buffer.write(bytes);
                } catch (IOException e) {
                    throw raise(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL);
                }
            } else {
                writeInt(0, version, buffer);
            }
        }

        private void writeInt(int v, @SuppressWarnings("unused") int version, DataOutputStream buffer) {
            CompilerAsserts.neverPartOfCompilation(); // placed here because this is common
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

        private void writeLong(long v, @SuppressWarnings("unused") int version, DataOutputStream buffer) {
            try {
                buffer.writeLong(v);
            } catch (IOException e) {
                handleIOException(v);
            }
        }

        @Specialization
        void handleLong(long v, int version, DataOutputStream buffer) {
            writeByte(TYPE_LONG, version, buffer);
            writeLong(v, version, buffer);
        }

        @Specialization
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
        void handlePString(PString v, int version, DataOutputStream buffer,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode) {
            if (version >= 3 && isInternedStringNode.execute(v)) {
                writeByte(TYPE_INTERNED, version, buffer);
            } else {
                writeByte(TYPE_STRING, version, buffer);
            }
            writeString(v.getValue(), version, buffer);
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        void handleBuffer(Object buffer, int version, DataOutputStream out,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            writeByte(TYPE_BYTESLIKE, version, out);
            try {
                writeBytes(bufferLib.getBufferBytes(buffer), version, out);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization
        void handlePArray(@SuppressWarnings("unused") PArray v, @SuppressWarnings("unused") int version, @SuppressWarnings("unused") DataOutputStream buffer) {
            throw raise(NotImplementedError, "marshal.dumps(array)");
        }

        private void writeArray(Object[] items, int version, DataOutputStream buffer) {
            if (items != null) {
                writeInt(items.length, version, buffer);
                for (Object item : items) {
                    getRecursiveNode().execute(item, version, buffer);
                }
            } else {
                writeInt(0, version, buffer);
            }
        }

        @Specialization
        void handlePTuple(PTuple t, int version, DataOutputStream buffer,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            writeByte(TYPE_TUPLE, version, buffer);
            writeArray(getObjectArrayNode.execute(t), version, buffer);
        }

        @Specialization
        void handlePList(PList l, int version, DataOutputStream buffer,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray) {
            writeByte(TYPE_LIST, version, buffer);
            writeArray(getArray.execute(l.getSequenceStorage()), version, buffer);
        }

        @Specialization(limit = "1")
        void handlePDict(@SuppressWarnings("unused") PDict d, int version, DataOutputStream buffer,
                        @Bind("d.getDictStorage()") HashingStorage dictStorage,
                        @CachedLibrary("dictStorage") HashingStorageLibrary lib) {
            writeByte(TYPE_DICT, version, buffer);
            int len = lib.length(dictStorage);
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(entry.key, version, buffer);
                getRecursiveNode().execute(entry.value, version, buffer);
            }
        }

        @Specialization
        void handlePCode(PCode c, int version, DataOutputStream buffer) {
            writeByte(TYPE_CODE, version, buffer);
            writeString(c.getFilename(), version, buffer);
            writeInt(c.getFlags(), version, buffer);
            writeBytes(c.getCodestring(), version, buffer);
            writeInt(c.getFirstLineNo(), version, buffer);
            writeBytes(c.getLnotab(), version, buffer);
        }

        @Specialization(limit = "1")
        void handlePSet(PSet s, int version, DataOutputStream buffer,
                        @CachedLibrary("s.getDictStorage()") HashingStorageLibrary lib) {
            writeByte(TYPE_SET, version, buffer);
            HashingStorage dictStorage = s.getDictStorage();
            int len = lib.length(dictStorage);
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(entry.key, version, buffer);
            }
        }

        @Specialization(limit = "1")
        void handlePForzenSet(PFrozenSet s, int version, DataOutputStream buffer,
                        @CachedLibrary("s.getDictStorage()") HashingStorageLibrary lib) {
            writeByte(TYPE_FROZENSET, version, buffer);
            HashingStorage dictStorage = s.getDictStorage();
            int len = lib.length(dictStorage);
            writeInt(len, version, buffer);
            for (DictEntry entry : lib.entries(dictStorage)) {
                getRecursiveNode().execute(entry.key, version, buffer);
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
            CompilerAsserts.neverPartOfCompilation(); // placed here because this is common
            if (plib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                plib = insert(PythonObjectLibrary.getFactory().create(v));
            }
            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw raise(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            } else if (v == null) {
                writeByte(TYPE_NULL, version, buffer);
            } else if (v == PNone.NONE) {
                writeByte(TYPE_NONE, version, buffer);
            } else if (plib.isLazyPythonClass(v)) {
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
        @Child private StringNodes.InternStringNode internStringNode;
        @Child private HashingStorageLibrary storeLib = HashingStorageLibrary.getFactory().createDispatched(3);
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

        private PInt readPInt(ByteBuffer buffer) {
            return factory().createInt(new BigInteger(readBytes(buffer)));
        }

        private static String readString(ByteBuffer buffer) {
            int len = buffer.getInt();
            String text = new String(buffer.array(), buffer.position(), len);
            buffer.position(buffer.position() + len);
            return text;
        }

        private static String readJavaInternedString(ByteBuffer buffer) {
            return readString(buffer).intern();
        }

        private PString readInternedString(ByteBuffer buffer) {
            return ensureInternStringNode().execute(readString(buffer));
        }

        private static byte[] readBytes(ByteBuffer buffer) {
            int len = buffer.getInt();
            if (len > 0) {
                byte[] bytes = new byte[len];
                buffer.get(bytes);
                return bytes;
            } else {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
        }

        private Object[] readArray(int depth, ByteBuffer buffer) {
            int n = buffer.getInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            Object[] items = new Object[n];
            for (int i = 0; i < n; i++) {
                Object item = readObject(depth + 1, buffer);
                if (item == null) {
                    throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
                }
                items[i] = item;
            }
            return items;
        }

        private PBytes readBytesLike(ByteBuffer buffer) {
            byte[] bytes = readBytes(buffer);
            return factory().createBytes(bytes);
        }

        private PComplex readPComplex(ByteBuffer buffer) {
            double real = buffer.getDouble();
            double imag = buffer.getDouble();
            return factory().createComplex(real, imag);
        }

        private PCode readCode(ByteBuffer buffer) {
            // TODO: fix me and use PString interning if needed
            String fileName = readJavaInternedString(buffer);
            int flags = buffer.getInt();
            byte[] codeString = readBytes(buffer);
            int firstLineNo = buffer.getInt();
            byte[] lnoTab = readBytes(buffer);

            return ensureCreateCodeNode().execute(null, PythonBuiltinClassType.PCode, flags, codeString, fileName, firstLineNo, lnoTab);
        }

        private PDict readDict(int depth, ByteBuffer buffer) {
            int len = buffer.getInt();
            HashingStorage store = PDict.createNewStorage(PythonLanguage.getCurrent(), false, len);
            PDict dict = factory().createDict(store);
            for (int i = 0; i < len; i++) {
                Object key = readObject(depth + 1, buffer);
                if (key == null) {
                    break;
                }
                Object value = readObject(depth + 1, buffer);
                if (value != null) {
                    store = storeLib.setItem(store, key, value);
                }
            }
            dict.setDictStorage(store);
            return dict;
        }

        private PTuple readTuple(int depth, ByteBuffer buffer) {
            return factory().createTuple(readArray(depth, buffer));
        }

        private PList readList(int depth, ByteBuffer buffer) {
            return factory().createList(readArray(depth, buffer));
        }

        private PSet readSet(int depth, ByteBuffer buffer) {
            int n = buffer.getInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            HashingStorage newStorage = EconomicMapStorage.create(n);
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1, buffer);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transferred
                newStorage = storeLib.setItem(newStorage, key, PNone.NO_VALUE);
            }

            return factory().createSet(newStorage);
        }

        private PFrozenSet readFrozenSet(int depth, ByteBuffer buffer) {
            int n = buffer.getInt();
            if (n < 0) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            HashingStorage newStorage = EconomicMapStorage.create(n);
            for (int i = 0; i < n; i++) {
                Object key = readObject(depth + 1, buffer);
                // note: we may pass a 'null' frame here because global state is ensured to be
                // transfered
                newStorage = storeLib.setItem(newStorage, key, PNone.NO_VALUE);
            }

            return factory().createFrozenSet(newStorage);
        }

        private Object readObject(int depth, ByteBuffer buffer) {
            CompilerAsserts.neverPartOfCompilation();
            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw raise(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }
            int type = buffer.get();
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
                    return buffer.getInt();
                case TYPE_INT64:
                    return buffer.getLong();
                case TYPE_LONG:
                    return buffer.getLong();
                case TYPE_PINT:
                    return readPInt(buffer);
                case TYPE_FLOAT:
                    return buffer.getDouble();
                case TYPE_STRING:
                    return readString(buffer);
                case TYPE_INTERNED:
                    return readInternedString(buffer);
                case TYPE_BYTESLIKE:
                    return readBytesLike(buffer);
                case TYPE_TUPLE:
                    return readTuple(depth, buffer);
                case TYPE_DICT:
                    return readDict(depth, buffer);
                case TYPE_LIST:
                    return readList(depth, buffer);
                case TYPE_SET:
                    return readSet(depth, buffer);
                case TYPE_FROZENSET:
                    return readFrozenSet(depth, buffer);
                case TYPE_CODE:
                    return readCode(buffer);
                case TYPE_COMPLEX:
                    return readPComplex(buffer);
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

        private StringNodes.InternStringNode ensureInternStringNode() {
            if (internStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                internStringNode = insert(StringNodes.InternStringNode.create());
            }
            return internStringNode;
        }

        @TruffleBoundary
        private Object readObjectBoundary(byte[] dataBytes) {
            return readObject(0, ByteBuffer.wrap(dataBytes));
        }

        @Specialization
        Object readObject(VirtualFrame frame, byte[] dataBytes, @SuppressWarnings("unused") int version,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return readObjectBoundary(dataBytes);
            } catch (BufferUnderflowException e) {
                throw raise(EOFError, "EOF read where not expected");
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static UnmarshallerNode create() {
            return MarshalModuleBuiltinsFactory.UnmarshallerNodeGen.create();
        }
    }
}
