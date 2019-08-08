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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.function.Function;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.NodeContextManager;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithGlobalState;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNodeFactory.DynamicNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@TypeSystemReference(PythonArithmeticTypes.class)
public class CastToIntegerFromIntNode extends Node {

    @Child private Dynamic dynamicNode;

    private final Function<Object, Byte> typeErrorHandler;

    public CastToIntegerFromIntNode(Function<Object, Byte> typeErrorHandler) {
        super();
        this.typeErrorHandler = typeErrorHandler;
    }

    public Object execute(Object x) {
        if (dynamicNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dynamicNode = insert(Dynamic.create());
        }
        return dynamicNode.execute(x, typeErrorHandler);
    }

    public static CastToIntegerFromIntNode create() {
        return new CastToIntegerFromIntNode(null);
    }

    public static CastToIntegerFromIntNode create(Function<Object, Byte> typeErrorHandler) {
        return new CastToIntegerFromIntNode(typeErrorHandler);
    }

    public static final class CastToIntegerContextManager extends NodeContextManager {

        private final Dynamic delegate;

        private CastToIntegerContextManager(Dynamic delegate, PythonContext context, VirtualFrame frame) {
            super(context, frame, delegate);
            this.delegate = delegate;
        }

        public Object execute(Object x) {
            return delegate.execute(x);
        }

        public Object execute(Object x, Function<Object, Byte> typeErrorHandler) {
            return delegate.execute(x, typeErrorHandler);
        }
    }

    @GenerateUncached
    @ImportStatic(MathGuards.class)
    public abstract static class Dynamic extends PNodeWithGlobalState<CastToIntegerContextManager> {

        public abstract Object execute(Object x, Function<Object, Byte> typeErrorHandler);

        public final Object execute(Object x) {
            return execute(x, null);
        }

        @Specialization
        int fromInt(int x, @SuppressWarnings("unused") Function<Object, Byte> typeErrorHandler) {
            return x;
        }

        @Specialization
        long fromLong(long x, @SuppressWarnings("unused") Function<Object, Byte> typeErrorHandler) {
            return x;
        }

        @Specialization
        PInt fromPInt(PInt x, @SuppressWarnings("unused") Function<Object, Byte> typeErrorHandler) {
            return x;
        }

        @Specialization
        long fromDouble(double x, Function<Object, Byte> typeErrorHandler,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (typeErrorHandler != null) {
                return typeErrorHandler.apply(x);
            } else {
                throw raiseNode.raise(TypeError, "'%p' object cannot be interpreted as an integer", x);
            }
        }

        @Specialization(guards = "!isNumber(x)")
        Object fromObject(Object x, Function<Object, Byte> typeErrorHandler,
                        @Cached LookupAndCallUnaryDynamicNode callIndexNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            Object result = callIndexNode.passState().executeObject(x, SpecialMethodNames.__INT__);
            if (result == PNone.NO_VALUE) {
                if (typeErrorHandler != null) {
                    return typeErrorHandler.apply(x);
                } else {
                    throw raiseNode.raise(TypeError, "'%p' object cannot be interpreted as an integer", x);
                }
            }
            if (!PGuards.isInteger(result) && !PGuards.isPInt(result) && !(result instanceof Boolean)) {
                throw raiseNode.raise(TypeError, " __int__ returned non-int (type %p)", result);
            }
            return result;
        }

        @Override
        public CastToIntegerContextManager withGlobalState(ContextReference<PythonContext> contextRef, VirtualFrame frame) {
            return new CastToIntegerContextManager(this, contextRef.get(), frame);
        }

        @Override
        public CastToIntegerContextManager passState() {
            return new CastToIntegerContextManager(this, null, null);
        }

        public static Dynamic create() {
            return DynamicNodeGen.create();
        }

        public static Dynamic getUncached() {
            return DynamicNodeGen.getUncached();
        }
    }

}
