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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetHashingStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * binary operations are implemented in {@link BaseSetBuiltins}
 */
@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFrozenSet})
public final class FrozenSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrozenSetBuiltinsFactory.getFactories();
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isBuiltinClass.profileIsAnyBuiltinObject(arg)")
        public static PFrozenSet frozensetIdentity(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet arg,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
            return arg;
        }

        @Specialization(guards = "!isBuiltinClass.profileIsAnyBuiltinObject(arg)")
        public PFrozenSet subFrozensetIdentity(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet arg,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
            return factory().createFrozenSet(arg.getDictStorage());
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.intersectWithFrame(left.getDictStorage(), right.getDictStorage(), hasFrame, frame);
            return factory().createFrozenSet(storage);
        }

        @Specialization(guards = {"!isPBaseSet(right)", "canDoSetBinOp(right)"}, limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, Object right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.intersectWithFrame(left.getDictStorage(), getHashingStorageNode.execute(frame, right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Fallback
        Object doAnd(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "&", self, other);
        }
    }

    @Builtin(name = "intersection", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class IntersectNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, @SuppressWarnings("unused") PNone other) {
            return factory().createFrozenSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createFrozenSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createFrozenSet(result);
        }

        @Specialization(limit = "1")
        PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.intersectWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            return factory().createFrozenSet(result);
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class OrNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.union(left.getDictStorage(), right.getDictStorage());
            return factory().createFrozenSet(storage);
        }

        @Specialization(guards = {"!isPBaseSet(right)", "canDoSetBinOp(right)"}, limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.union(left.getDictStorage(), getHashingStorageNode.execute(frame, right));
            return factory().createSet(storage);
        }

        @Fallback
        Object doOr(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "|", self, other);
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class XorNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.xor(left.getDictStorage(), right.getDictStorage());
            return factory().createFrozenSet(storage);
        }

        @Specialization(guards = {"!isPBaseSet(right)", "canDoSetBinOp(right)"}, limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.xor(left.getDictStorage(), getHashingStorageNode.execute(frame, right));
            return factory().createSet(storage);
        }

        @Fallback
        Object doOr(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "^", self, other);
        }
    }

    @Builtin(name = "symmetric_difference", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceNode extends PythonBuiltinNode {

        @Specialization(limit = "1")
        PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.xor(self.getDictStorage(), getHashingStorage.execute(frame, other));
            return factory().createFrozenSet(result);
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.diffWithFrame(left.getDictStorage(), right.getDictStorage(), hasFrame, frame);
            return factory().createFrozenSet(storage);
        }

        @Specialization(guards = {"!isPBaseSet(right)", "canDoSetBinOp(right)"}, limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, Object right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(left.getDictStorage(), getHashingStorageNode.execute(frame, right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Fallback
        Object doSub(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "-", self, other);
        }
    }

    @Builtin(name = "difference", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DifferenceNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, @SuppressWarnings("unused") PNone other) {
            return factory().createFrozenSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createFrozenSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createFrozenSet(result);
        }

        @Specialization(limit = "1")
        PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.diffWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            return factory().createFrozenSet(result);
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.union(result, getHashingStorage.execute(frame, args[i]));
            }
            return factory().createFrozenSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.union(result, getHashingStorage.execute(frame, args[i]));
            }
            return factory().createFrozenSet(result);
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        public static long getHash(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"}, limit = "1")
        public static long computeHash(VirtualFrame frame, PFrozenSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary hlib,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            // adapted from https://github.com/python/cpython/blob/master/Objects/setobject.c#L758
            HashingStorage storage = self.getDictStorage();
            int len = hlib.length(storage);
            long m1 = 0x72e8ef4d;
            long m2 = 0x10dcd;
            long c1 = 0x3611c3e3;
            long c2 = 0x2338c7c1;
            long hash = 0;

            for (Object key : hlib.keys(storage)) {
                long tmp = lib.hashWithFrame(key, frame);
                hash ^= shuffleBits(tmp);
            }

            // TODO:
            // Remove the effect of an odd number of NULL entries

            // TODO:
            // Remove the effect of an odd number of dummy entries

            // Factor in the number of active entries
            hash ^= (len + 1) * m1;

            // Disperse patterns arising in nested frozensets
            hash ^= (hash >> 11) ^ (hash >> 25);
            hash = hash * m2 + c1;

            // -1 is reserved as an error code
            if (hash == -1) {
                hash = c2;
            }

            self.setHash(hash);
            return hash;
        }

        private static long shuffleBits(long value) {
            return ((value ^ 0x55b4db3) ^ (value << 16)) * 0xd93f34d7;
        }

        @Fallback
        static Object genericHash(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
