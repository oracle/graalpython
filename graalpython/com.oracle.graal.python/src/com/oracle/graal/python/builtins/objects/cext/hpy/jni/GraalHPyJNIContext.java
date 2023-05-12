/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext.hpy.jni;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.UNSAFE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.IMMUTABLE_HANDLE_COUNT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SIZEOF_LONG;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.nio.file.Paths;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.CapsuleKey;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextVarGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFieldStore;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTypeGetNameNodeGen;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNodeGen;
import com.oracle.graal.python.lib.PyFloatAsDoubleNodeGen;
import com.oracle.graal.python.lib.PyIndexCheckNodeGen;
import com.oracle.graal.python.lib.PyLongAsDoubleNodeGen;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNodeGen;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.GilNode.UncachedAcquire;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This object is used to override specific native upcall pointers in the HPyContext. This is
 * queried for every member of HPyContext by {@code graal_hpy_context_to_native}, and overrides the
 * original values (which are NFI closures for functions in {@code hpy.c}, subsequently calling into
 * {@link GraalHPyContextFunctions}.
 */
@ExportLibrary(InteropLibrary.class)
public final class GraalHPyJNIContext extends GraalHPyNativeContext {

    private static final String J_NAME = "HPy Universal ABI (GraalVM JNI backend)";

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyJNIContext.class);
    private static final long NATIVE_ARGUMENT_STACK_SIZE = (2 ^ 15) * SIZEOF_LONG; // 32k entries

    private static boolean jniBackendLoaded = false;

    private final PythonObjectSlowPathFactory slowPathFactory;
    private final int[] counts;

    private long hPyDebugContext;
    private long nativePointer;

    private long nativeArgumentsStack = 0;
    private int nativeArgumentStackPos = 0;

    public GraalHPyJNIContext(GraalHPyContext context, boolean useNativeFastPaths, boolean traceUpcalls) {
        super(context, useNativeFastPaths, traceUpcalls);
        this.slowPathFactory = context.getContext().factory();
        this.counts = traceUpcalls ? new int[Counter.VALUES.length] : null;
    }

    @Override
    protected String getName() {
        return J_NAME;
    }

    protected HPyUpcall[] getUpcalls() {
        return Counter.VALUES;
    }

    protected int[] getUpcallCounts() {
        return counts;
    }

    @Override
    protected long getWcharSize() {
        // TODO(fa): implement
        throw CompilerDirectives.shouldNotReachHere("not yet implemented");
    }

    @ExportMessage
    boolean isPointer() {
        return nativePointer != 0;
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return nativePointer;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnsupportedMessageException.create();
    }

    /**
     * Internal method for transforming the HPy universal context to native. This is mostly like the
     * interop message {@code toNative} but may of course fail if native access is not allowed. This
     * method can be used to force the context to native if a native pointer is needed that will be
     * handed to a native (e.g. JNI or NFI) function.
     */
    @Override
    protected void toNativeInternal() {
        if (nativePointer == 0) {
            CompilerDirectives.transferToInterpreter();
            assert PythonLanguage.get(null).getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI;
            if (!getContext().getEnv().isNativeAccessAllowed()) {
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            loadJNIBackend();
            nativePointer = initJNI(this);
            if (nativePointer == 0) {
                throw CompilerDirectives.shouldNotReachHere("Could not initialize HPy JNI backend.");
            }
        }
    }

    @Override
    protected void initNativeFastPaths() {
        /*
         * Currently, the native fast path functions are only available if the JNI backend is used
         * because they rely on 'initJNI' being called. In future, we might also want to use the
         * native fast path functions for the NFI backend.
         */
        if (useNativeFastPaths) {
            initJNINativeFastPaths(nativePointer);
// PythonContext context = getContext();
// SignatureLibrary signatures = SignatureLibrary.getUncached();
// try {
// Object rlib = evalNFI(context, "load \"" + getJNILibrary() + "\"", "load " +
// PythonContext.J_PYTHON_JNI_LIBRARY_NAME);
// InteropLibrary interop = InteropLibrary.getUncached(rlib);
//
// Object augmentSignature = evalNFI(context, "(POINTER):VOID", "hpy-nfi-signature");
// Object augmentFunction = interop.readMember(rlib, "initDirectFastPaths");
// signatures.call(augmentSignature, augmentFunction, nativePointer);
//
// Object setNativeSpaceSignature = evalNFI(context, "(POINTER, SINT64):VOID", "hpy-nfi-signature");
// setNativeSpaceFunction = signatures.bind(setNativeSpaceSignature, interop.readMember(rlib,
// "setHPyContextNativeSpace"));
// } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException |
// UnknownIdentifierException e) {
// throw CompilerDirectives.shouldNotReachHere();
// }
        }
    }

    public static void loadJNIBackend() {
        if (!(ImageInfo.inImageBuildtimeCode() || jniBackendLoaded)) {
            String pythonJNIPath = getJNILibrary();
            LOGGER.fine("Loading HPy JNI backend from " + pythonJNIPath);
            try {
                System.load(pythonJNIPath);
                jniBackendLoaded = true;
            } catch (NullPointerException | UnsatisfiedLinkError e) {
                LOGGER.severe("HPy JNI backend library could not be found: " + pythonJNIPath);
                LOGGER.severe("Error was: " + e);
            }
        }
    }

    public static String getJNILibrary() {
        CompilerAsserts.neverPartOfCompilation();
        return Paths.get(PythonContext.get(null).getJNIHome().toJavaStringUncached(), PythonContext.J_PYTHON_JNI_LIBRARY_NAME).toString();
    }

    @Override
    protected void initNativeContext() {
        /*
         * We eagerly initialize any native resources (e.g. allocating off-heap memory for
         * 'HPyContext') for the JNI backend because this method will be called if we are up to load
         * an HPy extension module with the JNI backend and there is no way to run the JNI backend
         * without native resources.
         */
        toNativeInternal();
    }

    @Override
    protected void finalizeNativeContext() {
        finalizeJNIContext(nativePointer);
        if (hPyDebugContext != 0) {
            finalizeJNIDebugContext(hPyDebugContext);
        }
        if (nativeArgumentsStack != 0) {
            UNSAFE.freeMemory(nativeArgumentsStack);
        }
    }

    @Override
    public long createNativeArguments(Object[] delegate, InteropLibrary delegateLib) {
        if (nativeArgumentsStack == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeArgumentsStack = UNSAFE.allocateMemory(NATIVE_ARGUMENT_STACK_SIZE);
        }
        long arraySize = delegate.length * SIZEOF_LONG;
        if (nativeArgumentStackPos + arraySize > NATIVE_ARGUMENT_STACK_SIZE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new InternalError("overflow on native argument stack");
        }
        long arrayPtr = nativeArgumentsStack;
        nativeArgumentsStack += arraySize;

        for (int i = 0; i < delegate.length; i++) {
            Object element = delegate[i];
            delegateLib.toNative(element);
            try {
                UNSAFE.putLong(arrayPtr + i * SIZEOF_LONG, delegateLib.asPointer(element));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return arrayPtr;
    }

    @Override
    public void freeNativeArgumentsArray(int size) {
        long arraySize = size * SIZEOF_LONG;
        nativeArgumentsStack -= arraySize;
    }

    @Override
    public void initHPyDebugContext() throws ApiInitException {
        if (hPyDebugContext == 0) {
            CompilerDirectives.transferToInterpreter();
            if (!getContext().getEnv().isNativeAccessAllowed() || getContext().getLanguage().getEngineOption(PythonOptions.HPyBackend) != HPyBackendMode.JNI) {
                throw new ApiInitException(null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
            }
            try {
                toNativeInternal();
                long debugCtxPtr = initJNIDebugContext(nativePointer);
                if (debugCtxPtr == 0) {
                    throw new RuntimeException("Could not initialize HPy debug context");
                }
                hPyDebugContext = debugCtxPtr;
            } catch (CannotCastException e) {
                // TODO(fa): this can go away once 'isNativeAccessAllowed' is always correctly set
                throw new ApiInitException(null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
            }
        }
    }

    /**
     * Equivalent of {@code hpy_debug_get_ctx}. In fact, this method is called from the native
     * {@code hpy_jni.c: hpy_debug_get_ctx} function to get the debug context's pointer via JNI. So,
     * if you change the name of this function, also modify {@code hpy_jni.c} appropriately.
     */
    long getHPyDebugContext() {
        return hPyDebugContext;
    }

    @Override
    @TruffleBoundary
    public PythonModule getHPyDebugModule() throws ImportException {
        if (!getContext().getEnv().isNativeAccessAllowed() || getContext().getLanguage().getEngineOption(PythonOptions.HPyBackend) != HPyBackendMode.JNI) {
            throw new ImportException(null, null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
        }

        // force the universal context to native; we need a real pointer for JNI
        try {
            toNativeInternal();

            // initialize the debug module via JNI
            long debugCtxPtr = initJNIDebugModule(nativePointer);
            if (debugCtxPtr == 0) {
                throw new ImportException(null, null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
            }
            int handle = GraalHPyBoxing.unboxHandle(debugCtxPtr);
            Object nativeDebugModule = context.getObjectForHPyHandle(handle);
            context.releaseHPyHandleForObject(handle);
            if (!(nativeDebugModule instanceof PythonModule)) {
                /*
                 * Since we have the debug module fully under control, this is clearly an internal
                 * error.
                 */
                throw CompilerDirectives.shouldNotReachHere("Debug module is expected to be a Python module object");
            }
            return (PythonModule) nativeDebugModule;
        } catch (CannotCastException e) {
            // TODO(fa): this can go away once 'isNativeAccessAllowed' is always correctly set
            throw new ImportException(null, null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
        }
    }

    @Override
    protected void setNativeCache(long cachePtr) {
        assert useNativeCache();
        setNativeSpaceFunction(nativePointer, cachePtr);
    }

    /* JNI helper functions */

    @TruffleBoundary
    public static native int strcmp(long s1, long s2);

    @TruffleBoundary
    private static native int setNativeSpaceFunction(long uctxPointer, long cachePtr);

    @TruffleBoundary
    private static native int initJNINativeFastPaths(long uctxPointer);

    /* HPY internal JNI trampoline declarations */

    @TruffleBoundary
    private static native long initJNI(GraalHPyJNIContext backend);

    @TruffleBoundary
    private static native int finalizeJNIContext(long uctxPointer);

    @TruffleBoundary
    private static native long initJNIDebugContext(long uctxPointer);

    @TruffleBoundary
    private static native int finalizeJNIDebugContext(long dctxPointer);

    @TruffleBoundary
    private static native long initJNIDebugModule(long uctxPointer);

    public enum Counter implements HPyUpcall {
        // {{start jni upcalls}}
        UpcallCast,
        UpcallNew,
        UpcallTypeGenericNew,
        UpcallTrackerClose,
        UpcallTrackerAdd,
        UpcallClose,
        UpcallBulkClose,
        UpcallTrackerNew,
        UpcallGetItemI,
        UpcallSetItem,
        UpcallSetItemI,
        UpcallDup,
        UpcallNumberCheck,
        UpcallTypeCheck,
        UpcallLength,
        UpcallListCheck,
        UpcallLongAsLong,
        UpcallLongAsDouble,
        UpcallLongFromLong,
        UpcallFloatAsDouble,
        UpcallFloatFromDouble,
        UpcallUnicodeFromWideChar,
        UpcallUnicodeFromJCharArray,
        UpcallDictNew,
        UpcallListNew,
        UpcallTupleFromArray,
        UpcallGetItemS,
        UpcallSetItemS,
        UpcallFieldLoad,
        UpcallFieldStore,
        UpcallGlobalLoad,
        UpcallGlobalStore,
        UpcallType,
        UpcallTypeGetName,
        UpcallContextVarGet,
        UpcallGetAttrS;
        // {{end jni upcalls}}

        @CompilationFinal(dimensions = 1) private static final Counter[] VALUES = values();

        @Override
        public String getName() {
            return name();
        }
    }

    private void increment(Counter upcall) {
        if (counts != null) {
            counts[upcall.ordinal()]++;
        }
    }

    private Object bitsAsPythonObject(long bits) {
        if (GraalHPyBoxing.isBoxedNullHandle(bits)) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        } else if (GraalHPyBoxing.isBoxedInt(bits)) {
            return GraalHPyBoxing.unboxInt(bits);
        } else if (GraalHPyBoxing.isBoxedDouble(bits)) {
            return GraalHPyBoxing.unboxDouble(bits);
        }
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
    }

    private static PythonBuiltinClassType getBuiltinClass(Object cls) {
        if (cls instanceof PythonBuiltinClassType) {
            return (PythonBuiltinClassType) cls;
        } else if (cls instanceof PythonBuiltinClass) {
            return ((PythonBuiltinClass) cls).getType();
        } else {
            return null;
        }
    }

    private int typeCheck(long handle, Object type) {
        Object receiver;
        if (GraalHPyBoxing.isBoxedDouble(handle)) {
            receiver = PythonBuiltinClassType.PFloat;
        } else if (GraalHPyBoxing.isBoxedInt(handle)) {
            receiver = PythonBuiltinClassType.PInt;
        } else {
            receiver = GetClassNode.getUncached().execute(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)));
        }

        if (receiver == type) {
            return 1;
        }

        PythonBuiltinClassType receiverBuiltin = getBuiltinClass(receiver);
        if (receiverBuiltin != null) {
            PythonBuiltinClassType typeBuiltin = getBuiltinClass(type);
            if (typeBuiltin == null) {
                // builtin type cannot be a subclass of a non-builtin type
                return 0;
            }
            // fast path for builtin types: walk class hierarchy
            while (true) {
                if (receiverBuiltin == typeBuiltin) {
                    return 1;
                }
                if (receiverBuiltin == PythonBuiltinClassType.PythonObject) {
                    return 0;
                }
                receiverBuiltin = receiverBuiltin.getBase();
            }
        }

        try {
            return IsSubtypeNode.getUncached().execute(receiver, type) ? 1 : 0;
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return 0;
        }
    }

    /**
     * Coerces an object to a native pointer (i.e. a {@code void *}; represented as Java
     * {@code long}). This is similar to {@link #expectPointer(Object)} but will send
     * {@link InteropLibrary#toNative(Object)} if the object is not a pointer already. The method
     * will throw a {@link CannotCastException} if coercion is not possible.
     */
    private static long coerceToPointer(Object value) throws CannotCastException {
        if (value == null) {
            return 0;
        }
        if (value instanceof Long) {
            return (long) value;
        }
        InteropLibrary interopLibrary = InteropLibrary.getUncached(value);
        if (!interopLibrary.isPointer(value)) {
            interopLibrary.toNative(value);
        }
        try {
            return interopLibrary.asPointer(value);
        } catch (UnsupportedMessageException e) {
            throw CannotCastException.INSTANCE;
        }
    }

    /**
     * Expects an object that can be casted (without coercion) to a native pointer (i.e. a
     * {@code void *}; represented as Java {@code long}). This will throw a
     * {@link CannotCastException} if that is not possible
     */
    private static long expectPointer(Object value) throws CannotCastException {
        if (value instanceof Long) {
            return (long) value;
        }
        InteropLibrary interopLibrary = InteropLibrary.getUncached(value);
        if (interopLibrary.isPointer(value)) {
            try {
                return interopLibrary.asPointer(value);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("cannot cast " + value);
            }
        }
        throw CannotCastException.INSTANCE;
    }

    // {{start ctx funcs}}
    public int ctxTypeCheck(long bits, long typeBits) {
        increment(Counter.UpcallTypeCheck);
        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeBits));
        return typeCheck(bits, type);
    }

    public int ctxTypeCheckG(long bits, long typeGlobalBits) {
        increment(Counter.UpcallTypeCheck);
        Object type = context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(typeGlobalBits));
        return typeCheck(bits, type);
    }

    public long ctxLength(long handle) {
        increment(Counter.UpcallLength);
        assert GraalHPyBoxing.isBoxedHandle(handle);

        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));

        Object clazz = GetClassNode.getUncached().execute(receiver);
        if (clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple) {
            PSequence sequence = (PSequence) receiver;
            SequenceStorage storage = sequence.getSequenceStorage();
            return storage.length();
        }
        try {
            return PyObjectSizeNodeGen.getUncached().execute(null, receiver);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return -1;
        }
    }

    public int ctxListCheck(long handle) {
        increment(Counter.UpcallListCheck);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object obj = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            Object clazz = GetClassNode.getUncached().execute(obj);
            return PInt.intValue(clazz == PythonBuiltinClassType.PList || IsSubtypeNodeGen.getUncached().execute(clazz, PythonBuiltinClassType.PList));
        } else {
            return 0;
        }
    }

    public long ctxUnicodeFromWideChar(long wcharArrayPtr, long size) {
        increment(Counter.UpcallUnicodeFromWideChar);

        if (!PInt.isIntRange(size)) {
            // NULL handle
            return 0;
        }
        int isize = (int) size;
        // TODO GR-37216: use TruffleString.FromNativePointer?
        char[] decoded = new char[isize];
        for (int i = 0; i < size; i++) {
            int wchar = UNSAFE.getInt(wcharArrayPtr + (long) Integer.BYTES * i);
            if (Character.isBmpCodePoint(wchar)) {
                decoded[i] = (char) wchar;
            } else {
                // TODO(fa): handle this case
                throw new RuntimeException();
            }
        }
        TruffleString result = toTruffleStringUncached(new String(decoded, 0, isize));
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
    }

    public long ctxUnicodeFromJCharArray(char[] arr) {
        increment(Counter.UpcallUnicodeFromJCharArray);
        TruffleString string = TruffleString.fromCharArrayUTF16Uncached(arr).switchEncodingUncached(TS_ENCODING);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(string));
    }

    public long ctxDictNew() {
        increment(Counter.UpcallDictNew);
        PDict dict = slowPathFactory.createDict();
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(dict));
    }

    public long ctxListNew(long llen) {
        try {
            increment(Counter.UpcallListNew);
            int len = CastToJavaIntExactNode.getUncached().execute(llen);
            Object[] data = new Object[len];
            Arrays.fill(data, PNone.NONE);
            PList list = slowPathFactory.createList(data);
            return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(list));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            // NULL handle
            return 0;
        }
    }

    /**
     * Implementation of context function {@code ctx_Tuple_FromArray} (JNI upcall). This method can
     * optionally steal the item handles in order to avoid repeated upcalls just to close them. This
     * is useful to implement, e.g., tuple builder.
     */
    public long ctxTupleFromArray(long[] hItems, boolean steal) {
        increment(Counter.UpcallTupleFromArray);

        Object[] objects = new Object[hItems.length];
        for (int i = 0; i < hItems.length; i++) {
            long hBits = hItems[i];
            objects[i] = HPyAsPythonObjectNodeGen.getUncached().execute(hBits);
            if (steal) {
                closeNativeHandle(hBits);
            }
        }
        PTuple tuple = slowPathFactory.createTuple(objects);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(tuple));
    }

    public long ctxFieldLoad(long bits, long idx) {
        increment(Counter.UpcallFieldLoad);
        Object owner = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        // HPyField index is always non-zero because zero means: uninitialized
        assert idx > 0;
        Object referent = ((PythonObject) owner).getHPyData()[(int) idx];
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(referent));
    }

    public long ctxFieldStore(long bits, long idx, long value) {
        increment(Counter.UpcallFieldStore);
        PythonObject owner = (PythonObject) context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object referent = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(value));
        return GraalHPyFieldStore.assign(owner, referent, (int) idx);
    }

    public long ctxGlobalLoad(long bits) {
        increment(Counter.UpcallGlobalLoad);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits))));
    }

    public long ctxGlobalStore(long bits, long v) {
        increment(Counter.UpcallGlobalStore);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return context.createGlobal(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(v)), GraalHPyBoxing.unboxHandle(bits));
    }

    public long ctxType(long bits) {
        increment(Counter.UpcallType);
        Object clazz;
        if (GraalHPyBoxing.isBoxedHandle(bits)) {
            clazz = GetClassNode.getUncached().execute(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits)));
        } else if (GraalHPyBoxing.isBoxedInt(bits)) {
            clazz = GetClassNode.getUncached().execute(GraalHPyBoxing.unboxInt(bits));
        } else if (GraalHPyBoxing.isBoxedDouble(bits)) {
            clazz = GetClassNode.getUncached().execute(GraalHPyBoxing.unboxDouble(bits));
        } else {
            assert false;
            clazz = null;
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(clazz));
    }

    public long ctxTypeGetName(long bits) {
        increment(Counter.UpcallTypeGetName);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        Object clazz = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object tpName = HPyTypeGetNameNodeGen.getUncached().execute(clazz);
        try {
            return coerceToPointer(tpName);
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public long ctxContextVarGet(long varBits, long defBits, long errBits) {
        increment(Counter.UpcallContextVarGet);
        assert GraalHPyBoxing.isBoxedHandle(varBits);
        Object var = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(varBits));
        if (!(var instanceof PContextVar)) {
            try {
                throw PRaiseNode.raiseUncached(null, TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            }
            return errBits;
        }
        PythonContext ctx = getContext();
        PythonLanguage lang = ctx.getLanguage();
        Object def = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(defBits));
        Object res = GraalHPyContextVarGet.getObject(ctx.getThreadState(lang), (PContextVar) var, def);
        if (res == GraalHPyHandle.NULL_HANDLE_DELEGATE) {
            return 0;
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(res));
    }

    public int ctxIs(long aBits, long bBits) {
        Object a = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(aBits));
        Object b = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bBits));
        try {
            return PInt.intValue(IsNodeGen.getUncached().execute(a, b));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return -1;
        }
    }

    public int ctxIsG(long aBits, long bBits) {
        Object a = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(aBits));
        Object b = context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bBits));
        try {
            return PInt.intValue(IsNodeGen.getUncached().execute(a, b));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return -1;
        }
    }

    public long ctxCapsuleNew(long pointer, long name, long destructor) {
        if (pointer == 0) {
            return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, GraalHPyCapsuleNew.NULL_PTR_ERROR);
        }
        PyCapsule result = slowPathFactory.createCapsule(pointer, name, destructor);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
    }

    static boolean capsuleNameMatches(long name1, long name2) {
        // additional shortcut (compared to CPython) to avoid a unnecessary downcalls
        if (name1 == name2) {
            return true;
        }
        /*
         * If one of them is NULL, then both need to be NULL. However, at this point we have
         * invariant 'name1 != name2' because of the above shortcut.
         */
        if (name1 == 0 || name2 == 0) {
            return false;
        }
        return strcmp(name1, name2) == 0;
    }

    public long ctxCapsuleGet(long capsuleBits, int key, long namePtr) {
        Object capsule = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(capsuleBits));
        try {
            if (!(capsule instanceof PyCapsule) || ((PyCapsule) capsule).getPointer() == null) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, GraalHPyCapsuleGet.getErrorMessage(key));
            }
            GraalHPyCapsuleGet.isLegalCapsule(capsule, key, PRaiseNode.getUncached());
            PyCapsule pyCapsule = (PyCapsule) capsule;
            Object result;
            switch (key) {
                case CapsuleKey.Pointer:
                    if (!capsuleNameMatches(namePtr, coerceToPointer(pyCapsule.getName()))) {
                        return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, GraalHPyCapsuleGet.INCORRECT_NAME);
                    }
                    result = pyCapsule.getPointer();
                    break;
                case CapsuleKey.Context:
                    result = pyCapsule.getContext();
                    break;
                case CapsuleKey.Name:
                    result = pyCapsule.getName();
                    break;
                case CapsuleKey.Destructor:
                    result = pyCapsule.getDestructor();
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid key");
            }
            return coerceToPointer(result);
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public long ctxGetAttrs(long receiverHandle, String name) {
        increment(Counter.UpcallGetAttrS);
        Object receiver = bitsAsPythonObject(receiverHandle);
        TruffleString tsName = toTruffleStringUncached(name);
        Object result;
        try {
            result = PyObjectGetAttr.getUncached().execute(null, receiver, tsName);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(context, e);
            return 0;
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
    }

    @SuppressWarnings("static-method")
    public long ctxFloatFromDouble(double value) {
        increment(Counter.UpcallFloatFromDouble);
        return GraalHPyBoxing.boxDouble(value);
    }

    public double ctxFloatAsDouble(long handle) {
        increment(Counter.UpcallFloatAsDouble);

        if (GraalHPyBoxing.isBoxedDouble(handle)) {
            return GraalHPyBoxing.unboxDouble(handle);
        } else if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return PyFloatAsDoubleNodeGen.getUncached().execute(null, object);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
                return -1.0;
            }
        }
    }

    public long ctxLongAsLong(long handle) {
        increment(Counter.UpcallLongAsLong);

        if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return (long) AsNativePrimitiveNodeGen.getUncached().execute(object, 1, java.lang.Long.BYTES, true);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
                return -1L;
            }
        }
    }

    public double ctxLongAsDouble(long handle) {
        increment(Counter.UpcallLongAsDouble);

        if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return (double) PyLongAsDoubleNodeGen.getUncached().execute(object);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
                return -1L;
            }
        }
    }

    public long ctxLongFromLong(long l) {
        increment(Counter.UpcallLongFromLong);

        if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(l)) {
            return GraalHPyBoxing.boxInt((int) l);
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(l));
    }

    public long ctxAsStruct(long handle) {
        increment(Counter.UpcallCast);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
        try {
            return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
        } catch (CannotCastException e) {
            return 0;
        }
    }

    // Note: assumes that receiverHandle is not a boxed primitive value
    @SuppressWarnings("try")
    public int ctxSetItems(long receiverHandle, String name, long valueHandle) {
        increment(Counter.UpcallSetItemS);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(receiverHandle));
        Object value = bitsAsPythonObject(valueHandle);
        if (value == GraalHPyHandle.NULL_HANDLE_DELEGATE) {
            HPyRaiseNode.raiseIntUncached(context, -1, SystemError, ErrorMessages.HPY_UNEXPECTED_HPY_NULL);
            return -1;
        }
        TruffleString tsName = toTruffleStringUncached(name);
        try (UncachedAcquire gil = GilNode.uncachedAcquire()) {
            PyObjectSetItem.getUncached().execute(null, receiver, tsName, value);
            return 0;
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(context, e);
            return -1;
        }
    }

    // Note: assumes that receiverHandle is not a boxed primitive value
    @SuppressWarnings("try")
    public final long ctxGetItems(long receiverHandle, String name) {
        increment(Counter.UpcallGetItemS);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(receiverHandle));
        TruffleString tsName = toTruffleStringUncached(name);
        Object result;
        try (UncachedAcquire gil = GilNode.uncachedAcquire()) {
            result = PyObjectGetItem.getUncached().execute(null, receiver, tsName);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(context, e);
            return 0;
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
    }

    public long ctxNew(long typeHandle, long dataOutVar) {
        increment(Counter.UpcallNew);

        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));
        PythonObject pythonObject;

        /*
         * Check if argument is actually a type. We will only accept PythonClass because that's the
         * only one that makes sense here.
         */
        if (type instanceof PythonClass) {
            PythonClass clazz = (PythonClass) type;

            // allocate native space
            long basicSize = clazz.basicSize;
            if (basicSize == -1) {
                // create the managed Python object
                pythonObject = slowPathFactory.createPythonObject(clazz, clazz.getInstanceShape());
            } else {
                /*
                 * Since this is a JNI upcall method, we know that (1) we are not running in some
                 * managed mode, and (2) the data will be used in real native code. Hence, we can
                 * immediately allocate native memory via Unsafe.
                 */
                long dataPtr = UNSAFE.allocateMemory(basicSize);
                UNSAFE.setMemory(dataPtr, basicSize, (byte) 0);
                UNSAFE.putLong(dataOutVar, dataPtr);
                pythonObject = slowPathFactory.createPythonHPyObject(clazz, dataPtr);
                Object destroyFunc = clazz.hpyDestroyFunc;
                context.createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
        } else {
            // check if argument is still a type (e.g. a built-in type, ...)
            if (!IsTypeNode.getUncached().execute(type)) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, PythonBuiltinClassType.TypeError, ErrorMessages.HPY_NEW_ARG_1_MUST_BE_A_TYPE);
            }
            // TODO(fa): this should actually call __new__
            pythonObject = slowPathFactory.createPythonObject(type);
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(pythonObject));
    }

    public long ctxTypeGenericNew(long typeHandle) {
        increment(Counter.UpcallTypeGenericNew);

        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));

        if (type instanceof PythonClass) {
            PythonClass clazz = (PythonClass) type;

            PythonObject pythonObject;
            long basicSize = clazz.basicSize;
            if (basicSize != -1) {
                // allocate native space
                long dataPtr = UNSAFE.allocateMemory(basicSize);
                UNSAFE.setMemory(dataPtr, basicSize, (byte) 0);
                pythonObject = slowPathFactory.createPythonHPyObject(clazz, dataPtr);
            } else {
                pythonObject = slowPathFactory.createPythonObject(clazz);
            }
            return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(pythonObject));
        }
        throw CompilerDirectives.shouldNotReachHere("not implemented");
    }

    /**
     * Close a native handle received from a JNI upcall (hence represented by a Java {code long}).
     */
    private void closeNativeHandle(long handle) {
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            context.releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(handle));
        }
    }

    public void ctxClose(long handle) {
        increment(Counter.UpcallClose);
        closeNativeHandle(handle);
    }

    public void ctxBulkClose(long unclosedHandlePtr, int size) {
        increment(Counter.UpcallBulkClose);
        for (int i = 0; i < size; i++) {
            long handle = UNSAFE.getLong(unclosedHandlePtr);
            unclosedHandlePtr += 8;
            assert GraalHPyBoxing.isBoxedHandle(handle);
            assert handle >= IMMUTABLE_HANDLE_COUNT;
            context.releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(handle));
        }
    }

    public long ctxDup(long handle) {
        increment(Counter.UpcallDup);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object delegate = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(delegate));
        } else {
            return handle;
        }
    }

    public long ctxGetItemi(long hCollection, long lidx) {
        increment(Counter.UpcallGetItemI);
        try {
            // If handle 'hCollection' is a boxed int or double, the object is not subscriptable.
            if (!GraalHPyBoxing.isBoxedHandle(hCollection)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, 0);
            }
            Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hCollection));
            Object clazz = GetClassNode.getUncached().execute(receiver);
            if (clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple) {
                if (!PInt.isIntRange(lidx)) {
                    throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, lidx);
                }
                int idx = (int) lidx;
                PSequence sequence = (PSequence) receiver;
                SequenceStorage storage = sequence.getSequenceStorage();
                if (storage instanceof IntSequenceStorage) {
                    return GraalHPyBoxing.boxInt(((IntSequenceStorage) storage).getIntItemNormalized(idx));
                } else if (storage instanceof DoubleSequenceStorage) {
                    return GraalHPyBoxing.boxDouble(((DoubleSequenceStorage) storage).getDoubleItemNormalized(idx));
                } else if (storage instanceof LongSequenceStorage) {
                    long lresult = ((LongSequenceStorage) storage).getLongItemNormalized(idx);
                    if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(lresult)) {
                        return GraalHPyBoxing.boxInt((int) lresult);
                    }
                    return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(lresult));
                } else if (storage instanceof ObjectSequenceStorage) {
                    Object result = ((ObjectSequenceStorage) storage).getItemNormalized(idx);
                    if (result instanceof Integer) {
                        return GraalHPyBoxing.boxInt((int) result);
                    } else if (result instanceof Double) {
                        return GraalHPyBoxing.boxDouble((double) result);
                    }
                    return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
                }
                // TODO: other storages...
            }
            Object result = PInteropSubscriptNode.getUncached().execute(receiver, lidx);
            return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            // NULL handle
            return 0;
        }
    }

    /**
     * HPy signature: {@code HPy_SetItem(HPyContext ctx, HPy obj, HPy key, HPy value)}
     *
     * @param hSequence
     * @param hKey
     * @param hValue
     * @return {@code 0} on success; {@code -1} on error
     */
    public int ctxSetItem(long hSequence, long hKey, long hValue) {
        increment(Counter.UpcallSetItem);
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence));
            Object clazz = GetClassNode.getUncached().execute(receiver);
            Object key = HPyAsPythonObjectNodeGen.getUncached().execute(hKey);
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(hValue);

            // fast path
            if (clazz == PythonBuiltinClassType.PDict) {
                PDict dict = (PDict) receiver;
                HashingStorage dictStorage = dict.getDictStorage();

                // super-fast path for string keys
                if (key instanceof TruffleString) {
                    if (dictStorage instanceof EmptyStorage) {
                        dictStorage = PDict.createNewStorage(1);
                        dict.setDictStorage(dictStorage);
                    }

                    if (dictStorage instanceof EconomicMapStorage) {
                        ((EconomicMapStorage) dictStorage).putUncached((TruffleString) key, value);
                        return 0;
                    }
                    // fall through to generic case
                }
                dict.setDictStorage(HashingStorageSetItem.executeUncached(dictStorage, key, value));
                return 0;
            } else if (clazz == PythonBuiltinClassType.PList && PGuards.isInteger(key) && ctxListSetItem(receiver, ((Number) key).longValue(), hValue)) {
                return 0;
            }
            return setItemGeneric(receiver, clazz, key, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            // non-null value indicates an error
            return -1;
        }
    }

    public int ctxSetItemi(long hSequence, long lidx, long hValue) {
        increment(Counter.UpcallSetItemI);
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence));
            Object clazz = GetClassNode.getUncached().execute(receiver);

            if (clazz == PythonBuiltinClassType.PList && ctxListSetItem(receiver, lidx, hValue)) {
                return 0;
            }
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(hValue);
            return setItemGeneric(receiver, clazz, lidx, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            // non-null value indicates an error
            return -1;
        }
    }

    private boolean ctxListSetItem(Object receiver, long lidx, long hValue) {
        // fast path for list
        if (!PInt.isIntRange(lidx)) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, lidx);
        }
        int idx = (int) lidx;
        PList sequence = (PList) receiver;
        SequenceStorage storage = sequence.getSequenceStorage();
        if (storage instanceof IntSequenceStorage && GraalHPyBoxing.isBoxedInt(hValue)) {
            ((IntSequenceStorage) storage).setIntItemNormalized(idx, GraalHPyBoxing.unboxInt(hValue));
            return true;
        } else if (storage instanceof DoubleSequenceStorage && GraalHPyBoxing.isBoxedDouble(hValue)) {
            ((DoubleSequenceStorage) storage).setDoubleItemNormalized(idx, GraalHPyBoxing.unboxDouble(hValue));
            return true;
        } else if (storage instanceof LongSequenceStorage && GraalHPyBoxing.isBoxedInt(hValue)) {
            ((LongSequenceStorage) storage).setLongItemNormalized(idx, GraalHPyBoxing.unboxInt(hValue));
            return true;
        } else if (storage instanceof ObjectSequenceStorage) {
            Object value = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hValue));
            ((ObjectSequenceStorage) storage).setItemNormalized(idx, value);
            return true;
        }
        // TODO: other storages...
        return false;
    }

    private static int setItemGeneric(Object receiver, Object clazz, Object key, Object value) {
        Object setItemAttribute = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.SetItem).execute(clazz);
        if (setItemAttribute == PNone.NO_VALUE) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, receiver);
        }
        CallTernaryMethodNode.getUncached().execute(null, setItemAttribute, receiver, key, value);
        return 0;
    }

    public int ctxNumberCheck(long handle) {
        increment(Counter.UpcallNumberCheck);
        if (GraalHPyBoxing.isBoxedDouble(handle) || GraalHPyBoxing.isBoxedInt(handle)) {
            return 1;
        }
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));

        try {
            if (PyIndexCheckNodeGen.getUncached().execute(receiver) || CanBeDoubleNodeGen.getUncached().execute(receiver)) {
                return 1;
            }
            Object receiverType = GetClassNode.getUncached().execute(receiver);
            return PInt.intValue(LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Int).execute(receiverType) != PNone.NO_VALUE);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return 0;
        }
    }
    // {{end ctx funcs}}

    // {{start autogen}}
    // {{end autogen}}
}
