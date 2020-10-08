/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.formatting.InternalFormat.Spec.specified;

import java.math.BigInteger;
import java.text.NumberFormat;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.formatting.FormattingBuffer.StringFormattingBuffer;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A class that provides the implementation of integer formatting. In a limited way, it acts like a
 * StringFormattingBuffer to which text and one or more numbers may be appended, formatted according
 * to the format specifier supplied at construction. These are ephemeral objects that are not, on
 * their own, thread safe.
 */
public class IntegerFormatter extends InternalFormat.Formatter {

    /**
     * Construct the formatter from a client-supplied buffer, to which the result will be appended,
     * and a specification. Sets {@link #mark} to the end of the buffer.
     *
     * @param result destination buffer
     * @param spec parsed conversion specification
     */
    public IntegerFormatter(PRaiseNode raiseNode, FormattingBuffer result, Spec spec) {
        super(raiseNode, result, spec);
    }

    public IntegerFormatter(PRaiseNode raiseNode, Spec spec) {
        super(raiseNode, new StringFormattingBuffer(32), spec);
    }

    /*
     * Re-implement the text appends so they return the right type.
     */
    @Override
    public IntegerFormatter append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public IntegerFormatter append(CharSequence csq) {
        super.append(csq);
        return this;
    }

    @Override
    public IntegerFormatter append(CharSequence csq, int begin, int end) //
                    throws IndexOutOfBoundsException {
        super.append(csq, begin, end);
        return this;
    }

    /**
     * Format a {@link BigInteger}, which is the implementation type of Jython <code>long</code>,
     * according to the specification represented by this <code>IntegerFormatter</code>. The
     * conversion type, and flags for grouping or base prefix are dealt with here. At the point this
     * is used, we know the {@link #spec} is one of the integer types.
     *
     * @param value to convert
     * @return this object
     */
    public IntegerFormatter format(BigInteger value) {
        try {
            // Scratch all instance variables and start = result.length().
            setStart();

            // Different process for each format type.
            switch (spec.type) {
                case 'd':
                case Spec.NONE:
                    // None format or d-format: decimal
                    format_d(value);
                    break;
                case 'u':
                case 'i':
                    format_i(value);
                    break;

                case 'x':
                    // hexadecimal.
                    format_x(value, false);
                    break;

                case 'X':
                    // HEXADECIMAL!
                    format_x(value, true);
                    break;

                case 'o':
                    // Octal.
                    format_o(value);
                    break;

                case 'b':
                    // Binary.
                    format_b(value);
                    break;

                case 'c':
                case '%':
                    // Binary.
                    format_c(value);
                    break;

                case 'n':
                    // Locale-sensitive version of d-format
                    format_d(value);
                    setGroupingAndGroupSize(getCurrentDecimalFormat());
                    break;

                default:
                    // Should never get here, since this was checked in caller.
                    throw unknownFormat(raiseNode, spec.type, "integer");
            }

            // If required to, group the whole-part digits.
            groupWholePartIfRequired();

            return this;

        } catch (OutOfMemoryError eme) {
            // Most probably due to excessive precision.
            throw precisionTooLarge("long");
        }
    }

    /**
     * This format specifier is not allowed in format, but allowed in printf-style formatting. This
     * method is overridden in the {@link Traditional} formatter to delegate to
     * {@link #format_d(BigInteger)}.
     */
    void format_i(@SuppressWarnings("unused") BigInteger value) {
        throw unknownFormat(raiseNode, spec.type, "integer");
    }

    void format_i(@SuppressWarnings("unused") int value) {
        throw unknownFormat(raiseNode, spec.type, "integer");
    }

    /**
     * Format the value as decimal (into {@link #result}). The option for mandatory sign is dealt
     * with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_d(BigInteger value) {
        String number;
        if (value.signum() < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(null);
            number = value.negate().toString();
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(null);
            number = value.toString();
        }
        appendNumber(number);
    }

    /**
     * Format the value as decimal (into {@link #result}) according to the current locale.
     */
    void format_n(BigInteger value) {
        String number;
        NumberFormat nf = NumberFormat.getNumberInstance();
        if (value.signum() < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(null);
            number = nf.format(value.negate());
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(null);
            number = nf.format(value);
        }
        appendNumber(number);
    }

