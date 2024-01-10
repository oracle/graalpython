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
package com.oracle.graal.python.builtins.modules.multiprocessing;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PGraalPySemLock extends AbstractPythonLock {
    public static final int RECURSIVE_MUTEX = 0;
    public static final int SEMAPHORE = 1;

    private final Semaphore semaphore;
    private final int kind;
    private final TruffleString name;

    private long lastThreadID = -1;
    private int count;

    public PGraalPySemLock(Object cls, Shape instanceShape, TruffleString name, int kind, Semaphore sharedSemaphore) {
        super(cls, instanceShape);
        this.name = name;
        this.semaphore = sharedSemaphore;
        this.kind = kind;
    }

    @Override
    @TruffleBoundary
    protected boolean acquireNonBlocking() {
        boolean ret = semaphore.tryAcquire();
        if (ret) {
            lastThreadID = PThread.getThreadId(Thread.currentThread());
            count++;
        }
        return ret;
    }

    @Override
    @TruffleBoundary
    protected boolean acquireBlocking(Node node) {
        boolean[] b = new boolean[1];
        TruffleSafepoint.setBlockedThreadInterruptible(node, (s) -> {
            s.acquire();
            b[0] = true;
        }, semaphore);
        if (b[0]) {
            lastThreadID = PThread.getThreadId(Thread.currentThread());
            count++;
        }
        return b[0];
    }

    @Override
    @TruffleBoundary
    protected boolean acquireTimeout(Node node, long timeout) {
        boolean[] b = new boolean[1];
        TruffleSafepoint.setBlockedThreadInterruptible(node, (s) -> b[0] = s.tryAcquire(timeout, TimeUnit.MILLISECONDS), semaphore);
        if (b[0]) {
            lastThreadID = PThread.getThreadId(Thread.currentThread());
            count++;
        }
        return b[0];
    }

    @Override
    @TruffleBoundary
    public void release() {
        semaphore.release();
        count--;
    }

    @Override
    @TruffleBoundary
    public boolean locked() {
        return semaphore.availablePermits() == 0;
    }

    @TruffleBoundary
    public int getValue() {
        return semaphore.availablePermits();
    }

    public int getCount() {
        return count;
    }

    public void increaseCount() {
        count++;
        lastThreadID = PThread.getThreadId(Thread.currentThread());
    }

    public void decreaseCount() {
        count--;
    }

    @TruffleBoundary
    public boolean isMine() {
        return count > 0 && lastThreadID == PThread.getThreadId(Thread.currentThread());
    }

    public boolean isZero() {
        return semaphore.availablePermits() == 0;
    }

    public int getKind() {
        return kind;
    }

    public TruffleString getName() {
        return name;
    }
}
