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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetHashingStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * binary operations are implemented in {@link BaseSetBuiltins}
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PSet)
public final class SetBuiltins extends PythonBuiltins {

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SetBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class InitNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(iterable)")
        @SuppressWarnings("unused")
        static PNone doNoValue(VirtualFrame frame, PSet self, PNone iterable) {
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(iterable)"})
        static PNone doGeneric(VirtualFrame frame, PSet self, Object iterable,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage storage = getHashingStorageNode.doNoValue(frame, iterable);
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        @Fallback
        PNone fail(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object self, Object iterable) {
            throw raise(TypeError, ErrorMessages.SET_DOES_NOT_SUPPORT_ITERABLE_OBJ, iterable);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self) {
            return factory().createSet(self.getDictStorage());
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public static Object clear(PSet self,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage newStorage = lib.clear(self.getDictStorage());
            self.setDictStorage(newStorage);
            return PNone.NONE;
        }
    }

    @Builtin(name = "add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static Object add(VirtualFrame frame, PSet self, Object o,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self, o, PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(other)", limit = "1")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.union(self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
        }

        @Fallback
        Object doOr(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "|", self, other);
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }
    }

    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached", limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.union(result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.union(self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(right)", limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
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

        @Specialization(guards = "isNoValue(other)", limit = "2")
        PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.copy(self.getDictStorage()));
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(limit = "1")
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            result = lib.intersectWithFrame(result, getHashingStorage.execute(frame, other), hasFrame, frame);
            return factory().createSet(result);
        }
    }

    @Builtin(name = "intersection_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    @ImportStatic({SpecialMethodNames.class})
    public abstract static class IntersectUpdateNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.intersectWithFrame(result, getHashingStorage.execute(frame, args[i]), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached", limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.intersectWithFrame(result, getHashingStorage.execute(frame, o), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.intersectWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(other)", limit = "1")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.xor(self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
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
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.xor(self.getDictStorage(), getHashingStorage.execute(frame, other));
            return factory().createSet(result);
        }
    }

    @Builtin(name = "symmetric_difference_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceUpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.xor(result, getHashingStorage.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached", limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.xor(result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.xor(self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(right)", limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
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
        PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other) {
            return factory().createSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(limit = "1")
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.diffWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            return factory().createSet(result);
        }
    }

    @Builtin(name = "difference_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DifferenceUpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.diffWithFrame(result, getHashingStorage.execute(frame, args[i]), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached", limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.diffWithFrame(result, getHashingStorage.execute(frame, o), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "1")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.diffWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        Object remove(VirtualFrame frame, PSet self, Object key,
                        @Cached BranchProfile updatedStorage,
                        @Cached BaseSetBuiltins.ConvertKeyNode conv,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            Object checkedKey = conv.execute(key, factory());
            boolean hasKey = lib.hasKeyWithFrame(storage, checkedKey, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, checkedKey, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    self.setDictStorage(newStore);
                }
                return PNone.NONE;
            }
            throw raise(PythonErrorType.KeyError, key);

        }
    }

    @Builtin(name = "discard", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DiscardNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        Object discard(VirtualFrame frame, PSet self, Object key,
                        @Cached BranchProfile updatedStorage,
                        @Cached BaseSetBuiltins.ConvertKeyNode conv,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            Object checkedKey = conv.execute(key, factory());
            boolean hasKey = lib.hasKeyWithFrame(storage, checkedKey, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, checkedKey, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    self.setDictStorage(newStore);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PopNode extends PythonUnaryBuiltinNode {

        protected static void removeItem(VirtualFrame frame, PSet self, Object key,
                        HashingStorageLibrary lib, ConditionProfile hasFrame, BranchProfile updatedStorage) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    self.setDictStorage(newStore);
                }
            }
        }

        @Specialization(limit = "1")
        Object remove(VirtualFrame frame, PSet self,
                        @Cached BranchProfile updatedStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            for (Object next : lib.keys(self.getDictStorage())) {
                removeItem(frame, self, next, lib, hasFrame, updatedStorage);
                return next;
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.POP_FROM_EMPTY_SET);
        }
    }

}
