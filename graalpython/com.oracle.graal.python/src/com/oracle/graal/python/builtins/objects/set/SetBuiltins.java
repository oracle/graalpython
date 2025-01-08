/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_ADD;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
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
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetSetStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageClear;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDiff;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIntersect;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStoragePop;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageXor;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * binary operations are implemented in {@link BaseSetBuiltins}
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PSet)
public final class SetBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SetBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class InitNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(iterable)")
        static PNone doNoValue(PSet self, @SuppressWarnings("unused") PNone iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(inliningTarget, self.getDictStorage());
            self.setDictStorage(newStorage);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(iterable)"})
        static PNone doGeneric(VirtualFrame frame, PSet self, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage storage = getHashingStorageNode.doNoValue(frame, inliningTarget, iterable);
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        @Fallback
        static PNone fail(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object self, Object iterable,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.SET_DOES_NOT_SUPPORT_ITERABLE_OBJ, iterable);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        static PSet doSet(PSet self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createSet(copyNode.execute(inliningTarget, self.getDictStorage()));
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static Object clear(PSet self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(inliningTarget, self.getDictStorage());
            self.setDictStorage(newStorage);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ADD, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static Object add(VirtualFrame frame, PSet self, Object o,
                        @Cached SetNodes.AddNode addNode) {
            addNode.execute(frame, self, o);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___IOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSet(VirtualFrame frame, PSet self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            addAllToOther.execute(frame, inliningTarget, other.getDictStorage(), self);
            return self;
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
        static PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = addAllToOther.execute(frame, inliningTarget, getSetStorageNode.execute(frame, inliningTarget, args[i]), result);
            }
            return factory.createSet(result);
        }

        @Specialization(replaces = "doCached")
        static PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = addAllToOther.execute(frame, inliningTarget, getSetStorageNode.execute(frame, inliningTarget, args[i]), result);
            }
            return factory.createSet(result);
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 108 -> 90
    public abstract static class UpdateSingleNode extends Node {

        public abstract void execute(VirtualFrame frame, PHashingCollection collection, Object other);

        @Specialization
        static void update(VirtualFrame frame, PHashingCollection collection, PHashingCollection other,
                        @Bind("this") Node inliningTarget,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage dictStorage = other.getDictStorage();
            addAllToOther.execute(frame, inliningTarget, dictStorage, collection);
        }

        @Specialization
        static void update(VirtualFrame frame, PHashingCollection collection, PDictView.PDictKeysView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage dictStorage = other.getWrappedStorage();
            addAllToOther.execute(frame, inliningTarget, dictStorage, collection);
        }

        @Idempotent
        static boolean isBuiltinSequence(Node inliningTarget, Object other, GetPythonObjectClassNode getClassNode) {
            return other instanceof PSequence && !(other instanceof PString) && getClassNode.execute(inliningTarget, (PSequence) other) instanceof PythonBuiltinClassType;
        }

        @Specialization(guards = "isBuiltinSequence(inliningTarget, other, getClassNode)", limit = "1")
        static void doBuiltin(VirtualFrame frame, PHashingCollection collection, @SuppressWarnings("unused") PSequence other,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetPythonObjectClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Exclusive @Cached HashingStorageSetItem setStorageItem) {
            SequenceStorage sequenceStorage = getSequenceStorageNode.execute(inliningTarget, other);
            int length = sequenceStorage.length();
            HashingStorage curStorage = collection.getDictStorage();
            for (int i = 0; i < length; i++) {
                Object key = getItemScalarNode.execute(inliningTarget, sequenceStorage, i);
                curStorage = setStorageItem.execute(frame, inliningTarget, curStorage, key, PNone.NONE);
            }
            collection.setDictStorage(curStorage);
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isBuiltinSequence(inliningTarget, other, getClassNode)"}, limit = "1")
        static void doIterable(VirtualFrame frame, PHashingCollection collection, Object other,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetPythonObjectClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached HashingStorageSetItem setStorageItem) {
            HashingStorage curStorage = collection.getDictStorage();
            Object iterator = getIter.execute(frame, inliningTarget, other);
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    collection.setDictStorage(curStorage);
                    return;
                }
                curStorage = setStorageItem.execute(frame, inliningTarget, curStorage, key, PNone.NONE);
            }
        }

        @NeverDefault
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
            update.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage storage = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                storage = addAllToOther.execute(frame, inliningTarget, getSetStorageNode.execute(frame, inliningTarget, args[i]), storage);
            }
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage storage = self.getDictStorage();
            for (Object o : args) {
                storage = addAllToOther.execute(frame, inliningTarget, getSetStorageNode.execute(frame, inliningTarget, o), storage);
            }
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther) {
            addAllToOther.execute(frame, inliningTarget, getSetStorageNode.execute(frame, inliningTarget, other), self);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___IAND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IAndNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PBaseSet right,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen lenNode,
                        @Cached InlinedConditionProfile swapProfile,
                        @Cached HashingStorageIntersect intersectNode) {
            HashingStorage storage1 = left.getDictStorage();
            HashingStorage storage2 = right.getDictStorage();
            // Try to minimize the number of __eq__ calls
            if (swapProfile.profile(inliningTarget, lenNode.execute(inliningTarget, storage2) > lenNode.execute(inliningTarget, storage1))) {
                HashingStorage tmp = storage1;
                storage1 = storage2;
                storage2 = tmp;
            }
            HashingStorage storage = intersectNode.execute(frame, inliningTarget, storage2, storage1);
            left.setDictStorage(storage);
            return left;
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

        @Specialization(guards = "isNoValue(other)")
        Object doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            return createResult(self, result, factory);
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        Object doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = intersectNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return createResult(self, result, factory);
        }

        @Specialization(replaces = "doCached")
        Object doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = intersectNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return createResult(self, result, factory);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            result = intersectNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, other));
            return createResult(self, result, factory);
        }

        protected Object createResult(PSet self, HashingStorage result, PythonObjectFactory factory) {
            return factory.createSet(result);
        }
    }

    @Builtin(name = "intersection_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    @ImportStatic({SpecialMethodNames.class})
    public abstract static class IntersectUpdateNode extends IntersectNode {
        @Override
        protected Object createResult(PSet self, HashingStorage result, PythonObjectFactory factory) {
            // In order to be compatible w.r.t. __eq__ calls we cannot reuse self storage
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___IXOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IXorNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doSet(VirtualFrame frame, PSet self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageXor xorNode) {
            self.setDictStorage(xorNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage()));
            return self;
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

        @Specialization
        static PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached GetSetStorageNode getHashingStorage,
                        @Cached HashingStorageXor xorNode,
                        @Cached PythonObjectFactory factory) {
            HashingStorage result = xorNode.execute(frame, inliningTarget, self.getDictStorage(), getHashingStorage.execute(frame, inliningTarget, other));
            return factory.createSet(result);
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
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageXor xorNode) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = xorNode.execute(frame, inliningTarget, result, getHashingStorage.execute(frame, inliningTarget, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSetArgs(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageXor xorNode) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = xorNode.execute(frame, inliningTarget, result, getHashingStorage.execute(frame, inliningTarget, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PNone doSetOther(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageXor xorNode) {
            HashingStorage result = xorNode.execute(frame, inliningTarget, self.getDictStorage(), getHashingStorage.execute(frame, inliningTarget, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___ISUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ISubNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PBaseSet right,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left.getDictStorage(), right.getDictStorage());
            left.setDictStorage(storage);
            return left;
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
        static PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = diffNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return factory.createSet(result);
        }

        @Specialization(replaces = "doCached")
        static PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = diffNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return factory.createSet(result);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = diffNode.execute(frame, inliningTarget, self.getDictStorage(), getSetStorageNode.execute(frame, inliningTarget, other));
            return factory.createSet(result);
        }
    }

    @Builtin(name = "difference_update", minNumOfPositionalArgs = 1, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class DifferenceUpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doNone(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageDiff diffNode) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = diffNode.execute(frame, inliningTarget, result, getHashingStorage.execute(frame, inliningTarget, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageDiff diffNode) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = diffNode.execute(frame, inliningTarget, result, getHashingStorage.execute(frame, inliningTarget, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageDiff diffNode) {
            HashingStorage result = diffNode.execute(frame, inliningTarget, self.getDictStorage(), getHashingStorage.execute(frame, inliningTarget, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object remove(VirtualFrame frame, PSet self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode discardNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!discardNode.execute(frame, self, key)) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.KeyError, new Object[]{key});
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "discard", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DiscardNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object discard(VirtualFrame frame, PSet self, Object key,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode discardNode) {
            discardNode.execute(frame, self, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object remove(PSet self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStoragePop popNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object[] result = popNode.execute(inliningTarget, self.getDictStorage(), self);
            if (result != null) {
                return result[0];
            }
            throw raiseNode.get(inliningTarget).raise(PythonErrorType.KeyError, ErrorMessages.POP_FROM_EMPTY_SET);
        }
    }

}
