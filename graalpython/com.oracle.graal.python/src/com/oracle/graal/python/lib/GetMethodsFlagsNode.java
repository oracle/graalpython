/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.HiddenAttr.METHODS_FLAGS;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Retrieve slots occupation of `cls->tp_as_number`, `cls->tp_as_sequence` and `cls->tp_as_mapping`
 * for a given class.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class GetMethodsFlagsNode extends Node {

    public abstract long execute(Node inliningTarget, Object cls);

    @Specialization
    protected static long pythonbuiltinclasstype(PythonManagedClass cls) {
        return cls.getMethodsFlags();
    }

    @Specialization
    protected static long pythonclasstype(PythonBuiltinClassType cls) {
        return cls.getMethodsFlags();
    }

    @TruffleBoundary
    private static long populateMethodsFlags(PythonAbstractNativeObject cls) {
        Long flags = (Long) PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_GET_METHODS_FLAGS, cls.getPtr());
        HiddenAttr.WriteNode.executeUncached(cls, METHODS_FLAGS, flags);
        return flags;
    }

    protected static long getMethodsFlags(PythonAbstractNativeObject cls) {
        return doNative(null, cls, HiddenAttr.ReadNode.getUncached());
    }

    // The assumption should hold unless `PyType_Modified` is called.
    protected static Assumption nativeAssumption(PythonAbstractNativeObject cls) {
        return PythonContext.get(null).getNativeClassStableAssumption(cls, true).getAssumption();
    }

    @Specialization(guards = "cachedCls == cls", limit = "5", assumptions = "nativeAssumption(cachedCls)")
    static long doNativeCached(@SuppressWarnings("unused") PythonAbstractNativeObject cls,
                    @SuppressWarnings("unused") @Cached("cls") PythonAbstractNativeObject cachedCls,
                    @Cached("getMethodsFlags(cls)") long flags) {
        return flags;
    }

    @Specialization(replaces = "doNativeCached")
    static long doNative(Node inliningTarget, PythonAbstractNativeObject cls,
                    @Cached HiddenAttr.ReadNode readFlagsNode) {
        // classes must have tp_dict since they are set during PyType_Ready
        Long flags = (Long) readFlagsNode.execute(inliningTarget, cls, METHODS_FLAGS, null);
        if (flags == null) {
            return populateMethodsFlags(cls);
        }
        return flags;
    }

    @Fallback
    protected static long zero(@SuppressWarnings("unused") Object cls) {
        return 0;
    }
}
