package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.Arrays;

public enum QuoteStyle {
    QUOTE_MINIMAL,
    QUOTE_ALL,
    QUOTE_NONNUMERIC,
    QUOTE_NONE;

    @CompilationFinal(dimensions=1)
    private static final QuoteStyle[] VALUES = Arrays.copyOf(values(), values().length);

    public static QuoteStyle getQuoteStyle(int ordinal) {
        return VALUES[ordinal];
    }

    public static boolean containsOrdinalValue(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length;
    }
}
