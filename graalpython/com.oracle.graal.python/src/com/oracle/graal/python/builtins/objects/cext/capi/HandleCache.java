/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public final class HandleCache implements TruffleObject {
    public static final int CACHE_SIZE = 3;

    final long[] keys;
    private final TruffleObject ptrToResolveHandle;

    int pos = 0;

    public HandleCache(TruffleObject ptrToResolveHandle) {
        keys = new long[CACHE_SIZE];
        this.ptrToResolveHandle = ptrToResolveHandle;
    }

    protected int len() {
        return keys.length;
    }

    protected TruffleObject getPtrToResolveHandle() {
        return ptrToResolveHandle;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(Object[] arguments,
                    @Cached GetOrInsertNode getOrInsertNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        if (arguments.length != 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(1, arguments.length);
        }
        return getOrInsertNode.execute(this, (long) arguments[0]);
    }

    @GenerateUncached
    @ImportStatic(HandleCache.class)
    abstract static class GetOrInsertNode extends Node {
        public abstract Object execute(HandleCache cache, long handle) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;

        @Specialization(limit = "CACHE_SIZE", //
                        guards = {"handle == cachedHandle", "cachedValue != null"}, //
                        assumptions = "singleContextAssumption()", //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper doCachedSingleContext(@SuppressWarnings("unused") HandleCache cache, @SuppressWarnings("unused") long handle,
                                                         @Cached("handle") @SuppressWarnings("unused") long cachedHandle,
                                                         @Cached("resolveHandleUncached(cache, handle)") PythonNativeWrapper cachedValue,
                                                         @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(replaces = "doCachedSingleContext", assumptions = "singleContextAssumption()")
        static Object doGenericSingleContext(@SuppressWarnings("unused") HandleCache cache, long handle,
                        @Cached(value = "cache.getPtrToResolveHandle()", allowUncached = true) TruffleObject resolveHandleFunction,
                        @CachedLibrary("resolveHandleFunction") InteropLibrary interopLibrary) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return resolveHandle(handle, resolveHandleFunction, interopLibrary);
        }

        @Specialization(limit = "3", replaces = {"doCachedSingleContext", "doGenericSingleContext"})
        static Object doGeneric(@SuppressWarnings("unused") HandleCache cache, long handle,
                        @CachedLibrary("cache.getPtrToResolveHandle()") InteropLibrary interopLibrary) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return resolveHandle(handle, cache.getPtrToResolveHandle(), interopLibrary);
        }

        static PythonNativeWrapper resolveHandleUncached(HandleCache cache, long handle)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            CompilerAsserts.neverPartOfCompilation();
            TruffleObject ptrToResolveHandle = cache.getPtrToResolveHandle();
            Object resolved = resolveHandle(handle, ptrToResolveHandle, InteropLibrary.getFactory().getUncached(ptrToResolveHandle));
            if (resolved instanceof PythonNativeWrapper) {
                return (PythonNativeWrapper) resolved;
            }
            return null;
        }

        static Object resolveHandle(long handle, TruffleObject ptrToResolveHandle, InteropLibrary interopLibrary)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return interopLibrary.execute(ptrToResolveHandle, handle);
        }

        static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        static Assumption getHandleValidAssumption(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper.ensureHandleValidAssumption();
        }
    }
}
