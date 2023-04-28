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
import static com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins.paramFunc;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCData_FromBaseObj;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCData_GetContainer;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES.FFI_TYPE_STRUCT;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins.PyCFuncPtrFromDllNode.strchr;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T__TYPE_;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CTYPES;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENT_D;
import static com.oracle.graal.python.nodes.ErrorMessages.BYREF_ARGUMENT_MUST_BE_A_CTYPES_INSTANCE_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.CAST_ARGUMENT_2_MUST_BE_A_POINTER_TYPE_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.COULD_NOT_CONVERT_THE_HANDLE_ATTRIBUTE_TO_A_POINTER;
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
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;

import java.io.IOException;
import java.util.Arrays;
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
import com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltins.PyLong_AsVoidPtr;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltinsClinicProviders.DyldSharedCacheContainsPathClinicProviderGen;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.GetBytesFromNativePointerNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectHashNodeGen;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
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
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
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
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.nfi.api.SignatureLibrary;

@CoreFunctions(defineModule = J__CTYPES)
public class CtypesModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_DL_ERROR = tsLiteral("dlerror");
    private static final TruffleString T_DL_OPEN_ERROR = tsLiteral("dlopen() error");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CtypesModuleBuiltinsFactory.getFactories();
    }

    private DLHandler rtldDefault;
    private Object dyldSharedCacheContainsPathFunction;
    @CompilationFinal private Object strlenFunction;
    @CompilationFinal private Object memcpyFunction;

    private static final String J_NFI_LANGUAGE = "nfi";

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

    protected static final int DICTFLAG_FINAL = 0x1000;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("_pointer_type_cache", core.factory().createDict());
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
            handle = DlOpenNode.loadNFILibrary(context, NFIBackend.NATIVE, J_EMPTY_STRING, rtldLocal);
            setCtypeNFIHelpers(this, context, handle);
        } else {
            try {
                CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(null, context, T_EMPTY_STRING, T_EMPTY_STRING);
                handle = new DLHandler(cApiContext.getLLVMLibrary(), 0, J_EMPTY_STRING, true);
                setCtypeLLVMHelpers(this, handle);
            } catch (ApiInitException e) {
                throw e.reraise(PConstructAndRaiseNode.getUncached(), null);
            } catch (ImportException e) {
                throw e.reraise(PConstructAndRaiseNode.getUncached(), null);
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

    @Builtin(name = "get_errno", maxNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class GetErrnoNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getErrno(@SuppressWarnings("unused") PythonModule module,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.get_errno");
            return get_errno(getContext(), getLanguage());
        }

        static Object get_errno(PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = CtypesThreadState.get(context, language);
            return ctypes.errno;
        }
    }

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
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.set_errno", newErrno);
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
        Object POINTER(VirtualFrame frame, Object cls,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @Cached IsTypeNode isTypeNode,
                        @Cached CallNode callNode,
                        @Cached GetNameNode getNameNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached SimpleTruffleStringFormatNode formatNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), getLanguage());
            Object result = getItem.execute(frame, ctypes.ptrtype_cache, cls);
            if (result != null) {
                return result;
            }
            Object key;
            if (PGuards.isString(cls)) {
                TruffleString name = toTruffleStringNode.execute(cls);
                TruffleString buf = formatNode.format("LP_%s", name);
                Object[] args = new Object[]{buf, PyCPointer, factory().createDict()};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = factory().createNativeVoidPtr(result);
            } else if (isTypeNode.execute(cls)) {
                TruffleString buf = formatNode.format("LP_%s", getNameNode.execute(cls));
                PTuple bases = factory().createTuple(new Object[]{PyCPointer});
                Object[] args = new Object[]{buf, bases, factory().createDict(new PKeyword[]{new PKeyword(T__TYPE_, cls)})};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = cls;
            } else {
                throw raise(TypeError, MUST_BE_A_CTYPES_TYPE);
            }
            HashingStorage newStorage = setItem.execute(frame, ctypes.ptrtype_cache, key, result);
            assert newStorage == ctypes.ptrtype_cache;
            return result;
        }
    }

    @Builtin(name = "pointer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PointerObjectNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object pointer(VirtualFrame frame, Object arg,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PointerTypeNode callPOINTER,
                        @Cached CallNode callNode,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), getLanguage());
            Object typ = getItem.execute(frame, ctypes.ptrtype_cache, getClassNode.execute(inliningTarget, arg));
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
        Object buffer_info(Object arg,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict == null) {
                dict = pyObjectStgDictNode.execute(arg);
            }
            if (dict == null) {
                throw raise(TypeError, NOT_A_CTYPES_TYPE_OR_OBJECT);
            }
            Object[] shape = new Object[dict.ndim];
            for (int i = 0; i < dict.ndim; ++i) {
                shape[i] = dict.shape[i];
            }

            return factory().createTuple(new Object[]{dict.format, dict.ndim, factory().createTuple(shape)});
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
        Object resize(CDataObject obj, int size,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyObjectStgDictNode.execute(obj);
            if (dict == null) {
                throw raise(TypeError, EXCEPTED_CTYPES_INSTANCE);
            }
            if (size < dict.size) {
                throw raise(ValueError, MINIMUM_SIZE_IS_D, dict.size);
            }
            if (obj.b_needsfree == 0) {
                throw raise(ValueError, MEMORY_CANNOT_BE_RESIZED_BECAUSE_THIS_OBJECT_DOESN_T_OWN_IT);
            }
            /*- TODO
            if (size <= sizeof(obj.b_value)) {
                // internal default buffer is large enough
                obj.b_size = size;
                return PNone.NONE;
            }
            Object ptr;
            if (!_CDataObject_HasExternalBuffer(obj)) {
                /*
                 * We are currently using the objects default buffer, but it isn't large enough any
                 * more.
                 * /
                ptr = PyMem_Malloc(size);
                memset(ptr, 0, size);
                memmove(ptr, obj.b_ptr, obj.b_size);
            } else {
                ptr = PyMem_Realloc(obj.b_ptr, size);
            }
            obj.b_ptr.ptr = ptr;
            */
            obj.b_size = size;
            return PNone.NONE;
        }
    }

    @Builtin(name = "dlopen", minNumOfPositionalArgs = 1, parameterNames = {"$self", "name", "mode"}, declaresExplicitSelf = true)
    // TODO: 'name' might need to be processed using FSConverter.
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString, defaultValue = "T_EMPTY_STRING", useDefaultForNone = true)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "Integer.MIN_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    protected abstract static class DlOpenNode extends PythonTernaryClinicBuiltinNode {

        private static final TruffleString MACOS_Security_LIB = tsLiteral("/System/Library/Frameworks/Security.framework/Security");
        private static final TruffleString MACOS_CoreFoundation_LIB = tsLiteral("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation");
        // "LibFFILibrary(" + handle + ")"
        private static final int LIBFFI_ADR_FORMAT_START = "LibFFILibrary".length() + 1;

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
            String handleStr;
            try {
                handleStr = lib.asString(lib.toDisplayString(handler));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("toDisplayString result not convertible to String");
            }
            String adrStr = handleStr.substring(LIBFFI_ADR_FORMAT_START, handleStr.length() - 1);
            try {
                long adr = Long.parseLong(adrStr, 10);
                return new DLHandler(handler, adr, name, false);
            } catch (NumberFormatException e) {
                // TODO handle exception [GR-38101]
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        private static Object load(PythonContext context, String src, String name) {
            Source loadSrc = Source.newBuilder(J_NFI_LANGUAGE, src, "load:" + name).internal(true).build();
            return context.getEnv().parseInternal(loadSrc).call();
        }

        @TruffleBoundary
        protected static Object loadLLVMLibrary(PythonContext context, TruffleString path) throws ImportException, ApiInitException, IOException {
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
        Object py_dl_open(VirtualFrame frame, PythonModule self, TruffleString name, int m,
                        @Cached PyObjectHashNode hashNode,
                        @Cached AuditNode auditNode,
                        @Cached CodePointLengthNode codePointLengthNode,
                        @Cached EndsWithNode endsWithNode,
                        @Cached EqualNode eqNode) {
            auditNode.audit("ctypes.dlopen", name);
            if (name.isEmpty()) {
                return factory().createNativeVoidPtr(((CtypesModuleBuiltins) self.getBuiltins()).rtldDefault);
            }

            int mode = m != Integer.MIN_VALUE ? m : RTLD_LOCAL.getValueIfDefined();
            mode |= RTLD_NOW.getValueIfDefined();
            PythonContext context = getContext();
            DLHandler handle;
            Exception exception = null;
            boolean loadWithLLVM = !context.getEnv().isNativeAccessAllowed() || endsWithNode.executeBoolean(frame, name, context.getSoAbi(), 0, codePointLengthNode.execute(name, TS_ENCODING));
            try {
                if (loadWithLLVM) {
                    Object handler = loadLLVMLibrary(context, name);
                    long adr = hashNode.execute(frame, handler);
                    handle = new DLHandler(handler, adr, name.toJavaStringUncached(), true);
                    registerAddress(context, handle.adr, handle);
                    return factory().createNativeVoidPtr(handle);
                } else {
                    CtypesThreadState ctypes = CtypesThreadState.get(context, getLanguage());
                    /*-
                     TODO: (mq) cryptography in macos isn't always compatible with ctypes.
                     */
                    if (!eqNode.execute(name, MACOS_Security_LIB, TS_ENCODING) && !eqNode.execute(name, MACOS_CoreFoundation_LIB, TS_ENCODING)) {
                        handle = loadNFILibrary(context, ctypes.backendType, name.toJavaStringUncached(), mode);
                        registerAddress(context, handle.adr, handle);
                        return factory().createNativeVoidPtr(handle, handle.adr);
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            throw raise(OSError, getErrMsg(exception));
        }
    }

    static DLHandler getHandler(Object ptr, PythonContext context) {
        Object adr = ptr;
        if (PGuards.isInteger(adr)) {
            adr = getObjectAtAddress(context, (Long) adr);
        }
        if (adr instanceof DLHandler) {
            return (DLHandler) adr;
        }
        return null;
    }

    static NativeFunction getNativeFunction(Object ptr, PythonContext context) {
        Object adr = ptr;
        if (PGuards.isInteger(adr)) {
            adr = getObjectAtAddress(context, (Long) adr);
        }
        if (adr instanceof NativeFunction) {
            return (NativeFunction) adr;
        }
        return null;
    }

    @Builtin(name = "dlclose", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class DlCloseNode extends PythonUnaryBuiltinNode {

        private static boolean dlclose(Object ptr, PythonContext context) {
            DLHandler handle = getHandler(ptr, context);
            if (handle != null) {
                handle.isClosed = true;
                return true;
            }
            return false;
        }

        @Specialization
        Object py_dl_close(Object h,
                        @Cached PyLong_AsVoidPtr asVoidPtr) {
            Object handle = asVoidPtr.execute(h);

            if (!dlclose(handle, getContext())) {
                throw raise(OSError, T_DL_ERROR);
            }
            return PNone.NONE;
        }
    }

    protected static DLHandler getHandleFromLongObject(Object obj, PythonContext context, PyLong_AsVoidPtr asVoidPtr, PRaiseNode raiseNode) {
        Object h = null;
        try {
            h = asVoidPtr.execute(obj);
        } catch (PException e) {
            // throw later.
        }
        DLHandler handle = h != null ? getHandler(h, context) : null;
        if (handle == null) {
            throw raiseNode.raise(ValueError, COULD_NOT_CONVERT_THE_HANDLE_ATTRIBUTE_TO_A_POINTER);
        }
        return handle;
    }

    protected static NativeFunction getFunctionFromLongObject(Object obj, PythonContext context,
                    PyLong_AsVoidPtr asVoidPtr) {
        if (obj instanceof NativeFunction) {
            return (NativeFunction) obj;
        }
        Object f = asVoidPtr.execute(obj);
        return f != null ? getNativeFunction(f, context) : null;
    }

    protected abstract static class CtypesDlSymNode extends PNodeWithRaise {

        protected abstract Object execute(VirtualFrame frame, DLHandler handle, Object n, PythonBuiltinClassType error);

        @Specialization
        protected Object ctypes_dlsym(VirtualFrame frame, DLHandler handle, Object n, PythonBuiltinClassType error,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaStringNode asString,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached PythonObjectFactory factory) {
            String name = asString.execute(n);
            if (handle == null || handle.isClosed) {
                throw raise(error, T_DL_ERROR);
            }
            try {
                Object sym = ilib.readMember(handle.library, name);
                boolean isManaged = handle.isManaged;
                long adr = isManaged ? hashNode.execute(frame, sym) : ilib.asPointer(sym);
                sym = isManaged ? CallLLVMFunction.create(sym, ilib) : sym;
                NativeFunction func = new NativeFunction(sym, adr, name, isManaged);
                registerAddress(getContext(), adr, func);
                // PyLong_FromVoidPtr(ptr);
                if (!isManaged) {
                    return factory.createNativeVoidPtr(func, adr);
                } else {
                    return factory.createNativeVoidPtr(func);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw raise(error, e);
            }
        }
    }

    @Builtin(name = "dlsym", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    protected abstract static class DlSymNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object py_dl_sym(VirtualFrame frame, Object obj, Object name,
                        @Cached PyLong_AsVoidPtr asVoidPtr,
                        @Cached AuditNode auditNode,
                        @Cached CtypesDlSymNode dlSymNode) {
            auditNode.audit("ctypes.dlsym/handle", obj, name);
            DLHandler handle = getHandleFromLongObject(obj, getContext(), asVoidPtr, getRaiseNode());
            return dlSymNode.execute(frame, handle, name, OSError);
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
        Object doBytes(PythonModule self, PBytes path,
                        @Cached ToBytesNode toBytesNode,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib) {
            if (!hasDynamicLoaderCache()) {
                throw raise(NotImplementedError, S_SYMBOL_IS_MISSING, DYLD_SHARED_CACHE_CONTAINS_PATH);
            }

            CtypesModuleBuiltins builtins = (CtypesModuleBuiltins) self.getBuiltins();
            Object cachedFunction = builtins.dyldSharedCacheContainsPathFunction;
            if (cachedFunction == null) {
                cachedFunction = initializeDyldSharedCacheContainsPathFunction(getContext(), builtins);
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
        Object align_func(Object obj,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            dict = pyObjectStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            throw raise(TypeError, NO_ALIGNMENT_INFO);
        }
    }

    @Builtin(name = "sizeof", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class SizeOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doit(Object obj,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.size;
            }

            if (pyTypeCheck.isCDataObject(obj)) {
                return ((CDataObject) obj).b_size;
            }
            throw raise(TypeError, THIS_TYPE_HAS_NO_SIZE);
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
                        @Shared @Cached InlinedGetClassNode getClassNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PtrNodes.MemcpyNode memcpyNode) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(null, obj, offset, inliningTarget, getClassNode);
            }
            FFIType ffiType = pyObjectStgDictNode.execute(obj).ffi_type_pointer;
            PyCArgObject parg = factory().createCArgObject();
            parg.tag = 'P';
            parg.pffi_type = FFIType.ffi_type_uint8_array;
            parg.obj = obj;
            if (!(obj.b_ptr.ptr instanceof PtrValue.ByteArrayStorage)) {
                PtrValue convertedValue = PtrValue.allocate(parg.pffi_type, ffiType.size);
                if (!obj.b_ptr.isNil()) {
                    memcpyNode.execute(convertedValue, obj.b_ptr, ffiType.size);
                }
                obj.b_ptr.ptr = convertedValue.ptr;
                obj.b_ptr.offset = 0;
            }
            parg.value = obj.b_ptr.ref(offset);

            return parg;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object obj, Object off,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedGetClassNode getClassNode) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            TruffleString name = GetNameNode.getUncached().execute(clazz);
            throw raise(TypeError, BYREF_ARGUMENT_MUST_BE_A_CTYPES_INSTANCE_NOT_S, name);
        }
    }

    protected static Object getObjectAt(PythonContext context, Object ptr) {
        if (PGuards.isInteger(ptr)) {
            return getObjectAtAddress(context, (Long) ptr);
        }
        return ptr;
    }

    @Builtin(name = "call_function", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class CallFunctionNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object call_function(VirtualFrame frame, PythonModule ctypesModule, Object f, PTuple arguments,
                        @Cached AuditNode auditNode,
                        @Cached CallProcNode callProcNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached PyLong_AsVoidPtr asVoidPtr) {
            // Object func = _parse_voidp(tuple[0]);
            NativeFunction func = (NativeFunction) getObjectAt(getContext(), asVoidPtr.execute(f));
            auditNode.audit("ctypes.call_function", func, arguments);
            return callProcNode.execute(frame, func,
                            getArray.execute(arguments.getSequenceStorage()),
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
        Object doit(CDataObject obj,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached AuditNode auditNode) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(null, obj);
            }
            auditNode.audit("ctypes.addressof", obj);
            return obj.b_ptr;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object o) {
            throw raise(TypeError, INVALID_TYPE);
        }
    }

    @Builtin(name = "call_cdeclfunction", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class CallCdeclfunctionNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object doit(VirtualFrame frame, PythonModule ctypesModule, Object f, PTuple arguments,
                        @Cached AuditNode auditNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CallProcNode callProcNode,
                        @Cached PyLong_AsVoidPtr asVoidPtr) {
            // Object func = _parse_voidp(tuple[0]);
            NativeFunction func = (NativeFunction) getObjectAt(getContext(), asVoidPtr.execute(f));
            auditNode.audit("ctypes.call_function", func, arguments);
            return callProcNode.execute(frame, func,
                            getArray.execute(arguments.getSequenceStorage()),
                            FUNCFLAG_CDECL, /* flags */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.argtypes */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.converters */
                            null, /* self.restype */
                            null);
        }
    }

    protected static final class argument {
        FFIType ffi_type;
        StgDictObject stgDict;
        Object value;
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
    protected abstract static class CallProcNode extends PNodeWithRaise {

        abstract Object execute(VirtualFrame frame, NativeFunction pProc, Object[] argtuple, int flags, Object[] argtypes, Object[] converters, Object restype, Object checker);

        /*
         * bpo-13097: Max number of arguments _ctypes_callproc will accept.
         *
         * This limit is enforced for the `alloca()` call in `_ctypes_callproc`, to avoid allocating
         * a massive buffer on the stack.
         */
        protected static final int CTYPES_MAX_ARGCOUNT = 1024;

        @Specialization(guards = "!pProc.isManaged() || pProc.isLLVM()")
        Object _ctypes_callproc(VirtualFrame frame,
                        NativeFunction pProc,
                        Object[] argarray,
                        @SuppressWarnings("unused") int flags,
                        Object[] argtypes, Object[] converters,
                        Object restype,
                        Object checker,
                        @Cached ConvParamNode convParamNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CallNode callNode,
                        @Cached GetBytesFromNativePointerNode getNativeBytes,
                        @Cached GetResultNode getResultNode,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int argcount = argarray.length;
            if (argcount > CTYPES_MAX_ARGCOUNT) {
                throw raise(ArgError, TOO_MANY_ARGUMENTS_D_MAXIMUM_IS_D, argcount, CTYPES_MAX_ARGCOUNT);
            }

            argument[] args = new argument[argcount];
            Object[] avalues = new Object[argcount];
            FFIType[] atypes = new FFIType[argcount];
            int argtype_count = argtypes != null ? argtypes.length : 0;

            /* Convert the arguments */
            final boolean isLLVM = pProc.isLLVM();
            for (int i = 0; i < argcount; ++i) {
                args[i] = new argument();
                Object arg = argarray[i]; /* borrowed ref */
                /*
                 * For cdecl functions, we allow more actual arguments than the length of the
                 * argtypes tuple. This is checked in _ctypes::PyCFuncPtr_Call
                 */
                if (converters != null && argtype_count > i) {
                    Object v;
                    try {
                        v = callNode.execute(frame, converters[i], argtypes[i], arg);
                    } catch (PException e) {
                        throw raise(ArgError, ARGUMENT_D, i + 1);
                    }

                    convParamNode.execute(frame, v, i + 1, args[i]);
                } else {
                    convParamNode.execute(frame, arg, i + 1, args[i]);
                }
                FFIType ffiType = args[i].ffi_type;
                atypes[i] = ffiType;
                if (arg instanceof PyCFuncPtrObject) {
                    atypes[i] = new FFIType(atypes[i], ((PyCFuncPtrObject) arg).thunk);
                }
                Object value = args[i].value;
                if (ffiType.type.isArray()) {
                    if (!isLLVM) {
                        value = getContext().getEnv().asGuestValue(value);
                    } else {
                        if (ffiType.type == FFI_TYPE_STRUCT) {
                            assert value instanceof byte[] : "It should be byte[]!";
                            assert args[i].stgDict != null : "We need stgDict for Structs";
                            value = CDataObject.createWrapper(args[i].stgDict, (byte[]) value);
                        } else {
                            value = getContext().getEnv().asGuestValue(value);
                        }
                    }
                }
                avalues[i] = value;
            }

            FFIType rtype = FFIType.ffi_type_sint;
            if (restype != null) {
                StgDictObject dict = pyTypeStgDictNode.execute(restype);
                if (dict != null) {
                    rtype = dict.ffi_type_pointer;
                }
            }
            Object result;
            if (isLLVM) {
                result = callManagedFunction(pProc, avalues, ilib);
            } else {
                result = callNativeFunction(pProc, avalues, atypes, rtype, ilib, appendStringNode, toStringNode);
            }
            if (rtype.type.isArray()) {
                if (ilib.isNull(result)) {
                    return PNone.NONE;
                } else if (ilib.hasArrayElements(result)) {
                    try {
                        long resSize = ilib.getArraySize(result);
                        byte[] bytes = new byte[(int) resSize];
                        for (int i = 0; i < resSize; i++) {
                            bytes[i] = (byte) ilib.readArrayElement(result, i);
                        }
                        result = bytes;
                    } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else if (!isLLVM && ilib.isPointer(result)) {
                    result = getNativeBytes.execute(result, -1);
                } else if (ilib.isNumber(result)) {
                    byte[] bytes = new byte[rtype.size];
                    CtypesNodes.setValue(rtype.type, bytes, 0, result);
                    result = bytes;
                } else {
                    throw raise(NotImplementedError, toTruffleStringUncached("Returned object is not supported."));
                }
            } else {
                try {
                    switch (rtype.type) {
                        case FFI_TYPE_UINT8:
                        case FFI_TYPE_SINT8:
                            result = ilib.asByte(result);
                            break;
                        case FFI_TYPE_UINT16:
                        case FFI_TYPE_SINT16:
                            result = ilib.asShort(result);
                            break;
                        case FFI_TYPE_UINT32:
                        case FFI_TYPE_SINT32:
                            result = ilib.asInt(result);
                            break;
                        case FFI_TYPE_SINT64:
                        case FFI_TYPE_UINT64:
                            result = ilib.asLong(result);
                            break;
                    }
                } catch (UnsupportedMessageException e) {
                    // pass through.
                }
            }

            if (!PGuards.isPNone(checker)) {
                if (rtype.type.isArray()) {
                    throw raise(NotImplementedError, toTruffleStringUncached("Array checker is not implemented."));
                }
                return callNode.execute(checker, result);
            }

            // return result;
            /*- TODO (mq) require more support from NFI.
            Object resbuf = alloca(max(rtype.size, sizeof(ffi_arg)));
            _call_function_pointer(flags, pProc, avalues, atypes, rtype, resbuf, argcount, state);
            */
            return getResultNode.execute(restype, rtype, result, checker, getRaiseNode());
        }

        @Specialization(guards = "pProc.isManaged()")
        Object doManaged(NativeFunction pProc,
                        Object[] argarray,
                        @SuppressWarnings("unused") int flags,
                        @SuppressWarnings("unused") Object[] argtypes,
                        @SuppressWarnings("unused") Object[] converters,
                        @SuppressWarnings("unused") Object restype,
                        @SuppressWarnings("unused") Object checker,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return callManagedFunction(pProc, argarray, ilib);
        }

        Object callManagedFunction(NativeFunction pProc, Object[] argarray, InteropLibrary ilib) {
            try {
                return ilib.execute(pProc.sym, argarray);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raise(RuntimeError, FFI_CALL_FAILED);
            } catch (UnsupportedSpecializationException ee) {
                throw raise(NotImplementedError, toTruffleStringUncached("require backend support.")); // TODO:
                                                                                                       // llvm/GR-???
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
        Object callNativeFunction(NativeFunction pProc, Object[] avalues, FFIType[] atypes, FFIType restype,
                        InteropLibrary ilib, TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode) {
            Object function;
            if (pProc.function == null) {
                TruffleString signature = FFIType.buildNFISignature(atypes, restype, appendStringNode, toStringNode);
                try {
                    function = getFunction(pProc, signature.toJavaStringUncached());
                } catch (Exception e) {
                    throw raise(RuntimeError, FFI_PREP_CIF_FAILED);
                }
                pProc.atypes = atypes;
                pProc.function = function;
                pProc.signature = signature;
            } else {
                if (equals(atypes, pProc.atypes)) {
                    function = pProc.function;
                } else {
                    TruffleString signature = FFIType.buildNFISignature(atypes, restype, appendStringNode, toStringNode);
                    try {
                        function = getFunction(pProc, signature.toJavaStringUncached());
                    } catch (Exception e) {
                        throw raise(RuntimeError, FFI_PREP_CIF_FAILED);
                    }
                }
            }
            try {
                return ilib.execute(function, avalues);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raise(RuntimeError, FFI_CALL_FAILED);
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

    abstract static class GetResultNode extends Node {

        abstract Object execute(Object restype, FFIType rtype, Object result, Object checker, PRaiseNode raiseNode);

        /*
         * Convert the C value in result into a Python object, depending on restype.
         *
         * - If restype is NULL, return a Python integer. - If restype is None, return None. - If
         * restype is a simple ctypes type (c_int, c_void_p), call the type's getfunc, pass the
         * result to checker and return the result. - If restype is another ctypes type, return an
         * instance of that. - Otherwise, call restype and return the result.
         */
        @Specialization(guards = "restype == null")
        static Object asIs(@SuppressWarnings("unused") Object restype, FFIType rtype, Object result, @SuppressWarnings("unused") Object checker,
                        @SuppressWarnings("unused") PRaiseNode raiseNode) {
            assert !rtype.type.isArray() : "need array conversion!";
            return result;
        }

        @Specialization(guards = {"restype != null", "dict == null"})
        static Object callResType(Object restype, FFIType rtype, Object result, @SuppressWarnings("unused") Object checker,
                        @SuppressWarnings("unused") PRaiseNode raiseNode,
                        @SuppressWarnings("unused") @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @SuppressWarnings("unused") @Bind("getStgDict(restype, pyTypeStgDictNode)") StgDictObject dict,
                        @Cached CallNode callNode) {
            assert !rtype.type.isArray() : "must be an int!";
            // return PyObject_CallFunction(restype, "i", *(int *)result);
            return callNode.execute(restype, result);
        }

        @Specialization(guards = {"restype != null", "dict != null"})
        static Object callGetFunc(Object restype, FFIType rtype, Object result, Object checker, PRaiseNode raiseNode,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Bind("getStgDict(restype, pyTypeStgDictNode)") StgDictObject dict,
                        @Cached CallNode callNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached InlinedIsSameTypeNode isSameTypeNode,
                        @Cached GetFuncNode getFuncNode,
                        @Cached PythonObjectFactory factory) {
            Object retval;
            PtrValue r;
            if (rtype.type.isArray()) {
                assert result instanceof byte[] : "byte[] should have been extracted after native call";
                r = PtrValue.bytes((byte[]) result);
            } else {
                r = PtrValue.create(rtype, rtype.size, result, 0);
            }
            if (dict.getfunc != FieldGet.nil && !pyTypeCheck.ctypesSimpleInstance(inliningTarget, restype, getBaseClassNode, isSameTypeNode)) {
                retval = getFuncNode.execute(dict.getfunc, r, dict.size);
            } else {
                retval = PyCData_FromBaseObj(restype, null, 0, r, factory, raiseNode, pyTypeStgDictNode);
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
    protected abstract static class ConvParamNode extends PNodeWithRaise {

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
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached PtrNodes.MemcpyNode memcpyNode,
                        @Cached PtrNodes.ConvertToNFIParameter convertToNFIParameter,
                        @Cached ConvParamNode recursive,
                        @Cached PythonObjectFactory factory) {
            StgDictObject dict = pyObjectStgDictNode.execute(obj);
            pa.stgDict = dict;
            if (dict != null) {
                assert dict.paramfunc != -1;
                /* If it has an stgdict, it is a CDataObject */
                PyCArgObject carg = paramFunc(dict.paramfunc, (CDataObject) obj, dict, factory, codePointAtIndexNode, memcpyNode);
                pa.ffi_type = carg.pffi_type;
                pa.value = convertToNFIParameter.execute(carg.value, carg.pffi_type);
                return;
            }

            if (PGuards.isPyCArg(obj)) {
                PyCArgObject carg = (PyCArgObject) obj;
                pa.ffi_type = carg.pffi_type;
                pa.stgDict = pyObjectStgDictNode.execute(carg.obj); // helpful for llvm backend
                pa.value = convertToNFIParameter.execute(carg.value, carg.pffi_type);
                return;
            }

            /* check for None, integer, string or unicode and use directly if successful */
            if (obj == PNone.NONE) {
                pa.ffi_type = FFIType.ffi_type_pointer;
                pa.value = getContext().getEnv().asGuestValue(null); // TODO check
                return;
            }

            if (longCheckNode.execute(obj)) {
                pa.ffi_type = FFIType.ffi_type_sint;
                try {
                    pa.value = asInt.executeExact(frame, obj);
                } catch (PException e) {
                    e.expectOverflowError(inliningTarget, profile);
                    throw raise(OverflowError, INT_TOO_LONG_TO_CONVERT);
                }
                return;
            }

            if (obj instanceof PBytes) {
                // pa.ffi_type = FFIType.ffi_type_pointer;
                pa.ffi_type = FFIType.ffi_type_sint8_array;
                int len = bufferLib.getBufferLength(obj);
                byte[] bytes = new byte[len + 1];
                bufferLib.readIntoByteArray(obj, 0, bytes, 0, len);
                pa.value = bytes;
                return;
            }

            if (unicodeCheckNode.execute(obj)) {
                // TODO determine this better
                Encoding wCharTEncoding = PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? UTF_16 : UTF_32;
                TruffleString string = switchEncodingNode.execute(toString.execute(obj), wCharTEncoding);
                int len = string.byteLength(wCharTEncoding);
                byte[] bytes = new byte[len + (wCharTEncoding == UTF_16 ? 2 : 4)];
                copyToByteArrayNode.execute(string, 0, bytes, 0, len, wCharTEncoding);
                pa.ffi_type = FFIType.ffi_type_sint8_array;
                pa.value = bytes;
                return;
            }

            Object arg = lookupAttr.execute(frame, obj, CDataTypeBuiltins.T__AS_PARAMETER_);

            /*
             * Which types should we exactly allow here? integers are required for using Python
             * classes as parameters (they have to expose the '_as_parameter_' attribute)
             */
            if (arg != null && allowRecursion) {
                recursive.execute(frame, arg, index, pa, false);
                return;
            }
            throw raise(TypeError, DON_T_KNOW_HOW_TO_CONVERT_PARAMETER_D, index);
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
                        @Cached AuditNode auditNode) {
            Object ob = converter(obj);
            auditNode.audit("ctypes.PyObj_FromPtr", "(O)", ob);
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
    abstract static class FailedCastCheckNode extends Node {
        abstract void execute(Object arg);

        @Specialization
        static void raiseError(Object arg,
                        @Cached PRaiseNode raiseNode,
                        @Cached IsTypeNode isTypeNode,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            Object clazz = isTypeNode.execute(arg) ? arg : getClassNode.execute(inliningTarget, arg);
            throw raiseNode.raise(TypeError, CAST_ARGUMENT_2_MUST_BE_A_POINTER_TYPE_NOT_S, getNameNode.execute(clazz));
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
    protected abstract static class CastFunctionNode extends Node {

        abstract Object execute(Object ptr, Object src, Object ctype);

        @Specialization
        static Object l(long ptr, @SuppressWarnings("unused") long src, Object ctype,
                        @Shared @Cached CastCheckPtrTypeNode castCheckPtrTypeNode,
                        @Shared @Cached CallNode callNode) {
            castCheckPtrTypeNode.execute(ctype);
            CDataObject result = (CDataObject) callNode.execute(ctype);
            result.b_ptr = PtrValue.nativePointer(ptr);
            return result;
        }

        @Specialization
        static Object nativeptr(PythonNativeVoidPtr ptr, @SuppressWarnings("unused") PythonNativeVoidPtr src, Object ctype,
                        @Shared @Cached CastCheckPtrTypeNode castCheckPtrTypeNode,
                        @Shared @Cached CallNode callNode) {
            castCheckPtrTypeNode.execute(ctype);
            CDataObject result = (CDataObject) callNode.execute(ctype);
            result.b_ptr = PtrValue.nativePointer(ptr.getPointerObject());
            return result;
        }

        @Specialization
        static Object cdata(CDataObject ptr, CDataObject src, Object ctype,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached CallNode callNode,
                        @Shared @Cached CastCheckPtrTypeNode castCheckPtrTypeNode) {
            castCheckPtrTypeNode.execute(ctype);
            CDataObject result = (CDataObject) callNode.execute(ctype);

            /*
             * The casted objects '_objects' member:
             *
             * It must certainly contain the source objects one. It must contain the source object
             * itself.
             */
            if (pyTypeCheck.isCDataObject(src)) {
                /*
                 * PyCData_GetContainer will initialize src.b_objects, we need this so it can be
                 * shared
                 */
                PyCData_GetContainer(src, factory);

                /* But we need a dictionary! */
                if (src.b_objects == null) {
                    src.b_objects = factory.createDict();
                }
                result.b_objects = src.b_objects;
                if (PGuards.isDict(result.b_objects)) {
                    // PyLong_FromVoidPtr((void *)src);
                    PDict dict = (PDict) result.b_objects;
                    Object index = factory.createNativeVoidPtr(src);
                    dict.setDictStorage(setItem.execute(null, dict.getDictStorage(), index, src));
                }
            }
            /* Should we assert that result is a pointer type? */
            // memcpy(result.b_ptr, &ptr, sizeof(void *));
            result.b_ptr = ptr.b_ptr.ref(0);
            return result;
        }

        @Specialization(guards = "!isCDataObject(src)")
        static Object ptr(PtrValue ptr, @SuppressWarnings("unused") Object src, Object ctype,
                        @Shared @Cached CastCheckPtrTypeNode castCheckPtrTypeNode,
                        @Shared @Cached CallNode callNode) {
            castCheckPtrTypeNode.execute(ctype);
            CDataObject result = (CDataObject) callNode.execute(ctype);

            /* Should we assert that result is a pointer type? */
            // memcpy(result.b_ptr, &ptr, sizeof(void *));
            result.b_ptr = ptr.ref(0);
            return result;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class CastFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new CastFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), NativeCAPISymbol.FUN_CAST.getName(), true);
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

        @Specialization(limit = "1")
        static Object memmove(CDataObject dest, PBytes src, int size,
                        @CachedLibrary("src") PythonBufferAccessLibrary bufferLib,
                        @Cached PtrNodes.WriteBytesNode writeBytesNode) {
            byte[] bytes = bufferLib.getInternalOrCopiedByteArray(src);
            writeBytesNode.execute(dest.b_ptr, bytes, 0, size);
            return dest;
        }

        @Specialization
        static Object memmove(CDataObject dest, CDataObject src, int size,
                        @Cached PtrNodes.MemcpyNode memcpyNode) {
            memcpyNode.execute(dest.b_ptr, src.b_ptr, size);
            return dest;
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
                adr = PyObjectHashNodeGen.getUncached().execute(null, f);
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
        static Object memset(CDataObject ptr, int value, int num,
                        @Cached PtrNodes.WriteBytesNode writeBytesNode) {
            byte[] fill = new byte[num];
            Arrays.fill(fill, (byte) value);
            writeBytesNode.execute(ptr.b_ptr, fill);
            return ptr;
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
                adr = PyObjectHashNodeGen.getUncached().execute(null, f);
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
    protected abstract static class StringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static Object string_at(TruffleString ptr, int size,
                        @Cached PythonObjectFactory factory,
                        @Cached AuditNode auditNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            auditNode.audit("ctypes.string_at", ptr, size);
            // TODO GR-38502: Review and add negative/out-of-bounds checks for the size argument
            assert TS_ENCODING == UTF_32;
            byte[] bytes = new byte[ptr.byteLength(TS_ENCODING)];
            copyToByteArrayNode.execute(ptr, 0, bytes, 0, bytes.length, TS_ENCODING);
            int len = 0;
            if (size == -1) {
                while (len < bytes.length && bytes[len] != 0) {
                    ++len;
                }
            } else {
                len = size;
            }
            return factory.createBytes(bytes, 0, len);
        }

        @Specialization
        static Object string_at(CDataObject ptr, int size,
                        @Cached PythonObjectFactory factory,
                        @Cached PtrNodes.ReadBytesNode read,
                        @Cached PtrNodes.StrLenNode strLenNode,
                        @Cached AuditNode auditNode) {
            if (size == -1) {
                size = strLenNode.execute(ptr.b_ptr);
            }
            auditNode.audit("ctypes.string_at", ptr, size);
            return factory.createBytes(read.execute(ptr.b_ptr, size));
        }

        @Specialization
        static Object string_at(PBytes ptr, int size,
                        @Cached GetInternalByteArrayNode getBytes,
                        @Cached PythonObjectFactory factory,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.string_at", ptr, size);
            byte[] bytes = getBytes.execute(ptr.getSequenceStorage());
            if (size != -1) {
                bytes = PythonUtils.arrayCopyOf(bytes, size);
            }
            return factory.createBytes(bytes);
        }

        @TruffleBoundary
        @Fallback
        static Object error(@SuppressWarnings("unused") Object ptr, @SuppressWarnings("unused") Object size) {
            throw PRaiseNode.getUncached().raise(NotImplementedError, toTruffleStringUncached("string_at doesn't support some storage types yet."));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class StringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new StringAtFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), NativeCAPISymbol.FUN_STRING_AT.getName(), true);
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
    protected abstract static class WStringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static TruffleString wstring_at(TruffleString ptr, int size,
                        @Cached AuditNode auditNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            int ssize = size;
            auditNode.audit("ctypes.wstring_at", ptr, ssize);
            if (ssize == -1) {
                ssize = codePointLengthNode.execute(ptr, TS_ENCODING); // wcslen(ptr);
            }
            return substringNode.execute(ptr, 0, ssize, TS_ENCODING, false); // PyUnicode_FromWideChar(ptr,
                                                                             // ssize);
        }

        @Specialization
        static TruffleString wstring_at(CDataObject ptr, int size,
                        @Cached AuditNode auditNode,
                        @Cached PtrNodes.ReadBytesNode read,
                        @Cached PtrNodes.WCsLenNode wCsLenNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            auditNode.audit("ctypes.wstring_at", ptr, size);
            // FIXME wchar_t on linux is 4 bytes
            int wcharSize = 2;
            Encoding encoding = UTF_16;
            if (size == -1) {
                size = wCsLenNode.execute(ptr.b_ptr, wcharSize);
            }
            byte[] bytes = read.execute(ptr.b_ptr, size * wcharSize);
            TruffleString s = fromByteArrayNode.execute(bytes, encoding);
            return switchEncodingNode.execute(s, TS_ENCODING);
        }

        @TruffleBoundary
        @Fallback
        static Object error(@SuppressWarnings("unused") Object ptr, @SuppressWarnings("unused") Object size) {
            throw PRaiseNode.getUncached().raise(NotImplementedError, toTruffleStringUncached("wstring_at doesn't support some storage types yet."));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    protected static final class WStringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new WStringAtFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), NativeCAPISymbol.FUN_WSTRING_AT.getName(), true);
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
