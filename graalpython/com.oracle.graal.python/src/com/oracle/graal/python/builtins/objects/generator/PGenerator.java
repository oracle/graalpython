/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode.GeneratorYieldResult;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.operations.POperationRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.ContinuationResult;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class PGenerator extends PythonBuiltinObject {

    private TruffleString name;
    private TruffleString qualname;
    /**
     * Call targets with copies of the generator's AST. Each call target corresponds to one possible
     * entry point into the generator: the first call, and continuation for each yield. Each AST can
     * then specialize towards which nodes are executed when starting from that particular entry
     * point. When yielding, the next index to the next call target to continue from is updated via
     * {@link #handleResult}.
     */
    @CompilationFinal(dimensions = 1) protected final RootCallTarget[] callTargets;
    protected final Object[] arguments;
    private boolean finished;
    private PCode code;
    private int currentCallTarget;
    private final PBytecodeRootNode bytecodeRootNode;
    private final FrameInfo frameInfo;
    // running means it is currently on the stack, not just started
    private boolean running;
    private final boolean isCoroutine;
    private final boolean isAsyncGen;

    private final POperationRootNode operationRootNode;
    private ContinuationResult currentContinuationResult;

    // An explicit isIterableCoroutine argument is needed for iterable coroutines (generally created
    // via types.coroutine)
    public static PGenerator create(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments,
                    PythonBuiltinClassType cls, boolean isIterableCoroutine) {
        // note: also done in PAsyncGen.create
        rootNode.createGeneratorFrame(arguments);
        return new PGenerator(lang, name, qualname, rootNode, callTargets, arguments, cls, isIterableCoroutine);
    }

    public static PGenerator create(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments,
                    PythonBuiltinClassType cls) {
        return create(lang, name, qualname, rootNode, callTargets, arguments, cls, false);
    }

    public static PGenerator createOperation(PythonLanguage lang, TruffleString name, TruffleString qualname, POperationRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments,
                    PythonBuiltinClassType cls) {
        // TODO: should this frame be used somewhere?
        rootNode.createGeneratorFrame(arguments);
        return new PGenerator(lang, name, qualname, rootNode, callTargets, arguments, cls, false);
    }

    protected PGenerator(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments, PythonBuiltinClassType cls,
                    boolean isIterableCoroutine) {
        super(cls, cls.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        this.callTargets = callTargets;
        this.currentCallTarget = 0;
        this.arguments = arguments;
        this.finished = false;
        this.bytecodeRootNode = rootNode;
        this.operationRootNode = null;
        this.frameInfo = (FrameInfo) rootNode.getFrameDescriptor().getInfo();
        this.isCoroutine = isIterableCoroutine || cls == PythonBuiltinClassType.PCoroutine;
        this.isAsyncGen = cls == PythonBuiltinClassType.PAsyncGenerator;
    }

    protected PGenerator(PythonLanguage lang, TruffleString name, TruffleString qualname, POperationRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments, PythonBuiltinClassType cls,
                    boolean isIterableCoroutine) {
        super(cls, cls.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        this.callTargets = callTargets;
        this.currentCallTarget = 0;
        this.arguments = arguments;
        this.finished = false;
        this.bytecodeRootNode = null;
        this.operationRootNode = rootNode;
        this.frameInfo = (FrameInfo) rootNode.getFrameDescriptor().getInfo();
        this.isCoroutine = isIterableCoroutine || cls == PythonBuiltinClassType.PCoroutine;
        this.isAsyncGen = cls == PythonBuiltinClassType.PAsyncGenerator;
    }

    public final void handleResult(PythonLanguage language, GeneratorYieldResult result) {
        currentCallTarget = result.resumeBci;
        if (callTargets[currentCallTarget] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PBytecodeGeneratorRootNode rootNode = new PBytecodeGeneratorRootNode(language, bytecodeRootNode, result.resumeBci, result.resumeStackTop);
            callTargets[currentCallTarget] = rootNode.getCallTarget();
        }
    }

    /**
     * Returns the call target that should be used the next time the generator is called. Each time
     * a generator call target returns through a yield, the generator should be updated with the
     * next yield index to use via {@link #handleResult}
     */
    public final RootCallTarget getCurrentCallTarget() {
        return callTargets[currentCallTarget];
    }

    public boolean isOperationGenerator() {
        return operationRootNode != null;
    }

    public ContinuationResult getContinuationResult() {
        assert isOperationGenerator();
        return currentContinuationResult;
    }

    public POperationRootNode getOperationRootNode() {
        return operationRootNode;
    }

    public final Object getYieldFrom() {
        if (running || finished) {
            return null;
        }
        return frameInfo.getYieldFrom(PArguments.getGeneratorFrame(arguments), getBci(), getCurrentRootNode().getResumeStackTop());
    }

    private PBytecodeGeneratorRootNode getCurrentRootNode() {
        return (PBytecodeGeneratorRootNode) getCurrentCallTarget().getRootNode();
    }

    public final boolean isStarted() {
        return currentCallTarget != 0 && !running;
    }

    public final int getBci() {
        if (!isStarted()) {
            return -1;
        } else if (finished) {
            return bytecodeRootNode.getCodeUnit().code.length;
        } else {
            return getCurrentRootNode().getResumeBci();
        }
    }

    public final Object[] getArguments() {
        return arguments;
    }

    public final boolean isFinished() {
        return finished;
    }

    public final void markAsFinished() {
        finished = true;
    }

    @Override
    public final String toString() {
        return "<generator object " + name + " at " + hashCode() + ">";
    }

    public final PCode getOrCreateCode(Node inliningTarget, InlinedConditionProfile hasCodeProfile, PythonObjectFactory.Lazy factory) {
        if (hasCodeProfile.profile(inliningTarget, code == null)) {
            RootCallTarget callTarget;
            callTarget = bytecodeRootNode.getCallTarget();
            code = factory.get(inliningTarget).createCode(callTarget);
        }
        return code;
    }

    public final boolean isRunning() {
        return running;
    }

    public final void setRunning(boolean running) {
        assert !running || !this.running : "Attempted to set an already running generator as running";
        this.running = running;
    }

    public final TruffleString getName() {
        return name;
    }

    public final void setName(TruffleString name) {
        this.name = name;
    }

    public final TruffleString getQualname() {
        return qualname;
    }

    public final void setQualname(TruffleString qualname) {
        this.qualname = qualname;
    }

    public final boolean isCoroutine() {
        return isCoroutine;
    }

    public final boolean isAsyncGen() {
        return isAsyncGen;
    }
}
