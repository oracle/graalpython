/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_S_NOT_P;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(MathGuards.class)
public abstract class CastToJavaShortNode extends PNodeWithContext {

    public final short executeWithThrowSystemError(Node inliningTarget, Object x, PRaiseNode.Lazy raiseNode) {
        return executeWithThrow(inliningTarget, x, raiseNode, SystemError);
    }

    public final short executeWithThrow(Node inliningTarget, Object x, PRaiseNode.Lazy raiseNode, PythonBuiltinClassType errType) {
        try {
            return execute(inliningTarget, x);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(errType, MUST_BE_S_NOT_P, "a short", x);
        }
    }

    public abstract short execute(Node inliningTarget, short x);

    public abstract short execute(Node inliningTarget, int x);

    public abstract short execute(Node inliningTarget, long x);

    public abstract short execute(Node inliningTarget, Object x);

    @Specialization
    static short fromShort(short x) {
        return x;
    }

    @Specialization(rewriteOn = OverflowException.class)
    static short fromInt(int x) throws OverflowException {
        return PInt.shortValueExact(x);
    }

    @Specialization(rewriteOn = OverflowException.class)
    static short fromLong(long x) throws OverflowException {
        return PInt.shortValueExact(x);
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    static short fromPInt(PInt x) {
        return x.shortValueExact();
    }

    @Specialization(replaces = "fromInt")
    static short fromIntErr(Node inliningTarget, int x,
                    @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
        try {
            return PInt.shortValueExact(x);
        } catch (OverflowException e) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.SHORT_MUST_BE_IN_RANGE);
        }
    }

    @Specialization(replaces = "fromLong")
    static short fromLongErr(Node inliningTarget, long x,
                    @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
        try {
            return PInt.shortValueExact(x);
        } catch (OverflowException e) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.SHORT_MUST_BE_IN_RANGE);
        }
    }

    @Specialization(replaces = "fromPInt")
    static short fromPIntErr(Node inliningTarget, PInt x,
                    @Shared("raiseNode") @Cached PRaiseNode.Lazy raiseNode) {
        try {
            return x.shortValueExact();
        } catch (ArithmeticException e) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.SHORT_MUST_BE_IN_RANGE);
        }
    }
}
