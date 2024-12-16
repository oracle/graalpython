/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_16;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_16_BE;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_16_LE;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_32;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_32_BE;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.T_UTF_32_LE;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CONST_WCHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_UCS4;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_UNICODE_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor._PY_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode.castLong;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16LE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32LE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.ChrNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi6BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ReadUnicodeArrayNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
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
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
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

    static boolean isStringSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PString);
    }

    static boolean isAnyString(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return PGuards.isString(obj) || isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode);
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
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_FromObject extends CApiUnaryBuiltinNode {
        @Specialization
        static TruffleString fromObject(TruffleString s) {
            return s;
        }

        @Specialization(guards = "isPStringType(inliningTarget, s, getClassNode)")
        static PString fromObject(PString s,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode) {
            return s;
        }

        @Specialization(guards = {"!isPStringType(inliningTarget, obj, getClassNode)", "isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return strNode.executeWith(obj);
        }

        @Specialization(guards = {"!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, obj);
        }

        protected boolean isPStringType(Node inliningTarget, Object obj, GetClassNode getClassNode) {
            return getClassNode.execute(inliningTarget, obj) == PythonBuiltinClassType.PString;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Concat extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isString(left) || isStringSubtype(inliningTarget, left, getClassNode, isSubtypeNode)",
                        "isString(right) || isStringSubtype(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object concat(Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached StringBuiltins.ConcatNode addNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return addNode.execute(null, left, right);
        }

        @Specialization(guards = {"!isString(left)", "!isStringSubtype(inliningTarget, left, getClassNode, isSubtypeNode)"})
        static Object leftNotString(Object left, @SuppressWarnings("unused") Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, left);
        }

        @Specialization(guards = {"!isString(right)", "!isStringSubtype(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object rightNotString(@SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, right);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_FromEncodedObject extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object obj, Object encodingObj, Object errorsObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedExactClassProfile encodingProfile,
                        @Cached InlinedExactClassProfile errorsProfile,
                        @Cached InlinedConditionProfile nullProfile,
                        @Cached PyUnicodeFromEncodedObject decodeNode) {
            TruffleString encoding;
            Object encodingObjProfiled = encodingProfile.profile(inliningTarget, encodingObj);
            if (encodingObjProfiled == PNone.NO_VALUE) {
                encoding = T_UTF8;
            } else {
                assert encodingObjProfiled instanceof TruffleString;
                encoding = (TruffleString) encodingObjProfiled;
            }

            TruffleString errors;
            Object errorsObjProfiled = errorsProfile.profile(inliningTarget, errorsObj);
            if (errorsObjProfiled == PNone.NO_VALUE) {
                errors = T_STRICT;
            } else {
                assert errorsObjProfiled instanceof TruffleString;
                errors = (TruffleString) errorsObjProfiled;
            }
            if (nullProfile.profile(inliningTarget, obj == PNone.NO_VALUE)) {
                throw PRaiseNode.raiseUncached(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
            return decodeNode.execute(null, inliningTarget, obj, encoding, errors);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleUnicode_LookupAndIntern extends CApiUnaryBuiltinNode {

        @Specialization
        static Object withPString(PString str,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeCheckExactNode unicodeCheckExactNode,
                        @Cached CastToTruffleStringNode cast,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode,
                        @Cached StringNodes.InternStringNode internNode,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (!unicodeCheckExactNode.execute(inliningTarget, str)) {
                return getNativeNull(inliningTarget);
            }
            /*
             * TODO this is not integrated with str.intern, pointer comparisons of two str.intern'ed
             * string may still yield failse
             */
            boolean isInterned = isInternedStringNode.execute(inliningTarget, str);
            if (isInterned) {
                return str;
            }
            TruffleString ts = cast.execute(inliningTarget, str);
            PDict dict = getCApiContext(inliningTarget).getInternedUnicode();
            if (dict == null) {
                dict = factory.get(inliningTarget).createDict();
                getCApiContext(inliningTarget).setInternedUnicode(dict);
            }
            Object interned = getItem.execute(inliningTarget, dict.getDictStorage(), ts);
            if (interned == null) {
                interned = internNode.execute(inliningTarget, str);
                dict.setDictStorage(setItem.execute(inliningTarget, dict.getDictStorage(), ts, interned));
            }
            return interned;
        }

        @Fallback
        Object nil(@SuppressWarnings("unused") Object obj) {
            /*
             * If it's a subclass, we don't really know what putting it in the interned dict might
             * do.
             */
            return getNativeNull();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Format extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"isString(format) || isStringSubtype(inliningTarget, format, getClassNode, isSubtypeNode)"})
        static Object find(Object format, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached ModNode modNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            checkNonNullArg(inliningTarget, format, args, raiseNode);
            return modNode.execute(null, format, args);
        }

        @Specialization(guards = {"!isTruffleString(format)", "isStringSubtype(inliningTarget, format, getClassNode, isSubtypeNode)"})
        static Object find(Object format, @SuppressWarnings("unused") Object args,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            checkNonNullArg(inliningTarget, format, args, raiseNode);
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, format);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PY_UCS4, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_FindChar extends CApi5BuiltinNode {
        @Specialization(guards = {"isString(string) || isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)", "direction > 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached ChrNode chrNode,
                        @Cached FindNode findNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return findNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"isString(string) || isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)", "direction <= 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached ChrNode chrNode,
                        @Cached RFindNode rFindNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return rFindNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"!isTruffleString(string)", "!isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)"})
        static Object find(Object string, @SuppressWarnings("unused") Object c, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") Object direction,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Substring extends CApiTernaryBuiltinNode {
        @Specialization(guards = {"isString(s) || isStringSubtype(s, inliningTarget, getClassNode, isSubtypeNode)"}, limit = "1")
        static Object doString(Object s, long start, long end,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile profile,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            if (profile.profile(inliningTarget, start < 0 || end < 0)) {
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, s, T___GETITEM__);
            return callNode.executeWithoutFrame(getItemCallable, sliceNode.execute(inliningTarget, start, end, PNone.NONE));
        }

        @Specialization(guards = {"!isTruffleString(s)", "isStringSubtype(s, inliningTarget, getClassNode, isSubtypeNode)"}, limit = "1")
        static Object doError(Object s, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, s);
        }

        protected static boolean isStringSubtype(Object obj, Node n, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(n, obj), PythonBuiltinClassType.PString);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Join extends CApiBinaryBuiltinNode {
        @Specialization(guards = {"isString(separator) || isStringSubtype(inliningTarget, separator, getClassNode, isSubtypeNode)"})
        static Object find(Object separator, Object seq,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached StringBuiltins.JoinNode joinNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return joinNode.execute(null, separator, seq);
        }

        @Specialization(guards = {"!isTruffleString(separator)", "isStringSubtype(inliningTarget, separator, getClassNode, isSubtypeNode)"})
        static Object find(Object separator, @SuppressWarnings("unused") Object seq,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, separator);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class _PyUnicode_EqualToASCIIString extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(inliningTarget, left, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EqNode eqNode,
                        @Cached PyObjectIsTrueNode isTrue) {
            return PInt.intValue(isTrue.execute(null, inliningTarget, eqNode.execute(null, left, right)));
        }

        @Specialization(guards = {"!isAnyString(inliningTarget, left, getClassNode, isSubtypeNode) || !isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObjectAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_CompareWithASCIIString extends CApiBinaryBuiltinNode {

        @Specialization
        static int compare(TruffleString left, TruffleString right,
                        @Cached TruffleString.CompareIntsUTF32Node compare) {
            return compare.execute(left, right);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Compare extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(inliningTarget, left, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EqNode eqNode,
                        @Cached LtNode ltNode,
                        @Cached InlinedConditionProfile eqProfile) {
            if (eqProfile.profile(inliningTarget, (boolean) eqNode.execute(null, left, right))) {
                return 0;
            } else {
                return (boolean) ltNode.execute(null, left, right) ? -1 : 1;
            }
        }

        @Specialization(guards = {"!isAnyString(inliningTarget, left, getClassNode, isSubtypeNode) || !isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Tailmatch extends CApi5BuiltinNode {
        @Specialization(guards = {"isAnyString(inliningTarget, string, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, substring, getClassNode, isSubtypeNode)", "direction > 0"})
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectLookupAttr lookupAttrNode,
                        @Shared @Cached PySliceNew sliceNode,
                        @Shared @Cached CallNode callNode,
                        @Cached EndsWithNode endsWith,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, string, T___GETITEM__);
            Object slice = callNode.executeWithoutFrame(getItemCallable, sliceNode.execute(inliningTarget, start, end, PNone.NONE));
            return (boolean) endsWith.execute(null, slice, substring, start, end) ? 1 : 0;
        }

        @Specialization(guards = {"isAnyString(inliningTarget, string, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, substring, getClassNode, isSubtypeNode)", "direction <= 0"})
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectLookupAttr lookupAttrNode,
                        @Shared @Cached PySliceNew sliceNode,
                        @Shared @Cached CallNode callNode,
                        @Cached StartsWithNode startsWith,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, string, T___GETITEM__);
            Object slice = callNode.executeWithoutFrame(getItemCallable, sliceNode.execute(inliningTarget, start, end, PNone.NONE));
            return (boolean) startsWith.execute(null, slice, substring, start, end) ? 1 : 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isAnyString(inliningTarget, string, getClassNode, isSubtypeNode) || !isAnyString(inliningTarget, substring, getClassNode, isSubtypeNode)"})
        static Object find(Object string, Object substring, Object start, Object end, Object direction,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_AsEncodedString extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isString(obj) || isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)")
        static Object encode(Object obj, Object encoding, Object errors,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EncodeNode encodeNode) {
            return encodeNode.execute(null, obj, convertEncoding(encoding), convertErrors(errors));
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object encode(@SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Replace extends CApiQuaternaryBuiltinNode {
        @Specialization(guards = {"isString(s)", "isString(substr)", "isString(replstr)"})
        static Object replace(Object s, Object substr, Object replstr, long count,
                        @Shared @Cached ReplaceNode replaceNode) {
            return replaceNode.execute(null, s, substr, replstr, count);
        }

        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "isStringSubtype(inliningTarget, s, getClassNode, isSubtypeNode)",
                        "isStringSubtype(inliningTarget, substr, getClassNode, isSubtypeNode)",
                        "isStringSubtype(inliningTarget, replstr, getClassNode, isSubtypeNode)"})
        static Object replace(Object s, Object substr, Object replstr, long count,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached ReplaceNode replaceNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return replace(s, substr, replstr, count, replaceNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "!isStringSubtype(inliningTarget, s, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(inliningTarget, substr, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(inliningTarget, replstr, getClassNode, isSubtypeNode)"})
        static Object replace(Object s, Object substr, Object replstr, long count,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return getNativeNull(inliningTarget);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_AsUnicodeEscapeString extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isString(s)")
        static Object escape(Object s,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CodecsEncodeNode encodeNode,
                        @Shared @Cached PyTupleGetItem getItemNode) {
            return getItemNode.execute(inliningTarget, encodeNode.execute(null, s, T_UNICODE_ESCAPE, PNone.NO_VALUE), 0);
        }

        @Specialization(guards = {"!isString(s)", "isStringSubtype(inliningTarget, s, getClassNode, isSubtypeNode)"})
        static Object escape(Object s,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Shared @Cached CodecsEncodeNode encodeNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PyTupleGetItem getItemNode) {
            return escape(s, inliningTarget, encodeNode, getItemNode);
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object escape(@SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PY_UCS4, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_ReadChar extends CApiBinaryBuiltinNode {
        @Specialization
        static int doGeneric(Object type, long lindex,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                TruffleString s = castToStringNode.execute(inliningTarget, type);
                int index = PInt.intValueExact(lindex);
                // avoid StringIndexOutOfBoundsException
                if (index < 0 || index >= lengthNode.execute(s, TS_ENCODING)) {
                    throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                }
                return codepointAtIndexNode.execute(s, index, TS_ENCODING);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Py_ssize_t, PY_UCS4}, call = Ignored)
    abstract static class PyTruffleUnicode_New extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object ptr, long elements, long elementSize, int isAscii,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(new NativeCharSequence(ptr, (int) elements, (int) elementSize, isAscii != 0));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_FromUCS extends CApiTernaryBuiltinNode {

        private static Encoding encodingFromKind(Node inliningTarget, int kind, PRaiseNode.Lazy raiseNode) throws PException {
            return switch (kind) {
                case 1 -> ISO_8859_1;
                case 2 -> UTF_16;
                case 4 -> TS_ENCODING;
                default -> throw raiseNode.get(inliningTarget).raiseBadInternalCall();
            };
        }

        private static PString asPString(TruffleString ts, SwitchEncodingNode switchEncodingNode, PythonObjectFactory factory) {
            return factory.createString(switchEncodingNode.execute(ts, TS_ENCODING));
        }

        @Specialization(guards = "ptrLib.isPointer(ptr)")
        static Object doNative(Object ptr, long byteLength, int kind,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached FromNativePointerNode fromNativePointerNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int iByteLength = PInt.intValueExact(byteLength);
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                /*
                 * TODO(fa): TruffleString does currently not support creating strings from UCS1 and
                 * UCS2 bytes (GR-44312). Remind: UCS1 and UCS2 are actually compacted UTF-32 bytes.
                 * For now, we use ISO-8859-1 and UTF-16 but that's not entirely correct.
                 */
                TruffleString ts = fromNativePointerNode.execute(ptr, 0, iByteLength, srcEncoding, true);
                return asPString(ts, switchEncodingNode, factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }
        }

        @Specialization(guards = "!ptrLib.isPointer(ptr)")
        static Object doManaged(Object ptr, long byteLength, int kind,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            try {
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                byte[] ucsBytes = getByteArrayNode.execute(inliningTarget, ptr, byteLength);
                TruffleString ts = fromByteArrayNode.execute(ucsBytes, srcEncoding);
                return asPString(ts, switchEncodingNode, factory);
            } catch (InteropException e) {
                /*
                 * This means that we cannot read the array-like foreign object or the foreign
                 * elements cannot be interpreted as bytes. In any case, that's a fatal error.
                 */
                throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_FromUTF extends CApiTernaryBuiltinNode {

        private static Encoding encodingFromKind(Node inliningTarget, int kind, PRaiseNode.Lazy raiseNode) throws PException {
            return switch (kind) {
                case 1 -> UTF_8;
                case 2 -> UTF_16LE;
                case 4 -> UTF_32LE;
                default -> throw raiseNode.get(inliningTarget).raiseBadInternalCall();
            };
        }

        private static PString asPString(TruffleString ts, SwitchEncodingNode switchEncodingNode, PythonObjectFactory factory) {
            return factory.createString(switchEncodingNode.execute(ts, TS_ENCODING));
        }

        @Specialization(guards = "ptrLib.isPointer(ptr)")
        static Object doNative(Object ptr, long byteLength, int kind,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached FromNativePointerNode fromNativePointerNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int iByteLength = PInt.intValueExact(byteLength);
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                TruffleString ts = fromNativePointerNode.execute(ptr, 0, iByteLength, srcEncoding, true);
                return asPString(ts, switchEncodingNode, factory);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }
        }

        @Specialization(guards = "!ptrLib.isPointer(ptr)")
        static Object doManaged(Object ptr, long byteLength, int kind,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            try {
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                byte[] ucsBytes = getByteArrayNode.execute(inliningTarget, ptr, byteLength);
                TruffleString ts = fromByteArrayNode.execute(ucsBytes, srcEncoding);
                return asPString(ts, switchEncodingNode, factory);
            } catch (InteropException e) {
                /*
                 * This means that we cannot read the array-like foreign object or the foreign
                 * elements cannot be interpreted as bytes. In any case, that's a fatal error.
                 */
                throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_FromString extends CApiUnaryBuiltinNode {
        @Specialization
        static PString run(TruffleString str,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(str);
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
        static Object split(Object string, Object sep, Object maxsplit,
                        @Cached StringBuiltins.SplitNode splitNode) {
            return splitNode.execute(null, string, sep, maxsplit);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_DecodeUTF8Stateful extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doUtf8Decode(Object cByteArray, long size, TruffleString errors, int reportConsumed,
                        @Bind("this") Node inliningTarget,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PBytes bytes = factory.createBytes(getByteArrayNode.execute(inliningTarget, cByteArray, size));
                return decode.call(null, bytes, T_UTF8, errors, reportConsumed == 0);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_DecodeUTF16Stateful extends CApi5BuiltinNode {

        @Specialization
        static Object decode(Object cByteArray, long size, TruffleString errors, int byteorder, int reportConsumed,
                        @Bind("this") Node inliningTarget,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PBytes bytes = factory.createBytes(getByteArrayNode.execute(inliningTarget, cByteArray, size));
                TruffleString encoding;
                if (byteorder == 0) {
                    encoding = T_UTF_16;
                } else if (byteorder < 0) {
                    encoding = T_UTF_16_LE;
                } else {
                    encoding = T_UTF_16_BE;
                }
                return decode.call(null, bytes, encoding, errors, reportConsumed == 0);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Int}, call = Ignored)
    abstract static class PyTruffleUnicode_DecodeUTF32Stateful extends CApi5BuiltinNode {

        @Specialization
        static Object decode(Object cByteArray, long size, TruffleString errors, int byteorder, int reportConsumed,
                        @Bind("this") Node inliningTarget,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PBytes bytes = factory.createBytes(getByteArrayNode.execute(inliningTarget, cByteArray, size));
                TruffleString encoding;
                if (byteorder == 0) {
                    encoding = T_UTF_32;
                } else if (byteorder < 0) {
                    encoding = T_UTF_32_LE;
                } else {
                    encoding = T_UTF_32_BE;
                }
                return decode.call(null, bytes, encoding, errors, reportConsumed == 0);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleUnicode_Decode extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doDecode(PMemoryView mv, TruffleString encoding, TruffleString errors,
                        @Cached CodecsModuleBuiltins.DecodeNode decodeNode) {
            return decodeNode.executeWithStrings(null, mv, encoding, errors);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyUnicode_EncodeFSDefault extends CApiUnaryBuiltinNode {
        @Specialization
        static PBytes fromObject(Object s,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached EncodeNativeStringNode encode,
                        @Cached PythonObjectFactory factory) {
            byte[] array = encode.execute(StandardCharsets.UTF_8, castStr.execute(inliningTarget, s), T_REPLACE);
            return factory.createBytes(array);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_EncodeLocale extends CApiBinaryBuiltinNode {
        @Specialization
        static Object encode(Object s, Object errors,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode cast,
                        @Cached CodecsTruffleModuleBuiltins.GetEncodingNode getEncodingNode,
                        @Cached CodecsModuleBuiltins.EncodeNode encodeNode) {
            return encodeNode.execute(null, cast.execute(inliningTarget, s), getEncodingNode.execute(null), errors);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {CONST_WCHAR_PTR, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_FromWideChar extends CApiBinaryBuiltinNode {
        @Specialization
        Object doInt(Object arr, long size,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadUnicodeArrayNode readArray,
                        @Cached TruffleString.FromIntArrayUTF32Node fromArray,
                        @Cached PythonObjectFactory factory) {
            assert TS_ENCODING == Encoding.UTF_32 : "needs switch_encoding otherwise";
            return factory.createString(fromArray.execute(readArray.execute(inliningTarget, arr, castToInt(size), CStructs.wchar_t.size())));
        }
    }

    abstract static class NativeEncoderNode extends CApiBinaryBuiltinNode {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(Object s, @SuppressWarnings("unused") PNone errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return doUnicode(s, T_STRICT, encodeNativeStringNode, factory);
        }

        @Specialization
        Object doUnicode(Object s, TruffleString errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(encodeNativeStringNode.execute(charset, s, errors));
        }

        @Fallback
        static Object doUnicode(@SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors,
                        @Cached PRaiseNode raiseNode) {
            return raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
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

        @NeverDefault
        public static _PyUnicode_AsUTF8String create() {
            return PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringNodeGen.create();
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObject, PY_SSIZE_T_PTR}, call = Ignored)
    abstract static class PyTruffleUnicode_AsUTF8AndSize extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doUnicode(PString s, Object sizePtr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached InlinedConditionProfile hasSizeProfile,
                        @Cached InlinedConditionProfile hasUtf8Profile,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached _PyUnicode_AsUTF8String asUTF8String) {
            if (hasUtf8Profile.profile(inliningTarget, s.getUtf8Bytes() == null)) {
                PBytes bytes = (PBytes) asUTF8String.execute(s, T_STRICT);
                s.setUtf8Bytes(bytes);
            }
            if (hasSizeProfile.profile(inliningTarget, !lib.isNull(sizePtr))) {
                writeLongNode.write(sizePtr, s.getUtf8Bytes().getSequenceStorage().length());
            }
            return PySequenceArrayWrapper.ensureNativeSequence(s.getUtf8Bytes());
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doError(Object s, Object sizePtr,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleUnicode_FillUtf8 extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doNative(PythonAbstractNativeObject s,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached _PyUnicode_AsUTF8String asUTF8String,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.AllocateNode allocateNode,
                        @Cached CStructAccess.WriteByteNode writeByteNode) {
            PBytes bytes = (PBytes) asUTF8String.execute(s, T_STRICT);
            int len = bufferLib.getBufferLength(bytes);
            Object mem = allocateNode.alloc(len + 1, true);
            writeByteNode.writeByteArray(mem, bufferLib.getInternalOrCopiedByteArray(bytes), len, 0, 0);
            writePointerNode.writeToObj(s, CFields.PyCompactUnicodeObject__utf8, mem);
            writeLongNode.writeToObject(s, CFields.PyCompactUnicodeObject__utf8_length, len);
            return 0;
        }
    }

    @CApiBuiltin(ret = PY_UNICODE_PTR, args = {PyObject, PY_SSIZE_T_PTR}, call = Ignored)
    abstract static class PyTruffleUnicode_AsUnicodeAndSize extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doUnicode(PString s, Object sizePtr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached InlinedConditionProfile hasSizeProfile,
                        @Cached InlinedConditionProfile hasUnicodeProfile,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached UnicodeAsWideCharNode asWideCharNode) {
            int wcharSize = CStructs.wchar_t.size();
            if (hasUnicodeProfile.profile(inliningTarget, s.getWCharBytes() == null)) {
                PBytes bytes = asWideCharNode.executeNativeOrder(inliningTarget, s, wcharSize);
                s.setWCharBytes(bytes);
            }
            if (hasSizeProfile.profile(inliningTarget, !lib.isNull(sizePtr))) {
                writeLongNode.write(sizePtr, s.getWCharBytes().getSequenceStorage().length() / wcharSize);
            }
            return PySequenceArrayWrapper.ensureNativeSequence(s.getWCharBytes());
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doError(Object s, Object sizePtr,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleUnicode_FillUnicode extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doNative(PythonAbstractNativeObject s,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode cast,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.AllocateNode allocateNode,
                        @Cached CStructAccess.WriteByteNode writeByteNode) {
            int wcharSize = CStructs.wchar_t.size();
            PBytes bytes = asWideCharNode.executeNativeOrder(inliningTarget, cast.castKnownString(inliningTarget, s), wcharSize);
            int len = bufferLib.getBufferLength(bytes);
            Object mem = allocateNode.alloc(len + wcharSize, true);
            writeByteNode.writeByteArray(mem, bufferLib.getInternalOrCopiedByteArray(bytes), len, 0, 0);
            writePointerNode.writeToObj(s, CFields.PyASCIIObject__wstr, mem);
            writeLongNode.writeToObject(s, CFields.PyCompactUnicodeObject__wstr_length, len / wcharSize);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Ignored)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Unicode_AsWideChar extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doUnicode(Object s, long elementSize,
                        @Bind("this") Node inliningTarget,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PBytes wchars = asWideCharNode.executeLittleEndian(inliningTarget, castStr.execute(inliningTarget, s), elementSize);
                if (wchars != null) {
                    return wchars;
                } else {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.LookupError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, VA_LIST_PTR}, call = CApiCallPath.Ignored)
    abstract static class PyTruffle_Unicode_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(TruffleString format, Object vaList,
                        @Bind("this") Node inliningTarget,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode) {
            return unicodeFromFormatNode.execute(inliningTarget, format, vaList);
        }
    }

    @CApiBuiltin(ret = _PY_ERROR_HANDLER, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _Py_GetErrorHandler extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Cached ErrorHandlers.GetErrorHandlerNode getErrorHandlerNode) {
            return getErrorHandlerNode.execute(inliningTarget, errors).getNativeValue();
        }

        @Specialization
        static Object doNull(@SuppressWarnings("unused") PNone noValue) {
            return ErrorHandlers.ErrorHandler.STRICT.getNativeValue();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, ConstCharPtr, Py_ssize_t, Py_ssize_t, Py_ssize_t, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicodeDecodeError_Create extends CApi6BuiltinNode {
        @Specialization
        static Object doit(Object encoding, Object object, long length, long start, long end, Object reason,
                        @Bind("this") Node inliningTarget,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CallNode callNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PBytes bytes;
            try {
                bytes = factory.createBytes(getByteArrayNode.execute(inliningTarget, object, length));
            } catch (InteropException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
            return callNode.executeWithoutFrame(UnicodeDecodeError, encoding, bytes, start, end, reason);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, call = Ignored)
    abstract static class PyTruffle_PyUnicode_Find extends CApi5BuiltinNode {
        @Specialization(guards = "direction > 0")
        long find(Object string, Object sub, long start, long end, @SuppressWarnings("unused") int direction,
                        @Cached StringBuiltins.FindNode findNode) {
            return convertResult(findNode.execute(string, sub, castLong(start), castLong(end)));
        }

        @Specialization(guards = "direction <= 0")
        long find(Object string, Object sub, long start, long end, @SuppressWarnings("unused") int direction,
                        @Cached StringBuiltins.RFindNode rFindNode) {
            return convertResult(rFindNode.execute(string, sub, castLong(start), castLong(end)));
        }

        private static int convertResult(int result) {
            /*
             * PyUnicode_Find should return -1 for "not found" and -2 for exception. Our int upcalls
             * harcode -1 for exception return, so we use -2 for "not found" here and correct it on
             * the C side.
             */
            return result >= 0 ? result : -2;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_Count extends CApiQuaternaryBuiltinNode {
        @Specialization
        long count(Object string, Object sub, long start, long end,
                        @Cached StringBuiltins.CountNode countNode) {
            return countNode.execute(string, sub, castLong(start), castLong(end));
        }
    }
}
