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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonBufferAcquireLibrary.class)
public final class PPickleBuffer extends PythonBuiltinObject {
    // Buffer object implementing PythonBufferAccessLibrary
    private Object view;

    public PPickleBuffer(Object cls, Shape instanceShape, Object view) {
        super(cls, instanceShape);
        PythonBufferAccessLibrary.assertIsBuffer(view);
        this.view = view;
    }

    public Object getView() {
        return view;
    }

    public void release() {
        this.view = null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    Object acquire(int flags,
                    @Bind Node inliningTarget,
                    @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                    @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                    @Cached PRaiseNode raise) {
        Object owner = null;
        if (view != null) {
            owner = bufferLib.getOwner(view);
        }
        if (owner == null) {
            throw raise.raise(inliningTarget, ValueError, ErrorMessages.OP_FORBIDDEN_ON_OBJECT, "PickleBuffer");
        }
        return acquireLib.acquire(owner, flags);
    }
}
