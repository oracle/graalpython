/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "read_caller_fame")
public final class ReadCallerFrameNode extends Node {
    @CompilationFinal private ConditionProfile cachedCallerFrameProfile;
    @Child private MaterializeFrameNode materializeNode;

    protected ReadCallerFrameNode() {
    }

    public static ReadCallerFrameNode create() {
        return new ReadCallerFrameNode();
    }

    public final PFrame executeWith(VirtualFrame frame, int level) {
        return executeWith(frame, PArguments.getCurrentFrameInfo(frame), true, level);
    }

    public final PFrame executeWith(VirtualFrame frame, Frame startFrame, int level) {
        return executeWith(frame, PArguments.getCurrentFrameInfo(startFrame), true, level);
    }

    public PFrame executeWith(VirtualFrame frame, PFrame.Reference startFrameInfo, int level) {
        return executeWith(frame, startFrameInfo, true, level);
    }

    public PFrame executeWith(VirtualFrame frame, PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, int level) {
        return executeWith(frame, startFrameInfo, frameAccess, true, level);
    }

    public PFrame executeWith(VirtualFrame frame, PFrame.Reference startFrameInfo, boolean skipInternal, int level) {
        return executeWith(frame, startFrameInfo, FrameInstance.FrameAccess.READ_ONLY, skipInternal, level);
    }

    public PFrame executeWith(VirtualFrame frame, PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, boolean skipInternal, int level) {
        PFrame.Reference curFrameInfo = startFrameInfo;
        if (cachedCallerFrameProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cachedCallerFrameProfile = ConditionProfile.createBinaryProfile();
            // executed the first time - don't pollute the profile
            for (int i = 0; i <= level;) {
                PFrame.Reference callerInfo = curFrameInfo.getCallerInfo();
                if (callerInfo == null) {
                    Frame callerFrame = getCallerFrame(startFrameInfo, frameAccess, skipInternal, level);
                    if (callerFrame != null) {
                        return ensureMaterializeNode().execute(frame, false, true, callerFrame);
                    }
                    return null;
                } else if (!(skipInternal && (callerInfo.getCallNode() == null || PRootNode.isPythonInternal(callerInfo.getCallNode().getRootNode())))) {
                    i++;
                }
                curFrameInfo = callerInfo;
            }
        } else {
            curFrameInfo = walkLevels(frame, curFrameInfo, frameAccess, skipInternal, level);
        }
        return curFrameInfo.getPyFrame();
    }

    private PFrame.Reference walkLevels(VirtualFrame frame, PFrame.Reference startFrameInfo, FrameInstance.FrameAccess frameAccess, boolean skipInternal, int level) {
        PFrame.Reference currentFrame = startFrameInfo;
        for (int i = 0; i <= level;) {
            PFrame.Reference callerInfo = currentFrame.getCallerInfo();
            if (cachedCallerFrameProfile.profile(callerInfo == null)) {
                Frame callerFrame = getCallerFrame(startFrameInfo, frameAccess, skipInternal, level);
                if (callerFrame != null) {
                    // At this point, we must 'materialize' the frame. Actually, the Truffle frame
                    // is never materialized but we ensure that a corresponding PFrame is created
                    // and that the locals and arguments are synced.
                    ensureMaterializeNode().execute(frame, false, true, callerFrame);
                    return PArguments.getCurrentFrameInfo(callerFrame);
                }
                return PFrame.Reference.EMPTY;
            } else if (!(skipInternal && (callerInfo.getCallNode() == null || PRootNode.isPythonInternal(callerInfo.getCallNode().getRootNode())))) {
                i++;
            }
            currentFrame = callerInfo;
        }
        return currentFrame;
    }

