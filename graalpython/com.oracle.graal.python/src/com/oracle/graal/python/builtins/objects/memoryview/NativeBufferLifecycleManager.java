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

/**
 * Object for tracking lifetime of buffers inside memoryviews. The only purpose is to release the
 * underlying buffer when this object's export count goes to 0 or it gets garbage collected. Should
 * only be created for native buffers that actually need to be released (have a release function).
 *
 * Rough equivalent of CPython's {@code _PyManagedBuffer_Type}
 */
public abstract class NativeBufferLifecycleManager extends BufferLifecycleManager {

    /**
     * Object used for tracking the lifetime of a Python native buffer (i.e. {@code Py_buffer}) when
     * created via a buffer-like native type (i.e. via {@code tp_as_buffer->bf_getbuffer}). The
     * {@link #bufferStructPointer} is meant to be used for calling {@code PyBuffer_Release} (which
     * will call the corresponding {@code tp_as_bufer->bf_releasebuffer} of the buffer's owner).
     */
    public static final class NativeBufferLifecycleManagerFromType extends NativeBufferLifecycleManager {
        /** Pointer to native Py_buffer */
        final Object bufferStructPointer;

        public NativeBufferLifecycleManagerFromType(Object bufferStructPointer) {
            assert bufferStructPointer != null;
            this.bufferStructPointer = bufferStructPointer;
        }
    }

    /**
     * Object used for tracking the lifetime of a Python native buffer when created via a buffer
     * slot (i.e. {@code Py_bf_getbuffer}) and if there is a corresponding release slot (
     * {@code Py_bf_releasebuffer}).
     */
    public static final class NativeBufferLifecycleManagerFromSlot extends NativeBufferLifecycleManager {
        final CExtPyBuffer buffer;
        /**
         * This is the self for the release function. It will in most cases be the same as
         * {@code buffer.getObj()} but this is not guaranteed. So, we need to explicitly keep track
         * of {@code self}.
         */
        final Object self;
        final Object releaseFunction;

        public NativeBufferLifecycleManagerFromSlot(CExtPyBuffer buffer, Object self, Object releaseFunction) {
            assert buffer != null;
            this.buffer = buffer;
            this.self = self;
            this.releaseFunction = releaseFunction;
        }
    }
}