    /**
     * Format the value as decimal (into {@link #result}) according to the current locale.
     */
    void format_n(int value) {
        String number;
        NumberFormat nf = NumberFormat.getNumberInstance();
        if (value < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(null);
            number = nf.format(-value);
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(null);
            number = nf.format(value);
        }
        appendNumber(number);
    }

    /**
     * Format the value as hexadecimal (into {@link #result}), with the option of using upper-case
     * or lower-case letters. The options for mandatory sign and for the presence of a base-prefix
     * "0x" or "0X" are dealt with by reference to the format specification.
     *
     * @param value to convert
     * @param upper if the hexadecimal should be upper case
     */
    void format_x(BigInteger value, boolean upper) {
        String base = upper ? "0X" : "0x";
        String number;
        if (value.signum() < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = toHexString(value.negate());
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = toHexString(value);
        }
        // Append to result, case-shifted if necessary.
        if (upper) {
            number = number.toUpperCase();
        }
        appendNumber(number);
    }

    /**
     * Format the value as octal (into {@link #result}). The options for mandatory sign and for the
     * presence of a base-prefix "0o" are dealt with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_o(BigInteger value) {
        String base = "0o";
        String number;
        if (value.signum() < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = toOctalString(value.negate());
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = toOctalString(value);
        }
        // Append to result.
        appendNumber(number);
    }

    /**
     * Format the value as binary (into {@link #result}). The options for mandatory sign and for the
     * presence of a base-prefix "0b" are dealt with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_b(BigInteger value) {
        String base = "0b";
        String number;
        if (value.signum() < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = toBinaryString(value.negate());
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = toBinaryString(value);
        }
        // Append to result.
        appendNumber(number);
    }

    /**
     * Format the value as a character (into {@link #result}).
     *
     * @param value to convert
     */
    final void format_c(BigInteger value) {
        assert !bytes; // for bytes we use directly BytesFormatter
        if (value.signum() < 0 || value.compareTo(LIMIT_UNICODE) >= 0) {
            throw raiseNode.raise(OverflowError, ErrorMessages.C_ARG_NOT_IN_RANGE, toHexString(LIMIT_UNICODE));
        }
        result.appendCodePoint(value.intValue());
    }

    // Limits used in format_c(BigInteger)
    public static final BigInteger LIMIT_UNICODE = BigInteger.valueOf(0x110000);

    /**
     * Format an integer according to the specification represented by this
     * <code>IntegerFormatter</code>. The conversion type, and flags for grouping or base prefix are
     * dealt with here. At the point this is used, we know the {@link #spec} is one of the integer
     * types.
     *
     * @param value to convert
     * @return this object
     */
    @TruffleBoundary
    public IntegerFormatter format(int value) {
        try {
            // Scratch all instance variables and start = result.length().
            setStart();

            // Different process for each format type.
            switch (spec.type) {
                case 'd':
                case Spec.NONE:
                    // None format or d-format: decimal
                    format_d(value);
                    break;
                case 'u':
                case 'i':
                    format_i(value);
                    break;

                case 'x':
                    // hexadecimal.
                    format_x(value, false);
                    break;

                case 'X':
                    // HEXADECIMAL!
                    format_x(value, true);
                    break;

                case 'o':
                    // Octal.
                    format_o(value);
                    break;

                case 'b':
                    // Binary.
                    format_b(value);
                    break;

                case 'c':
                case '%':
                    // Binary.
                    format_c(value);
                    break;

                case 'n':
                    // Locale-sensitive version of d-format
                    format_d(value);
                    setGroupingAndGroupSize(getCurrentDecimalFormat());
                    break;

                default:
                    throw unknownFormat(raiseNode, spec.type, "int");
            }

            // If required to, group the whole-part digits.
            groupWholePartIfRequired();

            return this;
        } catch (OutOfMemoryError eme) {
            // Most probably due to excessive precision.
            throw precisionTooLarge("integer");
        }
    }

    /**
     * Format the value as decimal (into {@link #result}). The option for mandatory sign is dealt
     * with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_d(int value) {
        String number;
        if (value < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(null);
            number = Integer.toString(-value);
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(null);
            number = Integer.toString(value);
        }
        appendNumber(number);
    }

    /**
     * Format the value as hexadecimal (into {@link #result}), with the option of using upper-case
     * or lower-case letters. The options for mandatory sign and for the presence of a base-prefix
     * "0x" or "0X" are dealt with by reference to the format specification.
     *
     * @param value to convert
     * @param upper if the hexadecimal should be upper case
     */
    void format_x(int value, boolean upper) {
        String base = upper ? "0X" : "0x";
        String number;
        if (value < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = Integer.toHexString(-value);
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = Integer.toHexString(value);
        }
        // Append to result, case-shifted if necessary.
        if (upper) {
            number = number.toUpperCase();
        }
        appendNumber(number);
    }

