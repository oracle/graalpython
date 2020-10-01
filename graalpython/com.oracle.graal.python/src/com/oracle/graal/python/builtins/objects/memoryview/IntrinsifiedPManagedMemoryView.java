package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

// TODO POL
public class IntrinsifiedPManagedMemoryView extends PythonBuiltinObject {
    private final Object delegate;
    private final int length;
    private final int sliceStart;
    private final int sliceStep;

    public IntrinsifiedPManagedMemoryView(Object cls, Shape instanceShape, Object delegate, int length, int sliceStart, int sliceStep) {
        super(cls, instanceShape);
        this.delegate = delegate;
        this.length = length;
        this.sliceStart = sliceStart;
        this.sliceStep = sliceStep;
    }

    public IntrinsifiedPManagedMemoryView(Object cls, Shape instanceShape, Object delegate, int length) {
        this(cls, instanceShape, delegate, length, 0, 1);
    }

    public Object getDelegate() {
        return delegate;
    }

    public int getLength() {
        return length;
    }

    public int getSliceStart() {
        return sliceStart;
    }

    public int getSliceStep() {
        return sliceStep;
    }
}
