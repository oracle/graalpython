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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline(inlineByDefault = true)
public abstract class PyNumberAddNode extends BinaryOpNode {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object w);

    @Override
    public final Object executeObject(VirtualFrame frame, Object left, Object right) {
        return executeCached(frame, left, right);
    }

    public final Object executeCached(VirtualFrame frame, Object v, Object w) {
        return execute(frame, this, v, w);
    }

    public abstract int executeInt(VirtualFrame frame, Node inliningTarget, int left, int right) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Node inliningTarget, double left, double right) throws UnexpectedResultException;

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins, FloatBuiltins, ListBuiltins, ...
     */

    @Specialization(rewriteOn = ArithmeticException.class)
    public static int add(int left, int right) {
        return Math.addExact(left, right);
    }

    @Specialization
    public static long doIIOvf(int x, int y) {
        return x + (long) y;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public static long addLong(long left, long right) {
        return Math.addExact(left, right);
    }

    @Specialization
    public static double doDD(double left, double right) {
        return left + right;
    }

    @Specialization
    public static double doDL(double left, long right) {
        return left + right;
    }

    @Specialization
    public static double doLD(long left, double right) {
        return left + right;
    }

    @Specialization
    public static double doDI(double left, int right) {
        return left + right;
    }

    @Specialization
    public static double doID(int left, double right) {
        return left + right;
    }

    @NeverDefault
    protected static SequenceStorageNodes.ConcatNode createConcat() {
        return SequenceStorageNodes.ConcatNode.create(ListGeneralizationNode::create);
    }

    @Specialization
    static PList doPList(Node inliningTarget, PList left, PList right,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                    @Shared @Cached(inline = false) PythonObjectFactory factory) {
        SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        return factory.createList(getClassNode.execute(inliningTarget, left), newStore);
    }

    @Specialization(guards = {"isBuiltinTuple(left)", "isBuiltinTuple(right)"})
    static PTuple doTuple(Node inliningTarget, PTuple left, PTuple right,
                    @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                    @Shared @Cached(inline = false) PythonObjectFactory factory) {
        SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        return factory.createTuple(concatenated);
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
                    @Cached CallBinaryOp1Node callBinaryOp1Node,
                    @Cached InlinedBranchProfile hasNbAddSlot,
                    @Cached InlinedBranchProfile hasNbAddResult,
                    @Cached CallSlotBinaryFuncNode callBinarySlotNode,
                    @Cached PRaiseNode.Lazy raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
        TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
        TpSlot slotV = slotsV.nb_add();
        TpSlot slotW = slotsW.nb_add();
        if (slotV != null || slotW != null) {
            hasNbAddSlot.enter(inliningTarget);
            Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, BinaryOpSlot.NB_ADD);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                hasNbAddResult.enter(inliningTarget);
                return result;
            }
        }
        if (slotsV.sq_concat() != null) {
            return callBinarySlotNode.execute(frame, inliningTarget, slotsV.sq_concat(), v, w);
        }
        return raiseNotSupported(inliningTarget, v, w, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, Lazy raiseNode) {
        return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "+", v, w);
    }

    @NeverDefault
    public static PyNumberAddNode create() {
        return PyNumberAddNodeGen.create();
    }
}
