/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode.GeneratorResult;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.generator.AbstractYieldNode;
import com.oracle.graal.python.nodes.generator.YieldFromNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class PGenerator extends PythonBuiltinObject {

    private String name;
    private String qualname;
    /**
     * Call targets with copies of the generator's AST. Each call target corresponds to one possible
     * entry point into the generator: the first call, and continuation for each yield. Each AST can
     * then specialize towards which nodes are executed when starting from that particular entry
     * point. When yielding, the next index to the next call target to continue from is updated via
     * {@link #setNextCallTarget} or {@link #handleResult}.
     */
    @CompilationFinal(dimensions = 1) protected final RootCallTarget[] callTargets;
    protected final Object[] arguments;
    private final PCell[] closure;
    private boolean finished;
    private PCode code;
    private int currentCallTarget;
    private final Object iterator;
    private final boolean isPRangeIterator;
    private final GeneratorInfo generatorInfo;
    private final PBytecodeRootNode bytecodeRootNode;
    private final FrameInfo frameInfo;
    // running means it is currently on the stack, not just started
    private boolean running;

    public static PGenerator create(PythonLanguage lang, String name, String qualname, RootCallTarget[] callTargets, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure,
                    ExecutionCellSlots cellSlots, GeneratorInfo generatorInfo, PythonObjectFactory factory,
                    Object iterator) {
        /*
         * Setting up the persistent frame in {@link #arguments}.
         */
        GeneratorControlData generatorArgs = new GeneratorControlData(generatorInfo);
        Object[] generatorFrameArguments = PArguments.create();
        MaterializedFrame generatorFrame = Truffle.getRuntime().createMaterializedFrame(generatorFrameArguments, frameDescriptor);
        PArguments.setGeneratorFrame(arguments, generatorFrame);
        PArguments.setControlData(arguments, generatorArgs);
        // The invoking node will set these two to the correct value only when the callee requests
        // it, otherwise they stay at the initial value, which we must set to null here
        PArguments.setException(arguments, null);
        PArguments.setCallerFrameInfo(arguments, null);
        PArguments.setCurrentFrameInfo(generatorFrameArguments, new PFrame.Reference(null));
        // set generator closure to the generator frame locals
        CompilerAsserts.partialEvaluationConstant(cellSlots);
        int[] freeVarSlots = cellSlots.getFreeVarSlots();
        CompilerAsserts.partialEvaluationConstant(freeVarSlots);
        int[] cellVarSlots = cellSlots.getCellVarSlots();
        CompilerAsserts.partialEvaluationConstant(cellVarSlots);
        Assumption[] cellVarAssumptions = cellSlots.getCellVarAssumptions();

        if (closure != null) {
            assert closure.length == freeVarSlots.length : "generator creation: the closure must have the same length as the free var slots array";
            assignClosure(closure, generatorFrame, freeVarSlots);
        } else {
            assert freeVarSlots.length == 0;
        }
        assignCells(generatorFrame, cellVarSlots, cellVarAssumptions);
        PArguments.setGeneratorFrameLocals(generatorFrameArguments, factory.createDictLocals(generatorFrame));
        return new PGenerator(lang, name, qualname, callTargets, generatorInfo, arguments, closure, iterator);
    }

    public static PGenerator create(PythonLanguage lang, String name, String qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        rootNode.createGeneratorFrame(arguments);
        return new PGenerator(lang, name, qualname, rootNode, callTargets, arguments);
    }

    @ExplodeLoop
    private static void assignCells(MaterializedFrame generatorFrame, int[] cellVarSlots, Assumption[] cellVarAssumptions) {
        // initialize own cell vars to new cells (these cells will be used by nested functions to
        // create their own closures)
        for (int i = 0; i < cellVarSlots.length; i++) {
            generatorFrame.setObject(cellVarSlots[i], new PCell(cellVarAssumptions[i]));
        }
    }

    @ExplodeLoop
    private static void assignClosure(PCell[] closure, MaterializedFrame generatorFrame, int[] freeVarSlots) {
        for (int i = 0; i < freeVarSlots.length; i++) {
            generatorFrame.setObject(freeVarSlots[i], closure[i]);
        }
    }

    private PGenerator(PythonLanguage lang, String name, String qualname, RootCallTarget[] callTargets, GeneratorInfo generatorInfo, Object[] arguments,
                    PCell[] closure, Object iterator) {
        super(PythonBuiltinClassType.PGenerator, PythonBuiltinClassType.PGenerator.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        this.callTargets = callTargets;
        this.generatorInfo = generatorInfo;
        this.currentCallTarget = 0;
        this.arguments = arguments;
        this.closure = closure;
        this.finished = false;
        this.iterator = iterator;
        this.isPRangeIterator = iterator instanceof PIntRangeIterator;
        this.bytecodeRootNode = null;
        this.frameInfo = null;
    }

    private PGenerator(PythonLanguage lang, String name, String qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        super(PythonBuiltinClassType.PGenerator, PythonBuiltinClassType.PGenerator.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        this.callTargets = callTargets;
        this.currentCallTarget = 0;
        this.arguments = arguments;
        this.finished = false;
        this.bytecodeRootNode = rootNode;
        this.frameInfo = (FrameInfo) rootNode.getFrameDescriptor().getInfo();
        this.iterator = null;
        this.isPRangeIterator = false;
        this.closure = null;
        this.generatorInfo = null;
    }

    public void handleResult(PythonLanguage language, GeneratorResult result) {
        assert usesBytecode();
        if (result.isReturn) {
            markAsFinished();
            return;
        }
        currentCallTarget = result.resumeBci;
        if (callTargets[currentCallTarget] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PBytecodeGeneratorRootNode rootNode = new PBytecodeGeneratorRootNode(language, bytecodeRootNode, result.resumeBci, result.resumeStackTop);
            callTargets[currentCallTarget] = rootNode.getCallTarget();
        }
    }

    public void setNextCallTarget() {
        assert !usesBytecode();
        currentCallTarget = PArguments.getControlDataFromGeneratorArguments(getArguments()).getLastYieldIndex();
    }

    /**
     * Returns the call target that should be used the next time the generator is called. Each time
     * a generator call target returns through a yield, the generator should be updated with the
     * next yield index to use via {@link #handleResult}
     */
    public RootCallTarget getCurrentCallTarget() {
        assert !finished;
        return callTargets[currentCallTarget];
    }

    public AbstractYieldNode getCurrentYieldNode() {
        assert !usesBytecode();
        if (currentCallTarget == 0 || running || finished) {
            // Not stopped on a yield
            return null;
        }
        // Call target indices are yield indices + 1, see AbstractYieldNode
        return generatorInfo.getYieldNodes()[currentCallTarget - 1];
    }

    public boolean usesBytecode() {
        return bytecodeRootNode != null;
    }

    public Object getYieldFrom() {
        if (!usesBytecode()) {
            AbstractYieldNode currentYield = getCurrentYieldNode();
            if (currentYield instanceof YieldFromNode) {
                int iteratorSlot = ((YieldFromNode) currentYield).getIteratorSlot();
                return PArguments.getControlDataFromGeneratorArguments(arguments).getIteratorAt(iteratorSlot);
            }
            return null;
        } else {
            if (running || finished) {
                return null;
            }
            return frameInfo.getYieldFrom(PArguments.getGeneratorFrame(arguments), getBci(), getCurrentRootNode().getResumeStackTop());
        }
    }

    private PBytecodeGeneratorRootNode getCurrentRootNode() {
        assert usesBytecode();
        return (PBytecodeGeneratorRootNode) getCurrentCallTarget().getRootNode();
    }

    public boolean isStarted() {
        return currentCallTarget != 0 && !running;
    }

    public int getBci() {
        if (!isStarted()) {
            return -1;
        } else if (finished) {
            return bytecodeRootNode.getCodeUnit().code.length;
        } else {
            return getCurrentRootNode().getResumeBci();
        }
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

    @ExportMessage.Ignore
    public Object getIterator() {
        return iterator;
    }

    public boolean isPRangeIterator() {
        return isPRangeIterator;
    }

    @Override
    public String toString() {
        return "<generator object " + name + " at " + hashCode() + ">";
    }

    public PCode getCode() {
        return code;
    }

    public void setCode(PCode code) {
        this.code = code;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        assert !running || !this.running : "Attempted to set an already running generator as running";
        this.running = running;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualname() {
        return qualname;
    }

    public void setQualname(String qualname) {
        this.qualname = qualname;
    }
}
