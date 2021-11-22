package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.object.Shape;

import java.util.ArrayList;

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

public final class CSVReader extends PythonBuiltinObject {

    private static final int EOL = -2;
    private static final int NEWLINE_CODEPOINT = "\n".codePointAt(0);
    private static final int CARRIAGE_RETURN_CODEPOINT = "\r".codePointAt(0);
    private static final int SPACE_CODEPOINT = " ".codePointAt(0);

    public enum ReaderState {
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
    CSVDialect dialect;  /* parsing dialect */
    ArrayList<Object> fields; /* field list for current record */
    ReaderState state;  /* current CSV parse state */
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

    void parseLine(String line) {
        final int lineLength = line.length();

        /* Python supports utf-32 characters, as Java characters are utf-16 only,
        * we have to work with code points instead. */
        for (int offset = 0; offset < lineLength; ) {
            final int codepoint = line.codePointAt(offset);

            parseProcessCodePoint(codepoint);

            offset += Character.charCount(codepoint);
        }

        parseProcessCodePoint(EOL);
    }

    @SuppressWarnings("fallthrough")
    void parseProcessCodePoint(int codePoint) {
        CSVDialect dialect = this.dialect;

        switch (this.state) {

            case START_RECORD:
                /* start of record */
                if (codePoint== EOL) {
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
                }
                else if (codePoint == dialect.quoteCharCodePoint &&
                        dialect.quoting != QUOTE_NONE) {
                    /* start quoted field */
                    this.state = IN_QUOTED_FIELD;
                }
                else if (codePoint == dialect.escapeCharCodePoint) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else if (codePoint == SPACE_CODEPOINT && dialect.skipInitialSpace)
                    /* ignore space at start of field */
                    ;
                else if (codePoint == dialect.delimiterCodePoint) {
                    /* save empty field */
                    parseSaveField();
                }
                else {
                    /* begin new unquoted field */
                    if (dialect.quoting == QUOTE_NONNUMERIC)
                        this.numericField = true;
                    parseAddCodePoint(codePoint);
                    this.state = IN_FIELD;
                }
                break;

            case ESCAPED_CHAR:
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                    parseAddCodePoint(codePoint);
                    this.state = AFTER_ESCAPED_CRNL;
                    break;
                }
                if (codePoint == EOL) {
                    codePoint = NEWLINE_CODEPOINT;
                }
                parseAddCodePoint(codePoint);

                this.state = IN_FIELD;
                break;

            case AFTER_ESCAPED_CRNL:
                if (codePoint == EOL)
                    break;
                /*fallthru*/

            case IN_FIELD:
                /* in unquoted field */
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();

                    this.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                }
                else if (codePoint == dialect.escapeCharCodePoint) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else if (codePoint == dialect.delimiterCodePoint) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                }
                else {
                    /* normal character - save in field */
                    parseAddCodePoint(codePoint);
                }
                break;

            case IN_QUOTED_FIELD:
                /* in quoted field */
                if (codePoint == EOL)
                    ;
                else if (codePoint == dialect.escapeCharCodePoint) {
                    /* Possible escape character */
                    this.state = ESCAPE_IN_QUOTED_FIELD;
                }
                else if (codePoint == dialect.quoteCharCodePoint &&
                        dialect.quoting != QUOTE_NONE) {
                    if (dialect.doubleQuote) {
                        /* doublequote; " represented by "" */
                        this.state = ReaderState.QUOTE_IN_QUOTED_FIELD;
                    }
                    else {
                        /* end of quote part of field */
                        this.state = IN_FIELD;
                    }
                }
                else {
                    /* normal character - save in field */
                    parseAddCodePoint(codePoint);
                }
                break;

            case ESCAPE_IN_QUOTED_FIELD:
                if (codePoint == EOL)
                    codePoint = NEWLINE_CODEPOINT;
                parseAddCodePoint(codePoint);
                this.state = IN_QUOTED_FIELD;
                break;

            case QUOTE_IN_QUOTED_FIELD:
                /* doublequote - seen a quote in a quoted field */
                if (dialect.quoting != QUOTE_NONE &&
                        codePoint == dialect.quoteCharCodePoint) {
                    /* save "" as " */
                    parseAddCodePoint(codePoint);
                    this.state = IN_QUOTED_FIELD;
                }
                else if (codePoint == dialect.delimiterCodePoint) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                }
                else if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();
                    this.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                }
                else if (!dialect.strict) {
                    parseAddCodePoint(codePoint);
                    this.state = IN_FIELD;
                }
                else {
                    /* illegal */
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.S_EXPECTED_AFTER_S,
                            dialect.delimiter,
                            dialect.quoteChar);
                }
                break;

            case EAT_CRNL:
                if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT)
                    ;
                else if (codePoint == EOL)
                    this.state = START_RECORD;
                else {
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.NEWLINE_IN_UNQOUTED_FIELD);
                }
                break;
        }

    }

    void parseAddCodePoint(int codePoint){

        if (this.field.length() + Character.charCount(codePoint) > CSVModuleBuiltins.fieldLimit){
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.LARGER_THAN_FIELD_SIZE_LIMIT,
                    CSVModuleBuiltins.fieldLimit);
        }

        this.field.appendCodePoint(codePoint);
    }
}
