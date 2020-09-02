/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.math.BigInteger;

import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class BigIntegerUtils {
    public static int intValueExact(BigInteger x) throws OverflowException {
        int signum = x.signum();
        if (signum == 0) {
            return 0;
        }
        int[] mag = getMag(x);
        if (mag.length == 1) {
            int mag0 = mag[0];
            if (mag0 > 0 || (mag0 == 0x80000000 && signum < 0)) {
                return signum * mag0;
            }
        }
        throw OverflowException.INSTANCE;
    }

    public static long longValueExact(BigInteger x) throws OverflowException {
        int signum = x.signum();
        if (signum == 0) {
            return 0;
        }
        int[] mag = getMag(x);
        if (mag.length == 1) {
            int mag0 = mag[0];
            if (mag0 > 0 || (mag0 == 0x80000000 && signum < 0)) {
                return signum * mag0;
            } else {
                long mag0l = mag0 & 0xFFFFFFFFL;
                return signum * mag0l;
            }
        }
        if (mag.length == 2) {
            int mag0 = mag[0];
            int mag1 = mag[1];
            if (mag0 > 0 || (mag0 == 0x80000000 && signum < 0 && mag1 == 0)) {
                long mag0l = mag0 & 0xFFFFFFFFL;
                long mag1l = mag1 & 0xFFFFFFFFL;
                return signum * ((mag0l << 32) | mag1l);
            }
        }
        throw OverflowException.INSTANCE;
    }

    private static final Field MAG_FIELD;

    static {
        try {
            MAG_FIELD = BigInteger.class.getDeclaredField("mag");
            MAG_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Unable to access BigInteger.mag", e);
        }
    }

    @TruffleBoundary(allowInlining = true)
    static int[] getMag(BigInteger x) {
        try {
            return (int[]) MAG_FIELD.get(x);
        } catch (IllegalAccessException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
