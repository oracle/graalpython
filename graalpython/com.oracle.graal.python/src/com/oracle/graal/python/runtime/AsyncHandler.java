/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * A handler for asynchronous actions events that need to be handled on a main thread of execution,
 * including signals and finalization.
 */
public class AsyncHandler {
    /**
     * An action to be run triggered by an asynchronous event.
     */
    public interface AsyncAction {
        void execute(PythonContext context);
    }

    public abstract static class AsyncPythonAction implements AsyncAction {
        /**
         * The object to call via a standard Python call
         */
        protected abstract Object callable();

        /**
         * The arguments to pass to the call
         */
        protected abstract Object[] arguments();

        /**
         * If the arguments need to include an element for the currently executing frame upon which
         * this async action is triggered, this method should return something >= 0. The array
         * returned by {@link #arguments()} should have a space for the frame already, as it will be
         * filled in without growing the arguments array.
         */
        protected int frameIndex() {
            return -1;
        }

        @Override
        public final void execute(PythonContext context) {
            Object callable = callable();
            if (callable != null) {
                Object[] arguments = arguments();
                Object[] args = PArguments.create(arguments.length + CallRootNode.ASYNC_ARG_COUNT);
                System.arraycopy(arguments, 0, args, PArguments.USER_ARGUMENTS_OFFSET + CallRootNode.ASYNC_ARG_COUNT, arguments.length);
                PArguments.setArgument(args, CallRootNode.ASYNC_CALLABLE_INDEX, callable);
                PArguments.setArgument(args, CallRootNode.ASYNC_FRAME_INDEX_INDEX, frameIndex());

                try {
                    GenericInvokeNode.getUncached().execute(context.getAsyncHandler().callTarget, args);
                } catch (RuntimeException e) {
                    // we cannot raise the exception here (well, we could, but CPython
                    // doesn't), so we do what they do and just print it

                    // Just print a Python-like stack trace; CPython does the same (see
                    // 'weakrefobject.c: handle_callback')
                    ExceptionUtils.printPythonLikeStackTrace(e);
                }
            }
        }
    }

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3, new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    });

    private final PythonContext context;
    private final ConcurrentLinkedQueue<AsyncAction> scheduledActions = new ConcurrentLinkedQueue<>();
    private volatile boolean hasScheduledAction = false;
    private final Lock executingScheduledActions = new ReentrantLock();
    private static final int ASYNC_ACTION_DELAY = 15; // chosen by a fair D20 dice roll

    private class AsyncRunnable implements Runnable {
        private final Supplier<AsyncAction> actionSupplier;

        public AsyncRunnable(Supplier<AsyncAction> actionSupplier) {
            this.actionSupplier = actionSupplier;
        }

        public void run() {
            AsyncAction asyncAction = actionSupplier.get();
            if (asyncAction != null) {
                // If there's thread executing scheduled actions right now,
                // we wait until adding the next work item
                executingScheduledActions.lock();
                try {
                    scheduledActions.add(asyncAction);
                    hasScheduledAction = true;
                } finally {
                    executingScheduledActions.unlock();
                }
            }
        }
    }

    private static class CallRootNode extends PRootNode {
        static final int ASYNC_CALLABLE_INDEX = 0;
        static final int ASYNC_FRAME_INDEX_INDEX = 1;
        static final int ASYNC_ARG_COUNT = 2;

        @Child private CallNode callNode = CallNode.create();
        @Child private ReadCallerFrameNode readCallerFrameNode = ReadCallerFrameNode.create();
        @Child private CalleeContext calleeContext = CalleeContext.create();

        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        protected CallRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, profile);
            Object[] frameArguments = frame.getArguments();
            Object callable = PArguments.getArgument(frameArguments, ASYNC_CALLABLE_INDEX);
            int frameIndex = (int) PArguments.getArgument(frameArguments, ASYNC_FRAME_INDEX_INDEX);
            Object[] arguments = Arrays.copyOfRange(frameArguments, PArguments.USER_ARGUMENTS_OFFSET + ASYNC_ARG_COUNT, frameArguments.length);

            if (frameIndex >= 0) {
                arguments[frameIndex] = readCallerFrameNode.executeWith(frame, 0);
            }
            try {
                return callNode.execute(frame, callable, arguments);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return Signature.EMPTY;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }
    }

    private final RootCallTarget callTarget;

    AsyncHandler(PythonContext context) {
        this.context = context;
        this.callTarget = Truffle.getRuntime().createCallTarget(new CallRootNode(context.getLanguage()));
    }

    void registerAction(Supplier<AsyncAction> actionSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        if (PythonLanguage.getContext().getOption(PythonOptions.NoAsyncActions)) {
            return;
        }
        executorService.scheduleWithFixedDelay(new AsyncRunnable(actionSupplier), ASYNC_ACTION_DELAY, ASYNC_ACTION_DELAY, TimeUnit.MILLISECONDS);
    }

    void triggerAsyncActions(VirtualFrame frame, BranchProfile actionProfile) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, hasScheduledAction)) {
            actionProfile.enter();
            IndirectCallContext.enter(frame, context, null);
            try {
                CompilerDirectives.transferToInterpreter();
                processAsyncActions();
            } finally {
                IndirectCallContext.exit(frame, context, null);
            }
        }
    }

    /**
     * It's fine that there is a race between checking the hasScheduledAction flag and processing
     * actions, we use the executingScheduledActions lock to ensure that only one thread is
     * processing and that no asynchronous handler thread would set it again while we're processing.
     * While the nice scenario would be any variation of:
     *
     * <ul>
     * <li>Thread2 - acquireLock, pushWork, setFlag, releaseLock</li>
     * <li>Thread1 - checkFlag, acquireLock, resetFlag, processActions, releaseLock</li>
     * </ul>
     *
     * <ul>
     * <li>Thread1 - checkFlag</li>
     * <li>Thread2 - acquireLock, pushWork, setFlag, releaseLock</li>
     * <li>Thread1 - acquireLock, resetFlag, processActions, releaseLock</li>
     * </ul>
     *
     * it's also fine if we get into a race for example like this:
     *
     * <ul>
     * <li>Thread2 - acquireLock, pushWork, setFlag</li>
     * <li>Thread1 - checkFlag, tryAcquireLock, bail out</li>
     * <li>Thread2 - releaseLock</li>
     * </ul>
     *
     * because Thread1 is sure to check the flag again soon enough, and very likely much sooner than
     * the {@value #ASYNC_ACTION_DELAY} ms delay between successive runs of the async handler
     * threads (Thread2 in this example). Of course, there can be more than one handler thread, but
     * it's unlikely that there are so many that it would completely saturate the ability to process
     * async actions on the main thread, because there's only one per "type" of async thing (e.g. 1
     * for weakref finalizers, 1 for signals, 1 for destructors).
     */
    private void processAsyncActions() {
        if (executingScheduledActions.tryLock()) {
            hasScheduledAction = false;
            try {
                ConcurrentLinkedQueue<AsyncAction> actions = scheduledActions;
                AsyncAction action;
                while ((action = actions.poll()) != null) {
                    action.execute(context);
                }
            } finally {
                executingScheduledActions.unlock();
            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
