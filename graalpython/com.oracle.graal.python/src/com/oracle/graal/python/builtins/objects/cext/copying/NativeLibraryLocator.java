/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.copying;

import static com.oracle.graal.python.nodes.StringLiterals.J_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.T_BASE_PREFIX;
import static com.oracle.graal.python.nodes.StringLiterals.J_MAX_CAPI_COPIES;
import static com.oracle.graal.python.nodes.StringLiterals.T_PREFIX;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;

/**
 * Given a GraalPy virtual environment, this class helps prepare that environment so that multiple
 * GraalPy contexts in the same process can use its native extensions at the same time.
 *
 * The mechanism for this is tied to the implementation of GraalPy and the resulting venv is only
 * guaranteed to work with the matching GraalPy version.
 */
public final class NativeLibraryLocator {
    static final TruffleLogger LOGGER = PythonLanguage.getLogger("NativeLibraryLocator");

    private static final int MAX_CEXT_COPIES = Integer.getInteger(J_MAX_CAPI_COPIES, 64);

    /**
     * Bitset for which copied C extension to use when {@link PythonOptions#IsolateNativeModules} is
     * enabled.
     */
    private static final AtomicInteger CEXT_COPY_INDICES = new AtomicInteger(MAX_CEXT_COPIES);

    /**
     * The suffix to add to C extensions when loading. This allows us to support native module
     * isolation with {@link PythonOptions#IsolateNativeModules}. If the value is {@code -1}, it
     * means we are not using isolation.
     */
    private final int capiSlot;

    /**
     * The original C API library <strong>filename</strong>.
     */
    private final String capiOriginal;

    /**
     * The original C API library <strong>path</strong>.
     */
    private final String capiCopy;

    /**
     * Create a locator for native extension libraries.
     *
     * @param capiLibrary - the library file that implements the C API symbols for all extensions to
     *            bind to
     * @param isolateNative - if {@code true}, look for or create relocated copies of native
     *            libraries to load, else just load the originals
     */
    public NativeLibraryLocator(PythonContext context, TruffleFile capiLibrary, boolean isolateNative) throws ApiInitException {
        this.capiOriginal = capiLibrary.getName();
        if (isolateNative) {
            this.capiSlot = MAX_CEXT_COPIES - CEXT_COPY_INDICES.getAndDecrement();
            if (this.capiSlot < 0) {
                CEXT_COPY_INDICES.set(0);
                throw new ApiInitException(ErrorMessages.CAPI_ISOLATION_CAPPED_AT_D, MAX_CEXT_COPIES);
            }
            this.capiCopy = resolve(context, capiLibrary, capiSlot, null);
        } else {
            this.capiSlot = -1;
            this.capiCopy = capiLibrary.getPath();
        }
    }

    /**
     * Determine path of actual shared object to load for the given capi slot. If the capi slot is
     * {@code -1}, return the {@code original} argument's path. Otherwise, look for a relocated copy
     * corresponding to the desired slot.
     *
     * @see PythonOptions#IsolateNativeModules
     */
    public String resolve(PythonContext context, TruffleFile original) throws ImportException {
        try {
            return resolve(context, original, capiSlot, capiOriginal);
        } catch (ApiInitException e) {
            throw new ImportException(null, toTruffleStringUncached(original.getName()), toTruffleStringUncached(original.getPath()), toTruffleStringUncached(e.getMessage()));
        }
    }

    public String getCapiLibrary() {
        return capiCopy;
    }

    public void close() {
    }

