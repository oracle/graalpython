/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.StringLiterals.T_VERSION;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltinsClinicProviders.DumpNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltinsClinicProviders.DumpsNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.CreateCodeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.IsInternedStringNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.lib.PyComplexCheckExactNode;
import com.oracle.graal.python.lib.PyDictCheckExactNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyFrozenSetCheckExactNode;
import com.oracle.graal.python.lib.PyListCheckExactNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PySetCheckExactNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

@CoreFunctions(defineModule = "marshal")
public final class MarshalModuleBuiltins extends PythonBuiltins {
    static final int CURRENT_VERSION = 5;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MarshalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T_VERSION, CURRENT_VERSION);
    }

    @Builtin(name = "dump", minNumOfPositionalArgs = 2, parameterNames = {"value", "file", "version"})
    @ArgumentClinic(name = "version", defaultValue = "CURRENT_VERSION", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DumpNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DumpNodeClinicProviderGen.INSTANCE;
        }

        @NeverDefault
        protected static LookupAndCallBinaryNode createCallWriteNode() {
            return LookupAndCallBinaryNode.create(T_WRITE);
        }

        @Specialization
        static Object doit(VirtualFrame frame, Object value, Object file, int version,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached("createCallWriteNode()") LookupAndCallBinaryNode callNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object savedState = IndirectCallContext.enter(frame, indirectCallData);
            try {
                return callNode.executeObject(frame, file, factory.createBytes(Marshal.dump(value, version, PythonContext.get(inliningTarget))));
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Marshal.MarshalError me) {
                throw raiseNode.get(inliningTarget).raise(me.type, me.message, me.arguments);
            } finally {
                IndirectCallContext.exit(frame, indirectCallData, savedState);
            }
        }
    }

    @Builtin(name = "dumps", minNumOfPositionalArgs = 1, parameterNames = {"value", "version"})
    @ArgumentClinic(name = "version", defaultValue = "CURRENT_VERSION", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DumpsNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DumpsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doit(VirtualFrame frame, Object value, int version,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object savedState = IndirectCallContext.enter(frame, indirectCallData);
            try {
                return factory.createBytes(Marshal.dump(value, version, PythonContext.get(inliningTarget)));
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Marshal.MarshalError me) {
                throw raiseNode.get(inliningTarget).raise(me.type, me.message, me.arguments);
            } finally {
                IndirectCallContext.exit(frame, indirectCallData, savedState);
            }
        }
    }

    @Builtin(name = "load", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadNode extends PythonBuiltinNode {
        @NeverDefault
        protected static LookupAndCallBinaryNode createCallReadNode() {
            return LookupAndCallBinaryNode.create(T_READ);
        }

        @Specialization
        static Object doit(VirtualFrame frame, Object file,
                        @Bind("this") Node inliningTarget,
                        @Cached("createCallReadNode()") LookupAndCallBinaryNode callNode,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = callNode.executeObject(frame, file, 0);
            if (!bufferLib.hasBuffer(buffer)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.READ_RETURNED_NOT_BYTES, buffer);
            }
            try {
                return Marshal.loadFile(file);
            } catch (NumberFormatException e) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
            } catch (Marshal.MarshalError me) {
                throw raiseNode.get(inliningTarget).raise(me.type, me.message, me.arguments);
            }
        }
    }

    @Builtin(name = "loads", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"bytes"})
    @ArgumentClinic(name = "bytes", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class LoadsNode extends PythonUnaryClinicBuiltinNode {

        @Specialization
        static Object doit(VirtualFrame frame, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return Marshal.load(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } catch (NumberFormatException e) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
            } catch (Marshal.MarshalError me) {
                throw raiseNode.get(inliningTarget).raise(me.type, me.message, me.arguments);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MarshalModuleBuiltinsClinicProviders.LoadsNodeClinicProviderGen.INSTANCE;
        }

    }

    static final class Marshal {
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
        private static final char TYPE_BINARY_FLOAT = 'g';
        private static final char TYPE_COMPLEX = 'x';
        private static final char TYPE_BINARY_COMPLEX = 'y';
        private static final char TYPE_LONG = 'l';
        private static final char TYPE_STRING = 's';
        private static final char TYPE_INTERNED = 't';
        private static final char TYPE_REF = 'r';
        private static final char TYPE_TUPLE = '(';
        private static final char TYPE_LIST = '[';
        private static final char TYPE_DICT = '{';
        private static final char TYPE_UNICODE = 'u';
        private static final char TYPE_UNKNOWN = '?';
        private static final char TYPE_SET = '<';
        private static final char TYPE_FROZENSET = '>';
        private static final char FLAG_REF = 0x80;
        private static final char TYPE_ASCII = 'a';
        private static final char TYPE_ASCII_INTERNED = 'A';
        private static final char TYPE_SMALL_TUPLE = ')';
        private static final char TYPE_SHORT_ASCII = 'z';
        private static final char TYPE_SHORT_ASCII_INTERNED = 'Z';

        // Following types are GraalPython-specific for serializing our code objects that may use
        // plain Java objects
        private static final char TYPE_GRAALPYTHON_CODE = 'C';
        private static final char TYPE_GRAALPYTHON_CODE_UNIT = 'U';
        private static final char TYPE_BIG_INTEGER = 'B';
        private static final char TYPE_ARRAY = ']';
        // These are constants that show up in the Bytecode DSL interpreter.
        private static final char TYPE_GRAALPYTHON_DSL_CODE_UNIT = 'D';
        private static final char TYPE_DSL_SOURCE = '$';
        private static final char TYPE_DSL_EMPTY_KEYWORDS = 'k';

        private static final char ARRAY_TYPE_OBJECT = 'o';
        private static final char ARRAY_TYPE_INT = 'i';
        private static final char ARRAY_TYPE_LONG = 'l';
        private static final char ARRAY_TYPE_DOUBLE = 'd';
        private static final char ARRAY_TYPE_BYTE = 'b';
        private static final char ARRAY_TYPE_BOOLEAN = 'B';
        private static final char ARRAY_TYPE_SHORT = 's';
        private static final char ARRAY_TYPE_STRING = 'S';
        private static final int MAX_MARSHAL_STACK_DEPTH = 201;

        // CPython enforces 15bits per digit when reading/writing large integers for portability
        private static final int MARSHAL_SHIFT = 15;
        private static final BigInteger MARSHAL_BASE = BigInteger.valueOf(1 << MARSHAL_SHIFT);

        private static final int BYTES_PER_LONG = Long.SIZE / Byte.SIZE;
        private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;
        private static final int BYTES_PER_SHORT = Short.SIZE / Byte.SIZE;

        /**
         * This class exists to throw errors out of the (un)marshalling code, without having to
         * construct Python exceptions (yet). Since the (un)marshalling code does not have nodes or
         * frames ready, callers are responsible for catching the MarshalError and translating it
         * into a PException so that the python level exception has the correct context and
         * traceback.
         */
        static final class MarshalError extends RuntimeException {
            static final long serialVersionUID = 5323687983726237118L;

            final PythonBuiltinClassType type;
            final transient TruffleString message;
            final transient Object[] arguments;

            MarshalError(PythonBuiltinClassType type, TruffleString message, Object... arguments) {
                super(null, null);
                this.type = type;
                this.message = message;
                this.arguments = arguments;
            }

            @SuppressWarnings("sync-override")
            @Override
            public final Throwable fillInStackTrace() {
                return this;
            }
        }

        @TruffleBoundary
        static byte[] dump(Object value, int version, Python3Core core) throws IOException, MarshalError {
            Marshal outMarshal = new Marshal(version, core.getTrue(), core.getFalse());
            outMarshal.writeObject(value);
            return outMarshal.outData.toByteArray();
        }

        @TruffleBoundary
        static Object load(byte[] ary, int length) throws NumberFormatException, MarshalError {
            Marshal inMarshal = new Marshal(ary, length);
            Object result = inMarshal.readObject();
            if (result == null) {
                throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
            }
            return result;
        }

        @TruffleBoundary
        static Object loadFile(Object file) throws NumberFormatException, MarshalError {
            Marshal inMarshal = new Marshal(file);
            Object result = inMarshal.readObject();
            if (result == null) {
                throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
            }
            return result;
        }

        /**
         * This is for making the Marshal object simpler. This stream implements the logic of
         * Python's r_string function in marshal.c when p->readable is set, i.e., it uses readinto
         * to read enough bytes into a buffer.
         */
        static final class FileLikeInputStream extends InputStream {
            private final Object fileLike;
            private final PyNumberAsSizeNode asSize;
            private final PByteArray buffer;
            private final ByteSequenceStorage singleByteStore;

            FileLikeInputStream(Object fileLike) {
                this.fileLike = fileLike;
                this.asSize = PyNumberAsSizeNode.getUncached();
                this.singleByteStore = new ByteSequenceStorage(new byte[1]);
                this.buffer = PythonObjectFactory.getUncached().createByteArray(singleByteStore);
            }

            @Override
            public int read() {
                Object readIntoResult = PyObjectCallMethodObjArgs.executeUncached(fileLike, T_READINTO, buffer);
                int numRead = asSize.executeExact(null, readIntoResult, ValueError);
                if (numRead > 1) {
                    throw new MarshalError(ValueError, ErrorMessages.S_RETURNED_TOO_MUCH_DATA, "read()", 1, numRead);
                }
                return singleByteStore.getIntItemNormalized(0);
            }

            @Override
            public int read(byte[] b, int off, int len) {
                assert off == 0;
                ByteSequenceStorage tempStore = new ByteSequenceStorage(b, len);
                buffer.setSequenceStorage(tempStore);
                try {
                    Object readIntoResult = PyObjectCallMethodObjArgs.executeUncached(fileLike, T_READINTO, buffer);
                    int numRead = asSize.executeExact(null, readIntoResult, ValueError);
                    if (numRead > len) {
                        throw new MarshalError(ValueError, ErrorMessages.S_RETURNED_TOO_MUCH_DATA, "read()", 1, numRead);
                    }
                    return numRead;
                } finally {
                    buffer.setSequenceStorage(singleByteStore);
                }
            }
        }

        private static final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        final HashMap<Object, Integer> refMap;
        final ArrayList<Object> refList;
        final ByteArrayOutputStream outData;
        final DataOutput out;
        final DataInput in;
        final int version;
        final PInt pyTrue;
        final PInt pyFalse;
        // CPython's marshal code is little endian
        final ByteArraySupport baSupport = ByteArraySupport.littleEndian();
        byte[] buffer = new byte[Long.BYTES];
        int depth = 0;
        /*
         * A DSL node needs access to its Source during deserialization, but we do not wish to
         * actually encode it in the serialized representation. Instead, we supply a Source to the
         * Marshal object and return it when the source is needed.
         */
        Source source = null;

        Marshal(int version, PInt pyTrue, PInt pyFalse) {
            this.version = version;
            this.pyTrue = pyTrue;
            this.pyFalse = pyFalse;
            this.outData = new ByteArrayOutputStream();
            this.out = new DataOutputStream(outData);
            this.refMap = new HashMap<>();
            this.in = null;
            this.refList = null;
        }

        Marshal(int version, PInt pyTrue, PInt pyFalse, DataOutput out) {
            this.version = version;
            this.pyTrue = pyTrue;
            this.pyFalse = pyFalse;
            this.outData = null;
            this.out = out;
            this.refMap = new HashMap<>();
            this.in = null;
            this.refList = null;
        }

        Marshal(byte[] in, int length) {
            this(new DataInputStream(new ByteArrayInputStream(in, 0, length)), null);
        }

        Marshal(Object in) {
            this(new DataInputStream(new FileLikeInputStream(in)), null);
        }

        Marshal(DataInput in, Source source) {
            this.in = in;
            this.source = source;
            this.refList = new ArrayList<>();
            this.version = -1;
            this.pyTrue = null;
            this.pyFalse = null;
            this.outData = null;
            this.out = null;
            this.refMap = null;
        }

        private void writeByte(int v) {
            try {
                out.write(v);
            } catch (IOException e) {
                // The underlying output streams we use should not throw IOExceptions.
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private void writeBytes(byte[] b, int off, int len) {
            try {
                out.write(b, off, len);
            } catch (IOException e) {
                // The underlying output streams we use should not throw IOExceptions.
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private int readByte() {
            int nextByte;
            try {
                nextByte = in.readUnsignedByte();
            } catch (EOFException e) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_EOF);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return nextByte;
        }

        private void writeSize(int sz) {
            writeInt(sz);
        }

        private int readByteSize() {
            return checkSize(readByte());
        }

        private int readSize() {
            return checkSize(readInt());
        }

        private static int checkSize(int sz) {
            if (sz < 0) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_S, "size out of range");
            }
            return sz;
        }

        private void writeBytes(byte[] bytes) throws IOException {
            writeSize(bytes.length);
            out.write(bytes);
        }

        private byte[] readNBytes(int sz) {
            if (sz == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            } else {
                if (buffer.length < sz) {
                    buffer = new byte[sz];
                }
                return readNBytes(sz, buffer);
            }
        }

        private byte[] readNBytes(int sz, byte[] output) {
            if (sz == 0) {
                return output;
            }
            try {
                in.readFully(output, 0, sz);
            } catch (EOFException e) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_EOF);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return output;
        }

        private byte[] readBytes() {
            int sz = readSize();
            return readNBytes(sz, new byte[sz]);
        }

        private void writeInt(int v) {
            for (int i = 0; i < Integer.SIZE; i += Byte.SIZE) {
                writeByte((v >> i) & 0xff);
            }
        }

        private void writeShort(short v) {
            for (int i = 0; i < Short.SIZE; i += Byte.SIZE) {
                writeByte((v >> i) & 0xff);
            }
        }

        private int readInt() {
            return baSupport.getInt(readNBytes(BYTES_PER_INT), 0);
        }

        private short readShort() {
            return baSupport.getShort(readNBytes(BYTES_PER_SHORT), 0);
        }

        private void writeLong(long v) {
            for (int i = 0; i < Long.SIZE; i += Byte.SIZE) {
                writeByte((int) ((v >>> i) & 0xff));
            }
        }

        private long readLong() {
            return baSupport.getLong(readNBytes(BYTES_PER_LONG), 0);
        }

        private void writeBigInteger(BigInteger v) {
            // for compatibility with cpython, we store the number in base 2**15
            BigInteger[] divRem;
            ArrayList<Integer> digits = new ArrayList<>();
            BigInteger quotient = v.abs();
            do {
                divRem = quotient.divideAndRemainder(MARSHAL_BASE);
                quotient = divRem[0];
                digits.add(divRem[1].intValue());
            } while (quotient.signum() != 0);
            int sz = digits.size();
            if (v.signum() < 0) {
                writeSize(-sz);
            } else {
                writeSize(sz);
            }
            for (int digit : digits) {
                for (int i = 0; i < Short.SIZE; i += Byte.SIZE) {
                    writeByte((digit >> i) & 0xff);
                }
            }
        }

        private BigInteger readBigInteger() {
            boolean negative;
            int sz = readInt();
            if (sz < 0) {
                negative = true;
                sz = -sz;
            } else {
                negative = false;
            }

            // size is in shorts
            sz *= 2;

            byte[] data = readNBytes(sz);

            int i = 0;
            int digit = baSupport.getShort(data, i);
            i += 2;
            BigInteger result = BigInteger.valueOf(digit);

            while (i < sz) {
                int power = i / 2;
                digit = baSupport.getShort(data, i);
                i += 2;
                result = result.add(BigInteger.valueOf(digit).multiply(MARSHAL_BASE.pow(power)));
            }
            if (negative) {
                return result.negate();
            } else {
                return result;
            }
        }

        private void writeDouble(double v) {
            writeLong(Double.doubleToLongBits(v));
        }

        private double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        private void writeDoubleString(double v) throws IOException {
            writeShortString(Double.toString(v));
        }

        private double readDoubleString() throws NumberFormatException {
            return Double.parseDouble(readShortString().toJavaStringUncached());
        }

        private void writeReferenceOrComplexObject(Object v) {
            Integer reference = null;
            if (version < 3 || (reference = refMap.get(v)) == null) {
                int flag = 0;
                if (version >= 3) {
                    flag = FLAG_REF;
                    refMap.put(v, refMap.size());
                }
                writeComplexObject(v, flag);
            } else if (reference != null) {
                writeByte(TYPE_REF);
                writeInt(reference);
            }
        }

        private Object readReference() {
            int n = readInt();
            if (n < 0 || n >= refList.size()) {
                throw new MarshalError(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            Object o = refList.get(n);
            assert o != null;
            return o;
        }

        private void writeObject(Object v) throws IOException {
            depth++;

            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw new MarshalError(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }

            // see CPython's w_object
            if (v == null) {
                writeNull();
            } else if (v == PNone.NONE) {
                writeByte(TYPE_NONE);
            } else if (v == PNone.NO_VALUE) {
                writeByte(TYPE_NOVALUE);
            } else if (IsSameTypeNode.executeUncached(v, PythonBuiltinClassType.StopIteration)) {
                writeByte(TYPE_STOPITER);
            } else if (v == PEllipsis.INSTANCE) {
                writeByte(TYPE_ELLIPSIS);
            } else if (v == Boolean.TRUE || v == pyTrue) {
                writeByte(TYPE_TRUE);
            } else if (v == Boolean.FALSE || v == pyFalse) {
                writeByte(TYPE_FALSE);
            } else if (v instanceof Integer) {
                writeByte(TYPE_INT);
                writeInt((Integer) v);
            } else if (v instanceof Long) {
                writeByte(TYPE_INT64);
                writeLong((Long) v);
            } else if (v instanceof Double) {
                if (version > 1) {
                    writeByte(TYPE_BINARY_FLOAT);
                    writeDouble((Double) v);
                } else {
                    writeByte(TYPE_FLOAT);
                    writeDoubleString((Double) v);
                }
            } else if (v instanceof BigInteger) {
                writeByte(TYPE_BIG_INTEGER);
                writeBigInteger((BigInteger) v);
            } else {
                writeReferenceOrComplexObject(v);
            }

            depth--;
        }

        private void writeNull() {
            writeByte(TYPE_NULL);
        }

        private void writeComplexObject(Object v, int flag) {
            try {
                if (PyLongCheckExactNode.executeUncached(v)) {
                    BigInteger bigInt = ((PInt) v).getValue();
                    if (bigInt.signum() == 0) {
                        // we don't handle ZERO in read/writeBigInteger
                        writeByte(TYPE_INT | flag);
                        writeInt(0);
                    } else {
                        // other cases are fine to not narrow here
                        writeByte(TYPE_LONG | flag);
                        writeBigInteger(((PInt) v).getValue());
                    }
                } else if (PyFloatCheckExactNode.executeUncached(v)) {
                    if (version > 1) {
                        writeByte(TYPE_BINARY_FLOAT | flag);
                        writeDouble(((PFloat) v).getValue());
                    } else {
                        writeByte(TYPE_FLOAT | flag);
                        writeDoubleString(((PFloat) v).getValue());
                    }
                } else if (PyComplexCheckExactNode.executeUncached(v)) {
                    if (version > 1) {
                        writeByte(TYPE_BINARY_COMPLEX | flag);
                        writeDouble(((PComplex) v).getReal());
                        writeDouble(((PComplex) v).getImag());
                    } else {
                        writeByte(TYPE_COMPLEX | flag);
                        writeDoubleString(((PComplex) v).getReal());
                        writeDoubleString(((PComplex) v).getImag());
                    }
                } else if (isJavaString(v)) {
                    writeByte(TYPE_UNICODE | flag);
                    writeString(TruffleString.fromJavaStringUncached((String) v, TS_ENCODING));
                } else if (v instanceof TruffleString) {
                    writeByte(TYPE_UNICODE | flag);
                    writeString((TruffleString) v);
                } else if (PyUnicodeCheckExactNode.executeUncached(v)) {
                    if (version >= 3 && IsInternedStringNode.executeUncached((PString) v)) {
                        writeByte(TYPE_INTERNED | flag);
                    } else {
                        writeByte(TYPE_UNICODE | flag);
                    }
                    writeString(((PString) v).getValueUncached());
                } else if (PyTupleCheckExactNode.executeUncached(v)) {
                    Object[] items = GetObjectArrayNode.executeUncached(v);
                    if (version >= 4 && items.length < 256) {
                        writeByte(TYPE_SMALL_TUPLE | flag);
                        writeByte(items.length);
                    } else {
                        writeByte(TYPE_TUPLE | flag);
                        writeSize(items.length);
                    }
                    for (Object item : items) {
                        writeObject(item);
                    }
                } else if (PyListCheckExactNode.executeUncached(v)) {
                    writeByte(TYPE_LIST | flag);
                    Object[] items = GetInternalObjectArrayNode.executeUncached(SequenceNodes.GetSequenceStorageNode.executeUncached(v));
                    writeSize(items.length);
                    for (Object item : items) {
                        writeObject(item);
                    }
                } else if (v instanceof PDict && PyDictCheckExactNode.executeUncached(v)) {
                    HashingStorage dictStorage = ((PDict) v).getDictStorage();
                    // NULL terminated as in CPython
                    writeByte(TYPE_DICT | flag);
                    HashingStorageIterator it = HashingStorageGetIterator.executeUncached(dictStorage);
                    while (HashingStorageIteratorNext.executeUncached(dictStorage, it)) {
                        writeObject(HashingStorageIteratorKey.executeUncached(dictStorage, it));
                        writeObject(HashingStorageIteratorValue.executeUncached(dictStorage, it));
                    }
                    writeNull();
                } else if (v instanceof PBaseSet && (PySetCheckExactNode.executeUncached(v) || PyFrozenSetCheckExactNode.executeUncached(v))) {
                    if (PyFrozenSetCheckExactNode.executeUncached(v)) {
                        writeByte(TYPE_FROZENSET | flag);
                    } else {
                        writeByte(TYPE_SET | flag);
                    }
                    HashingStorage dictStorage = ((PBaseSet) v).getDictStorage();
                    int len = HashingStorageLen.executeUncached(dictStorage);
                    writeSize(len);
                    HashingStorageIterator it = HashingStorageGetIterator.executeUncached(dictStorage);
                    while (HashingStorageIteratorNext.executeUncached(dictStorage, it)) {
                        writeObject(HashingStorageIteratorKey.executeUncached(dictStorage, it));
                    }
                } else if (v instanceof int[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_INT);
                    writeIntArray((int[]) v);
                } else if (v instanceof long[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_LONG);
                    writeLongArray((long[]) v);
                } else if (v instanceof short[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_SHORT);
                    writeShortArray((short[]) v);
                } else if (v instanceof boolean[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_BOOLEAN);
                    writeBooleanArray((boolean[]) v);
                } else if (v instanceof double[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_DOUBLE);
                    writeDoubleArray((double[]) v);
                } else if (v instanceof byte[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_BYTE);
                    writeBytes((byte[]) v);
                } else if (v instanceof TruffleString[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_STRING);
                    writeStringArray((TruffleString[]) v);
                } else if (v instanceof PKeyword[]) {
                    assert v == PKeyword.EMPTY_KEYWORDS;
                    writeByte(TYPE_DSL_EMPTY_KEYWORDS);
                } else if (v instanceof Object[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_OBJECT);
                    writeObjectArray((Object[]) v);
                } else if (v instanceof PCode) {
                    // we always store code objects in our format, CPython will not read our
                    // marshalled data when that contains code objects
                    PCode c = (PCode) v;
                    writeByte(TYPE_GRAALPYTHON_CODE | flag);
                    writeString(c.getFilename());
                    writeInt(c.getFlags());
                    writeBytes(c.getCodestring());
                    writeInt(c.getFirstLineNo());
                    byte[] lnotab = c.getLinetable();
                    if (lnotab == null) {
                        lnotab = PythonUtils.EMPTY_BYTE_ARRAY;
                    }
                    writeBytes(lnotab);
                } else if (v instanceof CodeUnit) {
                    if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                        writeByte(TYPE_GRAALPYTHON_DSL_CODE_UNIT | flag);
                        writeBytecodeDSLCodeUnit((BytecodeDSLCodeUnit) v);
                    } else {
                        writeByte(TYPE_GRAALPYTHON_CODE_UNIT | flag);
                        writeBytecodeCodeUnit((BytecodeCodeUnit) v);
                    }
                } else if (v instanceof Source s) {
                    writeByte(TYPE_DSL_SOURCE | flag);
                    setSource(s);
                } else {
                    PythonBufferAcquireLibrary acquireLib = PythonBufferAcquireLibrary.getFactory().getUncached(v);
                    if (acquireLib.hasBuffer(v)) {
                        writeByte(TYPE_STRING | flag);
                        Object buf = acquireLib.acquireReadonly(v);
                        PythonBufferAccessLibrary accessLib = PythonBufferAccessLibrary.getFactory().getUncached(buf);
                        try {
                            int len = accessLib.getBufferLength(buf);
                            writeSize(len);
                            out.write(accessLib.getInternalOrCopiedByteArray(buf), 0, len);
                        } finally {
                            accessLib.release(buf);
                        }
                    } else {
                        writeByte(TYPE_UNKNOWN);
                        throw new MarshalError(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, v);
                    }
                }
            } catch (IOException e) {
                throw new MarshalError(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, v);
            }
        }

        private void writeObjectArray(Object[] a) throws IOException {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeObject(a[i]);
            }
        }

        private void writeDoubleArray(double[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeDouble(a[i]);
            }
        }

        private void writeLongArray(long[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeLong(a[i]);
            }
        }

        private void writeIntArray(int[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeInt(a[i]);
            }
        }

        private void writeStringArray(TruffleString[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeString(a[i]);
            }
        }

        private void writeShortArray(short[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeShort(a[i]);
            }
        }

        private void writeBooleanArray(boolean[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeByte(a[i] ? 1 : 0);
            }
        }

        @FunctionalInterface
        static interface AddRefAndReturn {
            Object run(Object o);
        }

        private Object readObject() throws NumberFormatException {
            CompilerAsserts.neverPartOfCompilation();
            depth++;

            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw new MarshalError(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }

            int code = readByte();
            int flag = code & FLAG_REF;
            int type = code & ~FLAG_REF;

            if (type == TYPE_REF) {
                depth--;
                return readReference();
            } else {
                int reference = refList.size();
                if (flag != 0) {
                    refList.add(null);
                }
                Object retval = readObject(type, (o) -> {
                    if (flag != 0) {
                        refList.set(reference, o);
                    }
                    return o;
                });
                depth--;
                return retval;
            }
        }

        private Object readObject(int type, AddRefAndReturn addRef) throws NumberFormatException {
            CompilerAsserts.neverPartOfCompilation();
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_NONE:
                    return PNone.NONE;
                case TYPE_NOVALUE:
                    return PNone.NO_VALUE;
                case TYPE_STOPITER:
                    return PythonBuiltinClassType.StopIteration;
                case TYPE_ELLIPSIS:
                    return PEllipsis.INSTANCE;
                case TYPE_FALSE:
                    return false;
                case TYPE_TRUE:
                    return true;
                case TYPE_INT:
                    return addRef.run(readInt());
                case TYPE_INT64:
                    return addRef.run(readLong());
                case TYPE_BIG_INTEGER:
                    return readBigInteger();
                case TYPE_LONG:
                    return addRef.run(factory.createInt(readBigInteger()));
                case TYPE_FLOAT:
                    return addRef.run(readDoubleString());
                case TYPE_BINARY_FLOAT:
                    return addRef.run(readDouble());
                case TYPE_COMPLEX:
                    return addRef.run(factory.createComplex(readDoubleString(), readDoubleString()));
                case TYPE_BINARY_COMPLEX:
                    return addRef.run(factory.createComplex(readDouble(), readDouble()));
                case TYPE_STRING:
                    return addRef.run(factory.createBytes(readBytes()));
                case TYPE_ASCII_INTERNED:
                    return addRef.run(readAscii(readSize(), true));
                case TYPE_ASCII:
                    return addRef.run(readAscii(readSize(), false));
                case TYPE_SHORT_ASCII_INTERNED:
                    return addRef.run(readAscii(readByteSize(), true));
                case TYPE_SHORT_ASCII:
                    return addRef.run(readAscii(readByteSize(), false));
                case TYPE_INTERNED:
                    return addRef.run(StringNodes.InternStringNode.executeUncached(readString()));
                case TYPE_UNICODE:
                    return addRef.run(readString());
                case TYPE_SMALL_TUPLE:
                    int smallTupleSize = readByteSize();
                    Object[] smallTupleItems = new Object[smallTupleSize];
                    Object smallTuple = addRef.run(factory.createTuple(smallTupleItems));
                    readArray(smallTupleItems);
                    return smallTuple;
                case TYPE_TUPLE:
                    int tupleSize = readSize();
                    Object[] tupleItems = new Object[tupleSize];
                    Object tuple = addRef.run(factory.createTuple(tupleItems));
                    readArray(tupleItems);
                    return tuple;
                case TYPE_LIST:
                    int listSize = readSize();
                    Object[] listItems = new Object[listSize];
                    Object list = addRef.run(factory.createList(listItems));
                    readArray(listItems);
                    return list;
                case TYPE_DICT:
                    HashingStorage store = PDict.createNewStorage(0);
                    PDict dict = factory.createDict(store);
                    addRef.run(dict);
                    while (true) {
                        Object key = readObject();
                        if (key == null) {
                            break;
                        }
                        Object value = readObject();
                        if (value != null) {
                            store = HashingStorageSetItem.executeUncached(store, key, value);
                        }
                    }
                    dict.setDictStorage(store);
                    return dict;
                case TYPE_SET:
                case TYPE_FROZENSET:
                    int setSz = readSize();
                    HashingStorage setStore = EconomicMapStorage.create(setSz);
                    PBaseSet set;
                    if (type == TYPE_FROZENSET) {
                        set = factory.createFrozenSet(setStore);
                    } else {
                        set = factory.createSet(setStore);
                    }
                    addRef.run(set);
                    for (int i = 0; i < setSz; i++) {
                        Object key = readObject();
                        if (key == null) {
                            throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
                        }
                        setStore = HashingStorageSetItem.executeUncached(setStore, key, PNone.NO_VALUE);
                    }
                    set.setDictStorage(setStore);
                    return set;
                case TYPE_GRAALPYTHON_CODE:
                    return addRef.run(readCode());
                case TYPE_GRAALPYTHON_CODE_UNIT:
                    return addRef.run(readBytecodeCodeUnit());
                case TYPE_GRAALPYTHON_DSL_CODE_UNIT:
                    return addRef.run(readBytecodeDSLCodeUnit());
                case TYPE_DSL_SOURCE:
                    return getSource();
                case TYPE_DSL_EMPTY_KEYWORDS:
                    return PKeyword.EMPTY_KEYWORDS;
                case TYPE_ARRAY:
                    return addRef.run(readJavaArray());
                default:
                    throw new MarshalError(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
        }

        private void writeString(TruffleString v) {
            /*
             * Ugly workaround for GR-39571 - TruffleString UTF-8 doesn't support surrogate
             * passthrough. If the string contains surrogates, we mark it and emit it as UTF-32.
             */
            Encoding encoding;
            if (v.isValidUncached(TS_ENCODING)) {
                encoding = Encoding.UTF_8;
            } else {
                encoding = Encoding.UTF_32LE;
                writeInt(-1);
            }
            InternalByteArray ba = v.switchEncodingUncached(encoding).getInternalByteArrayUncached(encoding);
            writeSize(ba.getLength());
            writeBytes(ba.getArray(), ba.getOffset(), ba.getLength());
        }

        private TruffleString readString() {
            Encoding encoding = Encoding.UTF_8;
            int sz = readInt();
            if (sz < 0) {
                encoding = Encoding.UTF_32LE;
                sz = readSize();
            }
            return TruffleString.fromByteArrayUncached(readNBytes(sz), 0, sz, encoding, true).switchEncodingUncached(TS_ENCODING);
        }

        private void writeShortString(String v) throws IOException {
            byte[] bytes = v.getBytes(StandardCharsets.ISO_8859_1);
            assert bytes.length < 256;
            writeByte(bytes.length);
            out.write(bytes);
        }

        private TruffleString readShortString() {
            int sz = readByteSize();
            byte[] bytes = readNBytes(sz);
            return TruffleString.fromByteArrayUncached(bytes, 0, sz, Encoding.ISO_8859_1, true).switchEncodingUncached(TS_ENCODING);
        }

        private Object readAscii(long sz, boolean intern) {
            byte[] bytes = readNBytes((int) sz);
            TruffleString value = TruffleString.fromByteArrayUncached(bytes, 0, (int) sz, Encoding.US_ASCII, true).switchEncodingUncached(TS_ENCODING);
            if (intern) {
                return StringNodes.InternStringNode.executeUncached(value);
            } else {
                return value;
            }
        }

        private void readArray(Object[] items) throws NumberFormatException {
            for (int i = 0; i < items.length; i++) {
                Object item = readObject();
                if (item == null) {
                    throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA);
                }
                items[i] = item;
            }
        }

        private Object readJavaArray() {
            int type = readByte();
            switch (type) {
                case ARRAY_TYPE_BYTE:
                    return readBytes();
                case ARRAY_TYPE_INT:
                    return readIntArray();
                case ARRAY_TYPE_LONG:
                    return readLongArray();
                case ARRAY_TYPE_DOUBLE:
                    return readDoubleArray();
                case ARRAY_TYPE_SHORT:
                    return readShortArray();
                case ARRAY_TYPE_BOOLEAN:
                    return readBooleanArray();
                case ARRAY_TYPE_STRING:
                    return readStringArray();
                case ARRAY_TYPE_OBJECT:
                    return readObjectArray();
                default:
                    throw CompilerDirectives.shouldNotReachHere("unknown array type");
            }
        }

        private int[] readIntArray() {
            int length = readInt();
            int[] a = new int[length];
            for (int i = 0; i < length; i++) {
                a[i] = readInt();
            }
            return a;
        }

        private long[] readLongArray() {
            int length = readInt();
            long[] a = new long[length];
            for (int i = 0; i < length; i++) {
                a[i] = readLong();
            }
            return a;
        }

        private double[] readDoubleArray() {
            int length = readInt();
            double[] a = new double[length];
            for (int i = 0; i < length; i++) {
                a[i] = readDouble();
            }
            return a;
        }

        private short[] readShortArray() {
            int length = readInt();
            short[] a = new short[length];
            for (int i = 0; i < length; i++) {
                a[i] = readShort();
            }
            return a;
        }

        private boolean[] readBooleanArray() {
            int length = readInt();
            boolean[] a = new boolean[length];
            for (int i = 0; i < length; i++) {
                a[i] = readByte() != 0;
            }
            return a;
        }

        private TruffleString[] readStringArray() {
            int length = readInt();
            TruffleString[] a = new TruffleString[length];
            for (int i = 0; i < length; i++) {
                a[i] = readString();
            }
            return a;
        }

        private Object[] readObjectArray() {
            int length = readInt();
            Object[] a = new Object[length];
            for (int i = 0; i < length; i++) {
                a[i] = readObject();
            }
            return a;
        }

        private void setSource(Source s) {
            if (source == null) {
                source = s;
            } else if (source != s) {
                throw CompilerDirectives.shouldNotReachHere("attempted to serialize with multiple Source objects");
            }
        }

        private Source getSource() {
            assert source != null;
            return source;
        }

        private void writeSparseTable(int[][] table) {
            writeInt(table.length);
            for (int i = 0; i < table.length; i++) {
                if (table[i] != null && table[i].length > 0) {
                    writeInt(i);
                    writeIntArray(table[i]);
                }
            }
            writeInt(-1);
        }

        private int[][] readSparseTable() {
            int length = readInt();
            int[][] table = new int[length][];
            while (true) {
                int i = readInt();
                if (i == -1) {
                    return table;
                }
                table[i] = readIntArray();
            }
        }

        private CodeUnit readCodeUnit() {
            int codeUnitType = readByte();
            return switch (codeUnitType) {
                case TYPE_GRAALPYTHON_CODE_UNIT -> readBytecodeCodeUnit();
                case TYPE_GRAALPYTHON_DSL_CODE_UNIT -> readBytecodeDSLCodeUnit();
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        private BytecodeCodeUnit readBytecodeCodeUnit() {
            int fileVersion = readByte();
            if (fileVersion != Compiler.BYTECODE_VERSION) {
                throw new MarshalError(ValueError, ErrorMessages.BYTECODE_VERSION_MISMATCH, Compiler.BYTECODE_VERSION, fileVersion);
            }
            TruffleString name = readString();
            TruffleString qualname = readString();
            int argCount = readInt();
            int kwOnlyArgCount = readInt();
            int positionalOnlyArgCount = readInt();
            int stacksize = readInt();
            byte[] code = readBytes();
            byte[] srcOffsetTable = readBytes();
            int flags = readInt();
            TruffleString[] names = readStringArray();
            TruffleString[] varnames = readStringArray();
            TruffleString[] cellvars = readStringArray();
            TruffleString[] freevars = readStringArray();
            int[] cell2arg = readIntArray();
            if (cell2arg.length == 0) {
                cell2arg = null;
            }
            Object[] constants = readObjectArray();
            long[] primitiveConstants = readLongArray();
            int[] exceptionHandlerRanges = readIntArray();
            int conditionProfileCount = readInt();
            int startLine = readInt();
            int startColumn = readInt();
            int endLine = readInt();
            int endColumn = readInt();
            byte[] outputCanQuicken = readBytes();
            byte[] variableShouldUnbox = readBytes();
            int[][] generalizeInputsMap = readSparseTable();
            int[][] generalizeVarsMap = readSparseTable();
            return new BytecodeCodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames,
                            cellvars, freevars, cell2arg, constants, startLine, startColumn, endLine, endColumn, code, srcOffsetTable,
                            primitiveConstants, exceptionHandlerRanges, stacksize, conditionProfileCount,
                            outputCanQuicken, variableShouldUnbox, generalizeInputsMap, generalizeVarsMap);
        }

        private BytecodeDSLCodeUnit readBytecodeDSLCodeUnit() {
            byte[] serialized = readBytes();
            TruffleString name = readString();
            TruffleString qualname = readString();
            int argCount = readInt();
            int kwOnlyArgCount = readInt();
            int positionalOnlyArgCount = readInt();
            int flags = readInt();
            TruffleString[] names = readStringArray();
            TruffleString[] varnames = readStringArray();
            TruffleString[] cellvars = readStringArray();
            TruffleString[] freevars = readStringArray();
            int[] cell2arg = readIntArray();
            if (cell2arg.length == 0) {
                cell2arg = null;
            }
            Object[] constants = readObjectArray();
            int startLine = readInt();
            int startColumn = readInt();
            int endLine = readInt();
            int endColumn = readInt();

            return new BytecodeDSLCodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames, cellvars, freevars, cell2arg, constants,
                            startLine, startColumn, endLine, endColumn, serialized, null);
        }

        private void writeCodeUnit(CodeUnit code) throws IOException {
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                writeByte(TYPE_GRAALPYTHON_DSL_CODE_UNIT);
                writeBytecodeDSLCodeUnit((BytecodeDSLCodeUnit) code);
            } else {
                writeByte(TYPE_GRAALPYTHON_CODE_UNIT);
                writeBytecodeCodeUnit((BytecodeCodeUnit) code);
            }
        }

        private void writeBytecodeCodeUnit(BytecodeCodeUnit code) throws IOException {
            writeByte(Compiler.BYTECODE_VERSION);
            writeString(code.name);
            writeString(code.qualname);
            writeInt(code.argCount);
            writeInt(code.kwOnlyArgCount);
            writeInt(code.positionalOnlyArgCount);
            writeInt(code.stacksize);
            writeBytes(code.code);
            writeBytes(code.srcOffsetTable);
            writeInt(code.flags);
            writeStringArray(code.names);
            writeStringArray(code.varnames);
            writeStringArray(code.cellvars);
            writeStringArray(code.freevars);
            if (code.cell2arg != null) {
                writeIntArray(code.cell2arg);
            } else {
                writeIntArray(PythonUtils.EMPTY_INT_ARRAY);
            }
            writeObjectArray(code.constants);
            writeLongArray(code.primitiveConstants);
            writeIntArray(code.exceptionHandlerRanges);
            writeInt(code.conditionProfileCount);
            writeInt(code.startLine);
            writeInt(code.startColumn);
            writeInt(code.endLine);
            writeInt(code.endColumn);
            writeBytes(code.outputCanQuicken);
            writeBytes(code.variableShouldUnbox);
            writeSparseTable(code.generalizeInputsMap);
            writeSparseTable(code.generalizeVarsMap);
        }

        @SuppressWarnings("unchecked")
        private void writeBytecodeDSLCodeUnit(BytecodeDSLCodeUnit code) throws IOException {
            byte[] serialized = code.getSerialized(pyTrue, pyFalse);
            writeBytes(serialized);
            writeString(code.name);
            writeString(code.qualname);
            writeInt(code.argCount);
            writeInt(code.kwOnlyArgCount);
            writeInt(code.positionalOnlyArgCount);
            writeInt(code.flags);
            writeStringArray(code.names);
            writeStringArray(code.varnames);
            writeStringArray(code.cellvars);
            writeStringArray(code.freevars);
            if (code.cell2arg != null) {
                writeIntArray(code.cell2arg);
            } else {
                writeIntArray(PythonUtils.EMPTY_INT_ARRAY);
            }
            writeObjectArray(code.constants);
            writeInt(code.startLine);
            writeInt(code.startColumn);
            writeInt(code.endLine);
            writeInt(code.endColumn);
        }

        private PCode readCode() {
            TruffleString fileName = readString();
            int flags = readInt();

            int codeLen = readSize();
            byte[] codeString = new byte[codeLen + Long.BYTES];
            try {
                in.readFully(codeString, 0, codeLen);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            // get a new ID every time we deserialize the same filename in the same context. We use
            // slow-path context lookup, since this code is likely dominated by the deserialization
            // time
            PythonContext context = PythonContext.get(null);
            ByteBuffer.wrap(codeString).putLong(codeLen, context.getDeserializationId(fileName));
            int firstLineNo = readInt();
            byte[] lnoTab = readBytes();
            return CreateCodeNode.createCode(context, flags, codeString, fileName, firstLineNo, lnoTab);
        }
    }

    @TruffleBoundary
    public static byte[] serializeCodeUnit(CodeUnit code) {
        try {
            Marshal marshal = new Marshal(CURRENT_VERSION, null, null);
            marshal.writeCodeUnit(code);
            return marshal.outData.toByteArray();
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } catch (Marshal.MarshalError me) {
            throw PRaiseNode.getUncached().raise(me.type, me.message, me.arguments);
        }
    }

    @TruffleBoundary
    public static CodeUnit deserializeCodeUnit(byte[] bytes) {
        try {
            Marshal marshal = new Marshal(bytes, bytes.length);
            return marshal.readCodeUnit();
        } catch (Marshal.MarshalError me) {
            throw PRaiseNode.getUncached().raise(me.type, me.message, me.arguments);
        } catch (NumberFormatException e) {
            throw PRaiseNode.getUncached().raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
        }
    }

    public static BytecodeRootNodes<PBytecodeDSLRootNode> deserializeBytecodeNodes(PythonLanguage language, Source source, byte[] serialized) {
        try {
            Supplier<DataInput> supplier = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(serialized));
            return PBytecodeDSLRootNodeGen.deserialize(language, BytecodeConfig.WITH_SOURCE, supplier, new MarshalModuleBuiltins.PBytecodeDSLDeserializer(source));
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere("Deserialization error.");
        }
    }

    public static class PBytecodeDSLSerializer implements BytecodeSerializer {
        private final PInt pyTrue;
        private final PInt pyFalse;

        public PBytecodeDSLSerializer(PInt pyTrue, PInt pyFalse) {
            this.pyTrue = pyTrue;
            this.pyFalse = pyFalse;
        }

        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            /*
             * NB: Since the deserializer uses a fresh Marshal instance for each object (see below)
             * we must also do the same here. Otherwise, the encoding may be different (e.g., a
             * reference for an already-emitted object).
             */
            new Marshal(CURRENT_VERSION, pyTrue, pyFalse, buffer).writeObject(object);
        }
    }

    public static class PBytecodeDSLDeserializer implements BytecodeDeserializer {
        final Source source;

        public PBytecodeDSLDeserializer(Source source) {
            this.source = source;
        }

        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            /*
             * NB: Since a DSL node may reparse multiple times, we cannot reuse a common Marshal
             * object across calls (each call may take a different buffer).
             */
            return new Marshal(buffer, source).readObject();
        }
    }
}
