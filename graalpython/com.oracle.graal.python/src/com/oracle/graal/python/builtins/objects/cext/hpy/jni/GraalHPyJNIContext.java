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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_ELIPSIS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NONE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NOT_IMPLEMENTED;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SIZEOF_LONG;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.nio.file.Paths;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
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
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
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

    public GraalHPyJNIContext(GraalHPyContext context, boolean traceUpcalls) {
        super(context, traceUpcalls);
        this.slowPathFactory = context.getContext().factory();
        this.counts = traceUpcalls ? new int[HPyJNIUpcall.VALUES.length] : null;
    }

    @Override
    protected String getName() {
        return J_NAME;
    }

    protected HPyUpcall[] getUpcalls() {
        return HPyJNIUpcall.VALUES;
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
            nativePointer = initJNI(this, createContextHandleArray());
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
        if (useNativeFastPaths()) {
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
        assert useNativeFastPaths();
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
    private static native long initJNI(GraalHPyJNIContext backend, long[] ctxHandles);

    @TruffleBoundary
    private static native int finalizeJNIContext(long uctxPointer);

    @TruffleBoundary
    private static native long initJNIDebugContext(long uctxPointer);

    @TruffleBoundary
    private static native int finalizeJNIDebugContext(long dctxPointer);

    @TruffleBoundary
    private static native long initJNIDebugModule(long uctxPointer);

    enum HPyJNIUpcall implements HPyUpcall {
        HPyUnicodeFromJCharArray,
        HPyBulkClose,

        // {{start jni upcalls}}
        HPyModuleCreate,
        HPyDup,
        HPyClose,
        HPyLongFromLong,
        HPyLongFromUnsignedLong,
        HPyLongFromLongLong,
        HPyLongFromUnsignedLongLong,
        HPyLongFromSizet,
        HPyLongFromSsizet,
        HPyLongAsLong,
        HPyLongAsUnsignedLong,
        HPyLongAsUnsignedLongMask,
        HPyLongAsLongLong,
        HPyLongAsUnsignedLongLong,
        HPyLongAsUnsignedLongLongMask,
        HPyLongAsSizet,
        HPyLongAsSsizet,
        HPyLongAsVoidPtr,
        HPyLongAsDouble,
        HPyFloatFromDouble,
        HPyFloatAsDouble,
        HPyBoolFromLong,
        HPyLength,
        HPySequenceCheck,
        HPyNumberCheck,
        HPyAdd,
        HPySubtract,
        HPyMultiply,
        HPyMatrixMultiply,
        HPyFloorDivide,
        HPyTrueDivide,
        HPyRemainder,
        HPyDivmod,
        HPyPower,
        HPyNegative,
        HPyPositive,
        HPyAbsolute,
        HPyInvert,
        HPyLshift,
        HPyRshift,
        HPyAnd,
        HPyXor,
        HPyOr,
        HPyIndex,
        HPyLong,
        HPyFloat,
        HPyInPlaceAdd,
        HPyInPlaceSubtract,
        HPyInPlaceMultiply,
        HPyInPlaceMatrixMultiply,
        HPyInPlaceFloorDivide,
        HPyInPlaceTrueDivide,
        HPyInPlaceRemainder,
        HPyInPlacePower,
        HPyInPlaceLshift,
        HPyInPlaceRshift,
        HPyInPlaceAnd,
        HPyInPlaceXor,
        HPyInPlaceOr,
        HPyCallableCheck,
        HPyCallTupleDict,
        HPyFatalError,
        HPyErrSetString,
        HPyErrSetObject,
        HPyErrSetFromErrnoWithFilename,
        HPyErrSetFromErrnoWithFilenameObjects,
        HPyErrOccurred,
        HPyErrExceptionMatches,
        HPyErrNoMemory,
        HPyErrClear,
        HPyErrNewException,
        HPyErrNewExceptionWithDoc,
        HPyErrWarnEx,
        HPyErrWriteUnraisable,
        HPyIsTrue,
        HPyTypeFromSpec,
        HPyTypeGenericNew,
        HPyGetAttr,
        HPyGetAttrs,
        HPyMaybeGetAttrs,
        HPyHasAttr,
        HPyHasAttrs,
        HPySetAttr,
        HPySetAttrs,
        HPyGetItem,
        HPyGetItemi,
        HPyGetItems,
        HPyContains,
        HPySetItem,
        HPySetItemi,
        HPySetItems,
        HPyType,
        HPyTypeCheck,
        HPyTypeCheckg,
        HPySetType,
        HPyTypeIsSubtype,
        HPyTypeGetName,
        HPyIs,
        HPyIsg,
        HPyAsStruct,
        HPyAsStructLegacy,
        HPyNew,
        HPyRepr,
        HPyStr,
        HPyASCII,
        HPyBytes,
        HPyRichCompare,
        HPyRichCompareBool,
        HPyHash,
        HPySeqIterNew,
        HPyBytesCheck,
        HPyBytesSize,
        HPyBytesGETSIZE,
        HPyBytesAsString,
        HPyBytesASSTRING,
        HPyBytesFromString,
        HPyBytesFromStringAndSize,
        HPyUnicodeFromString,
        HPyUnicodeCheck,
        HPyUnicodeAsASCIIString,
        HPyUnicodeAsLatin1String,
        HPyUnicodeAsUTF8String,
        HPyUnicodeAsUTF8AndSize,
        HPyUnicodeFromWideChar,
        HPyUnicodeDecodeFSDefault,
        HPyUnicodeDecodeFSDefaultAndSize,
        HPyUnicodeEncodeFSDefault,
        HPyUnicodeReadChar,
        HPyUnicodeDecodeASCII,
        HPyUnicodeDecodeLatin1,
        HPyUnicodeFromEncodedObject,
        HPyUnicodeInternFromString,
        HPyUnicodeSubstring,
        HPyListCheck,
        HPyListNew,
        HPyListAppend,
        HPyDictCheck,
        HPyDictNew,
        HPyDictKeys,
        HPyDictGetItem,
        HPyTupleCheck,
        HPyTupleFromArray,
        HPySliceUnpack,
        HPyContextVarNew,
        HPyContextVarGet,
        HPyContextVarSet,
        HPyImportImportModule,
        HPyCapsuleNew,
        HPyCapsuleGet,
        HPyCapsuleIsValid,
        HPyCapsuleSet,
        HPyFromPyObject,
        HPyAsPyObject,
        HPyCallRealFunctionFromTrampoline,
        HPyListBuilderNew,
        HPyListBuilderSet,
        HPyListBuilderBuild,
        HPyListBuilderCancel,
        HPyTupleBuilderNew,
        HPyTupleBuilderSet,
        HPyTupleBuilderBuild,
        HPyTupleBuilderCancel,
        HPyTrackerNew,
        HPyTrackerAdd,
        HPyTrackerForgetAll,
        HPyTrackerClose,
        HPyFieldStore,
        HPyFieldLoad,
        HPyReenterPythonExecution,
        HPyLeavePythonExecution,
        HPyGlobalStore,
        HPyGlobalLoad,
        HPyDump,
        HPyTypeCheckSlot;
        // {{end jni upcalls}}

        @CompilationFinal(dimensions = 1) private static final HPyJNIUpcall[] VALUES = values();

        @Override
        public String getName() {
            return name();
        }
    }

    private void increment(HPyJNIUpcall upcall) {
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
        increment(HPyJNIUpcall.HPyTypeCheck);
        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeBits));
        return typeCheck(bits, type);
    }

    public int ctxTypeCheckG(long bits, long typeGlobalBits) {
        increment(HPyJNIUpcall.HPyTypeCheck);
        Object type = context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(typeGlobalBits));
        return typeCheck(bits, type);
    }

    public long ctxLength(long handle) {
        increment(HPyJNIUpcall.HPyLength);
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
        increment(HPyJNIUpcall.HPyListCheck);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object obj = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            Object clazz = GetClassNode.getUncached().execute(obj);
            return PInt.intValue(clazz == PythonBuiltinClassType.PList || IsSubtypeNodeGen.getUncached().execute(clazz, PythonBuiltinClassType.PList));
        } else {
            return 0;
        }
    }

    public long ctxUnicodeFromWideChar(long wcharArrayPtr, long size) {
        increment(HPyJNIUpcall.HPyUnicodeFromWideChar);

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
        increment(HPyJNIUpcall.HPyUnicodeFromJCharArray);
        TruffleString string = TruffleString.fromCharArrayUTF16Uncached(arr).switchEncodingUncached(TS_ENCODING);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(string));
    }

    public long ctxDictNew() {
        increment(HPyJNIUpcall.HPyDictNew);
        PDict dict = slowPathFactory.createDict();
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(dict));
    }

    public long ctxListNew(long llen) {
        try {
            increment(HPyJNIUpcall.HPyListNew);
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
        increment(HPyJNIUpcall.HPyTupleFromArray);

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
        increment(HPyJNIUpcall.HPyFieldLoad);
        Object owner = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        // HPyField index is always non-zero because zero means: uninitialized
        assert idx > 0;
        Object referent = ((PythonObject) owner).getHPyData()[(int) idx];
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(referent));
    }

    public long ctxFieldStore(long bits, long idx, long value) {
        increment(HPyJNIUpcall.HPyFieldStore);
        PythonObject owner = (PythonObject) context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object referent = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(value));
        return GraalHPyFieldStore.assign(owner, referent, (int) idx);
    }

    public long ctxGlobalLoad(long bits) {
        increment(HPyJNIUpcall.HPyGlobalLoad);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits))));
    }

    public long ctxGlobalStore(long bits, long v) {
        increment(HPyJNIUpcall.HPyGlobalStore);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return context.createGlobal(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(v)), GraalHPyBoxing.unboxHandle(bits));
    }

    public long ctxType(long bits) {
        increment(HPyJNIUpcall.HPyType);
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
        increment(HPyJNIUpcall.HPyTypeGetName);
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
        increment(HPyJNIUpcall.HPyContextVarGet);
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
        increment(HPyJNIUpcall.HPyGetAttrs);
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
        increment(HPyJNIUpcall.HPyFloatFromDouble);
        return GraalHPyBoxing.boxDouble(value);
    }

    public double ctxFloatAsDouble(long handle) {
        increment(HPyJNIUpcall.HPyFloatAsDouble);

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
        increment(HPyJNIUpcall.HPyLongAsLong);

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
        increment(HPyJNIUpcall.HPyLongAsDouble);

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
        increment(HPyJNIUpcall.HPyLongFromLong);

        if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(l)) {
            return GraalHPyBoxing.boxInt((int) l);
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(l));
    }

    public long ctxAsStruct(long handle) {
        increment(HPyJNIUpcall.HPyAsStruct);
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
        increment(HPyJNIUpcall.HPySetItems);
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
        increment(HPyJNIUpcall.HPyGetItems);
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
        increment(HPyJNIUpcall.HPyNew);

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
        increment(HPyJNIUpcall.HPyTypeGenericNew);

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
        increment(HPyJNIUpcall.HPyClose);
        closeNativeHandle(handle);
    }

    public void ctxBulkClose(long unclosedHandlePtr, int size) {
        increment(HPyJNIUpcall.HPyBulkClose);
        for (int i = 0; i < size; i++) {
            long handle = UNSAFE.getLong(unclosedHandlePtr);
            unclosedHandlePtr += 8;
            assert GraalHPyBoxing.isBoxedHandle(handle);
            assert handle >= IMMUTABLE_HANDLE_COUNT;
            context.releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(handle));
        }
    }

    public long ctxDup(long handle) {
        increment(HPyJNIUpcall.HPyDup);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object delegate = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(delegate));
        } else {
            return handle;
        }
    }

    public long ctxGetItemi(long hCollection, long lidx) {
        increment(HPyJNIUpcall.HPyGetItemi);
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
        increment(HPyJNIUpcall.HPySetItem);
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
        increment(HPyJNIUpcall.HPySetItemi);
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
        increment(HPyJNIUpcall.HPyNumberCheck);
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

    public long ctxModuleCreate(long def) {
        increment(HPyJNIUpcall.HPyModuleCreate);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongFromUnsignedLong(long value) {
        increment(HPyJNIUpcall.HPyLongFromUnsignedLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongFromLongLong(long v) {
        increment(HPyJNIUpcall.HPyLongFromLongLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongFromUnsignedLongLong(long v) {
        increment(HPyJNIUpcall.HPyLongFromUnsignedLongLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongFromSizet(long value) {
        increment(HPyJNIUpcall.HPyLongFromSizet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongFromSsizet(long value) {
        increment(HPyJNIUpcall.HPyLongFromSsizet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsUnsignedLong(long h) {
        increment(HPyJNIUpcall.HPyLongAsUnsignedLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsUnsignedLongMask(long h) {
        increment(HPyJNIUpcall.HPyLongAsUnsignedLongMask);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsLongLong(long h) {
        increment(HPyJNIUpcall.HPyLongAsLongLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsUnsignedLongLong(long h) {
        increment(HPyJNIUpcall.HPyLongAsUnsignedLongLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsUnsignedLongLongMask(long h) {
        increment(HPyJNIUpcall.HPyLongAsUnsignedLongLongMask);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsSizet(long h) {
        increment(HPyJNIUpcall.HPyLongAsSizet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsSsizet(long h) {
        increment(HPyJNIUpcall.HPyLongAsSsizet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLongAsVoidPtr(long h) {
        increment(HPyJNIUpcall.HPyLongAsVoidPtr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBoolFromLong(long v) {
        increment(HPyJNIUpcall.HPyBoolFromLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxSequenceCheck(long h) {
        increment(HPyJNIUpcall.HPySequenceCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxAdd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyAdd);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxSubtract(long h1, long h2) {
        increment(HPyJNIUpcall.HPySubtract);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyMultiply);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxMatrixMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyMatrixMultiply);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxFloorDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyFloorDivide);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxTrueDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyTrueDivide);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxRemainder(long h1, long h2) {
        increment(HPyJNIUpcall.HPyRemainder);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxDivmod(long h1, long h2) {
        increment(HPyJNIUpcall.HPyDivmod);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxPower(long h1, long h2, long h3) {
        increment(HPyJNIUpcall.HPyPower);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxNegative(long h1) {
        increment(HPyJNIUpcall.HPyNegative);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxPositive(long h1) {
        increment(HPyJNIUpcall.HPyPositive);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxAbsolute(long h1) {
        increment(HPyJNIUpcall.HPyAbsolute);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInvert(long h1) {
        increment(HPyJNIUpcall.HPyInvert);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyLshift);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxRshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyRshift);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxAnd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyAnd);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxXor(long h1, long h2) {
        increment(HPyJNIUpcall.HPyXor);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxOr(long h1, long h2) {
        increment(HPyJNIUpcall.HPyOr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxIndex(long h1) {
        increment(HPyJNIUpcall.HPyIndex);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLong(long h1) {
        increment(HPyJNIUpcall.HPyLong);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxFloat(long h1) {
        increment(HPyJNIUpcall.HPyFloat);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceAdd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceAdd);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceSubtract(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceSubtract);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceMultiply);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceMatrixMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceMatrixMultiply);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceFloorDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceFloorDivide);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceTrueDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceTrueDivide);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceRemainder(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceRemainder);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlacePower(long h1, long h2, long h3) {
        increment(HPyJNIUpcall.HPyInPlacePower);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceLshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceLshift);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceRshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceRshift);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceAnd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceAnd);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceXor(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceXor);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxInPlaceOr(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceOr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxCallableCheck(long h) {
        increment(HPyJNIUpcall.HPyCallableCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxCallTupleDict(long callable, long args, long kw) {
        increment(HPyJNIUpcall.HPyCallTupleDict);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxFatalError(long message) {
        increment(HPyJNIUpcall.HPyFatalError);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrSetString(long h_type, long message) {
        increment(HPyJNIUpcall.HPyErrSetString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrSetObject(long h_type, long h_value) {
        increment(HPyJNIUpcall.HPyErrSetObject);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrSetFromErrnoWithFilename(long h_type, long filename_fsencoded) {
        increment(HPyJNIUpcall.HPyErrSetFromErrnoWithFilename);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrSetFromErrnoWithFilenameObjects(long h_type, long filename1, long filename2) {
        increment(HPyJNIUpcall.HPyErrSetFromErrnoWithFilenameObjects);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxErrOccurred() {
        increment(HPyJNIUpcall.HPyErrOccurred);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxErrExceptionMatches(long exc) {
        increment(HPyJNIUpcall.HPyErrExceptionMatches);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrNoMemory() {
        increment(HPyJNIUpcall.HPyErrNoMemory);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxErrClear() {
        increment(HPyJNIUpcall.HPyErrClear);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrNewException(long name, long base, long dict) {
        increment(HPyJNIUpcall.HPyErrNewException);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxErrNewExceptionWithDoc(long name, long doc, long base, long dict) {
        increment(HPyJNIUpcall.HPyErrNewExceptionWithDoc);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxErrWarnEx(long category, long message, long stack_level) {
        increment(HPyJNIUpcall.HPyErrWarnEx);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxErrWriteUnraisable(long obj) {
        increment(HPyJNIUpcall.HPyErrWriteUnraisable);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxIsTrue(long h) {
        increment(HPyJNIUpcall.HPyIsTrue);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxTypeFromSpec(long spec, long params) {
        increment(HPyJNIUpcall.HPyTypeFromSpec);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxGetAttr(long obj, long name) {
        increment(HPyJNIUpcall.HPyGetAttr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxMaybeGetAttrs(long obj, long name) {
        increment(HPyJNIUpcall.HPyMaybeGetAttrs);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxHasAttr(long obj, long name) {
        increment(HPyJNIUpcall.HPyHasAttr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxHasAttrs(long obj, long name) {
        increment(HPyJNIUpcall.HPyHasAttrs);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxSetAttr(long obj, long name, long value) {
        increment(HPyJNIUpcall.HPySetAttr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxSetAttrs(long obj, long name, long value) {
        increment(HPyJNIUpcall.HPySetAttrs);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxGetItem(long obj, long key) {
        increment(HPyJNIUpcall.HPyGetItem);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxContains(long container, long key) {
        increment(HPyJNIUpcall.HPyContains);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxTypeCheckg(long obj, long type) {
        increment(HPyJNIUpcall.HPyTypeCheckg);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxSetType(long obj, long type) {
        increment(HPyJNIUpcall.HPySetType);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxTypeIsSubtype(long sub, long type) {
        increment(HPyJNIUpcall.HPyTypeIsSubtype);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxIsg(long obj, long other) {
        increment(HPyJNIUpcall.HPyIsg);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxAsStructLegacy(long h) {
        increment(HPyJNIUpcall.HPyAsStructLegacy);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxRepr(long obj) {
        increment(HPyJNIUpcall.HPyRepr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxStr(long obj) {
        increment(HPyJNIUpcall.HPyStr);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxASCII(long obj) {
        increment(HPyJNIUpcall.HPyASCII);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytes(long obj) {
        increment(HPyJNIUpcall.HPyBytes);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxRichCompare(long v, long w, int op) {
        increment(HPyJNIUpcall.HPyRichCompare);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxRichCompareBool(long v, long w, int op) {
        increment(HPyJNIUpcall.HPyRichCompareBool);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxHash(long obj) {
        increment(HPyJNIUpcall.HPyHash);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxSeqIterNew(long seq) {
        increment(HPyJNIUpcall.HPySeqIterNew);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxBytesCheck(long h) {
        increment(HPyJNIUpcall.HPyBytesCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesSize(long h) {
        increment(HPyJNIUpcall.HPyBytesSize);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesGETSIZE(long h) {
        increment(HPyJNIUpcall.HPyBytesGETSIZE);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesAsString(long h) {
        increment(HPyJNIUpcall.HPyBytesAsString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesASSTRING(long h) {
        increment(HPyJNIUpcall.HPyBytesASSTRING);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesFromString(long v) {
        increment(HPyJNIUpcall.HPyBytesFromString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxBytesFromStringAndSize(long v, long len) {
        increment(HPyJNIUpcall.HPyBytesFromStringAndSize);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeFromString(long utf8) {
        increment(HPyJNIUpcall.HPyUnicodeFromString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxUnicodeCheck(long h) {
        increment(HPyJNIUpcall.HPyUnicodeCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeAsASCIIString(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsASCIIString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeAsLatin1String(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsLatin1String);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeAsUTF8String(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsUTF8String);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeAsUTF8AndSize(long h, long size) {
        increment(HPyJNIUpcall.HPyUnicodeAsUTF8AndSize);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeDecodeFSDefault(long v) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeFSDefault);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeDecodeFSDefaultAndSize(long v, long size) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeFSDefaultAndSize);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeEncodeFSDefault(long h) {
        increment(HPyJNIUpcall.HPyUnicodeEncodeFSDefault);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeReadChar(long h, long index) {
        increment(HPyJNIUpcall.HPyUnicodeReadChar);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeDecodeASCII(long s, long size, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeASCII);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeDecodeLatin1(long s, long size, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeLatin1);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeFromEncodedObject(long obj, long encoding, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeFromEncodedObject);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeInternFromString(long str) {
        increment(HPyJNIUpcall.HPyUnicodeInternFromString);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxUnicodeSubstring(long obj, long start, long end) {
        increment(HPyJNIUpcall.HPyUnicodeSubstring);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxListAppend(long h_list, long h_item) {
        increment(HPyJNIUpcall.HPyListAppend);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxDictCheck(long h) {
        increment(HPyJNIUpcall.HPyDictCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxDictKeys(long h) {
        increment(HPyJNIUpcall.HPyDictKeys);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxDictGetItem(long op, long key) {
        increment(HPyJNIUpcall.HPyDictGetItem);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxTupleCheck(long h) {
        increment(HPyJNIUpcall.HPyTupleCheck);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxSliceUnpack(long slice, long start, long stop, long step) {
        increment(HPyJNIUpcall.HPySliceUnpack);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxContextVarNew(long name, long default_value) {
        increment(HPyJNIUpcall.HPyContextVarNew);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxContextVarSet(long context_var, long value) {
        increment(HPyJNIUpcall.HPyContextVarSet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxImportImportModule(long name) {
        increment(HPyJNIUpcall.HPyImportImportModule);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxCapsuleIsValid(long capsule, long name) {
        increment(HPyJNIUpcall.HPyCapsuleIsValid);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxCapsuleSet(long capsule, int key, long value) {
        increment(HPyJNIUpcall.HPyCapsuleSet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxFromPyObject(long obj) {
        increment(HPyJNIUpcall.HPyFromPyObject);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxAsPyObject(long h) {
        increment(HPyJNIUpcall.HPyAsPyObject);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxCallRealFunctionFromTrampoline(long sig, long func, long args) {
        increment(HPyJNIUpcall.HPyCallRealFunctionFromTrampoline);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxListBuilderNew(long initial_size) {
        increment(HPyJNIUpcall.HPyListBuilderNew);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxListBuilderSet(long builder, long index, long h_item) {
        increment(HPyJNIUpcall.HPyListBuilderSet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxListBuilderBuild(long builder) {
        increment(HPyJNIUpcall.HPyListBuilderBuild);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxListBuilderCancel(long builder) {
        increment(HPyJNIUpcall.HPyListBuilderCancel);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxTupleBuilderNew(long initial_size) {
        increment(HPyJNIUpcall.HPyTupleBuilderNew);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxTupleBuilderSet(long builder, long index, long h_item) {
        increment(HPyJNIUpcall.HPyTupleBuilderSet);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxTupleBuilderBuild(long builder) {
        increment(HPyJNIUpcall.HPyTupleBuilderBuild);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxTupleBuilderCancel(long builder) {
        increment(HPyJNIUpcall.HPyTupleBuilderCancel);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxTrackerNew(long size) {
        increment(HPyJNIUpcall.HPyTrackerNew);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxTrackerAdd(long ht, long h) {
        increment(HPyJNIUpcall.HPyTrackerAdd);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxTrackerForgetAll(long ht) {
        increment(HPyJNIUpcall.HPyTrackerForgetAll);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxTrackerClose(long ht) {
        increment(HPyJNIUpcall.HPyTrackerClose);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxReenterPythonExecution(long state) {
        increment(HPyJNIUpcall.HPyReenterPythonExecution);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public long ctxLeavePythonExecution() {
        increment(HPyJNIUpcall.HPyLeavePythonExecution);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public void ctxDump(long h) {
        increment(HPyJNIUpcall.HPyDump);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }

    public int ctxTypeCheckSlot(long type, long value) {
        increment(HPyJNIUpcall.HPyTypeCheckSlot);
        // TODO implement
        throw CompilerDirectives.shouldNotReachHere();
    }
    // {{end ctx funcs}}

    private long createConstant(Object value) {
        return context.getHPyContextHandle(value);
    }

    private static long createSingletonConstant(Object value, int handle) {
        assert GraalHPyContext.getHPyHandleForSingleton(value) == handle;
        return handle;
    }

    private long createTypeConstant(PythonBuiltinClassType value) {
        return context.getHPyContextHandle(context.getContext().lookupType(value));
    }

    /**
     * Creates the context handles, i.e., allocates a handle for each object that is available in
     * {@code HPyContext} (e.g. {@code HPyContext.h_None}). This table is then intended to be used
     * to initialize the native {@code HPyContext *}. The handles are stored in a {@code long} array
     * and the index for each handle is the <it>context index</it> (i.e. the index as specified in
     * HPy's {@code public_api.h}).
     */
    private long[] createContextHandleArray() {
        // {{start ctx handles array}}
        long[] ctxHandles = new long[234];
        ctxHandles[0] = createSingletonConstant(PNone.NONE, SINGLETON_HANDLE_NONE);
        ctxHandles[1] = createConstant(context.getContext().getTrue());
        ctxHandles[2] = createConstant(context.getContext().getFalse());
        ctxHandles[3] = createSingletonConstant(PNotImplemented.NOT_IMPLEMENTED, SINGLETON_HANDLE_NOT_IMPLEMENTED);
        ctxHandles[4] = createSingletonConstant(PEllipsis.INSTANCE, SINGLETON_HANDLE_ELIPSIS);
        ctxHandles[5] = createTypeConstant(PythonBuiltinClassType.PBaseException);
        ctxHandles[6] = createTypeConstant(PythonBuiltinClassType.Exception);
        ctxHandles[7] = createTypeConstant(PythonBuiltinClassType.StopAsyncIteration);
        ctxHandles[8] = createTypeConstant(PythonBuiltinClassType.StopIteration);
        ctxHandles[9] = createTypeConstant(PythonBuiltinClassType.GeneratorExit);
        ctxHandles[10] = createTypeConstant(PythonBuiltinClassType.ArithmeticError);
        ctxHandles[11] = createTypeConstant(PythonBuiltinClassType.LookupError);
        ctxHandles[12] = createTypeConstant(PythonBuiltinClassType.AssertionError);
        ctxHandles[13] = createTypeConstant(PythonBuiltinClassType.AttributeError);
        ctxHandles[14] = createTypeConstant(PythonBuiltinClassType.BufferError);
        ctxHandles[15] = createTypeConstant(PythonBuiltinClassType.EOFError);
        ctxHandles[16] = createTypeConstant(PythonBuiltinClassType.FloatingPointError);
        ctxHandles[17] = createTypeConstant(PythonBuiltinClassType.OSError);
        ctxHandles[18] = createTypeConstant(PythonBuiltinClassType.ImportError);
        ctxHandles[19] = createTypeConstant(PythonBuiltinClassType.ModuleNotFoundError);
        ctxHandles[20] = createTypeConstant(PythonBuiltinClassType.IndexError);
        ctxHandles[21] = createTypeConstant(PythonBuiltinClassType.KeyError);
        ctxHandles[22] = createTypeConstant(PythonBuiltinClassType.KeyboardInterrupt);
        ctxHandles[23] = createTypeConstant(PythonBuiltinClassType.MemoryError);
        ctxHandles[24] = createTypeConstant(PythonBuiltinClassType.NameError);
        ctxHandles[25] = createTypeConstant(PythonBuiltinClassType.OverflowError);
        ctxHandles[26] = createTypeConstant(PythonBuiltinClassType.RuntimeError);
        ctxHandles[27] = createTypeConstant(PythonBuiltinClassType.RecursionError);
        ctxHandles[28] = createTypeConstant(PythonBuiltinClassType.NotImplementedError);
        ctxHandles[29] = createTypeConstant(PythonBuiltinClassType.SyntaxError);
        ctxHandles[30] = createTypeConstant(PythonBuiltinClassType.IndentationError);
        ctxHandles[31] = createTypeConstant(PythonBuiltinClassType.TabError);
        ctxHandles[32] = createTypeConstant(PythonBuiltinClassType.ReferenceError);
        ctxHandles[33] = createTypeConstant(SystemError);
        ctxHandles[34] = createTypeConstant(PythonBuiltinClassType.SystemExit);
        ctxHandles[35] = createTypeConstant(PythonBuiltinClassType.TypeError);
        ctxHandles[36] = createTypeConstant(PythonBuiltinClassType.UnboundLocalError);
        ctxHandles[37] = createTypeConstant(PythonBuiltinClassType.UnicodeError);
        ctxHandles[38] = createTypeConstant(PythonBuiltinClassType.UnicodeEncodeError);
        ctxHandles[39] = createTypeConstant(PythonBuiltinClassType.UnicodeDecodeError);
        ctxHandles[40] = createTypeConstant(PythonBuiltinClassType.UnicodeTranslateError);
        ctxHandles[41] = createTypeConstant(PythonBuiltinClassType.ValueError);
        ctxHandles[42] = createTypeConstant(PythonBuiltinClassType.ZeroDivisionError);
        ctxHandles[43] = createTypeConstant(PythonBuiltinClassType.BlockingIOError);
        ctxHandles[44] = createTypeConstant(PythonBuiltinClassType.BrokenPipeError);
        ctxHandles[45] = createTypeConstant(PythonBuiltinClassType.ChildProcessError);
        ctxHandles[46] = createTypeConstant(PythonBuiltinClassType.ConnectionError);
        ctxHandles[47] = createTypeConstant(PythonBuiltinClassType.ConnectionAbortedError);
        ctxHandles[48] = createTypeConstant(PythonBuiltinClassType.ConnectionRefusedError);
        ctxHandles[49] = createTypeConstant(PythonBuiltinClassType.ConnectionResetError);
        ctxHandles[50] = createTypeConstant(PythonBuiltinClassType.FileExistsError);
        ctxHandles[51] = createTypeConstant(PythonBuiltinClassType.FileNotFoundError);
        ctxHandles[52] = createTypeConstant(PythonBuiltinClassType.InterruptedError);
        ctxHandles[53] = createTypeConstant(PythonBuiltinClassType.IsADirectoryError);
        ctxHandles[54] = createTypeConstant(PythonBuiltinClassType.NotADirectoryError);
        ctxHandles[55] = createTypeConstant(PythonBuiltinClassType.PermissionError);
        ctxHandles[56] = createTypeConstant(PythonBuiltinClassType.ProcessLookupError);
        ctxHandles[57] = createTypeConstant(PythonBuiltinClassType.TimeoutError);
        ctxHandles[58] = createTypeConstant(PythonBuiltinClassType.Warning);
        ctxHandles[59] = createTypeConstant(PythonBuiltinClassType.UserWarning);
        ctxHandles[60] = createTypeConstant(PythonBuiltinClassType.DeprecationWarning);
        ctxHandles[61] = createTypeConstant(PythonBuiltinClassType.PendingDeprecationWarning);
        ctxHandles[62] = createTypeConstant(PythonBuiltinClassType.SyntaxWarning);
        ctxHandles[63] = createTypeConstant(PythonBuiltinClassType.RuntimeWarning);
        ctxHandles[64] = createTypeConstant(PythonBuiltinClassType.FutureWarning);
        ctxHandles[65] = createTypeConstant(PythonBuiltinClassType.ImportWarning);
        ctxHandles[66] = createTypeConstant(PythonBuiltinClassType.UnicodeWarning);
        ctxHandles[67] = createTypeConstant(PythonBuiltinClassType.BytesWarning);
        ctxHandles[68] = createTypeConstant(PythonBuiltinClassType.ResourceWarning);
        ctxHandles[69] = createTypeConstant(PythonBuiltinClassType.PythonObject);
        ctxHandles[70] = createTypeConstant(PythonBuiltinClassType.PythonClass);
        ctxHandles[71] = createTypeConstant(PythonBuiltinClassType.Boolean);
        ctxHandles[72] = createTypeConstant(PythonBuiltinClassType.PInt);
        ctxHandles[73] = createTypeConstant(PythonBuiltinClassType.PFloat);
        ctxHandles[74] = createTypeConstant(PythonBuiltinClassType.PString);
        ctxHandles[75] = createTypeConstant(PythonBuiltinClassType.PTuple);
        ctxHandles[76] = createTypeConstant(PythonBuiltinClassType.PList);
        ctxHandles[229] = createTypeConstant(PythonBuiltinClassType.PComplex);
        ctxHandles[230] = createTypeConstant(PythonBuiltinClassType.PBytes);
        ctxHandles[231] = createTypeConstant(PythonBuiltinClassType.PMemoryView);
        ctxHandles[232] = createTypeConstant(PythonBuiltinClassType.Capsule);
        ctxHandles[233] = createTypeConstant(PythonBuiltinClassType.PSlice);
        return ctxHandles;
        // {{end ctx handles array}}
    }
}
