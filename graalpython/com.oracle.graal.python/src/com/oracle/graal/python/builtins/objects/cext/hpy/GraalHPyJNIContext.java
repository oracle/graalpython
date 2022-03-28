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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextMember;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextNativePointer;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * This object is used to override specific native upcall pointers in the HPyContext. This is
 * queried for every member of HPyContext by {@code graal_hpy_context_to_native}, and overrides the
 * original values (which are NFI closures for functions in {@code hpy.c}, subsequently calling into
 * {@link GraalHPyContextFunctions}.
 */
@ExportLibrary(InteropLibrary.class)
final class GraalHPyJNIContext implements TruffleObject {

    GraalHPyJNIContext(@SuppressWarnings("unused") GraalHPyContext context) {
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HPyContextMember.KEYS;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    boolean isMemberReadable(String key) {
        return HPyContextMember.getIndex(key) != -1;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    Object readMember(@SuppressWarnings("unused") String key) {
        return new HPyContextNativePointer(0L);
    }

    enum JNIFunctionSignature {
        PRIMITIVE1(1),
        PRIMITIVE2(2),
        PRIMITIVE3(3),
        PRIMITIVE4(4),
        PRIMITIVE5(5),
        PRIMITIVE6(6),
        PRIMITIVE7(7),
        PRIMITIVE8(8),
        PRIMITIVE9(9),
        PRIMITIVE10(10),
        INQUIRY(2),
        SSIZEOBJARGPROC(4),
        SSIZESSIZEOBJARGPROC(5),
        OBJOBJPROC(3),
        OBJOBJARGPROC(4),
        INITPROC(5),
        DESTROYFUNC(1),
        FREEFUNC(2),
        GETBUFFERPROC(4),
        RELEASEBUFFERPROC(3),
        RICHCOMPAREFUNC(4),
        DESTRUCTOR(2);

        final int arity;

        JNIFunctionSignature(int arity) {
            this.arity = arity;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyJNIFunctionPointer implements TruffleObject {
        final long pointer;
        final JNIFunctionSignature signature;

        GraalHPyJNIFunctionPointer(long pointer, JNIFunctionSignature signature) {
            this.pointer = pointer;
            this.signature = signature;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static class Execute {

            @Specialization(guards = "receiver.signature == cachedSignature")
            static Object doCached(GraalHPyJNIFunctionPointer receiver, Object[] arguments,
                            @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                            @Cached("receiver.signature") JNIFunctionSignature cachedSignature,
                            @Cached(parameters = "receiver.signature") GraalHPyJNIConvertArgNode convertArgNode) {
                long result;
                switch (cachedSignature) {
                    case PRIMITIVE1:
                        result = GraalHPyContext.executePrimitive1(receiver.pointer, convertHPyContext(arguments, interopLibrary));
                        break;
                    case PRIMITIVE2:
                        result = GraalHPyContext.executePrimitive2(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1));
                        break;
                    case PRIMITIVE3:
                        result = GraalHPyContext.executePrimitive3(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                        break;
                    case PRIMITIVE4:
                        result = GraalHPyContext.executePrimitive4(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                        break;
                    case PRIMITIVE5:
                        result = GraalHPyContext.executePrimitive5(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4));
                        break;
                    case PRIMITIVE6:
                        result = GraalHPyContext.executePrimitive6(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4), convertArgNode.execute(arguments, 5));
                        break;
                    case PRIMITIVE7:
                        result = GraalHPyContext.executePrimitive7(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4), convertArgNode.execute(arguments, 5),
                                        convertArgNode.execute(arguments, 6));
                        break;
                    case PRIMITIVE8:
                        result = GraalHPyContext.executePrimitive8(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4), convertArgNode.execute(arguments, 5),
                                        convertArgNode.execute(arguments, 6), convertArgNode.execute(arguments, 7));
                        break;
                    case PRIMITIVE9:
                        result = GraalHPyContext.executePrimitive9(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4), convertArgNode.execute(arguments, 5),
                                        convertArgNode.execute(arguments, 6), convertArgNode.execute(arguments, 7), convertArgNode.execute(arguments, 8));
                        break;
                    case PRIMITIVE10:
                        result = GraalHPyContext.executePrimitive10(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4), convertArgNode.execute(arguments, 5),
                                        convertArgNode.execute(arguments, 6), convertArgNode.execute(arguments, 7), convertArgNode.execute(arguments, 8), convertArgNode.execute(arguments, 9));
                        break;
                    case INQUIRY:
                        result = GraalHPyContext.executeInquiry(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1));
                        break;
                    case SSIZEOBJARGPROC:
                        result = GraalHPyContext.executeSsizeobjargproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1), (long) arguments[2],
                                        convertArgNode.execute(arguments, 3));
                        break;
                    case SSIZESSIZEOBJARGPROC:
                        result = GraalHPyContext.executeSsizesizeobjargproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1), (long) arguments[2],
                                        (long) arguments[3], convertArgNode.execute(arguments, 4));
                        break;
                    case OBJOBJPROC:
                        result = GraalHPyContext.executeObjobjproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                        break;
                    case OBJOBJARGPROC:
                        result = GraalHPyContext.executeObjobjargproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                        break;
                    case INITPROC:
                        result = GraalHPyContext.executeInitproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (long) arguments[3], convertArgNode.execute(arguments, 4));
                        break;
                    case DESTROYFUNC:
                        GraalHPyContext.hpyCallDestroyFunc(convertPointer(arguments[0], interopLibrary), receiver.pointer);
                        result = 0;
                        break;
                    case FREEFUNC:
                        GraalHPyContext.executeFreefunc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1));
                        result = 0;
                        break;
                    case GETBUFFERPROC:
                        result = GraalHPyContext.executeGetbufferproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                        break;
                    case RELEASEBUFFERPROC:
                        GraalHPyContext.executeReleasebufferproc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                        result = 0;
                        break;
                    case RICHCOMPAREFUNC:
                        result = GraalHPyContext.executeRichcomparefunc(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                        break;
                    case DESTRUCTOR:
                        GraalHPyContext.executeDestructor(receiver.pointer, convertHPyContext(arguments, interopLibrary), convertArgNode.execute(arguments, 1));
                        result = 0;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                return result;
            }

            private static long convertHPyContext(Object[] arguments, InteropLibrary interopLibrary) {
                GraalHPyContext hPyContext = GraalHPyJNIConvertArgNode.getHPyContext(arguments);
                if (!hPyContext.isPointer()) {
                    hPyContext.toNative();
                }
                try {
                    return hPyContext.asPointer(interopLibrary);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            private static long convertPointer(Object argument, InteropLibrary interopLibrary) {
                if (!interopLibrary.isPointer(argument)) {
                    interopLibrary.toNative(argument);
                }
                try {
                    return interopLibrary.asPointer(argument);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    abstract static class GraalHPyJNIConvertArgNode extends Node {

        private static final GraalHPyJNIConvertArgUncachedNode UNCACHED = new GraalHPyJNIConvertArgUncachedNode();

        public static GraalHPyJNIConvertArgNode create(@SuppressWarnings("unused") JNIFunctionSignature signature) {
            return new GraalHPyJNIConvertArgCachedNode();
        }

        public static GraalHPyJNIConvertArgNode getUncached(@SuppressWarnings("unused") JNIFunctionSignature signature) {
            return UNCACHED;
        }

        public abstract long execute(Object[] arguments, int i);

        protected static GraalHPyContext getHPyContext(Object[] arguments) {
            Object ctx = arguments[0];
            if (ctx instanceof GraalHPyContext) {
                return (GraalHPyContext) ctx;
            }
            throw CompilerDirectives.shouldNotReachHere("first argument is expected to the HPy context");
        }

        static final class GraalHPyJNIConvertArgCachedNode extends GraalHPyJNIConvertArgNode {
            /**
             * Carefully picked limit. Expected possible argument object types are: LLVM native
             * pointer, LLVM managed pointer, {@link GraalHPyContext}, and {@link GraalHPyHandle}.
             */
            private static final int CACHE_LIMIT = 4;

            @Child private InteropLibrary interopLibrary;
            @CompilationFinal private ConditionProfile profile;

            @Override
            public long execute(Object[] arguments, int i) {
                CompilerAsserts.partialEvaluationConstant(i);
                // TODO(fa): improved cached implementation; use state bits to remember types we've
                // seen per argument
                Object value = arguments[i];

                if (value instanceof GraalHPyHandle) {
                    GraalHPyHandle handle = (GraalHPyHandle) value;
                    Object delegate = handle.getDelegate();
                    if (GraalHPyBoxing.isBoxablePrimitive(delegate)) {
                        if (delegate instanceof Integer) {
                            return GraalHPyBoxing.boxInt((Integer) delegate);
                        }
                        assert delegate instanceof Double;
                        return GraalHPyBoxing.boxDouble((Double) delegate);
                    } else {
                        return handle.getId(getHPyContext(arguments), ensureProfile());
                    }
                } else if (value instanceof Long) {
                    return (long) value;
                } else {
                    if (interopLibrary == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        interopLibrary = insert(InteropLibrary.getFactory().createDispatched(CACHE_LIMIT));
                    }
                    if (!interopLibrary.isPointer(value)) {
                        interopLibrary.toNative(value);
                    }
                    try {
                        return interopLibrary.asPointer(value);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }

            private ConditionProfile ensureProfile() {
                if (profile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profile = ConditionProfile.createBinaryProfile();
                }
                return profile;
            }
        }

        static final class GraalHPyJNIConvertArgUncachedNode extends GraalHPyJNIConvertArgNode {

            @Override
            public long execute(Object[] arguments, int i) {
                Object value = arguments[i];
                if (value instanceof GraalHPyHandle) {
                    GraalHPyHandle handle = (GraalHPyHandle) value;
                    Object delegate = handle.getDelegate();
                    if (GraalHPyBoxing.isBoxablePrimitive(delegate)) {
                        if (delegate instanceof Integer) {
                            return GraalHPyBoxing.boxInt((Integer) delegate);
                        }
                        assert delegate instanceof Double;
                        return GraalHPyBoxing.boxDouble((Double) delegate);
                    } else {
                        return handle.getId(getHPyContext(arguments), ConditionProfile.getUncached());
                    }
                } else if (value instanceof Long) {
                    return (long) value;
                } else {
                    InteropLibrary interopLibrary = InteropLibrary.getUncached(value);
                    if (!interopLibrary.isPointer(value)) {
                        interopLibrary.toNative(value);
                    }
                    try {
                        return interopLibrary.asPointer(value);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }
        }
    }
}
