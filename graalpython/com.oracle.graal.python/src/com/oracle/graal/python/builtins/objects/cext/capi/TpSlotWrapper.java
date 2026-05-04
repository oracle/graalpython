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
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.runtime.nativeaccess.NativeSimpleType.RAW_POINTER;
import static com.oracle.graal.python.runtime.nativeaccess.NativeSimpleType.SINT32;
import static com.oracle.graal.python.runtime.nativeaccess.NativeSimpleType.SINT64;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.CApiUpcallTarget;
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
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.nativeaccess.NativeSignature;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class TpSlotWrapper {

    private static final NativeSignature SIGNATURE_P_P = NativeSignature.create(RAW_POINTER, RAW_POINTER);
    private static final NativeSignature SIGNATURE_P_PP = NativeSignature.create(RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NativeSignature SIGNATURE_P_PPP = NativeSignature.create(RAW_POINTER, RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NativeSignature SIGNATURE_P_PPI = NativeSignature.create(RAW_POINTER, RAW_POINTER, RAW_POINTER, SINT32);
    private static final NativeSignature SIGNATURE_P_PL = NativeSignature.create(RAW_POINTER, RAW_POINTER, SINT64);
    private static final NativeSignature SIGNATURE_I_P = NativeSignature.create(SINT32, RAW_POINTER);
    private static final NativeSignature SIGNATURE_I_PP = NativeSignature.create(SINT32, RAW_POINTER, RAW_POINTER);
    private static final NativeSignature SIGNATURE_I_PPP = NativeSignature.create(SINT32, RAW_POINTER, RAW_POINTER, RAW_POINTER);
    private static final NativeSignature SIGNATURE_I_PLP = NativeSignature.create(SINT32, RAW_POINTER, SINT64, RAW_POINTER);
    private static final NativeSignature SIGNATURE_L_P = NativeSignature.create(SINT64, RAW_POINTER);

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
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            HANDLE_GET_ATTR = lookup.findVirtual(GetAttrWrapper.class, "executeGetAttr",
                            MethodType.methodType(long.class, long.class, long.class));
            HANDLE_BINARY_SLOT_FUNC = lookup.findVirtual(BinarySlotFuncWrapper.class, "executeBinarySlot",
                            MethodType.methodType(long.class, long.class, long.class));
            HANDLE_BINARY_OP_SLOT_FUNC = lookup.findVirtual(BinaryOpSlotFuncWrapper.class, "executeBinaryOpSlot",
                            MethodType.methodType(long.class, long.class, long.class));
            HANDLE_UNARY_FUNC = lookup.findVirtual(UnaryFuncWrapper.class, "executeUnary",
                            MethodType.methodType(long.class, long.class));
            HANDLE_ITER_NEXT = lookup.findVirtual(IterNextWrapper.class, "executeIterNext",
                            MethodType.methodType(long.class, long.class));
            HANDLE_INQUIRY = lookup.findVirtual(InquiryWrapper.class, "executeInquiry",
                            MethodType.methodType(int.class, long.class));
            HANDLE_SQ_CONTAINS = lookup.findVirtual(SqContainsWrapper.class, "executeSqContains",
                            MethodType.methodType(int.class, long.class, long.class));
            HANDLE_OBJ_OBJ_ARG = lookup.findVirtual(ObjobjargWrapper.class, "executeObjobjarg",
                            MethodType.methodType(int.class, long.class, long.class, long.class));
            HANDLE_SET_ATTR = lookup.findVirtual(SetAttrWrapper.class, "executeSetAttr",
                            MethodType.methodType(int.class, long.class, long.class, long.class));
            HANDLE_DESCR_SET_FUNCTION = lookup.findVirtual(DescrSetFunctionWrapper.class, "executeDescrSetFunction",
                            MethodType.methodType(int.class, long.class, long.class, long.class));
            HANDLE_INIT = lookup.findVirtual(InitWrapper.class, "executeInit",
                            MethodType.methodType(int.class, long.class, long.class, long.class));
            HANDLE_NEW = lookup.findVirtual(NewWrapper.class, "executeNew",
                            MethodType.methodType(long.class, long.class, long.class, long.class));
            HANDLE_CALL = lookup.findVirtual(CallWrapper.class, "executeCall",
                            MethodType.methodType(long.class, long.class, long.class, long.class));
            HANDLE_NB_POWER = lookup.findVirtual(NbPowerWrapper.class, "executeNbPower",
                            MethodType.methodType(long.class, long.class, long.class, long.class));
            HANDLE_NB_IN_PLACE_POWER = lookup.findVirtual(NbInPlacePowerWrapper.class, "executeNbInPlacePower",
                            MethodType.methodType(long.class, long.class, long.class, long.class));
            HANDLE_RICHCMP_FUNCTION = lookup.findVirtual(RichcmpFunctionWrapper.class, "executeRichcmpFunction",
                            MethodType.methodType(long.class, long.class, long.class, int.class));
            HANDLE_SSIZEARGFUNC_SLOT = lookup.findVirtual(SsizeargfuncSlotWrapper.class, "executeSsizeargfuncSlot",
                            MethodType.methodType(long.class, long.class, long.class));
            HANDLE_SSIZEOBJARGPROC = lookup.findVirtual(SsizeobjargprocWrapper.class, "executeSsizeobjargproc",
                            MethodType.methodType(int.class, long.class, long.class, long.class));
            HANDLE_LENFUNC = lookup.findVirtual(LenfuncWrapper.class, "executeLenfunc",
                            MethodType.methodType(long.class, long.class));
            HANDLE_HASHFUNC = lookup.findVirtual(HashfuncWrapper.class, "executeHashfunc",
                            MethodType.methodType(long.class, long.class));
            HANDLE_DESCR_GET_FUNCTION = lookup.findVirtual(DescrGetFunctionWrapper.class, "executeDescrGetFunction",
                            MethodType.methodType(long.class, long.class, long.class, long.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected final CApiTiming timing;
    private final TpSlotManaged slot;
    private final NativeSignature upcallSignature;
    private final MethodHandle boundMethodHandle;
    private final long nativePointer;

    @SuppressWarnings("this-escape")
    TpSlotWrapper(TpSlotManaged slot, NativeSignature upcallSignature, MethodHandle methodHandle) {
        this.timing = CApiTiming.create(false, slot);
        this.slot = slot;
        this.upcallSignature = upcallSignature;
        this.boundMethodHandle = methodHandle.bindTo(this);

        if (canShareNativePointers()) {
            nativePointer = registerClosure();
        } else {
            nativePointer = NULLPTR;
        }
    }

    private static boolean canShareNativePointers() {
        return PythonLanguage.get(null).isSingleContext() && !PythonContext.get(null).getOption(PythonOptions.IsolateNativeModules);
    }

    private long registerClosure() {
        CApiContext cApiContext = PythonContext.get(null).getCApiContext();
        return cApiContext.registerClosure(getClass().getSimpleName(), upcallSignature, boundMethodHandle, this, slot);
    }

    public final TpSlotManaged getSlot() {
        return slot;
    }

    @TruffleBoundary
    public final long getPointer() {
        if (nativePointer != NULLPTR) {
            assert canShareNativePointers();
            return nativePointer;
        }
        long pointer = PythonContext.get(null).getCApiContext().getClosurePointer(this);
        return pointer == -1 ? registerClosure() : pointer;
    }

    public abstract TpSlotWrapper cloneWith(TpSlotManaged slot);

    public static final class GetAttrWrapper extends TpSlotWrapper {
        public GetAttrWrapper(TpSlotManaged slot) {
            super(slot, SIGNATURE_P_PP, HANDLE_GET_ATTR);
        }

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeGetAttr(long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object result = CallManagedSlotGetAttrNode.executeUncached(getSlot(), jArg0, jArg1);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "GetAttrWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeBinarySlot(long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object result = CallSlotBinaryFuncNode.executeUncached(getSlot(), jArg0, jArg1);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinarySlotFuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeBinaryOpSlot(long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object other = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object otherType = GetClassNode.executeUncached(other);
                    Object receiverType = GetClassNode.executeUncached(receiver);
                    TpSlot otherSlot = binaryOp.getSlotValue(GetTpSlotsNode.executeUncached(otherType));
                    boolean sameTypes = IsSameTypeNode.executeUncached(receiverType, otherType);
                    Object result = CallSlotBinaryOpNode.executeUncached(getSlot(), receiver, receiverType, other, otherSlot, otherType, sameTypes, binaryOp);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "BinaryOpSlotFuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeUnary(long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object result = CallSlotUnaryNode.executeUncached(getSlot(), jArg0);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "UnaryFuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeIterNext(long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object result;
                    try {
                        Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                        result = CallSlotTpIterNextNode.executeUncached(getSlot(), jArg0);
                    } catch (IteratorExhausted e) {
                        return NULLPTR;
                    }
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "IterNextWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeInquiry(long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotNbBoolNode.executeUncached(getSlot(), jArg0) ? 1 : 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InquiryWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeSqContains(long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    return CallSlotSqContainsNode.executeUncached(getSlot(), jArg0, jArg1) ? 1 : 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SqContainsWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeObjobjarg(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotMpAssSubscriptNode.executeUncached(getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "ObjobjargWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeSetAttr(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallManagedSlotSetAttrNode.executeUncached(getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SetAttrWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeDescrSetFunction(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotDescrSet.executeUncached(getSlot(), jArg0, jArg1, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "DescrSetFunctionWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeInit(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object starArgs = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object kwArgs = NativeToPythonInternalNode.executeUncached(arg2, false);

                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.executeUncached(kwArgs);
                    CallSlotTpInitNode.executeUncached(getSlot(), receiver, starArgsArray, kwArgsArray);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "InitWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeNew(long arg0, long arg1, long arg2) {
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

                    Object result = CallSlotTpNewNode.executeUncached(getSlot(), receiver, pArgs, kwArgsArray);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NewWrapper", getSlot());
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeCall(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object starArgs = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object kwArgs = NativeToPythonInternalNode.executeUncached(arg2, false);

                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.executeUncached(kwArgs);
                    Object result = CallSlotTpCallNode.executeUncached(getSlot(), receiver, starArgsArray, kwArgsArray);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "CallWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeNbPower(long arg0, long arg1, long arg2) {
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
                    Object result = CallSlotNbPowerNode.executeUncached(getSlot(), v, vType, w, wSlots.nb_power(), wType, z, sameTypes);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbPowerWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeNbInPlacePower(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object v = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object w = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object z = NativeToPythonInternalNode.executeUncached(arg2, false);
                    Object result = CallSlotNbInPlacePowerNode.executeUncached(getSlot(), v, w, z);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "NbInPlacePowerWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeRichcmpFunction(long arg0, long arg1, int arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object jArg1 = NativeToPythonInternalNode.executeUncached(arg1, false);
                    RichCmpOp op = RichCmpOp.fromNative(arg2);
                    Object result = CallSlotRichCmpNode.executeUncached(getSlot(), jArg0, jArg1, op);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "RichcmpFunctionWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeSsizeargfuncSlot(long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    int index = ssizeAsIntUncached(arg1);
                    Object result = CallSlotSizeArgFun.executeUncached(getSlot(), jArg0, index);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeargfuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private int executeSsizeobjargproc(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    int key = ssizeAsIntUncached(arg1);
                    Object jArg2 = NativeToPythonInternalNode.executeUncached(arg2, false);
                    CallSlotSqAssItemNode.executeUncached(getSlot(), jArg0, key, jArg2);
                    return 0;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "SsizeobjargprocWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeLenfunc(long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotLenNode.executeUncached(getSlot(), jArg0);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "LenfuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeHashfunc(long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonInternalNode.executeUncached(arg0, false);
                    return CallSlotHashFunNode.executeUncached(getSlot(), jArg0);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "HashfuncWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return -1;
            } finally {
                CApiTiming.exit(timing);
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

        @CApiUpcallTarget
        @SuppressWarnings("try")
        private long executeDescrGetFunction(long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    // convert args
                    Object receiver = NativeToPythonInternalNode.executeUncached(arg0, false);
                    Object obj = NativeToPythonInternalNode.executeUncached(arg1, false);
                    Object cls = NativeToPythonInternalNode.executeUncached(arg2, false);
                    Object result = CallSlotDescrGet.executeUncached(getSlot(), receiver, obj, cls);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "DescrGetFunctionWrapper", getSlot());
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e.getEscapedException());
                return NULLPTR;
            } finally {
                CApiTiming.exit(timing);
            }
        }

        @Override
        public TpSlotWrapper cloneWith(TpSlotManaged slot) {
            return new DescrGetFunctionWrapper(slot);
        }
    }

}
