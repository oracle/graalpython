/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.CallSlotSqAssItemNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode.IndexForSqSlotInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PySequence_DelItem}. For native object it would only call
 * {@code sq_ass_item} and never {@code mp_ass_subscript}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(PGuards.class)
public abstract class PySequenceDelItemNode extends Node {
    public abstract void execute(Frame frame, Node inliningTarget, Object container, int index);

    @Specialization(guards = "isBuiltinList(object)")
    static void doList(VirtualFrame frame, PList object, int key,
                    @Cached(inline = false) ListBuiltins.SetItemNode setItemNode) {
        setItemNode.executeIntKey(frame, object, key, PNone.NO_VALUE);
    }

    @InliningCutoff
    @Specialization(replaces = "doList")
    static void doGeneric(VirtualFrame frame, Node inliningTarget, Object object, int index,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached IndexForSqSlotInt indexForSqSlot,
                    @Cached CallSlotSqAssItemNode callSetItem,
                    @Cached PRaiseNode.Lazy raiseNode) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, object);
        index = indexForSqSlot.execute(frame, inliningTarget, object, slots, index);
        if (slots.sq_ass_item() != null) {
            callSetItem.execute(frame, inliningTarget, slots.sq_ass_item(), object, index, PNone.NO_VALUE);
        } else {
            throw raiseNotSupported(object, inliningTarget, raiseNode, slots);
        }
    }

    @InliningCutoff
    static PException raiseNotSupported(Object object, Node inliningTarget, PRaiseNode.Lazy raiseNode, TpSlots slots) {
        TruffleString message = ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_DELETION;
        if (slots.mp_subscript() != null) {
            message = ErrorMessages.IS_NOT_A_SEQUENCE;
        }
        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, message, object);
    }
}
