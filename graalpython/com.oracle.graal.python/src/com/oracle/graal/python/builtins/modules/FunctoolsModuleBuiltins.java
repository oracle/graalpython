/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.objects.partial.PartialBuiltins.getNewPartialArgs;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARTIAL;
import static com.oracle.graal.python.nodes.ErrorMessages.REDUCE_EMPTY_SEQ;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_N_MUST_SUPPORT_ITERATION;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_S_TAKES_AT_LEAST_ONE_ARGUMENT;
import static com.oracle.truffle.api.nodes.LoopNode.reportLoopCount;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.partial.PPartial;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "_functools")
public class FunctoolsModuleBuiltins extends PythonBuiltins {
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
        @Specialization(guards = "isNoValue(initial)")
        Object doReduceNoInitial(VirtualFrame frame, Object function, Object sequence, @SuppressWarnings("unused") PNone initial,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile stopIterProfile,
                        @Cached IsBuiltinClassProfile typeError) {
            return doReduce(frame, function, sequence, null, getIter, nextNode, callNode, stopIterProfile, typeError);
        }

        @Specialization(guards = "!isNoValue(initial)")
        Object doReduce(VirtualFrame frame, Object function, Object sequence, Object initial,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile stopIterProfile,
                        @Cached IsBuiltinClassProfile typeError) {
            Object seqIterator, result = initial;
            try {
                seqIterator = getIter.execute(frame, sequence);
            } catch (PException pe) {
                pe.expectTypeError(typeError);
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
                    count++;
                } catch (PException e) {
                    e.expectStopIteration(stopIterProfile);
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
        protected boolean isPartialWithoutDict(GetDictIfExistsNode getDict, Object[] args, HashingStorageLibrary lib, boolean withKwDict) {
            return isPartialWithoutDict(getDict, args) && withKwDict == ((PPartial) args[0]).hasKw(lib);
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

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lib, false)"})
        Object createFromPartialWoDictWoKw(Object cls, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached ConditionProfile hasKeywordsProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, hasArgsProfile, 1);

            PDict funcKwDict;
            if (hasKeywordsProfile.profile(keywords.length > 0)) {
                funcKwDict = factory().createDict(keywords);
            } else {
                funcKwDict = factory().createDict();
            }

            return factory().createPartial(cls, function.getFn(), funcArgs, funcKwDict);
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lib, true)", "!withKeywords(keywords)"})
        Object createFromPartialWoDictWKw(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached ConditionProfile hasArgsProfile,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, hasArgsProfile, 1);
            return factory().createPartial(cls, function.getFn(), funcArgs, function.getKwCopy(factory(), lib));
        }

        @Specialization(guards = {"atLeastOneArg(args)", "isPartialWithoutDict(getDict, args, lib, true)", "withKeywords(keywords)"})
        Object createFromPartialWoDictWKwKw(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached HashingStorage.InitNode initNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            assert args[0] instanceof PPartial;
            final PPartial function = (PPartial) args[0];
            Object[] funcArgs = getNewPartialArgs(function, args, hasArgsProfile, 1);

            HashingStorage storage = function.getKw().getDictStorage();
            storage = lib.addAllToOther(initNode.execute(frame, PNone.NO_VALUE, keywords), storage);

            return factory().createPartial(cls, function.getFn(), funcArgs, factory().createDict(storage));
        }

        @Specialization(guards = {"atLeastOneArg(args)", "!isPartialWithoutDict(getDict, args)"})
        Object createGeneric(Object cls, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                        @Cached ConditionProfile hasKeywordsProfile,
                        @Cached PyCallableCheckNode callableCheckNode) {
            Object function = args[0];
            if (!callableCheckNode.execute(function)) {
                throw raise(PythonBuiltinClassType.TypeError, S_ARG_MUST_BE_CALLABLE, "the first");
            }

            final Object[] funcArgs = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            PDict funcKwDict;
            if (hasKeywordsProfile.profile(keywords.length > 0)) {
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
