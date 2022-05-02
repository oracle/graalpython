package com.oracle.graal.python.pegparser;

/**
 * Type of input input for the parser
 */
public enum InputType {
    SINGLE,
    FILE,
    EVAL,
    FUNCTION_TYPE,
    FSTRING
}
