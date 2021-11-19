package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.object.Shape;

import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.NOT_SET;
import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_ALL;
import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_NONNUMERIC;

public final class CSVWriter extends PythonBuiltinObject {

    Object write;        /* write output lines to this file */
    CSVDialect dialect;  /* parsing dialect */
    StringBuilder rec;   /* buffer for parser.join */
    int recSize;         /* size of allocated record */
    int recLen;          /* length of record */
    int numFields;       /* number of fields in record */

    public CSVWriter(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    void joinReset() {
        this.rec = new StringBuilder();
        this.recLen = 0;
        this.numFields = 0;
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

    boolean needsQuotes(Object field) {
        boolean needsQuotes = false;
        if (field != null) {
            String fieldStr = field.toString();
            int strLen = fieldStr.length();
            for (int i = 0; i < strLen; i++) {
                boolean wantEscape = false;

                String c = fieldStr.substring(i, i + 1);
                if (c.equals(dialect.delimiter) ||
                        c.equals(dialect.escapeChar) ||
                        c.equals(dialect.quoteChar) ||
                        dialect.lineTerminator.contains(c)) {

                    if (c.equals(dialect.quoteChar)) {
                        if (!dialect.doubleQuote) {
                            wantEscape = true;
                        }
                    } else if (c.equals(dialect.escapeChar)) {
                        wantEscape = true;
                    }
                    if (!wantEscape) {
                        needsQuotes = true;
                    }
                }
            }
        }
        return needsQuotes;
    }

    void joinAppend(Object field, boolean quoted) {
        CSVDialect dialect = this.dialect;

        /* If we don't already know that the field must be quoted due to
           dialect settings, check if the field contains characters due
           to which it must be quoted.
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
            String fieldStr = field.toString();
            int strLen = fieldStr.length();
            for (int i = 0; i < strLen; i++) {

                boolean wantEscape = false;

                String c = fieldStr.substring(i, i + 1);
                if (c.equals(dialect.delimiter) ||
                        c.equals(dialect.escapeChar) ||
                        c.equals(dialect.quoteChar) ||
                        dialect.lineTerminator.contains(c)) {

                    if (dialect.quoting == QUOTE_NONE) {
                        wantEscape = true;
                    } else {
                        if (c.equals(dialect.quoteChar)) {
                            if (dialect.doubleQuote) {
                                this.rec.append(dialect.quoteChar);
                            } else {
                                wantEscape = true;
                            }
                        } else if (c.equals(dialect.escapeChar)) {
                            wantEscape = true;
                        }
                        if (!wantEscape) {
                            quoted = true;
                        }
                    }
                    if (wantEscape) {
                        if (dialect.escapeChar == NOT_SET) {
                            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.CSVError, ErrorMessages.ESCAPE_WITHOUT_ESCAPECHAR);
                        }
                        this.rec.append(dialect.escapeChar);
                    }
                }
                this.rec.append(c);
            }
        }
        if (quoted) {
            this.rec.append(dialect.quoteChar);
        }
        this.numFields++;
    }

    void joinAppendLineterminator() {
        int terminatorLen = this.dialect.lineTerminator.length();
        this.rec.append(this.dialect.lineTerminator);

        this.recLen += terminatorLen;
    }

}
