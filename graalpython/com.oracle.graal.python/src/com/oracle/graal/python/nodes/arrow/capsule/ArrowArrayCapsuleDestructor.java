/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow.capsule;

import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins.PyCapsuleGetPointerNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.nodes.arrow.ArrowArray;
import com.oracle.graal.python.nodes.arrow.InvokeArrowReleaseCallbackNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public class ArrowArrayCapsuleDestructor implements TruffleObject {

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static class Execute {

        @Specialization(guards = "isPointer(args, interopLib)")
        static Object doRelease(ArrowArrayCapsuleDestructor self, Object[] args,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached NativeToPythonNode nativeToPythonNode,
                        @Cached PyCapsuleGetPointerNode capsuleGetPointerNode,
                        @Cached InvokeArrowReleaseCallbackNode.Lazy invokeReleaseCallbackNode) {
            Object capsule = nativeToPythonNode.execute(args[0]);
            var capsuleName = new CArrayWrappers.CByteArrayWrapper(ArrowArray.CAPSULE_NAME);
            var arrowArray = ArrowArray.wrap((long) capsuleGetPointerNode.execute(inliningTarget, capsule, capsuleName));
            /*
             * The exported PyCapsules should have a destructor that calls the release callback of
             * the Arrow struct, if it is not already null. This prevents a memory leak in case the
             * capsule was never passed to another consumer.
             *
             * For more information see:
             * https://arrow.apache.org/docs/format/CDataInterface/PyCapsuleInterface.html#lifetime-
             * semantics
             */
            if (!arrowArray.isReleased()) {
                invokeReleaseCallbackNode.get(inliningTarget).executeCached(arrowArray.releaseCallback(), arrowArray.memoryAddress());
            }
            PythonContext.get(inliningTarget).getUnsafe().freeMemory(arrowArray.memoryAddress());
            return PNone.NO_VALUE;
        }

        @Fallback
        static Object doError(ArrowArrayCapsuleDestructor self, Object[] args) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        static boolean isPointer(Object[] args, InteropLibrary interopLib) {
            return args.length == 1 && interopLib.isPointer(args[0]);
        }
    }
}
