/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.generator.GeneratorControlData;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

//@formatter:off
/**
 * The layout of an argument array for a normal frame.
 *
 *                                         +-------------------+
 * INDEX_VARIABLE_ARGUMENTS             -> | Object[]          |
 *                                         +-------------------+
 * INDEX_KEYWORD_ARGUMENTS              -> | PKeyword[]        |
 *                                         +-------------------+
 * INDEX_GENERATOR_FRAME                -> | MaterializedFrame |
 *                                         +-------------------+
 * INDEX_CALLER_FRAME_OR_EXCEPTION      -> | MaterializedFrame |
 *                                         | / PException      |
 *                                         +-------------------+
 * SPECIAL_ARGUMENT                     -> | Object            |
 *                                         +-------------------+
 * INDEX_GLOBALS_ARGUMENT               -> | PythonObject      |
 *                                         +-------------------+
 * INDEX_PFRAME_ARGUMENT                -> | PFrame[1]         |
 *                                         +-------------------+
 * INDEX_CLOSURE                        -> | PCell[]           |
 *                                         +-------------------+
 * USER_ARGUMENTS                       -> | arg_0             |
 *                                         | arg_1             |
 *                                         | ...               |
 *                                         | arg_(nArgs-1)     |
 *                                         +-------------------+
 *
 * The layout of a generator frame (stored in INDEX_GENERATOR_FRAME in the figure above)
 * is different in the second index:
 *
 * MaterializedFrame
 *       |
 *       |
 *       |---- arguments:     +----------------------+
 * INDEX_KEYWORD_ARGUMENTS -> | PKeyword[]           |
 *                            +----------------------+
 * INDEX_GENERATOR_FRAME   -> | GeneratorControlData |
 *                            +----------------------+
 *                            |         ....         |
 */
//@formatter:on
public final class PArguments {
    public static final Object[] EMPTY_VARARGS = new Object[0];

    public static final int INDEX_VARIABLE_ARGUMENTS = 0;
    public static final int INDEX_KEYWORD_ARGUMENTS = 1;
    public static final int INDEX_GENERATOR_FRAME = 2;
    public static final int INDEX_CALLER_FRAME_OR_EXCEPTION = 3;
    public static final int INDEX_SPECIAL_ARGUMENT = 4;
    public static final int INDEX_GLOBALS_ARGUMENT = 5;
    public static final int INDEX_PFRAME_ARGUMENT = 6;
    public static final int INDEX_CLOSURE = 7;
    public static final int USER_ARGUMENTS_OFFSET = 8;

    private static PFrame[] getPFrameWrapper() {
        // this is needed to bypass the fact that PFrame instances get a READONLY frame which will
        // not allow for setting the locals
        // TODO: a READONLY frame's is just a copy, revisit this to avoid possible flakiness
        return new PFrame[]{null};
    }

    private static Object[] iInitArguments() {
        return new Object[]{EMPTY_VARARGS, PKeyword.EMPTY_KEYWORDS, null, null, null, null, getPFrameWrapper(), null};
    }

    public static Object[] withGlobals(PythonObject globals) {
        Object[] arguments = iInitArguments();
        setGlobals(arguments, globals);
        return arguments;
    }

    public static Object[] create() {
        return iInitArguments();
    }

    public static Object[] create(int userArgumentLength) {
        Object[] initialArguments = new Object[USER_ARGUMENTS_OFFSET + userArgumentLength];
        initialArguments[INDEX_VARIABLE_ARGUMENTS] = EMPTY_VARARGS;
        initialArguments[INDEX_KEYWORD_ARGUMENTS] = PKeyword.EMPTY_KEYWORDS;
        initialArguments[INDEX_PFRAME_ARGUMENT] = getPFrameWrapper();
        return initialArguments;
    }

    public static void setVariableArguments(Object[] arguments, Object... variableArguments) {
        arguments[INDEX_VARIABLE_ARGUMENTS] = variableArguments;
    }

    public static Object[] getVariableArguments(Frame frame) {
        return getVariableArguments(frame.getArguments());
    }

    public static Object[] getVariableArguments(Object[] frame) {
        return (Object[]) frame[INDEX_VARIABLE_ARGUMENTS];
    }

    public static void setKeywordArguments(Object[] arguments, PKeyword[] keywordArguments) {
        arguments[INDEX_KEYWORD_ARGUMENTS] = keywordArguments;
    }

    public static PKeyword[] getKeywordArguments(Frame frame) {
        return getKeywordArguments(frame.getArguments());
    }

    public static PKeyword[] getKeywordArguments(Object[] frame) {
        return (PKeyword[]) frame[INDEX_KEYWORD_ARGUMENTS];
    }

    public static void setSpecialArgument(Object[] arguments, Object value) {
        arguments[INDEX_SPECIAL_ARGUMENT] = value;
    }

    /**
     * The special argument is used for various purposes, none of which can occur at the same time:
     * <ul>
     * <li>The value sent to a generator via <code>send</code></li>
     * <li>An exception thrown through a generator via <code>throw</code></li>
     * <li>The {@link ClassBodyRootNode} when we are executing a class body</li>
     * <li>The custom locals mapping when executing through <code>exec</code> with a
     * <code>locals</code> keyword argument</li>
     * </ul>
     */
    public static Object getSpecialArgument(Frame frame) {
        return getSpecialArgument(frame.getArguments());
    }

