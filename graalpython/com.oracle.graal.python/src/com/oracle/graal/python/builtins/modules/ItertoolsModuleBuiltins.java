/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.ErrorMessages.S_EXPECTED_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FOR_ISLICE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;

import java.util.ArrayList;
import java.util.List;

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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
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
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
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

        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PAccumulate construct(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone func, @SuppressWarnings("unused") PNone initial,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return create(frame, cls, iterable, null, null, getIter);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(initial)"})
        protected PAccumulate construct(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone func, Object initial,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return create(frame, cls, iterable, null, initial, getIter);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(func)"})
        protected PAccumulate construct(VirtualFrame frame, Object cls, Object iterable, Object func, @SuppressWarnings("unused") PNone initial,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return create(frame, cls, iterable, func, null, getIter);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(func)", "!isNone(initial)"})
        protected PAccumulate construct(VirtualFrame frame, Object cls, Object iterable, Object func, Object initial,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return create(frame, cls, iterable, func, initial, getIter);
        }

        private PAccumulate create(VirtualFrame frame, Object cls, Object iterable, Object func, Object initial, PyObjectGetIter getIter) {
            PAccumulate self = factory().createAccumulate(cls);
            self.setIterable(getIter.execute(frame, iterable));
            self.setFunc(func instanceof PNone ? null : func);
            self.setTotal(null);
            self.setInitial(initial instanceof PNone ? null : initial);
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object iterable, Object func, Object initial,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = {"isTypeNode.execute(cls)", "r >= 0"})
        Object construct(VirtualFrame frame, Object cls, Object iterable, int r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached LoopConditionProfile indicesLoopProfile) {
            PCombinations self = factory().createCombinations(cls);
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

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "r < 0"})
        Object construct(Object cls, Object iterable, int r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(ValueError, MUST_BE_NON_NEGATIVE, "r");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, Object iterable, Object r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = {"isTypeNode.execute(cls)", "r >= 0"})
        Object construct(VirtualFrame frame, Object cls, Object iterable, int r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode,
                        @Cached ToArrayNode toArrayNode) {
            PCombinationsWithReplacement self = factory().createCombinationsWithReplacement(cls);
            self.setPool(toArrayNode.execute(frame, iterable));
            self.setR(r);

            self.setIndices(new int[r]);
            self.setLastResult(null);
            self.setStopped(self.getPool().length == 0 && r > 0);

            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "r < 0"})
        Object construct(Object cls, Object iterable, int r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(ValueError, MUST_BE_NON_NEGATIVE, "r");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, Object iterable, Object r,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PCompress construct(VirtualFrame frame, Object cls, Object data, Object selectors,
                        @Cached PyObjectGetIter getIter,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PCompress self = factory().createCompress(cls);
            self.setData(getIter.execute(frame, data));
            self.setSelectors(getIter.execute(frame, selectors));
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "cycle", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PCycle, doc = "Make an iterator returning elements from the iterable and\n" +
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
    public abstract static class CycleNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PCycle construct(VirtualFrame frame, Object cls, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PCycle self = factory().createCycle(cls);
            self.setSaved(new ArrayList<>());
            self.setIterable(getIter.execute(frame, iterable));
            self.setIndex(0);
            self.setFirstpass(false);
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object iterable,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "dropwhile", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PDropwhile, doc = "dropwhile(predicate, iterable) --> dropwhile object\n\n" +
                    "Drop items from the iterable while predicate(item) is true.\n" +
                    "Afterwards, return every element until the iterable is exhausted.")
    @GenerateNodeFactory
    public abstract static class DropwhileNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PDropwhile construct(VirtualFrame frame, Object cls, Object predicate, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PDropwhile self = factory().createDropwhile(cls);
            self.setPredicate(predicate);
            self.setIterable(getIter.execute(frame, iterable));
            self.setDoneDropping(false);
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object predicate, Object iterable,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "filterfalse", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PFilterfalse, doc = "filterfalse(function or None, sequence) --> filterfalse object\n\n" +
                    "Return those items of sequence for which function(item) is false.\n" +
                    "If function is None, return the items that are false.")
    @GenerateNodeFactory
    public abstract static class FilterFalseNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PFilterfalse constructNoFunc(VirtualFrame frame, Object cls, @SuppressWarnings("unused") PNone func, Object sequence,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return construct(frame, cls, null, sequence, getIter, isTypeNode);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(func)", "!isNoValue(func)"})
        protected PFilterfalse construct(VirtualFrame frame, Object cls, Object func, Object sequence,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PFilterfalse self = factory().createFilterfalse(cls);
            self.setFunc(func);
            self.setSequence(getIter.execute(frame, sequence));
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = {"isTypeNode.execute(cls)", "isNone(key)"})
        protected PGroupBy constructNoType(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone key,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return construct(frame, cls, iterable, null, getIter, isTypeNode);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(key)"})
        protected PGroupBy construct(VirtualFrame frame, Object cls, Object iterable, Object key,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PGroupBy self = factory().createGroupBy(cls);
            self.setKeyFunc(key);
            self.setIt(getIter.execute(frame, iterable));
            return self;
        }

        @Specialization(guards = "isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object iterable, @SuppressWarnings("unused") PNone key,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "_grouper", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PGrouper, parameterNames = {"$self", "parent", "tgtkey"})
    @GenerateNodeFactory
    public abstract static class GrouperNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PGrouper construct(Object cls, PGroupBy parent, Object tgtKey,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return factory().createGrouper(parent, tgtKey);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "!isGroupBy(parent)"})
        Object construct(Object cls, Object parent, Object tgtky,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.INCORRECT_USAGE_OF_INTERNAL_GROUPER);
        }

        protected boolean isGroupBy(Object obj) {
            return obj instanceof PGroupBy;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, PGroupBy parent, Object tgtKey,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "takewhile", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PTakewhile, doc = "Make an iterator that returns elements from the iterable as\n" +
                    "long as the predicate is true.\n\nEquivalent to :\n\ndef takewhile(predicate, iterable):\n\tfor x in iterable:\n\t\tif predicate(x):\n\t\t\tyield x\n" +
                    "\t\telse:\n\t\t\tbreak")
    @GenerateNodeFactory
    public abstract static class TakewhileNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PTakewhile construct(VirtualFrame frame, Object cls, Object predicate, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PTakewhile self = factory().createTakewhile(cls);
            self.setPredicate(predicate);
            self.setIterable(getIter.execute(frame, iterable));
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object predicate, Object iterable,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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
        protected Object negativeN(Object iterable, int n) {
            throw raise(ValueError, S_MUST_BE_S, "n", ">=0");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        protected Object zeroN(Object iterable, int n) {
            return factory().createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        @Specialization(guards = "n > 0")
        @SuppressWarnings("truffle-static-method")
        protected Object tee(VirtualFrame frame, Object iterable, int n,
                        @Bind("this") Node inliningTarget,
                        @Cached IterNode iterNode,
                        @Cached PyObjectLookupAttr getAttrNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached CallVarargsMethodNode callNode,
                        @Cached InlinedBranchProfile notCallableProfile) {
            Object it = iterNode.execute(frame, iterable, PNone.NO_VALUE);
            Object copyCallable = getAttrNode.execute(frame, it, T___COPY__);
            if (!callableCheckNode.execute(copyCallable)) {
                notCallableProfile.enter(inliningTarget);
                // as in Tee.__NEW__()
                PTeeDataObject dataObj = factory().createTeeDataObject(it);
                it = factory().createTee(dataObj, 0);
            }

            // return tuple([it] + [it.__copy__() for i in range(1, n)])
            Object[] tupleObjs = new Object[n];
            tupleObjs[0] = it;

            copyCallable = getAttrNode.execute(frame, it, T___COPY__);
            for (int i = 1; i < n; i++) {
                tupleObjs[i] = callNode.execute(frame, copyCallable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
            }
            return factory().createTuple(tupleObjs);
        }

    }

    @Builtin(name = "_tee_dataobject", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PTeeDataObject)
    @GenerateNodeFactory
    public abstract static class TeeDataObjectNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PTeeDataObject construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return factory().createTeeDataObject();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = {"isTypeNode.execute(cls)", "isNone(r)"})
        Object constructNoR(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone r,
                        @Bind("this") Node inliningTarget,
                        @Cached ToArrayNode toArrayNode,
                        @Cached InlinedConditionProfile nrProfile,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached LoopConditionProfile cyclesLoopProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            Object[] pool = toArrayNode.execute(frame, iterable);
            return construct(cls, pool, pool.length, inliningTarget, nrProfile, indicesLoopProfile, cyclesLoopProfile);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(rArg)"})
        @SuppressWarnings("truffle-static-method")
        Object construct(VirtualFrame frame, Object cls, Object iterable, Object rArg,
                        @Bind("this") Node inliningTarget,
                        @Cached ToArrayNode toArrayNode,
                        @Cached CastToJavaIntExactNode castToInt,
                        @Cached InlinedBranchProfile wrongRprofile,
                        @Cached InlinedBranchProfile negRprofile,
                        @Cached InlinedConditionProfile nrProfile,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached LoopConditionProfile cyclesLoopProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            int r;
            try {
                r = castToInt.execute(rArg);
            } catch (CannotCastException e) {
                wrongRprofile.enter(inliningTarget);
                throw raise(TypeError, EXPECTED_INT_AS_R);
            }
            if (r < 0) {
                negRprofile.enter(inliningTarget);
                throw raise(ValueError, MUST_BE_NON_NEGATIVE, "r");
            }
            Object[] pool = toArrayNode.execute(frame, iterable);
            return construct(cls, pool, r, inliningTarget, nrProfile, indicesLoopProfile, cyclesLoopProfile);
        }

        public PPermutations construct(Object cls, Object[] pool, int r, Node inliningTarget, InlinedConditionProfile nrProfile,
                        LoopConditionProfile indicesLoopProfile, LoopConditionProfile cyclesLoopProfile) {
            PPermutations self = factory().createPermutations(cls);
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
                indicesLoopProfile.profileCounted(indices.length);
                for (int i = 0; indicesLoopProfile.inject(i < indices.length); i++) {
                    indices[i] = i;
                }
                self.setIndices(indices);
                int[] cycles = new int[r];
                int idx = 0;
                cyclesLoopProfile.profileCounted(nMinusR);
                for (int i = n; cyclesLoopProfile.inject(i > nMinusR); i--) {
                    cycles[idx++] = i;
                }
                self.setCycles(cycles);
                self.setRaisedStopIteration(false);
                self.setStarted(false);
                return self;
            }
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = "isTypeNode.execute(cls)")
        Object constructNoneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") PNone repeat,
                        @Cached ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PProduct self = factory().createProduct(cls);
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "repeat == 1"})
        Object constructOneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @Cached ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PProduct self = factory().createProduct(cls);
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "repeat > 1"})
        Object construct(VirtualFrame frame, Object cls, Object[] iterables, int repeat,
                        @Cached ToArrayNode toArrayNode,
                        @Cached LoopConditionProfile loopProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            Object[][] lists = unpackIterables(frame, iterables, toArrayNode);
            Object[][] gears = new Object[lists.length * repeat][];
            loopProfile.profileCounted(repeat);
            for (int i = 0; loopProfile.inject(i < repeat); i++) {
                PythonUtils.arraycopy(lists, 0, gears, i * lists.length, lists.length);
            }
            PProduct self = factory().createProduct(cls);
            construct(self, gears);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "repeat == 0"})
        Object constructNoRepeat(Object cls, @SuppressWarnings("unused") Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PProduct self = factory().createProduct(cls);
            self.setGears(new Object[0][]);
            self.setIndices(new int[0]);
            self.setLst(null);
            self.setStopped(false);
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "repeat < 0"})
        Object constructNeg(Object cls, Object[] iterables, int repeat,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ARG_CANNOT_BE_NEGATIVE, "repeat");
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

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object iterables, Object repeat,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "repeat", minNumOfPositionalArgs = 2, parameterNames = {"$self", "object",
                    "times"}, constructsClass = PythonBuiltinClassType.PRepeat, doc = "repeat(object [,times]) -> create an iterator which returns the object\n" +
                                    "for the specified number of times.  If not specified, returns the object\nendlessly.")
    @ArgumentClinic(name = "times", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class RepeatNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.RepeatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "isTypeNode.execute(cls)")
        Object constructNone(Object cls, Object object, @SuppressWarnings("unused") PNone times,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PRepeat self = factory().createRepeat(cls);
            self.setElement(object);
            self.setCnt(-1);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "times < 0"})
        Object constructNeg(Object cls, Object object, @SuppressWarnings("unused") int times,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PRepeat self = factory().createRepeat(cls);
            self.setElement(object);
            self.setCnt(0);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "times >= 0"})
        Object construct(Object cls, Object object, int times,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PRepeat self = factory().createRepeat(cls);
            self.setElement(object);
            self.setCnt(times);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "times >= 0"})
        Object construct(VirtualFrame frame, Object cls, Object object, long times,
                        @Cached PyLongAsIntNode asIntNode,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PRepeat self = factory().createRepeat(cls);
            self.setElement(object);
            self.setCnt(asIntNode.execute(frame, times));
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNone(times)", "!isInt(times)", "!isLong(times)"})
        Object construct(Object cls, Object object, Object times,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, S_EXPECTED_GOT_P, "integer", times);
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object object, Object times,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }

    }

    @Builtin(name = "chain", minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PChain, doc = "Return a chain object whose .__next__() method returns elements from the\n" +
                    "first iterable until it is exhausted, then elements from the next\niterable, until all of the iterables are exhausted.")
    @GenerateNodeFactory
    public abstract static class ChainNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PChain construct(VirtualFrame frame, Object cls, Object[] iterables,
                        @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PChain self = factory().createChain(cls);
            self.setSource(getIter.execute(frame, factory().createList(iterables)));
            self.setActive(PNone.NONE);
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, Object[] iterables,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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

        @Specialization(guards = "isTypeNode.execute(cls)")
        Object construct(VirtualFrame frame, Object cls, Object start, Object step,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached InlinedBranchProfile startNumberProfile,
                        @Cached InlinedBranchProfile stepNumberProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PCount self = factory().createCount(cls);
            checkType(frame, inliningTarget, start, typeCheckNode, lookupAttrNode, startNumberProfile);
            checkType(frame, inliningTarget, step, typeCheckNode, lookupAttrNode, stepNumberProfile);
            self.setCnt(start);
            self.setStep(step);
            return self;
        }

        private void checkType(VirtualFrame frame, Node inliningTarget, Object obj, PyObjectTypeCheck typeCheckNode, PyObjectLookupAttr lookupAttrNode, InlinedBranchProfile isNumberProfile) {
            if (typeCheckNode.execute(obj, PythonBuiltinClassType.PComplex) ||
                            lookupAttrNode.execute(frame, obj, T___INDEX__) != PNone.NO_VALUE ||
                            lookupAttrNode.execute(frame, obj, T___FLOAT__) != PNone.NO_VALUE ||
                            lookupAttrNode.execute(frame, obj, T___INT__) != PNone.NO_VALUE) {
                isNumberProfile.enter(inliningTarget);
                return;
            }
            throw raise(TypeError, NUMBER_IS_REQUIRED);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object construct(Object cls, Object start, Object step,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "starmap", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PStarmap, doc = "starmap(function, sequence) --> starmap object\n\n" +
                    "Return an iterator whose values are returned from the function evaluated\n" +
                    "with an argument tuple taken from the given sequence.")
    @GenerateNodeFactory
    public abstract static class StarmapNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PStarmap construct(VirtualFrame frame, Object cls, Object fun, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PStarmap self = factory().createStarmap(cls);
            self.setFun(fun);
            self.setIterable(getIter.execute(frame, iterable));
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object construct(Object cls, Object fun, Object iterable,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "islice", minNumOfPositionalArgs = 2, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PIslice)
    @GenerateNodeFactory
    public abstract static class IsliceNode extends PythonBuiltinNode {

        private static class StartStop {
            int start = 0;
            int stop = -1;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "args.length == 1"})
        Object constructOne(VirtualFrame frame, Object cls, Object iterable, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PyNumberAsSizeNode asIntNode,
                        @Cached InlinedBranchProfile hasStop,
                        @Cached InlinedBranchProfile stopNotInt,
                        @Cached InlinedBranchProfile stopWrongValue,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            int stop = -1;
            if (args[0] != PNone.NONE) {
                hasStop.enter(inliningTarget);
                try {
                    stop = asIntNode.executeExact(frame, args[0], OverflowError);
                } catch (PException e) {
                    stopNotInt.enter(inliningTarget);
                    throw raise(ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            }
            if (stop < -1 || stop > SysModuleBuiltins.MAXSIZE) {
                stopWrongValue.enter(inliningTarget);
                throw raise(ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
            }
            PIslice self = factory().createIslice(cls);
            populateSelf(self, getIter, frame, iterable, 0, stop, 1);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "args.length == 2"})
        Object constructTwo(VirtualFrame frame, Object cls, Object iterable, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PyNumberAsSizeNode asIntNode,
                        @Cached InlinedBranchProfile hasStart,
                        @Cached InlinedBranchProfile hasStop,
                        @Cached InlinedBranchProfile startNotInt,
                        @Cached InlinedBranchProfile stopNotInt,
                        @Cached InlinedBranchProfile wrongValue,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            StartStop ss = getStartStop(frame, args, inliningTarget, asIntNode, hasStart, hasStop, startNotInt, stopNotInt, wrongValue);
            PIslice self = factory().createIslice(cls);
            populateSelf(self, getIter, frame, iterable, ss.start, ss.stop, 1);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "args.length == 3"})
        Object constructThree(VirtualFrame frame, Object cls, Object iterable, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PyNumberAsSizeNode asIntNode,
                        @Cached InlinedBranchProfile hasStart,
                        @Cached InlinedBranchProfile hasStop,
                        @Cached InlinedBranchProfile hasStep,
                        @Cached InlinedBranchProfile startNotInt,
                        @Cached InlinedBranchProfile stopNotInt,
                        @Cached InlinedBranchProfile wrongValue,
                        @Cached InlinedBranchProfile stepWrongValue,
                        @Cached InlinedBranchProfile overflowBranch,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            StartStop ss = getStartStop(frame, args, inliningTarget, asIntNode, hasStart, hasStop, startNotInt, stopNotInt, wrongValue);
            int step = 1;

            if (args[2] != PNone.NONE) {
                hasStep.enter(inliningTarget);
                try {
                    step = asIntNode.executeExact(frame, args[2], OverflowError);
                } catch (PException e) {
                    overflowBranch.enter(inliningTarget);
                    step = -1;
                }
            }
            if (step < 1) {
                stepWrongValue.enter(inliningTarget);
                throw raise(ValueError, STEP_FOR_ISLICE_MUST_BE);
            }
            PIslice self = factory().createIslice(cls);
            populateSelf(self, getIter, frame, iterable, ss.start, ss.stop, step);
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(cls)", "args.length < 1 || args.length > 3"})
        Object notype(Object cls, Object iterable, Object[] args,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ISLICE_WRONG_ARGS);
        }

        private StartStop getStartStop(VirtualFrame frame, Object[] args, Node inliningTarget, PyNumberAsSizeNode asIntNode,
                        InlinedBranchProfile hasStart, InlinedBranchProfile hasStop, InlinedBranchProfile startNotInt,
                        InlinedBranchProfile stopNotInt, InlinedBranchProfile wrongValue) {
            StartStop ss = new StartStop();
            if (args[0] != PNone.NONE) {
                hasStart.enter(inliningTarget);
                try {
                    ss.start = asIntNode.executeExact(frame, args[0], OverflowError);
                } catch (PException e) {
                    startNotInt.enter(inliningTarget);
                    throw raise(ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            }
            if (args[1] != PNone.NONE) {
                hasStop.enter(inliningTarget);
                try {
                    ss.stop = asIntNode.executeExact(frame, args[1], OverflowError);
                } catch (PException e) {
                    stopNotInt.enter(inliningTarget);
                    throw raise(ValueError, S_FOR_ISLICE_MUST_BE, "Stop argument");
                }
            }
            if (ss.start < 0 || ss.stop < -1 || ss.start > SysModuleBuiltins.MAXSIZE || ss.stop > SysModuleBuiltins.MAXSIZE) {
                wrongValue.enter(inliningTarget);
                throw raise(ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
            }
            return ss;
        }

        private static void populateSelf(PIslice self, PyObjectGetIter getIter, VirtualFrame frame, Object iterable, int start, int stop, int step) {
            self.setIterable(getIter.execute(frame, iterable));
            self.setNext(start);
            self.setStop(stop);
            self.setStep(step);
            self.setCnt(0);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object construct(Object cls, Object iterable, Object[] args,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
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
        @Specialization(guards = "isTypeNode.execute(cls)")
        Object constructNoFillValue(VirtualFrame frame, Object cls, Object[] args, @SuppressWarnings("unused") PNone fillValue,
                        @Shared("getIter") @Cached PyObjectGetIter getIterNode,
                        @Cached LoopConditionProfile loopProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            return construct(frame, cls, args, null, getIterNode, loopProfile, isTypeNode);
        }

        @Specialization(guards = {"isTypeNode.execute(cls)", "!isNoValue(fillValue)"})
        Object construct(VirtualFrame frame, Object cls, Object[] args, Object fillValue,
                        @Shared("getIter") @Cached PyObjectGetIter getIterNode,
                        @Cached LoopConditionProfile loopProfile,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PZipLongest self = factory().createZipLongest(cls);
            self.setFillValue(fillValue);
            self.setNumActive(args.length);

            Object[] itTuple = new Object[args.length];
            loopProfile.profileCounted(itTuple.length);
            for (int i = 0; loopProfile.inject(i < itTuple.length); i++) {
                itTuple[i] = getIterNode.execute(frame, args[i]);
            }
            self.setItTuple(itTuple);
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object iterables, Object repeat,
                        @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "pairwise", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PPairwise, doc = "Return an iterator of overlapping pairs taken from the input iterator.\n\n" +
                    "    s -> (s0,s1), (s1,s2), (s2, s3), ...")
    @GenerateNodeFactory
    public abstract static class PairwaiseNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)")
        protected PPairwise construct(VirtualFrame frame, Object cls, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            PPairwise self = factory().createPairwise(cls);
            self.setIterable(getIter.execute(frame, iterable));
            return self;
        }

        @Specialization(guards = "!isTypeNode.execute(cls)")
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object iterable,
                        @SuppressWarnings("unused") @Shared("typeNode") @Cached IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

}
