/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.BufferStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromSlot;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class MemoryViewNodes {
    static boolean isByteFormat(BufferFormat format) {
        return format == BufferFormat.UINT_8 || format == BufferFormat.INT_8 || format == BufferFormat.CHAR;
    }

    static void checkBufferBounds(Node node, PMemoryView self, PythonBufferAccessLibrary bufferLib, int offset, int length) {
        if (offset + length > bufferLib.getBufferLength(self.getBuffer())) {
            /*
             * This can only happen when the buffer gets resized while being exported. CPython makes
             * such resizing illegal in the first place, but we don't prevent it due to absence of
             * reference counting.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(node, IndexError, ErrorMessages.INVALID_BUFFER_ACCESS);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class InitFlagsNode extends Node {
        public abstract int execute(Node inliningTarget, int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets);

        @Specialization
        static int compute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets) {
            if (ndim == 0) {
                return PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN | PMemoryView.FLAG_SCALAR;
            } else if (suboffsets != null) {
                return PMemoryView.FLAG_PIL;
            } else {
                int flags = PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN;
                int expectedStride = itemsize;
                for (int i = ndim - 1; i >= 0; i--) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_C;
                        break;
                    }
                    expectedStride *= dim;
                }
                expectedStride = itemsize;
                for (int i = 0; i < ndim; i++) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_FORTRAN;
                        break;
                    }
                    expectedStride *= dim;
                }
                return flags;
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(BufferFormat.class)
    public abstract static class UnpackValueNode extends Node {
        public abstract Object execute(Node inliningTarget, BufferFormat format, TruffleString formatStr, Object buffer, int offset);

        @Specialization(guards = "format != OTHER")
        static Object unpack(Node inliningTarget, BufferFormat format, @SuppressWarnings("unused") TruffleString formatStr, Object buffer, int offset,
                        @Cached BufferStorageNodes.UnpackValueNode unpackValueNode) {
            return unpackValueNode.execute(inliningTarget, format, buffer, offset);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object notImplemented(Node inliningTarget, BufferFormat format, TruffleString formatStr, Object buffer, int offset) {
            throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(BufferFormat.class)
    public abstract static class PackValueNode extends Node {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, BufferFormat format, TruffleString formatStr, Object object, Object buffer, int offset);

        @Specialization(guards = "format != OTHER")
        static void pack(VirtualFrame frame, Node inliningTarget, BufferFormat format, TruffleString formatStr, Object value, Object buffer, int offset,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached BufferStorageNodes.PackValueNode packValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                packValueNode.execute(frame, inliningTarget, format, value, buffer, offset);
            } catch (PException e) {
                e.expect(inliningTarget, OverflowError, errorProfile);
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MEMORYVIEW_INVALID_VALUE_FOR_FORMAT_S, formatStr);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static void notImplemented(Node inliningTarget, BufferFormat format, TruffleString formatStr, Object object, Object buffer, int offset) {
            throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class ReadBytesAtNode extends Node {
        public abstract void execute(Node inliningTarget, byte[] dest, int destOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @Shared @Cached(inline = false) CStructAccess.ReadByteNode readNode) {
            readNode.readByteArray(ptr, dest, cachedLen, offset, destOffset);
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] dest, int destOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Shared @Cached(inline = false) CStructAccess.ReadByteNode readNode) {
            readNode.readByteArray(ptr, dest, len, offset, destOffset);
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        void doManagedCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("len") int cachedLen) {
            checkBufferBounds(this, self, bufferLib, offset, cachedLen);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, dest, destOffset, cachedLen);
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached", limit = "3")
        void doManagedGeneric(byte[] dest, int destOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib) {
            checkBufferBounds(this, self, bufferLib, offset, len);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, dest, destOffset, len);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class WriteBytesAtNode extends Node {
        public abstract void execute(Node inliningTarget, byte[] src, int srcOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @Shared @Cached(inline = false) CStructAccess.WriteByteNode writeNode) {
            writeNode.writeByteArray(ptr, src, cachedLen, srcOffset, offset);
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] src, int srcOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Shared @Cached(inline = false) CStructAccess.WriteByteNode writeNode) {
            writeNode.writeByteArray(ptr, src, len, srcOffset, offset);
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        void doManagedCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("len") int cachedLen) {
            checkBufferBounds(this, self, bufferLib, offset, cachedLen);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, src, srcOffset, cachedLen);
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached", limit = "3")
        void doManagedGeneric(byte[] src, int srcOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib) {
            checkBufferBounds(this, self, bufferLib, offset, len);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, src, srcOffset, len);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 29
    abstract static class ReadItemAtNode extends Node {
        public abstract Object execute(VirtualFrame frame, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = "ptr != null")
        static Object doNative(PMemoryView self, Object ptr, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            checkBufferBounds(inliningTarget, self, bufferLib, offset, itemSize);
            NativeByteSequenceStorage buffer = NativeByteSequenceStorage.create(ptr, itemSize + offset, itemSize + offset, false);
            return unpackValueNode.execute(inliningTarget, self.getFormat(), self.getFormatString(), buffer, offset);
        }

        @Specialization(guards = "ptr == null")
        static Object doManaged(PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            checkBufferBounds(inliningTarget, self, bufferLib, offset, itemSize);
            return unpackValueNode.execute(inliningTarget, self.getFormat(), self.getFormatString(), self.getBuffer(), offset);
        }

        @NeverDefault
        protected static CastToByteNode createCoerce() {
            return CastToByteNode.create(true);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 29
    abstract static class WriteItemAtNode extends Node {
        public abstract void execute(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object);

        @Specialization(guards = "ptr != null")
        static void doNative(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PackValueNode packValueNode) {
            int itemSize = self.getItemSize();
            checkBufferBounds(inliningTarget, self, bufferLib, offset, itemSize);
            NativeByteSequenceStorage buffer = NativeByteSequenceStorage.create(ptr, itemSize + offset, itemSize + offset, false);
            packValueNode.execute(frame, inliningTarget, self.getFormat(), self.getFormatString(), object, buffer, offset);
        }

        @Specialization(guards = "ptr == null")
        static void doManaged(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PackValueNode packValueNode) {
            int itemSize = self.getItemSize();
            checkBufferBounds(inliningTarget, self, bufferLib, offset, itemSize);
            packValueNode.execute(frame, inliningTarget, self.getFormat(), self.getFormatString(), object, self.getBuffer(), offset);
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
    abstract static class PointerLookupNode extends PNodeWithRaise {
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;
        @Child private PyNumberAsSizeNode asSizeNode;

        // index can be a tuple, int or int-convertible
        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, Object index);

        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, int index);

        private void lookupDimension(Node inliningTarget, PMemoryView self, MemoryPointer ptr, int dim, int initialIndex, InlinedConditionProfile hasSuboffsetsProfile) {
            int index = initialIndex;
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
            if (hasSuboffsetsProfile.profile(inliningTarget, suboffsets != null) && suboffsets[dim] >= 0) {
                // The length may be out of bounds, but sulong shouldn't care if we don't
                // access the out-of-bound part
                ptr.ptr = getCallCapiFunction().call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr.ptr, ptr.offset, suboffsets[dim]);
                ptr.offset = 0;
            }
        }

        @Specialization(guards = "self.getDimensions() == 1")
        MemoryPointer resolveInt(PMemoryView self, int index,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile hasSuboffsetsProfile) {
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            lookupDimension(inliningTarget, self, ptr, 0, index, hasSuboffsetsProfile);
            return ptr;
        }

        @Specialization(guards = "self.getDimensions() != 1")
        MemoryPointer resolveIntError(PMemoryView self, @SuppressWarnings("unused") int index) {
            if (self.getDimensions() == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            // CPython doesn't implement this either, as of 3.8
            throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
        }

        @Specialization(guards = {"cachedDimensions == self.getDimensions()", "cachedDimensions <= 8"}, limit = "3")
        @ExplodeLoop
        @SuppressWarnings("truffle-static-method")
        MemoryPointer resolveTupleCached(VirtualFrame frame, PMemoryView self, PTuple indices,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile hasSuboffsetsProfile,
                        @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Shared @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(inliningTarget, indices);
            checkTupleLength(indicesStorage, cachedDimensions);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < cachedDimensions; dim++) {
                Object indexObj = getItemNode.execute(inliningTarget, indicesStorage, dim);
                int index = convertIndex(frame, inliningTarget, indexCheckNode, indexObj);
                lookupDimension(inliningTarget, self, ptr, dim, index, hasSuboffsetsProfile);
            }
            return ptr;
        }

        @Specialization(replaces = "resolveTupleCached")
        MemoryPointer resolveTupleGeneric(VirtualFrame frame, PMemoryView self, PTuple indices,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile hasSuboffsetsProfile,
                        @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Shared @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(inliningTarget, indices);
            int ndim = self.getDimensions();
            checkTupleLength(indicesStorage, ndim);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < ndim; dim++) {
                Object indexObj = getItemNode.execute(inliningTarget, indicesStorage, dim);
                int index = convertIndex(frame, inliningTarget, indexCheckNode, indexObj);
                lookupDimension(inliningTarget, self, ptr, dim, index, hasSuboffsetsProfile);
            }
            return ptr;
        }

        @Specialization(guards = "!isPTuple(indexObj)")
        MemoryPointer resolveIntObj(VirtualFrame frame, PMemoryView self, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile hasSuboffsetsProfile,
                        @Shared @Cached PyIndexCheckNode indexCheckNode) {
            final int index = convertIndex(frame, inliningTarget, indexCheckNode, indexObj);
            if (self.getDimensions() == 1) {
                return resolveInt(self, index, inliningTarget, hasSuboffsetsProfile);
            } else {
                return resolveIntError(self, index);
            }
        }

        private void checkTupleLength(SequenceStorage indicesStorage, int ndim) {
            int length = indicesStorage.length();
            if (length == ndim) {
                return;
            }
            // Error cases
            if (ndim == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            } else if (length > ndim) {
                throw raise(TypeError, ErrorMessages.CANNOT_INDEX_D_DIMENSION_VIEW_WITH_D, ndim, length);
            } else {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.SUB_VIEWS_NOT_IMPLEMENTED);
            }
        }

        private int convertIndex(VirtualFrame frame, Node inliningTarget, PyIndexCheckNode indexCheckNode, Object indexObj) {
            if (!indexCheckNode.execute(inliningTarget, indexObj)) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
            }
            return getAsSizeNode().executeExact(frame, inliningTarget, indexObj, IndexError);
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
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
    @GenerateInline(false)
    public abstract static class ToJavaBytesNode extends Node {
        public abstract byte[] execute(PMemoryView self);

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"}, limit = "3")
        @SuppressWarnings("truffle-static-method")
        byte[] tobytesCached(PMemoryView self,
                        @Bind("this") Node inliningTarget,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Shared @Cached ReadBytesAtNode readBytesAtNode,
                        @Shared @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkReleased(raiseNode);
            byte[] bytes = new byte[self.getLength()];
            if (cachedDimensions == 0) {
                readBytesAtNode.execute(inliningTarget, bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convert(inliningTarget, bytes, self, cachedDimensions, readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @Specialization(replaces = "tobytesCached")
        byte[] tobytesGeneric(PMemoryView self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ReadBytesAtNode readBytesAtNode,
                        @Shared @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkReleased(raiseNode);
            byte[] bytes = new byte[self.getLength()];
            if (self.getDimensions() == 0) {
                readBytesAtNode.execute(inliningTarget, bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convertBoundary(inliningTarget, bytes, self, self.getDimensions(), readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @TruffleBoundary
        private void convertBoundary(Node inliningTarget, byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            convert(inliningTarget, dest, self, ndim, readBytesAtNode, callCapiFunction);
        }

        protected void convert(Node inliningTarget, byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(inliningTarget, dest, 0, self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static int recursive(Node inliningTarget, byte[] dest, int initialDestOffset, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim]);
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(inliningTarget, dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                    destOffset += self.getItemSize();
                } else {
                    destOffset = recursive(inliningTarget, dest, destOffset, self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                offset += self.getBufferStrides()[dim];
            }
            return destOffset;
        }

        @NeverDefault
        public static ToJavaBytesNode create() {
            return MemoryViewNodesFactory.ToJavaBytesNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ToJavaBytesFortranOrderNode extends ToJavaBytesNode {
        @Override
        protected void convert(Node inliningTarget, byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(inliningTarget, dest, 0, self.getItemSize(), self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static void recursive(Node inliningTarget, byte[] dest, int initialDestOffset, int destStride, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset,
                        ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim]);
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(inliningTarget, dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                } else {
                    recursive(inliningTarget, dest, destOffset, destStride * self.getBufferShape()[dim], self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                destOffset += destStride;
                offset += self.getBufferStrides()[dim];
            }
        }

        public static ToJavaBytesFortranOrderNode create() {
            return MemoryViewNodesFactory.ToJavaBytesFortranOrderNodeGen.create();
        }
    }

    public abstract static class ReleaseNode extends PNodeWithRaiseAndIndirectCall {

        public final void execute(PMemoryView self) {
            execute(null, self);
        }

        public abstract void execute(VirtualFrame frame, PMemoryView self);

        @Specialization(guards = "self.getReference() == null")
        static void releaseSimple(PMemoryView self,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            self.checkExports(raiseNode);
            self.setReleased();
        }

        @Specialization(guards = {"self.getReference() != null"})
        void releaseNative(VirtualFrame frame, PMemoryView self,
                        @Bind("this") Node inliningTarget,
                        @Cached ReleaseBufferNode releaseNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            self.checkExports(raiseNode);
            if (self.checkShouldReleaseBuffer()) {
                releaseNode.execute(frame, inliningTarget, this, self.getLifecycleManager());
            }
            self.setReleased();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReleaseBufferNode extends Node {

        public abstract void execute(Node inliningTarget, BufferLifecycleManager buffer);

        public static void executeUncached(BufferLifecycleManager buffer) {
            MemoryViewNodesFactory.ReleaseBufferNodeGen.getUncached().execute(null, buffer);
        }

        public final void execute(VirtualFrame frame, Node inliningTarget, PNodeWithRaiseAndIndirectCall caller, BufferLifecycleManager buffer) {
            Object state = IndirectCallContext.enter(frame, caller);
            try {
                execute(inliningTarget, buffer);
            } finally {
                IndirectCallContext.exit(frame, caller, state);
            }
        }

        @Specialization
        static void doCApiCached(NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType buffer,
                        @Cached(inline = false) PCallCapiFunction callReleaseNode) {
            callReleaseNode.call(NativeCAPISymbol.FUN_PY_TRUFFLE_RELEASE_BUFFER, buffer.bufferStructPointer);
        }

        @Specialization
        static void doCExtBuffer(NativeBufferLifecycleManagerFromSlot buffer,
                        @Cached(inline = false) CallNode callNode) {
            callNode.execute(buffer.releaseFunction, buffer.self, buffer.buffer);
        }

        @Fallback
        static void doManaged(@SuppressWarnings("unused") BufferLifecycleManager buffer) {
            // nothing to do
        }
    }
}
