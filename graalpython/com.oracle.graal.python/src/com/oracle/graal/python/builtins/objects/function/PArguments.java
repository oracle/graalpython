/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.generator.GeneratorControlData;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

//@formatter:off
/**
 * The layout of an argument array for a Python frame.
 *
 *                                         +-------------------+
 * INDEX_VARIABLE_ARGUMENTS             -> | Object[]          |
 *                                         +-------------------+
 * INDEX_KEYWORD_ARGUMENTS              -> | PKeyword[]        |
 *                                         +-------------------+
 * INDEX_GENERATOR_FRAME                -> | MaterializedFrame |
 *                                         +-------------------+
 * SPECIAL_ARGUMENT                     -> | Object            |
 *                                         +-------------------+
 * INDEX_GLOBALS_ARGUMENT               -> | PythonObject      |
 *                                         +-------------------+
 * INDEX_CLOSURE                        -> | PCell[]           |
 *                                         +-------------------+
 * INDEX_CALLER_FRAME_INFO              -> | PFrame.Reference  |
 *                                         +-------------------+
 * INDEX_CURRENT_FRAME_INFO             -> | PFrame.Reference  |
 *                                         +-------------------+
 * INDEX_CURRENT_EXCEPTION              -> | PException        |
 *                                         +-------------------+
 * USER_ARGUMENTS                       -> | arg_0             |
 *                                         | arg_1             |
 *                                         | ...               |
 *                                         | arg_(nArgs-1)     |
 *                                         +-------------------+
 *
 * The layout of a generator frame (stored in INDEX_GENERATOR_FRAME in the figure above)
 * is different in on place:
 *
 * MaterializedFrame
 *       |
 *       |
 *       |                    |         ....         |
 *       |                    +----------------------+
 * INDEX_GENERATOR_FRAME   -> | GeneratorControlData |
 *       |                    +----------------------+
 *       |                    |                      |
 *       |                              ....
 *       |                    |                      |
 *       |                    +----------------------+
 * INDEX_CALLER_FRAME_INFO -> | PDict (locals)       |
 *                            +----------------------+
 *                            |         ....         |
 */
//@formatter:on
public final class PArguments {
    public static final Object[] EMPTY_VARARGS = new Object[0];

    private static final FrameDescriptor EMTPY_FD = new FrameDescriptor();

    private static final int INDEX_VARIABLE_ARGUMENTS = 0;
    private static final int INDEX_KEYWORD_ARGUMENTS = 1;
    private static final int INDEX_GENERATOR_FRAME = 2;
    private static final int INDEX_SPECIAL_ARGUMENT = 3;
    private static final int INDEX_GLOBALS_ARGUMENT = 4;
    private static final int INDEX_CLOSURE = 5;
    private static final int INDEX_CALLER_FRAME_INFO = 6;
    private static final int INDEX_CURRENT_FRAME_INFO = 7;
    private static final int INDEX_CURRENT_EXCEPTION = 8;
    public static final int USER_ARGUMENTS_OFFSET = 9;

    public static boolean isPythonFrame(Frame frame) {
        return frame != null && isPythonFrame(frame.getArguments());
    }

    public static boolean isPythonFrame(Object[] frameArgs) {
        return frameArgs.length >= USER_ARGUMENTS_OFFSET && frameArgs[INDEX_KEYWORD_ARGUMENTS] instanceof PKeyword[];
    }

    public static boolean isGeneratorFrame(Frame frame) {
        return frame != null && isGeneratorFrame(frame.getArguments());
    }

