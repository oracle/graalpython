/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
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
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_ENCODING;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectConstPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor._PY_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.ErrorMessages.PRECISION_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.SEPARATOR_EXPECTED_STR_INSTANCE_P_FOUND;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode.castLong;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16LE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32LE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.ChrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltins.PyNumber_ToBase;
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
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.NativeStringData;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.FindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.RFindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ReplaceNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.StartsWithNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.ConcurrentWeakSet;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.strings.TruffleStringBuilder;

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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode) {
            return s;
        }

        @Specialization(guards = {"!isPStringType(inliningTarget, obj, getClassNode)", "isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached StringBuiltins.StrNewNode strNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return strNode.executeWith(obj);
        }

        @Specialization(guards = {"!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object fromObject(Object obj,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, obj);
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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached StringBuiltins.ConcatNode addNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return addNode.execute(null, left, right);
        }

        @Specialization(guards = {"!isString(left)", "!isStringSubtype(inliningTarget, left, getClassNode, isSubtypeNode)"})
        static Object leftNotString(Object left, @SuppressWarnings("unused") Object right,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, left);
        }

        @Specialization(guards = {"!isString(right)", "!isStringSubtype(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object rightNotString(@SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, right);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_FromEncodedObject extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object obj, Object encodingObj, Object errorsObj,
                        @Bind Node inliningTarget,
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
                throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
            return decodeNode.execute(null, inliningTarget, obj, encoding, errors);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_LookupAndIntern extends CApiUnaryBuiltinNode {

        @Specialization
        static Object withPString(PString str,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckExactNode unicodeCheckExactNode) {
            if (!unicodeCheckExactNode.execute(inliningTarget, str)) {
                return getNativeNull(inliningTarget);
            }

            str.intern();
            /*
             * TODO this is not integrated with str.intern, pointer comparisons of two str.intern'ed
             * string may still yield failse
             */
            ConcurrentWeakSet<PString> interningCache = PythonContext.get(inliningTarget).getCApiContext().getPstringInterningCache();
            return interningCache.intern(str, s -> s);
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

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int, Int, Int}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class _PyUnicode_FormatLong extends CApiQuaternaryBuiltinNode {

        protected static boolean isOF(int prec) {
            return prec > Integer.MAX_VALUE - 3;
        }

        @TruffleBoundary
        private static char[] getCharArray(String s) {
            return s.toCharArray();
        }

        @Specialization(guards = "isOF(prec)")
        @SuppressWarnings("unused")
        static Object overflow(Object val, int alt, int prec, int type,
                        @Bind Node inliningTarget) {
            /* Avoid exceeding SSIZE_T_MAX */
            throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, PRECISION_TOO_LARGE);
        }

        @Specialization(guards = "!isOF(prec)")
        static Object formatLong(Object val, int alt, int prec, int type,
                        @Bind Node inliningTarget,
                        @Cached OctNode toOctBase,
                        @Cached HexNode toHexBase,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached StringBuiltins.StrNewNode strNode,
                        @Cached CastToJavaStringNode cast,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayNode) {
            int numnondigits = 0;
            TruffleString result;
            switch (type) {
                case 'd', 'i', 'u' ->
                    /* int and int subclasses should print numerically when a numeric */
                    /* format code is used (see issue18780) */
                    result = (TruffleString) PyNumber_ToBase.toBase10(val, 10, inliningTarget, indexNode, strNode);
                case 'o' -> {
                    numnondigits = 2;
                    result = (TruffleString) PyNumber_ToBase.toBase8(val, 8, toOctBase);
                }
                case 'x', 'X' -> {
                    numnondigits = 2;
                    result = (TruffleString) PyNumber_ToBase.toBase16(val, 16, inliningTarget, indexNode, toHexBase);
                }
                default -> {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
                }
            }

            char[] buf = getCharArray(cast.execute(result));
            int bufi = 0;
            int len = buf.length;
            /*- mq: this beyond the java limit anyway
            llen = buf.length;
            if (llen > Integer.MAX_VALUE) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, STRING_TOO_LARGE_IN_PY_UNICODE_FORMAT_LONG);
            }
            int len = (int)llen;
            */
            int sign = buf[0] == '-' ? 1 : 0;
            numnondigits += sign;
            int numdigits = len - numnondigits;
            assert (numdigits > 0);

            /* Get rid of base marker unless F_ALT */
            if ((alt == 0 && (type == 'o' || type == 'x' || type == 'X'))) {
                assert (buf[bufi + sign] == '0');
                assert (buf[bufi + sign + 1] == 'x' || buf[sign + 1] == 'X' || buf[bufi + sign + 1] == 'o');
                numnondigits -= 2;
                bufi += 2;
                len -= 2;
                /*- mq: already has this value
                if (sign != 0)
                    buf[0] = '-';
                 */
                assert (len == numnondigits + numdigits);
            }

            /* Fill with leading zeroes to meet minimum width. */
            if (prec > numdigits) {
                char[] b1 = new char[numnondigits + prec];
                int b1i = 0;
                for (int i = 0; i < numnondigits; ++i) {
                    b1[b1i++] = buf[bufi++];
                }
                for (int i = 0; i < prec - numdigits; i++) {
                    b1[b1i++] = '0';
                }
                for (int i = 0; i < numdigits; i++) {
                    b1[b1i++] = buf[bufi++];
                    b1[b1i] = '\0';
                }
                buf = b1;
                len = numnondigits + prec;
            }

            /* Fix up case for hex conversions. */
            if (type == 'X') {
                /*
                 * Need to convert all lower case letters to upper case. and need to convert 0x to
                 * 0X (and -0x to -0X).
                 */
                for (int i = bufi; i < len; i++) {
                    if (buf[i] >= 'a' && buf[i] <= 'x') {
                        buf[i] -= 'a' - 'A';
                    }
                }
            }
            return fromCharArrayNode.execute(buf);
        }

    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PY_UCS4, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_FindChar extends CApi5BuiltinNode {
        @Specialization(guards = {"isString(string) || isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)", "direction > 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") int direction,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Shared @Cached ChrNode chrNode,
                        @Cached FindNode findNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return findNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"isString(string) || isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)", "direction <= 0"})
        static Object find(Object string, Object c, long start, long end, @SuppressWarnings("unused") int direction,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Shared @Cached ChrNode chrNode,
                        @Cached RFindNode rFindNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return rFindNode.execute(null, string, chrNode.execute(null, c), start, end);
        }

        @Specialization(guards = {"!isTruffleString(string)", "!isStringSubtype(inliningTarget, string, getClassNode, isSubtypeNode)"})
        static Object find(Object string, @SuppressWarnings("unused") Object c, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") Object direction,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Py_ssize_t, Py_ssize_t}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Substring extends CApiTernaryBuiltinNode {
        @Specialization(guards = {"isString(s) || isStringSubtype(s, inliningTarget, getClassNode, isSubtypeNode)"}, limit = "1")
        static Object doString(Object s, long start, long end,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile profile,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySliceNew sliceNode,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            if (profile.profile(inliningTarget, start < 0 || end < 0)) {
                throw PRaiseNode.raiseStatic(inliningTarget, IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
            Object getItemCallable = lookupAttrNode.execute(null, inliningTarget, s, T___GETITEM__);
            return callNode.executeWithoutFrame(getItemCallable, sliceNode.execute(inliningTarget, start, end, PNone.NONE));
        }

        @Specialization(guards = {"!isTruffleString(s)", "isStringSubtype(s, inliningTarget, getClassNode, isSubtypeNode)"}, limit = "1")
        static Object doError(Object s, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, s);
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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached StringBuiltins.JoinNode joinNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return joinNode.execute(null, separator, seq);
        }

        @Specialization(guards = {"!isTruffleString(separator)", "isStringSubtype(inliningTarget, separator, getClassNode, isSubtypeNode)"})
        static Object find(Object separator, @SuppressWarnings("unused") Object seq,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, separator);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class _PyUnicode_EqualToASCIIString extends CApiBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(inliningTarget, left, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StringBuiltins.StringRichCmpNode eqNode,
                        @Cached PyObjectIsTrueNode isTrue) {
            return PInt.intValue(isTrue.execute(null, eqNode.execute(null, left, right, RichCmpOp.Py_EQ)));
        }

        @Specialization(guards = {"!isAnyString(inliningTarget, left, getClassNode, isSubtypeNode) || !isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_COMPARE, left, right);
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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StringBuiltins.StringRichCmpNode eqNode,
                        @Cached StringBuiltins.StringRichCmpNode ltNode,
                        @Cached InlinedConditionProfile eqProfile) {
            if (eqProfile.profile(inliningTarget, (boolean) eqNode.execute(null, left, right, RichCmpOp.Py_EQ))) {
                return 0;
            } else {
                return (boolean) ltNode.execute(null, left, right, RichCmpOp.Py_LT) ? -1 : 1;
            }
        }

        @Specialization(guards = {"!isAnyString(inliningTarget, left, getClassNode, isSubtypeNode) || !isAnyString(inliningTarget, right, getClassNode, isSubtypeNode)"})
        static Object compare(Object left, Object right,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_Tailmatch extends CApi5BuiltinNode {
        @Specialization(guards = {"isAnyString(inliningTarget, string, getClassNode, isSubtypeNode)", "isAnyString(inliningTarget, substring, getClassNode, isSubtypeNode)", "direction > 0"})
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") int direction,
                        @Bind Node inliningTarget,
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
        static int tailmatch(Object string, Object substring, long start, long end, @SuppressWarnings("unused") int direction,
                        @Bind Node inliningTarget,
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
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_AsEncodedString extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isString(obj) || isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)")
        static Object encode(Object obj, Object encoding, Object errors,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EncodeNode encodeNode) {
            return encodeNode.execute(null, obj, convertEncoding(encoding), convertErrors(errors));
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object encode(@SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject, Py_ssize_t}, call = Direct)
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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
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
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            return getNativeNull(inliningTarget);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObjectConstPtr, Py_ssize_t}, call = Direct)
    @TypeSystemReference(PythonIntegerTypes.class)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class _PyUnicode_JoinArray extends CApiTernaryBuiltinNode {
        @Specialization
        static Object join(Object separatorObj, Object itemsObj, long seqlenlong,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode readNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            if (seqlenlong == 0) {
                return T_EMPTY_STRING;
            }

            TruffleString separator = T_SPACE;
            if (separatorObj != PNone.NO_VALUE) {
                if (PGuards.isString(separatorObj)) {
                    separator = toTruffleStringNode.execute(inliningTarget, separatorObj);
                } else {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, SEPARATOR_EXPECTED_STR_INSTANCE_P_FOUND, separatorObj);
                }
            }
            int seqlen = (int) seqlenlong;
            assert seqlen == seqlenlong;
            Object[] items = readNode.readPyObjectArray(itemsObj, seqlen);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            for (int i = 0; i < items.length; i++) {
                TruffleString item = toTruffleStringNode.execute(inliningTarget, items[i]);
                if (i != 0) {
                    appendStringNode.execute(sb, separator);
                }
                appendStringNode.execute(sb, item);
            }
            return toStringNode.execute(sb);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    @ImportStatic(PythonCextUnicodeBuiltins.class)
    abstract static class PyUnicode_AsUnicodeEscapeString extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isString(s)")
        static Object escape(Object s,
                        @Bind Node inliningTarget,
                        @Shared @Cached CodecsEncodeNode encodeNode,
                        @Shared @Cached PyTupleGetItem getItemNode) {
            return getItemNode.execute(inliningTarget, encodeNode.execute(null, s, T_UNICODE_ESCAPE, PNone.NO_VALUE), 0);
        }

        @Specialization(guards = {"!isString(s)", "isStringSubtype(inliningTarget, s, getClassNode, isSubtypeNode)"})
        static Object escape(Object s,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Shared @Cached CodecsEncodeNode encodeNode,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached PyTupleGetItem getItemNode) {
            return escape(s, inliningTarget, encodeNode, getItemNode);
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object escape(@SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PY_UCS4, args = {PyObject, Py_ssize_t}, call = Direct)
    abstract static class PyUnicode_ReadChar extends CApiBinaryBuiltinNode {
        @Specialization
        static int doGeneric(Object type, long lindex,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                TruffleString s = castToStringNode.execute(inliningTarget, type);
                int index = PInt.intValueExact(lindex);
                // avoid StringIndexOutOfBoundsException
                if (index < 0 || index >= lengthNode.execute(s, TS_ENCODING)) {
                    throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                }
                return codepointAtIndexNode.execute(s, index, TS_ENCODING);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_New extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object ptr, long elements, int charSize, int isAscii,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HiddenAttr.WriteNode writeNode,
                        @Cached PRaiseNode raiseNode) {
            long size = elements * charSize;
            if (!PInt.isIntRange(size)) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            PString s = PFactory.createString(language, null);
            NativeStringData data = NativeStringData.create(charSize, isAscii != 0, ptr, (int) size);
            s.setNativeStringData(inliningTarget, writeNode, data);
            return s;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_FromUCS extends CApiTernaryBuiltinNode {

        private static Encoding encodingFromKind(Node inliningTarget, int kind, PRaiseNode raiseNode) throws PException {
            return switch (kind) {
                case 1 -> ISO_8859_1;
                case 2 -> UTF_16;
                case 4 -> TS_ENCODING;
                default -> throw raiseNode.raiseBadInternalCall(inliningTarget);
            };
        }

        @Specialization(guards = "ptrLib.isPointer(ptr)")
        static Object doNative(Object ptr, long byteLength, int kind,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached FromNativePointerNode fromNativePointerNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                int iByteLength = PInt.intValueExact(byteLength);
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                /*
                 * TODO(fa): TruffleString does currently not support creating strings from UCS1 and
                 * UCS2 bytes (GR-44312). Remind: UCS1 and UCS2 are actually compacted UTF-32 bytes.
                 * For now, we use ISO-8859-1 and UTF-16 but that's not entirely correct.
                 */
                TruffleString ts = fromNativePointerNode.execute(ptr, 0, iByteLength, srcEncoding, true);
                return PFactory.createString(PythonLanguage.get(inliningTarget), switchEncodingNode.execute(ts, TS_ENCODING));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }

        @Specialization(guards = "!ptrLib.isPointer(ptr)")
        static Object doManaged(Object ptr, long byteLength, int kind,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                byte[] ucsBytes = getByteArrayNode.execute(inliningTarget, ptr, byteLength);
                TruffleString ts = fromByteArrayNode.execute(ucsBytes, srcEncoding);
                return PFactory.createString(PythonLanguage.get(inliningTarget), switchEncodingNode.execute(ts, TS_ENCODING));
            } catch (InteropException e) {
                /*
                 * This means that we cannot read the array-like foreign object or the foreign
                 * elements cannot be interpreted as bytes. In any case, that's a fatal error.
                 */
                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_FSDecoder extends CApiUnaryBuiltinNode {

        @Specialization
        static Object fsDecoder(Object arg,
                        @Cached PyUnicodeFSDecoderNode fsDecoderNode) {
            return fsDecoderNode.execute(null, arg);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_FromUTF extends CApiTernaryBuiltinNode {

        private static Encoding encodingFromKind(Node inliningTarget, int kind, PRaiseNode raiseNode) throws PException {
            return switch (kind) {
                case 1 -> UTF_8;
                case 2 -> UTF_16LE;
                case 4 -> UTF_32LE;
                default -> throw raiseNode.raiseBadInternalCall(inliningTarget);
            };
        }

        @Specialization(guards = "ptrLib.isPointer(ptr)")
        static Object doNative(Object ptr, long byteLength, int kind,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached FromNativePointerNode fromNativePointerNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                int iByteLength = PInt.intValueExact(byteLength);
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                TruffleString ts = fromNativePointerNode.execute(ptr, 0, iByteLength, srcEncoding, true);
                return PFactory.createString(PythonLanguage.get(inliningTarget), switchEncodingNode.execute(ts, TS_ENCODING));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }

        @Specialization(guards = "!ptrLib.isPointer(ptr)")
        static Object doManaged(Object ptr, long byteLength, int kind,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("ptrLib") @CachedLibrary(limit = "1") InteropLibrary ptrLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncodingNode") @Cached SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                Encoding srcEncoding = encodingFromKind(inliningTarget, kind, raiseNode);
                byte[] ucsBytes = getByteArrayNode.execute(inliningTarget, ptr, byteLength);
                TruffleString ts = fromByteArrayNode.execute(ucsBytes, srcEncoding);
                return PFactory.createString(PythonLanguage.get(inliningTarget), switchEncodingNode.execute(ts, TS_ENCODING));
            } catch (InteropException e) {
                /*
                 * This means that we cannot read the array-like foreign object or the foreign
                 * elements cannot be interpreted as bytes. In any case, that's a fatal error.
                 */
                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_FromString extends CApiUnaryBuiltinNode {
        @Specialization
        static PString run(TruffleString str,
                        @Bind PythonLanguage language) {
            return PFactory.createString(language, str);
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
    abstract static class GraalPyPrivate_Unicode_DecodeUTF8Stateful extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doUtf8Decode(Object cByteArray, long size, TruffleString errors, int reportConsumed,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PRaiseNode raiseNode) {
            try {
                PBytes bytes = PFactory.createBytes(language, getByteArrayNode.execute(inliningTarget, cByteArray, size));
                return decode.call(null, bytes, T_UTF8, errors, reportConsumed == 0);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_DecodeUTF16Stateful extends CApi5BuiltinNode {

        @Specialization
        static Object decode(Object cByteArray, long size, TruffleString errors, int byteorder, int reportConsumed,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PRaiseNode raiseNode) {
            try {
                PBytes bytes = PFactory.createBytes(language, getByteArrayNode.execute(inliningTarget, cByteArray, size));
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
                throw raiseNode.raise(inliningTarget, PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_DecodeUTF32Stateful extends CApi5BuiltinNode {

        @Specialization
        static Object decode(Object cByteArray, long size, TruffleString errors, int byteorder, int reportConsumed,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CodecsModuleBuiltins.CodecsDecodeNode decode,
                        @Cached PRaiseNode raiseNode) {
            try {
                PBytes bytes = PFactory.createBytes(language, getByteArrayNode.execute(inliningTarget, cByteArray, size));
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
                throw raiseNode.raise(inliningTarget, PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            } catch (InteropException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_Decode extends CApiTernaryBuiltinNode {

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
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            TruffleString utf8Str = switchEncodingNode.execute(castStr.execute(inliningTarget, s), TruffleString.Encoding.UTF_8);
            return PFactory.createBytes(PythonLanguage.get(inliningTarget), copyToByteArrayNode.execute(utf8Str, TruffleString.Encoding.UTF_8));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyUnicode_EncodeLocale extends CApiBinaryBuiltinNode {
        @Specialization
        static Object encode(Object s, Object errors,
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
                        @Cached ReadUnicodeArrayNode readArray,
                        @Cached TruffleString.FromIntArrayUTF32Node fromArray) {
            assert TS_ENCODING == Encoding.UTF_32 : "needs switch_encoding otherwise";
            return PFactory.createString(PythonLanguage.get(inliningTarget), fromArray.execute(readArray.execute(inliningTarget, arr, castToInt(size), CStructs.wchar_t.size())));
        }
    }

    abstract static class NativeEncoderNode extends CApiBinaryBuiltinNode {
        private final TruffleString.Encoding encoding;

        protected NativeEncoderNode(TruffleString.Encoding encoding) {
            this.encoding = encoding;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(Object s, @SuppressWarnings("unused") PNone errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Shared("copyNode") @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return doUnicode(s, T_STRICT, encodeNativeStringNode, copyToByteArrayNode);
        }

        @Specialization
        Object doUnicode(Object s, TruffleString errors,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Shared("copyNode") @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return PFactory.createBytes(PythonLanguage.get(this), copyToByteArrayNode.execute(encodeNativeStringNode.execute(encoding, s, errors), encoding));
        }

        @Fallback
        static Object doUnicode(@SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsLatin1String extends NativeEncoderNode {
        protected _PyUnicode_AsLatin1String() {
            super(TruffleString.Encoding.ISO_8859_1);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsASCIIString extends NativeEncoderNode {
        protected _PyUnicode_AsASCIIString() {
            super(TruffleString.Encoding.US_ASCII);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _PyUnicode_AsUTF8String extends NativeEncoderNode {

        protected _PyUnicode_AsUTF8String() {
            super(TruffleString.Encoding.UTF_8);
        }

        @NeverDefault
        public static _PyUnicode_AsUTF8String create() {
            return PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringNodeGen.create();
        }
    }

    @CApiBuiltin(ret = ConstCharPtr, args = {PyObject, PY_SSIZE_T_PTR}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_AsUTF8AndSize extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doUnicode(PString s, Object sizePtr,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached InlinedConditionProfile hasSizeProfile,
                        @Cached InlinedConditionProfile hasUtf8Profile,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached _PyUnicode_AsUTF8String asUTF8String,
                        @Cached HiddenAttr.ReadNode readAttrNode,
                        @Cached HiddenAttr.WriteNode writeAttrNode) {
            PBytes utf8bytes = s.getUtf8Bytes(inliningTarget, readAttrNode);
            if (hasUtf8Profile.profile(inliningTarget, utf8bytes == null)) {
                utf8bytes = (PBytes) asUTF8String.execute(s, T_STRICT);
                s.setUtf8Bytes(inliningTarget, writeAttrNode, utf8bytes);
            }
            if (hasSizeProfile.profile(inliningTarget, !lib.isNull(sizePtr))) {
                writeLongNode.write(sizePtr, utf8bytes.getSequenceStorage().length());
            }
            return PySequenceArrayWrapper.ensureNativeSequence(utf8bytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doError(Object s, Object sizePtr,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_FillUtf8 extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doNative(PythonAbstractNativeObject s,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.AllocateNode allocateNode,
                        @Cached CStructAccess.WriteTruffleStringNode writeTruffleStringNode) {
            TruffleString utf8Str = encodeNativeStringNode.execute(UTF_8, s, T_STRICT);
            int len = utf8Str.byteLength(UTF_8);
            Object mem = allocateNode.alloc(len + 1, true);
            writeTruffleStringNode.write(mem, utf8Str, UTF_8);
            writePointerNode.writeToObj(s, CFields.PyCompactUnicodeObject__utf8, mem);
            writeLongNode.writeToObject(s, CFields.PyCompactUnicodeObject__utf8_length, len);
            return 0;
        }
    }

    @CApiBuiltin(ret = PY_UNICODE_PTR, args = {PyObject, PY_SSIZE_T_PTR}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_AsUnicodeAndSize extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doUnicode(PString s, Object sizePtr,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached InlinedConditionProfile hasSizeProfile,
                        @Cached InlinedConditionProfile hasUnicodeProfile,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached HiddenAttr.ReadNode readAttrNode,
                        @Cached HiddenAttr.WriteNode writeAttrNode) {
            int wcharSize = CStructs.wchar_t.size();
            PBytes wcharBytes = s.getWCharBytes(inliningTarget, readAttrNode);
            if (hasUnicodeProfile.profile(inliningTarget, wcharBytes == null)) {
                wcharBytes = asWideCharNode.executeNativeOrder(inliningTarget, s, wcharSize);
                s.setWCharBytes(inliningTarget, writeAttrNode, wcharBytes);
            }
            if (hasSizeProfile.profile(inliningTarget, !lib.isNull(sizePtr))) {
                writeLongNode.write(sizePtr, wcharBytes.getSequenceStorage().length() / wcharSize);
            }
            return PySequenceArrayWrapper.ensureNativeSequence(wcharBytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doError(Object s, Object sizePtr,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_IsMaterialized extends CApiUnaryBuiltinNode {

        @Specialization
        static int pstring(PString s) {
            return s.isMaterialized() ? 1 : 0;
        }

        @Fallback
        static Object other(@SuppressWarnings("unused") Object s) {
            return 1;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_FillUnicode extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doNative(PythonAbstractNativeObject s,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode cast,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached CStructAccess.AllocateNode allocateNode,
                        @Cached CStructAccess.WriteTruffleStringNode writeTruffleStringNode) {
            TruffleString str = switchEncodingNode.execute(cast.castKnownString(inliningTarget, s), WCHAR_T_ENCODING);
            int len = str.byteLength(WCHAR_T_ENCODING);
            Object mem = allocateNode.alloc(len + WCHAR_T_SIZE, true);
            writeTruffleStringNode.write(mem, str, WCHAR_T_ENCODING);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Unicode_AsWideChar extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doUnicode(Object s, int elementSize,
                        @Bind Node inliningTarget,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached PRaiseNode raiseNode) {
            try {
                PBytes wchars = asWideCharNode.executeLittleEndian(inliningTarget, castStr.execute(inliningTarget, s), elementSize);
                if (wchars != null) {
                    return wchars;
                } else {
                    throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                throw raiseNode.raise(inliningTarget, PythonErrorType.LookupError, ErrorMessages.M, e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, VA_LIST_PTR}, call = CApiCallPath.Ignored)
    abstract static class GraalPyPrivate_Unicode_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(TruffleString format, Object vaList,
                        @Bind Node inliningTarget,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode) {
            return unicodeFromFormatNode.execute(inliningTarget, format, vaList);
        }
    }

    @CApiBuiltin(ret = _PY_ERROR_HANDLER, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class _Py_GetErrorHandler extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(TruffleString errors,
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            PBytes bytes;
            try {
                bytes = PFactory.createBytes(PythonLanguage.get(inliningTarget), getByteArrayNode.execute(inliningTarget, object, length));
            } catch (InteropException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
            return callNode.executeWithoutFrame(UnicodeDecodeError, encoding, bytes, start, end, reason);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, call = Ignored)
    abstract static class GraalPyPrivate_PyUnicode_Find extends CApi5BuiltinNode {
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
