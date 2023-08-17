/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T_AST;
import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__ATTRIBUTES;
import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__FIELDS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MATCH_ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.util.PythonUtils.convertToObjectArray;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Utility methods that simplify the generated code in {@link AstState}.
 */
final class AstTypeFactory {

    private final PythonLanguage language;
    private final PythonObjectFactory factory;
    private final PythonModule astModule;

    AstTypeFactory(PythonLanguage language, PythonObjectFactory factory, PythonModule astModule) {
        this.language = language;
        this.factory = factory;
        this.astModule = astModule;
    }

    PythonClass makeType(TruffleString name, PythonAbstractClass base, TruffleString[] fields, TruffleString[] attributes, TruffleString[] optional, TruffleString docString) {
        PythonClass newType = factory.createPythonClassAndFixupSlots(language, PythonBuiltinClassType.PythonClass, name, base, new PythonAbstractClass[]{base});
        newType.setAttribute(T___MODULE__, T_AST);
        newType.setAttribute(T___DOC__, docString);
        newType.setAttribute(T__FIELDS, factory.createTuple(convertToObjectArray(fields)));
        newType.setAttribute(T___MATCH_ARGS__, factory.createTuple(convertToObjectArray(fields)));
        if (attributes != null) {
            newType.setAttribute(T__ATTRIBUTES, factory.createTuple(convertToObjectArray(attributes)));
        }
        for (TruffleString n : optional) {
            newType.setAttribute(n, PNone.NONE);
        }
        astModule.setAttribute(name, newType);
        return newType;
    }

    PythonObject createSingleton(PythonClass cls) {
        return factory.createPythonObject(cls);
    }
}
