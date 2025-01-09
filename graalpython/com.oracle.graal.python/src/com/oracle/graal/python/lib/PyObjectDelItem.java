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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.CallSlotMpAssSubscriptNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.CallSlotSqAssItemNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode.IndexForSqSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent to use for PyObject_DelItem.
 */
@GenerateUncached
@GenerateCached
@GenerateInline(inlineByDefault = true)
@ImportStatic(PGuards.class)
public abstract class PyObjectDelItem extends Node {
    public final void executeCached(Frame frame, Object container, Object index) {
        execute(frame, this, container, index);
    }

    public abstract void execute(Frame frame, Node inliningTarget, Object container, Object index);

    @Specialization(guards = "isBuiltinList(object)")
    static void doList(VirtualFrame frame, PList object, Object key,
                    @Cached(inline = false) ListBuiltins.SetSubscriptNode setItemNode) {
        setItemNode.executeVoid(frame, object, key, PNone.NO_VALUE);
    }

    @InliningCutoff
    @Specialization(replaces = "doList")
    static void doGeneric(VirtualFrame frame, Node inliningTarget, Object object, Object key,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached PyObjectDelItemGeneric genericNode) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, object);
        genericNode.execute(frame, inliningTarget, object, slots, key);
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PyObjectDelItemGeneric extends Node {
        public abstract void execute(Frame frame, Node inliningTarget, Object object, TpSlots objectKlassSlots, Object key);

        @Specialization(guards = "slots.mp_ass_subscript() != null")
        static void doMapping(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object key,
                        @Cached CallSlotMpAssSubscriptNode callNode) {
            callNode.execute(frame, inliningTarget, slots.mp_ass_subscript(), object, key, PNone.NO_VALUE);
        }

        @Specialization(guards = {"slots.mp_ass_subscript() == null", "slots.sq_ass_item() != null", "key >= 0"})
        static void doSequenceFastPath(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, int key,
                        @Exclusive @Cached CallSlotSqAssItemNode callSqItem) {
            callSqItem.execute(frame, inliningTarget, slots.sq_ass_item(), object, key, PNone.NO_VALUE);
        }

        @Specialization(guards = {"slots.mp_ass_subscript() == null", "slots.sq_ass_item() != null"}, replaces = "doSequenceFastPath")
        @InliningCutoff
        static void doSequence(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object key,
                        @Cached IndexForSqSlot indexForSqSlot,
                        @Exclusive @Cached CallSlotSqAssItemNode callSqItem) {
            int index = indexForSqSlot.execute(frame, inliningTarget, object, slots, key);
            callSqItem.execute(frame, inliningTarget, slots.sq_ass_item(), object, index, PNone.NO_VALUE);
        }

        @Fallback
        @InliningCutoff
        static void error(Object object, @SuppressWarnings("unused") TpSlots slots, @SuppressWarnings("unused") Object key,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_DELETION, object);
        }
    }

    @NeverDefault
    public static PyObjectDelItem create() {
        return PyObjectDelItemNodeGen.create();
    }

    public static PyObjectDelItem getUncached() {
        return PyObjectDelItemNodeGen.getUncached();
    }
}
