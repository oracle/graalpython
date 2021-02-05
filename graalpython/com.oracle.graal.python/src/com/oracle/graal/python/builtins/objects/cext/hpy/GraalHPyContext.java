/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ArithmeticError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BlockingIOError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BrokenPipeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BytesWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ChildProcessError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ConnectionAbortedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ConnectionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ConnectionRefusedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ConnectionResetError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.FileExistsError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.FileNotFoundError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.FloatingPointError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.FutureWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ImportError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ImportWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndentationError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.InterruptedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IsADirectoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyboardInterrupt;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LookupError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ModuleNotFoundError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NameError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotADirectoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBaseException;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBytes;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PDict;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PInt;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PList;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PString;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTuple;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PendingDeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PermissionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ProcessLookupError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PythonClass;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PythonObject;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ReferenceError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ResourceWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopAsyncIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SyntaxError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SyntaxWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TabError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TimeoutError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnboundLocalError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeDecodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeEncodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeTranslateError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UserWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.Warning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZeroDivisionError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.ReferenceStack;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsIndex;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBinaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderBuild;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderCancel;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderSet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesAsString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesGetSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallBuiltinFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCast;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCheckBuiltinType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyClose;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictSetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrClear;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrOccurred;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrRaisePredefined;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrSetString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatAsDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatFromDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFromPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyHasAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyInplaceArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsNumber;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsTrue;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListAppend;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyModuleCreate;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyRichcompare;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTernaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerAdd;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerCleanup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTupleFromArray;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeFromSpec;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeGenericNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsUTF8String;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromWchar;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public final class GraalHPyContext extends CExtContext implements TruffleObject {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.class);

    enum HPyContextTypeMember {

    }

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMembers {
        CTX_VERSION("ctx_version"),

        // constants
        H_NONE("h_None"),
        H_TRUE("h_True"),
        H_FALSE("h_False"),

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
        H_LONGTYPE("h_LongType"),
        H_UNICODETYPE("h_UnicodeType"),
        H_TUPLETYPE("h_TupleType"),
        H_LISTTYPE("h_ListType"),

        CTX_MODULE_CREATE("ctx_Module_Create"),
        CTX_DUP("ctx_Dup"),
        CTX_CAST("ctx_Cast"),
        CTX_CLOSE("ctx_Close"),
        CTX_LONG_FROMLONG("ctx_Long_FromLong"),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong"),
        CTX_LONG_FROM_UNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong"),
        CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t"),
        CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t"),
        CTX_LONG_ASLONG("ctx_Long_AsLong"),
        CTX_NEW("ctx_New"),
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

        CTX_ERR_NOMEMORY("ctx_Err_NoMemory"),
        CTX_ERR_SETSTRING("ctx_Err_SetString"),
        CTX_ERR_SETOBJECT("ctx_Err_SetObject"),
        CTX_ERR_OCCURRED("ctx_Err_Occurred"),
        CTX_ERR_CLEAR("ctx_Err_Clear"),
        CTX_ISTRUE("ctx_IsTrue"),
        CTX_TYPE_FROM_SPEC("ctx_Type_FromSpec"),
        CTX_GETATTR("ctx_GetAttr"),
        CTX_GETATTR_S("ctx_GetAttr_s"),
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
        CTX_BYTES_CHECK("ctx_Bytes_Check"),
        CTX_BYTES_SIZE("ctx_Bytes_Size"),
        CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE"),
        CTX_BYTES_ASSTRING("ctx_Bytes_AsString"),
        CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING"),
        CTX_UNICODE_FROMSTRING("ctx_Unicode_FromString"),
        CTX_UNICODE_CHECK("ctx_Unicode_Check"),
        CTX_UNICODE_ASUTF8STRING("ctx_Unicode_AsUTF8String"),
        CTX_UNICODE_FROMWIDECHAR("ctx_Unicode_FromWideChar"),
        CTX_LIST_NEW("ctx_List_New"),
        CTX_LIST_APPEND("ctx_List_Append"),
        CTX_DICT_CHECK("ctx_Dict_Check"),
        CTX_DICT_NEW("ctx_Dict_New"),
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
        CTX_TRACKER_CLOSE("ctx_Tracker_Close");

        private final String name;

        HPyContextMembers(String name) {
            this.name = name;
        }

        @CompilationFinal(dimensions = 1) private static final HPyContextMembers[] VALUES = values();

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        public static HPyContextMembers getByName(String name) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].name.equals(name)) {
                    return VALUES[i];
                }
            }
            return null;
        }
    }

    private GraalHPyHandle[] hpyHandleTable = new GraalHPyHandle[]{GraalHPyHandle.NULL_HANDLE};
    private final HandleStack freeStack = new HandleStack(16);
    Object nativePointer;

    @CompilationFinal(dimensions = 1) private final Object[] hpyContextMembers;
    @CompilationFinal private GraalHPyHandle hpyNullHandle;

    /** the native type ID of C struct 'HPyContext' */
    @CompilationFinal private Object hpyContextNativeTypeID;

    /** the native type ID of C struct 'HPy' */
    @CompilationFinal private Object hpyNativeTypeID;
    @CompilationFinal private Object hpyArrayNativeTypeID;
    @CompilationFinal private long wcharSize = -1;

    @CompilationFinal private ReferenceQueue<Object> nativeSpaceReferenceQueue;
    @CompilationFinal private RootCallTarget referenceCleanerCallTarget;
    public final ReferenceStack<GraalHPyHandleReference> references = new ReferenceStack<>();

    public GraalHPyContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, GraalHPyConversionNodeSupplier.HANDLE);
        this.hpyContextMembers = createMembers(context);

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
     * Root node that actually runs the destroy functions for the native memory of unreachable
     * Python objects.
     */
    private static final class HPyNativeSpaceCleanerRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"refs"}, PythonUtils.EMPTY_STRING_ARRAY);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.HPyNativeSpaceCleanerRootNode.class);

        @Child private CalleeContext calleeContext;
        @Child private InteropLibrary pointerObjectLib;
        @Child private InteropLibrary destroyFunLib;
        @Child private PCallHPyFunction callBulkFree;

        @CompilationFinal private ContextReference<PythonContext> contextRef;

        protected HPyNativeSpaceCleanerRootNode(PythonContext context) {
            super(context.getLanguage());
            this.calleeContext = CalleeContext.create();
            this.callBulkFree = PCallHPyFunctionNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                GraalHPyHandleReference[] handleReferences = (GraalHPyHandleReference[]) PArguments.getArgument(frame, 0);
                int cleaned = 0;
                long startTime = 0;
                long middleTime = 0;
                final int n = handleReferences.length;
                boolean loggable = LOGGER.isLoggable(Level.FINE);

                if (loggable) {
                    startTime = System.currentTimeMillis();
                }

                GraalHPyContext context = getContext().getHPyContext();

                // it's not an OSR loop, so we do this before the loop
                if (n > 0 && pointerObjectLib == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    pointerObjectLib = insert(InteropLibrary.getFactory().create(handleReferences[0].getNativeSpace()));
                }

                for (int i = 0; i < n; i++) {
                    GraalHPyHandleReference handleRef = handleReferences[i];
                    context.references.remove(handleRef.id);
                    Object pointerObject = handleRef.getNativeSpace();
                    Object destroyFunc = handleRef.getDestroyFunc();
                    if (!pointerObjectLib.isNull(pointerObject)) {
                        LOGGER.finer(() -> "Cleaning native object reference to " + CApiContext.asHex(pointerObject));
                        cleaned++;

                        // if there is a custom destroy function, run it right now
                        if (destroyFunc != null) {
                            try {
                                ensureCallNode().execute(destroyFunc, pointerObject);

                                // now clear it to avoid another free
                                handleReferences[i] = null;
                            } catch (InteropException e) {
                                LOGGER.fine(() -> String.format("Execution of destroy function %s failed", destroyFunc));
                            }
                        }
                    }
                }

                if (loggable) {
                    middleTime = System.currentTimeMillis();
                }

                callBulkFree.call(context, GraalHPyNativeSymbol.GRAAL_HPY_BULK_FREE, new NativeSpaceArrayWrapper(handleReferences), (long) n);

                if (loggable) {
                    final long countDuration = middleTime - startTime;
                    final long duration = System.currentTimeMillis() - middleTime;
                    final int finalCleaned = cleaned;
                    LOGGER.fine(() -> "Total queued references: " + n);
                    LOGGER.fine(() -> "Cleaned references: " + finalCleaned);
                    LOGGER.fine(() -> "Count duration: " + countDuration);
                    LOGGER.fine(() -> "Duration: " + duration);
                }
            } finally {
                calleeContext.exit(frame, this);
            }
            return PNone.NONE;
        }

        private InteropLibrary ensureCallNode() {
            if (destroyFunLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                destroyFunLib = insert(InteropLibrary.getUncached());
            }
            return destroyFunLib;
        }

        private PythonContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef.get();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public String getName() {
            return "native_reference_cleaner";
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public boolean isPythonInternal() {
            return false;
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

    /** Set the global exception state. */
    public void setCurrentException(PException e) {
        getContext().setCurrentException(e);
    }

    /** Get the global exception state. */
    public PException getCurrentException() {
        return getContext().getCurrentException();
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

    @ExportMessage
    void toNative(
                    @Cached PCallHPyFunction callContextToNativeNode) {
        if (!isPointer()) {
            nativePointer = callContextToNativeNode.call(this, GRAAL_HPY_CONTEXT_TO_NATIVE, this);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @ExplodeLoop
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        String[] names = new String[HPyContextMembers.VALUES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = HPyContextMembers.VALUES[i].name;
        }
        return new PythonAbstractObject.Keys(names);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String key) {
        return HPyContextMembers.getByName(key) != null;
    }

    @ExportMessage
    Object readMember(String key,
                    @Cached GraalHPyReadMemberNode readMemberNode) {
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
    @ImportStatic(HPyContextMembers.class)
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, String key);

        @Specialization(guards = "cachedKey.equals(key)")
        static Object doMember(GraalHPyContext hpyContext, String key,
                        @Cached(value = "key", allowUncached = true) @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)", allowUncached = true) int cachedIdx) {
            // TODO(fa) once everything is implemented, remove this check
            if (cachedIdx != -1) {
                return hpyContext.hpyContextMembers[cachedIdx];
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.format("context function %s not yet implemented: ", key));
        }

        static int getIndex(String key) {
            HPyContextMembers member = HPyContextMembers.getByName(key);
            return member != null ? member.ordinal() : -1;
        }
    }

    private static Object[] createMembers(PythonContext context) {
        Object[] members = new Object[HPyContextMembers.VALUES.length];
        PythonCore core = context.getCore();

        createConstant(members, HPyContextMembers.H_NONE, PNone.NONE);
        createConstant(members, HPyContextMembers.H_TRUE, core.getTrue());
        createConstant(members, HPyContextMembers.H_FALSE, core.getFalse());

        createTypeConstant(members, HPyContextMembers.H_BASEEXCEPTION, core, PBaseException);
        createTypeConstant(members, HPyContextMembers.H_EXCEPTION, core, PythonBuiltinClassType.Exception);
        createTypeConstant(members, HPyContextMembers.H_STOPASYNCITERATION, core, StopAsyncIteration);
        createTypeConstant(members, HPyContextMembers.H_STOPITERATION, core, StopIteration);
        createTypeConstant(members, HPyContextMembers.H_GENERATOREXIT, core, GeneratorExit);
        createTypeConstant(members, HPyContextMembers.H_ARITHMETICERROR, core, ArithmeticError);
        createTypeConstant(members, HPyContextMembers.H_LOOKUPERROR, core, LookupError);
        createTypeConstant(members, HPyContextMembers.H_ASSERTIONERROR, core, PythonBuiltinClassType.AssertionError);
        createTypeConstant(members, HPyContextMembers.H_ATTRIBUTEERROR, core, AttributeError);
        createTypeConstant(members, HPyContextMembers.H_BUFFERERROR, core, BufferError);
        createTypeConstant(members, HPyContextMembers.H_EOFERROR, core, EOFError);
        createTypeConstant(members, HPyContextMembers.H_FLOATINGPOINTERROR, core, FloatingPointError);
        createTypeConstant(members, HPyContextMembers.H_OSERROR, core, OSError);
        createTypeConstant(members, HPyContextMembers.H_IMPORTERROR, core, ImportError);
        createTypeConstant(members, HPyContextMembers.H_MODULENOTFOUNDERROR, core, ModuleNotFoundError);
        createTypeConstant(members, HPyContextMembers.H_INDEXERROR, core, IndexError);
        createTypeConstant(members, HPyContextMembers.H_KEYERROR, core, KeyError);
        createTypeConstant(members, HPyContextMembers.H_KEYBOARDINTERRUPT, core, KeyboardInterrupt);
        createTypeConstant(members, HPyContextMembers.H_MEMORYERROR, core, MemoryError);
        createTypeConstant(members, HPyContextMembers.H_NAMEERROR, core, NameError);
        createTypeConstant(members, HPyContextMembers.H_OVERFLOWERROR, core, OverflowError);
        createTypeConstant(members, HPyContextMembers.H_RUNTIMEERROR, core, RuntimeError);
        createTypeConstant(members, HPyContextMembers.H_RECURSIONERROR, core, RecursionError);
        createTypeConstant(members, HPyContextMembers.H_NOTIMPLEMENTEDERROR, core, NotImplementedError);
        createTypeConstant(members, HPyContextMembers.H_SYNTAXERROR, core, SyntaxError);
        createTypeConstant(members, HPyContextMembers.H_INDENTATIONERROR, core, IndentationError);
        createTypeConstant(members, HPyContextMembers.H_TABERROR, core, TabError);
        createTypeConstant(members, HPyContextMembers.H_REFERENCEERROR, core, ReferenceError);
        createTypeConstant(members, HPyContextMembers.H_SYSTEMERROR, core, SystemError);
        createTypeConstant(members, HPyContextMembers.H_SYSTEMEXIT, core, SystemExit);
        createTypeConstant(members, HPyContextMembers.H_TYPEERROR, core, TypeError);
        createTypeConstant(members, HPyContextMembers.H_UNBOUNDLOCALERROR, core, UnboundLocalError);
        createTypeConstant(members, HPyContextMembers.H_UNICODEERROR, core, UnicodeError);
        createTypeConstant(members, HPyContextMembers.H_UNICODEENCODEERROR, core, UnicodeEncodeError);
        createTypeConstant(members, HPyContextMembers.H_UNICODEDECODEERROR, core, UnicodeDecodeError);
        createTypeConstant(members, HPyContextMembers.H_UNICODETRANSLATEERROR, core, UnicodeTranslateError);
        createTypeConstant(members, HPyContextMembers.H_VALUEERROR, core, ValueError);
        createTypeConstant(members, HPyContextMembers.H_ZERODIVISIONERROR, core, ZeroDivisionError);
        createTypeConstant(members, HPyContextMembers.H_BLOCKINGIOERROR, core, BlockingIOError);
        createTypeConstant(members, HPyContextMembers.H_BROKENPIPEERROR, core, BrokenPipeError);
        createTypeConstant(members, HPyContextMembers.H_CHILDPROCESSERROR, core, ChildProcessError);
        createTypeConstant(members, HPyContextMembers.H_CONNECTIONERROR, core, ConnectionError);
        createTypeConstant(members, HPyContextMembers.H_CONNECTIONABORTEDERROR, core, ConnectionAbortedError);
        createTypeConstant(members, HPyContextMembers.H_CONNECTIONREFUSEDERROR, core, ConnectionRefusedError);
        createTypeConstant(members, HPyContextMembers.H_CONNECTIONRESETERROR, core, ConnectionResetError);
        createTypeConstant(members, HPyContextMembers.H_FILEEXISTSERROR, core, FileExistsError);
        createTypeConstant(members, HPyContextMembers.H_FILENOTFOUNDERROR, core, FileNotFoundError);
        createTypeConstant(members, HPyContextMembers.H_INTERRUPTEDERROR, core, InterruptedError);
        createTypeConstant(members, HPyContextMembers.H_ISADIRECTORYERROR, core, IsADirectoryError);
        createTypeConstant(members, HPyContextMembers.H_NOTADIRECTORYERROR, core, NotADirectoryError);
        createTypeConstant(members, HPyContextMembers.H_PERMISSIONERROR, core, PermissionError);
        createTypeConstant(members, HPyContextMembers.H_PROCESSLOOKUPERROR, core, ProcessLookupError);
        createTypeConstant(members, HPyContextMembers.H_TIMEOUTERROR, core, TimeoutError);
        createTypeConstant(members, HPyContextMembers.H_WARNING, core, Warning);
        createTypeConstant(members, HPyContextMembers.H_USERWARNING, core, UserWarning);
        createTypeConstant(members, HPyContextMembers.H_DEPRECATIONWARNING, core, DeprecationWarning);
        createTypeConstant(members, HPyContextMembers.H_PENDINGDEPRECATIONWARNING, core, PendingDeprecationWarning);
        createTypeConstant(members, HPyContextMembers.H_SYNTAXWARNING, core, SyntaxWarning);
        createTypeConstant(members, HPyContextMembers.H_RUNTIMEWARNING, core, RuntimeWarning);
        createTypeConstant(members, HPyContextMembers.H_FUTUREWARNING, core, FutureWarning);
        createTypeConstant(members, HPyContextMembers.H_IMPORTWARNING, core, ImportWarning);
        createTypeConstant(members, HPyContextMembers.H_UNICODEWARNING, core, UnicodeWarning);
        createTypeConstant(members, HPyContextMembers.H_BYTESWARNING, core, BytesWarning);
        createTypeConstant(members, HPyContextMembers.H_RESOURCEWARNING, core, ResourceWarning);

        createTypeConstant(members, HPyContextMembers.H_BASEOBJECTTYPE, core, PythonObject);
        createTypeConstant(members, HPyContextMembers.H_TYPETYPE, core, PythonClass);
        createTypeConstant(members, HPyContextMembers.H_LONGTYPE, core, PInt);
        createTypeConstant(members, HPyContextMembers.H_UNICODETYPE, core, PString);
        createTypeConstant(members, HPyContextMembers.H_TUPLETYPE, core, PTuple);
        createTypeConstant(members, HPyContextMembers.H_LISTTYPE, core, PList);

        members[HPyContextMembers.CTX_ASPYOBJECT.ordinal()] = new GraalHPyAsPyObject();
        members[HPyContextMembers.CTX_DUP.ordinal()] = new GraalHPyDup();
        members[HPyContextMembers.CTX_CLOSE.ordinal()] = new GraalHPyClose();
        members[HPyContextMembers.CTX_MODULE_CREATE.ordinal()] = new GraalHPyModuleCreate();
        GraalHPyLongFromLong fromSignedLong = new GraalHPyLongFromLong();
        GraalHPyLongFromLong fromUnsignedLong = new GraalHPyLongFromLong(false);
        members[HPyContextMembers.CTX_LONG_FROMLONG.ordinal()] = fromSignedLong;
        members[HPyContextMembers.CTX_LONG_FROMLONGLONG.ordinal()] = fromSignedLong;
        members[HPyContextMembers.CTX_LONG_FROM_UNSIGNEDLONGLONG.ordinal()] = fromUnsignedLong;
        members[HPyContextMembers.CTX_LONG_FROMSSIZE_T.ordinal()] = fromSignedLong;
        members[HPyContextMembers.CTX_LONG_FROMSIZE_T.ordinal()] = fromUnsignedLong;
        members[HPyContextMembers.CTX_LONG_ASLONG.ordinal()] = new GraalHPyLongAsLong();
        members[HPyContextMembers.CTX_NEW.ordinal()] = new GraalHPyNew();
        members[HPyContextMembers.CTX_TYPE_GENERIC_NEW.ordinal()] = new GraalHPyTypeGenericNew();
        members[HPyContextMembers.CTX_CAST.ordinal()] = new GraalHPyCast();

        // unary
        members[HPyContextMembers.CTX_NEGATIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Neg);
        members[HPyContextMembers.CTX_POSITIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Pos);
        members[HPyContextMembers.CTX_ABSOLUTE.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.ABS, 1);
        members[HPyContextMembers.CTX_INVERT.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Invert);
        members[HPyContextMembers.CTX_INDEX.ordinal()] = new GraalHPyAsIndex();
        members[HPyContextMembers.CTX_LONG.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.INT, 1);
        members[HPyContextMembers.CTX_FLOAT.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.FLOAT, 1);

        // binary
        members[HPyContextMembers.CTX_ADD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Add);
        members[HPyContextMembers.CTX_SUB.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Sub);
        members[HPyContextMembers.CTX_MULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mul);
        members[HPyContextMembers.CTX_MATRIXMULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.MatMul);
        members[HPyContextMembers.CTX_FLOORDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.FloorDiv);
        members[HPyContextMembers.CTX_TRUEDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.TrueDiv);
        members[HPyContextMembers.CTX_REMAINDER.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mod);
        members[HPyContextMembers.CTX_DIVMOD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.DivMod);
        members[HPyContextMembers.CTX_LSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.LShift);
        members[HPyContextMembers.CTX_RSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.RShift);
        members[HPyContextMembers.CTX_AND.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.And);
        members[HPyContextMembers.CTX_XOR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Xor);
        members[HPyContextMembers.CTX_OR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Or);
        members[HPyContextMembers.CTX_INPLACEADD.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAdd);
        members[HPyContextMembers.CTX_INPLACESUBTRACT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ISub);
        members[HPyContextMembers.CTX_INPLACEMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMul);
        members[HPyContextMembers.CTX_INPLACEMATRIXMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMatMul);
        members[HPyContextMembers.CTX_INPLACEFLOORDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IFloorDiv);
        members[HPyContextMembers.CTX_INPLACETRUEDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ITrueDiv);
        members[HPyContextMembers.CTX_INPLACEREMAINDER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMod);
        members[HPyContextMembers.CTX_INPLACELSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ILShift);
        members[HPyContextMembers.CTX_INPLACERSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IRShift);
        members[HPyContextMembers.CTX_INPLACEAND.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAnd);
        members[HPyContextMembers.CTX_INPLACEXOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IXor);
        members[HPyContextMembers.CTX_INPLACEOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IOr);

        // ternary
        members[HPyContextMembers.CTX_POWER.ordinal()] = new GraalHPyTernaryArithmetic(TernaryArithmetic.Pow);
        members[HPyContextMembers.CTX_INPLACEPOWER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IPow);

        members[HPyContextMembers.CTX_DICT_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PDict);
        members[HPyContextMembers.CTX_DICT_NEW.ordinal()] = new GraalHPyDictNew();
        members[HPyContextMembers.CTX_DICT_SETITEM.ordinal()] = new GraalHPyDictSetItem();
        members[HPyContextMembers.CTX_DICT_GETITEM.ordinal()] = new GraalHPyDictGetItem();
        members[HPyContextMembers.CTX_LIST_NEW.ordinal()] = new GraalHPyListNew();
        members[HPyContextMembers.CTX_LIST_APPEND.ordinal()] = new GraalHPyListAppend();
        members[HPyContextMembers.CTX_FLOAT_FROMDOUBLE.ordinal()] = new GraalHPyFloatFromDouble();
        members[HPyContextMembers.CTX_FLOAT_ASDOUBLE.ordinal()] = new GraalHPyFloatAsDouble();
        members[HPyContextMembers.CTX_BYTES_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PBytes);
        members[HPyContextMembers.CTX_BYTES_GET_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMembers.CTX_BYTES_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMembers.CTX_BYTES_AS_STRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMembers.CTX_BYTES_ASSTRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMembers.CTX_ERR_NOMEMORY.ordinal()] = new GraalHPyErrRaisePredefined(MemoryError);
        members[HPyContextMembers.CTX_ERR_SETSTRING.ordinal()] = new GraalHPyErrSetString(true);
        members[HPyContextMembers.CTX_ERR_SETOBJECT.ordinal()] = new GraalHPyErrSetString(false);
        members[HPyContextMembers.CTX_ERR_OCCURRED.ordinal()] = new GraalHPyErrOccurred();
        members[HPyContextMembers.CTX_ERR_CLEAR.ordinal()] = new GraalHPyErrClear();
        members[HPyContextMembers.CTX_FROMPYOBJECT.ordinal()] = new GraalHPyFromPyObject();
        members[HPyContextMembers.CTX_UNICODE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PString);
        members[HPyContextMembers.CTX_ISTRUE.ordinal()] = new GraalHPyIsTrue();
        members[HPyContextMembers.CTX_UNICODE_ASUTF8STRING.ordinal()] = new GraalHPyUnicodeAsUTF8String();
        members[HPyContextMembers.CTX_UNICODE_FROMSTRING.ordinal()] = new GraalHPyUnicodeFromString();
        members[HPyContextMembers.CTX_UNICODE_FROMWIDECHAR.ordinal()] = new GraalHPyUnicodeFromWchar();
        members[HPyContextMembers.CTX_TYPE_FROM_SPEC.ordinal()] = new GraalHPyTypeFromSpec();
        members[HPyContextMembers.CTX_GETATTR.ordinal()] = new GraalHPyGetAttr(OBJECT);
        members[HPyContextMembers.CTX_GETATTR_S.ordinal()] = new GraalHPyGetAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_HASATTR.ordinal()] = new GraalHPyHasAttr(OBJECT);
        members[HPyContextMembers.CTX_HASATTR_S.ordinal()] = new GraalHPyHasAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_SETATTR.ordinal()] = new GraalHPySetAttr(OBJECT);
        members[HPyContextMembers.CTX_SETATTR_S.ordinal()] = new GraalHPySetAttr(CHAR_PTR);
        members[HPyContextMembers.CTX_GETITEM.ordinal()] = new GraalHPyGetItem(OBJECT);
        members[HPyContextMembers.CTX_GETITEM_S.ordinal()] = new GraalHPyGetItem(CHAR_PTR);
        members[HPyContextMembers.CTX_GETITEM_I.ordinal()] = new GraalHPyGetItem(INT32);
        members[HPyContextMembers.CTX_SETITEM.ordinal()] = new GraalHPySetItem(OBJECT);
        members[HPyContextMembers.CTX_SETITEM_S.ordinal()] = new GraalHPySetItem(CHAR_PTR);
        members[HPyContextMembers.CTX_SETITEM_I.ordinal()] = new GraalHPySetItem(INT32);
        members[HPyContextMembers.CTX_REPR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.REPR, 1);
        members[HPyContextMembers.CTX_STR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.STR, 1);
        members[HPyContextMembers.CTX_ASCII.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.ASCII, 1);
        members[HPyContextMembers.CTX_BYTES.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.BYTES, 1);
        members[HPyContextMembers.CTX_RICHCOMPARE.ordinal()] = new GraalHPyRichcompare(false);
        members[HPyContextMembers.CTX_RICHCOMPAREBOOL.ordinal()] = new GraalHPyRichcompare(true);
        members[HPyContextMembers.CTX_HASH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.HASH, 1, GraalHPyConversionNodeSupplier.TO_INT64);
        members[HPyContextMembers.CTX_NUMBER_CHECK.ordinal()] = new GraalHPyIsNumber();
        members[HPyContextMembers.CTX_LENGTH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.LEN, 1, GraalHPyConversionNodeSupplier.TO_INT64);
        members[HPyContextMembers.CTX_TUPLE_FROMARRAY.ordinal()] = new GraalHPyTupleFromArray();

        GraalHPyBuilderNew graalHPyBuilderNew = new GraalHPyBuilderNew();
        GraalHPyBuilderSet graalHPyBuilderSet = new GraalHPyBuilderSet();
        GraalHPyBuilderCancel graalHPyBuilderCancel = new GraalHPyBuilderCancel();
        members[HPyContextMembers.CTX_TUPLE_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMembers.CTX_TUPLE_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMembers.CTX_TUPLE_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PTuple);
        members[HPyContextMembers.CTX_TUPLE_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMembers.CTX_LIST_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PList);
        members[HPyContextMembers.CTX_LIST_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMembers.CTX_LIST_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMembers.CTX_LIST_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PList);
        members[HPyContextMembers.CTX_LIST_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMembers.CTX_TRACKER_NEW.ordinal()] = new GraalHPyTrackerNew();
        members[HPyContextMembers.CTX_TRACKER_ADD.ordinal()] = new GraalHPyTrackerAdd();
        members[HPyContextMembers.CTX_TRACKER_FORGET_ALL.ordinal()] = new GraalHPyTrackerCleanup(true);
        members[HPyContextMembers.CTX_TRACKER_CLOSE.ordinal()] = new GraalHPyTrackerCleanup(false);
        return members;
    }

    private static void createConstant(Object[] members, HPyContextMembers member, Object value) {
        members[member.ordinal()] = new GraalHPyHandle(value);
    }

    private static void createTypeConstant(Object[] members, HPyContextMembers member, PythonCore core, PythonBuiltinClassType value) {
        members[member.ordinal()] = new GraalHPyHandle(core.lookupType(value));
    }

    @TruffleBoundary(allowInlining = true)
    private int allocateHandle() {
        int freeItem = freeStack.pop();
        if (freeItem != -1) {
            assert 0 <= freeItem && freeItem < hpyHandleTable.length;
            assert hpyHandleTable[freeItem] == null;
            return freeItem;
        }
        for (int i = 1; i < hpyHandleTable.length; i++) {
            if (hpyHandleTable[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public int getHPyHandleForObject(GraalHPyHandle object) {
        // find free association
        int handle = allocateHandle();
        if (handle == -1) {
            // resize
            int newSize = Math.max(16, hpyHandleTable.length * 2);
            LOGGER.fine(() -> "resizing HPy handle table to " + newSize);
            hpyHandleTable = Arrays.copyOf(hpyHandleTable, newSize);
            handle = allocateHandle();
        }
        assert handle > 0;
        hpyHandleTable[handle] = object;
        if (LOGGER.isLoggable(Level.FINER)) {
            final int handleID = handle;
            LOGGER.finer(() -> String.format("allocating HPy handle %d (object: %s)", handleID, object));
        }
        return handle;
    }

    public GraalHPyHandle getObjectForHPyHandle(int handle) {
        // find free association
        return hpyHandleTable[handle];
    }

    public void releaseHPyHandleForObject(long handle) {
        try {
            releaseHPyHandleForObject(com.oracle.graal.python.builtins.objects.ints.PInt.intValueExact(handle));
        } catch (OverflowException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning(() -> String.format("tried to release invalid handle %d", handle));
            }
            assert false : PythonUtils.format("tried to release invalid handle %d", handle);
        }
    }

    public void releaseHPyHandleForObject(int handle) {
        assert handle != 0 : "NULL handle cannot be released";
        assert hpyHandleTable[handle] != null : PythonUtils.format("releasing handle that has already been released: %d", handle);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(() -> "releasing HPy handle " + handle);
        }
        hpyHandleTable[handle] = null;
        freeStack.push(handle);
    }

    // nb. keep in sync with 'meth.h'
    private static final int HPy_METH = 0x100000;

    // These methods could be static but they are deliberately implemented as member methods because
    // we may fetch the constants from the native library at initialization time.

    @SuppressWarnings("static-method")
    public boolean isHPyMeth(int flags) {
        return (flags & HPy_METH) != 0;
    }

    void setNullHandle(GraalHPyHandle hpyNullHandle) {
        this.hpyNullHandle = hpyNullHandle;
    }

    public GraalHPyHandle getNullHandle() {
        return hpyNullHandle;
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
     * A phantom reference to an object that has an associated HPy native space (
     * {@link GraalHPyDef#OBJECT_HPY_NATIVE_SPACE} is set).
     */
    static final class GraalHPyHandleReference extends PhantomReference<Object> {

        private final int id;
        private final Object nativeSpace;
        private final Object destroyFunc;

        public GraalHPyHandleReference(int id, Object referent, ReferenceQueue<Object> q, Object nativeSpace, Object destroyFunc) {
            super(referent, q);
            this.id = id;
            this.nativeSpace = nativeSpace;
            this.destroyFunc = destroyFunc;
        }

        public Object getNativeSpace() {
            return nativeSpace;
        }

        public Object getDestroyFunc() {
            return destroyFunc;
        }
    }

    /**
     * Registers an HPy native space of a Python object.<br/>
     * Use this method to register a native memory that is associated with a Python object in order
     * to ensure that the native memory will be free'd when the owning Python object dies.<br/>
     * This works by creating a phantom reference to the Python object, using a thread that
     * concurrently polls the reference queue and then schedules an async action to free the native
     * memory. So, the destroy function will always be executed on the main thread.
     *
     * @param pythonObject The Python object that has associated native memory.
     * @param dataPtr The pointer object of the native memory.
     * @param destroyFunc The destroy function to call when the Python object is unreachable (may be
     *            {@code null}; in this case, bare {@code free} will be used).
     */
    void createHandleReference(PythonObject pythonObject, Object dataPtr, Object destroyFunc) {
        ensureReferenceQueue();
        int id = references.reserve();
        references.commit(id, new GraalHPyHandleReference(id, pythonObject, ensureReferenceQueue(), dataPtr, destroyFunc));
    }

    private ReferenceQueue<Object> ensureReferenceQueue() {
        if (nativeSpaceReferenceQueue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

            // lazily register the runnable that concurrently collects the queued references
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

            nativeSpaceReferenceQueue = referenceQueue;
            return referenceQueue;
        }
        return nativeSpaceReferenceQueue;
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
}
