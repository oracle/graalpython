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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(BuiltinNames.class)
public abstract class WriteUnraisableNode extends Node {
    static final String UNRAISABLE_HOOK_ARGUMENTS_CLASS = "__UnraisableHookArgs";

    public abstract void execute(VirtualFrame frame, PBaseException exception, String message, Object object);

    @Specialization(limit = "1")
    static void writeUnraisable(VirtualFrame frame, PBaseException exception, String message, Object object,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @CachedLibrary("exception") PythonObjectLibrary lib,
                    @Cached PythonObjectFactory factory,
                    @Cached GetExceptionTracebackNode getExceptionTracebackNode,
                    @Cached("create(UNRAISABLEHOOK)") GetAttributeNode getUnraisableHook,
                    @Cached CallNode callUnraisableHook,
                    @Cached("create(UNRAISABLE_HOOK_ARGUMENTS_CLASS)") GetAttributeNode getArgumentsFactory,
                    @Cached CallNode callArgumentsFactory) {
        try {
            PythonModule sysModule = contextRef.get().getCore().lookupBuiltinModule("sys");
            Object unraisablehook = getUnraisableHook.executeObject(frame, sysModule);
            Object argumentsFactory = getArgumentsFactory.executeObject(frame, sysModule);
            Object exceptionType = lib.getLazyPythonClass(exception);
            Object traceback = getExceptionTracebackNode.execute(frame, exception);
            if (traceback == null) {
                traceback = PNone.NONE;
            }
            Object messageObj = PNone.NONE;
            if (message != null) {
                messageObj = formatMessage(message);
            }
            Object hookArguments = callArgumentsFactory.execute(frame, argumentsFactory,
                            factory.createTuple(new Object[]{exceptionType, exception, traceback, messageObj, object != null ? object : PNone.NONE}));
            callUnraisableHook.execute(frame, unraisablehook, hookArguments);
        } catch (PException e) {
            ignoreException(message);
        }
    }

    @TruffleBoundary
    private static void ignoreException(String message) {
        if (message != null) {
            System.err.println(formatMessage(message));
        } else {
            System.err.println("Exception ignored in sys.unraisablehook");
        }
    }

    @TruffleBoundary
    private static Object formatMessage(String message) {
        return "Exception ignored " + message;
    }

    public static WriteUnraisableNode create() {
        return WriteUnraisableNodeGen.create();
    }
}
