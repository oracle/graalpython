package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IntrinsifiedPMemoryView;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

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

    static abstract class ReadItemAtNode extends Node {
        public abstract Object execute(VirtualFrame frame, IntrinsifiedPMemoryView self, Object ptr, int offset);

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
        static Object doManaged(VirtualFrame frame, IntrinsifiedPMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            // TODO cast can change the format
            return getItemNode.execute(frame, getStorageNode.execute(self.getOwner()), offset / self.getItemSize());
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    static abstract class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.LenNode sequenceLenNode;
        @Child private SequenceStorageNodes.GetItemNode sequenceGetItemNode;
        @Child private PythonObjectLibrary plib;
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;

        @Specialization
        Object getitemNative(VirtualFrame frame, IntrinsifiedPMemoryView self, int index,
                        @Cached ReadItemAtNode readItemAtNode) {
            if (self.getDimensions() > 1) {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
            }
            Object ptr = self.getBufferPointer();
            int offset = self.getOffset();
            for (int dim = 0; dim < self.getDimensions(); dim++) {
                int nitems;

                int[] shape = self.getBufferShape();
                nitems = shape[dim];
                if (index < 0) {
                    index += nitems;
                }
                if (index < 0 || index >= nitems) {
                    throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
                }

                // TODO stride is allowed to be negative
                offset += self.getBufferStrides()[dim] * index;

                int[] suboffsets = self.getBufferSuboffsets();
                if (suboffsets != null && suboffsets[dim] >= 0) {
                    // The length may be out of bounds, but sulong shouldn't care if we don't
                    // access the out-of-bound part
                    ptr = getCallCapiFunction().call("truffle_add_suboffset", ptr, offset, suboffsets[dim], self.getLength());
                }
            }

            return readItemAtNode.execute(frame, self, ptr, offset);
        }

        @Specialization
        static Object getitemSlice(IntrinsifiedPMemoryView self, PSlice slice,
                        @Cached SliceLiteralNode.SliceUnpack sliceUnpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
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
            return new IntrinsifiedPMemoryView(IntrinsifiedPMemoryView, IntrinsifiedPMemoryView.getInstanceShape(), self.getBufferStructPointer(),
                            self.getOwner(), lenght, self.isReadOnly(), self.getItemSize(), self.getFormat(), self.getDimensions(), self.getBufferPointer(),
                            self.getOffset() + sliceInfo.start * strides[0], newShape, newStrides, self.getBufferSuboffsets());
        }

        @Specialization
        Object getitemNativeMulti(VirtualFrame frame, IntrinsifiedPMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached ReadItemAtNode readItemAtNode) {
            Object ptr = self.getBufferPointer();
            int offset = self.getOffset();
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            checkTupleLength(indicesStorage, self.getDimensions());
            for (int dim = 0; dim < self.getDimensions(); dim++) {
                int nitems;
                int index = getIndex(frame, indicesStorage, dim);
                int[] shape = self.getBufferShape();
                nitems = shape[dim];
                if (index < 0) {
                    index += nitems;
                }
                if (index < 0 || index >= nitems) {
                    throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
                }

                offset += self.getBufferStrides()[dim] * index;

                int[] suboffsets = self.getBufferSuboffsets();
                if (suboffsets != null && suboffsets[dim] >= 0) {
                    // The length may be out of bounds, but sulong shouldn't care if we don't
                    // access the out-of-bound part
                    ptr = getCallCapiFunction().call("truffle_add_suboffset", ptr, offset, suboffsets[dim], self.getLength());
                    offset = 0;
                }
            }

            return readItemAtNode.execute(frame, self, ptr, offset);
        }

        @Specialization(guards = {"!isPTuple(indexObj)", "!isPSlice(indexObj)"})
        Object getitemNativeObject(VirtualFrame frame, IntrinsifiedPMemoryView self, Object indexObj,
                        @Cached ReadItemAtNode readItemAtNode) {
            return getitemNative(frame, self, convertIndex(frame, indexObj), readItemAtNode);
        }

        private int getIndex(VirtualFrame frame, SequenceStorage indicesStorage, int index) {
            Object indexObj = getSequenceGetItemNode().execute(frame, indicesStorage, index);
            return convertIndex(frame, indexObj);
        }

        private int convertIndex(@SuppressWarnings("unused") VirtualFrame frame, Object indexObj) {
            if (!getPlib().canBeIndex(indexObj)) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
            }
            // FIXME use asSizeWithFrame when GR-26456 is fixed
            return getPlib().asSize(indexObj, IndexError);
        }

        private void checkTupleLength(SequenceStorage indicesStorage, int ndim) {
            int length = getSequenceLenNode().execute(indicesStorage);
            if (length > ndim) {
                throw raise(TypeError, ErrorMessages.CANNOT_INDEX_D_DIMENSION_VIEW_WITH_D, ndim, length);
            } else if (length < ndim) {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.SUB_VIEWS_NOT_IMPLEMENTED);
            }
        }

        private SequenceStorageNodes.LenNode getSequenceLenNode() {
            if (sequenceLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sequenceLenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return sequenceLenNode;
        }

        private SequenceStorageNodes.GetItemNode getSequenceGetItemNode() {
            if (sequenceGetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sequenceGetItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return sequenceGetItemNode;
        }

        private PythonObjectLibrary getPlib() {
            if (plib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                plib = insert(PythonObjectLibrary.getFactory().createDispatched(3));
            }
            return plib;
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int nativeLen(IntrinsifiedPMemoryView self) {
            return self.getLength() / self.getItemSize();
        }
    }
}
