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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.NodeContextManager;
import com.oracle.graal.python.nodes.PNodeWithGlobalState;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongNodeGen.CastToJavaLongExactNodeGen;
import com.oracle.graal.python.nodes.util.CastToJavaLongNodeGen.CastToJavaLongLossyNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class CastToJavaLongNode extends PNodeWithGlobalState<NodeContextManager> {

    public abstract long execute(Object x);

    protected long toLongInternal(@SuppressWarnings("unused") PInt x) {
        throw new IllegalStateException("should not be reached");
    }

    public static CastToJavaLongNode create() {
        return CastToJavaLongExactNodeGen.create();
    }

    public static CastToJavaLongNode createLossy() {
        return CastToJavaLongLossyNodeGen.create();
    }

    public static CastToJavaLongNode getUncached() {
        return CastToJavaLongExactNodeGen.getUncached();
    }

    public static CastToJavaLongNode createLossyUncached() {
        return CastToJavaLongLossyNodeGen.getUncached();
    }

    @Specialization
    public long toLong(long x) {
        return x;
    }

    @Specialization
    public long toLong(PInt x) {
        return toLongInternal(x);
    }

    @Specialization(guards = "!isNumber(x)")
    public long toLong(Object x,
                    @Cached PRaiseNode raise,
                    @Cached LookupAndCallUnaryDynamicNode callIntNode) {
        Object result = callIntNode.passState().executeObject(x, __INT__);
        if (result == PNone.NO_VALUE) {
            throw raise.raise(TypeError, "must be numeric, not %p", x);
        }
        if (result instanceof PInt) {
            return toLongInternal((PInt) result);
        }
        if (result instanceof Integer) {
            return ((Integer) result).longValue();
        }
        if (result instanceof Long) {
            return (long) result;
        }
        throw raise.raise(TypeError, "%p.__int__ returned a non long (type %p)", x, result);
    }

    public static final class CastToJavaLongContextManager extends NodeContextManager {

        private final CastToJavaLongNode delegate;

        private CastToJavaLongContextManager(CastToJavaLongNode delegate, PythonContext context, VirtualFrame frame) {
            super(context, frame, delegate);
            this.delegate = delegate;
        }

        public Object execute(Object x) {
            return delegate.execute(x);
        }
    }

    @Override
    public CastToJavaLongContextManager withGlobalState(ContextReference<PythonContext> contextRef, VirtualFrame frame) {
        return new CastToJavaLongContextManager(this, contextRef.get(), frame);
    }

    @Override
    public CastToJavaLongContextManager passState() {
        return new CastToJavaLongContextManager(this, null, null);
    }

    @GenerateUncached
    abstract static class CastToJavaLongLossyNode extends CastToJavaLongNode {
        @Override
        protected long toLongInternal(PInt x) {
            return x.longValue();
        }
    }

    @GenerateUncached
    abstract static class CastToJavaLongExactNode extends CastToJavaLongNode {
        @Override
        protected long toLongInternal(PInt x) {
            try {
                return x.longValueExact();
            } catch (ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.getUncached().raise(TypeError, "%s cannot be interpreted as long (type %p)", x);
            }
        }
    }
}
