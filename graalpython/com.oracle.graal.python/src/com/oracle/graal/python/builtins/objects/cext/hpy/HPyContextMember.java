/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyUpcall;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * An enum of all fields currently available in the HPy context (see {@code public_api.h}).
 */
public enum HPyContextMember implements HPyUpcall {
    NAME("name"),
    PRIVATE("_private"),
    CTX_VERSION("ctx_version"),

    // {{start ctx members}}
    // @formatter:off
    // Checkstyle: stop
    // DO NOT EDIT THIS PART!
    // This part is automatically generated by hpy.tools.autogen.graalpy.autogen_ctx_member_enum
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
    H_BUILTINS("h_Builtins"),
    CTX_DUP("ctx_Dup", HPy, HPyContextPtr, HPy),
    CTX_CLOSE("ctx_Close", CVoid, HPyContextPtr, HPy),
    CTX_LONG_FROMINT32_T("ctx_Long_FromInt32_t", HPy, HPyContextPtr, Int32_t),
    CTX_LONG_FROMUINT32_T("ctx_Long_FromUInt32_t", HPy, HPyContextPtr, Uint32_t),
    CTX_LONG_FROMINT64_T("ctx_Long_FromInt64_t", HPy, HPyContextPtr, Int64_t),
    CTX_LONG_FROMUINT64_T("ctx_Long_FromUInt64_t", HPy, HPyContextPtr, Uint64_t),
    CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t", HPy, HPyContextPtr, Size_t),
    CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t", HPy, HPyContextPtr, HPy_ssize_t),
    CTX_LONG_ASINT32_T("ctx_Long_AsInt32_t", Int32_t, HPyContextPtr, HPy),
    CTX_LONG_ASUINT32_T("ctx_Long_AsUInt32_t", Uint32_t, HPyContextPtr, HPy),
    CTX_LONG_ASUINT32_TMASK("ctx_Long_AsUInt32_tMask", Uint32_t, HPyContextPtr, HPy),
    CTX_LONG_ASINT64_T("ctx_Long_AsInt64_t", Int64_t, HPyContextPtr, HPy),
    CTX_LONG_ASUINT64_T("ctx_Long_AsUInt64_t", Uint64_t, HPyContextPtr, HPy),
    CTX_LONG_ASUINT64_TMASK("ctx_Long_AsUInt64_tMask", Uint64_t, HPyContextPtr, HPy),
    CTX_LONG_ASSIZE_T("ctx_Long_AsSize_t", Size_t, HPyContextPtr, HPy),
    CTX_LONG_ASSSIZE_T("ctx_Long_AsSsize_t", HPy_ssize_t, HPyContextPtr, HPy),
    CTX_LONG_ASVOIDPTR("ctx_Long_AsVoidPtr", VoidPtr, HPyContextPtr, HPy),
    CTX_LONG_ASDOUBLE("ctx_Long_AsDouble", CDouble, HPyContextPtr, HPy),
    CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble", HPy, HPyContextPtr, CDouble),
    CTX_FLOAT_ASDOUBLE("ctx_Float_AsDouble", CDouble, HPyContextPtr, HPy),
    CTX_BOOL_FROMBOOL("ctx_Bool_FromBool", HPy, HPyContextPtr, Bool),
    CTX_LENGTH("ctx_Length", HPy_ssize_t, HPyContextPtr, HPy),
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
    CTX_CALL("ctx_Call", HPy, HPyContextPtr, HPy, ConstHPyPtr, Size_t, HPy),
    CTX_CALLMETHOD("ctx_CallMethod", HPy, HPyContextPtr, HPy, ConstHPyPtr, Size_t, HPy),
    CTX_FATALERROR("ctx_FatalError", CVoid, HPyContextPtr, ConstCharPtr),
    CTX_ERR_SETSTRING("ctx_Err_SetString", CVoid, HPyContextPtr, HPy, ConstCharPtr),
    CTX_ERR_SETOBJECT("ctx_Err_SetObject", CVoid, HPyContextPtr, HPy, HPy),
    CTX_ERR_SETFROMERRNOWITHFILENAME("ctx_Err_SetFromErrnoWithFilename", HPy, HPyContextPtr, HPy, ConstCharPtr),
    CTX_ERR_SETFROMERRNOWITHFILENAMEOBJECTS("ctx_Err_SetFromErrnoWithFilenameObjects", CVoid, HPyContextPtr, HPy, HPy, HPy),
    CTX_ERR_OCCURRED("ctx_Err_Occurred", Int, HPyContextPtr),
    CTX_ERR_EXCEPTIONMATCHES("ctx_Err_ExceptionMatches", Int, HPyContextPtr, HPy),
    CTX_ERR_NOMEMORY("ctx_Err_NoMemory", CVoid, HPyContextPtr),
    CTX_ERR_CLEAR("ctx_Err_Clear", CVoid, HPyContextPtr),
    CTX_ERR_NEWEXCEPTION("ctx_Err_NewException", HPy, HPyContextPtr, ConstCharPtr, HPy, HPy),
    CTX_ERR_NEWEXCEPTIONWITHDOC("ctx_Err_NewExceptionWithDoc", HPy, HPyContextPtr, ConstCharPtr, ConstCharPtr, HPy, HPy),
    CTX_ERR_WARNEX("ctx_Err_WarnEx", Int, HPyContextPtr, HPy, ConstCharPtr, HPy_ssize_t),
    CTX_ERR_WRITEUNRAISABLE("ctx_Err_WriteUnraisable", CVoid, HPyContextPtr, HPy),
    CTX_ISTRUE("ctx_IsTrue", Int, HPyContextPtr, HPy),
    CTX_TYPE_FROMSPEC("ctx_Type_FromSpec", HPy, HPyContextPtr, HPyType_SpecPtr, HPyType_SpecParamPtr),
    CTX_TYPE_GENERICNEW("ctx_Type_GenericNew", HPy, HPyContextPtr, HPy, ConstHPyPtr, HPy_ssize_t, HPy),
    CTX_GETATTR("ctx_GetAttr", HPy, HPyContextPtr, HPy, HPy),
    CTX_GETATTR_S("ctx_GetAttr_s", HPy, HPyContextPtr, HPy, ConstCharPtr),
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
    CTX_DELITEM("ctx_DelItem", Int, HPyContextPtr, HPy, HPy),
    CTX_DELITEM_I("ctx_DelItem_i", Int, HPyContextPtr, HPy, HPy_ssize_t),
    CTX_DELITEM_S("ctx_DelItem_s", Int, HPyContextPtr, HPy, ConstCharPtr),
    CTX_TYPE("ctx_Type", HPy, HPyContextPtr, HPy),
    CTX_TYPECHECK("ctx_TypeCheck", Int, HPyContextPtr, HPy, HPy),
    CTX_TYPE_GETNAME("ctx_Type_GetName", ConstCharPtr, HPyContextPtr, HPy),
    CTX_TYPE_ISSUBTYPE("ctx_Type_IsSubtype", Int, HPyContextPtr, HPy, HPy),
    CTX_IS("ctx_Is", Int, HPyContextPtr, HPy, HPy),
    CTX_ASSTRUCT_OBJECT("ctx_AsStruct_Object", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_LEGACY("ctx_AsStruct_Legacy", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_TYPE("ctx_AsStruct_Type", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_LONG("ctx_AsStruct_Long", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_FLOAT("ctx_AsStruct_Float", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_UNICODE("ctx_AsStruct_Unicode", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_TUPLE("ctx_AsStruct_Tuple", VoidPtr, HPyContextPtr, HPy),
    CTX_ASSTRUCT_LIST("ctx_AsStruct_List", VoidPtr, HPyContextPtr, HPy),
    CTX_TYPE_GETBUILTINSHAPE("ctx_Type_GetBuiltinShape", HPyType_BuiltinShape, HPyContextPtr, HPy),
    CTX_NEW("ctx_New", HPy, HPyContextPtr, HPy, VoidPtrPtr),
    CTX_REPR("ctx_Repr", HPy, HPyContextPtr, HPy),
    CTX_STR("ctx_Str", HPy, HPyContextPtr, HPy),
    CTX_ASCII("ctx_ASCII", HPy, HPyContextPtr, HPy),
    CTX_BYTES("ctx_Bytes", HPy, HPyContextPtr, HPy),
    CTX_RICHCOMPARE("ctx_RichCompare", HPy, HPyContextPtr, HPy, HPy, Int),
    CTX_RICHCOMPAREBOOL("ctx_RichCompareBool", Int, HPyContextPtr, HPy, HPy, Int),
    CTX_HASH("ctx_Hash", HPy_hash_t, HPyContextPtr, HPy),
    CTX_BYTES_CHECK("ctx_Bytes_Check", Int, HPyContextPtr, HPy),
    CTX_BYTES_SIZE("ctx_Bytes_Size", HPy_ssize_t, HPyContextPtr, HPy),
    CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE", HPy_ssize_t, HPyContextPtr, HPy),
    CTX_BYTES_ASSTRING("ctx_Bytes_AsString", ConstCharPtr, HPyContextPtr, HPy),
    CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING", ConstCharPtr, HPyContextPtr, HPy),
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
    CTX_UNICODE_SUBSTRING("ctx_Unicode_Substring", HPy, HPyContextPtr, HPy, HPy_ssize_t, HPy_ssize_t),
    CTX_LIST_CHECK("ctx_List_Check", Int, HPyContextPtr, HPy),
    CTX_LIST_NEW("ctx_List_New", HPy, HPyContextPtr, HPy_ssize_t),
    CTX_LIST_APPEND("ctx_List_Append", Int, HPyContextPtr, HPy, HPy),
    CTX_DICT_CHECK("ctx_Dict_Check", Int, HPyContextPtr, HPy),
    CTX_DICT_NEW("ctx_Dict_New", HPy, HPyContextPtr),
    CTX_DICT_KEYS("ctx_Dict_Keys", HPy, HPyContextPtr, HPy),
    CTX_DICT_COPY("ctx_Dict_Copy", HPy, HPyContextPtr, HPy),
    CTX_TUPLE_CHECK("ctx_Tuple_Check", Int, HPyContextPtr, HPy),
    CTX_TUPLE_FROMARRAY("ctx_Tuple_FromArray", HPy, HPyContextPtr, HPyPtr, HPy_ssize_t),
    CTX_SLICE_UNPACK("ctx_Slice_Unpack", Int, HPyContextPtr, HPy, HPy_ssize_tPtr, HPy_ssize_tPtr, HPy_ssize_tPtr),
    CTX_IMPORT_IMPORTMODULE("ctx_Import_ImportModule", HPy, HPyContextPtr, ConstCharPtr),
    CTX_CAPSULE_NEW("ctx_Capsule_New", HPy, HPyContextPtr, VoidPtr, ConstCharPtr, HPyCapsule_DestructorPtr),
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
    CTX_COMPILE_S("ctx_Compile_s", HPy, HPyContextPtr, ConstCharPtr, ConstCharPtr, HPy_SourceKind),
    CTX_EVALCODE("ctx_EvalCode", HPy, HPyContextPtr, HPy, HPy, HPy),
    CTX_CONTEXTVAR_NEW("ctx_ContextVar_New", HPy, HPyContextPtr, ConstCharPtr, HPy),
    CTX_CONTEXTVAR_GET("ctx_ContextVar_Get", Int32_t, HPyContextPtr, HPy, HPy, HPyPtr),
    CTX_CONTEXTVAR_SET("ctx_ContextVar_Set", HPy, HPyContextPtr, HPy, HPy),
    CTX_SETCALLFUNCTION("ctx_SetCallFunction", Int, HPyContextPtr, HPy, HPyCallFunctionPtr);

    // @formatter:on
    // Checkstyle: resume
    // {{end ctx members}}

    private final String name;
    private final HPyContextSignature signature;

    HPyContextMember(String name) {
        this.name = name;
        this.signature = null;
    }

    HPyContextMember(String name, HPyContextSignatureType returnType, HPyContextSignatureType... paramTypes) {
        this.name = name;
        this.signature = new HPyContextSignature(returnType, paramTypes);
    }

    @CompilationFinal(dimensions = 1) public static final HPyContextMember[] VALUES = values();

    @Override
    public String getName() {
        return name;
    }

    public HPyContextSignature getSignature() {
        return signature;
    }
}
