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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ASCII_UPPERCASE;
import static com.oracle.graal.python.nodes.StringLiterals.T_ISO_8859_1;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyArithmeticNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallBuiltinFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrRaisePredefined;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsPrimitive;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyContextFunctionWithBuiltinType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyContextFunctionWithFlag;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyContextFunctionWithMode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.HPyContextFunctionWithObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.ReturnType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsIndexNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBoolFromLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderBuildNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderCancelNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesAsStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesFromStringAndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesGetSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCallTupleDictNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleIsValidNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCastNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCheckBuiltinTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContainsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictKeysNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictSetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDumpNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDupNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrClearNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrExceptionMatchesNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrOccurredNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrWarnExNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrWriteUnraisableNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFatalErrorNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFieldLoadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFieldStoreNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloatAsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloatFromDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFromPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGlobalLoadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGlobalStoreNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyHasAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyImportModuleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsCallableNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsNumberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsSequenceNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsTrueNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLeavePythonExecutionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListAppendNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyMaybeGetAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyModuleCreateNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNewExceptionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyReenterPythonExecutionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyRichcompareNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySeqIterNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySliceUnpackNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerAddNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerCleanupNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerForgetAllNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTupleFromArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeCheckSlotNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeFromSpecNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGenericNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGetNameNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeIsSubtypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsCharsetStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsUTF8AndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeDecodeCharsetAndSizeAndErrorsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromEncodedObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromWcharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeInternFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeReadCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeSubstringNodeGen;
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
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
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
import com.oracle.truffle.api.dsl.NeverDefault;
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
                if (m instanceof HPyExecuteWrapperGeneric<?> executeWrapper) {
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
        CTX_DUP("ctx_Dup"),
        CTX_AS_STRUCT("ctx_AsStruct"),
        CTX_AS_STRUCT_LEGACY("ctx_AsStructLegacy"),
        CTX_CLOSE("ctx_Close"),
        CTX_BOOL_FROMLONG("ctx_Bool_FromLong"),
        CTX_LONG_FROMLONG("ctx_Long_FromLong"),
        CTX_LONG_FROMUNSIGNEDLONG("ctx_Long_FromUnsignedLong"),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong"),
        CTX_LONG_FROM_UNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong"),
        CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t"),
        CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t"),
        CTX_LONG_ASLONG("ctx_Long_AsLong"),
        CTX_LONG_ASLONGLONG("ctx_Long_AsLongLong"),
        CTX_LONG_ASUNSIGNEDLONG("ctx_Long_AsUnsignedLong"),
        CTX_LONG_ASUNSIGNEDLONGMASK("ctx_Long_AsUnsignedLongMask"),
        CTX_LONG_ASUNSIGNEDLONGLONG("ctx_Long_AsUnsignedLongLong"),
        CTX_LONG_ASUNSIGNEDLONGLONGMASK("ctx_Long_AsUnsignedLongLongMask"),
        CTX_LONG_ASSIZE_T("ctx_Long_AsSize_t"),
        CTX_LONG_ASSSIZE_T("ctx_Long_AsSsize_t"),
        CTX_LONG_ASVOIDPTR("ctx_Long_AsVoidPtr"),
        CTX_LONG_ASDOUBLE("ctx_Long_AsDouble"),
        CTX_NEW("ctx_New"),
        CTX_TYPE("ctx_Type"),
        CTX_TYPECHECK("ctx_TypeCheck"),
        CTX_TYPECHECK_G("ctx_TypeCheck_g"),
        CTX_SETTYPE("ctx_SetType"),
        CTX_TYPE_ISSUBTYPE("ctx_Type_IsSubtype"),
        CTX_TYPE_GETNAME("ctx_Type_GetName"),
        CTX_TYPE_CHECKSLOT("ctx_Type_CheckSlot"),
        CTX_IS("ctx_Is"),
        CTX_IS_G("ctx_Is_g"),
        CTX_TYPE_GENERIC_NEW("ctx_Type_GenericNew"),
        CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble"),
        CTX_FLOAT_ASDOUBLE("ctx_Float_AsDouble"),

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
        CTX_GETITEM_I("ctx_GetItem_i"),
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
        CTX_NUMBER_CHECK("ctx_Number_Check"),
        CTX_LENGTH("ctx_Length"),
        CTX_IMPORT_IMPORTMODULE("ctx_Import_ImportModule"),
        CTX_TUPLE_CHECK("ctx_Tuple_Check"),
        CTX_TUPLE_FROMARRAY("ctx_Tuple_FromArray"),
        CTX_TUPLE_BUILDER_NEW("ctx_TupleBuilder_New"),
        CTX_TUPLE_BUILDER_SET("ctx_TupleBuilder_Set"),
        CTX_TUPLE_BUILDER_BUILD("ctx_TupleBuilder_Build"),
        CTX_TUPLE_BUILDER_CANCEL("ctx_TupleBuilder_Cancel"),
        CTX_LIST_CHECK("ctx_List_Check"),
        CTX_LIST_BUILDER_NEW("ctx_ListBuilder_New"),
        CTX_LIST_BUILDER_SET("ctx_ListBuilder_Set"),
        CTX_LIST_BUILDER_BUILD("ctx_ListBuilder_Build"),
        CTX_LIST_BUILDER_CANCEL("ctx_ListBuilder_Cancel"),
        CTX_TRACKER_NEW("ctx_Tracker_New"),
        CTX_TRACKER_ADD("ctx_Tracker_Add"),
        CTX_TRACKER_FORGET_ALL("ctx_Tracker_ForgetAll"),
        CTX_TRACKER_CLOSE("ctx_Tracker_Close"),
        CTX_FIELD_STORE("ctx_Field_Store"),
        CTX_FIELD_LOAD("ctx_Field_Load"),
        CTX_LEAVEPYTHONEXECUTION("ctx_LeavePythonExecution"),
        CTX_REENTERPYTHONEXECUTION("ctx_ReenterPythonExecution"),
        CTX_GLOBAL_STORE("ctx_Global_Store"),
        CTX_GLOBAL_LOAD("ctx_Global_Load"),
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

        HPyContextMember(String name) {
            this.name = name;
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

    private static HPyExecuteWrapper createContextFunction(GraalHPyContextFunction node) {
        return null;
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

        // TODO

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

    static Object createContextFunction(HPyContextMember member) {
        return switch (member) {
            case CTX_ASPYOBJECT -> new HPyExecuteDefaultWrapper(GraalHPyAsPyObjectNodeGen::create, GraalHPyAsPyObjectNodeGen.getUncached());
            case CTX_DUP -> new HPyExecuteDefaultWrapper(GraalHPyDupNodeGen::create, GraalHPyDupNodeGen.getUncached());
            case CTX_CLOSE -> new HPyExecuteDefaultWrapper(GraalHPyCloseNodeGen::create, GraalHPyCloseNodeGen.getUncached());
            case CTX_MODULE_CREATE -> new HPyExecuteDefaultWrapper(GraalHPyModuleCreateNodeGen::create, GraalHPyModuleCreateNodeGen.getUncached());
            case CTX_BOOL_FROMLONG -> new HPyExecuteDefaultWrapper(GraalHPyBoolFromLongNodeGen::create, GraalHPyBoolFromLongNodeGen.getUncached());
            case CTX_LONG_FROMLONG, CTX_LONG_FROMLONGLONG, CTX_LONG_FROMSSIZE_T -> new HPyExecuteWrapperWithFlag(GraalHPyLongFromLongNodeGen::create, GraalHPyLongFromLongNodeGen.getUncached(), true);

            case CTX_LONG_FROMUNSIGNEDLONG, CTX_LONG_FROM_UNSIGNEDLONGLONG, CTX_LONG_FROMSIZE_T -> new HPyExecuteWrapperWithFlag(GraalHPyLongFromLongNodeGen::create,
                            GraalHPyLongFromLongNodeGen.getUncached(), false);
            case CTX_LONG_ASLONG, CTX_LONG_ASLONGLONG -> new HPyLongAsPrimitiveExecuteWrapper(1, java.lang.Long.BYTES, true, false);
            case CTX_LONG_ASUNSIGNEDLONG, CTX_LONG_ASUNSIGNEDLONGLONG, CTX_LONG_ASSIZE_T, CTX_LONG_ASVOIDPTR -> new HPyLongAsPrimitiveExecuteWrapper(0, java.lang.Long.BYTES, true, true);
            case CTX_LONG_ASUNSIGNEDLONGMASK, CTX_LONG_ASUNSIGNEDLONGLONGMASK -> new HPyLongAsPrimitiveExecuteWrapper(0, java.lang.Long.BYTES, false, false);
            case CTX_LONG_ASSSIZE_T -> new HPyLongAsPrimitiveExecuteWrapper(1, java.lang.Long.BYTES, true, true);
            case CTX_LONG_ASDOUBLE -> new HPyExecuteDefaultWrapper(GraalHPyLongAsDoubleNodeGen::create, GraalHPyLongAsDoubleNodeGen.getUncached());
            case CTX_NEW -> new HPyExecuteDefaultWrapper(GraalHPyNewNodeGen::create, GraalHPyNewNodeGen.getUncached());
            case CTX_TYPE -> new HPyExecuteDefaultWrapper(GraalHPyTypeNodeGen::create, GraalHPyTypeNodeGen.getUncached());
            case CTX_TYPECHECK -> new HPyExecuteWrapperWithFlag(GraalHPyTypeCheckNodeGen::create, GraalHPyTypeCheckNodeGen.getUncached(), false);
            case CTX_TYPECHECK_G -> new HPyExecuteWrapperWithFlag(GraalHPyTypeCheckNodeGen::create, GraalHPyTypeCheckNodeGen.getUncached(), true);
            case CTX_IS -> new HPyExecuteWrapperWithFlag(GraalHPyIsNodeGen::create, GraalHPyIsNodeGen.getUncached(), false);
            case CTX_IS_G -> new HPyExecuteWrapperWithFlag(GraalHPyIsNodeGen::create, GraalHPyIsNodeGen.getUncached(), true);
            case CTX_TYPE_GENERIC_NEW -> new HPyExecuteDefaultWrapper(GraalHPyTypeGenericNewNodeGen::create, GraalHPyTypeGenericNewNodeGen.getUncached());
            case CTX_AS_STRUCT, CTX_AS_STRUCT_LEGACY -> new HPyExecuteDefaultWrapper(GraalHPyCastNodeGen::create, GraalHPyCastNodeGen.getUncached());

            // unary
            case CTX_NEGATIVE -> new HPyExecuteWrapperUnaryArithmetic(UnaryArithmetic.Neg);
            case CTX_POSITIVE -> new HPyExecuteWrapperUnaryArithmetic(UnaryArithmetic.Pos);
            case CTX_ABSOLUTE -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_ABS, 1);
            case CTX_INVERT -> new HPyExecuteWrapperUnaryArithmetic(UnaryArithmetic.Invert);
            case CTX_INDEX -> new HPyExecuteDefaultWrapper(GraalHPyAsIndexNodeGen::create, GraalHPyAsIndexNodeGen.getUncached());
            case CTX_LONG -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_INT, 1);
            case CTX_FLOAT -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_FLOAT, 1);

            // binary
            case CTX_ADD -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Add);
            case CTX_SUB -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Sub);
            case CTX_MULTIPLY -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Mul);
            case CTX_MATRIXMULTIPLY -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.MatMul);
            case CTX_FLOORDIVIDE -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.FloorDiv);
            case CTX_TRUEDIVIDE -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.TrueDiv);
            case CTX_REMAINDER -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Mod);
            case CTX_DIVMOD -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.DivMod);
            case CTX_LSHIFT -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.LShift);
            case CTX_RSHIFT -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.RShift);
            case CTX_AND -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.And);
            case CTX_XOR -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Xor);
            case CTX_OR -> new HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic.Or);
            case CTX_INPLACEADD -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IAdd);
            case CTX_INPLACESUBTRACT -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.ISub);
            case CTX_INPLACEMULTIPLY -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IMul);
            case CTX_INPLACEMATRIXMULTIPLY -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IMatMul);
            case CTX_INPLACEFLOORDIVIDE -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IFloorDiv);
            case CTX_INPLACETRUEDIVIDE -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.ITrueDiv);
            case CTX_INPLACEREMAINDER -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IMod);
            case CTX_INPLACELSHIFT -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.ILShift);
            case CTX_INPLACERSHIFT -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IRShift);
            case CTX_INPLACEAND -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IAnd);
            case CTX_INPLACEXOR -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IXor);
            case CTX_INPLACEOR -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IOr);

            // ternary
            case CTX_POWER -> new HPyExecuteWrapperTernaryArithmetic(TernaryArithmetic.Pow);
            case CTX_INPLACEPOWER -> new HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic.IPow);

            case CTX_CALLABLE_CHECK -> new HPyExecuteDefaultWrapper(GraalHPyIsCallableNodeGen::create, GraalHPyIsCallableNodeGen.getUncached());
            case CTX_CALLTUPLEDICT -> new HPyExecuteDefaultWrapper(GraalHPyCallTupleDictNodeGen::create, GraalHPyCallTupleDictNodeGen.getUncached());

            case CTX_DICT_CHECK -> new HPyExecuteWrapperWithBuiltinType(GraalHPyCheckBuiltinTypeNodeGen::create, GraalHPyCheckBuiltinTypeNodeGen.getUncached(), PythonBuiltinClassType.PDict);
            case CTX_DICT_NEW -> new HPyExecuteDefaultWrapper(GraalHPyDictNewNodeGen::create, GraalHPyDictNewNodeGen.getUncached());
            case CTX_DICT_SETITEM -> new HPyExecuteDefaultWrapper(GraalHPyDictSetItemNodeGen::create, GraalHPyDictSetItemNodeGen.getUncached());
            case CTX_DICT_GETITEM -> new HPyExecuteDefaultWrapper(GraalHPyDictGetItemNodeGen::create, GraalHPyDictGetItemNodeGen.getUncached());
            case CTX_LIST_NEW -> new HPyExecuteDefaultWrapper(GraalHPyListNewNodeGen::create, GraalHPyListNewNodeGen.getUncached());
            case CTX_LIST_APPEND -> new HPyExecuteDefaultWrapper(GraalHPyListAppendNodeGen::create, GraalHPyListAppendNodeGen.getUncached());
            case CTX_FLOAT_FROMDOUBLE -> new HPyExecuteDefaultWrapper(GraalHPyFloatFromDoubleNodeGen::create, GraalHPyFloatFromDoubleNodeGen.getUncached());
            case CTX_FLOAT_ASDOUBLE -> new HPyExecuteDefaultWrapper(GraalHPyFloatAsDoubleNodeGen::create, GraalHPyFloatAsDoubleNodeGen.getUncached());
            case CTX_BYTES_CHECK -> new HPyExecuteWrapperWithBuiltinType(GraalHPyCheckBuiltinTypeNodeGen::create, GraalHPyCheckBuiltinTypeNodeGen.getUncached(), PythonBuiltinClassType.PBytes);
            case CTX_BYTES_GET_SIZE -> new HPyExecuteDefaultWrapper(GraalHPyBytesGetSizeNodeGen::create, GraalHPyBytesGetSizeNodeGen.getUncached());
            case CTX_BYTES_SIZE -> new HPyExecuteDefaultWrapper(GraalHPyBytesGetSizeNodeGen::create, GraalHPyBytesGetSizeNodeGen.getUncached());
            case CTX_BYTES_AS_STRING -> new HPyExecuteDefaultWrapper(GraalHPyBytesAsStringNodeGen::create, GraalHPyBytesAsStringNodeGen.getUncached());
            case CTX_BYTES_ASSTRING -> new HPyExecuteDefaultWrapper(GraalHPyBytesAsStringNodeGen::create, GraalHPyBytesAsStringNodeGen.getUncached());
            case CTX_BYTES_FROMSTRING -> new HPyExecuteWrapperWithFlag(GraalHPyBytesFromStringAndSizeNodeGen::create, GraalHPyBytesFromStringAndSizeNodeGen.getUncached(), false);
            case CTX_BYTES_FROMSTRINGANDSIZE -> new HPyExecuteWrapperWithFlag(GraalHPyBytesFromStringAndSizeNodeGen::create, GraalHPyBytesFromStringAndSizeNodeGen.getUncached(), true);
            case CTX_ERR_NOMEMORY -> new HPyErrRaisePredefinedExecuteWrapper(PythonBuiltinClassType.MemoryError);
            case CTX_ERR_SETSTRING -> new HPyExecuteWrapperWithFlag(GraalHPyErrSetStringNodeGen::create, GraalHPyErrSetStringNodeGen.getUncached(), true);
            case CTX_ERR_SETOBJECT -> new HPyExecuteWrapperWithFlag(GraalHPyErrSetStringNodeGen::create, GraalHPyErrSetStringNodeGen.getUncached(), false);
            case CTX_ERR_SETFROMERRNOWITHFILENAME -> new HPyExecuteWrapperWithFlag(GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen::create,
                            GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen.getUncached(), false);
            case CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS -> new HPyExecuteWrapperWithFlag(GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen::create,
                            GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen.getUncached(), true);
            case CTX_ERR_OCCURRED -> new HPyExecuteDefaultWrapper(GraalHPyErrOccurredNodeGen::create, GraalHPyErrOccurredNodeGen.getUncached());
            case CTX_ERR_EXCEPTIONMATCHES -> new HPyExecuteDefaultWrapper(GraalHPyErrExceptionMatchesNodeGen::create, GraalHPyErrExceptionMatchesNodeGen.getUncached());
            case CTX_ERR_CLEAR -> new HPyExecuteDefaultWrapper(GraalHPyErrClearNodeGen::create, GraalHPyErrClearNodeGen.getUncached());
            case CTX_ERR_NEWEXCEPTION -> new HPyExecuteWrapperWithFlag(GraalHPyNewExceptionNodeGen::create, GraalHPyNewExceptionNodeGen.getUncached(), false);
            case CTX_ERR_NEWEXCEPTIONWITHDOC -> new HPyExecuteWrapperWithFlag(GraalHPyNewExceptionNodeGen::create, GraalHPyNewExceptionNodeGen.getUncached(), true);
            case CTX_ERR_WARNEX -> new HPyExecuteDefaultWrapper(GraalHPyErrWarnExNodeGen::create, GraalHPyErrWarnExNodeGen.getUncached());
            case CTX_ERR_WRITEUNRAISABLE -> new HPyExecuteDefaultWrapper(GraalHPyErrWriteUnraisableNodeGen::create, GraalHPyErrWriteUnraisableNodeGen.getUncached());
            case CTX_FATALERROR -> new HPyExecuteDefaultWrapper(GraalHPyFatalErrorNodeGen::create, GraalHPyFatalErrorNodeGen.getUncached());
            case CTX_FROMPYOBJECT -> new HPyExecuteDefaultWrapper(GraalHPyFromPyObjectNodeGen::create, GraalHPyFromPyObjectNodeGen.getUncached());
            case CTX_UNICODE_CHECK -> new HPyExecuteWrapperWithBuiltinType(GraalHPyCheckBuiltinTypeNodeGen::create, GraalHPyCheckBuiltinTypeNodeGen.getUncached(), PythonBuiltinClassType.PString);
            case CTX_ISTRUE -> new HPyExecuteDefaultWrapper(GraalHPyIsTrueNodeGen::create, GraalHPyIsTrueNodeGen.getUncached());
            case CTX_UNICODE_ASUTF8STRING -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeAsCharsetStringNodeGen::create, GraalHPyUnicodeAsCharsetStringNodeGen.getUncached(),
                            StandardCharsets.UTF_8);
            case CTX_UNICODE_ASASCIISTRING -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeAsCharsetStringNodeGen::create, GraalHPyUnicodeAsCharsetStringNodeGen.getUncached(),
                            StandardCharsets.US_ASCII);
            case CTX_UNICODE_ASLATIN1STRING -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeAsCharsetStringNodeGen::create, GraalHPyUnicodeAsCharsetStringNodeGen.getUncached(),
                            StandardCharsets.ISO_8859_1);
            case CTX_UNICODE_ASUTF8ANDSIZE -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeAsUTF8AndSizeNodeGen::create, GraalHPyUnicodeAsUTF8AndSizeNodeGen.getUncached());
            case CTX_UNICODE_FROMSTRING -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeFromStringNodeGen::create, GraalHPyUnicodeFromStringNodeGen.getUncached());
            case CTX_UNICODE_FROMWIDECHAR -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeFromWcharNodeGen::create, GraalHPyUnicodeFromWcharNodeGen.getUncached());
            case CTX_UNICODE_DECODEASCII -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeDecodeCharsetAndSizeAndErrorsNodeGen::create,
                            GraalHPyUnicodeDecodeCharsetAndSizeAndErrorsNodeGen.getUncached(), T_ASCII_UPPERCASE);
            case CTX_UNICODE_DECODELATIN1 -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeDecodeCharsetAndSizeAndErrorsNodeGen::create,
                            GraalHPyUnicodeDecodeCharsetAndSizeAndErrorsNodeGen.getUncached(), T_ISO_8859_1);
