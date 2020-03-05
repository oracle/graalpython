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
package com.oracle.graal.python.runtime;

import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;

/**
 * Storage for initialized Python built-in modules and types.
 */
public interface PythonCore extends ParserErrorCallback {

    /**
     * Load the core library and prepare all builtin classes and modules.
     */
    public void initialize(PythonContext pythonContext);

    /**
     * Run post-initialization code that needs a fully working Python environment. This will be run
     * eagerly when the context is initialized on the JVM or a new context is created on SVM, but is
     * omitted when the native image is generated.
     */
    public void postInitialize();

    /**
     * Checks whether the core is initialized.
     */
    public boolean isInitialized();

    // Various lookup functions
    public PythonModule lookupBuiltinModule(String name);

    public PythonBuiltinClass lookupType(PythonBuiltinClassType type);

    public String[] builtinModuleNames();

    // Error throwing functions
    public PException raise(PythonBuiltinClassType type, String format, Object... args);

    // Accessors
    public PythonLanguage getLanguage();

    public PythonParser getParser();

    public PythonCodeSerializer getSerializer();

    public PythonObjectFactory factory();

    public void setContext(PythonContext context);

    public PythonContext getContext();

    public PInt getTrue();

    public PInt getFalse();

    public PFloat getNaN();

    public PythonModule getBuiltins();

    static void writeInfo(String message) {
        PythonLanguage.getLogger().fine(message);
    }

    static void writeInfo(Supplier<String> messageSupplier) {
        PythonLanguage.getLogger().fine(messageSupplier);
    }
}
