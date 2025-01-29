/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
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
import com.oracle.graal.python.builtins.objects.common.ForeignHashingStorage;
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
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyDictCheckNode;
import com.oracle.graal.python.lib.PyDictSetDefault;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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

/**
 * NOTE: self can either be a PDict or a foreign dict (hasHashEntries()).
 * {@link DictNodes.GetDictStorageNode} should be used to get the {@link HashingStorage} and to get
 * a proper error and not allow other objects as arguments.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PDict)
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
        Object doVarargs(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Shared @Cached DictNodes.UpdateDictStorageNode updateDictStorageNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            HashingStorage add = getInitNode().execute(frame, args[0], kwargs);
            var storage = getStorageNode.execute(inliningTarget, self);
            var newStorage = addAllToOtherNode.execute(frame, inliningTarget, add, storage);
            updateDictStorageNode.execute(inliningTarget, self, storage, newStorage);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        Object doKeywords(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Shared @Cached DictNodes.UpdateDictStorageNode updateDictStorageNode,
                        @Shared @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            HashingStorage add = getInitNode().execute(frame, NO_VALUE, kwargs);
            var storage = getStorageNode.execute(inliningTarget, self);
            var newStorage = addAllToOtherNode.execute(frame, inliningTarget, add, storage);
            updateDictStorageNode.execute(inliningTarget, self, storage, newStorage);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object doEmpty(Object self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "dict", 1, args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @ArgumentClinic(name = "default", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    abstract static class SetDefaultNode extends PythonTernaryClinicBuiltinNode {

        @Specialization
        Object doIt(VirtualFrame frame, Object dict, Object key, Object defaultValue,
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
        static Object popDefault(VirtualFrame frame, Object dict, Object key, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached InlinedConditionProfile hasKeyProfile,
                        @Cached HashingStorageDelItem delItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var storage = getStorageNode.execute(inliningTarget, dict);
            Object retVal = delItem.executePop(frame, inliningTarget, storage, key, dict);
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
        static Object popItem(Object dict,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStoragePop popNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var storage = getStorageNode.execute(inliningTarget, dict);
            Object[] result = popNode.execute(inliningTarget, storage, dict);
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
        static PDictView keys(PDict self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictKeysView(self);
        }

        @Fallback
        static PDictView foreign(Object self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictKeysView(self, new ForeignHashingStorage(self));
        }
    }

    // items()
    @Builtin(name = J_ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDictView items(PDict self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictItemsView(self);
        }

        @Fallback
        static PDictView foreign(Object self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictItemsView(self, new ForeignHashingStorage(self));
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doWithDefault(VirtualFrame frame, Object self, Object key, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageGetItem getItem) {
            var storage = getStorageNode.execute(inliningTarget, self);
            final Object value = getItem.execute(frame, inliningTarget, storage, key);
            return value != null ? value : (defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Child private DispatchMissingNode missing;

        @Specialization
        Object getItem(VirtualFrame frame, Object self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached InlinedConditionProfile notFoundProfile,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PRaiseNode raiseNode) {
            var storage = getStorageNode.execute(inliningTarget, self);
            final Object result = getItem.execute(frame, inliningTarget, storage, key);
            if (notFoundProfile.profile(inliningTarget, result == null)) {
                return handleMissing(frame, self, key, raiseNode);
            }
            return result;
        }

        @InliningCutoff
        private Object handleMissing(VirtualFrame frame, Object self, Object key, PRaiseNode raiseNode) {
            if (self instanceof PDict dict && !PGuards.isBuiltinDict(dict)) {
                if (missing == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    missing = insert(DispatchMissingNodeGen.create());
                }
                return missing.execute(frame, dict, key);
            }
            throw raiseNode.raise(KeyError, new Object[]{key});
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

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends MpAssSubscriptBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void run(VirtualFrame frame, Object self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, inliningTarget, self, key, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static void run(VirtualFrame frame, Object self, Object key, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageDelItem delItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var storage = getStorageNode.execute(inliningTarget, self);
            if (!delItem.execute(frame, inliningTarget, storage, key, self)) {
                throw raiseNode.get(inliningTarget).raise(KeyError, new Object[]{key});
            }
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached PythonObjectFactory factory) {
            var storage = getStorageNode.execute(inliningTarget, self);
            return factory.createDictKeyIterator(getIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Cached PythonObjectFactory factory) {
            var storage = getStorageNode.execute(inliningTarget, self);
            return factory.createDictKeyIterator(getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HashingStorageEq eqNode) {
            return eqNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        static Object doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictCheckNode isDictNode,
                        @Exclusive @Cached HashingStorageEq eqNode,
                        @Cached DictNodes.GetDictStorageNode getStorageNode) {
            if (isDictNode.execute(inliningTarget, other)) {
                var selfStorage = getStorageNode.execute(inliningTarget, self);
                var otherStorage = getStorageNode.execute(inliningTarget, other);
                return eqNode.execute(frame, inliningTarget, selfStorage, otherStorage);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean run(VirtualFrame frame, Object self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageGetItem getItem) {
            var storage = getStorageNode.execute(inliningTarget, self);
            return getItem.hasKey(frame, inliningTarget, storage, key);
        }
    }

    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageLen lenNode) {
            var storage = getStorageNode.execute(inliningTarget, self);
            return lenNode.execute(inliningTarget, storage);
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDict copy(Object dict,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached PythonObjectFactory factory) {
            var storage = getStorageNode.execute(inliningTarget, dict);
            return factory.createDict(copyNode.execute(inliningTarget, storage));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(Object dict,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached DictNodes.UpdateDictStorageNode updateStorageNode,
                        @Cached HashingStorageClear clearNode) {
            var storage = getStorageNode.execute(inliningTarget, dict);
            HashingStorage newStorage = clearNode.execute(inliningTarget, storage);
            updateStorageNode.execute(inliningTarget, dict, storage, newStorage);
            return PNone.NONE;
        }
    }

    // values()
    @Builtin(name = J_VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDictView values(PDict self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictValuesView(self);
        }

        @Fallback
        static PDictView foreign(Object self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createDictValuesView(self, new ForeignHashingStorage(self));
        }
    }

    // update()
    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object updateEmpy(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length == 0"})
        static Object update(VirtualFrame frame, Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length <= 1", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached DictNodes.UpdateDictStorageNode updateDictStorageNode,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode,
                        @Cached HashingStorage.InitNode initNode,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            if (args.length > 0) {
                updateNode.execute(frame, self, args[0]);
            }
            HashingStorage kwargsStorage = initNode.execute(frame, NO_VALUE, kwargs);
            var storage = getStorageNode.execute(inliningTarget, self);
            var newStorage = addAllToOtherNode.execute(frame, inliningTarget, kwargsStorage, storage);
            updateDictStorageNode.execute(inliningTarget, self, storage, newStorage);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        @SuppressWarnings("unused")
        static Object error(Object self, Object[] args, PKeyword[] kwargs,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "update", 1, args.length);
        }

    }

    // fromkeys()
    @Builtin(name = "fromkeys", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "iterable", "value"}, isClassmethod = true)
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
                        @Cached PyObjectSetItem setItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            Object dict = callCtor.execute(frame, cls);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            Object it = getIter.execute(frame, inliningTarget, iterable);
            while (true) {
                try {
                    Object key = nextNode.execute(frame, it);
                    setItem.execute(frame, inliningTarget, dict, key, val);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                }
            }
            return dict;
        }

        protected static boolean isBuiltinDict(Node inliningTarget, Object cls, IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PDict, cls);
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @ImportStatic(PythonBuiltinClassType.class)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization(guards = {"isDictNode.execute(inliningTarget, self)", "isDictNode.execute(inliningTarget, other)"}, limit = "1")
        static PDict or(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictCheckNode isDictNode,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached DictNodes.UpdateNode updateNode,
                        @Cached PythonObjectFactory factory) {
            var storage = getStorageNode.execute(inliningTarget, self);
            PDict merged = factory.createDict(copyNode.execute(inliningTarget, storage));
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
        Object or(VirtualFrame frame, Object self, Object other,
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
