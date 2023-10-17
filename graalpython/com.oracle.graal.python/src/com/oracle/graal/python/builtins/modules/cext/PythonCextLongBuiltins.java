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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins.NegNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

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
                        @Cached BranchProfile zeroProfile,
                        @Cached BranchProfile negProfile) {
            if (n.isNegative()) {
                negProfile.enter();
                return -1;
            } else if (n.isZero()) {
                zeroProfile.enter();
                return 0;
            } else {
                return 1;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!canBeInteger(obj)", "isPIntSubtype(inliningTarget, obj, getClassNode, isSubtypeNode)"})
        static Object signNative(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            // function returns int, but -1 is expected result for 'n < 0'
            throw CompilerDirectives.shouldNotReachHere("not yet implemented");
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)", "!isPIntSubtype(inliningTarget, obj,getClassNode,isSubtypeNode)"})
        static Object sign(@SuppressWarnings("unused") Object obj,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            // assert(PyLong_Check(v));
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected boolean isPIntSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PInt);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Double}, call = Direct)
    abstract static class PyLong_FromDouble extends CApiUnaryBuiltinNode {

        @Specialization
        static Object fromDouble(double d,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, d);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObjectAsTruffleString, Int, Int}, call = Ignored)
    @TypeSystemReference(PythonTypes.class)
    abstract static class PyTruffleLong_FromString extends CApiTernaryBuiltinNode {

        @Specialization(guards = "negative == 0")
        Object fromString(Object s, int base, @SuppressWarnings("unused") int negative,
                        @Cached BuiltinConstructors.IntNode intNode) {
            return intNode.executeWith(null, s, base);
        }

        @Specialization(guards = "negative != 0")
        Object fromString(Object s, int base, @SuppressWarnings("unused") int negative,
                        @Cached BuiltinConstructors.IntNode intNode,
                        @Cached NegNode negNode) {
            return negNode.execute(null, intNode.executeWith(null, s, base));
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Long, args = {PyObject, Int, ArgDescriptor.Long}, call = Ignored)
    abstract static class PyTruffleLong_AsPrimitive extends CApiTernaryBuiltinNode {

        @Specialization
        Object doGeneric(Object object, int mode, long targetTypeSize,
                        @Bind("this") Node inliningTarget,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Cached CastToNativeLongNode castToNativeLongNode) {
            try {
                /*
                 * The 'mode' parameter is usually a constant since this function is primarily used
                 * in 'PyLong_As*' API functions that pass a fixed mode. So, there is not need to
                 * profile the value and even if it is not constant, it is profiled implicitly.
                 */
                if (requiredPInt(mode) && !isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                // the 'ConvertPIntToPrimitiveNode' uses 'AsNativePrimitive' which does coercion
                Object coerced = convertPIntToPrimitiveNode.execute(object, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                return castToNativeLongNode.execute(coerced);
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

    @CApiBuiltin(name = "PyLong_FromSsize_t", ret = PyObjectTransfer, args = {Py_ssize_t}, call = Direct)
    @CApiBuiltin(name = "PyLong_FromLong", ret = PyObjectTransfer, args = {ArgDescriptor.Long}, call = Direct)
    @CApiBuiltin(ret = PyObjectTransfer, args = {LONG_LONG}, call = Direct)
    abstract static class PyLong_FromLongLong extends CApiUnaryBuiltinNode {

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
                        @Cached PythonObjectFactory factory) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return factory.createNativeVoidPtr(pointer, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return factory.createNativeVoidPtr(pointer);
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
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(convertToBigInteger(n));
        }

        @Specialization(guards = "!isInteger(pointer)", limit = "2")
        static Object doPointer(Object pointer,
                        @CachedLibrary("pointer") InteropLibrary lib,
                        @Shared @Cached PythonObjectFactory factory) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return factory.createNativeVoidPtr(pointer, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return factory.createNativeVoidPtr(pointer);
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
                        @Cached BranchProfile overflowProfile) {
            try {
                return n.longValueExact();
            } catch (OverflowException e) {
                overflowProfile.enter();
                try {
                    throw raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                } catch (PException pe) {
                    ensureTransformExcNode().execute(pe);
                    return 0;
                }
            }
        }

        @Specialization
        static Object doPointer(PythonNativeVoidPtr n) {
            return n.getPointerObject();
        }

        @Fallback
        long doGeneric(Object n) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            try {
                try {
                    return asPrimitiveNode.executeLong(n, 0, Long.BYTES);
                } catch (UnexpectedResultException e) {
                    throw raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                }
            } catch (PException e) {
                ensureTransformExcNode().execute(e);
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

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffleLong_One extends CApiNullaryBuiltinNode {
        @Specialization
        static int run() {
            return 1;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffleLong_Zero extends CApiNullaryBuiltinNode {
        @Specialization
        static int run() {
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyLongObject, UNSIGNED_CHAR_PTR, SIZE_T, Int, Int}, call = Direct)
    abstract static class _PyLong_AsByteArray extends CApi5BuiltinNode {
        private static void checkSign(Node inliningTarget, boolean negative, int isSigned, PRaiseNode.Lazy raiseNode) {
            if (negative) {
                if (isSigned == 0) {
                    throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
                }
            }
        }

        @Specialization
        static Object get(int value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            checkSign(inliningTarget, value < 0, isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromLong(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }

        @Specialization
        static Object get(long value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            checkSign(inliningTarget, value < 0, isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromLong(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }

        @Specialization
        static Object get(PInt value, Object bytes, long n, int littleEndian, int isSigned,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profile,
                        @Shared @Cached CStructAccess.WriteByteNode write,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            checkSign(inliningTarget, value.isNegative(), isSigned, raiseNode);
            byte[] array = IntBuiltins.ToBytesNode.fromBigInteger(value, PythonUtils.toIntError(n), littleEndian == 0, isSigned != 0, inliningTarget, profile, raiseNode);
            write.writeByteArray(bytes, array);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObjectAsTruffleString, Int}, call = Direct)
    abstract static class PyLong_FromUnicodeObject extends CApiBinaryBuiltinNode {
        @Specialization
        static Object convert(TruffleString s, int base,
                        @Cached IntNode intNode) {
            return intNode.executeWith(null, s, base);
        }
    }
}
