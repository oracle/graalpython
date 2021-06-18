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
package com.oracle.graal.python.builtins.objects.buffer;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
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
     * Return whether it is be possible to acquire a read-only buffer for this object. The actual
     * acquisition may still raise an exception. Equivalent of CPython's
     * {@code PyObject_CheckBuffer}.
     */
    @Abstract
    public boolean hasBuffer(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Return whether it may be possible to acquire a writable buffer for this object. It is just a
     * heuristic, the buffer may not be writable in the end. The caller needs to catch exceptions
     * from {@link #acquireWritable(Object)} to be sure.
     */
    @Abstract(ifExported = "acquireWritable")
    public boolean mayHaveWritableBuffer(Object receiver) {
        return hasBuffer(receiver);
    }

    /**
     * Acquire a buffer object meant for reading. Equivalent of CPython's;
     * <ul>
     * <li>{@code PyObject_GetBuffer} with flag {@code PyBUF_SIMPLE}
     * <li>Argument clinic's {@code Py_buffer} converter - our equivalent is
     * {@link ClinicConversion#Buffer}</li>
     * <li>{PyArg_Parse*}'s {@code "y*"} converter</li>
     * </ul>
     * Will raise exception if the acquisition fails. Must call
     * {@link PythonBufferAccessLibrary#release(Object)} on the returned object after the access is
     * finished. When intrinsifying CPython {PyObject_GetBuffer} calls, pay attention to what it
     * does to the exception. Sometimes it replaces the exception raised here with another one.
     */
    public Object acquireReadonly(Object receiver) {
        return acquireWritable(receiver);
    }

    /**
     * Acquire a buffer object meant for writing. Equivalent of CPython's {@code PyObject_GetBuffer}
     * with flag {@code PyBUF_WRITABLE}. For equivalents of clinic and {@code PyArg_Parse*}
     * converters, see {@link #acquireWritableWithTypeError(Object, String)}.Will raise exception if
     * the acquisition fails. Must call {@link PythonBufferAccessLibrary#release(Object)} on the
     * returned object after the access is finished. When intrinsifying CPython {PyObject_GetBuffer}
     * calls, pay attention to what it does to the exception. More often than not, it replaces the
     * exception raised here with another one.
     */
    @Abstract(ifExported = "mayHaveWritableBuffer")
    public Object acquireWritable(Object receiver) {
        throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, receiver);
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
    public final Object acquireWritableWithTypeError(Object receiver, String callerName) {
        try {
            return acquireWritable(receiver);
        } catch (PException e) {
            throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.S_BRACKETS_ARG_MUST_BE_READ_WRITE_BYTES_LIKE_NOT_P, callerName, receiver);
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
        public Object acquireWritable(Object receiver) {
            Object buffer = delegate.acquireWritable(receiver);
            assert delegate.hasBuffer(receiver);
            assert delegate.mayHaveWritableBuffer(receiver);
            assert PythonBufferAccessLibrary.getUncached().isBuffer(buffer);
            assert PythonBufferAccessLibrary.getUncached().isWritable(buffer);
            return buffer;
        }

        @Override
        public boolean hasBuffer(Object receiver) {
            return delegate.hasBuffer(receiver);
        }

        @Override
        public boolean mayHaveWritableBuffer(Object receiver) {
            return delegate.mayHaveWritableBuffer(receiver);
        }

        @Override
        public Object acquireReadonly(Object receiver) {
            Object buffer = delegate.acquireReadonly(receiver);
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
