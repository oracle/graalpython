/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.buffer;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

/**
 * Provide a way to acquire and release underlying byte buffer of objects that support it. Roughly
 * corresponds to CPython's internal buffer API, with the primary difference that we don't always
 * have direct access to the underlying array. Objects implementing this API are:
 * <ul>
 * <li>{@code bytes}
 * <li>{@code bytearray}
 * <li>{@code array}
 * <li>{@code memoryview}
 * <li>few other module-specific managed objects (e.g. {@code BytesIO})
 * <li>objects that implement the C buffer API (using {@code tp_as_buffer} slot)
 * <li>interop objects that return true from {@link InteropLibrary#hasBufferElements(Object)}
 * </ul>
 * The acquired buffer object should be accessed using {@link PythonBufferAccessLibrary} and needs
 * to be released using {@link PythonBufferAccessLibrary#release(Object)} method when done.
 */
@GenerateLibrary(assertions = PythonBufferAcquireLibrary.Assertions.class)
public abstract class PythonBufferAcquireLibrary extends Library {
    /**
     * Return whether it is possible to acquire a read-only buffer for this object. The actual
     * acquisition may still raise an exception. Equivalent of CPython's
     * {@code PyObject_CheckBuffer}.
     */
    @Abstract
    public boolean hasBuffer(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Acquire a buffer object meant for reading. Equivalent of CPython's;
     * <ul>
     * <li>{@code PyObject_GetBuffer} with flag {@code PyBUF_SIMPLE}
     * <li>Argument clinic's {@code Py_buffer} converter - our equivalent is
     * {@link ClinicConversion#ReadableBuffer}</li>
     * <li>{PyArg_Parse*}'s {@code "y*"} converter</li>
     * </ul>
     * Will raise exception if the acquisition fails. Must call
     * {@link PythonBufferAccessLibrary#release(Object)} on the returned object after the access is
     * finished. When intrinsifying CPython {PyObject_GetBuffer} calls, pay attention to what it
     * does to the exception. Sometimes it replaces the exception raised here with another one.
     * <p>
     * <b>IMPORTANT:</b> This method may only be used in the context of an indirect call (see
     * {@link IndirectCallContext}). If a frame is available, prefer using convenience methods
     * {@link #acquireReadonly(Object, VirtualFrame, IndirectCallData)} or
     * {@link #acquireReadonly(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallData)}
     * .
     * </p>
     */
    public final Object acquireReadonly(Object receiver) {
        return acquire(receiver, BufferFlags.PyBUF_SIMPLE);
    }

    /**
     * Convenience method that sets up an indirect call and then uses
     * {@link #acquireReadonly(Object)}. <b>NOTE:</b> the provided indirectCallData must belong to a
     * node which is an ancestor of the library.
     */
    public final Object acquireReadonly(Object receiver, VirtualFrame frame, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, indirectCallData);
        try {
            return acquire(receiver, BufferFlags.PyBUF_SIMPLE);
        } finally {
            IndirectCallContext.exit(frame, indirectCallData, savedState);
        }
    }

    /**
     * Convenience method that sets up an indirect call and then uses
     * {@link #acquireReadonly(Object)}. <b>NOTE:</b> the provided indirectCallData must belong to a
     * node which is an ancestor of the library.
     */
    public final Object acquireReadonly(Object receiver, VirtualFrame frame, PythonContext context, PythonLanguage language, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, language, context, indirectCallData);
        try {
            return acquire(receiver, BufferFlags.PyBUF_SIMPLE);
        } finally {
            IndirectCallContext.exit(frame, language, context, savedState);
        }
    }

    /**
     * Acquire a buffer object meant for writing. Equivalent of CPython's {@code PyObject_GetBuffer}
     * with flag {@code PyBUF_WRITABLE}. For equivalents of clinic and {@code PyArg_Parse*}
     * converters, see
     * {@link #acquireWritableWithTypeError(Object, String, VirtualFrame, IndirectCallData)} . Will
     * raise exception if the acquisition fails. Must call
     * {@link PythonBufferAccessLibrary#release(Object)} on the returned object after the access is
     * finished. When intrinsifying CPython {PyObject_GetBuffer} calls, pay attention to what it
     * does to the exception. More often than not, it replaces the exception raised here with
     * another one.
     * <p>
     * <b>IMPORTANT:</b> This method may only be used in the context of an indirect call (see
     * {@link IndirectCallContext}). If a frame is available, prefer using convenience methods
     * {@link #acquireWritable(Object, VirtualFrame, IndirectCallData)} or
     * {@link #acquireWritable(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallData)}
     * .
     * </p>
     */
    public final Object acquireWritable(Object receiver) {
        return acquire(receiver, BufferFlags.PyBUF_WRITABLE);
    }

    /**
     * Convenience method that sets up an indirect call and then uses
     * {@link #acquireWritable(Object)}. <b>NOTE:</b> the provided indirectCallData must belong to a
     * node which is an ancestor of the library.
     */
    public final Object acquireWritable(Object receiver, VirtualFrame frame, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, indirectCallData);
        try {
            return acquire(receiver, BufferFlags.PyBUF_WRITABLE);
        } finally {
            IndirectCallContext.exit(frame, indirectCallData, savedState);
        }
    }

    /**
     * Convenience method that sets up an indirect call and then uses
     * {@link #acquireWritable(Object)}. <b>NOTE:</b> the provided indirectCallData must belong to a
     * node which is an ancestor of the library.
     */
    public final Object acquireWritable(Object receiver, VirtualFrame frame, PythonContext context, PythonLanguage language, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, language, context, indirectCallData);
        try {
            return acquire(receiver, BufferFlags.PyBUF_WRITABLE);
        } finally {
            IndirectCallContext.exit(frame, language, context, savedState);
        }
    }

    /**
     * Acquire a buffer object meant for writing. Equivalent of CPython's:
     * <ul>
     * <li>Argument clinic's {@code Py_buffer(accept={rwbuffer})} converter - our equivalent is
     * {@link ClinicConversion#WritableBuffer}</li>
     * <li>{PyArg_Parse*}'s {@code "w*"} converter</li>
     * </ul>
     * Will raise a {@code TypeError} if the acquisition fails, regardless of what exception the
     * acquisition produced.
     */
    public final Object acquireWritableWithTypeError(Object receiver, String callerName, VirtualFrame frame, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, indirectCallData);
        try {
            return acquireWritable(receiver);
        } catch (PException e) {
            throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.S_BRACKETS_ARG_MUST_BE_READ_WRITE_BYTES_LIKE_NOT_P, callerName, receiver);
        } finally {
            IndirectCallContext.exit(frame, indirectCallData, savedState);
        }
    }

    /**
     * Acquire a buffer with given flags. Equivalent of CPython's {@code PyObject_GetBuffer}. Note
     * that the API is currently not expressive enough to deal with the more complex types. Make
     * sure you know what the flags mean and that you can handle the result properly.
     * <p>
     * <b>IMPORTANT:</b> This method may only be used in the context of an indirect call (see
     * {@link IndirectCallContext}). If a frame is available, prefer using convenience methods
     * {@link #acquire(Object, int, VirtualFrame, IndirectCallData)}}.
     * </p>
     *
     * @param flags combined constants from {@link BufferFlags}. Unlike CPython, our buffer objects
     *            typically return themselves for performance reasons and thus cannot remove the
     *            format/shape/strides when the flags request them to do so. Be prepared to ignore
     *            those elements in such case.
     */
    @Abstract
    public Object acquire(Object receiver, int flags) {
        throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, receiver);
    }

    /**
     * Convenience method that sets up an indirect call and then uses {@link #acquire(Object, int)}.
     * <b>NOTE:</b> the provided indirectCallData must belong to a node which is an ancestor of the
     * library.
     */
    public final Object acquire(Object receiver, int flags, VirtualFrame frame, IndirectCallData indirectCallData) {
        Object savedState = IndirectCallContext.enter(frame, indirectCallData);
        try {
            return acquire(receiver, flags);
        } finally {
            IndirectCallContext.exit(frame, indirectCallData, savedState);
        }
    }

    static class Assertions extends PythonBufferAcquireLibrary {
        @Child PythonBufferAcquireLibrary delegate;

        public Assertions(PythonBufferAcquireLibrary delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accepts(Object receiver) {
            return delegate.accepts(receiver);
        }

        @Override
        public boolean hasBuffer(Object receiver) {
            return delegate.hasBuffer(receiver);
        }

        @Override
        public Object acquire(Object receiver, int flags) {
            Object buffer = delegate.acquire(receiver, flags);
            assert delegate.hasBuffer(receiver);
            assert PythonBufferAccessLibrary.getUncached().isBuffer(buffer);
            return buffer;
        }
    }

    static final LibraryFactory<PythonBufferAcquireLibrary> FACTORY = LibraryFactory.resolve(PythonBufferAcquireLibrary.class);

    public static LibraryFactory<PythonBufferAcquireLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonBufferAcquireLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
