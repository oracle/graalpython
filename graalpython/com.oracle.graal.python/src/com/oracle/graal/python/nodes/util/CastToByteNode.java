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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
public abstract class CastToByteNode extends Node {
    public static final CastToByteNode UNCACHED_INSTANCE = CastToByteNode.create();

    public static final String INVALID_BYTE_VALUE = "byte must be in range(0, 256)";

    @Child private PRaiseNode raiseNode;

    private final Function<Object, Byte> rangeErrorHandler;
    private final Function<Object, Byte> typeErrorHandler;
    protected final boolean coerce;

    protected CastToByteNode(Function<Object, Byte> rangeErrorHandler, Function<Object, Byte> typeErrorHandler, boolean coerce) {
        this.rangeErrorHandler = rangeErrorHandler;
        this.typeErrorHandler = typeErrorHandler;
        this.coerce = coerce;
    }

    public abstract byte execute(VirtualFrame frame, Object val);

    @Specialization
    protected byte doByte(byte value) {
        return value;
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected byte doShort(short value) throws OverflowException {
        return PInt.byteValueExact(value);
    }

    @Specialization(replaces = "doShort")
    protected byte doShortOvf(short value) {
        try {
            return PInt.byteValueExact(value);
        } catch (OverflowException e) {
            return handleRangeError(value);
        }
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected byte doInt(int value) throws OverflowException {
        return PInt.byteValueExact(value);
    }

    @Specialization(replaces = "doInt")
    protected byte doIntOvf(int value) {
        try {
            return PInt.byteValueExact(value);
        } catch (OverflowException e) {
            return handleRangeError(value);
        }
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected byte doLong(long value) throws OverflowException {
        return PInt.byteValueExact(value);
    }

    @Specialization(replaces = "doLong")
    protected byte doLongOvf(long value) {
        try {
            return PInt.byteValueExact(value);
        } catch (OverflowException e) {
            return handleRangeError(value);
        }
    }

    @Specialization(rewriteOn = OverflowException.class)
    protected byte doPInt(PInt value) throws OverflowException {
        return PInt.byteValueExact(value.longValueExact());
    }

    @Specialization(replaces = "doPInt")
    protected byte doPIntOvf(PInt value) {
        try {
            return PInt.byteValueExact(value.longValueExact());
        } catch (OverflowException e) {
            return handleRangeError(value);
        }
    }

    @Specialization
    protected byte doBoolean(boolean value) {
        return value ? (byte) 1 : (byte) 0;
    }

    @Specialization(guards = "coerce")
    protected byte doBytes(VirtualFrame frame, PBytesLike value,
                    @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
        return doIntOvf(getItemNode.executeInt(frame, value.getSequenceStorage(), 0));
    }

    @Specialization(guards = "plib.isForeignObject(value)", limit = "1")
    protected byte doForeign(Object value,
                    @SuppressWarnings("unused") @CachedLibrary("value") PythonObjectLibrary plib,
                    @CachedLibrary("value") InteropLibrary lib) {
        if (lib.fitsInByte(value)) {
            try {
                return lib.asByte(value);
            } catch (UnsupportedMessageException e) {
                // fall through
            }
        }
        return doGeneric(value);
    }

    @Fallback
    protected byte doGeneric(@SuppressWarnings("unused") Object val) {
        if (typeErrorHandler != null) {
            return typeErrorHandler.apply(val);
        } else {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, val);
        }
    }

    private byte handleRangeError(Object val) {
        if (rangeErrorHandler != null) {
            return rangeErrorHandler.apply(val);
        } else {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(ValueError, INVALID_BYTE_VALUE);
        }
    }

    public static CastToByteNode create() {
        return CastToByteNodeGen.create(null, null, false);
    }

    public static CastToByteNode getUncached() {
        return UNCACHED_INSTANCE;
    }

    public static CastToByteNode create(boolean coerce) {
        return CastToByteNodeGen.create(null, null, coerce);
    }

    public static CastToByteNode create(Function<Object, Byte> rangeErrorHandler, Function<Object, Byte> typeErrorHandler) {
        return CastToByteNodeGen.create(rangeErrorHandler, typeErrorHandler, false);
    }

    public static CastToByteNode create(Function<Object, Byte> rangeErrorHandler, Function<Object, Byte> typeErrorHandler, boolean coerce) {
        return CastToByteNodeGen.create(rangeErrorHandler, typeErrorHandler, coerce);
    }
}
