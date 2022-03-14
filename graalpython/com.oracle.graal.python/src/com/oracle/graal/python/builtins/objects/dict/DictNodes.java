package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class DictNodes {
    public abstract static class UpdateNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PDict self, Object other);

        @SuppressWarnings("unused")
        @Specialization(guards = "isIdentical(self, other)")
        static void updateSelf(VirtualFrame frame, PDict self, Object other) {
        }

        @Specialization(guards = "isDictButNotEconomicMap(other)")
        static void updateDict(PDict self, Object other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage storage = lib.addAllToOther(((PDict) other).getDictStorage(), self.getDictStorage());
            self.setDictStorage(storage);
        }

        @Specialization(guards = "isDictEconomicMap(other)")
        static void updateDict(PDict self, Object other,
                        @CachedLibrary(limit = "2") HashingStorageLibrary libSelf,
                        @CachedLibrary(limit = "1") HashingStorageLibrary libOther,
                        @Cached PRaiseNode raiseNode) {
            HashingStorage selfStorage = self.getDictStorage();
            HashingStorage otherStorage = ((PDict) other).getDictStorage();
            HashingStorageLibrary.HashingStorageIterator<HashingStorage.DictEntry> itOther = libOther.entries(otherStorage).iterator();
            int initialSize = libOther.length(otherStorage);
            while (itOther.hasNext()) {
                HashingStorage.DictEntry next = itOther.next();
                selfStorage = libSelf.setItem(selfStorage, next.key, next.value);
                if (initialSize != libOther.length(otherStorage)) {
                    throw raiseNode.raise(RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            self.setDictStorage(selfStorage);
        }

        @Specialization(guards = {"!isDict(other)", "hasKeysAttr(frame, other, lookupKeys)"}, limit = "1")

        static void updateMapping(VirtualFrame frame, PDict self, Object other,
                        @SuppressWarnings("unused") @Shared("lookupKeys") @Cached PyObjectLookupAttr lookupKeys,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            HashingStorage storage = HashingStorage.copyToStorage(frame, other, PKeyword.EMPTY_KEYWORDS, self.getDictStorage(),
                            callKeysNode, getItem, getIter, nextNode, errorProfile, lib);
            self.setDictStorage(storage);
        }

        @Specialization(guards = {"!isDict(other)", "!hasKeysAttr(frame, other, lookupKeys)"}, limit = "1")
        static void updateSequence(VirtualFrame frame, PDict self, Object other,
                        @SuppressWarnings("unused") @Shared("lookupKeys") @Cached PyObjectLookupAttr lookupKeys,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PRaiseNode raise,
                        @Cached GetNextNode nextNode,
                        @Cached ListNodes.FastConstructListNode createListNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached ConditionProfile lengthTwoProfile,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached IsBuiltinClassProfile isTypeErrorProfile) {
            HashingStorage.StorageSupplier storageSupplier = (boolean isStringKey, int length) -> self.getDictStorage();
            HashingStorage storage = HashingStorage.addSequenceToStorage(frame, other, PKeyword.EMPTY_KEYWORDS, storageSupplier,
                            getIter, nextNode, createListNode, seqLenNode, lengthTwoProfile, raise, getItem, isTypeErrorProfile, errorProfile, lib);
            self.setDictStorage(storage);
        }

        protected static boolean isIdentical(PDict dict, Object other) {
            return dict == other;
        }

        protected static boolean isDictEconomicMap(Object other) {
            return other instanceof PDict && ((PDict) other).getDictStorage() instanceof EconomicMapStorage;
        }

        protected static boolean isDictButNotEconomicMap(Object other) {
            return other instanceof PDict && !(((PDict) other).getDictStorage() instanceof EconomicMapStorage);
        }

        protected static boolean hasKeysAttr(VirtualFrame frame, Object other, PyObjectLookupAttr lookupKeys) {
            return lookupKeys.execute(frame, other, SpecialMethodNames.KEYS) != PNone.NO_VALUE;
        }

        public static UpdateNode create() {
            return DictNodesFactory.UpdateNodeGen.create();
        }
    }
}
