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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.LookupAttributeNode;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Boolean.class)
final class DefaultPythonBooleanExports {
    @ExportMessage
    static boolean canBeIndex(@SuppressWarnings("unused") Boolean value) {
        return true;
    }

    @ExportMessage
    static int asIndex(Boolean value) {
        return value ? 1 : 0;
    }

    @ExportMessage
    static int asSize(Boolean value, @SuppressWarnings("unused") LazyPythonClass errorType) {
        return asIndex(value);
    }

    @ExportMessage
    static boolean isHashable(@SuppressWarnings("unused") Boolean value) {
        return true;
    }

    @ExportMessage
    static LazyPythonClass getLazyPythonClass(@SuppressWarnings("unused") Boolean value) {
        return PythonBuiltinClassType.Boolean;
    }

    @ExportMessage
    static long hash(Boolean value) {
        return hash((boolean) value);
    }

    @Ignore
    static long hash(boolean value) {
        return value ? 1 : 0;
    }

    @ExportMessage
    static boolean isTrue(Boolean value) {
        return value;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean bb(Boolean receiver, boolean other) {
            return receiver == other;
        }

        @Specialization
        static boolean bI(Boolean receiver, PInt other,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (receiver) {
                if (other == context.getCore().getTrue()) {
                    return true; // avoid the TruffleBoundary isOne call if we can
                } else if (isBuiltin.profileObject(other, PythonBuiltinClassType.Boolean)) {
                    return other.isOne();
                }
            } else {
                if (other == context.getCore().getFalse()) {
                    return true;
                } else if (isBuiltin.profileObject(other, PythonBuiltinClassType.Boolean)) {
                    return other.isZero();
                }
            }
            return false;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean bO(Boolean receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static class EqualsInternal {
        @Specialization
        static int bb(Boolean receiver, boolean other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int bi(Boolean receiver, int other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver && other == 1 || !receiver && other == 0) ? 1 : 0;
        }

        @Specialization
        static int bl(Boolean receiver, long other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver && other == 1 || !receiver && other == 0) ? 1 : 0;
        }

        @Specialization
        static int bI(Boolean receiver, PInt other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver && other.isOne() || !receiver && other.isZero()) ? 1 : 0;
        }

        @Specialization
        static int bd(Boolean receiver, double other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver && other == 1 || receiver && other == 0) ? 1 : 0;
        }

        @Specialization
        static int bF(Boolean receiver, PFloat other, @SuppressWarnings("unused") ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            // n.b.: long objects cannot compare here, but if its a builtin float we can shortcut
            if (isBuiltin.profileIsAnyBuiltinClass(other.getLazyPythonClass())) {
                return (receiver && other.getValue() == 1 || receiver && other.getValue() == 0) ? 1 : 0;
            } else {
                return -1;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static int bO(Boolean receiver, Object other, @SuppressWarnings("unused") ThreadState threadState) {
            return -1;
        }
    }

    @ImportStatic(PythonOptions.class)
    @ExportMessage
    @SuppressWarnings("unused")
    static class EqualsWithState {
        @Specialization
        static boolean bb(Boolean receiver, boolean other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean bi(Boolean receiver, int other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver ? other == 1 : other == 0;
        }

        @Specialization
        static boolean bl(Boolean receiver, long other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver ? other == 1 : other == 0;
        }

        @Specialization
        static boolean bI(Boolean receiver, PInt other, PythonObjectLibrary oLib, ThreadState threadState) {
            // n.b.: subclassing is ignored in this direction in CPython
            return receiver ? other.isOne() : other.isZero();
        }

        @Specialization
        static boolean bd(Boolean receiver, double other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver ? other == 1 : other == 0;
        }

        @Specialization
        static boolean bF(Boolean receiver, PFloat other, PythonObjectLibrary oLib, ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (isBuiltin.profileIsAnyBuiltinClass(oLib.getLazyPythonClass(other))) {
                return receiver ? other.getValue() == 1 : other.getValue() == 0;
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }

        @Specialization(replaces = {"bb", "bi", "bl", "bI", "bd", "bF"})
        static boolean bO(Boolean receiver, Object other, PythonObjectLibrary oLib, ThreadState threadState) {
            if (other instanceof Boolean) {
                return bb(receiver, (boolean) other, oLib, threadState);
            } else if (other instanceof Integer) {
                return bi(receiver, (int) other, oLib, threadState);
            } else if (other instanceof Long) {
                return bl(receiver, (long) other, oLib, threadState);
            } else if (other instanceof PInt) {
                return bI(receiver, (PInt) other, oLib, threadState);
            } else if (other instanceof Double) {
                return bd(receiver, (double) other, oLib, threadState);
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }
    }

    @ExportMessage
    static String asPString(Boolean receiver) {
        return receiver ? "True" : "False";
    }

    @ExportMessage
    static int asFileDescriptor(Boolean x) {
        return x ? 1 : 0;
    }

    @ExportMessage
    public static Object lookupAttribute(Boolean x, String name, boolean inheritedOnly,
                    @Exclusive @Cached LookupAttributeNode lookup) {
        return lookup.execute(x, name, inheritedOnly);
    }
}
