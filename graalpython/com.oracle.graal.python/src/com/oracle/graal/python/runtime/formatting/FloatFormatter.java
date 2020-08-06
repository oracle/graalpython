/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.runtime.formatting.InternalFormat.Spec.specified;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A class that provides the implementation of floating-point formatting. In a limited way, it acts
 * like a StringFormattingBuffer to which text and one or more numbers may be appended, formatted
 * according to the format specifier supplied at construction. These are ephemeral objects that are
 * not, on their own, thread safe.
 */
public class FloatFormatter extends InternalFormat.Formatter {

    /** The rounding mode dominant in the formatter. */
    static final RoundingMode ROUND_PY = RoundingMode.HALF_EVEN;

    /** If it contains no decimal point, this length is zero, and 1 otherwise. */
    private int lenPoint;
    /** The length of the fractional part, right of the decimal point. */
    private int lenFraction;
    /** The length of the exponent marker ("e"), "inf" or "nan", or zero if there isn't one. */
    private int lenMarker;
    /** The length of the exponent sign and digits or zero if there isn't one. */
    private int lenExponent;
    /** if >=0, minimum digits to follow decimal point (where consulted) */
    private int minFracDigits;

    /**
     * Construct the formatter from a client-supplied buffer, to which the result will be appended,
     * and a specification. Sets {@link #mark} to the end of the buffer.
     *
     * @param result destination buffer
     * @param spec parsed conversion specification
     * @param addDot0 reflects flag {@code Py_DTSF_ADD_DOT_0} in CPython, applicable only for 'r'
     *            specifier
     */
    public FloatFormatter(PythonCore core, FormattingBuffer result, Spec spec, boolean addDot0) {
        super(core, result, spec);
        if (!addDot0 && spec.type == 'r') {
            minFracDigits = 0;
        } else if (spec.alternate) {
            // Alternate form means do not trim the zero fractional digits.
            // This should be equivalent to Py_DTSF_ALT flag in CPython
            minFracDigits = -1;
        } else if (spec.type == 'r' || spec.type == Spec.NONE) {
            // These formats by default show at least one fractional digit.
            minFracDigits = 1;
        } else {
            /*
             * Every other format (if it does not ignore the setting) will by default trim off all
             * the trailing zero fractional digits.
             */
            minFracDigits = 0;
        }
    }

    public FloatFormatter(PythonCore core, FormattingBuffer result, Spec spec) {
        this(core, result, spec, true);
    }

    public FloatFormatter(PythonCore core, Spec spec) {
        this(core, new FormattingBuffer.StringFormattingBuffer(size(spec)), spec);
    }

    /**
     * Recommend a buffer size for a given specification, assuming one float is converted. This will
     * be a "right" answer for e and g-format, and for f-format with values up to 9,999,999.
     *
     * @param spec parsed conversion specification
     */
    public static int size(Spec spec) {
        // Rule of thumb used here (no right answer):
        // in e format each float occupies: (p-1) + len("+1.e+300") = p+7;
        // in f format each float occupies: p + len("1,000,000.%") = p+11;
        // or an explicit (minimum) width may be given, with one overshoot possible.
        return Math.max(spec.width + 1, spec.getPrecision(6) + 11);
    }

    /**
     * Override the default truncation behaviour for the specification originally supplied. Some
     * formats remove trailing zero digits, trimming to zero or one. Set member
     * <code>minFracDigits</code>, to modify this behaviour.
     *
     * @param minFracDigits if &lt;0 prevent truncation; if >=0 the minimum number of fractional
     *            digits; when this is zero, and all fractional digits are zero, the decimal point
     *            will also be removed.
     */
    public void setMinFracDigits(int minFracDigits) {
        this.minFracDigits = minFracDigits;
    }

    @Override
    protected void reset() {
        // Clear the variables describing the latest number in result.
        super.reset();
        lenPoint = lenFraction = lenMarker = lenExponent = 0;
    }

