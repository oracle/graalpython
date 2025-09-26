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
// skip GIL
package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode.GeneratorYieldResult;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class PGenerator extends PythonBuiltinObject {

    private TruffleString name;
    private TruffleString qualname;
    private final FrameInfo frameInfo;

    private boolean finished;
    // running means it is currently on the stack, not just started
    private boolean running;
    private final boolean isCoroutine;
    private final boolean isAsyncGen;

    private PCode code;

    private final MaterializedFrame frame;
    private final PythonObject globals;
    private final PFunction generatorFunction;

    // TODO (GR-38700): remove BytecodeState after migrated to the Bytecode DSL interpreter.
    protected static class BytecodeState {
        private final PBytecodeRootNode rootNode;

        /**
         * Call targets with copies of the generator's AST. Each call target corresponds to one
         * possible entry point into the generator: the first call, and continuation for each yield.
         * Each AST can then specialize towards which nodes are executed when starting from that
         * particular entry point. When yielding, the next index to the next call target to continue
         * from is updated via {@link #handleResult}.
         */
        @CompilationFinal(dimensions = 1) private final RootCallTarget[] callTargets;
        private int currentCallTarget;

        public BytecodeState(PBytecodeRootNode rootNode, RootCallTarget[] callTargets) {
            this.rootNode = rootNode;
            this.callTargets = callTargets;
            this.currentCallTarget = 0;
        }

        public RootCallTarget getCurrentCallTarget() {
            return callTargets[currentCallTarget];
        }

        public PBytecodeGeneratorRootNode getCurrentRootNode() {
            return (PBytecodeGeneratorRootNode) getCurrentCallTarget().getRootNode();
        }

        public Object handleResult(PythonLanguage language, GeneratorYieldResult result) {
            currentCallTarget = result.resumeBci;
            if (callTargets[currentCallTarget] == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PBytecodeGeneratorRootNode generatorRootNode = new PBytecodeGeneratorRootNode(language, rootNode, result.resumeBci, result.resumeStackTop);
                callTargets[currentCallTarget] = generatorRootNode.getCallTarget();
            }
            return result.yieldValue;
        }
    }

    private static class BytecodeDSLState {
        private final PBytecodeDSLRootNode rootNode;
        private final Object[] arguments;
        private ContinuationRootNode continuationRootNode;
        private boolean isStarted;

        public BytecodeDSLState(PBytecodeDSLRootNode rootNode, Object[] arguments, ContinuationRootNode continuationRootNode) {
            this.rootNode = rootNode;
            this.arguments = arguments;
            this.continuationRootNode = continuationRootNode;
        }

        public Object handleResult(ContinuationResult result) {
            isStarted = true;
            continuationRootNode = result.getContinuationRootNode();
            return result.getResult();
        }
    }

    // This is either BytecodeState or BytecodeDSLState.
    private final Object state;

    private BytecodeState getBytecodeState() {
        return (BytecodeState) state;
    }

    private BytecodeDSLState getBytecodeDSLState() {
        return (BytecodeDSLState) state;
    }

    // An explicit isIterableCoroutine argument is needed for iterable coroutines (generally created
    // via types.coroutine)
    public static PGenerator create(PythonLanguage lang, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments,
                    PythonBuiltinClassType cls, boolean isIterableCoroutine) {
        // note: also done in PAsyncGen.create
        MaterializedFrame generatorFrame = rootNode.createGeneratorFrame(arguments);
        return new PGenerator(lang, function, generatorFrame, cls, isIterableCoroutine, new BytecodeState(rootNode, callTargets));
    }

    public static PGenerator create(PythonLanguage lang, PFunction function, PBytecodeDSLRootNode rootNode, Object[] arguments,
                    PythonBuiltinClassType cls, boolean isIterableCoroutine, ContinuationRootNode continuationRootNode, MaterializedFrame continuationFrame) {
        return new PGenerator(lang, function, continuationFrame, cls, isIterableCoroutine, new BytecodeDSLState(rootNode, arguments, continuationRootNode));
    }

    public static PGenerator create(PythonLanguage lang, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments,
                    PythonBuiltinClassType cls) {
        return create(lang, function, rootNode, callTargets, arguments, cls, false);
    }

    public static PGenerator create(PythonLanguage lang, PFunction function, PBytecodeDSLRootNode rootNode, Object[] arguments,
                    PythonBuiltinClassType cls, ContinuationRootNode continuationRootNode, MaterializedFrame continuationFrame) {
        return create(lang, function, rootNode, arguments, cls, false, continuationRootNode, continuationFrame);
    }

    protected PGenerator(PythonLanguage lang, PFunction function, MaterializedFrame frame, PythonBuiltinClassType cls, boolean isIterableCoroutine, Object state) {
        super(cls, cls.getInstanceShape(lang));
        this.name = function.getName();
        this.qualname = function.getQualname();
        this.globals = function.getGlobals();
        this.generatorFunction = function;
        this.frame = frame;
        this.finished = false;
        this.isCoroutine = isIterableCoroutine || cls == PythonBuiltinClassType.PCoroutine;
        this.isAsyncGen = cls == PythonBuiltinClassType.PAsyncGenerator;
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            BytecodeDSLState bytecodeDSLState = (BytecodeDSLState) state;
            this.state = state;
            this.frameInfo = (BytecodeDSLFrameInfo) bytecodeDSLState.rootNode.getFrameDescriptor().getInfo();
        } else {
            BytecodeState bytecodeState = (BytecodeState) state;
            this.state = state;
            this.frameInfo = (BytecodeFrameInfo) bytecodeState.rootNode.getFrameDescriptor().getInfo();
        }
    }

    public Object[] getCallArguments(Object sendValue) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            prepareResume();
            return getBytecodeDSLState().arguments;
        } else {
            Object[] arguments = PArguments.create(2);
            PArguments.setGlobals(arguments, globals);
            PArguments.setFunctionObject(arguments, generatorFunction);
            PArguments.setArgument(arguments, 0, frame);
            PArguments.setArgument(arguments, 1, sendValue);
            return arguments;
        }
    }

    public void prepareResume() {
        assert PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER; // not needed for manual interpreter
        PArguments.setException(getBytecodeDSLState().arguments, null);
    }

    /**
     * Test whether given traceback element represents a frame used by Bytecode DSL for execution of
     * a generator.
     */
    public static boolean isDSLGeneratorTracebackElement(TruffleStackTraceElement element) {
        // We need to check the source root node type, because this could be another Truffle
        // language that uses Bytecode DSL
        return PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER && isDSLGeneratorTarget(element.getTarget());
    }

    public static boolean isDSLGeneratorTarget(RootCallTarget callTarget) {
        return PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER &&
                        callTarget.getRootNode() instanceof ContinuationRootNode cr &&
                        cr.getSourceRootNode() instanceof PBytecodeDSLRootNode;
    }

    public static Frame unwrapDSLGeneratorFrame(TruffleStackTraceElement element) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (isDSLGeneratorTracebackElement(element)) {
                return (Frame) element.getFrame().getArguments()[0];
            }
        }
        return element.getFrame();
    }

    public static Frame getDSLGeneratorFrame(Object[] continuationCallArguments) {
        return (Frame) continuationCallArguments[0];
    }

    public static RootNode unwrapContinuationRoot(RootNode rootNode) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER &&
                        rootNode instanceof ContinuationRootNode continuationRoot &&
                        continuationRoot.getSourceRootNode() instanceof PBytecodeDSLRootNode result) {
            return result;
        }
        return rootNode;
    }

    public static boolean isGeneratorFrame(Frame frame) {
        Object frameInfo = frame.getFrameDescriptor().getInfo();
        // just to avoid interface dispatch we must cast the info object
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return frameInfo instanceof BytecodeDSLFrameInfo info && info.getCodeUnit().isGeneratorOrCoroutine();
        } else {
            return frameInfo instanceof BytecodeFrameInfo info && info.getCodeUnit().isGeneratorOrCoroutine();
        }
    }

    public static MaterializedFrame getGeneratorFrame(Object[] arguments) {
        return (MaterializedFrame) PArguments.getArgument(arguments, 0);
    }

    public static MaterializedFrame getGeneratorFrame(Frame frame) {
        // For DSL generator frames: we need to unwrap them already before this is used
        // This method should go away with the manual interpreter
        assert isGeneratorFrame(frame);
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return (MaterializedFrame) frame;
        }
        return getGeneratorFrame(frame.getArguments());
    }

    public MaterializedFrame getGeneratorFrame() {
        return frame;
    }

    public static Object getSendValue(Object[] arguments) {
        return PArguments.getArgument(arguments, 1);
    }

    public RootNode getRootNode() {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return getBytecodeDSLState().rootNode;
        } else {
            return getBytecodeState().rootNode;
        }
    }

    /**
     * Returns the call target that should be used the next time the generator is called.
     */
    public RootCallTarget getCurrentCallTarget() {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return getBytecodeDSLState().continuationRootNode.getCallTarget();
        } else {
            return getBytecodeState().getCurrentCallTarget();
        }
    }

    public ContinuationRootNode getCurrentRootNode() {
        assert PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        return getBytecodeDSLState().continuationRootNode;
    }

    public Object getYieldFrom() {
        if (isRunning() || isFinished()) {
            return null;
        }

        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            PBytecodeDSLRootNode rootNode = getBytecodeDSLState().rootNode;
            if (rootNode.yieldFromGeneratorIndex == -1 || !getBytecodeDSLState().isStarted) {
                return null;
            }
            return rootNode.getBytecodeNode().getLocalValue(0, getGeneratorFrame(), rootNode.yieldFromGeneratorIndex);
        } else {
            return frameInfo.getYieldFrom(frame, getBci(), getBytecodeState().getCurrentRootNode().getResumeStackTop());
        }

    }

    public boolean isStarted() {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return getBytecodeDSLState().isStarted && !isRunning();
        } else {
            return getBytecodeState().currentCallTarget != 0 && !isRunning();
        }
    }

    public Object handleResult(PythonLanguage language, Object result) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return getBytecodeDSLState().handleResult((ContinuationResult) result);
        } else {
            return getBytecodeState().handleResult(language, (GeneratorYieldResult) result);
        }
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

    public final PCode getOrCreateCode(Node inliningTarget, InlinedConditionProfile hasCodeProfile) {
        if (hasCodeProfile.profile(inliningTarget, code == null)) {
            RootCallTarget callTarget = getRootNode().getCallTarget();
            code = PFactory.createCode(PythonLanguage.get(inliningTarget), callTarget);
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

    public int getBci() {
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        if (!isStarted()) {
            return -1;
        } else if (isFinished()) {
            return getBytecodeState().rootNode.getCodeUnit().code.length;
        } else {
            return getBytecodeState().getCurrentRootNode().getResumeBci();
        }
    }
}
