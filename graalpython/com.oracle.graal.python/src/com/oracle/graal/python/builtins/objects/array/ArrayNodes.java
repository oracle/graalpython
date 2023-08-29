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
package com.oracle.graal.python.builtins.objects.array;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.BufferStorageNodes;
import com.oracle.graal.python.builtins.objects.common.BufferStorageNodes.UnpackValueNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public abstract class ArrayNodes {
    @GenerateInline
    @GenerateUncached(false)
    @GenerateCached(false)
    public abstract static class GetValueNode extends Node {
        public abstract Object execute(Node inliningTarget, PArray array, int index);

        @Specialization
        static Object get(Node inliningTarget, PArray array, int index,
                        @Cached UnpackValueNode unpackValueNode) {
            return unpackValueNode.execute(inliningTarget, array.getFormat(), array.getBuffer(), index * array.getFormat().bytesize);
        }
    }

    @GenerateInline
    @GenerateUncached(false)
    @GenerateCached(false)
    public abstract static class PutValueNode extends Node {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, PArray array, int index, Object value);

        @Specialization
        static void put(VirtualFrame frame, Node inliningTarget, PArray array, int index, Object value,
                        @Cached BufferStorageNodes.PackValueNode packValueNode) {
            packValueNode.execute(frame, inliningTarget, array.getFormat(), value, array.getBuffer(), index * array.getFormat().bytesize);
        }
    }

    @GenerateInline
    @GenerateUncached(false)
    @GenerateCached(false)
    public abstract static class CheckValueNode extends Node {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, PArray array, Object value);

        @Specialization
        static void check(VirtualFrame frame, Node inliningTarget, PArray array, Object value,
                        @Cached BufferStorageNodes.PackValueNode packValueNode) {
            packValueNode.execute(frame, inliningTarget, array.getFormat(), value, new ByteSequenceStorage(new byte[8]), 0);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class EnsureCapacityNode extends Node {
        public abstract void execute(Node inliningTarget, PArray array, int newCapacity);

        @Specialization
        static void ensure(Node inliningTarget, PArray array, int newCapacity,
                        @Cached SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached InlinedBranchProfile updateProfile) {
            try {
                int internalCapacity = PythonUtils.multiplyExact(newCapacity, array.getItemSize());
                SequenceStorage newStorage = ensureCapacityNode.execute(inliningTarget, array.getSequenceStorage(), internalCapacity);
                if (array.getSequenceStorage() != newStorage) {
                    updateProfile.enter(inliningTarget);
                    array.setSequenceStorage(newStorage);
                }
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.MemoryError);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetLengthNode extends Node {
        public abstract void execute(Node inliningTarget, PArray array, int newLength);

        @Specialization
        static void set(Node inliningTarget, PArray array, int newLength,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            try {
                int internalLength = PythonUtils.multiplyExact(newLength, array.getItemSize());
                setLenNode.execute(inliningTarget, array.getSequenceStorage(), internalLength);
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.MemoryError);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DeleteArraySliceNode extends Node {
        public abstract void execute(Node inliningTarget, PArray array, int from, int length);

        @Specialization
        static void del(Node inliningTarget, PArray array, int from, int length,
                        @Cached SequenceStorageNodes.DeleteSliceNode deleteSliceNode) {
            assert from + length <= array.getLength();
            SliceInfo info = new SliceInfo(from * array.getItemSize(), (from + length) * array.getItemSize(), 1);
            deleteSliceNode.execute(inliningTarget, array.getSequenceStorage(), info);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ShiftNode extends Node {
        public abstract void execute(Node inliningTarget, PArray array, int from, int by);

        @Specialization
        static void shift(Node inliningTarget, PArray array, int from, int by,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached SetLengthNode setLengthNode,
                        @Cached SequenceStorageNodes.MemMoveNode memMoveNode) {
            try {
                int newLength = PythonUtils.addExact(array.getLength(), by);
                ensureCapacityNode.execute(inliningTarget, array, newLength);
                int internalFrom = from * array.getItemSize();
                int internalBy = by * array.getItemSize();
                int internalLength = array.getSequenceStorage().length();
                memMoveNode.execute(inliningTarget, array.getSequenceStorage(), internalFrom + internalBy, internalFrom, internalLength - internalFrom);
                setLengthNode.execute(inliningTarget, array, newLength);
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.MemoryError);
            }
        }
    }
}
