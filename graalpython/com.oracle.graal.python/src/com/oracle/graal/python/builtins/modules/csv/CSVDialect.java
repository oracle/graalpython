package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class CSVDialect extends PythonBuiltinObject {
    String delimiter;           /* field separator */
    boolean doubleQuote;        /* is " represented by ""? */
    String escapeChar;          /* escape character */
    String lineTerminator;      /* string to write between records */
    QuoteStyle quoting;         /* style of quoting to write */
    String quoteChar;           /* quote character */
    boolean skipInitialSpace;   /* ignore spaces following delimiter? */
    boolean strict;             /* raise exception on bad CSV */

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
    }
}
