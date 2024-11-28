/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import java.util.logging.Level;

import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.truffle.api.TruffleLogger;

/**
 * Base class for runnables of "system" threads that handle some internal aspects of the Python
 * runtime. The thread should be created using
 * {@link com.oracle.graal.python.runtime.PythonContext#createSystemThread(PythonSystemThreadTask)}.
 * <p/>
 * Those threads are be regular polyglot threads, so they can execute Python code, but must take GIL
 * before doing so. They also <emph>must poll Truffle safepoints</emph>. The way such thread is
 * shutdown is through sending Java interrupt and submitting a Thread Local Action (TLA) that throws
 * {@link PythonThreadKillException}. The Java interrupt should interrupt any blocking operation,
 * which should then poll a safepoint. This wrapper takes care of handling
 * {@link PythonThreadKillException} gracefully. Note that Truffle may also shut down this thread
 * via TLA that throws its own internal cancellation exception, which is handled in Truffle internal
 * code that wraps this Runnable.
 */
public abstract class PythonSystemThreadTask implements Runnable {
    private final String name;
    private final TruffleLogger logger;

    public PythonSystemThreadTask(String name, TruffleLogger logger) {
        this.name = name;
        this.logger = logger;
    }

    public String getName() {
        return name;
    }

    @Override
    public final void run() {
        try {
            doRun();
            logger.fine(() -> String.format("'%s' finished", name));
        } catch (PythonThreadKillException ex) {
            logger.fine(() -> String.format("%s killed with exception '%s'", name, PythonThreadKillException.class.getSimpleName()));
        } catch (Throwable ex) {
            // Note: Truffle wraps this Runnable in its own Runnable that handles Truffle's own
            // internal cancellation exception that is submitted via TLA
            logger.log(Level.FINE, String.format("Unhandled exception %s in thread '%s'", ex.getClass().getSimpleName(), name), ex);
            throw ex;
        }
    }

    protected abstract void doRun();
}
