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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.DataPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.DataPtrPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Double;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPy;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPyField;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPyGlobal;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPyTracker;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPy_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Int;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Long;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Void;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType._HPyFieldPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType._HPyGlobalPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.HashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsIndex;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBinaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBoolFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderBuild;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderCancel;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderSet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesAsString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesFromStringAndSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesGetSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallBuiltinFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallTupleDict;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleIsValid;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCapsuleSet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCast;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCheckBuiltinType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyClose;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContains;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextVarGet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextVarNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextVarSet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictKeys;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictSetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDump;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrClear;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrExceptionMatches;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrOccurred;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrRaisePredefined;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrSetFromErrnoWithFilenameObjects;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrSetString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrWarnEx;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrWriteUnraisable;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFatalError;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFieldLoad;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFieldStore;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatAsDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatFromDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFromPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGlobalLoad;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGlobalStore;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyHasAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyImportModule;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyInplaceArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIs;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsCallable;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsNumber;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsSequence;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsTrue;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLeavePythonExecution;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListAppend;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsPrimitive;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyMaybeGetAttrS;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyModuleCreate;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNewException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyReenterPythonExecution;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyRichcompare;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySeqIterNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySliceUnpack;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTernaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerAdd;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerCleanup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerForgetAll;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTupleFromArray;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeCheck;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeCheckSlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeFromSpec;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeGenericNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeGetName;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeIsSubtype;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsCharsetString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsUTF8AndSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeDecodeCharset;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeDecodeCharsetAndSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeDecodeCharsetAndSizeAndErrors;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromEncodedObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromWchar;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeInternFromString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeReadChar;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeSubstring;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.ReturnType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
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

    public GraalHPyLLVMContext(GraalHPyContext context, @SuppressWarnings("unused") boolean useNativeFastPaths, boolean traceUpcalls) {
        // TODO(fa): we currently don't use native fast paths with the LLVM backend
        super(context, false, traceUpcalls);
        Object[] ctxMembers = createMembers(context.getContext(), tsLiteral(J_NAME));
        if (traceUpcalls) {
            this.counts = new int[HPyContextMember.VALUES.length];
            /*
             * For upcall tracing, each executable member is wrapped into an HPyExecuteWrapper which
             * does the tracing
             */
            for (int i = 0; i < ctxMembers.length; i++) {
                Object m = ctxMembers[i];
                if (m != null && !(m instanceof Number || m instanceof GraalHPyHandle)) {
                    ctxMembers[i] = new HPyExecuteWrapper(this.counts, i, m);
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
        TruffleFile capiFile = homePath.resolve("libhpy" + context.getSoAbi().toJavaStringUncached());
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
    protected void initNativeContext() throws Exception {
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
        assert useNativeCache();
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, cachePtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
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

    private static HPyContextSignature signature(HPyContextSignatureType returnType, HPyContextSignatureType... parameterTypes) {
        return new HPyContextSignature(returnType, parameterTypes);
    }

    /**
     * Describes the signature of an HPyContext function.
     */
    static final class HPyContextSignature {
        final HPyContextSignatureType returnType;
        final HPyContextSignatureType[] parameterTypes;

        HPyContextSignature(HPyContextSignatureType returnType, HPyContextSignatureType[] parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }
    }

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMember implements HPyUpcall {
        // {{start llvm ctx members}}
        NAME("name"),
        PRIVATE("_private"),
        CTX_VERSION("ctx_version"),

        // constants
        H_NONE("h_None"),
        H_TRUE("h_True"),
        H_FALSE("h_False"),
        H_NOTIMPLEMENTED("h_NotImplemented"),
        H_ELLIPSIS("h_Ellipsis"),

        // exception types
        H_BASEEXCEPTION("h_BaseException"),
        H_EXCEPTION("h_Exception"),
        H_STOPASYNCITERATION("h_StopAsyncIteration"),
        H_STOPITERATION("h_StopIteration"),
        H_GENERATOREXIT("h_GeneratorExit"),
        H_ARITHMETICERROR("h_ArithmeticError"),
        H_LOOKUPERROR("h_LookupError"),
        H_ASSERTIONERROR("h_AssertionError"),
        H_ATTRIBUTEERROR("h_AttributeError"),
        H_BUFFERERROR("h_BufferError"),
        H_EOFERROR("h_EOFError"),
        H_FLOATINGPOINTERROR("h_FloatingPointError"),
        H_OSERROR("h_OSError"),
        H_IMPORTERROR("h_ImportError"),
        H_MODULENOTFOUNDERROR("h_ModuleNotFoundError"),
        H_INDEXERROR("h_IndexError"),
        H_KEYERROR("h_KeyError"),
        H_KEYBOARDINTERRUPT("h_KeyboardInterrupt"),
        H_MEMORYERROR("h_MemoryError"),
        H_NAMEERROR("h_NameError"),
        H_OVERFLOWERROR("h_OverflowError"),
        H_RUNTIMEERROR("h_RuntimeError"),
        H_RECURSIONERROR("h_RecursionError"),
        H_NOTIMPLEMENTEDERROR("h_NotImplementedError"),
        H_SYNTAXERROR("h_SyntaxError"),
        H_INDENTATIONERROR("h_IndentationError"),
        H_TABERROR("h_TabError"),
        H_REFERENCEERROR("h_ReferenceError"),
        H_SYSTEMERROR("h_SystemError"),
        H_SYSTEMEXIT("h_SystemExit"),
        H_TYPEERROR("h_TypeError"),
        H_UNBOUNDLOCALERROR("h_UnboundLocalError"),
        H_UNICODEERROR("h_UnicodeError"),
        H_UNICODEENCODEERROR("h_UnicodeEncodeError"),
        H_UNICODEDECODEERROR("h_UnicodeDecodeError"),
        H_UNICODETRANSLATEERROR("h_UnicodeTranslateError"),
        H_VALUEERROR("h_ValueError"),
        H_ZERODIVISIONERROR("h_ZeroDivisionError"),
        H_BLOCKINGIOERROR("h_BlockingIOError"),
        H_BROKENPIPEERROR("h_BrokenPipeError"),
        H_CHILDPROCESSERROR("h_ChildProcessError"),
        H_CONNECTIONERROR("h_ConnectionError"),
        H_CONNECTIONABORTEDERROR("h_ConnectionAbortedError"),
        H_CONNECTIONREFUSEDERROR("h_ConnectionRefusedError"),
        H_CONNECTIONRESETERROR("h_ConnectionResetError"),
        H_FILEEXISTSERROR("h_FileExistsError"),
        H_FILENOTFOUNDERROR("h_FileNotFoundError"),
        H_INTERRUPTEDERROR("h_InterruptedError"),
        H_ISADIRECTORYERROR("h_IsADirectoryError"),
        H_NOTADIRECTORYERROR("h_NotADirectoryError"),
        H_PERMISSIONERROR("h_PermissionError"),
        H_PROCESSLOOKUPERROR("h_ProcessLookupError"),
        H_TIMEOUTERROR("h_TimeoutError"),
        H_WARNING("h_Warning"),
        H_USERWARNING("h_UserWarning"),
        H_DEPRECATIONWARNING("h_DeprecationWarning"),
        H_PENDINGDEPRECATIONWARNING("h_PendingDeprecationWarning"),
        H_SYNTAXWARNING("h_SyntaxWarning"),
        H_RUNTIMEWARNING("h_RuntimeWarning"),
        H_FUTUREWARNING("h_FutureWarning"),
        H_IMPORTWARNING("h_ImportWarning"),
        H_UNICODEWARNING("h_UnicodeWarning"),
        H_BYTESWARNING("h_BytesWarning"),
        H_RESOURCEWARNING("h_ResourceWarning"),

        // built-in types
        H_BASEOBJECTTYPE("h_BaseObjectType"),
        H_TYPETYPE("h_TypeType"),
        H_BOOLTYPE("h_BoolType"),
        H_LONGTYPE("h_LongType"),
        H_FLOATTYPE("h_FloatType"),
        H_COMPLEXTYPE("h_ComplexType"),
        H_UNICODETYPE("h_UnicodeType"),
        H_BYTESTYPE("h_BytesType"),
        H_TUPLETYPE("h_TupleType"),
        H_LISTTYPE("h_ListType"),
        H_MEMORYVIEWTYPE("h_MemoryViewType"),
        H_CAPSULETYPE("h_CapsuleType"),
        H_SLICETYPE("h_SliceType"),

        CTX_MODULE_CREATE("ctx_Module_Create"),
        CTX_DUP("ctx_Dup", signature(HPy, HPy)),
        CTX_AS_STRUCT("ctx_AsStruct", signature(DataPtr, HPy)),
        CTX_AS_STRUCT_LEGACY("ctx_AsStructLegacy"),
        CTX_CLOSE("ctx_Close", signature(Void, HPy)),
        CTX_BOOL_FROMLONG("ctx_Bool_FromLong"),
        CTX_LONG_FROMLONG("ctx_Long_FromLong"),
        CTX_LONG_FROMUNSIGNEDLONG("ctx_Long_FromUnsignedLong"),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong"),
        CTX_LONG_FROM_UNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong"),
        CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t"),
        CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t"),
        CTX_LONG_ASLONG("ctx_Long_AsLong", signature(Long, HPy)),
        CTX_LONG_ASLONGLONG("ctx_Long_AsLongLong"),
        CTX_LONG_ASUNSIGNEDLONG("ctx_Long_AsUnsignedLong"),
        CTX_LONG_ASUNSIGNEDLONGMASK("ctx_Long_AsUnsignedLongMask"),
        CTX_LONG_ASUNSIGNEDLONGLONG("ctx_Long_AsUnsignedLongLong"),
        CTX_LONG_ASUNSIGNEDLONGLONGMASK("ctx_Long_AsUnsignedLongLongMask"),
        CTX_LONG_ASSIZE_T("ctx_Long_AsSize_t"),
        CTX_LONG_ASSSIZE_T("ctx_Long_AsSsize_t"),
        CTX_LONG_ASVOIDPTR("ctx_Long_AsVoidPtr"),
        CTX_LONG_ASDOUBLE("ctx_Long_AsDouble", signature(Double, HPy)),
        CTX_NEW("ctx_New", signature(HPy, HPy, DataPtrPtr)),
        CTX_TYPE("ctx_Type"),
        CTX_TYPECHECK("ctx_TypeCheck", signature(Long, HPy, HPy)),
        CTX_TYPECHECK_G("ctx_TypeCheck_g", signature(Long, HPy, HPyGlobal)),
        CTX_SETTYPE("ctx_SetType"),
        CTX_TYPE_ISSUBTYPE("ctx_Type_IsSubtype"),
        CTX_TYPE_GETNAME("ctx_Type_GetName"),
        CTX_TYPE_CHECKSLOT("ctx_Type_CheckSlot"),
        CTX_IS("ctx_Is"),
        CTX_IS_G("ctx_Is_g", signature(Long, HPy, HPyGlobal)),
        CTX_TYPE_GENERIC_NEW("ctx_Type_GenericNew", signature(HPy, HPy)),
        CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble", signature(HPy, Double)),
        CTX_FLOAT_ASDOUBLE("ctx_Float_AsDouble", signature(Double, HPy)),

        // unary
        CTX_NEGATIVE("ctx_Negative"),
        CTX_POSITIVE("ctx_Positive"),
        CTX_ABSOLUTE("ctx_Absolute"),
        CTX_INVERT("ctx_Invert"),
        CTX_INDEX("ctx_Index"),
        CTX_LONG("ctx_Long"),
        CTX_FLOAT("ctx_Float"),

        // binary
        CTX_ADD("ctx_Add"),
        CTX_SUB("ctx_Subtract"),
        CTX_MULTIPLY("ctx_Multiply"),
        CTX_MATRIXMULTIPLY("ctx_MatrixMultiply"),
        CTX_FLOORDIVIDE("ctx_FloorDivide"),
        CTX_TRUEDIVIDE("ctx_TrueDivide"),
        CTX_REMAINDER("ctx_Remainder"),
        CTX_DIVMOD("ctx_Divmod"),
        CTX_LSHIFT("ctx_Lshift"),
        CTX_RSHIFT("ctx_Rshift"),
        CTX_AND("ctx_And"),
        CTX_XOR("ctx_Xor"),
        CTX_OR("ctx_Or"),
        CTX_INPLACEADD("ctx_InPlaceAdd"),
        CTX_INPLACESUBTRACT("ctx_InPlaceSubtract"),
        CTX_INPLACEMULTIPLY("ctx_InPlaceMultiply"),
        CTX_INPLACEMATRIXMULTIPLY("ctx_InPlaceMatrixMultiply"),
        CTX_INPLACEFLOORDIVIDE("ctx_InPlaceFloorDivide"),
        CTX_INPLACETRUEDIVIDE("ctx_InPlaceTrueDivide"),
        CTX_INPLACEREMAINDER("ctx_InPlaceRemainder"),
        // TODO(fa): support IDivMod
        // CTX_INPLACEDIVMOD("ctx_InPlaceDivmod"),
        CTX_INPLACELSHIFT("ctx_InPlaceLshift"),
        CTX_INPLACERSHIFT("ctx_InPlaceRshift"),
        CTX_INPLACEAND("ctx_InPlaceAnd"),
        CTX_INPLACEXOR("ctx_InPlaceXor"),
        CTX_INPLACEOR("ctx_InPlaceOr"),

        // ternary
        CTX_POWER("ctx_Power"),
        CTX_INPLACEPOWER("ctx_InPlacePower"),

        CTX_CALLABLE_CHECK("ctx_Callable_Check"),
        CTX_CALLTUPLEDICT("ctx_CallTupleDict"),
        CTX_ERR_NOMEMORY("ctx_Err_NoMemory"),
        CTX_ERR_SETSTRING("ctx_Err_SetString"),
        CTX_ERR_SETOBJECT("ctx_Err_SetObject"),
        CTX_ERR_SETFROMERRNOWITHFILENAME("ctx_Err_SetFromErrnoWithFilename"),
        CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS("ctx_Err_SetFromErrnoWithFilenameObjects"),
        CTX_ERR_OCCURRED("ctx_Err_Occurred"),
        CTX_ERR_EXCEPTIONMATCHES("ctx_Err_ExceptionMatches"),
        CTX_ERR_CLEAR("ctx_Err_Clear"),
        CTX_ERR_NEWEXCEPTION("ctx_Err_NewException"),
        CTX_ERR_NEWEXCEPTIONWITHDOC("ctx_Err_NewExceptionWithDoc"),
        CTX_ERR_WARNEX("ctx_Err_WarnEx"),
        CTX_ERR_WRITEUNRAISABLE("ctx_Err_WriteUnraisable"),
        CTX_FATALERROR("ctx_FatalError"),
        CTX_ISTRUE("ctx_IsTrue"),
        CTX_TYPE_FROM_SPEC("ctx_Type_FromSpec"),
        CTX_GETATTR("ctx_GetAttr"),
        CTX_GETATTR_S("ctx_GetAttr_s"),
        CTX_MAYBEGETATTR_S("ctx_MaybeGetAttr_s"),
        CTX_HASATTR("ctx_HasAttr"),
        CTX_HASATTR_S("ctx_HasAttr_s"),
        CTX_SETATTR("ctx_SetAttr"),
        CTX_SETATTR_S("ctx_SetAttr_s"),
        CTX_GETITEM("ctx_GetItem"),
        CTX_GETITEM_I("ctx_GetItem_i", signature(HPy, HPy, HPy_ssize_t)),
        CTX_GETITEM_S("ctx_GetItem_s"),
        CTX_SETITEM("ctx_SetItem"),
        CTX_SETITEM_I("ctx_SetItem_i"),
        CTX_SETITEM_S("ctx_SetItem_s"),
        CTX_CONTAINS("ctx_Contains"),
        CTX_BYTES_CHECK("ctx_Bytes_Check"),
        CTX_BYTES_SIZE("ctx_Bytes_Size"),
        CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE"),
        CTX_BYTES_ASSTRING("ctx_Bytes_AsString"),
        CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING"),
        CTX_BYTES_FROMSTRING("ctx_Bytes_FromString"),
        CTX_BYTES_FROMSTRINGANDSIZE("ctx_Bytes_FromStringAndSize"),
        CTX_UNICODE_FROMSTRING("ctx_Unicode_FromString"),
        CTX_UNICODE_CHECK("ctx_Unicode_Check"),
        CTX_UNICODE_ASUTF8STRING("ctx_Unicode_AsUTF8String"),
        CTX_UNICODE_ASASCIISTRING("ctx_Unicode_AsASCIIString"),
        CTX_UNICODE_ASLATIN1STRING("ctx_Unicode_AsLatin1String"),
        CTX_UNICODE_ASUTF8ANDSIZE("ctx_Unicode_AsUTF8AndSize"),
        CTX_UNICODE_FROMWIDECHAR("ctx_Unicode_FromWideChar"),
        CTX_UNICODE_DECODEASCII("ctx_Unicode_DecodeASCII"),
        CTX_UNICODE_DECODELATIN1("ctx_Unicode_DecodeLatin1"),
        CTX_UNICODE_FROMENCODEDOBJECT("ctx_Unicode_FromEncodedObject"),
        CTX_UNICODE_INTERNFROMSTRING("ctx_Unicode_InternFromString"),
        CTX_UNICODE_SUBSTRING("ctx_Unicode_Substring"),
        CTX_UNICODE_DECODEFSDEFAULT("ctx_Unicode_DecodeFSDefault"),
        CTX_UNICODE_DECODEFSDEFAULTANDSIZE("ctx_Unicode_DecodeFSDefaultAndSize"),
        CTX_UNICODE_ENCODEFSDEFAULT("ctx_Unicode_EncodeFSDefault"),
        CTX_UNICODE_READCHAR("ctx_Unicode_ReadChar"),
        CTX_LIST_NEW("ctx_List_New"),
        CTX_LIST_APPEND("ctx_List_Append"),
        CTX_DICT_CHECK("ctx_Dict_Check"),
        CTX_DICT_NEW("ctx_Dict_New"),
        CTX_DICT_KEYS("ctx_Dict_Keys"),
        CTX_DICT_SETITEM("ctx_Dict_SetItem"),
        CTX_DICT_GETITEM("ctx_Dict_GetItem"),
        CTX_FROMPYOBJECT("ctx_FromPyObject"),
        CTX_ASPYOBJECT("ctx_AsPyObject"),
        CTX_CALLREALFUNCTIONFROMTRAMPOLINE("ctx_CallRealFunctionFromTrampoline"),
        CTX_REPR("ctx_Repr"),
        CTX_STR("ctx_Str"),
        CTX_ASCII("ctx_ASCII"),
        CTX_BYTES("ctx_Bytes"),
        CTX_RICHCOMPARE("ctx_RichCompare"),
        CTX_RICHCOMPAREBOOL("ctx_RichCompareBool"),
        CTX_HASH("ctx_Hash"),
        CTX_NUMBER_CHECK("ctx_Number_Check", signature(Int, HPy)),
        CTX_LENGTH("ctx_Length", signature(HPy_ssize_t, HPy)),
        CTX_IMPORT_IMPORTMODULE("ctx_Import_ImportModule"),
        CTX_TUPLE_CHECK("ctx_Tuple_Check"),
        CTX_TUPLE_FROMARRAY("ctx_Tuple_FromArray"),
        CTX_TUPLE_BUILDER_NEW("ctx_TupleBuilder_New"),
        CTX_TUPLE_BUILDER_SET("ctx_TupleBuilder_Set"),
        CTX_TUPLE_BUILDER_BUILD("ctx_TupleBuilder_Build"),
        CTX_TUPLE_BUILDER_CANCEL("ctx_TupleBuilder_Cancel"),
        CTX_LIST_CHECK("ctx_List_Check", signature(Int, HPy)),
        CTX_LIST_BUILDER_NEW("ctx_ListBuilder_New"),
        CTX_LIST_BUILDER_SET("ctx_ListBuilder_Set"),
        CTX_LIST_BUILDER_BUILD("ctx_ListBuilder_Build"),
        CTX_LIST_BUILDER_CANCEL("ctx_ListBuilder_Cancel"),
        CTX_TRACKER_NEW("ctx_Tracker_New", signature(HPyTracker, HPy_ssize_t)),
        CTX_TRACKER_ADD("ctx_Tracker_Add", signature(Int, HPyTracker, HPy)),
        CTX_TRACKER_FORGET_ALL("ctx_Tracker_ForgetAll"),
        CTX_TRACKER_CLOSE("ctx_Tracker_Close", signature(Void, HPyTracker)),
        CTX_FIELD_STORE("ctx_Field_Store", signature(Void, HPy, _HPyFieldPtr, HPy)),
        CTX_FIELD_LOAD("ctx_Field_Load", signature(HPy, HPyField)),
        CTX_LEAVEPYTHONEXECUTION("ctx_LeavePythonExecution", signature(HPyThreadState)),
        CTX_REENTERPYTHONEXECUTION("ctx_ReenterPythonExecution", signature(Void, HPyThreadState)),
        CTX_GLOBAL_STORE("ctx_Global_Store", signature(Void, _HPyGlobalPtr, HPy)),
        CTX_GLOBAL_LOAD("ctx_Global_Load", signature(Void, HPyGlobal, HPy)),
        CTX_CONTEXTVAR_NEW("ctx_ContextVar_New"),
        CTX_CONTEXTVAR_GET("ctx_ContextVar_Get"),
        CTX_CONTEXTVAR_SET("ctx_ContextVar_Set"),
        CTX_CAPSULE_NEW("ctx_Capsule_New"),
        CTX_CAPSULE_GET("ctx_Capsule_Get"),
        CTX_CAPSULE_ISVALID("ctx_Capsule_IsValid"),
        CTX_CAPSULE_SET("ctx_Capsule_Set"),
        CTX_DUMP("ctx_Dump"),
        CTX_SEQUENCE_CHECK("ctx_Sequence_Check"),
        CTX_SLICE_UNPACK("ctx_Slice_Unpack"),
        CTX_SEQITER_NEW("ctx_SeqIter_New");
        // {{end llvm ctx members}}

        final String name;

        /**
         * If this signature is present (non-null), then a corresponding function in
         * {@link GraalHPyContext} needs to exist. E.g., for {@code ctx_Number_Check}, the function
         * {@link GraalHPyContext#ctxNumberCheck(long)} is used.
         */
        final HPyContextSignature signature;

        HPyContextMember(String name) {
            this(name, null);
        }

        HPyContextMember(String name, HPyContextSignature signature) {
            this.name = name;
            this.signature = signature;
        }

        @CompilationFinal(dimensions = 1) public static final HPyContextMember[] VALUES = values();
        public static final HashMap<String, HPyContextMember> MEMBERS = new HashMap<>();
        public static final Object KEYS;

        static {
            for (HPyContextMember member : VALUES) {
                MEMBERS.put(member.name, member);
            }

            String[] names = new String[VALUES.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = VALUES[i].name;
            }
            KEYS = new PythonAbstractObject.Keys(names);
        }

        @TruffleBoundary
        public static int getIndex(String key) {
            HPyContextMember member = HPyContextMember.MEMBERS.get(key);
            return member == null ? -1 : member.ordinal();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static void createIntConstant(Object[] members, HPyContextMember member, int value) {
        members[member.ordinal()] = value;
    }

    private static void createConstant(Object[] members, HPyContextMember member, Object value) {
        members[member.ordinal()] = GraalHPyHandle.create(value);
    }

    private void createSingletonConstant(Object[] members, HPyContextMember member, Object value, int handle) {
        GraalHPyHandle graalHandle = GraalHPyHandle.createSingleton(value, handle);
        members[member.ordinal()] = graalHandle;
    }

    private static void createTypeConstant(Object[] members, HPyContextMember member, Python3Core core, PythonBuiltinClassType value) {
        members[member.ordinal()] = GraalHPyHandle.create(core.lookupType(value));
    }

    private Object[] createMembers(PythonContext context, TruffleString name) {
        Object[] members = new Object[HPyContextMember.VALUES.length];

        members[HPyContextMember.NAME.ordinal()] = new CStringWrapper(name);
        createIntConstant(members, HPyContextMember.CTX_VERSION, 1);

        createSingletonConstant(members, HPyContextMember.H_NONE, PNone.NONE, SINGLETON_HANDLE_NONE);
        createConstant(members, HPyContextMember.H_TRUE, context.getTrue());
        createConstant(members, HPyContextMember.H_FALSE, context.getFalse());
        createSingletonConstant(members, HPyContextMember.H_NOTIMPLEMENTED, PNotImplemented.NOT_IMPLEMENTED, SINGLETON_HANDLE_NOT_IMPLEMENTED);
        createSingletonConstant(members, HPyContextMember.H_ELLIPSIS, PEllipsis.INSTANCE, SINGLETON_HANDLE_ELIPSIS);

        createTypeConstant(members, HPyContextMember.H_BASEEXCEPTION, context, PythonBuiltinClassType.PBaseException);
        createTypeConstant(members, HPyContextMember.H_EXCEPTION, context, PythonBuiltinClassType.Exception);
        createTypeConstant(members, HPyContextMember.H_STOPASYNCITERATION, context, PythonBuiltinClassType.StopAsyncIteration);
        createTypeConstant(members, HPyContextMember.H_STOPITERATION, context, PythonBuiltinClassType.StopIteration);
        createTypeConstant(members, HPyContextMember.H_GENERATOREXIT, context, PythonBuiltinClassType.GeneratorExit);
        createTypeConstant(members, HPyContextMember.H_ARITHMETICERROR, context, PythonBuiltinClassType.ArithmeticError);
        createTypeConstant(members, HPyContextMember.H_LOOKUPERROR, context, PythonBuiltinClassType.LookupError);
        createTypeConstant(members, HPyContextMember.H_ASSERTIONERROR, context, PythonBuiltinClassType.AssertionError);
        createTypeConstant(members, HPyContextMember.H_ATTRIBUTEERROR, context, PythonBuiltinClassType.AttributeError);
        createTypeConstant(members, HPyContextMember.H_BUFFERERROR, context, PythonBuiltinClassType.BufferError);
        createTypeConstant(members, HPyContextMember.H_EOFERROR, context, PythonBuiltinClassType.EOFError);
        createTypeConstant(members, HPyContextMember.H_FLOATINGPOINTERROR, context, PythonBuiltinClassType.FloatingPointError);
        createTypeConstant(members, HPyContextMember.H_OSERROR, context, PythonBuiltinClassType.OSError);
        createTypeConstant(members, HPyContextMember.H_IMPORTERROR, context, PythonBuiltinClassType.ImportError);
        createTypeConstant(members, HPyContextMember.H_MODULENOTFOUNDERROR, context, PythonBuiltinClassType.ModuleNotFoundError);
        createTypeConstant(members, HPyContextMember.H_INDEXERROR, context, PythonBuiltinClassType.IndexError);
        createTypeConstant(members, HPyContextMember.H_KEYERROR, context, PythonBuiltinClassType.KeyError);
        createTypeConstant(members, HPyContextMember.H_KEYBOARDINTERRUPT, context, PythonBuiltinClassType.KeyboardInterrupt);
        createTypeConstant(members, HPyContextMember.H_MEMORYERROR, context, PythonBuiltinClassType.MemoryError);
        createTypeConstant(members, HPyContextMember.H_NAMEERROR, context, PythonBuiltinClassType.NameError);
        createTypeConstant(members, HPyContextMember.H_OVERFLOWERROR, context, PythonBuiltinClassType.OverflowError);
        createTypeConstant(members, HPyContextMember.H_RUNTIMEERROR, context, PythonBuiltinClassType.RuntimeError);
        createTypeConstant(members, HPyContextMember.H_RECURSIONERROR, context, PythonBuiltinClassType.RecursionError);
        createTypeConstant(members, HPyContextMember.H_NOTIMPLEMENTEDERROR, context, PythonBuiltinClassType.NotImplementedError);
        createTypeConstant(members, HPyContextMember.H_SYNTAXERROR, context, PythonBuiltinClassType.SyntaxError);
        createTypeConstant(members, HPyContextMember.H_INDENTATIONERROR, context, PythonBuiltinClassType.IndentationError);
        createTypeConstant(members, HPyContextMember.H_TABERROR, context, PythonBuiltinClassType.TabError);
        createTypeConstant(members, HPyContextMember.H_REFERENCEERROR, context, PythonBuiltinClassType.ReferenceError);
        createTypeConstant(members, HPyContextMember.H_SYSTEMERROR, context, SystemError);
        createTypeConstant(members, HPyContextMember.H_SYSTEMEXIT, context, PythonBuiltinClassType.SystemExit);
        createTypeConstant(members, HPyContextMember.H_TYPEERROR, context, PythonBuiltinClassType.TypeError);
        createTypeConstant(members, HPyContextMember.H_UNBOUNDLOCALERROR, context, PythonBuiltinClassType.UnboundLocalError);
        createTypeConstant(members, HPyContextMember.H_UNICODEERROR, context, PythonBuiltinClassType.UnicodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODEENCODEERROR, context, PythonBuiltinClassType.UnicodeEncodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODEDECODEERROR, context, PythonBuiltinClassType.UnicodeDecodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODETRANSLATEERROR, context, PythonBuiltinClassType.UnicodeTranslateError);
        createTypeConstant(members, HPyContextMember.H_VALUEERROR, context, PythonBuiltinClassType.ValueError);
        createTypeConstant(members, HPyContextMember.H_ZERODIVISIONERROR, context, PythonBuiltinClassType.ZeroDivisionError);
        createTypeConstant(members, HPyContextMember.H_BLOCKINGIOERROR, context, PythonBuiltinClassType.BlockingIOError);
        createTypeConstant(members, HPyContextMember.H_BROKENPIPEERROR, context, PythonBuiltinClassType.BrokenPipeError);
        createTypeConstant(members, HPyContextMember.H_CHILDPROCESSERROR, context, PythonBuiltinClassType.ChildProcessError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONERROR, context, PythonBuiltinClassType.ConnectionError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONABORTEDERROR, context, PythonBuiltinClassType.ConnectionAbortedError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONREFUSEDERROR, context, PythonBuiltinClassType.ConnectionRefusedError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONRESETERROR, context, PythonBuiltinClassType.ConnectionResetError);
        createTypeConstant(members, HPyContextMember.H_FILEEXISTSERROR, context, PythonBuiltinClassType.FileExistsError);
        createTypeConstant(members, HPyContextMember.H_FILENOTFOUNDERROR, context, PythonBuiltinClassType.FileNotFoundError);
        createTypeConstant(members, HPyContextMember.H_INTERRUPTEDERROR, context, PythonBuiltinClassType.InterruptedError);
        createTypeConstant(members, HPyContextMember.H_ISADIRECTORYERROR, context, PythonBuiltinClassType.IsADirectoryError);
        createTypeConstant(members, HPyContextMember.H_NOTADIRECTORYERROR, context, PythonBuiltinClassType.NotADirectoryError);
        createTypeConstant(members, HPyContextMember.H_PERMISSIONERROR, context, PythonBuiltinClassType.PermissionError);
        createTypeConstant(members, HPyContextMember.H_PROCESSLOOKUPERROR, context, PythonBuiltinClassType.ProcessLookupError);
        createTypeConstant(members, HPyContextMember.H_TIMEOUTERROR, context, PythonBuiltinClassType.TimeoutError);
        createTypeConstant(members, HPyContextMember.H_WARNING, context, PythonBuiltinClassType.Warning);
        createTypeConstant(members, HPyContextMember.H_USERWARNING, context, PythonBuiltinClassType.UserWarning);
        createTypeConstant(members, HPyContextMember.H_DEPRECATIONWARNING, context, PythonBuiltinClassType.DeprecationWarning);
        createTypeConstant(members, HPyContextMember.H_PENDINGDEPRECATIONWARNING, context, PythonBuiltinClassType.PendingDeprecationWarning);
        createTypeConstant(members, HPyContextMember.H_SYNTAXWARNING, context, PythonBuiltinClassType.SyntaxWarning);
        createTypeConstant(members, HPyContextMember.H_RUNTIMEWARNING, context, PythonBuiltinClassType.RuntimeWarning);
        createTypeConstant(members, HPyContextMember.H_FUTUREWARNING, context, PythonBuiltinClassType.FutureWarning);
        createTypeConstant(members, HPyContextMember.H_IMPORTWARNING, context, PythonBuiltinClassType.ImportWarning);
        createTypeConstant(members, HPyContextMember.H_UNICODEWARNING, context, PythonBuiltinClassType.UnicodeWarning);
        createTypeConstant(members, HPyContextMember.H_BYTESWARNING, context, PythonBuiltinClassType.BytesWarning);
        createTypeConstant(members, HPyContextMember.H_RESOURCEWARNING, context, PythonBuiltinClassType.ResourceWarning);

        createTypeConstant(members, HPyContextMember.H_BASEOBJECTTYPE, context, PythonBuiltinClassType.PythonObject);
        createTypeConstant(members, HPyContextMember.H_TYPETYPE, context, PythonBuiltinClassType.PythonClass);
        createTypeConstant(members, HPyContextMember.H_BOOLTYPE, context, PythonBuiltinClassType.Boolean);
        createTypeConstant(members, HPyContextMember.H_LONGTYPE, context, PythonBuiltinClassType.PInt);
        createTypeConstant(members, HPyContextMember.H_FLOATTYPE, context, PythonBuiltinClassType.PFloat);
        createTypeConstant(members, HPyContextMember.H_UNICODETYPE, context, PythonBuiltinClassType.PString);
        createTypeConstant(members, HPyContextMember.H_TUPLETYPE, context, PythonBuiltinClassType.PTuple);
        createTypeConstant(members, HPyContextMember.H_LISTTYPE, context, PythonBuiltinClassType.PList);

        createTypeConstant(members, HPyContextMember.H_COMPLEXTYPE, context, PythonBuiltinClassType.PComplex);
        createTypeConstant(members, HPyContextMember.H_BYTESTYPE, context, PythonBuiltinClassType.PBytes);
        createTypeConstant(members, HPyContextMember.H_MEMORYVIEWTYPE, context, PythonBuiltinClassType.PMemoryView);
        createTypeConstant(members, HPyContextMember.H_CAPSULETYPE, context, PythonBuiltinClassType.Capsule);
        createTypeConstant(members, HPyContextMember.H_SLICETYPE, context, PythonBuiltinClassType.PSlice);

        members[HPyContextMember.CTX_ASPYOBJECT.ordinal()] = new GraalHPyAsPyObject();
        members[HPyContextMember.CTX_DUP.ordinal()] = new GraalHPyDup();
        members[HPyContextMember.CTX_CLOSE.ordinal()] = new GraalHPyClose();
        members[HPyContextMember.CTX_MODULE_CREATE.ordinal()] = new GraalHPyModuleCreate();
        members[HPyContextMember.CTX_BOOL_FROMLONG.ordinal()] = new GraalHPyBoolFromLong();
        GraalHPyLongFromLong fromSignedLong = new GraalHPyLongFromLong();
        GraalHPyLongFromLong fromUnsignedLong = new GraalHPyLongFromLong(false);
        members[HPyContextMember.CTX_LONG_FROMLONG.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROMUNSIGNEDLONG.ordinal()] = fromUnsignedLong;
        members[HPyContextMember.CTX_LONG_FROMLONGLONG.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROM_UNSIGNEDLONGLONG.ordinal()] = fromUnsignedLong;
        members[HPyContextMember.CTX_LONG_FROMSSIZE_T.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROMSIZE_T.ordinal()] = fromUnsignedLong;
        GraalHPyLongAsPrimitive asSignedLong = new GraalHPyLongAsPrimitive(1, java.lang.Long.BYTES, true);
        GraalHPyLongAsPrimitive asUnsignedLong = new GraalHPyLongAsPrimitive(0, java.lang.Long.BYTES, true, true);
        GraalHPyLongAsPrimitive asUnsignedLongMask = new GraalHPyLongAsPrimitive(0, java.lang.Long.BYTES, false);
        members[HPyContextMember.CTX_LONG_ASLONG.ordinal()] = asSignedLong;
        members[HPyContextMember.CTX_LONG_ASLONGLONG.ordinal()] = asSignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONG.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONG.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGMASK.ordinal()] = asUnsignedLongMask;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONGMASK.ordinal()] = asUnsignedLongMask;
        members[HPyContextMember.CTX_LONG_ASSIZE_T.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASSSIZE_T.ordinal()] = new GraalHPyLongAsPrimitive(1, java.lang.Long.BYTES, true, true);
        members[HPyContextMember.CTX_LONG_ASVOIDPTR.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASDOUBLE.ordinal()] = new GraalHPyLongAsDouble();
        members[HPyContextMember.CTX_NEW.ordinal()] = new GraalHPyNew();
        members[HPyContextMember.CTX_TYPE.ordinal()] = new GraalHPyType();
        members[HPyContextMember.CTX_TYPECHECK.ordinal()] = new GraalHPyTypeCheck();
        members[HPyContextMember.CTX_TYPECHECK_G.ordinal()] = new GraalHPyTypeCheck(true);
        members[HPyContextMember.CTX_IS.ordinal()] = new GraalHPyIs();
        members[HPyContextMember.CTX_IS_G.ordinal()] = new GraalHPyIs(true);
        members[HPyContextMember.CTX_TYPE_GENERIC_NEW.ordinal()] = new GraalHPyTypeGenericNew();
        GraalHPyCast graalHPyCast = new GraalHPyCast();
        members[HPyContextMember.CTX_AS_STRUCT.ordinal()] = graalHPyCast;
        members[HPyContextMember.CTX_AS_STRUCT_LEGACY.ordinal()] = graalHPyCast;

        // unary
        members[HPyContextMember.CTX_NEGATIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Neg);
        members[HPyContextMember.CTX_POSITIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Pos);
        members[HPyContextMember.CTX_ABSOLUTE.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_ABS, 1);
        members[HPyContextMember.CTX_INVERT.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Invert);
        members[HPyContextMember.CTX_INDEX.ordinal()] = new GraalHPyAsIndex();
        members[HPyContextMember.CTX_LONG.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_INT, 1);
        members[HPyContextMember.CTX_FLOAT.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_FLOAT, 1);

        // binary
        members[HPyContextMember.CTX_ADD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Add);
        members[HPyContextMember.CTX_SUB.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Sub);
        members[HPyContextMember.CTX_MULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mul);
        members[HPyContextMember.CTX_MATRIXMULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.MatMul);
        members[HPyContextMember.CTX_FLOORDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.FloorDiv);
        members[HPyContextMember.CTX_TRUEDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.TrueDiv);
        members[HPyContextMember.CTX_REMAINDER.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mod);
        members[HPyContextMember.CTX_DIVMOD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.DivMod);
        members[HPyContextMember.CTX_LSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.LShift);
        members[HPyContextMember.CTX_RSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.RShift);
        members[HPyContextMember.CTX_AND.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.And);
        members[HPyContextMember.CTX_XOR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Xor);
        members[HPyContextMember.CTX_OR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Or);
        members[HPyContextMember.CTX_INPLACEADD.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAdd);
        members[HPyContextMember.CTX_INPLACESUBTRACT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ISub);
        members[HPyContextMember.CTX_INPLACEMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMul);
        members[HPyContextMember.CTX_INPLACEMATRIXMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMatMul);
        members[HPyContextMember.CTX_INPLACEFLOORDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IFloorDiv);
        members[HPyContextMember.CTX_INPLACETRUEDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ITrueDiv);
        members[HPyContextMember.CTX_INPLACEREMAINDER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMod);
        members[HPyContextMember.CTX_INPLACELSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ILShift);
        members[HPyContextMember.CTX_INPLACERSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IRShift);
        members[HPyContextMember.CTX_INPLACEAND.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAnd);
        members[HPyContextMember.CTX_INPLACEXOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IXor);
        members[HPyContextMember.CTX_INPLACEOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IOr);

        // ternary
        members[HPyContextMember.CTX_POWER.ordinal()] = new GraalHPyTernaryArithmetic(TernaryArithmetic.Pow);
        members[HPyContextMember.CTX_INPLACEPOWER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IPow);

        members[HPyContextMember.CTX_CALLABLE_CHECK.ordinal()] = new GraalHPyIsCallable();
        members[HPyContextMember.CTX_CALLTUPLEDICT.ordinal()] = new GraalHPyCallTupleDict();

        members[HPyContextMember.CTX_DICT_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PDict);
        members[HPyContextMember.CTX_DICT_NEW.ordinal()] = new GraalHPyDictNew();
        members[HPyContextMember.CTX_DICT_SETITEM.ordinal()] = new GraalHPyDictSetItem();
        members[HPyContextMember.CTX_DICT_GETITEM.ordinal()] = new GraalHPyDictGetItem();
        members[HPyContextMember.CTX_LIST_NEW.ordinal()] = new GraalHPyListNew();
        members[HPyContextMember.CTX_LIST_APPEND.ordinal()] = new GraalHPyListAppend();
        members[HPyContextMember.CTX_FLOAT_FROMDOUBLE.ordinal()] = new GraalHPyFloatFromDouble();
        members[HPyContextMember.CTX_FLOAT_ASDOUBLE.ordinal()] = new GraalHPyFloatAsDouble();
        members[HPyContextMember.CTX_BYTES_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PBytes);
        members[HPyContextMember.CTX_BYTES_GET_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMember.CTX_BYTES_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMember.CTX_BYTES_AS_STRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMember.CTX_BYTES_ASSTRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMember.CTX_BYTES_FROMSTRING.ordinal()] = new GraalHPyBytesFromStringAndSize(false);
        members[HPyContextMember.CTX_BYTES_FROMSTRINGANDSIZE.ordinal()] = new GraalHPyBytesFromStringAndSize(true);

        members[HPyContextMember.CTX_ERR_NOMEMORY.ordinal()] = new GraalHPyErrRaisePredefined(PythonBuiltinClassType.MemoryError);
        members[HPyContextMember.CTX_ERR_SETSTRING.ordinal()] = new GraalHPyErrSetString(true);
        members[HPyContextMember.CTX_ERR_SETOBJECT.ordinal()] = new GraalHPyErrSetString(false);
        members[HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAME.ordinal()] = new GraalHPyErrSetFromErrnoWithFilenameObjects(false);
        members[HPyContextMember.CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS.ordinal()] = new GraalHPyErrSetFromErrnoWithFilenameObjects(true);
        members[HPyContextMember.CTX_ERR_OCCURRED.ordinal()] = new GraalHPyErrOccurred();
        members[HPyContextMember.CTX_ERR_EXCEPTIONMATCHES.ordinal()] = new GraalHPyErrExceptionMatches();
        members[HPyContextMember.CTX_ERR_CLEAR.ordinal()] = new GraalHPyErrClear();
        members[HPyContextMember.CTX_ERR_NEWEXCEPTION.ordinal()] = new GraalHPyNewException(false);
        members[HPyContextMember.CTX_ERR_NEWEXCEPTIONWITHDOC.ordinal()] = new GraalHPyNewException(true);
        members[HPyContextMember.CTX_ERR_WARNEX.ordinal()] = new GraalHPyErrWarnEx();
        members[HPyContextMember.CTX_ERR_WRITEUNRAISABLE.ordinal()] = new GraalHPyErrWriteUnraisable();
        members[HPyContextMember.CTX_FATALERROR.ordinal()] = new GraalHPyFatalError();
        members[HPyContextMember.CTX_FROMPYOBJECT.ordinal()] = new GraalHPyFromPyObject();
        members[HPyContextMember.CTX_UNICODE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PString);
        members[HPyContextMember.CTX_ISTRUE.ordinal()] = new GraalHPyIsTrue();
        members[HPyContextMember.CTX_UNICODE_ASUTF8STRING.ordinal()] = GraalHPyUnicodeAsCharsetString.asUTF8();
        members[HPyContextMember.CTX_UNICODE_ASASCIISTRING.ordinal()] = GraalHPyUnicodeAsCharsetString.asASCII();
        members[HPyContextMember.CTX_UNICODE_ASLATIN1STRING.ordinal()] = GraalHPyUnicodeAsCharsetString.asLatin1();
        members[HPyContextMember.CTX_UNICODE_ASUTF8ANDSIZE.ordinal()] = new GraalHPyUnicodeAsUTF8AndSize();
        members[HPyContextMember.CTX_UNICODE_FROMSTRING.ordinal()] = new GraalHPyUnicodeFromString();
        members[HPyContextMember.CTX_UNICODE_FROMWIDECHAR.ordinal()] = new GraalHPyUnicodeFromWchar();
        members[HPyContextMember.CTX_UNICODE_DECODEASCII.ordinal()] = GraalHPyUnicodeDecodeCharsetAndSizeAndErrors.decodeASCII();
        members[HPyContextMember.CTX_UNICODE_DECODELATIN1.ordinal()] = GraalHPyUnicodeDecodeCharsetAndSizeAndErrors.decodeLatin1();
        members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT.ordinal()] = GraalHPyUnicodeDecodeCharset.decodeFSDefault();
        members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULTANDSIZE.ordinal()] = GraalHPyUnicodeDecodeCharsetAndSize.decodeFSDefault();
        members[HPyContextMember.CTX_UNICODE_ENCODEFSDEFAULT.ordinal()] = GraalHPyUnicodeAsCharsetString.asFSDefault();
        members[HPyContextMember.CTX_UNICODE_READCHAR.ordinal()] = new GraalHPyUnicodeReadChar();
        members[HPyContextMember.CTX_TYPE_FROM_SPEC.ordinal()] = new GraalHPyTypeFromSpec();
        members[HPyContextMember.CTX_GETATTR.ordinal()] = new GraalHPyGetAttr(OBJECT);
        members[HPyContextMember.CTX_GETATTR_S.ordinal()] = new GraalHPyGetAttr(CHAR_PTR);
        members[HPyContextMember.CTX_MAYBEGETATTR_S.ordinal()] = new GraalHPyMaybeGetAttrS();
        members[HPyContextMember.CTX_HASATTR.ordinal()] = new GraalHPyHasAttr(OBJECT);
        members[HPyContextMember.CTX_HASATTR_S.ordinal()] = new GraalHPyHasAttr(CHAR_PTR);
        members[HPyContextMember.CTX_SETATTR.ordinal()] = new GraalHPySetAttr(OBJECT);
        members[HPyContextMember.CTX_SETATTR_S.ordinal()] = new GraalHPySetAttr(CHAR_PTR);
        members[HPyContextMember.CTX_GETITEM.ordinal()] = new GraalHPyGetItem(OBJECT);
        members[HPyContextMember.CTX_GETITEM_S.ordinal()] = new GraalHPyGetItem(CHAR_PTR);
        members[HPyContextMember.CTX_GETITEM_I.ordinal()] = new GraalHPyGetItem(INT32);
        members[HPyContextMember.CTX_SETITEM.ordinal()] = new GraalHPySetItem(OBJECT);
        members[HPyContextMember.CTX_SETITEM_S.ordinal()] = new GraalHPySetItem(CHAR_PTR);
        members[HPyContextMember.CTX_SETITEM_I.ordinal()] = new GraalHPySetItem(INT32);
        members[HPyContextMember.CTX_CONTAINS.ordinal()] = new GraalHPyContains();
        members[HPyContextMember.CTX_REPR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_REPR, 1);
        members[HPyContextMember.CTX_STR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_STR, 1);
        members[HPyContextMember.CTX_ASCII.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_ASCII, 1);
        members[HPyContextMember.CTX_BYTES.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_BYTES, 1);
        members[HPyContextMember.CTX_RICHCOMPARE.ordinal()] = new GraalHPyRichcompare(false);
        members[HPyContextMember.CTX_RICHCOMPAREBOOL.ordinal()] = new GraalHPyRichcompare(true);
        members[HPyContextMember.CTX_HASH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_HASH, 1, ReturnType.INT);
        members[HPyContextMember.CTX_NUMBER_CHECK.ordinal()] = new GraalHPyIsNumber();
        members[HPyContextMember.CTX_LENGTH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_LEN, 1, ReturnType.INT);
        members[HPyContextMember.CTX_IMPORT_IMPORTMODULE.ordinal()] = new GraalHPyImportModule();
        members[HPyContextMember.CTX_TUPLE_FROMARRAY.ordinal()] = new GraalHPyTupleFromArray();
        members[HPyContextMember.CTX_TUPLE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PTuple);

        GraalHPyBuilderNew graalHPyBuilderNew = new GraalHPyBuilderNew();
        GraalHPyBuilderSet graalHPyBuilderSet = new GraalHPyBuilderSet();
        GraalHPyBuilderCancel graalHPyBuilderCancel = new GraalHPyBuilderCancel();
        members[HPyContextMember.CTX_TUPLE_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMember.CTX_TUPLE_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMember.CTX_TUPLE_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PythonBuiltinClassType.PTuple);
        members[HPyContextMember.CTX_TUPLE_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMember.CTX_LIST_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PList);
        members[HPyContextMember.CTX_LIST_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMember.CTX_LIST_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMember.CTX_LIST_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PythonBuiltinClassType.PList);
        members[HPyContextMember.CTX_LIST_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMember.CTX_TRACKER_NEW.ordinal()] = new GraalHPyTrackerNew();
        members[HPyContextMember.CTX_TRACKER_ADD.ordinal()] = new GraalHPyTrackerAdd();
        members[HPyContextMember.CTX_TRACKER_FORGET_ALL.ordinal()] = new GraalHPyTrackerForgetAll();
        members[HPyContextMember.CTX_TRACKER_CLOSE.ordinal()] = new GraalHPyTrackerCleanup();

        members[HPyContextMember.CTX_FIELD_STORE.ordinal()] = new GraalHPyFieldStore();
        members[HPyContextMember.CTX_FIELD_LOAD.ordinal()] = new GraalHPyFieldLoad();
        members[HPyContextMember.CTX_LEAVEPYTHONEXECUTION.ordinal()] = new GraalHPyLeavePythonExecution();
        members[HPyContextMember.CTX_REENTERPYTHONEXECUTION.ordinal()] = new GraalHPyReenterPythonExecution();
        members[HPyContextMember.CTX_GLOBAL_STORE.ordinal()] = new GraalHPyGlobalStore();
        members[HPyContextMember.CTX_GLOBAL_LOAD.ordinal()] = new GraalHPyGlobalLoad();
        members[HPyContextMember.CTX_DUMP.ordinal()] = new GraalHPyDump();

        members[HPyContextMember.CTX_TYPE_ISSUBTYPE.ordinal()] = new GraalHPyTypeIsSubtype();
        members[HPyContextMember.CTX_TYPE_GETNAME.ordinal()] = new GraalHPyTypeGetName();
        members[HPyContextMember.CTX_SETTYPE.ordinal()] = new GraalHPySetType();
        members[HPyContextMember.CTX_TYPE_CHECKSLOT.ordinal()] = new GraalHPyTypeCheckSlot();

        members[HPyContextMember.CTX_DICT_KEYS.ordinal()] = new GraalHPyDictKeys();

        members[HPyContextMember.CTX_UNICODE_FROMENCODEDOBJECT.ordinal()] = new GraalHPyUnicodeFromEncodedObject();
        members[HPyContextMember.CTX_UNICODE_INTERNFROMSTRING.ordinal()] = new GraalHPyUnicodeInternFromString();
        members[HPyContextMember.CTX_UNICODE_SUBSTRING.ordinal()] = new GraalHPyUnicodeSubstring();

        members[HPyContextMember.CTX_CONTEXTVAR_NEW.ordinal()] = new GraalHPyContextVarNew();
        members[HPyContextMember.CTX_CONTEXTVAR_GET.ordinal()] = new GraalHPyContextVarGet();
        members[HPyContextMember.CTX_CONTEXTVAR_SET.ordinal()] = new GraalHPyContextVarSet();
        members[HPyContextMember.CTX_CAPSULE_NEW.ordinal()] = new GraalHPyCapsuleNew();
        members[HPyContextMember.CTX_CAPSULE_GET.ordinal()] = new GraalHPyCapsuleGet();
        members[HPyContextMember.CTX_CAPSULE_ISVALID.ordinal()] = new GraalHPyCapsuleIsValid();
        members[HPyContextMember.CTX_CAPSULE_SET.ordinal()] = new GraalHPyCapsuleSet();

        members[HPyContextMember.CTX_SEQUENCE_CHECK.ordinal()] = new GraalHPyIsSequence();
        members[HPyContextMember.CTX_SLICE_UNPACK.ordinal()] = new GraalHPySliceUnpack();

        members[HPyContextMember.CTX_SEQITER_NEW.ordinal()] = new GraalHPySeqIterNew();

        return members;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HPyContextMember.KEYS;
    }

    @ExportMessage
    @ImportStatic({HPyContextMember.class, PythonUtils.class})
    static class IsMemberReadable {
        @Specialization(guards = "cachedKey.equals(key)", limit = "1")
        static boolean isMemberReadableCached(@SuppressWarnings("unused") GraalHPyLLVMContext context, @SuppressWarnings("unused") String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
            return cachedIdx != -1;
        }

        @Specialization(replaces = "isMemberReadableCached")
        static boolean isMemberReadable(@SuppressWarnings("unused") GraalHPyLLVMContext context, String key) {
            return HPyContextMember.getIndex(key) != -1;
        }
    }

    @ExportMessage
    Object readMember(String key,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode) {
        return readMemberNode.execute(this, key);
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
    @ImportStatic({HPyContextMember.class, PythonUtils.class})
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(GraalHPyLLVMContext backend, String key);

        @Specialization(guards = "cachedKey == key", limit = "1")
        static Object doMemberCached(GraalHPyLLVMContext backend, String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
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
            return doMemberCached(backend, key, key, HPyContextMember.getIndex(key));
        }
    }

    @ExportMessage
    boolean isMemberInvocable(String key,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib) {
        Object member = readMemberNode.execute(this, key);
        return member != null && memberInvokeLib.isExecutable(member);
    }

    @ExportMessage
    Object invokeMember(String key, Object[] args,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        Object member = readMemberNode.execute(this, key);
        assert member != null;
        /*
         * Optimization: the first argument *MUST* always be the context. If not, we can just set
         * 'this'.
         */
        args[0] = this;
        return memberInvokeLib.execute(member, args);
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    public static final class HPyExecuteWrapper implements TruffleObject {

        private final int[] counts;
        private final int index;
        final Object delegate;

        public HPyExecuteWrapper(int[] counts, int index, Object delegate) {
            this.counts = counts;
            this.index = index;
            this.delegate = delegate;
        }

        @ExportMessage
        boolean isExecutable(
                        @CachedLibrary("this.delegate") InteropLibrary lib) {
            return lib.isExecutable(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @CachedLibrary("this.delegate") InteropLibrary lib) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            counts[index]++;
            return lib.execute(delegate, arguments);
        }
    }
}