package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class CSVDialect extends PythonBuiltinObject {
    // TODO: Check accessibility in CPYthon
    private String delimiter;
    private boolean doublequote;
    private String escapechar;
    private String lineterminator;
    private int quoting;
    private String quotechar;
    private boolean skipinitialspace;
    private boolean strict;
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

    public String getDelimiter() {
        return delimiter;
    }

    public boolean isDoublequote() {
        return doublequote;
    }

    public String getEscapechar() {
        return escapechar;
    }

    public String getLineterminator() {
        return lineterminator;
    }

    public String getQuotechar() {
        return quotechar;
    }

    public int getQuoting() {
        return quoting;
    }

    public boolean isSkipinitialspace() {
        return skipinitialspace;
    }

    public boolean isStrict() {
        return strict;
    }

}