    @Override
    protected int[] sectionLengths() {
        return new int[]{lenSign, lenWhole, lenPoint, lenFraction, lenMarker, lenExponent};
    }

    /*
     * Re-implement the text appends so they return the right type.
     */
    @Override
    public FloatFormatter append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public FloatFormatter append(CharSequence csq) {
        super.append(csq);
        return this;
    }

    @Override
    public FloatFormatter append(CharSequence csq, int begin, int end) //
                    throws IndexOutOfBoundsException {
        super.append(csq, begin, end);
        return this;
    }

    /**
     * Format a floating-point number according to the specification represented by this
     * <code>FloatFormatter</code>.
     *
     * @param value to convert
     * @return this object
     */
    public FloatFormatter format(double value) {
        return format(value, null);
    }

    /**
     * Format a floating-point number according to the specification represented by this
     * <code>FloatFormatter</code>. The conversion type, precision, and flags for grouping or
     * percentage are dealt with here. At the point this is used, we know the {@link #spec} is one
     * of the floating-point types. This entry point allows explicit control of the prefix of
     * positive numbers, overriding defaults for the format type.
     *
     * @param value to convert
     * @param positivePrefixString to use before positive values (e.g. "+") or null to default to ""
     * @return this object
     */
    @TruffleBoundary
    public FloatFormatter format(double value, String positivePrefixString) {
        String positivePrefix = positivePrefixString;
        // Puts all instance variables back to their starting defaults, and start = result.length().
        setStart();

        // Precision defaults to 6 (or 12 for none-format)
        int precision = spec.getPrecision(6);

        /*
         * By default, the prefix of a positive number is "", but the format specifier may override
         * it, and the built-in type complex needs to override the format.
         */
        char sign = spec.sign;
        if (positivePrefix == null && specified(sign) && sign != '-') {
            positivePrefix = Character.toString(sign);
        }

        char type = spec.type;
        int expThresholdAdj = 0;
        if (!specified(spec.type) && specified(spec.precision)) {
            // No specifier normally means do what __str__ would do, but if precision is specified,
            // we switch to g, moreover, CPython also adjusts the exponential notation threshold
            type = 'g';
            expThresholdAdj = -1;
        }

        // Different process for each format type, ignoring case for now.
        switch (Character.toLowerCase(type)) {
            case 'e':
                // Exponential case: 1.23e-45
                format_e(value, positivePrefix, precision);
                break;

            case 'f':
                // Fixed case: 123.45
                format_f(value, positivePrefix, precision);
                break;

            case 'g':
                // General format: fixed or exponential according to value.
                format_g(value, positivePrefix, precision, expThresholdAdj);
                break;

            case 'n':
                // Locale aware version of 'g'
                format_g(value, positivePrefix, precision, expThresholdAdj);
                DecimalFormat format = getCurrentDecimalFormat();
                if (format != null) {
                    setGroupingAndGroupSize(format);
                    DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
                    if (lenPoint > 0) {
                        result.setCharAt(lenWhole, symbols.getDecimalSeparator());
                    }
                }
                break;

            case Spec.NONE:
            case 'r':
                // For float.__repr__, very special case, breaks all the rules.
                format_r(value, positivePrefix);
                break;

            case '%':
                // Multiplies by 100 and displays in f-format, followed by a percent sign.
                format_f(100. * value, positivePrefix, precision);
                result.append('%');
                break;

            default:
                // Should never get here, since this was checked in PyFloat.
                throw unknownFormat(errors, spec.type, "float");
        }

        // If the format type is an upper-case letter, convert the result to upper case.
        if (Character.isUpperCase(spec.type)) {
            uppercase();
        }

        // If required to, group the whole-part digits.
        groupWholePartIfRequired();

        return this;
    }

    /**
     * Convert just the letters in the representation of the current number (in {@link #result}) to
     * upper case. (That's the exponent marker or the "inf" or "nan".)
     */
    @Override
    protected void uppercase() {
        int letters = indexOfMarker();
        int end = letters + lenMarker;
        for (int i = letters; i < end; i++) {
            char c = result.charAt(i);
            result.setCharAt(i, Character.toUpperCase(c));
        }
    }

