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
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.PrintStream;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextMember;
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
        args[0] = context;
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
                return getErrorValue(cachedMember.getSignature().returnType());
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