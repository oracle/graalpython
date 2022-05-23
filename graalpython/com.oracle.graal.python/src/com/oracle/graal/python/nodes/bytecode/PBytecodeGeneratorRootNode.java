/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

public class PBytecodeGeneratorRootNode extends PRootNode implements BytecodeOSRNode {
    private final PBytecodeRootNode rootNode;
    private final int resumeBci;
    private final int resumeStackTop;

    @Child private ExecutionContext.CalleeContext calleeContext = ExecutionContext.CalleeContext.create();
    @Child private IsBuiltinClassProfile errorProfile;
    @Child private PRaiseNode raise = PRaiseNode.create();
    private final ConditionProfile returnProfile = ConditionProfile.create();

    @CompilationFinal private Object osrMetadata;
    @CompilationFinal(dimensions = 1) private FrameSlotType[] frameSlotTypes;

    private enum FrameSlotType {
        Object,
        Int,
        Long,
        Double,
        Boolean
    }

    @TruffleBoundary
    public PBytecodeGeneratorRootNode(PythonLanguage language, PBytecodeRootNode rootNode, int resumeBci, int resumeStackTop) {
        super(language, rootNode.getFrameDescriptor());
        this.rootNode = rootNode;
        this.resumeBci = resumeBci;
        this.resumeStackTop = resumeStackTop;
        frameSlotTypes = new FrameSlotType[resumeStackTop + 1];
    }

