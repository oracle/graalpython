/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.generator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopAsyncIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PrepareExceptionNode;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode.GeneratorReturnException;
import com.oracle.graal.python.nodes.bytecode.GeneratorYieldResult;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCoroutine, PythonBuiltinClassType.PGenerator})
public final class CommonGeneratorBuiltins extends PythonBuiltins {
    /**
     * Creates a fresh copy of the generator arguments to be used for the next invocation of the
     * generator. This is necessary to avoid persisting caller state. For example: If the generator
     * is invoked using {@code next(g)} outside of any {@code except} handler but the generator
     * requests the exception state, then the exception state will be written into the arguments. If
     * we now use the same arguments array every time, the next invocation would think that there is
     * not an exception but in fact, a subsequent call to {@code next} may have a different
     * exception state.
     *
     * <pre>
     *     g = my_generator()
     *
     *     # invoke without any exception context
     *     next(g)
     *
     *     try:
     *         raise ValueError
     *     except ValueError:
     *         # invoke with exception context
     *         next(g)
     * </pre>
     *
     * This is necessary for correct chaining of exceptions.
     */
    private static Object[] prepareArguments(PGenerator self) {
        Object[] generatorArguments = self.getArguments();
        Object[] arguments = new Object[generatorArguments.length];
        PythonUtils.arraycopy(generatorArguments, 0, arguments, 0, arguments.length);
        return arguments;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CommonGeneratorBuiltinsFactory.getFactories();
    }

