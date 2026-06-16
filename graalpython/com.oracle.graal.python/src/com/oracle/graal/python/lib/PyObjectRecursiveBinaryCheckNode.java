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
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
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
    public final boolean execute(Frame frame, Object arg, Object classinfo) {
        return executeInternal(frame, arg, classinfo, 0);
    }

    protected abstract boolean executeInternal(Frame frame, Object arg, Object classinfo, int depth);

    @NeverDefault
    abstract PyObjectRecursiveBinaryCheckNode createRecursive();

    abstract PyObjectRecursiveBinaryCheckNode getUncachedRecursive();

    @Specialization(guards = {"depth < getNodeRecursionLimit(language)", "isTuple(clsTuple)"}, excludeForUncached = true)
    static boolean doRecursiveWithNode(VirtualFrame frame, Object arg, Object clsTuple, int depth,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Bind PythonLanguage language,
                    @Cached TupleNodes.GetTupleStorage getTupleStorage,
                    @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                    @Cached("createRecursive()") PyObjectRecursiveBinaryCheckNode recursiveNode) {
        return loopRecursive(frame, arg, clsTuple, inliningTarget, getTupleStorage, toArrayNode, recursiveNode, depth + 1);
    }

    @SuppressWarnings("truffle-static-method")
    @Specialization(guards = {"depth >= getNodeRecursionLimit(language)", "isTuple(clsTuple)"}, excludeForUncached = true)
    boolean doRecursiveTransition(VirtualFrame frame, Object arg, Object clsTuple, @SuppressWarnings("unused") int depth,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Bind PythonLanguage language,
                    @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                    @Cached TupleNodes.GetTupleStorage getTupleStorage,
                    @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
        Object state = BoundaryCallContext.enter(frame, boundaryCallData);
        try {
            // Note: we need actual recursion to trigger the stack overflow error like CPython.
            return callRecursiveWithNodeTruffleBoundary(inliningTarget, arg, clsTuple, getTupleStorage, toArrayNode);
        } finally {
            BoundaryCallContext.exit(frame, boundaryCallData, state);
        }
    }

    @SuppressWarnings("truffle-static-method")
    @Specialization(guards = "isTuple(clsTuple)")
    boolean doRecursiveUncached(VirtualFrame frame, Object arg, Object clsTuple, @SuppressWarnings("unused") int depth,
                    @Bind Node inliningTarget,
                    @Cached TupleNodes.GetTupleStorage getTupleStorage,
                    @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
        assert this instanceof UnadoptableNode;
        return loopRecursive(frame, arg, clsTuple, inliningTarget, getTupleStorage, toArrayNode, this, -1);
    }

    @TruffleBoundary
    private boolean callRecursiveWithNodeTruffleBoundary(Node inliningTarget, Object arg, Object clsTuple, TupleNodes.GetTupleStorage getTupleStorage,
                    SequenceStorageNodes.ToArrayNode toArrayNode) {
        return loopRecursive(null, arg, clsTuple, inliningTarget, getTupleStorage, toArrayNode, getUncachedRecursive(), -1);
    }

    private static boolean loopRecursive(VirtualFrame frame, Object arg, Object clsTuple, Node inliningTarget, TupleNodes.GetTupleStorage getTupleStorage,
                    SequenceStorageNodes.ToArrayNode toArrayNode, PyObjectRecursiveBinaryCheckNode node, int depth) {
        for (Object cls : getTupleArray(inliningTarget, clsTuple, getTupleStorage, toArrayNode)) {
            if (node.executeInternal(frame, arg, cls, depth)) {
                return true;
            }
        }
        return false;
    }

    private static Object[] getTupleArray(Node inliningTarget, Object clsTuple, TupleNodes.GetTupleStorage getTupleStorage, SequenceStorageNodes.ToArrayNode toArrayNode) {
        return toArrayNode.execute(inliningTarget, getTupleStorage.execute(inliningTarget, clsTuple));
    }

    @Idempotent
    protected static int getNodeRecursionLimit(PythonLanguage language) {
        return language.getEngineOption(PythonOptions.NodeRecursionLimit);
    }
}
