/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.function;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;

/**
 * The layout of an argument array for a Python frame.
 * <ul>
 * <li>{@code SPECIAL_ARGUMENT (Object)}</li>
 * <li>{@code INDEX_GLOBALS_ARGUMENT (PythonObject)}</li>
 * <li>{@code INDEX_FUNCTION_OBJECT (PFunction)}</li>
 * <li>{@code INDEX_CALLER_FRAME_INFO (PFrame.Reference)}</li>
 * <li>{@code INDEX_CURRENT_FRAME_INFO (PFrame.Reference)}</li>
 * <li>{@code INDEX_CURRENT_EXCEPTION (PException)}</li>
 * <li>{@code USER_ARGUMENTS (Object...)}; Further defined by a particular call convention:
 * <ul>
 * <li>Function calls: non-variadic arguments as individual items in order of {@code co_varnames},
 * then varargs as {@code Object[]} iff the function takes them, then variadic keywords as
 * {@code PKeyword[]} iff the function takes them. Implemented by {@link CreateArgumentsNode}</li>
 * <li>Generator resumes (non-DSL): generator frame ({@code MaterializedFrame}), then the send value
 * or null</li>
 * <li>Generator resumes (DSL): doesn't use PArguments to call the continuation root</li>
 * </ul>
 * </li>
 * </ul>
 */
public final class PArguments {
    private static final int INDEX_SPECIAL_ARGUMENT = 0;
    private static final int INDEX_GLOBALS_ARGUMENT = 1;
    private static final int INDEX_FUNCTION_OBJECT = 2;
    private static final int INDEX_CALLER_FRAME_INFO = 3;
    private static final int INDEX_CURRENT_FRAME_INFO = 4;
    private static final int INDEX_CURRENT_EXCEPTION = 5;
    public static final int USER_ARGUMENTS_OFFSET = 6;

    public static boolean isPythonFrame(Frame frame) {
        return frame != null && isPythonFrame(frame.getArguments());
    }

    public static boolean isPythonFrame(Object[] frameArgs) {
        return frameArgs.length >= USER_ARGUMENTS_OFFSET && frameArgs[INDEX_CURRENT_FRAME_INFO] instanceof PFrame.Reference;
    }

    public static Object[] withGlobals(PythonObject globals) {
        Object[] arguments = create();
        setGlobals(arguments, globals);
        return arguments;
    }

    public static Object[] create() {
        return create(0);
    }

    public static Object[] create(int userArgumentLength) {
        return new Object[USER_ARGUMENTS_OFFSET + userArgumentLength];
    }

    /**
     * Special arguments used to communicate various things to the callee. It is important than any
     * usage be documented and ensured that they cannot overlap.
     *
     * @see #getSpecialArgument(Frame)
     */
    public static void setSpecialArgument(Object[] arguments, Object value) {
        arguments[INDEX_SPECIAL_ARGUMENT] = value;
    }

    /**
     * The special argument is used for various purposes, none of which can occur at the same time:
     * <ul>
     * <li>The custom locals in a module or class scope when called through <code>exec</code> or
     * <code>__build_class__</code></li>
     * </ul>
     */
    public static Object getSpecialArgument(Frame frame) {
        return getSpecialArgument(frame.getArguments());
    }

    /**
     * @see #getSpecialArgument(Frame)
     */
    public static Object getSpecialArgument(Object[] arguments) {
        return arguments[INDEX_SPECIAL_ARGUMENT];
    }

    public static void setGlobals(Object[] arguments, PythonObject globals) {
        arguments[INDEX_GLOBALS_ARGUMENT] = globals;
    }

    public static PythonObject getGlobals(Object[] arguments) {
        return (PythonObject) arguments[INDEX_GLOBALS_ARGUMENT];
    }

    public static PythonObject getGlobals(Frame frame) {
        return getGlobals(frame.getArguments());
    }

    public static PythonObject getGlobalsSafe(Frame frame) {
        if (frame.getArguments()[INDEX_GLOBALS_ARGUMENT] instanceof PythonObject) {
            return getGlobals(frame);
        } else {
            return null;
        }
    }

    public static PFrame.Reference getCallerFrameInfo(Frame frame) {
        return getCallerFrameInfo(frame.getArguments());
    }

    public static PFrame.Reference getCallerFrameInfo(Object[] args) {
        return (PFrame.Reference) args[INDEX_CALLER_FRAME_INFO];
    }

    public static void setCallerFrameInfo(Object[] arguments, PFrame.Reference info) {
        arguments[INDEX_CALLER_FRAME_INFO] = info;
    }

    public static PFrame.Reference getCurrentFrameInfo(Frame frame) {
        return getCurrentFrameInfo(frame.getArguments());
    }

    public static PFrame.Reference getCurrentFrameInfo(Object[] args) {
        return (PFrame.Reference) args[INDEX_CURRENT_FRAME_INFO];
    }

    public static void setCurrentFrameInfo(Frame frame, PFrame.Reference info) {
        setCurrentFrameInfo(frame.getArguments(), info);
    }

    public static void setCurrentFrameInfo(Object[] arguments, PFrame.Reference info) {
        arguments[INDEX_CURRENT_FRAME_INFO] = info;
    }

    public static AbstractTruffleException getException(Object[] arguments) {
        return (AbstractTruffleException) getExceptionUnchecked(arguments);
    }

    public static AbstractTruffleException getException(Frame frame) {
        return (AbstractTruffleException) getExceptionUnchecked(frame.getArguments());
    }

    public static Object getExceptionUnchecked(Object[] arguments) {
        return arguments[INDEX_CURRENT_EXCEPTION];
    }

    public static boolean hasException(Object[] arguments) {
        Object exception = getExceptionUnchecked(arguments);
        return exception != null && exception != PException.NO_EXCEPTION;
    }

    public static void setException(Frame frame, AbstractTruffleException exc) {
        setException(frame.getArguments(), exc);
    }

    public static void setException(Object[] arguments, AbstractTruffleException exc) {
        setExceptionUnchecked(arguments, exc);
    }

    public static void setExceptionUnchecked(Object[] arguments, Object exc) {
        arguments[INDEX_CURRENT_EXCEPTION] = exc;
    }

    public static PFunction getFunctionObject(Object[] arguments) {
        return (PFunction) arguments[INDEX_FUNCTION_OBJECT];
    }

    public static void setFunctionObject(Object[] arguments, PFunction function) {
        arguments[INDEX_FUNCTION_OBJECT] = function;
    }

    public static void setArgument(Object[] arguments, int index, Object value) {
        arguments[USER_ARGUMENTS_OFFSET + index] = value;
    }

    public static Object getArgument(Object[] arguments, int index) {
        return arguments[USER_ARGUMENTS_OFFSET + index];
    }

    public static Object getArgument(Frame frame, int index) {
        return getArgument(frame.getArguments(), index);
    }
}
