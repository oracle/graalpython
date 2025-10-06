/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi2;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;

public final class NativeMemory {

    static final Unsafe UNSAFE = initUnsafe();

    private NativeMemory() {
    }

    public static long malloc(long size) {
        assert size > 0;
        return UNSAFE.allocateMemory(size);
    }

    public static void free(long ptr) {
        UNSAFE.freeMemory(ptr);
    }

    public static long javaStringToNativeUtf8(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        long ptr = NativeMemory.malloc(utf8.length + 1);
        UNSAFE.copyMemory(utf8, UNSAFE.arrayBaseOffset(byte[].class), null, ptr, utf8.length);
        UNSAFE.putByte(ptr + utf8.length, (byte) 0);
        return ptr;
    }

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw CompilerDirectives.shouldNotReachHere("exception while trying to get Unsafe", e);
            }
        }
    }
}
