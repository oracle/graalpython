/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.hpy;

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
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_GET_;
import static com.oracle.graal.python.nodes.StringLiterals.T_TYPE_ID;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAttachFunctionTypeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNodeGen;
import com.oracle.graal.python.lib.PyFloatAsDoubleNodeGen;
import com.oracle.graal.python.lib.PyIndexCheckNodeGen;
import com.oracle.graal.python.lib.PyLongAsDoubleNodeGen;
import com.oracle.graal.python.lib.PyObjectSizeNodeGen;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
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
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import sun.misc.Unsafe;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class GraalHPyContext extends CExtContext implements TruffleObject {

    private final boolean traceJNIUpcalls;

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.class);

    private static final long SIZEOF_LONG = java.lang.Long.BYTES;

    private static final TruffleString T_NAME = tsLiteral("HPy Universal ABI (GraalVM backend)");

    @TruffleBoundary
    public static GraalHPyContext ensureHPyWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path) throws IOException, ApiInitException, ImportException {
        if (!context.hasHPyContext()) {
            /*
             * TODO(fa): Currently, you can't have the HPy context without the C API context. This
             * should eventually be possible but requires some refactoring.
             */
            CApiContext.ensureCapiWasLoaded(node, context, name, path);
            Env env = context.getEnv();

            TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
            TruffleFile capiFile = homePath.resolve("libhpy" + context.getSoAbi().toJavaStringUncached());
            try {
                SourceBuilder capiSrcBuilder = Source.newBuilder(J_LLVM_LANGUAGE, capiFile);
                if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                    capiSrcBuilder.internal(true);
                }
                Object hpyLibrary = context.getEnv().parseInternal(capiSrcBuilder.build()).call();
                GraalHPyContext hPyContext = context.createHPyContext(hpyLibrary);

                InteropLibrary interopLibrary = InteropLibrary.getFactory().getUncached(hpyLibrary);
                interopLibrary.invokeMember(hpyLibrary, "graal_hpy_init", hPyContext, new GraalHPyInitObject(hPyContext));
            } catch (PException e) {
                /*
                 * Python exceptions that occur during the HPy API initialization are just passed
                 * through.
                 */
                throw e.getExceptionForReraise();
            } catch (RuntimeException | InteropException e) {
                throw new ApiInitException(CExtContext.wrapJavaException(e, node), name, ErrorMessages.HPY_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
            }
        }
        return context.getHPyContext();
    }

    /**
     * This method loads an HPy extension module and will initialize the corresponding native
     * contexts if necessary.
     *
     * @param location The node that's requesting this operation. This is required for reporting
     *            correct source code location in case exceptions occur.
     * @param context The Python context object.
     * @param name The name of the module to load (also just required for creating appropriate error
     *            messages).
     * @param path The path of the C extension module to load (usually something ending with
     *            {@code .so} or {@code .dylib} or similar).
     * @param checkResultNode An adopted node instance. This is necessary because the result check
     *            could raise an exception and only an adopted node will report useful source
     *            locations.
     * @return A Python module.
     * @throws IOException If the specified file cannot be loaded.
     * @throws ApiInitException If the corresponding native context could not be initialized.
     * @throws ImportException If an exception occurred during C extension initialization.
     */
    @TruffleBoundary
    public static Object loadHPyModule(Node location, PythonContext context, TruffleString name, TruffleString path, boolean debug,
                    HPyCheckFunctionResultNode checkResultNode) throws IOException, ApiInitException, ImportException {

        /*
         * Unfortunately, we need eagerly initialize the HPy context because the ctors of the
         * extension may already require some symbols defined in the HPy API or C API.
         */
        GraalHPyContext hpyUniversalContext = GraalHPyContext.ensureHPyWasLoaded(location, context, name, path);
        Object llvmLibrary = loadLLVMLibrary(location, context, name, path);
        InteropLibrary llvmInteropLib = InteropLibrary.getUncached(llvmLibrary);
        TruffleString basename = getBaseName(name);
        TruffleString hpyInitFuncName = StringUtils.cat(T_HPY_INIT, basename);
        try {
            if (llvmInteropLib.isMemberExisting(llvmLibrary, hpyInitFuncName.toJavaStringUncached())) {
                Object nativeResult = initHPyModule(hpyUniversalContext, llvmLibrary, hpyInitFuncName, name, path, debug, llvmInteropLib);
                return checkResultNode.execute(context.getThreadState(context.getLanguage()), hpyUniversalContext, name, nativeResult);
            }
            throw new ImportException(null, name, path, ErrorMessages.CANNOT_INITIALIZE_EXT_NO_ENTRY, basename, path, hpyInitFuncName);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_INITIALIZE_WITH, path, basename, "");
        }
    }

    /**
     * Bascially the same as
     * {@link #initHPyModule(GraalHPyContext, Object, TruffleString, TruffleString, TruffleString, boolean, InteropLibrary)}
     * but always loads the module with the universal context and also verifies the result of the
     * init function using the provided {@code checkResultNode}.
     *
     * @param context The {@link PythonContext}.
     * @param llvmLibrary The HPy extension's shared library object.
     * @param initFuncName The HPy extension's init function name (e.g. {@code HPyInit_poc}).
     * @param name The HPy extension's name as requested by the user.
     * @param path The HPy extension's shared library path.
     * @param llvmInteropLib An interop library instance.
     * @param checkResultNode An interop library instance.
     * @return The verified result of the HPy extension's init function (a Python object; most like
     *         a Python module).
     */
    @TruffleBoundary
    public Object initHPyModule(PythonContext context, Object llvmLibrary, TruffleString initFuncName, TruffleString name, TruffleString path,
                    InteropLibrary llvmInteropLib, HPyCheckFunctionResultNode checkResultNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        Object nativeResult = initHPyModule(this, llvmLibrary, initFuncName, name, path, false, llvmInteropLib);
        return checkResultNode.execute(context.getThreadState(context.getLanguage()), this, name, nativeResult);
    }

    /**
     * Execute an HPy extension's init function and return the raw result value.
     *
     * @param hpyUniversalContext The {@code HPyContext} pointer. This an either be an instance of
     *            our (managed) {@link GraalHPyContext} or a native pointer to some other HPy
     *            context (most common the debug context).
     * @param llvmLibrary The HPy extension's shared library object.
     * @param initFuncName The HPy extension's init function name (e.g. {@code HPyInit_poc}).
     * @param name The HPy extension's name as requested by the user.
     * @param path The HPy extension's shared library path.
     * @param llvmInteropLib An interop library instance.
     * @return The bare (unconverted) result of the HPy extension's init function. This will be a
     *         handle that was created with the given {@code hpyContext}.
     */
    @TruffleBoundary
    private static Object initHPyModule(GraalHPyContext hpyUniversalContext, Object llvmLibrary, TruffleString initFuncName, TruffleString name, TruffleString path, boolean debug,
                    InteropLibrary llvmInteropLib) throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        Object initFunction;
        try {
            initFunction = llvmInteropLib.readMember(llvmLibrary, initFuncName.toJavaStringUncached());
        } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
            throw new ImportException(null, name, path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, path);
        }

        // select appropriate HPy context
        boolean saved = hpyUniversalContext.debugMode;
        hpyUniversalContext.debugMode = debug;
        try {
            if (debug || !InteropLibrary.getUncached().isExecutable(initFunction)) {
                initFunction = HPyAttachFunctionTypeNode.getUncached().execute(hpyUniversalContext, initFunction, LLVMType.HPyModule_init);
            }
            return InteropLibrary.getUncached().execute(initFunction, hpyUniversalContext);
        } finally {
            hpyUniversalContext.debugMode = saved;
        }
    }

    /**
     * Describes the type of an argument or return type in the HPyContext functions.
     */
    enum HPyContextSignatureType {
        Void("void", "VOID", void.class),
        Int("int", "SINT32", int.class),
        Long("long", "SINT64", long.class),
        Double("double", "DOUBLE", double.class),
        UnsignedLongLong("unsigned long long", "UINT64", long.class),
        LongLong("long long", "SINT64", long.class),
        UnsignedLong("unsigned long", "UINT64", long.class),
        HPy("HPy", "POINTER", long.class),
        WideChars("wchar_t*", "STRING", long.class),
        ConstWideChars("const wchart_t*", "STRING", long.class),
        Chars("char*", "STRING", long.class),
        ConsChars("const char*", "STRING", long.class),
        DataPtr("void*", "POINTER", long.class),
        DataPtrPtr("void**", "POINTER", long.class),
        HPyTracker("HPyTracker", "POINTER", long.class),
        HPy_ssize_t("HPy_ssize_t", "UINT64", long.class),
        HPyTupleBuilder("HPyTupleBuilder", "POINTER", long.class),
        HPyListBuilder("HPyListBuilder", "POINTER", long.class),
        cpy_PyObject("cpy_PyObject*", "POINTER", long.class),
        _HPyPtr("_HPyPtr", "POINTER", long.class),
        HPyType_Spec("HPyType_Spec*", "POINTER", long.class),
        HPyType_SpecParam("HPyType_SpecParam*", "POINTER", long.class),
        HPyModuleDef("HPyModuleDef*", "POINTER", long.class),
        HPyThreadState("HPyThreadState", "POINTER", long.class),
        HPyField("HPyField", "POINTER", long.class),
        _HPyFieldPtr("_HPyFieldPtr", "POINTER", long.class),
        HPyGlobal("HPyGlobal", "POINTER", long.class),
        _HPyGlobalPtr("_HPyGlobalPtr", "POINTER", long.class);

        /** The type definition used in C source code. */
        final String cType;
        /** The type definition that is used in NFI signatures. */
        final String nfiType;
        /** The type used on the Java side in JNI/CLinker functions. */
        final Class<?> jniType;

        HPyContextSignatureType(String cType, String nfiType, Class<?> jniType) {
            this.cType = cType;
            this.nfiType = nfiType;
            this.jniType = jniType;
        }
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

    private static HPyContextSignature signature(HPyContextSignatureType returnType, HPyContextSignatureType... parameterTypes) {
        return new HPyContextSignature(returnType, parameterTypes);
    }

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMember {
        NAME(tsLiteral("name")),
        PRIVATE(tsLiteral("_private")),
        CTX_VERSION(tsLiteral("ctx_version")),

        // constants
        H_NONE(tsLiteral("h_None")),
        H_TRUE(tsLiteral("h_True")),
        H_FALSE(tsLiteral("h_False")),
        H_NOTIMPLEMENTED(tsLiteral("h_NotImplemented")),
        H_ELLIPSIS(tsLiteral("h_Ellipsis")),

        // exception types
        H_BASEEXCEPTION(tsLiteral("h_BaseException")),
        H_EXCEPTION(tsLiteral("h_Exception")),
        H_STOPASYNCITERATION(tsLiteral("h_StopAsyncIteration")),
        H_STOPITERATION(tsLiteral("h_StopIteration")),
        H_GENERATOREXIT(tsLiteral("h_GeneratorExit")),
        H_ARITHMETICERROR(tsLiteral("h_ArithmeticError")),
        H_LOOKUPERROR(tsLiteral("h_LookupError")),
        H_ASSERTIONERROR(tsLiteral("h_AssertionError")),
        H_ATTRIBUTEERROR(tsLiteral("h_AttributeError")),
        H_BUFFERERROR(tsLiteral("h_BufferError")),
        H_EOFERROR(tsLiteral("h_EOFError")),
        H_FLOATINGPOINTERROR(tsLiteral("h_FloatingPointError")),
        H_OSERROR(tsLiteral("h_OSError")),
        H_IMPORTERROR(tsLiteral("h_ImportError")),
        H_MODULENOTFOUNDERROR(tsLiteral("h_ModuleNotFoundError")),
        H_INDEXERROR(tsLiteral("h_IndexError")),
        H_KEYERROR(tsLiteral("h_KeyError")),
        H_KEYBOARDINTERRUPT(tsLiteral("h_KeyboardInterrupt")),
        H_MEMORYERROR(tsLiteral("h_MemoryError")),
        H_NAMEERROR(tsLiteral("h_NameError")),
        H_OVERFLOWERROR(tsLiteral("h_OverflowError")),
        H_RUNTIMEERROR(tsLiteral("h_RuntimeError")),
        H_RECURSIONERROR(tsLiteral("h_RecursionError")),
        H_NOTIMPLEMENTEDERROR(tsLiteral("h_NotImplementedError")),
        H_SYNTAXERROR(tsLiteral("h_SyntaxError")),
        H_INDENTATIONERROR(tsLiteral("h_IndentationError")),
        H_TABERROR(tsLiteral("h_TabError")),
        H_REFERENCEERROR(tsLiteral("h_ReferenceError")),
        H_SYSTEMERROR(tsLiteral("h_SystemError")),
        H_SYSTEMEXIT(tsLiteral("h_SystemExit")),
        H_TYPEERROR(tsLiteral("h_TypeError")),
        H_UNBOUNDLOCALERROR(tsLiteral("h_UnboundLocalError")),
        H_UNICODEERROR(tsLiteral("h_UnicodeError")),
        H_UNICODEENCODEERROR(tsLiteral("h_UnicodeEncodeError")),
        H_UNICODEDECODEERROR(tsLiteral("h_UnicodeDecodeError")),
        H_UNICODETRANSLATEERROR(tsLiteral("h_UnicodeTranslateError")),
        H_VALUEERROR(tsLiteral("h_ValueError")),
        H_ZERODIVISIONERROR(tsLiteral("h_ZeroDivisionError")),
        H_BLOCKINGIOERROR(tsLiteral("h_BlockingIOError")),
        H_BROKENPIPEERROR(tsLiteral("h_BrokenPipeError")),
        H_CHILDPROCESSERROR(tsLiteral("h_ChildProcessError")),
        H_CONNECTIONERROR(tsLiteral("h_ConnectionError")),
        H_CONNECTIONABORTEDERROR(tsLiteral("h_ConnectionAbortedError")),
        H_CONNECTIONREFUSEDERROR(tsLiteral("h_ConnectionRefusedError")),
        H_CONNECTIONRESETERROR(tsLiteral("h_ConnectionResetError")),
        H_FILEEXISTSERROR(tsLiteral("h_FileExistsError")),
        H_FILENOTFOUNDERROR(tsLiteral("h_FileNotFoundError")),
        H_INTERRUPTEDERROR(tsLiteral("h_InterruptedError")),
        H_ISADIRECTORYERROR(tsLiteral("h_IsADirectoryError")),
        H_NOTADIRECTORYERROR(tsLiteral("h_NotADirectoryError")),
        H_PERMISSIONERROR(tsLiteral("h_PermissionError")),
        H_PROCESSLOOKUPERROR(tsLiteral("h_ProcessLookupError")),
        H_TIMEOUTERROR(tsLiteral("h_TimeoutError")),
        H_WARNING(tsLiteral("h_Warning")),
        H_USERWARNING(tsLiteral("h_UserWarning")),
        H_DEPRECATIONWARNING(tsLiteral("h_DeprecationWarning")),
        H_PENDINGDEPRECATIONWARNING(tsLiteral("h_PendingDeprecationWarning")),
        H_SYNTAXWARNING(tsLiteral("h_SyntaxWarning")),
        H_RUNTIMEWARNING(tsLiteral("h_RuntimeWarning")),
        H_FUTUREWARNING(tsLiteral("h_FutureWarning")),
        H_IMPORTWARNING(tsLiteral("h_ImportWarning")),
        H_UNICODEWARNING(tsLiteral("h_UnicodeWarning")),
        H_BYTESWARNING(tsLiteral("h_BytesWarning")),
        H_RESOURCEWARNING(tsLiteral("h_ResourceWarning")),

        // built-in types
        H_BASEOBJECTTYPE(tsLiteral("h_BaseObjectType")),
        H_TYPETYPE(tsLiteral("h_TypeType")),
        H_BOOLTYPE(tsLiteral("h_BoolType")),
        H_LONGTYPE(tsLiteral("h_LongType")),
        H_FLOATTYPE(tsLiteral("h_FloatType")),
        H_COMPLEXTYPE(tsLiteral("h_ComplexType")),
        H_UNICODETYPE(tsLiteral("h_UnicodeType")),
        H_BYTESTYPE(tsLiteral("h_BytesType")),
        H_TUPLETYPE(tsLiteral("h_TupleType")),
        H_LISTTYPE(tsLiteral("h_ListType")),
        H_MEMORYVIEWTYPE(tsLiteral("h_MemoryViewType")),
        H_CAPSULETYPE(tsLiteral("h_CapsuleType")),
        H_SLICETYPE(tsLiteral("h_SliceType")),

        CTX_MODULE_CREATE(tsLiteral("ctx_Module_Create")),
        CTX_DUP(tsLiteral("ctx_Dup"), signature(HPy, HPy)),
        CTX_AS_STRUCT(tsLiteral("ctx_AsStruct"), signature(DataPtr, HPy)),
        CTX_AS_STRUCT_LEGACY(tsLiteral("ctx_AsStructLegacy")),
        CTX_CLOSE(tsLiteral("ctx_Close"), signature(Void, HPy)),
        CTX_BOOL_FROMLONG(tsLiteral("ctx_Bool_FromLong")),
        CTX_LONG_FROMLONG(tsLiteral("ctx_Long_FromLong")),
        CTX_LONG_FROMUNSIGNEDLONG(tsLiteral("ctx_Long_FromUnsignedLong")),
        CTX_LONG_FROMLONGLONG(tsLiteral("ctx_Long_FromLongLong")),
        CTX_LONG_FROM_UNSIGNEDLONGLONG(tsLiteral("ctx_Long_FromUnsignedLongLong")),
        CTX_LONG_FROMSSIZE_T(tsLiteral("ctx_Long_FromSsize_t")),
        CTX_LONG_FROMSIZE_T(tsLiteral("ctx_Long_FromSize_t")),
        CTX_LONG_ASLONG(tsLiteral("ctx_Long_AsLong"), signature(Long, HPy)),
        CTX_LONG_ASLONGLONG(tsLiteral("ctx_Long_AsLongLong")),
        CTX_LONG_ASUNSIGNEDLONG(tsLiteral("ctx_Long_AsUnsignedLong")),
        CTX_LONG_ASUNSIGNEDLONGMASK(tsLiteral("ctx_Long_AsUnsignedLongMask")),
        CTX_LONG_ASUNSIGNEDLONGLONG(tsLiteral("ctx_Long_AsUnsignedLongLong")),
        CTX_LONG_ASUNSIGNEDLONGLONGMASK(tsLiteral("ctx_Long_AsUnsignedLongLongMask")),
        CTX_LONG_ASSIZE_T(tsLiteral("ctx_Long_AsSize_t")),
        CTX_LONG_ASSSIZE_T(tsLiteral("ctx_Long_AsSsize_t")),
        CTX_LONG_ASVOIDPTR(tsLiteral("ctx_Long_AsVoidPtr")),
        CTX_LONG_ASDOUBLE(tsLiteral("ctx_Long_AsDouble"), signature(Double, HPy)),
        CTX_NEW(tsLiteral("ctx_New"), signature(HPy, HPy, DataPtrPtr)),
        CTX_TYPE(tsLiteral("ctx_Type")),
        CTX_TYPECHECK(tsLiteral("ctx_TypeCheck"), signature(Long, HPy, HPy)),
        CTX_SETTYPE(tsLiteral("ctx_SetType")),
        CTX_TYPE_ISSUBTYPE(tsLiteral("ctx_Type_IsSubtype")),
        CTX_TYPE_GETNAME(tsLiteral("ctx_Type_GetName")),
        CTX_TYPE_CHECKSLOT(tsLiteral("ctx_Type_CheckSlot")),
        CTX_IS(tsLiteral("ctx_Is")),
        CTX_TYPE_GENERIC_NEW(tsLiteral("ctx_Type_GenericNew"), signature(HPy, HPy)),
        CTX_FLOAT_FROMDOUBLE(tsLiteral("ctx_Float_FromDouble"), signature(HPy, Double)),
        CTX_FLOAT_ASDOUBLE(tsLiteral("ctx_Float_AsDouble"), signature(Double, HPy)),

        // unary
        CTX_NEGATIVE(tsLiteral("ctx_Negative")),
        CTX_POSITIVE(tsLiteral("ctx_Positive")),
        CTX_ABSOLUTE(tsLiteral("ctx_Absolute")),
        CTX_INVERT(tsLiteral("ctx_Invert")),
        CTX_INDEX(tsLiteral("ctx_Index")),
        CTX_LONG(tsLiteral("ctx_Long")),
        CTX_FLOAT(tsLiteral("ctx_Float")),

        // binary
        CTX_ADD(tsLiteral("ctx_Add")),
        CTX_SUB(tsLiteral("ctx_Subtract")),
        CTX_MULTIPLY(tsLiteral("ctx_Multiply")),
        CTX_MATRIXMULTIPLY(tsLiteral("ctx_MatrixMultiply")),
        CTX_FLOORDIVIDE(tsLiteral("ctx_FloorDivide")),
        CTX_TRUEDIVIDE(tsLiteral("ctx_TrueDivide")),
        CTX_REMAINDER(tsLiteral("ctx_Remainder")),
        CTX_DIVMOD(tsLiteral("ctx_Divmod")),
        CTX_LSHIFT(tsLiteral("ctx_Lshift")),
        CTX_RSHIFT(tsLiteral("ctx_Rshift")),
        CTX_AND(tsLiteral("ctx_And")),
        CTX_XOR(tsLiteral("ctx_Xor")),
        CTX_OR(tsLiteral("ctx_Or")),
        CTX_INPLACEADD(tsLiteral("ctx_InPlaceAdd")),
        CTX_INPLACESUBTRACT(tsLiteral("ctx_InPlaceSubtract")),
        CTX_INPLACEMULTIPLY(tsLiteral("ctx_InPlaceMultiply")),
        CTX_INPLACEMATRIXMULTIPLY(tsLiteral("ctx_InPlaceMatrixMultiply")),
        CTX_INPLACEFLOORDIVIDE(tsLiteral("ctx_InPlaceFloorDivide")),
        CTX_INPLACETRUEDIVIDE(tsLiteral("ctx_InPlaceTrueDivide")),
        CTX_INPLACEREMAINDER(tsLiteral("ctx_InPlaceRemainder")),
        // TODO(fa): support IDivMod
        // CTX_INPLACEDIVMOD(tsLiteral("ctx_InPlaceDivmod")),
        CTX_INPLACELSHIFT(tsLiteral("ctx_InPlaceLshift")),
        CTX_INPLACERSHIFT(tsLiteral("ctx_InPlaceRshift")),
        CTX_INPLACEAND(tsLiteral("ctx_InPlaceAnd")),
        CTX_INPLACEXOR(tsLiteral("ctx_InPlaceXor")),
        CTX_INPLACEOR(tsLiteral("ctx_InPlaceOr")),

        // ternary
        CTX_POWER(tsLiteral("ctx_Power")),
        CTX_INPLACEPOWER(tsLiteral("ctx_InPlacePower")),

        CTX_CALLABLE_CHECK(tsLiteral("ctx_Callable_Check")),
        CTX_CALLTUPLEDICT(tsLiteral("ctx_CallTupleDict")),
        CTX_ERR_NOMEMORY(tsLiteral("ctx_Err_NoMemory")),
        CTX_ERR_SETSTRING(tsLiteral("ctx_Err_SetString")),
        CTX_ERR_SETOBJECT(tsLiteral("ctx_Err_SetObject")),
        CTX_ERR_SETFROMERRNOWITHFILENAME(tsLiteral("ctx_Err_SetFromErrnoWithFilename")),
        CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS(tsLiteral("ctx_Err_SetFromErrnoWithFilenameObjects")),
        CTX_ERR_OCCURRED(tsLiteral("ctx_Err_Occurred")),
        CTX_ERR_EXCEPTIONMATCHES(tsLiteral("ctx_Err_ExceptionMatches")),
        CTX_ERR_CLEAR(tsLiteral("ctx_Err_Clear")),
        CTX_ERR_NEWEXCEPTION(tsLiteral("ctx_Err_NewException")),
        CTX_ERR_NEWEXCEPTIONWITHDOC(tsLiteral("ctx_Err_NewExceptionWithDoc")),
        CTX_ERR_WARNEX(tsLiteral("ctx_Err_WarnEx")),
        CTX_ERR_WRITEUNRAISABLE(tsLiteral("ctx_Err_WriteUnraisable")),
        CTX_FATALERROR(tsLiteral("ctx_FatalError")),
        CTX_ISTRUE(tsLiteral("ctx_IsTrue")),
        CTX_TYPE_FROM_SPEC(tsLiteral("ctx_Type_FromSpec")),
        CTX_GETATTR(tsLiteral("ctx_GetAttr")),
        CTX_GETATTR_S(tsLiteral("ctx_GetAttr_s")),
        CTX_MAYBEGETATTR_S(tsLiteral("ctx_MaybeGetAttr_s")),
        CTX_HASATTR(tsLiteral("ctx_HasAttr")),
        CTX_HASATTR_S(tsLiteral("ctx_HasAttr_s")),
        CTX_SETATTR(tsLiteral("ctx_SetAttr")),
        CTX_SETATTR_S(tsLiteral("ctx_SetAttr_s")),
        CTX_GETITEM(tsLiteral("ctx_GetItem")),
        CTX_GETITEM_I(tsLiteral("ctx_GetItem_i"), signature(HPy, HPy, HPy_ssize_t)),
        CTX_GETITEM_S(tsLiteral("ctx_GetItem_s")),
        CTX_SETITEM(tsLiteral("ctx_SetItem")),
        CTX_SETITEM_I(tsLiteral("ctx_SetItem_i")),
        CTX_SETITEM_S(tsLiteral("ctx_SetItem_s")),
        CTX_CONTAINS(tsLiteral("ctx_Contains")),
        CTX_BYTES_CHECK(tsLiteral("ctx_Bytes_Check")),
        CTX_BYTES_SIZE(tsLiteral("ctx_Bytes_Size")),
        CTX_BYTES_GET_SIZE(tsLiteral("ctx_Bytes_GET_SIZE")),
        CTX_BYTES_ASSTRING(tsLiteral("ctx_Bytes_AsString")),
        CTX_BYTES_AS_STRING(tsLiteral("ctx_Bytes_AS_STRING")),
        CTX_BYTES_FROMSTRING(tsLiteral("ctx_Bytes_FromString")),
        CTX_BYTES_FROMSTRINGANDSIZE(tsLiteral("ctx_Bytes_FromStringAndSize")),
        CTX_UNICODE_FROMSTRING(tsLiteral("ctx_Unicode_FromString")),
        CTX_UNICODE_CHECK(tsLiteral("ctx_Unicode_Check")),
        CTX_UNICODE_ASUTF8STRING(tsLiteral("ctx_Unicode_AsUTF8String")),
        CTX_UNICODE_ASASCIISTRING(tsLiteral("ctx_Unicode_AsASCIIString")),
        CTX_UNICODE_ASLATIN1STRING(tsLiteral("ctx_Unicode_AsLatin1String")),
        CTX_UNICODE_ASUTF8ANDSIZE(tsLiteral("ctx_Unicode_AsUTF8AndSize")),
        CTX_UNICODE_FROMWIDECHAR(tsLiteral("ctx_Unicode_FromWideChar")),
        CTX_UNICODE_DECODEASCII(tsLiteral("ctx_Unicode_DecodeASCII")),
        CTX_UNICODE_DECODELATIN1(tsLiteral("ctx_Unicode_DecodeLatin1")),
        CTX_UNICODE_FROMENCODEDOBJECT(tsLiteral("ctx_Unicode_FromEncodedObject")),
        CTX_UNICODE_INTERNFROMSTRING(tsLiteral("ctx_Unicode_InternFromString")),
        CTX_UNICODE_SUBSTRING(tsLiteral("ctx_Unicode_Substring")),
        CTX_UNICODE_DECODEFSDEFAULT(tsLiteral("ctx_Unicode_DecodeFSDefault")),
        CTX_UNICODE_DECODEFSDEFAULTANDSIZE(tsLiteral("ctx_Unicode_DecodeFSDefaultAndSize")),
        CTX_UNICODE_ENCODEFSDEFAULT(tsLiteral("ctx_Unicode_EncodeFSDefault")),
        CTX_UNICODE_READCHAR(tsLiteral("ctx_Unicode_ReadChar")),
        CTX_LIST_NEW(tsLiteral("ctx_List_New")),
        CTX_LIST_APPEND(tsLiteral("ctx_List_Append")),
        CTX_DICT_CHECK(tsLiteral("ctx_Dict_Check")),
        CTX_DICT_NEW(tsLiteral("ctx_Dict_New")),
        CTX_DICT_KEYS(tsLiteral("ctx_Dict_Keys")),
        CTX_DICT_SETITEM(tsLiteral("ctx_Dict_SetItem")),
        CTX_DICT_GETITEM(tsLiteral("ctx_Dict_GetItem")),
        CTX_FROMPYOBJECT(tsLiteral("ctx_FromPyObject")),
        CTX_ASPYOBJECT(tsLiteral("ctx_AsPyObject")),
        CTX_CALLREALFUNCTIONFROMTRAMPOLINE(tsLiteral("ctx_CallRealFunctionFromTrampoline")),
        CTX_REPR(tsLiteral("ctx_Repr")),
        CTX_STR(tsLiteral("ctx_Str")),
        CTX_ASCII(tsLiteral("ctx_ASCII")),
        CTX_BYTES(tsLiteral("ctx_Bytes")),
        CTX_RICHCOMPARE(tsLiteral("ctx_RichCompare")),
        CTX_RICHCOMPAREBOOL(tsLiteral("ctx_RichCompareBool")),
        CTX_HASH(tsLiteral("ctx_Hash")),
        CTX_NUMBER_CHECK(tsLiteral("ctx_Number_Check"), signature(Int, HPy)),
        CTX_LENGTH(tsLiteral("ctx_Length"), signature(HPy_ssize_t, HPy)),
        CTX_IMPORT_IMPORTMODULE(tsLiteral("ctx_Import_ImportModule")),
        CTX_TUPLE_CHECK(tsLiteral("ctx_Tuple_Check")),
        CTX_TUPLE_FROMARRAY(tsLiteral("ctx_Tuple_FromArray")),
        CTX_TUPLE_BUILDER_NEW(tsLiteral("ctx_TupleBuilder_New")),
        CTX_TUPLE_BUILDER_SET(tsLiteral("ctx_TupleBuilder_Set")),
        CTX_TUPLE_BUILDER_BUILD(tsLiteral("ctx_TupleBuilder_Build")),
        CTX_TUPLE_BUILDER_CANCEL(tsLiteral("ctx_TupleBuilder_Cancel")),
        CTX_LIST_CHECK(tsLiteral("ctx_List_Check"), signature(Int, HPy)),
        CTX_LIST_BUILDER_NEW(tsLiteral("ctx_ListBuilder_New")),
        CTX_LIST_BUILDER_SET(tsLiteral("ctx_ListBuilder_Set")),
        CTX_LIST_BUILDER_BUILD(tsLiteral("ctx_ListBuilder_Build")),
        CTX_LIST_BUILDER_CANCEL(tsLiteral("ctx_ListBuilder_Cancel")),
        CTX_TRACKER_NEW(tsLiteral("ctx_Tracker_New"), signature(HPyTracker, HPy_ssize_t)),
        CTX_TRACKER_ADD(tsLiteral("ctx_Tracker_Add"), signature(Int, HPyTracker, HPy)),
        CTX_TRACKER_FORGET_ALL(tsLiteral("ctx_Tracker_ForgetAll")),
        CTX_TRACKER_CLOSE(tsLiteral("ctx_Tracker_Close"), signature(Void, HPyTracker)),
        CTX_FIELD_STORE(tsLiteral("ctx_Field_Store"), signature(Void, HPy, _HPyFieldPtr, HPy)),
        CTX_FIELD_LOAD(tsLiteral("ctx_Field_Load"), signature(HPy, HPyField)),
        CTX_LEAVEPYTHONEXECUTION(tsLiteral("ctx_LeavePythonExecution"), signature(HPyThreadState)),
        CTX_REENTERPYTHONEXECUTION(tsLiteral("ctx_ReenterPythonExecution"), signature(Void, HPyThreadState)),
        CTX_GLOBAL_STORE(tsLiteral("ctx_Global_Store"), signature(Void, _HPyGlobalPtr, HPy)),
        CTX_GLOBAL_LOAD(tsLiteral("ctx_Global_Load"), signature(Void, HPyGlobal, HPy)),
        CTX_CONTEXTVAR_NEW(tsLiteral("ctx_ContextVar_New")),
        CTX_CONTEXTVAR_GET(tsLiteral("ctx_ContextVar_Get")),
        CTX_CONTEXTVAR_SET(tsLiteral("ctx_ContextVar_Set")),
        CTX_CAPSULE_NEW(tsLiteral("ctx_Capsule_New")),
        CTX_CAPSULE_GET(tsLiteral("ctx_Capsule_Get")),
        CTX_CAPSULE_ISVALID(tsLiteral("ctx_Capsule_IsValid")),
        CTX_CAPSULE_SET(tsLiteral("ctx_Capsule_Set")),
        CTX_DUMP(tsLiteral("ctx_Dump")),
        CTX_SEQUENCE_CHECK(tsLiteral("ctx_Sequence_Check")),
        CTX_SLICE_UNPACK(tsLiteral("ctx_Slice_Unpack")),
        CTX_SEQITER_NEW(tsLiteral("ctx_SeqIter_New"));

        final TruffleString name;

        /**
         * If this signature is present (non-null), then a corresponding function in
         * {@link GraalHPyContext} needs to exist. E.g., for {@code ctx_Number_Check}, the function
         * {@link GraalHPyContext#ctxNumberCheck(long)} is used.
         */
        final HPyContextSignature signature;

        HPyContextMember(TruffleString name) {
            this.name = name;
            this.signature = null;
        }

        HPyContextMember(TruffleString name, HPyContextSignature signature) {
            this.name = name;
            this.signature = signature;
        }

        @CompilationFinal(dimensions = 1) private static final HPyContextMember[] VALUES = values();
        public static final HashMap<TruffleString, HPyContextMember> MEMBERS = new HashMap<>();
        public static final Object KEYS;

        static {
            for (HPyContextMember member : VALUES) {
                MEMBERS.put(member.name, member);
            }

            TruffleString[] names = new TruffleString[VALUES.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = VALUES[i].name;
            }
            KEYS = new PythonAbstractObject.Keys(names);
        }

        @TruffleBoundary
        public static int getIndex(TruffleString key) {
            HPyContextMember member = HPyContextMember.MEMBERS.get(key);
            return member == null ? -1 : member.ordinal();
        }
    }

    /**
     * Enum of C types used in the HPy API. These type names need to stay in sync with the
     * declarations in 'hpytypes.h'.
     */
    public enum LLVMType {
        HPyFunc_noargs,
        HPyFunc_o,
        HPyFunc_varargs,
        HPyFunc_keywords,
        HPyFunc_unaryfunc,
        HPyFunc_binaryfunc,
        HPyFunc_ternaryfunc,
        HPyFunc_inquiry,
        HPyFunc_lenfunc,
        HPyFunc_ssizeargfunc,
        HPyFunc_ssizessizeargfunc,
        HPyFunc_ssizeobjargproc,
        HPyFunc_ssizessizeobjargproc,
        HPyFunc_objobjargproc,
        HPyFunc_freefunc,
        HPyFunc_getattrfunc,
        HPyFunc_getattrofunc,
        HPyFunc_setattrfunc,
        HPyFunc_setattrofunc,
        HPyFunc_reprfunc,
        HPyFunc_hashfunc,
        HPyFunc_richcmpfunc,
        HPyFunc_getiterfunc,
        HPyFunc_iternextfunc,
        HPyFunc_descrgetfunc,
        HPyFunc_descrsetfunc,
        HPyFunc_initproc,
        HPyFunc_getter,
        HPyFunc_setter,
        HPyFunc_objobjproc,
        HPyFunc_traverseproc,
        HPyFunc_destructor,
        HPyFunc_getbufferproc,
        HPyFunc_releasebufferproc,
        HPyFunc_destroyfunc,
        HPyModule_init;

        public static GraalHPyNativeSymbol getGetterFunctionName(LLVMType llvmType) {
            CompilerAsserts.neverPartOfCompilation();
            TruffleString getterFunctionName = cat(T_GET_, toTruffleStringUncached(llvmType.name()), T_TYPE_ID);
            if (!GraalHPyNativeSymbol.isValid(getterFunctionName, TruffleString.EqualNode.getUncached())) {
                throw CompilerDirectives.shouldNotReachHere("Unknown C API function " + getterFunctionName);
            }
            return GraalHPyNativeSymbol.getByName(getterFunctionName, TruffleString.EqualNode.getUncached());
        }
    }

    private static final int IMMUTABLE_HANDLE_COUNT = 256;

    private Object[] hpyHandleTable = new Object[]{GraalHPyHandle.NULL_HANDLE_DELEGATE};
    private int nextHandle = 1;

    private GraalHPyHandle[] hpyGlobalsTable = new GraalHPyHandle[]{GraalHPyHandle.NULL_HANDLE};
    private final HandleStack freeStack = new HandleStack(16);
    private long hPyDebugContext;
    Object nativePointer;

    /**
     * This is set to {@code true} if an HPy extension is initialized (i.e. {@code HPyInit_*} is
     * called) in debug mode. The value is then used to create the right closures for down calls
     * during module ({@code HPyModule_Create}) and type creation ({@code HPyType_FromSpec}). We
     * need this because the debug context is just a wrapper around the universal context, so the
     * module and type creation will look as normal. For reference on how other implementations do
     * it:
     * <p>
     * CPython stores the HPy context into global C variable {@code _ctx_for_trampolines} defined by
     * {@code HPy_MODINIT}. This variable belongs to the HPy extension and the context is loaded
     * from it when calling HPy extension functions.
     * </p>
     * <p>
     * PyPy has a different structure but basically also uses a global state (see file
     * {@code interp_hpy.py}). When initializing the module, the appropriate <em>handle manager</em>
     * is used. The manager then decides which trampolines are used to call HPy extensions and the
     * trampolines pick the appropriate context.
     * </p>
     */
    private boolean debugMode;

    @CompilationFinal(dimensions = 1) private final Object[] hpyContextMembers;

    /** the native type ID of C struct 'HPyContext' */
    @CompilationFinal private Object hpyContextNativeTypeID;

    /** the native type ID of C struct 'HPy' */
    @CompilationFinal private Object hpyNativeTypeID;
    @CompilationFinal private Object hpyFieldNativeTypeID;
    @CompilationFinal private Object hpyArrayNativeTypeID;
    @CompilationFinal private long wcharSize = -1;

    /**
     * This field mirrors value of {@link PythonOptions#HPyEnableJNIFastPaths}. We store it in this
     * final field because the value is also used in non-PE code paths.
     */
    private final boolean useNativeFastPaths;

    /**
     * The global reference queue is a list consisting of {@link GraalHPyHandleReference} objects.
     * It is used to keep those objects (which are weak refs) alive until they are enqueued in the
     * corresponding reference queue. The list instance referenced by this variable is exclusively
     * owned by the main thread (i.e. the main thread may operate on the list without
     * synchronization). The HPy reference cleaner thread (see
     * {@link GraalHPyReferenceCleanerRunnable}) will consume this instance using an atomic
     * {@code getAndSet} operation. At this point, the ownership is transferred to the cleaner
     * thread.
     */
    public final AtomicReference<GraalHPyHandleReference> references = new AtomicReference<>(null);
    private ReferenceQueue<Object> nativeSpaceReferenceQueue;
    @CompilationFinal private RootCallTarget referenceCleanerCallTarget;
    private Thread hpyReferenceCleanerThread;

    private final PythonObjectSlowPathFactory slowPathFactory;

    public GraalHPyContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, GraalHPyConversionNodeSupplier.HANDLE);
        PythonLanguage language = context.getLanguage();
        useNativeFastPaths = language.getEngineOption(PythonOptions.HPyEnableJNIFastPaths);
        int traceJNISleepTime = language.getEngineOption(PythonOptions.HPyTraceUpcalls);
        traceJNIUpcalls = traceJNISleepTime != 0;
        this.slowPathFactory = context.factory();
        this.hpyContextMembers = createMembers(context, T_NAME, traceJNIUpcalls);
        for (Object member : hpyContextMembers) {
            if (member instanceof GraalHPyHandle) {
                GraalHPyHandle handle = (GraalHPyHandle) member;
                int id = handle.getId(this, ConditionProfile.getUncached());
                assert id > 0 && id < IMMUTABLE_HANDLE_COUNT;

            }
        }
        hpyHandleTable = Arrays.copyOf(hpyHandleTable, IMMUTABLE_HANDLE_COUNT * 2);
        nextHandle = IMMUTABLE_HANDLE_COUNT;
        if (traceJNIUpcalls) {
            startUpcallsDaemon(traceJNISleepTime);
        }
    }

    public PythonObjectSlowPathFactory getSlowPathFactory() {
        return slowPathFactory;
    }

    /**
     * Reference cleaner action that will be executed by the {@link AsyncHandler}.
     */
    private static final class GraalHPyHandleReferenceCleanerAction implements AsyncHandler.AsyncAction {

        private final GraalHPyHandleReference[] nativeObjectReferences;

        public GraalHPyHandleReferenceCleanerAction(GraalHPyHandleReference[] nativeObjectReferences) {
            this.nativeObjectReferences = nativeObjectReferences;
        }

        @Override
        public void execute(PythonContext context) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, nativeObjectReferences);
            GenericInvokeNode.getUncached().execute(context.getHPyContext().getReferenceCleanerCallTarget(), pArguments);
        }
    }

    private RootCallTarget getReferenceCleanerCallTarget() {
        if (referenceCleanerCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            referenceCleanerCallTarget = PythonUtils.getOrCreateCallTarget(new HPyNativeSpaceCleanerRootNode(getContext()));
        }
        return referenceCleanerCallTarget;
    }

    /**
     * This is the HPy cleaner thread runnable. It will run in parallel to the main thread, collect
     * references from the corresponding reference queue, and eventually call
     * {@link HPyNativeSpaceCleanerRootNode}. For this, the cleaner thread consumes the
     * {@link #references} list by exchanging it with an empty one (for a description of the
     * exchanging process, see also {@link #references}).
     */
    static final class GraalHPyReferenceCleanerRunnable implements Runnable {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyReferenceCleanerRunnable.class);
        private final ReferenceQueue<?> referenceQueue;
        private GraalHPyHandleReference cleanerList;

        GraalHPyReferenceCleanerRunnable(ReferenceQueue<?> referenceQueue) {
            this.referenceQueue = referenceQueue;
        }

        @Override
        public void run() {
            try {
                PythonContext pythonContext = PythonContext.get(null);
                PythonLanguage language = pythonContext.getLanguage();
                GraalHPyContext hPyContext = pythonContext.getHPyContext();
                RootCallTarget callTarget = hPyContext.getReferenceCleanerCallTarget();
                PDict dummyGlobals = pythonContext.factory().createDict();
                boolean isLoggable = LOGGER.isLoggable(Level.FINE);
                /*
                 * Intentionally retrieve the thread state every time since this will kill the
                 * thread if shutting down.
                 */
                while (!pythonContext.getThreadState(language).isShuttingDown()) {
                    Reference<?> reference = null;
                    try {
                        reference = referenceQueue.remove();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    ArrayList<GraalHPyHandleReference> refs = new ArrayList<>();
                    do {
                        if (reference instanceof GraalHPyHandleReference) {
                            refs.add((GraalHPyHandleReference) reference);
                        }
                        // consume all
                        reference = referenceQueue.poll();
                    } while (reference != null);

                    if (isLoggable) {
                        LOGGER.fine(PythonUtils.formatJString("Collected references: %d", refs.size()));
                    }

                    /*
                     * To avoid race conditions, we take the whole references list such that we can
                     * solely process it. At this point, the references list is owned by the main
                     * thread and this will now transfer ownership to the cleaner thread. The list
                     * will be replaced by an empty list (which will then be owned by the main
                     * thread).
                     */
                    GraalHPyHandleReference refList;
                    int retries = 0;
                    do {
                        /*
                         * If 'refList' is null then the main is currently updating it. So, we need
                         * to repeat until we get something. The written empty list will just be
                         * lost.
                         */
                        refList = hPyContext.references.getAndSet(null);
                    } while (refList == null && retries++ < 3);

                    if (!refs.isEmpty()) {
                        try {
                            Object[] arguments = PArguments.create(3);
                            PArguments.setGlobals(arguments, dummyGlobals);
                            PArguments.setException(arguments, PException.NO_EXCEPTION);
                            PArguments.setCallerFrameInfo(arguments, PFrame.Reference.EMPTY);
                            PArguments.setArgument(arguments, 0, refs.toArray(new GraalHPyHandleReference[0]));
                            PArguments.setArgument(arguments, 1, refList);
                            PArguments.setArgument(arguments, 2, cleanerList);
                            cleanerList = (GraalHPyHandleReference) CallTargetInvokeNode.invokeUncached(callTarget, arguments);
                        } catch (PException e) {
                            /*
                             * Since the cleaner thread is not running any Python code, we should
                             * never receive a Python exception. If it happens, consider that to be
                             * a problem (however, it is not fatal problem).
                             */
                            PException exceptionForReraise = e.getExceptionForReraise();
                            exceptionForReraise.setMessage(exceptionForReraise.getUnreifiedException().getFormattedMessage());
                            LOGGER.warning("HPy reference cleaner thread received a Python exception: " + e);
                        }
                    }
                }
            } catch (PythonThreadKillException e) {
                // this is exception shuts down the thread
                LOGGER.fine("HPy reference cleaner thread received exit signal.");
            } catch (ControlFlowException e) {
                LOGGER.warning("HPy reference cleaner thread received unexpected control flow exception.");
            } catch (Exception e) {
                LOGGER.severe("HPy reference cleaner thread received fatal exception: " + e);
            }
            LOGGER.fine("HPy reference cleaner thread is exiting.");
        }
    }

    /**
     * Root node that actually runs the destroy functions for the native memory of unreachable
     * Python objects.
     */
    private static final class HPyNativeSpaceCleanerRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("refs"), EMPTY_TRUFFLESTRING_ARRAY);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.HPyNativeSpaceCleanerRootNode.class);

        @Child private PCallHPyFunction callBulkFree;

        HPyNativeSpaceCleanerRootNode(PythonContext context) {
            super(context.getLanguage());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            /*
             * This node is not running any Python code in the sense that it does not run any code
             * that would run in CPython's interpreter loop. So, we don't need to do a
             * calleeContext.enter/exit since we should never get any Python exception.
             */

            GraalHPyHandleReference[] handleReferences = (GraalHPyHandleReference[]) PArguments.getArgument(frame, 0);
            GraalHPyHandleReference refList = (GraalHPyHandleReference) PArguments.getArgument(frame, 1);
            GraalHPyHandleReference oldRefList = (GraalHPyHandleReference) PArguments.getArgument(frame, 2);
            long startTime = 0;
            long middleTime = 0;
            final int n = handleReferences.length;
            boolean loggable = LOGGER.isLoggable(Level.FINE);

            if (loggable) {
                startTime = System.currentTimeMillis();
            }

            GraalHPyContext context = PythonContext.get(this).getHPyContext();

            if (CompilerDirectives.inInterpreter()) {
                com.oracle.truffle.api.nodes.LoopNode.reportLoopCount(this, n);
            }

            // mark queued references as cleaned
            for (int i = 0; i < n; i++) {
                handleReferences[i].cleaned = true;
            }

            // remove marked references from the global reference list such that they can die
            GraalHPyHandleReference prev = null;
            for (GraalHPyHandleReference cur = refList; cur != null; cur = cur.next) {
                if (cur.cleaned) {
                    if (prev != null) {
                        prev.next = cur.next;
                    } else {
                        // new head
                        refList = cur.next;
                    }
                } else {
                    prev = cur;
                }
            }

            /*
             * Merge the received reference list into the existing one or just take it if there
             * wasn't one before.
             */
            if (prev != null) {
                // if prev exists, it now points to the tail
                prev.next = oldRefList;
            } else {
                refList = oldRefList;
            }

            if (loggable) {
                middleTime = System.currentTimeMillis();
            }

            NativeSpaceArrayWrapper nativeSpaceArrayWrapper = new NativeSpaceArrayWrapper(handleReferences);
            if (callBulkFree == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBulkFree = insert(PCallHPyFunctionNodeGen.create());
            }
            callBulkFree.call(context, GraalHPyNativeSymbol.GRAAL_HPY_BULK_FREE, nativeSpaceArrayWrapper, nativeSpaceArrayWrapper.getArraySize());

            if (loggable) {
                final long countDuration = middleTime - startTime;
                final long duration = System.currentTimeMillis() - middleTime;
                LOGGER.fine(PythonUtils.formatJString("Cleaned references: %d", n));
                LOGGER.fine(PythonUtils.formatJString("Count duration: %d", countDuration));
                LOGGER.fine(PythonUtils.formatJString("Duration: %d", duration));
            }
            return refList;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public String getName() {
            return "hpy_native_reference_cleaner";
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
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

    void setWcharSize(long wcharSize) {
        assert this.wcharSize == -1 : "setting wchar size a second time";
        this.wcharSize = wcharSize;
    }

    public long getWcharSize() {
        assert this.wcharSize >= 0 : "wchar size is not available";
        return wcharSize;
    }

    /**
     * Equivalent of {@code hpy_debug_get_ctx}. In fact, this method is called from the native
     * {@code hpy_jni.c: hpy_debug_get_ctx} function to get the debug context's pointer via JNI. So,
     * if you change the name of this function, also modify {@code hpy_jni.c} appropriately.
     */
    public long getHPyDebugContext() {
        if (hPyDebugContext == 0) {
            CompilerDirectives.transferToInterpreter();
            toNative();
            long debugCtxPtr = initJNIDebugContext(castLong(nativePointer));
            if (debugCtxPtr == 0) {
                throw new RuntimeException("Could not initialize HPy debug context");
            }
            hPyDebugContext = debugCtxPtr;
        }
        return hPyDebugContext;
    }

    @TruffleBoundary
    public PythonModule getHPyDebugModule() {
        toNative();
        long debugCtxPtr = initJNIDebugModule(castLong(nativePointer));
        if (debugCtxPtr == 0) {
            throw new RuntimeException("Could not initialize HPy debug module");
        }
        int handle = GraalHPyBoxing.unboxHandle(debugCtxPtr);
        Object nativeDebugModule = getObjectForHPyHandle(handle);
        releaseHPyHandleForObject(handle);
        if (!(nativeDebugModule instanceof PythonModule)) {
            throw new RuntimeException("Debug module is expected to be a Python module object");
        }
        return (PythonModule) nativeDebugModule;
    }

    boolean isDebugMode() {
        return debugMode;
    }

    @ExportMessage
    boolean isPointer() {
        return nativePointer != null;
    }

    @ExportMessage(limit = "1")
    @SuppressWarnings("static-method")
    long asPointer(
                    @CachedLibrary("this.nativePointer") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        if (isPointer()) {
            return interopLibrary.asPointer(nativePointer);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnsupportedMessageException.create();
    }

    private Object setNativeSpaceFunction;

    /**
     * Encodes a long value such that it responds to {@link InteropLibrary#isPointer(Object)}
     * messages.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class HPyContextNativePointer implements TruffleObject {

        private final long pointer;

        public HPyContextNativePointer(long pointer) {
            this.pointer = pointer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long asPointer() {
            return pointer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        void toNative() {
            // nothing to do
        }
    }

    private static long castLong(Object value) {
        try {
            return value instanceof Long ? (long) value : InteropLibrary.getUncached().asPointer(value);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("cannot cast " + value);
        }
    }

    private static Object evalNFI(PythonContext context, String source, String name) {
        Source src = Source.newBuilder("nfi", source, name).build();
        CallTarget ct = context.getEnv().parseInternal(src);
        return ct.call();
    }

    @ExportMessage
    void toNative() {
        if (!isPointer()) {
            CompilerDirectives.transferToInterpreter();
            if (!getContext().getEnv().isNativeAccessAllowed()) {
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            nativePointer = PCallHPyFunctionNodeGen.getUncached().call(this, GRAAL_HPY_CONTEXT_TO_NATIVE, this, new GraalHPyJNIContext(this));
            PythonLanguage language = PythonLanguage.get(null);
            if (language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI) {
                loadJNIBackend();
                if (initJNI(this, castLong(nativePointer)) != 0) {
                    throw new RuntimeException("Could not initialize HPy JNI backend.");
                }
                /*
                 * Currently, the native fast path functions are only available if the JNI backend
                 * is used because they rely on 'initJNI' being called. In future, we might also
                 * want to use the native fast path functions for the NFI backend.
                 */
                if (useNativeFastPaths) {
                    PythonContext context = getContext();
                    InteropLibrary interop = InteropLibrary.getUncached();
                    SignatureLibrary signatures = SignatureLibrary.getUncached();
                    try {
                        Object rlib = evalNFI(context, "load \"" + getJNILibrary() + "\"", "load " + PythonContext.J_PYTHON_JNI_LIBRARY_NAME);

                        Object augmentSignature = evalNFI(context, "(POINTER):VOID", "hpy-nfi-signature");
                        Object augmentFunction = interop.readMember(rlib, "initDirectFastPaths");
                        signatures.call(augmentSignature, augmentFunction, nativePointer);

                        Object setNativeSpaceSignature = evalNFI(context, "(POINTER, SINT64):VOID", "hpy-nfi-signature");
                        setNativeSpaceFunction = signatures.bind(setNativeSpaceSignature, interop.readMember(rlib, "setHPyContextNativeSpace"));

                        /*
                         * Allocate a native array for the native space pointers of HPy objects and
                         * initialize it.
                         */
                        allocateNativeSpacePointersMirror();

                        interop.execute(setNativeSpaceFunction, nativePointer, nativeSpacePointers);
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }
        }
    }

    private static boolean jniBackendLoaded = false;

    private String getJNILibrary() {
        CompilerAsserts.neverPartOfCompilation();
        return Paths.get(getContext().getJNIHome().toJavaStringUncached(), PythonContext.J_PYTHON_JNI_LIBRARY_NAME).toString();
    }

    private void loadJNIBackend() {
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

    /* HPy universal mode: JNI trampoline declarations */

    @TruffleBoundary
    public static native long executePrimitive1(long target, long arg1);

    @TruffleBoundary
    public static native long executePrimitive2(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native long executePrimitive3(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executePrimitive4(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native long executePrimitive5(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeInquiry(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native int executeSsizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeSsizesizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeObjobjproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native int executeObjobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeInitproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native void executeFreefunc(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native int executeGetbufferproc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    public static native void executeReleasebufferproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executeRichcomparefunc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    public static native void executeDestructor(long target, long arg1, long arg2);

    /* HPy debug mode: JNI trampoline declarations */

    @TruffleBoundary
    public static native long executeDebugModuleInit(long target, long arg1);

    @TruffleBoundary
    public static native long executeDebugUnaryFunc(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native long executeDebugLenFunc(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native long executeDebugBinaryFunc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executeDebugGetattrFunc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executeDebugVarargs(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native long executeDebugTernaryFunc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native long executeDebugSsizeSsizeArgFunc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native long executeDebugKeywords(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeDebugInquiry(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native int executeDebugSsizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeDebugSsizesizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeDebugSetter(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeDebugObjobjproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native int executeDebugObjobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeDebugSetattrFunc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeDebugInitproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeDebugGetbufferproc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    public static native void executeDebugReleasebufferproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executeDebugRichcomparefunc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    public static native void executeDebugDestructor(long target, long arg1, long arg2);

    /* HPY internal JNI trampoline declarations */

    @TruffleBoundary
    private static native int initJNI(GraalHPyContext context, long nativePointer);

    @TruffleBoundary
    private static native long initJNIDebugContext(long uctxPointer);

    @TruffleBoundary
    private static native int finalizeJNIDebugContext(long dctxPointer);

    @TruffleBoundary
    private static native long initJNIDebugModule(long uctxPointer);

    @TruffleBoundary
    static native void hpyCallDestroyFunc(long nativeSpace, long destroyFunc);

    public enum Counter {
        UpcallCast,
        UpcallNew,
        UpcallTypeGenericNew,
        UpcallTrackerClose,
        UpcallTrackerAdd,
        UpcallClose,
        UpcallBulkClose,
        UpcallTrackerNew,
        UpcallGetItemI,
        UpcallSetItem,
        UpcallSetItemI,
        UpcallDup,
        UpcallNumberCheck,
        UpcallTypeCheck,
        UpcallLength,
        UpcallListCheck,
        UpcallLongAsLong,
        UpcallLongAsDouble,
        UpcallLongFromLong,
        UpcallFloatAsDouble,
        UpcallFloatFromDouble,
        UpcallUnicodeFromWideChar,
        UpcallUnicodeFromJCharArray,
        UpcallDictNew,
        UpcallListNew,
        UpcallTupleFromArray,
        UpcallFieldLoad,
        UpcallFieldStore,
        UpcallGlobalLoad,
        UpcallGlobalStore;

        @CompilationFinal(dimensions = 1) private static final Counter[] VALUES = values();
    }

    private void increment(Counter upcall) {
        if (traceJNIUpcalls) {
            jniCounts[upcall.ordinal()]++;
        }
    }

    @SuppressWarnings("static-method")
    public long ctxFloatFromDouble(double value) {
        increment(Counter.UpcallFloatFromDouble);
        return GraalHPyBoxing.boxDouble(value);
    }

    public double ctxFloatAsDouble(long handle) {
        increment(Counter.UpcallFloatAsDouble);

        if (GraalHPyBoxing.isBoxedDouble(handle)) {
            return GraalHPyBoxing.unboxDouble(handle);
        } else if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return PyFloatAsDoubleNodeGen.getUncached().execute(null, object);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
                return -1.0;
            }
        }
    }

    public long ctxLongAsLong(long handle) {
        increment(Counter.UpcallLongAsLong);

        if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return (long) AsNativePrimitiveNodeGen.getUncached().execute(object, 1, java.lang.Long.BYTES, true);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
                return -1L;
            }
        }
    }

    public double ctxLongAsDouble(long handle) {
        increment(Counter.UpcallLongAsDouble);

        if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            try {
                return (double) PyLongAsDoubleNodeGen.getUncached().execute(object);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
                return -1L;
            }
        }
    }

    public long ctxLongFromLong(long l) {
        increment(Counter.UpcallLongFromLong);

        if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(l)) {
            return GraalHPyBoxing.boxInt((int) l);
        }
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(l));
    }

    public long ctxAsStruct(long handle) {
        increment(Counter.UpcallCast);

        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
        return (long) HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver);
    }

    public long ctxNew(long typeHandle, long dataOutVar) {
        increment(Counter.UpcallNew);

        Object type = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));
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
                long dataPtr = unsafe.allocateMemory(basicSize);
                unsafe.setMemory(dataPtr, basicSize, (byte) 0);
                unsafe.putLong(dataOutVar, dataPtr);
                pythonObject = slowPathFactory.createPythonHPyObject(clazz, dataPtr);
                Object destroyFunc = clazz.hpyDestroyFunc;
                createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
        } else {
            // check if argument is still a type (e.g. a built-in type, ...)
            if (!IsTypeNode.getUncached().execute(type)) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(this, 0, PythonBuiltinClassType.TypeError, ErrorMessages.HPY_NEW_ARG_1_MUST_BE_A_TYPE);
            }
            // TODO(fa): this should actually call __new__
            pythonObject = slowPathFactory.createPythonObject(type);
        }
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(pythonObject));
    }

    public long ctxTypeGenericNew(long typeHandle) {
        increment(Counter.UpcallTypeGenericNew);

        Object type = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));

        if (type instanceof PythonClass) {
            PythonClass clazz = (PythonClass) type;

            PythonObject pythonObject;
            long basicSize = clazz.basicSize;
            if (basicSize != -1) {
                // allocate native space
                long dataPtr = unsafe.allocateMemory(basicSize);
                unsafe.setMemory(dataPtr, basicSize, (byte) 0);
                pythonObject = slowPathFactory.createPythonHPyObject(clazz, dataPtr);
            } else {
                pythonObject = slowPathFactory.createPythonObject(clazz);
            }
            return GraalHPyBoxing.boxHandle(getHPyHandleForObject(pythonObject));
        }
        throw CompilerDirectives.shouldNotReachHere("not implemented");
    }

    /**
     * Close a native handle received from a JNI upcall (hence represented by a Java {code long}).
     */
    private void closeNativeHandle(long handle) {
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(handle));
        }
    }

    public void ctxClose(long handle) {
        increment(Counter.UpcallClose);
        closeNativeHandle(handle);
    }

    public void ctxBulkClose(long unclosedHandlePtr, int size) {
        increment(Counter.UpcallBulkClose);
        for (int i = 0; i < size; i++) {
            long handle = unsafe.getLong(unclosedHandlePtr);
            unclosedHandlePtr += 8;
            assert GraalHPyBoxing.isBoxedHandle(handle);
            assert handle >= IMMUTABLE_HANDLE_COUNT;
            releaseHPyHandleForObject(GraalHPyBoxing.unboxHandle(handle));
        }
    }

    public long ctxDup(long handle) {
        increment(Counter.UpcallDup);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object delegate = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            return GraalHPyBoxing.boxHandle(getHPyHandleForObject(delegate));
        } else {
            return handle;
        }
    }

    public long ctxGetItemi(long hCollection, long lidx) {
        increment(Counter.UpcallGetItemI);
        try {
            // If handle 'hCollection' is a boxed int or double, the object is not subscriptable.
            if (!GraalHPyBoxing.isBoxedHandle(hCollection)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hCollection));
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
                    return GraalHPyBoxing.boxHandle(getHPyHandleForObject(lresult));
                } else if (storage instanceof ObjectSequenceStorage) {
                    Object result = ((ObjectSequenceStorage) storage).getItemNormalized(idx);
                    if (result instanceof Integer) {
                        return GraalHPyBoxing.boxInt((int) result);
                    } else if (result instanceof Double) {
                        return GraalHPyBoxing.boxDouble((double) result);
                    }
                    return GraalHPyBoxing.boxHandle(getHPyHandleForObject(result));
                }
                // TODO: other storages...
            }
            Object result = PInteropSubscriptNode.getUncached().execute(receiver, lidx);
            return GraalHPyBoxing.boxHandle(getHPyHandleForObject(result));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
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
        increment(Counter.UpcallSetItem);
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence));
            Object clazz = GetClassNode.getUncached().execute(receiver);
            Object key = HPyAsPythonObjectNodeGen.getUncached().execute(this, hKey);
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(this, hValue);

            // fast path
            if (clazz == PythonBuiltinClassType.PDict) {
                PDict dict = (PDict) receiver;
                HashingStorage dictStorage = dict.getDictStorage();

                // super-fast path for string keys
                if (key instanceof TruffleString) {
                    if (dictStorage instanceof EmptyStorage) {
                        dictStorage = PDict.createNewStorage(true, 1);
                        dict.setDictStorage(dictStorage);
                    }

                    if (dictStorage instanceof HashMapStorage) {
                        ((HashMapStorage) dictStorage).put((TruffleString) key, value);
                        return 0;
                    }
                    // fall through to generic case
                }
                dict.setDictStorage(HashingStorageLibrary.getUncached().setItem(dictStorage, key, value));
                return 0;
            } else if (clazz == PythonBuiltinClassType.PList && PGuards.isInteger(key) && ctxListSetItem(receiver, ((Number) key).longValue(), hValue)) {
                return 0;
            }
            return setItemGeneric(receiver, clazz, key, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            // non-null value indicates an error
            return -1;
        }
    }

    public int ctxSetItemi(long hSequence, long lidx, long hValue) {
        increment(Counter.UpcallSetItemI);
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence));
            Object clazz = GetClassNode.getUncached().execute(receiver);

            if (clazz == PythonBuiltinClassType.PList && ctxListSetItem(receiver, lidx, hValue)) {
                return 0;
            }
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(this, hValue);
            return setItemGeneric(receiver, clazz, lidx, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
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
            Object value = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hValue));
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
        increment(Counter.UpcallNumberCheck);
        if (GraalHPyBoxing.isBoxedDouble(handle) || GraalHPyBoxing.isBoxedInt(handle)) {
            return 1;
        }
        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));

        try {
            if (PyIndexCheckNodeGen.getUncached().execute(receiver) || CanBeDoubleNodeGen.getUncached().execute(receiver)) {
                return 1;
            }
            Object receiverType = GetClassNode.getUncached().execute(receiver);
            return PInt.intValue(LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Int).execute(receiverType) != PNone.NO_VALUE);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            return 0;
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

    public int ctxTypeCheck(long handle, long typeHandle) {
        increment(Counter.UpcallTypeCheck);
        Object receiver;
        if (GraalHPyBoxing.isBoxedDouble(handle)) {
            receiver = PythonBuiltinClassType.PFloat;
        } else if (GraalHPyBoxing.isBoxedInt(handle)) {
            receiver = PythonBuiltinClassType.PInt;
        } else {
            receiver = GetClassNode.getUncached().execute(getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)));
        }
        Object type = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle));

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
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            return 0;
        }
    }

    public long ctxLength(long handle) {
        increment(Counter.UpcallLength);
        assert GraalHPyBoxing.isBoxedHandle(handle);

        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));

        Object clazz = GetClassNode.getUncached().execute(receiver);
        if (clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple) {
            PSequence sequence = (PSequence) receiver;
            SequenceStorage storage = sequence.getSequenceStorage();
            return storage.length();
        }
        try {
            return PyObjectSizeNodeGen.getUncached().execute(null, receiver);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            return -1;
        }
    }

    public int ctxListCheck(long handle) {
        increment(Counter.UpcallListCheck);
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object obj = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            Object clazz = GetClassNode.getUncached().execute(obj);
            return PInt.intValue(clazz == PythonBuiltinClassType.PList || IsSubtypeNodeGen.getUncached().execute(clazz, PythonBuiltinClassType.PList));
        } else {
            return 0;
        }
    }

    public long ctxUnicodeFromWideChar(long wcharArrayPtr, long size) {
        increment(Counter.UpcallUnicodeFromWideChar);

        if (!PInt.isIntRange(size)) {
            // NULL handle
            return 0;
        }
        int isize = (int) size;
        // TODO GR-37216: use TruffleString.FromNativePointer?
        char[] decoded = new char[isize];
        for (int i = 0; i < size; i++) {
            int wchar = unsafe.getInt(wcharArrayPtr + (long) Integer.BYTES * i);
            if (Character.isBmpCodePoint(wchar)) {
                decoded[i] = (char) wchar;
            } else {
                // TODO(fa): handle this case
                throw new RuntimeException();
            }
        }
        TruffleString result = toTruffleStringUncached(new String(decoded, 0, isize));
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(result));
    }

    public long ctxUnicodeFromJCharArray(char[] arr) {
        increment(Counter.UpcallUnicodeFromJCharArray);
        TruffleString string = TruffleString.fromCharArrayUTF16Uncached(arr).switchEncodingUncached(TS_ENCODING);
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(string));
    }

    public long ctxDictNew() {
        increment(Counter.UpcallDictNew);
        PDict dict = getSlowPathFactory().createDict();
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(dict));
    }

    public long ctxListNew(long llen) {
        try {
            increment(Counter.UpcallListNew);
            int len = CastToJavaIntExactNode.getUncached().execute(llen);
            Object[] data = new Object[len];
            Arrays.fill(data, PNone.NONE);
            PList list = getSlowPathFactory().createList(data);
            return GraalHPyBoxing.boxHandle(getHPyHandleForObject(list));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
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
        increment(Counter.UpcallTupleFromArray);

        Object[] objects = new Object[hItems.length];
        for (int i = 0; i < hItems.length; i++) {
            long hBits = hItems[i];
            objects[i] = HPyAsPythonObjectNodeGen.getUncached().execute(this, hBits);
            if (steal) {
                closeNativeHandle(hBits);
            }
        }
        PTuple tuple = getSlowPathFactory().createTuple(objects);
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(tuple));
    }

    public long ctxFieldLoad(long bits, long idx) {
        increment(Counter.UpcallFieldLoad);
        Object owner = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object referent = ((PythonObject) owner).getHpyFields()[(int) idx - 1];
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(referent));
    }

    public long ctxFieldStore(long bits, long idx, long value) {
        increment(Counter.UpcallFieldStore);
        PythonObject owner = (PythonObject) getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        Object referent = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(value));

        Object[] hpyFields = owner.getHpyFields();
        if (idx == 0) {
            if (hpyFields == null) {
                idx = 1;
                hpyFields = new Object[]{referent};
            } else {
                idx = hpyFields.length + 1;
                hpyFields = PythonUtils.arrayCopyOf(hpyFields, (int) idx);
                hpyFields[(int) idx - 1] = referent;
            }
            owner.setHpyFields(hpyFields);
        } else {
            hpyFields[(int) idx - 1] = referent;
        }
        return idx;
    }

    public long ctxGlobalLoad(long bits) {
        increment(Counter.UpcallGlobalLoad);
        assert GraalHPyBoxing.isBoxedHandle(bits);
        return GraalHPyBoxing.boxHandle(getHPyHandleForObject(getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getDelegate()));
    }

    public long ctxGlobalStore(long bits, long v) {
        increment(Counter.UpcallGlobalStore);
        Object value = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(v));
        int idx = 0;
        if (bits > 0) {
            idx = getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getGlobalId();
        }
        GraalHPyHandle newHandle = createGlobal(value, idx);
        return newHandle.getGlobalId();
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
        static boolean isMemberReadableCached(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached(value = "getIndex(fromJavaStringNode.execute(key, TS_ENCODING))") int cachedIdx) {
            return cachedIdx != -1;
        }

        @Specialization(replaces = "isMemberReadableCached")
        static boolean isMemberReadable(@SuppressWarnings("unused") GraalHPyContext context, String key,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return HPyContextMember.getIndex(fromJavaStringNode.execute(key, TS_ENCODING)) != -1;
        }
    }

    @ExportMessage
    Object readMember(String key,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return readMemberNode.execute(this, fromJavaStringNode.execute(key, TS_ENCODING));
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

        public abstract Object execute(GraalHPyContext hpyContext, TruffleString key);

        @Specialization(guards = "eqNode.execute(cachedKey, key, TS_ENCODING)", limit = "1")
        static Object doMemberCached(GraalHPyContext hpyContext, TruffleString key,
                        @Cached(value = "key") @SuppressWarnings("unused") TruffleString cachedKey,
                        @Shared("eq") @Cached TruffleString.EqualNode eqNode,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
            // TODO(fa) once everything is implemented, remove this check
            if (cachedIdx != -1) {
                Object value = hpyContext.hpyContextMembers[cachedIdx];
                if (value != null) {
                    return value;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(PythonUtils.formatJString("context function %s not yet implemented: ", key));
        }

        @Specialization(replaces = "doMemberCached")
        static Object doMember(GraalHPyContext hpyContext, TruffleString key,
                        @Cached(value = "key", allowUncached = true) @SuppressWarnings("unused") TruffleString cachedKey,
                        @Shared("eq") @Cached TruffleString.EqualNode eqNode) {
            return doMemberCached(hpyContext, key, key, eqNode, HPyContextMember.getIndex(key));
        }
    }

    @ExportMessage
    boolean isMemberInvocable(String key,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib) {
        Object member = readMemberNode.execute(this, fromJavaStringNode.execute(key, TS_ENCODING));
        return member != null && memberInvokeLib.isExecutable(member);
    }

    @ExportMessage
    Object invokeMember(String key, Object[] args,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        Object member = readMemberNode.execute(this, fromJavaStringNode.execute(key, TS_ENCODING));
        assert member != null;
        /*
         * Optimization: the first argument *MUST* always be the context. If not, we can just set
         * 'this'.
         */
        args[0] = this;
        return memberInvokeLib.execute(member, args);
    }

    private static Object[] createMembers(PythonContext context, TruffleString name, boolean traceJNIUpcalls) {
        Object[] members = new Object[HPyContextMember.VALUES.length];

        members[HPyContextMember.NAME.ordinal()] = new CStringWrapper(name);
        createIntConstant(members, HPyContextMember.CTX_VERSION, 1);

        createConstant(members, HPyContextMember.H_NONE, PNone.NONE);
        createConstant(members, HPyContextMember.H_TRUE, context.getTrue());
        createConstant(members, HPyContextMember.H_FALSE, context.getFalse());
        createConstant(members, HPyContextMember.H_NOTIMPLEMENTED, PNotImplemented.NOT_IMPLEMENTED);
        createConstant(members, HPyContextMember.H_ELLIPSIS, PEllipsis.INSTANCE);

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
        createTypeConstant(members, HPyContextMember.H_SYSTEMERROR, context, PythonBuiltinClassType.SystemError);
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
        members[HPyContextMember.CTX_IS.ordinal()] = new GraalHPyIs();
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
        members[HPyContextMember.CTX_HASH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_HASH, 1, ReturnType.INT, GraalHPyConversionNodeSupplier.TO_INT64);
        members[HPyContextMember.CTX_NUMBER_CHECK.ordinal()] = new GraalHPyIsNumber();
        members[HPyContextMember.CTX_LENGTH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.T_LEN, 1, ReturnType.INT, GraalHPyConversionNodeSupplier.TO_INT64);
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

        if (traceJNIUpcalls) {
            for (int i = 0; i < members.length; i++) {
                Object m = members[i];
                if (m != null && !(m instanceof Number || m instanceof GraalHPyHandle)) {
                    // wrap
                    members[i] = new HPyExecuteWrapper(i, m);
                }
            }
        }

        return members;
    }

    static final int[] counts = new int[HPyContextMember.VALUES.length];
    static final int[] jniCounts = new int[Counter.values().length];

    private static void startUpcallsDaemon(int traceJNISleepTime) {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(traceJNISleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("========= HPy LLVM/NFI upcall counts");
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] != 0) {
                        System.out.printf("  %40s[%3d]: %d\n", HPyContextMember.VALUES[i].name, i, counts[i]);
                    }
                }
                System.out.println("====  HPy JNI upcall counts");
                for (int i = 0; i < jniCounts.length; i++) {
                    if (jniCounts[i] != 0) {
                        System.out.printf("  %40s[%3d]: %d\n", Counter.VALUES[i].name(), i, jniCounts[i]);
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    public static final class HPyExecuteWrapper implements TruffleObject {

        final Object delegate;
        final int index;

        public HPyExecuteWrapper(int index, Object delegate) {
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

    private static void createIntConstant(Object[] members, HPyContextMember member, int value) {
        members[member.ordinal()] = value;
    }

    private static void createConstant(Object[] members, HPyContextMember member, Object value) {
        members[member.ordinal()] = GraalHPyHandle.create(value);
    }

    private static void createTypeConstant(Object[] members, HPyContextMember member, Python3Core core, PythonBuiltinClassType value) {
        members[member.ordinal()] = GraalHPyHandle.create(core.lookupType(value));
    }

    public GraalHPyHandle createHandle(Object delegate) {
        return GraalHPyHandle.create(delegate);
    }

    public GraalHPyHandle createField(Object delegate, int idx) {
        return GraalHPyHandle.createField(delegate, idx);
    }

    public GraalHPyHandle createGlobal(Object delegate, int idx) {
        assert !GilNode.getUncached().acquire(PythonContext.get(null)) : "Gil not held when creating global";
        final int newIdx;
        if (idx <= 0) {
            newIdx = allocateHPyGlobal();
        } else {
            newIdx = idx;
        }
        GraalHPyHandle h = GraalHPyHandle.createGlobal(delegate, newIdx);
        hpyGlobalsTable[newIdx] = h;
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(PythonUtils.formatJString("allocating HPy global %d (object: %s)", newIdx, delegate));
        }
        return h;
    }

    private int allocateHPyGlobal() {
        int handle = 0;
        for (int i = 1; i < hpyGlobalsTable.length; i++) {
            if (hpyGlobalsTable[i] == null) {
                handle = i;
                break;
            }
        }
        if (handle == 0) {
            // resize
            handle = hpyGlobalsTable.length;
            int newSize = Math.max(16, hpyGlobalsTable.length * 2);
            LOGGER.fine(() -> "resizing HPy globals table to " + newSize);
            hpyGlobalsTable = Arrays.copyOf(hpyGlobalsTable, newSize);
            // TODO: (tfel) mirror these to native as we do for handles? not sure if it pays
        }
        return handle;
    }

    private static Unsafe unsafe = CArrayWrappers.UNSAFE;

    private long nativeSpacePointers;

    private int resizeHandleTable() {
        CompilerAsserts.neverPartOfCompilation();
        assert nextHandle == hpyHandleTable.length;
        int newSize = Math.max(16, hpyHandleTable.length * 2);
        LOGGER.fine(() -> "resizing HPy handle table to " + newSize);
        hpyHandleTable = Arrays.copyOf(hpyHandleTable, newSize);
        if (useNativeFastPaths && isPointer()) {
            reallocateNativeSpacePointersMirror();
        }
        return nextHandle++;
    }

    public int getHPyHandleForObject(Object object) {
        assert !(object instanceof GraalHPyHandle);
        // find free association

        int handle = freeStack.pop();
        if (handle == -1) {
            if (nextHandle < hpyHandleTable.length) {
                handle = nextHandle++;
            } else {
                CompilerDirectives.transferToInterpreter();
                handle = resizeHandleTable();
            }
        }

        assert 0 <= handle && handle < hpyHandleTable.length;
        assert hpyHandleTable[handle] == null;

        hpyHandleTable[handle] = object;
        if (useNativeFastPaths && isPointer()) {
            mirrorNativeSpacePointerToNative(object, handle);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(PythonUtils.formatJString("allocating HPy handle %d (object: %s)", handle, object));
        }
        return handle;
    }

    @TruffleBoundary
    private void mirrorNativeSpacePointerToNative(Object delegate, int handleID) {
        assert isPointer();
        assert useNativeFastPaths;
        Object nativeSpace = HPyGetNativeSpacePointerNodeGen.getUncached().execute(delegate);
        try {
            long l = nativeSpace instanceof Long ? ((long) nativeSpace) : nativeSpace == PNone.NO_VALUE ? 0 : InteropLibrary.getUncached().asPointer(nativeSpace);
            unsafe.putLong(nativeSpacePointers + handleID * SIZEOF_LONG, l);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    private void reallocateNativeSpacePointersMirror() {
        assert isPointer();
        assert useNativeFastPaths;
        nativeSpacePointers = unsafe.reallocateMemory(nativeSpacePointers, hpyHandleTable.length * SIZEOF_LONG);
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, nativeSpacePointers);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Allocates a native array (element size is {@link #SIZEOF_LONG} for as many elements as in
     * {@link #hpyHandleTable} and writes the native space pointers of all objects in the handle
     * table into this array. The pointer of the array is then set to
     * {@code ((HPyContext) ctx)->_private} and meant to be used by the {@code ctx_Cast}'s upcall
     * stub to avoid an expensive upcall.
     */
    @TruffleBoundary
    private void allocateNativeSpacePointersMirror() {
        long arraySize = hpyHandleTable.length * SIZEOF_LONG;
        long arrayPtr = unsafe.allocateMemory(arraySize);
        unsafe.setMemory(arrayPtr, arraySize, (byte) 0);

        // publish pointer value (needed for initialization)
        nativeSpacePointers = arrayPtr;

        // write existing values to mirror; start at 1 to omit the NULL handle
        for (int i = 1; i < hpyHandleTable.length; i++) {
            Object delegate = hpyHandleTable[i];
            if (delegate != null) {
                mirrorNativeSpacePointerToNative(delegate, i);
            }
        }

        // commit pointer value for native usage
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, arrayPtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public Object getObjectForHPyHandle(int handle) {
        assert !GilNode.getUncached().acquire(PythonContext.get(null)) : "Gil not held when resolving object from handle";
        assert !GraalHPyBoxing.isBoxedInt(handle) && !GraalHPyBoxing.isBoxedDouble(handle) : "trying to lookup boxed primitive";
        return hpyHandleTable[handle];
    }

    public GraalHPyHandle getObjectForHPyGlobal(int handle) {
        assert !GilNode.getUncached().acquire(PythonContext.get(null)) : "Gil not held when resolving object from global";
        assert !GraalHPyBoxing.isBoxedInt(handle) && !GraalHPyBoxing.isBoxedDouble(handle) : "trying to lookup boxed primitive";
        return hpyGlobalsTable[handle];
    }

    boolean releaseHPyHandleForObject(int handle) {
        assert !GilNode.getUncached().acquire(PythonContext.get(null)) : "Gil not held when releasing handle";
        assert handle != 0 : "NULL handle cannot be released";
        assert hpyHandleTable[handle] != null : PythonUtils.formatJString("releasing handle that has already been released: %d", handle);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(PythonUtils.formatJString("releasing HPy handle %d (object: %s)", handle, hpyHandleTable[handle]));
        }
        if (handle < IMMUTABLE_HANDLE_COUNT) {
            return false;
        }
        hpyHandleTable[handle] = null;
        freeStack.push(handle);
        return true;
    }

    void onInvalidHandle(@SuppressWarnings("unused") int id) {
        // nothing to do in the universal context
    }

    private static final class HandleStack {
        private int[] handles;
        private int top = 0;

        public HandleStack(int initialCapacity) {
            handles = new int[initialCapacity];
        }

        void push(int i) {
            if (top >= handles.length) {
                handles = Arrays.copyOf(handles, handles.length * 2);
            }
            handles[top++] = i;
        }

        int pop() {
            if (top <= 0) {
                return -1;
            }
            return handles[--top];
        }
    }

    /**
     * A weak reference to an object that has an associated HPy native space (
     * {@link PythonHPyObject}).
     */
    static final class GraalHPyHandleReference extends WeakReference<Object> {

        private final Object nativeSpace;
        private final Object destroyFunc;

        boolean cleaned;
        private GraalHPyHandleReference next;

        public GraalHPyHandleReference(Object referent, ReferenceQueue<Object> q, Object nativeSpace, Object destroyFunc) {
            super(referent, q);
            this.nativeSpace = nativeSpace;
            this.destroyFunc = destroyFunc;
        }

        public Object getNativeSpace() {
            return nativeSpace;
        }

        public Object getDestroyFunc() {
            return destroyFunc;
        }

        public GraalHPyHandleReference getNext() {
            return next;
        }

        public void setNext(GraalHPyHandleReference next) {
            this.next = next;
        }
    }

    /**
     * Registers an HPy native space of a Python object.<br/>
     * Use this method to register a native memory that is associated with a Python object in order
     * to ensure that the native memory will be free'd when the owning Python object dies.<br/>
     * This works by creating a weak reference to the Python object, using a thread that
     * concurrently polls the reference queue. If threading is allowed, cleaning will be done fully
     * concurrent on a cleaner thread. If not, an async action will be scheduled to free the native
     * memory. Hence, the destroy function could also be executed on the cleaner thread.
     *
     * @param pythonObject The Python object that has associated native memory.
     * @param dataPtr The pointer object of the native memory.
     * @param destroyFunc The destroy function to call when the Python object is unreachable (may be
     *            {@code null}; in this case, bare {@code free} will be used).
     */
    @TruffleBoundary
    void createHandleReference(Object pythonObject, Object dataPtr, Object destroyFunc) {
        GraalHPyHandleReference newHead = new GraalHPyHandleReference(pythonObject, ensureReferenceQueue(), dataPtr, destroyFunc);
        references.getAndAccumulate(newHead, (prev, x) -> {
            x.next = prev;
            return x;
        });
    }

    private ReferenceQueue<Object> ensureReferenceQueue() {
        if (nativeSpaceReferenceQueue == null) {
            ReferenceQueue<Object> referenceQueue = createReferenceQueue();
            nativeSpaceReferenceQueue = referenceQueue;
            return referenceQueue;
        }
        return nativeSpaceReferenceQueue;
    }

    @TruffleBoundary
    private ReferenceQueue<Object> createReferenceQueue() {
        final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        // lazily register the runnable that concurrently collects the queued references
        Env env = getContext().getEnv();
        if (env.isCreateThreadAllowed()) {
            Thread thread = env.createThread(new GraalHPyReferenceCleanerRunnable(referenceQueue), null, getContext().getThreadGroup());
            // Make the cleaner thread a daemon; it should not prevent JVM shutdown.
            thread.setDaemon(true);
            thread.start();
            hpyReferenceCleanerThread = thread;
        } else {
            getContext().registerAsyncAction(() -> {
                Reference<?> reference = null;
                try {
                    reference = referenceQueue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                ArrayList<GraalHPyHandleReference> refs = new ArrayList<>();
                do {
                    if (reference instanceof GraalHPyHandleReference) {
                        refs.add((GraalHPyHandleReference) reference);
                    }
                    // consume all
                    reference = referenceQueue.poll();
                } while (reference != null);

                if (!refs.isEmpty()) {
                    return new GraalHPyHandleReferenceCleanerAction(refs.toArray(new GraalHPyHandleReference[0]));
                }

                return null;
            });
        }
        return referenceQueue;
    }

    @TruffleBoundary
    @Override
    protected Store initializeSymbolCache() {
        PythonLanguage language = getContext().getLanguage();
        Shape symbolCacheShape = language.getHPySymbolCacheShape();
        // We will always get an empty shape from the language and we do always add same key-value
        // pairs (in the same order). So, in the end, each context should get the same shape.
        Store s = new Store(symbolCacheShape);
        for (GraalHPyNativeSymbol sym : GraalHPyNativeSymbol.getValues()) {
            DynamicObjectLibrary.getUncached().put(s, sym, PNone.NO_VALUE);
        }
        return s;
    }

    /**
     * Join the reference cleaner thread.
     */
    public void finalizeContext() {
        Thread thread = this.hpyReferenceCleanerThread;
        if (thread != null) {
            if (thread.isAlive() && !thread.isInterrupted()) {
                thread.interrupt();
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (hPyDebugContext != 0) {
            finalizeJNIDebugContext(hPyDebugContext);
        }
        if (nativeArgumentsStack != 0) {
            unsafe.freeMemory(nativeArgumentsStack);
        }
    }

    private static final long NATIVE_ARGUMENT_STACK_SIZE = (2 ^ 15) * SIZEOF_LONG; // 32k entries
    private long nativeArgumentsStack = 0;
    private int nativeArgumentStackPos = 0;

    public long createNativeArguments(Object[] delegate, InteropLibrary delegateLib) {
        if (nativeArgumentsStack == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeArgumentsStack = unsafe.allocateMemory(NATIVE_ARGUMENT_STACK_SIZE);
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
                unsafe.putLong(arrayPtr + i * SIZEOF_LONG, delegateLib.asPointer(element));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return arrayPtr;
    }

    public void freeNativeArgumentsArray(int size) {
        long arraySize = size * SIZEOF_LONG;
        nativeArgumentsStack -= arraySize;
    }
}
