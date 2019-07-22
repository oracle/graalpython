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
package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

public final class PGenerator extends PythonBuiltinObject {

    protected final String name;
    protected final RootCallTarget callTarget;
    protected final FrameDescriptor frameDescriptor;
    protected final Object[] arguments;
    private final PCell[] closure;
    private boolean finished;
    private PCode code;

    public static PGenerator create(LazyPythonClass clazz, String name, RootCallTarget callTarget, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots,
                    FrameSlot[] flagSlots, FrameSlot[] indexSlots, PythonObjectFactory factory) {
        /*
         * Setting up the persistent frame in {@link #arguments}.
         */
        GeneratorControlData generatorArgs = new GeneratorControlData();
        Object[] generatorFrameArguments = PArguments.create();
        MaterializedFrame generatorFrame = Truffle.getRuntime().createMaterializedFrame(generatorFrameArguments, frameDescriptor);
        PArguments.setGeneratorFrame(arguments, generatorFrame);
        PArguments.setControlData(arguments, generatorArgs);
        PArguments.setCurrentFrameInfo(generatorFrameArguments, new PFrame.Reference(null));
        // set generator closure to the generator frame locals
        FrameSlot[] freeVarSlots = cellSlots.getFreeVarSlots();
        FrameSlot[] cellVarSlots = cellSlots.getCellVarSlots();
        Assumption[] cellVarAssumptions = cellSlots.getCellVarAssumptions();

        if (closure != null) {
            assert closure.length == freeVarSlots.length : "generator creation: the closure must have the same length as the free var slots array";
            for (int i = 0; i < closure.length; i++) {
                generatorFrame.setObject(freeVarSlots[i], closure[i]);
            }
        } else {
            assert freeVarSlots.length == 0;
        }
        // initialize own cell vars to new cells (these cells will be used by nested functions to
        // create their own closures)
        for (int i = 0; i < cellVarSlots.length; i++) {
            generatorFrame.setObject(cellVarSlots[i], new PCell(cellVarAssumptions[i]));
        }
        // reset all indices
        for (int i = 0; i < indexSlots.length; i++) {
            generatorFrame.setInt(indexSlots[i], 0);
        }
        // reset all flags
        for (int i = 0; i < flagSlots.length; i++) {
            generatorFrame.setBoolean(flagSlots[i], false);
        }
        PArguments.setGeneratorFrameLocals(generatorFrameArguments, factory.createDictLocals(generatorFrame));
        return new PGenerator(clazz, name, callTarget, frameDescriptor, arguments, closure);
    }

    private PGenerator(LazyPythonClass clazz, String name, RootCallTarget callTarget, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure) {
        super(clazz);
        this.name = name;
        this.callTarget = callTarget;
        this.frameDescriptor = frameDescriptor;
        this.arguments = arguments;
        this.closure = closure;
        this.finished = false;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public boolean isFinished() {
        return finished;
    }

    public void markAsFinished() {
        finished = true;
    }

    public PCell[] getClosure() {
        return closure;
    }

    @Override
    public String toString() {
        return "<generator object '" + name + "' at " + hashCode() + ">";
    }

    public static PGenerator require(Object value) {
        if (value instanceof PGenerator) {
            return (PGenerator) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("PGenerator required.");
    }

    public PCode getCode() {
        return code;
    }

    public void setCode(PCode code) {
        this.code = code;
    }
}
