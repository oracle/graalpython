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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_S_NOT_P;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Casts a Python integer to a Java long without coercion. <b>ATTENTION:</b> If the cast isn't
 * possible, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class CastToJavaLongExactNode extends CastToJavaLongNode {

    public final long executeWithThrowSystemError(Node inliningTarget, Object x, PRaiseNode.Lazy raiseNode) {
        return executeWithThrow(inliningTarget, x, raiseNode, SystemError);
    }

    public final long executeWithThrow(Node inliningTarget, Object x, PRaiseNode.Lazy raiseNode, PythonBuiltinClassType errType) {
        try {
            return execute(inliningTarget, x);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(errType, MUST_BE_S_NOT_P, "a long", x);
        }
    }

    public static long executeUncached(Object x) throws CannotCastException {
        return CastToJavaLongExactNodeGen.getUncached().execute(null, x);
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected static long toLongNoOverflow(PInt x) throws OverflowException {
        return x.longValueExact();
    }

    @Specialization(replaces = "toLongNoOverflow")
    protected static long toLong(Node inliningTarget, PInt x,
                    @Cached PRaiseNode.Lazy raiseNode) {
        try {
            return x.longValueExact();
        } catch (OverflowException e) {
            throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "long");
        }
    }
}
