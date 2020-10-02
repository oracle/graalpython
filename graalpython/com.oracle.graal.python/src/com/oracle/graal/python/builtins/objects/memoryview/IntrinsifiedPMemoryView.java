package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

// TODO POL
// TODO interop lib
public class IntrinsifiedPMemoryView extends PythonBuiltinObject {
    private final Object bufferStructPointer;
    private final Object owner;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final String format;
    private final int ndim;
    // We cannot easily add numbers to pointers in Java, so the actual pointer is bufPointer +
    // offset
    private final Object bufPointer;
    private final int offset;
    private final int[] shape;
    private final int[] strides;
    private final int[] suboffsets;

    public IntrinsifiedPMemoryView(Object cls, Shape instanceShape, Object bufferStructPointer, Object owner,
                    int len, boolean readonly, int itemsize, String format, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets) {
        super(cls, instanceShape);
        this.bufferStructPointer = bufferStructPointer;
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = format;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.offset = offset;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
    }

    public IntrinsifiedPMemoryView(Object cls, Shape instanceShape, Object bufferStructPointer, Object owner, int len, boolean readonly, int itemsize, String format,
                    int ndim, Object bufPointer, int[] shape, int[] strides, int[] suboffsets) {
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

    public int getOffset() {
        return offset;
    }

    public int[] getBufferShape() {
        return shape;
    }

    public int[] getBufferStrides() {
        return strides;
    }

    public int[] getBufferSuboffsets() {
        return suboffsets;
    }
}
