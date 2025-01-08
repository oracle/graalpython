/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.checkThrowableBeforeNative;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.CallSlotBinaryOpNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.CallSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallManagedSlotGetAttrNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.CallSlotNbBoolNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.CallManagedSlotSetAttrNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastUnsignedToJavaLongHashNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.nfi.api.SignatureLibrary;

@ExportLibrary(InteropLibrary.class)
public abstract class PyProcsWrapper extends PythonStructNativeWrapper {

    protected final CApiTiming timing;

    public PyProcsWrapper(Object delegate) {
        super(delegate, false);
        this.timing = CApiTiming.create(false, delegate);
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    protected Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw CompilerDirectives.shouldNotReachHere("abstract class");
    }

    @ExportMessage
    protected boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    protected long asPointer() {
        return getNativePointer();
    }

    protected abstract String getSignature();

    @ExportMessage
    @TruffleBoundary
    protected void toNative(
                    @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
        if (!isPointer()) {
            CApiContext cApiContext = PythonContext.get(null).getCApiContext();
            setNativePointer(cApiContext.registerClosure(getSignature(), this, getDelegate(), signatureLibrary));
        }
    }

    public abstract static class TpSlotWrapper extends PyProcsWrapper {
        public TpSlotWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        public final TpSlotManaged getSlot() {
            return (TpSlotManaged) getDelegate();
        }