    /**
     * Format the value as octal (into {@link #result}). The options for mandatory sign and for the
     * presence of a base-prefix "0o" are dealt with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_o(int value) {
        String base = "0o";
        String number;
        if (value < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = Integer.toOctalString(-value);
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = Integer.toOctalString(value);
        }
        // Append to result.
        appendNumber(number);
    }

    /**
     * Format the value as binary (into {@link #result}). The options for mandatory sign and for the
     * presence of a base-prefix "0b" are dealt with by reference to the format specification.
     *
     * @param value to convert
     */
    void format_b(int value) {
        String base = "0b";
        String number;
        if (value < 0) {
            // Negative value: deal with sign and base, and convert magnitude.
            negativeSign(base);
            number = Integer.toBinaryString(-value);
        } else {
            // Positive value: deal with sign, base and magnitude.
            positiveSign(base);
            number = Integer.toBinaryString(value);
        }
        // Append to result.
        appendNumber(number);
    }

    /**
     * Format the value as a character (into {@link #result}).
     *
     * @param value to convert
     */
    final void format_c(int value) {
        assert !bytes; // for bytes we use directly BytesFormatter
        if (value < 0 || value >= LIMIT_UNICODE.intValue()) {
            throw raiseNode.raise(OverflowError, ErrorMessages.C_ARG_NOT_IN_RANGE, toHexString(LIMIT_UNICODE));
        }
        result.appendCodePoint(value);
    }

    /**
     * Append to {@link #result} buffer a sign (if one is specified for positive numbers) and, in
     * alternate mode, the base marker provided. The sign and base marker are together considered to
     * be the "sign" of the converted number, spanned by {@link #lenSign}. This is relevant when we
     * come to insert padding.
     *
     * @param base marker "0x" or "0X" for hex, "0o" for octal, "0b" for binary, "" or
     *            <code>null</code> for decimal.
     */
    final void positiveSign(String base) {
        // Does the format specify a sign for positive values?
        char sign = spec.sign;
        if (specified(sign) && sign != '-') {
            append(sign);
            lenSign = 1;
        }
        // Does the format call for a base prefix?
        if (base != null && spec.alternate) {
            append(base);
            lenSign += base.length();
        }
    }

    /**
     * Append to {@link #result} buffer a minus sign and, in alternate mode, the base marker
     * provided. The sign and base marker are together considered to be the "sign" of the converted
     * number, spanned by {@link #lenSign}. This is relevant when we come to insert padding.
     *
     * @param base marker ("0x" or "0X" for hex, "0" for octal, <code>null</code> or "" for decimal.
     */
    final void negativeSign(String base) {
        // Insert a minus sign unconditionally.
        append('-');
        lenSign = 1;
        // Does the format call for a base prefix?
        if (base != null && spec.alternate) {
            append(base);
            lenSign += base.length();
        }
    }

    /**
     * Append a string (number) to {@link #result} and set {@link #lenWhole} to its length .
     *
     * @param number to append
     */
    void appendNumber(String number) {
        lenWhole = number.length();
        append(number);
    }

    // For hex-conversion by lookup
    private static final String LOOKUP = "0123456789abcdef";

    /**
     * A more efficient algorithm for generating a hexadecimal representation of a byte array.
     * {@link BigInteger#toString(int)} is too slow because it generalizes to any radix and,
     * consequently, is implemented using expensive mathematical operations.
     *
     * @param value the value to generate a hexadecimal string from
     * @return the hexadecimal representation of value, with "-" sign prepended if necessary
     */
    private static String toHexString(BigInteger value) {
        int signum = value.signum();

        // obvious shortcut
        if (signum == 0) {
            return "0";
        }

        // we want to work in absolute numeric value (negative sign is added afterward)
        byte[] input = value.abs().toByteArray();
        FormattingBuffer.StringFormattingBuffer sb = new FormattingBuffer.StringFormattingBuffer(input.length * 2);

        int b;
        for (int i = 0; i < input.length; i++) {
            b = input[i] & 0xFF;
            sb.append(LOOKUP.charAt(b >> 4));
            sb.append(LOOKUP.charAt(b & 0x0F));
        }

        // before returning the char array as string, remove leading zeroes, but not the last one
        String result = sb.toString().replaceFirst("^0+(?!$)", "");
        return signum < 0 ? "-" + result : result;
    }

