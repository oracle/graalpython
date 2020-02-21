/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode.CannotCastException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * This is a simple allocation reported that is used in the C API function {@code _PyObject_New} to
 * report an object allocation.
 */
@ExportLibrary(InteropLibrary.class)
public class PyObjectAllocationReporter implements TruffleObject {

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Cached CastToJavaLongNode castToJavaLongNode,
                    @Cached GetCurrentFrameRef getCurrentFrameRef,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @CachedLibrary(limit = "3") InteropLibrary lib,
                    @Cached(value = "getAllocationReporter(context)", allowUncached = true) AllocationReporter reporter) throws ArityException, UnsupportedTypeException {
        TruffleLogger logger = PythonLanguage.getLogger();
        boolean isLoggable = logger.isLoggable(Level.FINE);
        boolean traceNativeMemory = PythonOptions.getFlag(context, PythonOptions.TraceNativeMemory);
        boolean reportAllocation = reporter.isActive();
        if (isLoggable || traceNativeMemory || reportAllocation) {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            Object allocatedObject = arguments[0];
            long objectSize;
            try {
                objectSize = castToJavaLongNode.execute(arguments[1]);
            } catch (CannotCastException e) {
                throw UnsupportedTypeException.create(arguments, "invalid type for second argument 'objectSize'");
            }
            if (isLoggable) {
                logger.fine(() -> String.format("Allocated memory at %s (size: %d bytes)", CApiContext.asHex(arguments[0]), objectSize));
            }
            if (traceNativeMemory) {
                PFrame.Reference ref = null;
                if (PythonOptions.getFlag(context, PythonOptions.TraceNativeMemoryCalls)) {
                    ref = getCurrentFrameRef.execute(null);
                    ref.markAsEscaped();
                }
                context.getCApiContext().traceAlloc(CApiContext.asPointer(allocatedObject, lib), ref, null);
            }
            if (reportAllocation) {
                try {
                    reporter.onEnter(null, 0, objectSize);
                    reporter.onReturnValue(allocatedObject, 0, objectSize);
                } catch (CannotCastException e) {
                    throw UnsupportedTypeException.create(arguments, "invalid type for second argument 'objectSize'");
                }
            }
            return 0;
        }
        return -2;
    }

    protected static AllocationReporter getAllocationReporter(PythonContext context) {
        return context.getEnv().lookup(AllocationReporter.class);
    }
}
