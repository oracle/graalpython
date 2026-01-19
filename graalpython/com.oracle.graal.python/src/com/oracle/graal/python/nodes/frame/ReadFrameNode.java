/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateUncached
@GenerateInline(false)
public abstract class ReadFrameNode extends Node {
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
     * Selects only Python frames. They may still include internal frames.
     */
    public static class AllPythonFramesSelector implements FrameSelector {
        public static final AllPythonFramesSelector INSTANCE = new AllPythonFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                return PBytecodeDSLRootNode.cast(rootNode) == null;
            } else {
                return !(rootNode instanceof PBytecodeRootNode || rootNode instanceof PBytecodeGeneratorRootNode);
            }
        }
    }

    /**
     * Selects only python frames that should be visible, i.e. excludes internal sources from
     * lib-graalpython or importlib. Used primarily for tracebacks.
     */
    public static class VisiblePythonFramesSelector extends AllPythonFramesSelector {
        public static final VisiblePythonFramesSelector INSTANCE = new VisiblePythonFramesSelector();

        @Override
        public boolean skip(RootNode rootNode) {
            return super.skip(rootNode) || rootNode.isInternal() || PRootNode.isPythonInternal(rootNode);
        }
    }

    protected ReadFrameNode() {
    }

    @NeverDefault
    public static ReadFrameNode create() {
        return ReadFrameNodeGen.create();
    }

    /**
     * Get the current python-level frame (skips builtin function roots, but not internal python
     * frames)
     */
    public final PFrame getCurrentPythonFrame(VirtualFrame frame) {
        return getCurrentPythonFrame(frame, 0);
    }

    public final PFrame getCurrentPythonFrame(VirtualFrame frame, int callerFlags) {
        return getFrameForReference(frame, frame != null ? PArguments.getCurrentFrameInfo(frame) : null, AllPythonFramesSelector.INSTANCE, 0, callerFlags);
    }

    public final PFrame refreshFrame(VirtualFrame frame, PFrame.Reference reference, int callerFlags) {
        return getFrameForReference(frame, reference, 0, callerFlags);
    }

    public final PFrame ensureFresh(VirtualFrame frame, PFrame pFrame, int callerFlags) {
        if (pFrame.needsRefresh(frame, callerFlags)) {
            return refreshFrame(frame, pFrame.getRef(), callerFlags);
        }
        return pFrame;
    }

    public final PFrame getFrameForReference(Frame frame, PFrame.Reference startFrameInfo, int level, int callerFlags) {
        return getFrameForReference(frame, startFrameInfo, AllPythonFramesSelector.INSTANCE, level, callerFlags);
    }

    public final PFrame getFrameForReference(Frame frame, PFrame.Reference startFrameInfo, FrameSelector selector, int level, int callerFlags) {
        return execute(frame, startFrameInfo, FrameInstance.FrameAccess.READ_ONLY, selector, level, callerFlags | CallerFlags.NEEDS_PFRAME);
    }

    protected abstract PFrame execute(Frame frame, PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, int callerFlags);

    @Specialization
    PFrame read(VirtualFrame frame, PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, int callerFlags,
                    @Bind Node inliningTarget,
                    @Cached MaterializeFrameNode materializeFrameNode,
                    @Cached InlinedBranchProfile stackWalkProfile1,
                    @Cached InlinedBranchProfile stackWalkProfile2) {
        PFrame.Reference executingFrameInfo = frame != null ? PArguments.getCurrentFrameInfo(frame) : null;
        if (startFrameInfo == null) {
            PythonContext context = PythonContext.get(this);
            startFrameInfo = context.peekTopFrameInfo(context.getLanguage(this));
            executingFrameInfo = startFrameInfo;
        }
        int i = 0;
        PFrame.Reference curFrameInfo = startFrameInfo;
        while (curFrameInfo != null) {
            if (curFrameInfo == PFrame.Reference.EMPTY) {
                // We reached the top of the stack
                return null;
            }
            PFrame.Reference backref = curFrameInfo.getCallerInfo();
            if (!selector.skip(curFrameInfo.getRootNode())) {
                if (i == level) {
                    // We found the right reference
                    // Maybe it's for the frame we're in?
                    if (curFrameInfo == executingFrameInfo) {
                        return materializeFrameNode.execute(this, false, CallerFlags.needsLocals(callerFlags), frame);
                    }
                    if (curFrameInfo.getPyFrame() != null && !curFrameInfo.getPyFrame().needsRefresh(null, callerFlags)) {
                        return curFrameInfo.getPyFrame();
                    }
                    // We don't have the frame for the reference, fall back to the stack walk
                    break;
                }
                // Nowhere to continue, break now so that the stackwalk can continue from the
                // correct index
                if (backref == null) {
                    break;
                }
                i++;
            }
            if (backref == null) {
                break;
            }
            curFrameInfo = backref;
        }
        /*
         * The chain is broken here, we must continue using slow Truffle stack walk. Profile twice
         * so that doing the stack walk the first time doesn't affect the compiled code.
         */
        if (stackWalkProfile1.wasEntered(inliningTarget)) {
            stackWalkProfile2.enter(inliningTarget);
        }
        stackWalkProfile1.enter(inliningTarget);
        /*
         * It is necessary to continue from where we stopped with the backref walk because the
         * original starting frame might not be on stack anymore
         */
        return readSlowPath(curFrameInfo, frameAccess, selector, level - i, callerFlags, materializeFrameNode);
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    private PFrame readSlowPath(PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, int callerFlags,
                    MaterializeFrameNode materializeFrameNode) {
        if (level == 0 && startFrameInfo != null && startFrameInfo.getPyFrame() != null && !selector.skip(startFrameInfo.getRootNode()) && startFrameInfo.getPyFrame().getThread() != null &&
                        startFrameInfo.getPyFrame().getThread() != Thread.currentThread()) {
            // We have the frame we're looking for, but it's on another thread
            Thread thread = startFrameInfo.getPyFrame().getThread();
            if (thread.isAlive()) {
                try (var gil = GilNode.uncachedRelease()) {
                    // Schedule a safepoint action on that thread
                    Future<Void> future = PythonContext.get(null).getEnv().submitThreadLocal(new Thread[]{thread}, new ThreadLocalAction(true, false) {
                        @Override
                        protected void perform(Access access) {
                            Node location = access.getLocation();
                            if (location instanceof PBytecodeDSLRootNode) {
                                // See AsyncPythonAction#execute for explanation
                                location = PythonLanguage.get(null).unavailableSafepointLocation;
                            }
                            StackWalkResult result = ReadFrameNode.getFrame(location, startFrameInfo, frameAccess, selector, 0, callerFlags);
                            processStackWalkResult(materializeFrameNode, callerFlags, result);
                        }
                    });
                    TruffleSafepoint.setBlockedThreadInterruptible(this, voidFuture -> {
                        try {
                            voidFuture.get(10, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.RuntimeError, tsLiteral("Failed to interrupt thread " + Thread.currentThread() + " within 10 seconds"));
                        } catch (CancellationException e) {
                            // Ignore
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }, future);
                }
            }
            assert !startFrameInfo.getPyFrame().outdatedCallerFlags(callerFlags);
            return startFrameInfo.getPyFrame();
        }
        StackWalkResult callerFrameResult = getFrame(this, startFrameInfo, frameAccess, selector, level, callerFlags);
        return processStackWalkResult(materializeFrameNode, callerFlags, callerFrameResult);
    }

    private static PFrame processStackWalkResult(MaterializeFrameNode materializeFrameNode, int callerFlags, StackWalkResult callerFrameResult) {
        if (callerFrameResult != null) {
            Node location = callerFrameResult.callNode;
            if (!(callerFrameResult.rootNode instanceof PBytecodeDSLRootNode) && location == null) {
                /*
                 * We can fixup the location like this only for other root nodes, for Bytecode DSL
                 * we need the BytecodeNode
                 */
                location = callerFrameResult.rootNode;
            }
            return materializeFrameNode.execute(location, false, CallerFlags.needsLocals(callerFlags), callerFrameResult.frame);
        }
        return null;
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
     *                 Frame topPyFrame = ReadFrameNode.getCurrentFrame(this
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
    public static Frame getCurrentFrame(Node requestingNode, FrameInstance.FrameAccess frameAccess, int callerFlags) {
        StackWalkResult result = getFrame(Objects.requireNonNull(requestingNode), null, frameAccess, AllFramesSelector.INSTANCE, 0, callerFlags);
        if (result != null) {
            return result.frame;
        }
        return null;
    }

    // For getting just the Truffle frame, we do not need current location
    public static Frame getCallerFrame(PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, int callerFlags) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        StackWalkResult result = getFrame(null, Objects.requireNonNull(startFrame), frameAccess, selector, level, callerFlags);
        return result != null ? result.frame : null;
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
     * @param callerFlags - the {@link CallerFlags} specifying what we need from the frame
     */
    @TruffleBoundary
    public static StackWalkResult getFrame(Node requestingNode, PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, FrameSelector selector, int level, int callerFlags) {
        PythonContext.setWasStackWalk();
        return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
            int i = startFrame != null ? -1 : 0;
            boolean first = true;
            RootNode prevRootNode;

            public StackWalkResult visitFrame(FrameInstance frameInstance) {
                RootNode rootNode = ReadFrameNode.getRootNode(frameInstance);
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
                                callNode != null : String.format("root=%s, i=%d", rootNode, i);
                first = false;
                if (!(rootNode instanceof PRootNode pRootNode && pRootNode.setsUpCalleeContext())) {
                    // Note: any non-Python Truffle frames should have been preceded by
                    // BoundaryCallContext.enter/exit, and the PFrame.References will be chained
                    // through thread state. We will eventually arrive at the Python frame that did
                    // BoundaryCallContext.enter, find the IndirectCallData via the callNode and
                    // tell it to pass the PFrame.Reference in thread state next time
                    prevRootNode = rootNode;
                    return null;
                }
                if (i < 0 && startFrame != null) {
                    // We are still looking for the start frame
                    Frame roFrame = ReadFrameNode.getFrame(frameInstance, FrameInstance.FrameAccess.READ_ONLY);
                    if (PArguments.getCurrentFrameInfo(roFrame) == startFrame) {
                        i = 0;
                    }
                }
                if (i >= 0) {
                    // We found the start frame already, or we are supposed to return the start
                    // frame (startFrame argument was null)
                    if (!selector.skip(pRootNode)) {
                        if (i == level) {
                            Frame frame = ReadFrameNode.getFrame(frameInstance, frameAccess);
                            assert PArguments.isPythonFrame(frame);
                            IndirectCallData.setCallerFlagsOnIndirectCallData(callNode, callerFlags);
                            if (prevRootNode instanceof PRootNode prevPRootNode && prevPRootNode.setsUpCalleeContext()) {
                                // Update the flags in the callee
                                prevPRootNode.updateCallerFlags(callerFlags);
                            }
                            return new StackWalkResult(pRootNode, callNode, frame);
                        }
                        i += 1;
                    }
                }
                // For any Python root node we traverse we need the PFrame.Reference to be passed in
                // call arguments next time.
                IndirectCallData.setCallerFlagsOnIndirectCallData(callNode, CallerFlags.NEEDS_FRAME_REFERENCE);
                pRootNode.updateCallerFlags(CallerFlags.NEEDS_FRAME_REFERENCE);
                prevRootNode = pRootNode;
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
}
