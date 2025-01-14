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
package com.oracle.graal.python.lib;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/**
 * Parse an integer from a Java String. Similar to {@code PyLong_FromString}, but accepts Unicode
 * characters.
 */
@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PyLongFromString extends Node {
    public abstract Object execute(Node inliningTarget, String number, int base);

    public static Object executeUncached(String number, int base) {
        return PyLongFromStringNodeGen.getUncached().execute(null, number, base);
    }

    @Specialization
    static Object doGeneric(Node inliningTarget, String number, int base,
                    @Cached InlinedBranchProfile decimal,
                    @Cached InlinedBranchProfile invalidBase,
                    @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                    @Cached InlinedBranchProfile invalidValueProfile,
                    @Cached PythonObjectFactory factory,
                    @Cached PRaiseNode.Lazy raiseNode) {
        if (base == 0 || base == 10) {
            decimal.enter(inliningTarget);
            // TODO loop profile
            Object value = parseSimpleDecimalLiteral(number, 0, number.length());
            if (value != null) {
                return value;
            }
        }
        if ((base != 0 && base < 2) || base > 36) {
            invalidBase.enter(inliningTarget);
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.INT_BASE_MUST_BE_2_AND_36_OR_0);
        }
        notSimpleDecimalLiteralProfile.enter(inliningTarget);
        PythonContext context = PythonContext.get(inliningTarget);
        Object value = stringToIntInternal(number, base, context, factory);
        if (value == null) {
            invalidValueProfile.enter(inliningTarget);
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_LITERAL_FOR_INT_WITH_BASE, base, number);
        }
        return value;
    }

    /**
     * Fast path parser of integer literals. Accepts only a subset of allowed literals - no
     * underscores, no leading zeros, no plus sign, no spaces, only ascii digits and the result must
     * be small enough to fit into long.
     */
    private static Object parseSimpleDecimalLiteral(String arg, int offset, int remaining) {
        if (remaining <= 0) {
            return null;
        }
        int start = arg.charAt(offset) == '-' ? 1 : 0;
        if (remaining <= start || remaining > 18 + start) {
            return null;
        }
        if (arg.charAt(start + offset) == '0') {
            if (remaining > start + 1) {
                return null;
            }
            return 0;
        }
        long value = 0;
        for (int i = start; i < remaining; i++) {
            char c = arg.charAt(i + offset);
            if (c < '0' || c > '9') {
                return null;
            }
            value = value * 10 + (c - '0');
        }
        if (start != 0) {
            value = -value;
        }
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    @TruffleBoundary
    private static Object stringToIntInternal(String num, int base, PythonContext context, PythonObjectFactory factory) {
        try {
            BigInteger bi = asciiToBigInteger(num, base, context);
            if (bi == null) {
                return null;
            }
            if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                return factory.createInt(bi);
            } else {
                return bi.intValue();
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @TruffleBoundary
    private static BigInteger asciiToBigInteger(String str, int possibleBase, PythonContext context) throws NumberFormatException {
        int base = possibleBase;
        int b = 0;
        int e = str.length();

        while (b < e && Character.isWhitespace(str.charAt(b))) {
            b++;
        }

        while (e > b && Character.isWhitespace(str.charAt(e - 1))) {
            e--;
        }

        boolean acceptUnderscore = false;
        boolean raiseIfNotZero = false;
        char sign = 0;
        if (b < e) {
            sign = str.charAt(b);
            if (sign == '-' || sign == '+') {
                b++;
            }

            if (str.charAt(b) == '0') {
                char next = b + 1 < e ? Character.toUpperCase(str.charAt(b + 1)) : '?';
                if (base == 0) {
                    if (next == 'X') {
                        base = 16;
                    } else if (next == 'O') {
                        base = 8;
                    } else if (next == 'B') {
                        base = 2;
                    } else {
                        raiseIfNotZero = true;
                    }
                }
                if (base == 16 && next == 'X' || base == 8 && next == 'O' || base == 2 && next == 'B') {
                    b += 2;
                    acceptUnderscore = true;
                }
            }
        }

        if (base == 0) {
            base = 10;
        }

        // reject invalid characters without going to BigInteger
        for (int i = b; i < e; i++) {
            char c = str.charAt(i);
            if (c == '_') {
                if (!acceptUnderscore || i == e - 1) {
                    throw new NumberFormatException("Illegal underscore in int literal");
                } else {
                    acceptUnderscore = false;
                }
            } else {
                acceptUnderscore = true;
                if (Character.digit(c, base) == -1) {
                    // invalid char
                    return null;
                }
            }
        }

        String s = str;
        if (b > 0 || e < str.length()) {
            s = str.substring(b, e);
        }
        s = s.replace("_", "");

        checkMaxDigits(context, s.length(), base);

        BigInteger bi;
        if (sign == '-') {
            bi = new BigInteger("-" + s, base);
        } else {
            bi = new BigInteger(s, base);
        }

        if (raiseIfNotZero && !bi.equals(BigInteger.ZERO)) {
            throw new NumberFormatException("Obsolete octal int literal");
        }
        return bi;
    }

    private static void checkMaxDigits(PythonContext context, int digits, int base) {
        if (digits > SysModuleBuiltins.INT_MAX_STR_DIGITS_THRESHOLD && Integer.bitCount(base) != 1) {
            int maxDigits = context.getIntMaxStrDigits();
            if (maxDigits > 0 && digits > maxDigits) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION_D, maxDigits, digits);
            }
        }
    }
}