    /**
     * Common code to deal with the sign, and the special cases "0", "-0", "nan, "inf", or "-inf".
     * If the method returns <code>false</code>, we have started a non-zero number and the sign is
     * already in {@link #result}. The client need then only encode <i>abs(value)</i>. If the method
     * returns <code>true</code>, and {@link #lenMarker}==0, the value was "0" or "-0": the caller
     * may have to zero-extend this, and/or add an exponent, to match the requested format. If the
     * method returns <code>true</code>, and {@link #lenMarker}>0, the method has placed "nan, "inf"
     * in the {@link #result} buffer (preceded by a sign if necessary).
     *
     * @param value to convert
     * @return true if the value was one of "0", "-0", "nan, "inf", or "-inf".
     * @param positivePrefix to use before positive values (e.g. "+") or null to default to ""
     */
    private boolean signAndSpecialNumber(double value, String positivePrefix) {

        // This is easiest via the raw bits
        long bits = Double.doubleToRawLongBits(value);

        // NaN is always positive
        if (Double.isNaN(value)) {
            bits &= ~SIGN_MASK;
        }

        if ((bits & SIGN_MASK) != 0) {
            // Negative: encode a minus sign and strip it off bits
            result.append('-');
            lenSign = 1;
            bits &= ~SIGN_MASK;

        } else if (positivePrefix != null) {
            // Positive, and a prefix is required. Note CPython 2.7 produces "+nan", " nan".
            result.append(positivePrefix);
            lenSign = positivePrefix.length();
        }

        if (bits == 0L) {
            // All zero means it's zero. (It may have been negative, producing -0.)
            result.append('0');
            lenWhole = 1;
            return true;

        } else if ((bits & EXP_MASK) == EXP_MASK) {
            // This is characteristic of NaN or Infinity.
            result.append(((bits & ~EXP_MASK) == 0L) ? "inf" : "nan");
            lenMarker = 3;
            return true;

        } else {
            return false;
        }
    }

    private static final long SIGN_MASK = 0x8000000000000000L;
    private static final long EXP_MASK = 0x7ff0000000000000L;

    /**
     * The e-format helper function of {@link #format(double, String)} that uses Java's
     * {@link BigDecimal} to provide conversion and rounding. The converted number is appended to
     * the {@link #result} buffer, and {@link #start} will be set to the index of its first
     * character.
     *
     * @param value to convert
     * @param positivePrefix to use before positive values (e.g. "+") or null to default to ""
     * @param precision precision (maximum number of fractional digits)
     */
    private void format_e(double value, String positivePrefix, int precision) {

        // Exponent (default value is for 0.0 and -0.0)
        int exp = 0;

        if (!signAndSpecialNumber(value, positivePrefix)) {
            // Convert abs(value) to decimal with p+1 digits of accuracy.
            MathContext mc = new MathContext(precision + 1, ROUND_PY);
            BigDecimal vv = new BigDecimal(Math.abs(value), mc);

            // Take explicit control in order to get exponential notation out of BigDecimal.
            String digits = vv.unscaledValue().toString();
            int digitCount = digits.length();
            result.append(digits.charAt(0));
            lenWhole = 1;
            if (digitCount > 1) {
                // There is a fractional part
                result.append('.').append(digits.substring(1));
                lenPoint = 1;
                lenFraction = digitCount - 1;
            }
            exp = lenFraction - vv.scale();
        }

        // If the result is not already complete, add point and zeros as necessary, and exponent.
        if (lenMarker == 0) {
            ensurePointAndTrailingZeros(precision);
            appendExponent(exp);
        }
    }

