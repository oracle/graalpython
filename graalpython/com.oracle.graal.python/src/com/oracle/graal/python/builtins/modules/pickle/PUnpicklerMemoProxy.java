package com.oracle.graal.python.builtins.modules.pickle;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PUnpicklerMemoProxy extends PythonBuiltinObject {
    private final PUnpickler unpickler;

    public PUnpicklerMemoProxy(Object cls, Shape instanceShape, PUnpickler unpickler) {
        super(cls, instanceShape);
        this.unpickler = unpickler;
    }

    public PUnpickler getUnpickler() {
        return unpickler;
    }
}
