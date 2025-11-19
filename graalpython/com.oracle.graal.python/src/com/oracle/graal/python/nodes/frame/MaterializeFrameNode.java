/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * This node makes sure that the current frame has a filled-in PFrame object with a backref
 * container that will be filled in by the caller.
 * <p>
 * In the case of Bytecode DSL generators, the caller of this node must ensure that the
 * {@code frameToMaterialize} argument is the generator materialized frame and not the virtual frame
 * of the Bytecode DSL continuation root node, i.e., the caller is responsible, if necessary, to
 * unwrap the continuation materialized frame from such frames.
 * <p>
 * A virtual frame of the Bytecode DSL continuation root node may appear during Truffle stack walk,
 * otherwise the current frame used for execution of GraalPy AST nodes inside a generator is always
 * the materialized generator frame, which should be passed as {@code frameToMaterialize}.
 **/
@ReportPolymorphism
@GenerateUncached
@GenerateInline(false)       // footprint reduction 36 -> 17
@ImportStatic(PGenerator.class)
public abstract class MaterializeFrameNode extends Node {

    @NeverDefault
    public static MaterializeFrameNode create() {
        return MaterializeFrameNodeGen.create();
    }

    public static MaterializeFrameNode getUncached() {
        return MaterializeFrameNodeGen.getUncached();
    }

    /**
     * Like {@link #executeOnStack(boolean, boolean, Frame)}, but the current {@link BytecodeNode}
     * is passed as argument to avoid its lookup. Can be used if this node is uncached.
     */
    public final PFrame executeOnStack(Frame frameToMaterialize, BytecodeNode location, boolean markAsEscaped, boolean forceSync) {
        assert PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        return execute(location, markAsEscaped, forceSync, frameToMaterialize);
    }

    /**
     * Like {@link #executeOnStack(boolean, boolean, Frame)}, but the current root node is passed as
     * argument to avoid its lookup. Can be used if this node is uncached.
     */
    public final PFrame executeOnStack(Frame frameToMaterialize, PRootNode location, boolean markAsEscaped, boolean forceSync) {
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER || !(location instanceof PBytecodeDSLRootNode);
        return execute(location, markAsEscaped, forceSync, frameToMaterialize);
    }

    /**
     * Should be used when we are materializing frame that is currently on top of the stack. For
     * Bytecode DSL, this must not be used as uncached, because we need to be able to get the
     * {@link BytecodeNode} by traversing the AST. For the manual interpreter or builtin and other
     * root nodes, we fetch the root node from the frame descriptor.
     */
    public final PFrame executeOnStack(boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        Node location = this;
        if (!PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (!location.isAdoptable()) {
                // This should only happen in uncached manual bytecode interpreter, we are fine with
                // root node in such case
                location = PArguments.getCurrentFrameInfo(frameToMaterialize).getRootNode();
            }
        } else {
            // We will need EncapsulatingNodeReference or thread the BytecodeNode as argument for
            // BytecodeDSL uncached execution
            assert this.isAdoptable();
        }
        return execute(location, markAsEscaped, forceSync, frameToMaterialize);
    }

    /**
     * @param location Location of the call. For Bytecode DSL this must be the call node, not the
     *            root node from within which the call was made, because the AST could have been
     *            updated (from different thread or an async action) since the call was made.
     *            Specifically, Bytecode DSL updates the root node's {@code BytecodeNode} field,
     *            while another {@code BytecodeNode} is still on stack an executing in its
     *            {@code continueAt} method. We must use the on-stack BytecodeNode to resolve the
     *            BCI that we read from its stack frame. For a frame that is on top of the stack,
     *            this must be some adopted node in the AST that is currently being executed.
     */
    public final PFrame execute(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER || frameToMaterialize.getArguments().length != 2 : "caller forgot to unwrap continuation frame";
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER || !(location instanceof PBytecodeDSLRootNode) : String.format("Materialized frame: location must not be PBytecodeDSLRootNode, was: %s",
                        location);
        return executeImpl(location, markAsEscaped, forceSync, frameToMaterialize);
    }

