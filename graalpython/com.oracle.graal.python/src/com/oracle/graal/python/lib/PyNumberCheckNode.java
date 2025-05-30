/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyNumber_Check}. Returns true if the argument provides numeric
 * protocols, and false otherwise.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyNumberCheckNode extends PNodeWithContext {
    public abstract boolean execute(Node inliningTarget, Object object);

    public static boolean executeUncached(Object object) {
        return PyNumberCheckNodeGen.getUncached().execute(null, object);
    }

    @Specialization
    static boolean doString(@SuppressWarnings("unused") TruffleString object) {
        return false;
    }

    @Specialization
    @SuppressWarnings("unused")
    static boolean doDouble(Double object) {
        return true;
    }

    @Specialization
    @SuppressWarnings("unused")
    static boolean doInt(Integer object) {
        return true;
    }

    @Specialization
    @SuppressWarnings("unused")
    static boolean doLong(Long object) {
        return true;
    }

    @Specialization
    @SuppressWarnings("unused")
    static boolean doBoolean(Boolean object) {
        return true;
    }

    @Specialization
    @SuppressWarnings("unused")
    static boolean doNone(PNone object) {
        return false;
    }

    @Specialization
    static boolean doComplex(@SuppressWarnings("unused") PComplex object) {
        return true;
    }

    @Fallback
    static boolean doOthers(Node inliningTarget, Object object,
                    @Cached GetObjectSlotsNode getSlots,
                    @Cached PyComplexCheckNode checkComplex) {
        TpSlots slots = getSlots.execute(inliningTarget, object);
        return slots.nb_index() != null || slots.nb_int() != null || slots.nb_float() != null || checkComplex.execute(inliningTarget, object);
    }
}
