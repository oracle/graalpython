/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.posix;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixFileHandle;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.AsyncHandler.AsyncAction;
import com.oracle.graal.python.runtime.AsyncHandler.SharedFinalizer;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.object.Shape;

public final class PScandirIterator extends PythonBuiltinObject {

    final PosixFileHandle path;
    final DirStreamRef ref;

    public PScandirIterator(Object cls, Shape instanceShape, PythonContext context, Object dirStream, PosixFileHandle path, boolean needsRewind) {
        super(cls, instanceShape);
        this.ref = new DirStreamRef(this, dirStream, context.getSharedFinalizer(), needsRewind);
        this.path = path;
    }

    static class DirStreamRef extends SharedFinalizer.FinalizableReference {

        final boolean needsRewind;

        DirStreamRef(PScandirIterator referent, Object dirStream, SharedFinalizer finalizer, boolean needsRewind) {
            super(referent, dirStream, finalizer);
            this.needsRewind = needsRewind;
        }

        @Override
        public AsyncAction release() {
            return new ScandirIteratorBuiltins.ReleaseCallback(this);
        }

        void rewindAndClose(PosixSupportLibrary posixLib, Object posixSupport) {
            if (isReleased()) {
                return;
            }
            markReleased();
            Object dirStream = getReference();
            if (needsRewind) {
                posixLib.rewinddir(posixSupport, dirStream);
            }
            try {
                posixLib.closedir(posixSupport, dirStream);
            } catch (PosixException e) {
                // ignored (CPython does not chek the return value of closedir)
            }
        }
    }
}