    /**
     * Walk up the stack to find the currently top Python frame. This method is mostly useful for
     * code that cannot accept a {@code VirtualFrame} parameter (e.g. library code). It is necessary
     * to provide the requesting node because it might be necessary to locate the last
     * {@link IndirectCallNode} that effectively executes the requesting node such that the
     * necessary assumptions can be invalidated to avoid deopt loops.<br/>
     * Consider following situation:<br/>
     *
     * <pre>
     *     public class SomeCaller extends PRootNode implements IndirectCallNode {
     *         &#64;Child private InteropLibrary lib = ...;
     *         public Object execute(VirtualFrame frame, Object callee, Object[] args) {
     *             Object state = IndirectCallContext.enter(frame, ctx, this);
     *             try {
     *                 return lib.execute(callee, args);
     *             } finally {
     *                 IndirectCallContext.exit(frame, ctx, state);
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
     * @param requestingNode - the frame to start counting from or {@code null} to return the top
     *            frame
     * @param frameAccess - the desired {@link FrameInstance} access kind
     */
    public static Frame getCurrentFrame(Node requestingNode, FrameInstance.FrameAccess frameAccess) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return getFrame(Objects.requireNonNull(requestingNode), null, frameAccess, false, 0);
    }

    /**
     * Walk up the stack to find the {@code startFrame} and from then ({@code
     * level} + 1)-times (counting only non-internal Python frames if {@code
     * skipInternal} is true). If {@code startFrame} is {@code null}, return the currently top
     * Python frame.
     *
     * @param startFrame - the frame to start counting from (must not be {@code null})
     * @param frameAccess - the desired {@link FrameInstance} access kind
     * @param skipInternal - declares if Python internal frames should be skipped or counted
     * @param level - the stack depth to go to. Ignored if {@code startFrame} is {@code null}
     */
    public static Frame getCallerFrame(PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, boolean skipInternal, int level) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return getFrame(null, Objects.requireNonNull(startFrame), frameAccess, skipInternal, level);
    }

    private static Frame getFrame(Node requestingNode, PFrame.Reference startFrame, FrameInstance.FrameAccess frameAccess, boolean skipInternal, int level) {
        assert CompilerDirectives.inInterpreter();
        final Frame[] outputFrame = new Frame[1];
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Frame>() {
            int i = -1;

            /**
             * We may find the Python frame at the level we desire, but the {@link PRootNode}
             * associated with it may have been called from a different language, and thus not a
             * Python {@link IndirectCallNode}. That means that we cannot immediately return when we
             * find the correct level frame, but instead we need to remember the frame in
             * {@code outputFrame} and then keep going until we find the previous Python caller on
             * the stack (or not). That last Python caller before the Python frame we need must push
             * the info.
             *
             * This can easily be seen when this is used to {@link PythonContext#peekTopFrameInfo()}
             * , because in that case, that info must be set by the caller that is somewhere up the
             * stack.
             *
             * <pre>
             *                      ================
             *                   ,>| PythonCallNode |
             *                   |  ================
             *                   | |  LLVMRootNode  |
             *                   | |  LLVMCallNode  |
             *                   |  ================
             *                   |       . . .
             *                   |  ================
             *                   | |  LLVMRootNode  |
             *                   | |  LLVMCallNode  |
             *                   |  ================
             *                    \| PythonRootNode |
             *                      ================
             * </pre>
             */
            public Frame visitFrame(FrameInstance frameInstance) {
                RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                RootNode rootNode = target.getRootNode();
                Node callNode = frameInstance.getCallNode();
                boolean didMark = IndirectCallNode.setEncapsulatingNeedsToPassCallerFrame(callNode != null ? callNode : requestingNode);
                if (rootNode instanceof PRootNode && outputFrame[0] == null) {
                    PRootNode pRootNode = (PRootNode) rootNode;
                    pRootNode.setNeedsCallerFrame();
                    if (i < 0 && startFrame != null) {
                        Frame roFrame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        if (PArguments.getCurrentFrameInfo(roFrame) == startFrame) {
                            i = 0;
                        }
                    } else {
                        // Skip frames of builtin functions (if requested) because these do not have
                        // a Python frame in CPython.
                        if (!(skipInternal && pRootNode.isPythonInternal())) {
                            if (i == level || startFrame == null) {
                                Frame frame = frameInstance.getFrame(frameAccess);
                                assert PArguments.isPythonFrame(frame);
                                PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
                                // avoid overriding the location if we don't know it
                                if (callNode != null) {
                                    info.setCallNode(callNode);
                                } else {
                                    // In some special cases we call without a Truffle call node; in
                                    // this case, we use the root node as location (e.g. see
                                    // AsyncHandler.processAsyncActions).
                                    info.setCallNode(pRootNode);
                                }
                                // We may never return a frame without location because then we
                                // cannot materialize it.
                                assert info.getCallNode() != null : "tried to read frame without location (root: " + pRootNode + ")";
                                outputFrame[0] = frame;
                            }
                            i += 1;
                        }
                    }
                }
                if (didMark) {
                    return outputFrame[0];
                } else {
                    return null;
                }
            }
        });
        return outputFrame[0];
    }

    private MaterializeFrameNode ensureMaterializeNode() {
        if (materializeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            materializeNode = insert(MaterializeFrameNodeGen.create());
        }
        return materializeNode;
    }
}
