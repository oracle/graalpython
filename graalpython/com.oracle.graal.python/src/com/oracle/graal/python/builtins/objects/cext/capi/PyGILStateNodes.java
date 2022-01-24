/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PExecuteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public abstract class PyGILStateNodes {
    /**
     * A simple executable interop object that acquires the GIL if not acquired already. This cannot
     * be implemented as a Python built-in function in {@code PythonCextBuiltins} because if the
     * built-in function would be called via interop (this would go through
     * {@link com.oracle.graal.python.builtins.objects.PythonAbstractObject#execute(Object[], PExecuteNode, GilNode)}
     * ), it tries to acquire/release the GIL for execution. This would again release the GIL when
     * returning.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class PyGILStateEnsure implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        int execute(@SuppressWarnings("unused") Object[] arguments,
                        @Cached GilNode gil) {
            // TODO allow acquiring from foreign thread
            boolean acquired = gil.acquire();
            return acquired ? 1 : 0;
        }
    }

    /**
     * A simple executable interop object that releases GIL only if acquired in the corresponding
     * {@code PyGILState_Ensure} call. This cannot be implemented as a Python built-in function in
     * {@code PythonCextBuiltins} because if the built-in function would be called via interop (this
     * would go through
     * {@link com.oracle.graal.python.builtins.objects.PythonAbstractObject#execute(Object[], PExecuteNode, GilNode)}
     * ), it tries to acquire/release the GIL for execution. This would again release the GIL when
     * returning.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class PyGILStateRelease implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastToJavaIntLossyNode cast,
                        @Cached GilNode gil) {
            gil.release(cast.execute(arguments[0]) == 1);
            return 0;
        }
    }
}
