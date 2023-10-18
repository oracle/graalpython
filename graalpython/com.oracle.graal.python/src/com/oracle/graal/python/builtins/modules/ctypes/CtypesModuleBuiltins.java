/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ArgError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtr;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCData_GetContainer;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_ENCODING;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins.PyCFuncPtrFromDllNode.strchr;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T__TYPE_;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CTYPES;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENT_D;
import static com.oracle.graal.python.nodes.ErrorMessages.BYREF_ARGUMENT_MUST_BE_A_CTYPES_INSTANCE_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.CAST_ARGUMENT_2_MUST_BE_A_POINTER_TYPE_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.DON_T_KNOW_HOW_TO_CONVERT_PARAMETER_D;
import static com.oracle.graal.python.nodes.ErrorMessages.EXCEPTED_CTYPES_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.FFI_CALL_FAILED;
import static com.oracle.graal.python.nodes.ErrorMessages.FFI_PREP_CIF_FAILED;
import static com.oracle.graal.python.nodes.ErrorMessages.INT_TOO_LONG_TO_CONVERT;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.MEMORY_CANNOT_BE_RESIZED_BECAUSE_THIS_OBJECT_DOESN_T_OWN_IT;
import static com.oracle.graal.python.nodes.ErrorMessages.MINIMUM_SIZE_IS_D;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_A_CTYPES_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPES_TYPE_OR_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.NO_ALIGNMENT_INFO;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SYMBOL_IS_MISSING;
import static com.oracle.graal.python.nodes.ErrorMessages.THIS_TYPE_HAS_NO_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_MANY_ARGUMENTS_D_MAXIMUM_IS_D;
import static com.oracle.graal.python.nodes.StringLiterals.J_DEFAULT;
import static com.oracle.graal.python.nodes.StringLiterals.J_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_GLOBAL;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_LOCAL;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_NOW;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.FsConverterNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltinsClinicProviders.DyldSharedCacheContainsPathClinicProviderGen;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerReference;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNIContext;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.nfi.api.SignatureLibrary;

