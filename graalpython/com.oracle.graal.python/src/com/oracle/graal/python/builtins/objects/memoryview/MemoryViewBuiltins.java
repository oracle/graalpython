package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.ExpectIntNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.SepExpectByteNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.IntrinsifiedPMemoryView.BufferFormat;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMemoryView)
public class MemoryViewBuiltins extends PythonBuiltins {
    static final HiddenKey bufferReferencesKey = new HiddenKey("bufferRefQueue");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MemoryViewBuiltinsFactory.getFactories();
    }

    static class NativeBufferReleaseCallback implements AsyncHandler.AsyncAction {
        private BufferReference reference;

        public NativeBufferReleaseCallback(BufferReference reference) {
            this.reference = reference;
        }

        @Override
        public void execute(PythonContext context) {
            if (reference.isReleased()) {
                return;
            }
            ManagedBuffer buffer = reference.getManagedBuffer();
            // Managed buffers should be released directly in the reference queue thread
            assert buffer.getBufferStructPointer() != null;
            assert buffer.getReleaseFunction() != null;
            try {
                InteropLibrary.getUncached().execute(buffer.getReleaseFunction(), buffer.getOwner(), buffer.getBufferStructPointer());
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("Failed to invoke bf_releasebuffer", e);
            }
        }
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        MemoryViewNodes.BufferReferences bufferReferences = new MemoryViewNodes.BufferReferences();
        core.lookupType(PythonBuiltinClassType.PMemoryView).setAttribute(bufferReferencesKey, bufferReferences);
        core.getContext().registerAsyncAction(() -> {
            Reference<?> reference = null;
            try {
                reference = bufferReferences.queue.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (reference instanceof BufferReference) {
                BufferReference bufferReference = (BufferReference) reference;
                bufferReferences.set.remove(bufferReference);
                if (bufferReference.isReleased()) {
                    return null;
                }
                ManagedBuffer buffer = bufferReference.getManagedBuffer();
                if (buffer.decrementExports() == 0) {
                    if (buffer.getBufferStructPointer() != null) {
                        return new NativeBufferReleaseCallback(bufferReference);
                    } else {
                        MemoryViewNodes.ReleaseBufferOfManagedObjectNode.getUncached().execute(buffer.getOwner());
                        return null;
                    }
                }
            }
            return null;
        });
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    static abstract class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!isPSlice(index)", "!isEllipsis(index)"})
        Object getitem(VirtualFrame frame, IntrinsifiedPMemoryView self, Object index,
                        @Cached MemoryViewNodes.PointerLookupNode pointerFromIndexNode,
                        @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode) {
            self.checkReleased(this);
            MemoryViewNodes.MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            return readItemAtNode.execute(self, ptr.ptr, ptr.offset);
        }

        @Specialization
        Object getitemSlice(IntrinsifiedPMemoryView self, PSlice slice,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @Cached MemoryViewNodes.GetBufferReferences getQueue) {
            self.checkReleased(this);
            if (self.getDimensions() == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            int[] shape = self.getBufferShape();
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(shape[0], sliceUnpack.execute(slice));
            int[] strides = self.getBufferStrides();
            int[] newStrides = new int[strides.length];
            newStrides[0] = strides[0] * sliceInfo.step;
            PythonUtils.arraycopy(strides, 1, newStrides, 1, strides.length - 1);
            int[] newShape = new int[shape.length];
            newShape[0] = sliceInfo.sliceLength;
            PythonUtils.arraycopy(shape, 1, newShape, 1, shape.length - 1);
            int[] suboffsets = self.getBufferSuboffsets();
            int lenght = self.getLength() - (shape[0] - newShape[0]) * self.getItemSize();
            int flags = initFlagsNode.execute(self.getDimensions(), self.getItemSize(), newShape, newStrides, suboffsets);
            // TODO factory
            return new IntrinsifiedPMemoryView(PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PMemoryView.getInstanceShape(),
                            getQueue.execute(), self.getManagedBuffer(), self.getOwner(), lenght, self.isReadOnly(),
                            self.getItemSize(), self.getFormatString(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset() + sliceInfo.start * strides[0], newShape, newStrides, suboffsets, flags);
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
                        @Cached MemoryViewNodes.PointerLookupNode pointerFromIndexNode,
                        @Cached MemoryViewNodes.WriteItemAtNode writeItemAtNode) {
            self.checkReleased(this);
            checkReadonly(self);

            MemoryViewNodes.MemoryPointer ptr = pointerFromIndexNode.execute(frame, self, index);
            writeItemAtNode.execute(frame, self, ptr.ptr, ptr.offset, object);

            return PNone.NONE;
        }

        @Specialization
        Object setitem(VirtualFrame frame, IntrinsifiedPMemoryView self, PSlice slice, Object object,
                        @Cached GetItemNode getItemNode,
                        @Cached BuiltinConstructors.MemoryViewNode createMemoryView,
                        @Cached MemoryViewNodes.PointerLookupNode pointerLookupNode,
                        @Cached MemoryViewNodes.CopyBytesNode copyBytesNode) {
            self.checkReleased(this);
            checkReadonly(self);
            if (self.getDimensions() != 1) {
                throw raise(NotImplementedError, ErrorMessages.MEMORYVIEW_SLICE_ASSIGNMENT_RESTRICTED_TO_DIM_1);
            }
            IntrinsifiedPMemoryView srcView = createMemoryView.create(object);
            IntrinsifiedPMemoryView destView = (IntrinsifiedPMemoryView) getItemNode.execute(frame, self, slice);
            // TODO format skip @
            if (srcView.getDimensions() != destView.getDimensions() || srcView.getBufferShape()[0] != destView.getBufferShape()[0] || !srcView.getFormatString().equals(destView.getFormatString())) {
                throw raise(ValueError, ErrorMessages.MEMORYVIEW_DIFFERENT_STRUCTURES);
            }
            for (int i = 0; i < destView.getBufferShape()[0]; i++) {
                // TODO doesn't look very efficient
                MemoryViewNodes.MemoryPointer destPtr = pointerLookupNode.execute(frame, destView, i);
                MemoryViewNodes.MemoryPointer srcPtr = pointerLookupNode.execute(frame, srcView, i);
                copyBytesNode.execute(destView, destPtr.ptr, destPtr.offset, srcView, srcPtr.ptr, srcPtr.offset, destView.getItemSize());
            }
            return PNone.NONE;
        }

        @Specialization
        Object setitem(VirtualFrame frame, IntrinsifiedPMemoryView self, @SuppressWarnings("unused") PEllipsis ellipsis, Object object,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached MemoryViewNodes.WriteItemAtNode writeItemAtNode) {
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

    @Builtin(name = "tolist", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class ToListNode extends PythonUnaryBuiltinNode {
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"})
        Object tolistCached(IntrinsifiedPMemoryView self,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode) {
            self.checkReleased(this);
            if (cachedDimensions == 0) {
                // That's not a list but CPython does it this way
                return readItemAtNode.execute(self, self.getBufferPointer(), self.getOffset());
            } else {
                return recursive(self, readItemAtNode, 0, cachedDimensions, self.getBufferPointer(), self.getOffset());
            }
        }

        @Specialization(replaces = "tolistCached")
        Object tolist(IntrinsifiedPMemoryView self,
                        @Cached MemoryViewNodes.ReadItemAtNode readItemAtNode) {
            self.checkReleased(this);
            if (self.getDimensions() == 0) {
                return readItemAtNode.execute(self, self.getBufferPointer(), self.getOffset());
            } else {
                return recursiveBoundary(self, readItemAtNode, 0, self.getDimensions(), self.getBufferPointer(), self.getOffset());
            }
        }

        @TruffleBoundary
        private PList recursiveBoundary(IntrinsifiedPMemoryView self, MemoryViewNodes.ReadItemAtNode readItemAtNode, int dim, int ndim, Object ptr, int offset) {
            return recursive(self, readItemAtNode, dim, ndim, ptr, offset);
        }

        private PList recursive(IntrinsifiedPMemoryView self, MemoryViewNodes.ReadItemAtNode readItemAtNode, int dim, int ndim, Object ptr, int offset) {
            Object[] objects = new Object[self.getBufferShape()[dim]];
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = getCallCapiFunction().call(NativeCAPISymbols.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    objects[i] = readItemAtNode.execute(self, xptr, xoffset);
                } else {
                    objects[i] = recursive(self, readItemAtNode, dim + 1, ndim, xptr, xoffset);
                }
                offset += self.getBufferStrides()[dim];
            }
            return factory().createList(objects);
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @Builtin(name = "tobytes", minNumOfPositionalArgs = 1, parameterNames = {"$self", "order"})
    @ArgumentClinic(name = "order", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"C\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public static abstract class ToBytesNode extends PythonBinaryClinicBuiltinNode {
        @Child private MemoryViewNodes.ToJavaBytesNode toJavaBytesNode;
        @Child private MemoryViewNodes.ToJavaBytesFortranOrderNode toJavaBytesFortranOrderNode;

        @Specialization
        PBytes tobytes(IntrinsifiedPMemoryView self, String order) {
            self.checkReleased(this);
            byte[] bytes;
            // The nodes act as branch profiles
            if ("C".equals(order) || "A".equals(order)) {
                bytes = getToJavaBytesNode().execute(self);
            } else if ("F".equals(order)) {
                bytes = getToJavaBytesFortranOrderNode().execute(self);
            } else {
                throw raise(ValueError, ErrorMessages.ORDER_MUST_BE_C_F_OR_A);
            }
            return factory().createBytes(bytes);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.ToBytesNodeClinicProviderGen.INSTANCE;
        }

        private MemoryViewNodes.ToJavaBytesNode getToJavaBytesNode() {
            if (toJavaBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaBytesNode = insert(MemoryViewNodes.ToJavaBytesNode.create());
            }
            return toJavaBytesNode;
        }

        private MemoryViewNodes.ToJavaBytesFortranOrderNode getToJavaBytesFortranOrderNode() {
            if (toJavaBytesFortranOrderNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaBytesFortranOrderNode = insert(MemoryViewNodes.ToJavaBytesFortranOrderNode.create());
            }
            return toJavaBytesFortranOrderNode;
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "bytes_per_sep"})
    @ArgumentClinic(name = "sep", conversionClass = SepExpectByteNode.class, defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "bytes_per_sep", conversionClass = ExpectIntNode.class, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class HexNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        String none(IntrinsifiedPMemoryView self, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") int bytesPerSepGroup,
                        @Shared("p") @Cached ConditionProfile earlyExit,
                        @Shared("b") @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode) {
            return hex(self, (byte) 0, 0, earlyExit, toJavaBytesNode, toHexNode);
        }

        @Specialization
        String hex(IntrinsifiedPMemoryView self, byte sep, int bytesPerSepGroup,
                        @Shared("p") @Cached ConditionProfile earlyExit,
                        @Shared("b") @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode,
                        @Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode) {
            self.checkReleased(this);
            if (earlyExit.profile(self.getLength() == 0)) {
                return "";
            }
            byte[] b = toJavaBytesNode.execute(self);
            return toHexNode.execute(b, b.length, sep, bytesPerSepGroup);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.HexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "toreadonly", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class ToReadonlyNode extends PythonUnaryBuiltinNode {
        @Specialization
        IntrinsifiedPMemoryView toreadonly(IntrinsifiedPMemoryView self,
                        @Cached MemoryViewNodes.GetBufferReferences getQueue) {
            self.checkReleased(this);
            // TODO factory
            return new IntrinsifiedPMemoryView(PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PMemoryView.getInstanceShape(),
                            getQueue.execute(), self.getManagedBuffer(), self.getOwner(), self.getLength(), true,
                            self.getItemSize(), self.getFormatString(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset(), self.getBufferShape(), self.getBufferStrides(), self.getBufferSuboffsets(), self.getFlags());
        }
    }

    @Builtin(name = "cast", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format", "shape"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    public static abstract class CastNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        IntrinsifiedPMemoryView cast(IntrinsifiedPMemoryView self, String formatString, @SuppressWarnings("unused") PNone none,
                        @Shared("getQueue") @Cached MemoryViewNodes.GetBufferReferences getQueue) {
            self.checkReleased(this);
            return doCast(self, formatString, 1, null, getQueue.execute());
        }

        @Specialization(guards = "isPTuple(shapeObj) || isList(shapeObj)")
        IntrinsifiedPMemoryView cast(IntrinsifiedPMemoryView self, String formatString, Object shapeObj,
                        @Shared("getQueue") @Cached MemoryViewNodes.GetBufferReferences getQueue,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            self.checkReleased(this);
            SequenceStorage storage = getSequenceStorageNode.execute(shapeObj);
            int ndim = lenNode.execute(storage);
            int[] shape = new int[ndim];
            for (int i = 0; i < ndim; i++) {
                shape[i] = lib.asSize(getItemScalarNode.execute(storage, i));
                if (shape[i] <= 0) {
                    throw raise(TypeError, ErrorMessages.MEMORYVIEW_CAST_ELEMENTS_MUST_BE_POSITIVE_INTEGERS);
                }
            }
            return doCast(self, formatString, ndim, shape, getQueue.execute());
        }

        @Specialization(guards = {"!isPTuple(shape)", "!isList(shape)", "!isPNone(shape)"})
        @SuppressWarnings("unused")
        IntrinsifiedPMemoryView error(IntrinsifiedPMemoryView self, String format, Object shape) {
            throw raise(TypeError, ErrorMessages.ARG_S_MUST_BE_A_LIST_OR_TUPLE, "shape");
        }

        private IntrinsifiedPMemoryView doCast(IntrinsifiedPMemoryView self, String formatString, int ndim, int[] shape, MemoryViewNodes.BufferReferences refQueue) {
            if (!self.isCContiguous()) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_CASTS_RESTRICTED_TO_C_CONTIGUOUS);
            }
            BufferFormat format = BufferFormat.fromString(formatString);
            int itemsize = MemoryViewNodes.bytesize(format);
            if (itemsize < 0) {
                throw raise(ValueError, ErrorMessages.MEMORYVIEW_DESTINATION_FORMAT_ERROR);
            }
            if (!MemoryViewNodes.isByteFormat(format) && !MemoryViewNodes.isByteFormat(self.getFormat())) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_CANNOT_CAST_NON_BYTE);
            }
            if (self.getLength() % itemsize != 0) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_LENGTH_NOT_MULTIPLE_OF_ITEMSIZE);
            }
            if (shape != null || self.getDimensions() != 1) {
                for (int i = 0; i < self.getDimensions(); i++) {
                    if (self.getBufferShape()[i] == 0) {
                        throw raise(TypeError, ErrorMessages.MEMORYVIEW_CANNOT_CAST_VIEW_WITH_ZEROS_IN_SHAPE_OR_STRIDES);
                    }
                }
            }
            int[] newShape;
            int[] newStrides;
            int flags = IntrinsifiedPMemoryView.FLAG_C;
            if (ndim == 0) {
                newShape = null;
                newStrides = null;
                flags |= IntrinsifiedPMemoryView.FLAG_SCALAR;
                if (self.getLength() != itemsize) {
                    throw raise(TypeError, ErrorMessages.MEMORYVIEW_CAST_WRONG_LENGTH);
                }
            } else {
                if (shape == null) {
                    newShape = new int[]{self.getLength() / itemsize};
                } else {
                    if (ndim != 1 && self.getDimensions() != 1) {
                        throw raise(TypeError, ErrorMessages.MEMORYVIEW_CAST_MUST_BE_1D_TO_ND_OR_ND_TO_1D);
                    }
                    if (ndim > IntrinsifiedPMemoryView.MAX_DIM) {
                        throw raise(ValueError, ErrorMessages.MEMORYVIEW_NUMBER_OF_DIMENSIONS_MUST_NOT_EXCEED_D, ndim);
                    }
                    int newLenght = itemsize;
                    for (int i = 0; i < ndim; i++) {
                        newLenght *= shape[i];
                    }
                    if (newLenght != self.getLength()) {
                        throw raise(TypeError, ErrorMessages.MEMORYVIEW_CAST_WRONG_LENGTH);
                    }
                    newShape = shape;
                }
                newStrides = IntrinsifiedPMemoryView.initStridesFromShape(ndim, itemsize, shape);
            }
            // TODO factory
            return new IntrinsifiedPMemoryView(PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PMemoryView.getInstanceShape(),
                            refQueue, self.getManagedBuffer(), self.getOwner(), self.getLength(), self.isReadOnly(),
                            itemsize, formatString, ndim, self.getBufferPointer(),
                            self.getOffset(), newShape, newStrides, null, flags);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryViewBuiltinsClinicProviders.CastNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        int len(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile zeroDimProfile) {
            self.checkReleased(this);
            return zeroDimProfile.profile(self.getDimensions() == 0) ? 1 : self.getBufferShape()[0];
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static String repr(IntrinsifiedPMemoryView self) {
            if (self.isReleased()) {
                return String.format("<released memory at 0x%x>", System.identityHashCode(self));
            } else {
                return String.format("<memory at 0x%x>", System.identityHashCode(self));
            }
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        int hash(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile cachedProfile,
                        @Cached ConditionProfile writableProfile,
                        @Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode) {
            if (cachedProfile.profile(self.getCachedHash() != -1)) {
                return self.getCachedHash();
            }
            self.checkReleased(this);
            if (writableProfile.profile(!self.isReadOnly())) {
                throw raise(ValueError, ErrorMessages.CANNOT_HASH_WRITEABLE_MEMORYVIEW);
            } else {
                // TODO avoid copying
                int hash = hashArray(toJavaBytesNode.execute(self));
                self.setCachedHash(hash);
                return hash;
            }
        }

        @TruffleBoundary
        private static int hashArray(byte[] array) {
            return Arrays.hashCode(array);
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object enter(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public static abstract class ExitNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object exit(VirtualFrame frame, IntrinsifiedPMemoryView self, Object type, Object val, Object tb,
                        @Cached ReleaseNode releaseNode) {
            releaseNode.execute(frame, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class ReleaseNode extends PythonUnaryBuiltinNode {
        public abstract Object execute(VirtualFrame frame, IntrinsifiedPMemoryView self);

        @Specialization(guards = "self.getReference() == null")
        Object releaseSimple(IntrinsifiedPMemoryView self) {
            checkExports(self);
            self.setReleased();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.getReference() != null", "self.getManagedBuffer().getBufferStructPointer() == null"})
        Object releaseManaged(IntrinsifiedPMemoryView self,
                        @Cached MemoryViewNodes.ReleaseBufferOfManagedObjectNode release) {
            checkExports(self);
            if (checkShouldReleaseBuffer(self)) {
                release.execute(self.getManagedBuffer().getOwner());
            }
            self.setReleased();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.getReference() != null", "self.getManagedBuffer().getBufferStructPointer() != null"})
        Object releaseNative(VirtualFrame frame, IntrinsifiedPMemoryView self,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            checkExports(self);
            if (checkShouldReleaseBuffer(self)) {
                Object state = IndirectCallContext.enter(frame, getContext(), this);
                ManagedBuffer buffer = self.getManagedBuffer();
                try {
                    lib.execute(buffer.getReleaseFunction(), buffer.getOwner(), buffer.getBufferStructPointer());
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("Failed to invoke bf_releasebuffer", e);
                } finally {
                    IndirectCallContext.exit(frame, getContext(), state);
                }
            }
            self.setReleased();
            return PNone.NONE;
        }

        private static boolean checkShouldReleaseBuffer(IntrinsifiedPMemoryView self) {
            if (self.getReference() != null) {
                return self.getReference().getManagedBuffer().decrementExports() == 0;
            }
            return false;
        }

        private void checkExports(IntrinsifiedPMemoryView self) {
            int exports = self.getExports().get();
            if (exports > 0) {
                throw raise(BufferError, ErrorMessages.MEMORYVIEW_HAS_D_EXPORTED_BUFFERS, exports);
            }
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class ItemSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        int get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.getItemSize();
        }
    }

    @Builtin(name = "nbytes", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class NBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        int get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.getLength();
        }
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class ObjNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.getOwner() != null ? self.getOwner() : PNone.NONE;
        }
    }

    @Builtin(name = "format", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class FormatNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.getFormatString() != null ? self.getFormatString() : "B";
        }
    }

    @Builtin(name = "shape", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class ShapeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile nullProfile) {
            self.checkReleased(this);
            if (nullProfile.profile(self.getBufferShape() == null)) {
                return factory().createEmptyTuple();
            }
            return factory().createTuple(new IntSequenceStorage(self.getBufferShape()));
        }
    }

    @Builtin(name = "strides", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class StridesNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile nullProfile) {
            self.checkReleased(this);
            if (nullProfile.profile(self.getBufferStrides() == null)) {
                return factory().createEmptyTuple();
            }
            return factory().createTuple(new IntSequenceStorage(self.getBufferStrides()));
        }
    }

    @Builtin(name = "suboffsets", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class SuboffsetsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(IntrinsifiedPMemoryView self,
                        @Cached ConditionProfile nullProfile) {
            self.checkReleased(this);
            if (nullProfile.profile(self.getBufferSuboffsets() == null)) {
                return factory().createEmptyTuple();
            }
            return factory().createTuple(new IntSequenceStorage(self.getBufferSuboffsets()));
        }
    }

    @Builtin(name = "readonly", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class ReadonlyNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.isReadOnly();
        }
    }

    @Builtin(name = "ndim", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class NDimNode extends PythonUnaryBuiltinNode {
        @Specialization
        int get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.getDimensions();
        }
    }

    @Builtin(name = "c_contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class CContiguousNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.isCContiguous();
        }
    }

    @Builtin(name = "f_contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class FContiguousNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.isFortranContiguous();
        }
    }

    @Builtin(name = "contiguous", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public static abstract class ContiguousNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(IntrinsifiedPMemoryView self) {
            self.checkReleased(this);
            return self.isCContiguous() || self.isFortranContiguous();
        }
    }
}
