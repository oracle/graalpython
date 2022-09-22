package com.oracle.graal.python.builtins.objects.types;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.truffle.api.object.Shape;

public class PUnionType extends PythonBuiltinObject {
    private Object origin;
    private PTuple args;

    public PUnionType(Object cls, Shape instanceShape, Object origin, PTuple args) {
        super(cls, instanceShape);
        this.origin = origin;
        this.args = args;
    }

    public Object getOrigin() {
        return origin;
    }

    public PTuple getArgs() {
        return args;
    }
}
