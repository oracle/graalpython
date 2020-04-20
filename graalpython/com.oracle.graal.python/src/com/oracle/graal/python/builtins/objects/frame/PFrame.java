/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.frame;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetLocalsNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class PFrame extends PythonBuiltinObject {
    private Object[] arguments;
    private final Object localsDict;
    private final boolean inClassScope;
    private final Reference virtualFrameInfo;
    private Node location;
    private RootCallTarget callTarget;
    private int line = -2;

    private PFrame.Reference backref = null;

    // TODO: frames: this is a large object, think about how to make this
    // smaller
    public static final class Reference {
        public static final Reference EMPTY = new Reference(null);

        // The Python-level frame. Can be incomplete.
        private PFrame pyFrame = null;

        // The location of the last call
        private Node callNode = null;

        // A flag whether this frame is escaped. A Truffle frame is escaped if the corresponding
        // PFrame may be used without having the Truffle frame on the stack. This flag is also set
        // by a callee frame to inform the caller that it should materialize itself when it returns.
        private boolean escaped = false;

        private final Reference callerInfo;

        public Reference(Reference callerInfo) {
            this.callerInfo = callerInfo;
        }

        public void materialize(Frame targetFrame, PRootNode location) {
            Reference curFrameInfo = PArguments.getCurrentFrameInfo(targetFrame);
            boolean inClassScope = PArguments.getSpecialArgument(targetFrame) instanceof ClassBodyRootNode;
            CompilerAsserts.partialEvaluationConstant(location);
            if (location.getFrameEscapedWithoutAllocationProfile().profile(this.pyFrame == null || this.pyFrame.virtualFrameInfo == null)) {
                if (this.pyFrame == null) {
                    // TODO: frames: this doesn't go through the factory
                    this.pyFrame = new PFrame(PythonBuiltinClassType.PFrame, curFrameInfo, location, inClassScope);
                } else {
                    assert this.pyFrame.localsDict != null : "PFrame was set without a frame or a locals dict";
                    // this is the case when we had custom locals
                    this.pyFrame = new PFrame(PythonBuiltinClassType.PFrame, curFrameInfo, location, this.pyFrame.localsDict, inClassScope);
                }
            }
            // TODO: frames: update location
        }

        public void setCustomLocals(Object customLocals) {
            assert customLocals != null : "cannot set null custom locals";
            assert pyFrame == null : "cannot set customLocals when there's already a PFrame";
            // TODO: frames: this doesn't go through the factory
            this.pyFrame = new PFrame(PythonBuiltinClassType.PFrame, customLocals);
        }

        public void setBackref(PFrame.Reference backref) {
            assert pyFrame != null : "setBackref should only be called when the PFrame escaped";
            pyFrame.setBackref(backref);
        }

        public void markAsEscaped() {
            escaped = true;
        }

        public boolean isEscaped() {
            return escaped;
        }

        public PFrame getPyFrame() {
            return pyFrame;
        }

        public void setPyFrame(PFrame escapedFrame) {
            assert this.pyFrame == null || this.pyFrame.isIncomplete() || this.pyFrame == escapedFrame : "cannot change the escaped frame";
            this.pyFrame = escapedFrame;
        }

        public Node getCallNode() {
            return callNode;
        }

        public void setCallNode(Node callNode) {
            this.callNode = callNode;
        }

        public Reference getCallerInfo() {
            return callerInfo;
        }
    }

    public PFrame(LazyPythonClass cls, Reference virtualFrameInfo, Node location, boolean inClassScope) {
        this(cls, virtualFrameInfo, location, null, inClassScope);
    }

    public PFrame(LazyPythonClass cls, Reference virtualFrameInfo, Node location, Object locals, boolean inClassScope) {
        super(cls);
        this.virtualFrameInfo = virtualFrameInfo;
        this.localsDict = locals;
        this.location = location;
        this.inClassScope = inClassScope;
    }

    private PFrame(LazyPythonClass cls, Object locals) {
        super(cls);
        this.virtualFrameInfo = null;
        this.location = null;
        this.inClassScope = false;
        this.localsDict = locals;
    }

    public PFrame(LazyPythonClass cls, @SuppressWarnings("unused") Object threadState, PCode code, PythonObject globals, Object locals) {
        super(cls);
        // TODO: frames: extract the information from the threadState object
        Object[] frameArgs = PArguments.create();
        PArguments.setGlobals(frameArgs, globals);
        Reference curFrameInfo = new Reference(null);
        this.virtualFrameInfo = curFrameInfo;
        curFrameInfo.setPyFrame(this);
        this.location = code.getRootNode();
        this.inClassScope = code.getRootNode() instanceof ClassBodyRootNode;
        this.line = code.getRootNode() == null ? code.getFirstLineNo() : -2;

        localsDict = locals;
    }

    /**
     * Prefer to use the {@link GetLocalsNode}.<br/>
     * <br/>
     *
     * Returns a dictionary with the locals, possibly creating it from the frame. Note that the
     * dictionary may have been modified and should then be updated with the current frame locals.
     * To that end, use the {@link GetLocalsNode} instead of calling this method directly.
     */
    public Object getLocalsDict() {
        return localsDict;
    }

    public PFrame.Reference getRef() {
        if (virtualFrameInfo != null) {
            return virtualFrameInfo;
        } else {
            return null;
        }
    }

    public PFrame.Reference getBackref() {
        return backref;
    }

    public void setBackref(PFrame.Reference backref) {
        assert this.backref == null || this.backref == backref : "setBackref tried to set a backref different to the one that was previously attached";
        this.backref = backref;
    }

    public void setLine(int line) {
        this.line = line;
    }

    @TruffleBoundary
    public int getLine() {
        if (line == -2) {
            if (location == null) {
                line = -1;
            } else {
                SourceSection sourceSection = location.getEncapsulatingSourceSection();
                if (sourceSection == null) {
                    line = -1;
                } else {
                    line = sourceSection.getStartLine();
                }
            }
        }
        return line;
    }

    /**
     * Prefer to use the {@link GetLocalsNode}.<br/>
     * <br/>
     *
     * Returns a dictionary with the locals, possibly creating it from the frame. Note that the
     * dictionary may have been modified and should then be updated with the current frame locals.
     * To that end, use the {@link GetLocalsNode} instead of calling this method directly.
     */
    public Object getLocals(@SuppressWarnings("unused") PythonObjectFactory factory) {
        assert localsDict != null;
        return localsDict;
    }

    /**
     * Prefer to use the
     * {@link com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetGlobalsNode}.<br/>
     * <br/>
     *
     * Returns a dictionary with the locals, possibly creating it from the frame. Note that the
     * dictionary may have been modified and should then be updated with the current frame locals.
     * To that end, use the {@link GetLocalsNode} instead of calling this method directly.
     */
    public PythonObject getGlobals() {
        return PArguments.getGlobals(arguments);
    }

    /**
     * Checks if this frame is complete in the sense that all frame accessors would work, e.g.
     * locals, backref etc. We optimize locals access to create a lightweight PFrame that has no
     * stack attached to it, which is where we check this.
     */
    public boolean isIncomplete() {
        return location == null && virtualFrameInfo == null;
    }

    /**
     * {@code true} if this {@code PFrame} is associated with a {@code Frame}, i.e., it has a frame
     * info. On the other hand, {@code false} means that this {@code PFrame} has been created
     * artificially which is most commonly done to carry custom locals.
     **/
    public boolean isAssociated() {
        return virtualFrameInfo != null;
    }

    public boolean inClassScope() {
        return inClassScope;
    }

    public RootCallTarget getTarget() {
        if (callTarget == null) {
            if (location != null) {
                callTarget = createCallTarget(location);
            } else if (getRef() != null && getRef().getCallNode() != null) {
                callTarget = createCallTarget(getRef().getCallNode());
            }
        }
        return callTarget;
    }

    @TruffleBoundary
    private static RootCallTarget createCallTarget(Node location) {
        return Truffle.getRuntime().createCallTarget(location.getRootNode());
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments2) {
        this.arguments = arguments2;
    }

    public void setLocation(Node location) {
        this.location = location;
    }
}
