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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.runtime.sequence.storage.native2.NativeBuffer;
import com.oracle.graal.python.runtime.sequence.storage.native2.NativeBufferDeallocatorRunnable;
import com.oracle.graal.python.runtime.sequence.storage.native2.NativeBufferReference;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import sun.misc.Unsafe;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;

public class NativeBufferContext {
    private static final Unsafe unsafe = PythonUtils.initUnsafe();

    @CompilationFinal private ReferenceQueue<NativeBuffer> referenceQueue;
    // We need to keep around the references since Phantom References are one way bound
    private ConcurrentHashMap<NativeBufferReference, NativeBufferReference> phantomReferences;

    private Thread nativeBufferReferenceCleanerThread;

    @TruffleBoundary
    public void initReferenceQueue() {
        var context = PythonContext.get(null);
        this.referenceQueue = new ReferenceQueue<>();
        this.phantomReferences = new ConcurrentHashMap<>();
        TruffleLanguage.Env env = context.getEnv();
        if (env.isCreateThreadAllowed()) {
            var runnable = new NativeBufferDeallocatorRunnable(referenceQueue, this.phantomReferences);
            Thread thread = env.newTruffleThreadBuilder(runnable).build();
            thread.setDaemon(true);
            // TODO Do I need to do something? If the thread is daemon it should be cleaned
            thread.start();
            this.nativeBufferReferenceCleanerThread = thread;
        }
    }

    @TruffleBoundary
    public NativeBuffer createNativeBuffer(long capacityInBytes) {
        var buf = NativeBuffer.allocateNew(capacityInBytes);
        var phantomRef = new NativeBufferReference(buf, getReferenceQueue());
        phantomReferences.put(phantomRef, phantomRef);

        return buf;
    }

    public void finalizeContext() {
        Thread thread = this.nativeBufferReferenceCleanerThread;
        if (thread != null) {
            if (thread.isAlive() && !thread.isInterrupted()) {
                thread.interrupt();
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public NativeBuffer toNativeBuffer(int[] arr) {
        long sizeInBytes = (long) arr.length * (long) Integer.BYTES;
        var buffer = createNativeBuffer(sizeInBytes);

        unsafe.copyMemory(arr, Unsafe.ARRAY_INT_BASE_OFFSET, null, buffer.getMemoryAddress(), sizeInBytes);

        return buffer;

    }

    private ReferenceQueue<NativeBuffer> getReferenceQueue() {
        if (referenceQueue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initReferenceQueue();
        }

        return referenceQueue;
    }
}
