package com.oracle.graal.python.builtins.objects.partial;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PPartial extends PythonBuiltinObject {
    private final Object fn;
    private final Object args;
    private final Object kw;

    public PPartial(Object cls, Shape instanceShape, Object fn, Object args, Object kw) {
        super(cls, instanceShape);
        this.fn = fn;
        this.args = args;
        this.kw = kw;
    }

    public Object getFn() {
        return fn;
    }

    public Object getArgs() {
        return args;
    }

    public Object getKw() {
        return kw;
    }
}
