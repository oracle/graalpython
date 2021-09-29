/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.object;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Long.class)
final class DefaultPythonLongExports {
    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean li(Long receiver, int other) {
            return receiver == other;
        }

        @Specialization
        static boolean ll(Long receiver, long other) {
            return receiver == other;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean lP(Long receiver, PInt other,
                        @Shared("isBuiltinInt") @Cached PyLongCheckExactNode isBuiltin,
                        @Shared("gil") @Cached GilNode gil) throws OverflowException {
            boolean mustRelease = gil.acquire();
            try {
                if (isBuiltin.execute(other)) {
                    return receiver == other.longValueExact();
                }
                return false;
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(replaces = "lP", limit = "1")
        static boolean lPOverflow(Long receiver, PInt other,
                        @CachedLibrary("other") InteropLibrary otherLib,
                        @Shared("isBuiltinInt") @Cached PyLongCheckExactNode isBuiltin,
                        @Shared("gil") @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                if (isBuiltin.execute(other)) {
                    if (otherLib.fitsInLong(other)) {
                        return receiver == other.longValue();
                    }
                }
                return false;
            } finally {
                gil.release(mustRelease);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean lO(Long receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    public static Object lookupAttributeInternal(Long receiver, ThreadState state, String name, boolean strict,
                    @Cached ConditionProfile gotState,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeNode lookup,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            VirtualFrame frame = null;
            if (gotState.profile(state != null)) {
                frame = PArguments.frameForCall(state);
            }
            return lookup.execute(frame, receiver, name, strict);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAttributeOnTypeInternal(@SuppressWarnings("unused") Long receiver, String name, boolean strict,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeOnTypeNode lookup,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return lookup.execute(PythonBuiltinClassType.PInt, name, strict);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAndCallSpecialMethodWithState(Long receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object method = plib.lookupAttributeOnTypeStrict(receiver, methodName);
            return methodLib.callUnboundMethodWithState(method, state, receiver, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAndCallRegularMethodWithState(Long receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object method = plib.lookupAttributeStrictWithState(receiver, state, methodName);
            return methodLib.callObjectWithState(method, state, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static boolean typeCheck(@SuppressWarnings("unused") Long receiver, Object type,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object instanceClass = PythonBuiltinClassType.PInt;
            return isSameTypeNode.execute(instanceClass, type) || isSubtypeNode.execute(instanceClass, type);
        } finally {
            gil.release(mustRelease);
        }
    }
}
