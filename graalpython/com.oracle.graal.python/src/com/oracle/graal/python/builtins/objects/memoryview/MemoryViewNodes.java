package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

public class MemoryViewNodes {
    static int bytesize(IntrinsifiedPMemoryView.BufferFormat format) {
        // TODO fetch from sulong
        switch (format) {
            case UNSIGNED_BYTE:
            case SIGNED_BYTE:
            case CHAR:
            case BOOLEAN:
                return 1;
            case UNSIGNED_SHORT:
            case SIGNED_SHORT:
                return 2;
            case SIGNED_INT:
            case UNSIGNED_INT:
            case FLOAT:
                return 4;
            case UNSIGNED_LONG:
            case SIGNED_LONG:
            case UNSIGNED_SIZE:
            case SIGNED_SIZE:
            case SIGNED_LONG_LONG:
            case UNSIGNED_LONG_LONG:
            case DOUBLE:
            case POINTER:
                return 8;
        }
        return -1;
    }

    static boolean isByteFormat(IntrinsifiedPMemoryView.BufferFormat format) {
        return format == IntrinsifiedPMemoryView.BufferFormat.UNSIGNED_BYTE || format == IntrinsifiedPMemoryView.BufferFormat.SIGNED_BYTE || format == IntrinsifiedPMemoryView.BufferFormat.CHAR;
    }

    public static abstract class InitFlagsNode extends Node {
        public abstract int execute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets);