    /**
     * Prepare the {@code venvDirectory} for execution with multiple contexts using C extensions at
     * the same time. The minimum number of concurrent contexts to prepare for is given with {@code
     * count}.
     */
    public static void replicate(TruffleFile venvDirectory, PythonContext context, int count) throws IOException, InterruptedException {
        if (count > MAX_CEXT_COPIES) {
            LOGGER.warning(() -> String.format("The current limit for concurrent Python contexts accessing the Python C API is %d, " +
                            "but we are preparing %d copies. The extra copies will only be used if a different value " +
                            "of the system property %s is set.", MAX_CEXT_COPIES, count, J_MAX_CAPI_COPIES));
        }
        String suffix = context.getSoAbi().toJavaStringUncached();
        TruffleFile capiLibrary = context.getPublicTruffleFileRelaxed(context.getCAPIHome()).resolve(PythonContext.getSupportLibName("python-" + J_NATIVE));
        try {
            for (int i = 0; i < count; i++) {
                // Relocate the C API library
                replicate(capiLibrary, venvDirectory.resolve(copyNameOf(capiLibrary.getName(), i)), context, i);
                // Relocate the core C extensions
                walk(context.getPublicTruffleFileRelaxed(context.getCoreHome()), suffix, capiLibrary.getName(), context, i, (o, n) -> venvDirectory.resolve(n));
                // Relocate C extensions in the venv
                walk(venvDirectory, suffix, capiLibrary.getName(), context, i, (o, n) -> o.resolveSibling(n));
            }
        } catch (RuntimeException e) {
            var cause = e.getCause();
            if (cause instanceof IOException ioCause) {
                throw ioCause;
            } else if (cause instanceof InterruptedException intCause) {
                throw intCause;
            } else {
                throw e;
            }
        }
    }

    private static String copyNameOf(String original, int capiSlot) {
        return original + ".dup" + Integer.toHexString(capiSlot);
    }

    private static String resolve(PythonContext context, TruffleFile original, int capiSlot, String capiOrignalName) throws ApiInitException {
        if (capiSlot < 0) {
            return original.getPath();
        }
        Env env = context.getEnv();
        TruffleFile copy;
        String newName = copyNameOf(original.getName(), capiSlot);

        if (original.getAbsoluteFile().startsWith(context.getCoreHome().toJavaStringUncached())) {
            // must be relocated to venv
            Object sysPrefix = context.getSysModule().getAttribute(T_PREFIX);
            Object sysBasePrefix = context.getSysModule().getAttribute(T_BASE_PREFIX);
            if (sysPrefix.equals(sysBasePrefix)) {
                throw new ApiInitException(ErrorMessages.SYS_PREFIX_MUST_POINT_TO_A_VENV_FOR_CAPI_ISOLATION);
            } else {
                try {
                    var tsSysPrefix = CastToTruffleStringNode.executeUncached(sysPrefix);
                    copy = env.getPublicTruffleFile(tsSysPrefix.toJavaStringUncached()).resolve(newName);
                } catch (CannotCastException e) {
                    throw new ApiInitException(ErrorMessages.SYS_PREFIX_MUST_BE_STRING_NOT_P_FOR_CAPI_ISOLATION, sysPrefix);
                }
            }
        } else {
            copy = original.resolveSibling(newName);
        }
        if (!copy.isReadable()) {
            try {
                replicate(original, copy, context, capiSlot, capiOrignalName);
            } catch (IOException | InterruptedException e) {
                throw new ApiInitException(e);
            }
        }
        return copy.getPath();
    }

    private static void replicate(TruffleFile original, TruffleFile copy, PythonContext context, int slot, String... dependenciesToUpdate) throws IOException, InterruptedException {
        try (var o = SharedObject.open(original, context)) {
            for (var depToUpdate : dependenciesToUpdate) {
                if (depToUpdate != null) {
                    var newDepName = copyNameOf(depToUpdate, slot);
                    o.changeOrAddDependency(depToUpdate, newDepName);
                }
            }
            o.setId(copy.getName());
            o.write(copy);
        }
    }

    private static void walk(TruffleFile dir, String suffix, String capiOriginalName, PythonContext context, int capiSlot, BiFunction<TruffleFile, String, TruffleFile> f)
                    throws IOException, InterruptedException {
        try (var ds = dir.newDirectoryStream()) {
            for (var e : ds) {
                if (e.isDirectory()) {
                    walk(e, suffix, capiOriginalName, context, capiSlot, f);
                } else if (e.getName().endsWith(suffix) && e.isRegularFile()) {
                    replicate(e, f.apply(e, copyNameOf(e.getName(), capiSlot)), context, capiSlot, capiOriginalName);
                }
            }
        }
    }
}
