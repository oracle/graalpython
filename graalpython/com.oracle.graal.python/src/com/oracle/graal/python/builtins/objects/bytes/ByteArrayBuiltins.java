/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.objects.slice.PSlice.MISSING_INDEX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PByteArray)
public class ByteArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doGeneric(VirtualFrame frame, PByteArray self, Object key,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            throw raise(TypeError, "descriptor '__delitem__' requires a 'bytearray' object but received a '%p'", idx);
        }
    }

    @Builtin(name = __IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PByteArray add(PByteArray self, PIBytesLike other,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            updateSequenceStorage(self, res);
            return self;
        }

        @Specialization
        public PByteArray add(VirtualFrame frame, PByteArray self, PMemoryView other,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {

            Object bytesObj = toBytesNode.executeObject(frame, other);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), ((PBytes) bytesObj).getSequenceStorage());
                updateSequenceStorage(self, res);
                return self;
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytearray to %p", other);
        }

        private static void updateSequenceStorage(PByteArray array, SequenceStorage s) {
            if (array.getSequenceStorage() != s) {
                array.setSequenceStorage(s);
            }
        }
    }

    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayAppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PByteArray append(PByteArray byteArray, Object arg,
                        @Cached SequenceStorageNodes.AppendNode appendNode) {
            appendNode.execute(byteArray.getSequenceStorage(), arg, BytesLikeNoGeneralizationNode.SUPPLIER);
            return byteArray;
        }
    }

    // bytearray.extend(L)
    @Builtin(name = "extend", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        PNone doGeneric(VirtualFrame frame, PByteArray byteArray, Object source,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            SequenceStorage execute = extendNode.execute(frame, byteArray.getSequenceStorage(), source);
            assert byteArray.getSequenceStorage() == execute;
            return PNone.NONE;
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(BytesLikeNoGeneralizationNode.SUPPLIER);
        }

        protected boolean isPSequenceWithStorage(Object source) {
            return (source instanceof PSequence && !(source instanceof PTuple || source instanceof PRange));
        }

    }

    // bytearray.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayCopyNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray copy(PByteArray byteArray,
                        @Cached GetLazyClassNode getClass,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return factory().createByteArray(getClass.execute(byteArray), toByteArray.execute(byteArray.getSequenceStorage()));
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayReverseNode extends PythonBuiltinNode {

        @Specialization
        public PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PNone clear(VirtualFrame frame, PByteArray byteArray,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(frame, byteArray.getSequenceStorage(), factory().createSlice(MISSING_INDEX, MISSING_INDEX, 1));
            return PNone.NONE;
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"!isPSlice(idx)", "!isMemoryView(value)"})
        PNone doItem(VirtualFrame frame, PByteArray self, Object idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceMemoryview(VirtualFrame frame, PByteArray self, PSlice slice, PMemoryView value,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            Object bytesObj = callToBytesNode.executeObject(frame, value);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                doSlice(frame, self, slice, bytesObj, setItemNode);
                return PNone.NONE;
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @Specialization(guards = "!isMemoryView(value)")
        PNone doSlice(VirtualFrame frame, PByteArray self, PSlice idx, Object value,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            // this is really just a separate specialization due to the different error message
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object idx, Object value) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "an integer is required");
        }

        protected SequenceStorageNodes.SetItemNode createSetSlice() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "can assign only bytes, buffers, or iterables of ints in range(0, 256)");
        }

        protected static boolean isMemoryView(Object value) {
            return value instanceof PMemoryView;
        }
    }
}
