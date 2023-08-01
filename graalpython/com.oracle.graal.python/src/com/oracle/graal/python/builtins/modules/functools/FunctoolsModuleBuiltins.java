/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PythonObject;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARTIAL;
import static com.oracle.graal.python.nodes.ErrorMessages.REDUCE_EMPTY_SEQ;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_N_MUST_SUPPORT_ITERATION;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_S_TAKES_AT_LEAST_ONE_ARGUMENT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.truffle.api.nodes.LoopNode.reportLoopCount;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(defineModule = "_functools")
public final class FunctoolsModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctoolsModuleBuiltinsFactory.getFactories();
    }

    // functools.reduce(function, iterable[, initializer])
    @Builtin(name = "reduce", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, doc = "reduce(function, sequence[, initial]) -> value\n" +
                    "\n" +
                    "Apply a function of two arguments cumulatively to the items of a sequence,\n" +
                    "from left to right, so as to reduce the sequence to a single value.\n" +
                    "For example, reduce(lambda x, y: x+y, [1, 2, 3, 4, 5]) calculates\n" +
                    "((((1+2)+3)+4)+5).  If initial is present, it is placed before the items\n" +
                    "of the sequence in the calculation, and serves as a default when the\n" +
                    "sequence is empty.")
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doReduce(VirtualFrame frame, Object function, Object sequence, Object initialIn,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached InlinedConditionProfile initialNoValueProfile,
                        @Cached IsBuiltinObjectProfile stopIterProfile,
                        @Cached IsBuiltinObjectProfile typeError) {
            Object initial = initialNoValueProfile.profile(inliningTarget, PGuards.isNoValue(initialIn)) ? null : initialIn;
            Object seqIterator, result = initial;
            try {
                seqIterator = getIter.execute(frame, sequence);
            } catch (PException pe) {
                pe.expectTypeError(inliningTarget, typeError);
                throw raise(PythonBuiltinClassType.TypeError, S_ARG_N_MUST_SUPPORT_ITERATION, "reduce()", 2);
            }

            Object[] args = new Object[2];

            int count = 0;
            while (true) {
                Object op2;
                try {
                    op2 = nextNode.execute(frame, seqIterator);
                    if (result == null) {
                        result = op2;
                    } else {
                        // Update the args tuple in-place
                        args[0] = result;
                        args[1] = op2;
                        result = callNode.execute(frame, function, args);
                    }
                    if (CompilerDirectives.hasNextTier()) {
                        count++;
                    }
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterProfile);
                    break;
                }
            }
            reportLoopCount(this, count >= 0 ? count : Integer.MAX_VALUE);

            if (result == null) {
                throw raise(PythonBuiltinClassType.TypeError, REDUCE_EMPTY_SEQ);
            }

            return result;
        }
    }

    // functools.cmp_to_key(func)
    @Builtin(name = "cmp_to_key", minNumOfPositionalArgs = 1, parameterNames = {"mycmp"}, doc = "Convert a cmp= function into a key= function.")
    @GenerateNodeFactory
    public abstract static class CmpToKeyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doConvert(Object myCmp) {
            return factory().createKeyWrapper(myCmp);
        }
    }

    // functools.partial(func, /, *args, **keywords)
    @Builtin(name = J_PARTIAL, minNumOfPositionalArgs = 1, varArgsMarker = true, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PPartial, doc = "partial(func, *args, **keywords) - new function with partial application\n" +
                    "of the given arguments and keywords.\n")
    @GenerateNodeFactory
    public abstract static class PartialNode extends PythonBuiltinNode {
        protected boolean isPartialWithoutDict(GetDictIfExistsNode getDict, Object[] args, HashingStorageLen lenNode, boolean withKwDict) {
            return isPartialWithoutDict(getDict, args) && withKwDict == ((PPartial) args[0]).hasKw(lenNode);
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

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lenNode, false)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object createFromPartialWoDictWoKw(Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached InlinedConditionProfile hasArgsProfile,
                        @Cached InlinedConditionProfile hasKeywordsProfile,
                        @SuppressWarnings("unused") @Cached HashingStorageLen lenNode) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);

            PDict funcKwDict;
            if (hasKeywordsProfile.profile(inliningTarget, keywords.length > 0)) {
                funcKwDict = factory().createDict(keywords);
            } else {
                funcKwDict = factory().createDict();
            }

            return factory().createPartial(cls, function.getFn(), funcArgs, funcKwDict);
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lenNode, true)", "!withKeywords(keywords)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object createFromPartialWoDictWKw(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached InlinedConditionProfile hasArgsProfile,
                        @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageCopy copyNode) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);
            return factory().createPartial(cls, function.getFn(), funcArgs, function.getKwCopy(factory(), copyNode));
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lenNode, true)", "withKeywords(keywords)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object createFromPartialWoDictWKwKw(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached InlinedConditionProfile hasArgsProfile,
                        @Cached HashingStorage.InitNode initNode,
                        @SuppressWarnings("unused") @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageCopy copyHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, inliningTarget, hasArgsProfile, 1);

            HashingStorage storage = copyHashingStorageNode.execute(function.getKw().getDictStorage());
            PDict result = factory().createDict(storage);
            addAllToOtherNode.execute(frame, initNode.execute(frame, PNone.NO_VALUE, keywords), result);

            return factory().createPartial(cls, function.getFn(), funcArgs, result);
        }

        @Specialization(guards = {"atLeastOneArg(args)", "!isPartialWithoutDict(getDict, args)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object createGeneric(Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached InlinedConditionProfile hasKeywordsProfile,
                        @Cached PyCallableCheckNode callableCheckNode) {
            Object function = args[0];
            if (!callableCheckNode.execute(function)) {
                throw raise(PythonBuiltinClassType.TypeError, S_ARG_MUST_BE_CALLABLE, "the first");
            }

            final Object[] funcArgs = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            PDict funcKwDict;
            if (hasKeywordsProfile.profile(inliningTarget, keywords.length > 0)) {
                funcKwDict = factory().createDict(keywords);
            } else {
                funcKwDict = factory().createDict();
            }
            return factory().createPartial(cls, function, funcArgs, funcKwDict);
        }

        @Specialization(guards = "!atLeastOneArg(args)")
        @SuppressWarnings("unused")
        Object noCallable(Object cls, Object[] args, PKeyword[] keywords) {
            throw raise(PythonBuiltinClassType.TypeError, TYPE_S_TAKES_AT_LEAST_ONE_ARGUMENT, "partial");
        }
    }
}

