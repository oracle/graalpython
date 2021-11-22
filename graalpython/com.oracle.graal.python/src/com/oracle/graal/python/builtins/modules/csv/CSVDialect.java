package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET;
import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET_CODEPOINT;

public final class CSVDialect extends PythonBuiltinObject {
    String delimiter;           /* field separator */
    boolean doubleQuote;        /* is " represented by ""? */
    String escapeChar;          /* escape character */
    String lineTerminator;      /* string to write between records */
    QuoteStyle quoting;         /* style of quoting to write */
    String quoteChar;           /* quote character */
    boolean skipInitialSpace;   /* ignore spaces following delimiter? */
    boolean strict;             /* raise exception on bad CSV */

    int delimiterCodePoint;     /* code point representation for handling utf-32 delimiters */
    int escapeCharCodePoint;    /* code point representation for handling utf-32 escape chars */
    int quoteCharCodePoint;     /* code point representation for handling utf-32 quote chars */

    public CSVDialect(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public CSVDialect(Object cls, Shape instanceShape, String delimiter, boolean doubleQuote, String escapeChar,
                      String lineTerminator, String quoteChar, QuoteStyle quoting, boolean skipInitialSpace,
                      boolean strict) {
        super(cls, instanceShape);
        this.delimiter = delimiter;
        this.doubleQuote = doubleQuote;
        this.escapeChar = escapeChar;
        this.lineTerminator = lineTerminator;
        this.quoteChar = quoteChar;
        this.quoting = quoting;
        this.skipInitialSpace = skipInitialSpace;
        this.strict = strict;

        this.delimiterCodePoint = this.delimiter == NOT_SET ? NOT_SET_CODEPOINT : this.delimiter.codePointAt(0);
        this.escapeCharCodePoint = this.escapeChar == NOT_SET ? NOT_SET_CODEPOINT : this.escapeChar.codePointAt(0);
        this.quoteCharCodePoint = quoteChar.codePointAt(0); // quote char cannot be NOT_SET
    }
}
