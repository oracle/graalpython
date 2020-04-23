/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.HiddenKey;

/**
 * A Python built-in class that is immutable.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonBuiltinClass extends PythonManagedClass {
    private final PythonBuiltinClassType type;

    public PythonBuiltinClass(PythonBuiltinClassType builtinClass, PythonAbstractClass base) {
        super(PythonBuiltinClassType.PythonClass, builtinClass.getQualifiedName(), base);
        this.type = builtinClass;
    }

    @Override
    public void setAttribute(Object name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        if (name instanceof HiddenKey || !PythonLanguage.getCore().isInitialized()) {
            setAttributeUnsafe(name, value);
        } else {
            throw PythonLanguage.getCore().raise(TypeError, "can't set attributes of built-in/extension type '%s'", this);
        }
    }

    /**
     * Modify attributes in an unsafe way, should only use when initializing.
     */
    public void setAttributeUnsafe(Object name, Object value) {
        super.setAttribute(name, value);
    }

    public PythonBuiltinClassType getType() {
        return type;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Cached GetLazyClassNode getClass,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(getClass.execute(instance), this);
    }

    @ExportMessage
    String getMetaSimpleName() {
        return type.getName();
    }

    @ExportMessage
    String getMetaQualifiedName() {
        return type.getQualifiedName();
    }
}
