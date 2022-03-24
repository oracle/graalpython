package com.oracle.graal.python.compiler;

import com.oracle.graal.python.annotations.GenerateEnumConstants;

@GenerateEnumConstants
public enum UnaryOps {
    NOT,
    POSITIVE,
    NEGATIVE,
    INVERT,
}
