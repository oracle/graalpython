/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
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
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public class PGenerator extends PythonBuiltinObject {

    private TruffleString name;
    private TruffleString qualname;
    private final BytecodeDSLFrameInfo frameInfo;

    private boolean finished;
    // running means it is currently on the stack, not just started
    private boolean running;

    private PCode code;

    private final MaterializedFrame frame;
    private final PythonObject globals;
    private final PFunction generatorFunction;

    public static class BytecodeDSLState {
        private final PBytecodeDSLRootNode rootNode;
        private final Object[] arguments;
        private ContinuationRootNode prevContinuationRootNode;
        private ContinuationRootNode continuationRootNode;
        private boolean isStarted;

        public BytecodeDSLState(PBytecodeDSLRootNode rootNode, Object[] arguments, ContinuationRootNode continuationRootNode) {
            this.rootNode = rootNode;
            this.arguments = arguments;
            this.continuationRootNode = continuationRootNode;
        }

        public Object handleResult(PGenerator generator, ContinuationResult result) {
            assert PBytecodeDSLRootNode.cast(result.getContinuationRootNode()).getFrameDescriptor() == generator.frame.getFrameDescriptor();
            isStarted = true;
            // We must keep the previous root so that we can load its BytecodeNode to resolve BCI to
            // location, the next continuation node may have different BytecodeNode
            prevContinuationRootNode = continuationRootNode;
            continuationRootNode = result.getContinuationRootNode();
            return result.getResult();
        }
    }

    private final BytecodeDSLState state;

    private BytecodeDSLState getBytecodeDSLState() {
        return state;
    }

    public static PGenerator create(PythonLanguage lang, PFunction function, PBytecodeDSLRootNode rootNode, Object[] arguments,
                    PythonBuiltinClassType cls, ContinuationRootNode continuationRootNode, MaterializedFrame continuationFrame) {
        return new PGenerator(lang, function, continuationFrame, cls, new BytecodeDSLState(rootNode, arguments, continuationRootNode));
    }

    protected PGenerator(PythonLanguage lang, PFunction function, MaterializedFrame frame, PythonBuiltinClassType cls, BytecodeDSLState state) {
        super(cls, cls.getInstanceShape(lang));
        this.name = function.getName();
        this.qualname = function.getQualname();
        this.globals = function.getGlobals();
        this.generatorFunction = function;
        this.frame = frame;
        this.finished = false;
        this.state = state;
        this.frameInfo = (BytecodeDSLFrameInfo) state.rootNode.getFrameDescriptor().getInfo();
    }

    public Object[] getCallArguments(Object sendValue) {
        prepareResume();
        return getBytecodeDSLState().arguments;
    }

    public Object[] prepareResume() {
        Object[] arguments = getGeneratorFrame().getArguments();
        PArguments.setException(arguments, null);
        PArguments.setCallerFrameInfo(arguments, null);
        return arguments;
    }

    public ContinuationRootNode getBytecodeDSLContinuationRootNode() {
        return getBytecodeDSLState().continuationRootNode;
    }

    /**
     * Test whether given traceback element represents a frame used by Bytecode DSL for execution of
     * a generator.
     */
    public static boolean isDSLGeneratorTracebackElement(TruffleStackTraceElement element) {
        // We need to check the source root node type, because this could be another Truffle
        // language that uses Bytecode DSL
        return isDSLGeneratorTarget(element.getTarget());
    }

    public static boolean isDSLGeneratorTarget(RootCallTarget callTarget) {
        return callTarget.getRootNode() instanceof ContinuationRootNode cr &&
                        cr.getSourceRootNode() instanceof PBytecodeDSLRootNode;
    }

    public static Frame unwrapDSLGeneratorFrame(TruffleStackTraceElement element) {
        if (isDSLGeneratorTracebackElement(element)) {
            return (Frame) element.getFrame().getArguments()[0];
        }
        return element.getFrame();
    }

    public static RootNode unwrapContinuationRoot(RootNode rootNode) {
        if (rootNode instanceof ContinuationRootNode continuationRoot) {
            return unwrapContinuationRoot(continuationRoot);
        }
        return rootNode;
    }

    public static PBytecodeDSLRootNode unwrapContinuationRoot(ContinuationRootNode continuationRoot) {
        return PBytecodeDSLRootNode.cast(continuationRoot);
    }

    public static boolean isGeneratorFrame(Frame frame) {
        Object frameInfo = frame.getFrameDescriptor().getInfo();
        // just to avoid interface dispatch we must cast the info object
        return frameInfo instanceof BytecodeDSLFrameInfo info && info.getCodeUnit().isGeneratorOrCoroutine() && frame.getArguments()[0] instanceof MaterializedFrame;
    }

    public static MaterializedFrame getGeneratorFrame(Object[] arguments) {
        return (MaterializedFrame) PArguments.getArgument(arguments, 0);
    }

    public static MaterializedFrame getGeneratorFrame(Frame frame) {
        assert isGeneratorFrame(frame);
        return (MaterializedFrame) frame;
    }

    public MaterializedFrame getGeneratorFrame() {
        return frame;
    }

    public PythonObject getGlobals() {
        return globals;
    }

    public PFunction getGeneratorFunction() {
        return generatorFunction;
    }

    public static Object getSendValue(Object[] arguments) {
        return PArguments.getArgument(arguments, 1);
    }

    public RootNode getRootNode() {
        return getBytecodeDSLState().rootNode;
    }

    /**
     * Returns the call target that should be used the next time the generator is called.
     */
    public RootCallTarget getCurrentCallTarget() {
        return getBytecodeDSLState().continuationRootNode.getCallTarget();
    }

    public BytecodeNode getBytecodeNode() {
        assert !running; // When it is running, we must use stack walking to get the location
        BytecodeDSLState state = getBytecodeDSLState();
        if (state.isStarted) {
            return state.prevContinuationRootNode.getLocation().getBytecodeNode();
        } else {
            return state.rootNode.getBytecodeNode();
        }
    }

    /**
     * Return the BytecodeNode that should be used for accessing the frame of a continuation root
     * node.
     */
    public BytecodeNode getContinuationBytecodeNode() {
        BytecodeDSLState state = getBytecodeDSLState();
        assert state.isStarted;
        return state.continuationRootNode.getLocation().getBytecodeNode();
    }

    /**
     * Loads the BytecodeNode from the RootNode field, which is in general not correct, but can be
     * used if we know that the generator function is currently not on stack.
     */
    public BytecodeNode getGeneratorFunctionBytecodeNode() {
        BytecodeDSLState state = getBytecodeDSLState();
        assert !state.isStarted;
        return state.rootNode.getBytecodeNode();
    }

    public BytecodeLocation getCurrentLocation() {
        return getBytecodeDSLState().continuationRootNode.getLocation();
    }

    public Object getYieldFrom() {
        if (isRunning() || isFinished()) {
            return null;
        }

        PBytecodeDSLRootNode rootNode = getBytecodeDSLState().rootNode;
        if (!rootNode.hasYieldFromGenerator() || !getBytecodeDSLState().isStarted) {
            return null;
        }
        return rootNode.readYieldFromGenerator(getContinuationBytecodeNode(), getGeneratorFrame());
    }

    public boolean isStarted() {
        return getBytecodeDSLState().isStarted && !isRunning();
    }

    public Object handleResult(PythonLanguage language, Object result) {
        return getBytecodeDSLState().handleResult(this, (ContinuationResult) result);
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

    private BytecodeDSLCodeUnit getCodeUnit() {
        return getBytecodeDSLState().rootNode.getCodeUnit();
    }

    public final boolean isCoroutine() {
        BytecodeDSLCodeUnit codeUnit = getCodeUnit();
        return codeUnit.isCoroutine() || codeUnit.isIterableCoroutine();
    }

    public final boolean isAsyncGen() {
        return getCodeUnit().isAsyncGenerator();
    }

}
