/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = String.class)
final class DefaultPythonStringExports {
    @ExportMessage
    static boolean isIterable(@SuppressWarnings("unused") String str) {
        return true;
    }

    @ExportMessage
    static boolean isHashable(@SuppressWarnings("unused") String value) {
        return true;
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") String value) {
        return PythonBuiltinClassType.PString;
    }

    @ExportMessage
    static boolean isBuffer(@SuppressWarnings("unused") String str) {
        return false;
    }

    @ExportMessage
    static int getBufferLength(@SuppressWarnings("unused") String str) {
        return getBufferBytes(str).length;
    }

    @ExportMessage
    @TruffleBoundary
    static byte[] getBufferBytes(String str) {
        return str.getBytes();
    }

    @ExportMessage
    @TruffleBoundary
    static long hash(String self) {
        return self.hashCode();
    }

    @ExportMessage
    static int length(String self) {
        return self.length();
    }

    @ExportMessage
    static boolean isTrue(String self) {
        return self.length() > 0;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean ss(String receiver, String other) {
            return receiver == other;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean sO(String receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static class EqualsInternal {
        @Specialization
        static int ss(String receiver, String other, @SuppressWarnings("unused") ThreadState threadState) {
            return PString.equals(receiver, other) ? 1 : 0;
        }

        @Specialization
        static int sP(String receiver, PString other, @SuppressWarnings("unused") ThreadState threadState,
                        @Cached CastToJavaStringNode castNode) {
            // n.b.: subclassing is ignored in this direction in CPython
            String otherString = null;
            try {
                otherString = castNode.execute(other);
            } catch (CannotCastException e) {
                return -1;
            }
            return ss(receiver, otherString, threadState);
        }

        @Fallback
        @SuppressWarnings("unused")
        static int iO(String receiver, Object other, @SuppressWarnings("unused") ThreadState threadState) {
            return -1;
        }
    }

    @ExportMessage
    static String asPath(String value) {
        return value;
    }

    @ExportMessage
    static String asPString(String receiver) {
        return receiver;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    static boolean canBePInt(@SuppressWarnings("unused") String receiver) {
        return false;
    }

    @ExportMessage
    static int asPInt(String receiver,
                    @Exclusive @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, receiver);
    }

    @ExportMessage
    static boolean canBeJavaLong(@SuppressWarnings("unused") String receiver) {
        return false;
    }

    @ExportMessage
    static long asJavaLong(String receiver,
                    @Exclusive @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.MUST_BE_NUMERIC, receiver);
    }

    @ExportMessage
    static boolean canBeJavaDouble(@SuppressWarnings("unused") String receiver) {
        return false;
    }

    @ExportMessage
    static double asJavaDouble(String receiver,
                    @Exclusive @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, receiver);
    }

    @ExportMessage
    static Object lookupAttributeInternal(String receiver, ThreadState state, String name, boolean strict,
                    @Cached ConditionProfile gotState,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeNode lookup) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return lookup.execute(frame, receiver, name, strict);
    }

    @ExportMessage
    static Object lookupAttributeOnTypeInternal(@SuppressWarnings("unused") String receiver, String name, boolean strict,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeOnTypeNode lookup) {
        return lookup.execute(PythonBuiltinClassType.PString, name, strict);
    }

    @ExportMessage
    static Object lookupAndCallSpecialMethodWithState(String receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeOnTypeStrict(receiver, methodName);
        return methodLib.callUnboundMethodWithState(method, state, receiver, arguments);
    }

    @ExportMessage
    static Object lookupAndCallRegularMethodWithState(String receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeStrictWithState(receiver, state, methodName);
        return methodLib.callObjectWithState(method, state, arguments);
    }

    @ExportMessage
    static boolean typeCheck(@SuppressWarnings("unused") String receiver, Object type,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached IsSubtypeNode isSubtypeNode) {
        Object instanceClass = PythonBuiltinClassType.PString;
        return isSameTypeNode.execute(instanceClass, type) || isSubtypeNode.execute(instanceClass, type);
    }
}
