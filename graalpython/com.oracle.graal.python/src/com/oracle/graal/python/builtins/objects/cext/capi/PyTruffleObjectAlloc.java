/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public class PyTruffleObjectAlloc implements TruffleObject {
    private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffleObjectAlloc.class);

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    protected static PythonContext getContext(Node node) {
        return PythonContext.get(node);
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Cached CastToJavaLongLossyNode castToJavaLongNode,
                    @Cached GetCurrentFrameRef getCurrentFrameRef,
                    @CachedLibrary(limit = "3") InteropLibrary lib,
                    @Cached(value = "getAllocationReporter(getContext(lib))", allowUncached = true) AllocationReporter reporter, @Exclusive @Cached GilNode gil) throws ArityException {
        boolean mustRelease = gil.acquire();
        try {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, 2, arguments.length);
            }

            Object allocatedObject = arguments[0];
            Object sizeObject = arguments[1];
            long objectSize;
            try {
                objectSize = castToJavaLongNode.execute(sizeObject);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("invalid type for second argument 'objectSize'");
            }

            // memory management
            PythonContext context = getContext(lib);
            CApiContext cApiContext = context.getCApiContext();
            cApiContext.increaseMemoryPressure(objectSize, lib);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            boolean reportAllocation = reporter.isActive();
            if (isLoggable || traceNativeMemory || reportAllocation) {
                if (isLoggable) {
                    LOGGER.finer(() -> PythonUtils.formatJString("Allocated memory at %s (size: %d bytes)", CApiContext.asHex(allocatedObject), objectSize));
                }
                if (traceNativeMemory) {
                    PFrame.Reference ref = null;
                    if (context.getOption(PythonOptions.TraceNativeMemoryCalls)) {
                        ref = getCurrentFrameRef.execute(null);
                        ref.markAsEscaped();
                    }
                    cApiContext.traceAlloc(CApiContext.asPointer(allocatedObject, lib), ref, null, objectSize);
                }
                if (reportAllocation) {
                    reporter.onEnter(null, 0, objectSize);
                    reporter.onReturnValue(allocatedObject, 0, objectSize);
                }
                return 0;
            }
            return -2;
        } finally {
            gil.release(mustRelease);
        }
    }

    static AllocationReporter getAllocationReporter(PythonContext context) {
        return context.getEnv().lookup(AllocationReporter.class);
    }

}
