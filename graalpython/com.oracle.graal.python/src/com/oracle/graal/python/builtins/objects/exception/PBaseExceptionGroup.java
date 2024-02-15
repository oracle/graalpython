package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PBaseExceptionGroup extends PBaseException {
    private final TruffleString message;
    private final Object[] exceptions;

    public PBaseExceptionGroup(Object cls, Shape instanceShape, TruffleString message, Object[] exceptions, PTuple args) {
        super(cls, instanceShape, null, args);
        this.message = message;
        this.exceptions = exceptions;
    }

    public Object[] getExceptions() {
        return exceptions;
    }

    public TruffleString getMessage() {
        return message;
    }
}
