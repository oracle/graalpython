/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Storage for initialized Python built-in modules and types.
 */
public interface PythonCore {
    public static final String FILE_SEPARATOR = File.separator;
    public static final String PATH_SEPARATOR = File.pathSeparator;
    static final String PREFIX = "/";
    static final String LIB_PYTHON_3 = "/lib-python/3";
    static final String LIB_GRAALPYTHON = "/lib-graalpython";
    static final String NO_CORE_FATAL = "could not determine Graal.Python's core path - you must pass --python.CoreHome.";
    static final String NO_PREFIX_WARNING = "could not determine Graal.Python's sys prefix path - you may need to pass --python.SysPrefix.";
    static final String NO_CORE_WARNING = "could not determine Graal.Python's core path - you may need to pass --python.CoreHome.";
    static final String NO_STDLIB = "could not determine Graal.Python's standard library path. You need to pass --python.StdLibHome if you want to use the standard library.";
    static final boolean LIBPOLYGLOT = Boolean.getBoolean("graalvm.libpolyglot");

    /**
     * Load the core library and prepare all builtin classes and modules.
     */
    public void initialize(PythonContext pythonContext);

    /**
     * Initialize the runtime information in the sys module, capturing command line arguments,
     * executable paths and so on.
     */
    public PythonModule initializeSysModule();

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

    public PythonBuiltinClass lookupType(Class<? extends Object> clazz);

    public String[] builtinModuleNames();

    public PythonClass getErrorClass(PythonErrorType type);

    // Error throwing functions
    public PException raise(PythonErrorType type, Node node, String format, Object... args);

    public PException raise(PBaseException exception, Node node);

    public PException raise(PythonErrorType type, String format, Object... args);

    public PException raise(PythonErrorType type);

    public PException raise(PythonErrorType type, Node node);

    public PException raise(PythonClass cls, Node node);

    // Accessors
    public PythonLanguage getLanguage();

    public PythonParser getParser();

    public PythonObjectFactory factory();

    public void setContext(PythonContext context);

    public PythonContext getContext();

    static void writeWarning(TruffleLanguage.Env env, String warning) {
        if (!LIBPOLYGLOT || env.getOptions().get(PythonOptions.VerboseFlag)) {
            write(env, "WARNING: " + warning);
        }
    }

    static void writeInfo(TruffleLanguage.Env env, String warning) {
        if (env.getOptions().get(PythonOptions.VerboseFlag)) {
            write(env, warning);
        }
    }

    static void write(TruffleLanguage.Env env, String warning) {
        try {
            env.err().write("[python] ".getBytes());
            env.err().write(warning.getBytes());
            env.err().write('\n');
        } catch (IOException e) {
        }
    }

    @TruffleBoundary
    public static String getCoreHomeOrFail() {
        TruffleLanguage.Env env = PythonLanguage.getContextRef().get().getEnv();
        String coreHome = env.getOptions().get(PythonOptions.CoreHome);
        if (coreHome.isEmpty()) {
            throw new RuntimeException(NO_CORE_FATAL);
        }
        return coreHome;
    }

    @TruffleBoundary
    public static String getSysPrefix(TruffleLanguage.Env env) {
        String sysPrefix = env.getOptions().get(PythonOptions.SysPrefix);
        if (sysPrefix.isEmpty()) {
            writeWarning(env, NO_PREFIX_WARNING);
            env.getOptions().set(PythonOptions.SysPrefix, PREFIX);
            return LIB_GRAALPYTHON;
        }
        return sysPrefix;
    }

    @TruffleBoundary
    public static String getCoreHome(TruffleLanguage.Env env) {
        String coreHome = env.getOptions().get(PythonOptions.CoreHome);
        if (coreHome.isEmpty()) {
            writeWarning(env, NO_CORE_WARNING);
            env.getOptions().set(PythonOptions.CoreHome, LIB_GRAALPYTHON);
            return LIB_GRAALPYTHON;
        }
        return coreHome;
    }

    @TruffleBoundary
    public static String getStdlibHome(TruffleLanguage.Env env) {
        String stdLibHome = env.getOptions().get(PythonOptions.StdLibHome);
        if (stdLibHome.isEmpty()) {
            writeWarning(env, NO_STDLIB);
            env.getOptions().set(PythonOptions.StdLibHome, LIB_PYTHON_3);
            return LIB_PYTHON_3;
        }
        return stdLibHome;
    }
}
