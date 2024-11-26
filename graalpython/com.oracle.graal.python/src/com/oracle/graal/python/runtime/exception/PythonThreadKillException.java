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
package com.oracle.graal.python.runtime.exception;

import com.oracle.truffle.api.TruffleSafepoint.Interruptible;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.polyglot.PolyglotException;

/**
 * This exception kills a Python thread.
 * <p>
 * All the polyglot threads started from Python using
 * {@link com.oracle.truffle.api.TruffleLanguage.Env#newTruffleThreadBuilder(Runnable)} must poll
 * Truffle Safepoint. When a thread should be killed, we submit a Thread Local Action that throws
 * {@link PythonThreadKillException}. All such threads must be able to also handle this exception.
 * Threads running Python code handle this exception and forward it to the top level Python entry
 * point. Threads not running Python code should catch this exception and handle it accordingly.
 * <p>
 * Note that
 * {@link com.oracle.truffle.api.TruffleSafepoint#setBlockedThreadInterruptible(Node, Interruptible, Object)}
 * or similar APIs implicitly call Truffle Safepoint poll.
 * <p>
 * {@link PythonThreadKillException} does intentionally not extend from
 * {@link PythonControlFlowException} because if this exception is thrown, we <b>MUST NOT</b> run
 * any finally blocks. This is because we did probably fail to acquire the GIL during context
 * shutdown, and thus we do not own the GIL when this exception is flying.
 */
public final class PythonThreadKillException extends RuntimeException {

    private static final long serialVersionUID = 5323687983726237118L;
    public static final PythonThreadKillException INSTANCE = new PythonThreadKillException();

    /**
     * Creates an exception thrown to model control flow.
     *
     * @since 0.8 or earlier
     */
    public PythonThreadKillException() {
        /*
         * We use the super constructor that initializes the cause to null. Without that, the cause
         * would be this exception itself. This helps escape analysis: it avoids the circle of an
         * object pointing to itself. We also do not need a message, so we use the constructor that
         * also allows us to set the message to null.
         */
        super(null, null);
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     *
     * @since 0.8 or earlier
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Helper method for threads that do not run Python code and should gracefully terminate on
     * catching {@link PythonThreadKillException} and some types of Polyglot exception thrown from
     * Thread Location Actions run in Truffle Sefepoint.
     */
    public static boolean shouldKillThread(Throwable ex) {
        if (ex instanceof PythonThreadKillException) {
            return true;
        } else if (ex instanceof PolyglotException pex) {
            return pex.isCancelled() || pex.isInterrupted();
        }
        return false;
    }
}