    /**
     * A more efficient algorithm for generating an octal representation of a byte array.
     * {@link BigInteger#toString(int)} is too slow because it generalizes to any radix and,
     * consequently, is implemented using expensive mathematical operations.
     *
     * @param value the value to generate an octal string from
     * @return the octal representation of value, with "-" sign prepended if necessary
     */
    private static String toOctalString(BigInteger value) {
        int signum = value.signum();

        // obvious shortcut
        if (signum == 0) {
            return "0";
        }

        byte[] input = value.abs().toByteArray();
        if (input.length < 3) {
            return value.toString(8);
        }

        StringBuilder sb = new StringBuilder(input.length * 3);

        // working backwards, three bytes at a time
        int threebytes;
        int trip1, trip2, trip3;    // most, middle, and least significant bytes in the triplet
        for (int i = input.length - 1; i >= 0; i -= 3) {
            trip3 = input[i] & 0xFF;
            trip2 = ((i - 1) >= 0) ? (input[i - 1] & 0xFF) : 0x00;
            trip1 = ((i - 2) >= 0) ? (input[i - 2] & 0xFF) : 0x00;
            threebytes = trip3 | (trip2 << 8) | (trip1 << 16);

            // convert the three-byte value into an eight-character octal string
            for (int j = 0; j < 8; j++) {
                sb.append(LOOKUP.charAt((threebytes >> (j * 3)) & 0x000007));
            }
        }

        String result = sb.reverse().toString().replaceFirst("^0+(?!%)", "");
        return signum < 0 ? "-" + result : result;
    }

    /**
     * A more efficient algorithm for generating a binary representation of a byte array.
     * {@link BigInteger#toString(int)} is too slow because it generalizes to any radix and,
     * consequently, is implemented using expensive mathematical operations.
     *
     * @param value the value to generate a binary string from
     * @return the binary representation of value, with "-" sign prepended if necessary
     */
    private static String toBinaryString(BigInteger value) {
        int signum = value.signum();

        // obvious shortcut
        if (signum == 0) {
            return "0";
        }

        // we want to work in absolute numeric value (negative sign is added afterward)
        byte[] input = value.abs().toByteArray();
        FormattingBuffer.StringFormattingBuffer sb = new FormattingBuffer.StringFormattingBuffer(value.bitCount());

        int b;
        for (int i = 0; i < input.length; i++) {
            b = input[i] & 0xFF;
            for (int bit = 7; bit >= 0; bit--) {
                sb.append(((b >> bit) & 0x1) > 0 ? "1" : "0");
            }
        }

        // before returning the char array as string, remove leading zeroes, but not the last one
        String result = sb.toString().replaceFirst("^0+(?!$)", "");
        return signum < 0 ? "-" + result : result;
    }

    /**
     * A minor variation on {@link IntegerFormatter} to handle "traditional" %-formatting. The
     * difference is in support for <code>spec.precision</code>, and in c-format (in the error
     * logic).
     */
    public static class Traditional extends IntegerFormatter {

        /**
         * Construct the formatter from a client-supplied buffer, to which the result will be
         * appended, and a specification. Sets {@link #mark} to the end of the buffer.
         *
         * @param result destination buffer
         * @param spec parsed conversion specification
         */
        public Traditional(PRaiseNode raiseNode, FormattingBuffer result, Spec spec) {
            super(raiseNode, result, spec);
        }

        @Override
        void format_i(BigInteger value) {
            format_n(value);
        }

        @Override
        void format_i(int value) {
            format_n(value);
        }

        /**
         * Append a string (number) to {@link #result}, but insert leading zeros first in order
         * that, on return, the whole-part length #lenWhole should be no less than the precision.
         *
         * @param number to append
         */
        @Override
        void appendNumber(String number) {
            int n, p = spec.getPrecision(0);
            result.ensureAdditionalCapacity(p);
            for (n = number.length(); n < p; n++) {
                result.append('0');
            }
            lenWhole = n;
            append(number);
        }
    }
}
