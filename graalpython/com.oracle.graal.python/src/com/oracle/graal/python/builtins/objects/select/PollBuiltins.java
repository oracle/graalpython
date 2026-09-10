/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.select;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.TimeUtils.MS_TO_NS;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PPoll)
public final class PollBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PollBuiltinsFactory.getFactories();
    }

    @Builtin(name = "register", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "fd", "eventmask"})
    @GenerateNodeFactory
    abstract static class RegisterNode extends PythonBuiltinNode {
        @Specialization
        static PNone register(VirtualFrame frame, PPoll self, Object fdObject, Object eventmask,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PyLongAsLongAndOverflowNode asLong,
                        @Cached PRaiseNode raiseNode) {
            int fd = asFileDescriptor.execute(frame, inliningTarget, fdObject);
            int events = PGuards.isNoValue(eventmask) ? defaultEventMask() : asUnsignedShort(frame, inliningTarget, eventmask, longCheck, asLong, raiseNode);
            self.register(fd, events);
            return PNone.NONE;
        }
    }

    @Builtin(name = "modify", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "fd", "eventmask"})
    @GenerateNodeFactory
    abstract static class ModifyNode extends PythonBuiltinNode {
        @Specialization
        static PNone modify(VirtualFrame frame, PPoll self, Object fdObject, Object eventmask,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor,
                        @Cached PyLongCheckNode longCheck,
                        @Cached PyLongAsLongAndOverflowNode asLong,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            int fd = asFileDescriptor.execute(frame, inliningTarget, fdObject);
            int events = asUnsignedShort(frame, inliningTarget, eventmask, longCheck, asLong, raiseNode);
            if (!self.modify(fd, events)) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.ENOENT);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "unregister", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "fd"})
    @GenerateNodeFactory
    abstract static class UnregisterNode extends PythonBuiltinNode {
        @Specialization
        static PNone unregister(VirtualFrame frame, PPoll self, Object fdObject,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor,
                        @Cached PRaiseNode raiseNode) {
            int fd = asFileDescriptor.execute(frame, inliningTarget, fdObject);
            if (!self.unregister(fd)) {
                throw raiseNode.raise(inliningTarget, KeyError, new Object[]{fd});
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "poll", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "timeout"})
    @GenerateNodeFactory
    abstract static class PollNode extends PythonBuiltinNode {
        @Specialization
        static PList poll(VirtualFrame frame, PPoll self, Object timeoutObject,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyTimeFromObjectNode fromTime,
                        @Cached IsBuiltinObjectProfile typeErrorProfile,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            int timeoutMs = -1;
            long timeoutNs = -1;
            if (!(timeoutObject instanceof PNone)) {
                try {
                    timeoutNs = fromTime.execute(frame, inliningTarget, timeoutObject, RoundType.TIMEOUT, MS_TO_NS);
                } catch (PException e) {
                    e.expectTypeError(inliningTarget, typeErrorProfile);
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TIMEOUT_MUST_BE_INTEGER_OR_NONE);
                }
                long timeoutMsLong = TimeUtils.pyTimeDivide(timeoutNs, MS_TO_NS);
                if (timeoutMsLong < Integer.MIN_VALUE || timeoutMsLong > Integer.MAX_VALUE) {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.TIMEOUT_IS_TOO_LARGE);
                }
                if (timeoutMsLong >= 0) {
                    timeoutMs = (int) timeoutMsLong;
                }
            }

            if (!self.startPoll()) {
                throw raiseNode.raise(inliningTarget, RuntimeError, ErrorMessages.CONCURRENT_POLL_INVOCATION);
            }
            int[] pollFds = self.getPollFds();
            int[] pollEvents = self.getPollEvents();
            int[] pollRevents = self.getPollRevents();
            boolean timedOut = false;
            long startNano = timeoutMs >= 0 ? System.nanoTime() : 0;
            try {
                while (true) {
                    try {
                        gil.release(true);
                        try {
                            posixLib.poll(PosixSupport.get(inliningTarget), pollFds, pollEvents, pollRevents, timeoutMs);
                        } finally {
                            gil.acquire();
                        }
                        break;
                    } catch (PosixException e) {
                        if (!e.hasErrno(OSErrorEnum.EINTR)) {
                            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                        }
                        PythonContext.triggerAsyncActions(inliningTarget);
                        if (timeoutMs >= 0) {
                            long remainingNs = timeoutNs - (System.nanoTime() - startNano);
                            if (remainingNs <= 0) {
                                timedOut = true;
                                break;
                            }
                            timeoutMs = (int) TimeUtils.pyTimeDivide(remainingNs, MS_TO_NS);
                        }
                    }
                }
            } finally {
                self.finishPoll();
            }

            if (timedOut) {
                return PFactory.createList(language);
            }

            int resultSize = 0;
            for (int revents : pollRevents) {
                if (revents != 0) {
                    resultSize++;
                }
            }
            Object[] result = new Object[resultSize];
            int resultIndex = 0;
            for (int i = 0; i < pollRevents.length; i++) {
                if (pollRevents[i] != 0) {
                    result[resultIndex++] = PFactory.createTuple(language, new Object[]{pollFds[i], pollRevents[i]});
                }
            }
            return PFactory.createList(language, result);
        }
    }

    private static int defaultEventMask() {
        return PosixConstants.POLLIN.getValueIfDefined() | PosixConstants.POLLPRI.getValueIfDefined() | PosixConstants.POLLOUT.getValueIfDefined();
    }

    private static int asUnsignedShort(VirtualFrame frame, Node inliningTarget, Object value, PyLongCheckNode longCheck,
                    PyLongAsLongAndOverflowNode asLong, PRaiseNode raiseNode) {
        if (!longCheck.execute(inliningTarget, value)) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
        }
        long result;
        try {
            result = asLong.execute(frame, inliningTarget, value);
        } catch (OverflowException e) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C unsigned long");
        }
        if (result < 0) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.VALUE_MUST_BE_POSITIVE);
        }
        if (result > 0xffff) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C unsigned short");
        }
        return (int) result;
    }
}
