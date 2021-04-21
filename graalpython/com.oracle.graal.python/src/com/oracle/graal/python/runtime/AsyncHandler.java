/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;

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
                PythonUtils.arraycopy(arguments, 0, args, PArguments.USER_ARGUMENTS_OFFSET + CallRootNode.ASYNC_ARG_COUNT, arguments.length);
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

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(6, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    });

    private final WeakReference<PythonContext> context;
    private static final int ASYNC_ACTION_DELAY = 25;
    private static final int GIL_RELEASE_DELAY = 100;

    private class AsyncRunnable implements Runnable {
        private final Supplier<AsyncAction> actionSupplier;

        public AsyncRunnable(Supplier<AsyncAction> actionSupplier) {
            this.actionSupplier = actionSupplier;
        }

        @Override
        public void run() {
            final AsyncAction asyncAction = actionSupplier.get();
            if (asyncAction != null) {
                final PythonContext ctx = context.get();
                if (ctx != null) {
                    ctx.getEnv().submitThreadLocal(null, new ThreadLocalAction(true, false) {
                        @Override
                        @SuppressWarnings("try")
                        protected void perform(ThreadLocalAction.Access access) {
                            GilNode gil = GilNode.getUncached();
                            int state = gil.tryAcquire();
                            if (state >= 0) {
                                boolean prev = TruffleSafepoint.getCurrent().setAllowSideEffects(false);
                                try {
                                    asyncAction.execute(ctx);
                                } finally {
                                    gil.release(state == 1);
                                    TruffleSafepoint.getCurrent().setAllowSideEffects(prev);
                                }
                            }
                        }
                    });
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

        protected CallRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
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
        this.context = new WeakReference<>(context);
        this.callTarget = context.getLanguage().createCachedCallTarget(l -> new CallRootNode(l), CallRootNode.class);
    }

    void registerAction(Supplier<AsyncAction> actionSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        if (PythonLanguage.getContext().getOption(PythonOptions.NoAsyncActions)) {
            return;
        }
        executorService.scheduleWithFixedDelay(new AsyncRunnable(actionSupplier), ASYNC_ACTION_DELAY, ASYNC_ACTION_DELAY, TimeUnit.MILLISECONDS);
    }

    void activateGIL() {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext ctx = context.get();
        Env env = ctx.getEnv();
        final AtomicBoolean gilReleaseRequested = new AtomicBoolean(false);
        executorService.scheduleWithFixedDelay(() -> {
            if (gilReleaseRequested.compareAndSet(false, true)) {
                env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                    @Override
                    protected void perform(ThreadLocalAction.Access access) {
                        // it may happen that we request a GIL release and no thread is currently
                        // holding the GIL (e.g. all are sleeping). We still need to tick again
                        // later, so we reset the gilReleaseRequested flag even when the thread in
                        // question isn't actually holding it.
                        gilReleaseRequested.set(false);
                        GilNode gil = GilNode.getUncached();
                        if (gil.tryRelease()) {
                            Thread.yield();
                            gil.acquire(access.getLocation());
                        }
                    }
                });
            }
        }, GIL_RELEASE_DELAY, GIL_RELEASE_DELAY, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    public static class SharedFinalizer {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(SharedFinalizer.class);

        private final PythonContext pythonContext;
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        /**
         * This is a Set of references to keep them alive after their gc collected referents.
         */
        private final ConcurrentMap<FinalizableReference, FinalizableReference> liveReferencesSet = new ConcurrentHashMap<>();

        public SharedFinalizer(PythonContext context) {
            this.pythonContext = context;
        }

        /**
         * Finalizable references is a utility class for freeing resources that {@link Runtime#gc()}
         * is unaware of, such as of heap allocation through native interface. Resources that can be
         * freed with {@link Runtime#gc()} should not extend this class.
         */
        public abstract static class FinalizableReference extends PhantomReference<Object> {
            private final Object reference;
            private boolean released;

            public FinalizableReference(Object referent, Object reference, SharedFinalizer sharedFinalizer) {
                super(referent, sharedFinalizer.queue);
                assert reference != null;
                this.reference = reference;
                addLiveReference(sharedFinalizer, this);
            }

            /**
             * We'll keep a reference for the FinalizableReference object until the async handler
             * schedule the collect process.
             */
            @TruffleBoundary
            private static void addLiveReference(SharedFinalizer sharedFinalizer, FinalizableReference ref) {
                sharedFinalizer.liveReferencesSet.put(ref, ref);
            }

            /**
             *
             * @return the undelying reference which is usually a native pointer.
             */
            public final Object getReference() {
                return reference;
            }

            public final boolean isReleased() {
                return released;
            }

            /**
             * Mark the FinalizableReference as freed in case it has been freed elsewhare. This will
             * avoid double-freeing the reference.
             */
            public final void markReleased() {
                this.released = true;
            }

            /**
             * This implements the proper way to free the allocated resources associated with the
             * reference.
             */
            public abstract AsyncHandler.AsyncAction release();
        }

        static class SharedFinalizerErrorCallback implements AsyncHandler.AsyncAction {

            private final Exception exception;
            private final FinalizableReference referece; // problematic reference

            SharedFinalizerErrorCallback(FinalizableReference referece, Exception e) {
                this.exception = e;
                this.referece = referece;
            }

            @Override
            public void execute(PythonContext context) {
                LOGGER.severe(String.format("Error during async action for %s caused by %s", referece.getClass().getSimpleName(), exception.getMessage()));
            }
        }

        /**
         * We register the Async action once on the first encounter of a creation of
         * {@link FinalizableReference}. This will reduce unnecessary Async thread load when there
         * isn't any enqueued references.
         */
        public void registerAsyncAction() {
            pythonContext.registerAsyncAction(() -> {
                Reference<? extends Object> reference = null;
                try {
                    reference = queue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (reference instanceof FinalizableReference) {
                    FinalizableReference object = (FinalizableReference) reference;
                    try {
                        liveReferencesSet.remove(object);
                        if (object.isReleased()) {
                            return null;
                        }
                        return object.release();
                    } catch (Exception e) {
                        return new SharedFinalizerErrorCallback(object, e);
                    }
                }
                return null;
            });

        }
    }
}
