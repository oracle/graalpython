package com.oracle.graal.python.runtime;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFrameNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
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
         * If the arguments need to include an element for the currently executing frame upon which
         * this async action is triggered, this method should return something >= 0. The array
         * returned by {@link #arguments()} should have a space for the frame already, as it will be
         * filled in without growing the arguments array.
         */
        default int frameIndex() {
            return -1;
        }
    }

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ConcurrentLinkedQueue<AsyncAction> scheduledActions = new ConcurrentLinkedQueue<>();
    private AtomicBoolean hasScheduledAction = new AtomicBoolean(false);
    private AtomicBoolean executingActions = new AtomicBoolean(false);
    private static final int ASYNC_ACTION_DELAY = 100;

    private class AsyncRunnable implements Runnable {
        private final Supplier<AsyncAction> actionSupplier;

        public AsyncRunnable(Supplier<AsyncAction> actionSupplier) {
            this.actionSupplier = actionSupplier;
        }

        public void run() {
            AsyncAction asyncAction = actionSupplier.get();
            if (asyncAction != null) {
                scheduledActions.add(asyncAction);
                hasScheduledAction.set(true);
            }
        }
    }

    private static class CallRootNode extends RootNode {
        @Child CallNode callNode = CallNode.create();
        @Child GetFrameNode getFrameNode = GetFrameNode.create();

        protected CallRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArguments = frame.getArguments();
            Object callable = frameArguments[0];
            int frameIndex = (int) frameArguments[1];
            Object[] arguments = Arrays.copyOfRange(frameArguments, 2, frameArguments.length);
            if (frameIndex >= 0) {
                arguments[frameIndex] = getFrameNode.execute(2);
            }
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

    void triggerAsyncActions() {
        if (executingActions.compareAndSet(false, true)) {
            if (hasScheduledAction.compareAndSet(true, false)) {
                CompilerDirectives.transferToInterpreter();
                // TODO: (tfel) - for now all async actions are slow path
                ConcurrentLinkedQueue<AsyncAction> actions = scheduledActions;
                for (AsyncAction action : actions) {
                    Object[] arguments = action.arguments();
                    Object[] args = new Object[arguments.length + 2];
                    System.arraycopy(arguments, 0, args, 2, arguments.length);
                    args[0] = action.callable();
                    args[1] = action.frameIndex();
                    try {
                        callTarget.call(args);
                    } catch (RuntimeException e) {
                        // we cannot raise the exception here (well, we could, but CPython
                        // doesn't), so we do what they do and just print it

                        // TODO: print a nice Python stacktrace
                        e.printStackTrace();
                    }
                }
            }
            executingActions.set(false);
        }
    }
}
