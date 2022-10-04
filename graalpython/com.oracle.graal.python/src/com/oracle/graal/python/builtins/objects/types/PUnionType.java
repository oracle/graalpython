package com.oracle.graal.python.builtins.objects.types;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.truffle.api.object.Shape;

public class PUnionType extends PythonBuiltinObject {
    private PTuple args;

    public PUnionType(Object cls, Shape instanceShape, PTuple args) {
        super(cls, instanceShape);
        this.args = args;
    }

    public PTuple getArgs() {
        return args;
    }
}