    /**
     * The f-format inner helper function of {@link #format(double, String)} that uses Java's
     * {@link BigDecimal} to provide conversion and rounding. The converted number is appended to
     * the {@link #result} buffer, and {@link #start} will be set to the index of its first
     * character.
     *
     * @param value to convert
     * @param positivePrefix to use before positive values (e.g. "+") or null to default to ""
     * @param precision precision (maximum number of fractional digits)
     */
    private void format_f(double value, String positivePrefix, int precision) {

        if (!signAndSpecialNumber(value, positivePrefix)) {
            // Convert value to decimal exactly. (This can be very long.)
            BigDecimal vLong = new BigDecimal(Math.abs(value));

            // Truncate to the defined number of places to the right of the decimal point).
            BigDecimal vv = vLong.setScale(precision, ROUND_PY);

            // When converted to text, the number of fractional digits is exactly the scale we set.
            String raw = vv.toPlainString();
            result.append(raw);
            if ((lenFraction = vv.scale()) > 0) {
                // There is a decimal point and some digits following
                lenWhole = result.length() - (start + lenSign + (lenPoint = 1) + lenFraction);
            } else {
                // There are no fractional digits and so no decimal point
                lenWhole = result.length() - (start + lenSign);
            }
        }

        // Finally, ensure we have all the fractional digits we should.
        if (lenMarker == 0) {
            ensurePointAndTrailingZeros(precision);
        }
    }

    /**
     * Append a decimal point and trailing fractional zeros if necessary for 'e' and 'f' format.
     * This should not be called if the result is not numeric ("inf" for example). This method deals
     * with the following complexities: on return there will be at least the number of fractional
     * digits specified in the argument <code>n</code>, and at least {@link #minFracDigits};
     * further, if <code>minFracDigits&lt;0</code>, signifying the "alternate mode" of certain
     * formats, the method will ensure there is a decimal point, even if there are no fractional
     * digits to follow.
     *
     * @param numDigits smallest number of fractional digits on return
     */
    private void ensurePointAndTrailingZeros(int numDigits) {
        int n = numDigits;
        // Set n to the number of fractional digits we should have.
        if (n < minFracDigits) {
            n = minFracDigits;
        }

        // Do we have a decimal point already?
        if (lenPoint == 0) {
            // No decimal point: add one if there will be any fractional digits or
            if (n > 0 || minFracDigits < 0) {
                // First need to add a decimal point.
                result.append('.');
                lenPoint = 1;
            }
        }

        // Do we have enough fractional digits already?
        int f = lenFraction;
        if (n > f) {
            // Make up the required number of zeros.
            for (; f < n; f++) {
                result.append('0');
            }
            lenFraction = f;
        }
    }

