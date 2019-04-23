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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * RootNode of a Python Function body. It is invoked by a CallTarget.
 */
public class FunctionRootNode extends PClosureFunctionRootNode {
    private final ContextReference<PythonContext> contextRef;
    private final PCell[] cells;
    private final ExecutionCellSlots executionCellSlots;
    private final String functionName;
    private final SourceSection sourceSection;
    private final boolean isGenerator;
    private final ValueProfile generatorFrameProfile;
    private boolean isRewritten = false;

    @Child private ExpressionNode body;
    private ExpressionNode uninitializedBody;

    public FunctionRootNode(PythonLanguage language, SourceSection sourceSection, String functionName, boolean isGenerator, FrameDescriptor frameDescriptor, ExpressionNode body,
                    ExecutionCellSlots executionCellSlots, Signature signature) {
        super(language, frameDescriptor, executionCellSlots, signature);
        this.contextRef = language.getContextReference();
        this.executionCellSlots = executionCellSlots;
        this.cells = new PCell[this.cellVarSlots.length];

        this.sourceSection = sourceSection;
        assert sourceSection != null;
        this.functionName = functionName;
        this.isGenerator = isGenerator;
        this.body = new InnerRootNode(this, NodeUtil.cloneNode(body));
        this.uninitializedBody = NodeUtil.cloneNode(body);
        this.generatorFrameProfile = isGenerator ? ValueProfile.createClassProfile() : null;
    }

    public FunctionRootNode copyWithNewSignature(Signature newSignature) {
        return new FunctionRootNode(PythonLanguage.getCurrent(), getSourceSection(), functionName, isGenerator, getFrameDescriptor(), uninitializedBody, executionCellSlots, newSignature);
    }

    @Override
    public String getName() {
        return functionName;
    }

    public PCell[] getCells() {
        return cells;
    }

    public FrameSlot[] getCellVarSlots() {
        return cellVarSlots;
    }

    @Override
    public FunctionRootNode copy() {
        FunctionRootNode copy = new FunctionRootNode(PythonLanguage.getCurrent(), getSourceSection(), functionName, isGenerator, getFrameDescriptor(), uninitializedBody, executionCellSlots,
                        getSignature());
        // share assumptions; this is required due to splitting, otherwise the invoking nodes (i.e.
        // InvokeNode) is testing for the wrong assumptions since it will see a different instance
        // as actually executed.
        copy.setDontNeedCallerFrame(getDontNeedCallerFrame());
        copy.setDontNeedExceptionState(getDontNeedExceptionState());
        return copy;
    }

    @ExplodeLoop
    private void initializeCellVars(Frame frame) {
        for (int i = 0; i < cellVarSlots.length; i++) {
            FrameSlot frameSlot = cellVarSlots[i];

            // get the cell
            PCell cell = null;
            if (isGenerator) {
                cell = (PCell) FrameUtil.getObjectSafe(frame, frameSlot);
            }
            if (cell == null) {
                cell = new PCell(cellEffectivelyFinalAssumptions[i]);
            }

            // store the cell as a local var
            frame.setObject(frameSlot, cell);
            this.cells[i] = cell;
        }
    }

    private void initClosureAndCellVars(VirtualFrame frame) {
        Frame accessingFrame = frame;
        if (isGenerator) {
            accessingFrame = generatorFrameProfile.profile(PArguments.getGeneratorFrame(frame));
        }

        addClosureCellsToLocals(accessingFrame);
        initializeCellVars(accessingFrame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter() || CompilerDirectives.inCompilationRoot()) {
            contextRef.get().triggerAsyncActions();
        }
        try {
            return body.execute(frame);
        } finally {
            // if we leave with an exception, the Truffle code will lazily
            // attach the frames, so we only need to worry about if this frame
            // escaped some other way
            PFrame escapedFrame = PArguments.getPFrame(frame);

            // Kind-of equivalent to PyPy's "executioncontext enter" because
            // this is where we give the child it's backref
            // TODO: frames: this handling should move into the places where we
            // call, so we get accurate location information of the callsite.
            // once all those sites are covered, the entire branch below can go
            // away
            PFrame[] backrefFromChild = contextRef.get().getEscapedTopFrameRef();
            if (backrefFromChild != null) {
                // I was marked as escaped by a call in my body.

                if (backrefFromChild[0] == null) {
                    // whatever call site requested that we escape, we didn't
                    // fill in our information at that point, so we do it now

                    if (escapedFrame == null) {
                        escapedFrame = PythonObjectFactory.getUncached().createPFrame(frame.materialize());
                    } else if (!escapedFrame.hasFrame()) {
                        // The only way this happens is when we created a PFrame
                        // for custom locals or a PFrame from the C API with
                        // custom thread state and without a frame. In the first
                        // case, there's no reference to the PFrame object
                        // anywhere else, yet. In the second case, the custom
                        // created frame must have escaped somewhere else.
                        // TODO: frames: Think about how to ensure this
                        escapedFrame = PythonObjectFactory.getUncached().createPFrame(frame.materialize(), escapedFrame.getLocalsDict());
                    }
                    PArguments.setPFrame(frame, escapedFrame);

                    backrefFromChild[0] = escapedFrame;
                }

                // Clear the backref container, so this frame doesn't escape if
                // the other referent(s) to the container (any callee frames
                // that were called from this root) do not escape
                contextRef.get().clearEscapedTopFrameRef();
            }

            // Kind-of equivalent to PyPy's "executioncontext leave" because
            // this is where we inform the calling Python frame that it is
            // escaped (by setting the backref container)
            if (escapedFrame != null && escapedFrame.hasFrame()) {
                if (escapedFrame.getBackref() == null) {
                    PFrame[] backref = contextRef.get().markEscapedTopFrameRef();
                    escapedFrame.setBackref(backref);
                }
            }
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<function root " + functionName + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public boolean isRewritten() {
        return isRewritten;
    }

    public void setRewritten() {
        this.isRewritten = true;
    }

    @Override
    public void initializeFrame(VirtualFrame frame) {
        initClosureAndCellVars(frame);
    }
}
