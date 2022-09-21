/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.object;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.util.WeakIdentityHashMap;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;

public final class IDUtils {
    private static final long MAX_OBJECT_ID = (1L << 62) - 1;
    private static final long MAX_DOUBLE_ID = (1L << 63) - 1;

    private static final long ID_MASK_DOUBLE = 0b0; // this gives doubles 63 bits
    private static final long ID_MASK_LONG = 0b10; // 62 bits for longs until overflow
    private static final long ID_MASK_OBJECT = 0b11; // 62 bits for objects (48 bits max num
                                                     // addressable objects at once)

    private static final BigInteger ID_MASK_LONG_BI = BigInteger.valueOf(ID_MASK_LONG);
    private static final BigInteger ID_MASK_DOUBLE_BI = BigInteger.valueOf(ID_MASK_DOUBLE);

    // reserved ids
    private enum ReservedID {
        none,
        notImplemented,
        ellipsis,
        emptyBytes,
        emptyUnicode,
        emptyTuple,
        emptyFrozenSet,
    }

    private static final int NUM_RESERVED_IDS = ReservedID.values().length;
    private static final long ID_OFFSET = 1 + NUM_RESERVED_IDS + PythonBuiltinClassType.values().length;

    public static final long ID_NONE = getId(ReservedID.none);
    public static final long ID_NOTIMPLEMENTED = getId(ReservedID.notImplemented);
    public static final long ID_ELLIPSIS = getId(ReservedID.ellipsis);
    public static final long ID_EMPTY_BYTES = getId(ReservedID.emptyBytes);
    public static final long ID_EMPTY_UNICODE = getId(ReservedID.emptyUnicode);
    public static final long ID_EMPTY_TUPLE = getId(ReservedID.emptyTuple);
    public static final long ID_EMPTY_FROZENSET = getId(ReservedID.emptyFrozenSet);

    private final Map<Object, Long> weakIdMap = Collections.synchronizedMap(new WeakIdentityHashMap<>());
    // for Python interned strings and Truffle strings
    private final Map<TruffleString, Long> weakStringIdMap = Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicLong globalId = new AtomicLong(ID_OFFSET);

    private static long asMaskedReservedObjectId(long id) {
        assert 0 <= id && id < ID_OFFSET;
        return (id << 2) | ID_MASK_OBJECT;
    }

    public static long asMaskedObjectId(long id) {
        assert Long.compareUnsigned(ID_OFFSET, id) <= 0 && Long.compareUnsigned(id, MAX_OBJECT_ID) <= 0;
        // TODO: handle overflow (bucketed counter interval with free bucket monitor thread)
        return (id << 2) | ID_MASK_OBJECT;
    }

    @CompilerDirectives.TruffleBoundary
    private static BigInteger asMaskedBigIntId(long id, BigInteger mask) {
        return BigInteger.valueOf(id).shiftLeft(2).or(mask);
    }

    private static Object asMaskedId(long id, PythonObjectFactory factory, long max, long mask, BigInteger biMask) {
        if (Long.compareUnsigned(id, max) <= 0) {
            return (id << 2) | mask;
        }
        return factory.createInt(asMaskedBigIntId(id, biMask));
    }

    private static long getId(ReservedID reservedID) {
        return asMaskedReservedObjectId(1 + reservedID.ordinal());
    }

    public static long getId(PythonBuiltinClassType classType) {
        return asMaskedReservedObjectId(1 + NUM_RESERVED_IDS + classType.ordinal());
    }

    public static Object getId(int id) {
        return ((long) id << 2) | ID_MASK_LONG;
    }

    public static Object getId(long id, PythonObjectFactory factory) {
        return asMaskedId(id, factory, MAX_OBJECT_ID, ID_MASK_LONG, ID_MASK_LONG_BI);
    }

    public static Object getId(double id, PythonObjectFactory factory) {
        long ieee754 = Double.doubleToLongBits(id);
        return asMaskedId(ieee754, factory, MAX_DOUBLE_ID, ID_MASK_DOUBLE, ID_MASK_DOUBLE_BI);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    private long getNextId() {
        return globalId.incrementAndGet();
    }

    public long getNextObjectId() {
        return asMaskedObjectId(getNextId());
    }

    @CompilerDirectives.TruffleBoundary
    public long getNextObjectId(Object object) {
        return weakIdMap.computeIfAbsent(object, value -> getNextObjectId());
    }

    @CompilerDirectives.TruffleBoundary
    public long getNextStringId(TruffleString string) {
        return weakStringIdMap.computeIfAbsent(string, value -> getNextObjectId());
    }
}
