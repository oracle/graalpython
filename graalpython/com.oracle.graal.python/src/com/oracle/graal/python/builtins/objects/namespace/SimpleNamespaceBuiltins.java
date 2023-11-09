/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.NO_POSITIONAL_ARGUMENTS_EXPECTED;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EQ;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSimpleNamespace)
public final class SimpleNamespaceBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SimpleNamespaceBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class SimpleNamespaceInitNode extends PythonVarargsBuiltinNode {
        @Specialization(limit = "1")
        Object init(PSimpleNamespace self, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(value = "self") DynamicObjectLibrary dyLib) {
            if (args.length > 0) {
                throw raise(PythonBuiltinClassType.TypeError, NO_POSITIONAL_ARGUMENTS_EXPECTED);
            }
            for (PKeyword keyword : kwargs) {
                dyLib.put(self, keyword.getName(), keyword.getValue());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SimpleNamespaceDictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getDict(PSimpleNamespace self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceEqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object eq(VirtualFrame frame, PSimpleNamespace self, PSimpleNamespace other,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached DictBuiltins.EqNode eqNode) {
            return eqNode.execute(frame, getDict.execute(inliningTarget, self), getDict.execute(inliningTarget, other));
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PSimpleNamespace self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached PythonObjectFactory factory) {
            PTuple args = factory.createEmptyTuple();
            final PDict dict = getDict.execute(inliningTarget, self);
            return factory.createTuple(new Object[]{getClassNode.execute(inliningTarget, self), args, dict});
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SimpleNamespaceReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString T_RECURSE = tsLiteral("...)");
        private static final TruffleString T_NAMESPACE = tsLiteral("namespace");

        @CompilerDirectives.ValueType
        protected static final class NSReprState {
            private final HashingStorage dictStorage;
            private final List<Pair<TruffleString, TruffleString>> items;

            @CompilerDirectives.TruffleBoundary
            NSReprState(HashingStorage dictStorage) {
                this.dictStorage = dictStorage;
                this.items = new ArrayList<>();
            }

            @CompilerDirectives.TruffleBoundary
            private void sortItemsByKey() {
                items.sort(Comparator.comparing(Pair::getLeft, StringUtils::compareStringsUncached));
            }

            public void appendToTruffleStringBuilder(TruffleStringBuilder sb, TruffleStringBuilder.AppendStringNode appendStringNode) {
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

            protected static TruffleString getReprString(Node inliningTarget, Object obj,
                            LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode reprNode,
                            CastToTruffleStringNode castStr,
                            PRaiseNode raiseNode) {
                Object reprObj = reprNode.executeObject(obj, T___REPR__);
                try {
                    return castStr.execute(inliningTarget, reprObj);
                } catch (CannotCastException e) {
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_STRING, "__repr__", reprObj);
                }
            }

            @Override
            public abstract NSReprState execute(Frame frame, Node node, HashingStorage storage, HashingStorageIterator it, NSReprState state);

            @Specialization
            public static NSReprState doPStringKey(@SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, NSReprState state,
                            @Bind("this") Node inliningTarget,
                            @Cached LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToTruffleStringNode castStrKey,
                            @Cached CastToTruffleStringNode castStrValue,
                            @Cached PRaiseNode raiseNode,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageGetItem getItem) {
                Object keyObj = itKey.execute(inliningTarget, storage, it);
                if (PGuards.isString(keyObj)) {
                    TruffleString key = castStrKey.execute(inliningTarget, keyObj);
                    TruffleString valueReprString = getReprString(inliningTarget, getItem.execute(inliningTarget, state.dictStorage, key), valueReprNode, castStrValue, raiseNode);
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
                        @Bind("this") Node inliningTarget,
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
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
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
