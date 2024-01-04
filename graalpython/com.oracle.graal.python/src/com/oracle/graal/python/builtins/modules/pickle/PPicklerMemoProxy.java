package com.oracle.graal.python.builtins.modules.pickle;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PPicklerMemoProxy extends PythonBuiltinObject {
    private final PPickler pickler;

    public PPicklerMemoProxy(Object cls, Shape instanceShape, PPickler pickler) {
        super(cls, instanceShape);
        this.pickler = pickler;
    }

    public PPickler getPickler() {
        return pickler;
    }
}
