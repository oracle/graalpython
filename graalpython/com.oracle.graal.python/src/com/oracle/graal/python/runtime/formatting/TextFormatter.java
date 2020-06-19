/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;

/**
 * A class that provides the implementation of <code>str</code> and <code>unicode</code> formatting.
 * In a limited way, it acts like a StringFormattingBuffer to which text, formatted according to the
 * format specifier supplied at construction. These are ephemeral objects that are not, on their
 * own, thread safe.
 */
public class TextFormatter extends InternalFormat.Formatter {

    /**
     * Construct the formatter from a client-supplied buffer, to which the result will be appended,
     * and a specification. Sets {@link #mark} to the end of the buffer.
     *
     * @param result destination buffer
     * @param spec parsed conversion specification
     */
    public TextFormatter(PythonCore core, FormattingBuffer result, Spec spec) {
        super(core, result, spec);
    }

    /*
     * Re-implement the text appends so they return the right type.
     */
    @Override
    public TextFormatter append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public TextFormatter append(CharSequence csq) {
        super.append(csq);
        return this;
    }

    @Override
    public TextFormatter append(CharSequence csq, int begin, int end) //
                    throws IndexOutOfBoundsException {
        super.append(csq, begin, end);
        return this;
    }

    /**
     * Format the given <code>String</code> into the <code>result</code> buffer. Largely, this is a
     * matter of copying the value of the argument, but a subtlety arises when the string contains
     * supplementary (non-BMP) Unicode characters, which are represented as surrogate pairs. The
     * precision specified in the format relates to a count of Unicode characters (code points), not
     * Java <code>char</code>s. The method deals with this correctly, essentially by not counting
     * the high-surrogates in the allowance. The final value of {@link #lenWhole} counts the UTF-16
     * units added.
     *
     * @param value to format
     * @return this <code>TextFormatter</code> object
     */
    public TextFormatter format(CharSequence value) {

        // Scratch all instance variables and start = result.length().
        setStart();

        int p = spec.precision, n = value.length();

        if (Spec.specified(p) && p < n) {
            // Estimate the space for the converted result (preempt multiple re-allocation)
            int space = Math.max(spec.width, p);
            result.ensureAdditionalCapacity(space + (bytes ? 0 : space / 4));
            /*
             * A precision p was specified less than the length: we may have to truncate. Note we
             * compared p with the UTF-16 length, even though it is the code point length that
             * matters. But the code point length cannot be greater than n.
             */
            int count = 0;
            while (count < p) {
                // count is the number of UTF-16 chars.
                char c = value.charAt(count++);
                result.append(c);
                // A high-surrogate will always be followed by a low, so doesn't count.
                if (Character.isHighSurrogate(c) && p < n) {
                    // Accomplish "not counting" by bumping the limit p, within the array bounds.
                    p += 1;
                }
            }
            // Record the UTF-16 count as the length in buffer
            lenWhole = count;

        } else {
            // We definitely don't need to truncate. Append the whole string.
            lenWhole = n;
            result.append(value);
        }

        return this;
    }

    // Variant to deal with supplementary characters: other formatters don't produce them.
    @Override
    public TextFormatter pad() {
        // We'll need this many pad characters (if>0). Note Spec.UNDEFINED<0.
        int n = spec.width - result.codePointCount(mark, result.length());
        if (n > 0) {
            pad(mark, n);
        }
        return this;
    }

}
