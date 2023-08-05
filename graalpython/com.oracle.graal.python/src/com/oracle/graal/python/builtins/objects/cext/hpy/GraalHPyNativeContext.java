/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.io.IOException;
import java.io.PrintStream;

import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyABIVersion;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCallHelperFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public abstract class GraalHPyNativeContext implements TruffleObject {

    protected final GraalHPyContext context;

    protected GraalHPyNativeContext(GraalHPyContext context, boolean traceUpcalls) {
        this.context = context;
    }

    protected abstract String getName();

    protected final PythonContext getContext() {
        return context.getContext();
    }

    protected abstract void initNativeContext() throws Exception;

    protected abstract void finalizeNativeContext();

    protected abstract Object loadExtensionLibrary(Node location, PythonContext context, TruffleString name, TruffleString path) throws ImportException, IOException;

    protected abstract HPyABIVersion getHPyABIVersion(Object extLib, String getMajorVersionFuncName, String getMinorVersionFuncName) throws Exception;

    /**
     * Execute an HPy extension's init function and return the raw result value.
     *
     * @param extLib The HPy extension's shared library object (received from
     *            {@link #loadExtensionLibrary(Node, PythonContext, TruffleString, TruffleString)}).
     * @param initFuncName The HPy extension's init function name (e.g. {@code HPyInit_poc}).
     * @param name The HPy extension's name as requested by the user.
     * @param path The HPy extension's shared library path.
     * @param debug Flags indicating if the HPy extension should be initialized in debug mode.
     * @return The bare (unconverted) result of the HPy extension's init function. This will be a
     *         handle that was created with the given {@code hpyContext}.
     */
    protected abstract Object initHPyModule(Object extLib, String initFuncName, TruffleString name, TruffleString path, boolean debug)
                    throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException, ApiInitException;

    protected abstract HPyUpcall[] getUpcalls();

    protected abstract int[] getUpcallCounts();

    public abstract void initHPyDebugContext() throws ApiInitException;

    public abstract PythonModule getHPyDebugModule() throws ImportException;

    protected abstract void setNativeCache(long cachePtr);

    protected abstract long getWcharSize();

    protected abstract Object createArgumentsArray(Object[] args);

    protected abstract void freeArgumentsArray(Object argsArray);

    public abstract HPyCallHelperFunctionNode createCallHelperFunctionNode();

    public abstract HPyCallHelperFunctionNode getUncachedCallHelperFunctionNode();

    public abstract HPyFromCharPointerNode createFromCharPointerNode();

    public abstract HPyFromCharPointerNode getUncachedFromCharPointerNode();

    protected final boolean useNativeFastPaths() {
        return context.useNativeFastPaths;
    }

    public final GraalHPyContext getHPyContext() {
        return context;
    }

    @ExportMessage
    public void toNative() {
        try {
            toNativeInternal();
            if (useNativeFastPaths()) {
                initNativeFastPaths();
                /*
                 * Allocate a native array for the native space pointers of HPy objects and
                 * initialize it.
                 */
                context.allocateNativeSpacePointersMirror();
            }
        } catch (CannotCastException e) {
            /*
             * We should only receive 'toNative' if native access is allowed. Hence, the exception
             * should never happen.
             */
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    protected abstract void toNativeInternal();

    protected abstract void initNativeFastPaths();

    @TruffleBoundary
    public static PException checkThrowableBeforeNative(Throwable t, String where1, Object where2) {
        if (t instanceof PException pe) {
            // this is ok, and will be handled correctly
            throw pe;
        }
        if (t instanceof ThreadDeath td) {
            // ThreadDeath subclasses are used internally by Truffle
            throw td;
        }
        if (t instanceof StackOverflowError soe) {
            CompilerDirectives.transferToInterpreter();
            PythonContext context = PythonContext.get(null);
            context.reacquireGilAfterStackOverflow();
            PBaseException newException = context.factory().createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, EMPTY_OBJECT_ARRAY);
            throw ExceptionUtils.wrapJavaException(soe, null, newException);
        }
        if (t instanceof OutOfMemoryError oome) {
            PBaseException newException = PythonContext.get(null).factory().createBaseException(MemoryError);
            throw ExceptionUtils.wrapJavaException(oome, null, newException);
        }
        // everything else: log and convert to PException (SystemError)
        CompilerDirectives.transferToInterpreter();
        PNodeWithContext.printStack();
        PrintStream out = new PrintStream(PythonContext.get(null).getEnv().err());
        out.println("while executing " + where1 + " " + where2);
        out.println("should not throw exceptions apart from PException");
        t.printStackTrace(out);
        out.flush();
        throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.INTERNAL_EXCEPTION_OCCURED);
    }
}
