/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.referencetype;

import java.lang.ref.WeakReference;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class PReferenceType extends PythonBuiltinObject {
    private class WeakRefStorage extends WeakReference<PythonObject> {
        private final CallTarget callback;
        private final PReferenceType ref;
        private final PythonObject globals;

        public WeakRefStorage(PReferenceType ref, PythonObject referent, PFunction callback) {
            super(referent);
            if (callback != null) {
                this.callback = callback.getCallTarget();
                this.globals = callback.getGlobals();
            } else {
                this.callback = null;
                this.globals = null;
            }
            this.ref = ref;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if (callback != null) {
                // TODO: Exceptions raised by the callback will be noted on the
                // standard error output, but cannot be propagated; they are
                // handled in exactly the same way as exceptions raised TODO:
                // from an object’s __del__() method.
                // TODO: check: the referent must no longer be available at this
                // point
                Object[] arguments = PArguments.create(1);
                PArguments.setArgument(arguments, 0, this.ref);
                PArguments.setGlobals(arguments, globals);
                callback.call(arguments);
            }
        }
    }

    private final WeakRefStorage store;
    private int hash = -1;

    @TruffleBoundary
    public PReferenceType(LazyPythonClass cls, PythonObject pythonObject, PFunction callback) {
        super(cls);
        this.store = new WeakRefStorage(this, pythonObject, callback);
    }

    public Object getCallback() {
        if (this.store.callback == null) {
            return PNone.NONE;
        }
        return this.store.callback;
    }

    @TruffleBoundary
    public PythonObject getObject() {
        return this.store.get();
    }

    public PythonAbstractObject getPyObject() {
        PythonObject object = getObject();
        return (object == null) ? PNone.NONE : object;
    }

    public int getWeakRefCount() {
        return (this.getObject() == null) ? 0 : 1;
    }

    public int getHash() {
        if (this.hash != -1) {
            return this.hash;
        }

        PythonObject object = getObject();
        if (object != null) {
            this.hash = object.hashCode();
        }
        return this.hash;
    }
}
