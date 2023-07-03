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
package com.oracle.graal.python.builtins.objects.cext.hpy.llvm;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_ELIPSIS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NONE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NOT_IMPLEMENTED;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.HashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCallHelperFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyDummyToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsContextNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextMember;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.cext.hpy.llvm.GraalHPyLLVMNodesFactory.HPyLLVMFromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * This object is used to override specific native upcall pointers in the HPyContext. This is
 * queried for every member of HPyContext by {@code graal_hpy_context_to_native}, and overrides the
 * original values (which are NFI closures for functions in {@code hpy.c}, subsequently calling into
 * {@link GraalHPyContextFunctions}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class GraalHPyLLVMContext extends GraalHPyNativeContext {

    private static final String J_NAME = "HPy Universal ABI (GraalVM LLVM backend)";
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyLLVMContext.class);

    /** A resolving cache from Java string to HPyContextMember */
    public static HashMap<String, HPyContextMember> contextMembersByName;

    private final int[] counts;

    /** the native type ID of C struct 'HPyContext' */
    @CompilationFinal Object hpyContextNativeTypeID;

    /** the native type ID of C struct 'HPy' */
    @CompilationFinal Object hpyNativeTypeID;
    @CompilationFinal Object hpyFieldNativeTypeID;
    @CompilationFinal Object hpyArrayNativeTypeID;
    @CompilationFinal Object setNativeSpaceFunction;

    @CompilationFinal long wcharSize = -1;

    @CompilationFinal(dimensions = 1) private final Object[] hpyContextMembers;

    Object nativePointer;

    public GraalHPyLLVMContext(GraalHPyContext context, boolean traceUpcalls) {
        super(context, traceUpcalls);
        Object[] ctxMembers = createMembers(tsLiteral(J_NAME));
        if (traceUpcalls) {
            this.counts = new int[HPyContextMember.VALUES.length];
            /*
             * For upcall tracing, each executable member is wrapped into an HPyExecuteWrapper which
             * does the tracing
             */
            for (int i = 0; i < ctxMembers.length; i++) {
                Object m = ctxMembers[i];
                if (m instanceof HPyExecuteWrapper executeWrapper) {
                    ctxMembers[i] = new HPyExecuteWrapperTraceUpcall(this.counts, i, executeWrapper);
                }
            }
        } else {
            this.counts = null;
        }
        // This will assign handles to the remaining context constants
        for (Object member : ctxMembers) {
            if (member instanceof GraalHPyHandle handle) {
                int id = handle.getIdUncached(context);
                assert id > 0 && id < GraalHPyContext.IMMUTABLE_HANDLE_COUNT;
                assert id > GraalHPyBoxing.SINGLETON_HANDLE_MAX ||
                        context.getHPyHandleForObject(handle.getDelegate()) == id;
            }
        }
        this.hpyContextMembers = ctxMembers;
    }

    void setHPyContextNativeType(Object nativeType) {
        this.hpyContextNativeTypeID = nativeType;
    }

    void setHPyNativeType(Object hpyNativeTypeID) {
        assert this.hpyNativeTypeID == null : "setting HPy native type ID a second time";
        this.hpyNativeTypeID = hpyNativeTypeID;
    }

    public Object getHPyNativeType() {
        assert this.hpyNativeTypeID != null : "HPy native type ID not available";
        return hpyNativeTypeID;
    }

    public Object getHPyFieldNativeType() {
        assert this.hpyNativeTypeID != null : "HPyField native type ID not available";
        return hpyFieldNativeTypeID;
    }

    public void setHPyFieldNativeType(Object hpyFieldNativeTypeID) {
        this.hpyFieldNativeTypeID = hpyFieldNativeTypeID;
    }

    void setHPyArrayNativeType(Object hpyArrayNativeTypeID) {
        assert this.hpyArrayNativeTypeID == null : "setting HPy* native type ID a second time";
        this.hpyArrayNativeTypeID = hpyArrayNativeTypeID;
    }

    public Object getHPyArrayNativeType() {
        assert this.hpyArrayNativeTypeID != null : "HPy* native type ID not available";
        return hpyArrayNativeTypeID;
    }

    protected long getWcharSize() {
        assert wcharSize > 0;
        return wcharSize;
    }

    @Override
    protected String getName() {
        return J_NAME;
    }

    /**
     * Load {@code libhpy} with LLVM and return the library object.
     */
    public static Object loadLLVMLibrary(PythonContext context) throws IOException {
        CompilerAsserts.neverPartOfCompilation();
        Env env = context.getEnv();
        TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
        // e.g. "libhpy-native.so"
        TruffleFile capiFile = homePath.resolve(context.getLLVMSupportExt("libhpy"));
        try {
            LOGGER.fine("Loading HPy LLVM backend from " + capiFile);
            SourceBuilder capiSrcBuilder = Source.newBuilder(J_LLVM_LANGUAGE, capiFile);
            if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                capiSrcBuilder.internal(true);
            }
            return context.getEnv().parseInternal(capiSrcBuilder.build()).call();
        } catch (RuntimeException e) {
            LOGGER.severe(String.format("Fatal error occurred when loading %s", capiFile));
            /*
             * Just loading the library is not expected to throw any legitimate exceptions because
             * it does not have any 'ctors' that could raise, e.g., a Python exception. So, any
             * exception is considered to be fatal.
             */
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    protected Object loadExtensionLibrary(Node location, PythonContext context, TruffleString name, TruffleString path) throws ImportException, IOException {
        CompilerAsserts.neverPartOfCompilation();
        return CExtContext.loadLLVMLibrary(location, context, name, path);
    }

    @Override
    protected Object initHPyModule(Object llvmLibrary, TruffleString initFuncName, TruffleString name, TruffleString path, boolean debug)
                    throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        CompilerAsserts.neverPartOfCompilation();
        Object initFunction;
        InteropLibrary lib = InteropLibrary.getUncached(llvmLibrary);
        if (lib.isMemberReadable(llvmLibrary, initFuncName.toJavaStringUncached())) {
            try {
                initFunction = lib.readMember(llvmLibrary, initFuncName.toJavaStringUncached());
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        } else {
            throw new ImportException(null, name, path, ErrorMessages.CANNOT_INITIALIZE_EXT_NO_ENTRY, name, path, initFuncName);
        }
        /*
         * LLVM always answers message 'isExecutable' correctly. If the pointer object is not
         * executable, this most certainly means that the loaded library does not contain bitcode,
         * and so we fail.
         */
        if (!InteropLibrary.getUncached().isExecutable(initFunction)) {
            throw new ImportException(null, name, path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, path);
        }
        return InteropLibrary.getUncached().execute(initFunction, this);
    }

    @Override
    protected void initNativeContext() throws InteropException {
        Object hpyLibrary = context.getLLVMLibrary();
        InteropLibrary interopLibrary = InteropLibrary.getFactory().getUncached(hpyLibrary);
        interopLibrary.invokeMember(hpyLibrary, "graal_hpy_init", context, new GraalHPyInitObject(this));
    }

    @Override
    protected void initNativeFastPaths() {
        throw CompilerDirectives.shouldNotReachHere("");
    }

    @Override
    protected HPyUpcall[] getUpcalls() {
        return HPyContextMember.VALUES;
    }

    @Override
    protected int[] getUpcallCounts() {
        return counts;
    }

    @Override
    public long createNativeArguments(Object[] delegate, InteropLibrary lib) {
        return 0;
    }

    @Override
    protected void finalizeNativeContext() {

    }

    @Override
    public void freeNativeArgumentsArray(int nargs) {
        // nothing to do
    }

    @Override
    public void initHPyDebugContext() throws ApiInitException {
        // debug mode is currently not available with the LLVM backend
        throw new ApiInitException(null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
    }

    @Override
    public PythonModule getHPyDebugModule() throws ImportException {
        // debug mode is currently not available with the LLVM backend
        throw new ImportException(null, null, null, ErrorMessages.HPY_DEBUG_MODE_NOT_AVAILABLE);
    }

    @Override
    protected void setNativeCache(long cachePtr) {
        assert useNativeFastPaths();
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, cachePtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    public HPyCallHelperFunctionNode createCallHelperFunctionNode() {
        return GraalHPyLLVMCallHelperFunctionNodeGen.create();
    }

    @Override
    public HPyCallHelperFunctionNode getUncachedCallHelperFunctionNode() {
        return GraalHPyLLVMCallHelperFunctionNodeGen.getUncached();
    }

    @Override
    public HPyFromCharPointerNode createFromCharPointerNode() {
        return HPyLLVMFromCharPointerNodeGen.create();
    }

    @Override
    public HPyFromCharPointerNode getUncachedFromCharPointerNode() {
        return HPyLLVMFromCharPointerNodeGen.getUncached();
    }

    @ExportMessage
    boolean isPointer() {
        return nativePointer != null;
    }

    @ExportMessage(limit = "1")
    long asPointer(@CachedLibrary("this.nativePointer") InteropLibrary lib) throws UnsupportedMessageException {
        if (isPointer()) {
            return lib.asPointer(nativePointer);
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
        CompilerDirectives.transferToInterpreter();
        assert !isPointer();
        assert PythonLanguage.get(null).getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.LLVM;
        nativePointer = PCallHPyFunctionNodeGen.getUncached().call(context, GRAAL_HPY_CONTEXT_TO_NATIVE, this);
    }

    private static Object createConstant(Object value) {
        return GraalHPyHandle.create(value);
    }

    private static Object createSingletonConstant(Object value, int handle) {
        return GraalHPyHandle.createSingleton(value, handle);
    }

    private Object createTypeConstant(PythonBuiltinClassType value) {
        return GraalHPyHandle.create(context.getContext().lookupType(value));
    }

    private static HPyExecuteWrapper createContextFunction(HPyContextMember member) {
        return new HPyExecuteWrapper(member);
    }

    private Object[] createMembers(TruffleString name) {
        Object[] members = new Object[HPyContextMember.VALUES.length];

        members[HPyContextMember.NAME.ordinal()] = new CStringWrapper(name);
        members[HPyContextMember.CTX_VERSION.ordinal()] = 1;

        // {{start llvm ctx init}}
        // @formatter:off
        // Checkstyle: stop
        // DO NOT EDIT THIS PART!
        // This part is automatically generated by hpy.tools.autogen.graalpy.autogen_ctx_llvm_init
        members[HPyContextMember.H_NONE.ordinal()] = createSingletonConstant(PNone.NONE, SINGLETON_HANDLE_NONE);
        members[HPyContextMember.H_TRUE.ordinal()] = createConstant(context.getContext().getTrue());
        members[HPyContextMember.H_FALSE.ordinal()] = createConstant(context.getContext().getFalse());
        members[HPyContextMember.H_NOTIMPLEMENTED.ordinal()] = createSingletonConstant(PNotImplemented.NOT_IMPLEMENTED, SINGLETON_HANDLE_NOT_IMPLEMENTED);
        members[HPyContextMember.H_ELLIPSIS.ordinal()] = createSingletonConstant(PEllipsis.INSTANCE, SINGLETON_HANDLE_ELIPSIS);
        members[HPyContextMember.H_BASEEXCEPTION.ordinal()] = createTypeConstant(PythonBuiltinClassType.PBaseException);
        members[HPyContextMember.H_EXCEPTION.ordinal()] = createTypeConstant(PythonBuiltinClassType.Exception);
        members[HPyContextMember.H_STOPASYNCITERATION.ordinal()] = createTypeConstant(PythonBuiltinClassType.StopAsyncIteration);
        members[HPyContextMember.H_STOPITERATION.ordinal()] = createTypeConstant(PythonBuiltinClassType.StopIteration);
        members[HPyContextMember.H_GENERATOREXIT.ordinal()] = createTypeConstant(PythonBuiltinClassType.GeneratorExit);
        members[HPyContextMember.H_ARITHMETICERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ArithmeticError);
        members[HPyContextMember.H_LOOKUPERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.LookupError);
        members[HPyContextMember.H_ASSERTIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.AssertionError);
        members[HPyContextMember.H_ATTRIBUTEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.AttributeError);
        members[HPyContextMember.H_BUFFERERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.BufferError);
        members[HPyContextMember.H_EOFERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.EOFError);
        members[HPyContextMember.H_FLOATINGPOINTERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.FloatingPointError);
        members[HPyContextMember.H_OSERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.OSError);
        members[HPyContextMember.H_IMPORTERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ImportError);
        members[HPyContextMember.H_MODULENOTFOUNDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ModuleNotFoundError);
        members[HPyContextMember.H_INDEXERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.IndexError);
        members[HPyContextMember.H_KEYERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.KeyError);
        members[HPyContextMember.H_KEYBOARDINTERRUPT.ordinal()] = createTypeConstant(PythonBuiltinClassType.KeyboardInterrupt);
        members[HPyContextMember.H_MEMORYERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.MemoryError);
        members[HPyContextMember.H_NAMEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.NameError);
        members[HPyContextMember.H_OVERFLOWERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.OverflowError);
        members[HPyContextMember.H_RUNTIMEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.RuntimeError);
        members[HPyContextMember.H_RECURSIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.RecursionError);
        members[HPyContextMember.H_NOTIMPLEMENTEDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.NotImplementedError);
        members[HPyContextMember.H_SYNTAXERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.SyntaxError);
        members[HPyContextMember.H_INDENTATIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.IndentationError);
        members[HPyContextMember.H_TABERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.TabError);
        members[HPyContextMember.H_REFERENCEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ReferenceError);
        members[HPyContextMember.H_SYSTEMERROR.ordinal()] = createTypeConstant(SystemError);
        members[HPyContextMember.H_SYSTEMEXIT.ordinal()] = createTypeConstant(PythonBuiltinClassType.SystemExit);
        members[HPyContextMember.H_TYPEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.TypeError);
        members[HPyContextMember.H_UNBOUNDLOCALERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnboundLocalError);
        members[HPyContextMember.H_UNICODEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnicodeError);
        members[HPyContextMember.H_UNICODEENCODEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnicodeEncodeError);
        members[HPyContextMember.H_UNICODEDECODEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnicodeDecodeError);
        members[HPyContextMember.H_UNICODETRANSLATEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnicodeTranslateError);
        members[HPyContextMember.H_VALUEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ValueError);
        members[HPyContextMember.H_ZERODIVISIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ZeroDivisionError);
        members[HPyContextMember.H_BLOCKINGIOERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.BlockingIOError);
        members[HPyContextMember.H_BROKENPIPEERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.BrokenPipeError);
        members[HPyContextMember.H_CHILDPROCESSERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ChildProcessError);
        members[HPyContextMember.H_CONNECTIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ConnectionError);
        members[HPyContextMember.H_CONNECTIONABORTEDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ConnectionAbortedError);
        members[HPyContextMember.H_CONNECTIONREFUSEDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ConnectionRefusedError);
        members[HPyContextMember.H_CONNECTIONRESETERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ConnectionResetError);
        members[HPyContextMember.H_FILEEXISTSERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.FileExistsError);
        members[HPyContextMember.H_FILENOTFOUNDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.FileNotFoundError);
        members[HPyContextMember.H_INTERRUPTEDERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.InterruptedError);
        members[HPyContextMember.H_ISADIRECTORYERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.IsADirectoryError);
        members[HPyContextMember.H_NOTADIRECTORYERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.NotADirectoryError);
        members[HPyContextMember.H_PERMISSIONERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.PermissionError);
        members[HPyContextMember.H_PROCESSLOOKUPERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.ProcessLookupError);
        members[HPyContextMember.H_TIMEOUTERROR.ordinal()] = createTypeConstant(PythonBuiltinClassType.TimeoutError);
        members[HPyContextMember.H_WARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.Warning);
        members[HPyContextMember.H_USERWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.UserWarning);
        members[HPyContextMember.H_DEPRECATIONWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.DeprecationWarning);
        members[HPyContextMember.H_PENDINGDEPRECATIONWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.PendingDeprecationWarning);
        members[HPyContextMember.H_SYNTAXWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.SyntaxWarning);
        members[HPyContextMember.H_RUNTIMEWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.RuntimeWarning);
        members[HPyContextMember.H_FUTUREWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.FutureWarning);
        members[HPyContextMember.H_IMPORTWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.ImportWarning);
        members[HPyContextMember.H_UNICODEWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.UnicodeWarning);
        members[HPyContextMember.H_BYTESWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.BytesWarning);
        members[HPyContextMember.H_RESOURCEWARNING.ordinal()] = createTypeConstant(PythonBuiltinClassType.ResourceWarning);
        members[HPyContextMember.H_BASEOBJECTTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PythonObject);
        members[HPyContextMember.H_TYPETYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PythonClass);
        members[HPyContextMember.H_BOOLTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.Boolean);
        members[HPyContextMember.H_LONGTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PInt);
        members[HPyContextMember.H_FLOATTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PFloat);
        members[HPyContextMember.H_UNICODETYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PString);
        members[HPyContextMember.H_TUPLETYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PTuple);
        members[HPyContextMember.H_LISTTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PList);
        members[HPyContextMember.H_COMPLEXTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PComplex);
        members[HPyContextMember.H_BYTESTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PBytes);
        members[HPyContextMember.H_MEMORYVIEWTYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PMemoryView);
        members[HPyContextMember.H_CAPSULETYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.Capsule);
        members[HPyContextMember.H_SLICETYPE.ordinal()] = createTypeConstant(PythonBuiltinClassType.PSlice);
        members[HPyContextMember.H_BUILTINS.ordinal()] = createBuiltinsConstant();

        members[HPyContextMember.CTX_DUP.ordinal()] = createContextFunction(HPyContextMember.CTX_DUP);
        members[HPyContextMember.CTX_CLOSE.ordinal()] = createContextFunction(HPyContextMember.CTX_CLOSE);
        members[HPyContextMember.CTX_LONG_FROMINT32_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMINT32_T);
        members[HPyContextMember.CTX_LONG_FROMUINT32_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMUINT32_T);
        members[HPyContextMember.CTX_LONG_FROMINT64_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMINT64_T);
        members[HPyContextMember.CTX_LONG_FROMUINT64_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMUINT64_T);
        members[HPyContextMember.CTX_LONG_FROMSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMSIZE_T);
        members[HPyContextMember.CTX_LONG_FROMSSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMSSIZE_T);
        members[HPyContextMember.CTX_LONG_ASINT32_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASINT32_T);
        members[HPyContextMember.CTX_LONG_ASUINT32_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUINT32_T);
        members[HPyContextMember.CTX_LONG_ASUINT32_TMASK.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUINT32_TMASK);
        members[HPyContextMember.CTX_LONG_ASINT64_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASINT64_T);
        members[HPyContextMember.CTX_LONG_ASUINT64_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUINT64_T);
        members[HPyContextMember.CTX_LONG_ASUINT64_TMASK.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUINT64_TMASK);
        members[HPyContextMember.CTX_LONG_ASSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASSIZE_T);
        members[HPyContextMember.CTX_LONG_ASSSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASSSIZE_T);
        members[HPyContextMember.CTX_LONG_ASVOIDPTR.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASVOIDPTR);
        members[HPyContextMember.CTX_LONG_ASDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASDOUBLE);
        members[HPyContextMember.CTX_FLOAT_FROMDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOAT_FROMDOUBLE);
        members[HPyContextMember.CTX_FLOAT_ASDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOAT_ASDOUBLE);
        members[HPyContextMember.CTX_BOOL_FROMBOOL.ordinal()] = createContextFunction(HPyContextMember.CTX_BOOL_FROMBOOL);
        members[HPyContextMember.CTX_LENGTH.ordinal()] = createContextFunction(HPyContextMember.CTX_LENGTH);
        members[HPyContextMember.CTX_NUMBER_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_NUMBER_CHECK);
        members[HPyContextMember.CTX_ADD.ordinal()] = createContextFunction(HPyContextMember.CTX_ADD);
        members[HPyContextMember.CTX_SUBTRACT.ordinal()] = createContextFunction(HPyContextMember.CTX_SUBTRACT);
        members[HPyContextMember.CTX_MULTIPLY.ordinal()] = createContextFunction(HPyContextMember.CTX_MULTIPLY);
        members[HPyContextMember.CTX_MATRIXMULTIPLY.ordinal()] = createContextFunction(HPyContextMember.CTX_MATRIXMULTIPLY);
        members[HPyContextMember.CTX_FLOORDIVIDE.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOORDIVIDE);
        members[HPyContextMember.CTX_TRUEDIVIDE.ordinal()] = createContextFunction(HPyContextMember.CTX_TRUEDIVIDE);
        members[HPyContextMember.CTX_REMAINDER.ordinal()] = createContextFunction(HPyContextMember.CTX_REMAINDER);
        members[HPyContextMember.CTX_DIVMOD.ordinal()] = createContextFunction(HPyContextMember.CTX_DIVMOD);
        members[HPyContextMember.CTX_POWER.ordinal()] = createContextFunction(HPyContextMember.CTX_POWER);
        members[HPyContextMember.CTX_NEGATIVE.ordinal()] = createContextFunction(HPyContextMember.CTX_NEGATIVE);
        members[HPyContextMember.CTX_POSITIVE.ordinal()] = createContextFunction(HPyContextMember.CTX_POSITIVE);
        members[HPyContextMember.CTX_ABSOLUTE.ordinal()] = createContextFunction(HPyContextMember.CTX_ABSOLUTE);
        members[HPyContextMember.CTX_INVERT.ordinal()] = createContextFunction(HPyContextMember.CTX_INVERT);
        members[HPyContextMember.CTX_LSHIFT.ordinal()] = createContextFunction(HPyContextMember.CTX_LSHIFT);
        members[HPyContextMember.CTX_RSHIFT.ordinal()] = createContextFunction(HPyContextMember.CTX_RSHIFT);
        members[HPyContextMember.CTX_AND.ordinal()] = createContextFunction(HPyContextMember.CTX_AND);
        members[HPyContextMember.CTX_XOR.ordinal()] = createContextFunction(HPyContextMember.CTX_XOR);
        members[HPyContextMember.CTX_OR.ordinal()] = createContextFunction(HPyContextMember.CTX_OR);
        members[HPyContextMember.CTX_INDEX.ordinal()] = createContextFunction(HPyContextMember.CTX_INDEX);
        members[HPyContextMember.CTX_LONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG);
        members[HPyContextMember.CTX_FLOAT.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOAT);
        members[HPyContextMember.CTX_INPLACEADD.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEADD);
        members[HPyContextMember.CTX_INPLACESUBTRACT.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACESUBTRACT);
        members[HPyContextMember.CTX_INPLACEMULTIPLY.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEMULTIPLY);
        members[HPyContextMember.CTX_INPLACEMATRIXMULTIPLY.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEMATRIXMULTIPLY);
        members[HPyContextMember.CTX_INPLACEFLOORDIVIDE.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEFLOORDIVIDE);
        members[HPyContextMember.CTX_INPLACETRUEDIVIDE.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACETRUEDIVIDE);
        members[HPyContextMember.CTX_INPLACEREMAINDER.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEREMAINDER);
        members[HPyContextMember.CTX_INPLACEPOWER.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEPOWER);
        members[HPyContextMember.CTX_INPLACELSHIFT.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACELSHIFT);
        members[HPyContextMember.CTX_INPLACERSHIFT.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACERSHIFT);
        members[HPyContextMember.CTX_INPLACEAND.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEAND);
        members[HPyContextMember.CTX_INPLACEXOR.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEXOR);
        members[HPyContextMember.CTX_INPLACEOR.ordinal()] = createContextFunction(HPyContextMember.CTX_INPLACEOR);
        members[HPyContextMember.CTX_CALLABLE_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_CALLABLE_CHECK);
        members[HPyContextMember.CTX_CALLTUPLEDICT.ordinal()] = createContextFunction(HPyContextMember.CTX_CALLTUPLEDICT);
        members[HPyContextMember.CTX_CALL.ordinal()] = createContextFunction(HPyContextMember.CTX_CALL);
        members[HPyContextMember.CTX_CALLMETHOD.ordinal()] = createContextFunction(HPyContextMember.CTX_CALLMETHOD);
        members[HPyContextMember.CTX_FATALERROR.ordinal()] = createContextFunction(HPyContextMember.CTX_FATALERROR);
        members[HPyContextMember.CTX_ERR_SETSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_SETSTRING);
        members[HPyContextMember.CTX_ERR_SETOBJECT.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_SETOBJECT);
        members[HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAME.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAME);
        members[HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS);
        members[HPyContextMember.CTX_ERR_OCCURRED.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_OCCURRED);
        members[HPyContextMember.CTX_ERR_EXCEPTIONMATCHES.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_EXCEPTIONMATCHES);
        members[HPyContextMember.CTX_ERR_NOMEMORY.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_NOMEMORY);
        members[HPyContextMember.CTX_ERR_CLEAR.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_CLEAR);
        members[HPyContextMember.CTX_ERR_NEWEXCEPTION.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_NEWEXCEPTION);
        members[HPyContextMember.CTX_ERR_NEWEXCEPTIONWITHDOC.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_NEWEXCEPTIONWITHDOC);
        members[HPyContextMember.CTX_ERR_WARNEX.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_WARNEX);
        members[HPyContextMember.CTX_ERR_WRITEUNRAISABLE.ordinal()] = createContextFunction(HPyContextMember.CTX_ERR_WRITEUNRAISABLE);
        members[HPyContextMember.CTX_ISTRUE.ordinal()] = createContextFunction(HPyContextMember.CTX_ISTRUE);
        members[HPyContextMember.CTX_TYPE_FROMSPEC.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_FROMSPEC);
        members[HPyContextMember.CTX_TYPE_GENERICNEW.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_GENERICNEW);
        members[HPyContextMember.CTX_GETATTR.ordinal()] = createContextFunction(HPyContextMember.CTX_GETATTR);
        members[HPyContextMember.CTX_GETATTR_S.ordinal()] = createContextFunction(HPyContextMember.CTX_GETATTR_S);
        members[HPyContextMember.CTX_HASATTR.ordinal()] = createContextFunction(HPyContextMember.CTX_HASATTR);
        members[HPyContextMember.CTX_HASATTR_S.ordinal()] = createContextFunction(HPyContextMember.CTX_HASATTR_S);
        members[HPyContextMember.CTX_SETATTR.ordinal()] = createContextFunction(HPyContextMember.CTX_SETATTR);
        members[HPyContextMember.CTX_SETATTR_S.ordinal()] = createContextFunction(HPyContextMember.CTX_SETATTR_S);
        members[HPyContextMember.CTX_GETITEM.ordinal()] = createContextFunction(HPyContextMember.CTX_GETITEM);
        members[HPyContextMember.CTX_GETITEM_I.ordinal()] = createContextFunction(HPyContextMember.CTX_GETITEM_I);
        members[HPyContextMember.CTX_GETITEM_S.ordinal()] = createContextFunction(HPyContextMember.CTX_GETITEM_S);
        members[HPyContextMember.CTX_CONTAINS.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTAINS);
        members[HPyContextMember.CTX_SETITEM.ordinal()] = createContextFunction(HPyContextMember.CTX_SETITEM);
        members[HPyContextMember.CTX_SETITEM_I.ordinal()] = createContextFunction(HPyContextMember.CTX_SETITEM_I);
        members[HPyContextMember.CTX_SETITEM_S.ordinal()] = createContextFunction(HPyContextMember.CTX_SETITEM_S);
        members[HPyContextMember.CTX_DELITEM.ordinal()] = createContextFunction(HPyContextMember.CTX_DELITEM);
        members[HPyContextMember.CTX_DELITEM_I.ordinal()] = createContextFunction(HPyContextMember.CTX_DELITEM_I);
        members[HPyContextMember.CTX_DELITEM_S.ordinal()] = createContextFunction(HPyContextMember.CTX_DELITEM_S);
        members[HPyContextMember.CTX_TYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE);
        members[HPyContextMember.CTX_TYPECHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPECHECK);
        members[HPyContextMember.CTX_TYPE_GETNAME.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_GETNAME);
        members[HPyContextMember.CTX_TYPE_ISSUBTYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_ISSUBTYPE);
        members[HPyContextMember.CTX_IS.ordinal()] = createContextFunction(HPyContextMember.CTX_IS);
        members[HPyContextMember.CTX_ASSTRUCT_OBJECT.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_OBJECT);
        members[HPyContextMember.CTX_ASSTRUCT_LEGACY.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_LEGACY);
        members[HPyContextMember.CTX_ASSTRUCT_TYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_TYPE);
        members[HPyContextMember.CTX_ASSTRUCT_LONG.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_LONG);
        members[HPyContextMember.CTX_ASSTRUCT_FLOAT.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_FLOAT);
        members[HPyContextMember.CTX_ASSTRUCT_UNICODE.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_UNICODE);
        members[HPyContextMember.CTX_ASSTRUCT_TUPLE.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_TUPLE);
        members[HPyContextMember.CTX_ASSTRUCT_LIST.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT_LIST);
        members[HPyContextMember.CTX_TYPE_GETBUILTINSHAPE.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_GETBUILTINSHAPE);
        members[HPyContextMember.CTX_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_NEW);
        members[HPyContextMember.CTX_REPR.ordinal()] = createContextFunction(HPyContextMember.CTX_REPR);
        members[HPyContextMember.CTX_STR.ordinal()] = createContextFunction(HPyContextMember.CTX_STR);
        members[HPyContextMember.CTX_ASCII.ordinal()] = createContextFunction(HPyContextMember.CTX_ASCII);
        members[HPyContextMember.CTX_BYTES.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES);
        members[HPyContextMember.CTX_RICHCOMPARE.ordinal()] = createContextFunction(HPyContextMember.CTX_RICHCOMPARE);
        members[HPyContextMember.CTX_RICHCOMPAREBOOL.ordinal()] = createContextFunction(HPyContextMember.CTX_RICHCOMPAREBOOL);
        members[HPyContextMember.CTX_HASH.ordinal()] = createContextFunction(HPyContextMember.CTX_HASH);
        members[HPyContextMember.CTX_BYTES_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_CHECK);
        members[HPyContextMember.CTX_BYTES_SIZE.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_SIZE);
        members[HPyContextMember.CTX_BYTES_GET_SIZE.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_GET_SIZE);
        members[HPyContextMember.CTX_BYTES_ASSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_ASSTRING);
        members[HPyContextMember.CTX_BYTES_AS_STRING.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_AS_STRING);
        members[HPyContextMember.CTX_BYTES_FROMSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_FROMSTRING);
        members[HPyContextMember.CTX_BYTES_FROMSTRINGANDSIZE.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES_FROMSTRINGANDSIZE);
        members[HPyContextMember.CTX_UNICODE_FROMSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_FROMSTRING);
        members[HPyContextMember.CTX_UNICODE_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_CHECK);
        members[HPyContextMember.CTX_UNICODE_ASASCIISTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_ASASCIISTRING);
        members[HPyContextMember.CTX_UNICODE_ASLATIN1STRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_ASLATIN1STRING);
        members[HPyContextMember.CTX_UNICODE_ASUTF8STRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_ASUTF8STRING);
        members[HPyContextMember.CTX_UNICODE_ASUTF8ANDSIZE.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_ASUTF8ANDSIZE);
        members[HPyContextMember.CTX_UNICODE_FROMWIDECHAR.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_FROMWIDECHAR);
        members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT);
        members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULTANDSIZE.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_DECODEFSDEFAULTANDSIZE);
        members[HPyContextMember.CTX_UNICODE_ENCODEFSDEFAULT.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_ENCODEFSDEFAULT);
        members[HPyContextMember.CTX_UNICODE_READCHAR.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_READCHAR);
        members[HPyContextMember.CTX_UNICODE_DECODEASCII.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_DECODEASCII);
        members[HPyContextMember.CTX_UNICODE_DECODELATIN1.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_DECODELATIN1);
        members[HPyContextMember.CTX_UNICODE_FROMENCODEDOBJECT.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_FROMENCODEDOBJECT);
        members[HPyContextMember.CTX_UNICODE_SUBSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_SUBSTRING);
        members[HPyContextMember.CTX_LIST_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_CHECK);
        members[HPyContextMember.CTX_LIST_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_NEW);
        members[HPyContextMember.CTX_LIST_APPEND.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_APPEND);
        members[HPyContextMember.CTX_DICT_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_CHECK);
        members[HPyContextMember.CTX_DICT_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_NEW);
        members[HPyContextMember.CTX_DICT_KEYS.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_KEYS);
        members[HPyContextMember.CTX_DICT_COPY.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_COPY);
        members[HPyContextMember.CTX_TUPLE_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLE_CHECK);
        members[HPyContextMember.CTX_TUPLE_FROMARRAY.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLE_FROMARRAY);
        members[HPyContextMember.CTX_SLICE_UNPACK.ordinal()] = createContextFunction(HPyContextMember.CTX_SLICE_UNPACK);
        members[HPyContextMember.CTX_IMPORT_IMPORTMODULE.ordinal()] = createContextFunction(HPyContextMember.CTX_IMPORT_IMPORTMODULE);
        members[HPyContextMember.CTX_CAPSULE_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_CAPSULE_NEW);
        members[HPyContextMember.CTX_CAPSULE_GET.ordinal()] = createContextFunction(HPyContextMember.CTX_CAPSULE_GET);
        members[HPyContextMember.CTX_CAPSULE_ISVALID.ordinal()] = createContextFunction(HPyContextMember.CTX_CAPSULE_ISVALID);
        members[HPyContextMember.CTX_CAPSULE_SET.ordinal()] = createContextFunction(HPyContextMember.CTX_CAPSULE_SET);
        members[HPyContextMember.CTX_FROMPYOBJECT.ordinal()] = createContextFunction(HPyContextMember.CTX_FROMPYOBJECT);
        members[HPyContextMember.CTX_ASPYOBJECT.ordinal()] = createContextFunction(HPyContextMember.CTX_ASPYOBJECT);
        members[HPyContextMember.CTX_LISTBUILDER_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_LISTBUILDER_NEW);
        members[HPyContextMember.CTX_LISTBUILDER_SET.ordinal()] = createContextFunction(HPyContextMember.CTX_LISTBUILDER_SET);
        members[HPyContextMember.CTX_LISTBUILDER_BUILD.ordinal()] = createContextFunction(HPyContextMember.CTX_LISTBUILDER_BUILD);
        members[HPyContextMember.CTX_LISTBUILDER_CANCEL.ordinal()] = createContextFunction(HPyContextMember.CTX_LISTBUILDER_CANCEL);
        members[HPyContextMember.CTX_TUPLEBUILDER_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLEBUILDER_NEW);
        members[HPyContextMember.CTX_TUPLEBUILDER_SET.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLEBUILDER_SET);
        members[HPyContextMember.CTX_TUPLEBUILDER_BUILD.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLEBUILDER_BUILD);
        members[HPyContextMember.CTX_TUPLEBUILDER_CANCEL.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLEBUILDER_CANCEL);
        members[HPyContextMember.CTX_TRACKER_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_TRACKER_NEW);
        members[HPyContextMember.CTX_TRACKER_ADD.ordinal()] = createContextFunction(HPyContextMember.CTX_TRACKER_ADD);
        members[HPyContextMember.CTX_TRACKER_FORGETALL.ordinal()] = createContextFunction(HPyContextMember.CTX_TRACKER_FORGETALL);
        members[HPyContextMember.CTX_TRACKER_CLOSE.ordinal()] = createContextFunction(HPyContextMember.CTX_TRACKER_CLOSE);
        members[HPyContextMember.CTX_FIELD_STORE.ordinal()] = createContextFunction(HPyContextMember.CTX_FIELD_STORE);
        members[HPyContextMember.CTX_FIELD_LOAD.ordinal()] = createContextFunction(HPyContextMember.CTX_FIELD_LOAD);
        members[HPyContextMember.CTX_REENTERPYTHONEXECUTION.ordinal()] = createContextFunction(HPyContextMember.CTX_REENTERPYTHONEXECUTION);
        members[HPyContextMember.CTX_LEAVEPYTHONEXECUTION.ordinal()] = createContextFunction(HPyContextMember.CTX_LEAVEPYTHONEXECUTION);
        members[HPyContextMember.CTX_GLOBAL_STORE.ordinal()] = createContextFunction(HPyContextMember.CTX_GLOBAL_STORE);
        members[HPyContextMember.CTX_GLOBAL_LOAD.ordinal()] = createContextFunction(HPyContextMember.CTX_GLOBAL_LOAD);
        members[HPyContextMember.CTX_DUMP.ordinal()] = createContextFunction(HPyContextMember.CTX_DUMP);
        members[HPyContextMember.CTX_COMPILE_S.ordinal()] = createContextFunction(HPyContextMember.CTX_COMPILE_S);
        members[HPyContextMember.CTX_EVALCODE.ordinal()] = createContextFunction(HPyContextMember.CTX_EVALCODE);
        members[HPyContextMember.CTX_CONTEXTVAR_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_NEW);
        members[HPyContextMember.CTX_CONTEXTVAR_GET.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_GET);
        members[HPyContextMember.CTX_CONTEXTVAR_SET.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_SET);
        members[HPyContextMember.CTX_SETCALLFUNCTION.ordinal()] = createContextFunction(HPyContextMember.CTX_SETCALLFUNCTION);

        // @formatter:on
        // Checkstyle: resume
        // {{end llvm ctx init}}

        return members;
    }

    @TruffleBoundary
    public static int getIndex(String key) {
        if (contextMembersByName == null) {
            HashMap<String, HPyContextMember> contextMemberHashMap = new HashMap<>();
            for (HPyContextMember member : HPyContextMember.VALUES) {
                contextMemberHashMap.put(member.getName(), member);
            }
            // allow races; it doesn't matter since contents will always be the same
            contextMembersByName = contextMemberHashMap;
        }
        HPyContextMember member = contextMembersByName.get(key);
        return member == null ? -1 : member.ordinal();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        String[] names = new String[HPyContextMember.VALUES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = HPyContextMember.VALUES[i].getName();
        }
        return new PythonAbstractObject.Keys(names);
    }

    @ExportMessage
    @ImportStatic(GraalHPyLLVMContext.class)
    static class IsMemberReadable {
        @Specialization(guards = "cachedKey.equals(key)", limit = "1")
        static boolean isMemberReadableCached(@SuppressWarnings("unused") GraalHPyLLVMContext context, @SuppressWarnings("unused") String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
            return cachedIdx != -1;
        }

        @Specialization(replaces = "isMemberReadableCached")
        static boolean isMemberReadable(@SuppressWarnings("unused") GraalHPyLLVMContext context, String key) {
            return getIndex(key) != -1;
        }
    }

    @ExportMessage
    Object readMember(String key,
                    @Bind("$node") Node inliningTarget,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode) {
        return readMemberNode.execute(inliningTarget, this, key);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType() {
        return hpyContextNativeTypeID;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(GraalHPyLLVMContext.class)
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(Node node, GraalHPyLLVMContext backend, String key);

        @Specialization(guards = "cachedKey == key", limit = "1")
        static Object doMemberCached(GraalHPyLLVMContext backend, String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("getIndex(key)") int cachedIdx) {
            // TODO(fa) once everything is implemented, remove this check
            if (cachedIdx != -1) {
                Object value = backend.hpyContextMembers[cachedIdx];
                if (value != null) {
                    return value;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(PythonUtils.formatJString("context function %s not yet implemented: ", key));
        }

        @Specialization(replaces = "doMemberCached")
        static Object doMember(GraalHPyLLVMContext backend, String key) {
            return doMemberCached(backend, key, key, getIndex(key));
        }
    }

    @ExportMessage
    boolean isMemberInvocable(String key,
                    @Bind("$node") Node inliningTarget,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib) {
        Object member = readMemberNode.execute(inliningTarget, this, key);
        return member != null && memberInvokeLib.isExecutable(member);
    }

    @ExportMessage
    Object invokeMember(String key, Object[] args,
                    @Bind("$node") Node inliningTarget,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        Object member = readMemberNode.execute(inliningTarget, this, key);
        assert member != null;
        /*
         * Optimization: the first argument *MUST* always be the context. If not, we can just set
         * 'this'.
         */
        args[0] = context;
        return memberInvokeLib.execute(member, args);
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("truffle-inlining")
    abstract static class HPyExecuteContextFunction extends Node {
        public abstract Object execute(Node inliningTarget, HPyContextMember member, Object[] arguments) throws ArityException;

        @Specialization(guards = "member == cachedMember", limit = "1")
        static Object doCached(Node inliningTarget, @SuppressWarnings("unused") HPyContextMember member, Object[] arguments,
                        @Cached("member") HPyContextMember cachedMember,
                        @Cached(parameters = "member") GraalHPyContextFunction contextFunctionNode,
                        @Cached(value = "createRetNode(member)", neverDefault = false) CExtToNativeNode retNode,
                        @Cached(value = "createArgNodes(member)", neverDefault = false) CExtAsPythonObjectNode[] argNodes,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, cachedMember.getSignature().parameterTypes().length);
            try {
                try {
                    Object[] argCast;
                    if (argNodes != null) {
                        argCast = new Object[argNodes.length];
                        castArguments(arguments, argCast, argNodes);
                    } else {
                        argCast = arguments;
                    }
                    Object result = contextFunctionNode.execute(argCast);
                    if (retNode != null) {
                        result = retNode.execute(result);
                    }
                    return result;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "HPy context function", cachedMember.getName());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getErrorValue(inliningTarget, cachedMember.getSignature().returnType());
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(Node inliningTarget, HPyContextMember member, Object[] arguments) throws ArityException {
            return doCached(inliningTarget, member, arguments, member, GraalHPyContextFunction.getUncached(member), getUncachedRetNode(member), getUncachedArgNodes(member),
                            HPyTransformExceptionToNativeNodeGen.getUncached());
        }

        private static void checkArity(Object[] arguments, int expectedArity) throws ArityException {
            if (arguments.length != expectedArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(expectedArity, expectedArity, arguments.length);
            }
        }

        @ExplodeLoop
        private static void castArguments(Object[] arguments, Object[] argCast, CExtAsPythonObjectNode[] argNodes) {
            for (int i = 0; i < argNodes.length; i++) {
                argCast[i] = argNodes[i] == null ? arguments[i] : argNodes[i].execute(arguments[i]);
            }
        }

        private static Object getErrorValue(Node inliningTarget, HPyContextSignatureType type) {
            return switch (type) {
                case Int, HPy_UCS4 -> -1;
                case CLong, LongLong, UnsignedLong, UnsignedLongLong, Size_t, HPy_ssize_t, HPy_hash_t -> -1L;
                case CDouble -> -1.0;
                case HPy -> GraalHPyHandle.NULL_HANDLE;
                case VoidPtr, CharPtr, ConstCharPtr, Cpy_PyObjectPtr -> PythonContext.get(inliningTarget).getNativeNull().getPtr();
                case CVoid -> PNone.NO_VALUE;
                default -> throw CompilerDirectives.shouldNotReachHere("unsupported return type");
            };
        }

        static CExtToNativeNode createRetNode(HPyContextMember member) {
            return switch (member.getSignature().returnType()) {
                case HPy, HPyThreadState -> HPyAsHandleNodeGen.create();
                case HPy_ssize_t, HPy_hash_t -> HPyAsNativeInt64NodeGen.create();
                default -> null;
            };
        }

        static CExtToNativeNode getUncachedRetNode(HPyContextMember member) {
            return switch (member.getSignature().returnType()) {
                case HPy, HPyThreadState -> HPyAsHandleNodeGen.getUncached();
                case HPy_ssize_t, HPy_hash_t -> HPyAsNativeInt64NodeGen.getUncached();
                default -> null;
            };
        }

        /*
         * Special cases: the following context functions need the bare handles. Hence, we leave the
         * conversion up to the context function impl.
         */
        private static boolean noArgumentConversion(HPyContextMember member) {
            return switch (member) {
                case CTX_CLOSE, CTX_TRACKER_ADD -> true;
                default -> false;
            };
        }

        static CExtAsPythonObjectNode[] createArgNodes(HPyContextMember member) {
            if (noArgumentConversion(member)) {
                return null;
            }
            HPyContextSignatureType[] argTypes = member.getSignature().parameterTypes();
            CExtAsPythonObjectNode[] argNodes = new CExtAsPythonObjectNode[argTypes.length];
            for (int i = 0; i < argNodes.length; i++) {
                argNodes[i] = switch (argTypes[i]) {
                    case HPyContextPtr -> HPyAsContextNodeGen.create();
                    case HPy, HPyThreadState -> HPyAsPythonObjectNodeGen.create();
                    default -> HPyDummyToJavaNode.getUncached();
                };
            }
            return argNodes;
        }

        static CExtAsPythonObjectNode[] getUncachedArgNodes(HPyContextMember member) {
            if (noArgumentConversion(member)) {
                return null;
            }
            HPyContextSignatureType[] argTypes = member.getSignature().parameterTypes();
            CExtAsPythonObjectNode[] argNodes = new CExtAsPythonObjectNode[argTypes.length];
            for (int i = 0; i < argNodes.length; i++) {
                argNodes[i] = switch (argTypes[i]) {
                    case HPyContextPtr -> HPyAsContextNodeGen.getUncached();
                    case HPy, HPyThreadState -> HPyAsPythonObjectNodeGen.getUncached();
                    default -> HPyDummyToJavaNode.getUncached();
                };
            }
            return argNodes;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapper implements TruffleObject {
        final HPyContextMember member;

        HPyExecuteWrapper(HPyContextMember member) {
            this.member = member;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached HPyExecuteContextFunction call) throws ArityException {
            return call.execute(inliningTarget, member, arguments);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperTraceUpcall implements TruffleObject {

        private final int[] counts;
        private final int index;

        final HPyExecuteWrapper delegate;

        public HPyExecuteWrapperTraceUpcall(int[] counts, int index, HPyExecuteWrapper delegate) {
            this.counts = counts;
            this.index = index;
            this.delegate = delegate;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @CachedLibrary("this.delegate") InteropLibrary lib) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            counts[index]++;
            return lib.execute(delegate, arguments);
        }
    }
}
