/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;

/**
 * Coordinates exclusive ownership of the reference queue between the watcher and Python
 * execution threads. The watcher only removes the first reference. All processing and the
 * remaining queue drain happen on a Python thread while it owns the GIL.
 */
public final class ReferenceQueueCoordinator {
    enum State {
        /**
         * Initial state and steady state when no watcher was started, for example during
         * preinitialization, with async actions disabled, or when system-thread creation
         * failed. Python threads poll the reference queue directly in this state. Shutdown
         * uses {@link #STOPPING} and {@link #STOPPED} instead.
         */
        INACTIVE,
        WATCHING,
        DRAINING,
        DISABLED,
        STOPPING,
        STOPPED
    }

    private static final class DisabledReference {
        final Reference<?> reference;

        DisabledReference(Reference<?> reference) {
            this.reference = reference;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();
    private final AtomicReference<Object> state = new AtomicReference<>(State.INACTIVE);
    private Thread watcherThread;
    private volatile boolean watcherInRemove;
    private volatile boolean pauseRequested;

    /**
     * Takes ownership of the watcher thread, publishes the watching state, and starts the thread.
     */
    public void start(Thread thread) {
        lock.lock();
        try {
            assert state.get() == State.INACTIVE : state.get();
            watcherThread = thread;
            state.set(State.WATCHING);
        } finally {
            lock.unlock();
        }
        try {
            thread.start();
        } catch (RuntimeException e) {
            stopped();
            throw e;
        }
    }

    public boolean isActive() {
        Object current = state.get();
        return current != State.INACTIVE && current != State.STOPPING && current != State.STOPPED;
    }

    /**
     * Called by the watcher before entering {@link ReferenceQueue#remove()}.
     */
    public boolean beginWatcherRemove() {
        lock.lock();
        try {
            while (state.get() != State.WATCHING || pauseRequested) {
                Object current = state.get();
                if (current == State.STOPPING || current == State.STOPPED) {
                    return false;
                }
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    // Lifecycle transitions set the state before interrupting the watcher.
                }
            }
            watcherInRemove = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Reference<?> blockingRemove(ReferenceQueue<Object> queue) {
        try {
            return queue.remove();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * Completes one watcher remove operation and returns its handoff, or {@code null}
     * when no action should be submitted.
     */
    public Reference<?> endWatcherRemove(Reference<?> reference) {
        assert watcherInRemove;
        Reference<?> handoff = null;
        if (reference != null) {
            if (state.compareAndSet(State.WATCHING, reference)) {
                handoff = pauseRequested ? null : reference;
            }
        }
        watcherInRemove = false;
        signalStateChanged();
        return handoff;
    }

    /**
     * Claims a reference handed off by the watcher. Returns the reference if the claim
     * succeeded, or {@code null} if another thread or a lifecycle transition won the race.
     */
    public Reference<?> beginWatcherDrain(Reference<?> reference) {
        if (!state.compareAndSet(reference, State.DRAINING)) {
            return null;
        }
        return reference;
    }

    public Reference<?> getPendingReference() {
        Object current = state.get();
        return current instanceof Reference<?> reference ? reference : null;
    }

    Object getState() {
        return state.get();
    }

    /**
     * Claims the queue for a forced drain. The returned value is the previous coordinator
     * state: a {@link Reference} is the first entry to process and {@link State#WATCHING}
     * means that the claim succeeded without a watcher handoff. {@code null} means that the
     * queue could not be claimed.
     */
    public Object beginForcedDrain(Node location) {
        lock.lock();
        try {
            Object current = state.get();
            if (current == State.INACTIVE) {
                return null;
            }
            if (current == State.STOPPING || current == State.STOPPED || current == State.DRAINING || current == State.DISABLED || current instanceof DisabledReference) {
                return null;
            }
            pauseRequested = true;
            if (watcherInRemove) {
                watcherThread.interrupt();
                while (watcherInRemove) {
                    TruffleSafepoint.setBlockedThreadInterruptible(location, Condition::await, stateChanged);
                }
            }
            current = state.get();
            if (current == State.STOPPING || current == State.STOPPED || current == State.DRAINING || current == State.DISABLED || current instanceof DisabledReference ||
                            !state.compareAndSet(current, State.DRAINING)) {
                pauseRequested = false;
                stateChanged.signalAll();
                return null;
            }
            pauseRequested = false;
            return current;
        } finally {
            lock.unlock();
        }
    }

    public void finishDrain() {
        lock.lock();
        try {
            if (state.compareAndSet(State.DRAINING, State.WATCHING)) {
                stateChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean disable(Node location) {
        lock.lock();
        try {
            Object current = state.get();
            if (current == State.INACTIVE) {
                return true;
            }
            if (current == State.DISABLED || current instanceof DisabledReference || current == State.DRAINING || current == State.STOPPING || current == State.STOPPED) {
                return false;
            }
            pauseRequested = true;
            if (watcherInRemove) {
                watcherThread.interrupt();
                while (watcherInRemove) {
                    TruffleSafepoint.setBlockedThreadInterruptible(location, Condition::await, stateChanged);
                }
            }
            current = state.get();
            if (current == State.DISABLED || current instanceof DisabledReference || current == State.DRAINING || current == State.STOPPING || current == State.STOPPED) {
                pauseRequested = false;
                stateChanged.signalAll();
                return false;
            }
            Object disabledState = current instanceof Reference<?> reference ? new DisabledReference(reference) : State.DISABLED;
            if (!state.compareAndSet(current, disabledState)) {
                pauseRequested = false;
                stateChanged.signalAll();
                return false;
            }
            pauseRequested = false;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-enables the watcher without processing references on the native GC call stack.
     */
    public void enable() {
        lock.lock();
        try {
            Object current = state.get();
            Object enabledState;
            if (current == State.DISABLED) {
                enabledState = State.WATCHING;
            } else if (current instanceof DisabledReference disabled) {
                enabledState = disabled.reference;
            } else {
                // INACTIVE is expected when automatic async actions are disabled.
                return;
            }
            if (state.compareAndSet(current, enabledState)) {
                stateChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops the owned watcher thread. The coordinator first publishes {@link State#STOPPING}, then
     * interrupts the thread so that it leaves {@link ReferenceQueue#remove()}, and joins it before
     * clearing the lifecycle state. If joining is interrupted, shutdown still completes and the
     * caller's interrupt status is restored afterward.
     */
    public void stop() {
        Thread thread;
        lock.lock();
        try {
            Object current = state.get();
            if (current == State.INACTIVE || current == State.STOPPED) {
                return;
            }
            state.set(State.STOPPING);
            stateChanged.signalAll();
            thread = watcherThread;
        } finally {
            lock.unlock();
        }
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            boolean interrupted = false;
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        stopped();
    }

    public void stopped() {
        lock.lock();
        try {
            state.set(State.STOPPED);
            watcherThread = null;
            watcherInRemove = false;
            pauseRequested = false;
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void signalStateChanged() {
        lock.lock();
        try {
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
