package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.object.Shape;

public class PContext extends PythonBuiltinObject {
    public Hamt contextVarValues = new Hamt();

    public PContext(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }
}
