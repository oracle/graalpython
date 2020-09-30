package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class IntrinsifiedPNativeMemoryView extends PythonBuiltinObject {
    private Object bufferStructPointer;
    private Object owner;
    private int len;
    private boolean readonly;
    private int itemsize;
    private String format;
    private int ndim;
    private Object bufPointer;
    private long[] shape;
    private long[] strides;
    private long[] suboffsets;

    public IntrinsifiedPNativeMemoryView(Object cls, Shape instanceShape, Object bufferStructPointer, Object owner, int len, boolean readonly, int itemsize, String format,
                    int ndim, Object bufPointer, long[] shape, long[] strides, long[] suboffsets) {
        super(cls, instanceShape);
        this.bufferStructPointer = bufferStructPointer;
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = format;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
    }

    public Object getBufferStructPointer() {
        return bufferStructPointer;
    }

    public Object getOwner() {
        return owner;
    }

    public int getTotalLength() {
        return len;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    public int getItemSize() {
        return itemsize;
    }

    public String getFormat() {
        return format;
    }

    public int getDimensions() {
        return ndim;
    }

    public Object getBufferPointer() {
        return bufPointer;
    }

    public long[] getBufferShape() {
        return shape;
    }

    public long[] getBufferStrides() {
        return strides;
    }

    public long[] getBufferSuboffsets() {
        return suboffsets;
    }
}
