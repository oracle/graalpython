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
package com.oracle.graal.python.builtins.modules.ctypes;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

public final class CThunkObject extends PythonBuiltinObject {

    Object pcl_write; // ffi_closure /* the C callable, writeable */
    Object pcl_exec; /* the C callable, executable */
    Object cif; // ffi_cif
    int flags;
    Object converters;
    Object callable;
    Object restype;
    FieldSet setfunc;
    FFIType ffi_restype;
    FFIType[] atypes;

    public CThunkObject(Object cls, Shape instanceShape, int nArgs) {
        super(cls, instanceShape);
        atypes = new FFIType[nArgs + 1];
    }

    public void createCallback() {
        this.pcl_exec = new CtypeCallback(this);
        this.pcl_write = this.pcl_exec;
    }

    @ExportLibrary(InteropLibrary.class)
    protected static class CtypeCallback implements TruffleObject {
        private CThunkObject thunk;

        public CtypeCallback(CThunkObject thunk) {
            this.thunk = thunk;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @TruffleBoundary
        @ExportMessage
        Object execute(Object... args) throws ArityException {
            // we don't need to check arity as it will be checked by the callable
            return CallNode.getUncached().execute(thunk.callable, args, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
