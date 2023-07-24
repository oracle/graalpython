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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeDecodeError;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UNICODE_ESCAPE;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_UCS4;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_UNICODE_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor._PY_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.ChrNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.InternNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi6BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.GetErrorHandlerNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.DecodeNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.Charsets;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EqNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.FindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.LtNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.RFindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ReplaceNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.StartsWithNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.FromByteArrayNode;
import com.oracle.truffle.api.strings.TruffleString.FromNativePointerNode;
import com.oracle.truffle.api.strings.TruffleString.SwitchEncodingNode;

public final class PythonCextUnicodeBuiltins {

    static TruffleString convertEncoding(Object obj) {
        return obj == PNone.NO_VALUE ? StringLiterals.T_UTF8 : (TruffleString) obj;
    }

    static TruffleString convertErrors(Object obj) {
        return obj == PNone.NO_VALUE ? StringLiterals.T_STRICT : (TruffleString) obj;
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int}, call = Direct)
    abstract static class PyUnicode_FromOrdinal extends CApiUnaryBuiltinNode {
        @Specialization
        static Object chr(int value,
                        @Cached ChrNode chrNode) {
            return chrNode.execute(null, value);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyUnicode_FromObject extends CApiUnaryBuiltinNode {
        @Specialization
        static TruffleString fromObject(TruffleString s) {
            return s;
        }

        @Specialization(guards = "isPStringType(s, getClassNode)")
        static PString fromObject(PString s,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return s;
        }

        @Specialization(guards = {"!isPStringType(obj, getClassNode)", "isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object fromObject(Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return strNode.executeWith(obj);
        }

        @Specialization(guards = {"!isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, obj);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }

        protected boolean isPStringType(Object obj, GetClassNode getClassNode) {
            return getClassNode.execute(obj) == PythonBuiltinClassType.PString;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyUnicode_Concat extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isString(left) || isStringSubtype(left, getClassNode, isSubtypeNode)", "isString(right) || isStringSubtype(right, getClassNode, isSubtypeNode)"})
        Object concat(Object left, Object right,
                        @Cached StringBuiltins.AddNode addNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return addNode.execute(null, left, right);
        }

        @Specialization(guards = {"!isString(left)", "!isStringSubtype(left, getClassNode, isSubtypeNode)"})
        Object leftNotString(Object left, @SuppressWarnings("unused") Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, left);
        }

        @Specialization(guards = {"!isString(right)", "!isStringSubtype(right, getClassNode, isSubtypeNode)"})
        Object rightNotString(@SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, right);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_FromEncodedObject extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doTruffleString(Object obj, TruffleString encoding, TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile nullProfile,
                        @Shared @Cached PyUnicodeFromEncodedObject decodeNode) {
            if (nullProfile.profile(inliningTarget, obj == PNone.NO_VALUE)) {
                throw PRaiseNode.raiseUncached(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
            return decodeNode.execute(null, obj, encoding, errors);
        }

        @Specialization(replaces = "doTruffleString")
        static Object doGeneric(Object obj, Object encodingObj, Object errorsObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile nullProfile,
                        @Shared @Cached PyUnicodeFromEncodedObject decodeNode) {
            TruffleString encoding;
            if (encodingObj == PNone.NO_VALUE) {
                encoding = T_UTF8;
            } else {
                assert encodingObj instanceof TruffleString;
                encoding = (TruffleString) encodingObj;
            }

            TruffleString errors;
            if (errorsObj == PNone.NO_VALUE) {
                errors = T_STRICT;
            } else {
                assert errorsObj instanceof TruffleString;
                errors = (TruffleString) errorsObj;
            }
            return doTruffleString(obj, encoding, errors, inliningTarget, nullProfile, decodeNode);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {ArgDescriptor.PyObject}, call = Ignored)
    abstract static class PyTruffleUnicode_InternInPlace extends CApiUnaryBuiltinNode {
        @Specialization(guards = {"!isTruffleString(obj)", "isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object intern(Object obj,
                        @Cached InternNode internNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return internNode.execute(null, obj);
        }

        @Specialization(guards = {"!isTruffleString(obj)", "!isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object intern(@SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            assert false;
            return PNone.NONE;
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyUnicode_Format extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"isString(format) || isStringSubtype(format, getClassNode, isSubtypeNode)"})
        Object find(Object format, Object args,
                        @Cached ModNode modNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            checkNonNullArg(format, args);
            return modNode.execute(null, format, args);
        }

        @Specialization(guards = {"!isTruffleString(format)", "isStringSubtype(format, getClassNode, isSubtypeNode)"})
        Object find(Object format, @SuppressWarnings("unused") Object args,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            checkNonNullArg(format, args);
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, format);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PY_UCS4, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PyUnicode_FindChar extends CApi5BuiltinNode {
        @Specialization(guards = {"isString(string) || isStringSubtype(string, getClassNode, isSubtypeNode)", "direction > 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached ChrNode chrNode,
                        @Cached FindNode findNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return findNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"isString(string) || isStringSubtype(string, getClassNode, isSubtypeNode)", "direction <= 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached ChrNode chrNode,
                        @Cached RFindNode rFindNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return rFindNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"!isTruffleString(string)", "isStringSubtype(string, getClassNode, isSubtypeNode)"})
        Object find(Object string, @SuppressWarnings("unused") Object c, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") Object direction,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PyUnicode_Substring extends CApiTernaryBuiltinNode {
        @Specialization(guards = {"isString(s) || isStringSubtype(s, getClassNode, isSubtypeNode)"})
        Object find(Object s, long start, long end,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            Object getItemCallable = lookupAttrNode.execute(null, s, T___GETITEM__);
            return callNode.execute(getItemCallable, sliceNode.execute(start, end, PNone.NONE));
        }

        @Specialization(guards = {"!isTruffleString(s)", "isStringSubtype(s, getClassNode, isSubtypeNode)"})
        Object find(Object s, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, s);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyUnicode_Join extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"isString(separator) || isStringSubtype(separator, getClassNode, isSubtypeNode)"})
        Object find(Object separator, Object seq,
                        @Cached StringBuiltins.JoinNode joinNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return joinNode.execute(null, separator, seq);
        }

        @Specialization(guards = {"!isTruffleString(separator)", "isStringSubtype(separator, getClassNode, isSubtypeNode)"})
        Object find(Object separator, @SuppressWarnings("unused") Object seq,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, separator);
        }

        protected boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_EqualToASCIIString extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(left, getClassNode, isSubtypeNode)", "isAnyString(right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EqNode eqNode,
                        @Cached PyObjectIsTrueNode isTrue) {
            return PInt.intValue(isTrue.execute(null, eqNode.execute(null, left, right)));
        }

        @Specialization(guards = {"!isAnyString(left, getClassNode, isSubtypeNode) || !isAnyString(right, getClassNode, isSubtypeNode)"})
        Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }

        protected boolean isAnyString(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return PGuards.isString(obj) || isStringSubtype(obj, getClassNode, isSubtypeNode);
        }

        private static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyUnicode_Compare extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(left, getClassNode, isSubtypeNode)", "isAnyString(right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EqNode eqNode,
                        @Cached LtNode ltNode,
                        @Cached ConditionProfile eqProfile) {
            if (eqProfile.profile((boolean) eqNode.execute(null, left, right))) {
                return 0;
            } else {
                return (boolean) ltNode.execute(null, left, right) ? -1 : 1;
            }
        }

        @Specialization(guards = {"!isAnyString(left, getClassNode, isSubtypeNode) || !isAnyString(right, getClassNode, isSubtypeNode)"})
        Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }

        protected boolean isAnyString(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return PGuards.isString(obj) || isStringSubtype(obj, getClassNode, isSubtypeNode);
        }

        private static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PyUnicode_Tailmatch extends CApi5BuiltinNode {
        @Specialization(guards = {"isAnyString(string, getClassNode, isSubtypeNode)", "isAnyString(substring, getClassNode, isSubtypeNode)", "direction > 0"})
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode,
                        @Cached EndsWithNode endsWith,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            Object getItemCallable = lookupAttrNode.execute(null, string, T___GETITEM__);
            Object slice = callNode.execute(getItemCallable, sliceNode.execute(start, end, PNone.NONE));
            return (boolean) endsWith.execute(null, slice, substring, start, end) ? 1 : 0;
        }

        @Specialization(guards = {"isAnyString(string, getClassNode, isSubtypeNode)", "isAnyString(substring, getClassNode, isSubtypeNode)", "direction <= 0"})
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode,
                        @Cached StartsWithNode startsWith,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            Object getItemCallable = lookupAttrNode.execute(null, string, T___GETITEM__);
            Object slice = callNode.execute(getItemCallable, sliceNode.execute(start, end, PNone.NONE));
            return (boolean) startsWith.execute(null, slice, substring, start, end) ? 1 : 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isAnyString(string, getClassNode, isSubtypeNode) || !isAnyString(substring, getClassNode, isSubtypeNode)"})
        Object find(Object string, Object substring, Object start, Object end, Object direction,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }

        protected boolean isAnyString(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return PGuards.isString(obj) || isStringSubtype(obj, getClassNode, isSubtypeNode);
        }

        private static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_AsEncodedString extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isString(obj) || isStringSubtype(obj, getClassNode, isSubtypeNode)")
        Object encode(Object obj, Object encoding, Object errors,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EncodeNode encodeNode) {
            return encodeNode.execute(null, obj, convertEncoding(encoding), convertErrors(errors));
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object encode(@SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        protected static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PyUnicode_Replace extends CApiQuaternaryBuiltinNode {
        @Specialization(guards = {"isString(s)", "isString(substr)", "isString(replstr)"})
        Object replace(Object s, Object substr, Object replstr, long count,
                        @Cached ReplaceNode replaceNode) {
            return replaceNode.execute(null, s, substr, replstr, count);
        }

        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "isStringSubtype(s, getClassNode, isSubtypeNode)",
                        "isStringSubtype(substr, getClassNode, isSubtypeNode)",
                        "isStringSubtype(replstr, getClassNode, isSubtypeNode)"})
        public Object replace(Object s, Object substr, Object replstr, long count,
                        @Cached ReplaceNode replaceNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return replace(s, substr, replstr, count, replaceNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "!isStringSubtype(s, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(substr, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(replstr, getClassNode, isSubtypeNode)"})
        public Object replace(Object s, Object substr, Object replstr, long count,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return getNativeNull();
        }

        protected static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyUnicode_AsUnicodeEscapeString extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isString(s)")
        Object escape(Object s,
                        @Cached CodecsEncodeNode encodeNode,
                        @Cached com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode getItemNode) {
            return getItemNode.execute(null, encodeNode.execute(null, s, T_UNICODE_ESCAPE, PNone.NO_VALUE), 0);
        }

        @Specialization(guards = {"!isString(s)", "isStringSubtype(s, getClassNode, isSubtypeNode)"})
        Object escape(Object s,
                        @Cached CodecsEncodeNode encodeNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode getItemNode) {
            return escape(s, encodeNode, getItemNode);
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(obj, getClassNode, isSubtypeNode)"})
        Object escape(@SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        protected static boolean isStringSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PY_UCS4, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_ReadChar extends CApiBinaryBuiltinNode {
        @Specialization
        int doGeneric(Object type, long lindex,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode) {
            try {
                TruffleString s = castToStringNode.execute(type);
                int index = PInt.intValueExact(lindex);
                // avoid StringIndexOutOfBoundsException
                if (index < 0 || index >= lengthNode.execute(s, TS_ENCODING)) {
                    throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                }
                return codepointAtIndexNode.execute(s, index, TS_ENCODING);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            } catch (OverflowException e) {
                throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, PY_UCS4}, call = Ignored)
    abstract static class PyTruffleUnicode_New extends CApiTernaryBuiltinNode {
        @Specialization
        Object doGeneric(Object ptr, long elementSize, int isAscii) {
            return factory().createString(new NativeCharSequence(ptr, (int) elementSize, isAscii != 0));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_FromUCS extends CApiTernaryBuiltinNode {

        private Encoding encodingFromKind(int kind) throws PException {
            return switch (kind) {
                case 1 -> ISO_8859_1;
                case 2 -> UTF_16;
                case 4 -> TS_ENCODING;
                default -> throw raiseBadInternalCall();
            };
        }

        private PString asPString(TruffleString ts, SwitchEncodingNode switchEncodingNode) {
            return factory().createString(switchEncodingNode.execute(ts, TS_ENCODING));
        }

        @Specialization(guards = "ptrLib.isPointer(ptr)")
        Object doNative(Object ptr, long byteLength, int kind,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached FromNativePointerNode fromNativePointerNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode) {

            try {
                int iByteLength = PInt.intValueExact(byteLength);
                Encoding srcEncoding = encodingFromKind(kind);
                /*
                 * TODO(fa): TruffleString does currently not support creating strings from UCS1 and
                 * UCS2 bytes (GR-44312). Remind: UCS1 and UCS2 are actually compacted UTF-32 bytes.
                 * For now, we use ISO-8859-1 and UTF-16 but that's not entirely correct.
                 */
                TruffleString ts = fromNativePointerNode.execute(ptr, 0, iByteLength, srcEncoding, true);
                return asPString(ts, switchEncodingNode);
            } catch (OverflowException e) {
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = "!ptrLib.isPointer(ptr)")
        Object doManaged(Object ptr, long byteLength, int kind,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode) {
            try {
                Encoding srcEncoding = encodingFromKind(kind);
                byte[] ucsBytes = getByteArrayNode.execute(ptr, byteLength);
                TruffleString ts = fromByteArrayNode.execute(ucsBytes, srcEncoding);
                return asPString(ts, switchEncodingNode);
            } catch (InteropException e) {
                /*
                 * This means that we cannot read the array-like foreign object or the foreign
                 * elements cannot be interpreted as bytes. In any case, that's a fatal error.
                 */
                throw raise(SystemError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raise(MemoryError);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_FromString extends CApiUnaryBuiltinNode {
        @Specialization
        PString run(TruffleString str) {
            return factory().createString(str);
        }

        @Specialization
        static PString run(PString str) {
            return str;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_DecodeFSDefault extends CApiUnaryBuiltinNode {

        // TODO: this implementation does not honor Py_FileSystemDefaultEncoding and
        // Py_FileSystemDefaultEncodeErrors

        @Specialization
        PString run(TruffleString str) {
            return factory().createString(str);
        }

        @Specialization
        static PString run(PString str) {
            return str;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyUnicode_Contains extends CApiBinaryBuiltinNode {
        @Specialization
        static int contains(Object haystack, Object needle,
                        @Cached StringBuiltins.ContainsNode containsNode) {
            return containsNode.executeBool(haystack, needle) ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_Split extends CApiTernaryBuiltinNode {
        @Specialization
        Object split(Object string, Object sep, Object maxsplit,
                        @Cached StringBuiltins.SplitNode splitNode) {
            return splitNode.execute(null, string, sep, maxsplit);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, ConstCharPtrAsTruffleString, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_DecodeUTF8Stateful extends CApiTernaryBuiltinNode {

        @Specialization
        Object doUtf8Decode(Object cByteArray, TruffleString errors, @SuppressWarnings("unused") int reportConsumed,
                        @Cached GetByteArrayNode getByteArrayNode) {

            try {
                byte[] bytes = getByteArrayNode.execute(cByteArray, -1);
                return factory().createTuple(decode(errors, bytes));
            } catch (OverflowException e) {
                throw raise(PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }

        @TruffleBoundary
        private static Object[] decode(TruffleString errors, byte[] bytes) {
            ByteBuffer inputBuffer = wrap(bytes);
            int n = inputBuffer.remaining();
            CharBuffer resultBuffer = CharBuffer.allocate(n * 4);

            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, PRaiseNode.getUncached(), TruffleString.EqualNode.getUncached());
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
            int len = resultBuffer.position();
            TruffleString string;
            if (len > 0) {
                resultBuffer.rewind();
                string = toTruffleStringUncached(resultBuffer.subSequence(0, len).toString());
            } else {
                string = T_EMPTY_STRING;
            }
            return new Object[]{string, n - inputBuffer.remaining()};
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleUnicode_Decode extends CApiTernaryBuiltinNode {

        @Specialization
        Object doDecode(PMemoryView mv, TruffleString encoding, TruffleString errors,
                        @Cached CodecsModuleBuiltins.DecodeNode decodeNode) {
            return decodeNode.executeWithStrings(null, mv, encoding, errors);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyUnicode_EncodeFSDefault extends CApiUnaryBuiltinNode {
        @Specialization
        PBytes fromObject(TruffleString s,
                        @Shared("encode") @Cached EncodeNativeStringNode encode) {
            byte[] array = encode.execute(StandardCharsets.UTF_8, s, T_REPLACE);
            return factory().createBytes(array);
        }

        @Specialization
        PBytes fromObject(Object s,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("encode") @Cached EncodeNativeStringNode encode) {
            byte[] array = encode.execute(StandardCharsets.UTF_8, castStr.execute(s), T_REPLACE);
            return factory().createBytes(array);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, SIZE_T}, call = Ignored)
    abstract static class PyTruffle_Unicode_FromWchar extends CApiBinaryBuiltinNode {
        @Specialization
        Object doInt(Object arr, long elementSize,
                        @Cached UnicodeFromWcharNode unicodeFromWcharNode) {
            /*
             * If we receive a native wrapper here, we assume that it is one of the wrappers that
             * emulates some C array (e.g. CArrayWrapper or PySequenceArrayWrapper). Those wrappers
             * are directly handled by the node. Otherwise, it is assumed that the object is a typed
             * pointer object.
             */
            return factory().createString(unicodeFromWcharNode.execute(arr, castToInt(elementSize)));
        }
    }

    abstract static class NativeEncoderNode extends CApiBinaryBuiltinNode {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(PString s, @SuppressWarnings("unused") PNone errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            return doUnicode(s, T_STRICT, encodeNativeStringNode);
        }

        @Specialization
        Object doUnicode(PString s, TruffleString errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            return factory().createBytes(encodeNativeStringNode.execute(charset, s, errors));
        }

        @Fallback
        Object doUnicode(@SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors) {
            return raise(PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsLatin1String extends NativeEncoderNode {
        protected _PyUnicode_AsLatin1String() {
            super(StandardCharsets.ISO_8859_1);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsASCIIString extends NativeEncoderNode {
        protected _PyUnicode_AsASCIIString() {
            super(StandardCharsets.US_ASCII);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsUTF8String extends NativeEncoderNode {

        protected _PyUnicode_AsUTF8String() {
            super(StandardCharsets.UTF_8);
        }

        public static _PyUnicode_AsUTF8String create() {
            return PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringNodeGen.create();
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObject}, call = Direct)
    abstract static class PyTruffle_Unicode_AsUTF8AndSize_CharPtr extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doUnicode(PString s,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile profile,
                        @Cached _PyUnicode_AsUTF8String asUTF8String) {
            if (profile.profile(inliningTarget, s.getUtf8Bytes() == null)) {
                PBytes bytes = (PBytes) asUTF8String.execute(s, T_STRICT);
                s.setUtf8Bytes(bytes);
            }
            return new PySequenceArrayWrapper(s.getUtf8Bytes(), 1);
        }

        @Fallback
        Object doError(@SuppressWarnings("unused") Object s) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyTruffle_Unicode_AsUTF8AndSize_Size extends CApiUnaryBuiltinNode {

        @Specialization
        Object doUnicode(PString s) {
            // PyTruffle_Unicode_AsUTF8AndSize_CharPtr must have been be called before
            return s.getUtf8Bytes().getSequenceStorage().length();
        }
    }

    @CApiBuiltin(ret = PY_UNICODE_PTR, args = {PyObject}, call = Direct)
    abstract static class PyTruffle_Unicode_AsUnicodeAndSize_CharPtr extends CApiUnaryBuiltinNode {

        @Specialization
        Object doUnicode(PString s,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile profile,
                        @Cached CExtCommonNodes.SizeofWCharNode sizeofWcharNode,
                        @Cached UnicodeAsWideCharNode asWideCharNode) {
            if (profile.profile(inliningTarget, s.getWCharBytes() == null)) {
                PBytes bytes = asWideCharNode.executeNativeOrder(s, sizeofWcharNode.execute(getCApiContext()));
                s.setWCharBytes(bytes);
            }
            return new PySequenceArrayWrapper(s.getWCharBytes(), 1);
        }

        @Fallback
        Object doError(@SuppressWarnings("unused") Object s) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject}, call = Direct)
    abstract static class PyTruffle_Unicode_AsUnicodeAndSize_Size extends CApiUnaryBuiltinNode {

        @Specialization
        Object doUnicode(PString s,
                        @Cached CExtCommonNodes.SizeofWCharNode sizeofWcharNode) {
            // PyTruffle_Unicode_AsUnicodeAndSize_CharPtr must have been be called before
            return s.getWCharBytes().getSequenceStorage().length() / sizeofWcharNode.execute(getCApiContext());
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int}, call = Ignored)
    abstract static class PyTruffle_Unicode_DecodeUTF32 extends CApiQuaternaryBuiltinNode {

        @Specialization
        Object doUnicodeStringErrors(Object o, long size, TruffleString errors, int byteorder,
                        @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return decodeUTF32(getByteArrayNode.execute(o, size), (int) size, errors, byteorder);
            } catch (CharacterCodingException e) {
                throw raise(PythonErrorType.UnicodeEncodeError, ErrorMessages.M, e);
            } catch (IllegalArgumentException e) {
                TruffleString csName = Charsets.getUTF32Name(byteorder);
                throw raise(PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ENCODING, csName);
            } catch (InteropException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raise(OverflowError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @TruffleBoundary
        private TruffleString decodeUTF32(byte[] data, int size, TruffleString errors, int byteorder) throws CharacterCodingException {
            CharsetDecoder decoder = Charsets.getUTF32Charset(byteorder).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this, TruffleString.EqualNode.getUncached());
            CharBuffer decode = decoder.onMalformedInput(action).onUnmappableCharacter(action).decode(wrap(data, 0, size));
            return toTruffleStringUncached(decode.toString());
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Ignored)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Unicode_AsWideChar extends CApiBinaryBuiltinNode {
        @Specialization
        Object doUnicode(Object s, long elementSize,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached CastToTruffleStringNode castStr) {
            try {
                PBytes wchars = asWideCharNode.executeLittleEndian(castStr.execute(s), elementSize);
                if (wchars != null) {
                    return wchars;
                } else {
                    throw raise(PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                throw raise(PythonErrorType.LookupError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, VA_LIST_PTR}, call = CApiCallPath.Ignored)
    abstract static class PyTruffle_Unicode_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        Object doGeneric(TruffleString format, Object vaList,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode) {
            return unicodeFromFormatNode.execute(format, vaList);
        }
    }

    @CApiBuiltin(ret = _PY_ERROR_HANDLER, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _Py_GetErrorHandler extends CApiUnaryBuiltinNode {
        @Specialization
        Object doGeneric(TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Cached GetErrorHandlerNode getErrorHandlerNode) {
            return getErrorHandlerNode.execute(inliningTarget, errors).getNativeValue();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, ConstCharPtr, Py_ssize_t, Py_ssize_t, Py_ssize_t, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicodeDecodeError_Create extends CApi6BuiltinNode {
        @Specialization
        Object doit(Object encoding, Object object, int length, int start, int end, Object reason,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile isUnicodeDecode,
                        @Cached PConstructAndRaiseNode raiseNode,
                        @Cached GetByteArrayNode getByteArrayNode) {
            PBytes bytes;
            try {
                bytes = factory().createBytes(getByteArrayNode.execute(object, length));
            } catch (InteropException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
            try {
                throw raiseNode.executeWithArgsOnly(null, UnicodeDecodeError, new Object[]{encoding, bytes, start, end, reason});
            } catch (PException e) {
                e.expect(inliningTarget, UnicodeDecodeError, isUnicodeDecode);
                return e.getEscapedException();
            }
        }
    }
}