    @ExplodeLoop
    private void copyFrameSlotsIntoVirtualFrame(MaterializedFrame generatorFrame, VirtualFrame virtualFrame) {
        for (int i = 0; i < frameSlotTypes.length; i++) {
            switch (frameSlotTypes[i]) {
                case Object:
                    if (generatorFrame.isObject(i)) {
                        virtualFrame.setObject(i, generatorFrame.getObject(i));
                        continue;
                    }
                    break;
                case Int:
                    if (generatorFrame.isInt(i)) {
                        virtualFrame.setInt(i, generatorFrame.getInt(i));
                        continue;
                    }
                    break;
                case Long:
                    if (generatorFrame.isLong(i)) {
                        virtualFrame.setLong(i, generatorFrame.getLong(i));
                        continue;
                    }
                    break;
                case Double:
                    if (generatorFrame.isDouble(i)) {
                        virtualFrame.setDouble(i, generatorFrame.getDouble(i));
                        continue;
                    }
                    break;
                case Boolean:
                    if (generatorFrame.isBoolean(i)) {
                        virtualFrame.setBoolean(i, generatorFrame.getBoolean(i));
                        continue;
                    }
                    break;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (generatorFrame.isObject(i)) {
                virtualFrame.setObject(i, generatorFrame.getObject(i));
                frameSlotTypes[i] = FrameSlotType.Object;
            } else if (generatorFrame.isInt(i)) {
                virtualFrame.setInt(i, generatorFrame.getInt(i));
                frameSlotTypes[i] = FrameSlotType.Int;
            } else if (generatorFrame.isLong(i)) {
                virtualFrame.setLong(i, generatorFrame.getLong(i));
                frameSlotTypes[i] = FrameSlotType.Long;
            } else if (generatorFrame.isDouble(i)) {
                virtualFrame.setDouble(i, generatorFrame.getDouble(i));
                frameSlotTypes[i] = FrameSlotType.Double;
            } else if (generatorFrame.isBoolean(i)) {
                virtualFrame.setBoolean(i, generatorFrame.getBoolean(i));
                frameSlotTypes[i] = FrameSlotType.Boolean;
            } else {
                throw new IllegalStateException("unexpected frame slot type");
            }
        }
    }

    @ExplodeLoop
    private void copyFrameSlotsToGeneratorFrame(VirtualFrame virtualFrame, MaterializedFrame generatorFrame) {
        int stackTop = getFrameDescriptor().getNumberOfSlots();
        CompilerAsserts.partialEvaluationConstant(stackTop);
        for (int i = 0; i < stackTop; i++) {
            if (virtualFrame.isObject(i)) {
                generatorFrame.setObject(i, virtualFrame.getObject(i));
            } else if (virtualFrame.isInt(i)) {
                generatorFrame.setInt(i, virtualFrame.getInt(i));
            } else if (virtualFrame.isLong(i)) {
                generatorFrame.setLong(i, virtualFrame.getLong(i));
            } else if (virtualFrame.isDouble(i)) {
                generatorFrame.setDouble(i, virtualFrame.getDouble(i));
            } else if (virtualFrame.isBoolean(i)) {
                generatorFrame.setBoolean(i, virtualFrame.getBoolean(i));
            } else {
                throw CompilerDirectives.shouldNotReachHere("unexpected frame slot type");
            }
        }
    }

    private void profileFrameSlots(MaterializedFrame generatorFrame) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < frameSlotTypes.length; i++) {
            if (generatorFrame.isObject(i)) {
                frameSlotTypes[i] = FrameSlotType.Object;
            } else if (generatorFrame.isInt(i)) {
                frameSlotTypes[i] = FrameSlotType.Int;
            } else if (generatorFrame.isLong(i)) {
                frameSlotTypes[i] = FrameSlotType.Long;
            } else if (generatorFrame.isDouble(i)) {
                frameSlotTypes[i] = FrameSlotType.Double;
            } else if (generatorFrame.isBoolean(i)) {
                frameSlotTypes[i] = FrameSlotType.Boolean;
            } else {
                throw new IllegalStateException("unexpected frame slot type");
            }
        }
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        Integer osrStackTop = (Integer) interpreterState;
        MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(osrFrame);
        copyFrameSlotsIntoVirtualFrame(generatorFrame, osrFrame);
        copyOSRStackRemainderIntoVirtualFrame(generatorFrame, osrFrame, osrStackTop);
        try {
            return rootNode.executeFromBci(osrFrame, osrFrame, this, target, osrStackTop);
        } finally {
            copyFrameSlotsToGeneratorFrame(osrFrame, generatorFrame);
        }
    }

    @ExplodeLoop
    private void copyOSRStackRemainderIntoVirtualFrame(MaterializedFrame generatorFrame, VirtualFrame osrFrame, int stackTop) {
        /*
         * In addition to local variables and stack slots present at resume, OSR needs to also
         * revirtualize stack items that have been pushed since resume. Stack slots at a back edge
         * should never be primitives.
         */
        for (int i = resumeStackTop; i <= stackTop; i++) {
            osrFrame.setObject(i, generatorFrame.getObject(i));
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        calleeContext.enter(frame);
        MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(frame);
        /*
         * This copying of exceptions is necessary because we need to remember the exception state
         * in the generator, but we don't want to remember the state that is "inherited" from the
         * outer frame as that can change with each invocation.
         */
        PException localException = PArguments.getException(generatorFrame);
        PException outerException = PArguments.getException(frame);
        PArguments.setException(frame, localException == null ? outerException : localException);
        Object result;
        Frame localFrame;
        boolean usingMaterializedFrame = CompilerDirectives.inInterpreter();
        if (usingMaterializedFrame) {
            profileFrameSlots(generatorFrame);
            localFrame = generatorFrame;
        } else {
            copyFrameSlotsIntoVirtualFrame(generatorFrame, frame);
            localFrame = frame;
        }
        try {
            result = rootNode.executeFromBci(frame, localFrame, this, resumeBci, resumeStackTop);
        } catch (PException pe) {
            // PEP 479 - StopIteration raised from generator body needs to be wrapped in
            // RuntimeError
            pe.expectStopIteration(getErrorProfile());
            throw raise.raise(RuntimeError, pe.setCatchingFrameAndGetEscapedException(frame, this), ErrorMessages.GENERATOR_RAISED_STOPITER);
        } finally {
            if (!usingMaterializedFrame) {
                copyFrameSlotsToGeneratorFrame(frame, generatorFrame);
            }
            calleeContext.exit(frame, this);
            PException exception = PArguments.getException(frame);
            if (exception != outerException && exception != PException.NO_EXCEPTION) {
                PArguments.setException(generatorFrame, exception);
            }
        }
        if (returnProfile.profile(result == null)) {
            // Null result indicates a generator return
            FrameInfo info = (FrameInfo) generatorFrame.getFrameDescriptor().getInfo();
            Object returnValue = info.getGeneratorReturnValue(generatorFrame);
            if (returnValue != PNone.NONE) {
                throw raise.raise(StopIteration, returnValue);
            } else {
                throw raise.raise(StopIteration);
            }
        }
        return result;
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

    private IsBuiltinClassProfile getErrorProfile() {
        if (errorProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            errorProfile = insert(IsBuiltinClassProfile.create());
        }
        return errorProfile;
    }
}
