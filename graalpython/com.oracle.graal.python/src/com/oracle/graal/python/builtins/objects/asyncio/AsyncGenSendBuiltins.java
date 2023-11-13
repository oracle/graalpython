/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.asyncio;

import static com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenASend.AwaitableState;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AWAIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectExactProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PAsyncGenASend)
public final class AsyncGenSendBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AsyncGenSendBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___AWAIT__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Await extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doAwait(PAsyncGenASend self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Next extends PythonUnaryBuiltinNode {
        @Specialization
        public Object next(VirtualFrame frame, PAsyncGenASend self,
                        @Cached Send send) {
            return send.execute(frame, self, PNone.NONE);
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Send extends PythonBinaryBuiltinNode {
        @Specialization
        public Object send(VirtualFrame frame, PAsyncGenASend self, Object sent,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseReuse,
                        @Cached PRaiseNode raiseAlreadyRunning,
                        @Cached CommonGeneratorBuiltins.SendNode send,
                        @Cached IsBuiltinObjectProfile isStopIteration,
                        @Cached IsBuiltinObjectProfile isGenExit,
                        @Cached IsBuiltinObjectExactProfile isAsyncGenWrappedValue,
                        @Cached PRaiseNode raiseStopIteration) {
            Object result;
            if (self.getState() == AwaitableState.CLOSED) {
                throw raiseReuse.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_ASEND);
            }

            if (self.getState() == AwaitableState.INIT) {
                if (self.receiver.isRunningAsync()) {
                    throw raiseAlreadyRunning.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.AGEN_ALREADY_RUNNING);
                }
                if (sent == null || sent == PNone.NONE) {
                    sent = self.message;
                }
                self.setState(AwaitableState.ITER);
            }
            self.receiver.setRunningAsync(true);
            try {
                result = send.execute(frame, self.receiver, sent);
            } catch (PException e) {
                self.setState(AwaitableState.CLOSED);
                throw handleAGError(self.receiver, e, inliningTarget, isStopIteration, isGenExit);
            }
            try {
                return unwrapAGYield(self.receiver, result, inliningTarget, isAsyncGenWrappedValue, raiseStopIteration);
            } catch (PException e) {
                self.setState(AwaitableState.CLOSED);
                throw e;
            }
        }
    }

    /*
     * The following two functions are the equivalent of async_gen_unwrap_value. The assumption made
     * is that gen_send and throw of CPython will not return NULL unless they also set an error. If
     * a PException happens, handleAGError is used, otherwise, unwrapAGYield is used.
     */

    static PException handleAGError(PAsyncGen self, PException exception,
                    Node inliningTarget,
                    IsBuiltinObjectProfile isStopAsyncIteration,
                    IsBuiltinObjectProfile isGeneratorExit) {
        if (isStopAsyncIteration.profileException(inliningTarget, exception, PythonBuiltinClassType.StopAsyncIteration) ||
                        isGeneratorExit.profileException(inliningTarget, exception, PythonBuiltinClassType.GeneratorExit)) {
            self.markClosed();
        }
        self.setRunningAsync(false);
        return exception;
    }

    static Object unwrapAGYield(PAsyncGen self, Object result,
                    Node inliningTarget,
                    IsBuiltinObjectExactProfile isAGWrappedValue,
                    PRaiseNode raise) {
        if (isAGWrappedValue.profileObject(inliningTarget, result, PythonBuiltinClassType.PAsyncGenAWrappedValue)) {
            self.setRunningAsync(false);
            Object wrapped = ((PAsyncGenWrappedValue) result).getWrapped();
            throw raise.raise(PythonBuiltinClassType.StopIteration, new Object[]{wrapped});
        }
        return result;
    }

    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Throw extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PAsyncGenASend self, Object arg1, Object arg2, Object arg3);

        @Specialization
        public Object doThrow(VirtualFrame frame, PAsyncGenASend self, Object arg1, Object arg2, Object arg3,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseReuse,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode,
                        @Cached IsBuiltinObjectProfile isStopIteration,
                        @Cached IsBuiltinObjectProfile isGeneratorExit,
                        @Cached IsBuiltinObjectExactProfile isAGWrappedValue,
                        @Cached PRaiseNode raiseStopIteration) {
            Object result;

            if (self.getState() == AwaitableState.CLOSED) {
                throw raiseReuse.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_ASEND);
            }
            try {
                result = throwNode.execute(frame, self.receiver, arg1, arg2, arg3);
            } catch (PException e) {
                self.setState(AwaitableState.CLOSED);
                throw handleAGError(self.receiver, e, inliningTarget, isStopIteration, isGeneratorExit);
            }
            try {
                return unwrapAGYield(self.receiver, result, inliningTarget, isAGWrappedValue, raiseStopIteration);
            } catch (PException e) {
                self.setState(AwaitableState.CLOSED);
                throw e;
            }

        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Iter extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PAsyncGenASend self) {
            return self;
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Close extends PythonUnaryBuiltinNode {
        @Specialization
        public Object close(PAsyncGenASend self) {
            self.setState(AwaitableState.CLOSED);
            return PNone.NONE;
        }
    }
}
