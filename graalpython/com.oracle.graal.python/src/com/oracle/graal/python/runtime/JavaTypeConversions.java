/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PLenSupplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class JavaTypeConversions {

    @TruffleBoundary
    public static Object stringToInt(String num, int base) {
        if ((base >= 2 && base <= 32) || base == 0) {
            BigInteger bi = asciiToBigInteger(num, 10, false);
            if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                return bi;
            } else {
                return bi.intValue();
            }
        } else {
            throw new RuntimeException("base is out of range for int()");
        }
    }

    public static boolean toBoolean(Object arg) {
        CompilerAsserts.neverPartOfCompilation();

        if (arg instanceof Boolean) {
            return (Boolean) arg;
        } else if (arg instanceof Integer) {
            int intArg = (Integer) arg;
            return intArg != 0;
        } else if (arg instanceof BigInteger) {
            BigInteger bitIntArg = (BigInteger) arg;
            return (bitIntArg.compareTo(BigInteger.ZERO) != 0);
        } else if (arg instanceof Double) {
            double doubleArg = (Double) arg;
            return doubleArg != 0.0;
        } else if (arg instanceof String) {
            String stringArg = (String) arg;
            return !(stringArg.isEmpty());
        } else if (arg instanceof PLenSupplier) {
            PLenSupplier iterable = (PLenSupplier) arg;
            return iterable.len() != 0;
        } else {
            throw new RuntimeException("invalid value for boolean() " + arg);
        }
    }

    @TruffleBoundary
    public static Object toInt(Object arg) {
        if (arg instanceof Integer || arg instanceof BigInteger) {
            return arg;
        } else if (arg instanceof Boolean) {
            return (boolean) arg ? 1 : 0;
        } else if (arg instanceof Double) {
            return doubleToInt((Double) arg);
        } else if (arg instanceof String) {
            return stringToInt((String) arg, 10);
        }
        return null;
    }

    public static Object toInt(Object arg1, Object arg2) {
        if (arg1 instanceof String && arg2 instanceof Integer) {
            return stringToInt((String) arg1, (Integer) arg2);
        } else {
            throw new RuntimeException("invalid base or val for int()");
        }
    }

    public static Object doubleToInt(double num) {
        if (num > Integer.MAX_VALUE || num < Integer.MIN_VALUE) {
            return BigInteger.valueOf((long) num);
        } else {
            return (int) num;
        }
    }

    public static double toDouble(Object value) {
        try {
            return (double) value;
        } catch (ClassCastException e) {
            // fall through
        }

        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        } else if (value instanceof BigInteger) {
            return ((BigInteger) value).doubleValue();
        } else {
            throw new RuntimeException("unexpected value type " + value.getClass());
        }
    }

    // Copied directly from Jython
    @TruffleBoundary
    private static BigInteger asciiToBigInteger(String str, int possibleBase, boolean isLong) {
        int base = possibleBase;
        int b = 0;
        int e = str.length();

        while (b < e && Character.isWhitespace(str.charAt(b))) {
            b++;
        }

        while (e > b && Character.isWhitespace(str.charAt(e - 1))) {
            e--;
        }

        char sign = 0;
        if (b < e) {
            sign = str.charAt(b);
            if (sign == '-' || sign == '+') {
                b++;
                while (b < e && Character.isWhitespace(str.charAt(b))) {
                    b++;
                }
            }

            if (base == 16) {
                if (str.charAt(b) == '0') {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                        b += 2;
                    }
                }
            } else if (base == 0) {
                if (str.charAt(b) == '0') {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                        base = 16;
                        b += 2;
                    } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                        base = 8;
                        b += 2;
                    } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                        base = 2;
                        b += 2;
                    } else {
                        base = 8;
                    }
                }
            } else if (base == 8) {
                if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                    b += 2;
                }
            } else if (base == 2) {
                if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                    b += 2;
                }
            }
        }

        if (base == 0) {
            base = 10;
        }

        // if the base >= 22, then an 'l' or 'L' is a digit!
        if (isLong && base < 22 && e > b && (str.charAt(e - 1) == 'L' || str.charAt(e - 1) == 'l')) {
            e--;
        }

        String s = str;
        if (b > 0 || e < str.length()) {
            s = str.substring(b, e);
        }

        BigInteger bi;
        if (sign == '-') {
            bi = new BigInteger("-" + s, base);
        } else {
            bi = new BigInteger(s, base);
        }
        return bi;
    }

    // Taken from Jython PyString's atof() method
    // The last statement throw Py.ValueError is modified
    @TruffleBoundary
    public static double convertStringToDouble(String str) {
        StringBuilder s = null;
        int n = str.length();

        for (int i = 0; i < n; i++) {
            char ch = str.charAt(i);
            if (ch == '\u0000') {
                throw PythonLanguage.getCore().raise(ValueError, "empty string for complex()");
            }
            if (Character.isDigit(ch)) {
                if (s == null) {
                    s = new StringBuilder(str);
                }
                int val = Character.digit(ch, 10);
                s.setCharAt(i, Character.forDigit(val, 10));
            }
        }
        String sval = str.trim();
        if (s != null) {
            sval = s.toString();
        }
        try {
            // Double.valueOf allows format specifier ("d" or "f") at the end
            String lowSval = sval.toLowerCase();
            if (lowSval.equals("nan") || lowSval.equals("+nan") || lowSval.equals("-nan")) {
                return Double.NaN;
            } else if (lowSval.equals("inf") || lowSval.equals("+inf") || lowSval.equals("infinity") || lowSval.equals("+infinity")) {
                return Double.POSITIVE_INFINITY;
            } else if (lowSval.equals("-inf") || lowSval.equals("-infinity")) {
                return Double.NEGATIVE_INFINITY;
            }
            return Double.valueOf(sval).doubleValue();
        } catch (NumberFormatException exc) {
            // throw Py.ValueError("invalid literal for __float__: " + str);
            throw PythonLanguage.getCore().raise(ValueError, "could not convert string to float: %s", str);
        }
    }

    // Taken from Jython PyString's __complex__() method
    @TruffleBoundary
    public static PComplex convertStringToComplex(String str, PythonClass cls, PythonObjectFactory factory) {
        boolean gotRe = false;
        boolean gotIm = false;
        boolean done = false;
        boolean swError = false;

        int s = 0;
        int n = str.length();
        while (s < n && Character.isSpaceChar(str.charAt(s))) {
            s++;
        }

        if (s == n) {
            throw PythonLanguage.getCore().raise(ValueError, "empty string for complex()");
        }

        double z = -1.0;
        double x = 0.0;
        double y = 0.0;

        int sign = 1;
        do {
            char c = str.charAt(s);

            switch (c) {
                case '-':
                case '+':
                    if (c == '-') {
                        sign = -1;
                    }
                    if (done || s + 1 == n) {
                        swError = true;
                        break;
                    }
                    // a character is guaranteed, but it better be a digit
                    // or J or j
                    c = str.charAt(++s);  // eat the sign character
                    // and check the next
                    if (!Character.isDigit(c) && c != 'J' && c != 'j') {
                        swError = true;
                    }
                    break;

                case 'J':
                case 'j':
                    if (gotIm || done) {
                        swError = true;
                        break;
                    }
                    if (z < 0.0) {
                        y = sign;
                    } else {
                        y = sign * z;
                    }
                    gotIm = true;
                    done = gotRe;
                    sign = 1;
                    s++; // eat the J or j
                    break;

                case ' ':
                    while (s < n && Character.isSpaceChar(str.charAt(s))) {
                        s++;
                    }
                    if (s != n) {
                        swError = true;
                    }
                    break;

                default:
                    boolean digitOrDot = (c == '.' || Character.isDigit(c));
                    if (!digitOrDot) {
                        swError = true;
                        break;
                    }
                    int end = endDouble(str, s);
                    z = Double.valueOf(str.substring(s, end)).doubleValue();
                    if (z == Double.POSITIVE_INFINITY) {
                        throw PythonLanguage.getCore().raise(ValueError, String.format("float() out of range: %.150s", str));
                    }

                    s = end;
                    if (s < n) {
                        c = str.charAt(s);
                        if (c == 'J' || c == 'j') {
                            break;
                        }
                    }
                    if (gotRe) {
                        swError = true;
                        break;
                    }

                    /* accept a real part */
                    x = sign * z;
                    gotRe = true;
                    done = gotIm;
                    z = -1.0;
                    sign = 1;
                    break;

            } /* end of switch */

        } while (s < n && !swError);

        if (swError) {
            throw PythonLanguage.getCore().raise(ValueError, "malformed string for complex() %s", str.substring(s));
        }

        return factory.createComplex(cls, x, y);
    }

    // Taken from Jython PyString directly
    private static int endDouble(String string, int s) {
        int end = s;
        int n = string.length();
        while (end < n) {
            char c = string.charAt(end++);
            if (Character.isDigit(c)) {
                continue;
            }
            if (c == '.') {
                continue;
            }
            if (c == 'e' || c == 'E') {
                if (end < n) {
                    c = string.charAt(end);
                    if (c == '+' || c == '-') {
                        end++;
                    }
                    continue;
                }
            }
            return end - 1;
        }
        return end;
    }

    @TruffleBoundary
    public static String doubleToString(double item) {

        String d = Double.toString(item);
        int exp = d.indexOf("E");
        if (exp != -1) {
            int l = d.length() - 1;
            if (exp == (l - 2)) {
                if (d.charAt(exp + 1) == '-') {
                    if (Integer.valueOf(d.charAt(l) + "") == 4)
                        /*- Java convert double when 0.000###... while Python does it when 0.0000####... */
                        d = Double.toString((item * 10)).replace(".", ".0");
                    else
                        d = d.substring(0, l) + "0" + d.substring(l);

                    exp = d.indexOf("E");
                }
            }
            if (exp != -1 && d.charAt(exp + 1) != '-')
                d = d.substring(0, exp + 1) + "+" + d.substring(exp + 1, l + 1);
            d = d.toLowerCase();
        }
        return d;
    }

    @TruffleBoundary
    public static String doubleToStringJython(double item) {
        String d = Double.toString(item);
        return d.replace('E', 'e');
    }

    // Taken from Jython __builtin__ class chr(int i) method
    // Upper bound is modified to 1114111(0x10FFFF) based on Python 3 semantics
    public static char convertIntToChar(int i) {
        if (i < 0 || i > 0x10FFFF) {
            throw PythonLanguage.getCore().raise(ValueError, "chr() arg not in range(0x110000)");
        }
        return (char) i;
    }
}
