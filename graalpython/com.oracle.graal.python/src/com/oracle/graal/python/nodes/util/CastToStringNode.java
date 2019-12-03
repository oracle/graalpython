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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Converts an arbitrary object to a Python string (aka unicode) object. This node will also do
 * coercion, i.e., it will call {@code __str__} if necessary.
 */
@ImportStatic({PGuards.class, SpecialMethodNames.class})
public abstract class CastToStringNode extends PNodeWithContext {
    private static final String ERROR_MESSAGE = "__str__ returned non-string (type %p)";

    public abstract Object execute(VirtualFrame frame, Object x);

    public abstract Object execute(VirtualFrame frame, int x);

    public abstract Object execute(VirtualFrame frame, long x);

    public abstract Object execute(VirtualFrame frame, boolean x);

    @Specialization(guards = "isString(x)")
    static Object doString(Object x) {
        return x;
    }

    @Specialization
    static String doBoolean(boolean x) {
        return x ? "True" : "False";
    }

    @Specialization
    @TruffleBoundary
    static String doInt(int x) {
        return Integer.toString(x);
    }

    @Specialization
    @TruffleBoundary
    static String doLong(long x) {
        return Long.toString(x);
    }

    @Specialization
    static String doDouble(double x) {
        return PFloat.doubleToString(x);
    }

    @Specialization
    static String doString(String x) {
        return x;
    }

    @Specialization
    Object doGeneric(VirtualFrame frame, Object x,
                    @Cached("create(__STR__)") LookupAndCallUnaryNode callStrNode,
                    @Cached GetLazyClassNode getClassNode,
                    @Cached IsBuiltinClassProfile isStringProfile,
                    @Cached PRaiseNode raise) {
        Object result = callStrNode.executeObject(frame, x);
        if (!isStringProfile.profileClass(getClassNode.execute(result), PythonBuiltinClassType.PString)) {
            throw raise.raise(TypeError, ERROR_MESSAGE, result);
        }
        return result;
    }

    public static CastToStringNode create() {
        return CastToStringNodeGen.create();
    }
}
