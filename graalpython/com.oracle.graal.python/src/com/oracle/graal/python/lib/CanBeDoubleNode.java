/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.LazyInteropLibrary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Checks whether a value can be converted to {@code double} using {@link PyFloatAsDoubleNode} o
 * {@link PyNumberFloatNode}. There is no direct CPython function equivalent, as CPython typically
 * checks for the {@code nb_float} and {@code nb_index} slots directly.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(SpecialMethodSlot.class)
public abstract class CanBeDoubleNode extends PNodeWithContext {
    public static boolean executeUncached(Object receiver) {
        return CanBeDoubleNodeGen.getUncached().execute(null, receiver);
    }

    public abstract boolean execute(Node inliningTarget, Object object);

    @Specialization
    static boolean doDouble(@SuppressWarnings("unused") Double object) {
        return true;
    }

    @Specialization
    static boolean doInt(@SuppressWarnings("unused") Integer object) {
        return true;
    }

    @Specialization
    static boolean doLong(@SuppressWarnings("unused") Long object) {
        return true;
    }

    @Specialization
    static boolean doBoolean(@SuppressWarnings("unused") Boolean object) {
        return true;
    }

    @Specialization
    static boolean doString(@SuppressWarnings("unused") TruffleString object) {
        return false;
    }

    @Specialization
    static boolean doPBCT(@SuppressWarnings("unused") PythonBuiltinClassType object) {
        return false;
    }

    @Fallback
    static boolean doGeneric(Node inliningTarget, Object object,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached LazyInteropLibrary lazyInteropLibrary,
                    @Cached(parameters = "Float", inline = false) LookupCallableSlotInMRONode lookupFloat,
                    @Cached(parameters = "Index", inline = false) LookupCallableSlotInMRONode lookupIndex,
                    @Cached GetClassNode getClassNode) {
        Object type = getClassNode.execute(inliningTarget, object);
        if (isForeignObjectNode.execute(inliningTarget, object)) {
            InteropLibrary interopLibrary = lazyInteropLibrary.get(inliningTarget);
            return interopLibrary.fitsInDouble(object) || interopLibrary.fitsInLong(object) || interopLibrary.isBoolean(object);
        }
        return lookupFloat.execute(type) != PNone.NO_VALUE || lookupIndex.execute(type) != PNone.NO_VALUE;
    }
}