        public abstract TpSlotWrapper cloneWith(TpSlotManaged slot);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GetAttrWrapper extends TpSlotWrapper {
        public GetAttrWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallManagedSlotGetAttrNode callGetAttr,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length < 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, -1, arguments.length);
                }
                try {
                    return toNativeNode.execute(callGetAttr.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1])));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "GetAttrWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new GetAttrWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class BinaryFuncWrapper extends PyProcsWrapper {

        public BinaryFuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    return toNativeNode.execute(executeNode.executeObject(null, getDelegate(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1])));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinaryFuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class BinarySlotFuncWrapper extends TpSlotWrapper {

        public BinarySlotFuncWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallSlotBinaryFuncNode callSlotNode,
                        @Cached NativeToPythonNode selfToJavaNode,
                        @Cached NativeToPythonNode argTtoJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    return toNativeNode.execute(callSlotNode.execute(null, inliningTarget, getSlot(), selfToJavaNode.execute(arguments[0]), argTtoJavaNode.execute(arguments[1])));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinaryFuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new BinarySlotFuncWrapper(slot);
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class BinaryOpSlotFuncWrapper extends TpSlotWrapper {
        private final BinaryOpSlot binaryOp;

        public BinaryOpSlotFuncWrapper(TpSlotManaged delegate, BinaryOpSlot binaryOp) {
            super(delegate);
            this.binaryOp = binaryOp;
        }

        public static BinaryOpSlotFuncWrapper createAdd(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_ADD);
        }

        public static BinaryOpSlotFuncWrapper createSubtract(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_SUBTRACT);
        }

        public static BinaryOpSlotFuncWrapper createMultiply(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_MULTIPLY);
        }

        public static BinaryOpSlotFuncWrapper createRemainder(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_REMAINDER);
        }

        public static BinaryOpSlotFuncWrapper createLShift(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_LSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createRShift(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_RSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createAnd(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_AND);
        }

        public static BinaryOpSlotFuncWrapper createXor(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_XOR);
        }

        public static BinaryOpSlotFuncWrapper createOr(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_OR);
        }

        public static BinaryOpSlotFuncWrapper createFloorDivide(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_FLOOR_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createTrueDivide(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_TRUE_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createDivMod(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_DIVMOD);
        }

        public static BinaryOpSlotFuncWrapper createMatrixMultiply(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, BinaryOpSlot.NB_MATRIX_MULTIPLY);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallSlotBinaryOpNode callSlotNode,
                        @Cached NativeToPythonNode selfToJavaNode,
                        @Cached NativeToPythonNode argTtoJavaNode,
                        @Cached GetClassNode getSelfClassNode,
                        @Cached GetClassNode getOtherClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetCachedTpSlotsNode getOtherSlots,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    Object self = selfToJavaNode.execute(arguments[0]);
                    Object other = argTtoJavaNode.execute(arguments[1]);
                    Object otherType = getOtherClassNode.execute(inliningTarget, other);
                    Object selfType = getSelfClassNode.execute(inliningTarget, self);
                    TpSlot otherSlot = binaryOp.getSlotValue(getOtherSlots.execute(inliningTarget, otherType));
                    boolean sameTypes = isSameTypeNode.execute(inliningTarget, selfType, otherType);
                    return toNativeNode.execute(callSlotNode.execute(null, inliningTarget, getSlot(), self, selfType, other, otherSlot, otherType, sameTypes, binaryOp));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinaryFuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, binaryOp);
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class UnaryFuncWrapper extends PyProcsWrapper {

        public UnaryFuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallUnaryMethodNode executeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                /*
                 * Accept a second argumenthere, since these functions are sometimes called using
                 * METH_O with a "NULL" value.
                 */
                if (arguments.length > 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 2, arguments.length);
                }
                try {
                    return toNativeNode.execute(executeNode.executeObject(null, getDelegate(), toJavaNode.execute(arguments[0])));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "UnaryFuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class InquiryWrapper extends TpSlotWrapper {
        public InquiryWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotNbBoolNode callSlotNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length < 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, -1, arguments.length);
                }
                try {
                    return callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InquiryWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER):SINT32";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new InquiryWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class ObjobjargWrapper extends PyProcsWrapper {

        public ObjobjargWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallTernaryMethodNode callTernaryMethodNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached InlinedConditionProfile arityProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arityProfile.profile(inliningTarget, arguments.length != 3)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }
                try {
                    callTernaryMethodNode.execute(null, getDelegate(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]), toJavaNode.execute(arguments[2]));
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "ObjobjargWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):SINT32";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class SetattrWrapper extends TpSlotWrapper {
        public SetattrWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallManagedSlotSetAttrNode callSlotNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached InlinedConditionProfile arityProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arityProfile.profile(inliningTarget, arguments.length < 3)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, -1, arguments.length);
                }
                try {
                    callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]), toJavaNode.execute(arguments[2]));
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SetattrWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):SINT32";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SetattrWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class DescrSetFunctionWrapper extends TpSlotWrapper {
        public DescrSetFunctionWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotDescrSet callSetNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length < 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, -1, arguments.length);
                }
                try {
                    callSetNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]), toJavaNode.execute(arguments[2]));
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SetAttrWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):SINT32";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new DescrSetFunctionWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class InitWrapper extends PyProcsWrapper {

        public InitWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static int init(InitWrapper self, Object[] arguments,
                            @Bind("this") Node inliningTarget,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached ExpandKeywordStarargsNode expandKwargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached NativeToPythonNode toJavaNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                CApiTiming.enter();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);
                        Object kwArgs = toJavaNode.execute(arguments[2]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);
                        callNode.execute(null, self.getDelegate(), pArgs, kwArgsArray);
                        return 0;
                    } catch (Throwable t) {
                        throw checkThrowableBeforeNative(t, "InitWrapper", self.getDelegate());
                    }
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(inliningTarget, e);
                    return -1;
                } finally {
                    CApiTiming.exit(self.timing);
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static int error(@SuppressWarnings("unused") InitWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):SINT32";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class TernaryFunctionWrapper extends PyProcsWrapper {

        public TernaryFunctionWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static Object call(TernaryFunctionWrapper self, Object[] arguments,
                            @Bind("this") Node inliningTarget,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached ExpandKeywordStarargsNode expandKwargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached NativeToPythonNode toJavaNode,
                            @Cached PythonToNativeNewRefNode toNativeNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                CApiTiming.enter();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);
                        Object kwArgs = toJavaNode.execute(arguments[2]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);
                        Object result = callNode.execute(null, self.getDelegate(), pArgs, kwArgsArray);
                        return toNativeNode.execute(result);
                    } catch (Throwable t) {
                        throw checkThrowableBeforeNative(t, "TernaryFunctionWrapper", self.getDelegate());
                    }
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(inliningTarget, e);
                    return PythonContext.get(gil).getNativeNull();
                } finally {
                    CApiTiming.exit(self.timing);
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static Object error(@SuppressWarnings("unused") TernaryFunctionWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RichcmpFunctionWrapper extends PyProcsWrapper {

        public RichcmpFunctionWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached CallTernaryMethodNode callNode,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }
                try {
                    // convert args
                    Object arg0 = toJavaNode.execute(arguments[0]);
                    Object arg1 = toJavaNode.execute(arguments[1]);
                    Object arg2 = arguments[2];

                    Object result = callNode.execute(null, getDelegate(), arg0, arg1, arg2);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "RichcmpFunctionWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,SINT32):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class SsizeargfuncWrapper extends PyProcsWrapper {

        public SsizeargfuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                assert arguments[1] instanceof Number;
                try {
                    Object result = executeNode.executeObject(null, getDelegate(), toJavaNode.execute(arguments[0]), arguments[1]);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeargfuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(toJavaNode).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,SINT64):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class SsizeargfuncSlotWrapper extends TpSlotWrapper {

        public SsizeargfuncSlotWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SsizeargfuncSlotWrapper(slot);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallSlotSizeArgFun callSlotNode,
                        @Cached SsizeAsIntNode asIntNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                assert arguments[1] instanceof Number;
                try {
                    Object result = callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), asIntNode.execute(inliningTarget, arguments[1]));
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeargfuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(toJavaNode).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,SINT64):POINTER";
        }
    }

    /**
     * For the time being when indices/lengths in GraalPy are 32bit integers, we must deal with
     * possible situation that someone passes larger number to us. In long term, we should migrate
     * indices/length to use longs.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class SsizeAsIntNode extends Node {
        public abstract int execute(Node inliningTarget, Object value);

        @Specialization
        static int doI(int i) {
            return i;
        }

        @Specialization
        static int doL(Node inliningTarget, long l,
                        @Cached InlinedBranchProfile errorBranch) {
            if (PInt.isIntRange(l)) {
                return (int) l;
            }
            errorBranch.enter(inliningTarget);
            throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, l);
        }

        @Fallback
        @InliningCutoff
        static int doOthers(Object value) {
            throw CompilerDirectives.shouldNotReachHere("Unexpected value passed to upcall as Py_ssize_t");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class SsizeobjargfuncWrapper extends PyProcsWrapper {

        public SsizeobjargfuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallTernaryMethodNode executeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }
                assert arguments[1] instanceof Number;
                try {
                    executeNode.execute(null, getDelegate(), toJavaNode.execute(arguments[0]), arguments[1], toJavaNode.execute(arguments[2]));
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeobjargfuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,SINT64,POINTER):SINT32";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class LenfuncWrapper extends TpSlotWrapper {
        public LenfuncWrapper(TpSlotManaged managedSlot) {
            super(managedSlot);
        }

        @ExportMessage
        long execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotLenNode callSlotNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length < 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, -1, arguments.length);
                }
                try {
                    return callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "LenfuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER):SINT64";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new LenfuncWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HashfuncWrapper extends PyProcsWrapper {

        public HashfuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        long execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallUnaryMethodNode executeNode,
                        @Cached CastUnsignedToJavaLongHashNode cast,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                /*
                 * Accept a second argumenthere, since these functions are sometimes called using
                 * METH_O with a "NULL" value.
                 */
                if (arguments.length > 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 2, arguments.length);
                }
                try {
                    Object result = executeNode.executeObject(null, getDelegate(), toJavaNode.execute(arguments[0]));
                    return PyObjectHashNode.avoidNegative1(cast.execute(inliningTarget, result));
                } catch (CannotCastException e) {
                    throw raiseNode.raise(TypeError, ErrorMessages.HASH_SHOULD_RETURN_INTEGER);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "HashfuncWrapper", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return -1;
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER):SINT64";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class DescrGetFunctionWrapper extends TpSlotWrapper {
        public DescrGetFunctionWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static Object call(DescrGetFunctionWrapper self, Object[] arguments,
                            @Bind("this") Node inliningTarget,
                            @Cached CallSlotDescrGet callGetNode,
                            @Cached NativeToPythonNode toJavaNode,
                            @Cached PythonToNativeNewRefNode toNativeNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                CApiTiming.enter();
                try {
                    try {
                        if (arguments.length < 3) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw ArityException.create(3, -1, arguments.length);
                        }

                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object obj = toJavaNode.execute(arguments[1]);
                        Object cls = toJavaNode.execute(arguments[2]);

                        Object result = callGetNode.execute(null, inliningTarget, self.getSlot(), receiver, obj, cls);
                        return toNativeNode.execute(result);
                    } catch (Throwable t) {
                        throw checkThrowableBeforeNative(t, "DescrGetFunctionWrapper", self.getDelegate());
                    }
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(inliningTarget, e);
                    return PythonContext.get(gil).getNativeNull();
                } finally {
                    CApiTiming.exit(self.timing);
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static Object error(@SuppressWarnings("unused") DescrGetFunctionWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new DescrGetFunctionWrapper(slot);
        }
    }
}
