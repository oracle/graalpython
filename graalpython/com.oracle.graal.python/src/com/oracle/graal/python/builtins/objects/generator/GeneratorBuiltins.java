/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenerator)
public class GeneratorBuiltins extends PythonBuiltins {

    private static Object resumeGenerator(PGenerator self) {
        try {
            return self.getCurrentCallTarget().call(self.getArguments());
        } finally {
            self.setNextCallTarget();
            PArguments.setSpecialArgument(self.getArguments(), null);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object iter(PGenerator self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Child private GetCaughtExceptionNode getCaughtExceptionNode;

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        protected static CallTargetInvokeNode createDirectCall(CallTarget target) {
            return CallTargetInvokeNode.create(target, false, true);
        }

        protected static GenericInvokeNode createIndirectCall() {
            return GenericInvokeNode.create();
        }

        protected static boolean sameCallTarget(RootCallTarget target1, CallTarget target2) {
            return target1 == target2;
        }

        @Specialization(guards = "sameCallTarget(self.getCurrentCallTarget(), call.getCallTarget())", limit = "getCallSiteInlineCacheMaxDepth()")
        public Object nextCached(VirtualFrame frame, PGenerator self,
                        @Cached("createDirectCall(self.getCurrentCallTarget())") CallTargetInvokeNode call) {
            if (self.isFinished()) {
                throw raise(StopIteration);
            }
            try {
                Object[] arguments = self.getArguments();
                return call.execute(frame, null, null, arguments);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                self.markAsFinished();
                throw e;
            } finally {
                self.setNextCallTarget();
            }
        }

        @Specialization(replaces = "nextCached")
        public Object next(VirtualFrame frame, PGenerator self,
                        @Cached("createIndirectCall()") GenericInvokeNode call) {
            if (self.isFinished()) {
                throw raise(StopIteration);
            }
            try {
                Object[] arguments = self.getArguments();
                return call.execute(frame, self.getCurrentCallTarget(), arguments);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                self.markAsFinished();
                throw e;
            } finally {
                self.setNextCallTarget();
            }
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBuiltinNode {

        @Specialization
        public Object send(PGenerator self, Object value) {
            PArguments.setSpecialArgument(self.getArguments(), value);
            return resumeGenerator(self);
        }
    }

    // throw(typ[,val[,tb]])
    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ThrowNode extends PythonBuiltinNode {
        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, LazyPythonClass typ, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(frame, typ, new Object[]{typ});
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, LazyPythonClass typ, PTuple val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object[] array = val.getArray();
            Object[] args = new Object[array.length + 1];
            System.arraycopy(array, 0, args, 1, array.length);
            args[0] = typ;
            Object instance = callTyp.execute(frame, typ, args);
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization(guards = {"!isPNone(val)", "!isPTuple(val)"})
        Object sendThrow(VirtualFrame frame, PGenerator self, LazyPythonClass typ, Object val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(frame, typ, new Object[]{typ, val});
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, PBaseException instance, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb,
                        @Cached MaterializeFrameNode materializeNode) {
            PException pException = PException.fromObject(instance, this);
            PFrame pyFrame = materializeNode.execute(frame, this, true, false);
            pException.getExceptionObject().setTraceback(factory().createTraceback(pyFrame, pException));
            PArguments.setSpecialArgument(self.getArguments(), pException);
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, @SuppressWarnings("unused") LazyPythonClass typ, PBaseException instance, PTraceback tb) {
            PException pException = PException.fromObject(instance, this);
            instance.setTraceback(tb);
            PArguments.setSpecialArgument(self.getArguments(), pException);
            return resumeGenerator(self);
        }
    }

    @Builtin(name = "gi_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        Object getCode(PGenerator self,
                        @Cached("createBinaryProfile()") ConditionProfile hasCodeProfile) {
            PCode code = self.getCode();
            if (hasCodeProfile.profile(code == null)) {
                code = factory().createCode(self.getCurrentCallTarget());
                self.setCode(code);
            }
            return code;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String repr(PGenerator self) {
            return self.toString();
        }
    }
}