// members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT.ordinal()] =
// GraalHPyUnicodeDecodeCharset.decodeFSDefault();
// members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULTANDSIZE.ordinal()] =
// GraalHPyUnicodeDecodeCharsetAndSize.decodeFSDefault();
            case CTX_UNICODE_ENCODEFSDEFAULT -> new HPyExecuteWrapperWithObject(GraalHPyUnicodeAsCharsetStringNodeGen::create, GraalHPyUnicodeAsCharsetStringNodeGen.getUncached(),
                            getFSDefaultCharset());
            case CTX_UNICODE_READCHAR -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeReadCharNodeGen::create, GraalHPyUnicodeReadCharNodeGen.getUncached());
            case CTX_TYPE_FROM_SPEC -> new HPyExecuteDefaultWrapper(GraalHPyTypeFromSpecNodeGen::create, GraalHPyTypeFromSpecNodeGen.getUncached());
            case CTX_GETATTR -> new HPyExecuteWrapperWithMode(GraalHPyGetAttrNodeGen::create, GraalHPyGetAttrNodeGen.getUncached(), OBJECT);
            case CTX_GETATTR_S -> new HPyExecuteWrapperWithMode(GraalHPyGetAttrNodeGen::create, GraalHPyGetAttrNodeGen.getUncached(), CHAR_PTR);
            case CTX_MAYBEGETATTR_S -> new HPyExecuteDefaultWrapper(GraalHPyMaybeGetAttrSNodeGen::create, GraalHPyMaybeGetAttrSNodeGen.getUncached());
            case CTX_HASATTR -> new HPyExecuteWrapperWithMode(GraalHPyHasAttrNodeGen::create, GraalHPyHasAttrNodeGen.getUncached(), OBJECT);
            case CTX_HASATTR_S -> new HPyExecuteWrapperWithMode(GraalHPyHasAttrNodeGen::create, GraalHPyHasAttrNodeGen.getUncached(), CHAR_PTR);
            case CTX_SETATTR -> new HPyExecuteWrapperWithMode(GraalHPySetAttrNodeGen::create, GraalHPySetAttrNodeGen.getUncached(), OBJECT);
            case CTX_SETATTR_S -> new HPyExecuteWrapperWithMode(GraalHPySetAttrNodeGen::create, GraalHPySetAttrNodeGen.getUncached(), CHAR_PTR);
            case CTX_GETITEM -> new HPyExecuteWrapperWithMode(GraalHPyGetItemNodeGen::create, GraalHPyGetItemNodeGen.getUncached(), OBJECT);
            case CTX_GETITEM_S -> new HPyExecuteWrapperWithMode(GraalHPyGetItemNodeGen::create, GraalHPyGetItemNodeGen.getUncached(), CHAR_PTR);
            case CTX_GETITEM_I -> new HPyExecuteWrapperWithMode(GraalHPyGetItemNodeGen::create, GraalHPyGetItemNodeGen.getUncached(), INT32);
            case CTX_SETITEM -> new HPyExecuteWrapperWithMode(GraalHPySetItemNodeGen::create, GraalHPySetItemNodeGen.getUncached(), OBJECT);
            case CTX_SETITEM_S -> new HPyExecuteWrapperWithMode(GraalHPySetItemNodeGen::create, GraalHPySetItemNodeGen.getUncached(), CHAR_PTR);
            case CTX_SETITEM_I -> new HPyExecuteWrapperWithMode(GraalHPySetItemNodeGen::create, GraalHPySetItemNodeGen.getUncached(), INT32);
            case CTX_CONTAINS -> new HPyExecuteDefaultWrapper(GraalHPyContainsNodeGen::create, GraalHPyContainsNodeGen.getUncached());
            case CTX_REPR -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_REPR, 1);
            case CTX_STR -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_STR, 1);
            case CTX_ASCII -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_ASCII, 1);
            case CTX_BYTES -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_BYTES, 1);
            case CTX_RICHCOMPARE -> new HPyExecuteWrapperWithFlag(GraalHPyRichcompareNodeGen::create, GraalHPyRichcompareNodeGen.getUncached(), false);
            case CTX_RICHCOMPAREBOOL -> new HPyExecuteWrapperWithFlag(GraalHPyRichcompareNodeGen::create, GraalHPyRichcompareNodeGen.getUncached(), true);
            case CTX_HASH -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_HASH, 1, ReturnType.INT);
            case CTX_NUMBER_CHECK -> new HPyExecuteDefaultWrapper(GraalHPyIsNumberNodeGen::create, GraalHPyIsNumberNodeGen.getUncached());
            case CTX_LENGTH -> new HPyCallBuiltinFunctionExecuteWrapper(BuiltinNames.T_LEN, 1, ReturnType.INT);
            case CTX_IMPORT_IMPORTMODULE -> new HPyExecuteDefaultWrapper(GraalHPyImportModuleNodeGen::create, GraalHPyImportModuleNodeGen.getUncached());
            case CTX_TUPLE_FROMARRAY -> new HPyExecuteDefaultWrapper(GraalHPyTupleFromArrayNodeGen::create, GraalHPyTupleFromArrayNodeGen.getUncached());
            case CTX_TUPLE_CHECK -> new HPyExecuteWrapperWithBuiltinType(GraalHPyCheckBuiltinTypeNodeGen::create, GraalHPyCheckBuiltinTypeNodeGen.getUncached(), PythonBuiltinClassType.PTuple);
            case CTX_TUPLE_BUILDER_NEW, CTX_LIST_BUILDER_NEW -> new HPyExecuteDefaultWrapper(GraalHPyBuilderNewNodeGen::create, GraalHPyBuilderNewNodeGen.getUncached());
            case CTX_TUPLE_BUILDER_SET, CTX_LIST_BUILDER_SET -> new HPyExecuteDefaultWrapper(GraalHPyBuilderSetNodeGen::create, GraalHPyBuilderSetNodeGen.getUncached());
            case CTX_TUPLE_BUILDER_CANCEL, CTX_LIST_BUILDER_CANCEL -> new HPyExecuteDefaultWrapper(GraalHPyBuilderCancelNodeGen::create, GraalHPyBuilderCancelNodeGen.getUncached());
            case CTX_TUPLE_BUILDER_BUILD -> new HPyExecuteWrapperWithBuiltinType(GraalHPyBuilderBuildNodeGen::create, GraalHPyBuilderBuildNodeGen.getUncached(), PythonBuiltinClassType.PTuple);
            case CTX_LIST_BUILDER_BUILD -> new HPyExecuteWrapperWithBuiltinType(GraalHPyBuilderBuildNodeGen::create, GraalHPyBuilderBuildNodeGen.getUncached(), PythonBuiltinClassType.PList);
            case CTX_LIST_CHECK -> new HPyExecuteWrapperWithBuiltinType(GraalHPyCheckBuiltinTypeNodeGen::create, GraalHPyCheckBuiltinTypeNodeGen.getUncached(), PythonBuiltinClassType.PList);

            case CTX_TRACKER_NEW -> new HPyExecuteDefaultWrapper(GraalHPyTrackerNewNodeGen::create, GraalHPyTrackerNewNodeGen.getUncached());
            case CTX_TRACKER_ADD -> new HPyExecuteDefaultWrapper(GraalHPyTrackerAddNodeGen::create, GraalHPyTrackerAddNodeGen.getUncached());
            case CTX_TRACKER_FORGET_ALL -> new HPyExecuteDefaultWrapper(GraalHPyTrackerForgetAllNodeGen::create, GraalHPyTrackerForgetAllNodeGen.getUncached());
            case CTX_TRACKER_CLOSE -> new HPyExecuteDefaultWrapper(GraalHPyTrackerCleanupNodeGen::create, GraalHPyTrackerCleanupNodeGen.getUncached());

            case CTX_FIELD_STORE -> new HPyExecuteDefaultWrapper(GraalHPyFieldStoreNodeGen::create, GraalHPyFieldStoreNodeGen.getUncached());
            case CTX_FIELD_LOAD -> new HPyExecuteDefaultWrapper(GraalHPyFieldLoadNodeGen::create, GraalHPyFieldLoadNodeGen.getUncached());
            case CTX_LEAVEPYTHONEXECUTION -> new HPyExecuteDefaultWrapper(GraalHPyLeavePythonExecutionNodeGen::create, GraalHPyLeavePythonExecutionNodeGen.getUncached());
            case CTX_REENTERPYTHONEXECUTION -> new HPyExecuteDefaultWrapper(GraalHPyReenterPythonExecutionNodeGen::create, GraalHPyReenterPythonExecutionNodeGen.getUncached());
            case CTX_GLOBAL_STORE -> new HPyExecuteDefaultWrapper(GraalHPyGlobalStoreNodeGen::create, GraalHPyGlobalStoreNodeGen.getUncached());
            case CTX_GLOBAL_LOAD -> new HPyExecuteDefaultWrapper(GraalHPyGlobalLoadNodeGen::create, GraalHPyGlobalLoadNodeGen.getUncached());
            case CTX_DUMP -> new HPyExecuteDefaultWrapper(GraalHPyDumpNodeGen::create, GraalHPyDumpNodeGen.getUncached());

            case CTX_TYPE_ISSUBTYPE -> new HPyExecuteDefaultWrapper(GraalHPyTypeIsSubtypeNodeGen::create, GraalHPyTypeIsSubtypeNodeGen.getUncached());
            case CTX_TYPE_GETNAME -> new HPyExecuteDefaultWrapper(GraalHPyTypeGetNameNodeGen::create, GraalHPyTypeGetNameNodeGen.getUncached());
            case CTX_SETTYPE -> new HPyExecuteDefaultWrapper(GraalHPySetTypeNodeGen::create, GraalHPySetTypeNodeGen.getUncached());
            case CTX_TYPE_CHECKSLOT -> new HPyExecuteDefaultWrapper(GraalHPyTypeCheckSlotNodeGen::create, GraalHPyTypeCheckSlotNodeGen.getUncached());

            case CTX_DICT_KEYS -> new HPyExecuteDefaultWrapper(GraalHPyDictKeysNodeGen::create, GraalHPyDictKeysNodeGen.getUncached());

            case CTX_UNICODE_FROMENCODEDOBJECT -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeFromEncodedObjectNodeGen::create, GraalHPyUnicodeFromEncodedObjectNodeGen.getUncached());
            case CTX_UNICODE_INTERNFROMSTRING -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeInternFromStringNodeGen::create, GraalHPyUnicodeInternFromStringNodeGen.getUncached());
            case CTX_UNICODE_SUBSTRING -> new HPyExecuteDefaultWrapper(GraalHPyUnicodeSubstringNodeGen::create, GraalHPyUnicodeSubstringNodeGen.getUncached());

            case CTX_CONTEXTVAR_NEW -> new HPyExecuteDefaultWrapper(GraalHPyContextVarNewNodeGen::create, GraalHPyContextVarNewNodeGen.getUncached());
            case CTX_CONTEXTVAR_GET -> new HPyExecuteDefaultWrapper(GraalHPyContextVarGetNodeGen::create, GraalHPyContextVarGetNodeGen.getUncached());
            case CTX_CONTEXTVAR_SET -> new HPyExecuteDefaultWrapper(GraalHPyContextVarSetNodeGen::create, GraalHPyContextVarSetNodeGen.getUncached());
            case CTX_CAPSULE_NEW -> new HPyExecuteDefaultWrapper(GraalHPyCapsuleNewNodeGen::create, GraalHPyCapsuleNewNodeGen.getUncached());
            case CTX_CAPSULE_GET -> new HPyExecuteDefaultWrapper(GraalHPyCapsuleGetNodeGen::create, GraalHPyCapsuleGetNodeGen.getUncached());
            case CTX_CAPSULE_ISVALID -> new HPyExecuteDefaultWrapper(GraalHPyCapsuleIsValidNodeGen::create, GraalHPyCapsuleIsValidNodeGen.getUncached());
            case CTX_CAPSULE_SET -> new HPyExecuteDefaultWrapper(GraalHPyCapsuleSetNodeGen::create, GraalHPyCapsuleSetNodeGen.getUncached());

            case CTX_SEQUENCE_CHECK -> new HPyExecuteDefaultWrapper(GraalHPyIsSequenceNodeGen::create, GraalHPyIsSequenceNodeGen.getUncached());
            case CTX_SLICE_UNPACK -> new HPyExecuteDefaultWrapper(GraalHPySliceUnpackNodeGen::create, GraalHPySliceUnpackNodeGen.getUncached());

            case CTX_SEQITER_NEW -> new HPyExecuteDefaultWrapper(GraalHPySeqIterNewNodeGen::create, GraalHPySeqIterNewNodeGen.getUncached());
            default -> null;
        };
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class HPyExecuteWrapper implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    abstract static class HPyExecuteWrapperGeneric<T> extends HPyExecuteWrapper {
        protected final Supplier<T> cachedSupplier;
        protected final T uncached;

        public HPyExecuteWrapperGeneric(Supplier<T> cachedSupplier, T uncached) {
            this.cachedSupplier = cachedSupplier;
            this.uncached = uncached;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteDefaultWrapper extends HPyExecuteWrapperGeneric<GraalHPyContextFunction> {

        public HPyExecuteDefaultWrapper(Supplier<GraalHPyContextFunction> cachedSupplier, GraalHPyContextFunction uncached) {
            super(cachedSupplier, uncached);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(value = "this.createNode()", uncached = "this.getUncached()") GraalHPyContextFunction node)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return node.execute(arguments);
        }

        @NeverDefault
        GraalHPyContextFunction createNode() {
            return cachedSupplier.get();
        }

        GraalHPyContextFunction getUncached() {
            return uncached;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperUnaryArithmetic extends HPyExecuteWrapper {
        final UnaryArithmetic unaryOperator;

        public HPyExecuteWrapperUnaryArithmetic(UnaryArithmetic unaryOperator) {
            this.unaryOperator = unaryOperator;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(parameters = "this.unaryOperator") GraalHPyArithmeticNode node) throws ArityException {
            return node.execute(arguments);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperBinaryArithmetic extends HPyExecuteWrapper {
        final BinaryArithmetic binaryOperator;

        public HPyExecuteWrapperBinaryArithmetic(BinaryArithmetic binaryOperator) {
            this.binaryOperator = binaryOperator;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(parameters = "this.binaryOperator") GraalHPyArithmeticNode node) throws ArityException {
            return node.execute(arguments);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperTernaryArithmetic extends HPyExecuteWrapper {
        final TernaryArithmetic ternaryOperator;

        public HPyExecuteWrapperTernaryArithmetic(TernaryArithmetic ternaryOperator) {
            this.ternaryOperator = ternaryOperator;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(parameters = "this.ternaryOperator") GraalHPyArithmeticNode node) throws ArityException {
            return node.execute(arguments);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperInplaceArithmetic extends HPyExecuteWrapper {
        final InplaceArithmetic inplaceOperator;

        public HPyExecuteWrapperInplaceArithmetic(InplaceArithmetic inplaceOperator) {
            this.inplaceOperator = inplaceOperator;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(parameters = "this.inplaceOperator") GraalHPyArithmeticNode node) throws ArityException {
            return node.execute(arguments);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperWithFlag extends HPyExecuteWrapperGeneric<HPyContextFunctionWithFlag> {

        private final boolean flag;

        public HPyExecuteWrapperWithFlag(Supplier<HPyContextFunctionWithFlag> cachedSupplier, HPyContextFunctionWithFlag uncached, boolean flag) {
            super(cachedSupplier, uncached);
            this.flag = flag;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(value = "this.createNode()", uncached = "this.getUncached()") HPyContextFunctionWithFlag node)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return node.execute(arguments, flag);
        }

        @NeverDefault
        HPyContextFunctionWithFlag createNode() {
            return cachedSupplier.get();
        }

        HPyContextFunctionWithFlag getUncached() {
            return uncached;
        }
    }

    /**
     * For {@code ctx_GetItem(_i|_s)},{@code ctx_SetItem(_i|_s)}, {@code ctx_SetAttr(_i|_s)},
     * {@code ctx_GetAttr(_i|_s)}, {@code ctx_HasAttr(_i|_s)}
     */
    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperWithMode extends HPyExecuteWrapperGeneric<HPyContextFunctionWithMode> {

        private final FunctionMode mode;

        public HPyExecuteWrapperWithMode(Supplier<HPyContextFunctionWithMode> cachedSupplier, HPyContextFunctionWithMode uncached, FunctionMode mode) {
            super(cachedSupplier, uncached);
            this.mode = mode;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(value = "this.createNode()", uncached = "this.getUncached()") HPyContextFunctionWithMode node)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return node.execute(arguments, mode);
        }

        @NeverDefault
        HPyContextFunctionWithMode createNode() {
            return cachedSupplier.get();
        }

        HPyContextFunctionWithMode getUncached() {
            return uncached;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperWithBuiltinType extends HPyExecuteWrapperGeneric<HPyContextFunctionWithBuiltinType> {

        private final PythonBuiltinClassType type;

        public HPyExecuteWrapperWithBuiltinType(Supplier<HPyContextFunctionWithBuiltinType> cachedSupplier, HPyContextFunctionWithBuiltinType uncached, PythonBuiltinClassType type) {
            super(cachedSupplier, uncached);
            this.type = type;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(value = "this.createNode()", uncached = "this.getUncached()") HPyContextFunctionWithBuiltinType node)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return node.execute(arguments, type);
        }

        @NeverDefault
        HPyContextFunctionWithBuiltinType createNode() {
            return cachedSupplier.get();
        }

        HPyContextFunctionWithBuiltinType getUncached() {
            return uncached;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyExecuteWrapperWithObject extends HPyExecuteWrapperGeneric<HPyContextFunctionWithObject> {

        private final Object obj;

        public HPyExecuteWrapperWithObject(Supplier<HPyContextFunctionWithObject> cachedSupplier, HPyContextFunctionWithObject uncached, Object obj) {
            super(cachedSupplier, uncached);
            this.obj = obj;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached(value = "this.createNode()", uncached = "this.getUncached()") HPyContextFunctionWithObject node)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return node.execute(arguments, obj);
        }

        @NeverDefault
        HPyContextFunctionWithObject createNode() {
            return cachedSupplier.get();
        }

        HPyContextFunctionWithObject getUncached() {
            return uncached;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyErrRaisePredefinedExecuteWrapper extends HPyExecuteWrapper {

        private final PythonBuiltinClassType errType;
        private final TruffleString errorMessage;
        private final boolean primitiveErrorValue;

        public HPyErrRaisePredefinedExecuteWrapper(PythonBuiltinClassType errType) {
            this(errType, null, false);
        }

        public HPyErrRaisePredefinedExecuteWrapper(PythonBuiltinClassType errType, TruffleString errorMessage) {
            this(errType, errorMessage, false);
        }

        public HPyErrRaisePredefinedExecuteWrapper(PythonBuiltinClassType errType, TruffleString errorMessage, boolean primitiveErrorValue) {
            this.errType = errType;
            this.errorMessage = errorMessage;
            this.primitiveErrorValue = primitiveErrorValue;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached GraalHPyErrRaisePredefined node) throws ArityException {
            return node.execute(arguments, errType, errorMessage, primitiveErrorValue);
        }
    }

    @TruffleBoundary
    public static Charset getFSDefaultCharset() {
        TruffleString normalizedEncoding = CharsetMapping.normalizeUncached(GetFileSystemEncodingNode.getFileSystemEncoding());
        return CharsetMapping.getCharsetNormalized(normalizedEncoding);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyCallBuiltinFunctionExecuteWrapper extends HPyExecuteWrapper {

        private final TruffleString key;
        private final int nPythonArguments;
        private final ReturnType returnType;

        public HPyCallBuiltinFunctionExecuteWrapper(TruffleString key, int nPythonArguments) {
            this(key, nPythonArguments, ReturnType.OBJECT);
        }

        public HPyCallBuiltinFunctionExecuteWrapper(TruffleString key, int nPythonArguments, ReturnType returnType) {
            this.key = key;
            this.nPythonArguments = nPythonArguments;
            this.returnType = returnType;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached GraalHPyCallBuiltinFunction node) throws ArityException {
            return node.execute(arguments, key, nPythonArguments, returnType);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HPyLongAsPrimitiveExecuteWrapper extends HPyExecuteWrapper {

        private final int signed;
        private final int targetSize;
        private final boolean exact;
        private final boolean requiresPInt;

        public HPyLongAsPrimitiveExecuteWrapper(int signed, int targetSize, boolean exact, boolean requiresPInt) {
            this.signed = signed;
            this.targetSize = targetSize;
            this.exact = exact;
            this.requiresPInt = requiresPInt;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached GraalHPyLongAsPrimitive node) throws ArityException {
            return node.execute(arguments, signed, targetSize, exact, requiresPInt);
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