    /**
     * Implementation of the variants of g-format, that uses Java's {@link BigDecimal} to provide
     * conversion and rounding. These variants are g-format proper, alternate g-format (available
     * for "%#g" formatting), n-format (as g but subsequently "internationalised"), and none-format
     * (type code Spec.NONE).
     * <p>
     * None-format is the basis of <code>float.__str__</code>.
     * <p>
     * According to the Python documentation for g-format, the precise rules are as follows: suppose
     * that the result formatted with presentation type <code>'e'</code> and precision <i>p-1</i>
     * would have exponent exp. Then if <i>-4 &lt;= exp < p</i>, the number is formatted with
     * presentation type <code>'f'</code> and precision <i>p-1-exp</i>. Otherwise, the number is
     * formatted with presentation type <code>'e'</code> and precision <i>p-1</i>. In both cases
     * insignificant trailing zeros are removed from the significand, and the decimal point is also
     * removed if there are no remaining digits following it.
     * <p>
     * The Python documentation says none-format is the same as g-format, but the observed behaviour
     * differs from this, in that f-format is only used if <i>-4 &lt;= exp < p-1</i> (i.e. one
     * less), and at least one digit to the right of the decimal point is preserved in the f-format
     * (but not the e-format). That behaviour is controlled through the following arguments, with
     * these recommended values:
     *
     * <table>
     * <tr>
     * <th>type</th>
     * <th>precision</th>
     * <th>minFracDigits</th>
     * <th>expThresholdAdj</th>
     * <td>expThreshold</td>
     * </tr>
     * <tr>
     * <th>g</th>
     * <td>p</td>
     * <td>0</td>
     * <td>0</td>
     * <td>p</td>
     * </tr>
     * <tr>
     * <th>#g</th>
     * <td>p</td>
     * <td>-</td>
     * <td>0</td>
     * <td>p</td>
     * </tr>
     * <tr>
     * <th>\0</th>
     * <td>p</td>
     * <td>1</td>
     * <td>-1</td>
     * <td>p-1</td>
     * </tr>
     * <tr>
     * <th>__str__</th>
     * <td>12</td>
     * <td>1</td>
     * <td>-1</td>
     * <td>11</td>
     * </tr>
     * </table>
     *
     * @param value to convert
     * @param positivePrefix to use before positive values (e.g. "+") or null to default to ""
     * @param precision total number of significant digits (precision 0 behaves as 1)
     * @param expThresholdAdj <code>+precision =</code> the exponent at which to resume using
     *            exponential notation
     */
    private void format_g(double value, String positivePrefix, int precision, int expThresholdAdj) {

        // Precision 0 behaves as 1
        int prec = Math.max(1, precision);

        // Use exponential notation if exponent would be bigger thatn:
        int expThreshold = prec + expThresholdAdj;

        if (signAndSpecialNumber(value, positivePrefix)) {
            // Finish formatting if zero result. (This is a no-op for nan or inf.)
            zeroHelper(prec, expThreshold);

        } else {

            // Convert abs(value) to decimal with p digits of accuracy.
            MathContext mc = new MathContext(prec, ROUND_PY);
            BigDecimal vv = new BigDecimal(Math.abs(value), mc);

            // This gives us the digits we need for either fixed or exponential format.
            String pointlessDigits = vv.unscaledValue().toString();

            // If we were to complete this as e-format, the exponent would be:
            int exp = pointlessDigits.length() - vv.scale() - 1;

            if (-4 <= exp && exp < expThreshold) {
                // Finish the job as f-format with variable-prec p-(exp+1).
                appendFixed(pointlessDigits, exp, prec);

            } else {
                // Finish the job as e-format.
                appendExponential(pointlessDigits, exp);
            }
        }
    }

    /**
     * Implementation of r-format (<code>float.__repr__</code>) that uses Java's
     * {@link Double#toString(double)} to provide conversion and rounding. That method gives us
     * almost what we need, but not quite (sometimes it yields 18 digits): here we always round to
     * 17 significant digits. Much of the formatting after conversion is shared with
     * format_g(double, String, int, int, int). <code>minFracDigits</code> is consulted since while
     * <code>float.__repr__</code> truncates to one digit, within <code>complex.__repr__</code> we
     * truncate fully.
     *
     * @param value to convert
     * @param positivePrefix to use before positive values (e.g. "+") or null to default to ""
     */
    private void format_r(double value, String positivePrefix) {

        // Characteristics of repr (precision = 17 and go exponential at 16).
        int precision = 17;
        int expThreshold = precision - 1;

        if (signAndSpecialNumber(value, positivePrefix)) {
            // Finish formatting if zero result. (This is a no-op for nan or inf.)
            zeroHelper(precision, expThreshold);

        } else {

            // Generate digit sequence (with no decimal point) with custom rounding.
            FormattingBuffer.StringFormattingBuffer pointlessBuffer = new FormattingBuffer.StringFormattingBuffer(20);
            int exp = reprDigits(Math.abs(value), precision, pointlessBuffer);

            if (-4 <= exp && exp < expThreshold) {
                // Finish the job as f-format with variable-precision p-(exp+1).
                appendFixed(pointlessBuffer, exp, precision);

            } else {
                // Finish the job as e-format.
                appendExponential(pointlessBuffer, exp);
            }
        }
    }

