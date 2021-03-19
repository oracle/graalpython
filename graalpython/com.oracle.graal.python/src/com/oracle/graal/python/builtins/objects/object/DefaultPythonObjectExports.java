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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Object.class)
final class DefaultPythonObjectExports {
    @ExportMessage
    static boolean isSequence(Object receiver,
                    @CachedLibrary("receiver") InteropLibrary interopLib) {
        return interopLib.hasArrayElements(receiver);
    }

    @ExportMessage
    static boolean isMapping(Object receiver,
                    @CachedLibrary("receiver") InteropLibrary interopLib) {
        return interopLib.hasMembers(receiver);
    }

    @ExportMessage
    static boolean canBeIndex(Object receiver,
                    @CachedLibrary("receiver") InteropLibrary interopLib) {
        return interopLib.fitsInLong(receiver);
    }

    @ExportMessage
    static Object asIndexWithState(Object receiver, @SuppressWarnings("unused") ThreadState state,
                    @Shared("raiseNode") @Cached PRaiseNode raise,
                    @CachedLibrary("receiver") InteropLibrary interopLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            if (interopLib.fitsInLong(receiver)) {
                try {
                    return interopLib.asLong(receiver);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            } else if (interopLib.isBoolean(receiver)) {
                try {
                    return interopLib.asBoolean(receiver) ? 1 : 0;
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            } else {
                throw raise.raiseIntegerInterpretationError(receiver);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static int asSizeWithState(Object receiver, Object type, ThreadState state,
                    @Shared("raiseNode") @Cached PRaiseNode raise,
                    @CachedLibrary(limit = "2") InteropLibrary interopLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object index = asIndexWithState(receiver, state, raise, interopLib, gil);
            if (interopLib.fitsInInt(index)) {
                try {
                    return interopLib.asInt(index);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            } else {
                throw raise.raiseNumberTooLarge(type, index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") Object value) {
        return PythonBuiltinClassType.ForeignObject;
    }

    @ExportMessage
    @TruffleBoundary
    static long hashWithState(Object receiver, @SuppressWarnings("unused") ThreadState state, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return receiver.hashCode();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static int lengthWithState(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                    @Shared("raiseNode") @Cached PRaiseNode raise,
                    @CachedLibrary("receiver") InteropLibrary interopLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            if (interopLib.hasArrayElements(receiver)) {
                long sz;
                try {
                    sz = interopLib.getArraySize(receiver);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                if (sz == (int) sz) {
                    return (int) sz;
                } else {
                    throw raise.raiseNumberTooLarge(PythonBuiltinClassType.OverflowError, sz);
                }
            } else {
                throw raise.raiseHasNoLength(receiver);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static class IsTrueWithState {
        @Specialization(guards = "lib.isBoolean(receiver)")
        static boolean bool(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            try {
                return lib.asBoolean(receiver);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "lib.fitsInLong(receiver)")
        static boolean integer(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            try {
                return lib.asLong(receiver) != 0;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "lib.fitsInDouble(receiver)")
        static boolean floatingPoint(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            try {
                return lib.asDouble(receiver) != 0.0;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "lib.hasArrayElements(receiver)")
        static boolean array(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            try {
                return lib.getArraySize(receiver) > 0;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {
                        "!lib.isBoolean(receiver)", "!lib.fitsInLong(receiver)",
                        "!lib.fitsInDouble(receiver)", "!lib.hasArrayElements(receiver)"
        })
        static boolean generic(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            return !lib.isNull(receiver);
        }
    }

    @ExportMessage
    static boolean isSame(Object receiver, Object other,
                    @CachedLibrary("receiver") InteropLibrary receiverLib,
                    @CachedLibrary(limit = "3") InteropLibrary otherLib) {
        return receiverLib.isIdentical(receiver, other, otherLib);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static int equalsInternal(Object receiver, Object other, ThreadState threadState,
                    @CachedLibrary("receiver") InteropLibrary receiverLib,
                    @CachedLibrary(limit = "3") InteropLibrary otherLib) {
        return receiverLib.isIdentical(receiver, other, otherLib) ? 1 : 0;
    }

    @ExportMessage
    static boolean equalsWithState(Object receiver, Object other, PythonObjectLibrary oLib, ThreadState state,
                    @CachedLibrary("receiver") InteropLibrary receiverLib,
                    @CachedLibrary(limit = "3") InteropLibrary otherLib) {
        return receiverLib.isIdentical(receiver, other, otherLib) || oLib.equalsInternal(receiver, other, state) == 1;
    }

    @ExportMessage
    static boolean isForeignObject(Object receiver,
                    @CachedLibrary("receiver") InteropLibrary lib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            try {
                return !lib.hasLanguage(receiver) || lib.getLanguage(receiver) != PythonLanguage.class;
            } catch (UnsupportedMessageException e) {
                // cannot happen due to check
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object asPStringWithState(Object receiver, ThreadState state,
                    @CachedLibrary("receiver") PythonObjectLibrary plib) {
        // Needs to go through ForeignObjectBuiltins.StrNode
        // The thread state may be necessary when the object is an array-like that contains python
        // objects whose __repr__ will be called by the library
        return plib.lookupAndCallSpecialMethodWithState(receiver, state, __STR__);
    }

    @ExportMessage
    static boolean canBeJavaDouble(@SuppressWarnings("unused") Object receiver,
                    @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        return interopLib.fitsInDouble(receiver);
    }

    @ExportMessage
    static double asJavaDoubleWithState(Object receiver, @SuppressWarnings("unused") ThreadState state,
                    @Exclusive @Cached PRaiseNode raise,
                    @CachedLibrary(limit = "1") InteropLibrary interopLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            if (canBeJavaDouble(receiver, interopLib)) {
                try {
                    return interopLib.asDouble(receiver);
                } catch (UnsupportedMessageException ex) {
                    // cannot happen due to check
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, receiver);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static boolean canBeJavaLong(@SuppressWarnings("unused") Object receiver,
                    @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        return interopLib.fitsInLong(receiver);
    }

    @ExportMessage
    static long asJavaLongWithState(Object receiver, @SuppressWarnings("unused") ThreadState state,
                    @Exclusive @Cached PRaiseNode raise,
                    @CachedLibrary(limit = "1") InteropLibrary interopLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            if (canBeJavaDouble(receiver, interopLib)) {
                try {
                    return interopLib.asLong(receiver);
                } catch (UnsupportedMessageException ex) {
                    // cannot happen due to check
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, receiver);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static boolean canBePInt(@SuppressWarnings("unused") Object receiver,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        return lib.fitsInLong(receiver);
    }

    @ExportMessage
    static long asPIntWithState(Object receiver, @SuppressWarnings("unused") ThreadState state,
                    @CachedLibrary("receiver") InteropLibrary lib,
                    @Exclusive @Cached PRaiseNode raise, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            if (lib.fitsInLong(receiver)) {
                try {
                    return lib.asLong(receiver);
                } catch (UnsupportedMessageException ex) {
                    // cannot happen due to check
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            throw raise.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, receiver);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAttributeInternal(Object receiver, ThreadState state, String name, boolean strict,
                    @Cached ConditionProfile gotState,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeNode lookup, @Shared("gil") @Cached GilNode gil) {
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
    static Object lookupAttributeOnTypeInternal(Object receiver, String name, boolean strict,
                    @CachedLibrary("receiver") PythonObjectLibrary lib,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeOnTypeNode lookup, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return lookup.execute(lib.getLazyPythonClass(receiver), name, strict);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAndCallSpecialMethodWithState(Object receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object method = plib.lookupAttributeOnTypeStrict(receiver, methodName);
            return methodLib.callUnboundMethodWithState(method, state, receiver, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object lookupAndCallRegularMethodWithState(Object receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib, @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object method = plib.lookupAttributeStrictWithState(receiver, state, methodName);
            return methodLib.callObjectWithState(method, state, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    abstract static class GetIteratorWithState {

        @Specialization(guards = "lib.isIterator(receiver)")
        static Object doForeignIterator(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @SuppressWarnings("unused") @CachedLibrary("receiver") InteropLibrary lib) {
            return receiver;
        }

        @Specialization(guards = "lib.hasIterator(receiver)")
        static Object doForeignIterable(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            try {
                return lib.getIterator(receiver);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("foreign objects claims to have an iterator but doesn't");
            }
        }

        @Specialization(guards = "lib.hasArrayElements(receiver)")
        static PForeignArrayIterator doForeignArray(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @CachedLibrary("receiver") InteropLibrary lib, @Shared("gil") @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                try {
                    long size = lib.getArraySize(receiver);
                    if (size < Integer.MAX_VALUE) {
                        return factory.createForeignArrayIterator(receiver);
                    }
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("foreign objects claims to be an array but isn't");
                }
                throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(guards = "lib.isString(receiver)")
        static PStringIterator doBoxedString(Object receiver, @SuppressWarnings("unused") ThreadState threadState,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @CachedLibrary("receiver") InteropLibrary lib, @Shared("gil") @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                try {
                    return factory.createStringIterator(lib.asString(receiver));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("foreign objects claims to be a string but isn't");
                }
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(replaces = {"doForeignArray", "doBoxedString", "doForeignIterator", "doForeignIterable"})
        static Object doGeneric(Object receiver, ThreadState threadState,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @CachedLibrary("receiver") InteropLibrary lib, @Shared("gil") @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                if (lib.isIterator(receiver)) {
                    return receiver;
                } else if (lib.hasIterator(receiver)) {
                    try {
                        return lib.getIterator(receiver);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                } else if (lib.hasArrayElements(receiver)) {
                    return doForeignArray(receiver, threadState, factory, raiseNode, lib, gil);
                } else if (lib.isString(receiver)) {
                    return doBoxedString(receiver, threadState, factory, lib, gil);
                }
                throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
            } finally {
                gil.release(mustRelease);
            }
        }

    }
}
