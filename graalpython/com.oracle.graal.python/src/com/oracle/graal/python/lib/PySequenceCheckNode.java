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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PySequence_Check}.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PySequenceCheckNode extends PNodeWithContext {
    public abstract boolean execute(Object object);

    @Specialization
    static boolean doSequence(@SuppressWarnings("unused") PSequence object) {
        return true;
    }

    @Specialization
    static boolean doString(@SuppressWarnings("unused") TruffleString object) {
        return true;
    }

    @Specialization
    static boolean doDict(@SuppressWarnings("unused") PDict object) {
        return false;
    }

    @Specialization
    static boolean doMappingproxy(@SuppressWarnings("unused") PMappingproxy object) {
        return false;
    }

    protected static boolean cannotBeSequence(Object object) {
        return object instanceof PDict || object instanceof PMappingproxy;
    }

    @Specialization(guards = {"!cannotBeSequence(object)"})
    boolean doPythonObject(PythonObject object,
                    @Bind("this") Node inliningTarget,
                    @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                    @Shared("lookupGetItem") @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetItem) {
        Object type = getClassNode.execute(inliningTarget, object);
        return lookupGetItem.execute(type) != PNone.NO_VALUE;
    }

    @Specialization
    static boolean doNative(PythonAbstractNativeObject object,
                    @Cached PythonToNativeNode toSulongNode,
                    @Cached PCallCapiFunction callCapiFunction) {
        return ((int) callCapiFunction.call(FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK, toSulongNode.execute(object))) != 0;
    }

    @Specialization(guards = {"!cannotBeSequence(object)", "!isNativeObject(object)"}, replaces = "doPythonObject")
    boolean doGeneric(Object object,
                    @Bind("this") Node inliningTarget,
                    @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                    @Shared("lookupGetItem") @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetItem,
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        Object type = getClassNode.execute(inliningTarget, object);
        if (type == PythonBuiltinClassType.ForeignObject) {
            return lib.hasArrayElements(object);
        }
        return lookupGetItem.execute(type) != PNone.NO_VALUE;
    }
}
