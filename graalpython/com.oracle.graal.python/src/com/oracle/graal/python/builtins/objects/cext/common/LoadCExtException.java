/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class LoadCExtException extends Exception {
    private static final long serialVersionUID = 3517291912314595890L;
    protected final transient TruffleString formatString;
    protected final transient Object[] formatArgs;

    protected LoadCExtException(TruffleString formatString, Object[] formatArgs) {
        /*
         * We use the super constructor that initializes the cause to null. Without that, the cause
         * would be this exception itself. This helps escape analysis: it avoids the circle of an
         * object pointing to itself. We also do not need a message, so we use the constructor that
         * also allows us to set the message to null.
         */
        super(null, null);
        this.formatString = formatString;
        this.formatArgs = formatArgs;
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static final class ApiInitException extends LoadCExtException {
        private static final long serialVersionUID = 982734876234786L;
        private final Exception cause;

        public ApiInitException(Exception cause) {
            super(null, null);
            this.cause = cause;
        }

        public ApiInitException(TruffleString formatString, Object... formatArgs) {
            super(formatString, formatArgs);
            this.cause = null;
        }

        @TruffleBoundary
        public PException reraise() {
            if (cause instanceof PException pcause) {
                throw pcause.getExceptionForReraise(false);
            } else if (cause != null) {
                throw PConstructAndRaiseNode.getUncached().executeWithFmtMessageAndArgs(null, SystemError, ErrorMessages.M, new Object[]{cause}, null);
            }
            throw PConstructAndRaiseNode.getUncached().executeWithFmtMessageAndArgs(null, SystemError, formatString, formatArgs, null);
        }
    }

    public static final class ImportException extends LoadCExtException {
        private static final long serialVersionUID = 7862376523476548L;
        private final PException cause;
        private final transient TruffleString name;
        private final transient Object path;

        public ImportException(PException cause, TruffleString name, TruffleString path, TruffleString formatString, Object... formatArgs) {
            super(formatString, formatArgs);
            this.cause = cause;
            this.name = name;
            this.path = path;
        }

        public PException reraise() {
            if (cause != null) {
                throw PConstructAndRaiseNode.getUncached().raiseImportErrorWithModuleAndCause(null, cause.getEscapedException(), name, path, formatString, formatArgs);
            }
            throw PConstructAndRaiseNode.getUncached().raiseImportErrorWithModule(null, name, path, formatString, formatArgs);
        }
    }
}
