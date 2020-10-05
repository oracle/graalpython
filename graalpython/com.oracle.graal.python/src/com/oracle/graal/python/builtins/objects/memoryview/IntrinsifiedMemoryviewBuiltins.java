package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.IntrinsifiedPMemoryView)
public class IntrinsifiedMemoryviewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IntrinsifiedMemoryviewBuiltinsFactory.getFactories();
    }

    static abstract class UnpackValueNode extends Node {
        public abstract Object execute(String format, byte[] bytes);

        static final char B = 'B';
        static final char b = 'b';
        static final char h = 'h';
        static final char i = 'i';
        static final char l = 'l';

        @Specialization(guards = "format == null || format.charAt(0) == B")
        static int unpackUnsignedByte(@SuppressWarnings("unused") String format, byte[] bytes) {
            return bytes[0] & 0xFF;
        }

        @Specialization(guards = "format.charAt(0) == b")
        static int unpackSignedByte(@SuppressWarnings("unused") String format, byte[] bytes) {
            return bytes[0];
        }

        @Specialization(guards = "format.charAt(0) == h")
        static int unpackShort(@SuppressWarnings("unused") String format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
        }

        @Specialization(guards = "format.charAt(0) == i")
        static int unpackInt(@SuppressWarnings("unused") String format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
        }

        @Specialization(guards = "format.charAt(0) == l")
        static long unpackLong(@SuppressWarnings("unused") String format, byte[] bytes) {
            return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24 |
                            (bytes[4] & 0xFFL) << 32 | (bytes[5] & 0xFFL) << 40 | (bytes[6] & 0xFFL) << 48 | (bytes[7] & 0xFFL) << 56;
        }
    }

    static abstract class PackValueNode extends Node {
        // TODO deduplicate
        static final char B = 'B';
        static final char b = 'b';
        static final char h = 'h';
        static final char i = 'i';
        static final char l = 'l';

        @Child private PRaiseNode raiseNode;

        // Output goes to bytes, lenght not checked
        public abstract void execute(String format, Object object, byte[] bytes);

        @Specialization(guards = "format == null || format.charAt(0) == B", limit = "2")
        void packUnsignedByte(@SuppressWarnings("unused") String format, Object object, byte[] bytes,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            assert bytes.length == 1;
            long value = lib.asJavaLong(object);
            if (value < 0 || value > 0xFF) {
                throw raise(ValueError, ErrorMessages.MEMORYVIEW_INVALID_VALUE_FOR_FORMAT_S, format);
            }
            bytes[0] = (byte) value;
        }

        @Specialization(guards = "format.charAt(0) == l", limit = "2")
        static void packLong(@SuppressWarnings("unused") String format, Object object, byte[] bytes,
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
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            // TODO cast can change the format
            return getItemNode.executeInt(getStorageNode.execute(self.getOwner()), offset / self.getItemSize());
        }
    }

    static abstract class WriteItemAtNode extends Node {
        public abstract void execute(VirtualFrame frame, IntrinsifiedPMemoryView self, Object ptr, int offset, Object object);

        @Specialization(guards = "ptr != null")
        static void doNative(IntrinsifiedPMemoryView self, Object ptr, int offset, Object object,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached IntrinsifiedMemoryviewBuiltins.PackValueNode packValueNode) {
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
                        @Cached IntrinsifiedMemoryviewBuiltins.PackValueNode packValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            // TODO cast can change the format
            // TODO might not be bytes in array case
            assert self.getFormat() == null || self.getFormat().equals("B");
            byte[] bytes = new byte[1];
            packValueNode.execute(self.getFormat(), object, bytes);
            setItemNode.execute(getStorageNode.execute(self.getOwner()), offset / self.getItemSize(), bytes[0]);
        }
    }

    static abstract class CopyBytesNode extends Node {
        public abstract void execute(IntrinsifiedPMemoryView dest, Object destPtr, int destOffset, IntrinsifiedPMemoryView src, Object srcPtr, int srcOffset, int nbytes);

        @Specialization(guards = {"destPtr == null", "srcPtr == null"})
        @SuppressWarnings("unused")
        static void managedToManaged(IntrinsifiedPMemoryView dest, Object destPtr, int destOffset, IntrinsifiedPMemoryView src, Object srcPtr, int srcOffset, int nbytes,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.MemCopyNode memCopyNode) {
            // TODO assumes bytes storage
            SequenceStorage destStorage = getSequenceStorageNode.execute(dest.getOwner());
            SequenceStorage srcStorage = getSequenceStorageNode.execute(src.getOwner());
            memCopyNode.execute(destStorage, destOffset, srcStorage, srcOffset, nbytes);
        }

        @Specialization(guards = {"destPtr != null", "srcPtr == null"})
        @SuppressWarnings("unused")
        static void managedToNative(IntrinsifiedPMemoryView dest, Object destPtr, int destOffset, IntrinsifiedPMemoryView src, Object srcPtr, int srcOffset, int nbytes,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            // TODO assumes bytes storage
            // TODO avoid byte->int conversion
            // TODO explode?
            SequenceStorage srcStorage = getSequenceStorageNode.execute(src.getOwner());
            try {
                for (int i = 0; i < nbytes; i++) {
                    lib.writeArrayElement(destPtr, destOffset + i, (byte) getItemNode.executeInt(srcStorage, srcOffset + i));
                }
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {"destPtr == null", "srcPtr != null"})
        @SuppressWarnings("unused")
        static void nativeToManaged(IntrinsifiedPMemoryView dest, Object destPtr, int destOffset, IntrinsifiedPMemoryView src, Object srcPtr, int srcOffset, int nbytes,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            // TODO assumes bytes storage
            // TODO avoid byte->int conversion
            // TODO explode?
            SequenceStorage destStorage = getSequenceStorageNode.execute(dest.getOwner());
            try {
                for (int i = 0; i < nbytes; i++) {
                    setItemNode.execute(destStorage, (byte) lib.readArrayElement(srcPtr, srcOffset + i) & 0xFF, destOffset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {"destPtr != null", "srcPtr != null"})
        @SuppressWarnings("unused")
        static void nativeToNative(IntrinsifiedPMemoryView dest, Object destPtr, int destOffset, IntrinsifiedPMemoryView src, Object srcPtr, int srcOffset, int nbytes,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            // TODO call native memcpy?
            // TODO explode?
            try {
                for (int i = 0; i < nbytes; i++) {
                    lib.writeArrayElement(destPtr, destOffset + i, (byte) lib.readArrayElement(srcPtr, srcOffset + i));
                }
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
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

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    static abstract class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!isPSlice(index)", "!isEllipsis(index)"})
        Object getitem(VirtualFrame frame, IntrinsifiedPMemoryView self, Object index,
                        @Cached PointerLookupNode pointerFromIndexNode,
                        @Cached ReadItemAtNode readItemAtNode) {
            self.checkReleased(this);
            MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            return readItemAtNode.execute(self, ptr.ptr, ptr.offset);
        }

        @Specialization
        Object getitemSlice(IntrinsifiedPMemoryView self, PSlice slice,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            self.checkReleased(this);
            // TODO ndim == 0
            // TODO profile ndim == 1
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int[] strides = self.getBufferStrides();
            int[] newStrides = new int[strides.length];
            newStrides[0] = strides[0] * sliceInfo.step;
            PythonUtils.arraycopy(strides, 1, newStrides, 1, strides.length - 1);
            int[] shape = self.getBufferShape();
            int[] newShape = new int[shape.length];
            newShape[0] = sliceInfo.sliceLength;
            PythonUtils.arraycopy(shape, 1, newShape, 1, shape.length - 1);
            int lenght = self.getLength() - (shape[0] - newShape[0]) * self.getItemSize();
            // TODO factory
            return new IntrinsifiedPMemoryView(PythonBuiltinClassType.IntrinsifiedPMemoryView, PythonBuiltinClassType.IntrinsifiedPMemoryView.getInstanceShape(),
                            self.getBufferStructPointer(), self.getOwner(), lenght, self.isReadOnly(), self.getItemSize(), self.getFormat(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset() + sliceInfo.start * strides[0], newShape, newStrides, self.getBufferSuboffsets());
        }

        @Specialization
        Object getitemEllipsis(IntrinsifiedPMemoryView self, @SuppressWarnings("unused") PEllipsis ellipsis,
                        @Cached ConditionProfile zeroDimProfile) {
            self.checkReleased(this);
            if (zeroDimProfile.profile(self.getDimensions() == 0)) {
                return self;
            }
            throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public static abstract class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"!isPSlice(index)", "!isEllipsis(index)"})
        Object setitem(VirtualFrame frame, IntrinsifiedPMemoryView self, Object index, Object object,
                        @Cached PointerLookupNode pointerFromIndexNode,
                        @Cached WriteItemAtNode writeItemAtNode) {
            self.checkReleased(this);
            checkReadonly(self);

            MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            writeItemAtNode.execute(frame, self, ptr.ptr, ptr.offset, object);

            return PNone.NONE;
        }

        @Specialization
        Object setitem(VirtualFrame frame, IntrinsifiedPMemoryView self, PSlice slice, Object object,
                        @Cached GetItemNode getItemNode,
                        @Cached BuiltinConstructors.IMemoryViewNode createMemoryView,
                        @Cached PointerLookupNode pointerLookupNode,
                        @Cached CopyBytesNode copyBytesNode) {
            self.checkReleased(this);
            if (self.getDimensions() != 1) {
                throw raise(NotImplementedError, ErrorMessages.MEMORYVIEW_SLICE_ASSIGNMENT_RESTRICTED_TO_DIM_1);
            }
            IntrinsifiedPMemoryView srcView = createMemoryView.create(object);
            IntrinsifiedPMemoryView destView = (IntrinsifiedPMemoryView) getItemNode.execute(frame, self, slice);
            // TODO format skip @
            if (srcView.getDimensions() != destView.getDimensions() || srcView.getBufferShape()[0] != destView.getBufferShape()[0] || !srcView.getFormat().equals(destView.getFormat())) {
                throw raise(ValueError, ErrorMessages.MEMORYVIEW_DIFFERENT_STRUCTURES);
            }
            for (int i = 0; i < destView.getBufferShape()[0]; i++) {
                // TODO doesn't look very efficient
                MemoryPointer destPtr = pointerLookupNode.execute(frame, destView, i);
                MemoryPointer srcPtr = pointerLookupNode.execute(frame, srcView, i);
                copyBytesNode.execute(destView, destPtr.ptr, destPtr.offset, srcView, srcPtr.ptr, srcPtr.offset, destView.getItemSize());
            }
            return PNone.NONE;
        }

        @Specialization
        Object setitem(VirtualFrame frame, IntrinsifiedPMemoryView self, @SuppressWarnings("unused") PEllipsis ellipsis, Object object,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached WriteItemAtNode writeItemAtNode) {
            self.checkReleased(this);
            checkReadonly(self);

            if (zeroDimProfile.profile(self.getDimensions() == 0)) {
                writeItemAtNode.execute(frame, self, self.getBufferPointer(), 0, object);
                return PNone.NONE;
            }

            throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
        }

        private void checkReadonly(IntrinsifiedPMemoryView self) {
            if (self.isReadOnly()) {
                throw raise(TypeError, ErrorMessages.CANNOT_MODIFY_READONLY_MEMORY);
            }
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class DelItemNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object error(@SuppressWarnings("unused") IntrinsifiedPMemoryView self) {
            throw raise(TypeError, ErrorMessages.CANNOT_DELETE_MEMORY);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        int nativeLen(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile zeroDimProfile) {
            self.checkReleased(this);
            return zeroDimProfile.profile(self.getDimensions() == 0) ? 1 : self.getBufferShape()[0];
        }
    }
}
