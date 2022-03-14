/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetHashingStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * binary operations are implemented in {@link BaseSetBuiltins}
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PSet)
public final class SetBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
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

        @Specialization(guards = "isNoValue(iterable)", limit = "3")
        static PNone doNoValue(PSet self, @SuppressWarnings("unused") PNone iterable,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage newStorage = lib.clear(self.getDictStorage());
            self.setDictStorage(newStorage);
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

        @Specialization(limit = "3")
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
                        @Cached SetNodes.AddNode addNode) {
            addNode.execute(frame, self, o);
            return PNone.NONE;
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = __ROR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(other)", limit = "3")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.union(self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    @GenerateUncached
    public abstract static class UpdateSingleNode extends Node {

        public abstract HashingStorage execute(Frame frame, HashingStorage storage, Object other);

        @Specialization
        static HashingStorage update(HashingStorage storage, PHashingCollection other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage dictStorage = other.getDictStorage();
            return lib.addAllToOther(dictStorage, storage);
        }

        @Specialization
        static HashingStorage update(HashingStorage storage, PDictView.PDictKeysView other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage dictStorage = other.getWrappedDict().getDictStorage();
            return lib.addAllToOther(dictStorage, storage);
        }

        static boolean isBuiltinSequence(Object other, GetClassNode getClassNode) {
            return other instanceof PSequence && !(other instanceof PString) && getClassNode.execute((PSequence) other) instanceof PythonBuiltinClassType;
        }

        @Specialization(guards = "isBuiltinSequence(other, getClassNode)", limit = "1")
        static HashingStorage doBuiltin(VirtualFrame frame, HashingStorage storage, @SuppressWarnings("unused") PSequence other,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            SequenceStorage sequenceStorage = getSequenceStorageNode.execute(other);
            int length = lenNode.execute(sequenceStorage);
            HashingStorage curStorage = storage;
            for (int i = 0; i < length; i++) {
                Object key = getItemScalarNode.execute(sequenceStorage, i);
                curStorage = lib.setItemWithFrame(curStorage, key, PNone.NONE, hasFrame, frame);
            }
            return curStorage;
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isBuiltinSequence(other, getClassNode)"}, limit = "1")
        static HashingStorage doIterable(VirtualFrame frame, HashingStorage storage, Object other,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage curStorage = storage;
            Object iterator = getIter.execute(frame, other);
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return curStorage;
                }
                curStorage = lib.setItemWithFrame(curStorage, key, PNone.NONE, hasFrame, frame);
            }
        }

        public static UpdateSingleNode create() {
            return SetBuiltinsFactory.UpdateSingleNodeGen.create();
        }

        public static UpdateSingleNode getUncached() {
            return SetBuiltinsFactory.UpdateSingleNodeGen.getUncached();
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

        @Specialization(guards = "args.length == 1")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached UpdateSingleNode update) {
            self.setDictStorage(update.execute(frame, self.getDictStorage(), args[0]));
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.union(result, getHashingStorageNode.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.union(result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "3")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.union(self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @Builtin(name = __RAND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(right)", limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.intersectWithFrame(left.getDictStorage(), getHashingStorageNode.execute(frame, right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doAnd(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "intersection", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class IntersectNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)", limit = "3")
        PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.copy(self.getDictStorage()));
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.intersectWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(limit = "3")
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached ConditionProfile hasFrame,
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
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.intersectWithFrame(result, getHashingStorage.execute(frame, args[i]), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.intersectWithFrame(result, getHashingStorage.execute(frame, o), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "3")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.intersectWithFrame(self.getDictStorage(), getHashingStorage.execute(frame, other), hasFrame, frame);
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = __RXOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(other)", limit = "3")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(lib.xor(self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "symmetric_difference", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceNode extends PythonBuiltinNode {

        @Specialization(limit = "3")
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
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.xor(result, getHashingStorage.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.xor(result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "3")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage result = lib.xor(self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(right)", limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary("left.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(left.getDictStorage(), getHashingStorageNode.execute(frame, right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doSub(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
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
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = lib.diffWithFrame(result, getHashingStorageNode.execute(frame, args[i]), hasFrame, frame);
            }
            return factory().createSet(result);
        }

        @Specialization(limit = "3")
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached ConditionProfile hasFrame,
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
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = lib.diffWithFrame(result, getHashingStorage.execute(frame, args[i]), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached ConditionProfile hasFrame,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = lib.diffWithFrame(result, getHashingStorage.execute(frame, o), hasFrame, frame);
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(limit = "3")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached ConditionProfile hasFrame,
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
        @Specialization(limit = "3")
        Object remove(VirtualFrame frame, PSet self, Object key,
                        @Cached BranchProfile updatedStorage,
                        @Cached BaseSetBuiltins.ConvertKeyNode conv,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            Object checkedKey = conv.execute(key);
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
        @Specialization
        Object discard(VirtualFrame frame, PSet self, Object key,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode discardNode) {
            discardNode.execute(frame, self, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonUnaryBuiltinNode {

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

        @Specialization
        Object remove(VirtualFrame frame, PSet self,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            for (Object next : lib.keys(self.getDictStorage())) {
                removeItem(frame, self, next, lib, hasFrame, updatedStorage);
                return next;
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.POP_FROM_EMPTY_SET);
        }
    }

}
