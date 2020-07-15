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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.LookupAttributeNode;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Integer.class)
final class DefaultPythonIntegerExports {
    @ExportMessage
    static boolean isHashable(@SuppressWarnings("unused") Integer value) {
        return true;
    }

    @ExportMessage
    static boolean canBeIndex(@SuppressWarnings("unused") Integer value) {
        return true;
    }

    @ExportMessage
    static int asIndex(Integer value) {
        return value;
    }

    @ExportMessage
    static int asSize(Integer value, @SuppressWarnings("unused") Object errorType) {
        return value;
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") Integer value) {
        return PythonBuiltinClassType.PInt;
    }

    @ExportMessage
    static long hash(Integer value) {
        return value;
    }

    @Ignore
    static long hash(int value) {
        return value;
    }

    @ExportMessage
    static boolean isTrue(Integer value) {
        return value != 0;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean ii(Integer receiver, int other) {
            return receiver == other;
        }

        @Specialization
        static boolean il(Integer receiver, long other) {
            return receiver == other;
        }

        @Specialization
        static boolean iP(Integer receiver, PInt other,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (isBuiltin.profileObject(other, PythonBuiltinClassType.PInt)) {
                try {
                    return receiver == other.intValueExact();
                } catch (ArithmeticException e) {
                    // pass
                }
            }
            return false;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean iO(Integer receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static class EqualsInternal {
        @Specialization
        static int ib(Integer receiver, boolean other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver == 1 && other || receiver == 0 && !other) ? 1 : 0;
        }

        @Specialization
        static int ii(Integer receiver, int other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int il(Integer receiver, long other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int iI(Integer receiver, PInt other, @SuppressWarnings("unused") ThreadState threadState) {
            return other.compareTo((int) receiver) == 0 ? 1 : 0;
        }

        @Specialization
        static int id(Integer receiver, double other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int iF(Integer receiver, PFloat other, @SuppressWarnings("unused") ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            // n.b.: long objects cannot compare here, but if its a builtin float we can shortcut
            if (isBuiltin.profileIsAnyBuiltinClass(lib.getLazyPythonClass(other))) {
                return other.getValue() == receiver ? 1 : 0;
            } else {
                return -1;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static int iO(Integer receiver, Object other, @SuppressWarnings("unused") ThreadState threadState) {
            return -1;
        }
    }

    @ImportStatic(PythonOptions.class)
    @ExportMessage
    @SuppressWarnings("unused")
    static class EqualsWithState {
        @Specialization
        static boolean ib(Integer receiver, boolean other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == 1 && other || receiver == 0 && !other;
        }

        @Specialization
        static boolean ii(Integer receiver, int other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean il(Integer receiver, long other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean iI(Integer receiver, PInt other, PythonObjectLibrary oLib, ThreadState threadState) {
            return other.compareTo((int) receiver) == 0;
        }

        @Specialization
        static boolean id(Integer receiver, double other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean iF(Integer receiver, PFloat other, PythonObjectLibrary oLib, ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            // n.b.: long objects cannot compare here, but if its a builtin float we can shortcut
            if (isBuiltin.profileIsAnyBuiltinClass(oLib.getLazyPythonClass(other))) {
                return receiver == other.getValue();
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }

        @Specialization(replaces = {"ib", "ii", "il", "iI", "id", "iF"})
        static boolean iO(Integer receiver, Object other, PythonObjectLibrary oLib, ThreadState threadState) {
            if (other instanceof Boolean) {
                return ib(receiver, (boolean) other, oLib, threadState);
            } else if (other instanceof Integer) {
                return ii(receiver, (int) other, oLib, threadState);
            } else if (other instanceof Long) {
                return il(receiver, (long) other, oLib, threadState);
            } else if (other instanceof PInt) {
                return iI(receiver, (PInt) other, oLib, threadState);
            } else if (other instanceof Double) {
                return id(receiver, (double) other, oLib, threadState);
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    static String asPString(Integer receiver) {
        return Integer.toString(receiver);
    }

    @ExportMessage
    static int asFileDescriptor(Integer x) {
        return x;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    static boolean canBeJavaDouble(@SuppressWarnings("unused") Integer receiver) {
        return true;
    }

    @ExportMessage
    static double asJavaDouble(Integer receiver) {
        return receiver.doubleValue();
    }

    @ExportMessage
    static boolean canBeJavaLong(@SuppressWarnings("unused") Integer receiver) {
        return true;
    }

    @ExportMessage
    static long asJavaLong(Integer receiver) {
        return receiver;
    }

    @ExportMessage
    static boolean canBePInt(@SuppressWarnings("unused") Integer receiver) {
        return true;
    }

    @ExportMessage
    static int asPInt(Integer receiver) {
        return receiver;
    }

    @ExportMessage
    public static Object lookupAttribute(Integer x, String name, boolean inheritedOnly,
                    @Exclusive @Cached LookupAttributeNode lookup) {
        return lookup.execute(x, name, inheritedOnly);
    }
}
