/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wrapper object that emulates the ABI for {@code struct wrapperbase} (aka. slot descriptor).
 *
 * <pre>
 *     struct wrapperbase {
 *         const char *name;
 *         int offset;
 *         void *function;
 *         wrapperfunc wrapper;
 *         const char *doc;
 *         int flags;
 *         PyObject *name_strobj;
 *     };
 * </pre>
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class StructWrapperBaseWrapper extends PythonNativeWrapper {
    public static final String J_NAME = "name";
    public static final String J_OFFSET = "offset";
    public static final String J_FUNCTION = "function";
    public static final String J_WRAPPER = "wrapper";
    public static final String J_DOC = "doc";
    public static final String J_FLAGS = "flags";
    public static final String J_NAME_STROBJ = "name_strobj";

    public StructWrapperBaseWrapper(PBuiltinFunction delegate) {
        super(delegate);
    }

    PBuiltinFunction getBuiltinFunction() {
        return (PBuiltinFunction) getDelegate();
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(new Object[]{J_NAME, J_OFFSET, J_FUNCTION, J_WRAPPER, J_DOC, J_FLAGS, J_NAME_STROBJ});
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        switch (member) {
            case J_NAME:
            case J_OFFSET:
            case J_FUNCTION:
            case J_WRAPPER:
            case J_DOC:
            case J_FLAGS:
            case J_NAME_STROBJ:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    Object readMember(String member,
                    @Shared("gil") @Cached GilNode gil,
                    @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            switch (member) {
                case J_NAME:
                    return new CStringWrapper(getBuiltinFunction().getName());
                case J_OFFSET:
                    return 0;
                case J_FUNCTION:
                    PKeyword[] kwDefaults = getBuiltinFunction().getKwDefaults();
                    return ExternalFunctionNodes.getHiddenCallable(kwDefaults);
                case J_WRAPPER:
                case J_DOC:
                case J_NAME_STROBJ:
                    // TODO(fa): if ever necessary, provide proper values here
                    return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
                case J_FLAGS:
                    return getBuiltinFunction().getFlags();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(member);
        } finally {
            gil.release(mustRelease);
        }
    }

    // for memcpy, we also expose this as an interop array

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return 7 * Long.BYTES;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return 0 <= index && index < 7;
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Shared("gil") @Cached GilNode gil,
                    @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            int i = PInt.intValueExact(index);
            switch (i) {
                case 0:
                    return new CStringWrapper(getBuiltinFunction().getName());
                case Long.BYTES:
                    return 0;
                case 2 * Long.BYTES:
                    PKeyword[] kwDefaults = getBuiltinFunction().getKwDefaults();
                    return ExternalFunctionNodes.getHiddenCallable(kwDefaults);
                case 3 * Long.BYTES:
                case 4 * Long.BYTES:
                case 6 * Long.BYTES:
                    // TODO(fa): if ever necessary, provide proper values here
                    return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
                case 5 * Long.BYTES:
                    return getBuiltinFunction().getFlags();
            }
            // fall through
        } catch (OverflowException e) {
            // fall through
        } finally {
            gil.release(mustRelease);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw InvalidArrayIndexException.create(index);
    }
}
