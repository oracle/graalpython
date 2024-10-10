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
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
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

/**
 * Equivalent of CPython's {@code PyCallable_Check} function.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyCallableCheckNode extends PNodeWithContext {
    public static boolean executeUncached(Object object) {
        return PyCallableCheckNodeGen.getUncached().execute(null, object);
    }

    /**
     * Use this overload only if the node is cached!
     */
    public final boolean execute(Object object) {
        return execute(null, object);
    }

    public abstract boolean execute(Node inliningTarget, Object object);

    @Specialization
    static boolean doFunction(@SuppressWarnings("unused") PFunction o) {
        return true;
    }

    @Specialization
    static boolean doMethod(@SuppressWarnings("unused") PMethod o) {
        return true;
    }

    @Specialization
    static boolean doBuiltinFunction(@SuppressWarnings("unused") PBuiltinFunction o) {
        return true;
    }

    @Specialization
    static boolean doBuiltinMethod(@SuppressWarnings("unused") PBuiltinMethod o) {
        return true;
    }

    @Specialization
    static boolean doClass(@SuppressWarnings("unused") PythonClass o) {
        return true;
    }

    @Specialization
    static boolean doBuiltinClass(@SuppressWarnings("unused") PythonBuiltinClass o) {
        return true;
    }

    @Specialization
    static boolean doType(@SuppressWarnings("unused") PythonBuiltinClassType o) {
        return true;
    }

    @Fallback
    static boolean doObject(Node inliningTarget, Object o,
                    @Cached GetClassNode getClassNode,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached LazyInteropLibrary lazyInteropLib,
                    @Cached(parameters = "Call", inline = false) LookupCallableSlotInMRONode lookupCall) {
        Object type = getClassNode.execute(inliningTarget, o);
        if (isForeignObjectNode.execute(inliningTarget, o)) {
            InteropLibrary lib = lazyInteropLib.get(inliningTarget);
            return lib.isExecutable(o) || lib.isInstantiable(o);
        }
        return lookupCall.execute(type) != PNone.NO_VALUE;
    }

    public static PyCallableCheckNode create() {
        return PyCallableCheckNodeGen.create();
    }
}
