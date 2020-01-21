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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.UnsupportedEncodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltinsFactory.BytesLikeNoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GenNodeSupplier;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PBytes})
public class BytesBuiltins extends PythonBuiltins {

    public static CodingErrorAction toCodingErrorAction(String errors) {
        switch (errors) {
            case "strict":
                return CodingErrorAction.REPORT;
            case "ignore":
                return CodingErrorAction.IGNORE;
            case "replace":
                return CodingErrorAction.REPLACE;
        }
        return null;
    }

    public static CodingErrorAction toCodingErrorAction(String errors, PRaiseNode n) {
        CodingErrorAction action = toCodingErrorAction(errors);
        if (action != null) {
            return action;
        }
        throw n.raise(PythonErrorType.LookupError, "unknown error handler name '%s'", errors);
    }

    public static CodingErrorAction toCodingErrorAction(String errors, PythonBuiltinBaseNode n) {
        CodingErrorAction action = toCodingErrorAction(errors);
        if (action != null) {
            return action;
        }
        throw n.raise(PythonErrorType.LookupError, "unknown error handler name '%s'", errors);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        boolean eq(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return getEqNode().execute(frame, self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PIBytesLike) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a bytes-like object but received a '%p'", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        boolean ne(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return !getEqNode().execute(frame, self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        Object ne(Object self, Object other) {
            if (self instanceof PIBytesLike) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__ne__' requires a bytes-like object but received a '%p'", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    public abstract static class CmpNode extends PythonBinaryBuiltinNode {
        @Child private BytesNodes.CmpNode cmpNode;

        int cmp(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            if (cmpNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cmpNode = insert(BytesNodes.CmpNode.create());
            }
            return cmpNode.execute(frame, self, other);
        }

    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return cmp(frame, self, other) < 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return cmp(frame, self, other) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return cmp(frame, self, other) > 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PIBytesLike self, PIBytesLike other) {
            return cmp(frame, self, other) >= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isEmptyStorage(byteArray)")
        public boolean doEmpty(@SuppressWarnings("unused") PByteArray byteArray) {
            return false;
        }

        @Specialization(guards = "isIntStorage(byteArray)")
        public boolean doInt(PByteArray byteArray) {
            IntSequenceStorage store = (IntSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public boolean doByte(PByteArray byteArray) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isEmptyStorage(byteArray)")
        public boolean doEmpty(@SuppressWarnings("unused") PBytes byteArray) {
            return false;
        }

        @Specialization(guards = "isIntStorage(byteArray)")
        public boolean doInt(PBytes byteArray) {
            IntSequenceStorage store = (IntSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public boolean doByte(PBytes byteArray) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        boolean doLen(PIBytesLike operand,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(operand.getSequenceStorage()) != 0;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object add(PBytes left, PIBytesLike right,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            ByteSequenceStorage res = (ByteSequenceStorage) concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory().createBytes(res);
        }

        @Specialization
        public Object add(PByteArray self, PIBytesLike other,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            return factory().createByteArray(res);
        }

        @Specialization
        public Object add(VirtualFrame frame, PBytes self, PMemoryView other,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {

            Object bytesObj = toBytesNode.executeObject(frame, other);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), ((PBytes) bytesObj).getSequenceStorage());
                return factory().createBytes(res);
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @Specialization
        public Object add(VirtualFrame frame, PByteArray self, PMemoryView other,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {

            Object bytesObj = toBytesNode.executeObject(frame, other);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), ((PBytes) bytesObj).getSequenceStorage());
                return factory().createByteArray(res);
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object mul(VirtualFrame frame, PBytes self, int times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            return factory().createBytes(res);
        }

        @Specialization
        public Object mul(VirtualFrame frame, PByteArray self, int times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            return factory().createByteArray(res);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public Object mul(VirtualFrame frame, PBytes self, Object times,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode,
                        @CachedLibrary("times") PythonObjectLibrary lib) {
            int timesInt;
            if (hasFrame.profile(frame != null)) {
                timesInt = lib.asSizeWithState(times, PArguments.getThreadState(frame));
            } else {
                timesInt = lib.asSize(times);
            }
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), timesInt);
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", other);
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        private static final StringBuilder newStringBuilder() {
            return new StringBuilder("b'");
        }

        @TruffleBoundary
        private static final String sbFinishAndToString(StringBuilder sb) {
            sb.append("'");
            return sb.toString();
        }

        @Specialization
        public Object repr(VirtualFrame frame, PBytes self,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage store = self.getSequenceStorage();
            int len = lenNode.execute(store);
            StringBuilder sb = newStringBuilder();
            for (int i = 0; i < len; i++) {
                BytesUtils.byteRepr(sb, (byte) getItemNode.executeInt(frame, store, i));
            }
            return sbFinishAndToString(sb);
        }

        @Specialization
        public Object repr(PByteArray self, @Cached("create()") TypeNodes.GetNameNode getNameNode) {
            String typeName = getNameNode.execute(self.getLazyPythonClass());
            return self.formatByteArray(typeName);
        }
    }

    // bytes.join(iterable)
    // bytearray.join(iterable)
    @Builtin(name = "join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBytes join(VirtualFrame frame, PBytes bytes, Object iterable,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createBytes(bytesJoinNode.execute(frame, toByteArrayNode.execute(bytes.getSequenceStorage()), iterable));
        }

        @Specialization
        PByteArray join(VirtualFrame frame, PByteArray bytes, Object iterable,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createByteArray(bytesJoinNode.execute(frame, toByteArrayNode.execute(bytes.getSequenceStorage()), iterable));
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object arg) {
            throw raise(TypeError, "can only join an iterable");
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PIBytesLike self,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage());
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }

        @Specialization
        boolean contains(VirtualFrame frame, PIBytesLike self, PIBytesLike other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization
        boolean contains(VirtualFrame frame, PIBytesLike self, int other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization
        boolean contains(VirtualFrame frame, PIBytesLike self, long other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization(guards = {"!isBytes(other)"})
        boolean contains(VirtualFrame frame, PByteArray self, Object other,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {

            if (!containsNode.execute(frame, self.getSequenceStorage(), other)) {
                errorProfile.enter();
                throw raise(ValueError, "%s is not in bytes literal", other);
            }
            return true;
        }

        @Fallback
        boolean contains(@SuppressWarnings("unused") Object self, Object other) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", other);
        }

    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PSequenceIterator contains(PIBytesLike self) {
            return factory().createSequenceIterator(self);
        }
    }

    @Builtin(name = "startswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean startswith(VirtualFrame frame, PByteArray self, PTuple prefixes, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached GetNextNode getNextNode,
                        @Cached BytesNodes.FindNode findNode) {
            Object iterator = getIteratorNode.executeWith(frame, prefixes);
            while (true) {
                try {
                    Object arrayObj = getNextNode.execute(frame, iterator);
                    if (arrayObj instanceof PIBytesLike) {
                        PIBytesLike array = (PIBytesLike) arrayObj;
                        if (startswith(frame, self, array, start, end, findNode)) {
                            return true;
                        }
                    } else {
                        throw raise(PythonBuiltinClassType.TypeError, "a bytes-like object is required, not '%p'", arrayObj);
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return false;
                }
            }
        }

        @Specialization
        boolean startswith(VirtualFrame frame, PIBytesLike self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, prefix, 0, getLength(self.getSequenceStorage())) == 0;
        }

        @Specialization
        boolean startswith(VirtualFrame frame, PIBytesLike self, PIBytesLike prefix, int start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, prefix, start, getLength(self.getSequenceStorage())) == start;
        }

        @Specialization
        boolean startswith(VirtualFrame frame, PIBytesLike self, PIBytesLike prefix, int start, int end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, prefix, start, end) == start;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    @Builtin(name = "endswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class EndsWithNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean endswith(VirtualFrame frame, PIBytesLike self, PIBytesLike suffix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, suffix, getLength(self.getSequenceStorage()) - getLength(suffix.getSequenceStorage()), getLength(self.getSequenceStorage())) != -1;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    // bytes.index(x)
    // bytearray.index(x)
    @Builtin(name = "index", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayIndexNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        public int index(VirtualFrame frame, PIBytesLike byteArray, Object arg,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(frame, byteArray, arg, 0, getLength(byteArray.getSequenceStorage()));
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    // bytes.count(x)
    // bytearray.count(x)
    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ByteArrayCountNode extends PythonBinaryBuiltinNode {

        @Specialization
        int count(VirtualFrame frame, PIBytesLike byteArray, Object arg,
                        @Cached("createClassProfile()") ValueProfile storeProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {

            SequenceStorage profiled = storeProfile.profile(byteArray.getSequenceStorage());
            int cnt = 0;
            for (int i = 0; i < profiled.length(); i++) {
                if (eqNode.executeBool(frame, arg, getItemNode.execute(frame, profiled, i))) {
                    cnt++;
                }
            }
            return cnt;
        }
    }

    // bytes.find(bytes[, start[, end]])
    @Builtin(name = "find", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Specialization
        int find(VirtualFrame frame, PIBytesLike self, Object sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Shared("lenNode") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("findNode") @Cached BytesNodes.FindNode findNode) {
            return find(frame, self, sub, 0, lenNode.execute(self.getSequenceStorage()), findNode);
        }

        @Specialization
        int find(VirtualFrame frame, PIBytesLike self, Object sub, int start, @SuppressWarnings("unused") PNone end,
                        @Shared("lenNode") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("findNode") @Cached BytesNodes.FindNode findNode) {
            return find(frame, self, sub, start, lenNode.execute(self.getSequenceStorage()), findNode);
        }

        @Specialization
        int find (VirtualFrame frame, PIBytesLike self, Object sub, Object start, @SuppressWarnings("unused") PNone end,
                        @Shared("lenNode") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("findNode") @Cached BytesNodes.FindNode findNode) {
            return find(frame, self, sub, start, lenNode.execute(self.getSequenceStorage()), findNode);
        }

        @Specialization
        int find(VirtualFrame frame, PIBytesLike self, Object sub, int start, int ending,
                        @Shared("findNode") @Cached BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, sub, start, ending);
        }

        @Specialization
        int find(VirtualFrame frame, PIBytesLike self, Object sub, Object start, Object ending,
                        @Shared("findNode") @Cached BytesNodes.FindNode findNode) {
            return findNode.execute(frame, self, sub, start, ending);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSlice(VirtualFrame frame, PBytes self, Object key,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.create(), (s, f) -> f.createBytes(s));
        }

        @Specialization
        Object doSlice(VirtualFrame frame, PByteArray self, Object key,
                        @Cached("createGetArrayItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        protected static GetItemNode createGetArrayItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forBytearray(), (s, f) -> f.createByteArray(s));
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonTernaryBuiltinNode {
        @Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @Specialization
        PBytes replace(VirtualFrame frame, PBytes self, PIBytesLike substr, PIBytesLike replacement) {
            byte[] bytes = toBytes.execute(frame, self);
            byte[] subBytes = toBytes.execute(frame, substr);
            byte[] replacementBytes = toBytes.execute(frame, replacement);
            try {
                byte[] newBytes = doReplace(bytes, subBytes, replacementBytes);
                return factory().createBytes(newBytes);
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Specialization
        PByteArray replace(VirtualFrame frame, PByteArray self, PIBytesLike substr, PIBytesLike replacement) {
            byte[] bytes = toBytes.execute(frame, self);
            byte[] subBytes = toBytes.execute(frame, substr);
            byte[] replacementBytes = toBytes.execute(frame, replacement);
            try {
                byte[] newBytes = doReplace(bytes, subBytes, replacementBytes);
                return factory().createByteArray(newBytes);
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @TruffleBoundary
        private static byte[] doReplace(byte[] bytes, byte[] subBytes, byte[] replacementBytes) throws UnsupportedEncodingException {
            String string = new String(bytes, "ASCII");
            String subString = new String(subBytes, "ASCII");
            String replacementString = new String(replacementBytes, "ASCII");
            return string.replace(subString, replacementString).getBytes("ASCII");
        }
    }

    @Builtin(name = "lower", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LowerNode extends PythonUnaryBuiltinNode {
        @Node.Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @CompilerDirectives.TruffleBoundary
        private static byte[] lower(byte[] bytes) {
            try {
                String string = new String(bytes, "ASCII");
                return string.toLowerCase().getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Specialization
        PByteArray replace(VirtualFrame frame, PByteArray self) {
            return factory().createByteArray(lower(toBytes.execute(frame, self)));
        }

        @Specialization
        PBytes replace(VirtualFrame frame, PBytes self) {
            return factory().createBytes(lower(toBytes.execute(frame, self)));
        }
    }

    @Builtin(name = "upper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UpperNode extends PythonUnaryBuiltinNode {
        @Node.Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @CompilerDirectives.TruffleBoundary
        private static byte[] upper(byte[] bytes) {
            try {
                String string = new String(bytes, "ASCII");
                return string.toUpperCase().getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Specialization
        PByteArray replace(VirtualFrame frame, PByteArray self) {
            return factory().createByteArray(upper(toBytes.execute(frame, self)));
        }

        @Specialization
        PBytes replace(VirtualFrame frame, PBytes self) {
            return factory().createBytes(upper(toBytes.execute(frame, self)));
        }
    }

    abstract static class AStripNode extends PythonBinaryBuiltinNode {
        int mod() {
            throw new RuntimeException();
        }

        int stop(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        int start(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        PByteArray newByteArrayFrom(@SuppressWarnings("unused") byte[] bs, @SuppressWarnings("unused") int i) {
            throw new RuntimeException();
        }

        PBytes newBytesFrom(@SuppressWarnings("unused") byte[] bs, @SuppressWarnings("unused") int i) {
            throw new RuntimeException();
        }

        private int findIndex(byte[] bs) {
            int i = start(bs);
            int stop = stop(bs);
            for (; i != stop; i += mod()) {
                if (!isWhitespace(bs[i])) {
                    break;
                }
            }
            return i;
        }

        @Specialization
        PByteArray strip(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(frame, self);
            return newByteArrayFrom(bs, findIndex(bs));
        }

        @Specialization
        PBytes strip(VirtualFrame frame, PBytes self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(frame, self);
            return newBytesFrom(bs, findIndex(bs));
        }

        @CompilerDirectives.TruffleBoundary
        private static boolean isWhitespace(byte b) {
            return Character.isWhitespace(b);
        }

        private int findIndex(byte[] bs, byte[] stripBs) {
            int i = start(bs);
            int stop = stop(bs);
            outer: for (; i != stop; i += mod()) {
                for (byte b : stripBs) {
                    if (b == bs[i]) {
                        continue outer;
                    }
                }
                break;
            }
            return i;
        }

        @Specialization
        PByteArray strip(VirtualFrame frame, PByteArray self, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached("create()") BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(frame, bytes);
            byte[] bs = otherToBytesNode.execute(frame, self);
            return newByteArrayFrom(bs, findIndex(bs, stripBs));
        }

        @Specialization
        PBytes strip(VirtualFrame frame, PBytes self, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached("create()") BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(frame, bytes);
            byte[] bs = otherToBytesNode.execute(frame, self);
            return newBytesFrom(bs, findIndex(bs, stripBs));
        }

    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, parameterNames = {"self", "bytes"})
    @GenerateNodeFactory
    abstract static class LStripNode extends AStripNode {

        private static byte[] getResultBytes(int i, byte[] bs) {
            byte[] out;
            if (i != 0) {
                int len = bs.length - i;
                out = new byte[len];
                System.arraycopy(bs, i, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        PByteArray newByteArrayFrom(byte[] bs, int i) {
            return factory().createByteArray(getResultBytes(i, bs));
        }

        @Override
        PBytes newBytesFrom(byte[] bs, int i) {
            return factory().createBytes(getResultBytes(i, bs));
        }

        @Override
        int mod() {
            return 1;
        }

        @Override
        int stop(byte[] bs) {
            return bs.length;
        }

        @Override
        int start(byte[] bs) {
            return 0;
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, parameterNames = {"self", "bytes"})
    @GenerateNodeFactory
    abstract static class RStripNode extends AStripNode {

        private static byte[] getResultBytes(int i, byte[] bs) {
            byte[] out;
            int len = i + 1;
            if (len != bs.length) {
                out = new byte[len];
                System.arraycopy(bs, 0, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        PByteArray newByteArrayFrom(byte[] bs, int i) {
            byte[] out = getResultBytes(i, bs);
            return factory().createByteArray(out);
        }

        @Override
        PBytes newBytesFrom(byte[] bs, int i) {
            byte[] out = getResultBytes(i, bs);
            return factory().createBytes(out);
        }

        @Override
        int mod() {
            return -1;
        }

        @Override
        int stop(byte[] bs) {
            return -1;
        }

        @Override
        int start(byte[] bs) {
            return bs.length - 1;
        }
    }

    abstract static class AbstractSplitNode extends PythonBuiltinNode {

        abstract PList execute(VirtualFrame frame, Object bytes, Object sep, Object maxsplit);

        @SuppressWarnings("unused")
        protected List<byte[]> splitWhitespace(byte[] bytes, int maxsplit) {
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        protected List<byte[]> splitDelimiter(byte[] bytes, byte[] sep, int maxsplit) {
            throw new RuntimeException();
        }

        protected AbstractSplitNode createRecursiveNode() {
            throw new RuntimeException();
        }

        @CompilationFinal private ConditionProfile isEmptySepProfile;
        @CompilationFinal private ConditionProfile overflowProfile;

        @Child private BytesNodes.ToBytesNode selfToBytesNode;
        @Child private BytesNodes.ToBytesNode sepToBytesNode;
        @Child private AppendNode appendNode;
        @Child private AbstractSplitNode recursiveNode;

        // taken from JPython
        private static final int SWAP_CASE = 0x20;
        private static final byte UPPER = 0b1;
        private static final byte LOWER = 0b10;
        private static final byte DIGIT = 0b100;
        private static final byte SPACE = 0b1000;
        private static final byte[] CTYPE = new byte[256];

        static {
            for (int c = 'A'; c <= 'Z'; c++) {
                CTYPE[0x80 + c] = UPPER;
                CTYPE[0x80 + SWAP_CASE + c] = LOWER;
            }
            for (int c = '0'; c <= '9'; c++) {
                CTYPE[0x80 + c] = DIGIT;
            }
            for (char c : " \t\n\u000b\f\r".toCharArray()) {
                CTYPE[0x80 + c] = SPACE;
            }
        }

        private ConditionProfile getIsEmptyProfile() {
            if (isEmptySepProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isEmptySepProfile = ConditionProfile.createBinaryProfile();
            }
            return isEmptySepProfile;
        }

        private ConditionProfile getOverflowProfile() {
            if (overflowProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                overflowProfile = ConditionProfile.createBinaryProfile();
            }
            return overflowProfile;
        }

        protected BytesNodes.ToBytesNode getSelfToBytesNode() {
            if (selfToBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                selfToBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return selfToBytesNode;
        }

        protected BytesNodes.ToBytesNode getSepToBytesNode() {
            if (sepToBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sepToBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return sepToBytesNode;
        }

        protected AppendNode getAppendNode() {
            if (appendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendNode = insert(AppendNode.create());
            }
            return appendNode;
        }

        private AbstractSplitNode getRecursiveNode() {
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = insert(createRecursiveNode());
            }
            return recursiveNode;
        }

        private int getIntValue(PInt from) {
            try {
                return from.intValueExact();
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError, "Python int too large to convert to C long");
            }
        }

        private int getIntValue(long from) {
            if (getOverflowProfile().profile(Integer.MIN_VALUE > from || from > Integer.MAX_VALUE)) {
                throw raise(PythonErrorType.OverflowError, "Python int too large to convert to C long");
            }
            return (int) from;
        }

        private PList getBytesResult(List<byte[]> bytes) {
            PList result = factory().createList();
            for (byte[] bs : bytes) {
                getAppendNode().execute(result, factory().createBytes(bs));
            }
            return result;
        }

        private PList getByteArrayResult(List<byte[]> bytes) {
            PList result = factory().createList();
            for (byte[] bs : bytes) {
                getAppendNode().execute(result, factory().createByteArray(bs));
            }
            return result;
        }

        @TruffleBoundary
        protected static byte[] copyOfRange(byte[] bytes, int from, int to) {
            return Arrays.copyOfRange(bytes, from, to);
        }

        protected static boolean isSpace(byte b) {
            return (CTYPE[0x80 + b] & SPACE) != 0;
        }

        // split()
        // rsplit()
        @Specialization
        PList split(VirtualFrame frame, PBytes bytes, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") PNone maxsplit) {
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getBytesResult(splitWhitespace(splitBs, -1));
        }

        @Specialization
        PList split(VirtualFrame frame, PByteArray bytes, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") PNone maxsplit) {
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getByteArrayResult(splitWhitespace(splitBs, -1));
        }

        // split(sep=...)
        // rsplit(sep=...)
        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PBytes bytes, Object sep, @SuppressWarnings("unused") PNone maxsplit) {
            return split(frame, bytes, sep, -1);
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PByteArray bytes, Object sep, @SuppressWarnings("unused") PNone maxsplit) {
            return split(frame, bytes, sep, -1);
        }

        // split(sep=..., maxsplit=...)
        // rsplit(sep=..., maxsplit=...)
        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PBytes bytes, Object sep, int maxsplit) {
            byte[] sepBs = getSepToBytesNode().execute(frame, sep);
            if (getIsEmptyProfile().profile(sepBs.length == 0)) {
                throw raise(PythonErrorType.ValueError, "empty separator");
            }
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getBytesResult(splitDelimiter(splitBs, sepBs, maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PBytes bytes, Object sep, long maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PBytes bytes, Object sep, PInt maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)", limit = "getCallSiteInlineCacheMaxDepth()")
        PList split(VirtualFrame frame, PBytes bytes, Object sep, Object maxsplit,
                        @CachedLibrary("maxsplit") PythonObjectLibrary lib) {
            return getRecursiveNode().execute(frame, bytes, sep, asIndex(frame, maxsplit, lib));
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PByteArray bytes, Object sep, int maxsplit) {
            byte[] sepBs = getSepToBytesNode().execute(frame, sep);
            if (getIsEmptyProfile().profile(sepBs.length == 0)) {
                throw raise(PythonErrorType.ValueError, "empty separator");
            }
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getByteArrayResult(splitDelimiter(splitBs, sepBs, maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PByteArray bytes, Object sep, long maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)")
        PList split(VirtualFrame frame, PByteArray bytes, Object sep, PInt maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(guards = "!isPNone(sep)", limit = "getCallSiteInlineCacheMaxDepth()")
        PList split(VirtualFrame frame, PByteArray bytes, Object sep, Object maxsplit,
                        @CachedLibrary("maxsplit") PythonObjectLibrary lib) {
            return getRecursiveNode().execute(frame, bytes, sep, asIndex(frame, maxsplit, lib));
        }

        // split(maxsplit=...)
        // rsplit(maxsplit=...)
        @Specialization
        PList split(VirtualFrame frame, PBytes bytes, @SuppressWarnings("unused") PNone sep, int maxsplit) {
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getBytesResult(splitWhitespace(splitBs, maxsplit));
        }

        @Specialization
        PList split(VirtualFrame frame, PBytes bytes, PNone sep, long maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization
        PList split(VirtualFrame frame, PBytes bytes, PNone sep, PInt maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        PList split(VirtualFrame frame, PBytes bytes, PNone sep, Object maxsplit,
                        @CachedLibrary("maxsplit") PythonObjectLibrary lib) {
            return getRecursiveNode().execute(frame, bytes, sep, asIndex(frame, maxsplit, lib));
        }

        @Specialization
        PList split(VirtualFrame frame, PByteArray bytes, @SuppressWarnings("unused") PNone sep, int maxsplit) {
            byte[] splitBs = getSelfToBytesNode().execute(frame, bytes);
            return getByteArrayResult(splitWhitespace(splitBs, maxsplit));
        }

        @Specialization
        PList split(VirtualFrame frame, PByteArray bytes, PNone sep, long maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization
        PList split(VirtualFrame frame, PByteArray bytes, PNone sep, PInt maxsplit) {
            return split(frame, bytes, sep, getIntValue(maxsplit));
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        PList split(VirtualFrame frame, PByteArray bytes, PNone sep, Object maxsplit,
                        @CachedLibrary("maxsplit") PythonObjectLibrary lib) {
            return getRecursiveNode().execute(frame, bytes, sep, asIndex(frame, maxsplit, lib));
        }

        private static Object asIndex(VirtualFrame frame, Object maxsplit, PythonObjectLibrary lib) {
            return lib.asIndexWithState(maxsplit, PArguments.getThreadState(frame));
        }

    }

    @Builtin(name = "split", minNumOfPositionalArgs = 1, parameterNames = {"self", "sep", "maxsplit"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SplitNode extends AbstractSplitNode {

        @Override
        protected List<byte[]> splitWhitespace(byte[] bytes, int maxsplit) {
            int offset = 0;
            int size = bytes.length;

            List<byte[]> result = new ArrayList<>();
            if (size == 0) {
                return result;
            }
            if (maxsplit == 0) {
                // handling case b''.split(b' ') -> [b'']
                result.add(bytes);
                return result;
            }

            int countSplit = maxsplit;
            int p, q; // Indexes of unsplit text and whitespace

            // Scan over leading whitespace
            for (p = offset; p < size && isSpace(bytes[p]); p++) {
            }

            // At this point if p<limit it points to the start of a word.
            // While we have some splits left (if maxsplit started>=0)
            while (p < size && countSplit-- != 0) {
                // Delimit a word at p
                // Skip q over the non-whitespace at p
                for (q = p; q < size && !isSpace(bytes[q]); q++) {
                }
                // storage[q] is whitespace or it is at the limit
                result.add(copyOfRange(bytes, p - offset, q - offset));
                // Skip p over the whitespace at q
                for (p = q; p < size && isSpace(bytes[p]); p++) {
                }
            }

            // Append the remaining unsplit text if any
            if (p < size) {
                result.add(copyOfRange(bytes, p - offset, size));
            }
            return result;
        }

        @Override
        protected List<byte[]> splitDelimiter(byte[] bytes, byte[] sep, int maxsplit) {
            List<byte[]> result = new ArrayList<>();
            int size = bytes.length;

            if (maxsplit == 0 || size == 0) {
                // if maxsplit is 0, just add the whole input
                result.add(bytes);
                return result;
            }
            if (sep.length == 0) {
                // should not happen, and should be threated outside this method
                return result;
            }
            int countSplit = maxsplit;
            int begin = 0;

            outer: for (int offset = 0; offset < size - sep.length + 1; offset++) {
                for (int sepOffset = 0; sepOffset < sep.length; sepOffset++) {
                    if (bytes[offset + sepOffset] != sep[sepOffset]) {
                        continue outer;
                    }
                }

                if (begin < offset) {
                    result.add(copyOfRange(bytes, begin, offset));
                } else {
                    result.add(new byte[0]);
                }
                begin = offset + sep.length;
                offset = begin - 1;
                if (--countSplit == 0) {
                    break;
                }
            }

            if (begin != size) {
                result.add(copyOfRange(bytes, begin, size));
            }
            return result;
        }

        @Override
        protected AbstractSplitNode createRecursiveNode() {
            return BytesBuiltinsFactory.SplitNodeFactory.create(new ReadArgumentNode[]{});
        }
    }

    @Builtin(name = "rsplit", minNumOfPositionalArgs = 1, parameterNames = {"self", "sep", "maxsplit"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RSplitNode extends AbstractSplitNode {

        @Override
        protected List<byte[]> splitWhitespace(byte[] bytes, int maxsplit) {
            int size = bytes.length;
            List<byte[]> result = new ArrayList<>();

            if (size == 0) {
                return result;
            }

            if (maxsplit == 0) {
                // if maxsplit is 0, just add the whole input
                result.add(bytes);
                return result;
            }

            int countSplit = maxsplit;
            int offset = 0;

            int p, q; // Indexes of unsplit text and whitespace

            // Scan backwards over trailing whitespace
            for (q = offset + size; q > offset; --q) {
                if (!isSpace(bytes[q - 1])) {
                    break;
                }
            }

            // At this point storage[q-1] is the rightmost non-space byte, or
            // q=offset if there aren't any. While we have some splits left ...
            while (q > offset && countSplit-- != 0) {
                // Delimit the word whose last byte is storage[q-1]
                // Skip p backwards over the non-whitespace
                for (p = q; p > offset; --p) {
                    if (isSpace(bytes[p - 1])) {
                        break;
                    }
                }

                result.add(0, copyOfRange(bytes, p - offset, q - offset));
                // Skip q backwards over the whitespace
                for (q = p; q > offset; --q) {
                    if (!isSpace(bytes[q - 1])) {
                        break;
                    }
                }
            }

            // Prepend the remaining unsplit text if any
            if (q > offset) {
                result.add(0, copyOfRange(bytes, 0, q - offset));
            }
            return result;
        }

        @Override
        protected List<byte[]> splitDelimiter(byte[] bytes, byte[] sep, int maxsplit) {
            List<byte[]> result = new ArrayList<>();
            int size = bytes.length;

            if (maxsplit == 0 || size == 0) {
                // if maxsplit is 0, just add the whole input
                result.add(bytes);
                return result;
            }
            if (sep.length == 0) {
                // should not happen, and should be threated outside this method
                return result;
            }

            int countSplit = maxsplit;
            int end = size;

            outer: for (int offset = size - 1; offset >= 0; offset--) {
                for (int sepOffset = 0; sepOffset < sep.length; sepOffset++) {
                    if (bytes[offset - sepOffset] != sep[sep.length - sepOffset - 1]) {
                        continue outer;
                    }
                }

                if (end > offset) {
                    result.add(0, copyOfRange(bytes, offset + 1, end));
                } else {
                    result.add(0, new byte[0]);
                }
                end = offset;
                if (--countSplit == 0) {
                    break;
                }
            }

            if (end != 0) {
                result.add(0, copyOfRange(bytes, 0, end));
            }
            return result;
        }

        @Override
        protected AbstractSplitNode createRecursiveNode() {
            return BytesBuiltinsFactory.RSplitNodeFactory.create(new ReadArgumentNode[]{});
        }
    }

    // static bytes.maketrans()
    // static bytearray.maketrans()
    @Builtin(name = "maketrans", minNumOfPositionalArgs = 3, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class MakeTransNode extends PythonBuiltinNode {

        @Specialization
        PBytes maketrans(VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass cls, Object from, Object to,
                        @Cached("create()") BytesNodes.ToBytesNode toByteNode) {
            byte[] fromB = toByteNode.execute(frame, from);
            byte[] toB = toByteNode.execute(frame, to);
            if (fromB.length != toB.length) {
                throw raise(PythonErrorType.ValueError, "maketrans arguments must have same length");
            }

            byte[] table = new byte[256];
            for (int i = 0; i < 256; i++) {
                table[i] = (byte) i;
            }

            for (int i = 0; i < fromB.length; i++) {
                byte value = fromB[i];
                table[value < 0 ? value + 256 : value] = toB[i];
            }

            return factory().createBytes(table);
        }

    }

    // bytes.translate(table, delete=b'')
    // bytearray.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends PythonBuiltinNode {

        @Child BytesNodes.ToBytesNode toBytesNode;

        @CompilationFinal private ConditionProfile isLenTable256Profile;

        private BytesNodes.ToBytesNode getToBytesNode() {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode;
        }

        private void checkLengthOfTable(byte[] table) {
            if (isLenTable256Profile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isLenTable256Profile = ConditionProfile.createBinaryProfile();
            }

            if (isLenTable256Profile.profile(table.length != 256)) {
                throw raise(PythonErrorType.ValueError, "translation table must be 256 characters long");
            }
        }

        private static class Result {
            byte[] array;
            // we have to know, whether the result array was changed ->
            // if not in bytes case it has to return the input bytes
            // in bytearray case it has to return always new bytearray
            boolean changed;

            public Result(byte[] array, boolean changed) {
                this.array = array;
                this.changed = changed;
            }
        }

        private static boolean[] createDeleteTable(byte[] delete) {
            boolean[] result = new boolean[256];
            for (int i = 0; i < 256; i++) {
                result[i] = false;
            }
            for (int i = 0; i < delete.length; i++) {
                result[delete[i]] = true;
            }
            return result;
        }

        private static Result delete(byte[] self, byte[] table) {
            final int length = self.length;
            byte[] result = new byte[length];
            int resultLen = 0;
            boolean[] toDelete = createDeleteTable(table);

            for (int i = 0; i < length; i++) {
                if (!toDelete[self[i] & 0xFF]) {
                    result[resultLen] = self[i];
                    resultLen++;
                }
            }
            if (resultLen == length) {
                return new Result(result, false);
            }
            return new Result(Arrays.copyOf(result, resultLen), true);
        }

        private static Result translate(byte[] self, byte[] table) {
            final int length = self.length;
            byte[] result = new byte[length];
            boolean changed = false;
            for (int i = 0; i < length; i++) {
                byte b = table[self[i]];
                if (!changed && b != self[i]) {
                    changed = true;
                }
                result[i] = b;
            }
            return new Result(result, changed);
        }

        private static Result translateAndDelete(byte[] self, byte[] table, byte[] delete) {
            final int length = self.length;
            byte[] result = new byte[length];
            int resultLen = 0;
            boolean changed = false;
            boolean[] toDelete = createDeleteTable(delete);

            for (int i = 0; i < length; i++) {
                if (!toDelete[self[i]]) {
                    byte b = table[self[i]];
                    if (!changed && b != self[i]) {
                        changed = true;
                    }
                    result[resultLen] = b;
                    resultLen++;
                }
            }
            if (resultLen == length) {
                return new Result(result, changed);
            }
            return new Result(Arrays.copyOf(result, resultLen), true);
        }

        @Specialization(guards = "isNoValue(delete)")
        public PBytes translate(PBytes self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete) {
            return self;
        }

        @Specialization(guards = "isNoValue(delete)")
        public PByteArray translate(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @Cached BytesNodes.ToBytesNode toBytes) {
            byte[] content = toBytes.execute(frame, self);
            return factory().createByteArray(content);
        }

        @Specialization(guards = "!isNone(table)")
        PBytes translate(VirtualFrame frame, PBytes self, Object table, @SuppressWarnings("unused") PNone delete) {
            byte[] bTable = getToBytesNode().execute(frame, table);
            checkLengthOfTable(bTable);
            byte[] bSelf = getToBytesNode().execute(frame, self);

            Result result = translate(bSelf, bTable);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = "!isNone(table)")
        PByteArray translate(VirtualFrame frame, PByteArray self, Object table, @SuppressWarnings("unused") PNone delete) {
            byte[] bTable = getToBytesNode().execute(frame, table);
            checkLengthOfTable(bTable);
            byte[] bSelf = getToBytesNode().execute(frame, self);

            Result result = translate(bSelf, bTable);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = "isNone(table)")
        PBytes delete(VirtualFrame frame, PBytes self, @SuppressWarnings("unused") PNone table, Object delete) {
            byte[] bSelf = getToBytesNode().execute(frame, self);
            byte[] bDelete = getToBytesNode().execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = "isNone(table)")
        PByteArray delete(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, Object delete) {
            byte[] bSelf = getToBytesNode().execute(frame, self);
            byte[] bDelete = getToBytesNode().execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        PBytes translateAndDelete(VirtualFrame frame, PBytes self, Object table, Object delete) {
            byte[] bTable = getToBytesNode().execute(frame, table);
            checkLengthOfTable(bTable);
            byte[] bDelete = getToBytesNode().execute(frame, delete);
            byte[] bSelf = getToBytesNode().execute(frame, self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        PByteArray translateAndDelete(VirtualFrame frame, PByteArray self, Object table, Object delete) {
            byte[] bTable = getToBytesNode().execute(frame, table);
            checkLengthOfTable(bTable);
            byte[] bDelete = getToBytesNode().execute(frame, delete);
            byte[] bSelf = getToBytesNode().execute(frame, self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            return factory().createByteArray(result.array);
        }
    }

    @GenerateUncached
    public abstract static class BytesLikeNoGeneralizationNode extends SequenceStorageNodes.NoGeneralizationNode {

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {

            public GeneralizationNode create() {
                return BytesLikeNoGeneralizationNodeGen.create();
            }

            public GeneralizationNode getUncached() {
                return BytesLikeNoGeneralizationNodeGen.getUncached();
            }

        };

        @Override
        protected final String getErrorMessage() {
            return CastToByteNode.INVALID_BYTE_VALUE;
        }

    }
}
