package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.object.Shape;

import java.util.ArrayList;

import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_NONNUMERIC;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.AFTER_ESCAPED_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.EAT_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPED_CHAR;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPE_IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_RECORD;

public final class CSVReader extends PythonBuiltinObject {

    private static final String EOL = "EOL"; //TODO: How to share across package? Constants Class

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
    String field; /* temporary buffer */
    int fieldSize; /* size of allocated buffer */
    int fieldLen;  /* length of current field */
    boolean numericField; /* treat field as numeric */
    int lineNum; /* Source-file line number */

    public CSVReader(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }


     void parseReset() {
        this.field = "";
        this.fields = new ArrayList<>();
        this.state = START_RECORD;
        this.numericField = false;
    }

    void parseSaveField() {
        Object field = this.field;
        this.field = "";
        this.fieldLen = 0;

        if (this.numericField) {
            this.numericField = false;
            field = PyNumberFloatNode.getUncached().execute(field);
        }

        this.fields.add(field);
    }

    void parseLine(String line) {
        int lineLength = line.length();

        for (int i = 0; i < lineLength; i++) {
            String c = line.substring(i, i+1);
            parseProcessChar(c);
        }

        parseProcessChar(EOL);
    }

    void parseProcessChar(String c) {
        CSVDialect dialect = this.dialect;

        switch (this.state) {

            case START_RECORD:
                /* start of record */
                if (c == EOL) {
                    /* empty line - return [] */
                    break;
                } else if (c.equals("\n") || c.equals("\r")) {
                    this.state = EAT_CRNL;
                    break;
                }
                /* normal character - handle as START_FIELD */
                this.state = START_FIELD;
                /* fallthru */

            case START_FIELD:
                /* expecting field */
                if (c.equals("\n") || c.equals("\r") || c == EOL) {
                    /* save empty field - return [fields] */
                    parseSaveField();
                    this.state = (c == EOL) ? START_RECORD : EAT_CRNL;
                }
                else if (c.equals(dialect.quotechar) &&
                        dialect.quoting != QUOTE_NONE) {
                    /* start quoted field */
                    this.state = IN_QUOTED_FIELD;
                }
                else if (c.equals(dialect.escapechar)) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else if (c.equals(" ") && dialect.skipinitialspace)
                    /* ignore space at start of field */
                    ;
                else if (c.equals(dialect.delimiter)) {
                    /* save empty field */
                    parseSaveField();
                }
                else {
                    /* begin new unquoted field */
                    if (dialect.quoting == QUOTE_NONNUMERIC)
                        this.numericField = true;
                    parseAddChar(c);
                    this.state = IN_FIELD;
                }
                break;

            case ESCAPED_CHAR:
                if (c.equals("\n") || c.equals("\r")) {
                    parseAddChar(c);
                    this.state = AFTER_ESCAPED_CRNL;
                    break;
                }
                if (c == EOL) {
                    c = "\n";
                }
                parseAddChar(c);

                this.state = IN_FIELD;
                break;

            case AFTER_ESCAPED_CRNL:
                if (c == EOL)
                    break;
                /*fallthru*/

            case IN_FIELD:
                /* in unquoted field */
                if (c.equals("\n") || c.equals("\r") || c == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();

                    this.state = (c == EOL) ? START_RECORD : EAT_CRNL;
                }
                else if (c.equals(dialect.escapechar)) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else if (c.equals(dialect.delimiter)) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                }
                else {
                    /* normal character - save in field */
                    parseAddChar(c);
                }
                break;

            case IN_QUOTED_FIELD:
                /* in quoted field */
                if (c == EOL)
                    ;
                else if (c.equals(dialect.escapechar)) {
                    /* Possible escape character */
                    this.state = ESCAPE_IN_QUOTED_FIELD;
                }
                else if (c.equals(dialect.quotechar) &&
                        dialect.quoting != QUOTE_NONE) {
                    if (dialect.doublequote) {
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
                    parseAddChar(c);
                }
                break;

            case ESCAPE_IN_QUOTED_FIELD:
                if (c == EOL)
                    c = "\n";
                parseAddChar(c);
                this.state = IN_QUOTED_FIELD;
                break;

            case QUOTE_IN_QUOTED_FIELD:
                /* doublequote - seen a quote in a quoted field */
                if (dialect.quoting != QUOTE_NONE &&
                        c.equals(dialect.quotechar)) {
                    /* save "" as " */
                    parseAddChar(c);
                    this.state = IN_QUOTED_FIELD;
                }
                else if (c.equals(dialect.delimiter)) {
                    /* save field - wait for new field */
                    parseSaveField();
                    this.state = START_FIELD;
                }
                else if (c.equals("\n") || c.equals("\r") || c == EOL) {
                    /* end of line - return [fields] */
                    parseSaveField();
                    this.state = (c == EOL) ? START_RECORD : EAT_CRNL;
                }
                else if (!dialect.strict) {
                    parseAddChar(c);
                    this.state = IN_FIELD;
                }
                else {
                    /* illegal */
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.S_EXPECTED_AFTER_S,
                            dialect.delimiter,
                            dialect.quotechar);
                }
                break;

            case EAT_CRNL:
                if (c.equals("\n") || c.equals("\r"))
                    ;
                else if (c == EOL)
                    this.state = START_RECORD;
                else {
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.NEWLINE_IN_UNQOUTED_FIELD);
                }
                break;
        }

    }

    void parseAddChar(String c){

        if (this.field.length() + c.length() > CSVModuleBuiltins.fieldLimit){
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.LARGER_THAN_FIELD_SIZE_LIMIT,
                    CSVModuleBuiltins.fieldLimit);
        }

        this.fieldLen++;
        this.field = this.field.concat(c);

    }
}
