/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageClear;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageEq;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetReverseIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStoragePop;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltinsFactory.DispatchMissingNodeGen;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyDictSetDefault;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDefaultDict})
public final class DictBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DictBuiltinsSlotsGen.SLOTS;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private HashingStorage.InitNode initNode;

        private HashingStorage.InitNode getInitNode() {
            if (initNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initNode = insert(HashingStorage.InitNode.create());
            }
            return initNode;
        }

        @Specialization(guards = {"args.length == 1"})
        Object doVarargs(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, inliningTarget, getInitNode().execute(frame, args[0], kwargs), self);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        Object doKeywords(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, inliningTarget, getInitNode().execute(frame, NO_VALUE, kwargs), self);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object doEmpty(PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "dict", 1, args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @ArgumentClinic(name = "default", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    abstract static class SetDefaultNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        Object doIt(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictSetDefault setDefault) {
            return setDefault.execute(frame, inliningTarget, dict, key, defaultValue);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DictBuiltinsClinicProviders.SetDefaultNodeClinicProviderGen.INSTANCE;
        }
    }

    // pop(key[, default])
    @Builtin(name = "pop", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasKeyProfile,
                        @Cached HashingStorageDelItem delItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object retVal = delItem.executePop(frame, inliningTarget, dict.getDictStorage(), key, dict);
            if (hasKeyProfile.profile(inliningTarget, retVal != null)) {
                return retVal;
            } else {
                if (PGuards.isNoValue(defaultValue)) {
                    throw raiseNode.get(inliningTarget).raise(KeyError, new Object[]{key});
                } else {
                    return defaultValue;
                }
            }
        }
    }

    // popitem()
    @Builtin(name = "popitem", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopItemNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object popItem(PDict dict,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStoragePop popNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object[] result = popNode.execute(inliningTarget, dict.getDictStorage(), dict);
            if (result == null) {
                throw raiseNode.get(inliningTarget).raise(KeyError, ErrorMessages.IS_EMPTY, "popitem(): dictionary");
            }
            return factory.createTuple(result);
        }
    }

    // keys()
    @Builtin(name = J_KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {

        @Specialization
        PDictView keys(PDict self,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictKeysView(self);
        }
    }

    // items()
    @Builtin(name = J_ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDictView items(PDict self,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictItemsView(self);
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            final Object value = getItem.execute(frame, inliningTarget, self.getDictStorage(), key);
            return value != null ? value : (defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private DispatchMissingNode missing;

        @Specialization
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            final Object result = getItem.execute(frame, inliningTarget, self.getDictStorage(), key);
            if (result == null) {
                if (missing == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    missing = insert(DispatchMissingNodeGen.create());
                }
                return missing.execute(frame, self, key);
            }
            return result;
        }
    }

    @ImportStatic(SpecialMethodSlot.class)
    @GenerateInline(false)      // not inlining this node since GetItemNode prefers lazy creation
    protected abstract static class DispatchMissingNode extends Node {

        protected abstract Object execute(VirtualFrame frame, Object self, Object key);

        @Specialization
        static Object missing(VirtualFrame frame, Object self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Missing)") LookupAndCallBinaryNode callMissing,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object result = callMissing.executeObject(frame, self, key);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw raiseNode.get(inliningTarget).raise(KeyError, new Object[]{key});
            }
            return result;
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PDict self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, inliningTarget, self, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PDict self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageDelItem delItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object found = delItem.executePop(frame, inliningTarget, self.getDictStorage(), key, self);
            if (found != null) {
                return PNone.NONE;
            }
            throw raiseNode.get(inliningTarget).raise(KeyError, new Object[]{key});
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(@SuppressWarnings("unused") PDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached PythonObjectFactory factory) {
            HashingStorage dictStorage = self.getDictStorage();
            return factory.createDictKeyIterator(getIterator.execute(inliningTarget, dictStorage), dictStorage, lenNode.execute(inliningTarget, dictStorage));
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(PDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Cached PythonObjectFactory factory) {
            HashingStorage storage = self.getDictStorage();
            return factory.createDictKeyIterator(getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageEq eqNode) {
            return eqNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean run(VirtualFrame frame, PDict self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem) {
            return getItem.hasKey(frame, inliningTarget, self.getDictStorage(), key);
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(PDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, self.getDictStorage());
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDict dict,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createDict(copyNode.execute(inliningTarget, dict.getDictStorage()));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(PDict dict,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(inliningTarget, dict.getDictStorage());
            dict.setDictStorage(newStorage);
            return PNone.NONE;
        }
    }

    // values()
    @Builtin(name = J_VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDictView values(PDict self,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictValuesView(self);
        }
    }

    // update()
    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object updateEmpy(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length == 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            updateKwargs(frame, inliningTarget, self, kwargs, initNode, addAllToOtherNode);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            updateNode.execute(frame, self, args[0]);
            updateKwargs(frame, inliningTarget, self, kwargs, initNode, addAllToOtherNode);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        @SuppressWarnings("unused")
        static Object error(PDict self, Object[] args, PKeyword[] kwargs,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "update", 1, args.length);
        }

        private static void updateKwargs(VirtualFrame frame, Node inliningTarget, PDict self, PKeyword[] kwargs, HashingStorage.InitNode initNode, HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, inliningTarget, initNode.execute(frame, PNone.NO_VALUE, kwargs), self);
        }
    }

    // fromkeys()
    @Builtin(name = "fromkeys", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "iterable", "value"}, isClassmethod = true)
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateNodeFactory
    public abstract static class FromKeysNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isBuiltinDict(inliningTarget, cls, isSameTypeNode)", limit = "1")
        static Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsSameTypeNode isSameTypeNode,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode,
                        @Cached PythonObjectFactory factory) {
            HashingStorage s = getHashingStorageNode.execute(frame, inliningTarget, iterable, value);
            return factory.createDict(cls, s);
        }

        @Fallback
        static Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callCtor,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "SetItem") LookupSpecialMethodSlotNode lookupSetItem,
                        @Cached CallTernaryMethodNode callSetItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object dict = callCtor.execute(frame, cls);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            Object it = getIter.execute(frame, inliningTarget, iterable);
            Object setitemMethod = lookupSetItem.execute(frame, getClassNode.execute(inliningTarget, dict), dict);
            if (setitemMethod != PNone.NO_VALUE) {
                while (true) {
                    try {
                        Object key = nextNode.execute(frame, it);
                        callSetItem.execute(frame, setitemMethod, dict, key, val);
                    } catch (PException e) {
                        e.expectStopIteration(inliningTarget, errorProfile);
                        break;
                    }
                }
                return dict;
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, iterable);
            }
        }

        protected static boolean isBuiltinDict(Node inliningTarget, Object cls, IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PDict, cls);
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PDict or(VirtualFrame frame, PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Cached DictNodes.UpdateNode updateNode,
                        @Cached PythonObjectFactory factory) {
            PDict merged = factory.createDict(copyNode.execute(inliningTarget, self.getDictStorage()));
            updateNode.execute(frame, merged, other);
            return merged;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object or(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        PDict or(VirtualFrame frame, PDict self, Object other,
                        @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, other);
            return self;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
