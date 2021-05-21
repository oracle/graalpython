/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CIntArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.ConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.CExtPyBuffer;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

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
    private static final String MEMBER_BUF = "buf";
    private static final String MEMBER_OBJ = "obj";
    private static final String MEMBER_LEN = "len";
    private static final String MEMBER_ITEMSIZE = "itemsize";
    private static final String MEMBER_READONLY = "readonly";
    private static final String MEMBER_NDIM = "ndim";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_SHAPE = "shape";
    private static final String MEMBER_STRIDES = "strides";
    private static final String MEMBER_SUBOFFSETS = "suboffsets";
    private static final String MEMBER_INTERNAL = "internal";

    @CompilationFinal(dimensions = 1) private static final String[] MEMBERS = new String[]{MEMBER_BUF, MEMBER_OBJ, MEMBER_LEN, MEMBER_ITEMSIZE, MEMBER_READONLY, MEMBER_NDIM, MEMBER_FORMAT,
                    MEMBER_SHAPE, MEMBER_STRIDES, MEMBER_SUBOFFSETS, MEMBER_INTERNAL};

    private final CExtContext context;
    private final CExtPyBuffer buffer;

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
        return new PythonAbstractObject.Keys(new Object[]{MEMBER_BUF, MEMBER_OBJ, MEMBER_LEN, MEMBER_ITEMSIZE, MEMBER_READONLY,
                        MEMBER_NDIM, MEMBER_FORMAT, MEMBER_SHAPE, MEMBER_STRIDES, MEMBER_SUBOFFSETS, MEMBER_INTERNAL});
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
    static class ReadMember {
        @Specialization(guards = "getSupplier(receiver) == cachedSupplier")
        static Object readMember(GraalHPyBuffer receiver, String key,
                        @Cached(value = "getSupplier(receiver)", allowUncached = true) @SuppressWarnings("unused") ConversionNodeSupplier cachedSupplier,
                        @Cached(value = "cachedSupplier.createToNativeNode()", uncached = "cachedSupplier.getUncachedToNativeNode()") CExtToNativeNode toNativeNode) throws UnknownIdentifierException {
            switch (key) {
                case MEMBER_BUF:
                    return receiver.buffer.getBuf();
                case MEMBER_OBJ:
                    Object obj = receiver.buffer.getObj();
                    return toNativeNode.execute(receiver.context, obj != null ? obj : PNone.NO_VALUE);
                case MEMBER_LEN:
                    return receiver.buffer.getLen();
                case MEMBER_ITEMSIZE:
                    return receiver.buffer.getItemSize();
                case MEMBER_READONLY:
                    return PInt.intValue(receiver.buffer.isReadOnly());
                case MEMBER_NDIM:
                    return receiver.buffer.getDims();
                case MEMBER_FORMAT:
                    return receiver.buffer.getFormat() != null ? new CStringWrapper(receiver.buffer.getFormat()) : toNativeNode.execute(receiver.context, PNone.NO_VALUE);
                case MEMBER_SHAPE:
                    return toCArray(receiver.context, toNativeNode, receiver.buffer.getShape());
                case MEMBER_STRIDES:
                    return toCArray(receiver.context, toNativeNode, receiver.buffer.getStrides());
                case MEMBER_SUBOFFSETS:
                    return toCArray(receiver.context, toNativeNode, receiver.buffer.getSuboffsets());
                case MEMBER_INTERNAL:
                    return receiver.buffer.getInternal();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(key);
        }

        private static Object toCArray(CExtContext context, CExtToNativeNode toNativeNode, int[] arr) {
            if (arr != null) {
                return new CIntArrayWrapper(arr);
            }
            return toNativeNode.execute(context, PNone.NO_VALUE);
        }

        static ConversionNodeSupplier getSupplier(GraalHPyBuffer receiver) {
            return receiver.context.getSupplier();
        }
    }
}
