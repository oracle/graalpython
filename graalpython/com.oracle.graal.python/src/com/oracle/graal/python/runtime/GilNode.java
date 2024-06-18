/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class GilNode extends Node {

    private static final class Cached extends GilNode {
        // The same profile is used for all methods. The profile condition should always be so that
        // we profile if a boundary call needs to be made at all.
        private final ConditionProfile binaryProfile = ConditionProfile.create();

        @Override
        public boolean isAdoptable() {
            return true;
        }

        @Override
        public void release(boolean wasAcquired) {
            release(PythonContext.get(this), wasAcquired);
        }

        @Override
        public boolean acquire(Node location) {
            return acquire(PythonContext.get(this), location);
        }

        @Override
        public void release(PythonContext context, boolean wasAcquired) {
            // n.b.: we cannot make any optimizations here based on the singleThreadedAssumption of
            // the language. You would think that you could use that assumption to get rid even of
            // the ownsGil check, but we need to actually release the GIL around blocking operations
            // like sleeping. Consider an embedded use where one Python thread goes to sleep, while
            // another thread enters the same Python context, invalidating the assumption. The
            // regular "release GIL" logic will start to tick, but the second thread won't be able
            // to acquire the GIL at all until the first one has finished sleeping and actually runs
            // into the next (e.g. regular) GIL release. So we need to always have this ownsGil
            // check.
            if (binaryProfile.profile(wasAcquired)) {
                context.releaseGil();
            }
        }

        @Override
        public boolean acquire(PythonContext context, Node location) {
            if (binaryProfile.profile(!context.ownsGil())) {
                try {
                    TruffleSafepoint.setBlockedThreadInterruptible(location, PythonContext::acquireGil, context);
                } catch (PythonThreadKillException | PythonExitException | ThreadDeath e) {
                    throw e;
                } catch (Throwable t) {
                    /*
                     * Safepoint actions may throw exceptions, so we need to make sure that we
                     * really acquire the GIL in the end before we hand the exception to python. And
                     * let's not allow safepoint actions anymore so the exception doesn't get
                     * swallowed.
                     */
                    context.ensureGilAfterFailure();
                    throw t;
                }
                return true;
            }
            return false;
        }

        @Override
        public final boolean tryRelease() {
            PythonContext context = PythonContext.get(this);
            if (binaryProfile.profile(context.ownsGil())) {
                context.releaseGil();
                return true;
            }
            return false;
        }
    }

    private abstract static class Uncached extends GilNode implements AutoCloseable {
        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        @TruffleBoundary
        public final boolean acquire(Node location) {
            return acquire(PythonContext.get(this), location);
        }

        @Override
        @TruffleBoundary
        public final void release(boolean wasAcquired) {
            release(PythonContext.get(this), wasAcquired);
        }

        @Override
        @TruffleBoundary
        public final boolean acquire(PythonContext context, Node location) {
            if (!context.ownsGil()) {
                if (!context.tryAcquireGil()) {
                    try {
                        TruffleSafepoint.setBlockedThreadInterruptible(location, PythonContext::acquireGil, context);
                    } catch (PythonThreadKillException | PythonExitException | ThreadDeath e) {
                        throw e;
                    } catch (Throwable t) {
                        /*
                         * Safepoint actions may throw exceptions, so we need to make sure that we
                         * really acquire the GIL in the end before we hand the exception to python.
                         * And let's not allow safepoint actions anymore so the exception doesn't
                         * get swallowed.
                         */
                        context.ensureGilAfterFailure();
                        throw t;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        @TruffleBoundary
        public final void release(PythonContext context, boolean wasAcquired) {
            if (wasAcquired) {
                context.releaseGil();
            }
        }

        @Override
        @TruffleBoundary
        public final boolean tryRelease() {
            PythonContext context = PythonContext.get(this);
            if (context.ownsGil()) {
                context.releaseGil();
                return true;
            }
            return false;
        }

        public abstract void close();
    }

    public static final class UncachedRelease extends Uncached {
        private UncachedRelease() {
        }

        private static final UncachedRelease INSTANCE = new UncachedRelease();

        @Override
        public final void close() {
            acquire();
        }
    }

    public static final class UncachedAcquire extends Uncached {
        private UncachedAcquire() {
        }

        private static final UncachedAcquire INSTANCE_WITH_RELEASE = new UncachedAcquire();
        private static final UncachedAcquire INSTANCE_WITHOUT_RELEASE = new UncachedAcquire();

        @Override
        public final void close() {
            if (PythonContext.get(this).ownsGil()) {
                // we are forgiving in this usage for cases where the gil is released and we're
                // exiting with an exception
                release(this == INSTANCE_WITH_RELEASE);
            }
        }
    }

    @TruffleBoundary
    private final void yieldGil() {
        release(true);
        Thread.yield();
        acquire();
    }

    /**
     * @see #acquire(Node)
     */
    public final boolean acquire() {
        return acquire(this);
    }

    /**
     * @see #acquire(Node)
     */
    public final boolean acquire(PythonContext context) {
        return acquire(context, this);
    }

    /**
     * Acquires the GIL if it isn't already held. Returns {@code true} if the GIL had to be
     * acquired. Pass the return value into the {@link #release(boolean wasAcquired)} method in
     * order to ensure releasing the GIL only if it has been acquired.
     *
     * The {@code location} parameter is used to support the safepoint mechanism.
     */
    public abstract boolean acquire(Node location);

    /**
     * @see #acquire(Node)
     */
    public abstract boolean acquire(PythonContext context, Node location);

    /**
     * Release the GIL if {@code wasAcquired} is {@code true}.
     *
     * @param wasAcquired - the return value of the preceding {@link #acquire} call.
     */
    public abstract void release(boolean wasAcquired);

    /**
     * @see #release(boolean)
     */
    public abstract void release(PythonContext context, boolean wasAcquired);

    /**
     * Release the GIL if it is currently owned by this Thread and preemption is allowed. Preemption
     * may be disabled while running C extension code that does not expect to be preempted or for
     * certain built-in operations of the implementation.
     *
     * @return {@code true} if GIL was released, {@code false} if it wasn't locked by this Thread
     */
    public abstract boolean tryRelease();

    @NeverDefault
    public static GilNode create() {
        return new Cached();
    }

    public static UncachedRelease uncachedRelease() {
        assert PythonContext.get(UncachedRelease.INSTANCE).ownsGil();
        UncachedRelease.INSTANCE.release(true);
        return UncachedRelease.INSTANCE;
    }

    public static UncachedAcquire uncachedAcquire() {
        // if we already had the GIL, we don't acquire it again
        boolean wasAcquired = UncachedAcquire.INSTANCE_WITH_RELEASE.acquire();
        if (wasAcquired) {
            // the close method of this instance will release it again
            return UncachedAcquire.INSTANCE_WITH_RELEASE;
        } else {
            return UncachedAcquire.INSTANCE_WITHOUT_RELEASE;
        }
    }

    public static GilNode getUncached() {
        // it doesn't matter which is used for the default uncached case
        return UncachedRelease.INSTANCE;
    }
}