    private static void checkResumable(Node inliningTarget, PGenerator self, PRaiseNode.Lazy raiseNode) {
        if (self.isFinished()) {
            if (self.isAsyncGen()) {
                throw raiseNode.get(inliningTarget).raise(StopAsyncIteration);
            }
            if (self.isCoroutine()) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_CORO);
            }
            throw raiseNode.get(inliningTarget).raiseStopIteration();
        }
        if (self.isRunning()) {
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class, PythonOptions.class})
    abstract static class ResumeGeneratorNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PGenerator self, Object sendValue);

        @Specialization(guards = {"!isBytecodeDSLInterpreter()", "sameCallTarget(self.getCurrentCallTarget(), call.getCallTarget())"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static Object cached(VirtualFrame frame, Node inliningTarget, PGenerator self, Object sendValue,
                        @Cached(value = "createDirectCall(self.getCurrentCallTarget())", inline = false) CallTargetInvokeNode call,
                        @Exclusive @Cached InlinedBranchProfile returnProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.setRunning(true);
            Object[] arguments = prepareArguments(self);
            if (sendValue != null) {
                PArguments.setSpecialArgument(arguments, sendValue);
            }
            GeneratorYieldResult result;
            try {
                result = (GeneratorYieldResult) call.execute(frame, null, null, null, arguments);
            } catch (PException e) {
                throw handleException(self, inliningTarget, errorProfile, raiseNode, e);
            } catch (GeneratorReturnException e) {
                returnProfile.enter(inliningTarget);
                throw handleReturn(self, e.value, raiseNode.get(inliningTarget));
            } finally {
                self.setRunning(false);
            }
            return handleResult(inliningTarget, self, result);
        }

        @Specialization(guards = {"isBytecodeDSLInterpreter()", "sameCallTarget(currentCallTarget, call.getCallTarget())"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static Object cachedBytecodeDSL(VirtualFrame frame, Node inliningTarget, PGenerator self, Object sendValue,
                        @Bind("self.getCurrentCallTarget()") @SuppressWarnings("unused") RootCallTarget currentCallTarget,
                        @Cached(value = "createDirectCall(currentCallTarget)", inline = false) CallTargetInvokeNode call,
                        @Cached("self.getContinuation() == null") boolean firstCall,
                        @Exclusive @Cached InlinedBranchProfile returnProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.setRunning(true);
            Object generatorResult;
            try {
                ContinuationResult continuation = self.getContinuation();
                Object[] arguments;
                if (firstCall) {
                    // First invocation: call the regular root node.
                    arguments = prepareArguments(self);
                } else {
                    // Subsequent invocations: call a continuation root node.
                    arguments = new Object[]{continuation.getFrame(), sendValue};
                }
                generatorResult = call.execute(frame, null, null, null, arguments);
            } catch (PException e) {
                throw handleException(self, inliningTarget, errorProfile, raiseNode, e);
            } finally {
                self.setRunning(false);
            }
            if (generatorResult instanceof ContinuationResult continuation) {
                return handleResult(inliningTarget, self, continuation);
            } else {
                returnProfile.enter(inliningTarget);
                throw handleReturn(self, generatorResult, raiseNode.get(inliningTarget));
            }

        }

        @Specialization(replaces = "cached", guards = "!isBytecodeDSLInterpreter()")
        @Megamorphic
        static Object generic(VirtualFrame frame, Node inliningTarget, PGenerator self, Object sendValue,
                        @Cached InlinedConditionProfile hasFrameProfile,
                        @Cached(inline = false) GenericInvokeNode call,
                        @Exclusive @Cached InlinedBranchProfile returnProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.setRunning(true);
            Object[] arguments = prepareArguments(self);
            if (sendValue != null) {
                PArguments.setSpecialArgument(arguments, sendValue);
            }
            GeneratorYieldResult result;
            try {
                if (hasFrameProfile.profile(inliningTarget, frame != null)) {
                    result = (GeneratorYieldResult) call.execute(frame, self.getCurrentCallTarget(), arguments);
                } else {
                    result = (GeneratorYieldResult) call.execute(self.getCurrentCallTarget(), arguments);
                }
            } catch (PException e) {
                throw handleException(self, inliningTarget, errorProfile, raiseNode, e);
            } catch (GeneratorReturnException e) {
                returnProfile.enter(inliningTarget);
                throw handleReturn(self, e.value, raiseNode.get(inliningTarget));
            } finally {
                self.setRunning(false);
            }
            return handleResult(inliningTarget, self, result);
        }

        @Specialization(replaces = "cachedBytecodeDSL", guards = "isBytecodeDSLInterpreter()")
        @Megamorphic
        static Object genericBytecodeDSL(VirtualFrame frame, Node inliningTarget, PGenerator self, Object sendValue,
                        @Cached InlinedConditionProfile hasFrameProfile,
                        @Cached(inline = false) GenericInvokeNode call,
                        @Cached InlinedConditionProfile firstInvocationProfile,
                        @Cached InlinedBranchProfile returnProfile,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.setRunning(true);
            Object generatorResult;
            try {
                ContinuationResult continuation = self.getContinuation();
                Object[] arguments;
                if (firstInvocationProfile.profile(inliningTarget, continuation == null)) {
                    // First invocation: call the regular root node.
                    arguments = prepareArguments(self);
                } else {
                    // Subsequent invocations: call a continuation root node.
                    // TODO: forward arguments into the continuation frame (e.g., exception state).
                    arguments = new Object[]{continuation.getFrame(), sendValue};
                }

                if (hasFrameProfile.profile(inliningTarget, frame != null)) {
                    generatorResult = call.execute(frame, self.getCurrentCallTarget(), arguments);
                } else {
                    generatorResult = call.execute(self.getCurrentCallTarget(), arguments);
                }
            } catch (PException e) {
                throw handleException(self, inliningTarget, errorProfile, raiseNode, e);
            } finally {
                self.setRunning(false);
            }
            if (generatorResult instanceof ContinuationResult continuation) {
                return handleResult(inliningTarget, self, continuation);
            } else {
                returnProfile.enter(inliningTarget);
                throw handleReturn(self, generatorResult, raiseNode.get(inliningTarget));
            }
        }

        private static PException handleException(PGenerator self, Node inliningTarget, IsBuiltinObjectProfile profile, PRaiseNode.Lazy raiseNode, PException e) {
            self.markAsFinished();
            if (self.isAsyncGen()) {
                // Async generators need to wrap StopAsyncIteration in a runtime error
                if (profile.profileException(inliningTarget, e, StopAsyncIteration)) {
                    throw raiseNode.get(inliningTarget).raiseWithCause(RuntimeError, e.getEscapedException(), ErrorMessages.ASYNCGEN_RAISED_ASYNCSTOPITER);
                }
            }
            // PEP 479 - StopIteration raised from generator body needs to be wrapped in
            // RuntimeError
            e.expectStopIteration(inliningTarget, profile);
            throw raiseNode.get(inliningTarget).raiseWithCause(RuntimeError, e.getEscapedException(), ErrorMessages.GENERATOR_RAISED_STOPITER);
        }

        private static Object handleResult(Node node, PGenerator self, Object result) {
            return self.handleResult(PythonLanguage.get(node), result);
        }

        private static PException handleReturn(PGenerator self, Object returnValue, PRaiseNode raiseNode) {
            self.markAsFinished();
            if (self.isAsyncGen()) {
                throw raiseNode.raise(StopAsyncIteration);
            }
            if (returnValue != PNone.NONE) {
                throw raiseNode.raise(StopIteration, new Object[]{returnValue});
            } else {
                throw raiseNode.raise(StopIteration);
            }
        }

        protected static CallTargetInvokeNode createDirectCall(CallTarget target) {
            return CallTargetInvokeNode.create(target, false, true);
        }

        protected static boolean sameCallTarget(RootCallTarget target1, CallTarget target2) {
            return target1 == target2;
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object send(VirtualFrame frame, PGenerator self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached ResumeGeneratorNode resumeGeneratorNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // even though this isn't a builtin for async generators, SendNode is used on async
            // generators by PAsyncGenSend
            checkResumable(inliningTarget, self, raiseNode);
            if (!self.isStarted() && value != PNone.NONE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.SEND_NON_NONE_TO_UNSTARTED_GENERATOR);
            }
            return resumeGeneratorNode.execute(frame, inliningTarget, self, value);
        }
    }

    // throw(typ[,val[,tb]])
    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class ThrowNode extends PythonQuaternaryBuiltinNode {

        @Specialization
        static Object sendThrow(VirtualFrame frame, PGenerator self, Object typ, Object val, Object tb,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasTbProfile,
                        @Cached InlinedConditionProfile startedProfile,
                        @Cached InlinedBranchProfile invalidTbProfile,
                        @Cached InlinedBranchProfile runningProfile,
                        @Cached PrepareExceptionNode prepareExceptionNode,
                        @Cached ResumeGeneratorNode resumeGeneratorNode,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                        @Cached ExceptionNodes.SetTracebackNode setTracebackNode,
                        @Cached ExceptionNodes.SetContextNode setContextNode,
                        @Cached PythonObjectFactory.Lazy factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            boolean hasTb = hasTbProfile.profile(inliningTarget, !(tb instanceof PNone));
            if (hasTb && !(tb instanceof PTraceback)) {
                invalidTbProfile.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.THROW_THIRD_ARG_MUST_BE_TRACEBACK);
            }
            if (self.isRunning()) {
                runningProfile.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            Object instance = prepareExceptionNode.execute(frame, typ, val);
            if (hasTb) {
                setTracebackNode.execute(inliningTarget, instance, tb);
            }
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            setContextNode.execute(inliningTarget, instance, PNone.NONE); // Will be filled when
                                                                          // caught
            if (self.isCoroutine() && self.isFinished()) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_CORO);
            }
            if (startedProfile.profile(inliningTarget, self.isStarted() && !self.isFinished())) {
                // Pass it to the generator where it will be thrown by the last yield, the location
                // will be filled there
                return resumeGeneratorNode.execute(frame, inliningTarget, self, new ThrowData(instance, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            } else {
                // Unstarted generator, we cannot pass the exception into the generator as there is
                // nothing that would handle it.
                // Instead, we throw the exception here and fake entering the generator by adding
                // its frame to the traceback manually.
                self.markAsFinished();
                Node location = self.getCurrentCallTarget().getRootNode();
                MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                PFrame pFrame = MaterializeFrameNode.materializeGeneratorFrame(location, generatorFrame, PFrame.Reference.EMPTY, factory.get(inliningTarget));
                FrameInfo info = (FrameInfo) generatorFrame.getFrameDescriptor().getInfo();
                pFrame.setLine(info.getFirstLineNumber());
                Object existingTracebackObj = getTracebackNode.execute(inliningTarget, instance);
                PTraceback newTraceback = factory.get(inliningTarget).createTraceback(pFrame, pFrame.getLine(),
                                (existingTracebackObj instanceof PTraceback existingTraceback) ? existingTraceback : null);
                setTracebackNode.execute(inliningTarget, instance, newTraceback);
                throw PException.fromObject(instance, location, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object close(VirtualFrame frame, PGenerator self,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile isGeneratorExit,
                        @Cached IsBuiltinObjectProfile isStopIteration,
                        @Cached ResumeGeneratorNode resumeGeneratorNode,
                        @Cached InlinedConditionProfile isStartedPorfile,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.isRunning()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            if (isStartedPorfile.profile(inliningTarget, self.isStarted() && !self.isFinished())) {
                PBaseException pythonException = factory.createBaseException(GeneratorExit);
                // Pass it to the generator where it will be thrown by the last yield, the location
                // will be filled there
                boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(inliningTarget));
                try {
                    resumeGeneratorNode.execute(frame, inliningTarget, self, new ThrowData(pythonException, withJavaStacktrace));
                } catch (PException pe) {
                    if (isGeneratorExit.profileException(inliningTarget, pe, GeneratorExit) || isStopIteration.profileException(inliningTarget, pe, StopIteration)) {
                        // This is the "success" path
                        return PNone.NONE;
                    }
                    throw pe;
                } finally {
                    self.markAsFinished();
                }
                throw raiseNode.get(inliningTarget).raise(RuntimeError, ErrorMessages.GENERATOR_IGNORED_EXIT);
            } else {
                self.markAsFinished();
                return PNone.NONE;
            }
        }
    }
}
