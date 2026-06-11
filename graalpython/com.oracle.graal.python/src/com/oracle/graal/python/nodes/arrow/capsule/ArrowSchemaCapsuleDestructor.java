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
package com.oracle.graal.python.nodes.arrow.capsule;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.graal.python.annotations.CApiUpcallTarget;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins.PyCapsuleGetPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.nodes.arrow.ArrowReleaseCallback;
import com.oracle.graal.python.nodes.arrow.ArrowSchema;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.truffle.api.CompilerAsserts;

public final class ArrowSchemaCapsuleDestructor {
    private static final MethodHandle HANDLE_EXECUTE;

    static {
        try {
            HANDLE_EXECUTE = MethodHandles.lookup().findStatic(ArrowSchemaCapsuleDestructor.class, "execute",
                            MethodType.methodType(void.class, long.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private ArrowSchemaCapsuleDestructor() {
    }

    public static MethodHandle getMethodHandle() {
        return HANDLE_EXECUTE;
    }

    @CApiUpcallTarget
    private static void execute(long capsulePointer) {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext ctx = PythonContext.get(null);
        ctx.ensureNativeAccess();
        Object capsule = NativeToPythonInternalNode.executeUncached(capsulePointer, false);
        long capsuleNamePointer = ctx.stringToNativeUtf8Bytes(ArrowSchema.CAPSULE_NAME, false);
        try {
            var arrowSchema = ArrowSchema.wrap(PyCapsuleGetPointerNode.executeUncached(capsule, capsuleNamePointer));

            if (!arrowSchema.isReleased()) {
                ArrowReleaseCallback.execute(arrowSchema.releaseCallback(), arrowSchema.memoryAddress());
            }

            NativeMemory.free(arrowSchema.memoryAddress());
        } finally {
            NativeMemory.free(capsuleNamePointer);
        }
    }
}