    /**
     * Common code for g-format, none-format and r-format called when the conversion yields "inf",
     * "nan" or zero. The method completes formatting of the zero, with the appropriate number of
     * decimal places or (in particular circumstances) exponential; notation.
     *
     * @param precision of conversion (number of significant digits).
     * @param expThreshold if zero, causes choice of exponential notation for zero.
     */
    private void zeroHelper(int precision, int expThreshold) {

        if (lenMarker == 0) {
            // May be 0 or -0 so we still need to ...
            if (minFracDigits < 0) {
                // In "alternate format", we won't economise trailing zeros.
                appendPointAndTrailingZeros(precision - 1);
            } else if (lenFraction < minFracDigits) {
                // Otherwise, it should be at least the stated minimum length.
                appendTrailingZeros(minFracDigits);
            }

            // And just occasionally (in none-format) we go exponential even when exp = 0...
            if (0 >= expThreshold) {
                appendExponent(0);
            }
        }
    }

    /**
     * Common code for g-format, none-format and r-format used when the exponent is such that a
     * fixed-point presentation is chosen. Normally the method removes trailing digits so as to
     * shorten the presentation without loss of significance. This method respects the minimum
     * number of fractional digits (digits after the decimal point), in member
     * <code>minFracDigits</code>, which is 0 for g-format and 1 for none-format and r-format. When
     * <code>minFracDigits&lt;0</code> this signifies "no truncation" mode, in which trailing zeros
     * generated in the conversion are not removed. This supports "%#g" format.
     *
     * @param digits from converting the value at a given precision.
     * @param exp would be the exponent (in e-format), used to position the decimal point.
     * @param precision of conversion (number of significant digits).
     */

    private void appendFixed(CharSequence digits, int exp, int precision) {

        // Check for "alternate format", where we won't economise trailing zeros.
        boolean noTruncate = (minFracDigits < 0);

        int digitCount = digits.length();

        if (exp < 0) {
            // For a negative exponent, we must insert leading zeros 0.000 ...
            result.append("0.");
            lenWhole = lenPoint = 1;
            for (int i = -1; i > exp; --i) {
                result.append('0');
            }
            // Then the generated digits (always enough to satisfy no-truncate mode).
            result.append(digits);
            lenFraction = digitCount - exp - 1;

        } else {
            // For a non-negative exponent, it's a question of placing the decimal point.
            int w = exp + 1;
            if (w < digitCount) {
                // There are w whole-part digits
                result.append(digits.subSequence(0, w));
                lenWhole = w;
                result.append('.').append(digits.subSequence(w, digitCount));
                lenPoint = 1;
                lenFraction = digitCount - w;
            } else {
                // All the digits are whole-part digits.
                result.append(digits);
                // Just occasionally (in r-format) we need more digits than the precision.
                while (digitCount < w) {
                    result.append('0');
                    digitCount += 1;
                }
                lenWhole = digitCount;
            }
        }
        if (noTruncate) {
            // Extend the fraction as BigDecimal will have economised on zeros.
            appendPointAndTrailingZeros(lenFraction + precision - digitCount);
        }

        // Finally, ensure we have all and only the fractional digits we should.
        if (!noTruncate) {
            if (lenFraction < minFracDigits) {
                // Otherwise, it should be at least the stated minimum length.
                appendTrailingZeros(minFracDigits);
            } else {
                // And no more
                removeTrailingZeros(minFracDigits);
            }
        }
    }

    /**
     * Common code for g-format, none-format and r-format used when the exponent is such that an
     * exponential presentation is chosen. Normally the method removes trailing digits so as to
     * shorten the presentation without loss of significance. Although no minimum number of
     * fractional digits is enforced in the exponential presentation, when
     * <code>minFracDigits&lt;0</code> this signifies "no truncation" mode, in which trailing zeros
     * generated in the conversion are not removed. This supports "%#g" format.
     *
     * @param digits from converting the value at a given precision.
     * @param exp would be the exponent (in e-format), used to position the decimal point.
     */
    private void appendExponential(CharSequence digits, int exp) {

        // The whole-part is the first digit.
        result.append(digits.charAt(0));
        lenWhole = 1;

        // And the rest of the digits form the fractional part
        int digitCount = digits.length();
        result.append('.').append(digits.subSequence(1, digitCount));
        lenPoint = 1;
        lenFraction = digitCount - 1;

        // In no-truncate mode, the fraction is full precision. Otherwise trim it.
        if (minFracDigits >= 0) {
            // Note positive minFracDigits only applies to fixed formats.
            removeTrailingZeros(0);
        }

        // Finally, append the exponent as e+nn.
        appendExponent(exp);
    }

