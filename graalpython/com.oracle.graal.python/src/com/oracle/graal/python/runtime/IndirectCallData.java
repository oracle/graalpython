/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;

/**
 * Classes {@link InteropCallData} and {@link BoundaryCallData} mark a point where we transition
 * from code with access to the Python Truffle virtual frame to code to which we cannot pass the
 * virtual frame (e.g., TruffleBoundary, or Truffle interop call). Those should be passed to
 * {@link com.oracle.graal.python.runtime.ExecutionContext.InteropCallContext} or
 * {@link com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext}.
 * <p>
 * These classes hold assumptions that let us avoid storing the current exception state and PFrame
 * into the thread state unless some callee down the stack asked for it.
 * <p>
 * During a Truffle stack walk, we need to be able to traverse the AST from the "call node" to the
 * indirect call data. It would be cumbersome if all the nodes that need to do the transition would
 * have to implement some interface with getters/setters for the assumptions, so instead the
 * indirect call data objects are their children. This means that we need a mapping from the nodes
 * that do the transition to their indirect call data and this is kept in {@link PythonLanguage}.
 */
public abstract class IndirectCallData {

    /**
     * For given {@link Node} retried as "call node" during stack walk, finds the corresponding
     * {@link BoundaryCallData} and if it exists sets the caller flags on it.
     */
    public static void setCallerFlagsOnIndirectCallData(final Node callNode, int callerFlags) {
        // If the call was made from non-Python frame, i.e., code without access to its
        // current Truffle virtual frame, e.g., TruffleBoundary code, we must find the point
        // where the transition from Python frame to non-Python frame was made and
        // invalidate the "don't pass caller frame" assumption there, so next time we avoid
        // this stack walk. Such transition is marked with IndirectCallData node or a node
        // associated with IndirectCallData.
        if (callNode instanceof BoundaryCallData boundaryCallData) {
            // Situation where Python itself set a BoundaryCallData as EncapsulatingNodeReference
            boundaryCallData.updateCallerFlags(callerFlags);
        }
        // In any case we need to traverse the AST to find InteropCallData and BoundaryCallData in
        // case the EncapsulatingNodeReference was overridden.
        Node pythonCallNode = callNode;
        while (pythonCallNode != null) {
            InteropCallData interopCallData = PythonLanguage.lookupInteropCallData(pythonCallNode);
            if (interopCallData != null) {
                interopCallData.updateCallerFlags(callerFlags);
            }
            BoundaryCallData boundaryCallData = PythonLanguage.lookupBoundaryCallData(pythonCallNode);
            if (boundaryCallData != null) {
                boundaryCallData.updateCallerFlags(callerFlags);
            }
            pythonCallNode = pythonCallNode.getParent();
        }
    }

    public static class CallerFlagsAssumptionSet {
        @CompilationFinal private volatile int callerFlags;
        @CompilationFinal private volatile Assumption callerFlagsAssumption = createAssumption();

        private static final CallerFlagsAssumptionSet UNCACHED = new CallerFlagsAssumptionSet();
        static {
            UNCACHED.callerFlags = ~0;
        }

        public CallerFlagsAssumptionSet() {
        }

        public boolean isUncached() {
            return this == UNCACHED;
        }

        /**
         * Returns {@link CallerFlags} representing what this call site needs to pass in the context
         * to the calees within the scope of the indirect call
         */
        public int getCallerFlags() {
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this) && !callerFlagsAssumption.isValid()) {
                /*
                 * We create a new assumption after each invalidation, we shouldn't normally get
                 * here except when the compilation races with the assumption resetting
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return callerFlags;
        }

        @TruffleBoundary
        public void updateCallerFlags(int newFlags) {
            if ((newFlags & CallerFlags.NEEDS_LOCALS) != 0) {
                newFlags |= CallerFlags.NEEDS_PFRAME;
            }
            if ((newFlags & CallerFlags.NEEDS_PFRAME) != 0) {
                newFlags |= CallerFlags.NEEDS_FRAME_REFERENCE;
            }
            if ((callerFlags & newFlags) != newFlags) {
                synchronized (this) {
                    if ((callerFlags & newFlags) != newFlags) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        callerFlagsAssumption.invalidate("caller flags changed");
                        callerFlags |= newFlags;
                        callerFlagsAssumption = createAssumption();
                    }
                }
            }
        }

        private static Assumption createAssumption() {
            return Truffle.getRuntime().createAssumption("caller flags");
        }
    }

    /**
     * Truffle interop overrides the {@link com.oracle.truffle.api.nodes.EncapsulatingNodeReference}
     * with its own node when doing transition to uncached, or it does not transition to uncached at
     * all and makes a cached call. In either case, during a stack walk the "location" will be some
     * interop AST node connected to our AST.
     * <p>
     * This scheme is used also for our interop buffer Truffle libraries.
     */
    public static final class InteropCallData {
        @CompilationFinal private CallerFlagsAssumptionSet callerFlagsAssumptionSet = new CallerFlagsAssumptionSet();

        private static final InteropCallData UNCACHED = new InteropCallData();

        static {
            UNCACHED.callerFlagsAssumptionSet = CallerFlagsAssumptionSet.UNCACHED;
        }

        public boolean isUncached() {
            return this == UNCACHED;
        }

        public int getCallerFlags() {
            return callerFlagsAssumptionSet.getCallerFlags();
        }

        public void updateCallerFlags(int callerFlags) {
            callerFlagsAssumptionSet.updateCallerFlags(callerFlags);
        }

        @NeverDefault
        public static InteropCallData createFor(Node node) {
            return PythonLanguage.createInteropCallData(node);
        }

        @NeverDefault
        public static InteropCallData getUncached() {
            return UNCACHED;
        }
    }

    /**
     * This node is set as {@link com.oracle.truffle.api.nodes.EncapsulatingNodeReference#set(Node)}
     * before the transition, so that when we do Truffle stack walk or materialize a Truffle
     * exception we can access it as the "location" unless someone else overridden the location, for
     * example, if the code behind TruffleBoundary uses cached nodes to make a call. For this
     * situation we still need to maintain a mapping of this node and its parent like for
     * {@link InteropCallData}.
     */
    public static final class BoundaryCallData extends Node {

        @CompilationFinal private CallerFlagsAssumptionSet callerFlagsAssumptionSet = new CallerFlagsAssumptionSet();

        private static final BoundaryCallData UNCACHED = new BoundaryCallData();

        static {
            UNCACHED.callerFlagsAssumptionSet = CallerFlagsAssumptionSet.UNCACHED;
        }

        public boolean isUncached() {
            return this == UNCACHED;
        }

        public int getCallerFlags() {
            return callerFlagsAssumptionSet.getCallerFlags();
        }

        public void updateCallerFlags(int callerFlags) {
            callerFlagsAssumptionSet.updateCallerFlags(callerFlags);
        }

        @NeverDefault
        public static BoundaryCallData createFor(Node node) {
            return PythonLanguage.createBoundaryCallData(node);
        }

        @NeverDefault
        public static BoundaryCallData getUncached() {
            return UNCACHED;
        }
    }
}
