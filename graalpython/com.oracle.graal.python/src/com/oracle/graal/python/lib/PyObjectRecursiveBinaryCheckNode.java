/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnadoptableNode;

/**
 * Base for recursive binary checks like {@code isinstance} and {@code issubclass}. It implements
 * the recursive iteration of tuple {@code classinfo}; subclasses provide the scalar check.
 */
@ImportStatic({PythonOptions.class, PGuards.class})
@GenerateInline(false)
@GenerateCached(false)
@SuppressWarnings("truffle-neverdefault")
abstract class PyObjectRecursiveBinaryCheckNode extends PNodeWithContext {
    static final int MAX_EXPLODE_LOOP = 16; // is also shifted to the left by recursion depth

    public final boolean execute(Frame frame, Object arg, Object classinfo) {
        return executeInternal(frame, arg, classinfo, 0);
    }

    protected abstract boolean executeInternal(Frame frame, Object arg, Object classinfo, int depth);

    @NeverDefault
    abstract PyObjectRecursiveBinaryCheckNode createRecursive();

    abstract PyObjectRecursiveBinaryCheckNode getUncachedRecursive();

    @Idempotent
    protected static int getMaxExplodeLoop(int depth) {
        return MAX_EXPLODE_LOOP >> depth;
    }

    @Specialization(guards = {"depth < getNodeRecursionLimit(language)", "getLength(clsTuple) == cachedLen", "cachedLen < getMaxExplodeLoop(depth)"}, //
                    limit = "getVariableArgumentInlineCacheLimit()", excludeForUncached = true)
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    static boolean doTupleConstantLen(VirtualFrame frame, Object arg, PTuple clsTuple, int depth,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Bind PythonLanguage language,
                    @Cached("getLength(clsTuple)") int cachedLen,
                    @Shared @Cached GetObjectArrayNode getObjectArrayNode,
                    @Shared @Cached("createRecursive()") PyObjectRecursiveBinaryCheckNode recursiveNode) {
        Object[] array = getObjectArrayNode.execute(inliningTarget, clsTuple);
        int newDepth = depth + 1;
        for (int i = 0; i < cachedLen; i++) {
            Object cls = array[i];
            if (recursiveNode.executeInternal(frame, arg, cls, newDepth)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = "depth < getNodeRecursionLimit(language)", replaces = "doTupleConstantLen", excludeForUncached = true)
    static boolean doRecursiveWithNode(VirtualFrame frame, Object arg, PTuple clsTuple, int depth,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Bind PythonLanguage language,
                    @Shared @Cached GetObjectArrayNode getObjectArrayNode,
                    @Shared @Cached("createRecursive()") PyObjectRecursiveBinaryCheckNode recursiveNode) {
        return loopRecursive(frame, arg, clsTuple, inliningTarget, getObjectArrayNode, recursiveNode, depth + 1);
    }

    @Specialization(guards = {"depth >= getNodeRecursionLimit(language)"}, excludeForUncached = true)
    boolean doRecursiveTransition(VirtualFrame frame, Object arg, PTuple clsTuple, @SuppressWarnings("unused") int depth,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Bind PythonLanguage language,
                    @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                    @Shared @Cached GetObjectArrayNode getObjectArrayNode) {
        Object state = BoundaryCallContext.enter(frame, boundaryCallData);
        try {
            // Note: we need actual recursion to trigger the stack overflow error like CPython.
            return callRecursiveWithNodeTruffleBoundary(inliningTarget, arg, clsTuple, getObjectArrayNode);
        } finally {
            BoundaryCallContext.exit(frame, boundaryCallData, state);
        }
    }

    @Specialization
    boolean doRecursiveUncached(VirtualFrame frame, Object arg, PTuple clsTuple, @SuppressWarnings("unused") int depth) {
        assert this instanceof UnadoptableNode;
        return loopRecursive(frame, arg, clsTuple, null, GetObjectArrayNode.getUncached(), this, -1);
    }

    @TruffleBoundary
    private boolean callRecursiveWithNodeTruffleBoundary(Node inliningTarget, Object arg, PTuple clsTuple, GetObjectArrayNode getObjectArrayNode) {
        return loopRecursive(null, arg, clsTuple, inliningTarget, getObjectArrayNode, getUncachedRecursive(), -1);
    }

    private static boolean loopRecursive(VirtualFrame frame, Object arg, PTuple clsTuple, Node inliningTarget, GetObjectArrayNode getObjectArrayNode, PyObjectRecursiveBinaryCheckNode node,
                    int depth) {
        for (Object cls : getObjectArrayNode.execute(inliningTarget, clsTuple)) {
            if (node.executeInternal(frame, arg, cls, depth)) {
                return true;
            }
        }
        return false;
    }

    protected static int getLength(PTuple t) {
        return t.getSequenceStorage().length();
    }

    @Idempotent
    protected static int getNodeRecursionLimit(PythonLanguage language) {
        return language.getEngineOption(PythonOptions.NodeRecursionLimit);
    }
}
