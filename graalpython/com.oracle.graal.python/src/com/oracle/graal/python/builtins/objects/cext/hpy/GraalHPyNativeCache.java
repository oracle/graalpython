/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SIZEOF_LONG;

import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;

import sun.misc.Unsafe;

/**
 * HPy native cache implementation. For documentation of the layout, see {@code hpy_native_cache.h}.
 */
public abstract class GraalHPyNativeCache {

    private static final Unsafe UNSAFE = CArrayWrappers.UNSAFE;

    private static final long HANDLE_MIRROR_OFFSET = 1;

    static long toBytes(long idx) {
        return (HANDLE_MIRROR_OFFSET + idx) * SIZEOF_LONG;
    }

    static long allocateNativeCache(int nHandleTable, int nGlobalsTable) {
        long arraySize = toBytes(nHandleTable + nGlobalsTable);
        long arrayPtr = UNSAFE.allocateMemory(arraySize);
        UNSAFE.setMemory(arrayPtr, arraySize, (byte) 0);
        UNSAFE.putLong(arrayPtr, nHandleTable);
        return arrayPtr;
    }

    static long reallocateNativeCache(long cachePtr, int nHandleTableOld, int nHandleTable, int nGlobalsTableOld, int nGlobalsTable) {
        if (nHandleTableOld > nHandleTable || nGlobalsTableOld > nGlobalsTable) {
            throw new RuntimeException("shrinking HPy handle/globals table is not yet supported");
        }
        long arraySize = toBytes(nHandleTable + nGlobalsTable);
        long newCachePtr = UNSAFE.reallocateMemory(cachePtr, arraySize);
        if (nHandleTableOld != nHandleTable) {
            // update handle table size
            UNSAFE.putLong(newCachePtr, nHandleTable);
            // move globals table entries (only if the handle table size changed)
            UNSAFE.copyMemory(newCachePtr + toBytes(nHandleTableOld), newCachePtr + toBytes(nHandleTable), nGlobalsTableOld * SIZEOF_LONG);
        }
        return newCachePtr;
    }

    static void putHandleNativeSpacePointer(long cachePtr, int handleID, long value) {
        UNSAFE.putLong(cachePtr + toBytes(handleID), value);
    }

    static void putGlobalNativeSpacePointer(long cachePtr, long nHandleTable, int globalID, long value) {
        UNSAFE.putLong(cachePtr + toBytes(nHandleTable + globalID), value);
    }

    static void initGlobalsNativeSpacePointer(long cachePtr, long nHandleTable, int globalStartID, int numElem) {
        UNSAFE.setMemory(cachePtr + toBytes(nHandleTable + globalStartID), numElem * SIZEOF_LONG, (byte) 0);
    }
}
