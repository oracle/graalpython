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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_ELIPSIS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NONE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.SINGLETON_HANDLE_NOT_IMPLEMENTED;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CDouble;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CLong;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CVoid;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CharPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.ConstWchar_tPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Cpy_PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyCapsule_Destructor;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyContextPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyDefPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyField;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyFieldPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyGlobal;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyGlobalPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyListBuilder;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyModuleDefPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyTracker;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyTupleBuilder;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyType_SpecParamPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyType_SpecPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy_UCS4;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy_ssize_tPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.LongLong;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Size_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.UnsignedLong;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.UnsignedLongLong;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.VoidPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.VoidPtrPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType._HPyCapsule_key;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyBoxing;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyContextFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyASCIINodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAbsoluteNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAddNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAndNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsIndexNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBoolFromLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderCancelNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesAsStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesFromStringAndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesGetSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCallTupleDictNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleIsValidNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCastNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContainsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictKeysNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDivmodNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDupNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrClearNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrExceptionMatchesNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrNoMemoryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrOccurredNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetFromErrnoWithFilenameNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrSetStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrWarnExNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyErrWriteUnraisableNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFatalErrorNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFieldLoadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFieldStoreNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloatAsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloatFromDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloatNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFloorDivideNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyFromPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGetItemSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGlobalLoadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyGlobalStoreNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyHasAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyHasAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyHashNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyImportModuleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceAddNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceAndNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceFloorDivideNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceLshiftNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceMatrixMultiplyNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceMultiplyNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceOrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlacePowerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceRemainderNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceRshiftNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceSubtractNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceTrueDivideNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInPlaceXorNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyInvertNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsCallableNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsNumberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsSequenceNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsTrueNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLeavePythonExecutionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLengthNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListAppendNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListBuilderBuildNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsSsizeTNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUnsignedLongMaskNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUnsignedLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromUnsignedLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLshiftNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyMatrixMultiplyNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyMaybeGetAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyModuleCreateNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyMultiplyNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNegativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNewExceptionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNewExceptionWithDocNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyOrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyPositiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyPowerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyReenterPythonExecutionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyRemainderNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyReprNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyRichcompareBoolNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyRichcompareNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyRshiftNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySeqIterNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetItemSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySliceUnpackNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyStrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySubtractNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerAddNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerCleanupNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerForgetAllNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrackerNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTrueDivideNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTupleBuilderBuildNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTupleCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTupleFromArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeCheckSlotNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeFromSpecNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGenericNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGetNameNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeIsSubtypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsASCIIStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsLatin1StringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsUTF8AndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeAsUTF8StringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeDecodeASCIINodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeDecodeCharsetAndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeDecodeCharsetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeDecodeLatin1NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeEncodeFSDefaultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromEncodedObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeFromWcharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeInternFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeReadCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeSubstringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyXorNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyDummyToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsContextNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
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

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMember implements HPyUpcall {
        NAME("name"),
        PRIVATE("_private"),
        CTX_VERSION("ctx_version"),

        // {{start llvm ctx members}}
        H_NONE("h_None"),
        H_TRUE("h_True"),
        H_FALSE("h_False"),
        H_NOTIMPLEMENTED("h_NotImplemented"),
        H_ELLIPSIS("h_Ellipsis"),
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
        H_BASEOBJECTTYPE("h_BaseObjectType"),
        H_TYPETYPE("h_TypeType"),
        H_BOOLTYPE("h_BoolType"),
        H_LONGTYPE("h_LongType"),
        H_FLOATTYPE("h_FloatType"),
        H_UNICODETYPE("h_UnicodeType"),
        H_TUPLETYPE("h_TupleType"),
        H_LISTTYPE("h_ListType"),
        H_COMPLEXTYPE("h_ComplexType"),
        H_BYTESTYPE("h_BytesType"),
        H_MEMORYVIEWTYPE("h_MemoryViewType"),
        H_CAPSULETYPE("h_CapsuleType"),
        H_SLICETYPE("h_SliceType"),
        CTX_MODULE_CREATE("ctx_Module_Create", HPy, HPyContextPtr, HPyModuleDefPtr),
        CTX_DUP("ctx_Dup", HPy, HPyContextPtr, HPy),
        CTX_CLOSE("ctx_Close", CVoid, HPyContextPtr, HPy),
        CTX_LONG_FROMLONG("ctx_Long_FromLong", HPy, HPyContextPtr, CLong),
        CTX_LONG_FROMUNSIGNEDLONG("ctx_Long_FromUnsignedLong", HPy, HPyContextPtr, UnsignedLong),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong", HPy, HPyContextPtr, LongLong),
        CTX_LONG_FROMUNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong", HPy, HPyContextPtr, UnsignedLongLong),
        CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t", HPy, HPyContextPtr, Size_t),
        CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t", HPy, HPyContextPtr, HPy_ssize_t),
        CTX_LONG_ASLONG("ctx_Long_AsLong", CLong, HPyContextPtr, HPy),
        CTX_LONG_ASUNSIGNEDLONG("ctx_Long_AsUnsignedLong", UnsignedLong, HPyContextPtr, HPy),
        CTX_LONG_ASUNSIGNEDLONGMASK("ctx_Long_AsUnsignedLongMask", UnsignedLong, HPyContextPtr, HPy),
        CTX_LONG_ASLONGLONG("ctx_Long_AsLongLong", LongLong, HPyContextPtr, HPy),
        CTX_LONG_ASUNSIGNEDLONGLONG("ctx_Long_AsUnsignedLongLong", UnsignedLongLong, HPyContextPtr, HPy),
        CTX_LONG_ASUNSIGNEDLONGLONGMASK("ctx_Long_AsUnsignedLongLongMask", UnsignedLongLong, HPyContextPtr, HPy),
        CTX_LONG_ASSIZE_T("ctx_Long_AsSize_t", Size_t, HPyContextPtr, HPy),
        CTX_LONG_ASSSIZE_T("ctx_Long_AsSsize_t", HPy_ssize_t, HPyContextPtr, HPy),
        CTX_LONG_ASVOIDPTR("ctx_Long_AsVoidPtr", VoidPtr, HPyContextPtr, HPy),
        CTX_LONG_ASDOUBLE("ctx_Long_AsDouble", CDouble, HPyContextPtr, HPy),
        CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble", HPy, HPyContextPtr, CDouble),
        CTX_FLOAT_ASDOUBLE("ctx_Float_AsDouble", CDouble, HPyContextPtr, HPy),
        CTX_BOOL_FROMLONG("ctx_Bool_FromLong", HPy, HPyContextPtr, CLong),
        CTX_LENGTH("ctx_Length", HPy_ssize_t, HPyContextPtr, HPy),
        CTX_SEQUENCE_CHECK("ctx_Sequence_Check", Int, HPyContextPtr, HPy),
        CTX_NUMBER_CHECK("ctx_Number_Check", Int, HPyContextPtr, HPy),
        CTX_ADD("ctx_Add", HPy, HPyContextPtr, HPy, HPy),
        CTX_SUBTRACT("ctx_Subtract", HPy, HPyContextPtr, HPy, HPy),
        CTX_MULTIPLY("ctx_Multiply", HPy, HPyContextPtr, HPy, HPy),
        CTX_MATRIXMULTIPLY("ctx_MatrixMultiply", HPy, HPyContextPtr, HPy, HPy),
        CTX_FLOORDIVIDE("ctx_FloorDivide", HPy, HPyContextPtr, HPy, HPy),
        CTX_TRUEDIVIDE("ctx_TrueDivide", HPy, HPyContextPtr, HPy, HPy),
        CTX_REMAINDER("ctx_Remainder", HPy, HPyContextPtr, HPy, HPy),
        CTX_DIVMOD("ctx_Divmod", HPy, HPyContextPtr, HPy, HPy),
        CTX_POWER("ctx_Power", HPy, HPyContextPtr, HPy, HPy, HPy),
        CTX_NEGATIVE("ctx_Negative", HPy, HPyContextPtr, HPy),
        CTX_POSITIVE("ctx_Positive", HPy, HPyContextPtr, HPy),
        CTX_ABSOLUTE("ctx_Absolute", HPy, HPyContextPtr, HPy),
        CTX_INVERT("ctx_Invert", HPy, HPyContextPtr, HPy),
        CTX_LSHIFT("ctx_Lshift", HPy, HPyContextPtr, HPy, HPy),
        CTX_RSHIFT("ctx_Rshift", HPy, HPyContextPtr, HPy, HPy),
        CTX_AND("ctx_And", HPy, HPyContextPtr, HPy, HPy),
        CTX_XOR("ctx_Xor", HPy, HPyContextPtr, HPy, HPy),
        CTX_OR("ctx_Or", HPy, HPyContextPtr, HPy, HPy),
        CTX_INDEX("ctx_Index", HPy, HPyContextPtr, HPy),
        CTX_LONG("ctx_Long", HPy, HPyContextPtr, HPy),
        CTX_FLOAT("ctx_Float", HPy, HPyContextPtr, HPy),
        CTX_INPLACEADD("ctx_InPlaceAdd", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACESUBTRACT("ctx_InPlaceSubtract", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEMULTIPLY("ctx_InPlaceMultiply", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEMATRIXMULTIPLY("ctx_InPlaceMatrixMultiply", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEFLOORDIVIDE("ctx_InPlaceFloorDivide", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACETRUEDIVIDE("ctx_InPlaceTrueDivide", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEREMAINDER("ctx_InPlaceRemainder", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEPOWER("ctx_InPlacePower", HPy, HPyContextPtr, HPy, HPy, HPy),
        CTX_INPLACELSHIFT("ctx_InPlaceLshift", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACERSHIFT("ctx_InPlaceRshift", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEAND("ctx_InPlaceAnd", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEXOR("ctx_InPlaceXor", HPy, HPyContextPtr, HPy, HPy),
        CTX_INPLACEOR("ctx_InPlaceOr", HPy, HPyContextPtr, HPy, HPy),
        CTX_CALLABLE_CHECK("ctx_Callable_Check", Int, HPyContextPtr, HPy),
        CTX_CALLTUPLEDICT("ctx_CallTupleDict", HPy, HPyContextPtr, HPy, HPy, HPy),
        CTX_FATALERROR("ctx_FatalError", CVoid, HPyContextPtr, ConstCharPtr),
        CTX_ERR_SETSTRING("ctx_Err_SetString", HPy, HPyContextPtr, HPy, ConstCharPtr),
        CTX_ERR_SETOBJECT("ctx_Err_SetObject", HPy, HPyContextPtr, HPy, HPy),
        CTX_ERR_SETFROMERRNOWITHFILENAME("ctx_Err_SetFromErrnoWithFilename", HPy, HPyContextPtr, HPy, ConstCharPtr),
        CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS("ctx_Err_SetFromErrnoWithFilenameObjects", HPy, HPyContextPtr, HPy, HPy, HPy),
        CTX_ERR_OCCURRED("ctx_Err_Occurred", Int, HPyContextPtr),
        CTX_ERR_EXCEPTIONMATCHES("ctx_Err_ExceptionMatches", Int, HPyContextPtr, HPy),
        CTX_ERR_NOMEMORY("ctx_Err_NoMemory", HPy, HPyContextPtr),
        CTX_ERR_CLEAR("ctx_Err_Clear", CVoid, HPyContextPtr),
        CTX_ERR_NEWEXCEPTION("ctx_Err_NewException", HPy, HPyContextPtr, ConstCharPtr, HPy, HPy),
        CTX_ERR_NEWEXCEPTIONWITHDOC("ctx_Err_NewExceptionWithDoc", HPy, HPyContextPtr, ConstCharPtr, ConstCharPtr, HPy, HPy),
        CTX_ERR_WARNEX("ctx_Err_WarnEx", Int, HPyContextPtr, HPy, ConstCharPtr, HPy_ssize_t),
        CTX_ERR_WRITEUNRAISABLE("ctx_Err_WriteUnraisable", CVoid, HPyContextPtr, HPy),
        CTX_ISTRUE("ctx_IsTrue", Int, HPyContextPtr, HPy),
        CTX_TYPE_FROMSPEC("ctx_Type_FromSpec", HPy, HPyContextPtr, HPyType_SpecPtr, HPyType_SpecParamPtr),
        CTX_TYPE_GENERICNEW("ctx_Type_GenericNew", HPy, HPyContextPtr, HPy, HPyPtr, HPy_ssize_t, HPy),
        CTX_GETATTR("ctx_GetAttr", HPy, HPyContextPtr, HPy, HPy),
        CTX_GETATTR_S("ctx_GetAttr_s", HPy, HPyContextPtr, HPy, ConstCharPtr),
        CTX_MAYBEGETATTR_S("ctx_MaybeGetAttr_s", HPy, HPyContextPtr, HPy, ConstCharPtr),
        CTX_HASATTR("ctx_HasAttr", Int, HPyContextPtr, HPy, HPy),
        CTX_HASATTR_S("ctx_HasAttr_s", Int, HPyContextPtr, HPy, ConstCharPtr),
        CTX_SETATTR("ctx_SetAttr", Int, HPyContextPtr, HPy, HPy, HPy),
        CTX_SETATTR_S("ctx_SetAttr_s", Int, HPyContextPtr, HPy, ConstCharPtr, HPy),
        CTX_GETITEM("ctx_GetItem", HPy, HPyContextPtr, HPy, HPy),
        CTX_GETITEM_I("ctx_GetItem_i", HPy, HPyContextPtr, HPy, HPy_ssize_t),
        CTX_GETITEM_S("ctx_GetItem_s", HPy, HPyContextPtr, HPy, ConstCharPtr),
        CTX_CONTAINS("ctx_Contains", Int, HPyContextPtr, HPy, HPy),
        CTX_SETITEM("ctx_SetItem", Int, HPyContextPtr, HPy, HPy, HPy),
        CTX_SETITEM_I("ctx_SetItem_i", Int, HPyContextPtr, HPy, HPy_ssize_t, HPy),
        CTX_SETITEM_S("ctx_SetItem_s", Int, HPyContextPtr, HPy, ConstCharPtr, HPy),
        CTX_TYPE("ctx_Type", HPy, HPyContextPtr, HPy),
        CTX_TYPECHECK("ctx_TypeCheck", Int, HPyContextPtr, HPy, HPy),
        CTX_TYPECHECK_G("ctx_TypeCheck_g", Int, HPyContextPtr, HPy, HPyGlobal),
        CTX_SETTYPE("ctx_SetType", Int, HPyContextPtr, HPy, HPy),
        CTX_TYPE_ISSUBTYPE("ctx_Type_IsSubtype", Int, HPyContextPtr, HPy, HPy),
        CTX_TYPE_GETNAME("ctx_Type_GetName", ConstCharPtr, HPyContextPtr, HPy),
        CTX_IS("ctx_Is", Int, HPyContextPtr, HPy, HPy),
        CTX_IS_G("ctx_Is_g", Int, HPyContextPtr, HPy, HPyGlobal),
        CTX_ASSTRUCT("ctx_AsStruct", VoidPtr, HPyContextPtr, HPy),
        CTX_ASSTRUCTLEGACY("ctx_AsStructLegacy", VoidPtr, HPyContextPtr, HPy),
        CTX_NEW("ctx_New", HPy, HPyContextPtr, HPy, VoidPtrPtr),
        CTX_REPR("ctx_Repr", HPy, HPyContextPtr, HPy),
        CTX_STR("ctx_Str", HPy, HPyContextPtr, HPy),
        CTX_ASCII("ctx_ASCII", HPy, HPyContextPtr, HPy),
        CTX_BYTES("ctx_Bytes", HPy, HPyContextPtr, HPy),
        CTX_RICHCOMPARE("ctx_RichCompare", HPy, HPyContextPtr, HPy, HPy, Int),
        CTX_RICHCOMPAREBOOL("ctx_RichCompareBool", Int, HPyContextPtr, HPy, HPy, Int),
        CTX_HASH("ctx_Hash", HPy_hash_t, HPyContextPtr, HPy),
        CTX_SEQITER_NEW("ctx_SeqIter_New", HPy, HPyContextPtr, HPy),
        CTX_BYTES_CHECK("ctx_Bytes_Check", Int, HPyContextPtr, HPy),
        CTX_BYTES_SIZE("ctx_Bytes_Size", HPy_ssize_t, HPyContextPtr, HPy),
        CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE", HPy_ssize_t, HPyContextPtr, HPy),
        CTX_BYTES_ASSTRING("ctx_Bytes_AsString", CharPtr, HPyContextPtr, HPy),
        CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING", CharPtr, HPyContextPtr, HPy),
        CTX_BYTES_FROMSTRING("ctx_Bytes_FromString", HPy, HPyContextPtr, ConstCharPtr),
        CTX_BYTES_FROMSTRINGANDSIZE("ctx_Bytes_FromStringAndSize", HPy, HPyContextPtr, ConstCharPtr, HPy_ssize_t),
        CTX_UNICODE_FROMSTRING("ctx_Unicode_FromString", HPy, HPyContextPtr, ConstCharPtr),
        CTX_UNICODE_CHECK("ctx_Unicode_Check", Int, HPyContextPtr, HPy),
        CTX_UNICODE_ASASCIISTRING("ctx_Unicode_AsASCIIString", HPy, HPyContextPtr, HPy),
        CTX_UNICODE_ASLATIN1STRING("ctx_Unicode_AsLatin1String", HPy, HPyContextPtr, HPy),
        CTX_UNICODE_ASUTF8STRING("ctx_Unicode_AsUTF8String", HPy, HPyContextPtr, HPy),
        CTX_UNICODE_ASUTF8ANDSIZE("ctx_Unicode_AsUTF8AndSize", ConstCharPtr, HPyContextPtr, HPy, HPy_ssize_tPtr),
        CTX_UNICODE_FROMWIDECHAR("ctx_Unicode_FromWideChar", HPy, HPyContextPtr, ConstWchar_tPtr, HPy_ssize_t),
        CTX_UNICODE_DECODEFSDEFAULT("ctx_Unicode_DecodeFSDefault", HPy, HPyContextPtr, ConstCharPtr),
        CTX_UNICODE_DECODEFSDEFAULTANDSIZE("ctx_Unicode_DecodeFSDefaultAndSize", HPy, HPyContextPtr, ConstCharPtr, HPy_ssize_t),
        CTX_UNICODE_ENCODEFSDEFAULT("ctx_Unicode_EncodeFSDefault", HPy, HPyContextPtr, HPy),
        CTX_UNICODE_READCHAR("ctx_Unicode_ReadChar", HPy_UCS4, HPyContextPtr, HPy, HPy_ssize_t),
        CTX_UNICODE_DECODEASCII("ctx_Unicode_DecodeASCII", HPy, HPyContextPtr, ConstCharPtr, HPy_ssize_t, ConstCharPtr),
        CTX_UNICODE_DECODELATIN1("ctx_Unicode_DecodeLatin1", HPy, HPyContextPtr, ConstCharPtr, HPy_ssize_t, ConstCharPtr),
        CTX_UNICODE_FROMENCODEDOBJECT("ctx_Unicode_FromEncodedObject", HPy, HPyContextPtr, HPy, ConstCharPtr, ConstCharPtr),
        CTX_UNICODE_INTERNFROMSTRING("ctx_Unicode_InternFromString", HPy, HPyContextPtr, ConstCharPtr),
        CTX_UNICODE_SUBSTRING("ctx_Unicode_Substring", HPy, HPyContextPtr, HPy, HPy_ssize_t, HPy_ssize_t),
        CTX_LIST_CHECK("ctx_List_Check", Int, HPyContextPtr, HPy),
        CTX_LIST_NEW("ctx_List_New", HPy, HPyContextPtr, HPy_ssize_t),
        CTX_LIST_APPEND("ctx_List_Append", Int, HPyContextPtr, HPy, HPy),
        CTX_DICT_CHECK("ctx_Dict_Check", Int, HPyContextPtr, HPy),
        CTX_DICT_NEW("ctx_Dict_New", HPy, HPyContextPtr),
        CTX_DICT_KEYS("ctx_Dict_Keys", HPy, HPyContextPtr, HPy),
        CTX_DICT_GETITEM("ctx_Dict_GetItem", HPy, HPyContextPtr, HPy, HPy),
        CTX_TUPLE_CHECK("ctx_Tuple_Check", Int, HPyContextPtr, HPy),
        CTX_TUPLE_FROMARRAY("ctx_Tuple_FromArray", HPy, HPyContextPtr, HPyPtr, HPy_ssize_t),
        CTX_SLICE_UNPACK("ctx_Slice_Unpack", Int, HPyContextPtr, HPy, HPy_ssize_tPtr, HPy_ssize_tPtr, HPy_ssize_tPtr),
        CTX_CONTEXTVAR_NEW("ctx_ContextVar_New", HPy, HPyContextPtr, ConstCharPtr, HPy),
        CTX_CONTEXTVAR_GET("ctx_ContextVar_Get", Int, HPyContextPtr, HPy, HPy, HPyPtr),
        CTX_CONTEXTVAR_SET("ctx_ContextVar_Set", HPy, HPyContextPtr, HPy, HPy),
        CTX_IMPORT_IMPORTMODULE("ctx_Import_ImportModule", HPy, HPyContextPtr, ConstCharPtr),
        CTX_CAPSULE_NEW("ctx_Capsule_New", HPy, HPyContextPtr, VoidPtr, ConstCharPtr, HPyCapsule_Destructor),
        CTX_CAPSULE_GET("ctx_Capsule_Get", VoidPtr, HPyContextPtr, HPy, _HPyCapsule_key, ConstCharPtr),
        CTX_CAPSULE_ISVALID("ctx_Capsule_IsValid", Int, HPyContextPtr, HPy, ConstCharPtr),
        CTX_CAPSULE_SET("ctx_Capsule_Set", Int, HPyContextPtr, HPy, _HPyCapsule_key, VoidPtr),
        CTX_FROMPYOBJECT("ctx_FromPyObject", HPy, HPyContextPtr, Cpy_PyObjectPtr),
        CTX_ASPYOBJECT("ctx_AsPyObject", Cpy_PyObjectPtr, HPyContextPtr, HPy),
        CTX_LISTBUILDER_NEW("ctx_ListBuilder_New", HPyListBuilder, HPyContextPtr, HPy_ssize_t),
        CTX_LISTBUILDER_SET("ctx_ListBuilder_Set", CVoid, HPyContextPtr, HPyListBuilder, HPy_ssize_t, HPy),
        CTX_LISTBUILDER_BUILD("ctx_ListBuilder_Build", HPy, HPyContextPtr, HPyListBuilder),
        CTX_LISTBUILDER_CANCEL("ctx_ListBuilder_Cancel", CVoid, HPyContextPtr, HPyListBuilder),
        CTX_TUPLEBUILDER_NEW("ctx_TupleBuilder_New", HPyTupleBuilder, HPyContextPtr, HPy_ssize_t),
        CTX_TUPLEBUILDER_SET("ctx_TupleBuilder_Set", CVoid, HPyContextPtr, HPyTupleBuilder, HPy_ssize_t, HPy),
        CTX_TUPLEBUILDER_BUILD("ctx_TupleBuilder_Build", HPy, HPyContextPtr, HPyTupleBuilder),
        CTX_TUPLEBUILDER_CANCEL("ctx_TupleBuilder_Cancel", CVoid, HPyContextPtr, HPyTupleBuilder),
        CTX_TRACKER_NEW("ctx_Tracker_New", HPyTracker, HPyContextPtr, HPy_ssize_t),
        CTX_TRACKER_ADD("ctx_Tracker_Add", Int, HPyContextPtr, HPyTracker, HPy),
        CTX_TRACKER_FORGETALL("ctx_Tracker_ForgetAll", CVoid, HPyContextPtr, HPyTracker),
        CTX_TRACKER_CLOSE("ctx_Tracker_Close", CVoid, HPyContextPtr, HPyTracker),
        CTX_FIELD_STORE("ctx_Field_Store", CVoid, HPyContextPtr, HPy, HPyFieldPtr, HPy),
        CTX_FIELD_LOAD("ctx_Field_Load", HPy, HPyContextPtr, HPy, HPyField),
        CTX_REENTERPYTHONEXECUTION("ctx_ReenterPythonExecution", CVoid, HPyContextPtr, HPyThreadState),
        CTX_LEAVEPYTHONEXECUTION("ctx_LeavePythonExecution", HPyThreadState, HPyContextPtr),
        CTX_GLOBAL_STORE("ctx_Global_Store", CVoid, HPyContextPtr, HPyGlobalPtr, HPy),
        CTX_GLOBAL_LOAD("ctx_Global_Load", HPy, HPyContextPtr, HPyGlobal),
        CTX_DUMP("ctx_Dump", CVoid, HPyContextPtr, HPy),
        CTX_TYPE_CHECKSLOT("ctx_Type_CheckSlot", Int, HPyContextPtr, HPy, HPyDefPtr);
        // {{end llvm ctx members}}

        final String name;
        final HPyContextSignature signature;

        HPyContextMember(String name) {
            this.name = name;
            this.signature = null;
        }

        HPyContextMember(String name, HPyContextSignatureType returnType, HPyContextSignatureType... paramTypes) {
            this.name = name;
            this.signature = new HPyContextSignature(returnType, paramTypes);
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

        members[HPyContextMember.CTX_MODULE_CREATE.ordinal()] = createContextFunction(HPyContextMember.CTX_MODULE_CREATE);
        members[HPyContextMember.CTX_DUP.ordinal()] = createContextFunction(HPyContextMember.CTX_DUP);
        members[HPyContextMember.CTX_CLOSE.ordinal()] = createContextFunction(HPyContextMember.CTX_CLOSE);
        members[HPyContextMember.CTX_LONG_FROMLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMLONG);
        members[HPyContextMember.CTX_LONG_FROMUNSIGNEDLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMUNSIGNEDLONG);
        members[HPyContextMember.CTX_LONG_FROMLONGLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMLONGLONG);
        members[HPyContextMember.CTX_LONG_FROMUNSIGNEDLONGLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMUNSIGNEDLONGLONG);
        members[HPyContextMember.CTX_LONG_FROMSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMSIZE_T);
        members[HPyContextMember.CTX_LONG_FROMSSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_FROMSSIZE_T);
        members[HPyContextMember.CTX_LONG_ASLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASLONG);
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUNSIGNEDLONG);
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGMASK.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUNSIGNEDLONGMASK);
        members[HPyContextMember.CTX_LONG_ASLONGLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASLONGLONG);
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONG);
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONGMASK.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONGMASK);
        members[HPyContextMember.CTX_LONG_ASSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASSIZE_T);
        members[HPyContextMember.CTX_LONG_ASSSIZE_T.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASSSIZE_T);
        members[HPyContextMember.CTX_LONG_ASVOIDPTR.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASVOIDPTR);
        members[HPyContextMember.CTX_LONG_ASDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_LONG_ASDOUBLE);
        members[HPyContextMember.CTX_FLOAT_FROMDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOAT_FROMDOUBLE);
        members[HPyContextMember.CTX_FLOAT_ASDOUBLE.ordinal()] = createContextFunction(HPyContextMember.CTX_FLOAT_ASDOUBLE);
        members[HPyContextMember.CTX_BOOL_FROMLONG.ordinal()] = createContextFunction(HPyContextMember.CTX_BOOL_FROMLONG);
        members[HPyContextMember.CTX_LENGTH.ordinal()] = createContextFunction(HPyContextMember.CTX_LENGTH);
        members[HPyContextMember.CTX_SEQUENCE_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_SEQUENCE_CHECK);
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
        members[HPyContextMember.CTX_MAYBEGETATTR_S.ordinal()] = createContextFunction(HPyContextMember.CTX_MAYBEGETATTR_S);
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
        members[HPyContextMember.CTX_TYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE);
        members[HPyContextMember.CTX_TYPECHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPECHECK);
        members[HPyContextMember.CTX_TYPECHECK_G.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPECHECK_G);
        members[HPyContextMember.CTX_SETTYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_SETTYPE);
        members[HPyContextMember.CTX_TYPE_ISSUBTYPE.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_ISSUBTYPE);
        members[HPyContextMember.CTX_TYPE_GETNAME.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_GETNAME);
        members[HPyContextMember.CTX_IS.ordinal()] = createContextFunction(HPyContextMember.CTX_IS);
        members[HPyContextMember.CTX_IS_G.ordinal()] = createContextFunction(HPyContextMember.CTX_IS_G);
        members[HPyContextMember.CTX_ASSTRUCT.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCT);
        members[HPyContextMember.CTX_ASSTRUCTLEGACY.ordinal()] = createContextFunction(HPyContextMember.CTX_ASSTRUCTLEGACY);
        members[HPyContextMember.CTX_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_NEW);
        members[HPyContextMember.CTX_REPR.ordinal()] = createContextFunction(HPyContextMember.CTX_REPR);
        members[HPyContextMember.CTX_STR.ordinal()] = createContextFunction(HPyContextMember.CTX_STR);
        members[HPyContextMember.CTX_ASCII.ordinal()] = createContextFunction(HPyContextMember.CTX_ASCII);
        members[HPyContextMember.CTX_BYTES.ordinal()] = createContextFunction(HPyContextMember.CTX_BYTES);
        members[HPyContextMember.CTX_RICHCOMPARE.ordinal()] = createContextFunction(HPyContextMember.CTX_RICHCOMPARE);
        members[HPyContextMember.CTX_RICHCOMPAREBOOL.ordinal()] = createContextFunction(HPyContextMember.CTX_RICHCOMPAREBOOL);
        members[HPyContextMember.CTX_HASH.ordinal()] = createContextFunction(HPyContextMember.CTX_HASH);
        members[HPyContextMember.CTX_SEQITER_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_SEQITER_NEW);
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
        members[HPyContextMember.CTX_UNICODE_INTERNFROMSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_INTERNFROMSTRING);
        members[HPyContextMember.CTX_UNICODE_SUBSTRING.ordinal()] = createContextFunction(HPyContextMember.CTX_UNICODE_SUBSTRING);
        members[HPyContextMember.CTX_LIST_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_CHECK);
        members[HPyContextMember.CTX_LIST_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_NEW);
        members[HPyContextMember.CTX_LIST_APPEND.ordinal()] = createContextFunction(HPyContextMember.CTX_LIST_APPEND);
        members[HPyContextMember.CTX_DICT_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_CHECK);
        members[HPyContextMember.CTX_DICT_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_NEW);
        members[HPyContextMember.CTX_DICT_KEYS.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_KEYS);
        members[HPyContextMember.CTX_DICT_GETITEM.ordinal()] = createContextFunction(HPyContextMember.CTX_DICT_GETITEM);
        members[HPyContextMember.CTX_TUPLE_CHECK.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLE_CHECK);
        members[HPyContextMember.CTX_TUPLE_FROMARRAY.ordinal()] = createContextFunction(HPyContextMember.CTX_TUPLE_FROMARRAY);
        members[HPyContextMember.CTX_SLICE_UNPACK.ordinal()] = createContextFunction(HPyContextMember.CTX_SLICE_UNPACK);
        members[HPyContextMember.CTX_CONTEXTVAR_NEW.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_NEW);
        members[HPyContextMember.CTX_CONTEXTVAR_GET.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_GET);
        members[HPyContextMember.CTX_CONTEXTVAR_SET.ordinal()] = createContextFunction(HPyContextMember.CTX_CONTEXTVAR_SET);
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
        members[HPyContextMember.CTX_TYPE_CHECKSLOT.ordinal()] = createContextFunction(HPyContextMember.CTX_TYPE_CHECKSLOT);
        // {{end llvm ctx init}}

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
    @ImportStatic({HPyContextMember.class, PythonUtils.class})
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(Node node, GraalHPyLLVMContext backend, String key);

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
        args[0] = this;
        return memberInvokeLib.execute(member, args);
    }

    @GenerateUncached
    abstract static class HPyExecuteContextFunction extends Node {
        public abstract Object execute(HPyContextMember member, Object[] arguments) throws ArityException;

        @Specialization(guards = "member == cachedMember")
        Object doCached(@SuppressWarnings("unused") HPyContextMember member, Object[] arguments,
                        @Cached("member") HPyContextMember cachedMember,
                        @Cached("createContextFunctionNode(member)") GraalHPyContextFunction contextFunctionNode,
                        @Cached("createRetNode(member)") CExtToNativeNode retNode,
                        @Cached("createArgNodes(member)") CExtAsPythonObjectNode[] argNodes,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, cachedMember.signature.parameterTypes().length);
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
                    throw checkThrowableBeforeNative(t, "HPy context function", cachedMember.name);
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getErrorValue(cachedMember.signature.returnType());
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        Object doUncached(HPyContextMember member, Object[] arguments) throws ArityException {
            return doCached(member, arguments, member, getUncachedContextFunctionNode(member), getUncachedRetNode(member), getUncachedArgNodes(member),
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

        public static PException checkThrowableBeforeNative(Throwable t, String where1, Object where2) {
            if (t instanceof PException pe) {
                // this is ok, and will be handled correctly
                throw pe;
            }
            if (t instanceof ThreadDeath td) {
                // ThreadDeath subclasses are used internally by Truffle
                throw td;
            }
            if (t instanceof StackOverflowError soe) {
                PythonContext context = PythonContext.get(null);
                context.reacquireGilAfterStackOverflow();
                PBaseException newException = context.factory().createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, EMPTY_OBJECT_ARRAY);
                throw ExceptionUtils.wrapJavaException(soe, null, newException);
            }
            if (t instanceof OutOfMemoryError oome) {
                PBaseException newException = PythonContext.get(null).factory().createBaseException(MemoryError);
                throw ExceptionUtils.wrapJavaException(oome, null, newException);
            }
            // everything else: log and convert to PException (SystemError)
            CompilerDirectives.transferToInterpreter();
            PNodeWithContext.printStack();
            PrintStream out = new PrintStream(PythonContext.get(null).getEnv().err());
            out.println("while executing " + where1 + " " + where2);
            out.println("should not throw exceptions apart from PException");
            t.printStackTrace(out);
            out.flush();
            throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.INTERNAL_EXCEPTION_OCCURED);
        }

        private Object getErrorValue(HPyContextSignatureType type) {
            return switch (type) {
                case Int, HPy_UCS4 -> -1;
                case CLong, LongLong, UnsignedLong, UnsignedLongLong, Size_t, HPy_ssize_t, HPy_hash_t -> -1L;
                case CDouble -> -1.0;
                case HPy -> GraalHPyHandle.NULL_HANDLE;
                case VoidPtr, CharPtr, ConstCharPtr, Cpy_PyObjectPtr -> PythonContext.get(this).getNativeNull().getPtr();
                case CVoid -> PNone.NO_VALUE;
                default -> throw CompilerDirectives.shouldNotReachHere("unsupported return type");
            };
        }

        static CExtToNativeNode createRetNode(HPyContextMember member) {
            return switch (member.signature.returnType()) {
                case HPy, HPyThreadState -> HPyAsHandleNodeGen.create();
                case HPy_ssize_t, HPy_hash_t -> HPyAsNativeInt64NodeGen.create();
                default -> null;
            };
        }

        static CExtToNativeNode getUncachedRetNode(HPyContextMember member) {
            return switch (member.signature.returnType()) {
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
            HPyContextSignatureType[] argTypes = member.signature.parameterTypes();
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
            HPyContextSignatureType[] argTypes = member.signature.parameterTypes();
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

        // {{start llvm ctx func factory}}
        static GraalHPyContextFunction createContextFunctionNode(HPyContextMember member) {
            return switch (member) {
                case CTX_DUP -> GraalHPyDupNodeGen.create();
                case CTX_CLOSE -> GraalHPyCloseNodeGen.create();
                case CTX_POSITIVE -> GraalHPyPositiveNodeGen.create();
                case CTX_NEGATIVE -> GraalHPyNegativeNodeGen.create();
                case CTX_INVERT -> GraalHPyInvertNodeGen.create();
                case CTX_ADD -> GraalHPyAddNodeGen.create();
                case CTX_SUBTRACT -> GraalHPySubtractNodeGen.create();
                case CTX_MULTIPLY -> GraalHPyMultiplyNodeGen.create();
                case CTX_MATRIXMULTIPLY -> GraalHPyMatrixMultiplyNodeGen.create();
                case CTX_FLOORDIVIDE -> GraalHPyFloorDivideNodeGen.create();
                case CTX_TRUEDIVIDE -> GraalHPyTrueDivideNodeGen.create();
                case CTX_REMAINDER -> GraalHPyRemainderNodeGen.create();
                case CTX_DIVMOD -> GraalHPyDivmodNodeGen.create();
                case CTX_AND -> GraalHPyAndNodeGen.create();
                case CTX_XOR -> GraalHPyXorNodeGen.create();
                case CTX_OR -> GraalHPyOrNodeGen.create();
                case CTX_LSHIFT -> GraalHPyLshiftNodeGen.create();
                case CTX_RSHIFT -> GraalHPyRshiftNodeGen.create();
                case CTX_POWER -> GraalHPyPowerNodeGen.create();
                case CTX_INPLACEADD -> GraalHPyInPlaceAddNodeGen.create();
                case CTX_INPLACESUBTRACT -> GraalHPyInPlaceSubtractNodeGen.create();
                case CTX_INPLACEMULTIPLY -> GraalHPyInPlaceMultiplyNodeGen.create();
                case CTX_INPLACEMATRIXMULTIPLY -> GraalHPyInPlaceMatrixMultiplyNodeGen.create();
                case CTX_INPLACEFLOORDIVIDE -> GraalHPyInPlaceFloorDivideNodeGen.create();
                case CTX_INPLACETRUEDIVIDE -> GraalHPyInPlaceTrueDivideNodeGen.create();
                case CTX_INPLACEREMAINDER -> GraalHPyInPlaceRemainderNodeGen.create();
                case CTX_INPLACEPOWER -> GraalHPyInPlacePowerNodeGen.create();
                case CTX_INPLACELSHIFT -> GraalHPyInPlaceLshiftNodeGen.create();
                case CTX_INPLACERSHIFT -> GraalHPyInPlaceRshiftNodeGen.create();
                case CTX_INPLACEAND -> GraalHPyInPlaceAndNodeGen.create();
                case CTX_INPLACEXOR -> GraalHPyInPlaceXorNodeGen.create();
                case CTX_INPLACEOR -> GraalHPyInPlaceOrNodeGen.create();
                case CTX_MODULE_CREATE -> GraalHPyModuleCreateNodeGen.create();
                case CTX_BOOL_FROMLONG -> GraalHPyBoolFromLongNodeGen.create();
                case CTX_LONG_FROMLONG, CTX_LONG_FROMLONGLONG, CTX_LONG_FROMSSIZE_T -> GraalHPyLongFromLongNodeGen.create();
                case CTX_LONG_FROMUNSIGNEDLONG, CTX_LONG_FROMUNSIGNEDLONGLONG, CTX_LONG_FROMSIZE_T -> GraalHPyLongFromUnsignedLongNodeGen.create();
                case CTX_LONG_ASLONG, CTX_LONG_ASLONGLONG -> GraalHPyLongAsLongNodeGen.create();
                case CTX_LONG_ASUNSIGNEDLONG, CTX_LONG_ASUNSIGNEDLONGLONG, CTX_LONG_ASSIZE_T, CTX_LONG_ASVOIDPTR -> GraalHPyLongAsUnsignedLongNodeGen.create();
                case CTX_LONG_ASUNSIGNEDLONGMASK, CTX_LONG_ASUNSIGNEDLONGLONGMASK -> GraalHPyLongAsUnsignedLongMaskNodeGen.create();
                case CTX_LONG_ASSSIZE_T -> GraalHPyLongAsSsizeTNodeGen.create();
                case CTX_LONG_ASDOUBLE -> GraalHPyLongAsDoubleNodeGen.create();
                case CTX_DICT_NEW -> GraalHPyDictNewNodeGen.create();
                case CTX_DICT_GETITEM -> GraalHPyDictGetItemNodeGen.create();
                case CTX_LIST_NEW -> GraalHPyListNewNodeGen.create();
                case CTX_LIST_APPEND -> GraalHPyListAppendNodeGen.create();
                case CTX_FLOAT_FROMDOUBLE -> GraalHPyFloatFromDoubleNodeGen.create();
                case CTX_FLOAT_ASDOUBLE -> GraalHPyFloatAsDoubleNodeGen.create();
                case CTX_DICT_CHECK -> GraalHPyDictCheckNodeGen.create();
                case CTX_BYTES_CHECK -> GraalHPyBytesCheckNodeGen.create();
                case CTX_UNICODE_CHECK -> GraalHPyUnicodeCheckNodeGen.create();
                case CTX_TUPLE_CHECK -> GraalHPyTupleCheckNodeGen.create();
                case CTX_LIST_CHECK -> GraalHPyListCheckNodeGen.create();
                case CTX_ERR_NOMEMORY -> GraalHPyErrNoMemoryNodeGen.create();
                case CTX_ERR_SETOBJECT -> GraalHPyErrSetObjectNodeGen.create();
                case CTX_ERR_SETSTRING -> GraalHPyErrSetStringNodeGen.create();
                case CTX_ERR_SETFROMERRNOWITHFILENAME -> GraalHPyErrSetFromErrnoWithFilenameNodeGen.create();
                case CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS -> GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen.create();
                case CTX_FATALERROR -> GraalHPyFatalErrorNodeGen.create();
                case CTX_ERR_OCCURRED -> GraalHPyErrOccurredNodeGen.create();
                case CTX_ERR_EXCEPTIONMATCHES -> GraalHPyErrExceptionMatchesNodeGen.create();
                case CTX_ERR_CLEAR -> GraalHPyErrClearNodeGen.create();
                case CTX_ERR_WARNEX -> GraalHPyErrWarnExNodeGen.create();
                case CTX_ERR_WRITEUNRAISABLE -> GraalHPyErrWriteUnraisableNodeGen.create();
                case CTX_UNICODE_ASUTF8STRING -> GraalHPyUnicodeAsUTF8StringNodeGen.create();
                case CTX_UNICODE_ASLATIN1STRING -> GraalHPyUnicodeAsLatin1StringNodeGen.create();
                case CTX_UNICODE_ASASCIISTRING -> GraalHPyUnicodeAsASCIIStringNodeGen.create();
                case CTX_UNICODE_ENCODEFSDEFAULT -> GraalHPyUnicodeEncodeFSDefaultNodeGen.create();
                case CTX_UNICODE_ASUTF8ANDSIZE -> GraalHPyUnicodeAsUTF8AndSizeNodeGen.create();
                case CTX_UNICODE_FROMSTRING -> GraalHPyUnicodeFromStringNodeGen.create();
                case CTX_UNICODE_FROMWIDECHAR -> GraalHPyUnicodeFromWcharNodeGen.create();
                case CTX_UNICODE_DECODEFSDEFAULT -> GraalHPyUnicodeDecodeCharsetNodeGen.create();
                case CTX_UNICODE_DECODEFSDEFAULTANDSIZE -> GraalHPyUnicodeDecodeCharsetAndSizeNodeGen.create();
                case CTX_UNICODE_DECODEASCII -> GraalHPyUnicodeDecodeASCIINodeGen.create();
                case CTX_UNICODE_DECODELATIN1 -> GraalHPyUnicodeDecodeLatin1NodeGen.create();
                case CTX_UNICODE_READCHAR -> GraalHPyUnicodeReadCharNodeGen.create();
                case CTX_ASPYOBJECT -> GraalHPyAsPyObjectNodeGen.create();
                case CTX_BYTES_ASSTRING, CTX_BYTES_AS_STRING -> GraalHPyBytesAsStringNodeGen.create();
                case CTX_BYTES_SIZE, CTX_BYTES_GET_SIZE -> GraalHPyBytesGetSizeNodeGen.create();
                case CTX_BYTES_FROMSTRING -> GraalHPyBytesFromStringNodeGen.create();
                case CTX_BYTES_FROMSTRINGANDSIZE -> GraalHPyBytesFromStringAndSizeNodeGen.create();
                case CTX_ISTRUE -> GraalHPyIsTrueNodeGen.create();
                case CTX_GETATTR -> GraalHPyGetAttrNodeGen.create();
                case CTX_GETATTR_S -> GraalHPyGetAttrSNodeGen.create();
                case CTX_MAYBEGETATTR_S -> GraalHPyMaybeGetAttrSNodeGen.create();
                case CTX_TYPE_FROMSPEC -> GraalHPyTypeFromSpecNodeGen.create();
                case CTX_HASATTR -> GraalHPyHasAttrNodeGen.create();
                case CTX_HASATTR_S -> GraalHPyHasAttrSNodeGen.create();
                case CTX_SETATTR -> GraalHPySetAttrNodeGen.create();
                case CTX_SETATTR_S -> GraalHPySetAttrSNodeGen.create();
                case CTX_GETITEM, CTX_GETITEM_I -> GraalHPyGetItemNodeGen.create();
                case CTX_GETITEM_S -> GraalHPyGetItemSNodeGen.create();
                case CTX_SETITEM, CTX_SETITEM_I -> GraalHPySetItemNodeGen.create();
                case CTX_SETITEM_S -> GraalHPySetItemSNodeGen.create();
                case CTX_FROMPYOBJECT -> GraalHPyFromPyObjectNodeGen.create();
                case CTX_NEW -> GraalHPyNewNodeGen.create();
                case CTX_ASSTRUCT, CTX_ASSTRUCTLEGACY -> GraalHPyCastNodeGen.create();
                case CTX_TYPE_GENERICNEW -> GraalHPyTypeGenericNewNodeGen.create();
                case CTX_ABSOLUTE -> GraalHPyAbsoluteNodeGen.create();
                case CTX_LONG -> GraalHPyLongNodeGen.create();
                case CTX_FLOAT -> GraalHPyFloatNodeGen.create();
                case CTX_STR -> GraalHPyStrNodeGen.create();
                case CTX_REPR -> GraalHPyReprNodeGen.create();
                case CTX_ASCII -> GraalHPyASCIINodeGen.create();
                case CTX_BYTES -> GraalHPyBytesNodeGen.create();
                case CTX_HASH -> GraalHPyHashNodeGen.create();
                case CTX_LENGTH -> GraalHPyLengthNodeGen.create();
                case CTX_RICHCOMPARE -> GraalHPyRichcompareNodeGen.create();
                case CTX_RICHCOMPAREBOOL -> GraalHPyRichcompareBoolNodeGen.create();
                case CTX_INDEX -> GraalHPyAsIndexNodeGen.create();
                case CTX_NUMBER_CHECK -> GraalHPyIsNumberNodeGen.create();
                case CTX_TUPLE_FROMARRAY -> GraalHPyTupleFromArrayNodeGen.create();
                case CTX_TUPLEBUILDER_NEW, CTX_LISTBUILDER_NEW -> GraalHPyBuilderNewNodeGen.create();
                case CTX_TUPLEBUILDER_SET, CTX_LISTBUILDER_SET -> GraalHPyBuilderSetNodeGen.create();
                case CTX_TUPLEBUILDER_BUILD -> GraalHPyTupleBuilderBuildNodeGen.create();
                case CTX_LISTBUILDER_BUILD -> GraalHPyListBuilderBuildNodeGen.create();
                case CTX_TUPLEBUILDER_CANCEL, CTX_LISTBUILDER_CANCEL -> GraalHPyBuilderCancelNodeGen.create();
                case CTX_TRACKER_NEW -> GraalHPyTrackerNewNodeGen.create();
                case CTX_TRACKER_ADD -> GraalHPyTrackerAddNodeGen.create();
                case CTX_TRACKER_CLOSE -> GraalHPyTrackerCleanupNodeGen.create();
                case CTX_TRACKER_FORGETALL -> GraalHPyTrackerForgetAllNodeGen.create();
                case CTX_CALLABLE_CHECK -> GraalHPyIsCallableNodeGen.create();
                case CTX_SEQUENCE_CHECK -> GraalHPyIsSequenceNodeGen.create();
                case CTX_CALLTUPLEDICT -> GraalHPyCallTupleDictNodeGen.create();
                case CTX_TYPE -> GraalHPyTypeNodeGen.create();
                case CTX_TYPECHECK -> GraalHPyTypeCheckNodeGen.create();
                case CTX_ERR_NEWEXCEPTIONWITHDOC -> GraalHPyNewExceptionWithDocNodeGen.create();
                case CTX_ERR_NEWEXCEPTION -> GraalHPyNewExceptionNodeGen.create();
                case CTX_IS -> GraalHPyIsNodeGen.create();
                case CTX_IMPORT_IMPORTMODULE -> GraalHPyImportModuleNodeGen.create();
                case CTX_FIELD_STORE -> GraalHPyFieldStoreNodeGen.create();
                case CTX_FIELD_LOAD -> GraalHPyFieldLoadNodeGen.create();
                case CTX_GLOBAL_STORE -> GraalHPyGlobalStoreNodeGen.create();
                case CTX_GLOBAL_LOAD -> GraalHPyGlobalLoadNodeGen.create();
                case CTX_LEAVEPYTHONEXECUTION -> GraalHPyLeavePythonExecutionNodeGen.create();
                case CTX_REENTERPYTHONEXECUTION -> GraalHPyReenterPythonExecutionNodeGen.create();
                case CTX_CONTAINS -> GraalHPyContainsNodeGen.create();
                case CTX_TYPE_ISSUBTYPE -> GraalHPyTypeIsSubtypeNodeGen.create();
                case CTX_TYPE_GETNAME -> GraalHPyTypeGetNameNodeGen.create();
                case CTX_DICT_KEYS -> GraalHPyDictKeysNodeGen.create();
                case CTX_UNICODE_INTERNFROMSTRING -> GraalHPyUnicodeInternFromStringNodeGen.create();
                case CTX_CAPSULE_NEW -> GraalHPyCapsuleNewNodeGen.create();
                case CTX_CAPSULE_GET -> GraalHPyCapsuleGetNodeGen.create();
                case CTX_CAPSULE_SET -> GraalHPyCapsuleSetNodeGen.create();
                case CTX_CAPSULE_ISVALID -> GraalHPyCapsuleIsValidNodeGen.create();
                case CTX_SETTYPE -> GraalHPySetTypeNodeGen.create();
                case CTX_CONTEXTVAR_NEW -> GraalHPyContextVarNewNodeGen.create();
                case CTX_CONTEXTVAR_GET -> GraalHPyContextVarGetNodeGen.create();
                case CTX_CONTEXTVAR_SET -> GraalHPyContextVarSetNodeGen.create();
                case CTX_UNICODE_FROMENCODEDOBJECT -> GraalHPyUnicodeFromEncodedObjectNodeGen.create();
                case CTX_UNICODE_SUBSTRING -> GraalHPyUnicodeSubstringNodeGen.create();
                case CTX_SLICE_UNPACK -> GraalHPySliceUnpackNodeGen.create();
                case CTX_TYPE_CHECKSLOT -> GraalHPyTypeCheckSlotNodeGen.create();
                case CTX_SEQITER_NEW -> GraalHPySeqIterNewNodeGen.create();
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        static GraalHPyContextFunction getUncachedContextFunctionNode(HPyContextMember member) {
            return switch (member) {
                case CTX_DUP -> GraalHPyDupNodeGen.getUncached();
                case CTX_CLOSE -> GraalHPyCloseNodeGen.getUncached();
                case CTX_POSITIVE -> GraalHPyPositiveNodeGen.getUncached();
                case CTX_NEGATIVE -> GraalHPyNegativeNodeGen.getUncached();
                case CTX_INVERT -> GraalHPyInvertNodeGen.getUncached();
                case CTX_ADD -> GraalHPyAddNodeGen.getUncached();
                case CTX_SUBTRACT -> GraalHPySubtractNodeGen.getUncached();
                case CTX_MULTIPLY -> GraalHPyMultiplyNodeGen.getUncached();
                case CTX_MATRIXMULTIPLY -> GraalHPyMatrixMultiplyNodeGen.getUncached();
                case CTX_FLOORDIVIDE -> GraalHPyFloorDivideNodeGen.getUncached();
                case CTX_TRUEDIVIDE -> GraalHPyTrueDivideNodeGen.getUncached();
                case CTX_REMAINDER -> GraalHPyRemainderNodeGen.getUncached();
                case CTX_DIVMOD -> GraalHPyDivmodNodeGen.getUncached();
                case CTX_AND -> GraalHPyAndNodeGen.getUncached();
                case CTX_XOR -> GraalHPyXorNodeGen.getUncached();
                case CTX_OR -> GraalHPyOrNodeGen.getUncached();
                case CTX_LSHIFT -> GraalHPyLshiftNodeGen.getUncached();
                case CTX_RSHIFT -> GraalHPyRshiftNodeGen.getUncached();
                case CTX_POWER -> GraalHPyPowerNodeGen.getUncached();
                case CTX_INPLACEADD -> GraalHPyInPlaceAddNodeGen.getUncached();
                case CTX_INPLACESUBTRACT -> GraalHPyInPlaceSubtractNodeGen.getUncached();
                case CTX_INPLACEMULTIPLY -> GraalHPyInPlaceMultiplyNodeGen.getUncached();
                case CTX_INPLACEMATRIXMULTIPLY -> GraalHPyInPlaceMatrixMultiplyNodeGen.getUncached();
                case CTX_INPLACEFLOORDIVIDE -> GraalHPyInPlaceFloorDivideNodeGen.getUncached();
                case CTX_INPLACETRUEDIVIDE -> GraalHPyInPlaceTrueDivideNodeGen.getUncached();
                case CTX_INPLACEREMAINDER -> GraalHPyInPlaceRemainderNodeGen.getUncached();
                case CTX_INPLACEPOWER -> GraalHPyInPlacePowerNodeGen.getUncached();
                case CTX_INPLACELSHIFT -> GraalHPyInPlaceLshiftNodeGen.getUncached();
                case CTX_INPLACERSHIFT -> GraalHPyInPlaceRshiftNodeGen.getUncached();
                case CTX_INPLACEAND -> GraalHPyInPlaceAndNodeGen.getUncached();
                case CTX_INPLACEXOR -> GraalHPyInPlaceXorNodeGen.getUncached();
                case CTX_INPLACEOR -> GraalHPyInPlaceOrNodeGen.getUncached();
                case CTX_MODULE_CREATE -> GraalHPyModuleCreateNodeGen.getUncached();
                case CTX_BOOL_FROMLONG -> GraalHPyBoolFromLongNodeGen.getUncached();
                case CTX_LONG_FROMLONG, CTX_LONG_FROMLONGLONG, CTX_LONG_FROMSSIZE_T -> GraalHPyLongFromLongNodeGen.getUncached();
                case CTX_LONG_FROMUNSIGNEDLONG, CTX_LONG_FROMUNSIGNEDLONGLONG, CTX_LONG_FROMSIZE_T -> GraalHPyLongFromUnsignedLongNodeGen.getUncached();
                case CTX_LONG_ASLONG, CTX_LONG_ASLONGLONG -> GraalHPyLongAsLongNodeGen.getUncached();
                case CTX_LONG_ASUNSIGNEDLONG, CTX_LONG_ASUNSIGNEDLONGLONG, CTX_LONG_ASSIZE_T, CTX_LONG_ASVOIDPTR -> GraalHPyLongAsUnsignedLongNodeGen.getUncached();
                case CTX_LONG_ASUNSIGNEDLONGMASK, CTX_LONG_ASUNSIGNEDLONGLONGMASK -> GraalHPyLongAsUnsignedLongMaskNodeGen.getUncached();
                case CTX_LONG_ASSSIZE_T -> GraalHPyLongAsSsizeTNodeGen.getUncached();
                case CTX_LONG_ASDOUBLE -> GraalHPyLongAsDoubleNodeGen.getUncached();
                case CTX_DICT_NEW -> GraalHPyDictNewNodeGen.getUncached();
                case CTX_DICT_GETITEM -> GraalHPyDictGetItemNodeGen.getUncached();
                case CTX_LIST_NEW -> GraalHPyListNewNodeGen.getUncached();
                case CTX_LIST_APPEND -> GraalHPyListAppendNodeGen.getUncached();
                case CTX_FLOAT_FROMDOUBLE -> GraalHPyFloatFromDoubleNodeGen.getUncached();
                case CTX_FLOAT_ASDOUBLE -> GraalHPyFloatAsDoubleNodeGen.getUncached();
                case CTX_DICT_CHECK -> GraalHPyDictCheckNodeGen.getUncached();
                case CTX_BYTES_CHECK -> GraalHPyBytesCheckNodeGen.getUncached();
                case CTX_UNICODE_CHECK -> GraalHPyUnicodeCheckNodeGen.getUncached();
                case CTX_TUPLE_CHECK -> GraalHPyTupleCheckNodeGen.getUncached();
                case CTX_LIST_CHECK -> GraalHPyListCheckNodeGen.getUncached();
                case CTX_ERR_NOMEMORY -> GraalHPyErrNoMemoryNodeGen.getUncached();
                case CTX_ERR_SETOBJECT -> GraalHPyErrSetObjectNodeGen.getUncached();
                case CTX_ERR_SETSTRING -> GraalHPyErrSetStringNodeGen.getUncached();
                case CTX_ERR_SETFROMERRNOWITHFILENAME -> GraalHPyErrSetFromErrnoWithFilenameNodeGen.getUncached();
                case CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS -> GraalHPyErrSetFromErrnoWithFilenameObjectsNodeGen.getUncached();
                case CTX_FATALERROR -> GraalHPyFatalErrorNodeGen.getUncached();
                case CTX_ERR_OCCURRED -> GraalHPyErrOccurredNodeGen.getUncached();
                case CTX_ERR_EXCEPTIONMATCHES -> GraalHPyErrExceptionMatchesNodeGen.getUncached();
                case CTX_ERR_CLEAR -> GraalHPyErrClearNodeGen.getUncached();
                case CTX_ERR_WARNEX -> GraalHPyErrWarnExNodeGen.getUncached();
                case CTX_ERR_WRITEUNRAISABLE -> GraalHPyErrWriteUnraisableNodeGen.getUncached();
                case CTX_UNICODE_ASUTF8STRING -> GraalHPyUnicodeAsUTF8StringNodeGen.getUncached();
                case CTX_UNICODE_ASLATIN1STRING -> GraalHPyUnicodeAsLatin1StringNodeGen.getUncached();
                case CTX_UNICODE_ASASCIISTRING -> GraalHPyUnicodeAsASCIIStringNodeGen.getUncached();
                case CTX_UNICODE_ENCODEFSDEFAULT -> GraalHPyUnicodeEncodeFSDefaultNodeGen.getUncached();
                case CTX_UNICODE_ASUTF8ANDSIZE -> GraalHPyUnicodeAsUTF8AndSizeNodeGen.getUncached();
                case CTX_UNICODE_FROMSTRING -> GraalHPyUnicodeFromStringNodeGen.getUncached();
                case CTX_UNICODE_FROMWIDECHAR -> GraalHPyUnicodeFromWcharNodeGen.getUncached();
                case CTX_UNICODE_DECODEFSDEFAULT -> GraalHPyUnicodeDecodeCharsetNodeGen.getUncached();
                case CTX_UNICODE_DECODEFSDEFAULTANDSIZE -> GraalHPyUnicodeDecodeCharsetAndSizeNodeGen.getUncached();
                case CTX_UNICODE_DECODEASCII -> GraalHPyUnicodeDecodeASCIINodeGen.getUncached();
                case CTX_UNICODE_DECODELATIN1 -> GraalHPyUnicodeDecodeLatin1NodeGen.getUncached();
                case CTX_UNICODE_READCHAR -> GraalHPyUnicodeReadCharNodeGen.getUncached();
                case CTX_ASPYOBJECT -> GraalHPyAsPyObjectNodeGen.getUncached();
                case CTX_BYTES_ASSTRING, CTX_BYTES_AS_STRING -> GraalHPyBytesAsStringNodeGen.getUncached();
                case CTX_BYTES_SIZE, CTX_BYTES_GET_SIZE -> GraalHPyBytesGetSizeNodeGen.getUncached();
                case CTX_BYTES_FROMSTRING -> GraalHPyBytesFromStringNodeGen.getUncached();
                case CTX_BYTES_FROMSTRINGANDSIZE -> GraalHPyBytesFromStringAndSizeNodeGen.getUncached();
                case CTX_ISTRUE -> GraalHPyIsTrueNodeGen.getUncached();
                case CTX_GETATTR -> GraalHPyGetAttrNodeGen.getUncached();
                case CTX_GETATTR_S -> GraalHPyGetAttrSNodeGen.getUncached();
                case CTX_MAYBEGETATTR_S -> GraalHPyMaybeGetAttrSNodeGen.getUncached();
                case CTX_TYPE_FROMSPEC -> GraalHPyTypeFromSpecNodeGen.getUncached();
                case CTX_HASATTR -> GraalHPyHasAttrNodeGen.getUncached();
                case CTX_HASATTR_S -> GraalHPyHasAttrSNodeGen.getUncached();
                case CTX_SETATTR -> GraalHPySetAttrNodeGen.getUncached();
                case CTX_SETATTR_S -> GraalHPySetAttrSNodeGen.getUncached();
                case CTX_GETITEM, CTX_GETITEM_I -> GraalHPyGetItemNodeGen.getUncached();
                case CTX_GETITEM_S -> GraalHPyGetItemSNodeGen.getUncached();
                case CTX_SETITEM, CTX_SETITEM_I -> GraalHPySetItemNodeGen.getUncached();
                case CTX_SETITEM_S -> GraalHPySetItemSNodeGen.getUncached();
                case CTX_FROMPYOBJECT -> GraalHPyFromPyObjectNodeGen.getUncached();
                case CTX_NEW -> GraalHPyNewNodeGen.getUncached();
                case CTX_ASSTRUCT, CTX_ASSTRUCTLEGACY -> GraalHPyCastNodeGen.getUncached();
                case CTX_TYPE_GENERICNEW -> GraalHPyTypeGenericNewNodeGen.getUncached();
                case CTX_ABSOLUTE -> GraalHPyAbsoluteNodeGen.getUncached();
                case CTX_LONG -> GraalHPyLongNodeGen.getUncached();
                case CTX_FLOAT -> GraalHPyFloatNodeGen.getUncached();
                case CTX_STR -> GraalHPyStrNodeGen.getUncached();
                case CTX_REPR -> GraalHPyReprNodeGen.getUncached();
                case CTX_ASCII -> GraalHPyASCIINodeGen.getUncached();
                case CTX_BYTES -> GraalHPyBytesNodeGen.getUncached();
                case CTX_HASH -> GraalHPyHashNodeGen.getUncached();
                case CTX_LENGTH -> GraalHPyLengthNodeGen.getUncached();
                case CTX_RICHCOMPARE -> GraalHPyRichcompareNodeGen.getUncached();
                case CTX_RICHCOMPAREBOOL -> GraalHPyRichcompareBoolNodeGen.getUncached();
                case CTX_INDEX -> GraalHPyAsIndexNodeGen.getUncached();
                case CTX_NUMBER_CHECK -> GraalHPyIsNumberNodeGen.getUncached();
                case CTX_TUPLE_FROMARRAY -> GraalHPyTupleFromArrayNodeGen.getUncached();
                case CTX_TUPLEBUILDER_NEW, CTX_LISTBUILDER_NEW -> GraalHPyBuilderNewNodeGen.getUncached();
                case CTX_TUPLEBUILDER_SET, CTX_LISTBUILDER_SET -> GraalHPyBuilderSetNodeGen.getUncached();
                case CTX_TUPLEBUILDER_BUILD -> GraalHPyTupleBuilderBuildNodeGen.getUncached();
                case CTX_LISTBUILDER_BUILD -> GraalHPyListBuilderBuildNodeGen.getUncached();
                case CTX_TUPLEBUILDER_CANCEL, CTX_LISTBUILDER_CANCEL -> GraalHPyBuilderCancelNodeGen.getUncached();
                case CTX_TRACKER_NEW -> GraalHPyTrackerNewNodeGen.getUncached();
                case CTX_TRACKER_ADD -> GraalHPyTrackerAddNodeGen.getUncached();
                case CTX_TRACKER_CLOSE -> GraalHPyTrackerCleanupNodeGen.getUncached();
                case CTX_TRACKER_FORGETALL -> GraalHPyTrackerForgetAllNodeGen.getUncached();
                case CTX_CALLABLE_CHECK -> GraalHPyIsCallableNodeGen.getUncached();
                case CTX_SEQUENCE_CHECK -> GraalHPyIsSequenceNodeGen.getUncached();
                case CTX_CALLTUPLEDICT -> GraalHPyCallTupleDictNodeGen.getUncached();
                case CTX_TYPE -> GraalHPyTypeNodeGen.getUncached();
                case CTX_TYPECHECK -> GraalHPyTypeCheckNodeGen.getUncached();
                case CTX_ERR_NEWEXCEPTIONWITHDOC -> GraalHPyNewExceptionWithDocNodeGen.getUncached();
                case CTX_ERR_NEWEXCEPTION -> GraalHPyNewExceptionNodeGen.getUncached();
                case CTX_IS -> GraalHPyIsNodeGen.getUncached();
                case CTX_IMPORT_IMPORTMODULE -> GraalHPyImportModuleNodeGen.getUncached();
                case CTX_FIELD_STORE -> GraalHPyFieldStoreNodeGen.getUncached();
                case CTX_FIELD_LOAD -> GraalHPyFieldLoadNodeGen.getUncached();
                case CTX_GLOBAL_STORE -> GraalHPyGlobalStoreNodeGen.getUncached();
                case CTX_GLOBAL_LOAD -> GraalHPyGlobalLoadNodeGen.getUncached();
                case CTX_LEAVEPYTHONEXECUTION -> GraalHPyLeavePythonExecutionNodeGen.getUncached();
                case CTX_REENTERPYTHONEXECUTION -> GraalHPyReenterPythonExecutionNodeGen.getUncached();
                case CTX_CONTAINS -> GraalHPyContainsNodeGen.getUncached();
                case CTX_TYPE_ISSUBTYPE -> GraalHPyTypeIsSubtypeNodeGen.getUncached();
                case CTX_TYPE_GETNAME -> GraalHPyTypeGetNameNodeGen.getUncached();
                case CTX_DICT_KEYS -> GraalHPyDictKeysNodeGen.getUncached();
                case CTX_UNICODE_INTERNFROMSTRING -> GraalHPyUnicodeInternFromStringNodeGen.getUncached();
                case CTX_CAPSULE_NEW -> GraalHPyCapsuleNewNodeGen.getUncached();
                case CTX_CAPSULE_GET -> GraalHPyCapsuleGetNodeGen.getUncached();
                case CTX_CAPSULE_SET -> GraalHPyCapsuleSetNodeGen.getUncached();
                case CTX_CAPSULE_ISVALID -> GraalHPyCapsuleIsValidNodeGen.getUncached();
                case CTX_SETTYPE -> GraalHPySetTypeNodeGen.getUncached();
                case CTX_CONTEXTVAR_NEW -> GraalHPyContextVarNewNodeGen.getUncached();
                case CTX_CONTEXTVAR_GET -> GraalHPyContextVarGetNodeGen.getUncached();
                case CTX_CONTEXTVAR_SET -> GraalHPyContextVarSetNodeGen.getUncached();
                case CTX_UNICODE_FROMENCODEDOBJECT -> GraalHPyUnicodeFromEncodedObjectNodeGen.getUncached();
                case CTX_UNICODE_SUBSTRING -> GraalHPyUnicodeSubstringNodeGen.getUncached();
                case CTX_SLICE_UNPACK -> GraalHPySliceUnpackNodeGen.getUncached();
                case CTX_TYPE_CHECKSLOT -> GraalHPyTypeCheckSlotNodeGen.getUncached();
                case CTX_SEQITER_NEW -> GraalHPySeqIterNewNodeGen.getUncached();
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        // {{end llvm ctx func factory}}
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
                        @Cached HPyExecuteContextFunction call) throws ArityException {
            return call.execute(member, arguments);
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