package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
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

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(IntrinsifiedMemoryviewBuiltins.class)
    static abstract class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.LenNode sequenceLenNode;
        @Child private SequenceStorageNodes.GetItemNode sequenceGetItemNode;
        @Child private PythonObjectLibrary plib;

        @Specialization
        // TODO slices
        static Object getitem(VirtualFrame frame, IntrinsifiedPManagedMemoryView self, int index,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage storage = getStorageNode.execute(self.getDelegate());
            return getItemNode.execute(frame, storage, index);
        }

        @Specialization
        Object getitemMulti(VirtualFrame frame, IntrinsifiedPManagedMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            checkTupleLength(indicesStorage, 1);
            int index = getIndex(frame, indicesStorage, 0);
            return getitem(frame, self, index, getSequenceStorageNode, getItemNode);
        }

        @Specialization(guards = {"!isPSlice(indexObj)", "!isPTuple(indexObj)"})
        Object getitemObject(VirtualFrame frame, IntrinsifiedPManagedMemoryView self, Object indexObj,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            return getitem(frame, self, convertIndex(frame, indexObj), getStorageNode, getItemNode);
        }

        @Specialization
        Object getitemNative(IntrinsifiedPNativeMemoryView self, int index,
                        @Cached UnpackValueNode unpackValueNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (self.getDimensions() > 1) {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
            }
            Object ptr = self.getBufferPointer();
            long offset = 0;
            long itemsize = self.getItemSize();
            try {
                for (int dim = 0; dim < self.getDimensions(); dim++) {
                    long nitems;

                    long[] shape = self.getBufferShape();
                    nitems = shape[dim];
                    if (index < 0) {
                        index += nitems;
                    }
                    if (index < 0 || index >= nitems) {
                        throw raise(TypeError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
                    }

                    offset += self.getBufferStrides()[dim] * index;

                    long[] suboffsets = self.getBufferSuboffsets();
                    if (suboffsets != null && suboffsets[dim] >= 0) {
                        // The length may be out of bounds, but sulong shouldn't care if we don't
                        // access the out-of-bound part
                        ptr = callCapiFunction.call("truffle_add_suboffset", ptr, offset, suboffsets[dim], self.getTotalLength());
                    }
                }

                byte[] bytes = new byte[(int) itemsize];
                for (int i = 0; i < itemsize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }

                return unpackValueNode.execute(self.getFormat(), bytes);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = {"!isPTuple(indexObj)", "!isPSlice(indexObj)"})
        Object getitemNativeObject(VirtualFrame frame, IntrinsifiedPNativeMemoryView self, Object indexObj,
                        @Cached UnpackValueNode unpackValueNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            return getitemNative(self, convertIndex(frame, indexObj), unpackValueNode, callCapiFunction, lib);
        }

        @Specialization
        Object getitemNativeMulti(VirtualFrame frame, IntrinsifiedPNativeMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached UnpackValueNode unpackValueNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object ptr = self.getBufferPointer();
            long offset = 0;
            long itemsize = self.getItemSize();
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            checkTupleLength(indicesStorage, self.getDimensions());
            try {
                for (int dim = 0; dim < self.getDimensions(); dim++) {
                    long nitems;
                    int index = getIndex(frame, indicesStorage, dim);
                    long[] shape = self.getBufferShape();
                    nitems = shape[dim];
                    if (index < 0) {
                        index += nitems;
                    }
                    if (index < 0 || index >= nitems) {
                        throw raise(TypeError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
                    }

                    offset += self.getBufferStrides()[dim] * index;

                    long[] suboffsets = self.getBufferSuboffsets();
                    if (suboffsets != null && suboffsets[dim] >= 0) {
                        // The length may be out of bounds, but sulong shouldn't care if we don't
                        // access the out-of-bound part
                        ptr = callCapiFunction.call("truffle_add_suboffset", ptr, offset, suboffsets[dim], self.getTotalLength());
                        offset = 0;
                    }
                }

                byte[] bytes = new byte[(int) itemsize];
                for (int i = 0; i < itemsize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }

                return unpackValueNode.execute(self.getFormat(), bytes);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
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
    }
}
