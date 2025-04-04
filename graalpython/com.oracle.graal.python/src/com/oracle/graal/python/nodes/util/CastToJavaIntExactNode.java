/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_S_NOT_P;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Casts a Python integer to a Java int without coercion. <b>ATTENTION:</b> If the cast isn't
 * possible, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class CastToJavaIntExactNode extends CastToJavaIntNode {

    public final int executeWithThrowSystemError(Node inliningTarget, Object x, PRaiseNode raiseNode) {
        return executeWithThrow(inliningTarget, x, raiseNode, SystemError);
    }

    public final int executeWithThrow(Node inliningTarget, Object x, PRaiseNode raiseNode, PythonBuiltinClassType errType) {
        try {
            return execute(inliningTarget, x);
        } catch (CannotCastException cce) {
            throw raiseNode.raise(inliningTarget, errType, MUST_BE_S_NOT_P, "an int", x);
        }
    }

    public static int executeUncached(long x) {
        return CastToJavaIntExactNodeGen.getUncached().execute(null, x);
    }

    public static int executeUncached(Object x) {
        return CastToJavaIntExactNodeGen.getUncached().execute(null, x);
    }

    public final int executeCached(long x) {
        return execute(this, x);
    }

    public final int executeCached(Object x) {
        return execute(this, x);
    }

    @NeverDefault
    public static CastToJavaIntExactNode create() {
        return CastToJavaIntExactNodeGen.create();
    }

    @NeverDefault
    public static CastToJavaIntExactNode getUncached() {
        return CastToJavaIntExactNodeGen.getUncached();
    }

    @Specialization(rewriteOn = OverflowException.class)
    static int longToInt(long x) throws OverflowException {
        return PInt.intValueExact(x);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static int pIntToInt(PInt x) throws OverflowException {
        return x.intValueExact();
    }

    @Specialization(replaces = "longToInt")
    static int longToIntOverflow(Node inliningTarget, long x,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        try {
            return PInt.intValueExact(x);
        } catch (OverflowException e) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
        }
    }

    @Specialization(replaces = "pIntToInt")
    static int pIntToIntOverflow(Node inliningTarget, PInt x,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        try {
            return x.intValueExact();
        } catch (OverflowException e) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
        }
    }
}
