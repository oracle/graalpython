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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.function.Function;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNodeFactory.CachedNodeGen;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

/**
 * Converts an arbitrary object to an index-sized integer (which is a Java {@code int}).
 */
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class CastToIndexNode extends PNodeWithContext {

    private static final UncachedNode UNCACHED = new UncachedNode();

    private static final String ERROR_MESSAGE = "cannot fit 'int' into an index-sized integer";

    public abstract int execute(Object x);

    public abstract int execute(int x);

    public abstract int execute(long x);

    public abstract int execute(boolean x);

    abstract static class CachedNode extends CastToIndexNode {

        @Child private LookupAndCallUnaryNode callIndexNode;
        @Child private CastToIndexNode recursiveNode;
        @Child private PRaiseNode raiseNode;

        private final PythonBuiltinClassType errorType;
        private final boolean recursive;
        private final Function<Object, Integer> typeErrorHandler;

        protected CachedNode(PythonBuiltinClassType errorType, boolean recursive, Function<Object, Integer> typeErrorHandler) {
            this.errorType = errorType;
            this.recursive = recursive;
            this.typeErrorHandler = typeErrorHandler;
        }

        private PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
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
        int doLongOvf(long x) {
            try {
                return PInt.intValueExact(x);
            } catch (ArithmeticException e) {
                throw getRaiseNode().raise(errorType, ERROR_MESSAGE);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt x) {
            return x.intValueExact();
        }

        @Specialization(replaces = "doLong")
        int doPIntOvf(PInt x) {
            try {
                return x.intValueExact();
            } catch (ArithmeticException e) {
                throw getRaiseNode().raise(errorType, ERROR_MESSAGE);
            }
        }

        @Specialization
        public int toInt(double x) {
            return handleError("'%p' object cannot be interpreted as an integer", x);
        }

        @Fallback
        int doGeneric(Object x) {
            if (recursive) {
                if (callIndexNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callIndexNode = insert(LookupAndCallUnaryNode.create(__INDEX__));
                }
                // note: it's fine to pass 'null' because this node has an uncached version an so
                // the caller must already take care of the global state
                Object result = callIndexNode.executeObject(null, x);
                if (result == PNone.NO_VALUE) {
                    return handleError("'%p' object cannot be interpreted as an integer", x);
                }
                if (recursiveNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    recursiveNode = insert(CachedNodeGen.create(errorType, false, typeErrorHandler));
                }
                return recursiveNode.execute(result);
            }
            return handleError("__index__ returned non-int (type %p)", x);
        }

        private int handleError(String fmt, Object x) {
            if (typeErrorHandler != null) {
                return typeErrorHandler.apply(x);
            }
            throw getRaiseNode().raise(TypeError, fmt, x);
        }
    }

    private static final class UncachedNode extends CastToIndexNode {

        @Override
        @TruffleBoundary
        public int execute(Object x) {
            if (x instanceof Integer) {
                return execute((int) x);
            } else if (x instanceof Long) {
                return execute((long) x);
            } else if (x instanceof Boolean) {
                return execute((boolean) x);
            } else {
                // NOTE: since this is an uncached node, any of the callers must already have taken
                // care of the exception state
                Object result = LookupAndCallUnaryDynamicNode.getUncached().executeObject(x, __INDEX__);
                if (result == PNone.NO_VALUE) {
                    throw PRaiseNode.getUncached().raise(TypeError, "'%p' object cannot be interpreted as an integer", x);
                }
                return execute(result);
            }
        }

        @Override
        public int execute(int x) {
            return x;
        }

        @Override
        @TruffleBoundary
        public int execute(long x) {
            try {
                return PInt.intValueExact(x);
            } catch (ArithmeticException e) {
                throw PRaiseNode.getUncached().raise(TypeError, ERROR_MESSAGE);
            }
        }

        @Override
        public int execute(boolean x) {
            return PInt.intValue(x);
        }

    }

    public static CastToIndexNode create() {
        return CachedNodeGen.create(IndexError, true, null);
    }

    public static CastToIndexNode createOverflow() {
        return CachedNodeGen.create(OverflowError, true, null);
    }

    public static CastToIndexNode create(PythonBuiltinClassType errorType, Function<Object, Integer> typeErrorHandler) {
        return CachedNodeGen.create(errorType, true, typeErrorHandler);
    }

    public static CastToIndexNode getUncached() {
        return UNCACHED;
    }
}
