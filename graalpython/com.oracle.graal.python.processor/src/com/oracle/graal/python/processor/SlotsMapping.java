/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.processor;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.processor.SlotsProcessor.TpSlotData;

public class SlotsMapping {
    private static String getSuffix(boolean isComplex) {
        return isComplex ? "Complex" : "Simple";
    }

    static String getSlotBaseClass(Slot s) {
        return switch (s.value()) {
            case nb_bool -> "TpSlotInquiry.TpSlotInquiryBuiltin";
            case nb_add, nb_multiply -> "TpSlotBinaryOp.TpSlotBinaryOpBuiltin";
            case sq_concat -> "TpSlotBinaryFunc.TpSlotSqConcat";
            case sq_length, mp_length -> "TpSlotLen.TpSlotLenBuiltin" + getSuffix(s.isComplex());
            case sq_item, sq_repeat -> "TpSlotSizeArgFun.TpSlotSizeArgFunBuiltin";
            case mp_subscript -> "TpSlotBinaryFunc.TpSlotMpSubscript";
            case tp_getattro -> "TpSlotGetAttr.TpSlotGetAttrBuiltin";
            case tp_descr_get -> "TpSlotDescrGet.TpSlotDescrGetBuiltin" + getSuffix(s.isComplex());
            case tp_descr_set -> "TpSlotDescrSet.TpSlotDescrSetBuiltin";
            case tp_setattro -> "TpSlotSetAttr.TpSlotSetAttrBuiltin";
        };
    }

    static String getSlotNodeBaseClass(Slot s) {
        return switch (s.value()) {
            case tp_descr_get -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode";
            case nb_bool -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode";
            case nb_add, nb_multiply -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode";
            case sq_concat -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode";
            case sq_length, mp_length -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode";
            case sq_item -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode";
            case sq_repeat -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode";
            case mp_subscript -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode";
            case tp_getattro -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode";
            case tp_descr_set -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode";
            case tp_setattro -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode";
        };
    }

    static String getUncachedExecuteSignature(SlotKind s) {
        return switch (s) {
            case nb_bool -> "boolean executeUncached(Object self)";
            case tp_descr_get -> "Object executeUncached(Object self, Object obj, Object type)";
            case sq_length, mp_length -> "int executeUncached(Object self)";
            case tp_getattro, tp_descr_set, tp_setattro, sq_item, mp_subscript, nb_add, sq_concat, sq_repeat, nb_multiply ->
                throw new AssertionError("Should not reach here: should be always complex");
        };
    }

    static boolean supportsComplex(SlotKind s) {
        return switch (s) {
            case nb_bool -> false;
            case sq_length, mp_length, tp_getattro, tp_descr_get, tp_descr_set,
                            tp_setattro, sq_item, mp_subscript, nb_add, sq_concat,
                            sq_repeat, nb_multiply ->
                true;
        };
    }

    static boolean supportsSimple(SlotKind s) {
        return switch (s) {
            case nb_bool, sq_length, mp_length, tp_descr_get -> true;
            case tp_getattro, tp_descr_set, tp_setattro, sq_item, mp_subscript,
                            nb_add, sq_concat, sq_repeat, nb_multiply ->
                false;
        };
    }

    static String getUncachedExecuteCall(SlotKind s) {
        return switch (s) {
            case nb_bool -> "executeBool(null, self)";
            case sq_length, mp_length -> "executeInt(null, self)";
            case tp_descr_get -> "execute(null, self, obj, type)";
            case tp_getattro, tp_descr_set, tp_setattro, sq_item, mp_subscript,
                            nb_add, sq_concat, nb_multiply, sq_repeat ->
                throw new AssertionError("Should not reach here: should be always complex");
        };
    }

    public static String getExtraCtorArgs(TpSlotData slot) {
        return switch (slot.slot().value()) {
            case nb_add -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__";
            case nb_multiply -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__";
            default -> "";
        };
    }
}
