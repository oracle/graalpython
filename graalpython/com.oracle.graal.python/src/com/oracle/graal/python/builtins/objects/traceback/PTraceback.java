/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class PTraceback extends PythonBuiltinObject {

    /** A marker object to indicate the end of a traceback chain. */
    public static final PTraceback NO_TRACEBACK = new PTraceback(null, (PException) null);

    public static final String TB_FRAME = "tb_frame";
    public static final String TB_NEXT = "tb_next";
    public static final String TB_LASTI = "tb_lasti";
    public static final String TB_LINENO = "tb_lineno";

    @CompilationFinal(dimensions = 1) private static final Object[] TB_DIR_FIELDS = new Object[]{TB_FRAME, TB_NEXT, TB_LASTI, TB_LINENO};

    static Object[] getTbFieldNames() {
        return TB_DIR_FIELDS.clone();
    }

    // we have to keep the exception around to lazily create the tb_next element
    // if that isn't available and still stored in the TruffleStackTrace
    private final PException exception;

    private final PFrame frame;
    private final int lasti;
    private PTraceback next;

    public PTraceback(PFrame frame, PException exception) {
        super(PythonBuiltinClassType.PTraceback, PythonBuiltinClassType.PTraceback.newInstance());
        this.frame = frame;
        this.exception = exception;
        this.lasti = 0;
    }

    public PTraceback(PFrame frame, PTraceback next) {
        super(PythonBuiltinClassType.PTraceback, PythonBuiltinClassType.PTraceback.newInstance());
        this.frame = frame;
        this.exception = next.exception;
        this.next = next;
        this.lasti = 0;
    }

    public PFrame getPFrame() {
        return frame;
    }

    public int getLasti() {
        return lasti;
    }

    public int getLineno() {
        return frame.getLine();
    }

    public PTraceback getNext() {
        return next;
    }

    public void setNext(PTraceback next) {
        this.next = next;
    }

    public PException getException() {
        return exception;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (this == NO_TRACEBACK) {
            return "NO_TRACEBACK";
        }
        return super.toString();
    }

}