    /**
     * Convert a double to digits and an exponent for use in <code>float.__repr__</code> (or
     * r-format). This method takes advantage of (or assumes) a close correspondence between
     * {@link Double#toString(double)} and Python <code>float.__repr__</code>. The correspondence
     * appears to be exact, insofar as the Java method produces the minimal non-zero digit string.
     * It mostly chooses the same number of digits (and the same digits) as the CPython repr, but in
     * a few cases <code>Double.toString</code> produces more digits. This method truncates to the
     * number <code>maxDigits</code>, which in practice is always 17.
     *
     * @param value to convert
     * @param maxDigits maximum number of digits to return in <code>buf</code>.
     * @param buf for digits of result (recommend size be 20)
     * @return the exponent
     */
    private static int reprDigits(double value, int maxDigits, FormattingBuffer.StringFormattingBuffer buf) {

        // Most of the work is done by Double.
        String s = Double.toString(value);

        // Variables for scanning the string
        int p = 0, end = s.length(), first = 0, point = end, exp;
        char c = 0;
        boolean allZero = true;

        // Scan whole part and fractional part digits
        buf.ensureAdditionalCapacity(s.length()); // this may over-allocate a bit
        while (p < end) {
            c = s.charAt(p++);
            if (Character.isDigit(c)) {
                if (allZero) {
                    if (c != '0') {
                        // This is the first non-zero digit.
                        buf.append(c);
                        allZero = false;
                        // p is one *after* the first non-zero digit.
                        first = p;
                    }
                    // Only seen zeros so far: do nothing.
                } else {
                    // We've started, so every digit counts.
                    buf.append(c);
                }

            } else if (c == '.') {
                // We remember this location (one *after* '.') to calculate the exponent later.
                point = p;

            } else {
                // Something after the mantissa. (c=='E' we hope.)
                break;
            }
        }

        // Possibly followed by an exponent. p has already advanced past the 'E'.
        if (p < end && c == 'E') {
            // If there is an exponent, the mantissa must be in standard form: m.mmmm
            assert point == first + 1;
            exp = Integer.parseInt(s.substring(p));

        } else {
            // Exponent is based on relationship of decimal point and first non-zero digit.
            exp = point - first - 1;
            // But that's only correct when the point is to the right (or absent).
            if (exp < 0) {
                // The point is to the left of the first digit
                exp += 1; // = -(first-point)
            }
        }

        /*
         * XXX This still does not round in all the cases it could. I think Java stops generating
         * digits when the residual is <= ulp/2. This is to neglect the possibility that the extra
         * ulp/2 (before it becomes a different double) could take us to a rounder numeral. To fix
         * this, we could express ulp/2 as digits in the same scale as those in the buffer, and
         * consider adding them. But Java's behaviour here is probably a manifestation of bug
         * JDK-4511638.
         */

        // Sometimes the result is more digits than we want for repr.
        if (buf.length() > maxDigits) {
            // Chop the trailing digits, remembering the most significant lost digit.
            int d = buf.charAt(maxDigits);
            buf.setLength(maxDigits);
            // We round half up. Not absolutely correct since Double has already rounded.
            if (d >= '5') {
                // Treat this as a "carry one" into the numeral buf[0:maxDigits].
                for (p = maxDigits - 1; p >= 0; p--) {
                    // Each pass of the loop does one carry from buf[p+1] to buf[p].
                    d = buf.charAt(p) + 1;
                    if (d <= '9') {
                        // Carry propagation stops here.
                        buf.setCharAt(p, (char) d);
                        break;
                    } else {
                        // 9 + 1 -> 0 carry 1. Keep looping.
                        buf.setCharAt(p, '0');
                    }
                }
                if (p < 0) {
                    /*
                     * We fell off the bottom of the buffer with one carry still to propagate. You
                     * may expect: buf.insert(0, '1') here, but note that every digit in
                     * buf[0:maxDigits] is currently '0', so all we need is:
                     */
                    buf.setCharAt(0, '1');
                    exp += 1;
                }
            }
        }

        return exp;
    }

