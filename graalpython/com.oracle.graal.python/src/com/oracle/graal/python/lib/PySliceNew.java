/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Creates a new slice object with given parameters. Equivalent of CPython's {@code PySlice_New}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(PInt.class)
public abstract class PySliceNew extends PNodeWithContext {
    public abstract PSlice execute(Node inliningTarget, Object start, Object stop, Object step);

    @SuppressWarnings("unused")
    static PSlice doInt(int start, int stop, PNone step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntSlice(start, stop, 1, false, true);
    }

    @Specialization
    static PSlice doInt(int start, int stop, int step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntSlice(start, stop, step);
    }

    @Specialization
    @SuppressWarnings("unused")
    static PSlice doInt(PNone start, int stop, PNone step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntSlice(0, stop, 1, true, true);
    }

    @Specialization
    @SuppressWarnings("unused")
    static PSlice doInt(PNone start, int stop, int step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntSlice(0, stop, step, true, false);
    }

    // This specialization is often used when called from C builtins
    @Specialization(guards = {"isIntRange(start)", "isIntRange(stop)"})
    @SuppressWarnings("unused")
    static PSlice doLong(long start, long stop, PNone step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntSlice((int) start, (int) stop, 1, false, true);
    }

    @Fallback
    static PSlice doGeneric(Object start, Object stop, Object step,
                    @Cached.Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
        assert start != PNone.NO_VALUE && stop != PNone.NO_VALUE && step != PNone.NO_VALUE;
        return factory.createObjectSlice(start, stop, step);
    }
}
