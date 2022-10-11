/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
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
        private static final TruffleString T_ELLIPSIS = tsLiteral("{...}");
        private static final TruffleString T_COLONSPACE = tsLiteral(": ");
        private static final TruffleString T_LPAREN_BRACKET = tsLiteral("([");
        private static final TruffleString T_RPAREN_BRACKET = tsLiteral("])");

        @Override
        public abstract TruffleString execute(VirtualFrame VirtualFrame, Object arg);

        @CompilerDirectives.ValueType
        protected static final class ReprState {
            private final Object self;
            private final HashingStorage dictStorage;
            private final TruffleStringBuilder result;
            private final int initialLength;

            ReprState(Object self, HashingStorage dictStorage, TruffleStringBuilder result) {
                this.self = self;
                this.dictStorage = dictStorage;
                this.result = result;
                initialLength = result.byteLength();
            }
        }

        abstract static class AbstractForEachRepr extends ForEachNode<ReprState> {
            private final int limit;

            AbstractForEachRepr(int limit) {
                this.limit = limit;
            }

            protected final int getLimit() {
                return limit;
            }

            public abstract ReprState executeReprState(Object key, ReprState arg);

            @Override
            public final ReprState execute(Object key, ReprState arg) {
                return executeReprState(key, arg);
            }

            protected static TruffleString getReprString(Object obj, ReprState s,
                            LookupAndCallUnaryDynamicNode reprNode,
                            CastToTruffleStringNode castStr,
                            PRaiseNode raiseNode) {
                Object reprObj = s == null || obj != s.self ? reprNode.executeObject(obj, T___REPR__) : T_ELLIPSIS;
                try {
                    return castStr.execute(reprObj);
                } catch (CannotCastException e) {
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_STRING, "__repr__", reprObj);
                }
            }

            protected static void appendSeparator(ReprState s, ConditionProfile lengthCheck, TruffleStringBuilder.AppendStringNode appendStringNode) {
                if (lengthCheck.profile(s.result.byteLength() > s.initialLength)) {
                    appendStringNode.execute(s.result, T_COMMA_SPACE);
                }
            }
        }

        abstract static class ForEachKeyRepr extends AbstractForEachRepr {
            public ForEachKeyRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState append(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached ConditionProfile lengthCheck,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(s, lengthCheck, appendStringNode);
                appendStringNode.execute(s.result, getReprString(key, null, reprNode, castStr, raiseNode));
                return s;
            }
        }

        abstract static class ForEachValueRepr extends AbstractForEachRepr {
            public ForEachValueRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached ConditionProfile lengthCheck,
                            @Cached HashingStorageGetItem getItem,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(s, lengthCheck, appendStringNode);
                // TODO: channel the frame through the for each node
                appendStringNode.execute(s.result, getReprString(getItem.execute(null, s.dictStorage, key), s, reprNode, castStr, raiseNode));
                return s;
            }
        }

        abstract static class ForEachItemRepr extends AbstractForEachRepr {
            public ForEachItemRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached ConditionProfile lengthCheck,
                            @Cached HashingStorageGetItem getItem,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                appendSeparator(s, lengthCheck, appendStringNode);
                appendStringNode.execute(s.result, T_LPAREN);
                appendStringNode.execute(s.result, getReprString(key, null, keyReprNode, castStr, raiseNode));
                appendStringNode.execute(s.result, T_COMMA_SPACE);
                // TODO: channel the frame through the for each node
                appendStringNode.execute(s.result, getReprString(getItem.execute(null, s.dictStorage, key), s, valueReprNode, castStr, raiseNode));
                appendStringNode.execute(s.result, T_RPAREN);
                return s;
            }
        }

        abstract static class ForEachDictRepr extends AbstractForEachRepr {
            public ForEachDictRepr(int limit) {
                super(limit);
            }

            @Specialization
            public static ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToTruffleStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached ConditionProfile lengthCheck,
                            @Cached HashingStorageGetItem getItem,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
                // TODO: channel the frame through the for each node
                TruffleString keyReprString = getReprString(key, null, keyReprNode, castStr, raiseNode);
                TruffleString valueReprString = getReprString(getItem.execute(null, s.dictStorage, key), s, valueReprNode, castStr, raiseNode);
                appendSeparator(s, lengthCheck, appendStringNode);
                appendStringNode.execute(s.result, keyReprString);
                appendStringNode.execute(s.result, T_COLONSPACE);
                appendStringNode.execute(s.result, valueReprString);
                return s;
            }
        }

        @Specialization // use same limit as for EachRepr nodes library
        public static TruffleString repr(PDict dict,
                        @Cached("create(3)") ForEachDictRepr consumerNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            PythonContext ctxt = PythonContext.get(lib);
            if (!ctxt.reprEnter(dict)) {
                return T_ELLIPSIS;
            }
            try {
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(sb, T_LBRACE);
                HashingStorage dictStorage = dict.getDictStorage();
                lib.forEach(dictStorage, consumerNode, new ReprState(dict, dictStorage, sb));
                appendStringNode.execute(sb, T_RBRACE);
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(dict);
            }
        }

        @Specialization// use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictKeysView view,
                        @Cached("create(3)") ForEachKeyRepr consumerNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            return viewRepr(view, PythonBuiltinClassType.PDictKeysView.getName(), lib, consumerNode, appendStringNode, toStringNode);
        }

        @Specialization // use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictValuesView view,
                        @Cached("create(3)") ForEachValueRepr consumerNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            return viewRepr(view, PythonBuiltinClassType.PDictValuesView.getName(), lib, consumerNode, appendStringNode, toStringNode);
        }

        @Specialization// use same limit as for EachRepr nodes library
        public static TruffleString repr(PDictItemsView view,
                        @Cached("create(3)") ForEachItemRepr consumerNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            return viewRepr(view, PythonBuiltinClassType.PDictItemsView.getName(), lib, consumerNode, appendStringNode, toStringNode);
        }

        private static TruffleString viewRepr(PDictView view, TruffleString type, HashingStorageLibrary lib, AbstractForEachRepr consumerNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, type);
            appendStringNode.execute(sb, T_LPAREN_BRACKET);
            HashingStorage dictStorage = view.getWrappedDict().getDictStorage();
            lib.forEach(dictStorage, consumerNode, new ReprState(view, dictStorage, sb));
            appendStringNode.execute(sb, T_RPAREN_BRACKET);
            return toStringNode.execute(sb);
        }
    }
}
