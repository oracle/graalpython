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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.PICKLE_ITERTOOLS_IN_PYTHON_3_14;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.PTee;
import com.oracle.graal.python.builtins.objects.itertools.TeeBuiltins;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(defineModule = "itertools")
public final class ItertoolsModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ItertoolsModuleBuiltinsFactory.getFactories();
    }

    private static void warnPickleDeprecated() {
        WarningsModuleBuiltins.WarnNode.getUncached().warnEx(null, DeprecationWarning, PICKLE_ITERTOOLS_IN_PYTHON_3_14, 1);
    }

    @SuppressWarnings("this-escape")
    public abstract static class DeprecatedReduceBuiltin extends PythonUnaryBuiltinNode {
        @Child private BoundaryCallData boundaryCallData = BoundaryCallData.createFor(this);

        @Override
        public final Object execute(VirtualFrame frame, Object arg) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return warnAndExecute(arg);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private Object warnAndExecute(Object arg) {
            warnPickleDeprecated();
            return executeImpl(arg);
        }

        protected abstract Object executeImpl(Object arg);
    }

    @SuppressWarnings("this-escape")
    public abstract static class DeprecatedSetStateBuiltin extends PythonBinaryBuiltinNode {
        @Child private BoundaryCallData boundaryCallData = BoundaryCallData.createFor(this);

        @Override
        public final Object execute(VirtualFrame frame, Object arg1, Object arg2) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return warnAndExecute(arg1, arg2);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private Object warnAndExecute(Object arg1, Object arg2) {
            warnPickleDeprecated();
            return executeImpl(arg1, arg2);
        }

        protected abstract Object executeImpl(Object arg1, Object arg2);
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
                        @Bind Node inliningTarget) {
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached InlinedConditionProfile isTeeInstanceProfile,
                        @Bind PythonLanguage language) {
            Object it = getIter.execute(frame, inliningTarget, iterable);

            // return tuple([it] + [it.__copy__() for i in range(1, n)])
            Object[] tupleObjs = new Object[n];
            PTee to = TeeBuiltins.NewNode.teeFromIterable(frame, null, it,
                            inliningTarget, getIter, isTeeInstanceProfile, language);

            tupleObjs[0] = to;

            for (int i = 1; i < n; i++) {
                tupleObjs[i] = TeeBuiltins.CopyNode.copy(to, language);
            }
            return PFactory.createTuple(language, tupleObjs);
        }
    }

}
