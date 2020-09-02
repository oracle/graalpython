/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Casts a Python integer to a Java long, lossy and without coercion. <b>ATTENTION:</b> If the cast
 * isn't possible, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
public abstract class CastToJavaLongLossyNode extends CastToJavaLongNode {

    public static CastToJavaLongLossyNode create() {
        return CastToJavaLongLossyNodeGen.create();
    }

    public static CastToJavaLongLossyNode getUncached() {
        return CastToJavaLongLossyNodeGen.getUncached();
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected static long toLong(PInt x) throws OverflowException {
        return x.longValueExact();
    }

    @Specialization(replaces = "toLong")
    protected static long toLongOverflow(PInt x) {
        if (x.compareTo(PInt.MAX_LONG) > 0) {
            return Long.MAX_VALUE;
        } else if (x.compareTo(PInt.MIN_LONG) < 0) {
            return Long.MIN_VALUE;
        } else {
            return x.longValue();
        }
    }
}
