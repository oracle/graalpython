/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetLocalsNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class PFrame extends PythonBuiltinObject {
    private Object localsDict;
    private final boolean inClassScope;
    private final MaterializedFrame frame;
    private final Node location;
    private RootCallTarget callTarget;
    private int line = -2;

    private PFrame[] backref = null;

    private static final PFrame NO_FRAME_MARKER = new PFrame();

    private PFrame() {
        super(PythonBuiltinClassType.PFrame);
        this.frame = null;
        this.location = null;
        this.inClassScope = false;
    }

    public PFrame(LazyPythonClass cls, MaterializedFrame frame) {
        super(cls);
        this.frame = frame;
        this.location = null;
        this.inClassScope = PArguments.getSpecialArgument(frame) instanceof ClassBodyRootNode;
    }

    public PFrame(LazyPythonClass cls, MaterializedFrame frame, Object locals) {
        super(cls);
        this.frame = frame;
        this.localsDict = locals;
        this.location = null;
        this.inClassScope = PArguments.getSpecialArgument(frame) instanceof ClassBodyRootNode;
    }

    public PFrame(LazyPythonClass cls, Object locals) {
        super(cls);
        this.frame = null;
        this.location = null;
        this.inClassScope = false;
        this.localsDict = locals;
    }

    public PFrame(LazyPythonClass cls, @SuppressWarnings("unused") Object threadState, PCode code, PythonObject globals, Object locals) {
        super(cls);
        // TODO: frames: extract the information from the threadState object
        Object[] frameArgs = PArguments.create();
        PArguments.setGlobals(frameArgs, globals);
        this.frame = Truffle.getRuntime().createMaterializedFrame(frameArgs);
        this.location = code.getRootNode();
        this.inClassScope = code.getRootNode() instanceof ClassBodyRootNode;
        this.line = code.getRootNode() == null ? code.getFirstLineNo() : -2;

        localsDict = locals;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public Object getLocalsDict() {
        return localsDict;
    }

    /**
     * If this frame has a backref container, it is escaped, because it expects
     * its Python caller to fill in that container. However, the backref may not
     * be filled in, yet, because this frame might still be live. In that case,
     * {@link #getBackref()} will still return <tt>null</tt>.
     */
    public boolean isEscaped() {
        return backref != null;
    }

    /**
     * Returns the Python caller of this frame, provided that it has already
     * been filled in.
     */
    public Object getBackref() {
        if (backref != null) {
            if (backref[0] == NO_FRAME_MARKER) {
                return PNone.NONE;
            } else {
                return backref[0];
            }
        } else {
            // TODO: frames: Use ReadCallerFrameNode in caller.
            // TODO: frames: Update ReadCallerFrameNode to also store PFrame in
            // the caller arguments at this point
            return null;
        }
    }

    public void setBackref(PFrame frame) {
        assert backref == null || backref[0] == null : "do not overwrite backref";
        if (backref == null) {
            backref = new PFrame[] { frame };
        } else {
            backref[0] = frame;
        }
    }

    public void setBackref(PFrame[] frame) {
        assert backref == null : "do not overwrite backref container";
        backref = frame;
    }

    public void setNoBackref() {
        assert backref != null && backref[0] == null : "do not overwrite backref to mark a top frame";
        backref[0] = NO_FRAME_MARKER;
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

    public Node getCallNode() {
        return location;
    }

    /**
     * Prefer to use the {@link GetLocalsNode}.<br/>
     * <br/>
     *
     * Returns a dictionary with the locals, possibly creating it from the frame. Note that the
     * dictionary may have been modified and should then be updated with the current frame locals.
     * To that end, use the {@link GetLocalsNode} instead of calling this method directly.
     */
    public Object getLocals(PythonObjectFactory factory) {
        if (localsDict == null) {
            assert frame != null;
            return localsDict = factory.createDictLocals(frame);
        } else {
            return localsDict;
        }
    }

    /**
     * Checks if this frame is complete in the sense that all frame accessors would work, e.g.
     * locals, backref etc. We optimize locals access to create a lightweight PFrame that has no
     * stack attached to it, which is where we check this.
     */
    public boolean isIncomplete() {
        return location == null;
    }

    /**
     * When this is true, the frame has escaped
     **/
    public boolean hasFrame() {
        return frame != null;
    }

    public boolean inClassScope() {
        return inClassScope;
    }

    public RootCallTarget getTarget() {
        if (callTarget == null && location != null) {
            callTarget = createCallTarget(location);
        }
        return callTarget;
    }

    @TruffleBoundary
    private static RootCallTarget createCallTarget(Node location) {
        return Truffle.getRuntime().createCallTarget(location.getRootNode());
    }
}
