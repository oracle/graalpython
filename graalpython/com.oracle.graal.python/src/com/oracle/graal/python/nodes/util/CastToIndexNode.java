/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.function.Function;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Converts an arbitrary object to an index-sized integer (which is a Java {@code int}).
 *
 * There are convenience execute methods for primitive types that do not need a
 * frame.  Note that frame can be {@code null} if you're certain that the frame
 * was either attached to the context, because this node was reached through an
 * indirect call, or you know the stack won't be needed.
 */
@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic({SpecialMethodNames.class, PGuards.class})
@GenerateUncached
public abstract class CastToIndexNode extends PNodeWithContext {
    private static final String ERROR_MESSAGE = "cannot fit 'int' into an index-sized integer";

    public abstract int execute(Frame frame, Object x);

    public abstract int execute(Frame frame, int x);

    public final int execute(int x) {
        return execute(null, x);
    }

    public abstract int execute(Frame frame, long x);

    public final int execute(long x) {
        return execute(null, x);
    }

    public abstract int execute(Frame frame, boolean x);

    public final int execute(boolean x) {
        return execute(null, x);
    }

    @Specialization
    int doBoolean(boolean x) {
        return PInt.intValue(x);
    }

    @Specialization
    int doInt(int x) {
        return x;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    int doLong(long x) {
        return PInt.intValueExact(x);
    }

    @Specialization(replaces = "doLong")
    int doLongOvf(long x,
                    @Cached PRaiseNode raiseNode) {
        try {
            return PInt.intValueExact(x);
        } catch (ArithmeticException e) {
            throw raiseNode.raise(errorType(), ERROR_MESSAGE);
        }
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    int doPInt(PInt x) {
        return x.intValueExact();
    }

    @Specialization(replaces = "doPInt")
    int doPIntOvf(PInt x,
                  @Cached PRaiseNode raiseNode) {
        try {
            return x.intValueExact();
        } catch (ArithmeticException e) {
            throw raiseNode.raise(errorType(), ERROR_MESSAGE);
        }
    }

    @Specialization
    public int toInt(double x,
                    @Cached PRaiseNode raiseNode) {
        return handleError("'%p' object cannot be interpreted as an integer", x, raiseNode);
    }

    protected LookupAndCallUnaryNode uncachedDirectCall() {
        return null;
    }

    protected LookupAndCallUnaryDynamicNode cachedDynamicCall() {
        return null;
    }

    @Specialization(guards = {"!isBoolean(x)", "!isInteger(x)", "!isDouble(x)"})
    int doGeneric(Frame frame, Object x,
                    @Cached(value = "create(__INDEX__)", uncached = "uncachedDirectCall()") LookupAndCallUnaryNode callIndexNode,
                    @Cached(value = "cachedDynamicCall()", uncached = "getUncached()") LookupAndCallUnaryDynamicNode callIndexUncachedNode,
                    @Cached("createNonRecursive()") CastToIndexNode recursiveNode,
                    @Cached PRaiseNode raiseNode) {
        Object result;
        if (callIndexNode != null) {
            assert frame instanceof VirtualFrame : "cannot use the cached version with a non-virtual frame";
            result = callIndexNode.executeObject((VirtualFrame) frame, x);
        } else {
            result = callIndexUncachedNode.executeObject(x, SpecialMethodNames.__INDEX__);
        }
        if (result == PNone.NO_VALUE) {
            return handleError("'%p' object cannot be interpreted as an integer", x, raiseNode);
        }
        if (isRecursive()) {
            return recursiveNode.execute(frame, result);
        }
        return handleError("__index__ returned non-int (type %p)", x, raiseNode);
    }

    protected int handleError(String fmt, Object x, PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, fmt, x);
    }

    protected PythonBuiltinClassType errorType() {
        return IndexError;
    }

    protected boolean isRecursive() {
        return true;
    }

    protected Function<Object, Integer> typeErrorHandler() {
        return null;
    }

    protected CastToIndexNode createNonRecursive() {
        return CastToIndexNodeGen.CastToIndexNodeNotRecursiveNodeGen.create();
    }

    protected CastToIndexNode getNonRecursiveUncached() {
        return CastToIndexNodeGen.CastToIndexNodeNotRecursiveNodeGen.getUncached();
    }

    @GenerateUncached
    protected abstract static class CastToIndexNodeNotRecursive extends CastToIndexNode {
        @Override
        protected boolean isRecursive() {
            return false;
        }
    }

    @GenerateUncached
    protected abstract static class CastToIndexWithOverflowNode extends CastToIndexNode {
        @Override
        protected PythonBuiltinClassType errorType() {
            return OverflowError;
        }

        @Override
        protected CastToIndexNode createNonRecursive() {
            return CastToIndexNodeGen.CastToIndexWithOverflowNodeGen.CastToIndexWithOverflowNodeNotRecursiveNodeGen.create();
        }

        @Override
        protected CastToIndexNode getNonRecursiveUncached() {
            return CastToIndexNodeGen.CastToIndexWithOverflowNodeGen.CastToIndexWithOverflowNodeNotRecursiveNodeGen.getUncached();
        }

        @GenerateUncached
        protected abstract static class CastToIndexWithOverflowNodeNotRecursive extends CastToIndexWithOverflowNode {
            @Override
            protected boolean isRecursive() {
                return false;
            }
        }
    }

    @GenerateUncached
    protected abstract static class CastToIndexIgnoringResult extends CastToIndexNode {
        @Override
        protected PythonBuiltinClassType errorType() {
            return TypeError;
        }

        @Override
        protected int handleError(String fmt, Object x, PRaiseNode raiseNode) {
            return 0;
        }

        @Override
        protected CastToIndexNode createNonRecursive() {
            return CastToIndexNodeGen.CastToIndexIgnoringResultNodeGen.create();
        }

        @Override
        protected CastToIndexNode getNonRecursiveUncached() {
            return CastToIndexNodeGen.CastToIndexIgnoringResultNodeGen.getUncached();
        }

        @GenerateUncached
        protected abstract static class CastToIndexIgnoringResultNotRecursive extends CastToIndexIgnoringResult {
            @Override
            protected boolean isRecursive() {
                return false;
            }
        }
    }

    @GenerateUncached
    protected abstract static class CastToSliceIndex extends CastToIndexNode {
        @Override
        protected int handleError(String fmt, Object x, PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, "slice indices must be integers or None or have an __index__ method");
        }

        @Override
        protected CastToIndexNode createNonRecursive() {
            return CastToIndexNodeGen.CastToSliceIndexNodeGen.CastToSliceIndexNotRecursiveNodeGen.create();
        }

        @Override
        protected CastToIndexNode getNonRecursiveUncached() {
            return CastToIndexNodeGen.CastToSliceIndexNodeGen.CastToSliceIndexNotRecursiveNodeGen.getUncached();
        }

        @GenerateUncached
        protected abstract static class CastToSliceIndexNotRecursive extends CastToSliceIndex {
            @Override
            protected boolean isRecursive() {
                return false;
            }
        }
    }

    public static CastToIndexNode create() {
        return CastToIndexNodeGen.create();
    }

    public static CastToIndexNode createOverflow() {
        return CastToIndexNodeGen.CastToIndexWithOverflowNodeGen.create();
    }

    public static CastToIndexNode createIgnoringResult() {
        return CastToIndexNodeGen.CastToIndexIgnoringResultNodeGen.create();
    }

    public static CastToIndexNode createForSlice() {
        return CastToIndexNodeGen.CastToSliceIndexNodeGen.create();
    }

    public static CastToIndexNode getUncached() {
        return CastToIndexNodeGen.getUncached();
    }
}