        @Specialization
        static int compute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets) {
            if (ndim == 0) {
                return IntrinsifiedPMemoryView.FLAG_C | IntrinsifiedPMemoryView.FLAG_FORTRAN | IntrinsifiedPMemoryView.FLAG_SCALAR;
            } else if (suboffsets != null) {
                return IntrinsifiedPMemoryView.FLAG_PIL;
            } else {
                int flags = IntrinsifiedPMemoryView.FLAG_C | IntrinsifiedPMemoryView.FLAG_FORTRAN;
                int expectedStride = itemsize;
                for (int i = ndim - 1; i >= 0; i--) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~IntrinsifiedPMemoryView.FLAG_C;
                        break;
                    }
                    expectedStride *= dim;
                }
                expectedStride = itemsize;
                for (int i = 0; i < ndim; i++) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~IntrinsifiedPMemoryView.FLAG_FORTRAN;
                        break;
                    }
                    expectedStride *= dim;
                }
                return flags;
            }
        }
    }

    @ImportStatic(IntrinsifiedPMemoryView.BufferFormat.class)
    static abstract class UnpackValueNode extends Node {
        public abstract Object execute(IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes);

        @Specialization(guards = "format == UNSIGNED_BYTE")
        static int unpackUnsignedByte(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes) {
            return bytes[0] & 0xFF;
        }

        @Specialization(guards = "format == SIGNED_BYTE")
        static int unpackSignedByte(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes) {
            return bytes[0];
        }

        @Specialization(guards = "format == SIGNED_SHORT")
        static int unpackShort(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
        }

        @Specialization(guards = "format == SIGNED_INT")
        static int unpackInt(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
        }

        @Specialization(guards = "format == SIGNED_LONG")
        static long unpackLong(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24 |
                            (bytes[4] & 0xFFL) << 32 | (bytes[5] & 0xFFL) << 40 | (bytes[6] & 0xFFL) << 48 | (bytes[7] & 0xFFL) << 56;
        }

        // TODO rest of formats
    }

    @ImportStatic(IntrinsifiedPMemoryView.BufferFormat.class)
    static abstract class PackValueNode extends Node {
        @Child private PRaiseNode raiseNode;

        // Output goes to bytes, lenght not checked
        public abstract void execute(IntrinsifiedPMemoryView.BufferFormat format, Object object, byte[] bytes);

        @Specialization(guards = "format == UNSIGNED_BYTE", limit = "2")
        void packUnsignedByte(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 1;
            long value = lib.asJavaLong(object);
            if (value < 0 || value > 0xFF) {
                throw raise(ValueError, ErrorMessages.MEMORYVIEW_INVALID_VALUE_FOR_FORMAT_S, format);
            }
            bytes[0] = (byte) value;
        }

        @Specialization(guards = "format == SIGNED_LONG", limit = "2")
        static void packLong(@SuppressWarnings("unused") IntrinsifiedPMemoryView.BufferFormat format, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 8;
            long value = lib.asJavaLong(object);
            for (int i = 7; i >= 0; i--) {
                bytes[i] = (byte) (value & 0xFFL);
                value >>= 8;
            }
        }

        private PException raise(PythonBuiltinClassType type, String message, Object... args) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(type, message, args);
        }
    }

    @GenerateUncached
    static abstract class ReadBytesAtNode extends Node {
        public abstract void execute(byte[] dest, int destOffset, int len, IntrinsifiedPMemoryView self, Object ptr, int offset);

        @Specialization(guards = "ptr != null")
        static void doNative(byte[] dest, int destOffset, int len, @SuppressWarnings("unused") IntrinsifiedPMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    dest[destOffset + i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr == null")
        static void doManaged(byte[] dest, int destOffset, int len, IntrinsifiedPMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < len; i++) {
                dest[destOffset + i] = (byte) getItemNode.executeInt(storage, offset + i);
            }
        }
    }

    @GenerateUncached
    static abstract class WriteBytesAtNode extends Node {
        public abstract void execute(byte[] src, int srcOffset, int len, IntrinsifiedPMemoryView self, Object ptr, int offset);

        @Specialization(guards = "ptr != null")
        static void doNative(byte[] src, int srcOffset, int len, @SuppressWarnings("unused") IntrinsifiedPMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(ptr, offset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr == null")
        static void doManaged(byte[] src, int srcOffset, int len, IntrinsifiedPMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes byte storage
            SequenceStorage storage = getStorageNode.execute(self.getOwner());
            for (int i = 0; i < len; i++) {
                setItemNode.execute(storage, offset + i, src[srcOffset + i]);
            }
        }
    }

    static abstract class ReadItemAtNode extends Node {
        public abstract Object execute(IntrinsifiedPMemoryView self, Object ptr, int offset);

        @Specialization(guards = "ptr != null")
        static Object doNative(IntrinsifiedPMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached UnpackValueNode unpackValueNode) {
            int itemsize = self.getItemSize();
            byte[] bytes = new byte[itemsize];
            try {
                for (int i = 0; i < itemsize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
            return unpackValueNode.execute(self.getFormat(), bytes);
        }

        @Specialization(guards = "ptr == null")
        static Object doManaged(IntrinsifiedPMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached UnpackValueNode unpackValueNode) {
            // TODO assumes byte storage
            byte[] bytes = new byte[self.getItemSize()];
            for (int i = 0; i < self.getItemSize(); i++) {
                bytes[i] = (byte) getItemNode.executeInt(getStorageNode.execute(self.getOwner()), offset + i);
            }
            return unpackValueNode.execute(self.getFormat(), bytes);
        }
    }

    static abstract class WriteItemAtNode extends Node {
        public abstract void execute(VirtualFrame frame, IntrinsifiedPMemoryView self, Object ptr, int offset, Object object);

        @Specialization(guards = "ptr != null")
        static void doNative(IntrinsifiedPMemoryView self, Object ptr, int offset, Object object,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PackValueNode packValueNode) {
            int itemsize = self.getItemSize();
            byte[] bytes = new byte[itemsize];
            packValueNode.execute(self.getFormat(), object, bytes);
            try {
                for (int i = 0; i < itemsize; i++) {
                    lib.writeArrayElement(ptr, offset + i, bytes[i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr == null")
        static void doManaged(IntrinsifiedPMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @Cached PackValueNode packValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO assumes bytes storage
            byte[] bytes = new byte[self.getItemSize()];
            packValueNode.execute(self.getFormat(), object, bytes);
            for (int i = 0; i < self.getItemSize(); i++) {
                setItemNode.execute(getStorageNode.execute(self.getOwner()), offset + i, bytes[i]);
            }
        }
    }

    @ValueType
    static class MemoryPointer {
        public Object ptr;
        public int offset;

        public MemoryPointer(Object ptr, int offset) {
            this.ptr = ptr;
            this.offset = offset;
        }
    }

    @ImportStatic(PGuards.class)
    static abstract class PointerLookupNode extends Node {
        @Child private PRaiseNode raiseNode;
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;

        // index can be a tuple, int or int-convertible
        public abstract MemoryPointer execute(VirtualFrame frame, IntrinsifiedPMemoryView self, Object index);

        public abstract MemoryPointer execute(VirtualFrame frame, IntrinsifiedPMemoryView self, int index);

        private void lookupDimension(IntrinsifiedPMemoryView self, MemoryPointer ptr, int dim, int index) {
            int[] shape = self.getBufferShape();
            int nitems = shape[dim];
            if (index < 0) {
                index += nitems;
            }
            if (index < 0 || index >= nitems) {
                throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
            }

            ptr.offset += self.getBufferStrides()[dim] * index;

            int[] suboffsets = self.getBufferSuboffsets();
            if (suboffsets != null && suboffsets[dim] >= 0) {
                // The length may be out of bounds, but sulong shouldn't care if we don't
                // access the out-of-bound part
                ptr.ptr = getCallCapiFunction().call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr.ptr, ptr.offset, suboffsets[dim], self.getLength());
                ptr.offset = 0;
            }
        }

        @Specialization
        MemoryPointer resolveInt(IntrinsifiedPMemoryView self, int index) {
            if (self.getDimensions() > 1) {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
            } else if (self.getDimensions() == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            lookupDimension(self, ptr, 0, index);
            return ptr;
        }

        // TODO explode loop
        @Specialization
        MemoryPointer resolveTuple(IntrinsifiedPMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared("indexLib") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            int ndim = self.getDimensions();
            checkTupleLength(lenNode, indicesStorage, ndim);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < ndim; dim++) {
                Object indexObj = getItemNode.execute(indicesStorage, dim);
                int index = convertIndex(lib, indexObj);
                lookupDimension(self, ptr, dim, index);
            }
            return ptr;
        }

        @Specialization(guards = "!isPTuple(indexObj)")
        MemoryPointer resolveInt(IntrinsifiedPMemoryView self, Object indexObj,
                        @Shared("indexLib") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            return resolveInt(self, convertIndex(lib, indexObj));
        }

        private void checkTupleLength(SequenceStorageNodes.LenNode lenNode, SequenceStorage indicesStorage, int ndim) {
            int length = lenNode.execute(indicesStorage);
            if (ndim == 0 && length != 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            } else if (length > ndim) {
                throw raise(TypeError, ErrorMessages.CANNOT_INDEX_D_DIMENSION_VIEW_WITH_D, ndim, length);
            } else if (length < ndim) {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.SUB_VIEWS_NOT_IMPLEMENTED);
            }
        }

        private int convertIndex(PythonObjectLibrary lib, Object indexObj) {
            if (!lib.canBeIndex(indexObj)) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
            }
            return lib.asSize(indexObj, IndexError);
        }

        private PException raise(PythonBuiltinClassType type, String message, Object... args) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(type, message, args);
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @GenerateUncached
    public static abstract class ToJavaBytesNode extends Node {
        public abstract byte[] execute(IntrinsifiedPMemoryView self);

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"})
        byte[] tobytesCached(IntrinsifiedPMemoryView self,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            byte[] bytes = new byte[self.getLength()];
            if (cachedDimensions == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convert(bytes, self, cachedDimensions, readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @Specialization(replaces = "tobytesCached")
        byte[] tobytesGeneric(IntrinsifiedPMemoryView self,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            byte[] bytes = new byte[self.getLength()];
            if (self.getDimensions() == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convertBoundary(bytes, self, self.getDimensions(), readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @TruffleBoundary
        private void convertBoundary(byte[] dest, IntrinsifiedPMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            convert(dest, self, ndim, readBytesAtNode, callCapiFunction);
        }

        protected void convert(byte[] dest, IntrinsifiedPMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static int recursive(byte[] dest, int destOffset, IntrinsifiedPMemoryView self, int dim, int ndim, Object ptr, int offset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                    destOffset += self.getItemSize();
                } else {
                    destOffset = recursive(dest, destOffset, self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                offset += self.getBufferStrides()[dim];
            }
            return destOffset;
        }

        public static ToJavaBytesNode create() {
            return MemoryViewNodesFactory.ToJavaBytesNodeGen.create();
        }
    }

    @GenerateUncached
    public static abstract class ToJavaBytesFortranOrderNode extends ToJavaBytesNode {
        @Override
        protected void convert(byte[] dest, IntrinsifiedPMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self.getItemSize(), self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static void recursive(byte[] dest, int destOffset, int destStride, IntrinsifiedPMemoryView self, int dim, int ndim, Object ptr, int offset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                } else {
                    recursive(dest, destOffset, destStride * self.getBufferShape()[dim], self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                destOffset += destStride;
                offset += self.getBufferStrides()[dim];
            }
        }

        public static ToJavaBytesFortranOrderNode create() {
            return MemoryViewNodesFactory.ToJavaBytesFortranOrderNodeGen.create();
        }
    }

    @GenerateUncached
    public static abstract class ReleaseBufferOfManagedObjectNode extends Node {
        public abstract void execute(Object object);

        @Specialization
        static void bytearray(PByteArray object) {
            // TODO
        }

        public static ReleaseBufferOfManagedObjectNode create() {
            return MemoryViewNodesFactory.ReleaseBufferOfManagedObjectNodeGen.create();
        }

        public static ReleaseBufferOfManagedObjectNode getUncached() {
            return MemoryViewNodesFactory.ReleaseBufferOfManagedObjectNodeGen.getUncached();
        }
    }

    public static abstract class GetBufferReferences extends Node {
        public abstract BufferReferences execute();

        @Specialization
        @SuppressWarnings("unchecked")
        static BufferReferences getRefs(@CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ReadAttributeFromObjectNode readNode) {
            return (BufferReferences) readNode.execute(context.getCore().lookupType(PythonBuiltinClassType.PMemoryView), MemoryViewBuiltins.bufferReferencesKey);
        }
    }

    public static class BufferReferences {
        public final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        public final Set<BufferReference> set = new HashSet<>();
    }
}
