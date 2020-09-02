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
package com.oracle.graal.python.builtins.objects.str;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class StringUtils {
    public enum StripKind {
        LEFT,
        RIGHT,
        BOTH
    }

    /** corresponds to {@code unicodeobject.c:_Py_ascii_whitespace} */
    private static final int[] ASCII_WHITESPACE = {
                    0, 0, 0, 0, 0, 0, 0, 0,
                    /* case 0x0009: * CHARACTER TABULATION */
                    /* case 0x000A: * LINE FEED */
                    /* case 0x000B: * LINE TABULATION */
                    /* case 0x000C: * FORM FEED */
                    /* case 0x000D: * CARRIAGE RETURN */
                    0, 1, 1, 1, 1, 1, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    /* case 0x001C: * FILE SEPARATOR */
                    /* case 0x001D: * GROUP SEPARATOR */
                    /* case 0x001E: * RECORD SEPARATOR */
                    /* case 0x001F: * UNIT SEPARATOR */
                    0, 0, 0, 0, 1, 1, 1, 1,
                    /* case 0x0020: * SPACE */
                    1, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,

                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
    };

    public static boolean isUnicodeWhitespace(int ch) {
        switch (ch) {
            case 0x0009:
            case 0x000A:
            case 0x000B:
            case 0x000C:
            case 0x000D:
            case 0x001C:
            case 0x001D:
            case 0x001E:
            case 0x001F:
            case 0x0020:
            case 0x0085:
            case 0x00A0:
            case 0x1680:
            case 0x2000:
            case 0x2001:
            case 0x2002:
            case 0x2003:
            case 0x2004:
            case 0x2005:
            case 0x2006:
            case 0x2007:
            case 0x2008:
            case 0x2009:
            case 0x200A:
            case 0x2028:
            case 0x2029:
            case 0x202F:
            case 0x205F:
            case 0x3000:
                return true;
            default:
                return false;
        }
    }

    public static boolean isUnicodeLineBreak(char ch) {
        switch (ch) {
            case 0x000A:
            case 0x000B:
            case 0x000C:
            case 0x000D:
            case 0x001C:
            case 0x001D:
            case 0x001E:
            case 0x0085:
            case 0x2028:
            case 0x2029:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSpace(int ch) {
        if (ch < 128) {
            return ASCII_WHITESPACE[ch] == 1;
        }
        return isUnicodeWhitespace(ch);
    }

    public static String strip(String str, StripKind stripKind) {
        int i = 0;
        int len = str.length();

        if (stripKind != StripKind.RIGHT) {
            while (i < len) {
                char ch = str.charAt(i);
                if (!isSpace(ch)) {
                    break;
                }
                i++;
            }
        }

        int j = len;
        if (stripKind != StripKind.LEFT) {
            j--;
            while (j >= i) {
                char ch = str.charAt(j);
                if (!isSpace(ch)) {
                    break;
                }
                j--;
            }
            j++;
        }

        return str.substring(i, j);
    }

    public static String strip(String str, String chars, StripKind stripKind) {
        int i = 0;
        int len = str.length();
        // TODO: cpython uses a bloom filter for to skip chars that are not in the sep list:
        // to avoid the linear search in chars
        if (stripKind != StripKind.RIGHT) {
            while (i < len) {
                char ch = str.charAt(i);
                if (chars.indexOf(ch) < 0) {
                    break;
                }
                i++;
            }
        }

        int j = len;
        if (stripKind != StripKind.LEFT) {
            j--;
            while (j >= i) {
                char ch = str.charAt(j);
                if (chars.indexOf(ch) < 0) {
                    break;
                }
                j--;
            }
            j++;
        }

        return str.substring(i, j);
    }

    @TruffleBoundary
    public static boolean containsNullCharacter(String value) {
        return value.indexOf(0) > 0;
    }

    @TruffleBoundary
    public static Object[] toCharacterArray(String arg) {
        Object[] values = new Object[arg.codePointCount(0, arg.length())];
        for (int i = 0, o = 0; i < arg.length(); o++) {
            int codePoint = arg.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (charCount == 1) {
                values[o] = String.valueOf((char) codePoint);
            } else {
                values[o] = String.valueOf(Character.toChars(codePoint));
            }
            i += charCount;
        }
        return values;
    }
}