@CoreFunctions(defineModule = J__CTYPES)
public final class CtypesModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_DL_ERROR = tsLiteral("dlerror");
    private static final TruffleString T_DL_OPEN_ERROR = tsLiteral("dlopen() error");

    private static final TruffleString T_WINDOWS_ERROR = tsLiteral("Windows Error");

    private static final String J_DEFAULT_LIBRARY = PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? "msvcrt.dll" : J_EMPTY_STRING;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CtypesModuleBuiltinsFactory.getFactories();
    }

    private DLHandler rtldDefault;
    private Object dyldSharedCacheContainsPathFunction;
    @CompilationFinal private Object strlenFunction;
    @CompilationFinal private Object memcpyFunction;

    protected static final int FUNCFLAG_STDCALL = 0x0;
    protected static final int FUNCFLAG_CDECL = 0x1;
    protected static final int FUNCFLAG_HRESULT = 0x2;
    protected static final int FUNCFLAG_PYTHONAPI = 0x4;
    protected static final int FUNCFLAG_USE_ERRNO = 0x8;
    protected static final int FUNCFLAG_USE_LASTERROR = 0x10;

    protected static final int TYPEFLAG_ISPOINTER = 0x100;
    protected static final int TYPEFLAG_HASPOINTER = 0x200;
    protected static final int TYPEFLAG_HASUNION = 0x400;
    protected static final int TYPEFLAG_HASBITFIELD = 0x800;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("_pointer_type_cache", core.factory().createDict());
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            addBuiltinConstant("FUNCFLAG_STDCALL", FUNCFLAG_STDCALL);
        }
        addBuiltinConstant("FUNCFLAG_CDECL", FUNCFLAG_CDECL);
        addBuiltinConstant("FUNCFLAG_USE_ERRNO", FUNCFLAG_USE_ERRNO);
        addBuiltinConstant("FUNCFLAG_USE_LASTERROR", FUNCFLAG_USE_LASTERROR);
        addBuiltinConstant("FUNCFLAG_PYTHONAPI", FUNCFLAG_PYTHONAPI);
        addBuiltinConstant("__version__", "1.1.0");
        addBuiltinConstant("CFuncPtr", core.lookupType(PyCFuncPtr));
        addBuiltinConstant("ArgumentError", core.lookupType(ArgError));
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonObjectFactory factory = core.factory();
        PythonModule ctypesModule = core.lookupBuiltinModule(T__CTYPES);
        ctypesModule.setAttribute(tsLiteral("_string_at_addr"), factory.createNativeVoidPtr(StringAtFunction.create()));
        ctypesModule.setAttribute(tsLiteral("_cast_addr"), factory.createNativeVoidPtr(CastFunction.create()));
        ctypesModule.setAttribute(tsLiteral("_wstring_at_addr"), factory.createNativeVoidPtr(WStringAtFunction.create()));
        int rtldLocal = RTLD_LOCAL.getValueIfDefined();
        ctypesModule.setAttribute(tsLiteral("RTLD_LOCAL"), rtldLocal);
        ctypesModule.setAttribute(tsLiteral("RTLD_GLOBAL"), RTLD_GLOBAL.getValueIfDefined());

        PythonContext context = core.getContext();

        DLHandler handle;
        if (context.getEnv().isNativeAccessAllowed()) {
            handle = DlOpenNode.loadNFILibrary(context, NFIBackend.NATIVE, J_DEFAULT_LIBRARY, rtldLocal);
            setCtypeNFIHelpers(this, context, handle);

            if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                PythonModule sysModule = context.getSysModule();
                Object loadLibraryMethod = ReadAttributeFromObjectNode.getUncached().execute(ctypesModule, toTruffleStringUncached("LoadLibrary"));
                Object pythonLib = CallNode.getUncached().execute(loadLibraryMethod, toTruffleStringUncached(GraalHPyJNIContext.getJNILibrary()), 0);
                WriteAttributeToDynamicObjectNode.getUncached().execute(sysModule, toTruffleStringUncached("dllhandle"), pythonLib);
            }
        } else {
            try {
                CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(null, context, T_EMPTY_STRING, T_EMPTY_STRING);
                handle = new DLHandler(cApiContext.getLLVMLibrary(), 0, J_EMPTY_STRING, true);
                setCtypeLLVMHelpers(this, handle);
            } catch (ApiInitException e) {
                throw e.reraise(null, null, PConstructAndRaiseNode.Lazy.getUncached());
            } catch (ImportException e) {
                throw e.reraise(null, null, PConstructAndRaiseNode.Lazy.getUncached());
            } catch (IOException e) {
                throw PConstructAndRaiseNode.getUncached().raiseOSError(null, e, EqualNode.getUncached());
            }
        }
        NativeFunction memmove = MemMoveFunction.create(handle, context);
        ctypesModule.setAttribute(tsLiteral("_memmove_addr"), factory.createNativeVoidPtr(memmove, memmove.adr));
        NativeFunction memset = MemSetFunction.create(handle, context);
        ctypesModule.setAttribute(tsLiteral("_memset_addr"), factory.createNativeVoidPtr(memset, memset.adr));
        rtldDefault = handle;
    }

    Object getStrlenFunction() {
        return strlenFunction;
    }

    Object getMemcpyFunction() {
        return memcpyFunction;
    }

    private static void setCtypeLLVMHelpers(CtypesModuleBuiltins ctypesModuleBuiltins, DLHandler h) {
        try {
            InteropLibrary lib = InteropLibrary.getUncached(h.library);
            ctypesModuleBuiltins.strlenFunction = lib.readMember(h.library, NativeCAPISymbol.FUN_STRLEN.getName());
            ctypesModuleBuiltins.memcpyFunction = lib.readMember(h.library, NativeCAPISymbol.FUN_MEMCPY.getName());
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void setCtypeNFIHelpers(CtypesModuleBuiltins ctypesModuleBuiltins, PythonContext context, DLHandler h) {
        try {
            ctypesModuleBuiltins.strlenFunction = createNFIHelperFunction(context, h, "strlen", "(POINTER):UINT32");
            ctypesModuleBuiltins.memcpyFunction = createNFIHelperFunction(context, h, "memcpy", "([UINT8], POINTER, UINT32):POINTER");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    private static Object createNFIHelperFunction(PythonContext context, DLHandler h, String name, String signature) throws UnsupportedMessageException, UnknownIdentifierException {
        Object symbol = InteropLibrary.getUncached().readMember(h.library, name);
        Source source = Source.newBuilder(J_NFI_LANGUAGE, signature, name).build();
        Object nfiSignature = context.getEnv().parseInternal(source).call();
        return SignatureLibrary.getUncached().bind(nfiSignature, symbol);
    }

    protected static final String J_UNPICKLE = "_unpickle";
    protected static final TruffleString T_UNPICKLE = tsLiteral(J_UNPICKLE);

    enum NFIBackend {
        NATIVE(StringLiterals.T_EMPTY_STRING),
        LLVM(tsLiteral("with llvm "));

        private final TruffleString withClause;

        NFIBackend(TruffleString withClause) {
            this.withClause = withClause;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "sym")
    protected static final class NativeFunction implements TruffleObject {
        final Object sym;
        final long adr;
        final String name;

        Object function;
        TruffleString signature;
        FFIType[] atypes;
        FFIType rtype;

        final boolean isManaged;

        NativeFunction(Object sym, long adr, String name, boolean isManaged) {
            this.sym = sym;
            this.adr = adr;
            this.name = name;
            this.function = null;
            this.signature = null;
            this.isManaged = isManaged;
        }

        protected boolean isManaged() {
            return isManaged;
        }

        protected boolean isLLVM() {
            return sym instanceof CallLLVMFunction;
        }

        protected boolean isManaged(long address) {
            return adr == address;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "library")
    protected static final class DLHandler implements TruffleObject {
        final Object library;
        final String name;
        final long adr;
        final boolean isManaged;
        boolean isClosed;

        private DLHandler(Object library, long adr, String name, boolean isManaged) {
            this.library = library;
            this.adr = adr;
            this.name = name;
            this.isManaged = isManaged;
            this.isClosed = false;
        }

        public Object getLibrary() {
            return library;
        }
    }

    @TruffleBoundary
    protected static void registerAddress(PythonContext context, long adr, Object o) {
        context.getCtypesAdrMap().put(adr, o);
    }

    @TruffleBoundary
    protected static Object getObjectAtAddress(PythonContext context, long adr) {
        return context.getCtypesAdrMap().get(adr);
    }

    public static final class CtypesThreadState {
        /*
         * ctypes maintains thread-local storage that has space for two error numbers: private
         * copies of the system 'errno' value and, on Windows, the system error code accessed by the
         * GetLastError() and SetLastError() api functions.
         *
         * Foreign functions created with CDLL(..., use_errno=True), when called, swap the system
         * 'errno' value with the private copy just before the actual function call, and swapped
         * again immediately afterwards. The 'use_errno' parameter defaults to False, in this case
         * 'ctypes_errno' is not touched.
         *
         * On Windows, foreign functions created with CDLL(..., use_last_error=True) or WinDLL(...,
         * use_last_error=True) swap the system LastError value with the ctypes private copy.
         *
         * The values are also swapped immediately before and after ctypes callback functions are
         * called, if the callbacks are constructed using the new optional use_errno parameter set
         * to True: CFUNCTYPE(..., use_errno=TRUE) or WINFUNCTYPE(..., use_errno=True).
         *
         * New ctypes functions are provided to access the ctypes private copies from Python:
         *
         * - ctypes.set_errno(value) and ctypes.set_last_error(value) store 'value' in the private
         * copy and returns the previous value.
         *
         * - ctypes.get_errno() and ctypes.get_last_error() returns the current ctypes private
         * copies value.
         */
        // (mq) TODO: add another field for errno (Windows support)
        int errno; // see '_ctypes_get_errobj'

        NFIBackend backendType;
        EconomicMapStorage ptrtype_cache;
        EconomicMapStorage cache;

        public CtypesThreadState() {
            this.ptrtype_cache = EconomicMapStorage.create();
            this.cache = EconomicMapStorage.create();
            this.backendType = NFIBackend.NATIVE;
            this.errno = 0;
        }

        @TruffleBoundary
        static CtypesThreadState initCtypesThreadState(PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = new CtypesThreadState();
            context.getThreadState(language).setCtypes(ctypes);
            return ctypes;
        }

        static CtypesThreadState get(PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = context.getThreadState(language).getCtypes();
            if (ctypes == null) {
                ctypes = initCtypesThreadState(context, language);
            }
            return ctypes;
        }
    }

    @Builtin(name = "get_last_error", maxNumOfPositionalArgs = 1, declaresExplicitSelf = true, os = PythonOS.PLATFORM_WIN32)
    @Builtin(name = "get_errno", maxNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class GetErrnoNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getErrno(@SuppressWarnings("unused") PythonModule module,
                        @Bind("this") Node inliningTarget,
                        @Cached AuditNode auditNode) {
            auditNode.audit(inliningTarget, "ctypes.get_errno");
            return get_errno(getContext(), getLanguage());
        }

        static Object get_errno(PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = CtypesThreadState.get(context, language);
            return ctypes.errno;
        }
    }

    @Builtin(name = "set_last_error", minNumOfPositionalArgs = 1, parameterNames = {"errno"}, os = PythonOS.PLATFORM_WIN32)
    @Builtin(name = "set_errno", minNumOfPositionalArgs = 1, parameterNames = {"errno"})
    @ArgumentClinic(name = "errno", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    protected abstract static class SetErrnoNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.SetErrnoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object setErrno(int newErrno,
                        @Bind("this") Node inliningTarget,
                        @Cached AuditNode auditNode) {
            auditNode.audit(inliningTarget, "ctypes.set_errno", newErrno);
            return set_errno(newErrno, getContext(), getLanguage());
        }

        static Object set_errno(int newErrno, PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = CtypesThreadState.get(context, language);
            int oldErrno = ctypes.errno;
            ctypes.errno = newErrno;
            return oldErrno;
        }
    }

    @Builtin(name = "POINTER", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PointerTypeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object POINTER(VirtualFrame frame, Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @Cached IsTypeNode isTypeNode,
                        @Cached CallNode callNode,
                        @Cached GetNameNode getNameNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached SimpleTruffleStringFormatNode formatNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(PythonContext.get(inliningTarget), PythonLanguage.get(inliningTarget));
            Object result = getItem.execute(frame, inliningTarget, ctypes.ptrtype_cache, cls);
            if (result != null) {
                return result;
            }
            Object key;
            if (PGuards.isString(cls)) {
                TruffleString name = toTruffleStringNode.execute(inliningTarget, cls);
                TruffleString buf = formatNode.format("LP_%s", name);
                Object[] args = new Object[]{buf, PyCPointer, factory.createDict()};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = factory.createNativeVoidPtr(result);
            } else if (isTypeNode.execute(inliningTarget, cls)) {
                TruffleString buf = formatNode.format("LP_%s", getNameNode.execute(inliningTarget, cls));
                PTuple bases = factory.createTuple(new Object[]{PyCPointer});
                Object[] args = new Object[]{buf, bases, factory.createDict(new PKeyword[]{new PKeyword(T__TYPE_, cls)})};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = cls;
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, MUST_BE_A_CTYPES_TYPE);
            }
            HashingStorage newStorage = setItem.execute(frame, inliningTarget, ctypes.ptrtype_cache, key, result);
            assert newStorage == ctypes.ptrtype_cache;
            return result;
        }
    }

    @Builtin(name = "pointer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PointerObjectNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object pointer(VirtualFrame frame, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PointerTypeNode callPOINTER,
                        @Cached CallNode callNode,
                        @Cached GetClassNode getClassNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), getLanguage());
            Object typ = getItem.execute(frame, inliningTarget, ctypes.ptrtype_cache, getClassNode.execute(inliningTarget, arg));
            if (typ != null) {
                return callNode.execute(frame, typ, arg);
            }
            typ = callPOINTER.execute(frame, getClassNode.execute(inliningTarget, arg));
            return callNode.execute(frame, typ, arg);
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @Builtin(name = J_UNPICKLE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class UnpickleNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object unpickle(VirtualFrame frame, Object typ, PTuple state,
                        @Cached CallNode callNode,
                        @Cached("create(New)") LookupAndCallUnaryNode lookupAndCallUnaryNode,
                        @Cached("create(T___SETSTATE__)") GetAttributeNode setStateAttr) {
            Object obj = lookupAndCallUnaryNode.executeObject(frame, typ);
            Object meth = setStateAttr.executeObject(frame, obj);
            callNode.execute(frame, meth, state);
            return obj;
        }

    }

    @Builtin(name = "buffer_info", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BufferInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object buffer_info(Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict == null) {
                dict = pyObjectStgDictNode.execute(arg);
            }
            if (dict == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, NOT_A_CTYPES_TYPE_OR_OBJECT);
            }
            Object[] shape = new Object[dict.ndim];
            for (int i = 0; i < dict.ndim; ++i) {
                shape[i] = dict.shape[i];
            }

            return factory.createTuple(new Object[]{dict.format, dict.ndim, factory.createTuple(shape)});
        }
    }

    @Builtin(name = "resize", minNumOfPositionalArgs = 2, parameterNames = {"", "size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    protected abstract static class ResizeNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.ResizeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object resize(CDataObject obj, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyObjectStgDictNode.execute(obj);
            if (dict == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, EXCEPTED_CTYPES_INSTANCE);
            }
            if (size < dict.size) {
                throw raiseNode.get(inliningTarget).raise(ValueError, MINIMUM_SIZE_IS_D, dict.size);
            }
            if (obj.b_needsfree) {
                throw raiseNode.get(inliningTarget).raise(ValueError, MEMORY_CANNOT_BE_RESIZED_BECAUSE_THIS_OBJECT_DOESN_T_OWN_IT);
            }
            obj.b_size = size;
            return PNone.NONE;
        }
    }

    @Builtin(name = "LoadLibrary", minNumOfPositionalArgs = 1, parameterNames = {"$self", "name", "mode"}, declaresExplicitSelf = true, os = PythonOS.PLATFORM_WIN32)
    @Builtin(name = "dlopen", minNumOfPositionalArgs = 1, parameterNames = {"$self", "name", "mode"}, declaresExplicitSelf = true)
    // TODO: 'name' might need to be processed using FSConverter.
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString, defaultValue = "T_EMPTY_STRING", useDefaultForNone = true)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "Integer.MIN_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    protected abstract static class DlOpenNode extends PythonTernaryClinicBuiltinNode {

        private static final TruffleString MACOS_Security_LIB = tsLiteral("/System/Library/Frameworks/Security.framework/Security");
        private static final TruffleString MACOS_CoreFoundation_LIB = tsLiteral("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation");

        private static final String T_RTLD_LOCAL = "RTLD_LOCAL|RTLD_NOW";
        private static final String T_RTLD_GLOBAL = "RTLD_GLOBAL|RTLD_NOW";

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(DlOpenNode.class);

        protected static String flagsToString(int flag) {
            return (flag & RTLD_LOCAL.getValueIfDefined()) != 0 ? T_RTLD_LOCAL : T_RTLD_GLOBAL;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.DlOpenNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        protected static DLHandler loadNFILibrary(PythonContext context, NFIBackend backendType, String name, int flags) {
            String src;
            if (!name.isEmpty()) {
                src = String.format("%sload (%s) \"%s\"", backendType.withClause, flagsToString(flags), name);
            } else {
                src = J_DEFAULT;
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Loading native library %s %s", name, backendType.withClause));
            }
            Object handler = load(context, src, name);
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                return new DLHandler(handler, lib.asPointer(handler), name, false);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("Cannot convert NFI library to pointer", e);
            }
        }

        @TruffleBoundary
        private static Object load(PythonContext context, String src, String name) {
            Source loadSrc = Source.newBuilder(J_NFI_LANGUAGE, src, "load:" + name).internal(true).build();
            return context.getEnv().parseInternal(loadSrc).call();
        }

        @TruffleBoundary
        protected static Object loadLLVMLibrary(PythonContext context, Node nodeForRaise, TruffleString path) throws ImportException, ApiInitException, IOException {
            context.ensureLLVMLanguage(nodeForRaise);
            if (path.isEmpty()) {
                CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(null, context, T_EMPTY_STRING, T_EMPTY_STRING);
                return cApiContext.getLLVMLibrary();
            }
            Source loadSrc = Source.newBuilder(J_LLVM_LANGUAGE, context.getPublicTruffleFileRelaxed(path)).build();
            return context.getEnv().parseInternal(loadSrc).call();
        }

        @TruffleBoundary
        private static TruffleString getErrMsg(Exception e) {
            String errmsg = e != null ? e.getMessage() : null;
            if (errmsg == null || errmsg.isEmpty()) {
                return T_DL_OPEN_ERROR;
            }
            return toTruffleStringUncached(errmsg);
        }

        @Specialization
        static Object py_dl_open(VirtualFrame frame, PythonModule self, TruffleString name, int m,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached AuditNode auditNode,
                        @Cached CodePointLengthNode codePointLengthNode,
                        @Cached EndsWithNode endsWithNode,
                        @Cached EqualNode eqNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            auditNode.audit(inliningTarget, "ctypes.dlopen", name);
            if (name.isEmpty()) {
                return factory.createNativeVoidPtr(((CtypesModuleBuiltins) self.getBuiltins()).rtldDefault);
            }

            int mode = m != Integer.MIN_VALUE ? m : RTLD_LOCAL.getValueIfDefined();
            mode |= RTLD_NOW.getValueIfDefined();
            PythonContext context = PythonContext.get(inliningTarget);
            DLHandler handle;
            Exception exception = null;
            boolean loadWithLLVM = !context.getEnv().isNativeAccessAllowed() || //
                            (!context.getOption(PythonOptions.UseSystemToolchain) &&
                                            endsWithNode.executeBoolean(frame, name, context.getSoAbi(), 0, codePointLengthNode.execute(name, TS_ENCODING)));
            try {
                if (loadWithLLVM) {
                    Object handler = loadLLVMLibrary(context, inliningTarget, name);
                    long adr = hashNode.execute(frame, inliningTarget, handler);
                    handle = new DLHandler(handler, adr, name.toJavaStringUncached(), true);
                    registerAddress(context, handle.adr, handle);
                    return factory.createNativeVoidPtr(handle);
                } else {
                    CtypesThreadState ctypes = CtypesThreadState.get(context, PythonLanguage.get(inliningTarget));
                    /*-
                     TODO: (mq) cryptography in macos isn't always compatible with ctypes.
                     */
                    if (!eqNode.execute(name, MACOS_Security_LIB, TS_ENCODING) && !eqNode.execute(name, MACOS_CoreFoundation_LIB, TS_ENCODING)) {
                        handle = loadNFILibrary(context, ctypes.backendType, name.toJavaStringUncached(), mode);
                        registerAddress(context, handle.adr, handle);
                        return factory.createNativeVoidPtr(handle, handle.adr);
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            throw raiseNode.get(inliningTarget).raise(OSError, getErrMsg(exception));
        }
    }

    @Builtin(name = "dlclose", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class DlCloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object py_dl_close(Object pointerObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CtypesNodes.HandleFromLongNode handleFromLongNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            DLHandler handle = handleFromLongNode.getDLHandler(inliningTarget, pointerObj);
            if (handle != null) {
                handle.isClosed = true;
                return PNone.NONE;
            }
            throw raiseNode.get(inliningTarget).raise(OSError, T_DL_ERROR);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 60 -> 43
    protected abstract static class CtypesDlSymNode extends PNodeWithContext {

        protected abstract Object execute(VirtualFrame frame, Pointer handlePtr, Object n, PythonBuiltinClassType error);

        @Specialization
        static Object ctypes_dlsym(VirtualFrame frame, Pointer handlePtr, Object n, PythonBuiltinClassType error,
                        @Bind("this") Node inliningTarget,
                        @Cached CtypesNodes.HandleFromPointerNode handleFromPointerNode,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaStringNode asString,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            DLHandler handle = handleFromPointerNode.getDLHandler(inliningTarget, handlePtr);
            String name = asString.execute(n);
            if (handle == null || handle.isClosed) {
                throw raiseNode.get(inliningTarget).raise(error, T_DL_ERROR);
            }
            try {
                Object sym = ilib.readMember(handle.library, name);
                boolean isManaged = handle.isManaged;
                long adr = isManaged ? hashNode.execute(frame, inliningTarget, sym) : ilib.asPointer(sym);
                sym = isManaged ? CallLLVMFunction.create(sym, ilib) : sym;
                NativeFunction func = new NativeFunction(sym, adr, name, isManaged);
                registerAddress(PythonContext.get(inliningTarget), adr, func);
                // PyLong_FromVoidPtr(ptr);
                if (!isManaged) {
                    return factory.createNativeVoidPtr(func, adr);
                } else {
                    return factory.createNativeVoidPtr(func);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw raiseNode.get(inliningTarget).raise(error, e);
            }
        }
    }

    @Builtin(name = "dlsym", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class DlSymNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object py_dl_sym(VirtualFrame frame, Object obj, Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Cached AuditNode auditNode,
                        @Cached CtypesDlSymNode dlSymNode) {
            auditNode.audit(inliningTarget, "ctypes.dlsym/handle", obj, name);
            return dlSymNode.execute(frame, pointerFromLongNode.execute(inliningTarget, obj), name, OSError);
        }
    }

    @Builtin(name = "_dyld_shared_cache_contains_path", parameterNames = {"$self", "path"}, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ArgumentClinic(name = "path", conversionClass = FsConverterNode.class)
    protected abstract static class DyldSharedCacheContainsPath extends PythonBinaryClinicBuiltinNode {
        private static final String DYLD_SHARED_CACHE_CONTAINS_PATH = "_dyld_shared_cache_contains_path";

        @CompilationFinal private static boolean hasDynamicLoaderCacheValue = false;
        @CompilationFinal private static boolean hasDynamicLoaderCacheInit = false;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DyldSharedCacheContainsPathClinicProviderGen.INSTANCE;
        }

        private static boolean hasDynamicLoaderCache() {
            if (hasDynamicLoaderCacheInit) {
                return hasDynamicLoaderCacheValue;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (System.getProperty("os.name").contains("Mac")) {
                String osVersion = System.getProperty("os.version");
                // dynamic linker cache support on os.version >= 11.x
                int major = 11;
                int i = osVersion.indexOf('.');
                try {
                    major = Integer.parseInt(i < 0 ? osVersion : osVersion.substring(0, i));
                } catch (NumberFormatException e) {
                }
                hasDynamicLoaderCacheValue = major >= 11;
            } else {
                hasDynamicLoaderCacheValue = false;
            }
            hasDynamicLoaderCacheInit = true;
            return hasDynamicLoaderCacheValue;
        }

        @Specialization
        static Object doBytes(PythonModule self, PBytes path,
                        @Bind("this") Node inliningTarget,
                        @Cached ToBytesNode toBytesNode,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!hasDynamicLoaderCache()) {
                throw raiseNode.get(inliningTarget).raise(NotImplementedError, S_SYMBOL_IS_MISSING, DYLD_SHARED_CACHE_CONTAINS_PATH);
            }

            CtypesModuleBuiltins builtins = (CtypesModuleBuiltins) self.getBuiltins();
            Object cachedFunction = builtins.dyldSharedCacheContainsPathFunction;
            if (cachedFunction == null) {
                cachedFunction = initializeDyldSharedCacheContainsPathFunction(PythonContext.get(inliningTarget), builtins);
                builtins.dyldSharedCacheContainsPathFunction = cachedFunction;
            }
            if (!ilib.isNull(cachedFunction)) {
                try {
                    // note: CByteArrayWrapper is '\0' terminated
                    CByteArrayWrapper byteArrayWrapper = new CByteArrayWrapper(toBytesNode.execute(path));
                    try {
                        Object result = ilib.execute(cachedFunction, byteArrayWrapper);
                        return resultLib.asInt(result) != 0;
                    } finally {
                        byteArrayWrapper.free();
                    }
                } catch (InteropException e) {
                    // fall through
                }
            }
            return false;
        }

        @TruffleBoundary
        private static Object initializeDyldSharedCacheContainsPathFunction(PythonContext context, CtypesModuleBuiltins builtins) {
            try {
                if (context.getEnv().isNativeAccessAllowed()) {
                    // note: sizeof(bool) == 1
                    // bool _dyld_shared_cache_contains_path(const char* path)
                    return createNFIHelperFunction(context, builtins.rtldDefault, DYLD_SHARED_CACHE_CONTAINS_PATH, "(POINTER):SINT8");
                }
                InteropLibrary lib = InteropLibrary.getUncached(builtins.rtldDefault.library);
                if (lib.isMemberReadable(builtins.rtldDefault.library, DYLD_SHARED_CACHE_CONTAINS_PATH)) {
                    return lib.readMember(builtins.rtldDefault.library, DYLD_SHARED_CACHE_CONTAINS_PATH);
                }
            } catch (InteropException e) {
                // fall through
            }
            return context.getNativeNull();
        }
    }

    @Builtin(name = "alignment", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class AlignmentNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object align_func(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            dict = pyObjectStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            throw raiseNode.get(inliningTarget).raise(TypeError, NO_ALIGNMENT_INFO);
        }
    }

    @Builtin(name = "sizeof", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class SizeOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doit(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.size;
            }

            if (pyTypeCheck.isCDataObject(obj)) {
                return ((CDataObject) obj).b_size;
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, THIS_TYPE_HAS_NO_SIZE);
        }
    }

    @Builtin(name = "byref", minNumOfPositionalArgs = 1, parameterNames = {"", "offset"})
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    protected abstract static class ByRefNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.ByRefNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doit(CDataObject obj, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(null, obj, offset, inliningTarget, getClassNode, raiseNode);
            }
            PyCArgObject parg = factory.createCArgObject();
            parg.tag = 'P';
            parg.pffi_type = FFIType.ffi_type_pointer;
            parg.obj = obj;
            parg.valuePointer = obj.b_ptr.createReference(offset);

            return parg;
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object error(VirtualFrame frame, Object obj, Object off,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            TruffleString name = GetNameNode.executeUncached(clazz);
            throw raiseNode.get(inliningTarget).raise(TypeError, BYREF_ARGUMENT_MUST_BE_A_CTYPES_INSTANCE_NOT_S, name);
        }
    }

    protected static Object getObjectAt(PythonContext context, Object ptr) {
        if (PGuards.isInteger(ptr)) {
            return getObjectAtAddress(context, (Long) ptr);
        }
        return ptr;
    }

    @Builtin(name = "call_function", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class CallFunctionNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object call_function(VirtualFrame frame, Object pointerObj, PTuple arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached AuditNode auditNode,
                        @Cached CallProcNode callProcNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CtypesNodes.HandleFromLongNode handleFromLongNode) {
            NativeFunction func = handleFromLongNode.getNativeFunction(inliningTarget, pointerObj);
            auditNode.audit(inliningTarget, "ctypes.call_function", func, arguments);
            return callProcNode.execute(frame, func,
                            getArray.execute(inliningTarget, arguments.getSequenceStorage()),
                            0, /* flags */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.argtypes */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.converters */
                            null, /* self.restype */
                            null);
        }
    }

    @Builtin(name = "addressof", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class AddressOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doit(CDataObject obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached AuditNode auditNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(obj, raiseNode.get(inliningTarget));
            }
            auditNode.audit(inliningTarget, "ctypes.addressof", obj);
            return factory.createNativeVoidPtr(obj.b_ptr);
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, INVALID_TYPE);
        }
    }

    @Builtin(name = "call_cdeclfunction", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class CallCdeclfunctionNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object call_function(VirtualFrame frame, Object pointerObj, PTuple arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached AuditNode auditNode,
                        @Cached CallProcNode callProcNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CtypesNodes.HandleFromLongNode handleFromLongNode) {
            NativeFunction func = handleFromLongNode.getNativeFunction(inliningTarget, pointerObj);
            auditNode.audit(inliningTarget, "ctypes.call_function", func, arguments);
            return callProcNode.execute(frame, func,
                            getArray.execute(inliningTarget, arguments.getSequenceStorage()),
                            FUNCFLAG_CDECL, /* flags */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.argtypes */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.converters */
                            null, /* self.restype */
                            null);
        }
    }

    @Builtin(name = "FormatError", minNumOfPositionalArgs = 1, os = PythonOS.PLATFORM_WIN32)
    @GenerateNodeFactory
    protected abstract static class FormatErrorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(@SuppressWarnings("unused") Object errorCode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(NotImplementedError);
        }
    }

    @Builtin(name = "_check_HRESULT", minNumOfPositionalArgs = 1, parameterNames = {"hresult"}, os = PythonOS.PLATFORM_WIN32)
    @ArgumentClinic(name = "hresult", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    protected abstract static class CheckHresultNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.CheckHresultNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object check(VirtualFrame frame, int hresult,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (hresult >= 0) {
                return hresult;
            } else {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, hresult, T_WINDOWS_ERROR);
            }
        }
    }

    protected static final class argument {
        FFIType ffi_type;
        StgDictObject stgDict;
        /*
         * CPython stores the value directly in a union. We use a pointer, so there is one more
         * pointer indirection
         */
        Pointer valuePointer;
        // Used to hold reference to object that have native memory finalizers
        Object keep;
    }

    /**
     * Requirements, must be ensured by the caller: - argtuple is tuple of arguments - argtypes is
     * either NULL, or a tuple of the same size as argtuple
     *
     * - XXX various requirements for restype, not yet collected
     *
     * argtypes is amisleading name: This is a tuple of methods, not types: the .from_param class
     * methods of the types
     */
    @SuppressWarnings("truffle-inlining")       // footprint reduction 88 -> 69
    protected abstract static class CallProcNode extends PNodeWithContext {

        abstract Object execute(VirtualFrame frame, NativeFunction pProc, Object[] argtuple, int flags, Object[] argtypes, Object[] converters, Object restype, Object checker);

        /*
         * bpo-13097: Max number of arguments _ctypes_callproc will accept.
         *
         * This limit is enforced for the `alloca()` call in `_ctypes_callproc`, to avoid allocating
         * a massive buffer on the stack.
         */
        protected static final int CTYPES_MAX_ARGCOUNT = 1024;

        @Specialization
        Object _ctypes_callproc(VirtualFrame frame, NativeFunction pProc, Object[] argarray, @SuppressWarnings("unused") int flags, Object[] argtypes, Object[] converters, Object restype,
                        Object checker,
                        @Bind("this") Node inliningTarget,
                        @Cached ConvParamNode convParamNode,
                        @Cached ConvertParameterToBackendValueNode convertParameterToBackendValueNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CallNode callNode,
                        @Cached GetResultNode getResultNode,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int argcount = argarray.length;
            if (argcount > CTYPES_MAX_ARGCOUNT) {
                throw raiseNode.get(inliningTarget).raise(ArgError, TOO_MANY_ARGUMENTS_D_MAXIMUM_IS_D, argcount, CTYPES_MAX_ARGCOUNT);
            }

            argument[] args = new argument[argcount];
            Object[] avalues = new Object[argcount];
            FFIType[] atypes = new FFIType[argcount];
            int argtype_count = argtypes != null ? argtypes.length : 0;

            /* Convert the arguments */
            BackendMode mode = BackendMode.NFI;
            if (pProc.isManaged()) {
                if (pProc.isLLVM()) {
                    mode = BackendMode.LLVM;
                } else {
                    mode = BackendMode.INTRINSIC;
                }
            }
            for (int i = 0; i < argcount; ++i) {
                args[i] = new argument();
                Object arg = argarray[i];
                /*
                 * For cdecl functions, we allow more actual arguments than the length of the
                 * argtypes tuple. This is checked in _ctypes::PyCFuncPtr_Call
                 */
                Object v = arg;
                if (converters != null && argtype_count > i) {
                    try {
                        v = callNode.execute(frame, converters[i], arg);
                    } catch (PException e) {
                        throw raiseNode.get(inliningTarget).raise(ArgError, ARGUMENT_D, i + 1);
                    }
                }
                convParamNode.execute(frame, v, i + 1, args[i]);
                FFIType ffiType = args[i].ffi_type;
                atypes[i] = ffiType;
                if (arg instanceof PyCFuncPtrObject) {
                    atypes[i] = new FFIType(atypes[i], ((PyCFuncPtrObject) arg).thunk);
                }
                avalues[i] = convertParameterToBackendValueNode.execute(inliningTarget, args[i].valuePointer, args[i].ffi_type, args[i].stgDict, mode);
            }

            FFIType rtype = FFIType.ffi_type_sint;
            if (restype != null) {
                StgDictObject dict = pyTypeStgDictNode.execute(restype);
                if (dict != null) {
                    rtype = dict.ffi_type_pointer;
                }
            }
            Object result;
            if (mode == BackendMode.NFI) {
                result = callNativeFunction(inliningTarget, pProc, avalues, atypes, rtype, ilib, raiseNode);
            } else {
                result = callManagedFunction(inliningTarget, pProc, avalues, ilib, raiseNode);
                if (mode == BackendMode.INTRINSIC) {
                    /*
                     * We don't want result conversion for functions implemented in Java, they
                     * should take care to produce the desired result type
                     */
                    return result;
                }
            }

            return getResultNode.execute(restype, rtype, result, checker);
        }

        static Object callManagedFunction(Node inliningTarget, NativeFunction pProc, Object[] argarray, InteropLibrary ilib, PRaiseNode.Lazy raiseNode) {
            try {
                return ilib.execute(pProc.sym, argarray);
            } catch (PException e) {
                throw e;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | AbstractTruffleException e) {
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.raiseUncached(inliningTarget, RuntimeError, FFI_CALL_FAILED);
            } catch (UnsupportedSpecializationException ee) {
                // TODO: llvm/GR-???
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, toTruffleStringUncached("require backend support."));
            }
        }

        @TruffleBoundary
        protected Object getFunction(NativeFunction pProc, String signature) throws Exception {
            Source source = Source.newBuilder(J_NFI_LANGUAGE, signature, pProc.name).build();
            Object nfiSignature = getContext().getEnv().parseInternal(source).call();
            return SignatureLibrary.getUncached().bind(nfiSignature, pProc.sym);
        }

        /**
         * NFI compatible native function calls (temporary replacement)
         */
        Object callNativeFunction(Node inliningTarget, NativeFunction pProc, Object[] avalues, FFIType[] atypes, FFIType restype,
                        InteropLibrary ilib, PRaiseNode.Lazy raiseNode) {
            Object function;
            if (pProc.function != null && equals(atypes, pProc.atypes) && restype == pProc.rtype) {
                function = pProc.function;
            } else {
                TruffleString signature = FFIType.buildNFISignature(atypes, restype);
                try {
                    function = getFunction(pProc, signature.toJavaStringUncached());
                } catch (Exception e) {
                    throw raiseNode.get(inliningTarget).raise(RuntimeError, FFI_PREP_CIF_FAILED);
                }
                pProc.atypes = atypes;
                pProc.rtype = restype;
                pProc.function = function;
                pProc.signature = signature;
            }
            try {
                return ilib.execute(function, avalues);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.raiseUncached(inliningTarget, RuntimeError, FFI_CALL_FAILED);
            }
        }

        private static boolean equals(FFIType[] atypes, FFIType[] atypes2) {
            if (atypes.length != atypes2.length) {
                return false;
            }
            for (int i = 0; i < atypes.length; i++) {
                if (atypes[i] != atypes2[i]) {
                    return false;
                }
            }
            return true;
        }

        /*
         * libffi uses:
         *
         * ffi_status ffi_prep_cif(ffi_cif *cif, ffi_abi abi, unsigned int nargs, ffi_type *rtype,
         * ffi_type **atypes);
         *
         * and then
         *
         * void ffi_call(ffi_cif *cif, void *fn, void *rvalue, void **avalues);
         */
        /*- TODO (mq) require more support from NFI.
        void _call_function_pointer(int flags,
                                NativeFunction pProc,
                                Object[] avalues,
                                FFIType[] atypes,
                                FFIType restype,
                                Object resmem,
                                int argcount,
                                CtypesThreadState state) {
            // XXX check before here
            if (restype == null) {
                throw raise(RuntimeError, NO_FFI_TYPE_FOR_RESULT);
            }

            int cc = FFI_DEFAULT_ABI;
            ffi_cif cif;
            if (FFI_OK != ffi_prep_cif(&cif, cc, argcount, restype, atypes)) {
                throw raise(RuntimeError, FFI_PREP_CIF_FAILED);
            }

            Object error_object = null;
            if ((flags & (FUNCFLAG_USE_ERRNO | FUNCFLAG_USE_LASTERROR)) != 0) {
                error_object = state.errno;
            }
            if ((flags & FUNCFLAG_PYTHONAPI) == 0)
                Py_UNBLOCK_THREADS
            if ((flags & FUNCFLAG_USE_ERRNO) != 0) {
                int temp = state.errno;
                state.errno = errno;
                errno = temp;
            }
            ffi_call(&cif, pProc, resmem, avalues);
            if ((flags & FUNCFLAG_USE_ERRNO) != 0) {
                int temp = state.errno;
                state.errno = errno;
                errno = temp;
            }
            if ((flags & FUNCFLAG_PYTHONAPI) == 0)
                Py_BLOCK_THREADS
            if ((flags & FUNCFLAG_PYTHONAPI) && PyErr_Occurred()) {
            }
        }
        */
    }

    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 25
    abstract static class GetResultNode extends Node {

        abstract Object execute(Object restype, FFIType rtype, Object result, Object checker);

        /*
         * Convert the C value in result into a Python object, depending on restype.
         *
         * - If restype is NULL, return a Python integer. - If restype is None, return None. - If
         * restype is a simple ctypes type (c_int, c_void_p), call the type's getfunc, pass the
         * result to checker and return the result. - If restype is another ctypes type, return an
         * instance of that. - Otherwise, call restype and return the result.
         */
        @Specialization(guards = "restype == null", limit = "1")
        static Object asInt(@SuppressWarnings("unused") Object restype, @SuppressWarnings("unused") FFIType rtype, Object result, @SuppressWarnings("unused") Object checker,
                        @CachedLibrary("result") InteropLibrary ilib) {
            try {
                return ilib.asInt(result);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "isNone(restype)")
        @SuppressWarnings("unused")
        static Object none(Object restype, FFIType rtype, Object result, Object checker) {
            return PNone.NONE;
        }

        @Specialization(guards = {"restype != null", "!isNone(restype)", "dict == null"}, limit = "1")
        static Object callResType(Object restype, @SuppressWarnings("unused") FFIType rtype, Object result, @SuppressWarnings("unused") Object checker,
                        @CachedLibrary("result") InteropLibrary ilib,
                        @SuppressWarnings("unused") @Shared @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @SuppressWarnings("unused") @Bind("getStgDict(restype, pyTypeStgDictNode)") StgDictObject dict,
                        @Shared @Cached CallNode callNode) {
            try {
                return callNode.execute(restype, ilib.asInt(result));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {"restype != null", "!isNone(restype)", "dict != null"}, limit = "1")
        static Object callGetFunc(Object restype, FFIType rtype, Object result, Object checker,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("result") InteropLibrary ilib,
                        @SuppressWarnings("unused") @Shared @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Bind("getStgDict(restype, pyTypeStgDictNode)") StgDictObject dict,
                        @Shared @Cached CallNode callNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetFuncNode getFuncNode,
                        @Cached CtypesNodes.PyCDataFromBaseObjNode fromBaseObjNode) {
            Pointer resultPtr;
            try {
                resultPtr = switch (rtype.type) {
                    case FFI_TYPE_UINT8, FFI_TYPE_SINT8 -> Pointer.create(rtype, rtype.size, ilib.asByte(result), 0);
                    case FFI_TYPE_UINT16, FFI_TYPE_SINT16 -> Pointer.create(rtype, rtype.size, ilib.asShort(result), 0);
                    case FFI_TYPE_UINT32, FFI_TYPE_SINT32 -> Pointer.create(rtype, rtype.size, ilib.asInt(result), 0);
                    case FFI_TYPE_SINT64, FFI_TYPE_UINT64 -> Pointer.create(rtype, rtype.size, ilib.asLong(result), 0);
                    case FFI_TYPE_FLOAT -> Pointer.create(rtype, rtype.size, ilib.asFloat(result), 0);
                    case FFI_TYPE_DOUBLE -> Pointer.create(rtype, rtype.size, ilib.asDouble(result), 0);
                    case FFI_TYPE_VOID -> Pointer.NULL;
                    case FFI_TYPE_POINTER, FFI_TYPE_STRUCT -> {
                        Pointer pointer;
                        if (ilib.isNull(result)) {
                            pointer = Pointer.NULL;
                        } else if (ilib.isPointer(result)) {
                            pointer = Pointer.nativeMemory(ilib.asPointer(result));
                        } else if (ilib.fitsInLong(result)) {
                            pointer = Pointer.nativeMemory(ilib.asLong(result));
                        } else if (ilib.hasArrayElements(result)) {
                            int size = (int) ilib.getArraySize(result);
                            byte[] bytes = new byte[size + 1];
                            for (int i = 0; i < size; i++) {
                                bytes[i] = (byte) ilib.readArrayElement(result, i);
                            }
                            pointer = Pointer.bytes(bytes);
                        } else {
                            pointer = Pointer.nativeMemory(result);
                        }
                        yield pointer.createReference();
                    }
                    // Needed because javac-generated default is not PE-friendly
                    default -> throw CompilerDirectives.shouldNotReachHere();
                };
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            Object retval;
            if (dict.getfunc != FieldGet.nil && !pyTypeCheck.ctypesSimpleInstance(inliningTarget, restype, getBaseClassNode, isSameTypeNode)) {
                retval = getFuncNode.execute(dict.getfunc, resultPtr, dict.size);
            } else {
                retval = fromBaseObjNode.execute(inliningTarget, restype, null, 0, resultPtr);
            }
            assert retval != null : "Should have raised an error earlier!";
            if (PGuards.isPNone(checker) || checker == null) {
                return retval;
            }
            return callNode.execute(checker, retval);
        }

        protected static StgDictObject getStgDict(Object restype, PyTypeStgDictNode pyTypeStgDictNode) {
            return pyTypeStgDictNode.execute(restype);
        }
    }

    /*
     * Convert a single Python object into a PyCArgObject and return it.
     */
    @SuppressWarnings("truffle-inlining")       // footprint reduction 124 -> 106
    protected abstract static class ConvParamNode extends Node {

        final void execute(VirtualFrame frame, Object obj, int index, argument pa) {
            execute(frame, obj, index, pa, true);
        }

        protected abstract void execute(VirtualFrame frame, Object obj, int index, argument pa, boolean allowRecursion);

        @Specialization
        void convParam(VirtualFrame frame, Object obj, int index, argument pa, boolean allowRecursion,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode toString,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached IsBuiltinObjectProfile profile,
                        @Cached PyNumberAsSizeNode asInt,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CArgObjectBuiltins.ParamFuncNode paramFuncNode,
                        @Cached ConvParamNode recursive,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (obj instanceof CDataObject cdata) {
                pa.stgDict = pyObjectStgDictNode.execute(cdata);
                PyCArgObject carg = paramFuncNode.execute(cdata, pa.stgDict);
                pa.ffi_type = carg.pffi_type;
                pa.valuePointer = carg.valuePointer;
                pa.keep = cdata;
                return;
            }

            if (PGuards.isPyCArg(obj)) {
                PyCArgObject carg = (PyCArgObject) obj;
                pa.stgDict = pyObjectStgDictNode.execute(carg.obj); // helpful for llvm backend
                pa.ffi_type = carg.pffi_type;
                pa.valuePointer = carg.valuePointer;
                pa.keep = carg;
                return;
            }

            /* check for None, integer, string or unicode and use directly if successful */
            if (obj == PNone.NONE) {
                pa.ffi_type = FFIType.ffi_type_pointer;
                pa.valuePointer = Pointer.NULL.createReference();
                return;
            }

            if (longCheckNode.execute(inliningTarget, obj)) {
                pa.ffi_type = FFIType.ffi_type_sint;
                try {
                    pa.valuePointer = Pointer.create(pa.ffi_type, pa.ffi_type.size, asInt.executeExact(frame, inliningTarget, obj), 0);
                } catch (PException e) {
                    e.expectOverflowError(inliningTarget, profile);
                    throw raiseNode.get(inliningTarget).raise(OverflowError, INT_TOO_LONG_TO_CONVERT);
                }
                return;
            }

            if (obj instanceof PBytes) {
                int len = bufferLib.getBufferLength(obj);
                byte[] bytes = new byte[len + 1];
                bufferLib.readIntoByteArray(obj, 0, bytes, 0, len);
                Pointer valuePtr = Pointer.bytes(bytes);
                pa.ffi_type = FFIType.ffi_type_pointer;
                pa.valuePointer = valuePtr.createReference();
                // Unlike CPython, we can attach the lifetime directly to the argument object
                new PointerReference(pa, valuePtr, PythonContext.get(this).getSharedFinalizer());
                return;
            }

            if (unicodeCheckNode.execute(inliningTarget, obj)) {
                TruffleString string = switchEncodingNode.execute(toString.execute(inliningTarget, obj), WCHAR_T_ENCODING);
                int len = string.byteLength(WCHAR_T_ENCODING);
                byte[] bytes = new byte[len + WCHAR_T_SIZE];
                copyToByteArrayNode.execute(string, 0, bytes, 0, len, WCHAR_T_ENCODING);
                Pointer valuePtr = Pointer.bytes(bytes);
                pa.ffi_type = FFIType.ffi_type_pointer;
                pa.valuePointer = valuePtr.createReference();
                // Unlike CPython, we can attach the lifetime directly to the argument object
                new PointerReference(pa, valuePtr, PythonContext.get(this).getSharedFinalizer());
                return;
            }

            Object arg = lookupAttr.execute(frame, inliningTarget, obj, CDataTypeBuiltins.T__AS_PARAMETER_);

            /*
             * Which types should we exactly allow here? integers are required for using Python
             * classes as parameters (they have to expose the '_as_parameter_' attribute)
             */
            if (arg != null && allowRecursion) {
                recursive.execute(frame, arg, index, pa, false);
                return;
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, DON_T_KNOW_HOW_TO_CONVERT_PARAMETER_D, index);
        }
    }

    enum BackendMode {
        NFI,
        LLVM,
        INTRINSIC
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({FFIType.FFI_TYPES.class, BackendMode.class})
    abstract static class ConvertParameterToBackendValueNode extends Node {
        public abstract Object execute(Node inliningTarget, Pointer ptr, FFIType ffiType, StgDictObject dict, BackendMode mode);

        @Specialization(guards = "ffiType.type == FFI_TYPE_SINT8 || ffiType.type == FFI_TYPE_UINT8")
        static byte doByte(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Cached PointerNodes.ReadByteNode readByteNode) {
            return readByteNode.execute(inliningTarget, pointer);
        }

        @Specialization(guards = "ffiType.type == FFI_TYPE_SINT16 || ffiType.type == FFI_TYPE_UINT16")
        static short doShort(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Cached PointerNodes.ReadShortNode readShortNode) {
            return readShortNode.execute(inliningTarget, pointer);
        }

        @Specialization(guards = "ffiType.type == FFI_TYPE_SINT32 || ffiType.type == FFI_TYPE_UINT32")
        static int doInt(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            return readIntNode.execute(inliningTarget, pointer);
        }

        @Specialization(guards = "ffiType.type == FFI_TYPE_SINT64 || ffiType.type == FFI_TYPE_UINT64")
        static long doLong(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode) {
            return readLongNode.execute(inliningTarget, pointer);
        }

        @Specialization(guards = "ffiType.type == FFI_TYPE_FLOAT")
        static float doFloat(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            return Float.intBitsToFloat(readIntNode.execute(inliningTarget, pointer));
        }

        @Specialization(guards = "ffiType.type == FFI_TYPE_DOUBLE")
        static double doDouble(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode) {
            return Double.longBitsToDouble(readLongNode.execute(inliningTarget, pointer));
        }

        @Specialization(guards = {"mode == NFI", "ffiType.type == FFI_TYPE_POINTER"})
        static Object doNFIPointer(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Shared @Cached PointerNodes.GetPointerValueAsObjectNode toNativeNode) {
            Pointer value = readPointerNode.execute(inliningTarget, pointer);
            return toNativeNode.execute(inliningTarget, value);
        }

        @Specialization(guards = {"mode == LLVM", "ffiType.type == FFI_TYPE_POINTER"})
        static Object doLLVMPointer(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Shared @Cached PointerNodes.GetPointerValueAsObjectNode toNativeNode) {
            Pointer value = readPointerNode.execute(inliningTarget, pointer);
            /*
             * TODO this is currently the same as NFI, but here we have an opportunity to pass
             * interop objects instead of allocating native memory.
             */
            return toNativeNode.execute(inliningTarget, value);
        }

        @Specialization(guards = {"mode == INTRINSIC", "ffiType.type == FFI_TYPE_POINTER"})
        static Object doIntrinsicPointer(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Shared @Cached PointerNodes.ReadPointerNode readPointerNode) {
            return readPointerNode.execute(inliningTarget, pointer);
        }

        @Specialization(guards = {"mode == NFI", "ffiType.type == FFI_TYPE_STRUCT"})
        @SuppressWarnings("unused")
        static Object doNFIStruct(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, @SuppressWarnings("unused") StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError, ErrorMessages.PASSING_STRUCTS_BY_VALUE_NOT_SUPPORTED);
        }

        @Specialization(guards = {"mode == LLVM", "ffiType.type == FFI_TYPE_STRUCT"})
        static Object doLLVMStruct(Node inliningTarget, Pointer pointer, @SuppressWarnings("unused") FFIType ffiType, StgDictObject dict,
                        @SuppressWarnings("unused") BackendMode mode,
                        @Cached PointerNodes.ReadBytesNode readBytesNode) {
            byte[] bytes = new byte[dict.size];
            // TODO avoid copying the bytes if possible
            readBytesNode.execute(inliningTarget, bytes, 0, pointer, dict.size);
            return CDataObject.createWrapper(dict, bytes);
        }
    }

    @Builtin(name = "PyObj_FromPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PyObjFromPtrNode extends PythonUnaryBuiltinNode {

        static Object converter(Object obj) { // , Object[] address) TODO
            // *address = PyLong_AsVoidPtr(obj);
            // return *address != NULL;
            return obj;
        }

        @Specialization
        static Object doit(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached AuditNode auditNode) {
            Object ob = converter(obj);
            auditNode.audit(inliningTarget, "ctypes.PyObj_FromPtr", "(O)", ob);
            return ob;
        }
    }

    @ImportStatic(CApiGuards.class)
    @Builtin(name = "Py_INCREF", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PyINCREFNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNativeWrapper(arg)")
        static Object doNative(Object arg,
                        @Shared("ref") @Cached AddRefCntNode incRefCntNode) {
            incRefCntNode.execute(arg, 1 /* that's what this function is for */ + //
                            1 /* that for returning it */);
            return arg;
        }

        @Specialization(guards = "!isNativeWrapper(arg)")
        static Object other(Object arg,
                        @Shared("ref") @Cached AddRefCntNode incRefCntNode,
                        @CachedLibrary(limit = "2") InteropLibrary ilib) {
            if (!ilib.isNull(arg) && ilib.isPointer(arg) && ilib.hasMembers(arg)) {
                return doNative(arg, incRefCntNode);
            }
            // do nothing and return object
            return arg;
        }
    }

    @ImportStatic(CApiGuards.class)
    @Builtin(name = "Py_DECREF", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PyDECREFNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNativeWrapper(arg)")
        static Object doNative(Object arg,
                        @Shared("dec") @Cached SubRefCntNode decRefCntNode,
                        @Shared("inc") @Cached AddRefCntNode incRefCntNode) {
            decRefCntNode.execute(arg, 1 /* that's what this function is for */);
            incRefCntNode.execute(arg, 1 /* that for returning it */);
            return arg;
        }

        @Specialization(guards = "!isNativeWrapper(arg)")
        static Object other(Object arg,
                        @Shared("dec") @Cached SubRefCntNode decRefCntNode,
                        @Shared("inc") @Cached AddRefCntNode incRefCntNode,
                        @CachedLibrary(limit = "2") InteropLibrary ilib) {
            if (!ilib.isNull(arg) && ilib.isPointer(arg) && ilib.hasMembers(arg)) {
                return doNative(arg, decRefCntNode, incRefCntNode);
            }
            // do nothing and return object
            return arg;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "llvmSym")
    @SuppressWarnings("static-method")
    protected static final class CallLLVMFunction implements TruffleObject {

        final Object llvmSym;

        private CallLLVMFunction(Object llvmSym) {
            this.llvmSym = llvmSym;
        }

        protected static Object create(Object sym, InteropLibrary lib) {
            if (lib.isExecutable(sym)) {
                return new CallLLVMFunction(sym);
            }
            return sym;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @CachedLibrary("this.llvmSym") InteropLibrary ilib) {
            try {
                for (int i = 0; i < arguments.length; i++) {
                    if (arguments[i] instanceof TruffleString) {
                        arguments[i] = toJavaStringNode.execute((TruffleString) arguments[i]);
                    }
                }
                return ilib.execute(llvmSym, arguments);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw PRaiseNode.getUncached().raise(RuntimeError, e);
            }
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 20
    abstract static class FailedCastCheckNode extends Node {
        abstract void execute(Object arg);

        @Specialization
        static void raiseError(Object arg,
                        @Cached PRaiseNode raiseNode,
                        @Cached IsTypeNode isTypeNode,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            Object clazz = isTypeNode.execute(inliningTarget, arg) ? arg : getClassNode.execute(inliningTarget, arg);
            throw raiseNode.raise(TypeError, CAST_ARGUMENT_2_MUST_BE_A_POINTER_TYPE_NOT_S, getNameNode.execute(inliningTarget, clazz));
        }
    }

    // cast_check_pointertype
    @GenerateUncached
    abstract static class CastCheckPtrTypeNode extends Node {

        private static final char[] sPzUZXO = "sPzUZXO".toCharArray();

        abstract void execute(Object arg);

        protected static boolean isPtrTypeObject(Object arg, PyTypeCheck pyTypeCheck) {
            return pyTypeCheck.isPyCPointerTypeObject(arg) || pyTypeCheck.isPyCFuncPtrTypeObject(arg);
        }

        @Specialization(guards = "isPtrTypeObject(arg, pyTypeCheck)")
        static void fastCheck(@SuppressWarnings("unused") Object arg,
                        @SuppressWarnings("unused") @Shared @Cached PyTypeCheck pyTypeCheck) {
        }

        @Specialization(replaces = {"fastCheck"})
        static void fullcheck(Object arg,
                        @Shared @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached FailedCastCheckNode failedCastCheckNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            if (isPtrTypeObject(arg, pyTypeCheck)) {
                return;
            }
            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict != null && dict.proto != null) {
                if (PGuards.isTruffleString(dict.proto)) {
                    int code = codePointAtIndexNode.execute((TruffleString) dict.proto, 0, TS_ENCODING);
                    if (strchr(sPzUZXO, code)) {
                        /* simple pointer types, c_void_p, c_wchar_p, BSTR, ... */
                        return;
                    }
                }
            }
            failedCastCheckNode.execute(arg);
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 64 -> 46
    protected abstract static class CastFunctionNode extends Node {

        abstract Object execute(Object ptr, Object src, Object ctype);

        @Specialization
        Object cast(Pointer ptr, Pointer srcObj, Pointer ctypeObj,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PythonObjectFactory factory,
                        @Cached CallNode callNode,
                        @Cached CastCheckPtrTypeNode castCheckPtrTypeNode,
                        @Cached PointerNodes.ReadPythonObject readPythonObject,
                        @Cached PointerNodes.WritePointerNode writePointerNode) {
            Object src = readPythonObject.execute(inliningTarget, srcObj);
            Object ctype = readPythonObject.execute(inliningTarget, ctypeObj);
            castCheckPtrTypeNode.execute(ctype);
            CDataObject result = (CDataObject) callNode.execute(ctype);

            /*
             * The casted objects '_objects' member:
             *
             * It must certainly contain the source objects one. It must contain the source object
             * itself.
             */
            if (src instanceof CDataObject cdata && pyTypeCheck.isCDataObject(cdata)) {
                /*
                 * PyCData_GetContainer will initialize src.b_objects, we need this so it can be
                 * shared
                 */
                PyCData_GetContainer(cdata, factory);

                if (cdata.b_objects == null) {
                    cdata.b_objects = factory.createDict();
                }
                result.b_objects = cdata.b_objects;
                if (PGuards.isDict(result.b_objects)) {
                    // PyLong_FromVoidPtr((void *)src);
                    PDict dict = (PDict) result.b_objects;
                    Object index = factory.createNativeVoidPtr(cdata);
                    dict.setDictStorage(setItem.execute(null, inliningTarget, dict.getDictStorage(), index, cdata));
                }
            }
            // memcpy(result.b_ptr, &ptr, sizeof(void *));
            writePointerNode.execute(inliningTarget, result.b_ptr, ptr);
            return result;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class CastFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new CastFunction();
            return new NativeFunction(f, PyObjectHashNode.executeUncached(f), NativeCAPISymbol.FUN_CAST.getName(), true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastFunctionNode castFunctionNode) {
            return castFunctionNode.execute(arguments[0], arguments[1], arguments[2]);
        }
    }

    @GenerateUncached
    protected abstract static class MemmoveNode extends Node {

        abstract Object execute(Object dest, Object src, Object size);

        @Specialization
        static Object memmove(Pointer destPtr, Pointer srcPtr, long size,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.MemcpyNode memcpyNode,
                        @Cached PythonObjectFactory factory) {
            memcpyNode.execute(inliningTarget, destPtr, srcPtr, (int) size);
            return factory.createNativeVoidPtr(destPtr);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class MemMoveFunction implements TruffleObject {

        final Object nativeMemmove;

        MemMoveFunction(Object nativeMemmove) {
            this.nativeMemmove = nativeMemmove;
        }

        protected static NativeFunction create(DLHandler handle, PythonContext context) {
            String name = NativeCAPISymbol.FUN_MEMMOVE.getName();
            Object sym, f;
            long adr;
            try {
                sym = InteropLibrary.getUncached().readMember(handle.library, name);
                adr = InteropLibrary.getUncached().asPointer(sym);
                f = new MemMoveFunction(sym);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                f = new MemMoveFunction(null);
                adr = PyObjectHashNode.executeUncached(f);
                // Not supported.. carry on
            }
            NativeFunction func = new NativeFunction(f, adr, name, true);
            registerAddress(context, adr, func);
            return func;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static class Execute {

            @Specialization
            static Object managed(@SuppressWarnings("unused") MemMoveFunction self, Object[] arguments,
                            @Cached MemmoveNode memmoveNode) {
                return memmoveNode.execute(arguments[0], arguments[1], arguments[2]);
            }
        }
    }

    @GenerateUncached
    protected abstract static class MemsetNode extends Node {

        abstract Object execute(Object dest, Object src, Object size);

        @Specialization
        static Object memset(Pointer ptr, int value, long size,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.WriteLongNode writeLongNode,
                        @Cached PointerNodes.WriteByteNode writeByteNode,
                        @Cached PythonObjectFactory factory) {
            byte b = (byte) value;
            long fill = 0;
            for (int i = 0; i < Long.BYTES * 8; i += 8) {
                fill |= (long) b << i;
            }
            int i;
            /*
             * Try to write in chunks of 8 bytes, it's faster and it helps avoiding converting
             * pointer storages when writing zeros.
             */
            for (i = 0; i < size / 8 * 8; i += 8) {
                writeLongNode.execute(inliningTarget, ptr.withOffset(i), fill);
            }
            /* Write the remainder if any */
            for (; i < size; i++) {
                writeByteNode.execute(inliningTarget, ptr.withOffset(i), b);
            }
            return factory.createNativeVoidPtr(ptr);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class MemSetFunction implements TruffleObject {

        final Object nativeMemset;

        MemSetFunction(Object nativeMemset) {
            this.nativeMemset = nativeMemset;
        }

        protected static NativeFunction create(DLHandler handle, PythonContext context) {
            String name = NativeCAPISymbol.FUN_MEMSET.getName();
            Object sym, f;
            long adr;
            try {
                sym = InteropLibrary.getUncached().readMember(handle.library, name);
                adr = InteropLibrary.getUncached().asPointer(sym);
                f = new MemSetFunction(sym);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                f = new MemSetFunction(null);
                adr = PyObjectHashNode.executeUncached(f);
                // Not supported.. carry on
            }
            NativeFunction func = new NativeFunction(f, adr, name, true);
            registerAddress(context, adr, func);
            return func;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static class Execute {

            @Specialization
            static Object managed(@SuppressWarnings("unused") MemSetFunction self, Object[] arguments,
                            @Cached MemsetNode memsetNode) {
                return memsetNode.execute(arguments[0], arguments[1], arguments[2]);
            }
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 22
    protected abstract static class StringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static Object string_at(Pointer ptr, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached PointerNodes.ReadBytesNode read,
                        @Cached PointerNodes.StrLenNode strLenNode,
                        @Cached AuditNode auditNode) {
            auditNode.audit(inliningTarget, "ctypes.string_at", factory.createNativeVoidPtr(ptr), size);
            if (size == -1) {
                size = strLenNode.execute(inliningTarget, ptr);
            }
            return factory.createBytes(read.execute(inliningTarget, ptr, size));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class StringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new StringAtFunction();
            return new NativeFunction(f, PyObjectHashNode.executeUncached(f), NativeCAPISymbol.FUN_STRING_AT.getName(), true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached StringAtFunctionNode stringAtFunctionNode) {
            return stringAtFunctionNode.execute(arguments[0], arguments[1]);
        }

    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 30
    protected abstract static class WStringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static TruffleString wstring_at(Pointer ptr, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached AuditNode auditNode,
                        @Cached PointerNodes.ReadBytesNode read,
                        @Cached PointerNodes.WCsLenNode wCsLenNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            auditNode.audit(inliningTarget, "ctypes.wstring_at", factory.createNativeVoidPtr(ptr), size);
            if (size == -1) {
                size = wCsLenNode.execute(inliningTarget, ptr);
            }
            byte[] bytes = read.execute(inliningTarget, ptr, size * WCHAR_T_SIZE);
            TruffleString s = fromByteArrayNode.execute(bytes, WCHAR_T_ENCODING);
            return switchEncodingNode.execute(s, TS_ENCODING);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class WStringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new WStringAtFunction();
            return new NativeFunction(f, PyObjectHashNode.executeUncached(f), NativeCAPISymbol.FUN_WSTRING_AT.getName(), true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached WStringAtFunctionNode wStringAtFunctionNode) {
            return wStringAtFunctionNode.execute(arguments[0], arguments[1]);
        }
    }

    private static final TruffleString T_PLACEHOLDER_D_COMMA = tsLiteral("%d,");
    private static final TruffleString T_PLACEHOLDER_D_RPAREN = tsLiteral("%d)");

    /*
     * Allocate a memory block for a pep3118 format string, adding the given prefix (if non-null),
     * an additional shape prefix, and a suffix. Returns NULL on failure, with the error indicator
     * set. If called with a suffix of NULL the error indicator must already be set.
     */
    static TruffleString _ctypes_alloc_format_string_with_shape(int ndim, int[] shape,
                    TruffleString prefix, TruffleString suffix, TruffleStringBuilder.AppendStringNode appendStringNode,
                    TruffleStringBuilder.ToStringNode toStringNode, SimpleTruffleStringFormatNode formatNode) {
        TruffleStringBuilder buf = TruffleStringBuilder.create(TS_ENCODING);
        if (prefix != null) {
            appendStringNode.execute(buf, prefix);
        }
        if (ndim > 0) {
            /* Add the prefix "(shape[0],shape[1],...,shape[ndim-1])" */
            appendStringNode.execute(buf, T_LPAREN);
            TruffleString fmt = T_PLACEHOLDER_D_COMMA;
            for (int k = 0; k < ndim; ++k) {
                if (k == ndim - 1) {
                    fmt = T_PLACEHOLDER_D_RPAREN;
                }
                appendStringNode.execute(buf, formatNode.format(fmt, shape[k]));
            }
        }
        if (suffix != null) {
            appendStringNode.execute(buf, suffix);
        }
        return toStringNode.execute(buf);
    }
}
