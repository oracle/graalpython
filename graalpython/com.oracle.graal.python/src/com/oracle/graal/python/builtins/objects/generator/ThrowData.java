package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.builtins.objects.exception.PBaseException;

public final class ThrowData {
    public final PBaseException pythonException;
    public final boolean withJavaStacktrace;

    public ThrowData(PBaseException pythonException, boolean withJavaStacktrace) {
        this.pythonException = pythonException;
        this.withJavaStacktrace = withJavaStacktrace;
    }
}
