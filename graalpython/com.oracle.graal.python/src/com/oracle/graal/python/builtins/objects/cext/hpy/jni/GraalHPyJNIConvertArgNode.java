/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.jni;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GetHPyHandleForSingleton;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFactory.GetHPyHandleForSingletonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class GraalHPyJNIConvertArgNode extends Node {

    private static final GraalHPyJNIConvertArgUncachedNode UNCACHED = new GraalHPyJNIConvertArgUncachedNode();

    public static GraalHPyJNIConvertArgNode create(@SuppressWarnings("unused") LLVMType signature) {
        return new GraalHPyJNIConvertArgCachedNode();
    }

    public static GraalHPyJNIConvertArgNode getUncached(@SuppressWarnings("unused") LLVMType signature) {
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
         * Carefully picked limit. Expected possible argument object types are: LLVM native pointer,
         * LLVM managed pointer, {@link GraalHPyContext}, and {@link GraalHPyHandle}.
         */
        private static final int CACHE_LIMIT = 4;

        @Child private InteropLibrary interopLibrary;
        @CompilationFinal private ConditionProfile profile;
        @Child GetHPyHandleForSingleton getHPyHandleForSingleton;

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
                    return handle.getId(getHPyContext(arguments), ensureProfile(), ensureHandleForSingletonNode());
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
                profile = ConditionProfile.create();
            }
            return profile;
        }

        private GetHPyHandleForSingleton ensureHandleForSingletonNode() {
            if (getHPyHandleForSingleton == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getHPyHandleForSingleton = insert(GetHPyHandleForSingletonNodeGen.create());
            }
            return getHPyHandleForSingleton;
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
                    return handle.getIdUncached(getHPyContext(arguments));
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
