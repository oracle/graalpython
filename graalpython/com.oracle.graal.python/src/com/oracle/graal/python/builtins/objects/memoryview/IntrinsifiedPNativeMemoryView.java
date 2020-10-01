package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class IntrinsifiedPNativeMemoryView extends PythonBuiltinObject {
    private final Object bufferStructPointer;
    private final Object owner;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final String format;
    private final int ndim;
    // We cannot easily add numbers to pointers in Java, so the actual pointer is bufPointer +
    // bufPointerOffset
    private final Object bufPointer;
    private final long bufPointerOffset;
    private final long[] shape;
    private final long[] strides;
    private final long[] suboffsets;

    public IntrinsifiedPNativeMemoryView(Object cls, Shape instanceShape, Object bufferStructPointer, Object owner,
                    int len, boolean readonly, int itemsize, String format, int ndim, Object bufPointer,
                    long bufPointerOffset, long[] shape, long[] strides, long[] suboffsets) {
        super(cls, instanceShape);
        this.bufferStructPointer = bufferStructPointer;
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = format;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.bufPointerOffset = bufPointerOffset;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
    }

    public IntrinsifiedPNativeMemoryView(Object cls, Shape instanceShape, Object bufferStructPointer, Object owner, int len, boolean readonly, int itemsize, String format,
                    int ndim, Object bufPointer, long[] shape, long[] strides, long[] suboffsets) {
        this(cls, instanceShape, bufferStructPointer, owner, len, readonly, itemsize, format, ndim, bufPointer, 0, shape, strides, suboffsets);
    }

    public Object getBufferStructPointer() {
        return bufferStructPointer;
    }

    public Object getOwner() {
        return owner;
    }

    public int getLength() {
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

    public long getBufferPointerOffset() {
        return bufPointerOffset;
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