    public static boolean isGeneratorFrame(Object[] frameArgs) {
        // a generator frame never has a frame info
        return frameArgs.length >= USER_ARGUMENTS_OFFSET && frameArgs[INDEX_GENERATOR_FRAME] instanceof GeneratorControlData;
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
        Object[] initialArguments = new Object[USER_ARGUMENTS_OFFSET + userArgumentLength];
        initialArguments[INDEX_VARIABLE_ARGUMENTS] = EMPTY_VARARGS;
        initialArguments[INDEX_KEYWORD_ARGUMENTS] = PKeyword.EMPTY_KEYWORDS;
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

    /**
     * This is only safe before the frame has started executing.
     */
    public static Object getCustomLocals(Frame frame) {
        return frame.getArguments()[INDEX_CURRENT_FRAME_INFO];
    }

    public static void setCustomLocals(Object[] args, Object locals) {
        assert args[INDEX_CURRENT_FRAME_INFO] == null : "cannot set custom locals if the frame is already available";
        args[INDEX_CURRENT_FRAME_INFO] = locals;
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

    public static PException getException(Frame frame) {
        return (PException) frame.getArguments()[INDEX_CURRENT_EXCEPTION];
    }

    public static void setException(Frame frame, PException exc) {
        setException(frame.getArguments(), exc);
    }

    public static void setException(Object[] arguments, PException exc) {
        arguments[INDEX_CURRENT_EXCEPTION] = exc;
    }

    public static void setClosure(Object[] arguments, PCell[] closure) {
        arguments[INDEX_CLOSURE] = closure;
    }

    public static PCell[] getClosure(Object[] arguments) {
        return (PCell[]) arguments[INDEX_CLOSURE];
    }

    public static PCell[] getClosure(Frame frame) {
        return getClosure(frame.getArguments());
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

    public static MaterializedFrame getGeneratorFrame(Object[] arguments) {
        return (MaterializedFrame) arguments[INDEX_GENERATOR_FRAME];
    }

    public static MaterializedFrame getGeneratorFrame(Frame frame) {
        return getGeneratorFrame(frame.getArguments());
    }

    public static MaterializedFrame getGeneratorFrameSafe(Frame frame) {
        return getGeneratorFrameSafe(frame.getArguments());
    }

    public static MaterializedFrame getGeneratorFrameSafe(Object[] arguments) {
        if (arguments[INDEX_GENERATOR_FRAME] instanceof MaterializedFrame) {
            return getGeneratorFrame(arguments);
        } else {
            return null;
        }
    }

    public static void setGeneratorFrame(Object[] arguments, MaterializedFrame generatorFrame) {
        arguments[INDEX_GENERATOR_FRAME] = generatorFrame;
    }

    public static void setControlData(Object[] arguments, GeneratorControlData generatorArguments) {
        MaterializedFrame generatorFrame = (MaterializedFrame) arguments[INDEX_GENERATOR_FRAME];
        generatorFrame.getArguments()[INDEX_GENERATOR_FRAME] = generatorArguments;
    }

    public static GeneratorControlData getControlDataFromGeneratorFrame(Frame generatorFrame) {
        return (GeneratorControlData) generatorFrame.getArguments()[INDEX_GENERATOR_FRAME];
    }

    public static GeneratorControlData getControlDataFromGeneratorArguments(Object[] arguments) {
        return getControlDataFromGeneratorFrame((MaterializedFrame) arguments[INDEX_GENERATOR_FRAME]);
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

    public static void setGeneratorFrameLocals(Object[] arguments, PDict locals) {
        arguments[INDEX_CALLER_FRAME_INFO] = locals;
    }

    public static PDict getGeneratorFrameLocals(Frame frame) {
        return getGeneratorFrameLocals(frame.getArguments());
    }

    public static PDict getGeneratorFrameLocals(Object[] arguments) {
        return (PDict) arguments[INDEX_CALLER_FRAME_INFO];
    }

    public static ThreadState getThreadState(VirtualFrame frame) {
        assert frame != null : "cannot get thread state without a frame";
        return new ThreadState(PArguments.getCurrentFrameInfo(frame), PArguments.getException(frame));
    }

    public static ThreadState getThreadStateOrNull(VirtualFrame frame, ConditionProfile hasFrameProfile) {
        return hasFrameProfile.profile(frame != null) ? getThreadState(frame) : null;
    }

    public static VirtualFrame frameForCall(ThreadState frame) {
        Object[] args = PArguments.create();
        PArguments.setCurrentFrameInfo(args, frame.info);
        PArguments.setException(args, frame.exc);
        return Truffle.getRuntime().createVirtualFrame(args, EMTPY_FD);
    }

    /**
     * Represents the current thread state information that needs to be passed between calls.
     */
    @ValueType
    public static final class ThreadState {
        private final PFrame.Reference info;
        private final PException exc;

        private ThreadState(PFrame.Reference info, PException exc) {
            this.info = info;
            this.exc = exc;
        }
    }
}
