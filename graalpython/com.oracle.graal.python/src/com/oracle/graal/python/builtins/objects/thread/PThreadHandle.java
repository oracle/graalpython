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
package com.oracle.graal.python.builtins.objects.thread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class PThreadHandle extends PythonBuiltinObject {
    private static final int NOT_STARTED = 1;
    private static final int STARTING = 2;
    private static final int RUNNING = 3;
    private static final int DONE = 4;

    private int state = NOT_STARTED;
    private long ident;
    private Thread thread;
    private boolean exiting;
    // Used to signal joining for ident-only threads (like the main thread)
    private final CountDownLatch done = new CountDownLatch(1);

    public PThreadHandle(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public synchronized boolean markStarting() {
        if (state != NOT_STARTED) {
            return false;
        }
        state = STARTING;
        return true;
    }

    public synchronized void setRunning(Thread thread) {
        assert state == STARTING;
        this.thread = thread;
        this.ident = thread.threadId();
        this.state = RUNNING;
    }

    public synchronized void setRunning(long ident) {
        assert state == NOT_STARTED;
        this.ident = ident;
        this.state = RUNNING;
    }

    public synchronized void notifyThreadExiting() {
        exiting = true;
    }

    @TruffleBoundary
    public synchronized void setDone() {
        assert state >= RUNNING : "thread not started";
        exiting = true;
        state = DONE;
        done.countDown();
    }

    public synchronized boolean isStarted() {
        return state >= RUNNING;
    }

    public synchronized boolean isDone() {
        if (exiting) {
            state = DONE;
        }
        return state == DONE;
    }

    public synchronized long getIdent() {
        return ident;
    }

    @TruffleBoundary
    public void join(Node node, long timeoutMillis) {
        assert state >= RUNNING : "thread not started";
        assert exiting || ident != Thread.currentThread().threadId() : "Cannot join current thread";

        Thread threadToJoin = thread;
        if (threadToJoin != null) {
            if (timeoutMillis < 0) {
                TruffleSafepoint.setBlockedThreadInterruptible(node, Thread::join, threadToJoin);
            } else {
                TruffleSafepoint.setBlockedThreadInterruptible(node, (t) -> t.join(timeoutMillis), threadToJoin);
            }
            if (!threadToJoin.isAlive()) {
                setDone();
            }
        } else {
            if (timeoutMillis < 0) {
                TruffleSafepoint.setBlockedThreadInterruptible(node, CountDownLatch::await, done);
            } else {
                TruffleSafepoint.setBlockedThreadInterruptible(node, (latch) -> latch.await(timeoutMillis, TimeUnit.MILLISECONDS), done);
            }
        }
    }
}
