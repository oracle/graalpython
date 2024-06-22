/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_ITER_CANNOT_UNPACK_FROM_STRUCT_OF_SIZE_0;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_ITER_UNPACK_REQ_A_BUFFER_OF_A_MUL_OF_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_NOT_ENOUGH_DATA_TO_UNPACK_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_NO_SPACE_TO_PACK_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_OFFSET_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_PACK_INTO_REQ_BUFFER_TO_PACK;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_UNPACK_FROM_REQ_AT_LEAST_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.UNPACK_REQ_A_BUFFER_OF_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StructError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PStructUnpackIterator;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PStruct)
public class StructBuiltins extends PythonBuiltins {
    static void packInternal(VirtualFrame frame, PStruct self, StructNodes.PackValueNode packValueNode, Object[] args, byte[] buffer, int offset) {
        assert self.getSize() <= buffer.length - offset;
        FormatCode[] codes = self.getCodes();
        int pos = 0;
        for (FormatCode code : codes) {
            int buffer_offset = offset + code.offset;
            for (int j = 0; j < code.repeat; j++, pos++) {
                packValueNode.execute(frame, code, self.formatAlignment, args[pos], buffer, buffer_offset);
                buffer_offset += code.size;
            }
        }
    }

    public static Object[] unpackInternal(PStruct self, StructNodes.UnpackValueNode unpackValueNode, byte[] bytes, int offset) {
        Object[] values = new Object[self.getLen()];
        FormatCode[] codes = self.getCodes();
        int pos = 0;
        for (FormatCode code : codes) {
            int buffer_offset = offset + code.offset;
            for (int j = 0; j < code.repeat; j++, pos++) {
                Object value = unpackValueNode.execute(code, self.formatAlignment, bytes, buffer_offset);
                values[pos] = value;
                buffer_offset += code.size;
            }
        }
        return values;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructBuiltinsFactory.getFactories();
    }

    @Builtin(name = "pack", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, takesVarArgs = true, takesVarKeywordArgs = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    public abstract static class StructPackNode extends PythonVarargsBuiltinNode {
        public final Object execute(VirtualFrame frame, PStruct self, Object[] args) {
            return execute(frame, self, args, PKeyword.EMPTY_KEYWORDS);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return this.execute(frame, self, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization
        Object pack(VirtualFrame frame, PStruct self, Object[] args, PKeyword[] keywords,
                        @Cached StructNodes.PackValueNode packValueNode,
                        @Cached PythonObjectFactory factory) {
            if (keywords.length != 0) {
                throw raise(TypeError, S_TAKES_NO_KEYWORD_ARGS, "pack()");
            }
            if (args.length != self.getLen()) {
                throw raise(StructError, STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K, self.getLen(), args.length);
            }
            byte[] bytes = new byte[self.getSize()];
            packInternal(frame, self, packValueNode, args, bytes, 0);
            return factory.createBytes(bytes);
        }
    }

    @Builtin(name = "pack_into", minNumOfPositionalArgs = 3, parameterNames = {"$self", "buffer", "offset"}, takesVarArgs = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class StructPackIntoNode extends PythonClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer, int offset, Object[] args);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructPackIntoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object packInto(VirtualFrame frame, PStruct self, Object buffer, int offset, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.PackValueNode packValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                final long size = self.getUnsignedSize();
                if (args.length != self.getLen()) {
                    throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K, size, args.length);
                }
                int bufferOffset = offset;
                int bufferLen = bufferLib.getBufferLength(buffer);
                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    bytes = new byte[self.getSize()];
                }

                // support negative offsets
                if (bufferOffset < 0) {
                    // Check that negative offset is low enough to fit data
                    if (bufferOffset + size > 0) {
                        throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_NO_SPACE_TO_PACK_N_BYTES, size, bufferOffset);
                    }

                    // Check that negative offset is not crossing buffer boundary
                    if (bufferOffset + bufferLen < 0) {
                        throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_OFFSET_OUT_OF_RANGE, bufferOffset, bufferLen);
                    }

                    bufferOffset += bufferLen;
                }

