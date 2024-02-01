package com.oracle.graal.python.builtins.objects.types;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PGenericAliasIterator extends PythonBuiltinObject {
    private final PGenericAlias obj;
    private boolean exhausted;

    public PGenericAliasIterator(Object cls, Shape instanceShape, PGenericAlias obj) {
        super(cls, instanceShape);
        this.obj = obj;
    }

    public PGenericAlias getObj() {
        return obj;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public void markExhausted() {
        exhausted = true;
    }
}
