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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LIST;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_TUPLE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_UNICODE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE_DELEGATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.PrintWriter;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsuleNameMatchesNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ReadUnicodeArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyArithmeticNode.HPyBinaryArithmeticNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyArithmeticNode.HPyInplaceArithmeticNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyArithmeticNode.HPyTernaryArithmeticNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyArithmeticNode.HPyUnaryArithmeticNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyASCIINodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAbsoluteNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAddNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAndNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsIndexNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyAsPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBoolFromBoolNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderCancelNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBuilderSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesAsStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesFromStringAndSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesFromStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesGetSizeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyBytesNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCallMethodNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCallTupleDictNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleIsValidNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCapsuleSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCastNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyCompileNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContainsNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarGetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyContextVarSetNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDelItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDelItemSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictCopyNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictKeysNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDictNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDivmodNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyDumpNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyEvalCodeNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyIsTrueNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLeavePythonExecutionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLengthNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListAppendNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListBuilderBuildNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListCheckNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyListNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsSsizeTNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUInt32MaskNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUInt64MaskNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongAsUInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromUInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongFromUInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyLshiftNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyMatrixMultiplyNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetAttrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetAttrSNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetCallFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPySetItemSNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeFromSpecNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGenericNewNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyTypeGetBuiltinShapeNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeReadCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyUnicodeSubstringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctionsFactory.GraalHPyXorNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCallHelperFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseAndGetHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCreateTypeFromSpecNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldLoadNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldStoreNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyPackKeywordArgsNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyReadCallFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTypeGetNameNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.RecursiveExceptionMatches;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfoLong;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.StrGetItemNodeWithSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictKeys;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.lib.PyUnicodeReadCharNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public abstract class GraalHPyContextFunctions {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface HPyContextFunctions {
        HPyContextFunction[] value();
    }

    /**
     * Context function implementations are marked with this annotation. It is used to annotate a
     * node with the name of the implemented context function. This information is further consumed
     * to automatically generate the appropriate upcall path.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(value = HPyContextFunctions.class)
    public @interface HPyContextFunction {

        /**
         * Name of this builtin - the name can be omitted, which will use the name of the class that
         * this annotation is applied to.
         */
        String value() default "";
    }

    public abstract static class GraalHPyContextFunction extends Node {

        public abstract Object execute(Object[] arguments);

        // {{start ctx func factory}}
        // @formatter:off
        // Checkstyle: stop
        // DO NOT EDIT THIS PART!
        // This part is automatically generated by hpy.tools.autogen.graalpy.autogen_ctx_function_factory
        @NeverDefault
        public static GraalHPyContextFunction create(HPyContextMember member) {
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
                case CTX_BOOL_FROMBOOL -> GraalHPyBoolFromBoolNodeGen.create();
                case CTX_LONG_FROMINT32_T -> GraalHPyLongFromInt32NodeGen.create();
                case CTX_LONG_FROMUINT32_T -> GraalHPyLongFromUInt32NodeGen.create();
                case CTX_LONG_FROMINT64_T, CTX_LONG_FROMSSIZE_T -> GraalHPyLongFromInt64NodeGen.create();
                case CTX_LONG_FROMUINT64_T, CTX_LONG_FROMSIZE_T -> GraalHPyLongFromUInt64NodeGen.create();
                case CTX_LONG_ASINT32_T -> GraalHPyLongAsInt32NodeGen.create();
                case CTX_LONG_ASUINT32_T -> GraalHPyLongAsUInt32NodeGen.create();
                case CTX_LONG_ASUINT32_TMASK -> GraalHPyLongAsUInt32MaskNodeGen.create();
                case CTX_LONG_ASINT64_T -> GraalHPyLongAsInt64NodeGen.create();
                case CTX_LONG_ASUINT64_T, CTX_LONG_ASSIZE_T, CTX_LONG_ASVOIDPTR -> GraalHPyLongAsUInt64NodeGen.create();
                case CTX_LONG_ASUINT64_TMASK -> GraalHPyLongAsUInt64MaskNodeGen.create();
                case CTX_LONG_ASSSIZE_T -> GraalHPyLongAsSsizeTNodeGen.create();
                case CTX_LONG_ASDOUBLE -> GraalHPyLongAsDoubleNodeGen.create();
                case CTX_DICT_NEW -> GraalHPyDictNewNodeGen.create();
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
                case CTX_TYPE_FROMSPEC -> GraalHPyTypeFromSpecNodeGen.create();
                case CTX_HASATTR -> GraalHPyHasAttrNodeGen.create();
                case CTX_HASATTR_S -> GraalHPyHasAttrSNodeGen.create();
                case CTX_SETATTR -> GraalHPySetAttrNodeGen.create();
                case CTX_SETATTR_S -> GraalHPySetAttrSNodeGen.create();
                case CTX_GETITEM, CTX_GETITEM_I -> GraalHPyGetItemNodeGen.create();
                case CTX_GETITEM_S -> GraalHPyGetItemSNodeGen.create();
                case CTX_SETITEM, CTX_SETITEM_I -> GraalHPySetItemNodeGen.create();
                case CTX_SETITEM_S -> GraalHPySetItemSNodeGen.create();
                case CTX_DELITEM, CTX_DELITEM_I -> GraalHPyDelItemNodeGen.create();
                case CTX_DELITEM_S -> GraalHPyDelItemSNodeGen.create();
                case CTX_FROMPYOBJECT -> GraalHPyFromPyObjectNodeGen.create();
                case CTX_NEW -> GraalHPyNewNodeGen.create();
                case CTX_ASSTRUCT_OBJECT, CTX_ASSTRUCT_LEGACY, CTX_ASSTRUCT_TYPE, CTX_ASSTRUCT_LONG, CTX_ASSTRUCT_FLOAT, CTX_ASSTRUCT_UNICODE, CTX_ASSTRUCT_TUPLE, CTX_ASSTRUCT_LIST -> GraalHPyCastNodeGen.create();
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
                case CTX_CALLTUPLEDICT -> GraalHPyCallTupleDictNodeGen.create();
                case CTX_CALL -> GraalHPyCallNodeGen.create();
                case CTX_CALLMETHOD -> GraalHPyCallMethodNodeGen.create();
                case CTX_DUMP -> GraalHPyDumpNodeGen.create();
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
                case CTX_DICT_COPY -> GraalHPyDictCopyNodeGen.create();
                case CTX_CAPSULE_NEW -> GraalHPyCapsuleNewNodeGen.create();
                case CTX_CAPSULE_GET -> GraalHPyCapsuleGetNodeGen.create();
                case CTX_CAPSULE_SET -> GraalHPyCapsuleSetNodeGen.create();
                case CTX_CAPSULE_ISVALID -> GraalHPyCapsuleIsValidNodeGen.create();
                case CTX_CONTEXTVAR_NEW -> GraalHPyContextVarNewNodeGen.create();
                case CTX_CONTEXTVAR_GET -> GraalHPyContextVarGetNodeGen.create();
                case CTX_CONTEXTVAR_SET -> GraalHPyContextVarSetNodeGen.create();
                case CTX_UNICODE_FROMENCODEDOBJECT -> GraalHPyUnicodeFromEncodedObjectNodeGen.create();
                case CTX_UNICODE_SUBSTRING -> GraalHPyUnicodeSubstringNodeGen.create();
                case CTX_SLICE_UNPACK -> GraalHPySliceUnpackNodeGen.create();
                case CTX_TYPE_GETBUILTINSHAPE -> GraalHPyTypeGetBuiltinShapeNodeGen.create();
                case CTX_COMPILE_S -> GraalHPyCompileNodeGen.create();
                case CTX_EVALCODE -> GraalHPyEvalCodeNodeGen.create();
                case CTX_SETCALLFUNCTION -> GraalHPySetCallFunctionNodeGen.create();
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        public static GraalHPyContextFunction getUncached(HPyContextMember member) {
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
                case CTX_BOOL_FROMBOOL -> GraalHPyBoolFromBoolNodeGen.getUncached();
                case CTX_LONG_FROMINT32_T -> GraalHPyLongFromInt32NodeGen.getUncached();
                case CTX_LONG_FROMUINT32_T -> GraalHPyLongFromUInt32NodeGen.getUncached();
                case CTX_LONG_FROMINT64_T, CTX_LONG_FROMSSIZE_T -> GraalHPyLongFromInt64NodeGen.getUncached();
                case CTX_LONG_FROMUINT64_T, CTX_LONG_FROMSIZE_T -> GraalHPyLongFromUInt64NodeGen.getUncached();
                case CTX_LONG_ASINT32_T -> GraalHPyLongAsInt32NodeGen.getUncached();
                case CTX_LONG_ASUINT32_T -> GraalHPyLongAsUInt32NodeGen.getUncached();
                case CTX_LONG_ASUINT32_TMASK -> GraalHPyLongAsUInt32MaskNodeGen.getUncached();
                case CTX_LONG_ASINT64_T -> GraalHPyLongAsInt64NodeGen.getUncached();
                case CTX_LONG_ASUINT64_T, CTX_LONG_ASSIZE_T, CTX_LONG_ASVOIDPTR -> GraalHPyLongAsUInt64NodeGen.getUncached();
                case CTX_LONG_ASUINT64_TMASK -> GraalHPyLongAsUInt64MaskNodeGen.getUncached();
                case CTX_LONG_ASSSIZE_T -> GraalHPyLongAsSsizeTNodeGen.getUncached();
                case CTX_LONG_ASDOUBLE -> GraalHPyLongAsDoubleNodeGen.getUncached();
                case CTX_DICT_NEW -> GraalHPyDictNewNodeGen.getUncached();
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
                case CTX_TYPE_FROMSPEC -> GraalHPyTypeFromSpecNodeGen.getUncached();
                case CTX_HASATTR -> GraalHPyHasAttrNodeGen.getUncached();
                case CTX_HASATTR_S -> GraalHPyHasAttrSNodeGen.getUncached();
                case CTX_SETATTR -> GraalHPySetAttrNodeGen.getUncached();
                case CTX_SETATTR_S -> GraalHPySetAttrSNodeGen.getUncached();
                case CTX_GETITEM, CTX_GETITEM_I -> GraalHPyGetItemNodeGen.getUncached();
                case CTX_GETITEM_S -> GraalHPyGetItemSNodeGen.getUncached();
                case CTX_SETITEM, CTX_SETITEM_I -> GraalHPySetItemNodeGen.getUncached();
                case CTX_SETITEM_S -> GraalHPySetItemSNodeGen.getUncached();
                case CTX_DELITEM, CTX_DELITEM_I -> GraalHPyDelItemNodeGen.getUncached();
                case CTX_DELITEM_S -> GraalHPyDelItemSNodeGen.getUncached();
                case CTX_FROMPYOBJECT -> GraalHPyFromPyObjectNodeGen.getUncached();
                case CTX_NEW -> GraalHPyNewNodeGen.getUncached();
                case CTX_ASSTRUCT_OBJECT, CTX_ASSTRUCT_LEGACY, CTX_ASSTRUCT_TYPE, CTX_ASSTRUCT_LONG, CTX_ASSTRUCT_FLOAT, CTX_ASSTRUCT_UNICODE, CTX_ASSTRUCT_TUPLE, CTX_ASSTRUCT_LIST -> GraalHPyCastNodeGen.getUncached();
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
                case CTX_CALLTUPLEDICT -> GraalHPyCallTupleDictNodeGen.getUncached();
                case CTX_CALL -> GraalHPyCallNodeGen.getUncached();
                case CTX_CALLMETHOD -> GraalHPyCallMethodNodeGen.getUncached();
                case CTX_DUMP -> GraalHPyDumpNodeGen.getUncached();
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
                case CTX_DICT_COPY -> GraalHPyDictCopyNodeGen.getUncached();
                case CTX_CAPSULE_NEW -> GraalHPyCapsuleNewNodeGen.getUncached();
                case CTX_CAPSULE_GET -> GraalHPyCapsuleGetNodeGen.getUncached();
                case CTX_CAPSULE_SET -> GraalHPyCapsuleSetNodeGen.getUncached();
                case CTX_CAPSULE_ISVALID -> GraalHPyCapsuleIsValidNodeGen.getUncached();
                case CTX_CONTEXTVAR_NEW -> GraalHPyContextVarNewNodeGen.getUncached();
                case CTX_CONTEXTVAR_GET -> GraalHPyContextVarGetNodeGen.getUncached();
                case CTX_CONTEXTVAR_SET -> GraalHPyContextVarSetNodeGen.getUncached();
                case CTX_UNICODE_FROMENCODEDOBJECT -> GraalHPyUnicodeFromEncodedObjectNodeGen.getUncached();
                case CTX_UNICODE_SUBSTRING -> GraalHPyUnicodeSubstringNodeGen.getUncached();
                case CTX_SLICE_UNPACK -> GraalHPySliceUnpackNodeGen.getUncached();
                case CTX_TYPE_GETBUILTINSHAPE -> GraalHPyTypeGetBuiltinShapeNodeGen.getUncached();
                case CTX_COMPILE_S -> GraalHPyCompileNodeGen.getUncached();
                case CTX_EVALCODE -> GraalHPyEvalCodeNodeGen.getUncached();
                case CTX_SETCALLFUNCTION -> GraalHPySetCallFunctionNodeGen.getUncached();
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        // @formatter:on
        // Checkstyle: resume
        // {{end ctx func factory}}
    }

    public abstract static class HPyUnaryContextFunction extends GraalHPyContextFunction {
        public abstract Object execute(Object arg0);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0]);
        }
    }

    public abstract static class HPyBinaryContextFunction extends GraalHPyContextFunction {
        public abstract Object execute(Object arg0, Object arg1);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1]);
        }
    }

    public abstract static class HPyTernaryContextFunction extends GraalHPyContextFunction {
        public abstract Object execute(Object arg0, Object arg1, Object arg2);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2]);
        }
    }

    public abstract static class HPyQuaternaryContextFunction extends GraalHPyContextFunction {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3]);
        }
    }

    public abstract static class HPy5ContextFunction extends GraalHPyContextFunction {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4]);
        }
    }

    @HPyContextFunction("ctx_Dup")
    @GenerateUncached
    public abstract static class GraalHPyDup extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object) {
            return object;
        }
    }

    @HPyContextFunction("ctx_Close")
    @GenerateUncached
    public abstract static class GraalHPyClose extends HPyBinaryContextFunction {
        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object handle,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            closeHandleNode.execute(handle);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Positive")
    @GenerateUncached
    @ImportStatic(UnaryArithmetic.class)
    public abstract static class GraalHPyPositive extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg,
                        @Cached(parameters = "Pos") HPyUnaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg);
        }
    }

    @HPyContextFunction("ctx_Negative")
    @GenerateUncached
    @ImportStatic(UnaryArithmetic.class)
    public abstract static class GraalHPyNegative extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg,
                        @Cached(parameters = "Neg") HPyUnaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg);
        }
    }

    @HPyContextFunction("ctx_Invert")
    @GenerateUncached
    @ImportStatic(UnaryArithmetic.class)
    public abstract static class GraalHPyInvert extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg,
                        @Cached(parameters = "Invert") HPyUnaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg);
        }
    }

    @HPyContextFunction("ctx_Add")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyAdd extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Add") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Subtract")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPySubtract extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Sub") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Multiply")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyMultiply extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Mul") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_MatrixMultiply")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyMatrixMultiply extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "MatMul") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_FloorDivide")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyFloorDivide extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "FloorDiv") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_TrueDivide")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyTrueDivide extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "TrueDiv") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Remainder")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyRemainder extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Mod") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Divmod")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyDivmod extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "DivMod") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_And")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyAnd extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "And") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Xor")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyXor extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Xor") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Or")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyOr extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "Or") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Lshift")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyLshift extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "LShift") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Rshift")
    @GenerateUncached
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class GraalHPyRshift extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "RShift") HPyBinaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Power")
    @GenerateUncached
    @ImportStatic(TernaryArithmetic.class)
    public abstract static class GraalHPyPower extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1, Object arg2,
                        @Cached(parameters = "Pow") HPyTernaryArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1, arg2);
        }
    }

    @HPyContextFunction("ctx_InPlaceAdd")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceAdd extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IAdd") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceSubtract")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceSubtract extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "ISub") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceMultiply")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceMultiply extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IMul") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceMatrixMultiply")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceMatrixMultiply extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IMatMul") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceFloorDivide")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceFloorDivide extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IFloorDiv") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceTrueDivide")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceTrueDivide extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "ITrueDiv") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceRemainder")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceRemainder extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IMod") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlacePower")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlacePower extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1, Object arg2,
                        @Cached(parameters = "IPow") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1, arg2);
        }
    }

    @HPyContextFunction("ctx_InPlaceLshift")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceLshift extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "ILShift") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceRshift")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceRshift extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IRShift") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceAnd")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceAnd extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IAnd") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceXor")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceXor extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IXor") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_InPlaceOr")
    @GenerateUncached
    @ImportStatic(InplaceArithmetic.class)
    public abstract static class GraalHPyInPlaceOr extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg0, Object arg1,
                        @Cached(parameters = "IOr") HPyInplaceArithmeticNode arithmeticNode) {
            return arithmeticNode.execute(arg0, arg1);
        }
    }

    @HPyContextFunction("ctx_Bool_FromBool")
    @GenerateUncached
    public abstract static class GraalHPyBoolFromBool extends HPyBinaryContextFunction {

        @Specialization
        static PInt doBoolean(GraalHPyContext hpyContext, boolean value) {
            Python3Core core = hpyContext.getContext();
            return value ? core.getTrue() : core.getFalse();
        }

        @Specialization
        static PInt doByte(GraalHPyContext hpyContext, byte value) {
            return doBoolean(hpyContext, value != 0);
        }
    }

    @HPyContextFunction("ctx_Long_FromInt32_t")
    @GenerateUncached
    public abstract static class GraalHPyLongFromInt32 extends HPyBinaryContextFunction {

        @Specialization
        static Object doInt(@SuppressWarnings("unused") Object hpyContext, int value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLongFromLong fromLongNode) {
            return fromLongNode.execute(inliningTarget, value, true);
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") Object hpyContext, long value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLongFromLong fromLongNode) {
            return fromLongNode.execute(inliningTarget, value, true);
        }
    }

    @HPyContextFunction("ctx_Long_FromUInt32_t")
    @GenerateUncached
    public abstract static class GraalHPyLongFromUInt32 extends HPyBinaryContextFunction {

        @Specialization
        static Object doInt(@SuppressWarnings("unused") Object hpyContext, int value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyLongFromLong fromLongNode) {
            return fromLongNode.execute(inliningTarget, value, false);
        }

        @Specialization
        static Object doLong(Object hpyContext, long value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyLongFromLong fromLongNode) {
            return doInt(hpyContext, (int) value, inliningTarget, fromLongNode);
        }
    }

    @HPyContextFunction("ctx_Long_FromInt64_t")
    @HPyContextFunction("ctx_Long_FromSsize_t")
    @GenerateUncached
    public abstract static class GraalHPyLongFromInt64 extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, long value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLongFromLong fromLongNode) {
            return fromLongNode.execute(inliningTarget, value, true);
        }
    }

    @HPyContextFunction("ctx_Long_FromUInt64_t")
    @HPyContextFunction("ctx_Long_FromSize_t")
    @GenerateUncached
    public abstract static class GraalHPyLongFromUInt64 extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, long value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLongFromLong fromLongNode) {
            return fromLongNode.execute(inliningTarget, value, false);
        }
    }

    private static final int SIZEOF_INT32 = 4;
    private static final int SIZEOF_INT64 = 8;
    private static final int SIZEOF_INTPTR = 8;

    @HPyContextFunction("ctx_Long_AsInt32_t")
    @GenerateUncached
    public abstract static class GraalHPyLongAsInt32 extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(object, 1, SIZEOF_INT32, true);
        }
    }

    @HPyContextFunction("ctx_Long_AsUInt32_t")
    @GenerateUncached
    public abstract static class GraalHPyLongAsUInt32 extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            if (!isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            return asNativePrimitiveNode.execute(object, 0, SIZEOF_INT32, true);
        }
    }

    @HPyContextFunction("ctx_Long_AsUInt32_tMask")
    @GenerateUncached
    public abstract static class GraalHPyLongAsUInt32Mask extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(object, 0, SIZEOF_INT32, false);
        }
    }

    @HPyContextFunction("ctx_Long_AsInt64_t")
    @GenerateUncached
    public abstract static class GraalHPyLongAsInt64 extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(object, 1, SIZEOF_INT64, true);
        }
    }

    @HPyContextFunction("ctx_Long_AsUInt64_t")
    @HPyContextFunction("ctx_Long_AsSize_t")
    @HPyContextFunction("ctx_Long_AsVoidPtr")
    @GenerateUncached
    public abstract static class GraalHPyLongAsUInt64 extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            if (!isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            return asNativePrimitiveNode.execute(object, 0, SIZEOF_INT64, true);
        }
    }

    @HPyContextFunction("ctx_Long_AsUInt64_tMask")
    @GenerateUncached
    public abstract static class GraalHPyLongAsUInt64Mask extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(object, 0, SIZEOF_INT64, false);
        }
    }

    @HPyContextFunction("ctx_Long_AsSsize_t")
    @GenerateUncached
    public abstract static class GraalHPyLongAsSsizeT extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unusued") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            if (!isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            return asNativePrimitiveNode.execute(object, 1, SIZEOF_INTPTR, true);
        }
    }

    @HPyContextFunction("ctx_Long_AsDouble")
    @GenerateUncached
    public abstract static class GraalHPyLongAsDouble extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsDoubleNode asDoubleNode) {
            return asDoubleNode.execute(inliningTarget, arg);
        }
    }

    @HPyContextFunction("ctx_Dict_New")
    @GenerateUncached
    public abstract static class GraalHPyDictNew extends HPyUnaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext,
                        @Cached PythonObjectFactory factory) {
            return factory.createDict();
        }
    }

    @HPyContextFunction("ctx_List_New")
    @GenerateUncached
    public abstract static class GraalHPyListNew extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, long len,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            try {
                Object[] data = new Object[PInt.intValueExact(len)];
                // TODO(fa) maybe this should be NO_VALUE (representing native 'NULL')
                Arrays.fill(data, PNone.NONE);
                return factory.createList(data);
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonBuiltinClassType.MemoryError);
            }
        }
    }

    @HPyContextFunction("ctx_List_Append")
    @GenerateUncached
    public abstract static class GraalHPyListAppend extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object left, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached ListNodes.AppendNode appendNode,
                        @Cached PRaiseNode raiseNode) {
            if (!PGuards.isList(left)) {
                throw raiseNode.raise(SystemError, ErrorMessages.BAD_INTERNAL_CALL);
            }
            appendNode.execute((PList) left, value);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Float_FromDouble")
    @GenerateUncached
    public abstract static class GraalHPyFloatFromDouble extends HPyBinaryContextFunction {

        @Specialization
        static double doGeneric(@SuppressWarnings("unused") Object hpyContext, double value) {
            return value;
        }
    }

    @HPyContextFunction("ctx_Float_AsDouble")
    @GenerateUncached
    public abstract static class GraalHPyFloatAsDouble extends HPyBinaryContextFunction {

        @Specialization
        static double doGeneric(@SuppressWarnings("unused") Object hpyContext, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            return asDoubleNode.execute(null, inliningTarget, value);
        }
    }

    abstract static class HPyCheckBuiltinType extends HPyBinaryContextFunction {

        abstract PythonBuiltinClassType getExpectedType();

    }

    @HPyContextFunction("ctx_Dict_Check")
    @GenerateUncached
    public abstract static class GraalHPyDictCheck extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PDict));
        }
    }

    @HPyContextFunction("ctx_Bytes_Check")
    @GenerateUncached
    public abstract static class GraalHPyBytesCheck extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PBytes));
        }
    }

    @HPyContextFunction("ctx_Unicode_Check")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeCheck extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PString));
        }
    }

    @HPyContextFunction("ctx_Tuple_Check")
    @GenerateUncached
    public abstract static class GraalHPyTupleCheck extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PTuple));
        }
    }

    @HPyContextFunction("ctx_List_Check")
    @GenerateUncached
    public abstract static class GraalHPyListCheck extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PList));
        }
    }

    @HPyContextFunction("ctx_Err_NoMemory")
    @GenerateUncached
    public abstract static class GraalHPyErrNoMemory extends HPyUnaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.MemoryError);
        }
    }

    @HPyContextFunction("ctx_Err_SetObject")
    @GenerateUncached
    public abstract static class GraalHPyErrSetObject extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object errTypeObj, Object valueObj,
                        @Bind("this") Node inliningTarget,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSubtypeNode isExcValueSubtypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @Cached PyExceptionInstanceCheckNode exceptionCheckNode,
                        @Cached PRaiseNode raiseNode) {
            if (!(PGuards.isPythonClass(errTypeObj) && isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException))) {
                return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
            }
            Object exception;
            // If the exception value is already an exception object, just take it.
            if (isExcValueSubtypeNode.execute(getClassNode.execute(inliningTarget, valueObj), PythonBuiltinClassType.PBaseException)) {
                exception = valueObj;
            } else {
                exception = callExceptionConstructorNode.execute(errTypeObj, valueObj);
            }

            if (exceptionCheckNode.execute(inliningTarget, exception)) {
                throw raiseNode.raiseExceptionObject(exception);
            }
            // This should really not happen since we did a type check above but in theory,
            // the constructor could be broken.
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_Err_SetString")
    @GenerateUncached
    public abstract static class GraalHPyErrSetString extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object errTypeObj, Object charPtr,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @Cached PyExceptionInstanceCheckNode exceptionCheckNode,
                        @Cached PRaiseNode raiseNode) {
            if (!(PGuards.isPythonClass(errTypeObj) && isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException))) {
                return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
            }
            Object exception = callExceptionConstructorNode.execute(errTypeObj, fromCharPointerNode.execute(charPtr));

            if (exceptionCheckNode.execute(isSubtypeNode, exception)) {
                throw raiseNode.raiseExceptionObject(exception);
            }
            // This should really not happen since we did a type check above but in theory,
            // the constructor could be broken.
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_Err_SetFromErrnoWithFilename")
    @GenerateUncached
    public abstract static class GraalHPyErrSetFromErrnoWithFilename extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object errTypeObj, Object errMessagePtr,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "hpyContext") HPyCallHelperFunctionNode callHelperFunctionNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached PyExceptionInstanceCheckNode exceptionCheckNode,
                        @Cached PRaiseNode raiseNode) {
            Object i = callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_ERRNO);
            Object message = fromCharPointerNode.execute(callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_STRERROR, i));
            if (!isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException)) {
                return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
            }
            Object exception = null;
            if (!lib.isNull(errMessagePtr)) {
                TruffleString filename_fsencoded = fromCharPointerNode.execute(errMessagePtr);
                exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filename_fsencoded);
            }

            if (exception == null) {
                exception = callExceptionConstructorNode.execute(errTypeObj, i, message);
            }

            if (exceptionCheckNode.execute(inliningTarget, exception)) {
                throw raiseNode.raiseExceptionObject(exception);
            }
            // This should really not happen since we did a type check above but in theory,
            // the constructor could be broken.
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_Err_SetFromErrnoWithFilenameObjects")
    @GenerateUncached
    public abstract static class GraalHPyErrSetFromErrnoWithFilenameObjects extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object errTypeObj, Object filenameObject1, Object filenameObject2,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "hpyContext") HPyCallHelperFunctionNode callHelperNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @Cached PyExceptionInstanceCheckNode exceptionCheckNode,
                        @Cached PRaiseNode raiseNode) {
            Object i = callHelperNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_ERRNO);
            Object message = fromCharPointerNode.execute(callHelperNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_STRERROR, i));
            if (!isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException)) {
                return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
            }
            Object exception = null;
            if (filenameObject1 != NULL_HANDLE_DELEGATE) {
                if (filenameObject2 != NULL_HANDLE_DELEGATE) {
                    exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filenameObject1, 0, filenameObject2);
                } else {
                    exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filenameObject1);
                }
            }

            if (exception == null) {
                exception = callExceptionConstructorNode.execute(errTypeObj, i, message);
            }

            if (exceptionCheckNode.execute(inliningTarget, exception)) {
                throw raiseNode.raiseExceptionObject(exception);
            }
            // This should really not happen since we did a type check above but in theory,
            // the constructor could be broken.
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_FatalError")
    @GenerateUncached
    public abstract static class GraalHPyFatalError extends HPyBinaryContextFunction {
        @TruffleBoundary
        @Specialization
        Object doGeneric(GraalHPyContext hpyContext, Object charPtr) {
            TruffleString errorMessage;
            if (InteropLibrary.getUncached(charPtr).isNull(charPtr)) {
                errorMessage = ErrorMessages.MSG_NOT_SET;
            } else {
                // we don't need to copy the bytes since we die anyway
                errorMessage = FromCharPointerNodeGen.getUncached().execute(charPtr, false);
            }
            CExtCommonNodes.fatalError(this, hpyContext.getContext(), null, errorMessage, -1);
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_Err_Occurred")
    @GenerateUncached
    public abstract static class GraalHPyErrOccurred extends HPyUnaryContextFunction {

        @Specialization
        static int doGeneric(GraalHPyContext hpyContext,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode) {
            return getThreadStateNode.getCurrentException(inliningTarget, hpyContext.getContext()) != null ? 1 : 0;
        }
    }

    @HPyContextFunction("ctx_Err_ExceptionMatches")
    @GenerateUncached
    public abstract static class GraalHPyErrExceptionMatches extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object exc,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached RecursiveExceptionMatches exceptionMatches) {
            PException err = getThreadStateNode.getCurrentException(inliningTarget, hpyContext.getContext());
            if (err == null) {
                return 0;
            }
            if (exc == NULL_HANDLE_DELEGATE) {
                return 0;
            }
            return exceptionMatches.execute(hpyContext, err.getUnreifiedException(), exc);
        }
    }

    @HPyContextFunction("ctx_Err_Clear")
    @GenerateUncached
    public abstract static class GraalHPyErrClear extends HPyUnaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode) {
            getThreadStateNode.setCurrentException(inliningTarget, hpyContext.getContext(), null);
            return NULL_HANDLE_DELEGATE;
        }
    }

    @HPyContextFunction("ctx_Err_WarnEx")
    @GenerateUncached
    public abstract static class GraalHPyErrWarnEx extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object categoryArg, Object messageArg, long stackLevel,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached WarnNode warnNode) {
            Object category = categoryArg == NULL_HANDLE_DELEGATE ? RuntimeWarning : categoryArg;
            TruffleString message = lib.isNull(messageArg) ? T_EMPTY_STRING : fromCharPointerNode.execute(messageArg);
            warnNode.warnEx(null, category, message, (int) stackLevel);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Err_WriteUnraisable")
    @GenerateUncached
    public abstract static class GraalHPyErrWriteUnraisable extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached WriteUnraisableNode writeUnraisableNode) {
            PException exception = getThreadStateNode.getCurrentException(inliningTarget, hpyContext.getContext());
            getThreadStateNode.setCurrentException(inliningTarget, hpyContext.getContext(), null);
            writeUnraisableNode.execute(null, exception.getUnreifiedException(), null, (object instanceof PNone) ? PNone.NONE : object);
            return 0; // void
        }
    }

    @HPyContextFunction("ctx_Unicode_AsUTF8String")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsUTF8String extends HPyBinaryContextFunction {

        @Specialization
        static PBytes doGeneric(@SuppressWarnings("unused") Object hpyContext, Object unicodeObject,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(encodeNativeStringNode.execute(StandardCharsets.UTF_8, unicodeObject, T_STRICT));
        }
    }

    @HPyContextFunction("ctx_Unicode_AsLatin1String")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsLatin1String extends HPyBinaryContextFunction {

        @Specialization
        static PBytes doGeneric(@SuppressWarnings("unused") Object hpyContext, Object unicodeObject,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(encodeNativeStringNode.execute(StandardCharsets.ISO_8859_1, unicodeObject, T_STRICT));
        }
    }

    @HPyContextFunction("ctx_Unicode_AsASCIIString")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsASCIIString extends HPyBinaryContextFunction {

        @Specialization
        static PBytes doGeneric(@SuppressWarnings("unused") Object hpyContext, Object unicodeObject,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(encodeNativeStringNode.execute(StandardCharsets.US_ASCII, unicodeObject, T_STRICT));
        }
    }

    @HPyContextFunction("ctx_Unicode_EncodeFSDefault")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeEncodeFSDefault extends HPyBinaryContextFunction {
        @Specialization
        static PBytes doGeneric(@SuppressWarnings("unused") Object hpyContext, Object unicodeObject,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(encodeNativeStringNode.execute(getFSDefaultCharset(), unicodeObject, T_STRICT));
        }

        @TruffleBoundary
        public static Charset getFSDefaultCharset() {
            TruffleString normalizedEncoding = CharsetMapping.normalizeUncached(GetFileSystemEncodingNode.getFileSystemEncoding());
            return CharsetMapping.getCharsetNormalized(normalizedEncoding);
        }
    }

    @HPyContextFunction("ctx_Unicode_AsUTF8AndSize")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsUTF8AndSize extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object unicodeObject, Object sizePtr,
                        @Cached CStructAccess.WriteLongNode writeNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib) {
            byte[] result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, unicodeObject, T_STRICT);
            if (!ptrLib.isNull(sizePtr)) {
                writeNode.write(sizePtr, result.length);
            }
            return new CByteArrayWrapper(result);
        }
    }

    @HPyContextFunction("ctx_Unicode_FromString")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromString extends HPyBinaryContextFunction {

        @Specialization
        static TruffleString doGeneric(@SuppressWarnings("unused") Object hpyContext, Object charPtr,
                        @Cached FromCharPointerNode fromCharPointerNode) {
            if (charPtr instanceof TruffleString ts) {
                return ts;
            }
            return fromCharPointerNode.execute(charPtr);
        }
    }

    @HPyContextFunction("ctx_Unicode_FromWideChar")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromWchar extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object wcharPtr, long len,
                        @Cached ReadUnicodeArrayNode readArray,
                        @Cached TruffleString.FromIntArrayUTF32Node fromArray) {
            try {
                return fromArray.execute(readArray.execute(wcharPtr, PInt.intValueExact(len), CStructs.wchar_t.size()));
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @HPyContextFunction("ctx_Unicode_DecodeFSDefault")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeCharset extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object charPtr,
                        @Cached(parameters = "hpyContext") HPyFromCharPointerNode fromCharPointerNode) {
            return fromCharPointerNode.execute(hpyContext, charPtr, getFSDefault());
        }

        @TruffleBoundary
        static Encoding getFSDefault() {
            String fileEncoding = System.getProperty("file.encoding");
            if (fileEncoding != null) {
                try {
                    return Encoding.valueOf(fileEncoding.replace('-', '_'));
                } catch (IllegalArgumentException e) {
                    // avoid any fatal Java exceptions; fall through
                }
            }
            // fall back to UTF-8
            return Encoding.UTF_8;
        }
    }

    @HPyContextFunction("ctx_Unicode_DecodeFSDefaultAndSize")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeCharsetAndSize extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object charPtr, long lsize,
                        @Cached(parameters = "hpyContext") HPyFromCharPointerNode fromCharPointerNode) {
            Encoding fsDefault = GraalHPyUnicodeDecodeCharset.getFSDefault();
            try {
                return fromCharPointerNode.execute(hpyContext, charPtr, PInt.intValueExact(lsize), fsDefault, true);
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @HPyContextFunction("ctx_Unicode_DecodeASCII")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeASCII extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object charPtr, long size, Object errorsPtr,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached(parameters = "hpyContext") HPyFromCharPointerNode fromCharPointerNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadI8ArrayNode readI8ArrayNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TruffleString.EqualNode equalNode) {
            CodingErrorAction errorAction;
            if (interopLib.isNull(errorsPtr)) {
                errorAction = CodingErrorAction.REPORT;
            } else {
                TruffleString errors = fromCharPointerNode.execute(hpyContext, errorsPtr, false);
                errorAction = CodecsModuleBuiltins.convertCodingErrorAction(errors, equalNode);
            }
            byte[] bytes = readI8ArrayNode.execute(hpyContext, charPtr, 0, size);
            String decoded = decode(StandardCharsets.US_ASCII, errorAction, bytes);
            if (decoded != null) {
                return fromJavaStringNode.execute(decoded, TS_ENCODING);
            }
            // TODO: refactor helper nodes for CodecsModuleBuiltins to use them here
            throw raiseNode.raise(PythonBuiltinClassType.UnicodeDecodeError, ErrorMessages.MALFORMED_INPUT);
        }

        @TruffleBoundary
        static String decode(Charset charset, CodingErrorAction errorAction, byte[] bytes) {
            try {
                return charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ex) {
                return null;
            }
        }
    }

    @HPyContextFunction("ctx_Unicode_DecodeLatin1")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeLatin1 extends HPyQuaternaryContextFunction {

        @Specialization
        Object doGeneric(GraalHPyContext hpyContext, Object charPtr, long lsize, @SuppressWarnings("unused") Object errorsPtr,
                        @Cached(parameters = "hpyContext") HPyFromCharPointerNode fromCharPointerNode) {
            if (PInt.isIntRange(lsize)) {
                /*
                 * If we have ISO-8859-1, we can just force the encoding and short-circuit the error
                 * reading etc since there cannot be an invalid byte
                 */
                return fromCharPointerNode.execute(hpyContext, charPtr, (int) lsize, Encoding.ISO_8859_1, true);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @HPyContextFunction("ctx_Unicode_ReadChar")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeReadChar extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object unicodeObject, long index,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeReadCharNode unicodeReadChar) {
            return unicodeReadChar.execute(inliningTarget, unicodeObject, index);
        }
    }

    @HPyContextFunction("ctx_AsPyObject")
    @GenerateUncached
    public abstract static class GraalHPyAsPyObject extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Cached PythonToNativeNewRefNode toPyObjectPointerNode) {
            return toPyObjectPointerNode.execute(object);
        }
    }

    @HPyContextFunction("ctx_Bytes_AsString")
    @HPyContextFunction("ctx_Bytes_AS_STRING")
    @GenerateUncached
    public abstract static class GraalHPyBytesAsString extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Cached PRaiseNode raiseNode) {
            if (object instanceof PBytes bytes) {
                return PySequenceArrayWrapper.ensureNativeSequence(bytes);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, object);
        }
    }

    @HPyContextFunction("ctx_Bytes_Size")
    @HPyContextFunction("ctx_Bytes_GET_SIZE")
    @GenerateUncached
    public abstract static class GraalHPyBytesGetSize extends HPyBinaryContextFunction {

        @Specialization
        static long doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached PRaiseNode raiseNode) {
            if (object instanceof PBytes) {
                return lenNode.execute(inliningTarget, (PSequence) object);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, object);
        }
    }

    @HPyContextFunction("ctx_Bytes_FromString")
    @GenerateUncached
    public abstract static class GraalHPyBytesFromString extends HPyBinaryContextFunction {

        @Specialization
        static PBytes doGeneric(GraalHPyContext hpyContext, Object charPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadI8ArrayNode readI8ArrayNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            int size;
            try {
                size = castToJavaIntNode.execute(inliningTarget, callHelperNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_STRLEN, charPtr));
            } catch (PException e) {
                throw raiseNode.raise(OverflowError, ErrorMessages.BYTE_STR_IS_TOO_LARGE);
            }
            byte[] bytes = readI8ArrayNode.execute(hpyContext, charPtr, 0, size);
            return factory.createBytes(bytes);
        }
    }

    @HPyContextFunction("ctx_Bytes_FromStringAndSize")
    @GenerateUncached
    public abstract static class GraalHPyBytesFromStringAndSize extends HPyTernaryContextFunction {

        @Specialization
        static PBytes doGeneric(GraalHPyContext hpyContext, Object charPtr, long lsize,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadI8ArrayNode readI8ArrayNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            if (interopLib.isNull(charPtr)) {
                throw raiseNode.raise(ValueError, ErrorMessages.NULL_CHAR_PASSED);
            }
            if (lsize < 0) {
                throw raiseNode.raise(SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
            if (lsize == 0) {
                return factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] bytes = readI8ArrayNode.execute(hpyContext, charPtr, 0, lsize);
            return factory.createBytes(bytes);
        }
    }

    @HPyContextFunction("ctx_IsTrue")
    @GenerateUncached
    public abstract static class GraalHPyIsTrue extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            return PInt.intValue(isTrueNode.execute(null, inliningTarget, object));
        }
    }

    @HPyContextFunction("ctx_GetAttr")
    @GenerateUncached
    public abstract static class GraalHPyGetAttr extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttributeNode) {
            return getAttributeNode.execute(inliningTarget, receiver, key);
        }
    }

    @HPyContextFunction("ctx_GetAttr_s")
    @GenerateUncached
    public abstract static class GraalHPyGetAttrS extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetAttr getAttributeNode) {
            return getAttributeNode.execute(inliningTarget, receiver, fromCharPointerNode.execute(charPtr));
        }
    }

    @HPyContextFunction("ctx_Type_FromSpec")
    @GenerateUncached
    public abstract static class GraalHPyTypeFromSpec extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object typeSpecPtr, Object typeSpecParamArrayPtr,
                        @Cached HPyCreateTypeFromSpecNode createTypeFromSpecNode) {
            Object newType = createTypeFromSpecNode.execute(hpyContext, typeSpecPtr, typeSpecParamArrayPtr);
            assert PGuards.isClassUncached(newType) : "Object created from type spec is not a type";
            return newType;
        }
    }

    @HPyContextFunction("ctx_HasAttr")
    @GenerateUncached
    public abstract static class GraalHPyHasAttr extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttributeNode) {
            try {
                Object attr = getAttributeNode.execute(inliningTarget, receiver, key);
                return PInt.intValue(attr != PNone.NO_VALUE);
            } catch (PException e) {
                return 0;
            }
        }
    }

    @HPyContextFunction("ctx_HasAttr_s")
    @GenerateUncached
    public abstract static class GraalHPyHasAttrS extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetAttr getAttributeNode) {
            try {
                Object attr = getAttributeNode.execute(inliningTarget, receiver, fromCharPointerNode.execute(charPtr));
                return PInt.intValue(attr != PNone.NO_VALUE);
            } catch (PException e) {
                return 0;
            }
        }
    }

    @HPyContextFunction("ctx_SetAttr")
    @GenerateUncached
    public abstract static class GraalHPySetAttr extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSetAttr setAttrNode) {
            if (value == NULL_HANDLE_DELEGATE) {
                setAttrNode.execute(inliningTarget, receiver, key, null);
            } else {
                setAttrNode.execute(inliningTarget, receiver, key, value);
            }
            return 0;
        }
    }

    @HPyContextFunction("ctx_SetAttr_s")
    @GenerateUncached
    public abstract static class GraalHPySetAttrS extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectSetAttr setAttrNode) {
            TruffleString key = fromCharPointerNode.execute(charPtr);
            if (value == NULL_HANDLE_DELEGATE) {
                setAttrNode.execute(inliningTarget, receiver, key, null);
            } else {
                setAttrNode.execute(inliningTarget, receiver, key, value);
            }
            return 0;
        }
    }

    @HPyContextFunction("ctx_GetItem")
    @HPyContextFunction("ctx_GetItem_i")
    @GenerateUncached
    public abstract static class GraalHPyGetItem extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetItem getItemNode) {
            return getItemNode.execute(null, inliningTarget, receiver, key);
        }
    }

    @HPyContextFunction("ctx_GetItem_s")
    @GenerateUncached
    public abstract static class GraalHPyGetItemS extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetItem getItemNode) {
            return getItemNode.execute(null, inliningTarget, receiver, fromCharPointerNode.execute(charPtr));
        }
    }

    @HPyContextFunction("ctx_SetItem")
    @HPyContextFunction("ctx_SetItem_i")
    @GenerateUncached
    public abstract static class GraalHPySetItem extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(null, inliningTarget, receiver, key, value);
            return 0;
        }
    }

    @HPyContextFunction("ctx_SetItem_s")
    @GenerateUncached
    public abstract static class GraalHPySetItemS extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(null, inliningTarget, receiver, fromCharPointerNode.execute(charPtr), value);
            return 0;
        }
    }

    @HPyContextFunction("ctx_DelItem")
    @HPyContextFunction("ctx_DelItem_i")
    @GenerateUncached
    public abstract static class GraalHPyDelItem extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectDelItem delItemNode) {
            delItemNode.execute(null, inliningTarget, receiver, key);
            return 0;
        }
    }

    @HPyContextFunction("ctx_DelItem_s")
    @GenerateUncached
    public abstract static class GraalHPyDelItemS extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object charPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectDelItem delItemNode) {
            delItemNode.execute(null, inliningTarget, receiver, fromCharPointerNode.execute(charPtr));
            return 0;
        }
    }

    @HPyContextFunction("ctx_FromPyObject")
    @GenerateUncached
    public abstract static class GraalHPyFromPyObject extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Cached NativeToPythonNode toJavaNode) {
            // IMPORTANT: this is not stealing the reference. The CPython implementation
            // actually increases the reference count by 1.
            return toJavaNode.execute(object);
        }
    }

    @HPyContextFunction("ctx_New")
    @GenerateUncached
    public abstract static class GraalHPyNew extends HPyTernaryContextFunction {
        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(GraalHPyNew.class);
        public static final String INVALID_BUILT_IN_SHAPE = "invalid built-in shape";

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object type, Object dataOutVar,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.AllocateNode allocateNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.WritePointerNode writePointerNode,
                        @Cached InlinedExactClassProfile classProfile) {

            Object profiledTypeObject = classProfile.profile(inliningTarget, type);

            // check if argument is actually a type
            if (!isTypeNode.execute(inliningTarget, profiledTypeObject)) {
                return raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.HPY_NEW_ARG_1_MUST_BE_A_TYPE);
            }

            Object dataPtr = null;
            Object destroyFunc = null;
            Object defaultCallFunc = null;

            if (profiledTypeObject instanceof PythonClass clazz) {
                // allocate native space
                long basicSize = clazz.getBasicSize();
                if (basicSize != -1) {
                    dataPtr = allocateNode.calloc(hpyContext, 1, basicSize);
                    destroyFunc = clazz.getHPyDestroyFunc();

                    // write data pointer to out var
                    writePointerNode.write(hpyContext, dataOutVar, dataPtr);

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                    // TODO(fa): add memory tracing
                }
                defaultCallFunc = clazz.getHPyDefaultCallFunc();
            }

            int builtinShape = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(profiledTypeObject);
            PythonObject pythonObject = createFromBuiltinShape(builtinShape, profiledTypeObject, dataPtr, factory);

            if (destroyFunc != null) {
                hpyContext.createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
            if (defaultCallFunc != null) {
                GraalHPyData.setHPyCallFunction(pythonObject, defaultCallFunc);
            }

            return pythonObject;
        }

        static PythonObject createFromBuiltinShape(int builtinShape, Object type, Object dataPtr, PythonObjectFactory factory) {
            PythonObject result = switch (builtinShape) {
                case HPyType_BUILTIN_SHAPE_LEGACY, HPyType_BUILTIN_SHAPE_OBJECT -> factory.createPythonHPyObject(type, dataPtr);
                case HPyType_BUILTIN_SHAPE_TYPE -> throw CompilerDirectives.shouldNotReachHere("built-in shape type not yet implemented");
                case HPyType_BUILTIN_SHAPE_LONG -> factory.createInt(type, BigInteger.ZERO);
                case HPyType_BUILTIN_SHAPE_FLOAT -> factory.createFloat(type, 0.0);
                case HPyType_BUILTIN_SHAPE_UNICODE -> factory.createString(type, T_EMPTY_STRING);
                case HPyType_BUILTIN_SHAPE_TUPLE -> factory.createEmptyTuple(type);
                case HPyType_BUILTIN_SHAPE_LIST -> factory.createList(type);
                default -> throw CompilerDirectives.shouldNotReachHere(INVALID_BUILT_IN_SHAPE);
            };
            if (builtinShape != HPyType_BUILTIN_SHAPE_LEGACY && builtinShape != HPyType_BUILTIN_SHAPE_OBJECT) {
                GraalHPyData.setHPyNativeSpace(result, dataPtr);
            }
            return result;
        }
    }

    @HPyContextFunction("ctx_AsStruct_Object")
    @HPyContextFunction("ctx_AsStruct_Legacy")
    @HPyContextFunction("ctx_AsStruct_Type")
    @HPyContextFunction("ctx_AsStruct_Long")
    @HPyContextFunction("ctx_AsStruct_Float")
    @HPyContextFunction("ctx_AsStruct_Unicode")
    @HPyContextFunction("ctx_AsStruct_Tuple")
    @HPyContextFunction("ctx_AsStruct_List")
    @GenerateUncached
    public abstract static class GraalHPyCast extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Cached HPyGetNativeSpacePointerNode getNativeSpacePointerNode) {
            // we can also just return NO_VALUE since that will be interpreter as NULL
            return getNativeSpacePointerNode.execute(object);
        }
    }

    @HPyContextFunction("ctx_Type_GenericNew")
    @GenerateUncached
    public abstract static class GraalHPyTypeGenericNew extends HPy5ContextFunction {

        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(GraalHPyTypeGenericNew.class);

        @Specialization
        @SuppressWarnings("unused")
        static Object doGeneric(GraalHPyContext hpyContext, Object type, Object args, long nargs, Object kw,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.AllocateNode allocateNode,
                        @Cached InlinedExactClassProfile classProfile) {

            Object profiledTypeObject = classProfile.profile(inliningTarget, type);
            Object dataPtr = null;
            Object destroyFunc = null;

            if (type instanceof PythonClass clazz) {
                long basicSize = clazz.getBasicSize();
                if (basicSize != -1) {
                    // we fully control this attribute; if it is there, it's always a long
                    dataPtr = allocateNode.calloc(hpyContext, 1, basicSize);
                    destroyFunc = clazz.getHPyDestroyFunc();

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                    // TODO(fa): add memory tracing
                }
            }

            int builtinShape = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(profiledTypeObject);
            PythonObject pythonObject = GraalHPyNew.createFromBuiltinShape(builtinShape, type, dataPtr, factory);

            if (destroyFunc != null) {
                hpyContext.createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
            return pythonObject;
        }
    }

    @HPyContextFunction("ctx_Absolute")
    @GenerateUncached
    public abstract static class GraalHPyAbsolute extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_ABS);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Long")
    @GenerateUncached
    public abstract static class GraalHPyLong extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_INT);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Float")
    @GenerateUncached
    public abstract static class GraalHPyFloat extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_FLOAT);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Str")
    @GenerateUncached
    public abstract static class GraalHPyStr extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_STR);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Repr")
    @GenerateUncached
    public abstract static class GraalHPyRepr extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_REPR);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_ASCII")
    @GenerateUncached
    public abstract static class GraalHPyASCII extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_ASCII);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Bytes")
    @GenerateUncached
    public abstract static class GraalHPyBytes extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_BYTES);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Hash")
    @GenerateUncached
    public abstract static class GraalHPyHash extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_HASH);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_Length")
    @GenerateUncached
    public abstract static class GraalHPyLength extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arg,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallUnaryMethodNode callNode) {
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_LEN);
            return callNode.executeObject(builtinFunction, arg);
        }
    }

    @HPyContextFunction("ctx_RichCompare")
    @GenerateUncached
    public abstract static class GraalHPyRichcompare extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object receiver, Object arg1, int arg2,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupSpecialMethodNode.Dynamic lookupRichcmp,
                        @Cached CallTernaryMethodNode callRichcmp) {
            Object richcmp = lookupRichcmp.execute(null, inliningTarget, getClassNode.execute(inliningTarget, receiver), SpecialMethodNames.T_RICHCMP, receiver);
            return callRichcmp.execute(null, richcmp, receiver, arg1, arg2);
        }
    }

    @HPyContextFunction("ctx_RichCompareBool")
    @GenerateUncached
    public abstract static class GraalHPyRichcompareBool extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(Object ctx, Object receiver, Object arg1, int arg2,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupSpecialMethodNode.Dynamic lookupRichcmp,
                        @Cached CallTernaryMethodNode callRichcmp,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object result = GraalHPyRichcompare.doGeneric(ctx, receiver, arg1, arg2, inliningTarget, getClassNode, lookupRichcmp, callRichcmp);
            return PInt.intValue(isTrueNode.execute(null, inliningTarget, result));
        }
    }

    @HPyContextFunction("ctx_Index")
    @GenerateUncached
    public abstract static class GraalHPyAsIndex extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode) {
            return indexNode.execute(null, inliningTarget, object);
        }
    }

    @HPyContextFunction("ctx_Number_Check")
    @GenerateUncached
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class GraalHPyIsNumber extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Int") LookupCallableSlotInMRONode lookup) {
            if (indexCheckNode.execute(inliningTarget, object) || canBeDoubleNode.execute(inliningTarget, object)) {
                return 1;
            }
            Object receiverType = getClassNode.execute(inliningTarget, object);
            return PInt.intValue(lookup.execute(receiverType) != PNone.NO_VALUE);
        }
    }

    @HPyContextFunction("ctx_Tuple_FromArray")
    @GenerateUncached
    public abstract static class GraalHPyTupleFromArray extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object arrayPtr, long nelements,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadHPyArrayNode readHPyArrayNode,
                        @Cached PythonObjectFactory factory) {
            int n;
            try {
                n = castToJavaIntExactNode.execute(inliningTarget, nelements);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(castToJavaIntExactNode, PythonBuiltinClassType.MemoryError);
            }

            Object[] elements = readHPyArrayNode.execute(hpyContext, arrayPtr, 0, n);
            return factory.createTuple(elements);
        }
    }

    @HPyContextFunction("ctx_TupleBuilder_New")
    @HPyContextFunction("ctx_ListBuilder_New")
    @GenerateUncached
    public abstract static class GraalHPyBuilderNew extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, long lcapacity,
                        @Cached HPyAsHandleNode asHandleNode) {
            int capacity;
            if (PInt.isIntRange(lcapacity) && (capacity = (int) lcapacity) >= 0) {
                Object[] data = new Object[capacity];
                Arrays.fill(data, PNone.NONE);
                return asHandleNode.execute(new ObjectSequenceStorage(data));
            }
            return NULL_HANDLE;
        }
    }

    @HPyContextFunction("ctx_TupleBuilder_Set")
    @HPyContextFunction("ctx_ListBuilder_Set")
    @GenerateUncached
    public abstract static class GraalHPyBuilderSet extends HPyQuaternaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderHandle, long lidx, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached SequenceStorageNodes.SetItemDynamicNode setItemNode) {
            Object builder = asPythonObjectNode.execute(builderHandle);
            if (builder instanceof ObjectSequenceStorage storage) {
                try {
                    int idx = castToJavaIntExactNode.execute(inliningTarget, lidx);
                    setItemNode.execute(null, NoGeneralizationNode.DEFAULT, storage, idx, value);
                } catch (CannotCastException e) {
                    // fall through
                }
                return 0;
            }
            /*
             * that's really unexpected since the C signature should enforce a valid builder but
             * someone could have messed it up
             */
            throw CompilerDirectives.shouldNotReachHere("invalid builder object");
        }
    }

    @GenerateCached(false)
    abstract static class HPyBuilderBuild extends HPyBinaryContextFunction {

        boolean isTupleBuilder() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderHandle,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached PythonObjectFactory factory) {
            ObjectSequenceStorage builder = cast(closeAndGetHandleNode.execute(builderHandle));
            if (builder == null) {
                /*
                 * that's really unexpected since the C signature should enforce a valid builder but
                 * someone could have messed it up
                 */
                throw CompilerDirectives.shouldNotReachHere("invalid builder object");
            }
            return isTupleBuilder() ? factory.createTuple(builder) : factory.createList(builder);
        }

        static ObjectSequenceStorage cast(Object object) {
            if (object instanceof ObjectSequenceStorage) {
                return (ObjectSequenceStorage) object;
            }
            return null;
        }
    }

    @HPyContextFunction("ctx_TupleBuilder_Build")
    @GenerateUncached
    public abstract static class GraalHPyTupleBuilderBuild extends HPyBuilderBuild {
        @Override
        final boolean isTupleBuilder() {
            return true;
        }
    }

    @HPyContextFunction("ctx_ListBuilder_Build")
    @GenerateUncached
    public abstract static class GraalHPyListBuilderBuild extends HPyBuilderBuild {
        @Override
        final boolean isTupleBuilder() {
            return false;
        }
    }

    @HPyContextFunction("ctx_TupleBuilder_Cancel")
    @HPyContextFunction("ctx_ListBuilder_Cancel")
    @GenerateUncached
    public abstract static class GraalHPyBuilderCancel extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderHandle,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode) {
            // be pedantic and also check what we are cancelling
            ObjectSequenceStorage builder = HPyBuilderBuild.cast(closeAndGetHandleNode.execute(builderHandle));
            if (builder == null) {
                /*
                 * that's really unexpected since the C signature should enforce a valid builder but
                 * someone could have messed it up
                 */
                throw CompilerDirectives.shouldNotReachHere("invalid builder object");
            }
            return 0;
        }
    }

    @HPyContextFunction("ctx_Tracker_New")
    @GenerateUncached
    public abstract static class GraalHPyTrackerNew extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, long lcapacity,
                        @Cached HPyAsHandleNode asHandleNode) {
            int capacity;
            if (PInt.isIntRange(lcapacity) && (capacity = (int) lcapacity) >= 0) {
                return asHandleNode.execute(new GraalHPyTracker(capacity));
            }
            return NULL_HANDLE;
        }
    }

    @HPyContextFunction("ctx_Tracker_Add")
    @GenerateUncached
    public abstract static class GraalHPyTrackerAdd extends HPyTernaryContextFunction {
        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderArg, Object item,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyEnsureHandleNode ensureHandleNode) {
            GraalHPyTracker builder = cast(asPythonObjectNode.execute(builderArg));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                throw CompilerDirectives.shouldNotReachHere("invalid builder object");
            }
            try {
                GraalHPyHandle handle = ensureHandleNode.execute(item);
                if (handle != null) {
                    builder.add(handle);
                }
            } catch (OverflowException | OutOfMemoryError e) {
                return -1;
            }
            return 0;
        }

        static GraalHPyTracker cast(Object object) {
            if (object instanceof GraalHPyTracker) {
                return (GraalHPyTracker) object;
            }
            return null;
        }
    }

    @HPyContextFunction("ctx_Tracker_Close")
    @GenerateUncached
    public abstract static class GraalHPyTrackerCleanup extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderHandle,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            GraalHPyTracker builder = GraalHPyTrackerAdd.cast(closeAndGetHandleNode.execute(builderHandle));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                throw CompilerDirectives.shouldNotReachHere("invalid builder object");
            }
            builder.free(closeHandleNode);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Tracker_ForgetAll")
    @GenerateUncached
    public abstract static class GraalHPyTrackerForgetAll extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object builderArg,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode) {
            GraalHPyTracker builder = GraalHPyTrackerAdd.cast(asPythonObjectNode.execute(builderArg));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                throw CompilerDirectives.shouldNotReachHere("invalid builder object");
            }
            builder.removeAll();
            return 0;
        }
    }

    @HPyContextFunction("ctx_Callable_Check")
    @GenerateUncached
    public abstract static class GraalHPyIsCallable extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck) {
            return PInt.intValue(callableCheck.execute(inliningTarget, object));
        }
    }

    @HPyContextFunction("ctx_CallTupleDict")
    @GenerateUncached
    public abstract static class GraalHPyCallTupleDict extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object callable, Object argsObject, Object kwargsObject,
                        @Bind("this") Node inliningTarget,
                        @Cached ExecutePositionalStarargsNode expandArgsNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            // check and expand args
            Object[] args = castArgs(argsObject, expandArgsNode, raiseNode);
            // check and expand kwargs
            PKeyword[] keywords = castKwargs(inliningTarget, kwargsObject, lenNode, expandKwargsNode, raiseNode);
            return callNode.execute(callable, args, keywords);
        }

        private static Object[] castArgs(Object args,
                        ExecutePositionalStarargsNode expandArgsNode,
                        PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (args == PNone.NO_VALUE) {
                return PythonUtils.EMPTY_OBJECT_ARRAY;
            }
            if (PGuards.isPTuple(args)) {
                return expandArgsNode.executeWith(null, args);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.HPY_CALLTUPLEDICT_REQUIRES_ARGS_TUPLE_OR_NULL);
        }

        private static PKeyword[] castKwargs(Node inliningTarget, Object kwargs,
                        HashingStorageLen lenNode,
                        ExpandKeywordStarargsNode expandKwargsNode,
                        PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (kwargs == PNone.NO_VALUE || isEmptyDict(inliningTarget, kwargs, lenNode)) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (PGuards.isDict(kwargs)) {
                return expandKwargsNode.execute(inliningTarget, kwargs);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.HPY_CALLTUPLEDICT_REQUIRES_KW_DICT_OR_NULL);
        }

        private static boolean isEmptyDict(Node inliningTarget, Object delegate, HashingStorageLen lenNode) {
            return delegate instanceof PDict && lenNode.execute(inliningTarget, ((PDict) delegate).getDictStorage()) == 0;
        }
    }

    @HPyContextFunction("ctx_Call")
    @GenerateUncached
    public abstract static class GraalHPyCall extends HPy5ContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object callable, Object args, long lnargs, Object kwnamesObj,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadHPyArrayNode readHPyArrayNode,
                        @Cached PyTupleSizeNode tupleSizeNode,
                        @Cached HPyPackKeywordArgsNode packKeywordArgsNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {

            if (!PInt.isIntRange(lnargs)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            int nargs = (int) lnargs;
            PTuple kwnames;
            int nkw;
            if (kwnamesObj instanceof PTuple) {
                kwnames = (PTuple) kwnamesObj;
                nkw = tupleSizeNode.execute(inliningTarget, kwnames);
            } else {
                nkw = 0;
                kwnames = null;
            }

            // positional args are from 'args[0]' ... 'args[nargs - 1]'
            Object[] positionalArgs = readHPyArrayNode.execute(hpyContext, args, 0, nargs);

            PKeyword[] keywords;
            if (nkw > 0) {
                // keyword arg values are from 'args[nargs]' ... 'args[nargs + nkw - 1]'
                Object[] kwObjs = readHPyArrayNode.execute(hpyContext, args, nargs, nargs);
                keywords = packKeywordArgsNode.execute(inliningTarget, kwObjs, kwnames);
            } else {
                keywords = PKeyword.EMPTY_KEYWORDS;
            }

            return callNode.execute(callable, positionalArgs, keywords);
        }
    }

    @HPyContextFunction("ctx_CallMethod")
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GraalHPyCallMethod extends HPy5ContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, TruffleString name, Object args, long lnargs, Object kwnames,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadHPyArrayNode readHPyArrayNode,
                        @Cached PyTupleSizeNode tupleSizeNode,
                        @Cached HPyPackKeywordArgsNode packKeywordArgsNode,
                        @Cached PyObjectGetMethod getMethodNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {

            if (!PInt.isIntRange(lnargs)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            int nargs = (int) lnargs;
            int nkw = kwnames != PNone.NO_VALUE ? tupleSizeNode.execute(inliningTarget, kwnames) : 0;

            // positional args are from 'args[0]' ... 'args[nargs - 1]' (including 'self')
            Object[] positionalArgs = readHPyArrayNode.execute(hpyContext, args, 0, nargs);
            Object receiver = positionalArgs[0];

            Object callable = getMethodNode.execute(null, inliningTarget, receiver, name);

            PKeyword[] keywords;
            if (nkw > 0) {
                // check and expand kwargs
                Object[] kwObjs = readHPyArrayNode.execute(hpyContext, args, nargs, nargs);
                keywords = packKeywordArgsNode.execute(inliningTarget, kwObjs, (PTuple) kwnames);
            } else {
                keywords = PKeyword.EMPTY_KEYWORDS;
            }
            return callNode.execute(callable, positionalArgs, keywords);
        }
    }

    @HPyContextFunction("ctx_Dump")
    @GenerateUncached
    public abstract static class GraalHPyDump extends HPyBinaryContextFunction {

        @Specialization
        @TruffleBoundary
        static int doGeneric(GraalHPyContext hpyContext, Object object) {
            PythonContext context = hpyContext.getContext();
            Object type = GetClassNode.executeUncached(object);
            PrintWriter stderr = new PrintWriter(context.getStandardErr());
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + GetNameNode.executeUncached(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                stderr.println(PyObjectReprAsTruffleStringNode.executeUncached(object).toJavaStringUncached());
                stderr.flush();
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            return 0;
        }
    }

    @HPyContextFunction("ctx_Type")
    @GenerateUncached
    public abstract static class GraalHPyType extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode) {
            return getClassNode.execute(inliningTarget, object);
        }
    }

    @HPyContextFunction("ctx_TypeCheck")
    @GenerateUncached
    public abstract static class GraalHPyTypeCheck extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object object, Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), type));
        }
    }

    @HPyContextFunction("ctx_Err_NewExceptionWithDoc")
    @GenerateUncached
    public abstract static class GraalHPyNewExceptionWithDoc extends HPy5ContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object namePtr, Object docPtr, Object base, Object dictObj,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.CodePointLengthNode codepointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached HashingStorageGetItem getHashingStorageItem,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached CallNode callTypeConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            TruffleString doc;
            if (!lib.isNull(docPtr)) {
                doc = fromCharPointerNode.execute(docPtr);
            } else {
                doc = null;
            }
            return createNewExceptionWithDoc(inliningTarget, namePtr, base, dictObj, doc, fromCharPointerNode, castToTruffleStringNode, indexOfCodepointNode, codepointLengthNode, substringNode,
                            getHashingStorageItem,
                            setHashingStorageItem, callTypeConstructorNode, raiseNode, factory);
        }

        static Object createNewExceptionWithDoc(Node inliningTarget, Object namePtr, Object base, Object dictObj, TruffleString doc,
                        FromCharPointerNode fromCharPointerNode,
                        CastToTruffleStringNode castToTruffleStringNode,
                        TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        TruffleString.CodePointLengthNode codepointLengthNode,
                        TruffleString.SubstringNode substringNode,
                        HashingStorageGetItem getHashingStorageItem,
                        HashingStorageSetItem setHashingStorageItem,
                        CallNode callTypeConstructorNode,
                        PRaiseNode raiseNode,
                        PythonObjectFactory factory) {

            TruffleString name = fromCharPointerNode.execute(namePtr);
            int len = codepointLengthNode.execute(name, TS_ENCODING);
            int dotIdx = indexOfCodepointNode.execute(name, '.', 0, len, TS_ENCODING);
            if (dotIdx < 0) {
                throw raiseNode.raise(SystemError, ErrorMessages.NAME_MUST_BE_MOD_CLS);
            }

            if (base == PNone.NO_VALUE) {
                base = PythonBuiltinClassType.Exception;
            }
            PDict dict;
            HashingStorage dictStorage;
            if (dictObj == PNone.NO_VALUE) {
                dictStorage = new DynamicObjectStorage(PythonLanguage.get(castToTruffleStringNode));
                dict = factory.createDict(dictStorage);
            } else {
                if (!(dictObj instanceof PDict)) {
                    /*
                     * CPython expects a PyDictObject and if not, it raises a
                     * ErrorMessages.BAD_INTERNAL_CALL.
                     */
                    throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
                }
                dict = (PDict) dictObj;
                dictStorage = dict.getDictStorage();
            }

            if (!getHashingStorageItem.hasKey(inliningTarget, dictStorage, SpecialAttributeNames.T___MODULE__)) {
                dictStorage = setHashingStorageItem.execute(inliningTarget, dictStorage, SpecialAttributeNames.T___MODULE__, substringNode.execute(name, 0, dotIdx, TS_ENCODING, false));
            }
            if (doc != null) {
                dictStorage = setHashingStorageItem.execute(inliningTarget, dictStorage, SpecialAttributeNames.T___DOC__, doc);
            }
            dict.setDictStorage(dictStorage);

            PTuple bases;
            if (base instanceof PTuple) {
                bases = (PTuple) base;
            } else {
                bases = factory.createTuple(new Object[]{base});
            }

            return callTypeConstructorNode.execute(PythonBuiltinClassType.PythonClass, substringNode.execute(name, dotIdx + 1, len - dotIdx - 1, TS_ENCODING, false), bases, dict);
        }
    }

    @HPyContextFunction("ctx_Err_NewException")
    @GenerateUncached
    public abstract static class GraalHPyNewException extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object namePtr, Object base, Object dictObj,
                        @Bind("this") Node inliningTarget,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.CodePointLengthNode codepointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached HashingStorageGetItem getHashingStorageItem,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached CallNode callTypeConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            return GraalHPyNewExceptionWithDoc.createNewExceptionWithDoc(inliningTarget, namePtr, base, dictObj, null, fromCharPointerNode, castToTruffleStringNode, indexOfCodepointNode,
                            codepointLengthNode,
                            substringNode, getHashingStorageItem, setHashingStorageItem, callTypeConstructorNode, raiseNode, factory);
        }
    }

    @HPyContextFunction("ctx_Is")
    @GenerateUncached
    public abstract static class GraalHPyIs extends HPyTernaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object left, Object right,
                        @Cached IsNode isNode) {
            return PInt.intValue(isNode.execute(left, right));
        }
    }

    @HPyContextFunction("ctx_Import_ImportModule")
    @GenerateUncached
    public abstract static class GraalHPyImportModule extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object charPtr,
                        @Cached FromCharPointerNode fromCharPointerNode) {
            return AbstractImportNode.importModule(fromCharPointerNode.execute(charPtr));
        }
    }

    @HPyContextFunction("ctx_Field_Store")
    @GenerateUncached
    public abstract static class GraalHPyFieldStore extends HPyQuaternaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, PythonObject owner, Object hpyFieldPtr, Object referent,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyFieldStoreNode hPyFieldStoreNode) {
            Object hpyFieldObject = callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_FIELD_I, hpyFieldPtr);
            int idx = hPyFieldStoreNode.execute(inliningTarget, owner, hpyFieldObject, referent);
            GraalHPyHandle newHandle = asHandleNode.executeField(referent, idx);
            callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_SET_FIELD_I, hpyFieldPtr, newHandle);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Field_Load")
    @GenerateUncached
    public abstract static class GraalHPyFieldLoad extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, PythonObject owner, Object hpyFieldPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyFieldLoadNode hPyFieldLoadNode) {
            return hPyFieldLoadNode.execute(inliningTarget, owner, hpyFieldPtr);
        }
    }

    @HPyContextFunction("ctx_Global_Store")
    @GenerateUncached
    public abstract static class GraalHPyGlobalStore extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object hpyGlobalPtr, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached InlinedExactClassProfile typeProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            Object hpyGlobal = typeProfile.profile(inliningTarget, callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_GET_GLOBAL_I, hpyGlobalPtr));

            int idx = -1;
            if (hpyGlobal instanceof GraalHPyHandle) {
                // branch profiling with typeProfile
                idx = ((GraalHPyHandle) hpyGlobal).getGlobalId();
            } else if (!(hpyGlobal instanceof Long) && lib.isNull(hpyGlobal)) {
                // nothing to do
            } else {
                long bits;
                if (hpyGlobal instanceof Long) {
                    // branch profile due to lib.asPointer usage in else branch
                    // and typeProfile
                    bits = (Long) hpyGlobal;
                } else {
                    try {
                        bits = lib.asPointer(hpyGlobal);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                if (GraalHPyBoxing.isBoxedHandle(bits)) {
                    idx = GraalHPyBoxing.unboxHandle(bits);
                    // idx =
                    // context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getGlobalId();
                }
            }

            // TODO: (tfel) do not actually allocate the index / free the existing one when
            // value can be stored as tagged handle
            idx = hpyContext.createGlobal(value, idx);
            GraalHPyHandle newHandle = GraalHPyHandle.createGlobal(value, idx);
            callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_SET_GLOBAL_I, hpyGlobalPtr, newHandle);
            return 0;
        }
    }

    @HPyContextFunction("ctx_Global_Load")
    @GenerateUncached
    public abstract static class GraalHPyGlobalLoad extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object hpyGlobal,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (hpyGlobal instanceof GraalHPyHandle h) {
                // branch profiling with typeProfile
                return h.getDelegate();
            } else if (!(hpyGlobal instanceof Long) && lib.isNull(hpyGlobal)) {
                // type profile influences first test
                return NULL_HANDLE_DELEGATE;
            } else {
                long bits;
                if (hpyGlobal instanceof Long) {
                    // branch profile due to lib.asPointer usage in else branch
                    // and typeProfile
                    bits = (Long) hpyGlobal;
                } else {
                    try {
                        bits = lib.asPointer(hpyGlobal);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                if (GraalHPyBoxing.isBoxedHandle(bits)) {
                    // if asHandleNode wasn't used above, it acts as a branch profile
                    // here. otherwise we're probably already pulling in a lot of code
                    // and are a bit too polymorphic
                    return hpyContext.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits));
                } else {
                    // tagged handles can be returned directly
                    return bits;
                }
            }
        }
    }

    @HPyContextFunction("ctx_LeavePythonExecution")
    @GenerateUncached
    public abstract static class GraalHPyLeavePythonExecution extends HPyUnaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext,
                        @Cached GilNode gil) {
            PythonContext context = hpyContext.getContext();
            PythonThreadState threadState = context.getThreadState(PythonLanguage.get(gil));
            gil.release(context, true);
            return threadState;
        }
    }

    @HPyContextFunction("ctx_ReenterPythonExecution")
    @GenerateUncached
    public abstract static class GraalHPyReenterPythonExecution extends HPyBinaryContextFunction {

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, @SuppressWarnings("unused") Object threadState,
                        @Cached GilNode gil) {
            // nothing to do with PThreadState in 'threadState'
            gil.acquire(hpyContext.getContext());
            return 0;
        }
    }

    @HPyContextFunction("ctx_Contains")
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateUncached
    public abstract static class GraalHPyContains extends HPyTernaryContextFunction {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object container, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            return PInt.intValue(containsNode.execute(null, inliningTarget, container, key));
        }
    }

    @HPyContextFunction("ctx_Type_IsSubtype")
    @GenerateUncached
    public abstract static class GraalHPyTypeIsSubtype extends HPyTernaryContextFunction {
        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object derived, Object type,
                        @Cached IsSubtypeNode isSubtype) {
            return PInt.intValue(isSubtype.execute(derived, type));
        }
    }

    @HPyContextFunction("ctx_Type_GetName")
    @GenerateUncached
    public abstract static class GraalHPyTypeGetName extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyTypeGetNameNode getName) {
            return getName.execute(inliningTarget, hpyContext, type);
        }
    }

    @HPyContextFunction("ctx_Dict_Keys")
    @GenerateUncached
    public abstract static class GraalHPyDictKeys extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object dictObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictKeys keysNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (dictObj instanceof PDict dict) {
                return keysNode.execute(inliningTarget, dict);
            }
            throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @HPyContextFunction("ctx_Dict_Copy")
    @GenerateUncached
    public abstract static class GraalHPyDictCopy extends HPyBinaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object dictObj,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (dictObj instanceof PDict dict) {
                return factory.createDict(copyNode.execute(inliningTarget, dict.getDictStorage()));
            }
            throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    // see _HPyCapsule_key in the HPy API
    public static final class CapsuleKey {
        public static final byte Pointer = 0;
        public static final byte Name = 1;
        public static final byte Context = 2;
        public static final byte Destructor = 3;
    }

    @HPyContextFunction("ctx_Capsule_New")
    @GenerateUncached
    public abstract static class GraalHPyCapsuleNew extends HPyQuaternaryContextFunction {

        @Specialization
        static PyCapsule doGeneric(GraalHPyContext hpyContext, Object pointer, Object namePtr, Object dtorPtr,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            if (isNullNode.execute(hpyContext, pointer)) {
                throw raiseNode.raise(ValueError, ErrorMessages.HPYCAPSULE_NEW_NULL_PTR_ERROR);
            }
            Object hpyDestructor = null;
            if (!isNullNode.execute(hpyContext, dtorPtr)) {
                Object cpyTrampoline = readPointerNode.read(hpyContext, dtorPtr, GraalHPyCField.HPyCapsule_Destructor__cpy_trampoline);
                hpyDestructor = readPointerNode.read(hpyContext, dtorPtr, GraalHPyCField.HPyCapsule_Destructor__impl);
                if (isNullNode.execute(hpyContext, cpyTrampoline) || isNullNode.execute(hpyContext, hpyDestructor)) {
                    throw raiseNode.raise(ValueError, ErrorMessages.INVALID_HPYCAPSULE_DESTRUCTOR);
                }
            }
            return factory.createCapsule(pointer, namePtr, hpyDestructor);
        }
    }

    @HPyContextFunction("ctx_Capsule_Get")
    @GenerateUncached
    public abstract static class GraalHPyCapsuleGet extends HPyQuaternaryContextFunction {
        public static final TruffleString INCORRECT_NAME = tsLiteral("HPyCapsule_GetPointer called with incorrect name");

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object capsule, int key, Object namePtr,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode,
                        @Cached PRaiseNode raiseNode) {
            isLegalCapsule(capsule, key, raiseNode);
            PyCapsule pyCapsule = (PyCapsule) capsule;
            Object result;
            switch (key) {
                case CapsuleKey.Pointer -> {
                    if (!nameMatchesNode.execute(inliningTarget, pyCapsule.getName(), namePtr)) {
                        throw raiseNode.raise(ValueError, INCORRECT_NAME);
                    }
                    result = pyCapsule.getPointer();
                }
                case CapsuleKey.Context -> result = pyCapsule.getContext();
                case CapsuleKey.Name -> result = pyCapsule.getName();
                case CapsuleKey.Destructor -> result = pyCapsule.getDestructor();
                default -> throw CompilerDirectives.shouldNotReachHere("invalid key");
            }
            // never allow Java 'null' to be returned
            if (result == null) {
                return PNone.NO_VALUE;
            }
            return result;
        }

        public static void isLegalCapsule(Object object, int key, PRaiseNode raiseNode) {
            if (!(object instanceof PyCapsule) || ((PyCapsule) object).getPointer() == null) {
                throw raiseNode.raise(ValueError, getErrorMessage(key));
            }
        }

        @TruffleBoundary
        public static TruffleString getErrorMessage(int key) {
            return switch (key) {
                case CapsuleKey.Pointer -> ErrorMessages.CAPSULE_GETPOINTER_WITH_INVALID_CAPSULE;
                case CapsuleKey.Context -> ErrorMessages.CAPSULE_GETCONTEXT_WITH_INVALID_CAPSULE;
                case CapsuleKey.Name -> ErrorMessages.CAPSULE_GETNAME_WITH_INVALID_CAPSULE;
                case CapsuleKey.Destructor -> ErrorMessages.CAPSULE_GETDESTRUCTOR_WITH_INVALID_CAPSULE;
                default -> throw CompilerDirectives.shouldNotReachHere("invalid key");
            };
        }
    }

    @HPyContextFunction("ctx_Capsule_Set")
    @GenerateUncached
    public abstract static class GraalHPyCapsuleSet extends HPyQuaternaryContextFunction {
        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object capsule, int key, Object valuePtr,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            GraalHPyCapsuleGet.isLegalCapsule(capsule, key, raiseNode);
            PyCapsule pyCapsule = (PyCapsule) capsule;
            switch (key) {
                case CapsuleKey.Pointer -> {
                    if (interopLib.isNull(valuePtr)) {
                        throw raiseNode.raise(ValueError, ErrorMessages.CAPSULE_SETPOINTER_CALLED_WITH_NULL_POINTER);
                    }
                    pyCapsule.setPointer(valuePtr);
                }
                case CapsuleKey.Context -> pyCapsule.setContext(valuePtr);
                case CapsuleKey.Name -> {
                    // we may assume that the pointer is owned
                    pyCapsule.setName(fromCharPointerNode.execute(valuePtr, false));
                }
                case CapsuleKey.Destructor -> pyCapsule.setDestructor(valuePtr);
                default -> throw CompilerDirectives.shouldNotReachHere("invalid key");
            }
            return 0;
        }
    }

    @HPyContextFunction("ctx_Capsule_IsValid")
    @GenerateUncached
    public abstract static class GraalHPyCapsuleIsValid extends HPyTernaryContextFunction {
        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object capsule, Object namePtr,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode) {
            return PInt.intValue(capsule instanceof PyCapsule pyCapsule && nameMatchesNode.execute(inliningTarget, pyCapsule.getName(), namePtr));
        }
    }

    @HPyContextFunction("ctx_ContextVar_New")
    @GenerateUncached
    public abstract static class GraalHPyContextVarNew extends HPyTernaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object namePtr, Object def,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CallNode callContextvar) {
            TruffleString name = fromCharPointerNode.execute(namePtr);
            return callContextvar.execute(PythonBuiltinClassType.ContextVar, name, def);
        }
    }

    @HPyContextFunction("ctx_ContextVar_Get")
    @GenerateUncached
    public abstract static class GraalHPyContextVarGet extends HPyQuaternaryContextFunction {
        @Specialization
        static int doGeneric(GraalHPyContext hpyContext, Object var, Object def, Object outPtr,
                        @Cached PRaiseNode raiseNode,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.WriteHPyNode writeHPyNode) {
            if (!(var instanceof PContextVar contextVar)) {
                throw raiseNode.raise(TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
            }
            PythonThreadState threadState = hpyContext.getContext().getThreadState(PythonLanguage.get(raiseNode));
            Object result = getObject(threadState, contextVar, def);
            writeHPyNode.write(hpyContext, outPtr, result);
            return 0;
        }

        public static Object getObject(PythonThreadState threadState, PContextVar var, Object def) {
            Object result = var.getValue(threadState);
            if (result == null) {
                if (def == NULL_HANDLE_DELEGATE) {
                    def = var.getDefault();
                    if (def == PContextVar.NO_DEFAULT) {
                        def = NULL_HANDLE_DELEGATE;
                    }
                }
                result = def;
            }
            return result;
        }
    }

    @HPyContextFunction("ctx_ContextVar_Set")
    @GenerateUncached
    public abstract static class GraalHPyContextVarSet extends HPyTernaryContextFunction {
        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object var, Object val,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            if (!(var instanceof PContextVar contextVar)) {
                throw raiseNode.raise(TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
            }
            PythonThreadState threadState = hpyContext.getContext().getThreadState(PythonLanguage.get(raiseNode));
            Object oldValue = contextVar.getValue(threadState);
            contextVar.setValue(threadState, val);
            return factory.createContextVarsToken(contextVar, oldValue);
        }
    }

    @HPyContextFunction("ctx_Unicode_FromEncodedObject")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromEncodedObject extends HPyQuaternaryContextFunction {
        @Specialization(limit = "1")
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object obj, Object encodingPtr, Object errorsPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile nullProfile,
                        @CachedLibrary("encodingPtr") InteropLibrary encodingLib,
                        @CachedLibrary("errorsPtr") InteropLibrary errorsLib,
                        @Cached FromCharPointerNode fromNativeCharPointerNode,
                        @Cached PyUnicodeFromEncodedObject libNode) {
            if (nullProfile.profile(inliningTarget, obj == PNone.NO_VALUE)) {
                throw PRaiseNode.raiseUncached(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
            TruffleString encoding;
            if (!encodingLib.isNull(encodingPtr)) {
                encoding = fromNativeCharPointerNode.execute(encodingPtr);
            } else {
                encoding = T_UTF8;
            }

            TruffleString errors;
            if (!errorsLib.isNull(errorsPtr)) {
                errors = fromNativeCharPointerNode.execute(errorsPtr);
            } else {
                errors = T_STRICT;
            }
            return libNode.execute(null, inliningTarget, obj, encoding, errors);
        }
    }

    @HPyContextFunction("ctx_Unicode_Substring")
    @GenerateUncached
    public abstract static class GraalHPyUnicodeSubstring extends HPyQuaternaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, Object obj, long lstart, long lend,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached CastToJavaIntExactNode castStart,
                        @Cached CastToJavaIntExactNode castEnd,
                        @Cached InlinedConditionProfile profile,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached StrGetItemNodeWithSlice getSlice) {
            TruffleString value = castStr.execute(inliningTarget, obj);
            int start = castStart.execute(inliningTarget, lstart);
            int end = castEnd.execute(inliningTarget, lend);
            if (profile.profile(inliningTarget, start < 0 || end < 0)) {
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
            SliceInfo sliceInfo = PSlice.computeIndices(start, end, 1, codePointLengthNode.execute(value, TS_ENCODING));
            return getSlice.execute(value, sliceInfo);
        }
    }

    @HPyContextFunction("ctx_Slice_Unpack")
    @GenerateUncached
    public abstract static class GraalHPySliceUnpack extends HPy5ContextFunction {
        @Specialization
        static int doGeneric(GraalHPyContext hpyContext, Object obj, Object startPtr, Object endPtr, Object stepPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "hpyContext") GraalHPyCAccess.WriteI64Node writeHPyNode,
                        @Cached SliceNodes.SliceUnpackLong sliceUnpack) {
            if (obj instanceof PSlice slice) {
                SliceInfoLong info = sliceUnpack.execute(inliningTarget, slice);
                writeHPyNode.write(hpyContext, startPtr, info.start());
                writeHPyNode.write(hpyContext, endPtr, info.stop());
                writeHPyNode.write(hpyContext, stepPtr, info.step());
                return 0;
            }
            return -1;
        }
    }

    @HPyContextFunction("ctx_Type_GetBuiltinShape")
    @GenerateUncached
    public abstract static class GraalHPyTypeGetBuiltinShape extends HPyBinaryContextFunction {

        @Specialization
        static int doGeneric(@SuppressWarnings("unused") Object hpyContext, Object typeObject,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedExactClassProfile classProfile,
                        @Cached PRaiseNode raiseNode) {
            Object profiledTypeObject = classProfile.profile(inliningTarget, typeObject);
            int result = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(profiledTypeObject);
            if (result == -2) {
                throw raiseNode.raise(TypeError, ErrorMessages.S_MUST_BE_S, "arg", "type");
            }
            return result;
        }
    }

    @HPyContextFunction("ctx_Compile_s")
    @GenerateUncached
    public abstract static class GraalHPyCompile extends HPyQuaternaryContextFunction {
        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, Object srcPtr, Object filenamePtr, int kind,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString src = fromCharPointerNode.execute(srcPtr);
            TruffleString filename = fromCharPointerNode.execute(filenamePtr);
            Object builtinFunction = readAttributeFromObjectNode.execute(hpyContext.getContext().getBuiltins(), BuiltinNames.T_COMPILE);
            GraalHPySourceKind sourceKind = GraalHPySourceKind.fromValue(kind);
            if (sourceKind == null) {
                throw raiseNode.raise(SystemError, ErrorMessages.HPY_INVALID_SOURCE_KIND);
            }
            return callNode.execute(builtinFunction, src, filename, sourceKind.getMode());
        }
    }

    @HPyContextFunction("ctx_EvalCode")
    @GenerateUncached
    public abstract static class GraalHPyEvalCode extends HPyQuaternaryContextFunction {
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object hpyContext, PCode code, Object globals, Object locals,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached CodeNodes.GetCodeCallTargetNode getCallTargetNode,
                        @Cached GenericInvokeNode invokeNode) {

            // prepare Python frame arguments
            Object[] pArguments = PArguments.create();

            if (locals == PNone.NO_VALUE) {
                locals = globals;
            }
            PArguments.setSpecialArgument(pArguments, locals);
            // TODO(fa): set builtins in globals
            // PythonModule builtins = getContext().getBuiltins();
            // setBuiltinsInGlobals(globals, setBuiltins, builtins, lib);
            if (globals instanceof PythonObject) {
                PArguments.setGlobals(pArguments, (PythonObject) globals);
            } else {
                throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }

            RootCallTarget rootCallTarget = getCallTargetNode.execute(inliningTarget, code);
            return invokeNode.execute(rootCallTarget, pArguments);
        }
    }

    @HPyContextFunction("ctx_SetCallFunction")
    @GenerateUncached
    public abstract static class GraalHPySetCallFunction extends HPyTernaryContextFunction {
        @Specialization
        static int doGeneric(GraalHPyContext hpyContext, PythonObject object, Object callFunctionDefPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached HPyReadCallFunctionNode readCallFunctionNode) {

            Object clazz = getClassNode.execute(inliningTarget, object);
            if (!(clazz instanceof PythonClass pythonClass) || !pythonClass.isHPyType()) {
                errorProfile.enter(inliningTarget);
                throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.HPY_TYPE_DOES_NOT_IMPLEMENT_CALL_PROTOCOL, clazz);
            }
            Object callFunction = readCallFunctionNode.execute(inliningTarget, hpyContext, callFunctionDefPtr);
            GraalHPyData.setHPyCallFunction(object, callFunction);
            return 0;
        }
    }
}
