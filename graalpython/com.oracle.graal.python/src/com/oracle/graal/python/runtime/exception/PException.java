/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class PException extends RuntimeException implements TruffleException {
    private static final long serialVersionUID = -6437116280384996361L;

    /** A marker object indicating that there is for sure no exception. */
    public static final PException NO_EXCEPTION = PException.empty();

    private Node location;
    private String message = null;
    private boolean isIncompleteSource;
    private boolean exit;
    private final PBaseException pythonException;

    private PException(PBaseException actual, Node node) {
        this.pythonException = actual;
        this.location = node;
    }

    public static PException empty() {
        return new PExceptionWithoutStack(null, null);
    }

    public static PException fromObject(PBaseException actual, Node node) {
        PException pException = new PExceptionWithStack(actual, node);
        actual.setException(pException);
        return pException;
    }

    public static PException fromObjectWithoutStack(PBaseException actual, Node node) {
        PException pException = new PExceptionWithoutStack(actual, node);
        actual.setException(pException);
        return pException;
    }

    @Override
    public String getMessage() {
        if (message == null) {
            message = pythonException.toString();
        }
        return message;
    }

    public void setMessage(Object object) {
        message = object.toString();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (this == PException.NO_EXCEPTION) {
            return "NO_EXCEPTION";
        }
        return getMessage();
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }

    @Override
    public Node getLocation() {
        return location;
    }

    @Override
    public PBaseException getExceptionObject() {
        return pythonException;
    }

    @Override
    public boolean isInternalError() {
        return false;
    }

    @Override
    public int getStackTraceElementLimit() {
        return 20;
    }

    @Override
    public boolean isSyntaxError() {
        return IsBuiltinClassProfile.profileClassSlowPath(getExceptionObject().getLazyPythonClass(), PythonBuiltinClassType.SyntaxError);
    }

    public void setIncompleteSource(boolean val) {
        isIncompleteSource = val;
    }

    @Override
    public boolean isIncompleteSource() {
        return isSyntaxError() && isIncompleteSource;
    }

    public void setExit(boolean val) {
        exit = val;
    }

    @Override
    public boolean isExit() {
        return exit;
    }

    public void expectIndexError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.IndexError)) {
            throw this;
        }
    }

    public void expectStopIteration(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.StopIteration)) {
            throw this;
        }
    }

    public void expectAttributeError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.AttributeError)) {
            throw this;
        }
    }

    public void expect(PythonBuiltinClassType error, IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, error)) {
            throw this;
        }
    }

    private static final class PExceptionWithStack extends PException {
        private static final long serialVersionUID = 1;

        public PExceptionWithStack(PBaseException actual, Node node) {
            super(actual, node);
        }
    }

    private static final class PExceptionWithoutStack extends PException  {
        private static final long serialVersionUID = 1;

        public PExceptionWithoutStack(PBaseException actual, Node node) {
            super(actual, node);
        }

        @Override
        public int getStackTraceElementLimit() {
            return 0;
        }

        /**
         * For performance reasons, this exception does not record any stack trace information.
         */
        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }
}
