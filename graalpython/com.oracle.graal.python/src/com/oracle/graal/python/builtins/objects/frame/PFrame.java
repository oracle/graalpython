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
package com.oracle.graal.python.builtins.objects.frame;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.GetCodeRootNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetLocalsNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;

public final class PFrame extends PythonBuiltinObject {
    private static final int UNINITIALIZED_LINE = -2;

    private Object[] arguments;
    private final MaterializedFrame locals;
    private Object localsDict;
    private final Reference virtualFrameInfo;
    private Node location;
    private RootCallTarget callTarget;
    private int line = UNINITIALIZED_LINE;
    private int bci = -1;

    /*
     * when emitting trace events, the line number will not be correct by default, so it has be
     * manually managed
     */
    private boolean lockLine = false;

    private Object localTraceFun = null;

    private boolean traceLine = true;

    private PFrame.Reference backref = null;

    public Object getLocalTraceFun() {
        return localTraceFun;
    }

    public void setLocalTraceFun(Object localTraceFun) {
        this.localTraceFun = localTraceFun;
    }

    public boolean getTraceLine() {
        return traceLine;
    }

    public void setTraceLine(boolean traceLine) {
        this.traceLine = traceLine;
    }

    // TODO: frames: this is a large object, think about how to make this
    // smaller
    public static final class Reference {
        public static final Reference EMPTY = new Reference(null);

        // The Python-level frame
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
            assert this.pyFrame == null || this.pyFrame == escapedFrame : "cannot change the escaped frame";
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

    public PFrame(PythonLanguage lang, Reference virtualFrameInfo, Node location, MaterializedFrame locals) {
        super(PythonBuiltinClassType.PFrame, PythonBuiltinClassType.PFrame.getInstanceShape(lang));
        this.virtualFrameInfo = virtualFrameInfo;
        this.locals = locals;
        this.location = location;
    }

    public PFrame(PythonLanguage lang, @SuppressWarnings("unused") Object threadState, PCode code, PythonObject globals, Object localsDict) {
        super(PythonBuiltinClassType.PFrame, PythonBuiltinClassType.PFrame.getInstanceShape(lang));
        // TODO: frames: extract the information from the threadState object
        Object[] frameArgs = PArguments.create();
        PArguments.setGlobals(frameArgs, globals);
        PArguments.setSpecialArgument(frameArgs, localsDict);
        Reference curFrameInfo = new Reference(null);
        this.virtualFrameInfo = curFrameInfo;
        curFrameInfo.setPyFrame(this);
        this.location = GetCodeRootNode.executeUncached(code);
        this.line = this.location == null ? code.getFirstLineNo() : UNINITIALIZED_LINE;
        this.arguments = frameArgs;
        this.locals = null;
        this.localsDict = localsDict;
    }

    /**
     * Get the locals synced by {@link MaterializeFrameNode}. May be null when using custom locals.
     * In most cases, you should use {@link GetFrameLocalsNode}.
     */
    public MaterializedFrame getLocals() {
        return locals;
    }

    /**
     * Use {@link GetFrameLocalsNode} instead of accessing this directly.
     */
    public Object getLocalsDict() {
        return localsDict;
    }

    public boolean hasCustomLocals() {
        return locals == null;
    }

    public void setLocalsDict(Object dict) {
        localsDict = dict;
    }

    public PFrame.Reference getRef() {
        return virtualFrameInfo;
    }

    public PFrame.Reference getBackref() {
        return backref;
    }

    public void setBackref(PFrame.Reference backref) {
        assert this.backref == null || this.backref == backref : "setBackref tried to set a backref different to the one that was previously attached";
        this.backref = backref;
    }

    public void setLine(int line) {
        if (lockLine) {
            return;
        }
        this.line = line;
    }

    public void setLineLock(int line) {
        this.line = line;
        this.lockLine = true;
    }

    public void lineUnlock() {
        this.lockLine = false;
    }

    @TruffleBoundary
    public int getLine() {
        if (line == UNINITIALIZED_LINE) {
            if (location == null) {
                line = -1;
            } else if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                if (location instanceof BytecodeNode bytecodeNode) {
                    PBytecodeDSLRootNode rootNode = (PBytecodeDSLRootNode) bytecodeNode.getRootNode();
                    return rootNode.bciToLine(bci, bytecodeNode);
                }
            } else if (location instanceof PBytecodeRootNode bytecodeRootNode) {
                return bytecodeRootNode.bciToLine(bci);
            }
        }
        return line;
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

    public RootCallTarget getTarget() {
        if (callTarget == null) {
            if (location != null) {
                callTarget = PythonUtils.getOrCreateCallTarget(location.getRootNode());
            } else if (getRef() != null && getRef().getCallNode() != null) {
                callTarget = PythonUtils.getOrCreateCallTarget(getRef().getCallNode().getRootNode());
            }
        }
        return callTarget;
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

    public Node getLocation() {
        return location;
    }

    public int getBci() {
        return bci;
    }

    public void setBci(int bci) {
        this.bci = bci;
    }

    public int getLasti() {
        return bciToLasti(bci);
    }

    @TruffleBoundary
    public int bciToLasti(int bci) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            // TODO implement
            throw new UnsupportedOperationException("not implemented");
        } else {
            if (location instanceof PBytecodeRootNode bytecodeRootNode) {
                return bytecodeRootNode.bciToLasti(bci);
            } else if (location instanceof PBytecodeGeneratorRootNode generatorRootNode) {
                return generatorRootNode.getBytecodeRootNode().bciToLasti(bci);
            }
        }
        return -1;
    }
}
