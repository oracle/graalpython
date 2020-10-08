/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import java.util.Objects;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.NativeLibraryFactory.InvokeNativeFunctionNodeGen;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.Source;

/**
 * Wraps a native library loaded via NFI and provides caching for functions looked up in the
 * library. The set of functions to be loaded from the library is expressed as Java enum
 * implementing {@link NativeFunction}. This is runtime object: it should not be cached in the AST
 * and is expected to be stored in the context.
 * <p>
 * Because of Truffle DSL restrictions this class cannot be generic, but users should work with
 * generic subclass {@link TypedNativeLibrary}, which can be created with one of the {@code create}
 * factory methods.
 * <p>
 * For now, until there is no need to access the library and function objects directly, this object
 * is opaque to the outside code and the only entrypoint is {@link InvokeNativeFunction}, which
 * lazily loads the library, the requested function and invokes it. This node takes care of
 * efficient caching of the loaded NFI objects.
 */
public class NativeLibrary {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeLibrary.class);

    /**
     * This interface is intended to be implemented by enums.
     */
    interface NativeFunction {
        String signature();

        String name();

        int ordinal();
    }

    private final int functionsCount;
    private final String name;
    private final String nfiBackendName;

    private volatile Object[] cachedFunctions;
    private volatile Object cachedLibrary;
    private volatile InteropLibrary cachedLibraryInterop;
    private volatile Object dummy;

    public NativeLibrary(String name, int functionsCount, String nfiBackendName) {
        this.functionsCount = functionsCount;
        this.name = name;
        this.nfiBackendName = nfiBackendName;
    }

    private Object getCachedLibrary(PythonContext context) {
        if (cachedLibrary == null) {
            // This should be a one-off thing for each context
            CompilerDirectives.transferToInterpreter();
            synchronized (this) {
                if (cachedLibrary == null) {
                    Source loadSrc = getNFILoadSource(context);
                    Object lib = context.getEnv().parseInternal(loadSrc).call();
                    cachedLibraryInterop = InteropLibrary.getUncached(lib);
                    cachedLibrary = lib;
                }
            }
        }
        return cachedLibrary;
    }

    private Object getCachedFunction(PythonContext context, NativeFunction function) {
        Object lib = getCachedLibrary(context);
        if (cachedFunctions == null) {
            // This should be a one-off thing for each context
            CompilerDirectives.transferToInterpreter();
            synchronized (this) {
                if (cachedFunctions == null) {
                    cachedFunctions = new Object[functionsCount];
                }
            }
        }
        int functionIndex = function.ordinal();
        if (cachedFunctions[functionIndex] == null) {
            // This should be a one-off thing for each context
            CompilerDirectives.transferToInterpreter();
            synchronized (this) {
                dummy = getFunction(lib, function);
                // it is OK to overwrite cachedFunctions[functionIndex] that may have been
                // written from another thread: no need to double check that it's still null.
                // dummy is volatile, the object must be fully initialized at this point
                cachedFunctions[functionIndex] = dummy;
            }
        }
        return cachedFunctions[functionIndex];
    }

    private Object getFunction(PythonContext context, NativeFunction function) {
        Object lib = getCachedLibrary(context);
        return getFunction(lib, function);
    }

    private Object getFunction(Object lib, NativeFunction function) {
        try {
            Object symbol = cachedLibraryInterop.readMember(lib, function.name());
            return InteropLibrary.getUncached().invokeMember(symbol, "bind", function.signature());
        } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
            throw new IllegalStateException(String.format("Cannot load symbol '%s' from the internal shared library '%s'", function.name(), name), e);
        }
    }

    private Source getNFILoadSource(PythonContext context) {
        String withClause = nfiBackendName == null ? "" : "with " + nfiBackendName;
        String path = getLibPath(context);
        String src = String.format("%s load (RTLD_LOCAL) \"%s\"", withClause, path);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Loading native library %s from path %s %s", name, path, withClause));
        }
        return Source.newBuilder("nfi", src, "load:" + name).internal(true).build();
    }

    private String getLibPath(PythonContext context) {
        String libPythonName = name + ImpModuleBuiltins.ExtensionSuffixesNode.getSoAbi(context);
        TruffleFile homePath = context.getEnv().getInternalTruffleFile(context.getCAPIHome());
        TruffleFile file = homePath.resolve(libPythonName);
        return file.getPath();
    }

    public static <T extends Enum<T> & NativeFunction> TypedNativeLibrary<T> create(String name, T[] functions) {
        return create(name, functions, null);
    }

    public static <T extends Enum<T> & NativeFunction> TypedNativeLibrary<T> create(String name, T[] functions, String nfiBackendName) {
        return new TypedNativeLibrary<T>(name, functions.length, nfiBackendName);
    }

    public static final class TypedNativeLibrary<T extends Enum<T> & NativeFunction> extends NativeLibrary {
        public TypedNativeLibrary(String name, int functionsCount, String nfiBackendName) {
            super(name, functionsCount, nfiBackendName);
        }
    }

    public abstract static class InvokeNativeFunction extends PNodeWithContext {
        private static final InvokeNativeFunction UNCACHED = InvokeNativeFunctionNodeGen.create(InteropLibrary.getUncached());
        @Child private InteropLibrary resultInterop;

        public InvokeNativeFunction(InteropLibrary resultInterop) {
            this.resultInterop = resultInterop;
        }

        public static InvokeNativeFunction create() {
            return InvokeNativeFunctionNodeGen.create(null);
        }

        public static InvokeNativeFunction getUncached() {
            return UNCACHED;
        }

        public <T extends Enum<T> & NativeFunction> Object call(TypedNativeLibrary<T> lib, T function, Object... args) {
            return execute(lib, function, args);
        }

        public <T extends Enum<T> & NativeFunction> long callLong(TypedNativeLibrary<T> lib, T function, Object... args) {
            try {
                return ensureResultInterop().asLong(call(lib, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(function.name(), e);
            }
        }

        public <T extends Enum<T> & NativeFunction> int callInt(TypedNativeLibrary<T> lib, T function, Object... args) {
            try {
                return ensureResultInterop().asInt(call(lib, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(function.name(), e);
            }
        }

        protected abstract Object execute(NativeLibrary lib, NativeFunction function, Object[] args);

        @Specialization(guards = {"function == cachedFunction", "lib == cachedLib"}, assumptions = "singleContextAssumption()", limit = "3")
        static Object doSingleContext(NativeLibrary lib, NativeFunction function, Object[] args,
                        @Cached(value = "lib", weak = true) NativeLibrary cachedLib,
                        @Cached("function") NativeFunction cachedFunction,
                        @Cached(value = "getFunction(lib, function)", weak = true) Object funObj,
                        @CachedLibrary("funObj") InteropLibrary funInterop) {
            return invoke(function, args, funObj, funInterop);
        }

        @Specialization(replaces = "doSingleContext")
        static Object doMultiContext(NativeLibrary lib, NativeFunction functionIn, Object[] args,
                        @Cached("createIdentityProfile()") ValueProfile functionProfile,
                        @Cached("createClassProfile()") ValueProfile functionClassProfile,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @CachedLibrary(limit = "1") InteropLibrary funInterop) {
            NativeFunction function = functionClassProfile.profile(functionProfile.profile(functionIn));
            Object funObj = lib.getCachedFunction(ctx, function);
            return invoke(function, args, funObj, funInterop);
        }

        private static Object invoke(NativeFunction function, Object[] args, Object funObj, InteropLibrary funInterop) {
            try {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(buildLogMessage(function, args));
                }
                Object result = funInterop.execute(funObj, args);
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(buildReturnLogMessage(function, result));
                }
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(function.name(), e);
            }
        }

        protected static Object getFunction(NativeLibrary lib, NativeFunction fun) {
            return lib.getFunction(PythonLanguage.getContext(), fun);
        }

        @TruffleBoundary
        private static String buildLogMessage(NativeFunction function, Object[] args) {
            StringBuilder sb = new StringBuilder("Executing native function ");
            sb.append(function.name()).append(" with arguments: ");
            for (Object arg : args) {
                sb.append(safeToString(arg)).append(',');
            }
            return sb.toString();
        }

        @TruffleBoundary
        private static String buildReturnLogMessage(NativeFunction function, Object result) {
            return "Finished executing native function " + function.name() + " with result: " + safeToString(result);
        }

        private static String safeToString(Object value) {
            try {
                return Objects.toString(value);
            } catch (Exception ex) {
                return String.format("%s (toString threw %s),", value.getClass().getSimpleName(), ex.getClass().getSimpleName());
            }
        }

        public InteropLibrary ensureResultInterop() {
            if (resultInterop == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resultInterop = insert(InteropLibrary.getFactory().createDispatched(2));
            }
            return resultInterop;
        }
    }
}
