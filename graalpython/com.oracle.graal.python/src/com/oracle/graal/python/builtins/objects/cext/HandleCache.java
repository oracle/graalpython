/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
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
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public final class HandleCache implements TruffleObject {
    public static final int CACHE_SIZE = 10;

    final long[] keys;
    final Object[] values;
    private final TruffleObject ptrToResolveHandle;

    int pos = 0;

    public HandleCache(TruffleObject ptrToResolveHandle) {
        keys = new long[CACHE_SIZE];
        values = new Object[CACHE_SIZE];
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
                    @Cached GetOrInsertNode getOrInsertNode,
                    @Cached BranchProfile invalidArgCountProfile) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        if (arguments.length != 1) {
            invalidArgCountProfile.enter();
            throw ArityException.create(1, arguments.length);
        }
        return getOrInsertNode.execute(this, (long) arguments[0]);
    }

    static class InvalidCacheEntryException extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        public static final InvalidCacheEntryException INSTANCE = new InvalidCacheEntryException();
    }

    @GenerateUncached
    @ImportStatic(HandleCache.class)
    abstract static class GetOrInsertNode extends Node {
        public abstract Object execute(HandleCache cache, long handle) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;

        @Specialization(limit = "CACHE_SIZE", guards = {"cache.len() == cachedLen",
                        "handle == cachedHandle"}, rewriteOn = InvalidCacheEntryException.class, assumptions = "singleContextAssumption()")
        Object doCachedSingleContext(HandleCache cache, @SuppressWarnings("unused") long handle,
                        @Cached("handle") long cachedHandle,
                        @Cached("cache.len()") @SuppressWarnings("unused") int cachedLen,
                        @Cached("cache.getPtrToResolveHandle()") @SuppressWarnings("unused") TruffleObject cachedResolveHandleFunction,
                        @Cached("lookupPositionUncached(cache, handle, cachedLen, cachedResolveHandleFunction)") int cachedPosition) throws InvalidCacheEntryException {
            if (cache.keys[cachedPosition] == cachedHandle) {
                return cache.values[cachedPosition];
            }
            throw InvalidCacheEntryException.INSTANCE;
        }

        @Specialization(guards = {"cache.len() == cachedLen"}, replaces = "doCachedSingleContext", assumptions = "singleContextAssumption()")
        Object doFullLookupSingleContext(HandleCache cache, long handle,
                        @Cached(value = "cache.len()", allowUncached = true) int cachedLen,
                        @Cached(value = "cache.getPtrToResolveHandle()", allowUncached = true) TruffleObject resolveHandleFunction,
                        @CachedLibrary("resolveHandleFunction") InteropLibrary interopLibrary) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            int pos = lookupPosition(cache, handle, cachedLen, resolveHandleFunction, interopLibrary);
            return cache.values[pos];
        }

        @Specialization(limit = "CACHE_SIZE", guards = {"cache.len() == cachedLen",
                        "handle == cachedHandle", "cache.getPtrToResolveHandle() == cachedResolveHandleFunction"}, rewriteOn = InvalidCacheEntryException.class)
        Object doCached(HandleCache cache, @SuppressWarnings("unused") long handle,
                        @Cached("handle") long cachedHandle,
                        @Cached("cache.len()") @SuppressWarnings("unused") int cachedLen,
                        @Cached("cache.getPtrToResolveHandle()") @SuppressWarnings("unused") TruffleObject cachedResolveHandleFunction,
                        @Cached("lookupPositionUncached(cache, handle, cachedLen, cachedResolveHandleFunction)") int cachedPosition) throws InvalidCacheEntryException {
            if (cache.keys[cachedPosition] == cachedHandle) {
                return cache.values[cachedPosition];
            }
            throw InvalidCacheEntryException.INSTANCE;
        }

        @Specialization(guards = {"cache.len() == cachedLen", "cache.getPtrToResolveHandle() == cachedResolveHandleFunction"}, replaces = "doCached")
        Object doFullLookup(HandleCache cache, long handle,
                        @Cached(value = "cache.len()", allowUncached = true) int cachedLen,
                        @Cached(value = "cache.getPtrToResolveHandle()", allowUncached = true) TruffleObject cachedResolveHandleFunction,
                        @CachedLibrary("cachedResolveHandleFunction") InteropLibrary interopLibrary) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            int pos = lookupPosition(cache, handle, cachedLen, cachedResolveHandleFunction, interopLibrary);
            return cache.values[pos];
        }

        protected static int lookupPositionUncached(HandleCache cache, long handle, int len, TruffleObject ptrToResolveHandle)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            CompilerAsserts.neverPartOfCompilation();
            return lookupPosition(cache, handle, len, ptrToResolveHandle, InteropLibrary.getFactory().getUncached(ptrToResolveHandle));
        }

        @ExplodeLoop
        protected static int lookupPosition(HandleCache cache, long handle, int cachedLen, TruffleObject ptrToResolveHandle, InteropLibrary interopLibrary)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            for (int i = 0; i < cachedLen; i++) {
                if (cache.keys[i] == handle) {
                    return i;
                }
            }

            Object resolved = interopLibrary.execute(ptrToResolveHandle, handle);

            int insertPos = cache.pos;
            cache.keys[insertPos] = handle;
            cache.values[insertPos] = resolved;
            cache.pos = (insertPos + 1) % cache.len();

            return insertPos;
        }

        protected static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }
    }
}
