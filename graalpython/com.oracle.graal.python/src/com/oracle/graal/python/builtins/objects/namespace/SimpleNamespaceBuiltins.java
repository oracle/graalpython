/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.namespace;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EQ;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSimpleNamespace)
public final class SimpleNamespaceBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = SimpleNamespaceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SimpleNamespaceBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "SimpleNamespace", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PSimpleNamespace doit(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createSimpleNamespace(cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class SimpleNamespaceInitNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object init(VirtualFrame frame, PSimpleNamespace self, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached DictNodes.UpdateNode updateNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext iteratorNext,
                        @Cached HashingStorageIteratorKey iteratorKey,
                        @Cached HashingStorageIteratorValue iteratorValue,
                        @Cached CastToTruffleStringNode castString,
                        @Cached WriteAttributeToPythonObjectNode writeAttrNode,
                        @Cached PRaiseNode raiseNode) {
            if (args.length > 1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_AT_MOST_ONE_ARG_GOT_D,
                                self, args.length);
            }
            if (args.length == 1) {
                PDict dict;
                if (args[0] instanceof PDict dictArg && PGuards.isBuiltinDict(dictArg)) {
                    dict = dictArg;
                } else {
                    dict = PFactory.createDict(PythonLanguage.get(inliningTarget));
                    updateNode.execute(frame, dict, args[0]);
                }
                HashingStorage storage = dict.getDictStorage();
                HashingStorageIterator iterator = getIterator.execute(inliningTarget, storage);
                while (iteratorNext.execute(inliningTarget, storage, iterator)) {
                    Object key = iteratorKey.execute(inliningTarget, storage, iterator);
                    if (!PGuards.isString(key)) {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS);
                    }
                }
                iterator = getIterator.execute(inliningTarget, storage);
                while (iteratorNext.execute(inliningTarget, storage, iterator)) {
                    Object key = iteratorKey.execute(inliningTarget, storage, iterator);
                    writeAttrNode.execute(self, castString.execute(inliningTarget, key), iteratorValue.execute(inliningTarget, storage, iterator));
                }
            }
            for (PKeyword keyword : kwargs) {
                writeAttrNode.execute(self, keyword.getName(), keyword.getValue());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SimpleNamespaceDictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getDict(PSimpleNamespace self,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceEqNode extends RichCmpBuiltinNode {
        @Specialization
        static Object eq(VirtualFrame frame, PSimpleNamespace self, PSimpleNamespace other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached DictBuiltins.EqNode eqNode) {
            return eqNode.execute(frame, getDict.execute(inliningTarget, self), getDict.execute(inliningTarget, other), op);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PSimpleNamespace self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Bind PythonLanguage language) {
            PTuple args = PFactory.createEmptyTuple(language);
            final PDict dict = getDict.execute(inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), args, dict});
        }
    }

    @Builtin(name = "__replace__", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, doc = "Return a copy of the namespace object with new values for the specified attributes.")
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceReplaceNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object replace(VirtualFrame frame, PSimpleNamespace self, @SuppressWarnings("unused") Object[] args, PKeyword[] changes,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached CallNode callNode,
                        @Cached DynamicObject.GetKeyArrayNode getKeyArrayNode,
                        @Cached(inline = true) ReadAttributeFromPythonObjectNode readAttrNode,
                        @Cached WriteAttributeToPythonObjectNode writeAttrNode,
                        @Cached PRaiseNode raiseNode) {
            if (args.length > 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.SIMPLE_NAMESPACE_REPLACE_NO_POSITIONAL);
            }
            Object cls = getClassNode.execute(inliningTarget, self);
            Object resultObject = callNode.execute(frame, cls, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
            if (!(resultObject instanceof PSimpleNamespace result)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.SIMPLE_NAMESPACE_REPLACE_WRONG_TYPE, self, resultObject);
            }
            for (Object key : getKeyArrayNode.execute(self)) {
                if (key instanceof TruffleString name) {
                    Object value = readAttrNode.execute(inliningTarget, self, name, PNone.NO_VALUE);
                    if (value != PNone.NO_VALUE) {
                        writeAttrNode.execute(result, name, value);
                    }
                }
            }
            for (PKeyword change : changes) {
                writeAttrNode.execute(result, change.getName(), change.getValue());
            }
            return result;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class SimpleNamespaceReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString T_RECURSE = tsLiteral("...)");
        private static final TruffleString T_NAMESPACE = tsLiteral("namespace");

        @CompilerDirectives.ValueType
        protected static final class NSReprState {
            private final HashingStorage dictStorage;
            private final ArrayList<Pair<TruffleString, TruffleString>> items;

            @CompilerDirectives.TruffleBoundary
            NSReprState(HashingStorage dictStorage) {
                this.dictStorage = dictStorage;
                this.items = new ArrayList<>();
            }

            @CompilerDirectives.TruffleBoundary
            private void sortItemsByKey() {
                items.sort(Comparator.comparing(Pair::getLeft, StringUtils::compareStringsUncached));
            }

            public void appendToTruffleStringBuilder(TruffleStringBuilderUTF32 sb, TruffleStringBuilder.AppendStringNode appendStringNode) {
                sortItemsByKey();
                for (int i = 0; i < items.size(); i++) {
                    Pair<TruffleString, TruffleString> item = items.get(i);
                    if (i > 0) {
                        appendStringNode.execute(sb, T_COMMA_SPACE);
                    }
                    appendStringNode.execute(sb, item.getLeft());
                    appendStringNode.execute(sb, T_EQ);
                    appendStringNode.execute(sb, item.getRight());
                }
            }
        }

        @ImportStatic(PGuards.class)
        abstract static class ForEachNSRepr extends HashingStorageForEachCallback<NSReprState> {
            private final int limit;

            protected ForEachNSRepr(int limit) {
                this.limit = limit;
            }

            protected final int getLimit() {
                return limit;
            }

            protected static TruffleString getReprString(Frame frame, Node inliningTarget, Object obj,
                            PyObjectReprAsTruffleStringNode reprNode) {
                return reprNode.execute(frame, inliningTarget, obj);
            }

            @Override
            public abstract NSReprState execute(Frame frame, Node node, HashingStorage storage, HashingStorageIterator it, NSReprState state);

            @Specialization
            public static NSReprState doPStringKey(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, NSReprState state,
                            @Bind Node inliningTarget,
                            @Cached PyObjectReprAsTruffleStringNode valueReprNode,
                            @Cached CastToTruffleStringNode castStrKey,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageGetItem getItem) {
                Object keyObj = itKey.execute(inliningTarget, storage, it);
                if (PGuards.isString(keyObj)) {
                    TruffleString key = castStrKey.execute(inliningTarget, keyObj);
                    TruffleString valueReprString = getReprString(frame, inliningTarget, getItem.execute(inliningTarget, state.dictStorage, key), valueReprNode);
                    appendItem(state, key, valueReprString);
                }
                return state;
            }

            @CompilerDirectives.TruffleBoundary
            private static void appendItem(NSReprState state, TruffleString key, TruffleString valueReprString) {
                state.items.add(Pair.create(key, valueReprString));
            }
        }

        @Specialization
        public static Object repr(PSimpleNamespace ns,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassExactProfile clsProfile,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached("create(3)") ForEachNSRepr consumerNode,
                        @Cached HashingStorageForEach forEachNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            final Object klass = getClassNode.execute(inliningTarget, ns);
            final TruffleString name = clsProfile.profileClass(inliningTarget, klass, PythonBuiltinClassType.PSimpleNamespace) ? T_NAMESPACE : getNameNode.execute(inliningTarget, klass);
            TruffleStringBuilderUTF32 sb = TruffleStringBuilder.createUTF32();
            appendStringNode.execute(sb, name);
            appendStringNode.execute(sb, T_LPAREN);
            PythonContext ctxt = PythonContext.get(forEachNode);
            if (!ctxt.reprEnter(ns)) {
                appendStringNode.execute(sb, T_RECURSE);
                return toStringNode.execute(sb);
            }
            try {
                HashingStorage dictStorage = getDict.execute(inliningTarget, ns).getDictStorage();
                final NSReprState state = new NSReprState(dictStorage);
                forEachNode.execute(null, inliningTarget, dictStorage, consumerNode, state);
                state.appendToTruffleStringBuilder(sb, appendStringNode);
                appendStringNode.execute(sb, T_RPAREN);
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(ns);
            }
        }
    }
}
