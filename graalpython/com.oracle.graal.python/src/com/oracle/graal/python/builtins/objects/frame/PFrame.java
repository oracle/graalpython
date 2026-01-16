/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public final class PFrame extends PythonBuiltinObject {
    private static final int UNINITIALIZED_LINE = -2;

    /**
     * The manual interpreter exclusively uses this field, and the Bytecode DSL interpreter
     * exclusively uses the {@link #bytecodeFrame} field.
     */
    private MaterializedFrame locals;
    private BytecodeFrame bytecodeFrame;

    /**
     * Whether the frame has dict locals passed from the caller (happens in eval/exec and class
     * bodies). Then locals is null and localsDict contains the dict locals. Otherwise both locals
     * and localsDict might contain a copy of the frame locals.
     */
    private final boolean hasCustomLocals;
    private Object localsDict;
    private PythonObject globals;
    private final Reference virtualFrameInfo;
    private final Thread thread;
    /**
     * For the manual bytecode interpreter the location can be the {@link PBytecodeRootNode} itself,
     * but for the Bytecode DSL interpreter, the location must be an AST node connected to the
     * {@link BytecodeNode} that was executed at the time when the BCI was captured.
     */
    private Node location;
    private RootCallTarget callTarget;
    private int line = UNINITIALIZED_LINE;
    private int bci = -1;

    /*
     * when emitting trace events, the line number will not be correct by default, so it has be
     * manually managed
     */
    private boolean lockLine = false;

    // -3 for jumps not allowed, -2 for no jump, otherwise the line to jump to - only should be set
    // in a trace function.
    public static final int DISALLOW_JUMPS = -3;
    public static final int NO_JUMP = -2;
    private int jumpDestLine = DISALLOW_JUMPS;
    private Object localTraceFun = null;
    private boolean localsAccessed;

    private boolean traceLine = true;

    private PFrame.Reference backref = null;

    /**
     * The last {@link CallerFlags} that were used the last time the frame was synced or passed down
     * to a callee. See {@link #needsRefresh(VirtualFrame, int)} for more details.
     */
    private int lastCallerFlags;

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

    public boolean isTraceArgument() {
        return jumpDestLine != DISALLOW_JUMPS;
    }

    public int getJumpDestLine() {
        return jumpDestLine;
    }

    public void setJumpDestLine(int jumpDestLine) {
        this.jumpDestLine = jumpDestLine;
    }

    public boolean localsAccessed() {
        return localsAccessed;
    }

    public void setLocalsAccessed(boolean localsAccessed) {
        this.localsAccessed = localsAccessed;
    }

    // TODO: frames: this is a large object, think about how to make this
    // smaller
    public static final class Reference {
        public static final Reference EMPTY = new Reference(null, null);

        // The Python-level frame
        private PFrame pyFrame = null;

        private final RootNode rootNode;

        // A flag whether this frame is escaped. A Truffle frame is escaped if the corresponding
        // PFrame may be used without having the Truffle frame on the stack. This flag is also set
        // by a callee frame to inform the caller that it should materialize itself when it returns.
        private boolean escaped = false;

        private Reference callerInfo;

        public Reference(RootNode rootNode, Reference callerInfo) {
            this.rootNode = rootNode;
            this.callerInfo = callerInfo;
        }

        public RootNode getRootNode() {
            return rootNode;
        }

        public void setCallerInfo(Reference callerInfo) {
            this.callerInfo = callerInfo;
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

        public Reference getCallerInfo() {
            return callerInfo;
        }
    }

    public PFrame(PythonLanguage lang, Reference virtualFrameInfo, Node location, boolean hasCustomLocals) {
        super(PythonBuiltinClassType.PFrame, PythonBuiltinClassType.PFrame.getInstanceShape(lang));
        this.virtualFrameInfo = virtualFrameInfo;
        this.location = location;
        this.hasCustomLocals = hasCustomLocals;
        // Mark everything as current for now. MaterializeFrameNode will set lastCallerFlags to a
        // narrower value if needed
        this.lastCallerFlags = CallerFlags.ALL_FRAME_FLAGS;
        this.thread = Thread.currentThread();
    }

    public PFrame(PythonLanguage lang, @SuppressWarnings("unused") Object threadState, PCode code, PythonObject globals, Object localsDict) {
        super(PythonBuiltinClassType.PFrame, PythonBuiltinClassType.PFrame.getInstanceShape(lang));
        // TODO: frames: extract the information from the threadState object
        this.globals = globals;
        this.location = GetCodeRootNode.executeUncached(code);
        Reference curFrameInfo = new Reference(location != null ? location.getRootNode() : null, null);
        this.virtualFrameInfo = curFrameInfo;
        curFrameInfo.setPyFrame(this);
        this.line = this.location == null ? code.getFirstLineNo() : UNINITIALIZED_LINE;
        this.hasCustomLocals = true;
        this.localsDict = localsDict;
        // This is a synthetic frame, there will be no sync, mark everything as current
        this.lastCallerFlags = CallerFlags.ALL_FRAME_FLAGS;
        this.thread = null;
    }

    /**
     * Get the locals synced by {@link MaterializeFrameNode}. May be null when using custom locals.
     * In most cases, you should use {@link GetFrameLocalsNode}.
     */
    public MaterializedFrame getLocals() {
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        assert CallerFlags.needsLocals(lastCallerFlags) : "Missing frame locals sync";
        return locals;
    }

    public void setLocals(MaterializedFrame locals) {
        assert !PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        lastCallerFlags |= CallerFlags.NEEDS_LOCALS;
        this.locals = locals;
    }

    /**
     * Get the locals synced by {@link BytecodeFrame}. May be null when using custom locals, but
     * null may also indicate that the locals were just not synced yet. Use
     * {@link #hasCustomLocals()} to check if this {@code PFrame} has custom locals. In most cases,
     * you should use {@link GetFrameLocalsNode} instead of this method.
     */
    public BytecodeFrame getBytecodeFrame() {
        assert PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        return bytecodeFrame;
    }

    public void setBytecodeFrame(BytecodeFrame bytecodeFrame) {
        assert PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
        this.bytecodeFrame = bytecodeFrame;
    }

    /**
     * PFrame is created once for each real frame, but some information in it, like locals and
     * lasti+lineno can get out of sync when the frame is still executing. The PFrame is normally
     * updated by the caller when the callee requests it. But the PFrame might sometimes be passed
     * down before the callee has set the flags to request the update and thus the callee might see
     * stale info. This method can tell that this happened and an explicit update via
     * {@link ReadFrameNode#refreshFrame(VirtualFrame, Reference, int)} is needed.
     *
     * @param frame The current frame. If the current executing frame is this PFrame, then sync is
     *            always needed.
     * @param callerFlags Specifies which fields should be checked for needing sync. It should be
     *            flags from {@link CallerFlags}.
     */
    public boolean needsRefresh(VirtualFrame frame, int callerFlags) {
        if (outdatedCallerFlags(callerFlags)) {
            return true;
        }
        if (CallerFlags.needsLocals(callerFlags) || CallerFlags.needsLasti(callerFlags)) {
            if (frame != null && PArguments.getCurrentFrameInfo(frame) == getRef()) {
                return true;
            }
            // Frames from other threads need to go through slow path
            return thread != null && thread != Thread.currentThread();
        }
        return false;
    }

    public boolean outdatedCallerFlags(int callerFlags) {
        if (hasCustomLocals) {
            // Custom locals don't need locals sync
            callerFlags &= ~CallerFlags.NEEDS_LOCALS;
        }
        if (CallerFlags.needsLocals(callerFlags) && locals == null && bytecodeFrame == null) {
            return true;
        }
        return (callerFlags & lastCallerFlags) != callerFlags;
    }

    public void setLastCallerFlags(int lastCallerFlags) {
        this.lastCallerFlags = lastCallerFlags;
    }

    /**
     * Use {@link GetFrameLocalsNode} instead of accessing this directly.
     */
    public Object getLocalsDict() {
        return localsDict;
    }

    public boolean hasCustomLocals() {
        return hasCustomLocals;
    }

    public void setLocalsDict(Object dict) {
        localsDict = dict;
    }

    public PFrame.Reference getRef() {
        return virtualFrameInfo;
    }

    public Thread getThread() {
        return thread;
    }

    public void setLine(int line) {
        if (lockLine) {
            return;
        }
        this.line = line;
    }

    public void resetLine() {
        if (lockLine) {
            return;
        }
        this.line = UNINITIALIZED_LINE;
    }

    public void setLineLock(int line) {
        this.line = line;
        this.lockLine = true;
    }

    public void lineUnlock() {
        this.lockLine = false;
    }

    public boolean didJump() {
        return jumpDestLine > NO_JUMP;
    }

    @TruffleBoundary
    public int getLine() {
        if (line == UNINITIALIZED_LINE) {
            if (location == null) {
                line = -1;
            } else if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                if (location instanceof BytecodeNode bytecodeNode) {
                    PBytecodeDSLRootNode rootNode = (PBytecodeDSLRootNode) bytecodeNode.getRootNode();
                    return rootNode.bciToLine(getBci(), bytecodeNode);
                }
            } else if (location instanceof PBytecodeRootNode bytecodeRootNode) {
                return bytecodeRootNode.bciToLine(getBci());
            }
        }
        return line;
    }

    /**
     * Get the frame globals. Might be a dictionary or a PythonModule.
     */
    public PythonObject getGlobals() {
        return globals;
    }

    public RootCallTarget getTarget() {
        if (callTarget == null) {
            if (location != null) {
                callTarget = PythonUtils.getOrCreateCallTarget(location.getRootNode());
            } else if (getRef() != null && getRef().getRootNode() != null) {
                callTarget = PythonUtils.getOrCreateCallTarget(getRef().getRootNode());
            }
        }
        return callTarget;
    }

    public void setGlobals(PythonObject globals) {
        this.globals = globals;
    }

    public void setLocation(Node location) {
        this.location = location;
    }

    public Node getLocation() {
        return location;
    }

    public BytecodeNode getBytecodeNode() {
        return PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER ? BytecodeNode.get(location) : null;
    }

    public int getBci() {
        return bci;
    }

    public void setBci(int bci) {
        this.lastCallerFlags |= CallerFlags.NEEDS_LASTI;
        this.bci = bci;
    }

    public int getLasti() {
        assert CallerFlags.needsLasti(lastCallerFlags) : "Missing frame location sync";
        return bciToLasti(bci, location);
    }

    @TruffleBoundary
    public static int bciToLasti(int bci, Node location) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            if (bci >= 0 && location instanceof BytecodeNode bytecodeNode) {
                return PBytecodeDSLRootNode.bciToLasti(bci, bytecodeNode);
            }
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
