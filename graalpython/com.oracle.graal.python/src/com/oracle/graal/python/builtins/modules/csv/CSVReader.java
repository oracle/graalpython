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

import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.AFTER_ESCAPED_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.EAT_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPED_CHAR;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPE_IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_RECORD;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONNUMERIC;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class CSVReader extends PythonBuiltinObject {

    private static final int EOL = -2;
    private static final int NEWLINE_CODEPOINT = "\n".codePointAt(0);
    private static final int CARRIAGE_RETURN_CODEPOINT = "\r".codePointAt(0);
    private static final int SPACE_CODEPOINT = " ".codePointAt(0);

    enum ReaderState {
        START_RECORD,
        START_FIELD,
        ESCAPED_CHAR,
        IN_FIELD,
        IN_QUOTED_FIELD,
        ESCAPE_IN_QUOTED_FIELD,
        QUOTE_IN_QUOTED_FIELD,
        EAT_CRNL,
        AFTER_ESCAPED_CRNL
    }

    Object inputIter; /* iterate over this for input lines */
    CSVDialect dialect; /* parsing dialect */
    ArrayList<Object> fields; /* field list for current record */
    ReaderState state; /* current CSV parse state */
    StringBuilder field; /* temporary buffer */
    int fieldSize; /* size of allocated buffer */
    boolean numericField; /* treat field as numeric */
    int lineNum; /* Source-file line number */

    public CSVReader(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    void parseReset() {
        this.field = new StringBuilder();
        this.fields = new ArrayList<>();
        this.state = START_RECORD;
        this.numericField = false;
    }

    void parseSaveField() {
        Object field = this.field.toString();
        this.field = new StringBuilder();

        if (this.numericField) {
            this.numericField = false;
            field = PyNumberFloatNode.getUncached().execute(field);
        }

        this.fields.add(field);
    }

    @TruffleBoundary
    Object parseIterableInput(Node node) {
        do {
            Object lineObj;
            try {
                lineObj = GetNextNode.getUncached().execute(this.inputIter);
            } catch (PException e) {
                e.expectStopIteration(IsBuiltinClassProfile.getUncached());

                if (this.field.length() != 0 || this.state == IN_QUOTED_FIELD) {
                    if (this.dialect.strict) {
                        throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.UNEXPECTED_END_OF_DATA);
                    } else {
                        try {
                            parseSaveField();
                        } catch (AbstractTruffleException ignored) {
                            throw e.getExceptionForReraise();
                        }
                        break;
                    }
                }
                throw PRaiseNode.getUncached().raiseStopIteration();
            }

            String line;
            try {
                line = CastToJavaStringNode.getUncached().execute(lineObj);
            } catch (CannotCastException e) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.WRONG_ITERATOR_RETURN_TYPE, GetClassNode.getUncached().execute(lineObj));
            }

            // TODO: Implement PyUnicode_Check Node? => how do we handle the possibility of bytes?
            // PyPy: if isinstance(line, str) and '\0' in line or isinstance(line, bytes) and
            // line.index(0) >=0:
            // raise Error("line contains NULL byte")
            if (line.contains("\u0000")) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.LINE_CONTAINS_NULL_BYTE);
            }

            this.lineNum++;
            this.parseLine(node, line);

        } while (this.state != START_RECORD);

        ArrayList<Object> fields = this.fields;
        this.fields = null;

        return PythonObjectFactory.getUncached().createList(fields.toArray());
    }

    void parseLine(Node node, String line) {
        final int lineLength = line.length();

        /*
         * Python supports utf-32 characters, as Java characters are utf-16 only, we have to work
         * with code points instead.
         */
        for (int offset = 0; offset < lineLength;) {
            final int codepoint = line.codePointAt(offset);

            parseProcessCodePoint(node, codepoint);

            offset += Character.charCount(codepoint);
        }

        parseProcessCodePoint(node, EOL);
    }

    @SuppressWarnings("fallthrough")
    void parseProcessCodePoint(Node node, int codePoint) {
        CSVDialect dialect = this.dialect;

        switch (this.state) {

            case START_RECORD:
                /* start of record */
                if (codePoint == EOL) {
                    /* empty line - return [] */
                    break;
                } else if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                    this.state = EAT_CRNL;
                    break;
                }
                /* normal character - handle as START_FIELD */
                this.state = START_FIELD;
                /* fallthru */

            case START_FIELD:
                /* expecting field */
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                    /* save empty field - return [fields] */
                    parseSaveField();
                    this.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                } else if (codePoint == dialect.quoteCharCodePoint &&
                                dialect.quoting != QUOTE_NONE) {
                    /* start quoted field */
                    this.state = IN_QUOTED_FIELD;
                } else if (codePoint == dialect.escapeCharCodePoint) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                } else if (codePoint == SPACE_CODEPOINT && dialect.skipInitialSpace) {
                    /* ignore space at start of field */
                } else if (codePoint == dialect.delimiterCodePoint) {
                    /* save empty field */
                    parseSaveField();
                } else {
                    /* begin new unquoted field */
                    if (dialect.quoting == QUOTE_NONNUMERIC) {
                        this.numericField = true;
                    }
                    parseAddCodePoint(node, codePoint);
                    this.state = IN_FIELD;
                }
                break;

            case ESCAPED_CHAR:
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                    parseAddCodePoint(node, codePoint);
                    this.state = AFTER_ESCAPED_CRNL;
                    break;
                }
                if (codePoint == EOL) {
                    codePoint = NEWLINE_CODEPOINT;
                }
                parseAddCodePoint(node, codePoint);

                this.state = IN_FIELD;
                break;

            case AFTER_ESCAPED_CRNL:
                if (codePoint == EOL) {
                    break;
                }
                /* fallthru */

            case IN_FIELD:
                /* in unquoted field */
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();

                    this.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                } else if (codePoint == dialect.escapeCharCodePoint) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                } else if (codePoint == dialect.delimiterCodePoint) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                } else {
                    /* normal character - save in field */
                    parseAddCodePoint(node, codePoint);
                }
                break;

            case IN_QUOTED_FIELD:
                /* in quoted field */
                if (codePoint == EOL) {
                    /* ignore */
                } else if (codePoint == dialect.escapeCharCodePoint) {
                    /* Possible escape character */
                    this.state = ESCAPE_IN_QUOTED_FIELD;
                } else if (codePoint == dialect.quoteCharCodePoint &&
                                dialect.quoting != QUOTE_NONE) {
                    if (dialect.doubleQuote) {
                        /* doublequote; " represented by "" */
                        this.state = ReaderState.QUOTE_IN_QUOTED_FIELD;
                    } else {
                        /* end of quote part of field */
                        this.state = IN_FIELD;
                    }
                } else {
                    /* normal character - save in field */
                    parseAddCodePoint(node, codePoint);
                }
                break;

            case ESCAPE_IN_QUOTED_FIELD:
                if (codePoint == EOL) {
                    codePoint = NEWLINE_CODEPOINT;
                }
                parseAddCodePoint(node, codePoint);
                this.state = IN_QUOTED_FIELD;
                break;

            case QUOTE_IN_QUOTED_FIELD:
                /* doublequote - seen a quote in a quoted field */
                if (dialect.quoting != QUOTE_NONE &&
                                codePoint == dialect.quoteCharCodePoint) {
                    /* save "" as " */
                    parseAddCodePoint(node, codePoint);
                    this.state = IN_QUOTED_FIELD;
                } else if (codePoint == dialect.delimiterCodePoint) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                } else if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();
                    this.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                } else if (!dialect.strict) {
                    parseAddCodePoint(node, codePoint);
                    this.state = IN_FIELD;
                } else {
                    /* illegal */
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.S_EXPECTED_AFTER_S,
                                    dialect.delimiter,
                                    dialect.quoteChar);
                }
                break;

            case EAT_CRNL:
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                    /* ignore */
                } else if (codePoint == EOL) {
                    this.state = START_RECORD;
                } else {
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.NEWLINE_IN_UNQOUTED_FIELD);
                }
                break;
        }

    }

    void parseAddCodePoint(Node node, int codePoint) {

        CSVModuleBuiltins csvModuleBuiltins = (CSVModuleBuiltins) PythonContext.get(node).lookupBuiltinModule("_csv").getBuiltins();

        if (this.field.length() + Character.charCount(codePoint) > csvModuleBuiltins.fieldLimit) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.LARGER_THAN_FIELD_SIZE_LIMIT,
                            csvModuleBuiltins.fieldLimit);
        }

        this.field.appendCodePoint(codePoint);
    }
}
