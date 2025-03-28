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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.checkThrowableBeforeNative;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.CallSlotBinaryOpNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.CallSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallManagedSlotGetAttrNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.CallSlotHashFunNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.CallSlotNbBoolNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.CallSlotMpAssSubscriptNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotNbPower.CallSlotNbInPlacePowerNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotNbPower.CallSlotNbPowerNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.CallSlotRichCmpNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.CallManagedSlotSetAttrNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.CallSlotSqAssItemNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.CallSlotSqContainsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpInitNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
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
        private final ReversibleSlot binaryOp;

        public BinaryOpSlotFuncWrapper(TpSlotManaged delegate, ReversibleSlot binaryOp) {
            super(delegate);
            this.binaryOp = binaryOp;
        }

        public static BinaryOpSlotFuncWrapper createAdd(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_ADD);
        }

        public static BinaryOpSlotFuncWrapper createSubtract(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_SUBTRACT);
        }

        public static BinaryOpSlotFuncWrapper createMultiply(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_MULTIPLY);
        }

        public static BinaryOpSlotFuncWrapper createRemainder(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_REMAINDER);
        }

        public static BinaryOpSlotFuncWrapper createLShift(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_LSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createRShift(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_RSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createAnd(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_AND);
        }

        public static BinaryOpSlotFuncWrapper createXor(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_XOR);
        }

        public static BinaryOpSlotFuncWrapper createOr(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_OR);
        }

        public static BinaryOpSlotFuncWrapper createFloorDivide(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_FLOOR_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createTrueDivide(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_TRUE_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createDivMod(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_DIVMOD);
        }

        public static BinaryOpSlotFuncWrapper createMatrixMultiply(TpSlotManaged delegate) {
            return new BinaryOpSlotFuncWrapper(delegate, ReversibleSlot.NB_MATRIX_MULTIPLY);
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
    public static final class UnaryFuncLegacyWrapper extends PyProcsWrapper {

        public UnaryFuncLegacyWrapper(Object delegate) {
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
    public static final class UnaryFuncWrapper extends TpSlotWrapper {

        public UnaryFuncWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new UnaryFuncWrapper(slot);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallSlotUnaryNode callNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    Object result = callNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]));
                    return toNativeNode.execute(result);
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
    public static final class IterNextWrapper extends TpSlotWrapper {

        public IterNextWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new IterNextWrapper(slot);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallSlotTpIterNextNode callNextNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    Object result;
                    try {
                        result = callNextNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]));
                    } catch (IteratorExhausted e) {
                        return PythonContext.get(inliningTarget).getNativeNull();
                    }
                    return toNativeNode.execute(result);
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
    public static final class SqContainsWrapper extends TpSlotWrapper {
        public SqContainsWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotSqContainsNode callSlotNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    return callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SqContainsWrapper", getDelegate());
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
            return "(POINTER,POINTER):SINT32";
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SqContainsWrapper(slot);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class ObjobjargWrapper extends TpSlotWrapper {

        public ObjobjargWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new ObjobjargWrapper(slot);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotMpAssSubscriptNode callNode,
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
                    callNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]), toJavaNode.execute(arguments[2]));
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
    public static final class InitWrapper extends TpSlotWrapper {

        public InitWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new InitWrapper(slot);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached CallSlotTpInitNode callSlot,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    // convert args
                    Object receiver = toJavaNode.execute(arguments[0]);
                    Object starArgs = toJavaNode.execute(arguments[1]);
                    Object kwArgs = toJavaNode.execute(arguments[2]);

                    Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                    PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);
                    callSlot.execute(null, inliningTarget, getSlot(), receiver, starArgsArray, kwArgsArray);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InitWrapper", getDelegate());
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
    static final class NewWrapper extends TpSlotWrapper {

        public NewWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NewWrapper(slot);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind Node inliningTarget,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached PythonToNativeNewRefNode toSulongNode,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                try {
                    // convert args
                    Object receiver = toJavaNode.execute(arguments[0]);
                    Object starArgs = toJavaNode.execute(arguments[1]);
                    Object kwArgs = toJavaNode.execute(arguments[2]);

                    Object[] pArgs;
                    if (starArgs != PNone.NO_VALUE) {
                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                    } else {
                        pArgs = new Object[]{receiver};
                    }
                    PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);

                    // execute
                    return toSulongNode.execute(callNode.execute(null, getDelegate(), pArgs, kwArgsArray));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "MethKeywords", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(callNode).getNativeNull();
            } finally {
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class CallFunctionWrapper extends PyProcsWrapper {

        public CallFunctionWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static Object call(CallFunctionWrapper self, Object[] arguments,
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
                        throw checkThrowableBeforeNative(t, "CallFunctionWrapper", self.getDelegate());
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
            static Object error(@SuppressWarnings("unused") CallFunctionWrapper self, Object[] arguments) throws ArityException {
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
    public static final class NbPowerWrapper extends TpSlotWrapper {

        public NbPowerWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NbPowerWrapper(slot);
        }

        @ExportMessage
        static Object execute(NbPowerWrapper self, Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetClassNode vGetClassNode,
                        @Cached GetClassNode wGetClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetCachedTpSlotsNode wGetSlots,
                        @Cached CallSlotNbPowerNode callSlot,
                        @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    // convert args
                    Object v = toJavaNode.execute(arguments[0]);
                    Object w = toJavaNode.execute(arguments[1]);
                    Object z = toJavaNode.execute(arguments[2]);
                    Object vType = vGetClassNode.execute(inliningTarget, v);
                    Object wType = wGetClassNode.execute(inliningTarget, w);
                    TpSlots wSlots = wGetSlots.execute(inliningTarget, wType);
                    boolean sameTypes = isSameTypeNode.execute(inliningTarget, vType, wType);
                    Object result = callSlot.execute(null, inliningTarget, self.getSlot(), v, vType, w, wSlots.nb_power(), wType, z, sameTypes);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbPowerWrapper", self.getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(self.timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class NbInPlacePowerWrapper extends TpSlotWrapper {

        public NbInPlacePowerWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NbInPlacePowerWrapper(slot);
        }

        @ExportMessage
        static Object execute(NbInPlacePowerWrapper self, Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached CallSlotNbInPlacePowerNode callSlot,
                        @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                try {
                    // convert args
                    Object v = toJavaNode.execute(arguments[0]);
                    Object w = toJavaNode.execute(arguments[1]);
                    Object z = toJavaNode.execute(arguments[2]);
                    Object result = callSlot.execute(null, inliningTarget, self.getSlot(), v, w, z);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbInPlacePowerWrapper", self.getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(self.timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RichcmpFunctionWrapper extends TpSlotWrapper {

        public RichcmpFunctionWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new RichcmpFunctionWrapper(slot);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached CallSlotRichCmpNode callNode,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @CachedLibrary(limit = "1") InteropLibrary opInterop,
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
                    RichCmpOp op = RichCmpOp.fromNative(opInterop.asInt(arguments[2]));
                    Object result = callNode.execute(null, inliningTarget, getSlot(), arg0, arg1, op);
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
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, l);
        }

        @Fallback
        @InliningCutoff
        static int doOthers(Object value) {
            throw CompilerDirectives.shouldNotReachHere("Unexpected value passed to upcall as Py_ssize_t");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class SsizeobjargprocWrapper extends TpSlotWrapper {

        public SsizeobjargprocWrapper(TpSlotManaged slot) {
            super(slot);
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SsizeobjargprocWrapper(slot);
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached CallSlotSqAssItemNode executeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached SsizeAsIntNode asIntNode,
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
                    Object self = toJavaNode.execute(arguments[0]);
                    int key = asIntNode.execute(inliningTarget, arguments[1]);
                    Object value = toJavaNode.execute(arguments[2]);
                    executeNode.execute(null, inliningTarget, getSlot(), self, key, value);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeobjargprocWrapper", getDelegate());
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
    public static final class HashfuncWrapper extends TpSlotWrapper {

        public HashfuncWrapper(TpSlotManaged delegate) {
            super(delegate);
        }

        @ExportMessage
        long execute(Object[] arguments,
                        @Bind Node inliningTarget,
                        @Cached CallSlotHashFunNode callSlotNode,
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
                    return callSlotNode.execute(null, inliningTarget, getSlot(), toJavaNode.execute(arguments[0]));
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

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new HashfuncWrapper(slot);
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
