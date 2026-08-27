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
package com.oracle.graal.python.test.builtin.objects.cext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ReferenceQueueCoordinator;

public class ReferenceQueueCoordinatorTests {

    @Test
    public void watcherHandsOffExactlyOneReference() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();
        Reference<?> reference = new WeakReference<>(new Object());

        assertTrue(coordinator.beginWatcherRemove());
        Reference<?> handoff = coordinator.endWatcherRemove(reference);

        assertSame(reference, coordinator.beginWatcherDrain(handoff));
        coordinator.finishDrain();

        assertTrue(coordinator.beginWatcherRemove());
        assertNull(coordinator.endWatcherRemove(null));
        stop(coordinator);
    }

    @Test
    public void deferredWatcherDrainRetainsHandoff() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();
        Reference<?> reference = new WeakReference<>(new Object());

        assertTrue(coordinator.beginWatcherRemove());
        Reference<?> handoff = coordinator.endWatcherRemove(reference);

        // A caller that cannot drain yet must be able to leave the handoff pending and retry later.
        assertSame(reference, coordinator.getPendingReference());
        assertSame(reference, coordinator.beginWatcherDrain(handoff));
        coordinator.finishDrain();
        stop(coordinator);
    }

    @Test
    public void forcedDrainSupersedesQueuedHandoff() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();
        Reference<?> reference = new WeakReference<>(new Object());

        assertTrue(coordinator.beginWatcherRemove());
        Reference<?> handoff = coordinator.endWatcherRemove(reference);

        assertSame(reference, coordinator.beginForcedDrain(null));
        assertNull(coordinator.beginWatcherDrain(handoff));
        coordinator.finishDrain();
        stop(coordinator);
    }

    @Test
    public void forcedDrainWithoutHandoffIsStillAcquired() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();

        assertNotNull(coordinator.beginForcedDrain(null));
        coordinator.finishDrain();
        stop(coordinator);
    }

    @Test
    public void disabledCoordinatorPreservesHandoffUntilReenabled() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();
        Reference<?> reference = new WeakReference<>(new Object());

        assertTrue(coordinator.beginWatcherRemove());
        Reference<?> handoff = coordinator.endWatcherRemove(reference);

        assertTrue(coordinator.disable(null));
        assertNull(coordinator.beginWatcherDrain(handoff));
        assertNull(coordinator.beginForcedDrain(null));

        coordinator.enable();
        assertSame(reference, coordinator.beginWatcherDrain(handoff));
        coordinator.finishDrain();
        stop(coordinator);
    }

    @Test
    public void disabledCoordinatorResumesWatcherWhenReenabled() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();

        assertTrue(coordinator.disable(null));
        coordinator.enable();
        assertTrue(coordinator.beginWatcherRemove());
        assertNull(coordinator.endWatcherRemove(null));
        stop(coordinator);
    }

    @Test
    public void stopInvalidatesPendingHandoff() {
        ReferenceQueueCoordinator coordinator = startedCoordinator();
        Reference<?> reference = new WeakReference<>(new Object());

        assertTrue(coordinator.beginWatcherRemove());
        Reference<?> handoff = coordinator.endWatcherRemove(reference);
        coordinator.stop();

        assertFalse(coordinator.isActive());
        assertNull(coordinator.beginWatcherDrain(handoff));
    }

    @Test
    public void blockingRemoveIsInterruptibleForStop() throws InterruptedException {
        ReferenceQueueCoordinator coordinator = new ReferenceQueueCoordinator();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        CountDownLatch removing = new CountDownLatch(1);
        AtomicReference<Reference<?>> result = new AtomicReference<>();
        Thread watcher = new Thread(() -> {
            if (coordinator.beginWatcherRemove()) {
                removing.countDown();
                Reference<?> reference = coordinator.blockingRemove(queue);
                result.set(reference);
                coordinator.endWatcherRemove(reference);
            }
            coordinator.stopped();
        });
        coordinator.start(watcher);

        assertTrue(removing.await(1, TimeUnit.SECONDS));
        coordinator.stop();

        assertFalse(watcher.isAlive());
        assertNull(result.get());
        assertFalse(coordinator.isActive());
    }

    private static ReferenceQueueCoordinator startedCoordinator() {
        ReferenceQueueCoordinator coordinator = new ReferenceQueueCoordinator();
        coordinator.start(new Thread(() -> {
        }));
        assertTrue(coordinator.isActive());
        return coordinator;
    }

    private static void stop(ReferenceQueueCoordinator coordinator) {
        coordinator.stop();
        assertFalse(coordinator.isActive());
    }
}