                // Check boundaries
                if ((bufferLen - bufferOffset) < size) {
                    assert bufferOffset >= 0;
                    assert size >= 0;

                    throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_PACK_INTO_REQ_BUFFER_TO_PACK, size + bufferOffset, size, bufferOffset, bufferLen);
                }

                // TODO: GR-54860 use buffer API in the packing process
                packInternal(frame, self, packValueNode, args, bytes, directWrite ? bufferOffset : 0);
                if (!directWrite) {
                    bufferLib.writeFromByteArray(buffer, bufferOffset, bytes, 0, bytes.length);
                }
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @NeverDefault
        public static StructPackIntoNode create() {
            return StructBuiltinsFactory.StructPackIntoNodeFactory.create(null);
        }
    }

    @Builtin(name = "unpack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class StructUnpackNode extends PythonBinaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object unpack(VirtualFrame frame, PStruct self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int bytesLen = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                if (bytesLen != self.getSize()) {
                    throw raiseNode.get(inliningTarget).raise(StructError, UNPACK_REQ_A_BUFFER_OF_N_BYTES, self.getSize());
                }
                return factory.createTuple(unpackInternal(self, unpackValueNode, bytes, 0));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "iter_unpack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class StructIterUnpackNode extends PythonBinaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructIterUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object iterUnpack(VirtualFrame frame, PStruct self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                if (self.getSize() == 0) {
                    throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_ITER_CANNOT_UNPACK_FROM_STRUCT_OF_SIZE_0);
                }
                int bufferLen = bufferLib.getBufferLength(buffer);
                if (bufferLen % self.getSize() != 0) {
                    throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_ITER_UNPACK_REQ_A_BUFFER_OF_A_MUL_OF_BYTES, self.getSize());
                }
            } catch (Exception e) {
                bufferLib.release(buffer, frame, indirectCallData);
                throw e;
            }
            // The buffer ownership is transferred to the iterator
            // TODO: GR-54860 release it when iterator is collected
            final PStructUnpackIterator structUnpackIterator = factory.createStructUnpackIterator(self, buffer);
            structUnpackIterator.index = 0;
            return structUnpackIterator;
        }
    }

    @Builtin(name = "unpack_from", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "offset"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    public abstract static class StructUnpackFromNode extends PythonTernaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer, int offset);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructUnpackFromNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object unpackFrom(VirtualFrame frame, PStruct self, Object buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int bufferOffset = offset;
                int bytesLen = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                final long size = self.getUnsignedSize();
                if (bufferOffset < 0) {
                    if (bufferOffset + size > 0) {
                        throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_NOT_ENOUGH_DATA_TO_UNPACK_N_BYTES, size, bufferOffset);
                    }

                    if (bufferOffset + bytesLen < 0) {
                        throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_OFFSET_OUT_OF_RANGE, bufferOffset, bytesLen);
                    }
                    bufferOffset += bytesLen;
                }

                if ((bytesLen - bufferOffset) < size) {
                    throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_UNPACK_FROM_REQ_AT_LEAST_N_BYTES, size + bufferOffset, size, bufferOffset, bytesLen);
                }

                return factory.createTuple(unpackInternal(self, unpackValueNode, bytes, bufferOffset));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "calcsize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StructCalcSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object calcSize(PStruct self) {
            return self.getSize();
        }
    }

    @Builtin(name = "size", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStructSizeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PStruct self) {
            return self.getSize();
        }
    }

    @Builtin(name = "format", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStructFormat extends PythonBuiltinNode {
        @Specialization
        protected Object get(PStruct self,
                        @Cached TruffleString.FromByteArrayNode fromBytes,
                        @Cached TruffleString.SwitchEncodingNode switchEncoding) {
            return switchEncoding.execute(fromBytes.execute(self.getFormat(), TruffleString.Encoding.US_ASCII), TS_ENCODING);
        }
    }
}
