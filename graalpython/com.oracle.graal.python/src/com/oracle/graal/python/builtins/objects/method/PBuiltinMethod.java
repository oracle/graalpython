/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.method;

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

/**
 * Represents a builtin module-level function (bound to the module) or a bound builtin method.
 * Corresponds to python types:
 * <ul>
 * <li>{@code builtin_function_or_method} - Called {@code PyCFunction} in CPython C code. Used for
 * builtin module-level functions and when a builtin method on a type ({@code method_descriptor},
 * see {@link PBuiltinFunction}) gets bound. Examples: {@code sys.getdefaultencoding},
 * {@code "a".startswith}
 * <li>{@code method-wrapper} - Used when a slot method on a type ({@code wrapper_descriptor}, see
 * {@link PBuiltinFunction}) gets bound. Example: {@code "a".__str__}
 * <li>{@code builtin_method} - Used when a builtin method with C call convention
 * {@code METH_METHOD} is bound. Example: {@code pyexpat.ParserCreate().Parse}
 * </ul>
 */
@ExportLibrary(InteropLibrary.class)
public final class PBuiltinMethod extends PythonBuiltinObject {

    private final PBuiltinFunction function;
    private final Object self;
    private final Object classObject;

    public PBuiltinMethod(Object clazz, Shape instanceShape, Object self, PBuiltinFunction function, Object classObject) {
        super(clazz, instanceShape);
        this.self = self;
        this.function = function;
        this.classObject = classObject;
    }

    @Idempotent
    public PBuiltinFunction getFunction() {
        return function;
    }

    public Object getSelf() {
        return self;
    }

    public Object getClassObject() {
        return classObject;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<builtin-method '" + function + "' of '" + self + "' objects>";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    Object getExecutableName() {
        return function.getName();
    }
}
