package com.oracle.graal.python.builtins.objects.cext.capi;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode.CannotCastException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public class PyTruffleObjectAlloc implements TruffleObject {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleObjectAlloc.class);

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Cached CastToJavaLongNode castToJavaLongNode,
                    @Cached GetCurrentFrameRef getCurrentFrameRef,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached GenericInvokeNode invokeNode,
                    @CachedLibrary(limit = "3") InteropLibrary lib,
                    @Cached(value = "getAllocationReporter(contextRef)", allowUncached = true) AllocationReporter reporter) throws ArityException {
        if (arguments.length != 2) {
            throw ArityException.create(2, arguments.length);
        }

        Object allocatedObject = arguments[0];
        Object sizeObject = arguments[1];
        long objectSize;
        try {
            objectSize = castToJavaLongNode.execute(sizeObject);
        } catch (CannotCastException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("invalid type for second argument 'objectSize'");
        }

        // memory management
        PythonContext context = contextRef.get();
        CApiContext cApiContext = context.getCApiContext();
        cApiContext.increaseMemoryPressure(invokeNode, objectSize);

        boolean isLoggable = LOGGER.isLoggable(Level.FINE);
        boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
        boolean reportAllocation = reporter.isActive();
        if (isLoggable || traceNativeMemory || reportAllocation) {
            if (isLoggable) {
                LOGGER.fine(() -> String.format("Allocated memory at %s (size: %d bytes)", CApiContext.asHex(allocatedObject), objectSize));
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
    }

    static CallTargetInvokeNode createInvokeNode(ContextReference<PythonContext> contextRef) {
        CApiContext cApiContext = contextRef.get().getCApiContext();
        return CallTargetInvokeNode.create(cApiContext.getTriggerAsyncActionsCallTarget(), false, false);
    }

    static AllocationReporter getAllocationReporter(ContextReference<PythonContext> contextRef) {
        return contextRef.get().getEnv().lookup(AllocationReporter.class);
    }

}
