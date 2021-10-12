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
import com.oracle.graal.python.builtins.objects.itertools.PChain;
import com.oracle.graal.python.builtins.objects.itertools.PCount;
import com.oracle.graal.python.builtins.objects.itertools.PIslice;
import com.oracle.graal.python.builtins.objects.itertools.PPermutations;
import com.oracle.graal.python.builtins.objects.itertools.PRepeat;
import com.oracle.graal.python.builtins.objects.itertools.PStarmap;
import com.oracle.graal.python.builtins.objects.itertools.PTeeDataObject;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
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
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
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
        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PPermutations construct(Object cls, Object[] arguments, PKeyword[] keywords, @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return factory().createPermutations();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "repeat", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PRepeat, doc = "repeat(object [,times]) -> create an iterator which returns the object\n" +
                    "for the specified number of times.  If not specified, returns the object\nendlessly.")
    @GenerateNodeFactory
    public abstract static class RepeatNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PRepeat construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            return factory().createRepeat();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object notype(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "chain", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PChain, doc = "Return a chain object whose .__next__() method returns elements from the " +
                    "first iterable until it is exhausted, then elements from the next iterable, until all of the iterables are exhausted.")
    @GenerateNodeFactory
    public abstract static class ChainNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        protected PChain construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
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
        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PCount construct(Object cls, Object[] arguments, PKeyword[] keywords, @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return factory().createCount();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Builtin(name = "starmap", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PStarmap, doc = "starmap(function, sequence) --> starmap object\n\n" +
                    "    Return an iterator whose values are returned from the function evaluated\n" +
                    "    with an argument tuple taken from the given sequence.")
    @GenerateNodeFactory
    public abstract static class StarmapNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PStarmap construct(Object cls, Object[] arguments, PKeyword[] keywords, @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
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
        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PIslice construct(Object cls, Object[] arguments, PKeyword[] keywords, @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return factory().createIslice();
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object construct(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

}
