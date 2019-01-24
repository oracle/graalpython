package com.oracle.graal.python.runtime;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A handler for asynchronous actions events that need to be handled on a main thread of execution,
 * including signals and finalization.
 */
public class AsyncHandler {
    /**
     * An action to be run triggered by an asynchronous event.
     */
    public interface AsyncAction {
        /**
         * The object to call via a standard Python call
         */
        public Object callable();

        /**
         * The arguments to pass to the call
         */
        public Object[] arguments();

        /**
         * If the arguments include an element for the currently executing frame upon which this
         * async action is triggered, this method should return something >= 0. The array returned
         * by {@link #arguments()} should have a space for the frame already.
         */
        default int frameIndex() {
            return -1;
        }
    }

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private AtomicMarkableReference<ConcurrentLinkedQueue<AsyncAction>> scheduledActions = new AtomicMarkableReference<>(new ConcurrentLinkedQueue<>(), false);
    private AtomicBoolean hasScheduledAction = new AtomicBoolean(false);
    private static final int ASYNC_ACTION_DELAY = 3;

    private class AsyncRunnable implements Runnable {
        private final Supplier<AsyncAction> actionSupplier;

        public AsyncRunnable(Supplier<AsyncAction> actionSupplier) {
            this.actionSupplier = actionSupplier;
        }

        public void run() {
            AsyncAction asyncAction = actionSupplier.get();
            if (asyncAction != null) {
                scheduledActions.getReference().add(asyncAction);
                hasScheduledAction.set(true);
            }
        }
    }

    private static class CallRootNode extends RootNode {
        @Child CallNode callNode = CallNode.create();

        protected CallRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArguments = frame.getArguments();
            Object callable = frameArguments[0];
            Object[] arguments = Arrays.copyOfRange(frameArguments, 1, frameArguments.length);
            return callNode.execute(frame, callable, arguments);
        }
    }

    private final CallTarget callTarget;
    @Child CallNode callNode = CallNode.create();

    AsyncHandler(PythonLanguage language) {
        callTarget = Truffle.getRuntime().createCallTarget(new CallRootNode(language));
    }

    void registerAction(Supplier<AsyncAction> actionSupplier) {
        executorService.scheduleWithFixedDelay(new AsyncRunnable(actionSupplier), ASYNC_ACTION_DELAY, ASYNC_ACTION_DELAY, TimeUnit.MILLISECONDS);
    }

    void triggerAsyncActions(Frame frame, PythonObjectFactory factory) {
        if (hasScheduledAction.getAndSet(false)) {
            // TODO: (tfel) - for now all async actions are slow path
            CompilerDirectives.transferToInterpreter();
            ConcurrentLinkedQueue<AsyncAction> actions = scheduledActions.getReference();
            if (scheduledActions.compareAndSet(actions, actions, false, true)) {
                // if we're here, we marked the scheduled actions as executing, so no other thread
                // will enter this branch (other threads would just return)
                try {
                    for (AsyncAction action : actions) {
                        Object[] arguments = action.arguments();
                        Object[] args = new Object[arguments.length + 1];
                        System.arraycopy(arguments, 0, args, 1, arguments.length);
                        args[0] = action.callable();
                        if (action.frameIndex() >= 0) {
                            args[action.frameIndex() + 1] = factory.createPFrame(frame);
                        }
                        callTarget.call(args);
                    }
                } finally {
                    // unmark, we're done here
                    scheduledActions.compareAndSet(actions, actions, true, false);
                }
            }
        }
    }
}
