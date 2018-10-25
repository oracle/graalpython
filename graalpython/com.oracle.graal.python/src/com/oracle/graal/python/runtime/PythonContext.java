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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleLanguage.Env;

public final class PythonContext {

    private final PythonLanguage language;
    private PythonModule mainModule;
    private final PythonCore core;
    private final HashMap<Object, CallTarget> atExitHooks = new HashMap<>();
    private final AtomicLong globalId = new AtomicLong(Integer.MAX_VALUE * 2L + 4L);

    @CompilationFinal private TruffleLanguage.Env env;

    private PException currentException;

    private final ReentrantLock importLock = new ReentrantLock();
    @CompilationFinal private boolean isInitialized = false;

    @CompilationFinal private PythonModule builtinsModule;
    @CompilationFinal private PDict sysModules;

    private OutputStream out;
    private OutputStream err;
    private InputStream in;
    @CompilationFinal private Object capiLibrary = null;
    private final static Assumption singleNativeContext = Truffle.getRuntime().createAssumption("single native context assumption");

    @CompilationFinal private HashingStorage.Equivalence slowPathEquivalence;

    /** A thread-local dictionary for custom user state. */
    private ThreadLocal<PDict> customThreadState;

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env, PythonCore core) {
        this.language = language;
        this.core = core;
        this.env = env;
        if (env == null) {
            this.in = System.in;
            this.out = System.out;
            this.err = System.err;
        } else {
            this.in = env.in();
            this.out = env.out();
            this.err = env.err();
        }
    }

    @TruffleBoundary(allowInlining = true)
    public long getNextGlobalId() {
        return globalId.incrementAndGet();
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

    public PythonCore getCore() {
        return core;
    }

    public InputStream getStandardIn() {
        return in;
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
        core.initialize(this);
        setupRuntimeInformation();
        core.postInitialize();
    }

    public void patch(Env newEnv) {
        setEnv(newEnv);
        setOut(newEnv.out());
        setErr(newEnv.err());
        setupRuntimeInformation();
        core.postInitialize();
    }

    private void setupRuntimeInformation() {
        PythonModule sysModule = core.initializeSysModule();
        if (TruffleOptions.AOT && !language.isNativeBuildTime() && isExecutableAccessAllowed()) {
            sysModule.setAttribute("executable", ProcessProperties.getExecutableName());
        }
        sysModules = (PDict) sysModule.getAttribute("modules");
        builtinsModule = (PythonModule) sysModules.getItem("builtins");
        mainModule = core.factory().createPythonModule(__MAIN__);
        mainModule.setAttribute(__BUILTINS__, builtinsModule);
        sysModules.setItem(__MAIN__, mainModule);
        currentException = null;
        isInitialized = true;
    }

    public boolean capiWasLoaded() {
        return this.capiLibrary != null;
    }

    public Object getCapiLibrary() {
        return this.capiLibrary;
    }

    public void setCapiWasLoaded(Object capiLibrary) {
        this.capiLibrary = capiLibrary;
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

    public void initializeMainModule(String path) {
        if (path != null) {
            mainModule.setAttribute(__FILE__, path);
        }
    }

    public static Assumption getSingleNativeContextAssumption() {
        return singleNativeContext;
    }

    public boolean isExecutableAccessAllowed() {
        return getEnv().isHostLookupAllowed() || getEnv().isNativeAccessAllowed();
    }
}
