package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

// TODO POL
public class IntrinsifiedPManagedMemoryView extends PythonBuiltinObject {
    private Object delegate;
    // TODO sub, strides

    public IntrinsifiedPManagedMemoryView(Object cls, Shape instanceShape, Object delegate) {
        super(cls, instanceShape);
        this.delegate = delegate;
    }

    public Object getDelegate() {
        return delegate;
    }
}
