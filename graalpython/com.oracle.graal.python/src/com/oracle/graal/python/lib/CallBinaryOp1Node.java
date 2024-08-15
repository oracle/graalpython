/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.IsSameSlotNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.CallSlotBinaryOpNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Equivalent of cpython://Objects/abstract.c#binary_op1.
 */
@GenerateInline
@GenerateCached(false)
public abstract class CallBinaryOp1Node extends PNodeWithContext {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object classV, TpSlot slotV,
                    Object w, Object classW, TpSlot slotW, BinaryOpSlot op);

    // CPython binary_op1 may end up calling SLOT1BINFULL - wrapper around the dunder methods, which
    // duplicates some of the logic checking the right operand, its slot, and whether it is subtype.
    // We forward everything to our version of SLOT1BINFULL implemented in CallSlotBinaryOpNode
    // except for the result of the subclass check, but the subclass check is never going to be
    // performed in both binary_op1 and SLOT1BINFULL, consider these possibilities of the v and w
    // slot values:
    //
    // TpSlotPython & TpSlotPython: binary_op1 skips trying to call the w slot altogether, because
    // the slots are the "same" (from CPython perspective they're both the same binop wrapper).
    //
    // TpSlotPython & native/builtin: binary_op1 is going to try calling the native/builtin w slot,
    // but then in SLOT1BINFULL we find out that w is not "BinOpWrapper" and we skip the subclass
    // check, because there is no __rxxx__ to call anyway.
    //
    // native/builtin & TpSlotPython: binary_op1 is going to try calling the native/builtin w slot,
    // but then in SLOT1BINFULL we find out that the "slot" argument is not "BinOpWrapper" - the
    // slot is indeed called as `slotw(v, w)`, so "self" is not of the type that owns the slot - so
    // we just try __rxxx__, again, without any subclass check.

    @Specialization
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object v, Object classV, TpSlot slotV, Object w, Object classW, TpSlot slotWIn, BinaryOpSlot op,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached IsSameSlotNode isSameSlotNode,
                    @Cached InlinedConditionProfile isSameTypeProfile,
                    @Cached InlinedConditionProfile isSameSlotProfile,
                    @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                    @Cached InlinedBranchProfile wResultBranch,
                    @Cached InlinedBranchProfile vResultBranch,
                    @Cached InlinedBranchProfile notImplementedBranch,
                    @Cached CallSlotBinaryOpNode callSlotWNode,
                    @Cached CallSlotBinaryOpNode callSlotVNode) {
        TpSlot slotW = null;
        boolean sameTypes = isSameTypeProfile.profile(inliningTarget, isSameTypeNode.execute(inliningTarget, classW, classV));
        if (!sameTypes) {
            slotW = slotWIn;
            if (isSameSlotProfile.profile(inliningTarget, slotV != null && slotW != null && isSameSlotNode.execute(inliningTarget, slotW, slotV))) {
                slotW = null;
            }
        }
        // Note: we call slotW with v as the receiver. This appears to be the semantics of
        // CPython reversible binop slots. This is supposed to allow the slot to handle
        // the reversible case, if the slot does not want to handle it, it should detect that
        // the first receiver argument is not of the right type and just return NotImplemented.
        if (slotV != null) {
            if (slotW != null && isSubtypeNode.execute(frame, classW, classV)) {
                assert !sameTypes;
                Object result = callSlotWNode.execute(frame, inliningTarget, slotW, v, classV, w, slotW, classW, false, op);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    wResultBranch.enter(inliningTarget);
                    return result;
                }
                slotW = null;
            }
            Object result = callSlotVNode.execute(frame, inliningTarget, slotV, v, classV, w, slotWIn, classW, sameTypes, op);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                vResultBranch.enter(inliningTarget);
                return result;
            }
        }
        if (slotW != null) {
            assert !sameTypes;
            return callSlotWNode.execute(frame, inliningTarget, slotW, v, classV, w, slotW, classW, false, op);
        }
        notImplementedBranch.enter(inliningTarget);
        return PNotImplemented.NOT_IMPLEMENTED;
    }
}
