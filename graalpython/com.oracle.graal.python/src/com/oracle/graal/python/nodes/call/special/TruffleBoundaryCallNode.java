/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.BinaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.TernaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.UnaryBuiltinInfo;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class TruffleBoundaryCallNode extends Node implements ReplaceObserver, IndirectCallNode {
    private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
    private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

    @Override
    public final Assumption needNotPassFrameAssumption() {
        return dontNeedCallerFrame;
    }

    @Override
    public final Assumption needNotPassExceptionAssumption() {
        return dontNeedExceptionState;
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        // Any node replacements below this node do not invalidate the compilation
        return true;
    }

    public abstract static class Unary extends TruffleBoundaryCallNode {
        public abstract Object execute(VirtualFrame frame, PythonContext ctx, UnaryBuiltinInfo info, Object arg1);

        @TruffleBoundary
        protected final Object call(UnaryBuiltinInfo info, Object arg1) {
            return insert(info.createNode()).call(null, arg1);
        }

        public static Unary create() {
            return new UnaryCached();
        }

        private static final UnaryUncached UNCACHED = new UnaryUncached();

        public static Unary getUncached() {
            return UNCACHED;
        }
    }

    private static final class UnaryUncached extends Unary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, UnaryBuiltinInfo info, Object arg1) {
            return call(info, arg1);
        }
    }

    private static final class UnaryCached extends Unary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, UnaryBuiltinInfo info, Object arg1) {
            Object state = IndirectCallContext.enter(frame, ctx, this);
            try {
                return call(info, arg1);
            } finally {
                IndirectCallContext.exit(frame, ctx, state);
            }
        }
    }

    public abstract static class Binary extends TruffleBoundaryCallNode {
        public abstract Object execute(VirtualFrame frame, PythonContext ctx, BinaryBuiltinInfo info, Object arg1, Object arg2);

        @TruffleBoundary
        protected final Object call(BinaryBuiltinInfo info, Object arg1, Object arg2) {
            return insert(info.createNode()).call(null, arg1, arg2);
        }

        public static Binary create() {
            return new BinaryCached();
        }

        private static final BinaryUncached UNCACHED = new BinaryUncached();

        public static Binary getUncached() {
            return UNCACHED;
        }
    }

    private static final class BinaryUncached extends Binary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, BinaryBuiltinInfo info, Object arg1, Object arg2) {
            return call(info, arg1, arg2);
        }
    }

    private static final class BinaryCached extends Binary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, BinaryBuiltinInfo info, Object arg1, Object arg2) {
            Object state = IndirectCallContext.enter(frame, ctx, this);
            try {
                return call(info, arg1, arg2);
            } finally {
                IndirectCallContext.exit(frame, ctx, state);
            }
        }
    }

    public abstract static class Ternary extends TruffleBoundaryCallNode {
        public abstract Object execute(VirtualFrame frame, PythonContext ctx, TernaryBuiltinInfo info, Object arg1, Object arg2, Object arg3);

        @TruffleBoundary
        protected final Object call(TernaryBuiltinInfo info, Object arg1, Object arg2, Object arg3) {
            return insert(info.createNode()).call(null, arg1, arg2, arg3);
        }

        public static Ternary create() {
            return new TernaryCached();
        }

        private static final TernaryUncached UNCACHED = new TernaryUncached();

        public static Ternary getUncached() {
            return UNCACHED;
        }
    }

    private static final class TernaryUncached extends Ternary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, TernaryBuiltinInfo info, Object arg1, Object arg2, Object arg3) {
            return call(info, arg1, arg2, arg3);
        }
    }

    private static final class TernaryCached extends Ternary {
        @Override
        public Object execute(VirtualFrame frame, PythonContext ctx, TernaryBuiltinInfo info, Object arg1, Object arg2, Object arg3) {
            Object state = IndirectCallContext.enter(frame, ctx, this);
            try {
                return call(info, arg1, arg2, arg3);
            } finally {
                IndirectCallContext.exit(frame, ctx, state);
            }
        }
    }
}
