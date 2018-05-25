package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;

public class PBuffer extends PythonBuiltinObject {

    private final Object delegate;

    public PBuffer(PythonClass cls, Object iterable) {
        super(cls);
        this.delegate = iterable;
    }

    public Object getDelegate() {
        return delegate;
    }

}
