package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class CSVDialect extends PythonBuiltinObject {
    String delimiter;           /* field separator */
    boolean doublequote;        /* is " represented by ""? */
    String escapechar;          /* escape character */
    String lineterminator;      /* string to write between records */
    int quoting;                /* style of quoting to write */
    String quotechar;           /* quote character */
    boolean skipinitialspace;   /* ignore spaces following delimiter? */
    boolean strict;             /* raise exception on bad CSV */

    public CSVDialect(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public CSVDialect(Object cls, Shape instanceShape, String delimiter, boolean doublequote, String escapechar,
                      String lineterminator, String quotechar, int quoting, boolean skipinitialspace,
                      boolean strict) {
        super(cls, instanceShape);
        this.delimiter = delimiter;
        this.doublequote = doublequote;
        this.escapechar = escapechar;
        this.lineterminator = lineterminator;
        this.quotechar = quotechar;
        this.quoting = quoting;
        this.skipinitialspace = skipinitialspace;
        this.strict = strict;
    }
}
