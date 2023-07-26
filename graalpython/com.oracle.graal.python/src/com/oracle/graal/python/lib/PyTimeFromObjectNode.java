/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_VALUE_NAN;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_LARGE_TO_CONVERT_TO;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of {@code _PyTime_FromObject} from CPython.
 */
@TypeSystemReference(PythonArithmeticTypes.class)
@GenerateInline
@GenerateCached(false)
public abstract class PyTimeFromObjectNode extends PNodeWithContext {

    public enum RoundType {
        FLOOR,
        CEILING,
        ROUND_UP,
        TIMEOUT     // alias for ROUND_UP
    }

    public abstract long execute(VirtualFrame frame, Node inliningTarget, Object obj, RoundType round, long unitToNs);

    @Specialization
    static long doDouble(Node inliningTarget, double d, RoundType round, long unitToNs,
                    @Shared @Cached PRaiseNode.Lazy raiseNode) {
        // Implements _PyTime_FromDouble, rounding mode (HALF_UP) is hard-coded for now
        if (Double.isNaN(d)) {
            throw raiseNode.get(inliningTarget).raise(ValueError, INVALID_VALUE_NAN);
        }

        double value = d * unitToNs;
        switch (round) {
            case FLOOR:
                value = Math.floor(value);
                break;
            case CEILING:
                value = Math.ceil(value);
                break;
            case TIMEOUT:
            case ROUND_UP:
                value = value >= 0.0 ? Math.ceil(value) : Math.floor(value);
                break;
        }
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw raiseTimeOverflow(raiseNode.get(inliningTarget));
        }
        return (long) value;
    }

    @Specialization
    static long doLong(Node inliningTarget, long l, @SuppressWarnings("unused") RoundType round, long unitToNs,
                    @Shared @Cached PRaiseNode.Lazy raiseNode) {
        try {
            return PythonUtils.multiplyExact(l, unitToNs);
        } catch (OverflowException e) {
            throw raiseTimeOverflow(raiseNode.get(inliningTarget));
        }
    }

    @Specialization
    static long doOther(VirtualFrame frame, Node inliningTarget, Object value, RoundType round, long unitToNs,
                    @Cached CastToJavaDoubleNode castToDouble,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                    @Cached PyLongAsLongAndOverflowNode asLongNode) {
        try {
            return doDouble(inliningTarget, castToDouble.execute(inliningTarget, value), round, unitToNs, raiseNode);
        } catch (CannotCastException e) {
            try {
                return doLong(inliningTarget, asLongNode.execute(frame, inliningTarget, value), round, unitToNs, raiseNode);
            } catch (OverflowException e1) {
                throw raiseTimeOverflow(raiseNode.get(inliningTarget));
            }
        }
    }

    private static PException raiseTimeOverflow(PRaiseNode raise) {
        throw raise.raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT_TO, "timestamp", "long");
    }
}
