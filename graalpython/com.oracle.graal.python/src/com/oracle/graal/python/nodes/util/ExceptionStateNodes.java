/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContextFactory.GetThreadStateNodeGen;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExceptionStateNodes {
    /**
     * Use this node to forcefully get the current exception state. <it>Forcefully</it> means, if
     * the exception state is not provided in the frame, in the arguments or in the context, it will
     * do a full stack walk and request the exception state for the next time from the callers. The
     * returned object may escape to the value space.
     */
    public static final class GetCaughtExceptionNode extends Node {
        @Child private GetThreadStateNode getThreadStateNode;

        private final ConditionProfile nullFrameProfile = ConditionProfile.create();
        private final ConditionProfile hasExceptionProfile = ConditionProfile.create();
        private final ConditionProfile needsStackWalkProfile = ConditionProfile.create();

        public AbstractTruffleException execute(VirtualFrame frame) {
            if (nullFrameProfile.profile(frame == null)) {
                return getFromContext();
            }
            AbstractTruffleException e = PArguments.getException(frame);
            if (needsStackWalkProfile.profile(e == null)) {
                e = fromStackWalk();
                if (e == null) {
                    e = PException.NO_EXCEPTION;
                }
                // Set into frame to avoid doing the stack walk again
                PArguments.setException(frame, e);
            }
            return ensure(e);
        }

        public AbstractTruffleException executeFromNative() {
            return getFromContext();
        }

        private AbstractTruffleException getFromContext() {
            // contextRef acts as a branch profile
            if (getThreadStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getThreadStateNode = insert(GetThreadStateNodeGen.create());
            }
            PythonThreadState threadState = getThreadStateNode.executeCached();
            AbstractTruffleException fromContext = threadState.getCaughtException();
            if (needsStackWalkProfile.profile(fromContext == null)) {
                fromContext = fromStackWalk();

                // important: set into context to avoid stack walk next time
                threadState.setCaughtException(fromContext != null ? fromContext : PException.NO_EXCEPTION);
            }
            return ensure(fromContext);
        }

        private static AbstractTruffleException fromStackWalk() {
            // The very-slow path: This is the first time we want to fetch the exception state
            // from the context. The caller didn't know that it is necessary to provide the
            // exception in the context. So, we do a full stack walk until the first frame
            // having the exception state in the special slot. And we set the appropriate flag
            // on the root node such that the next time, we will find the exception state in the
            // context immediately.

            // TODO(fa) performance warning ?
            return fullStackWalk();
        }

        @TruffleBoundary
        public static AbstractTruffleException fullStackWalk() {

            return Truffle.getRuntime().iterateFrames(frameInstance -> {
                RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                RootNode rootNode = target.getRootNode();
                Node callNode = frameInstance.getCallNode();
                IndirectCallData.setEncapsulatingNeedsToPassExceptionState(callNode);
                if (rootNode instanceof PRootNode pRootNode) {
                    pRootNode.setNeedsExceptionState();
                    Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                    return PArguments.getException(frame);
                }
                return null;
            });

        }

        private AbstractTruffleException ensure(AbstractTruffleException e) {
            if (hasExceptionProfile.profile(e == PException.NO_EXCEPTION)) {
                return null;
            } else {
                return e;
            }
        }

        @NeverDefault
        public static GetCaughtExceptionNode create() {
            return new GetCaughtExceptionNode();
        }
    }
}
