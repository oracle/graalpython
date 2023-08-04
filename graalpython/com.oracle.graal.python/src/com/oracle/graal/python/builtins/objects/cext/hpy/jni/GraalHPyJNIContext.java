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
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyABIVersion;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.CapsuleKey;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextVarGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyBinaryContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyTernaryContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyData;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCallHelperFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.GraalHPyModuleCreateNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.GraalHPyModuleExecNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyPackKeywordArgsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTypeGetNameNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextMember;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNINodes.HPyJNIFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
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
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
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
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
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

    private static boolean jniBackendLoaded = false;

    private final PythonObjectSlowPathFactory slowPathFactory;
    private final int[] counts;

    private long hPyDebugContext;
    private long nativePointer;

    /**
     * This list holds a strong reference to all loaded extension libraries to keep the library
     * objects alive. This is necessary because NFI will {@code dlclose} the library (and thus
     * {@code munmap} all code) if the library object is no longer reachable. However, it can happen
     * that we still store raw function pointers (as Java {@code long} values) in
     * {@link GraalHPyJNIFunctionPointer} objects which are invoked <it>asynchronously</it>. For
     * example, destroy functions will be executed on a different thread some time after the object
     * died. Buffer release functions run on the main thread but like an async action at some
     * unknown point in time after the buffer owner died.
     * 
     * Since we have no control over the execution order of those cleaners, we need to ensure that
     * the code is still mapped.
     */
    private final List<Object> loadedExtensions = new LinkedList<>();

    public GraalHPyJNIContext(GraalHPyContext context, boolean traceUpcalls) {
        super(context, traceUpcalls);
        this.slowPathFactory = context.getContext().factory();
        this.counts = traceUpcalls ? new int[HPyJNIUpcall.VALUES.length] : null;
    }

    @Override
    protected String getName() {
        return J_NAME;
    }

    @Override
    protected Object loadExtensionLibrary(Node location, PythonContext context, TruffleString name, TruffleString path) throws ImportException {
        CompilerAsserts.neverPartOfCompilation();
        TruffleFile extLibFile = context.getPublicTruffleFileRelaxed(path, context.getSoAbi());
        try {
            /*
             * Even in the JNI backend, we load the library with NFI (instead of using
             * 'System.load') because NFI may take care of additional security checks.
             */
            String src = "load \"" + extLibFile + '"';
            Source loadSrc = Source.newBuilder(J_NFI_LANGUAGE, src, "load:" + name).internal(true).build();
            Object extLib = context.getEnv().parseInternal(loadSrc).call();
            loadedExtensions.add(extLib);
            return extLib;
        } catch (SecurityException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_LOAD_M, path, e);
        }
    }

    @Override
    protected HPyABIVersion getHPyABIVersion(Object extLib, String getMajorVersionFuncName, String getMinorVersionFuncName) throws UnknownIdentifierException {
        CompilerAsserts.neverPartOfCompilation();
        try {
            InteropLibrary lib = InteropLibrary.getUncached(extLib);
            Object majorVersionFun = lib.readMember(extLib, getMajorVersionFuncName);
            Object minorVersionFun = lib.readMember(extLib, getMinorVersionFuncName);
            int requiredMajorVersion = (int) GraalHPyJNITrampolines.executeModuleInit(coerceToPointer(majorVersionFun));
            int requiredMinorVersion = (int) GraalHPyJNITrampolines.executeModuleInit(coerceToPointer(minorVersionFun));
            return new HPyABIVersion(requiredMajorVersion, requiredMinorVersion);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    protected Object initHPyModule(Object extLib, String initFuncName, TruffleString name, TruffleString path, boolean debug)
                    throws ImportException, ApiInitException {
        CompilerAsserts.neverPartOfCompilation();
        /*
         * We eagerly initialize the debug mode here to be able to produce an error message now if
         * we cannot use it.
         */
        if (debug) {
            initHPyDebugContext();
        }

        Object initFunction;
        try {
            InteropLibrary lib = InteropLibrary.getUncached(extLib);
            initFunction = lib.readMember(extLib, initFuncName);
        } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
            throw new ImportException(null, name, path, ErrorMessages.CANNOT_INITIALIZE_EXT_NO_ENTRY, name, path, initFuncName);
        }
        // NFI doesn't know if a symbol is executable, so it always reports false
        assert !InteropLibrary.getUncached().isExecutable(initFunction);

        // coerce 'initFunction' to a native pointer and invoke it via JNI trampoline
        long moduleDefPtr;
        moduleDefPtr = GraalHPyJNITrampolines.executeModuleInit(coerceToPointer(initFunction));
        return convertLongArg(HPyContextSignatureType.HPyModuleDefPtr, moduleDefPtr);
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
            nativePointer = initJNI(this, context, createContextHandleArray());
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
        assert useNativeFastPaths();
        initJNINativeFastPaths(nativePointer);
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
        toNative();
    }

    @Override
    protected void finalizeNativeContext() {
        finalizeJNIContext(nativePointer);
        nativePointer = 0;
        if (hPyDebugContext != 0) {
            finalizeJNIDebugContext(hPyDebugContext);
            hPyDebugContext = 0;
        }
        loadedExtensions.clear();
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

    @Override
    protected Object createArgumentsArray(Object[] args) {
        return context.createNativeArguments(args);
    }

    @Override
    protected void freeArgumentsArray(Object argsArray) {
        if (argsArray instanceof Long argsArrayPtr) {
            context.freeNativeArgumentsUntil(argsArrayPtr);
        }
    }

    /**
     * Equivalent of {@code hpy_debug_get_ctx}. In fact, this method is called from the native
     * {@code hpy_jni.c: hpy_debug_get_ctx} function to get the debug context's pointer via JNI. So,
     * if you change the name of this function, also modify {@code hpy_jni.c} appropriately.
     */
    long getHPyDebugContext() {
        /*
         * It is a valid path that this method is called but the debug context has not yet been
         * initialized. In particular, this can happen if the leak detector is used which calls
         * methods of the native debug module. The native methods may call function
         * 'hpy_debug_get_ctx' which upcalls to this method. All this may happen before any HPy
         * extension was loaded with debug mode enabled.
         */
        if (hPyDebugContext == 0) {
            try {
                initHPyDebugContext();
            } catch (ApiInitException e) {
                throw CompilerDirectives.shouldNotReachHere(e.getMessage());
            }
        }
        return hPyDebugContext;
    }

    @Override
    @TruffleBoundary
    public PythonModule getHPyDebugModule() throws ImportException {
        assert getContext().getEnv().isNativeAccessAllowed();
        assert getContext().getLanguage().getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI;

        // force the universal context to native; we need a real pointer for JNI
        toNativeInternal();

        // initialize the debug module via JNI
        long debugModuleDef = initJNIDebugModule(nativePointer);
        if (debugModuleDef == 0) {
            throw new ImportException(null, null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
        }
        /*
         * Note: we don't need a 'spec' object since that's only required if the module has slot
         * HPy_mod_create which is guaranteed to be missing in this case.
         */
        TruffleString name = tsLiteral("_debug");
        Object debugModuleDefPtrObj = convertLongArg(HPyContextSignatureType.HPyModuleDefPtr, debugModuleDef);
        Object nativeDebugModule = GraalHPyModuleCreateNodeGen.getUncached().execute(context, name, null, debugModuleDefPtrObj);
        if (nativeDebugModule instanceof PythonModule pythonDebugModule) {
            GraalHPyModuleExecNodeGen.getUncached().execute(null, context, pythonDebugModule);
            return (PythonModule) nativeDebugModule;
        }
        /*
         * Since we have the debug module fully under control, this is clearly an internal error.
         */
        throw CompilerDirectives.shouldNotReachHere("Debug module is expected to be a Python module object");
    }

    @Override
    protected void setNativeCache(long cachePtr) {
        assert useNativeFastPaths();
        setNativeSpaceFunction(nativePointer, cachePtr);
    }

    @Override
    public HPyCallHelperFunctionNode createCallHelperFunctionNode() {
        return GraalHPyJNICallHelperFunctionNode.UNCACHED;
    }

    @Override
    public HPyCallHelperFunctionNode getUncachedCallHelperFunctionNode() {
        return GraalHPyJNICallHelperFunctionNode.UNCACHED;
    }

    @Override
    public HPyFromCharPointerNode createFromCharPointerNode() {
        return HPyJNIFromCharPointerNode.UNCACHED;
    }

    @Override
    public HPyFromCharPointerNode getUncachedFromCharPointerNode() {
        return HPyJNIFromCharPointerNode.UNCACHED;
    }

    /* JNI helper functions */

    @TruffleBoundary
    public static native int strcmp(long s1, long s2);

    @TruffleBoundary
    private static native int setNativeSpaceFunction(long uctxPointer, long cachePtr);

    @TruffleBoundary
    private static native int initJNINativeFastPaths(long uctxPointer);

    @TruffleBoundary
    public static native int getErrno();

    @TruffleBoundary
    public static native long getStrerror(int errno);

    /* HPY internal JNI trampoline declarations */

    @TruffleBoundary
    private static native long initJNI(GraalHPyJNIContext backend, GraalHPyContext hpyContext, long[] ctxHandles);

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
        HPySequenceFromArray,

        // {{start jni upcalls}}
        // @formatter:off
        // Checkstyle: stop
        // DO NOT EDIT THIS PART!
        // This part is automatically generated by hpy.tools.autogen.graalpy.autogen_ctx_jni_upcall_enum
        HPyDup,
        HPyClose,
        HPyLongFromInt32t,
        HPyLongFromUInt32t,
        HPyLongFromInt64t,
        HPyLongFromUInt64t,
        HPyLongFromSizet,
        HPyLongFromSsizet,
        HPyLongAsInt32t,
        HPyLongAsUInt32t,
        HPyLongAsUInt32tMask,
        HPyLongAsInt64t,
        HPyLongAsUInt64t,
        HPyLongAsUInt64tMask,
        HPyLongAsSizet,
        HPyLongAsSsizet,
        HPyLongAsVoidPtr,
        HPyLongAsDouble,
        HPyFloatFromDouble,
        HPyFloatAsDouble,
        HPyBoolFromBool,
        HPyLength,
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
        HPyCall,
        HPyCallMethod,
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
        HPyDelItem,
        HPyDelItemi,
        HPyDelItems,
        HPyType,
        HPyTypeCheck,
        HPyTypeGetName,
        HPyTypeIsSubtype,
        HPyIs,
        HPyAsStructObject,
        HPyAsStructLegacy,
        HPyAsStructType,
        HPyAsStructLong,
        HPyAsStructFloat,
        HPyAsStructUnicode,
        HPyAsStructTuple,
        HPyAsStructList,
        HPyTypeGetBuiltinShape,
        HPyNew,
        HPyRepr,
        HPyStr,
        HPyASCII,
        HPyBytes,
        HPyRichCompare,
        HPyRichCompareBool,
        HPyHash,
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
        HPyUnicodeSubstring,
        HPyListCheck,
        HPyListNew,
        HPyListAppend,
        HPyDictCheck,
        HPyDictNew,
        HPyDictKeys,
        HPyDictCopy,
        HPyTupleCheck,
        HPyTupleFromArray,
        HPySliceUnpack,
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
        HPyCompiles,
        HPyEvalCode,
        HPyContextVarNew,
        HPyContextVarGet,
        HPyContextVarSet,
        HPySetCallFunction;

        // @formatter:on
        // Checkstyle: resume
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
            receiver = InlinedGetClassNode.executeUncached(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)));
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
     * {@code void *}; represented as Java {@code long}). This method will return {@code 0} in case
     * of errors.
     */
    public static long expectPointer(Object value) {
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
        return 0;
    }

    /**
     * Transforms the given {@link TruffleString} to a string with native buffer and returns the
     * internal pointer object (which is guaranteed to answer
     * {@link InteropLibrary#isPointer(Object)} with {@code true}).
     */
    private static Object truffleStringToNative(TruffleString value) {
        TruffleString nativeTName = TruffleString.AsNativeNode.getUncached().execute(value, (size) -> {
            // over-allocate by 1 byte and write a zero terminator
            long ptr = UNSAFE.allocateMemory(size + 1);
            UNSAFE.putByte(ptr + size, (byte) 0);
            return new NativePointer(ptr);
        }, TS_ENCODING, true, true);
        Object result = TruffleString.GetInternalNativePointerNode.getUncached().execute(nativeTName, TS_ENCODING);
        assert InteropLibrary.getUncached().isPointer(result);
        return result;
    }

    private static Object capsuleNameToNative(Object name) {
        if (name instanceof TruffleString tname) {
            // The capsule's name may either be a native pointer or a TruffleString.
            return truffleStringToNative(tname);
        }
        return name;
    }

    // {{start ctx funcs}}
    public int ctxTypeCheck(long bits, long typeBits) {
        increment(HPyJNIUpcall.HPyTypeCheck);
        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeBits));
        return typeCheck(bits, type);
    }

    public int ctxTypeCheckg(long bits, long typeGlobalBits) {
        increment(HPyJNIUpcall.HPyTypeCheck);
        Object type = context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(typeGlobalBits));
        return typeCheck(bits, type);
    }

    public long ctxLength(long handle) {
        increment(HPyJNIUpcall.HPyLength);
        assert GraalHPyBoxing.isBoxedHandle(handle);

        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));

        Object clazz = InlinedGetClassNode.executeUncached(receiver);
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
            Object clazz = InlinedGetClassNode.executeUncached(obj);
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
    public long ctxSequenceFromArray(long[] hItems, boolean steal, boolean create_list) {
        increment(HPyJNIUpcall.HPySequenceFromArray);

        Object[] objects = new Object[hItems.length];
        for (int i = 0; i < hItems.length; i++) {
            long hBits = hItems[i];
            objects[i] = context.bitsAsPythonObject(hBits);
            if (steal) {
                closeNativeHandle(hBits);
            }
        }
        Object result;
        if (create_list) {
            result = slowPathFactory.createList(objects);
        } else {
            result = slowPathFactory.createTuple(objects);
        }
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
    }

    public long ctxFieldLoad(long bits, long idx) {
        increment(HPyJNIUpcall.HPyFieldLoad);
        Object owner = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        // HPyField index is always non-zero because zero means: uninitialized
        assert idx > 0;
        Object referent = GraalHPyData.getHPyField((PythonObject) owner, (int) idx);
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(referent));
    }

    public long ctxFieldStore(long bits, long idx, long value) {
        increment(HPyJNIUpcall.HPyFieldStore);
        PythonObject owner = (PythonObject) context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object referent = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(value));
        return GraalHPyData.setHPyField(owner, referent, (int) idx);
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
            clazz = InlinedGetClassNode.executeUncached(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits)));
        } else if (GraalHPyBoxing.isBoxedInt(bits)) {
            clazz = InlinedGetClassNode.executeUncached(GraalHPyBoxing.unboxInt(bits));
        } else if (GraalHPyBoxing.isBoxedDouble(bits)) {
            clazz = InlinedGetClassNode.executeUncached(GraalHPyBoxing.unboxDouble(bits));
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

    public int ctxIsg(long aBits, long bBits) {
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
            return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, ErrorMessages.HPYCAPSULE_NEW_NULL_PTR_ERROR);
        }
        long hpyDestructor;
        if (destructor != 0) {
            long cpyTrampoline = UNSAFE.getLong(destructor); // HPyCapsule_Destructor.cpy_trampoline
            hpyDestructor = UNSAFE.getLong(destructor + SIZEOF_LONG); // HPyCapsule_Destructor.impl
            if (cpyTrampoline == 0 || hpyDestructor == 0) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, ErrorMessages.INVALID_HPYCAPSULE_DESTRUCTOR);
            }
        } else {
            hpyDestructor = 0;
        }
        PyCapsule result = slowPathFactory.createCapsule(pointer, new NativePointer(name), hpyDestructor);
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
            if (!(capsule instanceof PyCapsule pyCapsule) || ((PyCapsule) capsule).getPointer() == null) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, GraalHPyCapsuleGet.getErrorMessage(key));
            }
            GraalHPyCapsuleGet.isLegalCapsule(capsule, key, PRaiseNode.getUncached());
            Object result;
            switch (key) {
                case CapsuleKey.Pointer -> {
                    if (!capsuleNameMatches(namePtr, coerceToPointer(capsuleNameToNative(pyCapsule.getName())))) {
                        return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(context, 0, ValueError, GraalHPyCapsuleGet.INCORRECT_NAME);
                    }
                    result = pyCapsule.getPointer();
                }
                case CapsuleKey.Context -> result = pyCapsule.getContext();
                // The capsule's name may either be a native pointer or a TruffleString.
                case CapsuleKey.Name -> result = capsuleNameToNative(pyCapsule.getName());
                case CapsuleKey.Destructor -> result = pyCapsule.getDestructor();
                default -> throw CompilerDirectives.shouldNotReachHere("invalid key");
            }
            return coerceToPointer(result);
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public long ctxGetAttrs(long receiverHandle, String name) {
        increment(HPyJNIUpcall.HPyGetAttrs);
        Object receiver = context.bitsAsPythonObject(receiverHandle);
        TruffleString tsName = toTruffleStringUncached(name);
        Object result;
        try {
            result = PyObjectGetAttr.getUncached().execute(null, receiver, tsName);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(context, e);
            return 0;
        }
        return context.pythonObjectAsBits(result);
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

    public int ctxLongAsInt32t(long h) {
        increment(HPyJNIUpcall.HPyLongAsInt32t);
        if (GraalHPyBoxing.isBoxedInt(h)) {
            return GraalHPyBoxing.unboxInt(h);
        }
        return executeIntBinaryContextFunction(HPyContextMember.CTX_LONG_ASINT32_T, h);
    }

    public int ctxLongAsUInt32t(long h) {
        increment(HPyJNIUpcall.HPyLongAsUInt32t);
        // we may only unbox positive values; negative values will raise an error
        int unboxedVal;
        if (GraalHPyBoxing.isBoxedInt(h) && (unboxedVal = GraalHPyBoxing.unboxInt(h)) >= 0) {
            return unboxedVal;
        }
        return executeIntBinaryContextFunction(HPyContextMember.CTX_LONG_ASUINT32_T, h);
    }

    public int ctxLongAsUInt32tMask(long h) {
        increment(HPyJNIUpcall.HPyLongAsUInt32tMask);
        if (GraalHPyBoxing.isBoxedInt(h)) {
            return GraalHPyBoxing.unboxInt(h);
        }
        return executeIntBinaryContextFunction(HPyContextMember.CTX_LONG_ASUINT32_TMASK, h);
    }

    public long ctxLongAsInt64t(long handle) {
        increment(HPyJNIUpcall.HPyLongAsInt64t);

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

    public long ctxLongAsUInt64t(long h) {
        increment(HPyJNIUpcall.HPyLongAsUInt64t);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_ASUINT64_T, h);
    }

    public long ctxLongAsUInt64tMask(long h) {
        increment(HPyJNIUpcall.HPyLongAsUInt64tMask);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_ASUINT64_TMASK, h);
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

    public long ctxLongFromInt32t(int v) {
        increment(HPyJNIUpcall.HPyLongFromInt32t);
        return GraalHPyBoxing.boxInt(v);
    }

    public long ctxLongFromUInt32t(int value) {
        increment(HPyJNIUpcall.HPyLongFromUInt32t);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_FROMUINT32_T, value);
    }

    public long ctxLongFromInt64t(long v) {
        increment(HPyJNIUpcall.HPyLongFromInt64t);
        if (PInt.isIntRange(v)) {
            return GraalHPyBoxing.boxInt((int) v);
        }
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_FROMINT64_T, v);
    }

    public long ctxLongFromUInt64t(long v) {
        increment(HPyJNIUpcall.HPyLongFromUInt64t);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_FROMUINT64_T, v);
    }

    public long ctxBoolFromBool(boolean v) {
        increment(HPyJNIUpcall.HPyBoolFromBool);
        Python3Core core = context.getContext();
        return GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(v ? core.getTrue() : core.getFalse()));
    }

    public long ctxAsStructObject(long h) {
        increment(HPyJNIUpcall.HPyAsStructObject);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructLegacy(long h) {
        increment(HPyJNIUpcall.HPyAsStructLegacy);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructType(long h) {
        increment(HPyJNIUpcall.HPyAsStructType);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructLong(long h) {
        increment(HPyJNIUpcall.HPyAsStructLong);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructFloat(long h) {
        increment(HPyJNIUpcall.HPyAsStructFloat);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructUnicode(long h) {
        increment(HPyJNIUpcall.HPyAsStructUnicode);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructTuple(long h) {
        increment(HPyJNIUpcall.HPyAsStructTuple);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    public long ctxAsStructList(long h) {
        increment(HPyJNIUpcall.HPyAsStructList);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
        return expectPointer(HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver));
    }

    // Note: assumes that receiverHandle is not a boxed primitive value
    @SuppressWarnings("try")
    public int ctxSetItems(long receiverHandle, String name, long valueHandle) {
        increment(HPyJNIUpcall.HPySetItems);
        Object receiver = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(receiverHandle));
        Object value = context.bitsAsPythonObject(valueHandle);
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
        if (type instanceof PythonClass clazz) {
            // allocate native space
            long basicSize = clazz.getBasicSize();
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
                Object destroyFunc = clazz.getHPyDestroyFunc();
                context.createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
            Object defaultCallFunc = clazz.getHPyDefaultCallFunc();
            if (defaultCallFunc != null) {
                GraalHPyData.setHPyCallFunction(pythonObject, defaultCallFunc);
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

    @SuppressWarnings("unused")
    public long ctxTypeGenericNew(long typeHandle, long args, long nargs, long kw) {
        increment(HPyJNIUpcall.HPyTypeGenericNew);

        Object type = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));

        if (type instanceof PythonClass clazz) {

            PythonObject pythonObject;
            long basicSize = clazz.getBasicSize();
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
            Object clazz = InlinedGetClassNode.executeUncached(receiver);
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
            Object clazz = InlinedGetClassNode.executeUncached(receiver);
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
            HPyTransformExceptionToNativeNode.executeUncached(context, e);
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
            Object clazz = InlinedGetClassNode.executeUncached(receiver);

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

    @TruffleBoundary
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
            Object receiverType = InlinedGetClassNode.executeUncached(receiver);
            return PInt.intValue(LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Int).execute(receiverType) != PNone.NO_VALUE);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(context, e);
            return 0;
        }
    }

    public long ctxLongFromSizet(long value) {
        increment(HPyJNIUpcall.HPyLongFromSizet);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_FROMSIZE_T, value);
    }

    public long ctxLongFromSsizet(long value) {
        increment(HPyJNIUpcall.HPyLongFromSsizet);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_FROMSSIZE_T, value);
    }

    public long ctxLongAsSizet(long h) {
        increment(HPyJNIUpcall.HPyLongAsSizet);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_ASSIZE_T, h);
    }

    public long ctxLongAsSsizet(long h) {
        increment(HPyJNIUpcall.HPyLongAsSsizet);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_ASSSIZE_T, h);
    }

    public long ctxLongAsVoidPtr(long h) {
        increment(HPyJNIUpcall.HPyLongAsVoidPtr);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG_ASVOIDPTR, h);
    }

    public long ctxAdd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyAdd);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_ADD, h1, h2);
    }

    public long ctxSubtract(long h1, long h2) {
        increment(HPyJNIUpcall.HPySubtract);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_SUBTRACT, h1, h2);
    }

    public long ctxMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyMultiply);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_MULTIPLY, h1, h2);
    }

    public long ctxMatrixMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyMatrixMultiply);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_MATRIXMULTIPLY, h1, h2);
    }

    public long ctxFloorDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyFloorDivide);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_FLOORDIVIDE, h1, h2);
    }

    public long ctxTrueDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyTrueDivide);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_TRUEDIVIDE, h1, h2);
    }

    public long ctxRemainder(long h1, long h2) {
        increment(HPyJNIUpcall.HPyRemainder);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_REMAINDER, h1, h2);
    }

    public long ctxDivmod(long h1, long h2) {
        increment(HPyJNIUpcall.HPyDivmod);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_DIVMOD, h1, h2);
    }

    public long ctxPower(long h1, long h2, long h3) {
        increment(HPyJNIUpcall.HPyPower);
        return executeLongContextFunction(HPyContextMember.CTX_POWER, new long[]{h1, h2, h3});
    }

    public long ctxNegative(long h1) {
        increment(HPyJNIUpcall.HPyNegative);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_NEGATIVE, h1);
    }

    public long ctxPositive(long h1) {
        increment(HPyJNIUpcall.HPyPositive);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_POSITIVE, h1);
    }

    public long ctxAbsolute(long h1) {
        increment(HPyJNIUpcall.HPyAbsolute);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_ABSOLUTE, h1);
    }

    public long ctxInvert(long h1) {
        increment(HPyJNIUpcall.HPyInvert);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_INVERT, h1);
    }

    public long ctxLshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyLshift);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_LSHIFT, h1, h2);
    }

    public long ctxRshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyRshift);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_RSHIFT, h1, h2);
    }

    public long ctxAnd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyAnd);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_AND, h1, h2);
    }

    public long ctxXor(long h1, long h2) {
        increment(HPyJNIUpcall.HPyXor);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_XOR, h1, h2);
    }

    public long ctxOr(long h1, long h2) {
        increment(HPyJNIUpcall.HPyOr);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_OR, h1, h2);
    }

    public long ctxIndex(long h1) {
        increment(HPyJNIUpcall.HPyIndex);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_INDEX, h1);
    }

    public long ctxLong(long h1) {
        increment(HPyJNIUpcall.HPyLong);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LONG, h1);
    }

    public long ctxFloat(long h1) {
        increment(HPyJNIUpcall.HPyFloat);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_FLOAT, h1);
    }

    public long ctxInPlaceAdd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceAdd);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEADD, h1, h2);
    }

    public long ctxInPlaceSubtract(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceSubtract);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACESUBTRACT, h1, h2);
    }

    public long ctxInPlaceMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceMultiply);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEMULTIPLY, h1, h2);
    }

    public long ctxInPlaceMatrixMultiply(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceMatrixMultiply);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEMATRIXMULTIPLY, h1, h2);
    }

    public long ctxInPlaceFloorDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceFloorDivide);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEFLOORDIVIDE, h1, h2);
    }

    public long ctxInPlaceTrueDivide(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceTrueDivide);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACETRUEDIVIDE, h1, h2);
    }

    public long ctxInPlaceRemainder(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceRemainder);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEREMAINDER, h1, h2);
    }

    public long ctxInPlacePower(long h1, long h2, long h3) {
        increment(HPyJNIUpcall.HPyInPlacePower);
        return executeLongContextFunction(HPyContextMember.CTX_INPLACEPOWER, new long[]{h1, h2, h3});
    }

    public long ctxInPlaceLshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceLshift);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACELSHIFT, h1, h2);
    }

    public long ctxInPlaceRshift(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceRshift);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACERSHIFT, h1, h2);
    }

    public long ctxInPlaceAnd(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceAnd);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEAND, h1, h2);
    }

    public long ctxInPlaceXor(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceXor);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEXOR, h1, h2);
    }

    public long ctxInPlaceOr(long h1, long h2) {
        increment(HPyJNIUpcall.HPyInPlaceOr);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_INPLACEOR, h1, h2);
    }

    public int ctxCallableCheck(long h) {
        increment(HPyJNIUpcall.HPyCallableCheck);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_CALLABLE_CHECK, h);
    }

    public long ctxCallTupleDict(long callable, long args, long kw) {
        increment(HPyJNIUpcall.HPyCallTupleDict);
        return executeLongContextFunction(HPyContextMember.CTX_CALLTUPLEDICT, new long[]{callable, args, kw});
    }

    public void ctxFatalError(long message) {
        increment(HPyJNIUpcall.HPyFatalError);
        executeIntBinaryContextFunction(HPyContextMember.CTX_FATALERROR, message);
    }

    public void ctxErrSetString(long h_type, long message) {
        increment(HPyJNIUpcall.HPyErrSetString);
        executeIntTernaryContextFunction(HPyContextMember.CTX_ERR_SETSTRING, h_type, message);
    }

    public void ctxErrSetObject(long h_type, long h_value) {
        increment(HPyJNIUpcall.HPyErrSetObject);
        executeIntTernaryContextFunction(HPyContextMember.CTX_ERR_SETOBJECT, h_type, h_value);
    }

    public long ctxErrSetFromErrnoWithFilename(long h_type, long filename_fsencoded) {
        increment(HPyJNIUpcall.HPyErrSetFromErrnoWithFilename);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAME, h_type, filename_fsencoded);
    }

    public void ctxErrSetFromErrnoWithFilenameObjects(long h_type, long filename1, long filename2) {
        increment(HPyJNIUpcall.HPyErrSetFromErrnoWithFilenameObjects);
        executeIntContextFunction(HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS, new long[]{h_type, filename1, filename2});
    }

    public int ctxErrOccurred() {
        increment(HPyJNIUpcall.HPyErrOccurred);
        return executeIntContextFunction(HPyContextMember.CTX_ERR_OCCURRED, new long[]{});
    }

    public int ctxErrExceptionMatches(long exc) {
        increment(HPyJNIUpcall.HPyErrExceptionMatches);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_ERR_EXCEPTIONMATCHES, exc);
    }

    public void ctxErrNoMemory() {
        increment(HPyJNIUpcall.HPyErrNoMemory);
        executeIntContextFunction(HPyContextMember.CTX_ERR_NOMEMORY, new long[]{});
    }

    public void ctxErrClear() {
        increment(HPyJNIUpcall.HPyErrClear);
        executeIntContextFunction(HPyContextMember.CTX_ERR_CLEAR, new long[]{});
    }

    public long ctxErrNewException(long name, long base, long dict) {
        increment(HPyJNIUpcall.HPyErrNewException);
        return executeLongContextFunction(HPyContextMember.CTX_ERR_NEWEXCEPTION, new long[]{name, base, dict});
    }

    public long ctxErrNewExceptionWithDoc(long name, long doc, long base, long dict) {
        increment(HPyJNIUpcall.HPyErrNewExceptionWithDoc);
        return executeLongContextFunction(HPyContextMember.CTX_ERR_NEWEXCEPTIONWITHDOC, new long[]{name, doc, base, dict});
    }

    public int ctxErrWarnEx(long category, long message, long stack_level) {
        increment(HPyJNIUpcall.HPyErrWarnEx);
        return executeIntContextFunction(HPyContextMember.CTX_ERR_WARNEX, new long[]{category, message, stack_level});
    }

    public void ctxErrWriteUnraisable(long obj) {
        increment(HPyJNIUpcall.HPyErrWriteUnraisable);
        executeIntBinaryContextFunction(HPyContextMember.CTX_ERR_WRITEUNRAISABLE, obj);
    }

    public int ctxIsTrue(long h) {
        increment(HPyJNIUpcall.HPyIsTrue);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_ISTRUE, h);
    }

    public long ctxTypeFromSpec(long spec, long params) {
        increment(HPyJNIUpcall.HPyTypeFromSpec);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_TYPE_FROMSPEC, spec, params);
    }

    public long ctxGetAttr(long obj, long name) {
        increment(HPyJNIUpcall.HPyGetAttr);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_GETATTR, obj, name);
    }

    public int ctxHasAttr(long obj, long name) {
        increment(HPyJNIUpcall.HPyHasAttr);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_HASATTR, obj, name);
    }

    public int ctxHasAttrs(long obj, long name) {
        increment(HPyJNIUpcall.HPyHasAttrs);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_HASATTR_S, obj, name);
    }

    public int ctxSetAttr(long obj, long name, long value) {
        increment(HPyJNIUpcall.HPySetAttr);
        return executeIntContextFunction(HPyContextMember.CTX_SETATTR, new long[]{obj, name, value});
    }

    public int ctxSetAttrs(long obj, long name, long value) {
        increment(HPyJNIUpcall.HPySetAttrs);
        return executeIntContextFunction(HPyContextMember.CTX_SETATTR_S, new long[]{obj, name, value});
    }

    public long ctxGetItem(long obj, long key) {
        increment(HPyJNIUpcall.HPyGetItem);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_GETITEM, obj, key);
    }

    public int ctxContains(long container, long key) {
        increment(HPyJNIUpcall.HPyContains);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_CONTAINS, container, key);
    }

    public int ctxTypeIsSubtype(long sub, long type) {
        increment(HPyJNIUpcall.HPyTypeIsSubtype);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_TYPE_ISSUBTYPE, sub, type);
    }

    public long ctxRepr(long obj) {
        increment(HPyJNIUpcall.HPyRepr);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_REPR, obj);
    }

    public long ctxStr(long obj) {
        increment(HPyJNIUpcall.HPyStr);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_STR, obj);
    }

    public long ctxASCII(long obj) {
        increment(HPyJNIUpcall.HPyASCII);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_ASCII, obj);
    }

    public long ctxBytes(long obj) {
        increment(HPyJNIUpcall.HPyBytes);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES, obj);
    }

    public long ctxRichCompare(long v, long w, int op) {
        increment(HPyJNIUpcall.HPyRichCompare);
        return executeLongContextFunction(HPyContextMember.CTX_RICHCOMPARE, new Object[]{v, w, op});
    }

    public int ctxRichCompareBool(long v, long w, int op) {
        increment(HPyJNIUpcall.HPyRichCompareBool);
        return executeIntContextFunction(HPyContextMember.CTX_RICHCOMPAREBOOL, new Object[]{v, w, op});
    }

    public long ctxHash(long obj) {
        increment(HPyJNIUpcall.HPyHash);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_HASH, obj);
    }

    public int ctxBytesCheck(long h) {
        increment(HPyJNIUpcall.HPyBytesCheck);
        if (GraalHPyBoxing.isBoxedHandle(h)) {
            Object object = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(h));
            if (object instanceof PBytes) {
                return 1;
            }
            return executeIntBinaryContextFunction(HPyContextMember.CTX_BYTES_CHECK, h);
        }
        return 0;
    }

    public long ctxBytesSize(long h) {
        increment(HPyJNIUpcall.HPyBytesSize);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES_SIZE, h);
    }

    public long ctxBytesGETSIZE(long h) {
        increment(HPyJNIUpcall.HPyBytesGETSIZE);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES_GET_SIZE, h);
    }

    public long ctxBytesAsString(long h) {
        increment(HPyJNIUpcall.HPyBytesAsString);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES_ASSTRING, h);
    }

    public long ctxBytesASSTRING(long h) {
        increment(HPyJNIUpcall.HPyBytesASSTRING);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES_AS_STRING, h);
    }

    public long ctxBytesFromString(long v) {
        increment(HPyJNIUpcall.HPyBytesFromString);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_BYTES_FROMSTRING, v);
    }

    public long ctxBytesFromStringAndSize(long v, long len) {
        increment(HPyJNIUpcall.HPyBytesFromStringAndSize);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_BYTES_FROMSTRINGANDSIZE, v, len);
    }

    public long ctxUnicodeFromString(long utf8) {
        increment(HPyJNIUpcall.HPyUnicodeFromString);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_FROMSTRING, utf8);
    }

    public int ctxUnicodeCheck(long h) {
        increment(HPyJNIUpcall.HPyUnicodeCheck);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_UNICODE_CHECK, h);
    }

    public long ctxUnicodeAsASCIIString(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsASCIIString);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_ASASCIISTRING, h);
    }

    public long ctxUnicodeAsLatin1String(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsLatin1String);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_ASLATIN1STRING, h);
    }

    public long ctxUnicodeAsUTF8String(long h) {
        increment(HPyJNIUpcall.HPyUnicodeAsUTF8String);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_ASUTF8STRING, h);
    }

    public long ctxUnicodeAsUTF8AndSize(long h, long size) {
        increment(HPyJNIUpcall.HPyUnicodeAsUTF8AndSize);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_UNICODE_ASUTF8ANDSIZE, h, size);
    }

    public long ctxUnicodeDecodeFSDefault(long v) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeFSDefault);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT, v);
    }

    public long ctxUnicodeDecodeFSDefaultAndSize(long v, long size) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeFSDefaultAndSize);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_UNICODE_DECODEFSDEFAULTANDSIZE, v, size);
    }

    public long ctxUnicodeEncodeFSDefault(long h) {
        increment(HPyJNIUpcall.HPyUnicodeEncodeFSDefault);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_UNICODE_ENCODEFSDEFAULT, h);
    }

    public int ctxUnicodeReadChar(long h, long index) {
        increment(HPyJNIUpcall.HPyUnicodeReadChar);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_UNICODE_READCHAR, h, index);
    }

    public long ctxUnicodeDecodeASCII(long s, long size, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeASCII);
        return executeLongContextFunction(HPyContextMember.CTX_UNICODE_DECODEASCII, new long[]{s, size, errors});
    }

    public long ctxUnicodeDecodeLatin1(long s, long size, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeDecodeLatin1);
        return executeLongContextFunction(HPyContextMember.CTX_UNICODE_DECODELATIN1, new long[]{s, size, errors});
    }

    public long ctxUnicodeFromEncodedObject(long obj, long encoding, long errors) {
        increment(HPyJNIUpcall.HPyUnicodeFromEncodedObject);
        return executeLongContextFunction(HPyContextMember.CTX_UNICODE_FROMENCODEDOBJECT, new long[]{obj, encoding, errors});
    }

    public long ctxUnicodeSubstring(long obj, long start, long end) {
        increment(HPyJNIUpcall.HPyUnicodeSubstring);
        return executeLongContextFunction(HPyContextMember.CTX_UNICODE_SUBSTRING, new long[]{obj, start, end});
    }

    public int ctxListAppend(long h_list, long h_item) {
        increment(HPyJNIUpcall.HPyListAppend);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_LIST_APPEND, h_list, h_item);
    }

    public int ctxDictCheck(long h) {
        increment(HPyJNIUpcall.HPyDictCheck);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_DICT_CHECK, h);
    }

    public long ctxDictKeys(long h) {
        increment(HPyJNIUpcall.HPyDictKeys);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_DICT_KEYS, h);
    }

    public int ctxTupleCheck(long h) {
        increment(HPyJNIUpcall.HPyTupleCheck);
        return executeIntBinaryContextFunction(HPyContextMember.CTX_TUPLE_CHECK, h);
    }

    public int ctxSliceUnpack(long slice, long start, long stop, long step) {
        increment(HPyJNIUpcall.HPySliceUnpack);
        return executeIntContextFunction(HPyContextMember.CTX_SLICE_UNPACK, new long[]{slice, start, stop, step});
    }

    public long ctxContextVarNew(long name, long default_value) {
        increment(HPyJNIUpcall.HPyContextVarNew);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_CONTEXTVAR_NEW, name, default_value);
    }

    public long ctxContextVarSet(long context_var, long value) {
        increment(HPyJNIUpcall.HPyContextVarSet);
        return executeLongTernaryContextFunction(HPyContextMember.CTX_CONTEXTVAR_SET, context_var, value);
    }

    public long ctxImportImportModule(long name) {
        increment(HPyJNIUpcall.HPyImportImportModule);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_IMPORT_IMPORTMODULE, name);
    }

    public int ctxCapsuleIsValid(long capsule, long name) {
        increment(HPyJNIUpcall.HPyCapsuleIsValid);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_CAPSULE_ISVALID, capsule, name);
    }

    public int ctxCapsuleSet(long capsule, int key, long value) {
        increment(HPyJNIUpcall.HPyCapsuleSet);
        return executeIntContextFunction(HPyContextMember.CTX_CAPSULE_SET, new Object[]{capsule, key, value});
    }

    public long ctxFromPyObject(long obj) {
        increment(HPyJNIUpcall.HPyFromPyObject);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_FROMPYOBJECT, obj);
    }

    public long ctxAsPyObject(long h) {
        increment(HPyJNIUpcall.HPyAsPyObject);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_ASPYOBJECT, h);
    }

    public long ctxListBuilderNew(long initial_size) {
        increment(HPyJNIUpcall.HPyListBuilderNew);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LISTBUILDER_NEW, initial_size);
    }

    public void ctxListBuilderSet(long builder, long index, long h_item) {
        increment(HPyJNIUpcall.HPyListBuilderSet);
        executeIntContextFunction(HPyContextMember.CTX_LISTBUILDER_SET, new long[]{builder, index, h_item});
    }

    public long ctxListBuilderBuild(long builder) {
        increment(HPyJNIUpcall.HPyListBuilderBuild);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_LISTBUILDER_BUILD, builder);
    }

    public void ctxListBuilderCancel(long builder) {
        increment(HPyJNIUpcall.HPyListBuilderCancel);
        executeIntBinaryContextFunction(HPyContextMember.CTX_LISTBUILDER_CANCEL, builder);
    }

    public long ctxTupleBuilderNew(long initial_size) {
        increment(HPyJNIUpcall.HPyTupleBuilderNew);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_TUPLEBUILDER_NEW, initial_size);
    }

    public void ctxTupleBuilderSet(long builder, long index, long h_item) {
        increment(HPyJNIUpcall.HPyTupleBuilderSet);
        executeIntContextFunction(HPyContextMember.CTX_TUPLEBUILDER_SET, new long[]{builder, index, h_item});
    }

    public long ctxTupleBuilderBuild(long builder) {
        increment(HPyJNIUpcall.HPyTupleBuilderBuild);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_TUPLEBUILDER_BUILD, builder);
    }

    public void ctxTupleBuilderCancel(long builder) {
        increment(HPyJNIUpcall.HPyTupleBuilderCancel);
        executeIntBinaryContextFunction(HPyContextMember.CTX_TUPLEBUILDER_CANCEL, builder);
    }

    public void ctxReenterPythonExecution(long state) {
        increment(HPyJNIUpcall.HPyReenterPythonExecution);
        executeIntBinaryContextFunction(HPyContextMember.CTX_REENTERPYTHONEXECUTION, state);
    }

    public long ctxLeavePythonExecution() {
        increment(HPyJNIUpcall.HPyLeavePythonExecution);
        return executeLongContextFunction(HPyContextMember.CTX_LEAVEPYTHONEXECUTION, new long[]{});
    }

    public void ctxDump(long h) {
        increment(HPyJNIUpcall.HPyDump);
        executeIntBinaryContextFunction(HPyContextMember.CTX_DUMP, h);
    }

    public long ctxCall(long callable, long args, long lnargs, long kwnames) {
        increment(HPyJNIUpcall.HPyCall);
        // some assumptions that may be made
        assert callable != 0 && GraalHPyBoxing.isBoxedHandle(callable);
        assert kwnames == 0 || GraalHPyBoxing.isBoxedHandle(kwnames);
        assert args != 0 || lnargs == 0;
        try {
            if (!PInt.isIntRange(lnargs)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            int nargs = (int) lnargs;
            Object callableObj = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(callable));

            PKeyword[] keywords;
            Object[] argsArr = new Object[nargs];
            for (int i = 0; i < argsArr.length; i++) {
                long argBits = UNSAFE.getLong(args + i * SIZEOF_LONG);
                argsArr[i] = context.bitsAsPythonObject(argBits);
            }

            if (kwnames != 0) {
                Object kwnamesObj = context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(kwnames));
                if (kwnamesObj instanceof PTuple kwnamesTuple) {
                    int nkw = kwnamesTuple.getSequenceStorage().length();
                    Object[] kwvalues = new Object[nkw];
                    long kwvaluesPtr = args + nargs * SIZEOF_LONG;
                    for (int i = 0; i < kwvalues.length; i++) {
                        long argBits = UNSAFE.getLong(kwvaluesPtr + i * SIZEOF_LONG);
                        kwvalues[i] = context.bitsAsPythonObject(argBits);
                    }
                    keywords = HPyPackKeywordArgsNodeGen.getUncached().execute(null, kwvalues, kwnamesTuple);
                } else {
                    // fatal error (CPython would just cause a memory corruption)
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                keywords = PKeyword.EMPTY_KEYWORDS;
            }

            Object result = CallNode.getUncached().execute(callableObj, argsArr, keywords);
            return context.pythonObjectAsBits(result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return 0;
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", HPyJNIUpcall.HPyCall.getName());
        }
    }

    public long ctxCallMethod(long name, long args, long nargs, long kwnames) {
        increment(HPyJNIUpcall.HPyCallMethod);
        return executeLongContextFunction(HPyContextMember.CTX_CALLMETHOD, new long[]{name, args, nargs, kwnames});
    }

    public int ctxDelItem(long obj, long key) {
        increment(HPyJNIUpcall.HPyDelItem);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_DELITEM, obj, key);
    }

    public int ctxDelItemi(long obj, long idx) {
        increment(HPyJNIUpcall.HPyDelItemi);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_DELITEM_I, obj, idx);
    }

    public int ctxDelItems(long obj, long utf8_key) {
        increment(HPyJNIUpcall.HPyDelItems);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_DELITEM_S, obj, utf8_key);
    }

    public int ctxTypeGetBuiltinShape(long h_type) {
        increment(HPyJNIUpcall.HPyTypeGetBuiltinShape);
        assert GraalHPyBoxing.isBoxedHandle(h_type);
        Object typeObject = context.bitsAsPythonObject(h_type);
        int result = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(typeObject);
        if (result == -2) {
            return HPyRaiseNode.raiseIntUncached(context, -2, TypeError, ErrorMessages.S_MUST_BE_S, "arg", "type");
        }
        assert GraalHPyDef.isValidBuiltinShape(result);
        return result;
    }

    public long ctxDictCopy(long h) {
        increment(HPyJNIUpcall.HPyDictCopy);
        return executeLongBinaryContextFunction(HPyContextMember.CTX_DICT_COPY, h);
    }

    public long ctxCompiles(long utf8_source, long utf8_filename, int kind) {
        increment(HPyJNIUpcall.HPyCompiles);
        return executeLongContextFunction(HPyContextMember.CTX_COMPILE_S, new Object[]{utf8_source, utf8_filename, kind});
    }

    public long ctxEvalCode(long code, long globals, long locals) {
        increment(HPyJNIUpcall.HPyEvalCode);
        return executeLongContextFunction(HPyContextMember.CTX_EVALCODE, new long[]{code, globals, locals});
    }

    public int ctxSetCallFunction(long h, long func) {
        increment(HPyJNIUpcall.HPySetCallFunction);
        return executeIntTernaryContextFunction(HPyContextMember.CTX_SETCALLFUNCTION, h, func);
    }
    // {{end ctx funcs}}

    private long createConstant(Object value) {
        return context.getHPyContextHandle(value);
    }

    private long createBuiltinsConstant() {
        return createConstant(GetOrCreateDictNode.getUncached().execute(context.getContext().getBuiltins()));
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
        // @formatter:off
        // Checkstyle: stop
        // DO NOT EDIT THIS PART!
        // This part is automatically generated by hpy.tools.autogen.graalpy.autogen_ctx_handles_init
        long[] ctxHandles = new long[244];
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
        ctxHandles[238] = createTypeConstant(PythonBuiltinClassType.PComplex);
        ctxHandles[239] = createTypeConstant(PythonBuiltinClassType.PBytes);
        ctxHandles[240] = createTypeConstant(PythonBuiltinClassType.PMemoryView);
        ctxHandles[241] = createTypeConstant(PythonBuiltinClassType.Capsule);
        ctxHandles[242] = createTypeConstant(PythonBuiltinClassType.PSlice);
        ctxHandles[243] = createBuiltinsConstant();
        return ctxHandles;

        // @formatter:on
        // Checkstyle: resume
        // {{end ctx handles array}}
    }

    private Object executeContextFunction(HPyContextMember member, long[] arguments) {
        HPyContextSignature signature = member.getSignature();
        HPyContextSignatureType[] argTypes = signature.parameterTypes();
        assert arguments.length == argTypes.length - 1;
        Object[] argCast = new Object[argTypes.length];
        argCast[0] = context;
        for (int i = 1; i < argCast.length; i++) {
            argCast[i] = convertLongArg(argTypes[i], arguments[i - 1]);
        }
        return GraalHPyContextFunction.getUncached(member).execute(argCast);
    }

    private Object executeBinaryContextFunction(HPyContextMember member, long larg0) {
        HPyContextSignature signature = member.getSignature();
        HPyContextSignatureType[] argTypes = signature.parameterTypes();
        assert argTypes.length - 1 == 1;
        Object arg0 = convertLongArg(argTypes[1], larg0);
        return ((HPyBinaryContextFunction) GraalHPyContextFunction.getUncached(member)).execute(context, arg0);
    }

    private Object executeTernaryContextFunction(HPyContextMember member, long larg0, long larg1) {
        HPyContextSignature signature = member.getSignature();
        HPyContextSignatureType[] argTypes = signature.parameterTypes();
        assert argTypes.length - 1 == 2;
        Object arg0 = convertLongArg(argTypes[1], larg0);
        Object arg1 = convertLongArg(argTypes[2], larg1);
        return ((HPyTernaryContextFunction) GraalHPyContextFunction.getUncached(member)).execute(context, arg0, arg1);
    }

    private Object executeContextFunction(HPyContextMember member, Object[] arguments) {
        HPyContextSignature signature = member.getSignature();
        HPyContextSignatureType[] argTypes = signature.parameterTypes();
        assert arguments.length == argTypes.length - 1;
        Object[] argCast = new Object[argTypes.length];
        argCast[0] = context;
        for (int i = 1; i < argCast.length; i++) {
            argCast[i] = convertArg(argTypes[i], arguments[i - 1]);
        }
        return GraalHPyContextFunction.getUncached(member).execute(argCast);
    }

    private long executeLongContextFunction(HPyContextMember member, long[] arguments) {
        try {
            Object result = executeContextFunction(member, arguments);
            return convertLongRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getLongErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private long executeLongBinaryContextFunction(HPyContextMember member, long arg0) {
        try {
            Object result = executeBinaryContextFunction(member, arg0);
            return convertLongRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getLongErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private long executeLongTernaryContextFunction(HPyContextMember member, long arg0, long arg1) {
        try {
            Object result = executeTernaryContextFunction(member, arg0, arg1);
            return convertLongRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getLongErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private int executeIntContextFunction(HPyContextMember member, long[] arguments) {
        try {
            Object result = executeContextFunction(member, arguments);
            return convertIntRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getIntErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    @TruffleBoundary
    private int executeIntBinaryContextFunction(HPyContextMember member, long arg0) {
        try {
            Object result = executeBinaryContextFunction(member, arg0);
            return convertIntRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getIntErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private int executeIntTernaryContextFunction(HPyContextMember member, long arg0, long arg1) {
        try {
            Object result = executeTernaryContextFunction(member, arg0, arg1);
            return convertIntRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getIntErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private long executeLongContextFunction(HPyContextMember member, Object[] arguments) {
        try {
            Object result = executeContextFunction(member, arguments);
            return convertLongRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getLongErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private int executeIntContextFunction(HPyContextMember member, Object[] arguments) {
        try {
            Object result = executeContextFunction(member, arguments);
            return convertIntRet(member.getSignature().returnType(), result);
        } catch (PException e) {
            HPyTransformExceptionToNativeNode.executeUncached(e);
            return getIntErrorValue(member.getSignature().returnType());
        } catch (Throwable t) {
            throw checkThrowableBeforeNative(t, "HPy context function", member.getName());
        }
    }

    private Object convertLongArg(HPyContextSignatureType type, long argBits) {
        return switch (type) {
            case HPy, HPyThreadState, HPyListBuilder, HPyTupleBuilder -> context.bitsAsPythonObject(argBits);
            case Int, HPy_UCS4 -> -1;
            case Int64_t, Uint64_t, Size_t, HPy_ssize_t, HPy_hash_t, VoidPtr, CVoid -> argBits;
            case Int32_t, Uint32_t -> argBits & 0xFFFFFFFFL;
            case CharPtr, ConstCharPtr -> new NativePointer(argBits);
            case CDouble -> throw CompilerDirectives.shouldNotReachHere("invalid argument handle");
            case HPyModuleDefPtr, HPyType_SpecPtr, HPyType_SpecParamPtr, HPy_ssize_tPtr, Cpy_PyObjectPtr, ConstHPyPtr, HPyPtr, HPyCallFunctionPtr ->
                PCallHPyFunctionNodeGen.getUncached().call(context, GraalHPyNativeSymbol.GRAAL_HPY_LONG2PTR, argBits);
            default -> throw CompilerDirectives.shouldNotReachHere("unsupported arg type");
        };
    }

    private Object convertArg(HPyContextSignatureType type, Object arg) {
        return switch (type) {
            case Int, Int32_t, Uint32_t, HPy_UCS4, _HPyCapsule_key, HPy_SourceKind -> (Integer) arg;
            default -> convertLongArg(type, (Long) arg);
        };
    }

    private long convertLongRet(HPyContextSignatureType type, Object result) {
        return switch (type) {
            case HPy, HPyThreadState, HPyListBuilder, HPyTupleBuilder -> GraalHPyBoxing.boxHandle(context.getHPyHandleForObject(result));
            case VoidPtr, CharPtr, ConstCharPtr, Cpy_PyObjectPtr -> coerceToPointer(result);
            case Int64_t, Uint64_t, Size_t, HPy_ssize_t, HPy_hash_t -> (Long) HPyAsNativeInt64NodeGen.getUncached().execute(result);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    private int convertIntRet(HPyContextSignatureType type, Object result) {
        return switch (type) {
            case Int, Int32_t, Uint32_t, HPy_UCS4 -> (int) result;
            case CVoid -> 0;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    private long getLongErrorValue(HPyContextSignatureType type) {
        return switch (type) {
            case HPy, VoidPtr, CharPtr, ConstCharPtr, Cpy_PyObjectPtr, HPyListBuilder, HPyTupleBuilder, HPyThreadState -> 0;
            case Int64_t, Uint64_t, Size_t, HPy_ssize_t, HPy_hash_t -> -1L;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    private int getIntErrorValue(HPyContextSignatureType type) {
        return switch (type) {
            case Int, Int32_t, Uint32_t, HPy_UCS4 -> -1;
            case CVoid -> 0;
            case HPyType_BuiltinShape -> -2;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }
}
