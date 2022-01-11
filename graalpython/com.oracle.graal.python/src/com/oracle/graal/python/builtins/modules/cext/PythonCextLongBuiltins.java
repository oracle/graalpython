/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CastToNativeLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ResolveHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins.IntNode;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins.NegNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import java.math.BigInteger;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextLongBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextLongBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "_PyLong_Sign", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongSignNode extends PythonUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        int sign(int n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        int signNeg(int n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        int signPos(int n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n == 0")
        int sign(long n) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n < 0")
        int signNeg(long n) {
            return -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "n > 0")
        int signPos(long n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "b")
        int signTrue(boolean b) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!b")
        int signFalse(boolean b) {
            return 0;
        }

        @Specialization
        int sign(PInt n,
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
        @Specialization(guards = {"!canBeInteger(obj)", "isPIntSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object signNative(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            // function returns int, but -1 is expected result for 'n < 0'
            throw CompilerDirectives.shouldNotReachHere("not yet implemented");
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)", "!isPIntSubtype(frame, obj,getClassNode,isSubtypeNode)"})
        public Object sign(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            // assert(PyLong_Check(v));
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected boolean isPIntSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PInt);
        }
    }

    @Builtin(name = "PyLong_FromDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongFromDoubleNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object fromDouble(VirtualFrame frame, double d,
                        @Cached IntNode intNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.execute(frame, d);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyLong_FromString", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyLongFromStringNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "negative == 0")
        Object fromString(VirtualFrame frame, String s, long base, @SuppressWarnings("unused") long negative,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Shared("transforEx") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.executeWith(frame, s, base);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "negative != 0")
        Object fromString(VirtualFrame frame, String s, long base, @SuppressWarnings("unused") long negative,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Cached NegNode negNode,
                        @Shared("transforEx") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return negNode.execute(frame, intNode.executeWith(frame, s, base));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyLong_AsPrimitive", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyLongAsPrimitive extends PythonTernaryBuiltinNode {
        @Child private ResolveHandleNode resolveHandleNode;
        @Child private ToJavaNode toJavaNode;
        @CompilationFinal private ValueProfile pointerClassProfile;
        @Child private ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @Child private CastToNativeLongNode castToNativeLongNode;
        @Child private GetClassNode getClassNode;
        @Child private IsSubtypeNode isSubtypeNode;

        public abstract Object executeWith(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        public abstract long executeLong(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        public abstract int executeInt(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        @Specialization(rewriteOn = {UnexpectedWrapperException.class, UnexpectedResultException.class})
        long doPrimitiveNativeWrapperToLong(VirtualFrame frame, Object object, int mode, long targetTypeSize) throws UnexpectedWrapperException, UnexpectedResultException {
            Object resolvedPointer = ensureResolveHandleNode().execute(object);
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    PrimitiveNativeWrapper wrapper = (PrimitiveNativeWrapper) resolvedPointer;
                    if (requiredPInt(mode) && !wrapper.isSubtypeOfInt()) {
                        throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                    }
                    return ensureConvertPIntToPrimitiveNode().executeLong(wrapper, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnexpectedWrapperException.INSTANCE;
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException e) {
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return -1;
            }
        }

        @Specialization(replaces = "doPrimitiveNativeWrapperToLong")
        Object doGeneric(VirtualFrame frame, Object objectPtr, int mode, long targetTypeSize) {
            Object resolvedPointer = ensurePointerClassProfile().profile(ensureResolveHandleNode().execute(objectPtr));
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    PrimitiveNativeWrapper wrapper = (PrimitiveNativeWrapper) resolvedPointer;
                    if (requiredPInt(mode) && !wrapper.isSubtypeOfInt()) {
                        throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                    }
                    return ensureConvertPIntToPrimitiveNode().execute(wrapper, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                }
                /*
                 * The 'mode' parameter is usually a constant since this function is primarily used
                 * in 'PyLong_As*' API functions that pass a fixed mode. So, there is not need to
                 * profile the value and even if it is not constant, it is profiled implicitly.
                 */
                Object object = ensureToJavaNode().execute(resolvedPointer);
                if (requiredPInt(mode) && !ensureIsSubtypeNode().execute(getPythonClass(object), PythonBuiltinClassType.PInt)) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                // the 'ConvertPIntToPrimitiveNode' uses 'AsNativePrimitive' which does coercion
                Object coerced = ensureConvertPIntToPrimitiveNode().execute(object, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                return ensureCastToNativeLongNode().execute(coerced);
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException e) {
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return -1;
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

        private ResolveHandleNode ensureResolveHandleNode() {
            if (resolveHandleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveHandleNode = insert(ResolveHandleNodeGen.create());
            }
            return resolveHandleNode;
        }

        private ToJavaNode ensureToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNodeGen.create());
            }
            return toJavaNode;
        }

        private ValueProfile ensurePointerClassProfile() {
            if (pointerClassProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pointerClassProfile = ValueProfile.createClassProfile();
            }
            return pointerClassProfile;
        }

        private ConvertPIntToPrimitiveNode ensureConvertPIntToPrimitiveNode() {
            if (convertPIntToPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                convertPIntToPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            return convertPIntToPrimitiveNode;
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }

        private CastToNativeLongNode ensureCastToNativeLongNode() {
            if (castToNativeLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToNativeLongNode = insert(CastToNativeLongNodeGen.create());
            }
            return castToNativeLongNode;
        }

        private Object getPythonClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(object);
        }

        private IsSubtypeNode ensureIsSubtypeNode() {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode;
        }

        static final class UnexpectedWrapperException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            static final UnexpectedWrapperException INSTANCE = new UnexpectedWrapperException();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyLong_FromLongLong", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyLongFromLongLong extends PythonBinaryBuiltinNode {
        @Specialization(guards = "signed != 0")
        static Object doSignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed == 0")
        static Object doUnsignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            if (n < 0) {
                return toNewRefNode.executeLong(n & 0xFFFFFFFFL);
            }
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed != 0")
        static Object doSignedLong(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n >= 0"})
        static Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(factory().createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }
    }

    @Builtin(name = "PyLong_FromVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongFromVoidPtr extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        Object doPointer(Object pointer,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @CachedLibrary("pointer") InteropLibrary lib) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return toSulongNode.execute(factory().createNativeVoidPtr(pointer, lib.asPointer(pointer)));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return toSulongNode.execute(factory().createNativeVoidPtr(pointer));
        }
    }

    @Builtin(name = "PyLong_AsVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyLongAsVoidPtr extends PythonUnaryBuiltinNode {
        @Child private ConvertPIntToPrimitiveNode asPrimitiveNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        public abstract Object execute(Object o);

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
}
