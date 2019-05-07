/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.runtime;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * An ExecutionContext ensures proper entry and exit for Python calls on both
 * sides of the call, and depending on whether the other side is also a Python
 * frame.
 */
@ValueType
public abstract class ExecutionContext implements AutoCloseable {
    /**
     * Prepare a call from a Python frame to a Python function.
     */
    public static final ExecutionContext call(VirtualFrame currentFrame, Object[] callArguments, RootCallTarget callTarget, Node callNode) {
        return new CallContext(currentFrame, callArguments, callTarget, callNode);
    }

    /**
     * Prepare a call from a Python frame to a foreign callable.
     */
    public static final ExecutionContext interopCall(VirtualFrame frame, PythonContext context, Node callNode) {
        return new ForeignCallContext(frame, context, callNode);
    }

    /**
     * Wrap the execution of a Python callee called from a Python frame.
     */
    public static final ExecutionContext callee(VirtualFrame frame, RootNode node) {
        return new CalleeContext(frame, node);
    }

    /**
     * Prepare a call from a foreign frame to a Python function.
     */
    public static final ExecutionContext interopCallee(PythonContext context, Object[] pArguments) {
        assert PArguments.isPythonFrame(pArguments) : "this must be called with the PArguments to prepare them";
        return new ForeignToPythonCallContext(context, pArguments);
    }

    @Override
    public abstract void close();

    private static final class CallContext extends ExecutionContext {
        private CallContext(VirtualFrame frame, Object[] callArguments, RootCallTarget callTarget, Node callNode) {
            // equivalent to PyPy's ExecutionContext.enter `frame.f_backref =
            // self.topframeref` we here pass the current top frame reference to
            // the next frame. An optimization we do is to only pass the frame
            // info if the caller requested it, otherwise they'll have to deopt
            // and walk the stack up once.

            // n.b.: The class cast should always be correct, since this context
            // must only be used when calling from Python to Python
            PRootNode calleeRootNode = (PRootNode) callTarget.getRootNode();
            if (calleeRootNode.needsCallerFrame()) {
                PFrame.Reference thisInfo = PArguments.getCurrentFrameInfo(frame);
                thisInfo.setFrame(frame.materialize());
                thisInfo.setCallNode(callNode);
                PArguments.setCallerFrameInfo(callArguments, thisInfo);
            }
            if (calleeRootNode.needsExceptionState()) {
                PException curExc = PArguments.getException(frame);
                if (curExc == PException.LAZY_FETCH_EXCEPTION) {
                    // bad, but we must provide the exception state

                    // TODO: frames: check that this also set
                    // needsExceptionState on our own root node
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    PException fromStackWalk = GetCaughtExceptionNode.fullStackWalk();
                    curExc = fromStackWalk != null ? fromStackWalk : PException.NO_EXCEPTION;
                    // now, set in our args, such that we won't do this again
                    PArguments.setException(frame, curExc);
                }
                PArguments.setException(callArguments, curExc);
            }
        }

        @Override
        public void close() {
            // Nothing to do at this point
        }
    }

    private static final class CalleeContext extends ExecutionContext {
        private final VirtualFrame frame;
        private final RootNode node;

        private CalleeContext(VirtualFrame frame, RootNode node) {
            this.frame = frame;
            this.node = node;
            // tfel: Create our frame reference here and store it so that
            // there's no reference to it from the caller side.
            PFrame.Reference thisFrameRef = new PFrame.Reference();
            Object customLocals = PArguments.getCustomLocals(frame);
            PArguments.setCurrentFrameInfo(frame, thisFrameRef);
            // tfel: If there are custom locals, write them into an (incomplete)
            // PFrame here
            if (customLocals != null) {
                thisFrameRef.setCustomLocals(customLocals);
            }
            CompilerDirectives.ensureVirtualized(this);
        }

        @Override
        public void close() {
            /* equivalent to PyPy's ExecutionContext.leave. Note that
             * <tt>got_exception</tt> in their code is handled automatically by
             * the Truffle lazy exceptions, so here we only deal with explicitly
             * escaped frames.
             */
            PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
            if (info.isEscaped()) {
                // force the frame so that it can be accessed later
                info.materialize(frame, node);
                // if this frame escaped we must ensure that also f_back does
                PFrame.Reference callerInfo = PArguments.getCallerFrameInfo(frame);
                if (callerInfo == null) {
                    // we didn't request the caller frame reference. now we need
                    // it.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ((PRootNode) ((RootCallTarget) Truffle.getRuntime().getCurrentFrame().getCallTarget()).getRootNode()).setNeedsCallerFrame();
                    FrameInstance callerFrameInstance = Truffle.getRuntime().getCallerFrame();
                    if (callerFrameInstance != null) {
                        Frame callerFrame = callerFrameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        callerInfo = PArguments.getCurrentFrameInfo(callerFrame);
                    } else {
                        callerInfo = PFrame.Reference.EMPTY;
                    }
                }
                callerInfo.markAsEscaped();
                info.setBackref(callerInfo);
            }
        }
    }

    private static final class ForeignCallContext extends ExecutionContext {
        private final PythonContext context;

        private ForeignCallContext(VirtualFrame frame, PythonContext context, Node callNode) {
            this.context = context;
            assert context.popTopFrameInfo() == null : "trying to call from Python to a foreign function, but we didn't clear the topframeref. " +
                "This indicates that a call into Python code happened without a proper enter through ForeignToPythonCallContext";
            PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
            info.setCallNode(callNode);
            // TODO: frames: add an assumption that none of the callers interop calls ever need these infos
            context.setTopFrameInfo(info);
        }

        @Override
        public void close() {
            context.popTopFrameInfo();
        }
    }

    private static final class ForeignToPythonCallContext extends ExecutionContext {
        private final PythonContext context;
        private final PFrame.Reference topframeref;

        private ForeignToPythonCallContext(PythonContext context, Object[] pArguments) {
            this.context = context;
            this.topframeref = context.popTopFrameInfo();
            PArguments.setCallerFrameInfo(pArguments, this.topframeref);
        }

        @Override
        public void close() {
            // Note that the Python callee, if it escaped, has already been
            // materialized due to a CalleeContext in its RootNode. If this
            // topframeref was marked as escaped, it'll be materialized at the
            // latest needed time
            context.setTopFrameInfo(this.topframeref);
        }
    }
}
