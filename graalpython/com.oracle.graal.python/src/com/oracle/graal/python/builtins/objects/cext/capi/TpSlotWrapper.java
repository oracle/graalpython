/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NfiType.RAW_POINTER;
import static com.oracle.graal.python.nfi2.NfiType.SINT32;
import static com.oracle.graal.python.nfi2.NfiType.SINT64;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetTpSlotsNode;
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
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpCallNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpInitNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpNewNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiUpcallSignature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class TpSlotWrapper {

    private static final NfiUpcallSignature SIGNATURE_P_P = Nfi.createUpcallSignature(RAW_POINTER, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_P_PP = Nfi.createUpcallSignature(RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_P_PPP = Nfi.createUpcallSignature(RAW_POINTER, RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_P_PPI = Nfi.createUpcallSignature(RAW_POINTER, RAW_POINTER, RAW_POINTER, SINT32);
    private static final NfiUpcallSignature SIGNATURE_P_PL = Nfi.createUpcallSignature(RAW_POINTER, RAW_POINTER, SINT64);
    private static final NfiUpcallSignature SIGNATURE_I_P = Nfi.createUpcallSignature(SINT32, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_I_PP = Nfi.createUpcallSignature(SINT32, RAW_POINTER, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_I_PPP = Nfi.createUpcallSignature(SINT32, RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_I_PLP = Nfi.createUpcallSignature(SINT32, RAW_POINTER, SINT64, RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_L_P = Nfi.createUpcallSignature(SINT64, RAW_POINTER);

    private static final MethodHandle HANDLE_GET_ATTR;
    private static final MethodHandle HANDLE_BINARY_SLOT_FUNC;
    private static final MethodHandle HANDLE_BINARY_OP_SLOT_FUNC;
    private static final MethodHandle HANDLE_UNARY_FUNC;
    private static final MethodHandle HANDLE_ITER_NEXT;
    private static final MethodHandle HANDLE_INQUIRY;
    private static final MethodHandle HANDLE_SQ_CONTAINS;
    private static final MethodHandle HANDLE_OBJ_OBJ_ARG;
    private static final MethodHandle HANDLE_SET_ATTR;
    private static final MethodHandle HANDLE_DESCR_SET_FUNCTION;
    private static final MethodHandle HANDLE_INIT;
    private static final MethodHandle HANDLE_NEW;
    private static final MethodHandle HANDLE_CALL;
    private static final MethodHandle HANDLE_NB_POWER;
    private static final MethodHandle HANDLE_NB_IN_PLACE_POWER;
    private static final MethodHandle HANDLE_RICHCMP_FUNCTION;
    private static final MethodHandle HANDLE_SSIZEARGFUNC_SLOT;
    private static final MethodHandle HANDLE_SSIZEOBJARGPROC;
    private static final MethodHandle HANDLE_LENFUNC;
    private static final MethodHandle HANDLE_HASHFUNC;
    private static final MethodHandle HANDLE_DESCR_GET_FUNCTION;

    static {
        try {
            HANDLE_GET_ATTR = MethodHandles.lookup().findStatic(GetAttrWrapper.class, "executeGetAttr", MethodType.methodType(long.class, GetAttrWrapper.class, long.class, long.class));
            HANDLE_BINARY_SLOT_FUNC = MethodHandles.lookup().findStatic(BinarySlotFuncWrapper.class, "executeBinarySlot",
                            MethodType.methodType(long.class, BinarySlotFuncWrapper.class, long.class, long.class));
            HANDLE_BINARY_OP_SLOT_FUNC = MethodHandles.lookup().findStatic(BinaryOpSlotFuncWrapper.class, "executeBinaryOpSlot",
                            MethodType.methodType(long.class, BinaryOpSlotFuncWrapper.class, long.class, long.class));
            HANDLE_UNARY_FUNC = MethodHandles.lookup().findStatic(UnaryFuncWrapper.class, "executeUnary", MethodType.methodType(long.class, UnaryFuncWrapper.class, long.class));
            HANDLE_ITER_NEXT = MethodHandles.lookup().findStatic(IterNextWrapper.class, "executeIterNext", MethodType.methodType(long.class, IterNextWrapper.class, long.class));
            HANDLE_INQUIRY = MethodHandles.lookup().findStatic(InquiryWrapper.class, "executeInquiry", MethodType.methodType(int.class, InquiryWrapper.class, long.class));
            HANDLE_SQ_CONTAINS = MethodHandles.lookup().findStatic(SqContainsWrapper.class, "executeSqContains", MethodType.methodType(int.class, SqContainsWrapper.class, long.class, long.class));
            HANDLE_OBJ_OBJ_ARG = MethodHandles.lookup().findStatic(ObjobjargWrapper.class, "executeObjobjarg",
                            MethodType.methodType(int.class, ObjobjargWrapper.class, long.class, long.class, long.class));
            HANDLE_SET_ATTR = MethodHandles.lookup().findStatic(SetAttrWrapper.class, "executeSetAttr",
                            MethodType.methodType(int.class, SetAttrWrapper.class, long.class, long.class, long.class));
            HANDLE_DESCR_SET_FUNCTION = MethodHandles.lookup().findStatic(DescrSetFunctionWrapper.class, "executeDescrSetFunction",
                            MethodType.methodType(int.class, DescrSetFunctionWrapper.class, long.class, long.class, long.class));
            HANDLE_INIT = MethodHandles.lookup().findStatic(InitWrapper.class, "executeInit", MethodType.methodType(int.class, InitWrapper.class, long.class, long.class, long.class));
            HANDLE_NEW = MethodHandles.lookup().findStatic(NewWrapper.class, "executeNew", MethodType.methodType(long.class, NewWrapper.class, long.class, long.class, long.class));
            HANDLE_CALL = MethodHandles.lookup().findStatic(CallWrapper.class, "executeCall", MethodType.methodType(long.class, CallWrapper.class, long.class, long.class, long.class));
            HANDLE_NB_POWER = MethodHandles.lookup().findStatic(NbPowerWrapper.class, "executeNbPower", MethodType.methodType(long.class, NbPowerWrapper.class, long.class, long.class, long.class));
            HANDLE_NB_IN_PLACE_POWER = MethodHandles.lookup().findStatic(NbInPlacePowerWrapper.class, "executeNbInPlacePower",
                            MethodType.methodType(long.class, NbInPlacePowerWrapper.class, long.class, long.class, long.class));
            HANDLE_RICHCMP_FUNCTION = MethodHandles.lookup().findStatic(RichcmpFunctionWrapper.class, "executeRichcmpFunction",
                            MethodType.methodType(long.class, RichcmpFunctionWrapper.class, long.class, long.class, int.class));
            HANDLE_SSIZEARGFUNC_SLOT = MethodHandles.lookup().findStatic(SsizeargfuncSlotWrapper.class, "executeSsizeargfuncSlot",
                            MethodType.methodType(long.class, SsizeargfuncSlotWrapper.class, long.class, long.class));
            HANDLE_SSIZEOBJARGPROC = MethodHandles.lookup().findStatic(SsizeobjargprocWrapper.class, "executeSsizeobjargproc",
                            MethodType.methodType(int.class, SsizeobjargprocWrapper.class, long.class, long.class, long.class));
            HANDLE_LENFUNC = MethodHandles.lookup().findStatic(LenfuncWrapper.class, "executeLenfunc", MethodType.methodType(long.class, LenfuncWrapper.class, long.class));
            HANDLE_HASHFUNC = MethodHandles.lookup().findStatic(HashfuncWrapper.class, "executeHashfunc", MethodType.methodType(long.class, HashfuncWrapper.class, long.class));
            HANDLE_DESCR_GET_FUNCTION = MethodHandles.lookup().findStatic(DescrGetFunctionWrapper.class, "executeDescrGetFunction",
                            MethodType.methodType(long.class, DescrGetFunctionWrapper.class, long.class, long.class, long.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected final CApiTiming timing;
    private final TpSlotManaged slot;
    private final long nativePointer;

    @SuppressWarnings("this-escape")
    TpSlotWrapper(TpSlotManaged slot, NfiUpcallSignature upcallSignature, MethodHandle methodHandle) {
        this.timing = CApiTiming.create(false, slot);
        this.slot = slot;

        CApiContext cApiContext = PythonContext.get(null).getCApiContext();
        long pointer = cApiContext.registerClosure(getClass().getSimpleName(), upcallSignature, methodHandle.bindTo(this), this, slot);
        if (PythonLanguage.get(null).isSingleContext()) {
            nativePointer = pointer;
        } else {
            nativePointer = NULLPTR;
        }
    }

    public final TpSlotManaged getSlot() {
        return slot;
    }

    @TruffleBoundary
    public final long getPointer() {
        if (nativePointer != NULLPTR) {
            assert PythonLanguage.get(null).isSingleContext();
            return nativePointer;
        }
        return PythonContext.get(null).getCApiContext().getClosurePointer(this);
    }

    public abstract TpSlotWrapper cloneWith(TpSlotManaged slot);

    public static final class GetAttrWrapper extends TpSlotWrapper {
        public GetAttrWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PP, HANDLE_GET_ATTR);
        }

        @SuppressWarnings("try")
        private static long executeGetAttr(GetAttrWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object result = CallManagedSlotGetAttrNode.executeUncached(self.getSlot(), jArg0, jArg1);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "GetAttrWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new GetAttrWrapper(slot);
        }
    }

    public static final class BinarySlotFuncWrapper extends TpSlotWrapper {

        public BinarySlotFuncWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PP, HANDLE_BINARY_SLOT_FUNC);
        }

        @SuppressWarnings("try")
        private static long executeBinarySlot(BinarySlotFuncWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object result = CallSlotBinaryFuncNode.executeUncached(self.getSlot(), jArg0, jArg1);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinarySlotFuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new BinarySlotFuncWrapper(slot);
        }
    }

    public static final class BinaryOpSlotFuncWrapper extends TpSlotWrapper {
        private final ReversibleSlot binaryOp;

        public BinaryOpSlotFuncWrapper(TpSlotManaged slot, ReversibleSlot binaryOp) {
            super(slot, SIGNATURE_P_PP, HANDLE_BINARY_OP_SLOT_FUNC);
            this.binaryOp = binaryOp;
        }

        public static BinaryOpSlotFuncWrapper createAdd(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_ADD);
        }

        public static BinaryOpSlotFuncWrapper createSubtract(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_SUBTRACT);
        }

        public static BinaryOpSlotFuncWrapper createMultiply(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_MULTIPLY);
        }

        public static BinaryOpSlotFuncWrapper createRemainder(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_REMAINDER);
        }

        public static BinaryOpSlotFuncWrapper createLShift(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_LSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createRShift(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_RSHIFT);
        }

        public static BinaryOpSlotFuncWrapper createAnd(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_AND);
        }

        public static BinaryOpSlotFuncWrapper createXor(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_XOR);
        }

        public static BinaryOpSlotFuncWrapper createOr(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_OR);
        }

        public static BinaryOpSlotFuncWrapper createFloorDivide(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_FLOOR_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createTrueDivide(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_TRUE_DIVIDE);
        }

        public static BinaryOpSlotFuncWrapper createDivMod(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_DIVMOD);
        }

        public static BinaryOpSlotFuncWrapper createMatrixMultiply(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, ReversibleSlot.NB_MATRIX_MULTIPLY);
        }

        @SuppressWarnings("try")
        private static long executeBinaryOpSlot(BinaryOpSlotFuncWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object other = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object otherType = GetClassNode.executeUncached(other);
                    Object receiverType = GetClassNode.executeUncached(receiver);
                    TpSlot otherSlot = self.binaryOp.getSlotValue(GetTpSlotsNode.executeUncached(otherType));
                    boolean sameTypes = IsSameTypeNode.executeUncached(receiverType, otherType);
                    Object result = CallSlotBinaryOpNode.executeUncached(self.getSlot(), receiver, receiverType, other, otherSlot, otherType, sameTypes, self.binaryOp);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinaryOpSlotFuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new BinaryOpSlotFuncWrapper(slot, binaryOp);
        }
    }

    public static final class UnaryFuncWrapper extends TpSlotWrapper {

        public UnaryFuncWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_P, HANDLE_UNARY_FUNC);
        }

        @SuppressWarnings("try")
        private static long executeUnary(UnaryFuncWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object result = CallSlotUnaryNode.executeUncached(self.getSlot(), jArg0);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "UnaryFuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new UnaryFuncWrapper(slot);
        }
    }

    public static final class IterNextWrapper extends TpSlotWrapper {

        public IterNextWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_P, HANDLE_ITER_NEXT);
        }

        @SuppressWarnings("try")
        private static long executeIterNext(IterNextWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object result;
                    try {
                        Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                        result = CallSlotTpIterNextNode.executeUncached(self.getSlot(), jArg0);
                    } catch (IteratorExhausted e) {
                        return NULLPTR;
                    }
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "IterNextWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new IterNextWrapper(slot);
        }
    }

    public static final class InquiryWrapper extends TpSlotWrapper {
        public InquiryWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_P, HANDLE_INQUIRY);
        }

        @SuppressWarnings("try")
        private static int executeInquiry(InquiryWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotNbBoolNode.executeUncached(self.getSlot(), jArg0) ? 1 : 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InquiryWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new InquiryWrapper(slot);
        }
    }

    public static final class SqContainsWrapper extends TpSlotWrapper {
        public SqContainsWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PP, HANDLE_SQ_CONTAINS);
        }

        @SuppressWarnings("try")
        private static int executeSqContains(SqContainsWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    return CallSlotSqContainsNode.executeUncached(self.getSlot(), jArg0, jArg1) ? 1 : 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SqContainsWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SqContainsWrapper(slot);
        }
    }

    public static final class ObjobjargWrapper extends TpSlotWrapper {

        public ObjobjargWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PPP, HANDLE_OBJ_OBJ_ARG);
        }

        @SuppressWarnings("try")
        private static int executeObjobjarg(ObjobjargWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotMpAssSubscriptNode.executeUncached(self.getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "ObjobjargWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new ObjobjargWrapper(slot);
        }
    }

    public static final class SetAttrWrapper extends TpSlotWrapper {
        public SetAttrWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PPP, HANDLE_SET_ATTR);
        }

        @SuppressWarnings("try")
        private static int executeSetAttr(SetAttrWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallManagedSlotSetAttrNode.executeUncached(self.getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SetAttrWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SetAttrWrapper(slot);
        }
    }

    public static final class DescrSetFunctionWrapper extends TpSlotWrapper {
        public DescrSetFunctionWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PPP, HANDLE_DESCR_SET_FUNCTION);
        }

        @SuppressWarnings("try")
        private static int executeDescrSetFunction(DescrSetFunctionWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotDescrSet.executeUncached(self.getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "DescrSetFunctionWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new DescrSetFunctionWrapper(slot);
        }
    }

    public static final class InitWrapper extends TpSlotWrapper {

        public InitWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PPP, HANDLE_INIT);
        }

        @SuppressWarnings("try")
        private static int executeInit(InitWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object starArgs = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object kwArgs = NativeToPythonInternalNode.executeUncached(arg2, false);

                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.executeUncached(kwArgs);
                    CallSlotTpInitNode.executeUncached(self.getSlot(), receiver, starArgsArray, kwArgsArray);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InitWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new InitWrapper(slot);
        }
    }

    public static final class NewWrapper extends TpSlotWrapper {

        public NewWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPP, HANDLE_NEW);
        }

        @SuppressWarnings("try")
        private static long executeNew(NewWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object starArgs = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object kwArgs = NativeToPythonInternalNode.executeUncached(arg2, false);

                    Object[] pArgs;
                    if (starArgs != PNone.NO_VALUE) {
                        pArgs = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    } else {
                        pArgs = EMPTY_OBJECT_ARRAY;
                    }
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.executeUncached(kwArgs);

                    Object result = CallSlotTpNewNode.executeUncached(self.getSlot(), receiver, pArgs, kwArgsArray);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NewWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NewWrapper(slot);
        }
    }

    public static final class CallWrapper extends TpSlotWrapper {

        public CallWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPP, HANDLE_CALL);
        }

        @SuppressWarnings("try")
        private static long executeCall(CallWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object starArgs = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object kwArgs = NativeToPythonInternalNode.executeUncached(arg2, false);

                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.executeUncached(kwArgs);
                    Object result = CallSlotTpCallNode.executeUncached(self.getSlot(), receiver, starArgsArray, kwArgsArray);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "CallWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new CallWrapper(slot);
        }
    }

    public static final class NbPowerWrapper extends TpSlotWrapper {

        public NbPowerWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPP, HANDLE_NB_POWER);
        }

        @SuppressWarnings("try")
        private static long executeNbPower(NbPowerWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object v = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object w = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object z = NativeToPythonInternalNode.executeUncached(arg2, false);
                    Object vType = GetClassNode.executeUncached(v);
                    Object wType = GetClassNode.executeUncached(w);
                    TpSlots wSlots = GetTpSlotsNode.executeUncached(wType);
                    boolean sameTypes = IsSameTypeNode.executeUncached(vType, wType);
                    Object result = CallSlotNbPowerNode.executeUncached(self.getSlot(), v, vType, w, wSlots.nb_power(), wType, z, sameTypes);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbPowerWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NbPowerWrapper(slot);
        }
    }

    public static final class NbInPlacePowerWrapper extends TpSlotWrapper {

        public NbInPlacePowerWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPP, HANDLE_NB_IN_PLACE_POWER);
        }

        @SuppressWarnings("try")
        private static long executeNbInPlacePower(NbInPlacePowerWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object v = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object w = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object z = NativeToPythonInternalNode.executeUncached(arg2, false);
                    Object result = CallSlotNbInPlacePowerNode.executeUncached(self.getSlot(), v, w, z);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbInPlacePowerWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new NbInPlacePowerWrapper(slot);
        }
    }

    public static final class RichcmpFunctionWrapper extends TpSlotWrapper {

        public RichcmpFunctionWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPI, HANDLE_RICHCMP_FUNCTION);
        }

        @SuppressWarnings("try")
        private static long executeRichcmpFunction(RichcmpFunctionWrapper self, long arg0, long arg1, int arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    RichCmpOp op = RichCmpOp.fromNative(arg2);
                    Object result = CallSlotRichCmpNode.executeUncached(self.getSlot(), jArg0, jArg1, op);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "RichcmpFunctionWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new RichcmpFunctionWrapper(slot);
        }
    }

    public static final class SsizeargfuncSlotWrapper extends TpSlotWrapper {

        public SsizeargfuncSlotWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PL, HANDLE_SSIZEARGFUNC_SLOT);
        }

        @SuppressWarnings("try")
        private static long executeSsizeargfuncSlot(SsizeargfuncSlotWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    int index = ssizeAsIntUncached(arg1);
                    Object result = CallSlotSizeArgFun.executeUncached(self.getSlot(), jArg0, index);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeargfuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SsizeargfuncSlotWrapper(slot);
        }
    }

    /**
     * For the time being when indices/lengths in GraalPy are 32bit integers, we must deal with
     * possible situation that someone passes larger number to us. In long term, we should migrate
     * indices/length to use longs.
     */
    @TruffleBoundary
    private static int ssizeAsIntUncached(long l) {
        if (PInt.isIntRange(l)) {
            return (int) l;
        }
        throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, l);
    }

    public static final class SsizeobjargprocWrapper extends TpSlotWrapper {

        public SsizeobjargprocWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_I_PLP, HANDLE_SSIZEOBJARGPROC);
        }

        @SuppressWarnings("try")
        private static int executeSsizeobjargproc(SsizeobjargprocWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    int key = ssizeAsIntUncached(arg1);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotSqAssItemNode.executeUncached(self.getSlot(), jArg0, key, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeobjargprocWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new SsizeobjargprocWrapper(slot);
        }
    }

    public static final class LenfuncWrapper extends TpSlotWrapper {
        public LenfuncWrapper(TpSlotManaged managedSlot) {
            super(managedSlot, SIGNATURE_L_P, HANDLE_LENFUNC);
        }

        @SuppressWarnings("try")
        private static long executeLenfunc(LenfuncWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotLenNode.executeUncached(self.getSlot(), jArg0);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "LenfuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new LenfuncWrapper(slot);
        }
    }

    public static final class HashfuncWrapper extends TpSlotWrapper {

        public HashfuncWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_L_P, HANDLE_HASHFUNC);
        }

        @SuppressWarnings("try")
        private static long executeHashfunc(HashfuncWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotHashFunNode.executeUncached(self.getSlot(), jArg0);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "HashfuncWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new HashfuncWrapper(slot);
        }
    }

    public static final class DescrGetFunctionWrapper extends TpSlotWrapper {
        public DescrGetFunctionWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PPP, HANDLE_DESCR_GET_FUNCTION);
        }

        @SuppressWarnings("try")
        private static long executeDescrGetFunction(DescrGetFunctionWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object obj = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object cls = NativeToPythonInternalNode.executeUncached(arg2, false);
                    Object result = CallSlotDescrGet.executeUncached(self.getSlot(), receiver, obj, cls);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "DescrGetFunctionWrapper", self.getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new DescrGetFunctionWrapper(slot);
        }
    }

}
