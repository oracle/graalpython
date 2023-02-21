/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CIntArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.CExtPyBuffer;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This class implements an interop object that behaves like {@code HPy_buffer} and is backed by
 * {@link CExtPyBuffer}. Therefore, this object is just a view and is read-only. The idea is to use
 * this view for releasing a buffer since releasing usually doesn't need all values and so we try to
 * avoid to do costly conversions eagerly.
 *
 * The {@code HPy_buffer} structure:
 *
 * <pre>
 *     typedef struct {
 *         void *buf;
 *         HPy obj;
 *         Py_ssize_t len;
 *         Py_ssize_t itemsize;
 *         int readonly;
 *         int ndim;
 *         char *format;
 *         Py_ssize_t *shape;
 *         Py_ssize_t *strides;
 *         Py_ssize_t *suboffsets;
 *         void *internal;
 * } HPy_buffer;
 * </pre>
 *
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class GraalHPyBuffer implements TruffleObject {
    private static final String J_MEMBER_BUF = "buf";
    private static final String J_MEMBER_OBJ = "obj";
    private static final String J_MEMBER_LEN = "len";
    private static final String J_MEMBER_ITEMSIZE = "itemsize";
    private static final String J_MEMBER_READONLY = "readonly";
    private static final String J_MEMBER_NDIM = "ndim";
    private static final String J_MEMBER_FORMAT = "format";
    private static final String J_MEMBER_SHAPE = "shape";
    private static final String J_MEMBER_STRIDES = "strides";
    private static final String J_MEMBER_SUBOFFSETS = "suboffsets";
    private static final String J_MEMBER_INTERNAL = "internal";

    @CompilationFinal(dimensions = 1) private static final String[] MEMBERS = new String[]{J_MEMBER_BUF, J_MEMBER_OBJ, J_MEMBER_LEN, J_MEMBER_ITEMSIZE, J_MEMBER_READONLY, J_MEMBER_NDIM,
                    J_MEMBER_FORMAT, J_MEMBER_SHAPE, J_MEMBER_STRIDES, J_MEMBER_SUBOFFSETS, J_MEMBER_INTERNAL};

    private final GraalHPyContext context;
    private final CExtPyBuffer buffer;

    private GraalHPyHandle ownerHandle;
    Object nativePointer;

    public GraalHPyBuffer(GraalHPyContext context, CExtPyBuffer buffer) {
        this.context = context;
        this.buffer = buffer;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new Object[]{J_MEMBER_BUF, J_MEMBER_OBJ, J_MEMBER_LEN, J_MEMBER_ITEMSIZE, J_MEMBER_READONLY,
                        J_MEMBER_NDIM, J_MEMBER_FORMAT, J_MEMBER_SHAPE, J_MEMBER_STRIDES, J_MEMBER_SUBOFFSETS, J_MEMBER_INTERNAL});
    }

    @ExportMessage
    boolean isMemberReadable(String key) {
        for (int i = 0; i < MEMBERS.length; i++) {
            if (MEMBERS[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Shared("toNativeNode") @Cached HPyAsHandleNode toNativeNode) throws UnknownIdentifierException {
        switch (member) {
            case J_MEMBER_BUF:
                return buffer.getBuf();
            case J_MEMBER_OBJ:
                if (ownerHandle == null) {
                    Object obj = buffer.getObj();
                    ownerHandle = toNativeNode.execute(obj != null ? obj : PNone.NO_VALUE);
                }
                return ownerHandle;
            case J_MEMBER_LEN:
                return buffer.getLen();
            case J_MEMBER_ITEMSIZE:
                return buffer.getItemSize();
            case J_MEMBER_READONLY:
                return PInt.intValue(buffer.isReadOnly());
            case J_MEMBER_NDIM:
                return buffer.getDims();
            case J_MEMBER_FORMAT:
                return buffer.getFormat() != null ? new CStringWrapper(buffer.getFormat()) : toNativeNode.execute(PNone.NO_VALUE);
            case J_MEMBER_SHAPE:
                return toCArray(toNativeNode, buffer.getShape());
            case J_MEMBER_STRIDES:
                return toCArray(toNativeNode, buffer.getStrides());
            case J_MEMBER_SUBOFFSETS:
                return toCArray(toNativeNode, buffer.getSuboffsets());
            case J_MEMBER_INTERNAL:
                return buffer.getInternal();
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnknownIdentifierException.create(member);
    }

    private static Object toCArray(HPyAsHandleNode toNativeNode, int[] arr) {
        if (arr != null) {
            return new CIntArrayWrapper(arr);
        }
        return toNativeNode.execute(PNone.NO_VALUE);
    }

    @ExportMessage
    boolean isPointer() {
        return nativePointer != null;
    }

    @ExportMessage(limit = "1")
    long asPointer(
                    @CachedLibrary("this.nativePointer") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.asPointer(nativePointer);
    }

    @ExportMessage
    void toNative(
                    @Cached PCallHPyFunction callBufferToNativeNode,
                    @Shared("toNativeNode") @Cached HPyAsHandleNode toNativeNode,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                    @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        if (nativePointer == null) {
            /*
             * This is basically the same as reading the members one-by-one via 'readMember' but
             * it's doing some shortcuts since we know that each value is going to receive
             * 'toNative'. So, we eagerly convert them to native.
             */
            if (ownerHandle == null) {
                Object obj = buffer.getObj();
                ownerHandle = toNativeNode.execute(obj != null ? obj : PNone.NO_VALUE);
            }
            Object[] args = new Object[]{
                            buffer.getBuf(), // buf
                            ownerHandle, // obj
                            buffer.getLen(), // len
                            buffer.getItemSize(), // itemsize
                            PInt.intValue(buffer.isReadOnly()), // readonly
                            buffer.getDims(), // ndim
                            CArrayWrappers.stringToNativeUtf8Bytes(buffer.getFormat(), switchEncodingNode, copyToByteArrayNode), // format
                            intArrayToNativeInt64(buffer.getShape()), // shape
                            intArrayToNativeInt64(buffer.getStrides()), // strides
                            intArrayToNativeInt64(buffer.getSuboffsets()), // suboffsets
                            buffer.getInternal()  // internal
            };
            nativePointer = callBufferToNativeNode.execute(context, GraalHPyNativeSymbol.GRAAL_HPY_BUFFER_TO_NATIVE, args);
        }
    }

    private static long intArrayToNativeInt64(int[] data) {
        if (data != null) {
            return CArrayWrappers.intArrayToNativeInt64(data);
        }
        return 0;
    }

    void free(PCallHPyFunction callBufferFreeNode) {
        if (ownerHandle != null) {
            ownerHandle.closeAndInvalidate(context);
        }
        if (nativePointer != null) {
            callBufferFreeNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_BUFFER_FREE, nativePointer);
        }
    }
}
