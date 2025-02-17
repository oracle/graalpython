/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.InplaceSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateInline
@GenerateCached(false)
public abstract class PySequenceInplaceRepeat extends PNodeWithContext {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object o, int count);

    @Specialization
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object o, int count,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode slotsNode,
                    @Cached PySequenceCheckNode sequenceCheckNode,
                    @Cached CallBinaryIOp1Node callBinaryIOp1Node,
                    @Cached InlinedBranchProfile hasInplaceRepeat,
                    @Cached InlinedBranchProfile hasRepeat,
                    @Cached InlinedBranchProfile isSequence,
                    @Cached InlinedBranchProfile hasNbMultiplyResult,
                    @Cached CallSlotSizeArgFun callSlot,
                    @Cached PRaiseNode raiseNode) {
        Object classV = getClassNode.execute(inliningTarget, o);
        TpSlots slotsV = slotsNode.execute(inliningTarget, classV);
        TpSlot repeatSlot = null;
        if (slotsV.sq_inplace_repeat() != null) {
            hasInplaceRepeat.enter(inliningTarget);
            repeatSlot = slotsV.sq_inplace_repeat();
        } else if (slotsV.sq_repeat() != null) {
            hasRepeat.enter(inliningTarget);
            repeatSlot = slotsV.sq_repeat();
        }
        if (repeatSlot != null) {
            return callSlot.execute(frame, inliningTarget, repeatSlot, o, count);
        }
        if (sequenceCheckNode.execute(inliningTarget, o)) {
            isSequence.enter(inliningTarget);
            PythonBuiltinClassType countType = PythonBuiltinClassType.PInt;
            Object result = callBinaryIOp1Node.execute(frame, inliningTarget, o, classV, slotsV, count, countType, countType.getSlots(), InplaceSlot.NB_INPLACE_MULTIPLY);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                hasNbMultiplyResult.enter(inliningTarget);
                return result;
            }
        }
        return raiseNotSupported(inliningTarget, o, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, PRaiseNode raiseNode) {
        return raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, v);
    }
}
