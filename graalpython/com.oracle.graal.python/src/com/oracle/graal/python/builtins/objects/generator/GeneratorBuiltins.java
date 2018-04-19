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
package com.oracle.graal.python.builtins.objects.generator;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PGenerator.class)
public class GeneratorBuiltins extends PythonBuiltins {

    private static Object resumeGenerator(PGenerator self) {
        try {
            return self.getCallTarget().call(self.getArguments());
        } finally {
            PArguments.setSpecialArgument(self.getArguments(), null);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return GeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object iter(PGenerator self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        private final ConditionProfile errorProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public Object next(PGenerator self) {
            if (self.isFinished()) {
                throw raise(StopIteration);
            }
            try {
                return self.getCallTarget().call(self.getArguments());
            } catch (PException e) {
                e.expectStopIteration(getCore(), errorProfile);
                self.markAsFinished();
                throw raise(StopIteration);
            }
        }
    }

    @Builtin(name = "send", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBuiltinNode {

        @Specialization
        public Object send(PGenerator self, Object value) {
            PArguments.setSpecialArgument(self.getArguments(), value);
            return resumeGenerator(self);
        }
    }

    // throw(typ[,val[,tb]])
    @Builtin(name = "throw", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class ThrowNode extends PythonBuiltinNode {
        @Specialization
        Object sendThrow(PGenerator self, PythonClass typ, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(typ, new Object[0]);
            if (instance instanceof PBaseException) {
                PException pException = new PException((PBaseException) instance, this);
                ((PBaseException) instance).setException(pException);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, PythonClass typ, PTuple val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(typ, val.getArray());
            if (instance instanceof PBaseException) {
                PException pException = new PException((PBaseException) instance, this);
                ((PBaseException) instance).setException(pException);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization(guards = {"!isPNone(val)", "!isPTuple(val)"})
        Object sendThrow(PGenerator self, PythonClass typ, Object val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(typ, new Object[]{val});
            if (instance instanceof PBaseException) {
                PException pException = new PException((PBaseException) instance, this);
                ((PBaseException) instance).setException(pException);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, PBaseException instance, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb) {
            PException pException = new PException(instance, this);
            instance.setException(pException);
            PArguments.setSpecialArgument(self.getArguments(), pException);
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, @SuppressWarnings("unused") PythonClass typ, PBaseException instance, PTraceback tb) {
            PException pException = new PException(instance, this);
            instance.setException(pException);
            instance.setTraceback(tb);
            return resumeGenerator(self);
        }
    }
}
