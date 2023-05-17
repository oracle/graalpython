/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * This node makes sure that the current frame has a filled-in PFrame object with a backref
 * container that will be filled in by the caller.
 **/
@ReportPolymorphism
@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
public abstract class MaterializeFrameNode extends Node {

    @NeverDefault
    public static MaterializeFrameNode create() {
        return MaterializeFrameNodeGen.create();
    }

    public static MaterializeFrameNode getUncached() {
        return MaterializeFrameNodeGen.getUncached();
    }

    public final PFrame execute(boolean markAsEscaped, Frame frameToMaterialize) {
        return execute(markAsEscaped, false, frameToMaterialize);
    }

    public final PFrame execute(boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        PFrame.Reference info = PArguments.getCurrentFrameInfo(frameToMaterialize);
        assert info != null && info.getCallNode() != null : "cannot materialize a frame without location information";
        Node callNode = info.getCallNode();
        return execute(callNode, markAsEscaped, forceSync, frameToMaterialize);
    }

    public final PFrame execute(Frame frame, boolean markAsEscaped) {
        return execute(markAsEscaped, frame);
    }

    public final PFrame execute(Frame frame, Node location, boolean markAsEscaped, boolean forceSync) {
        return execute(location, markAsEscaped, forceSync, frame);
    }

    public abstract PFrame execute(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize);

