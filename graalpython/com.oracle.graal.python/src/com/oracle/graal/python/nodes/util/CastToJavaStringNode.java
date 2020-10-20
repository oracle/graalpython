/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

/**
 * Casts a Python string to a Java string without coercion. <b>ATTENTION:</b> If the cast fails,
 * because the object is not a Python string, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class CastToJavaStringNode extends PNodeWithContext {

    public abstract String execute(Object x) throws CannotCastException;

    @Specialization
    static String doString(String x) {
        return x;
    }

    @Specialization(guards = "isMaterialized(x)")
    static String doPStringMaterialized(PString x) {
        // cast guaranteed by the guard
        return (String) x.getCharSequence();
    }

    @Specialization(guards = "!isMaterialized(x)")
    static String doPStringGeneric(PString x,
                    @Cached StringMaterializeNode materializeNode) {
        return materializeNode.execute(x);
    }

    @Specialization(limit = "2")
    static String doNativeObject(PythonNativeObject x,
                    @CachedLibrary("x") PythonObjectLibrary plib,
                    @Cached IsSubtypeNode isSubtypeNode,
                    @Cached PCallCapiFunction callNativeUnicodeAsStringNode,
                    @Cached ToSulongNode toSulongNode) {
        if (isSubtypeNode.execute(plib.getLazyPythonClass(x), PythonBuiltinClassType.PString)) {
            // read the native data
            Object result = callNativeUnicodeAsStringNode.call(NativeCAPISymbols.FUN_NATIVE_UNICODE_AS_STRING, toSulongNode.execute(x));
            assert result instanceof String;
            return (String) result;
        }
        // the object's type is not a subclass of 'str'
        throw CannotCastException.INSTANCE;
    }

    @Specialization(guards = "!isString(x)")
    static String doUnsupported(@SuppressWarnings("unused") Object x) {
        throw CannotCastException.INSTANCE;
    }

    static boolean isMaterialized(PString x) {
        return x.getCharSequence() instanceof String;
    }

    public static CastToJavaStringNode create() {
        return CastToJavaStringNodeGen.create();
    }

    public static CastToJavaStringNode getUncached() {
        return CastToJavaStringNodeGen.getUncached();
    }
}
