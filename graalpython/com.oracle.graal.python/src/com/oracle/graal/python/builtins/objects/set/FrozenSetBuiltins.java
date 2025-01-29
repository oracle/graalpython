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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetSetStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDiff;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIntersect;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageXor;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsAnyBuiltinObjectProfile;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

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

        @Specialization
        static PFrozenSet subFrozensetIdentity(PFrozenSet arg,
                        @Bind("this") Node inliningTarget,
                        @Cached IsAnyBuiltinObjectProfile isBuiltinClass,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (isBuiltinClass.profileIsAnyBuiltinObject(inliningTarget, arg)) {
                return arg;
            } else {
                return factory.get(inliningTarget).createFrozenSet(arg.getDictStorage());
            }
        }
    }

    @Builtin(name = "intersection", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class IntersectNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        static PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, @SuppressWarnings("unused") PNone other,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createFrozenSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PBaseSet doCached(VirtualFrame frame, PFrozenSet self, Object[] args,
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
            return factory.createFrozenSet(result);
        }

        @Specialization(replaces = "doCached")
        static PBaseSet doGeneric(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = intersectNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return factory.createFrozenSet(result);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = intersectNode.execute(frame, inliningTarget, self.getDictStorage(), getSetStorageNode.execute(frame, inliningTarget, other));
            return factory.createFrozenSet(result);
        }
    }

    @Builtin(name = "symmetric_difference", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceNode extends PythonBuiltinNode {

        @Specialization
        static PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.GetSetStorageNode getHashingStorage,
                        @Cached HashingStorageXor xorNode,
                        @Cached PythonObjectFactory factory) {
            HashingStorage result = xorNode.execute(frame, inliningTarget, self.getDictStorage(), getHashingStorage.execute(frame, inliningTarget, other));
            return factory.createFrozenSet(result);
        }
    }

    @Builtin(name = "difference", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DifferenceNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        static PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, @SuppressWarnings("unused") PNone other,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createFrozenSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PBaseSet doCached(VirtualFrame frame, PFrozenSet self, Object[] args,
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
            return factory.createFrozenSet(result);
        }

        @Specialization(replaces = "doCached")
        static PBaseSet doGeneric(VirtualFrame frame, PFrozenSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = diffNode.execute(frame, inliningTarget, result, getSetStorageNode.execute(frame, inliningTarget, args[i]));
            }
            return factory.createFrozenSet(result);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PFrozenSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getSetStorageNode,
                        @Shared @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = diffNode.execute(frame, inliningTarget, self.getDictStorage(), getSetStorageNode.execute(frame, inliningTarget, other));
            return factory.createFrozenSet(result);
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PBaseSet doCached(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int len,
                        @Shared @Cached GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = addAllToOther.execute(frame, inliningTarget, getHashingStorage.execute(frame, inliningTarget, args[i]), result);
            }
            return factory.createFrozenSet(result);
        }

        @Specialization(replaces = "doCached")
        static PBaseSet doGeneric(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetSetStorageNode getHashingStorage,
                        @Shared @Cached HashingStorageCopy copyNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage result = copyNode.execute(inliningTarget, self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = addAllToOther.execute(frame, inliningTarget, getHashingStorage.execute(frame, inliningTarget, args[i]), result);
            }
            return factory.createFrozenSet(result);
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        public static long getHash(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"})
        public static long computeHash(VirtualFrame frame, PFrozenSet self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached PyObjectHashNode hashNode) {
            // adapted from https://github.com/python/cpython/blob/master/Objects/setobject.c#L758
            HashingStorage storage = self.getDictStorage();
            long m1 = 0x72e8ef4d;
            long m2 = 0x10dcd;
            long c1 = 0x3611c3e3;
            long c2 = 0x2338c7c1;
            long hash = 0;

            int len = 0;
            HashingStorageIterator it = getIter.execute(inliningTarget, storage);
            while (iterNext.execute(inliningTarget, storage, it)) {
                len++;
                Object key = iterKey.execute(inliningTarget, storage, it);
                long tmp = hashNode.execute(frame, inliningTarget, key);
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
