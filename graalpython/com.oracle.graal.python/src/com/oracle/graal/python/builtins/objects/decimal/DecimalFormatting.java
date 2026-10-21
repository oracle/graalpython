/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.locale.PythonLocale.LC_NUMERIC;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.locale.LocaleUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

/** Decimal formatting is quiet: only rounding and capitals are taken from the context. */
final class DecimalFormatting {
    private static final TruffleString T_DECIMAL_POINT = tsLiteral("decimal_point");
    private static final TruffleString T_THOUSANDS_SEP = tsLiteral("thousands_sep");
    private static final TruffleString T_GROUPING = tsLiteral("grouping");
    private static final Pattern FORMAT = Pattern.compile("(?sU)\\A(?:(?<fill>.)?(?<align>[<>=^]))?(?<sign>[-+ ])?(?<z>z)?(?<alt>\\#)?(?<zero>0)?" +
                    "(?<width>(?!0)\\d+)?(?<sep>[,_])?(?:\\.(?<prec>\\d+))?(?<type>[eEfFgGnN%])?\\z");

    private DecimalFormatting() {
    }

    @TruffleBoundary
    static String format(PDecimal self, String format, Object override, PDecimalContext context) {
        Matcher match = FORMAT.matcher(format);
        if (!match.matches() || match.group("zero") != null && match.group("align") != null) {
            throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_INVALID);
        }
        String fill = groupOrDefault(match, "fill", " ");
        char align = groupOrDefault(match, "align", ">").charAt(0);
        char sign = groupOrDefault(match, "sign", "-").charAt(0);
        boolean zeroPad = match.group("zero") != null;
        boolean noNegativeZero = match.group("z") != null;
        boolean alternate = match.group("alt") != null;
        long width = number(match.group("width"), 0);
        long precision = number(match.group("prec"), -1);
        String defaultType = "g";
        if (context.getCapitals()) {
            defaultType = "G";
        }
        char type = groupOrDefault(match, "type", defaultType).charAt(0);
        char lowerType = Character.toLowerCase(type);
        // CPython falls back to _pydecimal for these newer syntax features. Its parser
        // rejects leading precision zeros and does not consume the locale override.
        boolean fallback = noNegativeZero || alternate || "_".equals(match.group("sep")) ||
                        !asciiDigits(match.group("width")) || !asciiDigits(match.group("prec"));
        String precisionString = match.group("prec");
        if (fallback && (type == 'N' || precisionString != null && precisionString.length() > 1 && precisionString.charAt(0) == '0')) {
            throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_INVALID);
        }
        if (precision == 0 && (lowerType == 'g' || lowerType == 'n')) {
            precision = 1;
        }
        String separator = groupOrDefault(match, "sep", "");
        // libmpdec leaves locale settings empty unless grouping or 'n' is requested.
        String dot = separator.isEmpty() ? "" : ".";
        String grouping = separator.isEmpty() ? "" : "\003\000";
        if (lowerType == 'n') {
            if (!separator.isEmpty()) {
                throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_INVALID);
            }
            Locale locale = PythonContext.get(null).getCurrentLocale().category(LC_NUMERIC);
            NumberFormat numberFormat = LocaleUtils.getNumberFormat(locale);
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
            dot = String.valueOf(symbols.getDecimalSeparator());
            separator = "";
            grouping = "";
            if (numberFormat instanceof DecimalFormat df && df.isGroupingUsed()) {
                separator = String.valueOf(symbols.getGroupingSeparator());
                grouping = String.valueOf((char) df.getGroupingSize()) + '\000';
            }
            type = type == 'N' ? 'G' : 'g';
            lowerType = 'g';
        }
        if (!fallback && override != PNone.NO_VALUE) {
            if (!(override instanceof PDict dict)) {
                throw PRaiseNode.getUncached().raise(null, TypeError, ErrorMessages.DECIMAL_FORMAT_DICT);
            }
            dot = overrideString(dict, T_DECIMAL_POINT, dot);
            separator = overrideString(dict, T_THOUSANDS_SEP, separator);
            grouping = overrideString(dict, T_GROUPING, grouping);
            if (dot.isEmpty() || dot.getBytes(StandardCharsets.UTF_8).length > 4 || separator.getBytes(StandardCharsets.UTF_8).length > 4 ||
                            !isAscii(grouping)) {
                throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_OVERRIDE);
            }
        }
        if (dot.isEmpty()) {
            dot = ".";
        }
        if (width > DecimalUtils.MAX_PREC || self.getKind() == Kind.FINITE && precision > DecimalUtils.MAX_PREC) {
            throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_LIMITS);
        }
        if (self.getKind() != Kind.FINITE) {
            String body = self.toScientificString(true);
            if (self.isNegative()) {
                body = body.substring(1);
            }
            if (type == '%') {
                body = body + '%';
            }
            if (zeroPad) {
                fill = " ";
                align = '>';
            }
            return align(sign(self.isNegative(), sign), body, fill, align, width);
        }
        long valueExponent = self.getExponent();
        if (type == '%') {
            valueExponent += 2;
        }
        Value value = new Value(self.isNegative(), self.getCoefficient(), valueExponent, Kind.FINITE);
        if (precision >= 0) {
            if (lowerType == 'f' || type == '%') {
                value = DecimalArithmetic.rescale(value, -precision, context.getRounding());
            } else if (lowerType == 'e' && value.coefficient().signum() != 0) {
                value = round(value, precision + 1, context.getRounding());
            } else if (lowerType == 'g' && DecimalUtils.digits(value.coefficient()) > precision) {
                value = round(value, precision, context.getRounding());
            }
        }
        boolean zero = value.coefficient().signum() == 0;
        if (zero && value.exponent() > 0 && (lowerType == 'f' || type == '%')) {
            value = new Value(value.negative(), BigInteger.ZERO, 0, Kind.FINITE);
        }
        String digits = value.coefficient().toString();
        long leftDigits = value.exponent() + digits.length();
        long point;
        if (lowerType == 'e') {
            point = 1;
            if (zero && precision >= 0) {
                point = 1 - precision;
            }
        } else if (lowerType == 'f' || type == '%') {
            point = leftDigits;
        } else {
            point = 1;
            if (value.exponent() <= 0 && leftDigits > -6) {
                point = leftDigits;
            }
        }
        String integer;
        String fraction;
        if (point <= 0) {
            integer = "0";
            fraction = zeros(-point) + digits;
        } else if (point >= digits.length()) {
            integer = digits + zeros(point - digits.length());
            fraction = "";
        } else {
            integer = digits.substring(0, (int) point);
            fraction = digits.substring((int) point);
        }
        long exponent = leftDigits - point;
        StringBuilder suffix = new StringBuilder();
        if (!fraction.isEmpty() || alternate) {
            suffix.append(dot).append(fraction);
        }
        if (exponent != 0 || lowerType == 'e') {
            suffix.append(Character.isUpperCase(type) ? 'E' : 'e');
            if (exponent >= 0) {
                suffix.append('+');
            }
            suffix.append(exponent);
        }
        if (type == '%') {
            suffix.append('%');
        }
        String signString = sign(value.negative() && !(zero && noNegativeZero), sign);
        long minWidth = 0;
        if (zeroPad) {
            minWidth = width - suffix.codePointCount(0, suffix.length()) - signString.length();
        }
        integer = group(integer, separator, grouping, minWidth);
        return align(signString, integer + suffix, fill, align, width);
    }

    private static Value round(Value value, long precision, DecimalRounding rounding) {
        long exponent = value.exponent() + DecimalUtils.digits(value.coefficient()) - precision;
        Value result = DecimalArithmetic.rescale(value, exponent, rounding);
        if (DecimalUtils.digits(result.coefficient()) > precision) {
            result = DecimalArithmetic.rescale(result, exponent + 1, rounding);
        }
        return result;
    }

    private static String groupOrDefault(Matcher match, String group, String defaultValue) {
        String result = match.group(group);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    private static boolean asciiDigits(String value) {
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static long number(String number, long defaultValue) {
        if (number == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw PRaiseNode.getUncached().raise(null, ValueError, ErrorMessages.DECIMAL_FORMAT_INVALID);
        }
    }

    private static String overrideString(PDict dict, TruffleString key, String defaultValue) {
        Object value = HashingStorageGetItem.executeUncached(dict.getDictStorage(), key);
        if (value == null) {
            return defaultValue;
        }
        if (!PyUnicodeCheckNode.executeUncached(value)) {
            throw PRaiseNode.getUncached().raise(null, TypeError, ErrorMessages.DECIMAL_FORMAT_STRING);
        }
        String result = CastToJavaStringNode.getUncached().execute(value);
        // libmpdec treats the override values as NUL-terminated strings.
        int nul = result.indexOf('\000');
        return nul < 0 ? result : result.substring(0, nul);
    }

    private static String zeros(long count) {
        return "0".repeat(Math.toIntExact(count));
    }

    private static String sign(boolean negative, char sign) {
        if (negative) {
            return "-";
        }
        if (sign == '-') {
            return "";
        }
        return String.valueOf(sign);
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String align(String sign, String body, String fill, char align, long width) {
        int padding = Math.toIntExact(Math.max(0, width - sign.length() - length(body)));
        StringBuilder result = new StringBuilder();
        return switch (align) {
            case '<' -> result.append(sign).append(body).append(fill.repeat(padding)).toString();
            case '=' -> result.append(sign).append(fill.repeat(padding)).append(body).toString();
            case '^' -> result.append(fill.repeat(padding / 2)).append(sign).append(body).append(fill.repeat(padding - padding / 2)).toString();
            default -> result.append(fill.repeat(padding)).append(sign).append(body).toString();
        };
    }

    private static String group(String digits, String separator, String grouping, long width) {
        List<String> groups = new ArrayList<>();
        int end = digits.length();
        int index = 0;
        int previous = 0;
        while (true) {
            int size = previous;
            if (index < grouping.length()) {
                size = grouping.charAt(index++);
            }
            if (size == 0) {
                size = previous;
            }
            if (size == 127 || size == 0) {
                size = Math.toIntExact(Math.max(Math.max(end, width), 1));
            }
            previous = size;
            int count = (int) Math.min(Math.max(Math.max(end, width), 1), size);
            int start = Math.max(0, end - count);
            groups.add(new StringBuilder().append(zeros(count - (end - start))).append(digits, start, end).toString());
            end = start;
            width -= count;
            if (end == 0 && width <= 0) {
                break;
            }
            width -= length(separator);
        }
        Collections.reverse(groups);
        return String.join(separator, groups);
    }
}
