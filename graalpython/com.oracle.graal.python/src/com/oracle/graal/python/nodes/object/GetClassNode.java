/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
@GenerateUncached
@Deprecated // TODO: DSL inlining
public abstract class GetClassNode extends PNodeWithContext {
    @NeverDefault
    public static GetClassNode create() {
        return GetClassNodeGen.create();
    }

    public static GetClassNode getUncached() {
        return GetClassNodeGen.getUncached();
    }

    @Deprecated // TODO: DSL inlining - replace calls with GetPythonObjectClassNode
    public abstract Object execute(Object object);

    @SuppressWarnings("static-method")
    public final Object execute(@SuppressWarnings("unused") int i) {
        return PythonBuiltinClassType.PInt;
    }

    @SuppressWarnings("static-method")
    public final Object execute(@SuppressWarnings("unused") double d) {
        return PythonBuiltinClassType.PFloat;
    }

    /*
     * Many nodes already specialize on Python[Abstract]Object subclasses, like PTuple. Add
     * shortcuts that avoid interpreter overhead of testing the object for all the primitive types
     * when not necessary.
     */
    @Deprecated // TODO: DSL inlining - replace calls with GetPythonObjectClassNode
    public abstract Object execute(PythonAbstractObject object);

    @Deprecated // TODO: DSL inlining - replace calls with GetPythonObjectClassNode
    public abstract Object execute(PythonAbstractNativeObject object);

    @Deprecated // TODO: replace calls with GetPythonObjectClassNode
    public abstract Object execute(PythonObject object);

    @Specialization
    Object doIt(Object object,
                    @Cached InlinedGetClassNode getClassNode) {
        return getClassNode.execute(this, object);
    }
}
