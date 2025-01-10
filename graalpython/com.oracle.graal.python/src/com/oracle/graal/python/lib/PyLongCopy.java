/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Creates a new builtin int object from a given int, which might be a subclass. Equivalent of
 * {@code _PyLong_Copy}.
 */
@GenerateInline
@GenerateCached(false)
@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class PyLongCopy extends Node {
    public abstract Object execute(Node inliningTarget, Object obj);

    @Specialization
    static int doB(boolean obj) {
        return obj ? 1 : 0;
    }

    @Specialization
    static int doI(int obj) {
        return obj;
    }

    @Specialization
    static long doL(long obj) {
        return obj;
    }

    @Specialization(guards = "isBuiltinPInt(obj)")
    static PInt doPInt(PInt obj) {
        return obj;
    }

    @Specialization(guards = "!isBuiltinPInt(obj)", rewriteOn = OverflowException.class)
    static int doPIntOverridenNarrowInt(PInt obj) throws OverflowException {
        return obj.intValueExact();
    }

    @Specialization(guards = "!isBuiltinPInt(obj)", replaces = "doPIntOverridenNarrowInt", rewriteOn = OverflowException.class)
    static long doPIntOverridenNarrowLong(PInt obj) throws OverflowException {
        return obj.longValueExact();
    }

    @Specialization(guards = "!isBuiltinPInt(obj)", replaces = "doPIntOverridenNarrowLong")
    static PInt doPIntOverriden(PInt obj,
                    @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createInt(obj.getValue());
    }

    @Specialization
    static PythonNativeVoidPtr doL(PythonNativeVoidPtr obj) {
        return obj;
    }
}
