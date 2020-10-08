package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_PY_BUFFER_TYPEID;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.memoryview.IntrinsifiedPMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
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

    @GenerateUncached
    static abstract class IntArrayToNativePySSizeArray extends Node {
        public abstract Object execute(int[] array);

        @Specialization
        static Object getShape(int[] intArray,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            long[] longArray = new long[intArray.length];
            for (int i = 0; i < intArray.length; i++) {
                longArray[i] = intArray[i];
            }
            // TODO memory leak, see GR-26590
            return callCapiFunction.call(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE, context.getEnv().asGuestValue(longArray), longArray.length);
        }
    }

    @ImportStatic(PyMemoryViewBufferWrapper.class)
    @GenerateUncached
    abstract static class ReadFieldNode extends Node {

        public abstract Object execute(IntrinsifiedPMemoryView delegate, String key) throws UnknownIdentifierException;

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(BUF, key)", "object.getBufferPointer() == null"})
        static Object getBufManaged(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @Shared("pointerAdd") @Cached CExtNodes.PointerAddNode pointerAddNode,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode) {
            PSequence owner = (PSequence) object.getOwner();
            NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(owner));
            if (nativeStorage == null) {
                throw CompilerDirectives.shouldNotReachHere("cannot allocate native storage");
            }
            setStorage.execute(owner, nativeStorage);
            Object pointer = nativeStorage.getPtr();
            if (object.getOffset() == 0) {
                return pointer;
            } else {
                return pointerAddNode.execute(pointer, object.getOffset());
            }
        }

        @Specialization(guards = {"eq(BUF, key)", "object.getBufferPointer() != null"})
        static Object getBufNative(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("pointerAdd") @Cached CExtNodes.PointerAddNode pointerAddNode) {
            if (object.getOffset() == 0) {
                return object.getBufferPointer();
            } else {
                return pointerAddNode.execute(object.getBufferPointer(), object.getOffset());
            }
        }

        @Specialization(guards = {"eq(OBJ, key)"})
        static Object getObj(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNativeNull") @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
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
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNativeNull") @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
            if (object.getFormatString() != null) {
                return asCharPointerNode.execute(object.getFormatString());
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = {"eq(SHAPE, key)"})
        static Object getShape(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            return intArrayToNativePySSizeArray.execute(object.getBufferShape());
        }

        @Specialization(guards = {"eq(STRIDES, key)"})
        static Object getStrides(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            return intArrayToNativePySSizeArray.execute(object.getBufferStrides());
        }

        @Specialization(guards = {"eq(SUBOFFSETS, key)"})
        static Object getSuboffsets(IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNativeNull") @Cached CExtNodes.GetNativeNullNode getNativeNullNode,
                        @Shared("toArray") @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray) {
            if (object.getBufferSuboffsets() != null) {
                return intArrayToNativePySSizeArray.execute(object.getBufferSuboffsets());
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = {"eq(INTERNAL, key)"})
        static Object getInternal(@SuppressWarnings("unused") IntrinsifiedPMemoryView object, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNativeNull") @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") IntrinsifiedPMemoryView object, String key) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(key);
        }
    }

    @ExportMessage
    protected boolean isPointer(
            @Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    public long asPointer(
            @CachedLibrary("this") PythonNativeWrapperLibrary lib,
            @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        Object nativePointer = lib.getNativePointer(this);
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLibrary.asPointer(nativePointer);
    }

    @ExportMessage
    protected void toNative(
            @CachedLibrary("this") PythonNativeWrapperLibrary lib,
            @Exclusive @Cached DynamicObjectNativeWrapper.ToPyObjectNode toPyObjectNode,
            @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        if (!lib.isNative(this)) {
            setNativePointer(toPyObjectNode.execute(this));
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    abstract static class GetNativeType {
        @Specialization(assumptions = "singleNativeContextAssumption()")
        static Object doByteArray(@SuppressWarnings("unused") PyMemoryViewBufferWrapper receiver,
                        @Exclusive @Cached(value = "callGetThreadStateTypeIDUncached()") Object nativeType) {
            return nativeType;
        }

        @Specialization(replaces = "doByteArray")
        static Object doByteArrayMultiCtx(@SuppressWarnings("unused") PyMemoryViewBufferWrapper receiver,
                        @Exclusive @Cached CExtNodes.PCallCapiFunction callUnaryNode) {
            return callUnaryNode.call(FUN_GET_PY_BUFFER_TYPEID);
        }

        protected static Object callGetThreadStateTypeIDUncached() {
            return CExtNodes.PCallCapiFunction.getUncached().call(FUN_GET_PY_BUFFER_TYPEID);
        }

        protected static Assumption singleNativeContextAssumption() {
            return PythonContext.getSingleNativeContextAssumption();
        }
    }
}
