/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(PGuards.class)
public abstract class CoerceToComplexNode extends PNodeWithContext {

    public abstract PComplex execute(VirtualFrame frame, Node inliningTarget, Object x);

    @Specialization
    static PComplex doLong(long x,
                    @Shared @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createComplex(x, 0);
    }

    @Specialization
    static PComplex doDouble(double x,
                    @Shared @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createComplex(x, 0);
    }

    @Specialization
    static PComplex doComplex(PComplex x) {
        return x;
    }

    @Specialization(guards = "!isPComplex(x)")
    static PComplex toComplex(VirtualFrame frame, Node inliningTarget, Object x,
                    @Cached(value = "create(T___COMPLEX__)", inline = false) LookupAndCallUnaryNode callComplexFunc,
                    @Cached PyFloatAsDoubleNode asDoubleNode,
                    @Shared @Cached(inline = false) PythonObjectFactory factory,
                    @Cached PRaiseNode.Lazy raiseNode) {
        Object result = callComplexFunc.executeObject(frame, x);
        if (result != PNone.NO_VALUE) {
            if (result instanceof PComplex) {
                // TODO we need pass here deprecation warning
                // DeprecationWarning: __complex__ returned non-complex (type %p).
                // The ability to return an instance of a strict subclass of complex is
                // deprecated,
                // and may be removed in a future version of Python.
                return (PComplex) result;
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.SHOULD_RETURN, "__complex__", "complex object");
            }
        }
        return factory.createComplex(asDoubleNode.execute(frame, inliningTarget, x), 0);
    }
}