    abstract PFrame executeImpl(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize);

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!isGeneratorFrame(frameToMaterialize)", "!hasCustomLocals(frameToMaterialize)"})
    static PFrame freshPFrameCachedFD(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Bind PythonLanguage language,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PFrame escapedFrame = PFactory.createPFrame(language, PArguments.getCurrentFrameInfo(frameToMaterialize), location, false);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, location, syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!isGeneratorFrame(frameToMaterialize)", "hasCustomLocals(frameToMaterialize)"})
    static PFrame freshPFrameCustomLocals(Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync,
                    Frame frameToMaterialize,
                    @Bind PythonLanguage language) {
        PFrame escapedFrame = PFactory.createPFrame(language, PArguments.getCurrentFrameInfo(frameToMaterialize), location, true);
        escapedFrame.setLocalsDict(PArguments.getSpecialArgument(frameToMaterialize));
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, false, location, null);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "isGeneratorFrame(frameToMaterialize)"})
    static PFrame freshPFrameForGenerator(Node location, @SuppressWarnings("unused") boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, Frame frameToMaterialize) {
        MaterializedFrame generatorFrame = PGenerator.getGeneratorFrame(frameToMaterialize);
        PFrame.Reference frameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        PFrame escapedFrame = materializeGeneratorFrame(location, generatorFrame, frameRef);
        frameRef.setPyFrame(escapedFrame);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, false, location, null);
    }

    @Specialization(guards = "getPFrame(frameToMaterialize) != null")
    static PFrame alreadyEscapedFrame(@SuppressWarnings("unused") Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PFrame pyFrame = getPFrame(frameToMaterialize);
        pyFrame.setLastCallerFlags(getCallerFlags(forceSync));
        if (forceSync) {
            syncValuesNode.execute(pyFrame, frameToMaterialize, location);
        }
        if (markAsEscaped) {
            pyFrame.getRef().markAsEscaped();
        }
        processBytecodeFrame(frameToMaterialize, pyFrame, location);
        return pyFrame;
    }

    public static PFrame materializeGeneratorFrame(Node location, MaterializedFrame generatorFrame, PFrame.Reference frameRef) {
        PFrame escapedFrame = PFactory.createPFrame(PythonLanguage.get(location), frameRef, location, false);
        escapedFrame.setLocals(generatorFrame);
        escapedFrame.setGlobals(PArguments.getGlobals(generatorFrame));
        return escapedFrame;
    }

    private static void processBytecodeFrame(Frame frameToMaterialize, PFrame pyFrame, Node location) {
        Object info = frameToMaterialize.getFrameDescriptor().getInfo();
        if (info == null) {
            pyFrame.setLocation(location);
            return;
        }
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            BytecodeNode bytecodeNode;
            assert !(location instanceof PBytecodeDSLRootNode); // we need BytecodeNode or its child
            CompilerAsserts.partialEvaluationConstant(location);
            bytecodeNode = BytecodeNode.get(location);
            if (bytecodeNode != null) {
                pyFrame.setBci(bytecodeNode.getBytecodeIndex(frameToMaterialize));
                pyFrame.setLocation(bytecodeNode);
            } else {
                assert location == PythonLanguage.get(null).unavailableSafepointLocation : String.format("%s, root: %s", location, location != null ? location.getRootNode() : "null");
                pyFrame.setBci(-1);
                pyFrame.setLocation(location);
            }
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
        escapedFrame.setGlobals(PArguments.getGlobals(frameToMaterialize));
        escapedFrame.setLastCallerFlags(getCallerFlags(forceSync));
        if (forceSync) {
            syncValuesNode.execute(escapedFrame, frameToMaterialize, location);
        }
        if (markAsEscaped) {
            topFrameRef.markAsEscaped();
        }
        processBytecodeFrame(frameToMaterialize, escapedFrame, location);
        return escapedFrame;
    }

    private static int getCallerFlags(boolean forceSync) {
        return CallerFlags.NEEDS_FRAME_REFERENCE | CallerFlags.NEEDS_PFRAME | CallerFlags.NEEDS_LASTI | (forceSync ? CallerFlags.NEEDS_LOCALS : 0);
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
    @GenerateInline(false) // 25 -> 5
    @GenerateUncached
    @ImportStatic(PGenerator.class)
    public abstract static class SyncFrameValuesNode extends Node {

        public abstract void execute(PFrame pyFrame, Frame frameToSync, Node location);

        @Specialization(guards = {"!pyFrame.hasCustomLocals()", "!isGeneratorFrame(frameToSync)"})
        static void doSync(PFrame pyFrame, Frame frameToSync, Node location,
                        @Bind Node inliningTarget,
                        @Cached(inline = false) ValueProfile frameDescriptorProfile,
                        @Cached InlinedIntValueProfile slotCountProfile,
                        @Cached InlinedBranchProfile createLocalsProfile) {
            FrameDescriptor cachedFd = frameDescriptorProfile.profile(frameToSync.getFrameDescriptor());
            MaterializedFrame target = pyFrame.getLocals();
            if (pyFrame.getLocals() == null) {
                createLocalsProfile.enter(inliningTarget);
                target = Truffle.getRuntime().createMaterializedFrame(EMPTY_OBJECT_ARRAY, cachedFd);
                pyFrame.setLocals(target);
            }
            assert cachedFd == target.getFrameDescriptor();
            int slotCount = slotCountProfile.profile(inliningTarget, variableSlotCount(cachedFd));

            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                CompilerAsserts.partialEvaluationConstant(location);
                BytecodeNode bytecodeNode = BytecodeNode.get(location);
                if (bytecodeNode != null) {
                    bytecodeNode.copyLocalValues(0, frameToSync, target, 0, slotCount);
                }
            } else {
                frameToSync.copyTo(0, target, 0, slotCount);
            }
        }

        @Specialization(guards = "pyFrame.hasCustomLocals()")
        @SuppressWarnings("unused")
        static void doCustomLocals(PFrame pyFrame, Frame frameToSync, Node location) {
            // nothing to do
        }

        @Specialization(guards = "isGeneratorFrame(frameToSync)")
        static void doGenerator(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location) {
            pyFrame.setLocals(PGenerator.getGeneratorFrame(frameToSync));
        }

        @Idempotent
        protected static int variableSlotCount(FrameDescriptor fd) {
            FrameInfo info = (FrameInfo) fd.getInfo();
            if (info == null) {
                return 0;
            }
            return info.getVariableCount();
        }
    }
}
