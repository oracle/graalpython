/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.compiler.QuickeningTypes;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

public final class PBytecodeGeneratorRootNode extends PRootNode implements BytecodeOSRNode {
    private final PBytecodeRootNode rootNode;
    private final int resumeBci;
    private final int resumeStackTop;

    @Child private ExecutionContext.CalleeContext calleeContext = ExecutionContext.CalleeContext.create();

    @CompilationFinal private Object osrMetadata;
    @CompilationFinal(dimensions = 1) private byte[] frameSlotTypes;

    @TruffleBoundary
    public PBytecodeGeneratorRootNode(PythonLanguage language, PBytecodeRootNode rootNode, int resumeBci, int resumeStackTop) {
        super(language, rootNode.getFrameDescriptor());
        this.rootNode = rootNode;
        rootNode.adoptChildren();
        this.resumeBci = resumeBci;
        this.resumeStackTop = resumeStackTop;
        frameSlotTypes = new byte[resumeStackTop - rootNode.stackoffset + 1];
    }

    @ExplodeLoop
    private void copyStackSlotsIntoVirtualFrame(MaterializedFrame generatorFrame, VirtualFrame virtualFrame) {
        int offset = rootNode.stackoffset;
        for (int i = 0; i < frameSlotTypes.length; i++) {
            int frameIndex = i + offset;
            switch (frameSlotTypes[i]) {
                case QuickeningTypes.OBJECT:
                    if (generatorFrame.isObject(frameIndex)) {
                        virtualFrame.setObject(frameIndex, generatorFrame.getObject(frameIndex));
                        continue;
                    }
                    break;
                case QuickeningTypes.INT:
                    if (generatorFrame.isInt(frameIndex)) {
                        virtualFrame.setInt(frameIndex, generatorFrame.getInt(frameIndex));
                        continue;
                    }
                    break;
                case QuickeningTypes.LONG:
                    if (generatorFrame.isLong(frameIndex)) {
                        virtualFrame.setLong(frameIndex, generatorFrame.getLong(frameIndex));
                        continue;
                    }
                    break;
                case QuickeningTypes.DOUBLE:
                    if (generatorFrame.isDouble(frameIndex)) {
                        virtualFrame.setDouble(frameIndex, generatorFrame.getDouble(frameIndex));
                        continue;
                    }
                    break;
                case QuickeningTypes.BOOLEAN:
                    if (generatorFrame.isBoolean(frameIndex)) {
                        virtualFrame.setBoolean(frameIndex, generatorFrame.getBoolean(frameIndex));
                        continue;
                    }
                    break;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (generatorFrame.isObject(frameIndex)) {
                virtualFrame.setObject(frameIndex, generatorFrame.getObject(frameIndex));
                frameSlotTypes[i] = QuickeningTypes.OBJECT;
            } else if (generatorFrame.isInt(frameIndex)) {
                virtualFrame.setInt(frameIndex, generatorFrame.getInt(frameIndex));
                frameSlotTypes[i] = QuickeningTypes.INT;
            } else if (generatorFrame.isLong(frameIndex)) {
                virtualFrame.setLong(frameIndex, generatorFrame.getLong(frameIndex));
                frameSlotTypes[i] = QuickeningTypes.LONG;
            } else if (generatorFrame.isDouble(frameIndex)) {
                virtualFrame.setDouble(frameIndex, generatorFrame.getDouble(frameIndex));
                frameSlotTypes[i] = QuickeningTypes.DOUBLE;
            } else if (generatorFrame.isBoolean(frameIndex)) {
                virtualFrame.setBoolean(frameIndex, generatorFrame.getBoolean(frameIndex));
                frameSlotTypes[i] = QuickeningTypes.BOOLEAN;
            } else {
                throw new IllegalStateException("unexpected frame slot type");
            }
        }
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterStateObject) {
        OSRInterpreterState interpreterState = (OSRInterpreterState) interpreterStateObject;
        MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(osrFrame);
        return rootNode.executeFromBci(osrFrame, generatorFrame, this, target, interpreterState.stackTop);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        calleeContext.enter(frame);
        MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(frame);
        /*
         * Using the materialized frame as stack would be bad for compiled performance, so we copy
         * the stack slots back to the virtual frame and use that as the stack. The values are
         * copied back in yield node.
         * 
         * TODO we could try to re-virtualize the locals too, but we would need to profile the loads
         * and stores to only copy what is actually used, otherwise copying everything makes things
         * worse.
         */
        copyStackSlotsIntoVirtualFrame(generatorFrame, frame);
        try {
            return rootNode.executeFromBci(frame, generatorFrame, this, resumeBci, resumeStackTop);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    @Override
    public Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
        return rootNode.storeParentFrameInArguments(parentFrame);
    }

    @Override
    public Frame restoreParentFrameFromArguments(Object[] arguments) {
        return rootNode.restoreParentFrameFromArguments(arguments);
    }

    @Override
    public String getName() {
        return rootNode.getName();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<bytecode " + rootNode.getName() + " (generator resume bci=" + resumeBci + ")>";
    }

    @Override
    public boolean setsUpCalleeContext() {
        return true;
    }

    @Override
    public Signature getSignature() {
        return rootNode.getSignature();
    }

    @Override
    public boolean isPythonInternal() {
        return rootNode.isPythonInternal();
    }

    @Override
    public SourceSection getSourceSection() {
        return rootNode.getSourceSection();
    }

    public int getResumeBci() {
        return resumeBci;
    }

    public int getResumeStackTop() {
        return resumeStackTop;
    }

    public PBytecodeRootNode getBytecodeRootNode() {
        return rootNode;
    }
}
