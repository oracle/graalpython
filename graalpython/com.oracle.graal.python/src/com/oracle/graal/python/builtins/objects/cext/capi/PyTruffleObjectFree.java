/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ClearNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleReleaser;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class PyTruffleObjectFree {
    private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffleObjectFree.class);

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class FreeNode extends Node {

        public abstract int execute(Node inliningTarget, Object pointerObject);

        @Specialization(guards = "!isCArrayWrapper(nativeWrapper)")
        static int doNativeWrapper(Node inliningTarget, PythonNativeWrapper nativeWrapper,
                        @Cached(inline = false) PCallCapiFunction callReleaseHandleNode,
                        @Cached ClearNativeWrapperNode clearNativeWrapperNode) {
            // if (nativeWrapper.getRefCount() > 0) {
            // CompilerDirectives.transferToInterpreterAndInvalidate();
            // throw new IllegalStateException("deallocating native object with refcnt > 0");
            // }

            // clear native wrapper
            Object delegate = nativeWrapper.getDelegate();
            clearNativeWrapperNode.execute(inliningTarget, delegate, nativeWrapper);
            PyTruffleObjectFree.releaseNativeWrapper(nativeWrapper, callReleaseHandleNode);
            return 1;
        }

        @Specialization
        static int arrayWrapper(@SuppressWarnings("unused") CArrayWrapper object) {
            // It's a pointer to a managed object but doesn't need special handling, so we just
            // ignore it.
            return 1;
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        static int doOther(@SuppressWarnings("unused") Object object) {
            // It's a pointer to a managed object but none of our wrappers, so we just ignore it.
            return 0;
        }

        protected static boolean isCArrayWrapper(Object obj) {
            return obj instanceof CArrayWrapper;
        }
    }

    @TruffleBoundary
    public static void releaseNativeWrapperUncached(PythonNativeWrapper nativeWrapper) {
        releaseNativeWrapper(nativeWrapper, PCallCapiFunction.getUncached());
    }

    /**
     * Releases a native wrapper. This requires to remove the native wrapper from any lookup tables
     * and to free potentially allocated native resources. If native wrappers receive
     * {@code toNative}, either a <it>handle pointer</it> is allocated or some off-heap memory is
     * allocated. This method takes care of that and will also free any off-heap memory.
     */
    static void releaseNativeWrapper(PythonNativeWrapper nativeWrapper, PCallCapiFunction callReleaseHandleNode) {

        // If wrapper already received toNative, release the handle or free the native memory.
        if (nativeWrapper.isNative()) {
            long nativePointer = nativeWrapper.getNativePointer();
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer(PythonUtils.formatJString("Releasing handle: %x (object: %s)", nativePointer, nativeWrapper));
            }
            if (HandlePointerConverter.pointsToPyHandleSpace(nativePointer)) {
                HandleReleaser.release(nativePointer);
            } else {
                CApiTransitions.nativeLookupRemove(PythonContext.get(callReleaseHandleNode).nativeContext, nativePointer);
                callReleaseHandleNode.call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, nativePointer);
            }
        }
    }
}
