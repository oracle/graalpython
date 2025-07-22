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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CONST_UNSIGNED_CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstPyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.lib.PyLongFromUnicodeObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public final class PythonCextLongBuiltins {

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class _PyLong_Sign extends CApiUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        static int sign(int n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        static int signNeg(int n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        static int signPos(int n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        static int sign(long n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        static int signNeg(long n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        static int signPos(long n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "b")
        static int signTrue(boolean b) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!b")
        static int signFalse(boolean b) {
            return 0;
        }

        @Specialization
        static int sign(PInt n,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile zeroProfile,
                        @Cached InlinedBranchProfile negProfile) {
            if (n.isNegative()) {
                negProfile.enter(inliningTarget);
                return -1;
            } else if (n.isZero()) {
                zeroProfile.enter(inliningTarget);
                return 0;
            } else {
                return 1;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!canBeInteger(obj)", "isPIntSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object signNative(Object obj,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached IsSubtypeNode isSubtypeNode) {
            // function returns int, but -1 is expected result for 'n < 0'
            throw CompilerDirectives.shouldNotReachHere("not yet implemented");
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)", "!isPIntSubtype(inliningTarget, obj,getClassNode,isSubtypeNode)"})
        static Object sign(@SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode) {
            // assert(PyLong_Check(v));
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected boolean isPIntSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PInt);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyLongObject}, call = Ignored)
    abstract static class GraalPyPrivate_Long_DigitCount extends CApiUnaryBuiltinNode {

        @Specialization
        static long getDC(Object n,
                        @Bind Node inliningTarget,
                        @Cached CExtNodes.LvTagNode lvTagNode) {
            return lvTagNode.getDigitCount(inliningTarget, n);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Double}, call = Direct)
    abstract static class PyLong_FromDouble extends CApiUnaryBuiltinNode {

        @Specialization
        static Object fromDouble(double d,
                        @Bind Node inliningTarget,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, d);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {CharPtrAsTruffleString, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Long_FromString extends CApiBinaryBuiltinNode {

        @Specialization
        Object fromString(Object s, int base,
                        @Bind Node inliningTarget,
                        @Cached PyLongFromUnicodeObject fromUnicodeObject) {
            return fromUnicodeObject.execute(inliningTarget, s, base);
        }
    }

    @CApiBuiltin(ret = Int, args = {ConstPyLongObject}, call = Direct)
    abstract static class PyUnstable_Long_IsCompact extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int doI(Object value) {
            if (value instanceof Integer || value instanceof Long) {
                return 1;
            } else if (value instanceof PInt pInt) {
                return pInt.fitsIn(PInt.MIN_LONG, PInt.MAX_LONG) ? 1 : 0;
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = LONG_LONG, args = {PyObject, Int, SIZE_T}, call = Ignored)
    abstract static class GraalPyPrivate_Long_AsPrimitive extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object object, int mode, long targetTypeSize,
                        @Bind Node inliningTarget,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Cached CastToNativeLongNode castToNativeLongNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                /*
                 * The 'mode' parameter is usually a constant since this function is primarily used
                 * in 'PyLong_As*' API functions that pass a fixed mode. So, there is not need to
                 * profile the value and even if it is not constant, it is profiled implicitly.
                 */
                if (requiredPInt(mode) && !isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                // the 'ConvertPIntToPrimitiveNode' uses 'AsNativePrimitive' which does coercion
                Object coerced = convertPIntToPrimitiveNode.execute(inliningTarget, object, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                return castToNativeLongNode.execute(inliningTarget, coerced);
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int signed(int mode) {
            return mode & 0x1;
        }

        private static boolean requiredPInt(int mode) {
            return (mode & 0x2) != 0;
        }

        private static boolean exact(int mode) {
            return (mode & 0x4) == 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {LONG_LONG}, call = Ignored)
    abstract static class GraalPyPrivate_Long_FromLongLong extends CApiUnaryBuiltinNode {

        @Specialization
        static int doSignedInt(int n) {
            return n;
        }

        @Specialization
        static long doSignedLong(long n) {
            return n;
        }

        @Specialization(guards = "!isInteger(pointer)", limit = "2")
        static Object doPointer(Object pointer,
                        @CachedLibrary("pointer") InteropLibrary lib,
                        @Bind PythonLanguage language) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return PFactory.createNativeVoidPtr(language, pointer, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return PFactory.createNativeVoidPtr(language, pointer);
        }
    }

    @CApiBuiltin(name = "PyLong_FromSize_t", ret = PyObjectTransfer, args = {SIZE_T}, call = Direct)
    @CApiBuiltin(name = "PyLong_FromUnsignedLong", ret = PyObjectTransfer, args = {UNSIGNED_LONG}, call = Direct)
    @CApiBuiltin(ret = PyObjectTransfer, args = {UNSIGNED_LONG_LONG}, call = Direct)
    abstract static class PyLong_FromUnsignedLongLong extends CApiUnaryBuiltinNode {

        @Specialization
        static long doUnsignedInt(int n) {
            if (n < 0) {
                return n & 0xFFFFFFFFL;
            }
            return n;
        }

        @Specialization(guards = "n >= 0")
        static Object doUnsignedLongPositive(long n) {
            return n;
        }

        @Specialization(guards = "n < 0")
        static Object doUnsignedLongNegative(long n,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, convertToBigInteger(n));
        }

        @Specialization(guards = "!isInteger(pointer)", limit = "2")
        static Object doPointer(Object pointer,
                        @CachedLibrary("pointer") InteropLibrary lib,
                        @Bind PythonLanguage language) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return PFactory.createNativeVoidPtr(language, pointer, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return PFactory.createNativeVoidPtr(language, pointer);
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Direct)
    public abstract static class PyLong_AsVoidPtr extends CApiUnaryBuiltinNode {
        @Child private ConvertPIntToPrimitiveNode asPrimitiveNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        @Specialization
        static long doPointer(int n) {
            return n;
        }

        @Specialization
        static long doPointer(long n) {
            return n;
        }

        @Specialization
        long doPointer(PInt n,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile overflowProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                return n.longValueExact();
            } catch (OverflowException e) {
                overflowProfile.enter(inliningTarget);
                try {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                } catch (PException pe) {
                    ensureTransformExcNode().executeCached(pe);
                    return 0;
                }
            }
        }

        @Specialization
        static Object doPointer(PythonNativeVoidPtr n) {
            return n.getPointerObject();
        }

        @Fallback
        long doGeneric(Object n,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            try {
                try {
                    return asPrimitiveNode.executeLongCached(n, 0, Long.BYTES);
                } catch (UnexpectedResultException e) {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                }
            } catch (PException e) {
                ensureTransformExcNode().executeCached(e);
                return 0;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExcNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyLongObject, UNSIGNED_CHAR_PTR, SIZE_T, Int, Int}, call = Direct)
    abstract static class _PyLong_AsByteArray extends CApi5BuiltinNode {
        private static void checkSign(Node inliningTarget, boolean negative, int isSigned, PRaiseNode raiseNode) {
            if (negative) {
                if (isSigned == 0) {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
                }
            }
        }

        @Specialization
        static Object get(int value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            checkSign(inliningTarget, value < 0, isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromLong(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }

        @Specialization
        static Object get(long value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            checkSign(inliningTarget, value < 0, isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromLong(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }

        @Specialization
        static Object get(PInt value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            checkSign(inliningTarget, value.isNegative(), isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromBigInteger(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int}, call = Direct)
    abstract static class PyLong_FromUnicodeObject extends CApiBinaryBuiltinNode {
        @Specialization
        static Object convert(Object s, int base,
                        @Bind Node inliningTarget,
                        @Cached PyLongFromUnicodeObject pyLongFromUnicodeObject) {
            return pyLongFromUnicodeObject.execute(inliningTarget, s, base);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {CONST_UNSIGNED_CHAR_PTR, SIZE_T, Int, Int}, call = Direct)
    abstract static class _PyLong_FromByteArray extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object convert(Object charPtr, long size, int littleEndian, int signed,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadByteNode readByteNode,
                        @Cached IntNodes.PyLongFromByteArray fromByteArray,
                        @Cached PRaiseNode raiseNode) {
            if (size != (int) size) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.BYTE_ARRAY_TOO_LONG_TO_CONVERT_TO_INT);
            }
            byte[] bytes = readByteNode.readByteArray(charPtr, (int) size);
            return fromByteArray.execute(inliningTarget, bytes, littleEndian != 0, signed != 0);
        }
    }

    @CApiBuiltin(ret = SIZE_T, args = {PyObject}, call = Direct)
    abstract static class _PyLong_NumBits extends CApiUnaryBuiltinNode {
        @Specialization
        static long numBits(Object obj,
                        @Cached IntBuiltins.BitLengthNode bitLengthNode) {
            return bitLengthNode.execute(obj);
        }
    }
}
