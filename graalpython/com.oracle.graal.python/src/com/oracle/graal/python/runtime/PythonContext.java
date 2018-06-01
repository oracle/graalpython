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

import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.__MAIN__;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.options.OptionValues;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;

public class PythonContext {

    private final PythonLanguage language;
    private PythonModule mainModule;
    private final PythonCore core;
    private final HashMap<Object, CallTarget> atExitHooks = new HashMap<>();

    @CompilationFinal private TruffleLanguage.Env env;

    private PException currentException;

    private final ReentrantLock importLock = new ReentrantLock();
    @CompilationFinal private boolean isInitialized = false;

    @CompilationFinal private PythonModule builtinsModule;
    @CompilationFinal private PDict sysModules;

    private OutputStream out;
    private OutputStream err;
    @CompilationFinal private boolean capiWasLoaded = false;

    @CompilationFinal private HashingStorage.Equivalence slowPathEquivalence;

    /** A thread-local dictionary for custom user state. */
    private ThreadLocal<PDict> customThreadState;

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env, PythonCore core) {
        this.language = language;
        this.core = core;
        this.env = env;
        if (env == null) {
            this.out = System.out;
            this.err = System.err;
        } else {
            this.out = env.out();
            this.err = env.err();
        }
    }

    public OptionValues getOptions() {
        return getEnv().getOptions();
    }

    public PythonLanguage getLanguage() {
        return language;
    }

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public PDict getImportedModules() {
        return sysModules;
    }

    public PDict getSysModules() {
        return sysModules;
    }

    public PythonModule getBuiltins() {
        return builtinsModule;
    }

    public PythonModule createMainModule(String path) {
        mainModule = core.factory().createPythonModule(__MAIN__, path);
        mainModule.setAttribute(__BUILTINS__, sysModules.getItem("builtins"));
        getSysModules().setItem(__MAIN__, mainModule);
        return mainModule;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    public void setEnv(TruffleLanguage.Env newEnv) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        env = newEnv;
    }

    public PythonModule getMainModule() {
        return mainModule;
    }

    public PythonModule getOrCreateMainModule(String path) {
        if (mainModule == null) {
            return createMainModule(path);
        } else {
            return mainModule;
        }
    }

    public PythonCore getCore() {
        return core;
    }

    public OutputStream getStandardErr() {
        return err;
    }

    public OutputStream getStandardOut() {
        return out;
    }

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public void setErr(OutputStream err) {
        this.err = err;
    }

    public void setCurrentException(PException e) {
        currentException = e;
    }

    public PException getCurrentException() {
        return currentException;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void initialize() {
        if (!PythonOptions.getOption(this, PythonOptions.SharedCore)) {
            core.setSingletonContext(this);
        }

        PythonModule sysModule = core.createSysModule(this);
        sysModules = (PDict) sysModule.getAttribute("modules");
        builtinsModule = (PythonModule) sysModules.getItem("builtins");

        isInitialized = true;
    }

    public boolean capiWasLoaded() {
        return this.capiWasLoaded;
    }

    public void setCapiWasLoaded() {
        this.capiWasLoaded = true;
    }

    public HashingStorage.Equivalence getSlowPathEquivalence() {
        if (slowPathEquivalence == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slowPathEquivalence = new HashingStorage.SlowPathEquivalence();
        }
        return slowPathEquivalence;
    }

    @TruffleBoundary
    public void registerShutdownHook(Object callable, CallTarget ct) {
        atExitHooks.put(callable, ct);
    }

    @TruffleBoundary
    public void deregisterShutdownHook(Object callable) {
        atExitHooks.remove(callable);
    }

    @TruffleBoundary
    public void runShutdownHooks() {
        for (CallTarget f : atExitHooks.values()) {
            f.call();
        }
    }

    @TruffleBoundary
    public PDict getCustomThreadState() {
        if (customThreadState == null) {
            ThreadLocal<PDict> threadLocal = new ThreadLocal<>();
            threadLocal.set(PythonObjectFactory.create().createDict());
            customThreadState = threadLocal;
        }
        return customThreadState.get();
    }

}
