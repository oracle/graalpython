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
package com.oracle.graal.python.builtins.objects.floats;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

/**
 * Contains helper methods for parsing float numbers in float() and complex() constructors.
 */
public class FloatUtils {

    public static int skipAsciiWhitespace(String str, int start, int len) {
        int offset = start;
        while (offset < len && isAsciiSpace(str.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    // Implements logic of Py_ISSPACE
    private static boolean isAsciiSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r';
    }

    /**
     * Replaces unicode decimal digits and whitespace, checks the any underscores are preceeded and
     * followed by a digit and removes them. Does not create a copy if the original String does not
     * need any cleanup. Combines _PyUnicode_TransformDecimalAndSpaceToASCII and
     * _Py_string_to_number_with_underscores.
     * 
     * @param src the String to transform
     * @return the transformed String, {@code src} if the input does not need cleanup or
     *         {@code null} if there are invalid underscores or unicode characters other than
     *         whitespace or decimal digits
     */
    @TruffleBoundary
    public static String removeUnicodeAndUnderscores(String src) {
        if (!needsCleanup(src)) {
            return src;
        }
        int len = src.length();
        StringBuilder sb = new StringBuilder(len);
        char prev = '\0';
        for (int i = 0; i < len; ++i) {
            char ch = src.charAt(i);
            if (ch == '_') {
                if (!(prev >= '0' && prev <= '9')) {
                    return null;
                }
            } else {
                if (Character.isWhitespace(ch)) {
                    ch = ' ';
                } else if (ch >= 127) {
                    int digit = Character.digit(ch, 10);
                    if (digit < 0) {
                        return null;
                    }
                    ch = Character.forDigit(digit, 10);
                }
                sb.append(ch);
                if (prev == '_' && !(ch >= '0' && ch <= '9')) {
                    return null;
                }
            }
            prev = ch;
        }
        if (prev == '_') {
            return null;
        }
        return sb.toString();
    }

    private static boolean needsCleanup(String src) {
        int len = src.length();
        for (int i = 0; i < len; ++i) {
            char ch = src.charAt(i);
            if (ch > 127 || ch == '_') {
                return true;
            }
        }
        return false;
    }

    @ValueType
    public static class StringToDoubleResult {
        public final double value;
        public final int position;

        public StringToDoubleResult(double value, int position) {
            this.value = value;
            this.position = position;
        }
    }

    /**
     * Attempts to parse a float literal (as defined by Python). Unlike
     * {@link Double#parseDouble(String)}:
     * <ul>
     * <li>tolerates additional characters in the string and reports the position of the first
     * unconsumed character (similar to strtod)</li>
     * <li>does not accept hexadecimal literals</li>
     * <li>does not accept whitespace</li>
     * <li>differentiates between negative and positive NaN</li>
     * <li>besides "infinity" accepts "inf" as well</li>
     * <li>does not throw (but returns {@code null} on error)</li>
     * </ul>
     * Implements PyOS_string_to_double and _PyOS_ascii_strtod except error handling and handling of
     * locale-specific decimal point.
     * 
     * @param str the string to parse
     * @param start starting position in the string
     * @param len length of the string
     * @return the parsed value and the position of the first unparsed character or {@code null} if
     *         there is no valid float literal
     */
    @TruffleBoundary
    public static StringToDoubleResult stringToDouble(String str, int start, int len) {
        boolean negate = false;
        int i = start;

        if (i >= len) {
            return null;
        }
        char firstChar = str.charAt(i);
        if (firstChar == '-' || firstChar == '+') {
            negate = firstChar == '-';
            if (++i >= len) {
                return null;
            }
            firstChar = str.charAt(i);
        }

        if (firstChar != '.' && !(firstChar >= '0' && firstChar <= '9')) {
            if (str.regionMatches(true, i, "inf", 0, 3)) {
                i += 3;
                if (str.regionMatches(true, i, "inity", 0, 5)) {
                    i += 5;
                }
                return new StringToDoubleResult(negate ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY, i);
            } else if (str.regionMatches(true, i, "nan", 0, 3)) {
                i += 3;
                return new StringToDoubleResult(negate ? Math.copySign(Double.NaN, -1) : Double.NaN, i);
            } else {
                return null;
            }
        }

        boolean seenDot = false;        // there must be at most one decimal point
        boolean seenDigit = false;      // there must be at least one digit

        while (i < len) {
            char ch = str.charAt(i);
            if (ch >= '0' && ch <= '9') {
                seenDigit = true;
            } else if (ch == '.') {
                if (seenDot) {
                    return null;
                }
                seenDot = true;
            } else if (ch == 'e' || ch == 'E') {
                if (!seenDigit) {
                    return null;
                }
                if (i + 1 < len) {
                    char ch2 = str.charAt(i + 1);
                    if (ch2 == '+' || ch2 == '-') {
                        i++;
                    }
                }
                seenDigit = false;  // there must be at least one digit in the exponent
                seenDot = true;     // there must be no decimal point in the exponent
            } else {
                break;
            }
            i++;
        }
        if (!seenDigit) {
            return null;
        }
        try {
            return new StringToDoubleResult(Double.parseDouble(str.substring(start, i)), i);
        } catch (NumberFormatException e) {
            // Should not happen since the input to Double.parseDouble should be correct
            return null;
        }
    }
}
