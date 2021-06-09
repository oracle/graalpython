/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of {@code _PyTime_FromObject} from CPython.
 */
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class PyTimeFromObjectNode extends PNodeWithRaise {

    public abstract long execute(VirtualFrame frame, Object obj, long unitToNs);

    @Specialization
    long doDouble(double d, long unitToNs) {
        // Implements _PyTime_FromDouble, rounding mode (HALF_UP) is hard-coded for now
        if (Double.isNaN(d)) {
            throw raise(ValueError, INVALID_VALUE_NAN);
        }
        double value = d * unitToNs;
        value = value >= 0.0 ? Math.ceil(value) : Math.floor(value);
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw raiseTimeOverflow();
        }
        return (long) value;
    }

    @Specialization
    long doLong(long l, long unitToNs) {
        try {
            return PythonUtils.multiplyExact(l, unitToNs);
        } catch (OverflowException e) {
            throw raiseTimeOverflow();
        }
    }

    @Specialization
    long doOther(VirtualFrame frame, Object value, long unitToNs,
                    @Cached CastToJavaDoubleNode castToDouble,
                    @Cached PyLongAsLongAndOverflowNode asLongNode) {
        try {
            return doDouble(castToDouble.execute(value), unitToNs);
        } catch (CannotCastException e) {
            try {
                return doLong(asLongNode.execute(frame, value), unitToNs);
            } catch (OverflowException e1) {
                throw raiseTimeOverflow();
            }
        }
    }

    private PException raiseTimeOverflow() {
        throw raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT_TO, "timestamp", "long");
    }
}
