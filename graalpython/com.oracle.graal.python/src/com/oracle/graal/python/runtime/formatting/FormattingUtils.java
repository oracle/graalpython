/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Formatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;

public final class FormattingUtils {
    private FormattingUtils() {
    }

    public static Spec validateAndPrepareForFloat(Spec spec, PythonCore core, String forType) {
        switch (spec.type) {
            case InternalFormat.Spec.NONE:
            case 'n':
            case 'e':
            case 'f':
            case 'g':
            case 'E':
            case 'F':
            case 'G':
            case '%':
                // Check for disallowed parts of the specification
                if (spec.alternate) {
                    throw alternateFormNotAllowed(core, forType);
                }
                // spec may be incomplete. The defaults are those commonly used for numeric
                // formats.
                return spec.withDefaults(InternalFormat.Spec.NUMERIC);
            default:
                throw Formatter.unknownFormat(core, spec.type, forType);
        }
    }

    /**
     * Convenience method returning a {ValueError} reporting that alternate form is not allowed in a
     * format specifier for the named type.
     *
     * @param forType the type it was found applied to
     * @return exception to throw
     */
    public static PException alternateFormNotAllowed(ParserErrorCallback errors, String forType) {
        return alternateFormNotAllowed(errors, forType, '\0');
    }

    /**
     * Convenience method returning a {ValueError} reporting that alternate form is not allowed in a
     * format specifier for the named type and specified typoe code.
     *
     * @param forType the type it was found applied to
     * @param code the formatting code (or '\0' not to mention one)
     * @return exception to throw
     */
    public static PException alternateFormNotAllowed(ParserErrorCallback errors, String forType, char code) {
        return notAllowed(errors, "Alternate form (#)", forType, code);
    }

    /**
     * Convenience method returning a {ValueError} reporting that some format specifier feature is
     * not allowed for the named format code and data type. Produces a message like:
     * <p>
     * <code>outrage+" not allowed with "+forType+" format specifier '"+code+"'"</code>
     * <p>
     * <code>outrage+" not allowed in "+forType+" format specifier"</code>
     *
     * @param outrage committed in the present case
     * @param forType the data type (e.g. "integer") it where it is an outrage
     * @param code the formatting code for which it is an outrage (or '\0' not to mention one)
     * @return exception to throw
     */
    public static PException notAllowed(ParserErrorCallback errors, String outrage, String forType, char code) {
        // Try really hard to be like CPython
        String codeAsString, withOrIn;
        if (code == 0) {
            withOrIn = "in ";
            codeAsString = "";
        } else {
            withOrIn = "with ";
            codeAsString = " '" + code + "'";
        }
        throw errors.raise(ValueError, ErrorMessages.NOT_ALLOWED_S_S_FORMAT_SPECIFIERS_S, outrage, withOrIn, forType, codeAsString);
    }
}
