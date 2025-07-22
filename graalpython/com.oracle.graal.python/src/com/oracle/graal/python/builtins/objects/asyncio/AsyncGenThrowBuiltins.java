/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.ErrorMessages.GENERATOR_IGNORED_EXIT;
import static com.oracle.graal.python.nodes.ErrorMessages.SEND_NON_NONE_TO_UNSTARTED_GENERATOR;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
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
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PAsyncGenAThrow)
public final class AsyncGenThrowBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = AsyncGenThrowBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AsyncGenThrowBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.am_await, isComplex = true)
    @GenerateNodeFactory
    public abstract static class Await extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doAwait(PAsyncGenAThrow self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class Iter extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIter(PAsyncGenAThrow self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class Next extends TpIterNextBuiltin {
        @Specialization
        public Object doSend(VirtualFrame frame, PAsyncGenAThrow self,
                        @Cached Send send) {
            return send.execute(frame, self, PNone.NONE);
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Send extends PythonBinaryBuiltinNode {
        @Specialization
        public Object send(VirtualFrame frame, PAsyncGenAThrow self, Object sent,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseReuse,
                        @Cached PRaiseNode raiseAlreadyRunning,
                        @Cached PRaiseNode raiseStopAsyncIteraion,
                        @Cached PRaiseNode raiseNonNodeToNewCoro,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode,
                        @Cached IsBuiltinObjectExactProfile isAGWrappedValue,
                        @Cached IsBuiltinObjectProfile isStopAsyncIter,
                        @Cached IsBuiltinObjectProfile isGeneratorExit,
                        @Cached PRaiseNode raiseStopIteration,
                        @Cached CommonGeneratorBuiltins.SendNode sendNode) {
            PAsyncGen gen = self.receiver;
            Object retval;

            if (self.getState() == AwaitableState.CLOSED) {
                throw raiseReuse.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_ATHROW);
            }

            // CPython checks for gi_frame_state here, but we don't have gi_frame_state.
            // This works for any testcase I could come up with.
            // https://github.com/python/cpython/blob/main/Objects/genobject.c#L2082-L2086
            if (self.receiver.isFinished()) {
                self.setState(AwaitableState.CLOSED);
                throw raiseStopIteration.raise(inliningTarget, PythonBuiltinClassType.StopIteration);
            }

            if (self.getState() == AwaitableState.INIT) {
                if (gen.isRunningAsync()) {
                    self.setState(AwaitableState.CLOSED);
                    throw raiseAlreadyRunning.raise(inliningTarget, PythonBuiltinClassType.RuntimeError); // todo
                    // error
                    // msg
                }

                if (gen.isClosed()) {
                    self.setState(AwaitableState.CLOSED);
                    throw raiseStopAsyncIteraion.raise(inliningTarget, PythonBuiltinClassType.StopAsyncIteration);
                }

                if (sent != PNone.NONE) {
                    throw raiseNonNodeToNewCoro.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, SEND_NON_NONE_TO_UNSTARTED_GENERATOR);
                }

                self.setState(AwaitableState.ITER);
                gen.setRunningAsync(true);

                if (self.arg1 == null) {
                    // aclose() mode
                    gen.markClosed();

                    try {
                        retval = throwNode.execute(frame, gen, PythonBuiltinClassType.GeneratorExit, PNone.NO_VALUE, PNone.NO_VALUE);
                    } catch (PException e) {
                        throw checkError(self, gen, e, inliningTarget, isStopAsyncIter, isGeneratorExit);
                    }
                    if (isAGWrappedValue.profileObject(inliningTarget, retval, PythonBuiltinClassType.PAsyncGenAWrappedValue)) {
                        throw yieldClose(inliningTarget, self, gen);
                    }
                } else {
                    // athrow mode
                    try {
                        retval = throwNode.execute(frame, gen, self.arg1, self.arg2, self.arg3);
                    } catch (PException e) {
                        PException exception = AsyncGenSendBuiltins.handleAGError(gen, e, inliningTarget, isStopAsyncIter, isGeneratorExit);
                        throw checkError(self, gen, exception, inliningTarget, isStopAsyncIter, isGeneratorExit);
                    }
                    return AsyncGenSendBuiltins.unwrapAGYield(gen, retval, inliningTarget, isAGWrappedValue);
                }
            }

            // getState() == ITER
            try {
                retval = sendNode.execute(frame, gen, sent);
            } catch (PException e) {
                if (self.arg1 != null) {
                    throw AsyncGenSendBuiltins.handleAGError(gen, e, inliningTarget, isStopAsyncIter, isGeneratorExit);
                } else {
                    // aclose
                    throw checkError(self, gen, e, inliningTarget, isStopAsyncIter, isGeneratorExit);
                }
            }
            if (self.arg1 != null) {
                return AsyncGenSendBuiltins.unwrapAGYield(gen, retval, inliningTarget, isAGWrappedValue);
            } else {
                // aclose
                if (isAGWrappedValue.profileObject(inliningTarget, retval, PythonBuiltinClassType.PAsyncGenAWrappedValue)) {
                    throw yieldClose(inliningTarget, self, gen);
                } else {
                    return retval;
                }
            }
        }

        static PException yieldClose(Node inliningTarget, PAsyncGenAThrow athrow, PAsyncGen gen) {
            gen.setRunningAsync(false);
            athrow.setState(AwaitableState.CLOSED);
            return PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.RuntimeError, GENERATOR_IGNORED_EXIT);
        }

        static PException checkError(PAsyncGenAThrow athrow, PAsyncGen gen, PException exception,
                        Node inliningTarget,
                        IsBuiltinObjectProfile isStopAsyncIter,
                        IsBuiltinObjectProfile isGenExit) {
            gen.setRunningAsync(false);
            athrow.setState(AwaitableState.CLOSED);
            if (athrow.arg1 == null && (isStopAsyncIter.profileException(inliningTarget, exception, PythonBuiltinClassType.StopAsyncIteration) ||
                            isGenExit.profileException(inliningTarget, exception, PythonBuiltinClassType.GeneratorExit))) {
                return PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.StopIteration);
            }
            return exception;
        }
    }

    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Throw extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PAsyncGenAThrow self, Object arg1, Object arg2, Object arg3);

        @NeverDefault
        public static Throw create() {
            return AsyncGenThrowBuiltinsFactory.ThrowFactory.create(null);
        }

        @Specialization
        public Object doThrow(VirtualFrame frame, PAsyncGenAThrow self, Object arg1, Object arg2, Object arg3,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseReuse,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode,
                        @Cached IsBuiltinObjectProfile isStopAsyncIteration,
                        @Cached IsBuiltinObjectProfile isGeneratorExit,
                        @Cached IsBuiltinObjectExactProfile isAGWrappedValue) {
            Object retval;

            if (self.getState() == AwaitableState.CLOSED) {
                throw raiseReuse.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_REUSE_ATHROW);
            }

            try {
                retval = throwNode.execute(frame, self.receiver, arg1, arg2, arg3);
            } catch (PException e) {
                if (self.arg1 != null) {
                    throw AsyncGenSendBuiltins.handleAGError(self.receiver, e, inliningTarget, isStopAsyncIteration, isGeneratorExit);
                } else {
                    // aclose()
                    if (isStopAsyncIteration.profileException(inliningTarget, e, PythonBuiltinClassType.StopAsyncIteration) ||
                                    isGeneratorExit.profileException(inliningTarget, e, PythonBuiltinClassType.GeneratorExit)) {
                        throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.StopIteration);
                    }
                    throw e;
                }
            }
            if (self.arg1 != null) {
                return AsyncGenSendBuiltins.unwrapAGYield(self.receiver, retval, inliningTarget, isAGWrappedValue);
            } else {
                if (isAGWrappedValue.profileObject(inliningTarget, retval, PythonBuiltinClassType.PAsyncGenAWrappedValue)) {
                    throw Send.yieldClose(inliningTarget, self, self.receiver);
                }
                return retval;
            }
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class Close extends PythonUnaryBuiltinNode {
        @Specialization
        public static Object close(PAsyncGenAThrow self) {
            self.setState(AwaitableState.CLOSED);
            return PNone.NONE;
        }
    }
}
