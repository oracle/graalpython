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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_INT_AS_R;
import static com.oracle.graal.python.nodes.ErrorMessages.ISLICE_WRONG_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.NUMBER_IS_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.STEP_FOR_ISLICE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FOR_ISLICE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COPY__;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IterNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.itertools.PAccumulate;
import com.oracle.graal.python.builtins.objects.itertools.PChain;
import com.oracle.graal.python.builtins.objects.itertools.PCombinations;
import com.oracle.graal.python.builtins.objects.itertools.PCombinationsWithReplacement;
import com.oracle.graal.python.builtins.objects.itertools.PCompress;
import com.oracle.graal.python.builtins.objects.itertools.PCount;
import com.oracle.graal.python.builtins.objects.itertools.PCycle;
import com.oracle.graal.python.builtins.objects.itertools.PDropwhile;
import com.oracle.graal.python.builtins.objects.itertools.PFilterfalse;
import com.oracle.graal.python.builtins.objects.itertools.PGroupBy;
import com.oracle.graal.python.builtins.objects.itertools.PGrouper;
import com.oracle.graal.python.builtins.objects.itertools.PIslice;
import com.oracle.graal.python.builtins.objects.itertools.PPairwise;
import com.oracle.graal.python.builtins.objects.itertools.PPermutations;
import com.oracle.graal.python.builtins.objects.itertools.PProduct;
import com.oracle.graal.python.builtins.objects.itertools.PRepeat;
import com.oracle.graal.python.builtins.objects.itertools.PStarmap;
import com.oracle.graal.python.builtins.objects.itertools.PTakewhile;
import com.oracle.graal.python.builtins.objects.itertools.PTeeDataObject;
import com.oracle.graal.python.builtins.objects.itertools.PZipLongest;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(defineModule = "itertools")
public final class ItertoolsModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ItertoolsModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "accumulate", minNumOfPositionalArgs = 2, varArgsMarker = true, parameterNames = {"cls", "iterable", "func"}, keywordOnlyNames = {
                    "initial"}, constructsClass = PythonBuiltinClassType.PAccumulate, doc = "accumulate(iterable) --> accumulate object\n\nReturn series of accumulated sums.")
    @GenerateNodeFactory
    public abstract static class AccumulateNode extends PythonBuiltinNode {

        @Specialization
        protected static PAccumulate construct(VirtualFrame frame, Object cls, Object iterable, Object func, Object initial,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyObjectGetIter getIter) {
            PAccumulate self = PFactory.createAccumulate(language, cls, getInstanceShape.execute(cls));
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setFunc(func instanceof PNone ? null : func);
            self.setTotal(null);
            self.setInitial(initial instanceof PNone ? null : initial);
            return self;
        }
    }

    @Builtin(name = "combinations", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PCombinations, parameterNames = {"cls", "iterable",
                    "r"}, doc = "combinations(iterable, r) --> combinations object\n\n" +
                                    "Return successive r-length combinations of elements in the iterable.\n\n" +
                                    "combinations(range(4), 3) --> (0,1,2), (0,1,3), (0,2,3), (1,2,3)")
    @ArgumentClinic(name = "r", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CombinationsNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.CombinationsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object iterable, int r,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedConditionProfile negativeProfile,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (negativeProfile.profile(inliningTarget, r < 0)) {
                throw raiseNode.raise(inliningTarget, ValueError, MUST_BE_NON_NEGATIVE, "r");
            }

            PCombinations self = PFactory.createCombinations(language, cls, getInstanceShape.execute(cls));
            self.setPool(toArrayNode.execute(frame, iterable));

            int[] indices = new int[r];
            indicesLoopProfile.profileCounted(r);
            for (int i = 0; indicesLoopProfile.inject(i < r); i++) {
                indices[i] = i;
            }
            self.setIndices(indices);
            self.setR(r);
            self.setLastResult(null);
            self.setStopped(r > self.getPool().length);

            return self;
        }
    }

    @Builtin(name = "combinations_with_replacement", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PCombinationsWithReplacement, parameterNames = {"cls", "iterable",
                    "r"}, doc = "combinations_with_replacement(iterable, r) --> combinations_with_replacement object\n\n" +
                                    "Return successive r-length combinations of elements in the iterable\n" +
                                    "allowing individual elements to have successive repeats.\n" +
                                    "    combinations_with_replacement('ABC', 2) --> AA AB AC BB BC CC")
    @ArgumentClinic(name = "r", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CombinationsWithReplacementNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.CombinationsWithReplacementNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object iterable, int r,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedConditionProfile negativeProfile,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (negativeProfile.profile(inliningTarget, r < 0)) {
                throw raiseNode.raise(inliningTarget, ValueError, MUST_BE_NON_NEGATIVE, "r");
            }
            PCombinationsWithReplacement self = PFactory.createCombinationsWithReplacement(language, cls, getInstanceShape.execute(cls));
            self.setPool(toArrayNode.execute(frame, iterable));
            self.setR(r);

            self.setIndices(new int[r]);
            self.setLastResult(null);
            self.setStopped(self.getPool().length == 0 && r > 0);

            return self;
        }
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PCompress, parameterNames = {"cls", "data",
                    "selectors"}, doc = "Make an iterator that filters elements from *data* returning\n" +
                                    "only those that have a corresponding element in *selectors* that evaluates to\n" +
                                    "``True``.  Stops when either the *data* or *selectors* iterables has been\n" +
                                    "exhausted.\n" +
                                    "Equivalent to::\n\n" +
                                    "\tdef compress(data, selectors):\n" +
                                    "\t\t# compress('ABCDEF', [1,0,1,0,1,1]) --> A C E F\n" +
                                    "\t\treturn (d for d, s in zip(data, selectors) if s)")
    @GenerateNodeFactory
    public abstract static class CompressNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PCompress construct(VirtualFrame frame, Object cls, Object data, Object selectors,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            PCompress self = PFactory.createCompress(language, cls, getInstanceShape.execute(cls));
            self.setData(getIter.execute(frame, inliningTarget, data));
            self.setSelectors(getIter.execute(frame, inliningTarget, selectors));
            return self;
        }
    }

    @Builtin(name = "cycle", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PCycle, doc = "Make an iterator returning elements from the iterable and\n" +
                    "    saving a copy of each. When the iterable is exhausted, return\n" +
                    "    elements from the saved copy. Repeats indefinitely.\n\n" +
                    "    Equivalent to :\n\n" +
                    "    def cycle(iterable):\n" +
                    "    \tsaved = []\n" +
                    "    \tfor element in iterable:\n" +
                    "    \t\tyield element\n" +
                    "    \t\tsaved.append(element)\n" +
                    "    \twhile saved:\n" +
                    "    \t\tfor element in saved:\n" +
                    "    \t\t\tyield element")
    @GenerateNodeFactory
    public abstract static class CycleNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PCycle construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "cycle()");
            }
            if (args.length != 1) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "cycle", 1);
            }
            Object iterable = args[0];
            PCycle self = PFactory.createCycle(language, cls, getInstanceShape.execute(cls));
            self.setSaved(new ArrayList<>());
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setIndex(0);
            self.setFirstpass(false);
            return self;
        }
    }

    @Builtin(name = "dropwhile", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDropwhile, doc = "dropwhile(predicate, iterable) --> dropwhile object\n\n" +
                    "Drop items from the iterable while predicate(item) is true.\n" +
                    "Afterwards, return every element until the iterable is exhausted.")
    @GenerateNodeFactory
    public abstract static class DropwhileNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PDropwhile construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "dropwhile()");
            }
            if (args.length != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "dropwhile", 2);
            }
            Object predicate = args[0];
            Object iterable = args[1];
            PDropwhile self = PFactory.createDropwhile(language, cls, getInstanceShape.execute(cls));
            self.setPredicate(predicate);
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setDoneDropping(false);
            return self;
        }
    }

    @Builtin(name = "filterfalse", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PFilterfalse, doc = "filterfalse(function or None, sequence) --> filterfalse object\n\n" +
                    "Return those items of sequence for which function(item) is false.\n" +
                    "If function is None, return the items that are false.")
    @GenerateNodeFactory
    public abstract static class FilterFalseNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PFilterfalse construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "filterfalse()");
            }
            if (args.length != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "filterfalse", 2);
            }
            Object func = args[0];
            Object sequence = args[1];
            PFilterfalse self = PFactory.createFilterfalse(language, cls, getInstanceShape.execute(cls));
            self.setFunc(PGuards.isPNone(func) ? null : func);
            self.setSequence(getIter.execute(frame, inliningTarget, sequence));
            return self;
        }
    }

    @Builtin(name = "groupby", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PGroupBy, parameterNames = {"cls", "iterable",
                    "key"}, doc = "Make an iterator that returns consecutive keys and groups from the\n" +
                                    "iterable. The key is a function computing a key value for each\n" +
                                    "element. If not specified or is None, key defaults to an identity\n" +
                                    "function and returns the element unchanged. Generally, the\n" +
                                    "iterable needs to already be sorted on the same key function.\n\n" +
                                    "The returned group is itself an iterator that shares the\n" +
                                    "underlying iterable with groupby(). Because the source is shared,\n" +
                                    "when the groupby object is advanced, the previous group is no\n" +
                                    "longer visible. So, if that data is needed later, it should be\n" +
                                    "stored as a list:\n\n" +
                                    "\tgroups = []\n" +
                                    "\tuniquekeys = []\n" +
                                    "\tfor k, g in groupby(data, keyfunc):\n" +
                                    "\t\tgroups.append(list(g))      # Store group iterator as a list\n" +
                                    "\t\tuniquekeys.append(k)")
    @ArgumentClinic(name = "key", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    public abstract static class GroupByNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.GroupByNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PGroupBy construct(VirtualFrame frame, Object cls, Object iterable, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            PGroupBy self = PFactory.createGroupBy(language, cls, getInstanceShape.execute(cls));
            self.setKeyFunc(PGuards.isNone(key) ? null : key);
            self.setIt(getIter.execute(frame, inliningTarget, iterable));
            return self;
        }
    }

    @Builtin(name = "_grouper", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PGrouper, parameterNames = {"$self", "parent", "tgtkey"})
    @GenerateNodeFactory
    public abstract static class GrouperNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PGrouper construct(Object cls, Object parent, Object tgtKey,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedConditionProfile isPGroupByProfile,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (!isPGroupByProfile.profile(inliningTarget, parent instanceof PGroupBy)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INCORRECT_USAGE_OF_INTERNAL_GROUPER);
            }
            return PFactory.createGrouper(language, (PGroupBy) parent, tgtKey);
        }
    }

    @Builtin(name = "takewhile", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PTakewhile, doc = "Make an iterator that returns elements from the iterable as\n" +
                    "long as the predicate is true.\n\nEquivalent to :\n\ndef takewhile(predicate, iterable):\n\tfor x in iterable:\n\t\tif predicate(x):\n\t\t\tyield x\n" +
                    "\t\telse:\n\t\t\tbreak")
    @GenerateNodeFactory
    public abstract static class TakewhileNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PTakewhile construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "takewhile()");
            }
            if (args.length != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "takewhile", 2);
            }
            Object predicate = args[0];
            Object iterable = args[1];
            PTakewhile self = PFactory.createTakewhile(language, cls, getInstanceShape.execute(cls));
            self.setPredicate(predicate);
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            return self;
        }
    }

    @Builtin(name = "tee", minNumOfPositionalArgs = 1, parameterNames = {"iterable", "n"})
    @ArgumentClinic(name = "n", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "2")
    @GenerateNodeFactory
    public abstract static class TeeNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.TeeNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        static Object negativeN(Object iterable, int n,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, S_MUST_BE_S, "n", ">=0");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        static Object zeroN(Object iterable, int n,
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        @Specialization(guards = "n > 0")
        static Object tee(VirtualFrame frame, Object iterable, int n,
                        @Bind("this") Node inliningTarget,
                        @Cached IterNode iterNode,
                        @Cached PyObjectLookupAttr getAttrNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached CallVarargsMethodNode callNode,
                        @Cached InlinedBranchProfile notCallableProfile,
                        @Bind PythonLanguage language) {
            Object it = iterNode.execute(frame, iterable, PNone.NO_VALUE);
            Object copyCallable = getAttrNode.execute(frame, inliningTarget, it, T___COPY__);
            if (!callableCheckNode.execute(inliningTarget, copyCallable)) {
                notCallableProfile.enter(inliningTarget);
                // as in Tee.__NEW__()
                PTeeDataObject dataObj = PFactory.createTeeDataObject(language, it);
                it = PFactory.createTee(language, dataObj, 0);
            }

            // return tuple([it] + [it.__copy__() for i in range(1, n)])
            Object[] tupleObjs = new Object[n];
            tupleObjs[0] = it;

            copyCallable = getAttrNode.execute(frame, inliningTarget, it, T___COPY__);
            for (int i = 1; i < n; i++) {
                tupleObjs[i] = callNode.execute(frame, copyCallable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
            }
            return PFactory.createTuple(language, tupleObjs);
        }

    }

    @Builtin(name = "_tee_dataobject", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PTeeDataObject)
    @GenerateNodeFactory
    public abstract static class TeeDataObjectNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PTeeDataObject construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind PythonLanguage language) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                errorProfile.enter(inliningTarget);
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            return PFactory.createTeeDataObject(language);
        }
    }

    @Builtin(name = "permutations", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PPermutations, parameterNames = {"cls", "iterable",
                    "r"}, doc = "permutations(iterable[, r]) --> permutations object\n\n" +
                                    "Return successive r-length permutations of elements in the iterable.\n\n" + "permutations(range(3), 2) --> (0,1), (0,2), (1,0), (1,2), (2,0), (2,1)")
    @ArgumentClinic(name = "r", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    public abstract static class PermutationsNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.PermutationsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object iterable, Object rArg,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile rIsNoneProfile,
                        @Cached ToArrayNode toArrayNode,
                        @Cached CastToJavaIntExactNode castToInt,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedBranchProfile wrongRprofile,
                        @Cached InlinedBranchProfile negRprofile,
                        @Cached InlinedConditionProfile nrProfile,
                        @Cached InlinedLoopConditionProfile indicesLoopProfile,
                        @Cached InlinedLoopConditionProfile cyclesLoopProfile,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            int r;
            Object[] pool = toArrayNode.execute(frame, iterable);
            if (rIsNoneProfile.profile(inliningTarget, PGuards.isNone(rArg))) {
                r = pool.length;
            } else {
                try {
                    r = castToInt.execute(inliningTarget, rArg);
                } catch (CannotCastException e) {
                    wrongRprofile.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, TypeError, EXPECTED_INT_AS_R);
                }
                if (r < 0) {
                    negRprofile.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, MUST_BE_NON_NEGATIVE, "r");
                }
            }
            PPermutations self = PFactory.createPermutations(language, cls, getInstanceShape.execute(cls));
            self.setPool(pool);
            self.setR(r);
            int n = pool.length;
            self.setN(n);
            int nMinusR = n - r;
            if (nrProfile.profile(inliningTarget, nMinusR < 0)) {
                self.setStopped(true);
                self.setRaisedStopIteration(true);
            } else {
                self.setStopped(false);
                int[] indices = new int[n];
                indicesLoopProfile.profileCounted(inliningTarget, indices.length);
                LoopNode.reportLoopCount(inliningTarget, indices.length);
                for (int i = 0; indicesLoopProfile.inject(inliningTarget, i < indices.length); i++) {
                    indices[i] = i;
                }
                self.setIndices(indices);
                int[] cycles = new int[r];
                int idx = 0;
                cyclesLoopProfile.profileCounted(inliningTarget, r);
                LoopNode.reportLoopCount(inliningTarget, r);
                for (int i = n; cyclesLoopProfile.inject(inliningTarget, i > nMinusR); i--) {
                    cycles[idx++] = i;
                }
                self.setCycles(cycles);
                self.setRaisedStopIteration(false);
                self.setStarted(false);
                return self;
            }
            return self;
        }
    }

    @Builtin(name = "product", minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PProduct, keywordOnlyNames = {
                    "repeat"}, doc = "Cartesian product of input iterables.\n\n" +
                                    "Equivalent to nested for-loops in a generator expression. For example,\n ``product(A, B)`` returns the same as ``((x,y) for x in A for y in B)``.\n\n" +
                                    "The nested loops cycle like an odometer with the rightmost element advancing\n on every iteration.  This pattern creates a lexicographic ordering so that if\n" +
                                    " the input's iterables are sorted, the product tuples are emitted in sorted\n order.\n\nTo compute the product of an iterable with itself, specify the number of\n" +
                                    " repetitions with the optional *repeat* keyword argument.  For example,\n ``product(A, repeat=4)`` means the same as ``product(A, A, A, A)``.\n\n" +
                                    "This function is equivalent to the following code, except that the\n actual implementation does not build up intermediate results in memory::\n\n" +
                                    "def product(*args, **kwds):\n\t# product('ABCD', 'xy') --> Ax Ay Bx By Cx Cy Dx Dy\n\t# product(range(2), repeat=3) --> 000 001 010 011 100 101 110 111\n" +
                                    "\tpools = map(tuple, args) * kwds.get('repeat', 1)\n\tresult = [[]]\n\tfor pool in pools:\n\t\tresult = [x+[y] for x in result for y in pool]\n" +
                                    "\tfor prod in result:\n\t\tyield tuple(prod)")
    @GenerateNodeFactory
    public abstract static class ProductNode extends PythonBuiltinNode {

        @Specialization(guards = "isTypeNode.execute(inliningTarget, cls)")
        static Object constructNoneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") PNone repeat,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat == 1"}, limit = "1")
        static Object constructOneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Exclusive @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat > 1"}, limit = "1")
        static Object construct(VirtualFrame frame, Object cls, Object[] iterables, int repeat,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            Object[][] lists = unpackIterables(frame, iterables, toArrayNode);
            Object[][] gears = new Object[lists.length * repeat][];
            loopProfile.profileCounted(inliningTarget, repeat);
            LoopNode.reportLoopCount(inliningTarget, repeat);
            for (int i = 0; loopProfile.inject(inliningTarget, i < repeat); i++) {
                PythonUtils.arraycopy(lists, 0, gears, i * lists.length, lists.length);
            }
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            construct(self, gears);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat == 0"}, limit = "1")
        static Object constructNoRepeat(Object cls, @SuppressWarnings("unused") Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            self.setGears(new Object[0][]);
            self.setIndices(new int[0]);
            self.setLst(null);
            self.setStopped(false);
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat < 0"}, limit = "1")
        static Object constructNeg(Object cls, Object[] iterables, int repeat,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsTypeNode isTypeNode) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ARG_CANNOT_BE_NEGATIVE, "repeat");
        }

        private static void constructOneRepeat(VirtualFrame frame, PProduct self, Object[] iterables, ToArrayNode toArrayNode) {
            Object[][] gears = unpackIterables(frame, iterables, toArrayNode);
            construct(self, gears);
        }

        private static void construct(PProduct self, Object[][] gears) {
            self.setGears(gears);
            for (int i = 0; i < gears.length; i++) {
                if (gears[i].length == 0) {
                    self.setIndices(null);
                    self.setLst(null);
                    self.setStopped(true);
                    return;
                }
            }
            self.setIndices(new int[gears.length]);
            self.setLst(null);
            self.setStopped(false);
        }

        private static Object[][] unpackIterables(VirtualFrame frame, Object[] iterables, ToArrayNode toArrayNode) {
            Object[][] lists = new Object[iterables.length][];
            for (int i = 0; i < lists.length; i++) {
                lists[i] = toArrayNode.execute(frame, iterables[i]);
            }
            return lists;
        }

        @Specialization(guards = "!isTypeNode.execute(inliningTarget, cls)")
        @SuppressWarnings("unused")
        static Object construct(Object cls, Object iterables, Object repeat,
                        @Bind("this") Node inliningTarget,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "repeat", minNumOfPositionalArgs = 2, parameterNames = {"$self", "object",
                    "times"}, constructsClass = PythonBuiltinClassType.PRepeat, doc = "repeat(object [,times]) -> create an iterator which returns the object\n" +
                                    "for the specified number of times.  If not specified, returns the object\nendlessly.")
    @GenerateNodeFactory
    public abstract static class RepeatNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object object, Object timesObj,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PRepeat self = PFactory.createRepeat(language, cls, getInstanceShape.execute(cls));
            self.setElement(object);
            if (timesObj != PNone.NO_VALUE) {
                int times = asSizeNode.executeExact(frame, inliningTarget, timesObj);
                self.setCnt(times > 0 ? times : 0);
            } else {
                self.setCnt(-1);
            }
            return self;
        }
    }

    @Builtin(name = "chain", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PChain, doc = "Return a chain object whose .__next__() method returns elements from the\n" +
                    "first iterable until it is exhausted, then elements from the next\niterable, until all of the iterables are exhausted.")
    @GenerateNodeFactory
    public abstract static class ChainNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PChain construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "chain()");
            }
            PChain self = PFactory.createChain(language, cls, getInstanceShape.execute(cls));
            self.setSource(getIter.execute(frame, inliningTarget, PFactory.createList(language, args)));
            self.setActive(PNone.NONE);
            return self;
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PCount, parameterNames = {"cls", "start", "step"})
    @ArgumentClinic(name = "start", defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "step", defaultValue = "1", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.CountNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(Object cls, Object start, Object step,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberCheckNode checkNode,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (!checkNode.execute(inliningTarget, start)) {
                throw raiseNode.raise(inliningTarget, TypeError, NUMBER_IS_REQUIRED);
            }
            if (!checkNode.execute(inliningTarget, step)) {
                throw raiseNode.raise(inliningTarget, TypeError, NUMBER_IS_REQUIRED);
            }
            PCount self = PFactory.createCount(language, cls, getInstanceShape.execute(cls));
            self.setCnt(start);
            self.setStep(step);
            return self;
        }
    }

    @Builtin(name = "starmap", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PStarmap, doc = "starmap(function, sequence) --> starmap object\n\n" +
                    "Return an iterator whose values are returned from the function evaluated\n" +
                    "with an argument tuple taken from the given sequence.")
    @GenerateNodeFactory
    public abstract static class StarmapNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PStarmap construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "starmap()");
            }
            if (args.length != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "starmap", 2);
            }
            Object fun = args[0];
            Object iterable = args[1];
            PStarmap self = PFactory.createStarmap(language, cls, getInstanceShape.execute(cls));
            self.setFun(fun);
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            return self;
        }
    }

    @Builtin(name = "islice", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PIslice)
    @GenerateNodeFactory
    public abstract static class IsliceNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object constructOne(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyNumberAsSizeNode asIntNode,
                        @Cached InlinedBranchProfile hasStart,
                        @Cached InlinedBranchProfile hasStop,
                        @Cached InlinedBranchProfile hasStep,
                        @Cached InlinedBranchProfile stopNotInt,
                        @Cached InlinedBranchProfile startNotInt,
                        @Cached InlinedBranchProfile stopWrongValue,
                        @Cached InlinedBranchProfile stepWrongValue,
                        @Cached InlinedBranchProfile wrongValue,
                        @Cached InlinedBranchProfile overflowBranch,
                        @Cached InlinedConditionProfile argsLen1,
                        @Cached InlinedConditionProfile argsLen2,
                        @Cached InlinedConditionProfile argsLen3,
                        @Cached InlinedBranchProfile wrongTypeBranch,
                        @Cached InlinedBranchProfile wrongArgsBranch,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                wrongTypeBranch.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "islice()");
            }
            if (args.length < 2 || args.length > 4) {
                wrongArgsBranch.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ISLICE_WRONG_ARGS);
            }
            int start = 0;
            int step = 1;
            int stop = -1;
            if (argsLen1.profile(inliningTarget, args.length == 2)) {
                if (args[1] != PNone.NONE) {
                    hasStop.enter(inliningTarget);
                    try {
                        stop = asIntNode.executeExact(frame, inliningTarget, args[1], OverflowError);
                    } catch (PException e) {
                        stopNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                    }
                }
                if (stop < -1 || stop > SysModuleBuiltins.MAXSIZE) {
                    stopWrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            } else if (argsLen2.profile(inliningTarget, args.length == 3) || argsLen3.profile(inliningTarget, args.length == 4)) {
                if (args[1] != PNone.NONE) {
                    hasStart.enter(inliningTarget);
                    try {
                        start = asIntNode.executeExact(frame, inliningTarget, args[1], OverflowError);
                    } catch (PException e) {
                        startNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                    }
                }
                if (args[2] != PNone.NONE) {
                    hasStop.enter(inliningTarget);
                    try {
                        stop = asIntNode.executeExact(frame, inliningTarget, args[2], OverflowError);
                    } catch (PException e) {
                        stopNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Stop argument");
                    }
                }
                if (start < 0 || stop < -1 || start > SysModuleBuiltins.MAXSIZE || stop > SysModuleBuiltins.MAXSIZE) {
                    wrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            }
            if (argsLen3.profile(inliningTarget, args.length == 4)) {
                if (args[3] != PNone.NONE) {
                    hasStep.enter(inliningTarget);
                    try {
                        step = asIntNode.executeExact(frame, inliningTarget, args[3], OverflowError);
                    } catch (PException e) {
                        overflowBranch.enter(inliningTarget);
                        step = -1;
                    }
                }
                if (step < 1) {
                    stepWrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, STEP_FOR_ISLICE_MUST_BE);
                }
            }
            Object iterable = args[0];
            PIslice self = PFactory.createIslice(language, cls, getInstanceShape.execute(cls));
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setNext(start);
            self.setStop(stop);
            self.setStep(step);
            self.setCnt(0);
            return self;
        }
    }

    @Builtin(name = "zip_longest", minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PZipLongest, keywordOnlyNames = {
                    "fillvalue"}, doc = "zip_longest(iter1 [,iter2 [...]], [fillvalue=None]) --> zip_longest object\n\n" +
                                    "Return a zip_longest object whose .next() method returns a tuple where\n" +
                                    "the i-th element comes from the i-th iterable argument.  The .next()\n" +
                                    "method continues until the longest iterable in the argument sequence\n" +
                                    "is exhausted and then it raises StopIteration.  When the shorter iterables\n" +
                                    "are exhausted, the fillvalue is substituted in their place.  The fillvalue\n" +
                                    "defaults to None or can be specified by a keyword argument.")
    @GenerateNodeFactory
    public abstract static class ZipLongestNode extends PythonBuiltinNode {
        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object[] args, Object fillValueIn,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIterNode,
                        @Cached InlinedConditionProfile fillIsNone,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached IsTypeNode isTypeNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                // Note: @Fallback or other @Specialization generate data-class
                errorProfile.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }

            Object fillValue = fillValueIn;
            if (fillIsNone.profile(inliningTarget, PGuards.isPNone(fillValue))) {
                fillValue = null;
            }

            PZipLongest self = PFactory.createZipLongest(language, cls, getInstanceShape.execute(cls));
            self.setFillValue(fillValue);
            self.setNumActive(args.length);

            Object[] itTuple = new Object[args.length];
            loopProfile.profileCounted(inliningTarget, itTuple.length);
            LoopNode.reportLoopCount(inliningTarget, itTuple.length);
            for (int i = 0; loopProfile.inject(inliningTarget, i < itTuple.length); i++) {
                itTuple[i] = getIterNode.execute(frame, inliningTarget, args[i]);
            }
            self.setItTuple(itTuple);
            return self;
        }
    }

    @Builtin(name = "pairwise", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PPairwise, doc = "Return an iterator of overlapping pairs taken from the input iterator.\n\n" +
                    "    s -> (s0,s1), (s1,s2), (s2, s3), ...")
    @GenerateNodeFactory
    public abstract static class PairwaiseNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PPairwise construct(VirtualFrame frame, Object cls, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                // Note: @Fallback or other @Specialization generate data-class
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }

            PPairwise self = PFactory.createPairwise(language, cls, getInstanceShape.execute(cls));
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            return self;
        }
    }
}
