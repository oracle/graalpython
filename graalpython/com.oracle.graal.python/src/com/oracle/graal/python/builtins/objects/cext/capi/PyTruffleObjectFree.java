/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ClearNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public class PyTruffleObjectFree implements TruffleObject {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleObjectFree.class);

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Cached FreeNode freeNode) throws ArityException {
        if (arguments.length != 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(1, arguments.length);
        }
        return freeNode.execute(arguments[0]);
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class FreeNode extends Node {

        public abstract int execute(Object pointerObject);

        @Specialization(limit = "3")
        static int doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @CachedLibrary("nativeWrapper") PythonNativeWrapperLibrary lib,
                        @Cached ClearNativeWrapperNode clearNativeWrapperNode,
                        @Cached PCallCapiFunction callReleaseHandleNode) {
            if (nativeWrapper.getRefCount() > 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("deallocating native object with refcnt > 0");
            }

            // clear native wrapper
            Object delegate = lib.getDelegate(nativeWrapper);
            clearNativeWrapperNode.execute(delegate, nativeWrapper);

            ReleaseHandleNode.doNativeWrapper(nativeWrapper, lib, callReleaseHandleNode);
            return 1;
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        static int doOther(@SuppressWarnings("unused") Object object) {
            // It's a pointer to a managed object but none of our wrappers, so we just ignore it.
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class ReleaseHandleNode extends Node {

        public abstract void execute(PythonNativeWrapper nativeWrapper);

        @Specialization(limit = "3")
        static void doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @CachedLibrary("nativeWrapper") PythonNativeWrapperLibrary lib,
                        @Cached PCallCapiFunction callReleaseHandleNode) {

            // If wrapper already received toNative, release the handle or free the native memory.
            if (lib.isNative(nativeWrapper)) {
                // We do not call 'truffle_release_handle' directly because we still want to support
                // native wrappers that have a real native pointer. 'PyTruffle_Free' does the
                // necessary distinction.
                Object nativePointer = lib.getNativePointer(nativeWrapper);
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(() -> String.format("Releasing handle: %s (object: %s)", nativePointer, nativeWrapper));
                }
                callReleaseHandleNode.call(NativeCAPISymbols.FUN_PY_TRUFFLE_FREE, nativePointer);
            }
        }
    }
}
