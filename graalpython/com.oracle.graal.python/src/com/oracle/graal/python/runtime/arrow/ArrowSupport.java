/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.annotations.NativeSimpleType.POINTER;
import static com.oracle.graal.python.annotations.NativeSimpleType.VOID;

import java.lang.invoke.MethodHandle;

import com.oracle.graal.python.nodes.arrow.capsule.ArrowArrayCapsuleDestructor;
import com.oracle.graal.python.nodes.arrow.capsule.ArrowSchemaCapsuleDestructor;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.nativeaccess.NativeSignature;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class ArrowSupport {
    private static final NativeSignature ARROW_CAPSULE_DESTRUCTOR_SIGNATURE = NativeSignature.create(VOID, POINTER);

    protected final PythonContext ctx;

    public ArrowSupport(PythonContext ctx) {
        this.ctx = ctx;
    }

    // ArrowArray destructor
    @CompilationFinal private long arrowArrayDestructor;

    // ArrowSchema destructor
    @CompilationFinal private long arrowSchemaDestructor;

    public long getArrowSchemaDestructor() {
        if (arrowSchemaDestructor == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initArrowSchemaDestructor();
        }
        return arrowSchemaDestructor;
    }

    public long getArrowArrayDestructor() {
        if (arrowArrayDestructor == 0L) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initArrowArrayDestructor();
        }
        return arrowArrayDestructor;
    }

    @TruffleBoundary
    private void initArrowArrayDestructor() {
        CompilerAsserts.neverPartOfCompilation();
        this.arrowArrayDestructor = createDestructorClosure("arrow_array_capsule_destructor",
                        ArrowArrayCapsuleDestructor.getMethodHandle());
    }

    @TruffleBoundary
    private void initArrowSchemaDestructor() {
        CompilerAsserts.neverPartOfCompilation();
        this.arrowSchemaDestructor = createDestructorClosure("arrow_schema_capsule_destructor",
                        ArrowSchemaCapsuleDestructor.getMethodHandle());
    }

    private long createDestructorClosure(String name, MethodHandle methodHandle) {
        return ARROW_CAPSULE_DESTRUCTOR_SIGNATURE.createClosure(ctx.ensureNativeContext(), name, methodHandle);
    }
}
