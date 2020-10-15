package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

// TODO interop lib
@ExportLibrary(PythonObjectLibrary.class)
public class IntrinsifiedPMemoryView extends PythonBuiltinObject {
    public static final int MAX_DIM = 64;

    public static final int FLAG_RELEASED = 0x001;
    public static final int FLAG_C = 0x002;
    public static final int FLAG_FORTRAN = 0x004;
    public static final int FLAG_SCALAR = 0x008;
    public static final int FLAG_PIL = 0x010;

    private Object owner;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final String formatString;
    private final BufferFormat format;
    private final int ndim;
    // We cannot easily add numbers to pointers in Java, so the actual pointer is bufPointer +
    // offset
    private final Object bufPointer;
    private final int offset;
    private final int[] shape;
    private final int[] strides;
    private final int[] suboffsets;

    // Count of exports via native buffer interface
    private final AtomicInteger exports = new AtomicInteger();
    // Phantom ref to this object that will decref/release the managed buffer if any
    private BufferReference reference;
    private int flags;

    // Cached hash value, required to compy with CPython's semantics
    private int cachedHash = -1;

    public IntrinsifiedPMemoryView(Object cls, Shape instanceShape, MemoryViewNodes.BufferReferences references, ManagedBuffer managedBuffer, Object owner,
                    int len, boolean readonly, int itemsize, String formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        super(cls, instanceShape);
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = BufferFormat.fromString(formatString);
        this.formatString = formatString;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.offset = offset;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
        this.flags = flags;
        if (managedBuffer != null) {
            this.reference = new BufferReference(this, managedBuffer, references.queue);
            references.set.add(this.reference);
        }
    }

    public enum BufferFormat {
        UNSIGNED_BYTE,
        SIGNED_BYTE,
        UNSIGNED_SHORT,
        SIGNED_SHORT,
        UNSIGNED_INT,
        SIGNED_INT,
        UNSIGNED_LONG,
        SIGNED_LONG,
        UNSIGNED_LONG_LONG,
        SIGNED_LONG_LONG,
        UNSIGNED_SIZE,
        SIGNED_SIZE,
        BOOLEAN,
        CHAR,
        POINTER,
        FLOAT,
        DOUBLE,
        OTHER;

        public static BufferFormat fromString(String format) {
            if (format == null) {
                return UNSIGNED_BYTE;
            }
            if (!format.isEmpty()) {
                char fmtchar = format.charAt(0);
                if (fmtchar == '@' && format.length() >= 2) {
                    fmtchar = format.charAt(1);
                }
                switch (fmtchar) {
                    case 'B':
                        return UNSIGNED_BYTE;
                    case 'b':
                        return SIGNED_BYTE;
                    case 'H':
                        return UNSIGNED_SHORT;
                    case 'h':
                        return SIGNED_SHORT;
                    case 'I':
                        return UNSIGNED_INT;
                    case 'i':
                        return SIGNED_INT;
                    case 'L':
                        return UNSIGNED_LONG;
                    case 'l':
                        return SIGNED_LONG;
                    case 'Q':
                        return UNSIGNED_LONG_LONG;
                    case 'q':
                        return SIGNED_LONG_LONG;
                    case 'N':
                        return SIGNED_SIZE;
                    case 'n':
                        return UNSIGNED_SIZE;
                    case 'f':
                        return FLOAT;
                    case 'd':
                        return DOUBLE;
                    case 'P':
                        return POINTER;
                    case '?':
                        return BOOLEAN;
                    case 'c':
                        return CHAR;
                }
            }
            return OTHER;
        }

    }

    // From CPython init_strides_from_shape
    public static int[] initStridesFromShape(int ndim, int itemsize, int[] shape) {
        int[] strides = new int[ndim];
        strides[ndim - 1] = itemsize;
        for (int i = ndim - 2; i >= 0; i--) {
            strides[i] = strides[i + 1] * shape[i + 1];
        }
        return strides;
    }

    public ManagedBuffer getManagedBuffer() {
        return (reference != null) ? reference.getManagedBuffer() : null;
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

    public String getFormatString() {
        return formatString;
    }

    public BufferFormat getFormat() {
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

    public boolean isReleased() {
        return (flags & FLAG_RELEASED) != 0;
    }

    public boolean isCContiguous() {
        return (flags & FLAG_C) != 0;
    }

    public boolean isFortranContiguous() {
        return (flags & FLAG_FORTRAN) != 0;
    }

    public int getFlags() {
        return flags;
    }

    public AtomicInteger getExports() {
        return exports;
    }

    public BufferReference getReference() {
        return reference;
    }

    public int getCachedHash() {
        return cachedHash;
    }

    public void setCachedHash(int cachedHash) {
        this.cachedHash = cachedHash;
    }

    public void setReleased() {
        flags |= FLAG_RELEASED;
        if (reference != null) {
            reference.markReleased();
            reference = null;
        }
        owner = null;
    }

    public void checkReleased(PythonBuiltinBaseNode node) {
        if (isReleased()) {
            throw node.raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    @ExportMessage
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return getLength();
    }

    @ExportMessage
    byte[] getBufferBytes(@Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode) {
        return toJavaBytesNode.execute(this);
    }
}
