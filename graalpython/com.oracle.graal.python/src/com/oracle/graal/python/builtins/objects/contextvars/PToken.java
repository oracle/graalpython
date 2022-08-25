package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PToken extends PythonBuiltinObject {
    public static final Object MISSING = new Object();
    private final PContextVar var;
    private final Object oldValue;

    public PToken(PContextVar var, Object oldValue, Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.var = var;
        this.oldValue = oldValue;
    }

    public PContextVar getVar() {
        return var;
    }

    public Object getOldValue() {
        return oldValue;
    }
}
