/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.csv;

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class CSVWriter extends PythonBuiltinObject {

    Object write; /* write output lines to this file */
    CSVDialect dialect; /* parsing dialect */
    StringBuilder rec; /* buffer for parser.join */
    int recSize; /* size of allocated record */
    int numFields; /* number of fields in record */

    public CSVWriter(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    void joinReset() {
        this.rec = new StringBuilder();
        this.numFields = 0;
    }

    @TruffleBoundary
    void joinFields(Node node, Object iter) {
        Object field;

        this.joinReset();

        while (true) {
            try {
                field = GetNextNode.getUncached().execute(iter);
                this.joinField(field);
            } catch (PException e) {
                e.expectStopIteration(IsBuiltinClassProfile.getUncached());
                break;
            }
        }

        if (this.numFields > 0 && this.rec.length() == 0) {
            if (this.dialect.quoting == QUOTE_NONE) {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.CSVError, ErrorMessages.EMPTY_FIELD_RECORD_MUST_BE_QUOTED);
            }
            this.numFields--;
            this.joinAppend(null, true);
        }

        this.joinAppendLineterminator();
    }

    void joinField(Object field) {
        boolean quoted;

        switch (this.dialect.quoting) {
            case QUOTE_NONNUMERIC:
                quoted = !PyNumberCheckNode.getUncached().execute(field);
                break;
            case QUOTE_ALL:
                quoted = true;
                break;
            default:
                quoted = false;
                break;
        }

        if (field == PNone.NONE) {
            this.joinAppend(null, quoted);
        } else {
            String str = PyObjectStrAsJavaStringNode.getUncached().execute(field);
            this.joinAppend(str, quoted);
        }
    }

    boolean needsQuotes(String field) {
        if (field == null) {
            return false;
        }

        boolean needsQuotes = false;
        final int strLen = field.length();

        for (int offset = 0; offset < strLen;) {
            boolean wantEscape = false;

            final int c = field.codePointAt(offset);
            if (c == dialect.delimiterCodePoint ||
                            c == dialect.escapeCharCodePoint ||
                            c == dialect.quoteCharCodePoint ||
                            containsCodePoint(dialect.lineTerminatorCodePoints, c)) {

                if (dialect.quoting == QUOTE_NONE ||
                                c == dialect.quoteCharCodePoint && !dialect.doubleQuote ||
                                c == dialect.escapeCharCodePoint) {
                    wantEscape = true;
                }

                if (!wantEscape) {
                    needsQuotes = true;
                    break;
                }
            }
            offset += Character.charCount(c);
        }

        return needsQuotes;
    }

    void joinAppend(String field, boolean quoted) {
        CSVDialect dialect = this.dialect;

        /*
         * If we don't already know that the field must be quoted due to dialect settings, check if
         * the field contains characters due to which it must be quoted.
         */
        if (!quoted) {
            quoted = needsQuotes(field);
        }

        /* If this is not the first field we need a field separator */
        if (this.numFields > 0) {
            this.rec.append(dialect.delimiter);
        }

        /* Handle preceding quote */
        if (quoted) {
            this.rec.append(dialect.quoteChar);
        }

        /* Copy field data and add escape chars as needed */
        /* If field is null just pass over */
        if (field != null) {
            int strLen = field.length();

            /*
             * Python supports utf-32 characters, as Java characters are utf-16 only, we have to
             * work with code points instead.
             */
            for (int offset = 0; offset < strLen;) {

                boolean wantEscape = false;

                final int c = field.codePointAt(offset);

                if (c == dialect.delimiterCodePoint ||
                                c == dialect.escapeCharCodePoint ||
                                c == dialect.quoteCharCodePoint ||
                                containsCodePoint(dialect.lineTerminatorCodePoints, c)) {

                    if (dialect.quoting == QUOTE_NONE) {
                        wantEscape = true;
                    } else {
                        if (c == dialect.quoteCharCodePoint) {
                            if (dialect.doubleQuote) {
                                this.rec.append(dialect.quoteChar);
                            } else {
                                wantEscape = true;
                            }
                        } else if (c == dialect.escapeCharCodePoint) {
                            wantEscape = true;
                        }
                        if (!wantEscape) {
                            quoted = true;
                        }
                    }
                    if (wantEscape) {
                        if (dialect.escapeChar.equals(NOT_SET)) {
                            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.ESCAPE_WITHOUT_ESCAPECHAR);
                        }
                        this.rec.append(dialect.escapeChar);
                    }
                }
                this.rec.appendCodePoint(c);
                offset += Character.charCount(c);
            }
        }
        if (quoted) {
            this.rec.append(dialect.quoteChar);
        }
        this.numFields++;
    }

    void joinAppendLineterminator() {
        this.rec.append(this.dialect.lineTerminator);
    }

    private boolean containsCodePoint(int[] codePoints, int codePoint) {
        for (int cp : codePoints) {
            if (cp == codePoint) {
                return true;
            }
        }
        return false;
    }

}
