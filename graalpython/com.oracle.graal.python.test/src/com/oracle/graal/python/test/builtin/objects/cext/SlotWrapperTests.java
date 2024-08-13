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
package com.oracle.graal.python.test.builtin.objects.cext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TpSlotWrapper;
import com.oracle.graal.python.builtins.objects.type.TpSlots.TpSlotMeta;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.strings.TruffleString;

public class SlotWrapperTests {
    // Doesn't really need to be a real callable or type
    private static final Object DUMMY_CALLABLE = new Object();
    private static final Object DUMMY_TYPE = new Object();

    @Test
    public void testCloneContract() {
        TruffleString testName = PythonUtils.toTruffleStringUncached("__test__");
        TpSlotPythonSingle slotValue = new TpSlotPythonSingle(DUMMY_CALLABLE, DUMMY_TYPE, testName);
        TpSlotPythonSingle slotValue2 = new TpSlotPythonSingle(DUMMY_CALLABLE, DUMMY_TYPE, testName);
        for (TpSlotMeta slot : TpSlotMeta.values()) {
            if (!slot.supportsManagedSlotValues()) {
                continue;
            }
            // We rely on the wrapper not doing any validation of the value...
            TpSlotWrapper wrapper = slot.createNativeWrapper(slotValue);
            assertThat(wrapper.getSlot(), equalTo(slotValue));

            TpSlotWrapper clonedWrapper = wrapper.cloneWith(slotValue2);
            assertThat(clonedWrapper, not(equalTo(wrapper)));
            assertThat(clonedWrapper, instanceOf(wrapper.getClass()));
            assertThat(clonedWrapper.getSlot(), equalTo(slotValue2));
        }
    }
}