    /**
     * Append the trailing fractional zeros, as required by certain formats, so that the total
     * number of fractional digits is no less than specified. If <code>n&lt;=0</code>, the method
     * leaves the {@link #result} buffer unchanged.
     *
     * @param n smallest number of fractional digits on return
     */
    private void appendTrailingZeros(int n) {

        int f = lenFraction;

        if (n > f) {
            if (lenPoint == 0) {
                // First need to add a decimal point. (Implies lenFraction=0.)
                result.append('.');
                lenPoint = 1;
            }

            // Now make up the required number of zeros.
            for (; f < n; f++) {
                result.append('0');
            }
            lenFraction = f;
        }
    }

    /**
     * Append the trailing fractional zeros, as required by certain formats, so that the total
     * number of fractional digits is no less than specified. If there is no decimal point
     * originally (and therefore no fractional part), the method will add a decimal point, even if
     * it adds no zeros.
     *
     * @param n smallest number of fractional digits on return
     */
    private void appendPointAndTrailingZeros(int n) {

        if (lenPoint == 0) {
            // First need to add a decimal point. (Implies lenFraction=0.)
            result.append('.');
            lenPoint = 1;
        }

        // Now make up the required number of zeros.
        int f;
        for (f = lenFraction; f < n; f++) {
            result.append('0');
        }
        lenFraction = f;
    }

    /**
     * Remove trailing zeros from the fractional part, as required by certain formats, leaving at
     * least the number of fractional digits specified. If the resultant number of fractional digits
     * is zero, this method will also remove the trailing decimal point (if there is one).
     *
     * @param n smallest number of fractional digits on return
     */
    private void removeTrailingZeros(int n) {

        if (lenPoint > 0) {
            // There's a decimal point at least, and there may be some fractional digits.
            int f = lenFraction;
            if (n == 0 || f > n) {

                int fracStart = result.length() - f;
                for (; f > n; --f) {
                    if (result.charAt(fracStart - 1 + f) != '0') {
                        // Keeping this one as it isn't a zero
                        break;
                    }
                }

                // f is now the number of fractional digits we wish to retain.
                if (f == 0 && lenPoint > 0) {
                    // We will be stripping all the fractional digits. Take the decimal point too.
                    lenPoint = lenFraction = 0;
                    f = -1;
                } else {
                    lenFraction = f;
                }

                // Snip the characters we are going to remove (if any).
                if (fracStart + f < result.length()) {
                    result.setLength(fracStart + f);
                }
            }
        }
    }

    /**
     * Append the current value of {@code exp} in the format <code>"e{:+02d}"</code> (for example
     * <code>e+05</code>, <code>e-10</code>, <code>e+308</code> , etc.).
     *
     * @param expArg exponent value to append
     */
    private void appendExponent(int expArg) {
        int exp = expArg;
        int marker = result.length();
        String e;

        // Deal with sign and leading-zero convention by explicit tests.
        if (exp < 0) {
            e = (exp <= -10) ? "e-" : "e-0";
            exp = -exp;
        } else {
            e = (exp < 10) ? "e+0" : "e+";
        }

        result.append(e).append(exp);
        lenMarker = 1;
        lenExponent = result.length() - marker - 1;
    }

    /**
     * Return the index in {@link #result} of the first letter. This is a helper for
     * {@link #uppercase()} and #getExponent()
     */
    private int indexOfMarker() {
        return start + lenSign + lenWhole + lenPoint + lenFraction;
    }

}
