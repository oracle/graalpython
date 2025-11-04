/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PBaseExceptionGroup extends PBaseException {
    private final TruffleString message;
    private final Object[] exceptions;
    private PException parent;

    /**
     * Flag to denote if the `exceptions` field contains only reraised and/or unmatched exceptions.
     */
    private boolean containsReraises;

    /**
     * This flag marks the outer/final exception group that encompasses all other exception groups
     * that are thrown at the end of the try-except* block. This is needed, since we can nest other
     * exception groups into exception groups themselves.
     */
    private boolean isOuter;

    /**
     * Context setting behaves somewhat differently when handling exception groups. This flag
     * assures, that context, after being once set, won't be set again/overwritten.
     */
    private boolean contextWasExplicitlySet;

    public PBaseExceptionGroup(Object cls, Shape instanceShape, TruffleString message, Object[] exceptions, PTuple args) {
        super(cls, instanceShape, null, args);
        this.message = message;
        this.exceptions = exceptions;
    }

    public Object[] getExceptions() {
        return exceptions;
    }

    public TruffleString getMessage() {
        return message;
    }

    public void setParent(PException parent) {
        this.parent = parent;
    }

    public PException getParent() {
        return this.parent;
    }

    /**
     * See {@link PBaseExceptionGroup#containsReraises}
     */
    public void setContainsReraises(boolean value) {
        this.containsReraises = value;
    }

    /**
     * See {@link PBaseExceptionGroup#containsReraises}
     */
    public boolean getContainsReraises() {
        return this.containsReraises;
    }

    /**
     * See {@link PBaseExceptionGroup#isOuter}
     */
    public void setIsOuter(boolean value) {
        this.isOuter = value;
    }

    /**
     * See {@link PBaseExceptionGroup#isOuter}
     */
    public boolean getIsOuter() {
        return this.isOuter;
    }

    /**
     * See {@link PBaseExceptionGroup#contextWasExplicitlySet}
     */
    public void setContextExplicitly(Object context) {
        contextWasExplicitlySet = true;
        super.setContext(context);
    }

    /**
     * See also {@link PBaseExceptionGroup#setContextExplicitly} and
     * {@link PBaseExceptionGroup#contextWasExplicitlySet}
     */
    public void setContext(Object context) {
        if (contextWasExplicitlySet) {
            return;
        }
        super.setContext(context);
    }
}
