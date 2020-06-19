/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.FormattingBuffer.BytesFormattingBuffer;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Formatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

public class BytesFormatProcessor extends FormatProcessor<byte[]> {
    private final byte[] formatBytes;

    public BytesFormatProcessor(PythonCore core, LookupAndCallBinaryNode getItemNode, TupleBuiltins.GetItemNode getTupleItemNode, byte[] formatBytes) {
        super(core, getItemNode, getTupleItemNode, new BytesFormattingBuffer());
        this.formatBytes = formatBytes;
    }

    @Override
    protected String getFormatType() {
        return "bytes";
    }

    @Override
    char pop() {
        try {
            return (char) formatBytes[index++];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw core.raise(ValueError, ErrorMessages.INCOMPLETE_FORMAT);
        }
    }

    @Override
    boolean hasNext() {
        return index < formatBytes.length;
    }

    @Override
    int parseNumber(int start, int end) {
        String str = new String(formatBytes, start, end - start, StandardCharsets.US_ASCII);
        return Integer.parseInt(str);
    }

    @Override
    Object parseMappingKey(int start, int end) {
        return core.factory().createBytes(Arrays.copyOfRange(formatBytes, start, end));
    }

    @Override
    protected double asFloat(Object arg) {
        try {
            return super.asFloat(arg);
        } catch (PException ex) {
            // exactly like in CPython, all errors are translated to this
            throw core.raise(TypeError, ErrorMessages.FLOAT_ARG_REQUIRED, arg);
        }
    }

    @Override
    protected Formatter handleSingleCharacterFormat(Spec spec) {
        // %c for bytes supports length one bytes buffer object (no __bytes__ coercion) or an
        // integer value fitting byte range
        Object arg = getArg();

        // Bytes
        PythonObjectLibrary pyLib = PythonObjectLibrary.getFactory().getUncached(arg);
        if (pyLib.isBuffer(arg)) {
            try {
                if (pyLib.getBufferLength(arg) == 1) {
                    BytesFormatter f = new BytesFormatter(core, buffer, spec);
                    f.format(pyLib.getBufferBytes(arg));
                    return f;
                }
            } catch (UnsupportedMessageException ex) {
                throw new IllegalStateException();
            }
        }

        // Integer value fitting byte range
        boolean foundByte = false;
        byte value = 0;
        arg = asNumber(arg, spec.type);
        if (arg instanceof Long) {
            long argLong = (Long) arg;
            if ((argLong & 0xFF) == argLong) {
                value = (byte) argLong;
                foundByte = true;
            } else {
                throw raiseOverflow();
            }
        } else if (arg instanceof PInt) {
            try {
                value = ((PInt) arg).byteValueExact();
                foundByte = true;
            } catch (ArithmeticException ex) {
                throw raiseOverflow();
            }
        } else if (arg instanceof Integer) {
            int argInt = (Integer) arg;
            if ((argInt & 0xFF) == argInt) {
                value = (byte) argInt;
                foundByte = true;
            } else {
                throw raiseOverflow();
            }
        }

        if (!foundByte) {
            throw core.raise(TypeError, ErrorMessages.C_REQUIRES_INT_IN_BYTE_RANGE_OR_SINGLE_BYTE);
        }

        BytesFormatter f = new BytesFormatter(core, buffer, spec);
        f.format(value);
        return f;
    }

    private PException raiseOverflow() {
        throw core.raise(OverflowError, ErrorMessages.C_ARG_NOT_IN_RANGE256_DECIMAL);
    }

    @Override
    protected InternalFormat.Formatter handleRemainingFormats(InternalFormat.Spec spec) {
        Object arg, attribute;
        byte[] bytes;
        switch (spec.type) {
            case 'b':
            case 's':
                // According to the spec: if object is Py_buffer, get the bytes directly, otherwise
                // call __bytes__
                arg = getArg();
                bytes = asBytes(arg);
                BytesFormatter fb = new BytesFormatter(core, buffer, spec);
                fb.format(bytes);
                return fb;

            case 'r':
            case 'a': // ascii: from spec: should be equivalent to repr(x).encode('ascii',
                      // 'backslashreplace')
                arg = getArg();
                attribute = lookupAttribute(arg, __REPR__);
                if (attribute != PNone.NO_VALUE) {
                    Object result = call(attribute, arg);
                    if (PGuards.isString(result)) {
                        bytes = BytesUtils.unicodeNonAsciiEscape(result.toString());
                        fb = new BytesFormatter(core, buffer, spec);
                        fb.format(bytes);
                        return fb;
                    }
                }
                throw core.raise(TypeError, ErrorMessages.REQUIRES_OBJ_THAT_IMPLEMENTS_S, __REPR__);

            default:
                return null;
        }
    }

    private byte[] asBytes(Object arg) {
        // bytes like object -> use directly
        if (arg instanceof PIBytesLike) {
            return toBytes((PIBytesLike) arg);
        }
        // try calling __bytes__
        Object attribute = lookupAttribute(arg, __BYTES__);
        if (attribute != PNone.NO_VALUE) {
            Object bytesResult = call(attribute, arg);
            if (!(bytesResult instanceof PBytes)) {
                throw core.raise(TypeError, ErrorMessages.RETURNED_NONBYTES, __BYTES__, arg);
            }
            return toBytes((PBytes) bytesResult);
        }
        // otherwise: use the buffer protocol
        byte[] result = byteBufferAsBytesOrNull(arg);
        if (result == null) {
            throw core.raise(TypeError, ErrorMessages.B_REQUIRES_BYTES_OR_OBJ_THAT_IMPLEMENTS_S_NOT_P, __BYTES__, arg);
        }
        return result;
    }

    private static byte[] toBytes(PIBytesLike arg) {
        return ToByteArrayNodeGen.getUncached().execute(arg.getSequenceStorage());
    }

    private static byte[] byteBufferAsBytesOrNull(Object obj) {
        PythonObjectLibrary pyLib = PythonObjectLibrary.getFactory().getUncached(obj);
        if (pyLib.isBuffer(obj)) {
            try {
                return pyLib.getBufferBytes(obj);
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException();
            }
        }
        return null;
    }

}
