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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.runtime.native_memory.NativeBuffer;
import com.oracle.graal.python.runtime.sequence.storage.NativeIntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativePrimitiveSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import sun.misc.Unsafe;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;

public class NativeBufferContext {
    private static final Unsafe unsafe = PythonUtils.initUnsafe();

    private ReferenceQueue<NativePrimitiveSequenceStorage> referenceQueue;
    // We need to keep around the references since Phantom References are one way bound
    // Key: MemoryAddr, Value: PhantomReference
    private ConcurrentHashMap<NativePrimitiveReference, NativePrimitiveReference> phantomReferences;

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
            thread.start();
            this.nativeBufferReferenceCleanerThread = thread;
        }
    }

    @TruffleBoundary
    public NativeIntSequenceStorage createNativeIntStorage(NativeBuffer valueBuffer, int length) {
        var storage = new NativeIntSequenceStorage(valueBuffer, length);
        var phantomRef = new NativePrimitiveReference(storage, getReferenceQueue());
        phantomReferences.put(phantomRef, phantomRef);

        return storage;
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

    public NativeIntSequenceStorage toNativeIntStorage(int[] arr) {
        long sizeInBytes = (long) arr.length * (long) Integer.BYTES;
        NativeBuffer nativeBuffer = NativeBuffer.allocateNew(sizeInBytes);

        unsafe.copyMemory(arr, Unsafe.ARRAY_INT_BASE_OFFSET, null, nativeBuffer.getMemoryAddress(), sizeInBytes);
        return createNativeIntStorage(nativeBuffer, arr.length);
    }

    private ReferenceQueue<NativePrimitiveSequenceStorage> getReferenceQueue() {
        assert PythonContext.get(null).ownsGil();
        if (referenceQueue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initReferenceQueue();
        }

        return referenceQueue;
    }

    static final class NativeBufferDeallocatorRunnable implements Runnable {
        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(NativeBufferDeallocatorRunnable.class);

        private final ReferenceQueue<NativePrimitiveSequenceStorage> referenceQueue;
        private final ConcurrentHashMap<NativePrimitiveReference, NativePrimitiveReference> references;

        public NativeBufferDeallocatorRunnable(ReferenceQueue<NativePrimitiveSequenceStorage> referenceQueue,
                        ConcurrentHashMap<NativePrimitiveReference, NativePrimitiveReference> references) {
            this.referenceQueue = referenceQueue;
            this.references = references;
        }

        @Override
        public void run() {
            PythonContext pythonContext = PythonContext.get(null);
            PythonLanguage language = pythonContext.getLanguage();

            while (!pythonContext.getThreadState(language).isShuttingDown()) {
                try {
                    NativePrimitiveReference phantomRef = (NativePrimitiveReference) referenceQueue.remove();
                    phantomRef.release();
                    references.remove(phantomRef);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.fine("Native buffer reference cleaner thread was interrupted and is exiting");
                    return;
                }
            }
            LOGGER.fine("Native buffer reference cleaner thread is exiting.");
        }
    }

    static final class NativePrimitiveReference extends PhantomReference<NativePrimitiveSequenceStorage> {

        private final NativeBuffer buffer;

        public NativePrimitiveReference(NativePrimitiveSequenceStorage referent, ReferenceQueue<NativePrimitiveSequenceStorage> q) {
            super(referent, q);
            this.buffer = referent.getValueBuffer();
        }

        public void release() {
            buffer.release();
        }
    }
}
