/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

//@formatter:off
/**
 * The layout of an argument array for a normal frame.
 *
 *                            +-------------------+
 * INDEX_KEYWORD_ARGUMENTS -> | PKeyword[]        |
 *                            +-------------------+
 * INDEX_GENERATOR_FRAME   -> | MaterializedFrame |
 *                            +-------------------+
 * SPECIAL_ARGUMENT        -> | Object            |
 *                            +-------------------+
 * INDEX_GLOBALS_ARGUMENT  -> | PythonObject      |
 *                            +-------------------+
 * INDEX_PFRAME_ARGUMENT   -> | PFrame[1]         |
 *                            +-------------------+
 * INDEX_CLOSURE           -> | PCell[]           |
 *                            +-------------------+
 * USER_ARGUMENTS          -> | arg_0             |
 *                            | arg_1             |
 *                            | ...               |
 *                            | arg_(nArgs-1)     |
 *                            +-------------------+
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

    public static final int INDEX_KEYWORD_ARGUMENTS = 0;
    public static final int INDEX_GENERATOR_FRAME = 1;
    public static final int INDEX_SPECIAL_ARGUMENT = 2;
    public static final int INDEX_GLOBALS_ARGUMENT = 3;
    public static final int INDEX_PFRAME_ARGUMENT = 4;
    public static final int INDEX_CLOSURE = 5;
    public static final int USER_ARGUMENTS_OFFSET = 6;

    private static PFrame[] getPFrameWrapper() {
        // this is needed to bypass the fact that PFrame instances get a READONLY frame which will
        // not allow for setting the locals
        // TODO: a READONLY frame's is just a copy, revisit this to avoid possible flakiness
        return new PFrame[]{null};
    }

    private static Object[] iInitArguments() {
        return new Object[]{PKeyword.EMPTY_KEYWORDS, null, null, null, getPFrameWrapper(), null};
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
        initialArguments[INDEX_KEYWORD_ARGUMENTS] = PKeyword.EMPTY_KEYWORDS;
        initialArguments[INDEX_PFRAME_ARGUMENT] = getPFrameWrapper();
        return initialArguments;
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

    public static Object getSpecialArgument(Frame frame) {
        return frame.getArguments()[INDEX_SPECIAL_ARGUMENT];
    }

    public static void setGlobals(Object[] arguments, PythonObject globals) {
        arguments[INDEX_GLOBALS_ARGUMENT] = globals;
    }

    public static PythonObject getGlobals(Frame frame) {
        return (PythonObject) frame.getArguments()[INDEX_GLOBALS_ARGUMENT];
    }

    public static void setPFrame(Frame frame, PFrame pFrame) {
        ((PFrame[]) frame.getArguments()[INDEX_PFRAME_ARGUMENT])[0] = pFrame;
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

    public static PCell[] getClosure(Frame frame, boolean fromCaller) {
        if (fromCaller) {
            Frame callerFrame = Truffle.getRuntime().getCallerFrame().getFrame(FrameInstance.FrameAccess.READ_ONLY);
            return PArguments.getClosure(callerFrame);
        }
        return getClosure(frame);
    }

    public static PythonObject getGlobals(Frame frame, boolean fromCaller) {
        if (fromCaller) {
            Frame callerFrame = Truffle.getRuntime().getCallerFrame().getFrame(FrameInstance.FrameAccess.READ_ONLY);
            return PArguments.getGlobals(callerFrame);
        }
        return getGlobals(frame);
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

    public static void setGeneratorFrame(Object[] arguments, Frame generatorFrame) {
        arguments[INDEX_GENERATOR_FRAME] = generatorFrame;
    }

    public static void setControlData(Object[] arguments, GeneratorControlData generatorArguments) {
        MaterializedFrame generatorFrame = (MaterializedFrame) arguments[INDEX_GENERATOR_FRAME];
        generatorFrame.getArguments()[INDEX_GENERATOR_FRAME] = generatorArguments;
    }

    public static GeneratorControlData getControlData(Frame frame) {
        Frame generatorFrame = getGeneratorFrame(frame);
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
}