@CoreFunctions(extendClasses = PythonBuiltinClassType.LsprofProfiler)
class ProfilerBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProfilerBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "timer", "timeunit", "subcalls", "builtins"})
    @GenerateNodeFactory
    abstract static class Init extends PythonBuiltinNode {
        @Specialization
        PNone doit(Profiler self, Object timer, double timeunit, long subcalls, long builtins) {
            self.subcalls = subcalls > 0;
            self.builtins = builtins > 0;
            self.timeunit = timeunit;
            self.externalTimer = timer;
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        PNone doit(Profiler self, Object timer, PNone timeunit, PNone subcalls, PNone builtins) {
            self.subcalls = true;
            self.builtins = true;
            self.timeunit = -1;
            self.externalTimer = timer;
            return PNone.NONE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 1, parameterNames = {"$self", "subcalls", "builtins"})
    @GenerateNodeFactory
    abstract static class Enable extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone doit(Profiler self, long subcalls, long builtins) {
            self.subcalls = subcalls > 0;
            self.builtins = builtins > 0;
            // TODO: deal with any arguments
            self.time = System.currentTimeMillis();
            self.sampler.setCollecting(true);
            return PNone.NONE;
        }

        @Specialization
        PNone doit(Profiler self, long subcalls, @SuppressWarnings("unused") PNone builtins) {
            return doit(self, subcalls, self.builtins ? 1 : 0);
        }

        @Specialization
        PNone doit(Profiler self, @SuppressWarnings("unused") PNone subcalls, long builtins) {
            return doit(self, self.subcalls ? 1 : 0, builtins);
        }

        @Specialization
        PNone doit(Profiler self, @SuppressWarnings("unused") PNone subcalls, @SuppressWarnings("unused") PNone builtins) {
            return doit(self, self.subcalls ? 1 : 0, self.builtins ? 1 : 0);
        }
    }
}
