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
package com.oracle.graal.python.builtins.objects.referencetype;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public class PReferenceType extends PythonBuiltinObject {
    public static class WeakRefStorage extends WeakReference<Object> {
        private final Object callback;
        private final PReferenceType ref;

        public WeakRefStorage(PReferenceType ref, Object referent, Object callback, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.callback = callback;
            this.ref = ref;
        }

        public Object getCallback() {
            return callback;
        }

        public PReferenceType getRef() {
            return ref;
        }
    }

    private final WeakRefStorage store;
    private long hash = -1;

    @TruffleBoundary
    public PReferenceType(Object cls, Shape instanceShape, Object pythonObject, Object callback, ReferenceQueue<Object> queue) {
        super(cls, instanceShape);
        this.store = new WeakRefStorage(this, pythonObject, callback, queue);
    }

    public Object getCallback() {
        if (this.store.callback == null) {
            return PNone.NONE;
        }
        return this.store.callback;
    }

    @TruffleBoundary
    public Object getObject() {
        return this.store.get();
    }

    public Object getPyObject() {
        Object object = getObject();
        return (object == null) ? PNone.NONE : object;
    }

    public int getWeakRefCount() {
        return (this.getObject() == null) ? 0 : 1;
    }

    public long getHash() {
        return hash;
    }

    public void setHash(long hash) {
        this.hash = hash;
    }
}
