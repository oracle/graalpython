/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.functools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_PARTIAL_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_S_TAKES_AT_LEAST_ONE_ARGUMENT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EQ;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictCheckExactNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.DeleteDictNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendStringNode;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PPartial)
public final class PartialBuiltins extends PythonBuiltins {
    public static Object[] getNewPartialArgs(PPartial partial, Object[] args, Node inliningTarget, InlinedConditionProfile hasArgsProfile) {
        return getNewPartialArgs(partial, args, inliningTarget, hasArgsProfile, 0);
    }

    public static Object[] getNewPartialArgs(PPartial partial, Object[] args, Node inliningTarget, InlinedConditionProfile hasArgsProfile, int offset) {
        Object[] newArgs;
        Object[] pArgs = partial.getArgs();
        if (hasArgsProfile.profile(inliningTarget, args.length > offset)) {
            newArgs = new Object[pArgs.length + args.length - offset];
            PythonUtils.arraycopy(pArgs, 0, newArgs, 0, pArgs.length);
            PythonUtils.arraycopy(args, offset, newArgs, pArgs.length, args.length - offset);
        } else {
            newArgs = pArgs;
        }
        return newArgs;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PartialBuiltinsFactory.getFactories();
    }

    // functools.partial(func, /, *args, **keywords)
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, varArgsMarker = true, takesVarArgs = true, //
                    takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PPartial, //
                    doc = "partial(func, *args, **keywords) - new function with partial application\n" + //
                                    "of the given arguments and keywords.\n")
    @GenerateNodeFactory
    public abstract static class PartialNewNode extends PythonBuiltinNode {
        protected boolean isPartialWithoutDict(Node inliningTarget, GetDictIfExistsNode getDict, Object[] args, HashingStorageLen lenNode, boolean withKwDict) {
            return isPartialWithoutDict(getDict, args) && withKwDict == ((PPartial) args[0]).hasKw(inliningTarget, lenNode);
        }

        protected boolean isPartialWithoutDict(GetDictIfExistsNode getDict, Object[] args) {
            return getDict.execute(args[0]) == null && args[0] instanceof PPartial;
        }

        protected boolean withKeywords(PKeyword[] keywords) {
            return keywords.length > 0;
        }

        protected boolean atLeastOneArg(Object[] args) {
            return args.length >= 1;
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(inliningTarget, getDict, args, lenNode, false)"}, limit = "1")
        static Object createFromPartialWoDictWoKw(Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Exclusive @Cached InlinedConditionProfile hasArgsProfile,
                        @Exclusive @Cached InlinedConditionProfile hasKeywordsProfile,
                        @Exclusive @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Shared @Cached PythonObjectFactory factory) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);

            PDict funcKwDict;
            if (hasKeywordsProfile.profile(inliningTarget, keywords.length > 0)) {
                funcKwDict = factory.createDict(keywords);
            } else {
                funcKwDict = factory.createDict();
            }

            return factory.createPartial(cls, function.getFn(), funcArgs, funcKwDict);
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(inliningTarget, getDict, args, lenNode, true)", "!withKeywords(keywords)"}, limit = "1")
        static Object createFromPartialWoDictWKw(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Exclusive @Cached InlinedConditionProfile hasArgsProfile,
                        @Exclusive @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Exclusive @Cached HashingStorageCopy copyNode,
                        @Shared @Cached PythonObjectFactory factory) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);
            return factory.createPartial(cls, function.getFn(), funcArgs, function.getKwCopy(inliningTarget, factory, copyNode));
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(inliningTarget, getDict, args, lenNode, true)", "withKeywords(keywords)"}, limit = "1")
        static Object createFromPartialWoDictWKwKw(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Exclusive @Cached InlinedConditionProfile hasArgsProfile,
                        @Exclusive @Cached HashingStorage.InitNode initNode,
                        @Exclusive @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Exclusive @Cached HashingStorageCopy copyHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Shared @Cached PythonObjectFactory factory) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);

            HashingStorage storage = copyHashingStorageNode.execute(inliningTarget, function.getKw().getDictStorage());
            PDict result = factory.createDict(storage);
            addAllToOtherNode.execute(frame, inliningTarget, initNode.execute(frame, PNone.NO_VALUE, keywords), result);

            return factory.createPartial(cls, function.getFn(), funcArgs, result);
        }

        @Specialization(guards = {"atLeastOneArg(args)", "!isPartialWithoutDict(getDict, args)"}, limit = "1")
        static Object createGeneric(Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Exclusive @Cached InlinedConditionProfile hasKeywordsProfile,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object function = args[0];
            if (!callableCheckNode.execute(inliningTarget, function)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_ARG_MUST_BE_CALLABLE, "the first");
            }

            final Object[] funcArgs = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            PDict funcKwDict;
            if (hasKeywordsProfile.profile(inliningTarget, keywords.length > 0)) {
                funcKwDict = factory.createDict(keywords);
            } else {
                funcKwDict = factory.createDict();
            }
            return factory.createPartial(cls, function, funcArgs, funcKwDict);
        }

        @Specialization(guards = "!atLeastOneArg(args)")
        @SuppressWarnings("unused")
        static Object noCallable(Object cls, Object[] args, PKeyword[] keywords,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, TYPE_S_TAKES_AT_LEAST_ONE_ARGUMENT, "partial");
        }
    }

    @Builtin(name = "func", minNumOfPositionalArgs = 1, isGetter = true, doc = "function object to use in future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialFuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGet(PPartial self) {
            return self.getFn();
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, isGetter = true, doc = "tuple of arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGet(PPartial self,
                        @Cached PythonObjectFactory factory) {
            return self.getArgsTuple(factory);
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class PartialDictNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(mapping)")
        static Object getDict(PPartial self, @SuppressWarnings("unused") PNone mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        static Object setDict(PPartial self, PDict mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        static Object setDict(@SuppressWarnings("unused") PPartial self, Object mapping,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = "keywords", minNumOfPositionalArgs = 1, isGetter = true, doc = "dictionary of keyword arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialKeywordsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGet(PPartial self,
                        @Cached PythonObjectFactory factory) {
            return self.getOrCreateKw(factory);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PartialReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PPartial self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @Cached GetOrCreateDictNode getOrCreateDictNode,
                        @Cached PythonObjectFactory factory) {
            final PDict dict;
            if (self.getShape().getPropertyCount() > 0) {
                dict = getOrCreateDictNode.execute(inliningTarget, self);
            } else {
                dict = getDictIfExistsNode.execute(self);
            }
            final Object type = getClassNode.execute(inliningTarget, self);
            final PTuple fnTuple = factory.createTuple(new Object[]{self.getFn()});
            final PTuple argsTuple = factory.createTuple(new Object[]{self.getFn(), self.getArgsTuple(factory), self.getKw(), (dict != null) ? dict : PNone.NONE});
            return factory.createTuple(new Object[]{type, fnTuple, argsTuple});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartialSetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(VirtualFrame frame, PPartial self, PTuple state,
                        @Bind("this") Node inliningTarget,
                        @Cached SetDictNode setDictNode,
                        @Cached DeleteDictNode deleteDictNode,
                        @Cached SequenceNodes.GetSequenceStorageNode storageNode,
                        @Cached SequenceStorageNodes.ToArrayNode arrayNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached PyTupleCheckExactNode tupleCheckExactNode,
                        @Cached PyDictCheckExactNode dictCheckExactNode,
                        @Cached PyTupleGetItem getItemNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached HashingStorageCopy copyStorageNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (state.getSequenceStorage().length() != 4) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
            }

            final Object function = getItemNode.execute(inliningTarget, state, 0);
            final Object fnArgs = getItemNode.execute(inliningTarget, state, 1);
            final Object fnKwargs = getItemNode.execute(inliningTarget, state, 2);
            final Object dict = getItemNode.execute(inliningTarget, state, 3);

            if (!callableCheckNode.execute(inliningTarget, function) ||
                            !PGuards.isPTuple(fnArgs) ||
                            (fnKwargs != PNone.NONE && !PGuards.isDict(fnKwargs))) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
            }

            self.setFn(function);

            final PTuple fnArgsTuple;
            if (!tupleCheckExactNode.execute(inliningTarget, fnArgs)) {
                fnArgsTuple = constructTupleNode.execute(frame, fnArgs);
            } else {
                fnArgsTuple = (PTuple) fnArgs;
            }
            self.setArgs(inliningTarget, fnArgsTuple, storageNode, arrayNode);

            final PDict fnKwargsDict;
            if (fnKwargs == PNone.NONE) {
                fnKwargsDict = factory.createDict();
            } else if (!dictCheckExactNode.execute(inliningTarget, fnKwargs)) {
                fnKwargsDict = factory.createDict(copyStorageNode.execute(inliningTarget, ((PDict) fnKwargs).getDictStorage()));
            } else {
                fnKwargsDict = (PDict) fnKwargs;
            }
            self.setKw(fnKwargsDict);

            if (dict == PNone.NONE) {
                deleteDictNode.execute(self);
            } else {
                assert dict instanceof PDict;
                setDictNode.execute(inliningTarget, self, (PDict) dict);
            }

            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object fallback(Object self, Object state,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PartialCallNode extends PythonVarargsBuiltinNode {
        private static int indexOf(PKeyword[] keywords, PKeyword kw) {
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].getName().equals(kw.getName())) {
                    return i;
                }
            }
            return -1;
        }

        protected boolean withKeywords(PKeyword[] keywords) {
            return keywords.length > 0;
        }

        @Specialization(guards = "!self.hasKw(inliningTarget, lenNode)")
        static Object callWoDict(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile hasArgsProfile,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @SuppressWarnings("unused") @Shared @Cached HashingStorageLen lenNode) {
            Object[] callArgs = getNewPartialArgs(self, args, inliningTarget, hasArgsProfile);
            return callNode.execute(frame, self.getFn(), callArgs, keywords);
        }

        @Specialization(guards = {"self.hasKw(inliningTarget, lenNode)", "!withKeywords(keywords)"})
        static Object callWDictWoKw(VirtualFrame frame, PPartial self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExpandKeywordStarargsNode starargsNode,
                        @Shared @Cached InlinedConditionProfile hasArgsProfile,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @SuppressWarnings("unused") @Shared @Cached HashingStorageLen lenNode) {
            Object[] callArgs = getNewPartialArgs(self, args, inliningTarget, hasArgsProfile);
            return callNode.execute(frame, self.getFn(), callArgs, starargsNode.execute(inliningTarget, self.getKw()));
        }

        @Specialization(guards = {"self.hasKw(inliningTarget, lenNode)", "withKeywords(keywords)"})
        static Object callWDictWKw(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExpandKeywordStarargsNode starargsNode,
                        @Shared @Cached InlinedConditionProfile hasArgsProfile,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @SuppressWarnings("unused") @Shared @Cached HashingStorageLen lenNode) {
            Object[] callArgs = getNewPartialArgs(self, args, inliningTarget, hasArgsProfile);

            final PKeyword[] pKeywords = starargsNode.execute(inliningTarget, self.getKw());
            PKeyword[] callKeywords = PKeyword.create(pKeywords.length + keywords.length);
            PythonUtils.arraycopy(pKeywords, 0, callKeywords, 0, pKeywords.length);
            int kwIndex = pKeywords.length;
            // check for overriding keywords and store the new ones
            for (PKeyword kw : keywords) {
                int idx = indexOf(pKeywords, kw);
                if (idx == -1) {
                    callKeywords[kwIndex] = kw;
                    kwIndex += 1;
                } else {
                    callKeywords[idx] = kw;
                }
            }
            callKeywords = PythonUtils.arrayCopyOfRange(callKeywords, 0, kwIndex);

            return callNode.execute(frame, self.getFn(), callArgs, callKeywords);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PartialReprNode extends PythonUnaryBuiltinNode {
        private static void reprArgs(VirtualFrame frame, Node inliningTarget, PPartial partial, TruffleStringBuilder sb, PyObjectReprAsTruffleStringNode reprNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode) {
            for (Object arg : partial.getArgs()) {
                appendStringNode.execute(sb, T_COMMA_SPACE);
                appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, arg));
            }
        }

        private static void reprKwArgs(VirtualFrame frame, Node inliningTarget, PPartial partial, TruffleStringBuilder sb, PyObjectReprAsTruffleStringNode reprNode,
                        PyObjectStrAsTruffleStringNode strNode, HashingStorageGetIterator getHashingStorageIterator, HashingStorageIteratorNext hashingStorageIteratorNext,
                        HashingStorageIteratorKey hashingStorageIteratorKey, HashingStorageGetItem getItem, AppendStringNode appendStringNode) {
            final PDict kwDict = partial.getKw();
            if (kwDict != null) {
                HashingStorage storage = kwDict.getDictStorage();
                HashingStorageIterator it = getHashingStorageIterator.execute(inliningTarget, storage);
                while (hashingStorageIteratorNext.execute(inliningTarget, storage, it)) {
                    Object key = hashingStorageIteratorKey.execute(inliningTarget, storage, it);
                    final Object value = getItem.execute(frame, inliningTarget, storage, key);
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, strNode.execute(frame, inliningTarget, key));
                    appendStringNode.execute(sb, T_EQ);
                    appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, value));
                }
            }
        }

        @Specialization
        public static TruffleString repr(VirtualFrame frame, PPartial partial,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStrAsTruffleStringNode strNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached GetClassNode classNode,
                        @Cached TypeNodes.GetNameNode nameNode,
                        @Cached ObjectNodes.GetFullyQualifiedClassNameNode classNameNode,
                        @Cached HashingStorageGetIterator getHashingStorageIterator,
                        @Cached HashingStorageIteratorNext hashingStorageIteratorNext,
                        @Cached HashingStorageIteratorKey hashingStorageIteratorKey,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            final Object cls = classNode.execute(inliningTarget, partial);
            final TruffleString name = (cls == PythonBuiltinClassType.PPartial) ? classNameNode.execute(frame, inliningTarget, partial) : nameNode.execute(inliningTarget, cls);
            PythonContext ctxt = PythonContext.get(classNameNode);
            if (!ctxt.reprEnter(partial)) {
                return T_ELLIPSIS;
            }
            try {
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(sb, name);
                appendStringNode.execute(sb, T_LPAREN);
                appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, partial.getFn()));
                reprArgs(frame, inliningTarget, partial, sb, reprNode, appendStringNode);
                reprKwArgs(frame, inliningTarget, partial, sb, reprNode, strNode,
                                getHashingStorageIterator, hashingStorageIteratorNext, hashingStorageIteratorKey,
                                getItem, appendStringNode);
                appendStringNode.execute(sb, T_RPAREN);
                return toStringNode.execute(sb);
            } finally {
                ctxt.reprLeave(partial);
            }
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
