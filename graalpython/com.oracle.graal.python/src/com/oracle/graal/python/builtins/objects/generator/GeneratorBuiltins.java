/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenerator)
public class GeneratorBuiltins extends PythonBuiltins {

    private static Object resumeGenerator(PGenerator self) {
        try {
            return self.getCurrentCallTarget().call(self.getArguments());
        } catch (PException e) {
            self.markAsFinished();
            throw e;
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
    @ReportPolymorphism
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

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

        @Child private MaterializeFrameNode materializeFrameNode;

        @ImportStatic({PGuards.class, SpecialMethodNames.class})
        public abstract static class PrepareExceptionNode extends Node {
            public abstract PBaseException execute(VirtualFrame frame, Object type, Object value);

            private PRaiseNode raiseNode;
            private IsSubtypeNode isSubtypeNode;

            @Specialization
            PBaseException doException(PBaseException exc, @SuppressWarnings("unused") PNone value) {
                return exc;
            }

            @Specialization(guards = "!isPNone(value)")
            PBaseException doException(@SuppressWarnings("unused") PBaseException exc, @SuppressWarnings("unused") Object value,
                            @Cached PRaiseNode raise) {
                throw raise.raise(PythonBuiltinClassType.TypeError, "instance exception may not have a separate value");
            }

            @Specialization
            PBaseException doException(VirtualFrame frame, LazyPythonClass type, PBaseException value,
                            @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                            @Cached BranchProfile isNotInstanceProfile,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                if (isInstanceNode.executeWith(frame, value, type)) {
                    checkExceptionClass(type);
                    return value;
                } else {
                    isNotInstanceProfile.enter();
                    return doCreateObject(frame, type, value, callConstructor);
                }
            }

            @Specialization
            PBaseException doCreate(VirtualFrame frame, LazyPythonClass type, @SuppressWarnings("unused") PNone value,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object instance = callConstructor.execute(frame, type, new Object[]{type});
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, "exceptions must derive from BaseException");
                }
            }

            @Specialization
            PBaseException doCreateTuple(VirtualFrame frame, LazyPythonClass type, PTuple value,
                            @Cached GetObjectArrayNode getObjectArrayNode,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object[] array = getObjectArrayNode.execute(value);
                Object[] args = new Object[array.length + 1];
                args[0] = type;
                System.arraycopy(array, 0, args, 1, array.length);
                Object instance = callConstructor.execute(frame, type, args);
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, "exceptions must derive from BaseException");
                }
            }

            @Specialization(guards = {"!isPNone(value)", "!isPTuple(value)", "!isPBaseException(value)"})
            PBaseException doCreateObject(VirtualFrame frame, LazyPythonClass type, Object value,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object instance = callConstructor.execute(frame, type, new Object[]{type, value});
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, "exceptions must derive from BaseException");
                }
            }

            @Fallback
            PBaseException doError(Object type, @SuppressWarnings("unused") Object value) {
                throw raise().raise(TypeError, "exceptions must be classes or instances deriving from BaseException, not %p", type);
            }

            private PRaiseNode raise() {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                return raiseNode;
            }

            private void checkExceptionClass(LazyPythonClass type) {
                if (isSubtypeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isSubtypeNode = insert(IsSubtypeNode.create());
                }
                if (!isSubtypeNode.execute(type, PythonBuiltinClassType.PBaseException)) {
                    throw raise().raise(TypeError, "exceptions must be classes or instances deriving from BaseException, not %p", type);
                }
            }
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, Object typ, Object val, @SuppressWarnings("unused") PNone tb,
                        @Cached PrepareExceptionNode prepareExceptionNode) {
            PBaseException instance = prepareExceptionNode.execute(frame, typ, val);
            return doThrow(self, instance);
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, Object typ, Object val, PTraceback tb,
                        @Cached PrepareExceptionNode prepareExceptionNode) {
            PBaseException instance = prepareExceptionNode.execute(frame, typ, val);
            instance.setTraceback(tb);
            return doThrow(self, instance);
        }

        private Object doThrow(PGenerator self, PBaseException instance) {
            if (self.isStarted()) {
                // Pass it to the generator where it will be thrown by the last yield, the location
                // will be filled there
                PException pException = PException.fromObject(instance, null);
                PArguments.setSpecialArgument(self.getArguments(), pException);
                return resumeGenerator(self);
            } else {
                // Unstarted generator, we cannot pass the exception into the generator as there is
                // nothing that would handle it.
                // Instead, we throw the exception here and fake entering the generator by adding
                // its frame to the traceback manually.
                Node location = self.getCurrentCallTarget().getRootNode();
                MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                PFrame pFrame = ensureMaterializeFrameNode().execute(null, location, false, false, generatorFrame);
                instance.setTraceback(new LazyTraceback(pFrame, PException.fromObject(instance, location), instance.getTraceback()));
                throw PException.fromObject(instance, location);
            }
        }

        @Specialization(guards = {"!isPNone(tb)", "!isPTraceback(tb)"})
        @SuppressWarnings("unused")
        Object doError(VirtualFrame frame, PGenerator self, Object typ, Object val, Object tb) {
            throw raise(TypeError, "throw() third argument must be a traceback object");
        }

        private MaterializeFrameNode ensureMaterializeFrameNode() {
            if (materializeFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeFrameNode = insert(MaterializeFrameNode.create());
            }
            return materializeFrameNode;
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
