/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.util.List;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDict})
public final class DictReprBuiltin extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictReprBuiltinFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @CompilerDirectives.ValueType
        protected static final class ReprState {
            private final Object self;
            private final HashingStorage dictStorage;
            private final StringBuilder result;
            private final int initialLength;

            ReprState(Object self, HashingStorage dictStorage, StringBuilder result) {
                this.self = self;
                this.dictStorage = dictStorage;
                this.result = result;
                initialLength = result.length();
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

            protected String getReprString(Object obj, ReprState s, LookupAndCallUnaryDynamicNode reprNode, CastToJavaStringNode castStr, BranchProfile nullBranch, PRaiseNode raiseNode)
                            throws PException {
                Object reprObj = s == null || obj != s.self ? reprNode.executeObject(obj, __REPR__) : "{...}";
                try {
                    return castStr.execute(reprObj);
                } catch (CannotCastException e) {
                    nullBranch.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_STRING, "__repr__", reprObj);
                }
            }

            protected static final void appendSeparator(ReprState s, ConditionProfile lengthCheck) {
                if (lengthCheck.profile(s.result.length() > s.initialLength)) {
                    sbAppend(s.result, ", ");
                }
            }
        }

        abstract static class ForEachKeyRepr extends AbstractForEachRepr {
            public ForEachKeyRepr(int limit) {
                super(limit);
            }

            @Specialization
            public ReprState append(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToJavaStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached("createBinaryProfile()") ConditionProfile lengthCheck,
                            @Cached BranchProfile nullBranch) {
                appendSeparator(s, lengthCheck);
                sbAppend(s.result, getReprString(key, null, reprNode, castStr, nullBranch, raiseNode));
                return s;
            }
        }

        abstract static class ForEachValueRepr extends AbstractForEachRepr {
            public ForEachValueRepr(int limit) {
                super(limit);
            }

            @Specialization
            public ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode reprNode,
                            @Cached CastToJavaStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached("createBinaryProfile()") ConditionProfile lengthCheck,
                            @Cached BranchProfile nullBranch,
                            @CachedLibrary(limit = "getLimit()") HashingStorageLibrary lib) {
                appendSeparator(s, lengthCheck);
                sbAppend(s.result, getReprString(lib.getItem(s.dictStorage, key), s, reprNode, castStr, nullBranch, raiseNode));
                return s;
            }
        }

        abstract static class ForEachItemRepr extends AbstractForEachRepr {
            public ForEachItemRepr(int limit) {
                super(limit);
            }

            @Specialization
            public ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToJavaStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached("createBinaryProfile()") ConditionProfile lengthCheck,
                            @Cached BranchProfile keyNullBranch,
                            @Cached BranchProfile valueNullBranch,
                            @CachedLibrary(limit = "getLimit()") HashingStorageLibrary lib) {
                appendSeparator(s, lengthCheck);
                sbAppend(s.result, "(");
                sbAppend(s.result, getReprString(key, null, keyReprNode, castStr, keyNullBranch, raiseNode));
                sbAppend(s.result, ", ");
                sbAppend(s.result, getReprString(lib.getItem(s.dictStorage, key), s, valueReprNode, castStr, valueNullBranch, raiseNode));
                sbAppend(s.result, ")");
                return s;
            }
        }

        abstract static class ForEachDictRepr extends AbstractForEachRepr {
            public ForEachDictRepr(int limit) {
                super(limit);
            }

            @Specialization
            public ReprState dict(Object key, ReprState s,
                            @Cached LookupAndCallUnaryDynamicNode keyReprNode,
                            @Cached LookupAndCallUnaryDynamicNode valueReprNode,
                            @Cached CastToJavaStringNode castStr,
                            @Cached PRaiseNode raiseNode,
                            @Cached("createBinaryProfile()") ConditionProfile lengthCheck,
                            @Cached BranchProfile keyNullBranch,
                            @Cached BranchProfile valueNullBranch,
                            @CachedLibrary(limit = "getLimit()") HashingStorageLibrary lib) {
                String keyReprString = getReprString(key, null, keyReprNode, castStr, keyNullBranch, raiseNode);
                String valueReprString = getReprString(lib.getItem(s.dictStorage, key), s, valueReprNode, castStr, valueNullBranch, raiseNode);
                appendSeparator(s, lengthCheck);
                sbAppend(s.result, keyReprString);
                sbAppend(s.result, ": ");
                sbAppend(s.result, valueReprString);
                return s;
            }
        }

        @Specialization // use same limit as for EachRepr nodes library
        public Object repr(PDict dict,
                        @Cached("create(3)") ForEachDictRepr consumerNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            StringBuilder sb = new StringBuilder("{");
            HashingStorage dictStorage = getDictStorage.execute(dict);
            lib.forEach(dictStorage, consumerNode, new ReprState(dict, dictStorage, sb));
            sbAppend(sb, "}");
            return sb.toString();
        }

        @Specialization// use same limit as for EachRepr nodes library
        public Object repr(PDictKeysView view,
                        @Cached("create(3)") ForEachKeyRepr consumerNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return viewRepr(view, PythonBuiltinClassType.PDictKeysView.getName(), getDictStorage, lib, consumerNode);
        }

        @Specialization // use same limit as for EachRepr nodes library
        public Object repr(PDictValuesView view,
                        @Cached("create(3)") ForEachValueRepr consumerNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return viewRepr(view, PythonBuiltinClassType.PDictValuesView.getName(), getDictStorage, lib, consumerNode);
        }

        @Specialization// use same limit as for EachRepr nodes library
        public Object repr(PDictItemsView view,
                        @Cached("create(3)") ForEachItemRepr consumerNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return viewRepr(view, PythonBuiltinClassType.PDictItemsView.getName(), getDictStorage, lib, consumerNode);
        }

        private static String viewRepr(PDictView view, String type, GetDictStorageNode getDictStorage, HashingStorageLibrary lib, AbstractForEachRepr consumerNode) {
            StringBuilder sb = new StringBuilder(type);
            sbAppend(sb, "([");
            HashingStorage dictStorage = getDictStorage.execute(view.getWrappedDict());
            lib.forEach(dictStorage, consumerNode, new ReprState(view, dictStorage, sb));
            sbAppend(sb, "])");
            return sb.toString();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static StringBuilder sbAppend(StringBuilder sb, String s) {
        return sb.append(s);
    }
}
