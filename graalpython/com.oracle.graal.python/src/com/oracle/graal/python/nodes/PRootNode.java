/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.PythonUtils.NodeCounterWithLimit;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class PRootNode extends RootNode {
    private final ConditionProfile frameEscaped = ConditionProfile.create();

    @CompilationFinal private transient Assumption dontNeedCallerFrame = createCallerFrameAssumption();

    /**
     * Flag indicating if some child node of this root node (or a callee) eventually needs the
     * exception state. Hence, the caller of this root node should provide the exception state in
     * the arguments.
     */
    @CompilationFinal private transient Assumption dontNeedExceptionState = createExceptionStateAssumption();

    private transient int nodeCount = -1;

    // contains the code of this root node in marshaled/serialized form
    private transient byte[] code;

    protected PRootNode(TruffleLanguage<?> language) {
        super(language);
    }

    protected PRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /**
     * Imprecise node count used for inlining heuristics, saturated at
     * {@link PythonOptions#BuiltinsInliningMaxCallerSize}.
     */
    public final int getNodeCountForInlining() {
        CompilerAsserts.neverPartOfCompilation();
        int n = nodeCount;
        if (n != -1) {
            return n;
        }
        int maxSize = PythonLanguage.get(this).getEngineOption(PythonOptions.BuiltinsInliningMaxCallerSize);
        NodeCounterWithLimit counter = new NodeCounterWithLimit(maxSize);
        accept(counter);
        return nodeCount = counter.getCount();
    }

    public final void setNodeCountForInlining(int newCount) {
        // We accept the potential race in the callers of the getter and this setter
        assert newCount > 0 && newCount <= PythonLanguage.get(this).getEngineOption(PythonOptions.BuiltinsInliningMaxCallerSize);
        nodeCount = newCount;
    }

    public ConditionProfile getFrameEscapedProfile() {
        return frameEscaped;
    }

    public boolean needsCallerFrame() {
        return !dontNeedCallerFrame.isValid();
    }

    public void setNeedsCallerFrame() {
        CompilerAsserts.neverPartOfCompilation("this is usually called from behind a TruffleBoundary");
        dontNeedCallerFrame.invalidate();
    }

    public boolean needsExceptionState() {
        return !dontNeedExceptionState.isValid();
    }

    public void setNeedsExceptionState() {
        CompilerAsserts.neverPartOfCompilation("this is usually called from behind a TruffleBoundary");
        dontNeedExceptionState.invalidate();
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return true;
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public Node copy() {
        PRootNode pRootNode = (PRootNode) super.copy();
        // create new assumptions such that splits do not share them
        pRootNode.dontNeedCallerFrame = createCallerFrameAssumption();
        pRootNode.dontNeedExceptionState = createExceptionStateAssumption();
        return pRootNode;
    }

    public abstract Signature getSignature();

    public abstract boolean isPythonInternal();

    @CompilerDirectives.TruffleBoundary
    private static boolean isPythonInternal(PRootNode rootNode) {
        return rootNode.isPythonInternal();
    }

    public static boolean isPythonInternal(RootNode rootNode) {
        return rootNode instanceof PRootNode && isPythonInternal((PRootNode) rootNode);
    }

    public static boolean isPythonBuiltin(RootNode rootNode) {
        return rootNode instanceof BuiltinFunctionRootNode;
    }

    private static Assumption createCallerFrameAssumption() {
        return Truffle.getRuntime().createAssumption("does not need caller frame");
    }

    private static Assumption createExceptionStateAssumption() {
        return Truffle.getRuntime().createAssumption("does not need exception state");
    }

    public final void setCode(byte[] data) {
        CompilerAsserts.neverPartOfCompilation();
        assert this.code == null;
        this.code = data;
    }

    @TruffleBoundary
    public final byte[] getCode() {
        if (code != null) {
            return code;
        }
        return code = extractCode();
    }

    protected byte[] extractCode() {
        // no code for non-user functions
        return PythonUtils.EMPTY_BYTE_ARRAY;
    }

    /**
     * True if the root calls CalleeContext.enter or an equivalent on entry.
     */
    public boolean setsUpCalleeContext() {
        return false;
    }
}
