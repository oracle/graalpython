/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

abstract class BufferReference extends AsyncHandler.SharedFinalizer.FinalizableReference {

    public BufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, AsyncHandler.SharedFinalizer sharedFinalizer) {
        super(referent, bufferLifecycleManager, sharedFinalizer);
        assert bufferLifecycleManager != null;
        bufferLifecycleManager.incrementExports();
    }

    public BufferLifecycleManager getLifecycleManager() {
        return (BufferLifecycleManager) getReference();
    }

    protected abstract AsyncHandler.AsyncAction callback();

    @Override
    public AsyncHandler.AsyncAction release() {
        BufferLifecycleManager buffer = getLifecycleManager();
        if (buffer.decrementExports() == 0) {
            return callback();
        }
        return null;
    }

    @TruffleBoundary
    public static BufferReference createNativeBufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, PythonContext context) {
        return new NativeBufferReference(referent, bufferLifecycleManager, context.getSharedFinalizer());
    }

    @TruffleBoundary
    public static BufferReference createSimpleBufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, PythonContext context) {
        return new SimpleBufferReference(referent, bufferLifecycleManager, context.getSharedFinalizer());
    }

    public static BufferReference createBufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, PythonContext context) {
        if (bufferLifecycleManager instanceof NativeBufferLifecycleManager) {
            return createNativeBufferReference(referent, bufferLifecycleManager, context);
        }
        return createSimpleBufferReference(referent, bufferLifecycleManager, context);
    }
}

final class NativeBufferReference extends BufferReference {

    public NativeBufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, AsyncHandler.SharedFinalizer sharedFinalizer) {
        super(referent, bufferLifecycleManager, sharedFinalizer);
    }

    @Override
    protected AsyncHandler.AsyncAction callback() {
        return new MemoryViewBuiltins.NativeBufferReleaseCallback(this);
    }
}

final class SimpleBufferReference extends BufferReference {

    public SimpleBufferReference(PMemoryView referent, BufferLifecycleManager bufferLifecycleManager, AsyncHandler.SharedFinalizer sharedFinalizer) {
        super(referent, bufferLifecycleManager, sharedFinalizer);
    }

    @Override
    protected AsyncHandler.AsyncAction callback() {
        return null;
    }
}
