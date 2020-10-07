package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.memoryview.IntrinsifiedPMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps MemoryView to provide a native view of the "view" struct element with a shape like
 * {@code struct Py_buffer}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PyMemoryViewBufferWrapper extends PythonNativeWrapper {
    public static final String BUF = "buf";
    public static final String OBJ = "obj";
    public static final String LEN = "len";
    public static final String ITEMSIZE = "itemsize";
    public static final String READONLY = "readonly";
    public static final String NDIM = "ndim";
    public static final String FORMAT = "format";
    public static final String SHAPE = "shape";
    public static final String STRIDES = "strides";
    public static final String SUBOFFSETS = "suboffsets";
    public static final String INTERNAL = "internal";

    public PyMemoryViewBufferWrapper(PythonObject delegate) {
        super(delegate);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case BUF:
            case OBJ:
            case LEN:
            case ITEMSIZE:
            case READONLY:
            case NDIM:
            case FORMAT:
            case SHAPE:
            case STRIDES:
            case SUBOFFSETS:
            case INTERNAL:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached ReadFieldNode readFieldNode) throws UnknownIdentifierException {
        return readFieldNode.execute((IntrinsifiedPMemoryView) lib.getDelegate(this), member);
    }

    @ImportStatic(PyMemoryViewBufferWrapper.class)
    @GenerateUncached
    abstract static class ReadFieldNode extends Node {

        public abstract Object execute(IntrinsifiedPMemoryView delegate, String key) throws UnknownIdentifierException;

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(OBJ, key)"})
        static Object getObj(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
            if (object.getOwner() != null) {
                return toSulongNode.execute(object.getOwner());
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = {"eq(LEN, key)"})
        static Object getLen(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key) {
            return (long) object.getLength();
        }

        @Specialization(guards = {"eq(ITEMSIZE, key)"})
        static Object getItemsize(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key) {
            return (long) object.getItemSize();
        }

        @Specialization(guards = {"eq(NDIM, key)"})
        static Object getINDim(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key) {
            return object.getDimensions();
        }

        @Specialization(guards = {"eq(READONLY, key)"})
        static Object getReadonly(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key) {
            return object.isReadOnly() ? 1 : 0;
        }

        @Specialization(guards = {"eq(FORMAT, key)"})
        static Object getFormat(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.AsCharPointerNode asCharPointerNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
            if (object.getFormatString() != null) {
                return asCharPointerNode.execute(object.getFormatString());
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") IntrinsifiedPMemoryView object, String key) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(key);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }
}
