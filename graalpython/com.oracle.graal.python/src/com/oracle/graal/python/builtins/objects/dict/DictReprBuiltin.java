/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_BRACES;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDict})
public final class DictReprBuiltin extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictReprBuiltinFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString T_LPAREN_BRACKET = tsLiteral("([");
        private static final TruffleString T_RPAREN_BRACKET = tsLiteral("])");

        @Override
        public abstract TruffleString execute(VirtualFrame VirtualFrame, Object arg);

        @ValueType
        protected static final class ReprState {
            private final Object self;
            private final TruffleStringBuilder result;
            private final int initialLength;

            ReprState(Object self, TruffleStringBuilder result) {
                this.self = self;
                this.result = result;
                initialLength = result.byteLength();
            }
        }

        abstract static class AbstractForEachRepr extends HashingStorageForEachCallback<ReprState> {
            private final int limit;

            AbstractForEachRepr(int limit) {
                this.limit = limit;
            }

            protected final int getLimit() {
                return limit;
            }

            @Override
            public abstract ReprState execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ReprState s);

            protected static TruffleString getReprString(Node inliningTarget, Object obj, ReprState s,
                            LookupAndCallUnaryDynamicNode reprNode,
                            CastToTruffleStringNode castStr,
                            PRaiseNode.Lazy raiseNode) {
                Object reprObj = s == null || obj != s.self ? reprNode.executeObject(obj, T___REPR__) : T_ELLIPSIS_IN_BRACES;
                try {
                    return castStr.execute(inliningTarget, reprObj);
                } catch (CannotCastException e) {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_STRING, "__repr__", reprObj);
                }
            }

            protected static void appendSeparator(Node inliningTarget, ReprState s, InlinedConditionProfile lengthCheck, TruffleStringBuilder.AppendStringNode appendStringNode) {
                if (lengthCheck.profile(inliningTarget, s.result.byteLength() > s.initialLength)) {
                    appendStringNode.execute(s.result, T_COMMA_SPACE);
                }
            }
        }

        abstract static class ForEachKeyRepr extends AbstractForEachRepr {
            public ForEachKeyRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState append(@SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, ReprState s,
                            @Bind("this") Node inliningTarget,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode.Lazy raiseNode,
                            @Cached InlinedConditionProfile lengthCheck,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(inliningTarget, s, lengthCheck, appendStringNode);
                Object key = itKey.execute(inliningTarget, storage, it);
                appendStringNode.execute(s.result, getReprString(inliningTarget, key, null, reprNode, castStr, raiseNode));
                return s;
            }
        }

        abstract static class ForEachValueRepr extends AbstractForEachRepr {
            public ForEachValueRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, ReprState s,
                            @Bind("this") Node inliningTarget,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode.Lazy raiseNode,
                            @Cached InlinedConditionProfile lengthCheck,
                            @Cached HashingStorageIteratorValue itValue,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(inliningTarget, s, lengthCheck, appendStringNode);
                Object value = itValue.execute(inliningTarget, storage, it);
                appendStringNode.execute(s.result, getReprString(inliningTarget, value, s, reprNode, castStr, raiseNode));
                return s;
            }
        }

        abstract static class ForEachItemRepr extends AbstractForEachRepr {
            public ForEachItemRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, ReprState s,
                            @Bind("this") Node inliningTarget,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode.Lazy raiseNode,
                            @Cached InlinedConditionProfile lengthCheck,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorValue itValue,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(inliningTarget, s, lengthCheck, appendStringNode);
                appendStringNode.execute(s.result, T_LPAREN);
                Object key = itKey.execute(inliningTarget, storage, it);
                Object value = itValue.execute(inliningTarget, storage, it);
                appendStringNode.execute(s.result, getReprString(inliningTarget, key, null, keyReprNode, castStr, raiseNode));
                appendStringNode.execute(s.result, T_COMMA_SPACE);
                appendStringNode.execute(s.result, getReprString(inliningTarget, value, s, valueReprNode, castStr, raiseNode));
                appendStringNode.execute(s.result, T_RPAREN);
                return s;
            }
        }

        abstract static class ForEachDictRepr extends AbstractForEachRepr {
            public ForEachDictRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, ReprState s,
                            @Bind("this") Node inliningTarget,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode.Lazy raiseNode,
                            @Cached InlinedConditionProfile lengthCheck,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorValue itValue,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                Object key = itKey.execute(inliningTarget, storage, it);
                Object value = itValue.execute(inliningTarget, storage, it);
                TruffleString keyReprString = getReprString(inliningTarget, key, null, keyReprNode, castStr, raiseNode);
                TruffleString valueReprString = getReprString(inliningTarget, value, s, valueReprNode, castStr, raiseNode);
                appendSeparator(inliningTarget, s, lengthCheck, appendStringNode);
                appendStringNode.execute(s.result, keyReprString);
                appendStringNode.execute(s.result, T_COLON_SPACE);
                appendStringNode.execute(s.result, valueReprString);
                return s;
            }
        }

        @Specialization(guards = "!isDictView(dict)") // use same limit as for EachRepr nodes
                                                      // library
        public static TruffleString repr(Object dict,
                        @Bind("this") Node inliningTarget,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached("create(3)") ForEachDictRepr consumerNode,
                        @Shared @Cached HashingStorageForEach forEachNode,
                        @Shared @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            PythonContext ctxt = PythonContext.get(forEachNode);
            if (!ctxt.reprEnter(dict)) {
                return T_ELLIPSIS_IN_BRACES;
            }
            try {
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(sb, T_LBRACE);
                var storage = getStorageNode.execute(inliningTarget, dict);
                forEachNode.execute(null, inliningTarget, storage, consumerNode, new ReprState(dict, sb));
                appendStringNode.execute(sb, T_RBRACE);
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(dict);
            }
        }

        @Specialization// use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictKeysView view,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(3)") ForEachKeyRepr consumerNode,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.GetNameNode getNameNode,
                        @Shared @Cached HashingStorageForEach forEachNode,
                        @Shared @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, view));
            return viewRepr(inliningTarget, view, typeName, forEachNode, consumerNode, appendStringNode, toStringNode);
        }

        @Specialization // use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictValuesView view,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(3)") ForEachValueRepr consumerNode,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.GetNameNode getNameNode,
                        @Shared @Cached HashingStorageForEach forEachNode,
                        @Shared @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, view));
            return viewRepr(inliningTarget, view, typeName, forEachNode, consumerNode, appendStringNode, toStringNode);
        }

        @Specialization// use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictItemsView view,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(3)") ForEachItemRepr consumerNode,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.GetNameNode getNameNode,
                        @Shared @Cached HashingStorageForEach forEachNode,
                        @Shared @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, view));
            return viewRepr(inliningTarget, view, typeName, forEachNode, consumerNode, appendStringNode, toStringNode);
        }

        private static TruffleString viewRepr(Node inliningTarget, PDictView view, TruffleString type, HashingStorageForEach forEachNode, AbstractForEachRepr consumerNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, type);
            appendStringNode.execute(sb, T_LPAREN_BRACKET);
            HashingStorage dictStorage = view.getWrappedStorage();
            forEachNode.execute(null, inliningTarget, dictStorage, consumerNode, new ReprState(view, sb));
            appendStringNode.execute(sb, T_RPAREN_BRACKET);
            return toStringNode.execute(sb);
        }
    }
}