    @Specialization(guards = {
                    "cachedFD == frameToMaterialize.getFrameDescriptor()", //
                    "getPFrame(frameToMaterialize) == null", //
                    "!hasGeneratorFrame(frameToMaterialize)", //
                    "!hasCustomLocals(frameToMaterialize)"}, limit = "1")
    static PFrame freshPFrameCachedFD(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Cached(value = "frameToMaterialize.getFrameDescriptor()") FrameDescriptor cachedFD,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        MaterializedFrame locals = createLocalsFrame(cachedFD);
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, location, syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!hasGeneratorFrame(frameToMaterialize)", "!hasCustomLocals(frameToMaterialize)"}, replaces = "freshPFrameCachedFD")
    static PFrame freshPFrame(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        MaterializedFrame locals = createLocalsFrame(frameToMaterialize.getFrameDescriptor());
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, location, syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!hasGeneratorFrame(frameToMaterialize)", "hasCustomLocals(frameToMaterialize)"})
    static PFrame freshPFrameCusstomLocals(Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync,
                    Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, null);
        escapedFrame.setLocalsDict(PArguments.getSpecialArgument(frameToMaterialize));
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, false, location, null);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "hasGeneratorFrame(frameToMaterialize)"})
    static PFrame freshPFrameForGenerator(Node location, @SuppressWarnings("unused") boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(frameToMaterialize);
        PFrame.Reference frameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        PFrame escapedFrame = materializeGeneratorFrame(location, generatorFrame, frameRef, factory);
        frameRef.setPyFrame(escapedFrame);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, false, location, null);
    }

    @Specialization(guards = "getPFrame(frameToMaterialize) != null")
    static PFrame alreadyEscapedFrame(@SuppressWarnings("unused") Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Bind("this") Node inliningTarget,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode,
                    @Cached InlinedConditionProfile syncProfile) {
        PFrame pyFrame = getPFrame(frameToMaterialize);
        if (syncProfile.profile(inliningTarget, forceSync && !hasGeneratorFrame(frameToMaterialize))) {
            syncValuesNode.execute(pyFrame, frameToMaterialize);
        }
        if (markAsEscaped) {
            pyFrame.getRef().markAsEscaped();
        }
        processBytecodeFrame(frameToMaterialize, pyFrame, location);
        return pyFrame;
    }

    private static MaterializedFrame createLocalsFrame(FrameDescriptor cachedFD) {
        return Truffle.getRuntime().createMaterializedFrame(PythonUtils.EMPTY_OBJECT_ARRAY, cachedFD);
    }

    public static PFrame materializeGeneratorFrame(Node location, MaterializedFrame generatorFrame, PFrame.Reference frameRef, PythonObjectFactory factory) {
        PFrame escapedFrame = factory.createPFrame(frameRef, location, generatorFrame);
        PArguments.synchronizeArgs(generatorFrame, escapedFrame);
        return escapedFrame;
    }

    private static void processBytecodeFrame(Frame frameToMaterialize, PFrame pyFrame, Node location) {
        Object info = frameToMaterialize.getFrameDescriptor().getInfo();
        if (info == null) {
            return;
        }
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            BytecodeDSLFrameInfo bytecodeDSLFrameInfo = (BytecodeDSLFrameInfo) info;
            PBytecodeDSLRootNode rootNode = bytecodeDSLFrameInfo.getRootNode();
            BytecodeNode bytecodeNode = BytecodeNode.get(location);
            pyFrame.setBci(rootNode.readBciFromFrame(frameToMaterialize));
            pyFrame.setLocation(bytecodeNode);
        } else {
            BytecodeFrameInfo bytecodeFrameInfo = (BytecodeFrameInfo) info;
            pyFrame.setBci(bytecodeFrameInfo.getBci(frameToMaterialize));
            pyFrame.setLocation(bytecodeFrameInfo.getRootNode());
        }
    }

    private static PFrame doEscapeFrame(Frame frameToMaterialize, PFrame escapedFrame, boolean markAsEscaped, boolean forceSync,
                    Node location, SyncFrameValuesNode syncValuesNode) {
        PFrame.Reference topFrameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        topFrameRef.setPyFrame(escapedFrame);

        // on a freshly created PFrame, we do always sync the arguments
        PArguments.synchronizeArgs(frameToMaterialize, escapedFrame);
        if (forceSync) {
            syncValuesNode.execute(escapedFrame, frameToMaterialize);
        }
        if (markAsEscaped) {
            topFrameRef.markAsEscaped();
        }
        processBytecodeFrame(frameToMaterialize, escapedFrame, location);
        return escapedFrame;
    }

    protected static boolean hasGeneratorFrame(Frame frame) {
        return PArguments.getGeneratorFrame(frame) != null;
    }

    protected static boolean hasCustomLocals(Frame frame) {
        return PArguments.getSpecialArgument(frame) != null;
    }

    protected static PFrame getPFrame(Frame frame) {
        return PArguments.getCurrentFrameInfo(frame).getPyFrame();
    }

    /**
     * We copy locals from Truffle frame to PFrame in the form of a materialized frame. That frame
     * can later be copied to a dict by {@link GetFrameLocalsNode} when needed. This split ensures
     * that we can materialize frames without causing immediate side effects to the locals dict
     * which may have already escaped to python.
     */
    @GenerateUncached
    public abstract static class SyncFrameValuesNode extends Node {

        public abstract void execute(PFrame pyFrame, Frame frameToSync);

        @Specialization(guards = {"!pyFrame.hasCustomLocals()", "frameToSync.getFrameDescriptor() == cachedFd",
                        "variableSlotCount(cachedFd) < 32"}, limit = "1")
        @ExplodeLoop
        static void doSyncExploded(PFrame pyFrame, Frame frameToSync,
                        @Cached(value = "frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd) {
            MaterializedFrame target = pyFrame.getLocals();
            assert cachedFd == target.getFrameDescriptor();
            doCopy(cachedFd, frameToSync, target);
        }

        @Specialization(guards = "!pyFrame.hasCustomLocals()", replaces = "doSyncExploded")
        static void doSync(PFrame pyFrame, Frame frameToSync) {
            MaterializedFrame target = pyFrame.getLocals();
            doCopy(frameToSync.getFrameDescriptor(), frameToSync, target);
        }

        @Specialization(guards = "pyFrame.hasCustomLocals()")
        @SuppressWarnings("unused")
        static void doCustomLocals(PFrame pyFrame, Frame frameToSync) {
            // nothing to do
        }

        @Idempotent
        protected static int variableSlotCount(FrameDescriptor fd) {
            FrameInfo info = (FrameInfo) fd.getInfo();
            if (info == null) {
                return 0;
            }
            return info.getVariableCount();
        }

        private static void doCopy(FrameDescriptor fd, Frame source, MaterializedFrame destination) {
            FrameInfo info = (FrameInfo) fd.getInfo();
            if (info == null) {
                return;
            }
            int count = info.getVariableCount();

            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                ((BytecodeDSLFrameInfo) info).getRootNode().copyLocals(source, destination, count);
            } else {
                for (int i = 0; i < count; i++) {
                    PythonUtils.copyFrameSlot(source, destination, i);
                }
            }
        }
    }
}
