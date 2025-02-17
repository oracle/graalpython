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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline
@GenerateCached(false)
public abstract class PySequenceConcat extends PNodeWithContext {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object w);

    @NeverDefault
    protected static SequenceStorageNodes.ConcatNode createConcat() {
        return SequenceStorageNodes.ConcatNode.create(ListGeneralizationNode::create);
    }

    @Specialization(guards = {"isBuiltinList(left)", "isBuiltinList(right)"})
    static PList doPList(PList left, PList right,
                    @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                    @Bind PythonLanguage language) {
        SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        return PFactory.createList(language, newStore);
    }

    @Specialization(guards = {"isBuiltinTuple(left)", "isBuiltinTuple(right)"})
    static PTuple doTuple(PTuple left, PTuple right,
                    @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                    @Bind PythonLanguage language) {
        SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        return PFactory.createTuple(language, concatenated);
    }

    @Specialization
    static TruffleString doIt(TruffleString left, TruffleString right,
                    @Cached(inline = false) TruffleString.ConcatNode concatNode) {
        return concatNode.execute(left, right, TS_ENCODING, false);
    }

    @Fallback
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object v, Object w,
                    @Exclusive @Cached GetClassNode getVClass,
                    @Cached GetCachedTpSlotsNode getVSlots,
                    @Cached GetCachedTpSlotsNode getWSlots,
                    @Exclusive @Cached GetClassNode getWClass,
                    @Cached PySequenceCheckNode pySeqCheckV,
                    @Cached PySequenceCheckNode pySeqCheckW,
                    @Cached CallBinaryOp1Node callBinaryOp1Node,
                    @Cached InlinedBranchProfile hasNbAddSlot,
                    @Cached InlinedBranchProfile hasNbAddResult,
                    @Cached CallSlotBinaryFuncNode callBinarySlotNode,
                    @Cached PRaiseNode raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
        if (slotsV.sq_concat() != null) {
            return callBinarySlotNode.execute(frame, inliningTarget, slotsV.sq_concat(), v, w);
        }
        if (pySeqCheckV.execute(inliningTarget, v) && pySeqCheckW.execute(inliningTarget, w)) {
            Object classW = getWClass.execute(inliningTarget, w);
            TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
            hasNbAddSlot.enter(inliningTarget);
            Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotsV, w, classW, slotsW, ReversibleSlot.NB_ADD);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                hasNbAddResult.enter(inliningTarget);
                return result;
            }
        }
        return raiseNotSupported(inliningTarget, v, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, PRaiseNode raiseNode) {
        return raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, v);
    }
}
