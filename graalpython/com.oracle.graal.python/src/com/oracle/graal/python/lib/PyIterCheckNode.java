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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Check if the object is iterable - has {@code __next__} method. Equivalent of CPython's
 * {@code PyIter_Check}.
 */
@ImportStatic(SpecialMethodSlot.class)
@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class PyIterCheckNode extends PNodeWithContext {
    public abstract boolean execute(Node inliningTarget, Object object);

    public final boolean executeCached(Object object) {
        return execute(this, object);
    }

    public static PyIterCheckNode create() {
        return PyIterCheckNodeGen.create();
    }

    @Specialization
    static boolean doIterator(@SuppressWarnings("unused") PBuiltinIterator object) {
        return true;
    }

    @InliningCutoff
    @Specialization
    static boolean doPythonObject(Node inliningTarget, PythonAbstractObject object,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached(parameters = "Next", inline = false) LookupCallableSlotInMRONode lookupNext) {
        return !(lookupNext.execute(getClassNode.execute(inliningTarget, object)) instanceof PNone);
    }

    @Specialization
    static boolean doInt(@SuppressWarnings("unused") Integer object) {
        return false;
    }

    @Specialization
    static boolean doLong(@SuppressWarnings("unused") Long object) {
        return false;
    }

    @Specialization
    static boolean doBoolean(@SuppressWarnings("unused") Boolean object) {
        return false;
    }

    @Specialization
    static boolean doDouble(@SuppressWarnings("unused") Double object) {
        return false;
    }

    @Specialization
    static boolean doPBCT(@SuppressWarnings("unused") PythonBuiltinClassType object) {
        return false;
    }

    @InliningCutoff
    @Specialization(replaces = "doPythonObject")
    static boolean doGeneric(Node inliningTarget, Object object,
                    @CachedLibrary(limit = "3") InteropLibrary interopLibrary,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached(parameters = "Next", inline = false) LookupCallableSlotInMRONode lookupNext) {
        Object type = getClassNode.execute(inliningTarget, object);
        if (type == PythonBuiltinClassType.ForeignObject) {
            return interopLibrary.isIterator(object);
        }
        return !(lookupNext.execute(type) instanceof PNone);
    }
}