    public static Object getSpecialArgument(Object[] arguments) {
        return arguments[INDEX_SPECIAL_ARGUMENT];
    }

    public static void setGlobals(Object[] arguments, PythonObject globals) {
        arguments[INDEX_GLOBALS_ARGUMENT] = globals;
    }

    public static PythonObject getGlobals(Frame frame) {
        return (PythonObject) frame.getArguments()[INDEX_GLOBALS_ARGUMENT];
    }

    public static PythonObject getGlobalsSafe(Frame frame) {
        if (frame.getArguments()[INDEX_GLOBALS_ARGUMENT] instanceof PythonObject) {
            return getGlobals(frame);
        } else {
            return null;
        }
    }

    public static void setPFrame(Frame frame, PFrame pFrame) {
        setPFrame(frame.getArguments(), pFrame);
    }

    public static void setPFrame(Object[] args, PFrame pFrame) {
        ((PFrame[]) args[INDEX_PFRAME_ARGUMENT])[0] = pFrame;
    }

    public static PFrame getPFrame(Frame frame) {
        return ((PFrame[]) frame.getArguments()[INDEX_PFRAME_ARGUMENT])[0];
    }

    public static void setClosure(Object[] arguments, PCell[] closure) {
        arguments[INDEX_CLOSURE] = closure;
    }

    public static PCell[] getClosure(Frame frame) {
        return (PCell[]) frame.getArguments()[INDEX_CLOSURE];
    }

    public static void setArgument(Object[] arguments, int index, Object value) {
        arguments[USER_ARGUMENTS_OFFSET + index] = value;
    }

    public static Object getArgument(Object[] arguments, int index) {
        int argIdx = USER_ARGUMENTS_OFFSET + index;
        if (argIdx < arguments.length) {
            return arguments[argIdx];
        } else {
            return null;
        }
    }

    public static Object getArgument(Frame frame, int index) {
        return getArgument(frame.getArguments(), index);
    }

    public static int getUserArgumentLength(VirtualFrame frame) {
        return frame.getArguments().length - USER_ARGUMENTS_OFFSET;
    }

    public static int getUserArgumentLength(Object[] arguments) {
        return arguments.length - USER_ARGUMENTS_OFFSET;
    }

    public static Frame getGeneratorFrame(Frame frame) {
        return (Frame) frame.getArguments()[INDEX_GENERATOR_FRAME];
    }

    public static Frame getGeneratorFrameSafe(Frame frame) {
        if (frame.getArguments()[INDEX_GENERATOR_FRAME] instanceof Frame) {
            return getGeneratorFrame(frame);
        } else {
            return null;
        }
    }

    public static void setGeneratorFrame(Object[] arguments, Frame generatorFrame) {
        arguments[INDEX_GENERATOR_FRAME] = generatorFrame;
    }

    /**
     * The caller frame or exception argument is used for two purposes:
     * <ul>
     * <li>The caller frame if the callee requested it.</li>
     * <li>The currently handled exception (i.e. the caught exception).</li>
     * </ul>
     * These two purposes are exclusive in the sence that if the caller frame is requested, it will
     * also contain the caught exception in the special frame slot named
     * {@link FrameSlotIDs#CAUGHT_EXCEPTION}.
     */
    public static Object getCallerFrameOrException(Frame frame) {
        return frame.getArguments()[INDEX_CALLER_FRAME_OR_EXCEPTION];
    }

    public static void setCallerFrame(Object[] arguments, Frame callerFrame) {
        arguments[INDEX_CALLER_FRAME_OR_EXCEPTION] = callerFrame;
    }

    public static void setCaughtException(Object[] arguments, PException caughtException) {
        assert arguments[INDEX_CALLER_FRAME_OR_EXCEPTION] == null;
        arguments[INDEX_CALLER_FRAME_OR_EXCEPTION] = caughtException;
    }

    public static void setControlData(Object[] arguments, GeneratorControlData generatorArguments) {
        MaterializedFrame generatorFrame = (MaterializedFrame) arguments[INDEX_GENERATOR_FRAME];
        generatorFrame.getArguments()[INDEX_GENERATOR_FRAME] = generatorArguments;
    }

    public static GeneratorControlData getControlDataFromGeneratorFrame(Frame generatorFrame) {
        return (GeneratorControlData) generatorFrame.getArguments()[INDEX_GENERATOR_FRAME];
    }

    public static Object[] insertSelf(Object[] arguments, Object self) {
        final int userArgumentLength = arguments.length - USER_ARGUMENTS_OFFSET;
        Object[] results = create(userArgumentLength + 1);
        results[USER_ARGUMENTS_OFFSET] = self;

        for (int i = 0; i < userArgumentLength; i++) {
            results[USER_ARGUMENTS_OFFSET + 1 + i] = arguments[USER_ARGUMENTS_OFFSET + i];
        }

        return results;
    }

    public static int getNumberOfUserArgs(Object[] arguments) {
        return arguments.length - PArguments.USER_ARGUMENTS_OFFSET;
    }
}
