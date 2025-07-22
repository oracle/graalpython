/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.ordereddict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SIZEOF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap;
import com.oracle.graal.python.builtins.objects.dict.DictReprBuiltin.ReprOrderedDictItemsNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ordereddict.POrderedDict.ODictNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetStateNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.POrderedDict)
public class OrderedDictBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = OrderedDictBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OrderedDictBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        static PNone update(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached UpdateFromArgsNode update,
                        @Cached PRaiseNode raiseNode) {
            Object mapping = PNone.NO_VALUE;
            if (args.length == 1) {
                mapping = args[0];
            } else if (args.length > 1) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, 1, args.length);
            }
            update.execute(frame, inliningTarget, self, mapping, kwargs);
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends MpAssSubscriptBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void setitem(VirtualFrame frame, POrderedDict self, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectHashNode hashNode,
                        @Cached HashingStorageNodes.HashingStorageSetItemWithHash setItemWithHash,
                        @Cached InlinedBranchProfile storageUpdated,
                        @Cached ObjectHashMap.GetNode getNode,
                        @Cached ObjectHashMap.PutNode putNode) {
            long hash = hashNode.execute(frame, inliningTarget, key);
            HashingStorage newStorage = setItemWithHash.execute(frame, inliningTarget, self.getDictStorage(), key, hash, value);
            if (newStorage != self.getDictStorage()) {
                storageUpdated.enter(inliningTarget);
                self.setDictStorage(newStorage);
            }
            if (getNode.execute(frame, inliningTarget, self.nodes, key, hash) == null) {
                ODictNode node = new ODictNode(key, hash, self.last, null);
                self.append(node);
                putNode.put(frame, inliningTarget, self.nodes, key, hash, node);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static void delitem(VirtualFrame frame, POrderedDict self, Object key, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectHashNode hashNode,
                        @Cached HashingStorageNodes.HashingStorageDelItem delItem,
                        @Cached ObjectHashMap.RemoveNode removeNode,
                        @Cached PRaiseNode raiseNode) {
            long hash = hashNode.execute(frame, inliningTarget, key);
            ODictNode node = (ODictNode) removeNode.execute(frame, inliningTarget, self.nodes, key, hash);
            if (node == null) {
                throw raiseNode.raise(inliningTarget, KeyError, new Object[]{key});
            }
            self.remove(node);
            // TODO with hash
            delItem.execute(frame, inliningTarget, self.getDictStorage(), key, self);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class UpdateFromArgsNode extends Node {
        final void execute(VirtualFrame frame, Node inliningTarget, Object self, Object mapping) {
            execute(frame, inliningTarget, self, mapping, PKeyword.EMPTY_KEYWORDS);
        }

        abstract void execute(VirtualFrame frame, Node inliningTarget, Object self, Object mapping, PKeyword[] kwargs);

        @GenerateInline(false)
        abstract static class ForEachNode extends HashingStorageNodes.HashingStorageForEachCallback<Object> {

            @Override
            public abstract Object execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageNodes.HashingStorageIterator it, Object dict);

            @Specialization
            static Object each(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageNodes.HashingStorageIterator it, Object dict,
                            @Bind Node inliningTarget,
                            @Cached HashingStorageNodes.HashingStorageIteratorKey itKey,
                            @Cached HashingStorageNodes.HashingStorageIteratorValue itValue,
                            @Cached PyObjectSetItem setItem) {
                Object key = itKey.execute(inliningTarget, storage, it);
                Object value = itValue.execute(inliningTarget, storage, it);
                setItem.execute(frame, inliningTarget, dict, key, value);
                return dict;
            }
        }

        @Specialization
        static void update(VirtualFrame frame, Node inliningTarget, Object self, Object mapping, PKeyword[] kwargs,
                        @Cached(inline = false) HashingStorage.InitNode initNode,
                        @Cached HashingStorageNodes.HashingStorageForEach forEach,
                        @Cached(inline = false) ForEachNode callbackNode) {
            // Utilize the fact that normal dict is also ordered
            HashingStorage newStorage = initNode.execute(frame, mapping, kwargs);
            forEach.execute(frame, inliningTarget, newStorage, callbackNode, self);
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object or(VirtualFrame frame, POrderedDict left, PDict right,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached CallNode callNode,
                        @Shared @Cached UpdateFromArgsNode update) {
            Object newDict = callNode.execute(frame, getClassNode.execute(inliningTarget, left), left);
            update.execute(frame, inliningTarget, newDict, right);
            return newDict;
        }

        @Specialization
        static Object or(VirtualFrame frame, PDict left, POrderedDict right,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached CallNode callNode,
                        @Shared @Cached UpdateFromArgsNode update) {
            Object newDict = callNode.execute(frame, getClassNode.execute(inliningTarget, right), left);
            update.execute(frame, inliningTarget, newDict, right);
            return newDict;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object or(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_inplace_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object or(VirtualFrame frame, POrderedDict self, Object other,
                        @Bind Node inliningTarget,
                        @Cached UpdateFromArgsNode update) {
            update.execute(frame, inliningTarget, self, other);
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(VirtualFrame frame, POrderedDict self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetStateNode getStateNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectGetIter getIter,
                        @Bind PythonLanguage language) {
            Object type = getClassNode.execute(inliningTarget, self);
            Object state = getStateNode.execute(frame, inliningTarget, self);
            Object args = PFactory.createEmptyTuple(language);
            // Might be overridden
            Object items = callMethod.execute(frame, inliningTarget, self, T_ITEMS);
            Object itemsIter = getIter.execute(frame, inliningTarget, items);
            return PFactory.createTuple(language, new Object[]{type, args, state, PNone.NONE, itemsIter});
        }
    }

    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"$self", "key", "default"})
    @ArgumentClinic(name = "default", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    abstract static class SetDefaultNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object setdefault(VirtualFrame frame, POrderedDict self, Object key, Object defaultValue,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectSetItem setItem) {
            // Defensive implementation because the sequence methods might be overridden
            if (containsNode.execute(frame, inliningTarget, self, key)) {
                return getItem.execute(frame, inliningTarget, self, key);
            } else {
                setItem.execute(frame, inliningTarget, self, key, defaultValue);
                return defaultValue;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return OrderedDictBuiltinsClinicProviders.SetDefaultNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 2, parameterNames = {"$self", "key", "default"})
    @GenerateNodeFactory
    abstract static class PopNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object pop(VirtualFrame frame, POrderedDict self, Object key, Object defaultValue,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectDelItem delItem,
                        @Cached PRaiseNode raiseNode) {
            // XXX the CPython implementation is weird when self is a subclass
            if (containsNode.execute(frame, inliningTarget, self, key)) {
                Object value = getItem.execute(frame, inliningTarget, self, key);
                delItem.execute(frame, inliningTarget, self, key);
                return value;
            } else if (defaultValue != PNone.NO_VALUE) {
                return defaultValue;
            } else {
                throw raiseNode.raise(inliningTarget, KeyError, new Object[]{key});
            }
        }
    }

    @Builtin(name = "popitem", minNumOfPositionalArgs = 1, parameterNames = {"$self", "last"})
    @ArgumentClinic(name = "last", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class PopItemNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object popitem(VirtualFrame frame, POrderedDict self, boolean last,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageDelItem delItem,
                        @Cached ObjectHashMap.RemoveNode removeNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raise) {
            ODictNode node = last ? self.last : self.first;
            if (node == null) {
                throw raise.raise(inliningTarget, KeyError, ErrorMessages.IS_EMPTY, "dictionary");
            }
            self.remove(node);
            removeNode.execute(frame, inliningTarget, self.nodes, node.key, node.hash);
            // TODO with hash
            Object value = delItem.executePop(frame, inliningTarget, self.getDictStorage(), node.key, self);
            return PFactory.createTuple(language, new Object[]{node.key, value});
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return OrderedDictBuiltinsClinicProviders.PopItemNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object copy(VirtualFrame frame, POrderedDict self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached CallNode callNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            return callNode.execute(frame, type, self);
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone clear(POrderedDict self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageNodes.HashingStorageClear clearNode) {
            HashingStorage storage = clearNode.execute(inliningTarget, self.getDictStorage());
            self.setDictStorage(storage);
            self.nodes.clear();
            self.first = self.last = null;
            return PNone.NONE;
        }
    }

    @Builtin(name = "move_to_end", minNumOfPositionalArgs = 2, parameterNames = {"$self", "key", "last"})
    @ArgumentClinic(name = "last", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class MoveToEndNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static PNone move(VirtualFrame frame, POrderedDict self, Object key, boolean last,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.GetNode getNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.first == null) {
                // Empty
                throw raiseNode.raise(inliningTarget, KeyError, new Object[]{key});
            }
            if ((last ? self.last : self.first).key != key) {
                long hash = hashNode.execute(frame, inliningTarget, key);
                ODictNode node = (ODictNode) getNode.execute(frame, inliningTarget, self.nodes, key, hash);
                if (node == null) {
                    throw raiseNode.raise(inliningTarget, KeyError, new Object[]{key});
                }
                if (last) {
                    if (self.last != node) {
                        self.remove(node);
                        self.append(node);
                    }
                } else {
                    if (self.first != node) {
                        self.remove(node);
                        self.prepend(node);
                    }
                }
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return OrderedDictBuiltinsClinicProviders.MoveToEndNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class KeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object keys(POrderedDict self,
                        @Bind PythonLanguage language) {
            return PFactory.createOrderedDictKeys(language, self);
        }
    }

    @Builtin(name = J_VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object values(POrderedDict self,
                        @Bind PythonLanguage language) {
            return PFactory.createOrderedDictValues(language, self);
        }
    }

    @Builtin(name = J_ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ItemsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object items(POrderedDict self,
                        @Bind PythonLanguage language) {
            return PFactory.createOrderedDictItems(language, self);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(POrderedDict self,
                        @Bind PythonLanguage language) {
            return PFactory.createOrderedDictIterator(language, self, POrderedDictIterator.IteratorType.KEYS, false);
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(POrderedDict self,
                        @Bind PythonLanguage language) {
            return PFactory.createOrderedDictIterator(language, self, POrderedDictIterator.IteratorType.KEYS, true);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object repr(VirtualFrame frame, POrderedDict self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached ReprOrderedDictItemsNode reprDictItems) {
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self));
            if (self.first == null) {
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(builder, typeName);
                appendStringNode.execute(builder, T_EMPTY_PARENS);
                return toStringNode.execute(builder);
            }
            PythonContext context = PythonContext.get(inliningTarget);
            if (!context.reprEnter(self)) {
                return T_ELLIPSIS;
            }
            try {
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(builder, typeName);
                appendStringNode.execute(builder, T_LPAREN);
                appendStringNode.execute(builder, T_LBRACE);
                reprDictItems.execute(frame, self, builder);
                appendStringNode.execute(builder, T_RBRACE);
                appendStringNode.execute(builder, T_RPAREN);
                return toStringNode.execute(builder);
            } finally {
                context.reprLeave(self);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class EqHelperNode extends Node {
        abstract boolean execute(VirtualFrame frame, Node inliningTarget, POrderedDict self, PDict other);

        @Specialization
        static boolean cmp(VirtualFrame frame, Node inliningTarget, POrderedDict self, POrderedDict other,
                        @Cached HashingStorageNodes.HashingStorageGetItemWithHash getItem,
                        @Cached PyObjectRichCompareBool eqNode) {
            ODictNode lnode = self.first;
            ODictNode rnode = other.first;
            do {
                if (lnode == null && rnode == null) {
                    return true;
                }
                if (lnode == null || rnode == null) {
                    return false;
                }
                if (!eqNode.executeEq(frame, inliningTarget, lnode.key, rnode.key)) {
                    return false;
                }
                Object lvalue = getItem.execute(frame, inliningTarget, self.getDictStorage(), lnode.key, lnode.hash);
                Object rvalue = getItem.execute(frame, inliningTarget, other.getDictStorage(), rnode.key, rnode.hash);
                if (!eqNode.executeEq(frame, inliningTarget, lvalue, rvalue)) {
                    return false;
                }
                lnode = lnode.next;
                rnode = rnode.next;
            } while (true);
        }

        @Fallback
        static boolean cmp(VirtualFrame frame, Node inliningTarget, POrderedDict self, PDict other,
                        @Cached HashingStorageNodes.HashingStorageEq eqNode) {
            return eqNode.execute(frame, inliningTarget, self.getDictStorage(), other.getDictStorage());
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class EqNode extends RichCmpBuiltinNode {
        @Specialization(guards = "op.isEqOrNe()")
        static boolean cmp(VirtualFrame frame, POrderedDict self, PDict other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached EqHelperNode eqHelperNode) {
            return eqHelperNode.execute(frame, inliningTarget, self, other) == op.isEq();
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object cmp(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone dict(Object self, PDict mapping,
                        @Bind Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)")
        static Object dict(Object self, @SuppressWarnings("unused") PNone mapping,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        static PNone dict(@SuppressWarnings("unused") Object self, Object mapping,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = J___SIZEOF__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SizeOfNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int sizeof(POrderedDict self) {
            // The constants are made up
            return 300 + self.nodes.size() * 32;
        }
    }
}
