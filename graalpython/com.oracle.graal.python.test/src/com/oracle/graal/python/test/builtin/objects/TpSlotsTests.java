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
package com.oracle.graal.python.test.builtin.objects;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.Builder;
import com.oracle.graal.python.builtins.objects.type.TpSlots.TpSlotMeta;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.util.Function;

public class TpSlotsTests {
    @Test
    public void testBuilderBasic() {
        Builder builder = TpSlots.newBuilder();
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            // Use the TpSlotMeta as dummy "callable" object to verify that the slot values were
            // properly assigned to the right fields of TpSlots record
            builder.set(def, new TpSlotNative(def));
        }

        TpSlots slots = builder.build();
        verifySlots(slots, def -> true);

        checkSlotValue(TpSlotMeta.SQ_LENGTH, slots.combined_sq_mp_length());
        checkSlotValue(TpSlotMeta.MP_LENGTH, slots.combined_mp_sq_length());
        checkSlotValue(TpSlotMeta.TP_GETATTRO, slots.combined_tp_getattro_getattr());
        checkSlotValue(TpSlotMeta.TP_SETATTRO, slots.combined_tp_setattro_setattr());
    }

    @Test
    public void testBuilderOptimizations1() {
        Builder builder = TpSlots.newBuilder();
        builder.set(TpSlotMeta.MP_LENGTH, new TpSlotNative(TpSlotMeta.MP_LENGTH));
        builder.set(TpSlotMeta.TP_GETATTR, new TpSlotNative(TpSlotMeta.TP_GETATTR));
        builder.set(TpSlotMeta.TP_SETATTR, new TpSlotNative(TpSlotMeta.TP_SETATTR));

        TpSlots slots = builder.build();
        verifySlots(slots, def -> def == TpSlotMeta.MP_LENGTH || def == TpSlotMeta.TP_GETATTR || def == TpSlotMeta.TP_SETATTR);

        checkSlotValue(TpSlotMeta.MP_LENGTH, slots.combined_sq_mp_length());
        checkSlotValue(TpSlotMeta.MP_LENGTH, slots.combined_mp_sq_length());
        checkSlotValue(TpSlotMeta.TP_GETATTR, slots.combined_tp_getattro_getattr());
        checkSlotValue(TpSlotMeta.TP_SETATTR, slots.combined_tp_setattro_setattr());
    }

    @Test
    public void testBuilderOptimizations2() {
        Builder builder = TpSlots.newBuilder();
        builder.set(TpSlotMeta.SQ_LENGTH, new TpSlotNative(TpSlotMeta.SQ_LENGTH));

        TpSlots slots = builder.build();
        verifySlots(slots, def -> def == TpSlotMeta.SQ_LENGTH);

        checkSlotValue(TpSlotMeta.SQ_LENGTH, slots.combined_sq_mp_length());
        checkSlotValue(TpSlotMeta.SQ_LENGTH, slots.combined_mp_sq_length());
        Assert.assertNull(slots.combined_tp_getattro_getattr());
    }

    private static void verifySlots(TpSlots slots, Function<TpSlotMeta, Boolean> checkNonNullValue) {
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            TpSlot slotValue = def.getValue(slots);
            if (checkNonNullValue.apply(def)) {
                checkSlotValue(def, slotValue);
            } else {
                Assert.assertNull(def.name(), slotValue);
            }
        }
    }

    private static void checkSlotValue(TpSlotMeta def, TpSlot slotValue) {
        Assert.assertTrue(def.name(), slotValue instanceof TpSlotNative slotNative && slotNative.getCallable() == def);
    }
}
