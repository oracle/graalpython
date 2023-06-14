package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PHamtIterator extends PythonBuiltinObject {
    public final HamtIterator it;

    public PHamtIterator(Object cls, Shape instanceShape, Hamt hamt) {
        super(cls, instanceShape);
        this.it = new HamtIterator(hamt);
    }
}
