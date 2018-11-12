/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PClassmethod})
public class ClassmethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ClassmethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class CallNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(type)"})
        protected Object get(PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object type,
                        @Cached("create()") GetClassNode getClass,
                        @Cached("create()") BranchProfile uninitialized,
                        @Cached("create()") BranchProfile genericCallable) {
            return doGet(self, getClass.execute(obj), uninitialized, genericCallable);
        }

        @Specialization(guards = "!isNoValue(type)")
        protected Object doIt(PDecoratedMethod self, @SuppressWarnings("unused") Object obj, Object type,
                        @Cached("create()") BranchProfile uninitialized,
                        @Cached("create()") BranchProfile genericCallable) {
            return doGet(self, type, uninitialized, genericCallable);
        }

        private PMethod doGet(PDecoratedMethod self, Object type, BranchProfile uninitialized, BranchProfile genericCallable) {
            Object callable = self.getCallable();
            if (callable == null) {
                uninitialized.enter();
                throw raise(PythonBuiltinClassType.RuntimeError, "uninitialized classmethod object");
            }
            if (callable instanceof PFunction) {
                return factory().createMethod(type, (PFunction) callable);
            }
            genericCallable.enter();
            throw raise(PythonBuiltinClassType.NotImplementedError, "classmethods with non-function callables");
        }
    }
}
