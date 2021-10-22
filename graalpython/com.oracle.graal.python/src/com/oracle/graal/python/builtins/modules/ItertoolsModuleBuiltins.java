/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COPY__;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IterNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.itertools.PAccumulate;
import com.oracle.graal.python.builtins.objects.itertools.PChain;
import com.oracle.graal.python.builtins.objects.itertools.PCombinations;
import com.oracle.graal.python.builtins.objects.itertools.PCombinationsWithReplacement;
import com.oracle.graal.python.builtins.objects.itertools.PCompress;
import com.oracle.graal.python.builtins.objects.itertools.PCount;
import com.oracle.graal.python.builtins.objects.itertools.PDropwhile;
import com.oracle.graal.python.builtins.objects.itertools.PFilterfalse;
import com.oracle.graal.python.builtins.objects.itertools.PGroupBy;
import com.oracle.graal.python.builtins.objects.itertools.PGrouper;
import com.oracle.graal.python.builtins.objects.itertools.PIslice;
import com.oracle.graal.python.builtins.objects.itertools.PPermutations;
import com.oracle.graal.python.builtins.objects.itertools.PProduct;
import com.oracle.graal.python.builtins.objects.itertools.PRepeat;
import com.oracle.graal.python.builtins.objects.itertools.PStarmap;
import com.oracle.graal.python.builtins.objects.itertools.PTakewhile;
import com.oracle.graal.python.builtins.objects.itertools.PTeeDataObject;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = "itertools")
public final class ItertoolsModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ItertoolsModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "accumulate", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PAccumulate, doc = "accumulate(iterable) --> accumulate object\n\nReturn series of accumulated sums.")
    @GenerateNodeFactory
    public abstract static class AcumulateNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PAccumulate construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createAccumulate();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "combinations", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PCombinations, parameterNames = {"$self", "iterable",
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

        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PCombinations construct(Object cls, Object iterable, int r,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createCombinations();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, Object iterable, Object r,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // XXX this vs. init?
    @Builtin(name = "combinations_with_replacement", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PCombinationsWithReplacement, parameterNames = {"$self", "iterable",
                    "r"}, doc = "combinations_with_replacement(iterable, r) --> combinations_with_replacement object\n\n" +
                                    "Return successive r-length combinations of elements in the iterable\n" +
                                    "allowing individual elements to have successive repeats.\n" +
                                    "    combinations_with_replacement('ABC', 2) --> AA AB AC BB BC CC")
    @ArgumentClinic(name = "r", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CombinationsWithReplacementNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ItertoolsModuleBuiltinsClinicProviders.CombinationsNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PCombinationsWithReplacement construct(Object cls, Object iterable, int r,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createCombinationsWithReplacement();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTypeNode.execute(cls)")
        protected Object notype(Object cls, Object iterable, Object r,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PCompress, doc = "Make an iterator that filters elements from *data* returning\n" +
                    "only those that have a corresponding element in *selectors* that evaluates to\n" +
                    "``True``.  Stops when either the *data* or *selectors* iterables has been\n" +
                    "exhausted.\n" +
                    "Equivalent to::\n\n" +
                    "\tdef compress(data, selectors):\n" +
                    "\t\t# compress('ABCDEF', [1,0,1,0,1,1]) --> A C E F\n" +
                    "\t\treturn (d for d, s in zip(data, selectors) if s)")
    @GenerateNodeFactory
    public abstract static class CompressNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PCompress construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createCompress();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "dropwhile", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDropwhile, doc = "dropwhile(predicate, iterable) --> dropwhile object\n\n" +
                    "Drop items from the iterable while predicate(item) is true.\n" +
                    "Afterwards, return every element until the iterable is exhausted.")
    @GenerateNodeFactory
    public abstract static class DropwhileNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PDropwhile construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createDropwhile();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "filterfalse", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PFilterfalse, doc = "filterfalse(function or None, sequence) --> filterfalse object\n\n" +
                    "Return those items of sequence for which function(item) is false.\n" +
                    "If function is None, return the items that are false.")
    @GenerateNodeFactory
    public abstract static class FilterFalseNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PFilterfalse construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createFilterfalse();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "groupby", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PGroupBy, doc = "Make an iterator that returns consecutive keys and groups from the\n" +
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
    @GenerateNodeFactory
    public abstract static class GroupByNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PGroupBy construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createGroupBy(cls);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "_grouper", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PGrouper)
    @GenerateNodeFactory
    public abstract static class GrouperNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PGrouper construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createGrouper(cls);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "takewhile", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PTakewhile, doc = "Make an iterator that returns elements from the iterable as\n" +
                    "long as the predicate is true.\n\nEquivalent to :\n\ndef takewhile(predicate, iterable):\n\tfor x in iterable:\n\t\tif predicate(x):\n\t\t\tyield x\n" +
                    "\t\telse:\n\t\t\tbreak")
    @GenerateNodeFactory
    public abstract static class TakewhileNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PTakewhile construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createTakewhile();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
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
        protected Object tee(VirtualFrame frame, Object iterable, int n,
                        @Cached IterNode iterNode,
                        @Cached PyObjectLookupAttr getAttrNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached CallVarargsMethodNode callNode,
                        @Cached BranchProfile notCallableProfile) {
            Object it = iterNode.execute(frame, iterable, PNone.NO_VALUE);
            Object copyCallable = getAttrNode.execute(frame, it, __COPY__);
            if (!callableCheckNode.execute(copyCallable)) {
                notCallableProfile.enter();
                // as in Tee.__NEW__()
                PTeeDataObject dataObj = factory().createTeeDataObject(it);
                it = factory().createTee(dataObj, 0);
            }

            // return tuple([it] + [it.__copy__() for i in range(1, n)])
            Object[] tupleObjs = new Object[n];
            tupleObjs[0] = it;

            copyCallable = getAttrNode.execute(frame, it, __COPY__);
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
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PTeeDataObject construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createTeeDataObject();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "permutations", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PPermutations, doc = "permutations(iterable[, r]) --> permutations object\n\n" +
                    "Return successive r-length permutations of elements in the iterable.\n\n" + "permutations(range(3), 2) --> (0,1), (0,2), (1,0), (1,2), (2,0), (2,1)")
    @GenerateNodeFactory
    public abstract static class PermutationsNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PPermutations construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createPermutations();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "product", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PProduct, doc = "Cartesian product of input iterables.\n\n" +
                    "Equivalent to nested for-loops in a generator expression. For example,\n ``product(A, B)`` returns the same as ``((x,y) for x in A for y in B)``.\n\n" +
                    "The nested loops cycle like an odometer with the rightmost element advancing\n on every iteration.  This pattern creates a lexicographic ordering so that if\n" +
                    " the input's iterables are sorted, the product tuples are emitted in sorted\n order.\n\nTo compute the product of an iterable with itself, specify the number of\n" +
                    " repetitions with the optional *repeat* keyword argument.  For example,\n ``product(A, repeat=4)`` means the same as ``product(A, A, A, A)``.\n\n" +
                    "This function is equivalent to the following code, except that the\n actual implementation does not build up intermediate results in memory::\n\n" +
                    "def product(*args, **kwds):\n\t# product('ABCD', 'xy') --> Ax Ay Bx By Cx Cy Dx Dy\n\t# product(range(2), repeat=3) --> 000 001 010 011 100 101 110 111\n" +
                    "\tpools = map(tuple, args) * kwds.get('repeat', 1)\n\tresult = [[]]\n\tfor pool in pools:\n\t\tresult = [x+[y] for x in result for y in pool]\n" +
                    "\tfor prod in result:\n\t\tyield tuple(prod)")
    @GenerateNodeFactory
    public abstract static class ProductNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PProduct construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createProduct();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // XXX all c-tors should implement what is done in init and remove init, it is a noop in python
    // too
    // XXX add tests for it
    @Builtin(name = "repeat", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PRepeat, doc = "repeat(object [,times]) -> create an iterator which returns the object\n" +
                    "for the specified number of times.  If not specified, returns the object\nendlessly.")
    @GenerateNodeFactory
    public abstract static class RepeatNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PRepeat construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createRepeat(cls);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "chain", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PChain, doc = "Return a chain object whose .__next__() method returns elements from the\n" +
                    "first iterable until it is exhausted, then elements from the next\niterable, until all of the iterables are exhausted.")
    @GenerateNodeFactory
    public abstract static class ChainNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PChain construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createChain();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PCount)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PCount construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createCount();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "starmap", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PStarmap, doc = "starmap(function, sequence) --> starmap object\n\n" +
                    "Return an iterator whose values are returned from the function evaluated\n" +
                    "with an argument tuple taken from the given sequence.")
    @GenerateNodeFactory
    public abstract static class StarmapNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PStarmap construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createStarmap();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "islice", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PIslice)
    @GenerateNodeFactory
    public abstract static class IsliceNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PIslice construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createIslice();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

}
