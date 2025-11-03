/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class ReadCallerFrameNode extends Node {
    public interface FrameSelector {
        boolean skip(RootNode rootNode);
    }

    public static class AllFramesSelector implements FrameSelector {
        public static final AllFramesSelector INSTANCE = new AllFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            return false;
        }
    }

    /**
     * Skips any internal code frames including internal Python level frames of functions annotated
     * with @builtin.
     */
    public static class SkipPythonInternalFramesSelector implements FrameSelector {
        public static final SkipPythonInternalFramesSelector INSTANCE = new SkipPythonInternalFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            return PRootNode.isPythonInternal(rootNode);
        }
    }

    /**
     * Skips any internal frames including Python frames from internal modules in lib-graalpy.
     */
    public static class SkipInternalFramesSelector implements FrameSelector {
        public static final SkipInternalFramesSelector INSTANCE = new SkipInternalFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            return (rootNode != null && rootNode.isInternal()) || PRootNode.isPythonInternal(rootNode);
        }
    }

    /**
     * Skips only builtins frames, not internal Python level frames.
     */
    public static class SkipPythonBuiltinFramesSelector implements FrameSelector {
        public static final SkipPythonBuiltinFramesSelector INSTANCE = new SkipPythonBuiltinFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            return PRootNode.isPythonBuiltin(rootNode);
        }
    }

    @CompilationFinal private ConditionProfile cachedCallerFrameProfile;
    @Child private MaterializeFrameNode materializeNode;

    protected ReadCallerFrameNode() {
    }

    @NeverDefault
    public static ReadCallerFrameNode create() {
        return new ReadCallerFrameNode();
    }

    public PFrame executeWith(VirtualFrame frame, int level) {
        return executeWith(PArguments.getCurrentFrameInfo(frame), SkipPythonInternalFramesSelector.INSTANCE, level);
    }

    public PFrame executeWith(VirtualFrame frame, FrameSelector selector, int level) {
        return executeWith(PArguments.getCurrentFrameInfo(frame), selector, level);
    }

    public PFrame executeWith(Frame startFrame, int level) {
        return executeWith(PArguments.getCurrentFrameInfo(startFrame), SkipPythonInternalFramesSelector.INSTANCE, level);
    }

    public PFrame executeWith(PFrame.Reference startFrameInfo, int level) {
        return executeWith(startFrameInfo, SkipPythonInternalFramesSelector.INSTANCE, level);
    }

    public PFrame executeWith(PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, int level) {
        return executeWith(startFrameInfo, frameAccess, SkipPythonInternalFramesSelector.INSTANCE, level);
    }

    public PFrame executeWith(PFrame.Reference startFrameInfo, FrameSelector selector, int level) {
        return executeWith(startFrameInfo, FrameInstance.FrameAccess.READ_ONLY, selector, level);
    }

    public PFrame executeWith(PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level) {
        PFrame.Reference curFrameInfo = startFrameInfo;
        if (cachedCallerFrameProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cachedCallerFrameProfile = ConditionProfile.create();
            // executed the first time - don't pollute the profile
            return walkLevels(curFrameInfo, frameAccess, selector, level, ConditionProfile.getUncached());
        } else {
            return walkLevels(curFrameInfo, frameAccess, selector, level, cachedCallerFrameProfile);
        }
    }

    private PFrame getMaterializedCallerFrame(PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level) {
        StackWalkResult callerFrameResult = getCallerFrameImpl(this, startFrameInfo, frameAccess, selector, level);
        if (callerFrameResult != null) {
            Node location = callerFrameResult.callNode;
            if (!(callerFrameResult.rootNode instanceof PBytecodeDSLRootNode) && location == null) {
                // We can fixup the location like this only for other root nodes, for Bytecode DSL
                // we need the BytecodeNode
                location = callerFrameResult.rootNode;
            }
            return ensureMaterializeNode().execute(location, false, true, callerFrameResult.frame);
        }
        return null;
    }

    private PFrame walkLevels(PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, ConditionProfile stackWalkProfile) {
        PFrame.Reference currentFrame = startFrameInfo;
        for (int i = 0; i <= level;) {
            PFrame.Reference callerInfo = currentFrame.getCallerInfo();
            if (stackWalkProfile.profile(callerInfo == null || (!selector.skip(callerInfo.getRootNode()) && callerInfo.getPyFrame() == null))) {
                // The chain is broken here, we must continue using slow Truffle stack walk
                return getMaterializedCallerFrame(startFrameInfo, frameAccess, selector, level);
            } else if (!selector.skip(callerInfo.getRootNode())) {
                i++;
            }
            currentFrame = callerInfo;
        }
        return currentFrame.getPyFrame();
    }

    /**
     * Walk up the stack to find the currently top Python frame. This method is mostly useful for
     * code that cannot accept a {@code VirtualFrame} parameter (e.g. library code). It is necessary
     * to provide the requesting node because it might be necessary to locate the last node with
     * {@link BoundaryCallData} that effectively executes the requesting node such that the
     * necessary assumptions can be invalidated to avoid deopt loops.<br/>
     * Consider following situation:<br/>
     *
     * <pre>
     *     public class SomeCaller extends PRootNode {
     *         &#64;Child private InteropLibrary lib = ...;
     *         @Child private IndirectCallData indirectCallData = IndirectCallData.createFor(this);
     *
     *         public Object execute(VirtualFrame frame, Object callee, Object[] args) {
     *             Object state = BoundaryCallContext.enter(frame, ctx, indirectCallData);
     *             try {
     *                 return lib.execute(callee, args);
     *             } finally {
     *                 BoundaryCallContext.exit(frame, ctx, state);
     *             }
     *         }
     *     }
     *
     *     &#64;ExportLibrary(InteropLibrary.class)
     *     public class ExecObject {
     *         &#64;ExportMessage
     *         boolean isExecutable() {
     *             return true;
     *         }
     *
     *         &#64;ExportMessage
     *         Object execute(Object[] args,
     *                            &#64;Cached SomeNode someNode) {
     *             return someNode.execute(args);
     *         }
     *     }
     *
     *     public class SomeNode extends Node {
     *         public Object execute(Object[] args) {
     *             try {
     *                 // do some stuff that might throw a PException
     *             } catch (PException e) {
     *                 // read currently top Python frame because it's required for exception reification
     *                 Frame topPyFrame = ReadCallerFrameNode.getCurrentFrame(this
     *                 // ...
     *             }
     *             return PNone.NONE;
     *         }
     *     }
     * </pre>
     *
     * Assume that we run
     * {@code SomeCaller.create().execute(frame, new ExecObject(), new Object[0])}. It will in the
     * end run {@code SomeNode.execute} and if that tries to get the current frame, we need to do a
     * stack walk in the first run. However, on the second run, node {@code SomeCaller} should
     * already put the current frame reference into the context to avoid subsequent stack walks.
     * Since there is no Truffle call happening, this can only be achieved if we walk the node's
     * parent chain.
     *
     * @param requestingNode - the top most "location" node
     * @param frameAccess - the desired {@link FrameInstance} access kind
     */
    public static Frame getCurrentFrame(Node requestingNode, FrameInstance.FrameAccess frameAccess) {
        StackWalkResult result = getFrame(Objects.requireNonNull(requestingNode), null, frameAccess, AllFramesSelector.INSTANCE, 0);
        if (result != null) {
            return result.frame;
        }
        return null;
    }

    // For getting just the Truffle frame, we do not need current location
    public static Frame getCallerFrame(PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        StackWalkResult result = getFrame(null, Objects.requireNonNull(startFrame), frameAccess, selector, level);
        return result != null ? result.frame : null;
    }

    private static StackWalkResult getCallerFrameImpl(Node location, PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return getFrame(location, Objects.requireNonNull(startFrame), frameAccess, selector, level);
    }

    /**
     * @param callNode The call node if called from code with Python frame, or a last node set as
     *            {@link com.oracle.truffle.api.nodes.EncapsulatingNodeReference} before an indirect
     *            call was made. Should be an adopted AST node connected to the
     *            {@link com.oracle.truffle.api.bytecode.BytecodeNode}, in case of Bytecode DSL, and
     *            to a root node in any case.
     */
    public record StackWalkResult(PRootNode rootNode, Node callNode, Frame frame) {
    }

    /**
     * Walk up the stack to find the {@code startFrame} and from then ({@code
     * level} + 1)-times (counting only Python frames according to the {@code selector}). If
     * {@code startFrame} is {@code null}, return the currently top Python frame.
     *
     * @param requestingNode - the node that requests the stack walk. Truffle does not give us the
     *            "call node" for the top most frame, so the caller must provide it explicitly. If
     *            the location is not passed, then the result may not contain the
     *            {@link StackWalkResult#callNode()}, which is needed by, e.g.,
     *            {@link MaterializeFrameNode}.
     * @param startFrame - the frame to start counting from, if {@code null}, then return the first
     *            Python frame.
     * @param frameAccess - the desired {@link FrameInstance} access kind
     * @param selector - declares which frames should be skipped or counted
     * @param level - the stack depth to go to. Ignored if {@code startFrame} is {@code null}
     */
    @TruffleBoundary
    public static StackWalkResult getFrame(Node requestingNode, PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level) {
        PythonContext.setWasStackWalk();
        return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
            int i = startFrame != null ? -1 : 0;
            boolean first = true;

            public StackWalkResult visitFrame(FrameInstance frameInstance) {
                RootNode rootNode = ReadCallerFrameNode.getRootNode(frameInstance);
                Node callNode = frameInstance.getCallNode();
                if (callNode == null && first) {
                    // This should happen only for the top most frame, i.e., the frame that is being
                    // executed now. Otherwise, this is a bug - we need the call node for Bytecode
                    // DSL. In the manual interpreter, the root node is enough.
                    callNode = requestingNode;
                }
                // We must have a callNode for Bytecode DSL root nodes. If this assertion fires, it
                // can be because of missing BoundaryCallContext.enter/exit around @TruffleBoundary
                // calls that may call back into Python code. Look at the Java stack trace and check
                // if all @TruffleBoundary methods are preceded by BoundaryCallContext.enter/exit
                assert first || !(PGenerator.unwrapContinuationRoot(rootNode) instanceof PBytecodeDSLRootNode) || !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER ||
                                callNode != null : rootNode;
                first = false;
                if (!(rootNode instanceof PRootNode pRootNode && pRootNode.setsUpCalleeContext())) {
                    // Note: any non-Python Truffle frames should have been preceded by
                    // BoundaryCallContext.enter/exit, and the PFrame.References will be chained
                    // through thread state. We will eventually arrive at the Python frame that did
                    // BoundaryCallContext.enter, find the IndirectCallData via the callNode and
                    // tell it to pass the PFrame.Reference in thread state next time
                    return null;
                }
                IndirectCallData.setEncapsulatingNeedsToPassCallerFrame(callNode);
                StackWalkResult result = null;
                if (i < 0 && startFrame != null) {
                    // We are still looking for the start frame
                    Frame roFrame = ReadCallerFrameNode.getFrame(frameInstance, FrameInstance.FrameAccess.READ_ONLY);
                    if (PArguments.getCurrentFrameInfo(roFrame) == startFrame) {
                        i = 0;
                    }
                } else if (i >= 0) {
                    // We found the start frame already, or we are supposed to return the start
                    // frame (startFrame argument was null)
                    if (!selector.skip(pRootNode)) {
                        if (i == level) {
                            Frame frame = ReadCallerFrameNode.getFrame(frameInstance, frameAccess);
                            assert PArguments.isPythonFrame(frame);
                            return new StackWalkResult(pRootNode, callNode, frame);
                        }
                        i += 1;
                    }
                }
                // For any Python root node we traverse we need the PFrame.Reference to be passed in
                // call arguments next time. Builtins don't materialize PFrame, because it
                // should not be visible to Python code anyway, but still pass the
                // PFrame.Reference to connect the linked list of references. If we are at the frame
                // that we need, we still need caller frame info if our frame is escaped, see
                // CalleeContext#exitEscaped
                pRootNode.setNeedsCallerFrame();
                return null; // if 'null' continue iterating
            }
        });
    }

    private static RootNode getRootNode(FrameInstance frameInstance) {
        RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
        return PGenerator.unwrapContinuationRoot(target.getRootNode());
    }

    private static Frame getFrame(FrameInstance frameInstance, FrameInstance.FrameAccess frameAccess) {
        Frame frame = frameInstance.getFrame(frameAccess);

        RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
        if (target.getRootNode() instanceof ContinuationRootNode) {
            return (Frame) frame.getArguments()[0];
        }
        return frame;
    }

    private MaterializeFrameNode ensureMaterializeNode() {
        if (materializeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            materializeNode = insert(MaterializeFrameNodeGen.create());
        }
        return materializeNode;
    }
}
