/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.arrow;

import com.oracle.graal.python.nodes.arrow.capsule.ArrowArrayCapsuleDestructor;
import com.oracle.graal.python.nodes.arrow.capsule.ArrowSchemaCapsuleDestructor;
import com.oracle.graal.python.nodes.arrow.release_callback.ArrowSchemaReleaseCallback;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public class ArrowSupport {

    protected final PythonContext ctx;

    public ArrowSupport(PythonContext ctx) {
        this.ctx = ctx;
    }

    // ArrowArray destructor
    private Object arrowArrayDestructorNFIClosure;
    @CompilationFinal private long arrowArrayDestructor;

    // ArrowSchema destructor
    private Object arrowSchemaDestructorNFIClosure;
    @CompilationFinal private long arrowSchemaDestructorCallback;

    // ArrowSchema release callback
    private Object arrowSchemaNFIClosure;
    @CompilationFinal private long arrowSchemaReleaseCallback;

    public long getArrowSchemaDestructor() {
        if (arrowSchemaDestructorCallback == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initArrowSchemaDestructor();
        }
        return arrowSchemaDestructorCallback;
    }

    public long getArrowArrayDestructor() {
        if (arrowArrayDestructor == 0L) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initArrowArrayDestructor();
        }
        return arrowArrayDestructor;
    }

    public long getArrowSchemaReleaseCallback() {
        if (arrowSchemaReleaseCallback == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initArrowSchemaReleaseCallback();
        }
        return arrowSchemaReleaseCallback;
    }

    @TruffleBoundary
    private void initArrowArrayDestructor() {
        CompilerAsserts.neverPartOfCompilation();
        var signature = ArrowUtil.createNfiSignature("(POINTER):VOID", ctx);
        var executable = new ArrowArrayCapsuleDestructor();
        this.arrowArrayDestructorNFIClosure = SignatureLibrary.getUncached().createClosure(signature, executable);
        this.arrowArrayDestructor = PythonUtils.coerceToLong(arrowArrayDestructorNFIClosure, InteropLibrary.getUncached());
    }

    @TruffleBoundary
    private void initArrowSchemaDestructor() {
        CompilerAsserts.neverPartOfCompilation();
        var signature = ArrowUtil.createNfiSignature("(POINTER):VOID", ctx);
        var executable = new ArrowSchemaCapsuleDestructor();
        this.arrowSchemaDestructorNFIClosure = SignatureLibrary.getUncached().createClosure(signature, executable);
        this.arrowSchemaDestructorCallback = PythonUtils.coerceToLong(arrowSchemaDestructorNFIClosure, InteropLibrary.getUncached());
    }

    @TruffleBoundary
    private void initArrowSchemaReleaseCallback() {
        CompilerAsserts.neverPartOfCompilation();
        var signature = ArrowUtil.createNfiSignature("(UINT64):VOID", ctx);
        var executable = new ArrowSchemaReleaseCallback();
        this.arrowSchemaNFIClosure = SignatureLibrary.getUncached().createClosure(signature, executable);
        this.arrowSchemaReleaseCallback = PythonUtils.coerceToLong(arrowSchemaNFIClosure, InteropLibrary.getUncached());
    }
}
